<script setup lang="ts">
import { computed, ref } from 'vue';
import { Home, Film, Users, Heart, HardDrive, Building2, Clapperboard, Megaphone, BarChart2, Puzzle } from '@lucide/vue';
import type { AppTab } from '../types';
import { PREFS } from '../utils/prefs';
import { t } from '../i18n';

defineProps<{ currentTab: AppTab }>();

const emit = defineEmits<{
  (e: 'change-tab', tab: AppTab): void;
}>();

/**
 * The reorderable「资源检索」section. 缓存与设置 is deliberately not in here — it is
 * a configuration entry rather than a search entry, so it stays pinned at the
 * bottom instead of competing for a slot in the order.
 */
const NAV_ITEMS = [
  { id: 'movies', labelKey: 'nav.movies', label: '影片库', icon: Film },
  { id: 'performers', labelKey: 'nav.performers', label: '演员库', icon: Users },
  { id: 'studios', labelKey: 'nav.studios', label: '片商库', icon: Building2 },
  { id: 'directors', labelKey: 'nav.directors', label: '导演库', icon: Megaphone },
  { id: 'episodes', labelKey: 'nav.episodes', label: '分集库', icon: Clapperboard },
  { id: 'favorites', labelKey: 'nav.favorites', label: '我的收藏', icon: Heart },
] as const;

type NavId = (typeof NAV_ITEMS)[number]['id'];

const ORDER_KEY = PREFS.navOrder;

/**
 * The order is a UI preference, so it lives in localStorage alongside the grid
 * density and page size.
 *
 * Ids this build no longer knows are dropped and unknown ones are appended, so a
 * stored order can never hide a nav entry that was added afterwards — the failure
 * mode being guarded against is a user who reordered the sidebar before an upgrade
 * and then could not find the new tab anywhere.
 */
function loadOrder(): NavId[] {
  const known = NAV_ITEMS.map(i => i.id) as NavId[];
  try {
    const raw = JSON.parse(localStorage.getItem(ORDER_KEY) || '[]');
    if (!Array.isArray(raw)) return known;
    const kept = raw.filter((id): id is NavId => known.includes(id));
    const missing = known.filter(id => !kept.includes(id));
    if (missing.length === 0) return kept;

    const result = [...kept];
    for (const id of missing) {
      const idxInKnown = known.indexOf(id);
      // Find the first item after `id` in `known` that is present in `result`
      const nextKnown = known.slice(idxInKnown + 1).find(k => result.includes(k));
      if (nextKnown) {
        const insertIdx = result.indexOf(nextKnown);
        result.splice(insertIdx, 0, id);
      } else {
        result.push(id);
      }
    }
    return result;
  } catch {
    return known;
  }
}

const order = ref<NavId[]>(loadOrder());
const navItems = computed(() => order.value.flatMap(id => NAV_ITEMS.filter(i => i.id === id)));

// HTML5 drag and drop, no dependency. The rows are divs rather than buttons
// because WebKit refuses to start a drag on a form control, which would have made
// reordering silently do nothing in the desktop build.
const draggingId = ref<NavId | null>(null);
const dropTargetId = ref<NavId | null>(null);
/** Whether the dragged row would land below the hovered one rather than above it. */
const dropBelow = ref(false);

function onDragStart(id: NavId, e: DragEvent) {
  draggingId.value = id;
  dropTargetId.value = null;
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', id);
  }
}

function onDragOver(id: NavId, e: DragEvent) {
  if (!draggingId.value || draggingId.value === id) return;
  e.preventDefault(); // without this the drop never fires
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move';
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  dropBelow.value = e.clientY > rect.top + rect.height / 2;
  dropTargetId.value = id;
}

function onDrop(e: DragEvent) {
  e.preventDefault();
  const from = draggingId.value;
  const over = dropTargetId.value;
  if (from && over && from !== over) {
    const next = order.value.filter(id => id !== from);
    next.splice(next.indexOf(over) + (dropBelow.value ? 1 : 0), 0, from);
    order.value = next;
    localStorage.setItem(ORDER_KEY, JSON.stringify(next));
  }
  onDragEnd();
}

function onDragEnd() {
  draggingId.value = null;
  dropTargetId.value = null;
  dropBelow.value = false;
}

/** Classes shared by the reorderable rows and the pinned bottom entries. */
function tabClass(id: AppTab, active: boolean) {
  const isBottom = id === 'settings' || id === 'analytics' || id === 'plugins' || id === 'trophies';
  return [
    'w-full flex items-center gap-3 px-3 py-2 rounded-xl text-xs font-medium transition border',
    active
      ? 'bg-accent-fill/10 text-accent border-accent-fill/20 font-semibold'
      : 'text-fg-3 hover:text-fg-2 hover:bg-surface/80 border-transparent',
    isBottom ? 'cursor-pointer' : 'cursor-grab active:cursor-grabbing',
  ];
}
</script>

<template>
  <aside class="chrome-side w-56 border-r border-line p-4 flex flex-col justify-between select-none">
    <div class="space-y-4">
      <div class="px-2 text-[11px] font-semibold text-fg-4 uppercase tracking-wider">
        {{ t('nav.sectionSearch', '资源检索') }}
      </div>
      <div class="space-y-1">
        <button
          @click="emit('change-tab', 'home')"
          :class="tabClass('home', currentTab === 'home')"
          class="w-full text-left cursor-pointer"
          :title="t('nav.home', '主页')"
        >
          <Home class="w-4 h-4 shrink-0" />
          <span>{{ t('nav.home', '主页') }}</span>
        </button>
      </div>
      <nav class="space-y-1">
        <div
          v-for="item in navItems"
          :key="item.id"
          draggable="true"
          role="button"
          tabindex="0"
          :aria-current="currentTab === item.id ? 'page' : undefined"
          :title="`${t(item.labelKey, item.label)}（拖动可调整顺序）`"
          @click="emit('change-tab', item.id)"
          @keydown.enter.prevent="emit('change-tab', item.id)"
          @keydown.space.prevent="emit('change-tab', item.id)"
          @dragstart="onDragStart(item.id, $event)"
          @dragover="onDragOver(item.id, $event)"
          @drop="onDrop($event)"
          @dragend="onDragEnd"
          :class="[
            'relative',
            tabClass(item.id, currentTab === item.id),
            draggingId === item.id ? 'opacity-40' : '',
          ]"
        >
          <component :is="item.icon" class="w-4 h-4 shrink-0" />
          <span>{{ t(item.labelKey, item.label) }}</span>
          <!-- Where the row would land: a bar above or below the hovered entry. -->
          <span
            v-if="dropTargetId === item.id"
            :class="[
              'absolute left-1 right-1 h-0.5 rounded-full bg-accent',
              dropBelow ? '-bottom-0.5' : '-top-0.5',
            ]"
          ></span>
        </div>
      </nav>
      <div class="px-2 text-[10px] text-fg-5">{{ t('nav.reorderTip', '拖动条目可调整顺序') }}</div>
    </div>

    <!-- Pinned: analytics, plugins, settings, then offline notice -->
    <div class="space-y-1.5 border-t border-line/60 pt-3">
      <button
        @click="emit('change-tab', 'analytics')"
        :class="tabClass('analytics', currentTab === 'analytics')"
        :title="t('nav.analytics')"
      >
        <BarChart2 class="w-4 h-4 shrink-0" />
        <span>{{ t('nav.analytics') }}</span>
      </button>

      <button
        @click="emit('change-tab', 'plugins')"
        :class="tabClass('plugins', currentTab === 'plugins')"
        :title="t('nav.plugins')"
      >
        <Puzzle class="w-4 h-4 shrink-0" />
        <span>{{ t('nav.plugins') }}</span>
      </button>

      <button
        @click="emit('change-tab', 'settings')"
        :class="tabClass('settings', currentTab === 'settings')"
        :title="t('nav.settings')"
      >
        <HardDrive class="w-4 h-4 shrink-0" />
        <span>{{ t('nav.settings') }}</span>
      </button>

      <div class="p-3 rounded-xl bg-surface/60 border border-line/80 text-[11px] text-fg-3 space-y-1 mt-2">
        <div class="font-medium text-fg-2">{{ t('nav.offlineMode') }}</div>
        <div class="text-[10px] text-fg-4">{{ t('nav.engine') }}</div>
      </div>
    </div>
  </aside>
</template>
