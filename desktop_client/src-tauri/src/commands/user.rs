//! The one piece of state the user writes: favorites.

use gpdb_core::models::FavoritesResponse;
use gpdb_core::queries;

use crate::db::open_db;

#[tauri::command]
pub fn get_favorites() -> Result<FavoritesResponse, String> {
    let conn = open_db()?;
    queries::favorites::get_favorites(&conn).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn toggle_favorite(entity_type: String, entity_key: String) -> Result<bool, String> {
    let conn = open_db()?;
    queries::favorites::toggle_favorite(&conn, entity_type, entity_key).map_err(|e| e.to_string())
}
