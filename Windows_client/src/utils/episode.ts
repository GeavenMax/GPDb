/** How an episode names itself on a card or a heading.
 *
 * The site titles every single episode "Episode #<its own row id>" — all 15,107 of
 * them, verified against the database — so the title says nothing about where the
 * scene sits in its film. `episode_ordinal`, the scene's rank inside its own film, is
 * what a card can usefully show ("第 3 集 / 共 5 集").
 *
 * Both fields are optional: only the two library-shaped payloads carry them. A film's
 * own episode list and the favourites list send plain episodes, which fall back to
 * whatever the title happens to be.
 */
export interface EpisodeNaming {
  title?: string | null;
  episode_ordinal?: number | null;
  episode_count?: number | null;
}

import { t, currentLocale } from '../i18n';

/** "第 3 集 / 共 5 集", "第 3 集", or null when the payload carries no ordinal. */
export function episodeOrdinalLabel(ep: EpisodeNaming): string | null {
  if (!ep.episode_ordinal) return null;
  if (ep.episode_count && ep.episode_count > 1) {
    return t('episode.ordinalWithCount', { ordinal: ep.episode_ordinal, count: ep.episode_count });
  }
  return t('episode.ordinal', { ordinal: ep.episode_ordinal });
}

/** The short label for a card badge or a list row. */
export function episodeLabel(ep: EpisodeNaming): string {
  return episodeOrdinalLabel(ep) || ep.title || t('episode.unnamed');
}

/**
 * The heading for the episode's own page: the film first, because "《片名》" is the
 * part that identifies the scene, then where in that film it sits.
 */
export function episodeHeading(ep: EpisodeNaming, movieTitle?: string | null): string {
  const position = episodeOrdinalLabel(ep);
  const isZh = currentLocale.value.startsWith('zh');
  if (movieTitle && position) {
    return isZh ? `《${movieTitle}》· ${position}` : `"${movieTitle}" · ${position}`;
  }
  if (movieTitle) {
    return isZh ? `《${movieTitle}》` : `"${movieTitle}"`;
  }
  return episodeLabel(ep);
}
