# Changelog (GPDb Windows Client)

All notable changes to the GPDb Windows Client project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.15.0] - 2026-10-01

### Fixed
- **新用户纯数据库导入场景下远程图片加载与自动离线缓存彻底修复 (`utils/image.ts` & `src-tauri/src/commands/cache.rs`)**：
  - **Windows WebView2 协议端点兼容对齐**：WebView2 内核基于安全沙箱规范拦截自定义协议，前端在 Windows 环境下自适应接入 `http://gpdb-img.localhost/?url=...` 规范端点，解决此前直接请求 `gpdb-img://` 导致请求被 Edge 内核当成未注册系统协议而直接静默阻断报错 (`ERR_UNKNOWN_URL_SCHEME`) 的致命缺陷；
  - **原生 Rust HTTP 线程池下载替代外部子进程**：引入轻量级纯 Rust TLS 高性能客户端 (`ureq` + `rustls` + `webpki-roots`) 接管边看边下载流水线。彻底摒弃此前单图并发调用外部 `curl.exe` 子进程导致的进程风暴、控制台窗口闪烁及缺少 curl 环境无法下载的缺陷；内置连接池复用与 Keep-Alive，支持全自动伪装 User-Agent 与防盗链 Referer 头；
  - **移除 302 重定向并直传图片流**：杜绝向 WebView2 返回 302 跳转导致的跨源跨协议拦截 (`ERR_UNSAFE_REDIRECT`)，直接在 Rust 内存层缓冲并以 200 流式输出图片字节，同步安全持久化至本地 `image_cache/`，实现“首次在线直读，后续 100% 离线亚毫秒呈现”；
  - **全量支持外部第三方图片源离线缓存 (`image_cache/External/`)**：`resolve_cache_target_path` 扩展支持对 PBC (PornBaseCentral)、SmutJunkies 及其他外部高清图源进行 URL 哈希持久化映射，全量图片均享本地离线化待遇。
- **外链点击无法跳转浏览器缺陷修复 (`App.vue` & `PerformerDetailModal.vue` & `main.ts`)**：
  - 根除 Windows WebView2 沙箱拦截原始 HTML `<a href="..." target="_blank">` 导致设置页「访问仓库 ↗」及演员档案页「互联档案」（IAFD、IMDb、X等）点击无响应的缺陷；
  - 在 `main.ts` 中集成全局外链点击委托分发机制，显式为对应链接绑定 `@click.prevent="openUrlExternal(...)"`，通过系统命令原生安全唤起 Windows 默认浏览器（Edge / Chrome 等）。
- **分享卡片选择模糊后保存未模糊 Bug 根除 (`ShareCardModal.vue`)**：
  - 彻底解决 Windows WebView2 下 `CanvasRenderingContext2D.filter` 在部分显卡驱动下静默失效问题；
  - 统一引入硬件中立的双离屏双线性多级降采样模糊算法（Hardware-Agnostic Bilinear Downsample Blur），保证导出图片具备柔和高级的磨砂打码质感；
  - 剧情文字模糊时叠加半透明磨砂脱敏盖章，防止文字可读性泄露。
- **分集剧照分享比例拉伸形变修复 (`ShareCardModal.vue`)**：
  - 分集分享卡片自动切换为 16:9 横屏展板；
  - 实现纯 Canvas 版 `object-fit: cover` 算法，居中裁剪无拉伸变形，彻底杜绝人物面部挤压形变。

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
  - 采用优雅并列卡片排版，左右标明「封面」与「封底」半透明质感胶囊，并无缝适配双离屏模糊保护与高 DPI 导出。
- **分享卡片角落新增 Telegram 官方频道二维码 (`ShareCardModal.vue`)**：
  - 在卡片底部角落集成 27x27 高清点阵二维码，直连官方频道 `https://t.me/gpdbnews`；
  - 采用白底圆角容器与高保真点阵矢量渲染，适配高 DPI 导出与预览，并搭配官方频道文字标示。
- **设置页新增「检查更新」按钮与即时反馈 (`App.vue` & `services/appUpdater.ts`)**：
  - 设置「关于与软件更新」板块中新增「检查更新」快捷按钮，支持随时手动触发检查 GitHub 官方 Releases 仓库；
  - 检查中呈现动态转圈状态，若当前已是最新版则显示「当前已是最新版本 (v2.15.0)」友好提示；若发现新版则弹出更新视窗并支持一键下载安装包更新。
- **启动静默巡检 GitHub Releases 与 EXE 安装包平滑重启升级 (`AppUpdateModal.vue` & `appUpdater.ts`)**：
  - 启动 3 秒后异步巡检 GitHub API，对比语义化版本号；
  - 优先匹配针对 Windows 的 `*.exe` / NSIS 安装包；
  - 弹出拟态浮层展示更新说明与体积，支持单线程断点进度条下载至系统 `%TEMP%` 目录；
  - 下载完成后通过 Rust 命令唤起独立安装向导，并主动调用 `std::process::exit(0)` 平滑退出当前进程释放被占用的文件句柄，避免安装冲突。

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
  - 演职员名单完整换行展示，移除截断限制；
  - 剧情简介移除 `line-clamp-4` 截断，文本度量算法自动计算所需高度并动态分配 Canvas 高度；
  - 彻底解耦全局防窥模式（`screenshotPrivacyEnabled`），卡片预览与保存导出不受主界面全局滤镜干扰。

### Removed
- **彻底移除奖杯成就系统插件及全量关联代码**：
  - 移除 `services/trophySystem.ts`、`components/FluidGlassTrophyIcon.vue`、`components/TrophyResetModal.vue`、`components/TrophyToast.vue`、`views/TrophiesView.vue`；
  - 从侧边栏、分析看板、插件管理器与国际化字典中彻底剔除成就依赖，大幅精简打包体积与后台监听。
- **移除桌面端「全功能伪装计算器 & 紧急脱身 (Panic Switch)」功能及代码**：
  - 遵循「仅手机端保留物理伪装脱身，桌面端回归纯粹高质感」准则；
  - 移除 `FakeCalculatorModal.vue`、快捷键侦听、全局防窥联动及设置页开关，净化桌面端使用体验。

## [2.14.0] - 2026-09-30

### Added
- **Windows 客户端演员档案页深度整合 PBC 百科全维度资料 (`PerformerDetailModal.vue`)**：
  - **严格冲突优先级与已有数据无损原则 (GEVI-First Non-Destructive Resolution)**：
    - 生理规格（身高、体重、尺寸规格、体型、肤色、包皮、发色、瞳色、体毛、胡须、纹身）：严格执行 `p.field || pbc.field`。**只要 GEVI 原始字段有值，100% 保持 GEVI 不变**；仅当 GEVI 字段缺失时，才无缝由 PBC 权威数据填补，绝不丢失已有数据。
    - 艺名与别名（Aliases）：执行去重并集合并，聚合呈现 GEVI 与 PBC 的全量艺名表。
    - 本名（Birth Name）：若与艺名不同，在作品数旁以次级柔和色调呈现 `本名: xxx`。
    - 出道年份（Career Start）：清晰呈现 `出道: 2008年`。
    - 活跃状态微标（Career Status）：顶部直观展示绿色脉冲微光徽标 `活跃中 Active` 或灰色 `已退役 Retired`。
    - 出生日期与年龄（Birth Date & Age）：以属性胶囊形式规范呈现 `出生: 1983-02-11 (43岁)`。
    - 星座与族裔（Astrology & Ethnicity）：内置标准化汉化映射，如 `星座: 水瓶座`、`族裔: 白人`。
    - 表演风格特色（Performance Tags）：汉化展示演出标签，如 `无套 (Bareback)`、`内射 (Creampie)`、`双龙` 等。
    - 维基生平人物小传（Bio Card）：极简半透明玻璃拟态卡片呈现人物生平，支持一键点击 `完整词条 ↗` 直达原百科网页。
    - 全网互联档案徽标（Connected Profiles）：底部聚合展示 `IAFD ↗`、`IMDb ↗`、`X (Twitter) ↗`、`OnlyFans ↗`、`Instagram ↗` 快捷胶囊。
    - 操作栏快捷入口：操作区部署 `PBC 百科 ↗` 快捷入口，一键呼出浏览器查看完整词条。
  - **界面一致性与简洁性保障**：
    - 严格复用 GPDb 的 Surface / Zinc / Accent 配色系统与毛玻璃材质；
    - 移除视觉干扰项：精简移除来自 PBC 的浅紫色攻受角色胶囊（无套定位、安全套定位、内射角色），保持档案信息聚焦克制；
    - 纯粹条件渲染：对于尚未收录 PBC 档案的普通演员，界面保持原汁原味的极简 GEVI 视图，不产生任何空白卡片或未匹配占位符。
- **底层数据库与查询引擎深度支持 SmutJunkies 扩展架构 (`gpdb-core`)**：
  - `migrate.rs` 自动保证 `performers.sj_url` 字段与 `performer_sj_profiles` 扩展表及其索引结构存在；
  - `queries/performers.rs` 自动从 `performer_sj_profiles` 中合并别名，并在 `performers.sj_url` 缺失时自动回退提取扩展表 URL。
- **全新曾用艺名 / 别名 (AKA) 独立徽章栏与动态折叠卡片 (`PerformerDetailModal.vue`)**：
  - 移至人物主卡片下方独立全宽展示，采用 Surface 玻璃拟态与标签设计，支持别名总数角标提示；
  - 智能折叠机制：超过 8 个别名时自动折叠并提供「展开全部 / 收起」按钮，杜绝多艺名演员档案过度拉长；
  - 演员备注说明（Notes）独立全宽呈现，排版更加精致协调。

### Fixed
- **Windows Rust/Tauri 构建依赖修复**：
  - 补充 `Windows_client/src-tauri/Cargo.toml` 中缺失的 `base64 = "0.22"` 依赖。
  - 修正 `database.rs` 中 `get_unique_filepath` 模块私有可见性问题。
  - 修复 `system.rs` 中原生剪贴板跨平台兼容性代码。
  - 通过 `npx vue-tsc -b` 检查与 `cargo check` 编译。

---

## [2.13.0] - 2026-09-29

### Added
- **截屏分享隐私保护与全局高斯防窥模式 (Screenshot Privacy & Blur Mode)**：
  - **手动启用与一键快捷切换**：在设置「隐私与数据安全」中提供专属控制面板，并在顶部导航栏 (`Navbar.vue`) 放置一键“截屏防窥”眼睛开关；支持快捷键与即时状态切换（防窥中 / 正常浏览）。
  - **细粒度隐私脱敏控制**：
    - `模糊海报与剧照图片`：全局高斯模糊 (`blur(24px)`) 所有影视封面、剧照、演职员头像等敏感视觉画面，杜绝截屏或社媒分享时泄露画面；
    - `模糊剧情介绍与敏感文字`：高斯模糊 (`blur(7px)`) 影视简介、分集梗概等敏感文字并禁止文本选中，防止剧透与涉密内容外泄。
- **影片与分集一键精美分享卡片生成器 (Aesthetic Share Card Generator)**：
  - **自适应海报高斯背景取色**：全新架构的 `ShareCardModal.vue` 组件，背景光晕直接从海报封面自适应提取色相并实施高精度大半径高斯模糊 (`blur(45px~60px)`)，搭配暗夜渐变遮罩，呈现 Apple Music / Spotify 级别的流光视效。
  - **海报与文字安全脱敏分享 (Selective Blur for Sharing)**：
    - 在生成分享卡片时，用户可独立勾选「模糊海报封面」（海报注入高斯模糊并覆盖隐私保护徽章）与「模糊剧情介绍」（文字注入高斯模糊并覆盖脱敏标签），实现 100% 安全社交分享。
    - 提供“流光 (Vibrant)”、“深黑 (Dark)”、“午夜 (Midnight)”三种专属背景光晕预设，以及剧情概要显示开关。
  - **超高清 Retina 2x 双重导出**：
    - **复制卡片图片 (Copy Image)**：基于离线 HTML5 Canvas 2D 引擎以 2x 超高清分辨率直接光栅化卡片为 PNG Blob，并无缝写入系统剪贴板（支持在微信、QQ、Telegram、Discord、X/Twitter 等社交聊天软件中直接 <kbd>Ctrl + V</kbd> 粘贴分享）；
    - **保存为图片 (Save Image)**：一键将高质量分享卡片导出至本地磁盘 (`.png`)。
  - **双档案页全面接入**：在影片详情页 (`MovieDetailModal.vue`) 与分集详情页 (`EpisodeDetailModal.vue`) 操作栏全面部署「分享卡片」入口。

---

## [2.12.0] - 2026-09-29

### Added
- **全新独立 Windows 客户端工程体系 (`/Windows_client`)**：
  - **架构解耦与独立维护**：在项目根目录下建立专有 `/Windows_client` 目录，将 Windows 专属的 Tauri 2 配置、打包管线、构建脚本与原生界面微调收拢于单一工程内，彻底避免与 macOS (`desktop_client`) 及 Android (`android_client`) 的开发环境产生任何文件侵染与构建冲突。
  - **开箱即用的一键构建套件**：提供 `build_windows.ps1` (PowerShell) 与 `build_windows.bat` (批处理) 双脚本，自动检查 Node.js (18+) 与 Rust (MSVC) 工具链环境，一键安装依赖并快速编译生成 Windows 安装程序。
  - **完善的工程自述与指引**：新增 `Windows_client/README.md`，详细记录本地开发运行、环境依赖要求（Node 18+、Rust MSVC、WebView2 Runtime）与 NSIS 打包全流程。
- **Windows Python 运行环境智能探测链 (Multi-Source Python Resolution Engine)**：
  - 针对 Windows 平台下 Python 安装形态多样的痛点（官方安装包、微软商店沙盒、Conda、自定义路径），实现多级分层智能探测：
    1. 优先读取用户显式指定的 `GPDB_PYTHON` 环境变量；
    2. 自动检索 `%LOCALAPPDATA%\Programs\Python\Python3xx\python.exe` 独立用户目录；
    3. 自动检索 `%ProgramFiles%\Python3xx\python.exe` 机器全局目录；
    4. 自动检索 `%USERPROFILE%\anaconda3\python.exe` 与 `%USERPROFILE%\miniconda3\python.exe` 科学计算环境；
    5. 降级执行 `python` / `python3` / `py` 命令行探测与快速静默启动校验，确保各类用户即装即用。

### Changed
- **跨平台 CI/CD 自动化构建矩阵解耦 (`.github/workflows/release.yml`)**：
  - GitHub Actions 构建矩阵引入 `client_dir` 参数，macOS 节点绑定 `desktop_client`（产出 `.dmg` / `.app`），Windows 节点绑定 `Windows_client`（产出 NSIS `.exe`），两端缓存键与依赖安装完全独立隔离。
- **Windows 原生视觉体验润饰与字体平滑 (Look & Feel Adaptation)**：
  - 优化全局中文字体回退链：优先使用 `"Microsoft YaHei UI"` 与 `"Microsoft YaHei"`，并开启 `-webkit-font-smoothing: antialiased` 与 `text-rendering: optimizeLegibility`。
  - 标准化现代化极细滚动条：注入 `scrollbar-width: thin` 与 `scrollbar-color: var(--surface-2) var(--app)`，规避 Windows 默认粗宽滚动条遮挡或挤压内容布局。
  - 窗口贴靠无损适配：保留原生装饰栏 (`decorations: true`)，支持 Windows 11 最大化按钮悬停唤出原生分屏贴靠布局矩阵（Snap Layouts）。
- **NSIS 单用户轻量化免提权安装规范**：
  - `Windows_client/src-tauri/tauri.conf.json` 采用 `installMode: "currentUser"` 与 `displayLanguageSelector: true`，安装全程无需 UAC 管理员提权，随装随用。
  - 内置高质量多分辨率 Windows 图标资源 (`icons/icon.ico`)。

### Fixed
- **Windows 子进程控制台黑框闪烁彻底消除 (CREATE_NO_WINDOW Console Suppression)**：
  - 根因定位：在 Windows GUI 应用程序（Tauri / Win32）中，通过 `std::process::Command` 直接执行外部命令（如 Python 爬虫、任务终止命令或 curl 网络请求）时，Windows 默认会瞬时弹出黑色控制台窗口并抢占焦点。
  - 修复实现：全面引入 `#[cfg(target_os = "windows")]`，并在所有子进程调用前统一注入 `creation_flags(0x08000000)` (`CREATE_NO_WINDOW`)，彻底消除以下所有场景下的黑框闪烁：
    1. 后台数据刮削与同步引擎启动 (`commands/sync.rs: start_scraper`)；
    2. 取消同步时的子进程树强制终止 (`commands/sync.rs: taskkill`)；
    3. 同步状态更新与实时管道读取 (`commands/sync.rs: run_sync`)；
    4. 运行时依赖检测与解释器有效性验证 (`commands/environment.rs`)；
    5. 翻译大模型服务商可用模型在线探测 (`commands/translate.rs: curl`)。

---

## [2.11.0] - 2026-09-29

### Added
- **多维度时间与入库批次时效筛选 (Multi-Dimensional Date Filters)**：
  - 与全生态全面统一支持 6 大时间范围过滤：`全部 (All)`、`上次入库 (Last Scraped)`、`最近7天 (Recent 7 Days)`、`最近30天 (Recent 30 Days)`、`最近90天 (Recent 90 Days)` 以及 `本年度 (Recent Year)`。
  - 在影片与分集主界面新增快速时间筛选胶囊栏，在高级筛选抽屉 (`FilterDrawer.vue`) 与活动筛选标签栏 (`ActiveFilterBar.vue`) 全面挂载时间胶囊交互，底层 Rust 查询引擎 (`gpdb-core`) 高性能快速召回。
  - 影片卡片 (`MovieCard.vue`) 与分集卡片 (`EpisodeCard.vue`) 新增 2026 及最新入库批次条目的动态渐变 `NEW` 高亮微标。
- **海报双排版方案与全屏手势/滚轮缩放灯箱 (Poster Schemes & Zoomable Lightbox)**：
  - 详情页全面支持“自适应高清画廊 (`adaptive_pager`)”与“3D 拟真实体卡片 (`flip_3d`)”，具备 60fps CSS 3D 景深物理透视、实体翻转胶囊按键与环境泛光。
  - 全屏灯箱 (`ImageLightbox.vue`)：支持鼠标滚轮平滑缩放 (1.0x ~ 5.0x)、按住拖拽平移、双击智能缩放/复位、键盘快捷键 (+/-/0/Esc) 与悬浮操作工具栏。
- **桌面端系统级隐私安全套件 (Desktop Privacy & Security Suite)**：
  - **PIN 码安全应用锁**：新增数字 PIN 锁与全屏玻璃拟态锁屏界面 (`AppLockOverlay.vue`)，支持自定义自动锁定策略（失焦立即锁定、离开 1/5/15/30 分钟后自动锁定）。
  - **窗口失焦高斯毛玻璃防窥遮罩**：失焦或切换其他应用时瞬间覆盖高斯毛玻璃防窥层。
  - **全功能伪装计算器 & 紧急脱身 (Panic Switch)**：一键瞬间伪装为标准深色计算器 (`FakeCalculatorModal.vue`)，支持真实四则运算；按下全局快捷键 <kbd>Ctrl + Shift + P</kbd> 极速呼出；在计算器中输入 PIN 码并按 `=`，或连击顶部标题 4 次即可安全解锁返回。
  - **应用窗口伪装标题**：支持自定义窗口标题（如 "Calculator", "Notes"），防止任务切换器或窗口列表中暴露应用属性。
- **大模型翻译数据双向导入导出与动态探测 (JSON Translations & Model Discovery)**：
  - 在插件中心新增“导出翻译 (JSON)”与“导入翻译”功能，通过 Rust 底层直接读取/写入 SQLite，支持跨平台无缝合并沉淀的已翻译影片与分集简介。
  - 在翻译服务配置面板中新增“探测可用模型”一键检测按钮，调用标准 OpenAI 兼容 `/models` 接口自动提取账号可用模型并填入建议列表。
  - 翻译服务商新增字节跳动豆包 (`doubao`) 预设。
- **导演频道与全库深度对齐 (Directors System Alignment)**：
  - 完善导演维度索引、导演作品多维度关联计算与收藏闭环。

---

## [2.10.0] - 2026-09-24

### Added
- **桌面端离线刮削与多源同步引擎 (Offline Scraper & Multi-Source Sync)**：
  - 整合 `sync_gpdb.py` 与 `sync_bftv_catalog.py` 后台抓取流程至桌面端，支持实时进度事件推送与日志流展示。
  - 支持片商、演员与分集三维结构同步与合并。
- **主题系统与多语言架构**：
  - 支持 6 套高质感色彩主题与跟随系统的自适应模式。
  - 完整接入 7 种语言国际化字典，支持界面即时切换。

---

## [2.9.0] - 2026-09-23

### Added
- **SQLite 高性能本地查询引擎 (gpdb-core)**：
  - 实现基于 `rusqlite` 的嵌入式轻量级查询层，支持千部作品秒级检索与分页。
  - 引入影片、演员、片商三维交叉索引与用户收藏标记。

---

## [2.7.0] - 2026-09-21

### Added
- **GPDb 桌面客户端项目初始化**：
  - 采用 Tauri 2 + Vue 3 + Tailwind CSS 技术栈，建立跨平台桌面应用架构基础。
