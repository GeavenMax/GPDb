<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick, defineAsyncComponent } from 'vue';
import { X, Film, Clock, Heart, Building2, Tag, Layers, Clapperboard, Languages, Loader2, ChevronDown, ChevronUp, ZoomIn, Share2 } from '@lucide/vue';
import type { Movie, FavoriteType, MovieSeriesResponse } from '../types';
import EpisodeRow from './EpisodeRow.vue';
import SeriesModal from './SeriesModal.vue';
import ShareCardModal, { type ShareCardData } from './ShareCardModal.vue';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';
import { openLightbox } from '../utils/lightbox';
import { titlePrimary, titleSecondary } from '../utils/bilingual';
import { trCategory } from '../utils/glossary';
import { api, IS_TAURI } from '../api';
import { t, currentLocale } from '../i18n';

const ResourceSearchWidget = defineAsyncComponent(() => import('./plugins/ResourceSearchWidget.vue'));

const props = defineProps<{
  movie: Movie | null;
  isFavorite?: boolean;
  /** Preferred synopsis language; falls back to the original when untranslated. */
  lang?: 'zh' | 'en';
  /**
   * Stacking order supplied by the parent. Detail views can open each other
   * (a performer's filmography links back to a movie and vice versa), so a
   * fixed z-index would let whichever modal happens to sit later in the DOM
   * cover the one the user just opened.
   */
  zIndex?: number;
  /**
   * Whether this is the topmost detail view. Both modals stay mounted while the
   * user navigates between them, and each listens for Escape on `window`, so
   * without this a single Escape would dismiss the whole stack.
   */
  isTop?: boolean;
  /**
   * Translate this film's synopsis on open, if it has none yet. Driven by the
   * single/batch switch in Settings; the parent owns that choice.
   */
  autoTranslate?: boolean;
  /** Favorited keys by type — drives the studio / director / episode hearts. */
  favoriteKeys?: Partial<Record<FavoriteType, Set<string>>>;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select-movie', movieId: number): void;
  (e: 'select-performer', performerId: number): void;
  (e: 'select-studio', studioName: string): void;
  (e: 'filter-studio', studioName: string): void;
  (e: 'filter-director', directorName: string): void;
  (e: 'toggle-favorite', movie: Movie): void;
  /** Studio / director / episode hearts; the film itself has its own event above. */
  (e: 'toggle-entity-favorite', type: FavoriteType, key: string): void;
  (e: 'user-data-changed', movieId: number): void;
  (e: 'translated', movieId: number, descriptionZh: string): void;
  (e: 'select-episode-id', episodeId: number): void;
}>();

/** Whether a studio/director/episode is favorited. Names are the key for studio and director. */
function isFav(type: FavoriteType, key: string | null | undefined): boolean {
  if (!key) return false;
  return Boolean(props.favoriteKeys?.[type]?.has(key));
}

/**
 * Films this session has already tried to auto-translate.
 *
 * Module-scoped on purpose: the parent re-fetches a film's detail on every open,
 * so without this, closing and reopening the same untranslated film - or one
 * whose translation keeps failing - would call the API again each time. Reset by
 * reloading the app, which is when a fresh attempt is reasonable.
 */
const autoAttempted = new Set<number>();

/**
 * All covers for the movie, mapped to full cached URLs.
 * If covers array exists and has entries, use them; otherwise fallback to cover_full.
 */
const allCovers = computed<string[]>(() => {
  if (props.movie?.covers && props.movie.covers.length > 0) {
    return props.movie.covers.map(c => getImageUrl(c)).filter(Boolean);
  }
  if (props.movie?.cover_full) {
    const u = getImageUrl(props.movie.cover_full);
    return u ? [u] : [];
  }
  return [];
});

const currentCover = computed(() => allCovers.value[0] || '');
const frontCoverUrl = computed(() => allCovers.value[0] || '');
const backCoverUrl = computed(() => allCovers.value[1] || null);

const showShareModal = ref(false);

const shareCardData = computed<ShareCardData | null>(() => {
  if (!props.movie) return null;
  const isZh = currentLocale.value.startsWith('zh');
  return {
    type: 'movie',
    title: isZh ? (props.movie.title_zh || props.movie.title) : props.movie.title,
    titleAlt: isZh
      ? (props.movie.title_zh && props.movie.title !== props.movie.title_zh ? props.movie.title : null)
      : null,
    posterUrl: frontCoverUrl.value,
    coverBackUrl: backCoverUrl.value,
    category: categoryLabel.value,
    releaseDate: displayReleaseDate.value || (props.movie.release_year ? String(props.movie.release_year) : ''),
    durationMins: props.movie.duration_mins,
    studioName: props.movie.studio_name,
    directorName: props.movie.directors?.map(d => d.name).join(isZh ? '、' : ', '),
    performers: props.movie.performers?.map(p => p.name) || [],
    description: props.movie.description,
    descriptionZh: props.movie.description_zh,
    id: props.movie.id,
  };
});

const seriesData = ref<MovieSeriesResponse | null>(null);
const showSeriesModal = ref(false);

async function loadSeries(id: number) {
  seriesData.value = null;
  try {
    seriesData.value = await api.getMovieSeries(id);
  } catch {
    seriesData.value = null;
  }
}

function onSelectSeriesMovie(id: number) {
  showSeriesModal.value = false;
  emit('select-movie', id);
}

// Synopses carry both the original English and (once translated) the Chinese text.
const zhDescription = ref<string | null>(null);
/**
 * The local "show original" toggle, as an override of the global language switch.
 *
 * `null` means "not touched — follow `props.lang`", which is what makes the app-wide
 * 中文/原文 switch reach inside this modal. The prop was declared from the start and
 * never read, so switching the whole library to Chinese left every open film showing
 * English; a plain `ref(false)` would have fixed that once but then silently ignored
 * the switch for any modal the user had already toggled.
 */
const showOriginalOverride = ref<boolean | null>(null);
const isTranslating = ref(false);
const translateError = ref('');
const allScenesExpanded = ref(false);

/** Whether the original synopsis is on screen right now — global switch, unless overridden. */
const showOriginal = computed(() => showOriginalOverride.value ?? props.lang === 'en');

function toggleOriginal() {
  showOriginalOverride.value = !showOriginal.value;
}

watch(() => props.movie, (m) => {
  zhDescription.value = m?.description_zh || null;
  showOriginalOverride.value = null;
  translateError.value = '';
  allScenesExpanded.value = false;

  if (m?.id) {
    loadSeries(m.id);
  } else {
    seriesData.value = null;
    showSeriesModal.value = false;
  }

  // Single-translation mode: fill in this one film's synopsis as it is opened,
  // together with any episode synopses that came back with it.
  //
  // The episode check is separate from the synopsis check because the two can
  // diverge: a film translated before episodes were translated at all has a Chinese
  // synopsis and English episodes, and it would otherwise never be offered again.
  // The server skips the synopsis in that case and only sends the episodes.
  // Skipped on the desktop build, which has no server to run the request.
  const id = m?.id;
  const needsSynopsis =
    (m?.description || '').trim() && !(m?.description_zh || '').trim();
  const needsEpisodes = (m?.episodes || []).some(
    ep => (ep.description || '').trim() && !(ep.description_zh || '').trim(),
  );
  if (
    props.autoTranslate && !IS_TAURI && id && !autoAttempted.has(id) &&
    (needsSynopsis || needsEpisodes)
  ) {
    autoAttempted.add(id);
    nextTick(() => translateNow());
  }
}, { immediate: true });

const hasZh = computed(() => Boolean(zhDescription.value?.trim()));

/**
 * The film's directors, one name each, to render as separate clickable chips.
 *
 * Prefers the roster the detail endpoint reads from movie_directors. Falls back to
 * `director_name` — split on " / " when the fixed parser wrote it, whole otherwise —
 * so a library that has not been through `scraper_v2.py --mode directors` still shows
 * its director, glued names and all, exactly as before.
 */
const directorNames = computed<string[]>(() => {
  const roster = props.movie?.directors;
  if (roster && roster.length > 0) {
    return roster.map(d => d.name).filter(n => n.trim());
  }
  const raw = props.movie?.director_name?.trim();
  if (!raw) return [];
  return raw.split(' / ').map(s => s.trim()).filter(Boolean);
});

/**
 * Episodes that still have an English-only synopsis.
 *
 * Tracked separately from `hasZh` because the two ages differ: a film translated
 * before episode synopses were translated at all keeps an English episode list, and
 * the synopsis button is hidden once `hasZh` is set. Surfacing the count is what
 * gives the user a way to finish the job from the film they are looking at.
 */
const pendingEpisodeCount = computed(() =>
  (props.movie?.episodes || []).filter(
    ep => (ep.description || '').trim() && !(ep.description_zh || '').trim(),
  ).length,
);

/** Chinese when available and wanted; the original otherwise. */
const displayedDescription = computed(() => {
  if (hasZh.value && !showOriginal.value) return zhDescription.value || '';
  return props.movie?.description || '';
});

/** The film's name: Chinese on top, the original beneath in smaller type. */
const titleMain = computed(() =>
  props.movie ? titlePrimary(props.movie, props.lang) : ''
);
/** The original title, or '' when there is nothing to put under the Chinese one. */
const titleAlt = computed(() =>
  props.movie ? titleSecondary(props.movie, props.lang) : ''
);
/** Category, term by term — the column holds "Wrestling<br />J/O" as one value. */
const categoryLabel = computed(() => trCategory(props.movie?.category));

const displayReleaseDate = computed(() => {
  if (!props.movie) return null;
  if (props.movie.release_date) return props.movie.release_date;
  if (props.movie.episodes && props.movie.episodes.length > 0) {
    const dates = props.movie.episodes
      .map(e => e.release_date)
      .filter((d): d is string => Boolean(d && d.trim()));
    if (dates.length > 0) {
      dates.sort();
      return dates[0];
    }
  }
  return null;
});

// Hint for long synopses: they scroll inside their own box, which is not obvious
// without a scrollbar, so the hint stays visible until the user reaches the end.
const descriptionBoxRef = ref<HTMLElement | null>(null);
const descriptionOverflows = ref(false);
const descriptionAtEnd = ref(false);

function measureDescription() {
  const el = descriptionBoxRef.value;
  if (!el) {
    descriptionOverflows.value = false;
    return;
  }
  descriptionOverflows.value = el.scrollHeight > el.clientHeight + 4;
  descriptionAtEnd.value = el.scrollTop + el.clientHeight >= el.scrollHeight - 8;
}

function onDescriptionScroll() {
  const el = descriptionBoxRef.value;
  if (!el) return;
  descriptionAtEnd.value = el.scrollTop + el.clientHeight >= el.scrollHeight - 8;
}

// Re-measure when the text or its language changes, and when the window resizes
// (the modal reflows, so a synopsis can start or stop overflowing).
watch(displayedDescription, async () => {
  await nextTick();
  measureDescription();
});

watch(() => props.isTop, (top) => {
  if (top) nextTick(() => measureDescription());
});

/**
 * Copy episode synopses that came back alongside the film's translation onto the
 * episode rows this modal already holds.
 *
 * Episodes are translated in the same request as their parent film because an
 * episode's synopsis is only ever read from inside that film — there is no separate
 * "translate this episode" action to keep in sync. The movie object belongs to the
 * parent, so writing through to it here is what makes the new text render.
 */
function applyEpisodeTranslations(rows: Array<{ id: number; description_zh: string | null }>) {
  const episodes = props.movie?.episodes;
  if (!episodes || episodes.length === 0) return;
  const byId = new Map(rows.map(r => [r.id, r.description_zh]));
  for (const ep of episodes) {
    const zh = byId.get(ep.id);
    if (zh) ep.description_zh = zh;
  }
}

async function translateNow() {
  if (!props.movie) return;
  isTranslating.value = true;
  translateError.value = '';
  const id = props.movie.id;
  const result = await api.translateMovie(id);
  if (result && !result.error && (result.description_zh || result.episodes.length > 0)) {
    // Empty when the server skipped an already-translated synopsis and only sent
    // episodes — keep what is on screen rather than blanking it.
    const zh = (result.description_zh || '').trim();
    if (zh) {
      zhDescription.value = zh;
      // A fresh translation means the Chinese text is what the user asked to see.
      showOriginalOverride.value = false;
      emit('translated', id, zh);
    }
    applyEpisodeTranslations(result.episodes);
    emit('user-data-changed', id);
  } else {
    translateError.value = result?.error || t('plugins.failed');
  }
  isTranslating.value = false;
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape' || props.isTop === false) return;
  if (!claimEscape(e)) return;
  emit('close');
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown);
  window.addEventListener('resize', measureDescription);
  nextTick(() => measureDescription());
});
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown);
  window.removeEventListener('resize', measureDescription);
});
</script>

<template>
  <div
    v-if="movie"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8 bg-scrim/80 backdrop-blur-md animate-fade-in"
    :style="{ zIndex: zIndex ?? 50 }"
    @click.self="emit('close')"
  >
    <!-- Modal Card -->
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

      <!--
        Hero Section with blurred backdrop.

        shrink-0 is load-bearing: the modal card is a flex column capped at 90vh, so
        without it this section is squashed down to whatever height is left over.
        Its own overflow-hidden then clips the rest of the synopsis away entirely -
        a long description used to be unreachable, not just awkward to scroll.
      -->
      <div class="relative w-full shrink-0 overflow-hidden bg-sunken p-6 md:p-8 border-b border-line">
        <!-- Blurred background image: follows the selected cover, cached like the poster -->
        <div
          v-if="currentCover"
          class="absolute inset-0 bg-cover bg-center blur-2xl opacity-25 scale-110 pointer-events-none"
          :style="{ backgroundImage: `url(${currentCover})` }"
        ></div>

        <!-- Foreground Content -->
        <div class="relative z-10 flex flex-col md:flex-row gap-6 items-start">
          <!-- Poster Container: Adaptive Simultaneous Multi-Cover Display -->
          <div class="flex flex-col gap-2 shrink-0">
            <div
              v-if="allCovers.length > 0"
              class="flex flex-row flex-wrap gap-3 items-start justify-center md:justify-start"
            >
              <div
                v-for="(coverUrl, idx) in allCovers"
                :key="idx"
                class="relative rounded-2xl overflow-hidden shadow-2xl border border-line-strong/60 bg-sunken group cursor-pointer flex items-center justify-center transition-all duration-300 hover:shadow-accent/20"
                :class="allCovers.length === 1
                  ? 'w-48 md:w-60 min-h-[260px] max-h-[380px]'
                  : 'w-36 sm:w-44 md:w-48 min-h-[200px] max-h-[320px]'"
                @click="openLightbox(coverUrl, `${movie.title} (${idx === 0 ? t('movie.frontCover') : idx === 1 ? t('movie.backCover') : '#' + (idx + 1)})`)"
              >
                <img
                  :src="coverUrl"
                  :alt="`${movie.title} - ${idx + 1}`"
                  referrerpolicy="no-referrer"
                  :class="allCovers.length === 1 ? 'max-h-[380px]' : 'max-h-[320px]'"
                  class="max-w-full w-auto h-auto object-contain transition-transform duration-300 group-hover:scale-105"
                />

                <!-- Cover badge (Front / Back / #N) when 2+ covers -->
                <div
                  v-if="allCovers.length > 1"
                  class="absolute top-2.5 left-2.5 px-2 py-0.5 rounded-md bg-black/60 backdrop-blur-md text-[10px] font-bold text-white/90 border border-white/10 shadow pointer-events-none"
                >
                  {{ idx === 0 ? t('movie.frontCover') : idx === 1 ? t('movie.backCover') : `#${idx + 1}` }}
                </div>

                <!-- Zoom hover badge -->
                <div class="absolute top-2.5 right-2.5 p-1.5 rounded-full bg-black/60 opacity-0 group-hover:opacity-100 backdrop-blur-md text-white/90 transition shadow pointer-events-none">
                  <ZoomIn class="w-3.5 h-3.5" />
                </div>
              </div>
            </div>

            <!-- Empty placeholder if no covers -->
            <div
              v-else
              class="w-48 md:w-60 h-72 rounded-2xl overflow-hidden shadow-2xl border border-line-strong/60 bg-sunken flex flex-col items-center justify-center text-fg-5 p-4 text-center"
            >
              <Film class="w-12 h-12 mb-2 stroke-1" />
              <span class="text-xs">{{ t('movie.noPoster') }}</span>
            </div>
          </div>

          <!-- Metadata -->
          <div class="flex-1 space-y-4">
            <div class="flex items-center gap-2 flex-wrap">
              <span v-if="displayReleaseDate || movie.release_year" class="px-2.5 py-1 rounded-lg text-xs font-bold bg-accent-fill/10 text-accent border border-accent-fill/30 font-mono">
                {{ displayReleaseDate || movie.release_year }}
              </span>
              <span v-if="movie.duration_mins" class="px-2.5 py-1 rounded-lg text-xs font-medium bg-surface-2 text-fg-2 border border-line-strong flex items-center gap-1.5">
                <Clock class="w-3.5 h-3.5" />
                {{ movie.duration_mins }} {{ t('movie.minutes') }}
              </span>
              <span v-if="movie.category" class="px-2.5 py-1 rounded-lg text-xs font-medium bg-surface-2 text-fg-2 border border-line-strong">
                {{ categoryLabel }}
              </span>
            </div>

            <h1 class="text-2xl md:text-3xl font-extrabold text-fg tracking-tight">
              {{ titleMain }}
            </h1>
            <p v-if="titleAlt" class="text-sm text-fg-4 mt-0.5">{{ titleAlt }}</p>

            <!-- Series Franchise Entry Badge / Button -->
            <div v-if="seriesData && seriesData.items.length >= 2" class="pt-1">
              <button
                @click="showSeriesModal = true"
                class="inline-flex items-center gap-2 px-3 py-1.5 rounded-xl bg-accent-fill/15 border border-accent-fill/30 text-accent hover:bg-accent-fill/25 transition text-xs font-semibold shadow-sm group cursor-pointer"
                :title="t('series.allWorks', { title: seriesData.root_title })"
              >
                <Film class="w-3.5 h-3.5 text-accent" />
                <span>{{ t('movie.allSeries') }} ({{ seriesData.items.length }})</span>
                <span class="text-[10px] text-fg-4 font-normal">· {{ seriesData.root_title }}</span>
                <span class="text-accent group-hover:translate-x-0.5 transition-transform">→</span>
              </button>
            </div>

            <!-- Studio & Director Pills (each filterable and favoritable) -->
            <div class="flex items-center gap-3 flex-wrap">
              <div v-if="movie.studio_name" class="flex items-center gap-1.5">
                <Building2 class="w-4 h-4 text-fg-3" />
                <button
                  @click="emit('select-studio', movie.studio_name)"
                  :title="t('favorites.openStudioProfile', { name: movie.studio_name })"
                  class="text-xs font-semibold text-accent hover:underline bg-surface-2/80 px-2.5 py-1 rounded-md border border-line-strong/60 cursor-pointer"
                >
                  {{ movie.studio_name }}
                </button>
                <button
                  @click="emit('toggle-entity-favorite', 'studio', movie.studio_name)"
                  :title="isFav('studio', movie.studio_name) ? t('studio.unfavorite') : t('studio.favorite')"
                  class="transition"
                  :class="isFav('studio', movie.studio_name) ? 'text-danger' : 'text-fg-5 hover:text-danger'"
                >
                  <Heart class="w-3.5 h-3.5" :fill="isFav('studio', movie.studio_name) ? 'currentColor' : 'none'" />
                </button>
              </div>

              <!-- One chip per director. The roster comes from the junction table;
                   `director_name` is a single string that, for rows scraped before
                   the parser fix, has every name glued together with no separator
                   ("Bill ClaytonSteven Scarborough") — unclickable and unfilterable.
                   The string stays as the fallback for a library that has not been
                   through `--mode directors`, split on " / " when the fixed parser
                   wrote it. -->
              <div v-if="directorNames.length > 0" class="flex items-center gap-1.5 bg-surface border border-line px-2.5 py-1 rounded-md text-xs text-fg-2 flex-wrap">
                <Clapperboard class="w-3.5 h-3.5 text-accent" />
                <span class="text-fg-4">{{ t('filter.director') }}:</span>
                <template v-for="(name, i) in directorNames" :key="name">
                  <span v-if="i > 0" class="text-fg-5">·</span>
                  <button
                    @click="emit('filter-director', name)"
                    class="font-medium text-fg-2 hover:text-accent-soft hover:underline transition"
                    :title="t('director.viewAllMovies', { name })"
                  >
                    {{ name }}
                  </button>
                  <button
                    @click="emit('toggle-entity-favorite', 'director', name)"
                    :title="isFav('director', name) ? t('director.unfavorite') : t('director.favorite')"
                    class="transition"
                    :class="isFav('director', name) ? 'text-danger' : 'text-fg-5 hover:text-danger'"
                  >
                    <Heart class="w-3.5 h-3.5" :fill="isFav('director', name) ? 'currentColor' : 'none'" />
                  </button>
                </template>
              </div>
            </div>

            <!-- Action Buttons Group: BT Search, BFTV Search, Google Search, Share Card, Favorite -->
            <div class="flex items-center gap-2 flex-wrap pt-2">
              <ResourceSearchWidget type="movie" :title="movie.title" />
              <button
                type="button"
                @click="showShareModal = true"
                class="py-1.5 px-3 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition cursor-pointer"
                :title="t('movie.shareCardTooltip')"
              >
                <Share2 class="w-3.5 h-3.5" />
                <span>{{ t('movie.shareCard') }}</span>
              </button>
              <button
                @click="emit('toggle-favorite', movie)"
                :class="[
                  'py-1.5 px-3 rounded-xl text-xs font-medium border flex items-center gap-1.5 transition cursor-pointer',
                  isFavorite
                    ? 'bg-danger-fill/20 text-danger-soft border-danger-fill/40'
                    : 'bg-surface-2/60 hover:bg-surface-3 border-line text-fg-3 hover:text-danger'
                ]"
              >
                <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
                <span>{{ isFavorite ? t('movie.favorited') : t('movie.favorite') }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Description (Chinese when translated, original otherwise) - Positioned below posters and metadata, extending full width to the right -->
        <div
          v-if="movie.description || hasZh"
          class="relative z-10 mt-6 w-full text-xs md:text-sm text-fg-2 leading-relaxed bg-sunken/60 border border-line/60 p-4 md:p-5 rounded-2xl"
        >
          <div class="flex items-center justify-between gap-2 mb-2 pb-2 border-b border-line/40 flex-wrap">
            <div class="text-xs font-bold text-fg-3 uppercase tracking-wider flex items-center gap-2">
              <Languages class="w-3.5 h-3.5 text-accent" />
              <span>{{ t('movie.synopsis') }}</span>
              <span v-if="hasZh && !showOriginal" class="text-success-fill/80 text-[11px] font-normal normal-case">{{ t('episode.showTranslation') }}</span>
              <span v-else-if="hasZh" class="text-fg-5 text-[11px] font-normal normal-case">{{ t('episode.showOriginal') }}</span>
            </div>

            <div class="flex items-center gap-2">
              <!-- The synopsis is translated but some episodes are not -->
              <button
                v-if="pendingEpisodeCount > 0"
                @click="translateNow"
                :disabled="isTranslating"
                class="text-[10px] px-2 py-0.5 rounded-md bg-accent-fill/10 hover:bg-accent-fill/20 text-accent border border-accent-fill/30 transition flex items-center gap-1 disabled:opacity-50 cursor-pointer"
                :title="t('movie.clipsUntranslatedHint')"
              >
                <Loader2 v-if="isTranslating" class="w-3 h-3 animate-spin" />
                <Languages v-else class="w-3 h-3" />
                {{ isTranslating ? t('movie.translating') : `${t('movie.translateEpisodes')} (${pendingEpisodeCount})` }}
              </button>
              <button
                v-if="hasZh"
                @click="toggleOriginal"
                class="text-xs px-2.5 py-1 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg-2 border border-line-strong transition cursor-pointer"
              >
                {{ showOriginal ? t('movie.showTranslation') : t('movie.showOriginal') }}
              </button>
              <button
                v-else-if="movie.description"
                @click="translateNow"
                :disabled="isTranslating"
                class="text-xs px-2.5 py-1 rounded-lg bg-accent-fill/10 hover:bg-accent-fill/20 text-accent border border-accent-fill/30 transition flex items-center gap-1 disabled:opacity-50 cursor-pointer"
              >
                <Loader2 v-if="isTranslating" class="w-3.5 h-3.5 animate-spin" />
                <Languages v-else class="w-3.5 h-3.5" />
                {{ isTranslating ? t('movie.translating') : t('movie.translateToZh') }}
              </button>
            </div>
          </div>

          <!--
            Long synopses scroll in place instead of stretching the card: some
            descriptions run to 8,000+ characters, which would otherwise push the
            cast list and everything below it far out of view.
          -->
          <div
            ref="descriptionBoxRef"
            class="max-h-72 overflow-y-auto pr-2 -mr-2"
            @scroll.passive="onDescriptionScroll"
          >
            <p class="whitespace-pre-line synopsis-text leading-relaxed select-text" data-privacy="text">{{ displayedDescription }}</p>
          </div>

          <!-- Fades in while there is more text below, so the cut-off is visible -->
          <div
            v-if="descriptionOverflows && !descriptionAtEnd"
            class="mt-1.5 text-xs text-fg-4 flex items-center gap-1"
          >
            <ChevronDown class="w-3.5 h-3.5" />
            {{ t('movie.scrollNotice') }}
          </div>

          <div v-if="translateError" class="text-xs text-danger mt-2">
            {{ translateError }}
          </div>
        </div>
      </div>

      <!-- Cast Section -->
      <div v-if="movie.performers && movie.performers.length > 0" class="p-6 md:p-8 border-b border-line space-y-3">
        <div class="flex items-center gap-2 text-sm font-bold text-fg-2">
          <Tag class="w-4 h-4 text-accent" />
          <span>{{ t('movie.cast') }} ({{ movie.performers.length }})</span>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="p in movie.performers"
            :key="p.id"
            @click="emit('select-performer', p.id)"
            class="group flex items-center gap-2 px-3 py-1.5 rounded-xl bg-surface-2 hover:bg-accent-fill/10 border border-line-strong hover:border-accent-fill/40 text-xs font-medium text-fg-2 hover:text-accent transition"
          >
            <div class="w-6 h-6 rounded-full overflow-hidden bg-surface-3 flex items-center justify-center text-[10px] font-bold text-fg-2 group-hover:bg-accent-fill group-hover:text-on-fill transition shrink-0">
              <img
                v-if="p.image_url"
                :src="getImageUrl(p.image_url)"
                :alt="p.name"
                loading="lazy"
                referrerpolicy="no-referrer"
                class="w-full h-full object-cover object-top"
                @error="(e) => ((e.target as HTMLImageElement).style.display = 'none')"
              />
              <span v-else>{{ p.name.charAt(0).toUpperCase() }}</span>
            </div>
            <span>{{ p.name }}</span>
          </button>
        </div>
      </div>

      <!-- Chapters / Scenes Section -->
      <div v-if="movie.episodes && movie.episodes.length > 0" class="p-6 md:p-8 space-y-4">
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-2 text-sm font-bold text-fg-2">
            <Layers class="w-4 h-4 text-accent" />
            <span>{{ t('movie.scenes') }} ({{ movie.episodes.length }})</span>
          </div>
          <button
            type="button"
            @click="allScenesExpanded = !allScenesExpanded"
            class="flex items-center gap-1 text-xs px-2.5 py-1 rounded-lg bg-surface-2 hover:bg-surface-3 text-accent hover:text-accent-soft border border-line-strong transition cursor-pointer"
            :title="allScenesExpanded ? t('common.collapseAll') : t('common.expandAll')"
          >
            <span>{{ allScenesExpanded ? t('common.collapseAll') : t('common.expandAll') }}</span>
            <component :is="allScenesExpanded ? ChevronUp : ChevronDown" class="w-3.5 h-3.5" />
          </button>
        </div>

        <div class="grid grid-cols-1 gap-3">
          <!-- One row per scene, in the shared reading layout — see EpisodeRow.
               The film's own scenes are ordered by id, so the row's index is the
               scene's position in the film (the same number the library shows).
               No 出处 and no year: this *is* the film. -->
          <EpisodeRow
            v-for="(ep, i) in movie.episodes"
            :key="ep.id"
            :episode="ep"
            :ordinal="i + 1"
            :ordinal-count="movie.episodes.length"
            :show-source="false"
            :show-year="false"
            :lang="showOriginal ? 'en' : lang"
            :expanded="allScenesExpanded"
            zoom-on-click
            :is-favorite="isFav('episode', String(ep.id))"
            @select-episode-id="emit('select-episode-id', $event)"
            @toggle-favorite="emit('toggle-entity-favorite', 'episode', String(ep.id))"
          />
        </div>
      </div>
    </div>
  </div>

  <!-- Series Franchise Modal -->
  <SeriesModal
    v-if="showSeriesModal && seriesData && movie"
    :series="seriesData"
    :current-movie-id="movie.id"
    :z-index="(zIndex ?? 50) + 20"
    :is-favorite="isFav('series', seriesData.root_title)"
    @close="showSeriesModal = false"
    @select-movie="onSelectSeriesMovie"
    @toggle-favorite="(rootTitle) => emit('toggle-entity-favorite', 'series', rootTitle)"
  />

  <!-- Share Card Modal -->
  <ShareCardModal
    :show="showShareModal"
    :data="shareCardData"
    :z-index="(zIndex ?? 50) + 25"
    @close="showShareModal = false"
  />
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
