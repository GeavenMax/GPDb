#!/usr/bin/env python3
"""
clean_title_brackets.py
-----------------------
规则修正与存量数据清洗：
1. 影片中文片名 (movies.title_zh) 本身绝对不要加《书名号》；
2. 剥离 GPDb.db 和 translations.db 中所有已加《书名号》的影片中文名；
3. 保留简介内部引用时的书名号规范。
"""

import sqlite3
import re
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
GPDB_PATH = BASE_DIR / "GPDb.db"
TRANS_PATH = BASE_DIR / "translations.db"


def strip_book_brackets(title: str) -> str:
    """去除片名首尾及外层的书名号《》"""
    if not title:
        return ""
    s = title.strip()
    # 循环去除最外层的《》
    while (s.startswith("《") and s.endswith("》")) or (s.startswith("<") and s.endswith(">")):
        s = s[1:-1].strip()
    return s


def main():
    print("=" * 70)
    print("🧹 启动影片中文片名《书名号》存量数据清洗程序")
    print("=" * 70)

    # 1. 清洗 GPDb.db
    conn = sqlite3.connect(GPDB_PATH)
    cur = conn.cursor()

    cur.execute("SELECT id, title, title_zh FROM movies WHERE title_zh LIKE '%《%' OR title_zh LIKE '%》%'")
    rows = cur.fetchall()
    print(f"📋 GPDb.db 中检测到 {len(rows)} 部带有书名号的影片中文名，正在剥离...")

    cleaned_gpdb = 0
    samples = []
    for mid, orig_title, title_zh in rows:
        cleaned_zh = strip_book_brackets(title_zh)
        if cleaned_zh != title_zh:
            cur.execute("UPDATE movies SET title_zh = ? WHERE id = ?", (cleaned_zh, mid))
            cleaned_gpdb += 1
            if len(samples) < 5:
                samples.append((orig_title, title_zh, cleaned_zh))

    conn.commit()
    conn.close()
    print(f"✅ GPDb.db 清洗完成！成功修复 {cleaned_gpdb} 条记录。")
    for orig, before, after in samples:
        print(f"   原名: {orig:<30} | 修正前: {before:<20} -> 修正后: {after}")

    # 2. 清洗 translations.db
    trans_conn = sqlite3.connect(TRANS_PATH)
    trans_cur = trans_conn.cursor()

    trans_cur.execute("""
        SELECT id, src_text, trans_text 
        FROM translations 
        WHERE entity_type = 'movie' AND field = 'title' 
          AND (trans_text LIKE '%《%' OR trans_text LIKE '%》%')
    """)
    trans_rows = trans_cur.fetchall()
    print(f"\n📋 translations.db 中检测到 {len(trans_rows)} 条带有书名号的片名归档记录，正在剥离...")

    cleaned_trans = 0
    for tid, src_text, trans_text in trans_rows:
        cleaned_zh = strip_book_brackets(trans_text)
        if cleaned_zh != trans_text:
            trans_cur.execute("UPDATE translations SET trans_text = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (cleaned_zh, tid))
            cleaned_trans += 1

    trans_conn.commit()
    trans_conn.close()
    print(f"✅ translations.db 清洗完成！成功修复 {cleaned_trans} 条记录。")

    # 3. 最终核验
    c1 = sqlite3.connect(GPDB_PATH).cursor()
    c1.execute("SELECT COUNT(*) FROM movies WHERE title_zh LIKE '%《%' OR title_zh LIKE '%》%'")
    rem_gpdb = c1.fetchone()[0]

    c2 = sqlite3.connect(TRANS_PATH).cursor()
    c2.execute("SELECT COUNT(*) FROM translations WHERE entity_type = 'movie' AND field = 'title' AND (trans_text LIKE '%《%' OR trans_text LIKE '%》%')")
    rem_trans = c2.fetchone()[0]

    print("\n" + "=" * 70)
    print(f"🏁 清洗核验结果: GPDb.db 剩余带书名号片名: {rem_gpdb} 条 | translations.db 剩余带书名号片名: {rem_trans} 条")
    print("=" * 70)


if __name__ == "__main__":
    main()
