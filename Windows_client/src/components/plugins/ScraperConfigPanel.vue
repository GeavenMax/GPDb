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
const scraperCustomMode = ref<'incremental' | 'bftv_catalog' | 'movies_boost' | 'movies_full' | 'performers_full'>('incremental');
const scraperCustomLimit = ref(1000);
const scraperCustomStartId = ref(1);
const scraperCustomEndId = ref(76000);

async function runScraper() {
  if (isScrapingRunning.value) return;
  scraperMessage.value = '正在安全检测并执行增量刮削...';
  scraperSuccess.value = null;

  try {
    await startScraperTask('incremental');
    scraperSuccess.value = true;
    scraperMessage.value = '增量同步任务已在后台极速启动，数据将实时自动同步！';
    emit('refresh-movies');
  } catch (err: any) {
    scraperSuccess.value = false;
    scraperMessage.value = `启动失败: ${err?.message || err || '服务未响应'}`;
  }
}

async function runCustomScraper() {
  if (isScrapingRunning.value) return;
  scraperMessage.value = '正在启动自定义刮削任务...';
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
    }
    scraperSuccess.value = true;
    scraperMessage.value = '刮削任务已在后台极速启动，数据将实时自动同步！';
    emit('refresh-movies');
  } catch (err: any) {
    scraperSuccess.value = false;
    scraperMessage.value = `启动失败: ${err?.message || err || '服务未响应'}`;
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
              同步中
            </span>
            <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 font-bold border border-emerald-500/20">内置</span>
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
          <span>{{ isScrapingRunning ? '增量同步运行中...' : '一键启动增量刮削' }}</span>
        </button>

        <button
          v-if="isScrapingRunning"
          @click="stopScraperTask"
          class="px-3 py-2 rounded-xl bg-rose-500/15 text-rose-300 hover:bg-rose-500/25 border border-rose-500/30 text-xs font-bold transition cursor-pointer"
        >
          中止任务
        </button>

        <button
          @click="emit('open-sync')"
          class="px-4 py-2 rounded-xl bg-surface-2 text-fg hover:bg-surface-3 border border-line text-xs font-bold flex items-center gap-2 transition cursor-pointer"
          title="打开全功能刮削与数据同步中心，支持全量、逆序及选段抓取"
        >
          <Compass class="w-3.5 h-3.5 text-indigo-400" />
          <span>打开同步控制中心</span>
        </button>

        <button
          @click="emit('open-environment-check')"
          class="px-3.5 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg text-xs font-bold border border-line flex items-center gap-1.5 transition cursor-pointer"
          title="校验 Python 3 解释器、SQLite 与本地影库环境就绪情况"
        >
          <Cpu class="w-3.5 h-3.5 text-sky-400" />
          <span>运行环境自检</span>
        </button>

        <button
          @click="showCustomScraperOptions = !showCustomScraperOptions"
          class="px-3.5 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg text-xs font-bold border flex items-center gap-1.5 transition cursor-pointer"
          :class="showCustomScraperOptions ? 'border-accent text-accent' : 'border-line'"
          title="自定义刮削策略与范围参数"
        >
          <SlidersHorizontal class="w-3.5 h-3.5 text-amber-400" />
          <span>高度自定义执行选项</span>
          <ChevronUp v-if="showCustomScraperOptions" class="w-3 h-3 ml-0.5" />
          <ChevronDown v-else class="w-3 h-3 ml-0.5" />
        </button>

        <span class="text-[11px] text-fg-4">
          自动检索 /newm、/newp、/newe，实时增量整合影片、演职员与分集
        </span>
      </div>

      <!-- Advanced Custom Options Panel -->
      <div v-if="showCustomScraperOptions" class="p-4 rounded-2xl bg-surface-2/70 border border-line space-y-3.5 animate-fade-in text-xs">
        <div class="flex items-center justify-between">
          <div class="font-bold text-fg flex items-center gap-2">
            <SlidersHorizontal class="w-4 h-4 text-accent" />
            <span>自定义刮削与同步参数</span>
          </div>
          <span class="text-[11px] text-fg-4">选择模式并根据需要调整参数</span>
        </div>

        <!-- Mode selection pills -->
        <div class="space-y-1.5">
          <div class="text-[11px] font-semibold text-fg-3">执行策略：</div>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
            <button
              type="button"
              @click="scraperCustomMode = 'incremental'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'incremental' ? 'bg-indigo-500/15 border-indigo-500 text-indigo-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <RefreshCw class="w-3.5 h-3.5" />
                <span>增量极速同步</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">抓取近期新增影片与演员</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'bftv_catalog'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'bftv_catalog' ? 'bg-emerald-500/15 border-emerald-500 text-emerald-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Globe class="w-3.5 h-3.5" />
                <span>BFTV 主页秒级关联</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">3秒注入12,000+演员直达链接</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'movies_boost'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'movies_boost' ? 'bg-amber-500/15 border-amber-500 text-amber-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Sparkles class="w-3.5 h-3.5" />
                <span>热门新片逆序爬取</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">倒序抓取精选热门库</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'movies_full'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'movies_full' ? 'bg-purple-500/15 border-purple-500 text-purple-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Download class="w-3.5 h-3.5" />
                <span>指定 ID 范围抓取</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">按自定义起止 ID 批量爬取</div>
            </button>

            <button
              type="button"
              @click="scraperCustomMode = 'performers_full'"
              class="p-2.5 rounded-xl border text-left transition cursor-pointer"
              :class="scraperCustomMode === 'performers_full' ? 'bg-rose-500/15 border-rose-500 text-rose-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
            >
              <div class="font-bold text-xs flex items-center gap-1.5">
                <Users class="w-3.5 h-3.5" />
                <span>演员全量资料补齐</span>
              </div>
              <div class="text-[10px] text-fg-4 mt-0.5">补齐已知演员身材与头像</div>
            </button>
          </div>
        </div>

        <!-- Dynamic Parameters -->
        <div v-if="scraperCustomMode === 'movies_boost'" class="p-3 rounded-xl bg-surface/60 border border-line flex items-center gap-3">
          <span class="text-fg-3">抓取数量上限：</span>
          <input
            type="number"
            v-model.number="scraperCustomLimit"
            min="100"
            max="10000"
            step="100"
            class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
          />
          <span class="text-[11px] text-fg-4">部影片（推荐 500 ~ 2,000 部）</span>
        </div>

        <div v-else-if="scraperCustomMode === 'movies_full'" class="p-3 rounded-xl bg-surface/60 border border-line flex items-center gap-3 flex-wrap">
          <div class="flex items-center gap-1.5">
            <span class="text-fg-3">起始 ID：</span>
            <input
              type="number"
              v-model.number="scraperCustomStartId"
              min="1"
              class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
            />
          </div>
          <div class="flex items-center gap-1.5">
            <span class="text-fg-3">结束 ID：</span>
            <input
              type="number"
              v-model.number="scraperCustomEndId"
              min="1"
              class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
            />
          </div>
          <span class="text-[11px] text-fg-4">当前影库上限约 #76,000</span>
        </div>

        <div v-else-if="scraperCustomMode === 'bftv_catalog'" class="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-[11px] flex items-center gap-2">
          <Globe class="w-4 h-4 shrink-0" />
          <span>通过 BoyfriendTV 全网目录反向匹配，自动剥离别名后缀，3 秒内将万余位演员直达链接写入数据库！</span>
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
            <span>立即按自定义配置启动</span>
          </button>
        </div>
      </div>

      <!-- Live Progress Banner when running -->
      <div v-if="isScrapingRunning" class="p-3.5 rounded-2xl bg-surface-2/70 border border-line/80 space-y-2 text-xs">
        <div class="flex items-center justify-between text-[11px]">
          <span class="font-bold text-fg flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span>
            {{ scraperState.message || '正在增量刮削...' }}
          </span>
          <div class="flex items-center gap-2 font-mono text-fg-3 text-[10px]">
            <span>+{{ scraperState.new_movies }} 电影</span>
            <span>+{{ scraperState.new_performers }} 演员</span>
            <span>+{{ scraperState.new_episodes }} 分集</span>
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
              ? `增量同步完成！本次入库影片 +${scraperState.new_movies} 部，演员 +${scraperState.new_performers} 位，分集 +${scraperState.new_episodes} 个。`
              : scraperMessage }}
          </span>
        </div>
        <button
          v-if="scraperState.finished"
          @click="emit('refresh-movies')"
          class="text-[11px] underline hover:text-fg transition cursor-pointer shrink-0"
        >
          刷新列表
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
                <span class="text-xs font-bold text-fg">定时自动后台更新</span>
                <span
                  v-if="pluginsConfig.autoSyncConfig.enabled"
                  class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 font-bold border border-emerald-500/30 flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
                  计划生效中
                </span>
                <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-surface-3 text-fg-4 font-medium">未启用</span>
              </div>
              <p class="text-[11px] text-fg-4 mt-0.5">在后台按自定义计划自动增量抓取最新条目，不干扰日常操作</p>
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
              <span>执行计划：</span>
            </div>
            <div class="flex items-center gap-1.5 p-1 rounded-xl bg-surface-3/80 border border-line">
              <button
                @click="handleUpdateScheduleMode('interval')"
                class="px-2.5 py-1 rounded-lg text-xs font-bold transition cursor-pointer"
                :class="pluginsConfig.autoSyncConfig.mode === 'interval' ? 'bg-surface text-fg shadow-sm' : 'text-fg-4 hover:text-fg'"
              >
                ⏱️ 周期循环
              </button>
              <button
                @click="handleUpdateScheduleMode('daily')"
                class="px-2.5 py-1 rounded-lg text-xs font-bold transition cursor-pointer"
                :class="pluginsConfig.autoSyncConfig.mode === 'daily' ? 'bg-surface text-fg shadow-sm' : 'text-fg-4 hover:text-fg'"
              >
                📅 每天定点
              </button>
            </div>

            <!-- If Interval mode -->
            <div v-if="pluginsConfig.autoSyncConfig.mode === 'interval'" class="flex items-center gap-2">
              <span class="text-fg-4 text-xs">每隔</span>
              <select
                :value="pluginsConfig.autoSyncConfig.intervalHours"
                @change="handleUpdateIntervalHours(Number(($event.target as HTMLSelectElement).value))"
                class="px-2.5 py-1 rounded-lg bg-surface border border-line text-xs font-bold text-fg focus:outline-none cursor-pointer"
              >
                <option :value="4">4 小时</option>
                <option :value="6">6 小时</option>
                <option :value="12">12 小时 (推荐)</option>
                <option :value="24">24 小时 (1 天)</option>
                <option :value="48">48 小时 (2 天)</option>
              </select>
              <span class="text-fg-4 text-xs">自动同步</span>
            </div>

            <!-- If Daily mode -->
            <div v-else class="flex items-center gap-2">
              <span class="text-fg-4 text-xs">每天</span>
              <input
                type="time"
                :value="pluginsConfig.autoSyncConfig.dailyTime"
                @change="handleUpdateDailyTime(($event.target as HTMLInputElement).value)"
                class="px-2 py-1 rounded-lg bg-surface border border-line text-xs font-bold text-fg focus:outline-none cursor-pointer"
              />
              <span class="text-fg-4 text-xs">静默执行</span>
            </div>
          </div>

          <!-- Status display: last run + next run -->
          <div class="flex items-center justify-between text-[11px] text-fg-4 pt-1 flex-wrap gap-2">
            <div class="flex items-center gap-1.5">
              <span>上次自动同步：</span>
              <span class="font-mono text-fg-3">{{ lastRunDescription }}</span>
            </div>
            <div class="flex items-center gap-1.5">
              <span>下次计划执行：</span>
              <span class="font-mono text-accent font-bold">{{ nextRunDescription }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
