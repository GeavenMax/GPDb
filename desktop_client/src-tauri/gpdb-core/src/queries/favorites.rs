//! Favorites: read them back shaped for the cards, and flip one.

use rusqlite::{params, Connection, OptionalExtension};

use crate::models::{FavoriteItem, FavoritesResponse};
use crate::sql::FAVORITE_TYPES;
use crate::Result;

pub fn get_favorites(conn: &Connection) -> Result<FavoritesResponse> {
    let mut out = FavoritesResponse::default();

    // Movies: the display fields a poster card needs, plus whether a translation exists.
    let mut stmt = conn
        .prepare(
            // `m.title_zh` appended last so no index above it moves.
            "SELECT f.entity_key, f.created_at, m.title, m.release_year, m.studio_name, \
                    m.cover_full, m.description_zh IS NOT NULL, m.title_zh \
             FROM user_favorites f JOIN movies m ON m.id = CAST(f.entity_key AS INTEGER) \
             WHERE f.entity_type = 'movie' ORDER BY f.created_at DESC",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(FavoriteItem {
                key: r.get(0)?,
                created_at: r.get(1)?,
                title: r.get(2)?,
                release_year: r.get(3)?,
                studio_name: r.get(4)?,
                cover_full: r.get(5)?,
                has_zh: Some(r.get::<usize, i64>(6)? != 0),
                title_zh: r.get(7)?,
                ..Default::default()
            })
        })
        .map_err(|e| e.to_string())?;
    out.movie = rows.filter_map(|r| r.ok()).collect();

    // Performers.
    let mut stmt = conn
        .prepare(
            "SELECT f.entity_key, f.created_at, p.name, p.image_url \
             FROM user_favorites f JOIN performers p ON p.id = CAST(f.entity_key AS INTEGER) \
             WHERE f.entity_type = 'performer' ORDER BY f.created_at DESC",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(FavoriteItem {
                key: r.get(0)?,
                created_at: r.get(1)?,
                name: r.get(2)?,
                image_url: r.get(3)?,
                ..Default::default()
            })
        })
        .map_err(|e| e.to_string())?;
    out.performer = rows.filter_map(|r| r.ok()).collect();

    // Episodes, with the parent film's title so the card can say where it came from.
    let mut stmt = conn
        .prepare(
            "SELECT f.entity_key, f.created_at, e.title, e.thumbnail_url, e.movie_id, \
                    m.title, m.studio_name, e.description_zh IS NOT NULL, m.title_zh \
             FROM user_favorites f JOIN episodes e ON e.id = CAST(f.entity_key AS INTEGER) \
             LEFT JOIN movies m ON m.id = e.movie_id \
             WHERE f.entity_type = 'episode' ORDER BY f.created_at DESC",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(FavoriteItem {
                key: r.get(0)?,
                created_at: r.get(1)?,
                title: r.get(2)?,
                thumbnail_url: r.get(3)?,
                movie_id: r.get(4)?,
                movie_title: r.get(5)?,
                studio_name: r.get(6)?,
                has_zh: Some(r.get::<usize, i64>(7)? != 0),
                title_zh: r.get(8)?,
                ..Default::default()
            })
        })
        .map_err(|e| e.to_string())?;
    out.episode = rows.filter_map(|r| r.ok()).collect();

    // A studio exists only as a column on movies, so its card shows the name plus how
    // many works the library holds for it.
    {
        let mut stmt = conn
            .prepare(
                "SELECT f.entity_key, f.created_at, \
                        (SELECT COUNT(*) FROM movies WHERE studio_name = f.entity_key) \
                 FROM user_favorites f WHERE f.entity_type = 'studio' \
                 ORDER BY f.created_at DESC",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], |r| {
                let key: String = r.get(0)?;
                Ok(FavoriteItem {
                    name: Some(key.clone()),
                    key,
                    created_at: r.get(1)?,
                    works_count: r.get(2)?,
                    ..Default::default()
                })
            })
            .map_err(|e| e.to_string())?;
        out.studio = rows.filter_map(|r| r.ok()).collect();
        out.counts.insert("studio".into(), out.studio.len() as i64);
    }

    // A director is the one favorite type with a real table behind it, so the count has
    // to go through the junction. Counting `movies.director_name = entity_key` — which is
    // what this did — only ever found the films they directed *alone*: names in that
    // column are joined with " / " on rows scraped after the parser fix, and on earlier
    // rows several names sit glued together with no separator at all. Chris Ward reads 89
    // that way against 274 real films. db_manager.get_favorites carried the same bug and
    // was fixed in step.
    {
        let mut stmt = conn
            .prepare(
                "SELECT f.entity_key, f.created_at, \
                        (SELECT COUNT(*) FROM movie_directors md \
                         JOIN directors d ON d.id = md.director_id \
                         WHERE d.name = f.entity_key) \
                 FROM user_favorites f WHERE f.entity_type = 'director' \
                 ORDER BY f.created_at DESC",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map([], |r| {
                let key: String = r.get(0)?;
                Ok(FavoriteItem {
                    name: Some(key.clone()),
                    key,
                    created_at: r.get(1)?,
                    works_count: r.get(2)?,
                    ..Default::default()
                })
            })
            .map_err(|e| e.to_string())?;
        out.director = rows.filter_map(|r| r.ok()).collect();
        out.counts.insert("director".into(), out.director.len() as i64);
    }

    out.counts.insert("movie".into(), out.movie.len() as i64);
    out.counts.insert("performer".into(), out.performer.len() as i64);
    out.counts.insert("episode".into(), out.episode.len() as i64);

    Ok(out)
}

/// Flip one favorite, returning whether it is favorited afterwards.
pub fn toggle_favorite(conn: &Connection,
    entity_type: String, entity_key: String,) -> Result<bool> {
    if !FAVORITE_TYPES.contains(&entity_type.as_str()) {
        return Err(format!("未知的收藏类型 '{}'", entity_type).into());
    }
    let key = entity_key.trim();
    if key.is_empty() {
        return Err("收藏对象不能为空".to_string().into());
    }

    // Toggling is read-then-write, so it runs in one transaction: two rapid clicks
    // must not both read "absent" and both insert.
    let tx = conn.unchecked_transaction().map_err(|e| e.to_string())?;
    let existing: Option<i64> = tx
        .query_row(
            "SELECT 1 FROM user_favorites WHERE entity_type = ?1 AND entity_key = ?2",
            params![entity_type, key],
            |r| r.get(0),
        )
        .optional()
        .map_err(|e| e.to_string())?;

    let now_favorite = match existing {
        Some(_) => {
            tx.execute(
                "DELETE FROM user_favorites WHERE entity_type = ?1 AND entity_key = ?2",
                params![entity_type, key],
            )
            .map_err(|e| e.to_string())?;
            false
        }
        None => {
            tx.execute(
                "INSERT OR IGNORE INTO user_favorites (entity_type, entity_key) VALUES (?1, ?2)",
                params![entity_type, key],
            )
            .map_err(|e| e.to_string())?;
            true
        }
    };
    tx.commit().map_err(|e| e.to_string())?;
    Ok(now_favorite)
}
