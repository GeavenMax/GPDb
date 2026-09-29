<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Film } from '@lucide/vue';
import { getImageUrl } from '../utils/image';

const props = withDefaults(
  defineProps<{
    covers?: (string | null | undefined)[] | null;
    singleCover?: string | null;
    title?: string | null;
    aspectRatio?: string;
  }>(),
  {
    covers: () => [],
    singleCover: null,
    title: '',
    aspectRatio: 'aspect-[2/3]',
  }
);

const failedUrls = ref<Set<string>>(new Set());

// Reset failed urls when covers change
watch(
  () => props.covers,
  () => {
    failedUrls.value.clear();
  },
  { deep: true }
);

function onImgError(url: string) {
  failedUrls.value.add(url);
}

const activeCovers = computed<string[]>(() => {
  const list: string[] = [];
  if (Array.isArray(props.covers)) {
    for (const c of props.covers) {
      if (c && typeof c === 'string' && c.trim() && !failedUrls.value.has(c)) {
        list.push(c.trim());
      }
    }
  }
  if (list.length === 0 && props.singleCover && !failedUrls.value.has(props.singleCover)) {
    list.push(props.singleCover.trim());
  }
  return list.slice(0, 4);
});
</script>

<template>
  <div
    :class="[
      aspectRatio,
      'w-full overflow-hidden bg-surface-2 relative border border-line/40 select-none group/collage'
    ]"
  >
    <!-- 0 Covers: Fallback Icon -->
    <div
      v-if="activeCovers.length === 0"
      class="w-full h-full flex flex-col items-center justify-center text-fg-4 bg-gradient-to-br from-surface-2 to-surface-3"
    >
      <Film class="w-8 h-8 stroke-1 text-fg-5" />
    </div>

    <!-- 1 Cover: Full Single Poster -->
    <div v-else-if="activeCovers.length === 1" class="w-full h-full">
      <img
        :src="getImageUrl(activeCovers[0])"
        :alt="title || 'Series cover'"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="onImgError(activeCovers[0])"
      />
    </div>

    <!-- 2 Covers: 2 Vertical Splits Side-by-Side (50% / 50%) -->
    <div v-else-if="activeCovers.length === 2" class="w-full h-full grid grid-cols-2 gap-0.5 bg-black/40">
      <div v-for="(cov, idx) in activeCovers" :key="idx" class="w-full h-full overflow-hidden relative">
        <img
          :src="getImageUrl(cov)"
          :alt="title || `Cover ${idx + 1}`"
          class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          loading="lazy"
          referrerpolicy="no-referrer"
          @error="onImgError(cov)"
        />
      </div>
    </div>

    <!-- 3 Covers: 1 Large Left (50%) + 2 Stacked Right (50%) -->
    <div v-else-if="activeCovers.length === 3" class="w-full h-full grid grid-cols-2 gap-0.5 bg-black/40">
      <!-- Left Large -->
      <div class="w-full h-full overflow-hidden relative">
        <img
          :src="getImageUrl(activeCovers[0])"
          :alt="title || 'Cover 1'"
          class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          loading="lazy"
          referrerpolicy="no-referrer"
          @error="onImgError(activeCovers[0])"
        />
      </div>
      <!-- Right Stack of 2 -->
      <div class="w-full h-full grid grid-rows-2 gap-0.5 overflow-hidden">
        <div class="w-full h-full overflow-hidden relative">
          <img
            :src="getImageUrl(activeCovers[1])"
            :alt="title || 'Cover 2'"
            class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            loading="lazy"
            referrerpolicy="no-referrer"
            @error="onImgError(activeCovers[1])"
          />
        </div>
        <div class="w-full h-full overflow-hidden relative">
          <img
            :src="getImageUrl(activeCovers[2])"
            :alt="title || 'Cover 3'"
            class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            loading="lazy"
            referrerpolicy="no-referrer"
            @error="onImgError(activeCovers[2])"
          />
        </div>
      </div>
    </div>

    <!-- 4+ Covers: 2x2 Grid (4 Quarters) -->
    <div v-else class="w-full h-full grid grid-cols-2 grid-rows-2 gap-0.5 bg-black/40">
      <div v-for="(cov, idx) in activeCovers.slice(0, 4)" :key="idx" class="w-full h-full overflow-hidden relative">
        <img
          :src="getImageUrl(cov)"
          :alt="title || `Cover ${idx + 1}`"
          class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          loading="lazy"
          referrerpolicy="no-referrer"
          @error="onImgError(cov)"
        />
      </div>
    </div>

    <!-- Soft Overlay Vignette to unite the collage -->
    <div class="absolute inset-0 ring-1 ring-inset ring-white/10 pointer-events-none rounded-[inherit]"></div>
  </div>
</template>
