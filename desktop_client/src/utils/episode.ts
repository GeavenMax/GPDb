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

/** "第 3 集 / 共 5 集", "第 3 集", or null when the payload carries no ordinal. */
export function episodeOrdinalLabel(ep: EpisodeNaming): string | null {
  if (!ep.episode_ordinal) return null;
  if (ep.episode_count && ep.episode_count > 1) {
    return `第 ${ep.episode_ordinal} 集 / 共 ${ep.episode_count} 集`;
  }
  return `第 ${ep.episode_ordinal} 集`;
}

/** The short label for a card badge or a list row. */
export function episodeLabel(ep: EpisodeNaming): string {
  return episodeOrdinalLabel(ep) || ep.title || '未命名分集';
}

/**
 * The heading for the episode's own page: the film first, because "《片名》" is the
 * part that identifies the scene, then where in that film it sits.
 */
export function episodeHeading(ep: EpisodeNaming, movieTitle?: string | null): string {
  const position = episodeOrdinalLabel(ep);
  if (movieTitle && position) return `《${movieTitle}》· ${position}`;
  if (movieTitle) return `《${movieTitle}》`;
  return episodeLabel(ep);
}
