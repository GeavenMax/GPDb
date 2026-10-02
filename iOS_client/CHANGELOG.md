# Changelog (GPDb iOS Client)

All notable changes to the GPDb iOS Client will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.15.0] - 2026-10-01

### Added
- **iOS 客户端架构脚手架与基础架构文件 (`iOS_client/`)**：
  - 基于 SwiftUI 搭建现代声明式离线架构，支持 SQLite 原生读取、Zip 虚拟文件系统与系统级防截屏安全保护；
  - 接入 `GPDbDatabase` 原生数据仓储层，提供影视浏览、演员档案与用户收藏等高频功能。
- **全平台统一通用用户配置导入与导出支持 (`UserBackupService.swift`)**：
  - 支持全端统一标准 `gpdb_universal_backup` JSON 格式导出与导入；
  - 覆盖用户全量「我的收藏」清单、自定义想看/已看状态、标签标记、评分与观影探索统计指标；
  - 实现与 macOS、Windows 及 Android 客户端配置文件的 100% 双向互通与免密平滑迁移。
- **分享卡片角落新增 Telegram 官方频道二维码 (`ShareCardView.swift`)**：
  - 在卡片底部角落集成 27x27 高清点阵矢量二维码，直连官方频道 `https://t.me/gpdbnews`；
  - 具备独立白底圆角容器与高保真矢量绘制，适配 iPhone 与 iPad Retina 屏导出及预览。
- **分享卡片支持封面与封底双海报并列展示 (`ShareCardView.swift`)**：
  - 影视分享卡片全面支持同时加载正向海报封面与反面封底（如有）；
  - 采用并排自适应画框排版，带有独立「封面」与「封底」半透明质感角标，大幅提升典藏分享美感。
- **异步巡检 GitHub Releases 最新版本与更新服务 (`GitHubUpdateService.swift`)**：
  - 内置异步网络服务检测官方仓库最新版本，支持启动静默检测与手动触发版本检测，向用户反馈版本状态与更新日志。
- **全息演员档案整合与无损合并机制 (`PerformerDetailView.swift` & `PerformerRepository.swift`)**：
  - 深度整合 PBC 百科与 SmutJunkies 扩展档案，严格遵循 GEVI 优先的无损合并策略；
  - 提供独立曾用艺名 (AKA) 折叠卡片，超过 8 个自动折叠；
  - 聚合全网互联档案胶囊（IAFD、IMDb、X、OnlyFans、Instagram、PBC、SmutJunkies）。
- **外部数据库挂载前置自愈机制 (`DatabaseHolder.swift`)**：
  - 在挂载外部 SQLite 文件时，自动检测 `performers.pbc_url` 与 `sj_url` 并就地补全；
  - 自动创建 `performer_pbc_profiles`、`performer_sj_profiles`、`user_favorites` 与 `user_movie_data` 扩展表，彻底杜绝缺列崩溃。
- **收藏系列防崩溃复合唯一键规范 (`LibraryView.swift`)**：
  - 采用 `series_\(id)` 与 `\(studioName)_\(rootTitle)_\(hashValue)` 复合唯一键，彻底解决多厂牌重名系列触发 ForEach 重复键闪退的缺陷。

### Changed
- **单流式滚动架构重构 (`PerformerDetailView.swift`)**：
  - 对标 Android v2.15.0，彻底移除手势折叠与易被误隐藏的 Header 逻辑，采用单流式 `LazyVStack` + `LazyVGrid` 架构，将人物全息档案与分类 Tab 顶置于统一滚动流中，根治头部被网格掩盖缺陷。
- **连贯篇章系列大放送随机抽取展示 (`HomeFeedView.swift` & `HomeFeedRepository.swift`)**：
  - 首页「经典系列大放送 · 连贯篇章」由原有的固定按影片数排序优化为随机抽取（`ORDER BY RANDOM() LIMIT 10`），保持与桌面及 Android 客户端一致的动态探索体验。
- **今日星光动态面孔过滤 (`HomeFeedView.swift`)**：
  - 仅展示拥有有效头像的演员，过滤所有无头像占位项，提升首屏高级质感。
- **主页顶部快捷防窥开关与即时模糊 (`HomeFeedView.swift` & `PrivacyProtection.swift`)**：
  - 探索主页顶部导航栏新增防窥模式快捷图标按钮，支持一键在正常模式与防窥模糊模式间自由切换，并伴随触感反馈。

### Removed
- **彻底排除奖杯成就与伪装计算器模块**：
  - 恪守轻量纯粹原则，iOS 客户端原生架构不包含任何奖杯成就冗余插件；
  - 移除伪装计算器与紧急脱身冗余视图，专注于原生系统的安全生物识别与防截屏保护。
