# GEVI 影视离线数据库与批量抓取系统 (独立模块)

> **注意**：本文件夹为 GEVI 影视数据库刮削、离线存储与同步的**独立专属模块**，与本项目（游戏库管理App）的其他模块和对话完全隔离。请勿将无关代码存入此目录。

---

## 目录结构

```
GEVI_Offline_Database/
├── README.md             # 本说明文档
├── schema.sql            # SQLite 数据库表范式与 FTS5 全文索引定义
├── db_manager.py         # 数据库连接、原子事务与断点进度管理
├── batch_scraper.py      # 多线程批量刮削引擎 (支持断点续传、流量控制、Ctrl+C优雅保存)
├── sync_gevi.py          # 增量更新与自动整合工具 (监控 /newm /newp 与高水位探测)
├── gevi_cli.py           # macOS 终端毫秒级离线全文检索工具
└── gevi.db               # 本地 SQLite 离线数据库 (含 FTS5 全文检索引擎)
```

---

## 常用命令指南

进入当前独立目录：
```bash
cd "/Users/joel/Documents/antigravity/游戏库管理App/GEVI_Offline_Database"
```

### 1. 离线检索 (毫秒级响应)
```bash
# 查看数据库统计概览
python3 gevi_cli.py --stats

# 搜索电影名或片商 (如搜索 "Dirty" 或 "Frat")
python3 gevi_cli.py "Dirty"

# 查看指定电影的完整详情 (含演职员、时长、高清封面、剧情、分集)
python3 gevi_cli.py -m 75183

# 搜索演员姓名
python3 gevi_cli.py -p "Clay"

# 查看演员完整身体档案与参演影视列表
python3 gevi_cli.py --performer-id 146520
```

### 2. 增量同步 (拉取官网最新收录并自动整合)
```bash
python3 sync_gevi.py
```

### 3. 批量抓取全站数据 (支持随时按 Ctrl+C 中断，再次运行自动断点续传)
```bash
# 抓取最新 1,000 部电影 (倒序)
python3 batch_scraper.py --mode movies --start 1 --end 76000 --limit 1000 --reverse --concurrency 8

# 全量抓取电影
python3 batch_scraper.py --mode movies --start 1 --end 76000 --concurrency 8

# 全量抓取演员档案
python3 batch_scraper.py --mode performers --start 1 --end 150000 --concurrency 8
```
