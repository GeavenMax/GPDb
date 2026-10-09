# GPDb Windows 客户端 (Windows Client)

> GPDb 官方离线影视库桌面端 —— Windows 10/11 专属客户端工程。  
> 基于 **Tauri 2 + Vue 3 + Rust + Tailwind CSS v4** 构建，针对 Windows 平台进行了系统级深度调优、Fluent Design 视觉融合与静默调度适配。

---

## 🌟 Windows 专属特性与技术亮点

1. **Windows 11 Fluent Design (Mica / Acrylic) 原生材质引擎**：
   - 深度集成 Tauri v2 原生窗口材质效果（`Effect::Mica` / `Effect::Tabbed` / `Effect::Acrylic`），窗口通透底色与 Windows 桌面壁纸自然交融；
   - 界面内置 4 档材质方案自由切换（经典纯色 / Mica 云母 / Mica Alt 深邃 / Acrylic 亚克力），偏好实时持久化；
   - 针对浅色主题（Classic Light、Glass Light、My Light）与深色主题做变量隔离，确保所有光效下字迹锐利高对比度。

2. **系统托盘驻留 (System Tray) 与 Jump List 快捷导航**：
   - 原生任务栏通知区域图标，右键提供「显示主界面」「今日探索」「我的收藏」「截屏防窥模式」「最小化到托盘」「退出 GPDb」快捷菜单；
   - 左键单击极速切换窗口显示/隐藏，置顶唤醒；
   - 支持「关闭窗口时最小化到系统托盘」，开启后点 X 按钮静默驻留后台；
   - 托盘直连「防窥盾」状态机，一键对海报与文本施加高斯模糊。

3. **任务栏实时进度指示 (Taskbar Progress Indicator)**：
   - 封装原生 `window.set_progress_bar(ProgressBarState)`；
   - 在执行全库增量网络刮削与元数据解析时，Windows 任务栏图标实时呈现绿色进度条与跑马灯状态，进度一目了然。

4. **纯 Rust 进程内大模型 AI 翻译引擎**：
   - 彻底摆脱外部 Python 服务依赖，在客户端内核中实现纯 Rust TLS 翻译引擎；
   - 原生支持 Google Gemini、OpenAI 兼容协议（DeepSeek、Moonshot、智谱、通义千问、本地 Ollama）与 Anthropic Claude；
   - 单条翻译自动容纳纯中文正文，智能清理多余标点与序号；自适应规范化补齐端点 `/v1/chat/completions`，双向漫游配置。

5. **片商 Logo & 横幅数据库内置离线引擎 (`studio_logos`)**：
   - 采用安全的虚拟协议拦截点（`http://gpdb-img.localhost` 与 `gpdb-img://`）；
   - 优先直接从 SQLite `studio_logos` 表读取内嵌的 WebP 压缩图，新用户在**零外部图片包**情况下亦可 100% 离线秒级加载高清厂牌标志与横幅。

6. **子进程防闪烁与控制台 UTF-8 流式管道**：
   - 所有的后台同步、抓取爬虫脚本调用与网络探测均注入 `CREATE_NO_WINDOW (0x08000000)`，彻底消除黑色 CMD 弹框打断交互的问题；
   - 自动剥除 Windows `canonicalize()` 产生的 `\\?\` UNC 长路径前缀（`normalize_path`），防止 Python 子进程报语法错误；
   - 自动注入 `PYTHONIOENCODING=utf-8` 与 `PYTHONUTF8=1`，配合字节流分块解析，防止 Windows GBK 控制台乱码。

7. **Windows 11 原生贴靠与视觉排版 (Snap Layouts)**：
   - 保留系统原生窗口装饰 (`decorations: true`)，支持 Windows 11 最大化悬停分屏贴靠（Snap Layouts）；
   - 全局引入 `"Microsoft YaHei UI"` 与 `"Microsoft YaHei"` CJK 平滑字体渲染，启用现代化细滚动条（`scrollbar-width: thin`）。

8. **单用户轻量化 NSIS 安装程序**：
   - 默认采用 `installMode: "currentUser"`，安装无需 UAC 管理员提权，随装随用；
   - 内置高质量多分辨率矢量转换图标 (`icons/icon.ico`)。

---

## 🛠️ 本地开发与环境准备

在 Windows 环境下开发或构建客户端，需具备以下环境：

1. **Node.js**：v18.0 或更高版本（推荐 v20 / v22 LTS）。
2. **Rust & Cargo**：1.77.2+（推荐通过 [rustup.rs](https://rustup.rs/) 安装，选择 `x86_64-pc-windows-msvc` 工具链与 Visual Studio C++ 生成工具）。
3. **WebView2 Runtime**：Windows 10/11 通常已内置。

### 常用开发指令

```powershell
# 1. 切换至客户端目录
cd Windows_client

# 2. 启动前端 + Rust 本地热重载开发模式
npm run tauri:dev

# 3. 执行严格类型检查与前端生产构建
npm run build

# 4. 执行后端 Rust 单元测试 (26 项全部通过)
cargo test --manifest-path src-tauri/Cargo.toml

# 5. 执行 7 国语言多语言镜像校验 (931 键完全对齐)
python3 scripts/test_parity.py

# 6. 一键打包 Windows NSIS 安装程序 (.exe)
npm run tauri:build
# 或直接运行 PowerShell 脚本：
.\build_windows.ps1
```

构建生成的 `.exe` 安装包位于：  
`src-tauri/target/release/bundle/nsis/GPDb_*_x64-setup.exe`

---

## 📁 目录结构

```
Windows_client/
├── build_windows.bat       # Windows 批处理一键构建脚本
├── build_windows.ps1       # Windows PowerShell 一键构建脚本
├── package.json            # 前端工程配置 (Vue 3.5, Vite 8, Tailwind CSS v4)
├── vite.config.ts          # Vite 打包配置
├── CODING_WIKI.md          # 架构 Wiki 与关键设计规范
├── HANDOVER.md             # 交接说明与核心铁律
├── scripts/
│   └── test_parity.py      # 国际化 7 语种 100% 对齐校验工具
├── src/                    # Vue 3 界面源码（已适配 Windows 字体与 Fluent 材质）
│   ├── App.vue             # 顶层布局、系统托盘事件绑定、Mica 材质控制
│   ├── api.ts              # Tauri IPC 强类型封装
│   ├── theme.css           # Fluent Design 半透明与明暗主题自适应 CSS
│   ├── views/              # 主页、专注洞察、插件管理
│   ├── components/         # 影片/分集/演员/片商模态弹窗、分享卡片、更新弹窗
│   └── i18n/               # 7 国语言全量字典 (931 个键)
└── src-tauri/              # Rust Tauri 2 后端工程
    ├── tauri.conf.json     # Windows 专属 Tauri 配置（NSIS、图标、材质透明度）
    ├── Cargo.toml          # Rust 依赖清单
    ├── src/
    │   ├── main.rs         # 二进制主入口
    │   ├── lib.rs          # WebView2 参数调优、协议注册、托盘设置
    │   └── commands/       # cache/database/sync/system/translate 命令集
    └── gpdb-core/          # GPDb 核心 SQLite 查询层 (与 macOS 100% 镜像)
```
