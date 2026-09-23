//! GPDb 桌面端入口。
//!
//! 这个文件只做两件事：声明模块、注册命令表。SQL 一行都不在这里 —— 查询全在
//! `gpdb-core`（它不依赖 tauri，所以能脱离 app 单独测），命令层是 `commands/` 里的
//! 薄壳。加一个功能的落点因此是确定的：queries 里写查询 → commands 里包一层 →
//! 下面这张表里注册。

// `pub` on these two so `tests/` can reach the command layer. A binary crate exposes
// nothing to anyone else either way; it just makes the wiring testable.
pub mod commands;
pub mod db;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .register_uri_scheme_protocol("gpdb-img", |_app, req| {
            commands::cache::handle_image_protocol(&req)
        })
        .plugin(tauri_plugin_log::Builder::default().build())
        .invoke_handler(tauri::generate_handler![
            commands::library::get_stats,
            commands::library::get_movies,
            commands::detail::get_movie_detail,
            commands::detail::get_movie_series,
            commands::detail::get_movie_series_by_root,
            commands::library::get_series_collections,
            commands::library::refresh_series_index,
            commands::library::get_performers,
            commands::library::get_performer_facets,
            commands::detail::get_performer_detail,
            commands::detail::get_episode_detail,
            commands::detail::get_home_feed,
            commands::library::get_studios,
            commands::library::get_studio_library,
            commands::detail::get_studio_works,
            commands::library::get_director_library,
            commands::detail::get_director_works,
            commands::library::get_episode_library,
            commands::library::get_categories,
            commands::library::get_glossaries,
            commands::user::get_favorites,
            commands::user::toggle_favorite,
            commands::user::get_movie_user_data,
            commands::user::save_movie_user_data,
            commands::user::get_user_tags,
            commands::user::create_user_tag,
            commands::user::export_user_data,
            commands::user::export_user_data_file,
            commands::user::import_user_data,
            commands::translate::get_translation_providers,
            commands::translate::save_translation_provider,
            commands::translate::activate_translation_provider,
            commands::translate::delete_translation_provider,
            commands::translate::run_ai_analysis,
            commands::sync::run_sync,
            commands::database::get_database_info,
            commands::database::create_new_database,
            commands::database::set_custom_database_path,
            commands::database::scan_databases,
            commands::database::pick_database_file,
            commands::database::export_database_file,
            commands::cache::get_cache_stats,
            commands::cache::clear_cache,
            commands::system::open_external_url,
            commands::system::set_dock_icon,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
