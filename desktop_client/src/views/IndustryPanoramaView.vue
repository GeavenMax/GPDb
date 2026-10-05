<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { t, currentLocale } from '../i18n';
import { api } from '../api';
import type { IndustryAnalyticsData } from '../types';
import * as echarts from 'echarts';
import {
  Activity, Film, Layers, Building2, Flame, Users, Sparkles,
  Award, Compass, Network, RefreshCw, Clapperboard, UserCheck, Shield
} from '@lucide/vue';

const emit = defineEmits<{
  (e: 'change-tab', tab: string): void;
  (e: 'select-performer', id: number): void;
  (e: 'select-studio', name: string): void;
}>();

type PanoramaTab = 'all' | 'timeline' | 'studios' | 'aesthetics' | 'tropes' | 'creators';
const currentTab = ref<PanoramaTab>('all');

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<IndustryAnalyticsData | null>(null);

// Chart DOM refs
const timelineChartRef = ref<HTMLDivElement | null>(null);
const studioShareChartRef = ref<HTMLDivElement | null>(null);
const hhiChartRef = ref<HTMLDivElement | null>(null);
const buildChartRef = ref<HTMLDivElement | null>(null);
const hairChartRef = ref<HTMLDivElement | null>(null);
const tattooChartRef = ref<HTMLDivElement | null>(null);
const tropesChartRef = ref<HTMLDivElement | null>(null);
const survivalChartRef = ref<HTMLDivElement | null>(null);
const networkChartRef = ref<HTMLDivElement | null>(null);

const chartInstances: echarts.ECharts[] = [];

function initChart(dom: HTMLElement | null): echarts.ECharts | null {
  if (!dom) return null;
  const existing = echarts.getInstanceByDom(dom);
  if (existing) {
    try { existing.dispose(); } catch {}
  }
  const chart = echarts.init(dom);
  chartInstances.push(chart);
  return chart;
}


function disposeAllCharts() {
  chartInstances.forEach(c => {
    try { c.dispose(); } catch {}
  });
  chartInstances.length = 0;
}

function handleResize() {
  chartInstances.forEach(c => {
    try { c.resize(); } catch {}
  });
}

// Color palettes for dark UI
const THEME_COLORS = [
  '#f59e0b', '#3b82f6', '#10b981', '#ec4899', '#8b5cf6',
  '#f97316', '#06b6d4', '#84cc16', '#e11d48', '#6366f1',
  '#14b8a6', '#d946ef', '#eab308', '#64748b'
];

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    data.value = await api.getIndustryAnalytics();
  } catch (err: any) {
    error.value = err?.message || t('panorama.loadFailed');
  } finally {
    loading.value = false;
  }

  // Once loading is false, Vue mounts the data DOM on nextTick
  if (data.value) {
    await nextTick();
    // Allow DOM layout and transitions to settle
    setTimeout(() => {
      renderCharts();
      setTimeout(handleResize, 50);
    }, 60);
  }
}

function renderCharts() {
  disposeAllCharts();
  if (!data.value) return;

  const d = data.value;
  const tab = currentTab.value;

  // 1. Timeline Chart (Dual Y-Axis: Movies + Episodes vs Avg Duration)
  if ((tab === 'all' || tab === 'timeline') && timelineChartRef.value) {
    const chart = initChart(timelineChartRef.value);
    if (chart) {
      const years = d.timeline.map(t => t.year);
      const movies = d.timeline.map(t => t.movies_count);
      const episodes = d.timeline.map(t => t.episodes_count);
      const durations = d.timeline.map(t => t.avg_duration);

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' },
          axisPointer: { type: 'cross', label: { backgroundColor: '#27272a' } }
        },
        legend: {
          data: [t('panorama.seriesMovies'), t('panorama.seriesEpisodes'), t('panorama.seriesAvgDuration')],
          textStyle: { color: '#a1a1aa' },
          top: 0
        },
        grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
        xAxis: {
          type: 'category',
          data: years,
          axisLine: { lineStyle: { color: '#3f3f46' } },
          axisLabel: { color: '#a1a1aa' }
        },
        yAxis: [
          {
            type: 'value',
            name: t('panorama.worksCountAxis'),
            nameTextStyle: { color: '#71717a' },
            splitLine: { lineStyle: { color: '#27272a' } },
            axisLabel: { color: '#a1a1aa' }
          },
          {
            type: 'value',
            name: t('panorama.durationAxis'),
            nameTextStyle: { color: '#71717a' },
            splitLine: { show: false },
            axisLabel: { color: '#a1a1aa' }
          }
        ],
        series: [
          {
            name: t('panorama.seriesMovies'),
            type: 'bar',
            data: movies,
            itemStyle: { color: '#f59e0b', borderRadius: [3, 3, 0, 0] }
          },
          {
            name: t('panorama.seriesEpisodes'),
            type: 'line',
            data: episodes,
            smooth: true,
            itemStyle: { color: '#06b6d4' },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(6, 182, 212, 0.4)' },
                { offset: 1, color: 'rgba(6, 182, 212, 0.0)' }
              ])
            }
          },
          {
            name: t('panorama.seriesAvgDuration'),
            type: 'line',
            yAxisIndex: 1,
            data: durations,
            smooth: true,
            itemStyle: { color: '#ec4899' },
            lineStyle: { width: 2.5 }
          }
        ]
      });
    }
  }

  // 2. Studio Landscape Stream/Stacked Area Chart
  if ((tab === 'all' || tab === 'studios') && studioShareChartRef.value) {
    const chart = initChart(studioShareChartRef.value);
    if (chart) {
      const tracked = d.studios.tracked_studios;
      const years = d.studios.yearly_matrix.map(m => m.year);

      const series = tracked.map((studio, idx) => ({
        name: studio,
        type: 'line',
        stack: 'Total',
        areaStyle: {},
        smooth: true,
        emphasis: { focus: 'series' },
        data: d.studios.yearly_matrix.map(m => (m[studio] as number) || 0),
        itemStyle: { color: THEME_COLORS[idx % THEME_COLORS.length] }
      }));

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' }
        },
        legend: {
          type: 'scroll',
          data: tracked,
          textStyle: { color: '#a1a1aa' },
          top: 0
        },
        grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: years,
          axisLine: { lineStyle: { color: '#3f3f46' } },
          axisLabel: { color: '#a1a1aa' }
        },
        yAxis: {
          type: 'value',
          name: t('panorama.yearlyReleaseAxis'),
          nameTextStyle: { color: '#71717a' },
          splitLine: { lineStyle: { color: '#27272a' } },
          axisLabel: { color: '#a1a1aa' }
        },
        series
      });
    }
  }

  // 2b. HHI & CR5 Market Concentration
  if ((tab === 'all' || tab === 'studios') && hhiChartRef.value) {
    const chart = initChart(hhiChartRef.value);
    if (chart) {
      const years = d.timeline.map(t => t.year);
      const hhi = d.timeline.map(t => t.hhi);
      const cr5 = d.timeline.map(t => t.cr5);

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' }
        },
        legend: {
          data: [t('panorama.cr5Series'), t('panorama.hhiSeries')],
          textStyle: { color: '#a1a1aa' },
          top: 0
        },
        grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
        xAxis: {
          type: 'category',
          data: years,
          axisLine: { lineStyle: { color: '#3f3f46' } },
          axisLabel: { color: '#a1a1aa' }
        },
        yAxis: [
          {
            type: 'value',
            name: 'CR5 (%)',
            nameTextStyle: { color: '#71717a' },
            splitLine: { lineStyle: { color: '#27272a' } },
            axisLabel: { color: '#a1a1aa' }
          },
          {
            type: 'value',
            name: 'HHI',
            nameTextStyle: { color: '#71717a' },
            splitLine: { show: false },
            axisLabel: { color: '#a1a1aa' }
          }
        ],
        series: [
          {
            name: t('panorama.cr5Series'),
            type: 'line',
            data: cr5,
            smooth: true,
            itemStyle: { color: '#3b82f6' },
            lineStyle: { width: 3 }
          },
          {
            name: t('panorama.hhiSeries'),
            type: 'bar',
            yAxisIndex: 1,
          data: hhi,
          itemStyle: { color: 'rgba(139, 92, 246, 0.4)', borderRadius: [3, 3, 0, 0] }
        }
      ]
    });
    }
  }

  // 3. Build Distribution by Decade (Stacked Bar)
  if ((tab === 'all' || tab === 'aesthetics') && buildChartRef.value) {
    const chart = initChart(buildChartRef.value);
    if (chart) {
      const decades = ['1970s', '1980s', '1990s', '2000s', '2010s', '2020s'];
      const builds = ['Trim', 'Swimmer', 'Muscular', 'Normal', 'Body Builder', 'Bear', 'Stocky', 'Heavy'];

      const series = builds.map((b, idx) => ({
        name: b,
        type: 'bar',
        stack: 'total',
        emphasis: { focus: 'series' },
        data: decades.map(dec => d.aesthetics.build_by_decade[dec]?.[b] || 0),
        itemStyle: { color: THEME_COLORS[idx % THEME_COLORS.length] }
      }));

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' }
        },
        legend: {
          data: builds,
          textStyle: { color: '#a1a1aa' },
          top: 0
        },
        grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
        xAxis: {
          type: 'category',
          data: decades,
          axisLine: { lineStyle: { color: '#3f3f46' } },
          axisLabel: { color: '#a1a1aa' }
        },
        yAxis: {
          type: 'value',
          name: t('panorama.performerCountsAxis'),
          nameTextStyle: { color: '#71717a' },
          splitLine: { lineStyle: { color: '#27272a' } },
          axisLabel: { color: '#a1a1aa' }
        },
        series
      });
    }
  }

  // 3b. Body Hair Trend by Decade
  if ((tab === 'all' || tab === 'aesthetics') && hairChartRef.value) {
    const chart = initChart(hairChartRef.value);
    if (chart) {
      const decades = ['1970s', '1980s', '1990s', '2000s', '2010s', '2020s'];
      const hairKeys = ['Smooth / 无毛', 'Light / 适度体毛', 'Hairy / 浓密毛发'];
      const hairs = [t('panorama.hairSmooth'), t('panorama.hairLight'), t('panorama.hairHairy')];
      const hairColors = ['#06b6d4', '#10b981', '#f59e0b'];

      const series = hairs.map((h, idx) => ({
        name: h,
        type: 'line',
        smooth: true,
        data: decades.map(dec => d.aesthetics.hair_by_decade[dec]?.[hairKeys[idx]] || 0),
        itemStyle: { color: hairColors[idx] },
        lineStyle: { width: 3 }
      }));

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' }
        },
        legend: {
          data: hairs,
          textStyle: { color: '#a1a1aa' },
          top: 0
        },
        grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
        xAxis: {
          type: 'category',
          data: decades,
          axisLine: { lineStyle: { color: '#3f3f46' } },
          axisLabel: { color: '#a1a1aa' }
        },
        yAxis: {
          type: 'value',
          name: t('panorama.frequencyAxis'),
          nameTextStyle: { color: '#71717a' },
          splitLine: { lineStyle: { color: '#27272a' } },
          axisLabel: { color: '#a1a1aa' }
        },
        series
      });
    }
  }

  // 3c. Tattoo Adoption Rate
  if ((tab === 'all' || tab === 'aesthetics') && tattooChartRef.value) {
    const chart = initChart(tattooChartRef.value);
    if (chart) {
      const decades = d.aesthetics.tattoo_by_decade.map(t => t.decade);
      const pcts = d.aesthetics.tattoo_by_decade.map(t => t.percentage);

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          formatter: (params: any) => t('panorama.tattooTooltip', { decade: params.name, pct: params.value }),
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' }
        },
        grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
        xAxis: {
          type: 'category',
          data: decades,
          axisLine: { lineStyle: { color: '#3f3f46' } },
          axisLabel: { color: '#a1a1aa' }
        },
        yAxis: {
          type: 'value',
          name: t('panorama.percentageAxis'),
          nameTextStyle: { color: '#71717a' },
          splitLine: { lineStyle: { color: '#27272a' } },
          axisLabel: { color: '#a1a1aa', formatter: '{value}%' }
        },
        series: [{
          type: 'bar',
          data: pcts,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#ec4899' },
              { offset: 1, color: '#8b5cf6' }
            ]),
            borderRadius: [4, 4, 0, 0]
          },
          label: {
            show: true,
            position: 'top',
            color: '#f4f4f5',
            formatter: '{c}%'
          }
        }]
      });
    }
  }

  // 4. Tropes Theme River / Streamgraph
  if ((tab === 'all' || tab === 'tropes') && tropesChartRef.value) {
    const chart = initChart(tropesChartRef.value);
    if (chart) {
      const genres = d.tropes.top_genres.slice(0, 8);
      const years = d.tropes.theme_river.map(r => r.year);

      const series = genres.map((g, idx) => ({
        name: currentLocale.value.startsWith('zh') ? `${g.zh} (${g.en})` : `${g.en} (${g.zh})`,
        type: 'line',
        stack: 'Total',
        areaStyle: {},
        smooth: true,
        emphasis: { focus: 'series' },
        data: d.tropes.theme_river.map(r => (r[g.en] as number) || 0),
        itemStyle: { color: THEME_COLORS[idx % THEME_COLORS.length] }
      }));

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' }
        },
        legend: {
          type: 'scroll',
          data: genres.map(g => currentLocale.value.startsWith('zh') ? `${g.zh} (${g.en})` : `${g.en} (${g.zh})`),
          textStyle: { color: '#a1a1aa' },
          top: 0
        },
        grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: years,
          axisLine: { lineStyle: { color: '#3f3f46' } },
          axisLabel: { color: '#a1a1aa' }
        },
        yAxis: {
          type: 'value',
          name: t('panorama.yearlyReleaseAxis'),
          nameTextStyle: { color: '#71717a' },
          splitLine: { lineStyle: { color: '#27272a' } },
          axisLabel: { color: '#a1a1aa' }
        },
        series
      });
    }
  }

  // 5. Creator Survival Curve (Kaplan-Meier Retention)
  if ((tab === 'all' || tab === 'creators') && survivalChartRef.value) {
    const chart = initChart(survivalChartRef.value);
    if (chart) {
      const years = d.creators.survival_curve.map(s => t('panorama.survivalYearsUnit', { years: s.years }));
      const pcts = d.creators.survival_curve.map(s => s.percentage);

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          formatter: (params: any) => t('panorama.survivalTooltip', { years: params.name, pct: params.value }),
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' }
        },
        grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
        xAxis: {
          type: 'category',
          data: years,
          axisLine: { lineStyle: { color: '#3f3f46' } },
          axisLabel: { color: '#a1a1aa' }
        },
        yAxis: {
          type: 'value',
          name: t('panorama.survivalRetentionAxis'),
          nameTextStyle: { color: '#71717a' },
          splitLine: { lineStyle: { color: '#27272a' } },
          axisLabel: { color: '#a1a1aa', formatter: '{value}%' }
        },
        series: [{
          type: 'line',
          step: 'start',
          data: pcts,
          itemStyle: { color: '#10b981' },
          lineStyle: { width: 3 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(16, 185, 129, 0.4)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0.0)' }
            ])
          },
          label: {
            show: true,
            position: 'top',
            color: '#34d399',
            formatter: '{c}%'
          }
        }]
      });
    }
  }

  // 5b. Force-Directed Co-star Network
  if ((tab === 'all' || tab === 'creators') && networkChartRef.value && d.creators.network_graph.nodes.length > 0) {
    const chart = initChart(networkChartRef.value);
    if (chart) {
      const g = d.creators.network_graph;

      chart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          backgroundColor: '#18181b',
          borderColor: '#27272a',
          textStyle: { color: '#f4f4f5' },
          formatter: (params: any) => {
            if (params.dataType === 'node') {
              return `<b>${params.data.name}</b><br/>${t('panorama.networkTotalWorks')}: ${params.data.value}<br/>${t('panorama.networkPrimaryFaction')}: ${params.data.studio || t('panorama.freelance')}`;
            }
            if (params.dataType === 'edge') {
              return t('panorama.networkCoopWorks', { count: params.data.value });
            }
          }
        },
        legend: [{
          data: g.categories.map(c => c.name),
          textStyle: { color: '#a1a1aa' },
          top: 0
        }],
        color: ['#f59e0b', '#3b82f6', '#10b981', '#ec4899'],
        series: [{
          type: 'graph',
          layout: 'force',
          data: g.nodes.map(n => ({
            ...n,
            symbolSize: Math.max(12, Math.min(48, Math.sqrt(n.value) * 3))
          })),
          links: g.links.map(l => ({
            ...l,
            lineStyle: { width: Math.max(1, Math.min(6, l.value / 3)), color: 'rgba(255, 255, 255, 0.2)' }
          })),
          categories: g.categories,
          roam: true,
          label: {
            show: true,
            position: 'right',
            color: '#e4e4e7',
            fontSize: 10
          },
          force: {
            repulsion: 120,
            gravity: 0.1,
            edgeLength: [30, 90]
          }
        }]
      });
    }
  }
}

watch(currentLocale, () => {
  renderCharts();
});

watch(currentTab, async () => {
  await nextTick();
  setTimeout(() => {
    renderCharts();
    setTimeout(handleResize, 50);
  }, 30);
});

onMounted(() => {
  loadData();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  disposeAllCharts();
});
</script>

<template>
  <div class="space-y-8 max-w-7xl mx-auto pb-20 animate-fade-in text-fg px-2 md:px-4">
    <!-- Header -->
    <div class="flex flex-col md:flex-row md:items-center justify-between border-b border-line pb-6 gap-4">
      <div>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-2xl bg-gradient-to-tr from-amber-500 to-orange-400 flex items-center justify-center text-zinc-950 font-bold shadow-lg shadow-amber-500/20">
            <Compass class="w-6 h-6" />
          </div>
          <div>
            <h1 class="text-2xl md:text-3xl font-extrabold text-fg tracking-tight">
              {{ t('panorama.title') }}
            </h1>
            <p class="text-xs text-fg-4 mt-0.5">
              {{ t('panorama.subtitle') }}
            </p>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <button
          @click="loadData"
          class="px-3.5 py-1.5 rounded-xl border border-line bg-surface hover:bg-surface-2 text-fg-3 hover:text-fg text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer"
          :title="t('panorama.refreshData')"
        >
          <RefreshCw :class="['w-3.5 h-3.5', loading ? 'animate-spin' : '']" />
          <span>{{ t('panorama.refreshMarket') }}</span>
        </button>

        <div v-if="data?.meta" class="px-3 py-1 rounded-full bg-accent-fill/10 border border-accent/30 text-[11px] font-medium text-accent">
          {{ t('panorama.includedCount', { count: data.meta.total_movies_covered.toLocaleString(), span: data.meta.years_span }) }}
        </div>
      </div>
    </div>

    <!-- Loading / Error States -->
    <div v-if="loading" class="py-24 text-center space-y-3">
      <RefreshCw class="w-8 h-8 mx-auto animate-spin text-accent" />
      <p class="text-sm text-fg-3">{{ t('panorama.loadingMetadata') }}</p>
    </div>

    <div v-else-if="error" class="py-16 text-center space-y-4 max-w-md mx-auto">
      <div class="w-12 h-12 rounded-full bg-danger-fill/20 text-danger flex items-center justify-center mx-auto">
        <Shield class="w-6 h-6" />
      </div>
      <p class="text-sm text-fg-3">{{ error }}</p>
      <button
        @click="loadData"
        class="px-4 py-2 rounded-xl bg-accent text-zinc-950 font-bold text-xs hover:brightness-110 transition cursor-pointer"
      >
        {{ t('panorama.reload') }}
      </button>
    </div>

    <div v-else-if="data" class="space-y-8">
      <!-- 4 Historical Epochs Banner -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
        <div class="p-4 rounded-2xl bg-surface border border-line/80 space-y-1.5 hover:border-accent/40 transition">
          <div class="text-[10px] font-extrabold uppercase tracking-wider text-amber-500">{{ t('panorama.era70sBadge') }}</div>
          <div class="text-sm font-bold text-fg">{{ t('panorama.era70sTitle') }}</div>
          <div class="text-xs text-fg-4 leading-relaxed">
            {{ t('panorama.era70sDesc') }}
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-surface border border-line/80 space-y-1.5 hover:border-accent/40 transition">
          <div class="text-[10px] font-extrabold uppercase tracking-wider text-cyan-400">{{ t('panorama.era80sBadge') }}</div>
          <div class="text-sm font-bold text-fg">{{ t('panorama.era80sTitle') }}</div>
          <div class="text-xs text-fg-4 leading-relaxed">
            {{ t('panorama.era80sDesc') }}
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-surface border border-line/80 space-y-1.5 hover:border-accent/40 transition">
          <div class="text-[10px] font-extrabold uppercase tracking-wider text-pink-400">{{ t('panorama.era90sBadge') }}</div>
          <div class="text-sm font-bold text-fg">{{ t('panorama.era90sTitle') }}</div>
          <div class="text-xs text-fg-4 leading-relaxed">
            {{ t('panorama.era90sDesc') }}
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-surface border border-line/80 space-y-1.5 hover:border-accent/40 transition">
          <div class="text-[10px] font-extrabold uppercase tracking-wider text-emerald-400">{{ t('panorama.era10sBadge') }}</div>
          <div class="text-sm font-bold text-fg">{{ t('panorama.era10sTitle') }}</div>
          <div class="text-xs text-fg-4 leading-relaxed">
            {{ t('panorama.era10sDesc') }}
          </div>
        </div>
      </div>

      <!-- Navigation Segment Tabs -->
      <div class="flex items-center gap-1.5 border-b border-line pb-2 overflow-x-auto text-xs font-semibold">
        <button
          v-for="tabItem in [
            { id: 'all', label: t('panorama.tabAll'), icon: Activity },
            { id: 'timeline', label: t('panorama.tabTimeline'), icon: Film },
            { id: 'studios', label: t('panorama.tabStudios'), icon: Building2 },
            { id: 'aesthetics', label: t('panorama.tabAesthetics'), icon: Flame },
            { id: 'tropes', label: t('panorama.tabTropes'), icon: Layers },
            { id: 'creators', label: t('panorama.tabCreators'), icon: Users },
          ]"
          :key="tabItem.id"
          @click="currentTab = (tabItem.id as PanoramaTab)"
          :class="[
            'px-3.5 py-2 rounded-xl flex items-center gap-2 transition cursor-pointer shrink-0',
            currentTab === tabItem.id
              ? 'bg-accent-fill/15 text-accent border border-accent/30 font-bold'
              : 'text-fg-4 hover:text-fg hover:bg-surface border border-transparent'
          ]"
        >
          <component :is="tabItem.icon" class="w-4 h-4" />
          <span>{{ tabItem.label }}</span>
        </button>
      </div>

      <!-- Tab Content 1: Macro Production & Duration Cliff -->
      <div v-show="currentTab === 'all' || currentTab === 'timeline'" class="space-y-4">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-bold text-fg flex items-center gap-2">
              <Film class="w-5 h-5 text-accent" />
              <span>{{ t('panorama.chartTimelineTitle') }}</span>
            </h2>
            <p class="text-xs text-fg-4">
              {{ t('panorama.chartTimelineSubtitle') }}
            </p>
          </div>
        </div>

        <div class="p-5 rounded-2xl bg-surface border border-line">
          <div ref="timelineChartRef" class="w-full h-80"></div>
        </div>
      </div>

      <!-- Tab Content 2: Studio Landscape & Market Shares -->
      <div v-show="currentTab === 'all' || currentTab === 'studios'" class="space-y-6">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-bold text-fg flex items-center gap-2">
              <Building2 class="w-5 h-5 text-accent" />
              <span>{{ t('panorama.chartStudiosTitle') }}</span>
            </h2>
            <p class="text-xs text-fg-4">
              {{ t('panorama.chartStudiosSubtitle') }}
            </p>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">{{ t('panorama.themeRiverHeading') }}</h3>
            <div ref="studioShareChartRef" class="w-full h-72"></div>
          </div>

          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">{{ t('panorama.hhiCr5Heading') }}</h3>
            <div ref="hhiChartRef" class="w-full h-72"></div>
          </div>
        </div>

        <!-- Top Studios Hall of Fame -->
        <div class="p-5 rounded-2xl bg-surface border border-line space-y-4">
          <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">{{ t('panorama.top10StudiosHeading') }}</h3>
          <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
            <div
              v-for="(st, idx) in data.studios.top_studios.slice(0, 10)"
              :key="st.studio_name"
              class="p-3.5 rounded-xl bg-surface-2 border border-line/60 flex flex-col justify-between hover:border-accent/40 transition group"
            >
              <div>
                <div class="flex items-center justify-between mb-1.5">
                  <span class="text-[10px] font-extrabold text-amber-500">#{{ idx + 1 }}</span>
                  <span class="text-[10px] text-fg-4">{{ st.year_start }} - {{ st.year_end }}</span>
                </div>
                <div class="font-bold text-sm text-fg group-hover:text-accent transition line-clamp-1" :title="st.studio_name">
                  {{ st.name_zh || st.studio_name }}
                </div>
                <div v-if="st.name_zh" class="text-[10px] text-fg-4 line-clamp-1">
                  {{ st.studio_name }}
                </div>
              </div>
              <div class="mt-3 pt-2 border-t border-line/40 flex items-center justify-between text-xs">
                <span class="text-fg-4">{{ t('panorama.releasedWorks') }}</span>
                <span class="font-extrabold text-accent">{{ t('panorama.worksUnit', { count: st.total_movies.toLocaleString() }) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab Content 3: Aesthetic & Physicality Archaeology -->
      <div v-show="currentTab === 'all' || currentTab === 'aesthetics'" class="space-y-6">
        <div>
          <h2 class="text-lg font-bold text-fg flex items-center gap-2">
            <Flame class="w-5 h-5 text-accent" />
            <span>{{ t('panorama.aestheticsTitle') }}</span>
          </h2>
          <p class="text-xs text-fg-4">
            {{ t('panorama.aestheticsSubtitle') }}
          </p>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <!-- Build by Decade -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">{{ t('panorama.buildHeading') }}</h3>
            <div ref="buildChartRef" class="w-full h-64"></div>
            <p class="text-[11px] text-fg-4">
              {{ t('panorama.buildDesc') }}
            </p>
          </div>

          <!-- Body Hair Trend -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">{{ t('panorama.hairHeading') }}</h3>
            <div ref="hairChartRef" class="w-full h-64"></div>
            <p class="text-[11px] text-fg-4">
              {{ t('panorama.hairDesc') }}
            </p>
          </div>

          <!-- Tattoo Adoption -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">{{ t('panorama.tattooHeading') }}</h3>
            <div ref="tattooChartRef" class="w-full h-64"></div>
            <p class="text-[11px] text-fg-4">
              {{ t('panorama.tattooDesc') }}
            </p>
          </div>
        </div>
      </div>

      <!-- Tab Content 4: Tropes & Category Shifts -->
      <div v-show="currentTab === 'all' || currentTab === 'tropes'" class="space-y-4">
        <div>
          <h2 class="text-lg font-bold text-fg flex items-center gap-2">
            <Layers class="w-5 h-5 text-accent" />
            <span>{{ t('panorama.tropesTitle') }}</span>
          </h2>
          <p class="text-xs text-fg-4">
            {{ t('panorama.tropesSubtitle') }}
          </p>
        </div>

        <div class="p-5 rounded-2xl bg-surface border border-line">
          <div ref="tropesChartRef" class="w-full h-80"></div>
        </div>

        <!-- 4b. Narrative Tropes NLP Keywords Grid -->
        <div v-if="data.tropes.narrative_keywords_timeline" class="p-5 rounded-2xl bg-surface border border-line space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3 flex items-center gap-2">
              <Sparkles class="w-4 h-4 text-amber-500" />
              <span>{{ t('panorama.nlpTitle') }}</span>
            </h3>
            <span class="text-[11px] text-fg-4">{{ t('panorama.nlpSubtitle') }}</span>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-xs text-left">
              <thead>
                <tr class="border-b border-line text-fg-4">
                  <th class="py-2.5 px-3 font-semibold">{{ t('panorama.nlpColTrope') }}</th>
                  <th class="py-2.5 px-2 text-right">1970s</th>
                  <th class="py-2.5 px-2 text-right">1980s</th>
                  <th class="py-2.5 px-2 text-right">1990s</th>
                  <th class="py-2.5 px-2 text-right">2000s</th>
                  <th class="py-2.5 px-2 text-right">2010s</th>
                  <th class="py-2.5 px-2 text-right">2020s</th>
                  <th class="py-2.5 px-3 text-right">{{ t('panorama.nlpColPeak') }}</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-line/40">
                <tr
                  v-for="item in data.tropes.narrative_keywords_timeline"
                  :key="item.trope"
                  class="hover:bg-surface-2/60 transition"
                >
                  <td class="py-2.5 px-3 font-medium text-fg flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-accent"></span>
                    <span>{{ item.trope }}</span>
                  </td>
                  <td class="py-2.5 px-2 text-right text-fg-4">{{ item.data['1970s'] || 0 }}</td>
                  <td class="py-2.5 px-2 text-right text-fg-4">{{ item.data['1980s'] || 0 }}</td>
                  <td class="py-2.5 px-2 text-right text-fg-3">{{ item.data['1990s'] || 0 }}</td>
                  <td class="py-2.5 px-2 text-right text-fg-2 font-semibold">{{ item.data['2000s'] || 0 }}</td>
                  <td class="py-2.5 px-2 text-right text-fg-2 font-semibold">{{ item.data['2010s'] || 0 }}</td>
                  <td class="py-2.5 px-2 text-right font-bold text-accent">{{ item.data['2020s'] || 0 }}</td>
                  <td class="py-2.5 px-3 text-right">
                    <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-accent-fill/10 text-accent">
                      {{
                        Object.entries(item.data).reduce((max, cur) => cur[1] > max[1] ? cur : max, ['1970s', 0])[0]
                      }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 4c. Titology & Synopsis Length Dynamics -->
        <div v-if="data.tropes.titology_by_decade" class="p-5 rounded-2xl bg-surface border border-line space-y-4">
          <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3 flex items-center gap-2">
            <Layers class="w-4 h-4 text-cyan-400" />
            <span>{{ t('panorama.titologyTitle') }}</span>
          </h3>
          <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            <div
              v-for="tito in data.tropes.titology_by_decade"
              :key="tito.decade"
              class="p-3 rounded-xl bg-surface-2 border border-line/60 space-y-1.5"
            >
              <div class="text-[11px] font-extrabold text-accent">{{ tito.decade }}</div>
              <div class="text-xs text-fg-3">
                {{ t('panorama.avgTitleLen') }}: <span class="font-bold text-fg">{{ t('panorama.charsUnit', { count: tito.avg_title_len }) }}</span>
              </div>
              <div class="text-xs text-fg-3">
                {{ t('panorama.avgDescLen') }}: <span class="font-bold text-fg">{{ t('panorama.charsUnit', { count: tito.avg_desc_len }) }}</span>
              </div>
              <div class="text-[10px] text-fg-4">
                {{ t('panorama.colonPct') }}: <span class="text-amber-400 font-semibold">{{ tito.colon_pct }}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab Content 5: Creators, Longevity & Network -->
      <div v-show="currentTab === 'all' || currentTab === 'creators'" class="space-y-6">
        <div>
          <h2 class="text-lg font-bold text-fg flex items-center gap-2">
            <Users class="w-5 h-5 text-accent" />
            <span>{{ t('panorama.creatorsTitle') }}</span>
          </h2>
          <p class="text-xs text-fg-4">
            {{ t('panorama.creatorsSubtitle') }}
          </p>
        </div>

        <!-- Survival Analysis -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">{{ t('panorama.survivalHeading') }}</h3>
            <div ref="survivalChartRef" class="w-full h-64"></div>
            <p class="text-[11px] text-fg-4">
              {{ t('panorama.survivalDesc') }}
            </p>
          </div>

          <div class="p-5 rounded-2xl bg-surface border border-line space-y-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">{{ t('panorama.careerSpanHeading') }}</h3>
            <div class="grid grid-cols-1 gap-2 pt-2">
              <div
                v-for="(cnt, bracket) in data.creators.span_brackets"
                :key="bracket"
                class="p-2.5 rounded-xl bg-surface-2 border border-line/60 flex items-center justify-between text-xs"
              >
                <div class="font-medium text-fg flex items-center gap-2">
                  <UserCheck class="w-4 h-4 text-accent" />
                  <span>{{ bracket }}</span>
                </div>
                <div class="flex items-center gap-3">
                  <span class="text-fg-4">{{ t('panorama.peopleUnit', { count: cnt.toLocaleString() }) }}</span>
                  <span class="font-bold text-accent">
                    {{ Math.round((cnt / data.creators.total_analyzed_performers) * 100) }}%
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Co-star Network Graph -->
        <div class="p-5 rounded-2xl bg-surface border border-line space-y-3">
          <div class="flex items-center justify-between">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3 flex items-center gap-2">
              <Network class="w-4 h-4 text-accent" />
              <span>{{ t('panorama.networkHeading') }}</span>
            </h3>
            <span class="text-[11px] text-fg-4">{{ t('panorama.networkTip') }}</span>
          </div>
          <div ref="networkChartRef" class="w-full h-96"></div>
        </div>

        <!-- Evergreen Performers Top 15 -->
        <div class="p-5 rounded-2xl bg-surface border border-line space-y-4">
          <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3 flex items-center gap-2">
            <Award class="w-4 h-4 text-amber-500" />
            <span>{{ t('panorama.evergreenHeading') }}</span>
          </h3>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
            <div
              v-for="(p, idx) in data.creators.top_evergreens.slice(0, 15)"
              :key="p.id"
              class="p-3.5 rounded-xl bg-surface-2 border border-line/60 flex flex-col justify-between hover:border-accent/40 transition group"
            >
              <div>
                <div class="flex items-center justify-between mb-1.5">
                  <span class="text-[10px] font-extrabold text-amber-500">#{{ idx + 1 }}</span>
                  <span class="text-[10px] text-fg-4">{{ p.debut_year }} - {{ p.last_year }}</span>
                </div>
                <div class="font-bold text-sm text-fg group-hover:text-accent transition line-clamp-1" :title="p.name">
                  {{ p.name }}
                </div>
                <div class="text-[11px] text-fg-4 mt-1">
                  {{ t('panorama.activeYears', { years: p.career_years }) }}
                </div>
              </div>
              <div class="mt-3 pt-2 border-t border-line/40 flex items-center justify-between text-xs">
                <span class="text-[10px] text-fg-4 line-clamp-1" :title="p.primary_studio || ''">
                  {{ p.primary_studio || t('panorama.freelance') }}
                </span>
                <span class="font-extrabold text-accent shrink-0">{{ t('panorama.worksUnit', { count: p.works_count }) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Golden Duos & Actor-Directors -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <!-- Golden Duos -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3 flex items-center gap-2">
              <Sparkles class="w-4 h-4 text-pink-400" />
              <span>{{ t('panorama.goldenDuo') }}</span>
            </h3>
            <div class="space-y-2">
              <div
                v-for="(d, idx) in data.creators.golden_duos.slice(0, 8)"
                :key="idx"
                class="p-2.5 rounded-xl bg-surface-2 border border-line/50 flex items-center justify-between text-xs"
              >
                <div class="font-semibold text-fg">
                  {{ d.actor1 }} <span class="text-accent font-normal">&</span> {{ d.actor2 }}
                </div>
                <span class="font-extrabold text-accent">{{ t('panorama.coopCount', { count: d.common_movies }) }}</span>
              </div>
            </div>
          </div>

          <!-- Actor-Directors -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3 flex items-center gap-2">
              <Clapperboard class="w-4 h-4 text-cyan-400" />
              <span>{{ t('panorama.actorDirector') }}</span>
            </h3>
            <div class="space-y-2">
              <div
                v-for="ad in data.creators.actor_directors.slice(0, 8)"
                :key="ad.director_id"
                class="p-2.5 rounded-xl bg-surface-2 border border-line/50 flex items-center justify-between text-xs"
              >
                <div class="font-semibold text-fg">{{ ad.name }}</div>
                <div class="text-[11px] text-fg-3">
                  {{ t('panorama.directCount', { count: ad.directed_count }) }} · {{ t('panorama.actCount', { count: ad.acted_count }) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
