<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue';
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

function registerChart(chart: echarts.ECharts) {
  chartInstances.push(chart);
  return chart;
}

function disposeAllCharts() {
  chartInstances.forEach(c => c.dispose());
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
    await nextTick();
    renderCharts();
  } catch (err: any) {
    error.value = err?.message || '加载行业全景数据失败';
  } finally {
    loading.value = false;
  }
}

function renderCharts() {
  disposeAllCharts();
  if (!data.value) return;

  const d = data.value;

  // 1. Timeline Chart (Dual Y-Axis: Movies + Episodes vs Avg Duration)
  if (timelineChartRef.value) {
    const chart = registerChart(echarts.init(timelineChartRef.value));
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
        data: ['独立影片 (Movies)', '场景片段 (Episodes)', '平均片长 (分钟)'],
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
          name: '作品数量',
          nameTextStyle: { color: '#71717a' },
          splitLine: { lineStyle: { color: '#27272a' } },
          axisLabel: { color: '#a1a1aa' }
        },
        {
          type: 'value',
          name: '时长 (分)',
          nameTextStyle: { color: '#71717a' },
          splitLine: { show: false },
          axisLabel: { color: '#a1a1aa' }
        }
      ],
      series: [
        {
          name: '独立影片 (Movies)',
          type: 'bar',
          data: movies,
          itemStyle: { color: '#f59e0b', borderRadius: [3, 3, 0, 0] }
        },
        {
          name: '场景片段 (Episodes)',
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
          name: '平均片长 (分钟)',
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

  // 2. Studio Landscape Stream/Stacked Area Chart
  if (studioShareChartRef.value) {
    const chart = registerChart(echarts.init(studioShareChartRef.value));
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
        name: '年发行量',
        nameTextStyle: { color: '#71717a' },
        splitLine: { lineStyle: { color: '#27272a' } },
        axisLabel: { color: '#a1a1aa' }
      },
      series
    });
  }

  // 2b. HHI & CR5 Market Concentration
  if (hhiChartRef.value) {
    const chart = registerChart(echarts.init(hhiChartRef.value));
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
        data: ['CR5 头部五大厂市占率 (%)', 'HHI 市场集中度指数'],
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
          name: 'CR5 头部五大厂市占率 (%)',
          type: 'line',
          data: cr5,
          smooth: true,
          itemStyle: { color: '#3b82f6' },
          lineStyle: { width: 3 }
        },
        {
          name: 'HHI 市场集中度指数',
          type: 'bar',
          yAxisIndex: 1,
          data: hhi,
          itemStyle: { color: 'rgba(139, 92, 246, 0.4)', borderRadius: [3, 3, 0, 0] }
        }
      ]
    });
  }

  // 3. Build Distribution by Decade (Stacked Bar)
  if (buildChartRef.value) {
    const chart = registerChart(echarts.init(buildChartRef.value));
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
        name: '演员人次',
        nameTextStyle: { color: '#71717a' },
        splitLine: { lineStyle: { color: '#27272a' } },
        axisLabel: { color: '#a1a1aa' }
      },
      series
    });
  }

  // 3b. Body Hair Trend by Decade
  if (hairChartRef.value) {
    const chart = registerChart(echarts.init(hairChartRef.value));
    const decades = ['1970s', '1980s', '1990s', '2000s', '2010s', '2020s'];
    const hairs = ['Smooth / 无毛', 'Light / 适度体毛', 'Hairy / 浓密毛发'];
    const hairColors = ['#06b6d4', '#10b981', '#f59e0b'];

    const series = hairs.map((h, idx) => ({
      name: h,
      type: 'line',
      smooth: true,
      data: decades.map(dec => d.aesthetics.hair_by_decade[dec]?.[h] || 0),
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
        name: '出现频次',
        nameTextStyle: { color: '#71717a' },
        splitLine: { lineStyle: { color: '#27272a' } },
        axisLabel: { color: '#a1a1aa' }
      },
      series
    });
  }

  // 3c. Tattoo Adoption Rate
  if (tattooChartRef.value) {
    const chart = registerChart(echarts.init(tattooChartRef.value));
    const decades = d.aesthetics.tattoo_by_decade.map(t => t.decade);
    const pcts = d.aesthetics.tattoo_by_decade.map(t => t.percentage);

    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        formatter: '{b}: 纹身演员占比 {c}%',
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
        name: '占比 (%)',
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

  // 4. Tropes Theme River / Streamgraph
  if (tropesChartRef.value) {
    const chart = registerChart(echarts.init(tropesChartRef.value));
    const genres = d.tropes.top_genres.slice(0, 8);
    const years = d.tropes.theme_river.map(r => r.year);

    const series = genres.map((g, idx) => ({
      name: `${g.zh} (${g.en})`,
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
        data: genres.map(g => `${g.zh} (${g.en})`),
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
        name: '年发行量',
        nameTextStyle: { color: '#71717a' },
        splitLine: { lineStyle: { color: '#27272a' } },
        axisLabel: { color: '#a1a1aa' }
      },
      series
    });
  }

  // 5. Creator Survival Curve (Kaplan-Meier Retention)
  if (survivalChartRef.value) {
    const chart = registerChart(echarts.init(survivalChartRef.value));
    const years = d.creators.survival_curve.map(s => `${s.years} 年`);
    const pcts = d.creators.survival_curve.map(s => s.percentage);

    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        formatter: '{b}以上留存率: {c}%',
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
        name: '从业留存率 (%)',
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

  // 5b. Force-Directed Co-star Network
  if (networkChartRef.value && d.creators.network_graph.nodes.length > 0) {
    const chart = registerChart(echarts.init(networkChartRef.value));
    const g = d.creators.network_graph;

    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        backgroundColor: '#18181b',
        borderColor: '#27272a',
        textStyle: { color: '#f4f4f5' },
        formatter: (params: any) => {
          if (params.dataType === 'node') {
            return `<b>${params.data.name}</b><br/>总作品数: ${params.data.value}<br/>主要阵营: ${params.data.studio || '自由从业'}`;
          }
          if (params.dataType === 'edge') {
            return `合作作品数: ${params.data.value} 部`;
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

watch(currentTab, async () => {
  await nextTick();
  renderCharts();
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
              行业全景编年史与文化考古
            </h1>
            <p class="text-xs text-fg-4 mt-0.5">
              50+ 年宏观产业周期 · 媒介演进 · 身体美学 · 资本版图 · 创作者拓扑网络
            </p>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <button
          @click="loadData"
          class="px-3.5 py-1.5 rounded-xl border border-line bg-surface hover:bg-surface-2 text-fg-3 hover:text-fg text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer"
          :title="'刷新数据'"
        >
          <RefreshCw :class="['w-3.5 h-3.5', loading ? 'animate-spin' : '']" />
          <span>刷新大盘</span>
        </button>

        <div v-if="data?.meta" class="px-3 py-1 rounded-full bg-accent-fill/10 border border-accent/30 text-[11px] font-medium text-accent">
          收录 {{ data.meta.total_movies_covered.toLocaleString() }} 部 · {{ data.meta.years_span }}
        </div>
      </div>
    </div>

    <!-- Loading / Error States -->
    <div v-if="loading" class="py-24 text-center space-y-3">
      <RefreshCw class="w-8 h-8 mx-auto animate-spin text-accent" />
      <p class="text-sm text-fg-3">正在装配全行业半世纪多维元数据大盘...</p>
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
        重新加载
      </button>
    </div>

    <div v-else-if="data" class="space-y-8">
      <!-- 4 Historical Epochs Banner -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
        <div class="p-4 rounded-2xl bg-surface border border-line/80 space-y-1.5 hover:border-accent/40 transition">
          <div class="text-[10px] font-extrabold uppercase tracking-wider text-amber-500">1970s · 启蒙与长片</div>
          <div class="text-sm font-bold text-fg">胶片/影院启蒙期</div>
          <div class="text-xs text-fg-4 leading-relaxed">
            石墙运动后平权萌芽，叙事长片奠基。以传统经典剧情长片驱动，早期片长平均 65 分钟。
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-surface border border-line/80 space-y-1.5 hover:border-accent/40 transition">
          <div class="text-[10px] font-extrabold uppercase tracking-wider text-cyan-400">1980s · 录像带与健康分水岭</div>
          <div class="text-sm font-bold text-fg">VHS 繁荣与防护革命</div>
          <div class="text-xs text-fg-4 leading-relaxed">
            录像机走入私密家庭消费。80年代末艾滋危机重塑全行业，全面转向安全套拍摄与健康公约。
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-surface border border-line/80 space-y-1.5 hover:border-accent/40 transition">
          <div class="text-[10px] font-extrabold uppercase tracking-wider text-pink-400">1990s-2000s · 黄金实体期</div>
          <div class="text-sm font-bold text-fg">DVD 鼎盛与东欧狂潮</div>
          <div class="text-xs text-fg-4 leading-relaxed">
            实体光盘工业顶峰，片长达 100 分钟峰值。苏联解体后东欧美学崛起 (Bel Ami 等)，大厂寡头鼎立。
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-surface border border-line/80 space-y-1.5 hover:border-accent/40 transition">
          <div class="text-[10px] font-extrabold uppercase tracking-wider text-emerald-400">2010s-至今 · 算法与碎片化</div>
          <div class="text-sm font-bold text-fg">流媒体与单场景时代</div>
          <div class="text-xs text-fg-4 leading-relaxed">
            Tube 站点与订阅制颠覆实体，单场景 (Scenes) 爆炸式替代长片，OnlyFans 与微型创作者去中心化。
          </div>
        </div>
      </div>

      <!-- Navigation Segment Tabs -->
      <div class="flex items-center gap-1.5 border-b border-line pb-2 overflow-x-auto text-xs font-semibold">
        <button
          v-for="t in [
            { id: 'all', label: '全景大盘', icon: Activity },
            { id: 'timeline', label: '宏观产能与片长', icon: Film },
            { id: 'studios', label: '厂牌版图与垄断度', icon: Building2 },
            { id: 'aesthetics', label: '身体美学考古', icon: Flame },
            { id: 'tropes', label: '题材与母题消长', icon: Layers },
            { id: 'creators', label: '常青树与合作网络', icon: Users },
          ]"
          :key="t.id"
          @click="currentTab = (t.id as PanoramaTab)"
          :class="[
            'px-3.5 py-2 rounded-xl flex items-center gap-2 transition cursor-pointer shrink-0',
            currentTab === t.id
              ? 'bg-accent-fill/15 text-accent border border-accent/30 font-bold'
              : 'text-fg-4 hover:text-fg hover:bg-surface border border-transparent'
          ]"
        >
          <component :is="t.icon" class="w-4 h-4" />
          <span>{{ t.label }}</span>
        </button>
      </div>

      <!-- Tab Content 1: Macro Production & Duration Cliff -->
      <div v-show="currentTab === 'all' || currentTab === 'timeline'" class="space-y-4">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-bold text-fg flex items-center gap-2">
              <Film class="w-5 h-5 text-accent" />
              <span>宏观产能与媒介形态演进 (1970–2026)</span>
            </h2>
            <p class="text-xs text-fg-4">
              独立完整长片 (Feature Movies) 与单场景片段 (Scenes/Episodes) 的替代变迁，以及平均片长历史轨迹
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
              <span>各大厂牌兴衰浪潮与资本集中度</span>
            </h2>
            <p class="text-xs text-fg-4">
              Top 15 顶级制片厂历年年产量堆叠消长，以及市场集中度 (HHI / CR5)
            </p>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">顶级制片厂历年产能消长 (Theme River)</h3>
            <div ref="studioShareChartRef" class="w-full h-72"></div>
          </div>

          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">行业垄断与资本集中度 (HHI 指数 & CR5 份额)</h3>
            <div ref="hhiChartRef" class="w-full h-72"></div>
          </div>
        </div>

        <!-- Top Studios Hall of Fame -->
        <div class="p-5 rounded-2xl bg-surface border border-line space-y-4">
          <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">全时期十大传奇制片厂排行榜</h3>
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
                <span class="text-fg-4">发行作品</span>
                <span class="font-extrabold text-accent">{{ st.total_movies.toLocaleString() }} 部</span>
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
            <span>身体美学与时代性征考古 (Aesthetic Archaeology)</span>
          </h2>
          <p class="text-xs text-fg-4">
            体型结构、体毛风尚、纹身刺青从 1970 年代至 2020 年代的演变轨迹
          </p>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <!-- Build by Decade -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">体型 (Build) 年代谱系</h3>
            <div ref="buildChartRef" class="w-full h-64"></div>
            <p class="text-[11px] text-fg-4">
              90-00年代 Trim/Swimmer 苗条型高居榜首；10年代后 Muscular 肌肉与 Bear 熊族强势提升。
            </p>
          </div>

          <!-- Body Hair Trend -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">体毛风尚 (Body Hair) 周期流变</h3>
            <div ref="hairChartRef" class="w-full h-64"></div>
            <p class="text-[11px] text-fg-4">
              从 70-80 年代浑身浓密毛发硬汉，到千禧年前后 Smooth 白净风潮，再到近年粗犷大叔回潮。
            </p>
          </div>

          <!-- Tattoo Adoption -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">纹身刺青 (Tattoos) 普及率</h3>
            <div ref="tattooChartRef" class="w-full h-64"></div>
            <p class="text-[11px] text-fg-4">
              纹身从 1980 年代的地下亚文化，发展为 2020 年代超过 30% 演员的标志性主流美学符号。
            </p>
          </div>
        </div>
      </div>

      <!-- Tab Content 4: Tropes & Category Shifts -->
      <div v-show="currentTab === 'all' || currentTab === 'tropes'" class="space-y-4">
        <div>
          <h2 class="text-lg font-bold text-fg flex items-center gap-2">
            <Layers class="w-5 h-5 text-accent" />
            <span>核心题材与文化母题消长 (Narrative Tropes)</span>
          </h2>
          <p class="text-xs text-fg-4">
            Top 8 标志性细分流派题材历年发行消长河流图（已排除基础通用 Hardcore 分类）
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
              <span>时代叙事母题 (Narrative Tropes) 关键词演进谱系</span>
            </h3>
            <span class="text-[11px] text-fg-4">基于 6.3 万部影片标题与简介 NLP 词频提取</span>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-xs text-left">
              <thead>
                <tr class="border-b border-line text-fg-4">
                  <th class="py-2.5 px-3 font-semibold">母题与幻想模式</th>
                  <th class="py-2.5 px-2 text-right">1970s</th>
                  <th class="py-2.5 px-2 text-right">1980s</th>
                  <th class="py-2.5 px-2 text-right">1990s</th>
                  <th class="py-2.5 px-2 text-right">2000s</th>
                  <th class="py-2.5 px-2 text-right">2010s</th>
                  <th class="py-2.5 px-2 text-right">2020s</th>
                  <th class="py-2.5 px-3 text-right">历史峰值期</th>
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
            <span>标题命名学与简介文本熵演变 (Titology & Synopsis NLP)</span>
          </h3>
          <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            <div
              v-for="tito in data.tropes.titology_by_decade"
              :key="tito.decade"
              class="p-3 rounded-xl bg-surface-2 border border-line/60 space-y-1.5"
            >
              <div class="text-[11px] font-extrabold text-accent">{{ tito.decade }}</div>
              <div class="text-xs text-fg-3">
                标题均长: <span class="font-bold text-fg">{{ tito.avg_title_len }} 字</span>
              </div>
              <div class="text-xs text-fg-3">
                简介均长: <span class="font-bold text-fg">{{ tito.avg_desc_len }} 字</span>
              </div>
              <div class="text-[10px] text-fg-4">
                冒号副标题率: <span class="text-amber-400 font-semibold">{{ tito.colon_pct }}%</span>
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
            <span>创作者生态、生存时钟与常青树图鉴</span>
          </h2>
          <p class="text-xs text-fg-4">
            演员职业生命周期生存分析 (Kaplan-Meier)、百大常青树金榜、黄金搭档与合作网络星系
          </p>
        </div>

        <!-- Survival Analysis -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-2">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">从业者留存阶梯曲线 (Kaplan-Meier Survival)</h3>
            <div ref="survivalChartRef" class="w-full h-64"></div>
            <p class="text-[11px] text-fg-4">
              行业具备显著的「单年断崖」特征：近 60% 创作者仅活跃 1 年；仅 13% 演员能坚持跨越 10 年以上。
            </p>
          </div>

          <div class="p-5 rounded-2xl bg-surface border border-line space-y-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3">从业年限阶段分布占比</h3>
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
                  <span class="text-fg-4">{{ cnt.toLocaleString() }} 人</span>
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
              <span>核心演员合作星系力导向网络 (Co-star Top 60 Network)</span>
            </h3>
            <span class="text-[11px] text-fg-4">支持鼠标滚轮缩放与节点拖拽</span>
          </div>
          <div ref="networkChartRef" class="w-full h-96"></div>
        </div>

        <!-- Evergreen Performers Top 15 -->
        <div class="p-5 rounded-2xl bg-surface border border-line space-y-4">
          <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3 flex items-center gap-2">
            <Award class="w-4 h-4 text-amber-500" />
            <span>全行业常青树劳模演员光荣榜 (Top 15 Evergreen Icons)</span>
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
                  活跃 <span class="font-bold text-fg-2">{{ p.career_years }}</span> 年
                </div>
              </div>
              <div class="mt-3 pt-2 border-t border-line/40 flex items-center justify-between text-xs">
                <span class="text-[10px] text-fg-4 line-clamp-1" :title="p.primary_studio || ''">
                  {{ p.primary_studio || '自由从业' }}
                </span>
                <span class="font-extrabold text-accent shrink-0">{{ p.works_count }} 部</span>
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
              <span>黄金搭档 (合作出演次数最多组合)</span>
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
                <span class="font-extrabold text-accent">{{ d.common_movies }} 部合作</span>
              </div>
            </div>
          </div>

          <!-- Actor-Directors -->
          <div class="p-5 rounded-2xl bg-surface border border-line space-y-3">
            <h3 class="text-xs font-bold uppercase tracking-wider text-fg-3 flex items-center gap-2">
              <Clapperboard class="w-4 h-4 text-cyan-400" />
              <span>演而优则导 (演导双栖权力跃迁)</span>
            </h3>
            <div class="space-y-2">
              <div
                v-for="ad in data.creators.actor_directors.slice(0, 8)"
                :key="ad.director_id"
                class="p-2.5 rounded-xl bg-surface-2 border border-line/50 flex items-center justify-between text-xs"
              >
                <div class="font-semibold text-fg">{{ ad.name }}</div>
                <div class="text-[11px] text-fg-3">
                  导 <span class="font-bold text-accent">{{ ad.directed_count }}</span> 部 · 
                  演 <span class="font-bold text-accent">{{ ad.acted_count }}</span> 部
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
