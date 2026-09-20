<script setup lang="ts">
import { ref } from 'vue';
import { X, RefreshCw, CheckCircle2, Film, Users } from '@lucide/vue';
import type { DatabaseStats } from '../types';
import { api } from '../api';

const props = defineProps<{
  open: boolean;
  stats: DatabaseStats | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'sync-complete'): void;
}>();

const isSyncing = ref(false);
const syncResult = ref<{ newMovies: number; newPerformers: number } | null>(null);

async function startSync() {
  isSyncing.value = true;
  syncResult.value = null;
  try {
    const res = await api.runSync();
    syncResult.value = res;
    emit('sync-complete');
  } catch (err) {
    console.error(err);
  } finally {
    isSyncing.value = false;
  }
}
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fade-in"
    @click.self="emit('close')"
  >
    <div class="relative w-full max-w-md bg-zinc-900 border border-zinc-700/80 rounded-3xl shadow-2xl p-6 text-zinc-100 space-y-6">
      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-4 right-4 text-zinc-400 hover:text-white p-1 rounded-lg hover:bg-zinc-800 transition"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Header -->
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400">
          <RefreshCw :class="['w-5 h-5', isSyncing ? 'animate-spin' : '']" />
        </div>
        <div>
          <h2 class="text-base font-bold text-white">增量同步中心</h2>
          <p class="text-xs text-zinc-400">检查官方最新上映影片与新收录演员</p>
        </div>
      </div>

      <!-- Current Database Overview -->
      <div v-if="stats" class="grid grid-cols-2 gap-3 p-4 rounded-2xl bg-zinc-950/80 border border-zinc-800 text-xs">
        <div class="space-y-1">
          <div class="text-zinc-500 flex items-center gap-1.5">
            <Film class="w-3.5 h-3.5 text-zinc-400" />
            <span>本地已收录电影</span>
          </div>
          <div class="text-lg font-bold text-white">{{ stats.movies.toLocaleString() }}</div>
        </div>
        <div class="space-y-1">
          <div class="text-zinc-500 flex items-center gap-1.5">
            <Users class="w-3.5 h-3.5 text-zinc-400" />
            <span>本地演员总数</span>
          </div>
          <div class="text-lg font-bold text-white">{{ stats.performers.toLocaleString() }}</div>
        </div>
      </div>

      <!-- Result Banner -->
      <div
        v-if="syncResult"
        class="p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 flex items-center gap-3 text-emerald-300 text-xs"
      >
        <CheckCircle2 class="w-5 h-5 shrink-0 text-emerald-400" />
        <div>
          <div class="font-bold">同步完成！</div>
          <div>本次成功整合入库 {{ syncResult.newMovies }} 部新片，{{ syncResult.newPerformers }} 位新演员。</div>
        </div>
      </div>

      <!-- Action Button -->
      <button
        @click="startSync"
        :disabled="isSyncing"
        class="w-full py-3 rounded-2xl bg-gradient-to-r from-amber-500 to-yellow-500 hover:from-amber-400 hover:to-yellow-400 text-black font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-amber-500/20 disabled:opacity-50 transition"
      >
        <RefreshCw :class="['w-4 h-4', isSyncing ? 'animate-spin' : '']" />
        <span>{{ isSyncing ? '正在连接官网检查并拉取...' : '立即检查并增量同步' }}</span>
      </button>
    </div>
  </div>
</template>
