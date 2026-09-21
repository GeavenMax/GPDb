//! GEVI+ 桌面端入口。
//!
//! 这个文件只做两件事：声明模块、注册命令表。SQL 一行都不在这里 —— 查询全在
//! `gevi-core`（它不依赖 tauri，所以能脱离 app 单独测），命令层是 `commands/` 里的
//! 薄壳。加一个功能的落点因此是确定的：queries 里写查询 → commands 里包一层 →
//! 下面这张表里注册。

mod commands;
mod db;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_log::Builder::default().build())
        .invoke_handler(tauri::generate_handler![
            commands::library::get_stats,
            commands::library::get_movies,
            commands::detail::get_movie_detail,
            commands::library::get_performers,
            commands::library::get_performer_facets,
            commands::detail::get_performer_detail,
            commands::library::get_studios,
            commands::library::get_studio_library,
            commands::detail::get_studio_works,
            commands::library::get_episode_library,
            commands::library::get_categories,
            commands::user::get_favorites,
            commands::user::toggle_favorite,
            commands::sync::run_sync,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
