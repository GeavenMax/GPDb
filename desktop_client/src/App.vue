<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import Navbar from './components/Navbar.vue';
import Sidebar from './components/Sidebar.vue';
import MovieCard from './components/MovieCard.vue';
import MovieDetailModal from './components/MovieDetailModal.vue';
import PerformerDetailModal from './components/PerformerDetailModal.vue';
import StudioDetailModal from './components/StudioDetailModal.vue';
import EpisodeCard from './components/EpisodeCard.vue';
import EpisodeDetailModal from './components/EpisodeDetailModal.vue';
import ImageLightbox from './components/ImageLightbox.vue';
import FilterDrawer from './components/FilterDrawer.vue';
import SyncModal from './components/SyncModal.vue';
import PaginationBar from './components/PaginationBar.vue';
import {
  api, createMovieFilters, createPerformerFilters, countActivePerformerFilters,
  createEpisodeFilters, countActiveEpisodeFilters, EPISODE_SORTS, FACET_KEYS,
  FACET_LABELS, IS_TAURI,
} from './api';
import { getImageUrl } from './utils/image';
import { openLightbox, viewableImageFrom, zoomsOnClick, lightboxImage } from './utils/lightbox';
import { initTheme, setTheme, themeChoice, themeOptions } from './utils/theme';
import { PREFS } from './utils/prefs';
import type {
  Movie,
  Performer,
  FilterState,
  DatabaseStats,
  PerformerFilterState,
  PerformerFacets,
  TranslationStats,
  TranslationProfile,
  TranslationPreset,
  FavoriteType,
  FavoriteItem,
  FavoritesResponse,
  AppTab,
  StudioSummary,
  StudioSortBy,
  StudioWorks,
  EpisodeSummary,
  EpisodeSortBy,
  EpisodeFilterState,
} from './types';
import { FAVORITE_TYPES } from './types';
import { loadGlossary, glossaryCount, trMeasure, trCategory } from './utils/glossary';
import { titlePrimary, titleSecondary, sceneFilm } from './utils/bilingual';
import {
  Film, Heart, HardDrive, Download, Upload, Trash2, Image as ImageIcon, RefreshCw, Loader2,
  Languages, User as UserIcon, Sparkles, Clapperboard, Building2, Layers, Palette, Check,
} from '@lucide/vue';

/** Labels for the five favorites sections and the type pickers. */
const FAVORITE_LABELS: Record<FavoriteType, string> = {
  movie: '影片',
  performer: '演员',
  studio: '片商',
  director: '导演',
  episode: '片段',
};

// State
const currentTab = ref<AppTab>('movies');
const viewMode = ref<'grid' | 'list'>('grid');
const isFilterOpen = ref(false);
const isSyncOpen = ref(false);

const stats = ref<DatabaseStats | null>(null);
const studios = ref<string[]>([]);
const categories = ref<string[]>([]);

const movies = ref<Movie[]>([]);
const totalMovies = ref(0);
const performers = ref<Performer[]>([]);
const totalPerformers = ref(0);
const studioRows = ref<StudioSummary[]>([]);
const totalStudioRows = ref(0);
const studioQuery = ref('');
const studioSortBy = ref<StudioSortBy>('works_desc');

/** Orderings offered on the studio tab. Studios have no facets to filter by. */
const STUDIO_SORTS = [
  { id: 'works_desc', label: '按作品数' },
  { id: 'episodes_desc', label: '按片段数' },
  { id: 'name_asc', label: '按名称' },
] as const;

/**
 * The episode library. Its rows come from the whole `episodes` table rather than
 * from one film or one performer, which is the only place a scene can be browsed
 * on its own — the site titles every episode "Episode #<row id>", so there is no
 * positional label to search on either.
 */
const episodeRows = ref<EpisodeSummary[]>([]);
const totalEpisodeRows = ref(0);
const episodeQuery = ref('');
const episodeSortBy = ref<EpisodeSortBy>('id_desc');
const episodeFilters = reactive<EpisodeFilterState>(createEpisodeFilters());
const activeEpisodeFilterCount = computed(() =>
  countActiveEpisodeFilters(episodeFilters, episodeSortBy.value)
);

/**
 * Favorited keys, grouped by type. Kept as plain string sets so a heart can be
 * coloured with a single `.has()` regardless of which of the five kinds it is
 * (movie/performer/episode key on the numeric id as a string; studio/director on
 * the name itself, which is what the library filter takes).
 *
 * SQLite is the only source of truth — this is a cache of it, hydrated on load and
 * updated optimistically on click. There is no localStorage copy.
 */
const favorites = ref<Record<FavoriteType, Set<string>>>(emptyKeys());

/** Favorited items enriched for display, fetched only while the favorites tab is open. */
const favoriteItems = ref<FavoritesResponse | null>(null);
const favoritesLoading = ref(false);

function emptyKeys(): Record<FavoriteType, Set<string>> {
  return { movie: new Set(), performer: new Set(), studio: new Set(), director: new Set(), episode: new Set() };
}

const selectedMovie = ref<Movie | null>(null);
const selectedPerformer = ref<Performer | null>(null);

/** The studio being viewed, and its works. Fetched here so the modal stays presentational. */
const selectedStudio = ref<{ name: string; works_count?: number; episodes_count?: number } | null>(null);
const studioWorks = ref<StudioWorks | null>(null);
const studioWorksLoading = ref(false);

/**
 * The scene being viewed. Unlike the studio, everything it shows is already in the
 * grid row it was opened from, so there is nothing to fetch — only the neighbour
 * lookup for ‹ / › needs the list it was opened in.
 */
const selectedEpisode = ref<EpisodeSummary | null>(null);
/** The rows ‹ / › pages through: whatever the grid had loaded when it was opened. */
const episodeList = ref<EpisodeSummary[]>([]);

/**
 * Detail views can open one another — a performer's filmography links to a
 * movie, a movie's cast links to a performer, a studio's films link to both.
 * All must stay mounted so the user can navigate back, so the open order decides
 * which one sits on top. Openers push to the end; the last entry gets the highest
 * layer.
 */
type ModalKind = 'movie' | 'performer' | 'studio' | 'episode';

const modalStack = ref<ModalKind[]>([]);

function pushModal(kind: ModalKind) {
  modalStack.value = [...modalStack.value.filter(k => k !== kind), kind];
}

function popModal(kind: ModalKind) {
  modalStack.value = modalStack.value.filter(k => k !== kind);
}

function layerOf(kind: ModalKind) {
  const i = modalStack.value.indexOf(kind);
  return 50 + (i < 0 ? 0 : i) * 10;
}

const filters = reactive<FilterState>(createMovieFilters());

// Synopsis language preference (issue #4). Falls back to English per-movie
// whenever a Chinese translation has not been generated yet.
const descLang = ref<'zh' | 'en'>(
  (localStorage.getItem(PREFS.descLang) as 'zh' | 'en') || 'zh'
);

function setDescLang(lang: 'zh' | 'en') {
  descLang.value = lang;
  localStorage.setItem(PREFS.descLang, lang);
}

// Dynamic Grid Columns state (persisted to localStorage)
const gridCols = ref<number>(Number(localStorage.getItem(PREFS.gridCols)) || 5);
// List view uses its own column count: the cards are horizontal and much wider,
// so the useful range is 2-4 rather than 2-8.
const listCols = ref<number>(Number(localStorage.getItem(PREFS.listCols)) || 3);

function decreaseCols() {
  if (viewMode.value === 'list') {
    if (listCols.value > 2) {
      listCols.value--;
      localStorage.setItem(PREFS.listCols, String(listCols.value));
    }
    return;
  }
  if (gridCols.value > 2) {
    gridCols.value--;
    localStorage.setItem(PREFS.gridCols, String(gridCols.value));
  }
}

function increaseCols() {
  if (viewMode.value === 'list') {
    if (listCols.value < 4) {
      listCols.value++;
      localStorage.setItem(PREFS.listCols, String(listCols.value));
    }
    return;
  }
  if (gridCols.value < 8) {
    gridCols.value++;
    localStorage.setItem(PREFS.gridCols, String(gridCols.value));
  }
}

/** Column count driving whichever view is active. */
const activeCols = computed(() => (viewMode.value === 'list' ? listCols.value : gridCols.value));
const activeColsMax = computed(() => (viewMode.value === 'list' ? 4 : 8));

// Performer filtering (issue #7)
const performerFilters = reactive<PerformerFilterState>(createPerformerFilters());
const performerFacets = ref<PerformerFacets>({ facets: {}, total: 0, withImage: 0, enriched: 0 });
const activePerformerFilterCount = computed(() => countActivePerformerFilters(performerFilters));

/** Flat list of the currently selected facet values, for the chip row. */
const activeFacetChips = computed(() =>
  FACET_KEYS.flatMap(key =>
    (performerFilters[key] as string[]).map(value => ({
      key: key as string,
      value,
      label: FACET_LABELS[key] || key,
    }))
  )
);

// Machine translation progress (issue #4)
const translationStats = ref<TranslationStats | null>(null);
const isTranslating = ref(false);
const translateMsg = ref('');

/**
 * How new synopses get translated.
 *
 * 'single' translates one film at a time, when its detail view is opened, and
 * nothing else - the library stays mostly untranslated until browsed, which is
 * the point: a full library is ~3,500 API calls, a browsing session is a handful.
 * 'batch' leaves translation to the explicit buttons in Settings.
 */
const translateMode = ref<'single' | 'batch'>(
  localStorage.getItem(PREFS.translateMode) === 'batch' ? 'batch' : 'single'
);

function setTranslateMode(mode: 'single' | 'batch') {
  translateMode.value = mode;
  localStorage.setItem(PREFS.translateMode, mode);
}

// Translation sources (multiple saved API providers, switchable by hand).
const providerList = ref<TranslationProfile[]>([]);
const providerPresets = ref<TranslationPreset[]>([]);
const providerForm = reactive({
  open: false,
  editing: '',
  name: '',
  label: '',
  type: 'openai',
  model: '',
  base_url: '',
  api_key: '',
});
const providerBusy = ref(false);
const providerMsg = ref('');
const providerTest = ref<{ ok: boolean; text: string } | null>(null);

// List loading state
//
// 'scroll' pulls the next page automatically near the bottom; 'paged' shows an
// explicit pagination bar. Both share `pageSize`; the page cursor is kept per
// tab, since one counter would carry "page 7 of the movies grid" over to the
// performer grid the moment the user switched.
// Ceiling is 96: the performer endpoint and both Tauri commands clamp pageSize
// to 100, so anything larger would desync the page count from the backend.
const PAGE_SIZE_OPTIONS = [24, 48, 96]; // all divide by 2, 3, 4, 6, 8
const pageSize = ref(snapPageSize(Number(localStorage.getItem(PREFS.pageSize))));

/** Snap to an offered option, so a stale stored value is not sent to the API. */
function snapPageSize(size: number): number {
  if (!Number.isFinite(size) || size <= 0) return 48;
  return PAGE_SIZE_OPTIONS.reduce(
    (best, option) => (Math.abs(option - size) < Math.abs(best - size) ? option : best),
    PAGE_SIZE_OPTIONS[0]
  );
}
const listMode = ref<'scroll' | 'paged'>(
  localStorage.getItem(PREFS.listMode) === 'paged' ? 'paged' : 'scroll'
);
const moviePage = ref(1);
const performerPage = ref(1);
const studioPage = ref(1);
const episodePage = ref(1);
const isLoading = ref(false);
const isLoadingMore = ref(false);

function setListMode(mode: 'scroll' | 'paged') {
  if (listMode.value === mode) return;
  listMode.value = mode;
  localStorage.setItem(PREFS.listMode, mode);
  // Page counts mean different things per mode; start each switch from the top.
  reloadCurrentTab();
}

function setPageSize(size: number) {
  if (size === pageSize.value) return;
  pageSize.value = size;
  localStorage.setItem(PREFS.pageSize, String(size));
  reloadCurrentTab();
}

function goToPage(n: number) {
  if (currentTab.value === 'movies') fetchMovies(true, n);
  else if (currentTab.value === 'performers') fetchPerformers(true, n);
  else if (currentTab.value === 'studios') fetchStudios(true, n);
  else if (currentTab.value === 'episodes') fetchEpisodes(true, n);
  scrollContainerRef.value?.scrollTo({ top: 0, behavior: 'smooth' });
}

/** Refetch the visible tab at page 1 — for filter, page-size and mode changes. */
function reloadCurrentTab() {
  if (currentTab.value === 'movies') fetchMovies(true);
  else if (currentTab.value === 'performers') fetchPerformers(true);
  else if (currentTab.value === 'studios') fetchStudios(true);
  else if (currentTab.value === 'episodes') fetchEpisodes(true);
  else return;
  nextTick(() => fillViewport());
}

const scrollContainerRef = ref<HTMLElement | null>(null);

// Cache & User Data Management State
const cacheStats = ref<{ count: number; size_mb: number; path: string }>({ count: 0, size_mb: 0, path: '' });
const isCacheLoading = ref(false);
const cacheStatusMsg = ref('');
const importStatusMsg = ref('');
const fileInputRef = ref<HTMLInputElement | null>(null);

// Load data
async function loadStats() {
  stats.value = await api.getStats();
  studios.value = await api.getStudios();
  categories.value = await api.getCategories();
  loadCacheStats();
  loadTranslationStats();
  loadProviders();
}

async function loadTranslationStats() {
  translationStats.value = await api.getTranslationStats();
}

// --- Performer attribute glossary -------------------------------------------
//
// Attribute values (skin tone, hair colour, tattoo locations, ...) come from a small
// closed vocabulary, so they are translated once into `attr_glossary` and looked up
// locally afterwards. That is why this is a one-off button rather than something the
// per-performer view triggers: 76 terms against tens of thousands of performers.
const glossaryBusy = ref(false);
const glossaryMsg = ref('');
const glossaryError = ref('');

async function handleRunGlossary(dryRun: boolean) {
  glossaryBusy.value = true;
  glossaryMsg.value = '';
  glossaryError.value = '';
  const res = await api.runGlossaryTranslation(dryRun);
  if (res.success) {
    if (dryRun) {
      glossaryMsg.value = `待翻译术语 ${res.pending} 条（试跑，未写入）`;
    } else {
      glossaryMsg.value = `术语表已更新：本次新增 ${res.translated} 条，累计 ${res.total} 条` +
        (res.failed ? `，失败 ${res.failed} 条` : '');
      // Refresh the local lookup table so the new labels appear without a reload.
      await loadGlossary(true);
    }
  } else {
    glossaryError.value = res.error || '术语表翻译失败';
  }
  glossaryBusy.value = false;
}

async function handleRunTranslation(limit: number | null) {
  isTranslating.value = true;
  translateMsg.value = '';
  const res = await api.runTranslation(limit);
  if (res.success) {
    translateMsg.value = res.message || '翻译任务已启动';
    // Poll until the background job reports completion.
    const poll = setInterval(async () => {
      await loadTranslationStats();
      if (!translationStats.value?.running) {
        clearInterval(poll);
        isTranslating.value = false;
        translateMsg.value = '翻译完成';
        fetchMovies(true);
      }
    }, 3000);
  } else {
    translateMsg.value = res.error || '启动失败';
    isTranslating.value = false;
  }
}

// --- Translation sources ---

async function loadProviders() {
  const data = await api.getTranslationProviders();
  providerList.value = data.profiles;
  providerPresets.value = data.presets;
}

/** Prefill the form from a vendor template. */
function applyPreset(preset: TranslationPreset) {
  providerForm.name = preset.id;
  providerForm.label = preset.label;
  providerForm.type = preset.type;
  providerForm.model = preset.model;
  providerForm.base_url = preset.base_url;
  providerForm.api_key = '';
}

function openProviderForm(profile?: TranslationProfile) {
  providerTest.value = null;
  providerMsg.value = '';
  providerForm.open = true;
  if (profile) {
    providerForm.editing = profile.name;
    providerForm.name = profile.name;
    providerForm.label = profile.label;
    providerForm.type = profile.type;
    providerForm.model = profile.model;
    providerForm.base_url = profile.base_url;
  } else {
    providerForm.editing = '';
    providerForm.name = '';
    providerForm.label = '';
    providerForm.type = 'openai';
    providerForm.model = '';
    providerForm.base_url = '';
  }
  // Never prefilled: the stored key is not sent to the client at all.
  providerForm.api_key = '';
}

async function saveProvider() {
  if (!providerForm.name.trim()) {
    providerMsg.value = '请填写配置名称';
    return;
  }
  providerBusy.value = true;
  providerMsg.value = '';
  const res = await api.saveTranslationProvider({
    name: providerForm.name.trim(),
    label: providerForm.label.trim() || providerForm.name.trim(),
    type: providerForm.type,
    model: providerForm.model.trim(),
    base_url: providerForm.base_url.trim(),
    api_key: providerForm.api_key.trim(),
    active: !providerForm.editing && providerList.value.length === 0,
  });
  providerBusy.value = false;
  if (res.success) {
    if (res.profiles) providerList.value = res.profiles;
    providerForm.open = false;
    providerMsg.value = '已保存';
    await loadTranslationStats();
  } else {
    providerMsg.value = res.error || '保存失败';
  }
}

async function activateProvider(name: string) {
  const res = await api.activateTranslationProvider(name);
  if (res.success && res.profiles) providerList.value = res.profiles;
  else providerMsg.value = res.error || '切换失败';
  await loadTranslationStats();
}

async function removeProvider(name: string) {
  const res = await api.deleteTranslationProvider(name);
  if (res.success && res.profiles) providerList.value = res.profiles;
  else providerMsg.value = res.error || '删除失败';
  await loadTranslationStats();
}

async function testProvider(name: string | null) {
  providerBusy.value = true;
  providerTest.value = null;
  providerMsg.value = '';
  const res = await api.testTranslationProvider(name);
  providerBusy.value = false;
  if (res.success) {
    providerTest.value = {
      ok: true,
      text: `${res.profile} · ${res.model} · ${res.elapsed}s\n原文: ${res.source}\n译文: ${res.result}`,
    };
  } else {
    providerTest.value = { ok: false, text: res.error || '测试失败' };
  }
}

async function loadPerformerFacets() {
  performerFacets.value = await api.getPerformerFacets();
}

/** Toggle one facet value; multiple values within a facet are OR-ed. */
function togglePerformerFacet(key: keyof PerformerFilterState, value: string) {
  const list = performerFilters[key] as string[];
  const idx = list.indexOf(value);
  if (idx === -1) list.push(value);
  else list.splice(idx, 1);
}

function resetMovieFilters() {
  Object.assign(filters, createMovieFilters());
}

function resetPerformerFilters() {
  Object.assign(performerFilters, createPerformerFilters());
}

/** Clears the drawer's episode section, sort included — it is edited in there too. */
function resetEpisodeFilters() {
  Object.assign(episodeFilters, createEpisodeFilters());
  episodeSortBy.value = 'id_desc';
}

/** The studio tab has no drawer; its only state is the search box and the sort. */
function resetStudioFilters() {
  studioQuery.value = '';
  studioSortBy.value = 'works_desc';
}

async function loadCacheStats() {
  cacheStats.value = await api.getCacheStats();
}

async function handleClearCache() {
  if (!confirm('确定清空本地所有缓存的封面和分集图片吗？')) return;
  isCacheLoading.value = true;
  await api.clearCache();
  await loadCacheStats();
  isCacheLoading.value = false;
  cacheStatusMsg.value = '图片缓存已清空';
  setTimeout(() => { cacheStatusMsg.value = ''; }, 3000);
}

async function handleBatchDownloadCache() {
  isCacheLoading.value = true;
  const res = await api.downloadAllCache();
  cacheStatusMsg.value = res.message || '全量后台下载已启动，请稍候...';
  isCacheLoading.value = false;
  setTimeout(loadCacheStats, 4000);
}

async function handleExportUserData() {
  const data = await api.exportUserData();
  if (!data) return;
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `gpdb_user_backup_${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

function triggerImportFileInput() {
  fileInputRef.value?.click();
}

async function handleImportFile(e: Event) {
  const target = e.target as HTMLInputElement;
  if (!target.files || target.files.length === 0) return;
  const file = target.files[0];
  const reader = new FileReader();
  reader.onload = async (evt) => {
    try {
      const json = JSON.parse(evt.target?.result as string);
      const res = await api.importUserData(json);
      importStatusMsg.value =
        `导入成功: 恢复了 ${res.tags_imported} 个标签、${res.movies_updated} 部影片的用户标记` +
        (res.favorites_imported ? `，以及 ${res.favorites_imported} 条收藏` : '') + '！';
      setTimeout(() => { importStatusMsg.value = ''; }, 5000);
      // Favorites come from the server snapshot now, so both caches need rebuilding.
      loadFavoriteKeys();
      if (currentTab.value === 'favorites') loadFavorites();
      fetchMovies();
    } catch (err: any) {
      alert('导入失败，请检查 JSON 格式是否正确: ' + err.message);
    }
  };
  reader.readAsText(file);
  target.value = '';
}

/** A single-synopsis translation finished; fold it into the lists already loaded. */
function onMovieTranslated(movieId: number, zh: string) {
  const m = movies.value.find(x => x.id === movieId);
  if (m) m.description_zh = zh;
  if (selectedMovie.value?.id === movieId) selectedMovie.value.description_zh = zh;
  loadTranslationStats();
}

async function onUserDataChanged(movieId: number) {
  const updated = await api.getMovieDetail(movieId);
  if (updated) {
    const idx = movies.value.findIndex(m => m.id === movieId);
    if (idx !== -1) {
      movies.value[idx] = updated;
    }
  }
}

/**
 * Load one page of movies.
 *
 * `replace` swaps the page in for the list instead of appending to it. The
 * cursor is always whatever `targetPage` says, so filter changes (page 1) and
 * the pagination bar (page N) both go through the same path.
 */
async function fetchMovies(replace = true, targetPage = 1) {
  moviePage.value = targetPage;
  if (replace) isLoading.value = true;
  else isLoadingMore.value = true;

  try {
    const res = await api.getMovies(filters, moviePage.value, pageSize.value);
    if (replace) {
      movies.value = res.items;
    } else {
      const existingIds = new Set(movies.value.map(m => m.id));
      const nextItems = res.items.filter(m => !existingIds.has(m.id));
      movies.value.push(...nextItems);
    }
    totalMovies.value = res.total;
  } finally {
    isLoading.value = false;
    isLoadingMore.value = false;
  }
}

async function loadMoreMovies() {
  if (isLoading.value || isLoadingMore.value) return;
  if (movies.value.length >= totalMovies.value) return;
  await fetchMovies(false, moviePage.value + 1);
}

async function fetchPerformers(replace = true, targetPage = 1) {
  performerPage.value = targetPage;
  if (replace) isLoading.value = true;
  else isLoadingMore.value = true;

  try {
    const res = await api.getPerformers(performerFilters, performerPage.value, pageSize.value);
    if (replace) {
      performers.value = res.items;
    } else {
      const existingIds = new Set(performers.value.map(p => p.id));
      const nextItems = res.items.filter(p => !existingIds.has(p.id));
      performers.value.push(...nextItems);
    }
    totalPerformers.value = res.total;
  } finally {
    isLoading.value = false;
    isLoadingMore.value = false;
  }
}

async function loadMorePerformers() {
  if (isLoading.value || isLoadingMore.value) return;
  if (performers.value.length >= totalPerformers.value) return;
  await fetchPerformers(false, performerPage.value + 1);
}

async function fetchStudios(replace = true, targetPage = 1) {
  studioPage.value = targetPage;
  if (replace) isLoading.value = true;
  else isLoadingMore.value = true;

  try {
    const res = await api.getStudioLibrary(
      studioQuery.value, studioSortBy.value, studioPage.value, pageSize.value
    );
    if (replace) {
      studioRows.value = res.items;
    } else {
      // Studios are keyed by name, since the library has no table for them.
      const existing = new Set(studioRows.value.map(s => s.name));
      studioRows.value.push(...res.items.filter(s => !existing.has(s.name)));
    }
    totalStudioRows.value = res.total;
  } finally {
    isLoading.value = false;
    isLoadingMore.value = false;
  }
}

async function loadMoreStudios() {
  if (isLoading.value || isLoadingMore.value) return;
  if (studioRows.value.length >= totalStudioRows.value) return;
  await fetchStudios(false, studioPage.value + 1);
}

async function fetchEpisodes(replace = true, targetPage = 1) {
  episodePage.value = targetPage;
  if (replace) isLoading.value = true;
  else isLoadingMore.value = true;

  try {
    const res = await api.getEpisodeLibrary(
      episodeQuery.value, episodeSortBy.value, episodeFilters, episodePage.value, pageSize.value
    );
    if (replace) {
      episodeRows.value = res.items;
    } else {
      // Episodes have a real id, so duplicates are unlikely; the filter is for a
      // page arriving twice (a fast scroll firing two loads at the same cursor).
      const existing = new Set(episodeRows.value.map(e => e.id));
      episodeRows.value.push(...res.items.filter(e => !existing.has(e.id)));
    }
    totalEpisodeRows.value = res.total;
  } finally {
    isLoading.value = false;
    isLoadingMore.value = false;
  }
}

async function loadMoreEpisodes() {
  if (isLoading.value || isLoadingMore.value) return;
  if (episodeRows.value.length >= totalEpisodeRows.value) return;
  await fetchEpisodes(false, episodePage.value + 1);
}

// Waterfall Infinite Scroll
//
// This used to be an IntersectionObserver rooted at the scroll container. That
// silently stalled after the first batch: the sentinel element was created by a
// `v-if` inside the very list it was meant to observe, so each rebuild swapped
// the observed node for a fresh one the observer was not watching, and the
// intersection change that should have triggered the next page never arrived.
// A plain scroll handler on the container is both simpler and immune to that
// class of bug — the sentinel no longer has to exist for loading to work.
const SCROLL_TRIGGER_PX = 800;

function atScrollEnd(): boolean {
  const el = scrollContainerRef.value;
  if (!el) return false;
  return el.scrollTop + el.clientHeight >= el.scrollHeight - SCROLL_TRIGGER_PX;
}

/** Load the next page when the container is scrolled near the bottom. */
function handleScroll() {
  if (listMode.value !== 'scroll') return;
  if (!atScrollEnd()) return;
  if (currentTab.value === 'movies') {
    if (!isLoading.value && !isLoadingMore.value && movies.value.length < totalMovies.value) loadMoreMovies();
  } else if (currentTab.value === 'performers') {
    if (!isLoading.value && !isLoadingMore.value && performers.value.length < totalPerformers.value) loadMorePerformers();
  } else if (currentTab.value === 'studios') {
    if (!isLoading.value && !isLoadingMore.value && studioRows.value.length < totalStudioRows.value) loadMoreStudios();
  } else if (currentTab.value === 'episodes') {
    if (!isLoading.value && !isLoadingMore.value && episodeRows.value.length < totalEpisodeRows.value) loadMoreEpisodes();
  }
}

/**
 * A short first page (or a tall window) can leave the container unscrollable,
 * so no scroll event ever fires and loading stops with the list half full.
 * Keep pulling pages until the content overflows or everything is loaded.
 */
async function fillViewport() {
  if (listMode.value !== 'scroll') return;
  const el = scrollContainerRef.value;
  if (!el) return;
  // Five pages of headroom is plenty; the guard stops a runaway loop if the
  // server keeps returning rows the dedupe filter discards.
  for (let i = 0; i < 5; i++) {
    if (el.scrollHeight > el.clientHeight + SCROLL_TRIGGER_PX) return;
    if (currentTab.value === 'movies') {
      if (isLoading.value || isLoadingMore.value || movies.value.length >= totalMovies.value) return;
      await loadMoreMovies();
    } else if (currentTab.value === 'performers') {
      if (isLoading.value || isLoadingMore.value || performers.value.length >= totalPerformers.value) return;
      await loadMorePerformers();
    } else if (currentTab.value === 'studios') {
      if (isLoading.value || isLoadingMore.value || studioRows.value.length >= totalStudioRows.value) return;
      await loadMoreStudios();
    } else if (currentTab.value === 'episodes') {
      if (isLoading.value || isLoadingMore.value || episodeRows.value.length >= totalEpisodeRows.value) return;
      await loadMoreEpisodes();
    } else {
      return;
    }
  }
}

// The navbar search box drives whichever tab is on screen; each tab keeps
// its own query so switching back does not clobber the other's results.
const searchQuery = computed({
  get: () => {
    if (currentTab.value === 'performers') return performerFilters.query;
    if (currentTab.value === 'studios') return studioQuery.value;
    if (currentTab.value === 'episodes') return episodeQuery.value;
    return filters.query;
  },
  set: (val: string) => {
    if (currentTab.value === 'performers') performerFilters.query = val;
    else if (currentTab.value === 'studios') studioQuery.value = val;
    else if (currentTab.value === 'episodes') episodeQuery.value = val;
    else filters.query = val;
  },
});

// Movie filters only re-query the movie grid.
watch(
  [
    () => filters.query,
    () => filters.studio,
    () => filters.director,
    () => filters.category,
    () => filters.sortBy,
  ],
  () => {
    if (currentTab.value === 'movies') reloadCurrentTab();
  }
);

// Performer facets re-query the performer grid. Deep, because the facet
// selections are arrays mutated in place.
watch(performerFilters, () => {
  if (currentTab.value === 'performers') reloadCurrentTab();
}, { deep: true });

// Studio search and sort have no filter drawer, so they are plain refs.
watch([studioQuery, studioSortBy], () => {
  if (currentTab.value === 'studios') reloadCurrentTab();
});

// Episodes take both: a sort control in the toolbar (as studios do) and a drawer
// for the three filters, which is where the sort also lives.
watch([episodeQuery, episodeSortBy, episodeFilters], () => {
  if (currentTab.value === 'episodes') reloadCurrentTab();
}, { deep: true });

watch(currentTab, (newTab) => {
  scrollContainerRef.value?.scrollTo({ top: 0 });
  if (newTab === 'movies') {
    // Already-loaded lists are kept as they are, so returning to a tab does not
    // throw away the page the user had scrolled to.
    if (movies.value.length === 0) reloadCurrentTab();
  } else if (newTab === 'performers') {
    if (performers.value.length === 0) reloadCurrentTab();
    loadPerformerFacets();
  } else if (newTab === 'studios') {
    if (studioRows.value.length === 0) reloadCurrentTab();
  } else if (newTab === 'episodes') {
    if (episodeRows.value.length === 0) reloadCurrentTab();
  } else if (newTab === 'favorites') {
    // Always refetched: the page is a server-side snapshot of five tables and the
    // scrape running in the background keeps adding rows it can point at.
    loadFavorites();
  } else if (newTab === 'settings') {
    loadCacheStats();
    loadTranslationStats();
  }
});

/** Whether one item is favorited. `key` is an id for most types, a name for studio/director. */
function isFavorite(type: FavoriteType, key: string | number | null | undefined): boolean {
  if (key == null) return false;
  return favorites.value[type].has(String(key));
}

/** MovieCard emits the whole movie; the generic toggle takes a key. */
function toggleFavorite(m: Movie) {
  void toggleFavoriteEntity('movie', String(m.id));
}

/** Same for the performer modal, which emits the performer. */
function togglePerformerFavorite(p: Performer) {
  void toggleFavoriteEntity('performer', String(p.id));
}

/** Studios have no id — the name is the key, here and in the library filter. */
function toggleStudioFavorite(name: string) {
  void toggleFavoriteEntity('studio', name);
}

/** Episodes key on their row id, like films. */
function toggleEpisodeFavorite(ep: EpisodeSummary) {
  void toggleFavoriteEntity('episode', String(ep.id));
}

async function toggleFavoriteEntity(type: FavoriteType, key: string) {
  const set = favorites.value[type];
  const wasFavorite = set.has(key);

  // Optimistic flip so the heart responds immediately; the round trip is a DB write.
  if (wasFavorite) set.delete(key);
  else set.add(key);

  try {
    const nowFavorite = await api.toggleFavorite(type, key);
    // The response is authoritative — adopt it rather than assuming the flip landed.
    if (nowFavorite) set.add(key);
    else set.delete(key);
  } catch {
    // Roll back so the UI never claims a favorite the database does not have.
    if (wasFavorite) set.add(key);
    else set.delete(key);
    return;
  }

  // The favorites page renders a server-side snapshot, so refresh it if it is on screen.
  if (currentTab.value === 'favorites') loadFavorites();
}

async function loadFavorites() {
  favoritesLoading.value = true;
  try {
    const groups = await api.getFavorites();
    favoriteItems.value = groups;
    // Re-derive the heart cache from the authoritative list.
    const keys = emptyKeys();
    for (const t of FAVORITE_TYPES) {
      for (const item of groups[t]) keys[t].add(item.key);
    }
    favorites.value = keys;
  } catch {
    // Leave the current hearts alone; they are still the best information we have.
  } finally {
    favoritesLoading.value = false;
  }
}

/** Load just the favorited keys, for colouring hearts across the whole app. */
async function loadFavoriteKeys() {
  try {
    const keys = await api.getFavoriteKeys();
    const next = emptyKeys();
    for (const t of FAVORITE_TYPES) {
      for (const k of keys[t] || []) next[t].add(k);
    }
    favorites.value = next;
  } catch {
    // Server unreachable: hearts stay unlit rather than the page failing to load.
  }
}

/** Section rows, kept separate so the template needs no non-null assertions. */
const favMovies = computed(() => favoriteItems.value?.movie || []);
const favPerformers = computed(() => favoriteItems.value?.performer || []);
const favEpisodes = computed(() => favoriteItems.value?.episode || []);
const favStudios = computed(() => favoriteItems.value?.studio || []);
const favDirectors = computed(() => favoriteItems.value?.director || []);

/** Total across all five sections, for the page header. */
const favoriteTotal = computed(() => {
  const counts = favoriteItems.value?.counts;
  if (!counts) return 0;
  return FAVORITE_TYPES.reduce((sum, t) => sum + (counts[t] || 0), 0);
});

/**
 * Adapt a server favorites row into the minimal shape MovieCard renders.
 *
 * The favorites endpoint returns display rows (key/title/cover) rather than full
 * movie records, which is deliberate — it must work once the library is larger than
 * what the client can hold. Only the fields MovieCard actually reads are filled in;
 * anything absent simply does not render.
 */
function asMovie(f: FavoriteItem): Movie {
  return {
    id: Number(f.key),
    title: f.title || `#${f.key}`,
    // Without this the favorites tab is the one grid still showing English in
    // Chinese mode — the card falls back to `title` whenever `title_zh` is absent.
    title_zh: f.title_zh ?? null,
    cover_full: f.cover_full ?? null,
    release_year: f.release_year ?? null,
    studio_name: f.studio_name ?? null,
  };
}

/**
 * A favourited scene's parent film, in the language the rest of the app is showing.
 *
 * The favourites payload carries no episode ordinal, so this card cannot say "第 3 集"
 * the way the library grid does; the parent film is what identifies the scene, and its
 * Chinese name is the only Chinese this row can offer (`f.title` is the site's
 * placeholder, "Episode #<row id>").
 */
function favFilmTitle(f: FavoriteItem): string {
  return titlePrimary(sceneFilm(f), descLang.value);
}

/** The same name with the original appended, for the tooltip. */
function favFilmFull(f: FavoriteItem): string {
  const alt = titleSecondary(sceneFilm(f), descLang.value);
  return alt ? `${favFilmTitle(f)}（${alt}）` : favFilmTitle(f);
}

/** Reveal the fallback tile behind an <img> that failed to load. */
function hideBrokenImage(e: Event) {
  const img = e.target as HTMLImageElement;
  if (img) img.style.display = 'none';
}

/** Jump to the library filtered by one studio (also used by favorited studios). */
function filterByStudio(studioName: string) {
  filters.studio = studioName;
  filters.director = '';
  closeAllModals();
  currentTab.value = 'movies';
  fetchMovies(true);
}

/** Same, for a director. Needs the `director` filter added to FilterState. */
function filterByDirector(directorName: string) {
  filters.director = directorName;
  filters.studio = '';
  closeAllModals();
  currentTab.value = 'movies';
  fetchMovies(true);
}

/** Drop every detail level at once. Modal ids are looked up rather than assumed
 *  non-null, because only the top of the stack is guaranteed to be populated. */
function closeAllModals() {
  if (selectedMovie.value) closeMovieDetail();
  if (selectedPerformer.value) closePerformerDetail();
  if (selectedStudio.value) closeStudioDetail();
  if (selectedEpisode.value) closeEpisodeDetail();
}

/**
 * A click in the sidebar.
 *
 * Clicking a library you are *already in* means "back up one level": the only
 * second level these tabs have is a filtered list — `filterByStudio` /
 * `filterByDirector` drop you into exactly that — so the click clears that tab's
 * filters and scrolls to the top. Clearing is what triggers the refetch, via the
 * same watchers the drawer writes through, which also means a click on a tab that
 * was never filtered costs nothing.
 */
function handleNavClick(tab: AppTab) {
  // A modal covers the sidebar, so this is a no-op today; kept so the handler
  // stays correct if that layering ever changes.
  closeAllModals();

  if (tab !== currentTab.value) {
    currentTab.value = tab;
    return;
  }

  if (tab === 'movies') resetMovieFilters();
  else if (tab === 'performers') resetPerformerFilters();
  else if (tab === 'studios') resetStudioFilters();
  else if (tab === 'episodes') resetEpisodeFilters();
  else return;   // favorites / settings have nothing to clear

  scrollContainerRef.value?.scrollTo({ top: 0, behavior: 'smooth' });
}

async function openMovieDetail(m: Movie) {
  pushModal('movie');
  const detail = await api.getMovieDetail(m.id);
  selectedMovie.value = detail || m;
}

async function openMovieDetailById(id: number) {
  pushModal('movie');
  const detail = await api.getMovieDetail(id);
  if (detail) selectedMovie.value = detail;
}

async function openPerformerDetail(id: number) {
  pushModal('performer');
  const detail = await api.getPerformerDetail(id);
  selectedPerformer.value = detail || { id, name: `Performer #${id}` };
}

/**
 * Open a studio's page.
 *
 * Callers only ever have a name (the library grid has counts with it, the favorites
 * page just the name), so the films and episodes are fetched here rather than being
 * handed in the way they are for a movie or a performer.
 */
async function openStudioDetail(studio: { name: string; works_count?: number; episodes_count?: number }) {
  pushModal('studio');
  selectedStudio.value = studio;
  studioWorks.value = null;
  studioWorksLoading.value = true;
  try {
    const works = await api.getStudioWorks(studio.name);
    // A slower fetch for studio A must not land on top of studio B's page.
    if (selectedStudio.value?.name === studio.name) studioWorks.value = works;
  } finally {
    studioWorksLoading.value = false;
  }
}

function closeMovieDetail() {
  selectedMovie.value = null;
  popModal('movie');
}

function closePerformerDetail() {
  selectedPerformer.value = null;
  popModal('performer');
}

function closeStudioDetail() {
  selectedStudio.value = null;
  studioWorks.value = null;
  popModal('studio');
}

/**
 * Open a scene.
 *
 * `rows` is the grid the card was clicked in, so ‹ / › can walk the loaded pages
 * without a request — the modal is a viewer, not a second browser.
 */
function openEpisodeDetail(ep: EpisodeSummary, rows: EpisodeSummary[]) {
  pushModal('episode');
  episodeList.value = rows;
  selectedEpisode.value = ep;
}

function closeEpisodeDetail() {
  selectedEpisode.value = null;
  episodeList.value = [];
  popModal('episode');
}

/** ‹ / › inside the loaded rows. The index is looked up rather than stored, so a
 *  reload behind the modal cannot leave it pointing at the wrong scene. */
function navigateEpisode(delta: number) {
  const current = selectedEpisode.value;
  if (!current) return;
  const i = episodeList.value.findIndex(e => e.id === current.id);
  if (i < 0) return;
  const next = episodeList.value[i + delta];
  if (next) selectedEpisode.value = next;
}

/**
 * One click on an image that has no click action of its own opens the viewer.
 *
 * Those are the images that ARE the panel's content — a film's poster, a performer's
 * portrait, a scene still — and they opt in with ZOOM_CLICK_ATTR, because a click is
 * otherwise free and asking for two of them is just friction. Registered in the
 * capture phase so it runs before any handler on a card the image sits in; a card
 * that is click-to-open therefore never contains a marked image (see the two cases
 * below, which keep the double-click gesture instead).
 */
function onGlobalClick(e: MouseEvent) {
  if (lightboxImage.value) return;
  const img = viewableImageFrom(e.target);
  if (!img || !zoomsOnClick(img)) return;
  openLightbox(img.currentSrc || img.src, img.alt);
}

/**
 * Double-clicking an image inside a click-to-open card opens it in the viewer.
 *
 * One listener on window rather than a binding per image: covers appear in a dozen
 * places and all of them should behave the same. On such a cover the two gestures
 * both happen — the detail page opens underneath and the viewer covers it — so a
 * single click stays free for navigation, which is why these images do not carry
 * ZOOM_CLICK_ATTR. Images inside the viewer are ignored so it cannot open on itself.
 */
function onGlobalDblClick(e: MouseEvent) {
  if (lightboxImage.value) return;
  const img = viewableImageFrom(e.target);
  if (!img || zoomsOnClick(img)) return;
  openLightbox(img.currentSrc || img.src, img.alt);
}

onMounted(async () => {
  // index.html's inline script already put the stored theme on <html> before the first
  // paint; this starts the OS listener that keeps 跟随系统 current.
  initTheme();

  // Hearts come from the database, not localStorage — so they survive a browser
  // change and travel with an export. Both loads are fire-and-forget: a failure
  // leaves the UI usable, just without hearts or Chinese attribute labels.
  loadFavoriteKeys();
  loadGlossary();

  window.addEventListener('click', onGlobalClick, true);
  window.addEventListener('dblclick', onGlobalDblClick);

  loadStats();
  await fetchMovies(true);
  loadPerformerFacets();
  nextTick(() => fillViewport());
});

onUnmounted(() => {
  window.removeEventListener('click', onGlobalClick, true);
  window.removeEventListener('dblclick', onGlobalDblClick);
});
</script>

<template>
  <!--
    h-screen, not min-h-screen: with a minimum the shell grows to fit the grid
    and `<main>`'s overflow-y-auto never engages, so the *document* ends up
    scrolling. Everything bound to `<main>` — the scroll-based loader in
    particular — then sits on an element that never scrolls.
  -->
  <div class="wallpaper h-screen overflow-hidden bg-app text-fg flex flex-col antialiased">
    <!-- Navbar -->
    <Navbar
      v-model="searchQuery"
      :movie-count="stats ? stats.movies : 0"
      :view-mode="viewMode"
      :filter-active="
        currentTab === 'performers'
          ? activePerformerFilterCount > 0
          : currentTab === 'episodes'
            ? activeEpisodeFilterCount > 0
            : currentTab === 'studios'
              ? false
              : Boolean(filters.studio || filters.director || filters.category || filters.sortBy !== 'year_desc')
      "
      @toggle-filter="isFilterOpen = !isFilterOpen"
      @toggle-sync="isSyncOpen = true"
      @change-view="(mode) => viewMode = mode"
    />

    <!-- Main App Body -->
    <div class="flex-1 flex overflow-hidden">
      <!-- Sidebar -->
      <Sidebar
        :current-tab="currentTab"
        @change-tab="handleNavClick"
      />

      <!-- Main Stage -->
      <main
        ref="scrollContainerRef"
        class="flex-1 overflow-y-auto p-6 md:p-8"
        @scroll.passive="handleScroll"
      >
        <!-- 1. Movies Tab -->
        <div v-if="currentTab === 'movies'" class="space-y-6">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <h1 class="text-xl font-bold text-fg tracking-tight">探索全量影片</h1>
              <span class="text-xs text-fg-4 font-mono">({{ movies.length }} / {{ totalMovies.toLocaleString() }})</span>
            </div>

            <div class="flex items-center gap-3">
              <!-- Active filter chips -->
              <div class="flex items-center gap-2">
                <span v-if="filters.studio" class="text-xs px-2.5 py-1 rounded-lg bg-accent-fill/10 text-accent border border-accent-fill/30 flex items-center gap-1">
                  厂牌: {{ filters.studio }}
                  <button @click="filters.studio = ''" class="hover:text-fg">×</button>
                </span>
                <span v-if="filters.category" class="text-xs px-2.5 py-1 rounded-lg bg-accent-fill/10 text-accent border border-accent-fill/30 flex items-center gap-1">
                  分类: {{ trCategory(filters.category) }}
                  <button @click="filters.category = ''" class="hover:text-fg">×</button>
                </span>
              </div>

              <!-- Synopsis language toggle (issue #4) -->
              <div class="flex items-center gap-1 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <Languages class="w-3 h-3 text-fg-4 ml-1.5" />
                <button
                  v-for="l in [{ id: 'zh', label: '中文' }, { id: 'en', label: '原文' }]"
                  :key="l.id"
                  @click="setDescLang(l.id as 'zh' | 'en')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    descLang === l.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="l.id === 'zh' ? '优先显示中文简介（未翻译的影片自动回落原文）' : '始终显示英文原文'"
                >
                  {{ l.label }}
                </button>
              </div>

              <!-- How the list pages in: auto-load on scroll, or explicit pages -->
              <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <button
                  v-for="m in [
                    { id: 'scroll', label: '滑动加载' },
                    { id: 'paged', label: '翻页' }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? '滚动到底部自动加载下一页' : '显示翻页按钮，可自定义每页条目数'"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid / list columns adjuster (both modes) -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">每行</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="减少每行列数"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="增加每行列数"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">列</span>
              </div>
            </div>
          </div>

          <!-- Movie Grid / Multi-Column List -->
          <div
            v-if="movies.length > 0"
            class="grid transition-all duration-200"
            :class="viewMode === 'grid' ? 'gap-4 sm:gap-6' : 'gap-3'"
            :style="{ gridTemplateColumns: `repeat(${activeCols}, minmax(0, 1fr))` }"
          >
            <MovieCard
              v-for="m in movies"
              :key="m.id"
              :movie="m"
              :is-favorite="isFavorite('movie', m.id)"
              :view="viewMode"
              :lang="descLang"
              @select="openMovieDetail"
              @toggle-favorite="toggleFavorite"
            />
          </div>

          <!-- Infinite-scroll footer: the list grows as the container bottom nears -->
          <div v-if="listMode === 'scroll' && movies.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>滑动加载更多作品中...</span>
            </div>
            <div v-else-if="movies.length >= totalMovies && totalMovies > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>已加载全部 {{ totalMovies.toLocaleString() }} 部作品</span>
              <span class="w-12 h-px bg-surface-2"></span>
            </div>
          </div>

          <!-- Paged mode: explicit controls, incl. a customisable page size -->
          <PaginationBar
            v-if="listMode === 'paged' && movies.length > 0"
            :page="moviePage"
            :page-size="pageSize"
            :total="totalMovies"
            :loading="isLoading"
            :page-size-options="PAGE_SIZE_OPTIONS"
            @update:page="goToPage"
            @update:page-size="setPageSize"
          />

          <!-- Empty State -->
          <div v-else-if="!isLoading" class="text-center py-24 space-y-3">
            <Film class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">未找到符合条件的影片</div>
            <div class="text-xs text-fg-5">尝试更换搜索关键词或重置筛选条件</div>
          </div>
        </div>

        <!-- 2. Performers Tab -->
        <div v-else-if="currentTab === 'performers'" class="space-y-6">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <h1 class="text-xl font-bold text-fg tracking-tight">演员档案库</h1>
              <span class="text-xs text-fg-4 font-mono">({{ performers.length }} / {{ totalPerformers.toLocaleString() }} 位)</span>
            </div>

            <div class="flex items-center gap-3">
              <!-- Performer filter button (issue #7) -->
              <button
                @click="isFilterOpen = true"
                :class="[
                  'px-3 py-1.5 rounded-xl text-xs font-semibold border flex items-center gap-1.5 transition',
                  activePerformerFilterCount > 0
                    ? 'bg-accent-fill/10 border-accent-fill/40 text-accent'
                    : 'bg-surface border-line text-fg-3 hover:text-fg-2'
                ]"
                title="筛选演员属性"
              >
                <Sparkles class="w-3.5 h-3.5" />
                <span>属性筛选</span>
                <span
                  v-if="activePerformerFilterCount > 0"
                  class="px-1.5 rounded-full bg-accent-fill text-on-fill text-[10px] font-bold"
                >
                  {{ activePerformerFilterCount }}
                </span>
              </button>

              <!-- Active facet chips -->
              <div class="flex items-center gap-1.5 flex-wrap max-w-lg">
                <span
                  v-for="chip in activeFacetChips"
                  :key="`${chip.key}:${chip.value}`"
                  class="text-[11px] px-2 py-0.5 rounded-lg bg-surface-2 text-fg-2 border border-line-strong inline-flex items-center gap-1"
                >
                  <span class="text-fg-4">{{ chip.label }}</span>
                  {{ chip.value }}
                  <button @click="togglePerformerFacet(chip.key as any, chip.value)" class="hover:text-fg">×</button>
                </span>
                <span
                  v-if="performerFilters.hasImage"
                  class="text-[11px] px-2 py-0.5 rounded-lg bg-surface-2 text-fg-2 border border-line-strong inline-flex items-center gap-1"
                >
                  有照片
                  <button @click="performerFilters.hasImage = false" class="hover:text-fg">×</button>
                </span>
                <span
                  v-if="performerFilters.minMovies != null"
                  class="text-[11px] px-2 py-0.5 rounded-lg bg-surface-2 text-fg-2 border border-line-strong inline-flex items-center gap-1"
                >
                  ≥{{ performerFilters.minMovies }} 部作品
                  <button @click="performerFilters.minMovies = null" class="hover:text-fg">×</button>
                </span>
                <button
                  v-if="activePerformerFilterCount > 0"
                  @click="resetPerformerFilters"
                  class="text-[11px] text-fg-4 hover:text-accent underline"
                >
                  清除全部
                </button>
              </div>

              <!-- How the list pages in: auto-load on scroll, or explicit pages -->
              <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <button
                  v-for="m in [
                    { id: 'scroll', label: '滑动加载' },
                    { id: 'paged', label: '翻页' }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? '滚动到底部自动加载下一页' : '显示翻页按钮，可自定义每页条目数'"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid columns adjuster -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">每行</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="减少每行列数"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="增加每行列数"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">列</span>
              </div>
            </div>
          </div>

          <div
            v-if="performers.length > 0"
            class="grid gap-4 transition-all duration-200"
            :style="{ gridTemplateColumns: `repeat(${activeCols}, minmax(0, 1fr))` }"
          >
            <div
              v-for="p in performers"
              :key="p.id"
              @click="openPerformerDetail(p.id)"
              class="p-4 rounded-2xl bg-surface/60 border border-line hover:border-accent-fill/40 hover:bg-surface transition-all cursor-pointer flex flex-col items-center text-center group"
            >
              <!-- Portrait when scraped, letter avatar otherwise (issue #6) -->
              <div class="w-16 h-16 rounded-2xl overflow-hidden shrink-0 shadow ring-1 ring-line-strong/60 group-hover:ring-accent-fill/50 transition">
                <img
                  v-if="p.image_url"
                  :src="getImageUrl(p.image_url)"
                  :alt="p.name"
                  loading="lazy"
                  referrerpolicy="no-referrer"
                  class="w-full h-full object-cover object-top group-hover:scale-105 transition-transform duration-300"
                  @error="(e) => ((e.target as HTMLImageElement).style.display = 'none')"
                />
                <div
                  v-else
                  class="w-full h-full bg-gradient-to-tr from-surface-2 to-surface-3 group-hover:from-accent-fill group-hover:to-accent-2 flex items-center justify-center font-bold text-lg text-fg-3 group-hover:text-on-fill transition"
                >
                  {{ p.name.charAt(0).toUpperCase() }}
                </div>
              </div>
              <h3 class="text-xs font-semibold text-fg-2 mt-3 group-hover:text-accent transition truncate w-full">
                {{ p.name }}
              </h3>
              <div v-if="p.build || p.height" class="text-[10px] text-fg-4 mt-1 truncate w-full">
                {{ p.build || trMeasure(p.height) }}
              </div>
              <div v-if="p.works_count ?? p.movies_count" class="text-[10px] text-fg-5 mt-0.5">
                {{ p.works_count ?? p.movies_count }} 部作品
              </div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else-if="!isLoading" class="text-center py-24 space-y-3">
            <UserIcon class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">没有符合条件的演员</div>
            <div class="text-xs text-fg-5">
              当前仅有 {{ performerFacets.enriched }} 位演员抓取过身体属性档案，可放宽筛选条件或先补全演员数据
            </div>
          </div>

          <!-- Infinite-scroll footer: the list grows as the container bottom nears -->
          <div v-if="listMode === 'scroll' && performers.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>滑动加载更多演员中...</span>
            </div>
            <div v-else-if="performers.length >= totalPerformers && totalPerformers > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>已加载全部 {{ totalPerformers.toLocaleString() }} 位演员</span>
              <span class="w-12 h-px bg-surface-2"></span>
            </div>
          </div>

          <!-- Paged mode: explicit controls, incl. a customisable page size -->
          <PaginationBar
            v-if="listMode === 'paged' && performers.length > 0"
            :page="performerPage"
            :page-size="pageSize"
            :total="totalPerformers"
            :loading="isLoading"
            :page-size-options="PAGE_SIZE_OPTIONS"
            @update:page="goToPage"
            @update:page-size="setPageSize"
          />
        </div>

        <!-- 3. Studio Library — the third axis of the library, next to films and performers -->
        <div v-else-if="currentTab === 'studios'" class="space-y-6">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <h1 class="text-xl font-bold text-fg tracking-tight">片商库</h1>
              <span class="text-xs text-fg-4 font-mono">({{ studioRows.length }} / {{ totalStudioRows.toLocaleString() }} 家)</span>
            </div>

            <div class="flex items-center gap-3">
              <!-- Sort: there is no filter drawer for studios, so the ordering lives here -->
              <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <button
                  v-for="s in STUDIO_SORTS"
                  :key="s.id"
                  @click="studioSortBy = s.id"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    studioSortBy === s.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                >
                  {{ s.label }}
                </button>
              </div>

              <!-- How the list pages in: auto-load on scroll, or explicit pages -->
              <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <button
                  v-for="m in [
                    { id: 'scroll', label: '滑动加载' },
                    { id: 'paged', label: '翻页' }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? '滚动到底部自动加载下一页' : '显示翻页按钮，可自定义每页条目数'"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid columns adjuster -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">每行</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="减少每行列数"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="增加每行列数"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">列</span>
              </div>
            </div>
          </div>

          <!-- Cards carry no artwork: the library has no studio logo, and picking one
               of the studio's film covers per card would cost a scan per group. -->
          <div
            v-if="studioRows.length > 0"
            class="grid gap-4 transition-all duration-200"
            :style="{ gridTemplateColumns: `repeat(${activeCols}, minmax(0, 1fr))` }"
          >
            <div
              v-for="s in studioRows"
              :key="s.name"
              @click="openStudioDetail(s)"
              class="p-4 rounded-2xl bg-surface/60 border border-line hover:border-accent-fill/40 hover:bg-surface transition-all cursor-pointer flex flex-col items-center text-center group"
            >
              <div class="w-16 h-16 rounded-2xl overflow-hidden shrink-0 shadow ring-1 ring-line-strong/60 group-hover:ring-accent-fill/50 transition">
                <div class="w-full h-full bg-gradient-to-tr from-accent-deep to-accent-2 flex items-center justify-center text-2xl font-black text-on-fill/70">
                  {{ s.name.charAt(0).toUpperCase() }}
                </div>
              </div>
              <h3 class="text-xs font-semibold text-fg-2 mt-3 group-hover:text-accent transition truncate w-full">
                {{ s.name }}
              </h3>
              <div class="text-[10px] text-fg-4 mt-1">{{ s.works_count }} 部作品</div>
              <div v-if="s.episodes_count" class="text-[10px] text-fg-5 mt-0.5">
                {{ s.episodes_count }} 个片段
              </div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else-if="!isLoading" class="text-center py-24 space-y-3">
            <Building2 class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">没有符合条件的片商</div>
            <div class="text-xs text-fg-5">试试更换关键词，或清空搜索框</div>
          </div>

          <!-- Infinite-scroll footer: the list grows as the container bottom nears -->
          <div v-if="listMode === 'scroll' && studioRows.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>滑动加载更多片商中...</span>
            </div>
            <div v-else-if="studioRows.length >= totalStudioRows && totalStudioRows > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>已加载全部 {{ totalStudioRows.toLocaleString() }} 家片商</span>
              <span class="w-12 h-px bg-surface-2"></span>
            </div>
          </div>

          <!-- Paged mode: explicit controls, incl. a customisable page size -->
          <PaginationBar
            v-if="listMode === 'paged' && studioRows.length > 0"
            :page="studioPage"
            :page-size="pageSize"
            :total="totalStudioRows"
            :loading="isLoading"
            :page-size-options="PAGE_SIZE_OPTIONS"
            @update:page="goToPage"
            @update:page-size="setPageSize"
          />
        </div>

        <!-- 4. Episodes Tab — the whole episodes table, browsable on its own -->
        <div v-else-if="currentTab === 'episodes'" class="space-y-6">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <h1 class="text-xl font-bold text-fg tracking-tight">分集库</h1>
              <span class="text-xs text-fg-4 font-mono">({{ episodeRows.length }} / {{ totalEpisodeRows.toLocaleString() }} 个片段)</span>
            </div>

            <div class="flex items-center gap-3">
              <!-- Sort: the same three orderings the drawer offers -->
              <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <button
                  v-for="s in EPISODE_SORTS"
                  :key="s.id"
                  @click="episodeSortBy = s.id"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    episodeSortBy === s.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                >
                  {{ s.label }}
                </button>
              </div>

              <!-- How the list pages in: auto-load on scroll, or explicit pages -->
              <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <button
                  v-for="m in [
                    { id: 'scroll', label: '滑动加载' },
                    { id: 'paged', label: '翻页' }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? '滚动到底部自动加载下一页' : '显示翻页按钮，可自定义每页条目数'"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid columns adjuster -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">每行</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="减少每行列数"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="增加每行列数"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">列</span>
              </div>
            </div>
          </div>

          <div
            v-if="episodeRows.length > 0"
            class="grid gap-4 transition-all duration-200"
            :style="{ gridTemplateColumns: `repeat(${activeCols}, minmax(0, 1fr))` }"
          >
            <EpisodeCard
              v-for="ep in episodeRows"
              :key="ep.id"
              :episode="ep"
              :lang="descLang"
              :is-favorite="isFavorite('episode', ep.id)"
              @select="openEpisodeDetail(ep, episodeRows)"
              @select-movie-id="openMovieDetailById"
              @select-performer="openPerformerDetail"
              @filter-studio="filterByStudio"
              @toggle-favorite="toggleEpisodeFavorite"
            />
          </div>

          <!-- Empty state -->
          <div v-else-if="!isLoading" class="text-center py-24 space-y-3">
            <Clapperboard class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">没有符合条件的片段</div>
            <div v-if="activeEpisodeFilterCount > 0 || episodeQuery" class="text-xs text-fg-5">
              试试更换关键词，或在筛选面板里重置条件
            </div>
            <!-- The episodes table starts empty until the dedicated scrape runs: the
                 film scrape only records the scenes it happens to walk past. -->
            <div v-else class="text-xs text-fg-5 max-w-md mx-auto leading-relaxed">
              分集库目前为空。影片刮削只记录顺带遇到的分集，完整的分集清单需要用
              <span class="font-mono text-fg-4">--mode episodes</span> 单独刮削一轮。
            </div>
          </div>

          <!-- Infinite-scroll footer: the list grows as the container bottom nears -->
          <div v-if="listMode === 'scroll' && episodeRows.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>滑动加载更多片段中...</span>
            </div>
            <div v-else-if="episodeRows.length >= totalEpisodeRows && totalEpisodeRows > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>已加载全部 {{ totalEpisodeRows.toLocaleString() }} 个片段</span>
              <span class="w-12 h-px bg-surface-2"></span>
            </div>
          </div>

          <!-- Paged mode: explicit controls, incl. a customisable page size -->
          <PaginationBar
            v-if="listMode === 'paged' && episodeRows.length > 0"
            :page="episodePage"
            :page-size="pageSize"
            :total="totalEpisodeRows"
            :loading="isLoading"
            :page-size-options="PAGE_SIZE_OPTIONS"
            @update:page="goToPage"
            @update:page-size="setPageSize"
          />
        </div>

        <!-- 5. Favorites Tab — five server-driven sections -->
        <div v-else-if="currentTab === 'favorites'" class="space-y-8">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <h1 class="text-xl font-bold text-fg tracking-tight">我的收藏</h1>
            <div class="flex items-center gap-3">
              <span class="text-xs text-fg-4 font-mono">({{ favoriteTotal }} 项)</span>
              <!-- Grid columns adjuster: only meaningful once there are movie cards -->
              <div
                v-if="favMovies.length > 0"
                class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs"
              >
                <span class="text-fg-4 text-[11px]">每行</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="减少每行列数"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  title="增加每行列数"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">列</span>
              </div>
            </div>
          </div>

          <div v-if="favoritesLoading && favoriteTotal === 0" class="text-center py-24">
            <Loader2 class="w-8 h-8 text-accent-fill animate-spin mx-auto" />
          </div>

          <template v-else-if="favoriteTotal > 0">
            <!-- 1. Movies -->
            <section v-if="favMovies.length > 0" class="space-y-3">
              <div class="flex items-center gap-2 pb-2 border-b border-line">
                <Film class="w-4 h-4 text-accent" />
                <h2 class="text-sm font-bold text-fg">{{ FAVORITE_LABELS.movie }}</h2>
                <span class="text-xs text-fg-4 font-mono">{{ favMovies.length }}</span>
              </div>
              <div
                class="grid transition-all duration-200"
                :class="viewMode === 'grid' ? 'gap-4 sm:gap-6' : 'gap-3'"
                :style="{ gridTemplateColumns: `repeat(${activeCols}, minmax(0, 1fr))` }"
              >
                <MovieCard
                  v-for="f in favMovies"
                  :key="f.key"
                  :movie="asMovie(f)"
                  :translated="Boolean(f.has_zh)"
                  :is-favorite="true"
                  :view="viewMode"
                  :lang="descLang"
                  @select="openMovieDetail(asMovie(f))"
                  @toggle-favorite="toggleFavoriteEntity('movie', f.key)"
                />
              </div>
            </section>

            <!-- 2. Performers -->
            <section v-if="favPerformers.length > 0" class="space-y-3">
              <div class="flex items-center gap-2 pb-2 border-b border-line">
                <UserIcon class="w-4 h-4 text-accent" />
                <h2 class="text-sm font-bold text-fg">{{ FAVORITE_LABELS.performer }}</h2>
                <span class="text-xs text-fg-4 font-mono">{{ favPerformers.length }}</span>
              </div>
              <div class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-3">
                <div
                  v-for="f in favPerformers"
                  :key="f.key"
                  @click="openPerformerDetail(Number(f.key))"
                  class="group relative rounded-2xl overflow-hidden bg-surface/60 border border-line/80 hover:border-accent-fill/50 transition-all duration-200 cursor-pointer select-none"
                >
                  <div class="relative w-full aspect-[3/4] bg-gradient-to-tr from-accent-deep to-accent-2">
                    <span class="absolute inset-0 flex items-center justify-center text-2xl font-black text-on-fill/70">
                      {{ (f.name || '?').charAt(0).toUpperCase() }}
                    </span>
                    <img
                      v-if="f.image_url"
                      :src="getImageUrl(f.image_url)"
                      :alt="f.name"
                      loading="lazy"
                      referrerpolicy="no-referrer"
                      class="absolute inset-0 w-full h-full object-cover object-top group-hover:scale-105 transition-transform duration-300"
                      @error="hideBrokenImage"
                    />
                    <button
                      @click.stop="toggleFavoriteEntity('performer', f.key)"
                      class="on-scrim absolute top-1.5 right-1.5 w-6 h-6 rounded-full bg-scrim/50 hover:bg-scrim/80 backdrop-blur-md flex items-center justify-center text-danger transition"
                      title="取消收藏该演员"
                    >
                      <Heart class="w-3 h-3" fill="currentColor" />
                    </button>
                  </div>
                  <div class="p-2">
                    <div class="text-[11px] font-semibold text-fg-2 truncate" :title="f.name">{{ f.name }}</div>
                  </div>
                </div>
              </div>
            </section>

            <!-- 3. Episodes -->
            <section v-if="favEpisodes.length > 0" class="space-y-3">
              <div class="flex items-center gap-2 pb-2 border-b border-line">
                <Layers class="w-4 h-4 text-accent" />
                <h2 class="text-sm font-bold text-fg">{{ FAVORITE_LABELS.episode }}</h2>
                <span class="text-xs text-fg-4 font-mono">{{ favEpisodes.length }}</span>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                <div
                  v-for="f in favEpisodes"
                  :key="f.key"
                  @click="f.movie_id && openMovieDetailById(f.movie_id)"
                  class="group flex gap-3 rounded-2xl bg-surface/60 border border-line/80 hover:border-accent-fill/50 transition-all duration-200 overflow-hidden cursor-pointer select-none p-2.5"
                >
                  <div class="relative w-24 shrink-0 aspect-video rounded-xl overflow-hidden bg-sunken">
                    <img
                      v-if="f.thumbnail_url"
                      :src="getImageUrl(f.thumbnail_url)"
                      :alt="f.title || ''"
                      loading="lazy"
                      referrerpolicy="no-referrer"
                      class="w-full h-full object-cover"
                    />
                    <div v-else class="w-full h-full flex items-center justify-center text-fg-5">
                      <Layers class="w-5 h-5 stroke-1" />
                    </div>
                  </div>
                  <div class="flex-1 min-w-0 flex flex-col justify-between gap-1">
                    <div class="min-w-0">
                      <div class="flex items-start justify-between gap-2">
                        <span class="text-xs font-bold text-accent-soft truncate" :title="f.title || ''">{{ f.title }}</span>
                        <button
                          @click.stop="toggleFavoriteEntity('episode', f.key)"
                          class="shrink-0 text-danger transition"
                          title="取消收藏该片段"
                        >
                          <Heart class="w-3.5 h-3.5" fill="currentColor" />
                        </button>
                      </div>
                      <div v-if="f.movie_title" class="text-[11px] text-fg-3 mt-0.5 flex items-center gap-1 truncate">
                        <Film class="w-2.5 h-2.5 text-fg-4 shrink-0" />
                        <span class="truncate" :title="favFilmFull(f)">出处: {{ favFilmTitle(f) }}</span>
                      </div>
                    </div>
                    <div class="flex items-center gap-2 text-[10px] text-fg-4">
                      <span v-if="f.studio_name" class="truncate max-w-[120px]" :title="f.studio_name">{{ f.studio_name }}</span>
                      <span v-if="f.has_zh" class="text-success flex items-center gap-0.5 shrink-0">
                        <Languages class="w-2.5 h-2.5" />中
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- 4. Studios — clicking opens the studio page, as a favorited performer does -->
            <section v-if="favStudios.length > 0" class="space-y-3">
              <div class="flex items-center gap-2 pb-2 border-b border-line">
                <Building2 class="w-4 h-4 text-accent" />
                <h2 class="text-sm font-bold text-fg">{{ FAVORITE_LABELS.studio }}</h2>
                <span class="text-xs text-fg-4 font-mono">{{ favStudios.length }}</span>
              </div>
              <div class="flex flex-wrap gap-2">
                <div
                  v-for="f in favStudios"
                  :key="f.key"
                  class="group flex items-center gap-2 pl-3 pr-1.5 py-1.5 rounded-xl bg-surface/70 border border-line hover:border-accent-fill/50 transition"
                >
                  <button
                    @click="openStudioDetail({ name: f.key, works_count: f.works_count ?? undefined })"
                    class="text-xs font-medium text-fg-2 hover:text-accent-soft transition"
                    :title="`打开 ${f.key} 的片商档案`"
                  >
                    {{ f.key }}
                  </button>
                  <span class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2 text-fg-4 font-mono">{{ f.works_count || 0 }}</span>
                  <button
                    @click.stop="toggleFavoriteEntity('studio', f.key)"
                    class="text-danger hover:text-danger-soft transition"
                    title="取消收藏该片商"
                  >
                    <Heart class="w-3 h-3" fill="currentColor" />
                  </button>
                </div>
              </div>
            </section>

            <!-- 5. Directors -->
            <section v-if="favDirectors.length > 0" class="space-y-3">
              <div class="flex items-center gap-2 pb-2 border-b border-line">
                <Clapperboard class="w-4 h-4 text-accent" />
                <h2 class="text-sm font-bold text-fg">{{ FAVORITE_LABELS.director }}</h2>
                <span class="text-xs text-fg-4 font-mono">{{ favDirectors.length }}</span>
              </div>
              <div class="flex flex-wrap gap-2">
                <div
                  v-for="f in favDirectors"
                  :key="f.key"
                  class="group flex items-center gap-2 pl-3 pr-1.5 py-1.5 rounded-xl bg-surface/70 border border-line hover:border-accent-fill/50 transition"
                >
                  <button @click="filterByDirector(f.key)" class="text-xs font-medium text-fg-2 hover:text-accent-soft transition" :title="`查看 ${f.key} 导演的全部影片`">
                    {{ f.key }}
                  </button>
                  <span class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2 text-fg-4 font-mono">{{ f.works_count || 0 }}</span>
                  <button
                    @click.stop="toggleFavoriteEntity('director', f.key)"
                    class="text-danger hover:text-danger-soft transition"
                    title="取消收藏该导演"
                  >
                    <Heart class="w-3 h-3" fill="currentColor" />
                  </button>
                </div>
              </div>
            </section>
          </template>

          <div v-else class="text-center py-24 space-y-3">
            <Heart class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">暂无收藏</div>
            <div class="text-xs text-fg-5">影片、演员、片商、导演和分集片段都可以收藏，点击心形图标即可加入</div>
          </div>
        </div>

        <!-- 6. Settings & Cache Tab -->
        <div v-else-if="currentTab === 'settings'" class="max-w-3xl space-y-6">
          <h1 class="text-xl font-bold text-fg tracking-tight">存储、缓存与系统设置</h1>

          <!-- Section 0: 外观. Three styles × dark/light, flat, plus 跟随系统. The
               swatch strip is inline-styled because it draws colours this page is not
               wearing — a preview of 经典·浅 has to be drawn in 经典·浅 while the
               settings panel is still 经典·暗. -->
          <div class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4">
            <div class="flex items-center gap-3">
              <Palette class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">外观主题</div>
                <div class="text-xs text-fg-3">默认跟随系统；三套风格各自有深色与浅色两版</div>
              </div>
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <button
                v-for="opt in themeOptions"
                :key="opt.id"
                @click="setTheme(opt.id)"
                class="text-left p-3 rounded-xl border transition flex items-start gap-3"
                :class="themeChoice === opt.id
                  ? 'bg-accent-fill/10 border-accent-fill/40'
                  : 'bg-surface border-line-strong hover:border-line-strong'"
              >
                <span
                  class="shrink-0 w-9 h-9 rounded-lg border border-line-strong/60 overflow-hidden flex flex-col"
                  :style="{ backgroundColor: opt.swatch[0] }"
                  aria-hidden="true"
                >
                  <span class="flex-1" :style="{ backgroundColor: opt.swatch[1] }"></span>
                  <span class="h-2.5" :style="{ backgroundColor: opt.swatch[2] }"></span>
                </span>
                <span class="min-w-0">
                  <span class="flex items-center gap-2">
                    <span
                      class="text-xs font-bold"
                      :class="themeChoice === opt.id ? 'text-accent-soft' : 'text-fg-2'"
                    >{{ opt.label }}</span>
                    <Check v-if="themeChoice === opt.id" class="w-3 h-3 text-accent" />
                  </span>
                  <span class="block text-[11px] text-fg-4 mt-1 leading-relaxed">{{ opt.hint }}</span>
                </span>
              </button>
            </div>
          </div>

          <!-- Section 1: SQLite Engine & Stats -->
          <div class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4">
            <div class="flex items-center gap-3">
              <HardDrive class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">本地离线数据中心</div>
                <div class="text-xs text-fg-3">SQLite3 WAL 极速引擎 + FTS5 全文搜索</div>
              </div>
            </div>
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs pt-2">
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">影片总收录</span>
                <div class="text-base font-bold text-fg mt-0.5">{{ stats ? stats.movies.toLocaleString() : 0 }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">演员总收录</span>
                <div class="text-base font-bold text-fg mt-0.5">{{ stats ? stats.performers.toLocaleString() : 0 }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">分集/片段</span>
                <div class="text-base font-bold text-accent mt-0.5">{{ stats ? stats.episodes.toLocaleString() : 0 }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">演职关联</span>
                <div class="text-base font-bold text-fg-2 mt-0.5">{{ stats ? stats.movie_performers.toLocaleString() : 0 }}</div>
              </div>
            </div>
          </div>

          <!-- Section 2: Offline Image Disk Cache System -->
          <div class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <ImageIcon class="w-5 h-5 text-accent" />
                <div>
                  <div class="text-sm font-bold text-fg">离线图片磁盘缓存系统</div>
                  <div class="text-xs text-fg-3">自动下载海报与分集图至本地磁盘，彻底告别外网依赖</div>
                </div>
              </div>
              <button
                @click="loadCacheStats"
                class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 hover:text-fg transition"
                title="刷新缓存统计"
              >
                <RefreshCw class="w-3.5 h-3.5" />
              </button>
            </div>

            <!-- Stats metrics -->
            <div class="grid grid-cols-2 gap-3 text-xs pt-1">
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">已缓存图片数量</span>
                <div class="text-base font-bold text-success mt-0.5">{{ cacheStats.count.toLocaleString() }} 张</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">占用磁盘空间</span>
                <div class="text-base font-bold text-accent-soft mt-0.5">{{ cacheStats.size_mb }} MB</div>
              </div>
            </div>

            <!-- Cache directory location -->
            <div v-if="cacheStats.path" class="text-[11px] text-fg-4 font-mono bg-sunken p-2.5 rounded-xl border border-line truncate">
              本地存储目录: {{ cacheStats.path }}
            </div>

            <div v-if="cacheStatusMsg" class="p-3 rounded-xl bg-accent-fill/10 border border-accent-fill/20 text-xs text-accent-soft">
              {{ cacheStatusMsg }}
            </div>

            <!-- Action buttons -->
            <div class="flex items-center gap-3 pt-2">
              <button
                @click="handleBatchDownloadCache"
                :disabled="isCacheLoading"
                class="px-4 py-2 rounded-xl bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs shadow-lg shadow-accent-fill/20 flex items-center gap-2 transition disabled:opacity-50"
              >
                <Download class="w-3.5 h-3.5" />
                <span>一键预下载离线图片库</span>
              </button>

              <button
                @click="handleClearCache"
                :disabled="isCacheLoading"
                class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-danger-fill/20 text-fg-2 hover:text-danger-soft border border-line-strong hover:border-danger-fill/30 text-xs font-medium flex items-center gap-2 transition disabled:opacity-50"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>清空图片缓存</span>
              </button>
            </div>
          </div>

          <!-- Section 3: Synopsis Machine Translation (EN -> ZH, issue #4) -->
          <div class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <Languages class="w-5 h-5 text-accent" />
                <div>
                  <div class="text-sm font-bold text-fg">剧情简介中文翻译</div>
                  <div class="text-xs text-fg-3">
                    调用大模型 API 把英文简介批量译成中文并写回本地库，之后完全离线可用
                  </div>
                </div>
              </div>
              <button
                @click="loadTranslationStats"
                class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 hover:text-fg transition"
                title="刷新翻译进度"
              >
                <RefreshCw class="w-3.5 h-3.5" />
              </button>
            </div>

            <!-- Progress metrics -->
            <div v-if="translationStats" class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs pt-1">
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">可翻译简介</span>
                <div class="text-base font-bold text-fg mt-0.5">{{ translationStats.translation_total.toLocaleString() }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">已翻译</span>
                <div class="text-base font-bold text-success mt-0.5">{{ translationStats.translation_done.toLocaleString() }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">待翻译</span>
                <div class="text-base font-bold text-accent-soft mt-0.5">{{ translationStats.translation_pending.toLocaleString() }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">翻译失败</span>
                <div class="text-base font-bold text-danger mt-0.5">{{ translationStats.translation_failed.toLocaleString() }}</div>
              </div>
            </div>

            <!-- Progress bar -->
            <div
              v-if="translationStats && translationStats.translation_total > 0"
              class="h-2 rounded-full bg-surface-2 overflow-hidden"
            >
              <div
                class="h-full bg-gradient-to-r from-accent-fill to-success transition-all duration-500"
                :style="{ width: `${(translationStats.translation_done / translationStats.translation_total) * 100}%` }"
              ></div>
            </div>

            <!-- Translation mode: one film at a time vs. explicit batch runs -->
            <div v-if="!IS_TAURI" class="p-4 rounded-xl bg-sunken/60 border border-line space-y-3">
              <div class="text-xs font-semibold text-fg-2">翻译方式</div>
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <button
                  @click="setTranslateMode('single')"
                  class="text-left p-3 rounded-xl border transition"
                  :class="translateMode === 'single'
                    ? 'bg-accent-fill/10 border-accent-fill/40'
                    : 'bg-surface border-line-strong hover:border-line-strong'"
                >
                  <div class="flex items-center gap-2">
                    <span class="text-xs font-bold"
                      :class="translateMode === 'single' ? 'text-accent-soft' : 'text-fg-2'">
                      单部自动翻译
                    </span>
                    <span class="text-[10px] px-1.5 py-0.5 rounded bg-success-fill/20 text-success-soft border border-success-fill/30">省 token</span>
                  </div>
                  <div class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                    打开某部影片时才翻译那一部。适合边看边译，不会一次性消耗大量额度。
                  </div>
                </button>

                <button
                  @click="setTranslateMode('batch')"
                  class="text-left p-3 rounded-xl border transition"
                  :class="translateMode === 'batch'
                    ? 'bg-accent-fill/10 border-accent-fill/40'
                    : 'bg-surface border-line-strong hover:border-line-strong'"
                >
                  <div class="flex items-center gap-2">
                    <span class="text-xs font-bold"
                      :class="translateMode === 'batch' ? 'text-accent-soft' : 'text-fg-2'">
                      批量翻译
                    </span>
                  </div>
                  <div class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                    打开影片时不翻译，改由下方按钮一次性批量处理。适合把整库译完。
                  </div>
                </button>
              </div>
              <div class="text-[11px] text-fg-4">
                <template v-if="translateMode === 'single'">
                  已开启单部自动翻译：之后每打开一部尚未翻译的影片会自动翻译它，同一部影片本次运行内只翻译一次。
                </template>
                <template v-else>
                  当前为批量模式：打开影片不会触发翻译，请用下方按钮批量处理。
                </template>
              </div>
            </div>

            <!-- Translation sources: several saved API providers, one active.
                 Shown in both modes. The desktop build edits the same
                 translate_config.json natively (src-tauri/src/commands/translate.rs),
                 so this is the one part of translation that does not need Python. -->
            <div class="pt-1 space-y-3">
              <div class="flex items-center justify-between">
                <div class="text-xs font-semibold text-fg-2">翻译服务来源</div>
                <button
                  @click="openProviderForm()"
                  class="px-3 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong flex items-center gap-1.5 transition"
                >
                  <Sparkles class="w-3 h-3 text-accent" />
                  <span>添加来源</span>
                </button>
              </div>

              <div v-if="providerList.length === 0" class="text-xs text-accent-soft/90 p-3 rounded-xl bg-accent-fill/10 border border-accent-fill/20">
                尚未配置任何来源。点「添加来源」选择服务商（DeepSeek / Claude / Gemini / 本地 Ollama 等）并填入 API Key。
              </div>

              <div v-else class="space-y-2">
                <div
                  v-for="p in providerList"
                  :key="p.name"
                  class="flex items-center justify-between gap-3 p-3 rounded-xl border transition"
                  :class="p.active
                    ? 'bg-accent-fill/10 border-accent-fill/30'
                    : 'bg-sunken/60 border-line'"
                >
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="text-xs font-semibold text-fg truncate">{{ p.label }}</span>
                      <span v-if="p.active" class="text-[10px] px-1.5 py-0.5 rounded bg-accent-fill text-on-fill font-bold">使用中</span>
                      <span v-if="!p.has_key" class="text-[10px] px-1.5 py-0.5 rounded bg-danger-fill/20 text-danger-soft border border-danger-fill/30">缺 API Key</span>
                    </div>
                    <div class="text-[11px] text-fg-4 font-mono truncate mt-0.5">
                      {{ p.type }} · {{ p.model || '默认模型' }} · {{ p.base_url || '默认端点' }}
                    </div>
                    <div v-if="p.key_hint" class="text-[10px] text-fg-5 font-mono">Key: {{ p.key_hint }}</div>
                  </div>
                  <div class="flex items-center gap-1.5 shrink-0">
                    <button
                      v-if="!p.active"
                      @click="activateProvider(p.name)"
                      class="px-2.5 py-1.5 rounded-lg bg-surface-2 hover:bg-accent-fill/20 text-fg-2 hover:text-accent-soft text-[11px] border border-line-strong transition"
                    >设为当前</button>
                    <!-- 试译 makes a real outbound call through translate.py's providers,
                         which the desktop build does not carry. Hidden rather than
                         disabled so it cannot look like a working button. -->
                    <button
                      v-if="!IS_TAURI"
                      @click="testProvider(p.name)"
                      :disabled="providerBusy"
                      class="px-2.5 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong transition disabled:opacity-40"
                    >测试</button>
                    <button
                      @click="openProviderForm(p)"
                      class="px-2.5 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong transition"
                    >编辑</button>
                    <button
                      @click="removeProvider(p.name)"
                      class="p-1.5 rounded-lg bg-surface-2 hover:bg-danger-fill/20 text-fg-3 hover:text-danger-soft border border-line-strong transition"
                      title="删除该来源"
                    >
                      <Trash2 class="w-3 h-3" />
                    </button>
                  </div>
                </div>
              </div>

              <!-- Add / edit form -->
              <div v-if="providerForm.open" class="p-4 rounded-xl bg-sunken/80 border border-line-strong space-y-3">
                <div class="text-xs font-semibold text-fg">
                  {{ providerForm.editing ? `编辑来源：${providerForm.editing}` : '添加翻译来源' }}
                </div>

                <div v-if="!providerForm.editing" class="flex flex-wrap gap-1.5">
                  <button
                    v-for="preset in providerPresets"
                    :key="preset.id"
                    @click="applyPreset(preset)"
                    :title="preset.hint"
                    class="px-2.5 py-1 rounded-lg text-[11px] border transition"
                    :class="providerForm.name === preset.id
                      ? 'bg-accent-fill text-on-fill border-accent-fill font-bold'
                      : 'bg-surface-2 text-fg-2 border-line-strong hover:bg-surface-3'"
                  >{{ preset.label }}</button>
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <label class="space-y-1">
                    <span class="text-[11px] text-fg-3">配置名称（唯一标识）</span>
                    <input v-model="providerForm.name" :disabled="!!providerForm.editing"
                      placeholder="deepseek"
                      class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono disabled:opacity-60 focus:border-accent-fill/50 focus:outline-none" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-[11px] text-fg-3">显示名称</span>
                    <input v-model="providerForm.label" placeholder="DeepSeek 深度求索"
                      class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg focus:border-accent-fill/50 focus:outline-none" />
                  </label>
                  <label class="space-y-1">
                    <span class="text-[11px] text-fg-3">接口类型</span>
                    <select v-model="providerForm.type"
                      class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg focus:border-accent-fill/50 focus:outline-none">
                      <option value="openai">openai（OpenAI 兼容接口）</option>
                      <option value="anthropic">anthropic（Claude 官方接口）</option>
                      <option value="gemini">gemini（Google Gemini）</option>
                    </select>
                  </label>
                  <label class="space-y-1">
                    <span class="text-[11px] text-fg-3">模型名</span>
                    <input v-model="providerForm.model" placeholder="deepseek-flash"
                      class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none" />
                  </label>
                  <label class="space-y-1 sm:col-span-2">
                    <span class="text-[11px] text-fg-3">API 端点 (Base URL)</span>
                    <input v-model="providerForm.base_url" placeholder="https://api.deepseek.com"
                      class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none" />
                  </label>
                  <label class="space-y-1 sm:col-span-2">
                    <span class="text-[11px] text-fg-3">
                      API Key
                      <span v-if="providerForm.editing" class="text-fg-4">（留空则保持原 Key 不变）</span>
                    </span>
                    <input v-model="providerForm.api_key" type="password" autocomplete="off"
                      :placeholder="providerForm.editing ? '••••••••（不修改）' : 'sk-...'"
                      class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none" />
                  </label>
                </div>

                <div class="text-[11px] text-fg-4 leading-relaxed">
                  API Key 只写入本机 <code class="font-mono">translate_config.json</code>（权限 600），
                  不会写入数据库，也<b class="text-fg-3">不会回传给前端</b>。
                </div>

                <div class="flex items-center gap-2">
                  <button
                    @click="saveProvider"
                    :disabled="providerBusy"
                    class="px-4 py-2 rounded-lg bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs transition disabled:opacity-40"
                  >保存</button>
                  <button
                    @click="providerForm.open = false"
                    class="px-4 py-2 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs border border-line-strong transition"
                  >取消</button>
                </div>
              </div>

              <div
                v-if="providerTest"
                class="p-3 rounded-xl text-[11px] font-mono whitespace-pre-wrap leading-relaxed"
                :class="providerTest.ok
                  ? 'bg-success-fill/10 border border-success-fill/20 text-success-soft'
                  : 'bg-danger-fill/10 border border-danger-fill/20 text-danger-soft'"
              >{{ providerTest.text }}</div>

              <div v-if="providerMsg" class="text-[11px] text-accent-soft">{{ providerMsg }}</div>
            </div>

            <div
              v-if="IS_TAURI"
              class="p-3 rounded-xl bg-surface-2/60 border border-line-strong text-xs text-fg-2 space-y-1.5"
            >
              <div class="font-semibold text-fg">桌面版：来源在这里管理，翻译请用命令行</div>
              <div class="text-fg-3 leading-relaxed">
                上面的「翻译服务来源」直接写入 <code class="font-mono">translate_config.json</code>，
                与命令行读的是同一份文件，改完即可用。
                但<b class="text-fg-2">发起翻译</b>仍走 Python（单部自动翻译、批量翻译、试译按钮都用它的接口实现），
                所以这里只显示进度、不能直接开跑：
              </div>
              <code class="on-scrim block bg-scrim/60 rounded-lg p-2 font-mono text-[11px] text-fg-2 overflow-x-auto">
                python3 translate.py --list-profiles
              </code>
              <code class="on-scrim block bg-scrim/60 rounded-lg p-2 font-mono text-[11px] text-fg-2 overflow-x-auto">
                python3 translate.py --profile deepseek --limit 20 --dry-run
              </code>
              <div class="text-fg-3">
                试跑无误后去掉 <code class="font-mono">--limit</code> 与 <code class="font-mono">--dry-run</code> 即可全量翻译。
              </div>
            </div>

            <div
              v-else-if="translationStats"
              class="text-[11px] text-fg-4 font-mono bg-sunken p-2.5 rounded-xl border border-line"
            >
              当前使用: {{ translationStats.profile_label || translationStats.profile || translationStats.provider }}
              / {{ translationStats.model || '默认模型' }}
            </div>

            <div v-if="translateMsg" class="p-3 rounded-xl bg-success-fill/10 border border-success-fill/20 text-xs text-success-soft">
              {{ translateMsg }}
            </div>

            <!-- Actions -->
            <div v-if="!IS_TAURI" class="flex items-center gap-3 pt-2 flex-wrap">
              <button
                @click="handleRunTranslation(50)"
                :disabled="isTranslating || !translationStats?.configured"
                class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg font-medium text-xs border border-line-strong flex items-center gap-2 transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Languages class="w-3.5 h-3.5" />
                <span>试跑 50 条</span>
              </button>

              <button
                @click="handleRunTranslation(null)"
                :disabled="isTranslating || !translationStats?.configured || translationStats?.translation_pending === 0"
                class="px-5 py-2.5 rounded-xl bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs shadow-lg shadow-accent-fill/20 flex items-center gap-2 transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Loader2 v-if="isTranslating" class="w-3.5 h-3.5 animate-spin" />
                <Sparkles v-else class="w-3.5 h-3.5" />
                <span v-if="isTranslating">翻译进行中…</span>
                <span v-else-if="translationStats?.translation_pending === 0">没有待翻译的简介</span>
                <span v-else>
                  一键翻译全部待翻译简介
                  ({{ translationStats?.translation_pending.toLocaleString() }} 条)
                </span>
              </button>
            </div>
          </div>

          <!--
            Section 3b: Performer attribute glossary.

            Separate from the synopsis job above: this is a one-off translation of a
            closed vocabulary rather than a per-film queue, and its result is stored
            in a lookup table the client reads on startup.
          -->
          <div class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4">
            <div class="flex items-center gap-3">
              <Sparkles class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">演员属性术语表</div>
                <div class="text-xs text-fg-3">
                  身高、肤色、发色、纹身部位等属性取值来自一个很小的固定词表。整表翻译一次后客户端本地查表，
                  浏览演员不再产生任何 API 调用。
                </div>
              </div>
            </div>

            <div class="flex items-center gap-2 text-xs">
              <span class="text-fg-4">已收录术语</span>
              <span class="font-mono font-bold text-success">{{ glossaryCount() }}</span>
              <span class="text-fg-5">条（浏览器本地已加载）</span>
            </div>

            <div v-if="glossaryMsg" class="p-3 rounded-xl bg-success-fill/10 border border-success-fill/20 text-xs text-success-soft">
              {{ glossaryMsg }}
            </div>
            <div v-if="glossaryError" class="p-3 rounded-xl bg-danger-fill/10 border border-danger-fill/20 text-xs text-danger-soft">
              {{ glossaryError }}
            </div>

            <div v-if="!IS_TAURI" class="flex items-center gap-3 pt-1 flex-wrap">
              <button
                @click="handleRunGlossary(true)"
                :disabled="glossaryBusy || !translationStats?.configured"
                class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg font-medium text-xs border border-line-strong flex items-center gap-2 transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Loader2 v-if="glossaryBusy" class="w-3.5 h-3.5 animate-spin" />
                <Languages v-else class="w-3.5 h-3.5" />
                <span>试跑（不写入）</span>
              </button>

              <button
                @click="handleRunGlossary(false)"
                :disabled="glossaryBusy || !translationStats?.configured"
                class="px-5 py-2.5 rounded-xl bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs shadow-lg shadow-accent-fill/20 flex items-center gap-2 transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Loader2 v-if="glossaryBusy" class="w-3.5 h-3.5 animate-spin" />
                <Sparkles v-else class="w-3.5 h-3.5" />
                <span>{{ glossaryBusy ? '翻译中…' : '翻译术语表' }}</span>
              </button>
            </div>

            <div v-else class="text-xs text-fg-3 leading-relaxed p-3 rounded-xl bg-surface-2/60 border border-line-strong">
              桌面版直接读写本地数据库，请用命令行运行：
              <code class="font-mono text-fg-2">python3 translate.py --glossary</code>
            </div>
          </div>

          <!-- Section 4: Data Import & Export (Custom Backup & Migration) -->
          <div class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4">
            <div class="flex items-center gap-3">
              <Download class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">个人扩展数据备份与恢复</div>
                <div class="text-xs text-fg-3">导出或导入所有自定义标签、私密星级评分、观看状态、私密笔记与全部收藏</div>
              </div>
            </div>

            <div v-if="importStatusMsg" class="p-3 rounded-xl bg-success-fill/10 border border-success-fill/20 text-xs text-success-soft">
              {{ importStatusMsg }}
            </div>

            <div class="flex items-center gap-3 pt-2">
              <button
                @click="handleExportUserData"
                class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg font-medium text-xs border border-line-strong flex items-center gap-2 transition"
              >
                <Download class="w-3.5 h-3.5 text-accent" />
                <span>导出备份数据 (JSON)</span>
              </button>

              <button
                @click="triggerImportFileInput"
                class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg font-medium text-xs border border-line-strong flex items-center gap-2 transition"
              >
                <Upload class="w-3.5 h-3.5 text-accent" />
                <span>导入恢复数据 (JSON)</span>
              </button>
              <input ref="fileInputRef" type="file" accept=".json" class="hidden" @change="handleImportFile" />
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- Modals & Drawers -->
    <MovieDetailModal
      :movie="selectedMovie"
      :is-favorite="selectedMovie ? isFavorite('movie', selectedMovie.id) : false"
      :favorite-keys="favorites"
      :lang="descLang"
      :z-index="layerOf('movie')"
      :is-top="modalStack[modalStack.length - 1] === 'movie'"
      :auto-translate="translateMode === 'single'"
      @close="closeMovieDetail"
      @select-performer="openPerformerDetail"
      @filter-studio="filterByStudio"
      @filter-director="filterByDirector"
      @toggle-favorite="toggleFavorite"
      @toggle-entity-favorite="toggleFavoriteEntity"
      @user-data-changed="onUserDataChanged"
      @translated="onMovieTranslated"
    />

    <PerformerDetailModal
      :performer="selectedPerformer"
      :is-favorite="selectedPerformer ? isFavorite('performer', selectedPerformer.id) : false"
      :favorite-keys="favorites"
      :lang="descLang"
      :z-index="layerOf('performer')"
      :is-top="modalStack[modalStack.length - 1] === 'performer'"
      @close="closePerformerDetail"
      @select-movie="openMovieDetail"
      @select-movie-id="openMovieDetailById"
      @toggle-favorite="togglePerformerFavorite"
      @toggle-entity-favorite="toggleFavoriteEntity"
      @filter-studio="filterByStudio"
    />

    <StudioDetailModal
      :studio="selectedStudio"
      :works="studioWorks"
      :loading="studioWorksLoading"
      :lang="descLang"
      :z-index="layerOf('studio')"
      :is-top="modalStack[modalStack.length - 1] === 'studio'"
      :is-favorite="selectedStudio ? isFavorite('studio', selectedStudio.name) : false"
      :favorite-keys="favorites"
      @close="closeStudioDetail"
      @select-movie="openMovieDetail"
      @select-movie-id="openMovieDetailById"
      @toggle-favorite="toggleStudioFavorite"
      @toggle-entity-favorite="toggleFavoriteEntity"
    />

    <EpisodeDetailModal
      :episode="selectedEpisode"
      :list="episodeList"
      :lang="descLang"
      :z-index="layerOf('episode')"
      :is-top="modalStack[modalStack.length - 1] === 'episode'"
      :is-favorite="selectedEpisode ? isFavorite('episode', selectedEpisode.id) : false"
      @close="closeEpisodeDetail"
      @navigate="navigateEpisode"
      @select-movie-id="openMovieDetailById"
      @select-performer="openPerformerDetail"
      @filter-studio="filterByStudio"
      @toggle-favorite="toggleEpisodeFavorite"
    />

    <FilterDrawer
      :open="isFilterOpen"
      :tab="currentTab"
      :filters="filters"
      :studios="studios"
      :categories="categories"
      :performer-filters="performerFilters"
      :performer-facets="performerFacets"
      :episode-filters="episodeFilters"
      :episode-sort-by="episodeSortBy"
      @close="isFilterOpen = false"
      @update:filters="(f) => Object.assign(filters, f)"
      @update:performer-filters="(f) => Object.assign(performerFilters, f)"
      @toggle-performer-facet="togglePerformerFacet"
      @update:episode-filters="(f) => Object.assign(episodeFilters, f)"
      @update:episode-sort="(s) => episodeSortBy = s"
      @reset="Object.assign(filters, { query: '', studio: '', category: '', sortBy: 'year_desc' })"
      @reset-performers="resetPerformerFilters"
      @reset-episodes="resetEpisodeFilters"
    />

    <SyncModal
      :open="isSyncOpen"
      :stats="stats"
      @close="isSyncOpen = false"
      @sync-complete="loadStats(); fetchMovies();"
    />

    <!-- Above the whole modal stack: it is opened from inside those modals. -->
    <ImageLightbox />
  </div>
</template>
