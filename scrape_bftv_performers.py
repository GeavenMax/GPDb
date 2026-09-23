#!/usr/bin/env python3
"""
scrape_bftv_performers.py
=========================
批量提取演员在 BoyfriendTV (BFTV) 上的个人主页 URL，保存到数据库 performers.bftv_url 列。

用法
----
  python scrape_bftv_performers.py                         # 自动读取客户端当前连接的数据库
  python scrape_bftv_performers.py --limit 50              # 只处理前 50 个未匹配的演员
  python scrape_bftv_performers.py --name "Alex Kof"       # 只精准处理指定的演员
  python scrape_bftv_performers.py --delay 1.0             # 请求间隔秒数（默认 1.0）
  python scrape_bftv_performers.py --overwrite             # 重新爬取已有 bftv_url 的演员
  python scrape_bftv_performers.py --db /path/to/gevi.db   # 手动指定数据库文件

工作原理
--------
1. 数据库路径自动侦测：优先从客户端配置 (~/Library/Application Support/com.gpdb.app/db_config.json)、
   环境变量 GEVI_DB、~/.gevi_db_path 或当前目录获取。
2. 自动化浏览器反反爬：使用 Playwright Chromium Stealth 模式轻松穿透 Cloudflare，
   获取真实渲染后的页面。若未安装 Playwright，自动降级为标准 HTTP 客户端。
3. 正则提取演员主页卡片直链（/pornstars/<slug>-<digits>/），例如：
   https://www.boyfriendtv.com/pornstars/alex-kof-5862/
4. 匹配结果立即存盘写入 performers.bftv_url，客户端点击即可 1 秒直达演员个人主页！
"""

import argparse
import os
import re
import sqlite3
import sys
import time
import urllib.parse
from pathlib import Path

from db_manager import find_default_db_path

# ── 默认数据库路径（自动读取客户端配置、环境变量或本地文件）─────────────────────
DEFAULT_DB = find_default_db_path()

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
PROFILE_RE = re.compile(r'href=[\"\'](/pornstars/[a-z0-9\-]+-\d+/)[\"\']', re.IGNORECASE)


def clean_performer_name(name: str) -> str:
    """清理演员名称中的注释与别名干扰，如 (white)、(80s)、(aka Kenny) 等。"""
    cleaned = re.sub(r'\(.*?\)', '', name).strip()
    cleaned = re.sub(r'\s+', ' ', cleaned)
    return cleaned or name


def build_search_url(name: str) -> str:
    cleaned = clean_performer_name(name)
    parts = cleaned.strip().split()
    q = urllib.parse.quote("+".join(parts), safe="+")
    return SEARCH_URL.format(q=q)


def extract_profile_url_from_html(html: str) -> str | None:
    matches = PROFILE_RE.findall(html)
    if not matches:
        return None
    # 提取首个匹配项
    path = matches[0]
    return f"https://www.boyfriendtv.com{path}"


def ensure_bftv_column(conn: sqlite3.Connection) -> None:
    """确保 bftv_url 列存在（兼容旧数据库）。"""
    cols = [row[1] for row in conn.execute("PRAGMA table_info(performers)")]
    if "bftv_url" not in cols:
        conn.execute("ALTER TABLE performers ADD COLUMN bftv_url TEXT")
        conn.commit()
        print("✓ 已为 performers 表添加 bftv_url 列", flush=True)


def load_performers(
    conn: sqlite3.Connection,
    limit: int | None,
    overwrite: bool,
    single_name: str | None = None
) -> list[tuple[int, str]]:
    """从数据库加载待处理演员列表。优先处理有头像的知名演员。"""
    if single_name:
        sql = "SELECT id, name FROM performers WHERE name LIKE ? ORDER BY id ASC"
        return conn.execute(sql, (f"%{single_name.strip()}%",)).fetchall()

    if overwrite:
        sql = """
            SELECT id, name FROM performers 
            ORDER BY (image_url IS NOT NULL AND trim(image_url) != '') DESC, id ASC
        """
    else:
        sql = """
            SELECT id, name FROM performers 
            WHERE bftv_url IS NULL 
            ORDER BY (image_url IS NOT NULL AND trim(image_url) != '') DESC, id ASC
        """
    if limit:
        sql += f" LIMIT {limit}"
    return conn.execute(sql).fetchall()


def run_with_playwright(performers: list[tuple[int, str]], conn: sqlite3.Connection, delay: float) -> None:
    from playwright.sync_api import sync_playwright

    total = len(performers)
    found = 0
    not_found = 0

    print("🤖 正在启动 Playwright 隐身浏览器引擎（已配置 Cloudflare 无感穿透与 Turnstile 自适应等待）...", flush=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=[
                "--disable-blink-features=AutomationControlled",
                "--disable-infobars",
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--window-size=1920,1080",
            ],
        )
        context = browser.new_context(
            user_agent=(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/128.0.0.0 Safari/537.36"
            ),
            viewport={"width": 1920, "height": 1080},
            locale="en-US",
            timezone_id="America/New_York",
        )
        context.add_init_script("""
            Object.defineProperty(navigator, "webdriver", { get: () => undefined });
            window.navigator.chrome = { runtime: {} };
            Object.defineProperty(navigator, "plugins", { get: () => [1, 2, 3, 4, 5] });
            Object.defineProperty(navigator, "languages", { get: () => ["en-US", "en"] });
        """)
        page = context.new_page()

        for idx, (pid, name) in enumerate(performers, start=1):
            url = build_search_url(name)
            profile_url = None
            try:
                page.goto(url, timeout=30000, wait_until="domcontentloaded")
                # 动态自适应等待 Cloudflare Turnstile 质询完成（通常耗时 2~4 秒）
                for _ in range(8):
                    if "Just a moment" not in page.title():
                        break
                    page.wait_for_timeout(1000)

                page.wait_for_timeout(int(delay * 1000) if delay > 1.0 else 1000)

                html = page.content()
                profile_url = extract_profile_url_from_html(html)
            except Exception as e:
                print(f"  ⚠ 抓取「{name}」异常: {e}", flush=True)

            status_icon = "✓" if profile_url else "✗"
            pct = (idx / total) * 100
            print(
                f"[{idx:>5}/{total}] {pct:5.1f}%  {status_icon} {name:<28}  {profile_url or '(BFTV未收录)'}",
                flush=True,
            )

            if profile_url:
                conn.execute(
                    "UPDATE performers SET bftv_url = ? WHERE id = ?",
                    (profile_url, pid),
                )
                conn.commit()
                found += 1
            else:
                not_found += 1

        browser.close()

    print(f"\n🎉 爬取完成！成功匹配: {found} 位 | BFTV未收录: {not_found} 位", flush=True)


def run_with_urllib(performers: list[tuple[int, str]], conn: sqlite3.Connection, delay: float) -> None:
    import urllib.request

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/128.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "en-US,en;q=0.9",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    }

    total = len(performers)
    found = 0
    not_found = 0

    print("🌐 使用 HTTP 标准客户端抓取（若遇 Cloudflare 403 请安装 playwright: pip install playwright && playwright install chromium）...", flush=True)
    for idx, (pid, name) in enumerate(performers, start=1):
        url = build_search_url(name)
        req = urllib.request.Request(url, headers=headers)
        profile_url = None
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                html = resp.read().decode("utf-8", errors="replace")
                profile_url = extract_profile_url_from_html(html)
        except Exception as e:
            print(f"  ⚠ 抓取「{name}」异常: {e}", flush=True)
        finally:
            time.sleep(delay)

        status_icon = "✓" if profile_url else "✗"
        pct = (idx / total) * 100
        print(
            f"[{idx:>5}/{total}] {pct:5.1f}%  {status_icon} {name:<28}  {profile_url or '(未找到)'}",
            flush=True,
        )

        if profile_url:
            conn.execute(
                "UPDATE performers SET bftv_url = ? WHERE id = ?",
                (profile_url, pid),
            )
            conn.commit()
            found += 1
        else:
            not_found += 1

    print(f"\n🎉 爬取完成！成功匹配: {found} 位 | 未找到: {not_found} 位", flush=True)


def run(args: argparse.Namespace) -> None:
    db_path = Path(args.db).expanduser()
    if not db_path.exists():
        sys.exit(
            f"❌ 数据库不存在: {db_path}\n"
            f"💡 提示：客户端当前未记录有效数据库路径。\n"
            f"   请先打开客户端指定或创建数据库，或通过 --db 参数手动指定，例如：\n"
            f"   python scrape_bftv_performers.py --db /path/to/gevi.db"
        )

    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    ensure_bftv_column(conn)

    performers = load_performers(conn, args.limit, args.overwrite, args.name)
    total = len(performers)
    if total == 0:
        print(f"✓ 数据库「{db_path.resolve()}」中待匹配演员数为 0（若需刷新可加 --overwrite 强制重爬）", flush=True)
        conn.close()
        return

    print(f"📂 当前数据库: {db_path.resolve()}", flush=True)
    print(f"▶ 准备检索 {total} 位演员的 BoyfriendTV 个人主页链接…", flush=True)
    print(f"  请求延迟: {args.delay}s", flush=True)

    has_playwright = False
    try:
        import playwright
        has_playwright = True
    except ImportError:
        pass

    try:
        if has_playwright:
            run_with_playwright(performers, conn, args.delay)
        else:
            run_with_urllib(performers, conn, args.delay)
    finally:
        conn.close()


def main() -> None:
    detected_db = find_default_db_path()
    parser = argparse.ArgumentParser(
        description="批量提取演员在 BoyfriendTV 上的个人主页直链，写入 performers.bftv_url"
    )
    parser.add_argument(
        "--db",
        default=str(detected_db),
        help=f"SQLite 数据库路径（已自动识别为: {detected_db}）",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="最多处理多少位演员（默认全部未处理演员）",
    )
    parser.add_argument(
        "--name",
        type=str,
        default=None,
        help="只检索指定演员名称（例如: --name 'Alex Kof'）",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=1.0,
        help="每次请求间隔秒数（默认: 1.0）",
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
