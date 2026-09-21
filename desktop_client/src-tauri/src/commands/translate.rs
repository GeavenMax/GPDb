//! 翻译服务来源：设置页编辑的 `translate_config.json`。
//!
//! 这一层就是 `server.py` 原来那几个 `handle_translate_provider_*`，只是从 HTTP
//! 搬到了进程内。分工是刻意的：
//!
//! - `gevi-core::translate_config` 负责**文件格式**，逐条对齐 `translate.py`；
//! - 这里负责**表单的传输语义** —— 哪个字段可以缺、缺了算什么。
//!
//! 两者分开，`translate_config_parity.rs` 才能把 core 单独拿去和 `translate.py`
//! 对拍。把它们揉在一起会让「和 CLI 读同一个文件」这件事失去可验证性。
//!
//! 配置文件与 `gevi.db` 同目录（`translate.py` 用的也是仓库根目录），所以换库
//! 路径会一并换掉翻译配置 —— 这正是用户把库和配置当成一套东西时的预期。

use gevi_core::translate_config as tc;
use std::path::{Path, PathBuf};

/// `translate_config.json`，与当前数据库同一个目录。
fn config_path() -> PathBuf {
    let db = crate::db::find_db_path();
    // `find_db_path` 在找不到候选时返回 `../gevi.db` 这类相对路径，其 parent 可能是
    // 空串 —— `Path::new("").join(..)` 就是当前目录，与 `translate.py` 的行为一致。
    let dir = db.parent().unwrap_or_else(|| Path::new(""));
    dir.join(tc::CONFIG_FILE_NAME)
}

/// 已保存的来源 + 可预填的预设。永远不含 API Key，只有 `has_key` / `key_hint`。
#[tauri::command]
pub fn get_translation_providers() -> Result<tc::Providers, String> {
    Ok(tc::providers(&config_path()))
}

/// 新建或更新一个来源，返回更新后的完整列表。
///
/// 默认值沿用 HTTP 时代的那套，所以界面行为不变：`type` 缺省为 `openai`，
/// `label` 缺省为来源名，`model` / `base_url` 缺省为空。只有 `api_key` 是
/// 「留空即保留」—— 表单永远拿不到已存的 Key，所以无法区分「没改」和「清空」。
#[tauri::command]
pub fn save_translation_provider(input: tc::ProfileInput) -> Result<Vec<tc::Profile>, String> {
    let path = config_path();
    let name = input.name.trim().to_string();
    if name.is_empty() {
        return Err("缺少配置名称".to_string());
    }
    let kind = match input.kind.as_deref().map(str::trim) {
        Some(k) if !k.is_empty() => k.to_string(),
        _ => "openai".to_string(),
    };
    tc::validate_type(&kind).map_err(|e| e.to_string())?;

    let label = input.label.as_deref().map(str::trim).unwrap_or("");
    let normalized = tc::ProfileInput {
        name: name.clone(),
        kind: Some(kind),
        label: Some(if label.is_empty() { name.clone() } else { label.to_string() }),
        model: Some(input.model.as_deref().map(str::trim).unwrap_or("").to_string()),
        base_url: Some(input.base_url.as_deref().map(str::trim).unwrap_or("").to_string()),
        api_key: input.api_key.clone(),
        active: input.active,
    };
    tc::save_profile(&path, &normalized).map_err(|e| e.to_string())?;
    // 勾了「立即使用」才切换，与 HTTP 处理器一致：单独保存不动当前来源。
    if input.active == Some(true) {
        tc::set_active_profile(&path, &name).map_err(|e| e.to_string())?;
    }
    Ok(tc::list_profiles(&path))
}

#[tauri::command]
pub fn activate_translation_provider(name: String) -> Result<Vec<tc::Profile>, String> {
    let path = config_path();
    tc::set_active_profile(&path, name.trim()).map_err(|e| e.to_string())?;
    Ok(tc::list_profiles(&path))
}

#[tauri::command]
pub fn delete_translation_provider(name: String) -> Result<Vec<tc::Profile>, String> {
    let path = config_path();
    tc::delete_profile(&path, name.trim()).map_err(|e| e.to_string())?;
    Ok(tc::list_profiles(&path))
}
