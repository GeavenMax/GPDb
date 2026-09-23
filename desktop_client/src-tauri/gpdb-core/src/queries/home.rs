//! Queries for the Home view: Spotlight banner, On This Day, and Star Spotlight.

use rusqlite::{params, Connection};
use crate::models::{
    HomeAnniversaryItem, HomeFeaturedPerformer, HomeFeedData, HomeSpotlightMovie
};
use crate::Result;

pub fn get_home_feed(conn: &Connection, month_day: Option<String>) -> Result<HomeFeedData> {
    // 1. Spotlight Movies: 12 films with rich cover and synopsis
    let spotlight_sql = "
        SELECT id, title, title_zh, studio_name, director_name, release_year,
               cover_full, cover_back, description_zh, description, rating, category
        FROM movies
        WHERE cover_full IS NOT NULL AND trim(cover_full) != ''
          AND ((description_zh IS NOT NULL AND trim(description_zh) != '') OR (description IS NOT NULL AND trim(description) != ''))
        ORDER BY RANDOM()
        LIMIT 12
    ";
    let mut stmt = conn.prepare(spotlight_sql).map_err(|e| e.to_string())?;
    let spotlight_movies: Vec<HomeSpotlightMovie> = stmt.query_map([], |row| {
        Ok(HomeSpotlightMovie {
            id: row.get(0)?,
            title: row.get(1)?,
            title_zh: row.get(2)?,
            studio_name: row.get(3)?,
            director_name: row.get(4)?,
            release_year: row.get(5)?,
            cover_full: row.get(6)?,
            cover_back: row.get(7)?,
            description_zh: row.get(8)?,
            description: row.get(9)?,
            rating: row.get(10)?,
            category: row.get(11)?,
        })
    }).map_err(|e| e.to_string())?.filter_map(|r| r.ok()).collect();

    // 2. On This Day in History: scenes released on this MM-DD
    let md = match month_day {
        Some(s) if s.trim().len() == 5 => s.trim().to_string(),
        _ => {
            // Default to today using SQLite strftime
            let today_md: String = conn.query_row(
                "SELECT strftime('%m-%d', 'now')",
                [],
                |r| r.get(0)
            ).unwrap_or_else(|_| "09-22".to_string());
            today_md
        }
    };

    let on_this_day_sql = "
        SELECT e.id, e.title, e.release_date, m.id, m.title, m.title_zh,
               COALESCE(NULLIF(trim(e.thumbnail_url), ''), m.cover_full),
               COALESCE(m.studio_name, e.studio_name),
               (strftime('%Y', 'now') - CAST(substr(e.release_date, 1, 4) AS INTEGER))
        FROM episodes e
        LEFT JOIN movies m ON e.movie_id = m.id
        WHERE substr(e.release_date, 6, 5) = ?1
        ORDER BY e.release_date DESC
        LIMIT 20
    ";
    let mut ot_stmt = conn.prepare(on_this_day_sql).map_err(|e| e.to_string())?;
    let mut on_this_day: Vec<HomeAnniversaryItem> = ot_stmt.query_map(params![md], |row| {
        Ok(HomeAnniversaryItem {
            episode_id: row.get(0)?,
            episode_title: row.get(1)?,
            release_date: row.get(2)?,
            movie_id: row.get(3)?,
            movie_title: row.get(4)?,
            movie_title_zh: row.get(5)?,
            cover_full: row.get(6)?,
            studio_name: row.get(7)?,
            years_ago: row.get::<_, Option<i64>>(8)?.unwrap_or(0).max(0),
        })
    }).map_err(|e| e.to_string())?.filter_map(|r| r.ok()).collect();

    // Fallback if exact day has no items: get items from this month
    if on_this_day.is_empty() && md.len() >= 2 {
        let month_only = &md[..2];
        let fallback_sql = "
            SELECT e.id, e.title, e.release_date, m.id, m.title, m.title_zh,
                   COALESCE(NULLIF(trim(e.thumbnail_url), ''), m.cover_full),
                   COALESCE(m.studio_name, e.studio_name),
                   (strftime('%Y', 'now') - CAST(substr(e.release_date, 1, 4) AS INTEGER))
            FROM episodes e
            LEFT JOIN movies m ON e.movie_id = m.id
            WHERE substr(e.release_date, 6, 2) = ?1
            ORDER BY RANDOM()
            LIMIT 15
        ";
        let mut fb_stmt = conn.prepare(fallback_sql).map_err(|e| e.to_string())?;
        on_this_day = fb_stmt.query_map(params![month_only], |row| {
            Ok(HomeAnniversaryItem {
                episode_id: row.get(0)?,
                episode_title: row.get(1)?,
                release_date: row.get(2)?,
                movie_id: row.get(3)?,
                movie_title: row.get(4)?,
                movie_title_zh: row.get(5)?,
                cover_full: row.get(6)?,
                studio_name: row.get(7)?,
                years_ago: row.get::<_, Option<i64>>(8)?.unwrap_or(0).max(0),
            })
        }).map_err(|e| e.to_string())?.filter_map(|r| r.ok()).collect();
    }

    // 3. Star Spotlight: Top performers with photos and multiple works
    let performer_sql = "
        SELECT p.id, p.name, p.image_url, p.build, p.hair, COUNT(mp.movie_id) as works_count
        FROM performers p
        JOIN movie_performers mp ON p.id = mp.performer_id
        WHERE p.image_url IS NOT NULL AND trim(p.image_url) != ''
        GROUP BY p.id
        HAVING works_count >= 5
        ORDER BY RANDOM()
        LIMIT 100
    ";
    let mut perf_stmt = conn.prepare(performer_sql).map_err(|e| e.to_string())?;
    let star_spotlight: Vec<HomeFeaturedPerformer> = perf_stmt.query_map([], |row| {
        Ok(HomeFeaturedPerformer {
            id: row.get(0)?,
            name: row.get(1)?,
            image_url: row.get(2)?,
            build: row.get(3)?,
            hair: row.get(4)?,
            works_count: row.get(5)?,
        })
    }).map_err(|e| e.to_string())?.filter_map(|r| r.ok()).collect();

    // 4. Quick Totals
    let total_movies: i64 = conn.query_row("SELECT count(*) FROM movies", [], |r| r.get(0)).unwrap_or(0);
    let total_episodes: i64 = conn.query_row("SELECT count(*) FROM episodes", [], |r| r.get(0)).unwrap_or(0);
    let total_performers: i64 = conn.query_row("SELECT count(*) FROM performers", [], |r| r.get(0)).unwrap_or(0);
    let total_studios: i64 = conn.query_row("SELECT count(DISTINCT NULLIF(trim(studio_name), '')) FROM movies", [], |r| r.get(0)).unwrap_or(0);

    Ok(HomeFeedData {
        spotlight_movies,
        on_this_day,
        star_spotlight,
        total_movies,
        total_episodes,
        total_performers,
        total_studios,
    })
}
