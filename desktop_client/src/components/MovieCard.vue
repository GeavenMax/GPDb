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
    class="group relative flex gap-3 rounded-2xl bg-surface/60 border border-line/80 hover:border-accent-fill/50 hover:bg-surface hover:shadow-lg hover:shadow-accent-fill/10 transition-all duration-200 overflow-hidden cursor-pointer select-none p-2.5"
  >
    <!-- Compact poster -->
    <div class="relative w-16 sm:w-20 shrink-0 aspect-[3/4] rounded-xl overflow-hidden bg-sunken">
      <img
        v-if="movie.cover_full && !imgError"
        :src="getImageUrl(movie.cover_full)"
        :alt="movie.title"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="handleImgError"
        class="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500 ease-out"
      />
      <div v-else class="w-full h-full flex items-center justify-center text-fg-5">
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
            class="px-1.5 py-0.5 rounded font-semibold bg-accent-fill/10 text-accent border border-accent-fill/20"
          >
            {{ movie.release_year }}
          </span>
          <span
            v-if="movie.studio_name"
            class="px-1.5 py-0.5 rounded font-medium bg-surface-2 text-fg-2 border border-line-strong/50 truncate max-w-[140px]"
            :title="movie.studio_name"
          >
            {{ movie.studio_name }}
          </span>
          <span
            v-if="movie.duration_mins"
            class="px-1.5 py-0.5 rounded text-fg-3 bg-surface-2/40 border border-fg/5 flex items-center gap-1"
          >
            <Clock class="w-2.5 h-2.5" />
            {{ movie.duration_mins }}m
          </span>
          <span
            v-if="hasTranslation"
            class="px-1.5 py-0.5 rounded text-success bg-success-fill/10 border border-success-fill/20 flex items-center gap-1"
            title="已有中文简介"
          >
            <Languages class="w-2.5 h-2.5" />
            中
          </span>
        </div>

        <!-- Title -->
        <h3 class="text-sm font-semibold text-fg group-hover:text-accent-soft transition-colors line-clamp-1 leading-snug">
          {{ movie.title }}
        </h3>

        <!-- Synopsis preview: the whole reason list mode exists -->
        <p
          v-if="shownDescription"
          class="text-[11px] text-fg-3 leading-relaxed line-clamp-2 mt-1"
        >
          {{ shownDescription }}
        </p>
        <div v-else class="text-[11px] text-fg-5 italic mt-1">暂无简介</div>
      </div>

      <!-- Footer row: cast + personal annotations -->
      <div class="flex items-center justify-between gap-2 flex-wrap">
        <div class="flex items-center gap-1 flex-wrap min-w-0">
          <span
            v-for="p in (movie.performers || []).slice(0, 3)"
            :key="p.id"
            class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2/60 text-fg-3 border border-line-strong/30 truncate max-w-[90px]"
          >
            {{ p.name }}
          </span>
          <span
            v-if="(movie.performers?.length || 0) > 3"
            class="text-[10px] px-1 py-0.5 rounded bg-surface-2/30 text-fg-4"
          >
            +{{ (movie.performers?.length || 0) - 3 }}
          </span>
          <span v-if="movie.director_name" class="flex items-center gap-1 text-[10px] text-fg-4 truncate max-w-[120px]" :title="`导演: ${movie.director_name}`">
            <Clapperboard class="w-2.5 h-2.5" />
            {{ movie.director_name }}
          </span>
        </div>

        <div class="flex items-center gap-1.5 shrink-0">
          <span
            v-if="movie.userData?.rating"
            class="flex items-center gap-0.5 text-[10px] font-bold text-accent bg-accent/10 px-1.5 py-0.5 rounded border border-accent/20"
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
                ? 'bg-danger-fill text-on-danger shadow shadow-danger-fill/40'
                : 'bg-surface-2/80 text-fg-4 hover:text-danger hover:bg-surface-2'
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
    class="group relative flex flex-col rounded-2xl bg-surface/60 border border-line/80 hover:border-accent-fill/50 hover:shadow-xl hover:shadow-accent-fill/10 transition-all duration-300 overflow-hidden cursor-pointer select-none"
  >
    <!-- Pristine Poster Container (Clean Artwork - 100% Unobstructed) -->
    <div class="relative w-full aspect-[3/4] bg-sunken overflow-hidden">
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
        class="w-full h-full flex flex-col items-center justify-center p-4 text-center bg-gradient-to-b from-surface to-sunken text-fg-5"
      >
        <Film class="w-10 h-10 mb-2 stroke-1 text-fg-5" />
        <span class="text-xs font-medium line-clamp-2">{{ movie.title }}</span>
      </div>

      <!-- Chinese synopsis indicator (Top Left) -->
      <span
        v-if="hasTranslation"
        class="absolute top-2.5 left-2.5 px-1.5 py-0.5 rounded-md text-[9px] font-bold bg-success-fill/90 text-on-fill backdrop-blur-md z-10 flex items-center gap-0.5"
        title="已有中文简介"
      >
        <Languages class="w-2.5 h-2.5" />
        中
      </span>

      <!-- Clean Favorite Heart Button (Top Right, Subtle on hover or active) -->
      <button
        @click.stop="emit('toggle-favorite', movie)"
        :class="[
          'on-scrim absolute top-2.5 right-2.5 w-7 h-7 rounded-full flex items-center justify-center backdrop-blur-md transition-all duration-200 z-10',
          isFavorite
            ? 'bg-danger-fill text-on-danger shadow-lg shadow-danger-fill/40 opacity-100'
            : 'bg-scrim/40 text-fg-3 hover:text-danger hover:bg-scrim/80 opacity-0 group-hover:opacity-100'
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
            class="px-1.5 py-0.5 rounded-md font-medium bg-surface-2 text-fg-2 border border-line-strong/50 truncate max-w-[120px]"
            :title="movie.studio_name"
          >
            {{ movie.studio_name }}
          </span>
          <span
            v-if="movie.release_year"
            class="px-1.5 py-0.5 rounded-md font-semibold bg-accent-fill/10 text-accent border border-accent-fill/20"
          >
            {{ movie.release_year }}
          </span>
          <span
            v-if="movie.duration_mins"
            class="px-1.5 py-0.5 rounded-md text-fg-3 bg-surface-2/40 border border-fg/5 flex items-center gap-1"
          >
            <Clock class="w-2.5 h-2.5" />
            {{ movie.duration_mins }}m
          </span>
        </div>

        <!-- Row 2: Title -->
        <h3 class="text-sm font-semibold text-fg group-hover:text-accent-soft transition-colors line-clamp-1 leading-snug">
          {{ movie.title }}
        </h3>

        <!-- Row 3: Category or Director -->
        <div class="flex items-center gap-2 text-[11px] text-fg-3 mt-1">
          <span v-if="movie.category" class="truncate">{{ movie.category }}</span>
          <span v-if="movie.director_name" class="flex items-center gap-1 text-fg-4 text-[10px] truncate" :title="`导演: ${movie.director_name}`">
            <Clapperboard class="w-2.5 h-2.5 text-fg-3" />
            {{ movie.director_name }}
          </span>
        </div>
      </div>

      <!-- Bottom Metadata: Cast preview + User Private Star Rating & Tags -->
      <div class="space-y-1.5 pt-1 border-t border-line/40">
        <!-- Cast preview chips -->
        <div v-if="movie.performers && movie.performers.length > 0" class="flex flex-wrap gap-1">
          <span
            v-for="p in movie.performers.slice(0, 2)"
            :key="p.id"
            class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2/60 text-fg-3 border border-line-strong/30 truncate max-w-[90px]"
          >
            {{ p.name }}
          </span>
          <span
            v-if="movie.performers.length > 2"
            class="text-[10px] px-1 py-0.5 rounded bg-surface-2/30 text-fg-4"
          >
            +{{ movie.performers.length - 2 }}
          </span>
        </div>

        <!-- User Private Rating & Custom Tags (If Annotated) -->
        <div v-if="movie.userData && (movie.userData.rating || (movie.userData.tags && movie.userData.tags.length > 0))" class="flex items-center gap-1.5 pt-0.5 flex-wrap">
          <span
            v-if="movie.userData.rating"
            class="flex items-center gap-0.5 text-[10px] font-bold text-accent bg-accent/10 px-1.5 py-0.5 rounded border border-accent/20"
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

