<script setup lang="ts">
import { ref, computed } from 'vue';
import { Film, Clock, Heart, Star, Clapperboard, Languages } from '@lucide/vue';
import type { Movie } from '../types';
import { getImageUrl } from '../utils/image';

const props = withDefaults(defineProps<{
  movie: Movie;
  isFavorite?: boolean;
  /** 'grid' = poster tile, 'list' = compact horizontal info card. */
  view?: 'grid' | 'list';
  /** Which synopsis to prefer when both are available. */
  lang?: 'zh' | 'en';
  /**
   * Set when the caller knows a translation exists but does not hold the text —
   * the favorites page gets display rows from the server, not full movie records,
   * so it cannot derive the badge from `description_zh` the way other callers do.
   */
  translated?: boolean;
}>(), {
  view: 'grid',
  lang: 'zh',
});

const emit = defineEmits<{
  (e: 'select', movie: Movie): void;
  (e: 'toggle-favorite', movie: Movie): void;
}>();

const imgError = ref(false);

function handleImgError() {
  imgError.value = true;
}

/** Chinese when we have it and the user asked for it, otherwise the original. */
const shownDescription = computed(() => {
  const zh = props.movie.description_zh?.trim();
  if (props.lang === 'zh' && zh) return zh;
  return props.movie.description?.trim() || '';
});

const hasTranslation = computed(
  () => props.translated ?? Boolean(props.movie.description_zh?.trim()),
);
</script>

<template>
  <!-- ============ LIST VIEW: compact horizontal info card ============ -->
  <div
    v-if="view === 'list'"
    @click="emit('select', movie)"
    class="group relative flex gap-3 rounded-2xl bg-zinc-900/60 border border-zinc-800/80 hover:border-amber-500/50 hover:bg-zinc-900 hover:shadow-lg hover:shadow-amber-500/10 transition-all duration-200 overflow-hidden cursor-pointer select-none p-2.5"
  >
    <!-- Compact poster -->
    <div class="relative w-16 sm:w-20 shrink-0 aspect-[3/4] rounded-xl overflow-hidden bg-zinc-950">
      <img
        v-if="movie.cover_full && !imgError"
        :src="getImageUrl(movie.cover_full)"
        :alt="movie.title"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="handleImgError"
        class="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500 ease-out"
      />
      <div v-else class="w-full h-full flex items-center justify-center text-zinc-700">
        <Film class="w-6 h-6 stroke-1" />
      </div>
    </div>

    <!-- Info column -->
    <div class="flex-1 min-w-0 flex flex-col justify-between gap-1.5">
      <div class="min-w-0">
        <!-- Meta row -->
        <div class="flex flex-wrap items-center gap-1.5 text-[10px] mb-1">
          <span
            v-if="movie.release_year"
            class="px-1.5 py-0.5 rounded font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20"
          >
            {{ movie.release_year }}
          </span>
          <span
            v-if="movie.studio_name"
            class="px-1.5 py-0.5 rounded font-medium bg-zinc-800 text-zinc-300 border border-zinc-700/50 truncate max-w-[140px]"
            :title="movie.studio_name"
          >
            {{ movie.studio_name }}
          </span>
          <span
            v-if="movie.duration_mins"
            class="px-1.5 py-0.5 rounded text-zinc-400 bg-zinc-800/40 border border-white/5 flex items-center gap-1"
          >
            <Clock class="w-2.5 h-2.5" />
            {{ movie.duration_mins }}m
          </span>
          <span
            v-if="hasTranslation"
            class="px-1.5 py-0.5 rounded text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 flex items-center gap-1"
            title="已有中文简介"
          >
            <Languages class="w-2.5 h-2.5" />
            中
          </span>
        </div>

        <!-- Title -->
        <h3 class="text-sm font-semibold text-zinc-100 group-hover:text-amber-300 transition-colors line-clamp-1 leading-snug">
          {{ movie.title }}
        </h3>

        <!-- Synopsis preview: the whole reason list mode exists -->
        <p
          v-if="shownDescription"
          class="text-[11px] text-zinc-400 leading-relaxed line-clamp-2 mt-1"
        >
          {{ shownDescription }}
        </p>
        <div v-else class="text-[11px] text-zinc-600 italic mt-1">暂无简介</div>
      </div>

      <!-- Footer row: cast + personal annotations -->
      <div class="flex items-center justify-between gap-2 flex-wrap">
        <div class="flex items-center gap-1 flex-wrap min-w-0">
          <span
            v-for="p in (movie.performers || []).slice(0, 3)"
            :key="p.id"
            class="text-[10px] px-1.5 py-0.5 rounded bg-zinc-800/60 text-zinc-400 border border-zinc-700/30 truncate max-w-[90px]"
          >
            {{ p.name }}
          </span>
          <span
            v-if="(movie.performers?.length || 0) > 3"
            class="text-[10px] px-1 py-0.5 rounded bg-zinc-800/30 text-zinc-500"
          >
            +{{ (movie.performers?.length || 0) - 3 }}
          </span>
          <span v-if="movie.director_name" class="flex items-center gap-1 text-[10px] text-zinc-500 truncate max-w-[120px]" :title="`导演: ${movie.director_name}`">
            <Clapperboard class="w-2.5 h-2.5" />
            {{ movie.director_name }}
          </span>
        </div>

        <div class="flex items-center gap-1.5 shrink-0">
          <span
            v-if="movie.userData?.rating"
            class="flex items-center gap-0.5 text-[10px] font-bold text-amber-400 bg-amber-400/10 px-1.5 py-0.5 rounded border border-amber-400/20"
          >
            <Star class="w-2.5 h-2.5 fill-current" />
            {{ movie.userData.rating.toFixed(1) }}
          </span>
          <span
            v-for="t in (movie.userData?.tags || []).slice(0, 2)"
            :key="t.id"
            class="text-[9px] px-1.5 py-0.5 rounded font-medium border"
            :style="{ color: t.color, borderColor: `${t.color}40`, backgroundColor: `${t.color}15` }"
          >
            {{ t.name }}
          </span>

          <button
            @click.stop="emit('toggle-favorite', movie)"
            :class="[
              'w-6 h-6 rounded-full flex items-center justify-center transition-all duration-200',
              isFavorite
                ? 'bg-rose-500 text-white shadow shadow-rose-500/40'
                : 'bg-zinc-800/80 text-zinc-500 hover:text-rose-400 hover:bg-zinc-800'
            ]"
            title="收藏"
          >
            <Heart class="w-3 h-3" :fill="isFavorite ? 'currentColor' : 'none'" />
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- ============ GRID VIEW: poster tile ============ -->
  <div
    v-else
    @click="emit('select', movie)"
    class="group relative flex flex-col rounded-2xl bg-zinc-900/60 border border-zinc-800/80 hover:border-amber-500/50 hover:shadow-xl hover:shadow-amber-500/10 transition-all duration-300 overflow-hidden cursor-pointer select-none"
  >
    <!-- Pristine Poster Container (Clean Artwork - 100% Unobstructed) -->
    <div class="relative w-full aspect-[3/4] bg-zinc-950 overflow-hidden">
      <img
        v-if="movie.cover_full && !imgError"
        :src="getImageUrl(movie.cover_full)"
        :alt="movie.title"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="handleImgError"
        class="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500 ease-out"
      />
      <!-- Fallback Placeholder -->
      <div
        v-else
        class="w-full h-full flex flex-col items-center justify-center p-4 text-center bg-gradient-to-b from-zinc-900 to-zinc-950 text-zinc-600"
      >
        <Film class="w-10 h-10 mb-2 stroke-1 text-zinc-700" />
        <span class="text-xs font-medium line-clamp-2">{{ movie.title }}</span>
      </div>

      <!-- Chinese synopsis indicator (Top Left) -->
      <span
        v-if="hasTranslation"
        class="absolute top-2.5 left-2.5 px-1.5 py-0.5 rounded-md text-[9px] font-bold bg-emerald-500/90 text-black backdrop-blur-md z-10 flex items-center gap-0.5"
        title="已有中文简介"
      >
        <Languages class="w-2.5 h-2.5" />
        中
      </span>

      <!-- Clean Favorite Heart Button (Top Right, Subtle on hover or active) -->
      <button
        @click.stop="emit('toggle-favorite', movie)"
        :class="[
          'absolute top-2.5 right-2.5 w-7 h-7 rounded-full flex items-center justify-center backdrop-blur-md transition-all duration-200 z-10',
          isFavorite
            ? 'bg-rose-500 text-white shadow-lg shadow-rose-500/40 opacity-100'
            : 'bg-black/40 text-zinc-400 hover:text-rose-400 hover:bg-black/80 opacity-0 group-hover:opacity-100'
        ]"
        title="收藏"
      >
        <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
      </button>
    </div>

    <!-- Info Block Outside the Poster (All Metadata Cleanly Presented Below) -->
    <div class="p-3.5 flex-1 flex flex-col justify-between space-y-2.5">
      <div>
        <!-- Row 1: Studio, Year, Duration (Clean micro-badges outside poster) -->
        <div class="flex flex-wrap items-center gap-1.5 text-[10px] mb-1.5">
          <span
            v-if="movie.studio_name"
            class="px-1.5 py-0.5 rounded-md font-medium bg-zinc-800 text-zinc-300 border border-zinc-700/50 truncate max-w-[120px]"
            :title="movie.studio_name"
          >
            {{ movie.studio_name }}
          </span>
          <span
            v-if="movie.release_year"
            class="px-1.5 py-0.5 rounded-md font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20"
          >
            {{ movie.release_year }}
          </span>
          <span
            v-if="movie.duration_mins"
            class="px-1.5 py-0.5 rounded-md text-zinc-400 bg-zinc-800/40 border border-white/5 flex items-center gap-1"
          >
            <Clock class="w-2.5 h-2.5" />
            {{ movie.duration_mins }}m
          </span>
        </div>

        <!-- Row 2: Title -->
        <h3 class="text-sm font-semibold text-zinc-100 group-hover:text-amber-300 transition-colors line-clamp-1 leading-snug">
          {{ movie.title }}
        </h3>

        <!-- Row 3: Category or Director -->
        <div class="flex items-center gap-2 text-[11px] text-zinc-400 mt-1">
          <span v-if="movie.category" class="truncate">{{ movie.category }}</span>
          <span v-if="movie.director_name" class="flex items-center gap-1 text-zinc-500 text-[10px] truncate" :title="`导演: ${movie.director_name}`">
            <Clapperboard class="w-2.5 h-2.5 text-zinc-400" />
            {{ movie.director_name }}
          </span>
        </div>
      </div>

      <!-- Bottom Metadata: Cast preview + User Private Star Rating & Tags -->
      <div class="space-y-1.5 pt-1 border-t border-zinc-800/40">
        <!-- Cast preview chips -->
        <div v-if="movie.performers && movie.performers.length > 0" class="flex flex-wrap gap-1">
          <span
            v-for="p in movie.performers.slice(0, 2)"
            :key="p.id"
            class="text-[10px] px-1.5 py-0.5 rounded bg-zinc-800/60 text-zinc-400 border border-zinc-700/30 truncate max-w-[90px]"
          >
            {{ p.name }}
          </span>
          <span
            v-if="movie.performers.length > 2"
            class="text-[10px] px-1 py-0.5 rounded bg-zinc-800/30 text-zinc-500"
          >
            +{{ movie.performers.length - 2 }}
          </span>
        </div>

        <!-- User Private Rating & Custom Tags (If Annotated) -->
        <div v-if="movie.userData && (movie.userData.rating || (movie.userData.tags && movie.userData.tags.length > 0))" class="flex items-center gap-1.5 pt-0.5 flex-wrap">
          <span
            v-if="movie.userData.rating"
            class="flex items-center gap-0.5 text-[10px] font-bold text-amber-400 bg-amber-400/10 px-1.5 py-0.5 rounded border border-amber-400/20"
          >
            <Star class="w-2.5 h-2.5 fill-current" />
            {{ movie.userData.rating.toFixed(1) }}
          </span>
          <span
            v-for="t in (movie.userData.tags || []).slice(0, 2)"
            :key="t.id"
            class="text-[9px] px-1.5 py-0.5 rounded font-medium border"
            :style="{ color: t.color, borderColor: `${t.color}40`, backgroundColor: `${t.color}15` }"
          >
            {{ t.name }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

