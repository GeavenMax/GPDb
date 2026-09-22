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
    <!-- UNLOCKED: FLUID GLASS -->
    <template v-if="isUnlocked">
      <!-- Glow Underlay -->
      <div
        class="absolute inset-0 rounded-inherit opacity-60 blur-md bg-gradient-to-tr"
        :class="[tierTheme.gradient, tierTheme.glow]"
      ></div>

      <!-- Ultra-thin Glass Body -->
      <div
        class="absolute inset-0 rounded-inherit border backdrop-blur-xl overflow-hidden bg-white/20 dark:bg-black/30 shadow-lg"
        :class="tierTheme.border"
      >
        <!-- Liquid Gradient Backdrop -->
        <div
          class="absolute inset-0 bg-gradient-to-br opacity-80"
          :class="tierTheme.gradient"
        ></div>

        <!-- Specular Fluid Highlight Arc (Curved top reflection) -->
        <svg
          class="absolute top-0 left-0 w-full h-2/3 pointer-events-none opacity-75"
          viewBox="0 0 100 60"
          preserveAspectRatio="none"
        >
          <path
            d="M 0 0 L 100 0 L 100 15 C 60 45 40 45 0 25 Z"
            fill="url(#fluidSpecular)"
          />
          <defs>
            <linearGradient id="fluidSpecular" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#ffffff" stop-opacity="0.9" />
              <stop offset="100%" stop-color="#ffffff" stop-opacity="0.0" />
            </linearGradient>
          </defs>
        </svg>

        <!-- Subtle Inner Sheen -->
        <div class="absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-white/30 to-transparent"></div>
      </div>

      <!-- Semantic Icon Content (Glowing in center) -->
      <component
        :is="ResolvedIcon"
        class="relative z-10 drop-shadow-[0_2px_4px_rgba(0,0,0,0.4)]"
        :class="[dimensions.icon, tierTheme.text]"
      />
    </template>

    <!-- LOCKED: OBSIDIAN FROSTED GLASS WITH CRYSTAL LOCK -->
    <template v-else>
      <!-- Obsidian Frosted Glass Layer -->
      <div
        class="absolute inset-0 rounded-inherit border border-white/10 bg-zinc-950/70 backdrop-blur-md shadow-inner overflow-hidden flex items-center justify-center"
      >
        <!-- Faint Silhouette of underlying semantic symbol -->
        <component
          :is="ResolvedIcon"
          class="absolute opacity-15 text-zinc-400 blur-[0.5px]"
          :class="dimensions.icon"
        />

        <!-- Obsidian Specular Highlight -->
        <div class="absolute top-0 inset-x-0 h-1/2 bg-gradient-to-b from-white/10 to-transparent opacity-40"></div>

        <!-- Crystal Lock Clasp in Center -->
        <div
          class="relative z-10 rounded-full p-1.5 bg-zinc-900/90 border border-white/20 shadow-md flex items-center justify-center text-zinc-400 group-hover:text-zinc-200 transition"
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
