//! Performers: the paged list, the attribute facets, and one performer in full.

use rusqlite::{params, Connection};
use std::collections::HashMap;

use crate::models::{
    Episode, FacetValue, Movie, Performer, PerformerFacets, PerformerFilterArgs,
    PerformersResponse,
};
use crate::sql::{
    explode_attr, facet_sql, map_episode_row, map_movie_row, map_performer_row, EPISODE_SQL,
    MOVIE_COLUMNS, PERFORMER_COLUMNS, PERFORMER_FACETS,
};
use crate::Result;

pub fn get_performers(conn: &Connection,
    filters: Option<PerformerFilterArgs>,
    page: Option<i64>,
    page_size: Option<i64>,) -> Result<PerformersResponse> {
    let f = filters.unwrap_or_default();
    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let mut conditions: Vec<String> = Vec::new();
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    let q = f.query.clone().unwrap_or_default().trim().to_string();
    if !q.is_empty() {
        conditions.push("(p.name LIKE ? OR p.id = ?)".to_string());
        params_vec.push(Box::new(format!("%{}%", q)));
        params_vec.push(Box::new(q.parse::<i64>().unwrap_or(-1)));
    }

    // Multiple values for one attribute are OR-ed; different attributes AND-ed.
    let facet_selections: [(&str, &Option<Vec<String>>); 8] = [
        ("build", &f.body_type),
        ("hair", &f.hair),
        ("eyes", &f.eyes),
        ("skin", &f.skin),
        ("body_hair", &f.body_hair),
        ("facial_hair", &f.facial_hair),
        ("dick_size", &f.dick_size),
        ("foreskin", &f.foreskin),
    ];
    for (column, selection) in facet_selections {
        let values: Vec<String> = selection
            .clone()
            .unwrap_or_default()
            .into_iter()
            .map(|v| v.trim().to_string())
            .filter(|v| !v.is_empty())
            // Facet labels are site-controlled; strip LIKE wildcards so a stray
            // % cannot widen the match.
            .map(|v| v.replace('%', "").replace('_', ""))
            .collect();
        if values.is_empty() {
            continue;
        }
        let expr = facet_sql(column);
        let mut ors: Vec<String> = Vec::with_capacity(values.len());
        for value in &values {
            params_vec.push(Box::new(format!("%|{}|%", value)));
            ors.push(format!("{} LIKE ?", expr));
        }
        conditions.push(format!("({})", ors.join(" OR ")));
    }

    if f.has_image.unwrap_or(false) {
        conditions.push("(p.image_url IS NOT NULL AND trim(p.image_url) != '')".to_string());
    }

    let count_expr = "(SELECT count(*) FROM movie_performers mp WHERE mp.performer_id = p.id)";
    // "作品数量" counts scenes as well as films. A performer whose work is mostly
    // scenes used to sort below one with a handful of films while the card showed
    // only the film number, so the two never agreed. Sort, the lower bound and the
    // displayed figure all use this now; the raw movies_count is still selected so
    // the film count stays available.
    let works_expr = format!(
        "{} + (SELECT count(*) FROM episode_performers ep WHERE ep.performer_id = p.id)",
        count_expr
    );
    if let Some(min) = f.min_movies {
        conditions.push(format!("{} >= ?", works_expr));
        params_vec.push(Box::new(min));
    }

    let where_clause = if conditions.is_empty() {
        String::new()
    } else {
        format!("WHERE {}", conditions.join(" AND "))
    };

    let sort_clause = match f.sort_by.as_deref() {
        Some("movies_asc") => format!("{} ASC, p.id ASC", works_expr),
        Some("name_asc") => "p.name ASC".to_string(),
        Some("id_desc") => "p.id DESC".to_string(),
        Some("id_asc") => "p.id ASC".to_string(),
        _ => format!("{} DESC, p.id DESC", works_expr),
    };

    let count_query = format!("SELECT count(*) FROM performers p {}", where_clause);
    let params_slice: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = conn.query_row(&count_query, &params_slice[..], |r| r.get(0)).map_err(|e| e.to_string())?;

    let select_query = format!(
        "SELECT {}, {} AS movies_count, {} AS works_count \
         FROM performers p \
         {} \
         ORDER BY {} \
         LIMIT ? OFFSET ?",
        PERFORMER_COLUMNS, count_expr, works_expr, where_clause, sort_clause
    );

    let mut full_params = params_vec;
    full_params.push(Box::new(page_size));
    full_params.push(Box::new(offset));
    let full_slice: Vec<&dyn rusqlite::ToSql> = full_params.iter().map(|p| p.as_ref()).collect();

    let mut stmt = conn.prepare(&select_query).map_err(|e| e.to_string())?;
    let rows = stmt.query_map(&full_slice[..], map_performer_row).map_err(|e| e.to_string())?;

    let items = rows.filter_map(|r| r.ok()).collect();
    Ok(PerformersResponse { items, total })
}

pub fn get_performer_facets(conn: &Connection) -> Result<PerformerFacets> {
    let columns: Vec<&str> = PERFORMER_FACETS.iter().map(|(col, _, _)| *col).collect();
    let select_query = format!("SELECT {} FROM performers", columns.join(", "));

    let mut stmt = conn.prepare(&select_query).map_err(|e| e.to_string())?;
    // Aggregation happens in Rust: only a small slice of performers has been
    // detail-scraped, and multi-value attributes share one column.
    let rows = stmt
        .query_map([], |r| {
            let mut collected: Vec<Vec<String>> = Vec::with_capacity(PERFORMER_FACETS.len());
            for i in 0..PERFORMER_FACETS.len() {
                collected.push(explode_attr(r.get::<usize, Option<String>>(i)?));
            }
            Ok(collected)
        })
        .map_err(|e| e.to_string())?;

    let mut counts: HashMap<&str, HashMap<String, i64>> = HashMap::new();
    let mut enriched: i64 = 0;
    for row in rows.filter_map(|r| r.ok()) {
        if row.iter().any(|values| !values.is_empty()) {
            enriched += 1;
        }
        for (idx, (_, api_name, _)) in PERFORMER_FACETS.iter().enumerate() {
            for value in &row[idx] {
                *counts.entry(api_name).or_default().entry(value.clone()).or_insert(0) += 1;
            }
        }
    }

    let mut facets: HashMap<String, Vec<FacetValue>> = HashMap::new();
    for (api_name, value_counts) in counts {
        let mut values: Vec<FacetValue> = value_counts
            .into_iter()
            .map(|(value, count)| FacetValue { value, count })
            .collect();
        // Most common first, then alphabetical, matching the Python server.
        values.sort_by(|a, b| b.count.cmp(&a.count).then_with(|| a.value.cmp(&b.value)));
        facets.insert(api_name.to_string(), values);
    }

    let scalar = |sql: &str| -> i64 { conn.query_row(sql, [], |r| r.get(0)).unwrap_or(0) };

    Ok(PerformerFacets {
        facets,
        total: scalar("SELECT count(*) FROM performers"),
        with_image: scalar(
            "SELECT count(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''",
        ),
        enriched,
    })
}
pub fn get_performer_detail(conn: &Connection,
    id: i64,) -> Result<Option<Performer>> {
    let mut stmt = conn.prepare(&format!(
        // The two trailing zeros stand in for movies_count / works_count: the
        // detail payload counts its own loaded lists instead (below).
        "SELECT {}, 0, 0 FROM performers p WHERE p.id = ?1",
        PERFORMER_COLUMNS
    )).map_err(|e| e.to_string())?;

    let perf_res = stmt.query_row(params![id], map_performer_row);

    match perf_res {
        Ok(mut p) => {
            let mut m_stmt = conn.prepare(&format!(
                "SELECT {} FROM movies m \
                 JOIN movie_performers mp ON m.id = mp.movie_id \
                 WHERE mp.performer_id = ?1 \
                 ORDER BY m.release_year DESC, m.id DESC",
                MOVIE_COLUMNS
            )).map_err(|e| e.to_string())?;

            let m_rows = m_stmt.query_map(params![id], map_movie_row)
                .map_err(|e| e.to_string())?;

            let movies: Vec<Movie> = m_rows.filter_map(|r| r.ok()).collect();
            p.movies_count = Some(movies.len() as i64);
            p.movies = Some(movies);

            // Scene/episode appearances, matched through episode_performers.
            let mut e_stmt = conn.prepare(&format!(
                "{} JOIN episode_performers ep ON e.id = ep.episode_id \
                 WHERE ep.performer_id = ?1 ORDER BY m.release_year DESC, e.id DESC",
                EPISODE_SQL
            )).map_err(|e| e.to_string())?;
            let e_iter = e_stmt.query_map(params![id], map_episode_row)
                .map_err(|e| e.to_string())?;
            let episodes: Vec<Episode> = e_iter.filter_map(|r| r.ok()).collect();
            p.episodes_count = Some(episodes.len() as i64);
            p.episodes = Some(episodes);

            // Same definition of "作品" as the list query, so the number on the card
            // and the number on the page it opens agree.
            p.works_count = Some(p.movies_count.unwrap_or(0) + p.episodes_count.unwrap_or(0));

            Ok(Some(p))
        }
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(e.into()),
    }
}
