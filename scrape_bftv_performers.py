#!/usr/bin/env python3
"""
scrape_bftv_performers.py
=========================
批量提取演员在 BoyfriendTV 上的个人主页 URL，保存到数据库 performers.bftv_url 列。

用法
----
  python scrape_bftv_performers.py                         # 使用默认数据库路径
  python scrape_bftv_performers.py --db /path/to/gevi.db   # 指定数据库
  python scrape_bftv_performers.py --limit 50              # 只处理前 50 个未爬取的演员
  python scrape_bftv_performers.py --delay 1.5             # 请求间隔秒数（默认 1.0）
  python scrape_bftv_performers.py --concurrency 3         # 并发线程数（默认 2）
  python scrape_bftv_performers.py --overwrite             # 重新爬取已有 bftv_url 的演员

工作流程
--------
1. 从数据库读取 performers 列表（默认跳过已有 bftv_url 的行）。
2. 对每个演员，向 BFTV 搜索端点发起 GET 请求。
3. 用正则从 HTML 提取第一个演员卡片的个人主页链接（/pornstars/<slug>-<id>/）。
4. 将链接写入 performers.bftv_url，同时打印进度。
5. 支持断点续爬（下次运行自动跳过已填充的行）。

注意事项
--------
- 请求间隔默认 1 秒，避免给对方服务器造成压力。
- 脚本使用标准 User-Agent 模拟浏览器。
- 如果搜索结果页无演员卡片，bftv_url 不会被更新（保持 NULL），
  下次运行会再次尝试，除非加上 --mark-notfound 标志写入占位符。
"""

import argparse
import os
import re
import sqlite3
import sys
import time
import urllib.request
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from threading import Lock

# ── 默认数据库路径（与客户端一致）──────────────────────────────────────────────
DEFAULT_DB = Path.home() / "Documents" / "GPDb" / "gevi.db"

# ── BFTV 搜索端点模板 ─────────────────────────────────────────────────────────
SEARCH_URL = (
    "https://www.boyfriendtv.com/pornstars/"
    "?modelsearchSubmitCheck=FORM_SENDED"
    "&key=models&mode=model-search"
    "&q={q}"
    "&submitModelSearch=Search"
    "&filterCountry=&filterHair=&filterEthnicity="
    "&filterEyes=&filterPenis=&filterBreast="
)

# ── 个人主页链接正则（匹配 /pornstars/<slug>-<digits>/）────────────────────────
PROFILE_RE = re.compile(r'href="(/pornstars/[a-z0-9\-]+-\d+/)"', re.IGNORECASE)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/125.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
}


def build_search_url(name: str) -> str:
    parts = name.strip().split()
    q = urllib.parse.quote("+".join(parts), safe="+")
    return SEARCH_URL.format(q=q)


def fetch_profile_url(name: str, delay: float) -> str | None:
    """
    向 BFTV 搜索页发请求，提取第一个演员主页链接。
    返回完整 URL（例如 https://www.boyfriendtv.com/pornstars/alex-kof-5862/）或 None。
    """
    url = build_search_url(name)
    req = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            html = resp.read().decode("utf-8", errors="replace")
    except Exception as exc:
        print(f"  ⚠ 网络错误「{name}」: {exc}", flush=True)
        return None
    finally:
        time.sleep(delay)

    matches = PROFILE_RE.findall(html)
    if not matches:
        return None

    path = matches[0]  # 取第一个结果
    return f"https://www.boyfriendtv.com{path}"


def load_performers(conn: sqlite3.Connection, limit: int | None, overwrite: bool) -> list[tuple[int, str]]:
    """从数据库加载待处理演员列表。"""
    if overwrite:
        sql = "SELECT id, name FROM performers ORDER BY id ASC"
    else:
        sql = "SELECT id, name FROM performers WHERE bftv_url IS NULL ORDER BY id ASC"
    if limit:
        sql += f" LIMIT {limit}"
    return conn.execute(sql).fetchall()


def ensure_bftv_column(conn: sqlite3.Connection) -> None:
    """确保 bftv_url 列存在（兼容旧数据库）。"""
    cols = [row[1] for row in conn.execute("PRAGMA table_info(performers)")]
    if "bftv_url" not in cols:
        conn.execute("ALTER TABLE performers ADD COLUMN bftv_url TEXT")
        conn.commit()
        print("✓ 已为 performers 表添加 bftv_url 列", flush=True)


def run(args: argparse.Namespace) -> None:
    db_path = Path(args.db)
    if not db_path.exists():
        sys.exit(f"❌ 数据库不存在: {db_path}")

    conn = sqlite3.connect(str(db_path), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    ensure_bftv_column(conn)

    performers = load_performers(conn, args.limit, args.overwrite)
    total = len(performers)
    if total == 0:
        print("✓ 所有演员均已有 bftv_url，无需重新爬取（可加 --overwrite 强制刷新）", flush=True)
        conn.close()
        return

    print(f"▶ 准备爬取 {total} 位演员的 BFTV 主页链接…", flush=True)
    print(f"  数据库: {db_path}", flush=True)
    print(f"  请求间隔: {args.delay}s  并发数: {args.concurrency}", flush=True)

    write_lock = Lock()
    found = 0
    not_found = 0
    errors = 0

    def process(pid: int, name: str) -> tuple[int, str, str | None]:
        profile_url = fetch_profile_url(name, args.delay)
        return pid, name, profile_url

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = {pool.submit(process, pid, name): (pid, name) for pid, name in performers}
        done = 0
        for future in as_completed(futures):
            done += 1
            pid, name, profile_url = future.result()
            status_icon = "✓" if profile_url else "✗"
            pct = done / total * 100
            print(
                f"[{done:>5}/{total}] {pct:5.1f}%  {status_icon} {name:<30}  {profile_url or '(未找到)'}",
                flush=True,
            )
            if profile_url:
                with write_lock:
                    conn.execute(
                        "UPDATE performers SET bftv_url = ? WHERE id = ?",
                        (profile_url, pid),
                    )
                    conn.commit()
                found += 1
            else:
                not_found += 1

    conn.close()
    print(f"\n完成！找到: {found}  未找到: {not_found}  错误: {errors}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="批量爬取演员在 BoyfriendTV 上的个人主页，写入 performers.bftv_url"
    )
    parser.add_argument(
        "--db",
        default=str(DEFAULT_DB),
        help=f"SQLite 数据库路径（默认: {DEFAULT_DB}）",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="最多处理多少位演员（默认不限）",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=1.0,
        help="每次请求后等待秒数，避免频率过高（默认: 1.0）",
    )
    parser.add_argument(
        "--concurrency",
        type=int,
        default=2,
        help="并发线程数（默认: 2）",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="重新爬取已有 bftv_url 的演员（默认跳过）",
    )
    args = parser.parse_args()
    run(args)


if __name__ == "__main__":
    main()
