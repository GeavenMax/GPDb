import type {
  Movie,
  Performer,
  FilterState,
  DatabaseStats,
  PerformerFilterState,
  PerformerFacets,
  TranslationStats,
  TranslationProfile,
  TranslationProviders,
  TranslationProviderInput,
} from './types';

// Detect if running inside Tauri runtime
const isTauri = typeof window !== 'undefined' && ('__TAURI_INTERNALS__' in window || '__TAURI__' in window);

/**
 * True when the app talks to SQLite through Tauri commands rather than the local
 * Python server. Translation runs server-side only, so the UI hides those
 * controls in this mode instead of offering an action that cannot succeed.
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

  async runSync(): Promise<{ newMovies: number; newPerformers: number }> {
    if (isTauri) {
      return tauriInvoke<{ newMovies: number; newPerformers: number }>('run_sync');
    }
    try {
      const res = await fetch('/api/sync', { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {}
    return { newMovies: 0, newPerformers: 0 };
  },

  // Image Disk Cache Management
  async getCacheStats(): Promise<{ count: number; size_mb: number; path: string }> {
    try {
      const res = await fetch('/api/cache/stats');
      if (res.ok) return await res.json();
    } catch {}
    return { count: 0, size_mb: 0, path: '' };
  },

  async clearCache(): Promise<{ success: boolean; cleared_files: number }> {
    try {
      const res = await fetch('/api/cache/clear', { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {}
    return { success: false, cleared_files: 0 };
  },

  async downloadAllCache(): Promise<{ success: boolean; message: string }> {
    try {
      const res = await fetch('/api/cache/download_all', { method: 'POST' });
      if (res.ok) return await res.json();
    } catch {}
    return { success: false, message: 'Failed to start' };
  },

  // User Custom Tags
  async getUserTags(): Promise<Array<{ id: number; name: string; color: string }>> {
    try {
      const res = await fetch('/api/user/tags');
      if (res.ok) return await res.json();
    } catch {}
    return [];
  },

  async createUserTag(name: string, color: string = '#f59e0b'): Promise<{ id: number; name: string; color: string } | null> {
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
    try {
      const res = await fetch('/api/user/export');
      if (res.ok) return await res.json();
    } catch {}
    return null;
  },

  async importUserData(data: any): Promise<any> {
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
  // these payloads — the backend only reports whether one is stored.
  async getTranslationProviders(): Promise<TranslationProviders> {
    const empty: TranslationProviders = { profiles: [], presets: [], config_file: '' };
    try {
      const res = await fetch('/api/translate/providers');
      if (res.ok) return await res.json();
    } catch {}
    return empty;
  },

  async saveTranslationProvider(payload: TranslationProviderInput): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
    return postProviderAction('save', { ...payload });
  },

  async activateTranslationProvider(name: string): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
    return postProviderAction('activate', { name });
  },

  async deleteTranslationProvider(name: string): Promise<{ success: boolean; profiles?: TranslationProfile[]; error?: string }> {
    return postProviderAction('delete', { name });
  },

  /** Round-trip one short string so the user can verify a key before a big run. */
  async testTranslationProvider(name: string | null): Promise<{
    success: boolean; profile?: string; model?: string; elapsed?: number;
    source?: string; result?: string; error?: string;
  }> {
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

  /** Translate one synopsis on demand. Returns the Chinese text, or null on failure. */
  async translateMovie(movieId: number): Promise<string | null> {
    try {
      const res = await fetch(`/api/movies/${movieId}/translate`, { method: 'POST' });
      const body = await res.json();
      return body?.description_zh || null;
    } catch {}
    return null;
  },

  // Studio Works (Separated Movies & Episodes)
  async getStudioWorks(studioName: string): Promise<{
    studio_name: string;
    movies: Movie[];
    movies_count: number;
    episodes: any[];
    episodes_count: number;
  }> {
    try {
      const res = await fetch(`/api/studios/${encodeURIComponent(studioName)}/works`);
      if (res.ok) return await res.json();
    } catch {}
    return { studio_name: studioName, movies: [], movies_count: 0, episodes: [], episodes_count: 0 };
  },
};

