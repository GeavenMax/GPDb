<script setup lang="ts">
import { ref } from 'vue';
import {
  pluginsConfig, savePluginsConfig, type BtSearchConfig
} from '../services/pluginManager';
import { TARGET_TRANSLATION_LANGUAGES, t } from '../i18n';
import { trophyStats } from '../services/trophySystem';
import { api } from '../api';
import {
  Blocks, Compass, RefreshCw, Languages, Trophy,
  AlertCircle, CheckCircle2, ChevronRight, Loader2
} from '@lucide/vue';

const emit = defineEmits<{
  (e: 'open-trophies'): void;
}>();

// Scraper running state
const isScraping = ref(false);
const scraperMessage = ref('');
const scraperSuccess = ref<boolean | null>(null);

async function runScraper() {
  if (isScraping.value) return;
  isScraping.value = true;
  scraperMessage.value = '正在安全检测并执行增量刮削...';
  scraperSuccess.value = null;

  try {
    const res = await api.runSync();
    scraperSuccess.value = true;
    scraperMessage.value = `增量同步完成！新增影片: ${res.newMovies} 部，新增演员: ${res.newPerformers} 位。已导入数据库，请点击顶部「同步」按钮刷新。`;
  } catch (err: any) {
    scraperSuccess.value = false;
    scraperMessage.value = `同步请求失败: ${err?.message || err || '服务未响应'}`;
  } finally {
    isScraping.value = false;
  }
}

function updateBtEngine(engine: BtSearchConfig['engine']) {
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      engine
    }
  });
}

function updateBtCustomUrl(e: Event) {
  const target = e.target as HTMLInputElement;
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      customUrlTemplate: target.value
    }
  });
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

    <!-- Plugins List -->
    <div class="space-y-6">
      <!-- 1. BT / Magnet Search Plugin -->
      <div class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div class="p-3 rounded-2xl bg-sky-500/10 text-sky-400 border border-sky-500/20">
              <Compass class="w-6 h-6" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-fg">{{ t('plugins.btSearch') }}</h3>
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-sky-500/10 text-sky-400 font-bold border border-sky-500/20">v1.2</span>
              </div>
              <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.btSearchDesc') }}</p>
            </div>
          </div>

          <!-- Switch -->
          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              v-model="pluginsConfig.btSearchEnabled"
              @change="savePluginsConfig({ btSearchEnabled: pluginsConfig.btSearchEnabled })"
              class="sr-only peer"
            />
            <div class="w-11 h-6 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-fill"></div>
          </label>
        </div>

        <!-- Configuration options (if enabled) -->
        <div v-if="pluginsConfig.btSearchEnabled" class="pt-4 border-t border-line/60 space-y-3 animate-fade-in">
          <div class="text-xs font-semibold text-fg-3">预设搜索引擎模板：</div>
          <div class="flex gap-2 flex-wrap">
            <button
              v-for="eng in [
                { id: 'sukebei', label: 'Sukebei (Nyaa)' },
                { id: '1337x', label: '1337x' },
                { id: 'torrentgalaxy', label: 'TorrentGalaxy' },
                { id: 'google', label: 'Google Search' },
                { id: 'custom', label: '自定义 URL 模板' }
              ]"
              :key="eng.id"
              @click="updateBtEngine(eng.id as any)"
              :class="[
                'px-3 py-1.5 rounded-xl text-xs font-semibold border transition',
                pluginsConfig.btSearchConfig.engine === eng.id
                  ? 'bg-accent-fill text-on-fill border-accent shadow'
                  : 'bg-surface-2/80 text-fg-3 border-line hover:bg-surface-3 hover:text-fg'
              ]"
            >
              {{ eng.label }}
            </button>
          </div>

          <div v-if="pluginsConfig.btSearchConfig.engine === 'custom'" class="pt-1">
            <div class="text-[11px] text-fg-4 mb-1">自定义搜索 URL（包含 {query} 占位符）：</div>
            <input
              :value="pluginsConfig.btSearchConfig.customUrlTemplate"
              @input="updateBtCustomUrl"
              placeholder="https://example.com/search?q={query}"
              class="w-full bg-sunken/80 border border-line-strong rounded-xl px-3 py-2 text-xs text-fg font-mono outline-none focus:border-accent"
            />
          </div>
        </div>
      </div>

      <!-- 2. Custom Scraper Plugin -->
      <div class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div class="p-3 rounded-2xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <RefreshCw class="w-6 h-6" :class="{ 'animate-spin': isScraping }" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-fg">{{ t('plugins.customScraper') }}</h3>
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 font-bold border border-emerald-500/20">内置</span>
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

        <!-- Action & Status -->
        <div v-if="pluginsConfig.customScraperEnabled" class="pt-4 border-t border-line/60 space-y-3">
          <div class="flex items-center gap-3 flex-wrap">
            <button
              @click="runScraper"
              :disabled="isScraping"
              class="px-4 py-2 rounded-xl bg-accent-fill text-on-fill text-xs font-bold flex items-center gap-2 hover:bg-accent-fill/90 transition shadow disabled:opacity-50"
            >
              <Loader2 v-if="isScraping" class="w-3.5 h-3.5 animate-spin" />
              <RefreshCw v-else class="w-3.5 h-3.5" />
              <span>{{ isScraping ? '增量刮削执行中...' : '一键启动增量刮削' }}</span>
            </button>
            <span class="text-[11px] text-fg-4">
              自动抓取 /newm 与 /newp 并将增量数据导入 SQLite
            </span>
          </div>

          <div
            v-if="scraperMessage"
            class="p-3 rounded-xl border text-xs flex items-center gap-2"
            :class="scraperSuccess ? 'bg-success-fill/10 border-success-fill/30 text-success' : 'bg-surface-2 border-line text-fg-3'"
          >
            <CheckCircle2 v-if="scraperSuccess" class="w-4 h-4 shrink-0" />
            <AlertCircle v-else class="w-4 h-4 shrink-0" />
            <span>{{ scraperMessage }}</span>
          </div>
        </div>
      </div>

      <!-- 3. AI Translation Plugin -->
      <div class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div class="p-3 rounded-2xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Languages class="w-6 h-6" />
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-fg">{{ t('plugins.translation') }}</h3>
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-400 font-bold border border-amber-500/20">LLM 驱动</span>
              </div>
              <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.translationDesc') }}</p>
            </div>
          </div>

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

        <div v-if="pluginsConfig.translationEnabled" class="pt-4 border-t border-line/60 space-y-4 animate-fade-in">
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

          <div>
            <div class="text-xs font-semibold text-fg-3 mb-1.5">自定义 System / Translation Prompt 提示词：</div>
            <textarea
              :value="pluginsConfig.translationConfig.customPromptTemplate"
              @blur="updateTranslationPrompt"
              rows="3"
              class="w-full bg-sunken/80 border border-line-strong rounded-xl p-3 text-xs text-fg outline-none focus:border-accent resize-none leading-relaxed"
              placeholder="请输入自定义翻译指示..."
            ></textarea>
          </div>
        </div>
      </div>

      <!-- 4. PSN 77 Trophies Plugin -->
      <div class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm">
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

        <div v-if="pluginsConfig.trophiesEnabled" class="pt-4 border-t border-line/60 flex items-center justify-between flex-wrap gap-4">
          <div class="flex items-center gap-3">
            <span class="text-xs text-fg-4">已解锁奖杯：</span>
            <span class="text-base font-extrabold text-accent font-mono">{{ trophyStats.unlocked }} / 77 ({{ trophyStats.percentage }}%)</span>
          </div>

          <button
            @click="emit('open-trophies')"
            class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 border border-line-strong text-xs font-bold text-fg flex items-center gap-1.5 transition"
          >
            <span>进入奖杯陈列馆</span>
            <ChevronRight class="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
