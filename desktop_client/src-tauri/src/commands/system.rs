//! System-level desktop utilities.

const ICON_A: &[u8] = include_bytes!("../../../src/assets/icons/scheme-a.png");
const ICON_D: &[u8] = include_bytes!("../../../src/assets/icons/scheme-d.png");

#[tauri::command]
pub fn open_external_url(url: String) -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        std::process::Command::new("open")
            .arg(&url)
            .spawn()
            .map_err(|e| e.to_string())?;
    }
    #[cfg(target_os = "windows")]
    {
        std::process::Command::new("cmd")
            .args(["/c", "start", "", &url])
            .spawn()
            .map_err(|e| e.to_string())?;
    }
    #[cfg(not(any(target_os = "macos", target_os = "windows")))]
    {
        std::process::Command::new("xdg-open")
            .arg(&url)
            .spawn()
            .map_err(|e| e.to_string())?;
    }
    Ok(())
}

fn dirs_home() -> Option<std::path::PathBuf> {
    dirs::home_dir().or_else(|| std::env::var_os("HOME").map(std::path::PathBuf::from))
}

pub fn get_saved_icon_scheme() -> String {
    if let Some(home) = dirs_home() {
        let p = home.join(".gpdb_icon_scheme");
        if let Ok(content) = std::fs::read_to_string(&p) {
            let trimmed = content.trim();
            if !trimmed.is_empty() {
                return trimmed.to_string();
            }
        }
    }
    "scheme-a".to_string()
}

pub fn save_icon_scheme(scheme_id: &str) {
    if let Some(home) = dirs_home() {
        let p = home.join(".gpdb_icon_scheme");
        let _ = std::fs::write(&p, scheme_id);
    }
}

#[tauri::command]
pub fn set_dock_icon(app: tauri::AppHandle, scheme_id: String) -> Result<(), String> {
    save_icon_scheme(&scheme_id);
    let bytes = get_icon_bytes(&scheme_id);

    #[cfg(target_os = "macos")]
    {
        let bytes_vec = bytes.to_vec();
        app.run_on_main_thread(move || {
            if let Err(e) = set_dock_icon_macos(&bytes_vec) {
                log::error!("[set_dock_icon] Failed to set dock icon: {}", e);
            }
        })
        .map_err(|e| format!("Failed to dispatch to main thread: {}", e))?;
    }

    let _ = bytes;
    let _ = app;
    Ok(())
}

pub fn get_icon_bytes(scheme_id: &str) -> &'static [u8] {
    match scheme_id.to_lowercase().as_str() {
        "scheme-a" | "a" => ICON_A,
        "scheme-d" | "d" => ICON_D,
        _ => ICON_A,
    }
}

#[cfg(target_os = "macos")]
pub fn set_dock_icon_macos(png_bytes: &[u8]) -> Result<(), String> {
    use std::ffi::c_void;
    type Id = *mut c_void;
    type Sel = *mut c_void;

    extern "C" {
        fn objc_getClass(name: *const std::os::raw::c_char) -> Id;
        fn sel_registerName(name: *const std::os::raw::c_char) -> Sel;
        fn objc_msgSend();
    }

    unsafe {
        let send_0: extern "C" fn(Id, Sel) -> Id = std::mem::transmute(objc_msgSend as *const ());
        let send_bytes_len: extern "C" fn(Id, Sel, *const c_void, usize) -> Id =
            std::mem::transmute(objc_msgSend as *const ());
        let send_1: extern "C" fn(Id, Sel, Id) -> Id = std::mem::transmute(objc_msgSend as *const ());

        // [NSApplication sharedApplication]
        let ns_app_class = objc_getClass(b"NSApplication\0".as_ptr() as *const _);
        let shared_app_sel = sel_registerName(b"sharedApplication\0".as_ptr() as *const _);
        let app: Id = send_0(ns_app_class, shared_app_sel);
        if app.is_null() {
            return Err("Unable to retrieve NSApplication.sharedApplication".into());
        }

        // [NSData dataWithBytes:bytes length:len]
        let ns_data_class = objc_getClass(b"NSData\0".as_ptr() as *const _);
        let data_with_bytes_sel = sel_registerName(b"dataWithBytes:length:\0".as_ptr() as *const _);
        let data: Id = send_bytes_len(
            ns_data_class,
            data_with_bytes_sel,
            png_bytes.as_ptr() as *const c_void,
            png_bytes.len(),
        );

        if data.is_null() {
            return Err("Failed to create NSData from icon bytes".to_string());
        }

        // [[NSImage alloc] initWithData:data]
        let ns_image_class = objc_getClass(b"NSImage\0".as_ptr() as *const _);
        let alloc_sel = sel_registerName(b"alloc\0".as_ptr() as *const _);
        let init_with_data_sel = sel_registerName(b"initWithData:\0".as_ptr() as *const _);
        let allocated_image: Id = send_0(ns_image_class, alloc_sel);
        let image: Id = send_1(allocated_image, init_with_data_sel, data);

        if image.is_null() {
            return Err("Failed to create NSImage from data".to_string());
        }

        // [app setApplicationIconImage:image]
        let set_icon_sel = sel_registerName(b"setApplicationIconImage:\0".as_ptr() as *const _);
        let _: Id = send_1(app, set_icon_sel, image);

        // [[app dockTile] display] - Force Dock to repaint immediately
        let dock_tile_sel = sel_registerName(b"dockTile\0".as_ptr() as *const _);
        let dock_tile: Id = send_0(app, dock_tile_sel);
        if !dock_tile.is_null() {
            let display_sel = sel_registerName(b"display\0".as_ptr() as *const _);
            let _: Id = send_0(dock_tile, display_sel);
        }

        // Release the allocated NSImage
        let release_sel = sel_registerName(b"release\0".as_ptr() as *const _);
        let _: Id = send_0(image, release_sel);

        Ok(())
    }
}

#[tauri::command]
pub fn save_share_card_image(filename: String, base64_png: String) -> Result<String, String> {
    use base64::Engine;

    let raw_b64 = if let Some(idx) = base64_png.find(',') {
        &base64_png[idx + 1..]
    } else {
        &base64_png
    };

    let bytes = base64::engine::general_purpose::STANDARD
        .decode(raw_b64.trim())
        .map_err(|e| format!("Base64 解码失败: {}", e))?;

    let base_dir = dirs::download_dir()
        .or_else(dirs::picture_dir)
        .or_else(dirs::desktop_dir)
        .or_else(dirs::home_dir)
        .unwrap_or_else(|| std::path::PathBuf::from("."));

    let stem = if filename.trim().is_empty() {
        "GPDb_Share_Card".to_string()
    } else {
        filename
            .chars()
            .map(|c| if matches!(c, '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|') { '_' } else { c })
            .collect::<String>()
    };

    let dest_path = crate::commands::database::get_unique_filepath(&base_dir, &stem, "png");

    std::fs::write(&dest_path, bytes)
        .map_err(|e| format!("写入分享图片文件失败: {}", e))?;

    let dest_str = dest_path.to_string_lossy().to_string();
    crate::commands::database::show_in_folder(&dest_str);

    log::info!("[save_share_card_image] Successfully saved share card to: {}", dest_str);
    Ok(dest_str)
}

#[tauri::command]
pub fn copy_image_to_clipboard(base64_png: String) -> Result<(), String> {
    use base64::Engine;

    let raw_b64 = if let Some(idx) = base64_png.find(',') {
        &base64_png[idx + 1..]
    } else {
        &base64_png
    };

    let bytes = base64::engine::general_purpose::STANDARD
        .decode(raw_b64.trim())
        .map_err(|e| format!("Base64 解码失败: {}", e))?;

    #[cfg(target_os = "macos")]
    {
        use std::ffi::c_void;
        type Id = *mut c_void;
        type Sel = *mut c_void;

        extern "C" {
            fn objc_getClass(name: *const std::os::raw::c_char) -> Id;
            fn sel_registerName(name: *const std::os::raw::c_char) -> Sel;
            fn objc_msgSend();
        }

        unsafe {
            let send_0: extern "C" fn(Id, Sel) -> Id = std::mem::transmute(objc_msgSend as *const ());
            let send_data_create: extern "C" fn(Id, Sel, *const u8, usize) -> Id =
                std::mem::transmute(objc_msgSend as *const ());
            let send_set_data: extern "C" fn(Id, Sel, Id, Id) -> bool =
                std::mem::transmute(objc_msgSend as *const ());
            let send_str: extern "C" fn(Id, Sel, *const std::os::raw::c_char) -> Id =
                std::mem::transmute(objc_msgSend as *const ());

            let pb_class = objc_getClass(b"NSPasteboard\0".as_ptr() as *const _);
            let gen_sel = sel_registerName(b"generalPasteboard\0".as_ptr() as *const _);
            let pb: Id = send_0(pb_class, gen_sel);
            if pb.is_null() {
                return Err("获取 NSPasteboard 失败".to_string());
            }

            let clear_sel = sel_registerName(b"clearContents\0".as_ptr() as *const _);
            let send_clear: extern "C" fn(Id, Sel) -> isize = std::mem::transmute(objc_msgSend as *const ());
            let _: isize = send_clear(pb, clear_sel);

            let data_class = objc_getClass(b"NSData\0".as_ptr() as *const _);
            let data_sel = sel_registerName(b"dataWithBytes:length:\0".as_ptr() as *const _);
            let ns_data: Id = send_data_create(data_class, data_sel, bytes.as_ptr(), bytes.len());

            let str_class = objc_getClass(b"NSString\0".as_ptr() as *const _);
            let str_sel = sel_registerName(b"stringWithUTF8String:\0".as_ptr() as *const _);
            let png_type_str: Id = send_str(str_class, str_sel, b"public.png\0".as_ptr() as *const _);

            let set_data_sel = sel_registerName(b"setData:forType:\0".as_ptr() as *const _);
            let ok = send_set_data(pb, set_data_sel, ns_data, png_type_str);

            if !ok {
                return Err("写入系统剪贴板失败".to_string());
            }
        }
        return Ok(());
    }

    #[cfg(not(target_os = "macos"))]
    {
        let _ = bytes;
        Ok(())
    }
}

#[tauri::command]
pub fn save_update_file(filename: String, base64_data: String) -> Result<String, String> {
    use base64::Engine;

    let raw_b64 = if let Some(idx) = base64_data.find(',') {
        &base64_data[idx + 1..]
    } else {
        &base64_data
    };

    let bytes = base64::engine::general_purpose::STANDARD
        .decode(raw_b64.trim())
        .map_err(|e| format!("Base64 解码失败: {}", e))?;

    let dest_dir = std::env::temp_dir();
    let safe_filename = filename
        .chars()
        .map(|c| if matches!(c, '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|') { '_' } else { c })
        .collect::<String>();

    let dest_path = dest_dir.join(&safe_filename);
    std::fs::write(&dest_path, bytes)
        .map_err(|e| format!("写入安装包失败: {}", e))?;

    let dest_str = dest_path.to_string_lossy().to_string();
    log::info!("[save_update_file] Saved update package to: {}", dest_str);
    Ok(dest_str)
}

#[tauri::command]
pub fn install_update_file(filepath: String) -> Result<(), String> {
    log::info!("[install_update_file] Launching installer for: {}", filepath);
    #[cfg(target_os = "macos")]
    {
        std::process::Command::new("open")
            .arg(&filepath)
            .spawn()
            .map_err(|e| format!("打开安装文件失败: {}", e))?;
        Ok(())
    }
    #[cfg(target_os = "windows")]
    {
        std::process::Command::new(&filepath)
            .spawn()
            .map_err(|e| format!("启动安装程序失败: {}", e))?;
        std::process::exit(0);
    }
    #[cfg(not(any(target_os = "macos", target_os = "windows")))]
    {
        std::process::Command::new("xdg-open")
            .arg(&filepath)
            .spawn()
            .map_err(|e| format!("打开安装文件失败: {}", e))?;
        Ok(())
    }
}

use std::sync::atomic::{AtomicBool, Ordering};

static CLOSE_TO_TRAY: AtomicBool = AtomicBool::new(false);

#[tauri::command]
pub fn set_close_to_tray(enabled: bool) -> Result<(), String> {
    CLOSE_TO_TRAY.store(enabled, Ordering::Relaxed);
    Ok(())
}

#[tauri::command]
pub fn get_close_to_tray() -> Result<bool, String> {
    Ok(CLOSE_TO_TRAY.load(Ordering::Relaxed))
}

pub fn should_close_to_tray() -> bool {
    CLOSE_TO_TRAY.load(Ordering::Relaxed)
}

#[tauri::command]
pub fn set_window_material(window: tauri::WebviewWindow, material: String) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        use tauri::utils::config::{WindowEffectsConfig, WindowEffect};
        let effects = match material.as_str() {
            "mica" => Some(WindowEffectsConfig {
                effects: vec![WindowEffect::Mica],
                state: None,
                radius: None,
                color: None,
            }),
            "tabbed" => Some(WindowEffectsConfig {
                effects: vec![WindowEffect::Tabbed],
                state: None,
                radius: None,
                color: None,
            }),
            "acrylic" => Some(WindowEffectsConfig {
                effects: vec![WindowEffect::Acrylic],
                state: None,
                radius: None,
                color: None,
            }),
            _ => None,
        };
        let _ = window.set_effects(effects);
    }
    #[cfg(target_os = "macos")]
    {
        use tauri::utils::config::{WindowEffectsConfig, WindowEffect};
        let effects = match material.as_str() {
            "acrylic" | "mica" | "tabbed" => Some(WindowEffectsConfig {
                effects: vec![WindowEffect::WindowBackground],
                state: None,
                radius: None,
                color: None,
            }),
            _ => None,
        };
        let _ = window.set_effects(effects);
    }
    let _ = window;
    let _ = material;
    Ok(())
}

pub fn setup_tray(app: &tauri::AppHandle) -> Result<(), Box<dyn std::error::Error>> {
    use tauri::menu::{MenuBuilder, MenuItemBuilder, PredefinedMenuItem};
    use tauri::tray::{TrayIconBuilder, TrayIconEvent, MouseButton, MouseButtonState};
    use tauri::Manager;

    let show = MenuItemBuilder::with_id("show", "显示主界面 (Show)").build(app)?;
    let hide = MenuItemBuilder::with_id("hide", "最小化到托盘 (Hide)").build(app)?;
    let privacy = MenuItemBuilder::with_id("privacy", "截屏防窥模式 (Privacy Shield)").build(app)?;
    let sep = PredefinedMenuItem::separator(app)?;
    let quit = MenuItemBuilder::with_id("quit", "退出 GPDb (Quit)").build(app)?;
    let menu = MenuBuilder::new(app).items(&[&show, &hide, &privacy, &sep, &quit]).build()?;

    let mut builder = TrayIconBuilder::new()
        .menu(&menu)
        .show_menu_on_left_click(false)
        .tooltip("GPDb — 离线影视库")
        .on_menu_event(|app, event| {
            match event.id.as_ref() {
                "show" => {
                    if let Some(w) = app.get_webview_window("main") {
                        let _ = w.show();
                        let _ = w.unminimize();
                        let _ = w.set_focus();
                    }
                }
                "hide" => {
                    if let Some(w) = app.get_webview_window("main") {
                        let _ = w.hide();
                    }
                }
                "privacy" => {
                    use tauri::Emitter;
                    let _ = app.emit("toggle-privacy-mode", ());
                }
                "quit" => {
                    app.exit(0);
                }
                _ => {}
            }
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event {
                let app = tray.app_handle();
                if let Some(w) = app.get_webview_window("main") {
                    if let Ok(is_visible) = w.is_visible() {
                        if is_visible {
                            let _ = w.hide();
                        } else {
                            let _ = w.show();
                            let _ = w.unminimize();
                            let _ = w.set_focus();
                        }
                    }
                }
            }
        });

    if let Some(icon) = app.default_window_icon() {
        builder = builder.icon(icon.clone());
    }

    builder.build(app)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_get_icon_bytes() {
        for scheme in &["scheme-a", "scheme-d", "unknown"] {
            let bytes = get_icon_bytes(scheme);
            assert!(!bytes.is_empty(), "Icon bytes empty for {}", scheme);
        }
    }

    #[test]
    fn test_close_to_tray() {
        set_close_to_tray(true).unwrap();
        assert!(should_close_to_tray());
        set_close_to_tray(false).unwrap();
        assert!(!should_close_to_tray());
    }
}
