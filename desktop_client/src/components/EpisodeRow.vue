<script setup lang="ts">
/**
 * One scene in a *list* — the 收录章节 / 场景片段 tab of the film, studio and performer
 * modals.
 *
 * Laid out horizontally, and deliberately not the same component as EpisodeCard.vue:
 * the library grid is browsing (a big still per tile is the point), while these three
 * lists are reading. A full-width still pushed the synopsis into a 12px, 3-line box —
 * measured against the data, a scene synopsis runs 435 characters on average and up to
 * 1,422, so that box was hiding most of what it had. Trading the still's width for the
 * text (160×90 on the left, 14px, 4 lines on the right) roughly doubles the visible
 * characters, and at 160px wide the still is shown near its native resolution anyway,
 * which is why the thumbnails looked soft at full width to begin with.
 */
import { computed, ref } from 'vue';
import { Film, Heart, Calendar, Clapperboard, ExternalLink } from '@lucide/vue';
import type { Episode } from '../types';
import { getImageUrl } from '../utils/image';
import { episodeOrdinalLabel } from '../utils/episode';
import { pickZh, titlePrimary, titleSecondary, sceneFilm } from '../utils/bilingual';
import { pluginsConfig, openBtSearch } from '../services/pluginManager';

const props = withDefaults(defineProps<{
  episode: Episode;
  isFavorite?: boolean;
  /** Show where the scene came from. Off for a film's own scene list. */
  showSource?: boolean;
  /** The whole row opens `movie_id`. */
  clickable?: boolean;
  /**
   * Single-click zooms the still (see utils/lightbox.ts). Only for rows that have no
   * click action of their own — a clickable row keeps the double-click gesture.
   */
  zoomOnClick?: boolean;
  /** Offer the studio name as a jump into that studio. */
  showStudio?: boolean;
  /**
   * Where the scene sits in its own film, when the caller knows. A film's own scene
   * list does (both backends order it by `id ASC`), so the film modal passes its
   * index; the studio and performer payloads carry no ordinal and fall back to the
   * site's own title.
   */
  ordinal?: number | null;
  ordinalCount?: number | null;
  /** The year chip is redundant in a film's own scene list. */
  showYear?: boolean;
  /**
   * Which language the synopsis and the source film's name are shown in.
   *
   * This row was the one place that had lost the `lang` half of the rule: it took
   * `description_zh` whenever it existed, so a row kept showing Chinese after the
   * user switched to the original even though the card beside it switched correctly.
   */
  lang?: 'zh' | 'en';
}>(), {
  isFavorite: false,
  showSource: true,
  clickable: false,
  zoomOnClick: false,
  showStudio: false,
  showYear: true,
  lang: 'zh',
});

const emit = defineEmits<{
  (e: 'select-movie-id', movieId: number): void;
  (e: 'toggle-favorite'): void;
  (e: 'filter-studio', studioName: string): void;
}>();

const imgError = ref(false);

/** "第 3 集 / 共 5 集" when the caller knows the position, else the site's title. */
const title = computed(() =>
  episodeOrdinalLabel({ episode_ordinal: props.ordinal, episode_count: props.ordinalCount })
  || props.episode.title
  || '未命名片段'
);

/** Chinese when we have it and the user asked for it, otherwise the original. */
const synopsis = computed(() =>
  pickZh(props.episode.description_zh, props.episode.description, props.lang)
);

/** The parent film's name in the chosen language, and the original beneath it. */
const filmPrimary = computed(() => titlePrimary(sceneFilm(props.episode), props.lang));
const filmAlt = computed(() => titleSecondary(sceneFilm(props.episode), props.lang));

/** The whole bilingual name, for tooltips where the row itself only has one line. */
const filmFull = computed(() =>
  filmAlt.value ? `${filmPrimary.value}（${filmAlt.value}）` : filmPrimary.value
);

function onRowClick() {
  if (props.clickable && props.episode.movie_id) emit('select-movie-id', props.episode.movie_id);
}
</script>

<template>
  <div
    @click="onRowClick"
    :class="[
      'flex gap-3 p-3 rounded-2xl bg-sunken/80 border border-line/80 transition group',
      clickable
        ? 'cursor-pointer hover:border-accent-fill/40'
        : 'hover:border-line-strong',
    ]"
  >
    <!-- Still. Always rendered, even with nothing in it: a row whose image is
         missing stays the same height as its neighbours in the list. -->
    <div class="relative w-40 shrink-0 aspect-video rounded-lg bg-surface overflow-hidden">
      <img
        v-if="episode.thumbnail_url && !imgError"
        :src="getImageUrl(episode.thumbnail_url)"
        :alt="title"
        loading="lazy"
        referrerpolicy="no-referrer"
        :data-zoom-click="zoomOnClick ? true : undefined"
        @error="imgError = true"
        class="w-full h-full object-cover object-center transition-transform duration-500 ease-out group-hover:scale-105"
      />
      <div
        v-else
        class="w-full h-full flex flex-col items-center justify-center gap-1 text-fg-5 bg-gradient-to-b from-surface to-sunken"
      >
        <Clapperboard class="w-5 h-5 stroke-1" />
        <span class="text-[9px] font-medium">暂无剧照</span>
      </div>
    </div>

    <div class="min-w-0 flex-1 flex flex-col gap-1.5">
      <div class="flex items-start justify-between gap-2">
        <span class="text-sm font-bold text-accent-soft">{{ title }}</span>
        <div class="flex items-center gap-2 shrink-0">
          <span
            v-if="showYear && episode.release_year"
            class="text-[10px] text-fg-4 font-mono flex items-center gap-1"
          >
            <Calendar class="w-2.5 h-2.5" /> {{ episode.release_year }}
          </span>
          <button
            v-if="pluginsConfig.btSearchEnabled && (episode.movie_title || episode.title)"
            @click.stop="openBtSearch(episode.movie_title || episode.title)"
            :title="`在 BT 站检索「${episode.movie_title || episode.title}」`"
            class="text-fg-5 hover:text-accent p-0.5 rounded transition"
          >
            <ExternalLink class="w-3.5 h-3.5" />
          </button>
          <button
            @click.stop="emit('toggle-favorite')"
            :title="isFavorite ? '取消收藏该片段' : '收藏该片段'"
            class="transition"
            :class="isFavorite ? 'text-danger' : 'text-fg-5 hover:text-danger'"
          >
            <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
          </button>
        </div>
      </div>

      <div
        v-if="showSource && episode.movie_title"
        class="text-xs font-medium text-fg-2 flex items-center gap-1 min-w-0"
      >
        <Film class="w-3 h-3 text-fg-4 shrink-0" />
        <!-- A clickable row already opens the film, so the label is only a jump
             where the row itself does nothing. -->
        <span v-if="clickable" class="truncate" :title="filmFull">出处: {{ filmPrimary }}</span>
        <button
          v-else
          type="button"
          :disabled="!episode.movie_id"
          @click.stop="episode.movie_id && emit('select-movie-id', episode.movie_id)"
          :title="episode.movie_id ? `跳转到《${filmFull}》` : '该片段没有关联影片'"
          :class="[
            'truncate transition',
            episode.movie_id ? 'hover:text-accent-soft hover:underline' : 'cursor-default'
          ]"
        >出处: {{ filmPrimary }}</button>
        <button
          v-if="showStudio && episode.studio_name"
          @click.stop="emit('filter-studio', episode.studio_name!)"
          class="text-fg-4 hover:text-accent text-[10px] ml-1 shrink-0 transition"
        >({{ episode.studio_name }})</button>
      </div>

      <div v-if="synopsis" class="text-sm text-fg-3 leading-relaxed line-clamp-4">
        {{ synopsis }}
      </div>

      <div
        v-if="episode.action_notes"
        class="text-[10px] text-fg-4 bg-surface px-2 py-1 rounded font-mono self-start"
      >
        动作标签: {{ episode.action_notes }}
      </div>
    </div>
  </div>
</template>
