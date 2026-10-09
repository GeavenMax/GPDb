# GPDb iOS 客户端开发规范与技术架构 Wiki (Coding Wiki)

---

## 1. 客户端功能定位与设计目标

GPDb iOS 客户端是基于 **GPDb 离线影视数据库系统** 的苹果原生移动端实现。
其核心定位为：**“单机私密、零云端依赖、极致触控流畅度、深度契合苹果 iOS 人机交互指南 (HIG) 的移动掌上影库”**。

### 1.1 核心业务功能矩阵

| 功能模块 | 功能特征与技术实现 | iOS 原生与 HIG 专属优化点 |
| :--- | :--- | :--- |
| **全库检索与多维筛选** | 63,000+ 部影片、100,000+ 分集、6,000+ 演员、1,300+ 厂牌、导演、系列 | 三级降级检索（FTS5 优先 -> 多字段联合 SQL 容错）；原生 PresentationDetents (`.sheet`) 底部抽屉；右侧悬浮字母索引条与触感反馈 (`UISelectionFeedbackGenerator`) |
| **沉浸式探索主页 (Home Feed)** | 焦点海报轮播、AI 导赏、今日星光、经典系列大放送、随心探索盲盒 | `TabView` 手势与自动轮播；今日星光动态过滤无头像演员；经典系列随机抽样 (`ORDER BY RANDOM() LIMIT 10`)；随心探索触感震动盲盒；顶部即时防窥开关 |
| **离线媒体与压缩图库读取** | `image_cache/` 物理目录或 `GPDb_Images.zip` (15GB) 内存流直读 | `ZIPFoundation` 纯内存流式按需解压条目 + `Kingfisher` 三级缓存 (Memory LRU + Disk Cache)；支持 iOS 文件夹安全书签 (Security-Scoped Bookmark) |
| **影视/演员/系列/厂牌详情** | 全量元数据展现、维基小传、生理特征、曾用艺名 (AKA)、多站检索外链 | **单流式 Lazy 布局架构**（档案卡片与 Tab 分组作为顶置 View 嵌入同一滚动容器，彻底杜绝头部折叠与滑动冲突）；双指手势缩放灯箱 (`ZoomableImageViewer`)；3D 景深翻转卡片 |
| **全平台通用数据备份与迁移** | 用户收藏夹、播放探索统计、评分、私密笔记、界面偏好配置 | `gpdb_universal_backup` 跨平台统一标准 JSON，接入 iOS 文件选择器 (`.fileExporter` / `.fileImporter`)，与 macOS / Windows / Android 四端无损互通 |
| **流光分享卡片** | 正向海报封面与反面封底双图排版、Telegram 官方频道点阵二维码 | SwiftUI `ImageRenderer` 3x 超高清 Retina 离屏长图捕获；16:9 分集居中自适应裁切；全量演职员与剧情自适应弹性膨胀；双离屏磨砂模糊打码 |
| **生物安全与防窥体系** | Face ID / Touch ID 解锁锁、退后台毛玻璃遮罩、全局高斯模糊打码 | `LocalAuthentication` 生物识别认证；监听 `scenePhase` 退后台自动覆盖 `.ultraThinMaterial` 毛玻璃；全局响应式 `.privacyBlur()` 装饰器 |
| **在线检测更新** | GitHub Releases API 检索、版本特性展示、在线跳转升级 | 启动 3 秒后异步静默巡检 GitHub API，提供拟态更新弹窗与 TestFlight / 签名包下载直达 |

---

## 2. iOS 移动端技术栈架构全景

```
[ UI 渲染与交互层 (Presentation Layer) ]
  SwiftUI 5.0+ (iOS 17+) + NavigationStack
  设备自适应: iPhone 端 TabView + NavigationStack / iPadOS 端 NavigationSplitView (三栏侧边栏)
  异步状态流: @Observable 现代响应式宏 (iOS 17) / Combine ObservableObject 适配
  图片引擎: Kingfisher 7.12+ (集成自定义 ZipImageDataProvider / GpdbImageView)
  交互细节: UIImpactFeedbackGenerator 触感反馈、平滑视差动画、Material 毛玻璃材质

[ 业务逻辑与仓储层 (Domain & Repository Layer) ]
  HomeFeedRepository / BrowseRepository / MovieRepository / PerformerRepository / UserRepository
  UserAnalyticsRepository (统计数据与行为追踪)
  GitHubUpdateService (GitHub Releases API 异步版本巡检)
  BiometricAuthService (LocalAuthentication 生物识别)

[ 离线媒体与文件系统层 (Media & VFS Layer) ]
  Security-Scoped Bookmarks: 记住外部挂载的 GPDb.db 与 GPDb_Images.zip 的沙盒安全授权
  ZIPFoundation 0.9+: 基于纯 Swift 内存流直接读取压缩包内图像字节
  Relative Path Resolver: images/(Covers|Episodes|Stars|...)/... -> 本地物理/虚拟路径映射
  PrivacyBlurEngine: 响应式高斯模糊与暗色半透明打码覆层

[ 本地数据持久化层 (Data Storage Layer) ]
  GRDB.swift 6.29+ (SQLite 3, WAL 并发读写模式, 只读并发连接池与事务写操作)
  挂载前置自愈机制: DatabaseHolder.ensureSchemaCompatibility (原生 SQLite C API 直连补列与建表)
  实体映射 (FetchableRecord, TableRecord, Codable):
    MovieRecord, PerformerRecord, PerformerPbcProfileRecord, PerformerSjProfileRecord,
    EpisodeRecord, SeriesCollectionRecord, UserFavoriteRecord, UserMovieDataRecord, Glossaries
```

---

## 3. 核心子系统与关键设计规范

### 3.1 外部数据库挂载与架构自愈机制 (GRDB Schema Compatibility)

由于 iOS 客户端通过 Document Picker 动态挂载用户指定的外部 `GPDb.db` 文件，不同时期生成的数据库可能缺少最新扩展字段或表。

#### 前置自愈机制 (`ensureSchemaCompatibility`)
在 GRDB 执行 `DatabaseQueue(path: path)` 构建连接之前，必须优先触发 `ensureSchemaCompatibility`：
1. 使用原生 SQLite C API (`sqlite3_open`) 以读写模式直接打开该文件；
2. 执行 `PRAGMA table_info(performers);`，检测 `pbc_url` 与 `sj_url` 是否存在，缺失则就地执行 `ALTER TABLE performers ADD COLUMN ...`；
3. 检测扩展表 `performer_pbc_profiles`、`performer_sj_profiles`、`user_favorites` 与 `user_movie_data` 是否存在，若不存在则就地创建完备 DDL；
4. 关闭原生连接，将自愈后的数据库交付 GRDB 打开。彻底根绝由于表结构字段缺失导致的挂载崩溃与查询失败。

### 3.2 单流式 Lazy 布局规范 (Single-Stream Lazy Layout)

#### 严禁使用的反模式
- **严禁使用手势折叠结合隐藏状态**：类似滑动隐藏 Header 的做法会导致 Header 被意外隐藏或滚动失灵。
- **严禁在非滚动的 `VStack` 中直接放置带有 `frame(maxHeight: .infinity)` 的 `LazyVGrid`**：SwiftUI 外部滚动视图与内部动态列表嵌套会引发严重的测量死锁与滚动冲突。

#### 推荐的标准单流规范
在 `PerformerDetailView.swift` 中：
```swift
ScrollView {
    LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
        // 1. 人物档案全量卡片置顶 (头像、生理指标、PBC百科、曾用艺名、外链)
        PerformerProfileHeaderView(performer: performer, pbc: pbc, sj: sj)
            .padding(.horizontal)

        // 2. 分类吸顶标签栏 (出演作品 / 出演分集)
        Section(header: PerformerTabBar(selectedTab: $selectedTab)) {
            if selectedTab == .movies {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)], spacing: 12) {
                    ForEach(movies) { movie in
                        MovieGridCard(movie: movie)
                    }
                }
                .padding()
            } else {
                LazyVStack(spacing: 12) {
                    ForEach(episodes) { ep in
                        EpisodeRowCard(episode: ep)
                    }
                }
                .padding()
            }
        }
    }
}
```
切换到「出演分集」时在同一容器流内更新，保证全局滑动顺畅、零手势冲突、首屏信息完备直观。

### 3.3 压缩图库直接读取与防窥保护系统

1. **统一图片加载管道 `ZipImageDataProvider`**：
   - 适配 `GPDb_Images.zip` (15GB+)。通过 `ZIPFoundation` 的 `Archive` 实例按 entry 相对路径流式提取 `Data`，无需提前在真机中解压庞大的物理文件；
   - 提取出的二进制流接入 `Kingfisher` 管道，自动享用内存 LRU 与磁盘持久化缓存；
   - 若用户挂载了已解压的物理目录，则直接拼接物理绝对路径读取，具备自动环境自适应。
2. **防窥模糊装饰器 `.privacyBlur(isActive:blurText:)`**：
   - 当全局防窥模式激活时，通过 SwiftUI `.blur(radius: 32)` 与暗色半透明覆层实现瞬时离屏模糊保护；
   - 主页顶部导航栏放置即时防窥图标开关，并与设置页状态双向强联动。

### 3.4 收藏系列防崩溃复合唯一键规范 (`LibraryView.swift`)

- 底层数据库中不同厂牌存在重名系列（如 `Auditions`、`Bad Boys` 等）。
- 在 SwiftUI `ForEach` 中，严禁单独使用 `series.rootTitle` 作为唯一 ID，必须统一使用复合唯一键：
  ```swift
  var compositeKey: String {
      if let id = self.id { return "series_\(id)" }
      return "\(studioName ?? "unknown")_\(rootTitle)_\(hashValue)"
  }
  ```
- 彻底避免 `ForEach` 遇到重复键引发的 UI 错乱或运行时崩溃。

### 3.5 全平台通用用户配置与数据备份规范 (`gpdb_universal_backup`)

- **规范标识**: `"format": "gpdb_universal_backup", "version": 2`
- **支持范围**:
  - `favorites`: 收藏实体（长片、分集、演员、导演、系列、厂牌）；
  - `movie_user_data`: 自定义标记、私密笔记、评星状态；
  - `analytics`: 专注总时长、各维度探索/播放计数、活跃天数；
  - `tags`: 自定义标签库。
- **文件交互**: 接入 iOS 原生 `.fileExporter` 与 `.fileImporter`，生成并读取标准 `.json` 文件，实现与 macOS / Windows / Android 四端无障碍互转。

### 3.6 分享卡片流光引擎与 Telegram 官方二维码

- **双封面布局**: 支持同时排布海报正面封面与反面封底，若缺少封底则优雅自适应居中单封面。
- **16:9 分集比例居中裁剪**: 彻底消除分集剧照被 2:3 海报比例强行挤压导致的形变。
- **官方二维码 Canvas 矢量点阵**: 嵌入 27×27 标准 QR 矩阵，白底圆角容器包裹，直通官方频道 `https://t.me/gpdbnews`。
- **超清离屏捕获**: 基于 SwiftUI `ImageRenderer` (scale = 3.0) 导出超高清 Retina 位图，直通系统相册 (`PHPhotoLibrary`) 与系统原生分享 (`UIActivityViewController`)。

---

## 4. 目录组织架构 (Source Tree)

```
iOS_client/
├── Package.swift                           # Swift Package Manager 依赖清单
├── README.md                               # 本项目说明指南与 HIG 规范
├── ARCHITECTURE.md                         # 架构分层设计与 Android 对照表
├── CHANGELOG.md                            # 跨平台版本演进记录
├── CODING_WIKI.md                          # 本开发规范与架构 Wiki
└── GPDb/
    ├── App/
    │   ├── GPDbApp.swift                   # 应用主入口 (@main)，ScenePhase 监听
    │   ├── AppEnvironment.swift            # 全局环境、挂载状态与防窥联动
    │   └── AppNavigation.swift             # 全局导航路由 (iPhone TabView / iPad NavigationSplitView)
    ├── Core/
    │   ├── Database/
    │   │   ├── DatabaseHolder.swift        # GRDB 数据库单例管理与 SQLite Schema 自愈
    │   │   ├── Schema/                     # 完备数据实体映射
    │   │   │   ├── MovieRecord.swift       # 电影表映射 (21列)
    │   │   │   ├── PerformerRecord.swift   # 演员主表、PBC 扩展表与 SJ 扩展表
    │   │   │   ├── EpisodeRecord.swift     # 分集表与演员关联表
    │   │   │   ├── SeriesRecord.swift      # 系列表与片商映射
    │   │   │   ├── UserRecord.swift        # 收藏表与影视打标/评分笔记
    │   │   │   └── GlossaryRecord.swift    # 属性术语与分类术语汉化查表
    │   │   └── Repositories/               # 业务数据仓储
    │   │       ├── HomeFeedRepository.swift # 首页 Feed 推荐、星光过滤、随机系列
    │   │       ├── BrowseRepository.swift  # 多维筛选、分页、三级降级检索
    │   │       ├── MovieRepository.swift   # 电影详情、演职员关联、分集关联
    │   │       ├── PerformerRepository.swift # 演员档案全息合并 (GEVI/PBC/SJ)
    │   │       ├── UserRepository.swift    # 收藏/评分/笔记与通用备份交互
    │   │       └── UserAnalyticsRepository.swift # 本地行为专注统计
    │   ├── Image/
    │   │   ├── ZipImageProvider.swift      # 基于 ZIPFoundation 的内存流解压 Provider
    │   │   └── GpdbImageView.swift         # 统一异步海报视图组件与防窥打码
    │   └── Security/
    │       ├── BiometricAuth.swift         # Face ID / Touch ID 生物识别认证
    │       └── PrivacyProtection.swift     # 退后台模糊快照与即时防窥修饰符
    ├── UI/
    │   ├── Common/                         # 通用组件 (灯箱、字母索引、排版选择器)
    │   │   ├── ZoomableImageViewer.swift   # 全屏手势缩放画廊灯箱
    │   │   ├── SectionIndexBar.swift       # iOS 风格右侧字母悬浮检索滑块
    │   │   └── VisualLayoutPicker.swift    # 图示化排版选择器 (画廊 vs 3D翻转)
    │   ├── Feed/                           # 首页 Feed 流 (HomeFeedView)
    │   ├── Browse/                         # 分类检索与多维筛选 (BrowseView, FilterSheetView)
    │   ├── Detail/                         # 电影/演员/分集/系列详情 (单流式架构)
    │   ├── Library/                        # 个人收藏与历史专注看板 (LibraryView)
    │   ├── Settings/                       # 设置页、挂载管理、通用备份导入导出
    │   └── Share/                          # 独立分享卡片与流光渲染 (ShareCardView)
    └── Services/
        ├── GitHubUpdateService.swift       # GitHub Release 检测更新服务
        └── UserBackupService.swift         # gpdb_universal_backup 编解码与系统文件交互
```

---

## 5. 构建、发布与验证指南

### 5.1 本地编译指令

```bash
# 切换至 iOS 工程根目录
cd iOS_client

# 执行 Swift Package 语法校验与编译构建
swift build
```

### 5.2 产物输出与规范

- SPM 库产物：`.build/release/libGPDbCore.a` 或目标 Xcode 应用程序工程包；
- 设计严格遵循苹果 iOS Human Interface Guidelines (HIG)，支持深色模式 (Dark Mode)、动态字体 (Dynamic Type)、iPadOS 分栏多任务、以及 Face ID 安全认证。

---

## 6. 片商超清 Logo & 封套徽章资产系统 (Studio Logos Engine)

iOS 客户端基于纯离线海报和厂牌 Logo 规范呈现：
- **资源共享与存放**：Logo 资源统一存放于 `image_cache/Logos/*_logo.webp` 与 `image_cache/Logos/*_banner.webp`，通过 `ZipImageProvider` 或本地文件路径快速渲染。
- **全量 100% WebP 规范**：全量 2,492 枚独立厂牌 Logo 与 694 枚 Banner 已 100% 升级为高保真 `.webp` 格式，原生 Kingfisher 与 SwiftUI 高速解码无内存抖动。
- **1:1 方块自适应**：全量封套包装切片与品牌标准字徽标统一生成 512×512 正方形版本，完美贴合 iOS SwiftUI 圆形/圆角头像展示。
- **100% 全覆盖现状**：全库 2,492 家有效独立制片厂牌已实现 100% 专属 Logo 覆盖，全库 97.99% 的影视作品拥有所属厂牌 Logo。
- **管理与生成工程**：收录于专用子目录 `studio_logos/`，详见 [`studio_logos/README.md`](../studio_logos/README.md) 与 [`STUDIO_LOGO_PROGRESS.md`](../STUDIO_LOGO_PROGRESS.md)。
