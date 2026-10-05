<script setup lang="ts">
import { ref, reactive, onMounted, defineAsyncComponent } from 'vue';
import { api, IS_TAURI } from '../api';
import { pluginsConfig, savePluginsConfig, DEFAULT_TRANSLATION_PROMPT } from '../services/pluginManager';
import { t, TARGET_TRANSLATION_LANGUAGES } from '../i18n';
import type { TranslationStats, TranslationProfile, TranslationPreset } from '../types';
import { Blocks, Sparkles, Compass, RefreshCw, Languages, Download, Upload, Trash2 } from '@lucide/vue';
import { recordPluginVisit } from '../services/analytics';
const emit = defineEmits<{
  (e: 'refresh-movies'): void;
  (e: 'open-sync'): void;
  (e: 'open-environment-check'): void;
}>();

type PluginSubTab = 'all' | 'bt' | 'scraper' | 'translate';
const activePluginTab = ref<PluginSubTab>('all');

const BtSearchConfigPanel = defineAsyncComponent(() => import('../components/plugins/BtSearchConfigPanel.vue'));
const ScraperConfigPanel = defineAsyncComponent(() => import('../components/plugins/ScraperConfigPanel.vue'));

// --- 3. AI Translation (Consolidated from Settings) ---
const translationStats = ref<TranslationStats | null>(null);
const isTranslating = ref(false);
const translateMsg = ref('');

const translateMode = ref<'single' | 'batch'>(
  (localStorage.getItem('gpdb_translate_mode') as 'single' | 'batch') || 'single'
);

function setTranslateMode(mode: 'single' | 'batch') {
  translateMode.value = mode;
  localStorage.setItem('gpdb_translate_mode', mode);
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
const discoveredModels = ref<string[]>([]);
const isDetectingModels = ref(false);
const exportImportMsg = ref('');
const fileInputRef = ref<HTMLInputElement | null>(null);

async function handleExportTranslations() {
  try {
    exportImportMsg.value = t('plugins.exportingTrans');
    const jsonStr = await api.exportTranslations();
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `GPDb_Translations_${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    exportImportMsg.value = t('plugins.exportSuccess');
    setTimeout(() => { exportImportMsg.value = ''; }, 4000);
  } catch (err: any) {
    exportImportMsg.value = t('plugins.exportFailed', { error: err?.message || err });
  }
}

function triggerImportFileInput() {
  fileInputRef.value?.click();
}

async function handleImportFileChange(e: Event) {
  const target = e.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  try {
    exportImportMsg.value = t('plugins.importingTrans');
    const text = await file.text();
    const res = await api.importTranslations(text);
    exportImportMsg.value = t('plugins.importSuccess', { movies: res.movies_updated, episodes: res.episodes_updated });
    await loadTranslationStats();
    emit('refresh-movies');
    setTimeout(() => { exportImportMsg.value = ''; }, 6000);
  } catch (err: any) {
    exportImportMsg.value = t('plugins.importFailed', { error: err?.message || err });
  } finally {
    target.value = '';
  }
}

async function detectModels() {
  if (!providerForm.name.trim()) {
    providerMsg.value = t('plugins.inputConfigId');
    return;
  }
  isDetectingModels.value = true;
  providerMsg.value = t('plugins.detectingModels');
  try {
    const list = await api.fetchProviderModels(providerForm.name.trim());
    discoveredModels.value = list;
    if (list.length > 0) {
      providerMsg.value = t('plugins.modelsDetected', { count: list.length });
    } else {
      providerMsg.value = t('plugins.modelsNotFound');
    }
  } catch (e: any) {
    providerMsg.value = t('plugins.detectFailed', { error: e?.message || e });
  } finally {
    isDetectingModels.value = false;
  }
}

const providerForm = reactive({
  open: false,
  editing: '',
  name: '',
  label: '',
  type: 'openai' as 'openai' | 'anthropic' | 'gemini',
  model: '',
  base_url: '',
  api_key: '',
  active: true,
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
  providerForm.active = true;
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
    providerForm.active = profile.active || !providerList.value.some(p => p.active);
  } else {
    providerForm.editing = '';
    providerForm.name = '';
    providerForm.label = '';
    providerForm.type = 'openai';
    providerForm.model = '';
    providerForm.base_url = '';
    providerForm.active = providerList.value.length === 0 || !providerList.value.some(p => p.active);
  }
  providerForm.api_key = '';
}

async function saveProvider() {
  if (!providerForm.name.trim()) {
    providerMsg.value = t('plugins.inputConfigName');
    return;
  }
  providerBusy.value = true;
  providerMsg.value = '';
  const hasActive = providerList.value.some(p => p.active);
  const shouldBeActive = providerForm.active || !hasActive || (!providerForm.editing && providerList.value.length === 0);
  const res = await api.saveTranslationProvider({
    name: providerForm.name.trim(),
    label: providerForm.label.trim() || providerForm.name.trim(),
    type: providerForm.type,
    model: providerForm.model.trim(),
    base_url: providerForm.base_url.trim(),
    api_key: providerForm.api_key.trim(),
    active: shouldBeActive,
  });
  providerBusy.value = false;
  if (res.success) {
    if (res.profiles) providerList.value = res.profiles;
    providerForm.open = false;
    providerMsg.value = t('plugins.configSaved');
    await loadTranslationStats();
  } else {
    providerMsg.value = res.error || t('plugins.saveFailed');
  }
}

async function activateProvider(name: string) {
  const res = await api.activateTranslationProvider(name);
  if (res.success && res.profiles) providerList.value = res.profiles;
  else providerMsg.value = res.error || t('plugins.switchFailed');
  await loadTranslationStats();
}

async function removeProvider(name: string) {
  const res = await api.deleteTranslationProvider(name);
  if (res.success && res.profiles) providerList.value = res.profiles;
  else providerMsg.value = res.error || t('plugins.deleteFailed');
  await loadTranslationStats();
}

async function testProvider(name: string) {
  providerTest.value = { ok: true, text: t('plugins.testingConn') };
  const res = await api.testTranslationProvider(name);
  if (res.success) {
    providerTest.value = {
      ok: true,
      text: t('plugins.testSuccess', { profile: res.profile || name, input: res.source, output: res.result }),
    };
  } else {
    providerTest.value = {
      ok: false,
      text: t('plugins.testFailed', { error: res.error || 'No response' }),
    };
  }
}

async function handleRunTranslation(limit: number | null) {
  isTranslating.value = true;
  translateMsg.value = '';
  const res = await api.runTranslation(limit);
  if (res.success) {
    translateMsg.value = res.message || t('plugins.transStarted');
    const poll = setInterval(async () => {
      await loadTranslationStats();
      if (!translationStats.value?.running) {
        clearInterval(poll);
        isTranslating.value = false;
        translateMsg.value = t('plugins.transComplete');
        emit('refresh-movies');
      }
    }, 3000);
  } else {
    translateMsg.value = res.error || t('plugins.transStartFailed');
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
      glossaryMsg.value = t('plugins.glossaryDryRun', { count: res.pending });
    } else {
      glossaryMsg.value = t('plugins.glossaryUpdated', { added: res.translated, total: res.total }) + (res.failed ? t('plugins.glossaryFailedExtra', { count: res.failed }) : '');
    }
  } else {
    glossaryError.value = res.error || t('plugins.glossaryFailed');
  }
  glossaryBusy.value = false;
}




onMounted(() => {
  recordPluginVisit();
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
          { id: 'all', label: t('plugins.all'), count: 3, icon: Blocks },
          { id: 'bt', label: t('plugins.resourceSearch'), count: 1, icon: Compass },
          { id: 'scraper', label: t('plugins.scraper'), count: 1, icon: RefreshCw },
          { id: 'translate', label: t('plugins.translation'), count: 1, icon: Languages },
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
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-400 font-bold border border-amber-500/20">{{ t('plugins.llmEngineBadge') }}</span>
              </div>
              <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.translationDesc') }}</p>
            </div>
          </div>

          <div class="flex items-center gap-3">
            <button
              @click="loadTranslationStats"
              class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 hover:text-fg transition"
              :title="t('plugins.aiRefreshProgress')"
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
              <span class="text-fg-4">{{ t('plugins.translatable') }}</span>
              <div class="text-base font-bold text-fg mt-0.5">{{ translationStats.translation_total.toLocaleString() }}</div>
            </div>
            <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
              <span class="text-fg-4">{{ t('plugins.translated') }}</span>
              <div class="text-base font-bold text-success mt-0.5">{{ translationStats.translation_done.toLocaleString() }}</div>
            </div>
            <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
              <span class="text-fg-4">{{ t('plugins.pending') }}</span>
              <div class="text-base font-bold text-accent-soft mt-0.5">{{ translationStats.translation_pending.toLocaleString() }}</div>
            </div>
            <div class="p-3 rounded-xl bg-sunken/60 border border-line/80">
              <span class="text-fg-4">{{ t('plugins.failed') }}</span>
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

          <!-- Export / Import Translation Data Actions -->
          <div class="flex items-center justify-between gap-3 p-3.5 rounded-xl bg-sunken/60 border border-line flex-wrap">
            <div class="space-y-0.5">
              <div class="text-xs font-semibold text-fg">{{ t('plugins.backupTitle') }}</div>
              <div class="text-[11px] text-fg-4">{{ t('plugins.backupDesc') }}</div>
            </div>
            <div class="flex items-center gap-2">
              <input
                ref="fileInputRef"
                type="file"
                accept=".json"
                class="hidden"
                @change="handleImportFileChange"
              />
              <button
                @click="triggerImportFileInput"
                class="px-3 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs font-medium border border-line-strong flex items-center gap-1.5 transition cursor-pointer"
                :title="t('plugins.importTransTip')"
              >
                <Upload class="w-3.5 h-3.5 text-accent" />
                <span>{{ t('plugins.importTrans') }}</span>
              </button>
              <button
                @click="handleExportTranslations"
                class="px-3 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs font-medium border border-line-strong flex items-center gap-1.5 transition cursor-pointer"
                :title="t('plugins.exportTransTip')"
              >
                <Download class="w-3.5 h-3.5 text-accent" />
                <span>{{ t('plugins.exportTrans') }}</span>
              </button>
            </div>
          </div>
          <div v-if="exportImportMsg" class="text-xs px-3 py-2 rounded-lg bg-accent-fill/10 border border-accent-fill/20 text-accent font-medium animate-fade-in">
            {{ exportImportMsg }}
          </div>

          <!-- Translation Mode (Single vs Batch) -->
          <div v-if="!IS_TAURI" class="p-4 rounded-xl bg-sunken/60 border border-line space-y-3">
            <div class="text-xs font-semibold text-fg-2">{{ t('plugins.translationMode') }}</div>
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
                    {{ t('plugins.singleAuto') }}
                  </span>
                  <span class="text-[10px] px-1.5 py-0.5 rounded bg-success-fill/20 text-success-soft border border-success-fill/30">{{ t('plugins.saveTokens') }}</span>
                </div>
                <div class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                  {{ t('plugins.singleAutoDesc') }}
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
                    {{ t('plugins.batchMode') }}
                  </span>
                </div>
                <div class="text-[11px] text-fg-4 mt-1 leading-relaxed">
                  {{ t('plugins.batchModeDesc') }}
                </div>
              </button>
            </div>
          </div>

          <!-- Target Language -->
          <div>
            <div class="text-xs font-semibold text-fg-3 mb-2">{{ t('plugins.targetLanguage') }}</div>
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
              <div class="text-xs font-semibold text-fg-2">{{ t('plugins.apiProviders') }}</div>
              <button
                @click="openProviderForm()"
                class="px-3 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong flex items-center gap-1.5 transition"
              >
                <Sparkles class="w-3 h-3 text-accent" />
                <span>{{ t('plugins.addProvider') }}</span>
              </button>
            </div>

            <div v-if="providerList.length === 0" class="text-xs text-accent-soft/90 p-3 rounded-xl bg-accent-fill/10 border border-accent-fill/20">
              {{ t('plugins.noProviders') }}
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
                    <span v-if="p.active" class="text-[10px] px-1.5 py-0.5 rounded bg-accent-fill text-on-fill font-bold">{{ t('plugins.activeBadge') }}</span>
                    <span v-if="!p.has_key" class="text-[10px] px-1.5 py-0.5 rounded bg-danger-fill/20 text-danger-soft border border-danger-fill/30">{{ t('plugins.missingKey') }}</span>
                  </div>
                  <div class="text-[11px] text-fg-4 font-mono truncate mt-0.5">
                    {{ p.type }} · {{ p.model || t('plugins.defaultModel') }} · {{ p.base_url || t('plugins.defaultEndpoint') }}
                  </div>
                  <div v-if="p.key_hint" class="text-[10px] text-fg-5 font-mono">Key: {{ p.key_hint }}</div>
                </div>

                <div class="flex items-center gap-1.5 shrink-0">
                  <button
                    v-if="!p.active"
                    @click="activateProvider(p.name)"
                    class="px-2.5 py-1 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong transition"
                  >{{ t('plugins.enable') }}</button>
                  <button
                    @click="testProvider(p.name)"
                    class="px-2.5 py-1 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong transition"
                  >{{ t('plugins.test') }}</button>
                  <button
                    @click="openProviderForm(p)"
                    class="px-2.5 py-1 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-[11px] border border-line-strong transition"
                  >{{ t('plugins.edit') }}</button>
                  <button
                    @click="removeProvider(p.name)"
                    class="p-1.5 rounded-lg bg-surface-2 hover:bg-danger-fill/20 text-fg-3 hover:text-danger-soft border border-line-strong transition"
                    :title="t('plugins.deleteProvider')"
                  >
                    <Trash2 class="w-3 h-3" />
                  </button>
                </div>
              </div>
            </div>

            <!-- Provider Form Modal / Card -->
            <div v-if="providerForm.open" class="p-4 rounded-xl bg-sunken/80 border border-line-strong space-y-3">
              <div class="text-xs font-semibold text-fg">
                {{ providerForm.editing ? t('plugins.editProviderTitle', { name: providerForm.editing }) : t('plugins.addProviderTitle') }}
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
                  <span class="text-[11px] text-fg-3">{{ t('plugins.configId') }}</span>
                  <input v-model="providerForm.name" :disabled="!!providerForm.editing"
                    placeholder="deepseek"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono disabled:opacity-60 focus:border-accent-fill/50 focus:outline-none" />
                </label>
                <label class="space-y-1">
                  <span class="text-[11px] text-fg-3">{{ t('plugins.displayName') }}</span>
                  <input v-model="providerForm.label" placeholder="DeepSeek"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg focus:border-accent-fill/50 focus:outline-none" />
                </label>
                <label class="space-y-1">
                  <span class="text-[11px] text-fg-3">{{ t('plugins.interfaceType') }}</span>
                  <select v-model="providerForm.type"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg focus:border-accent-fill/50 focus:outline-none">
                    <option value="openai">{{ t('plugins.openaiCompat') }}</option>
                    <option value="anthropic">{{ t('plugins.anthropicCompat') }}</option>
                    <option value="gemini">{{ t('plugins.geminiCompat') }}</option>
                  </select>
                </label>
                <label class="space-y-1">
                  <div class="flex items-center justify-between">
                    <span class="text-[11px] text-fg-3">{{ t('plugins.modelName') }}</span>
                    <button
                      type="button"
                      @click="detectModels"
                      :disabled="isDetectingModels || !providerForm.name"
                      class="text-[10px] text-accent hover:underline flex items-center gap-1 transition disabled:opacity-40 cursor-pointer"
                      :title="t('plugins.detectModels')"
                    >
                      <Sparkles class="w-2.5 h-2.5" />
                      <span>{{ isDetectingModels ? t('plugins.detecting') : t('plugins.detectModels') }}</span>
                    </button>
                  </div>
                  <input
                    v-model="providerForm.model"
                    list="discovered-models-list"
                    placeholder="deepseek-flash"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none"
                  />
                  <datalist id="discovered-models-list">
                    <option v-for="m in discoveredModels" :key="m" :value="m">{{ m }}</option>
                  </datalist>
                </label>
                <label class="space-y-1 sm:col-span-2">
                  <span class="text-[11px] text-fg-3">{{ t('plugins.apiEndpoint') }}</span>
                  <input v-model="providerForm.base_url" placeholder="https://api.deepseek.com"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none" />
                </label>
                <label class="space-y-1 sm:col-span-2">
                  <span class="text-[11px] text-fg-3">
                    API Key
                    <span v-if="providerForm.editing" class="text-fg-4">{{ t('plugins.apiKeyKeep') }}</span>
                  </span>
                  <input v-model="providerForm.api_key" type="password" autocomplete="off"
                    :placeholder="providerForm.editing ? t('plugins.keepKeyPlaceholder') : 'sk-...'"
                    class="w-full px-3 py-2 rounded-lg bg-surface border border-line-strong text-xs text-fg font-mono focus:border-accent-fill/50 focus:outline-none" />
                </label>
              </div>

              <div class="text-[11px] text-fg-4 leading-relaxed">
                {{ t('plugins.apiKeyTip') }}
              </div>

              <label class="flex items-center gap-2 text-xs text-fg-2 cursor-pointer select-none py-1">
                <input
                  type="checkbox"
                  v-model="providerForm.active"
                  class="rounded text-accent focus:ring-accent w-4 h-4 cursor-pointer"
                />
                <span>{{ t('plugins.setActiveOnSave') }}</span>
              </label>

              <div class="flex items-center gap-2 pt-1">
                <button
                  @click="saveProvider"
                  :disabled="providerBusy"
                  class="px-4 py-2 rounded-lg bg-accent-fill hover:bg-accent text-on-fill font-bold text-xs transition disabled:opacity-40"
                >{{ t('plugins.save') }}</button>
                <button
                  @click="providerForm.open = false"
                  class="px-4 py-2 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs border border-line-strong transition"
                >{{ t('plugins.cancel') }}</button>
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
              <div class="text-xs font-semibold text-fg-3">{{ t('plugins.customPromptTitle') }}</div>
              <button
                type="button"
                @click="resetTranslationPrompt"
                class="text-[11px] text-accent hover:underline flex items-center gap-1 cursor-pointer transition"
                :title="t('plugins.resetDefaultPromptTip')"
              >
                <RotateCcw class="w-3 h-3" />
                <span>{{ t('plugins.resetDefaultPrompt') }}</span>
              </button>
            </div>
            <textarea
              :value="pluginsConfig.translationConfig.customPromptTemplate"
              @blur="updateTranslationPrompt"
              rows="6"
              class="w-full bg-sunken/80 border border-line-strong rounded-xl p-3 text-xs text-fg outline-none focus:border-accent resize-y font-mono leading-relaxed"
              :placeholder="t('plugins.customPromptPlaceholder')"
            ></textarea>
            <div class="text-[11px] text-fg-4 mt-1 leading-relaxed">
              {{ t('plugins.promptTip') }}
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
              <span>{{ t('plugins.batch20') }}</span>
            </button>

            <button
              @click="handleRunTranslation(null)"
              :disabled="isTranslating"
              class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs font-medium border border-line-strong transition disabled:opacity-50"
            >{{ t('plugins.batchAll') }}</button>

            <span v-if="translateMsg" class="text-xs text-accent-soft font-mono">{{ translateMsg }}</span>
          </div>

          <!-- Desktop Helper Note -->
          <div
            v-if="IS_TAURI"
            class="p-3 rounded-xl bg-surface-2/60 border border-line-strong text-xs text-fg-2 space-y-1.5"
          >
            <div class="font-semibold text-fg">{{ t('plugins.cliNotice') }}</div>
            <code class="block bg-scrim/60 rounded-lg p-2 font-mono text-[11px] text-fg-2 overflow-x-auto">
              python3 translate.py --profile deepseek --limit 20 --dry-run
            </code>
          </div>

          <!-- Glossary Section -->
          <div class="pt-4 border-t border-line/60 space-y-2">
            <div class="flex items-center justify-between">
              <div>
                <div class="text-xs font-semibold text-fg-2">{{ t('plugins.attrGlossary') }}</div>
                <div class="text-[11px] text-fg-4">
                  {{ t('plugins.attrGlossaryDesc') }}
                </div>
              </div>
              <div v-if="!IS_TAURI" class="flex items-center gap-2">
                <button
                  @click="handleRunGlossary(true)"
                  :disabled="glossaryBusy"
                  class="px-3 py-1.5 rounded-lg bg-surface-2 hover:bg-surface-3 text-fg-2 text-xs border border-line-strong transition disabled:opacity-50"
                >{{ t('plugins.testRunGlossary') }}</button>
                <button
                  @click="handleRunGlossary(false)"
                  :disabled="glossaryBusy"
                  class="px-3 py-1.5 rounded-lg bg-accent-fill hover:bg-accent text-on-fill text-xs font-bold transition disabled:opacity-50"
                >{{ t('plugins.translateGlossary') }}</button>
              </div>
            </div>
            <div v-if="glossaryMsg" class="text-xs text-success-soft font-mono">{{ glossaryMsg }}</div>
            <div v-if="glossaryError" class="text-xs text-danger-soft font-mono">{{ glossaryError }}</div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>
