#!/usr/bin/env python3
"""
GEVI Incremental Sync & Auto-Integration Module
- Scans /newm for recent movie releases.
- Scans /newp for new performers.
- Scans /newe for latest episode updates.
- Probes auto-incrementing IDs above MAX(id).
- Automatically merges new records into local SQLite database.
"""

from __future__ import annotations
import re
import html
import time
import argparse
import urllib.request
from pathlib import Path
from db_manager import DatabaseManager, find_default_db_path
from batch_scraper import BatchScraper, BASE_URL, USER_AGENT

def fetch_html(url: str, retries: int = 3) -> str:
    req = urllib.request.Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    })
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                return resp.read().decode("utf-8", errors="ignore")
        except Exception:
            if attempt < retries - 1:
                time.sleep(0.5 * (attempt + 1))
    return ""

def download_image_to_cache(url: str, cache_dir: Path) -> bool:
    """Download an image into local image_cache/ directly during sync."""
    if not url:
        return False
    lower = url.lower()
    folder_and_rel = None
    if "images/covers/" in lower:
        idx = lower.find("images/covers/")
        folder_and_rel = ("Covers", url[idx + len("images/covers/"):].lstrip("/"))
    elif "images/episodes/" in lower:
        idx = lower.find("images/episodes/")
        folder_and_rel = ("Episodes", url[idx + len("images/episodes/"):].lstrip("/"))
    elif "images/stars/" in lower:
        idx = lower.find("images/stars/")
        folder_and_rel = ("Stars", url[idx + len("images/stars/"):].lstrip("/"))

    if not folder_and_rel:
        return False

    dest = cache_dir / folder_and_rel[0] / folder_and_rel[1]
    if dest.exists() and dest.stat().st_size > 0:
        return True

    dest.parent.mkdir(parents=True, exist_ok=True)
    full_url = url if url.startswith("http") else f"{BASE_URL}/{url.lstrip('/')}"
    import subprocess
    cmd = [
        "curl", "-s", "-L", "-f", "--connect-timeout", "4", "--max-time", "12",
        "-A", USER_AGENT, "-e", "https://gayeroticvideoindex.com/",
        "-o", str(dest), full_url
    ]
    try:
        res = subprocess.run(cmd, capture_output=True, timeout=15)
        if res.returncode == 0 and dest.is_file() and dest.stat().st_size > 0:
            return True
        if dest.exists() and dest.stat().st_size == 0:
            dest.unlink(missing_ok=True)
    except Exception:
        pass
    return False

def get_latest_ids_from_pages() -> tuple[set[int], set[int], list[dict]]:
    """Scrape /newm, /newp, and /newe to discover featured updates."""
    print("🌐 正在抓取官网最新更新页 (/newm, /newp, /newe)...")
    
    new_movies: set[int] = set()
    new_performers: set[int] = set()
    new_episodes: list[dict] = []

    # 1. New movies
    html_newm = fetch_html(f"{BASE_URL}/newm")
    for m in re.finditer(r"href=[\'\"]video/(\d+)[\'\"]", html_newm):
        new_movies.add(int(m.group(1)))

    # 2. New performers
    html_newp = fetch_html(f"{BASE_URL}/newp")
    for m in re.finditer(r"href=[\'\"]performer/(\d+)[\'\"]", html_newp):
        new_performers.add(int(m.group(1)))

    # 3. New episodes from /newe
    html_newe = fetch_html(f"{BASE_URL}/newe")
    seen_ep_ids = set()
    for m in re.finditer(r"href=[\'\"]episode/(\d+)[\'\"]", html_newe):
        ep_id = int(m.group(1))
        if ep_id not in seen_ep_ids:
            seen_ep_ids.add(ep_id)
            new_episodes.append({"id": ep_id})

    return new_movies, new_performers, new_episodes

def parse_episode_details(ep_id: int) -> dict | None:
    """Fetch and parse detailed attributes of an episode from /episode/{id}."""
    html_text = fetch_html(f"{BASE_URL}/episode/{ep_id}")
    if not html_text:
        return None

    # Title
    title_m = re.search(r"<title>(.*?)(?:: Gay Erotic Video Index)?</title>", html_text)
    title = html.unescape(title_m.group(1).replace(": Gay Erotic Video Index", "").strip()) if title_m else ""

    # Thumbnail (prefer HD 'b' variant if present)
    thumb_m = re.search(r"src=[\'\"](images/Episodes/[^\'\"]+)[\'\"]", html_text)
    thumb_url = f"{BASE_URL}/{thumb_m.group(1)}" if thumb_m else ""

    # Movie link if part of a parent film
    movie_m = re.search(r"href=[\'\"]video/(\d+)[\'\"]", html_text)
    movie_id = int(movie_m.group(1)) if movie_m else None

    # Studio/Company
    comp_m = re.search(r"href=[\'\"]company/(\d+)[\'\"][^>]*>([^<]+)<", html_text)
    studio_id = int(comp_m.group(1)) if comp_m else None
    studio_name = html.unescape(comp_m.group(2).strip()) if comp_m else None

    # Release Date
    date_m = re.search(r"\b(\d{4}-\d{2}-\d{2})\b", html_text)
    release_date = date_m.group(1) if date_m else None

    # Performers: href='performer/12345'><span...>Name</span>
    performers = []
    for pm in re.finditer(r"href=[\'\"]performer/(\d+)[\'\"][^>]*>(?:<span[^>]*>)?([^<]+)<", html_text):
        pid = int(pm.group(1))
        pname = html.unescape(pm.group(2).strip())
        if pname and (pid, pname) not in performers:
            performers.append((pid, pname))

    return {
        "id": ep_id,
        "movie_id": movie_id,
        "title": title or f"Episode #{ep_id}",
        "thumbnail_url": thumb_url,
        "description": "",
        "action_notes": "",
        "studio_id": studio_id,
        "studio_name": studio_name,
        "release_date": release_date,
        "performers": performers,
    }

def run_sync(db_path: str = "gevi.db", probe_depth: int = 50):
    print("=" * 70)
    print("🔄 GEVI 数据库增量同步与自动整合")
    print(f"   目标数据库: {Path(db_path).resolve()}")
    print("=" * 70)

    db = DatabaseManager(db_path)
    scraper = BatchScraper(db_path=db_path, concurrency=6, delay=0.08)

    # 1. Clean up stale/premature 404 entries above max movie id so forward probe isn't blocked
    cur = db.conn.cursor()
    cur.execute("SELECT COALESCE(MAX(id), 0) FROM movies")
    max_movie_id = cur.fetchone()[0]

    cur.execute("SELECT COALESCE(MAX(id), 0) FROM performers")
    max_perf_id = cur.fetchone()[0]

    cur.execute("SELECT COALESCE(MAX(id), 0) FROM episodes")
    max_ep_id = cur.fetchone()[0]

    print(f"📌 本地当前状态: 电影 MAX #{max_movie_id} | 演员 MAX #{max_perf_id} | 分集 MAX #{max_ep_id}")

    # Remove premature 404s for movies > max_movie_id to allow fresh discovery
    cur.execute(
        "DELETE FROM scrape_progress WHERE item_type = 'movie' AND status = 404 AND item_id > ?",
        (max_movie_id,)
    )
    db.conn.commit()

    # 2. Fetch new lists from portal
    featured_movies, featured_performers, featured_episodes = get_latest_ids_from_pages()
    print(f"🔍 官方更新页发现: 电影 {len(featured_movies)} 部, 演员 {len(featured_performers)} 位, 分集 {len(featured_episodes)} 个")

    # 3. Read existing local IDs
    cur.execute("SELECT id FROM movies")
    existing_movies = {row[0] for row in cur.fetchall()}

    cur.execute("SELECT id FROM performers")
    existing_perfs = {row[0] for row in cur.fetchall()}

    cur.execute("SELECT id FROM episodes WHERE title IS NOT NULL AND title != ''")
    existing_eps = {row[0] for row in cur.fetchall()}

    # Recent 404s within 6 hours (only filter probed IDs, never filter featured ones)
    cur.execute("SELECT item_id FROM scrape_progress WHERE item_type = 'movie' AND status = 404 AND last_scraped >= datetime('now', '-6 hours')")
    recent_404_movies = {row[0] for row in cur.fetchall()}

    # Calculate movie targets
    probed_movie_ids = set()
    if max_movie_id > 0:
        for i in range(max_movie_id + 1, max_movie_id + probe_depth + 1):
            if i not in recent_404_movies:
                probed_movie_ids.add(i)

    pending_movie_ids = (featured_movies - existing_movies) | (probed_movie_ids - existing_movies)
    to_scrape_movies = sorted(list(pending_movie_ids))

    # Calculate performer targets
    probed_perf_ids = set()
    if max_perf_id > 0:
        for i in range(max_perf_id + 1, max_perf_id + probe_depth + 1):
            probed_perf_ids.add(i)

    pending_perf_ids = (featured_performers - existing_perfs) | (probed_perf_ids - existing_perfs)
    to_scrape_perfs = sorted(list(pending_perf_ids))

    # Calculate episode targets
    to_scrape_eps = [e["id"] for e in featured_episodes if e["id"] not in existing_eps]

    print(f"\n⚡ 需要抓取同步的新增项: 电影 {len(to_scrape_movies)} 项, 演员 {len(to_scrape_perfs)} 项, 分集 {len(to_scrape_eps)} 项")

    new_movies_added = 0
    new_perfs_added = 0
    new_eps_added = 0

    cache_dir = Path(db_path).resolve().parent / "image_cache"

    # 4. Scrape & save movies
    if to_scrape_movies:
        print("\n📥 正在增量同步新电影与场景...")
        for mid in to_scrape_movies:
            status, html_content = scraper.fetch_url(f"{BASE_URL}/video/{mid}")
            if status == 200:
                m_data = scraper.parse_movie(mid, html_content)
                if m_data:
                    db.save_movie(m_data, status=200)
                    new_movies_added += 1
                    ep_cnt = len(m_data.get("episodes", []))
                    print(f"  + 新增电影 #{mid}: 《{m_data['title']}》 ({m_data['release_year']}) [含分集 {ep_cnt}]")
                    # Auto-cache cover images to local disk
                    download_image_to_cache(m_data.get("cover_full"), cache_dir)
                    download_image_to_cache(m_data.get("cover_icon"), cache_dir)
                    for ep in m_data.get("episodes", []):
                        download_image_to_cache(ep.get("thumbnail_url"), cache_dir)
                else:
                    db.record_progress("movie", mid, status=404)
            else:
                db.record_progress("movie", mid, status=status)

    # 5. Scrape & save performers
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
                    # Auto-cache portrait image
                    download_image_to_cache(p_data.get("image_url"), cache_dir)
                else:
                    db.record_progress("performer", pid, status=404)
            else:
                db.record_progress("performer", pid, status=status)

    # 6. Scrape & save standalone / latest episodes
    if to_scrape_eps:
        print("\n📥 正在增量同步最新分集与片段...")
        for eid in to_scrape_eps:
            ep_details = parse_episode_details(eid)
            if ep_details:
                db.save_company_episodes([ep_details])
                new_eps_added += 1
                db.record_progress("episode", eid, status=200)
                print(f"  + 新增分集 #{eid}: 《{ep_details['title']}》 ({ep_details.get('studio_name') or '独立发布'})")
                # Auto-cache episode thumbnail
                download_image_to_cache(ep_details.get("thumbnail_url"), cache_dir)
            else:
                db.record_progress("episode", eid, status=404)

    print("\n" + "=" * 70)
    print(f"🎉 增量同步完成！本次入库新电影: {new_movies_added} 部 | 新演员: {new_perfs_added} 位 | 新分集: {new_eps_added} 个")
    stats = db.get_stats()
    print(f"📊 当前数据库总量: 电影 {stats['movies']:,} 部 | 演员 {stats['performers']:,} 位 | 关联 {stats['movie_performers']:,} 条 | 分集 {stats.get('episodes', 0):,} 个")
    print("=" * 70)

def main():
    detected_db = find_default_db_path()
    parser = argparse.ArgumentParser(description="GEVI Incremental Sync & Auto-Integration Tool")
    parser.add_argument("--db", type=str, default=str(detected_db), help=f"Path to SQLite database (default: {detected_db})")
    parser.add_argument("--probe", type=int, default=30, help="Forward probe depth above MAX(id) (default: 30)")
    args = parser.parse_args()

    run_sync(db_path=args.db, probe_depth=args.probe)

if __name__ == "__main__":
    main()
