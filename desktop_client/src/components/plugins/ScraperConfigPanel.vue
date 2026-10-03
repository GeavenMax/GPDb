<script setup lang="ts">
import { ref } from 'vue';
import { pluginsConfig, savePluginsConfig } from '../../services/pluginManager';
import { t } from '../../i18n';
import {
  scraperState,
  isScrapingRunning,
  startScraperTask,
  stopScraperTask
} from '../../services/scraper';
import {
  nextRunDescription,
  lastRunDescription,
  updateAutoSyncSchedule
} from '../../services/autoSync';
import {
  RefreshCw, Compass, Cpu, SlidersHorizontal, ChevronUp, ChevronDown,
  Globe, Sparkles, Download, Users, Play, Loader2, CheckCircle2,
  AlertCircle, Clock
} from '@lucide/vue';

const emit = defineEmits<{
  (e: 'open-sync'): void;
  (e: 'open-environment-check'): void;
  (e: 'refresh-movies'): void;
}>();

const scraperMessage = ref('');
const scraperSuccess = ref<boolean | null>(null);

const showCustomScraperOptions = ref(false);
const scraperCustomMode = ref<'incremental' | 'bftv_catalog' | 'movies_boost' | 'movies_full' | 'performers_full' | 'pbc_actors' | 'smutjunkies_actors'>('incremental');
const scraperCustomLimit = ref(1000);
const scraperCustomStartId = ref(1);
const scraperCustomEndId = ref(76000);

async function runScraper() {
  if (isScrapingRunning.value) return;
  scraperMessage.value = t('plugins.scrapingIncremental');
  scraperSuccess.value = null;

  try {
    await startScraperTask('incremental');
    scraperSuccess.value = true;
    scraperMessage.value = t('plugins.transStarted');
    emit('refresh-movies');
  } catch (err: any) {
    scraperSuccess.value = false;
    scraperMessage.value = `${t('plugins.transStartFailed')}: ${err?.message || err || ''}`;
  }
}

async function runCustomScraper() {
  if (isScrapingRunning.value) return;
  scraperMessage.value = t('plugins.scrapingIncremental');
  scraperSuccess.value = null;

  try {
    if (scraperCustomMode.value === 'incremental') {
      await startScraperTask('incremental');
    } else if (scraperCustomMode.value === 'bftv_catalog') {
      await startScraperTask('bftv_catalog');
    } else if (scraperCustomMode.value === 'movies_boost') {
      await startScraperTask('movies_boost', scraperCustomLimit.value);
    } else if (scraperCustomMode.value === 'movies_full') {
      await startScraperTask('movies_full', undefined, scraperCustomStartId.value, scraperCustomEndId.value);
    } else if (scraperCustomMode.value === 'performers_full') {
      await startScraperTask('performers_full');
    } else if (scraperCustomMode.value === 'pbc_actors') {
      await startScraperTask('pbc_actors', scraperCustomLimit.value);
    } else if (scraperCustomMode.value === 'smutjunkies_actors') {
      await startScraperTask('smutjunkies_actors', scraperCustomLimit.value);
    }
    scraperSuccess.value = true;
    scraperMessage.value = t('plugins.transStarted');
    emit('refresh-movies');
  } catch (err: any) {
    scraperSuccess.value = false;
    scraperMessage.value = `${t('plugins.transStartFailed')}: ${err?.message || err || ''}`;
  }
}

function handleToggleAutoSync(enabled: boolean) {
  updateAutoSyncSchedule({ enabled });
}

function handleUpdateScheduleMode(mode: 'interval' | 'daily') {
  updateAutoSyncSchedule({ mode });
}

function handleUpdateIntervalHours(intervalHours: number) {
  updateAutoSyncSchedule({ intervalHours });
}

function handleUpdateDailyTime(dailyTime: string) {
  updateAutoSyncSchedule({ dailyTime });
}
</script>

<template>
  <div class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm">
    <div class="flex items-start justify-between gap-4">
      <div class="flex items-center gap-3.5">
        <div
          class="p-3 rounded-2xl transition shadow"
          :class="isScrapingRunning
            ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
            : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'"
        >
          <RefreshCw class="w-6 h-6" :class="{ 'animate-spin': isScrapingRunning }" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h3 class="text-base font-bold text-fg">{{ t('plugins.customScraper') }}</h3>
            <span
              v-if="isScrapingRunning"
              class="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 font-bold border border-amber-500/30 flex items-center gap-1"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"></span>
              {{ t('plugins.syncRunning') }}
            </span>
            <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 font-bold border border-emerald-500/20">{{ t('plugins.builtIn') }}</span>
          </div>
          <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.customScraperDesc') }}</p>
        </div>
      </div>

      <label class="relative inline-flex items-center cursor-pointer">
        <input
          type="checkbox"
          v-model="pluginsConfig.customScraperEnabled"
          @change="savePluginsConfig({ customScraperEnabled: pluginsConfig.customScraperEnabled })"
          class="sr-only peer"
        />
        <div class="w-11 h-6 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-fill"></div>
      </label>
    </div>

    <div v-if="pluginsConfig.customScraperEnabled" class="pt-4 border-t border-line/60 space-y-3.5">
      <!-- Control Actions -->
      <div class="flex items-center gap-3 flex-wrap">
        <button
          @click="runScraper"
          :disabled="isScrapingRunning"
          class="px-4 py-2 rounded-xl bg-accent-fill text-on-fill text-xs font-bold flex items-center gap-2 hover:bg-accent-fill/90 transition shadow disabled:opacity-50 cursor-pointer"
        >
          <Loader2 v-if="isScrapingRunning" class="w-3.5 h-3.5 animate-spin" />
          <RefreshCw v-else class="w-3.5 h-3.5" />
          <span>{{ isScrapingRunning ? t('plugins.runningIncremental') : t('plugins.oneClickIncremental') }}</span>
        </button>

        <button
          v-if="isScrapingRunning"
          @click="stopScraperTask"
          class="px-3 py-2 rounded-xl bg-rose-500/15 text-rose-300 hover:bg-rose-500/25 border border-rose-500/30 text-xs font-bold transition cursor-pointer"
        >
          {{ t('plugins.stopTask') }}
        </button>

        <button
          @click="emit('open-sync')"
          class="px-4 py-2 rounded-xl bg-surface-2 text-fg hover:bg-surface-3 border border-line text-xs font-bold flex items-center gap-2 transition cursor-pointer"
          :title="t('plugins.openSyncCenterTitle')"
        >
          <Compass class="w-3.5 h-3.5 text-indigo-400" />
          <span>{{ t('plugins.openSyncCenter') }}</span>
        </button>

        <button
          @click="emit('open-environment-check')"
          class="px-3.5 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg text-xs font-bold border border-line flex items-center gap-1.5 transition cursor-pointer"
          :title="t('plugins.envCheckTitle')"
        >
          <Cpu class="w-3.5 h-3.5 text-sky-400" />
          <span>{{ t('plugins.envCheck') }}</span>
        </button>

        <button
          @click="showCustomScraperOptions = !showCustomScraperOptions"
          class="px-3.5 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg text-xs font-bold border flex items-center gap-1.5 transition cursor-pointer"
          :class="showCustomScraperOptions ? 'border-accent text-accent' : 'border-line'"
          :title="t('plugins.customOptionsTitle')"
        >
          <SlidersHorizontal class="w-3.5 h-3.5 text-amber-400" />
          <span>{{ t('plugins.customOptions') }}</span>
          <ChevronUp v-if="showCustomScraperOptions" class="w-3 h-3 ml-0.5" />
          <ChevronDown v-else class="w-3 h-3 ml-0.5" />
        </button>

        <span class="text-[11px] text-fg-4">
          {{ t('plugins.incrementalTip') }}
        </span>
      </div>

      <!-- Advanced Custom Options Panel -->
      <div v-if="showCustomScraperOptions" class="p-4 rounded-2xl bg-surface-2/70 border border-line space-y-3.5 animate-fade-in text-xs">
        <div class="flex items-center justify-between">
          <div class="font-bold text-fg flex items-center gap-2">
            <SlidersHorizontal class="w-4 h-4 text-accent" />
            <span>{{ t('plugins.customParamsTitle') }}</span>
          </div>
          <span class="text-[11px] text-fg-4">{{ t('plugins.customParamsDesc') }}</span>
        </div>

        <!-- Mode selection pills -->
        <div class="space-y-1.5">
          <div class="text-[11px] font-semibold text-fg-3">{{ t('plugins.execStrategy') }}</div>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
            <button
              type="button"
              @click="scraperCustomMode = 'incremental'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'incremental' ? 'bg-indigo-500/15 border-indigo-500 text-indigo-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <RefreshCw class="w-3.5 h-3.5" />
                <span>{{ t('plugins.incrementalTitle') }}</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">{{ t('plugins.incrementalShortDesc') }}</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'bftv_catalog'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'bftv_catalog' ? 'bg-emerald-500/15 border-emerald-500 text-emerald-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Globe class="w-3.5 h-3.5" />
                <span>{{ t('plugins.bftvFastLink') }}</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">{{ t('plugins.bftvFastLinkDesc') }}</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'movies_boost'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'movies_boost' ? 'bg-amber-500/15 border-amber-500 text-amber-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Sparkles class="w-3.5 h-3.5" />
                <span>{{ t('plugins.moviesBoostTitle') }}</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">{{ t('plugins.moviesBoostDesc') }}</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'movies_full'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'movies_full' ? 'bg-purple-500/15 border-purple-500 text-purple-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Download class="w-3.5 h-3.5" />
                <span>{{ t('plugins.rangeScrapeTitle') }}</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">{{ t('plugins.rangeScrapeDesc') }}</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'performers_full'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'performers_full' ? 'bg-rose-500/15 border-rose-500 text-rose-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Users class="w-3.5 h-3.5" />
                <span>{{ t('plugins.enrichPerformersTitle') }}</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">{{ t('plugins.enrichPerformersDesc') }}</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'pbc_actors'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'pbc_actors' ? 'bg-amber-500/15 border-amber-500 text-amber-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Sparkles class="w-3.5 h-3.5" />
                <span>{{ t('plugins.pbcActorsTitle') }}</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">{{ t('plugins.pbcActorsDesc') }}</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'smutjunkies_actors'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'smutjunkies_actors' ? 'bg-pink-500/15 border-pink-500 text-pink-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Globe class="w-3.5 h-3.5" />
                <span>{{ t('plugins.smutjunkiesActorsTitle') }}</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">{{ t('plugins.smutjunkiesActorsDesc') }}</div>
            </button>
          </div>
        </div>

        <!-- Dynamic Parameters -->
        <div v-if="scraperCustomMode === 'movies_boost'" class="p-3 rounded-xl bg-surface/60 border border-line flex items-center gap-3">
          <span class="text-fg-3">{{ t('plugins.limitMax') }}</span>
          <input
            type="number"
            v-model.number="scraperCustomLimit"
            min="100"
            max="10000"
            step="100"
            class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
          />
          <span class="text-[11px] text-fg-4">{{ t('plugins.limitUnitMovies') }}</span>
        </div>

        <div v-else-if="scraperCustomMode === 'movies_full'" class="p-3 rounded-xl bg-surface/60 border border-line flex items-center gap-3 flex-wrap">
          <div class="flex items-center gap-1.5">
            <span class="text-fg-3">{{ t('plugins.startId') }}</span>
            <input
              type="number"
              v-model.number="scraperCustomStartId"
              min="1"
              class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
            />
          </div>
          <div class="flex items-center gap-1.5">
            <span class="text-fg-3">{{ t('plugins.endId') }}</span>
            <input
              type="number"
              v-model.number="scraperCustomEndId"
              min="1"
              class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
            />
          </div>
          <span class="text-[11px] text-fg-4">{{ t('plugins.currentMaxIdTip') }}</span>
        </div>

        <div v-else-if="scraperCustomMode === 'bftv_catalog'" class="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-[11px] flex items-center gap-2">
          <Globe class="w-4 h-4 shrink-0" />
          <span>{{ t('plugins.bftvCatalogBanner') }}</span>
        </div>

        <div v-else-if="scraperCustomMode === 'pbc_actors'" class="space-y-3">
          <div class="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-300 text-[11px] flex items-center gap-2">
            <Sparkles class="w-4 h-4 shrink-0" />
            <span>{{ t('plugins.pbcActorsBanner') }}</span>
          </div>
          <div class="p-3 rounded-xl bg-surface/60 border border-line flex items-center gap-3">
            <span class="text-fg-3">{{ t('plugins.limitMax') }}</span>
            <input
              type="number"
              v-model.number="scraperCustomLimit"
              min="10"
              max="5000"
              step="50"
              class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
            />
            <span class="text-[11px] text-fg-4">{{ t('plugins.limitUnitActors') }}</span>
          </div>
        </div>

        <div v-else-if="scraperCustomMode === 'smutjunkies_actors'" class="space-y-3">
          <div class="p-3 rounded-xl bg-pink-500/10 border border-pink-500/20 text-pink-300 text-[11px] flex items-center gap-2">
            <Globe class="w-4 h-4 shrink-0" />
            <span>{{ t('plugins.smutjunkiesBanner') }}</span>
          </div>
          <div class="p-3 rounded-xl bg-surface/60 border border-line flex items-center gap-3">
            <span class="text-fg-3">{{ t('plugins.limitMax') }}</span>
            <input
              type="number"
              v-model.number="scraperCustomLimit"
              min="10"
              max="1000"
              step="50"
              class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
            />
            <span class="text-[11px] text-fg-4">{{ t('plugins.smutjunkiesDefaultTip') }}</span>
          </div>
        </div>

        <!-- Launch button -->
        <div class="flex items-center justify-end gap-3 pt-1">
          <button
            @click="runCustomScraper"
            :disabled="isScrapingRunning"
            class="px-4 py-2 rounded-xl bg-accent-fill text-on-fill font-bold text-xs flex items-center gap-1.5 hover:bg-accent-fill/90 transition shadow cursor-pointer disabled:opacity-50"
          >
            <Loader2 v-if="isScrapingRunning" class="w-3.5 h-3.5 animate-spin" />
            <Play v-else class="w-3.5 h-3.5" />
            <span>{{ t('plugins.launchCustom') }}</span>
          </button>
        </div>
      </div>

      <!-- Live Progress Banner when running -->
      <div v-if="isScrapingRunning" class="p-3.5 rounded-2xl bg-surface-2/70 border border-line/80 space-y-2 text-xs">
        <div class="flex items-center justify-between text-[11px]">
          <span class="font-bold text-fg flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span>
            {{ scraperState.message || t('plugins.scrapingIncremental') }}
          </span>
          <div class="flex items-center gap-2 font-mono text-fg-3 text-[10px]">
            <span>+{{ scraperState.new_movies }} {{ t('plugins.statMovies') }}</span>
            <span>+{{ scraperState.new_performers }} {{ t('plugins.statPerformers') }}</span>
            <span>+{{ scraperState.new_episodes }} {{ t('plugins.statEpisodes') }}</span>
          </div>
        </div>

        <!-- Mini Progress bar -->
        <div class="w-full h-1.5 rounded-full bg-surface-3 overflow-hidden">
          <div
            class="h-full bg-gradient-to-r from-indigo-500 to-amber-500 transition-all duration-300"
            :style="{ width: `${Math.min(100, Math.max(5, scraperState.percent))}%` }"
          ></div>
        </div>

        <!-- Latest log line preview -->
        <div v-if="scraperState.logs.length" class="text-[10px] font-mono text-fg-4 truncate">
          > {{ scraperState.logs[scraperState.logs.length - 1] }}
        </div>
      </div>

      <!-- Status Message when finished or stopped -->
      <div
        v-else-if="scraperMessage || scraperState.finished"
        class="p-3 rounded-xl border text-xs flex items-center justify-between gap-2"
        :class="(scraperSuccess !== false && !scraperState.error) ? 'bg-success-fill/10 border-success-fill/30 text-success' : 'bg-surface-2 border-line text-rose-400'"
      >
        <div class="flex items-center gap-2">
          <CheckCircle2 v-if="scraperSuccess !== false && !scraperState.error" class="w-4 h-4 shrink-0" />
          <AlertCircle v-else class="w-4 h-4 shrink-0" />
          <span>
            {{ scraperState.finished
              ? t('plugins.incrementalFinished', { movies: scraperState.new_movies, performers: scraperState.new_performers, episodes: scraperState.new_episodes })
              : scraperMessage }}
          </span>
        </div>
        <button
          v-if="scraperState.finished"
          @click="emit('refresh-movies')"
          class="text-[11px] underline hover:text-fg transition cursor-pointer shrink-0"
        >
          {{ t('plugins.refreshList') }}
        </button>
      </div>

      <!-- Scheduled Auto-Sync Settings Panel -->
      <div class="p-4 rounded-2xl bg-surface-2/60 border border-line/80 space-y-3">
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-2.5">
            <div class="p-2 rounded-xl bg-indigo-500/10 text-indigo-400">
              <Clock class="w-4 h-4" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="text-xs font-bold text-fg">{{ t('plugins.scheduledSyncTitle') }}</span>
                <span
                  v-if="pluginsConfig.autoSyncConfig.enabled"
                  class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 font-bold border border-emerald-500/30 flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
                  {{ t('plugins.planActive') }}
                </span>
                <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-surface-3 text-fg-4 font-medium">{{ t('plugins.planDisabled') }}</span>
              </div>
              <p class="text-[11px] text-fg-4 mt-0.5">{{ t('plugins.scheduledSyncDesc') }}</p>
            </div>
          </div>

          <!-- Toggle switch -->
          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              :checked="pluginsConfig.autoSyncConfig.enabled"
              @change="handleToggleAutoSync(($event.target as HTMLInputElement).checked)"
              class="sr-only peer"
            />
            <div class="w-9 h-5 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-accent-fill"></div>
          </label>
        </div>

        <!-- Interval / Daily configuration controls when enabled -->
        <div v-if="pluginsConfig.autoSyncConfig.enabled" class="pt-3 border-t border-line/50 space-y-3 text-xs">
          <!-- Mode select: Interval vs Daily -->
          <div class="flex items-center gap-4 flex-wrap">
            <div class="flex items-center gap-2 text-xs font-medium text-fg-3">
              <span>{{ t('plugins.execSchedule') }}</span>
            </div>
            <div class="flex items-center gap-1.5 p-1 rounded-xl bg-surface-3/80 border border-line">
              <button
                @click="handleUpdateScheduleMode('interval')"
                class="px-2.5 py-1 rounded-lg text-xs font-bold transition cursor-pointer"
                :class="pluginsConfig.autoSyncConfig.mode === 'interval' ? 'bg-surface text-fg shadow-sm' : 'text-fg-4 hover:text-fg'"
              >
                {{ t('plugins.intervalMode') }}
              </button>
              <button
                @click="handleUpdateScheduleMode('daily')"
                class="px-2.5 py-1 rounded-lg text-xs font-bold transition cursor-pointer"
                :class="pluginsConfig.autoSyncConfig.mode === 'daily' ? 'bg-surface text-fg shadow-sm' : 'text-fg-4 hover:text-fg'"
              >
                {{ t('plugins.dailyMode') }}
              </button>
            </div>

            <!-- If Interval mode -->
            <div v-if="pluginsConfig.autoSyncConfig.mode === 'interval'" class="flex items-center gap-2">
              <span class="text-fg-4 text-xs">{{ t('plugins.every') }}</span>
              <select
                :value="pluginsConfig.autoSyncConfig.intervalHours"
                @change="handleUpdateIntervalHours(Number(($event.target as HTMLSelectElement).value))"
                class="px-2.5 py-1 rounded-lg bg-surface border border-line text-xs font-bold text-fg focus:outline-none cursor-pointer"
              >
                <option :value="4">4 {{ t('plugins.hours') }}</option>
                <option :value="6">6 {{ t('plugins.hours') }}</option>
                <option :value="12">12 {{ t('plugins.hoursRecommended') }}</option>
                <option :value="24">24 {{ t('plugins.hours1Day') }}</option>
                <option :value="48">48 {{ t('plugins.hours2Days') }}</option>
              </select>
              <span class="text-fg-4 text-xs">{{ t('plugins.autoSyncSuffix') }}</span>
            </div>

            <!-- If Daily mode -->
            <div v-else class="flex items-center gap-2">
              <span class="text-fg-4 text-xs">{{ t('plugins.everyDay') }}</span>
              <input
                type="time"
                :value="pluginsConfig.autoSyncConfig.dailyTime"
                @change="handleUpdateDailyTime(($event.target as HTMLInputElement).value)"
                class="px-2 py-1 rounded-lg bg-surface border border-line text-xs font-bold text-fg focus:outline-none cursor-pointer"
              />
              <span class="text-fg-4 text-xs">{{ t('plugins.silentExec') }}</span>
            </div>
          </div>

          <!-- Status display: last run + next run -->
          <div class="flex items-center justify-between text-[11px] text-fg-4 pt-1 flex-wrap gap-2">
            <div class="flex items-center gap-1.5">
              <span>{{ t('plugins.lastAutoSync') }}</span>
              <span class="font-mono text-fg-3">{{ lastRunDescription }}</span>
            </div>
            <div class="flex items-center gap-1.5">
              <span>{{ t('plugins.nextScheduled') }}</span>
              <span class="font-mono text-accent font-bold">{{ nextRunDescription }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
