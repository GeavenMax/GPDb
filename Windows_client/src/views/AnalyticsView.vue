<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { analytics, resetAllAnalytics } from '../services/analytics';
import {
  deepInsights, loadDeepInsights, exportUserDataBundle, isInsightsLoading
} from '../services/userAnalytics';
import { t, currentLocale } from '../i18n';
import {
  Clock, Film, Users, Layers, Search, Bookmark,
  Star, Flame, Moon, Trash2, AlertTriangle,
  Tag, Languages,
  BarChart3, Activity, Heart, ShieldCheck, Download, PieChart,
  Building2, Calendar
} from '@lucide/vue';

const emit = defineEmits<{
  (e: 'change-tab', tab: string): void;
}>();

type AnalyticsSubTab = 'overview' | 'footprint' | 'interaction' | 'insights';
const activeSubTab = ref<AnalyticsSubTab>('overview');
const showConfirmReset = ref(false);

const availableTabs = computed(() => {
  return [
    { id: 'overview' as AnalyticsSubTab, label: t('analytics.tabOverview'), icon: BarChart3 },
    { id: 'footprint' as AnalyticsSubTab, label: t('analytics.tabFootprint'), icon: Film },
    { id: 'interaction' as AnalyticsSubTab, label: t('analytics.tabInteraction'), icon: Star },
    { id: 'insights' as AnalyticsSubTab, label: t('analytics.tabInsights'), icon: PieChart },
  ];
});

onMounted(() => {
  loadDeepInsights();
});

const formattedFocusTime = computed(() => {
  const sec = analytics.value.totalFocusTimeSeconds;
  const hours = Math.floor(sec / 3600);
  const mins = Math.floor((sec % 3600) / 60);
  if (hours > 0) {
    return currentLocale.value.startsWith('zh') ? `${hours} 小时 ${mins} 分钟` : `${hours}h ${mins}m`;
  }
  return currentLocale.value.startsWith('zh') ? `${mins} 分钟 ${sec % 60} 秒` : `${mins}m ${sec % 60}s`;
});

const firstLaunchFormatted = computed(() => {
  if (!analytics.value.firstLaunchTime) return t('common.today');
  const d = new Date(analytics.value.firstLaunchTime);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
});

function confirmReset() {
  resetAllAnalytics();
  showConfirmReset.value = false;
}

function formatHistoryTime(ts: number): string {
  if (!ts) return '';
  const d = new Date(ts);
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
</script>

<template>
  <div class="space-y-8 max-w-5xl mx-auto pb-16 animate-fade-in text-fg">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-line pb-6 flex-wrap gap-4">
      <div>
        <h1 class="text-2xl md:text-3xl font-extrabold text-fg tracking-tight flex items-center gap-3">
          <Activity class="w-7 h-7 text-accent" />
          <span>{{ t('analytics.title') }}</span>
        </h1>
        <p class="text-xs text-fg-4 mt-1">
          {{ t('analytics.subtitle') }}
        </p>
      </div>

      <div class="flex items-center gap-2.5">
        <button
          @click="exportUserDataBundle"
          class="px-3.5 py-1.5 rounded-xl border border-accent/40 bg-accent-fill/10 hover:bg-accent-fill/20 text-accent-soft text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer"
          :title="t('analytics.exportBundle')"
        >
          <Download class="w-3.5 h-3.5" />
          <span>{{ t('analytics.exportBundle') }}</span>
        </button>

        <button
          @click="showConfirmReset = true"
          class="px-3.5 py-1.5 rounded-xl border border-danger-fill/40 bg-danger-fill/10 hover:bg-danger-fill/20 text-danger text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer"
        >
          <Trash2 class="w-3.5 h-3.5" />
          <span>{{ t('analytics.clearAll') }}</span>
        </button>
      </div>
    </div>

    <!-- Subcategory Capsule Switcher -->
    <div class="flex items-center gap-2 flex-wrap">
      <button
        v-for="tab in availableTabs"
        :key="tab.id"
        @click="activeSubTab = (tab.id as AnalyticsSubTab)"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-bold border transition flex items-center gap-2 cursor-pointer shadow-xs',
          activeSubTab === tab.id
            ? 'bg-accent-fill text-on-fill border-accent shadow-sm'
            : 'bg-surface-2/70 text-fg-3 border-line hover:text-fg hover:bg-surface-2'
        ]"
      >
        <component :is="tab.icon" class="w-3.5 h-3.5" />
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <!-- 1. Overview Tab -->
    <div v-if="activeSubTab === 'overview'" class="space-y-6">
      <!-- Core Metrics Grid -->
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        <!-- Dwell Time -->
        <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between text-fg-4 mb-3">
            <span class="text-xs font-medium">{{ t('analytics.totalTime') }}</span>
            <Clock class="w-4 h-4 text-accent" />
          </div>
          <div class="text-xl md:text-2xl font-black text-fg font-mono">
            {{ formattedFocusTime }}
          </div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.dwellStat') }}</div>
        </div>

        <!-- Movies Explored -->
        <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between text-fg-4 mb-3">
            <span class="text-xs font-medium">{{ t('analytics.moviesViewed') }}</span>
            <Film class="w-4 h-4 text-accent" />
          </div>
          <div class="text-xl md:text-2xl font-black text-fg font-mono">
            {{ analytics.uniqueMoviesViewed.length }} <span class="text-xs font-normal text-fg-4">{{ t('analytics.unitMovies') }}</span>
          </div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.movieViewsStat', { count: analytics.movieViewsCount }) }}</div>
        </div>

        <!-- Episodes Explored -->
        <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between text-fg-4 mb-3">
            <span class="text-xs font-medium">{{ t('analytics.episodesViewed') }}</span>
            <Layers class="w-4 h-4 text-accent" />
          </div>
          <div class="text-xl md:text-2xl font-black text-fg font-mono">
            {{ analytics.episodeViewsCount }} <span class="text-xs font-normal text-fg-4">{{ t('analytics.unitScenes') }}</span>
          </div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.episodeScenesStat') }}</div>
        </div>

        <!-- Performers Explored -->
        <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between text-fg-4 mb-3">
            <span class="text-xs font-medium">{{ t('analytics.performersViewed') }}</span>
            <Users class="w-4 h-4 text-accent" />
          </div>
          <div class="text-xl md:text-2xl font-black text-fg font-mono">
            {{ analytics.uniquePerformersViewed.length }} <span class="text-xs font-normal text-fg-4">{{ t('analytics.unitPersons') }}</span>
          </div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.performerProfilesStat', { count: analytics.performerViewsCount }) }}</div>
        </div>

        <!-- Search Count -->
        <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between text-fg-4 mb-3">
            <span class="text-xs font-medium">{{ t('analytics.searchCount') }}</span>
            <Search class="w-4 h-4 text-accent" />
          </div>
          <div class="text-xl md:text-2xl font-black text-fg font-mono">
            {{ analytics.searchesCount }} <span class="text-xs font-normal text-fg-4">{{ t('analytics.unitTimes') }}</span>
          </div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.searchesExecutedStat') }}</div>
        </div>

        <!-- Favorites -->
        <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between text-fg-4 mb-3">
            <span class="text-xs font-medium">{{ t('analytics.favoriteCount') }}</span>
            <Bookmark class="w-4 h-4 text-danger" />
          </div>
          <div class="text-xl md:text-2xl font-black text-fg font-mono">
            {{ analytics.favoritesAddedCount }} <span class="text-xs font-normal text-fg-4">{{ t('analytics.unitItems') }}</span>
          </div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.favoritesBreakdownStat') }}</div>
        </div>

        <!-- Ratings Given -->
        <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between text-fg-4 mb-3">
            <span class="text-xs font-medium">{{ t('analytics.ratingCount') }}</span>
            <Star class="w-4 h-4 text-accent fill-accent" />
          </div>
          <div class="text-xl md:text-2xl font-black text-fg font-mono">
            {{ analytics.ratingsCount }} <span class="text-xs font-normal text-fg-4">{{ t('analytics.unitTimes') }}</span>
          </div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.ratingsBreakdownStat') }}</div>
        </div>

        <!-- Active Days -->
        <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between text-fg-4 mb-3">
            <span class="text-xs font-medium">{{ t('analytics.activeStreak') }}</span>
            <Flame class="w-4 h-4 text-orange-400" />
          </div>
          <div class="text-xl md:text-2xl font-black text-fg font-mono">
            {{ analytics.activeDays.length }} <span class="text-xs font-normal text-fg-4">{{ t('analytics.unitDays') }}</span>
          </div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.activeDaysStat') }}</div>
        </div>
      </div>


      <!-- Secondary Insights: Night Owl & Privacy Notice -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- Night Owl Card -->
        <div class="p-6 rounded-3xl bg-surface/60 border border-line space-y-3">
          <div class="flex items-center gap-2.5">
            <div class="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
              <Moon class="w-5 h-5" />
            </div>
            <div>
              <h3 class="text-sm font-bold text-fg">{{ t('analytics.midnightDive') }}</h3>
              <p class="text-xs text-fg-4">{{ t('analytics.midnightDesc') }}</p>
            </div>
          </div>
          <div class="pt-2 flex items-baseline gap-2">
            <span class="text-2xl font-black text-purple-300 font-mono">{{ analytics.nightOwlViewsCount }}</span>
            <span class="text-xs text-fg-4">{{ t('analytics.nightOwlExplorations') }}</span>
          </div>
        </div>

        <!-- Data Guarantee Notice -->
        <div class="p-6 rounded-3xl bg-surface/60 border border-line space-y-3">
          <div class="flex items-center gap-2.5">
            <div class="p-2 rounded-xl bg-accent-fill/10 text-accent border border-accent-fill/20">
              <ShieldCheck class="w-5 h-5" />
            </div>
            <div>
              <h3 class="text-sm font-bold text-fg">{{ t('analytics.localPrivacyTitle') }}</h3>
              <p class="text-xs text-fg-4">{{ t('analytics.localPrivacySubtitle') }}</p>
            </div>
          </div>
          <p class="text-xs text-fg-3 leading-relaxed pt-1">
            {{ t('analytics.localPrivacyDesc') }}
          </p>
        </div>
      </div>
    </div>

    <!-- 2. Footprint Tab -->
    <div v-else-if="activeSubTab === 'footprint'" class="space-y-6">
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="p-5 rounded-2xl bg-surface/70 border border-line">
          <div class="text-xs font-semibold text-fg-4 mb-1">{{ t('analytics.firstLaunchTitle') }}</div>
          <div class="text-lg font-bold text-fg font-mono">{{ firstLaunchFormatted }}</div>
          <div class="text-[11px] text-fg-5 mt-1">{{ t('analytics.firstLaunchDesc') }}</div>
        </div>
        <div class="p-5 rounded-2xl bg-surface/70 border border-line">
          <div class="text-xs font-semibold text-fg-4 mb-1">{{ t('analytics.activeDaysTitle') }}</div>
          <div class="text-lg font-bold text-orange-400 font-mono">{{ analytics.activeDays.length }} {{ t('analytics.unitDays') }}</div>
          <div class="text-[11px] text-fg-5 mt-1">{{ t('analytics.activeDaysDesc') }}</div>
        </div>
        <div class="p-5 rounded-2xl bg-surface/70 border border-line">
          <div class="text-xs font-semibold text-fg-4 mb-1">{{ t('analytics.totalFocusTitle') }}</div>
          <div class="text-lg font-bold text-accent font-mono">{{ formattedFocusTime }}</div>
          <div class="text-[11px] text-fg-5 mt-1">{{ t('analytics.totalFocusDesc') }}</div>
        </div>
      </div>

      <!-- Breakdown Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="p-6 rounded-3xl bg-surface/60 border border-line space-y-4">
          <h3 class="text-sm font-bold text-fg flex items-center gap-2">
            <Film class="w-4 h-4 text-accent" />
            <span>{{ t('analytics.deepExplorationDimensions') }}</span>
          </h3>
          <div class="space-y-2.5 text-xs">
            <div class="flex justify-between py-1.5 border-b border-line/50">
              <span class="text-fg-4">{{ t('analytics.exploreFeatureMovies') }}</span>
              <span class="font-bold text-fg font-mono">{{ analytics.uniqueMoviesViewed.length }} {{ t('analytics.unitMovies') }}</span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-line/50">
              <span class="text-fg-4">{{ t('analytics.totalMovieBrowses') }}</span>
              <span class="font-bold text-fg font-mono">{{ analytics.movieViewsCount }} {{ t('analytics.unitTimes') }}</span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-line/50">
              <span class="text-fg-4">{{ t('analytics.exploreEpisodesClips') }}</span>
              <span class="font-bold text-fg font-mono">{{ analytics.episodeViewsCount }} {{ t('analytics.unitScenes') }}</span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-line/50">
              <span class="text-fg-4">{{ t('analytics.performerProfilesConsulted') }}</span>
              <span class="font-bold text-fg font-mono">{{ t('analytics.performerSummary', { performers: analytics.uniquePerformersViewed.length, count: analytics.performerViewsCount }) }}</span>
            </div>
            <div class="flex justify-between py-1.5">
              <span class="text-fg-4">{{ t('analytics.directorStudioExploration') }}</span>
              <span class="font-bold text-fg font-mono">{{ t('analytics.directorStudioSummary', { directors: analytics.directorViewsCount, studios: analytics.studioViewsCount }) }}</span>
            </div>
          </div>
        </div>

        <div class="p-6 rounded-3xl bg-surface/60 border border-line space-y-4">
          <h3 class="text-sm font-bold text-fg flex items-center gap-2">
            <Moon class="w-4 h-4 text-purple-400" />
            <span>{{ t('analytics.dayNightRhythm') }}</span>
          </h3>
          <div class="space-y-4 pt-1">
            <div>
              <div class="flex justify-between text-xs mb-1.5">
                <span class="text-fg-3 flex items-center gap-1.5">
                  <Moon class="w-3.5 h-3.5 text-purple-400" /> {{ t('analytics.midnightExplorer') }}
                </span>
                <span class="font-mono font-bold text-purple-300">{{ analytics.nightOwlViewsCount }} {{ t('analytics.unitTimes') }}</span>
              </div>
              <div class="h-2 rounded-full bg-sunken overflow-hidden">
                <div
                  class="h-full bg-purple-500 transition-all duration-500"
                  :style="{ width: `${analytics.movieViewsCount > 0 ? Math.min(100, Math.round((analytics.nightOwlViewsCount / analytics.movieViewsCount) * 100)) : 0}%` }"
                ></div>
              </div>
            </div>

            <div>
              <div class="flex justify-between text-xs mb-1.5">
                <span class="text-fg-3 flex items-center gap-1.5">
                  <Clock class="w-3.5 h-3.5 text-amber-400" /> {{ t('analytics.daytimeExplorer') }}
                </span>
                <span class="font-mono font-bold text-amber-300">
                  {{ Math.max(0, analytics.movieViewsCount - analytics.nightOwlViewsCount) }} {{ t('analytics.unitTimes') }}
                </span>
              </div>
              <div class="h-2 rounded-full bg-sunken overflow-hidden">
                <div
                  class="h-full bg-amber-500 transition-all duration-500"
                  :style="{ width: `${analytics.movieViewsCount > 0 ? Math.max(0, 100 - Math.round((analytics.nightOwlViewsCount / analytics.movieViewsCount) * 100)) : 0}%` }"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Recent Browsing History -->
      <div v-if="analytics.browseHistory && analytics.browseHistory.length > 0" class="p-6 rounded-3xl bg-surface/60 border border-line space-y-3">
        <h3 class="text-sm font-bold text-fg flex items-center gap-2">
          <Clock class="w-4 h-4 text-accent" />
          <span>{{ t('analytics.recentFootprints') }} ({{ Math.min(10, analytics.browseHistory.length) }})</span>
        </h3>
        <div class="divide-y divide-line/40">
          <div
            v-for="(item, idx) in analytics.browseHistory.slice(-10).reverse()"
            :key="idx"
            class="py-2.5 flex items-center justify-between text-xs gap-3"
          >
            <div class="flex items-center gap-2 min-w-0">
              <span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-surface-2 text-fg-3 shrink-0">
                {{ item.type === 'episode' ? t('nav.episodes') : item.type === 'movie' ? t('nav.movies') : item.type === 'performer' ? t('nav.performers') : item.type === 'director' ? t('filter.director') : t('nav.studios') }}
              </span>
              <span class="font-semibold text-fg-2 truncate">{{ item.title }}</span>
            </div>
            <span class="text-[11px] font-mono text-fg-5 shrink-0">{{ formatHistoryTime(item.time) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. Interaction Tab -->
    <div v-else-if="activeSubTab === 'interaction'" class="space-y-6">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="p-5 rounded-2xl bg-surface/80 border border-line">
          <div class="flex items-center justify-between text-fg-4 mb-2">
            <span class="text-xs font-semibold">{{ t('analytics.privateRatings') }}</span>
            <Star class="w-4 h-4 text-accent fill-accent" />
          </div>
          <div class="text-2xl font-black text-fg font-mono">{{ analytics.ratingsCount }}</div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.privateRatingsDesc') }}</div>
        </div>

        <div class="p-5 rounded-2xl bg-surface/80 border border-line">
          <div class="flex items-center justify-between text-fg-4 mb-2">
            <span class="text-xs font-semibold">{{ t('analytics.favoritesMarks') }}</span>
            <Heart class="w-4 h-4 text-danger fill-danger" />
          </div>
          <div class="text-2xl font-black text-fg font-mono">{{ analytics.favoritesAddedCount }}</div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.favoritesMarksDesc') }}</div>
        </div>

        <div class="p-5 rounded-2xl bg-surface/80 border border-line">
          <div class="flex items-center justify-between text-fg-4 mb-2">
            <span class="text-xs font-semibold">{{ t('analytics.customTagsCreated') }}</span>
            <Tag class="w-4 h-4 text-emerald-400" />
          </div>
          <div class="text-2xl font-black text-fg font-mono">{{ analytics.tagsCreatedCount }}</div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.customTagsDesc') }}</div>
        </div>

        <div class="p-5 rounded-2xl bg-surface/80 border border-line">
          <div class="flex items-center justify-between text-fg-4 mb-2">
            <span class="text-xs font-semibold">{{ t('analytics.aiTranslationsInvoked') }}</span>
            <Languages class="w-4 h-4 text-sky-400" />
          </div>
          <div class="text-2xl font-black text-fg font-mono">{{ analytics.translationsCount }}</div>
          <div class="text-[10px] text-fg-5 mt-1">{{ t('analytics.aiTranslationsDesc') }}</div>
        </div>
      </div>

      <!-- Search History Chips -->
      <div v-if="analytics.searchHistory && analytics.searchHistory.length > 0" class="p-6 rounded-3xl bg-surface/60 border border-line space-y-3">
        <h3 class="text-sm font-bold text-fg flex items-center gap-2">
          <Search class="w-4 h-4 text-accent" />
          <span>{{ t('analytics.searchKeywords') }} ({{ Math.min(12, analytics.searchHistory.length) }})</span>
        </h3>
        <div class="flex items-center gap-2 flex-wrap pt-1">
          <span
            v-for="(kw, idx) in analytics.searchHistory.slice(-12).reverse()"
            :key="idx"
            class="px-3 py-1.5 rounded-xl bg-surface-2 border border-line text-xs font-medium text-fg-2"
          >
            {{ kw }}
          </span>
        </div>
      </div>
    </div>

    <!-- 3.5 Deep Insights Tab -->
    <div v-else-if="activeSubTab === 'insights'" class="space-y-6">
      <!-- Insights Header Card -->
      <div class="p-6 rounded-3xl bg-gradient-to-br from-accent/15 via-surface/80 to-surface border border-accent/25 flex items-center justify-between gap-4 flex-wrap shadow-lg">
        <div class="flex items-center gap-4">
          <div class="p-3.5 rounded-2xl bg-accent-fill/20 text-accent border border-accent/30 shadow-inner">
            <PieChart class="w-8 h-8" />
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h2 class="text-xl font-extrabold text-fg tracking-tight">{{ t('analytics.deepInsightsTitle') }}</h2>
              <span class="text-[10px] px-2 py-0.5 rounded-full bg-accent-fill/20 text-accent font-bold border border-accent/30">
                {{ t('analytics.offlineComputed') }}
              </span>
            </div>
            <p class="text-xs text-fg-4 mt-0.5">
              {{ t('analytics.deepInsightsSubtitle', { count: deepInsights.totalItemsExplored }) }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <button
            @click="loadDeepInsights"
            :disabled="isInsightsLoading"
            class="px-3 py-1.5 rounded-xl bg-surface-2 hover:bg-surface-3 border border-line text-xs font-semibold text-fg flex items-center gap-1.5 transition cursor-pointer"
          >
            <RefreshCw class="w-3.5 h-3.5 text-accent" :class="{ 'animate-spin': isInsightsLoading }" />
            <span>{{ t('analytics.recalculate') }}</span>
          </button>
          <button
            @click="exportUserDataBundle"
            class="px-4 py-2 rounded-xl bg-accent-fill hover:bg-accent text-on-fill text-xs font-bold flex items-center gap-1.5 transition shadow cursor-pointer"
          >
            <Download class="w-3.5 h-3.5" />
            <span>{{ t('analytics.exportBundleBtn') }}</span>
          </button>
        </div>
      </div>

      <!-- Grid of Eras & Studios -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- 1. Eras Distribution -->
        <div class="p-6 rounded-3xl bg-surface/70 border border-line space-y-4 shadow-sm">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-bold text-fg flex items-center gap-2">
              <Calendar class="w-4 h-4 text-amber-400" />
              <span>{{ t('analytics.eraDistribution') }}</span>
            </h3>
            <span class="text-[11px] text-fg-4">{{ t('analytics.allErasSpan') }}</span>
          </div>

          <div class="space-y-3 pt-1">
            <div v-for="era in deepInsights.eras" :key="era.era" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <span class="text-fg-3">{{ t(era.era, era.era) }}</span>
                <span class="font-mono text-fg font-semibold">{{ era.count }} {{ t('analytics.unitMovies') }} ({{ era.percentage }}%)</span>
              </div>
              <div class="w-full h-2 rounded-full bg-sunken overflow-hidden">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-amber-500 to-accent transition-all duration-700"
                  :style="{ width: `${era.percentage}%` }"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 2. Top Studios Affinities -->
        <div class="p-6 rounded-3xl bg-surface/70 border border-line space-y-4 shadow-sm">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-bold text-fg flex items-center gap-2">
              <Building2 class="w-4 h-4 text-purple-400" />
              <span>{{ t('analytics.favoriteStudios') }}</span>
            </h3>
            <span class="text-[11px] text-fg-4">{{ t('analytics.top6Studios') }}</span>
          </div>

          <div v-if="deepInsights.topStudios.length > 0" class="space-y-3 pt-1">
            <div v-for="(st, idx) in deepInsights.topStudios" :key="st.studio" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <div class="flex items-center gap-2 min-w-0">
                  <span
                    class="w-4 h-4 rounded-full text-[10px] font-bold flex items-center justify-center shrink-0"
                    :class="idx === 0 ? 'bg-amber-400 text-black' : idx === 1 ? 'bg-slate-300 text-black' : idx === 2 ? 'bg-amber-700 text-white' : 'bg-surface-3 text-fg-4'"
                  >
                    {{ idx + 1 }}
                  </span>
                  <span class="text-fg-2 font-medium truncate">{{ st.studio }}</span>
                </div>
                <span class="font-mono text-fg font-semibold shrink-0">{{ st.count }} {{ t('analytics.unitMovies') }} ({{ st.percentage }}%)</span>
              </div>
              <div class="w-full h-2 rounded-full bg-sunken overflow-hidden">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-purple-500 to-indigo-400 transition-all duration-700"
                  :style="{ width: `${st.percentage}%` }"
                ></div>
              </div>
            </div>
          </div>
          <div v-else class="text-xs text-fg-4 text-center py-8">
            {{ t('analytics.noStudioData') }}
          </div>
        </div>
      </div>

      <!-- Rating Distribution & Persona Card -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- 3. Star Ratings Breakdown -->
        <div class="p-6 rounded-3xl bg-surface/70 border border-line space-y-4 shadow-sm">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-bold text-fg flex items-center gap-2">
              <Star class="w-4 h-4 text-accent fill-accent" />
              <span>{{ t('analytics.ratingDistribution') }}</span>
            </h3>
            <span class="text-[11px] text-fg-4">{{ t('analytics.totalRatingsCount', { count: analytics.ratingsCount }) }}</span>
          </div>

          <div class="space-y-3 pt-1">
            <div v-for="r in deepInsights.ratings" :key="r.stars" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <div class="flex items-center gap-1.5">
                  <div class="flex text-accent">
                    <Star v-for="s in r.stars" :key="s" class="w-3 h-3 fill-accent" />
                  </div>
                  <span class="text-fg-4 font-mono">({{ r.stars }} {{ t('analytics.starUnit') }})</span>
                </div>
                <span class="font-mono text-fg font-semibold">{{ r.count }} {{ t('analytics.unitMovies') }} ({{ r.percentage }}%)</span>
              </div>
              <div class="w-full h-2 rounded-full bg-sunken overflow-hidden">
                <div
                  class="h-full rounded-full bg-accent-fill transition-all duration-700"
                  :style="{ width: `${r.percentage}%` }"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 4. Privacy & Full Package Export Banner -->
        <div class="p-6 rounded-3xl bg-gradient-to-br from-emerald-500/10 via-surface/80 to-surface border border-emerald-500/30 space-y-4 flex flex-col justify-between shadow-sm">
          <div class="space-y-3">
            <div class="flex items-center gap-3">
              <div class="p-2.5 rounded-2xl bg-emerald-500/15 text-emerald-400 border border-emerald-500/25">
                <ShieldCheck class="w-6 h-6" />
              </div>
              <div>
                <h3 class="text-sm font-bold text-fg">{{ t('analytics.packageBackupTitle') }}</h3>
                <p class="text-xs text-fg-4 mt-0.5">{{ t('analytics.packageBackupSubtitle') }}</p>
              </div>
            </div>

            <p class="text-xs text-fg-3 leading-relaxed">
              {{ t('analytics.packageBackupDesc') }}
            </p>
          </div>

          <button
            @click="exportUserDataBundle"
            class="w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs flex items-center justify-center gap-2 transition shadow-lg shadow-emerald-600/20 cursor-pointer"
          >
            <Download class="w-4 h-4" />
            <span>{{ t('analytics.exportJsonBtn') }}</span>
          </button>
        </div>
      </div>
    </div>



    <!-- Confirm Modal -->
    <div
      v-if="showConfirmReset"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-scrim/80 backdrop-blur-sm animate-fade-in"
      @click.self="showConfirmReset = false"
    >
      <div class="max-w-md w-full chrome-panel border border-line-strong rounded-3xl p-6 space-y-4 shadow-2xl">
        <div class="flex items-center gap-3 text-danger">
          <AlertTriangle class="w-6 h-6" />
          <h3 class="text-base font-bold">{{ t('analytics.clearAll') }}</h3>
        </div>
        <p class="text-xs text-fg-3 leading-relaxed">
          {{ t('analytics.clearConfirm') }}
        </p>
        <div class="flex justify-end gap-3 pt-2">
          <button
            @click="showConfirmReset = false"
            class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-xs font-semibold text-fg-2 transition border border-line cursor-pointer"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            @click="confirmReset"
            class="px-4 py-2 rounded-xl bg-danger-fill text-on-fill text-xs font-bold transition shadow-lg shadow-danger-fill/20 cursor-pointer"
          >
            {{ t('analytics.confirmClearBtn') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
