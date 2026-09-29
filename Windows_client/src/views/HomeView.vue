<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import {
  Sparkles, Calendar, Users, Film, Clapperboard, Building2,
  Star, RefreshCw, ChevronRight, ChevronLeft, ArrowRight, Layers
} from '@lucide/vue';
import { api } from '../api';
import { getImageUrl } from '../utils/image';
import { activeAiReport } from '../services/aiAnalysis';
import SeriesCollageCover from '../components/SeriesCollageCover.vue';
import type { HomeFeedData, HomeSpotlightMovie, AppTab, SeriesCollectionItem, Movie } from '../types';

const emit = defineEmits<{
  (e: 'open-movie', id: number): void;
  (e: 'open-episode', id: number): void;
  (e: 'open-performer', id: number): void;
  (e: 'open-series', rootTitle: string, studioName?: string | null): void;
  (e: 'change-tab', tab: AppTab): void;
}>();

const isLoading = ref(true);
const feed = ref<HomeFeedData>({
  spotlight_movies: [],
  on_this_day: [],
  star_spotlight: [],
  total_movies: 0,
  total_episodes: 0,
  total_performers: 0,
  total_studios: 0,
});

const spotlightIndex = ref(0);
const currentSpotlight = computed<HomeSpotlightMovie | null>(() => {
  if (feed.value.spotlight_movies.length === 0) return null;
  return feed.value.spotlight_movies[spotlightIndex.value] || feed.value.spotlight_movies[0];
});

let autoplayTimer: ReturnType<typeof setInterval> | null = null;

function startAutoplay() {
  if (autoplayTimer) return;
  autoplayTimer = setInterval(() => {
    nextSpotlight();
  }, 6000);
}

function pauseAutoplay() {
  if (autoplayTimer) {
    clearInterval(autoplayTimer);
    autoplayTimer = null;
  }
}

function resumeAutoplay() {
  if (!autoplayTimer && feed.value.spotlight_movies.length > 1) {
    startAutoplay();
  }
}

function nextSpotlight() {
  if (feed.value.spotlight_movies.length > 0) {
    spotlightIndex.value = (spotlightIndex.value + 1) % feed.value.spotlight_movies.length;
  }
}

function prevSpotlight() {
  if (feed.value.spotlight_movies.length > 0) {
    spotlightIndex.value =
      (spotlightIndex.value - 1 + feed.value.spotlight_movies.length) % feed.value.spotlight_movies.length;
  }
}

function handleManualNext() {
  nextSpotlight();
  pauseAutoplay();
  startAutoplay();
}

function handleManualPrev() {
  prevSpotlight();
  pauseAutoplay();
  startAutoplay();
}

function handleSelectSpotlight(idx: number) {
  spotlightIndex.value = idx;
  pauseAutoplay();
  startAutoplay();
}

const todayFormatted = computed(() => {
  const now = new Date();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${m}月${d}日`;
});

const brokenImages = ref<Set<string>>(new Set());
function handleImgError(key: string) {
  brokenImages.value.add(key);
}

const starSpotlightWithAvatars = computed(() => {
  return feed.value.star_spotlight.filter(
    p => p.image_url && p.image_url.trim() && !brokenImages.value.has(String(p.id))
  );
});

const seriesList = ref<SeriesCollectionItem[]>([]);
const luckyMovies = ref<Movie[]>([]);
const isLuckyLoading = ref(false);

async function loadSeriesList() {
  try {
    const res = await api.getSeriesCollections(undefined, undefined, 'count_desc', 1, 6);
    seriesList.value = res.items || [];
  } catch (err) {
    console.error('Failed to load series collections for home', err);
  }
}

async function fetchLuckyMovies() {
  isLuckyLoading.value = true;
  try {
    const total = feed.value.total_movies || 60000;
    const maxPage = Math.max(1, Math.min(Math.floor(total / 6), 500));
    const randomPage = Math.floor(Math.random() * maxPage) + 1;
    const res = await api.getMovies({ sortBy: 'id_desc' }, randomPage, 6);
    if (res.items && res.items.length > 0) {
      luckyMovies.value = res.items;
    }
  } catch (err) {
    console.error('Failed to fetch lucky movies', err);
  } finally {
    isLuckyLoading.value = false;
  }
}

async function loadFeed() {
  isLoading.value = true;
  try {
    const now = new Date();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    const data = await api.getHomeFeed(`${mm}-${dd}`);
    feed.value = data;
  } catch (err) {
    console.error('Failed to load home feed', err);
  } finally {
    isLoading.value = false;
  }
}

onMounted(async () => {
  await loadFeed();
  if (feed.value.spotlight_movies.length > 1) {
    startAutoplay();
  }
  loadSeriesList();
  fetchLuckyMovies();
});

onBeforeUnmount(() => {
  pauseAutoplay();
});
</script>

<template>
  <div class="space-y-10 max-w-6xl mx-auto pb-20 animate-fade-in text-fg">
    <!-- 1. Hero Spotlight Banner (镇馆之选 / 焦点自动轮播) -->
    <section
      v-if="currentSpotlight"
      @mouseenter="pauseAutoplay"
      @mouseleave="resumeAutoplay"
      class="relative rounded-3xl overflow-hidden border border-line/80 shadow-2xl bg-surface group"
    >
      <!-- Immersive Backdrop Blur -->
      <div
        class="absolute inset-0 bg-cover bg-center opacity-25 blur-3xl scale-110 pointer-events-none transition-all duration-700"
        :style="{ backgroundImage: `url(${getImageUrl(currentSpotlight.cover_full)})` }"
      ></div>
      <div class="absolute inset-0 bg-gradient-to-t from-bg via-bg/80 to-transparent pointer-events-none"></div>
      <div class="absolute inset-0 bg-gradient-to-r from-bg via-bg/60 to-transparent pointer-events-none"></div>

      <!-- Main Spotlight Content -->
      <div class="relative z-10 p-6 md:p-8 flex flex-col md:flex-row items-center gap-6 md:gap-8">
        <!-- Cover Art with 3D Float Effect -->
        <div
          @click="emit('open-movie', currentSpotlight.id)"
          class="relative w-44 md:w-56 aspect-[2/3] shrink-0 rounded-2xl overflow-hidden shadow-2xl border border-white/10 cursor-pointer transition-transform duration-300 hover:scale-[1.03] group/poster"
        >
          <img
            :src="getImageUrl(currentSpotlight.cover_full)"
            :alt="currentSpotlight.title"
            class="w-full h-full object-cover"
            loading="eager"
          />
          <div class="absolute inset-0 bg-black/20 opacity-0 group-hover/poster:opacity-100 transition flex items-center justify-center">
            <span class="px-3 py-1.5 rounded-xl bg-black/70 backdrop-blur-md text-xs font-bold text-white border border-white/20">
              查看详情
            </span>
          </div>
        </div>

        <!-- Film Metadata & Synopsis -->
        <div class="flex-1 space-y-3.5 text-center md:text-left">
          <!-- Top Badges -->
          <div class="flex items-center justify-center md:justify-start gap-2 flex-wrap">
            <span class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-black bg-accent-fill/15 text-accent border border-accent/20">
              <Sparkles class="w-3.5 h-3.5" />
              <span>焦点推荐</span>
            </span>
            <span v-if="currentSpotlight.release_year" class="px-2 py-0.5 rounded-full text-xs font-mono font-bold bg-surface-2 text-fg-3 border border-line">
              {{ currentSpotlight.release_year }}
            </span>
            <span v-if="currentSpotlight.studio_name" class="px-2 py-0.5 rounded-full text-xs font-bold bg-surface-2 text-fg-3 border border-line">
              {{ currentSpotlight.studio_name }}
            </span>
            <span v-if="currentSpotlight.director_name" class="px-2 py-0.5 rounded-full text-xs bg-surface-2 text-fg-4 border border-line">
              导演: {{ currentSpotlight.director_name }}
            </span>
            <span v-if="currentSpotlight.rating" class="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-xs font-bold bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Star class="w-3 h-3 fill-amber-400" />
              <span>{{ currentSpotlight.rating }}</span>
            </span>
          </div>

          <!-- Titles -->
          <div class="space-y-1">
            <h2
              @click="emit('open-movie', currentSpotlight.id)"
              class="text-2xl md:text-3xl font-black text-fg tracking-tight cursor-pointer hover:text-accent transition"
            >
              {{ currentSpotlight.title_zh || currentSpotlight.title }}
            </h2>
            <p v-if="currentSpotlight.title_zh && currentSpotlight.title !== currentSpotlight.title_zh" class="text-sm text-fg-4 font-serif italic">
              {{ currentSpotlight.title }}
            </p>
          </div>

          <!-- Synopsis Excerpt -->
          <p class="text-xs md:text-sm text-fg-3 leading-relaxed line-clamp-3 md:line-clamp-4 max-w-2xl">
            {{ currentSpotlight.description_zh || currentSpotlight.description || '暂无详细剧情简介。' }}
          </p>

          <!-- Buttons & Switcher -->
          <div class="flex items-center justify-center md:justify-start gap-3 pt-2">
            <button
              @click="emit('open-movie', currentSpotlight.id)"
              class="px-5 py-2.5 rounded-xl bg-accent-fill hover:bg-accent-fill/90 text-on-fill font-bold text-xs flex items-center gap-2 shadow-lg shadow-accent/20 transition cursor-pointer"
            >
              <span>立即探索</span>
              <ArrowRight class="w-3.5 h-3.5" />
            </button>

            <button
              @click="handleManualNext"
              class="px-4 py-2.5 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 border border-line text-xs font-bold flex items-center gap-1.5 transition cursor-pointer"
              title="切换下一部焦点推荐"
            >
              <RefreshCw class="w-3.5 h-3.5" />
              <span>换一部</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Navigation Arrows -->
      <button
        @click="handleManualPrev"
        class="absolute left-3 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/40 hover:bg-black/70 text-white backdrop-blur-md border border-white/10 transition opacity-0 group-hover:opacity-100 z-20 cursor-pointer"
        title="上一部"
      >
        <ChevronLeft class="w-4 h-4" />
      </button>
      <button
        @click="handleManualNext"
        class="absolute right-3 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/40 hover:bg-black/70 text-white backdrop-blur-md border border-white/10 transition opacity-0 group-hover:opacity-100 z-20 cursor-pointer"
        title="下一部"
      >
        <ChevronRight class="w-4 h-4" />
      </button>

      <!-- Carousel Progress Dots -->
      <div v-if="feed.spotlight_movies.length > 1" class="absolute bottom-3 right-6 flex items-center gap-1.5 z-20">
        <button
          v-for="(_, idx) in feed.spotlight_movies"
          :key="idx"
          @click="handleSelectSpotlight(idx)"
          :class="[
            'h-1.5 rounded-full transition-all cursor-pointer',
            spotlightIndex === idx ? 'w-5 bg-accent' : 'w-1.5 bg-fg-5 hover:bg-fg-3'
          ]"
        ></button>
      </div>
    </section>

    <!-- 2. AI Personalized Taste Curation Banner -->
    <section class="rounded-3xl p-6 bg-gradient-to-r from-indigo-950/30 via-purple-950/20 to-surface border border-indigo-500/20 shadow-sm relative overflow-hidden">
      <div class="flex flex-col md:flex-row items-start md:items-center justify-between gap-5 relative z-10">
        <div class="space-y-2">
          <div class="flex items-center gap-2">
            <div class="p-2 rounded-xl bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
              <Sparkles class="w-4 h-4" />
            </div>
            <span class="text-xs font-black tracking-wider uppercase text-indigo-400">
              AI 专属定制导赏
            </span>
            <span v-if="activeAiReport" class="text-xs px-2.5 py-0.5 rounded-full bg-indigo-500/20 text-indigo-200 font-extrabold border border-indigo-500/30">
              {{ activeAiReport.archetype }}
            </span>
          </div>

          <p v-if="activeAiReport" class="text-sm font-medium text-fg-2 italic">
            “{{ activeAiReport.summary }}”
          </p>
          <p v-else class="text-xs text-fg-3">
            基于您在本地媒体库中的真实收藏、标记与观影记录，大模型可深度析构出专属影视美学画像与冷门寻宝指南。
          </p>

          <!-- Keywords tags -->
          <div v-if="activeAiReport?.keywords?.length" class="flex items-center gap-2 flex-wrap pt-1">
            <span
              v-for="kw in activeAiReport.keywords"
              :key="kw"
              class="text-[11px] px-2.5 py-0.5 rounded-lg bg-surface-2 text-indigo-200 border border-indigo-500/30 font-medium"
            >
              #{{ kw }}
            </span>
          </div>
        </div>

        <button
          @click="emit('change-tab', 'plugins')"
          class="px-4 py-2.5 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold text-xs flex items-center gap-2 shadow-md transition shrink-0 cursor-pointer"
        >
          <span>{{ activeAiReport ? '查看完整画像与探索指南' : '一键开启 AI 影迷偏好画像' }}</span>
          <ChevronRight class="w-4 h-4" />
        </button>
      </div>
    </section>

    <!-- 3. 往年今日 (On This Day in History) -->
    <section v-if="feed.on_this_day.length > 0" class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2.5">
          <div class="p-2 rounded-xl bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <Calendar class="w-4 h-4" />
          </div>
          <div>
            <h3 class="text-base font-extrabold text-fg tracking-tight flex items-center gap-2">
              <span>往年今日 · 经典首映</span>
              <span class="text-xs px-2 py-0.5 rounded-full bg-rose-500/15 text-rose-300 font-mono font-bold">
                {{ todayFormatted }}
              </span>
            </h3>
            <p class="text-xs text-fg-4 mt-0.5">历史上的今天在各大厂牌首发或收录的经典篇章</p>
          </div>
        </div>
      </div>

      <!-- Enlarged 16:10 Grid with 2-line title wrapping -->
      <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
        <div
          v-for="item in feed.on_this_day.slice(0, 8)"
          :key="item.episode_id"
          @click="item.movie_id ? emit('open-movie', item.movie_id) : emit('open-episode', item.episode_id)"
          class="group rounded-2xl bg-surface border border-line hover:border-accent/40 p-3.5 space-y-3 transition duration-200 hover:-translate-y-1 shadow-sm hover:shadow-md cursor-pointer flex flex-col justify-between"
        >
          <div class="relative aspect-[16/10] rounded-xl overflow-hidden bg-surface-2 border border-line/40">
            <img
              :src="getImageUrl(item.cover_full)"
              :alt="item.episode_title"
              class="w-full h-full object-cover group-hover:scale-105 transition duration-300"
              loading="lazy"
            />
          </div>

          <div class="space-y-1.5 flex-1 flex flex-col justify-between">
            <h4
              class="text-sm font-bold text-fg line-clamp-2 min-h-[2.5rem] leading-snug group-hover:text-accent transition"
              :title="item.episode_title || item.movie_title_zh || item.movie_title || undefined"
            >
              {{ item.episode_title || item.movie_title_zh || item.movie_title || '分集场景' }}
            </h4>
            <div class="flex items-center justify-between text-xs text-fg-4 pt-1.5 border-t border-line/40">
              <span class="truncate max-w-[140px] font-medium">{{ item.studio_name || '独立制作' }}</span>
              <span class="font-mono text-[11px] text-fg-3">{{ item.release_date }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 4. 今日星光 / 演员焦点 (Performer Spotlight) -->
    <section v-if="starSpotlightWithAvatars.length > 0" class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2.5">
          <div class="p-2 rounded-xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
            <Users class="w-4 h-4" />
          </div>
          <div>
            <h3 class="text-base font-extrabold text-fg tracking-tight">
              今日星光 · 标志面孔
            </h3>
            <p class="text-xs text-fg-4 mt-0.5">本地影库中备受瞩目的传奇演员与专属全集档案</p>
          </div>
        </div>

        <button
          @click="emit('change-tab', 'performers')"
          class="text-xs text-accent hover:underline flex items-center gap-1 font-bold cursor-pointer"
        >
          <span>查看全部演员</span>
          <ChevronRight class="w-3.5 h-3.5" />
        </button>
      </div>

      <!-- Performers Avatars Grid: enlarged avatars, 2-line name -->
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
        <div
          v-for="perf in starSpotlightWithAvatars"
          :key="perf.id"
          @click="emit('open-performer', perf.id)"
          class="group p-4 rounded-2xl bg-surface border border-line hover:border-accent/40 text-center space-y-3 transition duration-200 hover:-translate-y-1 shadow-sm hover:shadow-md cursor-pointer flex flex-col items-center justify-between"
        >
          <!-- Performer Avatar -->
          <div class="relative w-24 h-24 sm:w-28 sm:h-28 mx-auto rounded-full overflow-hidden border-2 border-line group-hover:border-accent transition duration-200 shadow-md bg-surface-2 shrink-0">
            <img
              :src="getImageUrl(perf.image_url)"
              :alt="perf.name"
              class="w-full h-full object-cover group-hover:scale-110 transition duration-300"
              loading="lazy"
              @error="handleImgError(String(perf.id))"
            />
          </div>

          <div class="space-y-1 w-full">
            <h4
              class="text-sm font-bold text-fg line-clamp-2 min-h-[2.5rem] leading-snug group-hover:text-accent transition px-1"
              :title="perf.name"
            >
              {{ perf.name }}
            </h4>
            <p class="text-xs text-fg-4 font-mono font-medium">
              {{ perf.works_count }} 部作品
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- 4.5. 经典系列大放送 (Series & Franchises Showcase) -->
    <section v-if="seriesList.length > 0" class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2.5">
          <div class="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
            <Layers class="w-4 h-4" />
          </div>
          <div>
            <h3 class="text-base font-extrabold text-fg tracking-tight">
              经典系列大放送 · 连贯篇章
            </h3>
            <p class="text-xs text-fg-4 mt-0.5">跨越多年的长篇经典企划，尽览多部曲全貌</p>
          </div>
        </div>

        <button
          @click="emit('change-tab', 'movies')"
          class="text-xs text-accent hover:underline flex items-center gap-1 font-bold cursor-pointer"
        >
          <span>浏览全量影库</span>
          <ChevronRight class="w-3.5 h-3.5" />
        </button>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-4">
        <div
          v-for="s in seriesList"
          :key="s.id"
          @click="emit('open-series', s.root_title, s.studio_name)"
          class="group rounded-2xl bg-surface border border-line hover:border-accent/40 p-2.5 space-y-2.5 transition duration-200 hover:-translate-y-1 shadow-sm hover:shadow-md cursor-pointer flex flex-col justify-between"
        >
          <div class="aspect-[2/3] w-full rounded-xl overflow-hidden bg-surface-2 relative border border-line/40">
            <SeriesCollageCover
              :covers="s.sample_covers"
              :title="s.root_title"
              aspect-ratio="h-full w-full"
              class="w-full h-full group-hover:scale-105 transition-transform duration-300"
            />
            <div class="absolute bottom-2 left-2 px-2 py-0.5 rounded-lg bg-black/75 backdrop-blur-md text-[10px] font-bold text-accent border border-accent/30 font-mono">
              {{ s.movie_count }} 部作品
            </div>
          </div>

          <div class="space-y-1 px-1">
            <h4
              class="text-xs font-bold text-fg line-clamp-1 group-hover:text-accent transition"
              :title="s.root_title"
            >
              {{ s.root_title }}
            </h4>
            <div class="flex items-center justify-between text-[11px] text-fg-4">
              <span class="truncate max-w-[90px]">{{ s.studio_name || '精选厂牌' }}</span>
              <span v-if="s.year_start && s.year_end" class="font-mono text-[10px]">
                {{ s.year_start === s.year_end ? s.year_start : `${s.year_start}-${s.year_end}` }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 4.6. 随心探索 · 盲盒发现 (Lucky Discovery) -->
    <section v-if="luckyMovies.length > 0" class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2.5">
          <div class="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
            <Sparkles class="w-4 h-4" />
          </div>
          <div>
            <h3 class="text-base font-extrabold text-fg tracking-tight">
              随心探索 · 盲盒发现
            </h3>
            <p class="text-xs text-fg-4 mt-0.5">漫无目的时，不妨从浩瀚影海中打捞几颗遗落的珍珠</p>
          </div>
        </div>

        <button
          @click="fetchLuckyMovies"
          :disabled="isLuckyLoading"
          class="px-3.5 py-1.5 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 border border-line text-xs font-bold flex items-center gap-1.5 transition cursor-pointer disabled:opacity-50"
        >
          <RefreshCw class="w-3.5 h-3.5" :class="{ 'animate-spin': isLuckyLoading }" />
          <span>换一批</span>
        </button>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-4">
        <div
          v-for="m in luckyMovies"
          :key="m.id"
          @click="emit('open-movie', m.id)"
          class="group rounded-2xl bg-surface border border-line hover:border-accent/40 p-2.5 space-y-2.5 transition duration-200 hover:-translate-y-1 shadow-sm hover:shadow-md cursor-pointer flex flex-col justify-between"
        >
          <div class="aspect-[2/3] w-full rounded-xl overflow-hidden bg-surface-2 relative border border-line/40">
            <img
              :src="getImageUrl(m.cover_full || m.cover_icon)"
              :alt="m.title"
              class="w-full h-full object-cover group-hover:scale-105 transition duration-300"
              loading="lazy"
            />
            <div v-if="m.rating" class="absolute top-2 right-2 px-1.5 py-0.5 rounded-md bg-black/70 backdrop-blur-md text-[10px] font-bold text-amber-400 flex items-center gap-0.5 border border-amber-400/20">
              <Star class="w-2.5 h-2.5 fill-amber-400" />
              <span>{{ m.rating }}</span>
            </div>
            <div v-if="m.release_year" class="absolute bottom-2 left-2 px-1.5 py-0.5 rounded-md bg-black/75 backdrop-blur-md text-[10px] font-mono font-bold text-fg-3 border border-white/10">
              {{ m.release_year }}
            </div>
          </div>

          <div class="space-y-1 px-1">
            <h4
              class="text-xs font-bold text-fg line-clamp-1 group-hover:text-accent transition"
              :title="m.title_zh || m.title"
            >
              {{ m.title_zh || m.title }}
            </h4>
            <div class="flex items-center justify-between text-[11px] text-fg-4">
              <span class="truncate max-w-[100px]">{{ m.studio_name || '独立作品' }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 5. 全库总览与探索捷径 (Jump Back In / Quick Library Access) -->
    <section class="space-y-4 pt-2">
      <div class="flex items-center gap-2.5">
        <div class="p-2 rounded-xl bg-accent-fill/10 text-accent border border-accent/20">
          <Film class="w-4 h-4" />
        </div>
        <div>
          <h3 class="text-base font-extrabold text-fg tracking-tight">
            影库纵览与快捷探索
          </h3>
          <p class="text-xs text-fg-4 mt-0.5">离线数据中心完整索引，点击即可直达各个专题媒体库</p>
        </div>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <!-- Movies Tile -->
        <div
          @click="emit('change-tab', 'movies')"
          class="p-5 rounded-2xl bg-surface/80 border border-line hover:border-accent/50 hover:bg-surface transition duration-200 shadow-sm hover:shadow-md cursor-pointer group space-y-2"
        >
          <div class="flex items-center justify-between">
            <span class="p-2 rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20">
              <Film class="w-5 h-5" />
            </span>
            <ChevronRight class="w-4 h-4 text-fg-4 group-hover:text-accent transition group-hover:translate-x-0.5" />
          </div>
          <div>
            <div class="text-2xl font-black text-fg font-mono tracking-tight">
              {{ feed.total_movies.toLocaleString() }}
            </div>
            <div class="text-xs text-fg-4 font-bold mt-0.5">
              精选影视长片
            </div>
          </div>
        </div>

        <!-- Episodes Tile -->
        <div
          @click="emit('change-tab', 'episodes')"
          class="p-5 rounded-2xl bg-surface/80 border border-line hover:border-accent/50 hover:bg-surface transition duration-200 shadow-sm hover:shadow-md cursor-pointer group space-y-2"
        >
          <div class="flex items-center justify-between">
            <span class="p-2 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <Clapperboard class="w-5 h-5" />
            </span>
            <ChevronRight class="w-4 h-4 text-fg-4 group-hover:text-accent transition group-hover:translate-x-0.5" />
          </div>
          <div>
            <div class="text-2xl font-black text-fg font-mono tracking-tight">
              {{ feed.total_episodes.toLocaleString() }}
            </div>
            <div class="text-xs text-fg-4 font-bold mt-0.5">
              独立分集与场景
            </div>
          </div>
        </div>

        <!-- Performers Tile -->
        <div
          @click="emit('change-tab', 'performers')"
          class="p-5 rounded-2xl bg-surface/80 border border-line hover:border-accent/50 hover:bg-surface transition duration-200 shadow-sm hover:shadow-md cursor-pointer group space-y-2"
        >
          <div class="flex items-center justify-between">
            <span class="p-2 rounded-xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Users class="w-4 h-4" />
            </span>
            <ChevronRight class="w-4 h-4 text-fg-4 group-hover:text-accent transition group-hover:translate-x-0.5" />
          </div>
          <div>
            <div class="text-2xl font-black text-fg font-mono tracking-tight">
              {{ feed.total_performers.toLocaleString() }}
            </div>
            <div class="text-xs text-fg-4 font-bold mt-0.5">
              入库演员阵容
            </div>
          </div>
        </div>

        <!-- Studios Tile -->
        <div
          @click="emit('change-tab', 'studios')"
          class="p-5 rounded-2xl bg-surface/80 border border-line hover:border-accent/50 hover:bg-surface transition duration-200 shadow-sm hover:shadow-md cursor-pointer group space-y-2"
        >
          <div class="flex items-center justify-between">
            <span class="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
              <Building2 class="w-5 h-5" />
            </span>
            <ChevronRight class="w-4 h-4 text-fg-4 group-hover:text-accent transition group-hover:translate-x-0.5" />
          </div>
          <div>
            <div class="text-2xl font-black text-fg font-mono tracking-tight">
              {{ feed.total_studios.toLocaleString() }}
            </div>
            <div class="text-xs text-fg-4 font-bold mt-0.5">
              制片厂牌与品牌
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
