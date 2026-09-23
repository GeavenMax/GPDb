<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import Navbar from './components/Navbar.vue';
import Sidebar from './components/Sidebar.vue';
import MovieCard from './components/MovieCard.vue';
import MovieDetailModal from './components/MovieDetailModal.vue';
import PerformerDetailModal from './components/PerformerDetailModal.vue';
import StudioDetailModal from './components/StudioDetailModal.vue';
import DirectorDetailModal from './components/DirectorDetailModal.vue';
import EpisodeCard from './components/EpisodeCard.vue';
import EpisodeDetailModal from './components/EpisodeDetailModal.vue';
import SeriesModal from './components/SeriesModal.vue';
import SeriesCollageCover from './components/SeriesCollageCover.vue';
import PermissionExplainModal from './components/PermissionExplainModal.vue';
import ImageLightbox from './components/ImageLightbox.vue';
import FilterDrawer from './components/FilterDrawer.vue';
import ActiveFilterBar from './components/ActiveFilterBar.vue';
import SyncModal from './components/SyncModal.vue';
import PaginationBar from './components/PaginationBar.vue';
import {
  api, createMovieFilters, createPerformerFilters, countActivePerformerFilters,
  createEpisodeFilters, countActiveEpisodeFilters, EPISODE_SORTS, IS_TAURI,
} from './api';
import { getImageUrl } from './utils/image';
import { openLightbox, viewableImageFrom, zoomsOnClick, lightboxImage } from './utils/lightbox';
import { initTheme, setTheme, themeChoice, themeOptions } from './utils/theme';
import AppIcon from './components/AppIcon.vue';
import { ICON_SCHEMES, currentIconScheme, setIconScheme, initAppIcon } from './utils/appIcon';
import { PREFS } from './utils/prefs';
import type {
  Movie,
  MovieSeriesResponse,
  Performer,
  FilterState,
  DatabaseStats,
  PerformerFilterState,
  PerformerFacets,
  TranslationStats,
  FavoriteType,
  FavoriteItem,
  FavoritesResponse,
  AppTab,
  StudioSummary,
  StudioSortBy,
  StudioWorks,
  DirectorSummary,
  DirectorSortBy,
  DirectorWorks,
  EpisodeSummary,
  EpisodeSortBy,
  EpisodeFilterState,
  DatabaseInfo,
} from './types';
import { FAVORITE_TYPES } from './types';
import { loadGlossary, glossaryCount, trMeasure } from './utils/glossary';
import { titlePrimary, titleSecondary, sceneFilm } from './utils/bilingual';
import {
  Film, Heart, HardDrive, Download, Upload, Trash2, Image as ImageIcon, RefreshCw, Loader2,
  Languages, User as UserIcon, Sparkles, Clapperboard, Building2, Layers, Palette, Check,
  Megaphone, FolderOpen, Search, Globe, Shield,
  Bookmark, CheckCircle2, ChevronDown, ChevronRight, ChevronsUpDown, Star, Info,
  SlidersHorizontal
} from '@lucide/vue';
import HomeView from './views/HomeView.vue';
import AnalyticsView from './views/AnalyticsView.vue';
import PluginsView from './views/PluginsView.vue';
import TrophiesView from './views/TrophiesView.vue';
import TrophyToast from './components/TrophyToast.vue';
import { pluginsConfig } from './services/pluginManager';
import { SUPPORTED_LANGUAGES, currentLocale, setLocale, t } from './i18n';
import { privacySettings, savePrivacySettings } from './services/privacy';
import {
  startFocusTracker, recordMovieView, recordPerformerView,
  recordEpisodeView, recordDirectorView, recordStudioView,
  clearSearchHistory, clearBrowseHistory, resetAllAnalytics,
  recordFavoriteToggle, recordRating
} from './services/analytics';

type SettingsSubTab = 'all' | 'appearance' | 'localization' | 'data' | 'privacy';
const settingsSubTab = ref<SettingsSubTab>('all');

/** Labels for the five favorites sections and the type pickers. */
const FAVORITE_LABELS: Record<FavoriteType, string> = {
  movie: '影片',
  performer: '演员',
  studio: '片商',
  director: '导演',
  episode: '片段',
  series: '系列专题',
};

// State
const currentTab = ref<AppTab>('home');
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
 * The director library. Rows carry a real id, unlike studios, but the grid is keyed
 * by name anyway — that is what `user_favorites` stores for a director, so the
 * favorites page and this grid address the same person the same way.
 */
const directorRows = ref<DirectorSummary[]>([]);
const totalDirectorRows = ref(0);
const directorQuery = ref('');
const directorSortBy = ref<DirectorSortBy>('works_desc');

/** Orderings offered on the director tab. Two is enough: count, or name. */
const DIRECTOR_SORTS = [
  { id: 'works_desc', label: '按作品数' },
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
 * The episode filters currently on, as chips — the same idea as `activeFacetChips`

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
  return { movie: new Set(), performer: new Set(), studio: new Set(), director: new Set(), episode: new Set(), series: new Set() };
}

const selectedMovie = ref<Movie | null>(null);
const selectedPerformer = ref<Performer | null>(null);

/** The studio being viewed, and its works. Fetched here so the modal stays presentational. */
const selectedStudio = ref<{ name: string; works_count?: number; episodes_count?: number } | null>(null);
const studioWorks = ref<StudioWorks | null>(null);
const studioWorksLoading = ref(false);

/**
 * The director being viewed, and their films. Fetched here so the modal stays
 * presentational, exactly like the studio one. `id` is only present when the modal
 * was opened from the grid; the favorites page has nothing but the name.
 */
const selectedDirector = ref<{ id?: number; name: string } | null>(null);
const directorWorks = ref<DirectorWorks | null>(null);
const directorWorksLoading = ref(false);

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
type ModalKind = 'movie' | 'performer' | 'studio' | 'director' | 'episode';

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

const hasTranslationAvailable = computed(() => {
  if (!pluginsConfig.value.translationEnabled && (!stats.value?.translation_done || stats.value.translation_done <= 0)) {
    return false;
  }
  return Boolean((stats.value?.translation_done && stats.value.translation_done > 0) || pluginsConfig.value.translationEnabled);
});

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

// Machine translation state (auto-translate on film detail modal)
const translationStats = ref<TranslationStats | null>(null);
const translateMode = ref<'single' | 'batch'>(
  localStorage.getItem(PREFS.translateMode) === 'batch' ? 'batch' : 'single'
);

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
const directorPage = ref(1);
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
  else if (currentTab.value === 'directors') fetchDirectors(true, n);
  else if (currentTab.value === 'episodes') fetchEpisodes(true, n);
  scrollContainerRef.value?.scrollTo({ top: 0, behavior: 'smooth' });
}

/** Refetch the visible tab at page 1 — for filter, page-size and mode changes. */
function reloadCurrentTab() {
  if (currentTab.value === 'movies') fetchMovies(true);
  else if (currentTab.value === 'performers') fetchPerformers(true);
  else if (currentTab.value === 'studios') fetchStudios(true);
  else if (currentTab.value === 'directors') fetchDirectors(true);
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

/**
 * The last list-load failure, or '' — rendered as a banner above the main stage.
 *
 * Each list fetcher used to end in a bare `finally`, so a rejected command left the
 * list at whatever it was and the window showed an empty library with nothing said
 * about why. That is how a bundled .app opening the wrong database read as "my data is
 * gone": nothing on either side of the boundary reported anything. Stays until a load
 * succeeds — a database that cannot be opened does not fix itself, so a message that
 * faded after a few seconds would be the same silence again.
 */
const loadError = ref('');

function reportLoadError(err: unknown) {
  loadError.value = err instanceof Error ? err.message : String(err);
}

// --- Database Path Management & Smart Detection -----------------------------
const dbInfo = ref<DatabaseInfo | null>(null);
const customDbInput = ref('');
const dbScanning = ref(false);
const dbSwitching = ref(false);
const dbCandidates = ref<string[]>([]);
const dbMessage = ref<{ ok: boolean; text: string } | null>(null);
const showPermissionModal = ref(false);

async function loadDatabaseInfo() {
  try {
    const info = await api.getDatabaseInfo();
    dbInfo.value = info;
    if (info.custom_path) {
      customDbInput.value = info.custom_path;
    }
    if (info.candidates && info.candidates.length > 0) {
      dbCandidates.value = info.candidates;
    }
    // If database cannot be located or is invalid, explain permissions and guide the user gently
    if (!info.exists || !info.valid) {
      showPermissionModal.value = true;
    }
  } catch (e) {
    console.error('Failed to load database info', e);
  }
}

async function applyCustomDbPath(targetPath?: string) {
  const p = (targetPath !== undefined ? targetPath : customDbInput.value).trim();
  dbSwitching.value = true;
  dbMessage.value = null;
  try {
    const updated = await api.setCustomDatabasePath(p);
    dbInfo.value = updated;
    customDbInput.value = updated.custom_path || '';
    if (updated.candidates) dbCandidates.value = updated.candidates;
    loadError.value = '';
    dbMessage.value = { ok: true, text: `已成功连接数据库：${updated.path || '默认路径'}` };
    showPermissionModal.value = false;
    await loadStats();
    await reloadCurrentTab();
  } catch (e: any) {
    dbMessage.value = { ok: false, text: e?.message || String(e) };
  } finally {
    dbSwitching.value = false;
  }
}

async function resetToAutoDbPath() {
  await applyCustomDbPath('');
}

async function handlePickDbFile() {
  try {
    const picked = await api.pickDatabaseFile();
    if (picked) {
      customDbInput.value = picked;
      await applyCustomDbPath(picked);
      showPermissionModal.value = false;
    }
  } catch (e: any) {
    dbMessage.value = { ok: false, text: e?.message || String(e) };
  }
}

async function handleCreateNewDatabase() {
  dbSwitching.value = true;
  dbMessage.value = null;
  try {
    const updated = await api.createNewDatabase();
    dbInfo.value = updated;
    customDbInput.value = updated.custom_path || '';
    if (updated.candidates) dbCandidates.value = updated.candidates;
    loadError.value = '';
    showPermissionModal.value = false;
    dbMessage.value = { ok: true, text: `全新影库已初始化创建：${updated.path || '默认位置'}` };
    await loadStats();
    await reloadCurrentTab();
    // Prompt scraping by opening sync modal automatically
    isSyncOpen.value = true;
  } catch (e: any) {
    dbMessage.value = { ok: false, text: e?.message || String(e) };
  } finally {
    dbSwitching.value = false;
  }
}

async function handleScanDatabases() {
  dbScanning.value = true;
  dbMessage.value = null;
  try {
    const cands = await api.scanDatabases();
    dbCandidates.value = cands;
    if (cands.length === 0) {
      dbMessage.value = { ok: false, text: '未能自动检测到 gevi.db，请手动浏览选择或输入路径。' };
    } else {
      dbMessage.value = { ok: true, text: `扫描完成，发现 ${cands.length} 个候选数据库。` };
      if (!dbInfo.value?.exists && cands[0]) {
        await applyCustomDbPath(cands[0]);
        showPermissionModal.value = false;
      }
    }
  } catch (e: any) {
    dbMessage.value = { ok: false, text: e?.message || String(e) };
  } finally {
    dbScanning.value = false;
  }
}

async function handleSmartAutoRescue() {
  dbScanning.value = true;
  try {
    const cands = await api.scanDatabases();
    dbCandidates.value = cands;
    if (cands.length > 0) {
      await applyCustomDbPath(cands[0]);
    } else {
      currentTab.value = 'settings';
      dbMessage.value = { ok: false, text: '未能在常规目录检测到数据库，请通过「浏览…」手动选择。' };
    }
  } catch (e: any) {
    reportLoadError(e);
  } finally {
    dbScanning.value = false;
  }
}

// Load data
async function loadStats() {
  loadDatabaseInfo();
  stats.value = await api.getStats();
  studios.value = await api.getStudios();
  categories.value = await api.getCategories();
  loadTranslationStats();
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

function clearMovieField(field: keyof FilterState) {
  if (field === 'yearMin' || field === 'yearMax') {
    filters[field] = null;
  } else if (field === 'query' || field === 'studio' || field === 'director' || field === 'category') {
    filters[field] = '';
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

/** Same for the director tab. */
function resetDirectorFilters() {
  directorQuery.value = '';
  directorSortBy.value = 'works_desc';
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
  if (IS_TAURI) {
    try {
      const dest = await api.exportUserDataFile();
      if (dest) {
        importStatusMsg.value = `个人标记与片单备份成功导出至：${dest}`;
        setTimeout(() => { importStatusMsg.value = ''; }, 6000);
      }
      return;
    } catch (err: any) {
      console.warn('Native exportUserDataFile failed, falling back to download:', err);
    }
  }
  const data = await api.exportUserData();
  if (!data) {
    alert('导出标记备份失败：未能获取到用户数据');
    return;
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `gpdb_user_backup_${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

const dbBackupStatusMsg = ref('');
const isDbExporting = ref(false);

async function handleExportDatabase() {
  if (isDbExporting.value) return;
  isDbExporting.value = true;
  dbBackupStatusMsg.value = '正在安全打包并导出数据库文件...';
  try {
    const dest = await api.exportDatabaseFile();
    if (dest) {
      dbBackupStatusMsg.value = `数据库完整备份成功导出至：${dest}`;
      setTimeout(() => { dbBackupStatusMsg.value = ''; }, 6000);
    } else {
      dbBackupStatusMsg.value = '';
    }
  } catch (err: any) {
    dbBackupStatusMsg.value = `导出数据库失败: ${err?.message || err}`;
  } finally {
    isDbExporting.value = false;
  }
}

async function handleImportDatabase() {
  try {
    const picked = await api.pickDatabaseFile();
    if (picked) {
      if (confirm(`确认切换至所选数据库文件？\n${picked}\n\n切换后应用将重新加载数据库内容。`)) {
        await api.setCustomDatabasePath(picked);
        await loadDatabaseInfo();
        await loadStats();
        await fetchMovies();
        alert('数据库已成功切换并载入！');
      }
    }
  } catch (err: any) {
    alert('导入并切换数据库失败: ' + (err?.message || err));
  }
}

function downloadIconFile(schemeId: string, format: 'svg' | 'png' = 'png') {
  const link = document.createElement('a');
  link.href = `/src/assets/icons/${schemeId}.${format}`;
  link.download = `gpdb-icon-${schemeId}.${format}`;
  link.click();
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
  recordRating();
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
    loadError.value = '';
  } catch (err) {
    reportLoadError(err);
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
    loadError.value = '';
  } catch (err) {
    reportLoadError(err);
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
    loadError.value = '';
  } catch (err) {
    reportLoadError(err);
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

async function fetchDirectors(replace = true, targetPage = 1) {
  directorPage.value = targetPage;
  if (replace) isLoading.value = true;
  else isLoadingMore.value = true;

  try {
    const res = await api.getDirectorLibrary(
      directorQuery.value, directorSortBy.value, directorPage.value, pageSize.value
    );
    if (replace) {
      directorRows.value = res.items;
    } else {
      // Deduped by id, not by name as studios are: 20 pairs of directors differ only
      // by case (Chi Chi LaRue / Chi Chi Larue), and a name-keyed filter would drop
      // the second of a pair from the loaded window.
      const existing = new Set(directorRows.value.map(d => d.id));
      directorRows.value.push(...res.items.filter(d => !existing.has(d.id)));
    }
    totalDirectorRows.value = res.total;
    loadError.value = '';
  } catch (err) {
    reportLoadError(err);
  } finally {
    isLoading.value = false;
    isLoadingMore.value = false;
  }
}

async function loadMoreDirectors() {
  if (isLoading.value || isLoadingMore.value) return;
  if (directorRows.value.length >= totalDirectorRows.value) return;
  await fetchDirectors(false, directorPage.value + 1);
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
    loadError.value = '';
  } catch (err) {
    reportLoadError(err);
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
  } else if (currentTab.value === 'directors') {
    if (!isLoading.value && !isLoadingMore.value && directorRows.value.length < totalDirectorRows.value) loadMoreDirectors();
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
    } else if (currentTab.value === 'directors') {
      if (isLoading.value || isLoadingMore.value || directorRows.value.length >= totalDirectorRows.value) return;
      await loadMoreDirectors();
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
    if (currentTab.value === 'directors') return directorQuery.value;
    if (currentTab.value === 'episodes') return episodeQuery.value;
    return filters.query;
  },
  set: (val: string) => {
    if (currentTab.value === 'performers') performerFilters.query = val;
    else if (currentTab.value === 'studios') studioQuery.value = val;
    else if (currentTab.value === 'directors') directorQuery.value = val;
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

// Same for directors: a search box and a sort, no drawer.
watch([directorQuery, directorSortBy], () => {
  if (currentTab.value === 'directors') reloadCurrentTab();
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
  } else if (newTab === 'directors') {
    if (directorRows.value.length === 0) reloadCurrentTab();
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

/**
 * Favoriting a director stores the *name*, the way studios do — and, as with studios,
 * that key is case-sensitive: hearting `Chi Chi LaRue` leaves the separate `Chi Chi
 * Larue` row un-hearted even though one search shows both. Merging the two would mean
 * picking a canonical spelling for people the site itself spells two ways, so the two
 * rows stay two rows.
 */
function toggleDirectorFavorite(name: string) {
  void toggleFavoriteEntity('director', name);
}

/** Episodes key on their row id, like films. */
function toggleEpisodeFavorite(ep: EpisodeSummary) {
  void toggleFavoriteEntity('episode', String(ep.id));
}

async function toggleFavoriteEntity(type: FavoriteType, key: string) {
  const set = favorites.value[type];
  const wasFavorite = set.has(key);

  // Optimistic flip so the heart responds immediately; the round trip is a DB write.
  if (wasFavorite) {
    set.delete(key);
  } else {
    set.add(key);
    recordFavoriteToggle();
  }

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
const favSeries = computed(() => favoriteItems.value?.series || []);
const favWishlist = computed(() => favoriteItems.value?.wishlist || []);
const favWatched = computed(() => favoriteItems.value?.watched || []);

/** Series Modal popup state for opening series from cards and favorites */
const seriesModalData = ref<MovieSeriesResponse | null>(null);
const showAppSeriesModal = ref(false);

async function openSeriesModalByRoot(rootTitle: string, studioName?: string | null) {
  const data = await api.getMovieSeriesByRoot(rootTitle, studioName);
  if (data) {
    seriesModalData.value = data;
    showAppSeriesModal.value = true;
  }
}

/** Total across all sections, for the page header. */
const favoriteTotal = computed(() => {
  const counts = favoriteItems.value?.counts;
  if (!counts) return 0;
  return (
    FAVORITE_TYPES.reduce((sum, t) => sum + (counts[t] || 0), 0) +
    (counts.wishlist || 0) +
    (counts.watched || 0)
  );
});

type FavoriteSubTab = 'all' | 'wishlist' | 'watched' | 'series' | 'movie' | 'performer' | 'studio' | 'director' | 'episode';
const favSubTab = ref<FavoriteSubTab>('all');

const FAV_COLLAPSED_KEY = 'gpdb_fav_collapsed_sections';
function loadFavCollapsed(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(FAV_COLLAPSED_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}
const favCollapsed = reactive<Record<string, boolean>>(loadFavCollapsed());

const FAV_EP_COLS_KEY = 'gpdb_fav_ep_cols';
const favEpisodeCols = ref<number>(Number(localStorage.getItem(FAV_EP_COLS_KEY)) || 2);
function setFavEpisodeCols(cols: number) {
  favEpisodeCols.value = cols;
  try {
    localStorage.setItem(FAV_EP_COLS_KEY, String(cols));
  } catch {}
}

function toggleFavSection(sectionKey: string) {
  favCollapsed[sectionKey] = !favCollapsed[sectionKey];
  try {
    localStorage.setItem(FAV_COLLAPSED_KEY, JSON.stringify(favCollapsed));
  } catch {}
}

const allSectionsCollapsed = computed(() => {
  const activeSectionKeys = [
    ...(favWishlist.value.length ? ['wishlist'] : []),
    ...(favWatched.value.length ? ['watched'] : []),
    ...(favSeries.value.length ? ['series'] : []),
    ...(favMovies.value.length ? ['movie'] : []),
    ...(favPerformers.value.length ? ['performer'] : []),
    ...(favEpisodes.value.length ? ['episode'] : []),
    ...(favStudios.value.length ? ['studio'] : []),
    ...(favDirectors.value.length ? ['director'] : []),
  ];
  if (activeSectionKeys.length === 0) return false;
  return activeSectionKeys.every(k => favCollapsed[k]);
});

function toggleCollapseAllFavs() {
  const target = !allSectionsCollapsed.value;
  const activeSectionKeys = ['wishlist', 'watched', 'series', 'movie', 'performer', 'episode', 'studio', 'director'];
  for (const k of activeSectionKeys) {
    favCollapsed[k] = target;
  }
  try {
    localStorage.setItem(FAV_COLLAPSED_KEY, JSON.stringify(favCollapsed));
  } catch {}
}

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
  if (selectedDirector.value) closeDirectorDetail();
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
  else if (tab === 'directors') resetDirectorFilters();
  else if (tab === 'episodes') resetEpisodeFilters();
  else return;   // favorites / settings have nothing to clear

  scrollContainerRef.value?.scrollTo({ top: 0, behavior: 'smooth' });
}

async function openMovieDetail(m: Movie) {
  pushModal('movie');
  recordMovieView(m.id, m.title);
  const detail = await api.getMovieDetail(m.id);
  selectedMovie.value = detail || m;
}

async function openMovieDetailById(id: number) {
  pushModal('movie');
  const detail = await api.getMovieDetail(id);
  if (detail) {
    selectedMovie.value = detail;
    recordMovieView(detail.id, detail.title);
  }
}

async function openEpisodeDetailById(id: number) {
  const detail = await api.getEpisodeDetail(id);
  if (detail) {
    openEpisodeDetail(detail, [detail]);
  }
}

async function handleFavEpisodeClick(f: FavoriteItem) {
  if (f.movie_id) {
    await openMovieDetailById(f.movie_id);
  } else {
    await openEpisodeDetailById(Number(f.key));
  }
}

async function openPerformerDetail(id: number) {
  pushModal('performer');
  const detail = await api.getPerformerDetail(id);
  selectedPerformer.value = detail || { id, name: `Performer #${id}` };
  recordPerformerView(id, selectedPerformer.value.name);
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
  recordStudioView(studio.name);
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
 * Open a director's page.
 *
 * Same shape as `openStudioDetail`: the grid has an id but the favorites page has
 * only a name, and every path fetches the films from the name anyway, so the id is
 * carried purely for the display.
 */
async function openDirectorDetail(director: { id?: number; name: string }) {
  pushModal('director');
  recordDirectorView(director.name);
  selectedDirector.value = director;
  directorWorks.value = null;
  directorWorksLoading.value = true;
  try {
    const works = await api.getDirectorWorks(director.name);
    // A slower fetch for director A must not land on top of director B's page.
    if (selectedDirector.value?.name === director.name) directorWorks.value = works;
  } finally {
    directorWorksLoading.value = false;
  }
}

function closeDirectorDetail() {
  selectedDirector.value = null;
  directorWorks.value = null;
  popModal('director');
}

/**
 * Jump to the 片商库, filtered to one studio.
 *
 * Distinct from `filterByStudio`, which drops you into the *movie* grid: this is the
 * studio-library analogue, used by the chip row on a director's page.
 *
 * The tab is set before the query, and that order matters: the
 * `[studioQuery, studioSortBy]` watcher only refetches while the studio tab is
 * current, so writing the query first would fetch nothing until the tab changed.
 */
function openStudioLibrary(studioName: string) {
  closeAllModals();
  currentTab.value = 'studios';
  studioQuery.value = studioName;
  fetchStudios(true);
}

/**
 * Open a scene.
 *
 * `rows` is the grid the card was clicked in, so ‹ / › can walk the loaded pages
 * without a request — the modal is a viewer, not a second browser.
 */
async function openEpisodeDetail(ep: EpisodeSummary, rows: EpisodeSummary[]) {
  pushModal('episode');
  recordEpisodeView(ep.id, ep.title || `Episode #${ep.id}`);
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
  startFocusTracker();
  initAppIcon();
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
            : currentTab === 'studios' || currentTab === 'directors'
              ? false
              : Boolean(filters.studio || filters.director || filters.category || filters.sortBy !== 'year_desc')
      "
      @toggle-filter="isFilterOpen = !isFilterOpen"
      @toggle-sync="isSyncOpen = true"
      @change-view="(mode) => viewMode = mode"
    />

    <!--
      Sits above the stage rather than inside a tab, because a database that cannot be
      opened breaks every tab and the movies one is not necessarily the one on screen.
      Not dismissible: the condition it reports does not go away on its own.
    -->
    <div
      v-if="loadError"
      class="shrink-0 px-6 py-3 bg-danger-fill/10 border-b border-danger-fill/20 text-xs text-danger-soft flex flex-wrap items-center justify-between gap-3"
    >
      <div class="whitespace-pre-line flex-1 min-w-[280px]">
        {{ loadError }}
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <button
          @click="handleSmartAutoRescue"
          :disabled="dbScanning || dbSwitching"
          class="px-3 py-1.5 rounded-lg bg-accent-fill text-on-fill font-bold text-xs hover:bg-accent transition flex items-center gap-1.5 disabled:opacity-50"
        >
          <Search class="w-3.5 h-3.5" />
          <span>{{ dbScanning ? '智能识别中…' : '智能识别数据库' }}</span>
        </button>
        <button
          @click="showPermissionModal = true"
          class="px-3 py-1.5 rounded-lg bg-surface border border-line-strong hover:bg-surface-2 text-fg-2 text-xs font-medium transition flex items-center gap-1.5 cursor-pointer"
        >
          <Shield class="w-3.5 h-3.5 text-indigo-400" />
          <span>权限与存储说明</span>
        </button>
        <button
          @click="handlePickDbFile"
          :disabled="dbSwitching"
          class="px-3 py-1.5 rounded-lg bg-surface border border-line-strong hover:bg-surface-2 text-fg-2 text-xs font-medium transition flex items-center gap-1.5"
        >
          <FolderOpen class="w-3.5 h-3.5" />
          <span>浏览选择文件</span>
        </button>
        <button
          @click="currentTab = 'settings'"
          class="px-3 py-1.5 rounded-lg bg-surface border border-line-strong hover:bg-surface-2 text-fg-2 text-xs font-medium transition"
        >
          前往设置
        </button>
      </div>
    </div>

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
        <!-- 0. Home Tab -->
        <HomeView
          v-if="currentTab === 'home'"
          @open-movie="openMovieDetailById"
          @open-episode="openEpisodeDetailById"
          @open-performer="openPerformerDetail"
          @open-series="(title, studio) => openSeriesModalByRoot(title, studio)"
          @change-tab="currentTab = $event"
        />

        <!-- 1. Movies Tab -->
        <div v-else-if="currentTab === 'movies'" class="space-y-6">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <h1 class="text-xl font-bold text-fg tracking-tight">探索全量影片</h1>
              <span class="text-xs text-fg-4 font-mono">({{ movies.length }} / {{ totalMovies.toLocaleString() }})</span>
            </div>

            <div class="flex items-center gap-3">
              <!-- Synopsis language toggle (issue #4) -->
              <div
                v-if="hasTranslationAvailable"
                class="flex items-center gap-1.5 bg-surface border border-line rounded-xl p-0.5 text-xs animate-fade-in"
              >
                <Languages class="w-3 h-3 text-fg-4 ml-1.5" />
                <button
                  v-for="l in [{ id: 'zh', label: '中文' }, { id: 'en', label: '原文' }]"
                  :key="l.id"
                  @click="setDescLang(l.id as 'zh' | 'en')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition cursor-pointer',
                    descLang === l.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="l.id === 'zh' ? '优先显示中文简介（未翻译的影片自动回落原文）' : '始终显示英文原文'"
                >
                  {{ l.label }}
                </button>

                <!-- Info icon with tooltip -->
                <div
                  class="flex items-center pr-1.5 text-fg-5 hover:text-accent cursor-help transition"
                  title="此处的语言切换仅针对影片简介与分集信息的译文，界面菜单语言请在设置中更改"
                >
                  <Info class="w-3.5 h-3.5" />
                </div>
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

          <!-- Prominent Active Filter Banner -->
          <ActiveFilterBar
            tab="movies"
            :movie-filters="filters"
            @clear-movie-field="clearMovieField"
            @clear-movie-years="() => { filters.yearMin = null; filters.yearMax = null; }"
            @reset-movies="resetMovieFilters"
          />

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

          <!-- Prominent Active Filter Banner -->
          <ActiveFilterBar
            tab="performers"
            :performer-filters="performerFilters"
            @clear-performer-facet="(key, val) => togglePerformerFacet(key, val)"
            @clear-performer-field="(f) => { if (f === 'hasImage') performerFilters.hasImage = false; else if (f === 'minMovies') performerFilters.minMovies = null; }"
            @reset-performers="resetPerformerFilters"
          />

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

          <!-- Prominent Active Filter Banner -->
          <ActiveFilterBar
            tab="studios"
            :studio-query="studioQuery"
            @clear-studio-query="studioQuery = ''"
          />

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

        <!-- 3b. Directors Tab — every director the library has parsed -->
        <div v-else-if="currentTab === 'directors'" class="space-y-6">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <h1 class="text-xl font-bold text-fg tracking-tight">导演库</h1>
              <span class="text-xs text-fg-4 font-mono">({{ directorRows.length }} / {{ totalDirectorRows.toLocaleString() }} 位)</span>
            </div>

            <div class="flex items-center gap-3">
              <!-- Sort: no filter drawer for directors either -->
              <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <button
                  v-for="s in DIRECTOR_SORTS"
                  :key="s.id"
                  @click="directorSortBy = s.id"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    directorSortBy === s.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                >
                  {{ s.label }}
                </button>
              </div>

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

          <!-- Prominent Active Filter Banner -->
          <ActiveFilterBar
            tab="directors"
            :director-query="directorQuery"
            @clear-director-query="directorQuery = ''"
          />

          <!-- No portrait exists for a director, so the tile is the first letter,
               as on the studio cards. -->
          <div
            v-if="directorRows.length > 0"
            class="grid gap-4 transition-all duration-200"
            :style="{ gridTemplateColumns: `repeat(${activeCols}, minmax(0, 1fr))` }"
          >
            <div
              v-for="d in directorRows"
              :key="d.id"
              @click="openDirectorDetail(d)"
              class="p-4 rounded-2xl bg-surface/60 border border-line hover:border-accent-fill/40 hover:bg-surface transition-all cursor-pointer flex flex-col items-center text-center group"
            >
              <div class="w-16 h-16 rounded-2xl overflow-hidden shrink-0 shadow ring-1 ring-line-strong/60 group-hover:ring-accent-fill/50 transition">
                <div class="w-full h-full bg-gradient-to-tr from-accent-deep to-accent-2 flex items-center justify-center text-2xl font-black text-on-fill/70">
                  {{ d.name.charAt(0).toUpperCase() }}
                </div>
              </div>
              <h3 class="text-xs font-semibold text-fg-2 mt-3 group-hover:text-accent transition truncate w-full">
                {{ d.name }}
              </h3>
              <div class="text-[10px] text-fg-4 mt-1">{{ d.works_count }} 部作品</div>
              <div v-if="d.studios_count" class="text-[10px] text-fg-5 mt-0.5">
                {{ d.studios_count }} 家片商
              </div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else-if="!isLoading" class="text-center py-24 space-y-3">
            <Megaphone class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">没有符合条件的导演</div>
            <div class="text-xs text-fg-5">试试更换关键词，或清空搜索框</div>
          </div>

          <!-- Infinite-scroll footer -->
          <div v-if="listMode === 'scroll' && directorRows.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>滑动加载更多导演中...</span>
            </div>
            <div v-else-if="directorRows.length >= totalDirectorRows && totalDirectorRows > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>已加载全部 {{ totalDirectorRows.toLocaleString() }} 位导演</span>
              <span class="w-12 h-px bg-surface-2"></span>
            </div>
          </div>

          <PaginationBar
            v-if="listMode === 'paged' && directorRows.length > 0"
            :page="directorPage"
            :page-size="pageSize"
            :total="totalDirectorRows"
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

          <!-- Prominent Active Filter Banner -->
          <ActiveFilterBar
            tab="episodes"
            :episode-filters="episodeFilters"
            @clear-episode-field="(f) => { if (f === 'studio') episodeFilters.studio = ''; else if (f === 'hasZh') episodeFilters.hasZh = false; else if (f === 'hasPerformers') episodeFilters.hasPerformers = false; }"
            @reset-episodes="resetEpisodeFilters"
          />

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

        <!-- 5. Favorites Tab — categorized, collapsible accordion & sub-tab navigation -->
        <div v-else-if="currentTab === 'favorites'" class="space-y-6">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <h1 class="text-xl font-bold text-fg tracking-tight">我的收藏与片单</h1>
              <span class="text-xs text-fg-4 font-mono">({{ favoriteTotal }} 项)</span>
            </div>

            <div class="flex items-center gap-2.5">
              <!-- Master Toggle Collapse All (only in overview mode) -->
              <button
                v-if="favSubTab === 'all' && favoriteTotal > 0"
                @click="toggleCollapseAllFavs"
                class="px-2.5 py-1 rounded-xl bg-surface border border-line hover:border-line-strong text-fg-3 hover:text-fg text-xs font-medium flex items-center gap-1.5 transition cursor-pointer"
                :title="allSectionsCollapsed ? '展开全部板块' : '折叠全部板块'"
              >
                <ChevronsUpDown class="w-3.5 h-3.5 text-accent" />
                <span>{{ allSectionsCollapsed ? '全部展开' : '全部折叠' }}</span>
              </button>

              <!-- Grid columns adjuster -->
              <div
                v-if="favMovies.length > 0 || favWishlist.length > 0 || favWatched.length > 0 || favEpisodes.length > 0"
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

          <!-- Subcategory Filter Pills -->
          <div class="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
            <button
              v-for="sub in [
                { id: 'all', label: '全部总览', count: favoriteTotal },
                { id: 'wishlist', label: '想看', count: favWishlist.length },
                { id: 'watched', label: '已看', count: favWatched.length },
                { id: 'series', label: '系列专题', count: favSeries.length },
                { id: 'movie', label: '喜爱影片', count: favMovies.length },
                { id: 'performer', label: '演员', count: favPerformers.length },
                { id: 'studio', label: '片商', count: favStudios.length },
                { id: 'director', label: '导演', count: favDirectors.length },
                { id: 'episode', label: '分集', count: favEpisodes.length },
              ]"
              :key="sub.id"
              @click="favSubTab = sub.id as any"
              class="px-3 py-1.5 rounded-xl border font-medium transition flex items-center gap-1.5 whitespace-nowrap cursor-pointer select-none"
              :class="favSubTab === sub.id
                ? 'bg-accent-fill text-on-fill border-accent-fill font-bold shadow-sm'
                : 'bg-surface border-line hover:border-line-strong text-fg-3 hover:text-fg-2'"
            >
              <span>{{ sub.label }}</span>
              <span
                class="text-[10px] px-1.5 py-0.2 rounded-full font-mono font-bold"
                :class="favSubTab === sub.id ? 'bg-on-fill/20 text-on-fill' : 'bg-surface-2 text-fg-4'"
              >
                {{ sub.count }}
              </span>
            </button>
          </div>

          <div v-if="favoritesLoading && favoriteTotal === 0" class="text-center py-24">
            <Loader2 class="w-8 h-8 text-accent-fill animate-spin mx-auto" />
          </div>

          <!-- Favorites Body -->
          <template v-else-if="favoriteTotal > 0">
            <!-- 1. Wishlist Section (想看) -->
            <section
              v-if="(favSubTab === 'all' || favSubTab === 'wishlist') && favWishlist.length > 0"
              class="space-y-3 bg-surface/40 p-4 rounded-2xl border border-line/80"
            >
              <div class="flex items-center justify-between pb-2 border-b border-line select-none">
                <button
                  @click="toggleFavSection('wishlist')"
                  class="flex items-center gap-2 text-left group cursor-pointer"
                >
                  <component
                    :is="favCollapsed.wishlist ? ChevronRight : ChevronDown"
                    class="w-4 h-4 text-fg-4 group-hover:text-accent transition"
                  />
                  <Bookmark class="w-4 h-4 text-amber-500" />
                  <h2 class="text-sm font-bold text-fg group-hover:text-accent transition">想看片单</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favWishlist.length }}</span>
                </button>
                <button
                  v-if="favSubTab === 'all'"
                  @click="favSubTab = 'wishlist'"
                  class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                >
                  <span>仅看此类</span>
                  <span>→</span>
                </button>
              </div>
              <div
                v-show="!favCollapsed.wishlist || favSubTab === 'wishlist'"
                class="grid transition-all duration-200 pt-1"
                :class="viewMode === 'grid' ? 'gap-4 sm:gap-6' : 'gap-3'"
                :style="{ gridTemplateColumns: `repeat(${activeCols}, minmax(0, 1fr))` }"
              >
                <MovieCard
                  v-for="f in favWishlist"
                  :key="`wishlist-${f.key}`"
                  :movie="asMovie(f)"
                  :translated="Boolean(f.has_zh)"
                  :is-favorite="favorites.movie.has(f.key)"
                  :view="viewMode"
                  :lang="descLang"
                  @select="openMovieDetail(asMovie(f))"
                  @toggle-favorite="toggleFavoriteEntity('movie', f.key)"
                />
              </div>
            </section>

            <!-- 2. Watched Section (已看) -->
            <section
              v-if="(favSubTab === 'all' || favSubTab === 'watched') && favWatched.length > 0"
              class="space-y-3 bg-surface/40 p-4 rounded-2xl border border-line/80"
            >
              <div class="flex items-center justify-between pb-2 border-b border-line select-none">
                <button
                  @click="toggleFavSection('watched')"
                  class="flex items-center gap-2 text-left group cursor-pointer"
                >
                  <component
                    :is="favCollapsed.watched ? ChevronRight : ChevronDown"
                    class="w-4 h-4 text-fg-4 group-hover:text-accent transition"
                  />
                  <CheckCircle2 class="w-4 h-4 text-emerald-500" />
                  <h2 class="text-sm font-bold text-fg group-hover:text-accent transition">已看记录</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favWatched.length }}</span>
                </button>
                <button
                  v-if="favSubTab === 'all'"
                  @click="favSubTab = 'watched'"
                  class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                >
                  <span>仅看此类</span>
                  <span>→</span>
                </button>
              </div>
              <div
                v-show="!favCollapsed.watched || favSubTab === 'watched'"
                class="grid transition-all duration-200 pt-1"
                :class="viewMode === 'grid' ? 'gap-4 sm:gap-6' : 'gap-3'"
                :style="{ gridTemplateColumns: `repeat(${activeCols}, minmax(0, 1fr))` }"
              >
                <div v-for="f in favWatched" :key="`watched-${f.key}`" class="relative group">
                  <MovieCard
                    :movie="asMovie(f)"
                    :translated="Boolean(f.has_zh)"
                    :is-favorite="favorites.movie.has(f.key)"
                    :view="viewMode"
                    :lang="descLang"
                    @select="openMovieDetail(asMovie(f))"
                    @toggle-favorite="toggleFavoriteEntity('movie', f.key)"
                  />
                  <!-- Rating badge overlay if rated -->
                  <div
                    v-if="f.rating != null && f.rating > 0"
                    class="on-scrim absolute bottom-2.5 left-2.5 px-2 py-0.5 rounded-lg bg-scrim/85 backdrop-blur-md border border-amber-500/40 text-[11px] text-amber-400 font-bold flex items-center gap-1 pointer-events-none shadow-md z-10"
                  >
                    <Star class="w-3 h-3 fill-amber-400 text-amber-400" />
                    <span>{{ f.rating }} 星</span>
                  </div>
                </div>
              </div>
            </section>

            <!-- 2.5 Series Section (系列专题) -->
            <section
              v-if="(favSubTab === 'all' || favSubTab === 'series') && favSeries.length > 0"
              class="space-y-3 bg-surface/40 p-4 rounded-2xl border border-line/80"
            >
              <div class="flex items-center justify-between pb-2 border-b border-line select-none">
                <button
                  @click="toggleFavSection('series')"
                  class="flex items-center gap-2 text-left group cursor-pointer"
                >
                  <component
                    :is="favCollapsed.series ? ChevronRight : ChevronDown"
                    class="w-4 h-4 text-fg-4 group-hover:text-accent transition"
                  />
                  <Layers class="w-4 h-4 text-accent" />
                  <h2 class="text-sm font-bold text-fg group-hover:text-accent transition">系列专题集</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favSeries.length }}</span>
                </button>
                <button
                  v-if="favSubTab === 'all'"
                  @click="favSubTab = 'series'"
                  class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                >
                  <span>仅看此类</span>
                  <span>→</span>
                </button>
              </div>

              <div
                v-show="!favCollapsed.series"
                class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3.5 pt-1"
              >
                <div
                  v-for="s in favSeries"
                  :key="s.key"
                  @click="openSeriesModalByRoot(s.title || s.key, s.studio_name)"
                  class="group relative rounded-2xl bg-surface border border-line hover:border-accent/40 p-2.5 space-y-2 hover:shadow-lg transition cursor-pointer flex flex-col justify-between"
                >
                  <div class="aspect-[2/3] w-full rounded-xl overflow-hidden bg-surface-2 relative border border-line/40">
                    <SeriesCollageCover
                      :covers="s.covers"
                      :single-cover="s.cover_full"
                      :title="s.title || s.key"
                      aspect-ratio="h-full w-full"
                      class="w-full h-full group-hover:scale-105 transition-transform duration-300"
                    />

                    <!-- Heart toggle button -->
                    <button
                      @click.stop="toggleFavoriteEntity('series', s.key)"
                      class="absolute top-2 right-2 p-1.5 rounded-full bg-black/60 hover:bg-black/80 text-rose-400 backdrop-blur-sm border border-white/10 transition z-10"
                      title="取消收藏系列"
                    >
                      <Heart class="w-3.5 h-3.5 fill-rose-400 text-rose-400" />
                    </button>

                    <!-- Series count badge -->
                    <div class="absolute bottom-2 left-2 px-2 py-0.5 rounded-lg bg-black/75 backdrop-blur-md text-[10px] font-bold text-accent border border-accent/30 font-mono">
                      {{ s.works_count ? `${s.works_count} 部全集` : '系列作品' }}
                    </div>
                  </div>

                  <div class="space-y-0.5">
                    <h3 class="text-xs font-bold text-fg line-clamp-2 leading-snug group-hover:text-accent transition" :title="s.title || s.key">
                      {{ s.title || s.key }}
                    </h3>
                    <div v-if="s.studio_name" class="text-[11px] text-fg-4 truncate">
                      {{ s.studio_name }}
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- 3. Movies Section (喜爱影片) -->
            <section
              v-if="(favSubTab === 'all' || favSubTab === 'movie') && favMovies.length > 0"
              class="space-y-3 bg-surface/40 p-4 rounded-2xl border border-line/80"
            >
              <div class="flex items-center justify-between pb-2 border-b border-line select-none">
                <button
                  @click="toggleFavSection('movie')"
                  class="flex items-center gap-2 text-left group cursor-pointer"
                >
                  <component
                    :is="favCollapsed.movie ? ChevronRight : ChevronDown"
                    class="w-4 h-4 text-fg-4 group-hover:text-accent transition"
                  />
                  <Film class="w-4 h-4 text-accent" />
                  <h2 class="text-sm font-bold text-fg group-hover:text-accent transition">{{ FAVORITE_LABELS.movie }}</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favMovies.length }}</span>
                </button>
                <button
                  v-if="favSubTab === 'all'"
                  @click="favSubTab = 'movie'"
                  class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                >
                  <span>仅看此类</span>
                  <span>→</span>
                </button>
              </div>
              <div
                v-show="!favCollapsed.movie || favSubTab === 'movie'"
                class="grid transition-all duration-200 pt-1"
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

            <!-- 4. Performers Section (演员) -->
            <section
              v-if="(favSubTab === 'all' || favSubTab === 'performer') && favPerformers.length > 0"
              class="space-y-3 bg-surface/40 p-4 rounded-2xl border border-line/80"
            >
              <div class="flex items-center justify-between pb-2 border-b border-line select-none">
                <button
                  @click="toggleFavSection('performer')"
                  class="flex items-center gap-2 text-left group cursor-pointer"
                >
                  <component
                    :is="favCollapsed.performer ? ChevronRight : ChevronDown"
                    class="w-4 h-4 text-fg-4 group-hover:text-accent transition"
                  />
                  <UserIcon class="w-4 h-4 text-accent" />
                  <h2 class="text-sm font-bold text-fg group-hover:text-accent transition">{{ FAVORITE_LABELS.performer }}</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favPerformers.length }}</span>
                </button>
                <button
                  v-if="favSubTab === 'all'"
                  @click="favSubTab = 'performer'"
                  class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                >
                  <span>仅看此类</span>
                  <span>→</span>
                </button>
              </div>
              <div
                v-show="!favCollapsed.performer || favSubTab === 'performer'"
                class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-3 pt-1"
              >
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

            <!-- 5. Episodes Section (片段) -->
            <section
              v-if="(favSubTab === 'all' || favSubTab === 'episode') && favEpisodes.length > 0"
              class="space-y-3 bg-surface/40 p-4 rounded-2xl border border-line/80"
            >
              <div class="flex items-center justify-between pb-2 border-b border-line select-none flex-wrap gap-2">
                <button
                  @click="toggleFavSection('episode')"
                  class="flex items-center gap-2 text-left group cursor-pointer"
                >
                  <component
                    :is="favCollapsed.episode ? ChevronRight : ChevronDown"
                    class="w-4 h-4 text-fg-4 group-hover:text-accent transition"
                  />
                  <Layers class="w-4 h-4 text-accent" />
                  <h2 class="text-sm font-bold text-fg group-hover:text-accent transition">{{ FAVORITE_LABELS.episode }}</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favEpisodes.length }}</span>
                </button>

                <div class="flex items-center gap-2.5">
                  <!-- Grid Size Selector (美化：调整分集网格尺寸) -->
                  <div class="flex items-center bg-surface-2/80 rounded-xl p-0.5 border border-line text-xs font-semibold">
                    <span class="text-[10px] text-fg-4 px-2 select-none">尺寸:</span>
                    <button
                      v-for="opt in [
                        { cols: 1, label: '大' },
                        { cols: 2, label: '中' },
                        { cols: 3, label: '小' },
                      ]"
                      :key="opt.cols"
                      @click="setFavEpisodeCols(opt.cols)"
                      :class="[
                        'px-2 py-0.5 rounded-lg text-xs font-medium transition cursor-pointer',
                        favEpisodeCols === opt.cols
                          ? 'bg-accent-fill text-on-fill shadow-xs'
                          : 'text-fg-4 hover:text-fg hover:bg-surface-3/50'
                      ]"
                      :title="`切换为每行 ${opt.cols} 列网格`"
                    >
                      {{ opt.label }}
                    </button>
                  </div>

                  <button
                    v-if="favSubTab === 'all'"
                    @click="favSubTab = 'episode'"
                    class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                  >
                    <span>仅看此类</span>
                    <span>→</span>
                  </button>
                </div>
              </div>

              <div
                v-show="!favCollapsed.episode || favSubTab === 'episode'"
                :class="[
                  'grid gap-3.5 pt-1',
                  favEpisodeCols === 1
                    ? 'grid-cols-1'
                    : favEpisodeCols === 2
                    ? 'grid-cols-1 lg:grid-cols-2'
                    : 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3'
                ]"
              >
                <div
                  v-for="f in favEpisodes"
                  :key="f.key"
                  @click="handleFavEpisodeClick(f)"
                  class="group flex gap-3.5 rounded-2xl bg-surface/60 border border-line/80 hover:border-accent-fill/50 transition-all duration-200 overflow-hidden cursor-pointer select-none p-3"
                >
                  <div
                    :class="[
                      'relative shrink-0 aspect-video rounded-xl overflow-hidden bg-sunken',
                      favEpisodeCols === 1 ? 'w-44 sm:w-56' : favEpisodeCols === 2 ? 'w-32 sm:w-40' : 'w-24 sm:w-28'
                    ]"
                  >
                    <img
                      v-if="f.thumbnail_url"
                      :src="getImageUrl(f.thumbnail_url)"
                      :alt="f.title || ''"
                      loading="lazy"
                      referrerpolicy="no-referrer"
                      class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                    />
                    <div v-else class="w-full h-full flex items-center justify-center text-fg-5">
                      <Layers class="w-5 h-5 stroke-1" />
                    </div>
                  </div>
                  <div class="flex-1 min-w-0 flex flex-col justify-between gap-1.5">
                    <div class="min-w-0">
                      <div class="flex items-start justify-between gap-2">
                        <span
                          class="font-bold text-accent-soft truncate"
                          :class="favEpisodeCols === 1 ? 'text-sm' : 'text-xs'"
                          :title="f.title || ''"
                        >
                          {{ f.title || '独立分集 #' + f.key }}
                        </span>
                        <button
                          @click.stop="toggleFavoriteEntity('episode', f.key)"
                          class="shrink-0 text-danger transition hover:scale-110 p-0.5"
                          title="取消收藏该片段"
                        >
                          <Heart class="w-4 h-4" fill="currentColor" />
                        </button>
                      </div>
                      <div v-if="f.movie_title" class="text-[11px] text-fg-3 mt-1 flex items-center gap-1 truncate">
                        <Film class="w-3 h-3 text-fg-4 shrink-0" />
                        <span
                          class="truncate hover:text-accent hover:underline"
                          :title="favFilmFull(f)"
                          @click.stop="f.movie_id && openMovieDetailById(f.movie_id)"
                        >
                          出处: {{ favFilmTitle(f) }}
                        </span>
                      </div>
                      <div v-else class="text-[10px] text-fg-5 mt-1 flex items-center gap-1 italic">
                        <span>独立收录分集</span>
                      </div>
                    </div>
                    <div class="flex items-center gap-2 text-[11px] text-fg-4">
                      <span v-if="f.studio_name" class="truncate max-w-[150px] font-medium" :title="f.studio_name">{{ f.studio_name }}</span>
                      <span v-if="f.has_zh" class="text-success flex items-center gap-0.5 shrink-0 text-[10px] font-bold">
                        <Languages class="w-3 h-3" />中
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- 6. Studios Section (片商) -->
            <section
              v-if="(favSubTab === 'all' || favSubTab === 'studio') && favStudios.length > 0"
              class="space-y-3 bg-surface/40 p-4 rounded-2xl border border-line/80"
            >
              <div class="flex items-center justify-between pb-2 border-b border-line select-none">
                <button
                  @click="toggleFavSection('studio')"
                  class="flex items-center gap-2 text-left group cursor-pointer"
                >
                  <component
                    :is="favCollapsed.studio ? ChevronRight : ChevronDown"
                    class="w-4 h-4 text-fg-4 group-hover:text-accent transition"
                  />
                  <Building2 class="w-4 h-4 text-accent" />
                  <h2 class="text-sm font-bold text-fg">{{ FAVORITE_LABELS.studio }}</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favStudios.length }}</span>
                </button>
                <button
                  v-if="favSubTab === 'all'"
                  @click="favSubTab = 'studio'"
                  class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                >
                  <span>仅看此类</span>
                  <span>→</span>
                </button>
              </div>
              <div
                v-show="!favCollapsed.studio || favSubTab === 'studio'"
                class="flex flex-wrap gap-2 pt-1"
              >
                <div
                  v-for="f in favStudios"
                  :key="f.key"
                  class="group flex items-center gap-2 pl-3 pr-1.5 py-1.5 rounded-xl bg-surface/70 border border-line hover:border-accent-fill/50 transition"
                >
                  <button
                    @click="openStudioDetail({ name: f.key, works_count: f.works_count ?? undefined })"
                    class="text-xs font-medium text-fg-2 hover:text-accent-soft transition cursor-pointer"
                    :title="`打开 ${f.key} 的片商档案`"
                  >
                    {{ f.key }}
                  </button>
                  <span class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2 text-fg-4 font-mono">{{ f.works_count || 0 }}</span>
                  <button
                    @click.stop="toggleFavoriteEntity('studio', f.key)"
                    class="text-danger hover:text-danger-soft transition cursor-pointer"
                    title="取消收藏该片商"
                  >
                    <Heart class="w-3 h-3" fill="currentColor" />
                  </button>
                </div>
              </div>
            </section>

            <!-- 7. Directors Section (导演) -->
            <section
              v-if="(favSubTab === 'all' || favSubTab === 'director') && favDirectors.length > 0"
              class="space-y-3 bg-surface/40 p-4 rounded-2xl border border-line/80"
            >
              <div class="flex items-center justify-between pb-2 border-b border-line select-none">
                <button
                  @click="toggleFavSection('director')"
                  class="flex items-center gap-2 text-left group cursor-pointer"
                >
                  <component
                    :is="favCollapsed.director ? ChevronRight : ChevronDown"
                    class="w-4 h-4 text-fg-4 group-hover:text-accent transition"
                  />
                  <Clapperboard class="w-4 h-4 text-accent" />
                  <h2 class="text-sm font-bold text-fg">{{ FAVORITE_LABELS.director }}</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favDirectors.length }}</span>
                </button>
                <button
                  v-if="favSubTab === 'all'"
                  @click="favSubTab = 'director'"
                  class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                >
                  <span>仅看此类</span>
                  <span>→</span>
                </button>
              </div>
              <div
                v-show="!favCollapsed.director || favSubTab === 'director'"
                class="flex flex-wrap gap-2 pt-1"
              >
                <div
                  v-for="f in favDirectors"
                  :key="f.key"
                  class="group flex items-center gap-2 pl-3 pr-1.5 py-1.5 rounded-xl bg-surface/70 border border-line hover:border-accent-fill/50 transition"
                >
                  <button
                    @click="openDirectorDetail({ name: f.key })"
                    class="text-xs font-medium text-fg-2 hover:text-accent-soft transition cursor-pointer"
                    :title="`打开 ${f.key} 的导演档案`"
                  >
                    {{ f.key }}
                  </button>
                  <span class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2 text-fg-4 font-mono">{{ f.works_count || 0 }}</span>
                  <button
                    @click.stop="toggleFavoriteEntity('director', f.key)"
                    class="text-danger hover:text-danger-soft transition cursor-pointer"
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
            <div class="text-sm font-semibold text-fg-3">暂无收藏与标记条目</div>
            <div class="text-xs text-fg-5">想看、已看、喜爱影片、演员、片商、导演和分集片段都会汇集在此，点击心形或详情页标记即可加入</div>
          </div>
        </div>

        <!-- 6. Settings & Cache Tab -->
        <div v-else-if="currentTab === 'settings'" class="max-w-3xl space-y-6">
          <div class="flex items-center justify-between border-b border-line pb-4 flex-wrap gap-4">
            <div>
              <h1 class="text-xl md:text-2xl font-bold text-fg tracking-tight">存储、缓存与系统设置</h1>
              <p class="text-xs text-fg-4 mt-1">管理外观主题、图标方案、界面语言、本地数据库与隐私配置</p>
            </div>
          </div>

          <!-- Subcategory Capsule Switcher -->
          <div class="flex items-center gap-2 flex-wrap">
            <button
              v-for="st in [
                { id: 'all', labelKey: 'settings.all', label: '全部设置', icon: SlidersHorizontal },
                { id: 'appearance', labelKey: 'settings.appearance', label: '外观与图标', icon: Palette },
                { id: 'localization', labelKey: 'settings.localization', label: '语言与本地化', icon: Globe },
                { id: 'data', labelKey: 'settings.data', label: '数据与存储', icon: HardDrive },
                { id: 'privacy', labelKey: 'settings.privacy', label: '隐私与安全', icon: Shield },
              ]"
              :key="st.id"
              @click="settingsSubTab = (st.id as SettingsSubTab)"
              :class="[
                'px-3.5 py-1.5 rounded-xl text-xs font-bold border transition flex items-center gap-1.5 cursor-pointer shadow-xs',
                settingsSubTab === st.id
                  ? 'bg-accent-fill text-on-fill border-accent shadow-sm'
                  : 'bg-surface-2/70 text-fg-3 border-line hover:text-fg hover:bg-surface-2'
              ]"
            >
              <component :is="st.icon" class="w-3.5 h-3.5" />
              <span>{{ t(st.labelKey, st.label) }}</span>
            </button>
          </div>

          <!-- Section 0: 外观. Three styles × dark/light, flat, plus 跟随系统. -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'appearance'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4"
          >
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

          <!-- Section 0.1: App Icon & Branding -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'appearance'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4"
          >
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div class="flex items-center gap-3">
                <Sparkles class="w-5 h-5 text-accent" />
                <div>
                  <div class="text-sm font-bold text-fg">应用图标方案 (App Icon)</div>
                  <div class="text-xs text-fg-3">提供四套独具文化认同与典藏质感的定制图标设计，支持一键切换与预览（默认预设方案 A）</div>
                </div>
              </div>
              <span class="text-xs px-2.5 py-1 rounded-full bg-accent-fill/15 text-accent font-semibold border border-accent-fill/30">
                当前生效: {{ ICON_SCHEMES.find(s => s.id === currentIconScheme)?.name.split(' ')[0] }}
              </span>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-3.5">
              <div
                v-for="scheme in ICON_SCHEMES"
                :key="scheme.id"
                @click="setIconScheme(scheme.id)"
                class="relative p-4 rounded-2xl border transition-all cursor-pointer flex flex-col justify-between gap-3"
                :class="currentIconScheme === scheme.id
                  ? 'bg-accent-fill/10 border-accent shadow-md shadow-accent-fill/10 ring-1 ring-accent/30'
                  : 'bg-surface border-line hover:border-line-strong hover:bg-surface-2/40'"
              >
                <!-- Card Header with Icon Preview -->
                <div class="flex items-start gap-4">
                  <div class="shrink-0 relative group">
                    <AppIcon
                      :scheme="scheme.id"
                      :size="64"
                      class="rounded-2xl shadow-lg border border-line/40 group-hover:scale-105 transition-transform"
                    />
                    <div
                      v-if="currentIconScheme === scheme.id"
                      class="absolute -bottom-1 -right-1 w-5 h-5 rounded-full bg-accent text-on-fill flex items-center justify-center shadow"
                    >
                      <Check class="w-3 h-3 stroke-[3]" />
                    </div>
                  </div>

                  <div class="min-w-0 flex-1 space-y-1">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="text-xs font-bold text-fg tracking-tight">{{ scheme.name }}</span>
                      <span
                        v-if="scheme.badge"
                        class="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-accent/20 text-accent border border-accent/30"
                      >
                        {{ scheme.badge }}
                      </span>
                    </div>
                    <div class="text-[11px] font-mono text-fg-4">{{ scheme.subtitle }}</div>
                    <div class="flex items-center gap-2 text-[11px] text-fg-3 flex-wrap pt-0.5">
                      <span class="px-1.5 py-0.5 rounded bg-surface-2 border border-line text-[10px] text-fg-3">
                        {{ scheme.style }}
                      </span>
                      <span class="text-[10px] text-accent-soft font-medium">
                        防窥: {{ '★'.repeat(scheme.stars) + '☆'.repeat(5 - scheme.stars) }}
                      </span>
                    </div>
                  </div>
                </div>

                <!-- Description -->
                <p class="text-xs text-fg-3 leading-relaxed">
                  {{ scheme.description }}
                </p>

                <!-- Footer tags & action -->
                <div class="flex items-center justify-between pt-1 border-t border-line/40 text-[11px]">
                  <div class="flex items-center gap-1.5 flex-wrap">
                    <span
                      v-for="tag in scheme.tags"
                      :key="tag"
                      class="text-[10px] text-fg-4"
                    >
                      #{{ tag }}
                    </span>
                  </div>
                  <div class="flex items-center gap-2">
                    <button
                      @click.stop="downloadIconFile(scheme.id, 'png')"
                      class="text-[11px] text-accent hover:underline flex items-center gap-1 shrink-0"
                      title="下载高清 PNG 图标"
                    >
                      <Download class="w-3 h-3" />
                      <span>PNG</span>
                    </button>
                    <span class="text-fg-4">·</span>
                    <button
                      @click.stop="downloadIconFile(scheme.id, 'svg')"
                      class="text-[11px] text-fg-3 hover:text-accent hover:underline flex items-center gap-1 shrink-0"
                      title="下载矢量 SVG 图标"
                    >
                      <span>SVG</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div class="text-xs text-fg-4 flex items-center gap-2 pt-1">
              <Info class="w-3.5 h-3.5 text-fg-4 shrink-0" />
              <span>所选图标将即时在应用导航栏、标签页中更新生效。您也可点击「下载矢量」获取源文件用于替换 macOS 应用与程序坞 (Dock) 图标。</span>
            </div>
          </div>

          <!-- Section 0.5: Language & Localization -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'localization'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-5"
          >
            <div class="flex items-center gap-3">
              <Globe class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">语言与本地化</div>
                <div class="text-xs text-fg-3">支持 7 种界面菜单语言，即时切换界面文字</div>
              </div>
            </div>

            <!-- UI Menu Language -->
            <div class="space-y-2.5">
              <div class="text-xs font-semibold text-fg-2">菜单与界面语言</div>
              <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
                <button
                  v-for="lang in SUPPORTED_LANGUAGES"
                  :key="lang.code"
                  @click="setLocale(lang.code)"
                  class="p-2.5 rounded-xl border text-xs font-medium flex items-center justify-between transition cursor-pointer"
                  :class="currentLocale === lang.code
                    ? 'bg-accent-fill/15 border-accent-fill/50 text-accent font-bold shadow-sm'
                    : 'bg-surface border-line hover:border-line-strong text-fg-3 hover:text-fg-2'"
                >
                  <div class="flex flex-col text-left">
                    <span class="text-[11px]">{{ lang.label }}</span>
                    <span class="text-[10px] text-fg-4">{{ lang.native }}</span>
                  </div>
                  <Check v-if="currentLocale === lang.code" class="w-3.5 h-3.5 text-accent" />
                </button>
              </div>
            </div>
          </div>

          <!-- Section 0.6: Privacy & History -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'privacy'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-5"
          >
            <div class="flex items-center gap-3">
              <Shield class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">隐私与数据安全</div>
                <div class="text-xs text-fg-3">所有使用统计和历史记录均保存在本地设备，永不上报任何云端服务器</div>
              </div>
            </div>

            <!-- Toggles -->
            <div class="space-y-3">
              <!-- Collect Analytics Toggle -->
              <div class="flex items-center justify-between p-3 rounded-xl bg-surface border border-line">
                <div>
                  <div class="text-xs font-semibold text-fg-2">收集本地使用统计数据</div>
                  <div class="text-[11px] text-fg-4">记录停留时间、浏览次数、评星分布等维度，用于生成个人统计看板与解锁成就奖杯</div>
                </div>
                <button
                  @click="savePrivacySettings({ collectAnalytics: !privacySettings.collectAnalytics })"
                  class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
                  :class="privacySettings.collectAnalytics ? 'bg-accent-fill' : 'bg-surface-3'"
                >
                  <span
                    class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out"
                    :class="privacySettings.collectAnalytics ? 'translate-x-5' : 'translate-x-0'"
                  />
                </button>
              </div>

              <!-- Keep Search History Toggle -->
              <div class="flex items-center justify-between p-3 rounded-xl bg-surface border border-line">
                <div>
                  <div class="text-xs font-semibold text-fg-2">保留搜索历史记录</div>
                  <div class="text-[11px] text-fg-4">在搜索框聚焦时在下拉菜单展示最近搜索词，支持一键快捷回填</div>
                </div>
                <button
                  @click="savePrivacySettings({ keepSearchHistory: !privacySettings.keepSearchHistory })"
                  class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
                  :class="privacySettings.keepSearchHistory ? 'bg-accent-fill' : 'bg-surface-3'"
                >
                  <span
                    class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out"
                    :class="privacySettings.keepSearchHistory ? 'translate-x-5' : 'translate-x-0'"
                  />
                </button>
              </div>

              <!-- Keep Browse History Toggle -->
              <div class="flex items-center justify-between p-3 rounded-xl bg-surface border border-line">
                <div>
                  <div class="text-xs font-semibold text-fg-2">保留浏览足迹历史</div>
                  <div class="text-[11px] text-fg-4">记录最近探索的影片、演员、片商和导演</div>
                </div>
                <button
                  @click="savePrivacySettings({ keepBrowseHistory: !privacySettings.keepBrowseHistory })"
                  class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
                  :class="privacySettings.keepBrowseHistory ? 'bg-accent-fill' : 'bg-surface-3'"
                >
                  <span
                    class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out"
                    :class="privacySettings.keepBrowseHistory ? 'translate-x-5' : 'translate-x-0'"
                  />
                </button>
              </div>
            </div>

            <!-- Clear Buttons -->
            <div class="flex items-center gap-3 pt-2 border-t border-line/60 flex-wrap">
              <button
                @click="clearSearchHistory"
                class="px-3.5 py-1.5 rounded-xl bg-surface-2 hover:bg-surface-3 border border-line-strong text-xs font-medium text-fg-3 hover:text-fg transition flex items-center gap-1.5 cursor-pointer"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>清空搜索历史</span>
              </button>

              <button
                @click="clearBrowseHistory"
                class="px-3.5 py-1.5 rounded-xl bg-surface-2 hover:bg-surface-3 border border-line-strong text-xs font-medium text-fg-3 hover:text-fg transition flex items-center gap-1.5 cursor-pointer"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>清空浏览历史</span>
              </button>

              <button
                @click="resetAllAnalytics"
                class="px-3.5 py-1.5 rounded-xl bg-danger-fill/10 hover:bg-danger-fill/20 border border-danger-fill/30 text-xs font-semibold text-danger flex items-center gap-1.5 transition ml-auto cursor-pointer"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>重置所有使用统计数据</span>
              </button>
            </div>
          </div>

          <!-- Section 1: SQLite Engine & Stats -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'data'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-5"
          >
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div class="flex items-center gap-3">
                <HardDrive class="w-5 h-5 text-accent" />
                <div>
                  <div class="text-sm font-bold text-fg">本地离线数据中心</div>
                  <div class="text-xs text-fg-3">SQLite3 WAL 极速引擎 + FTS5 全文搜索</div>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <span
                  v-if="dbInfo?.valid"
                  class="px-2.5 py-1 rounded-full bg-success-fill/20 border border-success-fill/30 text-success-soft text-[11px] font-medium flex items-center gap-1.5"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-success animate-pulse"></span>
                  已连接数据库 ({{ dbInfo.file_size_mb }} MB)
                </span>
                <span
                  v-else
                  class="px-2.5 py-1 rounded-full bg-danger-fill/20 border border-danger-fill/30 text-danger-soft text-[11px] font-medium flex items-center gap-1.5"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-danger"></span>
                  未找到有效数据库
                </span>
              </div>
            </div>

            <!-- Database Path Configuration & Intelligent Detection -->
            <div class="p-4 rounded-xl bg-surface/80 border border-line-strong/60 space-y-3 text-xs">
              <div class="flex items-center justify-between gap-2">
                <span class="font-semibold text-fg-2">数据库存储路径</span>
                <span v-if="dbInfo?.custom_path" class="text-[10px] px-1.5 py-0.5 rounded bg-accent-fill/15 text-accent-soft border border-accent-fill/25">
                  自定义路径
                </span>
                <span v-else class="text-[10px] text-fg-4">智能默认 / 自动解析</span>
              </div>

              <!-- Current resolved path display -->
              <div class="flex items-center gap-2 px-3 py-2 rounded-lg bg-sunken font-mono text-[11px] text-fg-3 break-all border border-line select-all">
                <span class="text-fg-4 shrink-0">当前路径:</span>
                <span class="flex-1 text-fg">{{ dbInfo?.path || '未关联数据库文件' }}</span>
              </div>

              <!-- Custom path input and buttons -->
              <div class="flex flex-col sm:flex-row gap-2 pt-1">
                <div class="flex-1 relative">
                  <input
                    v-model="customDbInput"
                    type="text"
                    placeholder="输入或粘贴 gevi.db 绝对路径，如 ~/Documents/.../gevi.db"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none"
                    @keydown.enter="applyCustomDbPath()"
                  />
                </div>
                <div class="flex items-center gap-2 shrink-0">
                  <button
                    @click="handlePickDbFile"
                    :disabled="dbSwitching"
                    class="px-3 py-2 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs border border-line-strong flex items-center gap-1.5 transition disabled:opacity-50"
                    title="在 Finder 中选取文件"
                  >
                    <FolderOpen class="w-3.5 h-3.5 text-accent" />
                    <span>浏览…</span>
                  </button>
                  <button
                    @click="applyCustomDbPath()"
                    :disabled="dbSwitching || !customDbInput.trim()"
                    class="px-4 py-2 rounded-lg bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs shadow-sm flex items-center gap-1.5 transition disabled:opacity-40"
                  >
                    <span>{{ dbSwitching ? '连接中…' : '保存并连接' }}</span>
                  </button>
                  <button
                    v-if="dbInfo?.custom_path"
                    @click="resetToAutoDbPath"
                    :disabled="dbSwitching"
                    class="px-3 py-2 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg-2 text-xs border border-line-strong transition"
                    title="清除自定义路径，改由智能识别"
                  >
                    恢复自动
                  </button>
                </div>
              </div>

              <!-- Create new blank database action for settings -->
              <div class="pt-2 border-t border-line/60 flex items-center justify-between">
                <div class="text-[11px] text-fg-4">
                  首次使用或新建独立库？系统将在“文稿”目录创建全新标准数据库：
                </div>
                <button
                  @click="handleCreateNewDatabase"
                  :disabled="dbSwitching"
                  class="px-2.5 py-1.5 rounded-lg bg-gradient-to-r from-amber-500/15 via-orange-500/15 to-rose-500/15 hover:from-amber-500/25 hover:to-rose-500/25 border border-amber-500/30 text-amber-300 text-[11px] font-bold flex items-center gap-1.5 transition disabled:opacity-50 cursor-pointer shrink-0"
                >
                  <Sparkles class="w-3.5 h-3.5 text-amber-400" />
                  <span>{{ dbSwitching ? '创建建表中…' : '一键创建全新空白影库' }}</span>
                </button>
              </div>

              <!-- Intelligent detection scanner -->
              <div class="pt-2 border-t border-line/60 flex flex-col gap-2">
                <div class="flex items-center justify-between">
                  <div class="text-[11px] text-fg-4">
                    找不到文件？点击进行全盘毫秒级 Spotlight 扫描：
                  </div>
                  <button
                    @click="handleScanDatabases"
                    :disabled="dbScanning"
                    class="px-2.5 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong flex items-center gap-1.5 transition disabled:opacity-50"
                  >
                    <RefreshCw class="w-3 h-3 text-accent" :class="dbScanning ? 'animate-spin' : ''" />
                    <span>{{ dbScanning ? '正在扫描全盘…' : '智能扫描系统中的数据库' }}</span>
                  </button>
                </div>

                <!-- Detected candidates -->
                <div v-if="dbCandidates.length > 0" class="space-y-1.5 pt-1">
                  <div class="text-[10px] text-fg-4 font-semibold uppercase tracking-wider">智能识别到的候选数据库：</div>
                  <div
                    v-for="cand in dbCandidates"
                    :key="cand"
                    class="flex items-center justify-between gap-2 p-2 rounded-lg bg-surface border border-line hover:border-accent-fill/30 transition text-[11px]"
                  >
                    <span class="font-mono text-fg-2 truncate flex-1" :title="cand">{{ cand }}</span>
                    <button
                      v-if="cand !== dbInfo?.path"
                      @click="applyCustomDbPath(cand)"
                      :disabled="dbSwitching"
                      class="px-2.5 py-1 rounded bg-accent-fill/15 hover:bg-accent-fill text-accent-soft hover:text-on-fill font-medium text-[11px] border border-accent-fill/30 transition shrink-0"
                    >
                      切换至此库
                    </button>
                    <span v-else class="text-[10px] text-success-soft px-2 py-0.5 rounded bg-success-fill/10 border border-success-fill/20 shrink-0">
                      当前使用中
                    </span>
                  </div>
                </div>
              </div>

              <!-- Feedback message -->
              <div
                v-if="dbMessage"
                class="p-2.5 rounded-lg text-[11px] font-medium"
                :class="dbMessage.ok ? 'bg-success-fill/10 text-success-soft border border-success-fill/20' : 'bg-danger-fill/10 text-danger-soft border border-danger-fill/20'"
              >
                {{ dbMessage.text }}
              </div>
            </div>

            <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs pt-1">
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
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'data'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4"
          >
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

          <!-- Section 3 Notice: Merged into Plugins tab -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'localization'"
            class="p-6 rounded-2xl bg-surface/60 border border-line flex items-center justify-between gap-4"
          >
            <div class="flex items-center gap-3">
              <Languages class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">大模型 AI 翻译引擎与剧情简介翻译</div>
                <div class="text-xs text-fg-3">
                  已合并至「功能插件」专区，支持多模型 API 配置、目标语言切换、试跑与批量翻译
                </div>
              </div>
            </div>
            <button
              @click="currentTab = 'plugins'"
              class="px-3.5 py-2 rounded-xl bg-accent-fill/15 hover:bg-accent-fill text-accent-soft hover:text-on-fill font-medium text-xs border border-accent-fill/30 transition shrink-0 cursor-pointer"
            >
              前往插件中心配置 →
            </button>
          </div>

          <!--
            Section 3b: Performer attribute glossary.
          -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'localization'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4"
          >
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

          <!-- Section 4: Data Import & Export (Database Package & Personal Migration) -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'data'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-5"
          >
            <div class="flex items-center gap-3">
              <Download class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">数据导入、导出与数据库备份中心</div>
                <div class="text-xs text-fg-3">支持完整 SQLite 数据库打包导出/导入，以及轻量个人扩展数据 (JSON) 的跨设备迁移</div>
              </div>
            </div>

            <!-- Database Backup Messages -->
            <div v-if="dbBackupStatusMsg" class="p-3 rounded-xl bg-accent-fill/10 border border-accent-fill/20 text-xs text-accent-soft flex items-center gap-2">
              <Sparkles class="w-4 h-4 shrink-0 text-accent" />
              <span>{{ dbBackupStatusMsg }}</span>
            </div>

            <!-- User Data Import Messages -->
            <div v-if="importStatusMsg" class="p-3 rounded-xl bg-success-fill/10 border border-success-fill/20 text-xs text-success-soft">
              {{ importStatusMsg }}
            </div>

            <!-- Two Sub-panels Grid -->
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
              <!-- Panel A: Full Database Package (.db) -->
              <div class="p-4 rounded-xl bg-surface-2/60 border border-line-strong space-y-3 flex flex-col justify-between">
                <div>
                  <div class="text-xs font-bold text-fg flex items-center gap-1.5">
                    <HardDrive class="w-4 h-4 text-purple-400" />
                    <span>完整离线数据库打包 (.db)</span>
                  </div>
                  <p class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                    包含所有影视长片、演员档案、分集剧照索引、片商分类以及 AI 中文翻译库的完整数据库。
                  </p>
                </div>

                <div class="flex items-center gap-2 flex-wrap pt-1">
                  <button
                    @click="handleExportDatabase"
                    :disabled="isDbExporting"
                    class="px-3.5 py-2 rounded-xl bg-surface-3 hover:bg-surface-3/80 text-fg font-medium text-xs border border-line flex items-center gap-1.5 transition cursor-pointer disabled:opacity-50"
                  >
                    <Download class="w-3.5 h-3.5 text-purple-400" />
                    <span>{{ isDbExporting ? '打包中…' : '打包导出数据库 (.db)' }}</span>
                  </button>

                  <button
                    @click="handleImportDatabase"
                    class="px-3.5 py-2 rounded-xl bg-surface-3 hover:bg-surface-3/80 text-fg font-medium text-xs border border-line flex items-center gap-1.5 transition cursor-pointer"
                  >
                    <Upload class="w-3.5 h-3.5 text-purple-400" />
                    <span>载入外部数据库 (.db)</span>
                  </button>
                </div>
              </div>

              <!-- Panel B: Personal User Data (JSON) -->
              <div class="p-4 rounded-xl bg-surface-2/60 border border-line-strong space-y-3 flex flex-col justify-between">
                <div>
                  <div class="text-xs font-bold text-fg flex items-center gap-1.5">
                    <Bookmark class="w-4 h-4 text-accent" />
                    <span>个人扩展标记与片单 (JSON)</span>
                  </div>
                  <p class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                    仅导出轻量用户个人数据（私密评星、想看/已看状态、自定义标签、私密笔记与收藏夹）。
                  </p>
                </div>

                <div class="flex items-center gap-2 flex-wrap pt-1">
                  <button
                    @click="handleExportUserData"
                    class="px-3.5 py-2 rounded-xl bg-surface-3 hover:bg-surface-3/80 text-fg font-medium text-xs border border-line flex items-center gap-1.5 transition cursor-pointer"
                  >
                    <Download class="w-3.5 h-3.5 text-accent" />
                    <span>导出标记备份 (JSON)</span>
                  </button>

                  <button
                    @click="triggerImportFileInput"
                    class="px-3.5 py-2 rounded-xl bg-surface-3 hover:bg-surface-3/80 text-fg font-medium text-xs border border-line flex items-center gap-1.5 transition cursor-pointer"
                  >
                    <Upload class="w-3.5 h-3.5 text-accent" />
                    <span>导入恢复标记 (JSON)</span>
                  </button>
                  <input ref="fileInputRef" type="file" accept=".json" class="hidden" @change="handleImportFile" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 7. Local Analytics Tab -->
        <div v-else-if="currentTab === 'analytics'" class="space-y-6">
          <AnalyticsView @open-trophies="currentTab = 'trophies'" />
        </div>

        <!-- 8. Plugins Center Tab -->
        <div v-else-if="currentTab === 'plugins'" class="space-y-6">
          <PluginsView @open-trophies="currentTab = 'trophies'" />
        </div>

        <!-- 9. PSN 77 Trophies Hall Tab -->
        <div v-else-if="currentTab === 'trophies'" class="space-y-6">
          <TrophiesView @back="currentTab = 'plugins'" />
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
      @select-movie="openMovieDetailById"
      @select-performer="openPerformerDetail"
      @filter-studio="filterByStudio"
      @filter-director="(name) => openDirectorDetail({ name })"
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
      @select-episode-id="openEpisodeDetailById"
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

    <DirectorDetailModal
      :director="selectedDirector"
      :works="directorWorks"
      :loading="directorWorksLoading"
      :lang="descLang"
      :z-index="layerOf('director')"
      :is-top="modalStack[modalStack.length - 1] === 'director'"
      :is-favorite="selectedDirector ? isFavorite('director', selectedDirector.name) : false"
      :favorite-keys="favorites"
      @close="closeDirectorDetail"
      @select-movie="openMovieDetail"
      @toggle-favorite="toggleDirectorFavorite"
      @toggle-entity-favorite="toggleFavoriteEntity"
      @open-studio="openStudioLibrary"
      @filter-director="filterByDirector"
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

    <SeriesModal
      v-if="showAppSeriesModal && seriesModalData"
      :series="seriesModalData"
      :z-index="10000"
      :is-favorite="isFavorite('series', seriesModalData.root_title)"
      @close="showAppSeriesModal = false"
      @select-movie="(id) => { showAppSeriesModal = false; openMovieDetailById(id); }"
      @toggle-favorite="(rootTitle) => toggleFavoriteEntity('series', rootTitle)"
    />

    <!--
      @reset is the full movie reset, matching what the drawer's own 重置 button does.
      It used to assign only {query, studio, category, sortBy}, which silently left an
      active 导演 filter in place: the drawer cleared its chip, the grid stayed filtered,
      and nothing on screen said why. The chip row above now makes that visible either
      way, but the two resets should still mean the same thing.
    -->
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
      @reset="resetMovieFilters"
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

    <!-- PSN Fluid Glass Trophy Unlock Toast Notification -->
    <TrophyToast />

    <!-- First launch / Folder permission explanation modal -->
    <PermissionExplainModal
      :show="showPermissionModal"
      :scanning="dbScanning"
      :creating="dbSwitching"
      @close="showPermissionModal = false"
      @pick-file="handlePickDbFile"
      @scan-folders="handleScanDatabases"
      @create-database="handleCreateNewDatabase"
    />
  </div>
</template>
