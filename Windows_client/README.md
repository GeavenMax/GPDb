# GPDb Windows 客户端 (Windows Client)

GPDb 官方离线影视库桌面端 —— Windows 专属客户端工程。基于 Tauri 2 + Vue 3 + Rust 构建，针对 Windows 10/11 平台进行了专属系统级深度调优与静默运行适配。

---

## 🌟 Windows 专属特性与优化

1. **子进程防闪烁与控制台隐藏 (Console Suppression)**：
   - 所有的后台同步、抓取爬虫脚本调用、环境检测及网络探测任务均注入 Windows `CREATE_NO_WINDOW (0x08000000)` 标志位。
   - 彻底告别后台任务执行时黑框 CMD 窗口瞬时弹出打断用户交互的问题。

2. **多源 Python 解释器智能探测链**：
   - 优先遵循 `GPDB_PYTHON` 环境变量；
   - 自动扫描 `%LOCALAPPDATA%\Programs\Python\Python3xx\python.exe` 独立用户目录；
   - 自动扫描 `%ProgramFiles%\Python3xx\python.exe` 全局目录；
   - 自动适配 Anaconda 与 Miniconda 发行环境；
   - 自动降级至系统 PATH (`python`, `python3`, `py`)，无需繁琐的手动配置。

3. **Windows 11 原生贴靠与视觉调优 (Snap Layouts & Look & Feel)**：
   - 保留系统原生窗口装饰 (`decorations: true`)，完全支持 Windows 11 最大化悬停分屏贴靠（Snap Layouts）；
   - 引入 `"Microsoft YaHei UI"` 与 `"Microsoft YaHei"` CJK 字体平滑渲染，开启 `-webkit-font-smoothing: antialiased`；
   - 引入现代化细滚动条（`scrollbar-width: thin`），避免系统粗滚动条侵占内容宽度。

4. **单用户轻量化 NSIS 安装包**：
   - 默认采用 `installMode: "currentUser"`，安装无需 UAC 管理员提权，随装随用；
   - 内置高质量多分辨率矢量转换图标 (`icons/icon.ico`)。

5. **纯 Rust TLS 离线图片高速直传与自动缓存**：
   - 适配 WebView2 虚拟域安全规范端点 (`http://gpdb-img.localhost`)；
   - 内置纯 Rust TLS 高性能客户端 (`ureq` + `rustls` + `webpki-roots`)，彻底摒弃并发外部 `curl.exe` 子进程，连接池复用与防盗链伪装，首次在线直读并持久化，后续 100% 毫秒级离线呈现。

---

## 🛠️ 本地开发与环境准备

在 Windows 环境下开发或构建客户端，需具备以下环境：

1. **Node.js**：v18.0 或更高版本（推荐 v20 / v22 LTS）。
2. **Rust & Cargo**：1.77.2+（推荐通过 [rustup.rs](https://rustup.rs/) 安装，选择 `x86_64-pc-windows-msvc` 工具链与 Visual Studio C++ 生成工具）。
3. **WebView2 Runtime**：Windows 10/11 通常已内置。

### 常用开发命令

```powershell
# 1. 安装前端依赖
npm install

# 2. 启动开发模式（支持热重载）
npm run tauri:dev

# 3. 仅构建前端代码
npm run build

# 4. 一键打包 Windows NSIS 安装程序 (.exe)
npm run tauri:build
# 或在 Windows 终端中直接运行：
.\build_windows.ps1
```

构建生成的 `.exe` 安装程序位于：
`src-tauri/target/release/bundle/nsis/`

---

## 📁 目录结构

```
Windows_client/
├── build_windows.bat       # Windows 批处理一键构建脚本
├── build_windows.ps1       # Windows PowerShell 一键构建脚本
├── package.json            # 前端工程配置与 Windows 构建脚本
├── vite.config.ts          # Vite 打包配置
├── src/                    # Vue 3 界面源码（已适配 Windows 字体与高斯毛玻璃）
├── src-tauri/              # Rust Tauri 2 后端工程
│   ├── tauri.conf.json     # Windows 专属 Tauri 配置（NSIS、图标、权限）
│   ├── Cargo.toml          # Rust 依赖清单
│   ├── gpdb-core/          # GPDb 核心 SQLite 查询层
│   └── src/
│       ├── commands/       # Windows 优化的原生命令（sync/env/translate 等）
│       └── db.rs           # 数据库定位与连接管理
```
