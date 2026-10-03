# Changelog (GPDb Windows Client)

All notable changes to the GPDb Windows Client project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.16.7] - 2026-10-03

### Fixed
- **用户自填大模型 API Key 剧情与分集 AI 翻译失效彻底修复（全量对齐 Android 端行为）(`commands/translate.rs`, `PluginsView.vue`, `api.ts`, `MovieDetailModal.vue`, `EpisodeDetailModal.vue`, `i18n/index.ts`)**：
  - **单条翻译纯文本解析兼容（消除 JSON 强制反序列化报错）**：查明此前用户配置自己的 API Key（如 DeepSeek/OpenAI/Moonshot/Claude 等）并在详情页点击 AI 翻译时，模型按直觉返回纯中文译文（正如 Android 端一样），但桌面端后端因强制调用 `text.find('{')` 解析 JSON 结构而导致抛出 `未在模型返回中找到有效 JSON` 并静默失败的根源；重构 `extract_translations`，当单条翻译（`expected == 1`）时优先容纳直接返回的纯中文正文，智能清理前缀序号与外层包裹，与 Android 端 `LLMTranslationService.kt` 行为 100% 对齐；
  - **OpenAI 兼容接口去除单条强制 `response_format` 并支持智能 400 重试**：移除单条翻译请求中的 `"response_format": {"type": "json_object"}`，彻底杜绝 DeepSeek、Moonshot、通义千问、本地 Ollama 以及各种第三方 OpenAI 代理网关因不支持或提示词未显式包含 "json" 词汇而抛出 `HTTP 400 Bad Request` 的问题；多条翻译时若服务端仍报错 400 则自动去掉该参数重试；
  - **端点 URL 自适应规范化补齐 `/v1`**：对于用户填入的 `https://api.openai.com`、`https://api.deepseek.com` 或反代域名，智能按协议规则自适应规范化补齐端点为 `/v1/chat/completions` 或 `/chat/completions`，避免 404；
  - **来源保存自动激活与配置多路径智能漫游**：保存或编辑自填 API Key 时若当前尚未激活任何来源则自动设为生效来源，并新增“设为默认生效的翻译来源”直观切换开关；配置文件 `translate_config.json` 采用数据库同级目录与系统用户数据目录（`%APPDATA%/com.gpdb.app`）智能双向回退与合并加载，杜绝无写权限目录报错；
  - **错误提示精准穿透直达 UI**：`api.ts` 与详情弹窗彻底废止吞掉报错返回 `null` 的做法，接口若报错（如 Key 无效、网络超时或未配置）实时将原因精准呈现在界面中，告别迷茫。
- **桌面端大模型剧情与分集 AI 翻译无响应与按钮不可用彻底修复 (`commands/translate.rs`, `src-tauri/src/lib.rs`, `src/api.ts`, `MovieDetailModal.vue`, `EpisodeDetailModal.vue`, `PluginsView.vue`)**：
  - **纯 Rust 原生进程内翻译引擎**：彻底替换此前前端直连 `server.py` HTTP 接口的调用链，在桌面端内核中原生实现纯 Rust TLS 翻译引擎（支持 Google Gemini、OpenAI 兼容协议如 DeepSeek、Moonshot、智谱、通义千问、硅基流动、本地 Ollama 以及 Anthropic Claude 等全部模型）；
  - **Google Gemini API Key 智能轮换与配额保护**：针对 Gemini Free Tier 每日 500 次配额限制 (`RESOURCE_EXHAUSTED` / `429`)，原生实现原子级 API Key 轮换池，单 Key 额度耗尽自动无缝切换备用 Key；全部 Key 耗尽时自动智能转交 DeepSeek 等备用服务商补救，避免任务中断；
  - **影片档案页 AI 翻译全场景解除桌面端限制**：移除此前因缺少 HTTP 服务而设定的 `!IS_TAURI` 禁用条件与“未翻译”死文本，影片剧情与未翻译分集翻译按钮全量点亮并支持一键翻译联动；
  - **插件设置页桌面端直接「试译」**：解除原“桌面版暂不支持在此试译”限制，用户填入 API Key 后可直接在设置界面实时连通大模型进行单句试译测试并查看延迟与质量反馈。
- **“厂牌历史档案与风格深度解析专栏”正文展示与版面舒展修复 (`StudioDetailModal.vue`, `App.vue`, `i18n/index.ts`)**：
  - **彻底修复正文展示一瞬间后收缩变窄的布局缺陷**：查明因父弹窗容器采用 `max-h-[90vh] flex flex-col` 布局，当作品列表（如 Man's Best 的 622 部影片）加载完成突发撑高时，未设置 `shrink-0` 的历史专栏被 Flexbox 强制收缩并将正文裁剪溢出的根本原因；显式为 Header、专栏卡片与 Works Section 容器注入 `shrink-0 min-h-fit`，杜绝任何弹性压缩；
  - **状态持久化防抖与响应式缓存保障 (`cachedDescriptionZh`)**：构建独立响应式缓存机制与多级兜底（`props.works` -> `props.studio` -> `fetchedDescriptionZh` -> `cachedDescriptionZh`），配合 `App.vue` 实时元数据回填，确保不论从片商库、收藏夹或跳转进入，正文首帧即显且在作品异步加载完成后恒定完整保留；
  - **专栏卡片升级大画幅舒展架构**：卡片采用全宽舒展结构（`w-auto mx-6 md:mx-8 p-6 md:p-8 rounded-3xl`），搭配左侧专属主色边线（`border-l-2 border-accent/50`）、典籍徽标（`BookOpen`）与深度解析标签（`Sparkles`）；
  - **优雅排版与多语言对齐**：正文排版采用 `text-sm md:text-[15px] leading-relaxed text-justify select-text whitespace-pre-line`，完整呈现 181 家核心厂牌创立年代、创始人背景、美学流派与文化演变深度历史档案；全语种统一更新标题为「厂牌历史档案与风格深度解析专栏」。

### Added
- **分集档案页 (Episode) 全新上线「AI 翻译剧情」与双语对照切换 (`EpisodeDetailModal.vue`, `App.vue`, `api.ts`, `commands/translate.rs`)**：
  - 分集详情弹窗现已支持一键调用 AI 大模型翻译英文剧情并写入本地数据库 `episodes.description_zh`，同时提供中文译文与英文原文一键切换；
  - 翻译完成实时触发 `@episode-translated` 事件，自动同步更新分集列表、影片内分集及全局翻译统计状态。

### Optimized
- **片商档案页头部按钮矩阵化多列重构与标题防遮挡 (`StudioDetailModal.vue`, `ResourceSearchWidget.vue`)**：
  - **彻底修复按钮挤压标题截断缺陷**：针对原 4 颗操作按钮（BT 磁力资源搜索扩展、Google 搜索、双语切换、收藏厂牌）单行横排横向挤占近 500px 宽度，导致左侧厂牌名称被严重挤压截断（如“狂烈...”与“Raging Stal...”）的问题，全面重构操作区为规整对称的 2 列网格矩阵（`grid grid-cols-2 gap-2`），横向宽度缩减近 40%；
  - **`ResourceSearchWidget` 灵动穿透适配**：为外挂资源组件引入 `wrapperClass` 与 `buttonClass`，在多列网格下利用 CSS `contents` 属性让各外链按钮直接作为独立网格单元居中对称排布，兼顾其它视图弹性排版，零代码冗余与零副作用；
  - **厂牌标题与作品统计舒展呈现**：主标题与副标题由单行强制截断升级为 `break-words line-clamp-2 leading-tight`，为中文名称与长英文原名留足空间，作品数与分集数平齐舒展排布，消除视觉拥挤。
- **片商专栏重构为「厂牌介绍」并剔除多余修饰 (`StudioDetailModal.vue`, `i18n/index.ts`)**：
  - **标题纯粹化更名**：将原「厂牌历史档案与风格深度解析专栏」全面精简更名为「厂牌介绍」（全语种镜像同步：Studio Overview / 廠牌介紹 / スタジオ紹介 / Presentazione studio / Presentación del estudio / Studio-Übersicht）；
  - **剔除冗余修饰字样**：彻底移除专栏右上角“深度解析”高亮徽标（`studio.historyBadge` / `Sparkles`）以及副标题“厂牌沿革与美学风格深度透视”（`studio.historySubtitle`），正文保留优雅左侧主色边线与典籍图标，界面更加纯净干练。
- **影片档案页布局重构：剧情简介板块移至海报下方通栏展示，操作按钮组矩阵化收拢 (`MovieDetailModal.vue`)**：
  - **剧情简介板块位置重塑与通栏拓宽**：将原位于右侧狭窄元数据列的「剧情简介 (Synopsis)」板块挪至海报与元数据区下方，宽度横向延伸贯穿至右侧边缘（`w-full rounded-2xl p-4 md:p-5`），赋予长篇剧情梗概、双语切换（中文译文/英文原文）及 AI 翻译操作极佳的阅读空间；
  - **核心操作按钮组矩阵化收拢**：将此前分散排布在顶栏及底部的「BT 磁力资源搜索扩展」「在BFTV搜索影片资料」「Google 搜索」「生成影视分享卡片」「收藏电影」等 5 项核心交互按钮，集中归拢并重塑为美观的操作按钮功能组，置于厂牌与导演标签下方；
  - **顶栏元数据净化**：顶栏纯粹聚焦于上映日期/年份、片长及分类标签，消除视觉杂讯。

## [2.16.6] - 2026-10-03

### Fixed
- **新用户纯数据库导入场景下远程图片加载与自动离线缓存彻底修复 (`utils/image.ts` & `src-tauri/src/commands/cache.rs`)**：
  - **Windows WebView2 协议端点兼容对齐**：WebView2 内核基于安全沙箱规范拦截自定义协议，前端在 Windows 环境下自适应接入 `http://gpdb-img.localhost/?url=...` 规范端点，解决此前直接请求 `gpdb-img://` 导致请求被 Edge 内核当成未注册系统协议而直接静默阻断报错 (`ERR_UNKNOWN_URL_SCHEME`) 的致命缺陷；
  - **原生 Rust HTTP 线程池下载替代外部子进程**：引入轻量级纯 Rust TLS 高性能客户端 (`ureq` + `rustls` + `webpki-roots`) 接管边看边下载流水线。彻底摒弃此前单图并发调用外部 `curl.exe` 子进程导致的进程风暴、控制台窗口闪烁及缺少 curl 环境无法下载的缺陷；内置连接池复用与 Keep-Alive，支持全自动伪装 User-Agent 与防盗链 Referer 头；
  - **移除 302 重定向并直传图片流**：杜绝向 WebView2 返回 302 跳转导致的跨源跨协议拦截 (`ERR_UNSAFE_REDIRECT`)，直接在 Rust 内存层缓冲并以 200 流式输出图片字节，同步安全持久化至本地 `image_cache/`，实现“首次在线直读，后续 100% 离线亚毫秒呈现”；
  - **全量支持外部第三方图片源离线缓存 (`image_cache/External/`)**：`resolve_cache_target_path` 扩展支持对 PBC (PornBaseCentral)、SmutJunkies 及其他外部高清图源进行 URL 哈希持久化映射，全量图片均享本地离线化待遇。

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
  - 根除 Windows WebView2 沙箱拦截原始 HTML `<a href="..." target="_blank">` 导致设置页「访问仓库 ↗」及演员档案页「互联档案」（IAFD、IMDb、X等）点击无响应的缺陷；
  - 在 `main.ts` 中集成全局外链点击委托分发机制，显式为对应链接绑定 `@click.prevent="openUrlExternal(...)"`，通过系统命令原生安全唤起 Windows 默认浏览器（Edge / Chrome 等）。
- **分享卡片选择模糊后保存未模糊 Bug 根除 (`ShareCardModal.vue`)**：
  - 彻底解决 Windows WebView2 下 `CanvasRenderingContext2D.filter` 在部分显卡驱动下静默失效问题；
  - 统一引入硬件中立的双离屏双线性多级降采样模糊算法（Hardware-Agnostic Bilinear Downsample Blur），保证导出图片具备柔和高级的磨砂打码质感；
  - 剧情文字模糊时叠加半透明磨砂脱敏盖章，防止文字可读性泄露。
- **分集剧照分享比例拉伸形变修复 (`ShareCardModal.vue`)**：
  - 分集分享卡片自动切换为 16:9 横屏展板；
  - 实现纯 Canvas 版 `object-fit: cover` 算法，居中裁剪无拉伸变形，彻底杜绝人物面部挤压形变。

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
  - 采用优雅并列卡片排版，左右标明「封面」与「封底」半透明质感胶囊，并无缝适配双离屏模糊保护与高 DPI 导出。
- **分享卡片角落新增 Telegram 官方频道二维码 (`ShareCardModal.vue`)**：
  - 在卡片底部角落集成 27x27 高清点阵二维码，直连官方频道 `https://t.me/gpdbnews`；
  - 采用白底圆角容器与高保真点阵矢量渲染，适配高 DPI 导出与预览，并搭配官方频道文字标示。
- **设置页新增「检查更新」按钮与即时反馈 (`App.vue` & `services/appUpdater.ts`)**：
  - 设置「关于与软件更新」板块中新增「检查更新」快捷按钮，支持随时手动触发检查 GitHub 官方 Releases 仓库；
  - 检查中呈现动态转圈状态，若当前已是最新版则显示「当前已是最新版本 (v2.15.0)」友好提示；若发现新版则弹出更新视窗并支持一键下载安装包更新。
- **启动静默巡检 GitHub Releases 与 EXE 安装包平滑重启升级 (`AppUpdateModal.vue` & `appUpdater.ts`)**：
  - 启动 3 秒后异步巡检 GitHub API，对比语义化版本号；
  - 优先匹配针对 Windows 的 `*.exe` / NSIS 安装包；
  - 弹出拟态浮层展示更新说明与体积，支持单线程断点进度条下载至系统 `%TEMP%` 目录；
  - 下载完成后通过 Rust 命令唤起独立安装向导，并主动调用 `std::process::exit(0)` 平滑退出当前进程释放被占用的文件句柄，避免安装冲突。

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
  - 演职员名单完整换行展示，移除截断限制；
  - 剧情简介移除 `line-clamp-4` 截断，文本度量算法自动计算所需高度并动态分配 Canvas 高度；
  - 彻底解耦全局防窥模式（`screenshotPrivacyEnabled`），卡片预览与保存导出不受主界面全局滤镜干扰。

### Removed
- **彻底移除奖杯成就系统插件及全量关联代码**：
  - 移除 `services/trophySystem.ts`、`components/FluidGlassTrophyIcon.vue`、`components/TrophyResetModal.vue`、`components/TrophyToast.vue`、`views/TrophiesView.vue`；
  - 从侧边栏、分析看板、插件管理器与国际化字典中彻底剔除成就依赖，大幅精简打包体积与后台监听。
- **移除桌面端「全功能伪装计算器 & 紧急脱身 (Panic Switch)」功能及代码**：
  - 遵循「仅手机端保留物理伪装脱身，桌面端回归纯粹高质感」准则；
  - 移除 `FakeCalculatorModal.vue`、快捷键侦听、全局防窥联动及设置页开关，净化桌面端使用体验。

## [2.14.0] - 2026-09-30

### Added
- **Windows 客户端演员档案页深度整合 PBC 百科全维度资料 (`PerformerDetailModal.vue`)**：
  - **严格冲突优先级与已有数据无损原则 (GEVI-First Non-Destructive Resolution)**：
    - 生理规格（身高、体重、尺寸规格、体型、肤色、包皮、发色、瞳色、体毛、胡须、纹身）：严格执行 `p.field || pbc.field`。**只要 GEVI 原始字段有值，100% 保持 GEVI 不变**；仅当 GEVI 字段缺失时，才无缝由 PBC 权威数据填补，绝不丢失已有数据。
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
    - 严格复用 GPDb 的 Surface / Zinc / Accent 配色系统与毛玻璃材质；
    - 移除视觉干扰项：精简移除来自 PBC 的浅紫色攻受角色胶囊（无套定位、安全套定位、内射角色），保持档案信息聚焦克制；
    - 纯粹条件渲染：对于尚未收录 PBC 档案的普通演员，界面保持原汁原味的极简 GEVI 视图，不产生任何空白卡片或未匹配占位符。
- **底层数据库与查询引擎深度支持 SmutJunkies 扩展架构 (`gpdb-core`)**：
  - `migrate.rs` 自动保证 `performers.sj_url` 字段与 `performer_sj_profiles` 扩展表及其索引结构存在；
  - `queries/performers.rs` 自动从 `performer_sj_profiles` 中合并别名，并在 `performers.sj_url` 缺失时自动回退提取扩展表 URL。
- **全新曾用艺名 / 别名 (AKA) 独立徽章栏与动态折叠卡片 (`PerformerDetailModal.vue`)**：
  - 移至人物主卡片下方独立全宽展示，采用 Surface 玻璃拟态与标签设计，支持别名总数角标提示；
  - 智能折叠机制：超过 8 个别名时自动折叠并提供「展开全部 / 收起」按钮，杜绝多艺名演员档案过度拉长；
  - 演员备注说明（Notes）独立全宽呈现，排版更加精致协调。

### Fixed
- **Windows Rust/Tauri 构建依赖修复**：
  - 补充 `Windows_client/src-tauri/Cargo.toml` 中缺失的 `base64 = "0.22"` 依赖。
  - 修正 `database.rs` 中 `get_unique_filepath` 模块私有可见性问题。
  - 修复 `system.rs` 中原生剪贴板跨平台兼容性代码。
  - 通过 `npx vue-tsc -b` 检查与 `cargo check` 编译。

---

## [2.13.0] - 2026-09-29

### Added
- **截屏分享隐私保护与全局高斯防窥模式 (Screenshot Privacy & Blur Mode)**：
  - **手动启用与一键快捷切换**：在设置「隐私与数据安全」中提供专属控制面板，并在顶部导航栏 (`Navbar.vue`) 放置一键“截屏防窥”眼睛开关；支持快捷键与即时状态切换（防窥中 / 正常浏览）。
  - **细粒度隐私脱敏控制**：
    - `模糊海报与剧照图片`：全局高斯模糊 (`blur(24px)`) 所有影视封面、剧照、演职员头像等敏感视觉画面，杜绝截屏或社媒分享时泄露画面；
    - `模糊剧情介绍与敏感文字`：高斯模糊 (`blur(7px)`) 影视简介、分集梗概等敏感文字并禁止文本选中，防止剧透与涉密内容外泄。
- **影片与分集一键精美分享卡片生成器 (Aesthetic Share Card Generator)**：
  - **自适应海报高斯背景取色**：全新架构的 `ShareCardModal.vue` 组件，背景光晕直接从海报封面自适应提取色相并实施高精度大半径高斯模糊 (`blur(45px~60px)`)，搭配暗夜渐变遮罩，呈现 Apple Music / Spotify 级别的流光视效。
  - **海报与文字安全脱敏分享 (Selective Blur for Sharing)**：
    - 在生成分享卡片时，用户可独立勾选「模糊海报封面」（海报注入高斯模糊并覆盖隐私保护徽章）与「模糊剧情介绍」（文字注入高斯模糊并覆盖脱敏标签），实现 100% 安全社交分享。
    - 提供“流光 (Vibrant)”、“深黑 (Dark)”、“午夜 (Midnight)”三种专属背景光晕预设，以及剧情概要显示开关。
  - **超高清 Retina 2x 双重导出**：
    - **复制卡片图片 (Copy Image)**：基于离线 HTML5 Canvas 2D 引擎以 2x 超高清分辨率直接光栅化卡片为 PNG Blob，并无缝写入系统剪贴板（支持在微信、QQ、Telegram、Discord、X/Twitter 等社交聊天软件中直接 <kbd>Ctrl + V</kbd> 粘贴分享）；
    - **保存为图片 (Save Image)**：一键将高质量分享卡片导出至本地磁盘 (`.png`)。
  - **双档案页全面接入**：在影片详情页 (`MovieDetailModal.vue`) 与分集详情页 (`EpisodeDetailModal.vue`) 操作栏全面部署「分享卡片」入口。

---

## [2.12.0] - 2026-09-29

### Added
- **全新独立 Windows 客户端工程体系 (`/Windows_client`)**：
  - **架构解耦与独立维护**：在项目根目录下建立专有 `/Windows_client` 目录，将 Windows 专属的 Tauri 2 配置、打包管线、构建脚本与原生界面微调收拢于单一工程内，彻底避免与 macOS (`desktop_client`) 及 Android (`android_client`) 的开发环境产生任何文件侵染与构建冲突。
  - **开箱即用的一键构建套件**：提供 `build_windows.ps1` (PowerShell) 与 `build_windows.bat` (批处理) 双脚本，自动检查 Node.js (18+) 与 Rust (MSVC) 工具链环境，一键安装依赖并快速编译生成 Windows 安装程序。
  - **完善的工程自述与指引**：新增 `Windows_client/README.md`，详细记录本地开发运行、环境依赖要求（Node 18+、Rust MSVC、WebView2 Runtime）与 NSIS 打包全流程。
- **Windows Python 运行环境智能探测链 (Multi-Source Python Resolution Engine)**：
  - 针对 Windows 平台下 Python 安装形态多样的痛点（官方安装包、微软商店沙盒、Conda、自定义路径），实现多级分层智能探测：
    1. 优先读取用户显式指定的 `GPDB_PYTHON` 环境变量；
    2. 自动检索 `%LOCALAPPDATA%\Programs\Python\Python3xx\python.exe` 独立用户目录；
    3. 自动检索 `%ProgramFiles%\Python3xx\python.exe` 机器全局目录；
    4. 自动检索 `%USERPROFILE%\anaconda3\python.exe` 与 `%USERPROFILE%\miniconda3\python.exe` 科学计算环境；
    5. 降级执行 `python` / `python3` / `py` 命令行探测与快速静默启动校验，确保各类用户即装即用。

### Changed
- **跨平台 CI/CD 自动化构建矩阵解耦 (`.github/workflows/release.yml`)**：
  - GitHub Actions 构建矩阵引入 `client_dir` 参数，macOS 节点绑定 `desktop_client`（产出 `.dmg` / `.app`），Windows 节点绑定 `Windows_client`（产出 NSIS `.exe`），两端缓存键与依赖安装完全独立隔离。
- **Windows 原生视觉体验润饰与字体平滑 (Look & Feel Adaptation)**：
  - 优化全局中文字体回退链：优先使用 `"Microsoft YaHei UI"` 与 `"Microsoft YaHei"`，并开启 `-webkit-font-smoothing: antialiased` 与 `text-rendering: optimizeLegibility`。
  - 标准化现代化极细滚动条：注入 `scrollbar-width: thin` 与 `scrollbar-color: var(--surface-2) var(--app)`，规避 Windows 默认粗宽滚动条遮挡或挤压内容布局。
  - 窗口贴靠无损适配：保留原生装饰栏 (`decorations: true`)，支持 Windows 11 最大化按钮悬停唤出原生分屏贴靠布局矩阵（Snap Layouts）。
- **NSIS 单用户轻量化免提权安装规范**：
  - `Windows_client/src-tauri/tauri.conf.json` 采用 `installMode: "currentUser"` 与 `displayLanguageSelector: true`，安装全程无需 UAC 管理员提权，随装随用。
  - 内置高质量多分辨率 Windows 图标资源 (`icons/icon.ico`)。

### Fixed
- **Windows 子进程控制台黑框闪烁彻底消除 (CREATE_NO_WINDOW Console Suppression)**：
  - 根因定位：在 Windows GUI 应用程序（Tauri / Win32）中，通过 `std::process::Command` 直接执行外部命令（如 Python 爬虫、任务终止命令或 curl 网络请求）时，Windows 默认会瞬时弹出黑色控制台窗口并抢占焦点。
  - 修复实现：全面引入 `#[cfg(target_os = "windows")]`，并在所有子进程调用前统一注入 `creation_flags(0x08000000)` (`CREATE_NO_WINDOW`)，彻底消除以下所有场景下的黑框闪烁：
    1. 后台数据刮削与同步引擎启动 (`commands/sync.rs: start_scraper`)；
    2. 取消同步时的子进程树强制终止 (`commands/sync.rs: taskkill`)；
    3. 同步状态更新与实时管道读取 (`commands/sync.rs: run_sync`)；
    4. 运行时依赖检测与解释器有效性验证 (`commands/environment.rs`)；
    5. 翻译大模型服务商可用模型在线探测 (`commands/translate.rs: curl`)。

---
