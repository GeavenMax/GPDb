/**
 * Every localStorage key the app owns, in one place.
 *
 * They used to be string literals scattered across App.vue, Sidebar.vue and theme.ts,
 * which meant a rename had to be done by hand in fourteen places without missing one —
 * and a miss is invisible: the preference silently resets, and the user concludes the
 * app forgot their settings. Naming them here makes the rename a single edit.
 *
 * The `gpdb_` prefix replaced `gpdb_` when the client was renamed from GPDb+ to GPDb.
 * `migrate()` below carries old values over; it runs as a side effect of importing this
 * module, so any module that reads a preference is guaranteed to see migrated values —
 * theme.ts reads localStorage at module scope, before any function could be called, and
 * this is what makes that safe.
 *
 * index.html keeps its own two-key copy of the migration for `theme`/`themeStyle` only:
 * it paints before the bundle loads and cannot import. See the comment there.
 */

export const PREFS = {
  /** 'auto' or a concrete ThemeId — see utils/theme.ts. */
  theme: 'gpdb_theme',
  /** Which style family 跟随系统 keeps: 'classic' | 'glass' | 'my'. */
  themeStyle: 'gpdb_theme_style',
  /** 'zh' (show Chinese) or 'en' (show the original). */
  descLang: 'gpdb_desc_lang',
  gridCols: 'gpdb_grid_cols',
  listCols: 'gpdb_list_cols',
  /** Translation batching: 'single' or 'batch'. */
  translateMode: 'gpdb_translate_mode',
  pageSize: 'gpdb_page_size',
  /** 'scroll' or 'paged'. */
  listMode: 'gpdb_list_mode',
  /** Sidebar order, a JSON array of nav ids — see components/Sidebar.vue. */
  navOrder: 'gpdb_nav_order',
} as const;

export type PrefName = keyof typeof PREFS;

/** The GPDb+ key each preference lived under before the rename. */
const LEGACY: Record<PrefName, string> = {
  theme: 'gpdb_theme',
  themeStyle: 'gpdb_theme_style',
  descLang: 'gpdb_desc_lang',
  gridCols: 'gpdb_grid_cols',
  listCols: 'gpdb_list_cols',
  translateMode: 'gpdb_translate_mode',
  pageSize: 'gpdb_page_size',
  listMode: 'gpdb_list_mode',
  navOrder: 'gpdb_nav_order',
};

/**
 * Carries values written under the GPDb+ names over to the GPDb names, once.
 *
 * A value already at the new key always wins — so this is safe to run on every start,
 * and safe to run twice (index.html does the theme pair before the bundle loads).
 * The old key is removed rather than left behind: keeping both would leave a stale
 * copy that the next rename would migrate from, resurrecting a preference the user
 * has since changed.
 */
export function migrateLegacyPrefs(): void {
  try {
    for (const name of Object.keys(PREFS) as PrefName[]) {
      const to = PREFS[name];
      const from = LEGACY[name];
      if (localStorage.getItem(to) === null) {
        const previous = localStorage.getItem(from);
        if (previous !== null) localStorage.setItem(to, previous);
      }
      localStorage.removeItem(from);
    }
  } catch {
    // Storage can be blocked or full (private window, hardened settings). Every
    // preference has a working default, so the app runs correctly without any of it.
  }
}

migrateLegacyPrefs();
