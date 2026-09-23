<script setup lang="ts">
import { ref } from 'vue';
import {
  AlertTriangle, Trash2, X, Sparkles,
  RotateCcw, ShieldAlert, CheckCircle2
} from '@lucide/vue';
import {
  resetAndRecheckTrophiesSequentially,
  resetAllDataAndTrophiesCompletely,
  trophyStats
} from '../services/trophySystem';

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'reset-complete', mode: 1 | 2, count?: number): void;
}>();

const isProcessing = ref(false);
const completedMessage = ref('');

function handleMode1() {
  isProcessing.value = true;
  const unlockedCount = resetAndRecheckTrophiesSequentially();
  completedMessage.value = `已重置并重新触发 ${unlockedCount} 座奖杯的连续队列解锁！`;
  setTimeout(() => {
    isProcessing.value = false;
    emit('reset-complete', 1, unlockedCount);
    emit('close');
  }, 800);
}

function handleMode2() {
  isProcessing.value = true;
  resetAllDataAndTrophiesCompletely();
  completedMessage.value = '已彻底抹除所有本地统计记录与成就奖杯，系统已恢复为全新未开启状态。';
  setTimeout(() => {
    isProcessing.value = false;
    emit('reset-complete', 2);
    emit('close');
  }, 800);
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-scrim/80 backdrop-blur-sm animate-fade-in">
    <div
      class="relative w-full max-w-xl chrome-panel border border-line-strong rounded-3xl p-6 md:p-8 shadow-2xl text-fg space-y-6"
    >
      <!-- Close Button -->
      <button
        @click="emit('close')"
        :disabled="isProcessing"
        class="absolute top-5 right-5 w-8 h-8 rounded-full bg-surface-2 hover:bg-surface-3 border border-line flex items-center justify-center text-fg-3 hover:text-fg transition cursor-pointer"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Header -->
      <div class="flex items-start gap-4">
        <div class="p-3.5 rounded-2xl bg-amber-500/15 text-amber-400 border border-amber-500/30 shrink-0">
          <AlertTriangle class="w-7 h-7" />
        </div>
        <div>
          <h2 class="text-xl font-extrabold text-fg tracking-tight">重置成就奖杯记录</h2>
          <p class="text-xs text-fg-4 mt-1 leading-relaxed">
            确认操作将清空当前已解锁的全部 <span class="text-amber-400 font-bold font-mono">{{ trophyStats.unlocked }}</span> 座奖杯。请在下方选择您希望采用的重置解锁方式：
          </p>
        </div>
      </div>

      <!-- Mode 1 & Mode 2 Options Cards -->
      <div class="space-y-3.5">
        <!-- Mode 1 Card -->
        <div
          class="p-5 rounded-2xl border border-purple-500/30 bg-purple-500/5 hover:bg-purple-500/10 transition space-y-3"
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2.5">
              <div class="p-1.5 rounded-xl bg-purple-500/20 text-purple-300">
                <Sparkles class="w-4 h-4" />
              </div>
              <span class="text-sm font-bold text-fg">方式 1：保留数据并连环逐个解锁</span>
            </div>
            <span class="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-300 font-bold border border-purple-500/30">
              强烈推荐 · 视听盛宴
            </span>
          </div>

          <p class="text-xs text-fg-3 leading-relaxed">
            <strong>不删除</strong>现有的浏览记录、评星打分与收藏数据。系统将根据现有数据重新比对成就条件，将所有符合条件的奖杯加入<strong>连环弹窗动画队列</strong>：在上一个解锁横幅消失后再弹出下一个，享受连续解锁带来的极致爽快感！
          </p>

          <div class="flex justify-end pt-1">
            <button
              @click="handleMode1"
              :disabled="isProcessing"
              class="px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs flex items-center gap-2 transition shadow-lg shadow-purple-600/30 cursor-pointer disabled:opacity-50"
            >
              <RotateCcw class="w-3.5 h-3.5" />
              <span>保留数据并连环解锁</span>
            </button>
          </div>
        </div>

        <!-- Mode 2 Card -->
        <div
          class="p-5 rounded-2xl border border-danger-fill/30 bg-danger-fill/5 hover:bg-danger-fill/10 transition space-y-3"
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2.5">
              <div class="p-1.5 rounded-xl bg-danger-fill/20 text-danger">
                <ShieldAlert class="w-4 h-4" />
              </div>
              <span class="text-sm font-bold text-fg">方式 2：彻底抹除数据从零开始</span>
            </div>
            <span class="text-[10px] px-2 py-0.5 rounded-full bg-danger-fill/20 text-danger font-bold border border-danger-fill/30">
              彻底归零
            </span>
          </div>

          <p class="text-xs text-fg-3 leading-relaxed">
            彻底清空所有本地使用统计、打卡天数、浏览足迹以及全部奖杯解锁历史，将整个系统恢复为全新未探索状态，从 0 开始重新探索并解锁。
          </p>

          <div class="flex justify-end pt-1">
            <button
              @click="handleMode2"
              :disabled="isProcessing"
              class="px-4 py-2 rounded-xl bg-danger-fill/20 hover:bg-danger-fill/30 text-danger border border-danger-fill/40 font-bold text-xs flex items-center gap-2 transition cursor-pointer disabled:opacity-50"
            >
              <Trash2 class="w-3.5 h-3.5" />
              <span>清空所有数据从 0 起步</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Feedback / Action footer -->
      <div v-if="completedMessage" class="p-3.5 rounded-2xl bg-success-fill/10 border border-success-fill/30 text-xs text-success flex items-center gap-2">
        <CheckCircle2 class="w-4 h-4 shrink-0" />
        <span>{{ completedMessage }}</span>
      </div>

      <div class="flex justify-end pt-1">
        <button
          @click="emit('close')"
          :disabled="isProcessing"
          class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-xs font-semibold text-fg-3 hover:text-fg border border-line transition cursor-pointer"
        >
          取消
        </button>
      </div>
    </div>
  </div>
</template>
