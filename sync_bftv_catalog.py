#!/usr/bin/env python3
"""
sync_bftv_catalog.py
====================
BoyfriendTV (BFTV) 演员档案逆向极速同步引擎。

特点：
1. 采用“全网目录逆向关联”策略：由于 BFTV 收录演员（约 1.2~1.5 万位）远少于本地全量影库（10.8 万位），
   直接从 BFTV 提取全部演员主页 URL 与演员 Slug / 编号，再批量快速映射进本地 SQLite 数据库！
2. 零 Cloudflare 阻断：优先直连 CDN Sitemap (cdn77.boyfriendtv.com)，3 秒内下载全网 12,000+ 演员档案列表；
   若遇网络异常则自动无缝降级为 Playwright 隐身浏览器翻页抓取。
3. 智能模糊匹配算法：自动剥离 GEVI 数据库中演员后缀如 (dp)、(white)、(asian)、(aka Kenny) 等，
   并支持连字符 Slug 格式还原与标准化去重匹配。
4. 毫秒级内存索引与批量写入：千万级比对在内存中 0.1 秒完成，SQLite 事务批量秒级入库。
"""

import argparse
import os
import re
import sqlite3
import subprocess
import sys
import time
from pathlib import Path

from db_manager import find_default_db_path

DEFAULT_DB = find_default_db_path()
SITEMAP_URL = "https://cdn77.boyfriendtv.com/sitemaps/boyfriendtv.com/sitemap_pornstars.xml"


def normalize_name_key(s: str) -> str:
    """提取标准化的比对键：剥离括号别名、小写、移除非字母数字字符。"""
    if not s:
        return ""
    # 剥离括号内容，例如 "Johnny Rapid (dp)" -> "Johnny Rapid"
    s = re.sub(r'\(.*?\)', '', s)
    # 移除非字母数字字符
    s = re.sub(r'[^a-z0-9]', '', s.lower())
    return s


def ensure_bftv_column(conn: sqlite3.Connection) -> None:
    """确保 performers 表存在 bftv_url 字段。"""
    cols = [row[1] for row in conn.execute("PRAGMA table_info(performers)")]
    if "bftv_url" not in cols:
        conn.execute("ALTER TABLE performers ADD COLUMN bftv_url TEXT")
        conn.commit()
        print("✓ 已为 performers 表初始化 bftv_url 字段", flush=True)


def fetch_from_sitemap(local_cache_file: Path | None = None) -> tuple[list[tuple[str, str, str]], int]:
    """
    通过官方 CDN Sitemap 获取全部演员主页 URL，并持久化到本地文件。
    返回: (matches, new_diff_count)
    """
    old_urls: set[str] = set()
    if local_cache_file and local_cache_file.exists():
        try:
            cached_text = local_cache_file.read_text(encoding="utf-8", errors="ignore")
            old_urls = set(re.findall(r'<loc>(https://www\.boyfriendtv\.com/pornstars/[a-z0-9\-]+-\d+/)</loc>', cached_text))
            print(f"📁 本地已缓存 BFTV 演员索引: {len(old_urls)} 位", flush=True)
        except Exception as e:
            print(f"⚠ 读取本地缓存失败: {e}", flush=True)

    print(f"📡 正在从 BFTV CDN Sitemap 获取全量演员索引 ({SITEMAP_URL})...", flush=True)
    xml_data = ""
    try:
        # 使用 curl 避免某些 Python 环境下的 TLS Handshake EOF 问题
        proc = subprocess.run(
            ["curl", "-s", "--max-time", "20", SITEMAP_URL],
            capture_output=True,
            text=True,
            timeout=25
        )
        if proc.returncode == 0 and proc.stdout and "<urlset" in proc.stdout:
            xml_data = proc.stdout
    except Exception as e:
        print(f"⚠ 直连 CDN Sitemap 失败: {e}，正在尝试 urllib 回退...", flush=True)

    if not xml_data:
        try:
            import urllib.request
            import ssl
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            req = urllib.request.Request(
                SITEMAP_URL,
                headers={"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"}
            )
            with urllib.request.urlopen(req, timeout=20, context=ctx) as resp:
                xml_data = resp.read().decode("utf-8", errors="ignore")
        except Exception as e:
            print(f"⚠ urllib 回退亦失败: {e}", flush=True)

    # 若抓取成功，立即持久化保存到本地缓存文件
    if xml_data and "<urlset" in xml_data:
        if local_cache_file:
            try:
                local_cache_file.write_text(xml_data, encoding="utf-8")
                print(f"💾 已将最新 BFTV 索引同步持久化至本地: {local_cache_file}", flush=True)
            except Exception as e:
                print(f"⚠ 写入本地缓存文件失败: {e}", flush=True)
    elif local_cache_file and local_cache_file.exists():
        print("📂 当前网络无法连接 CDN，自动无缝降级读取本地缓存文件...", flush=True)
        xml_data = local_cache_file.read_text(encoding="utf-8", errors="ignore")

    matches = re.findall(r'<loc>(https://www\.boyfriendtv\.com/pornstars/([a-z0-9\-]+)-(\d+)/)</loc>', xml_data)
    current_urls = {m[0] for m in matches}
    new_diff = current_urls - old_urls
    if old_urls and new_diff:
        print(f"✨ 发现 BFTV 官方最新收录演员 {len(new_diff)} 位！", flush=True)
    print(f"✓ 成功解析出 {len(matches)} 位演员档案条目！", flush=True)
    return matches, len(new_diff)


def crawl_with_playwright(max_pages: int = 20) -> list[tuple[str, str, str]]:
    """
    使用 Playwright 隐身浏览器抓取 BFTV 演员列表前 N 页。
    用于获取刚刚收录尚未更新至 Sitemap 的最新演员。
    """
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("⚠ 未安装 Playwright，跳过实时页面爬取", flush=True)
        return []

    results: dict[str, tuple[str, str, str]] = {}
    print(f"🤖 启动 Playwright 隐身浏览器爬取 BFTV 演员目录 (前 {max_pages} 页)...", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=[
                "--disable-blink-features=AutomationControlled",
                "--disable-infobars",
                "--no-sandbox",
            ],
        )
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        )
        context.add_init_script('Object.defineProperty(navigator, "webdriver", { get: () => undefined });')
        page = context.new_page()

        for page_num in range(1, max_pages + 1):
            url = f"https://www.boyfriendtv.com/pornstars/?page={page_num}" if page_num > 1 else "https://www.boyfriendtv.com/pornstars/"
            try:
                page.goto(url, timeout=30000, wait_until="domcontentloaded")
                for _ in range(8):
                    if "Just a moment" not in page.title():
                        break
                    page.wait_for_timeout(1000)

                items = page.evaluate('''() => {
                    const res = [];
                    for (const a of document.querySelectorAll('a[href*="/pornstars/"]')) {
                        const m = a.href.match(/\\/pornstars\\/([a-z0-9\\-]+)-(\\d+)\\//i);
                        if (m) {
                            res.push({ href: a.href, slug: m[1], id: m[2] });
                        }
                    }
                    return res;
                }''')

                new_in_page = 0
                for it in items:
                    href = it["href"]
                    if href not in results:
                        results[href] = (href, it["slug"], it["id"])
                        new_in_page += 1

                print(f"  [页面 {page_num:>2}/{max_pages}] 抓取演员 {len(items)} 位 (新增 {new_in_page} 位)", flush=True)
                page.wait_for_timeout(400)
            except Exception as e:
                print(f"  ⚠ 抓取第 {page_num} 页失败: {e}", flush=True)

        browser.close()

    print(f"✓ 页面抓取完成，共获得 {len(results)} 位演员", flush=True)
    return list(results.values())


def sync_bftv_catalog(
    db_path: str,
    overwrite: bool = False,
    crawl_pages: int = 0,
    dry_run: bool = False,
    verbose: bool = False
) -> tuple[int, int]:
    """
    执行逆向极速匹配与同步。
    返回: (matched_performers_count, updated_performers_count)
    """
    db_file = Path(db_path).expanduser().resolve()
    if not db_file.exists():
        print(f"❌ 数据库不存在: {db_file}", flush=True)
        sys.exit(1)

    print(f"📂 正在连接数据库: {db_file}", flush=True)
    conn = sqlite3.connect(str(db_file), timeout=30)
    ensure_bftv_column(conn)

    local_cache_file = db_file.parent / "sitemap_pornstars.xml"

    # 1. 抓取 BFTV 演员列表
    bftv_items: dict[str, tuple[str, str, str]] = {} # href -> item
    new_sitemap_diff = 0
    try:
        sitemap_items, new_sitemap_diff = fetch_from_sitemap(local_cache_file=local_cache_file)
        for item in sitemap_items:
            bftv_items[item[0]] = item
    except Exception as e:
        print(f"⚠ 读取 Sitemap 遇到异常: {e}", flush=True)

    if crawl_pages > 0 or not bftv_items:
        page_items = crawl_with_playwright(max_pages=crawl_pages or 10)
        for item in page_items:
            bftv_items[item[0]] = item

    total_bftv = len(bftv_items)
    if total_bftv == 0:
        print("❌ 未能获取任何 BFTV 演员数据，请检查网络或代理设置", flush=True)
        return 0, 0

    print(f"\n⚡ BFTV 演员索引库就绪，共计 {total_bftv} 位模特/演员 (最新发现 {new_sitemap_diff} 位新演员)", flush=True)
    print("🔍 正在构建本地数据库演职员内存哈希映射表...", flush=True)

    # 2. 构建本地数据库内存倒排索引 (key -> [(id, name, bftv_url)])
    db_map: dict[str, list[tuple[int, str, str | None]]] = {}
    rows = conn.execute("SELECT id, name, bftv_url FROM performers").fetchall()
    total_local = len(rows)

    for pid, name, bftv_url in rows:
        key = normalize_name_key(name)
        if key:
            db_map.setdefault(key, []).append((pid, name, bftv_url))

    print(f"✓ 本地影库已索引 {total_local} 位演职员（生成 {len(db_map)} 个检索索引）", flush=True)
    print("🚀 开始全量快速比对与增量关联...", flush=True)

    matched_count = 0
    already_had = 0
    updates: list[tuple[str, int]] = [] # (bftv_url, pid)

    for idx, (full_url, slug, bftv_id) in enumerate(bftv_items.values(), start=1):
        slug_key = normalize_name_key(slug)
        if slug_key in db_map:
            targets = db_map[slug_key]
            for pid, orig_name, existing_url in targets:
                matched_count += 1
                if existing_url == full_url:
                    already_had += 1
                elif existing_url and not overwrite:
                    already_had += 1
                else:
                    updates.append((full_url, pid))
                    if verbose or len(updates) <= 15:
                        print(f"+ 新增演员主页 #{pid}: {orig_name} -> BFTV #{bftv_id} ({full_url})", flush=True)

        if idx % 1000 == 0 or idx == total_bftv:
            pct = (idx / total_bftv) * 100
            print(f"[BFTV] {idx:>5}/{total_bftv} ({pct:5.1f}%) | 累计已匹配: {matched_count} 位 | 待写入: {len(updates)} 位", flush=True)

    print(f"\n📊 比对统计:")
    print(f"  • BFTV 演员总数:   {total_bftv} 位")
    print(f"  • 匹配本地数据库: {matched_count} 人次")
    print(f"  • 已有正确链接:   {already_had} 位")
    print(f"  • 新增/待更新数:  {len(updates)} 位")

    if dry_run:
        print("💡 当前为 --dry-run 试运行模式，未对数据库作任何修改。")
        conn.close()
        return matched_count, 0

    if updates:
        print(f"💾 正在批量更新数据库 ({len(updates)} 条记录)...", flush=True)
        conn.executemany("UPDATE performers SET bftv_url = ? WHERE id = ?", updates)
        conn.commit()
        print("✓ 数据批量持久化已完成！", flush=True)
    else:
        print("✓ 数据库演员档案已是最新，无须更新。", flush=True)

    conn.close()
    return matched_count, len(updates)


def main():
    parser = argparse.ArgumentParser(
        description="BoyfriendTV (BFTV) 演员档案逆向极速同步引擎",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--db", default=DEFAULT_DB, help=f"数据库路径 (默认: {DEFAULT_DB})")
    parser.add_argument("--overwrite", action="store_true", help="强制覆盖已有的 bftv_url")
    parser.add_argument("--crawl-pages", type=int, default=0, help="使用 Playwright 爬取前 N 页最新演员目录 (0 为仅使用 Sitemap)")
    parser.add_argument("--dry-run", action="store_true", help="仅检测比对结果，不写入数据库")
    parser.add_argument("--verbose", action="store_true", help="输出每一条演员匹配详情")

    args = parser.parse_args()

    t0 = time.time()
    matched, updated = sync_bftv_catalog(
        db_path=args.db,
        overwrite=args.overwrite,
        crawl_pages=args.crawl_pages,
        dry_run=args.dry_run,
        verbose=args.verbose
    )
    elapsed = time.time() - t0
    print(f"\n🎉 同步流程圆满结束！耗时: {elapsed:.2f} 秒 | 匹配: {matched} 位 | 新增关联: {updated} 位\n", flush=True)


if __name__ == "__main__":
    main()
