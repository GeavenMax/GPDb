/**
 * Full-screen image viewer state, opened by double-clicking any image.
 *
 * Global rather than per-image on purpose: covers are rendered in a dozen places
 * (grid cards, list rows, both detail modals, the favorites page) and every one of
 * them wants the same behaviour, so one window-level dblclick listener in App.vue
 * reaches them all instead of an event binding on each of the ~11 <img> tags.
 *
 * A double-click on a cover that is itself click-to-open therefore does both
 * things: the detail page opens underneath and the viewer covers it, and Escape
 * unwinds one layer at a time.
 */

import { ref } from 'vue';

export interface LightboxImage {
  src: string;
  alt: string;
}

export const lightboxImage = ref<LightboxImage | null>(null);

export function openLightbox(src: string, alt = ''): void {
  if (!src) return;
  lightboxImage.value = { src, alt };
}

export function closeLightbox(): void {
  lightboxImage.value = null;
}

/**
 * The image a double-click landed on, or null when it was not one worth showing.
 *
 * Broken images (naturalWidth 0) are skipped so a double-click on a cover that
 * never loaded does nothing, rather than opening an empty viewer over the page.
 */
export function viewableImageFrom(target: EventTarget | null): HTMLImageElement | null {
  if (!(target instanceof HTMLImageElement)) return null;
  const src = target.currentSrc || target.src;
  if (!src || target.naturalWidth === 0) return null;
  return target;
}
