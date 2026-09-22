use crate::db;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct CacheStats {
    pub count: usize,
    pub size_mb: f64,
    pub path: String,
}

pub fn find_cache_dir() -> PathBuf {
    // 1. If gevi.db is found, image_cache is right next to it
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

#[tauri::command]
pub fn get_cache_stats() -> Result<CacheStats, String> {
    let cache_dir = find_cache_dir();
    if !cache_dir.exists() {
        return Ok(CacheStats {
            count: 0,
            size_mb: 0.0,
            path: cache_dir.to_string_lossy().to_string(),
        });
    }

    let mut count = 0;
    let mut total_bytes = 0u64;

    fn walk_dir(dir: &Path, count: &mut usize, bytes: &mut u64) {
        if let Ok(entries) = fs::read_dir(dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() {
                    walk_dir(&path, count, bytes);
                } else if path.is_file() {
                    let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("");
                    if !name.ends_with(".tmp") && !name.starts_with('.') {
                        if let Ok(meta) = entry.metadata() {
                            *bytes += meta.len();
                            *count += 1;
                        }
                    }
                }
            }
        }
    }

    walk_dir(&cache_dir, &mut count, &mut total_bytes);

    let size_mb = (total_bytes as f64 / 1_048_576.0 * 100.0).round() / 100.0;

    Ok(CacheStats {
        count,
        size_mb,
        path: cache_dir.to_string_lossy().to_string(),
    })
}

#[tauri::command]
pub fn clear_cache() -> Result<usize, String> {
    let cache_dir = find_cache_dir();
    if !cache_dir.exists() {
        return Ok(0);
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
    Ok(cleared)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cache_stats_finds_dir_or_returns_zero() {
        let stats = get_cache_stats().expect("cache stats should not error");
        assert!(!stats.path.is_empty());
        // If image_cache exists on disk, it should report count and size
        let cache_dir = PathBuf::from(&stats.path);
        if cache_dir.exists() {
            println!("Discovered cache dir: {}, count: {}, size_mb: {}", stats.path, stats.count, stats.size_mb);
        }
    }
}
