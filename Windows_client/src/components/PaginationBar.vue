<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ChevronLeft, ChevronRight } from '@lucide/vue';

const props = defineProps<{
  page: number;
  pageSize: number;
  total: number;
  loading?: boolean;
  /** Page sizes offered in the selector. */
  pageSizeOptions?: number[];
}>();

const emit = defineEmits<{
  (e: 'update:page', page: number): void;
  (e: 'update:pageSize', size: number): void;
}>();

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));

// 96 is the ceiling on purpose: the performer endpoint (server.py) and both
// Tauri commands clamp pageSize to 100, so offering more here would make the
// page count disagree with the backend and strand the tail of the list.
const sizes = computed(() => props.pageSizeOptions ?? [24, 48, 96]);

/** A windowed page list: 1 … 4 5 [6] 7 8 … 120 — null marks an ellipsis. */
const pageItems = computed<Array<number | null>>(() => {
  const last = totalPages.value;
  const current = props.page;
  if (last <= 7) return Array.from({ length: last }, (_, i) => i + 1);

  const items: Array<number | null> = [1];
  const from = Math.max(2, current - 1);
  const to = Math.min(last - 1, current + 1);

  if (from > 2) items.push(null);
  for (let i = from; i <= to; i++) items.push(i);
  if (to < last - 1) items.push(null);
  items.push(last);
  return items;
});

function go(target: number) {
  const next = Math.min(Math.max(1, target), totalPages.value);
  if (next !== props.page && !props.loading) emit('update:page', next);
}

// Jump-to-page box: local text so the user can type freely, committed on Enter/blur.
const jumpText = ref(String(props.page));
watch(() => props.page, (p) => { jumpText.value = String(p); });

function commitJump() {
  const n = Number.parseInt(jumpText.value, 10);
  if (Number.isFinite(n)) go(n);
  else jumpText.value = String(props.page);
}

function changePageSize(e: Event) {
  const size = Number((e.target as HTMLSelectElement).value);
  if (size > 0) emit('update:pageSize', size);
}
</script>

<template>
  <div class="flex items-center justify-center flex-wrap gap-3 py-6 text-xs">
    <!-- Page size -->
    <div class="flex items-center gap-2 bg-surface border border-line rounded-xl px-2.5 py-1.5">
      <span class="text-fg-4">每页</span>
      <select
        :value="pageSize"
        @change="changePageSize"
        class="bg-sunken border border-line-strong rounded-lg px-1.5 py-0.5 text-fg-2 outline-none focus:border-accent-fill transition cursor-pointer"
      >
        <option v-for="s in sizes" :key="s" :value="s">{{ s }}</option>
      </select>
      <span class="text-fg-4">条</span>
    </div>

    <!-- Prev / numbers / next -->
    <div class="flex items-center gap-1">
      <button
        @click="go(page - 1)"
        :disabled="page <= 1 || loading"
        class="w-8 h-8 rounded-lg bg-surface border border-line hover:bg-surface-2 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 transition"
        title="上一页"
      >
        <ChevronLeft class="w-4 h-4" />
      </button>

      <template v-for="(item, i) in pageItems" :key="i">
        <span v-if="item === null" class="px-1 text-fg-5 select-none">…</span>
        <button
          v-else
          @click="go(item)"
          :disabled="loading"
          :class="[
            'min-w-8 h-8 px-2 rounded-lg border font-mono font-medium transition disabled:pointer-events-none',
            item === page
              ? 'bg-accent-fill text-on-fill border-accent-fill font-bold'
              : 'bg-surface border-line text-fg-3 hover:text-fg hover:bg-surface-2'
          ]"
        >
          {{ item }}
        </button>
      </template>

      <button
        @click="go(page + 1)"
        :disabled="page >= totalPages || loading"
        class="w-8 h-8 rounded-lg bg-surface border border-line hover:bg-surface-2 disabled:opacity-30 disabled:pointer-events-none flex items-center justify-center text-fg-2 transition"
        title="下一页"
      >
        <ChevronRight class="w-4 h-4" />
      </button>
    </div>

    <!-- Jump -->
    <div class="flex items-center gap-2 text-fg-4">
      <span>第</span>
      <input
        v-model="jumpText"
        @keydown.enter="commitJump"
        @blur="commitJump"
        inputmode="numeric"
        class="w-14 bg-surface border border-line rounded-lg px-2 py-1 text-center text-fg-2 font-mono outline-none focus:border-accent-fill transition"
      />
      <span>/ {{ totalPages.toLocaleString() }} 页 · 共 {{ total.toLocaleString() }} 条</span>
    </div>
  </div>
</template>
