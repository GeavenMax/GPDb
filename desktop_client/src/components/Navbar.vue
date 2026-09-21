<script setup lang="ts">
import { ref, watch } from 'vue';
import { Search, SlidersHorizontal, RefreshCw, X, LayoutGrid, List } from 'lucide-vue-next';

const props = defineProps<{
  modelValue: string;
  movieCount: number;
  viewMode: 'grid' | 'list';
  filterActive: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void;
  (e: 'toggle-filter'): void;
  (e: 'toggle-sync'): void;
  (e: 'change-view', mode: 'grid' | 'list'): void;
}>();

const searchQuery = ref(props.modelValue);

watch(() => props.modelValue, (val) => {
  searchQuery.value = val;
});

let debounceTimer: any = null;
function onInput(e: Event) {
  const val = (e.target as HTMLInputElement).value;
  searchQuery.value = val;
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    emit('update:modelValue', val);
  }, 200);
}

function clearSearch() {
  searchQuery.value = '';
  emit('update:modelValue', '');
}
</script>

<template>
  <header class="chrome-bar h-16 border-b border-line sticky top-0 z-30 px-6 flex items-center justify-between gap-4">
    <!-- Brand -->
    <div class="flex items-center gap-3 select-none">
      <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-accent-fill via-accent-2 to-accent-deep flex items-center justify-center shadow-lg shadow-accent-fill/20 font-black text-on-fill tracking-tighter text-lg">
        G
      </div>
      <div>
        <div class="font-bold text-fg text-base tracking-wide">
          GPDb
        </div>
        <div class="text-[11px] text-fg-3">已收录 {{ movieCount.toLocaleString() }} 部影片</div>
      </div>
    </div>

    <!-- Central Search Bar -->
    <div class="flex-1 max-w-xl relative">
      <Search class="w-4 h-4 text-fg-3 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
      <input
        type="text"
        :value="searchQuery"
        @input="onInput"
        placeholder="搜索电影、演员、片商、导演 (输入即搜)..."
        class="w-full bg-surface/90 border border-line-strong/60 hover:border-line-strong focus:border-accent-fill/70 rounded-xl pl-10 pr-20 py-2 text-sm text-fg placeholder-fg-4 focus:outline-none focus:ring-2 focus:ring-accent-fill/20 transition-all shadow-inner"
      />
      <div class="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1.5">
        <button
          v-if="searchQuery"
          @click="clearSearch"
          class="text-fg-3 hover:text-fg-2 p-0.5 rounded-md hover:bg-surface-2 transition"
        >
          <X class="w-3.5 h-3.5" />
        </button>
        <span class="text-[10px] bg-surface-2 text-fg-3 border border-line-strong px-1.5 py-0.5 rounded font-mono select-none">⌘K</span>
      </div>
    </div>

    <!-- Actions & View Switch -->
    <div class="flex items-center gap-2">
      <!-- View mode toggle -->
      <div class="flex bg-surface border border-line rounded-lg p-0.5">
        <button
          @click="emit('change-view', 'grid')"
          :class="[
            'p-1.5 rounded-md transition',
            viewMode === 'grid' ? 'bg-surface-2 text-accent shadow' : 'text-fg-3 hover:text-fg-2'
          ]"
          title="海报网格视图"
        >
          <LayoutGrid class="w-4 h-4" />
        </button>
        <button
          @click="emit('change-view', 'list')"
          :class="[
            'p-1.5 rounded-md transition',
            viewMode === 'list' ? 'bg-surface-2 text-accent shadow' : 'text-fg-3 hover:text-fg-2'
          ]"
          title="列表视图"
        >
          <List class="w-4 h-4" />
        </button>
      </div>

      <!-- Filter Drawer Button -->
      <button
        @click="emit('toggle-filter')"
        :class="[
          'flex items-center gap-2 px-3 py-1.5 rounded-xl text-xs font-medium border transition',
          filterActive
            ? 'bg-accent-fill/10 border-accent-fill/40 text-accent'
            : 'bg-surface hover:bg-surface-2 border-line text-fg-2'
        ]"
      >
        <SlidersHorizontal class="w-3.5 h-3.5" />
        <span>筛选</span>
        <span v-if="filterActive" class="w-1.5 h-1.5 rounded-full bg-accent animate-pulse"></span>
      </button>

      <!-- Sync Button -->
      <button
        @click="emit('toggle-sync')"
        class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium bg-surface hover:bg-surface-2 border border-line text-fg-2 hover:text-fg transition"
        title="增量同步官方最新影片"
      >
        <RefreshCw class="w-3.5 h-3.5 text-fg-3" />
        <span>同步</span>
      </button>
    </div>
  </header>
</template>
