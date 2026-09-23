use crate::db::{
    self, expand_tilde, is_valid_gevi_db, is_valid_sqlite_db, load_db_config, save_db_config,
    scan_candidate_databases,
};

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
pub struct DatabaseInfo {
    pub path: Option<String>,
    pub exists: bool,
    pub valid: bool,
    pub file_size_mb: f64,
    pub custom_path: Option<String>,
    pub candidates: Vec<String>,
}

#[tauri::command]
pub fn get_database_info() -> Result<DatabaseInfo, String> {
    let cfg = load_db_config();
    let current_path = db::find_db_path();

    let (exists, valid, size_mb) = if let Some(ref p) = current_path {
        let exists = p.is_file();
        let valid = exists && is_valid_gevi_db(p);
        let size = p.metadata().map(|m| m.len() as f64 / 1_048_576.0).unwrap_or(0.0);
        (exists, valid, (size * 10.0).round() / 10.0)
    } else {
        (false, false, 0.0)
    };

    // Note: Do not passively traverse user folders here.
    // Passive scanning of Documents/Downloads/Desktop triggers macOS TCC alerts on startup.
    // Explicit scanning is done via the dedicated `scan_databases` command.
    let candidates = if let Some(ref p) = current_path {
        vec![p.to_string_lossy().to_string()]
    } else {
        Vec::new()
    };

    Ok(DatabaseInfo {
        path: current_path.map(|p| p.to_string_lossy().to_string()),
        exists,
        valid,
        file_size_mb: size_mb,
        custom_path: cfg.custom_db_path,
        candidates,
    })
}

#[tauri::command]
pub fn create_new_database(target_path: Option<String>) -> Result<DatabaseInfo, String> {
    let final_path = if let Some(p) = target_path.filter(|s| !s.trim().is_empty()) {
        expand_tilde(p.trim())
    } else {
        // Default to ~/Documents/GPDb/gevi.db
        let home = std::env::var_os("HOME").map(std::path::PathBuf::from).unwrap_or_else(|| std::path::PathBuf::from("."));
        home.join("Documents").join("GPDb").join("gevi.db")
    };

    if let Some(parent) = final_path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("无法创建数据库存储目录: {}", e))?;
    }

    // Open/create database file and apply full migration schema
    let conn = rusqlite::Connection::open(&final_path)
        .map_err(|e| format!("无法创建数据库文件: {}", e))?;
    gpdb_core::migrate::create_empty_database_schema(&conn)
        .map_err(|e| format!("数据库初始化建表与索引失败: {}", e))?;

    // Save into db_config.json
    let mut cfg = load_db_config();
    cfg.custom_db_path = Some(final_path.to_string_lossy().to_string());
    save_db_config(&cfg)?;

    get_database_info()
}

#[tauri::command]
pub fn set_custom_database_path(path: String) -> Result<DatabaseInfo, String> {
    let trimmed = path.trim();
    if trimmed.is_empty() {
        // Reset to automatic
        let mut cfg = load_db_config();
        cfg.custom_db_path = None;
        save_db_config(&cfg)?;
        return get_database_info();
    }

    let expanded = expand_tilde(trimmed);
    if !expanded.is_file() {
        return Err(format!("文件不存在：{}", expanded.display()));
    }
    if !is_valid_sqlite_db(&expanded) {
        return Err("该文件不是有效的 SQLite 格式数据库".to_string());
    }
    if !is_valid_gevi_db(&expanded) {
        return Err("该数据库缺少 movies 表，不是有效的 GEVI 数据库".to_string());
    }

    let mut cfg = load_db_config();
    cfg.custom_db_path = Some(expanded.to_string_lossy().to_string());
    save_db_config(&cfg)?;

    // Test open
    db::open_db()?;

    get_database_info()
}

#[tauri::command]
pub fn scan_databases() -> Result<Vec<String>, String> {
    let candidates = scan_candidate_databases()
        .into_iter()
        .map(|p| p.to_string_lossy().to_string())
        .collect();
    Ok(candidates)
}

fn get_export_dir() -> std::path::PathBuf {
    if let Ok(home) = std::env::var("HOME") {
        let downloads = std::path::PathBuf::from(&home).join("Downloads");
        if downloads.is_dir() {
            return downloads;
        }
        let desktop = std::path::PathBuf::from(&home).join("Desktop");
        if desktop.is_dir() {
            return desktop;
        }
        return std::path::PathBuf::from(&home);
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
pub fn pick_database_file(app: tauri::AppHandle) -> Result<Option<String>, String> {
    #[cfg(target_os = "macos")]
    {
        pick_database_file_macos(&app)
    }
    #[cfg(not(target_os = "macos"))]
    {
        let _ = app;
        Ok(None)
    }
}

#[cfg(target_os = "macos")]
fn pick_database_file_macos(app: &tauri::AppHandle) -> Result<Option<String>, String> {
    use std::ffi::c_void;
    use std::sync::mpsc::channel;

    type Id = *mut c_void;
    type Sel = *mut c_void;

    extern "C" {
        fn objc_getClass(name: *const std::os::raw::c_char) -> Id;
        fn sel_registerName(name: *const std::os::raw::c_char) -> Sel;
        fn objc_msgSend();
    }

    let (tx, rx) = channel::<Option<String>>();

    app.run_on_main_thread(move || {
        unsafe {
            let send_0: extern "C" fn(Id, Sel) -> Id = std::mem::transmute(objc_msgSend as *const ());
            let send_bool: extern "C" fn(Id, Sel, bool) -> Id =
                std::mem::transmute(objc_msgSend as *const ());
            let send_run: extern "C" fn(Id, Sel) -> isize =
                std::mem::transmute(objc_msgSend as *const ());
            let send_cstr: extern "C" fn(Id, Sel) -> *const std::os::raw::c_char =
                std::mem::transmute(objc_msgSend as *const ());

            let panel_class = objc_getClass(b"NSOpenPanel\0".as_ptr() as *const _);
            let open_panel_sel = sel_registerName(b"openPanel\0".as_ptr() as *const _);
            let panel: Id = send_0(panel_class, open_panel_sel);
            if panel.is_null() {
                let _ = tx.send(None);
                return;
            }

            let set_can_choose_files =
                sel_registerName(b"setCanChooseFiles:\0".as_ptr() as *const _);
            let set_can_choose_dirs =
                sel_registerName(b"setCanChooseDirectories:\0".as_ptr() as *const _);
            let set_allows_multiple =
                sel_registerName(b"setAllowsMultipleSelection:\0".as_ptr() as *const _);

            let _: Id = send_bool(panel, set_can_choose_files, true);
            let _: Id = send_bool(panel, set_can_choose_dirs, false);
            let _: Id = send_bool(panel, set_allows_multiple, false);

            let run_modal_sel = sel_registerName(b"runModal\0".as_ptr() as *const _);
            let res: isize = send_run(panel, run_modal_sel);

            if res == 1 {
                // NSModalResponseOK = 1
                let url_sel = sel_registerName(b"URL\0".as_ptr() as *const _);
                let url: Id = send_0(panel, url_sel);
                if !url.is_null() {
                    let path_sel = sel_registerName(b"path\0".as_ptr() as *const _);
                    let path_str_obj: Id = send_0(url, path_sel);
                    if !path_str_obj.is_null() {
                        let utf8_sel = sel_registerName(b"UTF8String\0".as_ptr() as *const _);
                        let cstr = send_cstr(path_str_obj, utf8_sel);
                        if !cstr.is_null() {
                            let rust_str =
                                std::ffi::CStr::from_ptr(cstr).to_string_lossy().to_string();
                            let _ = tx.send(Some(rust_str));
                            return;
                        }
                    }
                }
            }
            let _ = tx.send(None);
        }
    })
    .map_err(|e| format!("无法在主线程启动文件选择器: {}", e))?;

    rx.recv()
        .map_err(|e| format!("接收文件选择器响应失败: {}", e))
}

#[tauri::command]
pub fn export_database_file() -> Result<Option<String>, String> {
    let current_path = db::find_db_path().ok_or_else(|| "未找到当前数据库文件".to_string())?;

    let conn = db::open_db()?;
    let date_str: String = conn
        .query_row("SELECT strftime('%Y-%m-%d', 'now')", [], |r| r.get(0))
        .unwrap_or_else(|_| "backup".to_string());

    let base_dir = get_export_dir();
    let stem = format!("gevi_backup_{}", date_str);
    let dest_path = get_unique_filepath(&base_dir, &stem, "db");

    std::fs::copy(&current_path, &dest_path)
        .map_err(|e| format!("导出数据库文件失败: {}", e))?;

    let dest_str = dest_path.to_string_lossy().to_string();

    #[cfg(target_os = "macos")]
    {
        let _ = std::process::Command::new("open")
            .arg("-R")
            .arg(&dest_str)
            .spawn();
    }

    Ok(Some(dest_str))
}

