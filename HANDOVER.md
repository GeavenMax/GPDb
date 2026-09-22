# GEVI 离线影视库 — 项目交接说明

> 最后更新：2026-09-22。写给接手的人或模型：先读这一份，再动代码。
> 文中所有数字都是当时实测值，不是估计值。
> 最初一版是在公司探针**跑到一半**时写的（§2 的 121,117 / 5,418 就是 12:44 的快照），
> 探针 13:09 跑完后已按最终状态重测一遍；长任务在跑的时候不要照当时的数字写文档。

---

## 0. 这是什么

把 `gayeroticvideoindex.com` 的影片、演员、分集数据刮下来，存进本地 SQLite，
配一个**完全离线**可用的桌面客户端（GPDb）。

- 项目根：`/Users/joel/iCloud Drive (Archive)/Documents/antigravity/游戏库管理App/GEVI_Offline_Database`
- 主库：`gevi.db`（约 300 MB）
- 分支：`gevi-plus`。更早一轮成果拆成 5 个提交 `f540530` → `6eb0d65`（明细见 §4.2）；
  本轮（导演库）的提交见 §4.1
- 后端：Python 3 **纯标准库**，`python3 server.py` 起在 **8787** 端口
- 桌面端：Tauri v2 + Vue 3，Rust 侧核心逻辑在 `desktop_client/src-tauri/gpdb-core/`

---

## 1. 铁律（违反会出事，先看完再动手）

1. **零第三方依赖。** 后端只用 macOS 系统 Python 3 的标准库。不许 `pip install`，
   不许 `import requests` / `anthropic`。HTTP 用 `urllib.request`，并发用
   `concurrent.futures`。桌面端的依赖另说，Rust/Vue 那边正常用。
2. **改完 `server.py` / `db_manager.py` 必须重启 8787 服务**，否则旧进程静默返回旧接口，
   你会以为改动没生效。
3. **绝不用 `--forget-voids`。** 那会把「确认抓不到」的账本清空，导致重新去抓几万条
   永远抓不到的东西。
4. **备份要用 `sqlite3 .backup`，不能用 `cp`。** WAL 里有没 checkpoint 的数据，
   `cp` 出来的库是坏的。（`scraper_v2.py` 里的 `backup_database()` 已经做对了。）
5. **测试一律用 `/tmp` 里的库副本，绝不动真库。**
6. **翻译 API Key 绝不下发前端。** Key 存在 `translate_config.json`（已 gitignore），
   或环境变量 `GEVI_LLM_API_KEY`。`/api/translate/*` 只许回 `has_key` 和打码的 `key_hint`。
7. **这些名字不许改**：`gevi.db`、`GEVI_DB`、`sync_gevi.py`、`gevi_cli.py`、`GEVI_LLM_*`。
   Python 侧和桌面端共用，改名直接连不上。
8. **站点按 IP 限吞吐，约 1.4–1.7 req/s 是它的舒适区。** 实测跑 8 req/s 也能过，
   但**并发拉到 24 并持续几小时，站点会开始拒绝连接**（`ssl.SSLEOFError:
   UNEXPECTED_EOF_WHILE_READING`）——而且**一行数据都不会写**。停掉之后封锁立刻解除。
   所以：**同时只跑一个重活**，别开两个终端一起刮。
9. **提交信息结尾加** `Co-Authored-By: Claude Code <noreply@anthropic.com>`。

---

## 2. 当前数据规模（2026-09-22 实测）

| 表 | 行数 |
|---|---|
| movies | 63,238 |
| performers | 108,231 |
| episodes | **134,489** |
| └ 独立分集（`movie_id IS NULL`） | **102,364** |
| └ 隶属影片的分集 | 32,125 |
| movie_performers | 450,697 |
| episode_performers | 272,462 |
| directors / movie_directors | 3,077 / 37,502（导演库入口，见 §4.1） |
| 片商（distinct `movies.studio_id`） | 2,411（最大 id 8,175 = **站点公司 id 的上限**，见 §4.4） |

**翻译进度（几乎没做）**：`movies.description_zh` 2,367 / 63,238；
`movies.title_zh` **0**；`episodes.description_zh` 77。

**账本**：`scrape_progress` — company 12,000 行（全部 200，即 id 1..12,000 全覆盖）；
movie 76,000 行（53,944 完成）；performer 60,701 行（60,692 完成）。
`scrape_voids` — movie 82,152 / performer 94,885。

---

## 3. 关键设计（改动前必读）

### 3.1 分集数据的两条来源，互不干扰

- **隶属影片的分集**：从影片页 `video/{id}` 的场景列表来，`movie_id` 有值。
- **独立分集**：站点上「不属于任何影片」的场景，**影片页完全列不到它们**，
  `/newe` 又没有分页（只能拿到最新 60 条）。唯一入口是公司页的
  DataTables 接口 **`/coep?CompanyID=N&draw=1&start=0&length=100&order[0][column]=1&order[0][dir]=desc`**，
  返回干净 JSON：

  | 列 | 内容 |
  |---|---|
  | `data[][1]` | 日期 `2013-12-06` |
  | `data[][2]` | `<a href='episode/101637'>真实标题</a>` |
  | `data[][3]` | 演员 `<a href='performer/124904'>名字</a>` 列表 |
  | `data[][4]` | `<img src='images/Episodes/episode101637.jpg'>`（**低清**） |
  | `data[][5]` | 简介正文 |
  | `recordsTotal` | 该公司分集总数（精确值） |

  服务端把 `length` 钳到 100，所以请求数 = `ceil(recordsTotal/100)`。
  命令：`python3 scraper_v2.py --mode episode-sync --apply`。
  **工作单元是「公司」，不是「分集 id」。** 扫 id 空间要 ~40 小时，按公司只要几十分钟。

- **判定「隶属 / 独立」的依据**：分集页 `episode/{id}` 里若含 `href='video/{n}'` 回链，
  就隶属影片 n；没有就是独立分集。

### 3.2 ★ 隶属关系绝不被覆盖（用户最在意的一条）

`db_manager.save_standalone_episodes()` 的整个存在意义就在这：**它从不写 `movie_id`。**
INSERT 时留 NULL，`ON CONFLICT` 的 UPDATE 里**根本没有 `movie_id` 这个字段**。
所以一条已经属于某部影片的分集，无论公司表里怎么列，隶属关系都不会被动。
同名的 `_write_episodes(movie_id, ...)` 会把同一个 `movie_id` 盖到每一行——
那个是给影片自己的场景列表用的，**不要拿它来写 coep 的结果**。

### 3.3 高清图 = 低清图 URL 后面加 `b`

`images/Episodes/episodeNNN.jpg`（约 7 KB，影片页网格用）
→ `images/Episodes/episodeNNNb.jpg`（约 60 KB，分集页打开后的图）。
实测整个 id 段 24/24 都有高清版，体积 6.0–12.6×。**不需要抓页面就能推导出来。**

`cache_images.hd_url_for()` 做这个改写；它只认「干净的」分集缩略图 URL，
已经是 `b.jpg` 的不再匹配，所以**幂等**。
缓存 key 用的是 URL 里的文件名，所以 `episodeNb.jpg` 是新文件、会重新下载，
旧的 `episodeN.jpg` 变成孤儿（用 `--prune-orphans` 清）。

### 3.4 void / progress 账本按 `item_type` 隔离

`scrape_voids` 主键 `(item_type, item_id, field)`，`scrape_progress` 主键
`(item_type, item_id)`。分集模式用的是**自己的命名空间**（company），
和 movie / performer 完全隔开——否则公司 id 和影片 id 同为整数会撞车，
污染「这部影片没有分集」的判定。这是本项目最容易伤数据的地方。

### 3.5 列是按 index 读的

`sql.rs` 的 `map_movie_row` / `map_episode_row`、以及 `EPISODE_SQL` 都是按**下标**取列。
**新列一律加在 SELECT 末尾**——从中间插一列不会编译报错，会把后面每个字段
静默错位到别的结构体字段上。本次改的是下标 8/9 的**表达式**（`COALESCE`），
下标数量没变，所以安全。

加列要**同时**改两处：`db_manager.MIGRATIONS["<表>"]` 和 `schema.sql` 的 CREATE TABLE。
`apply_migrations()` 是幂等的（`PRAGMA table_info` 查缺列再 `ALTER TABLE ADD COLUMN`）。

### 3.6 ★ 导演身份只认 junction 表，计数一律现算

两条都是踩过坑写下的，改任何导演相关查询之前先看一眼：

- **`movies.director_name` 不是导演身份。** 那是分词之前遗留的粘连字符串
  （33,757 行非空，只有 782 行带 `" / "`，其余是人名首尾相接的一整块）。
  一律走 `movie_directors` / `directors`。
  实测差距不是舍入误差：Chris Ward 走 junction 是 **274** 部，走那列只有 **89**。
- **`directors.works_count` 是过期列，不要读**（原因写在 `schema.sql` §11）。
  一律 `count(md.movie_id)` 现算。

两处必须成对改：Rust 的 `queries/favorites.rs` 与 Python 的 `db_manager.get_favorites`
各有一份同名逻辑，只改一边不会有编译错误，只会让两个前端显示不同的数字。

---

## 4. 已完成的工作

### 4.1 本轮（2026-09-22）：导演库

提交：`67676c3`（代码 + 测试 + `schema.sql` 注释订正），紧随其后 `5045452` 是这份文档。

用户在 `资源检索` 里要一个**导演库**：收纳全部导演、按名字检索、在导演页里检索他的作品、
显示合作片商（点击跳片商库）、一键收藏。此前导演是**唯一一个已落库却没有任何入口**的数据——
只在影片详情的导演 chip 和「我的收藏」的导演分组里露头。

**成果**

- `资源检索` 新增第六个入口「导演库」（`Megaphone` 图标，排在片商库与分集库之间），
  桌面端与 HTTP 后端**两条路都通**。
- 两个新查询，Rust 与 Python 各一份、语义逐字对齐（差别只有语言）：
  `get_director_library(query, sort_by, page, page_size)` 与
  `get_director_works(director_name)`；HTTP 侧是
  `GET /api/director-library` 与 `GET /api/directors/<name>/works`。
- **修了一个明明错了很久的计数**：收藏的导演作品数一直是错的，因为两边都在拿
  `movies.director_name` 精确等值去数（见 §3.6）。库里 5 条导演收藏的真实值：

  | 收藏的导演 | 修前 | 修后（正确） |
  |---|---|---|
  | Chi Chi LaRue | 497 | **614** |
  | Chris Ward | 89 | **274** |
  | Joe Gage | 155 | 160 |
  | Tom Moore | 63 | 64 |
  | Liam Cole | 19 | 31 |

- `cargo test --workspace`：**65 passed / 0 failed**，`--nocapture` 下 0 条 `skipping:`。
  `npx vue-tsc --noEmit` 干净。
- 接口实测与控制 SQL 对拍：`COUNT(*) FROM directors` = 3,077；William Higgins 817 部 /
  6 家片商，Jake Cruise 752/4，Chi Chi LaRue 614/36；Chris Ward 274 部（每条 17 个键）；
  `%` 与 `_` 按字面量搜索返回 0（不是通配）；不存在的导演名返回 0 条而不是报错。

**设计上定死的几件事（都有理由，别顺手改）**

- **作品一次全量返回，不再分页。** 单人最多 817 部（William Higgins），
  和片商详情同一个做法；片商 chip 与年份筛选因此可以纯前端算，**没有为它们新增任何 SQL**。
- **按名字而不是 id 检索作品**（`get_director_works(name)`）：收藏是按名字存的，
  「我的收藏」里点开导演时手上只有名字，走名字就不必先反查 id。
- **列表用 `LEFT JOIN`**：107 位导演一条链接都没有，inner join 会让他们在导演库里凭空消失。
- **排序带 `d.id ASC` 兜底**：1,304 位导演恰好只有一部作品，还有 20 对名字只差大小写
  （`Chi Chi LaRue` / `Chi Chi Larue`），两种排序都会大量并列。没有兜底时 SQLite 可能
  对不同的 LIMIT/OFFSET 给出不同的并列顺序——同一位导演会出现在两页上，另一位一次也不出现。
  **诚实记录：把它删掉不会让任何测试变红**（试过了，SQLite 在这个查询计划下恰好按 id 顺序
  返回并列行），所以这条是「对任何查询计划成立」的保证，不是「对今天这个计划成立」——
  代码注释里也是这么写的，没有一个测试声称能抓住它。
- Python 侧的数字探针（`d.site_id = ?`）用 `probe.isascii() and probe.isdigit()` 把 `int()`
  挡住：Python 的 `int()` 接受 `"1_0"`（=10）和全角 `"１２３"`，而 Rust 的 `i64::from_str`
  两个都拒。不加这个判断，同一个搜索词在桌面端和后端会给出不同结果。
- **没有碰 `MOVIE_COLUMNS`**：导演作品查询复用它，新列只出现在列表查询自己的 SELECT 里，
  所以 §3.5 的按下标读列这个风险本轮没有被引入。

**改动的文件**

| 文件 | 改了什么 |
|---|---|
| `gpdb-core/src/queries/directors.rs` | 新增（模板 `queries/studios.rs`），含上面两条查询 |
| `gpdb-core/src/models.rs` | `DirectorSummary` / `DirectorLibrary` / `DirectorWorks` |
| `gpdb-core/src/queries/favorites.rs` | 导演计数改走 junction（见上表） |
| `src-tauri/src/commands/{library,detail}.rs`、`lib.rs` | 两个 Tauri 命令 + 注册 |
| `db_manager.py` | `list_directors()` / `get_director_works()`；`get_favorites` 导演分支改走 junction |
| `server.py` | 两个路由 + 两个 handler（改完已重启 8787） |
| `tests/parity.rs` | 新增导演列表/搜索/作品/收藏计数四条控制 SQL 对照 |
| `tests/category_parity.rs` | 新增跨实现对照（驱动真 `db_manager.list_directors`） |
| `src/{types,api}.ts` | `DirectorSummary` 等类型；`getDirectorLibrary` / `getDirectorWorks`，Tauri 与 HTTP 分支都写 |
| `src/components/Sidebar.vue` | `NAV_ITEMS` 加导演库（`loadOrder()` 自动追加新 id，无需迁移） |
| `src/components/DirectorDetailModal.vue` | 新增。作品区纯前端：关键字（`title` + `title_zh`）、片商 chip 行、年份下拉、年份/片名排序 |
| `src/App.vue` | 新 tab 的 17 处接线（见下方「踩过的坑」） |
| `schema.sql` | §9 的过期注释订正；§11 补 `works_count` 不要读的说明 |

**验证过的事**

- 4 个变异全部被抓到：去掉 `ESCAPE '\'`、作品查询改成 `m.director_name = ?`、
  排序去掉 `COLLATE NOCASE`、收藏计数改回 legacy 列——各自对应的测试真的红，
  改回来后恢复绿。（唯一没被抓到的是 `d.id ASC` 兜底，已如实记录在上面。）
- 跨实现对照测试会驱动**真的 Python**（`-c` DRIVER 程序），所以「Rust 改了、Python 没改」
  这种单边改动会红。注意它没有共享快照，是跨进程比两份各自读的结果（别的进程在写就会假红，
  重跑一遍再下结论）。
- 本轮**没有动任何数据**：不跑 `refresh_director_counts`、不重抓、不改现有行。

### 4.2 上一轮（2026-09-22）：分集高清图 + 独立分集抓取

对应计划：`/Users/joel/.claude/plans/silly-bouncing-haven.md`
（**注意**：Claude 的 plan 文件路径会被后续会话覆盖，尽早另存）。

**成果**

- 分集 **32,125 → 134,489**；新增 **102,364 条独立分集**，全部 `movie_id IS NULL`。
  其中 88,992 条来自 2,411 家已知片商，另 **13,372 条来自 66 家「有分集、但库里没有
  它的影片」的公司**——是公司 id 探针找出来的（见下条）。
- 公司 id 探针 1..12,000 已**全部扫完**（12:26–13:09，`scrape_progress` 里 company
  12,000 行全 200）。**这一步是一次性补齐，已经结束**：站点公司 id 上限就是 8,175
  （证据见 §4.4），探到 12,000 纯属余量，再跑不会有新目标。
- 已有 32,125 条的 `movie_id` **一条没变**（拿开工前快照逐行对拍，差异 0）。
- 缩略图：**119,246 条高清（`b.jpg`），剩余低清 0 条**（剩下 15,243 条站点本身就没图，
  已用探针确认低清高清都 404）。
- 全公司 2,411 家跑完，**零失败**，8.0 req/s 无节流；探针 12,000 条同样零失败。
- `scrape_voids` 未被污染（数字与开工前一致）。
- `cargo test --workspace`：**39 passed / 0 failed**，`--nocapture` 下 0 条 `skipping:`
  （对拍 30 + 翻译配置 3 + 翻译协议 6）。

**提交（5 个，顺序即依赖顺序）**

| 提交 | 内容 |
|---|---|
| `f540530` | cache_images 复用 SSL 上下文，404 不再当异常重试 |
| `04e06c3` | 修备份被自己的轮转逻辑删掉 |
| `dc8e6c2` | 分集抓取按片商走 coep：+8.8 万条独立分集，隶属关系不动 |
| `e9e735a` | 分集缩略图改存高清版；影片页不再合成占位标题 |
| `6eb0d65` | 桌面端：让独立分集在分集库里显示出来 |

`cache_images.py` 里混着两批无关改动（更早的提速 + 本轮的高清），已用
`git apply --cached --reverse` 按 hunk 拆到两个提交里。

**改动的文件**

| 文件 | 改了什么 |
|---|---|
| `scraper_v2.py` | 新增 `--mode episode-sync`、`--sweep-companies`；`parse_coep_rows` / `coep_url`；`_process_company` / `_abort_company`（按公司全有或全无）；company 的 progress/void 命名空间；**修了备份自杀 bug**（见 §7.1） |
| `db_manager.py` | 新增 `save_standalone_episodes()`（永不写 `movie_id`）；`MIGRATIONS["episodes"]` 加 `release_date`/`studio_id`/`studio_name`；`get_studio_episodes` 与 `list_episodes` 改 `LEFT JOIN` + `COALESCE` |
| `schema.sql` | episodes 表加同三列 |
| `cache_images.py` | `hd_url_for()` / `probe_url()` / `run_hd_upgrade()` / `run_revert_missing_hd()` / `run_prune_orphans()`；`--hd-upgrade` `--no-probe` `--revert-missing-hd` `--prune-orphans` `--batch` `--apply` |
| `batch_scraper.py` | 停止合成 `f"Episode #{id}"` 占位标题（改 `""`），否则 UPSERT 的 COALESCE 会把真标题打回占位符；缩略图改写为高清 |
| `models.rs` | `Episode.movie_id: i64` → `Option<i64>`（否则 `r.get(1)?` 遇 NULL 报 `InvalidType`，被 `filter_map(ok)` 静默吞掉） |
| `sql.rs` / `queries/episodes.rs` | 下标 8/9 改 `COALESCE(m.studio_name, e.studio_name)` 与 `COALESCE(m.release_year, CAST(substr(e.release_date,1,4) AS INTEGER))`；片商筛选同样 COALESCE |
| `tests/parity.rs` | 新增 `standalone_episodes_survive_the_row_mapper`（内存库，独立分集不被丢） |
| `sync_gevi.py` | docstring 删掉从未实现的 `/newe` |

**验证过的事**

- 已有分集隶属关系 0 变动；新增行 100% 是独立分集。
- `scrape_voids` 无新增 movie 行。
- 接口实测：独立分集返回 `movie_id: null`、真实标题（如 `"Marcus"`）、
  `studio_name` 走兜底、`release_year` 从 `release_date` 推、`episode_ordinal/count = 0/0`
  （前端 `episodeOrdinalLabel` 对 0 判假 → 不显示位置标签，降级正确）。
- 备份自杀 bug 已写测试 + 变异验证（把旧实现放回去，测试确实抓到）。

**副作用（需要知道）**

- 已有 32,125 条里，**简介被覆盖了 17,613 条**——coep 给的是分集自己的简介，
  和影片页那份不同。这是「更多分集数据」的一部分，属预期，但确实改写了原有文本。
- **标题被改掉 29,942 条**，从 `Episode #N` 占位符换成了真实标题（这是目的）。
- 有 103 条已翻译的分集简介（`description_zh`）被清成 NULL：
  UPSERT 的语义是「简介变了 → 旧翻译失效」。实测这 103 条的简介**确实都变了**
  （逐条比对，0 条是简介没变却清空的），所以是正确行为，不是数据损坏。
- 还剩 **2,183 条**分集仍带 `Episode #N` 占位标题。抽查确认：
  **它们的影片所属公司的 coep 表里根本没有这条分集**（走遍全部分页都没有），
  是站点侧的缺口，不是我们漏抓。

### 4.3 更早的已完成工作（按提交）

- `16fefee` 修打包版打不开资料库（空库 ≠ 数据丢了，是找错地方）
- `668e73b` / `d05b9c2` GPDb 应用图标，明暗与着色自适应
- `4ca1478` / `10ef3ac` / `d67a471` 改名 GPDb、侧栏拖动、流体玻璃材质
- `c3e6ef7` … `87e849d` 中文片名与分类标签：schema、翻译层、`title_zh` 串遍所有查询、
  分类折叠成 53 个原子词、词元匹配筛选、搜索加中文片名
- `bcff731` 桌面版可直接管理翻译接口与模型
- `270d3bf` 查询层拆成 `gpdb-core`
- `d08e3af` 改名 GEVI+
- `52d05da` 修六项缺陷：分集板块空白、分集剧照过大、导演名粘连、一级菜单、片商 chip、作品计数

### 4.4 历史结论（别重复踩）

- **1,108 条字段残缺的影片不要再重抓**——源头站点本身就是空的。已验证。
- **导演分词已落库**，回滚语句在当时的计划里；启发式方案已被否决。
- **`/newe` 没有分页**，`?page=2` / `?start=100` 都返回同样 60 条，`/newe/1` 是 404。
- **站点公司 id 空间已探尽，上限是 8,175。** 两条独立证据：① `/company/8175` 返回 200，
  而 8176 以上抽测的 12 个 id（8176/8177/8180/8190/8250/9000/12000/12001/12500/13000/
  14000/15000/20000）全部 301 → `/404.shtml`；② coep 探针把 id 1..12,000 逐条扫过，
  8,175 以上一条分集都没有。**别再跑 `--sweep-companies`**，调多大都不会有新公司。
- **探针把不存在的公司记成「完成」是对的**：`coep` 对不存在的 id 返回 200 +
  `recordsTotal: 0`（不是 404），所以 `_process_company` 走 `ok` 分支、记 progress 200。
  这正是探针的设计，「12,000 行全 200」不等于「站点有 12,000 家公司」。
- **对拍测试会静默跳过**：报 `ok` 可能什么都没比。跑 `cargo test -- --nocapture`
  确认没有 `skipping:` 输出（真跑约 1.5s，跳过约 0.1s）。改对拍逻辑后**必须做变异验证**。

---

## 5. 待完成事项

### 5.1 提交状态：已完成

**本轮（导演库）**：`67676c3` 代码 + 测试 + `schema.sql` 注释订正（见 §4.1），
`5045452` 是这份文档的改动。两个都已提交，工作区干净。

**上一轮（分集）**：10 个代码文件的改动拆成 5 个提交（见 §4.2 的表），
交接文档与 `.gitignore` 是第 6 个。
`cover_dl.log` / `cover_dl.pid` / `translate.log` / `translate.pid` 已在 `.gitignore`
里挡掉（两个 `.pid` 是死进程留下的，已删）。
后面还有一次订正（探针跑完后做的，别当成数据被谁动过）：`HANDOVER.md` 的数字刷新 +
`scraper_v2.py` 的 `_report_empty_work_list()`（空目标时把「为什么空」讲清楚，见 §7.11）。

下面 5.2 起才是真正待做的。

### 5.2 数据侧：两个长任务（**必须串行，别开两个终端同时跑**）

**第一步 — 公司补齐探针：✅ 已完成，不要再跑。**

`scrape_progress` 里 company 12,000 行全部 200，即 id 1..12,000 全覆盖（2,411 家已知
片商 + 9,589 个探针）。结果是 66 家新公司、13,372 条独立分集（见 §4.2）。

**再跑这个命令只会打印「目标: 0 条」然后立刻退出**——这不是坏了，是真的没活了。
站点公司 id 上限就是 8,175（证据见 §4.4），**把 `--sweep-companies`
调多大都没用**，探针这个方向已经走到底。

```bash
# 只用来确认「确实没有目标」，不会再抓任何东西：
python3 -u scraper_v2.py --mode episode-sync --concurrency 3 --sweep-companies 12000
```

**第二步 — 高清图全量预下载（← 现在从这一步开始）**：

```bash
cd "/Users/joel/iCloud Drive (Archive)/Documents/antigravity/游戏库管理App/GEVI_Offline_Database"
python3 -u cache_images.py --mode episodes --concurrency 32 > /tmp/img_dl.log 2>&1
```

- **并发用 32，不要用 6。** 2026-09-22 实测：6 并发 **1.0 张/s**（要 33 小时），
  32 并发 **6.4 张/s**（约 5.1 小时），吞吐正比于并发数 → 瓶颈是**每请求固定延迟**
  （~4.5s 握手+TTFB），不是单 IP 带宽。封面那轮也是 32 并发跑的（7.2 张/s，4 小时）。
- 会跳过已缓存的（判断条件是本地文件存在且 >100 字节），**可反复重跑**。
- 要下的量：库里 134,489 条分集里 **119,246 条有图**（全部是 `b.jpg` 高清 URL），
  而 `image_cache/Episodes/` 里跟这些 URL **对得上的只有 96 个文件**——约 **11.9 万张没下**。
  （目录里现有那 1,831 个文件是旧的低清 `episodeN.jpg`：URL 改成高清后它们已成孤儿，
  `--prune-orphans` 清的就是这批。）
- 预计 **7–8 GB**（实测平均 ~64 KB/张）。磁盘可用 133 GB，够。
- 跑完后**先原样再跑一遍**（同一行命令）。它会跳过已下好的、只重试缺的，约 20 分钟。
  这一步不是可选的，原因见下面那条。

**收尾（顺序不能变，中间那步别跳）**：

1. 下载跑完 → 2. **同一命令再跑一遍**（只重试缺的）→ 3. `python3 cache_images.py --revert-missing-hd --apply`
   → 4. `python3 cache_images.py --prune-orphans --apply`

**为什么必须重跑一遍再回滚**：`run_revert_missing_hd()` 是**纯本地**判断——文件不在盘上就把
`thumbnail_url` 改回低清 URL，不去问站点（`cache_images.py:396` 的 docstring 自己写了
"judged locally, so this costs no requests"）。而下载把**连接被拒**也记成失败：
实测失败里混着 `ssl.SSLEOFError: UNEXPECTED_EOF_WHILE_READING`（就是铁律 8 记的那个签名），
我挑了几张被判「缺」的手工去拿，**返回 HTTP 200 + 47–92 KB 的真图**。所以第一遍的
7% 失败里有一部分是「图在、我们没拿到」，直接回滚会把它们**永久降级成低清**。
重跑一遍（幂等、只碰缺的）之后仍然缺的，才基本是站点真的没有。
`run_revert_missing_hd` 现在也会先把这个警告打出来再让你 `--apply`。

**顺序为什么不能反**：图是照数据库里的 `thumbnail_url` 下的。先下图、后补数据，
新分集的图就漏了，得再下一遍（虽然可续跑，但白费一轮带宽和时间）。第一步已经跑完，
所以现在这个顺序是安全的。

### 5.3 代码侧

- **分集搜索不搜 `e.title`**（`queries/episodes.rs:31-37` 和 `db_manager.list_episodes` 都是）。
  用户已明确列为**后续项**，本轮不做。但要知道：刚抓来的 10.2 万条真实标题**目前搜不到**。
- **导演库的两条已知边界**（都确认过，不是缺陷）：
  1. 导演的「作品」**只有影片，不含分集**——库里没有 `episode_directors` 表，
     分集数据里也没有导演字段。所以导演页上的作品数就是他的影片数。
  2. `directors.works_count` **仍然没有随刮削刷新**（§3.6），但导演库**完全不读它**，
     所以不会再有人看到过期数字。将来若要跑 `refresh_director_counts()`，
     那是一次全表回填、只改这一列，不影响 `movie_directors`，属于可做可不做。
- **导演详情弹窗的筛选是纯前端的**（关键字/年份/片商 chip），所以只作用于**已加载**的作品。
  作品是一次全量返回的，因此今天不等价于「只筛第一页」；但如果将来某位导演的作品真的
  多到需要分页，这套筛选必须一起改成服务端，否则会静默地只筛一部分。
- `server.py` / 桌面端的人工验证：分集库页面、片商页、演员详情页的分集页签
  （这轮改了 SQL 和 LEFT JOIN，值得实际点一遍）。

### 5.4 翻译（几乎没开始）

`movies.description_zh` 只做了 2,367 / 63,238；`title_zh` 全空；`episodes.description_zh` 77。
`translate.py` 有片名/分类/简介几个模式，术语表 81 条（注意：`J/O` 是自慰不是手交）。
跑之前确认 `translate_config.json` 在位（含用户自己的 DeepSeek key，**别提交**）。

### 5.5 用户主动推迟的

- **片商 logo 抓取**：用户推迟了。开工前必读那份设计稿，里面记了**两条最伤数据的坑**。

### 5.6 明确不做

- 重抓 32,125 条分集页去补 `action_notes`（coep 没有这个字段，分集页实测也多为空）。
- 多语种 i18n、演员艺名合并、原生窗口 vibrancy。

---

## 6. 常用命令

```bash
cd "/Users/joel/iCloud Drive (Archive)/Documents/antigravity/游戏库管理App/GEVI_Offline_Database"

# 起后端（改完 server.py / db_manager.py 必须重启）
python3 server.py                     # 8787

# 分集同步（按公司；12,000 家公司已全部完成，现在跑只会打印「目标: 0 条」）
python3 scraper_v2.py --mode episode-sync --concurrency 3
# 探针也一样扫完了，加 --sweep-companies 不会有新目标（见 §5.2）

# 备份（库很大，别用 cp）
sqlite3 gevi.db ".backup '/tmp/gevi-backup.db'"

# 图片缓存
python3 cache_images.py --stats
python3 cache_images.py --mode episodes --concurrency 32   # 6 会慢 6 倍，见 §5.2

# 桌面端
cd desktop_client && cargo test --workspace -- --nocapture
cd desktop_client && npx vue-tsc --noEmit
```

**看进度**：长任务一律 `nohup ... > /tmp/xxx.log 2>&1 &`，然后 `tail -c 500 /tmp/xxx.log`。
**别用管道接 `tail -30`**——那会把几小时任务的中间输出全吞掉，等于没有观测。

---

## 7. 踩过的坑（省你几小时）

1. **备份文件会自杀**（已修）。`prune_backups` 原来按**文件名**排序，时间戳以数字开头、
   手工备份以字母开头，数字排前面 → 刚写的备份被判为「最旧」优先删除。
   而且 `print("已备份")` 在删除**之前**，所以日志报了个不存在的备份。
   **这意味着此前每一次跑这个刮削器，报的备份都是不存在的。** 现在按 mtime 排、
   只轮转带时间戳的文件、打印挪到轮转之后并校验文件还在。
2. **并发 24 会把站点打到拒绝连接**，跑几小时零写入。停掉立刻恢复。低并发反而更快。
3. **写库静默无效**：`have` 里装的是 `(ep_id, hd)` 但 SQL 写成了
   `SET thumbnail_url = ?` 配 `WHERE id = ?`——参数顺序颠倒，SQLite 匹配 0 行且不报错，
   报告还显示「成功 283」。**改批量写入后一定要用 `conn.total_changes` 差值校验行数。**
4. **假的可续跑**：`already = len(rows) - len(todo)` 在 limit 之后算，
   等于把 limit 原样报回来。`all_todo` 要在应用 limit **之前**统计。
5. **漏记 progress**：`save_movie` 会为影片记进度，`save_standalone_episodes` 不会。
   不显式 `record_progress("company", id, 200)`，公司永远不算完成，每次都重抓 2,411 家。
6. **占位标题会覆盖真标题**：`_write_episodes` 的 UPSERT 是
   `COALESCE(NULLIF(excluded.title,''), episodes.title)`——只要重抓写进非空占位符，
   真标题必然被打回 `Episode #N`。所以解析器侧就不能再合成占位符。
7. **`filter_map(|r| r.ok())` 会静默吞行**：类型不匹配（NULL 读进 `i64`）时整行消失，
   不报错。`movie_id` 必须 `Option<i64>`。
8. **对拍测试静默跳过**：见 §4.4。
9. **`iCloud Drive` 路径带空格和括号**，命令里务必加引号。
10. **改文件不影响正在跑的进程**：Python 已把模块载入内存，
    可以趁长任务在跑时改代码，但要记得**下次启动才生效**。
11. **「目标: 0 条」然后秒退 ≠ 崩了**，但也确实什么都没干。`episode-sync` 尤其容易
    误解：它的探针是一次性补齐，跑完就没有第二轮。曾经因此被当成「命令坏了、一条
    新分集都没刮到」——实际是上一轮已经跑完了，而当时的交接文档写的是跑到一半的数字。
    现在空目标会走 `_report_empty_work_list()`，把账本状态和「重跑没有第二轮」讲出来。
    **教训：长任务还在跑的时候，别照当时的读数写文档。**
12. **`--revert-missing-hd` 会把「下载被拒」当成「站点没有」**：它只看本地有没有文件，
    而 `download_image` 把 `ssl.SSLEOFError: UNEXPECTED_EOF_WHILE_READING`（连接被拒，
    铁律 8 那个签名）也记成失败——两种失败在本地看起来一模一样。实测：判「缺」的图
    手工去拿，HTTP 200 + 47–92 KB 的真图。**所以回滚前必须原样重跑一遍下载**
    （幂等、只重试缺的），否则会把真实存在的高清图永久降级成低清。见 §5.2。
13. **图片下载的瓶颈是每请求延迟，不是带宽**：6 并发 1.0 张/s、32 并发 6.4 张/s，
    吞吐正比于并发数（~4.5s/请求）。别照搬刮 HTML 的并发纪律（那是 1.4–1.7 req/s、
    拉高会被拒），图片端点是静态文件，封面那轮 32 并发跑了 4 小时没事。
