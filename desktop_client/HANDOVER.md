# GPDb macOS 桌面客户端交接与运维指南 (Handover Document)

---

## 1. 系统概览与交接背景

- **软件名称**：GPDb macOS Desktop Client
- **当前版本**：v2.18.0
- **架构范式**：基于 **Tauri v2 + Rust Core + Vue 3.5 + TypeScript + Tailwind CSS v4**。
- **定位**：运行于 Apple macOS 原生环境下的离线影视元数据管理与深度档案检索客户端，完全解耦于外部 Web 服务，支持单机纯离线运行。
- **代码仓库当前主路径**：`/Users/joel/iCloud Drive (Archive)/Documents/antigravity/GPDb 开发/desktop_client`

---

## 2. 开发环境准备与前置条件

接手本项目前，请确保本地开发机满足以下环境依赖：

| 工具 / 运行时 | 建议版本 | 校验命令 | 备注 |
| :--- | :--- | :--- | :--- |
| **macOS** | 12.0+ (Monterey 及以上) | `sw_vers` | 适配 Apple Silicon (M1/M2/M3/M4) 与 Intel |
| **Xcode Command Line Tools** | 最新 | `xcode-select -p` | 必须具备编译原生 C/Objective-C/Rust 依赖能力 |
| **Node.js** | v20.x 或更高 LTS | `node -v` | 前端依赖管理与打包 |
| **Rust & Cargo** | 1.75.0 或更高稳定版 | `rustc --version` | 推荐通过 `rustup default stable` 管理 |
| **Tauri CLI** | v2.11.x+ | `npx tauri --version` | 通过项目局部 `devDependencies` 引入 |

---

## 3. 本地启动、调试与热重载

```bash
# 1. 切换至桌面端主目录
cd "desktop_client"

# 2. 安装前端所有依赖包
npm install

# 3. 启动开发模式 (同时拉起 Vite 开发服务器与 Tauri 原生应用窗口)
npm run tauri dev
```

> **调试技巧**：
> - 可以在运行中的桌面客户端窗口内按下 `Cmd + Option + I` 打开 macOS WebKit 原生开发者控制台，检查 Vue 视图层渲染与网络/IPC 调用日志。
> - Rust 后端控制台输出（`println!`, `tracing`）直接打在启动该命令的终端会话中。

---

## 4. 生产环境打包、发布与分发

```bash
# 1. 前端类型严格检查与生产打包 (vue-tsc + vite build)
npm run build

# 2. 编译生产二进制并生成 macOS DMG 镜像
npm run tauri build
```

### 产物输出与安装：
- **DMG 镜像**：`desktop_client/src-tauri/target/release/bundle/dmg/GPDb_2.18.0_aarch64.dmg`（适用于 Apple Silicon）。
- **原生 App**：`desktop_client/src-tauri/target/release/bundle/macos/GPDb.app`。
- **安装至本机**：双击生成的 `.dmg` 镜像，拖拽 `GPDb.app` 至 `/Applications`（应用程序目录）即可。

---

## 5. 核心子系统运转机理与维护重点

### 5.1 数据库挂载与寻址机制 (`src-tauri/src/db.rs`)
- 客户端在启动和查询时，会自动向上级递归查找 `GPDb.db`。
- 当前标准数据结构下，`GPDb.db` 与 `translations.db` 位于项目根目录：
  `/Users/joel/iCloud Drive (Archive)/Documents/antigravity/GPDb 开发/GPDb.db`
- 连接采用 SQLite WAL 模式，确保多线程读取无锁并发。

### 5.2 自定义流式图片协议 (`gpdb-img://`)
- **协议入口**：`desktop_client/src-tauri/src/commands/cache.rs` 中的 `handle_image_protocol`。
- **厂牌 Logo 读取优先级**：
  1. 优先调用 `load_logo_from_db` 查询 `GPDb.db` 中的 `studio_logos` 表（内嵌 WebP 字节流）。
  2. 若未命中数据库，则降级检索磁盘 `image_cache/` 物理目录中的本地文件。
- **维护注意**：如果海报或厂牌图加载不出，首先检查数据库路径以及 `image_cache/` 目录的软链接或相对路径映射。

### 5.3 全景洞察与厂牌谱系拓扑图 (`StudioGenealogyView.vue`)
- 基于 **ECharts 6.1+** 力导向图渲染：
  - 关系拓扑数据由前端结构化组织，支持拖拽力学仿真。
  - 右侧 Wiki 栏支持根据选中集团/厂牌动态提取历史、总部与名下作品。
- **维护注意**：窗口缩放或暗黑/明亮主题切换时，须确保调用 `chartInstance.resize()`。

### 5.4 大模型 AI 翻译引擎与提示词维护 (`pluginManager.ts`)
- 统一提示词常量：`DEFAULT_TRANSLATION_PROMPT`。
- **专职译者 5 条军规**：逐句对应、直白露骨、不意译不润色、人名保留拉丁字母写法、客观档案记录语气。
- **本地存储升级机制**：在 `getPluginsConfig()` 加载时，对 `localStorage` 内的遗留默认提示词进行自动平滑迁移升级。
- **常见报错处理**：
  - 若调用 Gemini 时提示 `429 RESOURCE_EXHAUSTED`，系 Google 免费层（Free Tier）每日配额（通常为 500 次）耗尽或瞬时 QPS 超限，需要指导用户更换 API Key、等待配额重置或切换至 Paid Tier / 其他厂商。

### 5.5 全局多语言 i18n 规范 (`src/i18n/`)
- 维护所有 7 种语言的 1:1 镜像对齐。
- 新增或调整 UI 文案时，必须同步在所有 7 种语言分片中增加对应 Key，严禁在模板中硬编码中文兜底文案。
- 详细规范请参考 `docs/UI_I18N_SPEC.md`。

---

## 6. 常见故障排查 (Troubleshooting)

### Q1: `npm run build` 报错类型不匹配？
- **原因**：Vue 单文件组件或 TypeScript 接口更新后未声明完整类型。
- **排查**：单独运行 `npx vue-tsc -b` 查看精准报错行号与类型断言。

### Q2: 启动时界面卡在加载或无法获取电影数据？
- **原因**：未找到 `GPDb.db` 数据库。
- **排查**：
  1. 检查根目录下是否存在 `GPDb.db`。
  2. 检查 `src-tauri/src/db.rs` 中的路径搜寻列表是否覆盖当前工作目录。

### Q3: 复制图片到剪贴板偶发闪退？
- **原因**：Objective-C ARC 内存释放问题。
- **排查**：查阅 `src-tauri/src/commands/system.rs`，确保未对 Autoreleased 便捷工厂对象手动调用 `msg_send![..., release]`。

---

## 7. 负责人与交接确认

- **维护团队**：GPDb 核心工程组
- **代码规范文档**：`desktop_client/CODING_WIKI.md`
- **界面多语言规范**：`desktop_client/docs/UI_I18N_SPEC.md`
