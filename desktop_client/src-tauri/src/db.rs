//! Where the database is, and how to open it.
//!
//! Unchanged by the phase-2 split. Phase 3 replaces `find_db_path` with a config
//! file the user can point anywhere, and makes `open_db` fail rather than create an
//! empty database when the path is wrong.

use rusqlite::Connection;
use std::collections::HashSet;
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};

/// Paths already migrated by this process.
///
/// Keyed by path rather than a bare `OnceLock<()>` because phase 3 lets the user point
/// the app at a different database at runtime; a process-wide flag would then leave the
/// second database unmigrated and every query against it failing.
fn migrated_paths() -> &'static Mutex<HashSet<PathBuf>> {
    static MIGRATED: OnceLock<Mutex<HashSet<PathBuf>>> = OnceLock::new();
    MIGRATED.get_or_init(|| Mutex::new(HashSet::new()))
}

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

    // The queries in `gevi_core` name columns this file may predate; SQLite fails at
    // prepare time for a missing column, so the whole library would come up empty. See
    // `gevi_core::migrate` for why the desktop has to do this itself.
    //
    // Done once per process per database, not per command: `open_db` is called on every
    // command, and this takes a write lock. The path is recorded only on success, so a
    // transient failure (the scraper holding the write lock) is retried by the next
    // command rather than latching the app into a broken state.
    let key = std::fs::canonicalize(&path).unwrap_or_else(|_| path.clone());
    let needs_migration = !migrated_paths().lock().unwrap().contains(&key);
    if needs_migration {
        gevi_core::migrate::ensure_schema(&conn).map_err(|e| {
            format!("Failed to upgrade the schema of {:?}: {}", path, e)
        })?;
        migrated_paths().lock().unwrap().insert(key);
    }

    Ok(conn)
}
