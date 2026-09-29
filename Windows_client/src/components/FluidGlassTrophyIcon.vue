<script setup lang="ts">
import { computed } from 'vue';
import type { Trophy } from '../services/trophySystem';
import {
  Lock, Crown, Film, Calendar, Users, Heart, Clock, Star,
  Compass, Flame, Layers, Tv, Award, Megaphone, Video,
  Building2, Landmark, Search, Radar, Bookmark, Gem,
  CheckCircle, Hourglass, CheckCheck, PlayCircle, Folder,
  User, Smile, Clapperboard, Grid, BookOpen, Building,
  Zap, FolderHeart, Package, CheckCircle2, Sliders, Moon,
  Ghost, Sparkle, Tag, Tags, Languages, Globe, Trophy as TrophyIcon,
  Watch, Sun, Globe2, Filter, History, Timer, LayoutGrid,
  Shield, Disc
} from '@lucide/vue';

const props = withDefaults(defineProps<{
  trophy: Trophy;
  size?: 'sm' | 'md' | 'lg';
  isUnlocked?: boolean;
}>(), {
  size: 'md',
  isUnlocked: false,
});

const ICON_MAP: Record<string, any> = {
  Crown, Film, Calendar, Users, Heart, Clock, Star,
  Compass, Flame, Layers, Tv, Award, Megaphone, Video,
  Building2, Landmark, Search, Radar, Bookmark, Gem,
  CheckCircle, Hourglass, CheckCheck, PlayCircle, Folder,
  User, Smile, Clapperboard, Grid, BookOpen, Building,
  Zap, FolderHeart, Package, CheckCircle2, Sliders, Moon,
  Ghost, Sparkle, Tag, Tags, Languages, Globe, Trophy: TrophyIcon,
  Watch, Sun, Globe2, Filter, History, Timer, LayoutGrid,
  Shield, Masks: Smile, Disc
};

const ResolvedIcon = computed(() => ICON_MAP[props.trophy.icon] || TrophyIcon);

const dimensions = computed(() => {
  switch (props.size) {
    case 'sm':
      return { box: 'w-10 h-10 rounded-xl', icon: 'w-4 h-4', lock: 'w-2.5 h-2.5' };
    case 'lg':
      return { box: 'w-20 h-20 rounded-3xl', icon: 'w-9 h-9', lock: 'w-5 h-5' };
    case 'md':
    default:
      return { box: 'w-14 h-14 rounded-2xl', icon: 'w-6 h-6', lock: 'w-3.5 h-3.5' };
  }
});

const tierTheme = computed(() => {
  switch (props.trophy.tier) {
    case 'platinum':
      return {
        gradient: 'from-cyan-400 via-sky-300 to-indigo-400',
        glow: 'shadow-cyan-400/30',
        border: 'border-cyan-200/60',
        highlight: 'rgba(255, 255, 255, 0.95)',
        text: 'text-cyan-100',
      };
    case 'gold':
      return {
        gradient: 'from-amber-400 via-yellow-300 to-amber-500',
        glow: 'shadow-amber-400/30',
        border: 'border-yellow-200/60',
        highlight: 'rgba(255, 250, 220, 0.95)',
        text: 'text-amber-100',
      };
    case 'silver':
      return {
        gradient: 'from-slate-200 via-zinc-100 to-slate-400',
        glow: 'shadow-slate-300/25',
        border: 'border-white/70',
        highlight: 'rgba(255, 255, 255, 0.90)',
        text: 'text-slate-100',
      };
    case 'bronze':
    default:
      return {
        gradient: 'from-orange-500 via-amber-600 to-amber-700',
        glow: 'shadow-orange-500/25',
        border: 'border-orange-300/50',
        highlight: 'rgba(255, 240, 225, 0.90)',
        text: 'text-orange-100',
      };
  }
});
</script>

<template>
  <div
    class="relative select-none flex items-center justify-center shrink-0 transition-transform hover:scale-105 duration-200"
    :class="dimensions.box"
  >
    <!-- UNLOCKED: FLAT DESIGN -->
    <template v-if="isUnlocked">
      <div
        class="absolute inset-0 rounded-inherit bg-gradient-to-br shadow-sm"
        :class="tierTheme.gradient"
      ></div>
      
      <!-- Semantic Icon Content -->
      <component
        :is="ResolvedIcon"
        class="relative z-10"
        :class="[dimensions.icon, tierTheme.text]"
      />
    </template>

    <!-- LOCKED: FLAT DARK DESIGN -->
    <template v-else>
      <div
        class="absolute inset-0 rounded-inherit bg-zinc-800/80 shadow-inner flex items-center justify-center"
      >
        <div
          class="relative z-10 rounded-full p-1.5 bg-zinc-900 border border-zinc-700 flex items-center justify-center text-zinc-500"
        >
          <Lock :class="dimensions.lock" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.rounded-inherit {
  border-radius: inherit;
}
</style>
