import { ref } from 'vue';
import { recordLangSwitch } from '../services/analytics';
import zhCN from './locales/zh-CN';
import zhTW from './locales/zh-TW';
import en from './locales/en';
import ja from './locales/ja';
import it from './locales/it';
import es from './locales/es';
import de from './locales/de';

export type SupportedLocale = 'zh-CN' | 'en' | 'it' | 'zh-TW' | 'ja' | 'es' | 'de';

export interface LanguageOption {
  code: SupportedLocale;
  label: string;
  native: string;
}

export const SUPPORTED_LANGUAGES: LanguageOption[] = [
  { code: 'zh-CN', label: '简体中文', native: '简体中文' },
  { code: 'zh-TW', label: '繁体中文', native: '繁體中文' },
  { code: 'en', label: '英语', native: 'English' },
  { code: 'ja', label: '日语', native: '日本語' },
  { code: 'it', label: '意大利语', native: 'Italiano' },
  { code: 'es', label: '西班牙语', native: 'Español' },
  { code: 'de', label: '德语', native: 'Deutsch' },
];

export const TARGET_TRANSLATION_LANGUAGES = [
  { code: 'zh-CN', label: '中文 (简体)' },
  { code: 'zh-TW', label: '中文 (繁體)' },
  { code: 'en', label: 'English' },
  { code: 'ja', label: '日本語' },
  { code: 'it', label: 'Italiano' },
  { code: 'es', label: 'Español' },
  { code: 'de', label: 'Deutsch' },
  { code: 'fr', label: 'Français' },
];

const STORAGE_KEY = 'gpdb_ui_locale';
const savedLocale = (localStorage.getItem(STORAGE_KEY) as SupportedLocale) || 'zh-CN';

export const currentLocale = ref<SupportedLocale>(savedLocale);

export function setLocale(locale: SupportedLocale) {
  currentLocale.value = locale;
  localStorage.setItem(STORAGE_KEY, locale);
  document.documentElement.lang = locale;
  try {
    recordLangSwitch();
  } catch {}
}

const MESSAGES: Record<SupportedLocale, Record<string, string>> = {
  'zh-CN': zhCN,
  'zh-TW': zhTW,
  'en': en,
  'ja': ja,
  'it': it,
  'es': es,
  'de': de,
};

export function t(
  key: string,
  paramsOrDefault?: Record<string, any> | string,
  defaultVal?: string
): string {
  const loc = currentLocale.value;
  // Intelligent Fallback Chain:
  // 1. Target locale
  // 2. English (for non-Chinese locales to prevent jarring fallback to Chinese)
  // 3. Simplified Chinese (root development language)
  // 4. Default value / Key name
  let text = MESSAGES[loc]?.[key];
  if (!text && loc !== 'en' && !loc.startsWith('zh')) {
    text = MESSAGES['en']?.[key];
  }
  if (!text) {
    text = MESSAGES['zh-CN']?.[key];
  }
  if (!text) {
    if (typeof paramsOrDefault === 'string') return paramsOrDefault;
    text = defaultVal ?? key;
  }
  if (typeof paramsOrDefault === 'object' && paramsOrDefault !== null) {
    for (const [k, v] of Object.entries(paramsOrDefault)) {
      text = text.replaceAll(`{${k}}`, String(v ?? ''));
    }
  }
  return text;
}
