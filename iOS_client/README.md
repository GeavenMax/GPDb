# GPDb iOS 客户端工程脚手架 (SwiftUI Native)

本项目为 **GPDb 离线影视库系统的原生 iOS 客户端**，以成熟稳定的 Android 客户端 (v2.15.0) 及桌面端为参照基准，针对苹果平台深度定制。
采用最新 **SwiftUI 5 (iOS 17+)** 声明式界面、**GRDB.swift** 高性能本地 SQLite 数据库引擎、**ZIPFoundation** 流式图片解压与 **Kingfisher** 三级缓存架构，严格遵循苹果人机交互指南 (HIG)。

---

## 一、技术栈核心映射与特性

| 业务模块 | Android / 桌面端实现 | iOS 原生技术栈 | 架构说明与 HIG 适配 |
| :--- | :--- | :--- | :--- |
| **界面 UI 框架** | Jetpack Compose (Material 3) | **SwiftUI** (iOS 17+) | 现代化声明式组件、支持 NavigationStack、iPadOS 侧边三栏分栏适配 |
| **本地数据库 ORM** | Room (SQLite ORM) | **GRDB.swift** (6.29+) | 100% 保持与 Android 相同的 `GPDb.db` 表结构读取，支持 SQLite Schema 动态自愈与 WAL 并发模式 |
| **压缩图库直接读取** | `ZipImageFetcher` (Coil 3) | **ZIPFoundation** | 基于纯 Swift 内存流直接解压 `GPDb_Images.zip` (15GB+) 内海报剧照，无需提前在真机中物理全解压 |
| **图片加载与三级缓存** | Coil 2 / 3 自定义 Fetcher | **Kingfisher** (7.12+) | 自定义 `ZipImageDataProvider`，支持内存 LRU + 磁盘持久化缓存 |
| **应用安全与防窥** | `FLAG_SECURE` + 密码锁 | **LocalAuthentication + Material** | 退后台毛玻璃遮罩防窥，支持 Face ID / Touch ID 解锁，顶部导航栏即时防窥图标开关 |
| **分享长图生成** | Compose `graphicsLayer` 抓取 | **SwiftUI `ImageRenderer`** | 原生高质量离屏视图栅格化，导出 3x 超高清流光长图，底角嵌入 Telegram 官方 27x27 矢量二维码 |
| **数据备份与跨端迁移** | JSON 序列化 + SAF 文件选择器 | **UniversalBackupService** | 采用全平台统一 `gpdb_universal_backup` 规范，实现与 macOS / Windows / Android 四端无缝互通 |
| **版本在线检测** | OkHttp + FileProvider | **URLSession** + GitHub API | 启动 3 秒后异步静默巡检 GitHub Releases，展示更新日志并支持下载更新 |

---

## 二、工程目录结构 (Source Tree)

```
iOS_client/
├── Package.swift                           # Swift Package Manager 依赖清单
├── README.md                               # 本项目说明指南
├── ARCHITECTURE.md                         # 架构分层设计与 Android 对照表
├── CHANGELOG.md                            # 版本演进履历
├── CODING_WIKI.md                          # 核心开发规范与架构 Wiki
└── GPDb/
    ├── App/
    │   ├── GPDbApp.swift                   # 应用主入口 (@main)
    │   ├── AppEnvironment.swift            # 全局环境与挂载状态管理
    │   └── AppNavigation.swift             # 全局导航路由 (iPhone / iPadOS 适配)
    ├── Core/
    │   ├── Database/
    │   │   ├── DatabaseHolder.swift        # GRDB 数据库单例管理与 SQLite Schema 自愈
    │   │   ├── Schema/                     # 完备数据实体映射 (Movie, Performer, Episode, Series, etc.)
    │   │   └── Repositories/               # 业务数据仓储 (HomeFeed, Browse, Movie, Performer, User)
    │   ├── Image/
    │   │   ├── ZipImageProvider.swift      # 基于 ZIPFoundation 的内存流解压 Provider
    │   │   └── GpdbImageView.swift         # 统一海报/剧照/头像异步渲染组件
    │   └── Security/
    │       ├── BiometricAuth.swift         # Face ID / Touch ID 生物认证
    │       └── PrivacyProtection.swift     # 退后台模糊快照防窥与即时高斯模糊修饰符
    ├── UI/
    │   ├── Common/                         # 通用原子组件 (灯箱、字母索引条、排版选择器)
    │   ├── Feed/                           # 首页 Feed 流 (HomeFeedView)
    │   ├── Browse/                         # 分类检索与多维筛选 (BrowseView, FilterSheetView)
    │   ├── Detail/                         # 影片详情、单流式演员全息档案、分集详情
    │   ├── Library/                        # 个人收藏与历史专注看板 (复合唯一键防崩溃)
    │   ├── Settings/                       # 设置页、挂载管理、通用备份导入导出
    │   └── Share/                          # 独立分享卡片与流光渲染 (ShareCardView)
    └── Services/
        ├── GitHubUpdateService.swift       # GitHub Release 检测更新服务
        └── UserBackupService.swift         # gpdb_universal_backup 编解码与系统文件交互
```

---

## 三、快速开始与编译验证

```bash
# 进入工程目录
cd iOS_client

# 校验 Swift 依赖并编译
swift build
```
