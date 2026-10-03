<script setup lang="ts">
import { ref, computed } from 'vue';
import { Film, Heart, Clapperboard, Languages, Sparkles } from '@lucide/vue';
import type { EpisodeSummary } from '../types';
import { getImageUrl } from '../utils/image';
import { episodeOrdinalLabel } from '../utils/episode';
import { pickZh, titlePrimary, titleSecondary, sceneFilm } from '../utils/bilingual';
import { t, currentLocale } from '../i18n';

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

/**
 * The parent film's name in the chosen language.
 *
 * The scene's own `title` is "Episode #<row id>" — a site-generated placeholder that
 * says nothing and is never translated — so the parent film is the only Chinese a
 * scene card can show. `filmAlt` is the original film name, or '' when it is already
 * what the chip shows.
 */
const filmPrimary = computed(() => titlePrimary(sceneFilm(props.episode), props.lang));
const filmAlt = computed(() => titleSecondary(sceneFilm(props.episode), props.lang));

const shownDescription = computed(
  () => pickZh(props.episode.description_zh, props.episode.description, props.lang),
);

const isNew = computed(() => {
  if (props.episode.release_date && props.episode.release_date.startsWith('2026')) return true;
  if (props.episode.release_year && props.episode.release_year >= 2026) return true;
  return false;
});
</script>

<template>
  <div
    @click="emit('select', episode)"
    class="group relative flex flex-col rounded-2xl bg-surface/60 border border-line/80 hover:border-accent-fill/50 hover:shadow-xl hover:shadow-accent-fill/10 transition-all duration-300 overflow-hidden cursor-pointer select-none"
  >
    <!-- Still. No `data-zoom-click`: the whole card is click-to-open, so the image
         keeps the app-wide double-click zoom gesture (see utils/lightbox.ts). -->
    <div class="relative w-full aspect-video bg-sunken overflow-hidden">
      <img
        v-if="episode.thumbnail_url && !imgError"
        :src="getImageUrl(episode.thumbnail_url)"
        :alt="filmPrimary || episode.title"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="imgError = true"
        class="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500 ease-out"
      />
      <div
        v-else
        class="w-full h-full flex flex-col items-center justify-center gap-2 bg-gradient-to-b from-surface to-sunken text-fg-5"
      >
        <Clapperboard class="w-8 h-8 stroke-1 text-fg-5" />
        <span class="text-[10px] font-medium">{{ t('episode.noThumbnail') }}</span>
      </div>

      <!-- NEW Badge (Top Left) -->
      <span
        v-if="isNew"
        class="absolute top-2.5 left-2.5 px-1.5 py-0.5 rounded-md text-[9px] font-extrabold bg-gradient-to-r from-amber-500 to-rose-500 text-white shadow-md shadow-rose-500/30 backdrop-blur-md z-10 flex items-center gap-0.5 tracking-wider uppercase"
        :title="t('movie.newlyAdded')"
      >
        <Sparkles class="w-2.5 h-2.5 fill-current" />
        NEW
      </span>

      <!-- Position in the parent film (the site's own "Episode #<row id>" says nothing) -->
      <span
        v-if="positionLabel"
        :class="[
          'on-scrim absolute top-2.5 px-1.5 py-0.5 rounded-md text-[9px] font-bold bg-scrim/70 text-accent-soft backdrop-blur-md z-10',
          isNew ? 'left-14' : 'left-2.5'
        ]"
      >
        {{ positionLabel }}
      </span>

      <!-- Chinese synopsis indicator, matching the film card's badge -->
      <span
        v-if="episode.description_zh?.trim()"
        class="absolute bottom-2.5 left-2.5 px-1.5 py-0.5 rounded-md text-[9px] font-bold bg-success-fill/90 text-on-fill backdrop-blur-md z-10 flex items-center gap-0.5"
        :title="t('filter.hasZhSynopsis')"
      >
        <Languages class="w-2.5 h-2.5" />
        {{ currentLocale.startsWith('zh') ? '中' : 'ZH' }}
      </span>

      <button
        @click.stop="emit('toggle-favorite', episode)"
        :class="[
          'on-scrim absolute top-2.5 right-2.5 w-7 h-7 rounded-full flex items-center justify-center backdrop-blur-md transition-all duration-200 z-10',
          isFavorite
            ? 'bg-danger-fill text-on-danger shadow-lg shadow-danger-fill/40 opacity-100'
            : 'bg-scrim/40 text-fg-3 hover:text-danger hover:bg-scrim/80 opacity-0 group-hover:opacity-100'
        ]"
        :title="isFavorite ? t('movie.favorited') : t('movie.favorite')"
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
            :title="episode.movie_id ? t('episode.goToFilm', { title: filmPrimary }) : t('episode.noLinkedFilm')"
            :class="[
              'flex items-center gap-1 min-w-0 max-w-full px-1.5 py-0.5 rounded-md font-medium bg-surface-2 text-fg-2 border border-line-strong/50 transition',
              episode.movie_id ? 'hover:text-accent-soft hover:border-accent-fill/40' : 'cursor-default',
            ]"
          >
            <Film class="w-2.5 h-2.5 shrink-0 text-fg-4" />
            <span class="truncate">{{ filmPrimary }}</span>
            <span v-if="filmAlt" class="truncate text-fg-4 font-normal">{{ filmAlt }}</span>
          </button>
          <span
            v-if="episode.release_date || episode.release_year"
            class="px-1.5 py-0.5 rounded-md font-semibold bg-accent-fill/10 text-accent border border-accent-fill/20 font-mono"
          >
            {{ episode.release_date || episode.release_year }}
          </span>
          <button
            v-if="episode.studio_name"
            type="button"
            @click.stop="emit('filter-studio', episode.studio_name)"
            class="px-1.5 py-0.5 rounded-md bg-surface-2/60 text-fg-3 border border-line-strong/40 hover:text-accent hover:border-accent-fill/40 transition truncate max-w-[110px]"
            :title="t('episode.filterByStudio', { studio: episode.studio_name })"
          >
            {{ episode.studio_name }}
          </button>
        </div>

        <!-- The scene's caption. The site's own title is "Episode #<row id>", which
             says nothing, so it is left to the tooltip rather than shown as a heading. -->
        <p v-if="shownDescription" class="text-[11px] text-fg-3 leading-relaxed line-clamp-3" :title="episode.title">
          {{ shownDescription }}
        </p>
        <div v-else class="text-[11px] text-fg-5 italic" :title="episode.title">{{ t('movie.noSynopsis') }}</div>
      </div>

      <div class="space-y-1.5">
        <div v-if="episode.performers.length > 0" class="flex flex-wrap gap-1">
          <button
            v-for="p in episode.performers.slice(0, 3)"
            :key="p.id"
            type="button"
            @click.stop="emit('select-performer', p.id)"
            class="text-[10px] px-1.5 py-0.5 rounded bg-surface-2/60 text-fg-3 border border-line-strong/30 truncate max-w-[90px] hover:text-accent-soft hover:border-accent-fill/40 transition"
            :title="p.name"
          >
            {{ p.name }}
          </button>
          <span
            v-if="episode.performers.length > 3"
            class="text-[10px] px-1 py-0.5 rounded bg-surface-2/30 text-fg-4"
          >
            +{{ episode.performers.length - 3 }}
          </span>
        </div>
        <div
          v-if="episode.action_notes"
          class="text-[10px] text-fg-4 bg-surface px-2 py-1 rounded font-mono truncate"
          :title="`${t('episode.actionNotes')}: ${episode.action_notes}`"
        >
          {{ t('episode.actionNotes') }}: {{ episode.action_notes }}
        </div>
      </div>
    </div>
  </div>
</template>
