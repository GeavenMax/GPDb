//! 增量同步：跑 sync_gevi.py，从它的标准输出里读回新增数量。
//!
//! 这不是查询，所以留在 app 侧。阶段 7 连它一起重新考虑 —— 它依赖 Python 解释器
//! 和仓库根目录下的脚本，是 GEVI+ 里最后一块非 SQLite 的依赖。

use gevi_core::models::SyncResult;
use std::path::PathBuf;

#[tauri::command]
pub fn run_sync() -> Result<SyncResult, String> {
    // Run sync_gevi.py as a subprocess
    let script_path = PathBuf::from("sync_gevi.py");
    let script = if script_path.exists() {
        script_path
    } else {
        PathBuf::from("../sync_gevi.py")
    };

    let output = std::process::Command::new("python3")
        .arg(&script)
        .arg("--probe-count")
        .arg("10")
        .output()
        .map_err(|e| format!("Failed to spawn sync script: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut new_movies = 0;
    let mut new_performers = 0;

    for line in stdout.lines() {
        if line.contains("Saved") && line.contains("movies") {
            if let Some(num_str) = line.split_whitespace().nth(1) {
                new_movies = num_str.parse().unwrap_or(0);
            }
        }
        if line.contains("Saved") && line.contains("performers") {
            if let Some(num_str) = line.split_whitespace().nth(1) {
                new_performers = num_str.parse().unwrap_or(0);
            }
        }
    }

    Ok(SyncResult {
        new_movies,
        new_performers,
    })
}
