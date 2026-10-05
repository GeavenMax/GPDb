#!/usr/bin/env python3
"""
translate_all_2000_studios.py
-----------------------------
全面翻译 GPDb 数据库中剩余全部 2,143 个厂牌：
1. 自动关联 movies 表提取每个厂牌的【真实年代跨度】与【核心代表作片名/简介】作为权威上下文；
2. 结合同志成人文化史，精准生成贴合行业称谓的中文译名 (name_zh)；
3. 撰写 100~200 字的深度历史文化与美学风格档案 (description_zh)；
4. 双写持久化至 GPDb.db (studios 表) 与 translations.db 独立归档库；
5. 支持断点续传、4 Keys 自动轮换与配额守护。
"""

import sys
import time
import json
import sqlite3
import re
from pathlib import Path

import translate
from translation_manager import save_translations

BASE_DIR = Path(__file__).resolve().parent
GPDB_PATH = BASE_DIR / "GPDb.db"
TRANS_PATH = BASE_DIR / "translations.db"
CONFIG_FILE = BASE_DIR / "translate_config.json"

STUDIO_SYSTEM_PROMPT = """你是一名男同性恋（Gay）成人影视文化与历史文献专职研究员。
你会收到一批厂牌（Studio）及其代表作、年代等一手资料，需要为每个厂牌完成：
1. 精准的简体中文译名（name_zh）：
   - 充分考据厂牌名称的词源、暗语或创立者姓名，结合其代表作题材，给出精准、地道、信达雅的中文定名。
   - 带有 Productions / Films / Media / Video 等词，规范译为“影业 / 制片 / 传媒 / 视界”。
   - 带有俚语、特定性癖或双关的名称，化进中文（如 Bareback->无套，Jock->体育生，Leather->皮革）。
   - 人名开头的厂牌保留人名音译或业界惯用译名加“影业/制片”。
   - 特殊代称（如 various）规范定名为“各厂合辑/多厂牌收录”。
2. 深度历史文化与美学档案（description_zh）：
   - 篇幅约 100~180 字。
   - 必须涵盖：厂牌名称的渊源/隐喻、代表性题材与男色美学定位（如 Twink、Jock、Bear、军警BDSM、街头搭讪等）、活跃历史年代及在同志成人影音发展中的标志性地位。
   - 文风客观典雅、极具文史资料馆专业感，严格男同语境，严禁出现任何针对女性的词汇。

只输出 JSON，格式如下：
{
  "studios": [
    {
      "id": 123,
      "name_zh": "中文定名",
      "description_zh": "100~180字的深度历史与风格解析档案..."
    }
  ]
}
不要输出任何前言、解释或 Markdown 代码块以外的内容。"""


def get_untranslated_studios_with_context(conn, limit=15):
    """提取一批待翻译厂牌并附带代表作真实背景 (优化索引查询)"""
    cur = conn.cursor()
    cur.execute("""
        SELECT id, name 
        FROM studios 
        WHERE name_zh IS NULL OR name_zh = '' 
        ORDER BY id ASC
        LIMIT ?
    """, (limit,))
    rows = cur.fetchall()

    items = []
    for sid, sname in rows:
        cur.execute("""
            SELECT title, release_year, description 
            FROM movies 
            WHERE studio_id = ?
            ORDER BY release_year ASC, id ASC
            LIMIT 5
        """, (sid,))
        movies = cur.fetchall()

        cur.execute("""
            SELECT COUNT(*) FROM movies 
            WHERE studio_id = ?
        """, (sid,))
        movie_count = cur.fetchone()[0]

        movie_samples = []
        for m in movies:
            title = m[0]
            year = m[1] or "年代未知"
            desc_snip = (m[2] or "").strip()[:80]
            if desc_snip:
                movie_samples.append(f"《{title}》({year}, 背景: {desc_snip})")
            else:
                movie_samples.append(f"《{title}》({year})")

        items.append({
            "id": sid,
            "name": sname,
            "count": movie_count,
            "movies_context": "; ".join(movie_samples) if movie_samples else "暂无作品样本"
        })

    return items


def build_user_prompt(items):
    lines = []
    for idx, item in enumerate(items, 1):
        lines.append(
            f"{idx}. 厂牌ID: {item['id']}\n"
            f"   原名: {item['name']}\n"
            f"   收录作品数: {item['count']} 部\n"
            f"   代表作样本: {item['movies_context']}"
        )
    return "请为以下这批厂牌完成中文定名(name_zh)与深度档案(description_zh)：\n\n" + "\n\n".join(lines)


def parse_response(raw_text):
    text = raw_text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```[a-zA-Z]*\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        data = json.loads(text)
    except Exception:
        # 兜底匹配最外层 JSON
        s = text.find("{")
        e = text.rfind("}")
        if s != -1 and e > s:
            data = json.loads(text[s : e + 1])
        else:
            raise ValueError(f"无法解析 JSON: {text[:200]}")

    if isinstance(data, dict):
        return data.get("studios") or data.get("translations") or []
    elif isinstance(data, list):
        return data
    return []


def main():
    import argparse
    parser = argparse.ArgumentParser(description="2000+ Studios Translation Engine")
    parser.add_argument("--max-batches", type=int, default=0, help="最多处理批次数，0为全部")
    args = parser.parse_args()

    print("=" * 75)
    print("🏢 【全库厂牌大提速】2,143 个厂牌全量中文定名与深度档案生成引擎")
    print("=" * 75)

    conn = sqlite3.connect(GPDB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM studios")
    total_studios = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM studios WHERE name_zh IS NOT NULL AND name_zh != ''")
    done_studios = cur.fetchone()[0]
    rem_studios = total_studios - done_studios
    conn.close()

    print(f"📊 数据库现状: 全库总厂牌: {total_studios} 个 | 已汉化: {done_studios} 个 | 待汉化: {rem_studios} 个")
    print("=" * 75)

    if rem_studios == 0:
        print("🎉 全部厂牌已 100% 汉化完毕，无需继续！")
        return

    # 构建带自动轮换的 Gemini Provider
    settings = translate.resolve_settings(None, profile="gemini")
    provider = translate.build_provider(
        settings,
        system_prompt=STUDIO_SYSTEM_PROMPT,
        noun="厂牌档案",
        echo_source=False,
        auto_wait=True
    )

    batch_size = 15
    total_batches = (rem_studios + batch_size - 1) // batch_size
    batch_idx = 0
    total_saved = 0
    start_time = time.time()

    while True:
        conn = sqlite3.connect(GPDB_PATH)
        items = get_untranslated_studios_with_context(conn, limit=batch_size)
        conn.close()

        if not items:
            print("\n🎉 所有待汉化厂牌已全部处理完毕！")
            break

        if args.max_batches > 0 and batch_idx >= args.max_batches:
            print(f"\n⏹️ 已达到指定的最大批次限制 ({args.max_batches} 批)，平稳退出。")
            break

        batch_idx += 1
        prompt = build_user_prompt(items)
        ids = [item["id"] for item in items]
        print(f"\n[{batch_idx}/{total_batches}] 正在研读并翻译 {len(items)} 个厂牌 (IDs: {ids[0]}..{ids[-1]})...", end=" ", flush=True)

        t0 = time.time()
        for attempt in range(3):
            try:
                # 调用 Gemini / DeepSeek 翻译
                payload = {
                    "systemInstruction": {"parts": [{"text": STUDIO_SYSTEM_PROMPT}]},
                    "contents": [{"role": "user", "parts": [{"text": prompt}]}],
                    "generationConfig": {"responseMimeType": "application/json", "temperature": 0.2},
                }
                base_url = provider.base_url or provider.DEFAULT_BASE_URL
                url = f"{base_url}/v1beta/models/{provider.model}:generateContent"
                body = provider._post_with_rotation(url, payload)
                raw_text = "".join(p.get("text", "") for p in body["candidates"][0]["content"]["parts"])
                results = parse_response(raw_text)

                dt = time.time() - t0

                # 映射解析结果
                res_by_id = {}
                for r in results:
                    if isinstance(r, dict) and "id" in r:
                        try:
                            rid = int(r["id"])
                            res_by_id[rid] = r
                        except Exception:
                            pass

                # 批量写入 GPDb.db 和 translations.db
                conn = sqlite3.connect(GPDB_PATH)
                c = conn.cursor()
                archive_names = []
                archive_descs = []
                success_count = 0

                for item in items:
                    sid = item["id"]
                    orig_name = item["name"]
                    res = res_by_id.get(sid)
                    if not res:
                        # 尝试按序号或位置顺位对应
                        res_list = [r for r in results if isinstance(r, dict)]
                        item_pos = items.index(item)
                        if item_pos < len(res_list):
                            res = res_list[item_pos]

                    if res and isinstance(res, dict):
                        name_zh = (res.get("name_zh") or res.get("zh") or "").strip()
                        desc_zh = (res.get("description_zh") or res.get("description") or "").strip()
                        if name_zh:
                            c.execute("""
                                UPDATE studios
                                SET name_zh = ?, description_zh = ?, updated_at = CURRENT_TIMESTAMP
                                WHERE id = ?
                            """, (name_zh, desc_zh, sid))

                            archive_names.append({"id": sid, "src": orig_name, "trans": name_zh})
                            if desc_zh:
                                archive_descs.append({"id": sid, "src": orig_name, "trans": desc_zh})
                            success_count += 1

                conn.commit()
                conn.close()

                # 归档写入 translations.db
                if archive_names:
                    save_translations(TRANS_PATH, "studio", "name", "zh-CN", archive_names)
                if archive_descs:
                    save_translations(TRANS_PATH, "studio", "description", "zh-CN", archive_descs)

                total_saved += success_count
                speed = total_saved / max(time.time() - start_time, 0.1)
                print(f"✅ 成功 ({dt:.1f}s, 入库 {success_count}/{len(items)}) | 累计已汉化厂牌: {total_saved}/{rem_studios} | 速度: {speed:.1f} 个/s")

                time.sleep(1.0)
                break

            except Exception as e:
                print(f"⚠️ 批次重试 (attempt {attempt + 1}/3): {e}")
                time.sleep(3.0)
                if attempt == 2:
                    print(f"❌ 本批放弃，进入下一批。")

    print("\n" + "=" * 75)
    print("🏆 全库 2,433 个厂牌名汉化与深度档案补全已大功告成！")
    print("=" * 75)


if __name__ == "__main__":
    main()
