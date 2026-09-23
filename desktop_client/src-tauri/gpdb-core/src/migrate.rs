//! Schema statements this crate depends on, replayed on connect.
//!
//! # Why this exists
//!
//! Every `SELECT` in `queries/` names `movies.title_zh`. If the file the app opened
//! predates that column, SQLite fails at *prepare* time — `no such column` — and no
//! amount of `COALESCE` or `LEFT JOIN` can rescue a column that does not exist. The
//! app would fail every movie query, which surfaces to the user as a blank library.
//!
//! The schema has always been owned by Python (`schema.sql` + `db_manager.MIGRATIONS`
//! + `apply_migrations`). Asking the user to run Python once does not close this hole:
//! the desktop is meant to work on a machine with no Python at all, and the library it
//! opens is not necessarily one Python has ever seen — `GEVI_DB` can point it anywhere.
//!
//! # The rule for this module
//!
//! **It mirrors, it never decides.** Every statement here already exists verbatim in
//! `schema.sql` or `db_manager.MIGRATIONS`, type strings included — `PRAGMA
//! table_info` reports the declared type back, so a drift here would make any future
//! schema-parity test lie. Adding a column means adding it in both places, and the
//! table definitions must stay readable side by side with `schema.sql` §12.
//!
//! This is the only DDL outside Python, and it is deliberately one small file.

use crate::error::Result;
use rusqlite::Connection;

/// Columns on `movies` that the queries in this crate reference.
///
/// Mirrors `db_manager.MIGRATIONS["movies"]`. Only the ones a *query* needs: the rest
/// of that list is scrape-side bookkeeping this crate never reads.
const MOVIE_COLUMNS: &[(&str, &str)] = &[
    ("title_zh", "TEXT"),
    ("title_attempts", "INTEGER DEFAULT 0"),
];

/// Columns on `performers` added after the initial release.
///
/// Mirrors `db_manager.MIGRATIONS["performers"]`. `image_url` is in CORE_SCHEMA already,
/// so only the newer ones go here. Adding a column means adding it in both places.
const PERFORMER_COLUMNS: &[(&str, &str)] = &[
    ("bftv_url", "TEXT"),
];

/// Tables this crate reads. Verbatim from `schema.sql` §11 and §12.
const TABLES: &str = "
CREATE TABLE IF NOT EXISTS category_glossary (
    term       TEXT PRIMARY KEY,
    zh         TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS directors (
    id INTEGER PRIMARY KEY,
    site_id INTEGER UNIQUE,
    name TEXT NOT NULL UNIQUE,
    works_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS movie_directors (
    movie_id INTEGER NOT NULL,
    director_id INTEGER NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (movie_id, director_id)
);
CREATE INDEX IF NOT EXISTS idx_movie_directors_director ON movie_directors(director_id);
CREATE INDEX IF NOT EXISTS idx_directors_name ON directors(name);
";

const CORE_SCHEMA: &str = "
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;

CREATE TABLE IF NOT EXISTS movies (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    studio_id INTEGER,
    studio_name TEXT,
    release_year INTEGER,
    duration_mins INTEGER,
    category TEXT,
    rating TEXT,
    movie_type TEXT,
    description TEXT,
    cover_icon TEXT,
    cover_full TEXT,
    cover_back TEXT,
    covers_json TEXT,
    director_id INTEGER,
    director_name TEXT,
    description_zh TEXT,
    translation_attempts INTEGER DEFAULT 0,
    title_zh TEXT,
    title_attempts INTEGER DEFAULT 0,
    scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_movies_year ON movies(release_year);
CREATE INDEX IF NOT EXISTS idx_movies_studio ON movies(studio_name);
CREATE INDEX IF NOT EXISTS idx_movies_category ON movies(category);
CREATE INDEX IF NOT EXISTS idx_movies_director ON movies(director_name);

CREATE TABLE IF NOT EXISTS performers (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    hair TEXT,
    eyes TEXT,
    body_hair TEXT,
    facial_hair TEXT,
    height TEXT,
    weight TEXT,
    build TEXT,
    skin TEXT,
    dick_size TEXT,
    foreskin TEXT,
    tattoos TEXT,
    notes TEXT,
    image_url TEXT,
    scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_performers_name ON performers(name);
CREATE INDEX IF NOT EXISTS idx_performers_build ON performers(build);
CREATE INDEX IF NOT EXISTS idx_performers_hair ON performers(hair);
CREATE INDEX IF NOT EXISTS idx_performers_eyes ON performers(eyes);
CREATE INDEX IF NOT EXISTS idx_performers_skin ON performers(skin);
CREATE INDEX IF NOT EXISTS idx_performers_body_hair ON performers(body_hair);
CREATE INDEX IF NOT EXISTS idx_performers_facial_hair ON performers(facial_hair);
CREATE INDEX IF NOT EXISTS idx_performers_image ON performers(image_url);

CREATE TABLE IF NOT EXISTS movie_performers (
    movie_id INTEGER NOT NULL,
    performer_id INTEGER NOT NULL,
    performer_name TEXT,
    PRIMARY KEY (movie_id, performer_id)
);

CREATE INDEX IF NOT EXISTS idx_mp_performer_id ON movie_performers(performer_id);

CREATE TABLE IF NOT EXISTS episodes (
    id INTEGER PRIMARY KEY,
    movie_id INTEGER,
    title TEXT,
    thumbnail_url TEXT,
    description TEXT,
    description_zh TEXT,
    action_notes TEXT,
    release_date TEXT,
    studio_id INTEGER,
    studio_name TEXT
);

CREATE INDEX IF NOT EXISTS idx_episodes_movie_id ON episodes(movie_id);

CREATE TABLE IF NOT EXISTS episode_performers (
    episode_id INTEGER NOT NULL,
    performer_id INTEGER NOT NULL,
    performer_name TEXT,
    PRIMARY KEY (episode_id, performer_id)
);

CREATE TABLE IF NOT EXISTS user_movie_data (
    movie_id INTEGER PRIMARY KEY,
    rating REAL,
    favorite INTEGER DEFAULT 0,
    tags TEXT,
    notes TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    color TEXT DEFAULT '#4F46E5',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS movie_user_tags (
    movie_id INTEGER,
    tag_id INTEGER,
    PRIMARY KEY (movie_id, tag_id)
);

CREATE TABLE IF NOT EXISTS favorites (
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_type, entity_id)
);

CREATE TABLE IF NOT EXISTS series_collections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    root_title TEXT NOT NULL,
    studio_name TEXT,
    movie_count INTEGER NOT NULL DEFAULT 0,
    sample_covers TEXT,
    year_start INTEGER,
    year_end INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(root_title, studio_name)
);
";

/// Initialize an empty database file with full core schema and migrations.
pub fn create_empty_database_schema(conn: &Connection) -> Result<()> {
    conn.execute_batch(CORE_SCHEMA)?;
    ensure_schema(conn)?;
    Ok(())
}

/// Bring an existing database up to what this crate's queries expect. Idempotent.
///
/// Takes `&Connection` and keeps no state, so the caller decides when to skip it; see
/// `db::open_db`, which remembers the paths it has already migrated.
pub fn ensure_schema(conn: &Connection) -> Result<()> {
    // A single `CREATE TABLE IF NOT EXISTS` has no check-then-act window, so this half
    // cannot race the Python migrator.
    conn.execute_batch(TABLES)?;

    // `ALTER TABLE ADD COLUMN` has no `IF NOT EXISTS` in SQLite, so this half *is*
    // check-then-act. IMMEDIATE takes the write lock up front: with a deferred
    // transaction two migrators can both read "column missing" and then collide on the
    // upgrade. (DEFERRED would surface as SQLITE_BUSY, IMMEDIATE just serialises.)
    conn.execute_batch("BEGIN IMMEDIATE")?;
    let outcome = add_missing_movie_columns(conn).and_then(|()| add_missing_performer_columns(conn));
    match outcome {
        Ok(()) => {
            conn.execute_batch("COMMIT")?;
            Ok(())
        }
        Err(e) => {
            // Best effort: the connection is about to be discarded by the caller anyway,
            // and the original error is the one worth reporting.
            let _ = conn.execute_batch("ROLLBACK");
            Err(e)
        }
    }
}

fn add_missing_movie_columns(conn: &Connection) -> Result<()> {
    let existing: Vec<String> = {
        let mut stmt = conn.prepare("PRAGMA table_info(movies)")?;
        let rows = stmt.query_map([], |r| r.get::<_, String>(1))?;
        rows.collect::<rusqlite::Result<Vec<_>>>()?
    };

    // No `movies` table means a fresh or empty file. `schema.sql` creates the whole
    // schema; creating it from here would be this module making a schema decision.
    // Mirrors the same guard in `db_manager.apply_migrations`.
    if existing.is_empty() {
        return Ok(());
    }

    for (name, ty) in MOVIE_COLUMNS {
        if existing.iter().any(|c| c == name) {
            continue;
        }
        add_column_tolerating_race(conn, name, ty)?;
    }
    Ok(())
}

/// `ALTER TABLE movies ADD COLUMN`, tolerating the column having appeared since the
/// caller checked for it.
///
/// Split out from the loop above so the tolerance is reachable from a test. It is not
/// reachable through `ensure_schema`: that function reads the column list first and
/// skips anything already present, so a second call never gets as far as the ALTER.
/// The only way to hit this branch is genuinely concurrent migrators —
/// `db_manager.apply_migrations` in the scraper against this one — which no
/// single-connection test can reproduce. Calling it directly is what makes the
/// difference between a tolerance that works and one that is merely written down.
fn add_column_tolerating_race(conn: &Connection, name: &str, ty: &str) -> Result<()> {
    let sql = format!("ALTER TABLE movies ADD COLUMN {name} {ty}");
    match conn.execute(&sql, []) {
        Ok(_) => Ok(()),
        // rusqlite reports this as a generic SqliteFailure, so the message is the only
        // signal available for "someone else won the race".
        Err(e) if e.to_string().contains("duplicate column name") => Ok(()),
        Err(e) => Err(e.into()),
    }
}

fn add_column_to_table_tolerating_race(conn: &Connection, table: &str, name: &str, ty: &str) -> Result<()> {
    let sql = format!("ALTER TABLE {table} ADD COLUMN {name} {ty}");
    match conn.execute(&sql, []) {
        Ok(_) => Ok(()),
        Err(e) if e.to_string().contains("duplicate column name") => Ok(()),
        Err(e) => Err(e.into()),
    }
}

fn add_missing_performer_columns(conn: &Connection) -> Result<()> {
    let existing: Vec<String> = {
        let mut stmt = conn.prepare("PRAGMA table_info(performers)")?;
        let rows = stmt.query_map([], |r| r.get::<_, String>(1))?;
        rows.collect::<rusqlite::Result<Vec<_>>>()?
    };

    // No `performers` table means a fresh or empty file — skip until Python creates it.
    if existing.is_empty() {
        return Ok(());
    }

    for (name, ty) in PERFORMER_COLUMNS {
        if existing.iter().any(|c| c == name) {
            continue;
        }
        add_column_to_table_tolerating_race(conn, "performers", name, ty)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The `movies` table as it looked before this change — no title_zh.
    const OLD_MOVIES: &str = "CREATE TABLE movies (
        id INTEGER PRIMARY KEY,
        title TEXT NOT NULL,
        description_zh TEXT,
        translation_attempts INTEGER DEFAULT 0
    )";

    fn column_names(conn: &Connection, table: &str) -> Vec<String> {
        let mut stmt = conn.prepare(&format!("PRAGMA table_info({table})")).unwrap();
        let rows = stmt.query_map([], |r| r.get::<_, String>(1)).unwrap();
        rows.collect::<rusqlite::Result<Vec<_>>>().unwrap()
    }

    #[test]
    fn adds_the_title_columns_to_an_old_database() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(OLD_MOVIES).unwrap();

        ensure_schema(&conn).unwrap();

        let cols = column_names(&conn, "movies");
        assert!(cols.contains(&"title_zh".to_string()));
        assert!(cols.contains(&"title_attempts".to_string()));
    }

    #[test]
    fn is_idempotent() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(OLD_MOVIES).unwrap();

        ensure_schema(&conn).unwrap();
        // Without the duplicate-column tolerance this second call is the one that fails,
        // and it fails for real users: open_db runs on every command.
        ensure_schema(&conn).unwrap();
        ensure_schema(&conn).unwrap();

        assert_eq!(
            column_names(&conn, "movies")
                .iter()
                .filter(|c| *c == "title_zh")
                .count(),
            1,
            "the column must be added exactly once"
        );
    }

    #[test]
    fn tolerates_a_column_that_appeared_since_the_check() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(OLD_MOVIES).unwrap();
        ensure_schema(&conn).unwrap();

        // This is precisely what the loser of a cross-process race sees: it decided the
        // column was missing, and by the time it ran the ALTER the other migrator had
        // added it. Without the tolerance in `add_column_tolerating_race`, opening the
        // app while the scraper is running would fail here.
        add_column_tolerating_race(&conn, "title_zh", "TEXT").unwrap();
        add_column_tolerating_race(&conn, "title_zh", "TEXT").unwrap();
    }

    #[test]
    fn the_race_tolerance_does_not_swallow_real_failures() {
        let conn = Connection::open_in_memory().unwrap();
        // No `movies` table at all. The tolerance matches on the message, so a bug that
        // widened it to "ignore every error" would hide exactly the failures worth
        // seeing - and this module exists to stop a bad database becoming a blank app.
        let err = add_column_tolerating_race(&conn, "title_zh", "TEXT")
            .expect_err("a missing table is not a lost race");
        assert!(
            err.to_string().contains("no such table"),
            "unexpected error: {err}"
        );
    }

    #[test]
    fn creates_the_category_glossary_and_migrates_on_the_same_pass() {        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(OLD_MOVIES).unwrap();

        ensure_schema(&conn).unwrap();

        // The table half must not depend on the column half having work to do.
        ensure_schema(&conn).unwrap();
        assert_eq!(
            column_names(&conn, "category_glossary"),
            vec!["term", "zh", "updated_at"]
        );
    }

    #[test]
    fn leaves_a_fresh_file_alone() {
        let conn = Connection::open_in_memory().unwrap();

        ensure_schema(&conn).unwrap();

        // No `movies` table appeared: `schema.sql` owns creation, not this module.
        // Creating an empty one here would be worse than an error, because the app
        // would then claim a valid library with zero films in it.
        assert!(column_names(&conn, "movies").is_empty());
        assert_eq!(column_names(&conn, "category_glossary").len(), 3);
    }

    #[test]
    fn preserves_existing_rows_and_their_translations() {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(OLD_MOVIES).unwrap();
        conn.execute(
            "INSERT INTO movies (id, title, description_zh) VALUES (1, 'Blow Me, Boy', '吹我，小子')",
            [],
        )
        .unwrap();

        ensure_schema(&conn).unwrap();

        let (title, zh, tzh): (String, String, Option<String>) = conn
            .query_row("SELECT title, description_zh, title_zh FROM movies WHERE id = 1", [], |r| {
                Ok((r.get(0)?, r.get(1)?, r.get(2)?))
            })
            .unwrap();
        assert_eq!(title, "Blow Me, Boy");
        assert_eq!(zh, "吹我，小子", "the synopsis must survive an ALTER");
        assert_eq!(tzh, None, "a newly added column starts empty, not stale");
    }

    #[test]
    fn the_declared_types_match_what_python_writes() {
        // `PRAGMA table_info` echoes the declared type. The desktop and `db_manager`
        // migrate the same file, so a divergence here would mean the two disagree about
        // the schema they both claim to mirror.
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(OLD_MOVIES).unwrap();
        ensure_schema(&conn).unwrap();

        let mut stmt = conn.prepare("PRAGMA table_info(movies)").unwrap();
        let types: Vec<(String, String)> = stmt
            .query_map([], |r| Ok((r.get::<_, String>(1)?, r.get::<_, String>(2)?)))
            .unwrap()
            .collect::<rusqlite::Result<Vec<_>>>()
            .unwrap();

        let get = |name: &str| {
            types
                .iter()
                .find(|(c, _)| c == name)
                .map(|(_, t)| t.clone())
                .unwrap_or_default()
        };
        assert_eq!(get("title_zh"), "TEXT");
        assert_eq!(get("title_attempts"), "INTEGER");
    }
}
