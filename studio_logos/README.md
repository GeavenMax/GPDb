# 🎨 GPDb 片商 Logo & 横幅高清升级与徽章提取模块 (Studio Logos Engine)

本目录收录 GPDb 离线影视库的片商 Logo 与 Banner 自动化归集引擎、本地 40,000+ 高清实体封套包装角标提取工具（macOS 原生 Vision OCR）、自适应居中正方形重塑工具以及交互式审查控制台。

---

## 📂 目录结构与核心职责

```
studio_logos/
├── upgrade_studio_logos_hd.py         # 🚀 核心控制台引擎 (全量候选比对、生成 studio_logo_review.html、--apply 生产安全入库)
├── convert_studios_to_webp.py        # 🗜️ 全量生产素材 WebP 格式化与数据库同步工具 (保留透明通道与多帧动图)
├── extract_cover_logos_rapid.py      # 🧠 基于深度学习 RapidOCR 的高精封面定位、边缘加权与 512x512 方形自适应重构
├── extract_cover_logos_fuzzy.py      # 🔍 方案一：多词干别名模糊与发行标 (Presents/Video) 增强封面角标定位提取
├── extract_cover_logos_deep.py       # ⛏️ 方案二：下探至中长尾厂牌的多封面并发提取与自适应正方形填充
├── extract_cover_logos.py            # 📦 基础版实体封套包装切片引擎
├── ocr_detector                      # ⚡ macOS 原生 Vision 框架编译的超高速 OCR 文本与多边形定位工具
├── import_desktop_logos.py           # 🖥️ 桌面/外部高保真 Logo 导入工具 (自动尺寸分析与格式标准化)
├── import_waybig_studio_logos.py     # 🌐 渠道二：WayBig 垂直成人图床官方高清台标导入
├── import_pbc_studio_logos.py        # 🎞️ PBC 档案库官方厂牌标志探测与导入
├── scrape_studio_logos.py            # 🕷️ 早期官方网站与 Favicon/Apple-Touch-Icon 嗅探
├── scrape_remaining_studio_logos.py  # 🎯 剩余缺失厂牌定向网络补全探测器
├── expand_studio_logos_deep.py       # 🌊 深度多渠道扩展与排重工具
├── fetch_high_volume_square_logos.py # 🟩 头部大厂 1:1 正方形 Logo 专项采集器
├── finalize_top_studio_square_logos.py# 🏆 核心片商方形 Logo 质量验收与入库工具
├── cleanup_unused_studio_logos.py    # 🧹 孤儿素材与被舍弃候选清理工具
└── README.md                         # 📖 本模块说明文档
```

---

## ⚡ 核心能力与关键特性

1. **三维立体化归集渠道**：
   - **渠道一（官方主站与矢量嗅探）**：提取 Retina Apple-Touch-Icon (180x180 ~ 512x512)、SVG 与 OpenGraph 宽屏横幅；
   - **渠道二（CDN 垂直图床探测）**：归集 800×520 与 2076×757 4K 级官方台标与横幅；
   - **渠道三（本地 40,000+ 实体录像带封面提取）**：通过 macOS 原生 Swift `Vision` OCR 技术，毫秒级定位复古 VHS/DVD 封顶发行标（`Presents / Video / Productions`）与四角徽标，自适应色调补齐生成 1:1 正方形版本。
2. **零动图与物理完整性硬指标**：
   - 严格 100% 静态图片交付，杜绝 GIF 动态流光干扰；
   - 全量提取素材必须通过 PIL `verify()` 与 `load()` 双重物理无损校验，0 损坏、0 截断。
3. **可视化审查与非破坏性生产保护**：
   - 产出素材仅暂存于 `image_cache/Logos_candidates/`；
   - 用户在 `studio_logo_review.html` 审查控制台中自主指派【设为 Logo】或【设为 Banner】；
   - 支持一键导出并固化至 `approved_assignments.json`，执行 `--apply` 时自动对旧文件在 `image_cache/Logos_backup/` 进行时间戳备份。

---

## 🚀 常用运行指令

```bash
# 1. 重新生成并刷新可视化审查控制台
python3 studio_logos/upgrade_studio_logos_hd.py --render-html

# 2. 对未覆盖厂牌运行模糊多词干封套角标提取
python3 studio_logos/extract_cover_logos_fuzzy.py

# 3. 对中长尾厂牌运行深层图腾与自适应正方形增强提取
python3 studio_logos/extract_cover_logos_deep.py

# 4. 将用户在控制台中确认导出的方案正式应用入库到生产库
python3 studio_logos/upgrade_studio_logos_hd.py --apply
```
