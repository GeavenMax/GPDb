<script setup lang="ts">
import { computed } from 'vue';
import { SlidersHorizontal, RotateCcw } from '@lucide/vue';
import FilterChip from './FilterChip.vue';
import type { FilterState, PerformerFilterState, EpisodeFilterState } from '../types';
import { FACET_KEYS, FACET_LABELS } from '../api';
import { trCategory } from '../utils/glossary';
import { t } from '../i18n';

interface ChipItem {
  id: string;
  label: string;
  value: string;
  onRemove: () => void;
}

const props = defineProps<{
  tab: string;
  movieFilters?: FilterState;
  performerFilters?: PerformerFilterState;
  episodeFilters?: EpisodeFilterState;
  studioQuery?: string;
  directorQuery?: string;
}>();

const emit = defineEmits<{
  (e: 'clear-movie-field', field: keyof FilterState): void;
  (e: 'clear-movie-years'): void;
  (e: 'reset-movies'): void;
  (e: 'clear-performer-facet', key: keyof PerformerFilterState, value: string): void;
  (e: 'clear-performer-field', field: 'hasImage' | 'minMovies'): void;
  (e: 'reset-performers'): void;
  (e: 'clear-episode-field', field: keyof EpisodeFilterState): void;
  (e: 'reset-episodes'): void;
  (e: 'clear-studio-query'): void;
  (e: 'clear-director-query'): void;
}>();

function getDateFilterLabel(id: string, isEpisode: boolean = false): string {
  if (id === 'all') return t('common.all');
  if (id === 'last_scraped') return t('filter.lastScraped');
  if (isEpisode) {
    if (id === 'recent_7') return t('filter.epRecent7');
    if (id === 'recent_30') return t('filter.epRecent30');
    if (id === 'recent_90') return t('filter.epRecent90');
    if (id === 'recent_year') return t('filter.epRecentYear');
  } else {
    if (id === 'recent_7') return t('filter.recent7');
    if (id === 'recent_30') return t('filter.recent30');
    if (id === 'recent_90') return t('filter.recent90');
    if (id === 'recent_year') return t('filter.recentYear');
  }
  return id;
}

function facetLabel(key: string): string {
  const map: Record<string, string> = {
    bodyType: 'performer.bodyType',
    hair: 'performer.hair',
    eyes: 'performer.eyes',
    skin: 'performer.skin',
    bodyHair: 'performer.bodyHair',
    facialHair: 'performer.facialHair',
    dickSize: 'performer.dickSize',
    foreskin: 'performer.foreskin',
  };
  return map[key] ? t(map[key]) : (FACET_LABELS[key] || key);
}

const activeChips = computed<ChipItem[]>(() => {
  const chips: ChipItem[] = [];

  if (props.tab === 'movies' && props.movieFilters) {
    const f = props.movieFilters;
    if (f.studio) {
      chips.push({
        id: `m-studio-${f.studio}`,
        label: `${t('filter.studio')}:`,
        value: f.studio,
        onRemove: () => emit('clear-movie-field', 'studio'),
      });
    }
    if (f.director) {
      chips.push({
        id: `m-director-${f.director}`,
        label: `${t('filter.director')}:`,
        value: f.director,
        onRemove: () => emit('clear-movie-field', 'director'),
      });
    }
    if (f.category) {
      chips.push({
        id: `m-category-${f.category}`,
        label: `${t('filter.category')}:`,
        value: trCategory(f.category),
        onRemove: () => emit('clear-movie-field', 'category'),
      });
    }
    if (f.dateFilter && f.dateFilter !== 'all') {
      chips.push({
        id: `m-date-${f.dateFilter}`,
        label: `${t('filter.time')}:`,
        value: getDateFilterLabel(f.dateFilter, false),
        onRemove: () => emit('clear-movie-field', 'dateFilter'),
      });
    }
    if (f.yearMin != null || f.yearMax != null) {
      let val = '';
      if (f.yearMin != null && f.yearMax != null) val = `${f.yearMin} - ${f.yearMax}`;
      else if (f.yearMin != null) val = `≥ ${f.yearMin}`;
      else if (f.yearMax != null) val = `≤ ${f.yearMax}`;
      chips.push({
        id: 'm-years',
        label: `${t('filter.era')}:`,
        value: val,
        onRemove: () => emit('clear-movie-years'),
      });
    }
    if (f.query?.trim()) {
      chips.push({
        id: 'm-query',
        label: `${t('filter.movieSearch')}:`,
        value: f.query.trim(),
        onRemove: () => emit('clear-movie-field', 'query'),
      });
    }
  } else if (props.tab === 'performers' && props.performerFilters) {
    const pf = props.performerFilters;
    for (const key of FACET_KEYS) {
      const list = pf[key] as string[] | undefined;
      if (list && list.length > 0) {
        for (const val of list) {
          chips.push({
            id: `p-${key}-${val}`,
            label: `${facetLabel(key)}:`,
            value: val,
            onRemove: () => emit('clear-performer-facet', key, val),
          });
        }
      }
    }
    if (pf.hasImage) {
      chips.push({
        id: 'p-has-image',
        label: '',
        value: t('filter.onlyWithPhoto'),
        onRemove: () => emit('clear-performer-field', 'hasImage'),
      });
    }
    if (pf.minMovies != null) {
      chips.push({
        id: 'p-min-movies',
        label: `${t('filter.worksCount')}:`,
        value: `≥ ${pf.minMovies}`,
        onRemove: () => emit('clear-performer-field', 'minMovies'),
      });
    }
  } else if (props.tab === 'episodes' && props.episodeFilters) {
    const ef = props.episodeFilters;
    if (ef.studio) {
      chips.push({
        id: `ep-studio-${ef.studio}`,
        label: `${t('filter.studio')}:`,
        value: ef.studio,
        onRemove: () => emit('clear-episode-field', 'studio'),
      });
    }
    if (ef.hasZh) {
      chips.push({
        id: 'ep-has-zh',
        label: '',
        value: t('filter.hasZhSynopsis'),
        onRemove: () => emit('clear-episode-field', 'hasZh'),
      });
    }
    if (ef.hasPerformers) {
      chips.push({
        id: 'ep-has-performers',
        label: '',
        value: t('filter.hasPerformers'),
        onRemove: () => emit('clear-episode-field', 'hasPerformers'),
      });
    }
    if (ef.dateFilter && ef.dateFilter !== 'all') {
      chips.push({
        id: `ep-date-${ef.dateFilter}`,
        label: `${t('filter.time')}:`,
        value: getDateFilterLabel(ef.dateFilter, true),
        onRemove: () => emit('clear-episode-field', 'dateFilter'),
      });
    }
  } else if (props.tab === 'studios' && props.studioQuery?.trim()) {
    chips.push({
      id: 'studio-query',
      label: `${t('common.search')}:`,
      value: props.studioQuery.trim(),
      onRemove: () => emit('clear-studio-query'),
    });
  } else if (props.tab === 'directors' && props.directorQuery?.trim()) {
    chips.push({
      id: 'director-query',
      label: `${t('common.search')}:`,
      value: props.directorQuery.trim(),
      onRemove: () => emit('clear-director-query'),
    });
  }

  return chips;
});

function handleClearAll() {
  if (props.tab === 'movies') {
    emit('reset-movies');
  } else if (props.tab === 'performers') {
    emit('reset-performers');
  } else if (props.tab === 'episodes') {
    emit('reset-episodes');
  } else if (props.tab === 'studios') {
    emit('clear-studio-query');
  } else if (props.tab === 'directors') {
    emit('clear-director-query');
  }
}
</script>

<template>
  <div
    v-if="activeChips.length > 0"
    class="flex items-center justify-between gap-3 px-4 py-2.5 rounded-2xl bg-surface/90 border border-accent/25 backdrop-blur-md shadow-sm transition-all animate-fade-in"
  >
    <div class="flex items-center gap-2.5 flex-wrap flex-1 min-w-0">
      <div class="flex items-center gap-1.5 text-xs font-bold text-accent shrink-0">
        <SlidersHorizontal class="w-3.5 h-3.5" />
        <span>{{ t('filter.activeFilters') }} ({{ activeChips.length }}):</span>
      </div>

      <div class="flex items-center gap-1.5 flex-wrap">
        <FilterChip
          v-for="chip in activeChips"
          :key="chip.id"
          variant="accent"
          :label="chip.label"
          :value="chip.value"
          @remove="chip.onRemove"
        />
      </div>
    </div>

    <button
      type="button"
      @click="handleClearAll"
      class="shrink-0 flex items-center gap-1 text-xs text-fg-4 hover:text-accent font-medium px-2.5 py-1 rounded-xl bg-surface-2/60 hover:bg-surface-2 border border-line-strong hover:border-accent/30 transition shadow-xs cursor-pointer"
      :title="t('filter.clearAll')"
    >
      <RotateCcw class="w-3 h-3" />
      <span>{{ t('filter.clearAll') }}</span>
    </button>
  </div>
</template>
