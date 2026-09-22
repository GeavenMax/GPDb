<script setup lang="ts">
import { computed } from 'vue';
import { SlidersHorizontal, RotateCcw } from '@lucide/vue';
import FilterChip from './FilterChip.vue';
import type { FilterState, PerformerFilterState, EpisodeFilterState } from '../types';
import { FACET_KEYS, FACET_LABELS } from '../api';
import { trCategory } from '../utils/glossary';

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

const activeChips = computed<ChipItem[]>(() => {
  const chips: ChipItem[] = [];

  if (props.tab === 'movies' && props.movieFilters) {
    const f = props.movieFilters;
    if (f.studio) {
      chips.push({
        id: `m-studio-${f.studio}`,
        label: '厂牌:',
        value: f.studio,
        onRemove: () => emit('clear-movie-field', 'studio'),
      });
    }
    if (f.director) {
      chips.push({
        id: `m-director-${f.director}`,
        label: '导演:',
        value: f.director,
        onRemove: () => emit('clear-movie-field', 'director'),
      });
    }
    if (f.category) {
      chips.push({
        id: `m-category-${f.category}`,
        label: '分类:',
        value: trCategory(f.category),
        onRemove: () => emit('clear-movie-field', 'category'),
      });
    }
    if (f.yearMin != null || f.yearMax != null) {
      let val = '';
      if (f.yearMin != null && f.yearMax != null) val = `${f.yearMin} - ${f.yearMax} 年`;
      else if (f.yearMin != null) val = `≥ ${f.yearMin} 年`;
      else if (f.yearMax != null) val = `≤ ${f.yearMax} 年`;
      chips.push({
        id: 'm-years',
        label: '年代:',
        value: val,
        onRemove: () => emit('clear-movie-years'),
      });
    }
    if (f.query?.trim()) {
      chips.push({
        id: 'm-query',
        label: '影片搜索:',
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
            label: `${FACET_LABELS[key] || key}:`,
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
        value: '仅有照片',
        onRemove: () => emit('clear-performer-field', 'hasImage'),
      });
    }
    if (pf.minMovies != null) {
      chips.push({
        id: 'p-min-movies',
        label: '作品数:',
        value: `≥ ${pf.minMovies} 部`,
        onRemove: () => emit('clear-performer-field', 'minMovies'),
      });
    }
  } else if (props.tab === 'episodes' && props.episodeFilters) {
    const ef = props.episodeFilters;
    if (ef.studio) {
      chips.push({
        id: `ep-studio-${ef.studio}`,
        label: '片商:',
        value: ef.studio,
        onRemove: () => emit('clear-episode-field', 'studio'),
      });
    }
    if (ef.hasZh) {
      chips.push({
        id: 'ep-has-zh',
        label: '',
        value: '有中文简介',
        onRemove: () => emit('clear-episode-field', 'hasZh'),
      });
    }
    if (ef.hasPerformers) {
      chips.push({
        id: 'ep-has-performers',
        label: '',
        value: '包含演员',
        onRemove: () => emit('clear-episode-field', 'hasPerformers'),
      });
    }
  } else if (props.tab === 'studios' && props.studioQuery?.trim()) {
    chips.push({
      id: 'studio-query',
      label: '搜索:',
      value: props.studioQuery.trim(),
      onRemove: () => emit('clear-studio-query'),
    });
  } else if (props.tab === 'directors' && props.directorQuery?.trim()) {
    chips.push({
      id: 'director-query',
      label: '搜索:',
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
        <span>当前生效筛选 ({{ activeChips.length }})：</span>
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
      class="shrink-0 flex items-center gap-1 text-xs text-fg-4 hover:text-accent font-medium px-2.5 py-1 rounded-xl bg-surface-2/60 hover:bg-surface-2 border border-line-strong hover:border-accent/30 transition shadow-xs"
      title="清空当前页面所有筛选条件"
    >
      <RotateCcw class="w-3 h-3" />
      <span>清空筛选</span>
    </button>
  </div>
</template>
