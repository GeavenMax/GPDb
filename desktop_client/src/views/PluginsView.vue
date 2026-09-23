<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import {
  pluginsConfig, savePluginsConfig, type BtSearchConfig, DEFAULT_TRANSLATION_PROMPT
} from '../services/pluginManager';
import { TARGET_TRANSLATION_LANGUAGES, t } from '../i18n';
import { trophyStats } from '../services/trophySystem';
import { api, IS_TAURI } from '../api';
import type {
  TranslationStats, TranslationProfile, TranslationPreset
} from '../types';
import {
  Blocks, Compass, RefreshCw, Languages, Trophy,
  AlertCircle, CheckCircle2, ChevronRight, Loader2,
  Sparkles, Trash2, Volume2, VolumeX, RotateCcw,
  Download, SlidersHorizontal, Clock, Cpu,
  Globe, Play, ChevronDown, ChevronUp, Users
} from '@lucide/vue';
import {
  activeAiReport,
  isAiAnalyzing,
  aiAnalysisError,
  generateAiPersonaInsight,
  exportAiReportMarkdown,
  clearAiReport,
} from '../services/aiAnalysis';
import AiAnalysisModal from '../components/AiAnalysisModal.vue';
import {
  scraperState,
  isScrapingRunning,
  startScraperTask,
  stopScraperTask
} from '../services/scraper';
import {
  nextRunDescription,
  lastRunDescription,
  updateAutoSyncSchedule
} from '../services/autoSync';

const emit = defineEmits<{
  (e: 'open-trophies'): void;
  (e: 'refresh-movies'): void;
  (e: 'open-sync'): void;
  (e: 'open-environment-check'): void;
}>();

type PluginSubTab = 'all' | 'ai' | 'bt' | 'scraper' | 'translate' | 'trophy';
const activePluginTab = ref<PluginSubTab>('all');

// --- 1. Scraper state ---
const scraperMessage = ref('');
const scraperSuccess = ref<boolean | null>(null);

const showCustomScraperOptions = ref(false);
const scraperCustomMode = ref<'incremental' | 'bftv_catalog' | 'movies_boost' | 'movies_full' | 'performers_full'>('incremental');
const scraperCustomLimit = ref(1000);
const scraperCustomStartId = ref(1);
const scraperCustomEndId = ref(76000);

async function runScraper() {
  if (isScrapingRunning.value) return;
  scraperMessage.value = '正在安全检测并执行增量刮削...';
  scraperSuccess.value = null;

  try {
    await startScraperTask('incremental');
    scraperSuccess.value = true;
    scraperMessage.value = '增量同步任务已在后台极速启动，数据将实时自动同步！';
    emit('refresh-movies');
  } catch (err: any) {
    scraperSuccess.value = false;
    scraperMessage.value = `启动失败: ${err?.message || err || '服务未响应'}`;
  }
}

async function runCustomScraper() {
  if (isScrapingRunning.value) return;
  scraperMessage.value = '正在启动自定义刮削任务...';
  scraperSuccess.value = null;

  try {
    if (scraperCustomMode.value === 'incremental') {
      await startScraperTask('incremental');
    } else if (scraperCustomMode.value === 'bftv_catalog') {
      await startScraperTask('bftv_catalog');
    } else if (scraperCustomMode.value === 'movies_boost') {
      await startScraperTask('movies_boost', scraperCustomLimit.value);
    } else if (scraperCustomMode.value === 'movies_full') {
      await startScraperTask('movies_full', undefined, scraperCustomStartId.value, scraperCustomEndId.value);
    } else if (scraperCustomMode.value === 'performers_full') {
      await startScraperTask('performers_full');
    }
    scraperSuccess.value = true;
    scraperMessage.value = '刮削任务已在后台极速启动，数据将实时自动同步！';
    emit('refresh-movies');
  } catch (err: any) {
    scraperSuccess.value = false;
    scraperMessage.value = `启动失败: ${err?.message || err || '服务未响应'}`;
  }
}

function handleToggleAutoSync(enabled: boolean) {
  updateAutoSyncSchedule({ enabled });
}

function handleUpdateScheduleMode(mode: 'interval' | 'daily') {
  updateAutoSyncSchedule({ mode });
}

function handleUpdateIntervalHours(intervalHours: number) {
  updateAutoSyncSchedule({ intervalHours });
}

function handleUpdateDailyTime(dailyTime: string) {
  updateAutoSyncSchedule({ dailyTime });
}

// --- 2. BT Search config ---
function updateBtEngine(engine: BtSearchConfig['engine']) {
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      engine
    }
  });
}

const newBtTemplateName = ref('');
const newBtTemplateUrl = ref('');

function selectCustomBtTemplate(id: string) {
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      activeCustomId: id,
    }
  });
}

function addCustomBtTemplate() {
  const name = newBtTemplateName.value.trim();
  const template = newBtTemplateUrl.value.trim();
  if (!name || !template) return;
  const newTmpl = {
    id: `custom-${Date.now()}`,
    name,
    template: template.includes('{query}') ? template : `${template}{query}`,
  };
  const list = [...(pluginsConfig.value.btSearchConfig.customTemplates || []), newTmpl];
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      customTemplates: list,
      activeCustomId: newTmpl.id,
    }
  });
  newBtTemplateName.value = '';
  newBtTemplateUrl.value = '';
}

function deleteCustomBtTemplate(id: string) {
  const list = (pluginsConfig.value.btSearchConfig.customTemplates || []).filter(t => t.id !== id);
  let activeId = pluginsConfig.value.btSearchConfig.activeCustomId;
  if (activeId === id) {
    activeId = list[0]?.id || '';
  }
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      customTemplates: list,
      activeCustomId: activeId,
    }
  });
}

function toggleWebJump(key: 'bftvPerformerEnabled' | 'bftvMovieEnabled' | 'googleSearchEnabled') {
  savePluginsConfig({
    webJumpConfig: {
      ...pluginsConfig.value.webJumpConfig,
      [key]: !pluginsConfig.value.webJumpConfig[key],
    },
  });
}

// --- 3. AI Translation (Consolidated from Settings) ---
const translationStats = ref<TranslationStats | null>(null);
const isTranslating = ref(false);
const translateMsg = ref('');

const translateMode = ref<'single' | 'batch'>(
  (localStorage.getItem('gevi_translate_mode') as 'single' | 'batch') || 'single'
);

function setTranslateMode(mode: 'single' | 'batch') {
  translateMode.value = mode;
  localStorage.setItem('gevi_translate_mode', mode);
}

function updateTranslationTarget(lang: string) {
  savePluginsConfig({
    translationConfig: {
      ...pluginsConfig.value.translationConfig,
      targetLanguage: lang
    }
  });
}

function updateTranslationPrompt(e: Event) {
  const target = e.target as HTMLTextAreaElement;
  savePluginsConfig({
    translationConfig: {
      ...pluginsConfig.value.translationConfig,
      customPromptTemplate: target.value
    }
  });
}

function resetTranslationPrompt() {
  savePluginsConfig({
    translationConfig: {
      ...pluginsConfig.value.translationConfig,
      customPromptTemplate: DEFAULT_TRANSLATION_PROMPT
    }
  });
}

// Translation sources & profiles
const providerList = ref<TranslationProfile[]>([]);
const providerPresets = ref<TranslationPreset[]>([]);
const providerBusy = ref(false);
const providerMsg = ref('');
const providerTest = ref<{ ok: boolean; text: string } | null>(null);

const providerForm = reactive({
  open: false,
  editing: '',
  name: '',
  label: '',
  type: 'openai' as 'openai' | 'anthropic' | 'gemini',
  model: '',
  base_url: '',
  api_key: '',
});

async function loadTranslationStats() {
  try {
    translationStats.value = await api.getTranslationStats();
  } catch {}
}

async function loadProviders() {
  try {
    const data = await api.getTranslationProviders();
    providerList.value = data.profiles;
    providerPresets.value = data.presets;
  } catch {}
}

function applyPreset(preset: TranslationPreset) {
  providerForm.name = preset.id;
  providerForm.label = preset.label;
  providerForm.type = preset.type as any;
  providerForm.model = preset.model;
  providerForm.base_url = preset.base_url;
  providerForm.api_key = '';
}

function openProviderForm(profile?: TranslationProfile) {
  providerTest.value = null;
  providerMsg.value = '';
  providerForm.open = true;
  if (profile) {
    providerForm.editing = profile.name;
    providerForm.name = profile.name;
    providerForm.label = profile.label;
    providerForm.type = profile.type as any;
    providerForm.model = profile.model;
    providerForm.base_url = profile.base_url;
  } else {
    providerForm.editing = '';
    providerForm.name = '';
    providerForm.label = '';
    providerForm.type = 'openai';
    providerForm.model = '';
    providerForm.base_url = '';
  }
  providerForm.api_key = '';
}

async function saveProvider() {
  if (!providerForm.name.trim()) {
    providerMsg.value = '请填写配置名称';
    return;
  }
  providerBusy.value = true;
  providerMsg.value = '';
  const res = await api.saveTranslationProvider({
    name: providerForm.name.trim(),
    label: providerForm.label.trim() || providerForm.name.trim(),
    type: providerForm.type,
    model: providerForm.model.trim(),
    base_url: providerForm.base_url.trim(),
    api_key: providerForm.api_key.trim(),
    active: !providerForm.editing && providerList.value.length === 0,
  });
  providerBusy.value = false;
  if (res.success) {
    if (res.profiles) providerList.value = res.profiles;
    providerForm.open = false;
    providerMsg.value = '已保存配置';
    await loadTranslationStats();
  } else {
    providerMsg.value = res.error || '保存失败';
  }
}

async function activateProvider(name: string) {
  const res = await api.activateTranslationProvider(name);
  if (res.success && res.profiles) providerList.value = res.profiles;
  else providerMsg.value = res.error || '切换失败';
  await loadTranslationStats();
}

async function removeProvider(name: string) {
  const res = await api.deleteTranslationProvider(name);
  if (res.success && res.profiles) providerList.value = res.profiles;
  else providerMsg.value = res.error || '删除失败';
  await loadTranslationStats();
}

async function testProvider(name: string) {
  providerTest.value = { ok: true, text: '测试连接中...' };
  const res = await api.testTranslationProvider(name);
  if (res.success) {
    providerTest.value = {
      ok: true,
      text: `✓ 连接成功 (${res.profile || name})\n输入：${res.source}\n译文：${res.result}`,
    };
  } else {
    providerTest.value = {
      ok: false,
      text: `✗ 测试失败：${res.error || '未收到回复'}`,
    };
  }
}

async function handleRunTranslation(limit: number | null) {
  isTranslating.value = true;
  translateMsg.value = '';
  const res = await api.runTranslation(limit);
  if (res.success) {
    translateMsg.value = res.message || '翻译任务已在后台启动';
    const poll = setInterval(async () => {
      await loadTranslationStats();
      if (!translationStats.value?.running) {
        clearInterval(poll);
        isTranslating.value = false;
        translateMsg.value = '翻译完成！';
        emit('refresh-movies');
      }
    }, 3000);
  } else {
    translateMsg.value = res.error || '启动失败';
    isTranslating.value = false;
  }
}

// Glossary translation
const glossaryBusy = ref(false);
const glossaryMsg = ref('');
const glossaryError = ref('');

async function handleRunGlossary(dryRun: boolean) {
  glossaryBusy.value = true;
  glossaryMsg.value = '';
  glossaryError.value = '';
  const res = await api.runGlossaryTranslation(dryRun);
  if (res.success) {
    if (dryRun) {
      glossaryMsg.value = `待翻译术语 ${res.pending} 条（试跑测试，未写入）`;
    } else {
      glossaryMsg.value = `术语表已更新：本次新增 ${res.translated} 条，累计 ${res.total} 条` +
        (res.failed ? `，失败 ${res.failed} 条` : '');
    }
  } else {
    glossaryError.value = res.error || '术语表翻译失败';
  }
  glossaryBusy.value = false;
}

// --- 4. Trophies Plugin ---
import TrophyResetModal from '../components/TrophyResetModal.vue';

const showTrophyResetModal = ref(false);

function toggleTrophySound() {
  savePluginsConfig({ trophiesSoundEnabled: !pluginsConfig.value.trophiesSoundEnabled });
}

function handleResetTrophies() {
  showTrophyResetModal.value = true;
}

// --- 5. AI Taste & Persona Insight Plugin ---
const showAiPromptCustomizer = ref(false);
const customAiPromptSuffix = ref('');
const isReportExpanded = ref(true);
const aiSuccessMsg = ref('');

const activeProfile = computed(() => providerList.value.find(p => p.active));
const showAiModal = ref(false);

async function handleRunAiAnalysis() {
  aiSuccessMsg.value = '';
  showAiModal.value = true;
  try {
    await generateAiPersonaInsight(customAiPromptSuffix.value);
    aiSuccessMsg.value = '影迷偏好洞察已生成并同步至本地！';
    setTimeout(() => {
      showAiModal.value = false;
      isReportExpanded.value = true;
    }, 1000);
  } catch (err: any) {
    // Error details are displayed inside the modal
  }
}

function handleExportAiReport() {
  if (activeAiReport.value) {
    exportAiReportMarkdown(activeAiReport.value);
  }
}

function handleClearAiReport() {
  clearAiReport();
  aiSuccessMsg.value = '';
}

const reportSections = computed(() => {
  if (!activeAiReport.value?.fullMarkdown) return [];
  const cleanMd = activeAiReport.value.fullMarkdown.replace(/```json[\s\S]*?```/, '').trim();
  const rawSections = cleanMd.split(/(?=###\s+)/g);
  return rawSections.map(s => {
    const lines = s.trim().split('\n');
    const titleMatch = lines[0].match(/^###\s+(.*)/);
    const title = titleMatch ? titleMatch[1].trim() : '';
    const body = titleMatch ? lines.slice(1).join('\n').trim() : lines.join('\n').trim();
    return { title, body };
  }).filter(s => s.title || s.body);
});

onMounted(() => {
  loadTranslationStats();
  loadProviders();
});
</script>

<template>
  <div class="space-y-8 max-w-5xl mx-auto pb-16 animate-fade-in text-fg">
    <!-- Header -->
    <div class="border-b border-line pb-6">
      <div class="flex items-center gap-3">
        <div class="p-2.5 rounded-2xl bg-accent-fill/10 text-accent border border-accent-fill/20">
          <Blocks class="w-6 h-6" />
        </div>
        <div>
          <h1 class="text-2xl md:text-3xl font-extrabold text-fg tracking-tight">
            {{ t('plugins.title') }}
          </h1>
          <p class="text-xs text-fg-4 mt-1">
            {{ t('plugins.subtitle') }}
          </p>
        </div>
      </div>
    </div>

    <!-- Subcategory Capsule Switcher -->
    <div class="flex items-center gap-2 flex-wrap">
      <button
        v-for="tab in [
          { id: 'all', label: '全部插件', count: 5, icon: Blocks },
          { id: 'ai', label: 'AI 影迷偏好洞察', count: 1, icon: Sparkles },
          { id: 'bt', label: t('plugins.resourceSearch', '资源搜索与扩展'), count: 1, icon: Compass },
          { id: 'scraper', label: '数据搜刮', count: 1, icon: RefreshCw },
          { id: 'translate', label: 'AI 翻译引擎', count: 1, icon: Languages },
          { id: 'trophy', label: '典藏成就奖杯', count: 1, icon: Trophy },
        ]"
        :key="tab.id"
        @click="activePluginTab = (tab.id as PluginSubTab)"
        :class="[
          'px-3.5 py-1.5 rounded-xl text-xs font-bold border transition flex items-center gap-2 cursor-pointer shadow-xs',
          activePluginTab === tab.id
            ? 'bg-accent-fill text-on-fill border-accent shadow-sm'
            : 'bg-surface-2/70 text-fg-3 border-line hover:text-fg hover:bg-surface-2'
        ]"
      >
        <component :is="tab.icon" class="w-3.5 h-3.5" />
        <span>{{ tab.label }}</span>
        <span
          v-if="tab.id === 'all'"
          class="text-[10px] px-1.5 py-0.2 rounded-full font-mono font-extrabold"
          :class="activePluginTab === tab.id ? 'bg-black/20 text-on-fill' : 'bg-surface-3 text-fg-4'"
        >
          {{ tab.count }}
        </span>
      </button>
    </div>

    <!-- Plugins List -->
    <div class="space-y-6">
      <!-- 0. AI Persona & Taste Insight Plugin -->
      <div
        v-if="activePluginTab === 'all' || activePluginTab === 'ai'"
        class="p-6 rounded-3xl bg-surface/80 border border-line space-y-5 shadow-sm"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div class="p-3 rounded-2xl bg-gradient-to-br from-indigo-500/20 to-purple-500/20 text-indigo-400 border border-indigo-500/30">
              <Sparkles class="w-6 h-6" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-fg">大模型 AI 影迷偏好洞察</h3>
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 font-bold border border-indigo-500/20">AI Persona v1.0</span>
              </div>
              <p class="text-xs text-fg-4 mt-0.5">将本地收藏、标记和评分记录转化为深层影视流派洞察，生成专属影迷艺术画像报告</p>
            </div>
          </div>

          <!-- Switch -->
          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              v-model="pluginsConfig.aiInsightEnabled"
              @change="savePluginsConfig({ aiInsightEnabled: pluginsConfig.aiInsightEnabled })"
              class="sr-only peer"
            />
            <div class="w-11 h-6 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-fill"></div>
          </label>
        </div>

        <div v-if="pluginsConfig.aiInsightEnabled" class="space-y-4 pt-2 border-t border-line/60">
          <!-- Active model profile info / warning -->
          <div v-if="!activeProfile" class="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-300 flex items-center justify-between gap-3">
            <div class="flex items-center gap-2.5">
              <AlertCircle class="w-4 h-4 shrink-0 text-amber-400" />
              <span>尚未激活大模型服务。请在下方「大模型 AI 翻译引擎」中配置并激活 API 来源（支持 OpenAI、DeepSeek、Claude、Gemini 等）。</span>
            </div>
            <button
              @click="activePluginTab = 'translate'"
              class="px-2.5 py-1 text-xs font-bold rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-200 border border-amber-500/30 transition shrink-0 cursor-pointer"
            >
              前往配置
            </button>
          </div>
          <div v-else class="flex items-center justify-between text-xs px-3.5 py-2 rounded-xl bg-surface-2/60 border border-line/40 text-fg-3">
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
              <span>已绑定活跃大模型：<strong class="text-fg font-medium">{{ activeProfile.label || activeProfile.name }}</strong> ({{ activeProfile.model || activeProfile.type }})</span>
            </div>
            <button
              @click="showAiPromptCustomizer = !showAiPromptCustomizer"
              class="text-xs text-accent hover:underline flex items-center gap-1 font-medium cursor-pointer"
            >
              <SlidersHorizontal class="w-3.5 h-3.5" />
              <span>{{ showAiPromptCustomizer ? '收起自定义关注点' : '自定义偏好提示词' }}</span>
            </button>
          </div>

          <!-- Custom prompt drawer -->
          <div v-if="showAiPromptCustomizer" class="p-3.5 rounded-2xl bg-surface-2 border border-line/60 space-y-2 text-xs animate-fade-in">
            <label class="block font-medium text-fg-2">自定义分析侧重或特别偏好（可选）：</label>
            <input
              type="text"
              v-model="customAiPromptSuffix"
              placeholder="例如：特别关注我对黄金年代复古欧美制片厂或小众剧情片的偏好，以及推荐偏好..."
              class="w-full px-3 py-2 rounded-xl bg-surface-3/80 border border-line text-fg text-xs focus:outline-none focus:border-accent"
            />
          </div>

          <!-- Actions & Status -->
          <div class="flex flex-wrap items-center justify-between gap-3 pt-1">
            <div class="flex items-center gap-2">
              <button
                @click="handleRunAiAnalysis"
                :disabled="isAiAnalyzing"
                class="px-4 py-2 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 hover:from-indigo-600 hover:to-purple-700 text-white font-bold text-xs flex items-center gap-2 shadow-md transition disabled:opacity-50 cursor-pointer"
              >
                <Loader2 v-if="isAiAnalyzing" class="w-4 h-4 animate-spin" />
                <Sparkles v-else class="w-4 h-4" />
                <span>{{ isAiAnalyzing ? '正在深度研判全库观影数据...' : (activeAiReport ? '重新生成偏好画像' : '一键生成影迷偏好画像') }}</span>
              </button>

              <button
                v-if="activeAiReport"
                @click="handleExportAiReport"
                class="px-3 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 border border-line text-xs font-medium flex items-center gap-1.5 transition cursor-pointer"
              >
                <Download class="w-3.5 h-3.5" />
                <span>导出报告 (.md)</span>
              </button>

              <button
                v-if="activeAiReport"
                @click="handleClearAiReport"
                class="px-3 py-2 rounded-xl text-xs text-fg-4 hover:text-red-400 hover:bg-red-500/10 transition cursor-pointer"
              >
                清空画像
              </button>
            </div>

            <div v-if="activeAiReport" class="text-[11px] text-fg-4">
              上次生成于 {{ new Date(activeAiReport.generatedAt).toLocaleString() }}
            </div>
          </div>

          <!-- Error or success banner -->
          <div v-if="aiAnalysisError" class="p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-xs text-red-400 flex items-center gap-2">
            <AlertCircle class="w-4 h-4 shrink-0" />
            <span>{{ aiAnalysisError }}</span>
          </div>
          <div v-if="aiSuccessMsg" class="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-xs text-emerald-400 flex items-center gap-2">
            <CheckCircle2 class="w-4 h-4 shrink-0" />
            <span>{{ aiSuccessMsg }}</span>
          </div>

          <!-- Generated AI Persona Report Card -->
          <div v-if="activeAiReport" class="rounded-2xl bg-surface-2/60 border border-line p-5 space-y-4">
            <!-- Archetype Header -->
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-line/40">
              <div class="space-y-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="text-xs px-2.5 py-0.5 rounded-full bg-gradient-to-r from-indigo-500/20 to-purple-500/20 text-indigo-300 font-extrabold border border-indigo-500/30">
                    影迷专属原型
                  </span>
                  <h4 class="text-lg font-black text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 via-purple-300 to-pink-400">
                    {{ activeAiReport.archetype }}
                  </h4>
                </div>
                <p class="text-xs text-fg-3 italic">
                  “{{ activeAiReport.summary }}”
                </p>
              </div>

              <!-- Keyword chips -->
              <div class="flex items-center gap-1.5 flex-wrap">
                <span
                  v-for="kw in activeAiReport.keywords"
                  :key="kw"
                  class="text-[11px] px-2 py-0.5 rounded-lg bg-surface-3 text-fg-2 border border-line/60 font-medium"
                >
                  #{{ kw }}
                </span>
              </div>
            </div>

            <!-- Report Sections Accordion / Container -->
            <div class="space-y-3 pt-1">
              <div
                v-for="(sec, idx) in reportSections"
                :key="idx"
                class="p-4 rounded-xl bg-surface-3/40 border border-line/40 space-y-2"
              >
                <h5 class="text-sm font-bold text-fg flex items-center gap-2">
                  <span class="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
                  {{ sec.title || `分析版块 ${idx + 1}` }}
                </h5>
                <div class="text-xs text-fg-2 leading-relaxed whitespace-pre-line pl-3.5 border-l border-indigo-500/20">
                  {{ sec.body }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 1. BT / Magnet Search Plugin -->
      <div
        v-if="activePluginTab === 'all' || activePluginTab === 'bt'"
        class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div class="p-3 rounded-2xl bg-sky-500/10 text-sky-400 border border-sky-500/20">
              <Compass class="w-6 h-6" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-fg">{{ t('plugins.resourceSearch', '资源搜索与外部扩展') }}</h3>
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-sky-500/10 text-sky-400 font-bold border border-sky-500/20">v2.0</span>
              </div>
              <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.resourceSearchDesc', '在影片、演员及分集页面一键跳转至主流 BT 磁力站点或外部资料网站检索相关资源') }}</p>
            </div>
          </div>

          <!-- Switch -->
          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              v-model="pluginsConfig.resourceSearchEnabled"
              @change="savePluginsConfig({ resourceSearchEnabled: pluginsConfig.resourceSearchEnabled })"
              class="sr-only peer"
            />
            <div class="w-11 h-6 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-fill"></div>
          </label>
        </div>

        <!-- Configuration options (if enabled) -->
        <div v-if="pluginsConfig.resourceSearchEnabled" class="pt-4 border-t border-line/60 space-y-6 animate-fade-in">
          <!-- 1. BT Magnet Search Engines -->
          <div class="space-y-3">
            <div class="flex items-center gap-2">
              <Compass class="w-4 h-4 text-sky-400" />
              <div class="text-xs font-bold text-fg">BT 磁力搜索引擎预设：</div>
            </div>
            <div class="flex gap-2 flex-wrap">
              <button
                v-for="eng in [
                  { id: 'bt4g', label: 'BT4G (默认推荐)' },
                  { id: 'btsearch', label: 'BTSearch (love)' },
                  { id: 'sukebei', label: 'Sukebei (Nyaa)' },
                  { id: '1337x', label: '1337x' },
                  { id: 'torrentgalaxy', label: 'TorrentGalaxy' },
                  { id: 'custom', label: '自定义多模版库' }
                ]"
                :key="eng.id"
                @click="updateBtEngine(eng.id as any)"
                :class="[
                  'px-3 py-1.5 rounded-xl text-xs font-semibold border transition cursor-pointer',
                  pluginsConfig.btSearchConfig.engine === eng.id
                    ? 'bg-accent-fill text-on-fill border-accent shadow'
                    : 'bg-surface-2/80 text-fg-3 border-line hover:bg-surface-3 hover:text-fg'
                ]"
              >
                {{ eng.label }}
              </button>
            </div>

            <!-- Multiple Custom Templates Management -->
            <div v-if="pluginsConfig.btSearchConfig.engine === 'custom'" class="pt-2 space-y-3">
              <div class="text-[11px] text-fg-4">
                已保存的自定义检索模板列表（支持点击选中作为当前默认，搜索时将把 {query} 自动替换为影片名）：
              </div>

              <div class="space-y-2">
                <div
                  v-for="tmpl in (pluginsConfig.btSearchConfig.customTemplates || [])"
                  :key="tmpl.id"
                  @click="selectCustomBtTemplate(tmpl.id)"
                  class="p-3 rounded-2xl border transition flex items-center justify-between gap-3 cursor-pointer"
                  :class="pluginsConfig.btSearchConfig.activeCustomId === tmpl.id
                    ? 'bg-accent-fill/10 border-accent text-fg shadow-sm'
                    : 'bg-surface-2/70 border-line hover:border-line-strong text-fg-3 hover:text-fg'"
                >
                  <div class="flex items-center gap-3 min-w-0">
                    <div
                      class="w-3.5 h-3.5 rounded-full border flex items-center justify-center shrink-0"
                      :class="pluginsConfig.btSearchConfig.activeCustomId === tmpl.id
                        ? 'border-accent bg-accent'
                        : 'border-line-strong bg-transparent'"
                    >
                      <div v-if="pluginsConfig.btSearchConfig.activeCustomId === tmpl.id" class="w-1.5 h-1.5 rounded-full bg-white"></div>
                    </div>
                    <div class="min-w-0">
                      <div class="text-xs font-bold truncate">{{ tmpl.name }}</div>
                      <div class="text-[10px] text-fg-4 font-mono truncate mt-0.5">{{ tmpl.template }}</div>
                    </div>
                  </div>

                  <button
                    @click.stop="deleteCustomBtTemplate(tmpl.id)"
                    class="p-1.5 rounded-lg text-fg-4 hover:text-danger hover:bg-danger-fill/10 transition shrink-0 cursor-pointer"
                    title="删除该模板"
                  >
                    <Trash2 class="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              <!-- Add new template form -->
              <div class="p-4 rounded-2xl bg-sunken/60 border border-line-strong/60 space-y-3">
                <div class="text-xs font-bold text-fg-2">添加新的自定义搜索站点：</div>
                <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
                  <input
                    v-model="newBtTemplateName"
                    placeholder="站点名称 (如: RuTracker)"
                    class="bg-surface border border-line rounded-xl px-3 py-2 text-xs text-fg outline-none focus:border-accent"
                  />
                  <input
                    v-model="newBtTemplateUrl"
                    placeholder="检索 URL 模版 (含 {query})"
                    class="sm:col-span-2 bg-surface border border-line rounded-xl px-3 py-2 text-xs text-fg font-mono outline-none focus:border-accent"
                  />
                </div>
                <div class="flex justify-end">
                  <button
                    @click="addCustomBtTemplate"
                    :disabled="!newBtTemplateName.trim() || !newBtTemplateUrl.trim()"
                    class="px-4 py-1.5 rounded-xl bg-accent-fill text-on-fill text-xs font-bold hover:bg-accent-fill/90 transition disabled:opacity-40 cursor-pointer"
                  >
                    添加模版
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 2. Web Jump External Sites -->
          <div class="space-y-3 pt-3 border-t border-line/60">
            <div class="flex items-center gap-2">
              <Blocks class="w-4 h-4 text-amber-400" />
              <div class="text-xs font-bold text-fg">外部网站资料跳转与直达按钮：</div>
            </div>
            <p class="text-xs text-fg-4">
              启用后将在对应条目（影片或演员）页面展示一键直达按钮，与 BT 搜索按钮同排展示。各按钮可独立启用或关闭，互不干扰。
            </p>

            <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3 pt-1">
              <!-- BFTV Performer Jump -->
              <div class="p-4 rounded-2xl bg-surface-2/60 border border-line flex flex-col justify-between gap-3">
                <div class="space-y-1">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-bold text-fg flex items-center gap-1.5">
                      <span class="w-2 h-2 rounded-full bg-amber-400"></span>
                      在 BFTV 搜索演员资料
                    </span>
                    <input
                      type="checkbox"
                      :checked="pluginsConfig.webJumpConfig.bftvPerformerEnabled"
                      @change="toggleWebJump('bftvPerformerEnabled')"
                      class="rounded accent-accent cursor-pointer"
                    />
                  </div>
                  <p class="text-[11px] text-fg-4 leading-relaxed">
                    在演员档案页展示按钮，直达 BoyfriendTV 对应艺名的专页与高级搜索页。
                  </p>
                </div>
                <div class="text-[10px] text-fg-5 font-mono truncate">
                  boyfriendtv.com/pornstars/?q={name}
                </div>
              </div>

              <!-- BFTV Movie Jump -->
              <div class="p-4 rounded-2xl bg-surface-2/60 border border-line flex flex-col justify-between gap-3">
                <div class="space-y-1">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-bold text-fg flex items-center gap-1.5">
                      <span class="w-2 h-2 rounded-full bg-orange-400"></span>
                      在 BFTV 搜索影片资料
                    </span>
                    <input
                      type="checkbox"
                      :checked="pluginsConfig.webJumpConfig.bftvMovieEnabled"
                      @change="toggleWebJump('bftvMovieEnabled')"
                      class="rounded accent-accent cursor-pointer"
                    />
                  </div>
                  <p class="text-[11px] text-fg-4 leading-relaxed">
                    在影片详情页展示按钮，自动提取影片主标题（剥离序号与副标题）后精准检索。
                  </p>
                </div>
                <div class="text-[10px] text-fg-5 font-mono truncate">
                  boyfriendtv.com/search/?q={clean_title}
                </div>
              </div>

              <!-- Google Web Jump -->
              <div class="p-4 rounded-2xl bg-surface-2/60 border border-line flex flex-col justify-between gap-3">
                <div class="space-y-1">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-bold text-fg flex items-center gap-1.5">
                      <span class="w-2 h-2 rounded-full bg-blue-400"></span>
                      Google 快速全网搜索
                    </span>
                    <input
                      type="checkbox"
                      :checked="pluginsConfig.webJumpConfig.googleSearchEnabled"
                      @change="toggleWebJump('googleSearchEnabled')"
                      class="rounded accent-accent cursor-pointer"
                    />
                  </div>
                  <p class="text-[11px] text-fg-4 leading-relaxed">
                    以条目原名直接在 Google 进行全网检索，方便查找第三方影评与演员履历。
                  </p>
                </div>
                <div class="text-[10px] text-fg-5 font-mono truncate">
                  google.com/search?q={query}
                </div>
              </div>
            </div>

            <!-- Future Roadmap Notice -->
            <div class="p-3 rounded-xl bg-accent-fill/5 border border-accent-fill/15 flex items-center gap-2 text-xs text-fg-3">
              <Sparkles class="w-4 h-4 text-accent shrink-0" />
              <span>待办清单：后续版本将持续支持接入更多垂直百科站点（如 IAFD、AEBN、Radar 等）。</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. Custom Scraper Plugin -->
      <div
        v-if="activePluginTab === 'all' || activePluginTab === 'scraper'"
        class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div
              class="p-3 rounded-2xl transition shadow"
              :class="isScrapingRunning
                ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'"
            >
              <RefreshCw class="w-6 h-6" :class="{ 'animate-spin': isScrapingRunning }" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-fg">{{ t('plugins.customScraper') }}</h3>
                <span
                  v-if="isScrapingRunning"
                  class="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 font-bold border border-amber-500/30 flex items-center gap-1"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"></span>
                  同步中
                </span>
                <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 font-bold border border-emerald-500/20">内置</span>
              </div>
              <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.customScraperDesc') }}</p>
            </div>
          </div>

          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              v-model="pluginsConfig.customScraperEnabled"
              @change="savePluginsConfig({ customScraperEnabled: pluginsConfig.customScraperEnabled })"
              class="sr-only peer"
            />
            <div class="w-11 h-6 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-fill"></div>
          </label>
        </div>

        <div v-if="pluginsConfig.customScraperEnabled" class="pt-4 border-t border-line/60 space-y-3.5">
          <!-- Control Actions -->
          <div class="flex items-center gap-3 flex-wrap">
            <button
              @click="runScraper"
              :disabled="isScrapingRunning"
              class="px-4 py-2 rounded-xl bg-accent-fill text-on-fill text-xs font-bold flex items-center gap-2 hover:bg-accent-fill/90 transition shadow disabled:opacity-50 cursor-pointer"
            >
              <Loader2 v-if="isScrapingRunning" class="w-3.5 h-3.5 animate-spin" />
              <RefreshCw v-else class="w-3.5 h-3.5" />
              <span>{{ isScrapingRunning ? '增量同步运行中...' : '一键启动增量刮削' }}</span>
            </button>

            <button
              v-if="isScrapingRunning"
              @click="stopScraperTask"
              class="px-3 py-2 rounded-xl bg-rose-500/15 text-rose-300 hover:bg-rose-500/25 border border-rose-500/30 text-xs font-bold transition cursor-pointer"
            >
              中止任务
            </button>

            <button
              @click="emit('open-sync')"
              class="px-4 py-2 rounded-xl bg-surface-2 text-fg hover:bg-surface-3 border border-line text-xs font-bold flex items-center gap-2 transition cursor-pointer"
              title="打开全功能刮削与数据同步中心，支持全量、逆序及选段抓取"
            >
              <Compass class="w-3.5 h-3.5 text-indigo-400" />
              <span>打开同步控制中心</span>
            </button>

            <button
              @click="emit('open-environment-check')"
              class="px-3.5 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg text-xs font-bold border border-line flex items-center gap-1.5 transition cursor-pointer"
              title="校验 Python 3 解释器、SQLite 与本地影库环境就绪情况"
            >
              <Cpu class="w-3.5 h-3.5 text-sky-400" />
              <span>运行环境自检</span>
            </button>

            <button
              @click="showCustomScraperOptions = !showCustomScraperOptions"
              class="px-3.5 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg text-xs font-bold border flex items-center gap-1.5 transition cursor-pointer"
              :class="showCustomScraperOptions ? 'border-accent text-accent' : 'border-line'"
              title="自定义刮削策略与范围参数"
            >
              <SlidersHorizontal class="w-3.5 h-3.5 text-amber-400" />
              <span>高度自定义执行选项</span>
              <ChevronUp v-if="showCustomScraperOptions" class="w-3 h-3 ml-0.5" />
              <ChevronDown v-else class="w-3 h-3 ml-0.5" />
            </button>

            <span class="text-[11px] text-fg-4">
              自动检索 /newm、/newp、/newe，实时增量整合影片、演职员与分集
            </span>
          </div>

          <!-- Advanced Custom Options Panel -->
          <div v-if="showCustomScraperOptions" class="p-4 rounded-2xl bg-surface-2/70 border border-line space-y-3.5 animate-fade-in text-xs">
            <div class="flex items-center justify-between">
              <div class="font-bold text-fg flex items-center gap-2">
                <SlidersHorizontal class="w-4 h-4 text-accent" />
                <span>自定义刮削与同步参数</span>
              </div>
              <span class="text-[11px] text-fg-4">选择模式并根据需要调整参数</span>
            </div>

            <!-- Mode selection pills -->
            <div class="space-y-1.5">
              <div class="text-[11px] font-semibold text-fg-3">执行策略：</div>
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
                <button
                  type="button"
                  @click="scraperCustomMode = 'incremental'"
                  class="p-2.5 rounded-xl border text-left transition cursor-pointer"
                  :class="scraperCustomMode === 'incremental' ? 'bg-indigo-500/15 border-indigo-500 text-indigo-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
                >
                  <div class="font-bold text-xs flex items-center gap-1.5">
                    <RefreshCw class="w-3.5 h-3.5" />
                    <span>增量极速同步</span>
                  </div>
                  <div class="text-[10px] text-fg-4 mt-0.5">抓取近期新增影片与演员</div>
                </button>

                <button
                  type="button"
                  @click="scraperCustomMode = 'bftv_catalog'"
                  class="p-2.5 rounded-xl border text-left transition cursor-pointer"
                  :class="scraperCustomMode === 'bftv_catalog' ? 'bg-emerald-500/15 border-emerald-500 text-emerald-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
                >
                  <div class="font-bold text-xs flex items-center gap-1.5">
                    <Globe class="w-3.5 h-3.5" />
                    <span>BFTV 主页秒级关联</span>
                  </div>
                  <div class="text-[10px] text-fg-4 mt-0.5">3秒注入12,000+演员直达链接</div>
                </button>

                <button
                  type="button"
                  @click="scraperCustomMode = 'movies_boost'"
                  class="p-2.5 rounded-xl border text-left transition cursor-pointer"
                  :class="scraperCustomMode === 'movies_boost' ? 'bg-amber-500/15 border-amber-500 text-amber-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
                >
                  <div class="font-bold text-xs flex items-center gap-1.5">
                    <Sparkles class="w-3.5 h-3.5" />
                    <span>热门新片逆序爬取</span>
                  </div>
                  <div class="text-[10px] text-fg-4 mt-0.5">倒序抓取精选热门库</div>
                </button>

                <button
                  type="button"
                  @click="scraperCustomMode = 'movies_full'"
                  class="p-2.5 rounded-xl border text-left transition cursor-pointer"
                  :class="scraperCustomMode === 'movies_full' ? 'bg-purple-500/15 border-purple-500 text-purple-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
                >
                  <div class="font-bold text-xs flex items-center gap-1.5">
                    <Download class="w-3.5 h-3.5" />
                    <span>指定 ID 范围抓取</span>
                  </div>
                  <div class="text-[10px] text-fg-4 mt-0.5">按自定义起止 ID 批量爬取</div>
                </button>

                <button
                  type="button"
                  @click="scraperCustomMode = 'performers_full'"
                  class="p-2.5 rounded-xl border text-left transition cursor-pointer"
                  :class="scraperCustomMode === 'performers_full' ? 'bg-rose-500/15 border-rose-500 text-rose-300' : 'bg-surface border-line text-fg-4 hover:text-fg'"
                >
                  <div class="font-bold text-xs flex items-center gap-1.5">
                    <Users class="w-3.5 h-3.5" />
                    <span>演员全量资料补齐</span>
                  </div>
                  <div class="text-[10px] text-fg-4 mt-0.5">补齐已知演员身材与头像</div>
                </button>
              </div>
            </div>

            <!-- Dynamic Parameters -->
            <div v-if="scraperCustomMode === 'movies_boost'" class="p-3 rounded-xl bg-surface/60 border border-line flex items-center gap-3">
              <span class="text-fg-3">抓取数量上限：</span>
              <input
                type="number"
                v-model.number="scraperCustomLimit"
                min="100"
                max="10000"
                step="100"
                class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
              />
              <span class="text-[11px] text-fg-4">部影片（推荐 500 ~ 2,000 部）</span>
            </div>

            <div v-else-if="scraperCustomMode === 'movies_full'" class="p-3 rounded-xl bg-surface/60 border border-line flex items-center gap-3 flex-wrap">
              <div class="flex items-center gap-1.5">
                <span class="text-fg-3">起始 ID：</span>
                <input
                  type="number"
                  v-model.number="scraperCustomStartId"
                  min="1"
                  class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
                />
              </div>
              <div class="flex items-center gap-1.5">
                <span class="text-fg-3">结束 ID：</span>
                <input
                  type="number"
                  v-model.number="scraperCustomEndId"
                  min="1"
                  class="px-2.5 py-1 rounded-lg bg-surface border border-line text-fg font-mono font-bold w-24 text-xs"
                />
              </div>
              <span class="text-[11px] text-fg-4">当前影库上限约 #76,000</span>
            </div>

            <div v-else-if="scraperCustomMode === 'bftv_catalog'" class="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-[11px] flex items-center gap-2">
              <Globe class="w-4 h-4 shrink-0" />
              <span>通过 BoyfriendTV 全网目录反向匹配，自动剥离别名后缀，3 秒内将万余位演员直达链接写入数据库！</span>
            </div>

            <!-- Launch button -->
            <div class="flex items-center justify-end gap-3 pt-1">
              <button
                @click="runCustomScraper"
                :disabled="isScrapingRunning"
                class="px-4 py-2 rounded-xl bg-accent-fill text-on-fill font-bold text-xs flex items-center gap-1.5 hover:bg-accent-fill/90 transition shadow cursor-pointer disabled:opacity-50"
              >
                <Loader2 v-if="isScrapingRunning" class="w-3.5 h-3.5 animate-spin" />
                <Play v-else class="w-3.5 h-3.5" />
                <span>立即按自定义配置启动</span>
              </button>
            </div>
          </div>

          <!-- Live Progress Banner when running -->
          <div v-if="isScrapingRunning" class="p-3.5 rounded-2xl bg-surface-2/70 border border-line/80 space-y-2 text-xs">
            <div class="flex items-center justify-between text-[11px]">
              <span class="font-bold text-fg flex items-center gap-1.5">
                <span class="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span>
                {{ scraperState.message || '正在增量刮削...' }}
              </span>
              <div class="flex items-center gap-2 font-mono text-fg-3 text-[10px]">
                <span>+{{ scraperState.new_movies }} 电影</span>
                <span>+{{ scraperState.new_performers }} 演员</span>
                <span>+{{ scraperState.new_episodes }} 分集</span>
              </div>
            </div>

            <!-- Mini Progress bar -->
            <div class="w-full h-1.5 rounded-full bg-surface-3 overflow-hidden">
              <div
                class="h-full bg-gradient-to-r from-indigo-500 to-amber-500 transition-all duration-300"
                :style="{ width: `${Math.min(100, Math.max(5, scraperState.percent))}%` }"
              ></div>
            </div>

            <!-- Latest log line preview -->
            <div v-if="scraperState.logs.length" class="text-[10px] font-mono text-fg-4 truncate">
              > {{ scraperState.logs[scraperState.logs.length - 1] }}
            </div>
          </div>

          <!-- Status Message when finished or stopped -->
          <div
            v-else-if="scraperMessage || scraperState.finished"
            class="p-3 rounded-xl border text-xs flex items-center justify-between gap-2"
            :class="(scraperSuccess !== false && !scraperState.error) ? 'bg-success-fill/10 border-success-fill/30 text-success' : 'bg-surface-2 border-line text-rose-400'"
          >
            <div class="flex items-center gap-2">
              <CheckCircle2 v-if="scraperSuccess !== false && !scraperState.error" class="w-4 h-4 shrink-0" />
              <AlertCircle v-else class="w-4 h-4 shrink-0" />
              <span>
                {{ scraperState.finished
                  ? `增量同步完成！本次入库影片 +${scraperState.new_movies} 部，演员 +${scraperState.new_performers} 位，分集 +${scraperState.new_episodes} 个。`
                  : scraperMessage }}
              </span>
            </div>
            <button
              v-if="scraperState.finished"
              @click="emit('refresh-movies')"
              class="text-[11px] underline hover:text-fg transition cursor-pointer shrink-0"
            >
              刷新列表
            </button>
          </div>

          <!-- Scheduled Auto-Sync Settings Panel -->
          <div class="p-4 rounded-2xl bg-surface-2/60 border border-line/80 space-y-3">
            <div class="flex items-center justify-between gap-4">
              <div class="flex items-center gap-2.5">
                <div class="p-2 rounded-xl bg-indigo-500/10 text-indigo-400">
                  <Clock class="w-4 h-4" />
                </div>
                <div>
                  <div class="flex items-center gap-2">
                    <span class="text-xs font-bold text-fg">定时自动后台更新</span>
                    <span
                      v-if="pluginsConfig.autoSyncConfig.enabled"
                      class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 font-bold border border-emerald-500/30 flex items-center gap-1"
                    >
                      <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
                      计划生效中
                    </span>
                    <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-surface-3 text-fg-4 font-medium">未启用</span>
                  </div>
                  <p class="text-[11px] text-fg-4 mt-0.5">在后台按自定义计划自动增量抓取最新条目，不干扰日常操作</p>
                </div>
              </div>

              <!-- Toggle switch -->
              <label class="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  :checked="pluginsConfig.autoSyncConfig.enabled"
                  @change="handleToggleAutoSync(($event.target as HTMLInputElement).checked)"
                  class="sr-only peer"
                />
                <div class="w-9 h-5 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-accent-fill"></div>
              </label>
            </div>

            <!-- Interval / Daily configuration controls when enabled -->
            <div v-if="pluginsConfig.autoSyncConfig.enabled" class="pt-3 border-t border-line/50 space-y-3 text-xs">
              <!-- Mode select: Interval vs Daily -->
              <div class="flex items-center gap-4 flex-wrap">
                <div class="flex items-center gap-2 text-xs font-medium text-fg-3">
                  <span>执行计划：</span>
                </div>
                <div class="flex items-center gap-1.5 p-1 rounded-xl bg-surface-3/80 border border-line">
                  <button
                    @click="handleUpdateScheduleMode('interval')"
                    class="px-2.5 py-1 rounded-lg text-xs font-bold transition cursor-pointer"
                    :class="pluginsConfig.autoSyncConfig.mode === 'interval' ? 'bg-surface text-fg shadow-sm' : 'text-fg-4 hover:text-fg'"
                  >
                    ⏱️ 周期循环
                  </button>
                  <button
                    @click="handleUpdateScheduleMode('daily')"
                    class="px-2.5 py-1 rounded-lg text-xs font-bold transition cursor-pointer"
                    :class="pluginsConfig.autoSyncConfig.mode === 'daily' ? 'bg-surface text-fg shadow-sm' : 'text-fg-4 hover:text-fg'"
                  >
                    📅 每天定点
                  </button>
                </div>

                <!-- If Interval mode -->
                <div v-if="pluginsConfig.autoSyncConfig.mode === 'interval'" class="flex items-center gap-2">
                  <span class="text-fg-4 text-xs">每隔</span>
                  <select
                    :value="pluginsConfig.autoSyncConfig.intervalHours"
                    @change="handleUpdateIntervalHours(Number(($event.target as HTMLSelectElement).value))"
                    class="px-2.5 py-1 rounded-lg bg-surface border border-line text-xs font-bold text-fg focus:outline-none cursor-pointer"
                  >
                    <option :value="4">4 小时</option>
                    <option :value="6">6 小时</option>
                    <option :value="12">12 小时 (推荐)</option>
                    <option :value="24">24 小时 (1 天)</option>
                    <option :value="48">48 小时 (2 天)</option>
                  </select>
                  <span class="text-fg-4 text-xs">自动同步</span>
                </div>

                <!-- If Daily mode -->
                <div v-else class="flex items-center gap-2">
                  <span class="text-fg-4 text-xs">每天</span>
                  <input
                    type="time"
                    :value="pluginsConfig.autoSyncConfig.dailyTime"
                    @change="handleUpdateDailyTime(($event.target as HTMLInputElement).value)"
                    class="px-2 py-1 rounded-lg bg-surface border border-line text-xs font-bold text-fg focus:outline-none cursor-pointer"
                  />
                  <span class="text-fg-4 text-xs">静默执行</span>
                </div>
              </div>

              <!-- Status display: last run + next run -->
              <div class="flex items-center justify-between text-[11px] text-fg-4 pt-1 flex-wrap gap-2">
                <div class="flex items-center gap-1.5">
                  <span>上次自动同步：</span>
                  <span class="font-mono text-fg-3">{{ lastRunDescription }}</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <span>下次计划执行：</span>
                  <span class="font-mono text-accent font-bold">{{ nextRunDescription }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 3. AI Translation Plugin (Merged Full Capabilities) -->
      <div
        v-if="activePluginTab === 'all' || activePluginTab === 'translate'"
        class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div class="p-3 rounded-2xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Languages class="w-6 h-6" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-fg">{{ t('plugins.translation') }}</h3>
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-400 font-bold border border-amber-500/20">LLM 引擎</span>
              </div>
              <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.translationDesc') }}</p>
            </div>
          </div>

          <div class="flex items-center gap-3">
            <button
              @click="loadTranslationStats"
              class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 hover:text-fg transition"
              title="刷新翻译进度"
            >
              <RefreshCw class="w-3.5 h-3.5" />
            </button>
            <label class="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                v-model="pluginsConfig.translationEnabled"
                @change="savePluginsConfig({ translationEnabled: pluginsConfig.translationEnabled })"
                class="sr-only peer"
              />
              <div class="w-11 h-6 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-fill"></div>
            </label>
          </div>
        </div>

        <div v-if="pluginsConfig.translationEnabled" class="pt-4 border-t border-line/60 space-y-5 animate-fade-in">
          <!-- Translation Progress Metrics -->
          <div v-if="translationStats" class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
            <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
              <span class="text-fg-4">可翻译简介</span>
              <div class="text-base font-bold text-fg mt-0.5">{{ translationStats.translation_total.toLocaleString() }}</div>
            </div>
            <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
              <span class="text-fg-4">已翻译</span>
              <div class="text-base font-bold text-success mt-0.5">{{ translationStats.translation_done.toLocaleString() }}</div>
            </div>
            <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
              <span class="text-fg-4">待翻译</span>
              <div class="text-base font-bold text-accent-soft mt-0.5">{{ translationStats.translation_pending.toLocaleString() }}</div>
            </div>
            <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
              <span class="text-fg-4">翻译失败</span>
              <div class="text-base font-bold text-danger mt-0.5">{{ translationStats.translation_failed.toLocaleString() }}</div>
            </div>
          </div>

          <!-- Progress bar -->
          <div
            v-if="translationStats && translationStats.translation_total > 0"
            class="h-2 rounded-full bg-surface-2 overflow-hidden"
          >
            <div
              class="h-full bg-gradient-to-r from-accent-fill to-success transition-all duration-500"
              :style="{ width: `${(translationStats.translation_done / translationStats.translation_total) * 100}%` }"
            ></div>
          </div>

          <!-- Translation Mode (Single vs Batch) -->
          <div v-if="!IS_TAURI" class="p-4 rounded-xl bg-sunken/60 border border-line space-y-3">
            <div class="text-xs font-semibold text-fg-2">翻译方式</div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <button
                @click="setTranslateMode('single')"
                class="text-left p-3 rounded-xl border transition"
                :class="translateMode === 'single'
                  ? 'bg-accent-fill/10 border-accent-fill/40'
                  : 'bg-surface border-line-strong hover:border-line-strong'"
              >
                <div class="flex items-center gap-2">
                  <span class="text-xs font-bold"
                    :class="translateMode === 'single' ? 'text-accent-soft' : 'text-fg-2'">
                    单部自动翻译
                  </span>
                  <span class="text-[10px] px-1.5 py-0.5 rounded bg-success-fill/20 text-success-soft border border-success-fill/30">省 token</span>
                </div>
                <div class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                  打开某部影片时才翻译那一部。适合边看边译，不会一次性消耗大量额度。
                </div>
              </button>

              <button
                @click="setTranslateMode('batch')"
                class="text-left p-3 rounded-xl border transition"
                :class="translateMode === 'batch'
                  ? 'bg-accent-fill/10 border-accent-fill/40'
                  : 'bg-surface border-line-strong hover:border-line-strong'"
              >
                <div class="flex items-center gap-2">
                  <span class="text-xs font-bold"
                    :class="translateMode === 'batch' ? 'text-accent-soft' : 'text-fg-2'">
                    批量翻译
                  </span>
                </div>
                <div class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                  打开影片时不翻译，改由下方按钮一次性批量处理。适合把整库译完。
                </div>
              </button>
            </div>
          </div>

          <!-- Target Language -->
          <div>
            <div class="text-xs font-semibold text-fg-3 mb-2">翻译目标语言：</div>
            <div class="flex gap-2 flex-wrap">
              <button
                v-for="l in TARGET_TRANSLATION_LANGUAGES"
                :key="l.code"
                @click="updateTranslationTarget(l.code)"
                :class="[
                  'px-3 py-1.5 rounded-xl text-xs font-semibold border transition',
                  pluginsConfig.translationConfig.targetLanguage === l.code
                    ? 'bg-accent-fill text-on-fill border-accent shadow'
                    : 'bg-surface-2/80 text-fg-3 border-line hover:bg-surface-3 hover:text-fg'
                ]"
              >
                {{ l.label }}
              </button>
            </div>
          </div>

          <!-- Translation Providers -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <div class="text-xs font-semibold text-fg-2">翻译服务来源 (API Providers)</div>
              <button
                @click="openProviderForm()"
                class="px-3 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong flex items-center gap-1.5 transition"
              >
                <Sparkles class="w-3 h-3 text-accent" />
                <span>添加来源</span>
              </button>
            </div>

            <div v-if="providerList.length === 0" class="text-xs text-accent-soft/90 p-3 rounded-xl bg-accent-fill/10 border border-accent-fill/20">
              尚未配置任何来源。点「添加来源」选择服务商（DeepSeek / Claude / Gemini / 本地 Ollama 等）并填入 API Key。
            </div>

            <div v-else class="space-y-2">
              <div
                v-for="p in providerList"
                :key="p.name"
                class="flex items-center justify-between gap-3 p-3 rounded-xl border transition"
                :class="p.active
                  ? 'bg-accent-fill/10 border-accent-fill/30'
                  : 'bg-sunken/60 border-line'"
              >
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-xs font-semibold text-fg truncate">{{ p.label }}</span>
                    <span v-if="p.active" class="text-[10px] px-1.5 py-0.5 rounded bg-accent-fill text-on-fill font-bold">使用中</span>
                    <span v-if="!p.has_key" class="text-[10px] px-1.5 py-0.5 rounded bg-danger-fill/20 text-danger-soft border border-danger-fill/30">缺 API Key</span>
                  </div>
                  <div class="text-[11px] text-fg-4 font-mono truncate mt-0.5">
                    {{ p.type }} · {{ p.model || '默认模型' }} · {{ p.base_url || '默认端点' }}
                  </div>
                  <div v-if="p.key_hint" class="text-[10px] text-fg-5 font-mono">Key: {{ p.key_hint }}</div>
                </div>

                <div class="flex items-center gap-1.5 shrink-0">
                  <button
                    v-if="!p.active"
                    @click="activateProvider(p.name)"
                    class="px-2.5 py-1 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong transition"
                  >启用</button>
                  <button
                    v-if="!IS_TAURI"
                    @click="testProvider(p.name)"
                    class="px-2.5 py-1 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong transition"
                  >测试</button>
                  <button
                    @click="openProviderForm(p)"
                    class="px-2.5 py-1 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong transition"
                  >编辑</button>
                  <button
                    @click="removeProvider(p.name)"
                    class="p-1.5 rounded-lg bg-surface-2 hover:bg-danger-fill/20 text-fg-3 hover:text-danger-soft border border-line-strong transition"
                    title="删除该来源"
                  >
                    <Trash2 class="w-3 h-3" />
                  </button>
                </div>
              </div>
            </div>

            <!-- Provider Form Modal / Card -->
            <div v-if="providerForm.open" class="p-4 rounded-xl bg-sunken/80 border border-line-strong space-y-3">
              <div class="text-xs font-semibold text-fg">
                {{ providerForm.editing ? `编辑来源：${providerForm.editing}` : '添加翻译来源' }}
              </div>

              <div v-if="!providerForm.editing" class="flex flex-wrap gap-1.5">
                <button
                  v-for="preset in providerPresets"
                  :key="preset.id"
                  @click="applyPreset(preset)"
                  :title="preset.hint"
                  class="px-2.5 py-1 rounded-lg text-[11px] border transition"
                  :class="providerForm.name === preset.id
                    ? 'bg-accent-fill text-on-fill border-accent-fill font-bold'
                    : 'bg-surface-2 text-fg-2 border-line-strong hover:bg-surface-3'"
                >{{ preset.label }}</button>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <label class="space-y-1">
                  <span class="text-[11px] text-fg-3">配置名称（唯一标识）</span>
                  <input v-model="providerForm.name" :disabled="!!providerForm.editing"
                    placeholder="deepseek"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono disabled:opacity-60 focus:border-accent-fill/50 focus:outline-none" />
                </label>
                <label class="space-y-1">
                  <span class="text-[11px] text-fg-3">显示名称</span>
                  <input v-model="providerForm.label" placeholder="DeepSeek 深度求索"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg focus:border-accent-fill/50 focus:outline-none" />
                </label>
                <label class="space-y-1">
                  <span class="text-[11px] text-fg-3">接口类型</span>
                  <select v-model="providerForm.type"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg focus:border-accent-fill/50 focus:outline-none">
                    <option value="openai">openai（OpenAI 兼容接口）</option>
                    <option value="anthropic">anthropic（Claude 官方接口）</option>
                    <option value="gemini">gemini（Google Gemini）</option>
                  </select>
                </label>
                <label class="space-y-1">
                  <span class="text-[11px] text-fg-3">模型名</span>
                  <input v-model="providerForm.model" placeholder="deepseek-flash"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none" />
                </label>
                <label class="space-y-1 sm:col-span-2">
                  <span class="text-[11px] text-fg-3">API 端点 (Base URL)</span>
                  <input v-model="providerForm.base_url" placeholder="https://api.deepseek.com"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none" />
                </label>
                <label class="space-y-1 sm:col-span-2">
                  <span class="text-[11px] text-fg-3">
                    API Key
                    <span v-if="providerForm.editing" class="text-fg-4">（留空则保持原 Key 不变）</span>
                  </span>
                  <input v-model="providerForm.api_key" type="password" autocomplete="off"
                    :placeholder="providerForm.editing ? '••••••••（不修改）' : 'sk-...'"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none" />
                </label>
              </div>

              <div class="text-[11px] text-fg-4 leading-relaxed">
                API Key 只写入本机 <code class="font-mono">translate_config.json</code>（权限 600），不会写入数据库，也不会回传给前端。
              </div>

              <div class="flex items-center gap-2">
                <button
                  @click="saveProvider"
                  :disabled="providerBusy"
                  class="px-4 py-2 rounded-lg bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs transition disabled:opacity-40"
                >保存</button>
                <button
                  @click="providerForm.open = false"
                  class="px-4 py-2 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs border border-line-strong transition"
                >取消</button>
              </div>
            </div>

            <div
              v-if="providerTest"
              class="p-3 rounded-xl text-[11px] font-mono whitespace-pre-wrap leading-relaxed"
              :class="providerTest.ok
                ? 'bg-success-fill/10 border border-success-fill/20 text-success-soft'
                : 'bg-danger-fill/10 border border-danger-fill/20 text-danger-soft'"
            >{{ providerTest.text }}</div>

            <div v-if="providerMsg" class="text-[11px] text-accent-soft">{{ providerMsg }}</div>
          </div>

          <!-- Custom Prompt -->
          <div>
            <div class="flex items-center justify-between mb-1.5">
              <div class="text-xs font-semibold text-fg-3">自定义 System / Translation Prompt 提示词：</div>
              <button
                type="button"
                @click="resetTranslationPrompt"
                class="text-[11px] text-accent hover:underline flex items-center gap-1 cursor-pointer transition"
                title="重置为 translate.py 专职译者规范提示词"
              >
                <RotateCcw class="w-3 h-3" />
                <span>恢复规范默认词</span>
              </button>
            </div>
            <textarea
              :value="pluginsConfig.translationConfig.customPromptTemplate"
              @blur="updateTranslationPrompt"
              rows="6"
              class="w-full bg-sunken/80 border border-line-strong rounded-xl p-3 text-xs text-fg outline-none focus:border-accent resize-y font-mono leading-relaxed"
              placeholder="请输入自定义翻译指示..."
            ></textarea>
            <div class="text-[11px] text-fg-4 mt-1 leading-relaxed">
              提示：遵循项目根目录 <code class="font-mono">translate.py</code> 专职译者规范：逐句忠于原文、直白如实按原露骨程度翻译、保留外文人名片名厂牌名、不美化不淡化。
            </div>
          </div>

          <!-- Actions -->
          <div v-if="!IS_TAURI" class="flex items-center gap-3 pt-2 flex-wrap">
            <button
              @click="handleRunTranslation(20)"
              :disabled="isTranslating"
              class="px-4 py-2 rounded-xl bg-accent-fill hover:bg-accent text-on-fill text-xs font-bold flex items-center gap-2 transition disabled:opacity-50"
            >
              <Loader2 v-if="isTranslating" class="w-3.5 h-3.5 animate-spin" />
              <Languages v-else class="w-3.5 h-3.5" />
              <span>批量翻译 20 部</span>
            </button>

            <button
              @click="handleRunTranslation(null)"
              :disabled="isTranslating"
              class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs font-medium border border-line-strong transition disabled:opacity-50"
            >全量批量翻译</button>

            <span v-if="translateMsg" class="text-xs text-accent-soft font-mono">{{ translateMsg }}</span>
          </div>

          <!-- Desktop Helper Note -->
          <div
            v-if="IS_TAURI"
            class="p-3 rounded-xl bg-surface-2/60 border border-line-strong text-xs text-fg-2 space-y-1.5"
          >
            <div class="font-semibold text-fg">桌面版：来源在此统一配置，全量批量翻译可通过命令行直接调用：</div>
            <code class="block bg-scrim/60 rounded-lg p-2 font-mono text-[11px] text-fg-2 overflow-x-auto">
              python3 translate.py --profile deepseek --limit 20 --dry-run
            </code>
          </div>

          <!-- Glossary Section -->
          <div class="pt-4 border-t border-line/60 space-y-2">
            <div class="flex items-center justify-between">
              <div>
                <div class="text-xs font-semibold text-fg-2">演员属性词库翻译</div>
                <div class="text-[11px] text-fg-4">
                  将演员身材、肤色、毛发等英文专有名词一次性翻译入库（共约 76 词）
                </div>
              </div>
              <div v-if="!IS_TAURI" class="flex items-center gap-2">
                <button
                  @click="handleRunGlossary(true)"
                  :disabled="glossaryBusy"
                  class="px-3 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs border border-line-strong transition disabled:opacity-50"
                >试跑检测</button>
                <button
                  @click="handleRunGlossary(false)"
                  :disabled="glossaryBusy"
                  class="px-3 py-1.5 rounded-lg bg-accent-fill hover:bg-accent text-on-fill text-xs font-bold transition disabled:opacity-50"
                >一键翻译词库</button>
              </div>
            </div>
            <div v-if="glossaryMsg" class="text-xs text-success-soft font-mono">{{ glossaryMsg }}</div>
            <div v-if="glossaryError" class="text-xs text-danger-soft font-mono">{{ glossaryError }}</div>
          </div>
        </div>
      </div>

      <!-- 4. Exploration Trophy System (77 Trophies, Clean & Configurable) -->
      <div
        v-if="activePluginTab === 'all' || activePluginTab === 'trophy'"
        class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div class="p-3 rounded-2xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
              <Trophy class="w-6 h-6" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-fg">{{ t('plugins.trophies') }}</h3>
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-400 font-bold border border-purple-500/20">77 奖杯</span>
              </div>
              <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.trophiesDesc') }}</p>
            </div>
          </div>

          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              v-model="pluginsConfig.trophiesEnabled"
              @change="savePluginsConfig({ trophiesEnabled: pluginsConfig.trophiesEnabled })"
              class="sr-only peer"
            />
            <div class="w-11 h-6 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-fill"></div>
          </label>
        </div>

        <div v-if="pluginsConfig.trophiesEnabled" class="pt-4 border-t border-line/60 space-y-4 animate-fade-in">
          <div class="flex items-center justify-between flex-wrap gap-4">
            <div class="flex items-center gap-3">
              <span class="text-xs text-fg-4">已解锁奖杯：</span>
              <span class="text-base font-extrabold text-accent font-mono">{{ trophyStats.unlocked }} / 77 ({{ trophyStats.percentage }}%)</span>
            </div>

            <!-- Trophies sound and reset controls -->
            <div class="flex items-center gap-2.5 flex-wrap">
              <!-- Sound toggle -->
              <button
                @click="toggleTrophySound"
                class="px-3 py-1.5 rounded-xl text-xs font-semibold border transition flex items-center gap-1.5 shadow-xs"
                :class="pluginsConfig.trophiesSoundEnabled ? 'bg-surface-2 text-fg border-line hover:bg-surface-3' : 'bg-surface-2/40 text-fg-5 border-line/60'"
                :title="pluginsConfig.trophiesSoundEnabled ? '解锁音效已开启' : '解锁音效已静音'"
              >
                <component :is="pluginsConfig.trophiesSoundEnabled ? Volume2 : VolumeX" class="w-3.5 h-3.5 text-accent" />
                <span>{{ pluginsConfig.trophiesSoundEnabled ? '解锁音效: 开' : '解锁音效: 关' }}</span>
              </button>

              <!-- Reset trophies button -->
              <button
                @click="handleResetTrophies"
                class="px-3 py-1.5 rounded-xl text-xs font-semibold border border-danger-fill/30 bg-danger-fill/10 text-danger-soft hover:bg-danger-fill/20 transition flex items-center gap-1.5 shadow-xs"
                title="清空当前已解锁奖杯记录，从头开始"
              >
                <RotateCcw class="w-3.5 h-3.5" />
                <span>{{ t('plugins.resetTrophies') }}</span>
              </button>

              <button
                @click="emit('open-trophies')"
                class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 border border-line-strong text-xs font-bold text-fg flex items-center gap-1.5 transition shadow-xs"
              >
                <span>进入奖杯陈列馆</span>
                <ChevronRight class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Trophy Reset Modal -->
    <TrophyResetModal
      v-if="showTrophyResetModal"
      @close="showTrophyResetModal = false"
    />

    <!-- AI Persona Analysis Progress Modal -->
    <AiAnalysisModal
      :show="showAiModal"
      :profile-name="activeProfile?.label || activeProfile?.name"
      :model-name="activeProfile?.model || activeProfile?.type"
      @close="showAiModal = false"
    />
  </div>
</template>
