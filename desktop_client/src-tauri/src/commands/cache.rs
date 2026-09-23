use crate::db;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::RwLock;
use std::time::{Duration, Instant};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct CacheStats {
    pub count: usize,
    pub size_mb: f64,
    pub path: String,
}

static CACHED_STATS: RwLock<Option<(Instant, CacheStats)>> = RwLock::new(None);
const STATS_TTL: Duration = Duration::from_secs(300); // 5 minutes

pub fn find_cache_dir() -> PathBuf {
    // 1. If GPDb.db is found, image_cache is right next to it
    if let Some(db_path) = db::find_db_path() {
        if let Some(parent) = db_path.parent() {
            let cache = parent.join("image_cache");
            if cache.exists() {
                return cache;
            }
        }
    }

    // 2. Fallback to common search paths or current directory
    let candidates = [
        PathBuf::from("image_cache"),
        PathBuf::from("../image_cache"),
        PathBuf::from("../../image_cache"),
    ];
    for c in candidates {
        if c.exists() {
            if let Ok(abs) = c.canonicalize() {
                return abs;
            }
        }
    }

    // Default to alongside db or current folder
    if let Some(db_path) = db::find_db_path() {
        if let Some(parent) = db_path.parent() {
            return parent.join("image_cache");
        }
    }
    PathBuf::from("image_cache")
}

fn compute_cache_stats() -> CacheStats {
    let cache_dir = find_cache_dir();
    if !cache_dir.exists() {
        return CacheStats {
            count: 0,
            size_mb: 0.0,
            path: cache_dir.to_string_lossy().to_string(),
        };
    }

    let mut count = 0;
    let mut total_bytes = 0u64;

    fn walk_dir(dir: &Path, count: &mut usize, bytes: &mut u64) {
        if let Ok(entries) = fs::read_dir(dir) {
            for entry in entries.flatten() {
                if let Ok(file_type) = entry.file_type() {
                    if file_type.is_dir() {
                        walk_dir(&entry.path(), count, bytes);
                    } else if file_type.is_file() {
                        let name = entry.file_name();
                        let name_str = name.to_string_lossy();
                        if !name_str.ends_with(".tmp") && !name_str.starts_with('.') {
                            if let Ok(meta) = entry.metadata() {
                                *bytes += meta.len();
                                *count += 1;
                            }
                        }
                    }
                }
            }
        }
    }

    walk_dir(&cache_dir, &mut count, &mut total_bytes);

    let size_mb = (total_bytes as f64 / 1_048_576.0 * 100.0).round() / 100.0;

    CacheStats {
        count,
        size_mb,
        path: cache_dir.to_string_lossy().to_string(),
    }
}

#[tauri::command]
pub async fn get_cache_stats() -> Result<CacheStats, String> {
    // 1. Fast read from memory cache
    if let Ok(guard) = CACHED_STATS.read() {
        if let Some((instant, ref stats)) = *guard {
            if instant.elapsed() < STATS_TTL {
                return Ok(stats.clone());
            }
        }
    }

    // 2. Offload heavy disk walking to background thread
    let stats = tauri::async_runtime::spawn_blocking(compute_cache_stats)
        .await
        .map_err(|e| e.to_string())?;

    if let Ok(mut guard) = CACHED_STATS.write() {
        *guard = Some((Instant::now(), stats.clone()));
    }

    Ok(stats)
}

#[tauri::command]
pub async fn clear_cache() -> Result<usize, String> {
    let cleared = tauri::async_runtime::spawn_blocking(|| {
        let cache_dir = find_cache_dir();
        if !cache_dir.exists() {
            return 0;
        }

        let mut cleared = 0;
        fn walk_remove(dir: &Path, cleared: &mut usize) {
            if let Ok(entries) = fs::read_dir(dir) {
                for entry in entries.flatten() {
                    let path = entry.path();
                    if path.is_dir() {
                        walk_remove(&path, cleared);
                        let _ = fs::remove_dir(&path);
                    } else if path.is_file() {
                        if fs::remove_file(&path).is_ok() {
                            *cleared += 1;
                        }
                    }
                }
            }
        }

        walk_remove(&cache_dir, &mut cleared);
        cleared
    })
    .await
    .map_err(|e| e.to_string())?;

    if let Ok(mut guard) = CACHED_STATS.write() {
        *guard = None;
    }

    Ok(cleared)
}

/// Resolves a requested image URL to a local cached file on disk.
/// Matches folders: Covers, Episodes, Stars, Icons, Logo.
/// Resolves what the destination path in local image_cache should be.
pub fn resolve_cache_target_path(url: &str) -> Option<(PathBuf, &'static str)> {
    let cache_dir = find_cache_dir();

    let normalized = url.replace('\\', "/");
    let lower = normalized.to_ascii_lowercase();

    let folder_and_subpath = if let Some(idx) = lower.find("images/covers/") {
        Some(("Covers", &normalized[idx + "images/covers/".len()..]))
    } else if let Some(idx) = lower.find("images/episodes/") {
        Some(("Episodes", &normalized[idx + "images/episodes/".len()..]))
    } else if let Some(idx) = lower.find("images/stars/") {
        Some(("Stars", &normalized[idx + "images/stars/".len()..]))
    } else if let Some(idx) = lower.find("images/icons/") {
        Some(("Icons", &normalized[idx + "images/icons/".len()..]))
    } else if let Some(idx) = lower.find("images/logo/") {
        Some(("Logo", &normalized[idx + "images/logo/".len()..]))
    } else if let Some(idx) = lower.find("covers/") {
        Some(("Covers", &normalized[idx + "covers/".len()..]))
    } else if let Some(idx) = lower.find("episodes/") {
        Some(("Episodes", &normalized[idx + "episodes/".len()..]))
    } else if let Some(idx) = lower.find("stars/") {
        Some(("Stars", &normalized[idx + "stars/".len()..]))
    } else if let Some(idx) = lower.find("icons/") {
        Some(("Icons", &normalized[idx + "icons/".len()..]))
    } else if let Some(idx) = lower.find("logo/") {
        Some(("Logo", &normalized[idx + "logo/".len()..]))
    } else {
        None
    };

    if let Some((folder, rel_path)) = folder_and_subpath {
        // Strip query string and fragment
        let clean_rel = rel_path
            .split('?')
            .next()
            .unwrap_or(rel_path)
            .split('#')
            .next()
            .unwrap_or(rel_path);

        let path = cache_dir.join(folder).join(clean_rel);
        let mime = if clean_rel.to_ascii_lowercase().ends_with(".png") {
            "image/png"
        } else if clean_rel.to_ascii_lowercase().ends_with(".webp") {
            "image/webp"
        } else {
            "image/jpeg"
        };
        return Some((path, mime));
    }

    None
}

/// Resolves a requested image URL to an existing local cached file on disk.
/// Matches folders: Covers, Episodes, Stars, Icons, Logo.
pub fn resolve_cache_file(url: &str) -> Option<(PathBuf, &'static str)> {
    let (path, mime) = resolve_cache_target_path(url)?;
    if path.is_file() {
        Some((path, mime))
    } else {
        None
    }
}

/// Custom URI scheme protocol handler for `gpdb-img://`.
/// Enables ultra-fast local disk image loading directly from `image_cache/`
/// with automatic on-demand download & caching for un-cached images.
pub fn handle_image_protocol(req: &tauri::http::Request<Vec<u8>>) -> tauri::http::Response<Vec<u8>> {
    let uri_str = req.uri().to_string();
    let parsed_url = url::Url::parse(&uri_str).ok();

    // Look for ?url=<target> query parameter, or fall back to uri path
    let target = parsed_url.as_ref().and_then(|u| {
        u.query_pairs().find(|(k, _)| k == "url").map(|(_, v)| v.into_owned())
    }).unwrap_or_else(|| {
        req.uri().path().trim_start_matches('/').to_string()
    });

    // 1. If already cached on local disk, serve immediately
    if let Some((local_path, mime)) = resolve_cache_file(&target) {
        if let Ok(bytes) = fs::read(&local_path) {
            return tauri::http::Response::builder()
                .status(200)
                .header("Content-Type", mime)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .header("Access-Control-Allow-Origin", "*")
                .body(bytes)
                .unwrap();
        }
    }

    // 2. On-demand download and persistent caching
    let remote_url = if target.starts_with("http://") || target.starts_with("https://") {
        Some(target.clone())
    } else if target.starts_with("images/") || target.starts_with("/images/") {
        Some(format!("https://gayeroticvideoindex.com/{}", target.trim_start_matches('/')))
    } else if target.starts_with("Covers/") || target.starts_with("covers/")
        || target.starts_with("Episodes/") || target.starts_with("episodes/")
        || target.starts_with("Stars/") || target.starts_with("stars/")
        || target.starts_with("Icons/") || target.starts_with("icons/")
        || target.starts_with("Logo/") || target.starts_with("logo/") {
        Some(format!("https://gayeroticvideoindex.com/images/{}", target.trim_start_matches('/')))
    } else {
        None
    };

    if let (Some(url), Some((dest_path, mime))) = (remote_url.as_ref(), resolve_cache_target_path(&target)) {
        if let Some(parent) = dest_path.parent() {
            let _ = fs::create_dir_all(parent);
        }

        // Fetch via curl and persist to disk safely with atomic temp file
        let nanos = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_nanos();
        let temp_path = dest_path.with_extension(format!("tmp.{}", nanos));

        let status = std::process::Command::new("curl")
            .arg("-s")
            .arg("-L")
            .arg("-f")
            .arg("--connect-timeout")
            .arg("4")
            .arg("--max-time")
            .arg("12")
            .arg("-A")
            .arg("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .arg("-e")
            .arg("https://gayeroticvideoindex.com/")
            .arg("-o")
            .arg(&temp_path)
            .arg(url)
            .status();

        if status.is_ok_and(|s| s.success()) && temp_path.is_file() {
            if let Ok(meta) = fs::metadata(&temp_path) {
                if meta.len() > 0 {
                    let _ = fs::rename(&temp_path, &dest_path);
                    if let Ok(bytes) = fs::read(&dest_path) {
                        if let Ok(mut g) = CACHED_STATS.write() {
                            *g = None;
                        }
                        return tauri::http::Response::builder()
                            .status(200)
                            .header("Content-Type", mime)
                            .header("Cache-Control", "public, max-age=31536000, immutable")
                            .header("Access-Control-Allow-Origin", "*")
                            .body(bytes)
                            .unwrap();
                    }
                } else {
                    let _ = fs::remove_file(&temp_path);
                }
            }
        } else {
            let _ = fs::remove_file(&temp_path);
        }
    }

    // 3. Fallback: If download failed or timed out, 302 redirect so WebKit can still try loading
    if let Some(url) = remote_url {
        return tauri::http::Response::builder()
            .status(302)
            .header("Location", &url)
            .header("Access-Control-Allow-Origin", "*")
            .body(Vec::new())
            .unwrap();
    }

    tauri::http::Response::builder()
        .status(404)
        .header("Content-Type", "text/plain")
        .header("Access-Control-Allow-Origin", "*")
        .body(b"Image not found".to_vec())
        .unwrap()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cache_stats_finds_dir_or_returns_zero() {
        let stats = compute_cache_stats();
        assert!(!stats.path.is_empty());
        let cache_dir = PathBuf::from(&stats.path);
        if cache_dir.exists() {
            println!("Discovered cache dir: {}, count: {}, size_mb: {}", stats.path, stats.count, stats.size_mb);
        }
    }

    #[test]
    fn test_resolve_cache_file() {
        let sample = "https://gayeroticvideoindex.com/images/Covers/0/video10.jpg";
        if let Some((path, mime)) = resolve_cache_file(sample) {
            assert!(path.is_file());
            assert_eq!(mime, "image/jpeg");
        }
    }

    #[test]
    fn test_resolve_cache_target_path() {
        let sample1 = "https://gayeroticvideoindex.com/images/Covers/1/video1.jpg";
        let (path1, mime1) = resolve_cache_target_path(sample1).expect("Failed to resolve sample1");
        assert_eq!(mime1, "image/jpeg");
        assert!(path1.to_string_lossy().ends_with("Covers/1/video1.jpg"));

        let sample2 = "images/stars/performer10.jpg?v=123";
        let (path2, mime2) = resolve_cache_target_path(sample2).expect("Failed to resolve sample2");
        assert_eq!(mime2, "image/jpeg");
        assert!(path2.to_string_lossy().ends_with("Stars/performer10.jpg"));

        let sample3 = "covers/2/video2.jpg";
        let (path3, _) = resolve_cache_target_path(sample3).expect("Failed to resolve sample3");
        assert!(path3.to_string_lossy().ends_with("Covers/2/video2.jpg"));
    }

    #[test]
    fn test_handle_image_protocol_response() {
        let req = tauri::http::Request::builder()
            .uri("gpdb-img://localhost/?url=https%3A%2F%2Fgayeroticvideoindex.com%2Fimages%2FCovers%2F1%2Fvideo1.jpg")
            .body(Vec::new())
            .unwrap();
        let resp = handle_image_protocol(&req);
        assert!(resp.status() == 200 || resp.status() == 302, "Unexpected status: {}", resp.status());
        if resp.status() == 200 {
            assert_eq!(resp.headers().get("Content-Type").unwrap(), "image/jpeg");
        }
    }
}


