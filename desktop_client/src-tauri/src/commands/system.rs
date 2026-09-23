//! System-level desktop utilities.

const ICON_A: &[u8] = include_bytes!("../../../src/assets/icons/scheme-a.png");
const ICON_B: &[u8] = include_bytes!("../../../src/assets/icons/scheme-b.png");
const ICON_C: &[u8] = include_bytes!("../../../src/assets/icons/scheme-c.png");
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
    std::env::var("HOME").ok().map(std::path::PathBuf::from)
}

pub fn get_saved_icon_scheme() -> String {
    if let Some(home) = dirs_home() {
        let p = home.join(".gevi_icon_scheme");
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
        let p = home.join(".gevi_icon_scheme");
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
        "scheme-b" | "b" => ICON_B,
        "scheme-c" | "c" => ICON_C,
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_get_icon_bytes() {
        for scheme in &["scheme-a", "scheme-b", "scheme-c", "scheme-d", "unknown"] {
            let bytes = get_icon_bytes(scheme);
            assert!(!bytes.is_empty(), "Icon bytes empty for {}", scheme);
        }
    }
}
