//! 异步自动化刮削与数据同步引擎中心。
//!
//! 支持后台静默运行、实时进度广播 (Tauri Event: `scraper-progress`, `scraper-finished`)、
//! 优雅停机中断 (SIGINT 安全存盘)、多模式切换 (增量同步 / 快速爬取 / 全站搜刮)。

use gpdb_core::models::SyncResult;
use serde::{Deserialize, Serialize};
use std::io::Read;
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};
use std::time::Instant;
use tauri::{AppHandle, Emitter, Manager};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ScraperStatus {
    pub running: bool,
    pub mode: String,          // "incremental" | "movies_boost" | "movies_full" | "performers_full"
    pub current_id: u32,
    pub target_total: u32,
    pub processed_count: u32,
    pub percent: f64,
    pub current_title: String,
    pub new_movies: u32,
    pub new_performers: u32,
    #[serde(default)]
    pub new_episodes: u32,
    pub speed_fps: f64,
    pub eta_minutes: f64,
    pub message: String,
    pub logs: Vec<String>,
    pub elapsed_secs: u64,
    pub finished: bool,
    pub error: Option<String>,
}

impl Default for ScraperStatus {
    fn default() -> Self {
        Self {
            running: false,
            mode: "idle".to_string(),
            current_id: 0,
            target_total: 0,
            processed_count: 0,
            percent: 0.0,
            current_title: String::new(),
            new_movies: 0,
            new_performers: 0,
            new_episodes: 0,
            speed_fps: 0.0,
            eta_minutes: 0.0,
            message: "就绪".to_string(),
            logs: Vec::new(),
            elapsed_secs: 0,
            finished: false,
            error: None,
        }
    }
}

struct ScraperManager {
    status: ScraperStatus,
    child_pid: Option<u32>,
    start_time: Option<Instant>,
}

static MANAGER: OnceLock<Mutex<ScraperManager>> = OnceLock::new();

fn manager() -> &'static Mutex<ScraperManager> {
    MANAGER.get_or_init(|| Mutex::new(ScraperManager {
        status: ScraperStatus::default(),
        child_pid: None,
        start_time: None,
    }))
}

/// 寻找可用的 Python 解释器路径 (macOS GUI App 下环境变量回退)
pub fn resolve_python() -> String {
    for exe in [
        "/opt/homebrew/bin/python3",
        "/usr/local/bin/python3",
        "/usr/bin/python3",
        "python3",
        "python",
    ] {
        if let Ok(out) = std::process::Command::new(exe).args(["-c", "print(1)"]).output() {
            if out.status.success() {
                return exe.to_string();
            }
        }
    }
    "python3".to_string()
}

/// 寻找指定的 Python 刮削脚本路径 (支持源码相对路径、数据库同级、Tauri资源包及程序祖先目录)
pub fn find_script(app: Option<&AppHandle>, name: &str) -> Option<PathBuf> {
    let candidates = [
        PathBuf::from(name),
        PathBuf::from("..").join(name),
        PathBuf::from("../..").join(name),
    ];
    for c in candidates {
        if c.exists() {
            if let Ok(abs) = c.canonicalize() {
                return Some(abs);
            }
            return Some(c);
        }
    }

    if let Some(db_path) = crate::db::find_db_path() {
        if let Some(parent) = db_path.parent() {
            let candidate = parent.join(name);
            if candidate.exists() {
                return Some(candidate);
            }
        }
    }

    if let Some(handle) = app {
        if let Ok(res_dir) = handle.path().resource_dir() {
            let candidate = res_dir.join(name);
            if candidate.exists() {
                return Some(candidate);
            }
        }
    }

    if let Ok(exe) = std::env::current_exe() {
        let mut cur = exe.parent();
        while let Some(dir) = cur {
            let candidate = dir.join(name);
            if candidate.exists() {
                return Some(candidate);
            }
            cur = dir.parent();
        }
    }

    None
}

/// 从流中读取以 '\n' 或 '\r' 结尾的非空行，实现无延迟即时流式解析
fn read_line_or_cr<R: Read>(reader: &mut R, buf: &mut String) -> std::io::Result<bool> {
    buf.clear();
    let mut byte = [0u8; 1];
    let mut has_data = false;

    while reader.read_exact(&mut byte).is_ok() {
        has_data = true;
        let c = byte[0];
        if c == b'\n' || c == b'\r' {
            if !buf.trim().is_empty() {
                return Ok(true);
            }
            // empty line, continue
            buf.clear();
        } else {
            buf.push(c as char);
        }
    }

    Ok(has_data && !buf.trim().is_empty())
}

#[tauri::command]
pub fn get_scraper_status() -> Result<ScraperStatus, String> {
    let mut mgr = manager().lock().unwrap();
    if mgr.status.running {
        if let Some(start) = mgr.start_time {
            mgr.status.elapsed_secs = start.elapsed().as_secs();
        }
    }
    Ok(mgr.status.clone())
}

#[tauri::command]
pub fn stop_scraper(app: AppHandle) -> Result<ScraperStatus, String> {
    let mut mgr = manager().lock().unwrap();
    if let Some(pid) = mgr.child_pid.take() {
        // Send SIGINT (2) for graceful SQLite write flush
        #[cfg(unix)]
        {
            let _ = std::process::Command::new("kill")
                .arg("-2")
                .arg(pid.to_string())
                .status();
        }
        #[cfg(not(unix))]
        {
            let _ = std::process::Command::new("taskkill")
                .arg("/F")
                .arg("/PID")
                .arg(pid.to_string())
                .status();
        }
    }

    mgr.status.running = false;
    mgr.status.message = "任务已由用户手动停止".to_string();
    mgr.status.logs.push("🛑 任务已由用户手动中止，已抓取数据已安全存盘。".to_string());
    
    let cloned = mgr.status.clone();
    let _ = app.emit("scraper-stopped", &cloned);
    let _ = app.emit("scraper-finished", &cloned);
    Ok(cloned)
}

#[tauri::command]
pub fn start_scraper(
    app: AppHandle,
    mode: String,
    limit: Option<u32>,
    start_id: Option<u32>,
    end_id: Option<u32>,
) -> Result<ScraperStatus, String> {
    let mut mgr = manager().lock().unwrap();
    if mgr.status.running {
        return Err("当前已有同步或刮削任务在后台运行中，请先停止或等待完成".to_string());
    }

    // Determine target script and arguments
    let db_path = crate::db::find_db_path()
        .map(|p| p.to_string_lossy().to_string())
        .unwrap_or_else(|| "gevi.db".to_string());

    let (script_name, args) = match mode.as_str() {
        "incremental" => {
            let mut a = vec!["--db".to_string(), db_path.clone(), "--probe".to_string(), "50".to_string()];
            if let Some(l) = limit {
                a.push("--probe".to_string());
                a.push(l.to_string());
            }
            ("sync_gevi.py", a)
        }
        "movies_boost" => {
            let limit_str = limit.unwrap_or(1000).to_string();
            let a = vec![
                "--mode".to_string(), "movies".to_string(),
                "--db".to_string(), db_path.clone(),
                "--reverse".to_string(),
                "--limit".to_string(), limit_str,
                "--concurrency".to_string(), "8".to_string(),
            ];
            ("batch_scraper.py", a)
        }
        "movies_full" => {
            let s_id = start_id.unwrap_or(1).to_string();
            let e_id = end_id.unwrap_or(76000).to_string();
            let mut a = vec![
                "--mode".to_string(), "movies".to_string(),
                "--db".to_string(), db_path.clone(),
                "--start".to_string(), s_id,
                "--end".to_string(), e_id,
                "--concurrency".to_string(), "8".to_string(),
            ];
            if let Some(l) = limit {
                a.push("--limit".to_string());
                a.push(l.to_string());
            }
            ("batch_scraper.py", a)
        }
        "performers_full" => {
            let mut a = vec![
                "--mode".to_string(), "performers".to_string(),
                "--db".to_string(), db_path.clone(),
                "--known-only".to_string(),
                "--concurrency".to_string(), "8".to_string(),
            ];
            if let Some(l) = limit {
                a.push("--limit".to_string());
                a.push(l.to_string());
            }
            ("batch_scraper.py", a)
        }
        _ => return Err(format!("未知的刮削模式: {}", mode)),
    };

    let script_path = find_script(Some(&app), script_name)
        .ok_or_else(|| format!("未在应用目录或父目录找到刮削脚本: {}", script_name))?;

    // Reset status
    mgr.status = ScraperStatus {
        running: true,
        mode: mode.clone(),
        current_id: 0,
        target_total: limit.unwrap_or(0),
        processed_count: 0,
        percent: 0.0,
        current_title: String::new(),
        new_movies: 0,
        new_performers: 0,
        new_episodes: 0,
        speed_fps: 0.0,
        eta_minutes: 0.0,
        message: format!("正在初始化 {} 任务引擎...", mode),
        logs: vec![format!("🚀 启动刮削脚本: {} {:?}", script_path.display(), args)],
        elapsed_secs: 0,
        finished: false,
        error: None,
    };
    mgr.start_time = Some(Instant::now());

    // Spawn child process with unbuffered IO
    let python_bin = resolve_python();
    let mut child = std::process::Command::new(&python_bin)
        .arg("-u")
        .arg(&script_path)
        .args(&args)
        .env("PYTHONUNBUFFERED", "1")
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .map_err(|e| format!("启动刮削子进程失败: {}", e))?;

    let pid = child.id();
    mgr.child_pid = Some(pid);

    let stdout = child.stdout.take();
    let stderr = child.stderr.take();
    let initial_status = mgr.status.clone();

    // Spawn background reader thread
    let app_clone = app.clone();
    std::thread::spawn(move || {
        let mut line_buf = String::new();

        if let Some(mut out) = stdout {
            let mut last_emit = Instant::now();

            while let Ok(true) = read_line_or_cr(&mut out, &mut line_buf) {
                let trimmed = line_buf.trim().to_string();
                if trimmed.is_empty() {
                    continue;
                }

                let mut mgr = manager().lock().unwrap();
                if !mgr.status.running {
                    break;
                }

                // Add to rolling log (keep last 60)
                if mgr.status.logs.len() >= 60 {
                    mgr.status.logs.remove(0);
                }
                mgr.status.logs.push(trimmed.clone());

                // Parse patterns
                // 1. "+ 新增电影 #1234: 《Title》 (Year)"
                if trimmed.contains("+ 新增电影 #") {
                    mgr.status.new_movies += 1;
                    if let Some(idx) = trimmed.find('#') {
                        let sub = &trimmed[idx + 1..];
                        if let Some(colon) = sub.find(':') {
                            mgr.status.current_id = sub[..colon].trim().parse().unwrap_or(0);
                            mgr.status.current_title = sub[colon + 1..].trim().to_string();
                        }
                    }
                    mgr.status.message = format!("已入库电影: {}", mgr.status.current_title);
                }
                // 2. "+ 新增演员 #567: Name"
                else if trimmed.contains("+ 新增演员 #") {
                    mgr.status.new_performers += 1;
                    if let Some(idx) = trimmed.find('#') {
                        let sub = &trimmed[idx + 1..];
                        if let Some(colon) = sub.find(':') {
                            mgr.status.current_id = sub[..colon].trim().parse().unwrap_or(0);
                            mgr.status.current_title = sub[colon + 1..].trim().to_string();
                        }
                    }
                    mgr.status.message = format!("已入库演员: {}", mgr.status.current_title);
                }
                // 2.5 "+ 新增分集 #890: Title"
                else if trimmed.contains("+ 新增分集 #") {
                    mgr.status.new_episodes += 1;
                    if let Some(idx) = trimmed.find('#') {
                        let sub = &trimmed[idx + 1..];
                        if let Some(colon) = sub.find(':') {
                            mgr.status.current_id = sub[..colon].trim().parse().unwrap_or(0);
                            mgr.status.current_title = sub[colon + 1..].trim().to_string();
                        }
                    }
                    mgr.status.message = format!("已入库分集: {}", mgr.status.current_title);
                }
                // 3. Batch scraper progress: "[Movie] 42/1000 ( 4.2%) | Speed: 12.5 req/s | 200 OK: 40 | 404: 2 | Err: 0 | ETA: 1.3m"
                else if trimmed.contains("req/s") && trimmed.contains('/') {
                    if let Some(slash_idx) = trimmed.find('/') {
                        // find number before slash
                        let before = &trimmed[..slash_idx];
                        if let Some(space_idx) = before.rfind(' ') {
                            mgr.status.processed_count = before[space_idx + 1..].trim().parse().unwrap_or(mgr.status.processed_count);
                        }
                        // find number after slash
                        let after = &trimmed[slash_idx + 1..];
                        if let Some(paren_idx) = after.find(' ') {
                            let total_str = after[..paren_idx].trim();
                            if let Ok(tot) = total_str.parse::<u32>() {
                                mgr.status.target_total = tot;
                            }
                        }
                    }
                    if let Some(pct_idx) = trimmed.find('%') {
                        let before_pct = &trimmed[..pct_idx];
                        if let Some(open_paren) = before_pct.rfind('(') {
                            mgr.status.percent = before_pct[open_paren + 1..].trim().parse().unwrap_or(mgr.status.percent);
                        }
                    }
                    if let Some(speed_idx) = trimmed.find("Speed:") {
                        let after_speed = &trimmed[speed_idx + 6..];
                        if let Some(req_idx) = after_speed.find("req/s") {
                            mgr.status.speed_fps = after_speed[..req_idx].trim().parse().unwrap_or(0.0);
                        }
                    }
                    if let Some(ok_idx) = trimmed.find("200 OK:") {
                        let after_ok = &trimmed[ok_idx + 7..];
                        if let Some(bar_idx) = after_ok.find('|') {
                            let ok_n: u32 = after_ok[..bar_idx].trim().parse().unwrap_or(0);
                            if mgr.status.mode.contains("movies") {
                                mgr.status.new_movies = ok_n;
                            } else if mgr.status.mode.contains("performers") {
                                mgr.status.new_performers = ok_n;
                            }
                        }
                    }
                    if let Some(eta_idx) = trimmed.find("ETA:") {
                        let after_eta = &trimmed[eta_idx + 4..];
                        if let Some(m_idx) = after_eta.find('m') {
                            mgr.status.eta_minutes = after_eta[..m_idx].trim().parse().unwrap_or(0.0);
                        }
                    }
                    mgr.status.message = format!(
                        "正在并发抓取: {}/{} ({:.1}%) - {:.1} 项/秒",
                        mgr.status.processed_count, mgr.status.target_total, mgr.status.percent, mgr.status.speed_fps
                    );
                }
                // 4. "⚡ 需要抓取同步的新增项: 电影 X 项, 演员 Y 项"
                else if trimmed.contains("需要抓取同步的新增项") {
                    mgr.status.message = trimmed.clone();
                }

                if let Some(start) = mgr.start_time {
                    mgr.status.elapsed_secs = start.elapsed().as_secs();
                }

                // Throttle UI events to max 10 per second to keep frontend silky smooth
                if last_emit.elapsed().as_millis() >= 80 {
                    let snapshot = mgr.status.clone();
                    drop(mgr);
                    let _ = app_clone.emit("scraper-progress", &snapshot);
                    last_emit = Instant::now();
                }
            }
        }

        // Collect any stderr if failed
        let mut err_msg = String::new();
        if let Some(mut err_reader) = stderr {
            let _ = err_reader.read_to_string(&mut err_msg);
        }

        let mut mgr = manager().lock().unwrap();
        mgr.status.running = false;
        mgr.status.finished = true;
        mgr.child_pid = None;

        if !err_msg.trim().is_empty() && (mgr.status.new_movies == 0 && mgr.status.new_performers == 0 && mgr.status.new_episodes == 0) {
            mgr.status.error = Some(err_msg.trim().to_string());
            mgr.status.message = format!("执行异常: {}", err_msg.lines().next().unwrap_or(""));
        } else {
            mgr.status.percent = 100.0;
            mgr.status.message = format!(
                "同步刮削完成！本次共整合新增影片 {} 部，新增演员 {} 位，新增分集 {} 个",
                mgr.status.new_movies, mgr.status.new_performers, mgr.status.new_episodes
            );
        }

        let final_status = mgr.status.clone();
        drop(mgr);

        let _ = app_clone.emit("scraper-progress", &final_status);
        let _ = app_clone.emit("scraper-finished", &final_status);
    });

    Ok(initial_status)
}

/// 兼容原有接口 run_sync
#[tauri::command]
pub fn run_sync(app: AppHandle) -> Result<SyncResult, String> {
    let script_path = find_script(Some(&app), "sync_gevi.py")
        .ok_or_else(|| "未找到 sync_gevi.py 脚本".to_string())?;

    let db_path = crate::db::find_db_path()
        .map(|p| p.to_string_lossy().to_string())
        .unwrap_or_else(|| "gevi.db".to_string());

    let python_bin = resolve_python();
    let output = std::process::Command::new(&python_bin)
        .arg(&script_path)
        .arg("--db")
        .arg(&db_path)
        .arg("--probe")
        .arg("30")
        .output()
        .map_err(|e| format!("启动同步脚本失败: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut new_movies = 0;
    let mut new_performers = 0;
    let mut new_episodes = 0;

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
        if line.contains("本次入库新电影:") {
            if let Some(idx) = line.find("本次入库新电影:") {
                let rest = &line[idx + 21..];
                if let Some(m_idx) = rest.find("部") {
                    new_movies = rest[..m_idx].trim().parse().unwrap_or(new_movies);
                }
            }
        }
        if line.contains("新分集:") {
            if let Some(idx) = line.find("新分集:") {
                let rest = &line[idx + 10..];
                if let Some(e_idx) = rest.find("个") {
                    new_episodes = rest[..e_idx].trim().parse().unwrap_or(new_episodes);
                }
            }
        }
    }

    let _ = app.emit("scraper-finished", ());

    Ok(SyncResult {
        new_movies,
        new_performers,
        new_episodes,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_find_scripts() {
        let script = find_script(None, "sync_gevi.py");
        assert!(script.is_some(), "sync_gevi.py must be discovered");
        let batch = find_script(None, "batch_scraper.py");
        assert!(batch.is_some(), "batch_scraper.py must be discovered");
    }

    #[test]
    fn test_read_line_or_cr() {
        let data = b"line 1\rline 2\nline 3\r\n";
        let mut cursor = std::io::Cursor::new(data);
        let mut line = String::new();

        assert!(read_line_or_cr(&mut cursor, &mut line).unwrap());
        assert_eq!(line.trim(), "line 1");

        assert!(read_line_or_cr(&mut cursor, &mut line).unwrap());
        assert_eq!(line.trim(), "line 2");

        assert!(read_line_or_cr(&mut cursor, &mut line).unwrap());
        assert_eq!(line.trim(), "line 3");
    }

    #[test]
    fn test_default_scraper_status() {
        let status = get_scraper_status().unwrap();
        assert!(!status.running);
        assert_eq!(status.mode, "idle");
    }
}

