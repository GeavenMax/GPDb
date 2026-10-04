//! Studios exist across movies and episodes.
//! Every studio query aggregates over `movies` and `episodes`.

use rusqlite::{params, Connection};

use crate::models::{Episode, Movie, StudioLibrary, StudioSummary, StudioWorks};
use crate::sql::{map_episode_row, map_movie_row, EPISODE_SQL, MOVIE_COLUMNS};
use crate::Result;

pub fn get_studios(conn: &Connection) -> Result<Vec<String>> {
    let mut stmt = conn.prepare(
        "SELECT studio_name FROM movies \
         WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
         GROUP BY studio_name ORDER BY count(*) DESC LIMIT 200"
    ).map_err(|e| e.to_string())?;

    let rows = stmt.query_map([], |r| r.get(0)).map_err(|e| e.to_string())?;
    Ok(rows.filter_map(|r| r.ok()).collect())
}

/// Studio library: every studio with its film and episode counts, paged.
///
/// Studios can appear on `movies` and/or `episodes` (including standalone episodes).
/// Both counts come out of an aggregation over movies and episodes with effective studio resolution.
pub fn get_studio_library(
    conn: &Connection,
    query: Option<String>,
    sort_by: Option<String>,
    page: Option<i64>,
    page_size: Option<i64>,
) -> Result<StudioLibrary> {
    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let has_query = query.as_ref().map(|q| !q.trim().is_empty()).unwrap_or(false);

    let mut conditions = vec![
        "s.name IS NOT NULL".to_string(),
        "trim(s.name) != ''".to_string(),
    ];
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    if let Some(q) = query {
        let q = q.trim().to_string();
        if !q.is_empty() {
            // ESCAPE so a literal % or _ typed into the search box stays literal.
            // Match against both original English name and translated Chinese name.
            conditions.push("(s.name LIKE ? ESCAPE '\\' OR st.name_zh LIKE ? ESCAPE '\\')".to_string());
            let escaped = q.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
            let param = format!("%{}%", escaped);
            params_vec.push(Box::new(param.clone()));
            params_vec.push(Box::new(param));
        }
    }
    let where_clause = format!("WHERE {}", conditions.join(" AND "));

    let sort_clause = match sort_by.as_deref() {
        Some("name_asc") => "s.name COLLATE NOCASE ASC",
        Some("episodes_desc") => {
            "episodes_count DESC, works_count DESC, s.name COLLATE NOCASE ASC"
        }
        _ => "works_count DESC, episodes_count DESC, s.name COLLATE NOCASE ASC",
    };

    let params_slice: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = if !has_query {
        // Fast path for browsing: counting the union needs no join with studios table
        conn.query_row(
            "SELECT count(*) FROM ( \
                SELECT DISTINCT studio_name AS name FROM movies WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
                UNION \
                SELECT DISTINCT studio_name AS name FROM episodes WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
            ) s",
            [],
            |r| r.get(0),
        ).map_err(|e| e.to_string())?
    } else {
        conn.query_row(
            &format!(
                "SELECT count(*) FROM ( \
                    SELECT DISTINCT studio_name AS name FROM movies WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
                    UNION \
                    SELECT DISTINCT studio_name AS name FROM episodes WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
                ) s \
                LEFT JOIN studios st ON st.name = s.name COLLATE NOCASE {}",
                where_clause
            ),
            &params_slice[..],
            |r| r.get(0),
        ).map_err(|e| e.to_string())?
    };

    let select_query = format!(
        "SELECT s.name, st.name_zh, st.description_zh, st.logo_url, st.banner_url, st.website_url, COALESCE(m.cnt, 0) AS works_count, COALESCE(e.cnt, 0) AS episodes_count \
         FROM ( \
             SELECT DISTINCT studio_name AS name FROM movies WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
             UNION \
             SELECT DISTINCT studio_name AS name FROM episodes WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
         ) s \
         LEFT JOIN studios st ON st.name = s.name COLLATE NOCASE \
         LEFT JOIN ( \
             SELECT studio_name, count(*) AS cnt \
             FROM movies \
             WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
             GROUP BY studio_name \
         ) m ON m.studio_name = s.name \
         LEFT JOIN ( \
             SELECT studio, count(DISTINCT episode_id) AS cnt \
             FROM ( \
                 SELECT e.id AS episode_id, e.studio_name AS studio \
                 FROM episodes e \
                 WHERE e.studio_name IS NOT NULL AND trim(e.studio_name) != '' \
                 UNION ALL \
                 SELECT e.id AS episode_id, m.studio_name AS studio \
                 FROM episodes e \
                 JOIN movies m ON e.movie_id = m.id \
                 WHERE m.studio_name IS NOT NULL AND trim(m.studio_name) != '' \
             ) \
             GROUP BY studio \
         ) e ON e.studio = s.name \
         {} ORDER BY {} LIMIT ? OFFSET ?",
        where_clause, sort_clause
    );

    let mut full_params = params_vec;
    full_params.push(Box::new(page_size));
    full_params.push(Box::new(offset));
    let full_slice: Vec<&dyn rusqlite::ToSql> = full_params.iter().map(|p| p.as_ref()).collect();

    let mut stmt = conn.prepare(&select_query).map_err(|e| e.to_string())?;
    let rows = stmt.query_map(&full_slice[..], |r| {
        Ok(StudioSummary {
            name: r.get(0)?,
            name_zh: r.get(1)?,
            description_zh: r.get(2)?,
            logo_url: r.get(3)?,
            banner_url: r.get(4)?,
            website_url: r.get(5)?,
            works_count: r.get(6)?,
            episodes_count: r.get(7)?,
        })
    }).map_err(|e| e.to_string())?;

    Ok(StudioLibrary {
        items: rows.filter_map(|r| r.ok()).collect(),
        total,
    })
}

/// One studio's complete works, matching `/api/studios/<name>/works`.
pub fn get_studio_works(conn: &Connection, studio_name: String) -> Result<StudioWorks> {
    let (studio_name_zh, description_zh, logo_url, banner_url, website_url, site_id): (
        Option<String>,
        Option<String>,
        Option<String>,
        Option<String>,
        Option<String>,
        Option<i64>,
    ) = conn
        .query_row(
            "SELECT name_zh, description_zh, logo_url, banner_url, website_url, site_id FROM studios WHERE name = ?1 COLLATE NOCASE OR name_zh = ?1 LIMIT 1",
            params![studio_name],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?)),
        )
        .unwrap_or((None, None, None, None, None, None));

    let (m_sql, m_params): (String, Vec<Box<dyn rusqlite::ToSql>>) = if let Some(sid) = site_id {
        (
            format!(
                "SELECT {} FROM movies m \
                 WHERE (m.studio_name = ?1 OR m.studio_id = ?2) \
                 ORDER BY m.release_year DESC, m.id DESC",
                MOVIE_COLUMNS
            ),
            vec![Box::new(studio_name.clone()), Box::new(sid)],
        )
    } else {
        (
            format!(
                "SELECT {} FROM movies m \
                 WHERE m.studio_name = ?1 \
                 ORDER BY m.release_year DESC, m.id DESC",
                MOVIE_COLUMNS
            ),
            vec![Box::new(studio_name.clone())],
        )
    };

    let mut m_stmt = conn.prepare(&m_sql).map_err(|e| e.to_string())?;
    let m_slice: Vec<&dyn rusqlite::ToSql> = m_params.iter().map(|p| p.as_ref()).collect();
    let m_iter = m_stmt.query_map(&m_slice[..], map_movie_row)
        .map_err(|e| e.to_string())?;
    let movies: Vec<Movie> = m_iter.filter_map(|r| r.ok()).collect();

    let (e_sql, e_params): (String, Vec<Box<dyn rusqlite::ToSql>>) = if let Some(sid) = site_id {
        (
            format!(
                "{} WHERE (e.studio_name = ?1 OR m.studio_name = ?1 OR e.studio_id = ?2 OR m.studio_id = ?2) \
                 ORDER BY COALESCE(m.release_year, CAST(substr(e.release_date, 1, 4) AS INTEGER)) DESC, e.id DESC",
                EPISODE_SQL
            ),
            vec![Box::new(studio_name.clone()), Box::new(sid)],
        )
    } else {
        (
            format!(
                "{} WHERE (e.studio_name = ?1 OR m.studio_name = ?1) \
                 ORDER BY COALESCE(m.release_year, CAST(substr(e.release_date, 1, 4) AS INTEGER)) DESC, e.id DESC",
                EPISODE_SQL
            ),
            vec![Box::new(studio_name.clone())],
        )
    };

    let mut e_stmt = conn.prepare(&e_sql).map_err(|e| e.to_string())?;
    let e_slice: Vec<&dyn rusqlite::ToSql> = e_params.iter().map(|p| p.as_ref()).collect();
    let e_iter = e_stmt.query_map(&e_slice[..], map_episode_row)
        .map_err(|e| e.to_string())?;
    let episodes: Vec<Episode> = e_iter.filter_map(|r| r.ok()).collect();

    Ok(StudioWorks {
        studio_name,
        studio_name_zh,
        description_zh,
        logo_url,
        banner_url,
        website_url,
        movies_count: movies.len() as i64,
        movies,
        episodes_count: episodes.len() as i64,
        episodes,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::migrate::create_empty_database_schema;

    #[test]
    fn test_studio_library_and_works_with_chinese_translations() {
        let conn = Connection::open_in_memory().unwrap();
        create_empty_database_schema(&conn).unwrap();

        conn.execute(
            "INSERT INTO movies (id, title, studio_name, release_year) VALUES (1, 'Falcon Film 1', 'Falcon Studios', 2020)",
            [],
        ).unwrap();
        conn.execute(
            "INSERT INTO movies (id, title, studio_name, release_year) VALUES (2, 'Falcon Film 2', 'Falcon Studios', 2021)",
            [],
        ).unwrap();
        conn.execute(
            "INSERT INTO episodes (id, movie_id, title, studio_name) VALUES (1, 1, 'Scene 1', 'Falcon Studios')",
            [],
        ).unwrap();

        conn.execute(
            "INSERT INTO studios (name, name_zh, description_zh, logo_url, banner_url) VALUES ('Falcon Studios', '猎鹰影视', '1971年创立于旧金山', 'images/logos/FalconVideo.png', 'images/FalconBanner4.jpg')",
            [],
        ).unwrap();

        // 1. Library fetch
        let lib = get_studio_library(&conn, None, None, Some(1), Some(10)).unwrap();
        assert_eq!(lib.total, 1);
        assert_eq!(lib.items[0].name, "Falcon Studios");
        assert_eq!(lib.items[0].name_zh.as_deref(), Some("猎鹰影视"));
        assert_eq!(lib.items[0].description_zh.as_deref(), Some("1971年创立于旧金山"));
        assert_eq!(lib.items[0].logo_url.as_deref(), Some("images/logos/FalconVideo.png"));
        assert_eq!(lib.items[0].banner_url.as_deref(), Some("images/FalconBanner4.jpg"));
        assert_eq!(lib.items[0].website_url, None);
        assert_eq!(lib.items[0].works_count, 2);
        assert_eq!(lib.items[0].episodes_count, 1);

        // 2. Search by Chinese name
        let lib_zh_search = get_studio_library(&conn, Some("猎鹰".to_string()), None, Some(1), Some(10)).unwrap();
        assert_eq!(lib_zh_search.total, 1);
        assert_eq!(lib_zh_search.items[0].name, "Falcon Studios");

        // 3. Studio works
        let works = get_studio_works(&conn, "Falcon Studios".to_string()).unwrap();
        assert_eq!(works.studio_name, "Falcon Studios");
        assert_eq!(works.studio_name_zh.as_deref(), Some("猎鹰影视"));
        assert_eq!(works.description_zh.as_deref(), Some("1971年创立于旧金山"));
        assert_eq!(works.logo_url.as_deref(), Some("images/logos/FalconVideo.png"));
        assert_eq!(works.banner_url.as_deref(), Some("images/FalconBanner4.jpg"));
        assert_eq!(works.website_url, None);
        assert_eq!(works.movies_count, 2);
        assert_eq!(works.episodes_count, 1);
    }
}
