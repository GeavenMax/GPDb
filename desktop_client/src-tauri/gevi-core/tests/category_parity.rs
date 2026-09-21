//! 分类折叠与分隔符：Rust 和 server.py 必须给出同一个答案。
//!
//! 浏览器版走 `server.py`，桌面版走 `gevi-core`，同一个库。两边只要有一边把分类
//! 折叠得不一样，用户就会看到两个不同的筛选抽屉；分隔符集只要有一边不认某种写法，
//! 同一个值在这边拆成 "Brown"、在那边还是 "Brown<br />Blond"，筛选就静默地时灵
//! 时不灵。
//!
//! 所以这里不写死期望值，而是**把同一份输入同时喂给两个实现，再比对输出**。期望
//! 值写死在测试里的话，两边一起改错也会一起通过。
//!
//! `translate.py` 不存在时跳过（这个 crate 被单独拿走的情况）；存在却没有可用的
//! python3 则**失败** —— 那种组合意味着「本来要做的对拍没做」，报绿就是撒谎。

use std::path::{Path, PathBuf};
use std::process::Command;

use rusqlite::{Connection, OpenFlags};

/// 往上找到含 `server.py` 的仓库根，与别的测试保持一致。
fn project_root() -> Option<PathBuf> {
    let mut dir = Path::new(env!("CARGO_MANIFEST_DIR"));
    for _ in 0..5 {
        if dir.join("server.py").exists() {
            return Some(dir.to_path_buf());
        }
        dir = dir.parent()?;
    }
    None
}

fn python() -> Option<String> {
    for exe in ["python3", "python"] {
        if let Ok(out) = Command::new(exe).args(["-c", "print(1)"]).output() {
            if out.status.success() {
                return Some(exe.to_string());
            }
        }
    }
    None
}

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

/// 一条请求走 stdin，一次回答走 stdout。驱动的是真函数，不是抄一份实现过来 ——
/// 抄来的那份会在实现改了之后继续通过。
const DRIVER: &str = r#"
import json, sqlite3, sys
import server
import db_manager

req = json.loads(sys.stdin.read())
out = {}

if req["op"] == "split":
    out["tokens"] = [server.split_facet_value(p) for p in req["probes"]]

elif req["op"] == "collapse":
    out["terms"] = server.collapse_categories([(r[0], r[1]) for r in req["rows"]])

elif req["op"] == "glossary":
    # 走 server.py 真正用的那两个读取函数，而不是在这里重写一份 SELECT ——
    # 重写的那份会在实现改了之后继续通过。
    db = db_manager.DatabaseManager(str(server.DB_PATH))
    try:
        out["terms"] = db.load_glossary()
        out["categories"] = db.load_category_glossary()
    finally:
        db.close()

elif req["op"] == "facet_sql":
    # 把 Python 生成的表达式丢进 SQLite 里真跑一遍。只比对 SQL 文本的话，
    # 两边写出字面不同但语义相同的表达式会被误判。probe 是整串，term 是待匹配词。
    expr = server.facet_sql("category", "m")
    conn = sqlite3.connect(":memory:")
    hits = []
    for probe, term in zip(req["probes"], req["terms"]):
        got = conn.execute(
            "SELECT " + expr.replace("m.category", "?") + " LIKE ?",
            (probe, "%|" + term + "|%"),
        ).fetchone()[0]
        hits.append(bool(got))
    out["hits"] = hits

print(json.dumps(out, ensure_ascii=False))
"#;

fn run_driver(root: &Path, py: &str, request: &str) -> serde_json::Value {
    use std::io::Write;
    let mut child = Command::new(py)
        .arg("-c")
        .arg(DRIVER)
        .current_dir(root)
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .expect("spawning python");
    child.stdin.as_mut().unwrap().write_all(request.as_bytes()).unwrap();
    let out = child.wait_with_output().unwrap();
    assert!(
        out.status.success(),
        "driver failed: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    serde_json::from_slice(&out.stdout).expect("driver printed non-JSON")
}

fn harness() -> Option<(PathBuf, String)> {
    let root = match project_root() {
        Some(r) => r,
        None => {
            eprintln!("skipping: server.py not found; this crate is outside the repo");
            return None;
        }
    };
    let py = python().expect(
        "server.py is here but no working python3 was found - the cross-implementation \
         check cannot run, and passing without running it would be a lie",
    );
    Some((root, py))
}

fn str_list(v: &serde_json::Value, key: &str) -> Vec<Vec<String>> {
    v[key]
        .as_array()
        .unwrap()
        .iter()
        .map(|row| {
            row.as_array()
                .unwrap()
                .iter()
                .map(|s| s.as_str().unwrap().to_string())
                .collect()
        })
        .collect()
}

/// 分类折叠：真库的原始值同时喂给两边，整个有序列表必须一致。
///
/// 比对**顺序**而不只是集合，因为 tiebreak 是有意义的：约 30 个词并列 count=1，
/// 只按 count 排序不是全序，浏览器的 chip 顺序会和桌面版不一样。
#[test]
fn collapse_agrees_with_python_on_the_library() {
    let Some((root, py)) = harness() else { return };

    let conn = Connection::open_with_flags(find_db(), OpenFlags::SQLITE_OPEN_READ_ONLY).unwrap();
    let mut stmt = conn
        .prepare(
            "SELECT category, count(*) FROM movies \
             WHERE category IS NOT NULL AND trim(category) != '' GROUP BY category",
        )
        .unwrap();
    let rows: Vec<(String, i64)> = stmt
        .query_map([], |r| Ok((r.get(0)?, r.get(1)?)))
        .unwrap()
        .filter_map(|r| r.ok())
        .collect();
    drop(stmt);

    assert!(rows.len() > 50, "库里的分类原始值太少，这条测试会退化成没测");

    let request = serde_json::json!({ "op": "collapse", "rows": rows }).to_string();
    let py_terms: Vec<String> = run_driver(&root, &py, &request)["terms"]
        .as_array()
        .unwrap()
        .iter()
        .map(|s| s.as_str().unwrap().to_string())
        .collect();
    let rust_terms = gevi_core::sql::collapse_categories(&rows);

    assert_eq!(
        rust_terms, py_terms,
        "Rust 与 server.py 折叠出的分类列表不一致（长度 {} vs {}）",
        rust_terms.len(),
        py_terms.len()
    );
    // 74 个原始值 → 53 个原子词。既钉住折叠真的发生了，也钉住没有多拆或少拆。
    assert!(rust_terms.len() < rows.len(), "折叠没生效，chip 数等于原始值数");
    assert!(
        !rust_terms.iter().any(|t| t.contains('<')),
        "折叠后仍有 chip 带着原始分隔符"
    );
}

/// 分隔符集：代码里的拆分和 SQL 里的替换必须认同一套写法。
///
/// 这是本文件存在的理由。此前的实际情况是三套：Rust 的 split 只认小写三种、
/// `_BR_RE` 是 `<br\s*/?>` + IGNORECASE、SQL 只认小写三种。库里恰好只有
/// `<br />`，所以三套不一致一直没暴露 —— 哪天站点改吐 `<BR />`，拆分和筛选就会
/// 各走各的。
#[test]
fn separator_sets_agree_on_every_probe() {
    let Some((root, py)) = harness() else { return };

    let probes = [
        "Brown<br />Blond",
        "Brown<br/>Blond",
        "Brown<br>Blond",
        "Brown<br />Blond<br />Dark",
        "Brown<BR />Blond",
        "Brown<Br/>Blond",
        "Brown<bR>Blond",
        // 两个空格：`\s*` 认，SQL 的 REPLACE 链表达不了。两边必须一致地不认，
        // 否则又是一个分叉点。
        "Brown<br  />Blond",
        // 不是分隔符的干扰项
        "Brown & Blond",
        "<br />",
        "   ",
    ];

    // 1. 拆分：Rust 的 token 序列 == Python 的 token 序列
    let request = serde_json::json!({ "op": "split", "probes": probes }).to_string();
    let py_tokens = str_list(&run_driver(&root, &py, &request), "tokens");
    let rust_tokens: Vec<Vec<String>> =
        probes.iter().map(|p| gevi_core::sql::explode_attr(Some(p.to_string()))).collect();
    assert_eq!(rust_tokens, py_tokens, "两边的拆词结果不一致");

    // 2. SQL 侧：Python 生成的表达式在 SQLite 里的命中，必须和 Rust 表达式一致。
    //    对每个 probe 的每个 token 都要求命中；一个 token 都不命中时要求不命中。
    let hit_probes: Vec<&str> = probes.to_vec();
    let hit_terms: Vec<String> = rust_tokens
        .iter()
        .map(|t| t.first().cloned().unwrap_or_else(|| "不存在的词".to_string()))
        .collect();
    let request = serde_json::json!({
        "op": "facet_sql", "probes": hit_probes, "terms": hit_terms,
    })
    .to_string();
    let py_hits: Vec<bool> = run_driver(&root, &py, &request)["hits"]
        .as_array()
        .unwrap()
        .iter()
        .map(|b| b.as_bool().unwrap())
        .collect();

    let scratch = Connection::open_in_memory().unwrap();
    let expr = gevi_core::sql::facet_sql("category", "m").replace("m.category", "?1");
    for (i, probe) in probes.iter().enumerate() {
        let rust_hit: bool = scratch
            .query_row(
                &format!("SELECT {} LIKE ?2", expr),
                rusqlite::params![probe, format!("%|{}|%", hit_terms[i])],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(
            rust_hit, py_hits[i],
            "probe {:?} 上两边的 SQL 表达式结论不同（匹配词 {:?}）",
            probe, hit_terms[i]
        );
    }

    // 3. 拆分与 SQL 必须自洽：拆出来的每个 token，SQL 都得能匹配到。
    //    这一条才是「代码里拆得出、SQL 里匹配不到」的直接判据。
    for (probe, tokens) in probes.iter().zip(&rust_tokens) {
        for token in tokens {
            let hit: bool = scratch
                .query_row(
                    &format!("SELECT {} LIKE ?2", expr),
                    rusqlite::params![probe, format!("%|{}|%", token)],
                    |r| r.get(0),
                )
                .unwrap();
            assert!(hit, "{:?} 拆出了 {:?}，但 SQL 匹配不到它", probe, token);
        }
    }
}

/// 词元筛选：点 `Wrestling` 要能带出存成 "Wrestling<br />J/O" 的影片。
///
/// 这不是「顺手多查几条」而是这次改动的可见后果：879 → 896。用 instr 而不是
/// LIKE，是因为 SQLite 的 LIKE 对 ASCII 不区分大小写，会把精确匹配悄悄放宽成
/// 还能命中 `|wrestling|`。
#[test]
fn the_category_filter_matches_tokens_and_stays_case_sensitive() {
    let conn = Connection::open_with_flags(find_db(), OpenFlags::SQLITE_OPEN_READ_ONLY).unwrap();

    let exact = |c: &str| -> i64 {
        conn.query_row("SELECT count(*) FROM movies WHERE category = ?1", [c], |r| r.get(0))
            .unwrap()
    };
    let token = |c: &str| -> i64 {
        conn.query_row(
            &format!(
                "SELECT count(*) FROM movies m WHERE {}",
                gevi_core::sql::facet_match_sql("category", "m")
            ),
            [c],
            |r| r.get(0),
        )
        .unwrap()
    };

    // 库里确实有组合值，否则这条测试什么也没证明
    let combos: i64 = conn
        .query_row(
            "SELECT count(*) FROM movies WHERE category LIKE '%<br%'",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert!(combos > 0, "库里没有多值分类，这条测试会退化成恒真");

    let w_exact = exact("Wrestling");
    let w_token = token("Wrestling");
    assert!(
        w_token > w_exact,
        "词元匹配没有带来任何增量：{} vs {} —— instr 没生效",
        w_token,
        w_exact
    );
    assert_eq!(
        token("wrestling"), 0,
        "小写被 LIKE 式的语义命中了，筛选比预期宽"
    );
    assert_eq!(token("Wrestlin"), 0, "部分匹配被命中了");
}

/// 词表读取：同一个库，Rust 与 server.py 必须给出同一份 {en: zh}。
///
/// 桌面版此前**根本没有**读取路径 —— `api.getGlossary()` 没有 isTauri 分支，
/// 落到 `fetch` 上、背后没有 HTTP 服务，于是静默返回 `{}`，演员属性在桌面版
/// 恒显示英文。这条测试钉住新补的那条路，以及「两张表分别读进哪个字段」。
#[test]
fn glossaries_agree_with_python_and_read_their_own_table() {
    // 1. 先在没有真库数据的情况下把「哪张表进哪个字段」钉死：真库的
    //    category_glossary 现在是空的（分类译文要等阶段 7），只比对真库的话
    //    这一半是空比 —— 把两个表读串、或者干脆都读 attr_glossary，测试照样绿。
    let scratch = Connection::open_in_memory().unwrap();
    scratch
        .execute_batch(
            "CREATE TABLE attr_glossary (en TEXT PRIMARY KEY, zh TEXT NOT NULL);
             CREATE TABLE category_glossary (term TEXT PRIMARY KEY, zh TEXT NOT NULL);
             INSERT INTO attr_glossary (en, zh) VALUES ('Swimmer', '游泳体型');
             INSERT INTO category_glossary (term, zh) VALUES ('J/O', '独自撸');",
        )
        .unwrap();
    let fixture = gevi_core::queries::glossary::get_glossaries(&scratch).unwrap();
    assert_eq!(
        fixture.terms.get("Swimmer").map(String::as_str),
        Some("游泳体型"),
        "attr_glossary 没读进 terms"
    );
    assert_eq!(
        fixture.terms.get("J/O"),
        None,
        "category_glossary 的词漏进了 terms —— 两张表读串了"
    );
    assert_eq!(
        fixture.categories.get("J/O").map(String::as_str),
        Some("独自撸"),
        "category_glossary 没读进 categories"
    );
    assert_eq!(fixture.categories.get("Swimmer"), None, "两张表读串了");

    // 2. 真库上对拍：属性词表这一半是实的（库里已有译文），分类那一半要等阶段 7。
    let Some((root, py)) = harness() else { return };
    let conn = Connection::open_with_flags(find_db(), OpenFlags::SQLITE_OPEN_READ_ONLY).unwrap();
    let rust = gevi_core::queries::glossary::get_glossaries(&conn).unwrap();

    let request = serde_json::json!({ "op": "glossary" }).to_string();
    let out = run_driver(&root, &py, &request);
    let as_map = |v: &serde_json::Value| -> std::collections::BTreeMap<String, String> {
        v.as_object()
            .unwrap()
            .iter()
            .map(|(k, v)| (k.clone(), v.as_str().unwrap().to_string()))
            .collect()
    };
    let py_terms = as_map(&out["terms"]);
    let py_categories = as_map(&out["categories"]);

    assert!(
        py_terms.len() > 30,
        "真库的属性词表只有 {} 条，这条对拍会退化成没测（跑一次设置里的「翻译术语表」？）",
        py_terms.len()
    );
    let rust_terms: std::collections::BTreeMap<String, String> =
        rust.terms.iter().map(|(k, v)| (k.clone(), v.clone())).collect();
    assert_eq!(rust_terms, py_terms, "属性词表 Rust 与 server.py 不一致");

    let rust_categories: std::collections::BTreeMap<String, String> =
        rust.categories.iter().map(|(k, v)| (k.clone(), v.clone())).collect();
    assert_eq!(
        rust_categories, py_categories,
        "分类词表 Rust 与 server.py 不一致（现在是空对空，等阶段 7 跑完才成为实证）"
    );
}
