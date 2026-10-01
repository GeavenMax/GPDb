<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import {
  X, Share2, Copy, Download, Check, Shield, Eye, EyeOff,
  Film, Quote, Lock, Clapperboard
} from '@lucide/vue';
import { claimEscape } from '../utils/escape';
import { api, IS_TAURI } from '../api';

export interface ShareCardData {
  type: 'movie' | 'episode';
  title: string;
  titleAlt?: string | null;
  posterUrl: string;
  coverBackUrl?: string | null;
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

// Privacy & Customization toggles (Self-contained, independent of global privacy mode)
const blurPoster = ref(false);
const blurDescription = ref(false);
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

const itemTag = computed(() => {
  if (!props.data) return '';
  return props.data.type === 'episode' ? `#EP-${props.data.id}` : `#MOV-${props.data.id}`;
});

// 27x27 Verified Binary Matrix for https://t.me/gpdbnews
const TG_QR_MATRIX = [
  "000000000000000000000000000",
  "011111110111100100011111110",
  "010000010101000000010000010",
  "010111010111001001010111010",
  "010111010001001001010111010",
  "010111010101100101010111010",
  "010000010000100101010000010",
  "011111110101010101011111110",
  "000000000010101001000000000",
  "010011111101100111100101110",
  "011101101010000010000111100",
  "010010110110101010000110010",
  "010101100101110101101011110",
  "000010110110010010010000010",
  "010111101010001011000100100",
  "011000110000110010100111110",
  "010011101000100010111011010",
  "010010011001000111111101100",
  "000000000101111001000101100",
  "011111110100100001010100010",
  "010000010111010111000100110",
  "010111010111011111111100110",
  "010111010110000110110000110",
  "010111010001010101100111110",
  "010000010011000100011101110",
  "011111110110001111010010010",
  "000000000000000000000000000"
];

const tgQrSvgPath = computed(() => {
  let path = '';
  for (let r = 0; r < 27; r++) {
    for (let c = 0; c < 27; c++) {
      if (TG_QR_MATRIX[r][c] === '1') {
        path += `M${c},${r}h1v1h-1z `;
      }
    }
  }
  return path;
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
 * Aspect-ratio preserving drawImage cover helper (No distortion or stretching)
 */
function drawImageCover(
  ctx: CanvasRenderingContext2D,
  img: HTMLImageElement,
  dx: number,
  dy: number,
  dw: number,
  dh: number
) {
  const imgW = img.naturalWidth || img.width;
  const imgH = img.naturalHeight || img.height;
  if (!imgW || !imgH) return;

  const imgRatio = imgW / imgH;
  const targetRatio = dw / dh;

  let sx = 0, sy = 0, sw = imgW, sh = imgH;
  if (imgRatio > targetRatio) {
    sw = imgH * targetRatio;
    sx = (imgW - sw) / 2;
  } else {
    sh = imgW / targetRatio;
    sy = (imgH - sh) / 2;
  }
  ctx.drawImage(img, sx, sy, sw, sh, dx, dy, dw, dh);
}

/**
 * Hardware-agnostic bilinear downsample blur algorithm
 * 100% reliable across macOS WKWebView, Windows WebView2, and Canvas 2D implementations
 */
function drawBlurredImageCover(
  ctx: CanvasRenderingContext2D,
  img: HTMLImageElement,
  dx: number,
  dy: number,
  dw: number,
  dh: number
) {
  const off = document.createElement('canvas');
  const smallW = Math.max(16, Math.round(dw / 16));
  const smallH = Math.max(16, Math.round(dh / 16));
  off.width = smallW;
  off.height = smallH;
  const offCtx = off.getContext('2d');
  if (offCtx) {
    offCtx.imageSmoothingEnabled = true;
    drawImageCover(offCtx, img, 0, 0, smallW, smallH);

    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(off, 0, 0, smallW, smallH, dx, dy, dw, dh);
    ctx.restore();
  } else {
    drawImageCover(ctx, img, dx, dy, dw, dh);
  }
}

/**
 * High-definition Canvas Render Engine
 * Generates an ultra-crisp 2x Retina PNG card with sampled ambient blur,
 * custom glassmorphism, dynamic height adaptation, and privacy controls.
 */
async function renderCardToCanvas(): Promise<HTMLCanvasElement | null> {
  if (!props.data) return null;

  const scale = 2; // 2x Retina resolution
  const width = 640;
  const padX = 36;
  const isEpisode = props.data.type === 'episode';
  const hasBackCover = Boolean(!isEpisode && props.data.coverBackUrl);

  // 1. Poster Dimensions (16:9 widescreen for episodes, dual covers or 3:4 portrait for movies)
  const posterW = isEpisode ? 440 : (hasBackCover ? 180 : 210);
  const posterH = isEpisode ? 248 : (hasBackCover ? 245 : 285);
  const dualGap = 16;
  const dualTotalW = posterW * 2 + dualGap;
  const posterX = hasBackCover ? (width - dualTotalW) / 2 : (width - posterW) / 2;
  const backCoverX = posterX + posterW + dualGap;

  // 2. Pre-measure Text Blocks to Calculate Exact Dynamic Canvas Height
  const tempCanvas = document.createElement('canvas');
  const mCtx = tempCanvas.getContext('2d');
  if (!mCtx) return null;

  // Pre-calculate Title lines
  mCtx.font = 'bold 22px "Microsoft YaHei UI", sans-serif';
  const rawTitle = props.data.title;
  const maxTitleW = width - padX * 2;
  const titleLines: string[] = [];
  let curTLine = '';
  for (let i = 0; i < rawTitle.length; i++) {
    const ch = rawTitle[i];
    const test = curTLine + ch;
    if (mCtx.measureText(test).width > maxTitleW && curTLine.length > 0) {
      titleLines.push(curTLine);
      curTLine = ch;
    } else {
      curTLine = test;
    }
  }
  if (curTLine) titleLines.push(curTLine);
  const titleBlockH = titleLines.length * 28;

  // Pre-calculate Alt Title
  const hasAlt = Boolean(props.data.titleAlt && props.data.titleAlt !== props.data.title);
  const altH = hasAlt ? 24 : 0;

  // Pre-calculate Meta badges
  const metaItems: string[] = [];
  if (props.data.releaseDate) metaItems.push(`📅 ${props.data.releaseDate}`);
  if (props.data.durationMins) metaItems.push(`⏱ ${props.data.durationMins} 分钟`);
  if (props.data.studioName) metaItems.push(`🏢 ${props.data.studioName}`);
  if (props.data.episodeHeading) metaItems.push(props.data.episodeHeading);
  const metaH = metaItems.length > 0 ? 26 : 0;

  // Pre-calculate Director line
  const hasDirector = Boolean(props.data.directorName);
  const directorH = hasDirector ? 22 : 0;

  // Pre-calculate Full Cast wrapping lines
  mCtx.font = '12px "Microsoft YaHei UI", sans-serif';
  const performers = props.data.performers || [];
  const castLines: string[] = [];
  if (performers.length > 0) {
    let curCast = '主演: ';
    const maxCastW = width - padX * 2;
    for (let i = 0; i < performers.length; i++) {
      const name = performers[i];
      const candidate = curCast === '主演: ' ? `主演: ${name}` : `${curCast}、${name}`;
      if (mCtx.measureText(candidate).width > maxCastW && curCast !== '主演: ') {
        castLines.push(curCast);
        curCast = `     ${name}`;
      } else {
        curCast = candidate;
      }
    }
    if (curCast) castLines.push(curCast);
  }
  const castBlockH = castLines.length > 0 ? castLines.length * 20 + 8 : 0;

  // Pre-calculate Description wrapping lines (Complete synopsis, adaptive height, no line limit)
  const hasDesc = includeDescription.value && Boolean(displayDescription.value);
  const descPad = 16;
  const descBoxW = width - padX * 2;
  const descLines: string[] = [];
  if (hasDesc) {
    mCtx.font = '12px "Microsoft YaHei UI", sans-serif';
    const words = displayDescription.value;
    const maxDescLineW = descBoxW - descPad * 2;
    let curDLine = '';
    for (let i = 0; i < words.length; i++) {
      const ch = words[i];
      const test = curDLine + ch;
      if (mCtx.measureText(test).width > maxDescLineW && curDLine.length > 0) {
        descLines.push(curDLine);
        curDLine = ch;
      } else {
        curDLine = test;
      }
    }
    if (curDLine) descLines.push(curDLine);
  }
  const descBoxH = hasDesc ? descPad * 2 + descLines.length * 20 : 0;

  // 3. Compute Total Adaptive Height
  const headerY = 42;
  const headerH = 48;
  const posterSpacing = 24;
  const footerH = 88;
  const height = headerY + headerH + posterH + posterSpacing + titleBlockH + altH + metaH + directorH + castBlockH + (hasDesc ? descBoxH + 20 : 12) + footerH;

  // 4. Create Main 2x Canvas
  const canvas = document.createElement('canvas');
  canvas.width = width * scale;
  canvas.height = height * scale;
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;

  ctx.scale(scale, scale);

  // 5. Base Background (Default Vibrant Theme)
  ctx.fillStyle = '#0c0f17';
  ctx.fillRect(0, 0, width, height);

  // 6. Load Poster Image(s) with 4s Safety Timeout
  let posterImg: HTMLImageElement | null = null;
  let backCoverImg: HTMLImageElement | null = null;

  const loadImgSafe = (url: string): Promise<HTMLImageElement | null> => {
    return new Promise((resolve) => {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.src = url;
      if (img.complete && (img.naturalWidth || img.width) > 0) return resolve(img);
      const timer = setTimeout(() => resolve(null), 4000);
      img.onload = () => { clearTimeout(timer); resolve(img); };
      img.onerror = () => { clearTimeout(timer); resolve(null); };
    });
  };

  if (props.data.posterUrl) {
    posterImg = await loadImgSafe(props.data.posterUrl);
  }
  if (hasBackCover && props.data.coverBackUrl) {
    backCoverImg = await loadImgSafe(props.data.coverBackUrl);
  }

  // 7. Draw Ambient Streamer Background (Sampled from Poster)
  const ambientImg = posterImg || backCoverImg;
  if (ambientImg && ambientImg.width > 0) {
    drawBlurredImageCover(ctx, ambientImg, -50, -50, width + 100, height + 100);
    ctx.save();
    ctx.fillStyle = 'rgba(10, 12, 18, 0.75)';
    ctx.fillRect(0, 0, width, height);
    ctx.restore();
  }

  // Gradient Tint Layer (Streamer / 流光)
  const grad = ctx.createLinearGradient(0, 0, 0, height);
  grad.addColorStop(0, 'rgba(15, 18, 26, 0.45)');
  grad.addColorStop(0.35, 'rgba(12, 15, 23, 0.85)');
  grad.addColorStop(1, 'rgba(8, 10, 16, 0.96)');
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, width, height);

  // Outer Card Rim
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
  ctx.lineWidth = 1;
  ctx.strokeRect(12, 12, width - 24, height - 24);

  // 8. Card Header (Branding & Type Badge)
  let curY = headerY;

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
  ctx.fillText(isEpisode ? 'GPDb · 分集剧照档案' : 'GPDb · 影视私有档案', padX + 14, curY + 18);
  ctx.restore();

  // Category Badge
  const rightTag = props.data.category || (isEpisode ? '分集剧情' : '电影档案');
  ctx.save();
  ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.roundRect(width - padX - 110, curY, 110, 28, 8);
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = 'rgba(255, 255, 255, 0.8)';
  ctx.font = '500 11px "Microsoft YaHei UI", sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText(rightTag, width - padX - 55, curY + 18);
  ctx.restore();

  curY += headerH;

  // 9. Poster / Thumbnail Section (Aspect-Ratio Aware)
  const posterY = curY;

  // Subtle ambient glow behind poster(s)
  if (posterImg && posterImg.width > 0) {
    ctx.save();
    ctx.globalAlpha = 0.35;
    if (hasBackCover) {
      drawBlurredImageCover(ctx, posterImg, posterX - 6, posterY - 6, dualTotalW + 12, posterH + 12);
    } else {
      drawBlurredImageCover(ctx, posterImg, posterX - 6, posterY - 6, posterW + 12, posterH + 12);
    }
    ctx.restore();
  }

  function drawCardCover(
    img: HTMLImageElement | null,
    cx: number,
    cy: number,
    cw: number,
    ch: number,
    tagLabel?: string
  ) {
    if (!ctx) return;
    ctx.save();
    ctx.beginPath();
    ctx.roundRect(cx, cy, cw, ch, 16);
    ctx.clip();

    if (img && img.width > 0) {
      if (blurPoster.value) {
        drawBlurredImageCover(ctx, img, cx, cy, cw, ch);
        ctx.fillStyle = 'rgba(0, 0, 0, 0.52)';
        ctx.fillRect(cx, cy, cw, ch);
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 12px "Microsoft YaHei UI", sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('🔒 已防窥脱敏', cx + cw / 2, cy + ch / 2);
      } else {
        drawImageCover(ctx, img, cx, cy, cw, ch);
      }
    } else {
      ctx.fillStyle = '#1c212c';
      ctx.fillRect(cx, cy, cw, ch);
      ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
      ctx.font = '12px "Microsoft YaHei UI", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(tagLabel ? `暂无${tagLabel}` : '暂无海报', cx + cw / 2, cy + ch / 2);
    }

    if (tagLabel && !blurPoster.value) {
      ctx.fillStyle = 'rgba(0, 0, 0, 0.65)';
      ctx.beginPath();
      ctx.roundRect(cx + 8, cy + 8, 38, 20, 6);
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 10px "Microsoft YaHei UI", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(tagLabel, cx + 8 + 19, cy + 8 + 14);
    }
    ctx.restore();

    ctx.save();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.roundRect(cx, cy, cw, ch, 16);
    ctx.stroke();
    ctx.restore();
  }

  if (hasBackCover) {
    drawCardCover(posterImg, posterX, posterY, posterW, posterH, '封面');
    drawCardCover(backCoverImg, backCoverX, posterY, posterW, posterH, '封底');
  } else {
    drawCardCover(posterImg, posterX, posterY, posterW, posterH);
  }

  curY += posterH + posterSpacing;

  // 10. Title & Meta
  ctx.textAlign = 'center';

  // Primary Title (Multi-line aware)
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 22px "Microsoft YaHei UI", sans-serif';
  for (let t = 0; t < titleLines.length; t++) {
    ctx.fillText(titleLines[t], width / 2, curY);
    curY += 26;
  }

  // Alt Title
  if (hasAlt && props.data.titleAlt) {
    ctx.fillStyle = 'rgba(255, 255, 255, 0.55)';
    ctx.font = '13px "Microsoft YaHei UI", sans-serif';
    ctx.fillText(props.data.titleAlt, width / 2, curY);
    curY += 22;
  }

  // Meta Badges Row
  if (metaItems.length > 0) {
    curY += 4;
    ctx.fillStyle = 'rgba(245, 158, 11, 0.95)';
    ctx.font = '500 12px "Microsoft YaHei UI", sans-serif';
    ctx.fillText(metaItems.join('  ·  '), width / 2, curY);
    curY += 20;
  }

  // Director Row
  if (hasDirector) {
    curY += 2;
    ctx.fillStyle = 'rgba(252, 211, 77, 0.9)';
    ctx.font = '500 12px "Microsoft YaHei UI", sans-serif';
    ctx.fillText(`🎬 导演: ${props.data.directorName}`, width / 2, curY);
    curY += 20;
  }

  // Complete Cast Lines
  if (castLines.length > 0) {
    curY += 4;
    ctx.fillStyle = 'rgba(255, 255, 255, 0.72)';
    ctx.font = '12px "Microsoft YaHei UI", sans-serif';
    for (let c = 0; c < castLines.length; c++) {
      ctx.fillText(castLines[c], width / 2, curY);
      curY += 20;
    }
  }

  // 11. Synopsis Card Block (Complete & Adaptive)
  if (hasDesc) {
    curY += 8;
    const descX = padX;
    const descY = curY;

    // Glassmorphism Container for Description
    ctx.save();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.05)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.roundRect(descX, descY, descBoxW, descBoxH, 14);
    ctx.fill();
    ctx.stroke();

    ctx.textAlign = 'left';
    ctx.font = '12px "Microsoft YaHei UI", sans-serif';

    if (blurDescription.value) {
      // 100% Reliable frosted text redaction effect
      ctx.fillStyle = 'rgba(255, 255, 255, 0.16)';
      for (let j = 0; j < descLines.length; j++) {
        const textW = Math.min(descBoxW - descPad * 2, mCtx.measureText(descLines[j]).width);
        const pillY = descY + descPad + 4 + j * 20;
        ctx.beginPath();
        ctx.roundRect(descX + descPad, pillY, textW, 12, 6);
        ctx.fill();
      }

      // Privacy Badge over description
      ctx.fillStyle = 'rgba(0, 0, 0, 0.75)';
      ctx.strokeStyle = 'rgba(245, 158, 11, 0.4)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.roundRect(descX + descBoxW / 2 - 90, descY + descBoxH / 2 - 15, 180, 30, 8);
      ctx.fill();
      ctx.stroke();

      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 11px "Microsoft YaHei UI", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('🔒 剧情介绍已脱敏隐藏', descX + descBoxW / 2, descY + descBoxH / 2 + 5);
    } else {
      ctx.fillStyle = 'rgba(255, 255, 255, 0.78)';
      for (let j = 0; j < descLines.length; j++) {
        ctx.fillText(descLines[j], descX + descPad, descY + descPad + 14 + j * 20);
      }
    }
    ctx.restore();

    curY += descBoxH + 20;
  }

  // 12. Footer Watermark, TG Official QR Code & ID
  ctx.save();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(padX, height - 70);
  ctx.lineTo(width - padX, height - 70);
  ctx.stroke();

  // Left column: Typography
  ctx.textAlign = 'left';
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 11px "Microsoft YaHei UI", sans-serif';
  ctx.fillText('GPDb Offline Library · 本地私有影视库', padX, height - 48);

  ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
  ctx.font = '10px "Microsoft YaHei UI", sans-serif';
  const itemTagStr = isEpisode ? `#EP-${props.data.id}` : `#MOV-${props.data.id}`;
  ctx.fillText(`${itemTagStr}  ·  ${formattedDate.value}`, padX, height - 32);

  ctx.fillStyle = 'rgba(245, 158, 11, 0.9)';
  ctx.font = '500 10px "Microsoft YaHei UI", sans-serif';
  ctx.fillText('📢 官方频道: t.me/gpdbnews', padX, height - 16);

  // Right column: Telegram Channel QR Code in rounded white container
  const qrBoxSize = 52;
  const qrBoxX = width - padX - qrBoxSize;
  const qrBoxY = height - 63;

  ctx.fillStyle = '#ffffff';
  ctx.beginPath();
  ctx.roundRect(qrBoxX, qrBoxY, qrBoxSize, qrBoxSize, 6);
  ctx.fill();

  const qrPad = 3.5;
  const modSize = (qrBoxSize - qrPad * 2) / 27;
  ctx.fillStyle = '#000000';
  for (let r = 0; r < 27; r++) {
    for (let c = 0; c < 27; c++) {
      if (TG_QR_MATRIX[r][c] === '1') {
        ctx.fillRect(
          qrBoxX + qrPad + c * modSize,
          qrBoxY + qrPad + r * modSize,
          modSize + 0.1,
          modSize + 0.1
        );
      }
    }
  }
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

    // Native Tauri save
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
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-6 bg-scrim/80 backdrop-blur-md animate-fade-in no-privacy-blur share-card-container"
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
            <p class="text-[11px] text-fg-4">流光自适应渐变，支持敏感内容自主脱敏与全量信息完整呈现</p>
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
              安全分享选项 (自主脱敏控制)
            </span>
            <label class="flex items-center gap-1.5 cursor-pointer text-[11px] text-fg-3 hover:text-fg">
              <input
                type="checkbox"
                v-model="includeDescription"
                class="rounded text-accent focus:ring-accent"
              />
              <span>附带完整剧情简介</span>
            </label>
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
        </div>

        <!-- Live Visual Card Preview (WYSIWYG) -->
        <div class="flex justify-center p-2">
          <div
            class="relative w-full max-w-[420px] rounded-3xl overflow-hidden border border-white/15 shadow-2xl p-6 flex flex-col items-center select-none bg-[#0c0f17] no-privacy-blur share-card-container"
          >
            <!-- Ambient Poster Gaussian Blur Glow Layer (Streamer / 流光) -->
            <div
              v-if="data.posterUrl"
              class="absolute inset-0 bg-cover bg-center pointer-events-none scale-125 transition-all duration-300 opacity-40 blur-2xl"
              :style="{ backgroundImage: `url(${data.posterUrl})` }"
            ></div>

            <!-- Overlay Tint -->
            <div
              class="absolute inset-0 pointer-events-none bg-gradient-to-b from-black/45 via-[#0c0f17]/85 to-[#080a10]/95"
            ></div>

            <!-- Foreground Content -->
            <div class="relative z-10 w-full flex flex-col items-center">
              <!-- Header Bar -->
              <div class="w-full flex items-center justify-between mb-4">
                <span class="px-2.5 py-1 rounded-lg bg-amber-500/15 border border-amber-500/30 text-amber-400 font-bold text-[10px] tracking-wide flex items-center gap-1 shadow-sm">
                  <Film v-if="data.type !== 'episode'" class="w-3 h-3" />
                  <Clapperboard v-else class="w-3 h-3" />
                  {{ data.type === 'episode' ? 'GPDb · 分集剧照档案' : 'GPDb · 影视私有档案' }}
                </span>
                <span class="px-2 py-0.5 rounded-lg bg-white/10 border border-white/15 text-white/80 text-[10px] font-medium font-mono">
                  {{ data.category || (data.type === 'episode' ? '分集剧情' : '电影档案') }}
                </span>
              </div>

              <!-- Poster Presentation (Dual covers if coverBackUrl, 16:9 for episode, 3:4.2 for single movie) -->
              <div class="relative mb-5 group flex justify-center w-full">
                <!-- Dual Covers Layout (Movie with Back Cover) -->
                <div v-if="data.type !== 'episode' && data.coverBackUrl" class="flex items-center justify-center gap-3 w-full max-w-[390px]">
                  <!-- Front Cover -->
                  <div class="relative w-1/2 aspect-[3/4.1] rounded-2xl overflow-hidden border border-white/20 shadow-2xl bg-black/40">
                    <img
                      v-if="data.posterUrl"
                      :src="data.posterUrl"
                      :alt="data.title + ' (封面)'"
                      class="w-full h-full object-cover transition-all duration-300 no-privacy-blur"
                      :style="{
                        filter: blurPoster ? 'blur(22px) brightness(0.82)' : 'none'
                      }"
                    />
                    <div v-else class="w-full h-full flex flex-col items-center justify-center text-white/40">
                      <Film class="w-6 h-6 mb-1 stroke-1" />
                      <span class="text-[10px]">无封面</span>
                    </div>
                    <span v-if="!blurPoster" class="absolute top-2 left-2 px-1.5 py-0.5 rounded bg-black/60 backdrop-blur-xs text-[9px] font-semibold text-white/90 border border-white/10">封面</span>
                    <div
                      v-if="blurPoster"
                      class="absolute inset-0 flex flex-col items-center justify-center bg-black/40 backdrop-blur-xs text-white"
                    >
                      <Lock class="w-4 h-4 mb-0.5 text-amber-400" />
                      <span class="text-[10px] font-bold">已脱敏</span>
                    </div>
                  </div>

                  <!-- Back Cover -->
                  <div class="relative w-1/2 aspect-[3/4.1] rounded-2xl overflow-hidden border border-white/20 shadow-2xl bg-black/40">
                    <img
                      :src="data.coverBackUrl"
                      :alt="data.title + ' (封底)'"
                      class="w-full h-full object-cover transition-all duration-300 no-privacy-blur"
                      :style="{
                        filter: blurPoster ? 'blur(22px) brightness(0.82)' : 'none'
                      }"
                    />
                    <span v-if="!blurPoster" class="absolute top-2 left-2 px-1.5 py-0.5 rounded bg-black/60 backdrop-blur-xs text-[9px] font-semibold text-white/90 border border-white/10">封底</span>
                    <div
                      v-if="blurPoster"
                      class="absolute inset-0 flex flex-col items-center justify-center bg-black/40 backdrop-blur-xs text-white"
                    >
                      <Lock class="w-4 h-4 mb-0.5 text-amber-400" />
                      <span class="text-[10px] font-bold">已脱敏</span>
                    </div>
                  </div>
                </div>

                <!-- Single Poster (Episode or Movie without Back Cover) -->
                <div
                  v-else
                  class="relative rounded-2xl overflow-hidden border border-white/20 shadow-2xl bg-black/40 transition-all"
                  :class="data.type === 'episode' ? 'w-full max-w-[360px] aspect-video' : 'w-44 aspect-[3/4.2]'"
                >
                  <img
                    v-if="data.posterUrl"
                    :src="data.posterUrl"
                    :alt="data.title"
                    class="w-full h-full object-cover transition-all duration-300 no-privacy-blur"
                    :style="{
                      filter: blurPoster ? 'blur(22px) brightness(0.82)' : 'none'
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
                    <span class="text-[11px] font-bold tracking-wider">封面已安全防窥脱敏</span>
                  </div>
                </div>
              </div>

              <!-- Title & Meta -->
              <div class="text-center w-full space-y-1 mb-3">
                <h3 class="text-lg font-extrabold text-white tracking-tight leading-snug">
                  {{ data.title }}
                </h3>
                <p v-if="data.titleAlt && data.titleAlt !== data.title" class="text-xs text-white/60 font-normal">
                  {{ data.titleAlt }}
                </p>

                <!-- Metadata Row -->
                <div class="flex items-center justify-center gap-2 text-[11px] text-amber-400/90 font-medium flex-wrap pt-1">
                  <span v-if="data.releaseDate">{{ data.releaseDate }}</span>
                  <span v-if="data.durationMins">· {{ data.durationMins }}分钟</span>
                  <span v-if="data.studioName">· {{ data.studioName }}</span>
                  <span v-if="data.episodeHeading">· {{ data.episodeHeading }}</span>
                </div>

                <!-- Director -->
                <p v-if="data.directorName" class="text-[11px] text-amber-300/80 font-medium pt-0.5">
                  🎬 导演: {{ data.directorName }}
                </p>

                <!-- Complete Cast (No truncation) -->
                <p v-if="data.performers && data.performers.length > 0" class="text-[11px] text-white/70 pt-0.5 leading-relaxed">
                  主演: {{ data.performers.join('、') }}
                </p>
              </div>

              <!-- Synopsis Card (Adaptive full display, no line clamp) -->
              <div
                v-if="includeDescription && displayDescription"
                class="w-full p-3.5 rounded-2xl bg-white/[0.06] border border-white/10 text-left relative overflow-hidden mb-3"
              >
                <p
                  class="text-[11px] text-white/80 leading-relaxed transition-all duration-300"
                  :style="{
                    filter: blurDescription ? 'blur(6px)' : 'none',
                    userSelect: blurDescription ? 'none' : 'auto'
                  }"
                >
                  {{ displayDescription }}
                </p>

                <div
                  v-if="blurDescription"
                  class="absolute inset-0 flex items-center justify-center bg-black/45 text-white font-bold text-[10px] tracking-wider"
                >
                  <Lock class="w-3.5 h-3.5 mr-1 text-amber-400" />
                  <span>剧情概要已脱敏隐藏</span>
                </div>
              </div>

              <!-- Card Footer Watermark & Official TG Channel QR -->
              <div class="w-full pt-3.5 border-t border-white/10 flex items-center justify-between text-[10px] text-white/50">
                <div class="space-y-0.5 text-left">
                  <div class="text-white/85 font-semibold text-[11px]">GPDb Offline Library · 本地私有档案</div>
                  <div class="text-[9px] text-white/40">{{ itemTag }} · {{ formattedDate }}</div>
                  <div class="text-[9px] text-amber-400 font-mono flex items-center gap-1 pt-0.5">
                    <span>📢 官方频道: t.me/gpdbnews</span>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <div class="p-1 bg-white rounded-lg shadow-md flex items-center justify-center shrink-0" title="扫描二维码关注 Telegram 官方频道">
                    <svg class="w-10 h-10" viewBox="0 0 27 27" fill="none">
                      <path :d="tgQrSvgPath" fill="#000000" />
                    </svg>
                  </div>
                </div>
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
            title="直接将卡片复制为图片，可在社交应用中粘贴"
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
