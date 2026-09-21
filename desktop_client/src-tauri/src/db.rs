//! Where the database is, and how to open it.
//!
//! The failure this module is shaped around: `Connection::open` creates the file it is
//! given. So a search that comes up empty does not produce an error — it produces a
//! brand new empty database, and the app comes up showing an empty library, which
//! reads as "the data is gone" rather than "I looked in the wrong place". Both halves
//! of the fix are here: search somewhere that actually works when bundled, and never
//! open for creation.
//!
//! Pointing the app at a library elsewhere is `GEVI_DB`, the same variable the Python
//! side and the parity tests read.

use rusqlite::{Connection, OpenFlags};
use std::collections::HashSet;
use std::path::{Path, PathBuf};
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

/// The database, or `None` if it is nowhere we know how to look.
///
/// The order matters, and it is the fix for a bug that presented as data loss. This
/// used to be three working-directory-relative paths and nothing else. That works
/// under `tauri dev` — the binary runs from `src-tauri/`, so the library is two levels
/// up — and fails for a bundled `.app`: Finder launches one with `/` as the working
/// directory, where none of the three resolve. What turned a lookup miss into "the
/// database is empty" is that the old version returned `../gevi.db` anyway, so
/// `Connection::open` created it. See `open_db`.
pub fn find_db_path() -> Option<PathBuf> {
    // An explicit path wins outright, and is not second-guessed. If `GEVI_DB` points at
    // nothing, that is an error naming the path the user asked for — not a hint to go
    // looking elsewhere, because quietly opening a *different* library than the one
    // asked for is worse than failing: the data on screen would be wrong in a way
    // nothing on screen reveals. (Same variable the Python side and the parity tests
    // read. An empty value counts as unset.) It is not a complete answer on its own —
    // a Finder launch gets no environment at all — hence what follows.
    if let Some(raw) = std::env::var_os("GEVI_DB") {
        if !raw.is_empty() {
            return Some(PathBuf::from(raw));
        }
    }

    // Relative to the working directory. This is the `tauri dev` case, and it stays
    // first among the automatic candidates because it is the one a developer expects
    // to win when they have deliberately started the app somewhere.
    for candidate in ["gevi.db", "../gevi.db", "../../gevi.db"] {
        let p = PathBuf::from(candidate);
        if p.is_file() {
            return Some(p);
        }
    }

    // Beside the executable, then each directory above it. This is what makes the
    // bundled `.app` work while it still sits in the build tree: with a working
    // directory of `/`, its own location is the only thing pointing back into the
    // project.
    let exe = std::env::current_exe().ok()?;
    find_beside_exe(&exe)
}

/// Walk from the executable's directory upward looking for `gevi.db`.
///
/// Split out from `find_db_path` so the rule that actually matters — *how far* up it
/// goes — can be tested. The real layout only exercises it when the app is genuinely
/// bundled, which is precisely the case that was broken, so a test that had to build a
/// `.app` to reach this would not have been written. It takes the executable path
/// rather than reading `current_exe()` for the same reason.
///
/// Upward rather than one level, because the bundled binary sits at
/// `.app/Contents/MacOS/gpdb` and the library is around ten levels further up.
fn find_beside_exe(exe: &Path) -> Option<PathBuf> {
    for dir in exe.parent()?.ancestors() {
        let p = dir.join("gevi.db");
        if p.is_file() {
            return Some(p);
        }
    }
    None
}

/// `find_db_path`, or an error saying what to do about it.
///
/// This reaches the user through the window (see `loadError` in App.vue), so it names
/// fixes that work rather than describing the search it just did.
pub fn db_path() -> Result<PathBuf, String> {
    find_db_path().ok_or_else(|| {
        "找不到 gevi.db。\n\
         请用 GEVI_DB=/完整/路径/gevi.db 指定资料库的位置，\
         或把 gevi.db 放到 GPDb.app 旁边。"
            .to_string()
    })
}

/// Open an *existing* database read-write, refusing to create one.
///
/// `Connection::open` is this plus `SQLITE_OPEN_CREATE`, and that one flag is the whole
/// bug: handed a path that does not exist it makes an empty database, `ensure_schema`
/// fills in empty tables, and the app shows a working library with nothing in it. An
/// empty library is indistinguishable from a lost one, so the failure has to be an
/// error instead. The schema is Python's to create; the desktop never has a reason to.
fn open_existing(path: &Path) -> rusqlite::Result<Connection> {
    Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_WRITE)
}

pub fn open_db() -> Result<Connection, String> {
    let path = db_path()?;
    let conn = open_existing(&path).map_err(|e| format!("打不开数据库 {:?}: {}", path, e))?;
    // The 5s busy timeout matters for writes: the scraper and the local server hold
    // this same file, so without it a favorite toggled mid-scrape would fail
    // immediately with SQLITE_BUSY instead of waiting for the writer to finish.
    let _ = conn.execute_batch(
        "PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL; PRAGMA busy_timeout = 5000;",
    );

    // The queries in `gpdb_core` name columns this file may predate; SQLite fails at
    // prepare time for a missing column, so the whole library would come up empty. See
    // `gpdb_core::migrate` for why the desktop has to do this itself.
    //
    // Done once per process per database, not per command: `open_db` is called on every
    // command, and this takes a write lock. The path is recorded only on success, so a
    // transient failure (the scraper holding the write lock) is retried by the next
    // command rather than latching the app into a broken state.
    let key = std::fs::canonicalize(&path).unwrap_or_else(|_| path.clone());
    let needs_migration = !migrated_paths().lock().unwrap().contains(&key);
    if needs_migration {
        gpdb_core::migrate::ensure_schema(&conn).map_err(|e| {
            format!("Failed to upgrade the schema of {:?}: {}", path, e)
        })?;
        migrated_paths().lock().unwrap().insert(key);
    }

    Ok(conn)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A scratch tree, removed on drop.
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

    /// The bundled layout, reproduced. This is the shape that was broken: a `.app`
    /// nested inside the build tree, launched by Finder with `/` as the working
    /// directory, so the executable's own location is the only thing that can lead back
    /// to the library ten levels up.
    #[test]
    fn finds_the_library_up_from_a_bundled_app() {
        let scratch = Scratch::new("bundle");
        let project = scratch.0.join("proj");
        let lib = project.join("gevi.db");
        let exe = project.join(
            "desktop_client/src-tauri/target/release/bundle/macos/GPDb.app/Contents/MacOS/gpdb",
        );
        std::fs::create_dir_all(exe.parent().unwrap()).expect("create bundle tree");
        std::fs::write(&lib, b"").expect("write db");

        assert_eq!(find_beside_exe(&exe), Some(lib));
    }

    /// The nearest `gevi.db` wins, so an app that ships one inside the bundle is not
    /// hijacked by an unrelated one further up.
    #[test]
    fn prefers_the_closest_one() {
        let scratch = Scratch::new("closest");
        let project = scratch.0.join("proj");
        let exe = project.join(
            "desktop_client/src-tauri/target/release/bundle/macos/GPDb.app/Contents/MacOS/gpdb",
        );
        std::fs::create_dir_all(exe.parent().unwrap()).expect("create bundle tree");
        let outer = project.join("gevi.db");
        let inner = exe.parent().unwrap().join("gevi.db");
        std::fs::write(&outer, b"").expect("write outer");
        std::fs::write(&inner, b"").expect("write inner");

        assert_eq!(find_beside_exe(&exe), Some(inner));
    }

    /// Nothing found means `None`, which is what makes `open_db` refuse instead of
    /// creating an empty library. Asserting on the *result* rather than `is_none()`
    /// because this walks all the way to `/`, and a stray `gevi.db` up there is not
    /// this test's business.
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

    /// The refusal itself. Without `SQLITE_OPEN_CREATE` this errors; with it, it would
    /// silently produce the empty database that started all of this.
    #[test]
    fn opening_a_missing_database_creates_nothing() {
        let scratch = Scratch::new("refuse");
        let missing = scratch.0.join("nope.db");

        assert!(open_existing(&missing).is_err(), "开一个不存在的库应该失败");
        assert!(!missing.exists(), "失败的打开不能留下文件");
    }
}
