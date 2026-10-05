<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import { X, Film, Layers, Heart, LayoutGrid, List, BookOpen, ExternalLink, Globe, Tag, ChevronDown, ChevronUp } from '@lucide/vue';
import type { Performer, Movie, FavoriteType } from '../types';
import MovieCard from './MovieCard.vue';
import EpisodeRow from './EpisodeRow.vue';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';
import { tr, trTattoo, trMeasure } from '../utils/glossary';
import { pluginsConfig, openPbcPerformer, openUrlExternal } from '../services/pluginManager';
import { currentLocale, t } from '../i18n';

const ResourceSearchWidget = defineAsyncComponent(() => import('./plugins/ResourceSearchWidget.vue'));

const props = defineProps<{
  performer: Performer | null;
  /** Synopsis language for the embedded movie cards. */
  lang?: 'zh' | 'en';
  /** Stacking order supplied by the parent; see MovieDetailModal. */
  zIndex?: number;
  /** Topmost view owns Escape — see MovieDetailModal. */
  isTop?: boolean;
  /** Whether this performer is favorited. */
  isFavorite?: boolean;
  /** Favorited keys by type — used for the studio and episode hearts in this modal. */
  favoriteKeys?: Partial<Record<FavoriteType, Set<string>>>;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select-movie', movie: Movie): void;
  (e: 'select-movie-id', movieId: number): void;
  (e: 'select-episode-id', episodeId: number): void;
  (e: 'toggle-favorite', performer: Performer): void;
  /** Studio / director / episode hearts; the performer has its own event above. */
  (e: 'toggle-entity-favorite', type: FavoriteType, key: string): void;
  (e: 'filter-studio', studioName: string): void;
}>();

const activeTab = ref<'movies' | 'episodes'>('movies');
const episodeLayout = ref<'grid' | 'list'>('grid');
const portraitError = ref(false);

function isFav(type: FavoriteType, key: string | null | undefined): boolean {
  if (!key) return false;
  return Boolean(props.favoriteKeys?.[type]?.has(key));
}

/** Attribute values, preferring the server's exploded list over raw markup. */
function attrValues(key: string, fallback: string | null | undefined): string[] {
  const exploded = props.performer?.attributes?.[key];
  if (exploded && exploded.length > 0) return exploded;
  if (!fallback) return [];
  return fallback.split(/<br\s*\/?>/i).map(s => s.trim()).filter(Boolean);
}

// ── PBC 维基档案字典与转换器 ──────────────────────────────────────────────────
const ASTRO_SYMBOLS: Record<string, string> = {
  Aries: '♈', Taurus: '♉', Gemini: '♊',
  Cancer: '♋', Leo: '♌', Virgo: '♍',
  Libra: '♎', Scorpio: '♏', Sagittarius: '♐',
  Capricorn: '♑', Aquarius: '♒', Pisces: '♓',
};
function trAstrology(sign?: string | null): string {
  if (!sign) return '';
  const sym = ASTRO_SYMBOLS[sign] || '';
  const key = `astro.${sign}`;
  const translated = t(key);
  const localizedName = (translated !== key) ? translated : sign;
  return `${localizedName} ${sym}`.trim();
}

function trEthnicity(eth?: string | null): string {
  if (!eth) return '';
  const cleanKey = eth.replace(/\s+/g, '');
  const key = `ethnicity.${cleanKey}`;
  const translated = t(key);
  if (translated !== key) return translated;
  return eth;
}

const pbcProfile = computed(() => props.performer?.pbc_profile);

const showOriginalBio = ref(false);
const isZh = computed(() => currentLocale.value.startsWith('zh'));

const displayedBio = computed(() => {
  const zh = pbcProfile.value?.bio_zh?.trim();
  const en = pbcProfile.value?.bio?.trim();
  if (!zh) return en || '';
  if (!en) return zh || '';
  if (isZh.value) {
    return showOriginalBio.value ? en : zh;
  } else {
    return showOriginalBio.value ? zh : en;
  }
});

const pbcCareerStatus = computed(() => {
  const status = pbcProfile.value?.career_status?.trim();
  if (!status) return null;
  const isAct = status.toLowerCase().includes('active');
  return {
    isActive: isAct,
    text: t(isAct ? 'performer.active' : 'performer.retired'),
  };
});

const pbcSocialLinks = computed<Record<string, string>>(() => {
  const prof = pbcProfile.value;
  if (!prof) return {};
  if (prof.social_links) return prof.social_links;
  if (prof.social_links_json) {
    try {
      return typeof prof.social_links_json === 'string' ? JSON.parse(prof.social_links_json) : prof.social_links_json;
    } catch {
      return {};
    }
  }
  return {};
});

const pbcExternalIds = computed<Record<string, string>>(() => {
  const prof = pbcProfile.value;
  if (!prof) return {};
  if (prof.external_ids) return prof.external_ids;
  if (prof.external_ids_json) {
    try {
      return typeof prof.external_ids_json === 'string' ? JSON.parse(prof.external_ids_json) : prof.external_ids_json;
    } catch {
      return {};
    }
  }
  return {};
});

interface Spec {
  key: string;
  label: string;
  /** Rendered in amber rather than zinc. */
  accent: boolean;
  /** Translate the value's units as well as the value — see trMeasure. */
  measure?: boolean;
  values: string[];
}

const SPECS = computed<Spec[]>(() => {
  const p = props.performer;
  if (!p) return [];
  const prof = p.pbc_profile;
  let originStr: string | null = null;
  if (isZh.value) {
    const parts: string[] = [];
    const nat = prof?.nationality_zh?.trim() || prof?.nationality?.trim();
    const country = prof?.country_zh?.trim() || prof?.country?.trim();
    const place = prof?.birth_place_zh?.trim() || prof?.birth_place?.trim();
    if (nat) parts.push(nat);
    if (country && !parts.includes(country) && country !== nat) parts.push(country);
    if (place && !parts.includes(place) && place !== country && place !== nat) parts.push(place);
    originStr = parts.length > 0 ? parts.join(' · ') : null;
  } else {
    const nat = prof?.nationality;
    const place = prof?.country || prof?.birth_place;
    const originParts = [nat, place].filter(Boolean);
    originStr = originParts.length > 0 ? originParts.join(' · ') : null;
  }

  return [
    { key: 'birthDate', label: t('performer.birthDate'), accent: false, values: prof?.birth_date ? [prof.age ? `${prof.birth_date} (${t('performer.ageYears', { age: prof.age })})` : prof.birth_date] : [] },
    { key: 'astrology', label: t('performer.astrology'), accent: false, values: prof?.astrology ? [trAstrology(prof.astrology)] : [] },
    { key: 'origin', label: t('performer.origin'), accent: false, values: originStr ? [originStr] : [] },
    { key: 'ethnicity', label: t('performer.ethnicity'), accent: false, values: prof?.ethnicity ? [trEthnicity(prof.ethnicity)] : [] },
    { key: 'height', label: t('performer.height'), accent: false, measure: true, values: attrValues('height', p.height || prof?.height) },
    { key: 'weight', label: t('performer.weight'), accent: false, measure: true, values: attrValues('weight', p.weight || prof?.weight) },
    { key: 'bodyType', label: t('performer.bodyType'), accent: false, values: attrValues('bodyType', p.build || prof?.build) },
    { key: 'dickSize', label: t('performer.dickSize'), accent: true, measure: true, values: attrValues('dickSize', p.dick_size || prof?.penis_size) },
    { key: 'skin', label: t('performer.skin'), accent: false, values: attrValues('skin', p.skin || prof?.skin) },
    { key: 'hair', label: t('performer.hair'), accent: false, values: attrValues('hair', p.hair || prof?.hair) },
    { key: 'eyes', label: t('performer.eyes'), accent: false, values: attrValues('eyes', p.eyes || prof?.eyes) },
    { key: 'bodyHair', label: t('performer.bodyHair'), accent: false, values: attrValues('bodyHair', p.body_hair || prof?.body_hair) },
    { key: 'facialHair', label: t('performer.facialHair'), accent: false, values: attrValues('facialHair', p.facial_hair || prof?.facial_hair) },
    { key: 'foreskin', label: t('performer.foreskin'), accent: false, values: attrValues('foreskin', p.foreskin || prof?.foreskin) },
  ].filter(s => s.values.length > 0);
});

const tattoos = computed(() => attrValues('tattoos', props.performer?.tattoos));

/**
 * Deduplicated aliases across credit records and PBC wiki profiles.
 */
const isAliasesExpanded = ref(false);
const ALIASES_COLLAPSE_THRESHOLD = 8;

const allAliases = computed<string[]>(() => {
  const p = props.performer;
  if (!p) return [];

  const rawList: string[] = [];

  if (p.aliases && Array.isArray(p.aliases)) {
    rawList.push(...p.aliases);
  }

  if (pbcProfile.value?.aliases) {
    rawList.push(...pbcProfile.value.aliases.split(','));
  }

  const seen = new Set<string>();
  const currentName = p.name.trim().toLowerCase();
  const result: string[] = [];

  for (const item of rawList) {
    const trimmed = item.trim();
    if (!trimmed) continue;
    const lower = trimmed.toLowerCase();
    if (lower === currentName) continue;
    if (!seen.has(lower)) {
      seen.add(lower);
      result.push(trimmed);
    }
  }

  return result;
});

const visibleAliases = computed(() => {
  if (isAliasesExpanded.value || allAliases.value.length <= ALIASES_COLLAPSE_THRESHOLD) {
    return allAliases.value;
  }
  return allAliases.value.slice(0, ALIASES_COLLAPSE_THRESHOLD);
});

/**
 * Studio filter for the works below.
 *
 * A performer's filmography can span a dozen studios, so the list is narrowed by a
 * chip row rather than paged. The same filter drives both tabs — episodes carry a
 * studio_name too — but it is cleared whenever the selected studio is not present
 * in the tab being shown, otherwise switching tabs (or performers) would land on a
 * silently empty list.
 */
const studioFilter = ref('');

const studioOptions = computed(() => {
  const counts = new Map<string, number>();
  const add = (name?: string | null) => {
    if (name) counts.set(name, (counts.get(name) || 0) + 1);
  };
  if (activeTab.value === 'movies') {
    (props.performer?.movies || []).forEach(m => add(m.studio_name));
  } else {
    (props.performer?.episodes || []).forEach(e => add(e.studio_name));
  }
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
});

/**
 * How many chips the collapsed row shows. A filmography can span a dozen-plus
 * studios, and an unbounded `flex-wrap` row pushed the grid most of a screen
 * down — measured against the library, the busiest performers cross twenty.
 */
const STUDIO_CHIP_LIMIT = 8;
const studiosExpanded = ref(true);

/**
 * The chips to render. Collapsed to the busiest `STUDIO_CHIP_LIMIT`, except that
 * the active filter is always kept on screen — it can sit at position 20, and a
 * highlighted chip you cannot see or click off is worse than a long row.
 */
const shownStudioOptions = computed(() => {
  const all = studioOptions.value;
  if (studiosExpanded.value || all.length <= STUDIO_CHIP_LIMIT) return all;
  const head = all.slice(0, STUDIO_CHIP_LIMIT);
  const active = all.find(o => o.name === studioFilter.value);
  if (active && !head.includes(active)) {
    return [...head.slice(0, STUDIO_CHIP_LIMIT - 1), active];
  }
  return head;
});

/** What the "更多" button is hiding. Zero once expanded. */
const collapsedStudioCount = computed(() =>
  Math.max(0, studioOptions.value.length - shownStudioOptions.value.length)
);

watch(studioOptions, (opts) => {
  if (studioFilter.value && !opts.some(o => o.name === studioFilter.value)) {
    studioFilter.value = '';
  }
});

watch(() => props.performer?.id, () => {
  portraitError.value = false;
  studioFilter.value = '';
  studiosExpanded.value = false;
  isAliasesExpanded.value = false;
  showOriginalBio.value = false;
});

const visibleMovies = computed(() => {
  const list = props.performer?.movies || [];
  return studioFilter.value ? list.filter(m => m.studio_name === studioFilter.value) : list;
});

const visibleEpisodes = computed(() => {
  const list = props.performer?.episodes || [];
  return studioFilter.value ? list.filter(e => e.studio_name === studioFilter.value) : list;
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
    v-if="performer"
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

      <!-- Profile Header. The attribute profile lives here rather than in a section of
           its own below: it is a dozen short label/value pairs, and as a full-width
           block of boxes it pushed the filmography below the fold for no gain. -->
      <!-- Profile Header -->
      <div class="p-6 md:p-8 bg-sunken border-b border-line space-y-4">
        <div class="flex items-start justify-between gap-5 flex-wrap">
          <div class="flex items-start gap-5 min-w-0 flex-1">
            <!-- Portrait (issue #6), falls back to PBC portrait or the letter tile when unscraped. -->
            <div class="w-24 h-24 md:w-28 md:h-28 rounded-2xl overflow-hidden shrink-0 shadow-lg shadow-accent-fill/10 ring-1 ring-line-strong/60">
              <img
                v-if="(performer.image_url || pbcProfile?.image_url) && !portraitError"
                :src="getImageUrl(performer.image_url || pbcProfile?.image_url)"
                :alt="performer.name"
                referrerpolicy="no-referrer"
                data-zoom-click
                class="w-full h-full object-cover object-top"
                @error="portraitError = true"
              />
              <div
                v-else
                class="w-full h-full bg-gradient-to-tr from-accent-deep to-accent-2 flex items-center justify-center text-3xl font-black text-on-fill"
              >
                {{ performer.name.charAt(0).toUpperCase() }}
              </div>
            </div>

            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="text-xs font-semibold text-accent uppercase tracking-wider">{{ t('performer.profile') }}</span>
                <span
                  v-if="pbcCareerStatus"
                  :class="[
                    'px-2 py-0.5 rounded-full text-[10px] font-bold border flex items-center gap-1',
                    pbcCareerStatus.isActive
                      ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
                      : 'bg-zinc-500/15 text-zinc-400 border-zinc-500/30'
                  ]"
                >
                  <span class="w-1.5 h-1.5 rounded-full" :class="pbcCareerStatus.isActive ? 'bg-emerald-400 animate-pulse' : 'bg-zinc-400'"></span>
                  {{ pbcCareerStatus.text }}
                </span>
              </div>
              <h1 class="text-2xl md:text-3xl font-extrabold text-fg break-words mt-0.5 leading-tight" :title="performer.name">
                {{ performer.name }}
              </h1>
              <div class="text-xs text-fg-3 mt-1 flex items-center gap-3 flex-wrap">
                <span>ID: #{{ performer.id }}</span>
                <span
                  v-if="performer.works_count ?? performer.movies_count"
                  class="text-accent/80 font-medium"
                >{{ performer.works_count ?? performer.movies_count }} {{ t('common.works') }}</span>
                <span v-if="pbcProfile?.birth_name && pbcProfile.birth_name !== performer.name" class="text-fg-4">
                  {{ t('performer.birthName') }} {{ pbcProfile.birth_name }}
                </span>
                <span v-if="pbcProfile?.career_start" class="text-fg-4">
                  {{ t('performer.careerStart', { year: pbcProfile.career_start }) }}
                </span>
                <span v-if="!performer.image_url && !pbcProfile?.image_url" class="text-fg-5">{{ t('performer.noPhoto') }}</span>
              </div>
            </div>
          </div>

          <!-- Action buttons (BT Search, BFTV, PBC, SmutJunkies, Google Search, Fav) - Dedicated right-side single column -->
          <div class="mr-10 shrink-0 self-start flex flex-col gap-1.5 w-36 sm:w-40">
            <template v-if="pluginsConfig.resourceSearchEnabled">
              <ResourceSearchWidget
                type="performer"
                :title="performer.name"
                :bftvUrl="performer.bftv_url"
                :pbcUrl="performer.pbc_url || pbcProfile?.pbc_url"
                :sjUrl="performer.sj_url"
                wrapper-class="flex flex-col gap-1.5 w-full"
                button-class="w-full justify-center"
              />
            </template>

            <button
              @click="emit('toggle-favorite', performer)"
              :class="[
                'py-1.5 px-2.5 rounded-xl text-xs font-medium border flex items-center justify-center gap-1.5 transition cursor-pointer whitespace-nowrap w-full',
                isFavorite
                  ? 'bg-danger-fill/20 text-danger-soft border-danger-fill/40'
                  : 'bg-surface-2/60 hover:bg-surface-3 border-line text-fg-3 hover:text-danger'
              ]"
            >
              <Heart class="w-3.5 h-3.5 shrink-0" :fill="isFavorite ? 'currentColor' : 'none'" />
              <span class="truncate">{{ isFavorite ? t('movie.favorited') : t('movie.favorite') }}</span>
            </button>
          </div>
        </div>

        <!-- Aliases / AKA Badges (Full width, expandable) -->
        <div
          v-if="allAliases.length > 0"
          class="w-full bg-surface/50 border border-line rounded-xl p-3 space-y-2"
        >
          <div class="flex items-center justify-between gap-2 flex-wrap">
            <div class="flex items-center gap-2">
              <Tag class="w-3.5 h-3.5 text-accent/80" />
              <span class="text-xs font-semibold text-fg-3">{{ t('performer.aliases') }}</span>
              <span class="px-1.5 py-0.5 rounded-full text-[10px] font-medium bg-surface-2 text-fg-4 border border-line">
                {{ t('series.worksTotal', { count: allAliases.length }) }}
              </span>
            </div>

            <!-- Expand / Collapse button if > ALIASES_COLLAPSE_THRESHOLD -->
            <button
              v-if="allAliases.length > ALIASES_COLLAPSE_THRESHOLD"
              @click="isAliasesExpanded = !isAliasesExpanded"
              class="inline-flex items-center gap-1 text-[11px] font-medium text-accent hover:text-accent-hover transition cursor-pointer select-none"
            >
              <span>{{ isAliasesExpanded ? t('performer.collapseAliases') : t('performer.expandAliases', { count: allAliases.length }) }}</span>
              <component :is="isAliasesExpanded ? ChevronUp : ChevronDown" class="w-3 h-3" />
            </button>
          </div>

          <div class="flex flex-wrap gap-1.5 items-center">
            <span
              v-for="alias in visibleAliases"
              :key="alias"
              class="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-medium bg-surface-2/80 hover:bg-surface-3 text-fg-2 border border-line-strong/60 transition select-text"
              :title="`${t('performer.aliases')}: ${alias}`"
            >
              {{ alias }}
            </span>
            <button
              v-if="!isAliasesExpanded && allAliases.length > ALIASES_COLLAPSE_THRESHOLD"
              @click="isAliasesExpanded = true"
              class="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold bg-accent-fill/10 hover:bg-accent-fill/20 text-accent border border-accent/25 transition cursor-pointer"
            >
              +{{ allAliases.length - ALIASES_COLLAPSE_THRESHOLD }} {{ t('common.loadMore') }}
            </button>
          </div>
        </div>

        <!-- Performer Notes (Full width) -->
        <div
          v-if="performer.notes"
          class="w-full text-xs text-fg-3/90 bg-surface/40 border border-line/60 rounded-xl px-3.5 py-2 leading-relaxed italic"
        >
          <span class="text-fg-4 not-italic font-medium mr-1.5">{{ t('performer.notes') }}</span>
          {{ performer.notes }}
        </div>

        <!-- Attribute profile as chips -->
        <div
          v-if="SPECS.length > 0"
          class="flex flex-wrap content-start gap-1.5 pt-1"
          :aria-label="t('performer.attributes')"
        >
          <span
            v-for="spec in SPECS"
            :key="spec.key"
            class="inline-flex items-baseline gap-1.5 max-w-full px-2 py-0.5 rounded-lg bg-surface/80 border border-line"
          >
            <span class="text-[11px] text-fg-4 shrink-0">{{ spec.label }}</span>
            <span
              v-for="v in spec.values"
              :key="v"
              :title="v"
              :class="[
                'text-[11px] font-semibold break-words',
                spec.accent ? 'text-accent' : 'text-fg-2'
              ]"
            >
              {{ spec.measure ? trMeasure(v) : tr(v) }}
            </span>
          </span>

          <span
            v-if="tattoos.length > 0"
            class="inline-flex items-baseline gap-1.5 basis-full px-2 py-0.5 rounded-lg bg-surface/80 border border-line"
          >
            <span class="text-[11px] text-fg-4 shrink-0">{{ t('performer.tattoos') }}</span>
            <span class="text-[11px] font-semibold text-fg-2 break-words">
              {{ tattoos.map(trTattoo).join('、') }}
            </span>
          </span>
        </div>

        <div v-else class="text-xs text-fg-4 italic pt-1">
          {{ t('common.noData') }}
        </div>

        <!-- Wikipedia Biography Card -->
        <div v-if="pbcProfile?.bio || pbcProfile?.bio_zh" class="mt-2 p-3.5 rounded-2xl bg-surface/60 border border-line/70 text-xs text-fg-3 leading-relaxed">
          <div class="flex items-center justify-between gap-2 mb-1.5">
            <div class="flex items-center gap-1.5 text-xs font-semibold text-purple-400">
              <BookOpen class="w-3.5 h-3.5" />
              <span>{{ t('performer.pbcBio') }}</span>
              <span v-if="pbcProfile?.bio_zh?.trim() && (isZh ? !showOriginalBio : showOriginalBio)" class="text-[9px] font-bold text-success-text px-1 py-0.2 rounded bg-success-fill/15 border border-success-fill/30 ml-1">
                {{ t('common.translated') }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <button
                v-if="pbcProfile?.bio && pbcProfile?.bio_zh"
                @click="showOriginalBio = !showOriginalBio"
                class="text-[10px] text-fg-4 hover:text-accent flex items-center gap-1 transition cursor-pointer"
                :title="isZh ? (showOriginalBio ? t('movie.showChinese') : t('movie.showOriginal')) : (showOriginalBio ? t('movie.showOriginal') : t('movie.showChinese'))"
              >
                <span>{{ isZh ? (showOriginalBio ? t('movie.showChinese') : t('movie.showOriginal')) : (showOriginalBio ? t('movie.showOriginal') : t('movie.showChinese')) }}</span>
              </button>
              <button
                v-if="performer.pbc_url || pbcProfile?.pbc_url"
                @click="openPbcPerformer(performer.pbc_url || pbcProfile!.pbc_url)"
                class="text-[10px] text-purple-400 hover:text-purple-300 flex items-center gap-1 transition cursor-pointer"
                :title="t('performer.viewWiki')"
              >
                <span>{{ t('performer.fullArticle') }}</span>
                <ExternalLink class="w-2.5 h-2.5" />
              </button>
            </div>
          </div>
          <p class="text-fg-3/90 text-[11px] leading-relaxed">
            {{ displayedBio }}
          </p>
        </div>

        <!-- Social and External Database Links -->
        <div v-if="Object.keys(pbcSocialLinks).length > 0 || Object.keys(pbcExternalIds).length > 0" class="flex flex-wrap items-center gap-2 pt-1">
          <span class="text-[11px] text-fg-4 shrink-0 flex items-center gap-1">
            <Globe class="w-3 h-3 text-fg-4" />
            <span>{{ t('performer.externalLinks') }}</span>
          </span>
          <a
            v-for="(url, platform) in pbcSocialLinks"
            :key="platform"
            :href="url"
            @click.prevent="openUrlExternal(url)"
            target="_blank"
            rel="noreferrer"
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg bg-surface/80 hover:bg-surface-2 border border-line text-[10px] font-medium text-fg-3 hover:text-accent transition cursor-pointer"
          >
            <ExternalLink class="w-2.5 h-2.5 opacity-60" />
            <span>{{ platform.toUpperCase() }}</span>
          </a>
          <a
            v-if="pbcExternalIds.iafd_id"
            :href="`https://www.iafd.com/person.rme/perfid=${pbcExternalIds.iafd_id}/gender=m`"
            @click.prevent="openUrlExternal(`https://www.iafd.com/person.rme/perfid=${pbcExternalIds.iafd_id}/gender=m`)"
            target="_blank"
            rel="noreferrer"
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg bg-surface/80 hover:bg-surface-2 border border-line text-[10px] font-medium text-fg-3 hover:text-accent transition cursor-pointer"
            :title="t('performer.viewIafd')"
          >
            <ExternalLink class="w-2.5 h-2.5 opacity-60" />
            <span>IAFD</span>
          </a>
          <a
            v-if="pbcExternalIds.imdb_id"
            :href="`https://www.imdb.com/name/nm${pbcExternalIds.imdb_id}`"
            @click.prevent="openUrlExternal(`https://www.imdb.com/name/nm${pbcExternalIds.imdb_id}`)"
            target="_blank"
            rel="noreferrer"
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg bg-surface/80 hover:bg-surface-2 border border-line text-[10px] font-medium text-fg-3 hover:text-accent transition cursor-pointer"
            :title="t('performer.viewImdb')"
          >
            <ExternalLink class="w-2.5 h-2.5 opacity-60" />
            <span>IMDb</span>
          </a>
        </div>
      </div>

      <!-- Works Section (Divided into Movies vs Episodes) -->
      <div class="p-6 md:p-8 space-y-6">
        <!-- Dual Tab Switcher -->
        <div class="flex items-center justify-between border-b border-line pb-4">
          <div class="flex items-center gap-2">
            <button
              @click="activeTab = 'movies'"
              :class="[
                'flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition',
                activeTab === 'movies'
                  ? 'bg-accent-fill text-on-fill shadow-lg shadow-accent-fill/20'
                  : 'bg-surface-2/70 text-fg-3 hover:text-fg-2 hover:bg-surface-2'
              ]"
            >
              <Film class="w-3.5 h-3.5" />
              <span>{{ t('studio.movies') }} ({{ performer.movies ? performer.movies.length : 0 }})</span>
            </button>

            <button
              @click="activeTab = 'episodes'"
              :class="[
                'flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition',
                activeTab === 'episodes'
                  ? 'bg-accent-fill text-on-fill shadow-lg shadow-accent-fill/20'
                  : 'bg-surface-2/70 text-fg-3 hover:text-fg-2 hover:bg-surface-2'
              ]"
            >
              <Layers class="w-3.5 h-3.5" />
              <span>{{ t('studio.episodes') }} ({{ performer.episodes ? performer.episodes.length : (performer.episodes_count || 0) }})</span>
            </button>
          </div>
        </div>

        <!--
          Studio filter. Hidden for a single-studio performer: a filter row with one
          choice is just noise. Clicking the active chip clears it. Collapsed to the
          busiest few, with the rest behind the toggle on the right.
        -->
        <div v-if="studioOptions.length > 1" class="space-y-1.5">
          <div class="flex items-center justify-between gap-2">
            <span class="text-[11px] font-semibold text-fg-4 uppercase tracking-wider">{{ t('common.studio') }}</span>
            <button
              v-if="collapsedStudioCount > 0 || studiosExpanded"
              type="button"
              @click="studiosExpanded = !studiosExpanded"
              class="text-[11px] font-medium text-fg-4 hover:text-accent-soft transition shrink-0 cursor-pointer"
            >
              {{ studiosExpanded ? t('common.collapse') : `+${studioOptions.length - STUDIO_CHIP_LIMIT} ${t('common.expandAll')}` }}
            </button>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <button
              v-for="opt in shownStudioOptions"
              :key="opt.name"
              @click="studioFilter = studioFilter === opt.name ? '' : opt.name"
              :class="[
                'px-2.5 py-1 rounded-lg text-xs font-medium border transition',
                studioFilter === opt.name
                  ? 'bg-accent-fill text-on-fill border-accent-fill shadow'
                  : 'bg-surface-2/70 hover:bg-surface-2 border-line-strong text-fg-2 hover:text-accent-soft'
              ]"
            >
              {{ opt.name }}
              <span :class="studioFilter === opt.name ? 'text-on-fill/60' : 'text-fg-4'">{{ opt.count }}</span>
            </button>
          </div>
        </div>

        <!-- 1. Feature Movies Tab -->
        <div v-if="activeTab === 'movies'">
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
          <div v-else class="text-center py-12 text-fg-4 text-xs">
            {{ studioFilter ? `${t('common.noData')}` : t('studio.noMovies') }}
          </div>
        </div>

        <!-- 2. Episodes & Scenes Tab -->
        <div v-else-if="activeTab === 'episodes'" class="space-y-3">
          <div class="flex items-center justify-between pb-1 flex-wrap gap-2">
            <span class="text-xs text-fg-4">{{ visibleEpisodes.length }} {{ t('common.episodes') }}</span>
            <!-- Layout Switcher: Grid vs List -->
            <div class="flex items-center bg-surface-2/80 rounded-xl p-0.5 border border-line text-xs font-semibold">
              <button
                type="button"
                @click="episodeLayout = 'grid'"
                :class="[
                  'px-2.5 py-1 rounded-lg text-xs font-medium transition flex items-center gap-1.5 cursor-pointer',
                  episodeLayout === 'grid'
                    ? 'bg-accent-fill text-on-fill shadow-xs'
                    : 'text-fg-4 hover:text-fg hover:bg-surface-3/50'
                ]"
                :title="t('view.grid')"
              >
                <LayoutGrid class="w-3.5 h-3.5" />
                <span>{{ t('view.grid') }}</span>
              </button>
              <button
                type="button"
                @click="episodeLayout = 'list'"
                :class="[
                  'px-2.5 py-1 rounded-lg text-xs font-medium transition flex items-center gap-1.5 cursor-pointer',
                  episodeLayout === 'list'
                    ? 'bg-accent-fill text-on-fill shadow-xs'
                    : 'text-fg-4 hover:text-fg hover:bg-surface-3/50'
                ]"
                :title="t('view.list')"
              >
                <List class="w-3.5 h-3.5" />
                <span>{{ t('view.list') }}</span>
              </button>
            </div>
          </div>

          <div v-if="visibleEpisodes.length > 0">
            <!-- Grid Layout (展示更多条目) -->
            <div v-if="episodeLayout === 'grid'" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3.5">
              <div
                v-for="ep in visibleEpisodes"
                :key="ep.id"
                @click="emit('select-episode-id', ep.id)"
                class="group relative flex flex-col rounded-2xl bg-surface/60 border border-line/80 hover:border-accent-fill/50 hover:shadow-xl hover:shadow-accent-fill/10 transition-all duration-300 overflow-hidden cursor-pointer select-none"
              >
                <div class="relative w-full aspect-video bg-sunken overflow-hidden">
                  <img
                    v-if="ep.thumbnail_url"
                    :src="getImageUrl(ep.thumbnail_url)"
                    :alt="ep.title"
                    loading="lazy"
                    referrerpolicy="no-referrer"
                    class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                  />
                  <div v-else class="w-full h-full flex items-center justify-center text-fg-5">
                    <Layers class="w-8 h-8 stroke-1" />
                  </div>

                  <!-- Favorite Heart Badge -->
                  <button
                    @click.stop="emit('toggle-entity-favorite', 'episode', String(ep.id))"
                    class="absolute top-2 right-2 p-1.5 rounded-full backdrop-blur-md bg-black/40 hover:bg-black/70 text-fg transition cursor-pointer"
                    :title="isFav('episode', String(ep.id)) ? t('episode.unfavorite') : t('episode.favorite')"
                  >
                    <Heart
                      class="w-3.5 h-3.5 transition"
                      :class="isFav('episode', String(ep.id)) ? 'text-danger fill-danger' : 'text-white/80'"
                    />
                  </button>
                </div>

                <div class="p-3 flex-1 flex flex-col justify-between gap-1.5">
                  <div>
                    <div class="flex items-start justify-between gap-1">
                      <h4 class="text-xs font-bold text-fg group-hover:text-accent transition line-clamp-1" :title="ep.title">
                        {{ ep.title }}
                      </h4>
                      <span v-if="ep.description_zh?.trim()" class="text-[9px] font-bold text-success flex items-center shrink-0">
                        {{ currentLocale.startsWith('zh') ? '中' : 'ZH' }}
                      </span>
                    </div>
                    <div
                      v-if="ep.movie_title"
                      @click.stop="ep.movie_id && emit('select-movie-id', ep.movie_id)"
                      class="text-[11px] text-fg-4 mt-0.5 truncate hover:text-accent hover:underline cursor-pointer"
                      :title="ep.movie_title"
                    >
                      {{ t('common.source') }}: {{ ep.movie_title }}
                    </div>
                  </div>
                  <div class="flex items-center justify-between text-[10px] text-fg-4 pt-1.5 border-t border-line/40">
                    <span class="truncate max-w-[120px] font-medium">{{ ep.studio_name || t('common.studio') }}</span>
                    <span v-if="ep.release_year">{{ ep.release_year }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- List Layout -->
            <div v-else class="grid grid-cols-1 gap-3">
              <EpisodeRow
                v-for="ep in visibleEpisodes"
                :key="ep.id"
                :episode="ep"
                :lang="lang"
                zoom-on-click
                show-studio
                :is-favorite="isFav('episode', String(ep.id))"
                @select-movie-id="emit('select-movie-id', $event)"
                @select-episode-id="emit('select-episode-id', $event)"
                @toggle-favorite="emit('toggle-entity-favorite', 'episode', String(ep.id))"
                @filter-studio="emit('filter-studio', $event)"
              />
            </div>
          </div>
          <div v-else class="text-center py-12 text-fg-4 text-xs">
            {{ studioFilter ? `${t('common.noData')}` : t('studio.noEpisodes') }}
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
