<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue';
import { analytics, resetAllAnalytics } from '../services/analytics';
import { trophyStats, resetUnlockedTrophies } from '../services/trophySystem';
import { pluginsConfig } from '../services/pluginManager';
import {
  deepInsights, loadDeepInsights, exportUserDataBundle, isInsightsLoading
} from '../services/userAnalytics';
import { t } from '../i18n';
import {
  Clock, Film, Users, Layers, Search, Bookmark,
  Star, Flame, Moon, Trash2, AlertTriangle,
  Trophy, Tag, Languages, Sparkles, ChevronRight,
  BarChart3, Activity, Heart, ShieldCheck, Download, PieChart,
  Building2, Calendar
} from '@lucide/vue';

const emit = defineEmits<{
  (e: 'open-trophies'): void;
}>();

type AnalyticsSubTab = 'overview' | 'footprint' | 'interaction' | 'insights' | 'trophies';
const activeSubTab = ref<AnalyticsSubTab>('overview');
const showConfirmReset = ref(false);

const availableTabs = computed(() => {
  const tabs: Array<{ id: AnalyticsSubTab; label: string; icon: any }> = [
    { id: 'overview', label: '概览汇总', icon: BarChart3 },
    { id: 'footprint', label: '观影足迹', icon: Film },
    { id: 'interaction', label: '互动偏好', icon: Star },
    { id: 'insights', label: '深度画像', icon: PieChart },
  ];
  if (pluginsConfig.value.trophiesEnabled) {
    tabs.push({ id: 'trophies', label: '典藏奖杯', icon: Trophy });
  }
  return tabs;
});

onMounted(() => {
  loadDeepInsights();
});

watch(() => pluginsConfig.value.trophiesEnabled, (enabled) => {
  if (!enabled && activeSubTab.value === 'trophies') {
    activeSubTab.value = 'overview';
  }
});

const formattedFocusTime = computed(() => {
  const sec = analytics.value.totalFocusTimeSeconds;
  const hours = Math.floor(sec / 3600);
  const mins = Math.floor((sec % 3600) / 60);
  if (hours > 0) {
    return `${hours} 小时 ${mins} 分钟`;
  }
  return `${mins} 分钟 ${sec % 60} 秒`;
});

const firstLaunchFormatted = computed(() => {
  if (!analytics.value.firstLaunchTime) return '今日';
  const d = new Date(analytics.value.firstLaunchTime);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
});

function confirmReset() {
  resetAllAnalytics();
  resetUnlockedTrophies();
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
          title="导出包含所有打分、收藏、标签和成就记录的打包文件"
        >
          <Download class="w-3.5 h-3.5" />
          <span>导出用户数据包 (.json)</span>
        </button>

        <button
          @click="showConfirmReset = true"
          class="px-3.5 py-1.5 rounded-xl border border-danger-fill/40 bg-danger-fill/10 hover:bg-danger-fill/20 text-danger text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer"
        >
          <Trash2 class="w-3.5 h-3.5" />
          <span>清空所有统计</span>
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
        <span
          v-if="tab.id === 'trophies'"
          class="text-[10px] px-1.5 py-0.2 rounded-full font-mono font-bold"
          :class="activeSubTab === tab.id ? 'bg-black/20 text-on-fill' : 'bg-purple-500/15 text-purple-300'"
        >
          {{ trophyStats.unlocked }}/{{ trophyStats.total }}
        </span>
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

      <!-- Trophies Highlight Banner -->
      <div
        v-if="pluginsConfig.trophiesEnabled"
        class="p-6 rounded-3xl bg-gradient-to-r from-purple-500/10 via-accent/5 to-transparent border border-purple-500/20 flex items-center justify-between gap-4 flex-wrap"
      >
        <div class="flex items-center gap-4">
          <div class="p-3 rounded-2xl bg-purple-500/15 text-purple-300 border border-purple-500/30 shrink-0">
            <Trophy class="w-7 h-7" />
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h3 class="text-base font-bold text-fg">典藏成就奖杯系统</h3>
              <span class="text-xs font-bold text-purple-300 font-mono">
                {{ trophyStats.unlocked }} / {{ trophyStats.total }} ({{ trophyStats.percentage }}%)
              </span>
            </div>
            <p class="text-xs text-fg-4 mt-0.5">77 座流体玻璃风格专属奖杯，记录你的每一步探索传奇</p>
          </div>
        </div>

        <button
          @click="emit('open-trophies')"
          class="px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs flex items-center gap-1.5 transition shadow-lg shadow-purple-600/20 cursor-pointer"
        >
          <span>进入奖杯陈列馆</span>
          <ChevronRight class="w-4 h-4" />
        </button>
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
              <ShieldCheck class="w-5 h-5" />
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
    </div>

    <!-- 2. Footprint Tab -->
    <div v-else-if="activeSubTab === 'footprint'" class="space-y-6">
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="p-5 rounded-2xl bg-surface/70 border border-line">
          <div class="text-xs font-semibold text-fg-4 mb-1">初次相遇启程</div>
          <div class="text-lg font-bold text-fg font-mono">{{ firstLaunchFormatted }}</div>
          <div class="text-[11px] text-fg-5 mt-1">开启本地影视漫游记</div>
        </div>
        <div class="p-5 rounded-2xl bg-surface/70 border border-line">
          <div class="text-xs font-semibold text-fg-4 mb-1">探索打卡活跃天数</div>
          <div class="text-lg font-bold text-orange-400 font-mono">{{ analytics.activeDays.length }} 天</div>
          <div class="text-[11px] text-fg-5 mt-1">持之以恒的影视热爱</div>
        </div>
        <div class="p-5 rounded-2xl bg-surface/70 border border-line">
          <div class="text-xs font-semibold text-fg-4 mb-1">总专注交互时长</div>
          <div class="text-lg font-bold text-accent font-mono">{{ formattedFocusTime }}</div>
          <div class="text-[11px] text-fg-5 mt-1">光影世界中的驻留岁月</div>
        </div>
      </div>

      <!-- Breakdown Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="p-6 rounded-3xl bg-surface/60 border border-line space-y-4">
          <h3 class="text-sm font-bold text-fg flex items-center gap-2">
            <Film class="w-4 h-4 text-accent" />
            <span>深度影视探索维度</span>
          </h3>
          <div class="space-y-2.5 text-xs">
            <div class="flex justify-between py-1.5 border-b border-line/50">
              <span class="text-fg-4">探索长片电影</span>
              <span class="font-bold text-fg font-mono">{{ analytics.uniqueMoviesViewed.length }} 部</span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-line/50">
              <span class="text-fg-4">累计长片点击浏览</span>
              <span class="font-bold text-fg font-mono">{{ analytics.movieViewsCount }} 次</span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-line/50">
              <span class="text-fg-4">独立分集 / 片段探索</span>
              <span class="font-bold text-fg font-mono">{{ analytics.episodeViewsCount }} 段</span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-line/50">
              <span class="text-fg-4">演职员档案查阅</span>
              <span class="font-bold text-fg font-mono">{{ analytics.uniquePerformersViewed.length }} 位演员 ({{ analytics.performerViewsCount }} 次)</span>
            </div>
            <div class="flex justify-between py-1.5">
              <span class="text-fg-4">导演与片商库探索</span>
              <span class="font-bold text-fg font-mono">{{ analytics.directorViewsCount }} 位导演 / {{ analytics.studioViewsCount }} 家片商</span>
            </div>
          </div>
        </div>

        <div class="p-6 rounded-3xl bg-surface/60 border border-line space-y-4">
          <h3 class="text-sm font-bold text-fg flex items-center gap-2">
            <Moon class="w-4 h-4 text-purple-400" />
            <span>昼夜光影节律分布</span>
          </h3>
          <div class="space-y-4 pt-1">
            <div>
              <div class="flex justify-between text-xs mb-1.5">
                <span class="text-fg-3 flex items-center gap-1.5">
                  <Moon class="w-3.5 h-3.5 text-purple-400" /> 午夜探索 (23:00~05:00)
                </span>
                <span class="font-mono font-bold text-purple-300">{{ analytics.nightOwlViewsCount }} 次</span>
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
                  <Clock class="w-3.5 h-3.5 text-amber-400" /> 日间与常态探索
                </span>
                <span class="font-mono font-bold text-amber-300">
                  {{ Math.max(0, analytics.movieViewsCount - analytics.nightOwlViewsCount) }} 次
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
          <span>近期探索足迹 (最近 {{ Math.min(10, analytics.browseHistory.length) }} 项)</span>
        </h3>
        <div class="divide-y divide-line/40">
          <div
            v-for="(item, idx) in analytics.browseHistory.slice(-10).reverse()"
            :key="idx"
            class="py-2.5 flex items-center justify-between text-xs gap-3"
          >
            <div class="flex items-center gap-2 min-w-0">
              <span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-surface-2 text-fg-3 shrink-0">
                {{ item.type === 'movie' ? '电影' : item.type === 'performer' ? '演员' : item.type === 'director' ? '导演' : '片商' }}
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
            <span class="text-xs font-semibold">私密评星打分</span>
            <Star class="w-4 h-4 text-accent fill-accent" />
          </div>
          <div class="text-2xl font-black text-fg font-mono">{{ analytics.ratingsCount }}</div>
          <div class="text-[10px] text-fg-5 mt-1">本地专属星级评价</div>
        </div>

        <div class="p-5 rounded-2xl bg-surface/80 border border-line">
          <div class="flex items-center justify-between text-fg-4 mb-2">
            <span class="text-xs font-semibold">收藏与片单标记</span>
            <Heart class="w-4 h-4 text-danger fill-danger" />
          </div>
          <div class="text-2xl font-black text-fg font-mono">{{ analytics.favoritesAddedCount }}</div>
          <div class="text-[10px] text-fg-5 mt-1">想看 / 已看 / 收藏总量</div>
        </div>

        <div class="p-5 rounded-2xl bg-surface/80 border border-line">
          <div class="flex items-center justify-between text-fg-4 mb-2">
            <span class="text-xs font-semibold">自定义标签创建</span>
            <Tag class="w-4 h-4 text-emerald-400" />
          </div>
          <div class="text-2xl font-black text-fg font-mono">{{ analytics.tagsCreatedCount }}</div>
          <div class="text-[10px] text-fg-5 mt-1">个性化影视分类标签</div>
        </div>

        <div class="p-5 rounded-2xl bg-surface/80 border border-line">
          <div class="flex items-center justify-between text-fg-4 mb-2">
            <span class="text-xs font-semibold">AI 翻译引擎调用</span>
            <Languages class="w-4 h-4 text-sky-400" />
          </div>
          <div class="text-2xl font-black text-fg font-mono">{{ analytics.translationsCount }}</div>
          <div class="text-[10px] text-fg-5 mt-1">大模型剧情简介翻译</div>
        </div>
      </div>

      <!-- Search History Chips -->
      <div v-if="analytics.searchHistory && analytics.searchHistory.length > 0" class="p-6 rounded-3xl bg-surface/60 border border-line space-y-3">
        <h3 class="text-sm font-bold text-fg flex items-center gap-2">
          <Search class="w-4 h-4 text-accent" />
          <span>近期检索词频 (最近 {{ Math.min(12, analytics.searchHistory.length) }} 次)</span>
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
              <h2 class="text-xl font-extrabold text-fg tracking-tight">本地深度影迷偏好画像</h2>
              <span class="text-[10px] px-2 py-0.5 rounded-full bg-accent-fill/20 text-accent font-bold border border-accent/30">
                100% 离线计算
              </span>
            </div>
            <p class="text-xs text-fg-4 mt-0.5">
              基于您标记的 {{ deepInsights.totalItemsExplored }} 部影片与评分数据，进行多维审美偏好与年代分布建模
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
            <span>重新统计</span>
          </button>
          <button
            @click="exportUserDataBundle"
            class="px-4 py-2 rounded-xl bg-accent-fill hover:bg-accent text-on-fill text-xs font-bold flex items-center gap-1.5 transition shadow cursor-pointer"
          >
            <Download class="w-3.5 h-3.5" />
            <span>导出整理好的数据包</span>
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
              <span>观影年代跨度分布</span>
            </h3>
            <span class="text-[11px] text-fg-4">全时期跨度</span>
          </div>

          <div class="space-y-3 pt-1">
            <div v-for="era in deepInsights.eras" :key="era.era" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <span class="text-fg-3">{{ era.era }}</span>
                <span class="font-mono text-fg font-semibold">{{ era.count }} 部 ({{ era.percentage }}%)</span>
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
              <span>偏好电影厂牌偏好</span>
            </h3>
            <span class="text-[11px] text-fg-4">Top 6 片商</span>
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
                <span class="font-mono text-fg font-semibold shrink-0">{{ st.count }} 部 ({{ st.percentage }}%)</span>
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
            暂无收藏或观影片商数据，收藏更多作品后即可解锁心仪厂牌分析
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
              <span>私密评星严苛度分布</span>
            </h3>
            <span class="text-[11px] text-fg-4">共 {{ analytics.ratingsCount }} 次打分</span>
          </div>

          <div class="space-y-3 pt-1">
            <div v-for="r in deepInsights.ratings" :key="r.stars" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <div class="flex items-center gap-1.5">
                  <div class="flex text-accent">
                    <Star v-for="s in r.stars" :key="s" class="w-3 h-3 fill-accent" />
                  </div>
                  <span class="text-fg-4 font-mono">({{ r.stars }} 星)</span>
                </div>
                <span class="font-mono text-fg font-semibold">{{ r.count }} 部 ({{ r.percentage }}%)</span>
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
                <h3 class="text-sm font-bold text-fg">本地专属数据包打包与备份</h3>
                <p class="text-xs text-fg-4 mt-0.5">所有评分、自定义标签、足迹与奖杯完全归您所有</p>
              </div>
            </div>

            <p class="text-xs text-fg-3 leading-relaxed">
              此功能将您在客户端中产生的所有私密互动（想看、看过、收藏的影片/演员/片商/分集、评星记录、自定义标签与解锁的成就奖杯记录）整合打包为一份标准 JSON 文件，可用于跨设备迁移或数据安全归档。
            </p>
          </div>

          <button
            @click="exportUserDataBundle"
            class="w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs flex items-center justify-center gap-2 transition shadow-lg shadow-emerald-600/20 cursor-pointer"
          >
            <Download class="w-4 h-4" />
            <span>立即导出完整本地用户数据包 (.json)</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 4. Trophies Tab -->
    <div v-else-if="activeSubTab === 'trophies'" class="space-y-6">
      <!-- Big Completion Progress Card -->
      <div class="p-8 rounded-3xl bg-gradient-to-br from-purple-500/15 via-surface/90 to-surface border border-purple-500/30 space-y-6 shadow-xl">
        <div class="flex items-start justify-between gap-4 flex-wrap">
          <div class="flex items-center gap-4">
            <div class="p-4 rounded-2xl bg-purple-500/20 text-purple-300 border border-purple-500/30 shadow-inner">
              <Trophy class="w-10 h-10" />
            </div>
            <div>
              <div class="flex items-center gap-2.5">
                <h2 class="text-2xl font-extrabold text-fg tracking-tight">典藏成就奖杯陈列馆</h2>
                <span class="px-2.5 py-0.5 rounded-full text-xs font-bold bg-purple-500/20 text-purple-300 border border-purple-500/30">
                  全 77 座
                </span>
              </div>
              <p class="text-xs text-fg-4 mt-1">包含白金、金、银、铜四大等级，忠实见证你在此片数据库中的每一步足迹</p>
            </div>
          </div>

          <button
            @click="emit('open-trophies')"
            class="px-5 py-2.5 rounded-2xl bg-purple-600 hover:bg-purple-500 text-white font-bold text-sm flex items-center gap-2 transition shadow-xl shadow-purple-600/30 cursor-pointer"
          >
            <Sparkles class="w-4 h-4" />
            <span>进入完整奖杯陈列馆</span>
            <ChevronRight class="w-4 h-4" />
          </button>
        </div>

        <!-- Progress Bar -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-xs font-bold">
            <span class="text-fg-3">总收集进度</span>
            <span class="font-mono text-purple-300 text-sm">
              {{ trophyStats.unlocked }} / {{ trophyStats.total }} ({{ trophyStats.percentage }}%)
            </span>
          </div>
          <div class="w-full h-3.5 rounded-full bg-sunken overflow-hidden p-0.5 border border-line">
            <div
              class="h-full rounded-full bg-gradient-to-r from-purple-500 via-accent to-accent-soft transition-all duration-700"
              :style="{ width: `${trophyStats.percentage}%` }"
            ></div>
          </div>
        </div>

        <!-- Tier breakdown badges -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2">
          <div class="p-4 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-between">
            <div>
              <div class="text-xs font-bold text-cyan-300">白金神级</div>
              <div class="text-lg font-black text-fg font-mono mt-0.5">{{ trophyStats.platinum }} / 1</div>
            </div>
            <Trophy class="w-5 h-5 text-cyan-300" />
          </div>

          <div class="p-4 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-between">
            <div>
              <div class="text-xs font-bold text-amber-300">金牌成就</div>
              <div class="text-lg font-black text-fg font-mono mt-0.5">{{ trophyStats.gold }} / 6</div>
            </div>
            <Trophy class="w-5 h-5 text-amber-300" />
          </div>

          <div class="p-4 rounded-2xl bg-slate-300/10 border border-slate-300/30 flex items-center justify-between">
            <div>
              <div class="text-xs font-bold text-slate-200">银牌荣耀</div>
              <div class="text-lg font-black text-fg font-mono mt-0.5">{{ trophyStats.silver }} / 20</div>
            </div>
            <Trophy class="w-5 h-5 text-slate-300" />
          </div>

          <div class="p-4 rounded-2xl bg-orange-500/10 border border-orange-500/30 flex items-center justify-between">
            <div>
              <div class="text-xs font-bold text-orange-300">铜牌印记</div>
              <div class="text-lg font-black text-fg font-mono mt-0.5">{{ trophyStats.bronze }} / 50</div>
            </div>
            <Trophy class="w-5 h-5 text-orange-400" />
          </div>
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
          <h3 class="text-base font-bold">清空使用统计确认</h3>
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
            确认清空
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
