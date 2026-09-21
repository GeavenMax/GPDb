<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { X, Film, Heart, Clapperboard, Calendar, ChevronLeft, ChevronRight, Languages } from '@lucide/vue';
import type { EpisodeSummary } from '../types';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';
import { episodeHeading, episodeLabel } from '../utils/episode';

const props = defineProps<{
  episode: EpisodeSummary | null;
  /**
   * The rows the card was clicked in — already loaded, so paging stays inside them.
   * The modal does not fetch: running off the end of the loaded page just disables
   * the button, which is cheaper than wiring a loader into a dialog.
   */
  list?: EpisodeSummary[];
  /** Synopsis language, as everywhere else. */
  lang?: 'zh' | 'en';
  /** Stacking order supplied by the parent; see MovieDetailModal. */
  zIndex?: number;
  /** Topmost view owns Escape — see MovieDetailModal. */
  isTop?: boolean;
  /** Whether this episode is favorited. */
  isFavorite?: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  /** ‹ / › within `list`; the parent owns which episode that lands on. */
  (e: 'navigate', delta: number): void;
  (e: 'select-movie-id', movieId: number): void;
  (e: 'select-performer', performerId: number): void;
  (e: 'filter-studio', studioName: string): void;
  (e: 'toggle-favorite', episode: EpisodeSummary): void;
}>();

const stillError = ref(false);

// A different scene means a different still, so a failed load is retried.
watch(() => props.episode?.id, () => {
  stillError.value = false;
});

/** "《影片名》· 第 3 集 / 共 5 集" — the film is what identifies the scene. */
const heading = computed(() =>
  props.episode ? episodeHeading(props.episode, props.episode.movie_title) : ''
);

const shownDescription = computed(() => {
  const zh = props.episode?.description_zh?.trim();
  if (props.lang === 'zh' && zh) return zh;
  return props.episode?.description?.trim() || '';
});

const index = computed(() => {
  if (!props.episode || !props.list) return -1;
  return props.list.findIndex(e => e.id === props.episode?.id);
});

const prevEpisode = computed(() => (index.value > 0 ? props.list![index.value - 1] : null));
const nextEpisode = computed(() =>
  index.value >= 0 && props.list && index.value < props.list.length - 1 ? props.list[index.value + 1] : null
);

/** "3 / 24" — where the scene sits in what is loaded, not in the whole library. */
const positionLabel = computed(() =>
  index.value >= 0 && props.list ? `${index.value + 1} / ${props.list.length}` : ''
);

// Escape only. The arrow keys are left alone deliberately: the image viewer pages
// with them, and a second listener paging the modal underneath would fight it.
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
    v-if="episode"
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

      <!-- Hero: the still IS the panel's content, so it zooms on a single click. -->
      <div class="relative w-full aspect-video bg-zinc-950 shrink-0 overflow-hidden">
        <img
          v-if="episode.thumbnail_url && !stillError"
          :src="getImageUrl(episode.thumbnail_url)"
          :alt="heading"
          referrerpolicy="no-referrer"
          data-zoom-click
          @error="stillError = true"
          class="w-full h-full object-cover object-center cursor-zoom-in"
        />
        <div v-else class="w-full h-full flex flex-col items-center justify-center gap-2 text-zinc-700">
          <Clapperboard class="w-10 h-10 stroke-1" />
          <span class="text-xs font-medium text-zinc-600">暂无剧照</span>
        </div>

        <!-- Scrim for the heading. Not interactive: the still underneath must keep
             receiving the click that opens the viewer. -->
        <div class="absolute inset-0 bg-gradient-to-t from-zinc-900 via-zinc-900/50 to-transparent pointer-events-none"></div>

        <div class="absolute bottom-0 left-0 right-0 p-6 md:p-8 min-w-0 pointer-events-none">
          <div class="text-xs font-semibold text-amber-400 uppercase tracking-wider flex items-center gap-2">
            <span>分集档案</span>
            <span
              v-if="episode.description_zh?.trim()"
              class="px-1.5 py-0.5 rounded text-[9px] font-bold bg-emerald-500/90 text-black flex items-center gap-0.5 pointer-events-auto"
              title="已有中文简介"
            >
              <Languages class="w-2.5 h-2.5" />
              中
            </span>
          </div>
          <h1 class="text-xl md:text-3xl font-extrabold text-white mt-1 break-words">{{ heading }}</h1>
          <div class="text-xs text-zinc-300 mt-1.5 flex items-center gap-3 flex-wrap">
            <span v-if="episode.release_year" class="flex items-center gap-1">
              <Calendar class="w-3 h-3" /> {{ episode.release_year }}
            </span>
            <span v-if="episode.studio_name">{{ episode.studio_name }}</span>
            <span v-if="episode.episode_count > 1" class="text-zinc-400">
              该影片共 {{ episode.episode_count }} 个片段
            </span>
          </div>
        </div>
      </div>

      <div class="p-6 md:p-8 space-y-5">
        <!-- Source film + studio + the heart. -->
        <div class="flex items-center justify-between gap-3 flex-wrap">
          <div class="flex items-center gap-2 flex-wrap min-w-0">
            <button
              v-if="episode.movie_title"
              type="button"
              :disabled="!episode.movie_id"
              @click="episode.movie_id && emit('select-movie-id', episode.movie_id)"
              :title="episode.movie_id ? `跳转到《${episode.movie_title}》` : '该片段没有关联影片'"
              :class="[
                'flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition max-w-full',
                episode.movie_id
                  ? 'bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-300 hover:text-amber-300 hover:border-amber-500/40'
                  : 'bg-zinc-800/50 border-zinc-800 text-zinc-500 cursor-default',
              ]"
            >
              <Film class="w-3.5 h-3.5 shrink-0 text-zinc-500" />
              <span class="truncate">出处: {{ episode.movie_title }}</span>
            </button>
            <button
              v-if="episode.studio_name"
              type="button"
              @click="emit('filter-studio', episode.studio_name)"
              class="px-2.5 py-1.5 rounded-lg text-xs bg-zinc-800/60 border border-zinc-700/50 text-zinc-400 hover:text-amber-400 hover:border-amber-500/40 transition truncate max-w-[180px]"
              :title="`按片商 ${episode.studio_name} 筛选影片`"
            >
              {{ episode.studio_name }}
            </button>
          </div>

          <button
            @click="emit('toggle-favorite', episode)"
            :class="[
              'mr-10 shrink-0 px-3 py-1.5 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
              isFavorite
                ? 'bg-rose-500/20 text-rose-300 border-rose-500/40'
                : 'bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-400 hover:text-rose-400'
            ]"
          >
            <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
            <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
          </button>
        </div>

        <!-- Synopsis -->
        <div>
          <div class="text-xs font-bold text-zinc-300 mb-2">片段简介</div>
          <div
            v-if="shownDescription"
            class="text-sm text-zinc-300 leading-relaxed max-h-[40vh] overflow-y-auto darkScrollbars whitespace-pre-line pr-2"
          >
            {{ shownDescription }}
          </div>
          <div v-else class="text-xs text-zinc-500 italic">该片段暂无简介</div>
        </div>

        <!-- Cast: a scene usually has one or two, so no folding needed. -->
        <div v-if="episode.performers.length > 0">
          <div class="text-xs font-bold text-zinc-300 mb-2">
            参演演员 <span class="text-zinc-500 font-normal">({{ episode.performers.length }})</span>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="p in episode.performers"
              :key="p.id"
              type="button"
              @click="emit('select-performer', p.id)"
              class="px-2 py-1 rounded-lg text-[11px] bg-zinc-800/70 border border-zinc-700/50 text-zinc-300 hover:text-amber-300 hover:border-amber-500/40 transition"
            >
              {{ p.name }}
            </button>
          </div>
        </div>

        <!-- Action tags. Empty across the library today, but the column is there. -->
        <div v-if="episode.action_notes">
          <div class="text-xs font-bold text-zinc-300 mb-2">动作标注</div>
          <div class="text-xs text-zinc-400 bg-zinc-950 px-3 py-2 rounded-lg font-mono">
            {{ episode.action_notes }}
          </div>
        </div>
      </div>

      <!-- Paging within what the grid has loaded. -->
      <div class="mt-auto p-4 md:px-8 border-t border-zinc-800 bg-zinc-950/60 flex items-center justify-between gap-3">
        <button
          type="button"
          :disabled="!prevEpisode"
          @click="emit('navigate', -1)"
          :title="prevEpisode ? episodeLabel(prevEpisode) : '已经是当前列表的第一条'"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border transition bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-300 disabled:opacity-30 disabled:pointer-events-none max-w-[40%]"
        >
          <ChevronLeft class="w-3.5 h-3.5 shrink-0" />
          <span class="truncate">{{ prevEpisode ? episodeLabel(prevEpisode) : '上一集' }}</span>
        </button>

        <span v-if="positionLabel" class="text-[11px] text-zinc-500 font-mono shrink-0">{{ positionLabel }}</span>

        <button
          type="button"
          :disabled="!nextEpisode"
          @click="emit('navigate', 1)"
          :title="nextEpisode ? episodeLabel(nextEpisode) : '已经是当前列表的最后一条'"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border transition bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-300 disabled:opacity-30 disabled:pointer-events-none max-w-[40%]"
        >
          <span class="truncate">{{ nextEpisode ? episodeLabel(nextEpisode) : '下一集' }}</span>
          <ChevronRight class="w-3.5 h-3.5 shrink-0" />
        </button>
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
