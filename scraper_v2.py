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
  refresh       Re-scrape everything older than --min-age-days.
  audit         Offline data-quality report. No network access.

Usage:
  python3 scraper_v2.py --audit
  python3 scraper_v2.py --mode gaps --limit 50 --dry-run
  python3 scraper_v2.py --mode gaps --concurrency 6
  python3 scraper_v2.py --mode new --start 1 --end 90000
  python3 scraper_v2.py --mode performers --limit 500
  python3 scraper_v2.py --mode failures

Zero third-party dependencies: standard library only, per project convention.
"""

from __future__ import annotations

import argparse
import gzip
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
        self.write_counts = {"written": 0, "gone": 0, "failed": 0, "rejected": 0}
        # Which fields to watch for absence, per item type. Drives both the gap
        # selection and the "source has no value" bookkeeping, so the two can never
        # disagree about what is being looked for.
        movie_fields = [g.strip() for g in
                        (args.gaps or ",".join(DatabaseManager.DEFAULT_MOVIE_GAPS)).split(",")
                        if g.strip()]
        self.checked_fields = {"movie": movie_fields, "performer": ["attributes", "image"]}

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

    def run(self) -> None:
        targets = self.select_targets()
        total = len(targets)

        print("=" * 74)
        print(f"🕷️  GEVI Scraper v2 | 模式: {self.args.mode} | 目标: {total:,} 条")
        print(f"   并发: {self.args.concurrency} | 起始速率: {self.args.rate} req/s "
              f"(上限 {self.args.max_rate}) | 重试: {self.args.max_retries}")
        print("=" * 74)

        if total == 0:
            print("🎉 没有需要抓取的目标：数据库中已有的内容都是完整的。")
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

    def _process(self, item: dict) -> None:
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

        self.results.put({"kind": "ok", "item": item, "data": parsed})
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
                    if item["type"] == "movie":
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
        print(f"   网络: {self.limiter.snapshot()} | 任务{'已被中断 (STOP)' if STOP.is_set() else '正常结束'}")
        if c["rejected"]:
            print("   ⚠️  有页面结构不符合预期（校验未通过）。这些条目未写入，原有数据保持不变。")
            print("      若数量很大，说明站点改版导致解析规则失效，需要更新 batch_scraper 的解析函数。")
        print("=" * 74)

    def backup_database(self) -> None:
        """Copy the database aside before writing, so any run can be rolled back."""
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        dest = Path(f"{self.db_path}.backup-{stamp}")
        try:
            self.db.conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            shutil.copy2(self.db_path, dest)
            size_mb = dest.stat().st_size / 1024 / 1024
            print(f"💾 已备份数据库: {dest.name} ({size_mb:.1f} MB)")
            self._prune_backups(keep=5)
        except Exception as e:
            print(f"⚠️  备份失败: {e}", file=sys.stderr)
            sys.exit("已中止：数据安全优先，请先手动备份。")

    def _prune_backups(self, keep: int) -> None:
        backups = sorted(Path(self.db_path).parent.glob(Path(self.db_path).name + ".backup-*"))
        for old in backups[:-keep]:
            try:
                old.unlink()
            except OSError:
                pass


# --------------------------------------------------------------------------
# Offline audit
# --------------------------------------------------------------------------

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
                                 "performers", "refresh", "audit"],
                        help="抓取模式 (默认 gaps: 只补齐缺失字段)")
    parser.add_argument("--db", default="gevi.db", help="SQLite 数据库路径")
    parser.add_argument("--gaps", help="--mode gaps 时检查哪些字段, 逗号分隔 "
                                      "(description,year,duration,cover,cover_variants,cast,studio,director)。"
                                      "cover_variants = 有封面但没记录封面变体(即封底未确认)")
    parser.add_argument("--start", type=int, default=1, help="--mode new 的起始 ID")
    parser.add_argument("--end", type=int, default=0, help="--mode new 的结束 ID")
    parser.add_argument("--only", help="只处理这些 ID (逗号分隔)，用于小范围试跑")
    parser.add_argument("--limit", type=int, default=0, help="本次最多处理多少条 (0=不限)")
    parser.add_argument("--min-age-days", type=int, default=180, help="--mode refresh 的过期天数")
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

    args = parser.parse_args()

    db = DatabaseManager(str(Path(args.db).resolve()))

    if args.forget_voids:
        n = db.clear_voids()
        print(f"🧹 已清空 {n:,} 条『源头无此字段』记录；下次运行会重新尝试抓取这些字段。")
        return

    if args.mode == "audit" or args.audit:
        run_audit(db)
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
