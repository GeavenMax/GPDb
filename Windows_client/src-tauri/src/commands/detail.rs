//! Commands that return one entity in full, for the detail modals.

use gpdb_core::models::{DirectorWorks, EpisodeSummary, Movie, MovieSeriesResponse, Performer, StudioWorks};
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

#[tauri::command]
pub fn get_director_works(director_name: String) -> Result<DirectorWorks, String> {
    let conn = open_db()?;
    queries::directors::get_director_works(&conn, director_name).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_episode_detail(id: i64) -> Result<EpisodeSummary, String> {
    let conn = open_db()?;
    queries::episodes::get_episode_detail(&conn, id).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_movie_series(id: i64) -> Result<Option<MovieSeriesResponse>, String> {
    let conn = open_db()?;
    queries::movies::get_movie_series(&conn, id).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_movie_series_by_root(root_title: String, studio_name: Option<String>) -> Result<Option<MovieSeriesResponse>, String> {
    let conn = open_db()?;
    queries::series::get_movie_series_by_root(&conn, &root_title, studio_name.as_deref()).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_home_feed(month_day: Option<String>) -> Result<gpdb_core::models::HomeFeedData, String> {
    let conn = open_db()?;
    let mut feed = queries::home::get_home_feed(&conn, month_day).map_err(|e| e.to_string())?;

    // 仅推荐本地磁盘缓存中真实存在头像照片的演员
    feed.star_spotlight.retain(|perf| {
        perf.image_url
            .as_deref()
            .and_then(super::cache::resolve_cache_file)
            .is_some()
    });
    feed.star_spotlight.truncate(12);

    Ok(feed)
}


