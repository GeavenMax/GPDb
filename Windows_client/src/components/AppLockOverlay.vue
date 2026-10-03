<script setup lang="ts">
import { ref } from 'vue';
import { Lock, Delete } from '@lucide/vue';
import { unlockApp, privacySettings } from '../services/privacy';
import { t } from '../i18n';

const enteredPin = ref('');
const isError = ref(false);

function handleNumber(num: number) {
  if (enteredPin.value.length < 6) {
    enteredPin.value += String(num);
    if (enteredPin.value.length >= 4 && enteredPin.value.length === (privacySettings.value.pinCode.length || 4)) {
      verify();
    }
  }
}

function handleBackspace() {
  enteredPin.value = enteredPin.value.slice(0, -1);
  isError.value = false;
}

function handleClear() {
  enteredPin.value = '';
  isError.value = false;
}

function verify() {
  const success = unlockApp(enteredPin.value);
  if (!success) {
    isError.value = true;
    setTimeout(() => {
      enteredPin.value = '';
      isError.value = false;
    }, 600);
  } else {
    enteredPin.value = '';
  }
}
</script>

<template>
  <div class="fixed inset-0 z-[290] bg-scrim/95 backdrop-blur-xl flex flex-col items-center justify-center select-none text-fg p-4 animate-fade-in">
    <div class="w-full max-w-sm flex flex-col items-center space-y-6">
      <!-- Icon & Title -->
      <div class="w-16 h-16 rounded-3xl bg-accent-fill/15 border border-accent-fill/30 flex items-center justify-center text-accent shadow-xl shadow-accent-fill/10 mb-2">
        <Lock class="w-8 h-8" />
      </div>

      <div class="text-center space-y-1">
        <h2 class="text-xl font-bold tracking-tight text-fg">{{ t('lock.title') }}</h2>
        <p class="text-xs text-fg-4">{{ t('lock.subtitle') }}</p>
      </div>

      <!-- PIN Dots Indicator -->
      <div
        class="flex items-center gap-3.5 py-4"
        :class="{ 'animate-shake': isError }"
      >
        <div
          v-for="idx in (privacySettings.pinCode.length || 4)"
          :key="idx"
          :class="[
            'w-3.5 h-3.5 rounded-full transition-all duration-200 border',
            isError
              ? 'bg-danger-fill border-danger-fill scale-110'
              : enteredPin.length >= idx
                ? 'bg-accent-fill border-accent-fill scale-110 shadow-md shadow-accent-fill/40'
                : 'bg-surface-2 border-line-strong'
          ]"
        ></div>
      </div>

      <!-- Keypad -->
      <div class="grid grid-cols-3 gap-3.5 w-64 pt-2">
        <button
          v-for="n in [1, 2, 3, 4, 5, 6, 7, 8, 9]"
          :key="n"
          @click="handleNumber(n)"
          class="key-btn"
        >
          {{ n }}
        </button>

        <button @click="handleClear" class="key-btn text-xs font-medium text-fg-4">
          {{ t('common.clear') }}
        </button>
        <button @click="handleNumber(0)" class="key-btn">
          0
        </button>
        <button @click="handleBackspace" class="key-btn text-fg-3 hover:text-fg">
          <Delete class="w-5 h-5" />
        </button>
      </div>

      <div v-if="isError" class="text-xs text-danger font-medium animate-fade-in">
        {{ t('lock.error') }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.key-btn {
  height: 60px;
  border-radius: 9999px;
  background-color: var(--color-surface, rgba(255, 255, 255, 0.08));
  border: 1px solid var(--color-line, rgba(255, 255, 255, 0.12));
  color: var(--color-fg, #ffffff);
  font-size: 1.5rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
  cursor: pointer;
  user-select: none;
}
.key-btn:hover {
  background-color: var(--color-surface-2, rgba(255, 255, 255, 0.15));
  border-color: var(--color-accent, #6366f1);
}
.key-btn:active {
  transform: scale(0.94);
  background-color: var(--color-accent-fill, #6366f1);
  color: var(--color-on-fill, #ffffff);
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-8px); }
  40%, 80% { transform: translateX(8px); }
}
.animate-shake {
  animation: shake 0.4s ease-in-out;
}
</style>
