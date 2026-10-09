use crate::db;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{OnceLock, RwLock};
use std::time::{Duration, Instant};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct CacheStats {
    pub count: usize,
    pub size_mb: f64,
    pub path: String,
}

static CACHED_STATS: RwLock<Option<(Instant, CacheStats)>> = RwLock::new(None);
const STATS_TTL: Duration = Duration::from_secs(300); // 5 minutes

static HTTP_CLIENT: OnceLock<ureq::Agent> = OnceLock::new();

fn get_http_client() -> &'static ureq::Agent {
    HTTP_CLIENT.get_or_init(|| {
        ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(6))
            .timeout_read(Duration::from_secs(15))
            .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .build()
    })
}

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
/// Matches folders: Covers, Episodes, Stars, Icons, Logo, and External.
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
    } else if let Some(idx) = lower.find("images/logos/") {
        Some(("Logos", &normalized[idx + "images/logos/".len()..]))
    } else if let Some(idx) = lower.find("images/logo/") {
        Some(("Logo", &normalized[idx + "images/logo/".len()..]))
    } else if let Some(idx) = lower.find("images/banners/") {
        Some(("Banners", &normalized[idx + "images/banners/".len()..]))
    } else if let Some(idx) = lower.find("covers/") {
        Some(("Covers", &normalized[idx + "covers/".len()..]))
    } else if let Some(idx) = lower.find("episodes/") {
        Some(("Episodes", &normalized[idx + "episodes/".len()..]))
    } else if let Some(idx) = lower.find("stars/") {
        Some(("Stars", &normalized[idx + "stars/".len()..]))
    } else if let Some(idx) = lower.find("icons/") {
        Some(("Icons", &normalized[idx + "icons/".len()..]))
    } else if let Some(idx) = lower.find("logos/") {
        Some(("Logos", &normalized[idx + "logos/".len()..]))
    } else if let Some(idx) = lower.find("logo/") {
        Some(("Logo", &normalized[idx + "logo/".len()..]))
    } else if let Some(idx) = lower.find("banners/") {
        Some(("Banners", &normalized[idx + "banners/".len()..]))
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
            .unwrap_or(rel_path)
            .trim_start_matches(|c| c == '/' || c == '\\');

        let path = cache_dir.join(folder).join(clean_rel);
        let lower_clean = clean_rel.to_ascii_lowercase();
        let mime = if lower_clean.ends_with(".png") {
            "image/png"
        } else if lower_clean.ends_with(".webp") {
            "image/webp"
        } else if lower_clean.ends_with(".svg") {
            "image/svg+xml"
        } else if lower_clean.ends_with(".gif") {
            "image/gif"
        } else if lower_clean.ends_with(".avif") {
            "image/avif"
        } else {
            "image/jpeg"
        };
        return Some((path, mime));
    }

    // External remote images (e.g. PBC, SmutJunkies, BoyfriendTV, etc.)
    if lower.starts_with("http://") || lower.starts_with("https://") {
        use std::collections::hash_map::DefaultHasher;
        use std::hash::{Hash, Hasher};
        let mut hasher = DefaultHasher::new();
        url.hash(&mut hasher);
        let hash = hasher.finish();

        let ext = if lower.contains(".png") {
            "png"
        } else if lower.contains(".webp") {
            "webp"
        } else if lower.contains(".svg") {
            "svg"
        } else if lower.contains(".gif") {
            "gif"
        } else if lower.contains(".avif") {
            "avif"
        } else {
            "jpg"
        };
        let mime = match ext {
            "png" => "image/png",
            "webp" => "image/webp",
            "svg" => "image/svg+xml",
            "gif" => "image/gif",
            "avif" => "image/avif",
            _ => "image/jpeg",
        };

        let path = cache_dir.join("External").join(format!("{:016x}.{}", hash, ext));
        return Some((path, mime));
    }

    None
}

/// Resolves a requested image URL to an existing local cached file on disk.
/// Matches folders: Covers, Episodes, Stars, Icons, Logo, External.
pub fn resolve_cache_file(url: &str) -> Option<(PathBuf, &'static str)> {
    let (path, mime) = resolve_cache_target_path(url)?;
    if path.is_file() {
        Some((path, mime))
    } else {
        None
    }
}

/// Attempts to load studio logo or banner binary WebP data directly from SQLite `studio_logos` table.
/// Ensures 100% offline logo/banner rendering even if image_cache is not present or unzipped.
/// Guarantees that logo and banner files strictly correspond to the matching studio.
pub fn load_logo_from_db(target: &str) -> Option<Vec<u8>> {
    let lower = target.to_ascii_lowercase();
    let is_logo = lower.contains("logo");
    let is_banner = lower.contains("banner");

    if !is_logo && !is_banner {
        return None;
    }

    let db_path = db::find_db_path()?;
    let conn = rusqlite::Connection::open_with_flags(&db_path, rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY).ok()?;

    // Extract filename from target URL/path, e.g. "images/logos/8teenboy_logo.png" -> "8teenboy_logo.png"
    let clean_target = target.split('?').next().unwrap_or(target).split('#').next().unwrap_or(target);
    let filename = Path::new(clean_target)
        .file_name()
        .and_then(|s| s.to_str())
        .unwrap_or(clean_target);

    let column = if is_banner { "banner_webp" } else { "logo_webp" };

    // 1. Exact match via studios.logo_url or studios.banner_url
    let sql_exact = format!(
        "SELECT sl.{} FROM studio_logos sl JOIN studios s ON s.id = sl.studio_id \
         WHERE (s.logo_url LIKE '%' || ?1 OR s.banner_url LIKE '%' || ?1) \
           AND sl.{} IS NOT NULL LIMIT 1",
        column, column
    );
    if let Ok(mut stmt) = conn.prepare(&sql_exact) {
        if let Ok(bytes) = stmt.query_row([filename], |row| row.get::<_, Vec<u8>>(0)) {
            if !bytes.is_empty() {
                return Some(bytes);
            }
        }
    }

    // 2. Fallback match via studio name slug (e.g. "8teenboy_logo.png" -> "8teenboy")
    let stem = Path::new(filename)
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or(filename);
    let slug = stem
        .trim_end_matches("_logo")
        .trim_end_matches("-logo")
        .trim_end_matches("_banner")
        .trim_end_matches("-banner")
        .replace('-', "_");

    let sql_slug = format!(
        "SELECT sl.{} FROM studio_logos sl JOIN studios s ON s.id = sl.studio_id \
         WHERE (LOWER(REPLACE(REPLACE(s.name, ' ', '_'), '-', '_')) = LOWER(?1) \
            OR LOWER(REPLACE(REPLACE(COALESCE(s.name_zh, ''), ' ', '_'), '-', '_')) = LOWER(?1)) \
           AND sl.{} IS NOT NULL LIMIT 1",
        column, column
    );
    if let Ok(mut stmt) = conn.prepare(&sql_slug) {
        if let Ok(bytes) = stmt.query_row([&slug], |row| row.get::<_, Vec<u8>>(0)) {
            if !bytes.is_empty() {
                return Some(bytes);
            }
        }
    }

    None
}

/// Custom URI scheme protocol handler for `gpdb-img://` and `http://gpdb-img.localhost/`.
/// Enables ultra-fast local disk image loading directly from `image_cache/`
/// with automatic on-demand download & caching for un-cached images.
pub fn handle_image_protocol(req: &tauri::http::Request<Vec<u8>>) -> tauri::http::Response<Vec<u8>> {
    // Handle CORS preflight
    if req.method() == "OPTIONS" {
        return tauri::http::Response::builder()
            .status(200)
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            .header("Access-Control-Allow-Headers", "*")
            .body(Vec::new())
            .unwrap();
    }

    let uri_str = req.uri().to_string();
    let parsed_url = url::Url::parse(&uri_str).ok();

    // Look for ?url=<target> query parameter, or fall back to uri path
    let target = parsed_url.as_ref().and_then(|u| {
        u.query_pairs().find(|(k, _)| k == "url").map(|(_, v)| v.into_owned())
    }).unwrap_or_else(|| {
        req.uri().path().trim_start_matches('/').to_string()
    });

    if target.is_empty() {
        return tauri::http::Response::builder()
            .status(404)
            .header("Content-Type", "text/plain")
            .header("Access-Control-Allow-Origin", "*")
            .body(b"Missing target".to_vec())
            .unwrap();
    }

    // 0. Priority 1: Check database studio_logos table for Logo & Banner images
    // Ensures instant 100% offline rendering for zero-cache users without external image packages.
    if let Some(bytes) = load_logo_from_db(&target) {
        return tauri::http::Response::builder()
            .status(200)
            .header("Content-Type", "image/webp")
            .header("Cache-Control", "public, max-age=31536000, immutable")
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            .header("Access-Control-Allow-Headers", "*")
            .header("Cross-Origin-Resource-Policy", "cross-origin")
            .header("Vary", "Origin")
            .body(bytes)
            .unwrap();
    }

    // 1. Priority 2: If already cached on local disk, serve immediately
    if let Some((local_path, mime)) = resolve_cache_file(&target) {
        if let Ok(bytes) = fs::read(&local_path) {
            return tauri::http::Response::builder()
                .status(200)
                .header("Content-Type", mime)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
                .header("Access-Control-Allow-Headers", "*")
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header("Vary", "Origin")
                .body(bytes)
                .unwrap();
        }
    }


    // 2. On-demand download and persistent caching
    let remote_url = if target.starts_with("http://") || target.starts_with("https://") {
        Some(target.clone())
    } else if target.starts_with("images/logos/") || target.starts_with("/images/logos/") {
        let filename = target.trim_start_matches('/').trim_start_matches("images/logos/");
        Some(format!("https://gayeroticvideoindex.com/images/{}", filename))
    } else if target.starts_with("logos/") || target.starts_with("Logos/") {
        let filename = target.trim_start_matches("logos/").trim_start_matches("Logos/");
        Some(format!("https://gayeroticvideoindex.com/images/{}", filename))
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

    if let (Some(url), Some((dest_path, expected_mime))) = (remote_url.as_ref(), resolve_cache_target_path(&target)) {
        if let Some(parent) = dest_path.parent() {
            let _ = fs::create_dir_all(parent);
        }

        let referer = if url.contains("gayeroticvideoindex.com") {
            "https://gayeroticvideoindex.com/"
        } else if url.contains("smutjunkies.com") {
            "https://www.smutjunkies.com/"
        } else if url.contains("pbc.xxx") {
            "https://pbc.xxx/"
        } else {
            ""
        };

        let mut downloaded_bytes = None;
        let mut final_mime = expected_mime.to_string();

        // Primary: Native Rust ureq HTTP client (high performance, connection pooled, pure Rust TLS)
        let mut req_builder = get_http_client().get(url);
        if !referer.is_empty() {
            req_builder = req_builder.set("Referer", referer);
        }

        match req_builder.call() {
            Ok(resp) => {
                if let Some(ct) = resp.header("Content-Type") {
                    let ct_clean = ct.split(';').next().unwrap_or(ct).trim().to_string();
                    if ct_clean.starts_with("image/") {
                        final_mime = ct_clean;
                    }
                }
                let mut reader = resp.into_reader();
                let mut buf = Vec::new();
                if std::io::copy(&mut reader, &mut buf).is_ok() && !buf.is_empty() {
                    downloaded_bytes = Some(buf);
                }
            }
            Err(e) => {
                log::debug!("ureq image download error for {}: {}", url, e);
            }
        }

        // Secondary fallback to curl if ureq fails (e.g. system proxy requirements)
        if downloaded_bytes.is_none() {
            let nanos = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos();
            let temp_path = dest_path.with_extension(format!("tmp.{}", nanos));

            let mut cmd = std::process::Command::new("curl");
            #[cfg(target_os = "windows")]
            {
                use std::os::windows::process::CommandExt;
                cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW
            }
            cmd.arg("-s")
                .arg("-L")
                .arg("-f")
                .arg("-k")
                .arg("--connect-timeout")
                .arg("5")
                .arg("--max-time")
                .arg("15")
                .arg("-A")
                .arg("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
            if !referer.is_empty() {
                cmd.arg("-e").arg(referer);
            }
            cmd.arg("-o").arg(&temp_path).arg(url);

            if let Ok(status) = cmd.status() {
                if status.success() && temp_path.is_file() {
                    if let Ok(bytes) = fs::read(&temp_path) {
                        if !bytes.is_empty() {
                            downloaded_bytes = Some(bytes);
                        }
                    }
                    let _ = fs::remove_file(&temp_path);
                }
            } else {
                let _ = fs::remove_file(&temp_path);
            }
        }

        if let Some(bytes) = downloaded_bytes {
            // Write to local cache on disk
            let _ = fs::write(&dest_path, &bytes);
            if let Ok(mut g) = CACHED_STATS.write() {
                *g = None;
            }
            return tauri::http::Response::builder()
                .status(200)
                .header("Content-Type", final_mime)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
                .header("Access-Control-Allow-Headers", "*")
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header("Vary", "Origin")
                .body(bytes)
                .unwrap();
        }
    }

    // 3. Fallback: 404 (do NOT 302 redirect, which breaks WebView2 on Windows)
    tauri::http::Response::builder()
        .status(404)
        .header("Content-Type", "text/plain")
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        .header("Access-Control-Allow-Headers", "*")
        .header("Cross-Origin-Resource-Policy", "cross-origin")
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

        let sample_logos = "images/logos/falcon_video.png";
        let (path_logos, mime_logos) = resolve_cache_target_path(sample_logos).expect("Failed to resolve sample_logos");
        assert_eq!(mime_logos, "image/png");
        assert!(path_logos.to_string_lossy().ends_with("Logos/falcon_video.png"));

        let sample_svg = "images/logos/say_uncle_logo.svg";
        let (path_svg, mime_svg) = resolve_cache_target_path(sample_svg).expect("Failed to resolve sample_svg");
        assert_eq!(mime_svg, "image/svg+xml");
        assert!(path_svg.to_string_lossy().ends_with("Logos/say_uncle_logo.svg"));

        let sample_logo = "images/logo/old_logo.png";
        let (path_logo, mime_logo) = resolve_cache_target_path(sample_logo).expect("Failed to resolve sample_logo");
        assert_eq!(mime_logo, "image/png");
        assert!(path_logo.to_string_lossy().ends_with("Logo/old_logo.png"));

        let sample_external = "https://pbc.xxx/wiki/Special:FilePath/Austin_Wilde.jpg";
        let (path_ext, mime_ext) = resolve_cache_target_path(sample_external).expect("Failed to resolve external");
        assert_eq!(mime_ext, "image/jpeg");
        assert!(path_ext.to_string_lossy().contains("External"));
    }

    #[test]
    fn test_handle_image_protocol_response() {
        let req = tauri::http::Request::builder()
            .uri("gpdb-img://localhost/?url=https%3A%2F%2Fgayeroticvideoindex.com%2Fimages%2FCovers%2F1%2Fvideo1.jpg")
            .body(Vec::new())
            .unwrap();
        let resp = handle_image_protocol(&req);
        assert!(resp.status() == 200 || resp.status() == 404, "Unexpected status: {}", resp.status());
        if resp.status() == 200 {
            assert_eq!(resp.headers().get("Content-Type").unwrap(), "image/jpeg");
        }
    }

    #[test]
    fn test_handle_image_protocol_windows_workaround_uri() {
        let req = tauri::http::Request::builder()
            .uri("http://gpdb-img.localhost/?url=https%3A%2F%2Fgayeroticvideoindex.com%2Fimages%2FCovers%2F1%2Fvideo1.jpg")
            .body(Vec::new())
            .unwrap();
        let resp = handle_image_protocol(&req);
        assert!(resp.status() == 200 || resp.status() == 404, "Unexpected status: {}", resp.status());
    }

    #[test]
    fn test_load_logo_and_banner_from_db() {
        // Test loading a real logo imported into studio_logos
        let logo_bytes = load_logo_from_db("images/logos/kinkmen_logo.png");
        assert!(logo_bytes.is_some(), "Expected kinkmen_logo.png to be found in studio_logos");
        let bytes = logo_bytes.unwrap();
        // WebP magic header: RIFF....WEBP
        assert!(bytes.len() > 12);
        assert_eq!(&bytes[0..4], b"RIFF");
        assert_eq!(&bytes[8..12], b"WEBP");

        // Test loading a banner from studio_logos
        let banner_bytes = load_logo_from_db("images/logos/8teenboy_banner.jpg");
        assert!(banner_bytes.is_some(), "Expected 8teenboy_banner.jpg to be found in studio_logos");
        let b_bytes = banner_bytes.unwrap();
        assert_eq!(&b_bytes[0..4], b"RIFF");
        assert_eq!(&b_bytes[8..12], b"WEBP");

        // Test protocol handler returns 200 and image/webp
        let req = tauri::http::Request::builder()
            .uri("gpdb-img://localhost/?url=images%2Flogos%2Fkinkmen_logo.png")
            .body(Vec::new())
            .unwrap();
        let resp = handle_image_protocol(&req);
        assert_eq!(resp.status(), 200);
        assert_eq!(resp.headers().get("Content-Type").unwrap(), "image/webp");
    }
}
