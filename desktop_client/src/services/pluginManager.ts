import { ref } from 'vue';

export interface BtSearchConfig {
  engine: 'sukebei' | '1337x' | 'torrentgalaxy' | 'google' | 'custom';
  customUrlTemplate: string;
}

export interface TranslationPluginConfig {
  targetLanguage: string;
  customPromptTemplate: string;
}

export interface PluginsConfig {
  btSearchEnabled: boolean;
  btSearchConfig: BtSearchConfig;
  customScraperEnabled: boolean;
  translationEnabled: boolean;
  translationConfig: TranslationPluginConfig;
  trophiesEnabled: boolean;
}

const PLUGINS_KEY = 'gevi_plugins_config';

const DEFAULT_CONFIG: PluginsConfig = {
  btSearchEnabled: true,
  btSearchConfig: {
    engine: 'sukebei',
    customUrlTemplate: 'https://sukebei.nyaa.si/?f=0&c=0_0&q={query}',
  },
  customScraperEnabled: true,
  translationEnabled: true,
  translationConfig: {
    targetLanguage: 'zh-CN',
    customPromptTemplate: '请将以下影视剧本、剧情简介与专有名词翻译为流畅自然的中文。保持专业影评基调，专有名词与演员艺名保留英文原名对照。',
  },
  trophiesEnabled: true,
};

function loadConfig(): PluginsConfig {
  try {
    const raw = localStorage.getItem(PLUGINS_KEY);
    if (raw) {
      return { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
    }
  } catch {}
  return DEFAULT_CONFIG;
}

export const pluginsConfig = ref<PluginsConfig>(loadConfig());

export function savePluginsConfig(updates: Partial<PluginsConfig>) {
  pluginsConfig.value = { ...pluginsConfig.value, ...updates };
  localStorage.setItem(PLUGINS_KEY, JSON.stringify(pluginsConfig.value));
}

export function buildBtSearchUrl(query: string): string {
  const cfg = pluginsConfig.value.btSearchConfig;
  const encoded = encodeURIComponent(query.trim());
  switch (cfg.engine) {
    case 'sukebei':
      return `https://sukebei.nyaa.si/?f=0&c=0_0&q=${encoded}`;
    case '1337x':
      return `https://1337x.to/search/${encoded}/1/`;
    case 'torrentgalaxy':
      return `https://torrentgalaxy.to/torrents.php?search=${encoded}`;
    case 'google':
      return `https://www.google.com/search?q=${encoded}+torrent+magnet`;
    case 'custom':
      return (cfg.customUrlTemplate || 'https://sukebei.nyaa.si/?f=0&c=0_0&q={query}').replace('{query}', encoded);
    default:
      return `https://sukebei.nyaa.si/?f=0&c=0_0&q=${encoded}`;
  }
}

export function openBtSearch(query: string) {
  const url = buildBtSearchUrl(query);
  window.open(url, '_blank');
}
