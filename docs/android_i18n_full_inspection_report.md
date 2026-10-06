# Android 客户端多语言全面整改与实机审查报告

## 1. 概述与任务目标

根据用户要求与 `client_i18n_remediation_walkthrough.md` 规范，本轮针对 **Android Debug 版本**（`com.gpdb.android.debug`）进行了地毯式的多语言审查与深层整改。目标是确保：
1. **菜单与界面 100% 本地化**：7 种语言（`zh-CN`, `zh-TW`, `en`, `ja`, `it`, `es`, `de`）下所有菜单、标签、弹窗、排序选项及设置项完全跟随当前选定语言；
2. **拒绝中文字典回退残留**：在非中文语言（如英语、日语、意大利语等）环境下，当词条缺失时，严格回退到英文或英文原名，杜绝突兀的汉字回退；
3. **实体原名规范**：非中文环境下，影片网格标题、演员档案作品、厂牌名称、分享卡片等实体一律优先展示英文原名；
4. **单位动态国际化**：杜绝中英硬编码分支（如 `"${count}部"` / `"${count} films"`），统一使用国际化复合单位模板（如 `common.filmsCount`、`analytics.timeHoursMins` 等）。

---

## 2. 发现的核心问题与整改方案

### 2.1 词典体系与词条覆盖率
- **问题**：原 Android 端的 `I18n.kt` 采用极简硬编码字典，非中文语言仅有二十余个词条，绝大部分小语种直接回退至简体中文，导致设置、排序、弹窗、空状态处大量残留中文。
- **整改**：
  - 将桌面端全量 7 语言字典（扩充至 **1,358 个词条**）无缝同步至 Android 端；
  - 采用模块化单例语言包架构（`com.gpdb.android.util.locales.*`），包括 `LocaleZhCn`, `LocaleZhTw`, `LocaleEn`, `LocaleJa`, `LocaleIt`, `LocaleEs`, `LocaleDe`；
  - 编写了自动化同步脚本 `tools/sync_desktop_i18n_to_android.py`，确保多端词典的一致性与可维护性；
  - 升级 `I18n.kt` 回退链：`当前语言 -> 英语 (非中文环境下) -> 简体中文 (中文环境下) -> 默认值 / 键名`。

### 2.2 实体显示逻辑（影片、厂牌、演员）
- **问题**：在非中文环境下，部分网格、卡片及详情页直接展示了数据库中的 `title_zh` 或 `name_zh`。
- **整改**：
  - **长片网格 (`MovieGridItem.kt`)**：仅在 `currentLang.isChinese` 时展示 `title_zh`（并辅以英文原名作为副标题）；非中文环境下直接展示英文原名 `title`。
  - **厂牌网格与详情 (`StudioListScreen.kt` & `StudioDetailScreen.kt`)**：非中文环境下主标题统一展示原厂牌名（如 `BelAmi`、`Raw Fuck Club`），彻底消除 `name_zh` 中文干扰。
  - **发现流与系列 (`HomeFeedScreen.kt` & `SeriesListScreen.kt`)**：非中文环境下优先采用 `movieTitle` / 原片名。

### 2.3 分享卡片 (`ShareCardDialog.kt`)
- **问题**：分享卡片在英文或小语种下，副标题显示了中文译名，导演和时长胶囊使用了中文硬编码单位（`"导"`、`"分"`）。
- **整改**：
  - 大标题及副标题严格联动当前语言模式：仅在中文环境下展示中文大标题与英文副标题；非中文环境下仅展示原标题，不显示任何中文副标题；
  - 胶囊属性统一改用 `common.director`、`common.minutes` 动态国际化键值；
  - 演职员分隔符在中文下使用 `、`，非中文下使用 `, `。

### 2.4 数据统计界面与复合单位 (`AnalyticsScreen.kt`)
- **问题**：
  - 专注时长采用 `"${hours}小时 ${mins}分"` 与 `"${hours}h ${mins}m"` 的二元硬编码分支，在日、意、西、德语下无法本地化；
  - 复合指标如 `"${data.favoritesCount} 藏 / ${data.ratingsCount} 评"`、`"${data.directorViewsCount} 导 / ${data.studioViewsCount} 厂"` 缺少多语言支持；
  - 演员档案出道年份采用 `"出道: 2020年"` 写死中文格式。
- **整改**：
  - 新增 `analytics.timeHoursMins`、`analytics.timeMinsSecs`、`analytics.favsAndRatings`、`analytics.dirsAndStudios` 等复合模板；
  - 在演员档案中使用 `performer.debutYear`（支持日文 `デビュー: {year}年`、英文 `Debut: {year}` 等）；
  - 统计卡片单位全面采用标准 `analytics.unitMovies`、`analytics.unitScenes`、`analytics.unitPersons`、`analytics.unitTimes`、`analytics.unitDays` 等规范键。

---

## 3. 实机审查与验证结果

在真实 Android 物理设备（设备号 `39071FDJG00L9S`）上运行 Debug 安装包（`com.gpdb.android.debug`），切换至各语言进行了全面验证：

| 模块 / 界面 | 审查结果 | 状态 |
| :--- | :--- | :---: |
| **底部导航栏** | 6 个 Tab（Home / Movies / Performers / Studios / Directors / Favorites）名称随语言完全动态本地化 | ✅ 通过 |
| **首页发现流** | 焦点推荐、历史上的今天、精选演员、经典系列、盲盒发现等标题与时间全部国际化（如 `1 yrs ago`、`Oct 6`、`films`） | ✅ 通过 |
| **影片网格** | 仅显示原片名，无任何中文残留；时长单位适配本地化（如 `108 min`） | ✅ 通过 |
| **厂牌网格与详情** | 厂牌名称显示英文原名（如 `BelAmi`），Tab 标签显示 `Released Movies (463)`、`Released Episodes (5132)` | ✅ 通过 |
| **演员档案页** | 身高、体重、体型使用 Glossary 动态本地化，出道年份使用 `Debut: {year}` | ✅ 通过 |
| **分享卡片弹窗** | 非中文下纯英文展示，无中文标题残留，标签与元数据完全匹配 | ✅ 通过 |
| **使用统计页面** | 时长格式、互动次数、打卡天数与图表单位全部国际化 | ✅ 通过 |
| **设置与语言切换** | 支持即时无缝切换 7 种语言，选项与描述随当前界面动态变更 | ✅ 通过 |

---

## 4. 涉及的关键文件清单

1. **国际化字典与工具**：
   - `desktop_client/src/i18n/locales/*.ts` (7 个语言包同步扩充)
   - `android_client/app/src/main/java/com/gpdb/android/util/I18n.kt`
   - `android_client/app/src/main/java/com/gpdb/android/util/locales/` (7 语言包 Kotlin 单例)
   - `tools/sync_desktop_i18n_to_android.py`
2. **业务界面与组件**：
   - `android_client/app/src/main/java/com/gpdb/android/ui/components/ShareCardDialog.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/components/MovieGridItem.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/components/TranslationSection.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/homefeed/HomeFeedScreen.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/detail/StudioDetailScreen.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/detail/MovieDetailScreen.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/detail/PerformerDetailScreen.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/detail/EpisodeDetailScreen.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/browse/StudioListScreen.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/browse/SeriesListScreen.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/analytics/AnalyticsScreen.kt`
   - `android_client/app/src/main/java/com/gpdb/android/ui/settings/SettingsScreen.kt`
