//! Does the Rust migration actually work against the real schema?
//!
//! `migrate.rs`'s own tests run on a two-column toy `movies` table. That proves the
//! ALTER logic, but not that it survives contact with `schema.sql` as it really is —
//! `movies_fts`, the four indexes over `movies`, the `cover_back`/`covers_json`
//! columns. So this one builds a database from the real `schema.sql`, strips the new
//! columns back out to simulate a library that predates them, and migrates it.
//!
//! It does not copy the 244 MB `gevi.db`: replaying `schema.sql` into a temp file
//! produces the same shape in milliseconds, and a test that heavy would not get run.
//! The real file is never opened, let alone written.

use std::path::{Path, PathBuf};
use std::process::Command;

use gpdb_core::migrate;
use rusqlite::Connection;

/// Walk up to the repo root, the way `parity.rs` finds `translate.py`.
fn repo_root() -> Option<PathBuf> {
    let mut dir = Path::new(env!("CARGO_MANIFEST_DIR"));
    for _ in 0..5 {
        if dir.join("schema.sql").exists() {
            return Some(dir.to_path_buf());
        }
        dir = dir.parent()?;
    }
    None
}

fn columns(conn: &Connection, table: &str) -> Vec<String> {
    let mut stmt = conn.prepare(&format!("PRAGMA table_info({table})")).unwrap();
    let rows = stmt.query_map([], |r| r.get::<_, String>(1)).unwrap();
    rows.collect::<rusqlite::Result<Vec<_>>>().unwrap()
}

/// A database built from the real schema, minus the two new columns, with one row.
fn pre_change_database(schema: &str) -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    conn.execute_batch(schema).unwrap();

    // Strip the change back out. SQLite cannot drop a column that an index or a
    // virtual table references, but nothing references these two.
    for col in ["title_zh", "title_attempts"] {
        if columns(&conn, "movies").iter().any(|c| c == col) {
            conn.execute_batch(&format!("ALTER TABLE movies DROP COLUMN {col}"))
                .unwrap();
        }
    }
    conn.execute_batch("DROP TABLE IF EXISTS category_glossary").unwrap();

    conn.execute(
        "INSERT INTO movies (id, title, description, description_zh) \
         VALUES (1, 'Nutt Crackers', 'the original synopsis', '原简介')",
        [],
    )
    .unwrap();
    conn
}

#[test]
fn migrates_a_database_built_from_the_real_schema() {
    let Some(root) = repo_root() else {
        eprintln!("skipping: schema.sql not found; this crate is outside the repo");
        return;
    };
    let schema = std::fs::read_to_string(root.join("schema.sql")).unwrap();
    let conn = pre_change_database(&schema);

    // Guard against the test passing vacuously: if `schema.sql` ever stops declaring
    // the columns, the DROP above does nothing and the migration has nothing to prove.
    assert!(
        !columns(&conn, "movies").iter().any(|c| c == "title_zh"),
        "the fixture still has title_zh, so nothing is being migrated"
    );

    migrate::ensure_schema(&conn).unwrap();

    let cols = columns(&conn, "movies");
    assert!(cols.contains(&"title_zh".to_string()), "columns after: {cols:?}");
    assert!(cols.contains(&"title_attempts".to_string()));
    assert_eq!(columns(&conn, "category_glossary"), vec!["term", "zh", "updated_at"]);

    // The migration must not have disturbed the data it ran alongside.
    let (title, desc_zh, title_zh): (String, String, Option<String>) = conn
        .query_row(
            "SELECT title, description_zh, title_zh FROM movies WHERE id = 1",
            [],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
        .unwrap();
    assert_eq!(title, "Nutt Crackers");
    assert_eq!(desc_zh, "原简介", "the existing synopsis translation must survive");
    assert_eq!(title_zh, None);
}

#[test]
fn the_real_schema_already_declares_everything_we_mirror() {
    let Some(root) = repo_root() else {
        eprintln!("skipping: schema.sql not found; this crate is outside the repo");
        return;
    };
    let schema = std::fs::read_to_string(root.join("schema.sql")).unwrap();
    let conn = Connection::open_in_memory().unwrap();
    conn.execute_batch(&schema).unwrap();

    // `migrate.rs` mirrors `schema.sql`. If a fresh database created straight from
    // schema.sql is missing something the migration would add, the two have drifted
    // and the mirror rule in that module's header is being violated.
    let cols = columns(&conn, "movies");
    for col in ["title_zh", "title_attempts"] {
        assert!(
            cols.contains(&col.to_string()),
            "schema.sql does not declare movies.{col}, but migrate.rs adds it"
        );
    }
    assert_eq!(columns(&conn, "category_glossary"), vec!["term", "zh", "updated_at"]);
}

#[test]
fn python_and_rust_agree_on_the_declared_types() {
    // Both migrators write into the same file. `PRAGMA table_info` echoes the declared
    // type back, so a divergence here means one of them would rewrite the other's work
    // with a different definition.
    let Some(root) = repo_root() else {
        eprintln!("skipping: schema.sql not found; this crate is outside the repo");
        return;
    };
    // The Python side's type strings live in db_manager.MIGRATIONS; the Rust side's in
    // migrate.rs. Compare the latter against what schema.sql declares, which Python
    // mirrors for fresh databases.
    let schema = std::fs::read_to_string(root.join("schema.sql")).unwrap();
    let conn = Connection::open_in_memory().unwrap();
    conn.execute_batch(&schema).unwrap();

    let mut stmt = conn.prepare("PRAGMA table_info(movies)").unwrap();
    let types: Vec<(String, String)> = stmt
        .query_map([], |r| Ok((r.get::<_, String>(1)?, r.get::<_, String>(2)?)))
        .unwrap()
        .collect::<rusqlite::Result<Vec<_>>>()
        .unwrap();
    let declared = |name: &str| {
        types
            .iter()
            .find(|(c, _)| c == name)
            .map(|(_, t)| t.clone())
            .unwrap_or_default()
    };

    assert_eq!(declared("title_zh"), "TEXT");
    assert_eq!(declared("title_attempts"), "INTEGER");

    // ...and that db_manager's ALTER strings agree, by reading them out of the file.
    let db_manager = std::fs::read_to_string(root.join("db_manager.py")).unwrap();
    let out = Command::new("python3")
        .args([
            "-c",
            "import re,sys; s=open(sys.argv[1]).read(); \
             m=re.search(r'\"movies\": \\[(.*?)\\]', s, re.S); \
             print(m.group(1) if m else '')",
            root.join("db_manager.py").to_str().unwrap(),
        ])
        .output();
    let Ok(out) = out else {
        eprintln!("skipping: no python3 to read db_manager.MIGRATIONS");
        return;
    };
    if !out.status.success() {
        eprintln!("skipping: python3 could not read db_manager.py");
        return;
    }
    let block = String::from_utf8_lossy(&out.stdout).to_string();
    assert!(
        block.contains("(\"title_zh\", \"TEXT\")"),
        "db_manager.MIGRATIONS does not add title_zh as TEXT; migrate.rs and Python \
         would then disagree. Block was: {block}"
    );
    assert!(
        block.contains("(\"title_attempts\", \"INTEGER DEFAULT 0\")"),
        "db_manager.MIGRATIONS does not add title_attempts as INTEGER DEFAULT 0. \
         Block was: {block}"
    );
    let _ = db_manager;
}
