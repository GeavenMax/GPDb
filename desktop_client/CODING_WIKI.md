# GPDb macOS 桌面客户端开发规范与技术架构 Wiki (Coding Wiki)

---

## 1. 客户端功能定位与设计目标

GPDb macOS 桌面客户端是基于 **Tauri v2 + Rust + Vue 3** 打造的旗舰级离线影库系统。
其设计目标是：**“极低内存占用、原生级响应速度、深度融合 macOS 人机交互指南 (HIG) 的现代化单机影视元数据库”**。

### 1.1 核心业务功能矩阵

| 功能模块 | 功能特征与技术实现 | macOS 桌面端专属优化 |
| :--- | :--- | :--- |
| **全库检索与多维筛选** | 63,000+ 部影片、100,000+ 分集、6,000+ 演员、1,300+ 厂牌 | Rust 原生 SQLite WAL 高性能并发查询、预编译 SQL 缓存、毫秒级即时聚合响应 |
| **全景洞察与厂牌谱系** | 行业全景编年史、厂牌集团脉络 Wiki、ECharts 6 力导向拓扑关系图 | 顶栏集成切换、平滑力导向力学仿真、派系高亮联动与集团母子层级 Wiki |
| **沉浸式主页 (Home Feed)** | 焦点海报轮播、AI 深度导赏、今日星光、经典系列大放送、随心探索盲盒 | 今日星光仅展示有头像的演员且聚焦中上面部防裁切、经典系列每次刷新采用随机抽取（10组）、全景流光光晕 |
| **离线媒体与海报系统** | `image_cache/` 物理目录直接映射、SQLite `studio_logos` 表 WebP 二进制嵌入 | 自研 `gpdb-img://` 自定义流式协议，零内存拷贝直接流式输出，优先从数据库 BLOB 读取厂牌 Logo 免磁盘冗余 |
| **影视/演员/厂牌/系列模态框** | 3D 拟真翻转实体卡片、全屏手势缩放灯箱 (Lightbox)、曾用艺名 (AKA) 折叠卡片 | 统一层叠上下文管理 (`zIndex`)、全局 Esc 键捕获堆栈 (`claimEscape`)、平滑硬件加速动画 |
| **全局一键双语切换** | 顶部导航栏直达语言切换按钮，全库影片、分集剧情、演员生平与国籍无缝中英切显 | 彻底移除 Grid/List 冗余切换，默认网格，双语状态全局响应式响应 |
| **大模型 AI 翻译引擎** | 集成 Gemini、OpenAI、Claude、DeepSeek 接口，支持批量/单条翻译与试译 | 内置“成人影片专职译者 5 条军规”专业提示词；平滑迁移废弃旧提示词；精准捕获 429 配额耗尽错误 |
| **全平台通用数据备份与迁移** | 用户收藏夹、播放探索统计、自定义标签、打标与私密笔记 | `gpdb_universal_backup` 跨平台统一 JSON 规范，与 Windows / Android / iOS 四端互通 |
| **流光双封面分享卡片** | 正向封面与反向封底双海报并排排版、Telegram 官方频道点阵二维码 | Canvas 2D Retina 2x 高清渲染、双离屏磨砂打码防窥、27×27 矢量点阵二维码直达官方频道 |
| **刮削器集成与自动入库** | PBC 与 SmutJunkies 演员刮削引擎 | 异步子进程调度、实时日志管道推流捕获、执行完成自动触发 `--apply` 无损增量入库 |
| **版本检测与在线更新** | GitHub Releases API 检索、版本特性展示、安装包自动下载 | 异步检测最新版本，支持自动下载 DMG 安装包并挂载升级 |

---

## 2. macOS 桌面端技术栈全景

```
[ UI 渲染层 (Frontend - WebView) ]
  Vue 3.5+ (Composition API, <script setup>) + TypeScript 6.0+
  构建工具: Vite 8+ + @tailwindcss/vite 4.3+
  拓扑可视化: ECharts 6.1+ (力导向关系网络图谱)
  图标体系: Lucide Icons (@lucide/vue / lucide-vue-next)
  交互机制: 模态框统一栈管理器、Esc 键优先级分配、全景高斯模糊滤镜

[ 桥接与命令层 (Tauri Bridge Layer) ]
  Tauri v2.11+ (基于 macOS WebKit / Wry 宿主)
  自定义安全流式协议: gpdb-img:// (拦截解析本地 image_cache/ 与 studio_logos 离线资源)
  Tauri IPC Commands: 数据库挂载、文件操作、系统剪贴板、配置备份导入导出、Python 刮削调度、AI 翻译配置

[ 后端核心与数据库引擎 (Rust Core Layer) ]
  gpdb-core (独立 Workspace Crate，零 Tauri 耦合，便于跨平台复用与单元测试)
  数据驱动: rusqlite 0.31 (内嵌 SQLite 3.45+, 启用 WAL 模式, 只读并发连接池)
  系统底层: Cocoa / Objective-C 运行时桥接 (安全剪贴板图像复制，消除内存死锁)
```

---

## 3. 核心子系统与关键设计规范

### 3.1 内存安全与 macOS Cocoa 运行时规范

在 Tauri 命令层与 macOS 原生系统交互（如系统剪贴板图像复制）时，必须严格遵守 Objective-C 引用计数与 ARC 生命周期规范。

#### 严禁使用的反模式 (Double-Free & 主线程崩溃)
- **绝对严禁对 Autoreleased 对象显式调用 `release`**：
  在 Objective-C 中，通过便捷工厂方法（如 `+[NSData dataWithBytes:length:]` 与 `+[NSString stringWithUTF8String:]`）创建的对象已经被加入当期的自动释放池（AutoreleasePool）。
  如果在 Rust FFI 中手动对其调用 `msg_send![ns_data, release]`，会导致在自动释放池排空时触发 **Double-Free（双重释放）**，瞬间引发 macOS 主线程死锁或 EXC_BAD_ACCESS 闪退崩溃！

#### 正确的规范实现 (`src-tauri/src/commands/system.rs`)
```rust
// 仅对通过 alloc + init 创建的持有所有权对象调用 release，便捷工厂对象绝不手动 release
let alloc: id = msg_send![class!(NSPasteboardItem), alloc];
let item: id = msg_send![alloc, init];
let () = msg_send![item, setData:ns_data forType:ns_type];
let () = msg_send![pasteboard, writeObjects:items_array];
let () = msg_send![item, release]; // 仅 release 自己 alloc 的 item
```

### 3.2 自定义图片协议 `gpdb-img://` 与离线 WebP 数据库嵌入规范

为了突破浏览器 WebView 的本地文件跨域限制，并实现 100% 离线免外部依赖加载：
- 在 `src-tauri/src/lib.rs` 中注册 Tauri Custom Protocol `gpdb-img`；
- 前端传递相对路径（如 `gpdb-img://localhost/?url=...` 或 `gpdb-img://image_cache/Covers/123.jpg`）；
- **优先命中数据库嵌入式 Logo (`studio_logos` 表)**：
  - 当请求涉及片商 Logo/Banner（如包含 `logo/`、`logos/`、`banners/`）时，Rust 协议处理器优先通过 `load_logo_from_db` 查询主库 `studio_logos` 表（`logo_webp` / `banner_webp` 二进制 BLOB）；
  - 命中后直接返回 `image/webp` 响应体，完全免除磁盘文件落地和外链请求；
- **磁盘物理目录直接流式输出**：
  - 未命中数据库 BLOB 时，提取安全相对路径，与当前挂载的数据库物理目录及 `image_cache/` 拼接；
  - 采用直接文件流读取并输出对应的 MIME 类型（`image/jpeg`, `image/webp`, `image/png`, `image/svg+xml` 等），无中间 Base64 编解码开销，显著降低海量海报网格的内存占用。

### 3.3 全景洞察与厂牌谱系拓扑图架构

`StudioGenealogyView.vue` 与 `IndustryPanoramaView.vue` 构建了全景洞察系统：
- **ECharts 6 力导向图**：
  - 节点根据集团、厂牌层级与作品数动态计算力导向排斥力与连接距离；
  - 交互支持节点拖拽、派系筛选、集团高亮联动；
  - 适配暗黑与明亮 macOS 主题，动态重绘画布尺寸；
- **响应式 Wiki 侧边栏**：
  - 选中厂牌或集团节点后，右侧平滑展开所属母公司历史、总部地址、成立年份、代表作品列表及双语背景介绍；
  - 双击或点击动作支持向外派发事件，直达该厂牌名下作品库或打开 `StudioDetailModal`。

### 3.4 大模型 AI 翻译引擎与译者 5 项军规规范

在 `desktop_client/src/services/pluginManager.ts` 中管理翻译插件配置：
- **专职译者 5 大军规默认提示词 (`DEFAULT_TRANSLATION_PROMPT`)**：
  1. **忠于原文**：逐句对应，原文说了什么就译什么，不增不减、不概括、不改写、不调整叙述顺序。
  2. **非常直白**：用词直接、露骨、不回避，按原文露骨程度如实翻译，不要替换成委婉语、暗示或模糊表述，绝不弱化、淡化、"净化"或美化原文内容。
  3. **不要意译或润色**：宁可保持原文的句式和语序，也不要为了文采而改变原意。
  4. **人名一律保留原文拉丁字母写法**：绝对不要音译成汉字（例：Count Vladimir 保持 "Count Vladimir"，不要写成 "弗拉基米尔伯爵"）。
  5. **保持档案记录语气**：只陈述原文内容，不添加主观评价。涉及成人内容、身体部位、性取向时照实翻译，使用目标语言成人语境中通用的直接说法，不要因内容露骨而删减、跳过或含糊处理。
- **配置平滑迁移**：系统启动加载本地 `localStorage` 时，自动比对并无损将旧版温和型/润色型默认提示词平滑升级为最新 5 项军规提示词。
- **错误捕获与提示**：对模型提供商（如 Google Gemini）的 HTTP 429 `RESOURCE_EXHAUSTED`（每日免费配额超限）进行精确错误提示，区分速率超限与凭据异常。

### 3.5 数据库查询引擎与多维聚合 (`gpdb-core`)

- **独立 Workspace 组织**：`gpdb-core` 作为独立的 Rust Crate 置于 `src-tauri/gpdb-core`，不直接依赖 Tauri 运行时，保证纯粹的数据处理与极速编译。
- **WAL 并发模式**：以 `PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL;` 打开连接，读写分离互不阻塞。
- **随机抽样推荐**：主页「经典系列大放送」采用 `ORDER BY RANDOM() LIMIT 10`，每次刷新均生成全新推荐组合。
- **星光面孔过滤与居中渲染**：主页「今日星光 · 标志面孔」过滤无头像占位项，仅展示包含有效头像的演员；照片采用聚焦中上部对齐（`object-position: center 20%`），避免由于比例差异裁切头部。

### 3.6 跨平台通用配置备份与迁移规范 (`gpdb_universal_backup`)

- **规范标识**: `"format": "gpdb_universal_backup", "version": 1`
- **支持范围**:
  - 用户收藏夹（长片、分集、演员、导演、系列、厂牌全维度支持）；
  - 探索专注分析数据（累计专注时长、播放量统计、活跃天数等）；
  - 自定义标签与私密评星笔记；
  - 界面排版与视觉偏好配置。
- **四端通用**: 导出的 `.json` 配置文件完全跨平台，支持在 macOS、Windows、Android 和 iOS 客户端之间直接互相导入。

### 3.7 分享卡片流光引擎与 Telegram 官方二维码

- **双封面并排排布**: 分享卡片全面支持正向海报封面与反面封底（如有）并排展示，左右标注「封面」「封底」半透明胶囊。
- **Retina 2x 离屏抗锯齿**: 采用双离屏 Canvas 技术，在 HiDPI 屏幕下始终保持高清晰度导出。
- **双离屏高保真磨砂模糊**: 解决 macOS WebKit 下 Canvas filter 硬件加速可能失效的偶发缺陷，确保打码隐私保护坚如磐石。
- **官方二维码点阵**: 卡片底角集成 27×27 标准高保真点阵二维码，直连官方频道 `https://t.me/gpdbnews`。

---

## 4. 目录组织架构 (Source Tree)

```
desktop_client/
├── package.json                         # 前端依赖配置 (Vue 3, Vite, Tailwind CSS v4, ECharts 6)
├── vite.config.ts                       # Vite 构建与开发服务器配置
├── src/
│   ├── main.ts                          # 前端入口，挂载 App.vue
│   ├── App.vue                          # 顶层布局框架、侧边栏集成、全局弹窗挂载
│   ├── api.ts                           # Tauri invoke 远程过程调用包装层
│   ├── types.ts                         # 全量 TypeScript 元数据与业务数据实体接口
│   ├── i18n/                            # 7 语言系统国际化字典
│   ├── views/
│   │   ├── HomeView.vue                 # 沉浸式主页 (轮播、星光、随机大放送、探索)
│   │   ├── StudioGenealogyView.vue      # 厂牌谱系拓扑图 (ECharts 6) 与历史 Wiki
│   │   ├── IndustryPanoramaView.vue     # 行业全景编年史 (时代分期、代表作)
│   │   ├── AnalyticsView.vue            # 用户本地专注与偏好洞察统计
│   │   └── PluginsView.vue              # 插件管理与 AI 翻译引擎配置
│   ├── components/
│   │   ├── Navbar.vue                   # 顶部全局导航栏 (全局搜索、双语切换、同步状态)
│   │   ├── MovieDetailModal.vue         # 影片详情模态框 (3D翻转、海报画廊、演职员表)
│   │   ├── PerformerDetailModal.vue     # 演员档案模态框 (AKA艺名折叠、中英双语、外链)
│   │   ├── StudioDetailModal.vue        # 厂牌详情模态框 (归属集团、名下作品)
│   │   ├── ShareCardModal.vue           # 分享卡片模态框 (双封面、流光背景、TG二维码)
│   │   ├── AppUpdateModal.vue           # 版本更新检测弹窗 (更新日志展示、一键下载)
│   │   └── Sidebar.vue                  # 质感侧边导航栏
│   └── services/
│       ├── appUpdater.ts                # GitHub Releases API 检索与在线升级
│       ├── pluginManager.ts             # 翻译引擎提示词与插件调度
│       ├── privacy.ts                   # 全局防窥模式与快捷键
│       └── scraper.ts                   # 增量爬虫数据管道管理
└── src-tauri/
    ├── Cargo.toml                       # Rust 依赖声明与 Workspace 定义
    ├── tauri.conf.json                  # Tauri 窗口属性、安全策略与打包配置
    ├── src/
    │   ├── main.rs                      # Tauri 桌面端二进制主入口
    │   ├── lib.rs                       # 插件加载、gpdb-img 协议注册、Tauri 命令集挂载
    │   ├── db.rs                        # SQLite 数据库寻址与连接
    │   └── commands/
    │       ├── cache.rs                 # 本地缓存目录监控、gpdb-img 协议拦截与 DB Logo 提取
    │       ├── system.rs                # macOS 系统级调用 (安全剪贴板、Finder、废纸篓)
    │       ├── translate.rs             # AI 翻译引擎命令层
    │       └── sync.rs                  # 通用配置与数据备份导入导出
    └── gpdb-core/                       # 核心 SQL 查询引擎 Workspace Crate
        ├── Cargo.toml
        └── src/
            ├── lib.rs                   # 数据库连接池与初始化
            └── queries/                 # 影片、演员、分集、系列、厂牌高性能查询
```

---

## 5. 构建、发布与验证指南

### 5.1 本地开发与调试指令

```bash
# 切换至桌面端工程目录
cd desktop_client

# 启动本地热重载开发环境 (Vite + Tauri)
npm run tauri dev
```

### 5.2 生产环境打包指令

```bash
# 执行前端静态类型检查与打包
npm run build

# 编译并生成 macOS 原生可执行文件与 .dmg 安装包
npm run tauri build
```

### 5.3 产物输出与规范

- 安装包文件：`desktop_client/src-tauri/target/release/bundle/dmg/GPDb_*.dmg`
- 原生应用包：`desktop_client/src-tauri/target/release/bundle/macos/GPDb.app`
- 产物完全遵循 macOS 沙盒与人机交互规范，内存与 CPU 负载表现平稳。

---

## 6. 客户端界面国际化与多语言规范 (i18n Spec)

项目具备高规格的 7 语言本地化支持体系与二元解耦架构，严禁任何硬编码中文字符。
详细规则、语言包结构、智能回退链与开发准则请参阅专用文档：
👉 **[UI_I18N_SPEC.md](../docs/i18n/UI_I18N_SPEC.md)**

### 核心开发准则速记：
1. **严格 1:1 键位完全对齐**：7 种支持语言（`zh-CN`, `zh-TW`, `en`, `ja`, `it`, `es`, `de`）所有字典键必须 100% 镜像对称（当前 1,096 键）。每次增改均须通过自动化键位校验检测。
2. **严禁在模板中硬编码中文兜底参数**：统一使用 `t('key')` 或 `t('key', { param })`，杜绝 `t('key', '中文')` 造成的英文模式中文污染。
3. **二元解耦**：系统菜单/控制层文本严格跟随 `currentLocale`；数据库实体（影视标题、简介、演职员生平）由 `bilingual.ts` 结合呈现模式统一调度。
4. **状态双向联动同步**：设置中切换菜单语言自动同步重置内容语言为 `auto`；资料库顶部切换中文/原文自动联动同步菜单语言，杜绝状态脱节。
5. **编译与质量闸口**：提交前必须执行 `cd desktop_client && npm run build`（包含 `vue-tsc -b` 全量类型检查）。

---

## 7. 官方频道发布与 Telegram 推送自动化规范 (Release & Broadcast)

向 GPDb 官方 Telegram 频道（[@gpdbnews](https://t.me/gpdbnews)）推送版本日志与统计通报必须严格遵守自动化流水线规范：

- **自动化脚本集中目录**：统一收拢于 `scripts/telegram_push/`（内附说明文档 `README.md` 与密钥配置 `.env`）。
- **两阶段安全发布流程**：
  1. **第一阶段：只读预览验证**：运行 `python3 scripts/telegram_push/preview_v2170_post.py`，核验消息排版、下划线 Markdown 转义完整性、字符数是否在 4,096 上限内，以及置顶导览草稿；
  2. **第二阶段：受控执行推送**：获得人工确认后，方可运行带 `--confirm` 参数的推送脚本（如 `python3 scripts/telegram_push/push_v2170.py --confirm`）。
- **发布内容约束**：
  - 更新日志与公告中**暂时排除 iOS 端信息**，聚焦 Windows / macOS / Android 三大客户端；
  - 必须同步调用 `edit_msg(17, ...)` 维护频道第 17 号置顶导览消息（保持“一句话速览”与历史版本同步）。

---

## 8. 片商超清 Logo & 封套徽章资产系统 (Studio Logos Engine)

桌面客户端深度集成了全离线的片商 Logo 与 Banner 体系，资源管理位于 `studio_logos/`：
- **资源寻址协议**：`gpdb-img://` 协议自动定位 `image_cache/Logos/*_logo.webp` 与 `image_cache/Logos/*_banner.webp`。
- **全量 WebP 规范**：全量 2,492 枚厂牌 Logo 与 694 枚横幅已 100% 升级为高保真 `.webp` 格式（保留透明通道与多帧动画），显著降低客户端解压与内存开销。
- **自适应居中正方形规范**：实体封套截取的徽标均经过 `RapidOCR` 高精定位与色调拓展为 512×512 正方形，杜绝客户端头像框内的空旷留白。
- **100% 全覆盖现状**：全库 2,492 家有效独立制片厂牌已实现 100% 专属 Logo 覆盖，全库 97.99% 的影视作品拥有所属厂牌 Logo。
- 详细架构与最新指标请参阅 [`studio_logos/README.md`](../studio_logos/README.md) 与 [`STUDIO_LOGO_PROGRESS.md`](../STUDIO_LOGO_PROGRESS.md)。

---

## 9. Git 管理与自动化发版规范 (Git Tasks & Release Workflow)

项目已建立专用的 Git 管理与发版自动化工作区，脚本与规范收拢于 `git_tasks/`：
- **工程主路径**：`/Users/joel/iCloud Drive (Archive)/Documents/antigravity/GPDb 开发`。
- **发版前自动预检**：在根目录下执行 `./git_tasks/pre_release_check.sh`，自动核验：
  1. 当前工作路径是否准确；
  2. 公开文档中是否存在开发者本地绝对路径泄露；
  3. 全语言文档是否严格排除 iOS 相关描述；
  4. 三端核心工程版本号是否严格一致；
  5. 检查并确认当前签名版 Android APK 就绪。
- **发版全流程**：详细指南与 CI/CD 产物规范请参阅 [`git_tasks/README.md`](../git_tasks/README.md)。
