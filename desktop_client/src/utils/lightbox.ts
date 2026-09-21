/**
 * Full-screen image viewer state.
 *
 * Two gestures, decided by whether the image has a click action of its own:
 *
 * - An image that IS the content of a panel — a film's poster, a performer's
 *   portrait, a scene still — has nothing else to do with a click, so one click
 *   opens the viewer. Those images are marked with `ZOOM_CLICK_ATTR`.
 * - An image inside a card that already opens something — a grid cover, a cast
 *   chip — has its click spoken for. Those keep the double-click gesture, so
 *   zooming a cover still works without breaking navigation.
 *
 * Both are handled by window-level listeners in App.vue rather than a binding on
 * each of the ~13 <img> tags, because covers are rendered in a dozen places (grid
 * cards, list rows, both detail modals, the favorites page) and all of them want
 * the same behaviour. A double-click on a click-to-open cover does both things:
 * the detail page opens underneath and the viewer covers it, and Escape unwinds
 * one layer at a time.
 */

import { ref } from 'vue';

export interface LightboxImage {
  src: string;
  alt: string;
}

export const lightboxImage = ref<LightboxImage | null>(null);

/**
 * Marks an image as zoom-on-single-click. Opt-in, and checked on the element
 * itself rather than guessed from the DOM ("is an ancestor clickable?"): a marker
 * forgotten on a new image degrades to the old double-click gesture, whereas a
 * mis-detected ancestor would silently swallow a navigation click.
 */
export const ZOOM_CLICK_ATTR = 'data-zoom-click';

export function openLightbox(src: string, alt = ''): void {
  if (!src) return;
  lightboxImage.value = { src, alt };
}

export function closeLightbox(): void {
  lightboxImage.value = null;
}

/**
 * The image a click landed on, or null when it was not one worth showing.
 *
 * Broken images (naturalWidth 0) are skipped so a click on a cover that never
 * loaded does nothing, rather than opening an empty viewer over the page.
 */
export function viewableImageFrom(target: EventTarget | null): HTMLImageElement | null {
  if (!(target instanceof HTMLImageElement)) return null;
  const src = target.currentSrc || target.src;
  if (!src || target.naturalWidth === 0) return null;
  return target;
}

/** Whether this image zooms on a single click — see ZOOM_CLICK_ATTR. */
export function zoomsOnClick(img: HTMLImageElement): boolean {
  return img.hasAttribute(ZOOM_CLICK_ATTR);
}
