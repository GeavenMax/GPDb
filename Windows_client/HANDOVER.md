# GPDb Windows 客户端 — 项目交接说明与工程文档 (HANDOVER.md)

> **文档状态**：已根据 `v2.17.0` 最新架构与发布状态全量更新。  
> **面向对象**：后续接手维护、开发新功能或修复 Bug 的工程师或 AI Assistant。**先完整通读本文档，再动手修改代码。**  
> **当前版本**：`v2.17.0`（2026-10-03，与 macOS/桌面端基线 100% 对齐）。  
> **代码健康度**：全端通过 `vue-tsc -b` 严格类型检查、`vite build` 生产构建、`Windows_client/scripts/test_parity.py` 7 语种 930 键 100% 校验、`cargo test` 后端单元测试（25/25 测例全部通过）。

---

## 目录
- [1. 项目定位与架构全景](#1-项目定位与架构全景)
- [2. 关键设计原则与核心铁律 (CRITICAL RULES)](#2-关键设计原则与核心铁律-critical-rules)
- [3. Windows 平台专属系统级特性与实现机制](#3-windows-平台专属系统级特性与实现机制)
  - [3.1 Windows 11 Fluent Design（Mica / Acrylic）原生材质引擎](#31-windows-11-fluent-designmica--acrylic原生材质引擎)
  - [3.2 系统托盘 (System Tray) 与 Jump List 快捷导航](#32-系统托盘-system-tray-与-jump-list-快捷导航)
  - [3.3 任务栏实时进度指示 (Taskbar Progress Indicator)](#33-任务栏实时进度指示-taskbar-progress-indicator)
  - [3.4 纯 Rust 进程内大模型 AI 翻译与端点自适应](#34-纯-rust-进程内大模型-ai-翻译与端点自适应)
  - [3.5 WebView2 高刷新率 (120Hz/144Hz) 与 GPU 硬件渲染](#35-webview2-高刷新率-120hz144hz-与-gpu-硬件渲染)
  - [3.6 纯 Rust TLS 离线图片协议 (http://gpdb-img.localhost)](#36-纯-rust-tls-离线图片协议-httpgpdb-imglocalhost)
  - [3.7 Python 刮削器静默调度与无黑框控制台抑制](#37-python-刮削器静默调度与无黑框控制台抑制)
- [4. 代码目录与模块分工](#4-代码目录与模块分工)
- [5. 7 国语言国际化 (i18n) 对齐规范](#5-7-国语言国际化-i18n-对齐规范)
- [6. 本地开发、测试与验证工作流](#6-本地开发测试与验证工作流)
- [7. 已知注意事项与排障指南 (Troubleshooting)](#7-已知注意事项与排障指南-troubleshooting)

---

## 1. 项目定位与架构全景

GPDb Windows 客户端是基于 **Tauri v2 + Rust + Vue 3 (Composition API) + Tailwind CSS v4** 构建的 Windows 10/11 平台原生桌面应用。

### 技术栈全景
```
[ UI 渲染层 (Frontend - WebView2) ]
  Vue 3.5+ (<script setup lang="ts">) + TypeScript 5.8+
  构建套件: Vite 8.3+ + @tailwindcss/vite 4.3+
  图标套件: @lucide/vue / lucide-vue-next
  国际化: 自研轻量级响应式 i18n 系统 (7 国语言, 930 个键)
  通信: @tauri-apps/api/core (invoke) + @tauri-apps/api/event (listen/emit)

[ 桥接与命令层 (Tauri Bridge Layer - src-tauri) ]
  Tauri v2.11+
  系统托盘: tauri::tray::{TrayIconBuilder, TrayIconEvent}
  窗口特效: tauri::window::{EffectsBuilder, Effect}
  任务栏指示: window.set_progress_bar (ProgressBarState)
  安全虚拟协议: http://gpdb-img.localhost (原生流拦截与边看边下)

[ 后端内核与数据库引擎 (Rust Core Layer) ]
  独立 Crate: gpdb-core (零 Tauri 耦合，便于跨端测试与重用)
  数据库驱动: rusqlite 0.31 (嵌入式 SQLite 3.45+, 启用 WAL 模式, 并发连接池)
  网络层: ureq 2.10 (启用 rustls, 零 OpenSSL 依赖)
  剪贴板引擎: arboard (安全 Win32 CF_DIB / PNG 处理)
```

---

## 2. 关键设计原则与核心铁律 (CRITICAL RULES)

接手者请务必牢记以下几点，任何违反都可能导致发布失败或严重 Bug：

1. **绝对禁止硬编码任何个人 API Key**：
   - 绝不允许在代码库中写入真实的 OpenAI、Gemini、DeepSeek 等大模型 API Key；
   - 翻译设置中的 Key 仅存放在本地配置文件或环境变量中，测试时使用打码虚拟占位符（如 `sk-test...`）。
2. **Windows_client 与 desktop_client 必须保持 1:1 双端同步**：
   - 本项目与 `desktop_client`（macOS 客户端）在业务逻辑、i18n 词条、组件能力上保持完全镜像；
   - 对 `Windows_client/src/` 或 `Windows_client/src-tauri/` 做出改动时，应评估并同步至 `desktop_client/`，且版本号必须严格一致（当前均为 `v2.17.0`）。
3. **i18n 7 语种 100% 镜像绝对对齐**：
   - 字典文件位于 `src/i18n/index.ts`，涵盖 `zh-CN`, `zh-TW`, `en`, `ja`, `it`, `es`, `de`；
   - 增删任何翻译键后，**必须**运行 `python3 Windows_client/scripts/test_parity.py`，确保 7 大语种的键集合完全一致，零缺失零多余。
4. **Vue 严格模式禁止无效引用与死代码**：
   - 项目开启了 `vue-tsc -b` 严格类型检查；任何在 `<script setup>` 中 import 但模板未使用的变量/图标（如 `Sparkles`、`trPerfTag` 等）都会触发 `TS6133` 导致 `npm run build` 失败。
5. **Windows 路径与反斜杠处理**：
   - Windows 上的文件路径包含盘符与反斜杠（如 `C:\Users\...`）。在 Rust 侧处理路径时必须使用 `std::path::Path` / `PathBuf`，严禁直接通过硬编码 `/` 做字符串切分。

---

## 3. Windows 平台专属系统级特性与实现机制

### 3.1 Windows 11 Fluent Design（Mica / Acrylic）原生材质引擎
- **实现位置**：
  - 后端：`Windows_client/src-tauri/src/commands/system.rs` (`set_window_material`) 与 `Windows_client/src-tauri/src/lib.rs`；
  - 前端：`Windows_client/src/App.vue`、`Windows_client/src/theme.css`、`Windows_client/src/utils/prefs.ts`。
- **机制与实现细节**：
  - Tauri v2 中设置材质必须使用 `tauri::window::{Effect, EffectsBuilder}`：
    ```rust
    let builder = match material {
        "mica" => EffectsBuilder::new().effect(Effect::Mica),
        "tabbed" => EffectsBuilder::new().effect(Effect::Tabbed),
        "acrylic" => EffectsBuilder::new().effect(Effect::Acrylic),
        _ => EffectsBuilder::new(),
    };
    let _ = window.set_effects(builder.build());
    ```
  - `tauri.conf.json` 中配置了 `"transparent": true`，且窗口配置包含了 `windowEffects` 字段；
  - 前端在 `html` 根节点动态注入 `data-window-material="mica|tabbed|acrylic|default"` 属性；在 `theme.css` 中为对应材质匹配半透明底色（`oklch(... / 0.75~0.78)`），使桌面壁纸自然渗透，浮动卡片层次分明。

### 3.2 系统托盘 (System Tray) 与 Jump List 快捷导航
- **实现位置**：
  - `Windows_client/src-tauri/src/commands/system.rs` (`setup_tray`)；
  - `Windows_client/src-tauri/src/lib.rs`；
  - `Windows_client/src/App.vue` (`listen('navigate-tab')`)。
- **机制与功能**：
  - **常驻托盘菜单**：包含「显示 GPDb 主界面」「今日探索 (Jump List)」「我的收藏 (Jump List)」「截屏防窥模式」「最小化到托盘」「退出 GPDb」；
  - **左键单击**：即时唤醒主窗口（自动取消最小化、置顶聚焦），若已聚焦则隐藏至后台；
  - **Jump List 直达**：点击「今日探索」或「我的收藏」通过 `app.emit("navigate-tab", "home" | "favorites")` 广播事件，前端无刷新平滑切换；
  - **防窥模式联动**：托盘点击「截屏防窥模式」广播 `toggle-privacy-mode` 事件，前端 `privacy.ts` 联动高斯模糊海报与文字；
  - **优雅关闭拦截**：支持「关闭窗口时最小化到系统托盘」（原子状态 `CLOSE_TO_TRAY` 存储）。用户在外观设置开启后，点击窗口右上方关闭按钮 (X) 不会杀进程，而是静默隐藏到托盘。

### 3.3 任务栏实时进度指示 (Taskbar Progress Indicator)
- **实现位置**：
  - 后端：`Windows_client/src-tauri/src/commands/system.rs` (`set_taskbar_progress`)；
  - 前端：`Windows_client/src/api.ts` 与 `Windows_client/src/services/scraper.ts`。
- **机制**：
  - 封装 Tauri v2 原生 `window.set_progress_bar(ProgressBarState)`，支持 `None`、`Normal`（绿色精确进度条）、`Indeterminate`（跑马灯等待态）；
  - 刮削启动时自动置为 `Indeterminate`；收到 `scraper-progress` 事件时按百分比更新为 `Normal`；完成或取消时自动置为 `None` 复位。

### 3.4 纯 Rust 进程内大模型 AI 翻译与端点自适应
- **实现位置**：
  - `Windows_client/src-tauri/src/commands/translate.rs`；
  - 单元测试：`Windows_client/src-tauri/tests/translation_commands.rs`。
- **解决的核心痛点（v2.16.7 - v2.17.0）**：
  - 用户自行配置第三方 API Key（DeepSeek、Moonshot、通义千问、Claude、本地 Ollama 等）时，模型单条翻译通常直接返回纯文本译文；此前后端因强制解析 JSON 结构导致报错；
  - 现在：`extract_translations` 针对单条翻译自动容纳纯中文正文，智能清理多余标点与前缀序号；
  - 针对 OpenAI 兼容网关移除单条请求中的 `"response_format": {"type": "json_object"}`，彻底避免 HTTP 400 报错；
  - API 端点 URL 智能自适应规整化，自动补全 `/v1/chat/completions` 或 `/chat/completions`；
  - 配置文件 `translate_config.json` 采用数据库所在目录与 `%APPDATA%\com.gpdb.app` 双向漫游与合并加载。

### 3.5 WebView2 高刷新率 (120Hz/144Hz) 与 GPU 硬件渲染
- **实现位置**：`Windows_client/src-tauri/src/lib.rs` 的 `tauri::Builder::default().setup(...)`。
- **注入参数**：
  ```
  --enable-features=msWebView2EnableDraggableRegions
  --disable-features=CalculateNativeWinOcclusion
  --high-dpi-support=1
  --enable-gpu-rasterization
  --enable-zero-copy
  ```
  彻底解决多显示器与高刷屏滚动卡顿问题。

### 3.6 纯 Rust TLS 离线图片协议 (http://gpdb-img.localhost)
- **实现位置**：`Windows_client/src-tauri/src/commands/cache.rs`。
- **机制**：
  - 统一通过 `http://gpdb-img.localhost/?path=...` 拦截请求；
  - 本地缓存存在时直接从磁盘流式读取；
  - 缓存缺失时使用 `ureq` + `rustls` 自动从远端抓取并写盘，无需外部进程。

### 3.7 Python 刮削器静默调度与无黑框控制台抑制
- **实现位置**：`Windows_client/src-tauri/src/commands/sync.rs`。
- **机制**：
  - Windows 执行子进程拉起 Python 脚本时，注入 `CREATE_NO_WINDOW (0x08000000)`，防止黑色 CMD 弹框打扰用户；
  - 自动注入 `PYTHONIOENCODING=utf-8` 与 `PYTHONUTF8=1`，防止 Windows GBK 控制台乱码；
  - 自动扫描多路径 Python 解释器（虚拟环境 `venv`、`LOCALAPPDATA`、`ProgramFiles` 及系统 PATH）。

---

## 4. 代码目录与模块分工

```
Windows_client/
├── CHANGELOG.md                         # 严格遵循 Keep a Changelog 规范的更新日志
├── CODING_WIKI.md                       # 开发规范与技术架构 Wiki
├── README.md                            # 快速上手与工程简介
├── package.json                         # 当前版本 2.17.0
├── vite.config.ts                       # Vite 8 配置
├── scripts/
│   └── test_parity.py                   # 7 语种 i18n 100% 校验脚本
├── src/
│   ├── App.vue                          # 主界面、路由标签页切换、外观材质设置、全局监听
│   ├── api.ts                           # 前端 Tauri IPC 封装层 (TypeScript)
│   ├── theme.css                        # Fluent 材质半透明样式规则
│   ├── components/                      # 模态弹窗与展示组件
│   │   ├── MovieDetailModal.vue         # 影片档案弹窗与 AI 翻译
│   │   ├── EpisodeDetailModal.vue       # 分集档案弹窗与 AI 翻译
│   │   ├── PerformerDetailModal.vue     # 演员档案弹窗
│   │   ├── StudioDetailModal.vue        # 厂牌档案与历史专栏
│   │   └── ...
│   ├── i18n/
│   │   └── index.ts                     # 7 国语言全量字典
│   ├── services/
│   │   ├── appUpdater.ts                # GitHub Release 更新检查
│   │   ├── privacy.ts                   # 截屏防窥模式状态机
│   │   └── scraper.ts                   # 刮削状态机与任务栏进度驱动
│   └── utils/
│       └── prefs.ts                     # 用户偏好持久化（材质、托盘等）
└── src-tauri/
    ├── Cargo.toml                       # gpdb 2.17.0
    ├── tauri.conf.json                  # Windows 客户端 Tauri 配置
    ├── gpdb-core/                       # 数据库查询引擎（独立 Crate）
    │   ├── Cargo.toml                   # gpdb-core 2.17.0
    │   └── src/
    │       ├── lib.rs
    │       ├── db.rs
    │       └── migrate.rs               # SQLite 数据表结构与迁移
    └── src/
        ├── lib.rs                       # Tauri 应用构建、WebView2 参数注入、事件绑定
        ├── main.rs                      # 入口
        └── commands/
            ├── cache.rs                 # 图片协议与缓存
            ├── database.rs              # 数据库操作
            ├── environment.rs           # 运行环境探测
            ├── sync.rs                  # Python 刮削器调度 (CREATE_NO_WINDOW)
            ├── system.rs                # 材质切换、系统托盘、任务栏进度、资源管理器定位
            ├── translate.rs             # 纯 Rust TLS 多服务商大模型翻译引擎
            └── user.rs                  # 通用配置导出/导入
```

---

## 5. 7 国语言国际化 (i18n) 对齐规范

1. **语言矩阵**：
   - 简体中文 (`zh-CN`)
   - 繁体中文 (`zh-TW`)
   - 英语 (`en`)
   - 日语 (`ja`)
   - 意大利语 (`it`)
   - 西班牙语 (`es`)
   - 德语 (`de`)
2. **校验铁律**：
   任何时候在 `src/i18n/index.ts` 中增删配置项，都必须在根目录下运行以下命令校验：
   ```bash
   python3 Windows_client/scripts/test_parity.py
   ```
   **合格输出指标**：所有 7 门语言显示相同的键总数（当前为 930 个键）且无缺失报错。

---

## 6. 本地开发、测试与验证工作流

在接手进行任何代码提交前，**必须依次通过以下 4 项校验**：

### 1. 国际化对齐校验
```bash
python3 Windows_client/scripts/test_parity.py
```
*预期结果：7 门语言 930 键 100% 对齐。*

### 2. 后端 Rust 单元测试
```bash
cargo test --manifest-path Windows_client/src-tauri/Cargo.toml
```
*预期结果：25 个测试全部通过（0 failed）。*

### 3. 前端编译与严格类型校验
```bash
npm --prefix Windows_client run build
```
*预期结果：`vue-tsc -b` 0 报错，Vite 成功生成 `dist/` 产物。*

### 4. 镜像桌面端（desktop_client）同步测试
```bash
cargo test --manifest-path desktop_client/src-tauri/Cargo.toml
npm --prefix desktop_client run build
```
*预期结果：双端代码完全对称，全部构建通过。*

---

## 7. 已知注意事项与排障指南 (Troubleshooting)

| 症状 / 报错 | 根本原因 | 解决方案 |
| :--- | :--- | :--- |
| `npm run build` 报错 `TS6133: 'xxx' is declared but its value is never read.` | Vue 模板中未使用的组件/变量被引入，触发了严格 TypeScript 检查。 | 移除未引用的 import 或变量声明。切勿在 `tsconfig` 中关闭严格检查。 |
| 窗口背景无法透出壁纸（Mica/Acrylic 不生效） | `tauri.conf.json` 中 `transparent` 未开启，或 CSS 未正确配置透明背景。 | 检查 `tauri.conf.json` 的 `"transparent": true`，确认 `theme.css` 中注入了对应的半透明 CSS 规则。 |
| 用户自填大模型 API Key 翻译报错 `未在模型返回中找到有效 JSON` | 模型直接返回了翻译纯文本，后端旧逻辑尝试当成 JSON 强行反序列化失败。 | 确认 `commands/translate.rs` 的 `extract_translations` 逻辑保持为单条翻译容纳纯文本模式。 |
| 用户自填第三方代理 API Key 报 HTTP 400 Bad Request | 第三方模型不支持 `response_format: {type: "json_object"}` 参数。 | 确认单条翻译已禁用该参数，多条翻译若遇到 400 会自动剔除该参数进行二次重试。 |
| 运行 `cargo test` 提示找不到数据库相关测试 | 单元测试采用只读临时连接或内存库，请勿指向正在被其他进程写锁定的真实库。 | 测试逻辑已内置在各模块的 `tests` 模块中，直接运行 `cargo test` 即可。 |
