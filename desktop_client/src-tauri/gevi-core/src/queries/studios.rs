//! Studios exist only as a column on movies — no table, no artwork — so every
//! studio query is a grouping pass over `movies`.

use rusqlite::{params, Connection};

use crate::models::{Episode, Movie, StudioLibrary, StudioSummary, StudioWorks};
use crate::sql::{map_episode_row, EPISODE_SQL};
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
/// Kept in step with db_manager.list_studios, which serves the same page over HTTP.
/// Studios exist only as a column on `movies` — no table, no artwork — so both
/// counts come out of one grouping pass; the LEFT JOIN is what keeps a studio whose
/// films have no episodes in the list, with 0.
pub fn get_studio_library(conn: &Connection,
    query: Option<String>,
    sort_by: Option<String>,
    page: Option<i64>,
    page_size: Option<i64>,) -> Result<StudioLibrary> {
    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let mut conditions = vec![
        "m.studio_name IS NOT NULL".to_string(),
        "trim(m.studio_name) != ''".to_string(),
    ];
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    if let Some(q) = query {
        let q = q.trim().to_string();
        if !q.is_empty() {
            // ESCAPE so a literal % or _ typed into the search box stays literal.
            conditions.push("m.studio_name LIKE ? ESCAPE '\\'".to_string());
            let escaped = q.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
            params_vec.push(Box::new(format!("%{}%", escaped)));
        }
    }
    let where_clause = format!("WHERE {}", conditions.join(" AND "));

    let sort_clause = match sort_by.as_deref() {
        Some("name_asc") => "m.studio_name COLLATE NOCASE ASC",
        Some("episodes_desc") => {
            "episodes_count DESC, works_count DESC, m.studio_name COLLATE NOCASE ASC"
        }
        _ => "works_count DESC, m.studio_name COLLATE NOCASE ASC",
    };

    let params_slice: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = conn.query_row(
        &format!("SELECT count(DISTINCT m.studio_name) FROM movies m {}", where_clause),
        &params_slice[..],
        |r| r.get(0),
    ).map_err(|e| e.to_string())?;

    let select_query = format!(
        "SELECT m.studio_name, count(DISTINCT m.id) AS works_count, count(e.id) AS episodes_count \
         FROM movies m LEFT JOIN episodes e ON e.movie_id = m.id \
         {} GROUP BY m.studio_name ORDER BY {} LIMIT ? OFFSET ?",
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
            works_count: r.get(1)?,
            episodes_count: r.get(2)?,
        })
    }).map_err(|e| e.to_string())?;

    Ok(StudioLibrary {
        items: rows.filter_map(|r| r.ok()).collect(),
        total,
    })
}
/// One studio's complete works, matching `/api/studios/<name>/works`.
pub fn get_studio_works(conn: &Connection,
    studio_name: String,) -> Result<StudioWorks> {

    let mut m_stmt = conn.prepare(
        "SELECT m.id, m.title, m.studio_id, m.studio_name, m.release_year, \
                m.duration_mins, m.category, m.rating, m.cover_icon, m.cover_full \
         FROM movies m \
         WHERE m.studio_name = ?1 \
         ORDER BY m.release_year DESC, m.id DESC"
    ).map_err(|e| e.to_string())?;

    let m_iter = m_stmt.query_map(params![studio_name], |r| {
        Ok(Movie {
            id: r.get(0)?,
            title: r.get(1)?,
            studio_id: r.get(2)?,
            studio_name: r.get(3)?,
            release_year: r.get(4)?,
            duration_mins: r.get(5)?,
            category: r.get(6)?,
            rating: r.get(7)?,
            movie_type: None,
            description: None,
            description_zh: None,
            cover_icon: r.get(8)?,
            cover_full: r.get(9)?,
            covers: None,
            director_id: None,
            director_name: None,
            directors: None,
            performers: None,
            episodes: None,
        })
    }).map_err(|e| e.to_string())?;
    let movies: Vec<Movie> = m_iter.filter_map(|r| r.ok()).collect();

    let mut e_stmt = conn.prepare(&format!(
        "{} WHERE m.studio_name = ?1 ORDER BY m.release_year DESC, e.id DESC",
        EPISODE_SQL
    )).map_err(|e| e.to_string())?;
    let e_iter = e_stmt.query_map(params![studio_name], map_episode_row)
        .map_err(|e| e.to_string())?;
    let episodes: Vec<Episode> = e_iter.filter_map(|r| r.ok()).collect();

    Ok(StudioWorks {
        studio_name,
        movies_count: movies.len() as i64,
        movies,
        episodes_count: episodes.len() as i64,
        episodes,
    })
}
