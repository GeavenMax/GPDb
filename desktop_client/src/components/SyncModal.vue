<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import {
  X, RefreshCw, CheckCircle2, Film, Users, Sparkles, AlertCircle,
  Square, ArrowDownToLine, Zap, Terminal, Layers
} from '@lucide/vue';
import type { DatabaseStats, ScraperMode } from '../types';
import {
  scraperState, isScrapingRunning, startScraperTask, stopScraperTask
} from '../services/scraper';

const props = defineProps<{
  open: boolean;
  stats: DatabaseStats | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'sync-complete'): void;
}>();

const selectedMode = ref<ScraperMode>('incremental');
const quickLimit = ref(1000);
const startId = ref(1);
const endId = ref(76000);
const actionError = ref<string | null>(null);

const terminalRef = ref<HTMLElement | null>(null);

// Auto-scroll terminal logs to bottom
watch(
  () => scraperState.value.logs.length,
  () => {
    nextTick(() => {
      if (terminalRef.value) {
        terminalRef.value.scrollTop = terminalRef.value.scrollHeight;
      }
    });
  }
);

async function handleStart() {
  actionError.value = null;
  try {
    if (selectedMode.value === 'incremental') {
      await startScraperTask('incremental');
    } else if (selectedMode.value === 'movies_boost') {
      await startScraperTask('movies_boost', quickLimit.value);
    } else if (selectedMode.value === 'movies_full') {
      await startScraperTask('movies_full', undefined, startId.value, endId.value);
    } else if (selectedMode.value === 'performers_full') {
      await startScraperTask('performers_full');
    }
  } catch (err: any) {
    actionError.value = err?.message || String(err);
  }
}

async function handleStop() {
  try {
    await stopScraperTask();
  } catch (err: any) {
    actionError.value = err?.message || String(err);
  }
}

function handleDismissToBackground() {
  emit('close');
}

function formatElapsed(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}分${s.toString().padStart(2, '0')}秒`;
}
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-scrim/80 backdrop-blur-md animate-fade-in select-none"
    @click.self="emit('close')"
  >
    <div class="relative w-full max-w-2xl chrome-panel border border-line-strong/80 rounded-3xl shadow-2xl p-6 md:p-8 text-fg space-y-5 max-h-[92vh] flex flex-col">
      <!-- Top ambient light -->
      <div class="absolute -top-24 left-1/2 -translate-x-1/2 w-96 h-48 bg-gradient-to-b from-indigo-500/15 via-purple-500/10 to-transparent blur-3xl pointer-events-none"></div>

      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-5 right-5 text-fg-3 hover:text-fg p-1.5 rounded-xl hover:bg-surface-2 transition cursor-pointer"
        title="关闭（若任务在运行，将继续在后台静默执行）"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Header -->
      <div class="flex items-center gap-3.5 relative z-10 shrink-0">
        <div
          class="w-11 h-11 rounded-2xl flex items-center justify-center shadow-lg transition"
          :class="isScrapingRunning
            ? 'bg-gradient-to-tr from-amber-500 to-rose-500 text-white shadow-orange-500/25'
            : 'bg-gradient-to-tr from-indigo-600 to-purple-600 text-white shadow-indigo-500/25'"
        >
          <RefreshCw :class="['w-5 h-5', isScrapingRunning ? 'animate-spin' : '']" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h2 class="text-lg font-black tracking-tight text-fg">自动化刮削与同步中心</h2>
            <span
              v-if="isScrapingRunning"
              class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30 flex items-center gap-1"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"></span>
              后台运行中
            </span>
          </div>
          <p class="text-xs text-fg-4 mt-0.5">支持极速增量与全量工业级搜刮，断点续传零数据竞争，界面完全解耦</p>
        </div>
      </div>

      <!-- Current Database Overview -->
      <div v-if="stats" class="grid grid-cols-2 sm:grid-cols-3 gap-3 p-3.5 rounded-2xl bg-surface-2/60 border border-line text-xs shrink-0">
        <div class="space-y-0.5">
          <div class="text-fg-4 flex items-center gap-1.5 text-[11px]">
            <Film class="w-3.5 h-3.5 text-indigo-400" />
            <span>本地已收录电影</span>
          </div>
          <div class="text-base font-black text-fg">{{ stats.movies.toLocaleString() }} 部</div>
        </div>
        <div class="space-y-0.5">
          <div class="text-fg-4 flex items-center gap-1.5 text-[11px]">
            <Users class="w-3.5 h-3.5 text-purple-400" />
            <span>本地演员档案</span>
          </div>
          <div class="text-base font-black text-fg">{{ stats.performers.toLocaleString() }} 位</div>
        </div>
        <div class="space-y-0.5 col-span-2 sm:col-span-1">
          <div class="text-fg-4 flex items-center gap-1.5 text-[11px]">
            <Layers class="w-3.5 h-3.5 text-amber-400" />
            <span>本次运行入库</span>
          </div>
          <div class="text-base font-black text-amber-300">
            +{{ (scraperState.new_movies + scraperState.new_performers).toLocaleString() }} 项
          </div>
        </div>
      </div>

      <!-- Scrollable content -->
      <div class="flex-1 overflow-y-auto space-y-4 pr-1">
        <!-- MODE SELECTOR (Only shown when NOT currently running) -->
        <div v-if="!isScrapingRunning && !scraperState.finished" class="space-y-2.5">
          <label class="text-xs font-bold text-fg-3 uppercase tracking-wider block">选择刮削策略</label>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            <!-- Mode 1: Incremental sync -->
            <button
              type="button"
              @click="selectedMode = 'incremental'"
              class="p-3.5 rounded-2xl border text-left transition flex flex-col justify-between cursor-pointer"
              :class="selectedMode === 'incremental'
                ? 'bg-indigo-500/10 border-indigo-500/60 shadow-sm'
                : 'bg-surface-2/40 border-line hover:border-line-strong hover:bg-surface-2/80'"
            >
              <div class="flex items-center gap-2 mb-1">
                <Zap class="w-4 h-4" :class="selectedMode === 'incremental' ? 'text-indigo-400' : 'text-fg-4'" />
                <span class="font-bold text-xs" :class="selectedMode === 'incremental' ? 'text-indigo-300' : 'text-fg'">增量极速同步</span>
              </div>
              <p class="text-[11px] text-fg-4 leading-relaxed">
                检查官网 /newm 与 /newp 获取近期上映新片与新星（推荐日常使用，30 秒完成）。
              </p>
            </button>

            <!-- Mode 2: Quick Boost 1000 -->
            <button
              type="button"
              @click="selectedMode = 'movies_boost'"
              class="p-3.5 rounded-2xl border text-left transition flex flex-col justify-between cursor-pointer"
              :class="selectedMode === 'movies_boost'
                ? 'bg-amber-500/10 border-amber-500/60 shadow-sm'
                : 'bg-surface-2/40 border-line hover:border-line-strong hover:bg-surface-2/80'"
            >
              <div class="flex items-center gap-2 mb-1">
                <Sparkles class="w-4 h-4" :class="selectedMode === 'movies_boost' ? 'text-amber-400' : 'text-fg-4'" />
                <span class="font-bold text-xs" :class="selectedMode === 'movies_boost' ? 'text-amber-300' : 'text-fg'">最新精选快速建库</span>
              </div>
              <p class="text-[11px] text-fg-4 leading-relaxed">
                逆序抓取最新 1,000 部热门影视作品（新空白库立竿见影，几分钟即可填满主页）。
              </p>
            </button>

            <!-- Mode 3: Movies Full -->
            <button
              type="button"
              @click="selectedMode = 'movies_full'"
              class="p-3.5 rounded-2xl border text-left transition flex flex-col justify-between cursor-pointer"
              :class="selectedMode === 'movies_full'
                ? 'bg-purple-500/10 border-purple-500/60 shadow-sm'
                : 'bg-surface-2/40 border-line hover:border-line-strong hover:bg-surface-2/80'"
            >
              <div class="flex items-center gap-2 mb-1">
                <ArrowDownToLine class="w-4 h-4" :class="selectedMode === 'movies_full' ? 'text-purple-400' : 'text-fg-4'" />
                <span class="font-bold text-xs" :class="selectedMode === 'movies_full' ? 'text-purple-300' : 'text-fg'">全站电影全量建库</span>
              </div>
              <p class="text-[11px] text-fg-4 leading-relaxed">
                从 #1 扫到 #76,000 完整入库 6 万部电影，8 线程并发，支持随时暂停与断点续爬。
              </p>
            </button>

            <!-- Mode 4: Performers Full -->
            <button
              type="button"
              @click="selectedMode = 'performers_full'"
              class="p-3.5 rounded-2xl border text-left transition flex flex-col justify-between cursor-pointer"
              :class="selectedMode === 'performers_full'
                ? 'bg-rose-500/10 border-rose-500/60 shadow-sm'
                : 'bg-surface-2/40 border-line hover:border-line-strong hover:bg-surface-2/80'"
            >
              <div class="flex items-center gap-2 mb-1">
                <Users class="w-4 h-4" :class="selectedMode === 'performers_full' ? 'text-rose-400' : 'text-fg-4'" />
                <span class="font-bold text-xs" :class="selectedMode === 'performers_full' ? 'text-rose-300' : 'text-fg'">已知演员全量档案补齐</span>
              </div>
              <p class="text-[11px] text-fg-4 leading-relaxed">
                根据已录入电影的演员名单，为所有出演演员批量爬取身体属性、曾用名与高清头像。
              </p>
            </button>
          </div>
        </div>

        <!-- RUNNING OR FINISHED DASHBOARD -->
        <div v-if="isScrapingRunning || scraperState.finished || scraperState.logs.length > 0" class="space-y-3.5">
          <!-- Real-time Progress Bar & Stats -->
          <div class="p-4 rounded-2xl bg-surface-2/60 border border-line space-y-3">
            <div class="flex items-center justify-between text-xs">
              <span class="font-bold text-fg flex items-center gap-2">
                <span
                  class="w-2 h-2 rounded-full"
                  :class="isScrapingRunning ? 'bg-amber-400 animate-ping' : (scraperState.error ? 'bg-rose-400' : 'bg-emerald-400')"
                ></span>
                {{ scraperState.message || '运行就绪' }}
              </span>
              <span class="font-mono text-fg-3 text-[11px]">
                {{ scraperState.percent.toFixed(1) }}%
              </span>
            </div>

            <!-- Progress Bar -->
            <div class="w-full h-2.5 rounded-full bg-surface-3 overflow-hidden border border-line/40">
              <div
                class="h-full bg-gradient-to-r from-indigo-500 via-purple-500 to-amber-500 transition-all duration-300"
                :style="{ width: `${Math.min(100, Math.max(isScrapingRunning ? 4 : 0, scraperState.percent))}%` }"
              ></div>
            </div>

            <!-- Metrics grid -->
            <div class="grid grid-cols-4 gap-2 pt-1 text-center font-mono">
              <div class="p-2 rounded-xl bg-surface/60 border border-line/60">
                <div class="text-[10px] text-fg-4">已新增电影</div>
                <div class="text-xs font-bold text-indigo-300">+{{ scraperState.new_movies }}</div>
              </div>
              <div class="p-2 rounded-xl bg-surface/60 border border-line/60">
                <div class="text-[10px] text-fg-4">已新增演员</div>
                <div class="text-xs font-bold text-purple-300">+{{ scraperState.new_performers }}</div>
              </div>
              <div class="p-2 rounded-xl bg-surface/60 border border-line/60">
                <div class="text-[10px] text-fg-4">当前速度</div>
                <div class="text-xs font-bold text-emerald-300">{{ scraperState.speed_fps > 0 ? `${scraperState.speed_fps.toFixed(1)}/s` : '连接中' }}</div>
              </div>
              <div class="p-2 rounded-xl bg-surface/60 border border-line/60">
                <div class="text-[10px] text-fg-4">已耗时</div>
                <div class="text-xs font-bold text-fg-2">{{ formatElapsed(scraperState.elapsed_secs) }}</div>
              </div>
            </div>
          </div>

          <!-- Real-time Terminal Log Console -->
          <div class="space-y-1.5">
            <div class="flex items-center justify-between text-[11px] text-fg-4">
              <span class="flex items-center gap-1.5 font-bold">
                <Terminal class="w-3.5 h-3.5 text-indigo-400" />
                <span>实时刮削日志输出</span>
              </span>
              <span class="font-mono text-[10px]">保留最新 60 条记录</span>
            </div>
            <div
              ref="terminalRef"
              class="h-36 overflow-y-auto p-3 rounded-2xl bg-black/70 border border-line/80 font-mono text-[11px] text-fg-3 space-y-1 select-text scroll-smooth"
            >
              <div
                v-for="(log, idx) in scraperState.logs"
                :key="idx"
                class="leading-tight break-all"
                :class="{
                  'text-amber-300 font-bold': log.includes('+ 新增'),
                  'text-emerald-300': log.includes('完成') || log.includes('200 OK'),
                  'text-rose-400': log.includes('Error') || log.includes('失败') || log.includes('🛑'),
                  'text-fg-4': log.includes('404') || log.includes('跳过'),
                }"
              >
                {{ log }}
              </div>
              <div v-if="scraperState.logs.length === 0" class="text-fg-5 italic">等待引擎任务初始化...</div>
            </div>
          </div>
        </div>

        <!-- Error alert -->
        <div
          v-if="actionError || scraperState.error"
          class="p-3.5 rounded-2xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-2.5 text-rose-300 text-xs"
        >
          <AlertCircle class="w-4 h-4 shrink-0 mt-0.5 text-rose-400" />
          <div class="break-all">{{ actionError || scraperState.error }}</div>
        </div>

        <!-- Success completion banner -->
        <div
          v-if="scraperState.finished && !scraperState.error && !isScrapingRunning"
          class="p-4 rounded-2xl bg-success-fill/10 border border-success-fill/30 flex items-center gap-3 text-success-soft text-xs"
        >
          <CheckCircle2 class="w-5 h-5 shrink-0 text-success" />
          <div>
            <div class="font-bold text-success">刮削任务已顺利完成！</div>
            <div class="mt-0.5 text-fg-3">
              本次运行已成功入库 {{ scraperState.new_movies }} 部新电影，{{ scraperState.new_performers }} 位新演员档案。
            </div>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="pt-2 border-t border-line/60 relative z-10 shrink-0 flex items-center gap-3">
        <!-- If Running: Background + Stop buttons -->
        <template v-if="isScrapingRunning">
          <button
            @click="handleDismissToBackground"
            class="flex-1 py-3 px-4 rounded-2xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold text-xs shadow-lg shadow-indigo-500/25 transition flex items-center justify-center gap-2 cursor-pointer active:scale-98"
          >
            <Sparkles class="w-4 h-4 text-indigo-200" />
            <span>后台静默运行（关闭此窗口，随时可唤回）</span>
          </button>

          <button
            @click="handleStop"
            class="py-3 px-4 rounded-2xl bg-rose-500/15 hover:bg-rose-500/25 text-rose-300 border border-rose-500/30 font-bold text-xs transition flex items-center justify-center gap-1.5 cursor-pointer active:scale-98 shrink-0"
          >
            <Square class="w-3.5 h-3.5 fill-rose-300" />
            <span>停止抓取</span>
          </button>
        </template>

        <!-- If Idle: Start button -->
        <template v-else-if="!scraperState.finished">
          <button
            @click="handleStart"
            class="flex-1 py-3 px-4 rounded-2xl bg-gradient-to-r from-amber-500 via-orange-500 to-rose-500 hover:from-amber-400 hover:to-rose-400 text-white font-bold text-xs shadow-lg shadow-orange-500/25 transition flex items-center justify-center gap-2 cursor-pointer active:scale-98"
          >
            <RefreshCw class="w-4 h-4 text-amber-200" />
            <span>开始执行刮削任务</span>
          </button>
        </template>

        <!-- If Finished: Finish and reload -->
        <template v-else>
          <button
            @click="emit('sync-complete'); emit('close')"
            class="flex-1 py-3 px-4 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold text-xs shadow-lg shadow-emerald-500/25 transition flex items-center justify-center gap-2 cursor-pointer active:scale-98"
          >
            <CheckCircle2 class="w-4 h-4" />
            <span>完成并查看新片库</span>
          </button>
          <button
            @click="scraperState.finished = false"
            class="py-3 px-4 rounded-2xl bg-surface-2 hover:bg-surface-3 border border-line text-fg text-xs font-medium transition cursor-pointer"
          >
            重新配置
          </button>
        </template>
      </div>
    </div>
  </div>
</template>
