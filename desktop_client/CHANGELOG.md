# Changelog

All notable changes to the macOS / Desktop Client will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.15.0] - 2026-10-01

### Fixed
- **外链点击无法跳转浏览器缺陷修复 (`App.vue` & `PerformerDetailModal.vue` & `main.ts`)**：
  - 根除 Tauri WebKit 沙箱拦截普通 HTML `<a href="..." target="_blank">` 导致设置页「访问仓库 ↗」及演员档案页「互联档案」（IAFD、IMDb、X等）点击无响应的缺陷；
  - 在 `main.ts` 中集成全局外链点击委托分发机制，显式为对应链接绑定 `@click.prevent="openUrlExternal(...)"`，通过系统命令原生安全唤起 macOS 默认浏览器。
- **P0 级致命卡死与崩溃根治：Objective-C 剪贴板 Over-Release 修复 (`system.rs`)**：
  - 修复 `commands/system.rs` 中 `copy_image_to_clipboard` 对 `+[NSData dataWithBytes:length:]` 与 `+[NSString stringWithUTF8String:]` 返回的自动释放（Autoreleased）对象错误显式调用 `release` 导致的 Double-Free 内存崩溃；
  - 彻底杜绝主线程 AppKit / NSPasteboard 损坏及转入保存图片时的伪死锁（Beachball）。
- **分享卡片选择模糊后保存未模糊 Bug 根除 (`ShareCardModal.vue`)**：
  - 解决 macOS WebKit 2D Canvas 下 `ctx.filter` 硬件加速未完整支持或失效的问题；
  - 引入硬件中立的双离屏双线性多级降采样模糊算法（Hardware-Agnostic Bilinear Downsample Blur），生成高质感磨砂打码效果并大幅降低主线程 GPU 栅格化开销；
  - 剧情简介勾选模糊时叠加半透明磨砂脱敏盖章，彻底杜绝文字内容泄露。
- **分集剧照分享比例拉伸形变修复 (`ShareCardModal.vue`)**：
  - 自动区分电影（3:4 竖版）与分集剧照（16:9 横版展台）；
  - 实现 Canvas 版 `object-fit: cover` 居中防形变裁剪算法，确保剧照中人物面部绝不变形。

### Added
- **全平台统一通用用户配置与数据备份/恢复机制 (`App.vue` & `services/analytics.ts`)**：
  - 设置页「数据管理」中新增「用户配置与数据备份/迁移 (跨设备通用 JSON)」模块；
  - 导出范围覆盖用户在 SQLite 中的全部「我的收藏」（包含长片、分集、演员、导演、片商、系列等）、自定义标签、想看/已看状态与私密评星笔记，以及全量本地统计数据（总专注时长、各维度播放与探索计数、活跃天数等）与界面偏好；
  - 导出格式采用统一规范 `gpdb_universal_backup` JSON，支持在 macOS、Windows、Android 与 iOS 四端通用互认，跨设备直接导入无缝迁移。
- **PBC 与 SmutJunkies 演员刮削引擎深度集成与自动入库 (`src-tauri/src/commands/sync.rs` & `ScraperConfigPanel.vue`)**：
  - 在底层 Rust 命令 `sync.rs` 中完整打通 `scrape_pbc_actors.py` 与 `scrape_smutjunkies_actors.py` 执行流水线；
  - 支持前台进度正则捕获（条目索引、总数、新增与更新数）、实时日志流与终止保护；
  - 刮削执行完成自动以 `--apply` 与 `--backfill` / `--home` 将数据无损注入 SQLite 数据库。
- **分享卡片支持封面与封底双海报并排展示 (`ShareCardModal.vue` & `MovieDetailModal.vue`)**：
  - 分享卡片全面支持同时加载正向海报封面与反面封底（如有）；
  - 采用优雅并列卡片排版，左右标明「封面」与「封底」半透明质感胶囊，并无缝适配双离屏模糊保护与 2x Retina 高清导出。
- **分享卡片角落新增 Telegram 官方频道二维码 (`ShareCardModal.vue`)**：
  - 在卡片底部角落集成 27x27 高清点阵二维码，直连官方频道 `https://t.me/gpdbnews`；
  - 采用白底圆角容器与高保真点阵矢量渲染，适配 Retina 2x 高清导出与预览，并搭配官方频道文字标示。
- **设置页新增「检查更新」按钮与即时反馈 (`App.vue` & `services/appUpdater.ts`)**：
  - 设置「关于与软件更新」板块中新增「检查更新」快捷按钮，支持随时手动触发检查 GitHub 官方 Releases 仓库；
  - 检查中呈现动态转圈状态，若当前已是最新版则显示「当前已是最新版本 (v2.15.0)」友好提示；若发现新版则弹出更新视窗并支持一键下载安装包更新。
- **启动静默巡检 GitHub Releases 与 DMG 在线挂载更新 (`AppUpdateModal.vue` & `appUpdater.ts`)**：
  - 启动 3 秒后异步巡检 GitHub API，对比语义化版本号；
  - 弹出拟态浮层展示更新说明与体积；
  - 支持单线程断点进度条下载，下载完成后调用 Rust 命令 `open <dmg_path>` 自动挂载 DMG 镜像，引导用户覆盖升级。

### Changed
- **设置页二级胶囊菜单新增「关于与更新」独立分类 (`App.vue`)**：
  - 顶部胶囊导航新增「关于与更新」独立分类，便于用户快速切换直达版本更新与开源仓库专区。
- **连贯篇章系列大放送随机抽取展示 (`HomeView.vue` & `gpdb-core/src/queries/series.rs`)**：
  - 首页「经典系列大放送 · 连贯篇章」由原有固定以合集中影片数量降序（`ORDER BY movie_count DESC`）改为随机抽取排序（`ORDER BY RANDOM()`）；
  - 模块标题栏新增「换一批」快捷随机刷新按钮，每次刷新呈现不同精品系列。
- **设置页排版大幅净化与图示化 (`App.vue`)**：
  - 彻底移除设置中对「应用图标方案 (App Icon)」的冗长文字说明；
  - 将「海报展示与翻转排版方案」的大段陈述性文字全面升级为直观的交互式微缩示意图（自适应平铺画廊 vs 3D 景深翻转卡片），所见即所得。
- **分享卡片全维度自适应排版与纯粹流光背景 (`ShareCardModal.vue`)**：
  - 精简背景光晕切换选项，统一保留视觉表现最佳的默认「流光 (vibrant)」渐变背景；
  - 演职员名单完整换行展示，移除 `slice(0, 4)` 截断限制；
  - 剧情简介移除 `line-clamp-4` 截断，文本度量算法自动计算所需高度并动态分配 Canvas 高度；
  - 彻底解耦全局防窥模式（`screenshotPrivacyEnabled`），卡片预览与保存导出不受主界面全局滤镜干扰。
- **精简首页视觉与算力开销 (`HomeView.vue`)**：
  - 移除首页顶部的「AI 专属定制导赏」Banner 模块，保留插件页（`PluginsView.vue`）原有独立 AI 模块不受影响。

### Removed
- **彻底移除奖杯成就系统插件及全量关联代码**：
  - 移除 `services/trophySystem.ts`、`components/FluidGlassTrophyIcon.vue`、`components/TrophyResetModal.vue`、`components/TrophyToast.vue`、`views/TrophiesView.vue`；
  - 从侧边栏、分析看板、插件管理器与国际化字典中彻底剔除成就依赖，大幅精简打包体积与后台监听。
- **移除桌面端「全功能伪装计算器 & 紧急脱身 (Panic Switch)」功能及代码**：
  - 遵循「仅手机端保留物理伪装脱身，桌面端回归纯粹高质感」准则；
  - 移除 `FakeCalculatorModal.vue`、快捷键侦听、全局防窥联动及设置页开关，净化桌面端使用体验。
