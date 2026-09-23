<script setup lang="ts">
import { computed } from 'vue';
import type { IconSchemeId } from '../utils/appIcon';
import schemeA from '../assets/icons/scheme-a.svg';
import schemeB from '../assets/icons/scheme-b.svg';
import schemeC from '../assets/icons/scheme-c.svg';
import schemeD from '../assets/icons/scheme-d.svg';

const props = withDefaults(
  defineProps<{
    scheme?: IconSchemeId;
    size?: number | string;
    alt?: string;
  }>(),
  {
    scheme: 'scheme-a',
    size: 40,
    alt: 'GPDb App Icon'
  }
);

const iconSrc = computed(() => {
  switch (props.scheme) {
    case 'scheme-b':
      return schemeB;
    case 'scheme-c':
      return schemeC;
    case 'scheme-d':
      return schemeD;
    case 'scheme-a':
    default:
      return schemeA;
  }
});

const sizeStyle = computed(() => {
  const s = typeof props.size === 'number' ? `${props.size}px` : props.size;
  return {
    width: s,
    height: s,
    minWidth: s,
    minHeight: s
  };
});
</script>

<template>
  <img
    :src="iconSrc"
    :alt="alt"
    :style="sizeStyle"
    class="select-none object-contain pointer-events-none transition-transform duration-200"
    loading="lazy"
  />
</template>
