# GPDb · 極速隱私優先的男同成人影視資料庫管理客戶端

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>專為男同成人影視愛好者打造的高性能、全維度檔案管理、絕對隱私與零雲端追蹤的現代化管理客戶端</strong>
</p>

<p align="center">
  <a href="./README.md">简体中文</a> |
  <a href="./README_EN.md">English</a> |
  <a href="./README_ZH_TW.md"><b>繁體中文</b></a> |
  <a href="./README_JA.md">日本語</a> |
  <a href="./README_DE.md">Deutsch</a> |
  <a href="./README_ES.md">Español</a> |
  <a href="./README_IT.md">Italiano</a>
</p>

<p align="center">
  <a href="https://t.me/gpdbnews" target="_blank"><img src="https://img.shields.io/badge/Telegram-Channel%20%40gpdbnews-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Channel" /></a>
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Android-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **官方 Telegram 頻道**：歡迎訂閱 [GPDb 官方 Telegram 頻道 (https://t.me/gpdbnews)](https://t.me/gpdbnews)，獲取最新的版本發布動態、資料庫更新通知與使用技巧！

---

## 📖 專案定位與簡介 (About GPDb)

**GPDb**（Gay Pornography Database Manager）是一款專門面向**男同成人影視（Gay Adult Media）**愛好者、數位媒體收藏家與影視資料研究者打造的**現代化全功能離線資料庫管理客戶端**。

在成人媒體領域，愛好者的個人觀影紀錄、收藏清單與性向偏好屬於**最核心、最敏感的個人隱私**。商業串流平台或中心化在線服務普遍存在帳號追蹤、數據分析洩漏、版權到期下架等不可控風險。

**GPDb 堅持「100% 本地優先（Offline-First）與絕對私密無痕」的核心設計哲學**：
- **零雲端依賴**：所有的媒體元數據、演職員身體檔案、海報/劇照快取、個人標籤評分及收藏足跡均完全留存在使用者自己的設備物理磁碟中；
- **純單機架構**：不設任何帳號體系、無任何遙測追蹤程式碼、無任何外部伺服器上傳；
- **極致效能保障**：基於高性能 **Rust 引擎 (`gpdb-core`)** 與 **Tauri v2 + Vue 3** 現代架構，面對 **60,000+ 部完整影片、100,000+ 獨立分集、6,000+ 演員身體檔案與 1,300+ 經典與現代製片廠牌**，仍能實現 60fps 絲滑渲染與毫秒級即時檢索。

---

## ✨ 緊扣領域特性的核心亮點 (Domain-Specific Features)

### 1. 全維度演員身體屬性與特徵深度檢索
- **極細粒度的生理與體貌屬性篩選**：
  支援按**體型身段（Build）**（如 Muscle / Twink / Bear / Hunk）、**髮色（Hair）**、**瞳色（Eyes）**、**鬍鬚與體毛豐度（Facial Hair / Body Hair）**、**身高體重（Height / Weight）**、**膚色（Skin）**、**生理特徵尺寸（Dick Size / Foreskin）**、**紋身穿孔（Tattoos）** 等多維特徵組合過濾。
- **藝名與曾用名全息對齊**：
  同一位演員在不同製片廠牌、不同年代的演出藝名或別名（Aliases）自動智慧聚合，避免由於藝名變動導致漏看。
- **正片（Films）與分集（Scenes）精準區分**：
  支援單獨檢視演員作為正片主角的完整影視作品，亦可獨立檢視其作為單集客串的獨立短劇，參演篇目一覽無餘。

### 2. 串流級沉浸式視聽主頁 (Home Feed)
- **焦點巨幕海報輪播**：頂端高解析度畫質海報平滑自動輪播，帶細膩視差互動。
- **往年今日 · 經典首映**：智慧對照歷史日曆，回顧黃金時代（80年代、90年代、千禧年代）同日首映的經典作品。
- **今日星光 · 標誌面孔**：嚴格進行本地物理磁碟海報檔案校驗，智慧推薦擁有高解析度頭像的標誌性面孔，杜絕字母占位符。
- **經典系列大放送**：自動打撈作品跨度達十餘部曲的長篇標誌性 IP 系列。
- **隨心探索 · 盲盒發現**：一鍵搖骰，從六萬餘部片庫中隨機挖掘冷門寶藏。

### 3. 智慧系列影片集與封面拼圖 (Smart Series & Collage Covers)
- **自動分群演算法**：內建羅馬數字與副標題識別技術，自動將散落的系列影片（如 *Part I, Part II, Part III*）聚合為連貫篇章。
- **自適應拼貼藝術封面**：為系列作品自動生成 1 張單幅海報、2 張對稱拼圖、3 張階梯排布或 4 分格田字矩陣的藝術拼接封面，支援暗角光暈與離線極速渲染。

### 4. 導演檔案卡與廠牌流派圖鑑
- **導演專屬履歷**：影片詳情一鍵喚起執導導演專屬卡片，展示其歷史執導全量片單，並支援一鍵前往片庫按導演篩選。
- **廠牌專屬收錄**：覆蓋各大經典膠片製片廠牌（Falcon, Colt, Catalina 等）至現代主流廠牌（Men.com, BelAmi, Lucas Entertainment, Corbin Fisher 等），作品編年史與風格標籤一覽無餘。

### 5. AI 影迷偏好洞察與審美畫像 (AI Persona Insights)
- **全非同步無感架構**：後台獨立執行緒排程本地/遠端大語言模型（如 DeepSeek、OpenAI、Claude 等），徹底消除介面卡頓。
- **深度審美肖像長文**：基於使用者的真實私密收藏與打標足跡，剖析其審美基因，生成包含「核心審美原型代號」（如「新浪潮懷舊探索者」）、「時代視聽光譜」的 2000 字深度藝術鑑賞報告。
- **即時進度彈窗**：高奢毛玻璃懸浮面板，分步展示大模型加密交握、足跡特徵提取與畫像生成進度。

### 6. 資源檢索與外部擴充外掛程式系統 (v2.0)
- **多站精準直達**：在影片與演員頁面，支援一鍵跳轉 BoyfriendTV、Google 及各大權威影視資料庫，使用條目原英文名精準跳轉，免去二次打字。
- **BT 磁力搜尋整合**：一鍵將電影原名與廠牌組合為標準搜尋式，直通外部資源引擎。
- **獨立自由開關**：外掛程式中心支援針對各個外部跳查源進行獨立細緻的啟閉配置。

### 7. 全語言國際化與遊戲化成就系統
- **7 種全介面可選語言**：簡體中文 (`zh-CN`)、繁體中文 (`zh-TW`)、English (`en`)、Italiano (`it`)、日本語 (`ja`)、Español (`es`)、Deutsch (`de`)。
- **PlayStation 風格成就獎盃**：內建數十種探索、收藏與檢索成就，解鎖時觸發 PSN 風格流體毛玻璃動效彈窗。

---

## 🛠️ 技術架構 (Technology Stack)

```
┌─────────────────────────────────────────────────────────────┐
│                    GPDb 前端表現層 (UI)                     │
│  Vue 3 + Vite + TypeScript + Tailwind CSS + Lucide Icons   │
│       (響應式網格佈局、毛玻璃特效、國際化 i18n、狀態管理)     │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Tauri IPC / 高速二進位通道)
┌──────────────────────────────▼──────────────────────────────┐
│                    Tauri v2 宿主適配層                      │
│     自訂通訊協定 (gpdb-img://)、系統視窗控制、安全檔案選擇器   │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Rust Native 介面)
┌──────────────────────────────▼──────────────────────────────┐
│                 Rust 核心服務層 (gpdb-core)                 │
│ • SQL 查詢生成器與模糊索引 (rusqlite)                         │
│ • 多級本地圖片物理校驗與安全解析 (Cache Resolver)             │
│ • 非同步大模型互動通道 (Async Runtime)                       │
│ • 自動化 Schema 遷移與自癒引擎 (Migrate Engine)              │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    底層持久化存儲 (Storage)                 │
│      GPDb.db (SQLite 3 WAL 模式) + image_cache/ (本地圖片)  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 快速上手 (Quick Start)

### 一般使用者（推薦）

直接前往本倉庫的 [Releases 頁面](https://github.com/GeavenMax/GPDb/releases) 下載已打包編譯完成的安裝套件：
- **macOS**：下載 `GPDb-macOS-v2.6.0.dmg`，按兩下後將 `GPDb.app` 拖入 `Applications`（應用程式）即可啟動。
- **Android**（獨立專案開發中）：下載 `.apk` 安裝套件直接安裝於手機或平板。

### 開發者本地編譯與執行

#### 環境要求
- Node.js 20+ 及 npm
- Rust 1.78+ (`cargo`)
- macOS 12+ (支援 Apple Silicon M 系列及 Intel 架構)

#### 執行步驟
```bash
# 1. 複製程式碼倉庫
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. 進入桌面客戶端目錄
cd desktop_client

# 3. 安裝前端依賴
npm install

# 4. 以開發模式啟動桌面客戶端
npm run tauri dev

# 5. 建置正式生產版應用程式 (自動打包 .app 與 .dmg)
npm run tauri build
```

---

## ⚖️ 法律聲明與免責條款 (Disclaimer)

1. **軟體定位**：
   本軟體（GPDb）僅為一款**通用開源的離線成人媒體元數據本地索引與資料庫管理工具（Universal Offline Adult Media Metadata & Library Management Tool）**。
2. **內容免責**：
   本專案原始碼及其發佈版本中**不包含、不代管、不散布任何受版權保護的影音檔案、種子數據或圖片素材**。軟體中展示的範例欄位僅用於資料庫技術驗證與介面排版測試。
3. **使用者責任**：
   使用者使用本軟體管理其個人的本地資料庫檔案、或使用外部搜尋跳轉功能所產生的一切行為及版權合規責任，均由使用者本人獨立承擔，與本軟體開發者及開源貢獻者無關。
4. **法規遵循**：
   本專案僅面向達到法定成年年齡的使用者。請在遵守您所在國家和地區相關法律法規的前提下合理、合法使用本開源工具。

---

## 📄 開源許可證 (License)

本專案採用 [MIT 許可證](LICENSE) 開源。您可以自由閱讀、修改、分發或整合本專案程式碼，唯須在副本中保留原作者版權資訊與本免責聲明。
