//! Director queries.
//!
//! Unlike studios — which exist only as a column on `movies` — directors have real
//! tables: `directors` (one row per person) and `movie_directors` (the junction).
//! `scraper_v2.py --mode directors` built them by segmenting the legacy glued
//! `movies.director_name` strings, and that history leaves two rules:
//!
//!   * **Never key anything off `movies.director_name`.** It is a raw multi-name string,
//!     and on rows scraped before the parser fix several names are glued together with
//!     no separator at all. Counting a director's films through it silently undercounts
//!     everyone who ever shared a credit (Chris Ward reads 89 that way, against 274 real
//!     ones). `movie_directors` is the only correct source — see `sql.rs`'s
//!     `DIRECTOR_MATCH_SQL`, which was written on the same principle.
//!   * **Never read `directors.works_count`.** It is denormalized and stale: only
//!     `--mode directors` ever refreshes it, while the ongoing scrapers add links through
//!     `_link_directors` without touching it (SUM(works_count) is 37,261 against 37,502
//!     actual links). Every count below is computed live from the junction instead.

use rusqlite::{params, Connection};

use crate::models::{DirectorLibrary, DirectorSummary, DirectorWorks, Movie};
use crate::sql::{map_movie_row, MOVIE_COLUMNS};
use crate::Result;

/// Director library: every director with how many films they made and how many studios
/// they made them for, paged and searchable by name or site id.
///
/// Kept in step with db_manager.list_directors, which serves the same page over HTTP.
///
/// The LEFT JOINs are load-bearing: 107 directors have no `movie_directors` row at all,
/// and a library whose whole purpose is to hold everybody cannot drop them — they come
/// back with both counts 0.
pub fn get_director_library(conn: &Connection,
    query: Option<String>,
    sort_by: Option<String>,
    page: Option<i64>,
    page_size: Option<i64>,) -> Result<DirectorLibrary> {
    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let mut conditions: Vec<String> = Vec::new();
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    if let Some(q) = query {
        let q = q.trim().to_string();
        if !q.is_empty() {
            // ESCAPE so a literal % or _ typed into the search box stays literal.
            conditions.push("(d.name LIKE ? ESCAPE '\\' OR d.site_id = ?)".to_string());
            let escaped = q.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
            params_vec.push(Box::new(format!("%{}%", escaped)));
            // A director is also reachable by the id the site gives them, matching how
            // the performer list answers a numeric query.
            params_vec.push(Box::new(q.parse::<i64>().unwrap_or(-1)));
        }
    }
    // Every condition above constrains `directors` alone, so the same clause counts the
    // total without dragging the joins along — and the two can never disagree about
    // which rows match.
    let where_clause = if conditions.is_empty() {
        String::new()
    } else {
        format!("WHERE {}", conditions.join(" AND "))
    };

    // `d.id ASC` last is load-bearing, not decoration: 1,304 directors have exactly one
    // film and 20 pairs of names differ only by case (`Chi Chi LaRue` / `Chi Chi Larue`,
    // `Tony DiMarco` / `Tony Dimarco`), so both orderings tie constantly. Without a
    // tiebreak SQLite may order tied rows differently for a different LIMIT/OFFSET — the
    // same director then shows up on two pages while another is never shown at all.
    //
    // Removing it does NOT turn any test red — tried it, the parity test stayed green
    // because SQLite happens to return tied rows in id order for this plan. So this line
    // is a guarantee about every plan, not about the one that runs today, and no test
    // here claims otherwise.
    let sort_clause = match sort_by.as_deref() {
        Some("name_asc") => "d.name COLLATE NOCASE ASC, d.id ASC",
        _ => "works_count DESC, d.name COLLATE NOCASE ASC, d.id ASC",
    };

    let params_slice: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = conn.query_row(
        &format!("SELECT count(*) FROM directors d {}", where_clause),
        &params_slice[..],
        |r| r.get(0),
    ).map_err(|e| e.to_string())?;

    // NULLIF/trim so a blank studio_name counts as "no studio" rather than as a studio
    // of its own; count(DISTINCT) skips the NULLs that leaves behind.
    let select_query = format!(
        "SELECT d.id, d.name, \
                count(md.movie_id) AS works_count, \
                count(DISTINCT NULLIF(trim(m.studio_name), '')) AS studios_count \
         FROM directors d \
         LEFT JOIN movie_directors md ON md.director_id = d.id \
         LEFT JOIN movies m ON m.id = md.movie_id \
         {} GROUP BY d.id ORDER BY {} LIMIT ? OFFSET ?",
        where_clause, sort_clause
    );

    let mut full_params = params_vec;
    full_params.push(Box::new(page_size));
    full_params.push(Box::new(offset));
    let full_slice: Vec<&dyn rusqlite::ToSql> = full_params.iter().map(|p| p.as_ref()).collect();

    let mut stmt = conn.prepare(&select_query).map_err(|e| e.to_string())?;
    let rows = stmt.query_map(&full_slice[..], |r| {
        Ok(DirectorSummary {
            id: r.get(0)?,
            name: r.get(1)?,
            works_count: r.get(2)?,
            studios_count: r.get(3)?,
        })
    }).map_err(|e| e.to_string())?;

    Ok(DirectorLibrary {
        items: rows.filter_map(|r| r.ok()).collect(),
        total,
    })
}

/// One director's complete filmography, matching `/api/directors/<name>/works`.
///
/// Keyed by name rather than id, for the same reason `get_studio_works` takes a studio
/// name: favorites store a director as their name (see `user_favorites`), so the
/// favorites page can open this without first resolving an id.
///
/// An unknown name — a favorite saved from the pre-segmentation era, when the key could
/// be a whole glued string — is not an error, it is simply a director with no films.
pub fn get_director_works(conn: &Connection,
    director_name: String,) -> Result<DirectorWorks> {

    let mut stmt = conn.prepare(&format!(
        "SELECT {} FROM movies m \
         JOIN movie_directors md ON md.movie_id = m.id \
         JOIN directors d ON d.id = md.director_id \
         WHERE d.name = ?1 \
         ORDER BY m.release_year DESC, m.id DESC",
        MOVIE_COLUMNS
    )).map_err(|e| e.to_string())?;

    let iter = stmt.query_map(params![director_name], map_movie_row)
        .map_err(|e| e.to_string())?;
    let movies: Vec<Movie> = iter.filter_map(|r| r.ok()).collect();

    Ok(DirectorWorks {
        name: director_name,
        movies_count: movies.len() as i64,
        movies,
    })
}
