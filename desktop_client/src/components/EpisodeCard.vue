<script setup lang="ts">
import { ref, computed } from 'vue';
import { Film, Heart, Clapperboard, Languages } from '@lucide/vue';
import type { EpisodeSummary } from '../types';
import { getImageUrl } from '../utils/image';
import { episodeOrdinalLabel } from '../utils/episode';

const props = withDefaults(defineProps<{
  episode: EpisodeSummary;
  isFavorite?: boolean;
  /** Which synopsis to prefer when both are available. */
  lang?: 'zh' | 'en';
}>(), {
  lang: 'zh',
});

const emit = defineEmits<{
  (e: 'select', episode: EpisodeSummary): void;
  (e: 'select-movie-id', movieId: number): void;
  (e: 'select-performer', performerId: number): void;
  (e: 'filter-studio', studioName: string): void;
  (e: 'toggle-favorite', episode: EpisodeSummary): void;
}>();

const imgError = ref(false);

/** Where the scene sits in its film — the only thing the badge can usefully say. */
const positionLabel = computed(() => episodeOrdinalLabel(props.episode));

const shownDescription = computed(() => {
  const zh = props.episode.description_zh?.trim();
  if (props.lang === 'zh' && zh) return zh;
  return props.episode.description?.trim() || '';
});
</script>

<template>
  <div
    @click="emit('select', episode)"
    class="group relative flex flex-col rounded-2xl bg-zinc-900/60 border border-zinc-800/80 hover:border-amber-500/50 hover:shadow-xl hover:shadow-amber-500/10 transition-all duration-300 overflow-hidden cursor-pointer select-none"
  >
    <!-- Still. No `data-zoom-click`: the whole card is click-to-open, so the image
         keeps the app-wide double-click zoom gesture (see utils/lightbox.ts). -->
    <div class="relative w-full aspect-video bg-zinc-950 overflow-hidden">
      <img
        v-if="episode.thumbnail_url && !imgError"
        :src="getImageUrl(episode.thumbnail_url)"
        :alt="episode.movie_title || episode.title"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="imgError = true"
        class="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500 ease-out"
      />
      <div
        v-else
        class="w-full h-full flex flex-col items-center justify-center gap-2 bg-gradient-to-b from-zinc-900 to-zinc-950 text-zinc-600"
      >
        <Clapperboard class="w-8 h-8 stroke-1 text-zinc-700" />
        <span class="text-[10px] font-medium">暂无剧照</span>
      </div>

      <!-- Position in the parent film (the site's own "Episode #<row id>" says nothing) -->
      <span
        v-if="positionLabel"
        class="absolute top-2.5 left-2.5 px-1.5 py-0.5 rounded-md text-[9px] font-bold bg-black/70 text-amber-300 backdrop-blur-md z-10"
      >
        {{ positionLabel }}
      </span>

      <!-- Chinese synopsis indicator, matching the film card's badge -->
      <span
        v-if="episode.description_zh?.trim()"
        class="absolute bottom-2.5 left-2.5 px-1.5 py-0.5 rounded-md text-[9px] font-bold bg-emerald-500/90 text-black backdrop-blur-md z-10 flex items-center gap-0.5"
        title="已有中文简介"
      >
        <Languages class="w-2.5 h-2.5" />
        中
      </span>

      <button
        @click.stop="emit('toggle-favorite', episode)"
        :class="[
          'absolute top-2.5 right-2.5 w-7 h-7 rounded-full flex items-center justify-center backdrop-blur-md transition-all duration-200 z-10',
          isFavorite
            ? 'bg-rose-500 text-white shadow-lg shadow-rose-500/40 opacity-100'
            : 'bg-black/40 text-zinc-400 hover:text-rose-400 hover:bg-black/80 opacity-0 group-hover:opacity-100'
        ]"
        title="收藏"
      >
        <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
      </button>
    </div>

    <div class="p-3.5 flex-1 flex flex-col justify-between gap-2">
      <div class="min-w-0">
        <!-- Which film the scene belongs to. Labs of the film card's title row. -->
        <div class="flex flex-wrap items-center gap-1.5 text-[10px] mb-1.5">
          <button
            v-if="episode.movie_title"
            type="button"
            :disabled="!episode.movie_id"
            @click.stop="episode.movie_id && emit('select-movie-id', episode.movie_id)"
            :title="episode.movie_id ? `跳转到《${episode.movie_title}》` : '该片段没有关联影片'"
            :class="[
              'flex items-center gap-1 min-w-0 max-w-full px-1.5 py-0.5 rounded-md font-medium bg-zinc-800 text-zinc-300 border border-zinc-700/50 transition',
              episode.movie_id ? 'hover:text-amber-300 hover:border-amber-500/40' : 'cursor-default',
            ]"
          >
            <Film class="w-2.5 h-2.5 shrink-0 text-zinc-500" />
            <span class="truncate">{{ episode.movie_title }}</span>
          </button>
          <span
            v-if="episode.release_year"
            class="px-1.5 py-0.5 rounded-md font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20"
          >
            {{ episode.release_year }}
          </span>
          <button
            v-if="episode.studio_name"
            type="button"
            @click.stop="emit('filter-studio', episode.studio_name)"
            class="px-1.5 py-0.5 rounded-md bg-zinc-800/60 text-zinc-400 border border-zinc-700/40 hover:text-amber-400 hover:border-amber-500/40 transition truncate max-w-[110px]"
            :title="`按片商 ${episode.studio_name} 筛选影片`"
          >
            {{ episode.studio_name }}
          </button>
        </div>

        <!-- The scene's caption. The site's own title is "Episode #<row id>", which
             says nothing, so it is left to the tooltip rather than shown as a heading. -->
        <p v-if="shownDescription" class="text-[11px] text-zinc-400 leading-relaxed line-clamp-3" :title="episode.title">
          {{ shownDescription }}
        </p>
        <div v-else class="text-[11px] text-zinc-600 italic" :title="episode.title">暂无简介</div>
      </div>

      <div class="space-y-1.5">
        <div v-if="episode.performers.length > 0" class="flex flex-wrap gap-1">
          <button
            v-for="p in episode.performers.slice(0, 3)"
            :key="p.id"
            type="button"
            @click.stop="emit('select-performer', p.id)"
            class="text-[10px] px-1.5 py-0.5 rounded bg-zinc-800/60 text-zinc-400 border border-zinc-700/30 truncate max-w-[90px] hover:text-amber-300 hover:border-amber-500/40 transition"
            :title="p.name"
          >
            {{ p.name }}
          </button>
          <span
            v-if="episode.performers.length > 3"
            class="text-[10px] px-1 py-0.5 rounded bg-zinc-800/30 text-zinc-500"
          >
            +{{ episode.performers.length - 3 }}
          </span>
        </div>
        <div
          v-if="episode.action_notes"
          class="text-[10px] text-zinc-500 bg-zinc-900 px-2 py-1 rounded font-mono truncate"
          :title="`动作标签: ${episode.action_notes}`"
        >
          动作标签: {{ episode.action_notes }}
        </div>
      </div>
    </div>
  </div>
</template>
