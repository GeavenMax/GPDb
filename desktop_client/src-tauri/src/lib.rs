use rusqlite::{params, Connection, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;

/// Attribute columns exposed as filter facets: (SQL column, API facet name,
/// index within PERFORMER_COLUMNS). Mirrors PERFORMER_FACETS in server.py.
const PERFORMER_FACETS: [(&str, &str, usize); 8] = [
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
fn explode_attr(raw: Option<String>) -> Vec<String> {
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
fn facet_sql(column: &str) -> String {
    format!(
        "'|' || REPLACE(REPLACE(REPLACE(COALESCE(p.{}, ''), '<br />', '|'), '<br/>', '|'), '<br>', '|') || '|'",
        column
    )
}

/// Cast list for a movie: the profile name/portrait when the performer has been
/// scraped, falling back to the name recorded on the movie row.
const CAST_SQL: &str = "SELECT mp.performer_id, \
            COALESCE(p.name, mp.performer_name), p.image_url \
     FROM movie_performers mp \
     LEFT JOIN performers p ON p.id = mp.performer_id \
     WHERE mp.movie_id = ?1";

/// Performer columns selected by every Performer query, in map_performer_row order.
const PERFORMER_COLUMNS: &str = "p.id, p.name, p.hair, p.eyes, p.body_hair, p.facial_hair, \
     p.height, p.weight, p.build, p.skin, p.dick_size, p.foreskin, \
     p.tattoos, p.notes, p.image_url";

/// An episode plus its parent film's context, in map_episode_row order.
///
/// The LEFT JOIN is what lets the performer page list episodes from many films at
/// once; the caller appends its own WHERE and ORDER BY.
const EPISODE_SQL: &str = "SELECT e.id, e.movie_id, e.title, e.thumbnail_url, e.description, \
     e.description_zh, e.action_notes, m.title, m.studio_name, m.release_year \
     FROM episodes e LEFT JOIN movies m ON m.id = e.movie_id";

fn map_episode_row(r: &rusqlite::Row) -> rusqlite::Result<Episode> {
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

#[derive(Serialize, Deserialize, Debug)]
pub struct DatabaseStats {
    pub movies: i64,
    pub performers: i64,
    pub episodes: i64,
    pub movie_performers: i64,
    pub movies_scraped_ok: i64,
    pub movies_scraped_404: i64,
    pub movies_failed_500: i64,
    pub performers_scraped_ok: i64,
    pub performers_scraped_404: i64,
    pub performers_with_image: i64,
    pub translation_total: i64,
    pub translation_done: i64,
    pub translation_failed: i64,
    pub translation_pending: i64,
    /// The Tauri build reads SQLite directly and never calls the translation API.
    pub translation_configured: bool,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct PerformerRef {
    pub id: i64,
    pub name: String,
    pub image_url: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Episode {
    pub id: i64,
    pub movie_id: i64,
    pub title: Option<String>,
    pub thumbnail_url: Option<String>,
    pub description: Option<String>,
    /// Chinese synopsis, written when the parent film is translated (the episode is
    /// never translated on its own). Null until then.
    pub description_zh: Option<String>,
    pub action_notes: Option<String>,
    /// Parent film context, so an episode can be shown outside its film (the
    /// performer detail page lists a performer's episodes across many films).
    pub movie_title: Option<String>,
    pub studio_name: Option<String>,
    pub release_year: Option<i64>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Movie {
    pub id: i64,
    pub title: String,
    pub studio_id: Option<i64>,
    pub studio_name: Option<String>,
    pub release_year: Option<i64>,
    pub duration_mins: Option<i64>,
    pub category: Option<String>,
    pub rating: Option<String>,
    pub movie_type: Option<String>,
    pub description: Option<String>,
    pub description_zh: Option<String>,
    pub cover_icon: Option<String>,
    pub cover_full: Option<String>,
    pub covers: Option<Vec<String>>,
    pub director_id: Option<i64>,
    pub director_name: Option<String>,
    pub performers: Option<Vec<PerformerRef>>,
    pub episodes: Option<Vec<Episode>>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct MoviesResponse {
    pub items: Vec<Movie>,
    pub total: i64,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Performer {
    pub id: i64,
    pub name: String,
    pub hair: Option<String>,
    pub eyes: Option<String>,
    pub body_hair: Option<String>,
    pub facial_hair: Option<String>,
    pub height: Option<String>,
    pub weight: Option<String>,
    pub build: Option<String>,
    pub skin: Option<String>,
    pub dick_size: Option<String>,
    pub foreskin: Option<String>,
    pub tattoos: Option<String>,
    pub notes: Option<String>,
    pub image_url: Option<String>,
    /// Multi-value attributes exploded into lists (see PERFORMER_FACETS).
    pub attributes: Option<HashMap<String, Vec<String>>>,
    pub movies_count: Option<i64>,
    pub movies: Option<Vec<Movie>>,
    /// Scene/episode appearances, which the performer page lists in their own tab.
    pub episodes: Option<Vec<Episode>>,
    pub episodes_count: Option<i64>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct PerformersResponse {
    pub items: Vec<Performer>,
    pub total: i64,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct FacetValue {
    pub value: String,
    pub count: i64,
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct PerformerFacets {
    pub facets: HashMap<String, Vec<FacetValue>>,
    pub total: i64,
    pub with_image: i64,
    /// Performers with at least one attribute column filled in.
    pub enriched: i64,
}

#[derive(Serialize, Deserialize, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct FilterArgs {
    pub query: Option<String>,
    pub studio: Option<String>,
    pub category: Option<String>,
    #[serde(alias = "year_min")]
    pub year_min: Option<i64>,
    #[serde(alias = "year_max")]
    pub year_max: Option<i64>,
    #[serde(alias = "sort_by")]
    pub sort_by: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct PerformerFilterArgs {
    pub query: Option<String>,
    pub body_type: Option<Vec<String>>,
    pub hair: Option<Vec<String>>,
    pub eyes: Option<Vec<String>>,
    pub skin: Option<Vec<String>>,
    pub body_hair: Option<Vec<String>>,
    pub facial_hair: Option<Vec<String>>,
    pub dick_size: Option<Vec<String>>,
    pub foreskin: Option<Vec<String>>,
    pub has_image: Option<bool>,
    pub min_movies: Option<i64>,
    pub sort_by: Option<String>,
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct SyncResult {
    #[serde(alias = "new_movies")]
    pub new_movies: i64,
    #[serde(alias = "new_performers")]
    pub new_performers: i64,
}

fn find_db_path() -> PathBuf {
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

/// The five kinds of thing a favorite can point at. Mirrors FAVORITE_TYPES in db_manager.py.
const FAVORITE_TYPES: [&str; 5] = ["movie", "performer", "studio", "director", "episode"];

/// One favorited item, shaped for the card that renders it.
///
/// Which fields are populated depends on the type, exactly as in
/// db_manager.get_favorites: movie/episode carry display fields, studio/director
/// just a name and how many works the library holds. Fields stay snake_case because
/// the TypeScript `FavoriteItem` reads them by these names.
#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct FavoriteItem {
    pub key: String,
    pub created_at: Option<String>,
    pub title: Option<String>,
    pub name: Option<String>,
    pub release_year: Option<i64>,
    pub studio_name: Option<String>,
    pub cover_full: Option<String>,
    pub has_zh: Option<bool>,
    pub image_url: Option<String>,
    pub thumbnail_url: Option<String>,
    pub movie_id: Option<i64>,
    pub movie_title: Option<String>,
    pub works_count: Option<i64>,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct FavoritesResponse {
    pub movie: Vec<FavoriteItem>,
    pub performer: Vec<FavoriteItem>,
    pub studio: Vec<FavoriteItem>,
    pub director: Vec<FavoriteItem>,
    pub episode: Vec<FavoriteItem>,
    pub counts: HashMap<String, i64>,
}

#[tauri::command]
fn get_favorites() -> Result<FavoritesResponse, String> {
    let conn = open_db()?;
    let mut out = FavoritesResponse::default();

    // Movies: the display fields a poster card needs, plus whether a translation exists.
    let mut stmt = conn
        .prepare(
            "SELECT f.entity_key, f.created_at, m.title, m.release_year, m.studio_name, \
                    m.cover_full, m.description_zh IS NOT NULL \
             FROM user_favorites f JOIN movies m ON m.id = CAST(f.entity_key AS INTEGER) \
             WHERE f.entity_type = 'movie' ORDER BY f.created_at DESC",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(FavoriteItem {
                key: r.get(0)?,
                created_at: r.get(1)?,
                title: r.get(2)?,
                release_year: r.get(3)?,
                studio_name: r.get(4)?,
                cover_full: r.get(5)?,
                has_zh: Some(r.get::<usize, i64>(6)? != 0),
                ..Default::default()
            })
        })
        .map_err(|e| e.to_string())?;
    out.movie = rows.filter_map(Result::ok).collect();

    // Performers.
    let mut stmt = conn
        .prepare(
            "SELECT f.entity_key, f.created_at, p.name, p.image_url \
             FROM user_favorites f JOIN performers p ON p.id = CAST(f.entity_key AS INTEGER) \
             WHERE f.entity_type = 'performer' ORDER BY f.created_at DESC",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(FavoriteItem {
                key: r.get(0)?,
                created_at: r.get(1)?,
                name: r.get(2)?,
                image_url: r.get(3)?,
                ..Default::default()
            })
        })
        .map_err(|e| e.to_string())?;
    out.performer = rows.filter_map(Result::ok).collect();

    // Episodes, with the parent film's title so the card can say where it came from.
    let mut stmt = conn
        .prepare(
            "SELECT f.entity_key, f.created_at, e.title, e.thumbnail_url, e.movie_id, \
                    m.title, m.studio_name, e.description_zh IS NOT NULL \
             FROM user_favorites f JOIN episodes e ON e.id = CAST(f.entity_key AS INTEGER) \
             LEFT JOIN movies m ON m.id = e.movie_id \
             WHERE f.entity_type = 'episode' ORDER BY f.created_at DESC",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(FavoriteItem {
                key: r.get(0)?,
                created_at: r.get(1)?,
                title: r.get(2)?,
                thumbnail_url: r.get(3)?,
                movie_id: r.get(4)?,
                movie_title: r.get(5)?,
                studio_name: r.get(6)?,
                has_zh: Some(r.get::<usize, i64>(7)? != 0),
                ..Default::default()
            })
        })
        .map_err(|e| e.to_string())?;
    out.episode = rows.filter_map(Result::ok).collect();

    // Studios and directors exist only as columns on movies, so the card shows the
    // name plus how many works the library holds for it.
    for (etype, column) in [("studio", "studio_name"), ("director", "director_name")] {
        let mut stmt = conn
            .prepare(&format!(
                "SELECT f.entity_key, f.created_at, \
                        (SELECT COUNT(*) FROM movies WHERE {} = f.entity_key) \
                 FROM user_favorites f WHERE f.entity_type = ?1 ORDER BY f.created_at DESC",
                column
            ))
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![etype], |r| {
                let key: String = r.get(0)?;
                Ok(FavoriteItem {
                    name: Some(key.clone()),
                    key,
                    created_at: r.get(1)?,
                    works_count: r.get(2)?,
                    ..Default::default()
                })
            })
            .map_err(|e| e.to_string())?;
        let items: Vec<FavoriteItem> = rows.filter_map(Result::ok).collect();
        out.counts.insert(etype.to_string(), items.len() as i64);
        match etype {
            "studio" => out.studio = items,
            _ => out.director = items,
        }
    }

    out.counts.insert("movie".into(), out.movie.len() as i64);
    out.counts.insert("performer".into(), out.performer.len() as i64);
    out.counts.insert("episode".into(), out.episode.len() as i64);

    Ok(out)
}

/// Flip one favorite, returning whether it is favorited afterwards.
#[tauri::command]
fn toggle_favorite(entity_type: String, entity_key: String) -> Result<bool, String> {
    if !FAVORITE_TYPES.contains(&entity_type.as_str()) {
        return Err(format!("未知的收藏类型 '{}'", entity_type));
    }
    let key = entity_key.trim();
    if key.is_empty() {
        return Err("收藏对象不能为空".to_string());
    }

    let conn = open_db()?;
    // Toggling is read-then-write, so it runs in one transaction: two rapid clicks
    // must not both read "absent" and both insert.
    let tx = conn.unchecked_transaction().map_err(|e| e.to_string())?;
    let existing: Option<i64> = tx
        .query_row(
            "SELECT 1 FROM user_favorites WHERE entity_type = ?1 AND entity_key = ?2",
            params![entity_type, key],
            |r| r.get(0),
        )
        .optional()
        .map_err(|e| e.to_string())?;

    let now_favorite = match existing {
        Some(_) => {
            tx.execute(
                "DELETE FROM user_favorites WHERE entity_type = ?1 AND entity_key = ?2",
                params![entity_type, key],
            )
            .map_err(|e| e.to_string())?;
            false
        }
        None => {
            tx.execute(
                "INSERT OR IGNORE INTO user_favorites (entity_type, entity_key) VALUES (?1, ?2)",
                params![entity_type, key],
            )
            .map_err(|e| e.to_string())?;
            true
        }
    };
    tx.commit().map_err(|e| e.to_string())?;
    Ok(now_favorite)
}

fn open_db() -> Result<Connection, String> {
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

#[tauri::command]
fn get_stats() -> Result<DatabaseStats, String> {
    let conn = open_db()?;
    let count = |tbl: &str| -> i64 {
        conn.query_row(&format!("SELECT count(*) FROM {}", tbl), [], |r| r.get(0)).unwrap_or(0)
    };
    let prog_count = |t: &str, s: i64| -> i64 {
        conn.query_row(
            "SELECT count(*) FROM scrape_progress WHERE item_type = ?1 AND status = ?2",
            params![t, s],
            |r| r.get(0),
        ).unwrap_or(0)
    };

    let scalar = |sql: &str| -> i64 { conn.query_row(sql, [], |r| r.get(0)).unwrap_or(0) };

    let translation_total = scalar(
        "SELECT count(*) FROM movies WHERE description IS NOT NULL AND trim(description) != ''",
    );
    let translation_done = scalar(
        "SELECT count(*) FROM movies WHERE description_zh IS NOT NULL AND trim(description_zh) != ''",
    );
    let translation_failed = scalar(
        "SELECT count(*) FROM movies WHERE (description_zh IS NULL OR trim(description_zh) = '') \
         AND COALESCE(translation_attempts, 0) >= 3 \
         AND description IS NOT NULL AND trim(description) != ''",
    );

    Ok(DatabaseStats {
        movies: count("movies"),
        performers: count("performers"),
        episodes: count("episodes"),
        movie_performers: count("movie_performers"),
        movies_scraped_ok: prog_count("movie", 200),
        movies_scraped_404: prog_count("movie", 404),
        movies_failed_500: prog_count("movie", 500),
        performers_scraped_ok: prog_count("performer", 200),
        performers_scraped_404: prog_count("performer", 404),
        performers_with_image: scalar(
            "SELECT count(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''",
        ),
        translation_total,
        translation_done,
        translation_failed,
        translation_pending: (translation_total - translation_done - translation_failed).max(0),
        translation_configured: false,
    })
}

#[tauri::command]
fn get_movies(
    filters: Option<FilterArgs>,
    page: Option<i64>,
    page_size: Option<i64>,
) -> Result<MoviesResponse, String> {
    let conn = open_db()?;
    let f = filters.unwrap_or_default();
    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let mut conditions = Vec::new();
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    if let Some(ref q) = f.query {
        let q_trimmed = q.trim();
        if !q_trimmed.is_empty() {
            let clean_q: String = q_trimmed.chars().filter(|c| c.is_alphanumeric() || c.is_whitespace()).collect();
            conditions.push("(m.title LIKE ? OR m.studio_name LIKE ? OR m.director_name LIKE ? OR m.description_zh LIKE ? OR m.id = ?)".to_string());
            let like_q = format!("%{}%", clean_q);
            let id_val: i64 = clean_q.parse().unwrap_or(-1);
            params_vec.push(Box::new(like_q.clone()));
            params_vec.push(Box::new(like_q.clone()));
            params_vec.push(Box::new(like_q.clone()));
            params_vec.push(Box::new(like_q));
            params_vec.push(Box::new(id_val));
        }
    }

    if let Some(ref st) = f.studio {
        let st_trimmed = st.trim();
        if !st_trimmed.is_empty() {
            conditions.push("m.studio_name = ?".to_string());
            params_vec.push(Box::new(st_trimmed.to_string()));
        }
    }

    if let Some(ref cat) = f.category {
        let cat_trimmed = cat.trim();
        if !cat_trimmed.is_empty() {
            conditions.push("m.category = ?".to_string());
            params_vec.push(Box::new(cat_trimmed.to_string()));
        }
    }

    if let Some(ymin) = f.year_min {
        conditions.push("m.release_year >= ?".to_string());
        params_vec.push(Box::new(ymin));
    }

    if let Some(ymax) = f.year_max {
        conditions.push("m.release_year <= ?".to_string());
        params_vec.push(Box::new(ymax));
    }

    let where_clause = if conditions.is_empty() {
        String::new()
    } else {
        format!("WHERE {}", conditions.join(" AND "))
    };

    let sort_clause = match f.sort_by.as_deref() {
        Some("year_asc") => "m.release_year ASC, m.id ASC",
        Some("title_asc") => "m.title ASC",
        Some("id_desc") => "m.id DESC",
        _ => "m.release_year DESC, m.id DESC",
    };

    // Total count
    let count_query = format!("SELECT count(*) FROM movies m {}", where_clause);
    let params_slice: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = conn.query_row(&count_query, &params_slice[..], |r| r.get(0)).map_err(|e| e.to_string())?;

    // Page items
    let select_query = format!(
        "SELECT m.id, m.title, m.studio_id, m.studio_name, m.release_year, \
                m.duration_mins, m.category, m.rating, m.movie_type, \
                m.description, m.description_zh, m.cover_icon, m.cover_full, \
                m.covers_json, m.director_id, m.director_name \
         FROM movies m \
         {} \
         ORDER BY {} \
         LIMIT ? OFFSET ?",
        where_clause, sort_clause
    );

    let mut full_params = params_vec;
    full_params.push(Box::new(page_size));
    full_params.push(Box::new(offset));
    let full_slice: Vec<&dyn rusqlite::ToSql> = full_params.iter().map(|p| p.as_ref()).collect();

    let mut stmt = conn.prepare(&select_query).map_err(|e| e.to_string())?;
    let movie_rows = stmt.query_map(&full_slice[..], |r| {
        let covers_json_opt: Option<String> = r.get(13)?;
        let covers: Option<Vec<String>> = covers_json_opt.and_then(|s| serde_json::from_str(&s).ok());
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
            performers: None,
            episodes: None,
        })
    }).map_err(|e| e.to_string())?;

    let mut items = Vec::new();
    for mr in movie_rows {
        if let Ok(mut m) = mr {
            let mut p_stmt = conn.prepare(CAST_SQL).map_err(|e| e.to_string())?;
            let p_iter = p_stmt.query_map(params![m.id], |pr| {
                Ok(PerformerRef {
                    id: pr.get(0)?,
                    name: pr.get(1)?,
                    image_url: pr.get(2)?,
                })
            }).map_err(|e| e.to_string())?;
            m.performers = Some(p_iter.filter_map(Result::ok).collect());
            items.push(m);
        }
    }

    Ok(MoviesResponse { items, total })
}

#[tauri::command]
fn get_movie_detail(id: i64) -> Result<Option<Movie>, String> {
    let conn = open_db()?;
    let mut stmt = conn.prepare(
        "SELECT id, title, studio_id, studio_name, release_year, duration_mins, \
                category, rating, movie_type, description, description_zh, cover_icon, cover_full, \
                covers_json, director_id, director_name \
         FROM movies WHERE id = ?1"
    ).map_err(|e| e.to_string())?;

    let movie_res = stmt.query_row(params![id], |r| {
        let covers_json_opt: Option<String> = r.get(13)?;
        let covers: Option<Vec<String>> = covers_json_opt.and_then(|s| serde_json::from_str(&s).ok());
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
            performers: None,
            episodes: None,
        })
    });

    match movie_res {
        Ok(mut m) => {
            // Performers
            let mut p_stmt = conn.prepare(CAST_SQL).map_err(|e| e.to_string())?;
            let p_iter = p_stmt.query_map(params![m.id], |pr| {
                Ok(PerformerRef {
                    id: pr.get(0)?,
                    name: pr.get(1)?,
                    image_url: pr.get(2)?,
                })
            }).map_err(|e| e.to_string())?;
            m.performers = Some(p_iter.filter_map(Result::ok).collect());

            // Episodes
            let mut ep_stmt = conn.prepare(
                &format!("{} WHERE e.movie_id = ?1 ORDER BY e.id ASC", EPISODE_SQL)
            ).map_err(|e| e.to_string())?;
            let ep_iter = ep_stmt.query_map(params![m.id], map_episode_row)
                .map_err(|e| e.to_string())?;
            m.episodes = Some(ep_iter.filter_map(Result::ok).collect());

            Ok(Some(m))
        }
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(e.to_string()),
    }
}

#[tauri::command]
fn get_performers(
    filters: Option<PerformerFilterArgs>,
    page: Option<i64>,
    page_size: Option<i64>,
) -> Result<PerformersResponse, String> {
    let conn = open_db()?;
    let f = filters.unwrap_or_default();
    let page = page.unwrap_or(1).max(1);
    let page_size = page_size.unwrap_or(24).clamp(1, 100);
    let offset = (page - 1) * page_size;

    let mut conditions: Vec<String> = Vec::new();
    let mut params_vec: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();

    let q = f.query.clone().unwrap_or_default().trim().to_string();
    if !q.is_empty() {
        conditions.push("(p.name LIKE ? OR p.id = ?)".to_string());
        params_vec.push(Box::new(format!("%{}%", q)));
        params_vec.push(Box::new(q.parse::<i64>().unwrap_or(-1)));
    }

    // Multiple values for one attribute are OR-ed; different attributes AND-ed.
    let facet_selections: [(&str, &Option<Vec<String>>); 8] = [
        ("build", &f.body_type),
        ("hair", &f.hair),
        ("eyes", &f.eyes),
        ("skin", &f.skin),
        ("body_hair", &f.body_hair),
        ("facial_hair", &f.facial_hair),
        ("dick_size", &f.dick_size),
        ("foreskin", &f.foreskin),
    ];
    for (column, selection) in facet_selections {
        let values: Vec<String> = selection
            .clone()
            .unwrap_or_default()
            .into_iter()
            .map(|v| v.trim().to_string())
            .filter(|v| !v.is_empty())
            // Facet labels are site-controlled; strip LIKE wildcards so a stray
            // % cannot widen the match.
            .map(|v| v.replace('%', "").replace('_', ""))
            .collect();
        if values.is_empty() {
            continue;
        }
        let expr = facet_sql(column);
        let mut ors: Vec<String> = Vec::with_capacity(values.len());
        for value in &values {
            params_vec.push(Box::new(format!("%|{}|%", value)));
            ors.push(format!("{} LIKE ?", expr));
        }
        conditions.push(format!("({})", ors.join(" OR ")));
    }

    if f.has_image.unwrap_or(false) {
        conditions.push("(p.image_url IS NOT NULL AND trim(p.image_url) != '')".to_string());
    }

    let count_expr = "(SELECT count(*) FROM movie_performers mp WHERE mp.performer_id = p.id)";
    if let Some(min) = f.min_movies {
        conditions.push(format!("{} >= ?", count_expr));
        params_vec.push(Box::new(min));
    }

    let where_clause = if conditions.is_empty() {
        String::new()
    } else {
        format!("WHERE {}", conditions.join(" AND "))
    };

    let sort_clause = match f.sort_by.as_deref() {
        Some("movies_asc") => format!("{} ASC, p.id ASC", count_expr),
        Some("name_asc") => "p.name ASC".to_string(),
        Some("id_desc") => "p.id DESC".to_string(),
        Some("id_asc") => "p.id ASC".to_string(),
        _ => format!("{} DESC, p.id DESC", count_expr),
    };

    let count_query = format!("SELECT count(*) FROM performers p {}", where_clause);
    let params_slice: Vec<&dyn rusqlite::ToSql> = params_vec.iter().map(|p| p.as_ref()).collect();
    let total: i64 = conn.query_row(&count_query, &params_slice[..], |r| r.get(0)).map_err(|e| e.to_string())?;

    let select_query = format!(
        "SELECT {}, {} AS movies_count \
         FROM performers p \
         {} \
         ORDER BY {} \
         LIMIT ? OFFSET ?",
        PERFORMER_COLUMNS, count_expr, where_clause, sort_clause
    );

    let mut full_params = params_vec;
    full_params.push(Box::new(page_size));
    full_params.push(Box::new(offset));
    let full_slice: Vec<&dyn rusqlite::ToSql> = full_params.iter().map(|p| p.as_ref()).collect();

    let mut stmt = conn.prepare(&select_query).map_err(|e| e.to_string())?;
    let rows = stmt.query_map(&full_slice[..], map_performer_row).map_err(|e| e.to_string())?;

    let items = rows.filter_map(Result::ok).collect();
    Ok(PerformersResponse { items, total })
}

#[tauri::command]
fn get_performer_facets() -> Result<PerformerFacets, String> {
    let conn = open_db()?;
    let columns: Vec<&str> = PERFORMER_FACETS.iter().map(|(col, _, _)| *col).collect();
    let select_query = format!("SELECT {} FROM performers", columns.join(", "));

    let mut stmt = conn.prepare(&select_query).map_err(|e| e.to_string())?;
    // Aggregation happens in Rust: only a small slice of performers has been
    // detail-scraped, and multi-value attributes share one column.
    let rows = stmt
        .query_map([], |r| {
            let mut collected: Vec<Vec<String>> = Vec::with_capacity(PERFORMER_FACETS.len());
            for i in 0..PERFORMER_FACETS.len() {
                collected.push(explode_attr(r.get::<usize, Option<String>>(i)?));
            }
            Ok(collected)
        })
        .map_err(|e| e.to_string())?;

    let mut counts: HashMap<&str, HashMap<String, i64>> = HashMap::new();
    let mut enriched: i64 = 0;
    for row in rows.filter_map(Result::ok) {
        if row.iter().any(|values| !values.is_empty()) {
            enriched += 1;
        }
        for (idx, (_, api_name, _)) in PERFORMER_FACETS.iter().enumerate() {
            for value in &row[idx] {
                *counts.entry(api_name).or_default().entry(value.clone()).or_insert(0) += 1;
            }
        }
    }

    let mut facets: HashMap<String, Vec<FacetValue>> = HashMap::new();
    for (api_name, value_counts) in counts {
        let mut values: Vec<FacetValue> = value_counts
            .into_iter()
            .map(|(value, count)| FacetValue { value, count })
            .collect();
        // Most common first, then alphabetical, matching the Python server.
        values.sort_by(|a, b| b.count.cmp(&a.count).then_with(|| a.value.cmp(&b.value)));
        facets.insert(api_name.to_string(), values);
    }

    let scalar = |sql: &str| -> i64 { conn.query_row(sql, [], |r| r.get(0)).unwrap_or(0) };

    Ok(PerformerFacets {
        facets,
        total: scalar("SELECT count(*) FROM performers"),
        with_image: scalar(
            "SELECT count(*) FROM performers WHERE image_url IS NOT NULL AND trim(image_url) != ''",
        ),
        enriched,
    })
}

fn map_performer_row(r: &rusqlite::Row) -> rusqlite::Result<Performer> {
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
        movies: None,
        episodes: None,
        episodes_count: None,
    })
}

#[tauri::command]
fn get_performer_detail(id: i64) -> Result<Option<Performer>, String> {
    let conn = open_db()?;
    let mut stmt = conn.prepare(&format!(
        "SELECT {}, 0 FROM performers p WHERE p.id = ?1",
        PERFORMER_COLUMNS
    )).map_err(|e| e.to_string())?;

    let perf_res = stmt.query_row(params![id], map_performer_row);

    match perf_res {
        Ok(mut p) => {
            let mut m_stmt = conn.prepare(
                "SELECT m.id, m.title, m.studio_id, m.studio_name, m.release_year, \
                        m.duration_mins, m.category, m.rating, m.cover_icon, m.cover_full \
                 FROM movies m \
                 JOIN movie_performers mp ON m.id = mp.movie_id \
                 WHERE mp.performer_id = ?1 \
                 ORDER BY m.release_year DESC, m.id DESC"
            ).map_err(|e| e.to_string())?;

            let m_rows = m_stmt.query_map(params![id], |r| {
                Ok(Movie {
                    id: r.get(0)?,
                    title: r.get(1)?,
                    studio_id: r.get(2)?,
                    studio_name: r.get(3)?,
                    release_year: r.get(4)?,
                    duration_mins: r.get(5)?,
                    category: r.get(6)?,
                    rating: r.get(7)?,
                    movie_type: None,
                    description: None,
                    description_zh: None,
                    cover_icon: r.get(8)?,
                    cover_full: r.get(9)?,
                    covers: None,
                    director_id: None,
                    director_name: None,
                    performers: None,
                    episodes: None,
                })
            }).map_err(|e| e.to_string())?;

            let movies: Vec<Movie> = m_rows.filter_map(Result::ok).collect();
            p.movies_count = Some(movies.len() as i64);
            p.movies = Some(movies);

            // Scene/episode appearances, matched through episode_performers.
            let mut e_stmt = conn.prepare(&format!(
                "{} JOIN episode_performers ep ON e.id = ep.episode_id \
                 WHERE ep.performer_id = ?1 ORDER BY m.release_year DESC, e.id DESC",
                EPISODE_SQL
            )).map_err(|e| e.to_string())?;
            let e_iter = e_stmt.query_map(params![id], map_episode_row)
                .map_err(|e| e.to_string())?;
            let episodes: Vec<Episode> = e_iter.filter_map(Result::ok).collect();
            p.episodes_count = Some(episodes.len() as i64);
            p.episodes = Some(episodes);

            Ok(Some(p))
        }
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(e.to_string()),
    }
}

#[tauri::command]
fn get_studios() -> Result<Vec<String>, String> {
    let conn = open_db()?;
    let mut stmt = conn.prepare(
        "SELECT studio_name FROM movies \
         WHERE studio_name IS NOT NULL AND trim(studio_name) != '' \
         GROUP BY studio_name ORDER BY count(*) DESC LIMIT 200"
    ).map_err(|e| e.to_string())?;

    let rows = stmt.query_map([], |r| r.get(0)).map_err(|e| e.to_string())?;
    Ok(rows.filter_map(Result::ok).collect())
}

#[tauri::command]
fn get_categories() -> Result<Vec<String>, String> {
    let conn = open_db()?;
    let mut stmt = conn.prepare(
        "SELECT category FROM movies \
         WHERE category IS NOT NULL AND trim(category) != '' \
         GROUP BY category ORDER BY count(*) DESC"
    ).map_err(|e| e.to_string())?;

    let rows = stmt.query_map([], |r| r.get(0)).map_err(|e| e.to_string())?;
    Ok(rows.filter_map(Result::ok).collect())
}

#[tauri::command]
fn run_sync() -> Result<SyncResult, String> {
    // Run sync_gevi.py as a subprocess
    let script_path = PathBuf::from("sync_gevi.py");
    let script = if script_path.exists() {
        script_path
    } else {
        PathBuf::from("../sync_gevi.py")
    };

    let output = std::process::Command::new("python3")
        .arg(&script)
        .arg("--probe-count")
        .arg("10")
        .output()
        .map_err(|e| format!("Failed to spawn sync script: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut new_movies = 0;
    let mut new_performers = 0;

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
    }

    Ok(SyncResult {
        new_movies,
        new_performers,
    })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_log::Builder::default().build())
        .invoke_handler(tauri::generate_handler![
            get_stats,
            get_movies,
            get_movie_detail,
            get_performers,
            get_performer_facets,
            get_performer_detail,
            get_studios,
            get_categories,
            get_favorites,
            toggle_favorite,
            run_sync,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
