<script setup lang="ts">
import { currentUnlockedToast } from '../services/trophySystem';
import FluidGlassTrophyIcon from './FluidGlassTrophyIcon.vue';
import { X, Sparkles } from '@lucide/vue';

function dismiss() {
  currentUnlockedToast.value = null;
}

function tierLabel(tier: string): string {
  switch (tier) {
    case 'platinum': return '白金奖杯';
    case 'gold': return '金奖杯';
    case 'silver': return '银奖杯';
    case 'bronze': default: return '铜奖杯';
  }
}

function tierBadgeClass(tier: string): string {
  switch (tier) {
    case 'platinum': return 'bg-cyan-500/20 text-cyan-300 border-cyan-400/40';
    case 'gold': return 'bg-amber-500/20 text-amber-300 border-amber-400/40';
    case 'silver': return 'bg-slate-300/20 text-slate-200 border-slate-300/40';
    case 'bronze': default: return 'bg-orange-500/20 text-orange-300 border-orange-400/40';
  }
}
</script>

<template>
  <transition
    enter-active-class="transform transition duration-300 ease-out"
    enter-from-class="-translate-y-12 opacity-0 scale-95"
    enter-to-class="translate-y-0 opacity-100 scale-100"
    leave-active-class="transform transition duration-200 ease-in"
    leave-from-class="translate-y-0 opacity-100 scale-100"
    leave-to-class="-translate-y-10 opacity-0 scale-95"
  >
    <div
      v-if="currentUnlockedToast"
      class="fixed top-5 inset-x-0 mx-auto z-[100] max-w-md w-11/12 pointer-events-auto"
    >
      <div
        class="relative flex items-center gap-3.5 p-3.5 rounded-2xl bg-zinc-950/85 backdrop-blur-2xl border border-white/20 shadow-2xl text-fg overflow-hidden"
      >
        <!-- Background shimmer beam -->
        <div class="absolute -inset-1 bg-gradient-to-r from-transparent via-white/10 to-transparent opacity-50 blur-sm pointer-events-none animate-pulse"></div>

        <!-- Trophy Icon (Unlocked, fluid glass) -->
        <FluidGlassTrophyIcon
          :trophy="currentUnlockedToast"
          :is-unlocked="true"
          size="md"
        />

        <!-- Text content -->
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 mb-0.5">
            <span class="text-[10px] font-bold uppercase tracking-wider text-fg-3 flex items-center gap-1">
              <Sparkles class="w-3 h-3 text-accent" /> 奖杯已解锁
            </span>
            <span
              class="text-[10px] font-extrabold px-1.5 py-0.2 rounded border"
              :class="tierBadgeClass(currentUnlockedToast.tier)"
            >
              {{ tierLabel(currentUnlockedToast.tier) }}
            </span>
          </div>

          <h4 class="text-sm font-bold text-fg truncate">
            {{ currentUnlockedToast.title }}
          </h4>
          <p class="text-xs text-fg-4 truncate">
            {{ currentUnlockedToast.desc }}
          </p>
        </div>

        <!-- Close button -->
        <button
          @click="dismiss"
          class="w-6 h-6 rounded-lg bg-white/5 hover:bg-white/10 text-fg-4 hover:text-fg flex items-center justify-center transition shrink-0"
        >
          <X class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  </transition>
</template>
