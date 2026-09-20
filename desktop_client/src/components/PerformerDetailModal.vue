<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { X, Film, Layers, Calendar } from '@lucide/vue';
import type { Performer, Movie } from '../types';
import MovieCard from './MovieCard.vue';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';

const props = defineProps<{
  performer: Performer | null;
  /** Synopsis language for the embedded movie cards. */
  lang?: 'zh' | 'en';
  /** Stacking order supplied by the parent; see MovieDetailModal. */
  zIndex?: number;
  /** Topmost view owns Escape — see MovieDetailModal. */
  isTop?: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select-movie', movie: Movie): void;
  (e: 'select-movie-id', movieId: number): void;
}>();

const activeTab = ref<'movies' | 'episodes'>('movies');
const portraitError = ref(false);

/** Attribute values, preferring the server's exploded list over raw markup. */
function attrValues(key: string, fallback: string | null | undefined): string[] {
  const exploded = props.performer?.attributes?.[key];
  if (exploded && exploded.length > 0) return exploded;
  if (!fallback) return [];
  return fallback.split(/<br\s*\/?>/i).map(s => s.trim()).filter(Boolean);
}

const SPECS = computed(() => {
  const p = props.performer;
  if (!p) return [];
  return [
    { key: 'height', label: '身高', accent: false, values: attrValues('height', p.height) },
    { key: 'weight', label: '体重', accent: false, values: attrValues('weight', p.weight) },
    { key: 'bodyType', label: '体型', accent: false, values: attrValues('bodyType', p.build) },
    { key: 'dickSize', label: '尺寸规格', accent: true, values: attrValues('dickSize', p.dick_size) },
    { key: 'skin', label: '肤色', accent: false, values: attrValues('skin', p.skin) },
    { key: 'hair', label: '发色', accent: false, values: attrValues('hair', p.hair) },
    { key: 'eyes', label: '瞳色', accent: false, values: attrValues('eyes', p.eyes) },
    { key: 'bodyHair', label: '体毛', accent: false, values: attrValues('bodyHair', p.body_hair) },
    { key: 'facialHair', label: '胡须', accent: false, values: attrValues('facialHair', p.facial_hair) },
    { key: 'foreskin', label: '包皮', accent: false, values: attrValues('foreskin', p.foreskin) },
  ].filter(s => s.values.length > 0);
});

const tattoos = computed(() => attrValues('tattoos', props.performer?.tattoos));

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
        <!-- Portrait (issue #6), falls back to the letter tile when unscraped -->
        <div class="w-24 h-24 md:w-28 md:h-28 rounded-2xl overflow-hidden shrink-0 shadow-lg shadow-amber-500/10 ring-1 ring-zinc-700/60">
          <img
            v-if="performer.image_url && !portraitError"
            :src="getImageUrl(performer.image_url)"
            :alt="performer.name"
            referrerpolicy="no-referrer"
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
        <div class="min-w-0">
          <div class="text-xs font-semibold text-amber-400 uppercase tracking-wider">演员档案</div>
          <h1 class="text-2xl md:text-3xl font-extrabold text-white truncate">{{ performer.name }}</h1>
          <div class="text-xs text-zinc-400 mt-1 flex items-center gap-3 flex-wrap">
            <span>ID: #{{ performer.id }}</span>
            <span v-if="performer.movies_count" class="text-amber-400/80">{{ performer.movies_count }} 部作品</span>
            <span v-if="!performer.image_url" class="text-zinc-600">暂无照片</span>
          </div>
        </div>
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
                :class="[
                  'text-sm font-semibold',
                  spec.accent ? 'text-amber-400' : 'text-zinc-200'
                ]"
              >
                {{ v }}
              </span>
            </div>
          </div>

          <div v-if="tattoos.length > 0" class="p-3 rounded-xl bg-zinc-950/70 border border-zinc-800/80 col-span-2">
            <div class="text-[11px] text-zinc-500">纹身标识</div>
            <div class="text-sm font-semibold text-zinc-200 mt-0.5">
              {{ tattoos.join('、') }}
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

        <!-- 1. Feature Movies Tab -->
        <div v-if="activeTab === 'movies'">
          <div v-if="performer.movies && performer.movies.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
            <MovieCard
              v-for="m in performer.movies"
              :key="m.id"
              :movie="m"
              :lang="lang"
              @select="emit('select-movie', m)"
            />
          </div>
          <div v-else class="text-center py-12 text-zinc-500 text-xs">
            暂无收录该演员的长片电影
          </div>
        </div>

        <!-- 2. Episodes & Scenes Tab -->
        <div v-else-if="activeTab === 'episodes'">
          <div v-if="performer.episodes && performer.episodes.length > 0" class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div
              v-for="ep in performer.episodes"
              :key="ep.id"
              class="flex flex-col bg-zinc-950/80 rounded-2xl border border-zinc-800/80 overflow-hidden hover:border-amber-500/40 transition group"
            >
              <!-- Episode thumbnail -->
              <div v-if="ep.thumbnail_url" class="relative w-full aspect-video bg-zinc-900 overflow-hidden">
                <img
                  :src="getImageUrl(ep.thumbnail_url)"
                  :alt="ep.title"
                  loading="lazy"
                  referrerpolicy="no-referrer"
                  class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
              </div>

              <!-- Episode info -->
              <div class="p-4 space-y-2 flex-1 flex flex-col justify-between">
                <div>
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-xs font-bold text-amber-300">{{ ep.title }}</span>
                    <span v-if="ep.release_year" class="text-[10px] text-zinc-500 font-mono flex items-center gap-1">
                      <Calendar class="w-2.5 h-2.5" /> {{ ep.release_year }}
                    </span>
                  </div>

                  <div v-if="ep.movie_title" class="text-xs font-medium text-zinc-300 mt-1 flex items-center gap-1">
                    <Film class="w-3 h-3 text-zinc-500" />
                    <span>出处: {{ ep.movie_title }}</span>
                    <span v-if="ep.studio_name" class="text-zinc-500 text-[10px] ml-1">({{ ep.studio_name }})</span>
                  </div>

                  <div v-if="ep.description" class="text-xs text-zinc-400 mt-1.5 line-clamp-3 leading-relaxed">
                    {{ ep.description }}
                  </div>
                </div>

                <div v-if="ep.action_notes" class="text-[10px] text-zinc-500 bg-zinc-900 px-2 py-1 rounded font-mono mt-2">
                  动作标签: {{ ep.action_notes }}
                </div>
              </div>
            </div>
          </div>
          <div v-else class="text-center py-12 text-zinc-500 text-xs">
            暂无收录该演员的独立分集片段
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
