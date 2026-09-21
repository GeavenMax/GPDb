//! Library-wide counts, for the settings page and the navbar.

use rusqlite::{params, Connection};

use crate::models::DatabaseStats;
use crate::Result;

pub fn get_stats(conn: &Connection) -> Result<DatabaseStats> {
    let count = |tbl: &str| -> i64 {
        conn.query_row(&format!("SELECT count(*) FROM {}", tbl), [], |r| r.get(0)).unwrap_or(0)
    };
    let prog_count = |t: &str, s: i64| -> i64 {
        conn.query_row(
            "SELECT count(*) FROM scrape_progress WHERE item_type = ?1 AND status = ?2",
            params![t, s],
            |r| r.get(0),
        ).unwrap_or(0)
    };

    let scalar = |sql: &str| -> i64 { conn.query_row(sql, [], |r| r.get(0)).unwrap_or(0) };

    let translation_total = scalar(
        "SELECT count(*) FROM movies WHERE description IS NOT NULL AND trim(description) != ''",
    );
    let translation_done = scalar(
        "SELECT count(*) FROM movies WHERE description_zh IS NOT NULL AND trim(description_zh) != ''",
    );
    let translation_failed = scalar(
        "SELECT count(*) FROM movies WHERE (description_zh IS NULL OR trim(description_zh) = '') \
         AND COALESCE(translation_attempts, 0) >= 3 \
         AND description IS NOT NULL AND trim(description) != ''",
    );

    Ok(DatabaseStats {
        movies: count("movies"),
        performers: count("performers"),
        episodes: count("episodes"),
        movie_performers: count("movie_performers"),
        movies_scraped_ok: prog_count("movie", 200),
        movies_scraped_404: prog_count("movie", 404),
        movies_failed_500: prog_count("movie", 500),
        performers_scraped_ok: prog_count("performer", 200),
        performers_scraped_404: prog_count("performer", 404),
        performers_with_image: scalar(
            "SELECT count(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''",
        ),
        translation_total,
        translation_done,
        translation_failed,
        translation_pending: (translation_total - translation_done - translation_failed).max(0),
        translation_configured: false,
    })
}
