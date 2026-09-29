import { ref } from 'vue';
import { privacySettings } from './privacy';

export interface BrowseHistoryItem {
  type: 'movie' | 'performer' | 'director' | 'studio';
  id: string | number;
  title: string;
  time: number;
}

export interface UserAnalytics {
  totalFocusTimeSeconds: number;
  movieViewsCount: number;
  uniqueMoviesViewed: number[];
  episodeViewsCount: number;
  performerViewsCount: number;
  uniquePerformersViewed: number[];
  directorViewsCount: number;
  studioViewsCount: number;
  searchesCount: number;
  favoritesAddedCount: number;
  ratingsCount: number;
  tagsCreatedCount: number;
  translationsCount: number;
  firstLaunchTime: number;
  activeDays: string[];
  nightOwlViewsCount: number;
  searchHistory: string[];
  browseHistory: BrowseHistoryItem[];
  pluginsVisitedCount?: number;
  settingsVisitedCount?: number;
  langSwitchedCount?: number;
  gridAdjustedCount?: number;
  btUsedCount?: number;
}

const ANALYTICS_KEY = 'gpdb_user_analytics';

function getTodayString(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function loadAnalytics(): UserAnalytics {
  const defaults: UserAnalytics = {
    totalFocusTimeSeconds: 0,
    movieViewsCount: 0,
    uniqueMoviesViewed: [],
    episodeViewsCount: 0,
    performerViewsCount: 0,
    uniquePerformersViewed: [],
    directorViewsCount: 0,
    studioViewsCount: 0,
    searchesCount: 0,
    favoritesAddedCount: 0,
    ratingsCount: 0,
    tagsCreatedCount: 0,
    translationsCount: 0,
    firstLaunchTime: Date.now(),
    activeDays: [getTodayString()],
    nightOwlViewsCount: 0,
    searchHistory: [],
    browseHistory: [],
    pluginsVisitedCount: 0,
    settingsVisitedCount: 0,
    langSwitchedCount: 0,
    gridAdjustedCount: 0,
    btUsedCount: 0,
  };

  try {
    const raw = localStorage.getItem(ANALYTICS_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      return { ...defaults, ...parsed };
    }
  } catch {}
  return defaults;
}

export const analytics = ref<UserAnalytics>(loadAnalytics());

function persist() {
  localStorage.setItem(ANALYTICS_KEY, JSON.stringify(analytics.value));
}

// Daily check-in
const today = getTodayString();
if (!analytics.value.activeDays.includes(today)) {
  analytics.value.activeDays.push(today);
  persist();
}

// Window focus tracking
let isFocused = document.hasFocus();
let focusTimer: number | null = null;

function checkNightOwl() {
  const hour = new Date().getHours();
  if (hour >= 23 || hour < 5) {
    analytics.value.nightOwlViewsCount++;
  }
}

export function startFocusTracker() {
  if (typeof window === 'undefined') return;

  const onFocus = () => { isFocused = true; };
  const onBlur = () => { isFocused = false; };
  const onVisibility = () => { isFocused = document.visibilityState === 'visible'; };

  window.addEventListener('focus', onFocus);
  window.addEventListener('blur', onBlur);
  document.addEventListener('visibilitychange', onVisibility);

  if (!focusTimer) {
    focusTimer = window.setInterval(() => {
      if (isFocused && privacySettings.value.collectAnalytics) {
        analytics.value.totalFocusTimeSeconds++;
        if (analytics.value.totalFocusTimeSeconds % 15 === 0) {
          persist();
        }
      }
    }, 1000);
  }
}

// Callbacks for trophy unlock checks
type TrophyCheckCallback = (a: UserAnalytics) => void;
const trophyCheckListeners: TrophyCheckCallback[] = [];

export function onAnalyticsEvent(cb: TrophyCheckCallback) {
  trophyCheckListeners.push(cb);
}

function notifyListeners() {
  persist();
  for (const listener of trophyCheckListeners) {
    try {
      listener(analytics.value);
    } catch (e) {
      console.error('Trophy check error:', e);
    }
  }
}

export function recordMovieView(id: number, title: string) {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.movieViewsCount++;
  if (!analytics.value.uniqueMoviesViewed.includes(id)) {
    analytics.value.uniqueMoviesViewed.push(id);
  }
  checkNightOwl();

  if (privacySettings.value.keepBrowseHistory) {
    analytics.value.browseHistory = [
      { type: 'movie' as const, id, title, time: Date.now() },
      ...analytics.value.browseHistory.filter(b => !(b.type === 'movie' && b.id === id))
    ].slice(0, 100);
  }

  notifyListeners();
}

export function recordEpisodeView(id: number, title: string) {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.episodeViewsCount++;
  checkNightOwl();

  if (privacySettings.value.keepBrowseHistory) {
    analytics.value.browseHistory = [
      { type: 'movie' as const, id, title: `片段: ${title}`, time: Date.now() },
      ...analytics.value.browseHistory.filter(b => b.id !== id)
    ].slice(0, 100);
  }

  notifyListeners();
}

export function recordPerformerView(id: number, name: string) {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.performerViewsCount++;
  if (!analytics.value.uniquePerformersViewed.includes(id)) {
    analytics.value.uniquePerformersViewed.push(id);
  }

  if (privacySettings.value.keepBrowseHistory) {
    analytics.value.browseHistory = [
      { type: 'performer' as const, id, title: name, time: Date.now() },
      ...analytics.value.browseHistory.filter(b => !(b.type === 'performer' && b.id === id))
    ].slice(0, 100);
  }

  notifyListeners();
}

export function recordDirectorView(name: string) {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.directorViewsCount++;
  if (privacySettings.value.keepBrowseHistory) {
    analytics.value.browseHistory = [
      { type: 'director' as const, id: name, title: name, time: Date.now() },
      ...analytics.value.browseHistory.filter(b => !(b.type === 'director' && b.id === name))
    ].slice(0, 100);
  }
  notifyListeners();
}

export function recordStudioView(name: string) {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.studioViewsCount++;
  if (privacySettings.value.keepBrowseHistory) {
    analytics.value.browseHistory = [
      { type: 'studio' as const, id: name, title: name, time: Date.now() },
      ...analytics.value.browseHistory.filter(b => !(b.type === 'studio' && b.id === name))
    ].slice(0, 100);
  }
  notifyListeners();
}

export function recordSearch(query: string) {
  const trimmed = query.trim();
  if (!trimmed) return;
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.searchesCount++;

  if (privacySettings.value.keepSearchHistory) {
    analytics.value.searchHistory = [
      trimmed,
      ...analytics.value.searchHistory.filter(s => s !== trimmed)
    ].slice(0, 50);
  }

  notifyListeners();
}

export function removeSearchHistoryItem(query: string) {
  analytics.value.searchHistory = analytics.value.searchHistory.filter(s => s !== query);
  persist();
}

export function clearSearchHistory() {
  analytics.value.searchHistory = [];
  persist();
}

export function clearBrowseHistory() {
  analytics.value.browseHistory = [];
  persist();
}

export function recordFavoriteToggle() {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.favoritesAddedCount++;
  notifyListeners();
}

export function recordRating() {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.ratingsCount++;
  notifyListeners();
}

export function recordTagCreate() {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.tagsCreatedCount++;
  notifyListeners();
}

export function recordPluginVisit() {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.pluginsVisitedCount = (analytics.value.pluginsVisitedCount || 0) + 1;
  notifyListeners();
}

export function recordSettingsVisit() {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.settingsVisitedCount = (analytics.value.settingsVisitedCount || 0) + 1;
  notifyListeners();
}

export function recordLangSwitch() {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.langSwitchedCount = (analytics.value.langSwitchedCount || 0) + 1;
  notifyListeners();
}

export function recordGridAdjust() {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.gridAdjustedCount = (analytics.value.gridAdjustedCount || 0) + 1;
  notifyListeners();
}

export function recordBtSearch() {
  if (!privacySettings.value.collectAnalytics) return;
  analytics.value.btUsedCount = (analytics.value.btUsedCount || 0) + 1;
  notifyListeners();
}

export function resetAllAnalytics() {
  analytics.value = {
    totalFocusTimeSeconds: 0,
    movieViewsCount: 0,
    uniqueMoviesViewed: [],
    episodeViewsCount: 0,
    performerViewsCount: 0,
    uniquePerformersViewed: [],
    directorViewsCount: 0,
    studioViewsCount: 0,
    searchesCount: 0,
    favoritesAddedCount: 0,
    ratingsCount: 0,
    tagsCreatedCount: 0,
    translationsCount: 0,
    firstLaunchTime: Date.now(),
    activeDays: [getTodayString()],
    nightOwlViewsCount: 0,
    searchHistory: [],
    browseHistory: [],
    pluginsVisitedCount: 0,
    settingsVisitedCount: 0,
    langSwitchedCount: 0,
    gridAdjustedCount: 0,
    btUsedCount: 0,
  };
  persist();
}
