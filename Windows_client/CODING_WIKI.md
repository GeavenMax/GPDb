# GPDb Windows 客户端开发规范与技术架构 Wiki (Coding Wiki)

> **当前工程基线**：`v2.18.0`（2026-10，Windows 10/11 平台专属深度适配）  
> **双端状态**：与 `desktop_client`（macOS 桌面端）在业务逻辑、数据接口、组件模型及多语言词条上保持 1:1 双向镜像。

---

## 1. 客户端功能定位与设计目标

GPDb Windows 客户端是基于 **Tauri v2 + Rust + Vue 3 (Composition API) + Tailwind CSS v4** 深度适配 Windows 10 与 11 操作系统的桌面端离线影库系统。  
其核心定位为：**“极致轻量、超低内存开销、深度融合 Windows Fluent / Mica 设计美学、稳定驾驭多驱动器海量媒体的桌面掌上影库”**。

### 1.1 核心业务功能矩阵

| 功能模块 | 功能特征与技术实现 | Windows 桌面端专属优化 |
| :--- | :--- | :--- |
| **全库检索与多维筛选** | 63,270+ 部影片、134,480+ 分集、108,230+ 演员、2,430+ 厂牌 | Rust 原生 SQLite WAL 高性能并发查询、预编译 SQL 缓存、毫秒级响应、支持厂牌中英双向模糊检索 (`COLLATE NOCASE`) |
| **沉浸式主页 (Home Feed)** | 焦点海报轮播、今日星光、经典系列大放送、随机抽选盲盒 | 今日星光仅展示有效头像演员（居中偏上对齐防切头）、经典系列 4 列宽画幅大展台随机抽选（8组）、流光背景 |
| **片商生态与历史专栏** | 2,433 家厂牌 100% 汉化百科、官方网站直跳、网格/列表双模分集展示 | 数据库内置 WebP Logo/Banner 离线引擎、12 点边缘取色与留白融合、4 列网格记忆、16:9 剧照画幅比例防形变 |
| **离线媒体与海报系统** | `image_cache/` 物理目录直接映射、`studio_logos` 表内置 WebP 直传 | 适配 WebView2 虚拟域端点 (`http://gpdb-img.localhost`) 与 `gpdb-img://` 协议，三级容灾读取（数据库 -> 本地缓存 -> 纯 Rust TLS 抓取） |
| **全站 7 语种 100% 镜像对齐** | 覆盖 zh-CN, zh-TW, en, ja, it, es, de 7 大语种，931 键绝对 1:1 对齐 | 界面文本与数据库实体完全解耦，支持 3 档内容呈现模式（自动匹配/双语/原文），严禁硬编码中文，配备 `test_parity.py` 自动化 CI 校验 |
| **影视/演员/分集模态档案** | 多海报自适应平铺、曾用艺名 (AKA) 折叠卡片、分集简介行内自由展开 | 统一层叠上下文管理 (`zIndex`)、全局 Esc 键捕获守卫 (`claimEscape` 拦截冒泡)、一键批量展开收起收录章节、直达分集独立档案 |
| **全平台通用数据备份与迁移** | 用户收藏夹、播放探索统计、自定义标签、打标与私密笔记 | `gpdb_universal_backup` 跨平台统一 JSON 规范，与 macOS / Android / iOS 四端互通 |
| **流光双封面分享卡片** | 正向封面与反向封底双海报并排排版、Telegram 官方频道点阵二维码 | Canvas 2D 高清渲染、双离屏磨砂打码防窥、27×27 矢量点阵二维码直达官方频道 (`t.me/gpdbnews`)，移除冗余日期 |
| **纯 Rust 进程内大模型翻译** | 支持 Gemini、DeepSeek、OpenAI 兼容协议、Claude、本地 Ollama 等 | 单条纯文本智能容纳、自动移除不支持的 `response_format` 并支持 400 重试、端点 URL 补全 `/v1`，配置双向漫游 |
| **刮削器集成与自动入库** | PBC 与 SmutJunkies 演员刮削引擎、全量片商网站抓取 | Windows 虚拟环境自动发现、UNC 前缀剥除、控制台 UTF-8 编码防乱码、子进程无黑框抑制 (`CREATE_NO_WINDOW`) |
| **系统托盘与任务栏指示** | 原生托盘常驻、Jump List 快捷直达、任务栏刮削进度实时指示 | 原生托盘右键菜单、左键极速切换窗口、关闭最小化到托盘、任务栏 `ProgressBarState` 实时进度条驱动 |
| **版本检测与在线更新** | GitHub Releases API 检索、版本特性展示、安装包自动下载 | 异步检测最新版本，匹配 Windows NSIS 安装包 (`.exe`) 引导用户一键升级 |

---

## 2. Windows 桌面端技术栈全景

```
[ UI 渲染层 (Frontend - WebView2) ]
  Vue 3.5+ (Composition API, <script setup>) + TypeScript 6.0+
  构建套件: Vite 8.3+ + @tailwindcss/vite 4.3+
  图标体系: Lucide Icons (@lucide/vue / lucide-vue-next)
  窗口框架: 深度适配 Windows 窗口控制、无边框阴影、拖拽区域 (data-tauri-drag-region)
  国际化: 7 语种 931 键绝对镜像对齐 (zh-CN, zh-TW, en, ja, it, es, de)

[ 桥接与命令层 (Tauri Bridge Layer - src-tauri) ]
  Tauri v2.11+ (基于 Microsoft Edge WebView2 宿主)
  窗口特效: tauri::window::{EffectsBuilder, Effect} (Mica, Mica Alt, Acrylic)
  系统托盘: tauri::tray::{TrayIconBuilder, TrayIconEvent} + Jump List
  任务栏指示: window.set_progress_bar (ProgressBarState)
  安全虚拟协议: http://gpdb-img.localhost 与 gpdb-img:// (原生流拦截与边看边下)
  Tauri IPC Commands: 数据库挂载、Explorer 定位、系统剪贴板 (arboard)、Python 刮削调度、纯 Rust 翻译

[ 后端核心与数据库引擎 (Rust Core Layer) ]
  独立 Workspace Crate: gpdb-core (零 Tauri 耦合，便于多平台共用核心算法与单元测试)
  数据驱动: rusqlite 0.31 (内嵌 SQLite 3.45+, 启用 WAL 模式, 并发连接池)
  系统底层: Win32 API / arboard (安全处理 DIB / PNG 格式系统剪贴板，杜绝 STA 线程锁死)
  网络层: ureq 2.12 (启用 rustls, 零 OpenSSL 依赖)
```

---

## 3. 核心子系统与关键设计规范

### 3.1 Windows 路径标准化与 UNC 前缀消除 (`normalize_path`)

Windows 环境在处理路径时存在反斜杠 `\`、驱动器盘符（如 `D:\`）、长路径前缀（`\\?\`）以及 UNC 共享网络路径等特殊形态。

#### 路径安全规范
1. **统一路径规整化与剥离 UNC 扩展前缀**：
   在 Rust 后端使用 `canonicalize()` 时，Windows 系统会为规范路径自动添加 `\\?\` 长路径前缀（例如 `\\?\C:\Users\...`）。当此类路径被直接传递给 Python 子进程解释器（`python.exe`）或命令行工具时，会报 `OSError: [WinError 123] 文件名、目录名或卷标语法不正确`。  
   在 `commands/sync.rs` 中引入了 `normalize_path` 工具函数：
   ```rust
   pub fn normalize_path(path: PathBuf) -> PathBuf {
       #[cfg(target_os = "windows")]
       {
           let s = path.to_string_lossy();
           if s.starts_with(r"\\?\") {
               return PathBuf::from(&s[4..]);
           }
       }
       path
   }
   ```
   所有传给子进程的脚本路径与数据库路径必须经过 `normalize_path` 消毒。
2. **多驱动器与外置分区适配**：
   媒体资源库 `image_cache` 可能存放于外置移动硬盘或非系统分区（如 `E:\GPDb_Images`），Rust 文件流必须直接基于绝对物理路径映射，严禁假设资源在应用根目录。

### 3.2 子进程防闪烁与控制台 UTF-8 流式管道

在 Windows 上执行 `scrape_pbc_actors.py`、`scrape_smutjunkies_actors.py` 与 `sync_gpdb.py` 时：
1. **窗口抑制 (`CREATE_NO_WINDOW`)**：
   通过注入 Windows 专属标志位 `CREATE_NO_WINDOW (0x08000000)`，防止后台任务拉起时出现黑色 CMD 弹框打扰用户：
   ```rust
   #[cfg(target_os = "windows")]
   {
       use std::os::windows::process::CommandExt;
       cmd.creation_flags(0x08000000);
   }
   ```
2. **控制台编码防乱码与字节流安全读取**：
   Windows 默认控制台代码页通常为 GBK (CP936)。Rust 在拉起子进程时必须注入环境变量 `PYTHONUNBUFFERED=1`、`PYTHONIOENCODING=utf-8` 与 `PYTHONUTF8=1`。  
   在流读取函数 `read_line_or_cr` 中，采用基于字节向量的 `raw_bytes` 缓冲配合 `String::from_utf8_lossy(&raw_bytes)` 解析，彻底杜绝中文字符在 `\r` 回车换行分割时被逐字节截断产生的乱码问题。

### 3.3 片商 Logo & 横幅内置数据库离线协议 (`studio_logos`)

为保证零缓存新用户在无外部 `image_cache/` 图片包的情况下也能 100% 离线浏览所有片商的高清标志与横幅，构建了三级容灾读取链路：
1. **Priority 1: SQLite 内置 WebP 二进制直读 (`load_logo_from_db`)**：
   直接查询 `studio_logos` 表中的 `logo_webp` / `banner_webp` 字段（通过精确文件名比对及片商 Slug 模糊兜底），命中后直接向 WebView2 返回 `image/webp` 响应及 1 年长缓存头；
2. **Priority 2: 本地磁盘缓存读取 (`resolve_cache_file`)**：
   若数据库未内嵌，检测本地磁盘 `image_cache/Logos/` 物理文件并流式输出；
3. **Priority 3: 纯 Rust TLS 异步按需拉取 (`ureq` + `rustls`)**：
   远端静默抓取并落盘持久化至 `image_cache/`，后续请求直达本地。

### 3.4 全局 Esc 键层级捕获守卫 (`claimEscape`)

在全屏沉浸式阅读与多层模态弹窗（例如影片详情 -> 演员详情 -> 片商详情 -> 分集详情）交互中：
- 模态窗消费 Esc 键时统一注入 `e.preventDefault()` 与 `e.stopPropagation()`；
- `App.vue` 顶层注册捕获阶段（Capture Phase）按键守卫 `onGlobalEscapeGuard`，当存在打开的详情模态、系列大放送、筛选抽屉或图片灯箱时，第一时间拦截事件，优先退出顶层浮层，杜绝系统直接退出全屏模式。

---

## 4. 目录组织架构 (Source Tree)

```
Windows_client/
├── package.json                         # 前端依赖配置 (Vue 3, Vite, Tailwind CSS v4)
├── vite.config.ts                       # Vite 8 构建配置
├── CODING_WIKI.md                       # 开发规范与技术架构 Wiki
├── README.md                            # 快速上手与工程简介
├── HANDOVER.md                          # 交接说明与核心铁律文档
├── scripts/
│   └── test_parity.py                   # 7 语种 i18n 100% 镜像对齐校验脚本
├── src/
│   ├── main.ts                          # 前端入口，挂载 App.vue
│   ├── App.vue                          # 顶层布局框架、Windows Fluent 材质切换、系统托盘监听
│   ├── api.ts                           # Tauri invoke IPC 强类型封装
│   ├── types.ts                         # 全量 TypeScript 元数据与业务数据实体定义
│   ├── theme.css                        # Fluent 材质半透明与亮暗色主题自适应规则
│   ├── views/
│   │   ├── HomeView.vue                 # 沉浸式主页 (巨幕轮播、今日星光、经典系列、随机抽选)
│   │   ├── AnalyticsView.vue            # 用户本地专注与偏好洞察统计
│   │   └── PluginsView.vue              # 插件管理与刮削器配置面板
│   ├── components/
│   │   ├── MovieDetailModal.vue         # 影片详情模态框 (3D翻转、海报画廊、演职员表、AI翻译)
│   │   ├── EpisodeDetailModal.vue       # 分集详情模态框 (独立剧照画幅、AI翻译)
│   │   ├── PerformerDetailModal.vue     # 演员档案模态框 (生平档案、国籍、单列操作栏)
│   │   ├── StudioDetailModal.vue        # 片商档案模态框 (官网直跳、网格/列表双模、16:9剧照)
│   │   ├── ShareCardModal.vue           # 分享卡片模态框 (流光双封面、TG二维码、去日期)
│   │   └── AppUpdateModal.vue           # 版本更新检测弹窗 (更新日志展示、一键下载)
│   ├── i18n/
│   │   └── index.ts                     # 7 国语言全量字典 (931 个键)
│   └── services/
│       ├── appUpdater.ts                # GitHub Releases API 检索与在线升级
│       ├── privacy.ts                   # 全局防窥模式与快捷键
│       └── scraper.ts                   # 刮削状态机与任务栏进度驱动
└── src-tauri/
    ├── Cargo.toml                       # Rust 依赖声明与 Workspace 定义
    ├── tauri.conf.json                  # Tauri 窗口属性、安全策略与 NSIS 打包配置
    ├── src/
    │   ├── main.rs                      # Tauri 桌面端二进制主入口
    │   ├── lib.rs                       # 插件加载、自定义协议注册、WebView2 参数注入
    │   └── commands/
    │       ├── cache.rs                 # 数据库 WebP 离线引擎与图片协议处理
    │       ├── database.rs              # 数据库挂载、新建、备份与环境扫描
    │       ├── environment.rs           # 运行环境探测
    │       ├── sync.rs                  # Python 刮削调度、路径规范化 (normalize_path)
    │       ├── system.rs                # Windows 材质切换、系统托盘、任务栏进度指示
    │       ├── translate.rs             # 纯 Rust 进程内大模型 AI 翻译引擎
    │       └── user.rs                  # 通用配置与数据备份导入导出
    └── gpdb-core/                       # 独立数据库查询引擎 Workspace Crate
        ├── Cargo.toml                   # gpdb-core 依赖清单
        └── src/
            ├── lib.rs                   # 数据库连接池与初始化
            ├── db.rs                    # 数据库路径定位与连接管理
            ├── migrate.rs               # SQLite 数据表结构自愈与迁移
            ├── models.rs                # 跨端数据模型实体
            └── queries/                 # 影片、分集、演员、厂牌高性能查询
```

---

## 5. 构建、发布与验证指南

在接手进行任何代码提交前，必须依次通过以下 3 项校验：

### 5.1 国际化对齐校验
```bash
python3 Windows_client/scripts/test_parity.py
```
*预期指标：7 门语言（zh-CN, zh-TW, en, ja, it, es, de）各 931 键 100% 对齐，零偏差。*

### 5.2 前端静态类型检查与生产构建
```bash
npm --prefix Windows_client run build
```
*预期指标：`vue-tsc -b` 0 报错，Vite 成功生成 `dist/` 生产产物。*

### 5.3 后端 Rust 单元测试
```bash
cargo test --manifest-path Windows_client/src-tauri/Cargo.toml
```
*预期指标：26 个单元测试全部通过（0 failed）。*

### 5.4 Windows 本地打包指令 (PowerShell)
```powershell
# 在 Windows 环境下运行：
.\build_windows.ps1
# 或通过 npm 打包 NSIS 安装程序：
npm run tauri:build
```
*构建产物：位于 `src-tauri/target/release/bundle/nsis/GPDb_*_x64-setup.exe`。*

---

## 6. 片商超清 Logo & 封套徽章资产系统 (Studio Logos Engine)

Windows 客户端通过 `gpdb-img://` 协议与本地图片缓存紧密协同：
- **离线 Logo 路径**：直接访问 `image_cache/Logos/*_logo.webp` 与 `image_cache/Logos/*_banner.webp`。
- **全量 100% WebP 规范**：全量 2,492 枚独立厂牌 Logo 与 694 枚 Banner 已 100% 升级为高保真 `.webp` 格式，WebView2 现代内核无损急速渲染。
- **1:1 方块自适应**：全量封套包装切片与品牌标准字徽标统一生成 512×512 正方形版本，杜绝 Windows UI 头像框内的边缘裁切与黑边留白。
- **100% 全覆盖现状**：全库 2,492 家有效独立制片厂牌已实现 100% 专属 Logo 覆盖，全库 97.99% 的影视作品拥有所属厂牌 Logo。
- **管理与生成工程**：收录于专用子目录 `studio_logos/`，详见 [`studio_logos/README.md`](../studio_logos/README.md) 与 [`STUDIO_LOGO_PROGRESS.md`](../STUDIO_LOGO_PROGRESS.md)。

---

## 7. Git 管理与自动化发版规范 (Git Tasks & Release Workflow)

项目已建立专用的 Git 管理与发版自动化工作区，脚本与规范收拢于 `git_tasks/`：
- **工程主路径**：`/Users/joel/iCloud Drive (Archive)/Documents/antigravity/GPDb 开发`。
- **发版前自动预检**：在根目录下执行 `./git_tasks/pre_release_check.sh`，核验路径合规性、零路径泄露、无 iOS 描述残留及各端版本号严格对齐。
- **发布流程参考**：详细发版流程与 CI/CD 自动化构建机制请参阅 [`git_tasks/README.md`](../git_tasks/README.md)。
