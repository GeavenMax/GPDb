<script setup lang="ts">
/**
 * One active filter, rendered on the page it narrows.
 *
 * The chips exist so an active filter is visible without reopening the drawer — before
 * this, a director filter set from a film's detail page lit the navbar dot and changed
 * the results, but nothing on screen said why the grid was short. Every chip carries
 * its own ×: a chip you can see but not remove is just a mystery.
 *
 * Two variants, because the tabs use filters differently. 影片 shows the accent outline
 * (a filter is the exception there, and it should read as one), while 演员 and 分集
 * stack several at once and use the neutral fill so five chips do not shout.
 */
withDefaults(
  defineProps<{
    /** Optional category prefix — 分类, 发色, 厂牌… Omitted when the value explains itself. */
    label?: string;
    value: string | number;
    variant?: 'accent' | 'neutral';
  }>(),
  { label: '', variant: 'accent' }
);

defineEmits<{ remove: [] }>();
</script>

<template>
  <span
    :class="[
      'inline-flex items-center gap-1 whitespace-nowrap',
      variant === 'accent'
        ? 'text-xs px-2.5 py-1 rounded-lg bg-accent-fill/10 text-accent border border-accent-fill/30'
        : 'text-[11px] px-2 py-0.5 rounded-lg bg-surface-2 text-fg-2 border border-line-strong',
    ]"
  >
    <span v-if="label" :class="variant === 'neutral' ? 'text-fg-4' : ''">{{ label }}</span>
    <span>{{ value }}</span>
    <button
      type="button"
      @click="$emit('remove')"
      class="hover:text-fg transition"
      :aria-label="`清除${label || ''}筛选：${value}`"
      :title="`清除${label || ''}筛选`"
    >
      ×
    </button>
  </span>
</template>
