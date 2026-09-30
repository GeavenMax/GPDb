# GPDb · 极速隐私优先的男同成人影视数据库管理客户端

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>专为男同成人影视爱好者打造的高性能、全维度档案管理、绝对隐私与零云端追踪的现代化跨平台管理客户端</strong>
</p>

<p align="center">
  <a href="./README.md"><b>简体中文</b></a> |
  <a href="./README_EN.md">English</a> |
  <a href="./README_ZH_TW.md">繁體中文</a> |
  <a href="./README_JA.md">日本語</a> |
  <a href="./README_DE.md">Deutsch</a> |
  <a href="./README_ES.md">Español</a> |
  <a href="./README_IT.md">Italiano</a>
</p>

<p align="center">
  <a href="https://t.me/gpdbnews" target="_blank"><img src="https://img.shields.io/badge/Telegram-Channel%20%40gpdbnews-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Channel" /></a>
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Android-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Version-v2.14.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **官方 Telegram 频道**：欢迎订阅 [GPDb 官方 Telegram 频道 (https://t.me/gpdbnews)](https://t.me/gpdbnews)，第一时间获取最新的版本发布动态、数据库增量更新通知与使用技巧！

---

## 📖 项目定位与简介 (About GPDb)

**GPDb**（Gay Pornography Database Manager）是一款专门面向**男同成人影视（Gay Adult Media）**爱好者、数字媒体收藏家与影视资料研究者打造的**现代化全功能离线数据库管理客户端**。

在成人媒体领域，爱好者的个人观影记录、收藏列表与性向偏好属于**最核心、最敏感的个人隐私**。商业流媒体或中心化在线平台普遍存在账号追踪、数据分析泄露、版权到期下架等不可控风险。

**GPDb 坚持“100% 本地优先（Offline-First）与绝对私密无痕”的核心设计哲学**：
- **零云端依赖**：所有的媒体元数据、演职人员身体档案、海报/剧照缓存、个人打标评分及收藏足迹均完全留存在用户自己的设备物理磁盘中；
- **纯单机架构**：不设任何账号体系、无任何遥测追踪代码、无任何外部服务器上传；
- **极致性能保障**：基于高性能 **Rust 引擎 (`gpdb-core`)** 与 **Tauri v2 + Vue 3**（macOS / Windows 桌面端）及 **Kotlin + Jetpack Compose + Room**（Android 原生移动端），面对 **60,000+ 部完整影片、100,000+ 独立分集、6,000+ 演员身体档案与 1,300+ 经典与现代制片厂牌**，仍能实现 60fps 丝滑渲染与毫秒级即时检索。

---

## ✨ 紧扣领域特性的核心亮点 (Domain-Specific Features)

### 1. 全维度演员身体属性与特征深度检索
- **极细粒度的生理与体貌属性筛选**：
  支持按**体型身段（Build）**（如 Muscle / Twink / Bear / Hunk）、**发色（Hair）**、**瞳色（Eyes）**、**胡须与体毛丰度（Facial Hair / Body Hair）**、**身高体重（Height / Weight）**、**肤色（Skin）**、**生理特征尺寸（Dick Size / Foreskin）**、**纹身穿孔（Tattoos）** 等多维特征组合过滤。
- **艺名与曾用名全息对齐**：
  同一位演员在不同制片厂牌、不同年代的演出艺名或别名（Aliases）自动智能聚合，避免由于艺名变动导致漏看。
- **正片（Films）与分集（Scenes）精准区分**：
  支持单独查看演员作为正片主角的完整影视作品，亦可独立查看其作为单集客串的独立短剧，参演篇目一览无余。
- **高容错多字段联合搜索引擎**：
  - FTS5 高速召回 -> 异常自动降级至标准 SQL 多字段联合检索 -> Room / SQLite 兜底，杜绝因分词或缺少虚拟表造成的空结果。
  - 单次检索同时跨越英文原名 (`title`)、中文译名 (`title_zh`)、片商名 (`studio_name`)、导演名 (`director_name`)、中文简介 (`description_zh`) 以及出演演员阵容。
- **多维度时间与入库时效筛选**：
  - 支持「全部」、「上次入库（按刮削时间）」、「最近7天发行」、「最近30天发行」、「最近90天发行」及「本年度」。
  - 影片与分集发行时间粒度精确分离（长片按年份，分集按具体日期）。
  - 新入库条目展示动态渐变 `NEW` 高亮微标。

### 2. 流媒体级沉浸式视听主页与 3D 典藏卡片
- **五大发现推荐流**：
  - **焦点巨幕海报轮播 (Hero Carousel)**：顶端高清画质海报平滑自动轮播，带细腻视差交互。
  - **往年今日 · 经典首映 (On This Day)**：智能对照历史日历，回顾黄金时代（80年代、90年代、千禧年代）同日首映的经典作品。
  - **今日星光 · 标志面孔 (Star Spotlight)**：严格进行本地物理磁盘海报文件校验，智能推荐拥有高清头像的标志性面孔，杜绝字母占位符。
  - **经典系列大放送 (Iconic Series)**：自动打捞作品跨度达十余部曲的长篇标志性 IP 系列。
  - **随心探索 · 盲盒发现 (Lucky Discovery)**：一键摇骰，从六万余部片库中随机挖掘冷门宝藏。
- **海报双排版方案与全屏手势/滚轮缩放灯箱**：
  - 支持**自适应高清画廊 (`adaptive_pager`)** 与 **3D 拟真实体卡片 (`flip_3d`)**：具备 60fps CSS 3D 景深物理透视、实体翻转胶囊按键与典藏环境泛光。
  - **全屏缩放灯箱 (`ImageLightbox.vue` / Compose 缩放)**：支持鼠标滚轮平滑缩放 (1.0x ~ 5.0x)、按住拖拽平移、双击智能缩放/复位、键盘快捷键 (+/-/0/Esc) 与移动端双指捏合缩放。

### 3. 智能系列影片集与封面拼图 (Smart Series & Collage Covers)
- **自动聚类算法**：内置罗马数字与副标题识别技术，自动将散落的系列影片（如 *Part I, Part II, Part III*）聚合为连贯篇章。
- **自适应拼贴艺术封面**：为系列作品自动生成 1 张单幅海报、2 张对称拼图、3 张阶梯排布或 4 分格田字矩阵的艺术拼接封面，支持暗角光晕与离线极速渲染。
- **系列专属收藏**：支持将喜爱的经典系列一键加入“收藏系列”独立书签，随心追更。

### 4. 导演档案卡与厂牌流派图鉴
- **导演专属履历**：影片详情一键唤起执导导演专属卡片，展示其历史执导全量片单，并支持一键前往片库按导演筛选。
- **厂牌专属收录**：覆盖各大经典胶片制片厂牌（Falcon, Colt, Catalina 等）至现代主流厂牌（Men.com, BelAmi, Lucas Entertainment, Corbin Fisher 等），作品编年史与风格标签一览无余。

### 5. 📸 截屏防窥模式与高颜值流光分享卡片 (v2.13.0 全新升级)
- **全局一键截屏防窥模式**：
  - 顶部导航栏提供一键“截屏防窥”眼睛开关；支持快捷键与即时状态切换（防窥中 / 正常浏览）。
  - **细粒度隐私脱敏控制**：
    - `模糊海报与剧照图片`：全局高斯模糊 (`blur(24px)`) 所有影视封面、剧照、演职员头像等敏感视觉画面，杜绝截屏或社媒分享时泄露画面；
    - `模糊剧情介绍与敏感文字`：高斯模糊 (`blur(7px)`) 影视简介、分集梗概等敏感文字并禁止文本选中，防止剧透与涉密内容外泄。
  - Android 移动端基于 `LocalPrivacyBlur` 响应式状态流驱动，全屏秒级即时生效。
- **自适应海报流光分享卡片生成器**：
  - 影片与分集详情一键生成 Apple Music / Spotify 级流光分享卡片。
  - 背景光晕直接从封面自适应提取色相并实施高精度大半径高斯模糊 (`blur(45px~60px)`)，提供“流光 (Vibrant)”、“深黑 (Dark)”、“午夜 (Midnight)”三种专属光晕预设。
  - **分享前安全隐私脱敏**：独立勾选「模糊海报」与「模糊文字」，确保安全分享至社交群聊与公开网络。
  - **双端无损导出**：
    - **桌面端**：基于离线 HTML5 Canvas 2D 引擎以 2x Retina 超高清分辨率光栅化，一键复制 PNG 至系统剪贴板（微信/QQ/Telegram/Discord/X 直接粘贴）或导出保存本地。
    - **移动端**：采用 Compose 1.8 硬件加速位图捕获引擎，支持一键无损保存至系统相册 (`MediaStore`)，并通过 `FileProvider` 唤起 Android 原生分享面板。

### 6. 🛡️ 全维度隐私安防套件 (Enterprise-Grade Privacy & Security)
- **PIN 码数字锁屏与失焦防护**：
  - 支持设置独立 4~6 位数字 PIN 码应用锁，失焦或超时（1/5/15/30分钟）自动覆盖玻璃拟态锁屏界面 (`AppLockOverlay.vue`)。
  - 窗口失焦或切换其他应用时瞬间覆盖高斯毛玻璃防窥层。
- **紧急一键脱身伪装计算器 (Panic Switch)**：
  - 极速切换为 Apple 风格真实可用暗黑计算器 (`FakeCalculatorModal.vue`)，支持真实四则运算。
  - 桌面端支持快捷键 <kbd>Cmd + Shift + P</kbd> 极速呼出；移动端支持手势（屏幕翻转反扣）极速唤出。
  - 在计算器中输入正确 PIN 码并按 `=`，或连击顶部标题 4 次即可安全解锁重返影库。
- **无害化应用标题与桌面图标伪装**：
  - 支持自定义窗口标题（如 "Calculator", "Notes"），防止系统任务切换器暴露。
  - 内置多套高端设计主题图标（如「双雄火星图腾」、「黑曜石胶片之匣」）及无害伪装图标（便签、记账、计算器）。
- **移动端系统级物理绝缘**：
  - 接入 Android 系统级 `FLAG_SECURE` 防录屏、防截屏与防系统多任务卡片预览。
  - 物理沙盒存储隔离，目录递归注入 `.nomedia` 防护，彻底绝缘第三方相册扫描。

### 7. AI 影迷偏好洞察与大模型多语言翻译
- **AI 影迷偏好洞察与审美画像 (AI Persona Insights)**：
  - **全异步无感架构**：后台独立线程调度本地/远程大语言模型（如 DeepSeek、OpenAI、Claude 等），彻底消除界面假死。
  - **深度审美肖像长文**：基于用户的真实私密收藏与打标足迹，剖析其审美基因，生成包含“核心审美原型代号”（如“新浪潮怀旧探索者”）、“时代视听光谱”的 2000 字深度艺术鉴赏报告。
  - **实时进度弹窗**：高奢磨砂玻璃悬浮面板，分步展示大模型加密握手、足迹特征提取与画像生成进度。
- **大模型多语言翻译与跨平台数据沉淀**：
  - 原生接入 OpenAI、DeepSeek、Claude、Gemini、字节跳动豆包 (Doubao) 等各大服务商。
  - 支持“探测可用模型”一键在线检测 API 可用模型列表。
  - 支持翻译数据跨平台（macOS / Windows / Android）以标准 JSON 双向无缝导入导出，零损沉淀。

### 8. 资源检索与外部扩展插件系统 (v2.0)
- **多站精准直达**：在影片与演员页面，支持一键跳转 BoyfriendTV、Google 及各大权威影视数据库，使用条目原英文名精准跳转，免去二次打字。
- **BT 磁力搜索集成**：一键将电影原名与厂牌组合为标准搜索式，直通外部资源引擎。
- **独立自由开关**：插件中心支持针对各个外部跳查源进行独立细致的启闭配置。
- **增量刮削与同步引擎**：支持按需网络刮削更新与本地海报剧照离线缓存。

### 9. 全语言国际化与 77+ 游戏化成就奖杯系统
- **7 种全界面可选语言**：简体中文 (`zh-CN`)、繁体中文 (`zh-TW`)、English (`en`)、Italiano (`it`)、日本語 (`ja`)、Español (`es`)、Deutsch (`de`)。
- **PlayStation 风格成就奖杯系统**：
  - 内置 77+ 项探索、检索、专注与收藏成就，解锁时触发 PSN 风格流体动效弹窗。
  - 基于真实行为指标追踪，支持独立的奖杯数据重置与检定管理。

### 10. 全平台覆盖与三端数据协同 (macOS / Windows / Android)
- **macOS 原生桌面端 (`desktop_client/`)**：通过 Tauri v2 + Rust 架构完美支持 Apple Silicon 与 Intel 架构，原生玻璃拟态与键盘快捷键交互。
- **Windows 原生桌面端 (`Windows_client/`)**：专有独立目录解耦，内置多路径 Python 智能解析引擎、控制台黑框抑制 (`CREATE_NO_WINDOW`)、微软雅黑字体平滑优化、纤细滚动条、Win11 分屏贴靠与 NSIS 免提权单用户轻量化安装。
- **原生 Android 移动端 (`android_client/`)**：采用 Kotlin + Jetpack Compose + Room 现代化技术栈纯原生构建，与桌面端数据模式无缝融合，满足随身随时随地离线查阅与打标收藏的需求。

---

## 🛠️ 技术架构 (Technology Stack)

```
┌──────────────────────────────────────┐   ┌──────────────────────────────────────┐   ┌──────────────────────────────────────┐
│       GPDb macOS (Universal)         │   │       GPDb Windows (x64)             │   │       GPDb Android (Mobile)          │
│   Vue 3 + Tauri v2 + Rust (gpdb-core)│   │   Vue 3 + Tauri v2 + Rust (gpdb-core)│   │    Kotlin + Jetpack Compose + Room   │
│         (desktop_client/)            │   │         (Windows_client/)            │   │         (android_client/)            │
│         [GPDb-macOS-*.dmg]           │   │       [GPDb-Windows-*.exe]           │   │       [GPDb-Android-*-signed.apk]    │
└──────────────────┬───────────────────┘   └──────────────────┬───────────────────┘   └──────────────────┬───────────────────┘
                   │                                          │                                          │
                   └──────────────────────────────────────────┴──────────────────────────────────────────┘
                                                              │
                                   ┌──────────────────────────▼──────────────────────────┐
                                   │              底层持久化存储 (Storage)               │
                                   │      GPDb.db (SQLite 3 WAL) + image_cache/          │
                                   └─────────────────────────────────────────────────────┘
```

---

## 🚀 快速上手与下载 (Quick Start)

### 普通用户下载安装（推荐）

直接前往本仓库的 [Releases 页面](https://github.com/GeavenMax/GPDb/releases) 下载最新 **`v2.13.0`** 正式安装包：

| 平台 | 安装包文件名 | 安装方式与说明 |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.13.0.dmg` | 双击挂载后将 `GPDb.app` 拖入 `Applications`（应用程序）文件夹即可。<br>*(首次打开若提示未公证，可在「系统设置 → 隐私与安全性」点击「仍要打开」，或终端执行 `sudo xattr -cr /Applications/GPDb.app`)* |
| **🪟 Windows** | `GPDb-Windows-v2.13.0.exe` | 双击安装程序完成安装。采用 NSIS 单用户免提权架构，无需管理员权限，随装随用。 |
| **🤖 Android** | `GPDb-Android-v2.13.0-signed.apk` | 手机下载后直接点击安装（已通过官方公钥强签名，如系统提示允许未知来源安装，请予以允许）。 |

---

### 💻 开发者本地编译与运行指南 (Developer Guide)

#### 环境要求
- **Node.js**：20+ 及 npm
- **Rust**：1.78+ (`rustup`, `cargo`)
- **Android SDK**（仅 Android 端需要）：Android Studio Hedgehog+ 或 JDK 17+

#### 1. macOS 桌面端开发与构建
```bash
# 1. 克隆代码仓库
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. 进入桌面客户端目录
cd desktop_client

# 3. 安装前端依赖
npm install

# 4. 以开发模式启动桌面客户端
npm run tauri dev

# 5. 构建正式生产版应用程序 (自动打包 .app 与 .dmg)
npm run tauri build
```

#### 2. Windows 桌面端开发与构建
```powershell
# 1. 进入 Windows 独立客户端目录
cd Windows_client

# 2. 安装前端依赖
npm install

# 3. 以开发模式启动
npm run tauri dev

# 4. 构建正式生产版安装包 (生成 NSIS .exe 安装程序)
npm run tauri build

# 或直接运行开箱即用打包脚本：
.\build_windows.ps1
```

#### 3. Android 原生移动端编译
```bash
# 1. 进入 Android 原生端目录
cd android_client

# 2. 编译 Debug 测试包
./gradlew assembleDebug

# 3. 编译正式签名 Release APK
./gradlew assembleRelease
```

---

## ⚖️ 法律声明与免责条款 (Disclaimer)

1. **软件定位**：
   本软件（GPDb）仅为一款**通用开源的离线成人媒体元数据本地索引与数据库管理工具（Universal Offline Adult Media Metadata & Library Management Tool）**。
2. **内容免责**：
   本项目源代码及其发布版本中**不包含、不托管、不分发任何受版权保护的音视频文件、种子数据或图片素材**。软件中展示的示例字段仅用于数据库技术验证与界面排版测试。
3. **使用者责任**：
   用户使用本软件管理其个人的本地数据库文件、或使用外部搜索跳转功能所产生的一切行为及版权合规责任，均由使用者本人独立承担，与本软件开发者及开源贡献者无关。
4. **合规遵守**：
   本项目仅面向达到法定成年年龄的用户。请在遵守您所在国家和地区相关法律法规的前提下合理、合法使用本开源工具。

---

## 📄 开源许可证 (License)

本项目采用 [MIT 许可证](LICENSE) 开源。您可以自由阅读、修改、分发或整合本项目代码，唯须在副本中保留原作者版权信息与本免责声明。
