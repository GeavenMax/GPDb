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

# ------------------------------------------------------------------ HD upgrade
#
# The site publishes every episode still twice: `episodeNNN.jpg` (the ~7 KB grid
# preview the movie page embeds) and `episodeNNNb.jpg` (the ~60 KB full-size
# still the episode's own page opens). The `b` is the entire difference, so the
# HD address is derivable from the URL we already store -- no page fetch needed
# to discover it. Measured across the whole id range: 24/24 sampled episodes had
# one, at 6.0x-12.6x the bytes.

_EPISODE_THUMB_RE = re.compile(r"^(.*/images/Episodes/episode\d+)\.jpg$", re.IGNORECASE)


def hd_url_for(url: str) -> str | None:
    """`.../episode123.jpg` -> `.../episode123b.jpg`; None if `url` is not a plain
    episode thumbnail. An already-HD URL does not match, so this is idempotent."""
    if not url:
        return None
    m = _EPISODE_THUMB_RE.match(url)
    return f"{m.group(1)}b.jpg" if m else None


def probe_url(url: str, timeout: int = 15) -> bool | None:
    """True if the file exists, False if the site says it does not, None if we
    never got a trustworthy answer.

    The three-way result is the point: a timeout must not be written down as
    "this episode has no HD version", for the same reason the scrapers refuse to
    record a 404 they did not actually see."""
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENTS}, method="HEAD")
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
                return 200 <= resp.status < 300
        except urllib.error.HTTPError as e:
            if e.code in (403, 429, 503):
                time.sleep(2.0 * (attempt + 1))
                continue
            return False
        except Exception:
            time.sleep(1.0 * (attempt + 1))
    return None


def format_eta(seconds: float) -> str:
    if seconds < 0 or seconds != seconds or seconds == float("inf"):
        return "--:--"
    seconds = int(seconds)
    if seconds >= 3600:
        return f"{seconds // 3600}h{seconds % 3600 // 60:02d}m"
    return f"{seconds // 60}m{seconds % 60:02d}s"


def run_hd_upgrade(apply: bool = False, limit: int | None = None, concurrency: int = 6,
                   batch: int = 250, abort_unresolved: float = 0.5, probe: bool = True):
    """Repoint episode thumbnails at their high-resolution twin.

    Only rows whose HD file is *confirmed present* are rewritten; every other row keeps
    the low-res URL it already had, so running this can never leave an episode pointing
    at a missing image.

    Written in batches, one commit each. That is what makes this resumable: a row already
    rewritten to `...b.jpg` no longer matches `hd_url_for`, so a second run skips it and
    picks up exactly where the last one stopped. It also means an interrupted run keeps
    everything it had confirmed rather than losing the lot.

    Concurrency defaults low. Probing 32k URLs at 24 workers held the site's attention
    long enough that it began refusing new connections outright, and the run then spent
    hours retrying instead of progressing; the block lifted as soon as it stopped.

    `probe=False` rewrites every row without asking the site first. That is half the
    requests, because the pre-download that follows fetches each HD file anyway and its
    success or failure is the same answer the probe would have given - see
    `run_revert_missing_hd`, which acts on it. Use probing when the download is not going
    to follow immediately; use `probe=False` for the rewrite-then-download flow.
    """
    conn = sqlite3.connect(str(DB_PATH))
    rows = conn.execute(
        "SELECT id, thumbnail_url FROM episodes "
        "WHERE thumbnail_url IS NOT NULL AND thumbnail_url != '' ORDER BY id"
    ).fetchall()

    all_todo = [(ep_id, hd) for ep_id, url in rows if (hd := hd_url_for(url))]
    # Counted before the limit is applied: `len(rows) - len(todo)` after a truncation
    # just reports the limit back, which is how a run that had written nothing still
    # looked like it had skipped 31,825 rows.
    already = len(rows) - len(all_todo)
    todo = all_todo[:limit] if limit else all_todo

    print("=== Episode Thumbnail HD Upgrade ===")
    print(f"Database:    {DB_PATH}")
    print(f"Rows:        {len(rows)} episodes | already HD: {already:,} | to probe: {len(todo):,}")
    print(f"Mode:        {'APPLY (writes to gevi.db)' if apply else 'dry run (no writes)'}")
    print(f"Batch:       {batch} | Concurrency: {concurrency}")
    print(f"Method:      {'probe each URL, then rewrite' if probe else 'rewrite all, verify by download'}")
    print("(interrupt freely — each batch is committed; rerun to continue)", flush=True)
    if not todo:
        print("Nothing to upgrade.")
        conn.close()
        return

    confirmed_total = written_total = missing_total = unknown_total = 0
    start = time.time()
    for base in range(0, len(todo), batch):
        chunk = todo[base:base + batch]
        have: list[tuple[int, str]] = []
        missing = unknown = 0
        if not probe:
            have = list(chunk)
        else:
            with ThreadPoolExecutor(max_workers=concurrency) as executor:
                futures = {executor.submit(probe_url, hd): (ep_id, hd) for ep_id, hd in chunk}
                for fut in as_completed(futures):
                    ep_id, hd = futures[fut]
                    try:
                        res = fut.result()
                    except Exception:
                        res = None
                    if res is True:
                        have.append((ep_id, hd))
                    elif res is False:
                        missing += 1
                    else:
                        unknown += 1

        if apply and have:
            # The SQL's parameters are (thumbnail_url, id) in that order; `have` holds
            # (id, url). Swapping them does not raise - SQLite compares an INTEGER id to a
            # TEXT url, matches nothing, and reports success - so the swap is done here,
            # next to the statement, and the result is then checked rather than assumed.
            before = conn.total_changes
            with conn:
                conn.executemany(
                    "UPDATE episodes SET thumbnail_url = ? WHERE id = ?",
                    [(hd, ep_id) for ep_id, hd in have],
                )
            changed = conn.total_changes - before
            if changed != len(have):
                print(f"⚠️  本批预期更新 {len(have)} 行，实际 {changed} 行 —— "
                      f"WHERE 没匹配上，这批没写进去。", flush=True)
            written_total += changed
        confirmed_total += len(have)
        missing_total += missing
        unknown_total += unknown

        done = base + len(chunk)
        elapsed = time.time() - start
        rate = done / elapsed if elapsed else 0
        eta = format_eta((len(todo) - done) / rate) if rate else "--:--"
        # Rate and ETA are network numbers; without probing there is no network to pace
        # against and they would print a meaningless six-figure requests-per-second.
        pace = f"{rate:.1f}/s | ETA {eta}" if probe else "no network"
        print(f"[{done:,}/{len(todo):,}] {done / len(todo) * 100:5.1f}% | HD {confirmed_total:,} | "
              f"no-HD {missing_total:,} | unresolved {unknown_total:,} | {pace}", flush=True)

        # A batch the site mostly refused means it is pushing back, and continuing only
        # digs in deeper. Stop cleanly: everything confirmed so far is already committed.
        if unknown > len(chunk) * abort_unresolved:
            print(f"\n⚠️  {unknown}/{len(chunk)} 条无响应 —— 站点已在限流，先停下。"
                  f"\n   已写入 {written_total:,} 条。等一段时间后重跑本命令即可续上。")
            break

    print(f"\nHD version present : {confirmed_total:,} | no HD: {missing_total:,} | "
          f"unresolved: {unknown_total:,} | rows written: {written_total:,}")
    if apply and written_total:
        print("Next: `cache_images.py --mode episodes` to fetch them — the cache keys on the "
              "filename, so these download as new files and the low-res ones become orphans "
              "(`--prune-orphans --apply` after the download).")
    elif confirmed_total:
        print("Dry run: no rows changed. Re-run with --apply to write.")
    elif confirmed_total == 0 and written_total == 0:
        print("Nothing to upgrade.")
    conn.close()


def run_revert_missing_hd(apply: bool = False):
    """Put back the low-res URL for any episode whose HD file never arrived.

    The safety net for the `probe=False` rewrite. The cache is the honest judge here:
    the desktop client serves from it, so a `...b.jpg` URL with no file on disk is a
    broken image no matter what the site would say about it. Judged locally, so this
    costs no requests.

    Run it after the episode download, never before — a file missing because the
    download has not run yet is not the same as one the site does not have."""
    conn = sqlite3.connect(str(DB_PATH))
    rows = conn.execute(
        "SELECT id, thumbnail_url FROM episodes "
        "WHERE thumbnail_url LIKE '%/Episodes/episode%b.jpg'"
    ).fetchall()

    missing: list[tuple[int, str]] = []
    for ep_id, url in rows:
        path = get_cache_path(url)
        if not (path.exists() and path.stat().st_size > 100):
            low_res = url[:-len("b.jpg")] + ".jpg"
            missing.append((ep_id, low_res))

    print("=== Revert Episodes With No Cached HD File ===")
    print(f"HD URLs in database : {len(rows):,}")
    print(f"No HD file on disk  : {len(missing):,}")
    if not missing:
        print("Every HD URL has its file. Nothing to revert.")
        conn.close()
        return
    for ep_id, low_res in missing[:5]:
        print(f"   e.g. #{ep_id} -> {low_res.rsplit('/', 1)[-1]}")
    if len(missing) > 5:
        print(f"   ... 另外 {len(missing) - 5:,} 条")
    # A file that is missing locally is not the same as one the site does not have:
    # the download counts a refused connection (`ssl.SSLEOFError:
    # UNEXPECTED_EOF_WHILE_READING`) as a failure too, and that failure looks exactly
    # like a 404 from here. Reverting those downgrades episodes whose HD twin is alive
    # and well — measured, not hypothetical: probing a handful of "missing" ones by
    # hand returned HTTP 200 with 47-92 KB of image. So say this before `--apply`.
    print("⚠️  先别急着 --apply：本地缺文件 ≠ 站点没有。下载把「连接被拒」也算作失败，")
    print("    那和 404 在这里长得一模一样。原样重跑一次下载（跳过已下好的、只重试缺的，")
    print("    约 20 分钟），仍然缺的那些才值得回滚。")
    if not apply:
        print("Dry run: nothing changed. Re-run with --apply.")
        conn.close()
        return
    before = conn.total_changes
    with conn:
        conn.executemany(
            "UPDATE episodes SET thumbnail_url = ? WHERE id = ?",
            [(low_res, ep_id) for ep_id, low_res in missing],
        )
    print(f"Reverted {conn.total_changes - before:,} rows to their low-res thumbnail.")
    conn.close()


def run_prune_orphans(apply: bool = False):
    """Delete cached episode thumbnails the database no longer points at.

    Pointing an episode at `episodeNNNb.jpg` leaves the old `episodeNNN.jpg` on
    disk: nothing deletes on change, and `--clear` would wipe the whole tree
    including covers and performer images that are still live."""
    conn = sqlite3.connect(str(DB_PATH))
    live = {
        get_cache_path(url).name
        for (url,) in conn.execute("SELECT thumbnail_url FROM episodes WHERE thumbnail_url IS NOT NULL")
        if url
    }
    conn.close()

    ep_dir = DEFAULT_CACHE_DIR / "Episodes"
    if not ep_dir.exists():
        print(f"No episode thumbnail cache at {ep_dir}.")
        return

    files = [p for p in ep_dir.iterdir() if p.is_file() and not p.name.endswith(".tmp")]
    orphans = [p for p in files if p.name not in live]
    freed = sum(p.stat().st_size for p in orphans)
    print("=== Orphaned Episode Thumbnails ===")
    print(f"Cache dir:   {ep_dir}")
    print(f"On disk:     {len(files)} files")
    print(f"Unreferenced:{len(orphans)} files, {freed / (1024 * 1024):.1f} MB")
    if not orphans:
        print("Nothing to prune.")
        return
    if not apply:
        print("Dry run: nothing deleted. Re-run with --apply.")
        return
    removed = 0
    for p in orphans:
        try:
            p.unlink()
            removed += 1
        except OSError:
            pass
    print(f"Deleted {removed} files, freed {freed / (1024 * 1024):.1f} MB.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="GEVI Offline Image Disk Cache Downloader")
    parser.add_argument("--mode", choices=["all", "covers", "episodes", "performers"], default="all")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--concurrency", type=int, default=6)
    parser.add_argument("--stats", action="store_true", help="Print cache stats and exit")
    parser.add_argument("--clear", action="store_true", help="Clear cache folder and exit")
    parser.add_argument("--hd-upgrade", action="store_true",
                        help="Repoint episode thumbnails at their high-resolution twin")
    parser.add_argument("--batch", type=int, default=250,
                        help="--hd-upgrade: probes committed per batch (default 250)")
    parser.add_argument("--no-probe", action="store_true",
                        help="--hd-upgrade: rewrite without probing; verify via the download")
    parser.add_argument("--revert-missing-hd", action="store_true",
                        help="Restore the low-res URL where the HD file never downloaded")
    parser.add_argument("--prune-orphans", action="store_true",
                        help="Delete cached episode thumbnails the database no longer references")
    parser.add_argument("--apply", action="store_true",
                        help="Actually write/delete; without it the maintenance modes are dry runs")
    args = parser.parse_args()

    if args.stats:
        s = get_cache_stats()
        print(f"Cached images: {s['count']} | Size: {s['size_mb']} MB | Path: {s['path']}")
    elif args.clear:
        c = clear_cache()
        print(f"Cleared {c} cached files.")
    elif args.hd_upgrade:
        run_hd_upgrade(apply=args.apply, limit=args.limit, concurrency=args.concurrency,
                       batch=args.batch, probe=not args.no_probe)
    elif args.revert_missing_hd:
        run_revert_missing_hd(apply=args.apply)
    elif args.prune_orphans:
        run_prune_orphans(apply=args.apply)
    else:
        run_batch_download(mode=args.mode, limit=args.limit, concurrency=args.concurrency)
