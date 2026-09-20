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
  <header class="h-16 border-b border-zinc-800 bg-zinc-950/80 backdrop-blur-md sticky top-0 z-30 px-6 flex items-center justify-between gap-4">
    <!-- Brand -->
    <div class="flex items-center gap-3 select-none">
      <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-amber-500 via-yellow-400 to-amber-600 flex items-center justify-center shadow-lg shadow-amber-500/20 font-black text-black tracking-tighter text-lg">
        G
      </div>
      <div>
        <div class="font-bold text-white text-base tracking-wide flex items-center gap-2">
          GEVI <span class="text-xs px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-400 border border-amber-500/20 font-medium">Desktop</span>
        </div>
        <div class="text-[11px] text-zinc-400">已收录 {{ movieCount.toLocaleString() }} 部影片</div>
      </div>
    </div>

    <!-- Central Search Bar -->
    <div class="flex-1 max-w-xl relative">
      <Search class="w-4 h-4 text-zinc-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
      <input
        type="text"
        :value="searchQuery"
        @input="onInput"
        placeholder="搜索电影、演员、片商、导演 (输入即搜)..."
        class="w-full bg-zinc-900/90 border border-zinc-700/60 hover:border-zinc-600 focus:border-amber-500/70 rounded-xl pl-10 pr-20 py-2 text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-amber-500/20 transition-all shadow-inner"
      />
      <div class="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1.5">
        <button
          v-if="searchQuery"
          @click="clearSearch"
          class="text-zinc-400 hover:text-zinc-200 p-0.5 rounded-md hover:bg-zinc-800 transition"
        >
          <X class="w-3.5 h-3.5" />
        </button>
        <span class="text-[10px] bg-zinc-800 text-zinc-400 border border-zinc-700 px-1.5 py-0.5 rounded font-mono select-none">⌘K</span>
      </div>
    </div>

    <!-- Actions & View Switch -->
    <div class="flex items-center gap-2">
      <!-- View mode toggle -->
      <div class="flex bg-zinc-900 border border-zinc-800 rounded-lg p-0.5">
        <button
          @click="emit('change-view', 'grid')"
          :class="[
            'p-1.5 rounded-md transition',
            viewMode === 'grid' ? 'bg-zinc-800 text-amber-400 shadow' : 'text-zinc-400 hover:text-zinc-200'
          ]"
          title="海报网格视图"
        >
          <LayoutGrid class="w-4 h-4" />
        </button>
        <button
          @click="emit('change-view', 'list')"
          :class="[
            'p-1.5 rounded-md transition',
            viewMode === 'list' ? 'bg-zinc-800 text-amber-400 shadow' : 'text-zinc-400 hover:text-zinc-200'
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
            ? 'bg-amber-500/10 border-amber-500/40 text-amber-400'
            : 'bg-zinc-900 hover:bg-zinc-800 border-zinc-800 text-zinc-300'
        ]"
      >
        <SlidersHorizontal class="w-3.5 h-3.5" />
        <span>筛选</span>
        <span v-if="filterActive" class="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"></span>
      </button>

      <!-- Sync Button -->
      <button
        @click="emit('toggle-sync')"
        class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-zinc-300 hover:text-white transition"
        title="增量同步官方最新影片"
      >
        <RefreshCw class="w-3.5 h-3.5 text-zinc-400" />
        <span>同步</span>
      </button>
    </div>
  </header>
</template>
