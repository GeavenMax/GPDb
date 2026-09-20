<script setup lang="ts">
import { Film, Users, Heart, HardDrive } from 'lucide-vue-next';

defineProps<{
  currentTab: 'movies' | 'performers' | 'favorites' | 'settings';
}>();

const emit = defineEmits<{
  (e: 'change-tab', tab: 'movies' | 'performers' | 'favorites' | 'settings'): void;
}>();

const navItems = [
  { id: 'movies' as const, label: '影片库', icon: Film },
  { id: 'performers' as const, label: '演员库', icon: Users },
  { id: 'favorites' as const, label: '我的收藏', icon: Heart },
  { id: 'settings' as const, label: '缓存与设置', icon: HardDrive },
];
</script>

<template>
  <aside class="w-56 border-r border-zinc-800 bg-zinc-950 p-4 flex flex-col justify-between select-none">
    <div class="space-y-6">
      <div class="px-2 text-[11px] font-semibold text-zinc-500 uppercase tracking-wider">
        资源检索
      </div>
      <nav class="space-y-1">
        <button
          v-for="item in navItems"
          :key="item.id"
          @click="emit('change-tab', item.id)"
          :class="[
            'w-full flex items-center gap-3 px-3 py-2 rounded-xl text-xs font-medium transition',
            currentTab === item.id
              ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20 font-semibold'
              : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/80 border border-transparent'
          ]"
        >
          <component :is="item.icon" class="w-4 h-4" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </div>

    <!-- Bottom info -->
    <div class="p-3 rounded-xl bg-zinc-900/60 border border-zinc-800/80 text-[11px] text-zinc-400 space-y-1">
      <div class="font-medium text-zinc-300">本地离线模式</div>
      <div class="text-[10px] text-zinc-500">SQLite FTS5 引擎驱动</div>
    </div>
  </aside>
</template>
