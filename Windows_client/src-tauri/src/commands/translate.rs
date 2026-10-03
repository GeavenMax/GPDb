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
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};

static GEMINI_KEY_INDEX: AtomicUsize = AtomicUsize::new(0);

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TranslatedEpisodeItem {
    pub id: i64,
    pub description_zh: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MovieTranslateResult {
    pub id: i64,
    pub description_zh: Option<String>,
    pub episodes: Vec<TranslatedEpisodeItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EpisodeTranslateResult {
    pub id: i64,
    pub movie_id: Option<i64>,
    pub description_zh: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProviderTestResult {
    pub profile: String,
    pub model: Option<String>,
    pub elapsed: u64,
    pub source: String,
    pub result: String,
    pub error: Option<String>,
}

/// `translate_config.json` 寻找策略：
/// 1. 优先检查数据库同级目录下的 `translate_config.json`（若已存在，则保持同目录）
/// 2. 检查应用数据目录 `com.gpdb.app/translate_config.json`（若已存在，则使用）
/// 3. 若均不存在：
///    - 若数据库同级目录存在且非空，使用数据库同级目录
///    - 否则安全回退至系统应用数据目录 `com.gpdb.app`
pub fn config_path() -> PathBuf {
    if let Some(db_dir) = crate::db::find_db_path().and_then(|db| db.parent().map(Path::to_path_buf)) {
        let p = db_dir.join(tc::CONFIG_FILE_NAME);
        if p.is_file() {
            return p;
        }
    }

    if let Some(app_cfg) = crate::db::config_file_path() {
        if let Some(parent) = app_cfg.parent() {
            let p = parent.join(tc::CONFIG_FILE_NAME);
            if p.is_file() {
                return p;
            }
        }
    }

    if let Some(db_dir) = crate::db::find_db_path().and_then(|db| db.parent().map(Path::to_path_buf)) {
        if !db_dir.as_os_str().is_empty() {
            return db_dir.join(tc::CONFIG_FILE_NAME);
        }
    }

    if let Some(app_cfg) = crate::db::config_file_path() {
        if let Some(parent) = app_cfg.parent() {
            let _ = std::fs::create_dir_all(parent);
            return parent.join(tc::CONFIG_FILE_NAME);
        }
    }

    PathBuf::from(tc::CONFIG_FILE_NAME)
}

/// 读取有效翻译配置：若主路径配置为空，自动尝试备用路径
pub fn load_active_config() -> tc::Config {
    let path = config_path();
    let cfg = tc::load_config(&path);
    if cfg.profiles.is_empty() {
        if let Some(app_cfg) = crate::db::config_file_path() {
            if let Some(parent) = app_cfg.parent() {
                let alt = parent.join(tc::CONFIG_FILE_NAME);
                if alt != path && alt.is_file() {
                    let alt_cfg = tc::load_config(&alt);
                    if !alt_cfg.profiles.is_empty() {
                        return alt_cfg;
                    }
                }
            }
        }
    }
    cfg
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

    // 如果显式勾选了立即使用，或者当前尚未激活任何来源，或者正在编辑当前激活的来源，自动激活
    let cur_cfg = tc::load_config(&path);
    let should_activate = input.active == Some(true)
        || cur_cfg.active.trim().is_empty()
        || cur_cfg.active.trim() == name;
    if should_activate {
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


/// 导出所有已翻译数据为结构化 JSON
#[tauri::command]
pub fn export_translations() -> Result<String, String> {
    let conn = crate::db::open_db()?;
    let mut stmt_m = conn.prepare(
        "SELECT id, title, studio_name, release_year, category, description, description_zh \
         FROM movies \
         WHERE description_zh IS NOT NULL AND trim(description_zh) != '' \
         ORDER BY id ASC"
    ).map_err(|e| e.to_string())?;

    let movie_rows = stmt_m.query_map([], |row| {
        Ok(serde_json::json!({
            "id": row.get::<_, i64>(0)?,
            "title": row.get::<_, Option<String>>(1)?,
            "studio_name": row.get::<_, Option<String>>(2)?,
            "release_year": row.get::<_, Option<i64>>(3)?,
            "category": row.get::<_, Option<String>>(4)?,
            "description": row.get::<_, Option<String>>(5)?,
            "description_zh": row.get::<_, String>(6)?,
        }))
    }).map_err(|e| e.to_string())?;

    let mut movies = Vec::new();
    for r in movie_rows {
        if let Ok(val) = r {
            movies.push(val);
        }
    }

    let mut stmt_e = conn.prepare(
        "SELECT id, movie_id, title, description, description_zh \
         FROM episodes \
         WHERE description_zh IS NOT NULL AND trim(description_zh) != '' \
         ORDER BY id ASC"
    ).map_err(|e| e.to_string())?;

    let ep_rows = stmt_e.query_map([], |row| {
        Ok(serde_json::json!({
            "id": row.get::<_, i64>(0)?,
            "movie_id": row.get::<_, Option<i64>>(1)?,
            "title": row.get::<_, Option<String>>(2)?,
            "description": row.get::<_, Option<String>>(3)?,
            "description_zh": row.get::<_, String>(4)?,
        }))
    }).map_err(|e| e.to_string())?;

    let mut episodes = Vec::new();
    for r in ep_rows {
        if let Ok(val) = r {
            episodes.push(val);
        }
    }

    let export_data = serde_json::json!({
        "app": "GPDb",
        "version": "2.11.0",
        "movies_count": movies.len(),
        "episodes_count": episodes.len(),
        "movies": movies,
        "episodes": episodes,
    });

    serde_json::to_string_pretty(&export_data).map_err(|e| e.to_string())
}

/// 批量导入翻译数据 JSON 并更新本地 SQLite 数据库
#[tauri::command]
pub fn import_translations(json_content: String) -> Result<serde_json::Value, String> {
    let mut conn = crate::db::open_db()?;
    let data: serde_json::Value = serde_json::from_str(&json_content)
        .map_err(|e| format!("解析 JSON 格式失败: {}", e))?;

    let tx = conn.transaction().map_err(|e| e.to_string())?;

    let mut movies_updated = 0usize;
    if let Some(movies) = data.get("movies").and_then(|v| v.as_array()) {
        let mut stmt = tx.prepare("UPDATE movies SET description_zh = ? WHERE id = ?").map_err(|e| e.to_string())?;
        for m in movies {
            if let (Some(id), Some(zh)) = (m.get("id").and_then(|v| v.as_i64()), m.get("description_zh").and_then(|v| v.as_str())) {
                let zh_trimmed = zh.trim();
                if !zh_trimmed.is_empty() {
                    if stmt.execute(rusqlite::params![zh_trimmed, id]).is_ok() {
                        movies_updated += 1;
                    }
                }
            }
        }
    }

    let mut episodes_updated = 0usize;
    if let Some(episodes) = data.get("episodes").and_then(|v| v.as_array()) {
        let mut stmt = tx.prepare("UPDATE episodes SET description_zh = ? WHERE id = ?").map_err(|e| e.to_string())?;
        for ep in episodes {
            if let (Some(id), Some(zh)) = (ep.get("id").and_then(|v| v.as_i64()), ep.get("description_zh").and_then(|v| v.as_str())) {
                let zh_trimmed = zh.trim();
                if !zh_trimmed.is_empty() {
                    if stmt.execute(rusqlite::params![zh_trimmed, id]).is_ok() {
                        episodes_updated += 1;
                    }
                }
            }
        }
    }

    tx.commit().map_err(|e| e.to_string())?;

    Ok(serde_json::json!({
        "movies_updated": movies_updated,
        "episodes_updated": episodes_updated,
        "total_updated": movies_updated + episodes_updated
    }))
}

/// 探测大模型服务并获取支持的模型列表
#[tauri::command]
pub fn fetch_provider_models(provider_name: String) -> Result<Vec<String>, String> {
    use std::process::Command;
    use serde_json::Value;

    let path = config_path();
    let cfg = tc::load_config(&path);
    let profile = cfg.profiles.get(&provider_name).ok_or_else(|| {
        format!("未找到配置「{}」", provider_name)
    })?;

    let base_url = profile.base_url.trim().trim_end_matches('/');
    let api_key = profile.api_key.trim();

    let endpoint = if base_url.is_empty() {
        "https://api.openai.com/v1/models".to_string()
    } else if base_url.ends_with("/v1") {
        format!("{}/models", base_url)
    } else {
        format!("{}/v1/models", base_url)
    };

    let mut cmd = Command::new("curl");
    cmd.arg("-s").arg("-N").arg("--max-time").arg("10");
    cmd.arg("-X").arg("GET");
    cmd.arg(&endpoint);
    if !api_key.is_empty() {
        cmd.arg("-H").arg(format!("Authorization: Bearer {}", api_key));
    }

    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW
    }

    let output = cmd.output().map_err(|e| format!("执行探测失败: {}", e))?;
    let resp_str = String::from_utf8_lossy(&output.stdout).to_string();
    let resp_json: Value = serde_json::from_str(&resp_str)
        .map_err(|_| format!("接口返回非 JSON 数据: {}", resp_str.chars().take(150).collect::<String>()))?;

    let mut model_list = Vec::new();
    if let Some(data) = resp_json.get("data").and_then(|d| d.as_array()) {
        for item in data {
            if let Some(id) = item.get("id").and_then(|i| i.as_str()) {
                model_list.push(id.to_string());
            }
        }
    }

    if model_list.is_empty() {
        if let Some(err_msg) = resp_json.get("error").and_then(|e| e.get("message")).and_then(|m| m.as_str()) {
            return Err(format!("大模型服务商报错: {}", err_msg));
        }
        return Err("未从该服务商探测到任何可用模型".to_string());
    }

    model_list.sort();
    Ok(model_list)
}

// ---------------------------------------------------------------------------
// 剧情与分集机器翻译引擎 (纯 Rust TLS 实现，支持 Gemini 自动轮换、OpenAI、DeepSeek、Claude)
// ---------------------------------------------------------------------------

pub const DEFAULT_SYSTEM_PROMPT: &str = "你是一名成人影片资料库的专职译者。你会收到英文剧情简介、分集与专有名词，需要如实翻译成简体中文。\n\n\
翻译风格（最重要，必须严格遵守）：\n\
1. 忠于原文：逐句对应，原文说了什么就译什么，不增不减、不概括、不改写、不调整叙述顺序。\n\
2. 非常直白：用词直接、露骨、不回避，按原文露骨程度如实翻译，不要替换成委婉语、暗示或模糊表述，绝不弱化、淡化、\"净化\"或美化原文内容。\n\
3. 不要意译或润色：宁可保持原文的句式和语序，也不要为了文采而改变原意。\n\
4. 人名、厂牌名（Studio）保留原文拉丁字母写法，绝对不要音译成汉字（例：Count Vladimir 保持 \"Count Vladimir\"，不要写成 \"弗拉基米尔伯爵\"；Falcon Studios 保持 \"Falcon Studios\"）。注意：仅人名和厂牌本身保留原文，厂牌衍生词和角色标签必须翻译（详见第9条）。\n\
5. 地名、国名翻译成规范中文（例：Prague 译为 \"布拉格\"；Budapest 译为 \"布达佩斯\"；Berlin 译为 \"柏林\"；California 译为 \"加州\"；France 译为 \"法国\"；Slovakia 译为 \"斯洛伐克\"；Czech 译为 \"捷克\" 等）。\n\
6. 作品片名处理：若简介中提及作品片名，无论原文大小写如何（全大写、小写或首字母大写），只要属于知名/已有中文译名的片名，一律规范翻译为中文片名并加上书名号《》；若无法确定义名则保留英文原名。\n\
7. 保持档案记录语气：只陈述原文内容，不添加主观评价。涉及成人内容、身体部位、性取向时照实翻译，使用中文成人语境中通用的直接说法，不要因内容露骨而删减、跳过或含糊处理。\n\
8. 严格契合男同性恋（Gay）语境：所有简介、分集和标题全部来自男同性恋题材，身体部位严禁出现任何针对女性的词汇（如严禁使用“逼”、“骚逼”、“屄”等）。涉及后庭器官或被插入部位时，必须使用“屁眼”、“菊花”、“菊门”、“肛门”、“后庭”等男性同性恋语境词汇；“操逼”一律译为“操屁股”、“干屁股”、“后入”或“猛操”。\n\
9. 行业术语、演员角色属性与演职词汇汉化（不是人名，严禁保留英文原文）：\n\
   - 厂牌专属演员称呼：Lucas Men 译为“Lucas 旗下演员/男优”；Falcon Men 译为“Falcon 旗下男优”；BelAmi Freshmen 译为“BelAmi 新人演员/新秀”；\n\
   - 签约与角色状态：Exclusive/Exclusives 译为“独家签约演员/专属男优”；newcomer 译为“新晋男优/新人”；debut 译为“首秀/出道作”；\n\
   - 演出方式与拍摄场景：featuring 译为“由...出演/特邀出演/主演”；casting 译为“试镜/选角”；solo 译为“单人秀/个人自慰秀”；hardcore 译为“硬核实战”；\n\
   - 生理与性爱角色：uncut 译为“未割包皮/原装包皮”；cut 译为“割包皮”；top 译为“1号/攻”；bottom 译为“0号/受”；vers/versatile 译为“0.5号/攻受兼备”；bareback/raw 译为“无套”；flip-flop/flip flop 译为“互攻”；rimming 译为“舔肛”；cumshot 译为“射精”；creampie 译为“内射”。\n\n\
只输出 JSON，不要输出任何解释、前言或 Markdown 代码块。\n\n\
输出格式（必须严格遵守）：\n\
{\"translations\": [{\"i\": 1, \"zh\": \"第一条译文\"}, {\"i\": 2, \"zh\": \"第二条译文\"}]}\n\
其中 i 是输入的序号，必须与输入一一对应，不得遗漏或调换顺序。";

fn build_user_turn(texts: &[String]) -> String {
    let mut numbered = String::new();
    for (idx, t) in texts.iter().enumerate() {
        if idx > 0 {
            numbered.push_str("\n\n");
        }
        numbered.push_str(&format!("{}. {}", idx + 1, t.trim()));
    }
    format!("请翻译以下 {} 条简介：\n\n{}", texts.len(), numbered)
}

pub fn extract_translations(raw: &str, expected: usize) -> Result<Vec<String>, String> {
    let mut text = raw.trim();
    if text.starts_with("```") {
        if let Some(first_line_end) = text.find('\n') {
            text = &text[first_line_end + 1..];
        }
        if let Some(last_fence) = text.rfind("```") {
            text = &text[..last_fence];
        }
        text = text.trim();
    }

    // 1. 单条翻译（影片档案页、分集档案页最普遍场景，与 Android 行为完全对齐）
    if expected == 1 {
        // 先检查是否为 JSON 结构
        let parsed_json: Option<serde_json::Value> = serde_json::from_str(text).ok().or_else(|| {
            if let (Some(start), Some(end)) = (text.find('{'), text.rfind('}')) {
                if start < end {
                    serde_json::from_str(&text[start..=end]).ok()
                } else {
                    None
                }
            } else {
                None
            }
        });

        if let Some(parsed) = parsed_json {
            if let Some(items) = parsed.get("translations").and_then(|v| v.as_array()).or_else(|| parsed.as_array()) {
                if let Some(first) = items.first() {
                    if let Some(s) = first.as_str() {
                        let trimmed = s.trim();
                        if !trimmed.is_empty() {
                            return Ok(vec![trimmed.to_string()]);
                        }
                    } else if let Some(obj) = first.as_object() {
                        let zh = obj.get("zh")
                            .or_else(|| obj.get("translation"))
                            .or_else(|| obj.get("text"))
                            .and_then(|v| v.as_str())
                            .unwrap_or("");
                        let trimmed = zh.trim();
                        if !trimmed.is_empty() {
                            return Ok(vec![trimmed.to_string()]);
                        }
                    }
                }
            } else if let Some(zh) = parsed.get("zh")
                .or_else(|| parsed.get("translation"))
                .or_else(|| parsed.get("result"))
                .and_then(|v| v.as_str())
            {
                let trimmed = zh.trim();
                if !trimmed.is_empty() {
                    return Ok(vec![trimmed.to_string()]);
                }
            }
        }

        // 大模型通常直接返回纯文本翻译（如 Android 端一样），清理前缀序号和包裹符号
        let mut s = text.trim();
        for prefix in ["1.", "1、", "1:", "1：", "(1)", "（1）"] {
            if s.starts_with(prefix) {
                s = s[prefix.len()..].trim();
                break;
            }
        }
        let cleaned = s.trim_matches(|c| c == '"' || c == '\'' || c == '`' || c == '{' || c == '}').trim();
        if !cleaned.is_empty() {
            return Ok(vec![cleaned.to_string()]);
        }
        return Err(format!("模型返回了空译文: {}", &raw[..raw.len().min(300)]));
    }

    // 2. 多条批量翻译场景
    let mut out = vec![String::new(); expected];

    let parsed_opt: Option<serde_json::Value> = serde_json::from_str(text).ok().or_else(|| {
        if let (Some(start), Some(end)) = (text.find('{'), text.rfind('}')) {
            if start < end {
                serde_json::from_str(&text[start..=end]).ok()
            } else {
                None
            }
        } else {
            None
        }
    });

    if let Some(parsed) = parsed_opt {
        if let Some(items) = parsed.get("translations").and_then(|v| v.as_array()).or_else(|| parsed.as_array()) {
            for (pos, item) in items.iter().enumerate() {
                if let Some(s) = item.as_str() {
                    if pos < expected {
                        out[pos] = s.trim().to_string();
                    }
                } else if let Some(obj) = item.as_object() {
                    let idx = obj.get("i")
                        .and_then(|v| v.as_i64())
                        .map(|i| (i - 1) as usize)
                        .unwrap_or(pos);
                    let val = obj.get("zh")
                        .or_else(|| obj.get("translation"))
                        .or_else(|| obj.get("text"))
                        .and_then(|v| v.as_str())
                        .unwrap_or("");
                    if idx < expected && !val.trim().is_empty() {
                        out[idx] = val.trim().to_string();
                    }
                }
            }
        }
    }

    // 3. 降级：如果模型按行或分段编号返回（例如 "1. xxx\n\n2. yyy"）
    if out.iter().all(|s| s.is_empty()) {
        let mut current_idx: Option<usize> = None;
        let mut chunks: Vec<(usize, String)> = Vec::new();

        for line in text.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() {
                continue;
            }

            let mut matched_idx = None;
            for prefix_len in 1..=3 {
                if trimmed.len() > prefix_len {
                    let (num_part, rest) = trimmed.split_at(prefix_len);
                    if let Ok(n) = num_part.parse::<usize>() {
                        if rest.starts_with('.') || rest.starts_with('、') || rest.starts_with(':') || rest.starts_with('：') {
                            matched_idx = Some((n, rest[rest.chars().next().unwrap().len_utf8()..].trim()));
                            break;
                        }
                    }
                }
            }

            if let Some((n, content)) = matched_idx {
                if n >= 1 && n <= expected {
                    current_idx = Some(n - 1);
                    chunks.push((n - 1, content.to_string()));
                    continue;
                }
            }

            if let Some(idx) = current_idx {
                if let Some(last) = chunks.iter_mut().find(|(i, _)| *i == idx) {
                    last.1.push('\n');
                    last.1.push_str(trimmed);
                }
            }
        }

        for (idx, content) in chunks {
            if idx < expected {
                out[idx] = content.trim().to_string();
            }
        }
    }

    // 如果仍为空但原始文本非空，将全文赋予第一项
    if out.iter().all(|s| s.is_empty()) && !text.is_empty() {
        let cleaned = text.trim_matches(|c| c == '{' || c == '}' || c == '"' || c == '`');
        if !cleaned.is_empty() {
            out[0] = cleaned.trim().to_string();
        }
    }

    if out.iter().all(|s| s.is_empty()) {
        return Err(format!("未能从模型返回解析出有效译文: {}", &raw[..raw.len().min(300)]));
    }

    Ok(out)
}

fn curl_post(url: &str, headers: &[(&str, &str)], body: &str, timeout_secs: u64) -> Result<String, String> {
    use std::io::Write;
    use std::process::{Command, Stdio};

    let mut cmd = Command::new("curl");
    cmd.arg("-s").arg("-N")
        .arg("--max-time").arg(timeout_secs.to_string())
        .arg("-X").arg("POST")
        .arg(url);

    for (k, v) in headers {
        cmd.arg("-H").arg(format!("{}: {}", k, v));
    }
    cmd.arg("--data-binary").arg("@-");
    cmd.stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::piped());

    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW
    }

    let mut child = cmd.spawn().map_err(|e| format!("启动 curl 失败: {}", e))?;
    if let Some(mut stdin) = child.stdin.take() {
        stdin.write_all(body.as_bytes()).map_err(|e| format!("写入请求体失败: {}", e))?;
    }

    let output = child.wait_with_output().map_err(|e| format!("等待 curl 执行失败: {}", e))?;
    if !output.status.success() {
        let err_msg = String::from_utf8_lossy(&output.stderr);
        return Err(format!("curl 请求失败: {}", err_msg));
    }
    Ok(String::from_utf8_lossy(&output.stdout).to_string())
}

fn http_post(url: &str, headers: &[(&str, &str)], body: &str, timeout_secs: u64) -> Result<String, String> {
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(std::time::Duration::from_secs(12))
        .timeout_read(std::time::Duration::from_secs(timeout_secs))
        .build();

    let mut req = agent.post(url);
    for (k, v) in headers {
        req = req.set(k, v);
    }

    match req.send_string(body) {
        Ok(resp) => {
            resp.into_string().map_err(|e| format!("读取响应失败: {}", e))
        }
        Err(ureq::Error::Status(code, resp)) => {
            let body_str = resp.into_string().unwrap_or_default();
            Err(format!("HTTP {} 错误: {}", code, body_str))
        }
        Err(ureq::Error::Transport(e)) => {
            log::warn!("ureq transport error ({}), falling back to curl", e);
            curl_post(url, headers, body, timeout_secs)
        }
    }
}

fn get_profile_api_keys(profile: &tc::ProfileEntry) -> Vec<String> {
    let mut keys = Vec::new();
    if let Some(serde_json::Value::Array(arr)) = profile.extra.get("api_keys") {
        for v in arr {
            if let Some(s) = v.as_str() {
                let trimmed = s.trim().to_string();
                if !trimmed.is_empty() && !keys.contains(&trimmed) {
                    keys.push(trimmed);
                }
            }
        }
    }
    let main_key = profile.api_key.trim().to_string();
    if !main_key.is_empty() && !keys.contains(&main_key) {
        keys.insert(0, main_key);
    }
    keys
}

pub fn call_llm_with_provider(
    provider_name: Option<&str>,
    texts: &[String],
    custom_prompt: Option<&str>,
) -> Result<(String, String, Vec<String>), String> {
    if texts.is_empty() {
        return Ok((String::new(), String::new(), Vec::new()));
    }

    let cfg = load_active_config();
    let active_name = match provider_name.filter(|s| !s.trim().is_empty()) {
        Some(p) => p.trim().to_string(),
        None => {
            if !cfg.active.trim().is_empty() && cfg.profiles.contains_key(cfg.active.trim()) {
                cfg.active.trim().to_string()
            } else {
                // 容错：自动挑选第一个已配置 Key 的模型来源，或本地 Ollama
                let candidate = cfg.profiles.iter().find(|(_, p)| {
                    !p.api_key.trim().is_empty()
                        || p.base_url.contains("localhost")
                        || p.base_url.contains("127.0.0.1")
                });
                if let Some((name, _)) = candidate {
                    name.clone()
                } else if let Some((name, _)) = cfg.profiles.iter().next() {
                    name.clone()
                } else {
                    return Err("尚未配置任何大模型翻译来源，请前往「设置 - 外部插件」添加大模型并填入 API Key".to_string());
                }
            }
        }
    };

    let profile = cfg.profiles.get(&active_name).ok_or_else(|| {
        format!("未找到翻译配置来源「{}」，请在设置中检查配置", active_name)
    })?;

    let prompt = match custom_prompt.map(str::trim).filter(|s| !s.is_empty()) {
        Some(p) => p,
        None => DEFAULT_SYSTEM_PROMPT,
    };

    // 1. Google Gemini 模式 (支持多 Key 自动轮换与配额保护)
    if profile.kind == "gemini" {
        let keys = get_profile_api_keys(profile);
        if keys.is_empty() {
            return Err(format!("Google Gemini 来源「{}」未配置有效的 API Key", active_name));
        }
        let model = if profile.model.trim().is_empty() {
            "gemini-3.5-flash-lite"
        } else {
            profile.model.trim()
        };
        let base_url = if profile.base_url.trim().is_empty() {
            "https://generativelanguage.googleapis.com"
        } else {
            profile.base_url.trim().trim_end_matches('/')
        };
        let endpoint = format!("{}/v1beta/models/{}:generateContent", base_url, model);

        let (gemini_prompt, gemini_user_turn) = if texts.len() == 1 {
            (prompt.to_string(), texts[0].trim().to_string())
        } else {
            (
                format!(
                    "{}\n\n只输出 JSON，不要输出任何解释、前言或 Markdown 代码块。\n输出格式（严格遵守）：\n{{\"translations\": [{{\"i\": 1, \"zh\": \"第一条译文\"}}, ...]}}",
                    prompt
                ),
                build_user_turn(texts),
            )
        };

        let mut gen_config = serde_json::json!({
            "temperature": 0.3
        });
        if texts.len() > 1 {
            gen_config["responseMimeType"] = serde_json::json!("application/json");
        }

        let payload = serde_json::json!({
            "systemInstruction": {
                "parts": [{ "text": gemini_prompt }]
            },
            "contents": [
                {
                    "role": "user",
                    "parts": [{ "text": gemini_user_turn }]
                }
            ],
            "generationConfig": gen_config,
            "safetySettings": [
                {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
                {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE"},
                {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE"},
                {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
            ]
        });
        let payload_str = serde_json::to_string(&payload).map_err(|e| e.to_string())?;

        let mut last_err = String::new();
        let num_keys = keys.len();
        let start_idx = GEMINI_KEY_INDEX.load(Ordering::Relaxed) % num_keys;

        for attempt in 0..num_keys {
            let cur_idx = (start_idx + attempt) % num_keys;
            let cur_key = &keys[cur_idx];
            let headers = [
                ("Content-Type", "application/json"),
                ("x-goog-api-key", cur_key.as_str()),
            ];

            match http_post(&endpoint, &headers, &payload_str, 90) {
                Ok(resp_str) => {
                    let resp_val: serde_json::Value = match serde_json::from_str(&resp_str) {
                        Ok(v) => v,
                        Err(e) => {
                            last_err = format!("Gemini 返回了非 JSON 数据: {}", e);
                            continue;
                        }
                    };

                    if let Some(err_obj) = resp_val.get("error") {
                        let err_code = err_obj.get("code").and_then(|c| c.as_i64()).unwrap_or(0);
                        let err_msg = err_obj.get("message").and_then(|m| m.as_str()).unwrap_or("未知错误");
                        let err_status = err_obj.get("status").and_then(|s| s.as_str()).unwrap_or("");

                        let is_quota_exceeded = err_code == 429
                            || err_status == "RESOURCE_EXHAUSTED"
                            || err_msg.to_lowercase().contains("quota")
                            || err_msg.to_lowercase().contains("limit 500")
                            || err_msg.to_lowercase().contains("per day");

                        let is_invalid_key = err_code == 400 && err_msg.to_lowercase().contains("api key not valid");

                        if (is_quota_exceeded || is_invalid_key) && num_keys > 1 {
                            log::warn!("[Gemini Key 轮换] Key #{} 配额已耗尽或无效 ({}) -> 轮换下一个 Key", cur_idx + 1, err_msg);
                            GEMINI_KEY_INDEX.store((cur_idx + 1) % num_keys, Ordering::Relaxed);
                            last_err = format!("Key #{}: {}", cur_idx + 1, err_msg);
                            continue;
                        }

                        last_err = format!("Gemini API 错误 (code {}): {}", err_code, err_msg);
                        continue;
                    }

                    let candidates = resp_val.get("candidates").and_then(|v| v.as_array());
                    let prompt_feedback = resp_val.get("promptFeedback");
                    let block_reason = prompt_feedback.and_then(|f| f.get("blockReason")).and_then(|b| b.as_str()).unwrap_or("");
                    let finish_reason = candidates.and_then(|c| c.first()).and_then(|first| first.get("finishReason")).and_then(|f| f.as_str()).unwrap_or("");

                    if block_reason == "PROHIBITED_CONTENT" || finish_reason == "SAFETY" || finish_reason == "PROHIBITED_CONTENT" {
                        if let Some(fallback_name) = cfg.extra.get("fallback").and_then(|v| v.as_str()) {
                            if fallback_name != active_name && cfg.profiles.contains_key(fallback_name) {
                                log::info!("[Gemini 安全审核拦截] 自动转交备用来源「{}」补救翻译", fallback_name);
                                return call_llm_with_provider(Some(fallback_name), texts, custom_prompt);
                            }
                        }
                        return Err(format!("Gemini 内容安全拦截 (blockReason={}, finishReason={})", block_reason, finish_reason));
                    }

                    let text_content = candidates
                        .and_then(|c| c.first())
                        .and_then(|first| first.get("content"))
                        .and_then(|content| content.get("parts"))
                        .and_then(|parts| parts.as_array())
                        .map(|arr| {
                            arr.iter().filter_map(|p| p.get("text").and_then(|t| t.as_str())).collect::<Vec<_>>().join("")
                        })
                        .unwrap_or_default();

                    if text_content.trim().is_empty() {
                        last_err = "Gemini 返回了空的文本候选内容".to_string();
                        continue;
                    }

                    GEMINI_KEY_INDEX.store(cur_idx, Ordering::Relaxed);
                    let translations = extract_translations(&text_content, texts.len())?;
                    return Ok((active_name, model.to_string(), translations));
                }
                Err(e) => {
                    log::warn!("Gemini 请求失败 (Key #{}): {}", cur_idx + 1, e);
                    last_err = e;
                    if num_keys > 1 {
                        GEMINI_KEY_INDEX.store((cur_idx + 1) % num_keys, Ordering::Relaxed);
                    }
                }
            }
        }

        // 所有 Gemini Key 配额均耗尽时，尝试转交 fallback 来源 (例如 DeepSeek)
        if let Some(fallback_name) = cfg.extra.get("fallback").and_then(|v| v.as_str()) {
            if fallback_name != active_name && cfg.profiles.contains_key(fallback_name) {
                log::info!("[Gemini 全部 Key 配额耗尽] 自动转交备用来源「{}」继续翻译", fallback_name);
                return call_llm_with_provider(Some(fallback_name), texts, custom_prompt);
            }
        }

        return Err(format!("Gemini 翻译失败: {}", last_err));
    }

    // 2. Anthropic Claude 模式
    if profile.kind == "anthropic" {
        let api_key = profile.api_key.trim();
        if api_key.is_empty() {
            return Err(format!("Anthropic 来源「{}」未配置 API Key", active_name));
        }
        let model = if profile.model.trim().is_empty() {
            "claude-opus-5"
        } else {
            profile.model.trim()
        };
        let base_url = if profile.base_url.trim().is_empty() {
            "https://api.anthropic.com"
        } else {
            profile.base_url.trim().trim_end_matches('/')
        };
        let endpoint = format!("{}/v1/messages", base_url);

        let (claude_system, claude_user) = if texts.len() == 1 {
            (prompt.to_string(), texts[0].trim().to_string())
        } else {
            (
                format!(
                    "{}\n\n只输出 JSON，不要输出任何解释、前言或 Markdown 代码块。\n输出格式（严格遵守）：\n{{\"translations\": [{{\"i\": 1, \"zh\": \"第一条译文\"}}, ...]}}",
                    prompt
                ),
                build_user_turn(texts),
            )
        };

        let mut payload = serde_json::json!({
            "model": model,
            "max_tokens": 16000,
            "system": claude_system,
            "messages": [
                { "role": "user", "content": claude_user }
            ]
        });

        if texts.len() > 1 {
            payload["output_config"] = serde_json::json!({
                "effort": "low",
                "format": {
                    "type": "json_schema",
                    "schema": {
                        "type": "object",
                        "properties": {
                            "translations": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "i": { "type": "integer" },
                                        "zh": { "type": "string" }
                                    },
                                    "required": ["i", "zh"],
                                    "additionalProperties": false
                                }
                            }
                        },
                        "required": ["translations"],
                        "additionalProperties": false
                    }
                }
            });
        }

        let payload_str = serde_json::to_string(&payload).map_err(|e| e.to_string())?;
        let headers = [
            ("Content-Type", "application/json"),
            ("x-api-key", api_key),
            ("anthropic-version", "2023-06-01"),
        ];

        let resp_str = http_post(&endpoint, &headers, &payload_str, 90)?;
        let resp_val: serde_json::Value = serde_json::from_str(&resp_str)
            .map_err(|e| format!("Anthropic 返回非 JSON: {}", e))?;

        if let Some(err_obj) = resp_val.get("error") {
            let msg = err_obj.get("message").and_then(|m| m.as_str()).unwrap_or("未知错误");
            return Err(format!("Anthropic 报错: {}", msg));
        }

        if resp_val.get("stop_reason").and_then(|s| s.as_str()) == Some("refusal") {
            return Err("模型拒绝了翻译请求 (stop_reason=refusal)".to_string());
        }

        let mut text_content = String::new();
        if let Some(contents) = resp_val.get("content").and_then(|c| c.as_array()) {
            for item in contents {
                if item.get("type").and_then(|t| t.as_str()) == Some("text") {
                    if let Some(t) = item.get("text").and_then(|s| s.as_str()) {
                        text_content.push_str(t);
                    }
                }
            }
        }

        let translations = extract_translations(&text_content, texts.len())?;
        return Ok((active_name, model.to_string(), translations));
    }

    // 3. 通用 OpenAI 兼容接口模式 (DeepSeek, Moonshot, Qwen, Ollama, OpenAI, SiliconFlow 等)
    // 行为完全对齐 Android 端（LLMTranslationService.kt），确保单条翻译直接可靠
    let api_key = profile.api_key.trim();
    let base_url = profile.base_url.trim().trim_end_matches('/');
    let is_local = base_url.contains("localhost") || base_url.contains("127.0.0.1");

    if api_key.is_empty() && !is_local {
        return Err(format!("来源「{}」未配置 API Key", active_name));
    }

    let model = if profile.model.trim().is_empty() {
        if active_name.contains("deepseek") || base_url.contains("deepseek") {
            "deepseek-chat"
        } else {
            "gpt-4o-mini"
        }
    } else {
        profile.model.trim()
    };

    let endpoint = if base_url.is_empty() {
        "https://api.openai.com/v1/chat/completions".to_string()
    } else if base_url.ends_with("/chat/completions") {
        base_url.to_string()
    } else if base_url.ends_with("/v1") || base_url.ends_with("/v4") || base_url.ends_with("/v3") {
        format!("{}/chat/completions", base_url)
    } else if base_url.contains("/v1/") {
        format!("{}/chat/completions", base_url.trim_end_matches('/'))
    } else if base_url.contains("deepseek.com") {
        format!("{}/chat/completions", base_url)
    } else {
        format!("{}/v1/chat/completions", base_url)
    };

    let (openai_system, openai_user) = if texts.len() == 1 {
        (prompt.to_string(), texts[0].trim().to_string())
    } else {
        (
            format!(
                "{}\n\n只输出 JSON，不要输出任何解释、前言或 Markdown 代码块。\n输出格式（严格遵守）：\n{{\"translations\": [{{\"i\": 1, \"zh\": \"第一条译文\"}}, ...]}}",
                prompt
            ),
            build_user_turn(texts),
        )
    };

    let mut payload = serde_json::json!({
        "model": model,
        "messages": [
            { "role": "system", "content": openai_system },
            { "role": "user", "content": openai_user }
        ],
        "temperature": 0.3
    });

    // 单条翻译绝不强制 json_object（避免 OpenAI / DeepSeek / 代理网关抛 HTTP 400，与 Android 端一致）
    let should_try_json_object = texts.len() > 1 && custom_prompt.is_none();
    if should_try_json_object {
        payload["response_format"] = serde_json::json!({ "type": "json_object" });
    }

    let payload_str = serde_json::to_string(&payload).map_err(|e| e.to_string())?;

    let mut headers = vec![("Content-Type", "application/json")];
    let auth_header;
    if !api_key.is_empty() {
        auth_header = format!("Bearer {}", api_key);
        headers.push(("Authorization", &auth_header));
    }

    let resp_str = match http_post(&endpoint, &headers, &payload_str, 90) {
        Ok(s) => s,
        Err(e) if should_try_json_object && (e.contains("400") || e.to_lowercase().contains("response_format")) => {
            log::warn!("OpenAI endpoint returned 400 with response_format, retrying without response_format: {}", e);
            payload.as_object_mut().unwrap().remove("response_format");
            let retry_payload = serde_json::to_string(&payload).map_err(|e| e.to_string())?;
            http_post(&endpoint, &headers, &retry_payload, 90)?
        }
        Err(e) => return Err(e),
    };

    let resp_val: serde_json::Value = serde_json::from_str(&resp_str)
        .map_err(|e| format!("接口返回非 JSON 数据: {}", e))?;

    if let Some(err_obj) = resp_val.get("error") {
        let msg = err_obj.get("message").and_then(|m| m.as_str()).unwrap_or("未知错误");
        return Err(format!("大模型服务商报错: {}", msg));
    }

    let text_content = resp_val
        .get("choices")
        .and_then(|c| c.as_array())
        .and_then(|c| c.first())
        .and_then(|first| first.get("message"))
        .and_then(|m| m.get("content"))
        .and_then(|c| c.as_str())
        .ok_or_else(|| format!("未从模型返回解析到 choices[0].message.content: {}", &resp_str[..resp_str.len().min(300)]))?;

    let translations = extract_translations(text_content, texts.len())?;
    Ok((active_name, model.to_string(), translations))
}

fn do_translate_movie(movie_id: i64, custom_prompt: Option<&str>) -> Result<MovieTranslateResult, String> {
    let conn = crate::db::open_db()?;
    let mut stmt_m = conn.prepare("SELECT title, description, description_zh FROM movies WHERE id = ?").map_err(|e| e.to_string())?;
    let mut movie_rows = stmt_m.query(rusqlite::params![movie_id]).map_err(|e| e.to_string())?;
    let (_movie_title, movie_desc, movie_zh) = if let Some(row) = movie_rows.next().map_err(|e| e.to_string())? {
        let t: Option<String> = row.get(0).ok();
        let d: Option<String> = row.get(1).ok();
        let z: Option<String> = row.get(2).ok();
        (t, d, z)
    } else {
        return Err("未找到该影片".to_string());
    };

    let has_valid_zh = movie_zh.as_deref().map(str::trim).filter(|s| !s.is_empty()).is_some();
    let has_valid_desc = movie_desc.as_deref().map(str::trim).filter(|s| !s.is_empty()).is_some();
    let need_movie_trans = !has_valid_zh && has_valid_desc;
    let movie_text = if need_movie_trans {
        movie_desc.clone().unwrap_or_default()
    } else {
        String::new()
    };

    let mut stmt_e = conn.prepare(
        "SELECT id, description FROM episodes \
         WHERE movie_id = ? \
           AND (description_zh IS NULL OR trim(description_zh) = '') \
           AND description IS NOT NULL AND trim(description) != '' \
         ORDER BY episode_number ASC, id ASC"
    ).map_err(|e| e.to_string())?;

    let ep_rows = stmt_e.query_map(rusqlite::params![movie_id], |row| {
        Ok((row.get::<_, i64>(0)?, row.get::<_, String>(1)?))
    }).map_err(|e| e.to_string())?;

    let mut pending_episodes = Vec::new();
    for r in ep_rows {
        if let Ok((ep_id, desc)) = r {
            if !desc.trim().is_empty() {
                pending_episodes.push((ep_id, desc));
            }
        }
    }

    if movie_text.is_empty() && pending_episodes.is_empty() {
        if has_valid_zh {
            return Ok(MovieTranslateResult {
                id: movie_id,
                description_zh: movie_zh,
                episodes: Vec::new(),
            });
        } else {
            return Err("该影片没有简介可供翻译".to_string());
        }
    }

    let mut texts: Vec<String> = Vec::new();
    if !movie_text.is_empty() {
        texts.push(movie_text.clone());
    }
    for (_, ep_desc) in &pending_episodes {
        texts.push(ep_desc.clone());
    }

    let (_profile, _model, results) = call_llm_with_provider(None, &texts, custom_prompt)?;

    let offset = if !movie_text.is_empty() { 1 } else { 0 };
    let mut final_movie_zh = movie_zh;

    if !movie_text.is_empty() && !results.is_empty() {
        let zh = results[0].trim();
        if !zh.is_empty() {
            conn.execute(
                "UPDATE movies SET description_zh = ? WHERE id = ?",
                rusqlite::params![zh, movie_id],
            ).map_err(|e| format!("更新影片译文失败: {}", e))?;
            final_movie_zh = Some(zh.to_string());
        }
    }

    let mut translated_episodes = Vec::new();
    for (i, (ep_id, _)) in pending_episodes.iter().enumerate() {
        if let Some(res) = results.get(offset + i) {
            let ep_zh = res.trim();
            if !ep_zh.is_empty() {
                let _ = conn.execute(
                    "UPDATE episodes SET description_zh = ? WHERE id = ?",
                    rusqlite::params![ep_zh, ep_id],
                );
                translated_episodes.push(TranslatedEpisodeItem {
                    id: *ep_id,
                    description_zh: ep_zh.to_string(),
                });
            }
        }
    }

    Ok(MovieTranslateResult {
        id: movie_id,
        description_zh: final_movie_zh,
        episodes: translated_episodes,
    })
}

fn do_translate_episode(episode_id: i64, custom_prompt: Option<&str>) -> Result<EpisodeTranslateResult, String> {
    let conn = crate::db::open_db()?;
    let mut stmt = conn.prepare("SELECT movie_id, description, description_zh FROM episodes WHERE id = ?").map_err(|e| e.to_string())?;
    let mut rows = stmt.query(rusqlite::params![episode_id]).map_err(|e| e.to_string())?;
    let (movie_id, desc, _desc_zh) = if let Some(row) = rows.next().map_err(|e| e.to_string())? {
        (row.get::<_, Option<i64>>(0).ok().flatten(), row.get::<_, Option<String>>(1).ok().flatten(), row.get::<_, Option<String>>(2).ok().flatten())
    } else {
        return Err("未找到该分集".to_string());
    };

    let text = desc.as_deref().map(str::trim).unwrap_or("");
    if text.is_empty() {
        return Err("该分集没有英文简介可供翻译".to_string());
    }

    let (_profile, _model, results) = call_llm_with_provider(None, &[text.to_string()], custom_prompt)?;
    let zh = results.first().map(|s| s.trim()).unwrap_or("");
    if zh.is_empty() {
        return Err("服务返回了空译文".to_string());
    }

    conn.execute(
        "UPDATE episodes SET description_zh = ? WHERE id = ?",
        rusqlite::params![zh, episode_id],
    ).map_err(|e| format!("更新分集译文失败: {}", e))?;

    Ok(EpisodeTranslateResult {
        id: episode_id,
        movie_id,
        description_zh: Some(zh.to_string()),
    })
}

fn do_test_translation_provider(name: Option<&str>) -> Result<ProviderTestResult, String> {
    let sample = "The biggest toys around and they all play with each others'.".to_string();
    let t0 = std::time::Instant::now();
    let (profile_name, model_name, results) = match call_llm_with_provider(name, &[sample.clone()], None) {
        Ok(res) => res,
        Err(e) => {
            return Ok(ProviderTestResult {
                profile: name.unwrap_or("active").to_string(),
                model: None,
                elapsed: t0.elapsed().as_millis() as u64,
                source: sample,
                result: String::new(),
                error: Some(e),
            });
        }
    };

    let elapsed = t0.elapsed().as_millis() as u64;
    let zh = results.first().map(|s| s.trim().to_string()).unwrap_or_default();
    let error = if zh.is_empty() {
        Some("服务返回了空译文".to_string())
    } else {
        None
    };

    Ok(ProviderTestResult {
        profile: profile_name,
        model: Some(model_name),
        elapsed,
        source: sample,
        result: zh,
        error,
    })
}

/// 翻译指定影片（包含该影片下所有未翻译分集）
#[tauri::command]
pub async fn translate_movie(movie_id: i64, custom_prompt: Option<String>) -> Result<MovieTranslateResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        do_translate_movie(movie_id, custom_prompt.as_deref())
    })
    .await
    .map_err(|e| format!("任务执行失败: {}", e))?
}

/// 翻译指定单个分集
#[tauri::command]
pub async fn translate_episode(episode_id: i64, custom_prompt: Option<String>) -> Result<EpisodeTranslateResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        do_translate_episode(episode_id, custom_prompt.as_deref())
    })
    .await
    .map_err(|e| format!("任务执行失败: {}", e))?
}

/// 测试指定（或当前激活）翻译服务商连通性与试译质量
#[tauri::command]
pub async fn test_translation_provider(name: Option<String>) -> Result<ProviderTestResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        do_test_translation_provider(name.as_deref())
    })
    .await
    .map_err(|e| format!("任务执行失败: {}", e))?
}

