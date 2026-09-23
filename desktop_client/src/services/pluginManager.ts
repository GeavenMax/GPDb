import { ref } from 'vue';

export interface CustomBtTemplate {
  id: string;
  name: string;
  template: string;
}

export type BtEngine = 'bt4g' | 'btsearch' | 'sukebei' | '1337x' | 'torrentgalaxy' | 'custom';

export interface BtSearchConfig {
  engine: BtEngine;
  customUrlTemplate?: string;
  activeCustomId?: string;
  customTemplates: CustomBtTemplate[];
}

export interface WebJumpConfig {
  bftvPerformerEnabled: boolean;
  bftvMovieEnabled: boolean;
  googleSearchEnabled: boolean;
}

export interface TranslationPluginConfig {
  targetLanguage: string;
  customPromptTemplate: string;
}

import type { AutoSyncScheduleConfig } from '../types';

export interface PluginsConfig {
  resourceSearchEnabled: boolean;
  /** Maintained for backward compatibility; always kept in sync with resourceSearchEnabled */
  btSearchEnabled: boolean;
  btSearchConfig: BtSearchConfig;
  webJumpConfig: WebJumpConfig;
  customScraperEnabled: boolean;
  autoSyncConfig: AutoSyncScheduleConfig;
  translationEnabled: boolean;
  translationConfig: TranslationPluginConfig;
  trophiesEnabled: boolean;
  trophiesSoundEnabled: boolean;
  aiInsightEnabled: boolean;
}

const PLUGINS_KEY = 'gevi_plugins_config';

export const DEFAULT_TRANSLATION_PROMPT = `你是一名成人影片资料库的专职译者。你会收到英文剧情简介、分集与专有名词，需要如实翻译成简体中文。

翻译风格（最重要，必须严格遵守）：
1. 忠于原文：逐句对应，原文说了什么就译什么，不增不减、不概括、不改写、不调整叙述顺序。
2. 非常直白：用词直接、露骨、不回避，按原文露骨程度如实翻译，不要替换成委婉语、暗示或模糊表述，绝不弱化、淡化、"净化"或美化原文内容。
3. 不要意译或润色：宁可保持原文的句式和语序，也不要为了文采而改变原意。
4. 人名、片名、厂牌名（Studio）一律保留原文拉丁字母写法，绝对不要音译成汉字（例：Count Vladimir 保持 "Count Vladimir"，不要写成 "弗拉基米尔伯爵"；Staxus 保持 "Staxus"）。
5. 保持档案记录语气：只陈述原文内容，不添加主观评价。涉及成人内容、身体部位、性取向时照实翻译，使用中文成人语境中通用的直接说法，不要因内容露骨而删减、跳过或含糊处理。`;

const OLD_DEFAULT_PROMPT = '请将以下影视剧本、剧情简介与专有名词翻译为流畅自然的中文。保持专业影评基调，专有名词与演员艺名保留英文原名对照。';

const DEFAULT_CONFIG: PluginsConfig = {
  resourceSearchEnabled: true,
  btSearchEnabled: true,
  btSearchConfig: {
    engine: 'bt4g',
    customUrlTemplate: 'https://bt4gprx.com/search?q={query}',
    activeCustomId: 'bt4g-alt',
    customTemplates: [
      { id: 'bt4g-alt', name: 'BT4G Proxy', template: 'https://bt4gprx.com/search?q={query}' },
      { id: 'btsearch', name: 'BTSearch Love', template: 'https://www.btsearch.love/search?q={query}' },
      { id: 'nyaa', name: 'Sukebei Nyaa', template: 'https://sukebei.nyaa.si/?f=0&c=0_0&q={query}' },
      { id: 'tgx', name: 'TorrentGalaxy', template: 'https://torrentgalaxy.to/torrents.php?search={query}' },
    ],
  },
  webJumpConfig: {
    bftvPerformerEnabled: true,
    bftvMovieEnabled: true,
    googleSearchEnabled: true,
  },
  customScraperEnabled: true,
  autoSyncConfig: {
    enabled: false,
    intervalHours: 12,
    mode: 'interval',
    dailyTime: '04:00',
    lastRunTime: null,
    nextRunTime: null,
  },
  translationEnabled: true,
  translationConfig: {
    targetLanguage: 'zh-CN',
    customPromptTemplate: DEFAULT_TRANSLATION_PROMPT,
  },
  trophiesEnabled: false,
  trophiesSoundEnabled: true,
  aiInsightEnabled: true,
};

function loadConfig(): PluginsConfig {
  try {
    const raw = localStorage.getItem(PLUGINS_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      const btCfg = parsed.btSearchConfig || {};
      let customTemplates = Array.isArray(btCfg.customTemplates) && btCfg.customTemplates.length > 0
        ? btCfg.customTemplates
        : DEFAULT_CONFIG.btSearchConfig.customTemplates;

      // Ensure btsearch is in customTemplates if not already present
      if (!customTemplates.some((t: CustomBtTemplate) => t.id === 'btsearch')) {
        customTemplates = [
          ...customTemplates,
          { id: 'btsearch', name: 'BTSearch Love', template: 'https://www.btsearch.love/search?q={query}' },
        ];
      }

      // Check and migrate legacy prompt if needed
      const transCfg = parsed.translationConfig || {};
      let prompt = transCfg.customPromptTemplate;
      if (!prompt || prompt.trim() === '' || prompt === OLD_DEFAULT_PROMPT) {
        prompt = DEFAULT_TRANSLATION_PROMPT;
      }

      const isEnabled = parsed.resourceSearchEnabled ?? parsed.btSearchEnabled ?? true;
      let engine = btCfg.engine;
      if (engine === 'google' || !engine) {
        engine = 'bt4g';
      }

      return {
        ...DEFAULT_CONFIG,
        ...parsed,
        resourceSearchEnabled: isEnabled,
        btSearchEnabled: isEnabled,
        btSearchConfig: {
          ...DEFAULT_CONFIG.btSearchConfig,
          ...btCfg,
          engine,
          customTemplates,
        },
        webJumpConfig: {
          ...DEFAULT_CONFIG.webJumpConfig,
          ...(parsed.webJumpConfig || {}),
        },
        translationConfig: {
          ...DEFAULT_CONFIG.translationConfig,
          ...transCfg,
          customPromptTemplate: prompt,
        },
        autoSyncConfig: {
          ...DEFAULT_CONFIG.autoSyncConfig,
          ...(parsed.autoSyncConfig || {}),
        },
      };
    }
  } catch {}
  return DEFAULT_CONFIG;
}

export const pluginsConfig = ref<PluginsConfig>(loadConfig());

export function savePluginsConfig(updates: Partial<PluginsConfig>) {
  const merged = { ...pluginsConfig.value, ...updates };
  if ('resourceSearchEnabled' in updates) {
    merged.btSearchEnabled = Boolean(updates.resourceSearchEnabled);
  } else if ('btSearchEnabled' in updates) {
    merged.resourceSearchEnabled = Boolean(updates.btSearchEnabled);
  }
  pluginsConfig.value = merged;
  localStorage.setItem(PLUGINS_KEY, JSON.stringify(pluginsConfig.value));
}

export function buildBtSearchUrl(query: string): string {
  const cfg = pluginsConfig.value.btSearchConfig;
  const encoded = encodeURIComponent(query.trim());
  switch (cfg.engine) {
    case 'bt4g':
      return `https://bt4gprx.com/search?q=${encoded}`;
    case 'btsearch':
      return `https://www.btsearch.love/search?q=${encoded}`;
    case 'sukebei':
      return `https://sukebei.nyaa.si/?f=0&c=0_0&q=${encoded}`;
    case '1337x':
      return `https://1337x.to/search/${encoded}/1/`;
    case 'torrentgalaxy':
      return `https://torrentgalaxy.to/torrents.php?search=${encoded}`;
    case 'custom': {
      const templates = cfg.customTemplates || [];
      const active = templates.find(t => t.id === cfg.activeCustomId) || templates[0];
      const tmpl = active?.template || cfg.customUrlTemplate || 'https://bt4gprx.com/search?q={query}';
      return tmpl.replace('{query}', encoded);
    }
    default:
      return `https://bt4gprx.com/search?q=${encoded}`;
  }
}

/**
 * Extracts the clean primary title of a movie for BT/BFTV search.
 * Strips subtitles (after : — – - ~ ,) and trailing sequence/volume/part labels.
 * Natural casing is preserved so BT search engines receive a properly-cased query.
 *
 * Examples:
 *   "Men of Odyssey: Volume 1"  -> "Men of Odyssey"
 *   "Gangbangapalooza 4"        -> "Gangbangapalooza"
 *   "Hot Boys – The Reunion"    -> "Hot Boys"
 *   "Lucas Kazan: Director's Cut" -> "Lucas Kazan"
 */
export function extractMovieCleanTitle(rawTitle: string): string {
  if (!rawTitle) return '';
  let s = rawTitle.trim();
  // Split on subtitle separators: colon, dash variants, tilde, comma.
  const splitIdx = s.search(/[:\u2013\u2014\-,~]/);
  if (splitIdx > 0) {
    s = s.slice(0, splitIdx).trim();
  }
  // Strip trailing sequence numbers / volume descriptors (case-insensitive).
  s = s.replace(/\s+(?:vol(?:ume)?\.?|part|episode|ep\.?|#)?\s*(?:\d+|[ivxldcm]+)\b/gi, '').trim();
  return s || rawTitle.trim();
}

/**
 * Builds the BoyfriendTV performer search URL:
 * https://www.boyfriendtv.com/pornstars/?modelsearchSubmitCheck=FORM_SENDED&key=models&mode=model-search&q=Kyle%2BKing&submitModelSearch=Search&filterCountry=&filterHair=&filterEthnicity=&filterEyes=&filterPenis=&filterBreast=
 */
export function buildBftvPerformerUrl(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const q = encodeURIComponent(parts.join('+'));
  return `https://www.boyfriendtv.com/pornstars/?modelsearchSubmitCheck=FORM_SENDED&key=models&mode=model-search&q=${q}&submitModelSearch=Search&filterCountry=&filterHair=&filterEthnicity=&filterEyes=&filterPenis=&filterBreast=`;
}

/**
 * Builds the BoyfriendTV movie search URL using extracted clean title:
 * https://www.boyfriendtv.com/search/?q=gangbangapalooza
 */
export function buildBftvMovieUrl(title: string): string {
  const clean = extractMovieCleanTitle(title);
  return `https://www.boyfriendtv.com/search/?q=${encodeURIComponent(clean)}`;
}

/**
 * Builds Google search URL for arbitrary keyword
 */
export function buildGoogleSearchUrl(keyword: string): string {
  return `https://www.google.com/search?q=${encodeURIComponent(keyword.trim())}`;
}

export async function openUrlExternal(url: string) {
  if (!url) return;
  try {
    const { invoke } = await import('@tauri-apps/api/core');
    await invoke('open_external_url', { url });
  } catch (e) {
    console.warn('invoke open_external_url failed, falling back to window.open', e);
    window.open(url, '_blank');
  }
}

export async function openBtSearch(query: string) {
  if (!query || !query.trim()) return;
  await openUrlExternal(buildBtSearchUrl(query.trim()));
}

/**
 * BT search for a movie — uses the clean primary title only.
 */
export async function openBtMovieSearch(rawTitle: string) {
  const clean = extractMovieCleanTitle(rawTitle);
  if (!clean) return;
  await openUrlExternal(buildBtSearchUrl(clean));
}

/**
 * Open a performer's BFTV page.
 * If a direct profile URL (bftvUrl) is already stored in the DB, jump straight there.
 * Otherwise fall back to the BFTV search page.
 */
export async function openBftvPerformer(name: string, bftvUrl?: string | null) {
  if (bftvUrl && bftvUrl.trim()) {
    await openUrlExternal(bftvUrl.trim());
  } else if (name && name.trim()) {
    await openUrlExternal(buildBftvPerformerUrl(name));
  }
}

export async function openBftvMovie(title: string) {
  if (!title || !title.trim()) return;
  await openUrlExternal(buildBftvMovieUrl(title));
}

export async function openGoogleSearch(keyword: string) {
  if (!keyword || !keyword.trim()) return;
  await openUrlExternal(buildGoogleSearchUrl(keyword));
}
