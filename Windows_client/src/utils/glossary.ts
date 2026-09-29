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

/**
 * The `<br />` spellings the site uses as a multi-value separator, enumerated in every
 * case exactly as `BR_SPELLINGS` (`gpdb-core/src/sql.rs`) and `_BR_SPELLINGS`
 * (`server.py`) do.
 *
 * Three places have to agree on this set — the two SQL expressions that turn a column
 * into `|a|b|`, the splitters, and this. They did not always: the Rust splitter matched
 * lower case only, Python's regex was case-insensitive, and both SQL expressions
 * matched lower case only, so a value one side split the other could not match. A probe
 * test (`separator_sets_agree_on_every_probe`) fails if any of the three drifts again.
 *
 * Built from the list rather than written as `/<br\s*\/?>/i` so that the set stays
 * enumerable — `\s*` could match two spaces, which no REPLACE chain can express. None
 * of these characters is a regex metacharacter, so joining them is safe as-is.
 */
const BR_SPELLINGS = [
  '<br />', '<bR />', '<Br />', '<BR />',
  '<br/>', '<bR/>', '<Br/>', '<BR/>',
  '<br>', '<bR>', '<Br>', '<BR>',
];
const BR_RE = new RegExp(BR_SPELLINGS.join('|'), 'g');

const terms = ref<Record<string, string>>({});
/**
 * Film categories, kept apart from `terms` rather than merged into one map.
 *
 * `Muscle`, `Twink` and `Bareback` are all plausible as either an attribute or a
 * category; today's 53 categories and ~76 attributes happen to be disjoint, but that
 * is a coincidence of the data, not a property of it — and a `tr()` that had to guess
 * would start mixing them the moment the site added one term to both.
 */
const categories = ref<Record<string, string>>({});
let loadPromise: Promise<void> | null = null;

/** Fetch the glossaries once. Later calls reuse the same promise. */
export function loadGlossary(force = false): Promise<void> {
  if (force) loadPromise = null;
  if (!loadPromise) {
    loadPromise = api.getGlossary().then((g) => {
      terms.value = g.terms;
      categories.value = g.categories;
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

/** How many categories have a Chinese label; 0 until `translate.py --categories` has run. */
export function categoryCount(): number {
  return Object.keys(categories.value).length;
}

/**
 * A film's category, translated term by term.
 *
 * `movies.category` is the site's raw multi-value string, so "Wrestling<br />J/O" is
 * one category column but two labels — each is looked up on its own and the results
 * are rejoined with `、` (the Chinese enumeration comma; the source's `<br />` is a
 * separator, not content). Matching the separator set is not guesswork: it is the same
 * enumeration the SQL and the Rust splitter use, and `separator_sets_agree_on_every_probe`
 * fails if the three ever drift.
 *
 * The whole glossary is empty until the category translation has been run, and each
 * term falls back to itself, so an untranslated library renders exactly what it renders
 * today rather than a row of blanks.
 */
export function trCategory(raw: string | null | undefined): string {
  if (!raw) return '';
  return raw
    .split(BR_RE)
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t) => categories.value[t] || t)
    .join('、');
}

/**
 * Units inside the measurement attributes (height / weight / dick size).
 *
 * Matches the digits and the unit together so `in` can only be the inch
 * abbreviation here and never a stray preposition, and refuses to match when a
 * letter follows so it cannot bite into a longer word.
 */
const MEASURE_RE = /(\d[\d.\-]*)\s*(ft|in|lbs|kg|cm)(?![A-Za-z])/gi;

/**
 * Rewrite the units in a measurement value: "5ft 10in / 178cm" → "5英尺 10英寸 / 178厘米".
 *
 * The values already carry both systems, and which values exist is open-ended —
 * "6ft 2in / 188cm" for one performer, a malformed "6-00ft 6-00in / 19812cm" for
 * another, something new after every re-scrape. So only the unit token is
 * translated: five glossary entries cover every measurement in the library,
 * forever. Digits, separators and anything unrecognised pass through untouched,
 * and a unit with no entry yet falls back to itself, so this is a no-op until the
 * glossary has been run.
 */
export function trMeasure(value: string | null | undefined): string {
  if (!value) return '';
  return value.replace(MEASURE_RE, (_match, digits: string, unit: string) => digits + tr(unit.toLowerCase()));
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
