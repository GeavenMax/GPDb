//! Series Collections: index and queries for multi-part movie franchises and series.

use std::collections::HashMap;
use rusqlite::{params, Connection};

use crate::models::{Movie, MovieSeriesResponse, SeriesCollectionItem, SeriesCollectionsResponse};
use crate::queries::movies::extract_series_root;
use crate::sql::{map_movie_row, MOVIE_COLUMNS};
use crate::Result;

#[derive(Default)]
struct SeriesAcc {
    root_title: String,
    studio_name: Option<String>,
    movie_count: i64,
    year_start: Option<i64>,
    year_end: Option<i64>,
    cover_url: Option<String>,
    movie_ids: Vec<i64>,
    sample_covers: Vec<String>,
}

/// Automatically builds or updates the `series_collections` index in SQLite.
pub fn ensure_series_index(conn: &Connection) -> Result<()> {
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS series_collections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            root_title TEXT NOT NULL,
            studio_name TEXT,
            movie_count INTEGER NOT NULL DEFAULT 0,
            cover_url TEXT,
            year_start INTEGER,
            year_end INTEGER,
            sample_movie_ids TEXT,
            sample_covers TEXT,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(root_title, studio_name)
        );
        CREATE INDEX IF NOT EXISTS idx_series_collections_root ON series_collections(root_title);
        CREATE INDEX IF NOT EXISTS idx_series_collections_studio ON series_collections(studio_name);",
    ).map_err(|e| e.to_string())?;

    let _ = conn.execute("ALTER TABLE series_collections ADD COLUMN sample_covers TEXT", []);

    let index_count: i64 = conn
        .query_row("SELECT count(*) FROM series_collections", [], |r| r.get(0))
        .unwrap_or(0);

    // If already populated, return early
    if index_count > 0 {
        return Ok(());
    }

    refresh_series_index(conn)?;
    Ok(())
}

/// Re-scans all movies to rebuild the `series_collections` index.
pub fn refresh_series_index(conn: &Connection) -> Result<usize> {
    let mut stmt = conn
        .prepare(
            "SELECT id, title, studio_name, release_year, cover_full, cover_icon \
             FROM movies WHERE title IS NOT NULL AND trim(title) != '' ORDER BY id ASC",
        )
        .map_err(|e| e.to_string())?;

    let mut groups: HashMap<(String, Option<String>), SeriesAcc> = HashMap::new();

    let rows = stmt.query_map([], |r| {
        Ok((
            r.get::<_, i64>(0)?,
            r.get::<_, String>(1)?,
            r.get::<_, Option<String>>(2)?,
            r.get::<_, Option<i64>>(3)?,
            r.get::<_, Option<String>>(4)?,
            r.get::<_, Option<String>>(5)?,
        ))
    }).map_err(|e| e.to_string())?;

    for row in rows.flatten() {
        let (id, title, studio, year, cover_full, cover_icon) = row;
        if let Some(root) = extract_series_root(&title) {
            let key = (root.clone(), studio.clone());
            let acc = groups.entry(key).or_insert_with(|| SeriesAcc {
                root_title: root,
                studio_name: studio,
                ..Default::default()
            });

            acc.movie_count += 1;
            acc.movie_ids.push(id);

            if let Some(y) = year {
                acc.year_start = Some(acc.year_start.map_or(y, |v| v.min(y)));
                acc.year_end = Some(acc.year_end.map_or(y, |v| v.max(y)));
            }

            let best_cover = cover_full.or(cover_icon);
            if let Some(ref c) = best_cover {
                if acc.sample_covers.len() < 4 && !acc.sample_covers.contains(c) {
                    acc.sample_covers.push(c.clone());
                }
            }

            if acc.cover_url.is_none() {
                acc.cover_url = best_cover;
            }
        }
    }

    let tx = conn.unchecked_transaction().map_err(|e| e.to_string())?;
    tx.execute("DELETE FROM series_collections", []).map_err(|e| e.to_string())?;

    let mut inserted = 0;
    {
        let mut ins_stmt = tx.prepare(
            "INSERT OR REPLACE INTO series_collections \
             (root_title, studio_name, movie_count, cover_url, year_start, year_end, sample_movie_ids, sample_covers) \
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
        ).map_err(|e| e.to_string())?;

        for (_, acc) in groups.into_iter().filter(|(_, a)| a.movie_count >= 2) {
            let sample_json = serde_json::to_string(&acc.movie_ids).unwrap_or_default();
            let covers_json = serde_json::to_string(&acc.sample_covers).unwrap_or_default();
            ins_stmt.execute(params![
                acc.root_title,
                acc.studio_name,
                acc.movie_count,
                acc.cover_url,
                acc.year_start,
                acc.year_end,
                sample_json,
                covers_json,
            ]).map_err(|e| e.to_string())?;
            inserted += 1;
        }
    }

    tx.commit().map_err(|e| e.to_string())?;
    Ok(inserted)
}

/// Retrieves paged series collections matching criteria.
pub fn get_series_collections(
    conn: &Connection,
    query: Option<String>,
    studio: Option<String>,
    sort_by: Option<String>,
    page: Option<i64>,
    page_size: Option<i64>,
) -> Result<SeriesCollectionsResponse> {
    ensure_series_index(conn)?;

    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let mut conditions = vec!["movie_count >= 2".to_string()];
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    if let Some(q) = query {
        let q = q.trim().to_string();
        if !q.is_empty() {
            conditions.push("root_title LIKE ? ESCAPE '\\'".to_string());
            let escaped = q.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
            params_vec.push(Box::new(format!("%{}%", escaped)));
        }
    }

    if let Some(s) = studio {
        let s = s.trim().to_string();
        if !s.is_empty() {
            conditions.push("studio_name = ?".to_string());
            params_vec.push(Box::new(s));
        }
    }

    let where_clause = conditions.join(" AND ");

    let count_sql = format!("SELECT count(*) FROM series_collections WHERE {}", where_clause);
    let mut count_stmt = conn.prepare(&count_sql).map_err(|e| e.to_string())?;
    let slice_params: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = count_stmt.query_row(slice_params.as_slice(), |r| r.get(0)).unwrap_or(0);

    let order_sql = match sort_by.as_deref() {
        Some("count_desc") => "movie_count DESC, root_title ASC",
        Some("title_asc") => "root_title ASC",
        Some("year_desc") => "COALESCE(year_end, 0) DESC, root_title ASC",
        _ => "movie_count DESC, root_title ASC",
    };

    let query_sql = format!(
        "SELECT id, root_title, studio_name, movie_count, cover_url, year_start, year_end, updated_at, sample_covers \
         FROM series_collections WHERE {} ORDER BY {} LIMIT ? OFFSET ?",
        where_clause, order_sql
    );

    let mut stmt = conn.prepare(&query_sql).map_err(|e| e.to_string())?;
    params_vec.push(Box::new(page_size));
    params_vec.push(Box::new(offset));

    let final_params: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let rows = stmt.query_map(final_params.as_slice(), |r| {
        let covers_raw: Option<String> = r.get(8)?;
        let sample_covers: Option<Vec<String>> = covers_raw.and_then(|raw| serde_json::from_str(&raw).ok());
        Ok(SeriesCollectionItem {
            id: r.get(0)?,
            root_title: r.get(1)?,
            studio_name: r.get(2)?,
            movie_count: r.get(3)?,
            cover_url: r.get(4)?,
            sample_covers,
            year_start: r.get(5)?,
            year_end: r.get(6)?,
            updated_at: r.get(7)?,
        })
    }).map_err(|e| e.to_string())?;

    let items = rows.filter_map(|r| r.ok()).collect();

    Ok(SeriesCollectionsResponse { items, total })
}

/// Returns the full MovieSeriesResponse by root title and optional studio name.
pub fn get_movie_series_by_root(
    conn: &Connection,
    root_title: &str,
    studio_name: Option<&str>,
) -> Result<Option<MovieSeriesResponse>> {
    let root = root_title.trim();
    if root.is_empty() {
        return Ok(None);
    }

    let like_space = format!("{} %", root);
    let like_colon = format!("{}:%", root);
    let like_dash = format!("{}-%", root);
    let like_comma = format!("{},%", root);

    let query_sql = match studio_name {
        Some(s) if !s.trim().is_empty() => format!(
            "SELECT {} FROM movies m \
             WHERE m.studio_name = ?1 \
               AND (m.title = ?2 OR m.title LIKE ?3 OR m.title LIKE ?4 OR m.title LIKE ?5 OR m.title LIKE ?6) \
             ORDER BY m.release_year ASC, m.title ASC",
            MOVIE_COLUMNS
        ),
        _ => format!(
            "SELECT {} FROM movies m \
             WHERE (m.title = ?1 OR m.title LIKE ?2 OR m.title LIKE ?3 OR m.title LIKE ?4 OR m.title LIKE ?5) \
             ORDER BY m.release_year ASC, m.title ASC",
            MOVIE_COLUMNS
        ),
    };

    let mut stmt = conn.prepare(&query_sql).map_err(|e| e.to_string())?;
    let rows = if let Some(s) = studio_name {
        if !s.trim().is_empty() {
            stmt.query_map(
                params![s.trim(), root, like_space, like_colon, like_dash, like_comma],
                map_movie_row,
            ).map_err(|e| e.to_string())?
        } else {
            stmt.query_map(
                params![root, like_space, like_colon, like_dash, like_comma],
                map_movie_row,
            ).map_err(|e| e.to_string())?
        }
    } else {
        stmt.query_map(
            params![root, like_space, like_colon, like_dash, like_comma],
            map_movie_row,
        ).map_err(|e| e.to_string())?
    };

    let items: Vec<Movie> = rows.filter_map(|r| r.ok()).collect();
    if items.is_empty() {
        return Ok(None);
    }

    let resolved_studio = studio_name.map(|s| s.to_string()).or_else(|| items[0].studio_name.clone());

    Ok(Some(MovieSeriesResponse {
        root_title: root.to_string(),
        studio_name: resolved_studio,
        items,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_series_collections_table_and_queries() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(
            "CREATE TABLE movies (
                id INTEGER PRIMARY KEY,
                title TEXT,
                studio_id INTEGER,
                studio_name TEXT,
                release_year INTEGER,
                duration_mins INTEGER,
                category TEXT,
                rating TEXT,
                movie_type TEXT,
                description TEXT,
                cover_icon TEXT,
                cover_full TEXT,
                scraped_at TIMESTAMP,
                covers_json TEXT,
                director_id INTEGER,
                director_name TEXT,
                description_zh TEXT,
                translation_attempts INTEGER,
                cover_back TEXT,
                title_zh TEXT,
                title_attempts INTEGER
            );
            INSERT INTO movies (id, title, studio_name, release_year) VALUES
            (1, 'Humungous 1', 'Titan Men', 2005),
            (2, 'Humungous 2', 'Titan Men', 2006),
            (3, 'Raw Force: Part 1', 'Raging Stallion', 2010),
            (4, 'Raw Force: Part 2', 'Raging Stallion', 2011),
            (5, 'Standalone Movie', 'Indie', 2020);"
        ).unwrap();

        ensure_series_index(&conn).unwrap();
        let res = get_series_collections(&conn, None, None, None, None, None).unwrap();
        assert_eq!(res.total, 2);

        let series = get_movie_series_by_root(&conn, "Humungous", Some("Titan Men")).unwrap();
        assert!(series.is_some());
        assert_eq!(series.unwrap().items.len(), 2);
    }
}
