#!/usr/bin/env python3
"""
GEVI Industrial Batch Scraper & Offline DB Builder
Features:
- Checkpoint / Resume: Never re-downloads already processed IDs.
- Concurrency with ThreadPool + Single DB Writer Queue (Zero database lock contention).
- Cloudflare polite rate control & exponential backoff on errors.
- Graceful Ctrl+C shutdown with pending queue flush.
- Zero third-party dependencies (100% macOS Python 3 standard library).

Networking notes (measured against the live site):
- Every worker owns ONE persistent TLS connection. Opening a fresh connection per
  request (as urllib.request does by default) makes each worker pay a full TLS
  handshake, which both caps throughput at ~0.65 req/s and trips Cloudflare's
  connection-rate limiter - the resulting failed requests used to be misrecorded
  as "404 page does not exist" and were then never retried. Connection reuse
  measured ~17 req/s at 10 workers with zero errors.
- The site signals a genuinely missing item with `301 -> /404.shtml`, not a 404
  status. A 200 page that fails to parse is a transient failure (500), never a 404.
"""

from __future__ import annotations
import sys
import os
import re
import html
import time
import signal
import argparse
import threading
import queue
import ssl
import json
import http.client
import urllib.parse
from datetime import date
from pathlib import Path
from typing import Any

from db_manager import DatabaseManager

BASE_URL = "https://gayeroticvideoindex.com"
HOST = "gayeroticvideoindex.com"
USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

# The site redirects missing items here instead of returning a 404 status.
NOT_FOUND_MARKER = "404.shtml"

REQUEST_HEADERS = {
    "User-Agent": USER_AGENT,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Accept-Encoding": "identity",
    "Connection": "keep-alive",
}


class KeepAliveClient:
    """Thread-confined HTTPS client that reuses one TLS connection across requests."""

    def __init__(self, timeout: float = 20.0):
        self.timeout = timeout
        self._ctx = ssl.create_default_context()
        self._conn: http.client.HTTPSConnection | None = None

    def close(self):
        conn, self._conn = self._conn, None
        if conn is not None:
            try:
                conn.close()
            except Exception:
                pass

    def _connect(self):
        self._conn = http.client.HTTPSConnection(HOST, timeout=self.timeout, context=self._ctx)

    def get(self, path: str) -> tuple[int, str, str]:
        """Perform one GET. Returns (status, decoded_body, location_header)."""
        if self._conn is None:
            self._connect()
        try:
            self._conn.request("GET", path, headers=REQUEST_HEADERS)
            resp = self._conn.getresponse()
            # Body must be fully drained or the connection cannot be reused.
            body = resp.read()
            return resp.status, body.decode("utf-8", errors="ignore"), (resp.getheader("Location") or "")
        except Exception:
            self.close()
            raise


def parse_release_year(cell_html: str) -> int | None:
    """The release year printed in the Released cell, or None when the site has none.

    The site prints a bare "?" for a film it has no release year for, and that is the
    case 69 stored films hit: the old pattern let `[^\\d]*` run past the "?" and into
    the *next* cell, so the first four-digit run in the row won — which is the Vendor
    ID ("ZV-1003" -> 1003, "AK-7201" -> 7201). Those films were stored with a
    catalogue number as their year.

    Called with that one cell's HTML, so the number cannot come from anywhere else in
    the row, and bounded so a cell that says something impossible stores nothing:
    a wrong year is worse than a missing one, because a missing one says "unknown"
    while a wrong one sorts, filters and displays as fact.
    """
    text = re.sub(r'<[^>]+>', ' ', cell_html)
    m = re.search(r'\b(1[89]\d{2}|20\d{2})\b', text)
    if not m:
        return None
    year = int(m.group(1))
    return year if 1900 <= year <= date.today().year else None


def to_path(url: str) -> str:
    """Reduce an absolute or relative URL to a request path."""
    parsed = urllib.parse.urlparse(url)
    if parsed.netloc and parsed.netloc != HOST:
        raise ValueError(f"Off-host redirect: {url}")
    path = parsed.path or "/"
    if parsed.query:
        path += "?" + parsed.query
    return path

class BatchScraper:
    def __init__(
        self,
        db_path: str = "gevi.db",
        concurrency: int = 8,
        delay: float = 0.02,
        max_retries: int = 4
    ):
        self.db_path = db_path
        self.concurrency = concurrency
        self.delay = delay
        self.max_retries = max_retries
        self.db = DatabaseManager(db_path)

        # One persistent connection per worker thread, created lazily on first use.
        self._clients = threading.local()

        self.work_queue: queue.Queue[int] = queue.Queue()
        self.result_queue: queue.Queue[tuple[str, Any, int]] = queue.Queue()
        self.stop_event = threading.Event()

        # Performance & progress metrics
        self.processed_count = 0
        self.ok_count = 0
        self.not_found_count = 0
        self.error_count = 0
        self.total_target = 0
        self.start_time = 0.0
        self.lock = threading.Lock()

    def _client(self) -> KeepAliveClient:
        client = getattr(self._clients, "client", None)
        if client is None:
            client = KeepAliveClient()
            self._clients.client = client
        return client

    def fetch_url(self, url: str) -> tuple[int, str]:
        """Fetch a URL over this thread's pooled connection.

        Returns (status, body) where status is 200 (page retrieved), 404 (the site
        redirected to 404.shtml: the item genuinely does not exist) or 500 (transient
        failure - safe to retry, and never recorded as "does not exist").
        """
        client = self._client()
        try:
            path = to_path(url)
        except ValueError:
            return 500, ""

        for attempt in range(self.max_retries):
            if self.stop_event.is_set():
                return 0, ""
            try:
                status, body, location = client.get(path)

                # The site answers missing items with 301 -> /404.shtml.
                hops = 0
                while status in (301, 302, 303, 307, 308) and hops < 3:
                    if NOT_FOUND_MARKER in location:
                        return 404, ""
                    if not location:
                        return 500, ""
                    try:
                        path = to_path(location)
                    except ValueError:
                        return 500, ""
                    status, body, location = client.get(path)
                    hops += 1

                if status in (301, 302, 303, 307, 308):
                    return 500, ""          # redirect loop
                if status == 404 or status == 410:
                    return 404, ""
                if status == 200:
                    return 200, body
                if status == 429:
                    # Cloudflare rate limit: back off briefly before retrying.
                    time.sleep(3.0 * (attempt + 1))
                    continue
                if status >= 500:
                    time.sleep(1.5 * (attempt + 1))
                    continue
                return 500, ""
            except Exception:
                # Any socket/TLS level failure: reconnect on the next attempt.
                client.close()
                if attempt < self.max_retries - 1:
                    time.sleep(1.0 * (attempt + 1))
        return 500, ""

    def parse_movie(self, video_id: int, html_text: str) -> dict | None:
        if not html_text:
            return None
        
        t_match = re.search(r'<title>(.*?): Gay Erotic Video Index</title>', html_text, re.IGNORECASE)
        if not t_match:
            h1_m = re.search(r'<h1[^>]*>(.*?)</h1>', html_text, re.IGNORECASE | re.DOTALL)
            title = html.unescape(h1_m.group(1).strip()) if h1_m else ""
        else:
            title = html.unescape(t_match.group(1).strip())

        if not title or title.lower().startswith("404") or "not found" in title.lower():
            return None

        # Studio & ID
        studio_id = None
        studio_name = ""
        std_m = re.search(r'Studio:</div>\s*<div[^>]*>(?:<a href=[\'\"]company/(\d+)[\'\"][^>]*>)?(.*?)(?:</a>)?</div>', html_text, re.DOTALL)
        if std_m:
            if std_m.group(1):
                studio_id = int(std_m.group(1))
            studio_name = html.unescape(re.sub(r'<[^>]+>', '', std_m.group(2)).strip())

        # Released Year. The capture stops at the cell's own </td> so the Vendor ID
        # column next door can never be mistaken for a year — see parse_release_year.
        year = None
        year_m = re.search(r'<th[^>]*>Released</th>.*?<tr>.*?<td[^>]*>.*?</td>\s*<td[^>]*>(.*?)</td>', html_text, re.DOTALL)
        if year_m:
            year = parse_release_year(year_m.group(1))

        # Duration
        duration = None
        dur_m = re.search(r'<th[^>]*>Length</th>.*?<tr>.*?<td[^>]*>.*?</td>\s*<td[^>]*>.*?</td>\s*<td[^>]*>(\d+)</td>', html_text, re.DOTALL)
        if dur_m:
            duration = int(dur_m.group(1))

        # Category
        cat_m = re.search(r'Category:</div>\s*<div[^>]*>(.*?)</div>', html_text, re.DOTALL)
        category = html.unescape(cat_m.group(1).strip()) if cat_m else ""

        # Rating
        rat_m = re.search(r'Rating Out of 4:</div>\s*<div[^>]*>(.*?)</div>', html_text, re.DOTALL)
        rating = html.unescape(rat_m.group(1).strip()) if rat_m else ""

        # Type
        type_m = re.search(r'Type:</div>\s*<div>\s*<div>(.*?)</div>', html_text, re.DOTALL)
        movie_type = html.unescape(type_m.group(1).strip()) if type_m else ""

        # Description
        desc_m = re.search(r'Description source:.*?<div class=\"text-justify[^\"]*\">(.*?)</div>\s*<!-- scenes', html_text, re.DOTALL)
        description = ""
        if desc_m:
            raw_desc = re.sub(r'<[^>]+>', ' ', desc_m.group(1))
            description = html.unescape(re.sub(r'\s+', ' ', raw_desc)).strip()

        # Director & ID
        director_id = None
        director_name = ""
        dir_m = re.search(r'Director:</div>\s*<div[^>]*>(?:<a href=[\'\"]director/(\d+)[\'\"][^>]*>)?(.*?)(?:</a>)?</div>', html_text, re.DOTALL)
        if dir_m:
            if dir_m.group(1):
                director_id = int(dir_m.group(1))
            director_name = html.unescape(re.sub(r'<[^>]+>', '', dir_m.group(2)).strip())

        # Covers (front, back, and any variant covers)
        #
        # Every cover the film has lives inside <div id="coverContainer">, one entry
        # per variant, each carrying both a thumbnail (`src`, under Covers/Icons) and
        # the full-size file (`image`, under Covers). Suffix convention: no suffix is
        # the front cover, "b" is the back cover, and c/d/... are further variants
        # (stills, alternate art). So cover_full is the front and all_covers[1] the
        # back when the film has one.
        #
        # The container element is the important part: a film with no cover art at all
        # renders no coverContainer (the page footer's imageMask modal is always there
        # and is unrelated). That gives us a way to tell "the site has no cover for
        # this film" from "our regex missed the cover area" - without it both look
        # like zero covers, and a later cover-download pass cannot know which films
        # still need checking. covers_known records that we actually saw the gallery.
        gallery_m = re.search(
            r'id=["\']coverContainer["\'](.*?)(?=<div[^>]*id=["\'](?!coverContainer)|</section>|<!--\s*full covers)',
            html_text, re.DOTALL,
        )
        covers_known = gallery_m is not None
        gallery = gallery_m.group(1) if gallery_m else ""

        raw_covers = re.findall(r"image=[\'\"](images/Covers/[^\'\"]+)[\'\"]", gallery)
        all_covers = [f"{BASE_URL}/{c}" for c in raw_covers]
        cover_full = all_covers[0] if all_covers else ""

        icon_m = re.search(r"src=[\'\"](images/Covers/Icons/[^\'\"]+)[\'\"]", gallery)
        cover_icon = f"{BASE_URL}/{icon_m.group(1)}" if icon_m else ""

        # The back cover, kept as its own column: a later bulk cover download should
        # be able to ask "which films have a back cover?" without parsing JSON, and
        # must be able to tell a confirmed-absent back cover ('') from an unchecked
        # one (None). all_covers keeps every variant in order regardless.
        cover_back = next((u for u in all_covers[1:] if re.search(r"b\.(?:jpg|jpeg|png|webp)$", u, re.I)), "")

        # Performers
        performers = []
        seen_perf = set()
        for m in re.finditer(r'<a href=[\'\"]performer/(\d+)[\'\"]><span[^>]*>(.*?)</span></a>', html_text):
            pid = int(m.group(1))
            pname = html.unescape(m.group(2).strip())
            if pid not in seen_perf:
                seen_perf.add(pid)
                performers.append((pid, pname))

        # Episodes / Scenes
        episodes = []
        ep_matches = list(re.finditer(r'<a\s+href=[\'\"](?:/)?episode/(\d+)[\'\"]>\s*<img\s+src=[\'\"](images/Episodes/[^\'\"]+)[\'\"]', html_text))
        for m in ep_matches:
            ep_id = int(m.group(1))
            thumb_path = m.group(2)
            ep_thumb = f"{BASE_URL}/{thumb_path}" if thumb_path else ""

            # Description after the link
            sub = html_text[m.end():m.end() + 1500]
            desc_m = re.search(r'<p[^>]*class=[\'\"]mb-2[\'\"]><span[^>]*>(.*?)</span>', sub, re.DOTALL)
            ep_desc = html.unescape(re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', desc_m.group(1)))).strip() if desc_m else ""

            # Performers in this episode (found in preceding HTML block)
            pre = html_text[max(0, m.start() - 600):m.start()]
            ep_perfs_raw = re.findall(r'<a\s+href=[\'\"](?:/)?performer/(\d+)[\'\"][^>]*>.*?<span[^>]*>(.*?)</span>', pre, re.DOTALL)
            ep_performers = []
            for pid_s, pname_s in ep_perfs_raw:
                try:
                    ep_performers.append((int(pid_s), html.unescape(pname_s.strip())))
                except Exception:
                    pass

            episodes.append({
                "id": ep_id,
                "title": f"Episode #{ep_id}",
                "thumbnail_url": ep_thumb,
                "description": ep_desc,
                "action_notes": "",
                "performers": ep_performers
            })

        return {
            "id": video_id,
            "title": title,
            "studio_id": studio_id,
            "studio_name": studio_name,
            "release_year": year,
            "duration_mins": duration,
            "category": category,
            "rating": rating,
            "movie_type": movie_type,
            "description": description,
            "cover_icon": cover_icon,
            "cover_full": cover_full,
            "cover_back": cover_back,
            "covers": all_covers,
            "covers_known": covers_known,
            "director_id": director_id,
            "director_name": director_name,
            "performers": performers,
            "episodes": episodes
        }

    def parse_performer(self, perf_id: int, html_text: str) -> dict | None:
        if not html_text:
            return None
            
        t_match = re.search(r'<title>(.*?): Gay Erotic Video Index</title>', html_text, re.IGNORECASE)
        if not t_match:
            h1_m = re.search(r'<h1[^>]*>(.*?)</h1>', html_text, re.DOTALL)
            name = html.unescape(h1_m.group(1).strip()) if h1_m else ""
        else:
            name = html.unescape(t_match.group(1).strip())
            
        if not name or name.lower().startswith("404") or "not found" in name.lower():
            return None

        def extract_stat(label: str) -> str:
            pattern = rf'<div class=\"text-yellow-100 whitespace-nowrap\">{label}:</div>\s*<div class=\'whitespace-nowrap\'>(.*?)</div>'
            m = re.search(pattern, html_text, re.DOTALL | re.IGNORECASE)
            return html.unescape(m.group(1).strip()) if m else ""

        tattoos_m = re.search(r'Tattoos:&nbsp;</span>(.*?)</div>', html_text, re.DOTALL)
        tattoos = ""
        if tattoos_m:
            tattoos = html.unescape(re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', tattoos_m.group(1)))).strip()

        notes_m = re.search(r'Notes:</div>\s*<div[^>]*>(.*?)</div>', html_text, re.DOTALL)
        notes = ""
        if notes_m:
            notes = html.unescape(re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', notes_m.group(1)))).strip()

        # Portrait. The filename counter is unrelated to the performer ID, so the
        # path has to be read off the page; performers without a photo omit the <img>.
        img_m = re.search(r"images/Stars/([^'\"]+\.(?:jpg|jpeg|png|webp))", html_text, re.IGNORECASE)
        image_url = f"{BASE_URL}/images/Stars/{img_m.group(1)}" if img_m else ""

        return {
            "id": perf_id,
            "name": name,
            "hair": extract_stat("Hair"),
            "eyes": extract_stat("Eyes"),
            "body_hair": extract_stat("Body Hair"),
            "facial_hair": extract_stat("Facial Hair"),
            "height": extract_stat("Height"),
            "weight": extract_stat("Weight"),
            "build": extract_stat("Build"),
            "skin": extract_stat("Skin"),
            "dick_size": extract_stat("Dick Size"),
            "foreskin": extract_stat("Foreskin"),
            "tattoos": tattoos,
            "notes": notes,
            "image_url": image_url
        }

    @staticmethod
    def _is_not_found_page(html_text: str) -> bool:
        """True when a 200 response is actually the site's 'Not Found' page."""
        m = re.search(r"<title>(.*?)</title>", html_text, re.IGNORECASE | re.DOTALL)
        if not m:
            return False
        title = html.unescape(m.group(1)).strip().lower()
        return title.startswith("not found") or title.startswith("404")

    def _worker(self, item_type: str):
        """Worker thread that fetches and parses items from work_queue."""
        while not self.stop_event.is_set():
            try:
                item_id = self.work_queue.get(timeout=0.5)
            except queue.Empty:
                break

            url = f"{BASE_URL}/{'video' if item_type == 'movie' else 'performer'}/{item_id}"
            status, html_content = self.fetch_url(url)

            parsed_data = None
            if status == 200:
                if self._is_not_found_page(html_content):
                    status = 404
                else:
                    if item_type == "movie":
                        parsed_data = self.parse_movie(item_id, html_content)
                    else:
                        parsed_data = self.parse_performer(item_id, html_content)
                    # A page we could not parse is a transient failure, NOT proof that
                    # the item does not exist. Recording it as 404 would permanently
                    # blacklist a real movie from ever being fetched again.
                    if not parsed_data:
                        status = 500

            self.result_queue.put((item_type, parsed_data or {"id": item_id}, status))
            self.work_queue.task_done()
            if self.delay > 0:
                time.sleep(self.delay)

    def _db_writer(self):
        """Single thread responsible for writing all results to SQLite to avoid db locks."""
        while not self.stop_event.is_set() or not self.result_queue.empty():
            try:
                item_type, data, status = self.result_queue.get(timeout=0.5)
            except queue.Empty:
                continue

            try:
                if status == 200 and data:
                    if item_type == "movie":
                        self.db.save_movie(data, status=200)
                    else:
                        self.db.save_performer(data, status=200)
                else:
                    self.db.record_progress(item_type, data["id"], status=status)
            except Exception as e:
                sys.stderr.write(f"\n[DB Error] Item #{data.get('id')}: {e}\n")

            with self.lock:
                self.processed_count += 1
                if status == 200:
                    self.ok_count += 1
                elif status == 404:
                    self.not_found_count += 1
                else:
                    self.error_count += 1

            self.result_queue.task_done()

    def _render_progress(self, item_type: str):
        """Print real-time one-line progress bar in terminal."""
        elapsed = time.time() - self.start_time
        rate = self.processed_count / elapsed if elapsed > 0 else 0
        pct = (self.processed_count / self.total_target * 100) if self.total_target > 0 else 0
        rem_sec = (self.total_target - self.processed_count) / rate if rate > 0 else 0
        rem_min = rem_sec / 60
        
        sys.stdout.write(
            f"\r[{item_type.capitalize()}] {self.processed_count}/{self.total_target} ({pct:4.1f}%) | "
            f"Speed: {rate:4.1f} req/s | 200 OK: {self.ok_count} | 404: {self.not_found_count} | "
            f"Err: {self.error_count} | ETA: {rem_min:4.1f}m"
        )
        sys.stdout.flush()

    def scrape_range(
        self,
        item_type: str,
        start_id: int,
        end_id: int,
        limit: int | None = None,
        reverse: bool = False,
        retry_failed: bool = False,
        recheck_404: bool = False,
        known_only: bool = False,
    ):
        print("=" * 70)
        if retry_failed:
            mode_label = "【重试先前失败项 (500)】"
        elif recheck_404:
            mode_label = "【重新核实历史 404 记录】"
        elif known_only:
            mode_label = "【仅抓取数据库中已知的演员 ID】"
        else:
            mode_label = f"[{start_id} -> {end_id}]"
        print(f"🚀 GEVI 批量刮削启动 | 目标: {item_type.upper()} | 范围: {mode_label}")
        print(f"   并发工作池: {self.concurrency} 线程 | 数据库: {self.db_path}")
        print("=" * 70)

        # Undo previously recorded 404s so they are re-examined from scratch.
        if recheck_404:
            cleared = self.db.clear_progress(item_type, (404,))
            print(f"♻️  已撤销 {cleared} 条历史 404 记录，将全部重新核实（真 404 会重新写回）。")

        # Query checkpoint / pending IDs
        print("🔍 正在比对断点进度 (Checkpoints)...")
        if retry_failed:
            pending_ids = sorted(self.db.get_failed_ids(item_type))
            print(f"📋 发现历史失败条目: {len(pending_ids)} 个，准备进行专程稳健重试...")
        elif known_only:
            completed = self.db.get_completed_ids(item_type)
            pending_ids = [i for i in self.db.get_known_performer_ids() if i not in completed]
            print(f"📋 数据库中已知演员 {len(self.db.get_known_performer_ids())} 位，待补全档案 {len(pending_ids)} 位...")
        else:
            pending_ids = self.db.get_pending_ids(item_type, start_id, end_id)

        if reverse:
            pending_ids.reverse()

        if limit and limit > 0:
            pending_ids = pending_ids[:limit]

        self.total_target = len(pending_ids)
        if self.total_target == 0:
            print(f"🎉 目标数据均已完成，无需重复执行！")
            return

        print(f"📋 待抓取总数: {self.total_target} 项 (已自动跳过已完成项)")

        for pid in pending_ids:
            self.work_queue.put(pid)

        self.start_time = time.time()

        # Start writer thread
        writer_thread = threading.Thread(target=self._db_writer, daemon=True)
        writer_thread.start()

        # Start worker pool
        workers: list[threading.Thread] = []
        for _ in range(self.concurrency):
            t = threading.Thread(target=self._worker, args=(item_type,), daemon=True)
            t.start()
            workers.append(t)

        # Monitor progress
        try:
            while any(t.is_alive() for t in workers) or not self.work_queue.empty() or not self.result_queue.empty():
                self._render_progress(item_type)
                time.sleep(0.3)
            self._render_progress(item_type)
            print("\n")
        except KeyboardInterrupt:
            print("\n\n🛑 收到中断信号 (Ctrl+C)，正在优雅停机并写入缓冲数据...")
            self.stop_event.set()
            # Drain remaining items
            for t in workers:
                t.join(timeout=2.0)
            writer_thread.join(timeout=5.0)
            print("✅ 进度断点已安全持久化保存到本地数据库！下次运行自动继续。")
            return

        for t in workers:
            t.join()
        self.stop_event.set()
        writer_thread.join()

        elapsed = time.time() - self.start_time
        print(f"\n✨ 本轮抓取完成！耗时: {elapsed:.2f}秒 | 成功写入: {self.ok_count} | 404不存在: {self.not_found_count}")

def main():
    parser = argparse.ArgumentParser(description="GEVI Industrial Batch Scraper & SQLite FTS5 Builder")
    parser.add_argument("--mode", choices=["movies", "performers"], default="movies", help="Scrape movies or performers")
    parser.add_argument("--start", type=int, default=1, help="Start ID (default: 1)")
    parser.add_argument("--end", type=int, default=76000, help="End ID (default: 76000 for movies, 150000 for performers)")
    parser.add_argument("--limit", type=int, default=None, help="Limit number of items to scrape in this run")
    parser.add_argument("--concurrency", type=int, default=10, help="Number of worker threads (default: 10; each thread reuses one persistent TLS connection)")
    parser.add_argument("--delay", type=float, default=0.02, help="Polite delay between requests per worker (seconds, default: 0.02)")
    parser.add_argument("--db", type=str, default="gevi.db", help="SQLite database path")
    parser.add_argument("--stats", action="store_true", help="Print current database statistics and exit")
    parser.add_argument("--reverse", action="store_true", help="Scrape from latest ID downwards")
    parser.add_argument("--retry-failed", action="store_true", help="Retry scraping failed items (status 500)")
    parser.add_argument("--recheck-404", action="store_true", help="Re-verify IDs previously recorded as 404 and re-scrape any that actually exist")
    parser.add_argument("--known-only", action="store_true", help="For performers: scrape only IDs already known from movie cast lists, instead of scanning a numeric range")

    args = parser.parse_args()

    db_path = str(Path(args.db).resolve())
    if args.stats:
        db = DatabaseManager(db_path)
        stats = db.get_stats()
        print(f"\n📊 【GEVI 离线数据库当前状态统计】({db_path})")
        print(f"  - 影片总数 (Movies):             {stats.get('movies', 0):,}")
        print(f"  - 演员总数 (Performers):         {stats.get('performers', 0):,}")
        print(f"  - 场景总数 (Episodes):           {stats.get('episodes', 0):,}")
        print(f"  - 演员参演映射 (Cast Links):     {stats.get('movie_performers', 0):,}")
        print(f"  - 影片抓取完成数 (200 OK):       {stats.get('movies_scraped_ok', 0):,}")
        print(f"  - 影片空页面数 (404 Not Found):  {stats.get('movies_scraped_404', 0):,}")
        print(f"  - 影片抓取失败数 (500 Error):    {stats.get('movies_failed_500', 0):,}")
        print(f"  - 演员抓取完成数 (200 OK):       {stats.get('performers_scraped_ok', 0):,}")
        print(f"  - 演员头像已收录:                {stats.get('performers_with_image', 0):,}")
        print(f"  - 中文简介已翻译:                {stats.get('translated', 0):,} / {stats.get('translatable', 0):,}"
              f" (待译 {stats.get('pending', 0):,} | 失败 {stats.get('failed', 0):,})")
        print("")
        return

    item_type = "movie" if args.mode == "movies" else "performer"
    scraper = BatchScraper(
        db_path=db_path,
        concurrency=args.concurrency,
        delay=args.delay
    )
    scraper.scrape_range(
        item_type=item_type,
        start_id=args.start,
        end_id=args.end,
        limit=args.limit,
        reverse=args.reverse,
        retry_failed=args.retry_failed,
        recheck_404=args.recheck_404,
        known_only=args.known_only
    )

if __name__ == "__main__":
    main()
