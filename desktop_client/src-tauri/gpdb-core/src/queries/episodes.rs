//! The global episode library — every scene in the database, paged.

use rusqlite::Connection;

use crate::models::{EpisodeCastRef, EpisodeLibrary, EpisodeSummary};
use crate::Result;

/// One page of the global episode library, matching `/api/episode-library`.
///
/// Not scoped to a film or a performer the way `get_studio_works` /
/// `get_performer_detail` are — this is the whole episodes table, so the film is a LEFT
/// JOIN and an episode whose film row has gone must still be listed.
pub fn get_episode_library(conn: &Connection,
    query: Option<String>,
    sort: Option<String>,
    studio: Option<String>,
    has_zh: Option<bool>,
    has_performers: Option<bool>,
    page: Option<i64>,
    page_size: Option<i64>,) -> Result<EpisodeLibrary> {
    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let mut conditions: Vec<String> = Vec::new();
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    if let Some(q) = query {
        let q = q.trim().to_string();
        if !q.is_empty() {
            // e.title is not searched. It only ever holds a placeholder for a film's
            // scene (the movie page's scene list carries no title, so we store "").
            // Standalone episodes do get a real title from the `coep` endpoint, but they
            // are reachable through `m.title`/description for now — adding `e.title` here
            // is a deliberate follow-up, not an oversight.
            conditions.push(
                "(m.title LIKE ? ESCAPE '\\' OR e.description LIKE ? ESCAPE '\\' \
                 OR e.description_zh LIKE ? ESCAPE '\\')"
                    .to_string(),
            );
            // ESCAPE so a literal % or _ typed into the search box stays literal.
            let escaped = q.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
            let like = format!("%{}%", escaped);
            params_vec.push(Box::new(like.clone()));
            params_vec.push(Box::new(like.clone()));
            params_vec.push(Box::new(like));
        }
    }
    if let Some(s) = studio {
        let s = s.trim().to_string();
        if !s.is_empty() {
            // Same fallback as the SELECT: a standalone episode has no parent film, so
            // filtering on `m.studio_name` alone would drop it from its own studio's list.
            conditions.push("COALESCE(m.studio_name, e.studio_name) = ?".to_string());
            params_vec.push(Box::new(s));
        }
    }
    if has_zh.unwrap_or(false) {
        conditions.push("e.description_zh IS NOT NULL AND trim(e.description_zh) != ''".to_string());
    }
    if has_performers.unwrap_or(false) {
        conditions
            .push("EXISTS (SELECT 1 FROM episode_performers ep WHERE ep.episode_id = e.id)".to_string());
    }

    let where_clause = if conditions.is_empty() {
        String::new()
    } else {
        format!("WHERE {}", conditions.join(" AND "))
    };
    let sort_clause = match sort.as_deref() {
        Some("year_desc") => "m.release_year DESC, e.id DESC",
        Some("movie_asc") => "m.title COLLATE NOCASE ASC, e.id ASC",
        _ => "e.id DESC",
    };
    let from_clause = "FROM episodes e LEFT JOIN movies m ON m.id = e.movie_id";

    let params_slice: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = conn
        .query_row(
            &format!("SELECT count(*) {} {}", from_clause, where_clause),
            &params_slice[..],
            |r| r.get(0),
        )
        .map_err(|e| e.to_string())?;

    let sql = format!(
        "SELECT e.id, e.movie_id, e.title, e.thumbnail_url, e.description, e.description_zh, \
                e.action_notes, m.title, \
                COALESCE(m.studio_name, e.studio_name), \
                COALESCE(m.release_year, CAST(substr(e.release_date, 1, 4) AS INTEGER)), \
                (SELECT count(*) FROM episodes e2 \
                  WHERE e2.movie_id = e.movie_id AND e2.id <= e.id), \
                (SELECT count(*) FROM episodes e2 WHERE e2.movie_id = e.movie_id), \
                m.title_zh \
         {} {} ORDER BY {} LIMIT ? OFFSET ?",
        from_clause, where_clause, sort_clause
    );

    let mut full_params = params_vec;
    full_params.push(Box::new(page_size));
    full_params.push(Box::new(offset));
    let full_slice: Vec<&dyn rusqlite::ToSql> = full_params.iter().map(|p| p.as_ref()).collect();

    let mut stmt = conn.prepare(&sql).map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map(&full_slice[..], |r| {
            Ok(EpisodeSummary {
                id: r.get(0)?,
                movie_id: r.get(1)?,
                title: r.get(2)?,
                thumbnail_url: r.get(3)?,
                description: r.get(4)?,
                description_zh: r.get(5)?,
                action_notes: r.get(6)?,
                movie_title: r.get(7)?,
                movie_title_zh: r.get(12)?,
                studio_name: r.get(8)?,
                release_year: r.get(9)?,
                episode_ordinal: r.get(10)?,
                episode_count: r.get(11)?,
                performers: Vec::new(),
            })
        })
        .map_err(|e| e.to_string())?;
    let mut items: Vec<EpisodeSummary> = rows.filter_map(|r| r.ok()).collect();

    // The whole page's cast in one extra query rather than a json_group_array inside the
    // big SELECT: the cards need ids to open a performer and those ids have to line up
    // with the names. Mirrors `db_manager._attach_episode_performers`.
    if !items.is_empty() {
        let marks = vec!["?"; items.len()].join(",");
        let ids: Vec<i64> = items.iter().map(|i| i.id).collect();
        let id_params: Vec<&dyn rusqlite::ToSql> =
            ids.iter().map(|i| i as &dyn rusqlite::ToSql).collect();
        let mut cast_stmt = conn
            .prepare(&format!(
                "SELECT episode_id, performer_id, performer_name FROM episode_performers \
                 WHERE episode_id IN ({}) ORDER BY episode_id, performer_id",
                marks
            ))
            .map_err(|e| e.to_string())?;
        let cast_rows = cast_stmt
            .query_map(&id_params[..], |r| {
                Ok((
                    r.get::<_, i64>(0)?,
                    EpisodeCastRef {
                        id: r.get(1)?,
                        name: r.get(2)?,
                    },
                ))
            })
            .map_err(|e| e.to_string())?;

        let mut by_episode: std::collections::HashMap<i64, Vec<EpisodeCastRef>> =
            std::collections::HashMap::new();
        for row in cast_rows.filter_map(|r| r.ok()) {
            by_episode.entry(row.0).or_default().push(row.1);
        }
        for item in items.iter_mut() {
            item.performers = by_episode.remove(&item.id).unwrap_or_default();
        }
    }

    Ok(EpisodeLibrary { items, total })
}
