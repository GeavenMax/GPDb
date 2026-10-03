import type {
  Movie,
  MovieSeriesResponse,
  Performer,
  FilterState,
  DatabaseStats,
  PerformerFilterState,
  PerformerFacets,
  TranslationStats,
  TranslationProfile,
  TranslationProviders,
  TranslationProviderInput,
  FavoriteType,
  FavoriteItem,
  FavoritesResponse,
  StudioLibraryResponse,
  StudioSortBy,
  StudioWorks,
  DirectorLibraryResponse,
  DirectorSortBy,
  DirectorWorks,
  EpisodeFilterState,
  EpisodeLibraryResponse,
  EpisodeSortBy,
  EpisodeSummary,
  DatabaseInfo,
  HomeFeedData,
  SeriesCollectionsResponse,
  ScraperStatus,
  ScraperMode,
  RuntimeEnvironmentInfo,
} from './types';
import { FAVORITE_TYPES } from './types';
import { pluginsConfig } from './services/pluginManager';

// Detect if running inside Tauri runtime
const isTauri = typeof window !== 'undefined' && ('__TAURI_INTERNALS__' in window || '__TAURI__' in window);

/**
 * True when the app talks to SQLite through Tauri commands rather than the local
 * Python server.
 *
 * The settings page works in both modes: managing translation sources is a file
 * edit, so it runs natively (`commands/translate.rs` on the same
 * `translate_config.json` the CLI reads). What stays server-side only is actually
 * *running* a translation, and the试译 button, because both need Python's providers.
 */
export const IS_TAURI = isTauri;

async function postProviderAction(
  action: 'save' | 'activate' | 'delete',
  payload: Record<string, unknown>
): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
  try {
    const res = await fetch(`/api/translate/providers/${action}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return await res.json();
  } catch (e: any) {
    return { success: false, error: e?.message || '请求失败' };
  }
}

async function tauriInvoke<T>(cmd: string, args: Record<string, unknown> = {}): Promise<T> {
  if (isTauri) {
    const { invoke } = await import('@tauri-apps/api/core');
    return invoke<T>(cmd, args);
  }
  throw new Error('Not in Tauri environment');
}

/**
 * Run a translation-source command through Tauri, reporting the result the way the
 * HTTP branch does.
 *
 * The Rust commands return the updated source list directly and reject with a plain
 * message string, whereas the Python endpoints answer `{success, profiles, error}`.
 * Adapting here means the settings UI keeps one shape to handle in both modes.
 */
async function tauriProviderAction(
  cmd: string,
  args: Record<string, unknown>
): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
  try {
    return { success: true, profiles: await tauriInvoke<TranslationProfile[]>(cmd, args) };
  } catch (e: any) {
    return { success: false, error: typeof e === 'string' ? e : e?.message || '操作失败' };
  }
}

function emptyFavoriteGroups(): Record<FavoriteType, FavoriteItem[]> {
  return { movie: [], performer: [], studio: [], director: [], episode: [], series: [] };
}

function emptyFavoriteKeys(): Record<FavoriteType, string[]> {
  return { movie: [], performer: [], studio: [], director: [], episode: [], series: [] };
}

/** Add the per-type counts the server computes, for the groups Rust hands back. */
function withCounts(groups: Partial<Record<FavoriteType, FavoriteItem[]>> & { wishlist?: FavoriteItem[]; watched?: FavoriteItem[] }): FavoritesResponse {
  const merged = { ...emptyFavoriteGroups(), wishlist: [], watched: [], ...groups };
  const counts = {} as Record<FavoriteType | 'wishlist' | 'watched', number>;
  for (const t of FAVORITE_TYPES) counts[t] = merged[t].length;
  counts.wishlist = (merged.wishlist || []).length;
  counts.watched = (merged.watched || []).length;
  return { ...merged, counts };
}

/** Reduce grouped favorites to just their keys, for heart-button colouring. */
function keysFromGroups(groups: FavoritesResponse): Record<FavoriteType, string[]> {
  const keys = emptyFavoriteKeys();
  for (const t of FAVORITE_TYPES) keys[t] = groups[t].map((item) => item.key);
  return keys;
}

/**
 * Fetch the grouped favorites. Throws rather than falling back to an empty list:
 * the caller uses the result to refresh its heart cache, so a silent empty result
 * would blank out every heart in the UI when the server is merely unreachable.
 */
async function fetchFavorites(): Promise<FavoritesResponse> {
  if (isTauri) {
    const res = await tauriInvoke<FavoritesResponse>('get_favorites');
    return withCounts(res);
  }
  const res = await fetch('/api/user/favorites');
  if (!res.ok) throw new Error(`收藏列表加载失败 (HTTP ${res.status})`);
  return await res.json();
}

/** Facet keys that map 1:1 onto repeatable query parameters. */
export const FACET_KEYS = [
  'bodyType',
  'hair',
  'eyes',
  'skin',
  'bodyHair',
  'facialHair',
  'dickSize',
  'foreskin',
] as const;

/** The API facet name for an attribute column, mirroring server.py PERFORMER_FACETS. */
export const FACET_COLUMN: Record<string, string> = {
  bodyType: 'build',
  hair: 'hair',
  eyes: 'eyes',
  skin: 'skin',
  bodyHair: 'body_hair',
  facialHair: 'facial_hair',
  dickSize: 'dick_size',
  foreskin: 'foreskin',
};

export const FACET_LABELS: Record<string, string> = {
  bodyType: '体型',
  hair: '发色',
  eyes: '瞳色',
  skin: '肤色',
  bodyHair: '体毛',
  facialHair: '胡须',
  dickSize: '尺寸',
  foreskin: '包皮',
};

/**
 * The film tab's blank state. Only ever needed to *reset* to — the grid reads the
 * fields directly — but it lives here with the other two so the three tabs reset
 * the same way. `FilterState` has no sort-independent "active" counter, so this
 * object doubles as the comparison baseline.
 */
export function createMovieFilters(): FilterState {
  return {
    query: '',
    studio: '',
    director: '',
    yearMin: null,
    yearMax: null,
    category: '',
    sortBy: 'year_desc',
    dateFilter: 'all',
  };
}

export function createPerformerFilters(): PerformerFilterState {
  return {
    query: '',
    bodyType: [],
    hair: [],
    eyes: [],
    skin: [],
    bodyHair: [],
    facialHair: [],
    dickSize: [],
    foreskin: [],
    hasImage: false,
    minMovies: null,
    sortBy: 'movies_desc',
  };
}

export function countActivePerformerFilters(f: PerformerFilterState): number {
  let n = FACET_KEYS.reduce((acc, k) => acc + (f[k]?.length || 0), 0);
  if (f.hasImage) n++;
  if (f.minMovies != null) n++;
  if (f.sortBy !== 'movies_desc') n++;
  return n;
}

export function createEpisodeFilters(): EpisodeFilterState {
  return { studio: '', hasZh: false, hasPerformers: false, dateFilter: 'all' };
}

export function countActiveEpisodeFilters(f: EpisodeFilterState, sortBy: EpisodeSortBy): number {
  let n = 0;
  if (f.studio) n++;
  if (f.hasZh) n++;
  if (f.hasPerformers) n++;
  if (f.dateFilter && f.dateFilter !== 'all') n++;
  if (sortBy !== 'id_desc') n++;
  return n;
}

/** Sort options for the episode library's drawer, in the order they are offered. */
export const EPISODE_SORTS: Array<{ id: EpisodeSortBy; label: string }> = [
  { id: 'id_desc', label: '最新入库' },
  { id: 'year_desc', label: '影片年份' },
  { id: 'movie_asc', label: '影片名 A-Z' },
];

export const api = {
  async getStats(): Promise<DatabaseStats> {
    if (isTauri) {
      return tauriInvoke<DatabaseStats>('get_stats');
    }
    // Fallback for browser dev
    try {
      const res = await fetch('/api/stats');
      if (res.ok) return await res.json();
    } catch {}
    return {
      movies: 20,
      performers: 176,
      episodes: 5,
      movie_performers: 151,
      movies_scraped_ok: 55,
      movies_scraped_404: 249,
      performers_scraped_ok: 176,
    };
  },

  async getMovies(
    filters: Partial<FilterState> = {},
    page: number = 1,
    pageSize: number = 24
  ): Promise<{ items: Movie[]; total: number }> {
    if (isTauri) {
      return tauriInvoke<{ items: Movie[]; total: number }>('get_movies', {
        filters,
        page,
        pageSize,
      });
    }

    try {
      const params = new URLSearchParams({
        query: filters.query || '',
        studio: filters.studio || '',
        director: filters.director || '',
        category: filters.category || '',
        sortBy: filters.sortBy || 'year_desc',
        page: String(page),
        pageSize: String(pageSize),
      });
      if (filters.yearMin != null) params.set('yearMin', String(filters.yearMin));
      if (filters.yearMax != null) params.set('yearMax', String(filters.yearMax));
      const res = await fetch(`/api/movies?${params.toString()}`);
      if (res.ok) return await res.json();
    } catch {}

    // Mock response for quick dev preview
    return {
      items: [
        {
          id: 75178,
          title: 'Frat Bros Go Bi',
          studio_name: 'Corbin Fisher',
          release_year: 2026,
          duration_mins: 60,
          category: 'Bisexual',
          rating: 'non rated',
          cover_full: 'https://gayeroticvideoindex.com/images/Covers/8/video75178.jpg',
          cover_icon: 'https://gayeroticvideoindex.com/images/Covers/Icons/8/video75178.jpg',
          description: 'The dorms are full, classes are in full swing, rush week is just around the corner...',
          performers: [
            { id: 146520, name: 'Clay (cf2)' },
            { id: 104315, name: 'Kennedy (cf)' },
            { id: 91300, name: 'James (cf2)' },
          ],
        },
        {
          id: 75190,
          title: 'Dirty Mates',
          studio_name: 'Staxus',
          release_year: 2026,
          duration_mins: 104,
          category: 'General Hardcore',
          cover_full: 'https://gayeroticvideoindex.com/images/Covers/0/video75190.jpg',
          cover_icon: 'https://gayeroticvideoindex.com/images/Covers/Icons/0/video75190.jpg',
          description: 'A wild, energetic encounter featuring muscular all-stars.',
          performers: [
            { id: 54579, name: 'Patrick Leone' },
            { id: 136003, name: 'Marcus Ashton' },
          ],
        },
        {
          id: 75183,
          title: 'Gay Casting Couch 36',
          studio_name: 'Driveshaft',
          release_year: 2026,
          duration_mins: 161,
          category: 'General Hardcore',
          cover_full: 'https://gayeroticvideoindex.com/images/Covers/3/video75183.jpg',
          cover_icon: 'https://gayeroticvideoindex.com/images/Covers/Icons/3/video75183.jpg',
          description: 'Justin Beal is breaking in 4 fresh talents.',
          performers: [
            { id: 59812, name: 'Justin Beal' },
            { id: 85991, name: 'Garrett Cooper' },
          ],
        },
      ],
      total: 3,
    };
  },

  async getMovieDetail(id: number): Promise<Movie | null> {
    if (isTauri) {
      return tauriInvoke<Movie | null>('get_movie_detail', { id });
    }
    try {
      const res = await fetch(`/api/movies/${id}`);
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  async getMovieSeries(id: number): Promise<MovieSeriesResponse | null> {
    if (isTauri) {
      return tauriInvoke<MovieSeriesResponse | null>('get_movie_series', { id });
    }
    try {
      const res = await fetch(`/api/movies/${id}/series`);
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  async getMovieSeriesByRoot(rootTitle: string, studioName?: string | null): Promise<MovieSeriesResponse | null> {
    if (isTauri) {
      return tauriInvoke<MovieSeriesResponse | null>('get_movie_series_by_root', {
        rootTitle,
        studioName: studioName || null,
      });
    }
    return null;
  },

  async getSeriesCollections(
    query?: string,
    studio?: string,
    sortBy?: string,
    page: number = 1,
    pageSize: number = 24
  ): Promise<SeriesCollectionsResponse> {
    if (isTauri) {
      return tauriInvoke<SeriesCollectionsResponse>('get_series_collections', {
        query: query || null,
        studio: studio || null,
        sortBy: sortBy || null,
        page,
        pageSize,
      });
    }
    return { items: [], total: 0 };
  },

  async getPerformers(
    filters: Partial<PerformerFilterState> = {},
    page: number = 1,
    pageSize: number = 24
  ): Promise<{ items: Performer[]; total: number }> {
    if (isTauri) {
      return tauriInvoke<{ items: Performer[]; total: number }>('get_performers', {
        filters,
        page,
        pageSize,
      });
    }
    try {
      const params = new URLSearchParams({
        page: String(page),
        pageSize: String(pageSize),
        sortBy: filters.sortBy || 'movies_desc',
      });
      if (filters.query) params.set('query', filters.query);
      if (filters.hasImage) params.set('hasImage', '1');
      if (filters.minMovies != null) params.set('minMovies', String(filters.minMovies));

      // Multi-select facets repeat the same key; the server OR-s within a key
      // and AND-s across keys.
      for (const key of FACET_KEYS) {
        for (const value of filters[key] || []) {
          params.append(key, value);
        }
      }

      const res = await fetch(`/api/performers?${params.toString()}`);
      if (res.ok) return await res.json();
    } catch {}
    return { items: [], total: 0 };
  },

  async getPerformerFacets(): Promise<PerformerFacets> {
    if (isTauri) {
      return tauriInvoke<PerformerFacets>('get_performer_facets');
    }
    try {
      const res = await fetch('/api/performers/facets');
      if (res.ok) return await res.json();
    } catch {}
    return { facets: {}, total: 0, withImage: 0, enriched: 0 };
  },

  async getPerformerDetail(id: number): Promise<Performer | null> {
    if (isTauri) {
      return tauriInvoke<Performer | null>('get_performer_detail', { id });
    }
    try {
      const res = await fetch(`/api/performers/${id}`);
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  async getStudios(): Promise<string[]> {
    if (isTauri) {
      return tauriInvoke<string[]>('get_studios');
    }
    try {
      const res = await fetch('/api/studios');
      if (res.ok) return await res.json();
    } catch {}
    return ['Corbin Fisher', 'Staxus', 'Driveshaft', 'Adult Time', 'Sean Cody', 'Bareback Network'];
  },

  /**
   * The studio library grid, paged and searchable.
   *
   * Deliberately not `getStudios()`: that one returns a bare name list for the
   * filter drawer's chips (capped at 200, names only), which is a different query
   * from a paged list with per-studio counts.
   */
  async getStudioLibrary(
    query: string = '',
    sortBy: StudioSortBy = 'works_desc',
    page: number = 1,
    pageSize: number = 24
  ): Promise<StudioLibraryResponse> {
    if (isTauri) {
      return tauriInvoke<StudioLibraryResponse>('get_studio_library', {
        query, sortBy, page, pageSize,
      });
    }
    try {
      const params = new URLSearchParams({
        page: String(page),
        pageSize: String(pageSize),
        sortBy,
      });
      if (query) params.set('query', query);
      const res = await fetch(`/api/studio-library?${params.toString()}`);
      if (res.ok) return await res.json();
    } catch {}
    return { items: [], total: 0 };
  },

  /**
   * The episode library grid, paged, searchable and filterable.
   *
   * Unlike `getStudioWorks()` / `getPerformerDetail()`, which return the episodes of
   * one film or one performer, this is the whole episodes table.
   */
  async getEpisodeLibrary(
    query: string = '',
    sortBy: EpisodeSortBy = 'id_desc',
    filters: EpisodeFilterState = createEpisodeFilters(),
    page: number = 1,
    pageSize: number = 24
  ): Promise<EpisodeLibraryResponse> {
    if (isTauri) {
      return tauriInvoke<EpisodeLibraryResponse>('get_episode_library', {
        query,
        sort: sortBy,
        studio: filters.studio,
        hasZh: filters.hasZh,
        hasPerformers: filters.hasPerformers,
        dateFilter: filters.dateFilter,
        page,
        pageSize,
      });
    }
    try {
      const params = new URLSearchParams({
        page: String(page),
        pageSize: String(pageSize),
        sort: sortBy,
      });
      if (query) params.set('q', query);
      if (filters.studio) params.set('studio', filters.studio);
      if (filters.hasZh) params.set('hasZh', '1');
      if (filters.hasPerformers) params.set('hasPerformers', '1');
      if (filters.dateFilter) params.set('dateFilter', filters.dateFilter);
      const res = await fetch(`/api/episode-library?${params.toString()}`);
      if (res.ok) return await res.json();
    } catch {}
    return { items: [], total: 0 };
  },

  async getEpisodeDetail(id: number): Promise<EpisodeSummary | null> {
    if (isTauri) {
      return tauriInvoke<EpisodeSummary>('get_episode_detail', { id });
    }
    try {
      const res = await fetch(`/api/episodes/${id}`);
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  async getCategories(): Promise<string[]> {
    if (isTauri) {
      return tauriInvoke<string[]>('get_categories');
    }
    try {
      const res = await fetch('/api/categories');
      if (res.ok) return await res.json();
    } catch {}
    return ['General Hardcore', 'Bisexual', 'Bareback', 'Twink', 'All-Male', 'Muscle'];
  },

  async runSync(): Promise<{ newMovies: number; newPerformers: number; newEpisodes: number }> {
    if (isTauri) {
      return tauriInvoke<{ newMovies: number; newPerformers: number; newEpisodes: number }>('run_sync');
    }
    try {
      const res = await fetch('/api/sync', { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {}
    return { newMovies: 0, newPerformers: 0, newEpisodes: 0 };
  },

  // Asynchronous Scraper & Sync Engine
  async startScraper(
    mode: ScraperMode,
    limit?: number,
    startId?: number,
    endId?: number
  ): Promise<ScraperStatus> {
    if (isTauri) {
      return tauriInvoke<ScraperStatus>('start_scraper', {
        mode,
        limit: limit ?? null,
        startId: startId ?? null,
        endId: endId ?? null,
      });
    }
    throw new Error('后台异步刮削引擎仅在桌面端可用');
  },

  async stopScraper(): Promise<ScraperStatus> {
    if (isTauri) {
      return tauriInvoke<ScraperStatus>('stop_scraper');
    }
    throw new Error('后台异步刮削引擎仅在桌面端可用');
  },

  async getScraperStatus(): Promise<ScraperStatus> {
    if (isTauri) {
      return tauriInvoke<ScraperStatus>('get_scraper_status');
    }
    return {
      running: false,
      mode: 'idle',
      current_id: 0,
      target_total: 0,
      processed_count: 0,
      percent: 0,
      current_title: '',
      new_movies: 0,
      new_performers: 0,
      new_episodes: 0,
      speed_fps: 0,
      eta_minutes: 0,
      message: '就绪',
      logs: [],
      elapsed_secs: 0,
      finished: false,
    };
  },


  // Image Disk Cache Management
  async getCacheStats(): Promise<{ count: number; size_mb: number; path: string }> {
    if (isTauri) {
      try {
        return await tauriInvoke<{ count: number; size_mb: number; path: string }>('get_cache_stats');
      } catch (err) {
        console.warn('Tauri get_cache_stats error, falling back to HTTP:', err);
      }
    }
    try {
      const res = await fetch('http://127.0.0.1:8787/api/cache/stats');
      if (res.ok) return await res.json();
    } catch {}
    try {
      const res = await fetch('/api/cache/stats');
      if (res.ok) return await res.json();
    } catch {}
    return { count: 0, size_mb: 0, path: '' };
  },

  async clearCache(): Promise<{ success: boolean; cleared_files: number }> {
    if (isTauri) {
      try {
        const cleared = await tauriInvoke<number>('clear_cache');
        return { success: true, cleared_files: cleared };
      } catch (err) {
        console.warn('Tauri clear_cache error, falling back to HTTP:', err);
      }
    }
    try {
      const res = await fetch('http://127.0.0.1:8787/api/cache/clear', { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {}
    try {
      const res = await fetch('/api/cache/clear', { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {}
    return { success: false, cleared_files: 0 };
  },

  async downloadAllCache(): Promise<{ success: boolean; message: string }> {
    try {
      const res = await fetch('http://127.0.0.1:8787/api/cache/download_all', { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {}
    try {
      const res = await fetch('/api/cache/download_all', { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {}
    return { success: false, message: 'Failed to start' };
  },

  // User Custom Tags
  async getUserTags(): Promise<Array<{ id: number; name: string; color: string }>> {
    if (isTauri) {
      try {
        const tags = await tauriInvoke<Array<{ id: number; name: string; color?: string }>>('get_user_tags');
        return tags.map(t => ({ id: t.id, name: t.name, color: t.color || '#6366f1' }));
      } catch {
        return [];
      }
    }
    try {
      const res = await fetch('/api/user/tags');
      if (res.ok) return await res.json();
    } catch {}
    return [];
  },

  async createUserTag(name: string, color: string = '#f59e0b'): Promise<{ id: number; name: string; color: string } | null> {
    if (isTauri) {
      try {
        const tag = await tauriInvoke<{ id: number; name: string; color?: string }>('create_user_tag', { name, color });
        return { id: tag.id, name: tag.name, color: tag.color || color };
      } catch {
        return null;
      }
    }
    try {
      const res = await fetch('/api/user/tags', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, color }),
      });
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  async deleteUserTag(tagId: number): Promise<boolean> {
    try {
      const res = await fetch(`/api/user/tags/${tagId}/delete`, { method: 'POST' });
      return res.ok;
    } catch {}
    return false;
  },

  // User Movie Annotations (Rating, Status, Notes, Tags)
  async getMovieUserData(movieId: number): Promise<any> {
    if (isTauri) {
      try {
        return await tauriInvoke('get_movie_user_data', { movieId });
      } catch {
        return null;
      }
    }
    try {
      const res = await fetch(`/api/movies/${movieId}/user_data`);
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  async saveMovieUserData(
    movieId: number,
    data: { rating?: number | null; status?: string | null; notes?: string; tag_ids?: number[] }
  ): Promise<any> {
    if (isTauri) {
      try {
        return await tauriInvoke('save_movie_user_data', { movieId, data });
      } catch {
        return null;
      }
    }
    try {
      const res = await fetch(`/api/movies/${movieId}/user_data`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      });
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  // Import / Export
  async exportUserData(): Promise<any> {
    if (isTauri) {
      try {
        return await tauriInvoke('export_user_data');
      } catch (e) {
        console.warn('tauri export_user_data failed', e);
        return null;
      }
    }
    try {
      const res = await fetch('/api/user/export');
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  async exportUserDataFile(): Promise<string | null> {
    if (isTauri) {
      return tauriInvoke<string | null>('export_user_data_file');
    }
    return null;
  },

  async importUserData(data: any): Promise<any> {
    if (isTauri) {
      try {
        return await tauriInvoke('import_user_data', { data });
      } catch (e: any) {
        console.warn('tauri import_user_data failed', e);
        throw e;
      }
    }
    try {
      const res = await fetch('/api/user/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      });
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  // Machine Translation (synopsis EN -> ZH)
  async getTranslationStats(): Promise<TranslationStats> {
    if (isTauri) {
      // The desktop build reads SQLite directly, so the counts come from
      // get_stats. Translation itself still runs through translate.py; there is
      // no in-app job to poll, so `running` stays false and the UI says so.
      const s = await tauriInvoke<DatabaseStats>('get_stats');
      return {
        translation_total: s.translation_total || 0,
        translation_done: s.translation_done || 0,
        translation_failed: s.translation_failed || 0,
        translation_pending: s.translation_pending || 0,
        configured: false,
        provider: '',
        model: '',
        reason: '',
        running: false,
      };
    }
    try {
      const res = await fetch('/api/translate/stats');
      if (res.ok) return await res.json();
    } catch {}
    return {
      translation_total: 0,
      translation_done: 0,
      translation_failed: 0,
      translation_pending: 0,
      configured: false,
      provider: '',
      model: '',
      reason: '无法连接本地服务',
      running: false,
    };
  },

  async runTranslation(
    limit: number | null = null,
    batchSize = 20,
    workers = 4,
    profile: string | null = null
  ): Promise<{ success: boolean; message?: string; error?: string }> {
    try {
      const res = await fetch('/api/translate/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ limit, batchSize, workers, profile }),
      });
      return await res.json();
    } catch (e: any) {
      return { success: false, error: e?.message || '请求失败' };
    }
  },

  // Translation sources the user can switch between. The API key is never part of
  // these payloads — neither backend reports one, only whether it is stored.
  async getTranslationProviders(): Promise<TranslationProviders> {
    const empty: TranslationProviders = { profiles: [], presets: [], config_file: '' };
    if (isTauri) {
      try {
        return await tauriInvoke<TranslationProviders>('get_translation_providers');
      } catch {
        return empty;
      }
    }
    try {
      const res = await fetch('/api/translate/providers');
      if (res.ok) return await res.json();
    } catch {}
    return empty;
  },

  async saveTranslationProvider(payload: TranslationProviderInput): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
    if (isTauri) return tauriProviderAction('save_translation_provider', { input: payload });
    return postProviderAction('save', { ...payload });
  },

  async activateTranslationProvider(name: string): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
    if (isTauri) return tauriProviderAction('activate_translation_provider', { name });
    return postProviderAction('activate', { name });
  },

  async deleteTranslationProvider(name: string): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
    if (isTauri) return tauriProviderAction('delete_translation_provider', { name });
    return postProviderAction('delete', { name });
  },

  /**
   * Round-trip one short string so the user can verify a key before a big run.
   *
   * Still server-side only: it makes a real outbound call through `translate.py`'s
   * providers, which live in Python. Desktop builds get a message pointing at the
   * command line rather than the generic "request failed" that a fetch rejection
   * would produce.
   */
  async testTranslationProvider(name: string | null): Promise<{
    success: boolean; profile?: string; model?: string; elapsed?: number;
    source?: string; result?: string; error?: string;
  }> {
    if (isTauri) {
      try {
        const res = await tauriInvoke<{
          profile: string;
          model?: string;
          elapsed?: number;
          source: string;
          result: string;
          error?: string;
        }>('test_translation_provider', { name: name || null });
        if (res.error) {
          return { success: false, ...res };
        }
        return { success: Boolean(res.result), ...res };
      } catch (err: any) {
        return {
          success: false,
          profile: name || '',
          error: String(err?.message || err || '测试翻译失败'),
        };
      }
    }
    try {
      const res = await fetch('/api/translate/providers/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name }),
      });
      return await res.json();
    } catch (e: any) {
      return { success: false, error: e?.message || '请求失败' };
    }
  },

  async postProviderAction(
    action: 'save' | 'activate' | 'delete',
    payload: Record<string, unknown>
  ): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
    return postProviderAction(action, payload);
  },

  async exportTranslations(): Promise<string> {
    if (isTauri) {
      return tauriInvoke<string>('export_translations');
    }
    const res = await fetch('/api/translate/export');
    if (!res.ok) throw new Error('导出翻译失败');
    return await res.text();
  },

  async importTranslations(jsonContent: string): Promise<{ movies_updated: number; episodes_updated: number; total_updated: number }> {
    if (isTauri) {
      return tauriInvoke<{ movies_updated: number; episodes_updated: number; total_updated: number }>('import_translations', { jsonContent });
    }
    const res = await fetch('/api/translate/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: jsonContent,
    });
    if (!res.ok) throw new Error('导入翻译失败');
    return await res.json();
  },

  async fetchProviderModels(providerName: string): Promise<string[]> {
    if (isTauri) {
      return tauriInvoke<string[]>('fetch_provider_models', { providerName });
    }
    const res = await fetch(`/api/translate/providers/${encodeURIComponent(providerName)}/models`);
    if (!res.ok) throw new Error('获取模型列表失败');
    return await res.json();
  },

  /**
   * Translate a film's synopsis on demand.
   *
   * The server folds this film's untranslated episode synopses into the same
   * request, so one call returns everything the detail modal needs. Returns null
   * on failure (the caller shows the settings hint).
   */
  async translateMovie(movieId: number, customPrompt?: string): Promise<{
    description_zh: string | null;
    episodes: Array<{ id: number; description_zh: string }>;
    error?: string;
  } | null> {
    if (isTauri) {
      try {
        const prompt = customPrompt || pluginsConfig.value.translationConfig?.customPromptTemplate || null;
        const res = await tauriInvoke<{
          id: number;
          description_zh: string | null;
          episodes: Array<{ id: number; description_zh: string }>;
        }>('translate_movie', { movieId, customPrompt: prompt });
        return {
          description_zh: res?.description_zh || null,
          episodes: Array.isArray(res?.episodes) ? res.episodes : [],
        };
      } catch (e: any) {
        console.error('Tauri translate_movie failed:', e);
        const errMsg = typeof e === 'string' ? e : (e?.message || '翻译失败');
        return {
          description_zh: null,
          episodes: [],
          error: errMsg,
        };
      }
    }
    try {
      const res = await fetch(`/api/movies/${movieId}/translate`, { method: 'POST' });
      const body = await res.json();
      if (!res.ok || body?.error) {
        return {
          description_zh: null,
          episodes: [],
          error: body?.error || `HTTP ${res.status}`,
        };
      }
      return {
        description_zh: body?.description_zh || null,
        episodes: Array.isArray(body?.episodes) ? body.episodes : [],
      };
    } catch (e: any) {
      return {
        description_zh: null,
        episodes: [],
        error: e?.message || '网络请求失败',
      };
    }
  },

  /**
   * Translate a single episode's synopsis on demand.
   */
  async translateEpisode(episodeId: number, customPrompt?: string): Promise<{
    id: number;
    movie_id?: number | null;
    description_zh: string | null;
    error?: string;
  } | null> {
    if (isTauri) {
      try {
        const prompt = customPrompt || pluginsConfig.value.translationConfig?.customPromptTemplate || null;
        const res = await tauriInvoke<{
          id: number;
          movie_id?: number | null;
          description_zh: string | null;
        }>('translate_episode', { episodeId, customPrompt: prompt });
        return {
          id: res?.id ?? episodeId,
          movie_id: res?.movie_id ?? null,
          description_zh: res?.description_zh || null,
        };
      } catch (e: any) {
        console.error('Tauri translate_episode failed:', e);
        const errMsg = typeof e === 'string' ? e : (e?.message || '翻译失败');
        return {
          id: episodeId,
          movie_id: null,
          description_zh: null,
          error: errMsg,
        };
      }
    }
    try {
      const res = await fetch(`/api/episodes/${episodeId}/translate`, { method: 'POST' });
      const body = await res.json();
      if (!res.ok || body?.error) {
        return {
          id: episodeId,
          movie_id: null,
          description_zh: null,
          error: body?.error || `HTTP ${res.status}`,
        };
      }
      return {
        id: body?.id ?? episodeId,
        movie_id: body?.movie_id ?? null,
        description_zh: body?.description_zh || null,
      };
    } catch (e: any) {
      return {
        id: episodeId,
        movie_id: null,
        description_zh: null,
        error: e?.message || '网络请求失败',
      };
    }
  },

  // --- Favorites (five entity types; see schema.sql §9) ---
  //
  // SQLite is the single source of truth: the browser talks to the local server,
  // the desktop build reads and writes the same file through Rust. There is no
  // localStorage copy and no double write, so the two paths cannot drift.

  /** Favorited keys grouped by type — enough to colour every heart in the UI. */
  async getFavoriteKeys(): Promise<Record<FavoriteType, string[]>> {
    if (isTauri) {
      // Rust exposes only the enriched query, so reduce it here rather than add a
      // second command for what is a few dozen rows at most.
      return keysFromGroups(await fetchFavorites());
    }
    try {
      const res = await fetch('/api/user/favorites/keys');
      if (res.ok) return await res.json();
    } catch {}
    return emptyFavoriteKeys();
  },

  /** Favorited items enriched for display, grouped by type. Drives the favorites page. */
  async getFavorites(): Promise<FavoritesResponse> {
    return fetchFavorites();
  },

  /** Flip one favorite. Returns whether it is favorited afterwards. */
  async toggleFavorite(type: FavoriteType, key: string): Promise<boolean> {
    if (isTauri) {
      return tauriInvoke<boolean>('toggle_favorite', { entityType: type, entityKey: key });
    }
    const res = await fetch('/api/user/favorites', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ entity_type: type, entity_key: key, action: 'toggle' }),
    });
    const body = await res.json();
    if (!res.ok || body?.error) throw new Error(body?.error || '收藏操作失败');
    return Boolean(body.is_favorite);
  },

  /**
   * Both translation glossaries, fetched once at startup: performer attributes
   * (en → zh) and film categories (term → zh).
   *
   * Two maps rather than one because the two vocabularies are separate tables and
   * `Muscle` / `Twink` are plausible as either.
   *
   * The desktop build reads the same two tables through its own Rust command. It used
   * to have no branch here at all, so it fell through to `fetch` with no HTTP server
   * behind it and quietly returned `{}` — which is why every performer attribute
   * showed in English there.
   */
  async getGlossary(): Promise<{ terms: Record<string, string>; categories: Record<string, string> }> {
    if (isTauri) {
      try {
        return await tauriInvoke<{ terms: Record<string, string>; categories: Record<string, string> }>(
          'get_glossaries',
        );
      } catch {
        return { terms: {}, categories: {} };
      }
    }
    try {
      const res = await fetch('/api/glossary');
      if (res.ok) {
        const body = await res.json();
        return { terms: body?.terms || {}, categories: body?.categories || {} };
      }
    } catch {}
    return { terms: {}, categories: {} };
  },

  /** Translate the whole attribute vocabulary in one API call. */
  async runGlossaryTranslation(dryRun: boolean = false): Promise<{
    success: boolean;
    total?: number;
    translated?: number;
    failed?: number;
    pending?: number;
    error?: string;
  }> {
    try {
      const res = await fetch('/api/translate/glossary/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dry_run: dryRun }),
      });
      return await res.json();
    } catch (e: any) {
      return { success: false, error: e?.message || '请求失败' };
    }
  },

  // Studio Works (Separated Movies & Episodes)
  async getStudioWorks(studioName: string): Promise<StudioWorks> {
    if (isTauri) {
      // Without this branch the desktop build fell through to fetch() and got the
      // empty fallback below, because there is no HTTP server in Tauri.
      return tauriInvoke<StudioWorks>('get_studio_works', { studioName });
    }
    try {
      const res = await fetch(`/api/studios/${encodeURIComponent(studioName)}/works`);
      if (res.ok) return await res.json();
    } catch {}
    return { studio_name: studioName, studio_name_zh: null, description_zh: null, movies: [], movies_count: 0, episodes: [], episodes_count: 0 };
  },

  /**
   * The director library grid, paged and searchable by name.
   *
   * The director analogue of `getStudioLibrary()`, and the same warning applies:
   * both branches are needed. A missing Tauri branch silently renders the empty
   * fallback in the desktop build, and a missing HTTP branch does the same in the
   * browser, with no error either way.
   */
  async getDirectorLibrary(
    query: string = '',
    sortBy: DirectorSortBy = 'works_desc',
    page: number = 1,
    pageSize: number = 24
  ): Promise<DirectorLibraryResponse> {
    if (isTauri) {
      return tauriInvoke<DirectorLibraryResponse>('get_director_library', {
        query, sortBy, page, pageSize,
      });
    }
    try {
      const params = new URLSearchParams({
        page: String(page),
        pageSize: String(pageSize),
        sortBy,
      });
      if (query) params.set('query', query);
      const res = await fetch(`/api/director-library?${params.toString()}`);
      if (res.ok) return await res.json();
    } catch {}
    return { items: [], total: 0 };
  },

  /**
   * One director's films, for the detail modal. Keyed by name, because that is how
   * `user_favorites` stores a director.
   *
   * An unknown name is not an error: a favorite saved before the director roster was
   * parsed can hold a whole glued string, and the endpoint answers with no films.
   */
  async getDirectorWorks(directorName: string): Promise<DirectorWorks> {
    if (isTauri) {
      return tauriInvoke<DirectorWorks>('get_director_works', { directorName });
    }
    try {
      const res = await fetch(`/api/directors/${encodeURIComponent(directorName)}/works`);
      if (res.ok) return await res.json();
    } catch {}
    return { name: directorName, movies: [], movies_count: 0 };
  },

  async getDatabaseInfo(): Promise<DatabaseInfo> {
    if (isTauri) {
      return tauriInvoke<DatabaseInfo>('get_database_info');
    }
    try {
      const res = await fetch('/api/database/info');
      if (res.ok) return await res.json();
    } catch {}
    return {
      path: null,
      exists: false,
      valid: false,
      file_size_mb: 0,
      custom_path: null,
      candidates: [],
    };
  },

  async createNewDatabase(targetPath?: string): Promise<DatabaseInfo> {
    if (isTauri) {
      return tauriInvoke<DatabaseInfo>('create_new_database', { targetPath });
    }
    return this.getDatabaseInfo();
  },

  async setCustomDatabasePath(path: string): Promise<DatabaseInfo> {
    if (isTauri) {
      return tauriInvoke<DatabaseInfo>('set_custom_database_path', { path });
    }
    const res = await fetch('/api/database/set-path', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path }),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || '设置数据库路径失败');
    }
    return await res.json();
  },

  async scanDatabases(): Promise<string[]> {
    if (isTauri) {
      return tauriInvoke<string[]>('scan_databases');
    }
    try {
      const res = await fetch('/api/database/scan');
      if (res.ok) return await res.json();
    } catch {}
    return [];
  },

  async pickDatabaseFile(): Promise<string | null> {
    if (isTauri) {
      return tauriInvoke<string | null>('pick_database_file');
    }
    return null;
  },

  async exportDatabaseFile(): Promise<string | null> {
    if (isTauri) {
      return tauriInvoke<string | null>('export_database_file');
    }
    return null;
  },

  async setDockIcon(schemeId: string): Promise<boolean> {
    if (isTauri) {
      try {
        await tauriInvoke('set_dock_icon', { schemeId });
        return true;
      } catch (e) {
        console.warn('set_dock_icon failed', e);
        return false;
      }
    }
    return false;
  },

  async setWindowMaterial(material: string): Promise<boolean> {
    if (isTauri) {
      try {
        await tauriInvoke('set_window_material', { material });
        return true;
      } catch (e) {
        console.warn('set_window_material failed', e);
        return false;
      }
    }
    return false;
  },

  async setCloseToTray(enabled: boolean): Promise<boolean> {
    if (isTauri) {
      try {
        await tauriInvoke('set_close_to_tray', { enabled });
        return true;
      } catch (e) {
        console.warn('set_close_to_tray failed', e);
        return false;
      }
    }
    return false;
  },

  async getCloseToTray(): Promise<boolean> {
    if (isTauri) {
      try {
        return await tauriInvoke<boolean>('get_close_to_tray');
      } catch {
        return false;
      }
    }
    return false;
  },

  async setTaskbarProgress(progress: number | null, status: 'normal' | 'indeterminate' | 'error' | 'none' = 'normal'): Promise<boolean> {
    if (isTauri) {
      try {
        await tauriInvoke('set_taskbar_progress', { progress, status });
        return true;
      } catch (e) {
        console.warn('set_taskbar_progress failed', e);
        return false;
      }
    }
    return false;
  },


  async getHomeFeed(monthDay?: string): Promise<HomeFeedData> {
    if (isTauri) {
      return tauriInvoke<HomeFeedData>('get_home_feed', { monthDay: monthDay || null });
    }
    try {
      const res = await fetch(`/api/home-feed${monthDay ? `?monthDay=${monthDay}` : ''}`);
      if (res.ok) return await res.json();
    } catch {}
    return {
      spotlight_movies: [],
      on_this_day: [],
      star_spotlight: [],
      total_movies: 0,
      total_episodes: 0,
      total_performers: 0,
      total_studios: 0,
    };
  },

  async checkRuntimeEnvironment(): Promise<RuntimeEnvironmentInfo> {
    if (isTauri) {
      return tauriInvoke<RuntimeEnvironmentInfo>('check_runtime_environment');
    }
    // Web fallback simulation
    return {
      python_installed: true,
      python_version: 'Python 3.12 (Web Mode)',
      python_path: '/usr/bin/python3',
      sqlite3_available: true,
      database_ready: true,
      database_path: 'GPDb.db',
      playwright_available: false,
      all_ready: true,
      missing_items: [],
      recommendations: [],
    };
  },

  async saveShareCardImage(filename: string, base64Png: string): Promise<string> {
    if (isTauri) {
      return await tauriInvoke<string>('save_share_card_image', { filename, base64Png });
    }
    return new Promise<string>((resolve, reject) => {
      try {
        const arr = base64Png.split(',');
        const mime = arr[0].match(/:(.*?);/)?.[1] || 'image/png';
        const bstr = atob(arr[1] || arr[0]);
        let n = bstr.length;
        const u8arr = new Uint8Array(n);
        while (n--) {
          u8arr[n] = bstr.charCodeAt(n);
        }
        const blob = new Blob([u8arr], { type: mime });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${filename}.png`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 10000);
        resolve(`${filename}.png`);
      } catch (err) {
        reject(err);
      }
    });
  },

  async copyImageToClipboard(base64Png: string): Promise<boolean> {
    if (isTauri) {
      try {
        await tauriInvoke('copy_image_to_clipboard', { base64Png });
        return true;
      } catch (e) {
        console.warn('Native copy to clipboard failed:', e);
      }
    }
    return false;
  },

  async saveUpdateFile(filename: string, base64Data: string): Promise<string> {
    if (isTauri) {
      return await tauriInvoke<string>('save_update_file', { filename, base64Data });
    }
    return filename;
  },

  async installUpdateFile(filepath: string): Promise<void> {
    if (isTauri) {
      await tauriInvoke('install_update_file', { filepath });
    }
  },
};



