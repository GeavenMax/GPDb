/**
 * Image Cache URL Resolver
 * Routes remote image URLs through the local offline disk cache proxy
 * for instant sub-millisecond local loading and 100% offline access.
 */

const LOCAL_API_BASE = 'http://127.0.0.1:8787';

const isTauriEnv =
  typeof window !== 'undefined' &&
  ('__TAURI_INTERNALS__' in window || '__TAURI__' in window);

export function getImageUrl(rawUrl: string | null | undefined): string {
  if (!rawUrl) return '';

  // If it's already a local proxy url, data url, or custom protocol, return directly
  if (
    rawUrl.startsWith('data:') ||
    rawUrl.startsWith('blob:') ||
    rawUrl.startsWith('gpdb-img:') ||
    rawUrl.startsWith(LOCAL_API_BASE)
  ) {
    return rawUrl;
  }

  // In Tauri desktop client: read directly from local disk image_cache via gpdb-img protocol
  if (isTauriEnv) {
    return `gpdb-img://localhost/?url=${encodeURIComponent(rawUrl)}`;
  }

  // If running in browser dev connecting to local python API server:
  // Route via /api/images/cache?url=<rawUrl>
  return `${LOCAL_API_BASE}/api/images/cache?url=${encodeURIComponent(rawUrl)}`;
}
