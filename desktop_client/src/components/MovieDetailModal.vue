<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue';
import { X, Film, Clock, Heart, Building2, Tag, Layers, Clapperboard, Star, Bookmark, CheckCircle2, Plus, Sparkles, Languages, Loader2, ChevronDown, ExternalLink } from '@lucide/vue';
import type { Movie, UserTag, FavoriteType, MovieSeriesResponse } from '../types';
import EpisodeRow from './EpisodeRow.vue';
import SeriesModal from './SeriesModal.vue';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';
import { titlePrimary, titleSecondary } from '../utils/bilingual';
import { trCategory } from '../utils/glossary';
import { api, IS_TAURI } from '../api';
import { pluginsConfig, openBtMovieSearch, openBftvMovie, openGoogleSearch } from '../services/pluginManager';

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
  (e: 'filter-studio', studioName: string): void;
  (e: 'filter-director', directorName: string): void;
  (e: 'toggle-favorite', movie: Movie): void;
  /** Studio / director / episode hearts; the film itself has its own event above. */
  (e: 'toggle-entity-favorite', type: FavoriteType, key: string): void;
  (e: 'user-data-changed', movieId: number): void;
  (e: 'translated', movieId: number, descriptionZh: string): void;
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

const activeCoverIndex = ref(0);

const showRatingCard = ref(false);
const showPrivateNotes = ref(false);

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

/** Whether the original synopsis is on screen right now — global switch, unless overridden. */
const showOriginal = computed(() => showOriginalOverride.value ?? props.lang === 'en');

function toggleOriginal() {
  showOriginalOverride.value = !showOriginal.value;
}

watch(() => props.movie, (m) => {
  zhDescription.value = m?.description_zh || null;
  showOriginalOverride.value = null;
  translateError.value = '';
  showRatingCard.value = false;
  showPrivateNotes.value = false;

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
watch([displayedDescription, showPrivateNotes], async () => {
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
  if (result) {
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
    translateError.value = '翻译失败。请到「设置 → 翻译服务来源」确认 API Key 可用（可点「测试」验证）。';
  }
  isTranslating.value = false;
}
const userRating = ref<number | null>(null);
const userStatus = ref<string | null>(null);
const userNotes = ref('');
const selectedTagIds = ref<number[]>([]);
const availableTags = ref<UserTag[]>([]);
const isCreatingTag = ref(false);
const newTagName = ref('');
const newTagColor = ref('#f59e0b');
const saveSuccess = ref(false);

/** Drives the badge on the toggle: the button should show at a glance
 *  whether this film already carries private annotations. */
const hasPrivateData = computed(() =>
  userRating.value != null ||
  Boolean(userStatus.value) ||
  Boolean(userNotes.value.trim()) ||
  selectedTagIds.value.length > 0
);

async function loadUserData() {
  if (!props.movie) return;
  // Available tags
  availableTags.value = await api.getUserTags();

  // Movie user data
  const data = await api.getMovieUserData(props.movie.id);
  if (data) {
    userRating.value = data.rating ?? null;
    userStatus.value = data.status ?? null;
    userNotes.value = data.notes ?? '';
    selectedTagIds.value = (data.tags || []).map((t: UserTag) => t.id);
  } else {
    userRating.value = null;
    userStatus.value = null;
    userNotes.value = '';
    selectedTagIds.value = [];
  }
}

watch(() => props.movie, () => {
  activeCoverIndex.value = 0;
  loadUserData();
}, { immediate: true });

async function persistUserData() {
  if (!props.movie) return;
  await api.saveMovieUserData(props.movie.id, {
    rating: userRating.value,
    status: userStatus.value,
    notes: userNotes.value,
    tag_ids: selectedTagIds.value,
  });
  saveSuccess.value = true;
  setTimeout(() => { saveSuccess.value = false; }, 2000);
  emit('user-data-changed', props.movie.id);
}

function setRating(val: number) {
  userRating.value = userRating.value === val ? null : val;
  persistUserData();
}

function setStatus(st: string) {
  userStatus.value = userStatus.value === st ? null : st;
  persistUserData();
}

function handleStatusClick(stId: string) {
  if (stId === 'watched') {
    if (userStatus.value === 'watched') {
      userStatus.value = null;
      showRatingCard.value = false;
    } else {
      userStatus.value = 'watched';
      showRatingCard.value = true;
    }
    persistUserData();
  } else {
    setStatus(stId);
    if (showRatingCard.value && userStatus.value !== 'watched') {
      showRatingCard.value = false;
    }
  }
}

function toggleTag(tagId: number) {
  if (selectedTagIds.value.includes(tagId)) {
    selectedTagIds.value = selectedTagIds.value.filter(id => id !== tagId);
  } else {
    selectedTagIds.value.push(tagId);
  }
  persistUserData();
}

async function handleCreateTag() {
  if (!newTagName.value.trim()) return;
  const created = await api.createUserTag(newTagName.value.trim(), newTagColor.value);
  if (created) {
    availableTags.value.push(created);
    selectedTagIds.value.push(created.id);
    newTagName.value = '';
    isCreatingTag.value = false;
    persistUserData();
  }
}

/**
 * The poster to show, already routed through the local image cache.
 *
 * Both the poster and the blurred backdrop read this, so the caching (and the
 * quality upgrade it performs) is applied in one place rather than at each use.
 */
const currentCover = computed(() => {
  const covers = props.movie?.covers;
  const raw = covers && covers.length > 0
    ? covers[activeCoverIndex.value] || props.movie?.cover_full
    : props.movie?.cover_full;
  return raw ? getImageUrl(raw) : '';
});

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape' || props.isTop === false) return;
  if (!claimEscape(e)) return;
  if (showRatingCard.value) {
    showRatingCard.value = false;
    return;
  }
  if (showPrivateNotes.value) {
    showPrivateNotes.value = false;
    return;
  }
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
          <!-- Poster Container with Multi-Cover Switching -->
          <div class="flex flex-col items-center gap-2 shrink-0">
            <!-- data-zoom-click: the poster has no click action of its own, so one
                 click opens the viewer (see utils/lightbox.ts). -->
            <div class="w-44 md:w-56 aspect-[3/4] rounded-2xl overflow-hidden shadow-2xl border border-line-strong/60 bg-sunken relative group">
              <img
                v-if="currentCover"
                :src="currentCover"
                :alt="movie.title"
                referrerpolicy="no-referrer"
                data-zoom-click
                class="w-full h-full object-cover transition-all duration-300"
              />
              <div v-else class="w-full h-full flex flex-col items-center justify-center text-fg-5 p-4 text-center">
                <Film class="w-12 h-12 mb-2 stroke-1" />
                <span class="text-xs">无封面</span>
              </div>
            </div>

            <!-- Front / Back Cover Switcher Pills -->
            <div v-if="movie.covers && movie.covers.length > 1" class="flex gap-1.5 p-1 bg-surface/90 border border-line rounded-xl shadow">
              <button
                v-for="(_, idx) in movie.covers"
                :key="idx"
                @click="activeCoverIndex = idx"
                :class="[
                  'px-3 py-1 rounded-lg text-xs font-semibold transition',
                  activeCoverIndex === idx
                    ? 'bg-accent-fill text-on-fill shadow'
                    : 'text-fg-3 hover:text-fg-2 hover:bg-surface-2'
                ]"
              >
                {{ idx === 0 ? '正面封面' : idx === 1 ? '封底背面' : `封面 ${idx + 1}` }}
              </button>
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
                {{ movie.duration_mins }} 分钟
              </span>
              <span v-if="movie.category" class="px-2.5 py-1 rounded-lg text-xs font-medium bg-surface-2 text-fg-2 border border-line-strong">
                {{ categoryLabel }}
              </span>
              <button
                @click="emit('toggle-favorite', movie)"
                :class="[
                  'ml-auto px-3 py-1 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
                  isFavorite
                    ? 'bg-danger-fill/20 text-danger-soft border-danger-fill/40'
                    : 'bg-surface-2 hover:bg-surface-3 border-line-strong text-fg-3 hover:text-danger'
                ]"
              >
                <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
                <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
              </button>
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
                :title="`查看「${seriesData.root_title}」全系列共 ${seriesData.items.length} 部作品`"
              >
                <Film class="w-3.5 h-3.5 text-accent" />
                <span>查看全系列作品 ({{ seriesData.items.length }}部)</span>
                <span class="text-[10px] text-fg-4 font-normal">· {{ seriesData.root_title }}</span>
                <span class="text-accent group-hover:translate-x-0.5 transition-transform">→</span>
              </button>
            </div>

            <!-- Studio & Director Pills (each filterable and favoritable) -->
            <div class="flex items-center gap-3 flex-wrap">
              <div v-if="movie.studio_name" class="flex items-center gap-1.5">
                <Building2 class="w-4 h-4 text-fg-3" />
                <button
                  @click="emit('filter-studio', movie.studio_name)"
                  class="text-xs font-semibold text-accent hover:underline bg-surface-2/80 px-2.5 py-1 rounded-md border border-line-strong/60"
                >
                  {{ movie.studio_name }}
                </button>
                <button
                  @click="emit('toggle-entity-favorite', 'studio', movie.studio_name)"
                  :title="isFav('studio', movie.studio_name) ? '取消收藏该片商' : '收藏该片商'"
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
                <span class="text-fg-4">导演:</span>
                <template v-for="(name, i) in directorNames" :key="name">
                  <span v-if="i > 0" class="text-fg-5">·</span>
                  <button
                    @click="emit('filter-director', name)"
                    class="font-medium text-fg-2 hover:text-accent-soft hover:underline transition"
                    :title="`查看 ${name} 的全部影片`"
                  >
                    {{ name }}
                  </button>
                  <button
                    @click="emit('toggle-entity-favorite', 'director', name)"
                    :title="isFav('director', name) ? '取消收藏该导演' : '收藏该导演'"
                    class="transition"
                    :class="isFav('director', name) ? 'text-danger' : 'text-fg-5 hover:text-danger'"
                  >
                    <Heart class="w-3 h-3" :fill="isFav('director', name) ? 'currentColor' : 'none'" />
                  </button>
                </template>
              </div>
            </div>

            <!-- Description (Chinese when translated, original otherwise) -->
            <div
              v-if="movie.description || hasZh"
              class="text-xs md:text-sm text-fg-2 leading-relaxed bg-sunken/60 border border-line/60 p-4 rounded-xl"
            >
              <div class="flex items-center justify-between gap-2 mb-1.5">
                <div class="text-[11px] font-semibold text-fg-4 uppercase tracking-wider flex items-center gap-1.5">
                  <Languages class="w-3 h-3" />
                  剧情简介
                  <span v-if="hasZh && !showOriginal" class="text-success-fill/80 normal-case">中文</span>
                  <span v-else-if="hasZh" class="text-fg-5 normal-case">原文</span>
                </div>

                <div class="flex items-center gap-2">
                  <!-- The synopsis is translated but some episodes are not -->
                  <button
                    v-if="pendingEpisodeCount > 0 && !IS_TAURI"
                    @click="translateNow"
                    :disabled="isTranslating"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-accent-fill/10 hover:bg-accent-fill/20 text-accent border border-accent-fill/30 transition flex items-center gap-1 disabled:opacity-50"
                    title="该影片的片段简介尚未翻译，会与简介一起送翻译"
                  >
                    <Loader2 v-if="isTranslating" class="w-3 h-3 animate-spin" />
                    <Languages v-else class="w-3 h-3" />
                    {{ isTranslating ? '翻译中...' : `翻译片段 (${pendingEpisodeCount})` }}
                  </button>
                  <button
                    v-if="hasZh"
                    @click="toggleOriginal"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg-2 border border-line-strong transition"
                  >
                    {{ showOriginal ? '显示中文' : '显示原文' }}
                  </button>
                  <button
                    v-else-if="movie.description && !IS_TAURI"
                    @click="translateNow"
                    :disabled="isTranslating"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-accent-fill/10 hover:bg-accent-fill/20 text-accent border border-accent-fill/30 transition flex items-center gap-1 disabled:opacity-50"
                  >
                    <Loader2 v-if="isTranslating" class="w-3 h-3 animate-spin" />
                    <Languages v-else class="w-3 h-3" />
                    {{ isTranslating ? '翻译中...' : '翻译成中文' }}
                  </button>
                  <!-- Desktop build has no server process to relay the request -->
                  <span
                    v-else-if="movie.description"
                    class="text-[10px] text-fg-5"
                    title="桌面版请在项目目录运行 python3 translate.py 批量翻译"
                  >
                    未翻译
                  </span>
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
                <p class="whitespace-pre-line">{{ displayedDescription }}</p>
              </div>

              <!-- Fades in while there is more text below, so the cut-off is visible -->
              <div
                v-if="descriptionOverflows && !descriptionAtEnd"
                class="mt-1 text-[10px] text-fg-4 flex items-center gap-1"
              >
                <ChevronDown class="w-3 h-3" />
                简介较长，可在框内滚动查看
              </div>

              <div v-if="translateError" class="text-[11px] text-danger mt-2">
                {{ translateError }}
              </div>
            </div>

            <!-- Watchlist Status & Rating Row (directly below synopsis) -->
            <div class="pt-1 flex flex-col gap-2.5">
              <div class="flex items-center gap-2 flex-wrap">
                <!-- Status Pills -->
                <button
                  v-for="st in [
                    { id: 'wishlist', label: '想看', icon: Bookmark },
                    { id: 'watched', label: '已看', icon: CheckCircle2 },
                    { id: 'favorite', label: '喜爱', icon: Heart }
                  ]"
                  :key="st.id"
                  @click="handleStatusClick(st.id)"
                  :class="[
                    'py-1.5 px-3 rounded-xl text-xs font-medium border flex items-center gap-1.5 transition',
                    userStatus === st.id
                      ? 'bg-accent-fill text-on-fill border-accent font-bold shadow-md shadow-accent-fill/20'
                      : 'bg-surface-2/80 text-fg-3 border-line-strong/60 hover:text-fg-2 hover:bg-surface-2'
                  ]"
                >
                  <component :is="st.icon" class="w-3.5 h-3.5" :fill="userStatus === st.id ? 'currentColor' : 'none'" />
                  <span>{{ st.label }}</span>
                </button>

                <!-- Current Rating Pill if user has rated (click to open rating popover) -->
                <button
                  v-if="userRating"
                  @click="showRatingCard = !showRatingCard"
                  class="py-1 px-2.5 rounded-xl text-xs font-bold border border-accent/40 bg-accent/10 text-accent flex items-center gap-1 hover:bg-accent/20 transition"
                  title="点击调整评分"
                >
                  <Star class="w-3.5 h-3.5 fill-accent text-accent" />
                  <span>{{ userRating.toFixed(1) }} 星</span>
                </button>

                <!-- Resource Search Plugin Button Group -->
                <div v-if="pluginsConfig.resourceSearchEnabled" class="flex items-center gap-1.5 flex-wrap ml-auto">
                  <button
                    @click="openBtMovieSearch(movie.title)"
                    class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition"
                    :title="`在 BT 站检索「${movie.title}」资源`"
                  >
                    <ExternalLink class="w-3.5 h-3.5" />
                    <span>BT 搜索</span>
                  </button>

                  <button
                    v-if="pluginsConfig.webJumpConfig.bftvMovieEnabled"
                    @click="openBftvMovie(movie.title)"
                    class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition"
                    :title="`在 BFTV 检索「${movie.title}」影片资料`"
                  >
                    <ExternalLink class="w-3.5 h-3.5 text-amber-400" />
                    <span>在BFTV搜索影片资料</span>
                  </button>

                  <button
                    v-if="pluginsConfig.webJumpConfig.googleSearchEnabled"
                    @click="openGoogleSearch(movie.title)"
                    class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition"
                    :title="`在 Google 检索「${movie.title}」`"
                  >
                    <ExternalLink class="w-3.5 h-3.5 text-blue-400" />
                    <span>Google 搜索</span>
                  </button>
                </div>

                <!-- Subordinate feature: Tags & Notes toggle button -->
                <button
                  @click="showPrivateNotes = !showPrivateNotes"
                  :class="[
                    pluginsConfig.resourceSearchEnabled ? '' : 'ml-auto',
                    'py-1.5 px-2.5 rounded-xl text-xs font-medium border flex items-center gap-1.5 transition',
                    showPrivateNotes
                      ? 'bg-accent-fill/20 text-accent-soft border-accent-fill/40'
                      : 'bg-surface-2/60 hover:bg-surface-3 border-line text-fg-4 hover:text-fg-2'
                  ]"
                  title="展开自定义标签与私密便签"
                >
                  <Sparkles class="w-3.5 h-3.5" />
                  <span>便签与标签</span>
                  <span
                    v-if="hasPrivateData"
                    class="w-1.5 h-1.5 rounded-full bg-accent"
                    title="已有私密记录"
                  ></span>
                </button>
              </div>

              <!-- Rating Card: only pops up when '已看' is clicked or user clicks the rating badge -->
              <div
                v-if="showRatingCard"
                class="p-3 rounded-2xl bg-surface/95 border border-line shadow-xl flex items-center justify-between gap-3 flex-wrap animate-fade-in"
              >
                <div class="flex items-center gap-3">
                  <span class="text-xs font-semibold text-fg-2">评星打分：</span>
                  <div class="flex items-center gap-1">
                    <button
                      v-for="star in 5"
                      :key="star"
                      @click="setRating(star)"
                      class="p-1 hover:scale-125 transition-transform"
                      :title="`评分 ${star} 星`"
                    >
                      <Star
                        class="w-5 h-5 transition-colors"
                        :class="userRating && userRating >= star ? 'text-accent fill-accent' : 'text-fg-5 hover:text-accent-soft'"
                      />
                    </button>
                  </div>
                  <span class="text-xs font-bold text-accent font-mono ml-1">
                    {{ userRating ? `${userRating.toFixed(1)} 星` : '未评' }}
                  </span>
                  <button
                    v-if="userRating"
                    @click="setRating(userRating)"
                    class="text-[11px] text-fg-5 hover:text-danger ml-2 transition"
                  >
                    清除
                  </button>
                </div>

                <div class="flex items-center gap-2 text-xs">
                  <span v-if="saveSuccess" class="text-success flex items-center gap-1 font-medium text-[11px]">
                    <CheckCircle2 class="w-3.5 h-3.5" /> 已保存
                  </span>
                  <button
                    @click="showRatingCard = false"
                    class="text-fg-4 hover:text-fg-2 px-2 py-0.5 rounded-md hover:bg-surface-2 transition text-[11px]"
                  >
                    完成
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Private Tags & Notes Drawer/Card (Subordinate feature) -->
      <div v-if="showPrivateNotes" class="p-6 md:p-8 border-b border-line bg-sunken/40 space-y-4 animate-fade-in">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm font-bold text-fg-2">
            <Sparkles class="w-4 h-4 text-accent" />
            <span>私密便签与标签 (仅本地可见)</span>
          </div>
          <div class="flex items-center gap-3">
            <span v-if="saveSuccess" class="text-xs text-success font-medium animate-fade-in flex items-center gap-1">
              <CheckCircle2 class="w-3.5 h-3.5" /> 已自动保存
            </span>
            <button
              @click="showPrivateNotes = false"
              class="w-6 h-6 rounded-lg bg-surface-2 hover:bg-surface-3 border border-line-strong flex items-center justify-center text-fg-3 hover:text-fg transition"
              title="收起"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <!-- Custom Tags -->
          <div class="p-4 rounded-2xl bg-surface/80 border border-line space-y-3">
            <div class="text-[11px] text-fg-3 font-medium flex items-center justify-between">
              <span>自定义分类标签</span>
              <button
                @click="isCreatingTag = !isCreatingTag"
                class="text-[10px] text-accent hover:text-accent-soft flex items-center gap-0.5"
              >
                <Plus class="w-3 h-3" /> 新建标签
              </button>
            </div>

            <!-- Create new tag inline form -->
            <div v-if="isCreatingTag" class="flex items-center gap-2 mb-2 p-2 rounded-lg bg-sunken border border-line">
              <input
                v-model="newTagName"
                type="text"
                placeholder="标签名称"
                @keyup.enter="handleCreateTag"
                class="flex-1 bg-transparent text-xs text-fg-2 outline-none placeholder-fg-5"
              />
              <input
                v-model="newTagColor"
                type="color"
                class="w-5 h-5 rounded cursor-pointer bg-transparent border-0"
              />
              <button
                @click="handleCreateTag"
                class="px-2 py-0.5 rounded bg-accent-fill text-on-fill text-[11px] font-bold"
              >
                添加
              </button>
            </div>

            <!-- Tag pills list -->
            <div class="flex flex-wrap gap-1.5 min-h-[28px]">
              <button
                v-for="t in availableTags"
                :key="t.id"
                @click="toggleTag(t.id)"
                :class="[
                  'px-2 py-0.5 rounded-lg text-xs font-medium border transition flex items-center gap-1',
                  selectedTagIds.includes(t.id) ? 'shadow' : 'opacity-50 hover:opacity-100'
                ]"
                :style="{
                  color: t.color,
                  borderColor: `${t.color}60`,
                  backgroundColor: selectedTagIds.includes(t.id) ? `${t.color}25` : 'transparent'
                }"
              >
                <span>{{ t.name }}</span>
                <span v-if="selectedTagIds.includes(t.id)">✓</span>
              </button>
              <div v-if="availableTags.length === 0 && !isCreatingTag" class="text-xs text-fg-5 italic">
                点击右上角「新建标签」添加个人分类
              </div>
            </div>
          </div>

          <!-- Notes textarea -->
          <div class="p-4 rounded-2xl bg-surface/80 border border-line space-y-2">
            <div class="text-[11px] text-fg-3 font-medium">私密备忘 / 观后感</div>
            <textarea
              v-model="userNotes"
              @blur="persistUserData"
              rows="3"
              placeholder="记录观后感、精彩场景节点或备忘..."
              class="w-full bg-sunken/80 border border-line-strong/80 rounded-xl p-2.5 text-xs text-fg-2 placeholder-fg-5 outline-none focus:border-accent-fill transition resize-none"
            ></textarea>
          </div>
        </div>
      </div>

      <!-- Cast Section -->
      <div v-if="movie.performers && movie.performers.length > 0" class="p-6 md:p-8 border-b border-line space-y-3">
        <div class="flex items-center gap-2 text-sm font-bold text-fg-2">
          <Tag class="w-4 h-4 text-accent" />
          <span>演职人员 ({{ movie.performers.length }})</span>
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
        <div class="flex items-center gap-2 text-sm font-bold text-fg-2">
          <Layers class="w-4 h-4 text-accent" />
          <span>收录章节 / 场景片段 ({{ movie.episodes.length }})</span>
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
            :lang="lang"
            zoom-on-click
            :is-favorite="isFav('episode', String(ep.id))"
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
