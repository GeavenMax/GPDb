<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { X, RotateCcw, Camera, Film, Clapperboard, Languages, Users } from '@lucide/vue';
import type {
  FilterState,
  PerformerFilterState,
  PerformerFacets,
  PerformerSortBy,
  EpisodeFilterState,
  EpisodeSortBy,
} from '../types';
import { FACET_KEYS, FACET_LABELS, EPISODE_SORTS } from '../api';
import { tr, trCategory } from '../utils/glossary';

const props = defineProps<{
  open: boolean;
  /** Which tab the drawer is filtering; each section only applies to its own tab. */
  tab?: string;
  filters: FilterState;
  studios: string[];
  categories: string[];
  performerFilters?: PerformerFilterState;
  performerFacets?: PerformerFacets;
  episodeFilters?: EpisodeFilterState;
  episodeSortBy?: EpisodeSortBy;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'update:filters', filters: FilterState): void;
  (e: 'update:performer-filters', filters: PerformerFilterState): void;
  (e: 'toggle-performer-facet', key: keyof PerformerFilterState, value: string): void;
  (e: 'update:episode-filters', filters: EpisodeFilterState): void;
  (e: 'update:episode-sort', sortBy: EpisodeSortBy): void;
  (e: 'reset'): void;
  (e: 'reset-performers'): void;
  (e: 'reset-episodes'): void;
}>();

const local = ref<FilterState>({ ...props.filters });

watch(() => props.filters, (val) => {
  local.value = { ...val };
}, { deep: true });

const isPerformerTab = computed(() => props.tab === 'performers');
const isEpisodeTab = computed(() => props.tab === 'episodes');

/**
 * Episodes are filtered by a different shape than films, so the drawer edits a
 * parallel state object. The studio list is the one thing shared with the movie
 * section — both filter on the same `movies.studio_name` column.
 */
function patchEpisodeFilters(patch: Partial<EpisodeFilterState>) {
  emit('update:episode-filters', { ...(props.episodeFilters as EpisodeFilterState), ...patch });
}

const MIN_MOVIE_OPTIONS = [1, 5, 10, 25, 50];

const PERFORMER_SORTS: Array<{ id: PerformerSortBy; label: string }> = [
  { id: 'movies_desc', label: '作品最多' },
  { id: 'movies_asc', label: '作品最少' },
  { id: 'name_asc', label: '姓名 A-Z' },
  { id: 'id_desc', label: '最新入库' },
  { id: 'id_asc', label: '最早收录' },
];

/** Facets the server actually has values for — an all-empty facet is not offered. */
const availableFacets = computed(() =>
  FACET_KEYS.filter(key => (props.performerFacets?.facets?.[key]?.length || 0) > 0)
);

function facetValues(key: string) {
  return props.performerFacets?.facets?.[key] || [];
}

function isFacetActive(key: string, value: string) {
  return Boolean((props.performerFilters?.[key as keyof PerformerFilterState] as string[] | undefined)?.includes(value));
}

function toggleFacet(key: string, value: string) {
  emit('toggle-performer-facet', key as keyof PerformerFilterState, value);
}

function patchPerformerFilters(patch: Partial<PerformerFilterState>) {
  emit('update:performer-filters', { ...(props.performerFilters as PerformerFilterState), ...patch });
}

function apply() {
  emit('update:filters', { ...local.value });
}

function selectStudio(st: string) {
  local.value.studio = local.value.studio === st ? '' : st;
  apply();
}

/**
 * Directors are filtered by name but are not offered as a chip list here.
 *
 * There is no director list to render — unlike studios, the server has no cheap
 * `SELECT DISTINCT director_name` endpoint the drawer could load, and the list runs
 * into the thousands. A director filter is instead set from the places that name a
 * director (a film's detail page, or the favorites page), so what the drawer owes
 * the user is visibility: an active director must be shown and clearable, or the
 * grid would silently narrow with nothing on screen to explain why.
 */
function clearDirector() {
  local.value.director = '';
  apply();
}

function selectCategory(cat: string) {
  local.value.category = local.value.category === cat ? '' : cat;
  apply();
}

function selectSort(sort: FilterState['sortBy']) {
  local.value.sortBy = sort;
  apply();
}

function resetAll() {
  local.value = {
    query: '',
    studio: '',
    director: '',
    yearMin: null,
    yearMax: null,
    category: '',
    sortBy: 'year_desc',
  };
  emit('reset');
}
</script>

<template>
  <div>
    <!-- Backdrop -->
    <div
      v-if="open"
      class="fixed inset-0 z-40 bg-scrim/60 backdrop-blur-sm transition-opacity"
      @click="emit('close')"
    ></div>

    <!-- Slide-over Drawer -->
    <aside
      :class="[
        'fixed top-0 right-0 bottom-0 chrome-side w-80 max-w-full border-l border-line z-50 p-6 flex flex-col justify-between shadow-2xl transition-transform duration-300 ease-out select-none',
        open ? 'translate-x-0' : 'translate-x-full'
      ]"
    >
      <div class="space-y-6 overflow-y-auto pr-1">
        <!-- Top bar -->
        <div class="flex items-center justify-between pb-4 border-b border-line">
          <div class="font-bold text-fg text-base">
            {{ isPerformerTab ? '演员属性筛选' : isEpisodeTab ? '分集筛选' : '高级筛选' }}
          </div>
          <button @click="emit('close')" class="p-1 rounded-lg text-fg-3 hover:text-fg hover:bg-surface transition">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- ============ Performer filters (issue #7) ============ -->
        <template v-if="isPerformerTab">
          <!-- Coverage notice: the attribute data is only as complete as the scrape -->
          <div
            v-if="performerFacets && performerFacets.enriched < performerFacets.total"
            class="p-3 rounded-xl bg-surface border border-line text-[11px] text-fg-3 leading-relaxed"
          >
            已抓取身体属性档案的演员：
            <span class="font-bold text-accent">{{ performerFacets.enriched.toLocaleString() }}</span>
            /
            {{ performerFacets.total.toLocaleString() }} 位。
            其余演员尚未抓取详情页，筛选结果只覆盖已建档的部分。
          </div>

          <!-- Sort By -->
          <div class="space-y-2">
            <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">排序方式</label>
            <div class="grid grid-cols-2 gap-1.5">
              <button
                v-for="s in PERFORMER_SORTS"
                :key="s.id"
                @click="patchPerformerFilters({ sortBy: s.id })"
                :class="[
                  'px-3 py-2 rounded-xl text-xs font-medium border text-center transition',
                  performerFilters?.sortBy === s.id
                    ? 'bg-accent-fill/10 border-accent-fill/40 text-accent font-bold'
                    : 'bg-surface/60 border-line text-fg-3 hover:text-fg-2'
                ]"
              >
                {{ s.label }}
              </button>
            </div>
          </div>

          <!-- Quick toggles -->
          <div class="space-y-2">
            <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">快速筛选</label>
            <div class="flex flex-wrap gap-1.5">
              <button
                @click="patchPerformerFilters({ hasImage: !performerFilters?.hasImage })"
                :class="[
                  'px-2.5 py-1.5 rounded-lg text-xs font-medium border transition flex items-center gap-1.5',
                  performerFilters?.hasImage
                    ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                    : 'bg-surface border-line text-fg-3 hover:text-fg-2'
                ]"
              >
                <Camera class="w-3 h-3" />
                仅有照片 ({{ performerFacets?.withImage || 0 }})
              </button>
            </div>
          </div>

          <!-- Minimum works -->
          <div class="space-y-2">
            <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">作品数量下限</label>
            <div class="flex flex-wrap gap-1.5">
              <button
                @click="patchPerformerFilters({ minMovies: null })"
                :class="[
                  'px-2.5 py-1 rounded-lg text-xs font-medium border transition',
                  performerFilters?.minMovies == null
                    ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                    : 'bg-surface border-line text-fg-3 hover:text-fg-2'
                ]"
              >
                不限
              </button>
              <button
                v-for="n in MIN_MOVIE_OPTIONS"
                :key="n"
                @click="patchPerformerFilters({ minMovies: n })"
                :class="[
                  'px-2.5 py-1 rounded-lg text-xs font-medium border transition flex items-center gap-1',
                  performerFilters?.minMovies === n
                    ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                    : 'bg-surface border-line text-fg-3 hover:text-fg-2'
                ]"
              >
                <Film class="w-3 h-3" />
                ≥{{ n }}
              </button>
            </div>
          </div>

          <!-- Attribute facets -->
          <div v-for="key in availableFacets" :key="key" class="space-y-2">
            <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">
              {{ FACET_LABELS[key] || key }}
            </label>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="f in facetValues(key)"
                :key="f.value"
                @click="toggleFacet(key, f.value)"
                :class="[
                  'px-2.5 py-1 rounded-lg text-xs font-medium border transition flex items-center gap-1',
                  isFacetActive(key, f.value)
                    ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                    : 'bg-surface border-line text-fg-3 hover:text-fg-2'
                ]"
              >
                <!--
                  Label in Chinese via the glossary, but the filter still sends `f.value`.
                  Attribute values are stored in English; translating the value itself
                  would break the server-side comparison.
                -->
                <span :title="f.value">{{ tr(f.value) }}</span>
                <span
                  :class="isFacetActive(key, f.value) ? 'text-on-fill/60' : 'text-fg-5'"
                  class="text-[10px] font-mono"
                >
                  {{ f.count }}
                </span>
              </button>
            </div>
          </div>

          <div v-if="availableFacets.length === 0" class="text-xs text-fg-5 italic">
            暂无已建档的属性数据
          </div>
        </template>

        <!-- ============ Episode filters ============ -->
        <template v-else-if="isEpisodeTab">
          <!-- Sort By. Also offered on the tab's toolbar, which is the faster path;
               this copy is here because every section of the drawer starts with it. -->
          <div class="space-y-2">
            <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">排序方式</label>
            <div class="grid grid-cols-2 gap-1.5">
              <button
                v-for="s in EPISODE_SORTS"
                :key="s.id"
                @click="emit('update:episode-sort', s.id)"
                :class="[
                  'px-3 py-2 rounded-xl text-xs font-medium border text-center transition',
                  episodeSortBy === s.id
                    ? 'bg-accent-fill/10 border-accent-fill/40 text-accent font-bold'
                    : 'bg-surface/60 border-line text-fg-3 hover:text-fg-2'
                ]"
              >
                {{ s.label }}
              </button>
            </div>
          </div>

          <!-- Quick toggles -->
          <div class="space-y-2">
            <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">快速筛选</label>
            <div class="flex flex-wrap gap-1.5">
              <button
                @click="patchEpisodeFilters({ hasZh: !episodeFilters?.hasZh })"
                :class="[
                  'px-2.5 py-1.5 rounded-lg text-xs font-medium border transition flex items-center gap-1.5',
                  episodeFilters?.hasZh
                    ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                    : 'bg-surface border-line text-fg-3 hover:text-fg-2'
                ]"
                title="只显示已有中文简介的片段"
              >
                <Languages class="w-3 h-3" />
                有中文简介
              </button>
              <button
                @click="patchEpisodeFilters({ hasPerformers: !episodeFilters?.hasPerformers })"
                :class="[
                  'px-2.5 py-1.5 rounded-lg text-xs font-medium border transition flex items-center gap-1.5',
                  episodeFilters?.hasPerformers
                    ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                    : 'bg-surface border-line text-fg-3 hover:text-fg-2'
                ]"
                title="只显示已关联演员的片段"
              >
                <Users class="w-3 h-3" />
                有演员
              </button>
            </div>
          </div>

          <!-- Studio. Same list as the film section: scenes carry no studio of their
               own, so this filters on the studio of the film they came from. -->
          <div class="space-y-2">
            <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">来源片商 (Studio)</label>
            <div class="flex flex-wrap gap-1.5 max-h-48 overflow-y-auto pr-1">
              <button
                v-for="st in studios"
                :key="st"
                @click="patchEpisodeFilters({ studio: episodeFilters?.studio === st ? '' : st })"
                :class="[
                  'px-2.5 py-1 rounded-lg text-xs font-medium border transition',
                  episodeFilters?.studio === st
                    ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                    : 'bg-surface border-line text-fg-3 hover:text-fg-2'
                ]"
              >
                {{ st }}
              </button>
            </div>
          </div>
        </template>

        <!-- ============ Movie filters ============ -->
        <template v-else>
        <!-- Sort By -->
        <div class="space-y-2">
          <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">排序方式</label>
          <div class="grid grid-cols-2 gap-1.5">
            <button
              v-for="s in [
                { id: 'year_desc', label: '最新上映' },
                { id: 'year_asc', label: '最早年代' },
                { id: 'title_asc', label: '片名 A-Z' },
                { id: 'id_desc', label: '最新入库' },
              ]"
              :key="s.id"
              @click="selectSort(s.id as any)"
              :class="[
                'px-3 py-2 rounded-xl text-xs font-medium border text-center transition',
                local.sortBy === s.id
                  ? 'bg-accent-fill/10 border-accent-fill/40 text-accent font-bold'
                  : 'bg-surface/60 border-line text-fg-3 hover:text-fg-2'
              ]"
            >
              {{ s.label }}
            </button>
          </div>
        </div>

        <!-- Studio Filter -->
        <div class="space-y-2">
          <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">制片厂牌 (Studio)</label>
          <div class="flex flex-wrap gap-1.5 max-h-48 overflow-y-auto pr-1">
            <button
              v-for="st in studios"
              :key="st"
              @click="selectStudio(st)"
              :class="[
                'px-2.5 py-1 rounded-lg text-xs font-medium border transition',
                local.studio === st
                  ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                  : 'bg-surface border-line text-fg-3 hover:text-fg-2'
              ]"
            >
              {{ st }}
            </button>
          </div>
        </div>

        <!-- Active Director Filter — set from a film's page or the favorites page -->
        <div v-if="local.director" class="space-y-2">
          <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">导演 (Director)</label>
          <div class="flex items-center gap-2">
            <span class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-bold bg-accent-fill text-on-fill border border-accent-fill">
              <Clapperboard class="w-3 h-3" />
              {{ local.director }}
              <button @click="clearDirector" class="hover:opacity-70 transition" title="清除导演筛选">
                <X class="w-3 h-3" />
              </button>
            </span>
          </div>
        </div>

        <!-- Category Filter -->
        <div class="space-y-2">
          <label class="text-xs font-semibold text-fg-3 uppercase tracking-wider">影片分类 (Category)</label>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="cat in categories"
              :key="cat"
              @click="selectCategory(cat)"
              :class="[
                'px-2.5 py-1 rounded-lg text-xs font-medium border transition',
                local.category === cat
                  ? 'bg-accent-fill text-on-fill font-bold border-accent-fill'
                  : 'bg-surface border-line text-fg-3 hover:text-fg-2'
              ]"
            >
              <!-- The label is translated, the value is not: `cat` is the atomic English
                   term the query matches on, and it stays the identity of the chip. -->
              {{ trCategory(cat) }}
            </button>
          </div>
        </div>
        </template>
      </div>

      <!-- Bottom Actions -->
      <div class="pt-4 border-t border-line flex gap-2">
        <button
          @click="isPerformerTab ? emit('reset-performers') : isEpisodeTab ? emit('reset-episodes') : resetAll()"
          class="flex-1 py-2.5 rounded-xl bg-surface hover:bg-surface-2 border border-line text-xs font-semibold text-fg-3 hover:text-fg flex items-center justify-center gap-1.5 transition"
        >
          <RotateCcw class="w-3.5 h-3.5" />
          重置
        </button>
        <button
          @click="emit('close')"
          class="flex-1 py-2.5 rounded-xl bg-accent-fill hover:bg-accent text-on-fill text-xs font-bold transition shadow-lg shadow-accent-fill/20"
        >
          完成
        </button>
      </div>
    </aside>
  </div>
</template>
