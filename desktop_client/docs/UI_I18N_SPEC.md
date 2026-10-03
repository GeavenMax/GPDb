# GPDb 客户端界面多语言与本地化规范 (UI Internationalization Specification)

> 本规范定义了 GPDb 桌面客户端（macOS / Windows）及相关前端界面的多语言本地化架构、开发规范、核心约定与自动化测试准则。供后续开发者及 AI 助手在新增功能、重构界面或扩展语言时严格遵循。

---

## 1. 核心架构哲学：二元文本体系严密解耦

GPDb 客户端采取**「系统界面文字」与「数据库影视内容」严密分离的二元解耦架构**：

```
                           ┌────────────────────────────────────────────────────────┐
                           │                     GPDb 客户端                        │
                           └──────────────────────────┬─────────────────────────────┘
                                                      │
                       ┌──────────────────────────────┴──────────────────────────────┐
                       ▼                                                             ▼
       【系统界面文字 (Static UI Texts)】                           【数据库影视实体 (DB Content Texts)】
       - 侧边栏/导航/设置/插件/搜索/状态提示                          - 片名/副标题/剧情简介/演员/片商名
       - 存储于: desktop_client/src/i18n/index.ts                    - 存储于: GPDb.db 与 translations.db
       - 状态受控于: currentLocale (localStorage: gpdb_ui_locale)    - 状态受控于: gpdb_content_presentation_mode
       - 严格 1:1 键位对齐 (1019 keys × 7 语言)                       - 支持「自动匹配 / 始终双语 / 始终原文」
```

1. **界面系统文字 (Static UI Texts)**：
   - 包含：导航栏、状态标签、按钮文本、筛选抽屉、指标卡片、设置选项、弹窗文案、提示与日志流。
   - **唯一数据源**：`desktop_client/src/i18n/index.ts`。
   - **语言切换**：立即响应 `currentLocale` 响应式变量，零等待全局热重载。
2. **数据库内部实体文本 (DB Content Texts)**：
   - 包含：电影原名/译名、分集剧情简介、演员艺名、厂牌名、风格历史解析等。
   - **数据源**：主数据库 `GPDb.db` 与离线翻译库 `translations.db`。
   - **呈现模式**：通过 `src/utils/bilingual.ts` 统一计算：
     - `auto`（推荐）：界面选择英文时自动纯英文；界面选择中文时自动开启「中文主标题 + 浅灰英文原名」双显模式。
     - `bilingual`：强制中英双显。
     - `original`：强制仅看原文。

---

## 2. 支持语言矩阵 (7 Locales)

客户端严格支持 7 大主流语言，对应的 `SupportedLocale` 代码定义如下：

| 代码 (`SupportedLocale`) | 语言显示名称 | 本地原生名称 (`native`) | 说明 |
| :--- | :--- | :--- | :--- |
| `zh-CN` | 简体中文 | 简体中文 | 默认语言，全功能覆盖 |
| `zh-TW` | 繁体中文 | 繁體中文 | 港台正体习惯，术语地道对齐 |
| `en` | 英语 | English | 国际标准，无中文字符残留 |
| `ja` | 日语 | 日本語 | 符合日本影迷与排版习惯 |
| `it` | 意大利语 | Italiano | 典雅电影学者风格 |
| `es` | 西班牙语 | Español | 纯正拉丁美洲与欧陆西语 |
| `de` | 德语 | Deutsch | 严谨影视文献术语 |

---

## 3. 铁律准则：100% 1:1 键位完全对齐 (Key Parity)

1. **零键位偏差**：
   - `desktop_client/src/i18n/index.ts` 中的 `MESSAGES` 字典包含 7 个语言分片。
   - **每一个新增的键（Key），必须在所有 7 个语言字典中同时声明**。
   - 严禁某一语言有 1019 个键，而另一语言只有 1015 个键。键位缺失属于编译级阻断缺陷。
2. **自动化对齐校验脚本**：
   - 任意修改国际化字典后，**必须**运行校验脚本：
     ```bash
     python3 desktop_client/scripts/test_parity.py
     ```
   - 校验标准：所有 7 种语言的有效唯一键数完全一致，控制台输出 `Parity test completed.` 且无任何 `missing keys` 提示。

---

## 4. `t()` 国际化函数编写规范

### 4.1 函数签名与用法
```typescript
import { t } from '../i18n';

// 1. 无参普通调用
const label = t('nav.movies');

// 2. 带动态参数插值调用
const countText = t('library.moviesCount', { current: 10, total: '1,200' });
```

### 4.2 严禁在模板中传入中文 Fallback 字符串
在历史版本中，部分组件使用了类似 `t('common.more', '更多')` 的写法。当该键在非中文语种回退或异常时，此默认值会导致界面瞬间混入中文。
- **错误写法（禁止）**：
  ```html
  <span>{{ t('nav.home', '主页') }}</span>
  <div :title="t('nav.sectionSearch', '资源检索')"></div>
  ```
- **正确写法（规范）**：
  ```html
  <span>{{ t('nav.home') }}</span>
  <div :title="t('nav.sectionSearch')"></div>
  ```

### 4.3 动态插值语法
字典条目中统一采用 `{variable}` 占位符：
```typescript
// 字典定义
'sync.inHoursMins': '{h}h {m}m remaining',
'library.studiosCount': '{current} shown · {total} studios',

// 组件使用
t('sync.inHoursMins', { h: 2, m: 30 });
```

---

## 5. 组件与脚本排查防漏规范

在用户选择英文界面菜单时，以下几类高危区域最容易遗留中文，开发与审查时需逐一确认：

### 5.1 胶囊按钮、筛选抽屉与网格排序
- 排序选项（`sortBy`）、时间范围胶囊（`DATE_FILTER_OPTIONS`）、人脸属性筛选项（`facetLabel`）必须调用 `t()` 动态派发。
- 排序的 `computed` 必须声明明确的联合类型，如 `computed<{ id: StudioSortBy; label: string }[]>`，防止类型拓宽为 `string` 导致 TypeScript 校验报错。

### 5.2 状态提示、加载中与空数据占位
- 错误提示、加载提示（`loadingWorks`）、空搜索结果（`noResults`）严禁在 `<template>` 内硬编码中文。
- 外部触发的异步异常信息（如 GitHub API 更新检测、下载安装流等），抛出与组装的 `Error` 必须调用 `t('update.apiFailed')` 等对应翻译。

### 5.3 导出文件与多媒体属性
- 导出 Markdown 报告时的文档标题、时间戳标题与下载文件名（`exportAiReportMarkdown`），根据 `currentLocale` 智能生成中英文文件名与标题。
- 图片 `alt` 属性（如灯箱海报、卡片封底封面），使用 `t('movie.frontCover')`、`t('movie.backCover')` 组装。

### 5.4 LLM 提示词引擎与分析报告
- AI 深度洞察报告（`aiAnalysis.ts`）：
  - 当 `currentLocale.value.startsWith('zh')` 时，组装中文分析导赏 Prompt；
  - 当处于英文或其他语种时，自动切换为纯正英文学术导赏 Prompt，并在末尾要求以当前目标语种（`targetLang`）输出报告结构与 JSON 块。

---

## 6. 标准验收流程 (Verification Protocol)

任何助手或开发者在提交多语言与国际化相关 PR / 变更前，必须依次执行以下 3 步命令进行完整验证：

```bash
# 步骤 1: 验证 7 大语言 100% 1:1 键位完全对齐
python3 desktop_client/scripts/test_parity.py

# 步骤 2: 全面扫描源码，确认非中文状态下无游离界面中文
python3 desktop_client/scripts/find_all_chinese.py

# 步骤 3: 严格执行全项目 TypeScript 类型检查与 Vite 生产编译打包
cd desktop_client && npm run build
```

只有当：
1. `test_parity.py` 报告 7 种语言键数完全相等（1019 keys）；
2. `npm run build` 以退出码 `0` 完成编译（零 TS 报错）；
该阶段任务方可判定为正式交付完毕。
