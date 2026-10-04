# 更新日志 (Changelog)

本项目严格遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范与语义化版本号管理。
本项目记录了每次迭代的更新详情，便于直接同步至 GitHub Releases 与提交历史。

## [v2.18.0] - 2026-10-04

### Added
- **GEVI 全量片商官方网站刮削与持久化入库 (`scrape_studio_websites.py`, `GPDb.db`, `schema.sql`)**：
  - **多线程高并发抓取管道**：开发专用的轻量级标准库爬虫脚本（10 workers，稳定 10.7 req/s），针对 GEVI 全库 2,470 个片商 ID 进行全量扫描与高效抓取；
  - **智能正则排重与精准提取**：严格界定 `<!-- name -->` 区块，自动过滤下属厂牌内部关联（`company/<id>`），精准提取片商官方站点直链（如 `company/7418` 对应 `http://all-americanheroes.net`）；
  - **数据库结构扩展与全量同步**：在 `studios` 表中新增 `website_url TEXT` 与 `site_id INTEGER` 字段，并在全库中成功关联入库 237 个片商的官方网址；同步新建 `studio_websites` 完整归档表与索引，确保元数据源头可追溯；同步更新根目录 `schema.sql`。
- **片商档案详情弹窗新增官方网站直跳支持 (`StudioDetailModal.vue`, `types.ts`, `api.ts`)**：
  - **官网操作按钮**：在片商档案页头部右侧操作按钮区（双列紧凑布局）中新增“官方网站”直达按钮，带有精致的地球图标 (`Globe`)，悬停显示目标网址，单击直接调用系统默认外部浏览器打开；
  - **智能协议自适应补齐**：自动检测协议前缀，针对未显式包含协议的链接智能补齐 `https://`，防止唤起异常。
- **全球 7 种语言完整国际化覆盖 (`i18n/index.ts`)**：
  - 在国际化字典中为所有支持语言添加 `'studio.officialWebsite'` 词条：中文简体（官方网站）、中文繁体（官方網站）、英语（Official Website）、日语（公式サイト）、意大利语（Sito ufficiale）、西班牙语（Sitio oficial）、德语（Offizielle Website）。

### Fixed
- **修复 macOS 客户端在全屏显示时按 Esc 键直接退出全屏而非退回上层界面的缺陷 (`escape.ts`, `App.vue`, `SeriesModal.vue`, `FilterDrawer.vue`, `SyncModal.vue`, `Navbar.vue`)**：
  - **根本原因定位**：此前各模态组件在响应 `Escape` 键关闭时未对 `KeyboardEvent` 执行 `e.preventDefault()` / `e.stopPropagation()`，导致 WebKit 将按键标记为未消费，进而向上冒泡触发 macOS AppKit 对全屏窗口的系统级默认动作 `cancelOperation:`（即直接执行退出全屏）；
  - **核心退出拦截器增强 (`claimEscape`)**：在 `escape.ts` 认领 Escape 键的处理管道中统一注入 `e.preventDefault()` 与 `e.stopPropagation()`，保证任一层级模态窗消费 Esc 键时 100% 告知宿主系统事件已处理；
  - **顶层全局捕获守卫 (`onGlobalEscapeGuard`)**：在 `App.vue` 顶层注册捕获阶段（Capture phase）按键守卫，当检测到存在任何展开中的详情模态（影片/演员/片商/导演/分集）、系列大放送、筛选抽屉、同步中心或图片灯箱等上层界面时，第一时间阻止事件被操作系统默认全屏行为劫持，确保优先依序退出最上层界面，保持客户端沉浸式全屏状态；
  - **全面覆盖所有弹出层**：为 `SeriesModal`、`FilterDrawer`、`SyncModal` 及搜索框/历史记录快捷面板补全 Escape 键联动关闭与默认行为拦截。

### Changed
- **主页 (HomeView) 视觉排版与文案全面优化与瘦身 (`HomeView.vue`, `i18n/index.ts`)**：
  - **焦点巨幕海报轮播 (Hero Carousel) 面积缩减与纯净化**：海报尺寸由 `w-44 md:w-56` 精简缩小为 `w-28 sm:w-32 md:w-36`，容器内边距收敛为 `p-4 sm:p-5`，移除顶部“镇馆之选”徽章，彻底移除底部“立即品鉴”和“换一个演员”等冗余操作按钮，聚焦于封面与标题直接点击直达，大幅压缩首屏垂直空间占用；
  - **“今日星光”板块紧凑化与头像缩小**：主标题由“今日星光 · 演员焦点”精简为“今日星光”（全 7 种语言同步对齐），移除“领衔巨星生涯档案与代表名作”副标题描述；演员头像尺寸由 `w-24 h-24 sm:w-28 sm:h-28` 缩小为精致紧凑的 `w-16 h-16 sm:w-18 sm:h-18`，卡片内边距调整为 `p-3`，大幅提升视觉精致度；
  - **“经典系列大放送”拼接海报显著放大**：系列网格展示由密集 6 列 (`md:grid-cols-6`) 调整为 4 列宽幅大展台 (`lg:grid-cols-4 gap-4 md:gap-5`)，单批次随机加载量由 6 部提升为 8 部（2 排整齐展示），单张拼接海报面积提升逾 2 倍，多图拼贴封面细节更饱满细腻；
  - **“随心探索 · 盲盒发现”文案精简为“随机抽选”**：主标题精炼为“随机抽选”（全 7 种语言同步对齐），彻底移除“漫无目的时，不妨从浩瀚影海中打捞几颗遗落的珍珠”副标题描述，界面排版更加清爽现代。
- **影片档案详情页片商胶囊点击直接唤起片商档案 (`MovieDetailModal.vue`, `App.vue`)**：
  - 调整影片详情页顶部片商按钮的点击行为，由原先的“关闭弹窗并在影片库筛选该片商”优化为“直接在顶层打开该片商专属档案页 (`openStudioDetail`)”；
  - 保持弹窗堆叠栈 (`modalStack`) 的平滑层级覆盖，关闭片商档案后可无缝返回当前正在浏览的影片档案，不再打断当前影片浏览上下文与清空过滤条件。
- **片商档案详情分集片段页支持网格海报模式与密集列表模式双模切换 (`StudioDetailModal.vue`, `App.vue`)**：
  - **默认网格海报模式**：全面对齐演员档案页的设计语言，默认以美观直观的 4 列网格海报卡片展示分集片段；
  - **横向剧照画幅深度适配**：分集封面采用 `aspect-video` (16:9) 宽屏比例容器与 `object-cover` 居中填充，针对横向剧照/场景截图进行比例优化，彻底消除竖版海报容器对横屏视频截图造成的过度拉伸或上下黑边裁切，并支持缩放平滑过渡；
  - **手动布局自由切换**：头部集成专属视图切换器（网格海报模式 / 密集列表模式），保留完整的密集列表视图供深度阅读剧本大纲；
  - **交互体验完善**：网格卡片点击直接唤起独立分集详情 (`openEpisodeDetailById`)，卡片内出处影片链接支持独立快捷点击跳转主影片 (`openMovieDetailById`)，并内置中文剧情指示标与收藏红心交互。
- **内核查询与数据契约全面打通 (`gpdb-core/src/models.rs`, `queries/studios.rs`, `migrate.rs`)**：
  - **数据模型增强**：`StudioSummary` 与 `StudioWorks` 均新增 `website_url: Option<String>` 字段；
  - **查询管道与连表映射**：`get_studio_library` 与 `get_studio_works` 均读取并映射 `st.website_url`，保证列表与详情两端数据一致；
  - **零配置平滑迁移**：在 `migrate.rs` 的 `STUDIO_COLUMNS` 中加入 `website_url` 与 `site_id`，保证旧版本数据库升级时自动补充列定义与结构，零损耗迁移；
  - 后端核心库 60 个单元测试与回归测试全数通过。

## [v2.17.0] - 2026-10-03

### Changed
- **片商库网格布局默认 4 列展示与独立记忆 (`App.vue`, `prefs.ts`)**：
  - 片商库网格界面新增专属的独立列数记忆配置 (`gpdb_studio_cols`)，默认呈现每行 4 列，相比影片库的 5 列更契合片商大展台徽标与宽画幅横幅设计；列数微调按钮联动片商独立状态，互不干扰影片库的列数偏好。
- **演员库网格头像卡片美化与自适应高斯模糊融底 (`App.vue`)**：
  - 演员头像容器尺寸从 `w-16 h-16` (64px) 大幅扩充升级为 `w-24 h-24 sm:w-28 sm:h-28` (96-112px)，让演员肖像展示更显饱满精致；
  - 采用双层流光融底结构：前景头像采用 `object-contain` 保持人脸与比例 100% 完整无硬裁切，周围留白区域由头像本身的取色高斯模糊层 (`blur-md opacity-70 scale-125`) 柔和填充，彻底消除留白与突兀边界感。
- **影片库网格卡片信息精炼化 (`MovieCard.vue`)**：
  - 影片网格卡片移除冗余的演员信息预览胶囊（进入影片详情后即可完整浏览阵容），大幅减少视觉干扰；
  - 移除网格界面上的“普通硬核”等题材分类标签，只保留作品名、年份、片长、导演（若有）及个人评分标签，卡片整体布局更干净纯粹、美观现代。
- **片商档案详情弹窗性能全面提升与滚动分批加载 (`StudioDetailModal.vue`, `commands/cache.rs`)**：
  - **彻底移除模态窗内的冗余重复 IPC 查询**：彻底清除模态内部多余的 `loadStudioArchive` 重复触发点，消除与父组件并发查询造成的双重连表大负载与 JSON 反序列化延迟，弹窗打开丝滑无卡顿；
  - **影片网格按需分批渲染 (`displayedMovies`)**：引入每批 40 部的按需滚动分批机制，彻底避免上千部作品瞬间同时插入 DOM 导致 WebKit 渲染队列与图片加载排队阻塞，确保首屏前排影片海报秒级即时呈现；
  - **精简顶部横幅滤镜层级**：移除高消耗的 CSS `mask-image` 与超大模糊半径，改用自适应轻量渐变遮罩，消除 WebKit 动画合成卡顿。
- **修复片商库网格中 Logo 无法加载或滚动时消失的缺陷 (`commands/cache.rs`, `App.vue`)**：
  - **修复缓存协议前缀判断顺序**：解决 `images/logos/` 与 `logos/` 因字符前缀包含关系被错误匹配进 `logo/` 规则导致路径截断为 `Logo/s/...` 而无法命中本地缓存的关键缺陷；
  - **前端非破坏性加载容错**：使用独立的响应式 Set 记录异常资源，杜绝直接将片商数据模型中的 `logo_url` 置空造成永久消失。
- **演员档案详情弹窗（Performer Modal）右侧操作按钮布局优化 (`PerformerDetailModal.vue`)**：
  - **右侧专属小块单列排布**：修复此前演员信息与艺名等文本被横向多按钮挤占排版空间的痛点，将“BT 磁力资源搜索扩展、BFTV、PBC 百科、SmutJunkies、Google 搜索、收藏演员”等外部链接与收藏操作按钮统一定位收纳于右侧紧凑专区（单列垂直排布，宽度自适应收束），释放左侧核心演员档案与本名/出道年份等信息呈现空间。

### Fixed
- **彻底解决片商库加载卡顿与滑动浏览追加卡片卡顿掉帧问题 (`queries/studios.rs`, `migrate.rs`, `schema.sql`, `App.vue`)**：
  - **SQLite 查询性能 10 倍级飞跃**：为 `studios(name COLLATE NOCASE)` 引入专属不区分大小写索引，重构 `get_studio_library` 连表条件为索引友好的 `st.name = s.name COLLATE NOCASE`，彻底消除由于 `OR / trim` 导致 SQLite 对 2,424 个片商反复全表扫描高达 72 万次的性能黑洞；针对常规翻页浏览拆分出极速 `COUNT(*)` 快路径，单次分页查询耗时由 2.53 秒降至 0.22 秒；
  - **前端 Logo 取色与响应式去抖**：将 `studioLogoColorMap` 解耦为非响应式静态缓存，Logo 取色后直接对当前 DOM 展台容器应用渐变底色，彻底杜绝图片加载时反复触发表格全部卡片重新渲染与布局抖动；
  - **消除图片滚动重绘开销**：移除片商卡片 Logo 图片上的 `filter drop-shadow` 动态透明通道投影滤镜，改用外层容器硬件加速阴影，大幅释放 WebKit/Blink 滚动帧率，使片商库滑动与无限加载全程丝滑流畅。
- **浅色主题背景与文字对比度与可读性修复 (`theme.css`, `App.vue`)**：
  - **窗口材质深色变量隔离**：排查并彻底解决 Windows 11 Mica / Acrylic 材质全局注入导致浅色主题（`classic-light`、`glass-light`、`my-light`）背景发黑、暗色文字（zinc-900）与暗黑背景对比度崩塌难以阅读的根本原因；
  - **浅色主题半透明底色正向适配**：限制深色材质变量仅在暗色主题下生效（`:not([data-theme*='light'])`），并为浅色主题适配高亮度通透半透明底色（`oklch(97.5% ...)` / `#ffffff`），确保所有浅色主题在任何系统材质与环境下均呈现清晰纯净的浅色背景与高对比度易读文字；
  - **非 Windows 平台原生材质环境隔离**：在 macOS / Linux 等非 Windows 环境下默认将窗口材质设为 `default` 并移除 `data-window-material` 属性，设置面板中「窗口背景材质」选项仅在 Windows 系统下展示，杜绝跨平台样式干扰。
- **影视分享卡片底部日期与官方频道链接修正 (`ShareCardModal.vue`, `i18n/index.ts`)**：
  - **移除分享卡片底部写入的日期**：Canvas 生成图与 DOM 预览均移除日期展示，仅保留纯净作品编号（如 `#MOV-12345` / `#EP-6789`）；
  - **修正官方 Telegram 频道链接**：将 7 种语言国际化字典中的官方频道链接统一修正为 `t.me/gpdbnews`。

### Added
- **Windows 11 原生 Mica / Acrylic 材质融合与 Fluent Design 现代感增强 (`commands/system.rs`, `lib.rs`, `App.vue`, `theme.css`, `tauri.conf.json`, `prefs.ts`)**：
  - **Windows 11 Mica / Mica Alt (Tabbed) / Acrylic 窗口效果原生驱动**：利用 Tauri v2 窗口材质特效引擎 (`EffectsBuilder` & `Effect::Mica` / `Effect::Tabbed` / `Effect::Acrylic`)，窗口透明度与桌面壁纸原生融合，带来 Windows 11 标志性 Fluent Design 半透明现代质感；
  - **外观设置新增「窗口背景材质」自由切换**：设置中心提供 4 档材质方案（经典纯色 / Windows 11 Mica 云母 / Windows 11 Mica Alt 深邃 / Windows 10/11 Acrylic 亚克力），偏好实时持久化并即时响应切换；
  - **Fluent Design 动态半透明层级适配**：CSS 主题层针对 Mica/Acrylic 动态注入自适应透明底色（`oklch(... / 0.75~0.78)`），浮动面板、侧边栏与主窗口自然透出桌面背景，视觉层次现代通透。
- **系统托盘驻留 (System Tray) 与后台静默运行 (`commands/system.rs`, `lib.rs`, `App.vue`, `privacy.ts`, `tauri.conf.json`)**：
  - **原生系统托盘图标与常驻菜单**：为 Windows 平台构建原生任务栏通知区域图标（GPDb 经典标志），右键提供「显示主界面」「最小化到托盘」「截屏防窥模式」「退出 GPDb」完备快捷菜单；
  - **托盘 Jump List 快捷导航直达**：托盘菜单内置「今日探索」「我的收藏」等常用板块快捷直达项，单击后自动唤醒窗口并平滑跳转至对应标签页；
  - **左键单击极速切换窗口**：单击托盘图标即可在隐藏后台与恢复唤醒主窗口（自动置顶并聚焦）之间无缝切换；
  - **优雅后台挂起与「关闭窗口时最小化到系统托盘」支持**：设置面板新增「关闭窗口时最小化到系统托盘」独立开关，勾选后点击窗口右上角关闭按钮 (X) 不会强退程序，而是静默隐藏至系统托盘，随时秒开恢复；
  - **托盘与应用防窥盾 (Privacy Shield) 原生联动**：右键菜单直达「截屏防窥模式」，一键模糊影片封面与剧情文字，防止录屏与他人窥探。
- **Windows 任务栏实时进度指示 (Taskbar Progress Indicator) (`commands/system.rs`, `lib.rs`, `scraper.ts`)**：
  - **Tauri 原生任务栏进度驱动**：联动数据库全量/增量刮削状态机，在执行网络刮削与元数据解析时，Windows 任务栏图标实时呈现原生绿色进度条及百分比，开始阶段展示不确定等待态，完成后自动复位，进度一目了然。
- **WebView2 高刷新率 (120Hz/144Hz) 与 GPU 硬件渲染调优 (`src-tauri/src/lib.rs`)**：
  - 启动阶段自动为 WebView2 运行时注入性能加速参数：`--enable-features=msWebView2EnableDraggableRegions`、`--disable-features=CalculateNativeWinOcclusion`、`--high-dpi-support=1`、`--enable-gpu-rasterization` 与 `--enable-zero-copy`，彻底消除多显示器环境下的掉帧卡顿，高刷屏瀑布流滚动极速流畅。

### Fixed
- **数据表结构与类型定义严格对齐 (`gpdb-core/src/migrate.rs`)**：
  - 将 `studios` 表中的 `updated_at` 字段统一为 `TEXT DEFAULT CURRENT_TIMESTAMP`，杜绝跨平台 SQLite 驱动时间戳解析不兼容。
- **离线演员数据库刮削脚本打包补齐 (`tauri.conf.json`)**：
  - 在打包资源依赖中补齐 `../../scrape_pbc_actors.py` 与 `../../scrape_smutjunkies_actors.py`，确保演员百科本地同步与深度整合正常工作。
- **7 国语言国际化字典 100% 绝对镜像对齐 (`i18n/index.ts`, `test_parity.py`)**：
  - 补齐日文、意大利文、西班牙文、德文缺失的 `plugins.setActiveOnSave` 键；
  - 对称增补 7 大语种窗口材质与托盘交互键（共 930 个键），通过 `test_parity.py` 自动化 1:1 对齐校验。

## [v2.16.7] - 2026-10-03

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

- **片商官方 Logo 与横幅全自动采集流水线及全端档案页导入 (`scripts/scrape_studio_logos.py`, `studios`, `gpdb-core`, `StudioDetailModal.vue`, `StudioDetailScreen.kt`, `StudioDetailView.swift`)**：
  - **GEVI 多维徽标采集与智能对齐引擎**：利用 GEVI 活跃演员主页厂牌外链徽章（`See this performer at:`）与核心片商 Company 页面顶栏 Banner 进行多重刮削，通过英文字符清洗与别名对齐字典（RFC、TI、AMH、GISP 等），成功抓取并回填 74 家核心厂牌 Logo 与 30 家厂牌官方 Banner；
  - **100% 离线协议与资产本地化**：图片全部并发下载并存储于 `image_cache/Logos/`，Rust 内核 (`gpdb-core`)、Tauri 自定义协议、Android 缓存路由及 iOS Kingfisher 离线提供者均实现物理路径映射与 Zip 归档解压支持；
  - **全端档案页高质感 Logo 呈现与双首字母渐变兜底**：桌面端 (macOS/Windows) 档案弹窗与片商库卡片、Android 顶部 AppBar、iOS 档案全息页顶部均优雅渲染官方 Logo，无 Logo 片商自动降级为双字母多维渐变方块，杜绝排版错位。
- **分集档案页 (Episode) 全新上线「AI 翻译剧情」与双语对照切换 (`EpisodeDetailModal.vue`, `App.vue`, `api.ts`, `commands/translate.rs`)**：
  - 分集详情弹窗现已支持一键调用 AI 大模型翻译英文剧情并写入本地数据库 `episodes.description_zh`，同时提供中文译文与英文原文一键切换；
  - 翻译完成实时触发 `@episode-translated` 事件，自动同步更新分集列表、影片内分集及全局翻译统计状态。

### Optimized
- **片商档案页 Logo 重构：移至右侧操作按钮上方并自适应大画幅呈现 (`StudioDetailModal.vue`)**：
  - **彻底解决细长 Logo 过小看不清痛点**：针对主流片商 Logo 普遍呈细长扁平比例（如 5:1 至 6:1 宽幅横标），原先置于左侧正方形头像框内被极度压缩至不足 20px 高度、文字模糊难辨的物理缺陷；
  - **自适应右侧按钮上方大画幅**：将 Logo 移至右侧多列操作按钮（BT 搜索、Google 搜索、双语切换、收藏厂牌）的正上方，利用右侧约 300px~340px 完整宽度自适应呈现（`w-full h-12 md:h-14`，轻量容器配合 `object-contain` 与悬浮微动效），像素面积扩充逾 5 倍，片商官方标牌大字高清锐利呈现；
  - **左侧排版极简化与全幅舒展**：左侧移除局促的方框头像，厂牌主标题与副标题自由伸展至大号字体（`text-2xl md:text-3xl lg:text-4xl font-black`），搭配作品/分集精致统计胶囊，视觉层次典雅高级。
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

## [v2.16.6] - 2026-10-03

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

## [v2.16.5] - 2026-10-03

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

### Fixed
- **Windows 客户端新用户纯导入数据库场景下远程图片加载与自动离线缓存彻底修复 (`utils/image.ts`, `src-tauri/src/commands/cache.rs`)**：
  - **Windows WebView2 协议端点兼容对齐**：WebView2 内核基于安全沙箱规范拦截自定义协议，前端在 Windows 环境下自适应接入 `http://gpdb-img.localhost/?url=...` 规范端点，解决此前直接请求 `gpdb-img://` 导致请求被 Edge 内核当成未注册系统协议而直接静默阻断报错 (`ERR_UNKNOWN_URL_SCHEME`) 的致命缺陷；
  - **原生 Rust HTTP 线程池下载替代外部子进程**：引入轻量级纯 Rust TLS 高性能客户端 (`ureq` + `rustls` + `webpki-roots`) 接管边看边下载流水线。彻底摒弃此前单图并发调用外部 `curl.exe` 子进程导致的进程风暴、控制台窗口闪烁及缺少 curl 环境无法下载的缺陷；内置连接池复用与 Keep-Alive，支持全自动伪装 User-Agent 与防盗链 Referer 头；
  - **移除 302 重定向并直传图片流**：杜绝向 WebView2 返回 302 跳转导致的跨源跨协议拦截 (`ERR_UNSAFE_REDIRECT`)，直接在 Rust 内存层缓冲并以 200 流式输出图片字节，同步安全持久化至本地 `image_cache/`，实现“首次在线直读，后续 100% 离线亚毫秒呈现”；
  - **全量支持外部第三方图片源离线缓存 (`image_cache/External/`)**：`resolve_cache_target_path` 扩展支持对 PBC (PornBaseCentral)、SmutJunkies 及其他外部高清图源进行 URL 哈希持久化映射，全量图片均享本地离线化待遇。

### Removed
- **精简设置面板与移除多余说明及伪装选项 (`App.vue`, `theme.ts`, `appIcon.ts`, `privacy.ts`, `i18n/index.ts`)**：
  - **移除 AI 影迷画像全球化相关代码**：回滚 `aiAnalysis.ts` 与 `AiAnalysisModal.vue` 中的英文大模型 Prompt 分支及多语种交互代码，还原纯粹原生的分析逻辑；
  - **精简应用图标方案 (App Icon)**：彻底移除图标卡片中的副标题、风格解析、私密星级评分、标签、说明及格式下载按钮等冗余文字，重构为极致清爽的 4 图案网格点选矩阵；
  - **移除窗口伪装标题 (Disguise Title)**：下线应用设置中的窗口伪装标题选项及 `privacy.ts` 中对应的存储与 `document.title` 同步逻辑；
  - **清除外观主题说明文字**：移除外观设置中对每种具体主题（暗黑、浅色、经典、玻璃、暖琥珀等）的长串说明文字与 `theme.ts` 中的 `hint` 属性，升级为对齐清爽的单行色块 + 标题布局；
  - **同步清理无用国际化词条 (`i18n/index.ts`)**：全量清理 7 大语种字典中已废弃的 63 个冗余 Key，各语种字典统一维持 958 键 100% 镜像对齐。

### Added
- **国际化字典多语种扩充 (`i18n/index.ts`)**：
  - 新增 `common.collapseAll`（'收起全部' / 'Collapse All' / 'すべて折りたたむ' / 'Riduci tutto' / 'Plegar todo' / 'Alle einklappen'）词条；
  - 7 大受支持语种（`zh-CN`, `zh-TW`, `en`, `ja`, `it`, `es`, `de`）严格维持 958 键 100% 镜像对齐，并通过 `test_parity.py` 自动化测试。

## [v2.16.4] - 2026-10-03

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

## [v2.16.3] - 2026-10-03

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

## [v2.16.2] - 2026-10-03

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

## [v2.16.1] - 2026-10-02

### Added
- **macOS 客户端多语种体系重构与数据库语种自动匹配联动 (`i18n/index.ts`, `bilingual.ts`, `prefs.ts`, `App.vue`, `EpisodeRow.vue`, `StudioDetailModal.vue`, `MovieDetailModal.vue`, `MovieCard.vue`)**：
  - **二元文本体系解耦设计**：
    - 严谨分离「客户端界面菜单文字（UI Texts）」与「数据库内部实体文本（DB Content Texts）」两大体系；
    - 客户端系统菜单、导航、筛选抽屉、按钮标签、通用状态提示等静态文字全量收敛至 `desktop_client/src/i18n/index.ts` 字典（覆盖 zh-CN, zh-TW, en, ja, it, es, de 7 种语言），彻底拔除各组件与视图内的硬编码中文字符；
    - 增强 `t()` 国际化函数，原生支持 `{param}` 动态变量插值与优雅回退机制；
  - **数据库内容语种自动匹配与联动矩阵**：
    - **英文模式 (English)**：当用户在设置中选择英文界面时，数据库文本自动进入纯原文模式。影片与分集主标题仅显示英文原名（自动隐藏中文副标题与空占位），剧情简介仅展示英文原版，界面干净紧凑；
    - **中文模式 (Chinese)**：当用户选择简体或繁体中文时，自动启用中英双显模式。厂牌详情弹窗（Studio Modal）、影片卡片等主标题优先展示中文译名、副标题展示浅灰英文原名；剧情简介优先展示中文译文，并保留一键对比原文的切换控件；
    - **设置面板三档独立模式自由切换**：在「缓存与设置」->「语言与本地化」新增【数据库内容呈现模式】配置（支持 `自动匹配 (推荐)`、`始终中英双显`、`始终仅看原文`），满足用户的个性化阅读偏好；
  - **核心组件多语种体验升级**：
    - **厂牌详情弹窗 (`StudioDetailModal.vue`)**：全面国际化固定文本（片商档案、完整电影、片段/分集、正在读取、无收录提示等），弹窗下长片与分集列表无缝响应当前语种策略；
    - **分集列表行 (`EpisodeRow.vue`)**：出处完整支持双语标题展示，国际化所有静态标签，并在单行分集简介右侧新增微型即时切换按钮（`显示原文 / 显示译文`），无需打开弹窗即可随时对比原文；
    - **影片卡片与详情弹窗 (`MovieCard.vue` & `MovieDetailModal.vue`)**：详情弹窗内的「显示原文 / 显示中文」按钮协同联动下方收录分集列表的显示语种；卡片在英文模式下自动收起副标题高度，排版更加精致。

### Optimized
- **演员档案页作品分类切换栏重塑为沉浸式悬浮胶囊药丸岛 (`PerformerDetailView.swift`)**：
  - **沉浸式胶囊药丸岛造型**：彻底废弃此前平铺直述的通栏矩形切条与机械分割线，升级为悬浮于底部的极简流线型胶囊药丸岛（Floating Capsule Pill Island）；
  - **MatchedGeometryEffect 丝滑滑块联动**：引入 `@Namespace tabNamespace` 与 `.matchedGeometryEffect`，切换「出演电影」与「出演分集」时，高光选中胶囊指示器在选项间以弹簧物理动效（`response: 0.35, dampingFraction: 0.76`）平滑滑移变形，附带微光描边与精致阴影；
  - **多层微光流体毛玻璃质感**：外层包裹高通透 `.ultraThinMaterial` 毛玻璃，搭配 `LinearGradient` 微光渐变反光描边（White 35% -> 8% -> 2%）与 16pt 柔和阴影，单手大拇指触手可及，与页面动态背景浑然天成；
  - **位置吸附与避让小白条微调**：通过 `.safeAreaInset(edge: .bottom)` 优雅悬浮于底部导航栏（TabBar）正上方；当底部 TabBar 隐藏收起时，完全交由原生 safeArea 保护，彻底移除原硬编码多余下移 offset（`y: 49`），并微调底部间距为 `12pt`，确保药丸岛始终悬停在系统底部小白条（Home Indicator）上方安全透气区域，杜绝遮挡遮盖。
- **瀑布流滑动时底部导航栏自动收缩与展开动效与底层穿透 (`WaterfallScrollView.swift`, `TabBarManager.swift`, `AppEnvironment.swift`, `AppNavigation.swift`)**：
  - **iOS 18+ 现代化滚动几何监听 (`WaterfallScrollView.swift`)**：重构滚动检测底层，引入 iOS 18+ 原生 `onScrollGeometryChange(for:action:)` API，直接追踪 `contentOffset.y`，彻底根除此前 `GeometryReader` + `PreferenceKey` 在 iOS 18+/26 复杂容器中滚动时仅触发单次 `0.0` 采样而无法持续触发的回调失效缺陷；同时保留 iOS 17 的 `GeometryReader` + 规格化初始零点基线回退机制；
  - **首帧安全区负偏移自动消除与布局平稳期保护**：排查并解决安全区与导航栏造成的首帧 `contentOffset.y = -116pt` 负偏移问题，引入布局稳定期基线采样与 `\pm 12\text{pt}` 差值阈值防抖，滚动向下时平滑收起底部导航栏，反向向上滑动或轻触拉回顶部时秒级自动展开复位；
  - **SwiftUI 纯声明式过渡动画协同 (`AppEnvironment.swift`)**：解除 UIKit `CGAffineTransform` 与 SwiftUI `.toolbar(for: .tabBar)` 之间的动画事务冲突，统一由 `withAnimation(.easeInOut(duration: 0.25))` 驱动状态流转，保证多级界面下 TabBar 收起/展开动效稳定连贯；
  - **演员详情页横条位移动画同步 (`PerformerDetailView.swift`)**：当导航栏收起时，作品分类横条自动下沉贴合底部安全区，展开时平滑上浮归位；
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
- **冷启动海量图片未显示彻底修复与 ZIP64 纳秒级中央目录极速索引 (`ZipArchiveService.swift`, `ZipImageProvider.swift`, `AppEnvironment.swift`)**：
  - **根因定位与根治**：14GB 的 `GPDb_Images.zip` 拥有近 24 万个条目，超过 74% 的条目位于 4GB 以外且使用 ZIP64 64 位局部头偏移。原实现依赖 `ZIPFoundation.Archive` 的迭代器，在构建索引时对每个条目强行执行 2 次跨 14GB 磁盘范围随机寻道（共计 48 万次磁盘 seek），耗时长达数分钟甚至在并发请求竞争同一个 C `FILE*` 句柄时导致迭代器因读取错乱提前于 8,206 个条目处中断终止（导致 90% 以上图片索引遗失）。与此同时，冷启动时 Kingfisher 并发发起图片抽取，在索引未构建完成时回退至线性文件扫描导致句柄竞争错乱并立即返回 404 错误，致使冷启后海量图片永久显示灰色占位图；
  - **底层单遍 Central Directory 连续流式解析重构**：彻底废弃全库随机寻道，自主实现 ZIP / ZIP64 中央目录（EOCD / ZIP64 Locator）单遍连续加载与解析，一次性读入末尾仅 27.7MB 的目录块并纯内存解析全量 239,159 个条目，索引构建耗时由 23 秒+骤降至 **0.34 秒**（性能提升近 70 倍），24 万条目元数据常驻内存仅消耗 6.2MB；
  - **挂载期非阻塞等待队列与线程安全串行读取**：重构 `extract` 逻辑，在后台 Central Directory 索引尚未完成的 300ms 间隙内，将所有进来的 Kingfisher 抽取任务加入安全排队，索引完成后立即并发清空队列并命中 O(1) 字典抽取；专属 `ioQueue` 串行保护物理文件读取与解压，彻底杜绝数据竞争与句柄偏移破坏；
  - **原生硬件级 zlib 极速 Deflate 解压 (`inflateInit2_`)**：针对 ZIP 存储的 RFC 1951 Deflate 压缩块，采用原生 `zlib` 流解压（`-MAX_WBITS`），单张海报解压耗时低于 0.05ms，硬件级加速大幅降低发热与功耗；
  - **路径归一化规范化增强 (`ZipImageProvider.swift`)**：剥离多余开头斜杠与 URL 编码，支持多前缀与去前缀双向映射，100% 完美匹配所有数据库内封面、剧照与头像路径。
- **冷启动“正在装载数据库”流光药丸胶囊冻结卡死修复与动画解耦 (`DatabaseLoadingView.swift`, `AppEnvironment.swift`)**：
  - **根因定位与根治**：排查发现原进度条胶囊采用 SwiftUI `@State private var shimmerOffset` 与 `withAnimation(.repeatForever)`。在冷启动过程中，`AppEnvironment` 的 `mountingProgressText` 与 `mountingSubText` 多次快速更新，触发父视图重新 evaluate，SwiftUI 的动画事务被状态变更重置并强行取消，导致流光药丸胶囊迅速冻结停滞；此外，原 SQLite 挂载操作在 MainActor 上执行，阻断了主线程绘制；
  - **DisplayLink 级 TimelineView 驱动**：将极细流光药丸胶囊与双火星微光徽标（旋转轨道与呼吸微光）全部重构为基于 `TimelineView(.animation)` 独立驱动的无状态子视图组件，直接基于系统绝对时间戳计算正弦平滑往返位置与旋转角，彻底脱离 `@State` 动画事务依赖，不受任何父视图重绘或文本刷新干扰，始终保持 60/120Hz 丝滑流畅；
  - **数据库初始化完全解耦主线程**：在 `AppEnvironment` 中将 `DatabaseHolder.shared.initialize` 移至 `Task.detached(priority: .userInitiated)` 后台线程执行，主线程帧率保持满格，杜绝任何掉帧卡顿。
- **跑马灯长文本从屏幕最右侧跳入 Bug 根除与左边缘锚定无缝循环重构 (`MarqueeText.swift`)**：
  - **根因深度定位**：排查发现原 `GeometryReader` 内嵌未限定宽度的 2 倍宽 `HStack` 时，SwiftUI 默认对超宽子视图采取居中排布，导致文本起始 X 坐标被强制推向屏幕负坐标，首个文本被左侧截断，而次级文本从屏幕右侧突兀滑入；且 `.repeatForever` 在状态更新时未能可靠重置；
  - **严格左边缘固定锚定**：对轮播 `HStack` 与容器包裹 `.frame(width: cWidth, alignment: .leading)`，确保进入页面、切换条目或重绘时初始位移严格为 0（即文字开头与普通 Text 完全一致，严格靠左自然对齐）；
  - **停留静读与无缝循环**：使用非阻塞异步任务在每次进入或回跳时静态停留 1.5 秒，让用户舒适阅读标题开头；随后单次匀速线性平滑滚向末尾，末尾接壤后即时无感对齐首部，彻底杜绝从屏幕最右侧横跨飞入的视觉缺陷。
- **片商档案页分集与作品加载截断修复与独立作品总数查询 (`StudioDetailView.swift` & `BrowseRepository.swift`)**：
  - 修复原硬编码 `pageSize: 10000` 导致大于一万条记录的大型片商作品被截断的缺陷；
  - 在 `BrowseRepository` 新增专门获取片商作品总数（电影数与分集数）的专用接口 `getStudioWorksCounts(studio:)`，并在标签栏准确显示真实数据库总数；
  - 单次拉取上限提升至 50,000 条，解除单次写死限制；同步将分类系列过滤页面的加载上限提升至 50,000。
- **macOS 桌面端片商作品与分集数量统计重大缺陷修复 (`gpdb-core/src/queries/studios.rs` & `sql.rs` & `episodes.rs`)**：
  - **根因定位与根治**：修复 macOS 桌面端底层 Rust 查询在 `get_studio_works` 中错误使用母片片商 `WHERE m.studio_name = ?1` 过滤分集，导致 102,415 个无母片的独立网络单集（`movie_id IS NULL`）及 4,573 个被母长片合辑或分销商标记的分集被全部漏计（例如 Raw Fuck Club 漏计 13,830 个分集，仅显示 44 个的严重数据偏离）；
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

## [v2.16.0] - 2026-10-02

### Added
- **主页焦点大图指示器防遮挡与悬浮药丸样式 (`HomeFeedView.swift`)**：
  - 彻底废除 `TabView` 内嵌系统 `UIPageControl` 12 个小点直接遮挡卡片底部与“立即探索”按钮的问题，采用 `indexDisplayMode: .never`；
  - 在轮播卡片正下方配备独立紧凑指示器条（当前选中项自适应展开为琥珀金药丸胶囊，未选中项为低对比圆点），呼吸动效平滑。
- **演员档案页作品分类标签栏视觉重构与毛玻璃吸顶模糊 (`PerformerDetailView.swift`)**：
  - 废除原简陋下划线设计，升级为原生级圆角双胶囊切换器，融合分类图标（`film.fill` 与 `play.rectangle.fill`）及动态数量气泡徽章；
  - 点击加入系统震动触感反馈与弹性过渡；
  - 容器包裹完整 `.ultraThinMaterial` 高级毛玻璃材质与微渐变分界线，在瀑布流向下滚动触发 Header 吸顶时，下方及穿行内容呈现优雅半透明高斯模糊。
- **全页面长文本循环滚动跑马灯覆盖 (`MarqueeText.swift` & 全局各视图)**：
  - 对标移动端最佳体验，全面装配至演员、导演、影片、分集、片商名字显示处（涵盖档案页标题、网格海报标题、列表项、主页焦点图与盲盒）；
  - 首屏留白 1.2 秒给用户充分阅读开头，随后平滑匀速无缝循环滚动全名；文本未超出容器宽度时保持静态左对齐。
- **全页面图片取色自适应动态氛围渐变色背景内核加固 (`PaletteExtractor.swift` & `DynamicAmbientBackground.swift`)**：
  - 彻底修复 `UIGraphicsImageRenderer` 生成的 `UIImage.cgImage` 为 `nil` 导致取色失败始终返回固定深灰蓝 fallback 颜色的底层 Bug；
  - 重构为确定性 `CGBitmapContext` 硬件底层直接解包 24x24 像素阵列，结合高彩度加权算法提取真正鲜艳代表色，并提供适度生动度增益；
  - 在 `GpdbImageView` 加载成功回调中无缝打通色彩登记与通知中心即时广播，实现海报解码完成即时激发背景颜色自适应平滑色彩流光变换；
  - 全面覆盖：探索主页 (`HomeFeedView`)、影片档案页 (`MovieDetailView`)、演员档案页 (`PerformerDetailView`)、分集档案页 (`EpisodeDetailView`)、片商档案页 (`StudioDetailView`)、分类系列过滤页 (`FilteredMovieListView`) 以及流光分享卡片 (`ShareCardView`)。
  - 基于 CoreGraphics 极速微图像（16x16 矩阵）降采样算法与饱和度/亮度加权模型，毫秒级提取当前图片主色彩相与次级对比色；
  - 集成 NSCache 内存高速缓存与多级寻址（Kingfisher 内存缓存、ZIP 归档解压、本地挂载目录）；
  - 结合底层系统自适应背景色、超大高斯虚化海报环境漫反射层与动态取色多阶线性渐变过渡，打造原生沉浸式氛围背景；
  - 全面覆盖：影片档案页 (`MovieDetailView`)、演员档案页 (`PerformerDetailView`)、分集档案页 (`EpisodeDetailView`)、片商档案页 (`StudioDetailView`)、分类系列过滤页 (`FilteredMovieListView`)、探索主页焦点大图 (`HomeFeedView`) 以及流光分享卡片 (`ShareCardView`)。
- **长标题循环滚动跑马灯组件 (`MarqueeText.swift`)**：
  - 自动测量单行自然文本物理宽度与容器可用尺寸；未溢出时静态靠左对齐，超长标题自动启用双副本线性循环平滑位移，实现无缝连续跑马灯滚动，影片档案页主标题与副标题皆已装配。
- **自适应流式折行布局容器 (`FlowLayout.swift`)**：
  - 基于 iOS 16+ 原生 `Layout` 协议，根据子视图自然尺寸排布，超出容器可用宽度自动平滑折行。
- **全影片瀑布流页装配快捷切列按钮 (`ColumnSwitchButton.swift`)**：
  - 在所有影片/分集瀑布流界面（“影片”二级Tab、片商作品列表、演员出演作品、系列详情等）直接提供一键切列（1~4列）操作，所见即所得。

### Fixed
- **主页顶部空白区域缩窄与内联导航对齐 (`HomeFeedView.swift`)**：
  - 将主页导航标题切换为紧凑内联模式 (`inlineNavigationTitle`)，避免 Large Title 产生近 60pt 的无内容空白；
  - 缩减 ScrollView 顶部间隙 (`padding(.top, 4)`)，使焦点轮播大图直接贴合顶部操作栏，视觉紧凑协调。
- **影片档案页说明文字边距与排版优化 (`MovieDetailView.swift`)**：
  - 剧情简介说明文字采用独立圆角卡片背景包裹，将内衬边距拓宽至水平 16pt、垂直 14pt，并提升行间距为 `lineSpacing(6)`，彻底消除拥挤贴边感。
- **影片档案页元数据徽章挤压溢出根除 (`MovieDetailView.swift`)**：
  - 将原单行固定 `HStack` 重构为自适应 `FlowLayout`；年份、时长、片商（支持跳转）、导演（支持跳转）及 BT4G 搜索按钮自适应文字长度，并支持自动多行折行排布，不再挤在单行溢出屏幕。
- **演员档案页生理指标胶囊自适应宽度 (`PerformerDetailView.swift`)**：
  - 彻底废除固定 75pt 的 `LazyVGrid` 等宽紧凑网格；重构为自适应流式折行 `Capsule` 药丸胶囊，身高、体重、三围、生日、出道年份等根据文字长度自适应伸缩与折行，告别文字截断与拥挤。
- **主页焦点大图右侧信息显示缺陷修复 (`HomeFeedView.swift` & `GpdbImageView.swift`)**：
  - 修复 `.task` 冷启动判断非空逻辑导致 Feed 首页未自动拉取数据的 Bug；
  - 为底层氛围虚化海报补充显式尺寸与裁剪约束，防止全像素大图撑破容器并挤压右侧内容；
  - 完整显示“焦点推荐”徽章、发行年份、星级评星、中文主标题、片商厂牌、剧情简介及“立即探索”按钮。
- **主页“随心探索”盲盒 6 部影片对标 Android 间距排版 (`HomeFeedView.swift`)**：
  - 严格对齐 Android `LuckyDiscoverySection` 与 `MovieGridItem` 规范；
  - 调整为 3 列网格、12pt 列间距、14pt 行间距与 16pt 屏幕边距；海报采用 0.7 黄金比例并覆以底部黑色渐变元数据栏（左侧年份、右侧时长），下方整齐排布标题与片商。
- **“影视库”Tab 作品数量统计与多类别全量加载 (`BrowseView.swift` & `BrowseRepository.swift`)**：
  - 片商与导演列表作品数量修复为长片与分集之和，消除显示为 0 的缺陷；
  - 修复“影片”、“演员”、“导演”、“分集”等网格只显示少量条目的问题，实现全量无限滚动瀑布流分页。
- **全局次级菜单返回页面进度与滚动条位置记忆防重载加固**：
  - 次级菜单返回上一级时不重置页面，记忆滚动条进度。
- **“今日星光”真实头像过滤**：
  - 严格过滤本地图库中不存在真实头像的演员，只展示有效头像面孔。
- **BT4G 搜索按钮与互联档案恢复**：
  - 影片与演员档案页完整恢复 BT4G 检索入口；演员档案页完整恢复 IAFD、IMDb、Twitter/X 等全网互联档案外链。
- **片商档案页电影与分集双瀑布流重构 (`StudioDetailView.swift`)**：
  - 支持在片商名下自由切换查看发行长片与发行分集。

### Changed
- **导航结构与菜单名称对齐 Android**：
  - 底部导航栏“发现”更名为“主页”；“厂牌”更名为“片商”；
  - 顶部导航栏移出独立“设置”Tab，将其整合至“我的影库”右上角操作区；
  - 将统计分析板块收纳至设置二级功能页面中，释放主界面空间。

### Removed
- **移除影片档案页“私密评星与标记”冗余功能及代码**。

## [v2.15.0] - 2026-10-01

### Fixed
- **桌面端（macOS & Windows）外链点击无法跳转浏览器缺陷修复 (`App.vue` & `PerformerDetailModal.vue` & `main.ts`)**：
  - 根除 Tauri WebKit / WebView2 沙箱拦截原始 HTML `<a href="..." target="_blank">` 导致设置页「访问仓库 ↗」及演员档案页「互联档案」（IAFD、IMDb、X等）点击无响应的缺陷；
  - 在 `main.ts` 中集成全局外链点击委托分发机制，显式为对应链接绑定 `@click.prevent="openUrlExternal(...)"`，通过系统命令原生安全唤起默认浏览器。
- **Android 演员档案页架构重构与网格遮盖缺陷根治 (`PerformerDetailScreen.kt`)**：
  - 彻底移除不稳定的 `nestedScrollConnection` 动态滑动折叠与易被持久化隐藏的 `isHeaderVisible` 机制；
  - 采用 Jetpack Compose 规范的单流滚动架构，将完整的演员人物档案板块（头像、生理特征、PBC 维基人物小传、互联档案、外链检索按钮、AKA 艺名）与「出演影片」「出演分集」分类 TabRow 作为顶置 Item 直接嵌入 `LazyVerticalGrid` 与 `LazyColumn`；
  - 彻底解决了进入演员档案页时人物信息板块和出演影片/出演分集按钮被掩盖在网格后面或折叠消失、界面只显示孤立影片网格的严重交互 Bug。
- **P0 级致命内存崩溃根治：Objective-C 剪贴板 Over-Release 修复 (`desktop_client/src-tauri/src/commands/system.rs`)**：
  - 修复 `copy_image_to_clipboard` 对 `+[NSData dataWithBytes:length:]` 与 `+[NSString stringWithUTF8String:]` 返回的 Autoreleased 对象显式调用 `release` 导致的 Double-Free 崩溃，彻底消除主线程死锁。
- **全平台分享卡片选择模糊后保存未模糊 Bug 根除 (`ShareCardModal.vue` & `ShareCardDialog.kt`)**：
  - 解决 macOS WebKit 与 Windows WebView2 下 Canvas filter 硬件加速静默失效的问题；
  - 引入硬件中立的双离屏双线性多级降采样模糊算法，生成细腻柔和的磨砂打码质感，并在剧情简介处叠加半透明脱敏盖章。
- **分集剧照分享比例拉伸形变修复**：
  - 分集分享卡片自动切换为 16:9 横屏展板；实现纯 Canvas / Compose 版 `object-fit: cover` 居中裁剪算法，确保剧照中人物面部绝无挤压形变。
- **Android 客户端数据库挂载失败根除 (Room 架构校验与物理列自愈)**：
  - 在 [`GpdbDatabase.kt`](file:///Users/joel/iCloud%20Drive%20(Archive)/Documents/antigravity/游戏库管理App/GEVI_Offline_Database/android_client/app/src/main/java/com/gpdb/android/data/db/GpdbDatabase.kt) 中将 Room 数据库版本由 3 升级至 4，补全 `MIGRATION_3_4` 以及各历史版本到 4 的完整迁移脚本（补充 `ALTER TABLE performers ADD COLUMN sj_url TEXT`）；
  - 引入外部数据库挂载前置自愈机制 `ensureSchemaCompatibility`：在 Room 执行 TableInfo 严格校验前，使用原生 SQLite 直连检查并就地无损补齐缺失列（`pbc_url`, `sj_url` 及扩展表 `performer_pbc_profiles`, `performer_sj_profiles`），彻底解决由于缺少 `sj_url` 列导致 `IllegalStateException: Migration didn't properly handle: performers` 挂载失败的缺陷。
- **Android 收藏系列（LibraryScreen）点击瞬间闪退修复**：
  - 将网格项键值升级为全局复合唯一键，查询语句增加 `SELECT DISTINCT`，彻底根治重名系列触发的闪退崩溃。

### Added
- **全平台统一通用用户配置与数据备份/恢复机制 (`GPDb_Backup.json`)**：
  - macOS、Windows、Android 客户端设置中新增「用户配置与数据备份/迁移 (跨设备通用 JSON)」模块；
  - 导出范围覆盖用户在 SQLite 数据库中的全部「我的收藏」（包含长片、分集、演员、导演、片商、系列等）、自定义标签、想看/已看状态与私密评星笔记，以及全量本地统计数据（总专注时长、各维度播放与探索计数、活跃天数等）与界面偏好；
  - 导出格式采用统一规范 `gpdb_universal_backup` JSON，支持在 macOS、Windows、Android 与 iOS 四端通用互认，跨设备直接导入无缝迁移。
- **PBC 与 SmutJunkies 演员刮削引擎深度集成与自动入库**：
  - 在桌面端（macOS / Windows）Tauri 底层命令中完整集成 `scrape_pbc_actors.py` 与 `scrape_smutjunkies_actors.py` 执行流与进度捕获；
  - 刮削执行完成自动以 `--apply` 与 `--backfill` / `--home` 将数据无损注入 SQLite 数据库。
- **全端分享卡片支持封面与封底双海报并排展示 (`ShareCardModal.vue` & `ShareCardDialog.kt` & `ShareCardView.swift`)**：
  - 影视分享卡片全面支持同时加载正向海报封面与反面封底（如有）；
  - 采用优雅并列卡片排版，左右标明「封面」与「封底」半透明质感胶囊，并无缝适配双离屏模糊保护与高清导出。
- **全端分享卡片角落新增 Telegram 官方频道二维码 (`ShareCardModal.vue` & `ShareCardDialog.kt` & `ShareCardView.swift`)**：
  - 在卡片底部角落集成 27x27 高清点阵二维码，直连官方频道 `https://t.me/gpdbnews`；
  - 采用白底圆角容器与高保真点阵矢量渲染，适配 Retina 2x 高清导出与预览，并搭配官方频道文字标示。
- **全平台客户端设置中新增「检查更新」按钮与即时检测升级**：
  - macOS、Windows 与 Android 客户端设置中统一新增「关于与软件更新」板块及「检查更新」功能按钮；
  - 点击后异步调用 GitHub 官方仓库 Releases API 检测最新版本，若本地已是最新版则给出明确友好反馈，若发现新版则弹出更新视窗/对话框，经用户确认后一键下载更新安装包（macOS DMG 自动挂载 / Windows EXE 向导 / Android APK 安装器）；iOS 客户端亦内置异步检测服务。
- **Android 探索主页顶部快捷防窥开关与即时模糊 (`HomeFeedScreen.kt`)**：
  - 主页顶部操作栏新增「防窥模式」快捷图标按钮，支持一键在正常浏览与防窥模糊模式间自由切换，并伴随即时 Toast 反馈；开启后全库海报、封面、剧照及演员头像立即覆盖高级高斯模糊层。
- **启动静默巡检 GitHub Releases 与内置增量升级**：
  - 桌面端（macOS DMG 挂载、Windows EXE 向导）与移动端（Android APK 安装）启动后静默巡检新版本，支持断点续传与一键无缝更新。
- **iOS 客户端目录脚手架与基础架构文件 (`iOS_client/`)**：
  - 基于 SwiftUI + GRDB.swift + ZIPFoundation 搭建现代声明式 iOS 离线架构，支持 SQLite 原生读取、Zip 虚拟文件系统与防截屏安全保护。

### Changed
- **Android 设置项更名：防窥模式 (`SettingsScreen.kt`)**：
  - 将设置「隐私与安全」中的「截屏隐私打码模式」正式更名为「防窥模式」，与主页快捷开关完全联动。
- **Android 设置页顶部二级胶囊分类切换栏 (`SettingsScreen.kt`)**：
  - 仿照 macOS 桌面端设计，在 Android 设置顶部增加二级胶囊菜单（全部、外观、统计、隐私、AI翻译、数据同步、关于更新），点击标签即时过滤对应板块，彻底消除设置项过多带来的臃肿感。
- **桌面端设置页二级胶囊菜单新增「关于与更新」独立分类 (`App.vue`)**：
  - 顶部胶囊导航新增「关于与更新」独立分类，便于用户快速切换直达版本更新与开源仓库专区。
- **连贯篇章系列大放送随机抽取展示 (`HomeView.vue` & `HomeFeedRepository.kt` & `HomeFeedView.swift`)**：
  - 首页「经典系列大放送 · 连贯篇章」由原有固定按影片数量降序（`ORDER BY movie_count DESC`）改为随机抽取排序（`ORDER BY RANDOM()`），并在桌面端标题栏增加「换一批」快捷随机刷新按钮。
- **设置页排版升级与纯图示交互体验**：
  - 移除 macOS 与 Windows 客户端设置页中关于「应用图标方案 (App Icon)」的冗长文字介绍；
  - 将桌面端与 Android 客户端设置中关于「海报展示与翻转排版方案」的大段说明文本全面替换为直观交互的微缩图解（平铺自适应画廊 vs 3D 景深翻转卡片），所见即所得。
- **Android 首页「今日星光 · 标志面孔」过滤优化 (`HomeFeedScreen.kt`)**：
  - 严格对齐 macOS 桌面端优质呈现标准，在今日星光圆环头像模块中过滤所有无头像或加载失败的空白项，只呈现拥有真实头像的明星面孔。
- **分享卡片全维度自适应排版与纯粹流光背景**：
  - 统一保留视觉表现最佳的默认「流光 (vibrant)」渐变背景；演职员名单与剧情简介完整换行展开展示，移除截断限制；彻底解耦全局防窥模式。
- **精简首页视觉与算力开销**：
  - 移除首页顶部的「AI 专属定制导赏」Banner 模块，降低冷启动 GPU/CPU 占用。

### Removed
- **全平台彻底移除奖杯成就系统插件及全量关联代码**：
  - 移除各端所有成就定义、监听与结算代码，侧边栏与插件管理器同步彻底净化。
- **移除桌面端「全功能伪装计算器 & 紧急脱身 (Panic Switch)」功能及代码**：
  - 移动端（Android / iOS）保留应用锁与安全防窥，桌面端全面剔除伪装计算器与紧急脱身快捷键，还原桌面纯粹典藏管理体验。

## [v2.14.0] - 2026-09-30

### Added
- **全新 PBC (Porn Base Central) 演员维基高精刮削与增量更新同步引擎 (`scrape_pbc_actors.py`)**：
  - **MediaWiki 分类全量深度爬取**：直连 `https://pbc.xxx/wiki/Category:Pornographic_actors`，全量解析 1,200+ 名演员主页，提取结构化维基百科数据。
  - **动态增量同步与编辑修订侦测 (`--recent`, `--update-library`)**：
    - 集成 MediaWiki `Special:RecentChanges` 接口，支持回溯指定天数（`--days`）与条目上限（`--recent-limit`）检索最新修订页面；
    - 支持 `--update-library` 本地库内巡检模式，深度回访已有演员主页并逐字段对比；
    - 内置 `compare_actor_with_db` 细粒度差异侦测算法，覆盖 25+ 项属性对比，仅在数据真正变更时执行 `UPDATE`，精准输出字段变动日志（如 `birth_date: '1985' -> '1985-04-12'`），无变动条目自动标记 `[UNCHANGED]`。
  - **全量无损整合入库与对齐**：
    - 成功对齐并持久化 1,195 位知名演员档案至 `GPDb.db` 的 `performer_pbc_profiles` 扩展表与 `performers.pbc_url`，匹配率高达 97.3%；
    - 严格遵循 GEVI 原始数据优先原则（GEVI-First Non-Destructive Resolution），保证已有数据 100% 完好无损。
  - **零依赖与多种安全模式**：
    - 基于 Python 3 原生标准库实现，纯零第三方 pip 依赖；
    - 提供 `--preview`（默认只读预览）、`--apply`（写入数据库）、`--only-new`（仅抓取新演员）、`--force`（强制更新）、`--backfill`（回填主表缺失字段）与 `--save-json`（离线导出）等灵活操作参数。

- **全新 SmutJunkies 演员信息高精刮削与增量更新同步引擎 (`scrape_smutjunkies_actors.py`)**：
  - **首波动监控与更新同步 (`--home`)**：实时抓取 `https://www.smutjunkies.com/home.html` 推荐与编辑更新的演员档案 (~106+ 位)，支持一键巡检最新演员。
  - **全站目录与字母索引覆盖 (`--letter <A-Z>`, `--all`)**：完整支持 SmutJunkies 26 个字母索引目录，覆盖全站 6,700+ 位男同成人演员档案。
  - **全维度深度属性提取**：
    - 基础标识：主艺名（Name）、内部模型编号（Model ID）、全尺寸高清 WebP 人物大图（Image URL）；
    - 演艺履历：头衔简介（Tagline）、活跃年限（Years Active，如 `2024 – 2026`）、出道年代标签（Decades，如 `2010's`, `2020's`）；
    - 厂牌与原籍：所属制片厂牌（Studios，如 BelAmi, Falcon, Men.com, Voyr 等）、国籍/族裔（Nationality）；
    - 细粒度身体与演出特征：身高、体重、丁丁尺寸、丁丁厚度/形态、包皮状态（Cut/Uncut）、体型、演出体位与攻受偏好、性取向、星座、年龄、鞋码、体毛、纹身与穿孔；
    - 社交与传记：官方社交档案直链（X/Twitter, Instagram, OnlyFans）、百科生平传记（Bio）与代表作片目（Filmography）。
  - **多级高容错精准对齐算法**：
    - 1级：精确主名匹配（大小写无关）；
    - 2级：归一化前缀匹配（自动兼容 GEVI 中带年代/厂牌后缀的演员名，如 `Dick Dawson (bgew)`）；
    - 3级：已知艺名与别名（Aliases / AKA）多向交叉反查；
    - 4级：URL Slug 关键字跨名深度匹配（如 `nikolai-lazaro-tyler-myers` 自动对齐至 GEVI 中的 `Tyler Myers`）。
  - **双向增量同步与编辑修订侦测 (Incremental Update & Diff Detection)**：
    - 新演员自动入库并打标 `[INSERTED]` / `[NEW]`；
    - 已收录演员逐字段对比差异，智能侦测网站修订（如活跃年限变更、厂牌增加、身体特征补充），记录具体变更项（如 `years_active: '2024' -> '2024 – 2026'`）并更新 `updated_at` 时间戳；若数据无变动则自动标记 `[UNCHANGED]`。
  - **安全预览与数据导出模式**：
    - 默认以 `--preview` 安全只读模式运行，不污染本地数据库，终端打印清晰的匹配报告与属性摘要；
    - 支持 `--save-json <path>` 导出结构化抓取结果；
    - 传入 `--apply` 参数时正式将新增与修订条目持久化写入 `GPDb.db`。
  - **数据库架构与三端底层支持**：
    - `schema.sql`、`db_manager.py` 与三端底层自动迁移新增 `performers.sj_url` 字段与 `performer_sj_profiles` 扩展表及其索引结构。

- **三大客户端演员档案页深度整合 PBC 百科全维度资料 (`desktop_client` / `Windows_client` / `android_client`)**：
  - **全平台一致性覆盖**：在 macOS 桌面端 (`PerformerDetailModal.vue`)、Windows 客户端 (`PerformerDetailModal.vue`) 与 Android 原生移动端 (`PerformerDetailScreen.kt`) 同步落地。
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

### Changed
- **演员档案页界面全面排版重构与布局优化 (`PerformerDetailModal.vue`)**：
    - **演员名全宽自适应排版，杜绝长名截断**：移除原有单行省略（`truncate`）限制，采用 `break-words leading-tight`，确保任意长度的复名、艺名以及外文长名完整优雅换行展示，不丢失任何文字。
    - **曾用艺名 / 别名 (AKA) 栏目全宽卡片重构 (`w-full`)**：
      - **彻底根治右侧大面积空白问题**：将原本堆叠于头像旁左列窄栏中的别名和备注独立移出，重构成全宽（`w-full`）独立卡片，放置于头像/身份行与操作按钮区正下方；
      - **顶部首行高度平衡**：头像、姓名与身份徽章居左，资源检索与收藏操作栏居右，彻底消除因大量别名纵向拉伸左列而导致的操作栏下方大面积视觉空白；
      - **高质感 Mini-Chip 徽章标签化呈现**：每个别名均渲染为精致独立的圆角徽标（`bg-surface-2/80 hover:bg-surface-3`），支持鼠标悬停、查看与高亮复制；
      - **智能折叠与平滑展开机制**：别名超过 8 个时（如部分演员多达 30~45 个别名），默认收起多余别名并展示 `+N 更多...` 快捷胶囊与 `展开全部 (共 X 个) / 收起部分别名` 交互切换按钮，既保持界面紧凑清爽，又支持一键秒级展开全貌；
      - **多源别名深度聚合去重**：合并影视作品表关联别名（`performer.aliases`）、SmutJunkies 别名（`performer_sj_profiles.aliases`）与 PBC 维基百科别名（`pbc_profile.aliases`），自动剔除自身当前姓名与重复项。
    - **演员备注信息全宽化呈现**：将 `performer.notes` 从窄列移入全宽优雅的独立文本容器，避免窄栏多行堆叠。
    - **全平台同步落地**：在桌面端 (`desktop_client/src/components/PerformerDetailModal.vue`) 与 Windows 客户端 (`Windows_client/src/components/PerformerDetailModal.vue`) 同步对齐。

### Fixed
- **Rust 后端演员别名与 SmutJunkies 详情查询补全 (`gpdb-core/src/queries/performers.rs`)**：
  - 在 `get_performer_detail` 查询中，自动合并读取 `performer_sj_profiles` 中的别名数据并填充至 `p.aliases`；
  - 增加 `sj_url` 查询高容错回退机制，确保 `performers` 与 `performer_sj_profiles` 两表的 SmutJunkies 档案主页直链均能完整透传至前端操作栏。
- **Rust / Tauri 桌面端编译依赖修复**：
  - 修复 `desktop_client/src-tauri/Cargo.toml` 与 `Windows_client/src-tauri/Cargo.toml` 中 `base64 = "0.22"` 依赖缺失导致的编译阻断。
  - 修复 `database.rs` 中 `get_unique_filepath` 模块私有可见性问题，提升为 `pub(crate)`。
  - 修复 macOS 原生系统剪贴板写入实现中 `objc_msgSend` 函数指针 transmute 类型安全问题。
  - 通过全量 69 项 Rust 核心单元与一致性测试、前端 Vue-tsc 检查与 Android Gradle 编译。
- **SQLite FTS5 全文索引一致性修复**：
  - 重建 `movies_fts` 虚表全文检索索引 (`INSERT INTO movies_fts(movies_fts) VALUES('rebuild')`)，确保数据库经 `PRAGMA integrity_check` 校验返回 `ok`，排除索引与实体表记录不同步隐患。

## [v2.13.0] - 2026-09-29

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
- **Android 原生客户端全面同步上线截屏隐私打码与卡片分享 (`android_client`)**：
  - **截屏隐私打码模式**：设置「隐私与安全」新增全局打码开关，支持独立开启高斯模糊所有海报/剧照/头像图片以及高斯模糊剧情介绍文字，基于 Jetpack Compose `LocalPrivacyBlur` 响应式状态流驱动。
  - **影片与分集自适应取色分享卡片**：在 Android 影片详情 (`MovieDetailScreen`) 与分集档案 (`EpisodeDetailScreen`) 顶部增设「卡片分享」功能。卡片自适应采样海报主导色与深色渐变构建高斯光晕背景，集成中英标题、参演演员、核心元数据及简介；弹窗内嵌「模糊海报」与「模糊文字」双安全脱敏开关；支持一键保存至系统相册 (`MediaStore`) 及通过 `FileProvider` 唤起原生分享面板。

### Changed
- **影片发行年份与分集发行日期粒度分离与规范化**：
  - 影片（Movies）以作品自身的 `release_year` 年份为准，避免片商合并旧分集发售时分集日期干扰长片发行年份定义；
  - 分集（Episodes）保留精确至“天”的具体发行日期（`release_date`），时间筛选选项优化为「上次入库（按刮削时间）」以及「最近7天发行 · 最近30天发行 · 最近90天发行（按具体日期）」。

### Fixed
- **修复时间筛选 SQL 缺失字段异常 (`no such column: m.release_date`)**：修复当用户点击“最近7天 · 最近30天 · 最近90天”筛选时后端 SQL 报错的问题，修正为根据影片 `release_year` 及入库时间 `created_at` 执行高容错查询。
- **修复 3D 实体卡片双面翻转排版方案**：排查并修复「海报展示与翻转排版方案」选择“3D 拟真实体卡片”时只显示正面、无法翻转至封底的问题，恢复流畅真实的 3D 卡片交互翻转与典藏背面质感。

## [v2.12.0] - 2026-09-29

### Added
- **全新独立 Windows 客户端工程体系 (`/Windows_client`)**：
  - **架构完全独立解耦**：在项目根目录建立专有 `/Windows_client` 文件夹，将 Windows 平台特定的 Tauri 2 配置、打包管线、构建脚本与界面微调收拢于单一工程内，彻底避免与 macOS (`desktop_client`) 及 Android (`android_client`) 的开发环境产生任何文件侵染。
  - **专属开箱即用构建套件**：提供 `build_windows.ps1` (PowerShell) 与 `build_windows.bat` (批处理) 双脚本，自动检查 Node.js 与 Rust 工具链版本，一键自动拉取依赖并编译生成 Windows 安装程序。
  - **完善的工程自述与指引**：新增 `Windows_client/README.md`，详细记录本地开发运行、环境依赖要求（Node 18+、Rust MSVC、WebView2 Runtime）与 NSIS 打包全流程。
- **Windows Python 运行环境智能探测链 (Multi-Source Python Resolution Engine)**：
  - 针对 Windows 平台下 Python 安装形态多样的痛点（官方包、微软商店沙盒、Conda、自定义路径），实现多级分层智能探测：
    1. 优先读取用户显式指定的 `GPDB_PYTHON` 环境变量；
    2. 自动检索 `%LOCALAPPDATA%\Programs\Python\Python3xx\python.exe` 独立用户目录；
    3. 自动检索 `%ProgramFiles%\Python3xx\python.exe` 机器全局目录；
    4. 自动检索 `%USERPROFILE%\anaconda3\python.exe` 与 `%USERPROFILE%\miniconda3\python.exe` 科学计算环境；
    5. 降级执行 `python` / `python3` / `py` 命令行探测与快速静默启动校验，确保各类用户即装即用。

### Changed
- **跨平台 CI/CD 自动化构建矩阵解耦 (`.github/workflows/release.yml`)**：
  - 构建矩阵引入 `client_dir` 参数，macOS 节点绑定 `desktop_client`（产出 `.dmg` / `.app`），Windows 节点绑定 `Windows_client`（产出 NSIS `.exe`），两端缓存键与依赖安装完全独立隔离。
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

## [v2.11.1] - 2026-09-29

### 🚀 移动端搜索核心修复与视觉优化 (Android Search Engine Fix & Visual Polish)
- **“影片”Tab 与全局搜索结果为空彻底修复 (Multi-Field Resilient Search Engine)**：
  - **根因排查**：原移动端搜索直接依赖 SQLite FTS5 虚拟表（`movies_fts` 与 `performers_fts`）。在部分 Android 设备环境或沙盒初始化数据库中，缺少 FTS5 虚拟表直接导致抛出 `SQLiteException: no such module/table: movies_fts`，静默被拦截后致使影片搜索结果恒为空；且 FTS5 原 unicode61 分词器无法识别连续中文词句，且未涵盖出演演员与片商维度。
  - **三级降级防御检索架构**：FTS5 高速召回 -> 异常无缝自动降级至标准 SQL 多字段联合检索 -> Room DAO 终极兜底，100% 杜绝因虚拟表缺失或分词异常造成的空结果。
  - **全维度深度检索匹配**：单次检索同时跨越英文原名 (`title`)、中文译名 (`title_zh`)、片商名 (`studio_name`)、导演名 (`director_name`)、中文简介 (`description_zh`)、出演演员关系表 (`movie_performers.performer_name`) 以及影片 ID。
  - **响应式仓库生命周期管理**：`SearchViewModel` 接入 `DatabaseHolder.isReadyFlow`，彻底杜绝数据源未就绪时的空指针悬挂。
- **“主页”频道 TopBar 移除搜索按钮 (Home Feed TopBar Streamline)**：
  - “主页”(`HomeFeedScreen`) 顶部操作栏正式移除搜索放大镜图标按钮，保留刷新控制；搜索职责收归各对应专属频道（影片、演员、片商、导演、我的收藏），主页浏览交互更加聚焦沉浸。
- **最新版签名发布 APK 编译就绪**：
  - 编译最新 Release 签名包并部署至项目根目录：`GPDb_Release.apk`（4.3MB）与 `GPDb_Android_v2.11.1_release.apk`，通过 APK Signature Scheme v2 正式签名与 R8 代码混淆优化。

## [v2.11.0] - 2026-09-29

### ✨ 新功能与双端生态全面对齐 (Cross-Platform Alignment & Features)
- **多维度时间与入库批次时效筛选 (Multi-Dimensional Date Filters)**：
  - 双端全面统一支持 6 大时间范围过滤：`全部 (All)`、`上次入库 (Last Scraped)`、`最近7天 (Recent 7 Days)`、`最近30天 (Recent 30 Days)`、`最近90天 (Recent 90 Days)` 以及 `本年度 (Recent Year)`。
  - macOS 客户端在影片与分集主界面新增快速时间筛选胶囊栏，在高级筛选抽屉 (`FilterDrawer.vue`) 与活动筛选标签栏 (`ActiveFilterBar.vue`) 全面挂载时间胶囊交互，Rust 底层查询引擎 (`gpdb-core`) 与 Android SQL 筛选规则完全对齐。
  - 影片卡片 (`MovieCard.vue`) 与分集卡片 (`EpisodeCard.vue`) 新增 2026 及最新入库批次条目的动态渐变 `NEW` 高亮微标。
- **海报双排版方案与全屏手势/滚轮缩放灯箱 (Poster Schemes & Zoomable Lightbox)**：
  - macOS 客户端详情页全面对齐移动端的海报呈现范式：支持“自适应高清画廊 (`adaptive_pager`)”与“3D 拟真实体卡片 (`flip_3d`)”，具备 60fps CSS 3D 景深物理透视、实体翻转胶囊按键与环境泛光。
  - 全屏灯箱 (`ImageLightbox.vue`) 全面重构：支持鼠标滚轮平滑缩放 (1.0x ~ 5.0x)、按住拖拽平移、双击智能缩放/复位、键盘快捷键 (+/-/0/Esc) 与悬浮操作工具栏。
- **macOS 桌面端系统级隐私安全套件 (Desktop Privacy & Security Suite)**：
  - **PIN 码安全应用锁**：新增数字 PIN 锁与全屏玻璃拟态锁屏界面 (`AppLockOverlay.vue`)，支持自定义自动锁定策略（失焦立即锁定、离开 1/5/15/30 分钟后自动锁定）。
  - **窗口失焦高斯毛玻璃防窥遮罩**：失焦或切换其他应用时瞬间覆盖高斯毛玻璃防窥层（对齐 Android `FLAG_SECURE` 防录屏与防窥探）。
  - **全功能伪装计算器 & 紧急脱身 (Panic Switch)**：一键瞬间伪装为 Apple 标准深色计算器 (`FakeCalculatorModal.vue`)，支持真实四则运算；按下全局快捷键 <kbd>Cmd + Shift + P</kbd> 极速呼出；在计算器中输入 PIN 码并按 `=`，或连击顶部标题 4 次即可安全解锁返回。
  - **应用窗口伪装标题**：支持自定义窗口标题（如 "Calculator", "Notes"），防止任务切换器或窗口列表中暴露应用属性。
- **大模型翻译数据双向导入导出与动态探测 (JSON Translations & Model Discovery)**：
  - 在插件中心新增“导出翻译 (JSON)”与“导入翻译”功能，通过 Rust 底层直接读取/写入 SQLite，支持跨平台（Android / macOS / CLI）无缝合并沉淀的已翻译影片与分集简介。
  - 在翻译服务配置面板中新增“探测可用模型”一键检测按钮，调用标准 OpenAI 兼容 `/models` 接口自动提取账号可用模型并填入建议列表。
  - 翻译服务商新增字节跳动豆包 (`doubao`) 预设。
- **导演频道与全库深度对齐 (Directors System Alignment)**：
  - 完善导演维度索引、导演作品多维度关联计算与收藏闭环，Android 客户端与 macOS 桌面端实现 100% 交互与数据结构统一。
- **版本号全生态同步升级 (Ecosystem Version Parity)**：
  - Android 客户端 (`app/build.gradle.kts` versionCode 310, versionName 2.11.0) 与桌面客户端（`package.json`, `tauri.conf.json`, `Cargo.toml`, `gpdb-core`）统一同步跃升至 `v2.11.0`。

## [v2.10.0] - 2026-09-29

### 🛡️ 隐私安防套件与展示增强 (Privacy & Security Suite & Visual Enhancements)
- **海报多方案展示与全屏手势缩放灯箱 (Poster Display Schemes & Zoomable Lightbox)**：
  - Android 移动端支持 3D 拟真翻转卡片与自适应画廊轮播双方案，并内置多点触控全屏缩放灯箱。
- **进阶系统级隐私安全套件 (Advanced Privacy & Security Suite)**：
  - 移动端引入系统级 `FLAG_SECURE` 防多任务截屏与防窥探防护。
  - 接入生物识别与独立 4~6 位数字 PIN 码应用锁，超时切后台自动锁定。
  - 引入紧急一键脱身手势（正面朝下扣桌/剧烈摇晃），瞬间呼出真实可用暗黑高仿计算器伪装页面，输入专属 PIN 码安全重返影库。
  - 支持桌面图标与应用标题无害化伪装（极简便签、常用计算器、收支记账）。
- **物理沙盒与 .nomedia 递归隔离**：
  - 强制应用内部沙盒存储机制，相册与第三方 App 物理绝缘，递归注入 `.nomedia` 防护。

## [v2.9.0] - 2026-09-29

### 🚀 移动端架构大版本迁移与生态融合 (Mobile Architecture & Feature Parity)
- **macOS「主页」发现流完整迁移至 Android (Home Feed Discovery Tab)**：
  - 移动端全面上线镇馆之选 (Hero Carousel)、往年今日 (On This Day)、今日星光 (Star Spotlight)、经典系列 (Series)、随心探索 (Lucky Discovery) 及影库全景统计。
- **分集频道独立扩展 (Dedicated Episodes Tab)**：
  - 移动端主界面与桌面端全面对齐，新增专属分集流，支持全库数万分集瀑布流与剧照海报联动。
- **原生增量网络刮削与反爬绕过 (Native Incremental Scraper Engine)**：
  - 移动端原生实现 `/newm`, `/newe`, `/newp` 增量数据解析与事务写入，解决移动端免 ZIP 大包按需图片离线缓存。
- **AI 智能大模型翻译与翻译统计持久化**：
  - 移动端原生接入 OpenAI / DeepSeek / Claude / Gemini 协议大模型翻译，翻译结果持久化落库。

## [v2.8.0] - 2026-09-24

### ✨ 新功能与安卓原生移动端发布 (Features & Native Android Release)
- **原生 Android 移动端客户端发布 (Kotlin / Jetpack Compose / Room)**：
  - 首个全功能原生 Android 客户端正式发布！采用现代化技术栈（Kotlin + Jetpack Compose + Room + Material 3），支持离线数据库查询、极速模糊搜索、演职人员与制片厂牌浏览、打分与收藏足迹等。
- **Android / macOS / Windows 三端并发支持**：
  - 项目正式实现全平台覆盖（macOS Universal, Windows x64, Android APK）。
- **收藏功能全面升级 (Favorites Expansion)**：
  - 在影片详情页 (`MovieDetailScreen`) 与演员详情页 (`PerformerDetailScreen`) 的顶部导航栏恢复了“收藏 (Favorite)”按钮，支持一键加入或取消收藏。
  - 在系列专题列表页 (`FilteredMovieListScreen` - `series` 模式) 新增了系列收藏功能，支持将喜欢的系列一键加入收藏。
  - 在“我的库 (`LibraryScreen`)”中新增了“收藏系列” Tab 页，实时查询并展示所有已收藏的系列，支持点击直接跳转至该系列详情页。


## [v2.7.1] - 2026-09-24

### ⚡ 优化 (Changed)
- **应用图标方案默认顺序调整**：
  - 将「双雄火星图腾」(The Twin Mars Monolith) 由原方案 B 调整为**方案 A（默认）**，成为新用户首次启动时展示的 Dock 图标。
  - 将「黑曜石棱镜胶片之匣」(The Obsidian Film Vault) 由原方案 A 调整为方案 B。
  - 同步物理交换 `scheme-a.svg`、`scheme-a.png` 与 `scheme-b.svg`、`scheme-b.png`，确保 Dock 图标、Favicon、图标选择器预览三处展示完全一致。
  - `appIcon.ts` 元数据（名称、描述、标签、评级、`badge`）随文件同步更新。

## [v2.7.0] - 2026-09-23

### ✨ 新功能与平台扩展 (Features & Multi-Platform)
- **Windows 桌面客户端全新支持**：
  - 构建架构全面适配 Windows 平台，支持一键安装 `.exe` (NSIS) 与 `.msi` 安装包。
- **云端双端并行 CI/CD 自动化打包流水线**：
  - 升级 GitHub Actions 工作流 `.github/workflows/release.yml` 为矩阵构建（Matrix Build），实现 **macOS (Universal - Apple Silicon & Intel)** 与 **Windows (x64)** 双平台自动化编译、打包与 Release 直传挂载。
- **Telegram 频道显要嵌入**：
  - 全语种 7 份 README 顶部全面嵌入大尺寸醒目 Badge 与官方 Telegram 频道（https://t.me/gpdbnews）链接及高亮公告框。

## [v2.6.2] - 2026-09-23

### 🐛 修复 (Fixed)
- **「增量极速同步」启动失败：真正根因定位与完整修复（两阶段）**：
  - **第一阶段（误判）**：最初误判为 Tauri build 目录下资源镜像残留旧文件名 `sync_gevi.py` 所致，向 `target/release/bundle/` 及 `target/debug/` 内的 `_up_/_up_/` 目录补入 `sync_gpdb.py` 并删除旧文件，客户端重启后错误依旧。
  - **第二阶段（真正根因）**：通过 `strings` 命令检查 `/Applications/GPDb.app/Contents/MacOS/gpdb` 二进制文件，发现二进制内部仍硬编码 `sync_gevi.py`。这说明用户实际运行的 `/Applications/GPDb.app` 是**源码改名之前编译的旧版二进制**，与 `target/release/bundle/` 里的 bundle 完全独立，替换资源文件对其无效。
  - **最终修复**：
    1. 执行 `npm run tauri build -- --no-bundle` 重新编译 Rust 二进制（23 秒完成）；
    2. 将新二进制 `target/release/gpdb` 覆盖安装至 `/Applications/GPDb.app/Contents/MacOS/gpdb`；
    3. 同步更新 `/Applications/GPDb.app/Contents/Resources/_up_/_up_/` 下全部 Python 脚本，删除残留的 `sync_gevi.py`，写入 `sync_gpdb.py`、`sync_bftv_catalog.py` 等最新版本；
    4. `strings` 验证确认新二进制内只含 `sync_gpdb.py`，`sync_gevi.py` 彻底消失。

### ⚡ 优化 (Changed)
- **`tauri.conf.json` 资源清单补全**：
  - 发现 `bundle.resources` 列表中遗漏了 `sync_bftv_catalog.py`，导致正式 `tauri build` 打包时「BFTV Catalog 同步」模式所需脚本不会被纳入应用包。已补充该条目，确保下次构建所有脚本均正确随包分发：
    ```json
    "../../sync_gpdb.py",
    "../../sync_bftv_catalog.py",   // 新增
    "../../batch_scraper.py",
    "../../db_manager.py",
    "../../scrape_bftv_performers.py",
    "../../cache_images.py"
    ```

## [v2.6.1] - 2026-09-23

### 🐛 修复 (Fixed)
- **奖杯自动复活与无法清空故障**：
  - 修复 `trophySystem.ts` 中 `bronze_plugin_toggle`、`bronze_db_scanned`、`bronze_lang_switch`、`bronze_grid_adjust` 触发条件硬编码为 `condition: () => true` 以及 `bronze_plugin_bt` 默认状态为 true 的设计缺陷。修复前每次专注计时刷新（每15秒）或产生交互都会自动重新点亮这些奖杯，导致清空操作失效。
  - 在 `UserAnalytics` 中引入了真实的行为指标统计（`pluginsVisitedCount`、`settingsVisitedCount`、`langSwitchedCount`、`gridAdjustedCount`、`btUsedCount`），所有奖杯成就仅在满足实际操作阈值后才可解锁，数据为 0 时严格锁定。
- **重置弹窗选项缺失与连环解锁死循环**：
  - 重写 `TrophyResetModal.vue`，彻底解决先前“方式 1”默认触发连续解锁队列导致奖杯无法保持清空的问题；新增“仅清空已获成就奖杯（重置奖杯数据）”专属选项，并提供“彻底抹除全部数据”与“重新检定连环解锁”三种清晰独立的操作模式。
- **跨视图奖杯统计状态脱节**：
  - 修复 `PluginsView.vue` 中 `trophyStats` 使用未同步的独立 ref 导致的统计数字不更新问题，统一导入响应式 `trophyStats`。
  - 修复 `AnalyticsView.vue`“清空所有统计”时未联动重置 `resetUnlockedTrophies()` 的问题。

### ⚡ 优化 (Changed)
- **奖杯重置机制与即时清空**：
  - 彻底清空并重置本地已获得奖杯数据（`gpdb_unlocked_trophies`），将奖杯系统恢复为初始未解锁状态（0/77）。
  - 在多语言切换（`setLocale`）、网格列数调节（`decreaseCols`/`increaseCols`）、页面 Tab 切换以及 BT 磁力搜索时建立精准的埋点追踪。

## [v2.6.0] - 2026-09-23

### 🚀 架构重构 (Architectural Refactoring)
- **后端核心与边缘模块解耦**：
  - 将 AI 翻译（`translate.py`）与 BFTV 数据同步（`sync_bftv_catalog.py`）的杂乱调度逻辑从主服务 `server.py` 中彻底剥离。
  - 新增独立的 `plugins/` 目录，封装上述功能为独立的模块接口，确保 `server.py` 恢复纯粹的 API 路由分发职责，并且严格保持零第三方依赖。
- **Rust 数据层纯净度审查**：
  - 全面审查 `gpdb-core` (Tauri/Rust 查询层)，确保其纯粹提供原始数据（如 `works_count`），不耦合任何前端 UI 的呈现状态与标识逻辑。

### ⚡ UI 极简与性能优化 (UI Simplification & Performance)
- **剔除过度设计的特效**：
  - 彻底移除耗能较高的 Web Audio 6 音琶音合成器 (`soundSynthesizer.ts`)。
  - 重构 PSN 风格的 `FluidGlassTrophyIcon`，移除严重消耗 GPU 的流体玻璃滤镜 (`backdrop-filter`)，采用扁平化 (Flat) SVG 徽章代替，大幅提升渲染性能。
- **拍平复杂交互层级**：
  - 重写影片与演员详情页中的“想看/已看”多模态手风琴卡片，将其折叠、弹窗打分等繁琐交互降级为清晰直观的单层级切换 (Toggle) 与内联展示。
  - 简化“功能插件/设置中心”面板，移除多余的动态叠加特效，回归标准 Tab 选项卡视图。

### 🧩 模块化升级 (Modularization)
- **前端外挂组件懒加载**：
  - 将各个详情页中重复内嵌的“BT 搜索”、“BFTV 主页直达”等外部资源按钮提取为独立的懒加载组件 (`ResourceSearchWidget.vue`)。
  - 将极速匹配与刮削自动化配置面板隔离至专属的 `SyncModal.vue` 并按需挂载。
  - 全面修复重构过程中产生的组件导入缺失与 TypeScript 报错，执行 `vue-tsc -b` 零警告通过。

## [v2.5.2] - 2026-09-23

### 🚀 新增 (Added)
- **BFTV 演员主页 Sitemap 本地持久化缓存**：
  - `sync_bftv_catalog.py` 新增本地缓存机制，同步完成后将最新 Sitemap XML 持久化至数据库同目录 `sitemap_pornstars.xml`。
  - 每次同步自动与旧缓存做差异对比，并输出新收录演员数量（`✨ 发现 BFTV 官方最新收录演员 N 位！`），实现增量感知。
  - 网络不可达时自动降级读取本地缓存，确保离线场景下继续可用。

### ⚡ 优化 (Changed)
- **演员详情页 BFTV 按钮全面升级**：
  - 移除"在 BFTV 搜索演员资料" fallback 搜索按钮，不再为无法匹配的演员生成搜索链接。
  - 仅当演员拥有已关联的 BFTV 主页 URL 时，才在演员详情弹窗中显示独立的"BFTV #编号"直达按钮（如 `BFTV #5862`）。
  - 按钮样式升级为琥珀色主题，视觉上明确标识为 BFTV 专属功能，点击即可直达官方主页。
  - 按钮编号从 URL 中实时解析提取（正则取最后数字段），无需额外数据库字段。

### 🐛 修复 (Fixed)
- **修复演员详情页 BFTV 按钮跳转逻辑误判问题**：
  - 之前即使演员不在 BFTV Sitemap 中（`bftv_url` 为 NULL），按钮仍显示为"在 BFTV 搜索演员资料"并错误地 fallback 到搜索页，让用户误以为会直达主页。
  - 现在改为：有 BFTV URL → 显示"BFTV #编号"直达按钮；无 BFTV URL → 不显示该按钮，逻辑简洁透明。

---

## [v2.5.1] - 2026-09-23


### 🚀 新增 (Added)
- **BoyfriendTV (BFTV) 演员档案逆向极速同步引擎 (`sync_bftv_catalog.py`)**：
  - **全网目录逆向关联突破性策略**：针对传统单人逐一向 BFTV 搜索匹配耗时数小时且易触发 Cloudflare 质询的性能瓶颈，反向利用 BFTV 全站演员数量远少于本地影库的特性，从 BFTV 官方 CDN Sitemap 瞬时下载解析全站 12,436+ 位男星/模特的官方主页 URL 与专属编号。
  - **零 Cloudflare 阻断极速拉取**：直连 CDN 节点，2.6 秒内完成全站演员列表下载解析，彻底摆脱反爬拦截限制。
  - **智能别名净化与内存倒排索引匹配**：自动剥离 GPDb 数据库中演员后缀如 `(dp)`、`(white)`、`(asian)`、`(aka Kenny)`，并支持连字符 Slug 格式还原与标准化去重匹配。10.8 万演员内存哈希比对仅耗时 0.1 秒，单次批量更新事务仅耗时 3.05 秒，一次性为本地数据库关联新增 16,950+ 位演员的官方 BFTV 直达主页。
  - **无缝集成增量同步流**：在 `sync_gpdb.py` 增量发现新入库演员时自动触发逆向匹配，确保新入库演员立即具备 BFTV 资料直达能力。

### ⚡ 优化 (Changed)
- **自动化刮削更新插件高度自定义执行选项面板**：
  - 在“功能外挂”控制台的“自动化刮削更新”插件中，新增常驻“高度自定义执行选项”展开面板。
  - 用户可在插件内直接切换并配置五大执行策略：
    1. ⚡ **增量极速同步**（自动抓取官网 `/newm`、`/newp`、`/newe`）
    2. 🌐 **BFTV 演员主页秒级关联**（3 秒注入 12,000+ 演员直达链接）
    3. 🚀 **热门新片逆序爬取**（自定义数量 500 ~ 2,000 部）
    4. 🎯 **指定 ID 范围抓取**（自定义起始与结束 ID，支持高达 76,000 范围抓取）
    5. 👥 **演员全量资料补齐**（一键补齐已知演员身材属性与写真头像）
  - 动态显示参数调整输入框（如抓取上限、起始与结束 ID），并提供“立即按自定义配置启动”按钮与全生命周期进度追踪。
- **全功能同步控制中心 (`SyncModal.vue`) 接入 BFTV 演员全网极速匹配**：
  - 新增“BFTV 演员主页全网极速匹配 (3秒入库万条)”策略卡片，支持一键在弹窗中启动并在终端中实时查看匹配进度。

### 🐛 修复 (Fixed)
- **修复应用打包未同步部署导致旧版占位提示文字残留问题**：
  - 针对用户反馈“点击运行后不会开始运行脚本，每次都只输出 增量同步完成！新增影片: 0 部，新增演员: 0 位。已导入数据库，请点击顶部「同步」按钮刷新”的根本原因进行彻底根治：该字符串来源于历史早期的占位 Mock 代码，因本地 `/Applications/GPDb.app` 停留在早间旧版进程未自动更新导致。
  - 重新全量编译 release 二进制与 DMG 生产包并安全部署至 `/Applications/GPDb.app`，全面激活新一代异步刮削引擎。

---

## [v2.5.0] - 2026-09-23

### 🚀 新增 (Added)
- **定时自动后台更新新条目 (Scheduled Background Auto-Sync)**：
  - 自动化刮削更新插件全新集成定时后台静默同步引擎，支持开启/关闭自动调度。
  - 支持用户高度自定义执行计划：
    - **周期循环模式**：可自由设定每 4 小时、6 小时、12 小时（推荐）、24 小时或 48 小时自动静默抓取。
    - **每天定点模式**：支持自定义每天指定时刻（如凌晨 04:00）在后台静默执行。
  - 心跳调度服务 (`services/autoSync.ts`) 实时计算上次自动同步时间与下次计划执行时间，并在同步中心 (`SyncModal.vue`) 与插件页 (`PluginsView.vue`) 提供呼吸态状态指示。
  - 任务执行采用完全解耦的异步后台线程，不占用前台 UI，入库完成后自动无感刷新影库数据与统计信息。
- **首次安装运行环境自动校验与配置向导 (Runtime Environment Check & Guidance)**：
  - 新增 Rust 端 `check_runtime_environment` 诊断命令，全面探测系统环境健康状况：
    - Python 3 解释器安装状态、版本号及执行路径（自动化刮削更新核心基石）；
    - Python 内置 SQLite 模块健康度；
    - 本地 `GPDb.db` 核心数据库连接与有效性；
    - 进阶 Playwright 浏览器反爬自动化引擎就绪状态（用于 BoyfriendTV Cloudflare 穿透）。
  - 全新设计并上线 `EnvironmentCheckModal.vue` 诊断向导：
    - 首次安装或核心环境未就绪时自动温和提醒用户；
    - 提供苹果官方命令行工具 (`xcode-select --install`) 与 Homebrew (`brew install python3`) 一键复制安装命令；
    - 支持一键“重新检测环境”与“去配置数据库”；
    - 在“功能外挂”控制台常驻“运行环境自检”入口，支持随时重新发起体检诊断。

- **增量同步时自动下载并持久化封面与剧照 (Auto-Download Images to Cache)**：
  - 针对增量同步（`sync_gpdb.py`）后新入库条目仅存 URL 无本地图片缓存的问题，新增 `download_image_to_cache()` 离线缓存下载引擎。
  - 在同步新电影、新演员、新分集元数据时，同步将海报大图（`Covers/`）、缩略图（`Icons/`）、分集剧照（`Episodes/`）以及演员写真（`Stars/`）持久化至 `image_cache/`。
  - 默认注入防盗链请求头（`Referer: https://gayeroticvideoindex.com/`）与 `curl` 双重重试机制，有效规避 Cloudflare TLS 异常，确保 macOS 客户端即时以 `gpdb-img://` 协议丝滑秒开高清封面。
- **macOS 27 规范 Dock 栏图标原生热切换与双轨同步引擎**：
  - **遵循 macOS 27 HIG 规范**：全套图标统一遵循 macOS 连续曲率超椭圆（Squircle）网格、824px 画布安全边距、环境落影（Ambient Drop-Shadow）与微透晶体材质规范，由新版 `generate-app-icons.py` 自动化管线生成包含 16x16 至 1024x1024 全像素阶梯的 `AppIcon.icns` 与现代化 Asset Catalog `Assets.car`。
  - **用户自选方案双轨持久化**：新增 `~/.gpdb_icon_scheme` 配置持久化层；Rust 原生 `setup` 启动钩子与前端 `initAppIcon()` 双重加载生效，确保冷启动、热重启或系统唤醒时 Dock 图标均精确保持用户自选方案。

### 🐛 修复 (Fixed)
- **彻底删除旧版 Dock 栏硬编码图标与冷启动重置缺陷**：
  - 彻底删除并清理原 `make-app-icon.py` 与 `AppIcon.icon` 硬编码生成的琥珀褐色 "G" 盘片图标以及旧版 Tauri 遗留资源，将官方推荐方案 A「黑曜石棱镜胶片之匣」设为编译期全局基准图标。
  - 修复前端 `initAppIcon()` 因历史注释未在应用启动时同步调用原生 Dock 图标更新指令的问题，彻底杜绝冷启动时 Dock 栏重置为旧图标的缺陷。
  - 重构 `commands/system.rs` 中的 `set_dock_icon_macos`，接入 `[NSApp setApplicationIconImage:]` 并协同 `[[NSApp dockTile] display]` 强制刷新 Dock Tile，修复切换后偶发延迟重绘问题，并补齐 `release` 内存管理。
- **BoyfriendTV 演员爬虫 Cloudflare Turnstile 质询拦截与别名干扰修复**：
  - **动态穿透 Turnstile 质询盾**：针对 BoyfriendTV 搜索网关 (`/searchgate/`) 部署的 Cloudflare Turnstile 人机质询，升级 Playwright 隐身指纹注入（抹除 `navigator.webdriver` 特征、模拟真实 Chrome 插件及英文语言环境），并引入自适应轮询等待机制（最长 8 秒自动检测并等待 Turnstile 盾解除），彻底根治此前仅等待 2.5 秒导致质询未完成即被判为“未找到”的卡点。
  - **搜索关键词智能净化 (Query Normalization)**：针对 GPDb 数据库中超 26% 演员带有括号别名或年代标注（例如 `(white)`、`(80s)`、`(aka Kenny)`）导致 BFTV 模糊匹配失效的问题，新增 `clean_performer_name()` 正则净化引擎，自动剔除注释字符，大幅提升现代活跃演员的检索命中率。
  - **收录状态精细化提示**：控制台输出细化区分“✓ 匹配成功”与“(BFTV未收录)”，避免混淆网络反爬拦截与平台数据源收录范围差异（BFTV 主打近 20 年活跃模特，GPDb 跨越 50 年历史）。

### ⚡ 优化 (Changed)
- **数据库路径全生态自动识别与双轨持久化**：
  - 客户端成功打开数据库时，自动将规范化绝对路径双轨写入 macOS 标准配置 `com.gpdb.app/db_config.json` 与便利标记文件 `~/.gpdb_db_path`。
  - Python 端重构 `find_default_db_path()` 算法，按序从环境变量 `GPDB_DB`、客户端配置文件、快捷标记文件及本地工作区自动定位数据库，`scrape_bftv_performers.py`、`sync_gpdb.py`、`batch_scraper.py`、`cache_images.py` 无需再手动加 `--db` 参数即可零配置运行。
- **BoyfriendTV 演员主页直链批量抓取能力增强**：
  - `scrape_bftv_performers.py` 优化抓取调度排序，优先遍历有肖像头像的活跃演员，支持 `--name` 单演员精准测试与秒级落库更新。

---

## [v2.4.1] - 2026-09-23

### 🐛 修复 (Fixed)
- **自动化刮削更新插件历史 404 误判修复**：
  - 修复 `sync_gpdb.py` 中因历史全量探测导致大量新 ID（75191..76000）被记录为 404 而被 `get_completed_ids` 判定为“已完成”进而 100% 误杀过滤新片的问题。
  - 重构增量待抓取列表计算逻辑：官网 `/newm`、`/newp` 确认发现的新片/新星条目不受历史 404 阻断，仅对本地数据库已收录记录去重；前向探测引入时效保护机制，并自动清除超前 404 占位缓存。
- **官网最新分集（/newe）增量接入与自动解析**：
  - 在 `sync_gpdb.py` 中接入 `/newe` 实时更新流，新增 `get_latest_ids_from_pages` 与 `parse_episode_details` 专用解析器。
  - 自动抓取新分集标题、高清预览剧照、所属片商（Studio/Company）、发布日期以及关联出演演员表，并通过 `save_company_episodes` 深度入库。
- **macOS 客户端环境下的 Python 路径与独立安装包资源寻址**：
  - 在 Tauri 核心 `commands/sync.rs` 中引入 `resolve_python()` 路径降级解析器，按序探测 `/opt/homebrew/bin/python3`、`/usr/local/bin/python3`、`/usr/bin/python3`，解决 macOS GUI 进程无终端环境变量导致的启动失败。
  - `find_script` 引入多级寻址（含 Tauri App 资源目录 `app.path().resource_dir()` 与可执行程序祖先目录），并在 `tauri.conf.json` 中配置 `bundle.resources`，确保打包 DMG / 独立应用后依然能稳定调用刮削脚本。

### ⚡ 优化 (Changed)
- **“功能外挂”自动化刮削插件视图实时体验升级**：
  - 重构 `PluginsView.vue` 中的刮削卡片交互，将其与统一后台刮削服务（`scraperState` / `startScraperTask`）完全接轨。
  - 引入呼吸态徽标（“同步中”）、微型渐变进度条、实时条目计数（+电影/+演员/+分集）与单行实时日志浮层，支持随时中止任务。
  - 新增“打开同步控制中心”快捷按钮，方便一键呼出全功能同步模态窗（`SyncModal.vue`）。
  - 任务完成后自动触发 `@refresh-movies` 全局数据与统计重载，彻底消除“点击无反应/页面无变化”的断层感。
- **同步结果数据结构增强**：
  - 在 Rust 后端 `SyncResult` 与前端 `ScraperStatus` / `SyncResult` 中统一加入 `new_episodes`（新增分集数）字段，多端数据模型严格对齐。

---

## [v2.4.0] - 2026-09-23

### 🚀 新增 (Added)
- **BoyfriendTV 演员主页直达与精准检索**：
  - 新增 `scrape_bftv_performers.py` 自动化批量爬虫，爬取演员 BFTV 专属档案页 URL 并持久化至 `performers.bftv_url`（支持断点续爬、并发度与延时控制）。
  - 在演员专属档案模态窗 `PerformerDetailModal.vue` 中支持一键直跳主页；若未收录直链则智能降级为演员名精准搜索。
- **BT 资源搜索主标题智能净化 (Clean Title Extraction)**：
  - 新增 `extractMovieCleanTitle` 算法，智能剔除副标题、多余序号与标点修饰符，保留影片自然大小写，全面兼容 em-dash 与 en-dash 破折号。
  - 影片详情卡片 `MovieDetailModal.vue`、分集弹窗 `EpisodeDetailModal.vue` 与分集行 `EpisodeRow.vue` 全面接入 `openBtMovieSearch`，磁力搜索命中准确度显著提升。
- **GitHub Actions 自动编译与 Release 发布流水线**：
  - 交付完整自动化构建工作流 `.github/workflows/release.yml`，打 Tag 即自动拉起 macOS Runner 编译前端与 Rust 核心，生成通用 DMG 安装包。
  - 自动发布 GitHub Release，附带详细 macOS 首次启动安全说明与 `xattr -cr` 隔离属性解除指引。
- **开源门面迁移至 GeavenMax 主页**：
  - 全面将 Git 远程源、克隆地址及 7 种语言矩阵的 Releases 下载链接统一校准至 `https://github.com/GeavenMax/GPDb`。

### ⚡ 优化 (Changed)
- **版本号全生态对齐**：
  - 将 `desktop_client/package.json`、`desktop_client/src-tauri/tauri.conf.json`、`Cargo.toml` 以及底层核心库 `gpdb-core` 统一升级至 `2.4.0`。
  - 在 Tauri 打包配置中显式启用 `["app", "dmg"]` 双产物构建目标。

---

## [v2.0.0] - 2026-09-23

### 🚀 新增 (Added)
- **GPDb Android 移动客户端立项与工程隔离**：
  - 新建独立子工程根目录 `android_client/`，实现与桌面 macOS 端（`desktop_client/`）的物理级数据与构建隔离。
  - 完成 Android 开发架构设计规范 [`android_client/CODING_WIKI.md`](./android_client/CODING_WIKI.md)（含 Scoped Storage 分区存储、SAF 权限、Coil 离线图片降采样等规范）。
  - 交付完备的编译计划项目书 [`android_client/COMPILATION_PLAN.md`](./android_client/COMPILATION_PLAN.md)（涵盖 JDK 17/21、NDK r26b、Rust 交叉编译 Target 与 Gradle 指令集）。
- **资源检索与外部扩展插件系统 (v2.0)**：
  - 将原“BT磁力搜索”与“外部跳转”合体升级为“资源搜索与外部扩展”综合插件。
  - 增加 BoyfriendTV 演员资料与影片作品原名跳转，提取主标题过滤副标题干扰，精准命中条目。
  - 支持 Google 智能搜索与各大 BT 引擎独立细粒度启闭。
- **智能系列影片集与封面拼图组件**：
  - 新增 `SeriesCollageCover.vue`，支持自适应 1~4 张海报拼接矩阵（单图大图、双图对称、三图阶梯、四分格田字格），并带胶卷暗角光晕。
  - 新增 `SeriesModal.vue`，支持从主页系列卡片或收藏列表直接进入系列专题全览。
- **主页全新探索货架**：
  - 新增“经典系列大放送”与“随心探索 · 盲盒发现”摇骰换一批功能。
- **PlayStation 风格成就系统**：
  - 引入 PSN 风格流体毛玻璃奖杯解锁 Toast 提示与成就详情页。
- **开源门面与法律声明**：
  - 编写全新中文 `README.md` 与标准 MIT `LICENSE` 文件。
- **全语种多语言文档矩阵 (Multi-language Documentation)**：
  - 为客户端支持的全部 7 种语言独立编写并发布原生文档：简体中文 (`README.md`)、English (`README_EN.md`)、繁體中文 (`README_ZH_TW.md`)、日本語 (`README_JA.md`)、Deutsch (`README_DE.md`)、Español (`README_ES.md`)、Italiano (`README_IT.md`)。
- **一键创建全新空白数据库与冷启动引导 (Zero-Friction DB Initializer)**：
  - 在 `gpdb-core/src/migrate.rs` 提供原生零依赖的独立建表与迁移脚本，无缝在 `~/Documents/GPDb/GPDb.db` 建立包含 14 张核心表结构、全文检索索引与视图的标准库。
  - 在权限引导模态窗 `PermissionExplainModal.vue` 与“缓存与设置”页顶置高奢渐变“✨ 一键创建全新空白影库（首次使用推荐）”按钮，支持创建中加载态与防重保护，创建后自动引导进入数据同步中心。
- **未缓存图片边看边自动离线下载与本地持久化 (On-Demand Image Caching Engine)**：
  - 在 Tauri 宿主层启用非阻塞异步协议 `register_asynchronous_uri_scheme_protocol("gpdb-img")`，实现多图并发多线程请求。
  - 在 `commands/cache.rs` 实现原子写入与智能路由机制：命中本地缓存时亚毫秒极速直读；未命中缓存时由后台通过伪装 User-Agent 与防盗链 Referer 头按需自动下载，落盘校验后原子重命名至 `image_cache/`，实现“边看边下载，下次全离线”。
- **后台异步自动化刮削与数据流式同步中心 (Asynchronous Scraping Engine & Live Center)**：
  - 在 `commands/sync.rs` 打造全异步生命周期引擎：支持增量极速同步、最新精选快速建库（1,000 部）、全量电影建库与演员档案补齐 4 大模式。
  - 通过 `PYTHONUNBUFFERED=1` 与即时流解析器，将子进程标准输出通过 Tauri Event (`scraper-progress`, `scraper-finished`, `scraper-stopped`) 实时广播到前端。
  - 支持优雅停机控制（SIGINT 安全存盘，零数据库损坏风险）。
  - 全新升级 `SyncModal.vue`：引入毛玻璃流体仪表盘、渐变进度条、动态速度与耗时计算、终端实时滚屏日志与“后台静默运行”快捷切换。
  - 顶栏导航 `Navbar.vue` 增加活动呼吸状态胶囊（`⚡ 同步中 +N`），点击可随时唤回控制台；前端通过 `onScraperDataChange` 驱动片库和主页在抓取过程中实时热更新。


### ⚡ 优化 (Changed)
- **男同成人影视专业管理定位确立**：
  - 全面优化项目展示与功能说明，突出身体特征属性（体型、发色、胡须体毛、身高体重、生理特征等）细粒度检索优势。
- **启动存储权限前置向导与消除被动扫描**：
  - 拔除启动时后台被动遍历用户 Documents/Downloads/Desktop 目录导致的连环权限拦截。
  - 新增 `PermissionExplainModal.vue`，在未定位到数据库时优雅解释离线存储原由，提供“手动单文件选取（零多余权限）”与“授权并自动扫描”两种自主选择。
- **“今日星光 · 标志面孔”演员头像严谨性保障**：
  - 后端候选池扩大至 100 位并严格校验本地磁盘物理图片存在性（`resolve_cache_file`），确保展示演员 100% 拥有精美头像，彻底消除字母占位图与碎图。
- **“一键生成影迷偏好画像”异步非阻塞与全流程进度弹窗**：
  - Rust 后端将 `run_ai_analysis` 升级为异步通道（`spawn_blocking`），彻底释放 UI 主循环，杜绝等待长文网络请求时的界面假死误解。
  - 新增 `AiAnalysisModal.vue` 磨砂玻璃进度悬浮面板，分步展示大模型握手、特征提取与画像生成进度，并带秒级计时器与安抚提示。
- **国际化多语言全量补全**：
  - 补齐所有新增组件与菜单项在 7 种语言（简体中文、繁体中文、English、Italiano、日本語、Español、Deutsch）中的对照翻译。

### 🐛 修复 (Fixed)
- **分集标题搜索覆盖**：
  - Rust 端 `queries/episodes.rs` 与 Python `db_manager.py` 全面加入 `e.title LIKE ? ESCAPE '\\'`，支持十万级分集真实标题全文检索。
- **数据库独立迁移 DDL**：
  - 在 `gpdb-core/src/migrate.rs` 中补齐 `directors` 与 `movie_directors` 建表语句与索引，脱离对 Python 端单向依赖。
- **导演点击交互行为统一**：
  - 详情页中点击导演统一直接呼出导演专属档案弹窗，且卡片内支持一键跳转片库筛选。

---

## [v1.0.0] - 2026-09-20

### 🚀 初始版本
- 建立 GPDb 离线数据库基础框架与 SQLite WAL 模式支持。
- 完成 60,000+ 电影与 100,000+ 分集基础数据存储。
- 基于 Tauri + Rust + Vue 3 的跨平台桌面客户端首次上线。

## [2026-09-23] v2.4 — BFTV 演员直达 + BT 搜索主标题净化

### 新增
- **`scrape_bftv_performers.py`** — 批量爬取演员在 BoyfriendTV 上的个人主页 URL，写入 performers.bftv_url；支持 --db / --limit / --delay / --concurrency / --overwrite；断点续爬

### 优化
- **BFTV 演员直达**：若 performers.bftv_url 已填充，点击按钮直接打开个人主页；按钮文字动态切换（"打开BFTV主页" / "在BFTV搜索演员资料"）
- **BT 搜索主标题净化**：影片/分集 BT 搜索只提交主标题，剥离副标题与序号（Vol/Part/Episode/I-IV 等），保留自然大小写
- extractMovieCleanTitle 补充 em-dash / en-dash 分隔符，不再强制小写

### 数据库迁移
- performers 表新增 bftv_url TEXT 列（客户端下次启动自动迁移，无需手动操作）

### 变更文件
- db_manager.py / migrate.rs / models.rs / sql.rs / types.ts / pluginManager.ts
- MovieDetailModal.vue / EpisodeDetailModal.vue / EpisodeRow.vue / PerformerDetailModal.vue
- scrape_bftv_performers.py（新建）
