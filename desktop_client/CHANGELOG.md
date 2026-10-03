# Changelog

All notable changes to the macOS / Desktop Client will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.16.7] - 2026-10-03

### Fixed
- **“厂牌历史档案与风格深度解析专栏”正文展示与版面舒展修复 (`StudioDetailModal.vue`, `i18n/index.ts`)**：
  - 彻底修复此前片商档案详情弹窗中因高度受限导致的厂牌历史专栏被压缩成紧凑胶囊药丸、正文无法正常显示的视觉缺陷；
  - 专栏卡片升级为大画幅舒展架构（`w-auto mx-6 md:mx-8 p-6 md:p-8 rounded-3xl`），搭配左侧专属主色边线（`border-l-2 border-accent/50`）、典籍徽标（`BookOpen`）与深度解析标签（`Sparkles`）；
  - 正文排版采用 `text-sm md:text-[15px] leading-relaxed text-justify select-text whitespace-pre-line`，完整呈现 181 家核心厂牌创立年代、创始人背景、美学流派与文化演变深度历史档案；
  - 规范并统一国际化专栏标题文案为「厂牌历史档案与风格深度解析专栏」。

### Optimized
- **影片档案页布局重构：剧情简介板块移至海报下方通栏展示，操作按钮组矩阵化收拢 (`MovieDetailModal.vue`)**：
  - **剧情简介板块位置重塑与通栏拓宽**：将原位于右侧狭窄元数据列的「剧情简介 (Synopsis)」板块挪至海报与元数据区下方，宽度横向延伸贯穿至右侧边缘（`w-full rounded-2xl p-4 md:p-5`），赋予长篇剧情梗概、双语切换（中文译文/英文原文）及 AI 翻译操作极佳的阅读空间；
  - **核心操作按钮组矩阵化收拢**：将此前分散排布在顶栏及底部的「BT 磁力资源搜索扩展」「在BFTV搜索影片资料」「Google 搜索」「生成影视分享卡片」「收藏电影」等 5 项核心交互按钮，集中归拢并重塑为美观的操作按钮功能组，置于厂牌与导演标签下方；
  - **顶栏元数据净化**：顶栏纯粹聚焦于上映日期/年份、片长及分类标签，消除视觉杂讯。

## [2.16.6] - 2026-10-03

### Optimized
- **“外观主题”设置项布局紧凑化与空白区域切除 (`theme.ts`, `App.vue`)**：
  - 提取“跟随系统 (auto)”为全宽独立顶栏卡片，直观指示当前系统色彩与色相预览；
  - 剩余 6 款具体主题（经典/流体玻璃/暖琥珀 × 深色/浅色）重构为工整对称的 3 列自适应网格（`grid-cols-2 sm:grid-cols-3 gap-2`），彻底消除了原 7 项布局在 2 列网格下第 4 行右侧的巨大空槽与卡片内部过宽空隙，视觉紧凑协调。
- **“应用图标方案”精简至经典与极简 2 种方案 (`appIcon.ts`, `AppIcon.vue`, `commands/system.rs`, `App.vue`)**：
  - 砍掉第 2 款（胶片霓虹 Scheme B）与第 3 款（质感复古 Scheme C）图标资产与分支代码；
  - 仅保留 Scheme A（方案一：经典典藏蓝）与 Scheme D（方案四：黑曜石金）；
  - 设置界面点选矩阵自适应缩减为双列精致卡片，系统托盘与 macOS 原生 Dock 同步仅保留此双色方案，异常缓存自动容灾回退至 Scheme A。
- **多图海报自适应同时平铺呈现 (`MovieDetailModal.vue`)**：
  - 重构影片档案页封面呈现：若影片拥有 2 张或以上封面图片（正封面、封底写真、多版本海报），自适应横向平铺同时展现（`flex-row flex-wrap gap-3`）；
  - 各封面均配备独立 Front/Back 序号角标与悬浮放大按钮，点击均可直接调用高清 Lightbox 交互灯箱，满足一屏纵览全部封面。

### Removed
- **移除海报展示与翻转排版方案设置项 (`App.vue`, `privacy.ts`, `types.ts`, `MovieDetailModal.vue`, `i18n/index.ts`)**：
  - 彻底移除设置中“海报展示与翻转排版方案”设置卡片（Section 0.2）及其关联的隐私配置与类型声明；
  - 移除影片详情中的 3D 拟真翻转实体卡片与单图标签切换模式，简化交互路径。
- **移除影片档案页“Rating/评星打分”功能及相关代码 (`MovieDetailModal.vue`, `i18n/index.ts`)**：
  - 彻底移除影片档案页剧情简介下方的 5 星交互打分条及清除评分功能；
  - 释放页面垂直留白，右侧影视磁链与搜索插件快捷入口平齐舒展排布。
- **移除“AI 偏好画像”插件及全链路代码 (`PluginsView.vue`, `pluginManager.ts`, `api.ts`, `commands/translate.rs`, `lib.rs`, `i18n/index.ts`)**：
  - 彻底删除 `aiAnalysis.ts` 与 `AiAnalysisModal.vue` 文件及前端组件调用；
  - 下线插件管理中心中的 “AI 偏好画像” 专区与选项卡，插件总数收敛为 3 款（磁链搜索、数据刮削、大模型翻译引擎）；
  - 移除后端 `run_ai_analysis` 与 `run_ai_analysis_blocking` 异步执行代码与 Tauri Command 绑定；
  - 全量清理所有相关已废弃的 39 个 i18n 词条，确保 7 大语种保持 919 键绝对 100% 镜像对齐。

## [2.16.5] - 2026-10-03

### Optimized
- **影片档案页收录章节与场景片段全文展开与阅读体验全面优化 (`EpisodeRow.vue`, `MovieDetailModal.vue`, `App.vue`)**：
  - **行内无限制展开与折叠**：彻底解决 `EpisodeRow.vue` 长期硬编码 `line-clamp-4` 导致平均 435 字符、长达 1,400+ 字符的分集简介被截断无法查看的问题。在字数超出阈值（>90 字符或含换行）时，动态展示展开（`ChevronDown`）/收起（`ChevronUp`）按钮；
  - **直观便捷的多维交互触控**：
    - 未展开状态下，轻触/点击简介正文即可直接平滑展开为全文（`whitespace-pre-line select-text text-fg-2`）；
    - 针对长篇剧本级简介，在底部额外提供优雅的「收起」快捷按钮，无需用户向上回滚查找即可快速收折；
    - 原文/中文翻译切换按钮与展开折叠按钮自适应并排排布，互不干扰；
  - **章节板块全局一键展开 / 收起 (`MovieDetailModal.vue`)**：
    - 在影片档案页「收录章节 / 场景片段」标题栏右侧新增「展开全部 / 收起全部」全局批处理开关，一键舒展本片全部章节简介；
    - 切换或打开不同影片时，全局折叠状态自动重置，保持版面清爽利落；
  - **分集深层档案穿透联动 (`MovieDetailModal.vue`, `PerformerDetailModal.vue`, `App.vue`)**：
    - 分集行标题接入悬浮跃迁标识，点击直接触发 `@select-episode-id` 并在 `App.vue` 中唤起顶层 `EpisodeDetailModal`，实现从影片详情向独立分集档案与高清剧照的无缝穿透。

### Removed
- **精简设置面板与移除多余说明及伪装选项 (`App.vue`, `theme.ts`, `appIcon.ts`, `privacy.ts`, `i18n/index.ts`)**：
  - **移除 AI 影迷画像全球化相关代码**：回滚 `aiAnalysis.ts` 与 `AiAnalysisModal.vue` 中的英文大模型 Prompt 分支及多语种交互代码，还原纯粹原生的分析逻辑；
  - **精简应用图标方案 (App Icon)**：彻底移除图标卡片中的副标题、风格解析、私密星级评分、标签、说明及格式下载按钮等冗余文字，重构为极致清爽的 4 图案网格点选矩阵；
  - **移除窗口伪装标题 (Disguise Title)**：下线应用设置中的窗口伪装标题选项及 `privacy.ts` 中对应的存储与 `document.title` 同步逻辑；
  - **清除外观主题说明文字**：移除外观设置中对每种具体主题（暗黑、浅色、经典、玻璃、暖琥珀等）的长串说明文字与 `theme.ts` 中的 `hint` 属性，升级为对齐清爽的单行色块 + 标题布局；
  - **同步清理无用国际化词条 (`i18n/index.ts`)**：全量清理 7 大语种字典中已废弃的 63 个冗余 Key，各语种字典统一维持 958 键 100% 镜像对齐。

### Added
- **国际化字典多语种扩充 (`i18n/index.ts`)**：
  - 新增 `common.collapseAll` 词条；
  - 7 大受支持语种（`zh-CN`, `zh-TW`, `en`, `ja`, `it`, `es`, `de`）严格维持 958 键 100% 镜像对齐，并通过 `test_parity.py` 自动化测试。

## [2.16.4] - 2026-10-03

### Optimized
- **厂牌历史档案与风格深度解析专栏全方位优化与版面拉长扩展 (`StudioDetailModal.vue`, `i18n/index.ts`)**：
  - **版面拉长与通透排版重构**：针对用户反馈专栏过窄、正文完全无法显现的痛点，彻底移除限制性约束，升级为宽幅通透的影视典藏专栏（`p-6 md:p-8 rounded-3xl`，典雅双层标题 + 美学透视副标题 + `Sparkles` 深度解析标签 + 左侧 `border-accent/50` 导赏引言边框 + 典藏水印）；
  - **文字排版与阅读体验跃升**：升级为 `15px` 高清正文字号、舒适行间距（`leading-relaxed`）与高对比度文字（`text-fg`），保留换行格式并支持原生文本选中（`select-text whitespace-pre-line`），让 181 家主流与地下先锋厂牌（如 Factory Video Productions 工厂影像）的百科级沿革考据与美学风格深度解析得以完整、醒目、舒展呈现；
  - **数据获取三级容灾回退与主动异步加载**：
    - 重构 `effectiveDescriptionZh` 计算属性，使用 `||` 级联回退：优先读取 `props.works?.description_zh`，其次回退至 `props.studio?.description_zh`，最后回退至组件内部独立拉取的数据 `fetchedDescriptionZh`；
    - 增加 `loadStudioArchive` 侦听机制（`watch props.studio?.name` 与属性变动）：若任一链路在父层未带回档案简介，子组件毫秒级主动调取 `api.getStudioWorks` 自动补齐，彻底杜绝空白与内容缺失。
  - **全语种常驻可见与中英文无缝切换**：无论用户处于何种界面模式，只要存在厂牌档案即优雅呈现，并在切换至原文/中文时无缝联动。

### Fixed
- **底层查询与跨端服务数据契约极致稳健化 (`queries/studios.rs`, `server.py`, `db_manager.py`, `App.vue`)**：
  - **大小写无关与中英文双向兼容匹配**：Rust 端 `queries::studios::get_studio_works` 增加 `WHERE trim(name) = trim(?1) COLLATE NOCASE OR trim(name_zh) = trim(?1) LIMIT 1`；`get_studio_library` 在 `LEFT JOIN studios` 时采用 `ON (trim(st.name) = trim(s.name) COLLATE NOCASE OR trim(st.name_zh) = trim(s.name))`，彻底消除因片商名称末尾空格、大小写差异或中文别名传参导致的档案落空问题；
  - **Python 服务端（server.py & db_manager.py）完全同步**：在 `db_manager.py:list_studios` 与 `server.py:handle_studio_works` 中补齐 `studios` 表联查逻辑与大小写不敏感匹配，保证无论运行在原生 Tauri 模式还是 Web 服务端模式下均能返回完整的 `name_zh` 与 `description_zh`；
  - **前端状态反向沉淀与补全 (`App.vue`)**：`openStudioDetail` 在作品信息加载完成后，若 `selectedStudio` 缺失 `name_zh` 或 `description_zh`，自动由 `works` 数据进行反向补充沉淀。

### Added
- **国际化字典扩充与 100% 绝对镜像对齐 (`i18n/index.ts`, `scripts/test_parity.py`)**：
  - 规范与扩展片商专栏三件套条目：`studio.historyArchive`（'厂牌历史档案与风格专栏'）、`studio.historyBadge`（'深度解析'）、`studio.historySubtitle`（'厂牌沿革与美学风格深度透视'）；
  - 7 大语种（`zh-CN`, `zh-TW`, `en`, `ja`, `it`, `es`, `de`）严格保持 1,020 键 100% 1:1 对齐，并通过 `test_parity.py` 自动化测试验证。

## [2.16.3] - 2026-10-03

### Added
- **全面多语言字典扩充与 100% 键位镜像对齐 (`i18n/index.ts`, `scripts/test_parity.py`)**：
  - **7,133 条高精度结构化翻译**：国际化条目扩充至 **1,019 个唯一键 / 语种**，7 大受支持语种（`zh-CN`, `zh-TW`, `en`, `ja`, `it`, `es`, `de`）实现 100% 1:1 绝对镜像对齐；
  - **自动化键位对齐检验脚本 (`test_parity.py`)**：建立自动化 CI 校验层，严格拦截任何语种键位不对齐或翻译回退缺陷；
  - **全链路界面交互字典覆盖**：新增版本在线检测与下载全流程状态词（`update.apiFailed`, `update.noPackage`, `update.networkTimeout`, `update.emptyStream` 等）；新增应用图标方案多语种标签（`iconScheme.*.tags`）；新增 AI 影迷艺术画像分析多语种提示词与报告字段（`plugins.aiPersonaReportTitle`, `plugins.aiFailFallback`, `plugins.aiFilmsCount`, `plugins.aiStudioLabel` 等）。
- **客户端多语言规范体系文档发布 (`docs/UI_I18N_SPEC.md`, `CODING_WIKI.md`)**：
  - 正式建立全套《客户端界面多语言与本地化规范》（`UI_I18N_SPEC.md`），详尽阐述「二元解耦设计哲学」、「7 种语言矩阵定义」、「1:1 对齐铁律」、「`t()` 函数防中文污染用法」、「高危区域排查检查清单」与「三步自动化验收流程」，供团队与后续助手严格遵循。

### Fixed
- **英文及非中文模式下硬编码中文界面残存彻底清除**：
  - **主导航与侧边栏 (`Sidebar.vue`)**：清除导航分组标题、主页悬浮提示与分类导航栏（影片库、演员库、片商库、导演库、分集库、我的收藏）中的中文回退字符串；
  - **片商与导演协作联动 (`DirectorDetailModal.vue`, `StudioDetailModal.vue`)**：修复合作厂牌展开折叠计数器中残留的硬编码「更多」，全量改由 `t('common.more')` 与 `t('common.collapse')` 动态派发；修复筛选无结果提示；
  - **分集标题与出处 (`EpisodeRow.vue`, `utils/episode.ts`)**：移除未命名分集硬编码「未命名片段」，统一调用 `t('episode.unnamed')`；外语环境下影片出处格式由中文书名号 `《片名》` 优雅切换为国际规范双引号 `"${movieTitle}" · ${position}`；
  - **多维筛选抽屉与全局胶囊条 (`FilterDrawer.vue`, `ActiveFilterBar.vue`, `App.vue`)**：修复时间范围筛选胶囊中「全部」、「上次入库」等文字在英文界面下显示中文的缺陷；演员多维体貌属性（体型、发色、肤色、瞳色、胡须等）筛选标签在 `ActiveFilterBar` 中接入 `facetLabel` 动态本地化映射；
  - **全屏海报灯箱 (`ImageLightbox.vue`)**：清除顶部标题回退文本「全屏海报灯箱」，纯净调用 `t('lightbox.title')`；
  - **流光分享卡片 (`ShareCardModal.vue`)**：修复正向封面 `alt` 标签中硬编码的「(封面)」，与封底统一调用 `t('movie.frontCover')`；Canvas 渲染与本地存盘异常统一抛出规范化英文 Error；
  - **应用图标方案预览卡片 (`appIcon.ts`, `App.vue`)**：新增 `getIconSchemeTags` 响应式提取器，将方案 A~D 底部特色标签（如「双雄图腾」、「黑曜石质感」等）全量本地化为对应语言；
  - **浏览足迹与探索统计 (`analytics.ts`, `AnalyticsView.vue`)**：`BrowseHistoryItem` 类型联合扩展包含 `'episode'`，分集足迹保存纯净标题并由前端统一赋予 `t('nav.episodes')` 勋章，彻底清除历史遗留的硬编码「片段: 」前缀；
  - **刮削器与定时同步状态 (`scraper.ts`, `autoSync.ts`)**：清除 scraper 初始默认状态中的中文「就绪」，由 UI 视图层根据语种调用 `t('sync.ready')`；清除 `autoSync.ts` 计算下一次执行时间时的中文参数。

### Optimized
- **AI 影迷专属艺术画像与偏好深度洞察引擎全球化改造 (`aiAnalysis.ts`)**：
  - 深度重构 LLM 导赏提示词生成逻辑：在中文模式下保留文学级深邃中文导赏词，而在英文及其他语种模式下，动态组装包含四核心版块（Core Persona, Studio Aesthetic, Performer Chemistry, Curated Discovery）的纯正英文电影学者级 Prompt，并指令大语言模型根据当前用户界面语种输出结构化报告与 JSON；
  - 导出 Markdown 报告与保存文件名（`exportAiReportMarkdown`）支持中英文自动化排版与文件名转换。
- **TypeScript 全局类型安全增强 (`App.vue`, `types.ts`, `PerformerDetailModal.vue`)**：
  - 补齐 `STUDIO_SORTS` 与 `DIRECTOR_SORTS` 明确的计算类型声明，解决 `string` 赋值给联合类型的编译阻断缺陷；
  - 修正 `PerformerDetailModal.vue` 缺失的 `SupportedLocale` 导入；
  - 清理 `ActiveFilterBar.vue` 中多余的未引用类型；
  - `vue-tsc -b && vite build` 生产构建 100% 零错误顺利通过。

## [2.16.2] - 2026-10-03

### Added
- **厂牌历史档案与风格深度解析专栏上线 (`StudioDetailModal.vue`, `i18n/index.ts`)**：
  - **专业影视百科级专栏质感**：在片商档案详情弹窗（Studio Modal）重磅引入「厂牌历史档案与风格专栏」（Studio History & Brand Archive），配备专属典雅渐变卡片、典籍标识、深度解析标签与水印徽标；
  - **181 家核心厂牌深度文化渊源入库**：完整收录 181 家主流与先锋厂牌创立年代、创始人背景、命名文化意涵、美学演进与行业开创性地位解析（如 Falcon Studios、Raging Stallion、Treasure Island Media 等详尽深度档案），瞬间赋予片商档案页 Criterion Collection / 影视百科全书般的厚重质感；
  - **多语种本地化适配**：专栏标题与深度解析标签全语种覆盖（`studio.historyArchive`, `studio.historyBadge`），并配备语言即时切换（`Languages`）控件，支持随时在中文百科与原文视图间无缝切换。
- **全站片商中英双显与智能双语检索体系 (`bilingual.ts`, `App.vue`, `types.ts`, `gpdb-core`)**：
  - **中文模式「主标题中文、副标题浅灰英文」**：
    - 片商档案库网格卡片（Studio Grid）与厂牌详情弹窗（Studio Modal）在中文环境下均采用标准双层标题设计：顶部主标题突出展示纯中文厂牌名（如「猎鹰影视」），下方以浅灰微缩字体清晰呈现英文原名（如「Falcon Studios」），兼顾本土化阅读亲和力与全球影视索引精准度；
    - 英文模式（English）自动隐藏副标题空占位与中文文本，保持纯正精炼的欧美原版排版；
  - **中英文双向模糊检索支持 (`queries/studios.rs`)**：
    - 底层 Rust 查询 `get_studio_library` 重构：在 `WHERE` 条件中无缝支持原名与中文译名双向匹配 `(s.name LIKE ? OR st.name_zh LIKE ?)`，用户不论输入中文（如「猎鹰」）或英文原名（如「Falcon」），均能极速直达；
  - **自包含离线数据库架构 (`schema.sql`, `migrate.rs`, `translation_manager.py`)**：
    - 在主数据库 `GPDb.db` 中建立一等公民 `studios` 实体表（包含 `name`, `name_zh`, `description_zh`），通过 `translation_manager.py --sync` 将 `translations.db` 中 362 条厂牌结构化条目完整同步沉淀，保证移动端（iOS / Android）与桌面端离线运行时 100% 自包含且零外部依赖；
    - Rust 迁移层 `migrate.rs` 镜像维护 `CREATE TABLE IF NOT EXISTS studios` DDL 与索引，启动时毫秒级自动就绪；
    - 补充 `gpdb-core/src/queries/studios.rs` 完整单元测试与 `translate_config.rs` 多 Key 轮换池兼容支持。

### Removed
- **冗余状态按钮与私密备忘功能全面移除 (`MovieDetailModal.vue`, `App.vue`, `userAnalytics.ts`, `aiAnalysis.ts`)**：
  - **移除「想看」与「已看」标记按钮**：彻底清理影片详情弹窗内的「想看」（wishlist）和「已看」（watched）胶囊按钮，并将 5 星打分（Rating）提升为简介正下方的一级常驻控件，排版更为精炼整洁；
  - **移除「便签与标签」抽屉功能**：移除「便签与标签」展开按钮及弹出的「自定义分类标签」与「私密备忘 / 观后感」关联代码，释放详情页垂直空间与交互复杂度；
  - **收藏库（Favorites）视图深度精简**：从收藏主页面与子分类筛选胶囊中移除「想看片单」与「已看记录」板块及统计逻辑，收藏页面纯粹聚焦于喜爱影片、系列专题、演员、片商、导演与分集片段；
  - **底层统计与分析逻辑同步清理**：同步清理 `userAnalytics.ts` 与 `aiAnalysis.ts` 中针对 `wishlist` 与 `watched` 的多余合并与计算，保障前端各模块状态与数据流轻量纯粹。

## [2.16.1] - 2026-10-02

### Added
- **多语种体系重构与数据库语种自动匹配联动 (`i18n/index.ts`, `bilingual.ts`, `prefs.ts`, `App.vue`, `EpisodeRow.vue`, `StudioDetailModal.vue`, `MovieDetailModal.vue`, `MovieCard.vue`)**：
  - **二元文本体系解耦设计**：
    - 严谨分离「客户端界面菜单文字（UI Texts）」与「数据库内部实体文本（DB Content Texts）」两大体系；
    - 客户端系统菜单、导航、筛选抽屉、按钮标签、通用状态提示等静态文字全量收敛至 `i18n/index.ts` 字典（覆盖 zh-CN, zh-TW, en, ja, it, es, de 7 种语言），彻底拔除各组件与视图内的硬编码中文字符；
    - 增强 `t()` 国际化函数，原生支持 `{param}` 动态变量插值与优雅回退机制；
  - **数据库内容语种自动匹配与联动矩阵**：
    - **英文模式 (English)**：当用户在设置中选择英文界面时，数据库文本自动进入纯原文模式。影片与分集主标题仅显示英文原名（自动隐藏中文副标题与空占位），剧情简介仅展示英文原版，界面干净紧凑；
    - **中文模式 (Chinese)**：当用户选择简体或繁体中文时，自动启用中英双显模式。厂牌详情弹窗（Studio Modal）、影片卡片等主标题优先展示中文译名、副标题展示浅灰英文原名；剧情简介优先展示中文译文，并保留一键对比原文的切换控件；
    - **设置面板三档独立模式自由切换**：在「缓存与设置」->「语言与本地化」新增【数据库内容呈现模式】配置（支持 `自动匹配 (推荐)`、`始终中英双显`、`始终仅看原文`），满足用户的个性化阅读偏好；
  - **核心组件多语种体验升级**：
    - **厂牌详情弹窗 (`StudioDetailModal.vue`)**：全面国际化固定文本（片商档案、完整电影、片段/分集、正在读取、无收录提示等），弹窗下长片与分集列表无缝响应当前语种策略；
    - **分集列表行 (`EpisodeRow.vue`)**：出处完整支持双语标题展示，国际化所有静态标签，并在单行分集简介右侧新增微型即时切换按钮（`显示原文 / 显示译文`），无需打开弹窗即可随时对比原文；
    - **影片卡片与详情弹窗 (`MovieCard.vue` & `MovieDetailModal.vue`)**：详情弹窗内的「显示原文 / 显示中文」按钮协同联动下方收录分集列表的显示语种；卡片在英文模式下自动收起副标题高度，排版更加精致。

### Fixed
- **片商档案页作品与分集数量统计重大缺陷修复 (`gpdb-core/src/queries/studios.rs` & `sql.rs` & `episodes.rs`)**：
  - **根因定位与根治**：修复底层 Rust 查询在 `get_studio_works` 中错误使用母片片商 `WHERE m.studio_name = ?1` 过滤分集，导致 102,415 个无母片的独立网络单集（`movie_id IS NULL`）及 4,573 个被母长片合辑或分销商标记的分集被全部漏计（例如 Raw Fuck Club 漏计 13,830 个分集，仅显示 44 个的严重数据偏离）；
  - **三端标准对齐 (Effective Studio Resolution)**：统一对齐 Android 与 iOS 标准算法，采用 `COALESCE(NULLIF(trim(e.studio_name), ''), m.studio_name)` 优先判定分集自带的独立厂牌；修复后 Raw Fuck Club 统计数量恢复为 13,874 部，与 Android 客户端完全一致；
  - **片商库聚合列表重构 (`get_studio_library`)**：彻底重构 `get_studio_library` 分页与计数 SQL，采用 `movies` 与 `episodes` 片商并集（UNION）双路联合聚合，完整收录仅发行独立分集的片商（如 Cade Maddox、College Boy Physicals、Freshmen.net 等），并将作品数与分集数统计性能优化至毫秒级响应；
  - **单集查询排序优化**：分集列表排序采用 `ORDER BY COALESCE(m.release_year, CAST(substr(e.release_date, 1, 4) AS INTEGER)) DESC, e.id DESC`，解决独立单集母片年份为空时的排序异常。
- **片商详情页海量分集平滑渐进式渲染优化 (`StudioDetailModal.vue`)**：
  - 为 `StudioDetailModal.vue`（macOS 桌面端及 Windows 端同步装配）新增分批渲染与滚动加载保护机制；
  - 首批渲染 60 个分集卡片，当用户向下滑动接近底部或点击底部「加载更多片段」时动态分批扩增 60 项；
  - 彻底杜绝万级作品厂牌（如 Raw Fuck Club 的 13,874 个分集、William Higgins 的 9,956 个分集）在打开瞬间一次性挂载海量 DOM 节点导致页面卡死或渲染崩溃的问题，保证 UI 极致流畅。
- **核心数据库索引与一致性测试完善 (`schema.sql` & `parity.rs`)**：
  - 在 `schema.sql` 中补充 `CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);`，将全库 15 万+分集按片商聚合的查询耗时缩短至 30ms 以内；
  - 更新 `parity.rs` 中的 5 项片商与分集断言，确保自动化测试 100% 覆盖有效厂牌解析逻辑。

## [2.15.0] - 2026-10-01

### Fixed
- **外链点击无法跳转浏览器缺陷修复 (`App.vue` & `PerformerDetailModal.vue` & `main.ts`)**：
  - 根除 Tauri WebKit 沙箱拦截普通 HTML `<a href="..." target="_blank">` 导致设置页「访问仓库 ↗」及演员档案页「互联档案」（IAFD、IMDb、X等）点击无响应的缺陷；
  - 在 `main.ts` 中集成全局外链点击委托分发机制，显式为对应链接绑定 `@click.prevent="openUrlExternal(...)"`，通过系统命令原生安全唤起 macOS 默认浏览器。
- **P0 级致命卡死与崩溃根治：Objective-C 剪贴板 Over-Release 修复 (`system.rs`)**：
  - 修复 `commands/system.rs` 中 `copy_image_to_clipboard` 对 `+[NSData dataWithBytes:length:]` 与 `+[NSString stringWithUTF8String:]` 返回的自动释放（Autoreleased）对象错误显式调用 `release` 导致的 Double-Free 内存崩溃；
  - 彻底杜绝主线程 AppKit / NSPasteboard 损坏及转入保存图片时的伪死锁（Beachball）。
- **分享卡片选择模糊后保存未模糊 Bug 根除 (`ShareCardModal.vue`)**：
  - 解决 macOS WebKit 2D Canvas 下 `ctx.filter` 硬件加速未完整支持或失效的问题；
  - 引入硬件中立的双离屏双线性多级降采样模糊算法（Hardware-Agnostic Bilinear Downsample Blur），生成高质感磨砂打码效果并大幅降低主线程 GPU 栅格化开销；
  - 剧情简介勾选模糊时叠加半透明磨砂脱敏盖章，彻底杜绝文字内容泄露。
- **分集剧照分享比例拉伸形变修复 (`ShareCardModal.vue`)**：
  - 自动区分电影（3:4 竖版）与分集剧照（16:9 横版展台）；
  - 实现 Canvas 版 `object-fit: cover` 居中防形变裁剪算法，确保剧照中人物面部绝不变形。

### Added
- **全平台统一通用用户配置与数据备份/恢复机制 (`App.vue` & `services/analytics.ts`)**：
  - 设置页「数据管理」中新增「用户配置与数据备份/迁移 (跨设备通用 JSON)」模块；
  - 导出范围覆盖用户在 SQLite 中的全部「我的收藏」（包含长片、分集、演员、导演、片商、系列等）、自定义标签、想看/已看状态与私密评星笔记，以及全量本地统计数据（总专注时长、各维度播放与探索计数、活跃天数等）与界面偏好；
  - 导出格式采用统一规范 `gpdb_universal_backup` JSON，支持在 macOS、Windows、Android 与 iOS 四端通用互认，跨设备直接导入无缝迁移。
- **PBC 与 SmutJunkies 演员刮削引擎深度集成与自动入库 (`src-tauri/src/commands/sync.rs` & `ScraperConfigPanel.vue`)**：
  - 在底层 Rust 命令 `sync.rs` 中完整打通 `scrape_pbc_actors.py` 与 `scrape_smutjunkies_actors.py` 执行流水线；
  - 支持前台进度正则捕获（条目索引、总数、新增与更新数）、实时日志流与终止保护；
  - 刮削执行完成自动以 `--apply` 与 `--backfill` / `--home` 将数据无损注入 SQLite 数据库。
- **分享卡片支持封面与封底双海报并排展示 (`ShareCardModal.vue` & `MovieDetailModal.vue`)**：
  - 分享卡片全面支持同时加载正向海报封面与反面封底（如有）；
  - 采用优雅并列卡片排版，左右标明「封面」与「封底」半透明质感胶囊，并无缝适配双离屏模糊保护与 2x Retina 高清导出。
- **分享卡片角落新增 Telegram 官方频道二维码 (`ShareCardModal.vue`)**：
  - 在卡片底部角落集成 27x27 高清点阵二维码，直连官方频道 `https://t.me/gpdbnews`；
  - 采用白底圆角容器与高保真点阵矢量渲染，适配 Retina 2x 高清导出与预览，并搭配官方频道文字标示。
- **设置页新增「检查更新」按钮与即时反馈 (`App.vue` & `services/appUpdater.ts`)**：
  - 设置「关于与软件更新」板块中新增「检查更新」快捷按钮，支持随时手动触发检查 GitHub 官方 Releases 仓库；
  - 检查中呈现动态转圈状态，若当前已是最新版则显示「当前已是最新版本 (v2.15.0)」友好提示；若发现新版则弹出更新视窗并支持一键下载安装包更新。
- **启动静默巡检 GitHub Releases 与 DMG 在线挂载更新 (`AppUpdateModal.vue` & `appUpdater.ts`)**：
  - 启动 3 秒后异步巡检 GitHub API，对比语义化版本号；
  - 弹出拟态浮层展示更新说明与体积；
  - 支持单线程断点进度条下载，下载完成后调用 Rust 命令 `open <dmg_path>` 自动挂载 DMG 镜像，引导用户覆盖升级。

### Changed
- **设置页二级胶囊菜单新增「关于与更新」独立分类 (`App.vue`)**：
  - 顶部胶囊导航新增「关于与更新」独立分类，便于用户快速切换直达版本更新与开源仓库专区。
- **连贯篇章系列大放送随机抽取展示 (`HomeView.vue` & `gpdb-core/src/queries/series.rs`)**：
  - 首页「经典系列大放送 · 连贯篇章」由原有固定以合集中影片数量降序（`ORDER BY movie_count DESC`）改为随机抽取排序（`ORDER BY RANDOM()`）；
  - 模块标题栏新增「换一批」快捷随机刷新按钮，每次刷新呈现不同精品系列。
- **设置页排版大幅净化与图示化 (`App.vue`)**：
  - 彻底移除设置中对「应用图标方案 (App Icon)」的冗长文字说明；
  - 将「海报展示与翻转排版方案」的大段陈述性文字全面升级为直观的交互式微缩示意图（自适应平铺画廊 vs 3D 景深翻转卡片），所见即所得。
- **分享卡片全维度自适应排版与纯粹流光背景 (`ShareCardModal.vue`)**：
  - 精简背景光晕切换选项，统一保留视觉表现最佳的默认「流光 (vibrant)」渐变背景；
  - 演职员名单完整换行展示，移除 `slice(0, 4)` 截断限制；
  - 剧情简介移除 `line-clamp-4` 截断，文本度量算法自动计算所需高度并动态分配 Canvas 高度；
  - 彻底解耦全局防窥模式（`screenshotPrivacyEnabled`），卡片预览与保存导出不受主界面全局滤镜干扰。
- **精简首页视觉与算力开销 (`HomeView.vue`)**：
  - 移除首页顶部的「AI 专属定制导赏」Banner 模块，保留插件页（`PluginsView.vue`）原有独立 AI 模块不受影响。

### Removed
- **彻底移除奖杯成就系统插件及全量关联代码**：
  - 移除 `services/trophySystem.ts`、`components/FluidGlassTrophyIcon.vue`、`components/TrophyResetModal.vue`、`components/TrophyToast.vue`、`views/TrophiesView.vue`；
  - 从侧边栏、分析看板、插件管理器与国际化字典中彻底剔除成就依赖，大幅精简打包体积与后台监听。
- **移除桌面端「全功能伪装计算器 & 紧急脱身 (Panic Switch)」功能及代码**：
  - 遵循「仅手机端保留物理伪装脱身，桌面端回归纯粹高质感」准则；
  - 移除 `FakeCalculatorModal.vue`、快捷键侦听、全局防窥联动及设置页开关，净化桌面端使用体验。
