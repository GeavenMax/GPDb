import { ref, computed } from 'vue';
import { pluginsConfig, savePluginsConfig } from './pluginManager';
import { isScrapingRunning, startScraperTask } from './scraper';
import type { AutoSyncScheduleConfig } from '../types';

let tickerTimer: ReturnType<typeof setInterval> | null = null;
const isAutoSyncTriggering = ref(false);

/**
 * Computes the next scheduled execution timestamp based on current configuration.
 */
export function computeNextRunDate(config: AutoSyncScheduleConfig): Date {
  const now = new Date();
  
  if (config.mode === 'daily') {
    const [hStr, mStr] = (config.dailyTime || '04:00').split(':');
    const targetH = parseInt(hStr || '4', 10);
    const targetM = parseInt(mStr || '0', 10);

    const candidate = new Date(now.getFullYear(), now.getMonth(), now.getDate(), targetH, targetM, 0, 0);
    // If the scheduled time today has already passed, schedule for tomorrow
    if (candidate.getTime() <= now.getTime()) {
      candidate.setDate(candidate.getDate() + 1);
    }
    return candidate;
  }

  // Interval mode
  const intervalHours = Math.max(1, config.intervalHours || 12);
  const intervalMs = intervalHours * 60 * 60 * 1000;

  if (config.lastRunTime) {
    const lastRun = new Date(config.lastRunTime).getTime();
    const candidateTime = lastRun + intervalMs;
    // If overdue by more than 1 interval, schedule within 2 minutes of app launch
    if (candidateTime <= now.getTime()) {
      return new Date(now.getTime() + 60 * 1000); // 1 minute from now
    }
    return new Date(candidateTime);
  }

  // Default: from now + interval
  return new Date(now.getTime() + intervalMs);
}

/**
 * Checks schedule and triggers background incremental scraper if due.
 */
export async function evaluateAutoSyncTick() {
  const cfg = pluginsConfig.value.autoSyncConfig;
  if (!cfg || !cfg.enabled || !pluginsConfig.value.customScraperEnabled) {
    return;
  }

  if (isScrapingRunning.value || isAutoSyncTriggering.value) {
    return;
  }

  const now = Date.now();
  let nextRunTimeMs = cfg.nextRunTime ? new Date(cfg.nextRunTime).getTime() : 0;

  // Initialize next run time if missing or invalid
  if (!nextRunTimeMs || isNaN(nextRunTimeMs)) {
    const nextDate = computeNextRunDate(cfg);
    savePluginsConfig({
      autoSyncConfig: {
        ...cfg,
        nextRunTime: nextDate.toISOString(),
      },
    });
    return;
  }

  // Is it time to run?
  if (now >= nextRunTimeMs) {
    try {
      isAutoSyncTriggering.value = true;
      console.log('⏰ [AutoSync] 定时自动后台同步触发，正在启动增量同步任务...');
      
      const nowIso = new Date().toISOString();
      await startScraperTask('incremental');
      
      // Calculate subsequent run time
      const nextRunDate = computeNextRunDate({
        ...cfg,
        lastRunTime: nowIso,
      });

      savePluginsConfig({
        autoSyncConfig: {
          ...cfg,
          lastRunTime: nowIso,
          nextRunTime: nextRunDate.toISOString(),
        },
      });
      console.log(`⏰ [AutoSync] 增量同步已启动，下一次预计执行时间: ${nextRunDate.toLocaleString()}`);
    } catch (err) {
      console.warn('⏰ [AutoSync] 定时启动增量同步失败:', err);
    } finally {
      isAutoSyncTriggering.value = false;
    }
  }
}

/**
 * Updates schedule configuration and immediately re-evaluates next run time.
 */
export function updateAutoSyncSchedule(partial: Partial<AutoSyncScheduleConfig>) {
  const current = pluginsConfig.value.autoSyncConfig;
  const merged: AutoSyncScheduleConfig = {
    ...current,
    ...partial,
  };

  if (merged.enabled) {
    const nextDate = computeNextRunDate(merged);
    merged.nextRunTime = nextDate.toISOString();
  } else {
    merged.nextRunTime = null;
  }

  savePluginsConfig({
    autoSyncConfig: merged,
  });
}

/**
 * Starts the heartbeat timer ticker (evaluates every 30 seconds).
 */
export function initAutoSyncSchedule() {
  if (tickerTimer) {
    clearInterval(tickerTimer);
  }

  // Initial immediate check after short delay
  setTimeout(() => {
    evaluateAutoSyncTick();
  }, 3000);

  // Periodic heartbeat
  tickerTimer = setInterval(() => {
    evaluateAutoSyncTick();
  }, 30000);
}

export function stopAutoSyncSchedule() {
  if (tickerTimer) {
    clearInterval(tickerTimer);
    tickerTimer = null;
  }
}

/**
 * Human-readable next run description
 */
export const nextRunDescription = computed(() => {
  const cfg = pluginsConfig.value.autoSyncConfig;
  if (!cfg?.enabled) return '未启用定时同步';
  if (!cfg.nextRunTime) return '计算中...';

  const target = new Date(cfg.nextRunTime);
  const diffMs = target.getTime() - Date.now();

  if (diffMs <= 0) return '即将执行...';

  const diffHours = Math.floor(diffMs / (3600 * 1000));
  const diffMinutes = Math.floor((diffMs % (3600 * 1000)) / (60 * 1000));

  let relative = '';
  if (diffHours > 0) {
    relative = `约 ${diffHours} 小时 ${diffMinutes} 分钟后`;
  } else {
    relative = `约 ${diffMinutes} 分钟后`;
  }

  const timeStr = target.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const dateStr = target.toLocaleDateString([], { month: 'numeric', day: 'numeric' });
  return `${dateStr} ${timeStr} (${relative})`;
});

export const lastRunDescription = computed(() => {
  const cfg = pluginsConfig.value.autoSyncConfig;
  if (!cfg?.lastRunTime) return '尚未执行过';
  const d = new Date(cfg.lastRunTime);
  return `${d.toLocaleDateString()} ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
});
