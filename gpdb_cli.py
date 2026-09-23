#!/usr/bin/env python3
"""
GPDb macOS Offline Search CLI
Instant sub-millisecond retrieval on SQLite FTS5.
"""

from __future__ import annotations
import sys
import argparse
import sqlite3
import time
from pathlib import Path

def search_movies(conn: sqlite3.Connection, query: str, limit: int = 15):
    sql = """
    SELECT m.id, m.title, m.studio_name, m.release_year, m.category, m.rating, m.cover_icon
    FROM movies m
    JOIN movies_fts ON m.id = movies_fts.id
    WHERE movies_fts MATCH ?
    ORDER BY m.release_year DESC
    LIMIT ?
    """
    t0 = time.perf_counter()
    cur = conn.cursor()
    try:
        cur.execute(sql, (query, limit))
        rows = cur.fetchall()
    except sqlite3.OperationalError:
        # Fallback to LIKE if FTS syntax error
        cur.execute("""
            SELECT id, title, studio_name, release_year, category, rating, cover_icon
            FROM movies
            WHERE title LIKE ? OR studio_name LIKE ?
            ORDER BY release_year DESC LIMIT ?
        """, (f"%{query}%", f"%{query}%", limit))
        rows = cur.fetchall()
    elapsed = (time.perf_counter() - t0) * 1000

    print(f"\n🎬 电影检索结果 (关键词: '{query}' | 耗时: {elapsed:.2f}ms | 匹配: {len(rows)}条):")
    print("-" * 75)
    if not rows:
        print("   (无匹配电影)")
        return

    for mid, title, studio, year, cat, rating, icon in rows:
        studio_str = f"[{studio}]" if studio else ""
        year_str = f"({year})" if year else ""
        cat_str = f"| 类型: {cat}" if cat else ""
        print(f"  • #{mid:<6d} 《{title}》 {year_str} {studio_str} {cat_str}")

def search_performers(conn: sqlite3.Connection, query: str, limit: int = 15):
    sql = """
    SELECT p.id, p.name, p.height, p.build, p.dick_size, p.tattoos
    FROM performers p
    JOIN performers_fts ON p.id = performers_fts.id
    WHERE performers_fts MATCH ?
    LIMIT ?
    """
    t0 = time.perf_counter()
    cur = conn.cursor()
    try:
        cur.execute(sql, (query, limit))
        rows = cur.fetchall()
    except sqlite3.OperationalError:
        cur.execute("""
            SELECT id, name, height, build, dick_size, tattoos
            FROM performers
            WHERE name LIKE ? LIMIT ?
        """, (f"%{query}%", limit))
        rows = cur.fetchall()
    elapsed = (time.perf_counter() - t0) * 1000

    print(f"\n👤 演员检索结果 (关键词: '{query}' | 耗时: {elapsed:.2f}ms | 匹配: {len(rows)}条):")
    print("-" * 75)
    if not rows:
        print("   (无匹配演员)")
        return

    for pid, name, height, build, dick, tattoos in rows:
        specs = []
        if height: specs.append(f"身高: {height}")
        if build: specs.append(f"体型: {build}")
        if dick: specs.append(f"尺寸: {dick}")
        spec_str = f" ({', '.join(specs)})" if specs else ""
        print(f"  • #{pid:<6d} {name}{spec_str}")

def show_movie_detail(conn: sqlite3.Connection, movie_id: int):
    cur = conn.cursor()
    cur.execute("SELECT * FROM movies WHERE id = ?", (movie_id,))
    m = cur.fetchone()
    if not m:
        print(f"❌ 未找到 ID 为 #{movie_id} 的影片")
        return

    # Column names
    col_names = [d[0] for d in cur.description]
    movie = dict(zip(col_names, m))

    print("=" * 75)
    print(f"🎬 《{movie['title']}》 (#{movie['id']})")
    print("=" * 75)
    print(f"  • 发行厂牌: {movie.get('studio_name') or '未知'}")
    print(f"  • 发行年份: {movie.get('release_year') or '未知'} | 时长: {movie.get('duration_mins') or '?'} 分钟")
    print(f"  • 影片分类: {movie.get('category') or '未分类'} | 评分: {movie.get('rating') or '无'}")
    print(f"  • 封面大图: {movie.get('cover_full') or '无'}")
    
    if movie.get('description'):
        print(f"\n📝 剧情简介:\n  {movie['description']}")

    # Cast
    cur.execute("SELECT performer_id, performer_name FROM movie_performers WHERE movie_id = ?", (movie_id,))
    cast = cur.fetchall()
    if cast:
        print(f"\n👥 参演阵容 ({len(cast)}位):")
        for pid, pname in cast:
            print(f"  - #{pid:<6d} {pname}")

    # Episodes
    cur.execute("SELECT id, title, thumbnail_url, description, action_notes FROM episodes WHERE movie_id = ?", (movie_id,))
    episodes = cur.fetchall()
    if episodes:
        print(f"\n🎞 包含场景/分集 ({len(episodes)}个):")
        for eid, etitle, thumb, edesc, act in episodes:
            print(f"  • Scene #{eid} {etitle}")
            if act: print(f"    动作代码: {act}")
            if edesc: print(f"    场景描述: {edesc[:120]}...")

def show_performer_detail(conn: sqlite3.Connection, performer_id: int):
    cur = conn.cursor()
    cur.execute("SELECT * FROM performers WHERE id = ?", (performer_id,))
    p = cur.fetchone()
    if not p:
        print(f"❌ 未找到 ID 为 #{performer_id} 的演员")
        return

    col_names = [d[0] for d in cur.description]
    perf = dict(zip(col_names, p))

    print("=" * 75)
    print(f"👤 演员档案: {perf['name']} (#{perf['id']})")
    print("=" * 75)
    for k in ["height", "weight", "build", "skin", "hair", "eyes", "dick_size", "foreskin", "tattoos", "notes"]:
        val = perf.get(k)
        if val:
            print(f"  • {k.capitalize()}: {val}")

    # Filmography
    cur.execute("""
        SELECT m.id, m.title, m.studio_name, m.release_year
        FROM movies m
        JOIN movie_performers mp ON m.id = mp.movie_id
        WHERE mp.performer_id = ?
        ORDER BY m.release_year DESC
    """, (performer_id,))
    movies = cur.fetchall()
    print(f"\n🎬 参演作品列表 ({len(movies)} 部收录):")
    for mid, mtitle, mstudio, myear in movies:
        print(f"  - #{mid:<6d} 《{mtitle}》 ({myear or '未知'}) [{mstudio or '未知'}]")

def main():
    parser = argparse.ArgumentParser(description="GPDb macOS Offline Search CLI")
    parser.add_argument("query", nargs="?", default="", help="Search query (movie title or keywords)")
    parser.add_argument("-p", "--performer", type=str, default="", help="Search performer by name")
    parser.add_argument("-m", "--movie-id", type=int, default=None, help="Show detailed movie info by ID")
    parser.add_argument("--performer-id", type=int, default=None, help="Show detailed performer info by ID")
    parser.add_argument("--db", type=str, default="GPDb.db", help="Path to SQLite database")
    parser.add_argument("--stats", action="store_true", help="Show database overview statistics")

    args = parser.parse_args()

    db_file = Path(args.db)
    if not db_file.exists():
        # Fallback check in same directory
        alt = Path(__file__).parent / args.db
        if alt.exists():
            db_file = alt

    if not db_file.exists():
        print(f"❌ 数据库文件不存在: {args.db}")
        print("   请先运行 batch_scraper.py 抓取数据生成 GPDb.db！")
        return

    conn = sqlite3.connect(str(db_file.resolve()))

    if args.stats:
        cur = conn.cursor()
        print(f"\n📊 【GPDb 离线数据库状态统计】({db_file.resolve()})")
        for tbl in ["movies", "performers", "episodes", "movie_performers"]:
            cur.execute(f"SELECT count(*) FROM {tbl}")
            print(f"  • {tbl:<18}: {cur.fetchone()[0]:,}")
        return

    if args.movie_id:
        show_movie_detail(conn, args.movie_id)
    elif args.performer_id:
        show_performer_detail(conn, args.performer_id)
    elif args.performer:
        search_performers(conn, args.performer)
    elif args.query:
        search_movies(conn, args.query)
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
