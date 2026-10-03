<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import { X, Film, Layers, Heart, Loader2, BookOpen, Sparkles, Languages } from '@lucide/vue';
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

// Bilingual & In-depth brand profile metadata
const fetchedDescriptionZh = ref<string | null>(null);
const fetchedLogoUrl = ref<string | null>(null);
const logoError = ref(false);

const effectiveLogoUrl = computed(() => {
  if (logoError.value) return null;
  return props.works?.logo_url || props.studio?.logo_url || fetchedLogoUrl.value || null;
});

function onLogoError() {
  logoError.value = true;
}

async function loadStudioArchive(name?: string | null) {
  if (!name) return;
  try {
    const works = await api.getStudioWorks(name);
    if (works?.description_zh && !props.works?.description_zh && !props.studio?.description_zh) {
      fetchedDescriptionZh.value = works.description_zh;
    }
    if (works?.logo_url && !props.works?.logo_url && !props.studio?.logo_url) {
      fetchedLogoUrl.value = works.logo_url;
    }
  } catch {
    // Ignore fetch error
  }
}

const effectiveNameZh = computed(() => (props.works?.studio_name_zh ?? props.studio?.name_zh ?? '').trim() || null);
const effectiveDescriptionZh = computed(() => {
  return (props.works?.description_zh || props.studio?.description_zh || fetchedDescriptionZh.value || '').trim() || null;
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

const displayInitial = computed(() => {
  const title = mainTitle.value || props.studio?.name || '';
  return title.charAt(0).toUpperCase();
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
  fetchedLogoUrl.value = null;
  logoError.value = false;
  if (newName) loadStudioArchive(newName);
}, { immediate: true });

watch(
  () => [props.works?.description_zh, props.studio?.description_zh],
  ([worksDesc, studioDesc]) => {
    if (!worksDesc && !studioDesc && props.studio?.name) {
      loadStudioArchive(props.studio.name);
    }
  }
);

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
      class="relative w-full max-w-4xl max-h-[90vh] chrome-panel border border-line-strong/80 rounded-3xl shadow-2xl overflow-y-auto flex flex-col text-fg"
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
      <div class="p-6 md:p-8 bg-sunken border-b border-line flex items-center gap-6">
        <!-- Studio Avatar / Official Logo -->
        <div class="w-24 h-20 md:w-32 md:h-24 rounded-2xl overflow-hidden shrink-0 shadow-lg shadow-accent-fill/10 ring-1 ring-line-strong/60 bg-surface-2/80 flex items-center justify-center p-2 relative group">
          <img
            v-if="effectiveLogoUrl"
            :src="getImageUrl(effectiveLogoUrl)"
            :alt="mainTitle"
            class="max-w-full max-h-full object-contain filter drop-shadow transition-transform duration-300 group-hover:scale-105"
            @error="onLogoError"
          />
          <div
            v-else
            class="w-full h-full bg-gradient-to-tr from-accent-deep to-accent-2 flex items-center justify-center text-3xl font-black text-on-fill rounded-xl"
          >
            {{ displayInitial }}
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <div class="text-xs font-semibold text-accent uppercase tracking-wider">{{ t('studio.profile') }}</div>
          <h1 class="text-2xl md:text-3xl font-extrabold text-fg truncate" :title="mainTitle">{{ mainTitle }}</h1>
          <div v-if="subTitle" class="text-xs md:text-sm font-medium text-fg-4 mt-0.5 truncate tracking-wide" :title="subTitle">
            {{ subTitle }}
          </div>
          <div class="text-xs text-fg-3 mt-1.5 flex items-center gap-3 flex-wrap">
            <span class="text-accent/80 font-medium">{{ worksCount }} {{ t('common.works') }}</span>
            <span v-if="episodesCount">{{ episodesCount }} {{ t('common.episodes') }}</span>
          </div>
        </div>

        <!-- Action buttons: Resource Search + Language Toggle + Fav -->
        <div class="mr-10 shrink-0 self-start flex items-center gap-2 flex-wrap">
          <template v-if="pluginsConfig.resourceSearchEnabled">
            <ResourceSearchWidget type="studio" :title="studio.name" />
          </template>

          <button
            v-if="hasTranslation"
            @click="toggleLang"
            :title="effectiveLang === 'en' ? t('movie.showTranslation') : t('movie.showOriginal')"
            class="px-2.5 py-1.5 rounded-lg text-xs font-medium border border-line-strong bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg flex items-center gap-1.5 transition cursor-pointer"
          >
            <Languages class="w-3.5 h-3.5" />
            <span>{{ effectiveLang === 'en' ? t('movie.showTranslation') : t('movie.showOriginal') }}</span>
          </button>

          <button
            @click="emit('toggle-favorite', studio.name)"
            :class="[
              'px-3 py-1.5 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition cursor-pointer',
              isFavorite
                ? 'bg-danger-fill/20 text-danger-soft border-danger-fill/40'
                : 'bg-surface-2 hover:bg-surface-3 border-line-strong text-fg-3 hover:text-danger'
            ]"
          >
            <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
            <span>{{ isFavorite ? t('studio.favorited') : t('studio.favorite') }}</span>
          </button>
        </div>
      </div>

      <!-- Studio History & Brand Archive Column ("厂牌历史档案与风格深度解析专栏") -->
      <div
        v-if="effectiveDescriptionZh"
        class="mx-6 md:mx-8 mt-6 p-6 md:p-8 rounded-3xl bg-gradient-to-br from-surface-2/95 via-surface/90 to-surface-2/70 border border-line-strong shadow-xl relative overflow-hidden group transition-all"
      >
        <!-- Background decorative watermark -->
        <div class="absolute -right-6 -bottom-8 text-accent/[0.04] pointer-events-none select-none transition-transform duration-500 group-hover:scale-105 group-hover:text-accent/[0.07]">
          <BookOpen class="w-48 h-48 stroke-[1]" />
        </div>

        <div class="relative z-10 space-y-4">
          <!-- Column Header with Title, Tagline and Badge -->
          <div class="flex items-center justify-between gap-4 pb-3.5 border-b border-line-strong/60 flex-wrap">
            <div class="flex items-center gap-3">
              <div class="w-9 h-9 rounded-xl bg-accent-fill/15 border border-accent-fill/30 flex items-center justify-center text-accent shrink-0 shadow-sm ring-2 ring-accent-fill/10">
                <BookOpen class="w-5 h-5" />
              </div>
              <div>
                <h2 class="text-base md:text-lg font-bold text-fg tracking-wide flex items-center gap-2">
                  <span>{{ t('studio.historyArchive') }}</span>
                </h2>
                <div class="text-xs text-fg-4 font-medium tracking-wide mt-0.5">
                  {{ t('studio.historySubtitle') }}
                </div>
              </div>
            </div>

            <div class="shrink-0 flex items-center gap-2">
              <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-accent-fill/15 text-accent border border-accent-fill/30 shadow-sm">
                <Sparkles class="w-3.5 h-3.5" />
                <span>{{ t('studio.historyBadge') }}</span>
              </span>
            </div>
          </div>

          <!-- Column Content Body: Expansive, legible, comfortable line-height and blockquote styling -->
          <div class="relative pl-4 md:pl-5 border-l-2 border-accent/50 py-1.5 mt-2">
            <p class="text-sm md:text-[15px] text-fg leading-relaxed tracking-normal font-normal text-justify select-text whitespace-pre-line">
              {{ effectiveDescriptionZh }}
            </p>
          </div>
        </div>
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
              v-for="m in movies"
              :key="m.id"
              :movie="m"
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
