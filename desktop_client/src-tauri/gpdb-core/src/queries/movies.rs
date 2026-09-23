//! Films: the paged list, one film in full, and the category facets.

use rusqlite::{params, Connection};

use crate::models::{DirectorRef, FilterArgs, Movie, MoviesResponse, MovieSeriesResponse, PerformerRef};
use crate::sql::{
    collapse_categories, facet_match_sql, map_episode_row, map_movie_row, CAST_SQL,
    DIRECTOR_MATCH_SQL, DIRECTOR_SEARCH_SQL, EPISODE_SQL, MOVIE_COLUMNS,
};
use crate::Result;

pub fn get_movies(conn: &Connection,
    filters: Option<FilterArgs>,
    page: Option<i64>,
    page_size: Option<i64>,) -> Result<MoviesResponse> {
    let f = filters.unwrap_or_default();
    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let mut conditions = Vec::new();
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    if let Some(ref q) = f.query {
        let q_trimmed = q.trim();
        if !q_trimmed.is_empty() {
            // `m.title_zh` is appended last so no existing parameter index moves.
            // It cannot go through movies_fts: that index is unicode61, which does
            // not segment CJK, so a whole Chinese title is a single token. Same
            // price as the `description_zh LIKE` this query has always run.
            let clean_q: String = q_trimmed.chars().filter(|c| c.is_alphanumeric() || c.is_whitespace()).collect();
            conditions.push(format!(
                "(m.title LIKE ? OR m.studio_name LIKE ? OR m.director_name LIKE ? OR m.description_zh LIKE ? OR m.id = ? OR {} OR m.title_zh LIKE ?)",
                DIRECTOR_SEARCH_SQL
            ));
            let like_q = format!("%{}%", clean_q);
            let id_val: i64 = clean_q.parse().unwrap_or(-1);
            params_vec.push(Box::new(like_q.clone()));
            params_vec.push(Box::new(like_q.clone()));
            params_vec.push(Box::new(like_q.clone()));
            params_vec.push(Box::new(like_q.clone()));
            params_vec.push(Box::new(id_val));
            params_vec.push(Box::new(like_q.clone()));
            params_vec.push(Box::new(like_q));
        }
    }

    if let Some(ref st) = f.studio {
        let st_trimmed = st.trim();
        if !st_trimmed.is_empty() {
            conditions.push("m.studio_name = ?".to_string());
            params_vec.push(Box::new(st_trimmed.to_string()));
        }
    }

    if let Some(ref cat) = f.category {
        let cat_trimmed = cat.trim();
        if !cat_trimmed.is_empty() {
            // Token match, not `m.category = ?`: the chips are atomic terms now, so
            // an exact match would leave "Wrestling" unable to reach the 15 films
            // stored as "Wrestling<br />J/O". Deliberately changes existing results
            // (Wrestling: 879 -> 896).
            conditions.push(facet_match_sql("category", "m"));
            params_vec.push(Box::new(cat_trimmed.to_string()));
        }
    }

    if let Some(ref dir) = f.director {
        let dir_trimmed = dir.trim();
        if !dir_trimmed.is_empty() {
            // The OR keeps a library that has not been through `--mode directors`
            // yet matching exactly as it did before.
            conditions.push(format!("({} OR m.director_name = ?)", DIRECTOR_MATCH_SQL));
            params_vec.push(Box::new(dir_trimmed.to_string()));
            params_vec.push(Box::new(dir_trimmed.to_string()));
        }
    }

    if let Some(ymin) = f.year_min {
        conditions.push("m.release_year >= ?".to_string());
        params_vec.push(Box::new(ymin));
    }

    if let Some(ymax) = f.year_max {
        conditions.push("m.release_year <= ?".to_string());
        params_vec.push(Box::new(ymax));
    }

    let where_clause = if conditions.is_empty() {
        String::new()
    } else {
        format!("WHERE {}", conditions.join(" AND "))
    };

    let sort_clause = match f.sort_by.as_deref() {
        Some("year_asc") => "m.release_year ASC, m.id ASC",
        Some("title_asc") => "m.title ASC",
        Some("id_desc") => "m.id DESC",
        _ => "m.release_year DESC, m.id DESC",
    };

    // Total count
    let count_query = format!("SELECT count(*) FROM movies m {}", where_clause);

    let params_slice: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = conn.query_row(&count_query, &params_slice[..], |r| r.get(0)).map_err(|e| e.to_string())?;

    // Page items
    let select_query = format!(
        "SELECT {} FROM movies m {} ORDER BY {} LIMIT ? OFFSET ?",
        MOVIE_COLUMNS, where_clause, sort_clause
    );

    let mut full_params = params_vec;
    full_params.push(Box::new(page_size));
    full_params.push(Box::new(offset));
    let full_slice: Vec<&dyn rusqlite::ToSql> = full_params.iter().map(|p| p.as_ref()).collect();

    let mut stmt = conn.prepare(&select_query).map_err(|e| e.to_string())?;
    let movie_rows = stmt.query_map(&full_slice[..], map_movie_row).map_err(|e| e.to_string())?;

    let mut items = Vec::new();
    for mr in movie_rows {
        if let Ok(mut m) = mr {
            let mut p_stmt = conn.prepare(CAST_SQL).map_err(|e| e.to_string())?;
            let p_iter = p_stmt.query_map(params![m.id], |pr| {
                Ok(PerformerRef {
                    id: pr.get(0)?,
                    name: pr.get(1)?,
                    image_url: pr.get(2)?,
                })
            }).map_err(|e| e.to_string())?;
            m.performers = Some(p_iter.filter_map(|r| r.ok()).collect());

            // Director roster, for the one-line name the cards show. Without it a
            // card falls back to movies.director_name, which for rows scraped
            // before the parser fix is every name glued into one truncated blob.
            let mut d_stmt = conn.prepare(
                "SELECT d.id, d.name FROM movie_directors md \
                 JOIN directors d ON d.id = md.director_id \
                 WHERE md.movie_id = ?1 ORDER BY md.position ASC, d.id ASC"
            ).map_err(|e| e.to_string())?;
            let d_iter = d_stmt.query_map(params![m.id], |dr| {
                Ok(DirectorRef { id: dr.get(0)?, name: dr.get(1)? })
            }).map_err(|e| e.to_string())?;
            m.directors = Some(d_iter.filter_map(|r| r.ok()).collect());

            items.push(m);
        }
    }

    Ok(MoviesResponse { items, total })
}

pub fn get_movie_detail(conn: &Connection,
    id: i64,) -> Result<Option<Movie>> {
    let mut stmt = conn.prepare(&format!(
        "SELECT {} FROM movies m WHERE m.id = ?1",
        MOVIE_COLUMNS
    )).map_err(|e| e.to_string())?;

    let movie_res = stmt.query_row(params![id], map_movie_row);

    match movie_res {
        Ok(mut m) => {
            // Performers
            let mut p_stmt = conn.prepare(CAST_SQL).map_err(|e| e.to_string())?;
            let p_iter = p_stmt.query_map(params![m.id], |pr| {
                Ok(PerformerRef {
                    id: pr.get(0)?,
                    name: pr.get(1)?,
                    image_url: pr.get(2)?,
                })
            }).map_err(|e| e.to_string())?;
            m.performers = Some(p_iter.filter_map(|r| r.ok()).collect());

            // Director roster — see DIRECTOR_MATCH_SQL. Empty for a library that
            // has not been through `--mode directors` yet; the frontend falls
            // back to the director_name string in that case.
            let mut d_stmt = conn.prepare(
                "SELECT d.id, d.name FROM movie_directors md \
                 JOIN directors d ON d.id = md.director_id \
                 WHERE md.movie_id = ?1 ORDER BY md.position ASC, d.id ASC"
            ).map_err(|e| e.to_string())?;
            let d_iter = d_stmt.query_map(params![m.id], |dr| {
                Ok(DirectorRef { id: dr.get(0)?, name: dr.get(1)? })
            }).map_err(|e| e.to_string())?;
            m.directors = Some(d_iter.filter_map(|r| r.ok()).collect());

            // Episodes
            let mut ep_stmt = conn.prepare(
                &format!("{} WHERE e.movie_id = ?1 ORDER BY e.id ASC", EPISODE_SQL)
            ).map_err(|e| e.to_string())?;
            let ep_iter = ep_stmt.query_map(params![m.id], map_episode_row)
                .map_err(|e| e.to_string())?;
            m.episodes = Some(ep_iter.filter_map(|r| r.ok()).collect());

            Ok(Some(m))
        }
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(e.into()),
    }
}
pub fn get_categories(conn: &Connection) -> Result<Vec<String>> {
    // Grouped by the raw value, collapsed in code. The counts are what the collapse
    // weights by: "Wrestling<br />J/O" is one film for both Wrestling and J/O, so
    // the ordering cannot be recovered from a bare DISTINCT list.
    let mut stmt = conn.prepare(
        "SELECT category, count(*) FROM movies \
         WHERE category IS NOT NULL AND trim(category) != '' \
         GROUP BY category"
    ).map_err(|e| e.to_string())?;

    let rows = stmt.query_map([], |r| Ok((r.get(0)?, r.get(1)?)))
        .map_err(|e| e.to_string())?;
    let raw: Vec<(String, i64)> = rows.filter_map(|r| r.ok()).collect();
    Ok(collapse_categories(&raw))
}

/// Helper to extract potential series root title.
/// e.g. "Fire Island Cruising 2: Boys on Fire" -> "Fire Island Cruising"
/// "Humungous 1" -> "Humungous"
/// "Raw Force: Part 1" -> "Raw Force"
pub fn extract_series_root(title: &str) -> Option<String> {
    let t = title.trim();
    if t.len() < 3 {
        return None;
    }

    fn is_num_or_roman(token: &str) -> bool {
        let clean = token.trim_matches(|c: char| !c.is_alphanumeric());
        if clean.is_empty() {
            return false;
        }
        if clean.chars().all(|c| c.is_ascii_digit()) {
            return true;
        }
        let lower = clean.to_ascii_lowercase();
        matches!(
            lower.as_str(),
            "i" | "ii" | "iii" | "iv" | "v" | "vi" | "vii" | "viii" | "ix" | "x" | "xi" | "xii"
        )
    }

    // 1. Check for subtitle / chapter separators: ": ", " - ", " – ", " — ", ", "
    for sep in &[": ", " - ", " – ", " — ", ", "] {
        if let Some(pos) = t.find(sep) {
            let left = t[..pos].trim();
            let right = t[pos + sep.len()..].trim();
            
            let right_lower = right.to_ascii_lowercase();
            if right_lower.starts_with("part")
                || right_lower.starts_with("vol")
                || right_lower.starts_with("pt")
                || right_lower.starts_with("chapter")
                || right_lower.starts_with("episode")
                || right_lower.starts_with("ep")
            {
                if left.len() >= 3 {
                    return Some(left.to_string());
                }
            }

            // Left might end with a number (e.g. "Fire Island Cruising 2: Boys on Fire")
            if let Some(last_space) = left.rfind(' ') {
                let candidate_num = &left[last_space + 1..];
                if is_num_or_roman(candidate_num) {
                    let root = left[..last_space].trim();
                    if root.len() >= 3 {
                        return Some(root.to_string());
                    }
                }
            }
        }
    }

    // 2. Check if title ends with "Part X", "Vol. X", "Volume X", "Chapter X"
    let tokens: Vec<&str> = t.split_whitespace().collect();
    if tokens.len() >= 2 {
        let last = tokens[tokens.len() - 1];
        let second_last = tokens[tokens.len() - 2].to_ascii_lowercase();
        let second_last_clean = second_last.trim_end_matches('.');
        if (second_last_clean == "part"
            || second_last_clean == "vol"
            || second_last_clean == "volume"
            || second_last_clean == "pt"
            || second_last_clean == "chapter"
            || second_last_clean == "episode"
            || second_last_clean == "ep")
            && is_num_or_roman(last)
        {
            let root = tokens[..tokens.len() - 2].join(" ");
            let root_clean = root.trim_end_matches(|c: char| c == ':' || c == '-' || c == ',' || c == '–' || c == '—').trim();
            if root_clean.len() >= 3 {
                return Some(root_clean.to_string());
            }
        }

        // 3. Check if title ends with a number or Roman numeral: e.g. "Humungous 1"
        if is_num_or_roman(last) {
            let root = tokens[..tokens.len() - 1].join(" ");
            let root_clean = root.trim_end_matches(|c: char| c == ':' || c == '-' || c == ',' || c == '–' || c == '—').trim();
            if root_clean.len() >= 3 {
                return Some(root_clean.to_string());
            }
        }
    }

    None
}

pub fn get_movie_series(conn: &Connection, movie_id: i64) -> Result<Option<MovieSeriesResponse>> {
    let mut stmt = conn.prepare(
        "SELECT id, title, studio_id, studio_name FROM movies WHERE id = ?1"
    ).map_err(|e| e.to_string())?;

    let movie_row = stmt.query_row(params![movie_id], |r| {
        Ok((r.get::<_, i64>(0)?, r.get::<_, String>(1)?, r.get::<_, Option<i64>>(2)?, r.get::<_, Option<String>>(3)?))
    });

    let (_, title, studio_id, studio_name) = match movie_row {
        Ok(m) => m,
        Err(rusqlite::Error::QueryReturnedNoRows) => return Ok(None),
        Err(e) => return Err(e.into()),
    };

    let root = match extract_series_root(&title) {
        Some(r) => r,
        None => return Ok(None),
    };

    let like_space = format!("{} %", root);
    let like_colon = format!("{}:%", root);
    let like_dash = format!("{}-%", root);
    let like_comma = format!("{},%", root);

    let query_sql = format!(
        "SELECT {} FROM movies m \
         WHERE (m.studio_id = ?1 OR (?1 IS NULL AND (m.studio_name = ?2 OR (?2 IS NULL AND m.studio_name IS NULL)))) \
           AND (m.title = ?3 OR m.title LIKE ?4 OR m.title LIKE ?5 OR m.title LIKE ?6 OR m.title LIKE ?7) \
         ORDER BY m.release_year ASC, m.title ASC",
        MOVIE_COLUMNS
    );

    let mut list_stmt = conn.prepare(&query_sql).map_err(|e| e.to_string())?;
    let rows = list_stmt.query_map(
        params![studio_id, studio_name, root, like_space, like_colon, like_dash, like_comma],
        map_movie_row
    ).map_err(|e| e.to_string())?;

    let items: Vec<Movie> = rows.filter_map(|r| r.ok()).collect();

    if items.len() >= 2 {
        Ok(Some(MovieSeriesResponse {
            root_title: root,
            studio_name,
            items,
        }))
    } else {
        Ok(None)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_series_root() {
        assert_eq!(extract_series_root("Humungous 1").as_deref(), Some("Humungous"));
        assert_eq!(extract_series_root("Humungous 2").as_deref(), Some("Humungous"));
        assert_eq!(extract_series_root("Fire Island Cruising 1").as_deref(), Some("Fire Island Cruising"));
        assert_eq!(extract_series_root("Fire Island Cruising 2: Boys on Fire").as_deref(), Some("Fire Island Cruising"));
        assert_eq!(extract_series_root("To Moscow with Love 1").as_deref(), Some("To Moscow with Love"));
        assert_eq!(extract_series_root("Breeding Season 1").as_deref(), Some("Breeding Season"));
        assert_eq!(extract_series_root("Raw Force: Part 1").as_deref(), Some("Raw Force"));
        assert_eq!(extract_series_root("The Best of Stud Vol. 2").as_deref(), Some("The Best of Stud"));
        assert_eq!(extract_series_root("Boys Behind Bars 1").as_deref(), Some("Boys Behind Bars"));
        assert_eq!(extract_series_root("SWEAT 10").as_deref(), Some("SWEAT"));
        assert_eq!(extract_series_root("Bi-Guy 1").as_deref(), Some("Bi-Guy"));
    }
}

