<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { X, Film, Layers, Calendar, Heart } from '@lucide/vue';
import type { Performer, Movie, FavoriteType } from '../types';
import MovieCard from './MovieCard.vue';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';
import { tr, trTattoo, trMeasure } from '../utils/glossary';

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
  (e: 'toggle-favorite', performer: Performer): void;
  /** Studio / director / episode hearts; the performer has its own event above. */
  (e: 'toggle-entity-favorite', type: FavoriteType, key: string): void;
  (e: 'filter-studio', studioName: string): void;
}>();

const activeTab = ref<'movies' | 'episodes'>('movies');
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

watch(studioOptions, (opts) => {
  if (studioFilter.value && !opts.some(o => o.name === studioFilter.value)) {
    studioFilter.value = '';
  }
});

watch(() => props.performer?.id, () => {
  portraitError.value = false;
  studioFilter.value = '';
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
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8 bg-black/80 backdrop-blur-md animate-fade-in"
    :style="{ zIndex: zIndex ?? 50 }"
    @click.self="emit('close')"
  >
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

      <!-- Profile Header -->
      <div class="p-6 md:p-8 bg-zinc-950 border-b border-zinc-800 flex items-center gap-6">
        <!-- Portrait (issue #6), falls back to the letter tile when unscraped.
             data-zoom-click: the portrait has no click action of its own, so a single
             click opens the viewer (see utils/lightbox.ts). -->
        <div class="w-24 h-24 md:w-28 md:h-28 rounded-2xl overflow-hidden shrink-0 shadow-lg shadow-amber-500/10 ring-1 ring-zinc-700/60">
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
            class="w-full h-full bg-gradient-to-tr from-amber-600 to-yellow-400 flex items-center justify-center text-3xl font-black text-black"
          >
            {{ performer.name.charAt(0).toUpperCase() }}
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <div class="text-xs font-semibold text-amber-400 uppercase tracking-wider">演员档案</div>
          <h1 class="text-2xl md:text-3xl font-extrabold text-white truncate">{{ performer.name }}</h1>
          <div class="text-xs text-zinc-400 mt-1 flex items-center gap-3 flex-wrap">
            <span>ID: #{{ performer.id }}</span>
            <span v-if="performer.movies_count" class="text-amber-400/80">{{ performer.movies_count }} 部作品</span>
            <span v-if="!performer.image_url" class="text-zinc-600">暂无照片</span>
          </div>
        </div>

        <!-- Fav button leaves room for the absolutely-positioned close button -->
        <button
          @click="emit('toggle-favorite', performer)"
          :class="[
            'mr-10 shrink-0 self-start px-3 py-1.5 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
            isFavorite
              ? 'bg-rose-500/20 text-rose-300 border-rose-500/40'
              : 'bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-400 hover:text-rose-400'
          ]"
        >
          <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
          <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
        </button>
      </div>

      <!-- Specs Grid -->
      <div class="p-6 md:p-8 border-b border-zinc-800">
        <div class="text-xs font-bold text-zinc-400 uppercase tracking-wider mb-4">身体属性档案</div>

        <div v-if="SPECS.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
          <div
            v-for="spec in SPECS"
            :key="spec.key"
            class="p-3 rounded-xl bg-zinc-950/70 border border-zinc-800/80"
          >
            <div class="text-[11px] text-zinc-500">{{ spec.label }}</div>
            <!-- Multi-value attributes are stored as "<br />"-separated lists -->
            <div class="flex flex-wrap gap-1 mt-1">
              <span
                v-for="v in spec.values"
                :key="v"
                :title="v"
                :class="[
                  'text-sm font-semibold',
                  spec.accent ? 'text-amber-400' : 'text-zinc-200'
                ]"
              >
                {{ spec.measure ? trMeasure(v) : tr(v) }}
              </span>
            </div>
          </div>

          <div v-if="tattoos.length > 0" class="p-3 rounded-xl bg-zinc-950/70 border border-zinc-800/80 col-span-2">
            <div class="text-[11px] text-zinc-500">纹身标识 <span class="text-zinc-600">(部位译中文，描述保留原文)</span></div>
            <div class="text-sm font-semibold text-zinc-200 mt-0.5">
              {{ tattoos.map(trTattoo).join('、') }}
            </div>
          </div>
        </div>

        <div v-else class="text-xs text-zinc-500 italic">
          该演员的详情页尚未抓取，暂无声色属性档案。
        </div>
      </div>

      <!-- Works Section (Divided into Movies vs Episodes) -->
      <div class="p-6 md:p-8 space-y-6">
        <!-- Dual Tab Switcher -->
        <div class="flex items-center justify-between border-b border-zinc-800 pb-4">
          <div class="flex items-center gap-2">
            <button
              @click="activeTab = 'movies'"
              :class="[
                'flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition',
                activeTab === 'movies'
                  ? 'bg-amber-500 text-black shadow-lg shadow-amber-500/20'
                  : 'bg-zinc-800/70 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800'
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
                  ? 'bg-amber-500 text-black shadow-lg shadow-amber-500/20'
                  : 'bg-zinc-800/70 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800'
              ]"
            >
              <Layers class="w-3.5 h-3.5" />
              <span>片段 / 分集 ({{ performer.episodes ? performer.episodes.length : (performer.episodes_count || 0) }})</span>
            </button>
          </div>
        </div>

        <!--
          Studio filter. Hidden for a single-studio performer: a filter row with one
          choice is just noise. Clicking the active chip clears it.
        -->
        <div v-if="studioOptions.length > 1" class="flex items-center gap-2 flex-wrap">
          <span class="text-[11px] font-semibold text-zinc-500 uppercase tracking-wider shrink-0">片商</span>
          <button
            v-for="opt in studioOptions"
            :key="opt.name"
            @click="studioFilter = studioFilter === opt.name ? '' : opt.name"
            :class="[
              'px-2.5 py-1 rounded-lg text-xs font-medium border transition',
              studioFilter === opt.name
                ? 'bg-amber-500 text-black border-amber-500 shadow'
                : 'bg-zinc-800/70 hover:bg-zinc-800 border-zinc-700 text-zinc-300 hover:text-amber-300'
            ]"
          >
            {{ opt.name }}
            <span :class="studioFilter === opt.name ? 'text-black/60' : 'text-zinc-500'">{{ opt.count }}</span>
          </button>
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
          <div v-else class="text-center py-12 text-zinc-500 text-xs">
            {{ studioFilter ? `该演员没有 ${studioFilter} 的长片电影` : '暂无收录该演员的长片电影' }}
          </div>
        </div>

        <!-- 2. Episodes & Scenes Tab -->
        <div v-else-if="activeTab === 'episodes'">
          <div v-if="visibleEpisodes.length > 0" class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div
              v-for="ep in visibleEpisodes"
              :key="ep.id"
              class="flex flex-col bg-zinc-950/80 rounded-2xl border border-zinc-800/80 overflow-hidden hover:border-amber-500/40 transition group"
            >
              <!-- Episode thumbnail. The card itself carries no click action (only the
                   出处 and 片商 labels below do), so the still zooms on one click. -->
              <div v-if="ep.thumbnail_url" class="relative w-full aspect-video bg-zinc-900 overflow-hidden">
                <img
                  :src="getImageUrl(ep.thumbnail_url)"
                  :alt="ep.title"
                  loading="lazy"
                  referrerpolicy="no-referrer"
                  data-zoom-click
                  class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
              </div>

              <!-- Episode info -->
              <div class="p-4 space-y-2 flex-1 flex flex-col justify-between">
                <div>
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-xs font-bold text-amber-300">{{ ep.title }}</span>
                    <div class="flex items-center gap-2 shrink-0">
                      <span v-if="ep.release_year" class="text-[10px] text-zinc-500 font-mono flex items-center gap-1">
                        <Calendar class="w-2.5 h-2.5" /> {{ ep.release_year }}
                      </span>
                      <button
                        @click="emit('toggle-entity-favorite', 'episode', String(ep.id))"
                        :title="isFav('episode', String(ep.id)) ? '取消收藏该片段' : '收藏该片段'"
                        class="transition"
                        :class="isFav('episode', String(ep.id)) ? 'text-rose-400' : 'text-zinc-600 hover:text-rose-400'"
                      >
                        <Heart class="w-3.5 h-3.5" :fill="isFav('episode', String(ep.id)) ? 'currentColor' : 'none'" />
                      </button>
                    </div>
                  </div>

                  <div v-if="ep.movie_title" class="text-xs font-medium text-zinc-300 mt-1 flex items-center gap-1 min-w-0">
                    <Film class="w-3 h-3 text-zinc-500 shrink-0" />
                    <!-- The film this scene came from. Clickable so a scene found on a
                         performer's page can be traced back to its film; a scene whose
                         film is gone keeps the label but not the jump.

                         The label rather than the whole card: the card's still has no
                         other click action, so it is what zooms on a single click. -->
                    <button
                      type="button"
                      :disabled="!ep.movie_id"
                      @click="ep.movie_id && emit('select-movie-id', ep.movie_id)"
                      :title="ep.movie_id ? `跳转到《${ep.movie_title}》` : '该片段没有关联影片'"
                      :class="[
                        'truncate transition',
                        ep.movie_id ? 'hover:text-amber-300 hover:underline' : 'cursor-default'
                      ]"
                    >出处: {{ ep.movie_title }}</button>
                    <button
                      v-if="ep.studio_name"
                      @click="emit('filter-studio', ep.studio_name)"
                      class="text-zinc-500 hover:text-amber-400 text-[10px] ml-1 shrink-0 transition"
                    >({{ ep.studio_name }})</button>
                  </div>

                  <!-- Chinese once the parent film has been translated, original otherwise -->
                  <div v-if="ep.description_zh || ep.description" class="text-xs text-zinc-400 mt-1.5 line-clamp-3 leading-relaxed">
                    {{ ep.description_zh || ep.description }}
                  </div>
                </div>

                <div v-if="ep.action_notes" class="text-[10px] text-zinc-500 bg-zinc-900 px-2 py-1 rounded font-mono mt-2">
                  动作标签: {{ ep.action_notes }}
                </div>
              </div>
            </div>
          </div>
          <div v-else class="text-center py-12 text-zinc-500 text-xs">
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
