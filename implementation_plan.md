# GEVI 离线影视库 (GPDb) 迭代任务实施计划（按 Token 消耗由少到多排序）

本轮任务按预计代码修改量与 Token 消耗由低到高严格排序，每完成一项任务均执行验证与文档记录。

---

## 任务执行序列（由少到多）

### 阶段 1：轻量纠错与界面清理 (Low Token)
1. **[清理] 移除设置页冗余的「大模型自动翻译目标语言」板块**
   - 目标：移除 `App.vue` 设置板块中重复的翻译目标语言设置（已在功能插件专区内提供）。
   - 文件：`desktop_client/src/App.vue`
2. **[纠错] 修复「典藏成就奖杯系统」重置记录未生效及确认弹窗**
   - 目标：点击重置按钮时弹窗提醒「确认操作将清空所有已获取的奖杯并重置为初始状态」，确认后彻底重置 `localStorage`、响应式内存数据并刷新界面。
   - 文件：`desktop_client/src/services/trophySystem.ts`, `desktop_client/src/views/PluginsView.vue`, `desktop_client/src/views/TrophiesView.vue`
3. **[纠错] 修复 BT 磁力资源搜索扩展在演员/影片页面点击无反应**
   - 目标：排查 `MovieDetailModal.vue` 与 `PerformerDetailModal.vue` 中 BT 搜索按钮的触发逻辑、插件启用状态检查以及系统浏览器/外部链接打开逻辑。
   - 文件：`desktop_client/src/components/MovieDetailModal.vue`, `desktop_client/src/components/PerformerDetailModal.vue`
4. **[纠错] 修复「我的收藏与片单」中点击无影片出处的独立分集未弹出详情页**
   - 目标：在 `App.vue` 的 Favorites 界面中，当分集 `movie_id` 为 null 时，点击直接调用 `openEpisodeDetail(ep)` 弹窗，显示独立片段详情。
   - 文件：`desktop_client/src/App.vue`

### 阶段 2：布局美化与控件优化 (Medium-Low Token)
5. **[美化] 「我的收藏与片单」分集网格尺寸支持调整**
   - 目标：为分集专区增加列数调节或调整默认网格卡片尺寸，避免卡片显示过小。
   - 文件：`desktop_client/src/App.vue`
6. **[美化] 演员页面合作片商胶囊默认全部显示且支持折叠**
   - 目标：`PerformerDetailModal.vue` 中片商胶囊列表默认完整展示，提供折叠/展开切换按钮与状态持久化。
   - 文件：`desktop_client/src/components/PerformerDetailModal.vue`
7. **[优化] 演员作品中的分集/片段支持网格排列**
   - 目标：在演员详情页的片段板块增加横卡列表 (List) 与网格图 (Grid) 切换按钮，网格模式下可展示更多分集条目。
   - 文件：`desktop_client/src/components/PerformerDetailModal.vue`
8. **[调整] 功能插件管理界面增加顶部子分类快捷切换胶囊**
   - 目标：在 `PluginsView.vue` 顶部增加子分类胶囊导航（全部、核心功能、数据扩展、娱乐成就等），分类聚焦浏览。
   - 文件：`desktop_client/src/views/PluginsView.vue`

### 阶段 3：复杂交互与扩展功能 (Medium-High Token)
9. **[增加] 统计界面专属子分类胶囊，深化细分维度，并整合奖杯陈列馆入口**
   - 目标：在 `AnalyticsView.vue` 增加顶部专属子分类胶囊（总览看板、影片洞察、演员喜好、探索足迹等），并当启用奖杯插件时，在统计子分类中内嵌「奖杯陈列馆」选项卡。
   - 文件：`desktop_client/src/views/AnalyticsView.vue`, `desktop_client/src/App.vue`, `desktop_client/src/components/Sidebar.vue`
10. **[修改] 影片库语言切换按钮动态显隐、根据翻译库判定可用语种并增加说明图标**
    - 目标：仅在启用翻译功能且数据库已存在有效翻译时显示语言切换按钮；动态根据已翻译语种提供选择；按钮旁增加 `(ℹ)` 说明图标并支持悬浮提示。
    - 文件：`desktop_client/src/App.vue`
11. **[新增] 数据库完整导入与导出功能**
    - 目标：在 Rust 后端实现安全数据库备份导出（`sqlite3 .backup` 机制）与安全恢复导入，并在设置界面的数据库板块中提供导入/导出 UI。
    - 文件：`desktop_client/src-tauri/src/commands/db.rs`, `desktop_client/src-tauri/src/lib.rs`, `desktop_client/src/api.ts`, `desktop_client/src/App.vue`
12. **[App 图标] 四套方案落地并在设置界面提供视觉选择器（默认方案 A）**
    - 目标：实现方案 A（黑曜石棱镜胶片之匣）、B（双雄火星图腾）、C（大卫躯干与赛博明暗）、D（流光字母印章）四套高品质应用图标设计；在设置界面提供实时预览卡片与切换器，默认预设方案 A。
    - 文件：`desktop_client/src/assets/icons/`, `desktop_client/src/App.vue`, `desktop_client/src/utils/theme.ts`
13. **[新增] 智能识别同片商同一系列影片，并在简介页面提供全系列网格入口**
    - 目标：实现主标题规范化抽取算法（去除序号、罗马数字、Vol/Part/副标题），在影片详情页检测同一片商下的系列作品，如果作品数 ≥ 2 则在标题下方增加「查看全系列作品 (N部) →」按钮，点击弹出全系列作品网格画廊。
    - 文件：`desktop_client/src-tauri/gpdb-core/src/queries/movies.rs`, `desktop_client/src/components/MovieDetailModal.vue`, `desktop_client/src/components/SeriesModal.vue`

---

## 验证计划
1. 每完成一步，运行 `npx vue-tsc -b` 检查前端类型；
2. 涉及 Rust 后端修改时，运行 `cargo test --workspace` 确保所有 69+ 项测试通过；
3. 全部完成后执行 `npm run build && npm run tauri build`，部署至 `/Applications/GPDb.app` 并更新文档记录。
