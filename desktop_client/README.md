# GPDb macOS 桌面客户端 (Desktop Client)

> 基于 **Tauri v2 + Rust + Vue 3 + TypeScript + Tailwind CSS** 架构打造的现代化离线影视资料库客户端。

---

## 1. 软件定位与核心特性

GPDb macOS 客户端专为管理和浏览离线影视元数据打造，支持 63,000+ 部长片、100,000+ 幕分集、6,000+ 位演职员与 1,300+ 家制作厂牌。兼顾原生 macOS 的优雅视效体验与单机纯离线环境下的极速响应。

- **极速离线检索与多维筛选**：基于 Rust `rusqlite` WAL 并发只读连接池，毫秒级即时聚合响应，支持年代、厂牌、演员、系列、标签等全维度交叉组合筛选。
- **全景洞察与谱系拓扑 (Panorama & Genealogy)**：
  - **行业全景编年史** (`IndustryPanoramaView`)：涵盖行业历史分期、里程碑事件、时代技术演进与代表作。
  - **厂牌谱系图谱与 Wiki** (`StudioGenealogyView`)：集成 ECharts 6 力导向拓扑关系图与右侧详情 Wiki，展示集团派系归属、母子厂牌脉络与历史变迁。
  - **个人足迹与偏好洞察** (`AnalyticsView`)：本地专注时长、评分分布、喜好雷达与观影足迹，隐私 100% 留存于本机。
- **沉浸式主页 (Home Feed)**：焦点轮播海报、AI 深度导赏、今日星光标志面孔、经典系列大放送（随机抽取推荐）与盲盒探索。
- **自研 `gpdb-img://` 离线流式协议**：
  - 拦截本地 `image_cache/` 与 SQLite `studio_logos` 表（WebP 二进制），实现零内存拷贝直接流式渲染，打破 WebView 安全域限制。
- **全局一键双语切换**：顶部导航栏随时切换中英双语，全库档案（包括演员生平、分集简介、厂牌背景）与 UI 无缝联动。
- **大模型 AI 翻译引擎**：
  - 内置 Gemini、OpenAI、Claude、DeepSeek 接入支持；
  - 严格内置“成人影片专职译者 5 条军规”专业系统提示词（逐句对应、直白露骨、不意译不润色、人名保留拉丁字母、客观档案语气）。
- **流光双封面卡片分享**：支持正反面双海报排版、双离屏高保真磨砂隐私打码与 Telegram 官方频道点阵二维码。
- **多端通用配置备份**：`gpdb_universal_backup` 跨平台统一规范，与 Windows、Android、iOS 四端无缝互通。

---

## 2. 系统环境与前置依赖

- **操作系统**：macOS 12.0 (Monterey) 或更高版本（原生适配 Apple Silicon M 系列与 Intel 芯片架构）
- **Node.js**：`v20.x` 或更高版本（推荐使用 LTS）
- **Rust 工具链**：`rustc` / `cargo` 1.75+（推荐通过 `rustup` 安装至最新稳定版）
- **构建工具**：
  - macOS Command Line Tools (`xcode-select --install`)
  - 包管理器：`npm` / `pnpm`

---

## 3. 快速上手与本地开发

进入桌面端目录执行依赖安装与开发调试：

```bash
# 1. 进入客户端根目录
cd "desktop_client"

# 2. 安装前端依赖
npm install

# 3. 启动本地开发环境 (自动启动 Vite 前端热重载 + Tauri 宿主窗口)
npm run tauri dev
```

> **注意**：
> 客户端首次启动将自动检测当前目录及上级目录中的主数据库 `GPDb.db`。若尚未生成，可通过根目录下的数据构建脚本完成初始化。

---

## 4. 生产环境构建与发布

```bash
# 1. 运行前端 TypeScript 类型检查与 Vite 生产打包
npm run build

# 2. 编译 Rust 核心并生成 macOS 原生可执行文件与 .dmg 安装包
npm run tauri build
```

**产物输出路径**：
- 安装包：`desktop_client/src-tauri/target/release/bundle/dmg/GPDb_2.18.0_aarch64.dmg`（根据 CPU 架构决定）
- macOS App Bundle：`desktop_client/src-tauri/target/release/bundle/macos/GPDb.app`

---

## 5. 项目工程结构

```
desktop_client/
├── package.json                   # 前端配置 (Vue 3.5, Vite 8, Tailwind CSS v4, ECharts 6)
├── vite.config.ts                 # Vite 配置文件
├── src/                           # 前端源码
│   ├── main.ts                    # 应用入口
│   ├── App.vue                    # 根组件 (布局框架、路由切换、全局模态框挂载)
│   ├── api.ts                     # Tauri IPC 命令调用包装层
│   ├── types.ts                   # 全局 TypeScript 接口定义
│   ├── i18n/                      # 7 语言本地化词典 (100% 键位镜像对齐)
│   ├── views/                     # 核心视图页面
│   │   ├── HomeView.vue           # 沉浸式主页
│   │   ├── StudioGenealogyView.vue# 厂牌谱系 Wiki 与 ECharts 力导向拓扑图
│   │   ├── IndustryPanoramaView.vue# 行业全景编年史
│   │   ├── AnalyticsView.vue      # 专注足迹与统计分析
│   │   └── PluginsView.vue        # 插件系统与 AI 翻译配置
│   ├── components/                # 业务组件
│   │   ├── Navbar.vue             # 顶部全局导航栏 (全局搜索、双语切换、同步状态)
│   │   ├── Sidebar.vue            # 侧边栏导航
│   │   ├── MovieDetailModal.vue   # 影片详情弹窗 (3D 翻转、剧照画廊、分集)
│   │   ├── PerformerDetailModal.vue# 演员档案弹窗 (中英双语、生平经历、曾用艺名)
│   │   ├── StudioDetailModal.vue  # 厂牌详情弹窗 (母公司集团、历史作品)
│   │   └── ShareCardModal.vue     # 流光双封面分享卡片生成器
│   └── services/                  # 业务逻辑服务
│       ├── pluginManager.ts       # AI 翻译大模型配置与专职译者提示词
│       ├── privacy.ts             # 截图隐私防窥模式
│       └── scraper.ts             # 增量爬虫与离线数据流处理
└── src-tauri/                     # Tauri 后端源码 (Rust)
    ├── Cargo.toml                 # Cargo 依赖配置与 Workspace 定义
    ├── tauri.conf.json            # Tauri 应用配置 (窗口尺寸、协议、打包资源)
    ├── src/
    │   ├── main.rs                # 二进制启动入口
    │   ├── lib.rs                 # 插件管理、gpdb-img 协议注册与命令集分发
    │   ├── db.rs                  # 数据库连接寻址与初始化
    │   └── commands/              # IPC 命令层 (cache, library, detail, translate 等)
    └── gpdb-core/                 # 核心 SQL 查询引擎 (零 Tauri 依赖 Crate)
```

---

## 6. 相关技术文档

- **技术架构与开发规范**：[CODING_WIKI.md](file:///Users/joel/iCloud%20Drive%20%28Archive%29/Documents/antigravity/GPDb%20开发/desktop_client/CODING_WIKI.md)
- **交接与运维维护指南**：[HANDOVER.md](file:///Users/joel/iCloud%20Drive%20%28Archive%29/Documents/antigravity/GPDb%20开发/desktop_client/HANDOVER.md)
- **界面多语言与本地化规范**：[docs/UI_I18N_SPEC.md](file:///Users/joel/iCloud%20Drive%20%28Archive%29/Documents/antigravity/GPDb%20开发/desktop_client/docs/UI_I18N_SPEC.md)
