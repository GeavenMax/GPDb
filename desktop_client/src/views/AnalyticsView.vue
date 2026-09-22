<script setup lang="ts">
import { computed, ref } from 'vue';
import { analytics, resetAllAnalytics } from '../services/analytics';
import { t } from '../i18n';
import {
  Clock, Film, Users, Layers, Search, Bookmark,
  Star, Flame, Moon, Trash2, AlertTriangle, Check
} from '@lucide/vue';

const showConfirmReset = ref(false);

const formattedFocusTime = computed(() => {
  const sec = analytics.value.totalFocusTimeSeconds;
  const hours = Math.floor(sec / 3600);
  const mins = Math.floor((sec % 3600) / 60);
  if (hours > 0) {
    return `${hours} 小时 ${mins} 分钟`;
  }
  return `${mins} 分钟 ${sec % 60} 秒`;
});

function confirmReset() {
  resetAllAnalytics();
  showConfirmReset.value = false;
}
</script>

<template>
  <div class="space-y-8 max-w-5xl mx-auto pb-16 animate-fade-in text-fg">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-line pb-6">
      <div>
        <h1 class="text-2xl md:text-3xl font-extrabold text-fg tracking-tight">
          {{ t('analytics.title') }}
        </h1>
        <p class="text-xs text-fg-4 mt-1">
          {{ t('analytics.subtitle') }}
        </p>
      </div>

      <button
        @click="showConfirmReset = true"
        class="px-3.5 py-1.5 rounded-xl border border-danger-fill/40 bg-danger-fill/10 hover:bg-danger-fill/20 text-danger text-xs font-semibold flex items-center gap-1.5 transition"
      >
        <Trash2 class="w-3.5 h-3.5" />
        <span>清空所有统计数据</span>
      </button>
    </div>

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
        <div class="text-[10px] text-fg-5 mt-1">聚焦活跃时间统计</div>
      </div>

      <!-- Movies Explored -->
      <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
        <div class="flex items-center justify-between text-fg-4 mb-3">
          <span class="text-xs font-medium">{{ t('analytics.moviesViewed') }}</span>
          <Film class="w-4 h-4 text-accent" />
        </div>
        <div class="text-xl md:text-2xl font-black text-fg font-mono">
          {{ analytics.uniqueMoviesViewed.length }} <span class="text-xs font-normal text-fg-4">部</span>
        </div>
        <div class="text-[10px] text-fg-5 mt-1">累计浏览 {{ analytics.movieViewsCount }} 次</div>
      </div>

      <!-- Episodes Explored -->
      <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
        <div class="flex items-center justify-between text-fg-4 mb-3">
          <span class="text-xs font-medium">{{ t('analytics.episodesViewed') }}</span>
          <Layers class="w-4 h-4 text-accent" />
        </div>
        <div class="text-xl md:text-2xl font-black text-fg font-mono">
          {{ analytics.episodeViewsCount }} <span class="text-xs font-normal text-fg-4">段</span>
        </div>
        <div class="text-[10px] text-fg-5 mt-1">独立场景与影片分集</div>
      </div>

      <!-- Performers Explored -->
      <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
        <div class="flex items-center justify-between text-fg-4 mb-3">
          <span class="text-xs font-medium">{{ t('analytics.performersViewed') }}</span>
          <Users class="w-4 h-4 text-accent" />
        </div>
        <div class="text-xl md:text-2xl font-black text-fg font-mono">
          {{ analytics.uniquePerformersViewed.length }} <span class="text-xs font-normal text-fg-4">位</span>
        </div>
        <div class="text-[10px] text-fg-5 mt-1">累计了解档案 {{ analytics.performerViewsCount }} 次</div>
      </div>

      <!-- Search Count -->
      <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
        <div class="flex items-center justify-between text-fg-4 mb-3">
          <span class="text-xs font-medium">{{ t('analytics.searchCount') }}</span>
          <Search class="w-4 h-4 text-accent" />
        </div>
        <div class="text-xl md:text-2xl font-black text-fg font-mono">
          {{ analytics.searchesCount }} <span class="text-xs font-normal text-fg-4">次</span>
        </div>
        <div class="text-[10px] text-fg-5 mt-1">全站检索执行次数</div>
      </div>

      <!-- Favorites -->
      <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
        <div class="flex items-center justify-between text-fg-4 mb-3">
          <span class="text-xs font-medium">{{ t('analytics.favoriteCount') }}</span>
          <Bookmark class="w-4 h-4 text-danger" />
        </div>
        <div class="text-xl md:text-2xl font-black text-fg font-mono">
          {{ analytics.favoritesAddedCount }} <span class="text-xs font-normal text-fg-4">项</span>
        </div>
        <div class="text-[10px] text-fg-5 mt-1">影片、演员与导演收藏</div>
      </div>

      <!-- Ratings Given -->
      <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
        <div class="flex items-center justify-between text-fg-4 mb-3">
          <span class="text-xs font-medium">{{ t('analytics.ratingCount') }}</span>
          <Star class="w-4 h-4 text-accent fill-accent" />
        </div>
        <div class="text-xl md:text-2xl font-black text-fg font-mono">
          {{ analytics.ratingsCount }} <span class="text-xs font-normal text-fg-4">次</span>
        </div>
        <div class="text-[10px] text-fg-5 mt-1">已打分星级记录</div>
      </div>

      <!-- Active Days -->
      <div class="p-5 rounded-2xl bg-surface/80 border border-line flex flex-col justify-between shadow-sm">
        <div class="flex items-center justify-between text-fg-4 mb-3">
          <span class="text-xs font-medium">{{ t('analytics.activeStreak') }}</span>
          <Flame class="w-4 h-4 text-orange-400" />
        </div>
        <div class="text-xl md:text-2xl font-black text-fg font-mono">
          {{ analytics.activeDays.length }} <span class="text-xs font-normal text-fg-4">天</span>
        </div>
        <div class="text-[10px] text-fg-5 mt-1">累计探索打卡天数</div>
      </div>
    </div>

    <!-- Secondary Insights: Night Owl & Recent Activity -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <!-- Night Owl Card -->
      <div class="p-6 rounded-3xl bg-surface/60 border border-line space-y-3">
        <div class="flex items-center gap-2.5">
          <div class="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
            <Moon class="w-5 h-5" />
          </div>
          <div>
            <h3 class="text-sm font-bold text-fg">午夜沉浸探影</h3>
            <p class="text-xs text-fg-4">统计凌晨 23:00 ~ 05:00 之间的观影探索习惯</p>
          </div>
        </div>
        <div class="pt-2 flex items-baseline gap-2">
          <span class="text-2xl font-black text-purple-300 font-mono">{{ analytics.nightOwlViewsCount }}</span>
          <span class="text-xs text-fg-4">次夜猫子专属探索</span>
        </div>
      </div>

      <!-- Data Guarantee Notice -->
      <div class="p-6 rounded-3xl bg-surface/60 border border-line space-y-3">
        <div class="flex items-center gap-2.5">
          <div class="p-2 rounded-xl bg-accent-fill/10 text-accent border border-accent-fill/20">
            <Check class="w-5 h-5" />
          </div>
          <div>
            <h3 class="text-sm font-bold text-fg">100% 本地隐私保证</h3>
            <p class="text-xs text-fg-4">所有浏览与检索数据仅存储在您的 Mac 本机</p>
          </div>
        </div>
        <p class="text-xs text-fg-3 leading-relaxed pt-1">
          程序不包含任何第三方跟踪探针或远程分析 SDK。您随时可以在「设置 → 隐私」随时关闭统计或一键抹除所有数据。
        </p>
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
          <h3 class="text-base font-bold">清空使用统计确认</h3>
        </div>
        <p class="text-xs text-fg-3 leading-relaxed">
          {{ t('analytics.clearConfirm') }}
        </p>
        <div class="flex justify-end gap-3 pt-2">
          <button
            @click="showConfirmReset = false"
            class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-xs font-semibold text-fg-2 transition border border-line"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            @click="confirmReset"
            class="px-4 py-2 rounded-xl bg-danger-fill text-on-fill text-xs font-bold transition shadow-lg shadow-danger-fill/20"
          >
            确认清空
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
