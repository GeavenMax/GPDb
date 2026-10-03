<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
  Terminal, CheckCircle2, AlertTriangle, X, RefreshCw,
  Copy, Check, Database, Sparkles, Cpu
} from '@lucide/vue';
import { api } from '../api';
import { t } from '../i18n';
import type { RuntimeEnvironmentInfo } from '../types';

const props = defineProps<{
  show: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'open-database-setup'): void;
}>();

const checking = ref(false);
const envInfo = ref<RuntimeEnvironmentInfo | null>(null);
const copiedKey = ref<string | null>(null);

async function runCheck() {
  checking.value = true;
  try {
    const info = await api.checkRuntimeEnvironment();
    envInfo.value = info;
  } catch (err) {
    console.error('Environment check failed:', err);
  } finally {
    checking.value = false;
  }
}

function copyCommand(key: string, text: string) {
  navigator.clipboard.writeText(text);
  copiedKey.value = key;
  setTimeout(() => {
    if (copiedKey.value === key) copiedKey.value = null;
  }, 2000);
}

function handleDismiss() {
  localStorage.setItem('gpdb_env_check_dismissed', 'true');
  emit('close');
}

onMounted(() => {
  runCheck();
});
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
        class="fixed inset-0 z-[160] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in select-none"
      >
        <div
          class="relative w-full max-w-xl rounded-3xl bg-surface border border-line shadow-2xl overflow-hidden p-6 md:p-8 space-y-6"
        >
          <!-- Top ambient glow -->
          <div class="absolute -top-24 left-1/2 -translate-x-1/2 w-96 h-48 bg-gradient-to-b from-indigo-500/20 via-sky-500/10 to-transparent blur-3xl pointer-events-none"></div>

          <!-- Header -->
          <div class="flex items-start justify-between gap-4 relative z-10">
            <div class="flex items-center gap-3.5">
              <div class="p-3 rounded-2xl bg-gradient-to-tr from-sky-500 to-indigo-600 text-white shadow-lg shadow-indigo-500/25">
                <Cpu class="w-6 h-6" />
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <h3 class="text-lg md:text-xl font-black text-fg tracking-tight">
                    {{ t('envCheck.title') }}
                  </h3>
                  <span
                    v-if="envInfo?.all_ready"
                    class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 font-bold border border-emerald-500/30 flex items-center gap-1"
                  >
                    <CheckCircle2 class="w-3 h-3" />
                    {{ t('envCheck.allReady') }}
                  </span>
                  <span
                    v-else
                    class="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 font-bold border border-amber-500/30 flex items-center gap-1"
                  >
                    <AlertTriangle class="w-3 h-3" />
                    {{ t('envCheck.needsConfig') }}
                  </span>
                </div>
                <p class="text-xs text-fg-4 mt-0.5">
                  {{ t('envCheck.subtitle') }}
                </p>
              </div>
            </div>
            <button
              @click="handleDismiss"
              class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg transition cursor-pointer"
              :title="t('common.close')"
            >
              <X class="w-4 h-4" />
            </button>
          </div>

          <!-- Diagnostic Check Items -->
          <div class="space-y-3 relative z-10 max-h-[60vh] overflow-y-auto pr-1">
            <!-- 1. Python 3 Runtime -->
            <div
              class="p-4 rounded-2xl border transition"
              :class="envInfo?.python_installed
                ? 'bg-surface-2/60 border-line/80'
                : 'bg-rose-500/5 border-rose-500/30'"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="flex items-start gap-3">
                  <div
                    class="p-2 rounded-xl shrink-0 mt-0.5"
                    :class="envInfo?.python_installed
                      ? 'bg-emerald-500/15 text-emerald-400'
                      : 'bg-rose-500/15 text-rose-400'"
                  >
                    <Terminal class="w-4 h-4" />
                  </div>
                  <div class="space-y-0.5">
                    <div class="flex items-center gap-2">
                      <span class="text-xs font-bold text-fg">{{ t('envCheck.pyTitle') }}</span>
                      <span
                        class="text-[10px] px-1.5 py-0.5 rounded font-mono font-medium"
                        :class="envInfo?.python_installed ? 'text-emerald-400 bg-emerald-500/10' : 'text-rose-400 bg-rose-500/10'"
                      >
                        {{ envInfo?.python_installed ? (envInfo.python_version || t('envCheck.pyReady')) : t('envCheck.pyNotDetected') }}
                      </span>
                    </div>
                    <p class="text-[11px] text-fg-4 leading-relaxed">
                      {{ envInfo?.python_installed
                        ? t('envCheck.pyPath', { path: envInfo.python_path || 'python3' })
                        : t('envCheck.pyMissingDesc') }}
                    </p>
                  </div>
                </div>

                <div v-if="envInfo?.python_installed" class="text-emerald-400 p-1">
                  <CheckCircle2 class="w-4 h-4" />
                </div>
              </div>

              <!-- Install guidance if python missing -->
              <div v-if="envInfo && !envInfo.python_installed" class="mt-3 pt-3 border-t border-line/60 space-y-2">
                <p class="text-[11px] text-fg-3 font-medium">{{ t('envCheck.pyInstallTip') }}</p>
                <div class="flex items-center justify-between gap-2 p-2 rounded-xl bg-surface-3/80 border border-line font-mono text-xs text-fg-2">
                  <span class="truncate">xcode-select --install</span>
                  <button
                    @click="copyCommand('xcode', 'xcode-select --install')"
                    class="px-2 py-1 rounded-lg bg-surface hover:bg-surface-2 text-[11px] text-fg font-sans flex items-center gap-1 shrink-0 transition"
                  >
                    <Check v-if="copiedKey === 'xcode'" class="w-3 h-3 text-emerald-400" />
                    <Copy v-else class="w-3 h-3" />
                    <span>{{ copiedKey === 'xcode' ? t('envCheck.copied') : t('envCheck.copyCmd') }}</span>
                  </button>
                </div>
                <div class="flex items-center justify-between gap-2 p-2 rounded-xl bg-surface-3/80 border border-line font-mono text-xs text-fg-2">
                  <span class="truncate">brew install python3</span>
                  <button
                    @click="copyCommand('brew', 'brew install python3')"
                    class="px-2 py-1 rounded-lg bg-surface hover:bg-surface-2 text-[11px] text-fg font-sans flex items-center gap-1 shrink-0 transition"
                  >
                    <Check v-if="copiedKey === 'brew'" class="w-3 h-3 text-emerald-400" />
                    <Copy v-else class="w-3 h-3" />
                    <span>{{ copiedKey === 'brew' ? t('envCheck.copied') : t('envCheck.copyCmd') }}</span>
                  </button>
                </div>
              </div>
            </div>

            <!-- 2. SQLite Database -->
            <div
              class="p-4 rounded-2xl border transition"
              :class="envInfo?.database_ready
                ? 'bg-surface-2/60 border-line/80'
                : 'bg-amber-500/5 border-amber-500/30'"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="flex items-start gap-3">
                  <div
                    class="p-2 rounded-xl shrink-0 mt-0.5"
                    :class="envInfo?.database_ready
                      ? 'bg-emerald-500/15 text-emerald-400'
                      : 'bg-amber-500/15 text-amber-400'"
                  >
                    <Database class="w-4 h-4" />
                  </div>
                  <div class="space-y-0.5">
                    <div class="flex items-center gap-2">
                      <span class="text-xs font-bold text-fg">{{ t('envCheck.dbTitle') }}</span>
                      <span
                        class="text-[10px] px-1.5 py-0.5 rounded font-medium"
                        :class="envInfo?.database_ready ? 'text-emerald-400 bg-emerald-500/10' : 'text-amber-400 bg-amber-500/10'"
                      >
                        {{ envInfo?.database_ready ? t('envCheck.dbConnected') : t('envCheck.dbNotConnected') }}
                      </span>
                    </div>
                    <p class="text-[11px] text-fg-4 leading-relaxed truncate max-w-sm">
                      {{ envInfo?.database_ready
                        ? t('envCheck.dbPath', { path: envInfo.database_path })
                        : t('envCheck.dbMissing') }}
                    </p>
                  </div>
                </div>

                <div v-if="envInfo?.database_ready" class="text-emerald-400 p-1">
                  <CheckCircle2 class="w-4 h-4" />
                </div>
                <button
                  v-else
                  @click="emit('open-database-setup')"
                  class="px-2.5 py-1 rounded-xl bg-amber-500/20 text-amber-300 hover:bg-amber-500/30 border border-amber-500/30 text-xs font-bold transition shrink-0 cursor-pointer"
                >
                  {{ t('envCheck.goConfigure') }}
                </button>
              </div>
            </div>

            <!-- 3. Playwright Extension (Optional) -->
            <div class="p-4 rounded-2xl bg-surface-2/60 border border-line/80 space-y-2">
              <div class="flex items-start justify-between gap-3">
                <div class="flex items-start gap-3">
                  <div
                    class="p-2 rounded-xl shrink-0 mt-0.5"
                    :class="envInfo?.playwright_available
                      ? 'bg-emerald-500/15 text-emerald-400'
                      : 'bg-indigo-500/15 text-indigo-400'"
                  >
                    <Sparkles class="w-4 h-4" />
                  </div>
                  <div class="space-y-0.5">
                    <div class="flex items-center gap-2">
                      <span class="text-xs font-bold text-fg">{{ t('envCheck.playwrightTitle') }}</span>
                      <span
                        class="text-[10px] px-1.5 py-0.5 rounded font-medium"
                        :class="envInfo?.playwright_available ? 'text-emerald-400 bg-emerald-500/10' : 'text-indigo-400 bg-indigo-500/10'"
                      >
                        {{ envInfo?.playwright_available ? t('envCheck.playwrightActive') : t('envCheck.playwrightOptional') }}
                      </span>
                    </div>
                    <p class="text-[11px] text-fg-4 leading-relaxed">
                      {{ t('envCheck.playwrightDesc') }}
                    </p>
                  </div>
                </div>

                <div v-if="envInfo?.playwright_available" class="text-emerald-400 p-1">
                  <CheckCircle2 class="w-4 h-4" />
                </div>
              </div>

              <div v-if="envInfo && !envInfo.playwright_available && envInfo.python_installed" class="pt-2 border-t border-line/40">
                <div class="flex items-center justify-between gap-2 p-2 rounded-xl bg-surface-3/80 border border-line font-mono text-[11px] text-fg-3">
                  <span class="truncate">pip install playwright && playwright install chromium</span>
                  <button
                    @click="copyCommand('playwright', 'pip install playwright && playwright install chromium')"
                    class="px-2 py-1 rounded-lg bg-surface hover:bg-surface-2 text-[11px] text-fg font-sans flex items-center gap-1 shrink-0 transition"
                  >
                    <Check v-if="copiedKey === 'playwright'" class="w-3 h-3 text-emerald-400" />
                    <Copy v-else class="w-3 h-3" />
                    <span>{{ copiedKey === 'playwright' ? t('envCheck.copied') : t('envCheck.copyCmd') }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Bottom Actions -->
          <div class="flex items-center justify-between gap-3 pt-4 border-t border-line/60 relative z-10 flex-wrap">
            <button
              @click="runCheck"
              :disabled="checking"
              class="px-4 py-2.5 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-2 hover:text-fg text-xs font-bold flex items-center gap-2 transition border border-line cursor-pointer disabled:opacity-50"
            >
              <RefreshCw class="w-3.5 h-3.5" :class="{ 'animate-spin': checking }" />
              <span>{{ checking ? t('envCheck.rechecking') : t('envCheck.recheckBtn') }}</span>
            </button>

            <div class="flex items-center gap-2 ml-auto">
              <button
                @click="handleDismiss"
                class="px-5 py-2.5 rounded-xl bg-accent-fill text-on-fill text-xs font-bold hover:bg-accent-fill/90 transition shadow-lg shadow-accent-fill/20 cursor-pointer"
              >
                {{ envInfo?.all_ready ? t('envCheck.readyBtn') : t('envCheck.laterBtn') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
