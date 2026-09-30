# GPDb · 極速隱私優先的男同成人影視資料庫管理客戶端

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>專為男同成人影視愛好者打造的高性能、全維度檔案管理、絕對隱私與零雲端追蹤的現代化跨平台管理客戶端</strong>
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
  <img src="https://img.shields.io/badge/Version-v2.14.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **官方 Telegram 頻道**：歡迎訂閱 [GPDb 官方 Telegram 頻道 (https://t.me/gpdbnews)](https://t.me/gpdbnews)，第一時間獲取最新的版本發布動態、資料庫增量更新通知與使用技巧！

---

## 📖 專案定位與簡介 (About GPDb)

**GPDb**（Gay Pornography Database Manager）是一款專門面向**男同成人影視（Gay Adult Media）**愛好者、數位媒體收藏家與影視資料研究者打造的**現代化全功能離線資料庫管理客戶端**。

在成人媒體領域，愛好者的個人觀影紀錄、收藏清單與性向偏好屬於**最核心、最敏感的個人隱私**。商業串流媒體或中心化在線平台普遍存在帳號追蹤、數據分析洩漏、版權到期下架等不可控風險。

**GPDb 堅持「100% 本地優先（Offline-First）與絕對私密無痕」的核心設計哲學**：
- **零雲端依賴**：所有的媒體元數據、演職員身體檔案、海報/劇照快取、個人標籤評分及收藏足跡均完全留存在使用者自己的設備物理磁碟中；
- **純單機架構**：不設任何帳號體系、無任何遙測追蹤程式碼、無任何外部伺服器上傳；
- **極致效能保障**：基於高性能 **Rust 引擎 (`gpdb-core`)** 與 **Tauri v2 + Vue 3**（macOS / Windows 桌面端）及 **Kotlin + Jetpack Compose + Room**（Android 原生行動端），面對 **60,000+ 部完整影片、100,000+ 獨立分集、6,000+ 演員身體檔案與 1,300+ 經典與現代製片廠牌**，仍能實現 60fps 絲滑渲染與毫秒級即時檢索。

---

## ✨ 緊扣領域特性的核心亮點 (Domain-Specific Features)

### 1. 全維度演員身體屬性與特徵深度檢索
- **極細粒度的生理與體貌屬性篩選**：
  支援按**體型身段（Build）**（如 Muscle / Twink / Bear / Hunk）、**髮色（Hair）**、**瞳色（Eyes）**、**鬍鬚與體毛豐度（Facial Hair / Body Hair）**、**身高體重（Height / Weight）**、**膚色（Skin）**、**生理特徵尺寸（Dick Size / Foreskin）**、**紋身穿孔（Tattoos）** 等多維特徵組合過濾。
- **藝名與曾用名全息對齊**：
  同一位演員在不同製片廠牌、不同年代的演出藝名或別名（Aliases）自動智慧聚合，避免由於藝名變動導致漏看。
- **正片（Films）與分集（Scenes）精準區分**：
  支援單獨檢視演員作為正片主角的完整影視作品，亦可獨立檢視其作為單集客串的獨立短劇，參演篇目一覽無餘。
- **PBC 維基全量資料整合**：
  演員個人檔案現已深度整合 PBC (Porn Base Central) 維基百科資料，完整呈現：本名、出道年份、活躍狀態微標、星座族裔、表演風格標籤、維基生平人物小傳，以及跨平台全網互聯檔案（IAFD / IMDb / X / OnlyFans / Instagram），讓每一份演員檔案資料更立體、更全面。
- **高容錯多欄位聯合搜尋引擎**：
  - FTS5 高速召回 -> 異常自動降級至標準 SQL 多欄位聯合檢索 -> Room / SQLite 兜底，杜絕因分詞或缺少虛擬資料表造成的空結果。
  - 單次檢索同時跨越英文原名 (`title`)、中文譯名 (`title_zh`)、片商名 (`studio_name`)、導演名 (`director_name`)、中文簡介 (`description_zh`) 以及出演演員陣容。
- **多維度時間與入庫時效篩選**：
  - 支援「全部」、「上次入庫（按刮削時間）」、「最近7天發行」、「最近30天發行」、「最近90天發行」及「本年度」。
  - 影片與分集發行時間粒度精確分離（長片按年份，分集按具體日期）。
  - 新入庫條目展示動態漸變 `NEW` 高亮微標。

### 2. 串流媒體級沉浸式視聽主頁與 3D 典藏卡片
- **五大發現推薦流**：
  - **焦點巨幕海報輪播 (Hero Carousel)**：頂端高清畫質海報平滑自動輪播，帶細膩視差互動。
  - **往年今日 · 經典首映 (On This Day)**：智慧對照歷史日曆，回顧黃金時代（80年代、90年代、千禧年代）同日首映的經典作品。
  - **今日星光 · 標誌面孔 (Star Spotlight)**：嚴格進行本地物理磁碟海報檔案校驗，智慧推薦擁有高清頭像的標誌性面孔，杜絕字母占位符。
  - **經典系列大放送 (Iconic Series)**：自動打撈作品跨度達十餘部曲的長篇標誌性 IP 系列。
  - **隨心探索 · 盲盒發現 (Lucky Discovery)**：一鍵搖骰，從六萬餘部片庫中隨機挖掘冷門寶藏。
- **海報雙排版方案與全螢幕手勢/滾輪縮放燈箱**：
  - 支援**自適應高清畫廊 (`adaptive_pager`)** 與 **3D 擬真實體卡片 (`flip_3d`)**：具備 60fps CSS 3D 景深物理透視、實體翻轉膠囊按鍵與典藏環境泛光。
  - **全螢幕縮放燈箱 (`ImageLightbox.vue` / Compose 縮放)**：支援滑鼠滾輪平滑縮放 (1.0x ~ 5.0x)、按住拖曳平移、雙擊智慧縮放/復位、鍵盤快捷鍵 (+/-/0/Esc) 與行動端雙指捏合縮放。

### 3. 智慧系列影片集與封面拼圖 (Smart Series & Collage Covers)
- **自動分群演算法**：內建羅馬數字與副標題識別技術，自動將散落的系列影片（如 *Part I, Part II, Part III*）聚合為連貫篇章。
- **自適應拼貼藝術封面**：為系列作品自動生成 1 張單幅海報、2 張對稱拼圖、3 張階梯排布或 4 分格田字矩陣的藝術拼接封面，支援暗角光暈與離線極速渲染。
- **系列專屬收藏**：支援將喜愛的經典系列一鍵加入「收藏系列」獨立書籤，隨心追更。

### 4. 導演檔案卡與廠牌流派圖鑑
- **導演專屬履歷**：影片詳情一鍵喚起執導導演專屬卡片，展示其歷史執導全量片單，並支援一鍵前往片庫按導演篩選。
- **廠牌專屬收錄**：覆蓋各大經典膠片製片廠牌（Falcon, Colt, Catalina 等）至現代主流廠牌（Men.com, BelAmi, Lucas Entertainment, Corbin Fisher 等），作品編年史與風格標籤一覽無餘。

### 5. 📸 截圖防窺模式與高顏值流光分享卡片 (v2.13.0 全新升級)
- **全域一鍵截圖防窺模式**：
  - 頂部導覽列提供一鍵「截圖防窺」眼睛開關；支援快捷鍵與即時狀態切換（防窺中 / 正常瀏覽）。
  - **細粒度隱私脫敏控制**：
    - `模糊海報與劇照圖片`：全域高斯模糊 (`blur(24px)`) 所有影視封面、劇照、演職員頭像等敏感視覺畫面，杜絕截圖或社群媒體分享時洩漏畫面；
    - `模糊劇情介紹與敏感文字`：高斯模糊 (`blur(7px)`) 影視簡介、分集梗概等敏感文字並禁止文字選取，防止劇透與涉密內容外洩。
  - Android 行動端基於 `LocalPrivacyBlur` 響應式狀態流驅動，全螢幕秒級即時生效。
- **自適應海報流光分享卡片產生器**：
  - 影片與分集詳情一鍵生成 Apple Music / Spotify 級流光分享卡片。
  - 背景光暈直接從封面自適應提取色相並實施高精度大半徑高斯模糊 (`blur(45px~60px)`)，提供「流光 (Vibrant)」、「深黑 (Dark)」、「午夜 (Midnight)」三種專屬光暈預設。
  - **分享前安全隱私脫敏**：獨立勾選「模糊海報」與「模糊文字」，確保安全分享至社交群聊與公開網路。
  - **雙端無損匯出**：
    - **桌面端**：基於離線 HTML5 Canvas 2D 引擎以 2x Retina 超高清解析度光柵化，一鍵複製 PNG 至系統剪貼簿（微信/QQ/Telegram/Discord/X 直接貼上）或匯出儲存至本地。
    - **行動端**：採用 Compose 1.8 硬體加速點陣圖擷取引擎，支援一鍵無損儲存至系統相簿 (`MediaStore`)，並透過 `FileProvider` 喚起 Android 原生分享面板。

### 6. 🛡️ 全維度隱私安防套件 (Enterprise-Grade Privacy & Security)
- **PIN 碼數字鎖定與失焦防護**：
  - 支援設定獨立 4~6 位數字 PIN 碼應用程式鎖，失焦或超時（1/5/15/30分鐘）自動覆蓋玻璃擬態鎖定畫面 (`AppLockOverlay.vue`)。
  - 視窗失焦或切換其他應用程式時瞬間覆蓋高斯毛玻璃防窺層。
- **緊急一鍵脫身偽裝計算機 (Panic Switch)**：
  - 極速切換為 Apple 風格真實可用暗黑計算機 (`FakeCalculatorModal.vue`)，支援真實四則運算。
  - 桌面端支援快捷鍵 <kbd>Cmd + Shift + P</kbd> 極速呼出；行動端支援手勢（螢幕翻轉反扣）極速喚出。
  - 在計算機中輸入正確 PIN 碼並按 `=`，或連擊頂部標題 4 次即可安全解鎖重返影庫。
- **無害化應用程式標題與桌面圖示偽裝**：
  - 支援自訂視窗標題（如 "Calculator"、"Notes"），防止系統工作切換器暴露。
  - 內建多套高端設計主題圖示（如「雙雄火星圖騰」、「黑曜石膠片之匣」）及無害偽裝圖示（便利貼、記帳、計算機）。
- **行動端系統級物理絕緣**：
  - 接入 Android 系統級 `FLAG_SECURE` 防錄影、防截圖與防系統多工卡片預覽。
  - 物理沙盒儲存隔離，目錄遞迴注入 `.nomedia` 防護，徹底絕緣第三方相簿掃描。

### 7. AI 影迷偏好洞察與大模型多語言翻譯
- **AI 影迷偏好洞察與審美畫像 (AI Persona Insights)**：
  - **全非同步無感架構**：後台獨立執行緒排程本地/遠端大型語言模型（如 DeepSeek、OpenAI、Claude 等），徹底消除介面假死。
  - **深度審美肖像長文**：基於使用者的真實私密收藏與打標足跡，剖析其審美基因，生成包含「核心審美原型代號」（如「新浪潮懷舊探索者」）、「時代視聽光譜」的 2000 字深度藝術鑑賞報告。
  - **即時進度彈窗**：高奢磨砂玻璃懸浮面板，分步展示大模型加密交握、足跡特徵提取與畫像生成進度。
- **大模型多語言翻譯與跨平台數據沉澱**：
  - 原生接入 OpenAI、DeepSeek、Claude、Gemini、字節跳動豆包 (Doubao) 等各大服務商。
  - 支援「探測可用模型」一鍵線上檢測 API 可用模型清單。
  - 支援翻譯數據跨平台（macOS / Windows / Android）以標準 JSON 雙向無縫匯入匯出，零損沉澱。

### 8. 資源檢索與外部擴充外掛程式系統 (v2.0)
- **多站精準直達**：在影片與演員頁面，支援一鍵跳轉 BoyfriendTV、Google 及各大權威影視資料庫，使用條目原英文名精準跳轉，免去二次打字。
- **BT 磁力搜尋整合**：一鍵將電影原名與廠牌組合為標準搜尋式，直通外部資源引擎。
- **獨立自由開關**：外掛程式中心支援針對各個外部跳查源進行獨立細緻的啟閉配置。
- **增量刮削與同步引擎**：支援按需網路刮削更新與本地海報劇照離線快取。
- **PBC (Porn Base Central) 演員維基刮削引擎** (`scrape_pbc_actors.py`)：
  - 涵蓋 **1,200+ 位演員**的 MediaWiki 全量深度爬取，採用增量修訂偵測機制，僅抓取自上次同步後有修訂的條目，大幅節省頻寬與時間；
  - **25+ 屬性欄位**精準比對，包含本名、出道年份、活躍狀態、星座族裔、表演風格等核心維度；
  - 整體演員條目匹配率高達 **97.3%**，確保資料庫資訊的完整性與準確性。
- **SmutJunkies 演員刮削引擎** (`scrape_smutjunkies_actors.py`)：
  - 專為 **6,700+ 位男同成人演員**設計的全站覆蓋方案，完整走訪 **26 個字母索引**頁面，零遺漏收錄；
  - 內建 **4 級高容錯精準對齊演算法**，逐層由嚴謹到寬鬆遞退，有效應對藝名拼寫變體、空格差異等噪訊干擾；
  - 支援**雙向增量同步**：新增演員自動入庫，既有條目僅在資訊有更新時才觸發覆寫，確保本地資料與上游資料源持續保持一致。

### 9. 全語言國際化與 77+ 遊戲化成就獎盃系統
- **7 種全介面可選語言**：簡體中文 (`zh-CN`)、繁體中文 (`zh-TW`)、English (`en`)、Italiano (`it`)、日本語 (`ja`)、Español (`es`)、Deutsch (`de`)。
- **PlayStation 風格成就獎盃系統**：
  - 內建 77+ 項探索、檢索、專注與收藏成就，解鎖時觸發 PSN 風格流體動效彈窗。
  - 基於真實行為指標追蹤，支援獨立的獎盃數據重置與核定管理。

### 10. 全平台覆蓋與三端數據協同 (macOS / Windows / Android)
- **macOS 原生桌面端 (`desktop_client/`)**：透過 Tauri v2 + Rust 架構完美支援 Apple Silicon 與 Intel 架構，原生玻璃擬態與鍵盤快捷鍵互動。
- **Windows 原生桌面端 (`Windows_client/`)**：專有獨立目錄解耦，內建多路徑 Python 智慧解析引擎、主控台黑框抑制 (`CREATE_NO_WINDOW`)、微軟正黑體字型平滑最佳化、細緻捲軸、Win11 分割貼靠與 NSIS 免提權單使用者輕量化安裝。
- **原生 Android 行動端 (`android_client/`)**：採用 Kotlin + Jetpack Compose + Room 現代化技術棧純原生建構，與桌面端資料模式無縫融合，滿足隨身隨時隨地離線查閱與打標收藏的需求。

---

## 🛠️ 技術架構 (Technology Stack)

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
                                   │              底層持久化儲存 (Storage)               │
                                   │      GPDb.db (SQLite 3 WAL) + image_cache/          │
                                   └─────────────────────────────────────────────────────┘
```

---

## 🚀 快速上手與下載 (Quick Start)

### 一般使用者下載安裝（推薦）

直接前往本倉庫的 [Releases 頁面](https://github.com/GeavenMax/GPDb/releases) 下載最新 **`v2.14.0`** 正式安裝套件：

| 平台 | 安裝套件檔案名稱 | 安裝方式與說明 |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.14.0.dmg` | 按兩下掛載後將 `GPDb.app` 拖入 `Applications`（應用程式）資料夾即可。<br>*(首次開啟若提示未公證，可在「系統設定 → 隱私權與安全性」點擊「仍要開啟」，或在終端機執行 `sudo xattr -cr /Applications/GPDb.app`)* |
| **🪟 Windows** | `GPDb-Windows-v2.14.0.exe` | 按兩下安裝程式完成安裝。採用 NSIS 單使用者免提權架構，無需管理員權限，隨裝即用。 |
| **🤖 Android** | `GPDb-Android-v2.14.0-signed.apk` | 手機下載後直接點擊安裝（已通過官方公鑰強簽名，若系統提示允許未知來源安裝，請予以允許）。 |

---

### 💻 開發者本地編譯與執行指南 (Developer Guide)

#### 環境要求
- **Node.js**：20+ 及 npm
- **Rust**：1.78+ (`rustup`、`cargo`)
- **Android SDK**（僅 Android 端需要）：Android Studio Hedgehog+ 或 JDK 17+

#### 1. macOS 桌面端開發與建置
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

#### 2. Windows 桌面端開發與建置
```powershell
# 1. 進入 Windows 獨立客戶端目錄
cd Windows_client

# 2. 安裝前端依賴
npm install

# 3. 以開發模式啟動
npm run tauri dev

# 4. 建置正式生產版安裝套件 (產生 NSIS .exe 安裝程式)
npm run tauri build

# 或直接執行開箱即用打包腳本：
.\build_windows.ps1
```

#### 3. Android 原生行動端編譯
```bash
# 1. 進入 Android 原生端目錄
cd android_client

# 2. 編譯 Debug 測試包
./gradlew assembleDebug

# 3. 編譯正式簽名 Release APK
./gradlew assembleRelease
```

---

## ⚖️ 法律聲明與免責條款 (Disclaimer)

1. **軟體定位**：
   本軟體（GPDb）僅為一款**通用開源的離線成人媒體元數據本地索引與資料庫管理工具（Universal Offline Adult Media Metadata & Library Management Tool）**。
2. **內容免責**：
   本專案原始碼及其發布版本中**不包含、不代管、不散布任何受版權保護的影音檔案、種子數據或圖片素材**。軟體中展示的範例欄位僅用於資料庫技術驗證與介面排版測試。
3. **使用者責任**：
   使用者使用本軟體管理其個人的本地資料庫檔案、或使用外部搜尋跳轉功能所產生的一切行為及版權合規責任，均由使用者本人獨立承擔，與本軟體開發者及開源貢獻者無關。
4. **法規遵循**：
   本專案僅面向達到法定成年年齡的使用者。請在遵守您所在國家和地區相關法律法規的前提下合理、合法使用本開源工具。

---

## 📄 開源許可證 (License)

本專案採用 [MIT 許可證](LICENSE) 開源。您可以自由閱讀、修改、分發或整合本專案程式碼，唯須在副本中保留原作者版權資訊與本免責聲明。
