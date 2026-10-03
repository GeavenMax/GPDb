<script setup lang="ts">
import { ShieldCheck, HardDrive, FileSearch, X, Sparkles, Loader2, Database } from '@lucide/vue';
import { t } from '../i18n';

defineProps<{
  show: boolean;
  scanning?: boolean;
  creating?: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'pickFile'): void;
  (e: 'scanFolders'): void;
  (e: 'createDatabase'): void;
}>();
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
        class="fixed inset-0 z-[150] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in select-none"
      >
        <div
          class="relative w-full max-w-lg rounded-3xl bg-surface border border-line shadow-2xl overflow-hidden p-6 md:p-8 space-y-6"
        >
          <!-- Top ambient light -->
          <div class="absolute -top-24 left-1/2 -translate-x-1/2 w-80 h-48 bg-gradient-to-b from-indigo-500/20 to-purple-500/0 blur-3xl pointer-events-none"></div>

          <!-- Header -->
          <div class="flex items-start justify-between gap-4 relative z-10">
            <div class="flex items-center gap-3.5">
              <div class="p-3 rounded-2xl bg-gradient-to-tr from-indigo-600 to-purple-600 text-white shadow-lg shadow-indigo-500/25">
                <ShieldCheck class="w-6 h-6" />
              </div>
              <div>
                <h3 class="text-lg md:text-xl font-black text-fg tracking-tight">
                  {{ t('permission.title') }}
                </h3>
                <p class="text-xs text-fg-4 mt-0.5">
                  {{ t('permission.subtitle') }}
                </p>
              </div>
            </div>
            <button
              @click="emit('close')"
              :disabled="creating"
              class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg transition cursor-pointer disabled:opacity-30"
            >
              <X class="w-4 h-4" />
            </button>
          </div>

          <!-- Description points -->
          <div class="space-y-3 relative z-10 text-xs text-fg-3 leading-relaxed">
            <div class="p-3.5 rounded-2xl bg-surface-2/60 border border-line flex items-start gap-3">
              <HardDrive class="w-4 h-4 text-indigo-400 shrink-0 mt-0.5" />
              <div>
                <strong class="text-fg block mb-0.5">{{ t('permission.whyAccess') }}</strong>
                {{ t('permission.whyAccessDesc') }}
              </div>
            </div>

            <div class="p-3.5 rounded-2xl bg-surface-2/60 border border-line flex items-start gap-3">
              <ShieldCheck class="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
              <div>
                <strong class="text-fg block mb-0.5">{{ t('permission.zeroCloud') }}</strong>
                {{ t('permission.zeroCloudDesc') }}
              </div>
            </div>

            <div class="p-3.5 rounded-2xl bg-surface-2/60 border border-line flex items-start gap-3">
              <Database class="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
              <div>
                <strong class="text-fg block mb-0.5">{{ t('permission.firstTime') }}</strong>
                {{ t('permission.firstTimeDesc') }}
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="space-y-3 pt-1 relative z-10">
            <!-- 1-Click Create New Blank Database (First-time user onboarding) -->
            <button
              @click="emit('createDatabase')"
              :disabled="creating || scanning"
              class="w-full px-5 py-3.5 rounded-2xl bg-gradient-to-r from-amber-500 via-orange-500 to-rose-500 hover:from-amber-400 hover:to-rose-400 text-white font-bold text-xs shadow-lg shadow-orange-500/20 transition flex items-center justify-center gap-2 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed active:scale-98"
            >
              <Loader2 v-if="creating" class="w-4 h-4 animate-spin text-white" />
              <Sparkles v-else class="w-4 h-4 text-amber-200" />
              <span>{{ creating ? t('permission.creating') : t('permission.createBtn') }}</span>
            </button>

            <div class="relative flex items-center justify-center my-1">
              <div class="border-t border-line w-full"></div>
              <span class="bg-surface px-3 text-[11px] text-fg-4 shrink-0 font-medium">{{ t('permission.hasExisting') }}</span>
              <div class="border-t border-line w-full"></div>
            </div>

            <div class="flex items-center gap-3">
              <!-- Pick file button -->
              <button
                @click="emit('pickFile')"
                :disabled="creating || scanning"
                class="flex-1 px-4 py-3 rounded-2xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold text-xs shadow-lg shadow-indigo-500/25 transition flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50 active:scale-98"
              >
                <FileSearch class="w-4 h-4" />
                <span>{{ t('permission.pickFile') }}</span>
              </button>

              <!-- Scan folders button -->
              <button
                @click="emit('scanFolders')"
                :disabled="creating || scanning"
                class="px-4 py-3 rounded-2xl bg-surface-2 hover:bg-surface-3 border border-line text-fg font-bold text-xs transition flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50 active:scale-98"
              >
                <Loader2 v-if="scanning" class="w-3.5 h-3.5 animate-spin" />
                <span>{{ scanning ? t('permission.scanning') : t('permission.scanFolders') }}</span>
              </button>
            </div>

            <div class="text-center pt-1">
              <button
                @click="emit('close')"
                :disabled="creating"
                class="text-[11px] text-fg-4 hover:text-fg-3 transition cursor-pointer disabled:opacity-30"
              >
                {{ t('permission.configLater') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
