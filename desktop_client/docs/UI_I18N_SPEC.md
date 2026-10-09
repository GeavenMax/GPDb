# GPDb 客户端界面国际化（i18n）技术规范

> 最后更新：2026-10-07。本文档是 macOS / Windows 桌面客户端界面多语言体系的唯一权威规范。

---

## 1. 架构总览：二元解耦体系

GPDb 客户端中存在**两套完全独立的语言体系**，开发者必须清晰区分：

| 维度 | 界面菜单语言（UI Locale） | 数据库内容语言（Content Language） |
|---|---|---|
| **控制变量** | `currentLocale`（`i18n/index.ts`） | `descLang` + `contentLangMode`（`App.vue`） |
| **存储键** | `gpdb_ui_locale` | `gpdb_desc_lang` + `gpdb_content_lang_mode` |
| **切换入口** | 设置 → 语言与本地化 | 资料库顶部 `[中文]` / `[原文]` 快捷按钮 |
| **覆盖范围** | 顶栏、侧边栏、弹窗标题、筛选面板、全景洞察图表坐标轴、分享卡片模板文案等一切客户端自有 UI 文字 | 影片标题/简介、分集剧情、演员生平（数据库 `title_zh`、`description_zh` 等字段） |
| **总则** | 严格跟随 `currentLocale`，100% 通过 `t('key')` 调用 | 由 `bilingual.ts` 根据呈现模式统一调度，绝不直接写入 i18n 字典 |

### 1.1 双向联动同步机制（v2.18.0+）

为彻底杜绝两套语言状态脱节导致的展示混乱（如分享卡片在中文界面展示英文简介），v2.18.0 引入了双向联动：

- **设置切换菜单语言** → `onSelectLocale(locale)` 自动同步重置 `descLang` 并将 `contentLangMode` 复位为 `'auto'`
- **资料库顶部快捷切换** → `setDescLang(lang)` 联动同步 `currentLocale`（切中文联动 `zh-CN`，切原文联动 `en`）

---

## 2. 语言包体系

### 2.1 支持语种与文件结构

```
desktop_client/src/i18n/
├── index.ts                    # 导出 t()、setLocale()、currentLocale 等核心 API
└── locales/
    ├── zh-CN.ts                # 简体中文（基准语言）
    ├── zh-TW.ts                # 繁体中文
    ├── en.ts                   # 英语
    ├── ja.ts                   # 日语
    ├── it.ts                   # 意大利语
    ├── es.ts                   # 西班牙语
    └── de.ts                   # 德语
```

- 当前词条总数：**1,096 个**（每语种完全对齐）
- 每个语言包导出一个扁平化键值对对象，键名采用 `module.key` 点分命名法

### 2.2 智能多级回退链

`t()` 函数的回退逻辑（`index.ts`）：

$$\text{目标语言字典} \longrightarrow \text{英文字典（非中文语系时）} \longrightarrow \text{简体中文字典} \longrightarrow \text{键名本身}$$

> **关键设计意图**：当日语/德语/西班牙语等小语种缺失某个词条时，回退至英文而非中文，避免在全英文界面中突兀出现中文碎片。

### 2.3 严格 1:1 键位对齐规则

7 种语言的字典键**必须 100% 镜像对称**。每次增改词条后，必须运行键位校验：

```bash
# 自动化交叉校验（在 desktop_client/ 目录下）
npx tsx scripts/check-i18n-keys.ts
```

预期输出：
```
zh-CN: 1096 keys
zh-TW: 1096 keys (missing compared to zh-CN: 0)
en:    1096 keys (missing compared to zh-CN: 0)
ja:    1096 keys (missing compared to zh-CN: 0)
it:    1096 keys (missing compared to zh-CN: 0)
es:    1096 keys (missing compared to zh-CN: 0)
de:    1096 keys (missing compared to zh-CN: 0)
```

---

## 3. 开发准则（铁律）

### 3.1 严禁硬编码中文文本

```vue
<!-- ❌ 错误：模板中直接写中文 -->
<span>最新入库</span>
<option>年份倒序</option>

<!-- ✅ 正确：通过 t() 调用 -->
<span>{{ t('filter.latestAdded') }}</span>
<option>{{ t('sort.yearDesc') }}</option>
```

### 3.2 严禁在 `t()` 中使用中文默认值

```ts
// ❌ 错误：英文界面下会回退到中文
t('sort.yearDesc', '年份倒序')

// ✅ 正确：无默认值，依赖字典回退链
t('sort.yearDesc')
```

### 3.3 术语翻译降级规则

`utils/glossary.ts` 中的 `tr()`、`trCategory()`、`trMeasure()` 等函数：
- 判断条件为 `!currentLocale.value.startsWith('zh')`
- 所有非中文环境一律返回英文原名与西文单位
- **严禁**仅判断 `=== 'en'`，否则日语/德语等环境会泄漏中文

### 3.4 分享卡片语言联动

`ShareCardModal.vue` 接收的 `ShareCardData` 必须携带 `lang: 'zh' | 'en'` 字段：
- 由 `MovieDetailModal.vue` / `EpisodeDetailModal.vue` 根据 `(props.lang === 'zh' || currentLocale.startsWith('zh')) && !showOriginal` 计算并注入
- 中文简介需同时透传 `zhDescription.value`（动态翻译结果）与 `props.movie.description_zh`（数据库原始值）
- Canvas 渲染与 HTML 预览必须统一使用 `isZh` 计算属性

### 3.5 ECharts 图表语言响应

全景洞察（`IndustryPanoramaView.vue`）与厂牌谱系（`StudioGenealogyView.vue`）中的 ECharts 坐标轴、图例与 Tooltip 必须：
- 通过 `t()` 注入文本
- 监听 `watch(currentLocale, () => renderCharts())`，语言切换时实时重绘

---

## 4. 质量闸口

### 4.1 编译检查（必须通过）

```bash
cd desktop_client && npm run build
# 包含 vue-tsc -b 全量类型检查 + Vite 打包
```

### 4.2 源码中文残留扫描

提交前应对 `desktop_client/src/**/*.vue` 执行中文字符扫描，确认所有客户端 UI 文本已 100% 接入 `t()`。

仅允许保留的中文：
- 代码注释
- 双语切换按钮标识角标（`{{ currentLocale.startsWith('zh') ? '中' : 'ZH' }}`）
- 日期格式化中的显式中文分支（`if (currentLocale.startsWith('zh')) return ...月...日`）
- 日志解析 token（如 `SyncModal.vue` 中的状态字符串匹配）

---

## 5. 变更历史

| 版本 | 日期 | 变更概要 |
|---|---|---|
| v2.18.0 | 2026-10-05 | 语言包模块化拆分（7 文件 × 1,096 键）、智能回退链、术语降级修复、分享卡片中英联动、全景洞察/厂牌谱系图表国际化、轮播标题语言感知、移除 Random Picks 板块 |
| v2.18.0 | 2026-10-05 | 菜单语言与数据库内容语言双向联动同步、分享卡片 `lang` 参数注入、`zhDescription` 透传修复 |
