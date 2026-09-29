<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { X, Film, Heart, Search, ArrowUpRight, Loader2 } from '@lucide/vue';
import type { Movie, DirectorWorks, FavoriteType } from '../types';
import MovieCard from './MovieCard.vue';
import { claimEscape } from '../utils/escape';
import { titlePrimary } from '../utils/bilingual';

/**
 * A director, as the library grid and the favorites page know them.
 *
 * `id` is only there when the card came from the director grid; a favorited director
 * carries nothing but a name, and the films are fetched by name either way.
 */
interface DirectorRef {
  id?: number;
  name: string;
}

const props = defineProps<{
  director: DirectorRef | null;
  /** The director's films, fetched by the parent; null while loading. */
  works: DirectorWorks | null;
  loading?: boolean;
  /** Synopsis language for the embedded movie cards. */
  lang?: 'zh' | 'en';
  /** Stacking order supplied by the parent; see MovieDetailModal. */
  zIndex?: number;
  /** Topmost view owns Escape — see MovieDetailModal. */
  isTop?: boolean;
  /** Whether this director is favorited. */
  isFavorite?: boolean;
  /** Favorited keys by type — used for the movie hearts in this modal. */
  favoriteKeys?: Partial<Record<FavoriteType, Set<string>>>;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select-movie', movie: Movie): void;
  // No `select-movie-id`: the studio modal needs it for episode rows linking to
  // their parent film by id, and a director's works hold no episodes.
  (e: 'toggle-favorite', directorName: string): void;
  /** The film hearts; the director itself has its own event above. */
  (e: 'toggle-entity-favorite', type: FavoriteType, key: string): void;
  /**
   * Open the 片商库 filtered to one studio. Distinct from `toggle-entity-favorite`:
   * this leaves the modal for another library rather than hearting something.
   */
  (e: 'open-studio', studioName: string): void;
  /** Filter movie library by this director */
  (e: 'filter-director', directorName: string): void;
}>();

const movies = computed(() => props.works?.movies || []);
/**
 * The payload counts its own list, so there is no caller-side fallback to make —
 * unlike a studio, the favorites page never knew a works count the modal lacks.
 */
const worksCount = computed(() => props.works?.movies_count ?? 0);

function isFav(type: FavoriteType, key: string | null | undefined): boolean {
  if (!key) return false;
  return Boolean(props.favoriteKeys?.[type]?.has(key));
}

// --- Filters and ordering -------------------------------------------------
//
// All of it is computed from the films already in hand: one director can have
// 817 of them (William Higgins), so filtering here costs a pass over a list the
// modal has already paid for, where a query per keystroke would not.

const keyword = ref('');
const yearFilter = ref<number | null>(null);
const sortBy = ref<'year_desc' | 'title_asc'>('year_desc');

/** Studios this director worked for, busiest first. Derived, never queried. */
const studioOptions = computed(() => {
  const counts = new Map<string, number>();
  for (const m of movies.value) {
    if (m.studio_name) counts.set(m.studio_name, (counts.get(m.studio_name) || 0) + 1);
  }
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
});

/**
 * How many chips the row shows before the 更多 toggle. Same reason as the performer
 * page: Chi Chi LaRue worked for 36 studios, and an unbounded wrap row pushes the
 * grid most of a screen down.
 */
const STUDIO_CHIP_LIMIT = 8;
const studiosExpanded = ref(false);

const shownStudioOptions = computed(() => {
  const all = studioOptions.value;
  if (studiosExpanded.value || all.length <= STUDIO_CHIP_LIMIT) return all;
  return all.slice(0, STUDIO_CHIP_LIMIT);
});

const collapsedStudioCount = computed(() =>
  Math.max(0, studioOptions.value.length - shownStudioOptions.value.length)
);

/** Years this director has films in, newest first. Nulls are left out. */
const years = computed(() => {
  const seen = new Set<number>();
  for (const m of movies.value) {
    if (m.release_year) seen.add(m.release_year);
  }
  return [...seen].sort((a, b) => b - a);
});

const visibleMovies = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  let list = movies.value;
  if (q) {
    // Both titles, because the card shows whichever the language setting picked —
    // searching only what is on screen would miss the other name.
    list = list.filter(m =>
      (m.title || '').toLowerCase().includes(q) ||
      (m.title_zh || '').toLowerCase().includes(q)
    );
  }
  // Equality on the filtered branch only: a film with no year must still show while
  // no year is selected, which is why this is not an inequality.
  if (yearFilter.value !== null) list = list.filter(m => m.release_year === yearFilter.value);
  if (sortBy.value === 'title_asc') {
    list = [...list].sort((a, b) =>
      titlePrimary(a, props.lang).localeCompare(titlePrimary(b, props.lang))
    );
  }
  return list;
});

const filterActive = computed(
  () => keyword.value.trim() !== '' || yearFilter.value !== null
);

/** A different director means different films, so the filters do not carry over. */
watch(() => props.director?.name, () => {
  keyword.value = '';
  yearFilter.value = null;
  sortBy.value = 'year_desc';
  studiosExpanded.value = false;
});

// A year that the new filmography does not contain would leave the list empty with
// nothing on screen explaining why; same guard as the performer modal's studio chips.
watch(years, (opts) => {
  if (yearFilter.value !== null && !opts.includes(yearFilter.value)) yearFilter.value = null;
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
    v-if="director"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8 bg-scrim/80 backdrop-blur-md animate-fade-in"
    :style="{ zIndex: zIndex ?? 50 }"
    @click.self="emit('close')"
  >
    <div
      class="relative w-full max-w-4xl max-h-[90vh] chrome-panel border border-line-strong/80 rounded-3xl shadow-2xl overflow-y-auto flex flex-col text-fg"
    >
      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-4 right-4 z-20 w-8 h-8 on-scrim rounded-full bg-scrim/60 hover:bg-scrim/90 border border-white/20 flex items-center justify-center text-fg-2 hover:text-fg transition"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Profile Header. No portrait exists in the library, so the tile is the
           name's initial, as on the director cards. -->
      <div class="p-6 md:p-8 bg-sunken border-b border-line flex items-center gap-6">
        <div class="w-20 h-20 md:w-24 md:h-24 rounded-2xl overflow-hidden shrink-0 shadow-lg shadow-accent-fill/10 ring-1 ring-line-strong/60">
          <div class="w-full h-full bg-gradient-to-tr from-accent-deep to-accent-2 flex items-center justify-center text-3xl font-black text-on-fill">
            {{ director.name.charAt(0).toUpperCase() }}
          </div>
        </div>
        <div class="min-w-0 flex-1">
          <div class="text-xs font-semibold text-accent uppercase tracking-wider">导演档案</div>
          <h1 class="text-2xl md:text-3xl font-extrabold text-fg truncate">{{ director.name }}</h1>
          <div class="text-xs text-fg-3 mt-1 flex items-center gap-3 flex-wrap">
            <span class="text-accent/80">{{ worksCount }} 部作品</span>
            <span v-if="studioOptions.length">{{ studioOptions.length }} 家合作片商</span>
          </div>
        </div>

        <div class="mr-10 shrink-0 self-start flex items-center gap-2">
          <button
            @click="emit('filter-director', director.name)"
            class="px-3 py-1.5 rounded-lg text-xs font-medium border border-line-strong bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg flex items-center gap-1.5 transition"
            title="在全量影片库中查看该导演所有影片"
          >
            <Film class="w-3.5 h-3.5" />
            <span>在片库查看</span>
          </button>

          <!-- Fav button leaves room for the absolutely-positioned close button -->
          <button
            @click="emit('toggle-favorite', director.name)"
            :class="[
              'px-3 py-1.5 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
              isFavorite
                ? 'bg-danger-fill/20 text-danger-soft border-danger-fill/40'
                : 'bg-surface-2 hover:bg-surface-3 border-line-strong text-fg-3 hover:text-danger'
            ]"
          >
            <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
            <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
          </button>
        </div>
      </div>

      <!-- Works Section -->
      <div class="p-6 md:p-8 space-y-6">
        <div class="flex items-center justify-between border-b border-line pb-4">
          <div class="flex items-center gap-2">
            <Film class="w-4 h-4 text-accent" />
            <span class="text-sm font-bold text-fg">完整电影 ({{ visibleMovies.length }})</span>
            <span v-if="filterActive" class="text-[11px] text-fg-4 font-mono">/ {{ movies.length }}</span>
          </div>
          <Loader2 v-if="loading" class="w-4 h-4 text-accent animate-spin shrink-0" />
        </div>

        <!-- Search this director's films, plus the year and the ordering. The films
             are all in hand already, so none of this costs a request. -->
        <div class="flex items-center gap-3 flex-wrap">
          <div class="relative flex-1 min-w-[180px]">
            <Search class="w-3.5 h-3.5 text-fg-4 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              v-model="keyword"
              type="text"
              placeholder="在这位导演的作品里搜索片名…"
              class="w-full pl-9 pr-3 py-2 rounded-xl bg-surface-2/70 border border-line-strong text-xs text-fg placeholder:text-fg-5 focus:outline-none focus:border-accent-fill/50 transition"
            />
          </div>

          <select
            v-model.number="yearFilter"
            class="px-3 py-2 rounded-xl bg-surface-2/70 border border-line-strong text-xs text-fg-2 focus:outline-none focus:border-accent-fill/50 transition"
          >
            <option :value="null">全部年份</option>
            <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
          </select>

          <div class="flex items-center gap-0.5 bg-surface border border-line rounded-xl p-0.5 text-xs">
            <button
              v-for="s in [
                { id: 'year_desc', label: '按年份' },
                { id: 'title_asc', label: '按片名' }
              ]"
              :key="s.id"
              @click="sortBy = s.id as 'year_desc' | 'title_asc'"
              :class="[
                'px-2 py-1 rounded-lg text-[11px] font-medium transition',
                sortBy === s.id ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-3 hover:text-fg-2'
              ]"
            >
              {{ s.label }}
            </button>
          </div>
        </div>

        <!--
          Collaborating studios. A click jumps to the 片商库 filtered to that studio
          rather than filtering here — the chip answers "who did they work for", and
          the studio library is where that question continues.
        -->
        <div v-if="studioOptions.length > 1" class="space-y-1.5">
          <div class="flex items-center justify-between gap-2">
            <span class="text-[11px] font-semibold text-fg-4 uppercase tracking-wider">合作片商</span>
            <button
              v-if="collapsedStudioCount > 0 || studiosExpanded"
              type="button"
              @click="studiosExpanded = !studiosExpanded"
              class="text-[11px] font-medium text-fg-4 hover:text-accent-soft transition shrink-0"
            >
              {{ studiosExpanded ? '收起' : `+${collapsedStudioCount} 更多` }}
            </button>
          </div>
          <div :class="['flex items-center gap-2 flex-wrap', studiosExpanded ? 'max-h-40 overflow-y-auto' : '']">
            <button
              v-for="opt in shownStudioOptions"
              :key="opt.name"
              @click="emit('open-studio', opt.name)"
              class="px-2.5 py-1 rounded-lg text-xs font-medium border transition bg-surface-2/70 hover:bg-surface-2 border-line-strong text-fg-2 hover:text-accent-soft inline-flex items-center gap-1"
              :title="`在片商库中查看「${opt.name}」`"
            >
              {{ opt.name }}
              <span class="text-fg-4">{{ opt.count }}</span>
              <ArrowUpRight class="w-3 h-3 text-fg-4" />
            </button>
          </div>
        </div>

        <!-- Films -->
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
        <div v-else-if="loading" class="text-center py-12 text-fg-4 text-xs">正在读取作品清单…</div>
        <div v-else-if="movies.length > 0" class="text-center py-12 text-fg-4 text-xs">没有符合筛选条件的作品</div>
        <!-- An unknown name is not a failure: a favorite saved before the director
             roster was parsed can hold a whole glued string. -->
        <div v-else class="text-center py-12 text-fg-4 text-xs">没找到这位导演的作品</div>
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
