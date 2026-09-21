//! Shared SQL fragments and row mappers.
//!
//! A column added to PERFORMER_COLUMNS and its reader in map_performer_row live
//! in this one file on purpose — splitting them is how the two drift apart.

use std::collections::{BTreeMap, HashMap};

use crate::models::{Episode, Movie, Performer};

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

/// The separator set, enumerated once. `explode_attr` and `facet_sql` must accept
/// exactly the same language: a value this splits into "Brown" but the SQL cannot
/// replace stays one token in the column, and the facet filter then silently
/// returns nothing.
///
/// Enumerated in every case rather than matched case-insensitively, because
/// SQLite's REPLACE has no notion of case: this file used to split only lower
/// case, `server.py`'s `_BR_RE` was `<br\s*/?>` + IGNORECASE, and the SQL matched
/// only lower case — three different sets. The bound is one optional space, since
/// `\s*` cannot be expressed as a finite REPLACE chain.
///
/// Mirrors `_BR_SPELLINGS` in `server.py`; `separator_sets_agree_on_every_probe`
/// in tests/parity.rs feeds probe strings through both and fails on drift.
pub const BR_SPELLINGS: [&str; 12] = [
    "<br />", "<bR />", "<Br />", "<BR />",
    "<br/>", "<bR/>", "<Br/>", "<BR/>",
    "<br>", "<bR>", "<Br>", "<BR>",
];

/// The site stores multi-value attributes with an inline `<br />` separator
/// (e.g. hair = "Brown<br />Blond"); split those into individual values.
pub fn explode_attr(raw: Option<String>) -> Vec<String> {
    let raw = match raw {
        Some(v) => v,
        None => return Vec::new(),
    };
    // The same progressive split the three hard-coded spellings used to do, over
    // the whole set. A value can carry several separators of different spellings
    // at once, so each split feeds the next.
    let mut parts = vec![raw.as_str()];
    for spelling in BR_SPELLINGS {
        parts = parts.into_iter().flat_map(|p| p.split(spelling)).collect();
    }
    parts
        .into_iter()
        .map(|p| p.trim())
        .filter(|p| !p.is_empty())
        .map(|p| p.to_string())
        .collect()
}

/// SQL expression rewriting a multi-value column into `|a|b|` form so one token
/// can be matched without a false hit on a longer label containing it.
///
/// `alias` is not decoration: this was written for `performers p`, and a caller
/// querying `movies m` would otherwise get `no such column: p.category`.
pub fn facet_sql(column: &str, alias: &str) -> String {
    let mut expr = format!("COALESCE({}.{}, '')", alias, column);
    for spelling in BR_SPELLINGS {
        expr = format!("REPLACE({}, '{}', '|')", expr, spelling);
    }
    format!("'|' || {} || '|'", expr)
}

/// Predicate matching one token inside a multi-value column, for `?` binding.
///
/// `instr` rather than `LIKE`: SQLite's LIKE is case-insensitive for ASCII, which
/// would quietly widen an exact-match filter into one that also hits
/// `|wrestling|`. There is no case-variant duplicate in the library today, so this
/// is free insurance rather than a bug fix.
pub fn facet_match_sql(column: &str, alias: &str) -> String {
    format!(
        "instr({}, '|' || ? || '|') > 0",
        facet_sql(column, alias)
    )
}

/// The library's categories as atomic terms, most films first.
///
/// `movies.category` stores the site's raw string, so 55 rows carry a multi-value
/// value ("Wrestling<br />J/O") and two carry a trailing separator with nothing
/// after it. Grouping by the raw string yields 74 entries, 21 of which render as a
/// chip with a literal `<br />` inside it; splitting first collapses them to 53.
///
/// A `BTreeSet` per value because a value could repeat a token and would otherwise
/// be counted twice into its own weight.
///
/// The `term` tiebreak is load-bearing: about 30 terms tie at count 1, and sorting
/// on count alone is not a total order, so the browser build and the desktop build
/// could render the same library in two different chip orders. Mirrors
/// `collapse_categories` in `server.py`.
pub fn collapse_categories(rows: &[(String, i64)]) -> Vec<String> {
    let mut counts: BTreeMap<String, i64> = BTreeMap::new();
    for (raw, n) in rows {
        let mut seen = std::collections::BTreeSet::new();
        for term in explode_attr(Some(raw.clone())) {
            if seen.insert(term.clone()) {
                *counts.entry(term).or_insert(0) += n;
            }
        }
    }
    let mut terms: Vec<(String, i64)> = counts.into_iter().collect();
    // `sort_by` is stable, and `terms` came out of a BTreeMap in key order, so the
    // alphabetical order the tiebreak promises is what the equal-count runs keep.
    terms.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
    terms.into_iter().map(|(t, _)| t).collect()
}

/// Cast list for a movie: the profile name/portrait when the performer has been
/// scraped, falling back to the name recorded on the movie row.
pub const CAST_SQL: &str = "SELECT mp.performer_id, \
            COALESCE(p.name, mp.performer_name), p.image_url \
     FROM movie_performers mp \
     LEFT JOIN performers p ON p.id = mp.performer_id \
     WHERE mp.movie_id = ?1";

/// Film columns selected by every Movie query, in `map_movie_row` order.
///
/// One list, not four. The paged list, the detail page, a studio's works and a
/// performer's films each used to carry their own copy, and the two short ones had
/// already drifted: neither selected `movie_type`, `description`, `description_zh`,
/// `covers_json`, `director_id` or `director_name`, so a studio page and a performer
/// page showed no 中 badge and no director line for exactly the films they list.
///
/// A short list would have to be paired with a *second* mapper, because
/// `map_movie_row` reads by index: dropping a column from the middle of the SELECT
/// would not fail to compile, it would silently shift every field after the gap into
/// the wrong struct field. Keeping the list whole is what makes one mapper safe.
///
/// The `m.` prefix is part of the constant — every caller aliases the table `movies m`,
/// which is also what the filter fragments in `DIRECTOR_MATCH_SQL` assume.
/// New columns go on the **end**: `map_movie_row` reads by index, so inserting one
/// in the middle does not fail to compile, it silently shifts every field after it
/// into the wrong struct field.
pub const MOVIE_COLUMNS: &str = "m.id, m.title, m.studio_id, m.studio_name, m.release_year, \
     m.duration_mins, m.category, m.rating, m.movie_type, \
     m.description, m.description_zh, m.cover_icon, m.cover_full, \
     m.covers_json, m.director_id, m.director_name, m.title_zh";

/// One film row. Leaves the three collection fields `None`: filling them needs extra
/// queries per film, so each caller decides which it wants (`get_movies` loads
/// performers and directors, `get_movie_detail` also loads episodes).
pub fn map_movie_row(r: &rusqlite::Row) -> rusqlite::Result<Movie> {
    // The per-image list, as stored. A malformed value is treated as absent rather
    // than failing the row: the caller still has cover_icon/cover_full to show.
    let covers_json: Option<String> = r.get(13)?;
    let covers: Option<Vec<String>> = covers_json.and_then(|s| serde_json::from_str(&s).ok());

    Ok(Movie {
        id: r.get(0)?,
        title: r.get(1)?,
        studio_id: r.get(2)?,
        studio_name: r.get(3)?,
        release_year: r.get(4)?,
        duration_mins: r.get(5)?,
        category: r.get(6)?,
        rating: r.get(7)?,
        movie_type: r.get(8)?,
        description: r.get(9)?,
        description_zh: r.get(10)?,
        cover_icon: r.get(11)?,
        cover_full: r.get(12)?,
        covers,
        director_id: r.get(14)?,
        director_name: r.get(15)?,
        title_zh: r.get(16)?,
        directors: None,
        performers: None,
        episodes: None,
    })
}

/// Performer columns selected by every Performer query, in map_performer_row order.
pub const PERFORMER_COLUMNS: &str = "p.id, p.name, p.hair, p.eyes, p.body_hair, p.facial_hair, \
     p.height, p.weight, p.build, p.skin, p.dick_size, p.foreskin, \
     p.tattoos, p.notes, p.image_url";

/// An episode plus its parent film's context, in map_episode_row order.
///
/// The LEFT JOIN is what lets the performer page list episodes from many films at
/// once; the caller appends its own WHERE and ORDER BY.
/// New columns go on the **end**; `map_episode_row` reads by index too. This is the
/// parent film's `title_zh` (index 10), not the episode's own title — episodes are
/// never translated, their `title` is a site-generated placeholder.
pub const EPISODE_SQL: &str = "SELECT e.id, e.movie_id, e.title, e.thumbnail_url, e.description, \
     e.description_zh, e.action_notes, m.title, m.studio_name, m.release_year, m.title_zh \
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
        movie_title_zh: r.get(10)?,
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
