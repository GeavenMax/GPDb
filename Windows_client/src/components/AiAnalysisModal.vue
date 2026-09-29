<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue';
import {
  Sparkles, Loader2, CheckCircle2, AlertCircle, X, Clock, Brain, Cpu
} from '@lucide/vue';
import {
  isAiAnalyzing,
  aiAnalysisStage,
  aiAnalysisStepText,
  aiAnalysisError,
} from '../services/aiAnalysis';

const props = defineProps<{
  show: boolean;
  profileName?: string;
  modelName?: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const elapsedSeconds = ref(0);
let timer: ReturnType<typeof setInterval> | null = null;

watch(
  () => props.show,
  (val) => {
    if (val) {
      elapsedSeconds.value = 0;
      if (timer) clearInterval(timer);
      timer = setInterval(() => {
        elapsedSeconds.value++;
      }, 1000);
    } else {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    }
  },
  { immediate: true }
);

onUnmounted(() => {
  if (timer) clearInterval(timer);
});

const stages = [
  { id: 'collecting', label: '1. 梳理本地媒体库收藏、标记与观影足迹', desc: '萃取喜爱影片、演员、厂牌、导演及年代跨度' },
  { id: 'prompting', label: '2. 构建艺术鉴赏提示词与大模型加密握手', desc: '封装文化学者与资深影视顾问的多维研判上下文' },
  { id: 'analyzing', label: '3. 大模型深度解构审美性格与时代光谱', desc: '分析叙事风格、面孔化学反应与制作风格偏好' },
  { id: 'finalizing', label: '4. 提炼专属原型代号与定制寻宝指南', desc: '生成流派原型、专属标签及冷门宝藏探索方向' },
];

function isStageDone(stageId: string): boolean {
  const order = ['collecting', 'prompting', 'analyzing', 'finalizing', 'success'];
  const currentIdx = order.indexOf(aiAnalysisStage.value);
  const stageIdx = order.indexOf(stageId);
  return currentIdx > stageIdx;
}

function isStageActive(stageId: string): boolean {
  return aiAnalysisStage.value === stageId;
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="show"
        class="fixed inset-0 z-[120] flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-fade-in select-none"
      >
        <div
          class="relative w-full max-w-lg rounded-3xl bg-surface border border-line shadow-2xl overflow-hidden p-6 md:p-8 space-y-6"
        >
          <!-- Top glowing light ambient -->
          <div class="absolute -top-24 left-1/2 -translate-x-1/2 w-80 h-48 bg-gradient-to-b from-indigo-500/25 to-purple-500/0 blur-3xl pointer-events-none"></div>

          <!-- Header -->
          <div class="flex items-start justify-between gap-4 relative z-10">
            <div class="flex items-center gap-3.5">
              <div class="relative p-3 rounded-2xl bg-gradient-to-tr from-indigo-600 to-purple-600 text-white shadow-lg shadow-indigo-500/25">
                <Sparkles v-if="!isAiAnalyzing && aiAnalysisStage === 'success'" class="w-6 h-6 animate-bounce" />
                <Brain v-else class="w-6 h-6 animate-pulse" />
              </div>
              <div>
                <h3 class="text-lg md:text-xl font-black text-fg tracking-tight flex items-center gap-2">
                  <span>{{ aiAnalysisStage === 'error' ? '偏好画像研判中断' : (aiAnalysisStage === 'success' ? '偏好画像研判完成！' : '正在研判影迷专属偏好画像') }}</span>
                </h3>
                <p class="text-xs text-fg-4 mt-0.5">
                  基于您在本地影库的真实足迹，大模型正在撰写深度艺术鉴赏报告
                </p>
              </div>
            </div>

            <!-- Close button only enabled on error or completed -->
            <button
              v-if="aiAnalysisStage === 'error' || aiAnalysisStage === 'success'"
              @click="emit('close')"
              class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg transition cursor-pointer"
            >
              <X class="w-4 h-4" />
            </button>
          </div>

          <!-- Active Model Chip -->
          <div v-if="profileName || modelName" class="flex items-center justify-between text-xs px-3.5 py-2 rounded-xl bg-surface-2/70 border border-line text-fg-3">
            <div class="flex items-center gap-2">
              <Cpu class="w-3.5 h-3.5 text-indigo-400" />
              <span>当前服务商：<strong class="text-fg">{{ profileName }}</strong></span>
              <span v-if="modelName" class="text-fg-4 font-mono font-bold text-[11px]">({{ modelName }})</span>
            </div>
            <div class="flex items-center gap-1 font-mono text-[11px] text-fg-3">
              <Clock class="w-3.5 h-3.5 text-accent" />
              <span>已耗时 {{ elapsedSeconds }}s</span>
            </div>
          </div>

          <!-- Current dynamic sub-step indicator -->
          <div v-if="aiAnalysisStepText && isAiAnalyzing" class="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-indigo-500/10 border border-indigo-500/20 text-xs text-indigo-300 font-medium">
            <Loader2 class="w-3.5 h-3.5 animate-spin text-indigo-400 shrink-0" />
            <span>{{ aiAnalysisStepText }}</span>
          </div>

          <!-- Steps Checklist -->
          <div class="space-y-3 relative z-10">
            <div
              v-for="st in stages"
              :key="st.id"
              :class="[
                'p-3.5 rounded-2xl border transition-all flex items-start gap-3',
                isStageDone(st.id)
                  ? 'bg-emerald-500/5 border-emerald-500/20 text-emerald-300'
                  : isStageActive(st.id)
                    ? 'bg-indigo-500/10 border-indigo-500/30 text-indigo-200 shadow-sm'
                    : 'bg-surface-2/30 border-line/40 text-fg-4 opacity-60'
              ]"
            >
              <!-- Icon -->
              <div class="shrink-0 mt-0.5">
                <CheckCircle2 v-if="isStageDone(st.id)" class="w-4 h-4 text-emerald-400" />
                <Loader2 v-else-if="isStageActive(st.id)" class="w-4 h-4 text-indigo-400 animate-spin" />
                <div v-else class="w-4 h-4 rounded-full border border-fg-5/60"></div>
              </div>

              <!-- Content -->
              <div class="flex-1 space-y-0.5">
                <div
                  class="text-xs font-bold leading-tight"
                  :class="isStageActive(st.id) ? 'text-indigo-200' : isStageDone(st.id) ? 'text-fg-2' : 'text-fg-4'"
                >
                  {{ st.label }}
                </div>
                <div class="text-[11px] text-fg-4 leading-normal">
                  {{ st.desc }}
                </div>
              </div>
            </div>
          </div>

          <!-- Error Alert Banner (if failed) -->
          <div
            v-if="aiAnalysisStage === 'error' && aiAnalysisError"
            class="p-4 rounded-2xl bg-danger-fill/15 border border-danger-fill/30 text-danger-soft space-y-2 text-xs animate-shake"
          >
            <div class="flex items-center gap-2 font-bold">
              <AlertCircle class="w-4 h-4 shrink-0 text-red-400" />
              <span>大模型研判未成功：</span>
            </div>
            <p class="text-[11px] leading-relaxed break-words opacity-90 pl-6">
              {{ aiAnalysisError }}
            </p>
            <div class="pt-2 flex justify-end">
              <button
                @click="emit('close')"
                class="px-3.5 py-1.5 rounded-xl bg-danger-fill text-white font-bold text-xs hover:opacity-90 transition cursor-pointer"
              >
                我知道了
              </button>
            </div>
          </div>

          <!-- Normal Reassurance Tip Footer -->
          <div v-else class="p-3.5 rounded-2xl bg-indigo-950/20 border border-indigo-500/20 text-xs text-indigo-200/90 leading-relaxed flex items-start gap-2.5">
            <span class="text-sm">💡</span>
            <div>
              <p class="font-medium">
                深度偏好画像为宏篇长文（约 1500~2500 字），通常需要 <strong>5~15 秒</strong> 生成。客户端正在后台全速运作，并非卡顿，请安心等待。
              </p>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
