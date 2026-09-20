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
  performers?: Array<{ id: number; name: string; image_url?: string | null }>;
  episodes?: Episode[];
  userData?: UserMovieData | null;
  is_favorite?: boolean;
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
  episodes?: Episode[];
  episodes_count?: number;
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
  studio_name?: string | null;
  release_year?: number | null;
}

export interface FilterState {
  query: string;
  studio: string;
  /** No director table exists, so this filters on movies.director_name directly. */
  director: string;
  yearMin: number | null;
  yearMax: number | null;
  category: string;
  sortBy: 'year_desc' | 'year_asc' | 'title_asc' | 'id_desc';
}

/** The five kinds of thing that can be favorited. Values match `entity_type`. */
export type FavoriteType = 'movie' | 'performer' | 'studio' | 'director' | 'episode';

/** Every top-level view, i.e. everything the sidebar can switch to. */
export type AppTab = 'movies' | 'performers' | 'studios' | 'favorites' | 'settings';

export const FAVORITE_TYPES: FavoriteType[] = ['movie', 'performer', 'studio', 'director', 'episode'];

/**
 * One favorited item, shaped for the card that renders it. Which fields are present
 * depends on the type: movie/episode carry a title, performer a name, studio/director
 * just the name plus how many works the library holds for it.
 */
export interface FavoriteItem {
  key: string;
  created_at?: string;
  title?: string | null;
  name?: string;
  release_year?: number | null;
  studio_name?: string | null;
  cover_full?: string | null;
  has_zh?: boolean;
  image_url?: string | null;
  thumbnail_url?: string | null;
  movie_id?: number | null;
  movie_title?: string | null;
  works_count?: number;
}

export interface FavoritesResponse extends Record<FavoriteType, FavoriteItem[]> {
  counts: Record<FavoriteType, number>;
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

