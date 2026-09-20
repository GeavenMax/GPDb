/**
 * One Escape, one dismissal.
 *
 * Detail modals stack — a performer's filmography opens a movie and that movie
 * opens a performer — and every mounted modal listens for Escape on `window`.
 * Closing the top one promotes the next, and Vue flushes that prop update
 * between the two listeners of the *same* keydown, so the newly-promoted modal
 * would see itself as topmost and dismiss too, collapsing the whole stack.
 *
 * Marking the event itself settles it: the first modal to act on a keypress
 * claims it, and any later listener in the same dispatch backs off.
 */
const CLAIMED = Symbol('gevi.escapeClaimed');

/** True for the first caller to claim this keypress, false for every later one. */
export function claimEscape(e: KeyboardEvent): boolean {
  const claimed = e as KeyboardEvent & { [CLAIMED]?: boolean };
  if (claimed[CLAIMED]) return false;
  claimed[CLAIMED] = true;
  return true;
}
