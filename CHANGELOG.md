# 更新日志 (Changelog)

本项目严格遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范与语义化版本号管理。
本项目记录了每次迭代的更新详情，便于直接同步至 GitHub Releases 与提交历史。

## [Unreleased]

### ✨ 新功能 (Features)
- **收藏功能全面升级 (Favorites Expansion)**：
  - 在影片详情页 (`MovieDetailScreen`) 与演员详情页 (`PerformerDetailScreen`) 的顶部导航栏恢复了“收藏 (Favorite)”按钮，支持一键加入或取消收藏。
  - 在系列专题列表页 (`FilteredMovieListScreen` - `series` 模式) 新增了系列收藏功能，支持将喜欢的系列一键加入收藏。
  - 在“我的库 (`LibraryScreen`)”中新增了“收藏系列” Tab 页，实时查询并展示所有已收藏的系列，支持点击直接跳转至该系列详情页。


## [v2.5.3] - 2026-09-24

### ⚡ 优化 (Changed)
- **应用图标方案默认顺序调整**：
  - 将「双雄火星图腾」(The Twin Mars Monolith) 由原方案 B 调整为**方案 A（默认）**，成为新用户首次启动时展示的 Dock 图标。
  - 将「黑曜石棱镜胶片之匣」(The Obsidian Film Vault) 由原方案 A 调整为方案 B。
  - 同步物理交换 `scheme-a.svg`、`scheme-a.png` 与 `scheme-b.svg`、`scheme-b.png`，确保 Dock 图标、Favicon、图标选择器预览三处展示完全一致。
  - `appIcon.ts` 元数据（名称、描述、标签、评级、`badge`）随文件同步更新。

## [v2.7.0] - 2026-09-23

### ✨ 新功能与平台扩展 (Features & Multi-Platform)
- **Windows 桌面客户端全新支持**：
  - 构建架构全面适配 Windows 平台，支持一键安装 `.exe` (NSIS) 与 `.msi` 安装包。
- **云端双端并行 CI/CD 自动化打包流水线**：
  - 升级 GitHub Actions 工作流 `.github/workflows/release.yml` 为矩阵构建（Matrix Build），实现 **macOS (Universal - Apple Silicon & Intel)** 与 **Windows (x64)** 双平台自动化编译、打包与 Release 直传挂载。
- **Telegram 频道显要嵌入**：
  - 全语种 7 份 README 顶部全面嵌入大尺寸醒目 Badge 与官方 Telegram 频道（https://t.me/gpdbnews）链接及高亮公告框。

## [v2.6.2] - 2026-09-23

### 🐛 修复 (Fixed)
- **「增量极速同步」启动失败：真正根因定位与完整修复（两阶段）**：
  - **第一阶段（误判）**：最初误判为 Tauri build 目录下资源镜像残留旧文件名 `sync_gevi.py` 所致，向 `target/release/bundle/` 及 `target/debug/` 内的 `_up_/_up_/` 目录补入 `sync_gpdb.py` 并删除旧文件，客户端重启后错误依旧。
  - **第二阶段（真正根因）**：通过 `strings` 命令检查 `/Applications/GPDb.app/Contents/MacOS/gpdb` 二进制文件，发现二进制内部仍硬编码 `sync_gevi.py`。这说明用户实际运行的 `/Applications/GPDb.app` 是**源码改名之前编译的旧版二进制**，与 `target/release/bundle/` 里的 bundle 完全独立，替换资源文件对其无效。
  - **最终修复**：
    1. 执行 `npm run tauri build -- --no-bundle` 重新编译 Rust 二进制（23 秒完成）；
    2. 将新二进制 `target/release/gpdb` 覆盖安装至 `/Applications/GPDb.app/Contents/MacOS/gpdb`；
    3. 同步更新 `/Applications/GPDb.app/Contents/Resources/_up_/_up_/` 下全部 Python 脚本，删除残留的 `sync_gevi.py`，写入 `sync_gpdb.py`、`sync_bftv_catalog.py` 等最新版本；
    4. `strings` 验证确认新二进制内只含 `sync_gpdb.py`，`sync_gevi.py` 彻底消失。

### ⚡ 优化 (Changed)
- **`tauri.conf.json` 资源清单补全**：
  - 发现 `bundle.resources` 列表中遗漏了 `sync_bftv_catalog.py`，导致正式 `tauri build` 打包时「BFTV Catalog 同步」模式所需脚本不会被纳入应用包。已补充该条目，确保下次构建所有脚本均正确随包分发：
    ```json
    "../../sync_gpdb.py",
    "../../sync_bftv_catalog.py",   // 新增
    "../../batch_scraper.py",
    "../../db_manager.py",
    "../../scrape_bftv_performers.py",
    "../../cache_images.py"
    ```

## [v2.6.1] - 2026-09-23

### 🐛 修复 (Fixed)
- **奖杯自动复活与无法清空故障**：
  - 修复 `trophySystem.ts` 中 `bronze_plugin_toggle`、`bronze_db_scanned`、`bronze_lang_switch`、`bronze_grid_adjust` 触发条件硬编码为 `condition: () => true` 以及 `bronze_plugin_bt` 默认状态为 true 的设计缺陷。修复前每次专注计时刷新（每15秒）或产生交互都会自动重新点亮这些奖杯，导致清空操作失效。
  - 在 `UserAnalytics` 中引入了真实的行为指标统计（`pluginsVisitedCount`、`settingsVisitedCount`、`langSwitchedCount`、`gridAdjustedCount`、`btUsedCount`），所有奖杯成就仅在满足实际操作阈值后才可解锁，数据为 0 时严格锁定。
- **重置弹窗选项缺失与连环解锁死循环**：
  - 重写 `TrophyResetModal.vue`，彻底解决先前“方式 1”默认触发连续解锁队列导致奖杯无法保持清空的问题；新增“仅清空已获成就奖杯（重置奖杯数据）”专属选项，并提供“彻底抹除全部数据”与“重新检定连环解锁”三种清晰独立的操作模式。
- **跨视图奖杯统计状态脱节**：
  - 修复 `PluginsView.vue` 中 `trophyStats` 使用未同步的独立 ref 导致的统计数字不更新问题，统一导入响应式 `trophyStats`。
  - 修复 `AnalyticsView.vue`“清空所有统计”时未联动重置 `resetUnlockedTrophies()` 的问题。

### ⚡ 优化 (Changed)
- **奖杯重置机制与即时清空**：
  - 彻底清空并重置本地已获得奖杯数据（`gpdb_unlocked_trophies`），将奖杯系统恢复为初始未解锁状态（0/77）。
  - 在多语言切换（`setLocale`）、网格列数调节（`decreaseCols`/`increaseCols`）、页面 Tab 切换以及 BT 磁力搜索时建立精准的埋点追踪。

## [v2.6.0] - 2026-09-23

### 🚀 架构重构 (Architectural Refactoring)
- **后端核心与边缘模块解耦**：
  - 将 AI 翻译（`translate.py`）与 BFTV 数据同步（`sync_bftv_catalog.py`）的杂乱调度逻辑从主服务 `server.py` 中彻底剥离。
  - 新增独立的 `plugins/` 目录，封装上述功能为独立的模块接口，确保 `server.py` 恢复纯粹的 API 路由分发职责，并且严格保持零第三方依赖。
- **Rust 数据层纯净度审查**：
  - 全面审查 `gpdb-core` (Tauri/Rust 查询层)，确保其纯粹提供原始数据（如 `works_count`），不耦合任何前端 UI 的呈现状态与标识逻辑。

### ⚡ UI 极简与性能优化 (UI Simplification & Performance)
- **剔除过度设计的特效**：
  - 彻底移除耗能较高的 Web Audio 6 音琶音合成器 (`soundSynthesizer.ts`)。
  - 重构 PSN 风格的 `FluidGlassTrophyIcon`，移除严重消耗 GPU 的流体玻璃滤镜 (`backdrop-filter`)，采用扁平化 (Flat) SVG 徽章代替，大幅提升渲染性能。
- **拍平复杂交互层级**：
  - 重写影片与演员详情页中的“想看/已看”多模态手风琴卡片，将其折叠、弹窗打分等繁琐交互降级为清晰直观的单层级切换 (Toggle) 与内联展示。
  - 简化“功能插件/设置中心”面板，移除多余的动态叠加特效，回归标准 Tab 选项卡视图。

### 🧩 模块化升级 (Modularization)
- **前端外挂组件懒加载**：
  - 将各个详情页中重复内嵌的“BT 搜索”、“BFTV 主页直达”等外部资源按钮提取为独立的懒加载组件 (`ResourceSearchWidget.vue`)。
  - 将极速匹配与刮削自动化配置面板隔离至专属的 `SyncModal.vue` 并按需挂载。
  - 全面修复重构过程中产生的组件导入缺失与 TypeScript 报错，执行 `vue-tsc -b` 零警告通过。

## [v2.5.2] - 2026-09-23

### 🚀 新增 (Added)
- **BFTV 演员主页 Sitemap 本地持久化缓存**：
  - `sync_bftv_catalog.py` 新增本地缓存机制，同步完成后将最新 Sitemap XML 持久化至数据库同目录 `sitemap_pornstars.xml`。
  - 每次同步自动与旧缓存做差异对比，并输出新收录演员数量（`✨ 发现 BFTV 官方最新收录演员 N 位！`），实现增量感知。
  - 网络不可达时自动降级读取本地缓存，确保离线场景下继续可用。

### ⚡ 优化 (Changed)
- **演员详情页 BFTV 按钮全面升级**：
  - 移除"在 BFTV 搜索演员资料" fallback 搜索按钮，不再为无法匹配的演员生成搜索链接。
  - 仅当演员拥有已关联的 BFTV 主页 URL 时，才在演员详情弹窗中显示独立的"BFTV #编号"直达按钮（如 `BFTV #5862`）。
  - 按钮样式升级为琥珀色主题，视觉上明确标识为 BFTV 专属功能，点击即可直达官方主页。
  - 按钮编号从 URL 中实时解析提取（正则取最后数字段），无需额外数据库字段。

### 🐛 修复 (Fixed)
- **修复演员详情页 BFTV 按钮跳转逻辑误判问题**：
  - 之前即使演员不在 BFTV Sitemap 中（`bftv_url` 为 NULL），按钮仍显示为"在 BFTV 搜索演员资料"并错误地 fallback 到搜索页，让用户误以为会直达主页。
  - 现在改为：有 BFTV URL → 显示"BFTV #编号"直达按钮；无 BFTV URL → 不显示该按钮，逻辑简洁透明。

---

## [v2.5.1] - 2026-09-23


### 🚀 新增 (Added)
- **BoyfriendTV (BFTV) 演员档案逆向极速同步引擎 (`sync_bftv_catalog.py`)**：
  - **全网目录逆向关联突破性策略**：针对传统单人逐一向 BFTV 搜索匹配耗时数小时且易触发 Cloudflare 质询的性能瓶颈，反向利用 BFTV 全站演员数量远少于本地影库的特性，从 BFTV 官方 CDN Sitemap 瞬时下载解析全站 12,436+ 位男星/模特的官方主页 URL 与专属编号。
  - **零 Cloudflare 阻断极速拉取**：直连 CDN 节点，2.6 秒内完成全站演员列表下载解析，彻底摆脱反爬拦截限制。
  - **智能别名净化与内存倒排索引匹配**：自动剥离 GPDb 数据库中演员后缀如 `(dp)`、`(white)`、`(asian)`、`(aka Kenny)`，并支持连字符 Slug 格式还原与标准化去重匹配。10.8 万演员内存哈希比对仅耗时 0.1 秒，单次批量更新事务仅耗时 3.05 秒，一次性为本地数据库关联新增 16,950+ 位演员的官方 BFTV 直达主页。
  - **无缝集成增量同步流**：在 `sync_gpdb.py` 增量发现新入库演员时自动触发逆向匹配，确保新入库演员立即具备 BFTV 资料直达能力。

### ⚡ 优化 (Changed)
- **自动化刮削更新插件高度自定义执行选项面板**：
  - 在“功能外挂”控制台的“自动化刮削更新”插件中，新增常驻“高度自定义执行选项”展开面板。
  - 用户可在插件内直接切换并配置五大执行策略：
    1. ⚡ **增量极速同步**（自动抓取官网 `/newm`、`/newp`、`/newe`）
    2. 🌐 **BFTV 演员主页秒级关联**（3 秒注入 12,000+ 演员直达链接）
    3. 🚀 **热门新片逆序爬取**（自定义数量 500 ~ 2,000 部）
    4. 🎯 **指定 ID 范围抓取**（自定义起始与结束 ID，支持高达 76,000 范围抓取）
    5. 👥 **演员全量资料补齐**（一键补齐已知演员身材属性与写真头像）
  - 动态显示参数调整输入框（如抓取上限、起始与结束 ID），并提供“立即按自定义配置启动”按钮与全生命周期进度追踪。
- **全功能同步控制中心 (`SyncModal.vue`) 接入 BFTV 演员全网极速匹配**：
  - 新增“BFTV 演员主页全网极速匹配 (3秒入库万条)”策略卡片，支持一键在弹窗中启动并在终端中实时查看匹配进度。

### 🐛 修复 (Fixed)
- **修复应用打包未同步部署导致旧版占位提示文字残留问题**：
  - 针对用户反馈“点击运行后不会开始运行脚本，每次都只输出 增量同步完成！新增影片: 0 部，新增演员: 0 位。已导入数据库，请点击顶部「同步」按钮刷新”的根本原因进行彻底根治：该字符串来源于历史早期的占位 Mock 代码，因本地 `/Applications/GPDb.app` 停留在早间旧版进程未自动更新导致。
  - 重新全量编译 release 二进制与 DMG 生产包并安全部署至 `/Applications/GPDb.app`，全面激活新一代异步刮削引擎。

---

## [v2.5.0] - 2026-09-23

### 🚀 新增 (Added)
- **定时自动后台更新新条目 (Scheduled Background Auto-Sync)**：
  - 自动化刮削更新插件全新集成定时后台静默同步引擎，支持开启/关闭自动调度。
  - 支持用户高度自定义执行计划：
    - **周期循环模式**：可自由设定每 4 小时、6 小时、12 小时（推荐）、24 小时或 48 小时自动静默抓取。
    - **每天定点模式**：支持自定义每天指定时刻（如凌晨 04:00）在后台静默执行。
  - 心跳调度服务 (`services/autoSync.ts`) 实时计算上次自动同步时间与下次计划执行时间，并在同步中心 (`SyncModal.vue`) 与插件页 (`PluginsView.vue`) 提供呼吸态状态指示。
  - 任务执行采用完全解耦的异步后台线程，不占用前台 UI，入库完成后自动无感刷新影库数据与统计信息。
- **首次安装运行环境自动校验与配置向导 (Runtime Environment Check & Guidance)**：
  - 新增 Rust 端 `check_runtime_environment` 诊断命令，全面探测系统环境健康状况：
    - Python 3 解释器安装状态、版本号及执行路径（自动化刮削更新核心基石）；
    - Python 内置 SQLite 模块健康度；
    - 本地 `GPDb.db` 核心数据库连接与有效性；
    - 进阶 Playwright 浏览器反爬自动化引擎就绪状态（用于 BoyfriendTV Cloudflare 穿透）。
  - 全新设计并上线 `EnvironmentCheckModal.vue` 诊断向导：
    - 首次安装或核心环境未就绪时自动温和提醒用户；
    - 提供苹果官方命令行工具 (`xcode-select --install`) 与 Homebrew (`brew install python3`) 一键复制安装命令；
    - 支持一键“重新检测环境”与“去配置数据库”；
    - 在“功能外挂”控制台常驻“运行环境自检”入口，支持随时重新发起体检诊断。

- **增量同步时自动下载并持久化封面与剧照 (Auto-Download Images to Cache)**：
  - 针对增量同步（`sync_gpdb.py`）后新入库条目仅存 URL 无本地图片缓存的问题，新增 `download_image_to_cache()` 离线缓存下载引擎。
  - 在同步新电影、新演员、新分集元数据时，同步将海报大图（`Covers/`）、缩略图（`Icons/`）、分集剧照（`Episodes/`）以及演员写真（`Stars/`）持久化至 `image_cache/`。
  - 默认注入防盗链请求头（`Referer: https://gayeroticvideoindex.com/`）与 `curl` 双重重试机制，有效规避 Cloudflare TLS 异常，确保 macOS 客户端即时以 `gpdb-img://` 协议丝滑秒开高清封面。
- **macOS 27 规范 Dock 栏图标原生热切换与双轨同步引擎**：
  - **遵循 macOS 27 HIG 规范**：全套图标统一遵循 macOS 连续曲率超椭圆（Squircle）网格、824px 画布安全边距、环境落影（Ambient Drop-Shadow）与微透晶体材质规范，由新版 `generate-app-icons.py` 自动化管线生成包含 16x16 至 1024x1024 全像素阶梯的 `AppIcon.icns` 与现代化 Asset Catalog `Assets.car`。
  - **用户自选方案双轨持久化**：新增 `~/.gpdb_icon_scheme` 配置持久化层；Rust 原生 `setup` 启动钩子与前端 `initAppIcon()` 双重加载生效，确保冷启动、热重启或系统唤醒时 Dock 图标均精确保持用户自选方案。

### 🐛 修复 (Fixed)
- **彻底删除旧版 Dock 栏硬编码图标与冷启动重置缺陷**：
  - 彻底删除并清理原 `make-app-icon.py` 与 `AppIcon.icon` 硬编码生成的琥珀褐色 "G" 盘片图标以及旧版 Tauri 遗留资源，将官方推荐方案 A「黑曜石棱镜胶片之匣」设为编译期全局基准图标。
  - 修复前端 `initAppIcon()` 因历史注释未在应用启动时同步调用原生 Dock 图标更新指令的问题，彻底杜绝冷启动时 Dock 栏重置为旧图标的缺陷。
  - 重构 `commands/system.rs` 中的 `set_dock_icon_macos`，接入 `[NSApp setApplicationIconImage:]` 并协同 `[[NSApp dockTile] display]` 强制刷新 Dock Tile，修复切换后偶发延迟重绘问题，并补齐 `release` 内存管理。
- **BoyfriendTV 演员爬虫 Cloudflare Turnstile 质询拦截与别名干扰修复**：
  - **动态穿透 Turnstile 质询盾**：针对 BoyfriendTV 搜索网关 (`/searchgate/`) 部署的 Cloudflare Turnstile 人机质询，升级 Playwright 隐身指纹注入（抹除 `navigator.webdriver` 特征、模拟真实 Chrome 插件及英文语言环境），并引入自适应轮询等待机制（最长 8 秒自动检测并等待 Turnstile 盾解除），彻底根治此前仅等待 2.5 秒导致质询未完成即被判为“未找到”的卡点。
  - **搜索关键词智能净化 (Query Normalization)**：针对 GPDb 数据库中超 26% 演员带有括号别名或年代标注（例如 `(white)`、`(80s)`、`(aka Kenny)`）导致 BFTV 模糊匹配失效的问题，新增 `clean_performer_name()` 正则净化引擎，自动剔除注释字符，大幅提升现代活跃演员的检索命中率。
  - **收录状态精细化提示**：控制台输出细化区分“✓ 匹配成功”与“(BFTV未收录)”，避免混淆网络反爬拦截与平台数据源收录范围差异（BFTV 主打近 20 年活跃模特，GPDb 跨越 50 年历史）。

### ⚡ 优化 (Changed)
- **数据库路径全生态自动识别与双轨持久化**：
  - 客户端成功打开数据库时，自动将规范化绝对路径双轨写入 macOS 标准配置 `com.gpdb.app/db_config.json` 与便利标记文件 `~/.gpdb_db_path`。
  - Python 端重构 `find_default_db_path()` 算法，按序从环境变量 `GPDB_DB`、客户端配置文件、快捷标记文件及本地工作区自动定位数据库，`scrape_bftv_performers.py`、`sync_gpdb.py`、`batch_scraper.py`、`cache_images.py` 无需再手动加 `--db` 参数即可零配置运行。
- **BoyfriendTV 演员主页直链批量抓取能力增强**：
  - `scrape_bftv_performers.py` 优化抓取调度排序，优先遍历有肖像头像的活跃演员，支持 `--name` 单演员精准测试与秒级落库更新。

---

## [v2.4.1] - 2026-09-23

### 🐛 修复 (Fixed)
- **自动化刮削更新插件历史 404 误判修复**：
  - 修复 `sync_gpdb.py` 中因历史全量探测导致大量新 ID（75191..76000）被记录为 404 而被 `get_completed_ids` 判定为“已完成”进而 100% 误杀过滤新片的问题。
  - 重构增量待抓取列表计算逻辑：官网 `/newm`、`/newp` 确认发现的新片/新星条目不受历史 404 阻断，仅对本地数据库已收录记录去重；前向探测引入时效保护机制，并自动清除超前 404 占位缓存。
- **官网最新分集（/newe）增量接入与自动解析**：
  - 在 `sync_gpdb.py` 中接入 `/newe` 实时更新流，新增 `get_latest_ids_from_pages` 与 `parse_episode_details` 专用解析器。
  - 自动抓取新分集标题、高清预览剧照、所属片商（Studio/Company）、发布日期以及关联出演演员表，并通过 `save_company_episodes` 深度入库。
- **macOS 客户端环境下的 Python 路径与独立安装包资源寻址**：
  - 在 Tauri 核心 `commands/sync.rs` 中引入 `resolve_python()` 路径降级解析器，按序探测 `/opt/homebrew/bin/python3`、`/usr/local/bin/python3`、`/usr/bin/python3`，解决 macOS GUI 进程无终端环境变量导致的启动失败。
  - `find_script` 引入多级寻址（含 Tauri App 资源目录 `app.path().resource_dir()` 与可执行程序祖先目录），并在 `tauri.conf.json` 中配置 `bundle.resources`，确保打包 DMG / 独立应用后依然能稳定调用刮削脚本。

### ⚡ 优化 (Changed)
- **“功能外挂”自动化刮削插件视图实时体验升级**：
  - 重构 `PluginsView.vue` 中的刮削卡片交互，将其与统一后台刮削服务（`scraperState` / `startScraperTask`）完全接轨。
  - 引入呼吸态徽标（“同步中”）、微型渐变进度条、实时条目计数（+电影/+演员/+分集）与单行实时日志浮层，支持随时中止任务。
  - 新增“打开同步控制中心”快捷按钮，方便一键呼出全功能同步模态窗（`SyncModal.vue`）。
  - 任务完成后自动触发 `@refresh-movies` 全局数据与统计重载，彻底消除“点击无反应/页面无变化”的断层感。
- **同步结果数据结构增强**：
  - 在 Rust 后端 `SyncResult` 与前端 `ScraperStatus` / `SyncResult` 中统一加入 `new_episodes`（新增分集数）字段，多端数据模型严格对齐。

---

## [v2.4.0] - 2026-09-23

### 🚀 新增 (Added)
- **BoyfriendTV 演员主页直达与精准检索**：
  - 新增 `scrape_bftv_performers.py` 自动化批量爬虫，爬取演员 BFTV 专属档案页 URL 并持久化至 `performers.bftv_url`（支持断点续爬、并发度与延时控制）。
  - 在演员专属档案模态窗 `PerformerDetailModal.vue` 中支持一键直跳主页；若未收录直链则智能降级为演员名精准搜索。
- **BT 资源搜索主标题智能净化 (Clean Title Extraction)**：
  - 新增 `extractMovieCleanTitle` 算法，智能剔除副标题、多余序号与标点修饰符，保留影片自然大小写，全面兼容 em-dash 与 en-dash 破折号。
  - 影片详情卡片 `MovieDetailModal.vue`、分集弹窗 `EpisodeDetailModal.vue` 与分集行 `EpisodeRow.vue` 全面接入 `openBtMovieSearch`，磁力搜索命中准确度显著提升。
- **GitHub Actions 自动编译与 Release 发布流水线**：
  - 交付完整自动化构建工作流 `.github/workflows/release.yml`，打 Tag 即自动拉起 macOS Runner 编译前端与 Rust 核心，生成通用 DMG 安装包。
  - 自动发布 GitHub Release，附带详细 macOS 首次启动安全说明与 `xattr -cr` 隔离属性解除指引。
- **开源门面迁移至 GeavenMax 主页**：
  - 全面将 Git 远程源、克隆地址及 7 种语言矩阵的 Releases 下载链接统一校准至 `https://github.com/GeavenMax/GPDb`。

### ⚡ 优化 (Changed)
- **版本号全生态对齐**：
  - 将 `desktop_client/package.json`、`desktop_client/src-tauri/tauri.conf.json`、`Cargo.toml` 以及底层核心库 `gpdb-core` 统一升级至 `2.4.0`。
  - 在 Tauri 打包配置中显式启用 `["app", "dmg"]` 双产物构建目标。

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
  - 在 `gpdb-core/src/migrate.rs` 提供原生零依赖的独立建表与迁移脚本，无缝在 `~/Documents/GPDb/GPDb.db` 建立包含 14 张核心表结构、全文检索索引与视图的标准库。
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
- 建立 GPDb 离线数据库基础框架与 SQLite WAL 模式支持。
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
