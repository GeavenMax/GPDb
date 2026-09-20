/**
 * Performer attribute glossary (English → Chinese).
 *
 * The site's attribute values come from a tiny closed vocabulary (~76 terms across
 * the seven translatable dimensions plus tattoo locations), so they are translated
 * once via Settings → 翻译术语表 and stored in the `attr_glossary` table. The client
 * loads that whole table on startup and looks terms up locally, which costs nothing
 * per performer and — unlike translating server-side — also covers the desktop build,
 * whose performer detail comes from Rust reading SQLite rather than from the API.
 *
 * Lookups are exact-match on the value exactly as the API returned it (the stored
 * values are already Title Case, e.g. `Swimmer`, `Dark Blond`), so nothing here
 * normalises case or whitespace.
 */

import { ref } from 'vue';
import { api } from '../api';

const terms = ref<Record<string, string>>({});
let loadPromise: Promise<void> | null = null;

/** Fetch the glossary once. Later calls reuse the same promise. */
export function loadGlossary(force = false): Promise<void> {
  if (force) loadPromise = null;
  if (!loadPromise) {
    loadPromise = api.getGlossary().then((t) => {
      terms.value = t;
    });
  }
  return loadPromise;
}

/** Chinese for one attribute value, falling back to the original English. */
export function tr(value: string | null | undefined): string {
  if (!value) return '';
  return terms.value[value] || value;
}

/** Whether any glossary has been loaded — used to hide the "translate glossary" hint. */
export function glossaryCount(): number {
  return Object.keys(terms.value).length;
}

/**
 * Translate only the location words out of a `tattoos` value.
 *
 * The stored format is "Deltoid left Deltoid: \"USMC\", Chest left chest: dragon",
 * i.e. comma-separated `<location>: <free text>` entries where the free text can
 * itself contain further pairs. Only the locations are in the glossary; the
 * descriptions are open text and stay in English, so each location is looked up
 * (falling back to itself) and everything from the colon on is passed through.
 */
export function trTattoo(raw: string | null | undefined): string {
  if (!raw) return '';
  return raw
    .split(',')
    .map((entry) => {
      const trimmed = entry.trim();
      if (!trimmed) return '';
      const colon = trimmed.indexOf(':');
      if (colon === -1) return tr(trimmed);
      const head = trimmed.slice(0, colon).trim();
      const tail = trimmed.slice(colon);
      // "Deltoid left Deltoid" → the first word is the comma-listed location and the
      // rest is the start of the description; translate the first word only.
      const words = head.split(' ');
      if (words.length === 1) return tr(head) + tail;
      return [tr(words[0]), ...words.slice(1)].join(' ') + tail;
    })
    .filter(Boolean)
    .join('，');
}
