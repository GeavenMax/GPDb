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

    // 16 edge probe points: 4 corners, 2 on each edge, 4 3%-insets
    const insetX = Math.max(1, Math.round(w * 0.03));
    const insetY = Math.max(1, Math.round(h * 0.03));

    const points: [number, number][] = [
      // 4 corners
      [0, 0],
      [w - 1, 0],
      [0, h - 1],
      [w - 1, h - 1],
      // Edge distribution points
      [Math.floor(w / 4), 0],
      [Math.floor(w / 2), 0],
      [Math.floor((3 * w) / 4), 0],
      [Math.floor(w / 4), h - 1],
      [Math.floor(w / 2), h - 1],
      [Math.floor((3 * w) / 4), h - 1],
      [0, Math.floor(h / 3)],
      [0, Math.floor((2 * h) / 3)],
      [w - 1, Math.floor(h / 3)],
      [w - 1, Math.floor((2 * h) / 3)],
      // 4 inset points
      [insetX, insetY],
      [w - 1 - insetX, insetY],
      [insetX, h - 1 - insetY],
      [w - 1 - insetX, h - 1 - insetY],
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

    // 1. Transparent PNG / SVG (8 or more probe points transparent, or fewer than 4 opaque points)
    if (transparentCount >= 8 || colors.length < 4) {
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

    // 2. Robust Outlier-Filtered Edge Sampling (find median RGB and filter outliers like text touching borders)
    const sortedR = colors.map(c => c[0]).sort((a, b) => a - b);
    const sortedG = colors.map(c => c[1]).sort((a, b) => a - b);
    const sortedB = colors.map(c => c[2]).sort((a, b) => a - b);
    const medR = sortedR[Math.floor(sortedR.length / 2)];
    const medG = sortedG[Math.floor(sortedG.length / 2)];
    const medB = sortedB[Math.floor(sortedB.length / 2)];

    const inliers: [number, number, number][] = [];
    for (const c of colors) {
      const diff = Math.abs(c[0] - medR) + Math.abs(c[1] - medG) + Math.abs(c[2] - medB);
      if (diff <= 75) {
        inliers.push(c);
      }
    }

    const inlierRatio = inliers.length / colors.length;

    // If 55% or more of edge points agree, it is a solid/near-solid background!
    if (inlierRatio >= 0.55 && inliers.length > 0) {
      let sumR = 0, sumG = 0, sumB = 0;
      for (const [r, g, b] of inliers) {
        sumR += r;
        sumG += g;
        sumB += b;
      }
      const avgR = Math.round(sumR / inliers.length);
      const avgG = Math.round(sumG / inliers.length);
      const avgB = Math.round(sumB / inliers.length);

      const luminance = 0.299 * avgR + 0.587 * avgG + 0.114 * avgB;
      const isDark = luminance < 140;
      const isLight = luminance > 180;

      // Smart extremes clamping: clamp near-black (< 35) to pure OLED black,
      // and near-white (> 225) to pure crisp white to eliminate muddy gray borders
      let finalBgColor: string;
      let bgRgbForAccent: [number, number, number];

      if (luminance < 35) {
        finalBgColor = '#000000';
        bgRgbForAccent = [0, 0, 0];
      } else if (luminance > 225) {
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

    // 3. Varied / Photo edge: seamless dark or crisp white ambient blending instead of plain gray
    let sumAllR = 0, sumAllG = 0, sumAllB = 0;
    for (const [r, g, b] of colors) {
      sumAllR += r;
      sumAllG += g;
      sumAllB += b;
    }
    const avgAllR = Math.round(sumAllR / colors.length);
    const avgAllG = Math.round(sumAllG / colors.length);
    const avgAllB = Math.round(sumAllB / colors.length);
    const overallLum = 0.299 * avgAllR + 0.587 * avgAllG + 0.114 * avgAllB;

    const accent = extractBrandAccentColor(imgData, w, h, null);
    let finalBgColor: string;
    let borderColor: string;
    let boxShadow: string;

    if (overallLum < 75) {
      finalBgColor = '#000000';
      if (accent) {
        const [ar, ag, ab] = accent.rgb;
        borderColor = `rgba(${ar}, ${ag}, ${ab}, 0.4)`;
        boxShadow = `0 6px 20px rgba(0, 0, 0, 0.45), 0 0 12px rgba(${ar}, ${ag}, ${ab}, 0.25)`;
      } else {
        borderColor = 'rgba(255, 255, 255, 0.18)';
        boxShadow = '0 6px 20px rgba(0, 0, 0, 0.45)';
      }
    } else if (overallLum > 185) {
      finalBgColor = '#ffffff';
      if (accent) {
        const [ar, ag, ab] = accent.rgb;
        borderColor = `rgba(${ar}, ${ag}, ${ab}, 0.3)`;
        boxShadow = `0 6px 20px rgba(0, 0, 0, 0.12), 0 0 10px rgba(${ar}, ${ag}, ${ab}, 0.15)`;
      } else {
        borderColor = 'rgba(0, 0, 0, 0.12)';
        boxShadow = '0 6px 20px rgba(0, 0, 0, 0.12)';
      }
    } else {
      finalBgColor = `rgba(${avgAllR}, ${avgAllG}, ${avgAllB}, 0.22)`;
      borderColor = accent ? `rgba(${accent.rgb[0]}, ${accent.rgb[1]}, ${accent.rgb[2]}, 0.35)` : 'rgba(255, 255, 255, 0.15)';
      boxShadow = '0 4px 16px rgba(0, 0, 0, 0.2)';
    }

    const containerStyle: Record<string, string> = {
      backgroundColor: finalBgColor,
      borderColor,
      boxShadow,
    };

    const res: SampledColorResult = {
      bgColor: finalBgColor,
      isDark: overallLum < 140,
      isTransparent: false,
      accentColor: accent?.css ?? null,
      borderColor,
      containerStyle,
    };
    colorCache.set(src, res);
    return res;
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
