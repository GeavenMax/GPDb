#!/usr/bin/env python3
"""
GEVI Offline Database - Scraper v2

A re-scrape-safe, anti-blocking replacement for batch_scraper.py. It exists to fix
three problems the v1 scraper has in practice:

1. It re-fetches things it already has. v2 selects work by *what the database is
   missing*, not by walking a numeric range, so a resumed run does no duplicate work.

2. It can destroy good data. v1 overwrote every column with whatever the parse
   produced, so one broken regex turned 4,000 correct rows into empty ones. v2
   validates a parse before accepting it and merges rather than replaces
   (see DatabaseManager.save_movie / save_performer).

3. It gets throttled. v1 fired requests as fast as the pool allowed. v2 behaves like
   a browser session (keep-alive, cookies, realistic headers, gzip), paces itself
   with an adaptive rate limiter, and treats a Cloudflare challenge as a signal to
   slow down - never as "this item does not exist".

Modes (pick exactly one):
  gaps          Re-scrape movies already stored but missing key fields. (default)
  new           Walk an ID range and fetch only IDs never attempted.
  failed        Retry items recorded as transient failure (500 / 0).
  recheck-404   Re-verify items previously recorded as 404.
  performers    Scrape performer profiles for known IDs that have none.
  episodes      Re-read films that have no episode rows, for their scene list only.
  refresh       Re-scrape everything older than --min-age-days.
  fix-years     Clear release years that cannot be true. No network access.
  audit         Offline data-quality report. No network access.

Usage:
  python3 scraper_v2.py --audit
  python3 scraper_v2.py --mode gaps --limit 50 --dry-run
  python3 scraper_v2.py --mode gaps --concurrency 6
  python3 scraper_v2.py --mode new --start 1 --end 90000
  python3 scraper_v2.py --mode performers --limit 500
  python3 scraper_v2.py --mode episodes --limit 200 --dry-run
  python3 scraper_v2.py --mode fix-years --apply
  python3 scraper_v2.py --mode directors
  python3 scraper_v2.py --mode directors --apply --verify 20
  python3 scraper_v2.py --mode failures

Zero third-party dependencies: standard library only, per project convention.
"""

from __future__ import annotations

import argparse
import gzip
import html
import http.client
import json
import os
import queue
import random
import re
import shutil
import signal
import ssl
import sys
import threading
import time
import urllib.parse
import zlib
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

from batch_scraper import HOST, NOT_FOUND_MARKER, BatchScraper
from cache_images import hd_url_for
from db_manager import DatabaseManager

BASE_DIR = Path(__file__).resolve().parent
BASE_URL = f"https://{HOST}"

# A real browser's header set. v1 sent a bare handful of headers, which is one of
# the cheapest bot signals a CDN can check.
NAV_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Accept-Encoding": "gzip, deflate",
    "Upgrade-Insecure-Requests": "1",
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "same-origin",
    "Sec-Fetch-User": "?1",
    "sec-ch-ua": '"Chromium";v="128", "Not;A=Brand";v="24", "Google Chrome";v="128"',
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": '"macOS"',
    "Cache-Control": "max-age=0",
    "Connection": "keep-alive",
}

# Markers of a Cloudflare interstitial / managed challenge. These must only ever be
# read as "slow down and retry", never as "the item is gone".
CHALLENGE_MARKERS = (
    "just a moment",
    "cf-challenge",
    "cf_chl_opt",
    "checking your browser",
    "enable javascript and cookies to continue",
    "attention required! | cloudflare",
    "cf-mitigated",
    "ray id",
)

# A challenge page is small. A real movie page is not. Used together with the
# markers so that the word "challenge-platform" appearing in a normal page's
# analytics script does not trigger a false positive (it does appear).
CHALLENGE_MAX_BYTES = 24_000

STOP = threading.Event()


# --------------------------------------------------------------------------
# Session state: cookies and adaptive pacing
# --------------------------------------------------------------------------

class CookieJar:
    """Process-wide cookie store, shared by every worker connection.

    Cloudflare-issued cookies (cf_clearance, __cf_bm) are bound to the client IP,
    not the TCP connection, so all threads should present the same set.
    """

    def __init__(self) -> None:
        self._jar: dict[str, str] = {}
        self._lock = threading.Lock()

    def update_from(self, raw_headers: list[tuple[str, str]]) -> None:
        with self._lock:
            for key, value in raw_headers:
                if key.lower() != "set-cookie":
                    continue
                chunk = value.split(";", 1)[0].strip()
                if "=" not in chunk:
                    continue
                name, _, val = chunk.partition("=")
                name, val = name.strip(), val.strip()
                # Drop an expired cookie rather than replaying a dead session token.
                if not val or val.lower() == "deleted":
                    self._jar.pop(name, None)
                else:
                    self._jar[name] = val

    def header(self) -> str:
        with self._lock:
            return "; ".join(f"{k}={v}" for k, v in self._jar.items())

    def names(self) -> list[str]:
        with self._lock:
            return sorted(self._jar)

    def clear(self) -> None:
        with self._lock:
            self._jar.clear()


class RateLimiter:
    """Global adaptive request pacer (AIMD), shared by all workers.

    Starts conservative, speeds up while responses are clean, and backs off hard on
    any throttle signal. On a suspected challenge it imposes a global cooldown so
    that every worker pauses at once instead of each retrying into the wall.
    """

    def __init__(self, start_rate: float, max_rate: float, min_rate: float = 0.5) -> None:
        self.rate = max(min_rate, min(start_rate, max_rate))
        self.max_rate = max_rate
        self.min_rate = min_rate
        self._next_slot = 0.0
        self._cooldown_until = 0.0
        self._clean_streak = 0
        self._lock = threading.Lock()
        self.throttle_events = 0
        self.cooldowns = 0

    def acquire(self) -> None:
        """Block until this thread may issue its next request."""
        while True:
            with self._lock:
                now = time.monotonic()
                start = max(now, self._cooldown_until)
                wait = max(0.0, self._next_slot - start)
                if wait <= 0.0:
                    self._next_slot = start + (1.0 / self.rate)
                    return
            # A request slot is at most ~2s away at the slowest sane rate.
            time.sleep(min(wait, 0.25))

    def on_clean(self) -> None:
        """A good response: creep the rate back up, but only after a clean streak."""
        with self._lock:
            self._clean_streak += 1
            if self._clean_streak >= 25 and self.rate < self.max_rate:
                self.rate = min(self.max_rate, self.rate * 1.25)
                self._clean_streak = 0

    def on_throttle(self, retry_after: float | None = None) -> float:
        """A throttle signal: halve the rate and pause everyone. Returns the pause."""
        with self._lock:
            self.throttle_events += 1
            self._clean_streak = 0
            self.rate = max(self.min_rate, self.rate * 0.5)
            pause = retry_after if retry_after else min(60.0, 5.0 * (2 ** min(self.cooldowns, 3)))
            self.cooldowns += 1
            self._cooldown_until = max(self._cooldown_until, time.monotonic() + pause)
            # Reset the slot clock so the limiter does not try to "catch up" on the
            # requests it skipped during the cooldown.
            self._next_slot = self._cooldown_until
            return pause

    def snapshot(self) -> str:
        with self._lock:
            return f"{self.rate:.1f} req/s (节流 {self.throttle_events} 次, 冷却 {self.cooldowns} 次)"


# --------------------------------------------------------------------------
# HTTP layer
# --------------------------------------------------------------------------

class BrowserClient:
    """Thread-confined keep-alive HTTPS client that behaves like one browser tab.

    Reusing the TLS connection matters: the v1 notes measured ~0.65 req/s with a
    fresh handshake per request versus ~17 req/s with keep-alive, because the CDN's
    connection-rate limiter reacts to the handshake burst.
    """

    def __init__(self, jar: CookieJar, timeout: float = 25.0):
        self.jar = jar
        self.timeout = timeout
        self._ctx = ssl.create_default_context()
        self._conn: http.client.HTTPSConnection | None = None
        self.requests = 0

    def close(self) -> None:
        conn, self._conn = self._conn, None
        if conn is not None:
            try:
                conn.close()
            except Exception:
                pass

    def _reconnect(self) -> None:
        self.close()
        self._conn = http.client.HTTPSConnection(HOST, timeout=self.timeout, context=self._ctx)

    def get(self, path: str, referer: str | None = None) -> tuple[int, str, str, list[tuple[str, str]]]:
        """One GET. Returns (status, body_text, location, raw_headers)."""
        if self._conn is None:
            self._reconnect()

        headers = dict(NAV_HEADERS)
        if referer:
            headers["Referer"] = referer
        cookie = self.jar.header()
        if cookie:
            headers["Cookie"] = cookie

        try:
            self._conn.request("GET", path, headers=headers)
            resp = self._conn.getresponse()
            raw = resp.read()
            status = resp.status
            location = resp.getheader("Location") or ""
            raw_headers = resp.getheaders()
            # Draining the body is what allows the connection to be reused.
        except Exception:
            self.close()
            raise

        self.requests += 1
        self.jar.update_from(raw_headers)

        encoding = (resp.getheader("Content-Encoding") or "").lower()
        body = self._decode(raw, encoding)
        return status, body, location, raw_headers

    @staticmethod
    def _decode(raw: bytes, encoding: str) -> str:
        try:
            if "gzip" in encoding:
                raw = gzip.decompress(raw)
            elif "deflate" in encoding:
                try:
                    raw = zlib.decompress(raw)
                except zlib.error:
                    raw = zlib.decompress(raw, -zlib.MAX_WBITS)
        except Exception:
            pass  # fall through and decode whatever we got
        return raw.decode("utf-8", errors="ignore")


class FetchResult:
    """Outcome of one fetch, with the reason kept for reporting and retry logic."""

    __slots__ = ("status", "body", "reason")

    def __init__(self, status: int, body: str = "", reason: str = ""):
        self.status = status      # 200 ok | 404 gone | 429/503 throttled | 500 transient | 0 aborted
        self.body = body
        self.reason = reason

    def __repr__(self) -> str:  # pragma: no cover - debugging aid
        return f"<FetchResult {self.status} {self.reason}>"


def parse_retry_after(raw_headers: list[tuple[str, str]]) -> float | None:
    for key, value in raw_headers:
        if key.lower() == "retry-after":
            try:
                return min(300.0, float(value.strip()))
            except ValueError:
                return 30.0
    return None


def looks_like_challenge(status: int, body: str) -> bool:
    if status in (403, 429, 503):
        return True
    if len(body) > CHALLENGE_MAX_BYTES:
        return False
    low = body[:CHALLENGE_MAX_BYTES].lower()
    return any(marker in low for marker in CHALLENGE_MARKERS)


# --------------------------------------------------------------------------
# Progress
# --------------------------------------------------------------------------

class Progress:
    def __init__(self, total: int, label: str):
        self.total = total
        self.label = label
        self.done = 0
        self.ok = 0
        self.gone = 0
        self.failed = 0
        self.skipped = 0
        self.t0 = time.time()
        self._lock = threading.Lock()
        self._last_draw = 0.0

    def tick(self, ok: int = 0, gone: int = 0, failed: int = 0, skipped: int = 0) -> None:
        with self._lock:
            self.done += 1
            self.ok += ok
            self.gone += gone
            self.failed += failed
            self.skipped += skipped
            now = time.time()
            if now - self._last_draw < 0.4 and self.done < self.total:
                return
            self._last_draw = now
            elapsed = now - self.t0
            rate = self.done / elapsed if elapsed > 0 else 0.0
            remain = (self.total - self.done) / rate if rate > 0 else 0.0
            sys.stdout.write(
                f"\r  [{self.done:,}/{self.total:,}] {self.done / self.total * 100 if self.total else 0:5.1f}% | "
                f"新增/更新 {self.ok:,} | 不存在 {self.gone:,} | 失败 {self.failed:,} "
                f"| 跳过 {self.skipped:,} | {rate:4.1f} 条/s | 剩余 {format_duration(remain)}   "
            )
            sys.stdout.flush()

    def finish(self) -> None:
        sys.stdout.write("\n")
        sys.stdout.flush()


def format_duration(seconds: float) -> str:
    seconds = int(max(0, seconds))
    if seconds < 60:
        return f"{seconds}s"
    if seconds < 3600:
        return f"{seconds // 60}m{seconds % 60:02d}s"
    return f"{seconds // 3600}h{(seconds % 3600) // 60:02d}m"


# --------------------------------------------------------------------------
# Content validation - the "already-scraped data must be valid" gate
# --------------------------------------------------------------------------

# Descriptions that are really navigation chrome or a broken template, not prose.
JUNK_DESCRIPTION_RE = re.compile(r"<[a-z/][^>]*>|^\s*(home|login|register|search)\s*$", re.I)


def movie_is_usable(m: dict | None) -> tuple[bool, str]:
    """Decide whether a movie parse is trustworthy enough to write to the database.

    A parse that only recovered a title is a broken parse, not a movie with no
    data, so it is rejected and recorded as a transient failure. Accepting it would
    overwrite real stored values with empty ones.
    """
    if not m:
        return False, "解析失败 (页面结构不符)"
    title = (m.get("title") or "").strip()
    if not title:
        return False, "无标题"
    if title.lower().startswith("404") or "not found" in title.lower():
        return False, "页面是 Not Found"

    signals = {
        "简介": bool((m.get("description") or "").strip()),
        "年份": bool(m.get("release_year")),
        "时长": bool(m.get("duration_mins")),
        "演员表": bool(m.get("performers")),
        "封面": bool(m.get("cover_full") or m.get("cover_icon")),
        "厂牌": bool((m.get("studio_name") or "").strip()),
    }
    if not any(signals.values()):
        return False, "只解析到标题，其余字段全空"
    return True, ""


def performer_is_usable(p: dict | None) -> tuple[bool, str]:
    if not p:
        return False, "解析失败 (页面结构不符)"
    name = (p.get("name") or "").strip()
    if not name:
        return False, "无姓名"
    if name.lower().startswith("404") or "not found" in name.lower():
        return False, "页面是 Not Found"
    attribute_fields = ("hair", "eyes", "body_hair", "facial_hair", "height", "weight",
                        "build", "skin", "dick_size", "foreskin", "tattoos", "notes")
    has_attr = any((p.get(f) or "").strip() for f in attribute_fields)
    if not has_attr and not p.get("image_url"):
        return False, "只解析到姓名，没有任何档案字段"
    return True, ""


# --------------------------------------------------------------------------
# The scraper
# --------------------------------------------------------------------------

class _HtmlParsers(BatchScraper):
    """Borrows batch_scraper's HTML parsers without its network or worker stack.

    Deliberately does not call super().__init__: the parent constructor opens a
    second database connection and a worker pool that v2 does not use. The parse
    methods are self-free, so the instance is only a namespace - if a future edit
    makes a parser touch self, this fails loudly with AttributeError rather than
    silently reading a half-initialised object.
    """

    def __init__(self) -> None:
        pass


def to_path(url: str) -> str:
    parsed = urllib.parse.urlparse(url)
    if parsed.netloc and parsed.netloc != HOST:
        raise ValueError(f"Off-host redirect: {url}")
    path = parsed.path or "/"
    if parsed.query:
        path += "?" + parsed.query
    return path


# --- company episode tables (`coep`) -------------------------------------
#
# A company page's episode grid is a DataTables server-side table; `company2.js`
# points its ajax at `coep` with `CompanyID`. It answers with plain JSON, so this
# is the one place where episodes can be enumerated without visiting a page each.
# The server clamps `length` to 100 — asking for more silently returns 100, which
# would make a page count computed from the requested size loop forever.
COEP_PAGE_SIZE = 100

_EPISODE_LINK_RE = re.compile(r"episode/(\d+)['\"]\s*>(.*?)</a>", re.DOTALL)
_IMG_SRC_RE = re.compile(r"<img[^>]+src=['\"]([^'\"]+)['\"]", re.IGNORECASE)
_PERFORMER_RE = re.compile(
    r"performer/(\d+)['\"][^>]*>(?:<span[^>]*>)?(.*?)(?:</span>)?</a>", re.DOTALL
)


def _text_of(fragment: object) -> str:
    """Strip tags, collapse whitespace, then unescape.

    Unescaping last is deliberate: doing it first would turn a title containing a
    literal `&lt;b&gt;` into a tag for the stripper to eat."""
    if not fragment:
        return ""
    stripped = re.sub(r"<[^>]+>", " ", str(fragment))
    return html.unescape(re.sub(r"\s+", " ", stripped)).strip()


def parse_coep_rows(payload: dict, company_id: int, company_name: str | None) -> list[dict]:
    """Turn one `coep` JSON page into rows for `save_standalone_episodes`.

    Cell layout, measured against the live endpoint:
    `[0]` a constant "0", `[1]` the release date, `[2]` `<a href='episode/N'>Real
    Title</a>`, `[3]` the cast, `[4]` the thumbnail `<img>`, `[5]` the description.

    Read positionally with a length guard, and skip any row without an episode link:
    a short or reshaped row must drop out rather than have its date read as a title.
    """
    rows: list[dict] = []
    for cell in payload.get("data") or []:
        if not isinstance(cell, list) or len(cell) < 5:
            continue
        m = _EPISODE_LINK_RE.search(str(cell[2] or ""))
        if not m:
            continue

        img = _IMG_SRC_RE.search(str(cell[4] or ""))
        thumb = ""
        if img:
            src = img.group(1)
            low_res = src if src.startswith("http") else f"{BASE_URL}/{src.lstrip('/')}"
            # The grid hands back the low-res preview; the episode page opens the HD
            # twin. Storing HD here matches what the movie-page parser now writes.
            thumb = hd_url_for(low_res) or low_res

        performers: list[tuple[int, str]] = []
        for pid, pname in _PERFORMER_RE.findall(str(cell[3] or "")):
            name = _text_of(pname)
            if name:
                performers.append((int(pid), name))

        rows.append({
            "id": int(m.group(1)),
            "title": _text_of(m.group(2)),
            "thumbnail_url": thumb,
            "description": _text_of(cell[5]) if len(cell) > 5 else "",
            "release_date": str(cell[1] or "").strip(),
            "studio_id": company_id,
            "studio_name": company_name,
            "performers": performers,
        })
    return rows


def coep_url(company_id: int, start: int, length: int = COEP_PAGE_SIZE) -> str:
    """One page of a company's episode table, newest first.

    `order[0][dir]=desc` on the date column is what makes an incremental sync
    possible: paging stops as soon as a page contains only episodes already stored.
    """
    query = urllib.parse.urlencode({
        "CompanyID": company_id,
        "draw": 1,
        "start": start,
        "length": length,
        "order[0][column]": 1,
        "order[0][dir]": "desc",
        "search[value]": "",
    })
    return f"{BASE_URL}/coep?{query}"


class ScraperV2:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.db_path = str(Path(args.db).resolve())
        self.db = DatabaseManager(self.db_path)
        self.parsers = _HtmlParsers()
        self.jar = CookieJar()
        self.limiter = RateLimiter(
            start_rate=args.rate, max_rate=args.max_rate, min_rate=args.min_rate
        )
        self.clients = threading.local()
        self.results: queue.Queue[dict] = queue.Queue()
        self.progress: Progress | None = None
        self.consecutive_throttles = 0
        self.throttle_lock = threading.Lock()
        self.write_counts = {"written": 0, "gone": 0, "failed": 0, "rejected": 0,
                             "episodes": 0}
        # Which fields to watch for absence, per item type. Drives both the gap
        # selection and the "source has no value" bookkeeping, so the two can never
        # disagree about what is being looked for.
        if args.mode == "episodes":
            # This mode looks at one field and writes one table. Watching the movie
            # fields here would let a re-fetch that happens to miss, say, a director
            # record a void for it - a verdict this mode never actually checked.
            movie_fields = ["episodes"]
        else:
            movie_fields = [g.strip() for g in
                            (args.gaps or ",".join(DatabaseManager.DEFAULT_MOVIE_GAPS)).split(",")
                            if g.strip()]
        # `company` is empty on purpose. Void bookkeeping means "the source has no value
        # for this field", and a company target has no movie/performer field to be
        # absent - recording one would put rows in the ledger that no other mode can
        # interpret. Its progress rows also live under item_type "company", which keeps
        # this mode's ids out of the movie id space they would otherwise collide with
        # (both are plain INTEGERs, so `get_incomplete_movies` would read them as films).
        self.checked_fields = {"movie": movie_fields, "performer": ["attributes", "image"],
                              "company": []}

    # --- networking -------------------------------------------------------

    def client(self) -> BrowserClient:
        c = getattr(self.clients, "client", None)
        if c is None:
            c = BrowserClient(self.jar, timeout=self.args.timeout)
            self.clients.client = c
        return c

    def warmup(self) -> None:
        """One homepage fetch to pick up session cookies before the real work."""
        if self.args.no_warmup:
            return
        try:
            c = self.client()
            status, _, _, _ = c.get("/")
            names = self.jar.names()
            print(f"🔑 预热完成: HTTP {status} | Cookie: {', '.join(names) if names else '无'}")
        except Exception as e:
            print(f"⚠️  预热请求失败 (继续，不影响后续): {e}", file=sys.stderr)

    def fetch(self, url: str) -> FetchResult:
        """Fetch one page, with retry, backoff and throttle-aware pacing.

        Returns 200 / 404 only when the answer is trustworthy. Anything the site did
        not clearly answer is 500 (transient) and is never recorded as "does not exist".
        """
        try:
            path = to_path(url)
        except ValueError:
            return FetchResult(500, reason="跳转到站外域名")

        referer = f"{BASE_URL}/"
        for attempt in range(self.args.max_retries):
            if STOP.is_set():
                return FetchResult(0, reason="已中断")

            self.limiter.acquire()
            # Jitter keeps many workers from marching in lockstep.
            time.sleep(random.uniform(0.0, self.args.jitter))

            try:
                status, body, location, raw_headers = self.client().get(path, referer=referer)
            except Exception as e:
                self.client().close()
                if attempt < self.args.max_retries - 1:
                    time.sleep(min(20.0, 1.5 * (2 ** attempt)) + random.uniform(0, 0.5))
                    continue
                return FetchResult(500, reason=f"连接失败: {type(e).__name__}")

            if status == 200 and looks_like_challenge(status, body):
                pause = self._throttled("Cloudflare 拦截页", parse_retry_after(raw_headers))
                if STOP.is_set():
                    return FetchResult(0, reason="已中断 (持续被拦截)")
                time.sleep(min(pause, 20.0))
                continue

            # Missing items are answered with a redirect to the 404 page, not a 404 status.
            hops = 0
            while status in (301, 302, 303, 307, 308) and hops < 4:
                if NOT_FOUND_MARKER in location:
                    return FetchResult(404, reason="站点标记为不存在")
                if not location:
                    break
                try:
                    path = to_path(location)
                except ValueError:
                    return FetchResult(500, reason="跳转到站外域名")
                status, body, location, raw_headers = self.client().get(path, referer=referer)
                hops += 1

            if status in (301, 302, 303, 307, 308):
                return FetchResult(500, reason="重定向循环")
            if status in (404, 410):
                return FetchResult(404, reason="HTTP 404")
            if status in (403, 429, 503):
                pause = self._throttled(f"HTTP {status}", parse_retry_after(raw_headers))
                if STOP.is_set():
                    return FetchResult(0, reason="已中断 (持续被限流)")
                time.sleep(min(pause, 20.0))
                continue
            if status >= 500:
                time.sleep(min(20.0, 1.5 * (2 ** attempt)) + random.uniform(0, 0.5))
                continue
            if status == 200:
                self.limiter.on_clean()
                return FetchResult(200, body, "OK")
            return FetchResult(500, reason=f"未预期状态码 {status}")

        return FetchResult(500, reason="重试次数用尽")

    def _throttled(self, why: str, retry_after: float | None) -> float:
        """Record a throttle signal; trip the circuit breaker if it keeps happening."""
        pause = self.limiter.on_throttle(retry_after)
        with self.throttle_lock:
            self.consecutive_throttles += 1
            streak = self.consecutive_throttles
        print(f"\n  ⏸️  {why}：全局降速至 {self.limiter.rate:.1f} req/s，暂停 {pause:.0f}s"
              f" (连续第 {streak} 次)", file=sys.stderr)
        if streak >= self.args.max_throttles:
            print(
                f"\n🛑 连续 {streak} 次被站点拦截，已停止本次任务。\n"
                f"   这不是数据问题，继续跑只会加深封禁。建议：\n"
                f"   1) 等 15-30 分钟再运行；\n"
                f"   2) 降低并发与速率：--concurrency 2 --rate 1 --max-rate 2；\n"
                f"   3) 换网络出口 (手机热点 / 重启路由器) 后重试。\n"
                f"   已抓取的数据已写入，下次运行会自动从断点继续。",
                file=sys.stderr,
            )
            STOP.set()
        return pause

    def _clean_response(self) -> None:
        with self.throttle_lock:
            self.consecutive_throttles = 0

    # --- target selection -------------------------------------------------

    def select_targets(self) -> list[dict]:
        """Build the work list: only what the database is actually missing."""
        mode = self.args.mode
        targets: list[dict] = []

        if mode == "gaps":
            rows = self.db.get_incomplete_movies(
                self.checked_fields["movie"], void_min_attempts=self.args.void_after
            )
            targets = [
                {"type": "movie", "id": r["id"], "url": f"{BASE_URL}/video/{r['id']}",
                 "why": "缺 " + "+".join(r["missing"]), "title": r["title"]}
                for r in rows
            ]

        elif mode == "new":
            if self.args.end <= 0:
                raise SystemExit("❌ --mode new 需要 --start 与 --end 指定 ID 范围")
            known = self.db.get_completed_ids("movie")
            failed = self.db.get_failed_ids("movie")
            done = known | failed
            targets = [
                {"type": "movie", "id": i, "url": f"{BASE_URL}/video/{i}", "why": "未抓取"}
                for i in range(self.args.start, self.args.end + 1) if i not in done
            ]

        elif mode in ("failed", "failures"):
            ids = sorted(self.db.get_failed_ids("movie") | self.db.get_status_ids("movie", 0))
            targets = [
                {"type": "movie", "id": i, "url": f"{BASE_URL}/video/{i}", "why": "上次失败"}
                for i in ids
            ]

        elif mode == "recheck-404":
            targets = [
                {"type": "movie", "id": i, "url": f"{BASE_URL}/video/{i}", "why": "复核 404"}
                for i in self.db.get_404_ids("movie")
            ]

        elif mode == "performers":
            rows = self.db.get_incomplete_performers(
                self.checked_fields["performer"], void_min_attempts=self.args.void_after
            )
            targets = [
                {"type": "performer", "id": r["id"], "url": f"{BASE_URL}/performer/{r['id']}",
                 "why": "缺 " + "+".join(r["missing"]), "title": r["name"]}
                for r in rows
            ]

        elif mode == "episodes":
            # Films with no episode rows at all. Most of these really do have no
            # scene list on their page - `--void-after` fetches decide which, twice by
            # default, and the ones that come back empty both times are recorded as
            # void so they leave the work list instead of growing it.
            rows = self.db.get_incomplete_movies(
                ["episodes"], void_min_attempts=self.args.void_after
            )
            targets = [
                {"type": "movie", "id": r["id"], "url": f"{BASE_URL}/video/{r['id']}",
                 "why": "无分集记录", "title": r["title"]}
                for r in rows
            ]


        elif mode == "episode-sync":
            # Standalone episodes - ones the site publishes with no parent film - are
            # unreachable from the film side: no film page lists them and `/newe` has no
            # pagination. Their companies' episode tables do list them, so the company is
            # the unit of work.
            known = self.db.get_completed_ids("company")
            rows = self.db.conn.execute(
                "SELECT studio_id, COUNT(*) FROM movies "
                "WHERE studio_id IS NOT NULL GROUP BY studio_id ORDER BY studio_id"
            ).fetchall()
            names = dict(self.db.conn.execute(
                "SELECT studio_id, MAX(studio_name) FROM movies "
                "WHERE studio_id IS NOT NULL GROUP BY studio_id"
            ).fetchall())
            targets = [
                {"type": "company", "id": sid, "url": coep_url(sid, 0),
                 "why": "有影片的公司", "title": names.get(sid)}
                for sid, _ in rows if sid not in known
            ]
            if self.args.sweep_companies:
                # A company can publish scenes with no film of its own, so the film side
                # alone cannot enumerate them. `_process_company` always fetches page 0, so
                # that first request doubles as the probe: its `recordsTotal` says whether
                # the company has anything, and a company with none returns an empty page
                # at any `length`. `url` here is for the log line only.
                # One-time backfill, not part of a routine sync - it is ~6k requests.
                have = {sid for sid, _ in rows}
                extra = [
                    cid for cid in range(1, self.args.sweep_companies + 1)
                    if cid not in have and cid not in known
                ]
                targets += [
                    {"type": "company", "id": cid, "url": coep_url(cid, 0),
                     "why": "公司 id 探针", "title": None}
                    for cid in extra
                ]

        elif mode == "refresh":
            cutoff = (datetime.now() - timedelta(days=self.args.min_age_days)).strftime("%Y-%m-%d %H:%M:%S")
            rows = self.db.conn.execute(
                "SELECT id, title FROM movies WHERE scraped_at IS NULL OR scraped_at < ? ORDER BY id",
                (cutoff,),
            ).fetchall()
            targets = [
                {"type": "movie", "id": r[0], "url": f"{BASE_URL}/video/{r[0]}",
                 "why": f"上次抓取早于 {self.args.min_age_days} 天前", "title": r[1]}
                for r in rows
            ]

        else:
            raise SystemExit(f"❌ 未知模式: {mode}")

        if self.args.only:
            wanted = {int(x) for x in self.args.only.split(",") if x.strip()}
            targets = [t for t in targets if t["id"] in wanted]
        if self.args.limit and self.args.limit > 0:
            targets = targets[: self.args.limit]
        return targets

    # --- execution --------------------------------------------------------

    def _report_empty_work_list(self) -> None:
        """Say why the work list came back empty, in the terms of this mode.

        A bare "nothing to do" is indistinguishable from a crash. `episode-sync` needs
        the longer answer more than any other mode: its `--sweep-companies` probes are a
        one-time backfill over the company id space, so the run that finishes them makes
        every later run print an identical, unexplained no-op — which reads as "the
        command died and scraped nothing" to whoever was told there were thousands left.
        """
        if self.args.mode != "episode-sync":
            print("🎉 没有需要抓取的目标：数据库中已有的内容都是完整的。")
            return

        known = self.db.get_completed_ids("company")
        print(f"🎉 没有需要抓取的目标：company 账本已有 {len(known):,} 家，全部完成。")
        if not self.args.sweep_companies:
            print("   公司表里查得到的片商都同步过了。要探片商 id 空间，加 --sweep-companies。")
            return
        print(f"   探针 1..{self.args.sweep_companies:,} 已经全部扫完（含其中不存在的 id）。"
              "这是一次性补齐，重跑不会再有第二轮。")
        print("   要继续只能往更高 id 探：先确认站点上确实还有更高的公司 id，"
              "再调大 --sweep-companies（每个 id = 1 次请求）。")

    def run(self) -> None:
        targets = self.select_targets()
        total = len(targets)

        print("=" * 74)
        print(f"🕷️  GEVI Scraper v2 | 模式: {self.args.mode} | 目标: {total:,} 条")
        print(f"   并发: {self.args.concurrency} | 起始速率: {self.args.rate} req/s "
              f"(上限 {self.args.max_rate}) | 重试: {self.args.max_retries}")
        print("=" * 74)

        if total == 0:
            self._report_empty_work_list()
            return

        self.warmup()

        by_reason: dict[str, int] = {}
        for t in targets:
            by_reason[t["why"]] = by_reason.get(t["why"], 0) + 1
        print("📋 待抓取明细:")
        for reason, count in sorted(by_reason.items(), key=lambda kv: -kv[1])[:8]:
            print(f"   - {reason}: {count:,} 条")
        if self.args.dry_run:
            print("\n⚠️  试运行模式 (--dry-run)：真实抓取并校验，但完全不写数据库。")
            print("   用于确认反爬与解析是否正常，跑完不会留下任何改动。")

        if not (self.args.no_backup or self.args.dry_run):
            self.backup_database()

        self.progress = Progress(total, self.args.mode)
        writer = threading.Thread(target=self._writer, name="db-writer", daemon=True)
        writer.start()

        work: queue.Queue[dict | None] = queue.Queue()
        for t in targets:
            work.put(t)

        def worker() -> None:
            while not STOP.is_set():
                try:
                    item = work.get_nowait()
                except queue.Empty:
                    return
                try:
                    self._process(item)
                except Exception as e:  # a worker must never die silently
                    self.results.put({"kind": "failed", "item": item,
                                      "reason": f"内部错误: {type(e).__name__}: {e}"})
                    if self.progress:
                        self.progress.tick(failed=1)
                finally:
                    work.task_done()

        threads = [threading.Thread(target=worker, name=f"w{i}", daemon=True)
                   for i in range(self.args.concurrency)]
        for th in threads:
            th.start()
        for th in threads:
            th.join()

        # Let the writer drain everything already queued, then stop it.
        while not self.results.empty():
            time.sleep(0.05)
        time.sleep(0.3)
        self.results.put(None)
        writer.join(timeout=30)

        if self.progress:
            self.progress.finish()
        self._report()

    def _abort_company(self, item: dict, res: FetchResult, why: str) -> None:
        """Record a company fetch that did not produce a trustworthy answer.

        Never 404: a company whose listing we failed to read must stay eligible, or the
        episodes behind it would be written off permanently on the strength of one bad
        response.
        """
        if res.status == 0:
            self.results.put({"kind": "aborted", "item": item, "reason": res.reason})
            if self.progress:
                self.progress.tick(skipped=1)
        elif res.status == 404:
            self.results.put({"kind": "gone", "item": item, "reason": res.reason})
            if self.progress:
                self.progress.tick(gone=1)
        else:
            self.results.put({"kind": "failed", "item": item,
                              "reason": f"{why}: {res.reason}"})
            if self.progress:
                self.progress.tick(failed=1)

    def _process_company(self, item: dict) -> None:
        """Page through one company's episode table and hand back every row it lists.

        All-or-nothing per company. A big company is dozens of requests, and treating a
        run that read only some of its pages as done would mark it complete and leave the
        rest of its episodes unreachable forever, since nothing would come back for them.
        """
        company_id = item["id"]
        first = self.fetch(coep_url(company_id, 0))
        if first.status != 200:
            self._abort_company(item, first, "首页失败")
            return
        try:
            payload = json.loads(first.body)
        except ValueError as e:
            # A parse failure is a layout change, not an absent company.
            self.results.put({"kind": "rejected", "item": item,
                              "reason": f"coep 不是 JSON: {e}"})
            if self.progress:
                self.progress.tick(failed=1)
            return

        rows = parse_coep_rows(payload, company_id, item.get("title"))
        total = int(payload.get("recordsTotal") or 0)
        pages = (total + COEP_PAGE_SIZE - 1) // COEP_PAGE_SIZE

        for page in range(1, pages):
            if STOP.is_set():
                self.results.put({"kind": "aborted", "item": item, "reason": "已中断"})
                if self.progress:
                    self.progress.tick(skipped=1)
                return
            res = self.fetch(coep_url(company_id, page * COEP_PAGE_SIZE))
            if res.status != 200:
                self._abort_company(item, res, f"第 {page + 1}/{pages} 页失败")
                return
            try:
                rows += parse_coep_rows(json.loads(res.body), company_id, item.get("title"))
            except ValueError as e:
                self.results.put({"kind": "rejected", "item": item,
                                  "reason": f"第 {page + 1} 页不是 JSON: {e}"})
                if self.progress:
                    self.progress.tick(failed=1)
                return

        self.results.put({"kind": "ok", "item": item,
                          "data": {"episodes": rows}, "records_total": total,
                          "episodes_found": len(rows)})
        if self.progress:
            self.progress.tick(ok=1)

    def _process(self, item: dict) -> None:
        if item["type"] == "company":
            self._process_company(item)
            return
        res = self.fetch(item["url"])
        if res.status == 0:
            self.results.put({"kind": "aborted", "item": item, "reason": res.reason})
            if self.progress:
                self.progress.tick(skipped=1)
            return
        if res.status == 404:
            self.results.put({"kind": "gone", "item": item, "reason": res.reason})
            if self.progress:
                self.progress.tick(gone=1)
            return
        if res.status != 200:
            self.results.put({"kind": "failed", "item": item, "reason": res.reason})
            if self.progress:
                self.progress.tick(failed=1)
            return

        self._clean_response()

        if item["type"] == "movie":
            parsed = self.parsers.parse_movie(item["id"], res.body)
            ok, why = movie_is_usable(parsed)
        else:
            parsed = self.parsers.parse_performer(item["id"], res.body)
            ok, why = performer_is_usable(parsed)

        if not ok:
            # Rejected parses are recorded as transient failures, never as 404:
            # a layout change must not be able to mark a live item as gone.
            self.results.put({"kind": "rejected", "item": item, "reason": why})
            if self.progress:
                self.progress.tick(failed=1)
            return

        msg = {"kind": "ok", "item": item, "data": parsed}
        if self.args.mode == "episodes":
            # The scene list is the only new information this mode is after. A page
            # that lists none is still a successful read - _sync_voids is what
            # remembers that the site has none - so it stays an "ok" either way.
            msg["episodes_found"] = len(parsed.get("episodes") or [])
        self.results.put(msg)
        if self.progress:
            self.progress.tick(ok=1)

    def _writer(self) -> None:
        """Single writer thread: SQLite writes are serialised, so keep them off the workers."""
        while True:
            try:
                msg = self.results.get(timeout=1.0)
            except queue.Empty:
                if STOP.is_set():
                    return
                continue
            if msg is None:
                return
            kind, item = msg["kind"], msg["item"]
            try:
                if self.args.dry_run:
                    # Count outcomes so the report is meaningful, but write nothing.
                    key = {"ok": "written", "gone": "gone", "failed": "failed",
                           "rejected": "rejected"}.get(kind)
                    if key:
                        self.write_counts[key] += 1
                    continue
                if kind == "ok":
                    if item["type"] == "company":
                        # Never writes movie_id: these are standalone episodes, and a row
                        # already attached to a film must keep that attachment.
                        self.db.save_standalone_episodes(msg["data"].get("episodes") or [])
                        # save_movie records progress for a film; nothing does it for a
                        # company, so without this line no company is ever marked done and
                        # every run re-fetches all 2,411 of them from scratch.
                        self.db.record_progress("company", item["id"], 200)
                        self.write_counts["episodes"] += msg.get("episodes_found", 0)
                    elif self.args.mode == "episodes":
                        # Writes the episode rows only: this mode re-reads pages of
                        # films whose own columns are already complete, so save_movie
                        # would rewrite fields nobody asked it to touch.
                        self.db.save_episodes(item["id"], msg["data"].get("episodes") or [])
                        self.write_counts["episodes"] += msg.get("episodes_found", 0)
                    elif item["type"] == "movie":
                        self.db.save_movie(msg["data"], status=200)
                    else:
                        self.db.save_performer(msg["data"], status=200)
                    self.write_counts["written"] += 1
                    self._sync_voids(item)
                elif kind == "gone":
                    self.db.record_progress(item["type"], item["id"], 404)
                    self.write_counts["gone"] += 1
                elif kind in ("failed", "rejected"):
                    self.db.record_progress(item["type"], item["id"], 500)
                    self.write_counts["failed" if kind == "failed" else "rejected"] += 1
                # "aborted" writes nothing: the site never answered, so there is
                # nothing to record and the ID stays eligible for the next run.
            except Exception as e:
                print(f"\n  ⚠️  写库失败 {item['type']}#{item['id']}: {e}", file=sys.stderr)

    def _sync_voids(self, item: dict) -> None:
        """Update the "source has no value for this field" bookkeeping for one item.

        Asked after the row is written: a field still empty now did not come back
        from a page we just fetched, which is the only honest evidence that the
        source lacks it. Fields that did come back have any old void cleared.
        """
        item_type = item["type"]
        if item_type == "company":
            # A company target has no watched field, so there is no absence to record.
            # Writing one would put "the source lacks X" rows in the ledger for an item
            # whose type nothing else in the codebase knows how to interpret.
            return
        try:
            if item_type == "movie":
                state = self.db.movie_field_state(item["id"], self.checked_fields["movie"])
            else:
                state = self.db.performer_field_state(item["id"], self.checked_fields["performer"])
        except ValueError:
            return
        for field, present in state.items():
            if present:
                self.db.clear_void(item_type, item["id"], field)
            else:
                self.db.record_void(item_type, item["id"], field)

    def _report(self) -> None:
        c = self.write_counts
        print("=" * 74)
        verb = "校验通过(未写库)" if self.args.dry_run else "写入/更新"
        print(f"✨ 完成 | {verb} {c['written']:,} | 标记不存在 {c['gone']:,} | "
              f"失败 {c['failed']:,} | 校验未通过 {c['rejected']:,}")
        if self.args.mode == "episodes":
            # The number that decides whether this mode found anything the film mode
            # had missed. A near-zero hit rate on a large run means the page really
            # has no scene list, not that the parser needs another look.
            print(f"   分集: 抓到 {c['episodes']:,} 集 "
                  f"| 本次读取的页面中无分集的会记 void，跑满 --void-after 次后不再重试")
        print(f"   网络: {self.limiter.snapshot()} | 任务{'已被中断 (STOP)' if STOP.is_set() else '正常结束'}")
        if c["rejected"]:
            print("   ⚠️  有页面结构不符合预期（校验未通过）。这些条目未写入，原有数据保持不变。")
            print("      若数量很大，说明站点改版导致解析规则失效，需要更新 batch_scraper 的解析函数。")
        print("=" * 74)

    def backup_database(self) -> None:
        """Copy the database aside before writing, so any run can be rolled back."""
        backup_database(self.db_path, self.db)


# --------------------------------------------------------------------------
# Offline audit
# --------------------------------------------------------------------------

_BACKUP_STAMP_RE = re.compile(r"\d{8}-\d{6}$")


def backup_database(db_path: str, db: DatabaseManager, keep: int = 5) -> None:
    """Copy the database aside before writing, so any run can be rolled back.

    Module level rather than only a method on ScraperV2: the offline repair modes
    write to the database too, and every path that writes should leave the same
    way back. On failure this exits instead of returning — an unbacked-up write is
    the one outcome not worth risking for a repair that can wait.
    """
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    dest = Path(f"{db_path}.backup-{stamp}")
    try:
        db.conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        shutil.copy2(db_path, dest)
        prune_backups(db_path, keep)
        # After the prune, not before: the two lines used to be the other way round, so
        # a prune that removed this very file still printed the success line for it.
        if not dest.exists():
            raise RuntimeError("备份在轮转中被删除")
        size_mb = dest.stat().st_size / 1024 / 1024
        print(f"💾 已备份数据库: {dest.name} ({size_mb:.1f} MB)")
    except Exception as e:
        print(f"⚠️  备份失败: {e}", file=sys.stderr)
        sys.exit("已中止：数据安全优先，请先手动备份。")


def prune_backups(db_path: str, keep: int) -> None:
    """Rotate away this function's own old backups, keeping the newest `keep`.

    Two things it must not do, both of which it used to:

    - Sort by name. A stamp starts with a digit, a hand-named backup starts with a
      letter, and digits sort first — so the file `backup_database` had just written
      compared as the *oldest* of the set and was the first one deleted. The caller
      prints "已备份数据库" before this runs, so the run announced a backup and then
      destroyed it, leaving a batch write with no way back.
    - Touch hand-named backups. `gevi.db.backup-before-titles` is a checkpoint someone
      chose to keep, not a run artifact to rotate; under the old name sort it was one
      of the files that outlived the real one.
    """
    prefix = Path(db_path).name + ".backup-"
    stamped = [
        p for p in Path(db_path).parent.glob(prefix + "*")
        if _BACKUP_STAMP_RE.match(p.name[len(prefix):])
    ]
    # mtime, not name: the name is what the bug above misread, and a backup renamed or
    # copied into place keeps the mtime that says when it was really taken.
    stamped.sort(key=lambda p: p.stat().st_mtime)
    for old in stamped[:-keep]:
        try:
            old.unlink()
        except OSError:
            pass


def run_fix_years(db: DatabaseManager, db_path: str, apply: bool,
                  no_backup: bool = False) -> None:
    """Clear release years that cannot be true, listing every row before touching one.

    Prints by default and writes only under --apply, because the interesting part of
    this repair is *which* rows it hits: a film from 2027 is a data bug, but a real
    upcoming release would look the same at a glance.

    The bad values are not typos in the source. The site prints "?" for a film whose
    release year it does not know, and batch_scraper's Released pattern used to read
    past that "?" into the Vendor ID cell, storing catalogue numbers as years
    ("ZV-1003" -> 1003, "AK-7201" -> 7201). The live page was checked for 68 of the 69
    rows (the 69th would not load): every one prints "?" or nothing at all, so the
    source has no better year to offer and NULL — "unknown" — is the honest repair
    rather than a re-scrape. The parser is fixed in the same change
    (batch_scraper.parse_release_year), so this runs once on what is already stored.
    """
    conn = db.conn
    this_year = datetime.now().year
    rows = conn.execute(
        "SELECT id, release_year, studio_name, title FROM movies "
        "WHERE release_year IS NOT NULL AND (release_year > ? OR release_year < 1900) "
        "ORDER BY release_year DESC",
        (this_year,),
    ).fetchall()

    print("=" * 74)
    print("🗓️  【发行年份除错】")
    print("=" * 74)
    if not rows:
        print(f"\n✅ 没有需要清理的年份（库中不存在 >{this_year} 或 <1900 的年份）。")
        return

    print(f"\n发现 {len(rows):,} 条不可能成立的年份。已核对的页面 Released 栏均为 “?”，"
          f"\n库中的数字实际来自同行的 Vendor ID（货号），并非年份：\n")
    print(f"  {'ID':>7}  {'年份':>5}  {'片商':<22} 片名")
    for mid, year, studio, title in rows:
        print(f"  {mid:>7}  {year:>5}  {(studio or '—')[:22]:<22} {(title or '')[:38]}")

    if not apply:
        print(f"\nℹ️  以上仅为预览，未改动任何数据。执行修复请加 --apply：")
        print(f"     python3 scraper_v2.py --mode fix-years --apply")
        return

    if not no_backup:
        backup_database(db_path, db)
    with conn:
        cur = conn.execute(
            "UPDATE movies SET release_year = NULL "
            "WHERE release_year IS NOT NULL AND (release_year > ? OR release_year < 1900)",
            (this_year,),
        )
    left = conn.execute(
        "SELECT COUNT(*) FROM movies WHERE release_year IS NOT NULL "
        "AND (release_year > ? OR release_year < 1900)", (this_year,)
    ).fetchone()[0]
    nulls = conn.execute("SELECT COUNT(*) FROM movies WHERE release_year IS NULL").fetchone()[0]
    print(f"\n✅ 已清除 {cur.rowcount:,} 条错误年份，改为「未知」。")
    print(f"   残留不可能年份: {left} (应为 0) | 库中无年份影片共 {nulls:,} 条")
    print("   回滚办法：用本次生成的 backups/gevi.db.backup-* 覆盖 gevi.db。")


# --------------------------------------------------------------------------
# 导演分词 (--mode directors)
# --------------------------------------------------------------------------

# A name at or below this length is taken as atomic on sight, and seeds the
# dictionary. Measured against the library: 2,388 of the 3,992 stored values are
# this short, and every one of them that turned out to be a glued pair would have
# to be two sub-8-character names — which the data does not contain.
DIRECTOR_SEED_MAX_LEN = 15
# Nothing longer than this is accepted as one person. The longest real name in the
# library is 30 characters; without the ceiling, a whole 211-character glued string
# could be filed as a single "director".
DIRECTOR_MAX_ATOMIC = 30
# Cost of using a word the dictionary knows, versus one character of a word it does
# not (`len + UNKNOWN_BIAS`). Small enough that a known name always wins over
# leaving a fragment, large enough that a needless split still costs something.
DIRECTOR_KNOWN_COST = 0.01


def _director_full_cover(s: str, words: set[str], max_piece: int) -> list[str] | None:
    """Split `s` into dictionary words with nothing left over, or None.

    This is the test for "is this string several names glued together?" — it only
    answers yes when the *entire* string is accounted for by known names.
    """
    n = len(s)
    reach = [False] * (n + 1)
    prev: list[int | None] = [None] * (n + 1)
    reach[0] = True
    for i in range(n):
        if not reach[i]:
            continue
        for j in range(i + 1, min(n, i + max_piece) + 1):
            if not reach[j] and s[i:j] in words:
                reach[j] = True
                prev[j] = i
    if not reach[n]:
        return None
    out: list[str] = []
    k = n
    while k > 0:
        out.append(s[prev[k]:k])          # type: ignore[index]
        k = prev[k]                       # type: ignore[index]
    return out[::-1]


def build_director_dictionary(names: list[str]) -> tuple[set[str], set[str]]:
    """Grow a dictionary of atomic director names. Returns (seeds, atomics).

    The naive dictionary — every name short enough to be one person — is not enough:
    `Kristofer Weston` (16) and `Steven Scarborough` (18) never appear alone, so they
    are absent from it and get shredded into fragments. Bootstrapping fixes that: a
    long name is treated as atomic unless it can be explained *entirely* as two or
    more names already in the dictionary, and each pass adds the names that pass that
    test, which in turn explains more strings on the next pass. Converges in two.

    The candidate is removed from the dictionary while it is being tested. Otherwise
    the string trivially "covers itself" as one word, every candidate looks glued, and
    55 real glued entries (`Bill ClaytonSteven Scarborough`) survive as atomic names.
    """
    seeds = {n for n in names if len(n) <= DIRECTOR_SEED_MAX_LEN}
    atomics = set(seeds)
    for _ in range(12):
        max_piece = max(len(w) for w in atomics)
        nxt = set(seeds)
        for n in names:
            if n in seeds or len(n) > DIRECTOR_MAX_ATOMIC:
                continue
            pieces = _director_full_cover(n, atomics - {n}, max_piece)
            if pieces is not None and len(pieces) >= 2:
                continue                  # explained as other names -> it is a glue
            nxt.add(n)
        if len(nxt) == len(atomics):
            return seeds, nxt
        atomics = nxt
    return seeds, atomics


def segment_director_name(s: str, words: set[str], max_piece: int) -> list[tuple[str, bool]]:
    """Hardest-working split of one stored value: [(piece, is_known_name)].

    A shortest-path DP rather than greedy longest-match, because greedy mis-splits
    names that contain a lowercase particle or an initial — it takes `Peter de` as
    one unit if that is in the dictionary and strands `Rome`. The cost is what makes
    the choice: reaching a known name costs almost nothing, so the path prefers few
    unknown fragments first and few cuts second.
    """
    n = len(s)
    INF = float("inf")
    cost = [INF] * (n + 1)
    prev: list[int | None] = [None] * (n + 1)
    known = [False] * (n + 1)
    cost[0] = 0.0
    for i in range(n):
        if cost[i] == INF:
            continue
        for j in range(i + 1, min(n, i + max_piece) + 1):
            piece = s[i:j]
            hit = piece in words
            c = cost[i] + (DIRECTOR_KNOWN_COST if hit else len(piece) + DIRECTOR_KNOWN_COST)
            if c < cost[j] - 1e-9:
                cost[j] = c
                prev[j] = i
                known[j] = hit
    if cost[n] == INF:
        return [(s, False)]
    out: list[tuple[str, bool]] = []
    k = n
    while k > 0:
        out.append((s[prev[k]:k], known[k]))   # type: ignore[index]
        k = prev[k]                            # type: ignore[index]
    return out[::-1]


def split_director_value(value: str, words: set[str], max_piece: int) -> tuple[list[str], bool]:
    """One stored `director_name` -> ([names], confident?).

    A value written by the fixed parser already reads "A / B / C" and is split on the
    separator rather than guessed at — the parse is authoritative, and that is exactly
    why this mode can be run again while a scrape is in progress without damaging the
    films the scrape has already done properly. Everything else is a value from before
    the parser was fixed and has to be guessed at with the DP.

    A single name with no separator still goes through the DP; it just comes back as
    one piece, so the guess is only ever "which names are in here", never "is this one
    name or several".
    """
    if " / " in value:
        return [p.strip() for p in value.split(" / ") if p.strip()], True
    pieces = segment_director_name(value, words, max_piece)
    return [p.strip() for p, _ in pieces if p.strip()], all(k for _, k in pieces)


def run_directors(db: DatabaseManager, db_path: str, apply: bool,
                  verify: int = 0, no_backup: bool = False) -> None:
    """Rebuild the director tables from what the movie rows already hold.

    Offline by default: the stored director values are enough to recover the names,
    because the site's own markup separated them and only the old parse glued them
    together. `--verify N` goes to the network to check the guess against N real
    pages, which is the only way to put a number on the accuracy.

    Two kinds of row are treated differently, and the difference is what makes this
    safe to re-run while a scrape is in progress:

      * a name already written as "A / B" came from the fixed parser, so it is truth —
        its names seed the dictionary, and the film is left to the scraper's own
        `movie_directors` rows rather than re-guessed from the string;
      * anything else is a value from before the fix, and gets split by the DP.

    Writes only under --apply, and only to the two new tables — `movies.director_name`
    is left exactly as it is, so rolling back is `DELETE FROM movie_directors`.
    """
    conn = db.conn
    all_values = [r[0] for r in conn.execute(
        "SELECT DISTINCT director_name FROM movies "
        "WHERE director_name IS NOT NULL AND trim(director_name) <> ''"
    )]
    glued_values = [v for v in all_values if " / " not in v]
    # Names the parser already resolved authoritatively. Only whole names, never the
    # combined string, go in: "A / B" must not enter the dictionary as one entry.
    known = {p.strip() for v in all_values if " / " in v
             for p in v.split(" / ") if p.strip()}
    total_movies = conn.execute(
        "SELECT COUNT(*) FROM movies WHERE director_name IS NOT NULL "
        "AND trim(director_name) <> ''"
    ).fetchone()[0]
    glued_movies = conn.execute(
        "SELECT COUNT(*) FROM movies WHERE director_name IS NOT NULL "
        "AND trim(director_name) <> '' AND director_name NOT LIKE '% / %'"
    ).fetchone()[0]

    print("=" * 74)
    print("🎬 【导演分词】把粘连的导演串还原成独立导演")
    print("=" * 74)
    print(f"\n库中有导演字段的影片 {total_movies:,} 条，去重后 {len(all_values):,} 个不同的值。")
    print(f"  其中 {glued_movies:,} 条是修复前抓的粘连串（本次要拆的目标），"
          f"{total_movies - glued_movies:,} 条已由新解析器写好，原样保留。")
    if known:
        print(f"  新解析器已确认的独立导演名 {len(known):,} 个，直接作为词典种子。")

    seeds, words = build_director_dictionary(list(glued_values) + sorted(known))
    words |= known
    seeds |= known
    max_piece = max(len(w) for w in words)
    print(f"分词词典: 种子 {len(seeds):,} 个 (长度 ≤{DIRECTOR_SEED_MAX_LEN} 或已确认) "
          f"→ 自举后 {len(words):,} 个原子名 (最长 {max_piece} 字符)")

    # Split every distinct glued value once, then reuse it for each film.
    splits: dict[str, list[str]] = {}
    unsure: list[tuple[str, list[str]]] = []
    for v in glued_values:
        names, confident = split_director_value(v, words, max_piece)
        splits[v] = names
        if not confident:
            unsure.append((v, names))

    recovered = {n for names in splits.values() for n in names} | known
    multi = sum(1 for names in splits.values() if len(names) > 1)
    print(f"拆出 {len(recovered):,} 个不同的导演；{multi:,} 个粘连串含多个导演。")
    print(f"⚠️  低置信度值 {len(unsure):,} 个（占粘连串 "
          f"{100*len(unsure)/max(1,len(glued_values)):.1f}%），已写入待复核清单。")

    print("\n--- 最长的几条粘连串 ---")
    for v in sorted(glued_values, key=len, reverse=True)[:3]:
        print(f"\n  {v[:150]}{'…' if len(v) > 150 else ''}")
        for name in splits[v]:
            print(f"     → {name}")

    review_path = BASE_DIR / "logs" / "director_segmentation_review.txt"
    if apply:
        review_path.parent.mkdir(exist_ok=True)
        with open(review_path, "w", encoding="utf-8") as f:
            f.write("# 导演分词待复核清单\n")
            f.write(f"# 共 {len(unsure)} 条。每行: 原始值\t拆出的名字(用 | 分隔)\n")
            f.write("# 词典外的片段多为「从未单独出现过」的真名（如 Veronique De Paul），\n")
            f.write("# 少量来自源头本身就残破的行（如 'oflixBarranco Deeick'）——后者可忽略。\n\n")
            for v, names in sorted(unsure):
                f.write(f"{v}\t{' | '.join(names)}\n")

    if not apply:
        print(f"\nℹ️  以上仅为预览，未改动任何数据。落库请加 --apply：")
        print(f"     python3 scraper_v2.py --mode directors --apply")
        print(f"   待复核清单届时写入: {review_path}")
        return

    if not no_backup:
        backup_database(db_path, db)

    # Only the glued rows. A film the fixed parser has already been through has exact
    # links, written from the page with real site IDs — re-deriving them from the
    # string would be a downgrade, and would race the scrape that is still running.
    links = [
        (mid, splits[value])
        for mid, value in conn.execute(
            "SELECT id, director_name FROM movies "
            "WHERE director_name IS NOT NULL AND trim(director_name) <> '' "
            "AND director_name NOT LIKE '% / %'"
        )
    ]
    db.replace_movie_directors(links)
    db.refresh_director_counts()

    n_directors = conn.execute("SELECT COUNT(*) FROM directors").fetchone()[0]
    n_links = conn.execute("SELECT COUNT(*) FROM movie_directors").fetchone()[0]
    no_link = conn.execute(
        "SELECT COUNT(*) FROM movies m WHERE m.director_name IS NOT NULL "
        "AND trim(m.director_name) <> '' AND NOT EXISTS "
        "(SELECT 1 FROM movie_directors md WHERE md.movie_id = m.id)"
    ).fetchone()[0]
    print(f"\n✅ 已重建 {len(links):,} 部影片的导演关联；"
          f"库中共 {n_directors:,} 位导演、{n_links:,} 条关联。")
    if no_link:
        print(f"   ℹ️  {no_link:,} 部有导演名的影片目前没有关联行 —— "
              f"它们是在本次运行之后才入库的，等下一次重跑补上。")
    print(f"   待复核清单: {review_path}")
    print("   回滚办法：DELETE FROM movie_directors; DELETE FROM directors;")
    print("   （movies.director_name 全程未被改动）")

    if verify:
        verify_director_splits(db, verify)


def verify_director_splits(db: DatabaseManager, n: int) -> None:
    """Re-fetch N multi-director pages and compare the site's cast list to ours.

    The segmentation is a guess about strings we never saw the markup for. This is
    the check: the pages still list every director as its own `<a>`, so re-reading a
    sample gives the answer key outright, and the two lists can be compared directly.
    """
    rows = db.conn.execute(
        "SELECT md.movie_id, count(*) c FROM movie_directors md "
        "GROUP BY md.movie_id HAVING c > 1 ORDER BY c DESC LIMIT ?", (n,)
    ).fetchall()
    if not rows:
        print("\n⚠️  没有多导演影片可供抽样校验。")
        return

    print(f"\n--- 抽样重抓校验 ({len(rows)} 部多导演影片) ---")
    parsers = _HtmlParsers()
    jar = CookieJar()
    client = BrowserClient(jar)
    try:
        client.get("/")
    except Exception as e:
        print(f"⚠️  预热失败，跳过校验: {e}", file=sys.stderr)
        return

    exact = partial = miss = 0
    for movie_id, _count in rows:
        try:
            status, body, _, _ = client.get(f"/video/{movie_id}", referer=f"{BASE_URL}/")
        except Exception as e:
            print(f"  {movie_id:>7}  抓取失败: {e}")
            miss += 1
            continue
        if status != 200 or not body:
            print(f"  {movie_id:>7}  HTTP {status}")
            miss += 1
            continue

        parsed = parsers.parse_movie(movie_id, body)
        truth = [name for _, name in (parsed or {}).get("directors", [])]
        mine = [r[0] for r in db.conn.execute(
            "SELECT d.name FROM movie_directors md JOIN directors d ON d.id = md.director_id "
            "WHERE md.movie_id = ? ORDER BY md.position", (movie_id,)
        )]
        if [t.strip() for t in truth] == mine:
            exact += 1
            mark = "✅"
        else:
            overlap = len(set(truth) & set(mine))
            if overlap:
                partial += 1
                mark = "🟡"
            else:
                miss += 1
                mark = "❌"
        print(f"  {movie_id:>7} {mark} 站点 {len(truth)} 人 / 我方 {len(mine)} 人")
        if mark != "✅":
            print(f"            站点: {' / '.join(truth)}")
            print(f"            我方: {' / '.join(mine)}")

    graded = exact + partial + miss
    if graded:
        print(f"\n校验结果: 完全一致 {exact}/{graded} ({100*exact/graded:.0f}%)，"
              f"部分重合 {partial}，完全不符 {miss}")
    print("   注：站点名单是标准答案，不一致说明该条分词有误，需人工看过再决定是否改词典。")


def run_audit(db: DatabaseManager) -> None:
    """Report what the database actually contains, without touching the network."""
    conn = db.conn
    total = conn.execute("SELECT COUNT(*) FROM movies").fetchone()[0]
    print("=" * 74)
    print("🔍 【离线数据体检】(不联网)")
    print("=" * 74)

    def count(sql: str, *args: Any) -> int:
        return conn.execute(sql, args).fetchone()[0]

    print(f"\n📼 影片: {total:,} 条")
    checks = {
        "缺简介": "description IS NULL OR trim(description) = ''",
        "简介过短 (<20字符)": "description IS NOT NULL AND length(trim(description)) < 20",
        "简介含 HTML 标签": "description LIKE '%<%>%'",
        "缺年份": "release_year IS NULL",
        "缺时长": "duration_mins IS NULL",
        "缺封面": "(cover_full IS NULL OR cover_full = '') AND (cover_icon IS NULL OR cover_icon = '')",
        # Not a defect on its own - these films were scraped before cover variants were
        # recorded at all. Listed so the cover bookkeeping can be seen closing over time.
        "封面变体未记录": """(COALESCE(covers_json, '') = ''
                            AND (COALESCE(cover_full, '') <> '' OR COALESCE(cover_icon, '') <> ''))""",
        "缺演员表": "NOT EXISTS (SELECT 1 FROM movie_performers mp WHERE mp.movie_id = movies.id)",
        "缺厂牌": "studio_name IS NULL OR trim(studio_name) = ''",
        "标题疑似错误页": "title LIKE '404%' OR lower(title) LIKE '%not found%'",
        "封面地址非法": "cover_full IS NOT NULL AND cover_full != '' AND cover_full NOT LIKE 'http%'",
    }
    for label, cond in checks.items():
        n = count(f"SELECT COUNT(*) FROM movies WHERE {cond}")
        flag = "⚠️ " if n else "✅ "
        print(f"   {flag}{label}: {n:,}")

    # Cover art inventory. cover_back is only written once the cover gallery has been
    # read, so NULL is exactly "not confirmed yet" (see schema.sql) and this line is
    # the number a later bulk cover download would have to fetch.
    has_front = count("SELECT COUNT(*) FROM movies WHERE COALESCE(cover_full,'') <> ''")
    back_yes = count("SELECT COUNT(*) FROM movies WHERE COALESCE(cover_back,'') <> ''")
    back_no = count("SELECT COUNT(*) FROM movies WHERE cover_back = ''")
    back_unknown = count("""SELECT COUNT(*) FROM movies WHERE COALESCE(cover_full,'') <> ''
                            AND cover_back IS NULL""")
    print(f"   ℹ️ 封面: 有正面 {has_front:,} | 其中封底 已确认有 {back_yes:,} / "
          f"已确认无 {back_no:,} / 待确认 {back_unknown:,}")

    missing_any = len(db.get_incomplete_movies())
    print(f"   → 至少缺一项关键字段: {missing_any:,} 条 (这些是 --mode gaps 的目标)")

    label_map = {"description": "简介", "year": "年份", "duration": "时长",
                 "cover": "封面", "cast": "演员表", "studio": "厂牌", "director": "导演"}
    voids = db.void_summary("movie")
    if voids:
        print("\n🚫 源头确实没有的数据 (已确认，不再重复抓取):")
        for field, n in voids.items():
            share = n / total * 100 if total else 0
            print(f"   - {label_map.get(field, field)}: {n:,} 条 ({share:.0f}% 的影片)")
            if share > 80:
                print(f"     ⚠️  比例异常高。若该字段本应普遍存在，更可能是解析规则失效，"
                      f"请先跑 --mode gaps --limit 20 --dry-run 检查。")
        print("   如需重新尝试这些字段: python3 scraper_v2.py --forget-voids")

    performers = count("SELECT COUNT(*) FROM performers")
    no_profile = len(db.get_incomplete_performers(["attributes"]))
    no_image = count("SELECT COUNT(*) FROM performers WHERE image_url IS NULL OR image_url = ''")
    print(f"\n👤 演员: {performers:,} 条")
    print(f"   ⚠️ 无任何档案属性: {no_profile:,}  (--mode performers 的目标)")
    print(f"   ℹ️ 无头像: {no_image:,}")
    pvoids = db.void_summary("performer")
    if pvoids:
        print(f"   🚫 已确认源头无档案: {pvoids.get('attributes', 0):,} 条，"
              f"源头无头像: {pvoids.get('image', 0):,} 条")

    print("\n📊 抓取进度表 (scrape_progress):")
    rows = conn.execute(
        "SELECT item_type, status, COUNT(*) FROM scrape_progress GROUP BY item_type, status ORDER BY item_type, status"
    ).fetchall()
    labels = {200: "成功", 404: "不存在", 500: "失败", 0: "中断"}
    for item_type, status, n in rows:
        print(f"   {item_type:10s} {labels.get(status, status)!s:6s}: {n:,}")

    print("\n🌐 翻译进度:")
    s = db.get_translation_stats()
    pct = (s["translated"] / s["translatable"] * 100) if s["translatable"] else 0.0
    print(f"   可翻译 {s['translatable']:,} | 已译 {s['translated']:,} ({pct:.1f}%) | "
          f"待译 {s['pending']:,} | 失败 {s['failed']:,}")
    print("=" * 74)


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="GEVI Scraper v2 — 不重复、不破坏数据、不被反爬拦截",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--mode", default="gaps",
                        choices=["gaps", "new", "failed", "failures", "recheck-404",
                                 "performers", "episodes", "episode-sync", "refresh",
                                 "fix-years", "audit", "directors"],
                        help="抓取模式 (默认 gaps: 只补齐缺失字段；episode-sync: 抓片商分集表里的"
                             "独立分集；directors: 离线拆分粘连的导演名)")
    parser.add_argument("--db", default="gevi.db", help="SQLite 数据库路径")
    parser.add_argument("--gaps", help="--mode gaps 时检查哪些字段, 逗号分隔 "
                                      "(description,year,duration,cover,cover_variants,cast,studio,director)。"
                                      "cover_variants = 有封面但没记录封面变体(即封底未确认)")
    parser.add_argument("--start", type=int, default=1, help="--mode new 的起始 ID")
    parser.add_argument("--end", type=int, default=0, help="--mode new 的结束 ID")
    parser.add_argument("--only", help="只处理这些 ID (逗号分隔)，用于小范围试跑")
    parser.add_argument("--limit", type=int, default=0, help="本次最多处理多少条 (0=不限)")
    parser.add_argument("--min-age-days", type=int, default=180, help="--mode refresh 的过期天数")
    parser.add_argument("--sweep-companies", type=int, default=0, metavar="MAX_ID",
                        help="--mode episode-sync 时额外探测 1..MAX_ID 里没有影片的公司 id。"
                             "一次性回填用（约 6k 请求）；日常增量同步不要带")
    parser.add_argument("--audit", action="store_true", help="等于 --mode audit：离线体检，不联网")
    parser.add_argument("--void-after", type=int, default=2,
                        help="某字段连续多少次抓取仍为空后，认定源头确实没有该数据 (默认 2)")
    parser.add_argument("--forget-voids", action="store_true",
                        help="清空『源头没有该字段』的记录后退出（网站补录数据后用）")

    net = parser.add_argument_group("网络与反爬")
    net.add_argument("--concurrency", type=int, default=4, help="并发连接数 (默认 4，越大越容易被拦)")
    net.add_argument("--rate", type=float, default=3.0, help="起始速率 req/s (默认 3.0)")
    net.add_argument("--max-rate", type=float, default=8.0, help="自适应加速上限 req/s (默认 8.0)")
    net.add_argument("--min-rate", type=float, default=0.4, help="降速下限 req/s")
    net.add_argument("--jitter", type=float, default=0.25, help="每请求随机抖动上限秒数")
    net.add_argument("--timeout", type=float, default=25.0, help="单请求超时秒数")
    net.add_argument("--max-retries", type=int, default=4, help="单条最大重试次数")
    net.add_argument("--max-throttles", type=int, default=8,
                     help="连续被拦截多少次后中止本次任务 (默认 8)")
    net.add_argument("--no-warmup", action="store_true", help="跳过开头的 Cookie 预热请求")

    safe = parser.add_argument_group("安全")
    safe.add_argument("--dry-run", action="store_true", help="只抓取校验，不写数据库")
    safe.add_argument("--no-backup", action="store_true", help="运行前不自动备份数据库")
    safe.add_argument("--apply", action="store_true",
                      help="真正执行 --mode fix-years 的修复 / --mode directors 的落库 "
                           "(不加则只列出将要改动的行)")
    safe.add_argument("--verify", type=int, default=0, metavar="N",
                      help="--mode directors 落库后，抽样重抓 N 部多导演影片核对分词准确率")

    args = parser.parse_args()

    db_path = str(Path(args.db).resolve())
    db = DatabaseManager(db_path)

    if args.forget_voids:
        n = db.clear_voids()
        print(f"🧹 已清空 {n:,} 条『源头无此字段』记录；下次运行会重新尝试抓取这些字段。")
        return

    if args.mode == "audit" or args.audit:
        run_audit(db)
        return

    if args.mode == "fix-years":
        run_fix_years(db, db_path, apply=args.apply, no_backup=args.no_backup)
        return

    if args.mode == "directors":
        run_directors(db, db_path, apply=args.apply, verify=args.verify,
                      no_backup=args.no_backup)
        return

    def on_sigint(signum, frame):
        if STOP.is_set():
            print("\n强制退出。", file=sys.stderr)
            os._exit(130)
        print("\n\n⏹️  收到中断信号：停止派发新任务，已抓到的数据正在写库…", file=sys.stderr)
        STOP.set()

    signal.signal(signal.SIGINT, on_sigint)
    signal.signal(signal.SIGTERM, on_sigint)

    scraper = ScraperV2(args)
    scraper.run()


if __name__ == "__main__":
    main()
