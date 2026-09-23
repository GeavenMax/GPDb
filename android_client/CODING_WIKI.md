# GPDb Android 客户端开发规范与技术架构 Wiki (Coding Wiki)

---

## 1. 客户端功能与设计目标

GPDb Android 客户端是基于 **GPDb 离线影库系统** 的移动端原生级延伸。
其核心定位为：**“单机私密、零云端依赖、极致触控流畅度、海量影视元数据即时检索的移动掌上影库”**。

### 1.1 核心业务功能矩阵

| 功能模块 | 对应 macOS 端能力 | Android 端移动化重构要点 |
| :--- | :--- | :--- |
| **全库检索与多维筛选** | 63,000+ 部影片、100,000+ 分集、6,000+ 演员、1,300+ 厂牌 | 针对触屏优化的底部滑动抽屉（Bottom Sheet Filter）、快速字母索引栏（Scroller Indexer）、分集/影片无缝切换 |
| **沉浸式主页 (Home Feed)** | 焦点海报轮播、AI 导赏、往年今日、今日星光、经典系列大放送、随心探索盲盒 | 移动端 ViewPager 自动轮播、瀑布流（Staggered Grid）、触觉震动反馈（Haptic Feedback）、下拉刷新 |
| **离线媒体与海报系统** | `image_cache/` 离线缓存、自定义图片协议 `gpdb-img://` | Android Scoped Storage（分区存储）规范、Coil / Glide 现代化图片离线加载器、自适应多图封面拼图 |
| **影视/演员/系列/厂牌详情** | 多层级模态弹窗系统 | 移动端沉浸式转场（Shared Element Transition）、沉浸式状态栏与折叠工具栏（CollapsingToolbarLayout） |
| **个人数据与偏好洞察** | 收藏夹、播放历史、用户评分打标、大模型偏好画像 | 离线 SQLite WAL 模式、移动端轻量级大模型流式调用或离线画像解析、用户数据一键导入导出 |
| **外部资源扩展** | BoyfriendTV / Google / BT 磁力多站跳转 | Android 原生 `Intent.ACTION_VIEW` 跳转、多浏览器安全唤起 |

---

## 2. macOS 与 Android 技术栈深度对比与选型

### 2.1 技术栈对比矩阵

```
               [ macOS 桌面端 ]                     [ Android 移动端 ]
架构模型       Tauri v2 + Wry (WebKit)               Tauri Mobile / Kotlin 原生 + Jetpack Compose
底层核心       Rust (gpdb-core)                     Rust (gpdb-core via JNI) / 原生 Room SQLite
UI 渲染层      Vue 3 + Tailwind CSS + Lucide        Vue 3 (Capacitor/Tauri) 或 Jetpack Compose Material 3
数据库驱动     rusqlite (SQLite 3.45+, WAL)         rusqlite (NDK) 或 Android SQLiteDriver (WAL)
图片加载       gpdb-img 协议 + 自研解析器           Coil 3 (Kotlin) / 移动端拦截器缓存适配器
文件系统       POSIX FS, ~/Documents, Spotlight    Android Storage Access Framework (SAF) / App-Specific External
```

### 2.2 Android 客户端架构路线选型：**Tauri Mobile (Rust + Vue 3 / NDK 深度混编)**

经过对代码复用率与长期维护成本的评估，推荐采用 **Tauri v2 Mobile 架构** 并辅以 Android 原生 NDK 支持：
1. **90% 业务逻辑与查询零成本复用**：`gpdb-core` (Rust) 中的 SQL 语句、分集全称模糊搜索、系列拼图算法直接跨平台编译为 `.so`（ARM64 / x86_64）；
2. **UI 逻辑无缝延续**：已针对移动端做过响应式测试的 Vue 3 组件生态（Tailwind、Lucide 图标）可直接适配折叠屏与手机竖屏；
3. **两端并行开发与数据隔离**：建立专有的 `android_client/`，配置文件与编译目标彻底隔离，避免任何平台间文件污染。

---

## 3. 两端并行开发与数据隔离策略 (Data Isolation Guidelines)

### 3.1 目录结构与隔离规范

```
GEVI_Offline_Database/
├── desktop_client/           # macOS 桌面端专属工程（独立 Git 变更集）
│   ├── src/                  # Vue 3 前端代码
│   ├── src-tauri/            # Tauri 桌面端 Rust 壳工程
│   └── tauri.conf.json       # 桌面端配置
│
├── android_client/           # 【新增】Android 移动端专属工程
│   ├── app/                  # Android 原生工程目录（Gradle）
│   │   ├── src/main/
│   │   │   ├── java/ (或 kotlin/)
│   │   │   ├── res/          # 移动端图标、XML 布局、启动画面
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── src-ui/               # 移动端适配的 Vue 3 前端模块
│   ├── src-rust/             # Android JNI / NDK 适配层（基于 gpdb-core）
│   ├── CODING_WIKI.md        # 本开发 Wiki
│   ├── COMPILATION_PLAN.md   # 编译与构建计划书
│   └── README.md             # 快速入门与环境指引
│
├── gevi.db                   # 主库数据文件（只读参考或开发测试使用）
└── image_cache/              # 媒体图片资源库（移动端采用 SAF 关联或分包测试）
```

### 3.2 数据隔离原则

1. **配置隔离**：
   - 桌面端配置存放于 `~/Library/Application Support/com.gpdb.app/`；
   - Android 端配置存放于 `context.filesDir` 或 `EncryptedSharedPreferences`（包名：`com.gpdb.android`），互不穿透。
2. **数据库文件隔离**：
   - Android 端不强制将桌面庞大的 `gevi.db` 硬编码打包入 APK（否则 APK 将超 1GB）；
   - 采用 **SAF (Storage Access Framework)** 允许用户点选手机内部存储、SD 卡或 OTG U 盘中的 `gevi.db` 与 `image_cache` 目录，或者支持一键从局域网与 Mac 客户端同步。
3. **编译产物隔离**：
   - 桌面构建产物严格输出至 `desktop_client/src-tauri/target/`；
   - Android 构建产物严格输出至 `android_client/app/build/` 或 `android_client/target/`，两端 `Cargo.lock` / `node_modules` 保持独立。

---

## 4. 移动端核心技术实现规范

### 4.1 存储访问框架 (SAF) 与权限管理
- Android 10+（API 29+）强制实行 Scoped Storage（分区存储）。
- 绝不使用野蛮的全局 `READ_EXTERNAL_STORAGE` 权限申请。
- 启动时弹出友好的**权限与存储指引界面**，引导用户通过系统文件选择器（`ACTION_OPEN_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`）指定影库文件夹，获取持久化 URI 权限（`takePersistableUriPermission`）。

### 4.2 高性能图片离线加载
- 移动端显存与内存更为敏感，必须对海报加载实施降采样（Downsampling）。
- 系列拼图（`SeriesCollageCover`）在移动端限制最大渲染纹理尺寸为 512×512，避免内存溢出（OOM）。
- 启用两级缓存：内存 LRU 缓存 + 磁盘缓存。

### 4.3 触控交互规范
- 所有卡片最小触控面积不低于 48×48 dp。
- 详情弹窗采用移动端底栏抽屉（Bottom Sheet）交互，支持轻拉展开、下滑关闭。
- 引入适度的触觉反馈（Haptic Feedback），在收藏、解锁成就、切换标签时给予细致物理震动响应。
