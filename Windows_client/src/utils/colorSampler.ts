/**
 * Image Edge Color & Brand Accent Sampler
 *
 * Samples the 12 edge probe pixels (with 4% anti-aliasing inset) and center 60% grid
 * of an image to detect if it has a solid background (e.g. white/black JPEG) or is
 * transparent (PNG/SVG).
 *
 * Key features:
 * 1. Millisecond 12-point edge probe with 4% inset to avoid anti-aliasing / edge filter noise.
 * 2. Extremes clamping: luminance < 20 clamped to OLED pure black (#000000), luminance > 240
 *    clamped to pure crisp white (#ffffff), eliminating muddy off-gray halos.
 * 3. Transparent PNG detection with subtle brand accent tinting (12% opacity) for rich depth.
 * 4. High-saturation brand accent color extraction from center 60% grid for elegant micro-glow borders.
 */

export interface SampledColorResult {
  /** CSS background-color string if solid color detected, or subtle tint for transparent, or null if non-uniform */
  bgColor: string | null;
  /** True if sampled color is dark (luminance < 0.55) */
  isDark: boolean;
  /** True if transparent alpha detected on edges */
  isTransparent: boolean;
  /** Extracted vibrant brand accent color if available */
  accentColor: string | null;
  /** Border color with dynamic brand glow or contrast outline */
  borderColor: string | null;
  /** Ready-to-use CSS style object for container */
  containerStyle: Record<string, string>;
}

// In-memory cache to prevent redundant canvas operations
const colorCache = new Map<string, SampledColorResult>();

/** Helper to convert RGB [0..255] to HSV [0..1, 0..1, 0..1] */
function rgbToHsv(r: number, g: number, b: number): [number, number, number] {
  r /= 255;
  g /= 255;
  b /= 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  let h = 0;
  const s = max === 0 ? 0 : d / max;
  const v = max;

  if (max !== min) {
    switch (max) {
      case r:
        h = (g - b) / d + (g < b ? 6 : 0);
        break;
      case g:
        h = (b - r) / d + 2;
        break;
      case b:
        h = (r - g) / d + 4;
        break;
    }
    h /= 6;
  }
  return [h, s, v];
}

/**
 * Samples a 5x5 grid in the central 60% of the image to extract the highest-saturation
 * brand accent color, ignoring pixels too close to the background color or grayscale.
 */
function extractBrandAccentColor(
  imgData: Uint8ClampedArray,
  w: number,
  h: number,
  ignoreRgb: [number, number, number] | null
): { rgb: [number, number, number]; css: string } | null {
  let bestSaturation = 0.28; // Filter out grayscale/muted tones
  let bestRgb: [number, number, number] | null = null;

  const stepX = Math.max(1, Math.floor((w * 0.6) / 5));
  const stepY = Math.max(1, Math.floor((h * 0.6) / 5));
  const startX = Math.floor(w * 0.2);
  const startY = Math.floor(h * 0.2);

  for (let ix = 0; ix < 5; ix++) {
    for (let iy = 0; iy < 5; iy++) {
      const px = Math.min(w - 1, startX + ix * stepX);
      const py = Math.min(h - 1, startY + iy * stepY);
      const idx = (py * w + px) * 4;
      const r = imgData[idx];
      const g = imgData[idx + 1];
      const b = imgData[idx + 2];
      const a = imgData[idx + 3];

      // Skip transparent or semi-transparent pixels
      if (a < 64) continue;

      // Skip pixels too close to the background color (Euclidean distance squared < 1500)
      if (ignoreRgb) {
        const dr = r - ignoreRgb[0];
        const dg = g - ignoreRgb[1];
        const db = b - ignoreRgb[2];
        const distSq = dr * dr + dg * dg + db * db;
        if (distSq < 1500) continue;
      }

      const [, sat, val] = rgbToHsv(r, g, b);
      if (sat > bestSaturation && val >= 0.25 && val <= 0.98) {
        bestSaturation = sat;
        bestRgb = [r, g, b];
      }
    }
  }

  if (!bestRgb) return null;
  return {
    rgb: bestRgb,
    css: `rgb(${bestRgb[0]}, ${bestRgb[1]}, ${bestRgb[2]})`,
  };
}

export function sampleImageEdgeColor(img: HTMLImageElement | null | undefined): SampledColorResult {
  if (!img) {
    return {
      bgColor: null,
      isDark: true,
      isTransparent: true,
      accentColor: null,
      borderColor: null,
      containerStyle: {},
    };
  }

  const src = img.currentSrc || img.src;
  if (!src) {
    return {
      bgColor: null,
      isDark: true,
      isTransparent: true,
      accentColor: null,
      borderColor: null,
      containerStyle: {},
    };
  }

  if (colorCache.has(src)) {
    return colorCache.get(src)!;
  }

  try {
    const canvas = document.createElement('canvas');
    // Scale down to max 80x80 for ultra-fast sampling (< 0.2ms) with crisp internal grid
    const w = (canvas.width = Math.min(img.naturalWidth || 80, 80));
    const h = (canvas.height = Math.min(img.naturalHeight || 80, 80));
    if (w <= 0 || h <= 0) {
      const fallback: SampledColorResult = {
        bgColor: null,
        isDark: true,
        isTransparent: true,
        accentColor: null,
        borderColor: null,
        containerStyle: {},
      };
      return fallback;
    }

    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) throw new Error('no canvas 2d context');

    ctx.drawImage(img, 0, 0, w, h);

    // Fetch the entire image buffer in ONE single readback
    const imgData = ctx.getImageData(0, 0, w, h).data;

    const getPixel = (x: number, y: number): [number, number, number, number] => {
      const idx = (y * w + x) * 4;
      return [imgData[idx], imgData[idx + 1], imgData[idx + 2], imgData[idx + 3]];
    };

    // 12 edge probe points:
    // 4 corners, 4 4%-insets (to bypass anti-aliasing / edge compression noise), 4 edge midpoints
    const insetX = Math.max(1, Math.round(w * 0.04));
    const insetY = Math.max(1, Math.round(h * 0.04));

    const points: [number, number][] = [
      // 4 corners
      [0, 0],
      [w - 1, 0],
      [0, h - 1],
      [w - 1, h - 1],
      // 4 4%-inset points
      [insetX, insetY],
      [w - 1 - insetX, insetY],
      [insetX, h - 1 - insetY],
      [w - 1 - insetX, h - 1 - insetY],
      // 4 edge midpoints
      [Math.floor(w / 2), 0],
      [Math.floor(w / 2), h - 1],
      [0, Math.floor(h / 2)],
      [w - 1, Math.floor(h / 2)],
    ];

    let transparentCount = 0;
    const colors: [number, number, number][] = [];

    for (const [px, py] of points) {
      const [r, g, b, a] = getPixel(px, py);
      if (a < 36) {
        transparentCount++;
      } else {
        colors.push([r, g, b]);
      }
    }

    // 1. Transparent PNG / SVG (6 or more probe points transparent, or no opaque edge points)
    if (transparentCount >= 6 || colors.length === 0) {
      const accent = extractBrandAccentColor(imgData, w, h, null);
      let containerStyle: Record<string, string> = {};
      let bgColor: string | null = null;
      let borderColor: string | null = null;

      if (accent) {
        const [ar, ag, ab] = accent.rgb;
        bgColor = `rgba(${ar}, ${ag}, ${ab}, 0.12)`;
        borderColor = `rgba(${ar}, ${ag}, ${ab}, 0.35)`;
        containerStyle = {
          backgroundColor: bgColor,
          borderColor,
          boxShadow: `0 4px 16px rgba(0, 0, 0, 0.2), 0 0 10px rgba(${ar}, ${ag}, ${ab}, 0.15)`,
        };
      }

      const res: SampledColorResult = {
        bgColor,
        isDark: true,
        isTransparent: true,
        accentColor: accent?.css ?? null,
        borderColor,
        containerStyle,
      };
      colorCache.set(src, res);
      return res;
    }

    // 2. Solid/near-solid edge: compute average edge RGB
    let sumR = 0, sumG = 0, sumB = 0;
    for (const [r, g, b] of colors) {
      sumR += r;
      sumG += g;
      sumB += b;
    }
    const avgR = Math.round(sumR / colors.length);
    const avgG = Math.round(sumG / colors.length);
    const avgB = Math.round(sumB / colors.length);

    // Check color consistency: ensure edge pixels are relatively uniform
    let maxDiff = 0;
    for (const [r, g, b] of colors) {
      const diff = Math.abs(r - avgR) + Math.abs(g - avgG) + Math.abs(b - avgB);
      if (diff > maxDiff) maxDiff = diff;
    }

    if (maxDiff <= 95) {
      const luminance = 0.299 * avgR + 0.587 * avgG + 0.114 * avgB;
      const isDark = luminance < 140; // < 0.55 * 255
      const isLight = luminance > 180;

      // Smart extremes clamping: clamp near-black (< 20) to pure OLED black,
      // and near-white (> 240) to pure crisp white to eliminate muddy gray borders
      let finalBgColor: string;
      let bgRgbForAccent: [number, number, number];

      if (luminance < 20) {
        finalBgColor = '#000000';
        bgRgbForAccent = [0, 0, 0];
      } else if (luminance > 240) {
        finalBgColor = '#ffffff';
        bgRgbForAccent = [255, 255, 255];
      } else {
        finalBgColor = `rgb(${avgR}, ${avgG}, ${avgB})`;
        bgRgbForAccent = [avgR, avgG, avgB];
      }

      // Extract core brand accent color from the center 60%
      const accent = extractBrandAccentColor(imgData, w, h, bgRgbForAccent);

      let borderColor: string;
      let boxShadow: string;

      if (accent) {
        const [ar, ag, ab] = accent.rgb;
        const alpha = isLight ? 0.35 : 0.45;
        borderColor = `rgba(${ar}, ${ag}, ${ab}, ${alpha})`;
        boxShadow = isDark
          ? `0 6px 20px rgba(0, 0, 0, 0.4), 0 0 12px rgba(${ar}, ${ag}, ${ab}, 0.2)`
          : `0 6px 20px rgba(0, 0, 0, 0.12), 0 0 10px rgba(${ar}, ${ag}, ${ab}, 0.15)`;
      } else {
        borderColor = isDark ? 'rgba(255, 255, 255, 0.16)' : 'rgba(0, 0, 0, 0.12)';
        boxShadow = isDark ? '0 6px 20px rgba(0, 0, 0, 0.4)' : '0 6px 20px rgba(0, 0, 0, 0.12)';
      }

      const containerStyle: Record<string, string> = {
        backgroundColor: finalBgColor,
        borderColor,
        boxShadow,
      };

      const res: SampledColorResult = {
        bgColor: finalBgColor,
        isDark,
        isTransparent: false,
        accentColor: accent?.css ?? null,
        borderColor,
        containerStyle,
      };
      colorCache.set(src, res);
      return res;
    }

    // 3. Edge colors vary significantly (e.g. photo edge / varied background)
    // Extract accent color for border anyway if possible
    const accent = extractBrandAccentColor(imgData, w, h, null);
    let borderColor: string | null = null;
    let containerStyle: Record<string, string> = {};

    if (accent) {
      const [ar, ag, ab] = accent.rgb;
      borderColor = `rgba(${ar}, ${ag}, ${ab}, 0.35)`;
      containerStyle = { borderColor };
    }

    const fallback: SampledColorResult = {
      bgColor: null,
      isDark: true,
      isTransparent: false,
      accentColor: accent?.css ?? null,
      borderColor,
      containerStyle,
    };
    colorCache.set(src, fallback);
    return fallback;
  } catch {
    // If canvas is tainted or throws
    const fallback: SampledColorResult = {
      bgColor: null,
      isDark: true,
      isTransparent: true,
      accentColor: null,
      borderColor: null,
      containerStyle: {},
    };
    colorCache.set(src, fallback);
    return fallback;
  }
}
