<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue';
import { Search, SlidersHorizontal, RefreshCw, X, LayoutGrid, List, Clock, Trash2 } from '@lucide/vue';
import { privacySettings } from '../services/privacy';
import { analytics, recordSearch, removeSearchHistoryItem, clearSearchHistory } from '../services/analytics';
import { t } from '../i18n';

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
const isHistoryOpen = ref(false);
const searchContainerRef = ref<HTMLElement | null>(null);

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
    if (val.trim()) {
      recordSearch(val.trim());
    }
  }, 250);
}

function onFocus() {
  if (privacySettings.value.keepSearchHistory && analytics.value.searchHistory.length > 0) {
    isHistoryOpen.value = true;
  }
}

function selectHistory(item: string) {
  searchQuery.value = item;
  emit('update:modelValue', item);
  recordSearch(item);
  isHistoryOpen.value = false;
}

function clearSearch() {
  searchQuery.value = '';
  emit('update:modelValue', '');
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    isHistoryOpen.value = false;
  } else if (e.key === 'Enter') {
    if (searchQuery.value.trim()) {
      recordSearch(searchQuery.value.trim());
    }
    isHistoryOpen.value = false;
  }
}

function handleClickOutside(e: MouseEvent) {
  if (searchContainerRef.value && !searchContainerRef.value.contains(e.target as Node)) {
    isHistoryOpen.value = false;
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});
</script>

<template>
  <header class="chrome-bar h-16 border-b border-line sticky top-0 z-30 px-6 flex items-center justify-between gap-4">
    <!-- Brand -->
    <div class="flex items-center gap-3 select-none">
      <!-- Same light as the app icon: lit from the top-left, deepening to the bottom-right.
           The gradient used to run to-tr, which put the darkest tone top-right — the exact
           opposite of the icon, so the two G's read as unrelated marks. -->
      <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-accent-2 via-accent-fill to-accent-deep flex items-center justify-center shadow-lg shadow-accent-fill/20 font-black text-on-fill tracking-tighter text-lg">
        G
      </div>
      <div>
        <div class="font-bold text-fg text-base tracking-wide">
          GPDb
        </div>
        <div class="text-[11px] text-fg-3">已收录 {{ movieCount.toLocaleString() }} 部影片</div>
      </div>
    </div>

    <!-- Central Search Bar with History Dropdown -->
    <div ref="searchContainerRef" class="flex-1 max-w-xl relative">
      <Search class="w-4 h-4 text-fg-3 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
      <input
        type="text"
        :value="searchQuery"
        @input="onInput"
        @focus="onFocus"
        @keydown="handleKeyDown"
        :placeholder="t('common.search', '搜索电影、演员、片商、导演 (输入即搜)...')"
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

      <!-- Search History Dropdown Popover -->
      <div
        v-if="isHistoryOpen && privacySettings.keepSearchHistory && analytics.searchHistory.length > 0"
        class="absolute left-0 right-0 top-full mt-2 bg-surface border border-line rounded-2xl shadow-2xl overflow-hidden z-50 backdrop-blur-xl p-2 animate-fade-in"
      >
        <div class="flex items-center justify-between px-2.5 py-1.5 text-[11px] font-semibold text-fg-4 border-b border-line/60">
          <span class="flex items-center gap-1.5">
            <Clock class="w-3.5 h-3.5 text-accent" />
            最近搜索历史
          </span>
          <button
            @click="clearSearchHistory"
            class="text-fg-4 hover:text-danger text-[10px] flex items-center gap-1 hover:underline transition"
          >
            <Trash2 class="w-3 h-3" />
            全部清空
          </button>
        </div>

        <div class="max-h-60 overflow-y-auto py-1 space-y-0.5">
          <div
            v-for="item in analytics.searchHistory"
            :key="item"
            @click="selectHistory(item)"
            class="group flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-surface-2 cursor-pointer transition text-xs text-fg-2 hover:text-fg"
          >
            <span class="truncate flex-1">{{ item }}</span>
            <button
              @click.stop="removeSearchHistoryItem(item)"
              class="opacity-0 group-hover:opacity-100 text-fg-4 hover:text-danger p-0.5 rounded transition"
              title="删除此条记录"
            >
              <X class="w-3 h-3" />
            </button>
          </div>
        </div>
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
        <span>{{ t('common.sync', '同步') }}</span>
      </button>
    </div>
  </header>
</template>
