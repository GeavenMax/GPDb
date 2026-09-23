# 更新日志 (Changelog)

本项目严格遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范与语义化版本号管理。
本文件记录了每次迭代的更新详情，便于直接同步至 GitHub Releases 与提交历史。

---

## [v2.0.0] - 2026-09-23

### 🚀 新增 (Added)
- **GPDb Android 移动客户端立项与工程隔离**：
  - 新建独立子工程根目录 `android_client/`，实现与桌面 macOS 端（`desktop_client/`）的物理级数据与构建隔离。
  - 完成 Android 开发架构设计规范 [`android_client/CODING_WIKI.md`](./android_client/CODING_WIKI.md)（含 Scoped Storage 分区存储、SAF 权限、Coil 离线图片降采样等规范）。
  - 交付完备的编译计划项目书 [`android_client/COMPILATION_PLAN.md`](./android_client/COMPILATION_PLAN.md)（涵盖 JDK 17/21、NDK r26b、Rust 交叉编译 Target 与 Gradle 指令集）。
- **资源检索与外部扩展插件系统 (v2.0)**：
  - 将原“BT磁力搜索”与“外部跳转”合体升级为“资源搜索与外部扩展”综合插件。
  - 增加 BoyfriendTV 演员资料与影片作品原名跳转，提取主标题过滤副标题干扰，精准命中条目。
  - 支持 Google 智能搜索与各大 BT 引擎独立细粒度启闭。
- **智能系列影片集与封面拼图组件**：
  - 新增 `SeriesCollageCover.vue`，支持自适应 1~4 张海报拼接矩阵（单图大图、双图对称、三图阶梯、四分格田字格），并带胶卷暗角光晕。
  - 新增 `SeriesModal.vue`，支持从主页系列卡片或收藏列表直接进入系列专题全览。
- **主页全新探索货架**：
  - 新增“经典系列大放送”与“随心探索 · 盲盒发现”摇骰换一批功能。
- **PlayStation 风格成就系统**：
  - 引入 PSN 风格流体毛玻璃奖杯解锁 Toast 提示与成就详情页。
- **开源门面与法律声明**：
  - 编写全新中文 `README.md` 与标准 MIT `LICENSE` 文件。
- **全语种多语言文档矩阵 (Multi-language Documentation)**：
  - 为客户端支持的全部 7 种语言独立编写并发布原生文档：简体中文 (`README.md`)、English (`README_EN.md`)、繁體中文 (`README_ZH_TW.md`)、日本語 (`README_JA.md`)、Deutsch (`README_DE.md`)、Español (`README_ES.md`)、Italiano (`README_IT.md`)。
- **一键创建全新空白数据库与冷启动引导 (Zero-Friction DB Initializer)**：
  - 在 `gpdb-core/src/migrate.rs` 提供原生零依赖的独立建表与迁移脚本，无缝在 `~/Documents/GPDb/gevi.db` 建立包含 14 张核心表结构、全文检索索引与视图的标准库。
  - 在权限引导模态窗 `PermissionExplainModal.vue` 与“缓存与设置”页顶置高奢渐变“✨ 一键创建全新空白影库（首次使用推荐）”按钮，支持创建中加载态与防重保护，创建后自动引导进入数据同步中心。
- **未缓存图片边看边自动离线下载与本地持久化 (On-Demand Image Caching Engine)**：
  - 在 Tauri 宿主层启用非阻塞异步协议 `register_asynchronous_uri_scheme_protocol("gpdb-img")`，实现多图并发多线程请求。
  - 在 `commands/cache.rs` 实现原子写入与智能路由机制：命中本地缓存时亚毫秒极速直读；未命中缓存时由后台通过伪装 User-Agent 与防盗链 Referer 头按需自动下载，落盘校验后原子重命名至 `image_cache/`，实现“边看边下载，下次全离线”。
- **后台异步自动化刮削与数据流式同步中心 (Asynchronous Scraping Engine & Live Center)**：
  - 在 `commands/sync.rs` 打造全异步生命周期引擎：支持增量极速同步、最新精选快速建库（1,000 部）、全量电影建库与演员档案补齐 4 大模式。
  - 通过 `PYTHONUNBUFFERED=1` 与即时流解析器，将子进程标准输出通过 Tauri Event (`scraper-progress`, `scraper-finished`, `scraper-stopped`) 实时广播到前端。
  - 支持优雅停机控制（SIGINT 安全存盘，零数据库损坏风险）。
  - 全新升级 `SyncModal.vue`：引入毛玻璃流体仪表盘、渐变进度条、动态速度与耗时计算、终端实时滚屏日志与“后台静默运行”快捷切换。
  - 顶栏导航 `Navbar.vue` 增加活动呼吸状态胶囊（`⚡ 同步中 +N`），点击可随时唤回控制台；前端通过 `onScraperDataChange` 驱动片库和主页在抓取过程中实时热更新。


### ⚡ 优化 (Changed)
- **男同成人影视专业管理定位确立**：
  - 全面优化项目展示与功能说明，突出身体特征属性（体型、发色、胡须体毛、身高体重、生理特征等）细粒度检索优势。
- **启动存储权限前置向导与消除被动扫描**：
  - 拔除启动时后台被动遍历用户 Documents/Downloads/Desktop 目录导致的连环权限拦截。
  - 新增 `PermissionExplainModal.vue`，在未定位到数据库时优雅解释离线存储原由，提供“手动单文件选取（零多余权限）”与“授权并自动扫描”两种自主选择。
- **“今日星光 · 标志面孔”演员头像严谨性保障**：
  - 后端候选池扩大至 100 位并严格校验本地磁盘物理图片存在性（`resolve_cache_file`），确保展示演员 100% 拥有精美头像，彻底消除字母占位图与碎图。
- **“一键生成影迷偏好画像”异步非阻塞与全流程进度弹窗**：
  - Rust 后端将 `run_ai_analysis` 升级为异步通道（`spawn_blocking`），彻底释放 UI 主循环，杜绝等待长文网络请求时的界面假死误解。
  - 新增 `AiAnalysisModal.vue` 磨砂玻璃进度悬浮面板，分步展示大模型握手、特征提取与画像生成进度，并带秒级计时器与安抚提示。
- **国际化多语言全量补全**：
  - 补齐所有新增组件与菜单项在 7 种语言（简体中文、繁体中文、English、Italiano、日本語、Español、Deutsch）中的对照翻译。

### 🐛 修复 (Fixed)
- **分集标题搜索覆盖**：
  - Rust 端 `queries/episodes.rs` 与 Python `db_manager.py` 全面加入 `e.title LIKE ? ESCAPE '\\'`，支持十万级分集真实标题全文检索。
- **数据库独立迁移 DDL**：
  - 在 `gpdb-core/src/migrate.rs` 中补齐 `directors` 与 `movie_directors` 建表语句与索引，脱离对 Python 端单向依赖。
- **导演点击交互行为统一**：
  - 详情页中点击导演统一直接呼出导演专属档案弹窗，且卡片内支持一键跳转片库筛选。

---

## [v1.0.0] - 2026-09-20

### 🚀 初始版本
- 建立 GEVI 离线数据库基础框架与 SQLite WAL 模式支持。
- 完成 60,000+ 电影与 100,000+ 分集基础数据存储。
- 基于 Tauri + Rust + Vue 3 的跨平台桌面客户端首次上线。

## [2026-09-23] v2.4 — BFTV 演员直达 + BT 搜索主标题净化

### 新增
- **`scrape_bftv_performers.py`** — 批量爬取演员在 BoyfriendTV 上的个人主页 URL，写入 performers.bftv_url；支持 --db / --limit / --delay / --concurrency / --overwrite；断点续爬

### 优化
- **BFTV 演员直达**：若 performers.bftv_url 已填充，点击按钮直接打开个人主页；按钮文字动态切换（"打开BFTV主页" / "在BFTV搜索演员资料"）
- **BT 搜索主标题净化**：影片/分集 BT 搜索只提交主标题，剥离副标题与序号（Vol/Part/Episode/I-IV 等），保留自然大小写
- extractMovieCleanTitle 补充 em-dash / en-dash 分隔符，不再强制小写

### 数据库迁移
- performers 表新增 bftv_url TEXT 列（客户端下次启动自动迁移，无需手动操作）

### 变更文件
- db_manager.py / migrate.rs / models.rs / sql.rs / types.ts / pluginManager.ts
- MovieDetailModal.vue / EpisodeDetailModal.vue / EpisodeRow.vue / PerformerDetailModal.vue
- scrape_bftv_performers.py（新建）
