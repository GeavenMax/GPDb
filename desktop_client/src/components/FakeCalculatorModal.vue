<script setup lang="ts">
import { ref } from 'vue';
import { privacySettings, dismissPanicMode } from '../services/privacy';

const display = ref('0');
const prevValue = ref<number | null>(null);
const currentOp = ref<string | null>(null);
const waitingForOperand = ref(false);
const secretBuffer = ref('');
const headerTapCount = ref(0);

function handleDigit(digit: string) {
  secretBuffer.value += digit;
  if (waitingForOperand.value) {
    display.value = digit;
    waitingForOperand.value = false;
  } else {
    display.value = display.value === '0' ? digit : display.value + digit;
  }
}

function handleDecimal() {
  if (waitingForOperand.value) {
    display.value = '0.';
    waitingForOperand.value = false;
    return;
  }
  if (!display.value.includes('.')) {
    display.value += '.';
  }
}

function handleClear() {
  display.value = '0';
  prevValue.value = null;
  currentOp.value = null;
  waitingForOperand.value = false;
  secretBuffer.value = '';
}

function handleSign() {
  const num = parseFloat(display.value);
  if (!isNaN(num)) {
    display.value = String(-num);
  }
}

function handlePercent() {
  const num = parseFloat(display.value);
  if (!isNaN(num)) {
    display.value = String(num / 100);
  }
}

function handleOp(op: string) {
  const current = parseFloat(display.value);
  if (prevValue.value != null && currentOp.value && !waitingForOperand.value) {
    calculate();
  } else {
    prevValue.value = current;
  }
  currentOp.value = op;
  waitingForOperand.value = true;
}

function calculate() {
  // Check secret unlock PIN + =
  const targetPin = privacySettings.value.pinCode || '1234';
  if (secretBuffer.value.endsWith(targetPin)) {
    dismissPanicMode();
    return;
  }

  const current = parseFloat(display.value);
  if (prevValue.value == null || !currentOp.value) return;

  let result = 0;
  switch (currentOp.value) {
    case '+': result = prevValue.value + current; break;
    case '-': result = prevValue.value - current; break;
    case '×': result = prevValue.value * current; break;
    case '÷': result = current !== 0 ? prevValue.value / current : 0; break;
  }

  display.value = String(Math.round(result * 100000000) / 100000000);
  prevValue.value = null;
  currentOp.value = null;
  waitingForOperand.value = true;
}

function onHeaderClick() {
  headerTapCount.value++;
  if (headerTapCount.value >= 4) {
    dismissPanicMode();
  }
}
</script>

<template>
  <div class="fixed inset-0 z-[300] bg-[#1c1c1e] text-white flex flex-col items-center justify-center select-none font-sans p-4">
    <!-- Calculator Frame -->
    <div class="w-full max-w-[340px] bg-black/90 p-5 rounded-3xl shadow-2xl border border-white/10 flex flex-col gap-3">
      <!-- Top Status Header (Discreet unlock target) -->
      <div
        @click="onHeaderClick"
        class="flex items-center justify-between text-xs text-white/30 px-2 py-1 cursor-default"
      >
        <span class="tracking-wide">标准计算器</span>
        <span v-if="currentOp" class="font-mono text-white/50 text-sm">{{ currentOp }}</span>
      </div>

      <!-- Display Screen -->
      <div class="px-3 py-4 text-right overflow-hidden flex items-end justify-end h-20">
        <span
          class="font-light tracking-tight font-mono truncate"
          :class="display.length > 9 ? 'text-3xl' : 'text-5xl'"
        >
          {{ display }}
        </span>
      </div>

      <!-- Button Grid -->
      <div class="grid grid-cols-4 gap-2.5">
        <!-- Row 1 -->
        <button @click="handleClear" class="calc-btn bg-[#a5a5a5] text-black active:bg-white font-medium">
          {{ display === '0' ? 'AC' : 'C' }}
        </button>
        <button @click="handleSign" class="calc-btn bg-[#a5a5a5] text-black active:bg-white font-medium">±</button>
        <button @click="handlePercent" class="calc-btn bg-[#a5a5a5] text-black active:bg-white font-medium">%</button>
        <button @click="handleOp('÷')" :class="['calc-btn bg-[#ff9f0a] text-white active:bg-[#ffb03a] font-medium text-2xl', currentOp === '÷' ? 'ring-2 ring-white' : '']">÷</button>

        <!-- Row 2 -->
        <button @click="handleDigit('7')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">7</button>
        <button @click="handleDigit('8')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">8</button>
        <button @click="handleDigit('9')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">9</button>
        <button @click="handleOp('×')" :class="['calc-btn bg-[#ff9f0a] text-white active:bg-[#ffb03a] font-medium text-2xl', currentOp === '×' ? 'ring-2 ring-white' : '']">×</button>

        <!-- Row 3 -->
        <button @click="handleDigit('4')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">4</button>
        <button @click="handleDigit('5')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">5</button>
        <button @click="handleDigit('6')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">6</button>
        <button @click="handleOp('-')" :class="['calc-btn bg-[#ff9f0a] text-white active:bg-[#ffb03a] font-medium text-2xl', currentOp === '-' ? 'ring-2 ring-white' : '']">-</button>

        <!-- Row 4 -->
        <button @click="handleDigit('1')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">1</button>
        <button @click="handleDigit('2')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">2</button>
        <button @click="handleDigit('3')" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">3</button>
        <button @click="handleOp('+')" :class="['calc-btn bg-[#ff9f0a] text-white active:bg-[#ffb03a] font-medium text-2xl', currentOp === '+' ? 'ring-2 ring-white' : '']">+</button>

        <!-- Row 5 -->
        <button @click="handleDigit('0')" class="calc-btn col-span-2 bg-[#333333] text-white active:bg-[#555555] !justify-start !pl-7">0</button>
        <button @click="handleDecimal" class="calc-btn bg-[#333333] text-white active:bg-[#555555]">.</button>
        <button @click="calculate" class="calc-btn bg-[#ff9f0a] text-white active:bg-[#ffb03a] font-medium text-2xl">=</button>
      </div>

      <div class="text-center pt-2">
        <span class="text-[10px] text-white/20">输入安全密码并按「=」或连击顶部标题返回</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.calc-btn {
  height: 64px;
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  transition: all 0.1s ease;
  user-select: none;
  cursor: pointer;
}
</style>
