<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, defineAsyncComponent, nextTick } from 'vue';
import { X, Film, Layers, Heart, Loader2, Languages } from '@lucide/vue';
import type { Movie, StudioWorks, FavoriteType } from '../types';
import MovieCard from './MovieCard.vue';
import EpisodeRow from './EpisodeRow.vue';
import { claimEscape } from '../utils/escape';
import { getImageUrl } from '../utils/image';
import { pluginsConfig } from '../services/pluginManager';
import { api } from '../api';
import { t } from '../i18n';

const ResourceSearchWidget = defineAsyncComponent(() => import('./plugins/ResourceSearchWidget.vue'));

/**
 * A studio, as the library grid and the favorites page know it.
 *
 * Deliberately not the `StudioSummary` type: the favorites page only carries a
 * name and a works count, and a studio with no row on screen has no counts at all,
 * so both fields stay optional and the header falls back to the loaded works.
 */
interface StudioRef {
  name: string;
  name_zh?: string | null;
  description_zh?: string | null;
  logo_url?: string | null;
  banner_url?: string | null;
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

const BATCH_SIZE = 60;
const displayedEpisodeCount = ref(BATCH_SIZE);

const displayedEpisodes = computed(() => {
  return episodes.value.slice(0, displayedEpisodeCount.value);
});

const hasMoreEpisodes = computed(() => {
  return displayedEpisodeCount.value < episodes.value.length;
});

function loadMoreEpisodes() {
  displayedEpisodeCount.value = Math.min(displayedEpisodeCount.value + BATCH_SIZE, episodes.value.length);
}

// Counts come from the loaded works, falling back to whatever the caller knew
// before the fetch landed so the header is populated from the first frame.
const worksCount = computed(() => props.works?.movies_count ?? props.studio?.works_count ?? 0);
const episodesCount = computed(() => props.works?.episodes_count ?? props.studio?.episodes_count ?? 0);

// Modal container ref to reset scroll position on studio switch
const modalContainerRef = ref<HTMLElement | null>(null);

// Bilingual & In-depth brand profile metadata
const fetchedDescriptionZh = ref<string | null>(null);
const cachedDescriptionZh = ref<string | null>(null);
const fetchedLogoUrl = ref<string | null>(null);
const logoError = ref(false);

const effectiveLogoUrl = computed(() => {
  if (logoError.value) return null;
  return (
    props.works?.logo_url ||
    props.studio?.logo_url ||
    fetchedLogoUrl.value ||
    props.works?.banner_url ||
    props.studio?.banner_url ||
    null
  );
});

function onLogoError() {
  logoError.value = true;
}

/**
 * Fallback archive fetcher: only called if works wasn't supplied by parent
 * or parent works did not contain description_zh.
 */
async function loadStudioArchive(name?: string | null) {
  if (!name) return;
  // If we already have description and logo from props, skip redundant IPC call
  if ((props.works?.description_zh || props.studio?.description_zh) && (props.works?.logo_url || props.studio?.logo_url)) {
    return;
  }
  try {
    const works = await api.getStudioWorks(name);
    if (works?.description_zh) {
      fetchedDescriptionZh.value = works.description_zh;
      cachedDescriptionZh.value = works.description_zh;
    }
    if (works?.logo_url && !props.works?.logo_url && !props.studio?.logo_url) {
      fetchedLogoUrl.value = works.logo_url;
    } else if (works?.banner_url && !props.works?.banner_url && !props.studio?.banner_url) {
      fetchedLogoUrl.value = works.banner_url;
    }
  } catch {
    // Ignore fetch error
  }
}

watch(
  () => [props.studio?.description_zh, props.works?.description_zh, fetchedDescriptionZh.value],
  ([studioDesc, worksDesc, fetchedDesc]) => {
    for (const c of [worksDesc, studioDesc, fetchedDesc]) {
      if (typeof c === 'string' && c.trim().length > 0) {
        cachedDescriptionZh.value = c.trim();
        return;
      }
    }
  },
  { immediate: true }
);

const effectiveNameZh = computed(() => (props.works?.studio_name_zh ?? props.studio?.name_zh ?? '').trim() || null);
const effectiveDescriptionZh = computed(() => {
  if (cachedDescriptionZh.value) return cachedDescriptionZh.value;
  for (const c of [props.works?.description_zh, props.studio?.description_zh, fetchedDescriptionZh.value]) {
    if (typeof c === 'string' && c.trim().length > 0) {
      return c.trim();
    }
  }
  return null;
});
const hasTranslation = computed(() => Boolean(effectiveNameZh.value || effectiveDescriptionZh.value));

const showOriginalOverride = ref<boolean | null>(null);

const effectiveLang = computed<'zh' | 'en'>(() => {
  if (showOriginalOverride.value !== null) {
    return showOriginalOverride.value ? 'en' : 'zh';
  }
  return props.lang ?? 'zh';
});

function toggleLang() {
  if (showOriginalOverride.value !== null) {
    showOriginalOverride.value = !showOriginalOverride.value;
  } else {
    showOriginalOverride.value = effectiveLang.value === 'zh';
  }
}

const mainTitle = computed(() => {
  const orig = (props.studio?.name || props.works?.studio_name || '').trim();
  if (effectiveLang.value === 'zh' && effectiveNameZh.value) {
    return effectiveNameZh.value;
  }
  return orig;
});

const subTitle = computed(() => {
  if (effectiveLang.value !== 'zh') return '';
  const zh = effectiveNameZh.value;
  const orig = (props.studio?.name || props.works?.studio_name || '').trim();
  if (zh && orig && zh !== orig) {
    return orig;
  }
  return '';
});

function isFav(type: FavoriteType, key: string | null | undefined): boolean {
  if (!key) return false;
  return Boolean(props.favoriteKeys?.[type]?.has(key));
}

// A different studio means a different pairing of tabs, so the one that was open
// is not carried over — as in PerformerDetailModal.
watch(() => props.studio?.name, (newName) => {
  activeTab.value = 'movies';
  displayedEpisodeCount.value = BATCH_SIZE;
  showOriginalOverride.value = null;
  fetchedDescriptionZh.value = null;
  cachedDescriptionZh.value = null;
  fetchedLogoUrl.value = null;
  logoError.value = false;
  nextTick(() => {
    if (modalContainerRef.value) {
      modalContainerRef.value.scrollTop = 0;
    }
  });
  // Only fetch if parent didn't already provide works or description
  if (newName && !props.works?.description_zh && !props.studio?.description_zh) {
    loadStudioArchive(newName);
  }
}, { immediate: true });

watch(activeTab, () => {
  displayedEpisodeCount.value = BATCH_SIZE;
});

function handleScroll(e: Event) {
  if (activeTab.value !== 'episodes' || !hasMoreEpisodes.value) return;
  const target = e.target as HTMLElement;
  if (!target) return;
  if (target.scrollTop + target.clientHeight >= target.scrollHeight - 400) {
    loadMoreEpisodes();
  }
}

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
      ref="modalContainerRef"
      class="relative w-full max-w-5xl lg:max-w-6xl max-h-[90vh] chrome-panel border border-line-strong/80 rounded-3xl shadow-2xl overflow-y-auto flex flex-col text-fg"
      @scroll="handleScroll"
    >
      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-4 right-4 z-20 w-8 h-8 on-scrim rounded-full bg-scrim/60 hover:bg-scrim/90 border border-white/20 flex items-center justify-center text-fg-2 hover:text-fg transition"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Profile Header. Displays dual titles (Chinese main + light-gray English subtitle) when available. -->
      <div class="p-6 md:p-8 bg-sunken border-b border-line flex items-center justify-between gap-6 shrink-0">
        <div class="min-w-0 flex-1">
          <div class="text-xs font-semibold text-accent uppercase tracking-wider">{{ t('studio.profile') }}</div>
          <h1 class="text-2xl md:text-3xl lg:text-4xl font-black text-fg break-words line-clamp-2 leading-tight mt-1" :title="mainTitle">{{ mainTitle }}</h1>
          <div v-if="subTitle" class="text-xs md:text-sm font-medium text-fg-4 mt-1 break-words line-clamp-2 tracking-wide" :title="subTitle">
            {{ subTitle }}
          </div>
          <div class="text-xs text-fg-3 mt-2.5 flex items-center gap-3 flex-wrap">
            <span class="text-accent/80 font-medium">{{ worksCount }} {{ t('common.works') }}</span>
            <span v-if="episodesCount">{{ episodesCount }} {{ t('common.episodes') }}</span>
          </div>
        </div>

        <!-- Right Side: Official Logo (Above buttons) + Action buttons -->
        <div class="mr-10 shrink-0 flex flex-col gap-2.5 w-auto min-w-[280px] max-w-[340px]">
          <!-- Studio Official Logo: Positioned above the action buttons with adaptive width -->
          <div
            v-if="effectiveLogoUrl"
            class="w-full h-12 md:h-14 px-3 py-1.5 rounded-2xl bg-surface-2/80 border border-line-strong/60 flex items-center justify-center overflow-hidden shadow-sm group"
          >
            <img
              :src="getImageUrl(effectiveLogoUrl)"
              :alt="mainTitle"
              class="w-auto h-full max-w-full max-h-full object-contain filter drop-shadow transition-transform duration-300 group-hover:scale-105"
              @error="onLogoError"
            />
          </div>

          <!-- Action buttons: Multi-column grid (BT Search, Google Search, Language Toggle, Favorite) -->
          <div class="grid grid-cols-2 gap-2 w-full">
            <template v-if="pluginsConfig.resourceSearchEnabled">
              <ResourceSearchWidget
                type="studio"
                :title="studio.name"
                wrapper-class="contents"
                button-class="w-full justify-center"
              />
            </template>

            <button
              v-if="hasTranslation"
              @click="toggleLang"
              :title="effectiveLang === 'en' ? t('movie.showTranslation') : t('movie.showOriginal')"
              class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-fg flex items-center justify-center gap-1.5 transition cursor-pointer whitespace-nowrap w-full"
            >
              <Languages class="w-3.5 h-3.5 shrink-0" />
              <span class="truncate">{{ effectiveLang === 'en' ? t('movie.showTranslation') : t('movie.showOriginal') }}</span>
            </button>

            <button
              @click="emit('toggle-favorite', studio.name)"
              :class="[
                'py-1.5 px-2.5 rounded-xl text-xs font-medium border flex items-center justify-center gap-1.5 transition cursor-pointer whitespace-nowrap w-full',
                isFavorite
                  ? 'bg-danger-fill/20 text-danger-soft border-danger-fill/40'
                  : 'bg-surface-2/60 hover:bg-surface-3 border-line text-fg-3 hover:text-danger'
              ]"
            >
              <Heart class="w-3.5 h-3.5 shrink-0" :fill="isFavorite ? 'currentColor' : 'none'" />
              <span class="truncate">{{ isFavorite ? t('studio.favorited') : t('studio.favorite') }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Studio Introduction ("厂牌介绍") - Clean, lightweight and responsive -->
      <div
        v-if="effectiveDescriptionZh"
        class="mx-6 md:mx-8 mt-5 p-4 md:p-5 rounded-2xl bg-surface-2/50 border border-line/60 shrink-0 min-h-fit"
      >
        <div class="text-xs font-bold text-accent uppercase tracking-wider mb-2">
          {{ t('studio.historyArchive') }}
        </div>
        <p class="text-xs md:text-sm text-fg-2 leading-relaxed text-justify select-text whitespace-pre-line">
          {{ effectiveDescriptionZh }}
        </p>
      </div>

      <!-- Works Section -->
      <div class="p-6 md:p-8 space-y-6 shrink-0">
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
              <span>{{ t('studio.movies') }} ({{ movies.length }})</span>
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
              <span>{{ t('studio.episodes') }} ({{ episodes.length }})</span>
            </button>
          </div>

          <Loader2 v-if="loading" class="w-4 h-4 text-accent animate-spin shrink-0" />
        </div>

        <!-- 1. Feature Movies Tab -->
        <div v-if="activeTab === 'movies'">
          <div v-if="movies.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
            <MovieCard
              v-for="(m, idx) in movies"
              :key="m.id"
              :movie="m"
              :priority="idx < 16"
              :lang="effectiveLang"
              :is-favorite="isFav('movie', String(m.id))"
              @select="emit('select-movie', m)"
              @toggle-favorite="emit('toggle-entity-favorite', 'movie', String(m.id))"
            />
          </div>
          <div v-else-if="loading" class="text-center py-12 text-fg-4 text-xs">{{ t('studio.loadingMovies') }}</div>
          <div v-else class="text-center py-12 text-fg-4 text-xs">{{ t('studio.noMovies') }}</div>
        </div>

        <!-- 2. Episodes & Scenes Tab -->
        <div v-else-if="activeTab === 'episodes'">
          <div v-if="episodes.length > 0" class="space-y-4">
            <div class="grid grid-cols-1 gap-3">
              <!-- One row per scene, in the shared reading layout — see EpisodeRow.
                   The whole row opens the film, so the 出处 label is not a link here. -->
              <EpisodeRow
                v-for="ep in displayedEpisodes"
                :key="ep.id"
                :episode="ep"
                :lang="effectiveLang"
                clickable
                :is-favorite="isFav('episode', String(ep.id))"
                @select-movie-id="emit('select-movie-id', $event)"
                @toggle-favorite="emit('toggle-entity-favorite', 'episode', String(ep.id))"
              />
            </div>

            <!-- Progressive Loading Indicator & Button -->
            <div v-if="hasMoreEpisodes" class="flex flex-col items-center justify-center py-4 gap-2 border-t border-line/60">
              <div class="text-xs text-fg-3">
                {{ t('studio.displayedEpisodes', { shown: displayedEpisodes.length, total: episodes.length }) }}
              </div>
              <button
                @click="loadMoreEpisodes"
                class="px-5 py-2 text-xs font-medium rounded-xl bg-surface-2 hover:bg-surface-3 border border-line-strong text-fg-2 hover:text-fg transition shadow-sm cursor-pointer"
              >
                {{ t('studio.loadMore') }} (+{{ Math.min(BATCH_SIZE, episodes.length - displayedEpisodes.length) }})
              </button>
            </div>
          </div>
          <div v-else-if="loading" class="text-center py-12 text-fg-4 text-xs">{{ t('studio.loadingEpisodes') }}</div>
          <div v-else class="text-center py-12 text-fg-4 text-xs">{{ t('studio.noEpisodes') }}</div>
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
