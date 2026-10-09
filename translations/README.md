# 🌐 GPDb 机器翻译与自动化汉化工程模块 (Translations Engine)

本目录收录 GPDb 离线影视库的核心多语言机器翻译引擎、批处理流水线、Google Gemini 多 Key 矩阵调度器与后台自动守护任务。

---

## 📂 目录结构与核心职责

```
translations/
├── auto_translate_daemon.py       # 🚀 开机/唤醒网络探测、API可用性预检、自动化启停与 Discord 报告守护进程
├── run_studios_pipeline.py        # 🎬 多厂牌多阶段全自动串联流水线 (剧情简介 -> 片名意译 -> 深度后处理)
├── translate.py                   # ⚡ 核心批量翻译引擎 (Gemini 4-Key轮换、15 RPM限频、安全风控规避、断点保存)
├── translation_manager.py         # 🗄️ 独立多语言归档库管理器 (translations.db 读写与双向同步)
├── translate_config.json          # ⚙️ 配置文件 (Gemini 4 Keys、DeepSeek API、Discord Webhook URL)
├── batch_translate_studios.py     # 🏢 厂牌批量翻译与深度文史建档工具
├── translate_all_2000_studios.py  # 📜 全库 2400+ 厂牌名及档案快速覆盖脚本
├── clean_title_brackets.py        # 🧹 片名格式规范清洗工具 (强制去除片名实体中的《书名号》)
└── README.md                      # 📖 本模块说明文档
```

---

## ⚡ 核心能力与关键特性

1. **4-Key 账号矩阵与 2,000 RPD 免费高吞吐**：
   - 支持多 Google Gemini API Key 平滑轮换（Free Tier 500 次/天/Key，4 Key 合计 2,000 次/天）；
   - 单请求后严格注入 4.2 秒安全避让，彻底杜绝 15 RPM 频率限流；
2. **全自动开机自启与休眠闭环 (`auto_translate_daemon.py`)**：
   - 依赖 macOS `launchd`（`~/Library/LaunchAgents/com.gpdb.autotranslate.plist`）；
   - 开机并连通互联网后自动探测 4 个 Key 的健康状态；
   - 额度耗尽自动安全停止，向 Discord Webhook 推送富文本成果卡片，并自动追加写入 `TRANSLATION_PROGRESS.md`；
3. **独立多语言归档库 (`translations.db`)**：
   - 翻译成果双写提交至主库 `GPDb.db` 与跨平台独立的 `translations.db`，支持随身迁移与跨端多语言同步。

---

## 🚀 常用运行指令

```bash
# 1. 查看当前全库汉化大盘统计
python3 translations/translate.py --stats

# 2. 手动启动多厂牌全自动流水线
python3 translations/run_studios_pipeline.py

# 3. 手动触发一次守护进程 (探测 -> 翻译 -> 配额耗尽停机 -> 发送 Discord 报告)
python3 translations/auto_translate_daemon.py
```
