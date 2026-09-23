# GPDb 发版与 Git 管理保证准则 (Git Workflow & Release Guidelines)

> 本文档规范了 GPDb 项目的所有 Git 提交、版本管理、多语言矩阵同步与 Releases 安装包发布标准。任何自动化脚本与 AI 协作 Agent 均须严格遵循。

---

## 📌 核心准则清单

### 1. 规范化提交信息 (Commit Conventions)
所有 Git 提交必须使用标准的 Conventional Commits 格式：
- `feat:` 新功能（如客户端交互、新刮削源、空白库初始化、组件扩展等）
- `fix:` 问题修复（如图片 404 补丁、海报穿透、链接修正、安全漏洞修复等）
- `docs:` 文档更新（如多语言 README、CHANGELOG、架构说明等）
- `refactor:` 代码重构（如 Rust 查询层纯化、模块解耦、界面扁平化等）
- `chore:` 辅助配置（如版本号对齐、构建脚本配置、GitHub Actions 调整等）

---

### 2. 📢 显要位置常驻 Telegram 频道链接（强制项）
* **规则**：每次修改或更新 README，必须保证在主文档及全语种 README 顶部的**显要位置**（页头 Badge 徽章区与导航栏）包含 Telegram 官方频道链接：
  * **频道链接**：[https://t.me/gpdbnews](https://t.me/gpdbnews)
  * **Badge 徽章规范**：
    ```html
    <a href="https://t.me/gpdbnews" target="_blank">
      <img src="https://img.shields.io/badge/Telegram-Channel%20%40gpdbnews-2CA5E0?style=flat-square&logo=telegram&logoColor=white" alt="Telegram Channel" />
    </a>
    ```

---

### 3. README 多语言矩阵全自动同步
* 主文档 `README.md`（简体中文）修改后，必须同步将改动高质量地更新至其余 6 种官方语言文档：
  - `README_EN.md` (English)
  - `README_ZH_TW.md` (繁體中文)
  - `README_JA.md` (日本語)
  - `README_DE.md` (Deutsch)
  - `README_ES.md` (Español)
  - `README_IT.md` (Italiano)
* **垂直领域专业术语地道统一**：
  - 体型/身段 $\rightarrow$ `Build` / `体型・スタイル`
  - 生理特征与尺寸 $\rightarrow$ `Foreskin (Cut/Uncut)` / `Dick Size`
  - 正片与分集/客串 $\rightarrow$ `Films / Movies` vs `Scenes / Individual Appearances`

---

### 4. 发布（Release）防空头与 100% 安装包直达保证
* **绝对禁止“空头 Release”**：任何 Releases 页面绝不能出现仅包含 `Source code (zip/tar.gz)` 的情况。
* **物理附件必须挂载**：每个 Release 必须成功绑定对应版本的物理安装包（如 `GPDb-macOS-vX.Y.Z.dmg`）。
* **双轨发版保障机制**：
  1. **云端 CI/CD 自动化**：通过 `.github/workflows/release.yml` 监听 `push: tags: ['v*']` 触发 macOS Runner 打包与发布；
  2. **本地直传兜底**：若 Actions 编译被中断或网络异常，必须由本地编译成功后通过 GitHub API/CLI 直传挂载 `.dmg` 附件，确保 Release 页面时刻提供安装包。

---

### 5. 全生态版本号严格对齐 (Version Synchronization)
发版前必须统一校验并升级以下 4 处配置文件中的版本号：
1. `desktop_client/package.json` $\rightarrow$ `"version": "X.Y.Z"`
2. `desktop_client/src-tauri/tauri.conf.json` $\rightarrow$ `"version": "X.Y.Z"`
3. `desktop_client/src-tauri/Cargo.toml` $\rightarrow$ `version = "X.Y.Z"`
4. `desktop_client/src-tauri/gpdb-core/Cargo.toml` $\rightarrow$ `version = "X.Y.Z"`

---

### 6. 零密钥泄露原则 (Zero Secret Leakage)
* 任何 Telegram Bot Token、API Key、密码或私钥**绝对禁止**写入源码提交中。
* 敏感凭据统一通过环境变量（如 `TG_BOT_TOKEN`）、命令行参数或 Git 忽略配置文件读取。

---

### 7. 🚫 私人/推送脚本黑名单防提交机制
* 绝对禁止在 Git 仓库中追踪或提交以下脚本与辅助文件：
  - `scripts/PUSH_GUIDELINES.md`
  - `scripts/send_db_to_tg.py`
  - `scripts/tg_changelog_pusher.py`
  - `scripts/.changelog_pusher_state.json`
* 上述规则已硬编码写入根目录 [.gitignore](file:///Users/joel/iCloud%20Drive%20%28Archive%29/Documents/antigravity/%E6%B8%B8%E6%88%8F%E5%BA%93%E7%AE%A1%E7%90%86App/GEVI_Offline_Database/.gitignore)，在以后的任意代码提交和版本发布操作中，不得将其添加进版本控制。
