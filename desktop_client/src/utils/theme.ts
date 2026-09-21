/**
 * Theme selection.
 *
 * Seven choices in the settings panel: 跟随系统 plus three styles in a dark and a
 * light variant. Only one thing reaches the DOM — `data-theme` on `<html>` — because
 * that is the seam theme.css watches; this module owns the localStorage and the
 * matchMedia listener, nothing else.
 *
 * 跟随系统 remembers the *style* separately (`PREFS.themeStyle`), so following the
 * system means "keep 流体玻璃, track the OS light/dark switch" rather than snapping
 * back to 经典 every time. A module-level `ref` is enough state: there is exactly one
 * app shell and one settings panel, and it matches how `utils/lightbox.ts` keeps the
 * viewer's state.
 *
 * Keep the resolution in sync with the inline script in index.html — that copy exists
 * only to set the attribute before Vue mounts, and it is the one thing here that
 * cannot be imported. Both must agree on the fallback style too, or the first frame
 * is painted in one theme and corrected to another a moment later.
 */

import { computed, ref } from 'vue';

import { PREFS } from './prefs';

export type ThemeStyle = 'classic' | 'glass' | 'my';
export type ThemeId =
  | 'classic-dark'
  | 'classic-light'
  | 'glass-dark'
  | 'glass-light'
  | 'my-dark'
  | 'my-light';
export type ThemeChoice = 'auto' | ThemeId;

const CHOICE_KEY = PREFS.theme;
const STYLE_KEY = PREFS.themeStyle;

const STYLES: Record<ThemeStyle, string> = {
  classic: '经典',
  glass: '流体玻璃',
  my: 'Material You',
};

export interface ThemeOption {
  id: ThemeChoice;
  label: string;
  /** One line under the label: what the family does, or what 跟随系统 resolved to. */
  hint: string;
  /** Page, panel and accent, for the swatch strip. The values theme.css ships;
   *  duplicated here because a preview has to draw colours it is not yet wearing. */
  swatch: [string, string, string];
}

const SWATCH: Record<ThemeId, [string, string, string]> = {
  'classic-dark': ['oklch(14.1% 0.005 285.823)', 'oklch(21% 0.006 285.885)', 'oklch(82.8% 0.189 84.429)'],
  'classic-light': ['oklch(97.5% 0 0)', '#ffffff', 'oklch(55.5% 0.163 48.998)'],
  'glass-dark': ['oklch(13.5% 0.012 275)', 'oklch(24% 0.012 275 / 0.62)', 'oklch(87.9% 0.169 91.605)'],
  'glass-light': ['oklch(96.5% 0.008 250)', 'oklch(100% 0 0 / 0.62)', 'oklch(47.3% 0.137 46.201)'],
  'my-dark': ['oklch(15.5% 0.012 70)', 'oklch(21% 0.014 70)', 'oklch(88% 0.1 88)'],
  'my-light': ['oklch(97.5% 0.014 80)', 'oklch(99% 0.006 80)', 'oklch(45% 0.13 55)'],
};

const HINTS: Record<ThemeId, string> = {
  'classic-dark': '应用原本的配色，不透明浮层。',
  'classic-light': '浅色底 + 白色卡片，浮层不透明。',
  'glass-dark': '半透明浮层与背景模糊，深色壁纸。',
  'glass-light': '半透明浮层与背景模糊，浅色壁纸。',
  'my-dark': '由品牌琥珀金推导的暖色调，靠色调分层。',
  'my-light': '暖白底 + 深琥珀主色，靠色调分层。',
};

function isThemeId(v: string | null): v is ThemeId {
  return !!v && v in SWATCH;
}

function readChoice(): ThemeChoice {
  const stored = localStorage.getItem(CHOICE_KEY);
  return stored === 'auto' || isThemeId(stored) ? stored : 'auto';
}

/**
 * What 跟随系统 resolves to before the user has chosen anything.
 *
 * 流体玻璃 rather than 经典: the glass family is the app's intended look, and a fresh
 * install should open on it. Someone who explicitly picks 经典 still gets 经典 — this
 * is only the value for "no preference recorded".
 *
 * index.html hardcodes this same style in its pre-paint script; the two must agree.
 */
export const DEFAULT_STYLE: ThemeStyle = 'glass';

function readStyle(): ThemeStyle {
  const stored = localStorage.getItem(STYLE_KEY);
  return stored === 'classic' || stored === 'glass' || stored === 'my' ? stored : DEFAULT_STYLE;
}

function styleOf(id: ThemeId): ThemeStyle {
  return id.split('-')[0] as ThemeStyle;
}

/** The OS preference. Read once at module load; the listener below keeps it current. */
const prefersDark = ref(
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : true
);

/** The style 跟随系统 should keep — the last concrete theme's family. */
const rememberedStyle = ref<ThemeStyle>(readStyle());

export const themeChoice = ref<ThemeChoice>(readChoice());

/** The concrete theme in the DOM, i.e. what 跟随系统 currently resolves to. */
export const resolvedTheme = computed<ThemeId>(() => {
  if (themeChoice.value !== 'auto') return themeChoice.value;
  return `${rememberedStyle.value}-${prefersDark.value ? 'dark' : 'light'}` as ThemeId;
});

function apply() {
  document.documentElement.dataset.theme = resolvedTheme.value;
}

export function setTheme(choice: ThemeChoice) {
  themeChoice.value = choice;
  localStorage.setItem(CHOICE_KEY, choice);
  if (choice !== 'auto') {
    // So that going back to 跟随系统 keeps this family rather than 经典.
    rememberedStyle.value = styleOf(choice);
    localStorage.setItem(STYLE_KEY, rememberedStyle.value);
  }
  apply();
}

/** The theme behind 跟随系统, in words: "经典 · 浅". */
export const autoLabel = computed(
  () => `${STYLES[rememberedStyle.value]} · ${prefersDark.value ? '暗' : '浅'}`
);

export const themeOptions = computed<ThemeOption[]>(() => [
  {
    id: 'auto',
    label: '跟随系统',
    hint: `跟随 macOS 的浅色/深色设置，当前：${autoLabel.value}。`,
    swatch: SWATCH[resolvedTheme.value],
  },
  ...(['classic', 'glass', 'my'] as ThemeStyle[]).flatMap(style =>
    (['dark', 'light'] as const).map(scheme => {
      const id = `${style}-${scheme}` as ThemeId;
      return {
        id,
        label: `${STYLES[style]} · ${scheme === 'dark' ? '暗' : '浅'}`,
        hint: HINTS[id],
        swatch: SWATCH[id],
      };
    })
  ),
]);

/**
 * Watches the OS preference and re-applies. Called once from the app shell; the
 * listener never fires while a concrete theme is chosen, since `resolvedTheme`
 * ignores `prefersDark` in that case.
 */
export function initTheme() {
  const media = window.matchMedia('(prefers-color-scheme: dark)');
  media.addEventListener('change', (e) => {
    prefersDark.value = e.matches;
    apply();
  });
  // The inline script in index.html already set this attribute; re-applying costs
  // nothing and guarantees the DOM matches after a hot reload or a restored session.
  apply();
}
