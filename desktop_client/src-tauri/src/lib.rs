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
        .register_asynchronous_uri_scheme_protocol("gpdb-img", |_app, req, responder| {
            tauri::async_runtime::spawn_blocking(move || {
                let resp = commands::cache::handle_image_protocol(&req);
                responder.respond(resp);
            });
        })
        .plugin(tauri_plugin_log::Builder::default().build())
        .setup(|app| {
            #[cfg(target_os = "windows")]
            {
                std::env::set_var(
                    "WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS",
                    "--enable-features=msWebView2EnableDraggableRegions --disable-features=CalculateNativeWinOcclusion --high-dpi-support=1 --force-device-scale-factor=1 --enable-gpu-rasterization --enable-zero-copy",
                );
            }

            #[cfg(target_os = "macos")]
            {
                let scheme = commands::system::get_saved_icon_scheme();
                let bytes = commands::system::get_icon_bytes(&scheme);
                let bytes_vec = bytes.to_vec();
                let app_handle = app.handle().clone();
                let _ = app_handle.run_on_main_thread(move || {
                    let _ = commands::system::set_dock_icon_macos(&bytes_vec);
                });
            }

            // 注册系统托盘 (System Tray) 与点击唤醒逻辑
            let _ = commands::system::setup_tray(app.handle());

            // 绑定主窗口关闭事件（支持「关闭窗口时最小化到系统托盘」）
            use tauri::Manager;
            if let Some(w) = app.get_webview_window("main") {
                let w_clone = w.clone();
                w.on_window_event(move |event| {
                    if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                        if commands::system::should_close_to_tray() {
                            api.prevent_close();
                            let _ = w_clone.hide();
                        }
                    }
                });
            }

            Ok(())
        })
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
            commands::translate::export_translations,
            commands::translate::import_translations,
            commands::translate::fetch_provider_models,
            commands::translate::translate_movie,
            commands::translate::translate_episode,
            commands::translate::test_translation_provider,
            commands::sync::run_sync,
            commands::sync::start_scraper,
            commands::sync::stop_scraper,
            commands::sync::get_scraper_status,
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
            commands::system::save_share_card_image,
            commands::system::copy_image_to_clipboard,
            commands::system::save_update_file,
            commands::system::install_update_file,
            commands::system::set_window_material,
            commands::system::set_close_to_tray,
            commands::system::get_close_to_tray,
            commands::system::set_taskbar_progress,
            commands::environment::check_runtime_environment,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
