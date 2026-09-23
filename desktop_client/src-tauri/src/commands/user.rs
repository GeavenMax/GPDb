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

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ExportedTag {
    pub id: i64,
    pub name: String,
    pub color: Option<String>,
    pub created_at: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ExportedMovieUserData {
    pub movie_id: i64,
    pub rating: Option<f64>,
    pub status: Option<String>,
    pub notes: Option<String>,
    pub updated_at: Option<String>,
    pub tag_ids: Vec<i64>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ExportedFavorite {
    pub entity_type: String,
    pub entity_key: String,
    pub created_at: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct UserBackupData {
    pub version: i32,
    pub export_time: String,
    pub tags: Vec<ExportedTag>,
    pub movie_user_data: Vec<ExportedMovieUserData>,
    pub favorites: Vec<ExportedFavorite>,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct ImportUserDataResult {
    pub tags_imported: usize,
    pub movies_updated: usize,
    pub favorites_imported: usize,
}

#[tauri::command]
pub fn export_user_data() -> Result<UserBackupData, String> {
    let conn = open_db()?;
    let _ = conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS user_tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            color TEXT DEFAULT '#6366f1',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS user_favorites (
            entity_type TEXT NOT NULL,
            entity_key TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (entity_type, entity_key)
        );",
    );

    // 1. Tags
    let mut stmt = conn
        .prepare("SELECT id, name, color, created_at FROM user_tags ORDER BY id ASC")
        .map_err(|e| e.to_string())?;
    let tags: Vec<ExportedTag> = stmt
        .query_map([], |r| {
            Ok(ExportedTag {
                id: r.get(0)?,
                name: r.get(1)?,
                color: r.get(2)?,
                created_at: r.get(3)?,
            })
        })
        .map_err(|e| e.to_string())?
        .filter_map(|r| r.ok())
        .collect();

    // 2. Movie user data
    let mut stmt = conn
        .prepare("SELECT movie_id, rating, status, notes, updated_at FROM user_movie_data ORDER BY movie_id ASC")
        .map_err(|e| e.to_string())?;
    let mut movie_data_rows: Vec<(i64, Option<f64>, Option<String>, Option<String>, Option<String>)> = stmt
        .query_map([], |r| {
            Ok((
                r.get(0)?,
                r.get(1)?,
                r.get(2)?,
                r.get(3)?,
                r.get(4)?,
            ))
        })
        .map_err(|e| e.to_string())?
        .filter_map(|r| r.ok())
        .collect();

    let mut movie_user_data = Vec::with_capacity(movie_data_rows.len());
    for (mid, rating, status, notes, updated_at) in movie_data_rows.drain(..) {
        let mut tag_stmt = conn
            .prepare("SELECT tag_id FROM movie_user_tags WHERE movie_id = ?1 ORDER BY tag_id ASC")
            .map_err(|e| e.to_string())?;
        let tag_ids: Vec<i64> = tag_stmt
            .query_map(params![mid], |r| r.get(0))
            .map_err(|e| e.to_string())?
            .filter_map(|r| r.ok())
            .collect();

        movie_user_data.push(ExportedMovieUserData {
            movie_id: mid,
            rating,
            status,
            notes,
            updated_at,
            tag_ids,
        });
    }

    // 3. Favorites
    let mut stmt = conn
        .prepare("SELECT entity_type, entity_key, created_at FROM user_favorites ORDER BY created_at ASC")
        .map_err(|e| e.to_string())?;
    let favorites: Vec<ExportedFavorite> = stmt
        .query_map([], |r| {
            Ok(ExportedFavorite {
                entity_type: r.get(0)?,
                entity_key: r.get(1)?,
                created_at: r.get(2)?,
            })
        })
        .map_err(|e| e.to_string())?
        .filter_map(|r| r.ok())
        .collect();

    let now_iso: String = conn
        .query_row("SELECT datetime('now')", [], |r| r.get(0))
        .unwrap_or_else(|_| "1970-01-01T00:00:00Z".to_string());
    Ok(UserBackupData {
        version: 2,
        export_time: now_iso,
        tags,
        movie_user_data,
        favorites,
    })
}

fn get_export_dir() -> std::path::PathBuf {
    if let Some(dl) = dirs::download_dir() {
        if dl.is_dir() {
            return dl;
        }
    }
    if let Some(desk) = dirs::desktop_dir() {
        if desk.is_dir() {
            return desk;
        }
    }
    if let Some(home) = dirs::home_dir() {
        return home;
    }
    std::env::current_dir().unwrap_or_else(|_| std::path::PathBuf::from("."))
}

fn get_unique_filepath(dir: &std::path::Path, stem: &str, ext: &str) -> std::path::PathBuf {
    let direct = dir.join(format!("{}.{}", stem, ext));
    if !direct.exists() {
        return direct;
    }
    for i in 1..1000 {
        let candidate = dir.join(format!("{} ({}).{}", stem, i, ext));
        if !candidate.exists() {
            return candidate;
        }
    }
    dir.join(format!(
        "{}_{}.{}",
        stem,
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs(),
        ext
    ))
}

#[tauri::command]
pub fn export_user_data_file() -> Result<Option<String>, String> {
    let backup_data = export_user_data()?;
    let json_content = serde_json::to_string_pretty(&backup_data)
        .map_err(|e| format!("序列化备份数据失败: {}", e))?;

    let conn = open_db()?;
    let date_str: String = conn
        .query_row("SELECT strftime('%Y-%m-%d', 'now')", [], |r| r.get(0))
        .unwrap_or_else(|_| "backup".to_string());

    let base_dir = get_export_dir();
    let stem = format!("gpdb_user_backup_{}", date_str);
    let dest_path = get_unique_filepath(&base_dir, &stem, "json");

    std::fs::write(&dest_path, json_content)
        .map_err(|e| format!("写入备份文件失败: {}", e))?;

    let dest_str = dest_path.to_string_lossy().to_string();
    crate::commands::database::show_in_folder(&dest_str);

    Ok(Some(dest_str))
}

#[tauri::command]
pub fn import_user_data(data: serde_json::Value) -> Result<ImportUserDataResult, String> {
    let mut conn = open_db()?;
    let _ = conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS user_tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            color TEXT DEFAULT '#6366f1',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS user_favorites (
            entity_type TEXT NOT NULL,
            entity_key TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (entity_type, entity_key)
        );",
    );

    let mut result = ImportUserDataResult::default();
    let mut tag_id_map: std::collections::HashMap<i64, i64> = std::collections::HashMap::new();

    let tx = conn.transaction().map_err(|e| e.to_string())?;

    // 1. Import tags
    if let Some(tags) = data.get("tags").and_then(|v| v.as_array()) {
        for t in tags {
            if let Some(name) = t.get("name").and_then(|v| v.as_str()) {
                let color = t.get("color").and_then(|v| v.as_str()).unwrap_or("#6366f1");
                let _ = tx.execute(
                    "INSERT OR IGNORE INTO user_tags (name, color) VALUES (?1, ?2)",
                    params![name, color],
                );
                if let Ok(new_id) = tx.query_row(
                    "SELECT id FROM user_tags WHERE name = ?1",
                    params![name],
                    |r| r.get::<_, i64>(0),
                ) {
                    if let Some(old_id) = t.get("id").and_then(|v| v.as_i64()) {
                        tag_id_map.insert(old_id, new_id);
                    }
                }
                result.tags_imported += 1;
            }
        }
    }

    // 2. Import movie user data
    if let Some(movies) = data.get("movie_user_data").and_then(|v| v.as_array()) {
        for md in movies {
            if let Some(mid) = md.get("movie_id").and_then(|v| v.as_i64()) {
                let rating = md.get("rating").and_then(|v| v.as_f64());
                let status = md.get("status").and_then(|v| v.as_str());
                let notes = md.get("notes").and_then(|v| v.as_str()).unwrap_or_default();

                let _ = tx.execute(
                    "INSERT INTO user_movie_data (movie_id, rating, status, notes, updated_at)
                     VALUES (?1, ?2, ?3, ?4, CURRENT_TIMESTAMP)
                     ON CONFLICT(movie_id) DO UPDATE SET
                        rating = excluded.rating,
                        status = excluded.status,
                        notes = excluded.notes,
                        updated_at = CURRENT_TIMESTAMP",
                    params![mid, rating, status, notes],
                );

                if let Some(tag_ids) = md.get("tag_ids").and_then(|v| v.as_array()) {
                    let _ = tx.execute("DELETE FROM movie_user_tags WHERE movie_id = ?1", params![mid]);
                    for tid_val in tag_ids {
                        if let Some(old_tid) = tid_val.as_i64() {
                            let mapped_id = tag_id_map.get(&old_tid).copied().unwrap_or(old_tid);
                            let _ = tx.execute(
                                "INSERT OR IGNORE INTO movie_user_tags (movie_id, tag_id) VALUES (?1, ?2)",
                                params![mid, mapped_id],
                            );
                        }
                    }
                }
                result.movies_updated += 1;
            }
        }
    }

    // 3. Import favorites
    if let Some(favorites) = data.get("favorites").and_then(|v| v.as_array()) {
        for fav in favorites {
            let entity_type = fav.get("entity_type").and_then(|v| v.as_str());
            let entity_key = fav.get("entity_key").and_then(|v| v.as_str());
            if let (Some(etype), Some(ekey)) = (entity_type, entity_key) {
                if !etype.is_empty() && !ekey.is_empty() {
                    let _ = tx.execute(
                        "INSERT OR IGNORE INTO user_favorites (entity_type, entity_key, created_at)
                         VALUES (?1, ?2, CURRENT_TIMESTAMP)",
                        params![etype, ekey],
                    );
                    result.favorites_imported += 1;
                }
            }
        }
    }

    tx.commit().map_err(|e| e.to_string())?;
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_export_and_import_user_data() {
        let export_res = export_user_data();
        assert!(export_res.is_ok(), "export_user_data should succeed");
        let data = export_res.unwrap();
        assert_eq!(data.version, 2);

        let json_val = serde_json::to_value(&data).expect("serialize to value");
        let import_res = import_user_data(json_val);
        assert!(import_res.is_ok(), "import_user_data should succeed");
    }
}

