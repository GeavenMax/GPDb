<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { X, Maximize2, Scan } from '@lucide/vue';
import { claimEscape } from '../utils/escape';
import { closeLightbox, lightboxImage } from '../utils/lightbox';

/**
 * Fit the window, or show the file at its own pixel size.
 *
 * The site's cover art is only available at one size (see the image-quality
 * finding), so "enlarged" here means shown as large as the file allows — the
 * toggle is what lets a viewer get closer than the window's fit-to-screen, and it
 * resets on every open rather than being remembered.
 */
const actualSize = ref(false);

watch(lightboxImage, () => {
  actualSize.value = false;
});

/**
 * Escape is claimed in the capture phase.
 *
 * The detail modals listen on window too, and a plain listener here would be
 * registered last and lose to them — closing the page underneath while the viewer
 * is what the user is looking at.
 */
function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape' || !lightboxImage.value) return;
  if (!claimEscape(e)) return;
  closeLightbox();
}

onMounted(() => window.addEventListener('keydown', onKeydown, true));
onUnmounted(() => window.removeEventListener('keydown', onKeydown, true));
</script>

<template>
  <div
    v-if="lightboxImage"
    class="fixed inset-0 z-[200] bg-scrim/95 backdrop-blur-sm flex flex-col animate-fade-in"
  >
    <!-- Header: the alt text doubles as the caption, so a grid cover opened here
         still says which film it belongs to. -->
    <div class="flex items-center justify-between gap-4 p-3 shrink-0">
      <span class="text-xs text-fg-3 truncate">{{ lightboxImage.alt }}</span>
      <div class="flex items-center gap-2 shrink-0">
        <button
          @click="actualSize = !actualSize"
          class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-surface/80 border border-line-strong text-[11px] text-fg-2 hover:text-fg hover:bg-surface-2 transition"
        >
          <component :is="actualSize ? Scan : Maximize2" class="w-3.5 h-3.5" />
          <span>{{ actualSize ? '适应屏幕' : '原始尺寸' }}</span>
        </button>
        <button
          @click="closeLightbox"
          title="关闭 (Esc)"
          class="p-1.5 rounded-lg bg-surface/80 border border-line-strong text-fg-2 hover:text-fg hover:bg-surface-2 transition"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- The inner wrapper is at least the viewport size, which both centres a
         smaller image and gives a click target around it for "click outside to
         close"; at actual size it grows with the image and scrolls. -->
    <div class="flex-1 overflow-auto">
      <div class="min-w-full min-h-full flex items-center justify-center p-4" @click.self="closeLightbox">
        <img
          :src="lightboxImage.src"
          :alt="lightboxImage.alt"
          referrerpolicy="no-referrer"
          @click="actualSize = !actualSize"
          @dblclick.stop
          :class="[
            'rounded-lg shadow-2xl transition-transform duration-150',
            actualSize
              ? 'max-w-none max-h-none cursor-zoom-out'
              : 'max-w-full max-h-full object-contain cursor-zoom-in',
          ]"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.animate-fade-in {
  animation: fadeIn 0.15s ease-out forwards;
}
</style>
