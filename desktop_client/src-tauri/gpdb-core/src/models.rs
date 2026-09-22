//! Serialized shapes shared by every query.
//!
//! Field names stay snake_case on purpose: the TypeScript types in
//! `desktop_client/src/types.ts` read them by these exact names, so a
//! `rename_all` here would silently empty the UI.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

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

/// One entry of a film's director roster, from movie_directors. Carries no image:
/// directors are the only credited party the site gives no portrait for.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct DirectorRef {
    pub id: i64,
    pub name: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Episode {
    pub id: i64,
    /// Null for a standalone episode — one the site publishes on its own page with
    /// no parent film. Must stay `Option`: a bare `i64` makes `map_episode_row`
    /// fail with `InvalidType` on those rows, and the callers' `filter_map(|r| r.ok())`
    /// then drops them silently.
    pub movie_id: Option<i64>,
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
    /// The parent film's Chinese title. The episode itself is never translated,
    /// so this is the only Chinese a scene card can show for a film's scene.
    pub movie_title_zh: Option<String>,
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
    /// Chinese title. Shown as the primary name with `title` beneath it in smaller
    /// type, so the frontend needs both. Null until the title is translated.
    pub title_zh: Option<String>,
    /// Real per-film director roster. `director_name` is a single string that
    /// predates the parser fix and can hold several glued names, so this is what
    /// the UI renders one clickable name per director from.
    pub directors: Option<Vec<DirectorRef>>,
    pub performers: Option<Vec<PerformerRef>>,
    pub episodes: Option<Vec<Episode>>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct MoviesResponse {
    pub items: Vec<Movie>,
    pub total: i64,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct StudioSummary {
    pub name: String,
    pub works_count: i64,
    pub episodes_count: i64,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct StudioLibrary {
    pub items: Vec<StudioSummary>,
    pub total: i64,
}

/// One director as the library grid shows them.
///
/// `works_count` and `studios_count` are both computed live from `movie_directors` —
/// *not* read off `directors.works_count`, which is stale (see `queries::directors`).
/// A director with no links at all is still a row here, with both counts 0.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct DirectorSummary {
    pub id: i64,
    pub name: String,
    pub works_count: i64,
    /// How many distinct studios they directed for. The chips themselves are derived
    /// from the loaded films on the client, so this is only the card's badge.
    pub studios_count: i64,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct DirectorLibrary {
    pub items: Vec<DirectorSummary>,
    pub total: i64,
}

/// An episode's cast, as the episode library's cards need it: the id to open the
/// performer, the name to label the chip. Deliberately not `PerformerRef`, which also
/// carries a portrait the chips never show and which would have to be joined per row.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct EpisodeCastRef {
    pub id: i64,
    pub name: String,
}

/// One episode as the library grid shows it: the episode itself, its parent film, its
/// cast, and where it sits among that film's scenes.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct EpisodeSummary {
    pub id: i64,
    pub movie_id: Option<i64>,
    pub title: Option<String>,
    pub thumbnail_url: Option<String>,
    pub description: Option<String>,
    pub description_zh: Option<String>,
    pub action_notes: Option<String>,
    pub movie_title: Option<String>,
    /// The parent film's Chinese title; see `Episode::movie_title_zh`.
    pub movie_title_zh: Option<String>,
    pub studio_name: Option<String>,
    pub release_year: Option<i64>,
    /// Rank of this scene inside its own film, 1-based, by id order — the site names
    /// every episode "Episode #<its own row id>", so the title carries no position.
    pub episode_ordinal: i64,
    /// How many scenes that film has in total, so a card can say "第 3 集 / 共 5 集".
    pub episode_count: i64,
    pub performers: Vec<EpisodeCastRef>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EpisodeLibrary {
    pub items: Vec<EpisodeSummary>,
    pub total: i64,
}

/// One studio's films and episodes — the shape `/api/studios/<name>/works` returns.
#[derive(Serialize, Deserialize, Debug)]
pub struct StudioWorks {
    pub studio_name: String,
    pub movies: Vec<Movie>,
    pub movies_count: i64,
    pub episodes: Vec<Episode>,
    pub episodes_count: i64,
}

/// One director's complete filmography — the shape `/api/directors/<name>/works` returns.
///
/// Films only: the site credits directors per film, and there is no `episode_directors`
/// table, so a director has no episodes to list. The studios they worked with are not
/// returned either — the modal derives those from `movies[].studio_name`, which costs
/// no extra query.
#[derive(Serialize, Deserialize, Debug)]
pub struct DirectorWorks {
    pub name: String,
    pub movies: Vec<Movie>,
    pub movies_count: i64,
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
    /// Films *and* scenes. What "作品数量" means in the UI — see `works_expr` in
    /// `get_performers`. `movies_count` stays the film-only figure.
    pub works_count: Option<i64>,
    pub movies: Option<Vec<Movie>>,
    /// Scene/episode appearances, which the performer page lists in their own tab.
    pub episodes: Option<Vec<Episode>>,
    pub episodes_count: Option<i64>,
    /// Known aliases / credited alternative stage names across movies and episodes.
    pub aliases: Option<Vec<String>>,
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
    /// Silently dropped by serde until now: the frontend has always sent
    /// `director`, so clicking a director name narrowed the list in the browser
    /// build and did nothing here.
    pub director: Option<String>,
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
    /// Chinese title of a favourited film or of a favourited scene's parent film.
    /// Without it the favourites tab is the one grid in the app still showing
    /// English in Chinese mode.
    pub title_zh: Option<String>,
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

/// Both translation glossaries, as the client loads them at startup.
///
/// Read once and kept for the session, so they are plain maps rather than a queryable
/// endpoint. `terms` is the performer attribute vocabulary (en → zh) and `categories`
/// the atomic film categories (term → zh) — two separate maps on purpose, so the
/// frontend's `tr()` cannot confuse an attribute with a category of the same spelling.
#[derive(Serialize, Deserialize, Debug, Default)]
pub struct Glossaries {
    pub terms: HashMap<String, String>,
    pub categories: HashMap<String, String>,
}
