//! 阶段 2（Rust 模块拆分）的语义判据。
//!
//! 拆分是纯搬家，所以「编译通过」不算数 —— 这些测试断言的是**行为**：每个 Rust
//! 查询的结果必须等于同一条 SQL 直接算出来的结果。这样写有两个好处：
//!
//! 1. **抗漂移**：刮削进程随时在往库里写，断言死总数会偶发失败。所有断言都用
//!    「Rust 的结果 == 对照 SQL 的结果」，两边同时变，永远相等。
//! 2. **能抓住参数错位**：`get_episode_library` 有 (query, sort, studio) 三个相邻的
//!    `Option<String>`，调换顺序照样编译通过。所以排序类断言比对的是**完整的 id
//!    序列**（含 ORDER BY / LIMIT / OFFSET），而不是我自己手写一套比较规则 ——
//!    手写规则会在 `COLLATE NOCASE` 这类细节上和实现悄悄分叉。
//!
//! 只读打开真库，不写、不建副本。路径可用 `GEVI_DB` 覆盖。

use gevi_core::models::{FilterArgs, MoviesResponse, PerformerFilterArgs};
use gevi_core::queries;
use rusqlite::{params, Connection, OpenFlags};
use std::collections::HashMap;
use std::path::PathBuf;

/// 测试二进制的 cwd 是 crate 根（gevi-core/），真库在上面三层。
fn find_db() -> PathBuf {
    if let Ok(p) = std::env::var("GEVI_DB") {
        return PathBuf::from(p);
    }
    for candidate in ["../../../gevi.db", "../../gevi.db", "gevi.db"] {
        let p = PathBuf::from(candidate);
        if p.exists() {
            return p;
        }
    }
    panic!("没找到 gevi.db —— 用 GEVI_DB=/path/to/gevi.db 指定，或从 src-tauri/ 下跑");
}

fn db() -> Connection {
    let path = find_db();
    Connection::open_with_flags(&path, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .unwrap_or_else(|e| panic!("只读打开 {:?} 失败: {}", path, e))
}

/// 一个读事务，让「Rust 查询」和「对照 SQL」看到同一份快照。
///
/// 没有它，两次读之间刮削进程插入一行，测试就会偶发失败 —— 这种失败最难查。
/// `Transaction` 会 Deref 成 `Connection`，所以能直接喂给 `queries::*`。
fn snapshot(conn: &Connection) -> rusqlite::Transaction<'_> {
    conn.unchecked_transaction().expect("开启读事务失败")
}

fn count(conn: &Connection, sql: &str) -> i64 {
    conn.query_row(sql, [], |r| r.get(0)).unwrap_or_else(|e| panic!("{}: {}", sql, e))
}

fn count1(conn: &Connection, sql: &str, arg: impl rusqlite::ToSql) -> i64 {
    conn.query_row(sql, params![arg], |r| r.get(0)).unwrap_or_else(|e| panic!("{}: {}", sql, e))
}

/// 第一列按 i64 取出来，用于比对完整排序结果。
fn ids(conn: &Connection, sql: &str) -> Vec<i64> {
    let mut stmt = conn.prepare(sql).unwrap_or_else(|e| panic!("{}: {}", sql, e));
    let rows = stmt.query_map([], |r| r.get(0)).unwrap_or_else(|e| panic!("{}: {}", sql, e));
    rows.filter_map(|r| r.ok()).collect()
}

fn strings(conn: &Connection, sql: &str) -> Vec<String> {
    let mut stmt = conn.prepare(sql).unwrap_or_else(|e| panic!("{}: {}", sql, e));
    let rows = stmt.query_map([], |r| r.get(0)).unwrap_or_else(|e| panic!("{}: {}", sql, e));
    rows.filter_map(|r| r.ok()).collect()
}

fn movie_ids(res: &MoviesResponse) -> Vec<i64> {
    res.items.iter().map(|m| m.id).collect()
}

/// 「作品数」在线性列表里的定义：影片数 + 分集数。列表页、筛选、排序都用它。
const WORKS_EXPR: &str = "(SELECT count(*) FROM movie_performers mp WHERE mp.performer_id = p.id) \
                          + (SELECT count(*) FROM episode_performers ep WHERE ep.performer_id = p.id)";

// ---------------------------------------------------------------- 错误类型

/// 拆分把 `Result<_, String>` 换成了 `Result<_, Error>`。用户看到的消息必须一字不差，
/// 所以 Display 必须等于旧的 `to_string()`。
#[test]
fn error_display_is_identical_to_the_old_to_string() {
    let db_err = rusqlite::Error::QueryReturnedNoRows;
    let expected = db_err.to_string();
    assert_eq!(gevi_core::Error::from(db_err).to_string(), expected);

    let msg = "未知的收藏类型 'bogus'".to_string();
    assert_eq!(gevi_core::Error::from(msg.clone()).to_string(), msg);
}

// ---------------------------------------------------------------- 纯函数

/// `<br />` 有四种写法，全都要切干净。
#[test]
fn explode_attr_splits_every_br_spelling() {
    use gevi_core::sql::explode_attr;
    assert_eq!(explode_attr(Some("Brown<br />Blond".into())), ["Brown", "Blond"]);
    assert_eq!(explode_attr(Some("A<br/>B<br>C".into())), ["A", "B", "C"]);
    assert_eq!(explode_attr(Some("  单个  ".into())), ["单个"]);
    assert!(explode_attr(None).is_empty());
    assert!(explode_attr(Some("   ".into())).is_empty());

    // 大小写。这条以前只认小写，而 server.py 那边是 `<br\s*/?>` + IGNORECASE，
    // 于是同一个库里两边拆得不一样；现在三个拼写 × 四种大小写都算数。
    for spelling in ["<br />", "<bR />", "<Br />", "<BR />", "<br/>", "<BR/>", "<br>", "<BR>"] {
        assert_eq!(
            explode_attr(Some(format!("Brown{}Blond", spelling))),
            ["Brown", "Blond"],
            "{} 应当被当作分隔符",
            spelling
        );
    }

    // 一个空格是上限：`\s*` 能认两个空格，但 SQL 的 REPLACE 链表达不了，
    // 两边就会分叉。宁可两边一致地不认。
    assert_eq!(
        explode_attr(Some("Brown<br  />Blond".into())),
        ["Brown<br  />Blond"]
    );
}

// ---------------------------------------------------------------- 统计

#[test]
fn stats_counts_match_sql() {
    let conn = db();
    let tx = snapshot(&conn);
    let s = queries::stats::get_stats(&tx).unwrap();

    assert_eq!(s.movies, count(&tx, "SELECT count(*) FROM movies"));
    assert_eq!(s.performers, count(&tx, "SELECT count(*) FROM performers"));
    assert_eq!(s.episodes, count(&tx, "SELECT count(*) FROM episodes"));
    assert_eq!(s.movie_performers, count(&tx, "SELECT count(*) FROM movie_performers"));
    assert_eq!(
        s.performers_with_image,
        count(&tx, "SELECT count(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''")
    );

    // 抓取进度来自另一张表 —— 换个 item_type 字符串就会静默归零，所以单独钉住
    for (field, item_type, status) in [
        (s.movies_scraped_ok, "movie", 200i64),
        (s.movies_scraped_404, "movie", 404),
        (s.movies_failed_500, "movie", 500),
        (s.performers_scraped_ok, "performer", 200),
        (s.performers_scraped_404, "performer", 404),
    ] {
        let expected: i64 = tx
            .query_row(
                "SELECT count(*) FROM scrape_progress WHERE item_type = ?1 AND status = ?2",
                params![item_type, status],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(field, expected, "{} / {} 的进度计数", item_type, status);
    }

    assert_eq!(
        s.translation_total,
        count(&tx, "SELECT count(*) FROM movies WHERE description IS NOT NULL AND trim(description) != ''")
    );
    assert_eq!(
        s.translation_done,
        count(&tx, "SELECT count(*) FROM movies WHERE description_zh IS NOT NULL AND trim(description_zh) != ''")
    );
    // pending 是算出来的，不是查出来的 —— 别让它变成负数
    assert_eq!(
        s.translation_pending,
        (s.translation_total - s.translation_done - s.translation_failed).max(0)
    );
    // Tauri 版从不调翻译 API
    assert!(!s.translation_configured);
}

// ---------------------------------------------------------------- 影片列表

#[test]
fn movie_list_total_matches_sql_and_respects_paging() {
    let conn = db();
    let tx = snapshot(&conn);

    let all = queries::movies::get_movies(&tx, None, None, None).unwrap();
    assert_eq!(all.total, count(&tx, "SELECT count(*) FROM movies"));
    assert_eq!(all.items.len(), 24, "默认每页 24");

    let p2 = queries::movies::get_movies(&tx, None, Some(2), Some(10)).unwrap();
    assert_eq!(p2.total, all.total, "翻页不该改变总数");
    assert_eq!(p2.items.len(), 10);
    assert_ne!(p2.items[0].id, all.items[0].id);

    // 页码下限 1、每页上限 100
    let clamped = queries::movies::get_movies(&tx, None, Some(0), Some(9999)).unwrap();
    assert_eq!(clamped.items.len(), 100);
    assert_eq!(movie_ids(&clamped)[0], movie_ids(&all)[0], "页码 0 应当被钳到第 1 页");
}

/// 排序不只看「有序」，而是比对完整 id 序列（含 ORDER BY / LIMIT），
/// 这样默认排序、year_asc、title_asc 各自走没走对分支都能钉住。
#[test]
fn movie_list_order_matches_sql() {
    let conn = db();
    let tx = snapshot(&conn);

    let default_page = queries::movies::get_movies(&tx, None, Some(1), Some(24)).unwrap();
    assert_eq!(
        movie_ids(&default_page),
        ids(&tx, "SELECT m.id FROM movies m ORDER BY m.release_year DESC, m.id DESC LIMIT 24")
    );

    let f = FilterArgs { sort_by: Some("year_asc".into()), ..Default::default() };
    let asc = queries::movies::get_movies(&tx, Some(f), Some(1), Some(50)).unwrap();
    assert_eq!(
        movie_ids(&asc),
        ids(&tx, "SELECT m.id FROM movies m ORDER BY m.release_year ASC, m.id ASC LIMIT 50")
    );

    let f = FilterArgs { sort_by: Some("title_asc".into()), ..Default::default() };
    let by_title = queries::movies::get_movies(&tx, Some(f), Some(1), Some(50)).unwrap();
    assert_eq!(
        movie_ids(&by_title),
        ids(&tx, "SELECT m.id FROM movies m ORDER BY m.title ASC LIMIT 50")
    );
}

/// 缺陷 #3 的后端回归：导演筛选必须走关联表，否则多导演影片永远匹配不上。
#[test]
fn director_filter_reads_the_junction_table() {
    let conn = db();
    let tx = snapshot(&conn);
    let name = "Chi Chi LaRue";

    let f = FilterArgs { director: Some(name.into()), ..Default::default() };
    let res = queries::movies::get_movies(&tx, Some(f), Some(1), Some(5)).unwrap();

    let expected = count1(
        &tx,
        "SELECT count(*) FROM movies m WHERE EXISTS (SELECT 1 FROM movie_directors md \
         JOIN directors d ON d.id = md.director_id WHERE md.movie_id = m.id AND d.name = ?1) \
         OR m.director_name = ?1",
        name,
    );
    assert_eq!(res.total, expected);
    assert!(res.total > 100, "{} 的作品数不合理：{}", name, res.total);

    // 关联表这条路必须比老的「整串精确匹配」查得更宽，否则就是没生效
    let legacy_only = count1(&tx, "SELECT count(*) FROM movies WHERE director_name = ?1", name);
    assert!(
        res.total > legacy_only,
        "关联表没带来任何增量：{} vs {}",
        res.total,
        legacy_only
    );
}

/// 搜索里的导演分支：粘连成整串的 director_name 搜不到，关联表能搜到。
#[test]
fn search_also_matches_through_the_director_roster() {
    let conn = db();
    let tx = snapshot(&conn);
    let q = "Corbin Fisher";

    let f = FilterArgs { query: Some(q.into()), ..Default::default() };
    let res = queries::movies::get_movies(&tx, Some(f), Some(1), Some(5)).unwrap();

    let expected: i64 = tx
        .query_row(
            "SELECT count(*) FROM movies m WHERE (m.title LIKE ?1 OR m.studio_name LIKE ?1 \
             OR m.director_name LIKE ?1 OR m.description_zh LIKE ?1 OR m.id = ?2 \
             OR m.title_zh LIKE ?1 \
             OR EXISTS (SELECT 1 FROM movie_directors md JOIN directors d ON d.id = md.director_id \
             WHERE md.movie_id = m.id AND d.name LIKE ?1))",
            params![format!("%{}%", q), -1i64],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(res.total, expected);
    assert!(res.total > 0);
}

/// 中文片名能被搜到。
///
/// 真库上没法验证这条：测试是只读打开的，而 `title_zh` 现在整列都是 NULL（第一
/// 批片名要等阶段 7 才落库）。所以这里自建一个内存库，把中文片名塞进去，验的是
/// **查询构造**而不是真库内容。
///
/// 走不了 movies_fts：那个索引是 unicode61，不切分 CJK，整串中文是一个 token。
#[test]
fn search_reaches_chinese_titles_through_like() {
    let conn = Connection::open_in_memory().unwrap();
    conn.execute_batch(
        "CREATE TABLE movies (id INTEGER PRIMARY KEY, title TEXT, studio_id TEXT,
             studio_name TEXT, release_year INTEGER, duration_mins INTEGER,
             category TEXT, rating REAL, movie_type TEXT, description TEXT,
             description_zh TEXT, cover_icon TEXT, cover_full TEXT, covers_json TEXT,
             director_id INTEGER, director_name TEXT, title_zh TEXT);
         CREATE TABLE directors (id INTEGER PRIMARY KEY, name TEXT);
         CREATE TABLE movie_directors (movie_id INTEGER, director_id INTEGER, position INTEGER);
         CREATE TABLE performers (id INTEGER PRIMARY KEY, name TEXT, image_url TEXT);
         CREATE TABLE movie_performers (movie_id INTEGER, performer_id INTEGER, performer_name TEXT);
         CREATE TABLE episodes (id INTEGER PRIMARY KEY, movie_id INTEGER, title TEXT,
             episode_number INTEGER, duration_mins INTEGER, cover_icon TEXT, cover_full TEXT);
         INSERT INTO movies (id, title, title_zh, description_zh, category) VALUES
             (1, 'Police Story',  '警察故事', NULL, 'Wrestling'),
             (2, 'Creamy Ranch',  NULL,      NULL, 'Wrestling'),
             (3, 'Nutt Crackers', '胡桃夹精', NULL, 'Solos');",
    )
    .unwrap();

    let found = |q: &str| {
        let f = FilterArgs { query: Some(q.into()), ..Default::default() };
        queries::movies::get_movies(&conn, Some(f), Some(1), Some(50)).unwrap().total
    };

    assert_eq!(found("警察"), 1, "中文片名搜不到 —— title_zh 没进搜索谓词");
    assert_eq!(found("胡桃夹精"), 1);
    assert_eq!(found("Police"), 1, "英文标题这条路不该受影响");
    assert_eq!(found("不存在的词"), 0);

    // 谓词是「整串对每一列各做一次 LIKE」，不是分词：没有哪一列同时含有
    // "Police" 和 "警察"，所以混着写搜不到。这是既有行为，不是这次引入的 ——
    // 记下来是为了别把它当成回归。
    assert_eq!(found("Police 警察"), 0);

    // 浏览器版（server.py）多一条 FTS 分支，而 movies_fts 是 unicode61、不切分
    // CJK：纯中文查询 MATCH 不到行就落到 LIKE 分支，但**混着写的查询可能命中
    // FTS 分支**，那条路上没加 title_zh 就永远搜不到中文。那条分支 Rust 这边
    // 没有，测不到，只能在 server.py 的两处一起改（已改）。
}

#[test]
fn movie_year_range_filter_excludes_null_years() {
    let conn = db();
    let tx = snapshot(&conn);

    let f = FilterArgs { year_min: Some(2000), year_max: Some(2010), ..Default::default() };
    let res = queries::movies::get_movies(&tx, Some(f), Some(1), Some(50)).unwrap();

    assert_eq!(
        movie_ids(&res),
        ids(
            &tx,
            "SELECT m.id FROM movies m WHERE m.release_year >= 2000 AND m.release_year <= 2010 \
             ORDER BY m.release_year DESC, m.id DESC LIMIT 50"
        )
    );
    for m in &res.items {
        let y = m.release_year.expect("年份区间筛选不该放行 NULL 年份");
        assert!((2000..=2010).contains(&y), "{} 的年份 {} 越界", m.title, y);
    }
}

// ---------------------------------------------------------------- 影片详情

/// 缺陷 #1 的数据侧：分集、导演名单、演员表都要真的挂在详情上。
#[test]
fn movie_detail_carries_episodes_directors_and_cast() {
    let conn = db();
    let tx = snapshot(&conn);

    let m = queries::movies::get_movie_detail(&tx, 59743).unwrap().expect("59743 在库里");

    let eps = m.episodes.as_ref().expect("episodes 字段必须有（哪怕是空数组）");
    assert_eq!(
        eps.len() as i64,
        count1(&tx, "SELECT count(*) FROM episodes WHERE movie_id = ?1", 59743)
    );
    assert!(!eps.is_empty(), "59743 是缺陷 #1 的复现样本，分集不该为空");

    // 详情页按这个顺序渲染，ordinal 就是这个顺序 —— 顺序错了 ordinal 就错了
    assert_eq!(
        eps.iter().map(|e| e.id).collect::<Vec<_>>(),
        ids(&tx, "SELECT e.id FROM episodes e WHERE e.movie_id = 59743 ORDER BY e.id ASC")
    );
    for e in eps {
        assert_eq!(e.movie_id, 59743);
        assert_eq!(e.studio_name.as_deref(), m.studio_name.as_deref(), "分集的片商来自父影片");
    }

    let roster = m.directors.as_ref().expect("directors 字段必须有");
    assert_eq!(
        roster.iter().map(|d| d.id).collect::<Vec<_>>(),
        ids(
            &tx,
            "SELECT d.id FROM movie_directors md JOIN directors d ON d.id = md.director_id \
             WHERE md.movie_id = 59743 ORDER BY md.position ASC, d.id ASC"
        )
    );

    let cast = m.performers.as_ref().expect("performers 字段必须有");
    assert_eq!(
        cast.iter().map(|p| p.id).collect::<Vec<_>>(),
        ids(&tx, "SELECT performer_id FROM movie_performers WHERE movie_id = 59743")
    );
    // 演员名来自 performers 表，缺档时回退到 movie_performers 里记的名字
    for p in cast {
        let expected: String = tx
            .query_row(
                "SELECT COALESCE(p.name, mp.performer_name) FROM movie_performers mp \
                 LEFT JOIN performers p ON p.id = mp.performer_id \
                 WHERE mp.movie_id = ?1 AND mp.performer_id = ?2",
                params![59743, p.id],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(p.name, expected);
    }
}

#[test]
fn movie_detail_of_unknown_id_is_none_not_an_error() {
    let conn = db();
    let tx = snapshot(&conn);
    assert!(queries::movies::get_movie_detail(&tx, -1).unwrap().is_none());
    assert!(queries::movies::get_movie_detail(&tx, 999_999_999).unwrap().is_none());
}

/// 列表行带演员表和导演名单（卡片要用），但不带分集（只有详情才查）。
#[test]
fn movie_list_rows_carry_cast_and_roster_but_not_episodes() {
    let conn = db();
    let tx = snapshot(&conn);
    let res = queries::movies::get_movies(&tx, None, Some(1), Some(10)).unwrap();

    for m in &res.items {
        let cast = m.performers.as_ref().expect("列表行也要有演员表");
        assert_eq!(
            cast.iter().map(|p| p.id).collect::<Vec<_>>(),
            ids(&tx, &format!("SELECT performer_id FROM movie_performers WHERE movie_id = {}", m.id)),
            "影片 {} 的演员名单",
            m.id
        );
        let roster = m.directors.as_ref().expect("列表行也要有导演名单");
        assert_eq!(
            roster.iter().map(|d| d.id).collect::<Vec<_>>(),
            ids(
                &tx,
                &format!(
                    "SELECT d.id FROM movie_directors md JOIN directors d ON d.id = md.director_id \
                     WHERE md.movie_id = {} ORDER BY md.position ASC, d.id ASC",
                    m.id
                )
            ),
        );
        assert!(m.episodes.is_none(), "列表行不该带分集");
    }
}

// ---------------------------------------------------------------- 演员

/// 缺陷 #6 的后端回归：作品数 = 影片数 + 分集数，排序与显示同口径。
#[test]
fn performer_works_count_is_films_plus_scenes() {
    let conn = db();
    let tx = snapshot(&conn);
    let f = PerformerFilterArgs { sort_by: Some("movies_desc".into()), ..Default::default() };
    let res = queries::performers::get_performers(&tx, Some(f), Some(1), Some(20)).unwrap();

    assert_eq!(res.items.len(), 20);
    let mut prev = i64::MAX;
    for p in &res.items {
        let films = count1(&tx, "SELECT count(*) FROM movie_performers WHERE performer_id = ?1", p.id);
        let scenes = count1(&tx, "SELECT count(*) FROM episode_performers WHERE performer_id = ?1", p.id);
        assert_eq!(p.movies_count, Some(films), "演员 {}（{}）的影片数", p.id, p.name);
        assert_eq!(p.works_count, Some(films + scenes), "演员 {}（{}）的作品数", p.id, p.name);
        assert!(p.works_count.unwrap() <= prev, "作品数排序必须单调不增");
        prev = p.works_count.unwrap();
    }
}

/// 排序分支本身：比对完整 id 序列。
#[test]
fn performer_list_order_matches_sql() {
    let conn = db();
    let tx = snapshot(&conn);

    let f = PerformerFilterArgs { sort_by: Some("movies_desc".into()), ..Default::default() };
    let desc = queries::performers::get_performers(&tx, Some(f), Some(1), Some(25)).unwrap();
    assert_eq!(
        desc.items.iter().map(|p| p.id).collect::<Vec<_>>(),
        ids(&tx, &format!("SELECT p.id FROM performers p ORDER BY {} DESC, p.id DESC LIMIT 25", WORKS_EXPR))
    );

    let f = PerformerFilterArgs { sort_by: Some("movies_asc".into()), ..Default::default() };
    let asc = queries::performers::get_performers(&tx, Some(f), Some(1), Some(25)).unwrap();
    assert_eq!(
        asc.items.iter().map(|p| p.id).collect::<Vec<_>>(),
        ids(&tx, &format!("SELECT p.id FROM performers p ORDER BY {} ASC, p.id ASC LIMIT 25", WORKS_EXPR))
    );

    let f = PerformerFilterArgs { sort_by: Some("name_asc".into()), ..Default::default() };
    let by_name = queries::performers::get_performers(&tx, Some(f), Some(1), Some(25)).unwrap();
    assert_eq!(
        by_name.items.iter().map(|p| p.id).collect::<Vec<_>>(),
        ids(&tx, "SELECT p.id FROM performers p ORDER BY p.name ASC LIMIT 25")
    );
}

/// 分集多的演员要靠分集排上来 —— 这正是缺陷 #6 的原话。
#[test]
fn performers_who_work_mostly_in_scenes_sort_by_the_combined_count() {
    let conn = db();
    let tx = snapshot(&conn);

    let (pid, films, scenes): (i64, i64, i64) = tx
        .query_row(
            "SELECT p.id, \
                    (SELECT count(*) FROM movie_performers mp WHERE mp.performer_id = p.id), \
                    (SELECT count(*) FROM episode_performers ep WHERE ep.performer_id = p.id) \
             FROM performers p \
             WHERE (SELECT count(*) FROM episode_performers ep WHERE ep.performer_id = p.id) \
                 > (SELECT count(*) FROM movie_performers mp WHERE mp.performer_id = p.id) \
             ORDER BY (SELECT count(*) FROM episode_performers ep WHERE ep.performer_id = p.id) DESC \
             LIMIT 1",
            [],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
        .expect("库里应该有这样的演员");

    // 按 id 搜会连带名字里含这串数字的人，所以在结果里找目标
    let f = PerformerFilterArgs { query: Some(pid.to_string()), ..Default::default() };
    let res = queries::performers::get_performers(&tx, Some(f), Some(1), Some(100)).unwrap();
    let p = res.items.iter().find(|p| p.id == pid).expect("按 id 应该能搜到自己");

    assert_eq!(p.movies_count, Some(films));
    assert_eq!(p.works_count, Some(films + scenes));
    assert!(p.works_count.unwrap() > p.movies_count.unwrap(), "分集必须把总数抬上去");
}

#[test]
fn min_movies_filter_uses_works_count() {
    let conn = db();
    let tx = snapshot(&conn);
    let min = 400;

    let f = PerformerFilterArgs { min_movies: Some(min), ..Default::default() };
    let res = queries::performers::get_performers(&tx, Some(f), Some(1), Some(100)).unwrap();

    for p in &res.items {
        assert!(p.works_count.unwrap() >= min, "演员 {}（{}）不该出现", p.id, p.name);
    }
    assert_eq!(
        res.total,
        count1(&tx, &format!("SELECT count(*) FROM performers p WHERE {} >= ?1", WORKS_EXPR), min)
    );
    // 筛选与排序同口径：第一页就是「作品数 >= min」里作品最多的那批
    assert_eq!(
        res.items.iter().map(|p| p.id).collect::<Vec<_>>(),
        ids(
            &tx,
            &format!(
                "SELECT p.id FROM performers p WHERE {} >= {} ORDER BY {} DESC, p.id DESC LIMIT 100",
                WORKS_EXPR, min, WORKS_EXPR
            )
        )
    );
}

#[test]
fn performer_detail_counts_its_own_loaded_lists() {
    let conn = db();
    let tx = snapshot(&conn);

    let pid = count(
        &tx,
        "SELECT (SELECT performer_id FROM episode_performers GROUP BY performer_id \
         ORDER BY count(*) DESC LIMIT 1)",
    );

    let p = queries::performers::get_performer_detail(&tx, pid).unwrap().expect("演员在库里");
    let movies = p.movies.as_ref().expect("movies 字段必须有");
    let eps = p.episodes.as_ref().expect("episodes 字段必须有");

    assert_eq!(p.movies_count, Some(movies.len() as i64));
    assert_eq!(p.episodes_count, Some(eps.len() as i64));
    assert_eq!(p.works_count, Some(movies.len() as i64 + eps.len() as i64));
    assert!(!eps.is_empty(), "挑的就是分集最多的演员");

    // 明细页的影片/分集确实都是这个演员的（与实现同走 JOIN）
    assert_eq!(
        movies.len() as i64,
        count1(
            &tx,
            "SELECT count(*) FROM movies m JOIN movie_performers mp ON m.id = mp.movie_id \
             WHERE mp.performer_id = ?1",
            pid
        )
    );
    assert_eq!(
        eps.len() as i64,
        count1(
            &tx,
            "SELECT count(*) FROM episode_performers ep JOIN episodes e ON e.id = ep.episode_id \
             WHERE ep.performer_id = ?1",
            pid
        )
    );
    // 影片按年份降序
    assert_eq!(
        movies.iter().map(|m| m.id).collect::<Vec<_>>(),
        ids(
            &tx,
            &format!(
                "SELECT m.id FROM movies m JOIN movie_performers mp ON m.id = mp.movie_id \
                 WHERE mp.performer_id = {} ORDER BY m.release_year DESC, m.id DESC",
                pid
            )
        )
    );

    // 属性也要跟着出来（多值属性已拆成 list）
    let attrs = p.attributes.as_ref().expect("attributes 字段必须有");
    let mut expected: HashMap<String, Vec<String>> = HashMap::new();
    for (api, column) in FACET_COLUMNS {
        let raw: Option<String> = tx
            .query_row(
                &format!("SELECT {} FROM performers WHERE id = ?1", column),
                params![pid],
                |r| r.get(0),
            )
            .unwrap();
        let values = split_attr(raw.as_deref().unwrap_or(""));
        if !values.is_empty() {
            expected.insert(api.to_string(), values);
        }
    }
    assert_eq!(attrs, &expected);
}

/// 卡片上的作品数（列表页）与详情页显示的作品数必须是同一个数 —— 这正是缺陷 #6 的诉求。
#[test]
fn performer_detail_works_count_agrees_with_the_list_card() {
    let conn = db();
    let tx = snapshot(&conn);

    // 取作品数最多的演员：按作品数降序，他一定在列表第一页，不用碰运气翻页
    let pid = count(
        &tx,
        &format!("SELECT (SELECT p.id FROM performers p ORDER BY {} DESC, p.id DESC LIMIT 1)", WORKS_EXPR),
    );

    let f = PerformerFilterArgs { sort_by: Some("movies_desc".into()), ..Default::default() };
    let list = queries::performers::get_performers(&tx, Some(f), Some(1), Some(24)).unwrap();
    let card = list.items.iter().find(|p| p.id == pid).expect("作品最多的演员应当在第一页");

    let detail = queries::performers::get_performer_detail(&tx, pid).unwrap().unwrap();

    assert_eq!(card.works_count, detail.works_count, "卡片与详情页的作品数不一致");
    assert_eq!(card.movies_count, detail.movies_count);
}

#[test]
fn performer_detail_of_unknown_id_is_none_not_an_error() {
    let conn = db();
    let tx = snapshot(&conn);
    assert!(queries::performers::get_performer_detail(&tx, -1).unwrap().is_none());
}

/// 八个属性列的服务端聚合 —— 用独立的重新计数来核对（拆分口径写两遍，
/// 实现里漏掉一种 `<br>` 写法这里就会现形）。
#[test]
fn performer_facets_match_an_independent_recount() {
    let conn = db();
    let tx = snapshot(&conn);
    let f = queries::performers::get_performer_facets(&tx).unwrap();

    assert_eq!(f.total, count(&tx, "SELECT count(*) FROM performers"));
    assert_eq!(
        f.with_image,
        count(&tx, "SELECT count(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''")
    );

    let columns: Vec<&str> = FACET_COLUMNS.iter().map(|(_, col)| *col).collect();
    let mut stmt = tx.prepare(&format!("SELECT {} FROM performers", columns.join(", "))).unwrap();
    let rows = stmt
        .query_map([], |r| {
            let mut out: Vec<Vec<String>> = Vec::with_capacity(FACET_COLUMNS.len());
            for i in 0..FACET_COLUMNS.len() {
                out.push(split_attr(r.get::<usize, Option<String>>(i)?.as_deref().unwrap_or("")));
            }
            Ok(out)
        })
        .unwrap();

    let mut tally: HashMap<&str, HashMap<String, i64>> = HashMap::new();
    let mut enriched = 0i64;
    for row in rows.filter_map(|r| r.ok()) {
        if row.iter().any(|v| !v.is_empty()) {
            enriched += 1;
        }
        for (idx, (api, _)) in FACET_COLUMNS.iter().enumerate() {
            for value in &row[idx] {
                *tally.entry(api).or_default().entry(value.clone()).or_insert(0) += 1;
            }
        }
    }
    assert_eq!(f.enriched, enriched);

    let mut expected: HashMap<String, Vec<(String, i64)>> = HashMap::new();
    for (api, value_counts) in tally {
        let mut values: Vec<(String, i64)> = value_counts.into_iter().collect();
        // 次数多的在前，同次数按字母 —— 与实现一致
        values.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
        expected.insert(api.to_string(), values);
    }

    let got: HashMap<String, Vec<(String, i64)>> = f
        .facets
        .iter()
        .map(|(k, v)| (k.clone(), v.iter().map(|fv| (fv.value.clone(), fv.count)).collect()))
        .collect();
    assert_eq!(got, expected);
    assert!(!expected.is_empty(), "库里应当已经有属性数据");
}

// ---------------------------------------------------------------- 分集库

/// `get_episode_library` 有 (query, sort, studio) 三个相邻的 `Option<String>`。
/// 调换任意两个都照样编译 —— 所以逐个参数验证它真的生效。
#[test]
fn episode_library_parameters_are_not_swapped() {
    let conn = db();
    let tx = snapshot(&conn);
    let from = "FROM episodes e LEFT JOIN movies m ON m.id = e.movie_id";

    // 默认排序：id 降序
    let by_id =
        queries::episodes::get_episode_library(&tx, None, None, None, None, None, Some(1), Some(25))
            .unwrap();
    assert_eq!(
        by_id.items.iter().map(|i| i.id).collect::<Vec<_>>(),
        ids(&tx, &format!("SELECT e.id {} ORDER BY e.id DESC LIMIT 25", from))
    );
    assert_eq!(by_id.total, count(&tx, "SELECT count(*) FROM episodes"));

    // sort=movie_asc：按父影片标题（注意 COLLATE NOCASE）
    let by_movie = queries::episodes::get_episode_library(
        &tx, None, Some("movie_asc".into()), None, None, None, Some(1), Some(25),
    )
    .unwrap();
    assert_eq!(
        by_movie.items.iter().map(|i| i.id).collect::<Vec<_>>(),
        ids(&tx, &format!("SELECT e.id {} ORDER BY m.title COLLATE NOCASE ASC, e.id ASC LIMIT 25", from))
    );

    // sort=year_desc
    let by_year = queries::episodes::get_episode_library(
        &tx, None, Some("year_desc".into()), None, None, None, Some(1), Some(25),
    )
    .unwrap();
    assert_eq!(
        by_year.items.iter().map(|i| i.id).collect::<Vec<_>>(),
        ids(&tx, &format!("SELECT e.id {} ORDER BY m.release_year DESC, e.id DESC LIMIT 25", from))
    );

    // studio：每一行都必须是这个片商
    let studio: String = tx
        .query_row(
            "SELECT m.studio_name FROM episodes e JOIN movies m ON m.id = e.movie_id \
             WHERE m.studio_name IS NOT NULL AND trim(m.studio_name) != '' \
             GROUP BY m.studio_name ORDER BY count(*) DESC LIMIT 1",
            [],
            |r| r.get(0),
        )
        .expect("库里应该有带分集的片商");
    let by_studio = queries::episodes::get_episode_library(
        &tx, None, None, Some(studio.clone()), None, None, Some(1), Some(25),
    )
    .unwrap();
    assert_eq!(
        by_studio.total,
        count1(
            &tx,
            "SELECT count(*) FROM episodes e JOIN movies m ON m.id = e.movie_id WHERE m.studio_name = ?1",
            studio.clone()
        )
    );
    assert_eq!(
        by_studio.items.iter().map(|i| i.id).collect::<Vec<_>>(),
        ids(&tx, &format!("SELECT e.id {} WHERE m.studio_name = '{}' ORDER BY e.id DESC LIMIT 25", from, sql_lit(&studio)))
    );
    for i in &by_studio.items {
        assert_eq!(i.studio_name.as_deref(), Some(studio.as_str()));
    }

    // has_zh
    let zh = queries::episodes::get_episode_library(&tx, None, None, None, Some(true), None, Some(1), Some(25))
        .unwrap();
    assert_eq!(
        zh.total,
        count(
            &tx,
            "SELECT count(*) FROM episodes e WHERE e.description_zh IS NOT NULL AND trim(e.description_zh) != ''"
        )
    );
    assert!(zh.total > 0, "库里应当已经有翻译好的分集简介");
    assert_eq!(
        zh.items.iter().map(|i| i.id).collect::<Vec<_>>(),
        ids(&tx, &format!(
            "SELECT e.id {} WHERE e.description_zh IS NOT NULL AND trim(e.description_zh) != '' \
             ORDER BY e.id DESC LIMIT 25",
            from
        ))
    );

    // has_performers：卡片的演员表要真的挂上去
    let cast = queries::episodes::get_episode_library(&tx, None, None, None, None, Some(true), Some(1), Some(25))
        .unwrap();
    assert!(cast.total > 0, "库里应当已经有带演员表的分集");
    assert_eq!(
        cast.items.iter().map(|i| i.id).collect::<Vec<_>>(),
        ids(&tx, &format!(
            "SELECT e.id {} WHERE EXISTS (SELECT 1 FROM episode_performers ep WHERE ep.episode_id = e.id) \
             ORDER BY e.id DESC LIMIT 25",
            from
        ))
    );
    for i in &cast.items {
        assert!(!i.performers.is_empty(), "分集 {} 的演员表是空的", i.id);
        let expected: Vec<(i64, String)> = {
            let mut s = tx
                .prepare(
                    "SELECT performer_id, performer_name FROM episode_performers \
                     WHERE episode_id = ?1 ORDER BY performer_id",
                )
                .unwrap();
            let rows = s.query_map(params![i.id], |r| Ok((r.get(0)?, r.get(1)?))).unwrap();
            rows.filter_map(|r| r.ok()).collect()
        };
        assert_eq!(
            i.performers.iter().map(|c| (c.id, c.name.clone())).collect::<Vec<_>>(),
            expected,
            "分集 {} 的演员名单或顺序不对",
            i.id
        );
    }

    // query：搜片名/简介
    let q = "Episode";
    let by_q =
        queries::episodes::get_episode_library(&tx, Some(q.into()), None, None, None, None, Some(1), Some(25)).unwrap();
    assert_eq!(
        by_q.total,
        count1(
            &tx,
            "SELECT count(*) FROM episodes e LEFT JOIN movies m ON m.id = e.movie_id \
             WHERE (m.title LIKE ?1 ESCAPE '\\' OR e.description LIKE ?1 ESCAPE '\\' \
             OR e.description_zh LIKE ?1 ESCAPE '\\')",
            format!("%{}%", q)
        )
    );
}

/// ordinal / count 是详情页「第 N 集 / 共 M 集」的依据，算错就显示错。
#[test]
fn episode_ordinal_and_count_match_sql() {
    let conn = db();
    let tx = snapshot(&conn);
    let page = queries::episodes::get_episode_library(&tx, None, None, None, None, None, Some(1), Some(20))
        .unwrap();

    for i in &page.items {
        assert_eq!(
            i.episode_ordinal,
            {
                let v: i64 = tx
                    .query_row(
                        "SELECT count(*) FROM episodes e2 WHERE e2.movie_id = ?1 AND e2.id <= ?2",
                        params![i.movie_id, i.id],
                        |r| r.get(0),
                    )
                    .unwrap();
                v
            },
            "分集 {} 的序号",
            i.id
        );
        assert_eq!(
            i.episode_count,
            count1(&tx, "SELECT count(*) FROM episodes e2 WHERE e2.movie_id = ?1", i.movie_id),
            "分集 {} 所属影片的总集数",
            i.id
        );
        assert!(i.episode_ordinal >= 1 && i.episode_ordinal <= i.episode_count);
    }
}

// ---------------------------------------------------------------- 片商

#[test]
fn studio_and_category_lists_match_sql() {
    let conn = db();
    let tx = snapshot(&conn);

    let studios = queries::studios::get_studios(&tx).unwrap();
    assert_eq!(
        studios,
        strings(
            &tx,
            "SELECT studio_name FROM movies \
             WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
             GROUP BY studio_name ORDER BY count(*) DESC LIMIT 200"
        )
    );
    assert_eq!(studios.len(), 200, "库里的片商数超过上限，应当被截到 200");

    // 分类不再是「GROUP BY 原始值」的直接映射：原始值有 74 个，其中 21 个是
    // 含字面量 `<br />` 的组合串，界面上会渲染成一个带标签的 chip。所以这里
    // 不再比对那条 SQL，改为断言折叠后必须成立的性质 —— 用 Python 侧的
    // collapse_categories 做逐项对拍的是 category_parity.rs。
    let cats = queries::movies::get_categories(&tx).unwrap();
    assert!(!cats.is_empty());
    assert!(
        cats.iter().all(|c| !c.contains('<')),
        "chip 里漏出了原始分隔符：{:?}",
        cats.iter().filter(|c| c.contains('<')).collect::<Vec<_>>()
    );
    assert!(cats.iter().all(|c| !c.trim().is_empty()), "有空 chip");

    // 每个 chip 的影片数，必须等于按原始值拆词后加出来的权重。这条把**两条不同
    // 的代码路径**钉在一起：折叠走 explode_attr，计数走 facet_match_sql，任何
    // 一边漏词、重复计词、或只认部分分隔符写法，这里都会对不上。
    let mut expect: HashMap<String, i64> = HashMap::new();
    for (raw, n) in raw_category_counts(&tx) {
        for term in gevi_core::sql::explode_attr(Some(raw)).into_iter().collect::<std::collections::BTreeSet<_>>() {
            *expect.entry(term).or_insert(0) += n;
        }
    }

    assert_eq!(cats.len(), expect.len(), "chip 数与拆词后的原子词数不符");
    for c in &cats {
        let f = FilterArgs { category: Some(c.clone()), ..Default::default() };
        let res = queries::movies::get_movies(&tx, Some(f), Some(1), Some(1)).unwrap();
        // 点得出影片：「只出现在组合值里」的词在词元匹配之前会渲染成一个点了没
        // 反应的 chip。（今天库里没有这种词，所以这条是防回归而不是抓现行。）
        assert!(res.total > 0, "chip「{}」点下去一部影片都没有", c);
        assert_eq!(
            res.total,
            *expect.get(c).unwrap_or(&-1),
            "chip「{}」的影片数与拆词权重不符",
            c
        );
    }
}

/// 分类原始值及其影片数，按 count 降序 —— 折叠的输入。
fn raw_category_counts(conn: &Connection) -> Vec<(String, i64)> {
    let mut stmt = conn
        .prepare(
            "SELECT category, count(*) FROM movies \
             WHERE category IS NOT NULL AND trim(category) != '' GROUP BY category",
        )
        .unwrap();
    let rows = stmt.query_map([], |r| Ok((r.get(0)?, r.get(1)?))).unwrap();
    rows.filter_map(|r| r.ok()).collect()
}

#[test]
fn studio_library_counts_match_sql() {
    let conn = db();
    let tx = snapshot(&conn);
    let lib = queries::studios::get_studio_library(&tx, None, None, Some(1), Some(10)).unwrap();

    assert_eq!(lib.items.len(), 10);
    for s in &lib.items {
        assert_eq!(
            s.works_count,
            count1(&tx, "SELECT count(DISTINCT m.id) FROM movies m WHERE m.studio_name = ?1", s.name.clone()),
            "片商 {} 的作品数",
            s.name
        );
        assert_eq!(
            s.episodes_count,
            count1(
                &tx,
                "SELECT count(*) FROM episodes e JOIN movies m ON m.id = e.movie_id WHERE m.studio_name = ?1",
                s.name.clone()
            ),
            "片商 {} 的分集数",
            s.name
        );
    }

    assert_eq!(
        lib.total,
        count(
            &tx,
            "SELECT count(DISTINCT studio_name) FROM movies \
             WHERE studio_name IS NOT NULL AND trim(studio_name) != ''"
        )
    );

    // 默认排序：作品数降序，同数按名字（NOCASE）
    assert_eq!(
        lib.items.iter().map(|s| s.name.clone()).collect::<Vec<_>>(),
        strings(
            &tx,
            "SELECT m.studio_name FROM movies m LEFT JOIN episodes e ON e.movie_id = m.id \
             WHERE m.studio_name IS NOT NULL AND trim(m.studio_name) != '' \
             GROUP BY m.studio_name \
             ORDER BY count(DISTINCT m.id) DESC, m.studio_name COLLATE NOCASE ASC LIMIT 10"
        )
    );

    // name_asc 走的是另一个分支
    let by_name = queries::studios::get_studio_library(&tx, None, Some("name_asc".into()), Some(1), Some(10)).unwrap();
    assert_eq!(
        by_name.items.iter().map(|s| s.name.clone()).collect::<Vec<_>>(),
        strings(
            &tx,
            "SELECT m.studio_name FROM movies m \
             WHERE m.studio_name IS NOT NULL AND trim(m.studio_name) != '' \
             GROUP BY m.studio_name ORDER BY m.studio_name COLLATE NOCASE ASC LIMIT 10"
        )
    );
}

#[test]
fn studio_works_matches_sql() {
    let conn = db();
    let tx = snapshot(&conn);
    let name: String = tx
        .query_row(
            "SELECT studio_name FROM movies WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
             GROUP BY studio_name ORDER BY count(*) DESC LIMIT 1",
            [],
            |r| r.get(0),
        )
        .unwrap();

    let w = queries::studios::get_studio_works(&tx, name.clone()).unwrap();
    assert_eq!(w.studio_name, name);
    assert_eq!(
        w.movies_count,
        count1(&tx, "SELECT count(*) FROM movies WHERE studio_name = ?1", name.clone())
    );
    assert_eq!(w.movies_count, w.movies.len() as i64);
    assert!(w.movies_count > 0);
    assert_eq!(
        w.episodes_count,
        count1(
            &tx,
            "SELECT count(*) FROM episodes e JOIN movies m ON m.id = e.movie_id WHERE m.studio_name = ?1",
            name.clone()
        )
    );
    assert_eq!(w.episodes_count, w.episodes.len() as i64);

    // 影片按年份降序
    assert_eq!(
        w.movies.iter().map(|m| m.id).collect::<Vec<_>>(),
        ids(&tx, &format!(
            "SELECT m.id FROM movies m WHERE m.studio_name = '{}' ORDER BY m.release_year DESC, m.id DESC",
            sql_lit(&name)
        ))
    );
    for m in &w.movies {
        assert_eq!(m.studio_name.as_deref(), Some(name.as_str()));
    }
}

// ---------------------------------------------------------------- 收藏

#[test]
fn favorites_counts_match_sql() {
    let conn = db();
    let tx = snapshot(&conn);
    let f = queries::favorites::get_favorites(&tx).unwrap();

    // movies / performers / episodes 走 INNER JOIN，所以收藏了但实体已不在的行
    // 不会出现在结果里 —— 对照 SQL 必须同样 JOIN，否则会误判成丢数据。
    for (etype, items, join) in [
        ("movie", &f.movie, "JOIN movies x ON x.id = CAST(f.entity_key AS INTEGER)"),
        ("performer", &f.performer, "JOIN performers x ON x.id = CAST(f.entity_key AS INTEGER)"),
        ("episode", &f.episode, "JOIN episodes x ON x.id = CAST(f.entity_key AS INTEGER)"),
        ("studio", &f.studio, ""),
        ("director", &f.director, ""),
    ] {
        assert_eq!(f.counts[etype], items.len() as i64, "{} 的计数", etype);
        let expected = count1(
            &tx,
            &format!("SELECT count(*) FROM user_favorites f {} WHERE f.entity_type = ?1", join),
            etype,
        );
        assert_eq!(items.len() as i64, expected, "{} 的收藏条数", etype);
    }

    // 影片收藏带着卡片要用的字段
    for m in f.movie.iter().take(10) {
        assert!(m.key.parse::<i64>().is_ok(), "影片收藏的 key 应当是 id：{}", m.key);
        assert!(m.title.is_some());
        assert!(m.has_zh.is_some());
    }
    // studio/director 只有名字和作品数
    for s in f.studio.iter().take(5) {
        assert_eq!(s.name.as_deref(), Some(s.key.as_str()));
        assert_eq!(
            s.works_count,
            Some(count1(&tx, "SELECT count(*) FROM movies WHERE studio_name = ?1", s.key.clone()))
        );
    }
    // 排序：按收藏时间倒序
    let times: Vec<Option<String>> = f.movie.iter().map(|m| m.created_at.clone()).collect();
    let mut sorted = times.clone();
    sorted.sort_by(|a, b| b.cmp(a));
    assert_eq!(times, sorted, "收藏应当按时间倒序");
}

/// toggle_favorite 是「读-判断-写」，用内存库真跑一遍往返。
/// 只读连接写不了，所以这里自建一张同结构的表 —— 它只碰 user_favorites，不需要真库。
#[test]
fn toggle_favorite_round_trips_and_validates() {
    let conn = Connection::open_in_memory().unwrap();
    conn.execute_batch(
        "CREATE TABLE user_favorites (
             entity_type TEXT NOT NULL,
             entity_key TEXT NOT NULL,
             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
             PRIMARY KEY (entity_type, entity_key)
         );",
    )
    .unwrap();

    assert!(queries::favorites::toggle_favorite(&conn, "movie".into(), "42".into()).unwrap());
    assert_eq!(count(&conn, "SELECT count(*) FROM user_favorites"), 1);
    assert!(!queries::favorites::toggle_favorite(&conn, "movie".into(), "42".into()).unwrap());
    assert_eq!(count(&conn, "SELECT count(*) FROM user_favorites"), 0);

    // 前后空格要去掉再存
    assert!(queries::favorites::toggle_favorite(&conn, "movie".into(), "  7  ".into()).unwrap());
    assert_eq!(count1(&conn, "SELECT count(*) FROM user_favorites WHERE entity_key = ?1", "7"), 1);

    // 五种类型都放行
    for t in ["movie", "performer", "studio", "director", "episode"] {
        assert!(
            queries::favorites::toggle_favorite(&conn, t.into(), "1".into()).unwrap(),
            "{} 应当被接受",
            t
        );
    }

    // 校验分支：消息必须与拆分前逐字相同
    let err = queries::favorites::toggle_favorite(&conn, "bogus".into(), "1".into()).unwrap_err();
    assert_eq!(err.to_string(), "未知的收藏类型 'bogus'");
    let err = queries::favorites::toggle_favorite(&conn, "movie".into(), "   ".into()).unwrap_err();
    assert_eq!(err.to_string(), "收藏对象不能为空");
}

// ---------------------------------------------------------------- MOVIE_COLUMNS

/// `MOVIE_COLUMNS` 和 `map_movie_row` 是按**位置**对齐的，而位置错位不会编译失败。
///
/// 实测过：把 `MOVIE_COLUMNS` 里的 `m.movie_type` 和 `m.description` 换个位置，
/// 本文件其余 26 条测试**全绿** —— 那时整行字段已经串位（`description` 里躺着影片
/// 类型、`description_zh` 里躺着英文简介），只是没有一条测试读过这些标量字段。
///
/// 这一点在阶段 5 尤其要紧：`m.title_zh` 要追加到这个列表**末尾**，一旦追加进中间，
/// 后面的字段整体串位，而且是静默的 —— 标题译错在界面上永远看着是对的。
///
/// 所以这里**按列名**取期望值：`SELECT *` 的列顺序与 `MOVIE_COLUMNS` 无关，无论列表
/// 怎么重排，按名字读出来的值都不动，两边顺序一旦不一致就现形。
#[test]
fn movie_columns_and_their_reader_agree_by_name_not_by_position() {
    let conn = db();
    let tx = snapshot(&conn);

    // 挑一部「每个列都非空」的影片。全空的话下面的比较就是一堆 None == None，
    // 换没换位置都过 —— 这正是上面那次 mutation 能活下来的原因。
    let id = count(
        &tx,
        "SELECT min(id) FROM movies WHERE description_zh IS NOT NULL AND trim(description_zh) != '' \
         AND movie_type IS NOT NULL AND trim(movie_type) != '' \
         AND director_name IS NOT NULL AND trim(director_name) != '' \
         AND covers_json IS NOT NULL AND trim(covers_json) != '' \
         AND rating IS NOT NULL AND trim(rating) != '' \
         AND category IS NOT NULL AND trim(category) != '' \
         AND studio_name IS NOT NULL AND trim(studio_name) != '' \
         AND release_year IS NOT NULL AND duration_mins IS NOT NULL",
    );
    assert_ne!(
        id, 0,
        "库里找不到一部每个字段都非空的影片，这条测试会退化成全 None 的比较（等于没测）"
    );

    let m = queries::movies::get_movie_detail(&tx, id).unwrap().expect("影片在库里");

    const NAMES: [&str; 16] = [
        "id", "title", "studio_id", "studio_name", "release_year", "duration_mins", "category",
        "rating", "movie_type", "description", "description_zh", "cover_icon", "cover_full",
        "covers_json", "director_id", "director_name",
    ];
    let mut stmt = tx.prepare("SELECT * FROM movies WHERE id = ?1").unwrap();
    // 索引先取出来：下面的闭包只借用 idx，不再借用 stmt（query_row 要 &mut stmt）。
    let idx: HashMap<&str, usize> = NAMES
        .iter()
        .map(|n| {
            let i = stmt
                .column_index(n)
                .unwrap_or_else(|e| panic!("movies 表没有列 {n}: {e}"));
            (*n, i)
        })
        .collect();
    let raw: HashMap<&str, Option<String>> = stmt
        .query_row(params![id], |r| {
            let mut out = HashMap::new();
            for n in NAMES {
                let v: rusqlite::types::Value = r.get(idx[n])?;
                out.insert(
                    n,
                    match v {
                        rusqlite::types::Value::Null => None,
                        rusqlite::types::Value::Integer(i) => Some(i.to_string()),
                        rusqlite::types::Value::Real(f) => Some(f.to_string()),
                        rusqlite::types::Value::Text(t) => Some(t),
                        rusqlite::types::Value::Blob(_) => None,
                    },
                );
            }
            Ok(out)
        })
        .unwrap();
    let db_col = |n: &str| raw[n].clone();
    let num = |v: Option<i64>| v.map(|n| n.to_string());

    // (列名, 按列名读到的值, Movie 结构体里的值)。左边是独立的一份期望值。
    let pairs: Vec<(&str, Option<String>, Option<String>)> = vec![
        ("id", db_col("id"), Some(m.id.to_string())),
        ("title", db_col("title"), Some(m.title.clone())),
        ("studio_id", db_col("studio_id"), num(m.studio_id)),
        ("studio_name", db_col("studio_name"), m.studio_name.clone()),
        ("release_year", db_col("release_year"), num(m.release_year)),
        ("duration_mins", db_col("duration_mins"), num(m.duration_mins)),
        ("category", db_col("category"), m.category.clone()),
        ("rating", db_col("rating"), m.rating.clone()),
        ("movie_type", db_col("movie_type"), m.movie_type.clone()),
        ("description", db_col("description"), m.description.clone()),
        ("description_zh", db_col("description_zh"), m.description_zh.clone()),
        ("cover_icon", db_col("cover_icon"), m.cover_icon.clone()),
        ("cover_full", db_col("cover_full"), m.cover_full.clone()),
        ("director_id", db_col("director_id"), num(m.director_id)),
        ("director_name", db_col("director_name"), m.director_name.clone()),
    ];

    let mut wrong = Vec::new();
    for (name, want, got) in &pairs {
        if want != got {
            wrong.push(format!("{name}: 库={want:?} 结构体={got:?}"));
        }
    }
    assert!(
        wrong.is_empty(),
        "MOVIE_COLUMNS 的顺序与 map_movie_row 的索引已经错位：\n  {}",
        wrong.join("\n  ")
    );

    // covers 是从 covers_json 派生的，单独比一次（上面那组比的是原始串）。
    let want_covers: Option<Vec<String>> =
        db_col("covers_json").and_then(|s| serde_json::from_str(&s).ok());
    assert_eq!(m.covers, want_covers, "covers 必须由 covers_json 解析而来");
    assert!(want_covers.is_some(), "挑的这部片应当有 covers_json");
}

/// 阶段 2 顺带修掉的既有毛病：片商作品页和演员作品页各自只 SELECT 了 10 列，
/// 于是 `description_zh` 恒为 None（卡片上不亮 `中` 角标）、`director_name` 恒为 None
/// （导演行不显示）。现在两处都改用 `MOVIE_COLUMNS`。
///
/// 挑的是**确实有中文简介和导演名**的那部片：否则「字段是 None」和「列根本没被
/// SELECT」两种情况分不开，把列表改回 10 列测试照样绿。
#[test]
fn studio_and_performer_film_lists_carry_the_translation_and_the_director() {
    let conn = db();
    let tx = snapshot(&conn);

    let (id, studio): (i64, String) = tx
        .query_row(
            "SELECT m.id, m.studio_name FROM movies m \
             WHERE m.description_zh IS NOT NULL AND trim(m.description_zh) != '' \
               AND m.director_name IS NOT NULL AND trim(m.director_name) != '' \
               AND m.studio_name IS NOT NULL AND trim(m.studio_name) != '' \
               AND EXISTS (SELECT 1 FROM movie_performers mp WHERE mp.movie_id = m.id) \
             LIMIT 1",
            [],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .expect("库里应当有带中文简介、导演名、片商且挂在演员名下的影片");
    let want_zh: String = tx
        .query_row(
            "SELECT description_zh FROM movies WHERE id = ?1",
            params![id],
            |r| r.get(0),
        )
        .unwrap();

    let w = queries::studios::get_studio_works(&tx, studio).unwrap();
    let in_studio = w
        .movies
        .iter()
        .find(|m| m.id == id)
        .expect("这部片应当在它片商的作品列表里");
    assert_eq!(
        in_studio.description_zh.as_deref(),
        Some(want_zh.as_str()),
        "片商作品页拿不到中文简介（列表被截短了？）"
    );
    assert!(in_studio.director_name.is_some(), "片商作品页拿不到导演名");
    assert!(in_studio.movie_type.is_some(), "片商作品页拿不到影片类型");

    let pid = count1(
        &tx,
        "SELECT performer_id FROM movie_performers WHERE movie_id = ?1 LIMIT 1",
        id,
    );
    let p = queries::performers::get_performer_detail(&tx, pid)
        .unwrap()
        .expect("演员在库里");
    let in_perf = p
        .movies
        .as_ref()
        .unwrap()
        .iter()
        .find(|m| m.id == id)
        .expect("这部片应当在该演员的作品列表里");
    assert_eq!(
        in_perf.description_zh.as_deref(),
        Some(want_zh.as_str()),
        "演员作品页拿不到中文简介（列表被截短了？）"
    );
    assert!(in_perf.director_name.is_some(), "演员作品页拿不到导演名");
    assert!(in_perf.movie_type.is_some(), "演员作品页拿不到影片类型");
}

// ---------------------------------------------------------------- 测试自用

/// 与实现里 PERFORMER_FACETS 同义，但由测试自己声明 —— 实现改了映射关系这里会现形。
/// (API 名, SQL 列名)
const FACET_COLUMNS: [(&str, &str); 8] = [
    ("bodyType", "build"),
    ("hair", "hair"),
    ("eyes", "eyes"),
    ("skin", "skin"),
    ("bodyHair", "body_hair"),
    ("facialHair", "facial_hair"),
    ("dickSize", "dick_size"),
    ("foreskin", "foreskin"),
];

/// 独立于实现的拆分：先统一替换成分隔符再切，与实现的链式 split 是两条代码路径。
fn split_attr(raw: &str) -> Vec<String> {
    raw.replace("<br />", "\u{1}")
        .replace("<br/>", "\u{1}")
        .replace("<br>", "\u{1}")
        .split('\u{1}')
        .map(|p| p.trim())
        .filter(|p| !p.is_empty())
        .map(|p| p.to_string())
        .collect()
}

/// SQL 字符串字面量（单引号翻倍），只用于上面那些把片商名拼进 SQL 的对照查询。
fn sql_lit(s: &str) -> String {
    s.replace('\'', "''")
}
