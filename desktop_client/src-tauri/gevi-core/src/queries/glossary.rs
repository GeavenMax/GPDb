//! The two translation glossaries: performer attributes and film categories.

use std::collections::HashMap;

use rusqlite::Connection;

use crate::models::Glossaries;
use crate::Result;

/// Both glossaries in one round trip.
///
/// One query rather than two commands because the client loads them together at startup
/// and keeps them for the session. This is also the desktop build's *only* way to reach
/// them: the performer attributes it renders come from Rust reading SQLite, not from the
/// HTTP API, and until this existed the desktop build showed every attribute in English
/// (`api.getGlossary()` had no `isTauri` branch, so it fell through to a `fetch` with no
/// server behind it and quietly returned `{}`).
///
/// The two tables are deliberately separate — see schema.sql §11. Merging them would
/// make the frontend's `tr()` ambiguous: `Muscle` and `Twink` are plausible as either an
/// attribute or a category, and only a coincidence of today's data keeps them apart.
pub fn get_glossaries(conn: &Connection) -> Result<Glossaries> {
    Ok(Glossaries {
        terms: load(conn, "SELECT en, zh FROM attr_glossary")?,
        categories: load(conn, "SELECT term, zh FROM category_glossary")?,
    })
}

/// A whole glossary table as a map. Both are tiny (tens of rows) and read once per
/// session, so there is nothing to gain from narrowing the SELECT.
fn load(conn: &Connection, sql: &str) -> Result<HashMap<String, String>> {
    let mut stmt = conn.prepare(sql).map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| Ok((r.get(0)?, r.get(1)?)))
        .map_err(|e| e.to_string())?;
    Ok(rows.filter_map(|r| r.ok()).collect())
}
