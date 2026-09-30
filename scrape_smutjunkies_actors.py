#!/usr/bin/env python3
"""
scrape_smutjunkies_actors.py
============================
SmutJunkies (https://www.smutjunkies.com/home.html) 演员详细个人信息高精刮削与增量更新同步引擎。

核心特性:
  1. 支持首页动态监控 (--home): 抓取 home.html 最新推荐与编辑更新的演员档案 (~106+ 位)。
  2. 支持全字母表索引 (--letter A-Z 或 --all): 覆盖全站 6,700+ 位男同成人演员。
  3. 双向增量同步:
     - 发现并收录新演员条目 (--only-new 或默认)。
     - 侦测现有演员档案修订 (--update-existing 或对比更新): 比对艺名、活跃年限、身体数据、厂牌标签变更，自动更新已有记录。
  4. 多级高容错精准对齐:
     - 1级: 精确姓名匹配 (Case-insensitive)
     - 2级: 别名与 AKA 聚合对齐 (匹配 GEVI 别名与 PBC 别名)
     - 3级: 归一化去标点与音调符号对齐
     - 4级: 网页子标题与 Slug 跨名对齐 (如 Nikolai Lazaro -> Tyler Myers)
  5. 预览与安全模式:
     - 默认以 --preview 模式运行，安全检查匹配与字段提取，支持 --save-json 导出。
     - 传入 --apply 时正式写入 GPDb.db 本地数据库。

用法示例:
  # 1. 预览首页最新 5 位演员的刮削与比对结果:
  python3 scrape_smutjunkies_actors.py --preview --limit 5

  # 2. 预览指定演员:
  python3 scrape_smutjunkies_actors.py --name "Camillo Beischel"
  python3 scrape_smutjunkies_actors.py --url "https://www.smutjunkies.com/updates/rhyheim-shabazz-hung-handsome-brazilian-gay-porn-star/"

  # 3. 抓取首页全部最新更新条目并保存到 JSON:
  python3 scrape_smutjunkies_actors.py --preview --home --save-json sj_preview.json

  # 4. 正式应用首页更新至数据库 (增量入库 + 自动更新已刮削条目):
  python3 scrape_smutjunkies_actors.py --apply --home

  # 5. 巡检并更新数据库中已收录的 SmutJunkies 演员:
  python3 scrape_smutjunkies_actors.py --apply --update-existing --limit 20
"""

import argparse
import json
import os
import re
import sqlite3
import subprocess
import sys
import time
import urllib.parse
from html import unescape
from pathlib import Path

# ── 默认数据库探测 ─────────────────────────────────────────────────────────────
def find_default_db() -> str:
    try:
        from db_manager import find_default_db_path
        p = find_default_db_path()
        if p and os.path.exists(p):
            return p
    except Exception:
        pass

    candidates = [
        os.environ.get("GPDB_DB", ""),
        "GPDb.db",
        "../GPDb.db",
        os.path.expanduser("~/Library/Application Support/com.gpdb.app/GPDb.db"),
    ]
    for c in candidates:
        if c and os.path.exists(c):
            return os.path.abspath(c)
    return "GPDb.db"


BASE_URL = "https://www.smutjunkies.com"
HOME_URL = "https://www.smutjunkies.com/home.html"
PROFILES_URL_PREFIX = "https://www.smutjunkies.com/updates/the-men-profiles/profiles-"
DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
)


# ── 网络请求核心 ───────────────────────────────────────────────────────────────
def fetch_html(url: str, timeout: int = 20) -> str:
    """获取页面 HTML 内容。优先调用 curl 保持 HTTP/2 连接，失败时降级回 urllib。"""
    try:
        cmd = [
            "curl", "-s", "-L",
            "--max-time", str(timeout),
            "-H", f"User-Agent: {DEFAULT_USER_AGENT}",
            url
        ]
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 5)
        if res.returncode == 0 and res.stdout and len(res.stdout) > 200:
            return res.stdout
    except Exception:
        pass

    # 降级到 urllib
    try:
        import urllib.request
        req = urllib.request.Request(url, headers={"User-Agent": DEFAULT_USER_AGENT})
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.read().decode("utf-8", errors="ignore")
    except Exception as e:
        sys.stderr.write(f"[WARN] 请求失败 {url}: {e}\n")
        return ""


# ── URL 收集器 ─────────────────────────────────────────────────────────────────
def get_home_profile_urls() -> list[str]:
    """从 home.html 提取所有最新推荐与更新的演员主页 URL (~106 条)。"""
    html = fetch_html(HOME_URL)
    if not html:
        return []
    links = re.findall(r'href=[\'"](https://www\.smutjunkies\.com/updates/[^\'"]+/)[\'"]', html)
    urls = []
    seen = set()
    for l in links:
        if l not in seen and "the-men-profiles" not in l and l != "https://www.smutjunkies.com/updates/":
            seen.add(l)
            urls.append(l)
    return urls


def get_letter_profile_urls(letter: str) -> list[str]:
    """从 profiles-{letter}/ 目录中提取该字母下所有演员的主页 URL。"""
    letter = letter.lower().strip()
    url = f"{PROFILES_URL_PREFIX}{letter}/"
    html = fetch_html(url)
    if not html:
        return []
    links = re.findall(r'<a\s+href=[\'"](https://www\.smutjunkies\.com/updates/[^\'"]+/)[\'"][^>]*>', html)
    urls = []
    seen = set()
    for l in links:
        if l not in seen and "the-men-profiles" not in l and "category" not in l and l != "https://www.smutjunkies.com/updates/":
            seen.add(l)
            urls.append(l)
    return urls


# ── 页面数据深度解析器 ─────────────────────────────────────────────────────────
def parse_smutjunkies_profile(html: str, url: str) -> dict | None:
    """全面解析 SmutJunkies 演员详情页面，提取规范化属性数据。"""
    if not html or "<h1" not in html:
        return None

    # 清理内嵌样式和脚本，杜绝 CSS 属性伪匹配
    clean_html = re.sub(r'<style[^>]*>.*?</style>', '', html, flags=re.DOTALL | re.IGNORECASE)
    clean_html = re.sub(r'<script[^>]*>.*?</script>', '', clean_html, flags=re.DOTALL | re.IGNORECASE)

    # 提取标题/演员主姓名
    h1_m = re.search(r'<h1[^>]*class=[\'"][^\'"]*entry-title[^\'"]*[\'"][^>]*>(.*?)</h1>', clean_html, re.IGNORECASE)
    if not h1_m:
        h1_m = re.search(r'<h1[^>]*>(.*?)</h1>', clean_html, re.IGNORECASE)
    raw_name = h1_m.group(1).strip() if h1_m else ""
    # 清理 HTML 标签与实体
    name = unescape(re.sub(r'<[^>]+>', '', raw_name)).strip().title()
    if not name:
        return None

    # 提取正文容器 .entry-content
    m_body = re.search(r'<div[^>]*class=[\'"][^\'"]*entry-content[^\'"]*[\'"][^>]*>(.*?)</div>', clean_html, re.DOTALL | re.IGNORECASE)
    content = m_body.group(1) if m_body else clean_html

    # 提取 Model ID 与高清定妆照大图
    img_m = re.search(r'src=[\'"](https://www\.smutjunkies\.com/updates/wp-content/uploads/models/[^\'"]+)[\'"]', content)
    image_url = img_m.group(1) if img_m else None
    model_id = None
    if image_url:
        mid_m = re.search(r'/models/(?:\d+/)?(\d+)[a-z]?\.webp', image_url)
        if mid_m:
            model_id = mid_m.group(1)

    # 提取分类标签 (Decades, Studios, Nationality/Ethnicity)
    cat_matches = re.findall(r'rel=[\'"]category tag[\'"]>([^<]+)</a>', clean_html)
    categories = [unescape(c.strip()) for c in cat_matches if c.strip()]
    
    decades = []
    studios = []
    nationalities = []
    for cat in categories:
        if re.search(r'\b\d{4}\'?s\b', cat, re.IGNORECASE):
            decades.append(cat)
        elif any(loc in cat.lower() for loc in ["brazil", "america", "czech", "slovakia", "russia", "european", "latin", "german", "french", "spanish", "colombia"]):
            nationalities.append(cat)
        else:
            studios.append(cat)

    # 提取头衔/一句话介绍 (Tagline)
    tagline_m = re.search(r'<p>([^<]+),\s*<strong>', content)
    tagline = unescape(tagline_m.group(1).strip()) if tagline_m else ""

    # 提取曾用别名 (Known Aliases / aka)
    aliases_m = re.search(r'Known Aliases[^:]*:\s*(?:</strong>)?\s*([^<]*)', content, re.IGNORECASE)
    aliases_raw = unescape(aliases_m.group(1).strip()) if aliases_m else ""
    aliases_list = [a.strip() for a in re.split(r'[,;/、]', aliases_raw) if a.strip()]

    # 提取活跃年代 (Years Active)
    years_m = re.search(r'Years Active:\s*(?:</strong>)?\s*([^<]*)', content, re.IGNORECASE)
    years_active = unescape(years_m.group(1).strip()) if years_m else ""

    # 提取社交媒体直链 (Twitter/X, Instagram, OnlyFans)
    social_matches = re.findall(r'href=[\'"](https?://(?:x\.com|twitter\.com|instagram\.com|onlyfans\.com)[^\'"]+)[\'"]', content)
    social_links = {}
    for s_url in social_matches:
        if "smutjunkies" in s_url.lower():
            continue
        if "x.com" in s_url or "twitter.com" in s_url:
            social_links["x"] = s_url
        elif "instagram.com" in s_url:
            social_links["instagram"] = s_url
        elif "onlyfans.com" in s_url:
            social_links["onlyfans"] = s_url

    # 提取细粒度身体与演出特征
    stats_patterns = [
        ("hair", r'Hair(?:\s*Color)?:\s*([^\r\n<]+)'),
        ("eyes", r'Eye(?:\s*Color)?:\s*([^\r\n<]+)'),
        ("height", r'Height:\s*([^\r\n<]+)'),
        ("weight", r'Weight:\s*([^\r\n<]+)'),
        ("dick_size", r'(?:Dick|Cock)\s*Size:\s*([^\r\n<]+)'),
        ("dick_type", r'(?:(?:Dick|Cock)\s*Type|Thickness):\s*([^\r\n<]+)'),
        ("foreskin", r'(?:Cut\s*or\s*Uncut|Cut):\s*([^\r\n<]+)'),
        ("build", r'(?:Body\s*Build|Build):\s*([^\r\n<]+)'),
        ("sexuality", r'Sexuality:\s*([^\r\n<]+)'),
        ("position", r'Sexual\s*Position:\s*([^\r\n<]+)'),
        ("zodiac", r'Zodiac:\s*([^\r\n<]+)'),
        ("age", r'Age:\s*([^\r\n<]+)'),
        ("shoe", r'Shoe:\s*([^\r\n<]+)'),
        ("body_hair", r'(?:Body\s*Hair|Chest\s*Hair):\s*([^\r\n<]+)'),
        ("tattoos", r'Tattoos?:\s*([^\r\n<]+)'),
        ("piercings", r'Piercings?:\s*([^\r\n<]+)'),
    ]
    stats = {}
    for key, pat in stats_patterns:
        sm = re.search(pat, content, re.IGNORECASE)
        if sm and sm.group(1):
            val = unescape(sm.group(1).strip())
            # 过滤超长异常或 CSS 残留
            if val and len(val) < 80 and not val.startswith(("{", "var(--", "calc(")):
                stats[key] = val

    # 提取代表作品及片目
    film_matches = re.findall(r'<strong>(PHOTOSHOOT[^<]+|BOBBY[^<]+|[A-Z0-9\s,\&#;]{4,50})</strong></a>', content)
    filmography = [unescape(f.strip()) for f in film_matches if f.strip() and "CLICK HERE" not in f and "VIDEO ON-DEMAND" not in f][:15]

    # 提取生平传记/文本段落
    p_tags = re.findall(r'<p>(.*?)</p>', content, re.DOTALL)
    bio_paragraphs = []
    for p in p_tags:
        clean_p = re.sub(r'<[^>]+>', ' ', p)
        clean_p = ' '.join(clean_p.split())
        if len(clean_p) > 50 and not any(k in clean_p.lower() for k in ["years active:", "known aliases:", "video on-demand:", "stats / info:"]):
            bio_paragraphs.append(clean_p)
    bio = "\n\n".join(bio_paragraphs[:3]) if bio_paragraphs else ""

    return {
        "sj_url": url,
        "name": name,
        "model_id": model_id,
        "tagline": tagline,
        "aliases": aliases_list,
        "years_active": years_active,
        "decades": decades,
        "studios": studios,
        "nationality": ", ".join(nationalities) if nationalities else None,
        "stats": stats,
        "social_links": social_links,
        "image_url": image_url,
        "bio": bio,
        "filmography": filmography,
    }


# ── 本地数据库对齐逻辑 ─────────────────────────────────────────────────────────
STUDIO_ABBR_MAP = {
    "corbin fisher": "cf",
    "sean cody": "sc",
    "belami": "ba",
    "bel ami": "ba",
    "active duty": "ad",
    "cockyboys": "cb",
    "next door": "nd",
    "helix": "hx",
    "lucas": "ml",
    "titan": "tm",
    "colt": "cs",
}

STOP_WORDS = {
    "gay", "porn", "star", "naked", "model", "american", "handsome", "muscle",
    "hunk", "cute", "college", "amateur", "bareback", "hung", "buff", "slender",
    "hairy", "daddy", "latin", "russian", "brazilian", "swede", "uncut", "power",
    "bottom", "top", "director", "dancer", "performer", "stud", "slut", "sexy",
    "men", "guy", "guys", "boy", "boys", "smooth", "tall", "fur", "furball"
}


def normalize_apostrophes(s: str) -> str:
    """统一各类单引号与转义符。"""
    return s.replace("’", "'").replace("‘", "'").replace("`", "'").strip()


def normalize_name(s: str) -> str:
    """去除非字母数字、去音调、转小写的归一化函数。"""
    s = normalize_apostrophes(s).lower().strip()
    s = re.sub(r'\s*\([^)]*\)', '', s)  # 去掉括号内容
    s = re.sub(r'[^a-z0-9\s]', '', s)
    return ' '.join(s.split())


def match_gpdb_performer(conn: sqlite3.Connection, sj_data: dict) -> tuple[int | None, str | None, str]:
    """
    智能将 SmutJunkies 演员档案与 GPDb 本地 performers 库匹配。
    返回 (performer_id, performer_name, match_reason)。
    """
    c = conn.cursor()
    raw_name = normalize_apostrophes(sj_data["name"])

    # 生成候选名尝试列表
    candidates = [raw_name]

    # 1. 处理 '@ [Studio]' 命名 (如 Barron @ Corbin Fisher)
    if "@" in raw_name:
        parts = [p.strip() for p in raw_name.split("@", 1)]
        base_name, studio_part = parts[0], parts[1].lower()
        candidates.append(f"{base_name} ({parts[1]})")
        for s_name, abbr in STUDIO_ABBR_MAP.items():
            if s_name in studio_part:
                candidates.append(f"{base_name} ({abbr})")
        candidates.append(base_name)

    # 2. 处理斜杠多名 (如 Quin Quire / Quinn)
    if "/" in raw_name:
        for sub in raw_name.split("/"):
            if sub.strip():
                candidates.append(sub.strip())

    # 3. 逐个候选名进行精确与后缀匹配
    for cand in candidates:
        cand_clean = normalize_apostrophes(cand)
        # 精确匹配
        c.execute("SELECT id, name FROM performers WHERE lower(name) = lower(?)", (cand_clean,))
        row = c.fetchone()
        if row:
            return row[0], row[1], f"exact ({cand})"

        # 后缀年份/厂牌匹配 (如 Steve Collins -> Steve Collins (ba))
        c.execute("SELECT id, name FROM performers WHERE lower(name) LIKE lower(?) || ' (%)'", (cand_clean,))
        rows = c.fetchall()
        if len(rows) == 1:
            return rows[0][0], rows[0][1], f"suffix_match ({rows[0][1]})"
        elif len(rows) > 1:
            # 若有多个同名后缀，结合厂牌标签智能筛选
            studios_in_data = [s.lower() for s in sj_data.get("studios", [])]
            for r_id, r_name in rows:
                for s in studios_in_data:
                    if s in r_name.lower():
                        return r_id, r_name, f"studio_suffix_match ({r_name})"
            return rows[0][0], rows[0][1], f"first_suffix_match ({rows[0][1]})"

    # 4. 别名反查 (SmutJunkies 的 Known Aliases 匹配 GEVI performers.name)
    for alias in sj_data.get("aliases", []):
        alias_clean = normalize_apostrophes(alias)
        if not alias_clean or len(alias_clean) < 3:
            continue
        c.execute("SELECT id, name FROM performers WHERE lower(name) = lower(?)", (alias_clean,))
        a_row = c.fetchone()
        if a_row:
            return a_row[0], a_row[1], f"alias_matched ({alias_clean})"

    # 5. URL Slug 特异关键词匹配 (排除停用词与泛词，避免误匹配 'College', 'American' 等)
    slug = sj_data["sj_url"].rstrip("/").split("/")[-1]
    slug_tokens = [t for t in slug.split("-") if t and t.lower() not in STOP_WORDS and len(t) >= 4]
    
    # 尝试双词组合 (如 tyler myers)
    for idx in range(len(slug_tokens) - 1):
        combo = f"{slug_tokens[idx]} {slug_tokens[idx+1]}"
        c.execute("SELECT id, name FROM performers WHERE lower(name) = lower(?)", (combo,))
        combo_row = c.fetchone()
        if combo_row:
            return combo_row[0], combo_row[1], f"slug_combo ({combo_row[1]})"

    # 6. 跨表查询: 从 performer_pbc_profiles 别名中反查
    try:
        c.execute("SELECT performer_id, pbc_name FROM performer_pbc_profiles WHERE lower(aliases) LIKE ?", (f"%{normalize_name(raw_name)}%",))
        pbc_row = c.fetchone()
        if pbc_row:
            c.execute("SELECT id, name FROM performers WHERE id = ?", (pbc_row[0],))
            g_row = c.fetchone()
            if g_row:
                return g_row[0], g_row[1], "pbc_aliases_cross_match"
    except Exception:
        pass

    return None, None, "unmatched"


# ── 数据库建表与升级 ───────────────────────────────────────────────────────────
def ensure_database_schema(conn: sqlite3.Connection):
    """确保 GPDb 数据库中存在 SmutJunkies 扩展表与 performers.sj_url 字段。"""
    c = conn.cursor()
    # 1. 给 performers 增加 sj_url
    c.execute("PRAGMA table_info(performers)")
    cols = [col[1] for col in c.fetchall()]
    if "sj_url" not in cols:
        c.execute("ALTER TABLE performers ADD COLUMN sj_url TEXT")

    # 2. 创建 performer_sj_profiles 扩展表
    c.execute("""
    CREATE TABLE IF NOT EXISTS performer_sj_profiles (
        performer_id INTEGER PRIMARY KEY,
        sj_url TEXT NOT NULL,
        sj_name TEXT NOT NULL,
        model_id TEXT,
        tagline TEXT,
        aliases TEXT,
        years_active TEXT,
        decades TEXT,
        studios TEXT,
        nationality TEXT,
        height TEXT,
        weight TEXT,
        hair TEXT,
        eyes TEXT,
        build TEXT,
        dick_size TEXT,
        dick_type TEXT,
        foreskin TEXT,
        position TEXT,
        sexuality TEXT,
        zodiac TEXT,
        age TEXT,
        shoe TEXT,
        body_hair TEXT,
        tattoos TEXT,
        piercings TEXT,
        social_links_json TEXT,
        image_url TEXT,
        bio TEXT,
        filmography_json TEXT,
        scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (performer_id) REFERENCES performers(id) ON DELETE CASCADE
    );
    """)
    c.execute("CREATE INDEX IF NOT EXISTS idx_sj_profiles_name ON performer_sj_profiles(sj_name);")
    c.execute("CREATE INDEX IF NOT EXISTS idx_sj_profiles_model_id ON performer_sj_profiles(model_id);")
    conn.commit()


# ── 增量比对与写入核心 ─────────────────────────────────────────────────────────
def sync_performer_to_db(conn: sqlite3.Connection, performer_id: int, sj_data: dict) -> tuple[str, list[str]]:
    """
    将解析得到的 SmutJunkies 数据写入或更新至数据库。
    具备精准的比对能力：不仅插入新条目，还能侦测并更新已有条目的数据。
    返回 (status, changed_fields)。status 为 'inserted' | 'updated' | 'unchanged'。
    """
    c = conn.cursor()
    stats = sj_data.get("stats", {})
    aliases_str = ", ".join(sj_data.get("aliases", []))
    decades_str = ", ".join(sj_data.get("decades", []))
    studios_str = ", ".join(sj_data.get("studios", []))
    socials_json = json.dumps(sj_data.get("social_links", {}), ensure_ascii=False)
    filmography_json = json.dumps(sj_data.get("filmography", []), ensure_ascii=False)

    new_record = {
        "sj_url": sj_data["sj_url"],
        "sj_name": sj_data["name"],
        "model_id": sj_data.get("model_id"),
        "tagline": sj_data.get("tagline"),
        "aliases": aliases_str or None,
        "years_active": sj_data.get("years_active") or None,
        "decades": decades_str or None,
        "studios": studios_str or None,
        "nationality": sj_data.get("nationality"),
        "height": stats.get("height"),
        "weight": stats.get("weight"),
        "hair": stats.get("hair"),
        "eyes": stats.get("eyes"),
        "build": stats.get("build"),
        "dick_size": stats.get("dick_size"),
        "dick_type": stats.get("dick_type"),
        "foreskin": stats.get("foreskin"),
        "position": stats.get("position"),
        "sexuality": stats.get("sexuality"),
        "zodiac": stats.get("zodiac"),
        "age": stats.get("age"),
        "shoe": stats.get("shoe"),
        "body_hair": stats.get("body_hair"),
        "tattoos": stats.get("tattoos"),
        "piercings": stats.get("piercings"),
        "social_links_json": socials_json if socials_json != "{}" else None,
        "image_url": sj_data.get("image_url"),
        "bio": sj_data.get("bio") or None,
        "filmography_json": filmography_json if filmography_json != "[]" else None,
    }

    # 检查是否已存在
    c.execute("SELECT * FROM performer_sj_profiles WHERE performer_id = ?", (performer_id,))
    existing = c.fetchone()

    if not existing:
        # 新增记录
        cols = ["performer_id"] + list(new_record.keys())
        placeholders = ", ".join(["?"] * len(cols))
        vals = [performer_id] + list(new_record.values())
        c.execute(f"INSERT INTO performer_sj_profiles ({', '.join(cols)}) VALUES ({placeholders})", vals)
        c.execute("UPDATE performers SET sj_url = ? WHERE id = ? AND (sj_url IS NULL OR sj_url = '')", (sj_data["sj_url"], performer_id))
        conn.commit()
        return "inserted", list(new_record.keys())

    # 已存在记录：逐字段比对差异
    # 获取现有字段字典
    c.execute("PRAGMA table_info(performer_sj_profiles)")
    col_names = [col[1] for col in c.fetchall()]
    existing_dict = dict(zip(col_names, existing))

    changed = []
    update_pairs = []
    update_vals = []

    for k, new_v in new_record.items():
        old_v = existing_dict.get(k)
        if new_v is not None and str(new_v).strip() != str(old_v or "").strip():
            changed.append(f"{k} ('{old_v}' -> '{new_v}')")
            update_pairs.append(f"{k} = ?")
            update_vals.append(new_v)

    if changed:
        update_pairs.append("updated_at = CURRENT_TIMESTAMP")
        update_vals.append(performer_id)
        sql = f"UPDATE performer_sj_profiles SET {', '.join(update_pairs)} WHERE performer_id = ?"
        c.execute(sql, update_vals)
        c.execute("UPDATE performers SET sj_url = ? WHERE id = ? AND (sj_url IS NULL OR sj_url = '')", (sj_data["sj_url"], performer_id))
        conn.commit()
        return "updated", changed

    return "unchanged", []


# ── 主控制流 ───────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description="SmutJunkies 演员高精刮削与增量更新同步引擎")
    parser.add_argument("--db", default=None, help="GPDb SQLite 数据库路径 (默认自动探测)")
    parser.add_argument("--preview", action="store_true", default=True, help="预览模式 (默认开启，不向数据库写入)")
    parser.add_argument("--apply", action="store_true", help="正式写入模式 (执行写入和更新)")
    parser.add_argument("--home", action="store_true", default=True, help="刮削首页最新更新 (~106 条，默认开启)")
    parser.add_argument("--letter", type=str, default=None, help="按字母抓取指定目录 (如 --letter A)")
    parser.add_argument("--url", type=str, default=None, help="抓取单个指定演员页面 URL")
    parser.add_argument("--name", type=str, default=None, help="按姓名在 SmutJunkies 中搜索并抓取")
    parser.add_argument("--limit", type=int, default=None, help="限制本次抓取和处理的条目数量")
    parser.add_argument("--update-existing", action="store_true", help="专门检查并更新数据库中已收录的 SmutJunkies 演员")
    parser.add_argument("--only-new", action="store_true", help="仅处理未收录的新人，跳过已存在记录")
    parser.add_argument("--save-json", type=str, default=None, help="将抓取到的所有演员结构化数据保存至 JSON 文件")
    parser.add_argument("--delay", type=float, default=0.5, help="请求间隔延迟秒数 (默认 0.5s)")

    args = parser.parse_args()
    if args.apply:
        args.preview = False

    db_path = args.db or find_default_db()
    print("=" * 72)
    print("🌟 SmutJunkies 演员档案高精刮削与跨端同步引擎")
    print(f"📁 目标数据库: {db_path}")
    print(f"⚙️ 运行模式: {'【写入模式 APPLY】' if args.apply else '【预览模式 PREVIEW】(安全只读)'}")
    print("=" * 72)

    conn = sqlite3.connect(db_path)
    if args.apply:
        ensure_database_schema(conn)

    # 收集待抓取目标 URL
    target_urls: list[str] = []

    if args.url:
        target_urls = [args.url]
    elif args.name:
        # 在首页或字母表中查找
        first_char = args.name.strip()[0].lower()
        print(f"🔍 正在从字母目录 profiles-{first_char}/ 中检索演员: {args.name}...")
        letter_urls = get_letter_profile_urls(first_char)
        norm_target = normalize_name(args.name)
        matched_urls = [u for u in letter_urls if norm_target in u.lower().replace("-", " ")]
        if not matched_urls:
            # 搜索 home.html
            home_urls = get_home_profile_urls()
            matched_urls = [u for u in home_urls if norm_target in u.lower().replace("-", " ")]
        target_urls = matched_urls
        if not target_urls:
            print(f"❌ 未在 SmutJunkies 找到匹配演员: {args.name}")
            return
    elif args.update_existing:
        c = conn.cursor()
        c.execute("SELECT sj_url FROM performer_sj_profiles WHERE sj_url IS NOT NULL AND sj_url != ''")
        target_urls = [r[0] for r in c.fetchall()]
        print(f"🔄 库内巡检模式: 共有 {len(target_urls)} 位已收录演员等待最新修订检查。")
    elif args.letter:
        print(f"📖 正在获取 profiles-{args.letter}/ 目录列表...")
        target_urls = get_letter_profile_urls(args.letter)
    else:
        print("⚡ 正在从 SmutJunkies home.html 获取最新更新条目列表...")
        target_urls = get_home_profile_urls()

    if args.limit:
        target_urls = target_urls[:args.limit]

    print(f"🎯 本次共排队 {len(target_urls)} 个演员页面等待抓取与对齐。\n")

    results = []
    stats_counter = {"matched": 0, "unmatched": 0, "inserted": 0, "updated": 0, "unchanged": 0}

    for i, url in enumerate(target_urls, 1):
        time.sleep(args.delay)
        html = fetch_html(url)
        profile = parse_smutjunkies_profile(html, url)
        if not profile:
            print(f"[{i}/{len(target_urls)}] ⚠️ 解析失败: {url}")
            continue

        p_id, p_name, reason = match_gpdb_performer(conn, profile)
        profile["matched_performer_id"] = p_id
        profile["matched_performer_name"] = p_name
        profile["match_reason"] = reason

        if p_id:
            stats_counter["matched"] += 1
            if args.apply:
                status, diffs = sync_performer_to_db(conn, p_id, profile)
                stats_counter[status] += 1
                diff_summary = f" (变更: {len(diffs)} 项)" if status == "updated" else ""
                print(f"[{i}/{len(target_urls)}] ✅ [{status.upper()}{diff_summary}] {profile['name']} -> GPDb #{p_id} ({p_name}) [{reason}]")
                if diffs:
                    for d in diffs[:3]:
                        print(f"       ↳ {d}")
            else:
                # 预览模式下检查若写入会是哪种状态
                c = conn.cursor()
                ex = None
                try:
                    c.execute("SELECT sj_name, years_active, height, weight, dick_size FROM performer_sj_profiles WHERE performer_id = ?", (p_id,))
                    ex = c.fetchone()
                except sqlite3.OperationalError:
                    pass
                status_preview = "EXISTING" if ex else "NEW"
                print(f"[{i}/{len(target_urls)}] 👁️ [PREVIEW - {status_preview}] {profile['name']} -> GPDb #{p_id} ({p_name}) [{reason}]")
                print(f"       属性: 活跃年限: {profile['years_active'] or '未知'} | 身体特征: {profile['stats']} | 厂牌: {profile['studios'][:3]}")
        else:
            stats_counter["unmatched"] += 1
            print(f"[{i}/{len(target_urls)}] ❓ [UNMATCHED] {profile['name']} (ModelID: {profile.get('model_id')}) - 库中暂未对齐")

        results.append(profile)

    # 导出 JSON
    if args.save_json:
        with open(args.save_json, "w", encoding="utf-8") as f:
            json.dump(results, f, ensure_ascii=False, indent=2)
        print(f"\n💾 成功将 {len(results)} 位演员的抓取数据保存至: {args.save_json}")

    print("\n" + "=" * 72)
    print("📊 本次刮削任务完成报告:")
    print(f"   • 总抓取页面: {len(results)}")
    print(f"   • 成功匹配本地库: {stats_counter['matched']} 位 (匹配率: {stats_counter['matched'] / max(1, len(results)):.1%})")
    print(f"   • 未匹配: {stats_counter['unmatched']} 位")
    if args.apply:
        print(f"   • 新增入库: {stats_counter['inserted']} 条")
        print(f"   • 更新已有: {stats_counter['updated']} 条")
        print(f"   • 数据未变: {stats_counter['unchanged']} 条")
    else:
        print("   • 提示: 当前为预览模式，未对数据库做出任何改动。添加 --apply 参数以正式应用入库。")
    print("=" * 72)


if __name__ == "__main__":
    main()
