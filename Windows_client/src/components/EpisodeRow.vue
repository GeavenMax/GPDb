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
import { computed, ref, watch } from 'vue';
import { Film, Heart, Calendar, Clapperboard, ChevronDown, ChevronUp, ExternalLink } from '@lucide/vue';
import type { Episode } from '../types';
import { getImageUrl } from '../utils/image';
import { episodeOrdinalLabel } from '../utils/episode';
import { titlePrimary, titleSecondary, sceneFilm } from '../utils/bilingual';
import { pluginsConfig } from '../services/pluginManager';
import { t } from '../i18n';
import { defineAsyncComponent } from 'vue';

const ResourceSearchWidget = defineAsyncComponent(() => import('./plugins/ResourceSearchWidget.vue'));

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
   */
  lang?: 'zh' | 'en';
  /** External expand state for batch expand/collapse */
  expanded?: boolean;
}>(), {
  isFavorite: false,
  showSource: true,
  clickable: false,
  zoomOnClick: false,
  showStudio: false,
  showYear: true,
  lang: 'zh',
  expanded: undefined,
});

const emit = defineEmits<{
  (e: 'select-movie-id', movieId: number): void;
  (e: 'toggle-favorite'): void;
  (e: 'filter-studio', studioName: string): void;
  (e: 'select-episode-id', episodeId: number): void;
}>();

const imgError = ref(false);

/** "第 3 集 / 共 5 集" when the caller knows the position, else the site's title. */
const title = computed(() =>
  episodeOrdinalLabel({ episode_ordinal: props.ordinal, episode_count: props.ordinalCount })
  || props.episode.title
  || t('episode.unnamed')
);

/** Local override for toggling between original and Chinese translation. */
const showOriginalOverride = ref<boolean | null>(null);

const hasZhDesc = computed(() => Boolean(props.episode.description_zh?.trim()));
const hasEnDesc = computed(() => Boolean(props.episode.description?.trim()));

const effectiveShowOriginal = computed(() => {
  if (showOriginalOverride.value !== null) return showOriginalOverride.value;
  return props.lang === 'en';
});

/** Chinese when we have it and the user asked for it, otherwise the original. */
const synopsis = computed(() => {
  if (effectiveShowOriginal.value) {
    return (props.episode.description || '').trim();
  }
  return (props.episode.description_zh || props.episode.description || '').trim();
});

/** Whether the synopsis is long enough to benefit from expand/collapse. */
const isLongSynopsis = computed(() => {
  const text = synopsis.value;
  if (!text) return false;
  return text.length > 90 || text.includes('\n');
});

const isExpanded = ref(false);

watch(() => props.expanded, (newVal) => {
  if (newVal !== undefined) {
    isExpanded.value = newVal;
  }
}, { immediate: true });

function toggleExpanded(e?: Event) {
  if (e) e.stopPropagation();
  isExpanded.value = !isExpanded.value;
}

/** The parent film's name in the chosen language, and the original beneath it. */
const filmPrimary = computed(() => titlePrimary(sceneFilm(props.episode), props.lang));
const filmAlt = computed(() => titleSecondary(sceneFilm(props.episode), props.lang));

/** The whole bilingual name, for tooltips where the row itself only has one line. */
const filmFull = computed(() =>
  filmAlt.value ? `${filmPrimary.value} (${filmAlt.value})` : filmPrimary.value
);

function toggleOriginal(e?: Event) {
  if (e) e.stopPropagation();
  showOriginalOverride.value = !effectiveShowOriginal.value;
}

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
        <span class="text-[9px] font-medium">{{ t('episode.noThumbnail') }}</span>
      </div>
    </div>

    <div class="min-w-0 flex-1 flex flex-col gap-1.5">
      <div class="flex items-start justify-between gap-2">
        <button
          v-if="!clickable"
          type="button"
          @click.stop="emit('select-episode-id', episode.id)"
          class="text-sm font-bold text-accent-soft hover:text-accent hover:underline text-left cursor-pointer transition flex items-center gap-1 group/title"
          :title="t('episode.profile')"
        >
          <span>{{ title }}</span>
          <ExternalLink class="w-3 h-3 opacity-0 group-hover/title:opacity-100 transition-opacity text-accent shrink-0" />
        </button>
        <span v-else class="text-sm font-bold text-accent-soft">{{ title }}</span>
        <div class="flex items-center gap-2 shrink-0">
          <span
            v-if="showYear && (episode.release_date || episode.release_year)"
            class="text-[10px] text-fg-4 font-mono flex items-center gap-1"
          >
            <Calendar class="w-2.5 h-2.5" /> {{ episode.release_date || episode.release_year }}
          </span>
          <ResourceSearchWidget 
            v-if="pluginsConfig.resourceSearchEnabled && (episode.movie_title || episode.title)"
            type="episode" 
            :title="episode.movie_title || episode.title"
            iconOnly 
          />
          <button
            @click.stop="emit('toggle-favorite')"
            :title="isFavorite ? t('episode.unfavorite') : t('episode.favorite')"
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
        <span v-if="clickable" class="truncate" :title="filmFull">
          {{ t('episode.source') }}: {{ filmPrimary }}
          <span v-if="filmAlt" class="text-fg-4 font-normal text-[11px] ml-1">({{ filmAlt }})</span>
        </span>
        <button
          v-else
          type="button"
          :disabled="!episode.movie_id"
          @click.stop="episode.movie_id && emit('select-movie-id', episode.movie_id)"
          :title="episode.movie_id ? `${t('episode.jumpToMovie')}: 《${filmFull}》` : t('episode.noMovie')"
          :class="[
            'truncate transition text-left',
            episode.movie_id ? 'hover:text-accent-soft hover:underline cursor-pointer' : 'cursor-default'
          ]"
        >
          {{ t('episode.source') }}: {{ filmPrimary }}
          <span v-if="filmAlt" class="text-fg-4 font-normal text-[11px] ml-1">({{ filmAlt }})</span>
        </button>
        <button
          v-if="showStudio && episode.studio_name"
          @click.stop="emit('filter-studio', episode.studio_name!)"
          class="text-fg-4 hover:text-accent text-[10px] ml-1 shrink-0 transition"
        >({{ episode.studio_name }})</button>
      </div>

      <div v-if="synopsis" class="text-sm text-fg-3 leading-relaxed">
        <div class="flex items-start justify-between gap-2">
          <p
            :class="[
              isExpanded && isLongSynopsis
                ? 'whitespace-pre-line select-text text-fg-2'
                : (isLongSynopsis ? 'line-clamp-4 cursor-pointer hover:text-fg-2' : ''),
              'flex-1 transition-colors'
            ]"
            :title="isLongSynopsis && !isExpanded ? t('common.expand') : undefined"
            @click="isLongSynopsis && !isExpanded && toggleExpanded()"
          >
            {{ synopsis }}
          </p>
          <div class="flex items-center gap-1.5 shrink-0 self-start">
            <button
              v-if="hasZhDesc && hasEnDesc"
              type="button"
              @click.stop="toggleOriginal"
              class="text-[10px] px-1.5 py-0.5 rounded bg-surface hover:bg-surface-2 text-fg-4 hover:text-accent border border-line-strong transition shrink-0 cursor-pointer"
              :title="effectiveShowOriginal ? t('episode.showTranslation') : t('episode.showOriginal')"
            >
              {{ effectiveShowOriginal ? t('episode.showTranslation') : t('episode.showOriginal') }}
            </button>
            <button
              v-if="isLongSynopsis"
              type="button"
              @click.stop="toggleExpanded"
              class="flex items-center gap-0.5 text-[10px] px-1.5 py-0.5 rounded bg-surface hover:bg-surface-2 text-accent hover:text-accent-soft border border-line-strong transition shrink-0 cursor-pointer"
              :title="isExpanded ? t('common.collapse') : t('common.expand')"
            >
              <span>{{ isExpanded ? t('common.collapse') : t('common.expand') }}</span>
              <component :is="isExpanded ? ChevronUp : ChevronDown" class="w-3 h-3" />
            </button>
          </div>
        </div>
        <div v-if="isExpanded && isLongSynopsis" class="mt-1.5 flex justify-end">
          <button
            type="button"
            @click.stop="toggleExpanded"
            class="text-[11px] text-accent hover:text-accent-soft hover:underline flex items-center gap-0.5 transition cursor-pointer"
          >
            <span>{{ t('common.collapse') }}</span>
            <ChevronUp class="w-3 h-3" />
          </button>
        </div>
      </div>

      <div
        v-if="episode.action_notes"
        class="text-[10px] text-fg-4 bg-surface px-2 py-1 rounded font-mono self-start"
      >
        {{ t('episode.actionNotes') }}: {{ episode.action_notes }}
      </div>
    </div>
  </div>
</template>
