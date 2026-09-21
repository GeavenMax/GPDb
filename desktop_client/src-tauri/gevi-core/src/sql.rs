//! Shared SQL fragments and row mappers.
//!
//! A column added to PERFORMER_COLUMNS and its reader in map_performer_row live
//! in this one file on purpose — splitting them is how the two drift apart.

use std::collections::HashMap;

use crate::models::{Episode, Performer};

/// Attribute columns exposed as filter facets: (SQL column, API facet name,
/// index within PERFORMER_COLUMNS). Mirrors PERFORMER_FACETS in server.py.
pub const PERFORMER_FACETS: [(&str, &str, usize); 8] = [
    ("build", "bodyType", 8),
    ("hair", "hair", 2),
    ("eyes", "eyes", 3),
    ("skin", "skin", 9),
    ("body_hair", "bodyHair", 4),
    ("facial_hair", "facialHair", 5),
    ("dick_size", "dickSize", 10),
    ("foreskin", "foreskin", 11),
];

/// The site stores multi-value attributes with an inline `<br />` separator
/// (e.g. hair = "Brown<br />Blond"); split those into individual values.
pub fn explode_attr(raw: Option<String>) -> Vec<String> {
    let raw = match raw {
        Some(v) => v,
        None => return Vec::new(),
    };
    raw.split("<br />")
        .flat_map(|p| p.split("<br/>"))
        .flat_map(|p| p.split("<br>"))
        .map(|p| p.trim())
        .filter(|p| !p.is_empty())
        .map(|p| p.to_string())
        .collect()
}

/// SQL expression rewriting an attribute into `|a|b|` form so a LIKE can match
/// one token without a false hit on a longer label containing it.
pub fn facet_sql(column: &str) -> String {
    format!(
        "'|' || REPLACE(REPLACE(REPLACE(COALESCE(p.{}, ''), '<br />', '|'), '<br/>', '|'), '<br>', '|') || '|'",
        column
    )
}

/// Cast list for a movie: the profile name/portrait when the performer has been
/// scraped, falling back to the name recorded on the movie row.
pub const CAST_SQL: &str = "SELECT mp.performer_id, \
            COALESCE(p.name, mp.performer_name), p.image_url \
     FROM movie_performers mp \
     LEFT JOIN performers p ON p.id = mp.performer_id \
     WHERE mp.movie_id = ?1";

/// Performer columns selected by every Performer query, in map_performer_row order.
pub const PERFORMER_COLUMNS: &str = "p.id, p.name, p.hair, p.eyes, p.body_hair, p.facial_hair, \
     p.height, p.weight, p.build, p.skin, p.dick_size, p.foreskin, \
     p.tattoos, p.notes, p.image_url";

/// An episode plus its parent film's context, in map_episode_row order.
///
/// The LEFT JOIN is what lets the performer page list episodes from many films at
/// once; the caller appends its own WHERE and ORDER BY.
pub const EPISODE_SQL: &str = "SELECT e.id, e.movie_id, e.title, e.thumbnail_url, e.description, \
     e.description_zh, e.action_notes, m.title, m.studio_name, m.release_year \
     FROM episodes e LEFT JOIN movies m ON m.id = e.movie_id";

pub fn map_episode_row(r: &rusqlite::Row) -> rusqlite::Result<Episode> {
    Ok(Episode {
        id: r.get(0)?,
        movie_id: r.get(1)?,
        title: r.get(2)?,
        thumbnail_url: r.get(3)?,
        description: r.get(4)?,
        description_zh: r.get(5)?,
        action_notes: r.get(6)?,
        movie_title: r.get(7)?,
        studio_name: r.get(8)?,
        release_year: r.get(9)?,
    })
}
/// The five kinds of thing a favorite can point at. Mirrors FAVORITE_TYPES in db_manager.py.
pub const FAVORITE_TYPES: [&str; 5] = ["movie", "performer", "studio", "director", "episode"];

/// A director's name does not necessarily appear verbatim in movies.director_name:
/// rows scraped before the parser fix glue several names into one separator-free
/// string ("Bill ClaytonSteven Scarborough"), and even a fixed parser writes the
/// whole roster into that one column. movie_directors/directors hold the real
/// per-film roster, so both director lookups go through it. Each fragment takes
/// exactly one bound parameter for the name.
pub const DIRECTOR_MATCH_SQL: &str = "EXISTS (SELECT 1 FROM movie_directors md \
     JOIN directors d ON d.id = md.director_id \
     WHERE md.movie_id = m.id AND d.name = ?)";
pub const DIRECTOR_SEARCH_SQL: &str = "EXISTS (SELECT 1 FROM movie_directors md \
     JOIN directors d ON d.id = md.director_id \
     WHERE md.movie_id = m.id AND d.name LIKE ?)";
pub fn map_performer_row(r: &rusqlite::Row) -> rusqlite::Result<Performer> {
    // Exploded attributes, so the client never has to parse "<br />" markup.
    let mut attributes: HashMap<String, Vec<String>> = HashMap::new();
    for (_, api_name, idx) in PERFORMER_FACETS.iter() {
        let values = explode_attr(r.get::<usize, Option<String>>(*idx)?);
        if !values.is_empty() {
            attributes.insert(api_name.to_string(), values);
        }
    }

    Ok(Performer {
        id: r.get(0)?,
        name: r.get(1)?,
        hair: r.get(2)?,
        eyes: r.get(3)?,
        body_hair: r.get(4)?,
        facial_hair: r.get(5)?,
        height: r.get(6)?,
        weight: r.get(7)?,
        build: r.get(8)?,
        skin: r.get(9)?,
        dick_size: r.get(10)?,
        foreskin: r.get(11)?,
        tattoos: r.get(12)?,
        notes: r.get(13)?,
        image_url: r.get(14)?,
        attributes: Some(attributes),
        movies_count: r.get(15)?,
        works_count: r.get(16)?,
        movies: None,
        episodes: None,
        episodes_count: None,
    })
}
