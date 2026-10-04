# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.18.0] - 2026-10-04

### Added
- **片商库现代化展示台 (Studio Shelf Grid) 重构 (`StudioListScreen.kt`, `StudioListViewModel.kt`, `GpdbNavigation.kt`)**：
  - **自适应展示台网格**：废弃单调的纯文字列表，重构为对齐 macOS 设计语言的自适应网格展示台（`GridCells.Adaptive(minSize = 150.dp)`）；
  - **宽画幅 Logo 展台**：每个片商卡片顶部配备独立画幅的 Logo 展台容器，通过 `GpdbAsyncImage` 居中完整缩放展示（`ContentScale.Fit`）；当未配置 Logo 或离线图片缺失时，自动优雅降级为品牌首字母渐变彩色微标；
  - **双语智能呈现**：优先展现中文译名，副标展现浅灰英文原名，结合既有的拼音/字母排序与中英文双向即时模糊检索。
- **片商档案详情沉浸式全景 Banner 与 Logo 徽章 (`StudioDetailScreen.kt`, `StudioDetailViewModel.kt`)**：
  - **全景沉浸 Banner 背景**：当片商档案存在 `bannerUrl` 时，在详情头部铺设全景 Banner，底层叠加 14dp 环境光模糊层与双向羽化渐变遮罩（左侧水平遮罩与底部垂直遮罩），消除突兀边缘，赋予前景文字与徽章通透高对比度；无 Banner 时自动适配典雅纯色渐变背景；
  - **大画幅 Logo 徽章卡片**：72dp 浮动圆角卡片，搭载精致边框与阴影，居中呈现厂牌 Logo，无 Logo 时展现首字母渐变徽章；
  - **发行统计胶囊**：动态呈现该厂牌发行作品数与分集数统计胶囊。
- **自适应取色沉浸式 Logo 展台容器 (`AdaptiveLogoContainer.kt`, `StudioListScreen.kt`, `StudioDetailScreen.kt`)**：
  - **智能留白吸色填充**：针对不同厂牌 Logo 比例、背景格式各异（纯黑底、纯白底、透明底、彩色底）在容器中产生的留白与黑边缝隙问题，引入边缘 12 点采样与极速调色板分析算法；容器背景色动态与 Logo 边缘色 100% 融合，消除「框中框」接缝；
  - **品牌强调色微光边框**：从 Logo 内部网格智能提取最高饱和度品牌强调色，为容器赋予高贵轻奢的微光边框（如 RFC 艳红、NextDoor 铭黄、Eurocreme 青蓝、CorbinFisher 金黄）；
  - **硬件位图兼容与内存 LRU 缓存**：全面兼容 Android 8.0+ `Hardware Bitmap`，后台协程异步解耦采样运算并接入 300 容量全局 LRU 缓存，配合 280ms 柔和色彩插值动效，确保 120Hz 高刷新率滑动零掉帧。
- **片商官方网站直链入库与系统浏览器外跳联动 (`StudioDetailScreen.kt`, `StudioEntity.kt`, `GpdbDatabase.kt`, `I18n.kt`)**：
  - **官方网站直跳按钮**：在片商档案页头部呈现「官方网站」操作芯片（配以地球图标 `Icons.Default.Language`）；
  - **智能协议自适应与外部唤起**：自动感知协议前缀并自适应补齐 `https://`，轻触调用系统默认外部浏览器打开；长按支持一键复制网址至剪贴板并弹出 Toast 提示；
  - **全球 7 种语言完整国际化**：在 `I18n.kt` 中补齐中文简繁、英语、日语、意大利语、西班牙语、德语的 `'studio.officialWebsite'` 与 `'studio.copiedWebsite'` 国际化词条。

### Changed
- **Room 架构升级至 Schema v6 与底层物理兼容自愈 (`GpdbDatabase.kt`, `StudioEntity.kt`)**：
  - `StudioEntity` 新增 `websiteUrl: String?` 与 `siteId: Long?` 字段映射，声明 `idx_studios_site_id` 索引；
  - Room 升级至 Schema v6，实现并注册 `MIGRATION_5_6` 及全量历史版本至 6 的迁移链路；
  - 加固 `ensureSchemaCompatibility`：在 Room 连接前通过底层原生 SQLite 自动检测并动态补齐 `website_url`、`site_id` 列及索引，并同步适配 `studios_schema_fix` 热重建逻辑，确保外部数据库挂载 100% 稳定兼容。
- **离线图片路径映射统一归一化 (`MovieEntity.kt`)**：
  - 优化 `toImageCachePath()` 正则解析：将 `images/logos/` 与 `images/banners/` 统一路由至本地物理目录 `image_cache/Logos/`，无缝支持离线直读。
- **底部导航栏及相关文案精简 (`I18n.kt`, `LibraryScreen.kt`, `SettingsScreen.kt`)**：
  - 将底部导航栏「我的收藏」统一精简为「收藏」，使底栏各 Tab（主页、影片、演员、片商、导演、收藏）字数整齐对称，视觉更加干练精简；搜索框与设置提示文案同步对齐。
- **版本号统一升级**：
  - `build.gradle.kts` 版本升级至 `v2.18.0` (versionCode 318)。

### Fixed
- **修复挂载新版 GPDb.db 时 Room 架构校验崩溃 (`StudioEntity.kt`, `GpdbDatabase.kt`)**：
  - 针对桌面端 `c24f9fd` / `02c073f` 提交中在 `studios` 表新增的 `idx_studios_name_nocase` (name COLLATE NOCASE) 索引，补齐 `StudioEntity` 中的 Room 索引声明；
  - 在 `GpdbDatabase.kt` 的全量迁移脚本 (`MIGRATION_x_6`)、启动前热自愈探测 (`ensureSchemaCompatibility`) 及 `studios_schema_fix` 重建流程中补齐 `idx_studios_name_nocase` 索引创建，彻底解决 `IllegalStateException: Pre-packaged database has an invalid schema: studios` 挂载失败异常。

## [2.17.0] - 2026-10-03

### Changed
- **影片档案海报展示深度优化与视觉纯化 (`MovieDetailScreen.kt`, `GpdbAsyncImage.kt`)**：
  - **物理画幅零黑边贴合自适应 (`AdaptivePosterCard`)**：彻底修复由于海报固定高度或尺寸未关联画幅比例导致海报被放在上下有空白的暗色框里的问题。新增 `AdaptivePosterCard` 组件，利用 Coil `onSuccess` 回调动态感知图片物理分辨率，通过 `aspectRatio(ratio, matchHeightConstraintsFirst = true)` 动态自适应贴合，使卡片圆角与微光边框紧紧包裹海报物理边缘，杜绝任何 letterboxing 上下/左右留白；
  - **彻底移除角标与常驻放大镜**：移除海报左上角动态标记的「正封面」「封底」或「#N」徽章，以及右上角常驻缩放镜图标，还原最纯粹沉浸的海报艺术画幅展示；海报仍支持轻触直接唤起全屏手势无级缩放灯箱 (`ZoomableImageDialog`)；
  - **单封面居中与多海报横向流式并列呈现**：单封面时以居中艺术卡片全幅呈现；多海报时在横向平滑滚动流中以等高自适应比例优雅平铺展示。
- **操作与版本号对齐**：
  - 演员档案与厂牌档案操作按钮交互对齐，版本号统一升级至 `v2.17.0` (versionCode 317)。

### Fixed
- **修复影片仅一张海报时显示封底空白框架的错误与容错自愈 (`MovieDetailScreen.kt`)**：
  - 针对历史数据库中部分记录 `cover_back` 为空白字符串或海报资源在离线 ZIP 包中缺失的场景，构建数据过滤与运行时 `effectiveCovers` / `onError` 动态自愈机制；
  - 当封底或附加海报文件在本地包中缺失触发加载失败时，自动从展示列表中剔除，杜绝渲染出暗色空框；若仅剩主封面有效，界面自动平滑降级为单图居中呈现。

## [2.16.7] - 2026-10-03

### Added
- **181 家核心厂牌历史档案专栏与风格深度解析 (`StudioDetailScreen.kt`, `StudioDetailViewModel.kt`)**：
  - 在片商详情页新增大画幅「厂牌历史档案与风格深度解析专栏」Material 3 卡片，配备 `MenuBook` 典籍图标与 `AutoAwesome` 深度解析标签；
  - 完整呈现 181 家核心厂牌创立年代、创始人背景、美学流派与文化演变深度历史档案；
  - 支持正文 `SelectionContainer` 自由复制选择，超过 100 字符时智能折叠为 3 行并提供「展开全文阅读 / 收起专栏」按钮，兼顾阅读体验与作品列表的快速访问。
- **全站片商中英双显与双向智能模糊检索 (`StudioListScreen.kt`, `StudioListViewModel.kt`, `BrowseRepository.kt`, `StudioDetailScreen.kt`)**：
  - 中文模式下片商列表项与详情顶栏标准呈现「主标题中文、副标题浅灰英文原名」，兼顾本土化阅读亲和力与全球影视索引精准度；
  - 列表搜索框全面增强中英文双向模糊检索，支持直接输入中文译名（如「猎鹰」）或英文原名（如「Falcon」）极速直达。
- **Room 架构版本升级至 Schema v5 与物理表自愈加固 (`GpdbDatabase.kt`, `StudioEntity.kt`, `StudioDao.kt`)**：
  - Room 升级至 Schema v5，新增 `StudioEntity` 实体与 `StudioDao` 数据访问层；
  - 补全 `MIGRATION_4_5` 及全量历史版本至 5 的平滑迁移逻辑；
  - `ensureSchemaCompatibility` 在 Room 挂载前使用原生 SQLite 无损补齐 `studios` 表结构（`name`, `name_zh`, `description_zh`, `logo_url`, `banner_url`）与 `idx_studios_name`、`idx_episodes_studio` 核心索引，杜绝因外部数据库缺失表导致挂载失败。

### Fixed
- **Room 外部数据库挂载 Schema 校验兼容性修复 (`GpdbDatabase.kt`, `StudioEntity.kt`, `EpisodeEntity.kt`)**：
  - 修复 `studios` 表 `updated_at` 字段在底层 SQLite 为 `TIMESTAMP` 时导致 Room 亲和性比较失败（NUMERIC vs TEXT）的校验异常；
  - 为 `StudioEntity.updatedAt` 补充 `@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")` 注解，与底层 SQLite DDL 保持 100% 对齐；
  - 加固 `ensureSchemaCompatibility`：自动探测外部 SQLite 历史 `studios.updated_at` 物理类型，若为 `TIMESTAMP` 则自动无损热升级为 `TEXT DEFAULT CURRENT_TIMESTAMP`，确保 0 数据丢失；
  - 修复 `EpisodeEntity` 与 `episodes` 表索引一致性，补充 `idx_episodes_studio` 声明。

### Optimized
- **演员档案页精简与视觉纯化：彻底移除「表演标签」板块 (`PerformerDetailScreen.kt`)**：
  - 移除原人物档案板块中堆叠的「表演标签:」流式芯片组，消除杂乱演出标签对个人档案核心生理特征与百科人物生平小传的视觉干扰，整体排版更加精致干练。
- **厂牌详情页专栏重构与「厂牌介绍」流式全文展示 (`StudioDetailScreen.kt`)**：
  - 将原「厂牌历史档案与风格深度解析专栏」精炼改名为「厂牌介绍」，移除冗余的「深度解析」标签；
  - 彻底解除宽度与折叠限制：废弃原外部固定容器与 3 行折叠限制，将「厂牌介绍」与作品分类 Tab 统一作为流式 Item 嵌入 `LazyVerticalGrid`（作品）与 `LazyColumn`（分集），整页单流滚动，自然舒展全文；
  - 补充作品与分集空状态优雅提示。
- **影片档案页海报展示模式重构：对齐 macOS 多图自适应同时平铺呈现 (`MovieDetailScreen.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `AppSettingsRepository.kt`, `I18n.kt`)**：
  - **移除「海报展示模式」设置项**：彻底废弃单一海报翻页设置选项及相关状态弹窗，精简设置交互；
  - **默认采用 macOS 同款自适应多海报平铺模式**：
    - 单封面时：采用居中大画幅艺术卡片全幅呈现；
    - 多封面时：采用平铺横向自适应流式展示（`LazyRow`），直观同时呈现正封面、封底及扩展海报，每张保留原生比例，左上角徽章动态标注「正封面 / 封底 / #N」，右上角常驻缩放图标，点击直达手势无级缩放灯箱 (`ZoomableImageDialog`)；
    - 底层融合首图氛围毛玻璃高斯模糊过渡与背景渐变。
- **剧情简介通栏展开与核心操作按钮矩阵化收拢 (`MovieDetailScreen.kt`)**：
  - **剧情简介通栏展示**：`TranslationSection` 独占全宽，赋予长篇剧情梗概、双语对比与 AI 翻译操作极佳的阅读空间；
  - **核心操作按钮矩阵化收拢 (Action Matrix Panel)**：将「BT 磁链」「BFTV 检索」「Google 检索」「分享卡片」「收藏电影」等 5 项核心按钮集中规整为操作芯片组（`SuggestionChip` 与 `FilterChip`），置于元数据标签正下方；
  - **移除冗余评星打分**：精简页面垂直杂讯，与 macOS 桌面端标准体验完全对齐。
- **应用图标方案精简至 Scheme A 与 Scheme D (`SettingsScreen.kt`)**：
  - 精简设置页伪装图标方案，重点聚焦于「方案一：经典典藏蓝 (Scheme A · 默认)」与「方案二：黑曜石金 / 极简 (Scheme D)」，与桌面端多端一致。

## [2.15.0] - 2026-10-01

### Fixed
- **演员档案页架构重构与网格遮盖缺陷根治 (`PerformerDetailScreen.kt`)**：
  - 彻底移除不稳定的 `nestedScrollConnection` 动态滑动折叠与易被持久化隐藏的 `isHeaderVisible` 机制；
  - 采用 Jetpack Compose 规范的单流滚动架构，将完整的演员人物档案板块（头像、生理特征、PBC 维基人物小传、互联档案、外链检索按钮、AKA 艺名）与「出演影片」「出演分集」分类 TabRow 作为顶置 Item 直接嵌入 `LazyVerticalGrid` 与 `LazyColumn`；
  - 彻底解决了进入演员档案页时人物信息板块和出演影片/出演分集按钮被掩盖在网格后面或折叠消失、界面只显示孤立影片网格的严重交互 Bug。
- **P0 级外部数据库挂载失败根治（Room 架构迁移与物理表自愈）**：
  - 在 [`GpdbDatabase.kt`](file:///Users/joel/iCloud%20Drive%20(Archive)/Documents/antigravity/游戏库管理App/GEVI_Offline_Database/android_client/app/src/main/java/com/gpdb/android/data/db/GpdbDatabase.kt) 中将 Room 数据库版本由 3 升级至 4，补全 `MIGRATION_3_4` 及所有历史版本到 4 的迁移脚本（`ALTER TABLE performers ADD COLUMN sj_url TEXT`）；
  - 实现挂载前置自愈机制 `ensureSchemaCompatibility`：在 Room 建立连接与执行架构校验之前，使用原生 SQLite 直连检查并就地无损补齐缺失列（`pbc_url`, `sj_url` 以及扩展表 `performer_pbc_profiles`, `performer_sj_profiles`）；
  - 彻底解决了由于外部数据库缺乏 `sj_url` 列触发 `IllegalStateException: Migration didn't properly handle: performers` 导致数据库挂载失败的严重缺陷。
- **P0 级致命闪退根治：收藏系列（LibraryScreen）点击瞬间 Crash 修复**：
  - 在 [`LibraryScreen.kt`](file:///Users/joel/iCloud%20Drive%20(Archive)/Documents/antigravity/游戏库管理App/GEVI_Offline_Database/android_client/app/src/main/java/com/gpdb/android/ui/library/LibraryScreen.kt) 中将 `LazyVerticalGrid` 网格项键值由原有的 `it.rootTitle` 升级为全局复合唯一键：`key = { it.id ?: "${it.studioName}_${it.rootTitle}_${it.hashCode()}" }`；
  - 在 [`BrowseRepository.kt`](file:///Users/joel/iCloud%20Drive%20(Archive)/Documents/antigravity/游戏库管理App/GEVI_Offline_Database/android_client/app/src/main/java/com/gpdb/android/data/repository/BrowseRepository.kt) 查询语句中补充 `SELECT DISTINCT s.*`，防止由于关联匹配条件产生重复数据项；
  - 彻底根治了底层数据库中不同厂牌存在重名系列（如 `Auditions`、`Bad Boys`、`Bareback` 等）时触发 `IllegalArgumentException: Key was already used` 闪退崩溃的重大缺陷。
- **系列路由标题解析格式化优化**：
  - 在 [`FilteredMovieListScreen.kt`](file:///Users/joel/iCloud%20Drive%20(Archive)/Documents/antigravity/游戏库管理App/GEVI_Offline_Database/android_client/app/src/main/java/com/gpdb/android/ui/browse/FilteredMovieListScreen.kt) 中对复合系列标题进行正则拆分与格式化，剔除 `|||` 内部标记，友好展示为「系列名称（厂牌名）」。

### Added
- **全平台统一通用用户配置与数据备份/恢复机制 (`UserRepository.kt` & `UserAnalyticsRepository.kt` & `SettingsViewModel.kt` & `SettingsScreen.kt`)**：
  - 设置页中新增「用户配置与数据备份/迁移 (跨设备通用 JSON)」功能行，接入 Android SAF (Storage Access Framework) 系统文件存储向导；
  - 导出用户在 SQLite 数据库中的全部「我的收藏」条目与 DataStore 中的全部专注统计、探索计数及活跃天数等；
  - 导出格式采用统一规范 `gpdb_universal_backup` JSON，跨平台无障碍兼容导入恢复。
- **分享卡片支持封面与封底双海报并排展示 (`ShareCardDialog.kt` & `MovieDetailScreen.kt`)**：
  - 分享卡片全面支持同时加载正向海报封面与反面封底（如有）；
  - 采用 Compose 原生优雅并列卡片排版，左右标明「封面」与「封底」半透明质感胶囊，并无缝适配双离屏模糊保护与超清位图合成。
- **分享卡片角落新增 Telegram 官方频道二维码 (`ShareCardDialog.kt`)**：
  - 卡片底栏集成 27x27 高清点阵二维码，直连官方频道 `https://t.me/gpdbnews`；
  - 采用白底圆角容器与原生 Compose Canvas 矢量栅格绘制，无论导出还是分享均呈现高精画质。
- **主页顶部快捷防窥开关与即时模糊 (`HomeFeedScreen.kt`)**：
  - 探索主页顶部标题栏新增「防窥模式」快捷图标按钮，支持一键在正常模式与防窥模糊模式间自由切换，并伴随即时 Toast 反馈；
  - 开启后全库海报、封面、剧照及演员头像立即覆盖高级高斯模糊层。
- **设置页新增「检查新版本更新」与在线升级 (`SettingsScreen.kt` & `AppUpdateManager.kt`)**：
  - 设置「关于与软件更新」板块中提供手动「检查新版本更新」入口，支持随时异步调用 GitHub Releases API；
  - 检查中呈现加载动画，最新版反馈「当前已是最新版本」，发现新版则拉起拟态更新弹窗供用户确认后下载 APK 并自动拉起系统安装。
- **启动静默巡检 GitHub Releases 与内置增量升级 (`AppUpdateManager.kt` & `AppUpdateDialog.kt`)**：
  - 启动 2.5 秒后异步巡检 GitHub API 最新版本，对比版本号，并在有新版本发布时弹出拟态更新弹窗；
  - 展示新版特性日志、版本对比以及 APK 文件大小；
  - 支持单线程断点进度条下载，并基于 `FileProvider` 安全生成 `content://` 临时权限 URI 自动唤起系统 APK 安装向导；
  - 在 `AndroidManifest.xml` 中补全 `REQUEST_INSTALL_PACKAGES`权限，在 `file_paths.xml` 中完成下载目录安全映射。

### Changed
- **设置项更名：防窥模式 (`SettingsScreen.kt`)**：
  - 将设置「隐私与安全」中的「截屏隐私打码模式」正式更名为「防窥模式」，并与主页顶部防窥开关实时联动。
- **设置页顶部二级胶囊分类切换栏 (`SettingsScreen.kt`)**：
  - 仿照 macOS 桌面端设计，在设置界面顶部引入横向二级胶囊菜单（全部、外观、统计、隐私、AI翻译、数据同步、关于更新）；
  - 切换胶囊标签即时过滤呈现对应板块，大幅降低选项过多的阅读与检索负担。
- **首页「今日星光 · 标志面孔」过滤优化 (`HomeFeedScreen.kt` & `GpdbAsyncImage.kt`)**：
  - 对齐 macOS 桌面端优质呈现标准，在今日星光圆环头像模块中增加动态加载侦测过滤机制；
  - 仅展示拥有真实头像图片且图片加载成功的演员，过滤所有无头像或加载失败的空白占位项，保证视觉高级感。
- **设置页海报排版选择视觉化升级 (`SettingsScreen.kt`)**：
  - 将原有海报排版模式（平铺自适应画廊 vs 3D 景深翻转卡片）的纯文本单选弹窗全面升级为直观的图示卡片式微缩示意图，点击卡片即时切换，所见即所得。
- **连贯篇章系列大放送随机抽取展示 (`HomeFeedRepository.kt`)**：
  - 首页「经典系列大放送 · 连贯篇章」由原有固定按影片数量降序（`ORDER BY movie_count DESC`）改为随机抽取（`ORDER BY RANDOM() LIMIT 10`），提升每次打开时的发现乐趣。
- **分享卡片全维度自适应排版与纯粹流光背景 (`ShareCardDialog.kt`)**：
  - 背景精简优化：统一保留视觉表现最优秀的「流光」动态自适应光晕背景；
  - 分集剧照 16:9 宽屏无拉伸：分集卡片海报框比例自适应切换为 1.777f (16:9)，人物面部不再挤压形变；
  - 演职员名单全量换行展示：移除 `take(5)` 与截断限制，完整呈现全部主演；
  - 剧情简介全量完整呈现：移除原有的 `take(130)` 与 `maxLines = 3` 限制，卡片自动向下弹性膨胀并生成高保真长图。

## [2.14.0] - 2026-09-30

### Added
- **Android 原生端演员档案页深度整合 PBC 百科全维度资料 (`PerformerDetailScreen.kt`)**：
  - **严格冲突优先级与已有数据无损原则 (GEVI-First Non-Destructive Resolution)**：
    - 生理规格（身高、体重、尺寸规格、体型、肤色、包皮、发色、瞳色、体毛、胡须、纹身）：严格执行 `p.field ?: pbc.field`。**只要 GEVI 原始字段有值，100% 保持 GEVI 不变**；仅当 GEVI 字段缺失时，才无缝由 PBC 权威数据填补，绝不丢失已有数据。
    - 艺名与别名（Aliases）：执行去重并集合并，聚合呈现 GEVI 与 PBC 的全量艺名表。
    - 本名（Birth Name）：若与艺名不同，在作品数旁以次级柔和色调呈现 `本名: xxx`。
    - 出道年份（Career Start）：清晰呈现 `出道: 2008年`。
    - 活跃状态微标（Career Status）：顶部直观展示绿色脉冲微光徽标 `活跃中 Active` 或灰色 `已退役 Retired`。
    - 出生日期与年龄（Birth Date & Age）：以属性胶囊形式规范呈现 `出生: 1983-02-11 (43岁)`。
    - 星座与族裔（Astrology & Ethnicity）：内置标准化汉化映射，如 `星座: 水瓶座`、`族裔: 白人`。
    - 表演风格特色（Performance Tags）：汉化展示演出标签，如 `无套 (Bareback)`、`内射 (Creampie)`、`双龙` 等。
    - 维基生平人物小传（Bio Card）：极简半透明玻璃拟态卡片呈现人物生平，支持一键点击 `完整词条 ↗` 直达原百科网页。
    - 全网互联档案徽标（Connected Profiles）：底部聚合展示 `IAFD ↗`、`IMDb ↗`、`X (Twitter) ↗`、`OnlyFans ↗`、`Instagram ↗` 快捷胶囊。
    - 操作栏快捷入口：操作区部署 `PBC 百科 ↗` 快捷入口，一键呼出浏览器查看完整词条。
  - **界面一致性与简洁性保障**：
    - 严格复用 GPDb 的 Material 3 暗色调 / Zinc / Accent 配色系统与卡片材质；
    - 纯粹条件渲染：对于尚未收录 PBC 档案的普通演员，界面保持原汁原味的极简 GEVI 视图，不产生任何空白卡片或未匹配占位符。
- **全新曾用艺名 / 别名 (AKA) 独立徽章栏与动态折叠卡片 (`PerformerDetailScreen.kt`)**：
  - 移至人物主卡片下方独立全宽展示，采用 Surface 玻璃拟态与标签设计，支持别名总数角标提示；
  - 智能折叠机制：超过 8 个别名时自动折叠并提供「展开全部 / 收起」按钮，杜绝多艺名演员档案过度拉长；
  - 聚合作品关联名、PBC 别名与 SmutJunkies 别名并执行不区分大小写的去重合并。
- **SmutJunkies 演员资料与直达外链支持**：
  - `PerformerEntity` 新增 `sj_url` 字段映射；
  - `PerformerDetailViewModel` 自动读取 `performer_sj_profiles` 扩展表并提取 `sj_url` 与别名；
  - 操作栏快捷区新增亮粉色 `SmutJunkies ↗` 外链按钮，一键直达 SmutJunkies 官方主页。
- **Room 数据库版本平滑升级与数据层修复**：
  - Room 数据库升级至 Version 3，提供 `MIGRATION_2_3` 平滑迁移方案，新增 `performers.pbc_url` 字段与 `performer_pbc_profiles` 实体表结构。
  - `SandboxDatabaseInitializer` 自动保证初始沙盒环境包含 PBC 结构支持。
  - **修复 `loadPbcProfile` 查询字段与 `performer_pbc_profiles` 表结构不匹配隐患**：对齐 `penis_size`、`roles_json`、`social_links_json`、`external_ids_json`、`performance_tags` 等字段名，确保真机环境下 100% 成功读取展示 PBC 百科全息数据。

## [2.13.0] - 2026-09-29

### Added
- **截屏隐私打码模式 (Screenshot Privacy Blur Mode)**：
  - **手动启用与细分控制**：在设置「隐私与安全」中新增「截屏隐私打码模式」主开关，并支持独立勾选「模糊图片」（全局高斯模糊影片海报、封面、剧照与演职员头像）与「模糊介绍文字」（全局高斯模糊剧情简介与敏感文字描述），专用于安全截图与社媒分享。
  - **全局响应式渲染架构**：采用 `LocalPrivacyBlur` CompositionLocal 结合 DataStore 响应式流，无侵入式作用于 `GpdbAsyncImage`、`TranslationSection`、`PerformerDetailScreen` 及 `ZoomableImageDialog`，全屏界面秒级即时生效。
- **影片与分集档案页卡片分享功能 (Aesthetic Share Card Generator)**：
  - **一键快捷生成**：影片详情页 (`MovieDetailScreen`) 与分集档案页 (`EpisodeDetailScreen`) 顶部操作栏新增「卡片分享」按钮，一键调出高颜值分享卡片。
  - **自适应海报流光高斯背景**：通过采样算法从封面自适应提取主导色相，辅以暗色渐变与高精度高斯模糊，营造如同 Apple Music / Spotify 般通透现代的艺术级光晕质感。
  - **精简而全面的元数据卡片**：集成高清封面/缩略图、中英双语标题、发行年份/日期、片商、导演、时长、评分、分级、参演演员阵容以及典雅引用样式的剧情简介。
  - **分享前安全隐私脱敏**：弹窗内嵌即时交互开关，支持分享前按需单独或同时勾选「模糊海报」与「模糊文字」，彻底免除剧透与隐私顾虑。
  - **无损双重导出**：采用 Compose 1.8 `rememberGraphicsLayer` 硬件加速位图捕获引擎，支持一键无损保存至系统相册 (`MediaStore` API，兼容 Android 10+ 分区存储与早期版本)，以及通过 `FileProvider` 唤起 Android 原生分享面板（直接分享至微信、QQ、Telegram、X 等）。
- **全生态版本号统一提升至 v2.13.0**。

## [2.12.0] - 2026-09-29

### Added
- **全新独立 Windows 客户端工程体系 (`/Windows_client`)**：
  - 架构完全独立解耦：在项目根目录建立专有 `/Windows_client` 文件夹，将 Windows 平台特定的 Tauri 2 配置、打包管线、构建脚本与界面微调收拢于单一工程内，彻底避免与 macOS (`desktop_client`) 及 Android (`android_client`) 的开发环境产生任何文件侵染。
  - 专属开箱即用构建套件：提供 `build_windows.ps1` (PowerShell) 与 `build_windows.bat` (批处理) 双脚本，自动检查 Node.js 与 Rust 工具链版本，一键自动拉取依赖并编译生成 Windows 安装程序。
- **Windows Python 运行环境智能探测链 (Multi-Source Python Resolution Engine)**：
  - 针对 Windows 平台下 Python 安装形态多样的痛点（官方包、微软商店沙盒、Conda、自定义路径），实现多级分层智能探测。

### Changed
- **跨平台 CI/CD 自动化构建矩阵解耦 (`.github/workflows/release.yml`)**：
  - 构建矩阵引入 `client_dir` 参数，macOS 节点绑定 `desktop_client`（产出 `.dmg` / `.app`），Windows 节点绑定 `Windows_client`（产出 NSIS `.exe`），两端缓存键与依赖安装完全独立隔离。
- **全生态版本号统一演进至 v2.12.0**：
  - 保持 Android、macOS 与 Windows 三端同步版本迭代演进。

### Fixed
- **Windows 子进程控制台黑框闪烁彻底消除 (CREATE_NO_WINDOW Console Suppression)**：
  - 全面引入 `#[cfg(target_os = "windows")]`，并在所有子进程调用前统一注入 `creation_flags(0x08000000)` (`CREATE_NO_WINDOW`)。

## [2.11.1] - 2026-09-29

### Fixed
- **“影片”Tab 与全局搜索结果为空彻底修复 (Multi-Field Resilient Search Engine)**：
  - 根因定位：此前 `SearchRepository` 直接依赖 FTS5 虚拟表（`movies_fts` 与 `performers_fts`）。在部分 Android 设备环境、模块未启用的 SQLite 或沙盒初始化数据库中，因缺少 FTS5 虚拟表直接抛出 `SQLiteException: no such module/table: movies_fts`，被 ViewModel 捕获后导致影片和演员列表始终返回为空；且 unicode61 分词机制对中文支持有限，且原逻辑未检索出演演员及片商。
  - 核心修复：
    1. 实现 **三级防御降级检索架构**：FTS5 高速召回 -> 自动捕获异常并无缝降级至标准 SQL 多字段联合检索 -> Room DAO 兜底。
    2. 深度多维度联合召回：单次搜索同时跨越英文原名 (`title`)、中文译名 (`title_zh`)、片商名 (`studio_name`)、导演名 (`director_name`)、中文简介 (`description_zh`)、出演演员名 (`movie_performers.performer_name`) 及影片 ID 检索。
    3. `SearchViewModel` 升级为响应式动态绑定，监听 `DatabaseHolder.isReadyFlow`，彻底解决数据库连接与仓库未就绪造成的检索悬挂与空结果。

### Changed
- **“主页”探索频道移除搜索按钮 (Home Feed TopBar Streamline)**：
  - “主页”(`HomeFeedScreen`) 顶部操作栏正式移除搜索放大镜按钮，保留刷新控制；搜索职责收归各对应专属频道（影片、演员、片商、导演、我的收藏），主页浏览交互更加聚焦沉浸。

## [2.11.0] - 2026-09-29

### Added
- **全新「导演」主频道与收藏导演体系 (Directors Tab & Favorites)**：
  - 底部导航栏新增与 macOS 客户端完全对齐的“导演”Tab (`Screen.Directors`)，直通全库导演全景索引浏览。
  - 导演列表支持姓名即时搜索、作品总数排序与姓名拼音排序切换，卡片清晰展示导演姓名、总作品数及代表作。
  - “我的库”专属二级筛选新增“收藏导演”选项卡 (`LibraryTab.FAV_DIRECTORS`)，支持一键查看、收藏与取消收藏心仪导演。
  - 导演作品集页面 (`FilteredMovieListScreen`) 增加导演收藏快捷心形切换，打通跨维度收藏数据闭环。
- **全新用户零配置开箱体验与本地私有沙盒影库 (Zero-Config Onboarding & Local Sandbox Database)**：
  - 针对只安装了 App、尚未准备外部 `GPDb.db` 或 15GB `GPDb_Images.zip` 的全新用户，在启动引导页 (`SetupScreen`) 推出「新手开箱 / 全新体验：一键创建本地沙盒影库」。
  - 实现 `SandboxDatabaseInitializer`，在内部沙盒 `/data/user/0/<package_name>/files/gpdb_database/` 自动化秒级初始化标准 SQLite 数据库与 Room 所需全套结构表（包含 movies, performers, movie_performers, episodes, episode_performers, directors, movie_directors, series_collections, user_favorites, user_movie_data, scraped_entries 等）。
  - **零外部存储权限依赖**：全新用户无需开启系统“所有文件访问权限”即可直接进入 App，零门槛快速体验完整界面与内置在线刮削更新。
  - 支持后续无缝导入或重新挂载电脑端导出的外部大文件完整影库。
- **多语言国际化系统深度对齐 (7-Language Multi-Language Engine)**：
  - 彻底与 macOS 客户端同步，支持 7 大主流语言及系统自适应跟随：简体中文 (`zh-CN`)、繁体中文 (`zh-TW`)、英语 (`en`)、日语 (`ja`)、意大利语 (`it`)、西班牙语 (`es`)、德语 (`de`) 以及系统默认 (`system`)。
  - 引入响应式 `LocalAppLanguage` CompositionLocal 与 `I18n.string(key)`，彻底修复“在设置中切换语言后界面不实时响应”的问题，切换语言瞬间全局 UI 毫秒级重绘，无需重启应用。
- **macOS 六大专属主题与自适应色板同步 (macOS Theme Palette Synchronization)**：
  - 同步 macOS 客户端 6 套高质感色彩主题：毛玻璃暗色 (`glass-dark`)、毛玻璃亮色 (`glass-light`)、经典深黑 (`classic-dark`)、经典浅白 (`classic-light`)、轻奢黑紫 (`my-dark`)、轻奢亮彩 (`my-light`)，以及跟随系统的自适应模式 (`auto`)。
  - 设置页提供直观的可视化调色板圆点迷你色样预览（Mini Color Swatches），点击即刻全局切换，支持原生 Android 12+ Dynamic Color（Material You）动态色彩联动。
- **100% 本地隐私使用统计看板 (Local Usage Analytics Dashboard)**：
  - 新增 `UserAnalyticsRepository` 与 `AnalyticsScreen`，全天候本地精准记录并呈现：
    - 累计专注浏览时长（秒级精确心跳统计与后台挂起即时结算）。
    - 浏览量统计：影片浏览总数、分集浏览总数、演员档案查看数。
    - 交互指标：全库搜索次数、收藏条目总数、个人星级打分次数、AI 双语翻译触发次数。
    - 习惯洞察：深夜夜猫子模式统计 (23:00~05:00 浏览次数) 与活跃连续天数。
  - 坚持 100% 本地 SQLite / DataStore 存储，不向任何第三方或云端发送任何遥测字节；提供一键抹除重置数据功能。

### Changed
- **演员档案生理特征默认完整展示 (Default Full Display of Performer Physiological Traits)**：
  - 演员档案页彻底移除“展开详细生理特征”/“收起详细特征”折叠交互按钮及冗余状态控制逻辑。
  - 默认以自适应流式胶囊标签（`FlowRow`）全量展示眼睛颜色、发色、体型、胡须、体毛、肤色、特殊生理特征与纹身详情，省去多余点击操作，信息获取更加直观畅快。
- **导航标签文案深度对齐 macOS 规范 (Navigation Tab Labels macOS Alignment)**：
  - 一级导航栏中将“影片库”、“演员库”、“片商库”精简为“影片”、“演员”、“片商”（去掉“库”字）。
  - 一级导航栏中将原“我的库”更名为“我的收藏”（对齐 macOS 客户端 `nav.favorites`）。
  - “影片”主频道内部二级 Tab 菜单中将“全部影片”正式更名为“长片”（对应分集与系列，分类层级更清晰严谨）。
- **设置页卡片化极简重构 (Streamlined & Categorized Settings Screen)**：
  - 针对原有设置页条目繁杂冗长的问题，重构为 5 个清晰有序的聚合功能卡片：
    1. 视觉外观与主题语言（主题选择器、迷你色样预览、多语言切换、封面展示风格）。
    2. 影库统计与使用洞察（直通全新数据看板、快速清理缓存）。
    3. 高级隐私安全与沙盒防护（多任务防窥、应用锁、紧急脱身、沙盒隔离）。
    4. AI 智能翻译引擎（OpenAI/Gemini/DeepSeek 翻译配置与测试）。
    5. 数据同步与存储挂载（增量更新检测、定时后台刮削、重新挂载影库目录）。

### Fixed
- **演员档案生理信息全中文自然本地化 (Performer Physiological Attributes Localization)**：
  - 解决“中文界面下演员档案页体貌特征仍显示英文”问题。
  - 新增 `GlossaryHelper` 字典，对 80+ 种眼睛颜色、发色、体毛量、胡须、体型、肤色、特殊生理特征以及高度/重量计量单位实现地道自然的中文双向智能转换。
- **系列集合四宫格拼接封面解析修复 (Series Collage Covers JSON Array Parsing)**：
  - 修复主页“经典系列大放送”与“影片 -> 系列”中四宫格拼接封面无法显示的缺陷。
  - 重构 `SeriesCollageCover`，健壮解析 JSON 字符串数组格式的 `sample_covers`（如 `["cover1.jpg", "cover2.jpg", ...]`），具备单封面回退与占位符兜底。

## [2.10.0] - 2026-09-29

### Added
- **影片海报双方案与全屏手势缩放灯箱 (Poster Display Schemes & Fullscreen Lightbox)**：
  - **用户自定义海报展示方案 (User Preference)**：在“设置 -> 海报与视觉展示”中提供展示模式切换，随心切换 3D 卡片与自适应画廊。
  - **方案一：3D 拟真翻转卡片 (3D Flip Card)**：运用 Jetpack Compose `graphicsLayer { rotationY = ... }` 与摄像机视角距离调节，实现正反面 3D 拟真翻转交互；配备翻转胶囊悬浮按钮与背面元数据指示，并伴随环境光晕（Ambient Glow）背景柔焦投影。
  - **方案二：自适应画廊轮播 (Adaptive Gallery Pager，默认)**：基于 `HorizontalPager` 与 `ContentScale.Fit` 完整保留正反面及变体海报的原始比例，杜绝任何不自然裁切；集成动态胶囊指示器与版本切换。
  - **方案三：全屏双指手势缩放灯箱 (Fullscreen Zoomable Lightbox，内置默认)**：点击任意封面即刻唤起纯黑全屏沉浸式灯箱，支持双指自由缩放（Pinch-to-zoom 1x~5x）、自由平移（Pan）与双击快速复位，海报细节纤毫毕现。
- **时间筛选胶囊栏扩充 (Expanded Date Filter Options)**：
  - 在“影片”和“分集”浏览列表顶部时间筛选胶囊栏中新增 **“上次入库” (`LAST_SCRAPED`)** 与 **“最近7天” (`RECENT_7`)** 快捷选项。
  - “上次入库”通过单次扫描本地最新抓取入库的时间戳（`max(scraped_at) / max(created_at)`），秒级精准锁定最近一批新条目。
  - “最近7天”通过 SQL 动态日期回溯（`datetime('now', '-7 days')`），为高频追更用户提供极速周更视图。
- **系统级隐私安全与沙盒物理隔离 (Privacy Protection & Sandbox Isolation)**：
  - **Linux UID 权限物理沙盒**：强制将图片缓存与网络刮削下载目录完全限定在应用专属内部沙盒目录：`/data/user/0/<package_name>/files/image_cache/`。
  - **系统相册与第三方 App 物理绝缘**：得益于 Android Linux 内核 UID 权限机制，系统 MediaScanner 绝对不会扫描应用私有 `filesDir`，微信、QQ、系统相册、第三方图库及文件选取器完全无权访问，杜绝任何成人图片外泄。
  - **递归 `.nomedia` 动态全覆盖防护**：新增 `PrivacyHelper` 工具集，在应用冷启动及任意图片落盘时，自动对根目录及 `Covers/`、`Episodes/`、`Performers/` 等所有子目录递归注入 `.nomedia` 屏蔽文件。
  - **外部存储权限强管控开关**：在“设置 -> 高级隐私安全与沙盒防护”中新增“允许将图库保存至外部存储”开关，**默认严格保持关闭**；支持手动一键“全量注入 .nomedia 保护”。
- **进阶隐私与安防套件 (Advanced Privacy & Security Suite)**：
  - **防多任务截屏与防窥探 (`FLAG_SECURE`)**：
    - 支持在“设置 -> 高级隐私安全与沙盒防护”中一键开启多任务防窥与防截屏开关。
    - 动态设置 Android 窗口标志位 `WindowManager.LayoutParams.FLAG_SECURE`。
    - 系统多任务切换器（Recent Apps）中只显示黑屏或空白卡片，杜绝身边他人一瞥窥屏；同时在系统层级彻底阻断截屏与录屏。
  - **生物识别与独立 PIN 码应用锁 (Biometric & PIN App Lock)**：
    - 支持设置 4 至 6 位独立数字 PIN 码与指纹/面容生物识别双轨验证体系（集成 `androidx.biometric.BiometricPrompt`）。
    - 支持自定义切出后台超时锁定时间（立即锁定 / 30 秒 / 1 分钟 / 5 分钟），冷启动与超时切回自动触发全屏密码锁遮罩（`AppLockOverlay`）。
    - 包含优雅的 Material 3 拟真键盘、数字圆点指示器、防爆破抖动与触觉振动反馈。
  - **紧急一键脱身与高仿计算器伪装 (Panic Switch & Fake Calculator)**：
    - 引入基于硬件加速度与距离传感器的 `PanicSensorManager`，支持正面朝下扣桌（Face Down）与剧烈晃动（Shake）双姿态手势识别。
    - 提供三级脱身策略可选：
      1. **高仿计算器伪装（推荐）**：瞬间无缝跳转至内置全功能暗黑计算器（`FakeCalculatorScreen`），可进行真实加减乘除运算，他人拿取手机无法察觉异常；用户输入“专属 PIN 码 + 等号”或长按 `C` 键 1.5 秒即可安全解除伪装重返影库。
      2. **极速退回系统桌面**：瞬间调用 `moveTaskToBack(true)` 将应用退至后台并隐藏。
      3. **彻底清退 App 进程**：立即调用 `finishAffinity()` 清退所有活动任务栈。
  - **图标与应用名称伪装 (Launcher Icon & Name Alias)**：
    - 全面激活 4 组 `activity-alias` 与动态包名组件切换机制，支持在设置中一键将桌面 App 图标与应用标题伪装为日常无害工具应用：
      - 原版标识：`GPDb`
      - 伪装方案 1：`极简便签`
      - 伪装方案 2：`常用计算器`
      - 伪装方案 3：`收支记账`

### Fixed
- **首页「主页」发现流空白与异步挂载时序修复 (Home Feed Async Database Race Condition)**：
  - 彻底解决用户反馈“主页仅显示四个统计磁贴、无法渲染发现流”的问题。
  - **根因分析**：由于 `DatabaseHolder` 采用后台 IO 协程异步热挂载数据库，`HomeFeedViewModel` 在进入页面瞬间初始化执行 `loadFeed()` 时数据库尚未挂载就绪（`DatabaseHolder.db == null`），导致查询静默回退为空对象且未重新拉取。
  - **修复实现**：在 `DatabaseHolder` 中引入响应式 `isReadyFlow: StateFlow<Boolean>`；在 `HomeFeedViewModel` 中主动监听该流，一旦数据库就绪即刻自动触发发现流加载。
  - 强化 `HomeFeedRepository` 各版块独立异常容错，在 `HomeFeedScreen` 顶栏增加手动刷新按钮与空状态重试卡片。

## [2.9.0] - 2026-09-29

### Added
- **macOS「主页」发现流完整迁移至 Android (macOS Home Feed Discovery Tab Migration)**：
  - 完美复刻 macOS 桌面端 HomeView 首页发现流体验，新增五大全新高能版块与全新底部首位“主页”Tab。
  - **镇馆之选 (Hero Spotlight Carousel)**：精选高分典藏影片大图横向滑块，带背景氛围模糊光晕（Ambient Blur）、评分/年份/片商多重徽章、双语标题、剧情简介以及“立即探索”与“换一部”交互。
  - **往年今日 · 经典首映 (On This Day in History)**：基于当前日期的历史今日首映影片横向流，附带“X年前”首映徽章；当日若无影片自动优雅降级为当月经典。
  - **今日星光 · 标志面孔 (Star Spotlight)**：精选标志性演员圆形头像流，展示演员代表作部数，支持一键直达演员档案。
  - **经典系列大放送 (Series & Franchises Showcase)**：热门系列横向展示流，探索各厂牌标志性系列影片。
  - **随心探索 · 盲盒发现 (Lucky Discovery)**：随机抽取 6 部未探索影片，配备骰子旋转动画的“换一批”换盘按钮。
  - **影库纵览与快捷探索 (Library Quick Stats)**：影片、分集、演员、片商 4 大核心维度统计磁贴卡片，点击直接无缝跳转至对应模块。
- **影片二级菜单新增“分集”专属展示页 (Dedicated Episodes Tab in Movies Screen)**：
  - 在“影片”界面的二级标签“全部影片”与“系列”之间无缝插入“分集”展示页（全部影片 | 分集 | 系列）。
  - 支持全库数万个分集的分页瀑布流展示，标准化横向卡片布局，包含所属影片海报、分集序号、标题、时长与排片日期。
- **最近入库/发行高亮徽章与时间多维度筛选 (New Entry Badges & Flexible Date Filtering)**：
  - 在影片海报网格卡片与分集列表中新增“NEW”/“最新入库”高亮质感徽章，让新近刮削入库的内容在全库中一目了然。
  - 在影片与分集列表顶部引入多维度时间过滤胶囊栏（全部、最新入库、最近30天、最近90天、最近一年），支持用户快速按发行时间或入库时间溯源。
- **原生增量网络刮削引擎 (Native Incremental Scraper Engine)**：
  - 在 Android 移动端原生实现对官方数据源（`/newm` 影片, `/newe` 分集, `/newp` 演员 及对应详情页）的高效增量抓取与 HTML 解析引擎（`GpdbScraperEngine`）。
  - 智能比对本地数据库已有条目 ID，只抓取最新发布的条目，实现毫秒级快速增量检测。
  - 自动解析影片、分集、演员元数据以及关联封面/缩略图，支持事务级安全批量写入本地 SQLite。
- **免 ZIP 包图片按需加载与反爬绕过 (Anti-403 On-Demand Image Cache)**：
  - 重构 `ZipImageFetcher` 与 `GpdbImageLoader`：建立“外部物理路径 -> 内部持久化目录 (`filesDir/image_cache/`) -> ZIP 包 -> 网络按需直连”的 4 级寻址管道。
  - 解决官方图床 Cloudflare 拦截 Android 默认客户端报 HTTP 403 的缺陷，全链路注入桌面端 Chrome UA 与合法 `Referer: https://gayeroticvideoindex.com/` 请求头，完美绕过反爬机制。
  - 网络按需拉取的图片自动在后台持久化落盘至本地缓存目录，新用户即使不下载高达 14GB 的 `GPDb_Images.zip`，浏览时也能秒级按需看图并永久沉淀为本地离线图片资产。
- **WorkManager 后台静默同步与设置控制面板 (Background Sync & Management UI)**：
  - 基于 Jetpack WorkManager 引入 `GpdbSyncWorker`，实现夜间自动静默同步与封面预热。
  - 严格限制任务触发约束条件（仅在 Wi-Fi 连接且处于设备充电中执行），保障零无谓电量与流量消耗。
  - 在“设置”页面新增“增量同步与图库缓存”管理面板，支持实时手动检查官方更新、展示待更新条目清单并一键执行增量入库与进度追踪。
- **统一多端共享数据库兼容 (Shared GPDb.db Migration)**：
  - 彻底打通与 macOS 桌面端使用同一份原生数据库文件（`GPDb.db`）的能力，消除 Android 端专用的魔改版数据库 `GPDb_Android.db`。
  - 在 `GpdbDatabase` 中引入 `MIGRATION_0_1` 与 `MIGRATION_0_2` 双阶迁移规则，桌面端 `user_version = 0` 且未初始化的数据库挂载时自动补充 Room 校验标识与索引，杜绝 `IllegalStateException: A migration from 0 to 2 was required but not found` 崩溃。
- **高频通用组件库 (`ui/components`)**：
  - `GpdbAsyncImage`：统一多级图片寻址与缓存加载引擎，支持相对路径转换 (`toImageCachePath()`)、直接挂载并解析离线图片 ZIP 包、实体 ID 智能推断与三级兜底降级。
  - `MovieGridItem`：标准化影片海报网格卡片，统一圆角、阴影、顶部/居中裁切、年份/时长渐变遮罩以及片商 Tag 交互。
  - `EpisodeListItem`：标准化 84dp 横向分集卡片，统一缩略图转换与标题、排片日期等元数据渲染。
  - `SearchTopAppBar`：标准化带有平活动画的上下文展开式搜索顶栏，集成清除按钮与焦点管理。
  - `TranslationSection`：标准化可复用 AI 翻译展示组件，内置原文/译文无缝切换与翻译状态指示。
- **架构解耦 (Architecture Decoupling)**：
  - 将原先深层嵌套在 UI 文件中的 `SettingsViewModel` 剥离至独立架构层，清晰划分“核心存储与挂载设置”与“AI引擎/增量导出”业务模块。
- **全链路页面进度保留 (Scroll & Navigation State Preservation)**：
  - 为 `HomeScreen`、`SearchScreen`、`FilteredMovieListScreen`、`LibraryScreen`、`PerformerListScreen`、`StudioListScreen`、`StudioDetailScreen` 等所有网格与列表全面接入 `rememberSaveable(saver = LazyGridState.Saver)` 与 `rememberSaveable(saver = LazyListState.Saver)`，彻底解决点击进入详情再返回上一级菜单时页面进度重置、强制跳顶的顽疾。
- **Import Translations Data**: 在“设置 -> 翻译统计”模块新增了“导入翻译数据”按钮功能，支持一键读取包含结构化翻译信息的 JSON 文件，并自动将译文还原写入对应影片与分集的本地数据库。同时会自动刷新顶部的全库翻译统计进度。
- **Export Translations Data**: 在“设置”页面的翻译统计面板中，新增了“导出翻译数据 (JSON)”功能，支持一键将本地已沉淀的所有外文到中文（或目标语言）的元数据（包含所属 Movie ID/Episode ID）完整导出，方便多端同步或外部使用。
- **LLM Provider Presets**: 在设置页的“AI翻译引擎配置”中，新增了主流大模型接口的下拉预设一键填入功能，涵盖了 Claude、Gemini (原生兼容接口)、Kimi (月之暗面)、通义千问、智谱清言、豆包等国内外第一梯队模型。
- **Smart Model Discovery**: 在 API Key 输入框旁新增了“验证并获取模型”探测功能。点击后可实时验证 Key 的有效性，并自动拉取账号下可用的模型列表，将 Model Name 智能转换为下拉菜单供用户快速选择。
- **Native LLM Translate Engine**: 引入强大的原生 AI 大模型翻译引擎，支持在影片和分集详情页一键直白翻译外文简介。
  - 支持在设置中完全自定义大模型接口（兼容 OpenAI / DeepSeek / Claude / Ollama 协议）。
  - 支持完全自定义 System Prompt 与目标语言（内置专业的无删减忠实翻译提示词为默认值）。
  - 支持“进入条目时自动触发后台翻译”选项。
- **Translation Persistence & Statistics**: 大模型翻译结果现已支持永久持久化写入本地 `GPDb.db` 的 `description_zh` 字段。同时在“设置”页面顶部新增“翻译统计”数据看板，直观展示全库的可翻译与已翻译进度。
- **Dynamic Theme (Material You)**: 深度整合 Material 3 动态主题系统，支持 Android 12+ 系统壁纸自适应取色，以及手动强制锁定暗色/亮色模式。

### Changed
- **设置入口重构至“我的库”右上角 (Settings Entry Relocated to Library Top-Right)**：
  - 将底部导航栏冗余的“设置”Tab 移出，精简底部全局导航项；在“我的库”顶部导航栏右上角新增设置齿轮入口。
  - 在“设置”界面新增左上角返回按钮与原生返回栈导航；在即时搜索展开时自动让位收起设置按钮，保障搜索文本框拥有全屏宽度，二者互不干扰。
- **数据库日志模式调优 (TRUNCATE Journal Mode)**：
  - 将 Room 数据库的 JournalMode 显式锁定为 `TRUNCATE`，杜绝 Android 外部存储 / SAF / FUSE 挂载点下由于不支持 POSIX `mmap` 对 `.db-shm` / `.db-wal` 文件引发的 I/O 权限异常，实现移动端与 macOS 间随意拷贝热插拔。
- **页面过度复杂性重构与极致瘦身 (Screen Streamlining & Deduplication)**：
  - `MovieDetailScreen`：从 536 行精简至 345 行（减少 36% 冗余代码），剔除内联图片加载与已废弃的浮动操作栏死代码，全面接入统一组件。
  - `EpisodeDetailScreen`：从 308 行精简至 225 行（减少 27% 冗余代码），接入 `TranslationSection` 与 `GpdbAsyncImage`。
  - `PerformerListScreen`：从 170 行精简至 88 行（减少 48% 冗余代码），接入 `SearchTopAppBar` 与 `PerformerGridItem`。
  - `StudioListScreen`：接入 `SearchTopAppBar` 并精简过滤逻辑。
  - `StudioDetailScreen`：全面接入 `EpisodeListItem` 与 `MovieGridItem`，并为双 Tab 建立独立进度持久化。
  - `SearchScreen`：重构无搜索词时的状态布局，修复搜索历史与热门标签层叠问题，接入统一网格卡片与滚动状态记忆。
- **Export Translations Data Enhanced**: 大幅扩充了 JSON 翻译数据导出的底层元数据信息。现在除原有的 ID 和中文翻译外，对于每一条记录还会详细追加原片名、片商名、发行年份、分类、外文原简介等辅助字段。这样即便在外部平台进行清洗、查阅或未来重新导入时，都能获得更完善的上下文支撑，避免了纯 ID 造成的映射混淆。
- **Library Tab Layout**: 优化了“我的库”中众多二级分类的展示布局，摒弃了需要水平滑动的滚动条样式，改为全局自动换行的 `FlowRow` 标签云样式，现在进入页面即可一眼纵览并点击所有分类。
- **Translate Toggle UI**: 优化了影片与分集详情页的 AI 翻译体验，现在“AI翻译”在完成输出后，右上角的魔法按钮会智能转变为“显示原文” / “显示译文”的双向无缝切换按钮，保持界面高度整洁。
- **LLM Prompt Generalization**: 修改了内置的大模型专属系统提示词（System Prompt）。将其中的“中文成人语境”泛化重构为“目标语言成人语境”，完美解耦了固定语种限制，为向繁体、英文、日文等多语种的直白翻译提供了更强的兼容性。
- **Immersive Edge-to-Edge UI**: 全面开启系统级沉浸式视图（Edge-to-Edge），打破状态栏与底部手势区黑边，瀑布流及海报展示更具现代高级感。
- **My Library Tabs**: 重新设计“我的库”二级分类导航栏。废除极易被忽略的水平滑动标签栏（`ScrollableTabRow`），替换为自适应折行包裹展示的胶囊按钮簇（`FlowRow` + `FilterChip`）。现在进入“我的库”即可一览所有分类（收藏、想看、已看、等），免去了任何滑动操作。

### Removed
- **Library Watched Tab**: 移除了“我的库”中已看选项卡及其冗余加载代码。
- **Duplicate UI Code Blocks**: 彻底消除了分散在 4 处界面的影片海报卡片代码、4 处界面的分集卡片代码以及各二级列表各自实现的搜索顶栏代码。
- **Dead Code**: 移除了 `MovieDetailScreen` 中的私有 `MovieUserActionBar` 废弃代码以及构建残留文件。

### Fixed
- **搜索输入框自动获焦与软键盘自动弹起 (Search Input Auto-Focus & Keyboard Trigger)**：
  - 修复此前点击搜索图标后仅展示文本框但软键盘不自动拉起、必须用户再次点击文本框的缺陷。
  - 在全局即时搜索顶栏 `SearchTopAppBar` 与独立搜索页 `SearchScreen` 中接入 `FocusRequester` 与 `LocalSoftwareKeyboardController`，点击搜索时自动获焦并即时呼出系统输入法键盘。
- **FTS5 虚表语法兼容性与中文搜索修复 (FTS5 Query Crash & CJK Fallback)**：
  - 修复此前硬编码 `"$query"*` 导致 SQLite FTS5 解析报错（`near "query": syntax error`）的问题，采用跨平台安全的 `id IN (SELECT id FROM movies_fts WHERE movies_fts MATCH ?)` 子查询语法。
  - 新增 CJK 中文标题与中文简介的 `LIKE` 兜底匹配机制，彻底解决 FTS5 分词器无法识别无空格中文词汇导致搜索无结果的问题。
- **Release 构建 LintVital 崩溃修复**：
  - 修复在 AGP 8+ 与 Kotlin 2.x 环境下运行 `./gradlew assembleRelease` 时由于 `lintVitalAnalyzeRelease` 引发的构建中断异常。

### Fixed
- **Export Translations Crash**: 修复了“导出翻译数据”功能在导出影片表时，因查询了实际不存在的 `original_title` 与 `action_notes` 列导致底层 SQLite 抛出异常并中断导出的严重 Bug。现已精简并严格对齐了实际存在的元数据字段。
- **Movie Detail Avatar Cropping**: 修复了“影片详情页”横向演员列表中演员圆角头像未能正确露脸的问题，将原有的居中裁切修正为 `Alignment.TopCenter` 顶端对齐裁切。
- **Movie Detail Episode Thumbnails**: 修复了影片详情页下方“相关分集”卡片不显示封面预览图的问题。补充了缺失的 `.toImageCachePath()` 转换映射，使底层框架能正确寻址离线图片缓存。
- **Movie Details Episodes Missing**: 修复了“影片档案页不显示其相关分集信息”的遗漏问题。现在影片详情页在展示演员列表下方，会正常遍历并用卡片形式渲染该影片包含的所有分集列表，点击对应分集即可直接无缝跳转至对应的分集档案页。
- **Series Mixed Studios Bug**: 修复了“对系列影片的判断方式导致多家片商的作品混合在一起”的严重逻辑缺陷，现在收藏和查询系列影片时，采用 `[片商名]|||[系列名]` 的联合主键进行隔离判断，彻底杜绝了同名系列被错误聚合的问题。
- **Episode Translate Button Missing**: 补齐了遗漏的“分集详情页”的 AI 翻译功能入口，现在单独点开分集也能对其简介内容独立触发翻译并落盘持久化。
- **Library Favorite Episodes Blank**: 修复了“我的库 -> 收藏分集”明明有数据却显示空白的严重缺陷，重写了底层 UI 路由分发逻辑使其能正确使用沉浸式卡片渲染独立的分集条目。
- **Studio Episode Count Mismatch**: 彻底修复了片商档案页“发行分集”数量与 macOS 客户端显示不一致（严重少算）的问题。重构了底层的 SQL 联合查询逻辑，加入了向 `movies` 表的回退推断 (`LEFT JOIN`)，成功捞回了自身未打片商标签但其所属影片确属于该片商的数千个隐藏分集数据（如 BelAmi 遗漏的 138 个分集）。
- **Series Identification Bug**: 彻底重写了系列影片的标识匹配逻辑。此前仅通过前缀字符（`title LIKE`）检索会导致多家不同片商（Studio）下的同名系列被错误糅合在一起。现已通过前端路由组合键与后端 SQL 双重复合校验（`studio_name = ? AND title LIKE ?`），完美修复此乱入 Bug。

## [2.8.0] - 2026-09-24

### Added
- **Release Build**: 自动集成专属发布密钥库，并在 `build.gradle.kts` 中配置 `signingConfigs`，支持一键编译受完整签名的 Release APK。

### Changed
- **Version Synchronization**: 将 Android 客户端版本号与 macOS/Windows 桌面端统一同步更新至 `2.8.0` (versionCode 280)。
- **Project Cleanup**: 彻底清理 `android_client` 目录下 90 余个历史自动化调试脚本（`fix_*.py`、`patch_*.py` 等）及冗余日志文件，大幅净化工程结构。
- **Database Cleanup**: 移除了项目根目录下遗留的多份废弃数据库备份分卷压缩包，释放了数百兆磁盘空间。

### Fixed
- **App Icon Settings Crash**: 修复了设置页“更换应用图标”弹窗因 Compose 原生的 `painterResource` 无法解析 Android 8.0+ 自适应图标（Adaptive Icon）而导致的闪退问题（已替换为基于 Coil 的 `AsyncImage`）。
- **Debug Build Alias Resolution**: 修复了在 Debug 模式下，带有 `.debug` 包名后缀时，动态切换桌面图标引发“类未找到 (Class Not Found)”闪退崩溃的问题。


### Fixed
- **Navigation State Loss**: 彻底修复从次级详情页返回时（如演员档案页、片商档案页），页面跳回顶部及 Tab 栏重置的问题。通过采用 `rememberSaveable` 持久化 UI 状态，并在 ViewModels 层拦截冗余的重复加载请求来实现。
- **Dynamic Icon Switching Crash**: 修复在设置页切换应用图标后，底层 `ActivityManager` 强杀进程导致的闪退黑屏问题。通过安全地绑定 Compose 协程及后台组件延时卸载解决。

### Changed
- **Adaptive App Icons**: 全面重构并支持 Android 8.0+ 的 `<adaptive-icon>` 自适应图标，消除所有图标白边，完美适配不同手机系统的形状裁切。
- **Default App Icon**: 重新排列应用图标预设，将「双雄火星图腾」设定为“方案 A”并作为首发默认应用图标。
- **Settings UI**: 在“设置 - 更换应用图标”弹窗中，新增了直观的图标视觉预览图。


### Added
- **Locale Support**: The AppPreferences language setting now dynamically applies "zh" or "en" to the application via `AppCompatDelegate.setApplicationLocales`.
- **Remount Action**: Moved the "Remount Database" action from the Home screen top bar to a dedicated list item in the Settings screen.

### Fixed
- **Prevent DB Reload on Theme Change**: Added `android:configChanges="uiMode|locale|layoutDirection"` to `AndroidManifest.xml` to prevent the activity from tearing down and reloading the database when switching themes or languages.

### Added
- **Locale Support**: The AppPreferences language setting now dynamically applies "zh" or "en" to the application via `AppCompatDelegate.setApplicationLocales`.
- **Remount Action**: Moved the "Remount Database" action from the Home screen top bar to a dedicated list item in the Settings screen.
- **Studio Detail**: Added a new tab "发行分集" (Episodes) to the Studio Detail screen to display all episodes produced by the studio alongside movies.
- **Contextual Search**: Implemented real-time context-specific search within the "演员" (Performers) and "片商" (Studios) tabs via an expandable TopAppBar TextField.
- **Category & Series Browsing (Milestone 2)**: Completely overhauled the root navigation structure by adding a Material 3 `NavigationBar` (Bottom Tabs). Users can now directly jump between "Movies" (全部影片), "Studios" (片商), "Series" (系列), and "Categories" (分类标签).
- **Studio & Category Detail Pages**: Added a fully functional Studio profile page with the ability to favorite the studio and browse all published movies. Also added deep-linkable filtered screens for browsing movies by Category or Series.
- **Library Sub-Tabs (Milestone 4)**: Added a secondary horizontal menu to "我的库" (My Library) tab, allowing users to independently browse Collections (收藏), Wishlist (想看), Watched (已看) and Favorite Performers (收藏演员).
- **Movie Director Info**: The Movie Detail screen now intelligently fetches and displays the Director(s) alongside the Studio and Runtime.
- **Resource Search (Milestone 4)**: Integrated one-click BT4G magnet search functionality directly in Movie and Performer detail screens. Added a dedicated BoyfriendTV link on Performer profiles for fast external access.
- **Private Ratings & Marking**: Added an interactive User Action Bar to the movie detail screen, enabling 0.5-5.0 ratings and Wishlist/Watched statuses. Data is safely written back to the external SQLite database (`user_movie_data`) without breaking Room validation.
- **Global Favorites**: Added a favorites toggle on the movie detail screen and a new "My Favorites" filter tab on the Home screen to query the `user_favorites` table.
- **Global Search Center (FTS5)**: Added milliseconds-level search capability using SQLite FTS5 virtual tables (`movies_fts` and `performers_fts`).
- **Category & Series Infrastructure**: Added entities and DAO setup (`CategoryGlossaryEntity`, `SeriesCollectionEntity`, `BrowseDao`) for future categorised browsing support.
- **Episode Detail Screen**: Added an independent detail screen for Episodes, showing thumbnails, release dates, descriptions, and participating performers.
- **Performer Detailed Traits**: Display additional anatomical and personal traits (Hair, Eyes, Body Hair, etc.) in the Performer Profile screen with an expandable/collapsible animation.
- **UI & Sort**: “演员”与“片商”列表顶部右侧新增了“排序”按钮。现在您可以随时在此下拉菜单中将数据“按作品数排序”（影片和分集的累计总数降序排列，此为默认设置）或“按拼音排序”（字母/拼音 A-Z 顺序排列）。
- **UI**: “系列” (Series) 展示现已全面升级为网格界面（Grid UI）。在展示时不再是单调的列表项，而是每个系列都直观地显示该系列中包含影片的代表海报，并带有统计影片收录数量的黑底遮罩。
- **UI**: 为“演员”、“片商”、“我的库”等所有一级 Tab 的顶部栏 (TopAppBar) 新增了专属的上下文搜索功能。现在可以直接在当前页面点击搜索图标进行实时过滤和搜索，无需跳转到全局搜索页。

### Changed
- **Global Search UI Replacement**: Completely removed the legacy inline search box from the `HomeScreen` (which only supported partial movie matching) and replaced it with a top-right Search icon that fully routes to the new FTS-powered `SearchScreen` (supporting both Movies and Performers).
- **Performer Detail Scroll Behavior**: Refactored the UI to use a unified `LazyColumn` with a `stickyHeader` for the TabRow. This natively allows the large performer header (avatar, details) to automatically scroll out of view when scrolling down the movies or episodes, significantly opening up vertical screen space.
- **Movie Detail Header**: Removed the static three-image limit. It now dynamically displays `coverFull` and `coverBack` inside a smooth, swipeable `HorizontalPager`. The low-resolution `coverIcon` is now excluded.
- **Performer Detail Layout**: Replaced the long vertical stacking of movies and episodes with a space-saving horizontal `TabRow` to toggle between "参演作品" (Movies) and "参演分集" (Episodes).
- **Zoomable Image Dialog Gestures**: Entirely rewrote the `ZoomableImageDialog` gesture dispatcher. It now perfectly supports:
  - Horizontal swiping between covers (when not zoomed).
  - Vertical drag-to-dismiss with a fade-out background effect (when not zoomed).
  - Pinch-to-zoom and pan (when zoomed > 1x).
- **UI**: 搜索结果页 (SearchScreen) 已从原先的单列垂直列表视图 (List) 升级为与主页一致的紧凑网格视图 (Grid)。演员与影片的展示将复用 `PerformerGridItem` 和 `MovieGridItem`，极大提升了屏幕空间利用率，并顺带修复了在原列表视图中因为仅读取了 `coverIcon`（而非拼接的 `coverFull`）导致搜索结果影片无法正常显示大尺寸拼接海报的 Bug。
- **UI**: 修复了“影片”和“系列”网格界面中，由于标题行数不同导致卡片高度不一致的问题。现在网格卡片将具有统一的高度，看起来更加整齐美观。
- **UI (重构)**: 重新设计了网格卡片（影片和系列）的标题文本区排版逻辑。移除了为了兼容多行文本而预留的巨大空白高度，**改用跑马灯 (Marquee) 滚动效果**。
  - 现在文本区被压缩到了最极致紧凑的固定高度（影片卡片减少了 60dp 空白，系列卡片减少了 58dp 空白）。
  - 所有标题、副标题（原名）、片商名称均限制为单行显示，保证极度整齐划一。
  - 对于超出宽度的超长标题，会自动**左右平滑滚动**，实现了“既不留多余空白，又完美完整展示长标题”的最佳折中体验。

### Fixed
- **Search Empty Results**: Fixed the Search function returning 0 results by correcting the SQLite `JOIN` query for FTS5 tables (`m.id = fts.id` instead of `fts.rowid`).
- **Favorites Filter Logic**: Expanded the "My Favorites" filter tab logic (now "我的库") to automatically include movies marked as "Wishlist", "Watched", or Rated, pulling dynamically from both `user_favorites` and `user_movie_data` tables using a SQL `UNION`.
- **Search Screen Crash**: Fixed an `IllegalArgumentException` crash occurring when entering the Search screen, caused by incorrectly attempting to mount a second Room Database instance using the folder root path instead of the `GPDb.db` file. The screen now safely reuses the global `DatabaseHolder.db` singleton.
- **Database Schema Validation Crash**: Fixed an `IllegalStateException` during database mount on physical devices where Room strictly rejected the pre-existing SQLite `category_glossary` and `series_collections` tables due to type affinity (`TIMESTAMP` vs `TEXT`) and nullability mismatches (`term` PK). Completely bypassed Room validation for these tables by migrating `BrowseDao` to `@RawQuery` while maintaining robust Kotlin mapping.
- **Room FUSE Schema Validation Crash**: Safely bypassed the `IllegalStateException: Room cannot verify the data integrity` crash on Android 11+ external storage FUSE mounts by employing an empty migration block rather than destroying user data via `fallbackToDestructiveMigration()`.
- **Avatar Cropping Issue**: Corrected the "headless" cropping issue for standing portrait photos (performers' circular avatars) by setting `alignment = Alignment.TopCenter` in all `AsyncImage` instances.
- **UI**: 修复了“我的库”（收藏影片、想看、已看）和“系列”中不显示拼接后海报（封面）的问题。原因是在此前的 UI 重构中，`MovieGridItem` 被误替换为了一个只读取缩略图标 `coverIcon` 的精简版本。现已恢复为原始包含时长、年份及黑底渐变字体的精美卡片式 `MovieGridItem` 设计，正确显示 `coverFull` 拼接海报。
- **Navigation/Crash**: 修复了在“片商”列表页点击任意片商可能会导致 App 闪退的问题。这是由于部分片商名称中含有特殊字符（例如 `/` 等斜杠），在 Navigation 传参阶段 `URLEncoder` 会将特殊符号处理得不彻底，最终使得 Compose Navigation 的路径解析抛出 `IllegalArgumentException`（找不到匹配的 route）。现已将 Navigation 中的参数统一改用 Base64 (URL_SAFE) 编码传递，彻底杜绝此类路径解析崩溃。
- **UI**: 修复了“全部影片”主页以及点进某个“系列”后展示的影片网格视图中，影片标题因为长度限制（被截断为1行）而无法完整显示的问题。现已调大 `maxLines` 支持多行自动折行。
- **UI**: 修复了“全部影片”主页因为 Compose Scaffold 在非沉浸式 (非 edge-to-edge) 模式下计算状态栏高度引起的双倍 Padding 问题，彻底消除了标题上方多余的空白区域，让出更多宝贵空间。
- **UI**: 为了避免误解，“全部影片”主页右上角的排序按钮（三条线）现已改造为带明确文字标注的下拉菜单（类似于演员/片商页面的操作方式），让您可以直观选择“按收录顺序”或“按发行年份”对影片进行排序。
- **Performance**: 彻底修复了“片商”选项卡一直转圈加载卡死的问题。重写了针对底层 SQLite 数据库的复杂子查询（Correlated Subquery），将其替换为效率更高的左外连接预聚合查询，使得排序读取几千个片商作品数量的速度从几分钟甚至卡死缩减到了几毫秒级别。
- **UI**: 修复了“我的库”如果在 App 刚启动、底层 ZIP 图片库尚未完全挂载完成（约需4秒）时点击，会导致页面状态陷入死循环、出现“无限转圈加载”的竞态条件（Race Condition）Bug。现在加入了智能轮询等待机制。
- **UI**: 修复了“从详情页等次级页面返回上一页时，页面总是会自动滚动回顶部，丢失浏览进度”的体验问题。现在底层网格列表会正确记住并恢复您的滚动位置。
- **Performance**: 优化: 移除了列表滑动时的图片渐变动画并增加 contentType 节点复用，大幅降低滑动掉帧问题。
