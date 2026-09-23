//! 翻译服务来源：设置页编辑的 `translate_config.json`。
//!
//! 这一层就是 `server.py` 原来那几个 `handle_translate_provider_*`，只是从 HTTP
//! 搬到了进程内。分工是刻意的：
//!
//! - `gpdb-core::translate_config` 负责**文件格式**，逐条对齐 `translate.py`；
//! - 这里负责**表单的传输语义** —— 哪个字段可以缺、缺了算什么。
//!
//! 两者分开，`translate_config_parity.rs` 才能把 core 单独拿去和 `translate.py`
//! 对拍。把它们揉在一起会让「和 CLI 读同一个文件」这件事失去可验证性。
//!
//! 配置文件与 `GPDb.db` 同目录（`translate.py` 用的也是仓库根目录），所以换库
//! 路径会一并换掉翻译配置 —— 这正是用户把库和配置当成一套东西时的预期。

use gpdb_core::translate_config as tc;
use std::path::{Path, PathBuf};

/// `translate_config.json`，与当前数据库同一个目录。
fn config_path() -> PathBuf {
    // 找不到数据库时退回当前目录（空 PathBuf 的 parent 就是它自己，join 出来是相对
    // 路径，与 `translate.py` 在仓库根目录下的行为一致）。这里**不能**因为找不到库
    // 就报错返回：设置页得能打开、能填 Key，否则用户连补救的机会都没有。
    let dir = crate::db::find_db_path()
        .and_then(|db| db.parent().map(Path::to_path_buf))
        .unwrap_or_default();
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

#[tauri::command]
pub async fn run_ai_analysis(prompt: String) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || {
        run_ai_analysis_blocking(prompt)
    })
    .await
    .map_err(|e| format!("异步执行异常: {}", e))?
}

fn run_ai_analysis_blocking(prompt: String) -> Result<String, String> {
    use serde_json::Value;

    let path = config_path();
    let cfg = tc::load_config(&path);
    if cfg.active.is_empty() {
        return Err("当前未激活任何大模型来源。请在「功能插件」下的「大模型 AI 翻译引擎」中选择或添加来源并勾选激活。".into());
    }
    let profile = cfg.profiles.get(&cfg.active).ok_or_else(|| {
        format!("未找到激活的配置「{}」", cfg.active)
    })?;

    let api_key = profile.api_key.trim();
    let model = if profile.model.trim().is_empty() {
        match profile.kind.as_str() {
            "anthropic" => "claude-3-5-sonnet-20241022",
            "gemini" => "gemini-1.5-flash",
            _ => "deepseek-chat",
        }
    } else {
        profile.model.trim()
    };

    let base_url = profile.base_url.trim();

    if api_key.is_empty() && profile.kind != "openai" && !base_url.contains("localhost") && !base_url.contains("127.0.0.1") {
        return Err("当前大模型来源尚未配置 API Key。请前往「功能插件」卡片填写 API Key。".into());
    }

    let (url, body, auth_header) = match profile.kind.as_str() {
        "anthropic" => {
            let endpoint = if base_url.is_empty() { "https://api.anthropic.com/v1/messages" } else { base_url };
            let payload = serde_json::json!({
                "model": model,
                "max_tokens": 2048,
                "messages": [{"role": "user", "content": prompt}]
            });
            (endpoint.to_string(), payload.to_string(), vec![
                format!("x-api-key: {}", api_key),
                "anthropic-version: 2023-06-01".to_string(),
            ])
        },
        "gemini" => {
            let base = if base_url.is_empty() { "https://generativelanguage.googleapis.com" } else { base_url.trim_end_matches('/') };
            let endpoint = format!("{}/v1beta/models/{}:generateContent?key={}", base, model, api_key);
            let payload = serde_json::json!({
                "contents": [{"parts": [{"text": prompt}]}]
            });
            (endpoint, payload.to_string(), vec![])
        },
        _ => {
            let base = if base_url.is_empty() { "https://api.deepseek.com/v1" } else { base_url.trim_end_matches('/') };
            let endpoint = if base.ends_with("/chat/completions") {
                base.to_string()
            } else {
                format!("{}/chat/completions", base)
            };
            let payload = serde_json::json!({
                "model": model,
                "messages": [
                    {"role": "system", "content": "你是一位资深的成人电影文化研究学者、影评人与私人影视鉴赏顾问。请根据用户提供的观影互动足迹，进行深入、透彻、独到的影迷偏好画像分析。"},
                    {"role": "user", "content": prompt}
                ],
                "temperature": 0.7
            });
            let mut headers = vec![];
            if !api_key.is_empty() {
                headers.push(format!("Authorization: Bearer {}", api_key));
            }
            (endpoint, payload.to_string(), headers)
        }
    };

    let mut cmd = std::process::Command::new("curl");
    cmd.arg("-s").arg("-X").arg("POST").arg(&url);
    cmd.arg("-H").arg("Content-Type: application/json");
    for h in auth_header {
        cmd.arg("-H").arg(h);
    }
    cmd.arg("-d").arg(&body);

    let output = cmd.output().map_err(|e| format!("执行网络请求失败: {}", e))?;
    if !output.status.success() {
        return Err(format!("大模型接口返回异常错误码: {:?}", output.status.code()));
    }

    let resp_str = String::from_utf8_lossy(&output.stdout).to_string();
    let resp_json: Value = serde_json::from_str(&resp_str)
        .map_err(|_| format!("接口返回了非 JSON 内容: {}", resp_str.chars().take(200).collect::<String>()))?;

    if profile.kind == "anthropic" {
        if let Some(content_arr) = resp_json.get("content").and_then(|c| c.as_array()) {
            if let Some(first_block) = content_arr.first() {
                if let Some(text) = first_block.get("text").and_then(|t| t.as_str()) {
                    return Ok(text.to_string());
                }
            }
        }
    } else if profile.kind == "gemini" {
        if let Some(candidates) = resp_json.get("candidates").and_then(|c| c.as_array()) {
            if let Some(first) = candidates.first() {
                if let Some(parts) = first.get("content").and_then(|c| c.get("parts")).and_then(|p| p.as_array()) {
                    if let Some(p0) = parts.first() {
                        if let Some(text) = p0.get("text").and_then(|t| t.as_str()) {
                            return Ok(text.to_string());
                        }
                    }
                }
            }
        }
    } else {
        if let Some(choices) = resp_json.get("choices").and_then(|c| c.as_array()) {
            if let Some(first) = choices.first() {
                if let Some(content) = first.get("message").and_then(|m| m.get("content")).and_then(|c| c.as_str()) {
                    return Ok(content.to_string());
                }
            }
        }
    }

    if let Some(err_msg) = resp_json.get("error").and_then(|e| e.get("message")).and_then(|m| m.as_str()) {
        return Err(format!("大模型服务商报错: {}", err_msg));
    }

    Err(format!("未能解析大模型响应: {}", resp_str.chars().take(300).collect::<String>()))
}
