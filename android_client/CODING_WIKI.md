# GPDb Android 客户端开发规范与技术架构 Wiki (Coding Wiki)

---

## 1. 客户端功能定位与设计目标

GPDb Android 客户端是基于 **GPDb 离线影视数据库系统** 的原生移动端实现。
其核心定位为：**“单机私密、零云端依赖、极致触控流畅度、海量影视元数据即时检索的移动掌上影库”**。

### 1.1 核心业务功能矩阵

| 功能模块 | 功能特征与技术实现 | 移动端原生优化点 |
| :--- | :--- | :--- |
| **全库检索与多维筛选** | 63,000+ 部影片、100,000+ 分集、6,000+ 演员、1,300+ 厂牌 | 针对触摸屏优化的底部筛选抽屉、快速滚动索引、作品与分集即时无缝切换 |
| **沉浸式主页 (Home Feed)** | 焦点海报轮播、AI 导赏、今日星光、经典系列大放送、随心探索盲盒 | 今日星光动态图片过滤（仅展示有效头像）、经典系列随机抽取（10组）、顶部防窥模式快捷开关 |
| **离线媒体与海报系统** | `image_cache/` 物理目录读取、自适应多图封面拼图 | Android Scoped Storage 分区存储规范、Coil 2 离线图片解码器、动态防窥高斯模糊层 (`privacyBlurImage`) |
| **影视/演员/系列/厂牌详情** | 全量元数据展现、维基小传、生理特征、曾用艺名AKA、多站检索外链 | 单流式 Lazy 布局架构（无嵌套滑动冲突）、通用全屏手势缩放灯箱 (`ZoomableImageDialog`)、3D翻转卡片/平铺画廊 |
| **全端统一数据备份与迁移** | 用户收藏夹、播放探索统计、评分、私密笔记、界面偏好配置 | `gpdb_universal_backup` 跨平台统一 JSON 规范，与 macOS / Windows / iOS 四端无损互通 |
| **流光分享卡片** | 正向海报封面与反面封底双图排版、Telegram 官方频道点阵二维码 | Canvas 原生矢量渲染、16:9 分集居中自适应裁剪、双离屏高保真磨砂模糊打码 |
| **在线检测更新与热升级** | GitHub Releases API 版本对比、新版特性展示、APK 断点下载 | `FileProvider` 安全权限映射、自动唤起系统 APK 安装向导、启动静默版本巡检 |

---

## 2. 移动端技术栈架构全景

```
[ UI 渲染层 (Presentation Layer) ]
  Jetpack Compose 1.7+ (Material 3) + Compose Navigation
  Navigation: Screen (密封类架构，类型安全传参)
  异步状态流: Lifecycle ViewModel + StateFlow / MutableStateFlow
  图片引擎: Coil 2 (集成自定义 GpdbImageFetcher / GpdbImageData)

[ 业务逻辑与仓储层 (Domain & Repository Layer) ]
  HomeFeedRepository / BrowseRepository / MovieRepository / UserRepository
  UserAnalyticsRepository (统计数据与行为追踪)
  AppUpdateManager (GitHub Releases API 检查与 APK 下载器)

[ 本地数据持久化层 (Data Storage Layer) ]
  Room 2.6+ (Schema v4, 支持 WAL 并发读模式)
  挂载前置自愈机制: GpdbDatabase.ensureSchemaCompatibility (原生 SQLite 直连补列)
  偏好存储: Jetpack DataStore Preferences (防窥模式、排版模式、语言配置等)
  文件系统访问: Storage Access Framework (SAF) + Scoped Storage (持久化 URI 树授权)
```

---

## 3. 核心子系统与关键设计规范

### 3.1 外部数据库挂载与架构自愈机制 (Room Schema Compatibility)

由于 Android 客户端通过 SAF 动态挂载用户指定的外部 `GPDb.db` 文件，不同时期生成的数据库可能缺少最新扩展字段或表。

#### 架构版本与迁移定义
- 当前 Room 架构版本为 **`4`**。
- `MIGRATION_3_4`: 执行 `ALTER TABLE performers ADD COLUMN sj_url TEXT`。

#### 前置自愈机制 (`ensureSchemaCompatibility`)
在 Room 执行 `GpdbDatabase.getDatabase(context, uri)` 构建连接与 TableInfo 强制校验之前，必须优先触发 `ensureSchemaCompatibility`：
1. 使用原生 `SQLiteDatabase.openDatabase` 以读写模式直接打开该文件；
2. 查询 `PRAGMA table_info(performers)`，检测 `pbc_url` 与 `sj_url` 是否存在，缺失则就地执行 `ALTER TABLE performers ADD COLUMN ...`；
3. 检测扩展表 `performer_pbc_profiles` 与 `performer_sj_profiles` 是否存在，若不存在则就地创建完备 DDL；
4. 关闭原生连接，将自愈后的数据库交付 Room 打开。彻底根绝 `IllegalStateException: Migration didn't properly handle: performers` 导致的数据库挂载崩溃。

### 3.2 单流式 Lazy 布局规范 (Single-Stream Lazy Layout)

#### 严禁使用的反模式
- **严禁使用 `nestedScrollConnection` 配合 `isHeaderVisible` 粗暴折叠头部**：极微小的上滑手势会误将状态置为 `false`，若混用 `rememberSaveable` 则会导致用户下次进入时头部被永久隐藏。
- **严禁在非滚动的 `Column` 中直接放置带有 `Modifier.fillMaxSize()` 的 `LazyVerticalGrid`**：Compose Column 会对无权重的子元素依次测量，巨幅头部卡片会将下方的 TabRow 与 Grid 挤出屏幕可见范围或引发严重重叠冲突。

#### 推荐的标准单流规范
在 `PerformerDetailScreen.kt` 中：
```kotlin
// 作品网格 Tab
LazyVerticalGrid(
    state = gridState,
    columns = GridCells.Adaptive(minSize = 160.dp),
    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
    modifier = Modifier.fillMaxSize()
) {
    // 1. 人物档案全量卡片置顶
    item(span = { GridItemSpan(maxLineSpan) }) {
        PerformerProfileHeader(...)
    }
    // 2. 分类标签栏置顶
    item(span = { GridItemSpan(maxLineSpan) }) {
        PerformerTabRow(tabs, selectedTabIndex, onTabSelected = { selectedTabIndex = it })
    }
    // 3. 作品网格项
    items(movies, key = { it.id ?: 0L }) { movie ->
        MovieGridItem(movie = movie, ...)
    }
}
```
切换到「出演分集」时渲染 `LazyColumn`，同样将 `PerformerProfileHeader` 与 `PerformerTabRow` 置于顶部 item，实现全局顺畅滚动、零布局冲突、首屏信息完备直观。

### 3.3 离线图片渲染与防窥保护系统

1. **统一数据模型 `GpdbImageData`**：
   - 包含 `relativePath`（相对物理根路径，如 `image_cache/Performers/123.jpg`）、`physicalRoot`（SAF 目录本地解算真实路径）与 `fallbackUrl`（网络降级直链）。
2. **防窥模糊装饰器 `privacyBlurImage()`**：
   - 当全局防窥模式激活时，通过 Compose `Modifier.blur(32.dp)` 与暗色半透明覆层实现瞬时离屏模糊保护。
   - 探索主页顶部操作栏放置即时防窥图标开关，并与设置页状态双向强联动。

### 3.4 全平台通用用户配置与数据备份规范 (`gpdb_universal_backup`)

- **规范标识**: `"format": "gpdb_universal_backup", "version": 1`
- **覆盖数据**:
  - `user_favorites`: 收藏实体（电影、分集、演员、导演、系列、厂牌）；
  - `user_analytics`: 专注总时长、各维度探索/播放计数、活跃天数；
  - `user_ratings_notes`: 自定义标记、私密笔记、评星状态；
  - `client_preferences`: 防窥状态、海报排版模式、语言偏好。
- **文件交互**: 接入 Android SAF (`ActivityResultContracts.CreateDocument` / `OpenDocument`)，生成并读取标准 `.json` 文件，实现与 macOS / Windows / iOS 四端无障碍互转。

### 3.5 分享卡片流光引擎与 Telegram 官方二维码

- **双封面布局**: 支持同时排布海报正面封面与反面封底，若缺少封底则优雅自适应居中单封面。
- **16:9 分集比例居中裁剪**: 彻底消除分集剧照被 2:3 海报比例强行挤压导致的形变。
- **官方二维码 Canvas 矢量点阵**: 嵌入 27×27 标准 QR 矩阵，白底圆角容器包裹，直通官方频道 `https://t.me/gpdbnews`。

---

## 4. 目录组织架构 (Source Tree)

```
android_client/app/src/main/
├── AndroidManifest.xml                  # 权限、FileProvider 与 Activity 声明
├── res/                                 # 启动图标、颜色定义、file_paths.xml 路径配置
└── java/com/gpdb/android/
    ├── MainActivity.kt                  # 根 Activity，Compose 主题与导航分发
    ├── data/
    │   ├── db/                          # Room Database, DAO 接口, Entity 实体定义
    │   ├── repository/                  # 业务数据仓储 (Movie, HomeFeed, User, Browse)
    │   ├── analytics/                   # 本地用户行为与探索洞察统计仓储
    │   └── settings/                    # DataStore 用户配置偏好
    ├── image/                           # Coil 离线解码适配器与 GpdbImageData 模型
    ├── ui/
    │   ├── navigation/                  # Screen 导航密封类与路由定义
    │   ├── homefeed/                    # 探索主页 (焦点轮播、星光、随机系列大放送)
    │   ├── browse/                      # 分类检索、字母筛选与过滤列表页
    │   ├── detail/                      # 电影详情、演员档案、分集详情、厂牌系列详情
    │   ├── library/                     # 个人收藏与历史库 (支持复合唯一键防崩溃)
    │   ├── settings/                    # 分类胶囊切换、排版图示选择、通用备份导入导出
    │   └── components/                  # 通用卡片、分享模态框、全屏灯箱、更新弹窗
    └── util/                            # 词汇本地化 (GlossaryHelper)、更新管理 (AppUpdateManager)
```

---

## 5. 构建、发布与验证指南

### 5.1 本地编译指令

```bash
# 切换至 Android 工程根目录
cd android_client

# 执行 Kotlin 语法与类型校验编译
./gradlew compileReleaseKotlin

# 编译并生成 Release 签名 APK 安装包
./gradlew assembleRelease
```

### 5.2 产物输出与命名规范

- Gradle 原生输出：`android_client/app/build/outputs/apk/release/app-release.apk`
- 顶层发布规范文件：根目录下 `GPDb_Android_v2.15.0_release_signed.apk`
- Release 构建已配置 R8 混淆瘦身与资源压缩，体积稳定保持在 4.5MB 左右极致轻量水平。
