//! Translation sources — the `translate_config.json` the settings UI edits.
//!
//! Both processes read the same file: a source added in the desktop app has to be
//! the source the command-line translator picks up. So this is a faithful port of
//! `translate.py`'s profile handling rather than a fresh design, and it lives in
//! `gpdb-core` (no Tauri) so it can be tested without building the app.
//!
//! Standing constraint: the API key never leaves this module. Callers get `has_key`
//! and a masked `key_hint`, never the key. The one exception is `save_profile`,
//! which accepts a key to write — an omitted key keeps the stored one, because the
//! form can never be prefilled with it.
//!
//! # Where this intentionally differs from `translate.py`
//!
//! `tests/translate_config_parity.rs` runs identical fixtures and operations through
//! both and asserts the results match, so the differences that remain are deliberate
//! and listed here rather than discovered later:
//!
//! 1. **Empty values are not written.** `translate.py` writes whichever keys the
//!    form supplied, including `"model": ""`. Every reader on both sides reads a
//!    field as `or ""`, so an absent key and an empty one are indistinguishable —
//!    but the files are not byte-identical after the two implementations save.
//! 2. **`type` / `label` fall back on empty, not just on absent.** Python's
//!    `setdefault` only fills a missing key. A hand-edited `"label": ""` alone
//!    diverges; the form always sends non-empty values, so this is unreachable
//!    through the UI.
//! 3. **`migrate_config` keys off emptiness, not key presence.** Python returns
//!    early when a `profiles` key exists, so a config holding an empty `profiles`
//!    alongside legacy keys is migrated here and left alone there. Both still list
//!    the same (empty) set of sources.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};
use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

use crate::Result;

pub const CONFIG_FILE_NAME: &str = "translate_config.json";

/// Wire protocols `translate.py` can speak. Vendors that differ only by `base_url`
/// (DeepSeek, Moonshot, Ollama, ...) all reuse `openai`.
///
/// The order is `translate.py`'s `PROVIDERS` dict order, not alphabetical: it is
/// spliced into the "unsupported type" message, so the two must match to the
/// character for the settings UI to show the same error either way.
pub const PROVIDERS: [&str; 3] = ["anthropic", "openai", "gemini"];

/// A vendor template the settings form prefills. Ported from `PROVIDER_PRESETS`.
#[derive(Debug, Clone, Serialize)]
pub struct Preset {
    pub id: &'static str,
    pub label: &'static str,
    #[serde(rename = "type")]
    pub kind: &'static str,
    pub base_url: &'static str,
    pub model: &'static str,
    pub hint: &'static str,
    /// Only set for a source that works without a key (local Ollama).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub needs_key: Option<bool>,
}

const fn preset(
    id: &'static str,
    label: &'static str,
    kind: &'static str,
    base_url: &'static str,
    model: &'static str,
    hint: &'static str,
    needs_key: Option<bool>,
) -> Preset {
    Preset { id, label, kind, base_url, model, hint, needs_key }
}

pub fn presets() -> Vec<Preset> {
    vec![
        preset("deepseek", "DeepSeek 深度求索", "openai", "https://api.deepseek.com",
               "deepseek-flash", "便宜、快。国内可直连。", None),
        preset("openai", "OpenAI", "openai", "https://api.openai.com/v1",
               "gpt-4o-mini", "需要能访问 OpenAI 的网络环境。", None),
        preset("anthropic", "Anthropic Claude", "anthropic", "https://api.anthropic.com",
               "claude-opus-5", "译文质量最好，价格最高。", None),
        preset("gemini", "Google Gemini", "gemini", "https://generativelanguage.googleapis.com",
               "gemini-2.5-flash", "有免费额度，需要能访问 Google。", None),
        preset("moonshot", "Moonshot 月之暗面 (Kimi)", "openai", "https://api.moonshot.cn/v1",
               "moonshot-v1-8k", "中文语感好。", None),
        preset("zhipu", "智谱 AI (GLM)", "openai", "https://open.bigmodel.cn/api/paas/v4",
               "glm-4-flash", "glm-4-flash 有免费额度。", None),
        preset("dashscope", "阿里通义千问", "openai",
               "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
               "阿里云百炼平台。", None),
        preset("siliconflow", "硅基流动 SiliconFlow", "openai", "https://api.siliconflow.cn/v1",
               "Qwen/Qwen2.5-7B-Instruct", "聚合多家开源模型。", None),
        preset("ollama", "本地 Ollama", "openai", "http://localhost:11434/v1",
               "qwen2.5:7b", "完全离线、不花钱，需要本机已装 Ollama 并 pull 过模型。",
               Some(false)),
        preset("custom", "自定义 (OpenAI 兼容接口)", "openai", "", "",
               "任何兼容 /chat/completions 的服务。", None),
    ]
}

// --- The file on disk ------------------------------------------------------

/// The whole config file. `extra` keeps keys this build does not model, so saving
/// from the desktop app cannot silently drop something a newer `translate.py` added.
///
/// Empty values are skipped on write: neither reader can tell an absent key from an
/// empty one (`translate.py` reads every field as `cfg.get(k) or ""`), so the file
/// only carries what is actually set. A fresh install therefore writes `{}`.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Config {
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub active: String,
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub profiles: BTreeMap<String, ProfileEntry>,
    #[serde(flatten)]
    pub extra: BTreeMap<String, Value>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct ProfileEntry {
    #[serde(default, rename = "type", skip_serializing_if = "String::is_empty")]
    pub kind: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub label: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub api_key: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub model: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub base_url: String,
    #[serde(flatten)]
    pub extra: BTreeMap<String, Value>,
}

/// Read the config, normalising any older shape into the current one.
///
/// A missing or malformed file reads as empty rather than failing, matching
/// `translate.py`: the settings UI then shows "no sources yet" instead of an error.
pub fn load_config(path: &Path) -> Config {
    let cfg = fs::read_to_string(path)
        .ok()
        .and_then(|raw| serde_json::from_str::<Config>(&raw).ok())
        .unwrap_or_default();
    migrate_config(cfg)
}

/// Write the config atomically and owner-only — it holds API keys.
pub fn save_config(path: &Path, cfg: &Config) -> Result<()> {
    let json = serde_json::to_string_pretty(cfg).map_err(|e| e.to_string())?;
    let tmp = tmp_path(path);
    fs::write(&tmp, json).map_err(|e| e.to_string())?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&tmp, fs::Permissions::from_mode(0o600)).map_err(|e| e.to_string())?;
    }
    // Rename, not write-in-place: a crash mid-save must not leave a truncated file
    // holding the only copy of a key.
    fs::rename(&tmp, path).map_err(|e| e.to_string())?;
    Ok(())
}

/// `translate_config.json` → `translate_config.json.tmp`, matching `translate.py`.
fn tmp_path(path: &Path) -> PathBuf {
    let mut name = path.file_name().unwrap_or_default().to_os_string();
    name.push(".tmp");
    path.with_file_name(name)
}

// --- Migration -------------------------------------------------------------

/// Fold an older config shape into the current one. Idempotent.
///
/// Older builds stored the settings flat, and briefly kept a `providers` map keyed
/// by protocol. Both are folded into `profiles`, and the superseded keys are
/// dropped so the file never holds two copies of an API key.
///
/// `translate.py` decides "already current" by the presence of a `profiles` key; a
/// config that has an empty `profiles` *and* legacy keys would therefore be left
/// alone by Python but migrated here. No released build produced that shape, and
/// every reader treats an absent map and an empty one identically.
pub fn migrate_config(mut cfg: Config) -> Config {
    if !cfg.profiles.is_empty() {
        return cfg;
    }

    let flat_key = take_str(&mut cfg.extra, "api_key");
    if let Some(Value::Object(map)) = cfg.extra.remove("providers") {
        for (name, entry) in map {
            let Value::Object(entry) = entry else { continue };
            let base_url = str_field(&entry, "base_url");
            let kind = non_empty(str_field(&entry, "type"), &name);
            let key = name_for_base_url(&base_url, &name);
            let label = non_empty(
                str_field(&entry, "label"),
                &preset_label(&key).unwrap_or_else(|| key.clone()),
            );
            cfg.profiles.insert(key, ProfileEntry {
                kind,
                label,
                api_key: str_field(&entry, "api_key"),
                model: str_field(&entry, "model"),
                base_url,
                extra: BTreeMap::new(),
            });
        }
    }

    // A flat key only becomes a source when no source already carries one, so a
    // config that has both cannot end up storing the key twice.
    let any_key = cfg.profiles.values().any(|p| !p.api_key.is_empty());
    if !flat_key.is_empty() && !any_key {
        let kind = non_empty(extra_str(&cfg.extra, "provider").trim().to_lowercase(), "openai");
        let base_url = extra_str(&cfg.extra, "base_url");
        let model = extra_str(&cfg.extra, "model");
        let key = name_for_base_url(&base_url, &kind);
        let label = preset_label(&key).unwrap_or_else(|| key.clone());
        cfg.profiles.insert(key, ProfileEntry {
            kind, label, api_key: flat_key, model, base_url, extra: BTreeMap::new(),
        });
    }

    if !cfg.profiles.is_empty() {
        let mut active = cfg.active.clone();
        if !cfg.profiles.contains_key(&active) {
            // A legacy `provider` naming the protocol still identifies the source
            // when only one was saved, which is the single-source upgrade case.
            let legacy = extra_str(&cfg.extra, "provider").to_lowercase();
            if cfg.profiles.len() == 1 && cfg.profiles.contains_key(&legacy) {
                active = legacy;
            }
        }
        cfg.active = if cfg.profiles.contains_key(&active) {
            active
        } else {
            cfg.profiles.keys().next().cloned().unwrap_or_default()
        };
    }

    for obsolete in ["provider", "api_key", "model", "base_url", "providers"] {
        cfg.extra.remove(obsolete);
    }
    cfg
}

/// Pick the preset whose host matches, so a source is named after the service it
/// points at ("deepseek") rather than the protocol it speaks ("openai").
fn name_for_base_url(base_url: &str, fallback: &str) -> String {
    if let Some(host) = netloc(base_url) {
        for p in presets() {
            if !p.base_url.is_empty() && netloc(p.base_url).as_deref() == Some(host.as_str()) {
                return p.id.to_string();
            }
        }
    }
    fallback.to_string()
}

/// `urllib.parse.urlparse(url).netloc`, near enough: scheme://[user@]host[:port].
/// A URL with no scheme yields `None`, exactly as Python yields an empty netloc.
fn netloc(url: &str) -> Option<String> {
    let rest = url.split_once("://")?.1;
    let end = rest.find(['/', '?', '#']).unwrap_or(rest.len());
    let authority = &rest[..end];
    if authority.is_empty() {
        return None;
    }
    Some(authority.rsplit('@').next().unwrap_or(authority).to_string())
}

fn preset_label(id: &str) -> Option<String> {
    presets().into_iter().find(|p| p.id == id).map(|p| p.label.to_string())
}

fn str_field(map: &Map<String, Value>, key: &str) -> String {
    map.get(key).and_then(|v| v.as_str()).unwrap_or("").to_string()
}

fn extra_str(map: &BTreeMap<String, Value>, key: &str) -> String {
    map.get(key).and_then(|v| v.as_str()).unwrap_or("").to_string()
}

fn take_str(map: &mut BTreeMap<String, Value>, key: &str) -> String {
    map.remove(key).and_then(|v| v.as_str().map(str::to_string)).unwrap_or_default()
}

fn non_empty(value: String, fallback: &str) -> String {
    if value.is_empty() { fallback.to_string() } else { value }
}

// --- What the settings UI sees ---------------------------------------------

/// One saved source. The key is reduced to a flag and a hint before it gets here.
#[derive(Debug, Clone, Serialize)]
pub struct Profile {
    pub name: String,
    pub label: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub model: String,
    pub base_url: String,
    pub has_key: bool,
    pub key_hint: String,
    pub active: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct Providers {
    pub profiles: Vec<Profile>,
    pub presets: Vec<Preset>,
    pub config_file: String,
}

/// `sk-099062…ee01`, or `已保存` for a short key, or empty when none is stored.
fn key_hint(key: &str) -> String {
    if key.is_empty() {
        return String::new();
    }
    let chars: Vec<char> = key.chars().collect();
    if chars.len() > 12 {
        let head: String = chars[..6].iter().collect();
        let tail: String = chars[chars.len() - 4..].iter().collect();
        format!("{}…{}", head, tail)
    } else {
        "已保存".to_string()
    }
}

pub fn list_profiles(path: &Path) -> Vec<Profile> {
    let cfg = load_config(path);
    let mut out: Vec<Profile> = cfg
        .profiles
        .iter()
        .map(|(name, p)| Profile {
            name: name.clone(),
            label: non_empty(p.label.clone(), name),
            kind: non_empty(p.kind.clone(), "openai"),
            model: p.model.clone(),
            base_url: p.base_url.clone(),
            has_key: !p.api_key.is_empty(),
            key_hint: key_hint(&p.api_key),
            active: *name == cfg.active,
        })
        .collect();
    // The source in use first, then by label. `translate.py` sorts the same way, so
    // the two lists never disagree about order.
    out.sort_by(|a, b| b.active.cmp(&a.active).then_with(|| a.label.cmp(&b.label)));
    out
}

pub fn providers(path: &Path) -> Providers {
    Providers {
        profiles: list_profiles(path),
        presets: presets(),
        config_file: CONFIG_FILE_NAME.to_string(),
    }
}

/// Form payload. An absent field (`None`) means "leave the stored value alone".
#[derive(Debug, Clone, Default, Deserialize)]
pub struct ProfileInput {
    #[serde(default)]
    pub name: String,
    #[serde(default, rename = "type")]
    pub kind: Option<String>,
    #[serde(default)]
    pub label: Option<String>,
    #[serde(default)]
    pub model: Option<String>,
    #[serde(default)]
    pub base_url: Option<String>,
    #[serde(default)]
    pub api_key: Option<String>,
    #[serde(default)]
    pub active: Option<bool>,
}

pub fn validate_type(kind: &str) -> Result<()> {
    if PROVIDERS.contains(&kind) {
        Ok(())
    } else {
        Err(format!("不支持的接口类型 '{}'，可选: {}", kind, PROVIDERS.join(", ")).into())
    }
}

/// Create or update one source. Mirrors `translate.py`'s `save_profile`.
///
/// Only the fields actually supplied are written; `type` and `label` then fall back
/// to `openai` and to the source's own name if still unset. Only `api_key` is
/// keep-if-blank, because the UI is never handed the key back and so a form saved
/// without retyping it must not erase it.
///
/// This deliberately does *not* validate `type` and does *not* default `model` /
/// `base_url` on every save — `translate.py` does neither, and enforcing them here
/// would make the two implementations write different files. Those belong to the
/// form's transport, which is `commands/translate.rs`.
pub fn save_profile(path: &Path, input: &ProfileInput) -> Result<()> {
    let name = input.name.trim();
    if name.is_empty() {
        return Err("配置名称不能为空".to_string().into());
    }

    let mut cfg = load_config(path);
    let entry = cfg.profiles.entry(name.to_string()).or_default();
    if let Some(v) = &input.kind {
        entry.kind = v.trim().to_string();
    }
    if let Some(v) = &input.label {
        entry.label = v.trim().to_string();
    }
    if let Some(v) = &input.model {
        entry.model = v.trim().to_string();
    }
    if let Some(v) = &input.base_url {
        entry.base_url = v.trim().to_string();
    }
    if let Some(key) = input.api_key.as_deref().map(str::trim) {
        if !key.is_empty() {
            entry.api_key = key.to_string();
        }
    }
    if entry.kind.is_empty() {
        entry.kind = "openai".to_string();
    }
    if entry.label.is_empty() {
        entry.label = name.to_string();
    }
    // A config with no source yet adopts this one, so the first save is usable
    // without a second click on "use this source".
    if cfg.active.is_empty() {
        cfg.active = name.to_string();
    }
    save_config(path, &cfg)
}

pub fn set_active_profile(path: &Path, name: &str) -> Result<()> {
    let mut cfg = load_config(path);
    if !cfg.profiles.contains_key(name) {
        return Err(format!("没有名为 '{}' 的翻译服务配置", name).into());
    }
    cfg.active = name.to_string();
    save_config(path, &cfg)
}

pub fn delete_profile(path: &Path, name: &str) -> Result<()> {
    let mut cfg = load_config(path);
    if cfg.profiles.remove(name).is_none() {
        return Err(format!("没有名为 '{}' 的翻译服务配置", name).into());
    }
    if cfg.active == name {
        cfg.active = cfg.profiles.keys().next().cloned().unwrap_or_default();
    }
    save_config(path, &cfg)
}
