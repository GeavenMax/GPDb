<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue';
import { X, Film, Clock, Heart, Building2, Tag, Layers, Clapperboard, Star, Bookmark, CheckCircle2, Plus, Sparkles, Languages, Loader2, ChevronDown } from '@lucide/vue';
import type { Movie, UserTag, FavoriteType } from '../types';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';
import { api, IS_TAURI } from '../api';

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

/**
 * The private star rating / tags / notes block is an aside, not part of the
 * film's own data, so it stays collapsed behind a button in the header instead
 * of pushing the synopsis and cast off the first screenful.
 */
const showPrivate = ref(false);

// Synopses carry both the original English and (once translated) the Chinese text.
const zhDescription = ref<string | null>(null);
const showOriginal = ref(false);
const isTranslating = ref(false);
const translateError = ref('');

watch(() => props.movie, (m) => {
  zhDescription.value = m?.description_zh || null;
  showOriginal.value = false;
  translateError.value = '';
  showPrivate.value = false;

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
watch([displayedDescription, showPrivate], async () => {
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
      showOriginal.value = false;
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
  // While the private-annotation panel is open it owns Escape.
  if (showPrivate.value) {
    showPrivate.value = false;
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
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8 bg-black/80 backdrop-blur-md animate-fade-in"
    :style="{ zIndex: zIndex ?? 50 }"
    @click.self="emit('close')"
  >
    <!-- Modal Card -->
    <div
      class="relative w-full max-w-4xl max-h-[90vh] bg-zinc-900 border border-zinc-700/80 rounded-3xl shadow-2xl overflow-y-auto flex flex-col darkScrollbars text-zinc-100"
    >
      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-4 right-4 z-20 w-8 h-8 rounded-full bg-black/60 hover:bg-black/90 border border-white/20 flex items-center justify-center text-zinc-300 hover:text-white transition"
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
      <div class="relative w-full shrink-0 overflow-hidden bg-zinc-950 p-6 md:p-8 border-b border-zinc-800">
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
            <div class="w-44 md:w-56 aspect-[3/4] rounded-2xl overflow-hidden shadow-2xl border border-zinc-700/60 bg-zinc-950 relative group">
              <img
                v-if="currentCover"
                :src="currentCover"
                :alt="movie.title"
                referrerpolicy="no-referrer"
                class="w-full h-full object-cover transition-all duration-300"
              />
              <div v-else class="w-full h-full flex flex-col items-center justify-center text-zinc-600 p-4 text-center">
                <Film class="w-12 h-12 mb-2 stroke-1" />
                <span class="text-xs">无封面</span>
              </div>
            </div>

            <!-- Front / Back Cover Switcher Pills -->
            <div v-if="movie.covers && movie.covers.length > 1" class="flex gap-1.5 p-1 bg-zinc-900/90 border border-zinc-800 rounded-xl shadow">
              <button
                v-for="(_, idx) in movie.covers"
                :key="idx"
                @click="activeCoverIndex = idx"
                :class="[
                  'px-3 py-1 rounded-lg text-xs font-semibold transition',
                  activeCoverIndex === idx
                    ? 'bg-amber-500 text-black shadow'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800'
                ]"
              >
                {{ idx === 0 ? '正面封面' : idx === 1 ? '封底背面' : `封面 ${idx + 1}` }}
              </button>
            </div>
          </div>

          <!-- Metadata -->
          <div class="flex-1 space-y-4">
            <div class="flex items-center gap-2 flex-wrap">
              <span v-if="movie.release_year" class="px-2.5 py-1 rounded-lg text-xs font-bold bg-amber-500/10 text-amber-400 border border-amber-500/30">
                {{ movie.release_year }}
              </span>
              <span v-if="movie.duration_mins" class="px-2.5 py-1 rounded-lg text-xs font-medium bg-zinc-800 text-zinc-300 border border-zinc-700 flex items-center gap-1.5">
                <Clock class="w-3.5 h-3.5" />
                {{ movie.duration_mins }} 分钟
              </span>
              <span v-if="movie.category" class="px-2.5 py-1 rounded-lg text-xs font-medium bg-zinc-800 text-zinc-300 border border-zinc-700">
                {{ movie.category }}
              </span>
              <!-- Private annotations live behind this button (aside, not film data) -->
              <button
                @click="showPrivate = !showPrivate"
                :class="[
                  'ml-auto px-3 py-1 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
                  showPrivate
                    ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                    : 'bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-400 hover:text-amber-400'
                ]"
                :title="showPrivate ? '收起我的私密评星与标记' : '展开我的私密评星、标签与笔记（仅本地可见）'"
              >
                <Sparkles class="w-3.5 h-3.5" />
                <span>我的标记</span>
                <span
                  v-if="hasPrivateData"
                  class="w-1.5 h-1.5 rounded-full bg-amber-400"
                  title="已有私密记录"
                ></span>
              </button>

              <button
                @click="emit('toggle-favorite', movie)"
                :class="[
                  'px-3 py-1 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
                  isFavorite
                    ? 'bg-rose-500/20 text-rose-300 border-rose-500/40'
                    : 'bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-400 hover:text-rose-400'
                ]"
              >
                <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
                <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
              </button>
            </div>

            <h1 class="text-2xl md:text-3xl font-extrabold text-white tracking-tight">
              {{ movie.title }}
            </h1>

            <!-- Studio & Director Pills (each filterable and favoritable) -->
            <div class="flex items-center gap-3 flex-wrap">
              <div v-if="movie.studio_name" class="flex items-center gap-1.5">
                <Building2 class="w-4 h-4 text-zinc-400" />
                <button
                  @click="emit('filter-studio', movie.studio_name)"
                  class="text-xs font-semibold text-amber-400 hover:underline bg-zinc-800/80 px-2.5 py-1 rounded-md border border-zinc-700/60"
                >
                  {{ movie.studio_name }}
                </button>
                <button
                  @click="emit('toggle-entity-favorite', 'studio', movie.studio_name)"
                  :title="isFav('studio', movie.studio_name) ? '取消收藏该片商' : '收藏该片商'"
                  class="transition"
                  :class="isFav('studio', movie.studio_name) ? 'text-rose-400' : 'text-zinc-600 hover:text-rose-400'"
                >
                  <Heart class="w-3.5 h-3.5" :fill="isFav('studio', movie.studio_name) ? 'currentColor' : 'none'" />
                </button>
              </div>

              <div v-if="movie.director_name" class="flex items-center gap-1.5 bg-zinc-900 border border-zinc-800 px-2.5 py-1 rounded-md text-xs text-zinc-300">
                <Clapperboard class="w-3.5 h-3.5 text-amber-400" />
                <span class="text-zinc-500">导演:</span>
                <button
                  @click="emit('filter-director', movie.director_name)"
                  class="font-medium text-zinc-200 hover:text-amber-300 hover:underline transition"
                >
                  {{ movie.director_name }}
                </button>
                <button
                  @click="emit('toggle-entity-favorite', 'director', movie.director_name)"
                  :title="isFav('director', movie.director_name) ? '取消收藏该导演' : '收藏该导演'"
                  class="transition"
                  :class="isFav('director', movie.director_name) ? 'text-rose-400' : 'text-zinc-600 hover:text-rose-400'"
                >
                  <Heart class="w-3.5 h-3.5" :fill="isFav('director', movie.director_name) ? 'currentColor' : 'none'" />
                </button>
              </div>
            </div>

            <!-- Description (Chinese when translated, original otherwise) -->
            <div
              v-if="movie.description || hasZh"
              class="text-xs md:text-sm text-zinc-300 leading-relaxed bg-zinc-950/60 border border-zinc-800/60 p-4 rounded-xl"
            >
              <div class="flex items-center justify-between gap-2 mb-1.5">
                <div class="text-[11px] font-semibold text-zinc-500 uppercase tracking-wider flex items-center gap-1.5">
                  <Languages class="w-3 h-3" />
                  剧情简介
                  <span v-if="hasZh && !showOriginal" class="text-emerald-500/80 normal-case">中文</span>
                  <span v-else-if="hasZh" class="text-zinc-600 normal-case">原文</span>
                </div>

                <div class="flex items-center gap-2">
                  <!-- The synopsis is translated but some episodes are not -->
                  <button
                    v-if="pendingEpisodeCount > 0 && !IS_TAURI"
                    @click="translateNow"
                    :disabled="isTranslating"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 transition flex items-center gap-1 disabled:opacity-50"
                    title="该影片的片段简介尚未翻译，会与简介一起送翻译"
                  >
                    <Loader2 v-if="isTranslating" class="w-3 h-3 animate-spin" />
                    <Languages v-else class="w-3 h-3" />
                    {{ isTranslating ? '翻译中...' : `翻译片段 (${pendingEpisodeCount})` }}
                  </button>
                  <button
                    v-if="hasZh"
                    @click="showOriginal = !showOriginal"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-zinc-200 border border-zinc-700 transition"
                  >
                    {{ showOriginal ? '显示中文' : '显示原文' }}
                  </button>
                  <button
                    v-else-if="movie.description && !IS_TAURI"
                    @click="translateNow"
                    :disabled="isTranslating"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 transition flex items-center gap-1 disabled:opacity-50"
                  >
                    <Loader2 v-if="isTranslating" class="w-3 h-3 animate-spin" />
                    <Languages v-else class="w-3 h-3" />
                    {{ isTranslating ? '翻译中...' : '翻译成中文' }}
                  </button>
                  <!-- Desktop build has no server process to relay the request -->
                  <span
                    v-else-if="movie.description"
                    class="text-[10px] text-zinc-600"
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
                class="max-h-72 overflow-y-auto darkScrollbars pr-2 -mr-2"
                @scroll.passive="onDescriptionScroll"
              >
                <p class="whitespace-pre-line">{{ displayedDescription }}</p>
              </div>

              <!-- Fades in while there is more text below, so the cut-off is visible -->
              <div
                v-if="descriptionOverflows && !descriptionAtEnd"
                class="mt-1 text-[10px] text-zinc-500 flex items-center gap-1"
              >
                <ChevronDown class="w-3 h-3" />
                简介较长，可在框内滚动查看
              </div>

              <div v-if="translateError" class="text-[11px] text-rose-400 mt-2">
                {{ translateError }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- User Private Annotations & Custom Tags Section (toggled from the header) -->
      <div v-if="showPrivate" class="p-6 md:p-8 border-b border-zinc-800 bg-zinc-950/40 space-y-4">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm font-bold text-zinc-300">
            <Sparkles class="w-4 h-4 text-amber-400" />
            <span>我的私密评星与标记 (仅本地可见)</span>
          </div>
          <div class="flex items-center gap-3">
            <span v-if="saveSuccess" class="text-xs text-emerald-400 font-medium animate-fade-in flex items-center gap-1">
              <CheckCircle2 class="w-3.5 h-3.5" /> 已自动保存
            </span>
            <button
              @click="showPrivate = false"
              class="w-6 h-6 rounded-lg bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 flex items-center justify-center text-zinc-400 hover:text-white transition"
              title="收起"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <!-- Left: Rating & Status -->
          <div class="p-4 rounded-2xl bg-zinc-900/80 border border-zinc-800 space-y-3">
            <!-- 5-Star interactive rater -->
            <div>
              <div class="text-[11px] text-zinc-400 font-medium mb-1.5 flex items-center justify-between">
                <span>私密星级评分</span>
                <span class="text-amber-400 font-bold font-mono">{{ userRating ? `${userRating.toFixed(1)} 星` : '未评' }}</span>
              </div>
              <div class="flex items-center gap-1.5">
                <button
                  v-for="star in 5"
                  :key="star"
                  @click="setRating(star)"
                  class="p-1 hover:scale-125 transition-transform"
                  :title="`评分 ${star} 星`"
                >
                  <Star
                    class="w-6 h-6 transition-colors"
                    :class="userRating && userRating >= star ? 'text-amber-400 fill-amber-400' : 'text-zinc-600 hover:text-amber-300'"
                  />
                </button>
                <button
                  v-if="userRating"
                  @click="setRating(userRating)"
                  class="ml-2 text-[10px] text-zinc-500 hover:text-zinc-300 transition"
                >
                  清除
                </button>
              </div>
            </div>

            <!-- Status selector -->
            <div class="pt-2 border-t border-zinc-800">
              <div class="text-[11px] text-zinc-400 font-medium mb-1.5">片单状态</div>
              <div class="flex gap-2">
                <button
                  v-for="st in [
                    { id: 'wishlist', label: '想看', icon: Bookmark },
                    { id: 'watched', label: '已看', icon: CheckCircle2 },
                    { id: 'favorite', label: '喜爱', icon: Heart }
                  ]"
                  :key="st.id"
                  @click="setStatus(st.id)"
                  :class="[
                    'flex-1 py-1.5 px-2 rounded-xl text-xs font-medium border flex items-center justify-center gap-1.5 transition',
                    userStatus === st.id
                      ? 'bg-amber-500 text-black border-amber-400 font-bold shadow'
                      : 'bg-zinc-800/80 text-zinc-400 border-zinc-700/60 hover:text-zinc-200 hover:bg-zinc-800'
                  ]"
                >
                  <component :is="st.icon" class="w-3.5 h-3.5" :fill="userStatus === st.id ? 'currentColor' : 'none'" />
                  <span>{{ st.label }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- Right: Custom Tags & Personal Note -->
          <div class="p-4 rounded-2xl bg-zinc-900/80 border border-zinc-800 space-y-3">
            <!-- Custom Tags -->
            <div>
              <div class="text-[11px] text-zinc-400 font-medium mb-1.5 flex items-center justify-between">
                <span>自定义标签</span>
                <button
                  @click="isCreatingTag = !isCreatingTag"
                  class="text-[10px] text-amber-400 hover:text-amber-300 flex items-center gap-0.5"
                >
                  <Plus class="w-3 h-3" /> 新建标签
                </button>
              </div>

              <!-- Create new tag inline form -->
              <div v-if="isCreatingTag" class="flex items-center gap-2 mb-2 p-2 rounded-lg bg-zinc-950 border border-zinc-800">
                <input
                  v-model="newTagName"
                  type="text"
                  placeholder="标签名称"
                  @keyup.enter="handleCreateTag"
                  class="flex-1 bg-transparent text-xs text-zinc-200 outline-none placeholder-zinc-600"
                />
                <input
                  v-model="newTagColor"
                  type="color"
                  class="w-5 h-5 rounded cursor-pointer bg-transparent border-0"
                />
                <button
                  @click="handleCreateTag"
                  class="px-2 py-0.5 rounded bg-amber-500 text-black text-[11px] font-bold"
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
                    selectedTagIds.includes(t.id)
                      ? 'shadow'
                      : 'opacity-50 hover:opacity-100'
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
                <div v-if="availableTags.length === 0 && !isCreatingTag" class="text-xs text-zinc-600 italic">
                  点击右上角「新建标签」添加个人分类
                </div>
              </div>
            </div>

            <!-- Notes textarea -->
            <div class="pt-2 border-t border-zinc-800">
              <div class="text-[11px] text-zinc-400 font-medium mb-1">私密笔记 / 简评</div>
              <textarea
                v-model="userNotes"
                @blur="persistUserData"
                rows="2"
                placeholder="记录观后感、精彩节点或备忘..."
                class="w-full bg-zinc-950/80 border border-zinc-700/80 rounded-xl p-2.5 text-xs text-zinc-200 placeholder-zinc-600 outline-none focus:border-amber-500 transition resize-none"
              ></textarea>
            </div>
          </div>
        </div>
      </div>

      <!-- Cast Section -->
      <div v-if="movie.performers && movie.performers.length > 0" class="p-6 md:p-8 border-b border-zinc-800 space-y-3">
        <div class="flex items-center gap-2 text-sm font-bold text-zinc-300">
          <Tag class="w-4 h-4 text-amber-400" />
          <span>演职人员 ({{ movie.performers.length }})</span>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="p in movie.performers"
            :key="p.id"
            @click="emit('select-performer', p.id)"
            class="group flex items-center gap-2 px-3 py-1.5 rounded-xl bg-zinc-800 hover:bg-amber-500/10 border border-zinc-700 hover:border-amber-500/40 text-xs font-medium text-zinc-200 hover:text-amber-400 transition"
          >
            <div class="w-6 h-6 rounded-full overflow-hidden bg-zinc-700 flex items-center justify-center text-[10px] font-bold text-zinc-300 group-hover:bg-amber-500 group-hover:text-black transition shrink-0">
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
        <div class="flex items-center gap-2 text-sm font-bold text-zinc-300">
          <Layers class="w-4 h-4 text-amber-400" />
          <span>收录章节 / 场景片段 ({{ movie.episodes.length }})</span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div
            v-for="ep in movie.episodes"
            :key="ep.id"
            class="flex flex-col bg-zinc-950/80 rounded-2xl border border-zinc-800/80 overflow-hidden hover:border-zinc-700 transition"
          >
            <!-- Scene thumbnail -->
            <div v-if="ep.thumbnail_url" class="relative w-full aspect-video bg-zinc-900 overflow-hidden">
              <img :src="getImageUrl(ep.thumbnail_url)" :alt="ep.title" loading="lazy" referrerpolicy="no-referrer" class="w-full h-full object-cover" />
            </div>

            <!-- Scene Info -->
            <div class="p-4 space-y-2 flex-1 flex flex-col justify-between">
              <div>
                <div class="flex items-start justify-between gap-2">
                  <div class="text-xs font-bold text-amber-300">{{ ep.title }}</div>
                  <button
                    @click="emit('toggle-entity-favorite', 'episode', String(ep.id))"
                    :title="isFav('episode', String(ep.id)) ? '取消收藏该片段' : '收藏该片段'"
                    class="shrink-0 transition"
                    :class="isFav('episode', String(ep.id)) ? 'text-rose-400' : 'text-zinc-600 hover:text-rose-400'"
                  >
                    <Heart class="w-3.5 h-3.5" :fill="isFav('episode', String(ep.id)) ? 'currentColor' : 'none'" />
                  </button>
                </div>
                <!-- Chinese once the parent film has been translated, original otherwise -->
                <div v-if="ep.description_zh || ep.description" class="text-xs text-zinc-400 mt-1 line-clamp-3 leading-relaxed">
                  {{ ep.description_zh || ep.description }}
                </div>
              </div>
              <div v-if="ep.action_notes" class="text-[10px] text-zinc-500 bg-zinc-900 px-2 py-1 rounded font-mono">
                动作标签: {{ ep.action_notes }}
              </div>
            </div>
          </div>
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
