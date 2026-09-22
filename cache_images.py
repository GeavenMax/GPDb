#!/usr/bin/env python3
"""
GEVI Offline Database - High-Performance Offline Image Disk Cache System
Downloads and caches covers, back covers, and episode thumbnails to local disk.
Provides CLI batch downloading with progress tracking and connection pooling.
"""

from __future__ import annotations
import argparse
import hashlib
import os
import re
import sqlite3
import sys
import time
import urllib.request
import urllib.error
import ssl
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "gevi.db"
DEFAULT_CACHE_DIR = BASE_DIR / "image_cache"

USER_AGENTS = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

# Built once for the whole run: create_default_context() reloads the system trust
# store on every call, which costs more than fetching a 7KB cover.
SSL_CTX = ssl.create_default_context()

def get_cache_path(url: str, cache_dir: Path = DEFAULT_CACHE_DIR) -> Path:
    """Map an image URL to a clean local file path."""
    if not url:
        return cache_dir / "unknown.jpg"
    
    # Check for GEVI standard paths: images/Covers/..., images/Episodes/..., images/Stars/...
    m = re.search(r"images/(Covers|Episodes|Stars|icons|logo)/([^?#]+)", url, re.IGNORECASE)
    if m:
        folder = m.group(1).capitalize()
        filename = m.group(2)
        target = cache_dir / folder / filename
    else:
        # Fallback to MD5 filename
        ext = ".jpg"
        if url.lower().endswith(".png"):
            ext = ".png"
        elif url.lower().endswith(".webp"):
            ext = ".webp"
        hash_name = hashlib.md5(url.encode("utf-8")).hexdigest() + ext
        target = cache_dir / "misc" / hash_name

    target.parent.mkdir(parents=True, exist_ok=True)
    return target

def download_image(url: str, cache_dir: Path = DEFAULT_CACHE_DIR, timeout: int = 15) -> bool:
    """Download single image to disk if not already cached. Returns True if cached or newly downloaded."""
    if not url:
        return False
    local_path = get_cache_path(url, cache_dir)
    if local_path.exists() and local_path.stat().st_size > 100:
        return True

    # Build request with spoofed headers and NO Referer (bypasses Cloudflare 403)
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENTS,
            "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Connection": "keep-alive"
        }
    )

    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
                if resp.status == 200:
                    data = resp.read()
                    if len(data) > 100:
                        tmp_path = local_path.with_suffix(".tmp")
                        with open(tmp_path, "wb") as f:
                            f.write(data)
                        tmp_path.replace(local_path)
                        return True
                return False
        except urllib.error.HTTPError:
            # The site answered — 404/403 will answer the same way next time. Without
            # this branch the generic handler below retries with 1+2+3s of backoff,
            # so each dead cover (over 12k of them here) burned a worker for 6s.
            return False
        except Exception:
            time.sleep(1.0 * (attempt + 1))
    return False

def get_cache_stats(cache_dir: Path = DEFAULT_CACHE_DIR) -> dict:
    """Calculate total cached image files and disk usage."""
    if not cache_dir.exists():
        return {"count": 0, "size_mb": 0.0, "path": str(cache_dir)}

    total_files = 0
    total_bytes = 0
    for root, _, files in os.walk(cache_dir):
        for f in files:
            if not f.endswith(".tmp"):
                fp = os.path.join(root, f)
                try:
                    total_bytes += os.path.getsize(fp)
                    total_files += 1
                except OSError:
                    pass

    return {
        "count": total_files,
        "size_mb": round(total_bytes / (1024 * 1024), 2),
        "path": str(cache_dir)
    }

def clear_cache(cache_dir: Path = DEFAULT_CACHE_DIR) -> int:
    """Remove all cached image files."""
    count = 0
    if not cache_dir.exists():
        return 0
    for root, _, files in os.walk(cache_dir):
        for f in files:
            fp = os.path.join(root, f)
            try:
                os.remove(fp)
                count += 1
            except OSError:
                pass
    return count

def run_batch_download(mode: str = "all", limit: int | None = None, concurrency: int = 6):
    """Batch download all covers and episode thumbnails in gevi.db."""
    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()

    urls_to_download: list[str] = []

    if mode in ("all", "covers"):
        cur.execute("SELECT cover_full, covers_json FROM movies WHERE cover_full IS NOT NULL")
        for c_full, c_json in cur.fetchall():
            if c_full:
                urls_to_download.append(c_full)
            if c_json:
                import json
                try:
                    for u in json.loads(c_json):
                        if u:
                            urls_to_download.append(u)
                except Exception:
                    pass

    if mode in ("all", "episodes"):
        cur.execute("SELECT thumbnail_url FROM episodes WHERE thumbnail_url IS NOT NULL AND thumbnail_url != ''")
        for (thumb,) in cur.fetchall():
            if thumb:
                urls_to_download.append(thumb)

    if mode in ("all", "performers"):
        cur.execute("SELECT image_url FROM performers WHERE image_url IS NOT NULL AND image_url != ''")
        for (img,) in cur.fetchall():
            if img:
                urls_to_download.append(img)

    conn.close()

    # Deduplicate & filter
    unique_urls = list(dict.fromkeys(u for u in urls_to_download if u.startswith("http")))
    if limit:
        unique_urls = unique_urls[:limit]

    total = len(unique_urls)
    print(f"=== GEVI Offline Image Cache Downloader ===")
    print(f"Target Cache Folder: {DEFAULT_CACHE_DIR}")
    print(f"Discovered Unique Images: {total} items (Mode: {mode})")
    print(f"Worker Concurrency: {concurrency}")

    already_cached = 0
    needed_urls = []
    for u in unique_urls:
        lp = get_cache_path(u, DEFAULT_CACHE_DIR)
        if lp.exists() and lp.stat().st_size > 100:
            already_cached += 1
        else:
            needed_urls.append(u)

    print(f"Already on disk: {already_cached} | Remaining to fetch: {len(needed_urls)}")
    if not needed_urls:
        print("All images are already cached offline! Done.")
        return

    success = 0
    failed = 0
    start_time = time.time()

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = {executor.submit(download_image, u): u for u in needed_urls}
        for i, fut in enumerate(as_completed(futures), 1):
            url = futures[fut]
            try:
                ok = fut.result()
                if ok:
                    success += 1
                else:
                    failed += 1
            except Exception:
                failed += 1

            if i % 10 == 0 or i == len(needed_urls):
                elapsed = time.time() - start_time
                speed = i / elapsed if elapsed > 0 else 0
                pct = (i / len(needed_urls)) * 100
                sys.stdout.write(f"\r[{i}/{len(needed_urls)}] ({pct:.1f}%) | Success: {success} | Failed: {failed} | Speed: {speed:.1f} img/s")
                sys.stdout.flush()

    print("\n\nBatch cache download complete!")
    stats = get_cache_stats(DEFAULT_CACHE_DIR)
    print(f"Total Local Images: {stats['count']} files ({stats['size_mb']} MB)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="GEVI Offline Image Disk Cache Downloader")
    parser.add_argument("--mode", choices=["all", "covers", "episodes", "performers"], default="all")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--concurrency", type=int, default=6)
    parser.add_argument("--stats", action="store_true", help="Print cache stats and exit")
    parser.add_argument("--clear", action="store_true", help="Clear cache folder and exit")
    args = parser.parse_args()

    if args.stats:
        s = get_cache_stats()
        print(f"Cached images: {s['count']} | Size: {s['size_mb']} MB | Path: {s['path']}")
    elif args.clear:
        c = clear_cache()
        print(f"Cleared {c} cached files.")
    else:
        run_batch_download(mode=args.mode, limit=args.limit, concurrency=args.concurrency)
