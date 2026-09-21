//! Where the database is, and how to open it.
//!
//! Unchanged by the phase-2 split. Phase 3 replaces `find_db_path` with a config
//! file the user can point anywhere, and makes `open_db` fail rather than create an
//! empty database when the path is wrong.

use rusqlite::Connection;
use std::path::PathBuf;

pub fn find_db_path() -> PathBuf {
    let candidates = [
        PathBuf::from("gevi.db"),
        PathBuf::from("../gevi.db"),
        PathBuf::from("../../gevi.db"),
    ];
    for p in &candidates {
        if p.exists() {
            return p.clone();
        }
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            let p = dir.join("gevi.db");
            if p.exists() {
                return p;
            }
        }
    }
    PathBuf::from("../gevi.db")
}

pub fn open_db() -> Result<Connection, String> {
    let path = find_db_path();
    let conn = Connection::open(&path).map_err(|e| format!("Failed to open DB at {:?}: {}", path, e))?;
    // The 5s busy timeout matters for writes: the scraper and the local server hold
    // this same file, so without it a favorite toggled mid-scrape would fail
    // immediately with SQLITE_BUSY instead of waiting for the writer to finish.
    let _ = conn.execute_batch(
        "PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL; PRAGMA busy_timeout = 5000;",
    );
    Ok(conn)
}
