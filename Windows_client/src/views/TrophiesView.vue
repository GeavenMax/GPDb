<script setup lang="ts">
import { ref, computed } from 'vue';
import { TROPHIES, trophyStats, unlockedMap, trophiesVersion, type TrophyTier } from '../services/trophySystem';
import { pluginsConfig, savePluginsConfig } from '../services/pluginManager';
import FluidGlassTrophyIcon from '../components/FluidGlassTrophyIcon.vue';
import { analytics } from '../services/analytics';
import { t } from '../i18n';
import {
  Trophy as TrophyIcon, Sparkles, ChevronLeft,
  Lock, CheckCircle2, Volume2, VolumeX, RotateCcw
} from '@lucide/vue';

const emit = defineEmits<{
  (e: 'back'): void;
}>();

const selectedTier = ref<'all' | TrophyTier | 'unlocked' | 'locked'>('all');

const filteredTrophies = computed(() => {
  void trophiesVersion.value;
  return TROPHIES.map(t => ({
    ...t,
    unlockedAt: unlockedMap.value[t.id] || null,
  })).filter(t => {
    const isUnlocked = Boolean(t.unlockedAt);
    if (selectedTier.value === 'all') return true;
    if (selectedTier.value === 'unlocked') return isUnlocked;
    if (selectedTier.value === 'locked') return !isUnlocked;
    return t.tier === selectedTier.value;
  });
});

import TrophyResetModal from '../components/TrophyResetModal.vue';

const showResetModal = ref(false);

function toggleSound() {
  savePluginsConfig({ trophiesSoundEnabled: !pluginsConfig.value.trophiesSoundEnabled });
}

function confirmReset() {
  showResetModal.value = true;
}

function formatDate(ts: number | null): string {
  if (!ts) return '';
  const d = new Date(ts);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function tierName(tier: TrophyTier): string {
  switch (tier) {
    case 'platinum': return '白金';
    case 'gold': return '金';
    case 'silver': return '银';
    case 'bronze': default: return '铜';
  }
}

function tierBadgeClass(tier: TrophyTier): string {
  switch (tier) {
    case 'platinum': return 'bg-cyan-500/15 text-cyan-300 border-cyan-400/30';
    case 'gold': return 'bg-amber-500/15 text-amber-300 border-amber-400/30';
    case 'silver': return 'bg-slate-300/15 text-slate-200 border-slate-300/30';
    case 'bronze': default: return 'bg-orange-500/15 text-orange-300 border-orange-400/30';
  }
}
</script>

<template>
  <div class="space-y-8 max-w-5xl mx-auto pb-16 animate-fade-in text-fg">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-line pb-6 flex-wrap gap-4">
      <div class="flex items-center gap-3">
        <button
          @click="emit('back')"
          class="w-8 h-8 rounded-xl bg-surface-2 hover:bg-surface-3 border border-line flex items-center justify-center text-fg-3 hover:text-fg transition"
          title="返回插件中心"
        >
          <ChevronLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-2xl md:text-3xl font-extrabold text-fg tracking-tight flex items-center gap-2.5">
            <TrophyIcon class="w-7 h-7 text-accent" />
            <span>{{ t('plugins.trophies') }}</span>
          </h1>
          <p class="text-xs text-fg-4 mt-0.5">
            {{ t('plugins.trophiesDesc') }}
          </p>
        </div>
      </div>

      <!-- Actions & Tiers breakdown chips -->
      <div class="flex items-center gap-3 flex-wrap">
        <!-- Sound switch -->
        <button
          @click="toggleSound"
          class="px-3 py-1.5 rounded-xl text-xs font-semibold border transition flex items-center gap-1.5 shadow-xs"
          :class="pluginsConfig.trophiesSoundEnabled ? 'bg-surface-2 text-fg border-line hover:bg-surface-3' : 'bg-surface-2/40 text-fg-5 border-line/60'"
          :title="pluginsConfig.trophiesSoundEnabled ? '解锁音效已开启' : '解锁音效已静音'"
        >
          <component :is="pluginsConfig.trophiesSoundEnabled ? Volume2 : VolumeX" class="w-3.5 h-3.5 text-accent" />
          <span>{{ pluginsConfig.trophiesSoundEnabled ? '解锁音效: 开' : '解锁音效: 关' }}</span>
        </button>

        <!-- Reset trophies button -->
        <button
          @click="confirmReset"
          class="px-3 py-1.5 rounded-xl text-xs font-semibold border border-danger-fill/30 bg-danger-fill/10 text-danger-soft hover:bg-danger-fill/20 transition flex items-center gap-1.5 shadow-xs"
          title="清空当前已解锁奖杯记录，从头开始"
        >
          <RotateCcw class="w-3.5 h-3.5" />
          <span>{{ t('plugins.resetTrophies') }}</span>
        </button>

        <!-- Tier badges -->
        <div class="flex items-center gap-1.5">
          <span class="px-2 py-1 rounded-xl text-xs font-bold bg-cyan-500/10 text-cyan-300 border border-cyan-500/30 flex items-center gap-1">
            <span>白金:</span> <span>{{ trophyStats.platinum }} / 1</span>
          </span>
          <span class="px-2 py-1 rounded-xl text-xs font-bold bg-amber-500/10 text-amber-300 border border-amber-500/30 flex items-center gap-1">
            <span>金:</span> <span>{{ trophyStats.gold }} / 6</span>
          </span>
          <span class="px-2 py-1 rounded-xl text-xs font-bold bg-slate-300/10 text-slate-200 border border-slate-300/30 flex items-center gap-1">
            <span>银:</span> <span>{{ trophyStats.silver }} / 20</span>
          </span>
          <span class="px-2 py-1 rounded-xl text-xs font-bold bg-orange-500/10 text-orange-300 border border-orange-500/30 flex items-center gap-1">
            <span>铜:</span> <span>{{ trophyStats.bronze }} / 50</span>
          </span>
        </div>
      </div>
    </div>

    <!-- Progress Card -->
    <div class="p-6 rounded-3xl bg-surface/70 border border-line space-y-4 shadow-sm">
      <div class="flex items-center justify-between text-xs">
        <span class="font-bold text-fg-2 flex items-center gap-2">
          <Sparkles class="w-4 h-4 text-accent" />
          全收集进度完成度
        </span>
        <span class="font-mono font-extrabold text-accent text-sm">
          {{ trophyStats.unlocked }} / {{ trophyStats.total }} ({{ trophyStats.percentage }}%)
        </span>
      </div>
      <div class="w-full h-3 rounded-full bg-sunken overflow-hidden p-0.5 border border-line">
        <div
          class="h-full rounded-full bg-gradient-to-r from-accent to-accent-soft transition-all duration-500"
          :style="{ width: `${trophyStats.percentage}%` }"
        ></div>
      </div>
    </div>

    <!-- Filter Tabs -->
    <div class="flex items-center gap-2 flex-wrap">
      <button
        v-for="opt in [
          { id: 'all', label: '全部 (77)' },
          { id: 'platinum', label: '白金 (1)' },
          { id: 'gold', label: '金 (6)' },
          { id: 'silver', label: '银 (20)' },
          { id: 'bronze', label: '铜 (50)' },
          { id: 'unlocked', label: `已解锁 (${trophyStats.unlocked})` },
          { id: 'locked', label: `未解锁 (${trophyStats.total - trophyStats.unlocked})` },
        ]"
        :key="opt.id"
        @click="selectedTier = (opt.id as any)"
        :class="[
          'px-3.5 py-1.5 rounded-xl text-xs font-semibold border transition',
          selectedTier === opt.id
            ? 'bg-accent-fill text-on-fill border-accent shadow'
            : 'bg-surface-2/70 text-fg-3 border-line-strong hover:text-fg-2 hover:bg-surface-2'
        ]"
      >
        {{ opt.label }}
      </button>
    </div>

    <!-- 77 Trophies Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div
        v-for="t in filteredTrophies"
        :key="t.id"
        class="p-4 rounded-2xl border transition-all flex items-start gap-4"
        :class="[
          t.unlockedAt
            ? 'bg-surface/90 border-line hover:border-accent/40 shadow-sm'
            : 'bg-surface/40 border-line/60 opacity-80 hover:opacity-100'
        ]"
      >
        <!-- Fluid Glass Icon -->
        <FluidGlassTrophyIcon
          :trophy="t"
          :is-unlocked="Boolean(t.unlockedAt)"
          size="md"
        />

        <!-- Info -->
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 mb-1">
            <h3 class="text-sm font-bold text-fg truncate">
              {{ t.title }}
            </h3>
            <span
              class="text-[10px] font-extrabold px-1.5 py-0.2 rounded border uppercase shrink-0"
              :class="tierBadgeClass(t.tier)"
            >
              {{ tierName(t.tier) }}
            </span>
          </div>

          <p class="text-xs text-fg-3 leading-relaxed mb-2.5">
            {{ t.desc }}
          </p>

          <!-- Status / Progress -->
          <div class="flex items-center justify-between text-[11px] pt-1 border-t border-line/50">
            <template v-if="t.unlockedAt">
              <span class="text-success flex items-center gap-1 font-medium">
                <CheckCircle2 class="w-3.5 h-3.5" /> 已解锁
              </span>
              <span class="text-fg-5 font-mono text-[10px]">
                {{ formatDate(t.unlockedAt) }}
              </span>
            </template>
            <template v-else>
              <span class="text-fg-5 flex items-center gap-1">
                <Lock class="w-3 h-3" /> 未达成
              </span>
              <span class="text-fg-4 font-mono text-[11px]">
                进度: {{ t.progress(analytics).current }} / {{ t.progress(analytics).max }}
              </span>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Trophy Reset Confirmation Modal with Dual Modes -->
    <TrophyResetModal
      v-if="showResetModal"
      @close="showResetModal = false"
    />
  </div>
</template>
