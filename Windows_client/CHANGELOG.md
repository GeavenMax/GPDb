# Changelog (GPDb Windows Client)

All notable changes to the GPDb Windows Client project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
