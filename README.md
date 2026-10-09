# GPDb · 极速隐私优先的男同成人影视数据库管理客户端

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>专为男同成人影视爱好者打造的高性能、全维度档案管理、绝对隐私与零云端追踪的现代化跨平台管理客户端</strong>
</p>

<p align="center">
  <a href="./README.md"><b>简体中文</b></a> |
  <a href="./README_EN.md">English</a> |
  <a href="./README_ZH_TW.md">繁體中文</a> |
  <a href="./README_JA.md">日本語</a> |
  <a href="./README_DE.md">Deutsch</a> |
  <a href="./README_ES.md">Español</a> |
  <a href="./README_IT.md">Italiano</a>
</p>

<p align="center">
  <a href="https://t.me/gpdbnews" target="_blank"><img src="https://img.shields.io/badge/Telegram-Channel%20%40gpdbnews-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Channel" /></a>
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Android-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Version-v2.18.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **官方频道**：订阅 [GPDb 官方 Telegram 频道 (@gpdbnews)](https://t.me/gpdbnews)，第一时间获取版本发布、数据库增量更新与使用技巧！

---

## 📖 项目定位 (About GPDb)

**GPDb**（Gay Pornography Database Manager）是专为**男同成人影视（Gay Adult Media）**爱好者与收藏家打造的**现代化全功能离线数据库管理系统**。

**坚持 100% 本地优先（Offline-First）与绝对私密无痕**：
- **零云端依赖**：媒体元数据、演员身体档案、海报缓存及个人标记完全存储于本地物理磁盘；
- **纯单机架构**：无用户账号体系、无数据遥测追踪、无外部服务器上传；
- **原生级强悍性能**：基于 **Rust 高性能核心 (`gpdb-core`)** + **Tauri v2 + Vue 3**（macOS / Windows）及 **Kotlin + Jetpack Compose + Room**（Android 原生），面对 **63,000+ 部长片、134,000+ 分集、108,000+ 演员与 2,400+ 厂牌**，保持 60fps 丝滑渲染与毫秒级瞬时检索。

---

## ✨ 核心特色亮点 (Core Highlights)

### 1. 全维度演员身体档案与高容错联合检索
- **细粒度体貌筛选**：支持体型身段（Build）、发色、瞳色、体毛丰度、身高体重、肤色、生理尺寸（Dick Size / Foreskin）与纹身穿孔组合过滤。
- **艺名 (AKA) 智能聚合**：自动归纳跨厂牌、跨年代的多重曾用艺名，杜绝遗漏。
- **正片与独立分集精准解耦**：清晰区分主角长片与客串短剧场景。
- **高容错联合搜索**：FTS5 高速索引 + SQL 多字段兜底，支持中英双语、厂牌、导演、简介与演员多维度联合检索。
- **PBC 百科全维度整合**：集成 Porn Base Central 百科档案（本名、出道年份、活跃状态、生平小传及 IAFD/IMDb/X 互联档案）。

### 2. 沉浸式流媒体主页与多封面平铺画廊
- **五大发现推荐流**：焦点巨幕海报轮播、往年今日经典首映、今日星光标志面孔、经典系列大放送及随心探索盲盒。
- **多封面平铺画廊**：正向封面、封底写真及多版本海报横向自适应平铺展示，配备 Front/Back 角标。
- **全屏缩放灯箱**：支持鼠标滚轮平滑缩放 (1.0x ~ 5.0x)、平移拖拽、双击缩放及移动端双指手势。

### 3. 智能系列影片集与封面拼贴
- **自动聚类算法**：智能识别罗马数字与副标题，自动将系列作品归拢成套。
- **自适应艺术拼贴封面**：自动生成 1~4 图对称或矩阵式艺术拼贴海报，离线极速渲染。

### 4. 导演档案卡与 2,400+ 厂牌超清图鉴
- **导演专属履历**：一键唤起执导作品集，支持全库作品快速联动筛选。
- **厂牌 Logo & 横幅 100% 覆盖**：全库 2,490+ 厂牌已 100% 配齐高清 Logo（WebP 离线直传），690+ 厂牌收录官方宽屏 Banner；作品 Logo 覆盖率达 98%。
- **舒适详情布局**：剧情简介通栏展开易读，核心操作按钮矩阵化收拢。

### 5. 📸 截屏防窥模式与流光分享卡片 (v2.18.0)
- **全局一键截屏防窥**：桌面顶栏眼睛开关 / Android 探索主页顶部快捷开关，瞬间触发海报高斯模糊 (`blur(24px)`) 与剧情文字脱敏 (`blur(7px)`)，杜绝泄露。
- **流光双封面分享卡片**：
  - 支持正反双封面并排排版、16:9 分集居中裁切防拉伸；
  - 纯离屏自适应双线性模糊光晕（流光/深黑/午夜预设）；
  - 嵌入 Telegram 官方频道二维码 (`t.me/gpdbnews`)；
  - 桌面端 2x Retina Canvas 导出 / Android 原生位图捕获一键分享。

### 6. 🛡️ 全维度隐私安防套件
- **桌面端精简高效**：去除冗余伪装，专注极致纯粹的媒体档案管理。
- **移动端生物识别与安全锁**：支持 PIN 码与指纹/面容解锁，失焦自动模糊遮罩。
- **系统级物理绝缘**：Android 系统级 `FLAG_SECURE` 防录屏截屏，物理沙盒递归注入 `.nomedia`，彻底绝缘第三方相册扫描。

### 7. 原生进程内 LLM 大模型翻译引擎
- **纯 Rust 异步 TLS 核心**：零外部 Python 依赖，单句试译与批量翻译极速响应。
- **多模型支持与 Key 轮换池**：原生接入 Google Gemini（配额超限自动切换备用 Key）、OpenAI 协议（DeepSeek、Claude、Moonshot、Ollama 等）。
- **容错与标准化沉淀**：智能容纳纯中文正文，消除强行 JSON 解析报错；支持翻译数据标准 JSON 跨平台无损导入导出。

### 8. 资源检索与扩展插件系统 (v2.0)
- **矩阵化直达跳转**：一键跳转 BoyfriendTV、Google、各大影视库及 BT 磁力资源搜索。
- **PBC 维基刮削引擎**：1,200+ 演员深度抓取，增量修订侦测，25+ 属性比对（准确率 97.3%）。
- **SmutJunkies 刮削引擎**：6,700+ 演员全站索引，4 级高容错对齐算法，双向增量同步。

### 9. 界面国际化与极致轻量纯粹体验
- **7 国语言原生支持**：简体中文、繁體中文、English、Italiano、日本語、Español、Deutsch。
- **100% 词条对齐与多级回退**：每种语言维护 1,096 个词条，多级智能回退杜绝夹杂生硬字符。
- **状态双向联动与轻量架构**：菜单语言与数据内容语言解耦调度并双向联动；完全剥离虚拟成就与冗余画像插件，内存开销极致精简。

### 10. 通用数据跨端无缝备份与全平台在线升级
- **跨平台通用备份 (`GPDb_Backup.json`)**：一键导出/导入收藏、足迹、笔记、配置，打通 macOS、Windows、Android 换机迁移。
- **一键检查更新与断点续传**：集成 GitHub Releases API 版本比对与增量断点高速下载。

### 11. 全平台深度原生定制
- **macOS**：Tauri v2 + Rust 原生架构，适配 Apple Silicon / Intel，系统级毛玻璃与快捷键。
- **Windows**：原生 Windows 11 Mica / Acrylic 材质，浅色主题高对比度隔离，系统托盘常驻与 Jump List，任务栏刮削实时进度指示，WebView2 高刷 GPU 硬件加速。
- **Android**：Kotlin + Jetpack Compose + Room 原生打造，流畅手势触控与随身离线查阅。

---

## 🛠️ 技术架构 (Technology Stack)

```
┌──────────────────────────────────────┐   ┌──────────────────────────────────────┐   ┌──────────────────────────────────────┐
│       GPDb macOS (Universal)         │   │       GPDb Windows (x64)             │   │       GPDb Android (Mobile)          │
│   Vue 3 + Tauri v2 + Rust (gpdb-core)│   │   Vue 3 + Tauri v2 + Rust (gpdb-core)│   │    Kotlin + Jetpack Compose + Room   │
│         (desktop_client/)            │   │         (Windows_client/)            │   │         (android_client/)            │
│         [GPDb-macOS-*.dmg]           │   │       [GPDb-Windows-*.exe]           │   │       [GPDb-Android-*-signed.apk]    │
└──────────────────┬───────────────────┘   └──────────────────┬───────────────────┘   └──────────────────┬───────────────────┘
                   │                                          │                                          │
                   └──────────────────────────────────────────┴──────────────────────────────────────────┘
                                                              │
                                   ┌──────────────────────────▼──────────────────────────┐
                                   │              底层持久化存储 (Storage)               │
                                   │      GPDb.db (SQLite 3 WAL) + image_cache/          │
                                   └─────────────────────────────────────────────────────┘
```

---

## 🚀 快速上手与下载 (Quick Start)

### 用户下载安装（推荐）

直接前往本仓库的 [Releases 页面](https://github.com/GeavenMax/GPDb/releases) 下载最新 **`v2.18.0`** 正式安装包：

| 平台 | 安装包文件名 | 安装方式与说明 |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.18.0.dmg` | 双击挂载后将 `GPDb.app` 拖入 `Applications` 目录即可。<br>*(若提示未公证，可在「系统设置 → 隐私与安全性」点击「仍要打开」)* |
| **🪟 Windows** | `GPDb-Windows-v2.18.0.exe` | 双击安装程序即可运行。采用 NSIS 免提权单用户架构，无需管理员权限。 |
| **🤖 Android** | `GPDb-Android-v2.18.0-signed.apk` | 手机下载直接安装（官方正式私钥强签名；若系统提示请允许未知来源安装）。 |

---

### 💻 开发者编译指南 (Developer Guide)

```bash
# 1. macOS 桌面端
cd desktop_client && npm install && npm run tauri dev      # 本地调试
npm run tauri build                                         # 生产打包 (.app / .dmg)

# 2. Windows 桌面端
cd Windows_client && npm install && npm run tauri dev      # 本地调试
npm run tauri build                                         # 生产打包 (.exe)

# 3. Android 原生端
cd android_client && ./gradlew assembleRelease              # 编译签名 Release APK

# 4. 发版合规自动化预检
./git_tasks/pre_release_check.sh                            # 一键验证路径合规、隐私泄漏与版本对齐
```

> 💡 更多子系统架构参考：
> - 界面多语言规范：[`docs/i18n/UI_I18N_SPEC.md`](docs/i18n/UI_I18N_SPEC.md)
> - 片商超清 Logo 系统：[`studio_logos/README.md`](studio_logos/README.md)
> - 自动化发版全流程：[`git_tasks/README.md`](git_tasks/README.md)

---

## ⚖️ 法律声明与免责条款 (Disclaimer)

1. **软件定位**：本软件（GPDb）仅为**通用开源的离线成人媒体元数据本地索引与数据库管理工具**。
2. **内容免责**：本项目源代码及其发布版本中**不包含、不托管、不分发任何受版权保护的音视频文件、种子数据或图片素材**。软件中展示的示例字段仅用于数据库技术验证与界面排版测试。
3. **使用者责任**：用户管理本地数据库文件或跳转外部搜索所产生的一切行为及版权责任，均由使用者本人独立承担。
4. **合规遵守**：本项目仅面向达到法定成年年龄的用户。请在遵守当地法律法规的前提下合理、合法使用。

---

## 📄 开源许可证 (License)

本项目采用 [MIT 许可证](LICENSE) 开源。
