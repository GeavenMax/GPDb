<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { X, ZoomIn, ZoomOut, RotateCcw } from '@lucide/vue';
import { claimEscape } from '../utils/escape';
import { closeLightbox, lightboxImage } from '../utils/lightbox';
import { t } from '../i18n';

const zoom = ref(1.0);
const panX = ref(0);
const panY = ref(0);
const isDragging = ref(false);
const dragStart = { x: 0, y: 0 };
const panStart = { x: 0, y: 0 };

function resetTransform() {
  zoom.value = 1.0;
  panX.value = 0;
  panY.value = 0;
}

watch(lightboxImage, () => {
  resetTransform();
});

function handleZoomIn() {
  zoom.value = Math.min(5.0, Number((zoom.value + 0.5).toFixed(1)));
}

function handleZoomOut() {
  zoom.value = Math.max(1.0, Number((zoom.value - 0.5).toFixed(1)));
  if (zoom.value === 1.0) {
    panX.value = 0;
    panY.value = 0;
  }
}

function handleWheel(e: WheelEvent) {
  e.preventDefault();
  const delta = e.deltaY < 0 ? 0.25 : -0.25;
  const newZoom = Math.max(1.0, Math.min(5.0, Number((zoom.value + delta).toFixed(2))));
  zoom.value = newZoom;
  if (zoom.value <= 1.0) {
    panX.value = 0;
    panY.value = 0;
  }
}

function handleMouseDown(e: MouseEvent) {
  if (zoom.value <= 1.0) return;
  isDragging.value = true;
  dragStart.x = e.clientX;
  dragStart.y = e.clientY;
  panStart.x = panX.value;
  panStart.y = panY.value;
}

function handleMouseMove(e: MouseEvent) {
  if (!isDragging.value) return;
  const dx = e.clientX - dragStart.x;
  const dy = e.clientY - dragStart.y;
  panX.value = panStart.x + dx;
  panY.value = panStart.y + dy;
}

function handleMouseUp() {
  isDragging.value = false;
}

function handleDoubleClick() {
  if (zoom.value > 1.0) {
    resetTransform();
  } else {
    zoom.value = 2.5;
  }
}

function onKeydown(e: KeyboardEvent) {
  if (!lightboxImage.value) return;
  if (e.key === 'Escape') {
    if (!claimEscape(e)) return;
    closeLightbox();
  } else if (e.key === '=' || e.key === '+') {
    handleZoomIn();
  } else if (e.key === '-') {
    handleZoomOut();
  } else if (e.key === '0') {
    resetTransform();
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown, true);
  window.addEventListener('mouseup', handleMouseUp);
});
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown, true);
  window.removeEventListener('mouseup', handleMouseUp);
});
</script>

<template>
  <div
    v-if="lightboxImage"
    class="on-scrim fixed inset-0 z-[200] bg-black/95 backdrop-blur-md flex flex-col animate-fade-in select-none"
    @wheel="handleWheel"
  >
    <!-- Top Header Bar -->
    <div class="flex items-center justify-between gap-4 p-4 shrink-0 bg-black/40 border-b border-white/10 z-10">
      <span class="text-sm font-medium text-fg-2 truncate pl-2">{{ lightboxImage.alt || t('lightbox.title') }}</span>

      <!-- Zoom Toolbar -->
      <div class="flex items-center gap-2 shrink-0">
        <div class="flex items-center bg-surface/80 border border-line-strong rounded-xl p-0.5 shadow-lg">
          <button
            @click="handleZoomOut"
            :disabled="zoom <= 1.0"
            :title="t('lightbox.zoomOut')"
            class="p-1.5 rounded-lg text-fg-3 hover:text-fg hover:bg-surface-2 disabled:opacity-30 disabled:pointer-events-none transition"
          >
            <ZoomOut class="w-4 h-4" />
          </button>
          <button
            @click="resetTransform"
            :title="t('lightbox.reset')"
            class="px-2 py-1 text-xs font-mono font-bold text-accent hover:bg-surface-2 rounded-lg transition"
          >
            {{ Math.round(zoom * 100) }}%
          </button>
          <button
            @click="handleZoomIn"
            :disabled="zoom >= 5.0"
            :title="t('lightbox.zoomIn')"
            class="p-1.5 rounded-lg text-fg-3 hover:text-fg hover:bg-surface-2 disabled:opacity-30 disabled:pointer-events-none transition"
          >
            <ZoomIn class="w-4 h-4" />
          </button>
        </div>

        <button
          @click="resetTransform"
          :title="t('lightbox.fitScreen')"
          class="p-2 rounded-xl bg-surface/80 border border-line-strong text-fg-3 hover:text-fg hover:bg-surface-2 transition shadow-lg"
        >
          <RotateCcw class="w-4 h-4" />
        </button>

        <button
          @click="closeLightbox"
          :title="t('common.close') + ' (Esc)'"
          class="p-2 rounded-xl bg-surface/80 border border-line-strong text-fg-3 hover:text-danger hover:bg-surface-2 transition shadow-lg ml-2"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- Main Zoom Viewport -->
    <div
      class="flex-1 relative overflow-hidden flex items-center justify-center"
      @mousedown="handleMouseDown"
      @mousemove="handleMouseMove"
      @dblclick="handleDoubleClick"
      @click.self="zoom === 1.0 && closeLightbox()"
      :class="[
        zoom > 1.0 ? (isDragging ? 'cursor-grabbing' : 'cursor-grab') : 'cursor-zoom-in'
      ]"
    >
      <img
        :src="lightboxImage.src"
        :alt="lightboxImage.alt"
        referrerpolicy="no-referrer"
        draggable="false"
        class="max-w-[92vw] max-h-[85vh] object-contain rounded-xl shadow-2xl transition-transform duration-75"
        :style="{
          transform: `translate(${panX}px, ${panY}px) scale(${zoom})`,
          transformOrigin: 'center center',
        }"
      />

      <!-- Floating Interaction Hint -->
      <div
        v-if="zoom === 1.0"
        class="absolute bottom-6 px-3 py-1.5 rounded-full bg-black/60 border border-white/10 text-[11px] text-white/50 backdrop-blur-md pointer-events-none animate-fade-in"
      >
        {{ t('lightbox.instructions') }}
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
