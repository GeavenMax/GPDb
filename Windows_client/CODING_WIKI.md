# GPDb Windows 客户端开发规范与技术架构 Wiki (Coding Wiki)

---

## 1. 客户端功能定位与设计目标

GPDb Windows 客户端是基于 **Tauri v2 + Rust + Vue 3** 深度适配 Windows 10 与 11 操作系统的桌面端离线影库系统。
其定位为：**“极致轻量、超低内存开销、深度融合 Windows Fluent / Mica 设计美学、稳定驾驭多驱动器海量媒体的桌面掌上影库”**。

### 1.1 核心业务功能矩阵

| 功能模块 | 功能特征与技术实现 | Windows 桌面端专属优化 |
| :--- | :--- | :--- |
| **全库检索与多维筛选** | 63,000+ 部影片、100,000+ 分集、6,000+ 演员、1,300+ 厂牌 | Rust 原生 SQLite WAL 高性能并发查询、预编译 SQL 缓存、毫秒级响应、支持厂牌中英双向模糊检索 (`COLLATE NOCASE`) |
| **沉浸式主页 (Home Feed)** | 焦点海报轮播、今日星光、经典系列大放送、随心探索盲盒 | 今日星光仅展示拥有有效头像的演员、经典系列每次刷新采用随机抽取（10组）、流光背景 |
| **离线媒体与海报系统** | `image_cache/` 物理目录直接映射、自适应多图封面平铺 | 深度适配 WebView2 虚拟域端点 (`http://gpdb-img.localhost`)，纯 Rust TLS (`ureq` + `rustls`) 高速并发下载与防盗链伪装，零外部进程依赖 |
| **全站 7 语种 100% 镜像对齐** | 覆盖 zh-CN, zh-TW, en, ja, it, es, de 7 大语种，919 键绝对 1:1 对齐 | 界面文本与数据库实体完全解耦，支持 3 档内容呈现模式，严禁硬编码中文，配备 `test_parity.py` 自动化 CI 校验 |
| **厂牌历史档案与美学专栏** | 181 家主流与先锋厂牌深度百科专栏、三级容灾回退与主动补齐 | 宽幅通透排版，无截断舒展呈现厂牌沿革与美学风格，60 批次平滑虚拟滚动分集列表，杜绝万级厂牌卡死 |
| **影视/演员/分集模态档案** | 多海报并排水平平铺、曾用艺名 (AKA) 折叠卡片、分集简介行内自由展开 | 统一层叠上下文管理 (`zIndex`)、全局 Esc 键捕获堆栈 (`claimEscape`)、一键批量展开收起收录章节、直达分集独立档案 |
| **全平台通用数据备份与迁移** | 用户收藏夹、播放探索统计、自定义标签、打标与私密笔记 | `gpdb_universal_backup` 跨平台统一 JSON 规范，与 macOS / Android / iOS 四端互通 |
| **流光双封面分享卡片** | 正向封面与反向封底双海报并排排版、Telegram 官方频道点阵二维码 | Canvas 2D 高清渲染、双离屏磨砂打码防窥、27×27 矢量点阵二维码直达官方频道 |
| **刮削器集成与自动入库** | PBC 与 SmutJunkies 演员刮削引擎 | Windows 虚拟环境与 Python 解释器自动发现、控制台 UTF-8 编码防乱码、自动执行 `--apply` 无损入库 |
| **版本检测与在线更新** | GitHub Releases API 检索、版本特性展示、安装包自动下载 | 异步检测最新版本，匹配 Windows NSIS 安装包 (`.exe`) 引导用户一键升级 |

---

## 2. Windows 桌面端技术栈全景

```
[ UI 渲染层 (Frontend - WebView2) ]
  Vue 3.5+ (Composition API, <script setup>) + TypeScript 6.0+
  构建工具: Vite 8+ + @tailwindcss/vite 4.3+
  图标体系: Lucide Icons (@lucide/vue / lucide-vue-next)
  窗口框架: 深度适配 Windows 窗口控制、无边框阴影、拖拽区域 (data-tauri-drag-region)

[ 桥接与命令层 (Tauri Bridge Layer) ]
  Tauri v2.11+ (基于 Microsoft Edge WebView2 宿主)
  自定义安全流式协议: http://gpdb-img.localhost (WebView2 虚拟域拦截，内置纯 Rust TLS 边看边下与本地物理路径映射)
  Tauri IPC Commands: 数据库挂载、Explorer 定位、系统剪贴板 (arboard)、Python 刮削调度、数据备份

[ 后端核心与数据库引擎 (Rust Core Layer) ]
  gpdb-core (独立 Workspace Crate，零 Tauri 耦合，便于多平台共用核心算法)
  数据驱动: rusqlite 0.31 (内嵌 SQLite 3.45+, 启用 WAL 模式, 只读并发连接池)
  系统底层: Win32 API / arboard (安全处理 DIB / PNG 格式系统剪贴板，杜绝 STA 线程锁死)
```

---

## 3. 核心子系统与关键设计规范

### 3.1 Windows 路径标准化与文件系统规范

Windows 环境在处理路径时存在反斜杠 `\`、驱动器盘符（如 `D:\`）、长路径前缀（`\\?\`）以及 UNC 共享网络路径等特殊形态。

#### 路径安全规范
1. **统一路径规整化 (Path Normalization)**：
   在 Rust 后端接收前端传入的路径或在 `gpdb-img://` 协议解析时，统一采用 `std::path::Path::new` 与标准 `canonicalize` 处理，杜绝因正反斜杠混用导致的 `NotFound` 错误。
2. **长路径与多分区适配**：
   媒体资源库 `image_cache` 可能存放于外置移动硬盘或非系统分区（如 `E:\GPDb_Images`），Rust 文件流必须直接基于绝对物理路径映射，严禁假设资源在应用根目录。

### 3.2 Windows 剪贴板图像处理与线程模型

在 Windows 上将生成的高清分享卡片复制到系统剪贴板时，必须注意：
- Windows 剪贴板采用 OLE / COM 架构，在部分场景下要求单一线程单元 (STA) 模型；
- 通过集成跨平台 `arboard` 库直接将 RGBA / PNG 位图注入系统剪贴板（CF_DIB / CF_DIBV5 / PNG 格式），避免手动调用 Win32 COM API 产生死锁或剪贴板锁占用超时。

### 3.3 Python 解释器发现与控制台编码 (Scraper Integration)

在 Windows 上执行 `scrape_pbc_actors.py` 与 `scrape_smutjunkies_actors.py` 刮削脚本时：
1. **解释器发现优先级**：
   - 优先检测当前目录或父目录的虚拟环境：`.\venv\Scripts\python.exe`；
   - 其次检测系统 PATH 中的 `python.exe` / `py.exe`。
2. **控制台输出与编码防乱码**：
   Windows 默认控制台代码页多为 GBK (CP936)。Rust 在拉起子进程时必须注入环境变量 `PYTHONIOENCODING=utf-8` 与 `PYTHONUTF8=1`，确保日志管道以标准的 UTF-8 字节流回传给前端，杜绝中文字符乱码崩溃。
3. **入库自动化**：
   刮削完毕后，Tauri 后端自动触发 `--apply` 参数将提取的 PBC / SmutJunkies 元数据直接同步至当前连接的 SQLite 数据库中。

### 3.4 跨平台通用配置备份与迁移规范 (`gpdb_universal_backup`)

- **规范标识**: `"format": "gpdb_universal_backup", "version": 1`
- **支持范围**:
  - 用户收藏夹（长片、分集、演员、导演、系列、厂牌全维度支持）；
  - 探索专注分析数据（累计专注时长、播放量统计、活跃天数等）；
  - 自定义标签与私密评星笔记；
  - 界面排版与视觉偏好配置。
- **四端通用**: 导出的 `.json` 配置文件完全跨平台，支持在 Windows、macOS、Android 和 iOS 客户端之间直接互相导入。

### 3.5 分享卡片流光引擎与 Telegram 官方二维码

- **双封面并排排布**: 分享卡片全面支持正向海报封面与反面封底（如有）并排展示，左右标注「封面」「封底」半透明胶囊。
- **Canvas 多级降采样模糊**: 针对 Windows WebView2 硬件加速特性进行优化，防止离屏渲染打码时出现锯齿或失真。
- **官方二维码点阵**: 卡片底角集成 27×27 标准高保真点阵二维码，直连官方频道 `https://t.me/gpdbnews`。

---

## 4. 目录组织架构 (Source Tree)

```
Windows_client/
├── package.json                         # 前端依赖配置 (Vue 3, Vite, Tailwind CSS v4)
├── vite.config.ts                       # Vite 构建与开发服务器配置
├── src/
│   ├── main.ts                          # 前端入口，挂载 App.vue
│   ├── App.vue                          # 顶层布局框架、Windows 窗口拖拽区域、侧边栏
│   ├── api.ts                           # Tauri invoke 远程过程调用封装
│   ├── types.ts                         # 全量 TypeScript 元数据与业务数据实体定义
│   ├── views/
│   │   ├── HomeView.vue                 # 沉浸式主页 (轮播、星光、随机大放送、探索)
│   │   ├── AnalyticsView.vue            # 用户本地专注与偏好洞察统计
│   │   └── PluginsView.vue              # 插件管理与刮削器配置面板
│   ├── components/
│   │   ├── MovieDetailModal.vue         # 影片详情模态框 (3D翻转、海报画廊、演职员表)
│   │   ├── PerformerDetailModal.vue     # 演员档案模态框 (AKA艺名折叠、生理特征、外链)
│   │   ├── ShareCardModal.vue           # 分享卡片模态框 (双封面、流光背景、TG二维码)
│   │   ├── AppUpdateModal.vue           # 版本更新检测弹窗 (更新日志展示、一键下载)
│   │   └── Sidebar.vue                  # 质感侧边导航栏
│   └── services/
│       ├── appUpdater.ts                # GitHub Releases API 检索与在线升级
│       ├── pluginManager.ts             # 刮削器执行调度与参数映射
│       ├── privacy.ts                   # 全局防窥模式与快捷键
│       └── userAnalytics.ts             # 专注与探索统计数据收集
└── src-tauri/
    ├── Cargo.toml                       # Rust 依赖声明与 Workspace 定义
    ├── tauri.conf.json                  # Tauri 窗口属性、安全策略与 NSIS 打包配置
    ├── src/
    │   ├── main.rs                      # Tauri 桌面端二进制主入口
    │   ├── lib.rs                       # 插件加载、自定义协议注册、Tauri 命令集挂载
    │   └── commands/
    │       ├── system.rs                # Windows 系统级调用 (Explorer、剪贴板、回收站)
    │       └── sync.rs                  # 通用配置与数据备份导入导出
    └── gpdb-core/                       # 核心 SQL 查询引擎 Workspace Crate
        ├── Cargo.toml
        └── src/
            ├── lib.rs                   # 数据库连接池与初始化
            └── queries/                 # 影片、演员、分集、系列、厂牌高性能查询
```

---

## 5. 构建、发布与验证指南

### 5.1 本地开发与调试指令

```powershell
# 切换至 Windows 端工程目录
cd Windows_client

# 启动本地热重载开发环境 (Vite + Tauri)
npm run tauri dev
```

### 5.2 生产环境打包指令

```powershell
# 执行前端静态类型校验与编译
npm run build

# 编译并生成 Windows 原生安装程序 (.exe / NSIS)
npm run tauri build
```

### 5.3 产物输出与规范

- 安装包文件：`Windows_client/src-tauri/target/release/bundle/nsis/GPDb_*_x64-setup.exe`
- 顶层发布规范：根目录下生成的对应版本客户端安装包（如 `GPDb_Windows_v2.16.6_x64_setup.exe`）
- 安装向导支持自定义安装路径、创建桌面快捷方式与开始菜单磁贴，卸载流程干净彻底无残留。
