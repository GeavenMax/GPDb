/**
 * Bilingual display: which language a title or synopsis is shown in, and what — if
 * anything — belongs beneath it.
 *
 * Extracted for the same reason `utils/episode.ts` was: "Chinese if we have it and the
 * user asked for it, otherwise the original" had been written inline in five
 * components, and one of them (`EpisodeRow.vue`) had lost the `lang` half of the rule
 * entirely, so a row ignored the language switch that the card beside it honoured.
 *
 * Pure functions with no reactive state on purpose — titles are rendered in tight
 * loops (a 24-card page), and a `ref` here would subscribe every card to every
 * glossary load.
 */

/** Which language to show. Matches the `descLang` switch in the UI. */
export type Lang = 'zh' | 'en';

/** Anything carrying a title and an optional Chinese one — `Movie`, `FavoriteItem`. */
export interface Titled {
  title?: string | null;
  title_zh?: string | null;
}

/**
 * The Chinese text when we have it and Chinese was asked for, otherwise the original.
 *
 * Both sides are trimmed and empties count as absent: the API returns `''` as well as
 * `null` for "not translated", and a whitespace-only translation would otherwise blank
 * out text the user could have read in the original.
 */
export function pickZh(
  zh: string | null | undefined,
  original: string | null | undefined,
  lang: Lang = 'zh',
): string {
  const zhText = (zh || '').trim();
  if (lang === 'zh' && zhText) return zhText;
  return (original || '').trim();
}

/** The film's name as the card should lead with. Never empty for a real row. */
export function titlePrimary(m: Titled, lang: Lang = 'zh'): string {
  return pickZh(m.title_zh, m.title, lang);
}

/**
 * The original title to show beneath `titlePrimary`, or `''` when there is nothing to
 * put there — the caller hides the line on an empty string.
 *
 * Empty whenever the original is all we have (nothing was switched, so showing it
 * twice would be noise), and also when the translation *is* the original character for
 * character — a title the model declined to translate comes back as the English text,
 * and rendering "Police Story" over "Police Story" looks like a bug.
 */
export function titleSecondary(m: Titled, lang: Lang = 'zh'): string {
  if (lang !== 'zh') return '';
  const zh = (m.title_zh || '').trim();
  const original = (m.title || '').trim();
  if (!zh || !original || zh === original) return '';
  return original;
}

/**
 * The parent film of a scene, re-shaped as a `Titled`.
 *
 * A scene carries `movie_title` / `movie_title_zh` — the names differ from a film's
 * own because they are about its parent — so every scene card would otherwise repeat
 * the same two-field adapter. Needed because the parent film's name is the only
 * Chinese a scene card can show: the site names every episode "Episode #<row id>",
 * so an episode's own `title` is a placeholder and is never translated.
 *
 * The favourites payload is the exception that makes this take both spellings: it
 * sends the parent film's Chinese name as `title_zh`, the same key a film's own
 * translation uses, while keeping `movie_title` for the original. One adapter that
 * knows about both is better than two that differ in one line.
 */
export function sceneFilm(e: {
  movie_title?: string | null;
  movie_title_zh?: string | null;
  title_zh?: string | null;
}): Titled {
  return { title: e.movie_title, title_zh: e.movie_title_zh ?? e.title_zh };
}
