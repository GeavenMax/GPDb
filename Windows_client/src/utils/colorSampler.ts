/**
 * Image Edge Color Sampler
 * Samples the 4 corners and edge probe pixels of an image to detect if it has a solid
 * background (e.g. white/black JPEG) or is transparent (PNG/SVG).
 * Returns the sampled background color and styling so containers can blend
 * seamlessly with the image without harsh rectangular borders.
 */

export interface SampledColorResult {
  /** CSS background-color string if solid color detected, or null if transparent/non-uniform */
  bgColor: string | null;
  /** True if sampled color is dark (luminance < 0.55) */
  isDark: boolean;
  /** True if transparent alpha detected on edges */
  isTransparent: boolean;
  /** Ready-to-use CSS style object for container */
  containerStyle: Record<string, string>;
}

// In-memory cache to prevent redundant canvas operations
const colorCache = new Map<string, SampledColorResult>();

export function sampleImageEdgeColor(img: HTMLImageElement | null | undefined): SampledColorResult {
  if (!img) {
    return {
      bgColor: null,
      isDark: true,
      isTransparent: true,
      containerStyle: {},
    };
  }

  const src = img.currentSrc || img.src;
  if (!src) {
    return {
      bgColor: null,
      isDark: true,
      isTransparent: true,
      containerStyle: {},
    };
  }

  if (colorCache.has(src)) {
    return colorCache.get(src)!;
  }

  try {
    const canvas = document.createElement('canvas');
    // Scale down to max 48x48 for ultra-fast sampling (< 0.2ms)
    const w = (canvas.width = Math.min(img.naturalWidth || 48, 48));
    const h = (canvas.height = Math.min(img.naturalHeight || 48, 48));
    if (w <= 0 || h <= 0) {
      const fallback: SampledColorResult = {
        bgColor: null,
        isDark: true,
        isTransparent: true,
        containerStyle: {},
      };
      return fallback;
    }

    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) throw new Error('no canvas 2d context');

    ctx.drawImage(img, 0, 0, w, h);

    // 8 edge probe points: 4 corners + 4 edge centers
    const points: [number, number][] = [
      [0, 0],
      [w - 1, 0],
      [0, h - 1],
      [w - 1, h - 1],
      [Math.floor(w / 2), 0],
      [Math.floor(w / 2), h - 1],
      [0, Math.floor(h / 2)],
      [w - 1, Math.floor(h / 2)],
    ];

    let transparentCount = 0;
    const colors: [number, number, number][] = [];

    for (const [px, py] of points) {
      const p = ctx.getImageData(px, py, 1, 1).data;
      // Alpha < 25 means transparent or semi-transparent edge
      if (p[3] < 25) {
        transparentCount++;
      } else {
        colors.push([p[0], p[1], p[2]]);
      }
    }

    // If 4 or more probe points are transparent, treat as transparent graphic (PNG/SVG)
    if (transparentCount >= 4) {
      const res: SampledColorResult = {
        bgColor: null,
        isDark: true,
        isTransparent: true,
        containerStyle: {},
      };
      colorCache.set(src, res);
      return res;
    }

    if (colors.length === 0) {
      const res: SampledColorResult = {
        bgColor: null,
        isDark: true,
        isTransparent: true,
        containerStyle: {},
      };
      colorCache.set(src, res);
      return res;
    }

    // Calculate average RGB of the edge probe points
    let sumR = 0, sumG = 0, sumB = 0;
    for (const [r, g, b] of colors) {
      sumR += r;
      sumG += g;
      sumB += b;
    }
    const avgR = Math.round(sumR / colors.length);
    const avgG = Math.round(sumG / colors.length);
    const avgB = Math.round(sumB / colors.length);

    // Check color consistency: are edge pixels close to the average?
    // Tolerance allows subtle JPEG compression artifacts near borders
    let maxDiff = 0;
    for (const [r, g, b] of colors) {
      const diff = Math.abs(r - avgR) + Math.abs(g - avgG) + Math.abs(b - avgB);
      if (diff > maxDiff) maxDiff = diff;
    }

    if (maxDiff <= 95) {
      const luminance = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB) / 255;
      const isDark = luminance < 0.55;
      const bgColor = `rgb(${avgR}, ${avgG}, ${avgB})`;

      const containerStyle: Record<string, string> = {
        backgroundColor: bgColor,
        borderColor: isDark ? 'rgba(255, 255, 255, 0.18)' : 'rgba(0, 0, 0, 0.12)',
        boxShadow: isDark
          ? '0 6px 20px rgba(0, 0, 0, 0.4)'
          : '0 6px 20px rgba(0, 0, 0, 0.15)',
      };

      const res: SampledColorResult = {
        bgColor,
        isDark,
        isTransparent: false,
        containerStyle,
      };
      colorCache.set(src, res);
      return res;
    }

    // Edge colors vary significantly (e.g. photo edge)
    const fallback: SampledColorResult = {
      bgColor: null,
      isDark: true,
      isTransparent: false,
      containerStyle: {},
    };
    colorCache.set(src, fallback);
    return fallback;
  } catch {
    // If canvas is tainted or throws (e.g. browser security policy on file://)
    const fallback: SampledColorResult = {
      bgColor: null,
      isDark: true,
      isTransparent: true,
      containerStyle: {},
    };
    colorCache.set(src, fallback);
    return fallback;
  }
}
