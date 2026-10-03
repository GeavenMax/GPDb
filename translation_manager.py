#!/usr/bin/env python3
"""
GPDb Translation Database Manager & Batch Translation CLI

Manages `translations.db` (standalone translation database) and interfaces
with the agent/script for batch translation, progress tracking, and importing
translations back into the main `GPDb.db`.
"""

import argparse
import json
import os
import sqlite3
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_MAIN_DB = BASE_DIR / "GPDb.db"
DEFAULT_TRANS_DB = BASE_DIR / "translations.db"


def init_translations_db(trans_db_path: Path = DEFAULT_TRANS_DB):
    """Initializes the translations.db schema."""
    conn = sqlite3.connect(trans_db_path)
    cursor = conn.cursor()
    cursor.execute("PRAGMA journal_mode = WAL;")
    cursor.execute("PRAGMA synchronous = NORMAL;")
    
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS translations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        entity_type TEXT NOT NULL,      -- 'movie', 'episode', 'performer', 'category'
        entity_id INTEGER,              -- ID in GPDb.db (movies.id, etc.)
        field TEXT NOT NULL,            -- 'title', 'description', 'notes'
        source_lang TEXT DEFAULT 'en',  -- 'en'
        target_lang TEXT NOT NULL,      -- 'zh-CN', 'zh-TW', 'ja', etc.
        src_text TEXT NOT NULL,         -- Source text
        trans_text TEXT NOT NULL,       -- Translated text
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(entity_type, entity_id, field, target_lang)
    );
    """)
    
    cursor.execute("""
    CREATE INDEX IF NOT EXISTS idx_trans_lookup 
    ON translations(entity_type, entity_id, field, target_lang);
    """)
    cursor.execute("""
    CREATE INDEX IF NOT EXISTS idx_trans_src 
    ON translations(src_text, target_lang);
    """)
    cursor.execute("""
    CREATE INDEX IF NOT EXISTS idx_trans_target_lang 
    ON translations(target_lang);
    """)

    conn.commit()
    conn.close()
    print(f"[*] Translations database initialized at: {trans_db_path}")


def get_pending_batch(main_db_path: Path, trans_db_path: Path, 
                      entity_type: str = "movie", field: str = "title", 
                      target_lang: str = "zh-CN", limit: int = 50, offset: int = 0):
    """
    Fetches untranslated items from GPDb.db that do not yet exist in translations.db.
    Returns a list of dicts: [{'id': ..., 'src': ..., 'context': ...}, ...]
    """
    init_translations_db(trans_db_path)
    
    trans_conn = sqlite3.connect(trans_db_path)
    trans_cur = trans_conn.cursor()
    
    # Existing translated IDs for this entity/field/target_lang
    trans_cur.execute("""
        SELECT entity_id FROM translations 
        WHERE entity_type = ? AND field = ? AND target_lang = ? AND entity_id IS NOT NULL
    """, (entity_type, field, target_lang))
    translated_ids = set(row[0] for row in trans_cur.fetchall())
    trans_conn.close()

    main_conn = sqlite3.connect(main_db_path)
    main_cur = main_conn.cursor()

    items = []
    if entity_type == "movie" and field == "title":
        # Fetch movies with optional description as context
        query = """
            SELECT id, title, description 
            FROM movies 
            WHERE title IS NOT NULL AND TRIM(title) != ''
            ORDER BY id ASC
        """
        for row in main_cur.execute(query):
            m_id, title, desc = row[0], row[1], row[2]
            if m_id not in translated_ids:
                items.append({
                    "id": m_id,
                    "src": title,
                    "context": (desc[:250] + "...") if desc else ""
                })
                if len(items) >= limit:
                    break
    elif entity_type == "movie" and field == "description":
        query = """
            SELECT id, description 
            FROM movies 
            WHERE description IS NOT NULL AND TRIM(description) != ''
            ORDER BY id ASC
        """
        for row in main_cur.execute(query):
            m_id, desc = row[0], row[1]
            if m_id not in translated_ids:
                items.append({
                    "id": m_id,
                    "src": desc,
                    "context": ""
                })
                if len(items) >= limit:
                    break
    elif entity_type == "episode" and field == "description":
        query = """
            SELECT id, description 
            FROM episodes 
            WHERE description IS NOT NULL AND TRIM(description) != ''
            ORDER BY id ASC
        """
        for row in main_cur.execute(query):
            e_id, desc = row[0], row[1]
            if e_id not in translated_ids:
                items.append({
                    "id": e_id,
                    "src": desc,
                    "context": ""
                })
                if len(items) >= limit:
                    break

    main_conn.close()
    return items


def save_translations(trans_db_path: Path, entity_type: str, field: str, 
                      target_lang: str, items: list[dict]):
    """
    Saves a batch of translations into translations.db.
    Each item in items: {'id': entity_id, 'src': src_text, 'trans': translated_text}
    """
    init_translations_db(trans_db_path)
    conn = sqlite3.connect(trans_db_path)
    cur = conn.cursor()
    
    inserted = 0
    updated = 0
    for item in items:
        e_id = item.get("id")
        src = item.get("src", "").strip()
        trans = item.get("trans", "").strip()
        if not src or not trans:
            continue
        
        cur.execute("""
            INSERT INTO translations (entity_type, entity_id, field, source_lang, target_lang, src_text, trans_text, updated_at)
            VALUES (?, ?, ?, 'en', ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(entity_type, entity_id, field, target_lang) 
            DO UPDATE SET 
                trans_text = excluded.trans_text,
                updated_at = CURRENT_TIMESTAMP
        """, (entity_type, e_id, field, target_lang, src, trans))
        inserted += 1

    conn.commit()
    conn.close()
    return inserted


def sync_to_main_db(main_db_path: Path, trans_db_path: Path, target_lang: str = "zh-CN"):
    """
    Syncs translations from translations.db back into GPDb.db columns (e.g. title_zh, description_zh).
    """
    if not trans_db_path.exists():
        print("[-] translations.db does not exist.")
        return

    trans_conn = sqlite3.connect(trans_db_path)
    trans_cur = trans_conn.cursor()

    main_conn = sqlite3.connect(main_db_path)
    main_cur = main_conn.cursor()

    print(f"[*] Syncing {target_lang} translations into {main_db_path.name}...")

    # 1. Sync movie titles
    trans_cur.execute("""
        SELECT entity_id, trans_text FROM translations 
        WHERE entity_type = 'movie' AND field = 'title' AND target_lang = ?
    """, (target_lang,))
    movie_titles = trans_cur.fetchall()
    main_cur.executemany("UPDATE movies SET title_zh = ? WHERE id = ?", 
                         [(t[1], t[0]) for t in movie_titles])
    print(f"    - Synced {len(movie_titles)} movie titles (title_zh)")

    # 2. Sync movie descriptions
    trans_cur.execute("""
        SELECT entity_id, trans_text FROM translations 
        WHERE entity_type = 'movie' AND field = 'description' AND target_lang = ?
    """, (target_lang,))
    movie_descs = trans_cur.fetchall()
    main_cur.executemany("UPDATE movies SET description_zh = ? WHERE id = ?", 
                         [(d[1], d[0]) for d in movie_descs])
    print(f"    - Synced {len(movie_descs)} movie descriptions (description_zh)")

    # 3. Sync episode descriptions
    trans_cur.execute("""
        SELECT entity_id, trans_text FROM translations 
        WHERE entity_type = 'episode' AND field = 'description' AND target_lang = ?
    """, (target_lang,))
    ep_descs = trans_cur.fetchall()
    main_cur.executemany("UPDATE episodes SET description_zh = ? WHERE id = ?", 
                         [(d[1], d[0]) for d in ep_descs])
    print(f"    - Synced {len(ep_descs)} episode descriptions (description_zh)")

    # 4. Sync studio name & cultural descriptions
    trans_cur.execute("""
        SELECT src_text,
               MAX(CASE WHEN field='name' THEN trans_text END),
               MAX(CASE WHEN field='description' THEN trans_text END)
        FROM translations
        WHERE entity_type = 'studio' AND target_lang = ?
        GROUP BY src_text
    """, (target_lang,))
    studio_rows = trans_cur.fetchall()
    if studio_rows:
        main_cur.execute("""
            CREATE TABLE IF NOT EXISTS studios (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                name           TEXT NOT NULL UNIQUE,
                name_zh        TEXT,
                description_zh TEXT,
                updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """)
        main_cur.execute("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
        for s_name, s_name_zh, s_desc_zh in studio_rows:
            main_cur.execute("""
                INSERT INTO studios (name, name_zh, description_zh)
                VALUES (?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET
                    name_zh = excluded.name_zh,
                    description_zh = excluded.description_zh,
                    updated_at = CURRENT_TIMESTAMP
            """, (s_name, s_name_zh, s_desc_zh))
        print(f"    - Synced {len(studio_rows)} studio profiles into studios table (name_zh & description_zh)")

    main_conn.commit()
    main_conn.close()
    trans_conn.close()
    print("[+] Sync completed successfully!")


def print_stats(main_db_path: Path, trans_db_path: Path, target_lang: str = "zh-CN"):
    """Prints translation progress statistics."""
    init_translations_db(trans_db_path)

    trans_conn = sqlite3.connect(trans_db_path)
    trans_cur = trans_conn.cursor()

    main_conn = sqlite3.connect(main_db_path)
    main_cur = main_conn.cursor()

    total_movies = main_cur.execute("SELECT COUNT(*) FROM movies WHERE title IS NOT NULL AND TRIM(title) != ''").fetchone()[0]
    total_movie_descs = main_cur.execute("SELECT COUNT(*) FROM movies WHERE description IS NOT NULL AND TRIM(description) != ''").fetchone()[0]
    total_episodes = main_cur.execute("SELECT COUNT(*) FROM episodes WHERE description IS NOT NULL AND TRIM(description) != ''").fetchone()[0]

    trans_movie_titles = trans_cur.execute("""
        SELECT COUNT(*) FROM translations 
        WHERE entity_type = 'movie' AND field = 'title' AND target_lang = ?
    """, (target_lang,)).fetchone()[0]

    trans_movie_descs = trans_cur.execute("""
        SELECT COUNT(*) FROM translations 
        WHERE entity_type = 'movie' AND field = 'description' AND target_lang = ?
    """, (target_lang,)).fetchone()[0]

    trans_episodes = trans_cur.execute("""
        SELECT COUNT(*) FROM translations 
        WHERE entity_type = 'episode' AND field = 'description' AND target_lang = ?
    """, (target_lang,)).fetchone()[0]

    print("\n================ 翻译数据库状态 (translations.db) ================")
    print(f"目标语言: {target_lang}")
    print(f"1. 影片片名 (movies.title):")
    print(f"   已翻译: {trans_movie_titles:,} / {total_movies:,} 条 ({(trans_movie_titles/total_movies*100) if total_movies else 0:.2f}%)")
    print(f"2. 影片简介 (movies.description):")
    print(f"   已翻译: {trans_movie_descs:,} / {total_movie_descs:,} 条 ({(trans_movie_descs/total_movie_descs*100) if total_movie_descs else 0:.2f}%)")
    print(f"3. 单集简介 (episodes.description):")
    print(f"   已翻译: {trans_episodes:,} / {total_episodes:,} 条 ({(trans_episodes/total_episodes*100) if total_episodes else 0:.2f}%)")
    print("===================================================================\n")

    main_conn.close()
    trans_conn.close()


def main():
    parser = argparse.ArgumentParser(description="GPDb Standalone Translations Database Manager")
    parser.add_argument("--init", action="store_true", help="Initialize translations.db schema")
    parser.add_argument("--stats", action="store_true", help="Show translation progress statistics")
    parser.add_argument("--fetch", action="store_true", help="Fetch a batch of untranslated items as JSON")
    parser.add_argument("--entity", default="movie", choices=["movie", "episode", "performer", "category"], help="Entity type")
    parser.add_argument("--field", default="title", choices=["title", "description", "notes"], help="Field name")
    parser.add_argument("--limit", type=int, default=50, help="Batch limit for fetching")
    parser.add_argument("--lang", default="zh-CN", help="Target language (default: zh-CN)")
    parser.add_argument("--save-json", help="Path to JSON file with translations to save into translations.db")
    parser.add_argument("--sync", action="store_true", help="Sync translations from translations.db into GPDb.db")
    parser.add_argument("--main-db", default=str(DEFAULT_MAIN_DB), help="Path to GPDb.db")
    parser.add_argument("--trans-db", default=str(DEFAULT_TRANS_DB), help="Path to translations.db")

    args = parser.parse_args()
    main_db = Path(args.main_db)
    trans_db = Path(args.trans_db)

    if args.init:
        init_translations_db(trans_db)
    elif args.stats:
        print_stats(main_db, trans_db, args.lang)
    elif args.fetch:
        batch = get_pending_batch(main_db, trans_db, args.entity, args.field, args.lang, args.limit)
        print(json.dumps(batch, ensure_ascii=False, indent=2))
    elif args.save_json:
        with open(args.save_json, "r", encoding="utf-8") as f:
            data = json.load(f)
        count = save_translations(trans_db, args.entity, args.field, args.lang, data)
        print(f"[+] Saved {count} translations into {trans_db.name}")
    elif args.sync:
        sync_to_main_db(main_db, trans_db, args.lang)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
