#!/usr/bin/env python3
"""
build_studio_genealogy.py
提取并构建 GPDb 厂牌谱系、母子归属网络与查询 Wiki 数据集。
完全独立运行，生成只读 JSON，不影响主数据库的任何表结构和索引。
"""

import sqlite3
import json
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "GPDb.db"
OUTPUT_PATH = BASE_DIR / "desktop_client" / "public" / "data" / "studio_genealogy.json"

# 行业公认的核心集团/母网基础家谱与权威知识库定义
KNOWN_GROUPS = [
    {
        "id": "aylo_mindgeek",
        "name": "Aylo / MindGeek Empire",
        "name_zh": "Aylo (原MindGeek) 全球男色帝国",
        "type": "conglomerate",
        "headquarters": "卢森堡 / 洛杉矶",
        "founded_year": 2004,
        "description_zh": "全球最大的商业成人娱乐巨无霸，旗下囊括 MEN.com、Sean Cody 及 Next Door Studios 等多大多元顶级流水线品牌，统治全球商业流媒体半壁江山。",
        "core_brands": [
            {"name": "MEN.com", "relation": "subsidiary", "label": "集团旗舰/流水线巨无霸"},
            {"name": "Next Door Studios", "relation": "subsidiary", "label": "都会邻家/剧情主力"},
            {"name": "Next Door Taboo", "relation": "sub_brand", "label": "邻家禁忌副线"},
        ]
    },
    {
        "id": "falcon_studios_group",
        "name": "Falcon Studios Group",
        "name_zh": "猎鹰影视集团 (Falcon / Active Media)",
        "type": "conglomerate",
        "headquarters": "旧金山",
        "founded_year": 1971,
        "description_zh": "全球同性成人影视工业殿堂级奠基者与行业常青藤。半个世纪以来通过兼并与子品牌开拓，建立了囊括经典男色、狂野皮革、竞技型男等多条产品线的宏大传媒王国。",
        "core_brands": [
            {"name": "Falcon Studios", "relation": "subsidiary", "label": "殿堂旗舰/阳光经典"},
            {"name": "Falcon International", "relation": "sub_brand", "label": "欧陆外景产品线"},
            {"name": "Raging Stallion Studios", "relation": "subsidiary", "label": "狂烈种马/野性皮革"},
            {"name": "Cocksure Men", "relation": "sub_brand", "label": "傲然型男系列"},
            {"name": "Jocks", "relation": "sub_brand", "label": "众生体育生影业"},
            {"name": "Jocks Video", "relation": "sub_brand", "label": "竞技健美专线"},
            {"name": "Bound Jocks", "relation": "sub_brand", "label": "束缚重口分支"},
        ]
    },
    {
        "id": "titan_media_group",
        "name": "Titan Media Group",
        "name_zh": "泰坦传媒集团 (Titan Media)",
        "type": "conglomerate",
        "headquarters": "旧金山",
        "founded_year": 1995,
        "description_zh": "由名导 Bruce Cam 创立的雄性荷尔蒙重镇，以粗犷野性、硬汉胡茬及电影级打光质感确立了全美‘泰坦猛男’审美体系，旗下全资收编 MSR 等经典厂牌。",
        "core_brands": [
            {"name": "Titan Media", "relation": "subsidiary", "label": "集团母网/硬汉标杆"},
            {"name": "Titan Men", "relation": "sub_brand", "label": "泰坦男儿专属"},
            {"name": "MSR Videos", "relation": "subsidiary", "label": "全资收编/经典长片"},
            {"name": "Titan Productions", "relation": "sub_brand", "label": "剧情长篇制片"},
        ]
    },
    {
        "id": "the_bro_network",
        "name": "The Bro Network",
        "name_zh": "兄弟联盟网络 (The Bro Network)",
        "type": "network",
        "headquarters": "迈阿密",
        "founded_year": 2015,
        "description_zh": "当代高人气都会男同性恋点播网络，旗下囊括以豪华制服与硬朗剧情闻名的 Masqulin、直男探访及真实同居等多个高质量垂直子厂牌。",
        "core_brands": [
            {"name": "The Bro Network", "relation": "subsidiary", "label": "流媒体母网/总平台"},
            {"name": "Masqulin", "relation": "division", "label": "旗舰部门/制服与粗犷"},
            {"name": "The Guy Site", "relation": "streaming", "label": "线上内容联合站点"},
        ]
    },
    {
        "id": "helix_studios_network",
        "name": "Helix Studios Network",
        "name_zh": "螺旋影视网络 (Helix Studios)",
        "type": "network",
        "headquarters": "圣地亚哥",
        "founded_year": 2010,
        "description_zh": "千禧一代青年偶像与唯美阳光美学的统治级厂牌，开创了顶流专属男优长期签约制度，拥有庞大的子厂牌与衍生明星宇宙。",
        "core_brands": [
            {"name": "Helix Studios", "relation": "subsidiary", "label": "集团旗舰/偶像青年"},
            {"name": "8teenBoy", "relation": "division", "label": "青春初熟专属线"},
            {"name": "Johnny Rapid", "relation": "sub_brand", "label": "一哥强尼拉皮德专属"},
        ]
    },
    {
        "id": "staxus_eurocreme",
        "name": "Eurocreme / Staxus Group",
        "name_zh": "欧陆之光集团 (Staxus / Eurocreme)",
        "type": "conglomerate",
        "headquarters": "布拉格 / 伦敦",
        "founded_year": 1993,
        "description_zh": "欧洲男同性恋影业历史上最大的跨国娱乐航空母舰，横跨英国与捷克，兼并 Staxus、Bulldog 等十余个欧洲本土经典品牌。",
        "core_brands": [
            {"name": "Eurocreme", "relation": "subsidiary", "label": "英伦母网/欧洲老字号"},
            {"name": "Staxus", "relation": "subsidiary", "label": "捷克东欧旗舰/流媒体站"},
            {"name": "Staxus Films", "relation": "division", "label": "长片实体音像发行"},
            {"name": "Bulldog XXX", "relation": "subsidiary", "label": "重型犬系猛男专线"},
            {"name": "AVI Production", "relation": "streaming", "label": "东欧写实长篇代工"},
            {"name": "Eurocreme Prague", "relation": "sub_brand", "label": "布拉格制作中心"},
        ]
    },
    {
        "id": "say_uncle_network",
        "name": "Say Uncle Network",
        "name_zh": "求饶网络 (Say Uncle Network)",
        "type": "network",
        "headquarters": "洛杉矶",
        "founded_year": 2017,
        "description_zh": "互联网时代声势最浩大的禁忌剧情与角色扮演网络，旗下统摄 Bareback Network、Family Dick、Young Perps 等多个轰动性垂直流媒体分站。",
        "core_brands": [
            {"name": "Say Uncle", "relation": "subsidiary", "label": "集团母网/流媒体总汇"},
            {"name": "Bareback Network", "relation": "division", "label": "核心实战无套长片线"},
            {"name": "Family Dick", "relation": "sub_brand", "label": "禁忌亲属剧情副线"},
            {"name": "Young Perps", "relation": "sub_brand", "label": "警匪嫌疑犯概念站"},
            {"name": "Missionary Boys", "relation": "sub_brand", "label": "清规戒律破戒副线"},
        ]
    },
    {
        "id": "gayroom_platform",
        "name": "GayRoom Platform",
        "name_zh": "GayRoom 线上点播大平台",
        "type": "network",
        "headquarters": "欧洲",
        "founded_year": 2011,
        "description_zh": "欧洲规模最大的同性恋综合点播平台之一，聚合发行了 Driveshaft、Man Royale 等几十家欧洲及国际长片制作厂牌的数千部作品。",
        "core_brands": [
            {"name": "GayRoom", "relation": "subsidiary", "label": "线上点播母网平台"},
            {"name": "Driveshaft", "relation": "distributed", "label": "专属长片制作商 (574部)"},
            {"name": "Man Royale", "relation": "distributed", "label": "皇家硬汉长片线 (432部)"},
        ]
    },
    {
        "id": "colt_studio_group",
        "name": "Colt Studio Group",
        "name_zh": "柯尔特传媒集团 (Colt Studio Group)",
        "type": "conglomerate",
        "headquarters": "纽约 / 达拉斯",
        "founded_year": 1967,
        "description_zh": "诞生于1960年代的美国国宝级传奇男色机构，首创肌肉猛男写真与复古工装硬汉风，其全资副牌 Buckshot 统治了野性伐木工与熊族审美。",
        "core_brands": [
            {"name": "Colt Studio", "relation": "subsidiary", "label": "半世纪传奇母厂"},
            {"name": "Buckshot Productions", "relation": "division", "label": "粗犷野性熊族副牌 (223部)"},
        ]
    },
    {
        "id": "kristen_bjorn_empire",
        "name": "Kristen Bjorn Video",
        "name_zh": "比约恩异域美学帝国",
        "type": "conglomerate",
        "headquarters": "布鲁塞尔 / 里约热内卢",
        "founded_year": 1988,
        "description_zh": "传奇摄影宗师 Kristen Bjorn 创立的跨国美学巨擘，足迹遍及南美、东欧、地中海，旗下 Sarava Productions 开创了南美桑巴热带神话。",
        "core_brands": [
            {"name": "Kristen Bjorn Video", "relation": "subsidiary", "label": "宗师主厂牌"},
            {"name": "Sarava Productions", "relation": "division", "label": "南美异域狂想 (313部)"},
        ]
    },
    {
        "id": "carnal_plus",
        "name": "Carnal+ Entertainment",
        "name_zh": "欲念加号媒体网 (Carnal+)",
        "type": "network",
        "headquarters": "蒙特利尔",
        "founded_year": 2016,
        "description_zh": "主打年轻美少年与多视角短剧的现代化线上流媒体聚合体，收编了 Fun-Size Boys、Twink Top、Boy for Sale 等多个细分题材工作室。",
        "core_brands": [
            {"name": "Carnal+", "relation": "subsidiary", "label": "流媒体总网"},
            {"name": "Fun-Size Boys", "relation": "sub_brand", "label": "袖珍美少男副牌"},
            {"name": "Twink Top", "relation": "sub_brand", "label": "纤细攻君副牌"},
            {"name": "Boy for Sale", "relation": "sub_brand", "label": "待售少年副牌"},
        ]
    },
    {
        "id": "kink_com_network",
        "name": "Kink.com / KinkMen",
        "name_zh": "旧金山军械库禁忌帝国 (Kink.com)",
        "type": "conglomerate",
        "headquarters": "旧金山旧军械库",
        "founded_year": 1997,
        "description_zh": "全球 BDSM 工业第一殿堂，以军械库地牢为根据地建立的极限束缚、硬核心理博弈与机器刑具专精王国。",
        "core_brands": [
            {"name": "KinkMen", "relation": "subsidiary", "label": "军械库男同志旗舰"},
            {"name": "Kink Video", "relation": "sub_brand", "label": "奇癖影视早期专线"},
            {"name": "Hardkinks", "relation": "sub_brand", "label": "硬核重口分支"},
        ]
    },
    {
        "id": "str8hell_william_higgins",
        "name": "Higgins / Str8Hell Classic",
        "name_zh": "威廉·希金斯欧陆传奇古典网",
        "type": "conglomerate",
        "headquarters": "巴黎 / 布拉格",
        "founded_year": 1978,
        "description_zh": "跨越半世纪的欧洲古典独立电影制片传奇 William Higgins Productions 及其数字化线上流媒体总站 Str8Hell.com，收录了近万部珍贵历史长片与场景。",
        "core_brands": [
            {"name": "William Higgins Productions", "relation": "subsidiary", "label": "欧洲古典电影殿堂 (9,790部)"},
            {"name": "Str8Hell.com", "relation": "streaming", "label": "流媒体数字化母网 (1,551部)"},
            {"name": "Czech Gay Casting", "relation": "sub_brand", "label": "捷克素人试镜专牌"},
        ]
    },
    {
        "id": "raw_fuck_club_network",
        "name": "Raw Fuck Club Network",
        "name_zh": "RFC 极限地下流媒体",
        "type": "network",
        "headquarters": "欧洲",
        "founded_year": 2012,
        "description_zh": "欧洲当今规模最大的暗黑地下实战网络之一，收录 Dark Alley Media 等多个暗巷地下制作厂牌作品。",
        "core_brands": [
            {"name": "Raw Fuck Club", "relation": "subsidiary", "label": "地下实战总汇母网"},
            {"name": "Dark Alley Media", "relation": "distributed", "label": "暗巷媒体长片制作商"},
        ]
    }
]

def main():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    # 1. 预先抓取所有厂牌在库中的元数据（作品数、分集数、中文名、简介、Logo、Banner）
    print("🔄 读取全库片商元数据与作品聚合统计...")
    cur.execute("""
        SELECT s.name, st.name_zh, st.description_zh, st.logo_url, st.banner_url,
               COALESCE(m.cnt, 0) AS works_count,
               COALESCE(e.cnt, 0) AS episodes_count
        FROM (
            SELECT DISTINCT studio_name AS name FROM movies WHERE studio_name IS NOT NULL AND trim(studio_name) != ''
            UNION
            SELECT DISTINCT studio_name AS name FROM episodes WHERE studio_name IS NOT NULL AND trim(studio_name) != ''
        ) s
        LEFT JOIN studios st ON st.name = s.name COLLATE NOCASE
        LEFT JOIN (
            SELECT studio_name, count(*) AS cnt FROM movies WHERE studio_name IS NOT NULL AND trim(studio_name) != '' GROUP BY studio_name
        ) m ON m.studio_name = s.name
        LEFT JOIN (
            SELECT studio, count(DISTINCT episode_id) AS cnt
            FROM (
                SELECT e.id AS episode_id, e.studio_name AS studio FROM episodes e WHERE e.studio_name IS NOT NULL AND trim(e.studio_name) != ''
                UNION ALL
                SELECT e.id AS episode_id, m.studio_name AS studio FROM episodes e JOIN movies m ON e.movie_id = m.id WHERE m.studio_name IS NOT NULL AND trim(m.studio_name) != ''
            )
            GROUP BY studio
        ) e ON e.studio = s.name
    """)
    studio_meta = {}
    for row in cur.fetchall():
        s_name, s_name_zh, s_desc, s_logo, s_banner, w_cnt, ep_cnt = row
        studio_meta[s_name] = {
            "name": s_name,
            "name_zh": s_name_zh,
            "description_zh": s_desc,
            "logo_url": s_logo,
            "banner_url": s_banner,
            "works_count": w_cnt,
            "episodes_count": ep_cnt
        }
    print(f"✅ 成功加载 {len(studio_meta)} 家厂牌的实时元数据与作品统计")

    # 2. 抓取跨表真实的母子厂牌分集收录关联（TOP 关联）
    print("🔄 提炼 350+ 组跨表真实母子厂牌收录网络...")
    cur.execute("""
        SELECT e.studio_name as network_name, m.studio_name as child_name, count(DISTINCT e.id) as weight
        FROM episodes e
        JOIN movies m ON e.movie_id = m.id
        WHERE e.studio_name IS NOT NULL AND trim(e.studio_name) != ''
          AND m.studio_name IS NOT NULL AND trim(m.studio_name) != ''
          AND e.studio_name != m.studio_name
        GROUP BY network_name, child_name
        HAVING weight >= 5
        ORDER BY weight DESC
    """)
    data_links = cur.fetchall()
    print(f"✅ 提取到活跃度 >= 5 部的母子合作边: {len(data_links)} 条")

    # 3. 构造 Groups
    groups = []
    registered_nodes = {}
    edges = []

    # 先处理已知核心集团
    for kg in KNOWN_GROUPS:
        gid = kg["id"]
        g_name = kg["name"]
        g_name_zh = kg["name_zh"]
        g_type = kg["type"]
        g_desc = kg["description_zh"]
        g_hq = kg.get("headquarters", "")
        g_year = kg.get("founded_year", None)

        group_studios = []
        tot_works = 0
        tot_eps = 0

        for brand in kg["core_brands"]:
            b_name = brand["name"]
            meta = studio_meta.get(b_name, {
                "name": b_name,
                "name_zh": None,
                "description_zh": None,
                "logo_url": None,
                "banner_url": None,
                "works_count": 0,
                "episodes_count": 0
            })
            tot_works += meta["works_count"]
            tot_eps += meta["episodes_count"]

            node_type = "network" if brand["relation"] in ("subsidiary", "streaming") and "Network" in g_name else "studio"
            if meta["works_count"] > 50 and meta["episodes_count"] > 100:
                node_type = "hybrid"

            node_obj = {
                "id": b_name,
                "name": b_name,
                "name_zh": meta["name_zh"],
                "type": node_type,
                "group_id": gid,
                "group_name": g_name,
                "group_name_zh": g_name_zh,
                "works_count": meta["works_count"],
                "episodes_count": meta["episodes_count"],
                "logo_url": meta["logo_url"],
                "banner_url": meta["banner_url"],
                "description_zh": meta["description_zh"],
                "badge": brand["label"],
                "relation": brand["relation"]
            }
            group_studios.append(node_obj)
            registered_nodes[b_name] = node_obj

            # 建立集团内部连线 (第一个品牌为中心，连接其余品牌)
            center_brand = kg["core_brands"][0]["name"]
            if b_name != center_brand:
                edges.append({
                    "source": center_brand,
                    "target": b_name,
                    "relation": brand["relation"],
                    "relation_label": brand["label"],
                    "weight": max(10, meta["works_count"])
                })

        groups.append({
            "id": gid,
            "name": g_name,
            "name_zh": g_name_zh,
            "type": g_type,
            "headquarters": g_hq,
            "founded_year": g_year,
            "description_zh": g_desc,
            "total_works": tot_works,
            "total_episodes": tot_eps,
            "studios": group_studios
        })

    # 4. 把数据库挖掘出的其余高频母子收录厂牌，动态聚合为流媒体合作联盟
    # 比如 Gaylife Network, Bare Adventures, BoyCrush 等
    dynamic_group_map = {}
    for net, child, weight in data_links:
        # 如果已经存在连线则跳过
        edge_exists = any(e["source"] == net and e["target"] == child for e in edges)
        if not edge_exists:
            # 判断 net 是否已属于某个已知 group
            net_group_id = registered_nodes[net]["group_id"] if net in registered_nodes else None
            net_group_name = registered_nodes[net]["group_name"] if net in registered_nodes else net
            net_group_zh = registered_nodes[net]["group_name_zh"] if net in registered_nodes else (studio_meta.get(net, {}).get("name_zh") or net)

            if not net_group_id:
                # 动态自建 Group
                if net not in dynamic_group_map:
                    dynamic_group_map[net] = {
                        "id": f"net_{net.lower().replace(' ', '_').replace('.', '_')}",
                        "name": f"{net} Alliance",
                        "name_zh": f"{studio_meta.get(net, {}).get('name_zh') or net} 聚合网络",
                        "type": "network",
                        "headquarters": "线上流媒体点播站",
                        "founded_year": None,
                        "description_zh": f"以 {net} 为核心点播分发平台的产业联盟，收录了多家高质量长片与分集制作厂牌。",
                        "total_works": studio_meta.get(net, {}).get("works_count", 0),
                        "total_episodes": studio_meta.get(net, {}).get("episodes_count", 0),
                        "studios": []
                    }
                    # 注册母网节点
                    m = studio_meta.get(net, {"name": net, "name_zh": None, "works_count": 0, "episodes_count": 0})
                    n_node = {
                        "id": net,
                        "name": net,
                        "name_zh": m.get("name_zh"),
                        "type": "network",
                        "group_id": dynamic_group_map[net]["id"],
                        "group_name": dynamic_group_map[net]["name"],
                        "group_name_zh": dynamic_group_map[net]["name_zh"],
                        "works_count": m.get("works_count", 0),
                        "episodes_count": m.get("episodes_count", 0),
                        "logo_url": m.get("logo_url"),
                        "banner_url": m.get("banner_url"),
                        "description_zh": m.get("description_zh"),
                        "badge": "线上点播母网",
                        "relation": "parent_network"
                    }
                    registered_nodes[net] = n_node
                    dynamic_group_map[net]["studios"].append(n_node)

                net_group_id = dynamic_group_map[net]["id"]
                net_group_name = dynamic_group_map[net]["name"]
                net_group_zh = dynamic_group_map[net]["name_zh"]

            # 注册子厂牌节点
            if child not in registered_nodes:
                cm = studio_meta.get(child, {"name": child, "name_zh": None, "works_count": 0, "episodes_count": 0})
                c_node = {
                    "id": child,
                    "name": child,
                    "name_zh": cm.get("name_zh"),
                    "type": "studio",
                    "group_id": net_group_id,
                    "group_name": net_group_name,
                    "group_name_zh": net_group_zh,
                    "works_count": cm.get("works_count", 0),
                    "episodes_count": cm.get("episodes_count", 0),
                    "logo_url": cm.get("logo_url"),
                    "banner_url": cm.get("banner_url"),
                    "description_zh": cm.get("description_zh"),
                    "badge": f"合作长片厂牌 ({weight}部)",
                    "relation": "streaming"
                }
                registered_nodes[child] = c_node
                # 如果这个 group 还在动态 group map 里，加进去
                if net in dynamic_group_map:
                    dynamic_group_map[net]["studios"].append(c_node)
                    dynamic_group_map[net]["total_works"] += c_node["works_count"]
                    dynamic_group_map[net]["total_episodes"] += c_node["episodes_count"]

            # 增加边
            edges.append({
                "source": net,
                "target": child,
                "relation": "streaming",
                "relation_label": f"点播收录 ({weight}部)",
                "weight": weight
            })

    # 把动态建立的集团按总作品量排序加入 groups
    dyn_sorted = sorted(dynamic_group_map.values(), key=lambda g: g["total_works"] + g["total_episodes"], reverse=True)
    # 取排名前 20 的活跃网络集团
    groups.extend(dyn_sorted[:20])

    # 节点列表
    nodes_list = list(registered_nodes.values())

    tot_episodes_linked = sum(e["weight"] for e in edges)

    result_data = {
        "stats": {
            "total_groups": len(groups),
            "total_studios": len(nodes_list),
            "total_links": len(edges),
            "total_episodes_linked": tot_episodes_linked
        },
        "groups": groups,
        "nodes": nodes_list,
        "edges": edges
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(result_data, f, ensure_ascii=False, indent=2)

    print(f"\n🎉 谱系与关系网络数据构建完成！已输出至: {OUTPUT_PATH}")
    print(f"📊 统计摘要: {len(groups)} 个集团/平台，{len(nodes_list)} 家核心厂牌节点，{len(edges)} 条网络连线，收录关联作品 {tot_episodes_linked} 部。")

if __name__ == "__main__":
    main()
