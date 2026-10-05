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

# 行业公认的核心集团/母网基础家谱与权威知识库全量定义
KNOWN_GROUPS = [
    {
        "id": "aylo_mindgeek",
        "name": "Aylo / MindGeek Empire",
        "name_zh": "Aylo (原MindGeek) 全球男色帝国",
        "type": "conglomerate",
        "headquarters": "卢森堡 / 洛杉矶",
        "founded_year": 2004,
        "description_zh": "全球最大的商业成人娱乐巨无霸，旗下囊括 MEN.com、Sean Cody、Next Door Studios (包括Taboo/Hookups) 及 Reality Kings、Stag Collective 等多元顶级流水线品牌，统治全球商业流媒体半壁江山。",
        "core_brands": [
            {"name": "MEN.com", "relation": "subsidiary", "label": "集团核心流水线旗舰"},
            {"name": "Sean Cody", "relation": "subsidiary", "label": "直男素人/全球偶像摇篮 (3,155部)"},
            {"name": "Next Door Studios", "relation": "subsidiary", "label": "都会邻家剧情主力 (5,306部)"},
            {"name": "Next Door Taboo", "relation": "sub_brand", "label": "禁忌系列副线 (117部)"},
            {"name": "Stag Collective", "relation": "sub_brand", "label": "轻熟雅致剧情代工 (53部)"},
            {"name": "Reality Kings", "relation": "subsidiary", "label": "纪实概念经典线 (7部)"},
        ]
    },
    {
        "id": "falcon_studios_group",
        "name": "Falcon Studios Group",
        "name_zh": "猎鹰影视集团 (Falcon / Active Media)",
        "type": "conglomerate",
        "headquarters": "旧金山",
        "founded_year": 1971,
        "description_zh": "全球同性成人影视工业殿堂级奠基者与行业常青藤。半个世纪以来通过兼并与子品牌开拓，建立了囊括经典男色(Falcon)、狂野皮革(Raging Stallion)、全美名导大厂(Hot House)、野外硬汉(Mustang)、古典罗马(Centaur)以及竞技型男(Jocks系列)等多条产品线的宏大传媒王国。",
        "core_brands": [
            {"name": "Falcon Studios", "relation": "subsidiary", "label": "殿堂旗舰/阳光经典 (1,332部)"},
            {"name": "Falcon International", "relation": "sub_brand", "label": "欧陆外景产品线 (32部)"},
            {"name": "Raging Stallion Studios", "relation": "subsidiary", "label": "狂烈种马/野性皮革 (1,395部)"},
            {"name": "Hot House Entertainment", "relation": "subsidiary", "label": "温室热浪/全资收购名厂 (830部)"},
            {"name": "Mustang Studios", "relation": "subsidiary", "label": "狂野公路/西部硬汉系列 (115部)"},
            {"name": "Centaur Films", "relation": "sub_brand", "label": "古典雕塑体魄专线 (34部)"},
            {"name": "Cocksure Men", "relation": "sub_brand", "label": "傲然型男系列 (833部)"},
            {"name": "Jocks Video", "relation": "sub_brand", "label": "竞技健美专线 (126部)"},
            {"name": "UK Hot Jocks", "relation": "sub_brand", "label": "英伦热血体育生 (309部)"},
            {"name": "Bound Jocks", "relation": "sub_brand", "label": "束缚重口分支 (188部)"},
            {"name": "Jocks in Socks Video Productions", "relation": "sub_brand", "label": "足袜概念专线"},
            {"name": "Hairy Jocks Video", "relation": "sub_brand", "label": "毛发健将视界"},
            {"name": "Jocks Home Video", "relation": "sub_brand", "label": "体育生家庭录像"},
            {"name": "Jocks", "relation": "sub_brand", "label": "众生体育生影业"},
        ]
    },
    {
        "id": "titan_media_group",
        "name": "Titan Media Group",
        "name_zh": "泰坦传媒集团 (Titan Media)",
        "type": "conglomerate",
        "headquarters": "旧金山",
        "founded_year": 1995,
        "description_zh": "由名导 Bruce Cam 创立的雄性荷尔蒙重镇，以粗犷野性、硬汉胡茬及电影级打光质感确立了全美‘泰坦猛男’审美体系，旗下全资收编传奇实体大厂 MSR Videos。",
        "core_brands": [
            {"name": "Titan Media", "relation": "subsidiary", "label": "集团母网/硬汉标杆 (1,445部)"},
            {"name": "MSR Videos", "relation": "subsidiary", "label": "全资收编/传奇实体厂 (35部)"},
            {"name": "Titan Productions", "relation": "sub_brand", "label": "剧情长篇制片 (1部)"},
        ]
    },
    {
        "id": "belami_empire",
        "name": "BelAmi Online / Film",
        "name_zh": "漂亮朋友美学帝国 (BelAmi)",
        "type": "conglomerate",
        "headquarters": "布拉格 / 布拉迪斯拉发",
        "founded_year": 1993,
        "description_zh": "享誉全球的东欧殿堂级唯美青年男色帝国，开创了高画质、唯美浪漫与古典沙龙镜头美学，旗下建有直属全球新星选拔网 Freshmen.net 以及名作副牌 be.me.fi。",
        "core_brands": [
            {"name": "BelAmi", "relation": "subsidiary", "label": "东欧殿堂主厂 (5,451部)"},
            {"name": "Freshmen.net", "relation": "division", "label": "官方直属新星选拔网 (1,420部)"},
            {"name": "be.me.fi", "relation": "sub_brand", "label": "俊友影业/沙龙副牌 (203部)"},
            {"name": "Freshmen Features", "relation": "sub_brand", "label": "大一新生特辑 (6部)"},
            {"name": "Freshmen Productions", "relation": "sub_brand", "label": "新秀首作工坊 (1部)"},
        ]
    },
    {
        "id": "say_uncle_network",
        "name": "Say Uncle Network",
        "name_zh": "求饶网络 (Say Uncle Network)",
        "type": "network",
        "headquarters": "洛杉矶",
        "founded_year": 2017,
        "description_zh": "互联网时代声势最浩大的禁忌剧情与角色扮演网络，旗下统摄 Bareback Network、Family Dick、Young Perps、Missionary Boys 及 Bromo 等多个轰动性垂直流媒体分站。",
        "core_brands": [
            {"name": "Say Uncle", "relation": "subsidiary", "label": "集团母网/流媒体总汇 (1,461部)"},
            {"name": "Bareback Network", "relation": "division", "label": "核心实战无套长片线 (203部)"},
            {"name": "Missionary Boys", "relation": "sub_brand", "label": "传教士破戒副线 (896部)"},
            {"name": "Bromo", "relation": "streaming", "label": "兄弟风潮/联合出品 (656部)"},
            {"name": "Family Dick", "relation": "sub_brand", "label": "禁忌亲情剧情副线 (480部)"},
            {"name": "Young Perps", "relation": "sub_brand", "label": "警匪嫌疑犯概念站 (212部)"},
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
            {"name": "The Bro Network", "relation": "subsidiary", "label": "流媒体母网/总平台 (518部)"},
            {"name": "Broke Straight Boys", "relation": "division", "label": "破产直男实录 (2,291部)"},
            {"name": "The Guy Site", "relation": "streaming", "label": "线上内容联合站点 (833部)"},
            {"name": "Masqulin", "relation": "division", "label": "旗舰部门/制服与粗犷 (42部)"},
        ]
    },
    {
        "id": "staxus_eurocreme",
        "name": "Eurocreme / Staxus Group",
        "name_zh": "欧陆之光集团 (Staxus / Eurocreme)",
        "type": "conglomerate",
        "headquarters": "布拉格 / 伦敦",
        "founded_year": 1993,
        "description_zh": "欧洲男同性恋影业历史上最大的跨国娱乐航空母舰，横跨英国与捷克，兼并 Staxus、Bulldog XXX 及 AVI Production 等十余个欧洲本土经典品牌。",
        "core_brands": [
            {"name": "Staxus", "relation": "subsidiary", "label": "捷克东欧旗舰/流媒体站 (1,435部)"},
            {"name": "Staxus Films", "relation": "division", "label": "长片实体音像发行 (387部)"},
            {"name": "Eurocreme", "relation": "subsidiary", "label": "英伦母网/欧洲老字号 (482部)"},
            {"name": "Bulldog XXX", "relation": "subsidiary", "label": "重型犬系猛男专线 (446部)"},
            {"name": "AVI Production", "relation": "streaming", "label": "东欧写实长篇代工 (212部)"},
            {"name": "AVI Films", "relation": "sub_brand", "label": "东欧电影专线 (87部)"},
            {"name": "Eurocreme Prague", "relation": "sub_brand", "label": "布拉格制作中心 (10部)"},
        ]
    },
    {
        "id": "helix_studios_network",
        "name": "Helix Studios Network",
        "name_zh": "螺旋影视网络 (Helix Studios)",
        "type": "network",
        "headquarters": "圣地亚哥",
        "founded_year": 2010,
        "description_zh": "千禧一代青年偶像与唯美阳光美学的统治级厂牌，开创了顶流专属男优长期签约制度，拥有庞大的子厂牌 8teenBoy、联合工坊 Edward James 与明星专属宇宙。",
        "core_brands": [
            {"name": "Helix Studios", "relation": "subsidiary", "label": "集团旗舰/偶像青年 (4,073部)"},
            {"name": "8teenBoy", "relation": "division", "label": "初熟少年专属线 (114部)"},
            {"name": "Johnny Rapid", "relation": "sub_brand", "label": "一哥强尼拉皮德专属 (115部)"},
            {"name": "Edward James Productions", "relation": "sub_brand", "label": "深度联合制片工坊 (160部)"},
            {"name": "Sean Storm Productions", "relation": "sub_brand", "label": "名模制片分支 (1部)"},
        ]
    },
    {
        "id": "carnal_plus",
        "name": "Carnal+ Entertainment",
        "name_zh": "欲念加号媒体网 (Carnal+)",
        "type": "network",
        "headquarters": "蒙特利尔",
        "founded_year": 2016,
        "description_zh": "主打年轻美少年与多视角短剧的现代化线上流媒体聚合体，收编了 Fun-Size Boys、Twink Top、Boy for Sale、Masonic Boys 等十余个细分题材工作室。",
        "core_brands": [
            {"name": "Carnal+", "relation": "subsidiary", "label": "流媒体总网 (1,219部)"},
            {"name": "JockPack", "relation": "sub_brand", "label": "运动健将专牌 (1,172部)"},
            {"name": "Stag Homme Studios", "relation": "sub_brand", "label": "法式轻熟男影业 (229部)"},
            {"name": "Fun-Size Boys", "relation": "sub_brand", "label": "袖珍美少男副牌 (15部)"},
            {"name": "Gaycest", "relation": "sub_brand", "label": "禁忌亲属副牌 (14部)"},
            {"name": "Boy for Sale", "relation": "sub_brand", "label": "待售少年副牌 (12部)"},
            {"name": "Masonic Boys", "relation": "sub_brand", "label": "共济会兄弟副牌 (11部)"},
            {"name": "Scout Boys", "relation": "sub_brand", "label": "童子军概念副牌 (11部)"},
            {"name": "Twink Top", "relation": "sub_brand", "label": "纤细攻君副牌 (10部)"},
        ]
    },
    {
        "id": "kink_com_network",
        "name": "Kink.com / KinkMen",
        "name_zh": "旧金山军械库禁忌帝国 (Kink.com)",
        "type": "conglomerate",
        "headquarters": "旧金山旧军械库",
        "founded_year": 1997,
        "description_zh": "全球 BDSM 工业第一殿堂，以军械库地牢为根据地建立的极限束缚、硬核心理博弈、机器刑具及亚文化专精王国，男同志专牌 KinkMen 为其王冠明珠。",
        "core_brands": [
            {"name": "KinkMen", "relation": "subsidiary", "label": "军械库男同志旗舰 (2,394部)"},
            {"name": "Kink Video", "relation": "sub_brand", "label": "奇癖影视早期专线 (176部)"},
            {"name": "Kinky Angels", "relation": "sub_brand", "label": "禁忌天使系列 (227部)"},
            {"name": "Hardkinks", "relation": "sub_brand", "label": "硬核重口工坊 (28部)"},
            {"name": "Kinky Asian Boys", "relation": "sub_brand", "label": "亚裔玩味制片 (28部)"},
            {"name": "Kinky Twink Entertainment", "relation": "sub_brand", "label": "乖巧禁忌娱乐 (13部)"},
            {"name": "Kinky Twink", "relation": "sub_brand", "label": "乖巧禁忌系列 (6部)"},
        ]
    },
    {
        "id": "channel_1_releasing",
        "name": "Channel 1 Releasing (C1R)",
        "name_zh": "第一频道发行联合体 (C1R / Chi Chi LaRue)",
        "type": "conglomerate",
        "headquarters": "洛杉矶",
        "founded_year": 1998,
        "description_zh": "由全球最负盛名的跨界名导 Chi Chi LaRue 创立并发起的音像发行巨无霸，旗下除自身核心名牌 Rascal Video 外，更统筹收购了拥有逾千部黄金长片的传奇片库 All Worlds Video 以及 Massive Studio、Chi Chi La Raw 等多条专线。",
        "core_brands": [
            {"name": "All Worlds Video", "relation": "subsidiary", "label": "万象视界/千部长片片库 (1,061部)"},
            {"name": "Rascal Video", "relation": "subsidiary", "label": "痞子影像/名导核心品牌 (126部)"},
            {"name": "Channel 1 Releasing", "relation": "subsidiary", "label": "母网发行机构 (107部)"},
            {"name": "Massive Studio", "relation": "sub_brand", "label": "巨构猛男专线 (36部)"},
            {"name": "Chi Chi La Raw", "relation": "sub_brand", "label": "导演无套概念专牌 (7部)"},
        ]
    },
    {
        "id": "gamma_entertainment",
        "name": "Gamma Entertainment (Adult Time)",
        "name_zh": "伽马娱乐集团 (Pride Studios / Gamma)",
        "type": "conglomerate",
        "headquarters": "蒙特利尔",
        "founded_year": 2005,
        "description_zh": "全球最具实力的跨国成人流媒体巨头之一，其男同性恋板块以 Pride Studios 为核心旗舰，联合 High Performance Men、Circle Jerk Boys 与 Adult Time 等品牌共同构建高端剧情矩阵。",
        "core_brands": [
            {"name": "Pride Studios", "relation": "subsidiary", "label": "集团男同旗舰/高端艺术 (3,316部)"},
            {"name": "Adult Time", "relation": "subsidiary", "label": "集团流媒体大平台 (44部)"},
            {"name": "Circle Jerk Boys", "relation": "sub_brand", "label": "兄弟圈围探险线 (21部)"},
            {"name": "High Performance Men", "relation": "sub_brand", "label": "高性能型男系列 (14部)"},
            {"name": "Extase G", "relation": "sub_brand", "label": "法式狂喜长片线 (7部)"},
        ]
    },
    {
        "id": "prowler_millivres_group",
        "name": "Millivres Prowler / Blake Mason Group",
        "name_zh": "英伦秘境与普罗勒传媒 (Prowler / Blake Mason)",
        "type": "conglomerate",
        "headquarters": "伦敦 / 阿姆斯特丹",
        "founded_year": 1990,
        "description_zh": "英国最大的同性恋出版与影视传媒集团，联合荷兰 Orrange Media Group 共同发行欧洲最受欢迎的英伦痞子与都会男模系列。",
        "core_brands": [
            {"name": "Blake Mason", "relation": "subsidiary", "label": "英伦痞子与都会男模旗舰 (1,799部)"},
            {"name": "Phoenixxx.com", "relation": "subsidiary", "label": "欧陆都会概念站 (365部)"},
            {"name": "Millivres Prowler Ltd.", "relation": "subsidiary", "label": "英国老牌传媒发行母厂 (247部)"},
            {"name": "Euroboy", "relation": "division", "label": "欧陆少年先锋线 (247部)"},
            {"name": "Orrange Media Group", "relation": "subsidiary", "label": "荷兰联合媒体集团 (149部)"},
        ]
    },
    {
        "id": "treasure_island_media_group",
        "name": "Treasure Island Media Group",
        "name_zh": "金银岛地下影视王国 (Treasure Island Media)",
        "type": "conglomerate",
        "headquarters": "旧金山",
        "founded_year": 1999,
        "description_zh": "由 Paul Morris 创立于旧金山的地下硬核邪典帝国，以生猛、极限纪实与突破常规的工业风格影响了整个千禧年代男性实录美学。",
        "core_brands": [
            {"name": "Treasure Island Media", "relation": "subsidiary", "label": "地下实战帝国母厂 (2,941部)"},
            {"name": "ioMacho", "relation": "subsidiary", "label": "粗粝猛汉实战系列 (429部)"},
            {"name": "Toxxxic Films", "relation": "sub_brand", "label": "极限毒药邪典线 (3部)"},
            {"name": "Grindhouse Raw", "relation": "sub_brand", "label": "磨坊暗室概念线 (1部)"},
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
            {"name": "Colt Studio", "relation": "subsidiary", "label": "半世纪传奇母厂 (774部)"},
            {"name": "Buckshot Productions", "relation": "division", "label": "粗犷野性熊族副牌 (67部)"},
        ]
    },
    {
        "id": "str8hell_william_higgins",
        "name": "Higgins / Str8Hell Classic",
        "name_zh": "威廉·希金斯欧陆传奇古典网",
        "type": "conglomerate",
        "headquarters": "巴黎 / 布拉格",
        "founded_year": 1978,
        "description_zh": "跨越半世纪的欧洲古典独立电影制片传奇 William Higgins Productions 及其数字化线上流媒体总站 Str8Hell.com，收录了逾万部珍贵历史长片与场景。",
        "core_brands": [
            {"name": "William Higgins Productions", "relation": "subsidiary", "label": "欧洲古典电影殿堂 (10,817部)"},
            {"name": "Str8Hell.com", "relation": "streaming", "label": "流媒体数字化母网 (1,551部)"},
            {"name": "Czech Gay Casting", "relation": "sub_brand", "label": "捷克素人试镜专牌 (1部)"},
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
            {"name": "GayRoom", "relation": "subsidiary", "label": "线上点播母网平台 (1,693部)"},
            {"name": "Driveshaft", "relation": "distributed", "label": "专属长片制作商 (142部)"},
            {"name": "Man Royale", "relation": "distributed", "label": "皇家硬汉长片线 (109部)"},
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
            {"name": "Kristen Bjorn Video", "relation": "subsidiary", "label": "宗师主厂牌 (1,389部)"},
            {"name": "Sarava Productions", "relation": "division", "label": "南美异域狂想 (67部)"},
        ]
    },
    {
        "id": "lucas_entertainment",
        "name": "Lucas Entertainment",
        "name_zh": "卢卡斯奢华影业 (Lucas Entertainment)",
        "type": "conglomerate",
        "headquarters": "纽约 / 巴黎",
        "founded_year": 2004,
        "description_zh": "由名模明星 Michael Lucas 创立的高奢电影工业标杆，主打好莱坞级华丽服化道与跨国叙事，联合传奇意式名导品牌 Lucas Kazan Productions 共同发行。",
        "core_brands": [
            {"name": "Lucas Entertainment", "relation": "subsidiary", "label": "奢华商业大片旗舰 (1,922部)"},
            {"name": "Lucas Kazan Productions", "relation": "distributed", "label": "意式地中海唯美专牌 (48部)"},
        ]
    },
    {
        "id": "raw_fuck_club_network",
        "name": "Raw Fuck Club Network",
        "name_zh": "RFC 极限地下流媒体",
        "type": "network",
        "headquarters": "欧洲",
        "founded_year": 2012,
        "description_zh": "欧洲当今规模最大的暗黑地下实战网络之一，收录 Dark Alley Media 等多个暗巷地下制作厂牌作品，单站作品破万部。",
        "core_brands": [
            {"name": "Raw Fuck Club", "relation": "subsidiary", "label": "地下实战总汇母网 (13,962部)"},
            {"name": "Dark Alley Media", "relation": "distributed", "label": "暗巷媒体长片制作商 (210部)"},
        ]
    },
    {
        "id": "catalina_pacific",
        "name": "Catalina / Pacific Sun Classics",
        "name_zh": "加州阳光与卡特琳娜经典黄金阵营",
        "type": "conglomerate",
        "headquarters": "洛杉矶",
        "founded_year": 1979,
        "description_zh": "美国西海岸黄金时代最重要的两大古典男色大厂，主打天然阳光加州泳池、海滩外景与大叙事年代电影。",
        "core_brands": [
            {"name": "Catalina Video", "relation": "subsidiary", "label": "加州黄金时代传奇 (599部)"},
            {"name": "Pacific Sun Entertainment", "relation": "subsidiary", "label": "太平洋阳光经典线 (161部)"},
        ]
    },
    {
        "id": "golden_age_classics",
        "name": "Golden Age Legends (Bacchus & Jet Set)",
        "name_zh": "黄金时代典藏联合体 (Bacchus / Jet Set / Bijou)",
        "type": "conglomerate",
        "headquarters": "旧金山 / 纽约",
        "founded_year": 1975,
        "description_zh": "收录上世纪70至80年代成人电影胶片黄金期的殿堂级老牌发行机构，拥有数千部珍贵胶片翻录长片。",
        "core_brands": [
            {"name": "Bacchus Releasing", "relation": "subsidiary", "label": "酒神狂欢影业/千部胶片典藏 (1,365部)"},
            {"name": "Jet Set Productions", "relation": "subsidiary", "label": "喷气机黄金长篇 (227部)"},
            {"name": "Bijou Video", "relation": "subsidiary", "label": "比茹经典影院 (135部)"},
        ]
    },
    {
        "id": "gaylife_youth_network",
        "name": "Gaylife Youth Network",
        "name_zh": "同志人生与街头青年网络 (Gaylife / BoyCrush)",
        "type": "network",
        "headquarters": "洛杉矶",
        "founded_year": 2005,
        "description_zh": "聚焦街头滑板滑手、嘻哈低腰裤（Saggerz）与现代年轻一代的多元线上影视网络。",
        "core_brands": [
            {"name": "BoyCrush", "relation": "subsidiary", "label": "校园与青年偶像旗舰 (1,448部)"},
            {"name": "Gaylife Network", "relation": "subsidiary", "label": "线上综合点播母网 (1,032部)"},
            {"name": "Saggerz Skaterz", "relation": "sub_brand", "label": "街头滑板低腰潮人线 (145部)"},
            {"name": "Xtreme Productions", "relation": "sub_brand", "label": "极限街头实录线 (78部)"},
        ]
    },
    {
        "id": "flava_works_group",
        "name": "Flava Works Group",
        "name_zh": "风味工坊集团 (Flava Works)",
        "type": "conglomerate",
        "headquarters": "迈阿密",
        "founded_year": 2001,
        "description_zh": "北美最著名的多元族裔成人影视集团，以都市嘻哈风格与素人实战闻名。",
        "core_brands": [
            {"name": "Flava Works Inc.", "relation": "subsidiary", "label": "多元族裔都市影视母厂 (260部)"},
            {"name": "Mix It Up Boy", "relation": "division", "label": "混搭男孩点播分站 (195部)"},
        ]
    },
    {
        "id": "amateur_straight_guys",
        "name": "Amateur Straight Guys (ASG)",
        "name_zh": "素人直男纪实网络 (ASG Network)",
        "type": "network",
        "headquarters": "拉斯维加斯",
        "founded_year": 2008,
        "description_zh": "风靡全美的硬核直男探访纪实网络，以真实金钱诱惑与温柔心理攻势闻名，开辟了高画质进阶专属品牌 ASGmax。",
        "core_brands": [
            {"name": "ASGmax", "relation": "subsidiary", "label": "豪华进阶专属线 (139部)"},
            {"name": "Amateur Straight Guys", "relation": "subsidiary", "label": "纪实探访旗舰母网 (92部)"},
        ]
    },
    {
        "id": "cobra_studios_group",
        "name": "Cobra Studios Group",
        "name_zh": "眼镜蛇阳刚制片集团",
        "type": "conglomerate",
        "headquarters": "旧金山",
        "founded_year": 1980,
        "description_zh": "80年代与90年代阳刚野性男色的中流砥柱，专注于极致体量巨根与粗犷男性对抗。",
        "core_brands": [
            {"name": "Cobra Video", "relation": "subsidiary", "label": "核心录像带发行线 (26部)"},
            {"name": "Cobra Studios", "relation": "subsidiary", "label": "母公司制片中心 (7部)"},
        ]
    },
    {
        "id": "caballero_vca",
        "name": "Caballero / VCA Vintage",
        "name_zh": "卡巴列罗与VCA复古黄金时代",
        "type": "conglomerate",
        "headquarters": "洛杉矶",
        "founded_year": 1974,
        "description_zh": "横跨好莱坞成人录像带早期的双雄集团，其白金系列与胶片大戏开创了成人商业录像发行的黄金年代。",
        "core_brands": [
            {"name": "Caballero Video", "relation": "subsidiary", "label": "原版胶片制片 (11部)"},
            {"name": "VCA Pictures", "relation": "subsidiary", "label": "VCA传奇母厂 (12部)"},
            {"name": "Caballero Home Video", "relation": "sub_brand", "label": "家庭录像发行旗舰 (9部)"},
            {"name": "VCA Platinum", "relation": "sub_brand", "label": "白金高奢专线 (6部)"},
        ]
    },
    {
        "id": "independent_giants",
        "name": "Top Independent Studios",
        "name_zh": "全球顶流独立电影化名厂",
        "type": "indie_ecosystem",
        "headquarters": "全球",
        "founded_year": 2005,
        "description_zh": "不依赖传统资本兼并、凭借高水准独立制片、独家男模签约与艺术叙事独步天下的全球顶流巨星厂牌。",
        "core_brands": [
            {"name": "Corbin Fisher", "relation": "subsidiary", "label": "独立美少年流水线天花板 (4,695部)"},
            {"name": "ChaosMen", "relation": "subsidiary", "label": "硬朗荷尔蒙传奇名厂 (3,160部)"},
            {"name": "BoyFun", "relation": "subsidiary", "label": "唯美少年乐园概念站 (2,613部)"},
            {"name": "Active Duty Productions", "relation": "subsidiary", "label": "现役大兵与军警制服标杆 (2,228部)"},
            {"name": "Cocky Boys", "relation": "subsidiary", "label": "独立高奢先锋电影化标杆 (2,078部)"},
            {"name": "Randy Blue", "relation": "subsidiary", "label": "千禧一代运动型男先锋 (1,868部)"},
            {"name": "Crunchboy", "relation": "subsidiary", "label": "热血少年实战大厂 (1,664部)"},
            {"name": "BoyNapped", "relation": "subsidiary", "label": "绑架幻想与剧情探险线 (1,240部)"},
            {"name": "Men at Play", "relation": "subsidiary", "label": "欧洲商务西装名仕天花板 (1,224部)"},
            {"name": "Czech Hunter", "relation": "subsidiary", "label": "捷克街头街拍实录鼻祖 (910部)"},
            {"name": "French Art", "relation": "subsidiary", "label": "法兰西唯美独立大师 (90部)"},
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

    # 2. 抓取跨表真实的母子厂牌分集收录关联
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

    # 先处理已知核心集团（按配置完整入库）
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

        # 如果没有核心厂牌列表，跳过
        if not kg.get("core_brands"):
            continue

        center_brand = kg["core_brands"][0]["name"]

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
