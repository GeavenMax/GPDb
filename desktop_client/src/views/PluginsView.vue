<script setup lang="ts">
import { ref, reactive, computed, onMounted, defineAsyncComponent } from 'vue';
import { api, IS_TAURI } from '../api';
import { pluginsConfig, savePluginsConfig, DEFAULT_TRANSLATION_PROMPT } from '../services/pluginManager';
import { generateAiPersonaInsight, clearAiReport, activeAiReport, isAiAnalyzing, aiAnalysisError, exportAiReportMarkdown } from '../services/aiAnalysis';
import { t, TARGET_TRANSLATION_LANGUAGES } from '../i18n';
import type { TranslationStats, TranslationProfile, TranslationPreset } from '../types';
import { Blocks, Sparkles, Compass, RefreshCw, Languages, Trophy, AlertCircle, CheckCircle2, SlidersHorizontal, Download, Trash2, Volume2, VolumeX } from '@lucide/vue';
import TrophyResetModal from '../components/TrophyResetModal.vue';
const emit = defineEmits<{
  (e: 'open-trophies'): void;
  (e: 'refresh-movies'): void;
  (e: 'open-sync'): void;
  (e: 'open-environment-check'): void;
}>();

type PluginSubTab = 'all' | 'ai' | 'bt' | 'scraper' | 'translate' | 'trophy';
const activePluginTab = ref<PluginSubTab>('all');

const BtSearchConfigPanel = defineAsyncComponent(() => import('../components/plugins/BtSearchConfigPanel.vue'));
const ScraperConfigPanel = defineAsyncComponent(() => import('../components/plugins/ScraperConfigPanel.vue'));

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

const showTrophyResetModal = ref(false);
const trophyStats = ref({ unlocked: 0, percentage: 0 });

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
  return rawSections.map((s: string) => {
    const lines = s.trim().split('\n');
    const titleMatch = lines[0].match(/^###\s+(.*)/);
    const title = titleMatch ? titleMatch[1].trim() : '';
    const body = titleMatch ? lines.slice(1).join('\n').trim() : lines.join('\n').trim();
    return { title, body };
  }).filter((s: { title: string; body: string }) => s.title || s.body);
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
      <BtSearchConfigPanel v-if="activePluginTab === 'all' || activePluginTab === 'bt'" />

      <!-- 2. Custom Scraper Plugin -->
      <ScraperConfigPanel 
        v-if="activePluginTab === 'all' || activePluginTab === 'scraper'"
        @open-sync="emit('open-sync')"
        @open-environment-check="emit('open-environment-check')"
        @refresh-movies="emit('refresh-movies')"
      />

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
