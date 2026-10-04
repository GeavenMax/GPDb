<script setup lang="ts">
import { ref, reactive, shallowReactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
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
import EnvironmentCheckModal from './components/EnvironmentCheckModal.vue';
import ImageLightbox from './components/ImageLightbox.vue';
import FilterDrawer from './components/FilterDrawer.vue';
import ActiveFilterBar from './components/ActiveFilterBar.vue';
import AppLockOverlay from './components/AppLockOverlay.vue';
import AppUpdateModal from './components/AppUpdateModal.vue';
import { checkForAppUpdate, checkAppUpdateDetailed, type AppReleaseInfo } from './services/appUpdater';
import { defineAsyncComponent } from 'vue';
const SyncModal = defineAsyncComponent(() => import('./components/plugins/SyncModal.vue'));
import PaginationBar from './components/PaginationBar.vue';
import {
  api, createMovieFilters, createPerformerFilters, countActivePerformerFilters,
  createEpisodeFilters, countActiveEpisodeFilters, EPISODE_SORTS, IS_TAURI,
} from './api';
import { getImageUrl } from './utils/image';
import { sampleImageEdgeColor, type SampledColorResult } from './utils/colorSampler';
import { openLightbox, viewableImageFrom, zoomsOnClick, lightboxImage } from './utils/lightbox';
import { initTheme, setTheme, themeChoice, autoThemeOption, concreteThemeOptions } from './utils/theme';
import AppIcon from './components/AppIcon.vue';
import { ICON_SCHEMES, currentIconScheme, setIconScheme, initAppIcon } from './utils/appIcon';
import { PREFS, type ContentLangMode } from './utils/prefs';
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
import { titlePrimary, titleSecondary, sceneFilm, resolveContentLang, studioPrimary, studioSecondary } from './utils/bilingual';
import {
  Film, Heart, HardDrive, Download, Upload, Trash2, Image as ImageIcon, RefreshCw, Loader2,
  Languages, User as UserIcon, Sparkles, Clapperboard, Building2, Layers, Palette, Check,
  Megaphone, FolderOpen, Search, Globe, Shield, EyeOff, Lock,
  Bookmark, ChevronDown, ChevronRight, ChevronsUpDown, Info,
  SlidersHorizontal, Monitor
} from '@lucide/vue';
import HomeView from './views/HomeView.vue';
import { t, currentLocale, setLocale, SUPPORTED_LANGUAGES, type SupportedLocale } from './i18n';
import AnalyticsView from './views/AnalyticsView.vue';
import PluginsView from './views/PluginsView.vue';
import { pluginsConfig, openUrlExternal } from './services/pluginManager';
import {
  privacySettings, savePrivacySettings, isAppLocked,
  isWindowBlurred, initPrivacyListeners
} from './services/privacy';
import { DATE_FILTER_OPTIONS } from './types';
import type { DateFilter } from './types';

function getDateFilterLabel(id: string): string {
  if (id === 'all') return t('common.all');
  if (id === 'last_scraped') return t('filter.lastScraped');
  if (id === 'recent_7') return t('filter.recent7');
  if (id === 'recent_30') return t('filter.recent30');
  if (id === 'recent_90') return t('filter.recent90');
  if (id === 'recent_year') return t('filter.recentYear');
  return id;
}
import {
  analytics, importAnalyticsData,
  startFocusTracker, recordMovieView, recordPerformerView,
  recordEpisodeView, recordDirectorView, recordStudioView,
  clearSearchHistory, clearBrowseHistory, resetAllAnalytics,
  recordFavoriteToggle, recordRating, recordSettingsVisit,
  recordPluginVisit, recordGridAdjust
} from './services/analytics';
import { initScraperService, onScraperDataChange } from './services/scraper';
import { initAutoSyncSchedule } from './services/autoSync';

type SettingsSubTab = 'all' | 'appearance' | 'localization' | 'data' | 'privacy' | 'about';
const settingsSubTab = ref<SettingsSubTab>('all');

const appReleaseInfo = ref<AppReleaseInfo | null>(null);
const isCheckingUpdate = ref(false);
const manualUpdateCheckMsg = ref<{ text: string; isError: boolean } | null>(null);

async function handleManualCheckUpdate() {
  if (isCheckingUpdate.value) return;
  isCheckingUpdate.value = true;
  manualUpdateCheckMsg.value = null;
  try {
    const res = await checkAppUpdateDetailed();
    if (res.hasUpdate && res.release) {
      appReleaseInfo.value = res.release;
      manualUpdateCheckMsg.value = { text: t('update.newVersionMsg', { version: res.release.versionName }), isError: false };
    } else if (res.error) {
      manualUpdateCheckMsg.value = { text: t('update.checkFailed', { error: res.error }), isError: true };
    } else {
      manualUpdateCheckMsg.value = { text: t('update.alreadyLatest', { version: res.currentVersion }), isError: false };
    }
  } catch (e: any) {
    manualUpdateCheckMsg.value = { text: t('update.checkException', { error: e?.message || 'Network Timeout' }), isError: true };
  } finally {
    isCheckingUpdate.value = false;
  }
}

const pinEditInput = ref(privacySettings.value.pinCode || '');

function handleUpdatePin() {
  savePrivacySettings({ pinCode: pinEditInput.value.trim() });
}

/** Labels for the five favorites sections and the type pickers. */
const FAVORITE_LABELS = computed<Record<FavoriteType, string>>(() => ({
  movie: t('common.movie'),
  performer: t('common.performer'),
  studio: t('common.studio'),
  director: t('common.director'),
  episode: t('common.episode'),
  series: t('common.series'),
}));

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
const STUDIO_SORTS = computed<{ id: StudioSortBy; label: string }[]>(() => [
  { id: 'works_desc', label: t('sort.byWorks') },
  { id: 'episodes_desc', label: t('sort.byEpisodes') },
  { id: 'name_asc', label: t('sort.byName') },
]);

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
const DIRECTOR_SORTS = computed<{ id: DirectorSortBy; label: string }[]>(() => [
  { id: 'works_desc', label: t('sort.byWorks') },
  { id: 'name_asc', label: t('sort.byName') },
]);

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
const selectedStudio = ref<{ name: string; name_zh?: string | null; description_zh?: string | null; logo_url?: string | null; banner_url?: string | null; website_url?: string | null; works_count?: number; episodes_count?: number } | null>(null);
const studioWorks = ref<StudioWorks | null>(null);
const studioWorksLoading = ref(false);

// Plain in-memory cache for studio logo sampled colors (non-reactive to avoid layout thrashing during scroll)
const studioLogoColorMap = new Map<string, SampledColorResult>();
const studioLogoErrorSet = shallowReactive(new Set<string>());

function onStudioGridLogoLoad(e: Event, logoUrl?: string | null) {
  if (!logoUrl) return;
  const img = e.target as HTMLImageElement;
  if (!img) return;
  const result = sampleImageEdgeColor(img);
  if (result.bgColor) {
    studioLogoColorMap.set(logoUrl, result);
    // Directly apply background and border styling to the shelf element without triggering Vue reactive re-renders
    const shelf = img.parentElement as HTMLElement | null;
    if (shelf) {
      shelf.style.backgroundColor = result.bgColor;
      shelf.style.borderColor = result.isDark ? 'rgba(255, 255, 255, 0.16)' : 'rgba(0, 0, 0, 0.12)';
    }
  }
}

function onStudioGridLogoError(logoUrl?: string | null) {
  if (logoUrl) {
    studioLogoErrorSet.add(logoUrl);
  }
}

function getStudioShelfStyle(logoUrl?: string | null): Record<string, string> {
  if (!logoUrl) return {};
  const sampled = studioLogoColorMap.get(logoUrl);
  if (sampled?.bgColor) {
    return {
      backgroundColor: sampled.bgColor,
      borderColor: sampled.isDark ? 'rgba(255, 255, 255, 0.16)' : 'rgba(0, 0, 0, 0.12)',
    };
  }
  return {};
}

function getStudioShelfClass(logoUrl?: string | null): string {
  if (logoUrl && studioLogoColorMap.get(logoUrl)?.bgColor) {
    return '';
  }
  return 'bg-surface-2/60 group-hover:bg-surface-2/80';
}

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

function setDateFilter(df: DateFilter) {
  filters.dateFilter = df;
  fetchMovies(true, 1);
}

function setEpisodeDateFilter(df: DateFilter) {
  episodeFilters.dateFilter = df;
  fetchEpisodes(true, 1);
}

const contentLangMode = ref<ContentLangMode>(
  (localStorage.getItem(PREFS.contentLangMode) as ContentLangMode) || 'auto'
);

// Synopsis & database content language preference.
// In 'auto' mode, automatically syncs with currentLocale; otherwise respects user manual override.
const descLang = ref<'zh' | 'en'>(
  contentLangMode.value === 'auto'
    ? resolveContentLang('auto', currentLocale.value)
    : ((localStorage.getItem(PREFS.descLang) as 'zh' | 'en') || 'zh')
);

function setDescLang(lang: 'zh' | 'en') {
  descLang.value = lang;
  localStorage.setItem(PREFS.descLang, lang);
  // Explicitly choosing Chinese or Original in quick toggle sets concrete mode
  contentLangMode.value = lang === 'zh' ? 'bilingual' : 'original';
  localStorage.setItem(PREFS.contentLangMode, contentLangMode.value);
}

function setContentLangMode(mode: ContentLangMode) {
  contentLangMode.value = mode;
  localStorage.setItem(PREFS.contentLangMode, mode);
  descLang.value = resolveContentLang(mode, currentLocale.value);
  localStorage.setItem(PREFS.descLang, descLang.value);
}

function onSelectLocale(locale: SupportedLocale) {
  setLocale(locale);
  if (contentLangMode.value === 'auto') {
    descLang.value = resolveContentLang('auto', locale);
    localStorage.setItem(PREFS.descLang, descLang.value);
  }
}

const isWindowsPlatform = typeof navigator !== 'undefined' && navigator.userAgent.includes('Windows');
const windowMaterial = ref<string>(localStorage.getItem(PREFS.windowMaterial) || (isWindowsPlatform ? 'mica' : 'default'));
const closeToTray = ref<boolean>(localStorage.getItem(PREFS.closeToTray) === 'true');

function setWindowMaterialChoice(material: string) {
  windowMaterial.value = material;
  localStorage.setItem(PREFS.windowMaterial, material);
  if (isWindowsPlatform) {
    document.documentElement.setAttribute('data-window-material', material);
    api.setWindowMaterial(material);
  } else {
    document.documentElement.removeAttribute('data-window-material');
  }
}

function setCloseToTrayChoice(enabled: boolean) {
  closeToTray.value = enabled;
  localStorage.setItem(PREFS.closeToTray, enabled ? 'true' : 'false');
  api.setCloseToTray(enabled);
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
// Studio library uses its own column count (defaults to 4 per user preference)
const studioCols = ref<number>(Number(localStorage.getItem(PREFS.studioCols)) || 4);

function decreaseCols() {
  if (currentTab.value === 'studios') {
    if (studioCols.value > 2) {
      studioCols.value--;
      localStorage.setItem(PREFS.studioCols, String(studioCols.value));
      recordGridAdjust();
    }
    return;
  }
  if (viewMode.value === 'list') {
    if (listCols.value > 2) {
      listCols.value--;
      localStorage.setItem(PREFS.listCols, String(listCols.value));
      recordGridAdjust();
    }
    return;
  }
  if (gridCols.value > 2) {
    gridCols.value--;
    localStorage.setItem(PREFS.gridCols, String(gridCols.value));
    recordGridAdjust();
  }
}

function increaseCols() {
  if (currentTab.value === 'studios') {
    if (studioCols.value < 8) {
      studioCols.value++;
      localStorage.setItem(PREFS.studioCols, String(studioCols.value));
      recordGridAdjust();
    }
    return;
  }
  if (viewMode.value === 'list') {
    if (listCols.value < 4) {
      listCols.value++;
      localStorage.setItem(PREFS.listCols, String(listCols.value));
      recordGridAdjust();
    }
    return;
  }
  if (gridCols.value < 8) {
    gridCols.value++;
    localStorage.setItem(PREFS.gridCols, String(gridCols.value));
    recordGridAdjust();
  }
}

/** Column count driving whichever view is active. */
const activeCols = computed(() => {
  if (currentTab.value === 'studios') return studioCols.value;
  return viewMode.value === 'list' ? listCols.value : gridCols.value;
});
const activeColsMax = computed(() => {
  if (currentTab.value === 'studios') return 8;
  return viewMode.value === 'list' ? 4 : 8;
});

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
const showEnvironmentModal = ref(false);

async function checkInitialEnvironment() {
  if (!IS_TAURI) return;
  try {
    const info = await api.checkRuntimeEnvironment();
    // Prompt if critical environment components are not ready and user hasn't dismissed the alert
    if (!info.all_ready && !localStorage.getItem('gpdb_env_check_dismissed')) {
      showEnvironmentModal.value = true;
    }
  } catch (e) {
    console.warn('Initial environment check error:', e);
  }
}

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
    dbMessage.value = { ok: true, text: t('settings.dbConnectedSuccess', { path: updated.path || 'default' }) };
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
    dbMessage.value = { ok: true, text: t('settings.dbCreatedSuccess', { path: updated.path || 'default' }) };
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
      dbMessage.value = { ok: false, text: t('settings.dbAutoDetectNone') };
    } else {
      dbMessage.value = { ok: true, text: t('settings.dbScanFinished', { count: cands.length }) };
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
      dbMessage.value = { ok: false, text: t('settings.dbNoAutoCandidate') };
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
      glossaryMsg.value = t('glossary.dryRunMsg', { count: res.pending });
    } else {
      glossaryMsg.value = t('glossary.updatedMsg', { added: res.translated, total: res.total }) +
        (res.failed ? t('glossary.failedCount', { count: res.failed }) : '');
      // Refresh the local lookup table so the new labels appear without a reload.
      await loadGlossary(true);
    }
  } else {
    glossaryError.value = res.error || t('glossary.translateFailed');
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
  if (!confirm(t('settings.clearCacheConfirm'))) return;
  isCacheLoading.value = true;
  await api.clearCache();
  await loadCacheStats();
  isCacheLoading.value = false;
  cacheStatusMsg.value = t('settings.cacheCleared');
  setTimeout(() => { cacheStatusMsg.value = ''; }, 3000);
}

async function handleBatchDownloadCache() {
  isCacheLoading.value = true;
  const res = await api.downloadAllCache();
  cacheStatusMsg.value = res.message || t('settings.cacheDownloadStarted');
  isCacheLoading.value = false;
  setTimeout(loadCacheStats, 4000);
}

async function handleExportUserData() {
  try {
    // 1. Fetch raw SQLite database user data (favorites, tags, movie_user_data)
    const dbUserData = await api.exportUserData();

    // 2. Build Universal Cross-Platform Backup JSON Bundle
    const universalBackup = {
      format: 'gpdb_universal_backup',
      version: 2,
      app_version: '2.15.0',
      exported_at: new Date().toISOString(),
      platform: typeof navigator !== 'undefined' && navigator.userAgent.includes('Windows') ? 'windows' : 'macos',
      favorites: dbUserData?.favorites || [],
      tags: dbUserData?.tags || [],
      movie_user_data: dbUserData?.movie_user_data || [],
      analytics: analytics.value,
      settings: {
        descLang: descLang.value,
        pageSize: pageSize.value,
        gridCols: gridCols.value,
        listCols: listCols.value,
        studioCols: studioCols.value,
        listMode: listMode.value,
        translateMode: translateMode.value
      }
    };

    const jsonStr = JSON.stringify(universalBackup, null, 2);
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const d = new Date();
    const dateStr = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}_${String(d.getHours()).padStart(2, '0')}${String(d.getMinutes()).padStart(2, '0')}`;
    a.href = url;
    a.download = `GPDb_Backup_${dateStr}.json`;
    a.click();
    URL.revokeObjectURL(url);

    const favCount = (universalBackup.favorites || []).length;
    importStatusMsg.value = t('settings.userJsonExportSuccess', { favCount });
    setTimeout(() => { importStatusMsg.value = ''; }, 6000);
  } catch (err: any) {
    alert(t('settings.userJsonExportFailed', { error: err?.message || err }));
  }
}

const dbBackupStatusMsg = ref('');
const isDbExporting = ref(false);

async function handleExportDatabase() {
  if (isDbExporting.value) return;
  isDbExporting.value = true;
  dbBackupStatusMsg.value = t('settings.dbExportPackaging');
  try {
    const dest = await api.exportDatabaseFile();
    if (dest) {
      dbBackupStatusMsg.value = t('settings.dbExportSuccess', { dest });
      setTimeout(() => { dbBackupStatusMsg.value = ''; }, 6000);
    } else {
      dbBackupStatusMsg.value = '';
    }
  } catch (err: any) {
    dbBackupStatusMsg.value = t('settings.dbExportFailed', { error: err?.message || err });
  } finally {
    isDbExporting.value = false;
  }
}

async function handleImportDatabase() {
  try {
    const picked = await api.pickDatabaseFile();
    if (picked) {
      if (confirm(t('settings.dbSwitchConfirm', { path: picked }))) {
        await api.setCustomDatabasePath(picked);
        await loadDatabaseInfo();
        await loadStats();
        await fetchMovies();
        alert(t('settings.dbSwitchSuccess'));
      }
    }
  } catch (err: any) {
    alert(t('settings.dbSwitchFailed', { error: err?.message || err }));
  }
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

      // 1. Restore/merge analytics data if present
      if (json.analytics) {
        importAnalyticsData(json.analytics);
      }


      // 3. Restore SQLite database user tables (favorites, tags, movie_user_data)
      const res = await api.importUserData(json);

      const favCount = res?.favorites_imported ?? (Array.isArray(json.favorites) ? json.favorites.length : 0);
      const tagCount = res?.tags_imported ?? 0;
      const movieCount = res?.movies_updated ?? 0;

      importStatusMsg.value = t('settings.userJsonImportSuccess', { favCount, tagCount, movieCount });
      setTimeout(() => { importStatusMsg.value = ''; }, 6000);

      // 4. Reload local keys and views
      loadFavoriteKeys();
      if (currentTab.value === 'favorites') loadFavorites();
      fetchMovies();
      loadStats();
    } catch (err: any) {
      alert(t('settings.userJsonImportFailed', { error: err?.message || err }));
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

/** A single-episode synopsis translation finished; fold it into lists and active modal. */
function onEpisodeTranslated(episodeId: number, zh: string) {
  const ep = episodeList.value.find(x => x.id === episodeId);
  if (ep) ep.description_zh = zh;
  if (selectedEpisode.value?.id === episodeId) selectedEpisode.value.description_zh = zh;
  if (selectedMovie.value?.episodes) {
    const mep = selectedMovie.value.episodes.find(x => x.id === episodeId);
    if (mep) mep.description_zh = zh;
  }
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
    recordSettingsVisit();
  } else if (newTab === 'plugins') {
    recordPluginVisit();
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
  return FAVORITE_TYPES.reduce((sum, t) => sum + (counts[t] || 0), 0);
});

type FavoriteSubTab = 'all' | 'series' | 'movie' | 'performer' | 'studio' | 'director' | 'episode';
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
  const activeSectionKeys = ['series', 'movie', 'performer', 'episode', 'studio', 'director'];
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
async function openStudioDetail(studio: { name: string; name_zh?: string | null; description_zh?: string | null; logo_url?: string | null; banner_url?: string | null; website_url?: string | null; works_count?: number; episodes_count?: number }) {
  pushModal('studio');
  recordStudioView(studio.name);
  selectedStudio.value = studio;
  studioWorks.value = null;
  studioWorksLoading.value = true;
  try {
    const works = await api.getStudioWorks(studio.name);
    // A slower fetch for studio A must not land on top of studio B's page.
    if (selectedStudio.value?.name === studio.name) {
      studioWorks.value = works;
      if (works.studio_name_zh) {
        selectedStudio.value.name_zh = works.studio_name_zh;
      }
      if (works.description_zh) {
        selectedStudio.value.description_zh = works.description_zh;
      }
      if (works.logo_url) {
        selectedStudio.value.logo_url = works.logo_url;
      }
      if (works.banner_url) {
        selectedStudio.value.banner_url = works.banner_url;
      }
      if (works.website_url) {
        selectedStudio.value.website_url = works.website_url;
      }
    }
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

function onGlobalEscapeGuard(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    const hasActiveLayer = modalStack.value.length > 0
      || showAppSeriesModal.value
      || isFilterOpen.value
      || isSyncOpen.value
      || Boolean(lightboxImage.value);
    if (hasActiveLayer) {
      e.preventDefault();
    }
  }
}

onMounted(async () => {
  startFocusTracker();
  initAppIcon();
  initPrivacyListeners();
  initTheme();

  // 激活保存的 Windows 11 Mica / Acrylic 材质效果与系统托盘驻留偏好
  if (isWindowsPlatform) {
    document.documentElement.setAttribute('data-window-material', windowMaterial.value);
    api.setWindowMaterial(windowMaterial.value);
  } else {
    document.documentElement.removeAttribute('data-window-material');
  }
  api.setCloseToTray(closeToTray.value);

  // Hearts come from the database, not localStorage — so they survive a browser
  // change and travel with an export. Both loads are fire-and-forget: a failure
  // leaves the UI usable, just without hearts or Chinese attribute labels.
  loadFavoriteKeys();
  loadGlossary();

  window.addEventListener('click', onGlobalClick, true);
  window.addEventListener('dblclick', onGlobalDblClick);
  // 确保在全屏显示时，若有弹窗/抽屉/层级界面展开，按 Esc 键捕获期阻止默认退出全屏，优先退回上层界面
  window.addEventListener('keydown', onGlobalEscapeGuard, true);

  loadStats();
  await fetchMovies(true);
  loadPerformerFacets();
  nextTick(() => fillViewport());

  // Initialize background scraper event listener and live auto-refresh
  initScraperService();
  onScraperDataChange(async () => {
    loadStats();
    if (currentTab.value === 'home' || currentTab.value === 'movies') {
      reloadCurrentTab();
    }
  });

  // Initialize auto-sync background schedule
  initAutoSyncSchedule();

  // Perform initial runtime environment health check
  checkInitialEnvironment();

  // 监听系统托盘与任务栏快捷导航跳转事件
  if (IS_TAURI) {
    import('@tauri-apps/api/event').then(({ listen }) => {
      listen<string>('navigate-tab', (e) => {
        const target = e.payload as any;
        if (['home', 'movies', 'performers', 'studios', 'directors', 'episodes', 'favorites', 'settings'].includes(target)) {
          currentTab.value = target;
        }
      });
    }).catch(() => {});
  }

  // Check for GitHub Release update after 3 seconds
  setTimeout(async () => {
    try {
      const info = await checkForAppUpdate();
      if (info) {
        appReleaseInfo.value = info;
      }
    } catch (e) {
      console.warn('Update check failed:', e);
    }
  }, 3000);
});

onUnmounted(() => {
  window.removeEventListener('click', onGlobalClick, true);
  window.removeEventListener('dblclick', onGlobalDblClick);
  window.removeEventListener('keydown', onGlobalEscapeGuard, true);
});
</script>

<template>
  <!--
    h-screen, not min-h-screen: with a minimum the shell grows to fit the grid
    and `<main>`'s overflow-y-auto never engages, so the *document* ends up
    scrolling. Everything bound to `<main>` — the scroll-based loader in
    particular — then sits on an element that never scrolls.
  -->
  <div
    class="wallpaper h-screen overflow-hidden bg-app text-fg flex flex-col antialiased"
    :class="{
      'privacy-blur-images': privacySettings.screenshotPrivacyEnabled && privacySettings.blurImages,
      'privacy-blur-text': privacySettings.screenshotPrivacyEnabled && privacySettings.blurDescriptions
    }"
  >
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
          <span>{{ dbScanning ? t('settings.identifying') : t('settings.identifyDb') }}</span>
        </button>
        <button
          @click="showPermissionModal = true"
          class="px-3 py-1.5 rounded-lg bg-surface border border-line-strong hover:bg-surface-2 text-fg-2 text-xs font-medium transition flex items-center gap-1.5 cursor-pointer"
        >
          <Shield class="w-3.5 h-3.5 text-indigo-400" />
          <span>{{ t('settings.storageTitle') }}</span>
        </button>
        <button
          @click="handlePickDbFile"
          :disabled="dbSwitching"
          class="px-3 py-1.5 rounded-lg bg-surface border border-line-strong hover:bg-surface-2 text-fg-2 text-xs font-medium transition flex items-center gap-1.5"
        >
          <FolderOpen class="w-3.5 h-3.5" />
          <span>{{ t('settings.browseSelectFile') }}</span>
        </button>
        <button
          @click="currentTab = 'settings'"
          class="px-3 py-1.5 rounded-lg bg-surface border border-line-strong hover:bg-surface-2 text-fg-2 text-xs font-medium transition"
        >
          {{ t('settings.goToSettings') }}
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
              <h1 class="text-xl font-bold text-fg tracking-tight">{{ t('library.exploreMovies') }}</h1>
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
                  v-for="l in [{ id: 'zh', label: t('library.langZh') }, { id: 'en', label: t('library.langEn') }]"
                  :key="l.id"
                  @click="setDescLang(l.id as 'zh' | 'en')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition cursor-pointer',
                    descLang === l.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="l.id === 'zh' ? t('library.langZhTooltip') : t('library.langEnTooltip')"
                >
                  {{ l.label }}
                </button>

                <!-- Info icon with tooltip -->
                <div
                  class="flex items-center pr-1.5 text-fg-5 hover:text-accent cursor-help transition"
                  :title="t('library.synopsisLangNotice')"
                >
                  <Info class="w-3.5 h-3.5" />
                </div>
              </div>

              <!-- How the list pages in: auto-load on scroll, or explicit pages -->
              <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
                <button
                  v-for="m in [
                    { id: 'scroll', label: t('common.scrollLoad') },
                    { id: 'paged', label: t('common.pagination') }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? t('common.scrollLoadTooltip') : t('common.paginationTooltip')"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid / list columns adjuster (both modes) -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">{{ t('common.perRow') }}</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.decreaseColumns')"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.increaseColumns')"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">{{ t('common.columns') }}</span>
              </div>
            </div>
          </div>

          <!-- Date Filter Capsule Bar (Aligned with Android 2.10) -->
          <div class="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar flex-wrap">
            <span class="text-xs text-fg-4 font-medium shrink-0 flex items-center gap-1.5 mr-1">
              <Clock class="w-3.5 h-3.5 text-accent" />
              <span>{{ t('filter.timeFilter') }}:</span>
            </span>
            <button
              v-for="df in DATE_FILTER_OPTIONS"
              :key="df.id"
              @click="setDateFilter(df.id)"
              :class="[
                'px-3 py-1 rounded-full text-xs font-medium transition cursor-pointer shrink-0 border',
                (filters.dateFilter || 'all') === df.id
                  ? 'bg-accent-fill text-on-fill font-bold border-accent shadow-sm'
                  : 'bg-surface/80 hover:bg-surface border-line text-fg-3 hover:text-fg'
              ]"
            >
              {{ getDateFilterLabel(df.id) }}
            </button>
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
              <span>{{ t('library.loadingMoreMovies') }}</span>
            </div>
            <div v-else-if="movies.length >= totalMovies && totalMovies > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>{{ t('library.loadedAllMovies', { count: totalMovies.toLocaleString() }) }}</span>
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
            <div class="text-sm font-semibold text-fg-3">{{ t('filter.noMatchingMovies') }}</div>
            <div class="text-xs text-fg-5">{{ t('filter.tryDifferentKeywords') }}</div>
          </div>
        </div>

        <!-- 2. Performers Tab -->
        <div v-else-if="currentTab === 'performers'" class="space-y-6">
          <div class="flex items-center justify-between flex-wrap gap-3">
            <div class="flex items-center gap-2">
              <h1 class="text-xl font-bold text-fg tracking-tight">{{ t('library.performersArchive') }}</h1>
              <span class="text-xs text-fg-4 font-mono">{{ t('library.performersCount', { current: performers.length, total: totalPerformers.toLocaleString() }) }}</span>
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
                :title="t('filter.attributeFilter')"
              >
                <Sparkles class="w-3.5 h-3.5" />
                <span>{{ t('filter.attributeFilter') }}</span>
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
                    { id: 'scroll', label: t('common.scrollLoad') },
                    { id: 'paged', label: t('common.pagination') }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? t('common.scrollLoadTooltip') : t('common.paginationTooltip')"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid columns adjuster -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">{{ t('common.perRow') }}</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.decreaseColumns')"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.increaseColumns')"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">{{ t('common.columns') }}</span>
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
              <div class="relative w-24 h-24 sm:w-28 sm:h-28 rounded-2xl overflow-hidden shrink-0 shadow ring-1 ring-line-strong/60 group-hover:ring-accent-fill/50 transition bg-surface-2 flex items-center justify-center">
                <template v-if="p.image_url">
                  <!-- Blurred color-fill backdrop to fill blank areas seamlessly -->
                  <div
                    class="absolute inset-0 bg-cover bg-center blur-md opacity-70 scale-125 pointer-events-none transform-gpu"
                    :style="{ backgroundImage: `url(${getImageUrl(p.image_url)})` }"
                  ></div>
                  <div class="absolute inset-0 bg-black/15 pointer-events-none"></div>
                  <!-- Foreground sharp uncropped portrait -->
                  <img
                    :src="getImageUrl(p.image_url)"
                    :alt="p.name"
                    loading="lazy"
                    referrerpolicy="no-referrer"
                    class="relative z-10 w-full h-full object-contain filter drop-shadow group-hover:scale-105 transition-transform duration-300"
                    @error="p.image_url = null"
                  />
                </template>
                <div
                  v-else
                  class="w-full h-full bg-gradient-to-tr from-surface-2 to-surface-3 group-hover:from-accent-fill group-hover:to-accent-2 flex items-center justify-center font-bold text-2xl text-fg-3 group-hover:text-on-fill transition"
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
                {{ p.works_count ?? p.movies_count }} {{ t('common.works') }}
              </div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else-if="!isLoading" class="text-center py-24 space-y-3">
            <UserIcon class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">{{ t('filter.noMatchingPerformers') }}</div>
            <div class="text-xs text-fg-5">
              {{ t('library.performerFacetsEnriched', { count: performerFacets.enriched }) }}
            </div>
          </div>

          <!-- Infinite-scroll footer: the list grows as the container bottom nears -->
          <div v-if="listMode === 'scroll' && performers.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>{{ t('library.loadingMorePerformers') }}</span>
            </div>
            <div v-else-if="performers.length >= totalPerformers && totalPerformers > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>{{ t('library.loadedAllPerformers', { count: totalPerformers.toLocaleString() }) }}</span>
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
              <h1 class="text-xl font-bold text-fg tracking-tight">{{ t('library.studiosLibrary') }}</h1>
              <span class="text-xs text-fg-4 font-mono">{{ t('library.studiosCount', { current: studioRows.length, total: totalStudioRows.toLocaleString() }) }}</span>
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
                    { id: 'scroll', label: t('common.scrollLoad') },
                    { id: 'paged', label: t('common.pagination') }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? t('common.scrollLoadTooltip') : t('common.paginationTooltip')"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid columns adjuster -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">{{ t('common.perRow') }}</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.decreaseColumns')"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.increaseColumns')"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">{{ t('common.columns') }}</span>
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
              <!-- Studio Logo Stand: Uniform wide adaptive display shelf with edge color sampling -->
              <div
                class="w-full h-20 rounded-2xl overflow-hidden shrink-0 shadow-sm border border-line-strong/60 group-hover:border-accent-fill/50 transition-all flex items-center justify-center p-2.5 relative"
                :class="getStudioShelfClass(s.logo_url)"
                :style="getStudioShelfStyle(s.logo_url)"
              >
                <img
                  v-if="s.logo_url && !studioLogoErrorSet.has(s.logo_url)"
                  :src="getImageUrl(s.logo_url)"
                  :alt="s.name"
                  crossorigin="anonymous"
                  class="max-w-[88%] max-h-full w-auto h-auto object-contain group-hover:scale-105 transition duration-300"
                  loading="lazy"
                  @load="onStudioGridLogoLoad($event, s.logo_url)"
                  @error="onStudioGridLogoError(s.logo_url)"
                />
                <div
                  v-else
                  class="w-12 h-12 rounded-xl bg-gradient-to-tr from-accent-deep/90 to-accent-2/90 flex items-center justify-center text-lg font-black text-on-fill/90 shadow-md group-hover:scale-105 transition"
                >
                  {{ (studioPrimary(s, descLang) || s.name).charAt(0).toUpperCase() }}
                </div>
              </div>
              <h3 class="text-xs font-semibold text-fg-2 mt-3 group-hover:text-accent transition truncate w-full" :title="studioPrimary(s, descLang)">
                {{ studioPrimary(s, descLang) }}
              </h3>
              <div v-if="studioSecondary(s, descLang)" class="text-[10px] text-fg-4/80 truncate w-full -mt-0.5" :title="studioSecondary(s, descLang)">
                {{ studioSecondary(s, descLang) }}
              </div>
              <div class="text-[10px] text-fg-4 mt-1.5">{{ s.works_count }} {{ t('common.works') }}</div>
              <div v-if="s.episodes_count" class="text-[10px] text-fg-5 mt-0.5">
                {{ s.episodes_count }} {{ t('common.episodes') }}
              </div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else-if="!isLoading" class="text-center py-24 space-y-3">
            <Building2 class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">{{ t('filter.noMatchingStudios') }}</div>
            <div class="text-xs text-fg-5">{{ t('filter.tryDifferentKeywordsOrClear') }}</div>
          </div>

          <!-- Infinite-scroll footer: the list grows as the container bottom nears -->
          <div v-if="listMode === 'scroll' && studioRows.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>{{ t('library.loadingMoreStudios') }}</span>
            </div>
            <div v-else-if="studioRows.length >= totalStudioRows && totalStudioRows > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>{{ t('library.loadedAllStudios', { count: totalStudioRows.toLocaleString() }) }}</span>
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
              <h1 class="text-xl font-bold text-fg tracking-tight">{{ t('library.directorsLibrary') }}</h1>
              <span class="text-xs text-fg-4 font-mono">{{ t('library.directorsCount', { current: directorRows.length, total: totalDirectorRows.toLocaleString() }) }}</span>
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
                    { id: 'scroll', label: t('common.scrollLoad') },
                    { id: 'paged', label: t('common.pagination') }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? t('common.scrollLoadTooltip') : t('common.paginationTooltip')"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid columns adjuster -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">{{ t('common.perRow') }}</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.decreaseColumns')"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.increaseColumns')"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">{{ t('common.columns') }}</span>
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
              <div class="text-[10px] text-fg-4 mt-1">{{ d.works_count }} {{ t('common.works') }}</div>
              <div v-if="d.studios_count" class="text-[10px] text-fg-5 mt-0.5">
                {{ t('library.directorStudiosCount', { count: d.studios_count }) }}
              </div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else-if="!isLoading" class="text-center py-24 space-y-3">
            <Megaphone class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">{{ t('filter.noMatchingDirectors') }}</div>
            <div class="text-xs text-fg-5">{{ t('filter.tryDifferentKeywordsOrClear') }}</div>
          </div>

          <!-- Infinite-scroll footer -->
          <div v-if="listMode === 'scroll' && directorRows.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>{{ t('library.loadingMoreDirectors') }}</span>
            </div>
            <div v-else-if="directorRows.length >= totalDirectorRows && totalDirectorRows > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>{{ t('library.loadedAllDirectors', { count: totalDirectorRows.toLocaleString() }) }}</span>
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
              <h1 class="text-xl font-bold text-fg tracking-tight">{{ t('library.episodesLibrary') }}</h1>
              <span class="text-xs text-fg-4 font-mono">{{ t('library.episodesCount', { current: episodeRows.length, total: totalEpisodeRows.toLocaleString() }) }}</span>
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
                    { id: 'scroll', label: t('common.scrollLoad') },
                    { id: 'paged', label: t('common.pagination') }
                  ]"
                  :key="m.id"
                  @click="setListMode(m.id as 'scroll' | 'paged')"
                  :class="[
                    'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                    listMode === m.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
                  ]"
                  :title="m.id === 'scroll' ? t('common.scrollLoadTooltip') : t('common.paginationTooltip')"
                >
                  {{ m.label }}
                </button>
              </div>

              <!-- Grid columns adjuster -->
              <div class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs">
                <span class="text-fg-4 text-[11px]">{{ t('common.perRow') }}</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.decreaseColumns')"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.increaseColumns')"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">{{ t('common.columns') }}</span>
              </div>
            </div>
          </div>

          <!-- Episode Date Filter Capsule Bar -->
          <div class="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar flex-wrap">
            <span class="text-xs text-fg-4 font-medium shrink-0 flex items-center gap-1.5 mr-1">
              <Clock class="w-3.5 h-3.5 text-accent" />
              <span>{{ t('filter.timeFilter') }}:</span>
            </span>
            <button
              v-for="df in DATE_FILTER_OPTIONS"
              :key="df.id"
              @click="setEpisodeDateFilter(df.id)"
              :class="[
                'px-3 py-1 rounded-full text-xs font-medium transition cursor-pointer shrink-0 border',
                (episodeFilters.dateFilter || 'all') === df.id
                  ? 'bg-accent-fill text-on-fill font-bold border-accent shadow-sm'
                  : 'bg-surface/80 hover:bg-surface border-line text-fg-3 hover:text-fg'
              ]"
            >
              {{ getDateFilterLabel(df.id) }}
            </button>
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
            <div class="text-sm font-semibold text-fg-3">{{ t('filter.noMatchingEpisodes') }}</div>
            <div v-if="activeEpisodeFilterCount > 0 || episodeQuery" class="text-xs text-fg-5">
              {{ t('filter.tryDifferentKeywordsOrReset') }}
            </div>
            <!-- The episodes table starts empty until the dedicated scrape runs: the
                 film scrape only records the scenes it happens to walk past. -->
            <div v-else class="text-xs text-fg-5 max-w-md mx-auto leading-relaxed">
              {{ t('library.episodesEmptyNotice') }}
            </div>
          </div>

          <!-- Infinite-scroll footer: the list grows as the container bottom nears -->
          <div v-if="listMode === 'scroll' && episodeRows.length > 0" class="py-8 flex flex-col items-center justify-center gap-2 text-xs text-fg-4">
            <div v-if="isLoadingMore" class="flex items-center gap-2 text-accent font-medium">
              <Loader2 class="w-4 h-4 animate-spin" />
              <span>{{ t('library.loadingMoreEpisodes') }}</span>
            </div>
            <div v-else-if="episodeRows.length >= totalEpisodeRows && totalEpisodeRows > 0" class="flex items-center gap-2 text-fg-4 text-xs">
              <span class="w-12 h-px bg-surface-2"></span>
              <span>{{ t('library.loadedAllEpisodes', { count: totalEpisodeRows.toLocaleString() }) }}</span>
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
              <h1 class="text-xl font-bold text-fg tracking-tight">{{ t('nav.favorites') }}</h1>
              <span class="text-xs text-fg-4 font-mono">{{ t('common.countItems', { count: favoriteTotal }) }}</span>
            </div>

            <div class="flex items-center gap-2.5">
              <!-- Master Toggle Collapse All (only in overview mode) -->
              <button
                v-if="favSubTab === 'all' && favoriteTotal > 0"
                @click="toggleCollapseAllFavs"
                class="px-2.5 py-1 rounded-xl bg-surface border border-line hover:border-line-strong text-fg-3 hover:text-fg text-xs font-medium flex items-center gap-1.5 transition cursor-pointer"
                :title="allSectionsCollapsed ? t('favorites.expandAll') : t('favorites.collapseAll')"
              >
                <ChevronsUpDown class="w-3.5 h-3.5 text-accent" />
                <span>{{ allSectionsCollapsed ? t('favorites.expandAllBtn') : t('favorites.collapseAllBtn') }}</span>
              </button>

              <!-- Grid columns adjuster -->
              <div
                v-if="favMovies.length > 0 || favEpisodes.length > 0"
                class="flex items-center gap-1.5 bg-surface border border-line rounded-xl px-2.5 py-1 text-xs"
              >
                <span class="text-fg-4 text-[11px]">{{ t('common.perRow') }}</span>
                <button
                  @click="decreaseCols"
                  :disabled="activeCols <= 2"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.decreaseColumns')"
                >
                  &lt;
                </button>
                <span class="w-5 text-center font-mono font-bold text-accent">{{ activeCols }}</span>
                <button
                  @click="increaseCols"
                  :disabled="activeCols >= activeColsMax"
                  class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 hover:text-fg transition font-mono font-bold"
                  :title="t('common.increaseColumns')"
                >
                  &gt;
                </button>
                <span class="text-fg-4 text-[11px]">{{ t('common.columns') }}</span>
              </div>
            </div>
          </div>

          <!-- Subcategory Filter Pills -->
          <div class="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
            <button
              v-for="sub in [
                { id: 'all', label: t('favorites.allOverview'), count: favoriteTotal },
                { id: 'series', label: t('favorites.seriesCollection'), count: favSeries.length },
                { id: 'movie', label: t('favorites.favMovies'), count: favMovies.length },
                { id: 'performer', label: t('common.performer'), count: favPerformers.length },
                { id: 'studio', label: t('common.studio'), count: favStudios.length },
                { id: 'director', label: t('common.director'), count: favDirectors.length },
                { id: 'episode', label: t('common.episode'), count: favEpisodes.length },
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
                  <h2 class="text-sm font-bold text-fg group-hover:text-accent transition">{{ t('favorites.seriesCollection') }}</h2>
                  <span class="text-xs text-fg-4 font-mono px-2 py-0.5 rounded-full bg-surface-2 font-bold">{{ favSeries.length }}</span>
                </button>
                <button
                  v-if="favSubTab === 'all'"
                  @click="favSubTab = 'series'"
                  class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                >
                  <span>{{ t('favorites.onlyThisType') }}</span>
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
                      :title="t('favorites.unfavoriteSeries')"
                    >
                      <Heart class="w-3.5 h-3.5 fill-rose-400 text-rose-400" />
                    </button>

                    <!-- Series count badge -->
                    <div class="absolute bottom-2 left-2 px-2 py-0.5 rounded-lg bg-black/75 backdrop-blur-md text-[10px] font-bold text-accent border border-accent/30 font-mono">
                      {{ s.works_count ? t('favorites.seriesCompleteCount', { count: s.works_count }) : t('favorites.seriesWorks') }}
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
                  <span>{{ t('favorites.onlyThisType') }}</span>
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
                  <span>{{ t('favorites.onlyThisType') }}</span>
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
                      :title="t('favorites.unfavoritePerformer')"
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
                    <span class="text-[10px] text-fg-4 px-2 select-none">{{ t('favorites.size') }}</span>
                    <button
                      v-for="opt in [
                        { cols: 1, label: t('favorites.sizeLarge') },
                        { cols: 2, label: t('favorites.sizeMedium') },
                        { cols: 3, label: t('favorites.sizeSmall') },
                      ]"
                      :key="opt.cols"
                      @click="setFavEpisodeCols(opt.cols)"
                      :class="[
                        'px-2 py-0.5 rounded-lg text-xs font-medium transition cursor-pointer',
                        favEpisodeCols === opt.cols
                          ? 'bg-accent-fill text-on-fill shadow-xs'
                          : 'text-fg-4 hover:text-fg hover:bg-surface-3/50'
                      ]"
                      :title="t('favorites.switchGridColumns', { cols: opt.cols })"
                    >
                      {{ opt.label }}
                    </button>
                  </div>

                  <button
                    v-if="favSubTab === 'all'"
                    @click="favSubTab = 'episode'"
                    class="text-[11px] text-fg-4 hover:text-accent transition flex items-center gap-1 cursor-pointer"
                  >
                    <span>{{ t('favorites.onlyThisType') }}</span>
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
                          {{ f.title || t('favorites.standaloneEpisodeTitle', { key: f.key }) }}
                        </span>
                        <button
                          @click.stop="toggleFavoriteEntity('episode', f.key)"
                          class="shrink-0 text-danger transition hover:scale-110 p-0.5"
                          :title="t('favorites.unfavoriteEpisode')"
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
                          {{ t('favorites.fromFilm', { title: favFilmTitle(f) }) }}
                        </span>
                      </div>
                      <div v-else class="text-[10px] text-fg-5 mt-1 flex items-center gap-1 italic">
                        <span>{{ t('favorites.standaloneEpisode') }}</span>
                      </div>
                    </div>
                    <div class="flex items-center gap-2 text-[11px] text-fg-4">
                      <span v-if="f.studio_name" class="truncate max-w-[150px] font-medium" :title="f.studio_name">{{ f.studio_name }}</span>
                      <span v-if="f.has_zh" class="text-success flex items-center gap-0.5 shrink-0 text-[10px] font-bold">
                        <Languages class="w-3 h-3" />{{ currentLocale.startsWith('zh') ? '中' : 'ZH' }}
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
                  <span>{{ t('favorites.onlyThisType') }}</span>
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
                    :title="t('favorites.openStudioProfile', { name: f.key })"
                  >
                    {{ f.key }}
                  </button>
                  <span class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2 text-fg-4 font-mono">{{ f.works_count || 0 }}</span>
                  <button
                    @click.stop="toggleFavoriteEntity('studio', f.key)"
                    class="text-danger hover:text-danger-soft transition cursor-pointer"
                    :title="t('favorites.unfavoriteStudio')"
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
                  <span>{{ t('favorites.onlyThisType') }}</span>
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
                    :title="t('favorites.openDirectorProfile', { name: f.key })"
                  >
                    {{ f.key }}
                  </button>
                  <span class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2 text-fg-4 font-mono">{{ f.works_count || 0 }}</span>
                  <button
                    @click.stop="toggleFavoriteEntity('director', f.key)"
                    class="text-danger hover:text-danger-soft transition cursor-pointer"
                    :title="t('favorites.unfavoriteDirector')"
                  >
                    <Heart class="w-3 h-3" fill="currentColor" />
                  </button>
                </div>
              </div>
            </section>
          </template>

          <div v-else class="text-center py-24 space-y-3">
            <Heart class="w-12 h-12 text-fg-5 mx-auto stroke-1" />
            <div class="text-sm font-semibold text-fg-3">{{ t('common.noData') }}</div>
            <div class="text-xs text-fg-5">{{ t('favorites.emptyNotice') }}</div>
          </div>
        </div>

        <!-- 6. Settings & Cache Tab -->
        <div v-else-if="currentTab === 'settings'" class="max-w-3xl space-y-6">
          <div class="flex items-center justify-between border-b border-line pb-4 flex-wrap gap-4">
            <div>
              <h1 class="text-xl md:text-2xl font-bold text-fg tracking-tight">{{ t('settings.headerTitle') }}</h1>
              <p class="text-xs text-fg-4 mt-1">{{ t('settings.headerDesc') }}</p>
            </div>
          </div>

          <!-- Subcategory Capsule Switcher -->
          <div class="flex items-center gap-2 flex-wrap">
            <button
              v-for="st in [
                { id: 'all', labelKey: 'settings.all', icon: SlidersHorizontal },
                { id: 'appearance', labelKey: 'settings.appearance', icon: Palette },
                { id: 'localization', labelKey: 'settings.localization', icon: Globe },
                { id: 'data', labelKey: 'settings.data', icon: HardDrive },
                { id: 'privacy', labelKey: 'settings.privacy', icon: Shield },
                { id: 'about', labelKey: 'settings.about', icon: Info },
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
              <span>{{ t(st.labelKey) }}</span>
            </button>
          </div>

          <!-- Section 0: 外观. Compact layout: 跟随系统 banner + 6 themes in 3-col grid -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'appearance'"
            class="p-5 rounded-2xl bg-surface/60 border border-line space-y-3.5"
          >
            <div class="flex items-center gap-3">
              <Palette class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">{{ t('settings.themeTitle') }}</div>
                <div class="text-xs text-fg-3">{{ t('settings.themeDesc') }}</div>
              </div>
            </div>

            <!-- Follow System Standalone Button -->
            <button
              @click="setTheme('auto')"
              class="w-full text-left p-2.5 px-3 rounded-xl border transition flex items-center justify-between gap-3 cursor-pointer"
              :class="themeChoice === 'auto'
                ? 'bg-accent-fill/10 border-accent-fill/40'
                : 'bg-surface border-line-strong hover:border-line-strong'"
            >
              <div class="flex items-center gap-2.5 min-w-0">
                <span
                  class="shrink-0 w-7 h-7 rounded-lg border border-line-strong/60 overflow-hidden flex flex-col"
                  :style="{ backgroundColor: autoThemeOption.swatch[0] }"
                  aria-hidden="true"
                >
                  <span class="flex-1" :style="{ backgroundColor: autoThemeOption.swatch[1] }"></span>
                  <span class="h-1.5" :style="{ backgroundColor: autoThemeOption.swatch[2] }"></span>
                </span>
                <span class="text-xs font-bold" :class="themeChoice === 'auto' ? 'text-accent-soft' : 'text-fg-2'">
                  {{ autoThemeOption.label }}
                </span>
              </div>
              <Check v-if="themeChoice === 'auto'" class="w-4 h-4 text-accent shrink-0" />
            </button>

            <!-- 6 Concrete Themes: Balanced 3-column grid -->
            <div class="grid grid-cols-2 sm:grid-cols-3 gap-2">
              <button
                v-for="opt in concreteThemeOptions"
                :key="opt.id"
                @click="setTheme(opt.id)"
                class="text-left p-2.5 rounded-xl border transition flex items-center gap-2.5 cursor-pointer"
                :class="themeChoice === opt.id
                  ? 'bg-accent-fill/10 border-accent-fill/40'
                  : 'bg-surface border-line-strong hover:border-line-strong'"
              >
                <span
                  class="shrink-0 w-7 h-7 rounded-lg border border-line-strong/60 overflow-hidden flex flex-col"
                  :style="{ backgroundColor: opt.swatch[0] }"
                  aria-hidden="true"
                >
                  <span class="flex-1" :style="{ backgroundColor: opt.swatch[1] }"></span>
                  <span class="h-1.5" :style="{ backgroundColor: opt.swatch[2] }"></span>
                </span>
                <span class="min-w-0 flex-1 flex items-center justify-between gap-1">
                  <span
                    class="text-xs font-medium truncate"
                    :class="themeChoice === opt.id ? 'text-accent-soft font-bold' : 'text-fg-2'"
                  >{{ opt.label }}</span>
                  <Check v-if="themeChoice === opt.id" class="w-3.5 h-3.5 text-accent shrink-0" />
                </span>
              </button>
            </div>
          </div>

          <!-- Section 0.1: App Icon & Branding -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'appearance'"
            class="p-5 rounded-2xl bg-surface/60 border border-line space-y-3.5"
          >
            <div class="flex items-center gap-3">
              <Sparkles class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">{{ t('settings.appIconTitle') }}</div>
              </div>
            </div>

            <div class="grid grid-cols-2 gap-4 max-w-xs">
              <button
                v-for="scheme in ICON_SCHEMES"
                :key="scheme.id"
                type="button"
                @click="setIconScheme(scheme.id)"
                class="relative p-4 rounded-2xl border transition-all cursor-pointer flex flex-col items-center justify-center group"
                :class="currentIconScheme === scheme.id
                  ? 'bg-accent-fill/15 border-accent shadow-md shadow-accent-fill/10 ring-2 ring-accent/40'
                  : 'bg-surface border-line hover:border-line-strong hover:bg-surface-2/60'"
              >
                <div class="relative">
                  <AppIcon
                    :scheme="scheme.id"
                    :size="76"
                    class="rounded-2xl shadow-md border border-line/40 group-hover:scale-105 transition-transform"
                  />
                  <div
                    v-if="currentIconScheme === scheme.id"
                    class="absolute -bottom-1 -right-1 w-5 h-5 rounded-full bg-accent text-on-fill flex items-center justify-center shadow"
                  >
                    <Check class="w-3 h-3 stroke-[3]" />
                  </div>
                </div>
              </button>
            </div>
          </div>

          <!-- Section 0.2: 窗口背景材质 (Windows 11 Mica / Acrylic) -->
          <div
            v-if="isWindowsPlatform && (settingsSubTab === 'all' || settingsSubTab === 'appearance')"
            class="p-5 rounded-2xl bg-surface/60 border border-line space-y-3.5"
          >
            <div class="flex items-center gap-3">
              <Layers class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">{{ t('settings.windowMaterial') }}</div>
                <div class="text-xs text-fg-3">{{ t('settings.windowMaterialDesc') }}</div>
              </div>
            </div>

            <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
              <button
                v-for="mat in [
                  { id: 'default', label: t('settings.materialDefault') },
                  { id: 'mica', label: t('settings.materialMica') },
                  { id: 'tabbed', label: t('settings.materialTabbed') },
                  { id: 'acrylic', label: t('settings.materialAcrylic') }
                ]"
                :key="mat.id"
                @click="setWindowMaterialChoice(mat.id)"
                class="p-2.5 rounded-xl border text-xs font-medium flex items-center justify-between transition cursor-pointer"
                :class="windowMaterial === mat.id
                  ? 'bg-accent-fill/15 border-accent-fill/50 text-accent font-bold shadow-sm'
                  : 'bg-surface border-line hover:border-line-strong text-fg-3 hover:text-fg-2'"
              >
                <span class="truncate">{{ mat.label }}</span>
                <Check v-if="windowMaterial === mat.id" class="w-3.5 h-3.5 text-accent shrink-0 ml-1" />
              </button>
            </div>
          </div>

          <!-- Section 0.3: 系统托盘与后台驻留 (System Tray & Background) -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'appearance'"
            class="p-5 rounded-2xl bg-surface/60 border border-line flex items-center justify-between gap-4"
          >
            <div class="flex items-center gap-3 min-w-0">
              <Monitor class="w-5 h-5 text-accent shrink-0" />
              <div>
                <div class="text-sm font-bold text-fg">{{ t('settings.closeToTray') }}</div>
                <div class="text-xs text-fg-3">{{ t('settings.closeToTrayDesc') }}</div>
              </div>
            </div>
            <button
              type="button"
              @click="setCloseToTrayChoice(!closeToTray)"
              class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
              :class="closeToTray ? 'bg-accent' : 'bg-surface-3'"
              role="switch"
              :aria-checked="closeToTray"
            >
              <span
                class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                :class="closeToTray ? 'translate-x-5' : 'translate-x-0'"
              />
            </button>
          </div>

          <!-- Section 0.5: Language & Localization -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'localization'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-5"
          >
            <div class="flex items-center gap-3">
              <Globe class="w-5 h-5 text-accent" />
              <div>
                <div class="text-sm font-bold text-fg">{{ t('settings.localization') }}</div>
                <div class="text-xs text-fg-3">{{ t('settings.languageDesc') }}</div>
              </div>
            </div>

            <!-- UI Menu Language -->
            <div class="space-y-2.5">
              <div class="text-xs font-semibold text-fg-2">{{ t('settings.menuLanguage') }}</div>
              <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
                <button
                  v-for="lang in SUPPORTED_LANGUAGES"
                  :key="lang.code"
                  @click="onSelectLocale(lang.code)"
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

            <!-- Database Content Display Mode (Auto-match / Bilingual / Original) -->
            <div class="space-y-2.5 pt-3 border-t border-line/60">
              <div>
                <div class="text-xs font-semibold text-fg-2">{{ t('settings.contentModeTitle') }}</div>
                <div class="text-[11px] text-fg-4 mt-0.5">{{ t('settings.contentModeDesc') }}</div>
              </div>
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                <button
                  type="button"
                  @click="setContentLangMode('auto')"
                  class="p-3 rounded-xl border text-xs text-left transition flex flex-col justify-between cursor-pointer"
                  :class="contentLangMode === 'auto'
                    ? 'bg-accent-fill/15 border-accent-fill/50 text-accent shadow-sm'
                    : 'bg-surface border-line hover:border-line-strong text-fg-3 hover:text-fg-2'"
                >
                  <div class="flex items-center justify-between mb-1">
                    <span class="font-bold text-[11px]">{{ t('settings.contentModeAuto') }}</span>
                    <Check v-if="contentLangMode === 'auto'" class="w-3.5 h-3.5 text-accent" />
                  </div>
                  <div class="text-[10px] text-fg-4 leading-normal">{{ t('settings.contentModeAutoDesc') }}</div>
                </button>

                <button
                  type="button"
                  @click="setContentLangMode('bilingual')"
                  class="p-3 rounded-xl border text-xs text-left transition flex flex-col justify-between cursor-pointer"
                  :class="contentLangMode === 'bilingual'
                    ? 'bg-accent-fill/15 border-accent-fill/50 text-accent shadow-sm'
                    : 'bg-surface border-line hover:border-line-strong text-fg-3 hover:text-fg-2'"
                >
                  <div class="flex items-center justify-between mb-1">
                    <span class="font-bold text-[11px]">{{ t('settings.contentModeBilingual') }}</span>
                    <Check v-if="contentLangMode === 'bilingual'" class="w-3.5 h-3.5 text-accent" />
                  </div>
                  <div class="text-[10px] text-fg-4 leading-normal">{{ t('settings.contentModeBilingualDesc') }}</div>
                </button>

                <button
                  type="button"
                  @click="setContentLangMode('original')"
                  class="p-3 rounded-xl border text-xs text-left transition flex flex-col justify-between cursor-pointer"
                  :class="contentLangMode === 'original'
                    ? 'bg-accent-fill/15 border-accent-fill/50 text-accent shadow-sm'
                    : 'bg-surface border-line hover:border-line-strong text-fg-3 hover:text-fg-2'"
                >
                  <div class="flex items-center justify-between mb-1">
                    <span class="font-bold text-[11px]">{{ t('settings.contentModeOriginal') }}</span>
                    <Check v-if="contentLangMode === 'original'" class="w-3.5 h-3.5 text-accent" />
                  </div>
                  <div class="text-[10px] text-fg-4 leading-normal">{{ t('settings.contentModeOriginalDesc') }}</div>
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
                <div class="text-sm font-bold text-fg">{{ t('settings.privacyTitle') }}</div>
                <div class="text-xs text-fg-3">{{ t('settings.analyticsDesc') }}</div>
              </div>
            </div>

            <!-- Toggles -->
            <div class="space-y-3">
              <!-- Collect Analytics Toggle -->
              <div class="flex items-center justify-between p-3 rounded-xl bg-surface border border-line">
                <div>
                  <div class="text-xs font-semibold text-fg-2">{{ t('settings.analyticsToggle') }}</div>
                  <div class="text-[11px] text-fg-4">{{ t('settings.statsCollectionDesc') }}</div>
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
                  <div class="text-xs font-semibold text-fg-2">{{ t('settings.searchHistoryToggle') }}</div>
                  <div class="text-[11px] text-fg-4">{{ t('settings.searchHistoryDetail') }}</div>
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
                  <div class="text-xs font-semibold text-fg-2">{{ t('settings.browseHistoryToggle') }}</div>
                  <div class="text-[11px] text-fg-4">{{ t('settings.browseHistoryDetail') }}</div>
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
                <span>{{ t('settings.clearSearchHistory') }}</span>
              </button>

              <button
                @click="clearBrowseHistory"
                class="px-3.5 py-1.5 rounded-xl bg-surface-2 hover:bg-surface-3 border border-line-strong text-xs font-medium text-fg-3 hover:text-fg transition flex items-center gap-1.5 cursor-pointer"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>{{ t('settings.clearBrowseHistory') }}</span>
              </button>

              <button
                @click="resetAllAnalytics"
                class="px-3.5 py-1.5 rounded-xl bg-danger-fill/10 hover:bg-danger-fill/20 border border-danger-fill/30 text-xs font-semibold text-danger flex items-center gap-1.5 transition ml-auto cursor-pointer"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>{{ t('settings.clearAllStats') }}</span>
              </button>
            </div>

            <!-- Advanced Security & Anti-peeping Suite (Aligned with Android v2.10.0 / v2.11.0) -->
            <div class="pt-4 border-t border-line/60 space-y-4">
              <div class="flex items-center gap-2">
                <Lock class="w-4 h-4 text-accent" />
                <span class="text-xs font-bold text-fg">{{ t('settings.privacySuiteTitle') }}</span>
              </div>

              <!-- PIN Lock Configuration -->
              <div class="p-4 rounded-xl bg-surface border border-line space-y-3">
                <div class="flex items-center justify-between">
                  <div>
                    <div class="text-xs font-semibold text-fg-2">{{ t('settings.pinLockTitle') }}</div>
                    <div class="text-[11px] text-fg-4">{{ t('settings.pinLockDesc') }}</div>
                  </div>
                  <button
                    @click="savePrivacySettings({ pinLockEnabled: !privacySettings.pinLockEnabled })"
                    class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
                    :class="privacySettings.pinLockEnabled ? 'bg-accent-fill' : 'bg-surface-3'"
                  >
                    <span
                      class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out"
                      :class="privacySettings.pinLockEnabled ? 'translate-x-5' : 'translate-x-0'"
                    />
                  </button>
                </div>

                <div v-if="privacySettings.pinLockEnabled" class="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-line/40">
                  <div>
                    <label class="block text-[11px] font-medium text-fg-3 mb-1">{{ t('settings.setPinLabel') }}</label>
                    <div class="flex gap-2">
                      <input
                        v-model="pinEditInput"
                        type="password"
                        maxlength="6"
                        :placeholder="t('settings.pinPlaceholder')"
                        class="w-full px-3 py-1.5 rounded-lg bg-surface-2 border border-line text-xs font-mono text-fg focus:outline-none focus:border-accent"
                        @blur="handleUpdatePin"
                        @keydown.enter="handleUpdatePin"
                      />
                      <button
                        @click="handleUpdatePin"
                        class="px-3 py-1.5 rounded-lg bg-accent-fill text-on-fill text-xs font-bold shrink-0 transition hover:bg-accent cursor-pointer"
                      >{{ t('common.save') }}</button>
                    </div>
                  </div>

                  <div>
                    <label class="block text-[11px] font-medium text-fg-3 mb-1">{{ t('settings.lockTimeoutLabel') }}</label>
                    <select
                      :value="privacySettings.lockTimeoutMinutes"
                      @change="(e) => savePrivacySettings({ lockTimeoutMinutes: Number((e.target as HTMLSelectElement).value) })"
                      class="w-full px-3 py-1.5 rounded-lg bg-surface-2 border border-line text-xs text-fg focus:outline-none focus:border-accent cursor-pointer"
                    >
                      <option :value="0">{{ t('settings.lockTimeout0') }}</option>
                      <option :value="1">{{ t('settings.lockTimeout1') }}</option>
                      <option :value="5">{{ t('settings.lockTimeout5') }}</option>
                      <option :value="15">{{ t('settings.lockTimeout15') }}</option>
                      <option :value="30">{{ t('settings.lockTimeout30') }}</option>
                      <option :value="-1">{{ t('settings.lockTimeoutManual') }}</option>
                    </select>
                  </div>
                </div>
              </div>

              <!-- Window Blur Mask Toggle -->
              <div class="flex items-center justify-between p-3.5 rounded-xl bg-surface border border-line">
                <div>
                  <div class="text-xs font-semibold text-fg-2">{{ t('settings.windowBlurShieldTitle') }}</div>
                  <div class="text-[11px] text-fg-4">{{ t('settings.windowBlurShieldDesc') }}</div>
                </div>
                <button
                  @click="savePrivacySettings({ blurOnWindowBlur: !privacySettings.blurOnWindowBlur })"
                  class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
                  :class="privacySettings.blurOnWindowBlur ? 'bg-accent-fill' : 'bg-surface-3'"
                >
                  <span
                    class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out"
                    :class="privacySettings.blurOnWindowBlur ? 'translate-x-5' : 'translate-x-0'"
                  />
                </button>
              </div>

              <!-- Screenshot Sharing Privacy Mode -->
              <div class="p-4 rounded-xl bg-surface border border-line space-y-3">
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2.5">
                    <EyeOff class="w-4 h-4 text-accent" />
                    <div>
                      <div class="text-xs font-semibold text-fg-2">{{ t('settings.screenshotPrivacyTitle') }}</div>
                      <div class="text-[11px] text-fg-4">{{ t('settings.screenshotPrivacyDesc') }}</div>
                    </div>
                  </div>
                  <button
                    @click="savePrivacySettings({ screenshotPrivacyEnabled: !privacySettings.screenshotPrivacyEnabled })"
                    class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
                    :class="privacySettings.screenshotPrivacyEnabled ? 'bg-accent-fill' : 'bg-surface-3'"
                  >
                    <span
                      class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out"
                      :class="privacySettings.screenshotPrivacyEnabled ? 'translate-x-5' : 'translate-x-0'"
                    />
                  </button>
                </div>

                <!-- Granular blur controls when enabled -->
                <div v-if="privacySettings.screenshotPrivacyEnabled" class="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2.5 border-t border-line/40">
                  <label class="flex items-center gap-2.5 p-2 rounded-lg bg-surface-2/60 border border-line/50 cursor-pointer hover:bg-surface-2 transition">
                    <input
                      type="checkbox"
                      :checked="privacySettings.blurImages"
                      @change="savePrivacySettings({ blurImages: !privacySettings.blurImages })"
                      class="rounded text-accent focus:ring-accent"
                    />
                    <div>
                      <div class="text-xs font-medium text-fg">{{ t('settings.blurImages') }}</div>
                      <div class="text-[10px] text-fg-4">{{ t('settings.blurImagesDesc') }}</div>
                    </div>
                  </label>

                  <label class="flex items-center gap-2.5 p-2 rounded-lg bg-surface-2/60 border border-line/50 cursor-pointer hover:bg-surface-2 transition">
                    <input
                      type="checkbox"
                      :checked="privacySettings.blurDescriptions"
                      @change="savePrivacySettings({ blurDescriptions: !privacySettings.blurDescriptions })"
                      class="rounded text-accent focus:ring-accent"
                    />
                    <div>
                      <div class="text-xs font-medium text-fg">{{ t('settings.blurDescriptions') }}</div>
                      <div class="text-[10px] text-fg-4">{{ t('settings.blurDescriptionsDesc') }}</div>
                    </div>
                  </label>
                </div>
              </div>
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
                  <div class="text-sm font-bold text-fg">{{ t('settings.dataCenterTitle') }}</div>
                  <div class="text-xs text-fg-3">{{ t('settings.dataCenterDesc') }}</div>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <span
                  v-if="dbInfo?.valid"
                  class="px-2.5 py-1 rounded-full bg-success-fill/20 border border-success-fill/30 text-success-soft text-[11px] font-medium flex items-center gap-1.5"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-success animate-pulse"></span>
                  {{ t('settings.dbConnected', { size: dbInfo.file_size_mb }) }}
                </span>
                <span
                  v-else
                  class="px-2.5 py-1 rounded-full bg-danger-fill/20 border border-danger-fill/30 text-danger-soft text-[11px] font-medium flex items-center gap-1.5"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-danger"></span>
                  {{ t('settings.dbNotFound') }}
                </span>
              </div>
            </div>

            <!-- Database Path Configuration & Intelligent Detection -->
            <div class="p-4 rounded-xl bg-surface/80 border border-line-strong/60 space-y-3 text-xs">
              <div class="flex items-center justify-between gap-2">
                <span class="font-semibold text-fg-2">{{ t('settings.dbStoragePath') }}</span>
                <span v-if="dbInfo?.custom_path" class="text-[10px] px-1.5 py-0.5 rounded bg-accent-fill/15 text-accent-soft border border-accent-fill/25">
                  {{ t('settings.customPath') }}
                </span>
                <span v-else class="text-[10px] text-fg-4">{{ t('settings.smartDefault') }}</span>
              </div>

              <!-- Current resolved path display -->
              <div class="flex items-center gap-2 px-3 py-2 rounded-lg bg-sunken font-mono text-[11px] text-fg-3 break-all border border-line select-all">
                <span class="text-fg-4 shrink-0">{{ t('settings.currentPath') }}:</span>
                <span class="flex-1 text-fg">{{ dbInfo?.path || t('settings.noDbLinked') }}</span>
              </div>

              <!-- Custom path input and buttons -->
              <div class="flex flex-col sm:flex-row gap-2 pt-1">
                <div class="flex-1 relative">
                  <input
                    v-model="customDbInput"
                    type="text"
                    :placeholder="t('settings.dbPathPlaceholder')"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none"
                    @keydown.enter="applyCustomDbPath()"
                  />
                </div>
                <div class="flex items-center gap-2 shrink-0">
                  <button
                    @click="handlePickDbFile"
                    :disabled="dbSwitching"
                    class="px-3 py-2 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs border border-line-strong flex items-center gap-1.5 transition disabled:opacity-50"
                    :title="t('common.browseFinder')"
                  >
                    <FolderOpen class="w-3.5 h-3.5 text-accent" />
                    <span>{{ t('settings.browseBtn') }}</span>
                  </button>
                  <button
                    @click="applyCustomDbPath()"
                    :disabled="dbSwitching || !customDbInput.trim()"
                    class="px-4 py-2 rounded-lg bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs shadow-sm flex items-center gap-1.5 transition disabled:opacity-40"
                  >
                    <span>{{ dbSwitching ? t('settings.connecting') : t('settings.saveAndConnect') }}</span>
                  </button>
                  <button
                    v-if="dbInfo?.custom_path"
                    @click="resetToAutoDbPath"
                    :disabled="dbSwitching"
                    class="px-3 py-2 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg-2 text-xs border border-line-strong transition"
                    :title="t('settings.resetAutoTooltip')"
                  >
                    {{ t('settings.resetAuto') }}
                  </button>
                </div>
              </div>

              <!-- Create new blank database action for settings -->
              <div class="pt-2 border-t border-line/60 flex items-center justify-between">
                <div class="text-[11px] text-fg-4">
                  {{ t('settings.createNewDbDesc') }}
                </div>
                <button
                  @click="handleCreateNewDatabase"
                  :disabled="dbSwitching"
                  class="px-2.5 py-1.5 rounded-lg bg-gradient-to-r from-amber-500/15 via-orange-500/15 to-rose-500/15 hover:from-amber-500/25 hover:to-rose-500/25 border border-amber-500/30 text-amber-300 text-[11px] font-bold flex items-center gap-1.5 transition disabled:opacity-50 cursor-pointer shrink-0"
                >
                  <Sparkles class="w-3.5 h-3.5 text-amber-400" />
                  <span>{{ dbSwitching ? t('settings.creatingDb') : t('settings.createDbBtn') }}</span>
                </button>
              </div>

              <!-- Intelligent detection scanner -->
              <div class="pt-2 border-t border-line/60 flex flex-col gap-2">
                <div class="flex items-center justify-between">
                  <div class="text-[11px] text-fg-4">
                    {{ t('settings.scanDatabasesDesc') }}
                  </div>
                  <button
                    @click="handleScanDatabases"
                    :disabled="dbScanning"
                    class="px-2.5 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong flex items-center gap-1.5 transition disabled:opacity-50"
                  >
                    <RefreshCw class="w-3 h-3 text-accent" :class="dbScanning ? 'animate-spin' : ''" />
                    <span>{{ dbScanning ? t('settings.scanningDbs') : t('settings.scanDbsBtn') }}</span>
                  </button>
                </div>

                <!-- Detected candidates -->
                <div v-if="dbCandidates.length > 0" class="space-y-1.5 pt-1">
                  <div class="text-[10px] text-fg-4 font-semibold uppercase tracking-wider">{{ t('settings.candidateDbs') }}</div>
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
                      {{ t('settings.switchToThisDb') }}
                    </button>
                    <span v-else class="text-[10px] text-success-soft px-2 py-0.5 rounded bg-success-fill/10 border border-success-fill/20 shrink-0">
                      {{ t('settings.currentlyInUse') }}
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
                <span class="text-fg-4">{{ t('settings.totalMoviesCount') }}</span>
                <div class="text-base font-bold text-fg mt-0.5">{{ stats ? stats.movies.toLocaleString() : 0 }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">{{ t('settings.totalPerformersCount') }}</span>
                <div class="text-base font-bold text-fg mt-0.5">{{ stats ? stats.performers.toLocaleString() : 0 }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">{{ t('settings.totalEpisodesCount') }}</span>
                <div class="text-base font-bold text-accent mt-0.5">{{ stats ? stats.episodes.toLocaleString() : 0 }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">{{ t('settings.totalCreditsCount') }}</span>
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
                  <div class="text-sm font-bold text-fg">{{ t('settings.cacheSystemTitle') }}</div>
                  <div class="text-xs text-fg-3">{{ t('settings.cacheSystemDesc') }}</div>
                </div>
              </div>
              <button
                @click="loadCacheStats"
                class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 hover:text-fg transition"
                :title="t('settings.refreshCacheStats')"
              >
                <RefreshCw class="w-3.5 h-3.5" />
              </button>
            </div>

            <!-- Stats metrics -->
            <div class="grid grid-cols-2 gap-3 text-xs pt-1">
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">{{ t('settings.cachedImageCount') }}</span>
                <div class="text-base font-bold text-success mt-0.5">{{ t('settings.cachedUnits', { count: cacheStats.count.toLocaleString() }) }}</div>
              </div>
              <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
                <span class="text-fg-4">{{ t('settings.diskSpaceUsed') }}</span>
                <div class="text-base font-bold text-accent-soft mt-0.5">{{ cacheStats.size_mb }} MB</div>
              </div>
            </div>

            <!-- Cache directory location -->
            <div v-if="cacheStats.path" class="text-[11px] text-fg-4 font-mono bg-sunken p-2.5 rounded-xl border border-line truncate">
              {{ t('settings.localStorageDir') }}: {{ cacheStats.path }}
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
                <span>{{ t('settings.preDownloadImages') }}</span>
              </button>

              <button
                @click="handleClearCache"
                :disabled="isCacheLoading"
                class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-danger-fill/20 text-fg-2 hover:text-danger-soft border border-line-strong hover:border-danger-fill/30 text-xs font-medium flex items-center gap-2 transition disabled:opacity-50"
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>{{ t('settings.clearImageCache') }}</span>
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
                <div class="text-sm font-bold text-fg">{{ t('settings.aiEngineNoticeTitle') }}</div>
                <div class="text-xs text-fg-3">
                  {{ t('settings.aiEngineNoticeDesc') }}
                </div>
              </div>
            </div>
            <button
              @click="currentTab = 'plugins'"
              class="px-3.5 py-2 rounded-xl bg-accent-fill/15 hover:bg-accent-fill text-accent-soft hover:text-on-fill font-medium text-xs border border-accent-fill/30 transition shrink-0 cursor-pointer"
            >
              {{ t('settings.goToPlugins') }}
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
                <div class="text-sm font-bold text-fg">{{ t('settings.glossaryTitle') }}</div>
                <div class="text-xs text-fg-3">
                  {{ t('settings.glossaryDesc') }}
                </div>
              </div>
            </div>

            <div class="flex items-center gap-2 text-xs">
              <span class="text-fg-4">{{ t('settings.glossaryCollected') }}</span>
              <span class="font-mono font-bold text-success">{{ glossaryCount() }}</span>
              <span class="text-fg-5">{{ t('settings.glossaryItemsLoaded') }}</span>
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
                <span>{{ t('settings.glossaryDryRun') }}</span>
              </button>

              <button
                @click="handleRunGlossary(false)"
                :disabled="glossaryBusy || !translationStats?.configured"
                class="px-5 py-2.5 rounded-xl bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs shadow-lg shadow-accent-fill/20 flex items-center gap-2 transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Loader2 v-if="glossaryBusy" class="w-3.5 h-3.5 animate-spin" />
                <Sparkles v-else class="w-3.5 h-3.5" />
                <span>{{ glossaryBusy ? t('settings.glossaryTranslating') : t('settings.glossaryTranslateBtn') }}</span>
              </button>
            </div>

            <div v-else class="text-xs text-fg-3 leading-relaxed p-3 rounded-xl bg-surface-2/60 border border-line-strong">
              {{ t('settings.glossaryCliHint') }}
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
                <div class="text-sm font-bold text-fg">{{ t('settings.backupCenterTitle') }}</div>
                <div class="text-xs text-fg-3">{{ t('settings.backupCenterDesc') }}</div>
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
                    <span>{{ t('settings.fullDbPackage') }}</span>
                  </div>
                  <p class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                    {{ t('settings.fullDbPackageDesc') }}
                  </p>
                </div>

                <div class="flex items-center gap-2 flex-wrap pt-1">
                  <button
                    @click="handleExportDatabase"
                    :disabled="isDbExporting"
                    class="px-3.5 py-2 rounded-xl bg-surface-3 hover:bg-surface-3/80 text-fg font-medium text-xs border border-line flex items-center gap-1.5 transition cursor-pointer disabled:opacity-50"
                  >
                    <Download class="w-3.5 h-3.5 text-purple-400" />
                    <span>{{ isDbExporting ? t('settings.exportingDb') : t('settings.exportDbBtn') }}</span>
                  </button>

                  <button
                    @click="handleImportDatabase"
                    class="px-3.5 py-2 rounded-xl bg-surface-3 hover:bg-surface-3/80 text-fg font-medium text-xs border border-line flex items-center gap-1.5 transition cursor-pointer"
                  >
                    <Upload class="w-3.5 h-3.5 text-purple-400" />
                    <span>{{ t('settings.loadExternalDb') }}</span>
                  </button>
                </div>
              </div>

              <!-- Panel B: Universal Personal User Data (JSON) -->
              <div class="p-4 rounded-xl bg-surface-2/60 border border-line-strong space-y-3 flex flex-col justify-between">
                <div>
                  <div class="text-xs font-bold text-fg flex items-center gap-1.5">
                    <Bookmark class="w-4 h-4 text-accent" />
                    <span>{{ t('settings.userJsonBackup') }}</span>
                  </div>
                  <p class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                    {{ t('settings.userJsonBackupDesc') }}
                  </p>
                </div>

                <div class="flex items-center gap-2 flex-wrap pt-1">
                  <button
                    @click="handleExportUserData"
                    class="px-3.5 py-2 rounded-xl bg-surface-3 hover:bg-surface-3/80 text-fg font-medium text-xs border border-line flex items-center gap-1.5 transition cursor-pointer"
                  >
                    <Download class="w-3.5 h-3.5 text-accent" />
                    <span>{{ t('settings.exportUserJson') }}</span>
                  </button>

                  <button
                    @click="triggerImportFileInput"
                    class="px-3.5 py-2 rounded-xl bg-surface-3 hover:bg-surface-3/80 text-fg font-medium text-xs border border-line flex items-center gap-1.5 transition cursor-pointer"
                  >
                    <Upload class="w-3.5 h-3.5 text-accent" />
                    <span>{{ t('settings.importUserJson') }}</span>
                  </button>
                  <input ref="fileInputRef" type="file" accept=".json" class="hidden" @change="handleImportFile" />
                </div>
              </div>
            </div>
          </div>

          <!-- Section 5: Software Version & Update -->
          <div
            v-if="settingsSubTab === 'all' || settingsSubTab === 'about'"
            class="p-6 rounded-2xl bg-surface/60 border border-line space-y-4"
          >
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div class="flex items-center gap-3">
                <Info class="w-5 h-5 text-accent" />
                <div>
                  <div class="text-sm font-bold text-fg">{{ t('settings.aboutTitle') }}</div>
                  <div class="text-xs text-fg-3">{{ t('settings.currentVersionDesc', { version: '2.15.0' }) }}</div>
                </div>
              </div>
              <button
                @click="handleManualCheckUpdate"
                :disabled="isCheckingUpdate"
                class="px-4 py-2 rounded-xl bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs shadow-sm flex items-center gap-2 transition disabled:opacity-50 cursor-pointer"
              >
                <Loader2 v-if="isCheckingUpdate" class="w-3.5 h-3.5 animate-spin" />
                <RefreshCw v-else class="w-3.5 h-3.5" />
                <span>{{ isCheckingUpdate ? t('settings.checkingUpdate') : t('settings.checkUpdateBtn') }}</span>
              </button>
            </div>
            <div v-if="manualUpdateCheckMsg" class="p-3 rounded-xl border text-xs flex items-center gap-2" :class="manualUpdateCheckMsg.isError ? 'bg-danger-fill/10 border-danger-fill/20 text-danger-soft' : 'bg-success-fill/10 border-success-fill/20 text-success-soft'">
              <span>{{ manualUpdateCheckMsg.text }}</span>
            </div>
            <div class="p-3.5 rounded-xl bg-surface-2/60 border border-line text-xs flex items-center justify-between">
              <div>
                <span class="text-fg-3">{{ t('settings.openSourceRepo') }}</span>
                <span class="font-mono text-fg-2">github.com/GeavenMax/GPDb</span>
              </div>
              <a
                href="https://github.com/GeavenMax/GPDb"
                @click.prevent="openUrlExternal('https://github.com/GeavenMax/GPDb')"
                target="_blank"
                rel="noopener noreferrer"
                class="text-accent hover:underline text-xs cursor-pointer"
              >{{ t('settings.visitRepo') }}</a>
            </div>
          </div>
        </div>

        <!-- 7. Local Analytics Tab -->
        <div v-else-if="currentTab === 'analytics'" class="space-y-6">
          <AnalyticsView />
        </div>

        <div v-else-if="currentTab === 'plugins'" class="space-y-6">
          <PluginsView
            @open-sync="isSyncOpen = true"
            @open-environment-check="showEnvironmentModal = true"
            @refresh-movies="loadStats(); fetchMovies(true);"
          />
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
      @select-studio="(name) => openStudioDetail({ name })"
      @filter-studio="(name) => openStudioDetail({ name })"
      @filter-director="(name) => openDirectorDetail({ name })"
      @toggle-favorite="toggleFavorite"
      @toggle-entity-favorite="toggleFavoriteEntity"
      @user-data-changed="onUserDataChanged"
      @translated="onMovieTranslated"
      @select-episode-id="openEpisodeDetailById"
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
      @select-episode-id="openEpisodeDetailById"
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
      @episode-translated="onEpisodeTranslated"
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

    <!-- Runtime Environment Diagnosis & First Install Guidance Modal -->
    <EnvironmentCheckModal
      :show="showEnvironmentModal"
      @close="showEnvironmentModal = false"
      @open-database-setup="showPermissionModal = true; showEnvironmentModal = false"
    />

    <!-- GitHub Release In-App Update Modal -->
    <AppUpdateModal
      v-if="appReleaseInfo"
      :release="appReleaseInfo"
      @close="appReleaseInfo = null"
    />

    <!-- Security Overlays (Aligned with Android 2.10) -->
    <AppLockOverlay v-if="isAppLocked" />

    <!-- Window Blur Privacy Protection Mask -->
    <div
      v-if="isWindowBlurred && privacySettings.blurOnWindowBlur && !isAppLocked"
      class="fixed inset-0 z-[280] bg-scrim/80 backdrop-blur-2xl flex flex-col items-center justify-center select-none text-fg p-4 cursor-pointer"
      @click="isWindowBlurred = false"
    >
      <div class="p-6 rounded-3xl bg-surface/90 border border-line-strong flex flex-col items-center gap-3 shadow-2xl">
        <Shield class="w-10 h-10 text-accent animate-pulse" />
        <div class="text-sm font-bold text-fg">{{ t('settings.blurShieldActive') }}</div>
        <div class="text-xs text-fg-4">{{ t('settings.blurShieldHint') }}</div>
      </div>
    </div>
  </div>
</template>
