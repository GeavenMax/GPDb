//! Where the database is, and how to open it.
//!
//! The failure this module is shaped around: `Connection::open` creates the file it is
//! given. So a search that comes up empty does not produce an error — it produces a
//! brand new empty database, and the app comes up showing an empty library, which
//! reads as "the data is gone" rather than "I looked in the wrong place". Both halves
//! of the fix are here: search somewhere that actually works when bundled, and never
//! open for creation.
//!
//! Pointing the app at a library elsewhere is `GPDB_DB`, the same variable the Python
//! side and the parity tests read, or custom configuration saved in user app support.

use rusqlite::{Connection, OpenFlags};
use std::collections::HashSet;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};

/// Configuration stored in user's Application Support directory.
#[derive(serde::Serialize, serde::Deserialize, Default, Clone, Debug)]
pub struct DbConfig {
    pub custom_db_path: Option<String>,
}

pub fn config_file_path() -> Option<PathBuf> {
    #[cfg(target_os = "windows")]
    {
        dirs::config_dir()
            .map(|p| p.join("com.gpdb.app").join("db_config.json"))
            .or_else(|| dirs::data_dir().map(|p| p.join("com.gpdb.app").join("db_config.json")))
    }
    #[cfg(not(target_os = "windows"))]
    {
        std::env::var_os("HOME").map(|h| {
            PathBuf::from(h)
                .join("Library")
                .join("Application Support")
                .join("com.gpdb.app")
                .join("db_config.json")
        })
    }
}

pub fn load_db_config() -> DbConfig {
    if let Some(p) = config_file_path() {
        if let Ok(bytes) = std::fs::read(p) {
            if let Ok(cfg) = serde_json::from_slice::<DbConfig>(&bytes) {
                return cfg;
            }
        }
    }
    DbConfig::default()
}

pub fn save_db_config(cfg: &DbConfig) -> Result<(), String> {
    if let Some(p) = config_file_path() {
        if let Some(parent) = p.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let data = serde_json::to_vec_pretty(cfg).map_err(|e| e.to_string())?;
        std::fs::write(&p, data).map_err(|e| e.to_string())?;
    }
    // Also save a ~/.gpdb_db_path convenience text file so any script/shell can easily read it
    if let Some(ref path_str) = cfg.custom_db_path {
        if let Some(home) = dirs::home_dir().or_else(|| std::env::var_os("HOME").map(PathBuf::from)) {
            let dotfile = home.join(".gpdb_db_path");
            let _ = std::fs::write(dotfile, path_str);
        }
    }
    Ok(())
}

pub fn expand_tilde<P: AsRef<Path>>(path: P) -> PathBuf {
    let p = path.as_ref();
    if let Ok(stripped) = p.strip_prefix("~") {
        if let Some(home) = dirs::home_dir().or_else(|| std::env::var_os("HOME").map(PathBuf::from)) {
            return home.join(stripped);
        }
    }
    p.to_path_buf()
}

pub fn is_valid_sqlite_db(path: &Path) -> bool {
    if !path.is_file() {
        return false;
    }
    if let Ok(mut f) = std::fs::File::open(path) {
        let mut header = [0u8; 16];
        if f.read_exact(&mut header).is_ok() {
            return &header == b"SQLite format 3\0";
        }
    }
    false
}

pub fn is_valid_gpdb_db(path: &Path) -> bool {
    if !is_valid_sqlite_db(path) {
        return false;
    }
    match Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY) {
        Ok(conn) => {
            let mut stmt = match conn.prepare(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='movies'",
            ) {
                Ok(s) => s,
                Err(_) => return false,
            };
            stmt.exists([]).unwrap_or(false)
        }
        Err(_) => false,
    }
}

/// Scan candidate databases using Spotlight mdfind on macOS, or bounded directory traversal on Windows / other OS.
pub fn scan_candidate_databases() -> Vec<PathBuf> {
    let mut candidates = Vec::new();
    let mut seen = HashSet::new();

    let mut add = |p: PathBuf| {
        let key = std::fs::canonicalize(&p).unwrap_or_else(|_| p.clone());
        if seen.insert(key.clone()) && is_valid_gpdb_db(&key) {
            candidates.push(key);
        }
    };

    // 1. Check Spotlight via mdfind (macOS specific, < 0.05s)
    #[cfg(target_os = "macos")]
    {
        if let Ok(output) = std::process::Command::new("mdfind")
            .args(["kMDItemFSName == 'GPDb.db'"])
            .output()
        {
            if output.status.success() {
                let text = String::from_utf8_lossy(&output.stdout);
                for line in text.lines() {
                    let trimmed = line.trim();
                    if !trimmed.is_empty() {
                        add(PathBuf::from(trimmed));
                    }
                }
            }
        }
    }

    // 2. Check well-known user folders across platforms
    let mut probe_roots: Vec<PathBuf> = Vec::new();

    if let Some(doc) = dirs::document_dir() {
        probe_roots.push(doc.clone());
        probe_roots.push(doc.join("GPDb"));
    }
    if let Some(desk) = dirs::desktop_dir() {
        probe_roots.push(desk);
    }
    if let Some(dl) = dirs::download_dir() {
        probe_roots.push(dl);
    }
    if let Some(home) = dirs::home_dir() {
        probe_roots.push(home.join("iCloud Drive (Archive)").join("Documents"));
        probe_roots.push(home.join("Library/Mobile Documents/com~apple~CloudDocs"));
    }

    // Traverse probe roots with depth <= 3
    for root in probe_roots {
        if !root.is_dir() {
            continue;
        }
        for entry in walkdir::WalkDir::new(&root)
            .max_depth(3)
            .into_iter()
            .filter_entry(|e| {
                let name = e.file_name().to_string_lossy();
                // Skip hidden folders, node_modules, target, .git
                !name.starts_with('.') && name != "node_modules" && name != "target"
            })
            .filter_map(|e| e.ok())
        {
            let path = entry.path();
            if path.is_file() && entry.file_name() == "GPDb.db" {
                add(path.to_path_buf());
            }
        }
    }

    candidates
}

/// Paths already migrated by this process.
///
/// Keyed by path rather than a bare `OnceLock<()>` because phase 3 lets the user point
/// the app at a different database at runtime; a process-wide flag would then leave the
/// second database unmigrated and every query against it failing.
fn migrated_paths() -> &'static Mutex<HashSet<PathBuf>> {
    static MIGRATED: OnceLock<Mutex<HashSet<PathBuf>>> = OnceLock::new();
    MIGRATED.get_or_init(|| Mutex::new(HashSet::new()))
}

/// The database, or `None` if it is nowhere we know how to look.
pub fn find_db_path() -> Option<PathBuf> {
    // 1. Explicit GPDB_DB env var wins outright
    if let Some(raw) = std::env::var_os("GPDB_DB") {
        if !raw.is_empty() {
            let p = expand_tilde(PathBuf::from(raw));
            if p.is_file() {
                return Some(p);
            }
        }
    }

    // 2. User configured custom path in db_config.json
    let cfg = load_db_config();
    if let Some(custom) = cfg.custom_db_path {
        let p = expand_tilde(PathBuf::from(custom));
        if is_valid_gpdb_db(&p) {
            return Some(p);
        }
    }

    // 3. Relative to the working directory (e.g. tauri dev)
    for candidate in ["GPDb.db", "../GPDb.db", "../../GPDb.db"] {
        let p = PathBuf::from(candidate);
        if p.is_file() {
            return Some(p);
        }
    }

    // 4. Beside the executable, then each directory above it.
    if let Ok(exe) = std::env::current_exe() {
        if let Some(p) = find_beside_exe(&exe) {
            if p.is_file() {
                return Some(p);
            }
        }
    }

    // 5. Check Spotlight index via mdfind (macOS specific, fast, no folder TCC prompt)
    #[cfg(target_os = "macos")]
    {
        if let Ok(output) = std::process::Command::new("mdfind")
            .args(["kMDItemFSName == 'GPDb.db'"])
            .output()
        {
            if output.status.success() {
                let text = String::from_utf8_lossy(&output.stdout);
                for line in text.lines() {
                    let trimmed = line.trim();
                    if !trimmed.is_empty() {
                        let p = PathBuf::from(trimmed);
                        if is_valid_gpdb_db(&p) {
                            let mut cfg = load_db_config();
                            cfg.custom_db_path = Some(p.to_string_lossy().to_string());
                            let _ = save_db_config(&cfg);
                            return Some(p);
                        }
                    }
                }
            }
        }
    }

    // 6. Fast fallback for Windows: check ~/Documents/GPDb/GPDb.db or ~/Documents/GPDb.db
    #[cfg(target_os = "windows")]
    {
        if let Some(doc) = dirs::document_dir() {
            let p1 = doc.join("GPDb").join("GPDb.db");
            if is_valid_gpdb_db(&p1) {
                return Some(p1);
            }
            let p2 = doc.join("GPDb.db");
            if is_valid_gpdb_db(&p2) {
                return Some(p2);
            }
        }
    }

    None
}

/// Walk from the executable's directory upward looking for `GPDb.db`.
fn find_beside_exe(exe: &Path) -> Option<PathBuf> {
    for dir in exe.parent()?.ancestors() {
        let p = dir.join("GPDb.db");
        if p.is_file() {
            return Some(p);
        }
    }
    None
}

/// `find_db_path`, or an error saying what to do about it.
pub fn db_path() -> Result<PathBuf, String> {
    find_db_path().ok_or_else(|| {
        "找不到 GPDb.db。\n\
         请在「缓存与设置 → 本地离线数据中心」中指定数据库路径，\
         或使用「智能扫描」自动定位。"
            .to_string()
    })
}

/// Open an *existing* database read-write, refusing to create one.
fn open_existing(path: &Path) -> rusqlite::Result<Connection> {
    Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_WRITE)
}

pub fn open_db() -> Result<Connection, String> {
    let path = db_path()?;
    let conn = open_existing(&path).map_err(|e| format!("打不开数据库 {:?}: {}", path, e))?;
    let _ = conn.execute_batch(
        "PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL; PRAGMA busy_timeout = 5000;",
    );

    let key = std::fs::canonicalize(&path).unwrap_or_else(|_| path.clone());
    let key_str = key.to_string_lossy().to_string();

    // Ensure client config and ~/.gpdb_db_path reflect the currently active database path
    let mut cfg = load_db_config();
    if cfg.custom_db_path.as_deref() != Some(&key_str) {
        cfg.custom_db_path = Some(key_str);
        let _ = save_db_config(&cfg);
    }

    let needs_migration = !migrated_paths().lock().unwrap().contains(&key);
    if needs_migration {
        gpdb_core::migrate::ensure_schema(&conn).map_err(|e| {
            format!("Failed to upgrade the schema of {:?}: {}", path, e)
        })?;
        let _ = gpdb_core::queries::series::ensure_series_index(&conn);
        migrated_paths().lock().unwrap().insert(key);
    }

    Ok(conn)
}

#[cfg(test)]
mod tests {
    use super::*;

    struct Scratch(PathBuf);

    impl Scratch {
        fn new(tag: &str) -> Self {
            let dir = std::env::temp_dir().join(format!("gpdb_db_{tag}_{}", std::process::id()));
            let _ = std::fs::remove_dir_all(&dir);
            std::fs::create_dir_all(&dir).expect("create scratch dir");
            Self(dir)
        }
    }

    impl Drop for Scratch {
        fn drop(&mut self) {
            let _ = std::fs::remove_dir_all(&self.0);
        }
    }

    #[test]
    fn finds_the_library_up_from_a_bundled_app() {
        let scratch = Scratch::new("bundle");
        let project = scratch.0.join("proj");
        let lib = project.join("GPDb.db");
        let exe = project.join(
            "desktop_client/src-tauri/target/release/bundle/macos/GPDb.app/Contents/MacOS/gpdb",
        );
        std::fs::create_dir_all(exe.parent().unwrap()).expect("create bundle tree");
        std::fs::write(&lib, b"").expect("write db");

        assert_eq!(find_beside_exe(&exe), Some(lib));
    }

    #[test]
    fn prefers_the_closest_one() {
        let scratch = Scratch::new("closest");
        let project = scratch.0.join("proj");
        let exe = project.join(
            "desktop_client/src-tauri/target/release/bundle/macos/GPDb.app/Contents/MacOS/gpdb",
        );
        std::fs::create_dir_all(exe.parent().unwrap()).expect("create bundle tree");
        let outer = project.join("GPDb.db");
        let inner = exe.parent().unwrap().join("GPDb.db");
        std::fs::write(&outer, b"").expect("write outer");
        std::fs::write(&inner, b"").expect("write inner");

        assert_eq!(find_beside_exe(&exe), Some(inner));
    }

    #[test]
    fn does_not_invent_a_path_when_there_is_none() {
        let scratch = Scratch::new("absent");
        let exe = scratch.0.join("deep/a/b/c/GPDb.app/Contents/MacOS/gpdb");
        std::fs::create_dir_all(exe.parent().unwrap()).expect("create tree");

        let found = find_beside_exe(&exe);
        assert!(
            !found.as_ref().is_some_and(|p| p.starts_with(&scratch.0)),
            "invented {found:?} inside the scratch tree"
        );
    }

    #[test]
    fn opening_a_missing_database_creates_nothing() {
        let scratch = Scratch::new("refuse");
        let missing = scratch.0.join("nope.db");

        assert!(open_existing(&missing).is_err(), "开一个不存在的库应该失败");
        assert!(!missing.exists(), "失败的打开不能留下文件");
    }

    #[test]
    fn tilde_expansion_replaces_home() {
        if let Some(home) = std::env::var_os("HOME") {
            let expanded = expand_tilde("~/test/GPDb.db");
            assert_eq!(expanded, PathBuf::from(home).join("test/GPDb.db"));
        }
    }

    #[test]
    fn validates_sqlite_magic_header() {
        let scratch = Scratch::new("magic");
        let non_sqlite = scratch.0.join("bad.db");
        std::fs::write(&non_sqlite, b"hello world not sqlite").unwrap();
        assert!(!is_valid_sqlite_db(&non_sqlite));

        let fake_sqlite = scratch.0.join("good_header.db");
        std::fs::write(&fake_sqlite, b"SQLite format 3\0extra_padding_bytes").unwrap();
        assert!(is_valid_sqlite_db(&fake_sqlite));
    }

    #[test]
    fn validates_gpdb_schema_requires_movies_table() {
        let scratch = Scratch::new("schema_check");
        let empty_db = scratch.0.join("empty.db");
        {
            let conn = Connection::open(&empty_db).unwrap();
            conn.execute("CREATE TABLE other (id INTEGER PRIMARY KEY);", []).unwrap();
        }
        assert!(!is_valid_gpdb_db(&empty_db));

        let valid_db = scratch.0.join("valid.db");
        {
            let conn = Connection::open(&valid_db).unwrap();
            conn.execute("CREATE TABLE movies (id INTEGER PRIMARY KEY, title TEXT);", []).unwrap();
        }
        assert!(is_valid_gpdb_db(&valid_db));
    }
}
