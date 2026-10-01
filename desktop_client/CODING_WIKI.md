# GPDb macOS 桌面客户端开发规范与技术架构 Wiki (Coding Wiki)

---

## 1. 客户端功能定位与设计目标

GPDb macOS 桌面客户端是基于 **Tauri v2 + Rust + Vue 3** 打造的旗舰级离线影库系统。
其设计目标是：**“极低内存占用、原生级响应速度、深度融合 macOS 人机交互指南 (HIG) 的现代化单机影视元数据库”**。

### 1.1 核心业务功能矩阵

| 功能模块 | 功能特征与技术实现 | macOS 桌面端专属优化 |
| :--- | :--- | :--- |
| **全库检索与多维筛选** | 63,000+ 部影片、100,000+ 分集、6,000+ 演员、1,300+ 厂牌 | Rust 原生 SQLite WAL 高性能并发查询、预编译 SQL 缓存、毫秒级即时聚合响应 |
| **沉浸式主页 (Home Feed)** | 焦点海报轮播、AI 导赏、今日星光、经典系列大放送、随心探索盲盒 | 今日星光仅展示有头像的演员、经典系列每次刷新采用随机抽取（10组）、全景流光光晕 |
| **离线媒体与海报系统** | `image_cache/` 物理目录直接映射、自适应多图封面拼图 | 自研 `gpdb-img://` 自定义流式协议，零内存拷贝直接读取磁盘海报文件 |
| **影视/演员/厂牌/系列模态框** | 3D 拟真翻转实体卡片、全屏手势缩放灯箱 (Lightbox)、曾用艺名 (AKA) 折叠卡片 | 统一层叠上下文管理 (`zIndex`)、全局 Esc 键捕获堆栈 (`claimEscape`)、平滑硬件加速动画 |
| **全平台通用数据备份与迁移** | 用户收藏夹、播放探索统计、自定义标签、打标与私密笔记 | `gpdb_universal_backup` 跨平台统一 JSON 规范，与 Windows / Android / iOS 四端互通 |
| **流光双封面分享卡片** | 正向封面与反向封底双海报并排排版、Telegram 官方频道点阵二维码 | Canvas 2D Retina 2x 高清渲染、双离屏磨砂打码防窥、27×27 矢量点阵二维码直达官方频道 |
| **刮削器集成与自动入库** | PBC 与 SmutJunkies 演员刮削引擎 | 异步子进程调度、实时日志管道推流捕获、执行完成自动触发 `--apply` 无损增量入库 |
| **版本检测与在线更新** | GitHub Releases API 检索、版本特性展示、安装包自动下载 | 异步检测最新版本，支持自动下载 DMG 安装包并挂载升级 |

---

## 2. macOS 桌面端技术栈全景

```
[ UI 渲染层 (Frontend - WebView) ]
  Vue 3.5+ (Composition API, <script setup>) + TypeScript 6.0+
  构建工具: Vite 8+ + @tailwindcss/vite 4.3+
  图标体系: Lucide Icons (@lucide/vue / lucide-vue-next)
  交互机制: 模态框统一栈管理器、Esc 键优先级分配、全景高斯模糊滤镜

[ 桥接与命令层 (Tauri Bridge Layer) ]
  Tauri v2.11+ (基于 macOS WebKit / Wry 宿主)
  自定义安全流式协议: gpdb-img:// (拦截解析本地 image_cache/ 资源)
  Tauri IPC Commands: 数据库挂载、文件操作、系统剪贴板、配置备份导入导出、Python 刮削调度

[ 后端核心与数据库引擎 (Rust Core Layer) ]
  gpdb-core (独立 Workspace Crate，零 Tauri 耦合，便于跨平台复用与单元测试)
  数据驱动: rusqlite 0.31 (内嵌 SQLite 3.45+, 启用 WAL 模式, 只读并发连接池)
  系统底层: Cocoa / Objective-C 运行时桥接 (安全剪贴板图像复制，消除内存死锁)
```

---

## 3. 核心子系统与关键设计规范

### 3.1 内存安全与 macOS Cocoa 运行时规范

在 Tauri 命令层与 macOS 原生系统交互（如系统剪贴板图像复制）时，必须严格遵守 Objective-C 引用计数与 ARC 生命周期规范。

#### 严禁使用的反模式 (Double-Free & 主线程崩溃)
- **绝对严禁对 Autoreleased 对象显式调用 `release`**：
  在 Objective-C 中，通过便捷工厂方法（如 `+[NSData dataWithBytes:length:]` 与 `+[NSString stringWithUTF8String:]`）创建的对象已经被加入当期的自动释放池（AutoreleasePool）。
  如果在 Rust FFI 中手动对其调用 `msg_send![ns_data, release]`，会导致在自动释放池排空时触发 **Double-Free（双重释放）**，瞬间引发 macOS 主线程死锁或 EXC_BAD_ACCESS 闪退崩溃！

#### 正确的规范实现 (`src-tauri/src/commands/system.rs`)
```rust
// 仅对通过 alloc + init 创建的持有所有权对象调用 release，便捷工厂对象绝不手动 release
let alloc: id = msg_send![class!(NSPasteboardItem), alloc];
let item: id = msg_send![alloc, init];
let () = msg_send![item, setData:ns_data forType:ns_type];
let () = msg_send![pasteboard, writeObjects:items_array];
let () = msg_send![item, release]; // 仅 release 自己 alloc 的 item
```

### 3.2 自定义图片协议 `gpdb-img://` 规范

为了突破浏览器 WebView 的本地文件跨域限制并实现极致的图片加载性能：
- 在 `src-tauri/src/lib.rs` 中注册 Tauri Custom Protocol `gpdb-img`；
- 前端传递相对路径（如 `gpdb-img://image_cache/Covers/123.jpg`）；
- Rust 协议处理器提取 URL Path，与当前挂载的数据库物理根目录安全拼接；
- 采用直接文件流读取并输出对应的 MIME 类型（`image/jpeg`, `image/webp`, `image/png`），无中间 Base64 编解码开销，显著降低海量海报网格的内存占用。

### 3.3 数据库查询引擎与多维聚合 (`gpdb-core`)

- **独立 Workspace 组织**：`gpdb-core` 作为独立的 Rust Crate 置于 `src-tauri/gpdb-core`，不直接依赖 Tauri 运行时，保证纯粹的数据处理与极速编译。
- **WAL 并发模式**：以 `PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL;` 打开连接，读写分离互不阻塞。
- **随机抽样推荐**：主页「经典系列大放送」采用 `ORDER BY RANDOM() LIMIT 10`，每次刷新均生成全新推荐组合。
- **星光面孔过滤**：主页「今日星光 · 标志面孔」过滤无头像占位项，仅展示包含有效本地缓存或有效外链头像的演员。

### 3.4 跨平台通用配置备份与迁移规范 (`gpdb_universal_backup`)

- **规范标识**: `"format": "gpdb_universal_backup", "version": 1`
- **支持范围**:
  - 用户收藏夹（长片、分集、演员、导演、系列、厂牌全维度支持）；
  - 探索专注分析数据（累计专注时长、播放量统计、活跃天数等）；
  - 自定义标签与私密评星笔记；
  - 界面排版与视觉偏好配置。
- **四端通用**: 导出的 `.json` 配置文件完全跨平台，支持在 macOS、Windows、Android 和 iOS 客户端之间直接互相导入。

### 3.5 分享卡片流光引擎与 Telegram 官方二维码

- **双封面并排排布**: 分享卡片全面支持正向海报封面与反面封底（如有）并排展示，左右标注「封面」「封底」半透明胶囊。
- **Retina 2x 离屏抗锯齿**: 采用双离屏 Canvas 技术，在 HiDPI 屏幕下始终保持高清晰度导出。
- **双离屏高保真磨砂模糊**: 解决 macOS WebKit 下 Canvas filter 硬件加速可能失效的偶发缺陷，确保打码隐私保护坚如磐石。
- **官方二维码点阵**: 卡片底角集成 27×27 标准高保真点阵二维码，直连官方频道 `https://t.me/gpdbnews`。

---

## 4. 目录组织架构 (Source Tree)

```
desktop_client/
├── package.json                         # 前端依赖配置 (Vue 3, Vite, Tailwind CSS v4)
├── vite.config.ts                       # Vite 构建与开发服务器配置
├── src/
│   ├── main.ts                          # 前端入口，挂载 App.vue
│   ├── App.vue                          # 顶层布局框架、侧边栏集成、全局弹窗挂载
│   ├── api.ts                           # Tauri invoke 远程过程调用包装层
│   ├── types.ts                         # 全量 TypeScript 元数据与业务数据实体接口
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
    ├── tauri.conf.json                  # Tauri 窗口属性、安全策略与打包配置
    ├── src/
    │   ├── main.rs                      # Tauri 桌面端二进制主入口
    │   ├── lib.rs                       # 插件加载、自定义协议注册、Tauri 命令集挂载
    │   └── commands/
    │       ├── system.rs                # macOS 系统级调用 (安全剪贴板、Finder、废纸篓)
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

```bash
# 切换至桌面端工程目录
cd desktop_client

# 启动本地热重载开发环境 (Vite + Tauri)
npm run tauri dev
```

### 5.2 生产环境打包指令

```bash
# 执行前端静态检查与打包
npm run build

# 编译并生成 macOS 原生可执行文件与 .dmg 安装包
npm run tauri build
```

### 5.3 产物输出与规范

- 安装包文件：`desktop_client/src-tauri/target/release/bundle/dmg/GPDb_*.dmg`
- 顶层发布规范：根目录下生成的对应版本客户端安装包（如 `GPDb_macOS_v2.15.0.dmg`）
- 产物完全遵循 macOS 沙盒与代码规范，内存与 CPU 负载表现平稳。
