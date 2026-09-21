<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { X, Film, Layers, Heart, Loader2 } from '@lucide/vue';
import type { Movie, StudioWorks, FavoriteType } from '../types';
import MovieCard from './MovieCard.vue';
import EpisodeRow from './EpisodeRow.vue';
import { claimEscape } from '../utils/escape';

/**
 * A studio, as the library grid and the favorites page know it.
 *
 * Deliberately not the `StudioSummary` type: the favorites page only carries a
 * name and a works count, and a studio with no row on screen has no counts at all,
 * so both fields stay optional and the header falls back to the loaded works.
 */
interface StudioRef {
  name: string;
  works_count?: number;
  episodes_count?: number;
}

const props = defineProps<{
  studio: StudioRef | null;
  /** Films and episodes, fetched by the parent; null while loading. */
  works: StudioWorks | null;
  loading?: boolean;
  /** Synopsis language for the embedded movie cards. */
  lang?: 'zh' | 'en';
  /** Stacking order supplied by the parent; see MovieDetailModal. */
  zIndex?: number;
  /** Topmost view owns Escape — see MovieDetailModal. */
  isTop?: boolean;
  /** Whether this studio is favorited. */
  isFavorite?: boolean;
  /** Favorited keys by type — used for the movie and episode hearts in this modal. */
  favoriteKeys?: Partial<Record<FavoriteType, Set<string>>>;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select-movie', movie: Movie): void;
  (e: 'select-movie-id', movieId: number): void;
  (e: 'toggle-favorite', studioName: string): void;
  /** Movie / episode hearts; the studio itself has its own event above. */
  (e: 'toggle-entity-favorite', type: FavoriteType, key: string): void;
}>();

const activeTab = ref<'movies' | 'episodes'>('movies');

const movies = computed(() => props.works?.movies || []);
const episodes = computed(() => props.works?.episodes || []);

// Counts come from the loaded works, falling back to whatever the caller knew
// before the fetch landed so the header is populated from the first frame.
const worksCount = computed(() => props.works?.movies_count ?? props.studio?.works_count ?? 0);
const episodesCount = computed(() => props.works?.episodes_count ?? props.studio?.episodes_count ?? 0);

function isFav(type: FavoriteType, key: string | null | undefined): boolean {
  if (!key) return false;
  return Boolean(props.favoriteKeys?.[type]?.has(key));
}

// A different studio means a different pairing of tabs, so the one that was open
// is not carried over — as in PerformerDetailModal.
watch(() => props.studio?.name, () => {
  activeTab.value = 'movies';
});

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape' || props.isTop === false) return;
  if (!claimEscape(e)) return;
  emit('close');
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <div
    v-if="studio"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8 bg-scrim/80 backdrop-blur-md animate-fade-in"
    :style="{ zIndex: zIndex ?? 50 }"
    @click.self="emit('close')"
  >
    <div
      class="relative w-full max-w-4xl max-h-[90vh] chrome-panel border border-line-strong/80 rounded-3xl shadow-2xl overflow-y-auto flex flex-col text-fg"
    >
      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-4 right-4 z-20 w-8 h-8 on-scrim rounded-full bg-scrim/60 hover:bg-scrim/90 border border-white/20 flex items-center justify-center text-fg-2 hover:text-fg transition"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Profile Header. No logo exists in the library, so the tile is the name's
           initial, matching the favorites page's studio chips. -->
      <div class="p-6 md:p-8 bg-sunken border-b border-line flex items-center gap-6">
        <div class="w-20 h-20 md:w-24 md:h-24 rounded-2xl overflow-hidden shrink-0 shadow-lg shadow-accent-fill/10 ring-1 ring-line-strong/60">
          <div class="w-full h-full bg-gradient-to-tr from-accent-deep to-accent-2 flex items-center justify-center text-3xl font-black text-on-fill">
            {{ studio.name.charAt(0).toUpperCase() }}
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <div class="text-xs font-semibold text-accent uppercase tracking-wider">片商档案</div>
          <h1 class="text-2xl md:text-3xl font-extrabold text-fg truncate">{{ studio.name }}</h1>
          <div class="text-xs text-fg-3 mt-1 flex items-center gap-3 flex-wrap">
            <span class="text-accent/80">{{ worksCount }} 部作品</span>
            <span v-if="episodesCount">{{ episodesCount }} 个片段</span>
          </div>
        </div>

        <!-- Fav button leaves room for the absolutely-positioned close button -->
        <button
          @click="emit('toggle-favorite', studio.name)"
          :class="[
            'mr-10 shrink-0 self-start px-3 py-1.5 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
            isFavorite
              ? 'bg-danger-fill/20 text-danger-soft border-danger-fill/40'
              : 'bg-surface-2 hover:bg-surface-3 border-line-strong text-fg-3 hover:text-danger'
          ]"
        >
          <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
          <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
        </button>
      </div>

      <!-- Works Section -->
      <div class="p-6 md:p-8 space-y-6">
        <div class="flex items-center justify-between border-b border-line pb-4">
          <div class="flex items-center gap-2">
            <button
              @click="activeTab = 'movies'"
              :class="[
                'flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition',
                activeTab === 'movies'
                  ? 'bg-accent-fill text-on-fill shadow-lg shadow-accent-fill/20'
                  : 'bg-surface-2/70 text-fg-3 hover:text-fg-2 hover:bg-surface-2'
              ]"
            >
              <Film class="w-3.5 h-3.5" />
              <span>完整电影 ({{ movies.length }})</span>
            </button>

            <button
              @click="activeTab = 'episodes'"
              :class="[
                'flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition',
                activeTab === 'episodes'
                  ? 'bg-accent-fill text-on-fill shadow-lg shadow-accent-fill/20'
                  : 'bg-surface-2/70 text-fg-3 hover:text-fg-2 hover:bg-surface-2'
              ]"
            >
              <Layers class="w-3.5 h-3.5" />
              <span>片段 / 分集 ({{ episodes.length }})</span>
            </button>
          </div>

          <Loader2 v-if="loading" class="w-4 h-4 text-accent animate-spin shrink-0" />
        </div>

        <!-- 1. Feature Movies Tab -->
        <div v-if="activeTab === 'movies'">
          <div v-if="movies.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
            <MovieCard
              v-for="m in movies"
              :key="m.id"
              :movie="m"
              :lang="lang"
              :is-favorite="isFav('movie', String(m.id))"
              @select="emit('select-movie', m)"
              @toggle-favorite="emit('toggle-entity-favorite', 'movie', String(m.id))"
            />
          </div>
          <div v-else-if="loading" class="text-center py-12 text-fg-4 text-xs">正在读取作品清单…</div>
          <div v-else class="text-center py-12 text-fg-4 text-xs">该片商暂未收录长片电影</div>
        </div>

        <!-- 2. Episodes & Scenes Tab -->
        <div v-else-if="activeTab === 'episodes'">
          <div v-if="episodes.length > 0" class="grid grid-cols-1 gap-3">
            <!-- One row per scene, in the shared reading layout — see EpisodeRow.
                 The whole row opens the film, so the 出处 label is not a link here. -->
            <EpisodeRow
              v-for="ep in episodes"
              :key="ep.id"
              :episode="ep"
              clickable
              :is-favorite="isFav('episode', String(ep.id))"
              @select-movie-id="emit('select-movie-id', $event)"
              @toggle-favorite="emit('toggle-entity-favorite', 'episode', String(ep.id))"
            />
          </div>
          <div v-else-if="loading" class="text-center py-12 text-fg-4 text-xs">正在读取片段清单…</div>
          <div v-else class="text-center py-12 text-fg-4 text-xs">该片商暂未收录独立分集片段</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.98); }
  to { opacity: 1; transform: scale(1); }
}
.animate-fade-in {
  animation: fadeIn 0.18s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
</style>
