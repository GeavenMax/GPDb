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

    let candidates = scan_candidate_databases()
        .into_iter()
        .map(|p| p.to_string_lossy().to_string())
        .collect();

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

#[tauri::command]
pub fn pick_database_file() -> Result<Option<String>, String> {
    #[cfg(target_os = "macos")]
    {
        let script = r#"
            try
                set chosenFile to choose file with prompt "请选择 gevi.db 数据库文件" default location (path to home folder)
                return POSIX path of chosenFile
            on error
                return ""
            end try
        "#;
        let output = std::process::Command::new("osascript")
            .arg("-e")
            .arg(script)
            .output()
            .map_err(|e| format!("无法启动系统文件选择器: {}", e))?;

        if output.status.success() {
            let path_str = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !path_str.is_empty() {
                return Ok(Some(path_str));
            }
        }
        Ok(None)
    }
    #[cfg(not(target_os = "macos"))]
    {
        Ok(None)
    }
}
