# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
