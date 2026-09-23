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
