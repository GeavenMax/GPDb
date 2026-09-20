/**
 * Image Cache URL Resolver
 * Routes remote image URLs through the local offline disk cache proxy
 * for instant sub-millisecond local loading and 100% offline access.
 */

const LOCAL_API_BASE = 'http://127.0.0.1:8787';

export function getImageUrl(rawUrl: string | null | undefined): string {
  if (!rawUrl) return '';
  
  // If it's already a local proxy url or data url, return directly
  if (rawUrl.startsWith('data:') || rawUrl.startsWith('blob:') || rawUrl.startsWith(LOCAL_API_BASE)) {
    return rawUrl;
  }

  // If running in browser/tauri connecting to local python API server:
  // Route via /api/images/cache?url=<rawUrl>
  return `${LOCAL_API_BASE}/api/images/cache?url=${encodeURIComponent(rawUrl)}`;
}
