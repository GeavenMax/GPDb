#!/usr/bin/env python3
"""
scrape_pbc_actors.py
====================
Porn Base Central (PBC) 演员详细个人信息高精刮削与跨端同步引擎。

数据来源:
  https://pbc.xxx/wiki/Category:Pornographic_actors
  包含 1,200+ 位男同成人演员的极其详尽的档案数据：
  - 基础信息: 艺名、本名 (Birth name)、别名 (Aliases)、出生日期、年龄、星座 (Astrology)、出生地、国籍、族裔、母语
  - 职业状态: 出道年份 (Career Start)、活跃状态 (Career Status: Active / Retired)
  - 身体特征: 身高、体重、丁丁尺寸 (含厘米与英寸)、包皮状态 (Foreskin: Cut/Uncut)、发色、瞳色、体型、肤色、臀型、体毛/胸毛/腋毛/阴毛、胡须、纹身、穿孔
  - 演出角色与体位: 角色分布 (Bareback / Safe sex / Creampie: Top/Bottom/Versatile)、表演标签 (Solo/Fetishes/Hardcore)
  - 社交与官方主页: X (Twitter)、Instagram、OnlyFans、官方个人网站
  - 全网数据库对齐: GEVI ID (本库主键直连!)、IAFD ID、IMDb ID、FapHouse ID
  - 高清定妆照: PBC CDN 高清人物大图
  - 人物生平小传: 百科导言人物生平梗概 (Bio)

匹配逻辑:
  1. 【100% 绝对精准】优先从 PBC 词条中提取 GEVI 直链 (如 gayeroticvideoindex.com/performer/27585)，直接对齐 GPDb.performers.id 主键！
  2. 【阶段二级匹配】按演员艺名 / 本名 / 别名标准化精确匹配。
  3. 【阶段三级清洗】去除 (Sean Cody)、(dp)、(aka ...) 等厂牌及别名后缀后进行归一化模糊匹配。

运行模式:
  - 预览测试模式 (默认 / --preview):
      python3 scrape_pbc_actors.py --preview --limit 5
      python3 scrape_pbc_actors.py --name "Adam Killian"
      python3 scrape_pbc_actors.py --name "Blake Mitchell" --save-json preview.json
  - 增量更新同步模式 (--recent):
      # 快速抓取最近 7 天或 30 天内被编辑/修订/新增的演员条目并精准同步
      python3 scrape_pbc_actors.py --recent --days 7 --apply
      python3 scrape_pbc_actors.py --recent --days 30 --recent-limit 100 --apply
  - 库内巡检更新模式 (--update-library):
      # 对本地数据库中已有 PBC 档案的演员进行重新抓取、比对差异并更新最新状态
      python3 scrape_pbc_actors.py --update-library --apply
      python3 scrape_pbc_actors.py --update-library --limit 20
  - 全量/大批次抓取写入模式 (--apply):
      python3 scrape_pbc_actors.py --apply --limit 50
      python3 scrape_pbc_actors.py --apply --only-new --limit 100   # 仅抓取未收录的新人
      python3 scrape_pbc_actors.py --apply --backfill               # 同步回填主表空字段
      python3 scrape_pbc_actors.py --apply --force-backfill         # 强制使用 PBC 数据覆写
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

# ── 默认数据库定位 ─────────────────────────────────────────────────────────────
def find_default_db() -> str:
    """自动探测 GPDb 数据库文件路径。"""
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


BASE_URL = "https://pbc.xxx"
CATEGORY_START_URL = "https://pbc.xxx/wiki/Category:Pornographic_actors"
DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
)


# ── 网络请求核心 (优先使用系统 curl 以杜绝 BunnyCDN SSL EOF 阻断) ──────────────
def fetch_html(url: str, timeout: int = 20) -> str:
    """获取页面 HTML 内容。优先调用 curl 保证 HTTP/2 握手与 CDN 连通性，失败时降级回 urllib。"""
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

    # 降级至 urllib
    import urllib.request
    import ssl
    req = urllib.request.Request(url, headers={"User-Agent": DEFAULT_USER_AGENT})
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, context=ctx, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="ignore")


# ── HTML 辅助清理工具 ─────────────────────────────────────────────────────────
def clean_text(html_str: str | None) -> str:
    if not html_str:
        return ""
    s = re.sub(r'<br\s*/?>', ' ', html_str, flags=re.IGNORECASE)
    s = re.sub(r'<script.*?</script>', '', s, flags=re.DOTALL | re.IGNORECASE)
    s = re.sub(r'<style.*?</style>', '', s, flags=re.DOTALL | re.IGNORECASE)
    s = re.sub(r'<[^>]+>', '', s)
    s = unescape(s)
    s = re.sub(r'\s+', ' ', s).strip()
    return s


def normalize_name_key(s: str) -> str:
    """用于模糊匹配的标准键：小写、去标点、去括号注释。"""
    if not s:
        return ""
    s = re.sub(r'\(.*?\)', '', s)
    s = re.sub(r'[^a-zA-Z0-9\u4e00-\u9fa5]', '', s)
    return s.lower().strip()


# ── 词条解析核心 (提取全维度演员属性) ──────────────────────────────────────────
def parse_actor_page(html: str, page_url: str) -> dict:
    """从 PBC 演员百科详情页 HTML 中提取全维度的个人档案与测量数据。"""
    data = {
        "pbc_url": page_url,
        "name": "",
        "birth_name": None,
        "aliases": [],
        "birth_date": None,
        "age": None,
        "astrology": None,
        "place_of_birth": None,
        "country_of_birth": None,
        "nationality": None,
        "ethnicity": None,
        "languages": [],
        "career_start": None,
        "career_status": None,
        "height": None,
        "weight": None,
        "penis_size": None,
        "foreskin": None,
        "hair": None,
        "eyes": None,
        "body_type": None,
        "skin": None,
        "ass_type": None,
        "butt": None,
        "body_hair": None,
        "facial_hair": None,
        "tattoos": None,
        "piercings": None,
        "roles": {},
        "performance_tags": [],
        "social_links": {},
        "external_ids": {},
        "image_url": None,
        "bio": None,
        "last_edited": None,
        "revision_id": None,
    }

    # 1. 词条标题 / 艺名
    title_m = re.search(r'<h1 id="firstHeading"[^>]*>(.*?)</h1>', html)
    if title_m:
        data["name"] = clean_text(title_m.group(1))

    # 2. 定妆照大图 (来自 infoboxnew)
    img_m = re.search(
        r'<table class="infoboxnew"[^>]*>.*?class="mw-file-description"><img[^>]+src="([^"]+)"',
        html,
        re.DOTALL
    )
    if img_m:
        data["image_url"] = img_m.group(1)

    # 3. 跨库外链与唯一标识符 (GEVI, IAFD, IMDb, FapHouse)
    gevi_m = re.search(r'gayeroticvideoindex\.com/performer/(\d+)', html)
    if gevi_m:
        data["external_ids"]["gevi_id"] = int(gevi_m.group(1))

    iafd_m = re.search(r'iafd\.com/person\.rme/perfid=([^/"]+)', html)
    if iafd_m:
        data["external_ids"]["iafd_id"] = iafd_m.group(1)

    imdb_m = re.search(r'imdb\.com/name/nm(\d+)', html)
    if imdb_m:
        data["external_ids"]["imdb_id"] = imdb_m.group(1)

    fap_m = re.search(r'faphouse\.com/gay/pornstars/([^/?"]+)', html)
    if fap_m:
        data["external_ids"]["faphouse_id"] = fap_m.group(1)

    # 4. 社交与官网外链
    x_m = re.search(r'href="(https://x\.com/[^"]+)"[^>]*>([^<]+)</a>', html)
    if x_m:
        data["social_links"]["x"] = x_m.group(1)

    of_m = re.search(r'href="(https://onlyfans\.com/[^"]+)"', html)
    if of_m:
        data["social_links"]["onlyfans"] = of_m.group(1)

    ig_m = re.search(r'href="(https://(?:www\.)?instagram\.com/[^"]+)"', html)
    if ig_m:
        data["social_links"]["instagram"] = ig_m.group(1)

    web_m = re.search(r'<a rel="nofollow" class="external text" href="([^"]+)">Official website</a>', html)
    if web_m:
        data["social_links"]["website"] = web_m.group(1)

    # 5. 三维测量数据网格 (身高 / 体重 / 丁丁)
    grid_m = re.search(r'grid-template-columns: auto auto auto;.*?(</div>\s*</td>\s*</tr>)', html, re.DOTALL)
    if grid_m:
        grid_html = grid_m.group(0)
        h_vals = re.findall(r'Category:Height[^>]*>([^<]+)</a>', grid_html)
        if h_vals:
            data["height"] = " / ".join(h_vals)
        w_vals = re.findall(r'Category:Weight[^>]*>([^<]+)</a>', grid_html)
        if w_vals:
            data["weight"] = " / ".join(w_vals)
        p_vals = re.findall(r'Category:Penis_size[^>]*>([^<]+)</a>', grid_html)
        if p_vals:
            data["penis_size"] = " / ".join(p_vals)

    # 6. Infobox 表格键值对解析
    ib_match = re.search(r'<table class="infoboxnew"[^>]*>(.*?)</table>', html, re.DOTALL)
    if ib_match:
        ib_html = ib_match.group(1)
        rows = re.findall(r'<tr[^>]*>(.*?)</tr>', ib_html, re.DOTALL)
        for row in rows:
            th_m = re.search(r'<th[^>]*>(.*?)</th>', row, re.DOTALL)
            td_m = re.search(r'<td[^>]*>(.*?)</td>', row, re.DOTALL)
            if not th_m or not td_m:
                continue
            k = clean_text(th_m.group(1)).rstrip(':').lower()
            v = clean_text(td_m.group(1))
            raw_td = td_m.group(1)

            if "birth name" in k:
                data["birth_name"] = v
            elif "aliases" in k:
                data["aliases"] = [a.strip() for a in v.split(",") if a.strip()]
            elif k == "birth":
                # 剔除可能包含的 Astrology 文本
                date_part = re.split(r'\n|Astrology|<br', v)[0].strip()
                data["birth_date"] = date_part
                age_m = re.search(r'\(age\s*(\d+)\)', v)
                if age_m:
                    data["age"] = int(age_m.group(1))
                astro_m = re.search(r'title="(?:Category:)?([A-Za-z]+)"[^>]*><img alt="[A-Za-z]*"', raw_td)
                if astro_m:
                    data["astrology"] = astro_m.group(1)
            elif "place of birth" in k:
                data["place_of_birth"] = v
            elif "country of birth" in k:
                data["country_of_birth"] = v
            elif "ethnicity" in k:
                data["ethnicity"] = v
            elif "nationality" in k:
                data["nationality"] = v
            elif "languages" in k:
                data["languages"] = [l.strip() for l in v.split(",") if l.strip()]
            elif "career start" in k:
                data["career_start"] = v
            elif "career status" in k:
                data["career_status"] = v
            elif k == "hair":
                data["hair"] = v
            elif "eye" in k:
                data["eyes"] = v
            elif "body type" in k or "build" in k:
                data["body_type"] = v
            elif k == "skin":
                data["skin"] = v
            elif "ass type" in k:
                data["ass_type"] = v
            elif k == "butt":
                data["butt"] = v
            elif "foreskin" in k:
                data["foreskin"] = v
            elif any(sub in k for sub in ["body hair", "chest hair", "pubic hair", "ass hair"]):
                if not data["body_hair"]:
                    data["body_hair"] = f"{k}: {v}"
                else:
                    data["body_hair"] += f"; {k}: {v}"
            elif "facial hair" in k:
                data["facial_hair"] = v
            elif "tattoo" in k:
                data["tattoos"] = v
            elif "piercing" in k:
                data["piercings"] = v
            elif "bareback" in k:
                data["roles"]["bareback"] = v
            elif "safe sex" in k:
                data["roles"]["safe_sex"] = v
            elif "creampie" in k:
                data["roles"]["creampie"] = v

    # 7. 表演属性标签列表 (biobox-perf-list)
    perf_lists = re.findall(r'<div class="biobox-perf-list">(.*?)</div>', html, re.DOTALL)
    for plist in perf_lists:
        tags = [clean_text(t) for t in re.findall(r'<a[^>]*>(.*?)</a>', plist)]
        for t in tags:
            if t and t not in data["performance_tags"]:
                data["performance_tags"].append(t)

    # 8. 个人简介正文 (Bio)
    parts = html.split('</table>', 1)
    if len(parts) > 1:
        p_m = re.findall(r'<p>(.*?)</p>', parts[1], re.DOTALL)
        for p in p_m:
            cleaned = clean_text(p)
            cleaned = re.sub(r'\[\d+\]', '', cleaned)  # 去掉维基引用角标 [1][2]
            if len(cleaned) > 25:
                data["bio"] = cleaned
                break

    # 9. 维基修订时间与版本标识 (用于智能比对增量更新)
    lastmod_m = re.search(r'id="footer-info-lastmod">\s*This page was last edited on ([^<.]+)', html)
    if lastmod_m:
        data["last_edited"] = clean_text(lastmod_m.group(1))

    revid_m = re.search(r'wgCurRevisionId":\s*(\d+)', html) or re.search(r'data-mw-revid="(\d+)"', html)
    if revid_m:
        data["revision_id"] = revid_m.group(1)

    return data


# ── 类别索引翻页 (抓取全量 1,200+ 演员目录) ────────────────────────────────────
def fetch_category_actors(max_pages: int = 10, delay: float = 0.5) -> list[tuple[str, str]]:
    """
    遍历 Category:Pornographic_actors，获取全部演员维基页面链接与艺名。
    返回: [(page_url, actor_name), ...]
    """
    actors: list[tuple[str, str]] = []
    seen_urls: set[str] = set()
    current_url = CATEGORY_START_URL
    page_count = 0

    print(f"📖 正在扫描 PBC 演员总目录索引: {CATEGORY_START_URL}", flush=True)

    while current_url and page_count < max_pages:
        page_count += 1
        html = fetch_html(current_url)
        if not html:
            print(f"  ⚠ 页面获取失败: {current_url}", flush=True)
            break

        # 匹配 mw-pages 区域的条目
        mw_match = re.search(r'<div id="mw-pages">(.*?)</div>\s*</div>', html, re.DOTALL)
        content_area = mw_match.group(1) if mw_match else html

        matches = re.findall(r'<li><a href="(/wiki/[^"]+)" title="([^"]+)">([^<]+)</a></li>', content_area)
        page_actors = 0
        for path, title, _ in matches:
            if title.startswith("List of ") or title.startswith("Category:"):
                continue
            full_url = urllib.parse.urljoin(BASE_URL, path)
            if full_url not in seen_urls:
                seen_urls.add(full_url)
                actors.append((full_url, title))
                page_actors += 1

        print(f"  ✓ 第 {page_count} 页提取到 {page_actors} 位演员 (累计: {len(actors)} 位)", flush=True)

        # 查找下一页 next page 链接
        next_m = re.search(r'href="([^"]+)"[^>]*>next page</a>', content_area)
        if next_m:
            next_path = unescape(next_m.group(1))
            current_url = urllib.parse.urljoin(BASE_URL, next_path)
            if delay > 0:
                time.sleep(delay)
        else:
            current_url = None

    print(f"🎉 索引目录扫描完成！共获取到 {len(actors)} 位演员词条。\n", flush=True)
    return actors


# ── 本地数据库对齐匹配逻辑 ───────────────────────────────────────────────────
def match_performer_in_db(conn: sqlite3.Connection, actor_data: dict) -> tuple[int | None, str, str]:
    """
    智能对齐本地数据库 performers 表。
    返回: (matched_id, matched_name, match_reason)
    """
    # 策略 1: GEVI 直链主键精准匹配 (100% 绝对权威)
    gevi_id = actor_data.get("external_ids", {}).get("gevi_id")
    if gevi_id:
        row = conn.execute("SELECT id, name FROM performers WHERE id = ?", (gevi_id,)).fetchone()
        if row:
            return row[0], row[1], f"GEVI ID 直连匹配 (#{gevi_id})"

    actor_name = actor_data.get("name", "").strip()
    if not actor_name:
        return None, "", "无名称"

    # 策略 2: 艺名完全一致匹配
    row = conn.execute(
        "SELECT id, name FROM performers WHERE LOWER(TRIM(name)) = LOWER(?)",
        (actor_name,)
    ).fetchone()
    if row:
        return row[0], row[1], "艺名完全一致"

    # 策略 3: 本名 (Birth Name) 匹配
    birth_name = actor_data.get("birth_name")
    if birth_name:
        row = conn.execute(
            "SELECT id, name FROM performers WHERE LOWER(TRIM(name)) = LOWER(?)",
            (birth_name.strip(),)
        ).fetchone()
        if row:
            return row[0], row[1], f"本名匹配 ({birth_name})"

    # 策略 4: 剥离括号后的归一化模糊匹配 (如 Abe (Sean Cody) -> Abe)
    norm_key = normalize_name_key(actor_name)
    if norm_key:
        candidates = conn.execute(
            "SELECT id, name FROM performers WHERE name LIKE ?",
            (f"%{actor_name.split()[0]}%",)
        ).fetchall()
        for cid, cname in candidates:
            if normalize_name_key(cname) == norm_key:
                return cid, cname, f"清洗归一化匹配 ({cname})"

    # 策略 5: 别名列表对齐
    for alias in actor_data.get("aliases", []):
        row = conn.execute(
            "SELECT id, name FROM performers WHERE LOWER(TRIM(name)) = LOWER(?)",
            (alias.strip(),)
        ).fetchone()
        if row:
            return row[0], row[1], f"别名匹配 ({alias})"

    return None, "", "本地数据库未收录"


# ── 数据库架构扩展与持久化 ───────────────────────────────────────────────────
def ensure_db_schema(conn: sqlite3.Connection) -> None:
    """初始化 PBC 专属扩展数据表与字段。"""
    # 1. 为 performers 表增加 pbc_url 列 (兼容已有客户端)
    cols = [row[1] for row in conn.execute("PRAGMA table_info(performers)")]
    if "pbc_url" not in cols:
        conn.execute("ALTER TABLE performers ADD COLUMN pbc_url TEXT")
        conn.commit()
        print("✓ 已为 performers 表增加 pbc_url 字段", flush=True)

    # 2. 建立详尽的演员百科专属元数据表 performer_pbc_profiles
    conn.execute("""
        CREATE TABLE IF NOT EXISTS performer_pbc_profiles (
            performer_id INTEGER PRIMARY KEY,
            pbc_url TEXT NOT NULL,
            pbc_name TEXT NOT NULL,
            birth_name TEXT,
            aliases TEXT,
            birth_date TEXT,
            age INTEGER,
            astrology TEXT,
            birth_place TEXT,
            country TEXT,
            nationality TEXT,
            ethnicity TEXT,
            languages TEXT,
            career_start TEXT,
            career_status TEXT,
            height TEXT,
            weight TEXT,
            penis_size TEXT,
            foreskin TEXT,
            hair TEXT,
            eyes TEXT,
            build TEXT,
            skin TEXT,
            ass_type TEXT,
            butt TEXT,
            body_hair TEXT,
            facial_hair TEXT,
            tattoos TEXT,
            piercings TEXT,
            roles_json TEXT,
            performance_tags TEXT,
            social_links_json TEXT,
            external_ids_json TEXT,
            image_url TEXT,
            bio TEXT,
            pbc_last_edited TEXT,
            scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (performer_id) REFERENCES performers(id) ON DELETE CASCADE
        );
    """)
    pbc_cols = [row[1] for row in conn.execute("PRAGMA table_info(performer_pbc_profiles)")]
    if "pbc_last_edited" not in pbc_cols:
        conn.execute("ALTER TABLE performer_pbc_profiles ADD COLUMN pbc_last_edited TEXT")
        conn.commit()
    conn.execute("CREATE INDEX IF NOT EXISTS idx_pbc_profiles_name ON performer_pbc_profiles(pbc_name);")
    conn.commit()


def compare_actor_with_db(conn: sqlite3.Connection, performer_id: int, data: dict) -> tuple[bool, list[str]]:
    """
    精确比对抓取到的新数据与本地数据库已有记录的差异。
    返回: (is_existing, diffs)
      is_existing: 是否已在库中收录
      diffs: 变动项的人类可读描述列表
    """
    cursor = conn.execute("SELECT * FROM performer_pbc_profiles WHERE performer_id = ?", (performer_id,))
    row = cursor.fetchone()
    if not row:
        return False, ["首次建档入库"]

    cols = [d[0] for d in cursor.description]
    old = dict(zip(cols, row))
    diffs: list[str] = []

    def check(label: str, old_val, new_val):
        o = str(old_val).strip() if old_val is not None else ""
        n = str(new_val).strip() if new_val is not None else ""
        if o != n:
            if not o and n:
                diffs.append(f"新增{label}: {n}")
            elif o and not n:
                diffs.append(f"移除{label} (原为: {o})")
            else:
                diffs.append(f"{label}: '{o}' -> '{n}'")

    new_aliases = ", ".join(data["aliases"]) if data.get("aliases") else None
    new_languages = ", ".join(data["languages"]) if data.get("languages") else None
    new_roles = json.dumps(data["roles"], ensure_ascii=False) if data.get("roles") else None
    new_tags = ", ".join(data["performance_tags"]) if data.get("performance_tags") else None
    new_social = json.dumps(data["social_links"], ensure_ascii=False) if data.get("social_links") else None
    new_ext = json.dumps(data["external_ids"], ensure_ascii=False) if data.get("external_ids") else None

    check("活跃状态", old.get("career_status"), data.get("career_status"))
    check("出道年份", old.get("career_start"), data.get("career_start"))
    check("本名", old.get("birth_name"), data.get("birth_name"))
    check("生日", old.get("birth_date"), data.get("birth_date"))
    check("年龄", old.get("age"), data.get("age"))
    check("星座", old.get("astrology"), data.get("astrology"))
    check("籍贯", old.get("birth_place"), data.get("place_of_birth"))
    check("出生国家", old.get("country"), data.get("country_of_birth"))
    check("国籍", old.get("nationality"), data.get("nationality"))
    check("族裔", old.get("ethnicity"), data.get("ethnicity"))
    check("语言", old.get("languages"), new_languages)
    check("别名/AKA", old.get("aliases"), new_aliases)
    check("身高", old.get("height"), data.get("height"))
    check("体重", old.get("weight"), data.get("weight"))
    check("丁丁尺寸", old.get("penis_size"), data.get("penis_size"))
    check("包皮", old.get("foreskin"), data.get("foreskin"))
    check("发色", old.get("hair"), data.get("hair"))
    check("瞳色", old.get("eyes"), data.get("eyes"))
    check("体型", old.get("build"), data.get("body_type"))
    check("肤色", old.get("skin"), data.get("skin"))
    check("体毛", old.get("body_hair"), data.get("body_hair"))
    check("胡须", old.get("facial_hair"), data.get("facial_hair"))
    check("纹身", old.get("tattoos"), data.get("tattoos"))
    check("穿孔", old.get("piercings"), data.get("piercings"))
    check("攻受定位", old.get("roles_json"), new_roles)
    check("表演标签", old.get("performance_tags"), new_tags)
    check("社交媒体", old.get("social_links_json"), new_social)
    check("权威库ID", old.get("external_ids_json"), new_ext)
    check("定妆大图", old.get("image_url"), data.get("image_url"))

    old_bio = (old.get("bio") or "").strip()
    new_bio = (data.get("bio") or "").strip()
    if old_bio != new_bio:
        diffs.append("人物小传 (Bio) 已更新")

    return True, diffs


def save_actor_to_db(
    conn: sqlite3.Connection,
    performer_id: int,
    data: dict,
    backfill: bool = False,
    force_backfill: bool = False,
    force: bool = False
) -> tuple[str, list[str]]:
    """
    保存或更新演员档案至数据库。
    返回: (action, diffs)
      action: "insert" | "update" | "unchanged"
      diffs: [变更项描述列表]
    """
    # 1. 确保主表 pbc_url 与 image_url (当主表缺头像时) 处于最新关联
    conn.execute(
        "UPDATE performers SET pbc_url = ? WHERE id = ? AND (pbc_url IS NULL OR pbc_url != ?)",
        (data["pbc_url"], performer_id, data["pbc_url"])
    )
    if data.get("image_url") and data["image_url"].strip():
        conn.execute(
            "UPDATE performers SET image_url = ? WHERE id = ? AND (image_url IS NULL OR trim(image_url) = '')",
            (data["image_url"].strip(), performer_id)
        )

    is_existing, diffs = compare_actor_with_db(conn, performer_id, data)

    new_aliases = ", ".join(data["aliases"]) if data.get("aliases") else None
    new_languages = ", ".join(data["languages"]) if data.get("languages") else None
    new_roles = json.dumps(data["roles"], ensure_ascii=False) if data.get("roles") else None
    new_tags = ", ".join(data["performance_tags"]) if data.get("performance_tags") else None
    new_social = json.dumps(data["social_links"], ensure_ascii=False) if data.get("social_links") else None
    new_ext = json.dumps(data["external_ids"], ensure_ascii=False) if data.get("external_ids") else None
    last_edited = data.get("last_edited")

    if not is_existing:
        conn.execute("""
            INSERT INTO performer_pbc_profiles (
                performer_id, pbc_url, pbc_name, birth_name, aliases,
                birth_date, age, astrology, birth_place, country,
                nationality, ethnicity, languages, career_start, career_status,
                height, weight, penis_size, foreskin, hair,
                eyes, build, skin, ass_type, butt,
                body_hair, facial_hair, tattoos, piercings,
                roles_json, performance_tags, social_links_json, external_ids_json,
                image_url, bio, pbc_last_edited, scraped_at
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, CURRENT_TIMESTAMP
            )
        """, (
            performer_id, data["pbc_url"], data["name"], data.get("birth_name"), new_aliases,
            data.get("birth_date"), data.get("age"), data.get("astrology"), data.get("place_of_birth"), data.get("country_of_birth"),
            data.get("nationality"), data.get("ethnicity"), new_languages, data.get("career_start"), data.get("career_status"),
            data.get("height"), data.get("weight"), data.get("penis_size"), data.get("foreskin"), data.get("hair"),
            data.get("eyes"), data.get("body_type"), data.get("skin"), data.get("ass_type"), data.get("butt"),
            data.get("body_hair"), data.get("facial_hair"), data.get("tattoos"), data.get("piercings"),
            new_roles, new_tags, new_social, new_ext,
            data.get("image_url"), data.get("bio"), last_edited
        ))
        action = "insert"
    elif diffs or force:
        conn.execute("""
            UPDATE performer_pbc_profiles SET
                pbc_url = ?, pbc_name = ?, birth_name = ?, aliases = ?,
                birth_date = ?, age = ?, astrology = ?, birth_place = ?, country = ?,
                nationality = ?, ethnicity = ?, languages = ?, career_start = ?, career_status = ?,
                height = ?, weight = ?, penis_size = ?, foreskin = ?, hair = ?,
                eyes = ?, build = ?, skin = ?, ass_type = ?, butt = ?,
                body_hair = ?, facial_hair = ?, tattoos = ?, piercings = ?,
                roles_json = ?, performance_tags = ?, social_links_json = ?, external_ids_json = ?,
                image_url = ?, bio = ?, pbc_last_edited = ?, scraped_at = CURRENT_TIMESTAMP
            WHERE performer_id = ?
        """, (
            data["pbc_url"], data["name"], data.get("birth_name"), new_aliases,
            data.get("birth_date"), data.get("age"), data.get("astrology"), data.get("place_of_birth"), data.get("country_of_birth"),
            data.get("nationality"), data.get("ethnicity"), new_languages, data.get("career_start"), data.get("career_status"),
            data.get("height"), data.get("weight"), data.get("penis_size"), data.get("foreskin"), data.get("hair"),
            data.get("eyes"), data.get("body_type"), data.get("skin"), data.get("ass_type"), data.get("butt"),
            data.get("body_hair"), data.get("facial_hair"), data.get("tattoos"), data.get("piercings"),
            new_roles, new_tags, new_social, new_ext,
            data.get("image_url"), data.get("bio"), last_edited,
            performer_id
        ))
        action = "update"
        if not diffs and force:
            diffs = ["强制刷新全量字段"]
    else:
        action = "unchanged"

    # 3. 回填主表空缺/覆写生理特征
    if backfill or force_backfill:
        field_mappings = [
            ("eyes", data.get("eyes")),
            ("skin", data.get("skin")),
            ("foreskin", data.get("foreskin")),
            ("tattoos", data.get("tattoos")),
            ("facial_hair", data.get("facial_hair")),
            ("hair", data.get("hair")),
            ("build", data.get("body_type")),
            ("height", data.get("height")),
            ("weight", data.get("weight")),
            ("dick_size", data.get("penis_size")),
            ("image_url", data.get("image_url")),
        ]
        for col, val in field_mappings:
            if val:
                if force_backfill:
                    conn.execute(f"UPDATE performers SET {col} = ? WHERE id = ?", (val, performer_id))
                else:
                    conn.execute(
                        f"UPDATE performers SET {col} = ? WHERE id = ? AND ({col} IS NULL OR trim({col}) = '' OR {col} = 'none available')",
                        (val, performer_id)
                    )

    conn.commit()
    return action, diffs


# ── 最近修订与更新提取 (Special:RecentChanges 增量同步) ───────────────────────
def fetch_recent_changes(
    days: int = 30,
    limit: int = 100,
    delay: float = 0.5
) -> list[tuple[str, str, str]]:
    """
    抓取 MediaWiki Special:RecentChanges 最近编辑的演员条目。
    返回: [(page_url, title, timestamp), ...]
    """
    url = f"{BASE_URL}/wiki/Special:RecentChanges?limit={limit}&days={days}&namespace=0"
    print(f"📡 正在获取 PBC 维基最近 {days} 天内的修订记录: {url}", flush=True)
    html = fetch_html(url)
    if not html:
        print("  ⚠ 无法获取 RecentChanges 页面", flush=True)
        return []

    tables = re.findall(r'(<table[^>]*class="[^"]*mw-changeslist-line[^"]*"[^>]*>.*?</table>)', html, re.DOTALL)
    entries: list[tuple[str, str, str]] = []
    seen = set()

    for t in tables:
        link = re.search(r'<a[^>]*href="(/wiki/[^"]+)"[^>]*class="mw-changeslist-title"[^>]*>([^<]+)</a>', t)
        if link:
            path, title = link.groups()
            title = unescape(title).strip()
            # 过滤掉系统特殊页面与分类
            if any(title.startswith(p) for p in ("Category:", "Template:", "Special:", "MediaWiki:", "Help:", "Talk:", "User:")):
                continue
            if path not in seen:
                seen.add(path)
                ts_m = re.search(r'data-mw-ts="(\d+)"', t)
                ts = ts_m.group(1) if ts_m else ""
                full_url = urllib.parse.urljoin(BASE_URL, path)
                entries.append((full_url, title, ts))

    print(f"✓ 成功定位到 {len(entries)} 个最近编辑/修订的维基页面\n", flush=True)
    return entries


def fetch_library_actors_to_update(conn: sqlite3.Connection, limit: int = 0) -> list[tuple[str, str, int]]:
    """
    从本地数据库获取已有关联 PBC 百科的演员列表，用于本地演员巡检更新。
    返回: [(pbc_url, name, performer_id), ...]
    """
    query = """
        SELECT DISTINCT p.id, p.name, COALESCE(prof.pbc_url, p.pbc_url) as pbc_url
        FROM performers p
        LEFT JOIN performer_pbc_profiles prof ON p.id = prof.performer_id
        WHERE (prof.pbc_url IS NOT NULL AND prof.pbc_url != '')
           OR (p.pbc_url IS NOT NULL AND p.pbc_url != '')
        ORDER BY p.id ASC
    """
    if limit > 0:
        query += f" LIMIT {limit}"

    rows = conn.execute(query).fetchall()
    return [(row[2], row[1], row[0]) for row in rows]


# ── 美化格式输出展示 (含状态与变动项 Diff) ────────────────────────────────────
def print_actor_preview_card(
    idx: int,
    total: int,
    data: dict,
    match_info: tuple[int | None, str, str],
    action: str = "preview",
    diffs: list[str] | None = None
) -> None:
    """在终端渲染精美的演员档案卡片，支持展示 Diff 变动详情与入库操作状态。"""
    pid, pname, reason = match_info

    if action == "insert":
        action_tag = "\033[1;32m[➕ 新增入库]\033[0m"
    elif action == "update":
        action_tag = "\033[1;36m[🔄 变动更新]\033[0m"
    elif action == "unchanged":
        action_tag = "\033[1;90m[✓ 档案最新]\033[0m"
    else:
        action_tag = "\033[1;33m[🔍 安全预览]\033[0m"

    match_tag = f"\033[32m已对齐 GPDb #{pid} 《{pname}》 [{reason}]\033[0m" if pid else f"\033[33m? 未在本地库匹配 [{reason}]\033[0m"

    print("=" * 80)
    print(f"[{idx}/{total}] 🌟 {data.get('name')}  {action_tag}  {match_tag}")
    print(f"🔗 维基直链: {data.get('pbc_url')}")
    if data.get("last_edited"):
        print(f"⏱  最后修订: {data.get('last_edited')}")
    if data.get("image_url"):
        print(f"🖼  定妆照片: {data.get('image_url')}")

    # 若检测到变动项，突出高亮展示变动详情
    if diffs and (action == "update" or (action == "preview" and diffs != ["首次建档入库"])):
        print("\033[36m" + "-" * 80)
        print("⚡ 档案变动侦测 (Diff):")
        for d in diffs:
            print(f"   • {d}")
        print("-" * 80 + "\033[0m")
    elif action == "insert" and diffs:
        print("\033[32m" + "-" * 80)
        print("🌱 首次入库建档")
        print("-" * 80 + "\033[0m")
    else:
        print("-" * 80)

    # 个人概览
    personal_parts = []
    if data.get("birth_name"): personal_parts.append(f"本名: {data['birth_name']}")
    if data.get("birth_date"): personal_parts.append(f"生日: {data['birth_date']}")
    if data.get("astrology"): personal_parts.append(f"星座: {data['astrology']}")
    if data.get("place_of_birth"): personal_parts.append(f"出生地: {data['place_of_birth']}")
    if data.get("nationality"): personal_parts.append(f"国籍: {data['nationality']}")
    if data.get("ethnicity"): personal_parts.append(f"族裔: {data['ethnicity']}")
    if data.get("career_start"): personal_parts.append(f"出道: {data['career_start']}")
    if data.get("career_status"): personal_parts.append(f"状态: {data['career_status']}")
    if personal_parts:
        print("👤 个人背景: " + " | ".join(personal_parts))

    # 身体特征
    body_parts = []
    if data.get("height"): body_parts.append(f"身高: {data['height']}")
    if data.get("weight"): body_parts.append(f"体重: {data['weight']}")
    if data.get("penis_size"): body_parts.append(f"丁丁: {data['penis_size']}")
    if data.get("foreskin"): body_parts.append(f"包皮: {data['foreskin']}")
    if data.get("body_type"): body_parts.append(f"体型: {data['body_type']}")
    if data.get("hair"): body_parts.append(f"发色: {data['hair']}")
    if data.get("eyes"): body_parts.append(f"瞳色: {data['eyes']}")
    if data.get("skin"): body_parts.append(f"肤色: {data['skin']}")
    if data.get("tattoos"): body_parts.append(f"纹身: {data['tattoos']}")
    if body_parts:
        print("📐 身体数据: " + " | ".join(body_parts))

    # 角色与表演风格
    if data.get("roles") or data.get("performance_tags"):
        roles_str = ", ".join(f"{k}: {v}" for k, v in data.get("roles", {}).items())
        tags_str = ", ".join(data.get("performance_tags", [])[:8])
        print(f"🎬 演出特征: 攻受角色 [{roles_str or '无'}] | 专长标签 [{tags_str or '无'}]")

    # 社交与跨库直链
    ext_ids = data.get("external_ids", {})
    ext_str = ", ".join(f"{k.upper()}: {v}" for k, v in ext_ids.items())
    soc_str = ", ".join(f"{k}: {v}" for k, v in data.get("social_links", {}).items())
    print(f"🌐 互联生态: 权威数据库 [{ext_str or '无'}]")
    if soc_str:
        print(f"📱 社交主页: {soc_str}")

    # 人物传记梗概
    if data.get("bio"):
        print(f"📝 简介导言: {data['bio']}")
    print("=" * 80 + "\n")


# ── 命令行主程序 ─────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(
        description="Porn Base Central (PBC) 男同成人演员维基档案全维度刮削与跨端同步工具"
    )
    parser.add_argument("--preview", action="store_true", default=True, help="预览模式 (默认开启，不写数据库)")
    parser.add_argument("--apply", action="store_true", help="写入模式: 真正执行数据库入库与字段更新")
    parser.add_argument("--limit", type=int, default=5, help="限制处理演员数量 (默认 5 位；设为 0 表示不限制)")
    parser.add_argument("--name", type=str, help="针对指定演员精准抓取，例如: --name 'Adam Killian'")
    parser.add_argument("--url", type=str, help="直接指定 PBC 演员主页 URL 进行抓取")
    parser.add_argument("--recent", action="store_true", help="增量更新模式: 从 Special:RecentChanges 获取最近修订/编辑的条目进行更新同步")
    parser.add_argument("--days", type=int, default=30, help="增量更新回溯天数 (默认 30 天)")
    parser.add_argument("--recent-limit", type=int, default=100, help="增量更新抓取修订条目上限 (默认 100 条)")
    parser.add_argument("--update-library", action="store_true", help="库内巡检模式: 仅针对本地数据库已有关联的演员进行重新抓取与最新状态比对更新")
    parser.add_argument("--only-new", action="store_true", help="仅抓取新演员: 跳过本地数据库中已收录的演员条目")
    parser.add_argument("--force", action="store_true", help="强制更新模式: 即使字段内容未检测到变动也强制覆写数据库")
    parser.add_argument("--force-backfill", action="store_true", help="强制回填: 使用 PBC 抓取数据覆盖主表 performers 对应字段 (即使原字段非空)")
    parser.add_argument("--backfill", action="store_true", help="回填主表 performers 缺失的空字段 (瞳色/肤色/包皮/纹身等)")
    parser.add_argument("--save-json", type=str, help="将抓取到的结构化数据另存为 JSON 文件")
    parser.add_argument("--db", type=str, help="指定 GPDb.db 数据库路径 (默认自动探测)")
    parser.add_argument("--delay", type=float, default=0.5, help="请求间隔秒数 (默认 0.5s)")
    parser.add_argument("--max-pages", type=int, default=10, help="目录最大翻页数 (默认 10 页全覆盖)")

    args = parser.parse_args()

    # 如果显式传入 --apply，则关闭纯预览模式
    is_apply_mode = args.apply

    db_path = args.db or find_default_db()
    conn = None
    if os.path.exists(db_path):
        conn = sqlite3.connect(db_path)
        if is_apply_mode:
            ensure_db_schema(conn)

    print("\n🚀 [PBC Scraper] Porn Base Central 演员档案刮削与智能更新引擎启动中...")
    print(f"📂 本地关联数据库: {db_path} (存在: {os.path.exists(db_path)})")
    print(f"⚙️ 运行模式: {'【正式存盘写入模式 --apply】' if is_apply_mode else '【安全预览测试模式 --preview】'}")
    if args.recent:
        print(f"🔄 增量同步模式: 扫描 Special:RecentChanges 最近 {args.days} 天内被编辑/修订的演员词条")
    elif args.update_library:
        print("🔄 库内巡检模式: 针对本地已有 PBC 档案的演员进行最新版本拉取比对更新")
    elif args.only_new:
        print("🌱 仅新增模式: 自动过滤已收录演员，仅检索新演员入库")

    if args.force_backfill:
        print("⚡ 已启用主库强制覆盖回填 (--force-backfill)")
    elif args.backfill and is_apply_mode:
        print("✨ 已启用主库空缺字段智能回填 (--backfill)")

    targets: list[tuple[str, str]] = []

    # 1. 指定单 URL
    if args.url:
        targets = [(args.url, "Custom Actor")]
    # 2. 指定单演员名
    elif args.name:
        clean_name = args.name.replace(" ", "_")
        targets = [(f"{BASE_URL}/wiki/{clean_name}", args.name)]
    # 3. 增量修订同步模式 (从 Special:RecentChanges 提取)
    elif args.recent:
        rc_list = fetch_recent_changes(days=args.days, limit=args.recent_limit, delay=args.delay)
        limit = args.limit if args.limit > 0 else len(rc_list)
        targets = [(url, title) for url, title, _ in rc_list[:limit]]
    # 4. 库内巡检更新模式 (重新同步本地数据库已有 PBC 档案的所有演员)
    elif args.update_library:
        if not conn:
            print("❌ 错误: 库内巡检模式必须连接本地有效数据库", file=sys.stderr)
            sys.exit(1)
        lib_actors = fetch_library_actors_to_update(conn, limit=args.limit)
        targets = [(url, name) for url, name, _ in lib_actors]
        print(f"📋 从本地数据库检索到 {len(targets)} 位已收录 PBC 演员，准备巡检最新状态\n")
    # 5. 全量/部分目录抓取
    else:
        all_actors = fetch_category_actors(max_pages=args.max_pages, delay=args.delay)
        if args.only_new and conn:
            existing_urls = set(
                r[0] for r in conn.execute("SELECT pbc_url FROM performer_pbc_profiles WHERE pbc_url IS NOT NULL").fetchall()
            )
            filtered = [a for a in all_actors if a[0] not in existing_urls]
            print(f"🔍 已过滤掉 {len(all_actors) - len(filtered)} 位库内已有演员，剩余 {len(filtered)} 位新演员待抓取\n")
            all_actors = filtered
        limit = args.limit if args.limit > 0 else len(all_actors)
        targets = all_actors[:limit]

    print(f"🎯 待抓取解析演员任务数: {len(targets)} 位\n")

    results = []
    matched_count = 0
    inserted_count = 0
    updated_count = 0
    unchanged_count = 0
    total = len(targets)

    for idx, (url, display_name) in enumerate(targets, start=1):
        try:
            html = fetch_html(url)
            if not html or "<table class=\"infoboxnew\"" not in html:
                print(f"[{idx}/{total}] ⚠ 无法解析或该条目非演员档案页 (跳过): {url}")
                continue

            actor_data = parse_actor_page(html, url)
            if not actor_data["name"]:
                actor_data["name"] = display_name

            match_info = (None, "", "未连接数据库")
            action = "preview"
            diffs = []

            if conn:
                match_info = match_performer_in_db(conn, actor_data)
                pid = match_info[0]
                if pid:
                    matched_count += 1
                    is_existing, detected_diffs = compare_actor_with_db(conn, pid, actor_data)
                    diffs = detected_diffs

                    if is_apply_mode:
                        action, diffs = save_actor_to_db(
                            conn,
                            pid,
                            actor_data,
                            backfill=args.backfill,
                            force_backfill=args.force_backfill,
                            force=args.force
                        )
                        if action == "insert":
                            inserted_count += 1
                        elif action == "update":
                            updated_count += 1
                        elif action == "unchanged":
                            unchanged_count += 1
                    else:
                        if not is_existing:
                            action = "insert"
                            inserted_count += 1
                        elif detected_diffs:
                            action = "update"
                            updated_count += 1
                        else:
                            action = "unchanged"
                            unchanged_count += 1

            print_actor_preview_card(idx, total, actor_data, match_info, action=action, diffs=diffs)
            results.append(actor_data)

            if args.save_json and idx % 50 == 0:
                try:
                    Path(args.save_json).write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
                except Exception:
                    pass

            if idx % 20 == 0 or idx == total:
                pct = (idx / total * 100) if total else 100.0
                print(f"\n📊 [进度统计 {idx}/{total} ({pct:.1f}%)] 匹配: {matched_count} | ➕新增: {inserted_count} | 🔄更新: {updated_count} | ✓最新: {unchanged_count}\n", flush=True)

            if idx < total and args.delay > 0:
                time.sleep(args.delay)

        except Exception as e:
            print(f"[{idx}/{total}] ❌ 解析异常 ({url}): {e}", flush=True)

    # 导出 JSON
    if args.save_json:
        out_path = Path(args.save_json)
        out_path.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"💾 已成功将 {len(results)} 位演员全维度档案保存至: {out_path.resolve()}", flush=True)

    if conn:
        conn.close()

    print("\n" + "=" * 50)
    print("📊 任务汇总报告:")
    print(f"  • 抓取扫描总数: {len(results)} 位")
    print(f"  • 本地成功对齐: {matched_count} 位 (匹配率: {matched_count / (len(results) or 1) * 100:.1f}%)")
    if is_apply_mode:
        print("  • 数据库操作统计:")
        print(f"      ➕ 首次建档入库 (Insert)   : {inserted_count} 位")
        print(f"      🔄 资料变动更新 (Update)   : {updated_count} 位")
        print(f"      ✓ 资料最新跳过 (Unchanged): {unchanged_count} 位")
    else:
        print("  • 数据入库状态: ℹ 处于安全预览模式，未写入数据库文件 (加 --apply 即可执行入库)")
        print(f"      ➕ 预计新增: {inserted_count} 位 | 🔄 预计更新: {updated_count} 位 | ✓ 预计无变动: {unchanged_count} 位")
    print("=" * 50 + "\n")


if __name__ == "__main__":
    main()
