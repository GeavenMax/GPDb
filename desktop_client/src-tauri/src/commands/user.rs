//! The state the user writes: favorites, movie watch status, ratings, tags.

use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};

use gpdb_core::models::FavoritesResponse;
use gpdb_core::queries;

use crate::db::open_db;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct UserTag {
    pub id: i64,
    pub name: String,
    pub color: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct UserMovieData {
    pub movie_id: i64,
    pub rating: Option<f64>,
    pub status: Option<String>,
    pub notes: String,
    pub updated_at: Option<String>,
    pub tags: Vec<UserTag>,
}

#[derive(Deserialize, Debug)]
pub struct SaveUserMovieDataPayload {
    pub rating: Option<f64>,
    pub status: Option<String>,
    pub notes: Option<String>,
    pub tag_ids: Option<Vec<i64>>,
}

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

#[tauri::command]
pub fn get_movie_user_data(movie_id: i64) -> Result<UserMovieData, String> {
    let conn = open_db()?;
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS user_tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            color TEXT DEFAULT '#6366f1'
        );
        CREATE TABLE IF NOT EXISTS movie_user_tags (
            movie_id INTEGER NOT NULL,
            tag_id INTEGER NOT NULL,
            PRIMARY KEY (movie_id, tag_id)
        );
        CREATE TABLE IF NOT EXISTS user_movie_data (
            movie_id INTEGER PRIMARY KEY,
            rating REAL,
            status TEXT,
            notes TEXT,
            updated_at TEXT
        );",
    )
    .map_err(|e| e.to_string())?;

    let mut data = UserMovieData {
        movie_id,
        ..Default::default()
    };

    let row = conn
        .query_row(
            "SELECT rating, status, notes, updated_at FROM user_movie_data WHERE movie_id = ?1",
            params![movie_id],
            |r| {
                Ok((
                    r.get::<_, Option<f64>>(0)?,
                    r.get::<_, Option<String>>(1)?,
                    r.get::<_, Option<String>>(2)?.unwrap_or_default(),
                    r.get::<_, Option<String>>(3)?,
                ))
            },
        )
        .optional()
        .map_err(|e| e.to_string())?;

    if let Some((rating, status, notes, updated_at)) = row {
        data.rating = rating;
        data.status = status;
        data.notes = notes;
        data.updated_at = updated_at;
    }

    let mut stmt = conn
        .prepare(
            "SELECT t.id, t.name, t.color FROM user_tags t \
             JOIN movie_user_tags mt ON t.id = mt.tag_id \
             WHERE mt.movie_id = ?1",
        )
        .map_err(|e| e.to_string())?;

    let tags = stmt
        .query_map(params![movie_id], |r| {
            Ok(UserTag {
                id: r.get(0)?,
                name: r.get(1)?,
                color: r.get(2)?,
            })
        })
        .map_err(|e| e.to_string())?;

    data.tags = tags.filter_map(|t| t.ok()).collect();
    Ok(data)
}

#[tauri::command]
pub fn save_movie_user_data(
    movie_id: i64,
    data: SaveUserMovieDataPayload,
) -> Result<bool, String> {
    let mut conn = open_db()?;
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS user_tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            color TEXT DEFAULT '#6366f1'
        );
        CREATE TABLE IF NOT EXISTS movie_user_tags (
            movie_id INTEGER NOT NULL,
            tag_id INTEGER NOT NULL,
            PRIMARY KEY (movie_id, tag_id)
        );
        CREATE TABLE IF NOT EXISTS user_movie_data (
            movie_id INTEGER PRIMARY KEY,
            rating REAL,
            status TEXT,
            notes TEXT,
            updated_at TEXT
        );",
    )
    .map_err(|e| e.to_string())?;

    let tx = conn.transaction().map_err(|e| e.to_string())?;
    tx.execute(
        "INSERT INTO user_movie_data (movie_id, rating, status, notes, updated_at) \
         VALUES (?1, ?2, ?3, ?4, CURRENT_TIMESTAMP) \
         ON CONFLICT(movie_id) DO UPDATE SET \
            rating = excluded.rating, \
            status = excluded.status, \
            notes = excluded.notes, \
            updated_at = CURRENT_TIMESTAMP",
        params![movie_id, data.rating, data.status, data.notes.unwrap_or_default()],
    )
    .map_err(|e| e.to_string())?;

    if let Some(tag_ids) = data.tag_ids {
        tx.execute("DELETE FROM movie_user_tags WHERE movie_id = ?1", params![movie_id])
            .map_err(|e| e.to_string())?;
        for tid in tag_ids {
            tx.execute(
                "INSERT OR IGNORE INTO movie_user_tags (movie_id, tag_id) VALUES (?1, ?2)",
                params![movie_id, tid],
            )
            .map_err(|e| e.to_string())?;
        }
    }

    tx.commit().map_err(|e| e.to_string())?;
    Ok(true)
}

#[tauri::command]
pub fn get_user_tags() -> Result<Vec<UserTag>, String> {
    let conn = open_db()?;
    let _ = conn.execute(
        "CREATE TABLE IF NOT EXISTS user_tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            color TEXT DEFAULT '#6366f1'
        )",
        [],
    );
    let mut stmt = conn
        .prepare("SELECT id, name, color FROM user_tags ORDER BY id ASC")
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(UserTag {
                id: r.get(0)?,
                name: r.get(1)?,
                color: r.get(2)?,
            })
        })
        .map_err(|e| e.to_string())?;
    Ok(rows.filter_map(|r| r.ok()).collect())
}

#[tauri::command]
pub fn create_user_tag(name: String, color: Option<String>) -> Result<UserTag, String> {
    let conn = open_db()?;
    let _ = conn.execute(
        "CREATE TABLE IF NOT EXISTS user_tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            color TEXT DEFAULT '#6366f1'
        )",
        [],
    );
    let col = color.unwrap_or_else(|| "#6366f1".to_string());
    conn.execute(
        "INSERT OR IGNORE INTO user_tags (name, color) VALUES (?1, ?2)",
        params![name, col],
    )
    .map_err(|e| e.to_string())?;
    let id: i64 = conn
        .query_row(
            "SELECT id FROM user_tags WHERE name = ?1",
            params![name],
            |r| r.get(0),
        )
        .map_err(|e| e.to_string())?;
    Ok(UserTag {
        id,
        name,
        color: Some(col),
    })
}
