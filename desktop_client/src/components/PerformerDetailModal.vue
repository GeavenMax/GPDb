<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { X, Film, Layers, Heart, ExternalLink, LayoutGrid, List } from '@lucide/vue';
import type { Performer, Movie, FavoriteType } from '../types';
import MovieCard from './MovieCard.vue';
import EpisodeRow from './EpisodeRow.vue';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';
import { tr, trTattoo, trMeasure } from '../utils/glossary';
import { pluginsConfig, openBtSearch, openBftvPerformer, openGoogleSearch } from '../services/pluginManager';

const props = defineProps<{
  performer: Performer | null;
  /** Synopsis language for the embedded movie cards. */
  lang?: 'zh' | 'en';
  /** Stacking order supplied by the parent; see MovieDetailModal. */
  zIndex?: number;
  /** Topmost view owns Escape — see MovieDetailModal. */
  isTop?: boolean;
  /** Whether this performer is favorited. */
  isFavorite?: boolean;
  /** Favorited keys by type — used for the studio and episode hearts in this modal. */
  favoriteKeys?: Partial<Record<FavoriteType, Set<string>>>;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select-movie', movie: Movie): void;
  (e: 'select-movie-id', movieId: number): void;
  (e: 'select-episode-id', episodeId: number): void;
  (e: 'toggle-favorite', performer: Performer): void;
  /** Studio / director / episode hearts; the performer has its own event above. */
  (e: 'toggle-entity-favorite', type: FavoriteType, key: string): void;
  (e: 'filter-studio', studioName: string): void;
}>();

const activeTab = ref<'movies' | 'episodes'>('movies');
const episodeLayout = ref<'grid' | 'list'>('grid');
const portraitError = ref(false);

function isFav(type: FavoriteType, key: string | null | undefined): boolean {
  if (!key) return false;
  return Boolean(props.favoriteKeys?.[type]?.has(key));
}

/** Attribute values, preferring the server's exploded list over raw markup. */
function attrValues(key: string, fallback: string | null | undefined): string[] {
  const exploded = props.performer?.attributes?.[key];
  if (exploded && exploded.length > 0) return exploded;
  if (!fallback) return [];
  return fallback.split(/<br\s*\/?>/i).map(s => s.trim()).filter(Boolean);
}

/**
 * The measurement fields (height / weight / dick size) hold "5ft 10in / 178cm"
 * strings. Their numbers need no translation but their units do, and the units are
 * the only part of the value in the glossary — see trMeasure.
 */
interface Spec {
  key: string;
  label: string;
  /** Rendered in amber rather than zinc. */
  accent: boolean;
  /** Translate the value's units as well as the value — see trMeasure. */
  measure?: boolean;
  values: string[];
}

const SPECS = computed<Spec[]>(() => {
  const p = props.performer;
  if (!p) return [];
  return [
    { key: 'height', label: '身高', accent: false, measure: true, values: attrValues('height', p.height) },
    { key: 'weight', label: '体重', accent: false, measure: true, values: attrValues('weight', p.weight) },
    { key: 'bodyType', label: '体型', accent: false, values: attrValues('bodyType', p.build) },
    { key: 'dickSize', label: '尺寸规格', accent: true, measure: true, values: attrValues('dickSize', p.dick_size) },
    { key: 'skin', label: '肤色', accent: false, values: attrValues('skin', p.skin) },
    { key: 'hair', label: '发色', accent: false, values: attrValues('hair', p.hair) },
    { key: 'eyes', label: '瞳色', accent: false, values: attrValues('eyes', p.eyes) },
    { key: 'bodyHair', label: '体毛', accent: false, values: attrValues('bodyHair', p.body_hair) },
    { key: 'facialHair', label: '胡须', accent: false, values: attrValues('facialHair', p.facial_hair) },
    { key: 'foreskin', label: '包皮', accent: false, values: attrValues('foreskin', p.foreskin) },
  ].filter(s => s.values.length > 0);
});

const tattoos = computed(() => attrValues('tattoos', props.performer?.tattoos));

/**
 * Studio filter for the works below.
 *
 * A performer's filmography can span a dozen studios, so the list is narrowed by a
 * chip row rather than paged. The same filter drives both tabs — episodes carry a
 * studio_name too — but it is cleared whenever the selected studio is not present
 * in the tab being shown, otherwise switching tabs (or performers) would land on a
 * silently empty list.
 */
const studioFilter = ref('');

const studioOptions = computed(() => {
  const counts = new Map<string, number>();
  const add = (name?: string | null) => {
    if (name) counts.set(name, (counts.get(name) || 0) + 1);
  };
  if (activeTab.value === 'movies') {
    (props.performer?.movies || []).forEach(m => add(m.studio_name));
  } else {
    (props.performer?.episodes || []).forEach(e => add(e.studio_name));
  }
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
});

/**
 * How many chips the collapsed row shows. A filmography can span a dozen-plus
 * studios, and an unbounded `flex-wrap` row pushed the grid most of a screen
 * down — measured against the library, the busiest performers cross twenty.
 */
const STUDIO_CHIP_LIMIT = 8;
const studiosExpanded = ref(true);

/**
 * The chips to render. Collapsed to the busiest `STUDIO_CHIP_LIMIT`, except that
 * the active filter is always kept on screen — it can sit at position 20, and a
 * highlighted chip you cannot see or click off is worse than a long row.
 */
const shownStudioOptions = computed(() => {
  const all = studioOptions.value;
  if (studiosExpanded.value || all.length <= STUDIO_CHIP_LIMIT) return all;
  const head = all.slice(0, STUDIO_CHIP_LIMIT);
  const active = all.find(o => o.name === studioFilter.value);
  if (active && !head.includes(active)) {
    return [...head.slice(0, STUDIO_CHIP_LIMIT - 1), active];
  }
  return head;
});

/** What the "更多" button is hiding. Zero once expanded. */
const collapsedStudioCount = computed(() =>
  Math.max(0, studioOptions.value.length - shownStudioOptions.value.length)
);

watch(studioOptions, (opts) => {
  if (studioFilter.value && !opts.some(o => o.name === studioFilter.value)) {
    studioFilter.value = '';
  }
});

watch(() => props.performer?.id, () => {
  portraitError.value = false;
  studioFilter.value = '';
  studiosExpanded.value = false;
});

const visibleMovies = computed(() => {
  const list = props.performer?.movies || [];
  return studioFilter.value ? list.filter(m => m.studio_name === studioFilter.value) : list;
});

const visibleEpisodes = computed(() => {
  const list = props.performer?.episodes || [];
  return studioFilter.value ? list.filter(e => e.studio_name === studioFilter.value) : list;
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
    v-if="performer"
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

      <!-- Profile Header. The attribute profile lives here rather than in a section of
           its own below: it is a dozen short label/value pairs, and as a full-width
           block of boxes it pushed the filmography below the fold for no gain. -->
      <!-- Profile Header -->
      <div class="p-6 md:p-8 bg-sunken border-b border-line space-y-4">
        <div class="flex items-start justify-between gap-5 flex-wrap">
          <div class="flex items-start gap-5 min-w-0 flex-1">
            <!-- Portrait (issue #6), falls back to the letter tile when unscraped. -->
            <div class="w-24 h-24 md:w-28 md:h-28 rounded-2xl overflow-hidden shrink-0 shadow-lg shadow-accent-fill/10 ring-1 ring-line-strong/60">
              <img
                v-if="performer.image_url && !portraitError"
                :src="getImageUrl(performer.image_url)"
                :alt="performer.name"
                referrerpolicy="no-referrer"
                data-zoom-click
                class="w-full h-full object-cover object-top"
                @error="portraitError = true"
              />
              <div
                v-else
                class="w-full h-full bg-gradient-to-tr from-accent-deep to-accent-2 flex items-center justify-center text-3xl font-black text-on-fill"
              >
                {{ performer.name.charAt(0).toUpperCase() }}
              </div>
            </div>

            <div class="min-w-0 flex-1">
              <div class="text-xs font-semibold text-accent uppercase tracking-wider">演员档案</div>
              <h1 class="text-2xl md:text-3xl font-extrabold text-fg truncate" :title="performer.name">
                {{ performer.name }}
              </h1>
              <div class="text-xs text-fg-3 mt-1 flex items-center gap-3 flex-wrap">
                <span>ID: #{{ performer.id }}</span>
                <span
                  v-if="performer.works_count ?? performer.movies_count"
                  class="text-accent/80"
                >{{ performer.works_count ?? performer.movies_count }} 部作品</span>
                <span v-if="!performer.image_url" class="text-fg-5">暂无照片</span>
              </div>

              <!-- Aliases / AKA and Notes -->
              <div
                v-if="performer.aliases && performer.aliases.length > 0"
                class="mt-2 text-[11px] text-fg-4 leading-relaxed max-w-xl"
              >
                <span class="text-fg-5 font-medium">曾用艺名 / 别名 (AKA)：</span>
                <span class="text-fg-3">{{ performer.aliases.join('、') }}</span>
              </div>
              <div
                v-if="performer.notes"
                class="mt-1.5 text-[11px] text-fg-4/80 leading-relaxed italic max-w-xl"
              >
                {{ performer.notes }}
              </div>
            </div>
          </div>

          <!-- Action buttons (BT Search + Fav) -->
          <div class="mr-10 shrink-0 self-start flex items-center gap-2 flex-wrap">
            <template v-if="pluginsConfig.resourceSearchEnabled">
              <button
                @click="openBtSearch(performer.name)"
                class="px-3 py-1.5 rounded-lg text-xs font-medium border border-line bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition cursor-pointer"
                :title="`在 BT 站检索「${performer.name}」作品`"
              >
                <ExternalLink class="w-3.5 h-3.5" />
                <span>BT 搜索</span>
              </button>

              <button
                v-if="pluginsConfig.webJumpConfig.bftvPerformerEnabled"
                @click="openBftvPerformer(performer.name, performer.bftv_url)"
                class="px-3 py-1.5 rounded-lg text-xs font-medium border border-line bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition cursor-pointer"
                :title="performer.bftv_url ? `直接打开「${performer.name}」的 BFTV 主页` : `在 BFTV 检索「${performer.name}」演员资料`"
              >
                <ExternalLink class="w-3.5 h-3.5 text-amber-400" />
                <span>{{ performer.bftv_url ? '打开BFTV主页' : '在BFTV搜索演员资料' }}</span>
              </button>

              <button
                v-if="pluginsConfig.webJumpConfig.googleSearchEnabled"
                @click="openGoogleSearch(performer.name)"
                class="px-3 py-1.5 rounded-lg text-xs font-medium border border-line bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition cursor-pointer"
                :title="`在 Google 检索「${performer.name}」`"
              >
                <ExternalLink class="w-3.5 h-3.5 text-blue-400" />
                <span>Google 搜索</span>
              </button>
            </template>

            <button
              @click="emit('toggle-favorite', performer)"
              :class="[
                'px-3 py-1.5 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition cursor-pointer',
                isFavorite
                  ? 'bg-danger-fill/20 text-danger-soft border-danger-fill/40'
                  : 'bg-surface-2 hover:bg-surface-3 border-line-strong text-fg-3 hover:text-danger'
              ]"
            >
              <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
              <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
            </button>
          </div>
        </div>

        <!-- Attribute profile as chips -->
        <div
          v-if="SPECS.length > 0"
          class="flex flex-wrap content-start gap-1.5 pt-1"
          aria-label="身体属性档案"
        >
          <span
            v-for="spec in SPECS"
            :key="spec.key"
            class="inline-flex items-baseline gap-1.5 max-w-full px-2 py-0.5 rounded-lg bg-surface/80 border border-line"
          >
            <span class="text-[11px] text-fg-4 shrink-0">{{ spec.label }}</span>
            <span
              v-for="v in spec.values"
              :key="v"
              :title="v"
              :class="[
                'text-[11px] font-semibold break-words',
                spec.accent ? 'text-accent' : 'text-fg-2'
              ]"
            >
              {{ spec.measure ? trMeasure(v) : tr(v) }}
            </span>
          </span>

          <span
            v-if="tattoos.length > 0"
            class="inline-flex items-baseline gap-1.5 basis-full px-2 py-0.5 rounded-lg bg-surface/80 border border-line"
            title="纹身标识：部位译中文，描述保留原文"
          >
            <span class="text-[11px] text-fg-4 shrink-0">纹身</span>
            <span class="text-[11px] font-semibold text-fg-2 break-words">
              {{ tattoos.map(trTattoo).join('、') }}
            </span>
          </span>
        </div>

        <div v-else class="text-xs text-fg-4 italic pt-1">
          该演员的详情页尚未抓取，暂无声色属性档案。
        </div>
      </div>

      <!-- Works Section (Divided into Movies vs Episodes) -->
      <div class="p-6 md:p-8 space-y-6">
        <!-- Dual Tab Switcher -->
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
              <span>完整电影 ({{ performer.movies ? performer.movies.length : 0 }})</span>
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
              <span>片段 / 分集 ({{ performer.episodes ? performer.episodes.length : (performer.episodes_count || 0) }})</span>
            </button>
          </div>
        </div>

        <!--
          Studio filter. Hidden for a single-studio performer: a filter row with one
          choice is just noise. Clicking the active chip clears it. Collapsed to the
          busiest few, with the rest behind the toggle on the right.
        -->
        <div v-if="studioOptions.length > 1" class="space-y-1.5">
          <div class="flex items-center justify-between gap-2">
            <span class="text-[11px] font-semibold text-fg-4 uppercase tracking-wider">片商</span>
            <button
              v-if="collapsedStudioCount > 0 || studiosExpanded"
              type="button"
              @click="studiosExpanded = !studiosExpanded"
              class="text-[11px] font-medium text-fg-4 hover:text-accent-soft transition shrink-0 cursor-pointer"
            >
              {{ studiosExpanded ? '折叠仅显示首行' : `+${studioOptions.length - STUDIO_CHIP_LIMIT} 展开全部` }}
            </button>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <button
              v-for="opt in shownStudioOptions"
              :key="opt.name"
              @click="studioFilter = studioFilter === opt.name ? '' : opt.name"
              :class="[
                'px-2.5 py-1 rounded-lg text-xs font-medium border transition',
                studioFilter === opt.name
                  ? 'bg-accent-fill text-on-fill border-accent-fill shadow'
                  : 'bg-surface-2/70 hover:bg-surface-2 border-line-strong text-fg-2 hover:text-accent-soft'
              ]"
            >
              {{ opt.name }}
              <span :class="studioFilter === opt.name ? 'text-on-fill/60' : 'text-fg-4'">{{ opt.count }}</span>
            </button>
          </div>
        </div>

        <!-- 1. Feature Movies Tab -->
        <div v-if="activeTab === 'movies'">
          <div v-if="visibleMovies.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
            <MovieCard
              v-for="m in visibleMovies"
              :key="m.id"
              :movie="m"
              :lang="lang"
              :is-favorite="isFav('movie', String(m.id))"
              @select="emit('select-movie', m)"
              @toggle-favorite="emit('toggle-entity-favorite', 'movie', String(m.id))"
            />
          </div>
          <div v-else class="text-center py-12 text-fg-4 text-xs">
            {{ studioFilter ? `该演员没有 ${studioFilter} 的长片电影` : '暂无收录该演员的长片电影' }}
          </div>
        </div>

        <!-- 2. Episodes & Scenes Tab -->
        <div v-else-if="activeTab === 'episodes'" class="space-y-3">
          <div class="flex items-center justify-between pb-1 flex-wrap gap-2">
            <span class="text-xs text-fg-4">共收录 {{ visibleEpisodes.length }} 个相关片段</span>
            <!-- Layout Switcher: Grid vs List -->
            <div class="flex items-center bg-surface-2/80 rounded-xl p-0.5 border border-line text-xs font-semibold">
              <button
                type="button"
                @click="episodeLayout = 'grid'"
                :class="[
                  'px-2.5 py-1 rounded-lg text-xs font-medium transition flex items-center gap-1.5 cursor-pointer',
                  episodeLayout === 'grid'
                    ? 'bg-accent-fill text-on-fill shadow-xs'
                    : 'text-fg-4 hover:text-fg hover:bg-surface-3/50'
                ]"
                title="网格视图（展示更多条目）"
              >
                <LayoutGrid class="w-3.5 h-3.5" />
                <span>网格</span>
              </button>
              <button
                type="button"
                @click="episodeLayout = 'list'"
                :class="[
                  'px-2.5 py-1 rounded-lg text-xs font-medium transition flex items-center gap-1.5 cursor-pointer',
                  episodeLayout === 'list'
                    ? 'bg-accent-fill text-on-fill shadow-xs'
                    : 'text-fg-4 hover:text-fg hover:bg-surface-3/50'
                ]"
                title="列表视图（含详细剧情）"
              >
                <List class="w-3.5 h-3.5" />
                <span>列表</span>
              </button>
            </div>
          </div>

          <div v-if="visibleEpisodes.length > 0">
            <!-- Grid Layout (展示更多条目) -->
            <div v-if="episodeLayout === 'grid'" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3.5">
              <div
                v-for="ep in visibleEpisodes"
                :key="ep.id"
                @click="emit('select-episode-id', ep.id)"
                class="group relative flex flex-col rounded-2xl bg-surface/60 border border-line/80 hover:border-accent-fill/50 hover:shadow-xl hover:shadow-accent-fill/10 transition-all duration-300 overflow-hidden cursor-pointer select-none"
              >
                <div class="relative w-full aspect-video bg-sunken overflow-hidden">
                  <img
                    v-if="ep.thumbnail_url"
                    :src="getImageUrl(ep.thumbnail_url)"
                    :alt="ep.title"
                    loading="lazy"
                    referrerpolicy="no-referrer"
                    class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                  />
                  <div v-else class="w-full h-full flex items-center justify-center text-fg-5">
                    <Layers class="w-8 h-8 stroke-1" />
                  </div>

                  <!-- Favorite Heart Badge -->
                  <button
                    @click.stop="emit('toggle-entity-favorite', 'episode', String(ep.id))"
                    class="absolute top-2 right-2 p-1.5 rounded-full backdrop-blur-md bg-black/40 hover:bg-black/70 text-fg transition cursor-pointer"
                    :title="isFav('episode', String(ep.id)) ? '取消收藏' : '收藏片段'"
                  >
                    <Heart
                      class="w-3.5 h-3.5 transition"
                      :class="isFav('episode', String(ep.id)) ? 'text-danger fill-danger' : 'text-white/80'"
                    />
                  </button>
                </div>

                <div class="p-3 flex-1 flex flex-col justify-between gap-1.5">
                  <div>
                    <div class="flex items-start justify-between gap-1">
                      <h4 class="text-xs font-bold text-fg group-hover:text-accent transition line-clamp-1" :title="ep.title">
                        {{ ep.title }}
                      </h4>
                      <span v-if="ep.description_zh?.trim()" class="text-[9px] font-bold text-success flex items-center shrink-0">
                        中
                      </span>
                    </div>
                    <div
                      v-if="ep.movie_title"
                      @click.stop="ep.movie_id && emit('select-movie-id', ep.movie_id)"
                      class="text-[11px] text-fg-4 mt-0.5 truncate hover:text-accent hover:underline cursor-pointer"
                      :title="ep.movie_title"
                    >
                      出处: {{ ep.movie_title }}
                    </div>
                  </div>
                  <div class="flex items-center justify-between text-[10px] text-fg-4 pt-1.5 border-t border-line/40">
                    <span class="truncate max-w-[120px] font-medium">{{ ep.studio_name || '未知片商' }}</span>
                    <span v-if="ep.release_year">{{ ep.release_year }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- List Layout -->
            <div v-else class="grid grid-cols-1 gap-3">
              <EpisodeRow
                v-for="ep in visibleEpisodes"
                :key="ep.id"
                :episode="ep"
                :lang="lang"
                zoom-on-click
                show-studio
                :is-favorite="isFav('episode', String(ep.id))"
                @select-movie-id="emit('select-movie-id', $event)"
                @toggle-favorite="emit('toggle-entity-favorite', 'episode', String(ep.id))"
                @filter-studio="emit('filter-studio', $event)"
              />
            </div>
          </div>
          <div v-else class="text-center py-12 text-fg-4 text-xs">
            {{ studioFilter ? `该演员没有 ${studioFilter} 的分集片段` : '暂无收录该演员的独立分集片段' }}
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
