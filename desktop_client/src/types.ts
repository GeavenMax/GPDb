export interface UserTag {
  id: number;
  name: string;
  color: string;
  created_at?: string;
}

export interface UserMovieData {
  movie_id: number;
  rating?: number | null;
  status?: string | null; // 'wishlist' | 'watched' | 'favorite'
  notes?: string;
  updated_at?: string;
  tags?: UserTag[];
}

export interface CacheStats {
  count: number;
  size_mb: number;
  path: string;
}

export interface Movie {
  id: number;
  title: string;
  studio_id?: number | null;
  studio_name?: string | null;
  release_year?: number | null;
  release_date?: string | null;
  duration_mins?: number | null;
  category?: string | null;
  rating?: string | null;
  movie_type?: string | null;
  description?: string | null;
  description_zh?: string | null;
  translation_attempts?: number | null;
  cover_icon?: string | null;
  cover_full?: string | null;
  covers?: string[];
  director_id?: number | null;
  director_name?: string | null;
  /**
   * Chinese title. In Chinese mode this is what the card shows as the film's name,
   * with the original `title` beneath it in smaller type — the pun in the English
   * title is often the point, so neither one is dropped. Null until the title has
   * been translated; see `utils/bilingual.ts` for the fallback rule.
   */
  title_zh?: string | null;
  /**
   * Real per-film director roster, from the movie_directors junction table.
   * `director_name` is one string and, for rows scraped before the parser fix,
   * holds every name glued together with no separator at all — so this is the
   * only reliable source for one clickable name per director. Absent from list
   * responses (detail only) and empty on a library that has not been through
   * `scraper_v2.py --mode directors` yet.
   */
  directors?: Array<{ id: number; name: string }> | null;
  performers?: Array<{ id: number; name: string; image_url?: string | null }>;
  episodes?: Episode[];
  userData?: UserMovieData | null;
  is_favorite?: boolean;
}

export interface MovieSeriesResponse {
  root_title: string;
  studio_name?: string | null;
  items: Movie[];
}

export interface Performer {
  id: number;
  name: string;
  hair?: string | null;
  eyes?: string | null;
  body_hair?: string | null;
  facial_hair?: string | null;
  height?: string | null;
  weight?: string | null;
  build?: string | null;
  skin?: string | null;
  dick_size?: string | null;
  foreskin?: string | null;
  tattoos?: string | null;
  notes?: string | null;
  image_url?: string | null;
  /** Server-side exploded view of the multi-value attributes (see PERFORMER_FACETS). */
  attributes?: Record<string, string[]>;
  movies?: Movie[];
  movies_count?: number;
  /**
   * Films *and* scenes — what the UI shows as "作品". Scenes live in a separate
   * table, so `movies_count` alone under-reported anyone whose work is mostly
   * scenes, and the grid's "作品最多" sort disagreed with the number on the card.
   */
  works_count?: number;
  episodes?: Episode[];
  episodes_count?: number;
  /** Credited aliases / alternative stage names across movies and episodes */
  aliases?: string[];
  /** Direct BoyfriendTV performer profile URL (from scrape_bftv_performers.py). When present, BFTV button jumps directly here. */
  bftv_url?: string | null;
}

/** Sort keys accepted by the studio library, on both the HTTP and Tauri paths. */
export type StudioSortBy = 'works_desc' | 'episodes_desc' | 'name_asc';

/**
 * One row of the studio library grid.
 *
 * Studios have no table of their own — they exist only as `movies.studio_name` —
 * so this is the grouping of that column rather than a stored record, and there is
 * no artwork to show.
 */
export interface StudioSummary {
  name: string;
  works_count: number;
  episodes_count: number;
}

export interface StudioLibraryResponse {
  items: StudioSummary[];
  total: number;
}

/** A studio's films and episodes, as the detail modal shows them. */
export interface StudioWorks {
  studio_name: string;
  movies: Movie[];
  movies_count: number;
  episodes: Episode[];
  episodes_count: number;
}

/** Sort keys accepted by the director library, on both the HTTP and Tauri paths. */
export type DirectorSortBy = 'works_desc' | 'name_asc';

/**
 * One row of the director library grid.
 *
 * Unlike a studio, a director is a real record — `directors` has one row per person
 * and `movie_directors` links them to films — but there is still no portrait, so the
 * card is a letter tile. Both counts are computed live from the junction table:
 * `directors.works_count` is a denormalized column the everyday scrapers never
 * refresh, and behind it `movies.director_name` is a legacy string that holds several
 * glued names, so counting through either one undercounts anyone who shared a credit.
 */
export interface DirectorSummary {
  id: number;
  name: string;
  works_count: number;
  studios_count: number;
}

export interface DirectorLibraryResponse {
  items: DirectorSummary[];
  total: number;
}

/**
 * A director's films, as the detail modal shows them.
 *
 * Keyed by name rather than id because that is how `user_favorites` stores a director,
 * so the 我的收藏 page can open one without resolving an id first. Films only: there is
 * no episode↔director table, so a director's work is movies. The collaborating studios
 * and the year list are derived from `movies` in the modal — no extra query.
 */
export interface DirectorWorks {
  name: string;
  movies: Movie[];
  movies_count: number;
}

/** An episode's cast, as the library cards need it: id to open, name to label. */
export interface EpisodeCastRef {
  id: number;
  name: string;
}

/** Sort keys accepted by the episode library, on both the HTTP and Tauri paths. */
export type EpisodeSortBy = 'id_desc' | 'year_desc' | 'movie_asc';

/**
 * One row of the episode library grid.
 *
 * The site names every episode "Episode #<its own row id>", so `title` carries no
 * position at all — `episode_ordinal` is the scene's rank inside its own film, which
 * is what a card can actually show ("第 3 集 / 共 5 集").
 */
export interface EpisodeSummary {
  id: number;
  movie_id?: number | null;
  title: string;
  thumbnail_url?: string | null;
  description?: string | null;
  /** Chinese synopsis; null until the parent film has been translated. */
  description_zh?: string | null;
  action_notes?: string | null;
  movie_title?: string | null;
  /**
   * The parent film's Chinese title. The episode itself is never translated — its
   * own `title` is a site-generated placeholder — so this is the only Chinese a
   * scene card can show.
   */
  movie_title_zh?: string | null;
  studio_name?: string | null;
  release_year?: number | null;
  release_date?: string | null;
  episode_ordinal: number;
  episode_count: number;
  performers: EpisodeCastRef[];
}

export interface EpisodeLibraryResponse {
  items: EpisodeSummary[];
  total: number;
}

/** The episode library's filters, as the drawer edits them. */
export interface EpisodeFilterState {
  studio: string;
  /** Only episodes whose synopsis has been translated — the readable ones. */
  hasZh: boolean;
  hasPerformers: boolean;
}

/** Performer attributes that can be filtered on, keyed by API facet name. */
export type PerformerFacetKey =
  | 'bodyType'
  | 'hair'
  | 'eyes'
  | 'skin'
  | 'bodyHair'
  | 'facialHair'
  | 'dickSize'
  | 'foreskin';

export type PerformerSortBy =
  | 'movies_desc'
  | 'movies_asc'
  | 'name_asc'
  | 'id_desc'
  | 'id_asc';

export interface PerformerFilterState {
  /** Shares the navbar search box with the movie tab. */
  query: string;
  bodyType: string[];
  hair: string[];
  eyes: string[];
  skin: string[];
  bodyHair: string[];
  facialHair: string[];
  dickSize: string[];
  foreskin: string[];
  hasImage: boolean;
  minMovies: number | null;
  sortBy: PerformerSortBy;
}

export interface FacetValue {
  value: string;
  count: number;
}

export interface PerformerFacets {
  facets: Record<string, FacetValue[]>;
  total: number;
  withImage: number;
  /** Performers that have at least one attribute scraped. */
  enriched: number;
}

export interface TranslationStats {
  translation_total: number;
  translation_done: number;
  translation_failed: number;
  translation_pending: number;
  configured: boolean;
  provider: string;
  /** Name of the source in use (`translate_config.json` profile). */
  profile?: string;
  profile_label?: string;
  model: string;
  reason: string;
  running: boolean;
  job?: Record<string, unknown>;
}

/**
 * One saved translation source. `api_key` is deliberately absent: the backend
 * never sends the key to the client, only whether one is stored.
 */
export interface TranslationProfile {
  name: string;
  label: string;
  type: string;
  model: string;
  base_url: string;
  has_key: boolean;
  key_hint: string;
  active: boolean;
}

/** A vendor template the settings form can prefill (DeepSeek, Claude, ...). */
export interface TranslationPreset {
  id: string;
  label: string;
  type: string;
  base_url: string;
  model: string;
  hint?: string;
  needs_key?: boolean;
}

export interface TranslationProviders {
  profiles: TranslationProfile[];
  presets: TranslationPreset[];
  config_file: string;
}

/** Form payload for creating/updating a source. Omit `api_key` to keep the stored one. */
export interface TranslationProviderInput {
  name: string;
  type: string;
  label?: string;
  model?: string;
  base_url?: string;
  api_key?: string;
  active?: boolean;
}

export interface Episode {
  id: number;
  movie_id?: number | null;
  title: string;
  thumbnail_url?: string | null;
  description?: string | null;
  /** Machine translation of `description`; null until translated. */
  description_zh?: string | null;
  action_notes?: string | null;
  movie_title?: string | null;
  /** The parent film's Chinese title; see `EpisodeSummary.movie_title_zh`. */
  movie_title_zh?: string | null;
  studio_name?: string | null;
  release_year?: number | null;
  release_date?: string | null;
}

export interface FilterState {
  query: string;
  studio: string;
  /**
   * A single director's name. Matched through the movie_directors junction table
   * (so a film with several directors is found by any one of them), falling back
   * to exact equality on movies.director_name for libraries that have not been
   * through `scraper_v2.py --mode directors`.
   */
  director: string;
  yearMin: number | null;
  yearMax: number | null;
  category: string;
  sortBy: 'year_desc' | 'year_asc' | 'title_asc' | 'id_desc';
}

export type FavoriteType = 'movie' | 'performer' | 'studio' | 'director' | 'episode' | 'series';
export const FAVORITE_TYPES: FavoriteType[] = ['movie', 'performer', 'studio', 'director', 'episode', 'series'];

export interface SeriesCollectionItem {
  id: number;
  root_title: string;
  studio_name?: string | null;
  movie_count: number;
  cover_url?: string | null;
  sample_covers?: string[] | null;
  year_start?: number | null;
  year_end?: number | null;
  updated_at?: string | null;
}

export interface SeriesCollectionsResponse {
  items: SeriesCollectionItem[];
  total: number;
}

/**
 * One favorited item, shaped for the card that renders it. Which fields are present
 * depends on the type: movie/episode carry a title, performer a name, studio/director
 * just the name plus how many works the library holds for it.
 */
export interface FavoriteItem {
  key: string;
  created_at?: string;
  title?: string | null;
  /**
   * Chinese title of a favourited film, or of a favourited scene's parent film.
   * Without it the favourites tab is the one grid in the app still showing English
   * in Chinese mode.
   */
  title_zh?: string | null;
  name?: string;
  release_year?: number | null;
  studio_name?: string | null;
  cover_full?: string | null;
  covers?: string[] | null;
  has_zh?: boolean;
  image_url?: string | null;
  thumbnail_url?: string | null;
  movie_id?: number | null;
  movie_title?: string | null;
  works_count?: number;
  rating?: number | null;
  status?: string | null;
}

export interface FavoritesResponse extends Record<FavoriteType, FavoriteItem[]> {
  counts: Record<FavoriteType | 'wishlist' | 'watched', number>;
  wishlist?: FavoriteItem[];
  watched?: FavoriteItem[];
}

export interface DatabaseStats {
  movies: number;
  performers: number;
  episodes: number;
  movie_performers: number;
  movies_scraped_ok: number;
  movies_scraped_404: number;
  movies_failed_500?: number;
  performers_scraped_ok: number;
  performers_scraped_404?: number;
  performers_with_image?: number;
  translation_total?: number;
  translation_done?: number;
  translation_failed?: number;
  translation_pending?: number;
  translation_configured?: boolean;
}

export interface DatabaseInfo {
  path: string | null;
  exists: boolean;
  valid: boolean;
  file_size_mb: number;
  custom_path: string | null;
  candidates: string[];
}

export type AppTab =
  | 'home'
  | 'movies'
  | 'performers'
  | 'studios'
  | 'directors'
  | 'episodes'
  | 'favorites'
  | 'analytics'
  | 'plugins'
  | 'trophies'
  | 'settings';

export interface HomeSpotlightMovie {
  id: number;
  title: string;
  title_zh?: string | null;
  studio_name?: string | null;
  director_name?: string | null;
  release_year?: number | null;
  cover_full?: string | null;
  cover_back?: string | null;
  description_zh?: string | null;
  description?: string | null;
  rating?: string | null;
  category?: string | null;
}

export interface HomeAnniversaryItem {
  episode_id: number;
  episode_title: string;
  release_date: string;
  movie_id?: number | null;
  movie_title?: string | null;
  movie_title_zh?: string | null;
  cover_full?: string | null;
  studio_name?: string | null;
  years_ago: number;
}

export interface HomeFeaturedPerformer {
  id: number;
  name: string;
  image_url?: string | null;
  build?: string | null;
  hair?: string | null;
  works_count: number;
}

export interface HomeFeedData {
  spotlight_movies: HomeSpotlightMovie[];
  on_this_day: HomeAnniversaryItem[];
  star_spotlight: HomeFeaturedPerformer[];
  total_movies: number;
  total_episodes: number;
  total_performers: number;
  total_studios: number;
}

export type ScraperMode = 'incremental' | 'movies_boost' | 'movies_full' | 'performers_full';

export interface ScraperStatus {
  running: boolean;
  mode: string;
  current_id: number;
  target_total: number;
  processed_count: number;
  percent: number;
  current_title: string;
  new_movies: number;
  new_performers: number;
  new_episodes: number;
  speed_fps: number;
  eta_minutes: number;
  message: string;
  logs: string[];
  elapsed_secs: number;
  finished: boolean;
  error?: string | null;
}

export interface SyncResult {
  newMovies: number;
  newPerformers: number;
  newEpisodes: number;
}



