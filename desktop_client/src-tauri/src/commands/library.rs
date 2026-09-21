//! Paged browse commands: films, performers and their facets, studios, episodes,
//! categories, and the library-wide counts.

use gpdb_core::models::{
    DatabaseStats, EpisodeLibrary, FilterArgs, Glossaries, MoviesResponse, PerformerFacets,
    PerformerFilterArgs, PerformersResponse, StudioLibrary,
};
use gpdb_core::queries;

use crate::db::open_db;

#[tauri::command]
pub fn get_stats() -> Result<DatabaseStats, String> {
    let conn = open_db()?;
    queries::stats::get_stats(&conn).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_movies(
    filters: Option<FilterArgs>,
    page: Option<i64>,
    page_size: Option<i64>,
) -> Result<MoviesResponse, String> {
    let conn = open_db()?;
    queries::movies::get_movies(&conn, filters, page, page_size).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_performers(
    filters: Option<PerformerFilterArgs>,
    page: Option<i64>,
    page_size: Option<i64>,
) -> Result<PerformersResponse, String> {
    let conn = open_db()?;
    queries::performers::get_performers(&conn, filters, page, page_size).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_performer_facets() -> Result<PerformerFacets, String> {
    let conn = open_db()?;
    queries::performers::get_performer_facets(&conn).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_studios() -> Result<Vec<String>, String> {
    let conn = open_db()?;
    queries::studios::get_studios(&conn).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_studio_library(
    query: Option<String>,
    sort_by: Option<String>,
    page: Option<i64>,
    page_size: Option<i64>,
) -> Result<StudioLibrary, String> {
    let conn = open_db()?;
    queries::studios::get_studio_library(&conn, query, sort_by, page, page_size)
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_episode_library(
    query: Option<String>,
    sort: Option<String>,
    studio: Option<String>,
    has_zh: Option<bool>,
    has_performers: Option<bool>,
    page: Option<i64>,
    page_size: Option<i64>,
) -> Result<EpisodeLibrary, String> {
    let conn = open_db()?;
    queries::episodes::get_episode_library(
        &conn,
        query,
        sort,
        studio,
        has_zh,
        has_performers,
        page,
        page_size,
    )
    .map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_categories() -> Result<Vec<String>, String> {
    let conn = open_db()?;
    queries::movies::get_categories(&conn).map_err(|e| e.to_string())
}

/// Both translation glossaries. The desktop build has no other way to reach them —
/// its performer attributes come from Rust, not from the HTTP API.
#[tauri::command]
pub fn get_glossaries() -> Result<Glossaries, String> {
    let conn = open_db()?;
    queries::glossary::get_glossaries(&conn).map_err(|e| e.to_string())
}
