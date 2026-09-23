# GPDb · 极速隐私优先的男同成人影视数据库管理客户端

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>专为男同成人影视爱好者打造的高性能、全维度档案管理、绝对隐私与零云端追踪的现代化管理客户端</strong>
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
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Android%20(In%20Dev)-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **官方 Telegram 频道**：欢迎订阅 [GPDb 官方 Telegram 频道 (https://t.me/gpdbnews)](https://t.me/gpdbnews)，获取最新的版本发布动态、数据库更新通知与使用技巧！

---

## 📖 项目定位与简介 (About GPDb)

**GPDb**（Gay Pornography Database Manager）是一款专门面向**男同成人影视（Gay Adult Media）**爱好者、数字媒体收藏家与影视资料研究者打造的**现代化全功能离线数据库管理客户端**。

在成人媒体领域，爱好者的个人观影记录、收藏列表与性向偏好属于**最核心、最敏感的个人隐私**。商业流媒体或中心化在线平台普遍存在账号追踪、数据分析泄露、版权到期下架等不可控风险。

**GPDb 坚持“100% 本地优先（Offline-First）与绝对私密无痕”的核心设计哲学**：
- **零云端依赖**：所有的媒体元数据、演职人员身体档案、海报/剧照缓存、个人打标评分及收藏足迹均完全留存在用户自己的设备物理磁盘中；
- **纯单机架构**：不设任何账号体系、无任何遥测追踪代码、无任何外部服务器上传；
- **极致性能保障**：基于高性能 **Rust 引擎 (`gpdb-core`)** 与 **Tauri v2 + Vue 3** 现代架构，面对 **60,000+ 部完整影片、100,000+ 独立分集、6,000+ 演员身体档案与 1,300+ 经典与现代制片厂牌**，仍能实现 60fps 丝滑渲染与毫秒级即时检索。

---

## ✨ 紧扣领域特性的核心亮点 (Domain-Specific Features)

### 1. 全维度演员身体属性与特征深度检索
- **极细粒度的生理与体貌属性筛选**：
  支持按**体型身段（Build）**（如 Muscle / Twink / Bear / Hunk）、**发色（Hair）**、**瞳色（Eyes）**、**胡须与体毛丰度（Facial Hair / Body Hair）**、**身高体重（Height / Weight）**、**肤色（Skin）**、**生理特征尺寸（Dick Size / Foreskin）**、**纹身穿孔（Tattoos）** 等多维特征组合过滤。
- **艺名与曾用名全息对齐**：
  同一位演员在不同制片厂牌、不同年代的演出艺名或别名（Aliases）自动智能聚合，避免由于艺名变动导致漏看。
- **正片（Films）与分集（Scenes）精准区分**：
  支持单独查看演员作为正片主角的完整影视作品，亦可独立查看其作为单集客串的独立短剧，参演篇目一览无余。

### 2. 流媒体级沉浸式视听主页 (Home Feed)
- **焦点巨幕海报轮播**：顶端高清画质海报平滑自动轮播，带细腻视差交互。
- **往年今日 · 经典首映**：智能对照历史日历，回顾黄金时代（80年代、90年代、千禧年代）同日首映的经典作品。
- **今日星光 · 标志面孔**：严格进行本地物理磁盘海报文件校验，智能推荐拥有高清头像的标志性面孔，杜绝字母占位符。
- **经典系列大放送**：自动打捞作品跨度达十余部曲的长篇标志性 IP 系列。
- **随心探索 · 盲盒发现**：一键摇骰，从六万余部片库中随机挖掘冷门宝藏。

### 3. 智能系列影片集与封面拼图 (Smart Series & Collage Covers)
- **自动聚类算法**：内置罗马数字与副标题识别技术，自动将散落的系列影片（如 *Part I, Part II, Part III*）聚合为连贯篇章。
- **自适应拼贴艺术封面**：为系列作品自动生成 1 张单幅海报、2 张对称拼图、3 张阶梯排布或 4 分格田字矩阵的艺术拼接封面，支持暗角光晕与离线极速渲染。

### 4. 导演档案卡与厂牌流派图鉴
- **导演专属履历**：影片详情一键唤起执导导演专属卡片，展示其历史执导全量片单，并支持一键前往片库按导演筛选。
- **厂牌专属收录**：覆盖各大经典胶片制片厂牌（Falcon, Colt, Catalina 等）至现代主流厂牌（Men.com, BelAmi, Lucas Entertainment, Corbin Fisher 等），作品编年史与风格标签一览无余。

### 5. AI 影迷偏好洞察与审美画像 (AI Persona Insights)
- **全异步无感架构**：后台独立线程调度本地/远程大语言模型（如 DeepSeek、OpenAI、Claude 等），彻底消除界面假死。
- **深度审美肖像长文**：基于用户的真实私密收藏与打标足迹，剖析其审美基因，生成包含“核心审美原型代号”（如“新浪潮怀旧探索者”）、“时代视听光谱”的 2000 字深度艺术鉴赏报告。
- **实时进度弹窗**：高奢磨砂玻璃悬浮面板，分步展示大模型加密握手、足迹特征提取与画像生成进度。

### 6. 资源检索与外部扩展插件系统 (v2.0)
- **多站精准直达**：在影片与演员页面，支持一键跳转 BoyfriendTV、Google 及各大权威影视数据库，使用条目原英文名精准跳转，免去二次打字。
- **BT 磁力搜索集成**：一键将电影原名与厂牌组合为标准搜索式，直通外部资源引擎。
- **独立自由开关**：插件中心支持针对各个外部跳查源进行独立细致的启闭配置。

### 7. 全语言国际化与游戏化成就系统
- **7 种全界面可选语言**：简体中文 (`zh-CN`)、繁体中文 (`zh-TW`)、English (`en`)、Italiano (`it`)、日本語 (`ja`)、Español (`es`)、Deutsch (`de`)。
- **PlayStation 风格成就奖杯**：内置数十种探索、收藏与检索成就，解锁时触发 PSN 风格流体毛玻璃动效弹窗。

---

## 🛠️ 技术架构 (Technology Stack)

```
┌─────────────────────────────────────────────────────────────┐
│                    GPDb 前端表现层 (UI)                     │
│  Vue 3 + Vite + TypeScript + Tailwind CSS + Lucide Icons   │
│       (响应式栅格布局、毛玻璃特效、国际化 i18n、状态管理)     │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Tauri IPC / 高速二进制通道)
┌──────────────────────────────▼──────────────────────────────┐
│                    Tauri v2 宿主适配层                      │
│     自定义协议 (gpdb-img://)、系统窗口控制、安全文件选择器     │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Rust Native 接口)
┌──────────────────────────────▼──────────────────────────────┐
│                 Rust 核心服务层 (gpdb-core)                 │
│ • SQL 查询生成器与模糊索引 (rusqlite)                         │
│ • 多级本地图片物理校验与安全解析 (Cache Resolver)             │
│ • 异步大模型交互通道 (Async Runtime)                         │
│ • 自动化 Schema 迁移与自愈引擎 (Migrate Engine)              │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    底层持久化存储 (Storage)                 │
│      GPDb.db (SQLite 3 WAL 模式) + image_cache/ (本地图片)  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 快速上手 (Quick Start)

### 普通用户（推荐）

直接前往本仓库的 [Releases 页面](https://github.com/GeavenMax/GPDb/releases) 下载已打包编译完成的安装包：
- **macOS**：下载 `GPDb-macOS-v2.6.0.dmg`，双击后将 `GPDb.app` 拖入 `Applications`（应用程序）即可启动。
- **Android**（独立工程开发中）：下载 `.apk` 安装包直接安装于手机或平板。

### 开发者本地编译与运行

#### 环境要求
- Node.js 20+ 及 npm
- Rust 1.78+ (`cargo`)
- macOS 12+ (支持 Apple Silicon M 系列及 Intel 架构)

#### 运行步骤
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
