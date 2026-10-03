#!/usr/bin/env python3
"""
Scrape studio official website URLs from GEVI (gayeroticvideoindex.com) and store into GPDb.db.

Usage:
    python3 scrape_studio_websites.py [--all] [--workers 8] [--limit N]
"""

import argparse
import concurrent.futures
import html
import os
import re
import sqlite3
import sys
import time
import urllib.request
import urllib.error

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "GPDb.db")

NAV_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Accept-Encoding": "gzip, deflate",
    "Connection": "keep-alive",
}


def init_db(conn: sqlite3.Connection):
    c = conn.cursor()
    # Ensure columns exist on studios table
    c.execute("PRAGMA table_info(studios)")
    cols = {row[1] for row in c.fetchall()}
    if "website_url" not in cols:
        c.execute("ALTER TABLE studios ADD COLUMN website_url TEXT")
    if "site_id" not in cols:
        c.execute("ALTER TABLE studios ADD COLUMN site_id INTEGER")

    # Table for all scraped company websites
    c.execute("""
        CREATE TABLE IF NOT EXISTS studio_websites (
            site_id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            website_url TEXT,
            scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    c.execute("CREATE INDEX IF NOT EXISTS idx_studio_websites_name ON studio_websites(name)")
    conn.commit()


def fetch_company_page(cid: int, timeout: float = 12.0) -> tuple[int, str | None, str | None, bool]:
    """
    Fetch /company/{cid} and extract (cid, company_name, website_url, success).
    """
    url = f"https://gayeroticvideoindex.com/company/{cid}"
    req = urllib.request.Request(url, headers=NAV_HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw_data = resp.read()
            # Handle gzip if compressed
            if resp.headers.get("Content-Encoding") == "gzip":
                import gzip
                html_txt = gzip.decompress(raw_data).decode("utf-8", errors="ignore")
            else:
                html_txt = raw_data.decode("utf-8", errors="ignore")

            # Extract company name
            m_title = re.search(r"<title>(.*?):\s*Gay Erotic Video Index</title>", html_txt, re.I)
            name = None
            if m_title:
                name = html.unescape(m_title.group(1).strip())
            else:
                m_h1 = re.search(r"<!--\s*name\s*-->.*?<h1[^>]*>\s*(.*?)\s*</h1>", html_txt, re.DOTALL)
                if m_h1:
                    name = html.unescape(m_h1.group(1).strip())

            # Extract website link from <!-- name --> section before <!-- stats
            website_url = None
            m_sec = re.search(r"<!--\s*name\s*-->.*?<!--\s*stats", html_txt, re.DOTALL)
            if m_sec:
                sec = m_sec.group(0)
                # Find all external links starting with http:// or https://
                links = re.findall(r"<a\s+[^>]*href=['\"](https?://[^'\"]+)['\"]", sec, re.I)
                for l in links:
                    l = l.strip()
                    if not l.startswith("https://gayeroticvideoindex.com") and not l.startswith("http://gayeroticvideoindex.com"):
                        website_url = l
                        break

            return (cid, name, website_url, True)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return (cid, None, None, True)
        return (cid, None, None, False)
    except Exception as e:
        return (cid, None, None, False)


def get_target_ids(conn: sqlite3.Connection, scrape_all: bool = False) -> list[int]:
    c = conn.cursor()
    # 1. Target all distinct studio_ids referenced in movies and episodes
    c.execute("""
        SELECT DISTINCT studio_id FROM (
            SELECT DISTINCT studio_id FROM movies WHERE studio_id IS NOT NULL AND studio_id > 0
            UNION
            SELECT DISTINCT studio_id FROM episodes WHERE studio_id IS NOT NULL AND studio_id > 0
        )
        ORDER BY studio_id
    """)
    core_ids = [row[0] for row in c.fetchall()]

    if not scrape_all:
        return core_ids

    # If --all, include all IDs up to 8175
    all_set = set(core_ids)
    all_set.update(range(1, 8176))
    return sorted(all_set)


def get_already_scraped_ids(conn: sqlite3.Connection) -> set[int]:
    c = conn.cursor()
    c.execute("SELECT site_id FROM studio_websites")
    return {row[0] for row in c.fetchall()}


def apply_website_to_studios(conn: sqlite3.Connection, site_id: int, name: str, website_url: str | None):
    c = conn.cursor()
    # Record in studio_websites
    c.execute("""
        INSERT OR REPLACE INTO studio_websites (site_id, name, website_url, scraped_at)
        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
    """, (site_id, name, website_url))

    if website_url:
        # Update studios table where name matches case-insensitively
        c.execute("""
            UPDATE studios
            SET website_url = ?, site_id = ?
            WHERE name = ? COLLATE NOCASE
        """, (website_url, site_id, name))

        # Also check movies/episodes to see if any studio_name matches this site_id
        c.execute("""
            UPDATE studios
            SET website_url = ?, site_id = ?
            WHERE (website_url IS NULL OR website_url = '')
              AND name IN (
                  SELECT DISTINCT studio_name FROM movies WHERE studio_id = ?
                  UNION
                  SELECT DISTINCT studio_name FROM episodes WHERE studio_id = ?
              )
        """, (website_url, site_id, site_id, site_id))
    else:
        # Just update site_id if available
        c.execute("""
            UPDATE studios
            SET site_id = ?
            WHERE site_id IS NULL AND name = ? COLLATE NOCASE
        """, (site_id, name))


def main():
    parser = argparse.ArgumentParser(description="Scrape studio official websites from GEVI")
    parser.add_argument("--all", action="store_true", help="Scrape all IDs up to 8175 instead of just library studio_ids")
    parser.add_argument("--workers", type=int, default=8, help="Number of concurrent workers (default: 8)")
    parser.add_argument("--limit", type=int, default=None, help="Limit number of studios to scrape")
    parser.add_argument("--force", action="store_true", help="Re-scrape already scraped IDs")
    args = parser.parse_args()

    conn = sqlite3.connect(DB_PATH)
    init_db(conn)

    target_ids = get_target_ids(conn, scrape_all=args.all)
    already_done = set() if args.force else get_already_scraped_ids(conn)
    pending_ids = [cid for cid in target_ids if cid not in already_done]

    if args.limit:
        pending_ids = pending_ids[:args.limit]

    print(f"Total target IDs: {len(target_ids)}")
    print(f"Already scraped: {len(already_done)}")
    print(f"Pending to scrape: {len(pending_ids)}")
    print(f"Concurrency: {args.workers} workers")

    if not pending_ids:
        print("All targets already scraped!")
        conn.close()
        return

    start_time = time.time()
    completed = 0
    with_websites = 0
    batch_size = 25

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        for i in range(0, len(pending_ids), batch_size):
            chunk = pending_ids[i:i + batch_size]
            futures = {executor.submit(fetch_company_page, cid): cid for cid in chunk}

            for fut in concurrent.futures.as_completed(futures):
                cid, name, website, ok = fut.result()
                completed += 1
                if ok and name:
                    apply_website_to_studios(conn, cid, name, website)
                    if website:
                        with_websites += 1
                        print(f"[{completed}/{len(pending_ids)}] ID {cid:4d} | {name} -> {website}")
                elif not ok:
                    # Retry once after brief pause
                    time.sleep(0.5)
                    r_cid, r_name, r_website, r_ok = fetch_company_page(cid, timeout=15.0)
                    if r_ok and r_name:
                        apply_website_to_studios(conn, r_cid, r_name, r_website)
                        if r_website:
                            with_websites += 1
                            print(f"[{completed}/{len(pending_ids)}] ID {cid:4d} (retried) | {r_name} -> {r_website}")

            conn.commit()
            elapsed = time.time() - start_time
            rate = completed / elapsed if elapsed > 0 else 0
            if completed % 100 == 0 or completed == len(pending_ids):
                print(f"--- Progress: {completed}/{len(pending_ids)} ({completed*100/len(pending_ids):.1f}%) | "
                      f"Websites found: {with_websites} | Speed: {rate:.1f} req/s ---")

    # Summary
    c = conn.cursor()
    c.execute("SELECT count(*) FROM studio_websites WHERE website_url IS NOT NULL AND website_url != ''")
    total_sites_found = c.fetchone()[0]
    c.execute("SELECT count(*) FROM studios WHERE website_url IS NOT NULL AND website_url != ''")
    studios_with_site = c.fetchone()[0]

    print("\n" + "="*60)
    print("Scraping Completed Successfully!")
    print(f"Total time elapsed: {time.time() - start_time:.1f}s")
    print(f"Total company websites found on GEVI: {total_sites_found}")
    print(f"Total studios updated with official website in studios table: {studios_with_site}")
    print("="*60)

    conn.close()


if __name__ == "__main__":
    main()
