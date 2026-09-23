import { ref, computed } from 'vue';
import { listen } from '@tauri-apps/api/event';
import { api } from '../api';
import type { ScraperStatus, ScraperMode } from '../types';

const defaultStatus: ScraperStatus = {
  running: false,
  mode: 'idle',
  current_id: 0,
  target_total: 0,
  processed_count: 0,
  percent: 0,
  current_title: '',
  new_movies: 0,
  new_performers: 0,
  speed_fps: 0,
  eta_minutes: 0,
  message: '就绪',
  logs: [],
  elapsed_secs: 0,
  finished: false,
  error: null,
};

export const scraperState = ref<ScraperStatus>({ ...defaultStatus });

export const isScrapingRunning = computed(() => scraperState.value.running);

export const newlyScrapedCount = computed(
  () => scraperState.value.new_movies + scraperState.value.new_performers
);

let isInitialized = false;
const listeners: Array<() => void> = [];

export function onScraperDataChange(cb: () => void) {
  listeners.push(cb);
  return () => {
    const idx = listeners.indexOf(cb);
    if (idx !== -1) listeners.splice(idx, 1);
  };
}

let lastMoviesCount = 0;
let lastPerformersCount = 0;

export async function initScraperService() {
  if (isInitialized) return;
  isInitialized = true;

  try {
    const status = await api.getScraperStatus();
    scraperState.value = status;
    lastMoviesCount = status.new_movies;
    lastPerformersCount = status.new_performers;
  } catch (err) {
    console.warn('[ScraperService] Failed to get initial status:', err);
  }

  // Listen to live Tauri events from Rust backend
  try {
    await listen<ScraperStatus>('scraper-progress', (event) => {
      scraperState.value = event.payload;

      // If new movies or performers were scraped, trigger UI refresh listeners
      if (
        event.payload.new_movies > lastMoviesCount ||
        event.payload.new_performers > lastPerformersCount
      ) {
        lastMoviesCount = event.payload.new_movies;
        lastPerformersCount = event.payload.new_performers;
        for (const cb of listeners) {
          try {
            cb();
          } catch {}
        }
      }
    });

    await listen<ScraperStatus>('scraper-finished', (event) => {
      scraperState.value = event.payload;
      for (const cb of listeners) {
        try {
          cb();
        } catch {}
      }
    });

    await listen<ScraperStatus>('scraper-stopped', (event) => {
      scraperState.value = event.payload;
    });
  } catch (e) {
    console.warn('[ScraperService] Tauri event listeners unavailable (browser mode):', e);
  }
}

export async function startScraperTask(
  mode: ScraperMode,
  limit?: number,
  startId?: number,
  endId?: number
): Promise<ScraperStatus> {
  const status = await api.startScraper(mode, limit, startId, endId);
  scraperState.value = status;
  lastMoviesCount = 0;
  lastPerformersCount = 0;
  return status;
}

export async function stopScraperTask(): Promise<ScraperStatus> {
  const status = await api.stopScraper();
  scraperState.value = status;
  return status;
}
