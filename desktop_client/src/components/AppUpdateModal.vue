<script setup lang="ts">
import { ref, computed } from 'vue';
import { ArrowDownCircle, Sparkles, X, AlertCircle } from 'lucide-vue-next';
import { type AppReleaseInfo, CURRENT_VERSION, downloadAndInstallUpdate } from '../services/appUpdater';
import { t } from '../i18n';

const props = defineProps<{
  release: AppReleaseInfo;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const isDownloading = ref(false);
const downloadProgress = ref(0);
const errorMessage = ref<string | null>(null);

const formattedSize = computed(() => {
  if (props.release.assetSize > 0) {
    return `${(props.release.assetSize / (1024 * 1024)).toFixed(1)} MB`;
  }
  return t('update.unknownSize');
});

async function startUpdate() {
  isDownloading.value = true;
  downloadProgress.value = 0;
  errorMessage.value = null;

  try {
    await downloadAndInstallUpdate(props.release, (percent) => {
      downloadProgress.value = percent;
    });
  } catch (err: any) {
    errorMessage.value = err?.message || t('update.failed');
    isDownloading.value = false;
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in no-privacy-blur">
    <div class="relative w-full max-w-md overflow-hidden rounded-3xl bg-surface border border-white/10 shadow-2xl shadow-black/50 p-6 text-fg">
      <!-- Background Ambient Glow -->
      <div class="absolute -top-20 -right-20 w-44 h-44 bg-amber-500/20 rounded-full blur-3xl pointer-events-none"></div>
      <div class="absolute -bottom-20 -left-20 w-44 h-44 bg-primary/20 rounded-full blur-3xl pointer-events-none"></div>

      <!-- Close Button -->
      <button
        v-if="!isDownloading"
        @click="emit('close')"
        class="absolute top-4 right-4 p-2 rounded-xl text-fg-3 hover:text-fg hover:bg-white/10 transition cursor-pointer"
        :title="t('update.later')"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Header Icon & Title -->
      <div class="flex items-center gap-3.5 mb-4">
        <div class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-amber-500/20 to-orange-500/30 border border-amber-500/30 flex items-center justify-center text-amber-400 shrink-0 shadow-inner">
          <Sparkles class="w-6 h-6" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h3 class="text-base font-bold text-fg">{{ t('update.newVersionFound') }}</h3>
            <span class="px-2 py-0.5 text-[11px] font-black tracking-wide rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">
              {{ release.tagName }}
            </span>
          </div>
          <div class="flex items-center gap-2 text-xs text-fg-3 mt-0.5">
            <span>{{ t('update.current', { version: CURRENT_VERSION }) }}</span>
            <span>·</span>
            <span>{{ t('update.packageSize', { size: formattedSize }) }}</span>
          </div>
        </div>
      </div>

      <!-- Release Notes Box -->
      <div class="my-4 p-3.5 rounded-2xl bg-surface-2/70 border border-white/5 max-h-48 overflow-y-auto space-y-1.5 custom-scrollbar text-left text-xs leading-relaxed text-fg-2">
        <div class="font-semibold text-fg text-[11px] uppercase tracking-wider text-fg-3 mb-1">
          {{ t('update.releaseNotes') }}
        </div>
        <div class="whitespace-pre-line text-[11px] text-fg-2 select-text">
          {{ release.notes || t('update.noNotes') }}
        </div>
      </div>

      <!-- Error alert if any -->
      <div v-if="errorMessage" class="mb-4 p-3 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center gap-2 text-xs text-red-300">
        <AlertCircle class="w-4 h-4 shrink-0 text-red-400" />
        <span>{{ errorMessage }}</span>
      </div>

      <!-- Downloading Progress or Buttons -->
      <div v-if="isDownloading" class="mt-5 space-y-2">
        <div class="flex justify-between items-center text-xs">
          <span class="text-fg-2 font-medium">
            {{ downloadProgress < 100 ? t('update.downloading') : t('update.mounting') }}
          </span>
          <span class="text-amber-400 font-bold font-mono">{{ downloadProgress }}%</span>
        </div>
        <div class="w-full h-2.5 bg-white/10 rounded-full overflow-hidden p-0.5 border border-white/5">
          <div
            class="h-full bg-gradient-to-r from-amber-500 to-orange-500 rounded-full transition-all duration-200"
            :style="{ width: `${downloadProgress}%` }"
          ></div>
        </div>
      </div>

      <div v-else class="mt-5 flex items-center justify-end gap-2.5">
        <button
          @click="emit('close')"
          class="px-4 py-2 rounded-xl text-xs font-semibold text-fg-3 hover:text-fg hover:bg-white/5 transition cursor-pointer"
        >
          {{ t('update.later') }}
        </button>
        <button
          @click="startUpdate"
          class="px-5 py-2.5 rounded-xl bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600 text-white shadow-lg shadow-orange-500/25 text-xs font-bold flex items-center gap-2 transition cursor-pointer active:scale-98"
        >
          <ArrowDownCircle class="w-4 h-4" />
          <span>{{ t('update.downloadAndInstall') }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
