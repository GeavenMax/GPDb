<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import {
  X, Share2, Copy, Download, Check, Shield, Eye, EyeOff,
  Film, Quote, Lock
} from '@lucide/vue';
import { claimEscape } from '../utils/escape';
import { api, IS_TAURI } from '../api';

export interface ShareCardData {
  type: 'movie' | 'episode';
  title: string;
  titleAlt?: string | null;
  posterUrl: string;
  category?: string | null;
  releaseDate?: string | null;
  durationMins?: number | null;
  studioName?: string | null;
  directorName?: string | null;
  performers?: string[];
  description?: string | null;
  descriptionZh?: string | null;
  episodeHeading?: string | null;
  id: number | string;
}

const props = defineProps<{
  show: boolean;
  data: ShareCardData | null;
  zIndex?: number;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

// Privacy & Customization toggles
const blurPoster = ref(false);
const blurDescription = ref(false);
const ambientTheme = ref<'vibrant' | 'dark' | 'midnight'>('vibrant');
const includeDescription = ref(true);

// Action button feedback states
const isCopying = ref(false);
const isCopied = ref(false);
const isSaving = ref(false);
const isSaved = ref(false);

const displayDescription = computed(() => {
  if (!props.data) return '';
  return props.data.descriptionZh?.trim() || props.data.description?.trim() || '';
});

const formattedDate = computed(() => {
  const d = new Date();
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
});

function handleClose() {
  emit('close');
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.show) {
    if (claimEscape(e)) {
      handleClose();
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown);
});

// Reset states on open
watch(() => props.show, (val) => {
  if (val) {
    isCopied.value = false;
    isSaved.value = false;
  }
});

/**
 * High-definition Canvas Render Engine
 * Generates an ultra-crisp 2x Retina PNG card with sampled ambient blur,
 * custom glassmorphism, typography, and optional privacy blur.
 */
async function renderCardToCanvas(): Promise<HTMLCanvasElement | null> {
  if (!props.data) return null;

  const scale = 2; // 2x Retina resolution
  const width = 640;
  // Estimate height dynamically based on description length
  const hasDesc = includeDescription.value && displayDescription.value;
  const descLines = hasDesc ? Math.min(6, Math.ceil(displayDescription.value.length / 28)) : 0;
  const height = 720 + (hasDesc ? 90 + descLines * 22 : 0);

  const canvas = document.createElement('canvas');
  canvas.width = width * scale;
  canvas.height = height * scale;
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;

  ctx.scale(scale, scale);

  // 1. Base Background
  ctx.fillStyle = ambientTheme.value === 'midnight' ? '#090d16' : '#0c0f17';
  ctx.fillRect(0, 0, width, height);

  // 2. Load Poster Image
  let posterImg: HTMLImageElement | null = null;
  if (props.data.posterUrl) {
    posterImg = new Image();
    posterImg.crossOrigin = 'anonymous';
    posterImg.src = props.data.posterUrl;
    await new Promise((resolve) => {
      if (!posterImg) return resolve(null);
      if (posterImg.complete) return resolve(null);
      posterImg.onload = () => resolve(null);
      posterImg.onerror = () => resolve(null);
    });
  }

  // 3. Draw Ambient Blur Background sampled from Poster
  if (posterImg && posterImg.width > 0 && ambientTheme.value !== 'dark') {
    ctx.save();
    ctx.filter = 'blur(60px) saturate(180%)';
    ctx.globalAlpha = ambientTheme.value === 'vibrant' ? 0.45 : 0.25;
    ctx.drawImage(posterImg, -50, -50, width + 100, height + 100);
    ctx.restore();
  }

  // Gradient Tint Layer
  const grad = ctx.createLinearGradient(0, 0, 0, height);
  if (ambientTheme.value === 'midnight') {
    grad.addColorStop(0, 'rgba(10, 16, 30, 0.6)');
    grad.addColorStop(0.5, 'rgba(8, 12, 22, 0.85)');
    grad.addColorStop(1, 'rgba(5, 8, 15, 0.98)');
  } else if (ambientTheme.value === 'dark') {
    grad.addColorStop(0, 'rgba(18, 20, 26, 0.9)');
    grad.addColorStop(1, 'rgba(10, 11, 15, 0.98)');
  } else {
    grad.addColorStop(0, 'rgba(15, 18, 26, 0.5)');
    grad.addColorStop(0.4, 'rgba(12, 15, 23, 0.85)');
    grad.addColorStop(1, 'rgba(8, 10, 16, 0.96)');
  }
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, width, height);

  // 4. Subtle Outer Card Rim
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
  ctx.lineWidth = 1;
  ctx.strokeRect(12, 12, width - 24, height - 24);

  // 5. Card Header (Branding & Tags)
  const padX = 36;
  let curY = 42;

  // GPDb Brand Badge
  ctx.save();
  ctx.fillStyle = 'rgba(245, 158, 11, 0.15)';
  ctx.strokeStyle = 'rgba(245, 158, 11, 0.35)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.roundRect(padX, curY, 150, 28, 8);
  ctx.fill();
  ctx.stroke();

  ctx.fillStyle = '#f59e0b';
  ctx.font = 'bold 12px "Microsoft YaHei UI", sans-serif';
  ctx.fillText('GPDb · 影视私有档案', padX + 14, curY + 18);
  ctx.restore();

  // Category / Year Badge on Top Right
  const rightTag = props.data.category || (props.data.type === 'episode' ? '分集剧情' : '电影档案');
  ctx.save();
  ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
  ctx.beginPath();
  ctx.roundRect(width - padX - 110, curY, 110, 28, 8);
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = 'rgba(255, 255, 255, 0.8)';
  ctx.font = '500 11px "Microsoft YaHei UI", sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText(rightTag, width - padX - 55, curY + 18);
  ctx.restore();

  curY += 48;

  // 6. Poster Section (Centered or Side Layout)
  const posterW = 210;
  const posterH = 285;
  const posterX = (width - posterW) / 2;
  const posterY = curY;

  // Ambient Drop Glow behind Poster
  if (posterImg && posterImg.width > 0) {
    ctx.save();
    ctx.filter = 'blur(20px)';
    ctx.globalAlpha = 0.5;
    ctx.drawImage(posterImg, posterX - 4, posterY - 4, posterW + 8, posterH + 8);
    ctx.restore();
  }

  // Draw Poster
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(posterX, posterY, posterW, posterH, 16);
  ctx.clip();

  if (posterImg && posterImg.width > 0) {
    if (blurPoster.value) {
      ctx.filter = 'blur(28px) brightness(0.85)';
    }
    ctx.drawImage(posterImg, posterX, posterY, posterW, posterH);

    // If blurred, draw privacy badge over poster
    if (blurPoster.value) {
      ctx.filter = 'none';
      ctx.fillStyle = 'rgba(0, 0, 0, 0.55)';
      ctx.fillRect(posterX, posterY, posterW, posterH);

      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 13px "Microsoft YaHei UI", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('🔒 海报隐私保护', posterX + posterW / 2, posterY + posterH / 2);
    }
  } else {
    ctx.fillStyle = '#1c212c';
    ctx.fillRect(posterX, posterY, posterW, posterH);
    ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
    ctx.font = '13px "Microsoft YaHei UI", sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('暂无海报', posterX + posterW / 2, posterY + posterH / 2);
  }
  ctx.restore();

  // Subtle border around poster
  ctx.save();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.roundRect(posterX, posterY, posterW, posterH, 16);
  ctx.stroke();
  ctx.restore();

  curY += posterH + 26;

  // 7. Title & Metadata
  ctx.textAlign = 'center';

  // Primary Title
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 22px "Microsoft YaHei UI", sans-serif';
  const mainTitle = props.data.title;
  // Truncate if too long
  const displayTitle = mainTitle.length > 28 ? mainTitle.slice(0, 27) + '...' : mainTitle;
  ctx.fillText(displayTitle, width / 2, curY);
  curY += 24;

  // Alt Title
  if (props.data.titleAlt && props.data.titleAlt !== props.data.title) {
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.font = '13px "Microsoft YaHei UI", sans-serif';
    const alt = props.data.titleAlt.length > 38 ? props.data.titleAlt.slice(0, 37) + '...' : props.data.titleAlt;
    ctx.fillText(alt, width / 2, curY);
    curY += 22;
  }

  // Meta Badges Row (Release Date, Duration, Studio, Episode)
  const metaItems: string[] = [];
  if (props.data.releaseDate) metaItems.push(`📅 ${props.data.releaseDate}`);
  if (props.data.durationMins) metaItems.push(`⏱ ${props.data.durationMins} 分钟`);
  if (props.data.studioName) metaItems.push(`🏢 ${props.data.studioName}`);
  if (props.data.episodeHeading) metaItems.push(props.data.episodeHeading);

  if (metaItems.length > 0) {
    curY += 6;
    ctx.fillStyle = 'rgba(245, 158, 11, 0.9)';
    ctx.font = '500 12px "Microsoft YaHei UI", sans-serif';
    ctx.fillText(metaItems.join('  ·  '), width / 2, curY);
    curY += 20;
  }

  // Cast Pills
  if (props.data.performers && props.data.performers.length > 0) {
    curY += 4;
    const castNames = props.data.performers.slice(0, 5).join('、');
    ctx.fillStyle = 'rgba(255, 255, 255, 0.65)';
    ctx.font = '12px "Microsoft YaHei UI", sans-serif';
    ctx.fillText(`主演: ${castNames}`, width / 2, curY);
    curY += 24;
  } else {
    curY += 10;
  }

  // 8. Synopsis Card Block (Optional)
  if (hasDesc) {
    curY += 8;
    const descBoxW = width - padX * 2;
    const descPad = 16;
    const descX = padX;
    const descY = curY;

    // Measure wrapped text lines
    ctx.font = '12px "Microsoft YaHei UI", sans-serif';
    const words = displayDescription.value;
    const maxLineW = descBoxW - descPad * 2;
    const lines: string[] = [];
    let curLine = '';

    for (let i = 0; i < words.length; i++) {
      const char = words[i];
      const test = curLine + char;
      if (ctx.measureText(test).width > maxLineW && curLine.length > 0) {
        lines.push(curLine);
        curLine = char;
        if (lines.length >= 6) {
          lines[lines.length - 1] += '...';
          break;
        }
      } else {
        curLine = test;
      }
    }
    if (curLine && lines.length < 6) lines.push(curLine);

    const descBoxH = descPad * 2 + lines.length * 20;

    // Draw Glassmorphism Container for Description
    ctx.save();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.05)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.roundRect(descX, descY, descBoxW, descBoxH, 14);
    ctx.fill();
    ctx.stroke();

    // Draw text with blur if blurDescription is enabled
    ctx.textAlign = 'left';
    if (blurDescription.value) {
      ctx.filter = 'blur(7px)';
      ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
      for (let j = 0; j < lines.length; j++) {
        ctx.fillText(lines[j], descX + descPad, descY + descPad + 14 + j * 20);
      }
      ctx.filter = 'none';

      // Privacy Badge over description
      ctx.fillStyle = 'rgba(0, 0, 0, 0.6)';
      ctx.beginPath();
      ctx.roundRect(descX + descBoxW / 2 - 75, descY + descBoxH / 2 - 14, 150, 28, 8);
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 11px "Microsoft YaHei UI", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('🔒 剧情介绍已隐藏', descX + descBoxW / 2, descY + descBoxH / 2 + 4);
    } else {
      ctx.fillStyle = 'rgba(255, 255, 255, 0.75)';
      for (let j = 0; j < lines.length; j++) {
        ctx.fillText(lines[j], descX + descPad, descY + descPad + 14 + j * 20);
      }
    }
    ctx.restore();

    curY += descBoxH + 20;
  }

  // 9. Footer Watermark & ID
  ctx.save();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(padX, height - 44);
  ctx.lineTo(width - padX, height - 44);
  ctx.stroke();

  ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
  ctx.font = '10px "Microsoft YaHei UI", sans-serif';
  ctx.textAlign = 'left';
  ctx.fillText('GPDb Offline Library · 本地私有影视库', padX, height - 24);

  ctx.textAlign = 'right';
  const itemTag = props.data.type === 'movie' ? `#MOV-${props.data.id}` : `#EP-${props.data.id}`;
  ctx.fillText(`${itemTag}  ·  ${formattedDate.value}`, width - padX, height - 24);
  ctx.restore();

  return canvas;
}

/**
 * Copy Card Image to System Clipboard
 */
async function copyCardImage() {
  if (isCopying.value) return;
  isCopying.value = true;

  try {
    const canvas = await renderCardToCanvas();
    if (!canvas) throw new Error('渲染画布失败');

    const dataUrl = canvas.toDataURL('image/png');

    // 1. In desktop app (macOS / Windows), write to native clipboard directly
    let copiedNatively = false;
    if (IS_TAURI) {
      copiedNatively = await api.copyImageToClipboard(dataUrl);
    }

    // 2. If not copied natively or in browser mode, use Web Clipboard API
    if (!copiedNatively && navigator.clipboard?.write) {
      await new Promise<void>((resolve, reject) => {
        canvas.toBlob(async (blob) => {
          if (!blob) return reject(new Error('生成图片数据失败'));
          try {
            await navigator.clipboard.write([
              new ClipboardItem({ 'image/png': blob })
            ]);
            resolve();
          } catch (err) {
            reject(err);
          }
        }, 'image/png');
      });
    }

    isCopied.value = true;
    setTimeout(() => { isCopied.value = false; }, 2500);
  } catch (e) {
    console.error('复制分享卡片失败，转为本地保存:', e);
    await saveCardImage();
  } finally {
    isCopying.value = false;
  }
}

/**
 * Save Card Image to Disk (.png) - 100% Native Crash-Proof
 */
async function saveCardImage() {
  if (isSaving.value) return;
  isSaving.value = true;

  try {
    const canvas = await renderCardToCanvas();
    if (!canvas) throw new Error('渲染画布失败');

    const dataUrl = canvas.toDataURL('image/png');
    const safeTitle = (props.data?.title || 'film')
      .replace(/[\\/:*?"<>|]/g, '_')
      .slice(0, 30);
    const filename = `GPDb_Share_${safeTitle}_${Date.now()}`;

    // Native Tauri save (zero WebKit navigation, zero process crashes)
    await api.saveShareCardImage(filename, dataUrl);

    isSaved.value = true;
    setTimeout(() => { isSaved.value = false; }, 3000);
  } catch (e) {
    console.error('保存分享卡片失败:', e);
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <div
    v-if="show && data"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-6 bg-scrim/80 backdrop-blur-md animate-fade-in no-privacy-blur"
    :style="{ zIndex: (zIndex ?? 50) + 30 }"
    @click.self="handleClose"
  >
    <!-- Modal Dialog Container -->
    <div
      class="relative w-full max-w-xl max-h-[92vh] chrome-panel border border-line-strong/80 rounded-3xl shadow-2xl overflow-hidden flex flex-col text-fg"
    >
      <!-- Modal Top Bar -->
      <div class="px-5 py-4 border-b border-line flex items-center justify-between bg-surface/60 backdrop-blur-md shrink-0">
        <div class="flex items-center gap-2">
          <div class="w-7 h-7 rounded-lg bg-accent-fill/15 border border-accent-fill/30 flex items-center justify-center text-accent">
            <Share2 class="w-4 h-4" />
          </div>
          <div>
            <h2 class="text-sm font-bold text-fg">一键生成分享卡片</h2>
            <p class="text-[11px] text-fg-4">高斯模糊渐变色自适应提取，支持敏感内容安全脱敏</p>
          </div>
        </div>

        <button
          @click="handleClose"
          class="w-7 h-7 rounded-full bg-surface-2 hover:bg-surface-3 border border-line flex items-center justify-center text-fg-3 hover:text-fg transition cursor-pointer"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Scrollable Preview & Settings Area -->
      <div class="p-5 overflow-y-auto space-y-4 flex-1">
        <!-- Interactive Controls Bar -->
        <div class="p-3.5 rounded-2xl bg-surface/80 border border-line space-y-3">
          <div class="flex items-center justify-between text-xs font-semibold text-fg-3">
            <span class="flex items-center gap-1.5 text-accent">
              <Shield class="w-3.5 h-3.5" />
              安全分享选项 (Privacy Controls)
            </span>
            <span class="text-[11px] text-fg-4">截屏与转发不泄露敏感画面</span>
          </div>

          <!-- Toggles Grid -->
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            <!-- Blur Poster Toggle -->
            <label
              class="flex items-center gap-2.5 p-2 rounded-xl border cursor-pointer transition select-none"
              :class="blurPoster ? 'bg-amber-500/10 border-amber-500/40 text-amber-300' : 'bg-surface-2/60 border-line text-fg-2 hover:bg-surface-2'"
            >
              <input
                type="checkbox"
                v-model="blurPoster"
                class="rounded text-amber-500 focus:ring-amber-500"
              />
              <EyeOff v-if="blurPoster" class="w-3.5 h-3.5 text-amber-400 shrink-0" />
              <Eye v-else class="w-3.5 h-3.5 text-fg-4 shrink-0" />
              <div class="text-xs">
                <span class="font-medium">模糊海报封面</span>
                <span class="text-[10px] block opacity-70">防窥敏感画面</span>
              </div>
            </label>

            <!-- Blur Description Toggle -->
            <label
              class="flex items-center gap-2.5 p-2 rounded-xl border cursor-pointer transition select-none"
              :class="blurDescription ? 'bg-amber-500/10 border-amber-500/40 text-amber-300' : 'bg-surface-2/60 border-line text-fg-2 hover:bg-surface-2'"
            >
              <input
                type="checkbox"
                v-model="blurDescription"
                class="rounded text-amber-500 focus:ring-amber-500"
              />
              <Lock v-if="blurDescription" class="w-3.5 h-3.5 text-amber-400 shrink-0" />
              <Quote v-else class="w-3.5 h-3.5 text-fg-4 shrink-0" />
              <div class="text-xs">
                <span class="font-medium">模糊剧情介绍</span>
                <span class="text-[10px] block opacity-70">防剧透与文字脱敏</span>
              </div>
            </label>
          </div>

          <!-- Theme & Detail Toggles -->
          <div class="flex items-center justify-between pt-1 text-xs text-fg-3 border-t border-line/40 flex-wrap gap-2">
            <div class="flex items-center gap-2">
              <span class="text-[11px] text-fg-4">背景光晕:</span>
              <button
                type="button"
                @click="ambientTheme = 'vibrant'"
                :class="[
                  'px-2 py-0.5 rounded-md text-[11px] font-medium border transition cursor-pointer',
                  ambientTheme === 'vibrant' ? 'bg-accent-fill/20 border-accent text-accent' : 'bg-surface-2 border-line text-fg-4'
                ]"
              >流光</button>
              <button
                type="button"
                @click="ambientTheme = 'dark'"
                :class="[
                  'px-2 py-0.5 rounded-md text-[11px] font-medium border transition cursor-pointer',
                  ambientTheme === 'dark' ? 'bg-accent-fill/20 border-accent text-accent' : 'bg-surface-2 border-line text-fg-4'
                ]"
              >深黑</button>
              <button
                type="button"
                @click="ambientTheme = 'midnight'"
                :class="[
                  'px-2 py-0.5 rounded-md text-[11px] font-medium border transition cursor-pointer',
                  ambientTheme === 'midnight' ? 'bg-accent-fill/20 border-accent text-accent' : 'bg-surface-2 border-line text-fg-4'
                ]"
              >午夜</button>
            </div>

            <label class="flex items-center gap-1.5 cursor-pointer text-[11px] text-fg-4 hover:text-fg">
              <input
                type="checkbox"
                v-model="includeDescription"
                class="rounded text-accent focus:ring-accent"
              />
              <span>附带剧情概要</span>
            </label>
          </div>
        </div>

        <!-- Live Visual Card Preview (WYSIWYG) -->
        <div class="flex justify-center p-2">
          <div
            class="relative w-full max-w-[420px] rounded-3xl overflow-hidden border border-white/15 shadow-2xl p-6 flex flex-col items-center select-none"
            :style="{
              backgroundColor: ambientTheme === 'midnight' ? '#090d16' : '#0c0f17'
            }"
          >
            <!-- Ambient Poster Gaussian Blur Glow Layer -->
            <div
              v-if="data.posterUrl && ambientTheme !== 'dark'"
              class="absolute inset-0 bg-cover bg-center pointer-events-none scale-125 transition-all duration-300"
              :style="{
                backgroundImage: `url(${data.posterUrl})`,
                filter: 'blur(45px) saturate(180%)',
                opacity: ambientTheme === 'vibrant' ? 0.45 : 0.25
              }"
            ></div>

            <!-- Overlay Tint -->
            <div
              class="absolute inset-0 pointer-events-none"
              :style="{
                background: ambientTheme === 'midnight'
                  ? 'linear-gradient(180deg, rgba(10, 16, 30, 0.6) 0%, rgba(5, 8, 15, 0.96) 100%)'
                  : ambientTheme === 'dark'
                    ? 'linear-gradient(180deg, rgba(18, 20, 26, 0.9) 0%, rgba(10, 11, 15, 0.98) 100%)'
                    : 'linear-gradient(180deg, rgba(15, 18, 26, 0.5) 0%, rgba(8, 10, 16, 0.94) 100%)'
              }"
            ></div>

            <!-- Foreground Content -->
            <div class="relative z-10 w-full flex flex-col items-center">
              <!-- Header Bar -->
              <div class="w-full flex items-center justify-between mb-4">
                <span class="px-2.5 py-1 rounded-lg bg-amber-500/15 border border-amber-500/30 text-amber-400 font-bold text-[10px] tracking-wide flex items-center gap-1 shadow-sm">
                  <Film class="w-3 h-3" />
                  GPDb · 影视档案
                </span>
                <span class="px-2 py-0.5 rounded-lg bg-white/10 border border-white/15 text-white/80 text-[10px] font-medium font-mono">
                  {{ data.category || (data.type === 'episode' ? '分集剧情' : '电影档案') }}
                </span>
              </div>

              <!-- Poster Presentation with Ambient Glow & Privacy Blur -->
              <div class="relative mb-5 group">
                <!-- Drop glow -->
                <div
                  v-if="data.posterUrl"
                  class="absolute -inset-2 bg-cover bg-center rounded-2xl blur-lg opacity-40 pointer-events-none"
                  :style="{ backgroundImage: `url(${data.posterUrl})` }"
                ></div>

                <div class="relative w-44 aspect-[3/4.2] rounded-2xl overflow-hidden border border-white/20 shadow-2xl bg-black/40">
                  <img
                    v-if="data.posterUrl"
                    :src="data.posterUrl"
                    :alt="data.title"
                    class="w-full h-full object-cover transition-all duration-300"
                    :style="{
                      filter: blurPoster ? 'blur(22px) brightness(0.85)' : 'none'
                    }"
                  />
                  <div v-else class="w-full h-full flex flex-col items-center justify-center text-white/40">
                    <Film class="w-8 h-8 mb-1 stroke-1" />
                    <span class="text-[10px]">无封面</span>
                  </div>

                  <!-- Blur Badge Indicator -->
                  <div
                    v-if="blurPoster"
                    class="absolute inset-0 flex flex-col items-center justify-center bg-black/40 backdrop-blur-xs text-white"
                  >
                    <Lock class="w-5 h-5 mb-1 text-amber-400" />
                    <span class="text-[11px] font-bold tracking-wider">海报隐私已隐藏</span>
                  </div>
                </div>
              </div>

              <!-- Title & Meta -->
              <div class="text-center w-full space-y-1 mb-3">
                <h3 class="text-lg font-extrabold text-white tracking-tight line-clamp-1">
                  {{ data.title }}
                </h3>
                <p v-if="data.titleAlt && data.titleAlt !== data.title" class="text-xs text-white/60 line-clamp-1 font-normal">
                  {{ data.titleAlt }}
                </p>

                <!-- Metadata Row -->
                <div class="flex items-center justify-center gap-2 text-[11px] text-amber-400/90 font-medium flex-wrap pt-1">
                  <span v-if="data.releaseDate">{{ data.releaseDate }}</span>
                  <span v-if="data.durationMins">· {{ data.durationMins }}分钟</span>
                  <span v-if="data.studioName">· {{ data.studioName }}</span>
                  <span v-if="data.episodeHeading">· {{ data.episodeHeading }}</span>
                </div>

                <!-- Cast -->
                <p v-if="data.performers && data.performers.length > 0" class="text-[10px] text-white/60 line-clamp-1 pt-0.5">
                  主演: {{ data.performers.slice(0, 4).join('、') }}
                </p>
              </div>

              <!-- Synopsis Card -->
              <div
                v-if="includeDescription && displayDescription"
                class="w-full p-3 rounded-2xl bg-white/[0.06] border border-white/10 text-left relative overflow-hidden mb-3"
              >
                <p
                  class="text-[11px] text-white/80 leading-relaxed line-clamp-4 transition-all duration-300"
                  :style="{
                    filter: blurDescription ? 'blur(6px)' : 'none',
                    userSelect: blurDescription ? 'none' : 'auto'
                  }"
                >
                  {{ displayDescription }}
                </p>

                <div
                  v-if="blurDescription"
                  class="absolute inset-0 flex items-center justify-center bg-black/40 text-white font-bold text-[10px] tracking-wider"
                >
                  <Lock class="w-3.5 h-3.5 mr-1 text-amber-400" />
                  <span>剧情概要已脱敏隐藏</span>
                </div>
              </div>

              <!-- Card Footer Watermark -->
              <div class="w-full pt-3 border-t border-white/10 flex items-center justify-between text-[9px] text-white/40">
                <span>GPDb Offline Library · 本地私有档案</span>
                <span>{{ formattedDate }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Modal Bottom Actions Bar -->
      <div class="p-4 border-t border-line bg-surface/80 backdrop-blur-md flex items-center justify-between gap-3 shrink-0">
        <button
          type="button"
          @click="handleClose"
          class="px-4 py-2 rounded-xl text-xs font-medium text-fg-3 hover:text-fg hover:bg-surface-2 transition cursor-pointer"
        >
          取消
        </button>

        <div class="flex items-center gap-2.5">
          <!-- Save Image Button -->
          <button
            type="button"
            @click="saveCardImage"
            :disabled="isSaving"
            class="px-4 py-2 rounded-xl bg-surface-2 hover:bg-surface-3 border border-line-strong text-xs font-semibold text-fg flex items-center gap-1.5 transition cursor-pointer disabled:opacity-60"
            title="保存卡片为高清 PNG 文件"
          >
            <Check v-if="isSaved" class="w-3.5 h-3.5 text-success" />
            <Download v-else class="w-3.5 h-3.5" />
            <span>{{ isSaved ? '已保存至下载！' : '保存图片' }}</span>
          </button>

          <!-- Copy to Clipboard Button -->
          <button
            type="button"
            @click="copyCardImage"
            :disabled="isCopying"
            class="px-5 py-2 rounded-xl bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600 text-white shadow-lg shadow-orange-500/20 text-xs font-bold flex items-center gap-1.5 transition cursor-pointer active:scale-98 disabled:opacity-60"
            title="直接将卡片复制为图片，可在社交应用中 Ctrl+V 粘贴"
          >
            <Check v-if="isCopied" class="w-3.5 h-3.5 text-white" />
            <Copy v-else class="w-3.5 h-3.5" />
            <span>{{ isCopied ? '已复制到剪贴板！' : '复制卡片图片' }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
