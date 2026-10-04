import sqlite3

# 33 studio IDs that had episodes with studio_id but were not in studios table:
UNMATCHED_STUDIOS = {
    7272: "Str8Hell.com",
    7956: "French Dudes",
    7593: "Raw and Rough",
    7537: "Sweet and Raw",
    7083: "Gay War Games",
    7528: "Kinky Angels",
    7084: "Gangster Fuck",
    8144: "Pegasus Studios",
    7596: "Bare Adventures",
    7540: "SuburbanBoys.com",
    7538: "World of Men",
    7253: "Man Avenue",
    7597: "Bareback Twink",
    7714: "Gay Massage Table",
    7295: "Hot Boys at Play",
    8015: "Naked Marine",
    7604: "GayBangBoy.com",
    7313: "SkinOnSkin.com",
    7844: "French Lads",
    8095: "Bi College Fucks",
    7284: "Gay Public Hardcore",
    7273: "Bentley Race",
    7362: "Lads Next Door",
    7315: "AlexBoys.com",
    7389: "HisFirstHugeCock.com",
    7240: "Game for Gay",
    7581: "Legend Men",
    7082: "Gay Snare",
    7845: "LucioSaints.com",
    7388: "HazeHim.com",
    7555: "DaddyRaunch.com",
    7387: "TwinksForCash",
    7283: "Czech Gay Casting",
}

def main():
    conn = sqlite3.connect("GPDb.db")
    cur = conn.cursor()

    # 1. Check baseline
    cur.execute("SELECT count(*) FROM episodes WHERE studio_name IS NULL OR trim(studio_name) = ''")
    before_null = cur.fetchone()[0]
    print(f"📊 修复前 episodes 表中 studio_name 为空/NULL 的分集总数: {before_null}")

    # 2. Insert missing studios into studios table
    inserted_studios = 0
    for sid, name in UNMATCHED_STUDIOS.items():
        # Check if already exists by site_id or name
        cur.execute("SELECT id FROM studios WHERE site_id = ? OR name = ?", (sid, name))
        existing = cur.fetchone()
        if not existing:
            cur.execute("""
                INSERT INTO studios (name, site_id)
                VALUES (?, ?)
            """, (name, sid))
            inserted_studios += 1
        else:
            # ensure site_id is updated if missing
            cur.execute("UPDATE studios SET site_id = ? WHERE id = ? AND site_id IS NULL", (sid, existing[0]))

    print(f"✅ 补全写入 missing studios 至 studios 表: {inserted_studios} 家厂牌")

    # 3. Update episodes.studio_name where null, joining on studios.site_id
    cur.execute("""
        UPDATE episodes
        SET studio_name = (
            SELECT s.name 
            FROM studios s 
            WHERE s.site_id = episodes.studio_id 
            LIMIT 1
        )
        WHERE (studio_name IS NULL OR trim(studio_name) = '')
          AND studio_id IN (SELECT site_id FROM studios WHERE site_id IS NOT NULL)
    """)
    updated_episodes = cur.rowcount
    print(f"✅ 更新 episodes 表补全 studio_name: {updated_episodes} 部剧集")

    # 4. Check if any NULL remains
    cur.execute("SELECT count(*) FROM episodes WHERE studio_name IS NULL OR trim(studio_name) = ''")
    after_null = cur.fetchone()[0]
    print(f"📊 修复后 episodes 表中 studio_name 为空/NULL 的分集总数: {after_null}")

    # 5. Create indexes
    cur.execute("CREATE INDEX IF NOT EXISTS idx_episodes_studio_id ON episodes(studio_id);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_studios_site_id ON studios(site_id);")
    print("✅ 建立索引 idx_episodes_studio_id 与 idx_studios_site_id")

    conn.commit()

    # 6. Verify key studios
    test_studios = [
        ("Say Uncle", 8064),
        ("Freshmen.net", 7748),
        ("GayRoom", 7544),
        ("GayHoopla", 7550),
        ("College Boy Physicals", 8014),
        ("The Guy Site", 7697),
        ("Str8Hell.com", 7272),
        ("French Dudes", 7956),
    ]

    print("\n🔍 重点片商修复后验证:")
    for name, sid in test_studios:
        cur.execute("SELECT count(*) FROM episodes WHERE studio_name = ?", (name,))
        by_name = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM episodes WHERE studio_id = ?", (sid,))
        by_id = cur.fetchone()[0]
        cur.execute("SELECT min(release_date), max(release_date) FROM episodes WHERE studio_name = ?", (name,))
        d_min, d_max = cur.fetchone()
        print(f"   [{name}] 分集(by studio_name): {by_name:5d} | 分集(by studio_id): {by_id:5d} | 时间跨度: {d_min} ~ {d_max}")

    conn.close()

if __name__ == "__main__":
    main()
