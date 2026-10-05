#!/usr/bin/env python3
"""
GPDb Industry Panorama Precomputation Pipeline
==============================================
Analyzes 50+ years of the gay adult film industry metadata from GPDb.db:
1. Macro Timeline & Media Epochs (Production volume, Duration cliff, HHI Market concentration)
2. Studio Competition & Landscape (Top studios, Market share Streamgraph, Studio niches)
3. Aesthetic & Physicality Archaeology (Build, Body Hair, Facial Hair, Tattoos across decades)
4. Narrative Dynamics & Cultural Tropes (Top categories evolution & Theme river)
5. Creator Ecosystem & Longevity (Survival curves, Evergreen icons, Golden duos, Super-connectors network)

Outputs:
- desktop_client/public/data/industry_analytics.json (for instant client-side rendering)
- stats_industry_meta SQLite table (for local offline SQL queries)
"""

import os
import sys
import json
import sqlite3
import time
from collections import defaultdict
from pathlib import Path

script_dir = Path(__file__).resolve().parent
if (script_dir / "GPDb.db").is_file():
    BASE_DIR = script_dir
elif (script_dir.parent / "GPDb.db").is_file():
    BASE_DIR = script_dir.parent
else:
    BASE_DIR = script_dir

DB_PATH = BASE_DIR / "GPDb.db"
OUTPUT_JSON = BASE_DIR / "desktop_client" / "public" / "data" / "industry_analytics.json"


def get_connection():
    if not DB_PATH.exists():
        raise FileNotFoundError(f"Database not found at {DB_PATH}")
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def compute_macro_timeline(conn):
    """Dimension 1: Macro Production, Duration, and Market Concentration (HHI) (1970-2026)"""
    print("-> Computing Macro Timeline & Media Epochs...")
    cursor = conn.cursor()

    # 1. Yearly movies stats
    cursor.execute("""
        SELECT 
            release_year as year,
            COUNT(*) as movies_count,
            ROUND(AVG(CASE WHEN duration_mins > 10 AND duration_mins < 360 THEN duration_mins END), 1) as avg_duration,
            COUNT(DISTINCT studio_name) as active_studios,
            COUNT(DISTINCT director_name) as active_directors
        FROM movies
        WHERE release_year >= 1970 AND release_year <= 2026
        GROUP BY release_year
        ORDER BY release_year ASC
    """)
    movie_years = {row["year"]: dict(row) for row in cursor.fetchall()}

    # 2. Yearly active performers (via movie_performers)
    cursor.execute("""
        SELECT 
            m.release_year as year,
            COUNT(DISTINCT mp.performer_id) as active_performers
        FROM movie_performers mp
        JOIN movies m ON mp.movie_id = m.id
        WHERE m.release_year >= 1970 AND m.release_year <= 2026
        GROUP BY m.release_year
    """)
    for row in cursor.fetchall():
        if row["year"] in movie_years:
            movie_years[row["year"]]["active_performers"] = row["active_performers"]

    # 3. Yearly episodes count (by episode release_date year)
    cursor.execute("""
        SELECT 
            CAST(SUBSTR(release_date, 1, 4) AS INTEGER) as year,
            COUNT(*) as episodes_count
        FROM episodes
        WHERE release_date IS NOT NULL 
          AND LENGTH(release_date) >= 4
          AND CAST(SUBSTR(release_date, 1, 4) AS INTEGER) >= 1990
          AND CAST(SUBSTR(release_date, 1, 4) AS INTEGER) <= 2026
        GROUP BY year
    """)
    episode_years = {row["year"]: row["episodes_count"] for row in cursor.fetchall()}

    # 4. HHI & CR5 per year
    cursor.execute("""
        SELECT 
            release_year as year,
            studio_name,
            COUNT(*) as cnt
        FROM movies
        WHERE release_year >= 1970 AND release_year <= 2026
          AND studio_name IS NOT NULL AND studio_name != ''
        GROUP BY release_year, studio_name
    """)
    yearly_studios = defaultdict(list)
    for row in cursor.fetchall():
        yearly_studios[row["year"]].append(row["cnt"])

    yearly_hhi = {}
    yearly_cr5 = {}
    for y, counts in yearly_studios.items():
        total = sum(counts)
        if total > 0:
            shares = [(c / total) * 100 for c in counts]
            shares.sort(reverse=True)
            # HHI = sum of squared percentage shares
            hhi = round(sum(s ** 2 for s in shares), 1)
            cr5 = round(sum(shares[:5]), 1)
            yearly_hhi[y] = hhi
            yearly_cr5[y] = cr5

    timeline = []
    for y in range(1970, 2027):
        if y in movie_years:
            item = movie_years[y]
            item["episodes_count"] = episode_years.get(y, 0)
            item["hhi"] = yearly_hhi.get(y, 0)
            item["cr5"] = yearly_cr5.get(y, 0)
            item["avg_duration"] = item["avg_duration"] or 0
            item["active_performers"] = item.get("active_performers", 0)
            timeline.append(item)

    return timeline


def compute_studio_landscape(conn):
    """Dimension 2: Studio Power & Market Share Over Time"""
    print("-> Computing Studio Landscape & Market Shares...")
    cursor = conn.cursor()

    # Get Top 20 studios all-time with Chinese translation if available
    cursor.execute("""
        SELECT 
            m.studio_name,
            s.id as studio_id,
            s.name_zh,
            s.logo_url,
            COUNT(*) as total_movies,
            MIN(m.release_year) as year_start,
            MAX(m.release_year) as year_end
        FROM movies m
        LEFT JOIN studios s ON m.studio_name = s.name
        WHERE m.studio_name IS NOT NULL 
          AND m.studio_name != '' 
          AND m.studio_name != 'various'
          AND m.release_year >= 1970
        GROUP BY m.studio_name
        ORDER BY total_movies DESC
        LIMIT 25
    """)
    top_studios = [dict(row) for row in cursor.fetchall()]
    top_names = {s["studio_name"] for s in top_studios[:15]}

    # Yearly output for top 15 studios
    cursor.execute(f"""
        SELECT 
            release_year as year,
            studio_name,
            COUNT(*) as count
        FROM movies
        WHERE release_year >= 1975 AND release_year <= 2026
          AND studio_name IN ({','.join(['?']*len(top_names))})
        GROUP BY release_year, studio_name
        ORDER BY release_year ASC
    """, list(top_names))

    studio_yearly = defaultdict(lambda: {name: 0 for name in top_names})
    for row in cursor.fetchall():
        studio_yearly[row["year"]][row["studio_name"]] = row["count"]

    yearly_matrix = []
    for y in sorted(studio_yearly.keys()):
        row_dict = {"year": y}
        row_dict.update(studio_yearly[y])
        yearly_matrix.append(row_dict)

    return {
        "top_studios": top_studios,
        "yearly_matrix": yearly_matrix,
        "tracked_studios": list(top_names)
    }


def compute_aesthetics_archaeology(conn):
    """Dimension 3: Aesthetic & Physicality Archaeology (Build, Hair, Tattoos across decades)"""
    print("-> Computing Aesthetics & Physicality Archaeology...")
    cursor = conn.cursor()

    # Decade breakdown of Build
    cursor.execute("""
        SELECT 
            CASE 
                WHEN m.release_year BETWEEN 1970 AND 1979 THEN '1970s'
                WHEN m.release_year BETWEEN 1980 AND 1989 THEN '1980s'
                WHEN m.release_year BETWEEN 1990 AND 1999 THEN '1990s'
                WHEN m.release_year BETWEEN 2000 AND 2009 THEN '2000s'
                WHEN m.release_year BETWEEN 2010 AND 2019 THEN '2010s'
                WHEN m.release_year >= 2020 THEN '2020s'
                ELSE 'Other'
            END as decade,
            p.build,
            COUNT(DISTINCT mp.performer_id) as count
        FROM movie_performers mp
        JOIN movies m ON mp.movie_id = m.id
        JOIN performers p ON mp.performer_id = p.id
        WHERE m.release_year >= 1970 AND m.release_year <= 2026
          AND p.build IS NOT NULL AND p.build != ''
        GROUP BY decade, p.build
    """)
    raw_builds = cursor.fetchall()
    build_decade = defaultdict(lambda: defaultdict(int))
    for row in raw_builds:
        if row["decade"] != 'Other':
            # normalize build
            b = row["build"].split("<br />")[0].strip()
            if b in ("Trim", "Swimmer", "Muscular", "Normal", "Body Builder", "Bear", "Stocky", "Heavy"):
                build_decade[row["decade"]][b] += row["count"]
            else:
                build_decade[row["decade"]]["Other"] += row["count"]

    # Decade breakdown of Body Hair (Smooth vs Light/Medium vs Hairy)
    cursor.execute("""
        SELECT 
            CASE 
                WHEN m.release_year BETWEEN 1970 AND 1979 THEN '1970s'
                WHEN m.release_year BETWEEN 1980 AND 1989 THEN '1980s'
                WHEN m.release_year BETWEEN 1990 AND 1999 THEN '1990s'
                WHEN m.release_year BETWEEN 2000 AND 2009 THEN '2000s'
                WHEN m.release_year BETWEEN 2010 AND 2019 THEN '2010s'
                WHEN m.release_year >= 2020 THEN '2020s'
                ELSE 'Other'
            END as decade,
            p.body_hair,
            COUNT(DISTINCT mp.performer_id) as count
        FROM movie_performers mp
        JOIN movies m ON mp.movie_id = m.id
        JOIN performers p ON mp.performer_id = p.id
        WHERE m.release_year >= 1970 AND m.release_year <= 2026
          AND p.body_hair IS NOT NULL AND p.body_hair != ''
        GROUP BY decade, p.body_hair
    """)
    hair_decade = defaultdict(lambda: defaultdict(int))
    for row in cursor.fetchall():
        if row["decade"] != 'Other':
            raw_h = row["body_hair"].lower()
            if "smooth" in raw_h or "shaved" in raw_h:
                category = "Smooth / 无毛"
            elif "light" in raw_h or "medium" in raw_h or "trimmed" in raw_h:
                category = "Light / 适度体毛"
            elif "heavy" in raw_h or "hairy" in raw_h:
                category = "Hairy / 浓密毛发"
            else:
                category = "Other"
            hair_decade[row["decade"]][category] += row["count"]

    # Tattoo rate by decade
    cursor.execute("""
        SELECT 
            CASE 
                WHEN m.release_year BETWEEN 1970 AND 1979 THEN '1970s'
                WHEN m.release_year BETWEEN 1980 AND 1989 THEN '1980s'
                WHEN m.release_year BETWEEN 1990 AND 1999 THEN '1990s'
                WHEN m.release_year BETWEEN 2000 AND 2009 THEN '2000s'
                WHEN m.release_year BETWEEN 2010 AND 2019 THEN '2010s'
                WHEN m.release_year >= 2020 THEN '2020s'
                ELSE 'Other'
            END as decade,
            CASE 
                WHEN p.tattoos IS NULL OR p.tattoos = '' OR p.tattoos = 'None' OR p.tattoos = 'none' THEN 0 
                ELSE 1 
            END as has_tattoo,
            COUNT(DISTINCT mp.performer_id) as count
        FROM movie_performers mp
        JOIN movies m ON mp.movie_id = m.id
        JOIN performers p ON mp.performer_id = p.id
        WHERE m.release_year >= 1970 AND m.release_year <= 2026
        GROUP BY decade, has_tattoo
    """)
    tattoo_decade = defaultdict(lambda: {"has": 0, "none": 0})
    for row in cursor.fetchall():
        if row["decade"] != 'Other':
            if row["has_tattoo"]:
                tattoo_decade[row["decade"]]["has"] += row["count"]
            else:
                tattoo_decade[row["decade"]]["none"] += row["count"]

    tattoo_stats = []
    for d in ["1970s", "1980s", "1990s", "2000s", "2010s", "2020s"]:
        tot = tattoo_decade[d]["has"] + tattoo_decade[d]["none"]
        pct = round((tattoo_decade[d]["has"] / tot * 100), 1) if tot > 0 else 0
        tattoo_stats.append({
            "decade": d,
            "has_tattoo_count": tattoo_decade[d]["has"],
            "total_count": tot,
            "percentage": pct
        })

    return {
        "build_by_decade": {d: dict(build_decade[d]) for d in ["1970s", "1980s", "1990s", "2000s", "2010s", "2020s"]},
        "hair_by_decade": {d: dict(hair_decade[d]) for d in ["1970s", "1980s", "1990s", "2000s", "2010s", "2020s"]},
        "tattoo_by_decade": tattoo_stats
    }


def compute_tropes_evolution(conn):
    """Dimension 4: Narrative Tropes & Category Shifts (Theme River)"""
    print("-> Computing Tropes & Category Shifts...")
    cursor = conn.cursor()

    # Get category glossary mapping
    cursor.execute("SELECT term, zh FROM category_glossary")
    cat_zh = {row["term"]: row["zh"] for row in cursor.fetchall()}

    # Extract atomic categories per year
    cursor.execute("""
        SELECT 
            release_year as year,
            category
        FROM movies
        WHERE release_year >= 1980 AND release_year <= 2026
          AND category IS NOT NULL AND category != ''
    """)
    
    yearly_cat_counts = defaultdict(lambda: defaultdict(int))
    all_time_cat_counts = defaultdict(int)

    for row in cursor.fetchall():
        y = row["year"]
        # Explode <br /> separated categories
        cats = [c.strip() for c in row["category"].split("<br />") if c.strip()]
        for c in cats:
            if c != "General Hardcore": # Exclude the generic default baseline so specific genres shine
                yearly_cat_counts[y][c] += 1
                all_time_cat_counts[c] += 1

    # Pick Top 12 most prominent non-generic genres
    top_genres = sorted(all_time_cat_counts.items(), key=lambda x: x[1], reverse=True)[:12]
    top_genre_names = [g[0] for g in top_genres]

    # Format for Theme River / Streamgraph
    theme_river = []
    for y in range(1980, 2027):
        entry = {"year": y}
        for g in top_genre_names:
            entry[g] = yearly_cat_counts[y][g]
        theme_river.append(entry)

    top_genres_meta = [
        {"en": g, "zh": cat_zh.get(g, g), "total": all_time_cat_counts[g]} 
        for g in top_genre_names
    ]

    # 4b. Titology & Synopsis Length across decades (Phase 4 NLP & Linguistics)
    cursor.execute("""
        SELECT 
            CASE 
                WHEN release_year BETWEEN 1970 AND 1979 THEN '1970s'
                WHEN release_year BETWEEN 1980 AND 1989 THEN '1980s'
                WHEN release_year BETWEEN 1990 AND 1999 THEN '1990s'
                WHEN release_year BETWEEN 2000 AND 2009 THEN '2000s'
                WHEN release_year BETWEEN 2010 AND 2019 THEN '2010s'
                WHEN release_year >= 2020 THEN '2020s'
                ELSE 'Other'
            END as decade,
            COUNT(*) as count,
            ROUND(AVG(LENGTH(title)), 1) as avg_title_len,
            ROUND(AVG(CASE WHEN description IS NOT NULL THEN LENGTH(description) END), 1) as avg_desc_len,
            ROUND(COUNT(CASE WHEN title LIKE '%:%' THEN 1 END) * 100.0 / COUNT(*), 1) as colon_pct
        FROM movies
        WHERE release_year >= 1970 AND release_year <= 2026
        GROUP BY decade
    """)
    titology_raw = {r["decade"]: dict(r) for r in cursor.fetchall()}
    titology_by_decade = [
        titology_raw[d] for d in ["1970s", "1980s", "1990s", "2000s", "2010s", "2020s"]
        if d in titology_raw
    ]

    # 4c. Key Narrative Tropes Keyword Taxonomy across decades
    trope_definitions = {
        "Military / 军旅军装": ["military", "soldier", "army", "navy", "marine"],
        "Police / 警察制服": ["police", "cop", "officer", "sheriff"],
        "College / 校园宿舍": ["college", "campus", "dorm", "frat", "student"],
        "Roommate / 合租室友": ["roommate", "room-mate", "room mate"],
        "Leather / 皮衣机车": ["leather", "biker", "leatherman"],
        "Vintage / 复古情怀": ["vintage", "retro", "classic film"],
        "Wrestling / 角力摔跤": ["wrestling", "wrestler"],
        "Step / 伪家庭拟态": ["stepbrother", "step-brother", "stepdad", "step-dad"],
        "Gym / 健身运动": ["gym", "locker room", "workout"],
        "Office / 职场白领": ["office", "boss", "businessman", "executive"]
    }
    decades_list = ["1970s", "1980s", "1990s", "2000s", "2010s", "2020s"]
    trope_counts = {k: {d: 0 for d in decades_list} for k in trope_definitions}

    cursor.execute("""
        SELECT release_year, LOWER(title), LOWER(description) 
        FROM movies 
        WHERE release_year >= 1970 AND release_year <= 2026
    """)
    for y, t_raw, d_raw in cursor.fetchall():
        dec = ('1970s' if y < 1980 else 
               '1980s' if y < 1990 else 
               '1990s' if y < 2000 else 
               '2000s' if y < 2010 else 
               '2010s' if y < 2020 else '2020s')
        text = (t_raw or '') + ' ' + (d_raw or '')
        for trope_k, kws in trope_definitions.items():
            if any(kw in text for kw in kws):
                trope_counts[trope_k][dec] += 1

    narrative_keywords_timeline = [
        {"trope": k, "data": trope_counts[k]}
        for k in trope_definitions
    ]

    return {
        "top_genres": top_genres_meta,
        "theme_river": theme_river,
        "titology_by_decade": titology_by_decade,
        "narrative_keywords_timeline": narrative_keywords_timeline
    }


def compute_creator_ecosystem(conn):
    """Dimension 5: Creator Longevity, Survival Analysis & Collaboration Network"""
    print("-> Computing Creator Longevity, Survival Analysis & Network...")
    cursor = conn.cursor()

    # 1. Career Longevity Breakdown (Kaplan-Meier survival proxy)
    cursor.execute("""
        SELECT 
            mp.performer_id,
            MIN(m.release_year) as debut_year,
            MAX(m.release_year) as last_year,
            MAX(m.release_year) - MIN(m.release_year) as span,
            COUNT(DISTINCT m.id) as total_works
        FROM movie_performers mp
        JOIN movies m ON mp.movie_id = m.id
        WHERE m.release_year >= 1970 AND m.release_year <= 2026
        GROUP BY mp.performer_id
    """)
    performers_span = cursor.fetchall()
    
    total_performers = len(performers_span)
    span_brackets = {
        "1 年 (单年流星)": 0,
        "2-3 年 (短期活跃)": 0,
        "4-6 年 (稳定从业)": 0,
        "7-9 年 (资深创作者)": 0,
        "10+ 年 (行业常青树)": 0
    }
    
    # Retention points: active for >= N years
    retention_counts = {1: 0, 2: 0, 3: 0, 5: 0, 10: 0, 15: 0, 20: 0}

    for p in performers_span:
        span = p["span"]
        if span == 0:
            span_brackets["1 年 (单年流星)"] += 1
        elif 1 <= span <= 2:
            span_brackets["2-3 年 (短期活跃)"] += 1
        elif 3 <= span <= 5:
            span_brackets["4-6 年 (稳定从业)"] += 1
        elif 6 <= span <= 8:
            span_brackets["7-9 年 (资深创作者)"] += 1
        else:
            span_brackets["10+ 年 (行业常青树)"] += 1

        for r in retention_counts:
            if span >= (r - 1):
                retention_counts[r] += 1

    survival_curve = [
        {"years": yr, "percentage": round((cnt / total_performers) * 100, 1)}
        for yr, cnt in retention_counts.items()
    ]

    # 2. Top 50 Evergreen Performers
    cursor.execute("""
        SELECT 
            p.id,
            p.name,
            p.image_url,
            MIN(m.release_year) as debut_year,
            MAX(m.release_year) as last_year,
            MAX(m.release_year) - MIN(m.release_year) as career_years,
            COUNT(DISTINCT m.id) as works_count,
            (
                SELECT m2.studio_name 
                FROM movie_performers mp2 
                JOIN movies m2 ON mp2.movie_id = m2.id 
                WHERE mp2.performer_id = p.id AND m2.studio_name != '' 
                GROUP BY m2.studio_name 
                ORDER BY COUNT(*) DESC LIMIT 1
            ) as primary_studio
        FROM movie_performers mp
        JOIN movies m ON mp.movie_id = m.id
        JOIN performers p ON mp.performer_id = p.id
        WHERE m.release_year >= 1970 AND m.release_year <= 2026
        GROUP BY p.id
        ORDER BY works_count DESC
        LIMIT 50
    """)
    top_evergreens = [dict(row) for row in cursor.fetchall()]

    # 3. Top Prolific Directors
    cursor.execute("""
        SELECT 
            d.id,
            d.name,
            COUNT(DISTINCT md.movie_id) as works_count,
            MIN(m.release_year) as year_start,
            MAX(m.release_year) as year_end
        FROM movie_directors md
        JOIN directors d ON md.director_id = d.id
        JOIN movies m ON md.movie_id = m.id
        WHERE m.release_year >= 1970 AND m.release_year <= 2026
        GROUP BY d.id
        ORDER BY works_count DESC
        LIMIT 30
    """)
    top_directors = [dict(row) for row in cursor.fetchall()]

    # 4. Golden Duos (Top 25 Co-star Actor Pairs)
    cursor.execute("""
        SELECT 
            p1.id as id1,
            p1.name as actor1,
            p2.id as id2,
            p2.name as actor2,
            COUNT(*) as common_movies
        FROM movie_performers mp1
        JOIN movie_performers mp2 ON mp1.movie_id = mp2.movie_id AND mp1.performer_id < mp2.performer_id
        JOIN performers p1 ON mp1.performer_id = p1.id
        JOIN performers p2 ON mp2.performer_id = p2.id
        GROUP BY mp1.performer_id, mp2.performer_id
        ORDER BY common_movies DESC
        LIMIT 25
    """)
    golden_duos = [dict(row) for row in cursor.fetchall()]

    # 5. Actor-Director Transitions (演而优则导) - Blazing fast in-memory join
    cursor.execute("""
        SELECT d.id, d.name, COUNT(DISTINCT md.movie_id) as directed_count
        FROM directors d
        JOIN movie_directors md ON d.id = md.director_id
        GROUP BY d.id
        HAVING directed_count >= 5
    """)
    dir_rows = cursor.fetchall()
    dir_map = {r["name"].strip().lower(): dict(r) for r in dir_rows}
    dir_names = list(dir_map.keys())

    actor_counts = {}
    chunk_size = 400
    for i in range(0, len(dir_names), chunk_size):
        chunk = dir_names[i:i+chunk_size]
        cursor.execute(f"""
            SELECT LOWER(p.name) as name_lower, COUNT(DISTINCT mp.movie_id) as acted_count
            FROM performers p
            JOIN movie_performers mp ON p.id = mp.performer_id
            WHERE LOWER(p.name) IN ({','.join(['?']*len(chunk))})
            GROUP BY LOWER(p.name)
        """, chunk)
        for r in cursor.fetchall():
            actor_counts[r["name_lower"]] = r["acted_count"]

    actor_directors = []
    for name_l, d_info in dir_map.items():
        act_cnt = actor_counts.get(name_l, 0)
        if act_cnt >= 5:
            actor_directors.append({
                "director_id": d_info["id"],
                "name": d_info["name"],
                "directed_count": d_info["directed_count"],
                "acted_count": act_cnt,
                "total": d_info["directed_count"] + act_cnt
            })
    actor_directors.sort(key=lambda x: x["total"], reverse=True)
    actor_directors = actor_directors[:25]

    # 6. Network Graph: Top 60 Nodes with Community Factions
    cursor.execute("""
        SELECT p.id, p.name, COUNT(*) as works
        FROM movie_performers mp
        JOIN performers p ON mp.performer_id = p.id
        GROUP BY p.id
        ORDER BY works DESC
        LIMIT 60
    """)
    network_nodes_raw = cursor.fetchall()
    node_ids = [r["id"] for r in network_nodes_raw]

    # Map primary studio for each node to assign cultural faction
    primary_studios = {}
    for nid in node_ids:
        cursor.execute("""
            SELECT m.studio_name, COUNT(*) as cnt
            FROM movie_performers mp
            JOIN movies m ON mp.movie_id = m.id
            WHERE mp.performer_id = ? AND m.studio_name != ''
            GROUP BY m.studio_name
            ORDER BY cnt DESC LIMIT 1
        """, (nid,))
        st_row = cursor.fetchone()
        primary_studios[nid] = (st_row[0] if st_row else "").lower()

    def get_faction(studio_str):
        if any(w in studio_str for w in ["higgins", "staxus", "bel ami", "euro", "czech", "prague"]):
            return 1  # 欧系先锋与布拉格军团
        elif any(w in studio_str for w in ["falcon", "stallion", "house", "catalina", "colt", "titan", "all worlds"]):
            return 0  # 美式黄金大厂门派
        elif any(w in studio_str for w in ["lucas", "door", "helix", "men.com", "cody", "active"]):
            return 2  # 现代流媒体新锐
        else:
            return 3  # 独立走穴与另类硬核

    categories = [
        {"name": "美式经典大厂门派"},
        {"name": "欧系唯美与布拉格"},
        {"name": "现代流媒体新锐"},
        {"name": "独立走穴与另类硬核"}
    ]

    nodes = [
        {
            "id": f"p_{r['id']}",
            "name": r["name"],
            "value": r["works"],
            "category": get_faction(primary_studios.get(r["id"], "")),
            "studio": primary_studios.get(r["id"], "自由走穴")
        }
        for r in network_nodes_raw
    ]

    cursor.execute(f"""
        SELECT 
            mp1.performer_id as id1,
            mp2.performer_id as id2,
            COUNT(*) as weight
        FROM movie_performers mp1
        JOIN movie_performers mp2 ON mp1.movie_id = mp2.movie_id AND mp1.performer_id < mp2.performer_id
        WHERE mp1.performer_id IN ({','.join(['?']*len(node_ids))})
          AND mp2.performer_id IN ({','.join(['?']*len(node_ids))})
        GROUP BY mp1.performer_id, mp2.performer_id
        HAVING weight >= 6
        ORDER BY weight DESC
    """, node_ids + node_ids)

    links = [{"source": f"p_{r['id1']}", "target": f"p_{r['id2']}", "value": r["weight"]} for r in cursor.fetchall()]

    network_graph = {
        "nodes": nodes,
        "links": links,
        "categories": categories
    }

    return {
        "total_analyzed_performers": total_performers,
        "span_brackets": span_brackets,
        "survival_curve": survival_curve,
        "top_evergreens": top_evergreens,
        "top_directors": top_directors,
        "golden_duos": golden_duos,
        "actor_directors": actor_directors,
        "network_graph": network_graph
    }


def save_to_sqlite(conn, analytics_data):
    """Store precomputed JSON in a lightweight SQLite table for offline query consistency."""
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS stats_industry_meta (
            key TEXT PRIMARY KEY,
            data_json TEXT NOT NULL,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cursor.execute("""
        INSERT OR REPLACE INTO stats_industry_meta (key, data_json, updated_at)
        VALUES ('all', ?, CURRENT_TIMESTAMP)
    """, (json.dumps(analytics_data, ensure_ascii=False),))
    conn.commit()
    print("-> Successfully cached analytics into SQLite 'stats_industry_meta'.")


def main():
    start_time = time.time()
    print("=" * 60)
    print("GPDb Industry Panorama Precomputation Starting...")
    print(f"Database: {DB_PATH}")
    print("=" * 60)

    conn = get_connection()
    try:
        timeline = compute_macro_timeline(conn)
        studio_landscape = compute_studio_landscape(conn)
        aesthetics = compute_aesthetics_archaeology(conn)
        tropes = compute_tropes_evolution(conn)
        creators = compute_creator_ecosystem(conn)

        analytics_bundle = {
            "meta": {
                "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
                "total_movies_covered": sum(t["movies_count"] for t in timeline),
                "years_span": "1970 - 2026",
            },
            "timeline": timeline,
            "studios": studio_landscape,
            "aesthetics": aesthetics,
            "tropes": tropes,
            "creators": creators
        }

        # 1. Save to JSON file
        OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)
        with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
            json.dump(analytics_bundle, f, ensure_ascii=False, indent=2)
        print(f"-> Successfully saved JSON to: {OUTPUT_JSON} ({os.path.getsize(OUTPUT_JSON) // 1024} KB)")

        # 2. Save into SQLite database
        save_to_sqlite(conn, analytics_bundle)

        elapsed = round(time.time() - start_time, 2)
        print("=" * 60)
        print(f"Precomputation Complete in {elapsed}s! All 5 Dimensions ready.")
        print("=" * 60)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
