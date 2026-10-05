<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { t, currentLocale } from '../i18n';
import * as echarts from 'echarts';
import {
  Building2, Network, Search, Layers, Clapperboard,
  ChevronDown, ChevronRight, ExternalLink, RotateCcw,
  Info, LayoutGrid
} from '@lucide/vue';
import { getImageUrl } from '../utils/image';

const emit = defineEmits<{
  (e: 'select-studio', name: string): void;
}>();

interface StudioNode {
  id: string;
  name: string;
  name_zh?: string | null;
  type: string;
  group_id: string;
  group_name: string;
  group_name_zh: string;
  works_count: number;
  episodes_count: number;
  logo_url?: string | null;
  banner_url?: string | null;
  description_zh?: string | null;
  badge?: string;
  relation?: string;
}

interface StudioEdge {
  source: string;
  target: string;
  relation: string;
  relation_label: string;
  weight: number;
}

interface StudioGroup {
  id: string;
  name: string;
  name_zh: string;
  type: 'conglomerate' | 'network' | 'indie_ecosystem';
  headquarters?: string;
  founded_year?: number;
  description_zh: string;
  total_works: number;
  total_episodes: number;
  studios: StudioNode[];
}

interface GenealogyData {
  stats: {
    total_groups: number;
    total_studios: number;
    total_links: number;
    total_episodes_linked: number;
  };
  groups: StudioGroup[];
  nodes: StudioNode[];
  edges: StudioEdge[];
}

const loading = ref(true);
const error = ref<string | null>(null);
const genealogyData = ref<GenealogyData | null>(null);

type ViewMode = 'wiki' | 'graph';
const viewMode = ref<ViewMode>('wiki');

const searchQuery = ref('');
const selectedGroupFilter = ref<string>('all');
const collapsedGroups = ref<Set<string>>(new Set());

// Graph chart ref
const chartContainerRef = ref<HTMLDivElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
const selectedGraphNode = ref<StudioNode | null>(null);

// Group type colors for visually distinguishing categories
const GROUP_COLORS: Record<string, string> = {
  aylo_mindgeek: '#f59e0b',
  falcon_studios_group: '#3b82f6',
  titan_media_group: '#10b981',
  the_bro_network: '#8b5cf6',
  helix_studios_network: '#ec4899',
  staxus_eurocreme: '#06b6d4',
  say_uncle_network: '#f97316',
  gayroom_platform: '#6366f1',
  colt_studio_group: '#e11d48',
  kristen_bjorn_empire: '#14b8a6',
  carnal_plus: '#d946ef',
  kink_com_network: '#84cc16',
  str8hell_william_higgins: '#eab308',
  raw_fuck_club_network: '#ef4444',
  gamma_entertainment: '#a855f7',
  prowler_millivres_group: '#0284c7',
  treasure_island_media_group: '#dc2626',
  channel_1_releasing: '#ea580c',
  golden_age_classics: '#ca8a04',
  independent_giants: '#10b981',
  gaylife_youth_network: '#06b6d4',
  flava_works_group: '#8b5cf6',
  amateur_straight_guys: '#64748b',
  catalina_pacific: '#f59e0b',
  cobra_studios_group: '#475569',
  caballero_vca: '#b45309',
};

const DEFAULT_COLOR = '#64748b';

function getGroupColor(groupId: string): string {
  return GROUP_COLORS[groupId] || DEFAULT_COLOR;
}

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const res = await fetch('/data/studio_genealogy.json');
    if (!res.ok) throw new Error(`HTTP error ${res.status}`);
    const json: GenealogyData = await res.json();
    genealogyData.value = json;
  } catch (err: any) {
    console.error('加载厂牌谱系数据失败:', err);
    error.value = err?.message || t('genealogy.loadingGenealogy');
  } finally {
    loading.value = false;
    if (viewMode.value === 'graph') {
      await nextTick();
      initGraphChart();
    }
  }
}

function toggleGroupCollapse(groupId: string) {
  if (collapsedGroups.value.has(groupId)) {
    collapsedGroups.value.delete(groupId);
  } else {
    collapsedGroups.value.add(groupId);
  }
}

function expandAllGroups() {
  collapsedGroups.value.clear();
}

function collapseAllGroups() {
  if (!genealogyData.value) return;
  genealogyData.value.groups.forEach(g => collapsedGroups.value.add(g.id));
}

const filteredGroups = computed(() => {
  if (!genealogyData.value) return [];
  const q = searchQuery.value.trim().toLowerCase();
  const filterType = selectedGroupFilter.value;

  return genealogyData.value.groups.map(group => {
    // 过滤类型
    if (filterType !== 'all' && group.type !== filterType) {
      return null;
    }

    // 搜索匹配
    if (!q) return group;

    const groupMatches = group.name.toLowerCase().includes(q) ||
      group.name_zh.toLowerCase().includes(q) ||
      group.description_zh.toLowerCase().includes(q);

    const matchingStudios = group.studios.filter(s => {
      return s.name.toLowerCase().includes(q) ||
        (s.name_zh && s.name_zh.toLowerCase().includes(q)) ||
        (s.badge && s.badge.toLowerCase().includes(q));
    });

    if (groupMatches || matchingStudios.length > 0) {
      return {
        ...group,
        studios: groupMatches ? group.studios : matchingStudios
      };
    }
    return null;
  }).filter((g): g is StudioGroup => g !== null);
});

function initGraphChart() {
  if (!chartContainerRef.value || !genealogyData.value) return;
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }

  chartInstance = echarts.init(chartContainerRef.value);

  const rawNodes = genealogyData.value.nodes;
  const rawEdges = genealogyData.value.edges;

  // Build categories by group
  const groupMap = new Map<string, string>();
  genealogyData.value.groups.forEach(g => groupMap.set(g.id, currentLocale.value.startsWith('zh') ? g.name_zh : g.name));

  const categories = Array.from(groupMap.entries()).map(([id, name]) => ({
    name,
    itemStyle: { color: getGroupColor(id) }
  }));

  const categoryIndexMap = new Map<string, number>();
  categories.forEach((c, idx) => categoryIndexMap.set(c.name, idx));

  // Build graph nodes
  const graphNodes = rawNodes.map(node => {
    const isNetwork = node.type === 'network';
    const isHybrid = node.type === 'hybrid';
    const weightSum = node.works_count + node.episodes_count;
    const symbolSize = Math.max(18, Math.min(65, Math.log10(weightSum + 10) * 18));
    const catIdx = categoryIndexMap.get(node.group_name_zh) ?? 0;
    const nodeColor = getGroupColor(node.group_id);

    return {
      id: node.name,
      name: node.name,
      value: weightSum,
      symbolSize,
      category: catIdx,
      label: {
        show: symbolSize > 25,
        formatter: node.name_zh ? `${node.name_zh}\n(${node.name})` : node.name,
        fontSize: 10,
        color: '#e2e8f0',
        lineHeight: 12,
        position: 'bottom' as const
      },
      itemStyle: {
        color: nodeColor,
        borderColor: isNetwork ? '#ffffff' : (isHybrid ? '#38bdf8' : 'rgba(255,255,255,0.2)'),
        borderWidth: isNetwork ? 3 : (isHybrid ? 2 : 1),
        shadowBlur: isNetwork ? 15 : 6,
        shadowColor: nodeColor
      },
      rawNode: node
    };
  });

  // Build graph links
  const graphLinks = rawEdges.map(edge => {
    return {
      source: edge.source,
      target: edge.target,
      value: edge.weight,
      lineStyle: {
        width: Math.min(5, Math.max(1.2, Math.log10(edge.weight + 1) * 1.5)),
        curveness: 0.12,
        opacity: 0.55
      },
      rawEdge: edge
    };
  });

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderColor: 'rgba(255, 255, 255, 0.15)',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { color: '#f8fafc', fontSize: 12 },
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const n: StudioNode = params.data.rawNode;
          const zh = (currentLocale.value.startsWith('zh') && n.name_zh) ? `<span style="color:#38bdf8;font-weight:bold;">${n.name_zh}</span> ` : '';
          const groupName = currentLocale.value.startsWith('zh') ? n.group_name_zh : n.group_name;
          return `
            <div style="font-size:13px;font-weight:bold;margin-bottom:4px;">${zh}${n.name}</div>
            <div style="color:#94a3b8;font-size:11px;margin-bottom:6px;">${t('genealogy.parentGroup')}: <span style="color:#f1f5f9;">${groupName}</span></div>
            <div style="display:flex;gap:12px;font-size:11px;margin-bottom:4px;">
              <span>${t('genealogy.featureWorksShort', { count: n.works_count })}</span>
              <span>${t('genealogy.episodesShort', { count: n.episodes_count })}</span>
            </div>
            ${n.badge ? `<div style="display:inline-block;padding:2px 6px;border-radius:4px;background:rgba(56,189,248,0.15);color:#38bdf8;font-size:10px;">${n.badge}</div>` : ''}
            <div style="margin-top:6px;font-size:10px;color:#64748b;">${t('genealogy.clickToInspect')}</div>
          `;
        } else if (params.dataType === 'edge') {
          const e = params.data.rawEdge;
          return `
            <div style="font-size:12px;font-weight:bold;margin-bottom:2px;">${e.source} ➔ ${e.target}</div>
            <div style="color:#38bdf8;font-size:11px;">${t('genealogy.relation', { rel: e.relation_label })}</div>
            <div style="color:#94a3b8;font-size:10px;">${t('genealogy.linkedWorks', { count: e.weight })}</div>
          `;
        }
        return '';
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: graphNodes,
        links: graphLinks,
        categories,
        roam: true,
        draggable: true,
        focusNodeAdjacency: true,
        force: {
          repulsion: 160,
          edgeLength: [35, 110],
          gravity: 0.12,
          friction: 0.75
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: {
            width: 4,
            opacity: 0.95
          }
        }
      }
    ]
  };

  chartInstance.setOption(option);

  // Click handler
  chartInstance.on('click', (params: any) => {
    if (params.dataType === 'node' && params.data.rawNode) {
      selectedGraphNode.value = params.data.rawNode;
    }
  });
}

function handleResize() {
  if (chartInstance) {
    try { chartInstance.resize(); } catch {}
  }
}

watch(currentLocale, () => {
  if (viewMode.value === 'graph') {
    initGraphChart();
  }
});

watch(viewMode, async (mode) => {
  if (mode === 'graph') {
    await nextTick();
    initGraphChart();
  }
});

onMounted(() => {
  loadData();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }
});
</script>

<template>
  <div class="space-y-6 animate-fade-in text-fg">
    <!-- 1. Header Banner & Stats Panel -->
    <div class="relative overflow-hidden rounded-3xl border border-line bg-surface-2 p-6 md:p-8 shadow-xs">
      <div class="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div>
          <div class="flex items-center gap-2.5 mb-2">
            <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-accent-fill/15 text-accent-text border border-accent-fill/25">
              {{ t('genealogy.title') }}
            </span>
            <span class="text-xs text-fg-4 font-mono">Decoupled Ecosystem Wiki</span>
          </div>
          <h2 class="text-2xl md:text-3xl font-black text-fg tracking-tight flex items-center gap-3">
            <span>🕸️ {{ t('genealogy.subtitle') }}</span>
          </h2>
          <p class="text-xs md:text-sm text-fg-3 mt-1.5 max-w-2xl leading-relaxed">
            {{ t('genealogy.desc') }}
          </p>
        </div>

        <!-- 统计四连卡片 -->
        <div v-if="genealogyData" class="grid grid-cols-2 md:grid-cols-4 gap-2.5 shrink-0">
          <div class="px-3.5 py-2.5 rounded-2xl bg-surface-3/80 border border-line/60 backdrop-blur-xs text-center">
            <div class="text-[10px] text-fg-4 font-medium flex items-center justify-center gap-1">
              <Building2 class="w-3 h-3 text-accent-text" /> {{ t('genealogy.statCoreGroups') }}
            </div>
            <div class="text-lg font-black text-fg mt-0.5">{{ genealogyData.stats.total_groups }}</div>
          </div>
          <div class="px-3.5 py-2.5 rounded-2xl bg-surface-3/80 border border-line/60 backdrop-blur-xs text-center">
            <div class="text-[10px] text-fg-4 font-medium flex items-center justify-center gap-1">
              <Layers class="w-3 h-3 text-sky-400" /> {{ t('genealogy.statCoveredStudios') }}
            </div>
            <div class="text-lg font-black text-fg mt-0.5">{{ genealogyData.stats.total_studios }}</div>
          </div>
          <div class="px-3.5 py-2.5 rounded-2xl bg-surface-3/80 border border-line/60 backdrop-blur-xs text-center">
            <div class="text-[10px] text-fg-4 font-medium flex items-center justify-center gap-1">
              <Network class="w-3 h-3 text-emerald-400" /> {{ t('genealogy.statNetworkEdges') }}
            </div>
            <div class="text-lg font-black text-fg mt-0.5">{{ genealogyData.stats.total_links }}</div>
          </div>
          <div class="px-3.5 py-2.5 rounded-2xl bg-surface-3/80 border border-line/60 backdrop-blur-xs text-center">
            <div class="text-[10px] text-fg-4 font-medium flex items-center justify-center gap-1">
              <Clapperboard class="w-3 h-3 text-amber-400" /> {{ t('genealogy.statRelatedWorks') }}
            </div>
            <div class="text-lg font-black text-fg mt-0.5">{{ genealogyData.stats.total_episodes_linked.toLocaleString() }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. Controls & Search Toolbar -->
    <div class="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 p-2 rounded-2xl bg-surface-2 border border-line">
      <!-- 搜索框 -->
      <div class="relative flex-1">
        <Search class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-fg-4" />
        <input
          v-model="searchQuery"
          type="text"
          :placeholder="t('genealogy.searchPlaceholder')"
          class="w-full pl-10 pr-4 py-2 rounded-xl bg-surface border border-line/80 text-xs text-fg focus:outline-hidden focus:border-accent-fill transition placeholder:text-fg-4"
        />
      </div>

      <!-- 集团类型过滤 -->
      <div class="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
        <div class="flex items-center p-1 rounded-xl bg-surface border border-line/80 shrink-0">
          <button
            @click="selectedGroupFilter = 'all'"
            :class="[
              'px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer',
              selectedGroupFilter === 'all' ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-4 hover:text-fg'
            ]"
          >
            {{ t('genealogy.filterAll') }}
          </button>
          <button
            @click="selectedGroupFilter = 'conglomerate'"
            :class="[
              'px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer',
              selectedGroupFilter === 'conglomerate' ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-4 hover:text-fg'
            ]"
          >
            {{ t('genealogy.filterConglomerate') }}
          </button>
          <button
            @click="selectedGroupFilter = 'network'"
            :class="[
              'px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer',
              selectedGroupFilter === 'network' ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-4 hover:text-fg'
            ]"
          >
            {{ t('genealogy.filterVodNetwork') }}
          </button>
          <button
            @click="selectedGroupFilter = 'indie_ecosystem'"
            :class="[
              'px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer',
              selectedGroupFilter === 'indie_ecosystem' ? 'bg-accent-fill text-on-fill font-bold' : 'text-fg-4 hover:text-fg'
            ]"
          >
            {{ t('genealogy.filterIndieEcosystem') }}
          </button>
        </div>

        <!-- 视图模式切换器 (Wiki vs 拓扑图) -->
        <div class="flex items-center p-1 rounded-xl bg-surface border border-line/80 shrink-0">
          <button
            @click="viewMode = 'wiki'"
            :class="[
              'px-3 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1.5 cursor-pointer',
              viewMode === 'wiki' ? 'bg-accent-fill text-on-fill shadow-xs' : 'text-fg-4 hover:text-fg'
            ]"
            :title="t('genealogy.viewWiki')"
          >
            <LayoutGrid class="w-3.5 h-3.5" />
            <span>{{ t('genealogy.viewWiki') }}</span>
          </button>
          <button
            @click="viewMode = 'graph'"
            :class="[
              'px-3 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1.5 cursor-pointer',
              viewMode === 'graph' ? 'bg-accent-fill text-on-fill shadow-xs' : 'text-fg-4 hover:text-fg'
            ]"
            :title="t('genealogy.viewGraph')"
          >
            <Network class="w-3.5 h-3.5" />
            <span>{{ t('genealogy.viewGraph') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 3. Loading & Error States -->
    <div v-if="loading" class="py-20 text-center text-fg-4 animate-pulse">
      <Network class="w-10 h-10 mx-auto mb-3 text-accent-text animate-spin" />
      <div class="text-sm font-medium">{{ t('genealogy.loadingGenealogy') }}</div>
    </div>

    <div v-else-if="error" class="py-12 text-center text-danger bg-danger/10 rounded-2xl border border-danger/20 p-6">
      <p class="font-bold mb-2">{{ error }}</p>
      <button @click="loadData" class="px-4 py-2 bg-accent-fill text-on-fill rounded-xl text-xs font-bold cursor-pointer">
        {{ t('genealogy.retry') }}
      </button>
    </div>

    <!-- 4. VIEW MODE 1: Wiki 家谱目录视图 (Wiki 模式) -->
    <div v-else-if="viewMode === 'wiki'" class="space-y-6">
      <!-- 快捷折叠工具条 -->
      <div class="flex items-center justify-between text-xs text-fg-4 px-1">
        <div>{{ t('genealogy.matchedGroups', { count: filteredGroups.length }) }}</div>
        <div class="flex items-center gap-3">
          <button @click="expandAllGroups" class="hover:text-accent-text cursor-pointer transition">{{ t('genealogy.expandAll') }}</button>
          <span>·</span>
          <button @click="collapseAllGroups" class="hover:text-accent-text cursor-pointer transition">{{ t('genealogy.collapseAll') }}</button>
        </div>
      </div>

      <!-- 集团卡片列表 -->
      <div
        v-for="group in filteredGroups"
        :key="group.id"
        class="rounded-3xl border border-line bg-surface-2 overflow-hidden shadow-xs transition duration-200 hover:border-line-focus"
      >
        <!-- 集团头部 (可点击折叠) -->
        <div
          @click="toggleGroupCollapse(group.id)"
          class="p-5 md:p-6 cursor-pointer flex items-center justify-between gap-4 select-none hover:bg-surface-3/50 transition border-b border-line/60"
        >
          <div class="flex items-start md:items-center gap-3.5">
            <div
              class="w-10 h-10 rounded-2xl flex items-center justify-center text-white shrink-0 shadow-sm"
              :style="{ backgroundColor: getGroupColor(group.id) }"
            >
              <Building2 class="w-5 h-5" />
            </div>

            <div>
              <div class="flex items-center gap-2 flex-wrap">
                <h3 class="text-base md:text-lg font-black text-fg tracking-tight">
                  {{ currentLocale.startsWith('zh') ? group.name_zh : group.name }}
                </h3>
                <span class="text-xs text-fg-4 font-mono font-medium">({{ currentLocale.startsWith('zh') ? group.name : group.name_zh }})</span>

                <span
                  class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider"
                  :class="group.type === 'conglomerate' ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30' : (group.type === 'indie_ecosystem' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30' : 'bg-purple-500/15 text-purple-400 border border-purple-500/30')"
                >
                  {{ group.type === 'conglomerate' ? t('genealogy.filterConglomerate') : (group.type === 'indie_ecosystem' ? t('genealogy.filterIndieEcosystem') : t('genealogy.filterVodNetwork')) }}
                </span>
              </div>

              <div class="flex items-center gap-3 text-xs text-fg-4 mt-1 flex-wrap">
                <span v-if="group.headquarters">📍 {{ group.headquarters }}</span>
                <span v-if="group.founded_year">{{ t('genealogy.foundedYear', { year: group.founded_year }) }}</span>
                <span>{{ t('genealogy.totalWorks', { count: group.total_works }) }}</span>
                <span>{{ t('genealogy.totalEpisodes', { count: group.total_episodes }) }}</span>
                <span class="font-bold text-accent-text">{{ t('genealogy.subStudiosCount', { count: group.studios.length }) }}</span>
              </div>
            </div>
          </div>

          <div class="text-fg-4 shrink-0">
            <ChevronDown v-if="!collapsedGroups.has(group.id)" class="w-5 h-5 transition transform" />
            <ChevronRight v-else class="w-5 h-5 transition transform" />
          </div>
        </div>

        <!-- 集团简介与旗下子厂牌网格 (折叠区) -->
        <div v-show="!collapsedGroups.has(group.id)" class="p-5 md:p-6 space-y-4">
          <!-- 简介 -->
          <p class="text-xs text-fg-3 leading-relaxed bg-surface/60 p-3.5 rounded-2xl border border-line/60">
            {{ group.description_zh }}
          </p>

          <!-- 旗下厂牌卡片网格 -->
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
            <div
              v-for="studio in group.studios"
              :key="studio.name"
              @click="emit('select-studio', studio.name)"
              class="group relative flex flex-col justify-between p-3.5 rounded-2xl bg-surface border border-line/80 hover:border-accent-fill/60 hover:shadow-md transition duration-200 cursor-pointer overflow-hidden"
            >
              <div>
                <!-- Top Row: Logo & Badge -->
                <div class="flex items-center justify-between gap-2 mb-2.5">
                  <div class="w-12 h-8 rounded-lg bg-surface-2 border border-line/60 flex items-center justify-center overflow-hidden shrink-0">
                    <img
                      v-if="studio.logo_url"
                      :src="getImageUrl(studio.logo_url)"
                      :alt="studio.name"
                      class="max-w-full max-h-full object-contain"
                      loading="lazy"
                    />
                    <span v-else class="text-xs font-black text-fg-4">
                      {{ studio.name.slice(0, 2).toUpperCase() }}
                    </span>
                  </div>

                  <span
                    v-if="studio.badge"
                    class="px-2 py-0.5 rounded-md text-[10px] font-bold text-accent-text bg-accent-fill/10 border border-accent-fill/20 truncate max-w-[150px]"
                    :title="studio.badge"
                  >
                    {{ studio.badge }}
                  </span>
                </div>

                <!-- Studio Names -->
                <div class="font-bold text-sm text-fg group-hover:text-accent-text transition line-clamp-1">
                  {{ currentLocale.startsWith('zh') ? (studio.name_zh || studio.name) : studio.name }}
                </div>
                <div class="text-[11px] text-fg-4 font-mono truncate mb-2">
                  {{ studio.name }}
                </div>
              </div>

              <!-- Bottom Row: Work Counts & Link Icon -->
              <div class="flex items-center justify-between text-[11px] text-fg-4 pt-2 border-t border-line/60 mt-1">
                <div class="flex items-center gap-2">
                  <span class="text-fg-3">{{ t('genealogy.featureWorksShort', { count: studio.works_count }) }}</span>
                  <span>·</span>
                  <span class="text-fg-3">{{ t('genealogy.episodesShort', { count: studio.episodes_count }) }}</span>
                </div>
                <ExternalLink class="w-3.5 h-3.5 text-fg-4 group-hover:text-accent-text transition shrink-0" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 5. VIEW MODE 2: 交互式力导向拓扑图 (Graph 模式) -->
    <div v-else-if="viewMode === 'graph'" class="relative rounded-3xl border border-line bg-surface-2 overflow-hidden shadow-xs">
      <!-- 拓扑图画布 -->
      <div ref="chartContainerRef" class="w-full h-[650px] md:h-[750px]"></div>

      <!-- 图例与操作浮条 (左上) -->
      <div class="absolute left-4 top-4 p-3 rounded-2xl bg-surface/90 border border-line backdrop-blur-md shadow-md text-xs space-y-1.5 pointer-events-auto">
        <div class="font-bold text-fg flex items-center gap-1.5 mb-1">
          <Info class="w-3.5 h-3.5 text-accent-text" /> {{ t('genealogy.guideTitle') }}
        </div>
        <div class="text-[11px] text-fg-3">{{ t('genealogy.guideWheel') }}</div>
        <div class="text-[11px] text-fg-3">{{ t('genealogy.guideHover') }}</div>
        <div class="text-[11px] text-fg-3">{{ t('genealogy.guideClick') }}</div>
      </div>

      <!-- 重置视角按钮 (右上) -->
      <div class="absolute right-4 top-4 flex items-center gap-2 pointer-events-auto">
        <button
          @click="initGraphChart"
          class="px-3 py-1.5 rounded-xl bg-surface/90 border border-line backdrop-blur-md text-xs font-bold text-fg hover:text-accent-text shadow-sm transition flex items-center gap-1.5 cursor-pointer"
        >
          <RotateCcw class="w-3.5 h-3.5" /> {{ t('genealogy.resetView') }}
        </button>
      </div>

      <!-- 选中节点详情抽屉浮层 (右侧抽屉) -->
      <div
        v-if="selectedGraphNode"
        class="absolute right-4 bottom-4 w-80 p-5 rounded-2xl bg-surface/95 border border-line backdrop-blur-md shadow-xl text-xs space-y-3 pointer-events-auto animate-fade-in"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-center gap-2.5">
            <div class="w-10 h-8 rounded-lg bg-surface-2 border border-line flex items-center justify-center overflow-hidden shrink-0">
              <img
                v-if="selectedGraphNode.logo_url"
                :src="getImageUrl(selectedGraphNode.logo_url)"
                class="max-w-full max-h-full object-contain"
              />
              <span v-else class="text-xs font-black">{{ selectedGraphNode.name.slice(0, 2) }}</span>
            </div>
            <div>
              <div class="font-black text-sm text-fg">{{ currentLocale.startsWith('zh') ? (selectedGraphNode.name_zh || selectedGraphNode.name) : selectedGraphNode.name }}</div>
              <div class="text-[10px] text-fg-4 font-mono">{{ selectedGraphNode.name }}</div>
            </div>
          </div>
          <button @click="selectedGraphNode = null" class="text-fg-4 hover:text-fg text-sm cursor-pointer">✕</button>
        </div>

        <div class="space-y-1 text-fg-3 bg-surface-2/60 p-2.5 rounded-xl border border-line/60">
          <div>{{ t('genealogy.parentGroup') }}: <span class="font-bold text-fg">{{ currentLocale.startsWith('zh') ? selectedGraphNode.group_name_zh : selectedGraphNode.group_name }}</span></div>
          <div>{{ t('genealogy.moviesCountDrawer', { count: selectedGraphNode.works_count }) }}</div>
          <div>{{ t('genealogy.episodesCountDrawer', { count: selectedGraphNode.episodes_count }) }}</div>
          <div v-if="selectedGraphNode.badge" class="text-accent-text font-bold">{{ t('genealogy.positioning', { role: selectedGraphNode.badge }) }}</div>
        </div>

        <p v-if="selectedGraphNode.description_zh" class="text-fg-4 line-clamp-3 text-[11px] leading-relaxed">
          {{ selectedGraphNode.description_zh }}
        </p>

        <button
          @click="emit('select-studio', selectedGraphNode.name)"
          class="w-full py-2 bg-accent-fill text-on-fill rounded-xl font-bold flex items-center justify-center gap-1.5 shadow-sm hover:opacity-90 transition cursor-pointer"
        >
          <span>{{ t('genealogy.viewStudioWorks') }}</span>
          <ExternalLink class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>
