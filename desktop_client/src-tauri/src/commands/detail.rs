//! Commands that return one entity in full, for the detail modals.

use gpdb_core::models::{Movie, Performer, StudioWorks};
use gpdb_core::queries;

use crate::db::open_db;

#[tauri::command]
pub fn get_movie_detail(id: i64) -> Result<Option<Movie>, String> {
    let conn = open_db()?;
    queries::movies::get_movie_detail(&conn, id).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_performer_detail(id: i64) -> Result<Option<Performer>, String> {
    let conn = open_db()?;
    queries::performers::get_performer_detail(&conn, id).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_studio_works(studio_name: String) -> Result<StudioWorks, String> {
    let conn = open_db()?;
    queries::studios::get_studio_works(&conn, studio_name).map_err(|e| e.to_string())
}
