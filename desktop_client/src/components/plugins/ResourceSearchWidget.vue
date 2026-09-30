<script setup lang="ts">
import { ExternalLink } from '@lucide/vue';
import { pluginsConfig, openBtMovieSearch, openBtSearch, openBftvMovie, openBftvPerformer, openPbcPerformer, openGoogleSearch, openUrlExternal } from '../../services/pluginManager';

const props = defineProps<{
  type: 'movie' | 'performer' | 'studio' | 'episode';
  title: string;
  bftvUrl?: string | null;
  pbcUrl?: string | null;
  sjUrl?: string | null;
  /** Used by EpisodeRow to render an icon-only button */
  iconOnly?: boolean;
}>();
</script>

<template>
  <template v-if="pluginsConfig.resourceSearchEnabled && title">
    <!-- Icon-only mode for list rows -->
    <template v-if="iconOnly">
      <button
        v-if="type === 'movie' || type === 'episode'"
        @click.stop="openBtMovieSearch(title)"
        :title="`在 BT 站检索「${title}」`"
        class="text-fg-5 hover:text-accent p-0.5 rounded transition"
      >
        <ExternalLink class="w-3.5 h-3.5" />
      </button>
    </template>
    
    <!-- Full button group mode for detail modals -->
    <template v-else>
      <div class="flex items-center gap-1.5 flex-wrap">
        <!-- BT Search -->
        <button
          v-if="type === 'movie' || type === 'episode'"
          @click="openBtMovieSearch(title)"
          class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition cursor-pointer"
          :title="`在 BT 站检索「${title}」资源`"
        >
          <ExternalLink class="w-3.5 h-3.5" />
          <span>BT 搜索</span>
        </button>
        <button
          v-else
          @click="openBtSearch(title)"
          class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition cursor-pointer"
          :title="`在 BT 站检索「${title}」`"
        >
          <ExternalLink class="w-3.5 h-3.5" />
          <span>BT 搜索</span>
        </button>

        <!-- BFTV Movie (Movies only) -->
        <button
          v-if="(type === 'movie' || type === 'episode') && pluginsConfig.webJumpConfig.bftvMovieEnabled"
          @click="openBftvMovie(title)"
          class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition cursor-pointer"
          :title="`在 BFTV 检索「${title}」影片资料`"
        >
          <ExternalLink class="w-3.5 h-3.5 text-amber-400" />
          <span>在BFTV搜索影片资料</span>
        </button>

        <!-- BFTV Performer (Performers only) -->
        <button
          v-if="type === 'performer' && bftvUrl"
          @click="openBftvPerformer(title, bftvUrl)"
          class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 hover:text-amber-300 border-amber-500/30 flex items-center gap-1.5 transition cursor-pointer"
          :title="`直接打开「${title}」的 BFTV 主页`"
        >
          <ExternalLink class="w-3.5 h-3.5" />
          <span>BFTV #{{ bftvUrl.match(/(\d+)\/$/)?.at(1) ?? '' }}</span>
        </button>

        <!-- PBC Performer Wiki (Performers only) -->
        <button
          v-if="type === 'performer' && pbcUrl"
          @click="openPbcPerformer(pbcUrl)"
          class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-purple-500/30 bg-purple-500/10 hover:bg-purple-500/20 text-purple-400 hover:text-purple-300 flex items-center gap-1.5 transition cursor-pointer"
          :title="`直接打开「${title}」的 PBC 维基百科档案`"
        >
          <ExternalLink class="w-3.5 h-3.5" />
          <span>PBC 百科</span>
        </button>

        <!-- SmutJunkies Profile (Performers only) -->
        <button
          v-if="type === 'performer' && sjUrl"
          @click="openUrlExternal(sjUrl)"
          class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-lime-500/30 bg-lime-500/10 hover:bg-lime-500/20 text-lime-400 hover:text-lime-300 flex items-center gap-1.5 transition cursor-pointer"
          :title="`直接打开「${title}」的 SmutJunkies 档案主页`"
        >
          <ExternalLink class="w-3.5 h-3.5" />
          <span>SmutJunkies</span>
        </button>

        <!-- Google Search -->
        <button
          v-if="pluginsConfig.webJumpConfig.googleSearchEnabled"
          @click="openGoogleSearch(title)"
          class="py-1.5 px-2.5 rounded-xl text-xs font-medium border border-line bg-surface-2/60 hover:bg-surface-3 text-fg-3 hover:text-accent flex items-center gap-1.5 transition cursor-pointer"
          :title="`在 Google 检索「${title}」`"
        >
          <ExternalLink class="w-3.5 h-3.5 text-blue-400" />
          <span>Google 搜索</span>
        </button>
      </div>
    </template>
  </template>
</template>
