#!/usr/bin/env python3
"""
GEVI Incremental Sync & Auto-Integration Module
- Scans /newm and /newp for recent updates.
- Probes auto-incrementing IDs above MAX(id).

Episodes are deliberately not here. This module used to claim it scanned /newe, but
that was never implemented and /newe has no pagination anyway — the newest 60 is all
you can get. Episodes come from the `coep` DataTables endpoint instead, one company
at a time: `scraper_v2.py --mode episode-sync`.
- Automatically merges new records into local SQLite database.
"""

from __future__ import annotations
import re
import html
import time
import argparse
import urllib.request
from pathlib import Path
from db_manager import DatabaseManager
from batch_scraper import BatchScraper, BASE_URL, USER_AGENT

def fetch_html(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.read().decode("utf-8", errors="ignore")
    except Exception:
        return ""

def get_latest_ids_from_pages() -> tuple[set[int], set[int]]:
    """Scrape /newm and /newp to discover currently featured new movies and performers."""
    print("🌐 正在抓取官网最新更新页 (/newm & /newp)...")
    
    new_movies: set[int] = set()
    new_performers: set[int] = set()

    # 1. New movies
    html_newm = fetch_html(f"{BASE_URL}/newm")
    for m in re.finditer(r"href=[\'\"]video/(\d+)[\'\"]", html_newm):
        new_movies.add(int(m.group(1)))

    # 2. New performers
    html_newp = fetch_html(f"{BASE_URL}/newp")
    for m in re.finditer(r"href=[\'\"]performer/(\d+)[\'\"]", html_newp):
        new_performers.add(int(m.group(1)))

    return new_movies, new_performers

def run_sync(db_path: str = "gevi.db", probe_depth: int = 50):
    print("=" * 70)
    print("🔄 GEVI 数据库增量同步与自动整合")
    print(f"   目标数据库: {Path(db_path).resolve()}")
    print("=" * 70)

    db = DatabaseManager(db_path)
    scraper = BatchScraper(db_path=db_path, concurrency=6, delay=0.08)

    # 1. Inspect local high-water marks
    cur = db.conn.cursor()
    cur.execute("SELECT COALESCE(MAX(id), 0) FROM movies")
    max_movie_id = cur.fetchone()[0]

    cur.execute("SELECT COALESCE(MAX(id), 0) FROM performers")
    max_perf_id = cur.fetchone()[0]

    print(f"📌 本地当前最大电影 ID: #{max_movie_id} | 最大演员 ID: #{max_perf_id}")

    # 2. Fetch new list from portal
    featured_movies, featured_performers = get_latest_ids_from_pages()
    print(f"🔍 官方更新页发现: 电影 {len(featured_movies)} 部, 演员 {len(featured_performers)} 位")

    # Combine with ID forward probing
    pending_movie_ids = set(featured_movies)
    if max_movie_id > 0:
        for i in range(max_movie_id + 1, max_movie_id + probe_depth + 1):
            pending_movie_ids.add(i)

    pending_perf_ids = set(featured_performers)
    if max_perf_id > 0:
        for i in range(max_perf_id + 1, max_perf_id + probe_depth + 1):
            pending_perf_ids.add(i)

    # Filter out already completed
    completed_movies = db.get_completed_ids("movie")
    to_scrape_movies = sorted(list(pending_movie_ids - completed_movies))

    completed_perfs = db.get_completed_ids("performer")
    to_scrape_perfs = sorted(list(pending_perf_ids - completed_perfs))

    print(f"\n⚡ 需要抓取同步的新增项: 电影 {len(to_scrape_movies)} 项, 演员 {len(to_scrape_perfs)} 项")

    new_movies_added = 0
    new_perfs_added = 0

    if to_scrape_movies:
        print("\n📥 正在增量同步新电影...")
        for mid in to_scrape_movies:
            status, html_content = scraper.fetch_url(f"{BASE_URL}/video/{mid}")
            if status == 200:
                m_data = scraper.parse_movie(mid, html_content)
                if m_data:
                    db.save_movie(m_data, status=200)
                    new_movies_added += 1
                    print(f"  + 新增电影 #{mid}: 《{m_data['title']}》 ({m_data['release_year']})")
                else:
                    db.record_progress("movie", mid, status=404)
            else:
                db.record_progress("movie", mid, status=status)

    if to_scrape_perfs:
        print("\n📥 正在增量同步新演员...")
        for pid in to_scrape_perfs:
            status, html_content = scraper.fetch_url(f"{BASE_URL}/performer/{pid}")
            if status == 200:
                p_data = scraper.parse_performer(pid, html_content)
                if p_data:
                    db.save_performer(p_data, status=200)
                    new_perfs_added += 1
                    print(f"  + 新增演员 #{pid}: {p_data['name']}")
                else:
                    db.record_progress("performer", pid, status=404)
            else:
                db.record_progress("performer", pid, status=status)

    print("\n" + "=" * 70)
    print(f"🎉 增量同步完成！本次入库新电影: {new_movies_added} 部 | 新演员: {new_perfs_added} 位")
    stats = db.get_stats()
    print(f"📊 当前数据库总量: 电影 {stats['movies']:,} 部 | 演员 {stats['performers']:,} 位 | 关联 {stats['movie_performers']:,} 条")
    print("=" * 70)

def main():
    parser = argparse.ArgumentParser(description="GEVI Incremental Sync & Auto-Integration Tool")
    parser.add_argument("--db", type=str, default="gevi.db", help="Path to SQLite database")
    parser.add_argument("--probe", type=int, default=30, help="Forward probe depth above MAX(id) (default: 30)")
    args = parser.parse_args()

    run_sync(db_path=args.db, probe_depth=args.probe)

if __name__ == "__main__":
    main()
