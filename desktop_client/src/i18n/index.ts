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
  i18nKey: string;
  native: string;
}

export const SUPPORTED_LANGUAGES: LanguageOption[] = [
  { code: 'zh-CN', i18nKey: 'lang.zhCN', native: '简体中文' },
  { code: 'zh-TW', i18nKey: 'lang.zhTW', native: '繁體中文' },
  { code: 'en', i18nKey: 'lang.en', native: 'English' },
  { code: 'ja', i18nKey: 'lang.ja', native: '日本語' },
  { code: 'it', i18nKey: 'lang.it', native: 'Italiano' },
  { code: 'es', i18nKey: 'lang.es', native: 'Español' },
  { code: 'de', i18nKey: 'lang.de', native: 'Deutsch' },
];

export const TARGET_TRANSLATION_LANGUAGES = [
  { code: 'zh-CN', i18nKey: 'lang.zhCN', native: '中文 (简体)' },
  { code: 'zh-TW', i18nKey: 'lang.zhTW', native: '中文 (繁體)' },
  { code: 'en', i18nKey: 'lang.en', native: 'English' },
  { code: 'ja', i18nKey: 'lang.ja', native: '日本語' },
  { code: 'it', i18nKey: 'lang.it', native: 'Italiano' },
  { code: 'es', i18nKey: 'lang.es', native: 'Español' },
  { code: 'de', i18nKey: 'lang.de', native: 'Deutsch' },
  { code: 'fr', i18nKey: 'lang.fr', native: 'Français' },
];

export function detectSystemLocale(): SupportedLocale {
  if (typeof navigator === 'undefined' || !navigator.language) {
    return 'en';
  }
  const tag = navigator.language.toLowerCase();
  if (tag.startsWith('zh-tw') || tag.startsWith('zh-hk') || tag.startsWith('zh-mo') || tag.startsWith('zh-hant')) {
    return 'zh-TW';
  }
  if (tag.startsWith('zh')) {
    return 'zh-CN';
  }
  if (tag.startsWith('ja')) {
    return 'ja';
  }
  if (tag.startsWith('it')) {
    return 'it';
  }
  if (tag.startsWith('es')) {
    return 'es';
  }
  if (tag.startsWith('de')) {
    return 'de';
  }
  return 'en';
}

const STORAGE_KEY = 'gpdb_ui_locale';
const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null;
const initialLocale: SupportedLocale = (stored as SupportedLocale) || detectSystemLocale();

export const currentLocale = ref<SupportedLocale>(initialLocale);

export function setLocale(locale: SupportedLocale) {
  currentLocale.value = locale;
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(STORAGE_KEY, locale);
  }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = locale;
  }
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
