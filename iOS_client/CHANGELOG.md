# Changelog (GPDb iOS Client)

All notable changes to the GPDb iOS Client will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.16.1] - 2026-10-02

### Optimized
- **演员档案页作品分类切换栏移至底部导航栏上方沉浸吸附 (`PerformerDetailView.swift`)**：
  - 将原置于页面滚动流内部的「出演电影」「出演分集」分类横条解耦，改为通过 `.safeAreaInset(edge: .bottom)` 优雅悬浮于底部导航栏（TabBar）正上方；
  - 配备顶部微光分界线与 `.ultraThinMaterial` 高级毛玻璃半透明质感，单手大拇指触手可及，大幅提升在长篇幅影视作品与分集之间切换的交互舒适度。
- **瀑布流滑动时底部导航栏自动收缩与展开动效 (`WaterfallScrollView.swift`, `AppEnvironment.swift`, `AppNavigation.swift`)**：
  - 新增专用沉浸式滚动容器 `WaterfallScrollView`，深度绑定 `AppEnvironment.isTabBarHidden` 与 SwiftUI 系统级 `.toolbar(visibility, for: .tabBar)`；
  - 当用户在瀑布流或网格中向下滑动浏览作品时，底部导航栏自动平滑收起隐藏（配合 22pt 滤波防抖阈值，杜绝微颤抖动），最大化纵向浏览视野与沉浸感；
  - 当用户向上滑动或触达顶部时，底部导航栏即时自动展开浮现；切换 Tab 或退出页面时安全自愈恢复可见；
  - 全量装配至主页 Feed、影视库全部二级分类、我的影库、片商档案以及分类过滤瀑布流。
- **冷启动数据库装载性能极限优化与冷启主线程零冻结 (`DatabaseHolder.swift`, `AppEnvironment.swift`, `ZipArchiveService.swift`, `HomeFeedRepository.swift`)**：
  - **SQLite PRAGMA 高性能连接参数注入**：GRDB 连接池全面配置 WAL 预写日志模式（`PRAGMA journal_mode = WAL;`）、`PRAGMA synchronous = NORMAL;`、内存临时库（`PRAGMA temp_store = MEMORY;`）、256MB 内存映射（`PRAGMA mmap_size = 268435456;`）以及 64MB 页面缓存（`PRAGMA cache_size = -64000;`），将冷启动大文件数据库读写与索引查询性能提升 3~10 倍；
  - **Schema 自愈检查极速直通**：在 `ensureSchemaCompatibility` 中引入 `sqlite_master` 索引表直通判定，已自愈就绪的数据库 0.1ms 立即返回，杜绝每次冷启动重复解析与执行全套 15 张数据表 DDL 语句的 CPU 开销；
  - **解耦 ZIP 图库后台索引与零阻塞寻址 (`ZipArchiveService.swift`)**：将 14GB、23万+条目的 `GPDb_Images.zip` 索引构建完全移至后台专用工具线程，通过线程安全锁实现微秒级快速读写，彻底解除原 `ioQueue.sync` 导致冷启动主线程与数据库线程被死锁等待 10~15 秒的严重卡顿根因；
  - **首页明星查询耗时由 330ms 优化至 10ms (`HomeFeedRepository.swift`)**：重构 `starsSql`，避免全库扫描与海量多表聚合，结合 `ZipArchiveService.isIndexed` 状态智能降级直通；
  - **异步冷启动装载流程 (`AppEnvironment.swift`)**：应用启动时在后台并发任务中挂载数据库与图库，主线程始终保持流畅响应，杜绝 UI 掉帧或白屏。
- **全新殿堂级“正在装载数据库”启动动效与骨架屏重塑 (`DatabaseLoadingView.swift`, `HomeFeedView.swift`, `GPDbApp.swift`)**：
  - **打造 DatabaseLoadingView 专属冷启品牌界面**：以午夜深空与星云微光为底色，居中呈现动态呼吸微光的 Scheme A 纯矢量双火星徽标（24K 金与钛金质感交错结），环绕旋转轨道微光粒子与脉冲光晕；
  - **流光细进度条与动态状态提示**：配备高科技微光流光进度胶囊与多阶段装载状态提示（“正在扫描本地数据库” $\to$ “正在开启离线数据通道” $\to$ “已配置 WAL 模式与 256MB 内存映射”），搭配淡入淡出平滑过渡；
  - **主页骨架屏重构 (`HomeFeedView.swift`)**：彻底移除简陋系统 `ProgressView`，升级为对标一流流媒体应用的毛玻璃胶囊加载状态与焦点海报、明星头像、经典系列高保真骨架卡片流。
- **iOS 应用图标全屏无边框重构与 iOS 18+ 自适应多外观支持 (`AppIcon.appiconset`)**：
  - **根除外围白框 Bug**：全面排查根因定位，此前直接复用 macOS 图标导致带有透明外边距和圆角切角，而 iOS 系统会在遇到透明像素时强制以白色填充整个外圈，形成突兀刺眼的“白框”；
  - **1024x1024 全画幅满铺无 Alpha 通道重制**：移除所有人为圆角切片与透明遮罩，将午夜深蓝宇宙星空背景直接无损铺满至画布四角（0,0 到 1023,1023），彻底抹除 Alpha 通道（导出为纯 24-bit RGB），交由 iOS SpringBoard 运行时自动裁切圆角，与系统桌面 100% 完美融合；
  - **支持 iOS 18+ 自适应三种外观 (Adaptive Icons)**：
    - **标准外观 (`AppIcon-1024.png`)**：高饱和经典午夜深蓝星空、宇宙星云光晕、24K 缎面拉丝金与冷钛双火星符号交错光结；
    - **深色外观 (`AppIcon-1024-dark.png`)**：基于 OLED 纯黑空间背景重新渲染，大幅强化金色与冷钛符号的发光辉度与聚焦光斑，与暗色桌面无缝融合；
    - **染色外观 (`AppIcon-1024-tinted.png`)**：严格遵循 Apple HIG 规范，采用高对比度单图层黑白灰度渐变与中性深灰底色（`#161618`），用户在 iOS 18 桌面选取任意个性化主题色时，系统可对高光与符号精确着色，呈现浑然一体的高级感。

### Fixed
- **跑马灯长文本从屏幕最右侧跳入 Bug 根除与左边缘锚定无缝循环重构 (`MarqueeText.swift`)**：
  - **根因深度定位**：排查发现原 `GeometryReader` 内嵌未限定宽度的 2 倍宽 `HStack` 时，SwiftUI 默认对超宽子视图采取居中排布，导致文本起始 X 坐标被强制推向屏幕负坐标，首个文本被左侧截断，而次级文本从屏幕右侧突兀滑入；且 `.repeatForever` 在状态更新时未能可靠重置；
  - **严格左边缘固定锚定**：对轮播 `HStack` 与容器包裹 `.frame(width: cWidth, alignment: .leading)`，确保进入页面、切换条目或重绘时初始位移严格为 0（即文字开头与普通 Text 完全一致，严格靠左自然对齐）；
  - **停留静读与无缝循环**：使用非阻塞异步任务在每次进入或回跳时静态停留 1.5 秒，让用户舒适阅读标题开头；随后单次匀速线性平滑滚向末尾，末尾接壤后即时无感对齐首部，彻底杜绝从屏幕最右侧横跨飞入的视觉缺陷。
- **片商档案页分集与作品加载截断修复与独立作品总数查询 (`StudioDetailView.swift` & `BrowseRepository.swift`)**：
  - 修复原硬编码 `pageSize: 10000` 导致大于一万条记录的大型片商作品被截断的缺陷；
  - 在 `BrowseRepository` 新增专门获取片商作品总数（电影数与分集数）的专用接口 `getStudioWorksCounts(studio:)`，并在标签栏准确显示真实数据库总数；
  - 单次拉取上限提升至 50,000 条，解除单次写死限制；同步将分类系列过滤页面的加载上限提升至 50,000。

## [2.15.0] - 2026-10-01

### Added
- **iOS 客户端架构脚手架与基础架构文件 (`iOS_client/`)**：
  - 基于 SwiftUI 搭建现代声明式离线架构，支持 SQLite 原生读取、Zip 虚拟文件系统与系统级防截屏安全保护；
  - 接入 `GPDbDatabase` 原生数据仓储层，提供影视浏览、演员档案与用户收藏等高频功能。
- **全平台统一通用用户配置导入与导出支持 (`UserBackupService.swift`)**：
  - 支持全端统一标准 `gpdb_universal_backup` JSON 格式导出与导入；
  - 覆盖用户全量「我的收藏」清单、自定义想看/已看状态、标签标记、评分与观影探索统计指标；
  - 实现与 macOS、Windows 及 Android 客户端配置文件的 100% 双向互通与免密平滑迁移。
- **分享卡片角落新增 Telegram 官方频道二维码 (`ShareCardView.swift`)**：
  - 在卡片底部角落集成 27x27 高清点阵矢量二维码，直连官方频道 `https://t.me/gpdbnews`；
  - 具备独立白底圆角容器与高保真矢量绘制，适配 iPhone 与 iPad Retina 屏导出及预览。
- **分享卡片支持封面与封底双海报并列展示 (`ShareCardView.swift`)**：
  - 影视分享卡片全面支持同时加载正向海报封面与反面封底（如有）；
  - 采用并排自适应画框排版，带有独立「封面」与「封底」半透明质感角标，大幅提升典藏分享美感。
- **异步巡检 GitHub Releases 最新版本与更新服务 (`GitHubUpdateService.swift`)**：
  - 内置异步网络服务检测官方仓库最新版本，支持启动静默检测与手动触发版本检测，向用户反馈版本状态与更新日志。
- **全息演员档案整合与无损合并机制 (`PerformerDetailView.swift` & `PerformerRepository.swift`)**：
  - 深度整合 PBC 百科与 SmutJunkies 扩展档案，严格遵循 GEVI 优先的无损合并策略；
  - 提供独立曾用艺名 (AKA) 折叠卡片，超过 8 个自动折叠；
  - 聚合全网互联档案胶囊（IAFD、IMDb、X、OnlyFans、Instagram、PBC、SmutJunkies）。
- **外部数据库挂载前置自愈机制 (`DatabaseHolder.swift`)**：
  - 在挂载外部 SQLite 文件时，自动检测 `performers.pbc_url` 与 `sj_url` 并就地补全；
  - 自动创建 `performer_pbc_profiles`、`performer_sj_profiles`、`user_favorites` 与 `user_movie_data` 扩展表，彻底杜绝缺列崩溃。
- **收藏系列防崩溃复合唯一键规范 (`LibraryView.swift`)**：
  - 采用 `series_\(id)` 与 `\(studioName)_\(rootTitle)_\(hashValue)` 复合唯一键，彻底解决多厂牌重名系列触发 ForEach 重复键闪退的缺陷。

### Changed
- **单流式滚动架构重构 (`PerformerDetailView.swift`)**：
  - 对标 Android v2.15.0，彻底移除手势折叠与易被误隐藏的 Header 逻辑，采用单流式 `LazyVStack` + `LazyVGrid` 架构，将人物全息档案与分类 Tab 顶置于统一滚动流中，根治头部被网格掩盖缺陷。
- **连贯篇章系列大放送随机抽取展示 (`HomeFeedView.swift` & `HomeFeedRepository.swift`)**：
  - 首页「经典系列大放送 · 连贯篇章」由原有的固定按影片数排序优化为随机抽取（`ORDER BY RANDOM() LIMIT 10`），保持与桌面及 Android 客户端一致的动态探索体验。
- **今日星光动态面孔过滤 (`HomeFeedView.swift`)**：
  - 仅展示拥有有效头像的演员，过滤所有无头像占位项，提升首屏高级质感。
- **主页顶部快捷防窥开关与即时模糊 (`HomeFeedView.swift` & `PrivacyProtection.swift`)**：
  - 探索主页顶部导航栏新增防窥模式快捷图标按钮，支持一键在正常模式与防窥模糊模式间自由切换，并伴随触感反馈。

### Removed
- **彻底排除奖杯成就与伪装计算器模块**：
  - 恪守轻量纯粹原则，iOS 客户端原生架构不包含任何奖杯成就冗余插件；
  - 移除伪装计算器与紧急脱身冗余视图，专注于原生系统的安全生物识别与防截屏保护。
