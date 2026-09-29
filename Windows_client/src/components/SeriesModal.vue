<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { X, Film, ArrowRight, Building2, CheckCircle2, Heart } from '@lucide/vue';
import type { MovieSeriesResponse } from '../types';
import { getImageUrl } from '../utils/image';
import { titlePrimary, titleSecondary } from '../utils/bilingual';

const props = defineProps<{
  series: MovieSeriesResponse;
  currentMovieId?: number;
  zIndex?: number;
  isFavorite?: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select-movie', id: number): void;
  (e: 'toggle-favorite', rootTitle: string): void;
}>();

const brokenCovers = ref<Set<number>>(new Set());

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    emit('close');
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown);
});
</script>

<template>
  <Teleport to="body">
    <div
      class="fixed inset-0 flex items-center justify-center p-4 sm:p-6 bg-black/75 backdrop-blur-md animate-fade-in select-none z-[9999]"
      :style="{ zIndex: zIndex ? Math.max(zIndex, 9999) : 9999 }"
      @click.self="emit('close')"
    >
      <div
        class="bg-surface border border-line-strong rounded-3xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden animate-scale-up"
        role="dialog"
        aria-modal="true"
      >
        <!-- Modal Header -->
        <div class="px-6 py-4 border-b border-line flex items-center justify-between gap-4 bg-surface-2/40">
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-10 h-10 rounded-2xl bg-accent-fill/15 border border-accent-fill/30 flex items-center justify-center text-accent shrink-0">
              <Film class="w-5 h-5" />
            </div>
            <div class="min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <h2 class="text-base sm:text-lg font-bold text-fg tracking-tight truncate">
                  「{{ series.root_title }}」全系列作品
                </h2>
                <span class="text-xs px-2.5 py-0.5 rounded-full bg-accent-fill/15 text-accent font-semibold border border-accent-fill/30">
                  共 {{ series.items.length }} 部
                </span>
                <!-- Favorite Toggle Button -->
                <button
                  @click="emit('toggle-favorite', series.root_title)"
                  class="px-3 py-1 rounded-full text-xs font-bold border transition flex items-center gap-1.5 cursor-pointer shadow-xs"
                  :class="isFavorite
                    ? 'bg-rose-500/15 text-rose-400 border-rose-500/30 hover:bg-rose-500/25'
                    : 'bg-surface-2 text-fg-3 border-line hover:text-fg hover:border-line-strong'"
                  :title="isFavorite ? '已收藏此系列，点击取消' : '收藏此系列'"
                >
                  <Heart class="w-3.5 h-3.5" :class="isFavorite ? 'fill-rose-400 text-rose-400' : 'text-fg-4'" />
                  <span>{{ isFavorite ? '已收藏系列' : '收藏系列' }}</span>
                </button>
              </div>
              <div v-if="series.studio_name" class="flex items-center gap-1.5 text-xs text-fg-3 mt-0.5">
                <Building2 class="w-3.5 h-3.5 text-fg-4" />
                <span>出品片商：{{ series.studio_name }}</span>
              </div>
            </div>
          </div>

          <button
            @click="emit('close')"
            class="p-2 rounded-xl text-fg-3 hover:text-fg hover:bg-surface-2 border border-transparent hover:border-line transition shrink-0"
            title="关闭 (Esc)"
          >
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- Series Grid Gallery -->
        <div class="p-6 overflow-y-auto flex-1 overscroll-contain">
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
            <div
              v-for="movie in series.items"
              :key="movie.id"
              @click="emit('select-movie', movie.id)"
              class="group relative rounded-2xl overflow-hidden border transition-all duration-200 cursor-pointer flex flex-col justify-between"
              :class="movie.id === currentMovieId
                ? 'bg-accent-fill/10 border-accent shadow-lg shadow-accent-fill/10 ring-2 ring-accent/30'
                : 'bg-surface-2/40 border-line hover:border-line-strong hover:bg-surface-2 hover:shadow-xl hover:-translate-y-0.5'"
            >
              <!-- Poster Thumbnail -->
              <div class="aspect-[2/3] w-full overflow-hidden bg-surface-3 relative">
                <img
                  v-if="!brokenCovers.has(movie.id) && (movie.cover_full || movie.cover_icon)"
                  :src="getImageUrl(movie.cover_full || movie.cover_icon)"
                  :alt="movie.title"
                  class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  loading="lazy"
                  referrerpolicy="no-referrer"
                  @error="brokenCovers.add(movie.id)"
                />
                <div v-else class="w-full h-full flex items-center justify-center text-fg-5 bg-surface-2">
                  <Film class="w-8 h-8 stroke-1" />
                </div>

                <!-- Current Movie Marker Badge -->
                <div
                  v-if="movie.id === currentMovieId"
                  class="absolute top-2 left-2 px-2 py-0.5 rounded-lg bg-accent text-on-fill text-[10px] font-bold shadow-md flex items-center gap-1"
                >
                  <CheckCircle2 class="w-3 h-3 stroke-[3]" />
                  <span>当前浏览</span>
                </div>

                <!-- Year & Runtime Floating Badges -->
                <div class="absolute bottom-2 inset-x-2 flex items-center justify-between text-[10px] text-white font-medium drop-shadow-md">
                  <span v-if="movie.release_year" class="px-1.5 py-0.5 rounded bg-black/60 backdrop-blur-sm">
                    {{ movie.release_year }} 年
                  </span>
                  <span v-if="movie.duration_mins" class="px-1.5 py-0.5 rounded bg-black/60 backdrop-blur-sm ml-auto">
                    {{ movie.duration_mins }} 分钟
                  </span>
                </div>
              </div>

              <!-- Card Meta Content -->
              <div class="p-3 flex flex-col justify-between flex-1 gap-2">
                <div>
                  <h3 class="text-xs font-bold text-fg group-hover:text-accent transition-colors line-clamp-2 leading-snug">
                    {{ titlePrimary(movie) }}
                  </h3>
                  <div v-if="titleSecondary(movie)" class="text-[11px] text-fg-4 truncate mt-0.5 font-mono">
                    {{ titleSecondary(movie) }}
                  </div>
                </div>

                <div class="flex items-center justify-between text-[11px] text-fg-3 pt-1 border-t border-line/40">
                  <span class="truncate text-fg-4 text-[10px]">{{ movie.studio_name || '独立发行' }}</span>
                  <span class="text-accent group-hover:translate-x-0.5 transition-transform flex items-center text-[10px] font-medium">
                    详情 <ArrowRight class="w-3 h-3 ml-0.5" />
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Modal Footer -->
        <div class="px-6 py-3 border-t border-line bg-surface-2/30 flex items-center justify-between text-xs text-fg-4">
          <span>基于同片商主标题规范化抽取算法智能索引</span>
          <button
            @click="emit('close')"
            class="px-4 py-1.5 rounded-xl border border-line bg-surface hover:bg-surface-2 text-fg-2 transition"
          >
            关闭
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
