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
  <img src="https://img.shields.io/badge/Version-v2.17.0-orange?style=for-the-badge" alt="Version" />
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
- **高容錯多欄位聯合搜尋引擎**：
  - FTS5 高速召回 -> 異常自動降級至標準 SQL 多欄位聯合檢索 -> Room / SQLite 兜底，杜絕因分詞或缺少虛擬資料表造成的空結果。
  - 單次檢索同時跨越英文原名 (`title`)、中文譯名 (`title_zh`)、片商名 (`studio_name`)、導演名 (`director_name`)、中文簡介 (`description_zh`) 以及出演演員陣容。
- **多維度時間與入庫時效篩選**：
  - 支援「全部」、「上次入庫（按刮削時間）」、「最近7天發行」、「最近30天發行」、「最近90天發行」及「本年度」。
  - 影片與分集發行時間粒度精確分離（長片按年份，分集按具體日期）。
  - 新入庫條目展示動態漸變 `NEW` 高亮微標。
- **PBC 百科全維度資料整合**：
  演員檔案現已全面融合 Porn Base Central 維基百科資料——涵蓋**本名、出道年份、活躍狀態微標、星座族裔、表演風格標籤、維基生平人物小傳**，以及指向 IAFD / IMDb / X / OnlyFans / Instagram 等平台的**全網互聯檔案**連結，助力深度人物研究與交叉查詢。

### 2. 串流媒體級沉浸式視聽首頁與多封面平鋪畫廊
- **五大發現推薦流**：
  - **焦點巨幕海報輪播 (Hero Carousel)**：頂端高解析畫質海報平滑自動輪播，帶細膩視差互動。
  - **往年今日 · 經典首映 (On This Day)**：智慧對照歷史日曆，回顧黃金時代（80年代、90年代、千禧年代）同日首映的經典作品。
  - **今日星光 · 標誌面孔 (Star Spotlight)**：嚴格進行本地實體磁碟海報檔案校驗，智慧推薦擁有高解析頭像的標誌性面孔，杜絕字母佔位符。
  - **經典系列大放送 (Iconic Series)**：自動打撈作品跨度達十餘部曲的長篇標誌性 IP 系列，支援隨機抽取與一鍵換一批。
  - **隨心探索 · 盲盒發現 (Lucky Discovery)**：一鍵搖骰，從六萬餘部片庫中隨機挖掘冷門寶藏。
- **正封/封底多海報自適應平鋪與全螢幕縮放燈箱**：
  - **多圖海報自適應平鋪展示**：擁有正封面、封底寫真或多版本海報的作品，自適應橫向平鋪同時展現，配備 Front/Back 角標與一鍵喚起高解析燈箱。
  - **全螢幕縮放燈箱 (`ImageLightbox.vue` / Compose 縮放)**：支援滑鼠滾輪平滑縮放 (1.0x ~ 5.0x)、按住拖曳平移、按兩下智慧縮放/復位、鍵盤快速鍵 (+/-/0/Esc) 與行動端雙指捏合縮放。

### 3. 智慧系列影片集與封面拼圖 (Smart Series & Collage Covers)
- **自動分群演算法**：內建羅馬數字與副標題識別技術，自動將散落的系列影片（如 *Part I, Part II, Part III*）聚合為連貫篇章。
- **自適應拼貼藝術封面**：為系列作品自動生成 1 張單幅海報、2 張對稱拼圖、3 張階梯排布或 4 分格田字矩陣的藝術拼接封面，支援暗角光暈與離線極速渲染。
- **系列專屬收藏**：支援將喜愛的經典系列一鍵加入「收藏系列」獨立書籤，隨心追更。

### 4. 導演檔案卡與 180+ 廠牌深度圖鑑
- **導演專屬履歷**：影片詳情一鍵喚起執導導演專屬卡片，展示其歷史執導全量片單，並支援一鍵前往片庫按導演篩選。
- **廠牌專屬收錄與深度圖鑑**：
  - **181 家核心廠牌深度歷史檔案與介紹**：完整收錄各經典與現代製片廠牌創立年代、創始人背景、美學流派與文化演變深度檔案；
  - **片商官方 Logo 與橫幅全自動採集與呈現**：覆蓋 74 家核心廠牌高解析 Logo 與官方橫幅 Banner，自適應大畫幅高解析銳利呈現，無 Logo 廠牌優雅降級為雙首字母多維漸變識別標識；
  - **影片詳情頁重構**：劇情簡介移至海報下方通欄拓展，排版舒展易讀；核心操作按鈕矩陣化收攏排布。

### 5. 📸 截圖防窺模式與高顏值流光分享卡片 (v2.17.0 全新升級)
- **全域一鍵截圖防窺模式**：
  - 頂部導覽列提供一鍵「截圖防窺」眼睛開關；支援快速鍵與即時狀態切換（防窺中 / 正常瀏覽）。
  - **Android 探索首頁頂部一鍵防窺模式切換**：行動端在「探索首頁」頂部常駐快速眼睛防窺切換開關，單手輕觸即可瞬間脫敏。
  - **細粒度隱私脫敏控制**：
    - `模糊海報與劇照圖片`：全域高斯模糊 (`blur(24px)`) 所有影視封面、劇照、演職員頭像等敏感視覺畫面，杜絕截圖或社群媒體分享時洩漏畫面；
    - `模糊劇情介紹與敏感文字`：高斯模糊 (`blur(7px)`) 影視簡介、分集梗概等敏感文字並禁止文字選取，防止劇透與涉密內容外洩。
  - Android 行動端基於 `LocalPrivacyBlur` 響應式狀態流驅動，全螢幕秒級即時生效。
- **自適應海報流光分享卡片產生器**：
  - 影片與分集詳情一鍵生成 Apple Music / Spotify 級流光分享卡片。
  - **封面與封底雙海報並排展示**：長片支援自動提取或並排展示 Front / Back 封面與封底海報，兼顧視覺飽滿度與藝術收藏感。
  - **純淨分享體驗與官方頻道直達**：卡片右下角精緻嵌入官方 Telegram 頻道二維碼與標示（`t.me/gpdbnews`），移除底部冗餘生成日期，純粹保留作品編號。
  - **純離屏雙線性模糊與 16:9 分集居中裁切防拉伸**：採用離屏純演算法渲染背景模糊光暈，劇照與分集圖片實施智慧 16:9 居中無畸變裁切，徹底告別畫面拉伸變形。
  - 背景光暈直接從封面自適應提取色相並實施高精度大半徑高斯模糊 (`blur(45px~60px)`)，提供「流光 (Vibrant)」、「深黑 (Dark)」、「午夜 (Midnight)」三種專屬光暈預設。
  - **分享前安全隱私脫敏**：獨立勾選「模糊海報」與「模糊文字」，確保安全分享至社群媒體群聊與公開網路。
  - **雙端無損匯出**：
    - **桌面端**：基於離線 HTML5 Canvas 2D 引擎以 2x Retina 超高解析度光柵化，一鍵複製 PNG 至系統剪貼簿（微信/QQ/Telegram/Discord/X 直接貼上）或匯出儲存至本機。
    - **行動端**：採用 Compose 1.8 硬體加速點陣圖擷取引擎，支援一鍵無損儲存至系統相簿 (`MediaStore`)，並透過 `FileProvider` 喚起 Android 原生分享面板。

### 6. 🛡️ 全維度隱私安防套件 (Enterprise-Grade Privacy & Security)
- **桌面端精簡設計專注歸檔**：
  - 桌面客戶端全面精簡移除了過往的偽裝緊急計算機，專注回歸純粹、高效、極簡的離線影視媒體資產管理與檔案歸納。
- **行動端 PIN 碼與生物識別鎖定防護**：
  - Android 行動端支援設定獨立 4~6 位數字 PIN 碼應用程式鎖與指紋/面容生物識別解鎖，失焦或超時（1/5/15/30分鐘）自動覆蓋安全擬態鎖定畫面。
  - 視窗失焦或切換其他應用程式時瞬間覆蓋高斯毛玻璃防窺層。
- **行動端系統級物理絕緣**：
  - 接入 Android 系統級 `FLAG_SECURE` 防錄影、防截圖與防系統多工卡片預覽。
  - 物理沙盒儲存隔離，目錄遞迴注入 `.nomedia` 防護，徹底絕緣第三方相簿掃描。

### 7. 原生執行緒內 LLM 大型語言模型翻譯引擎與分集/長片雙語對照
- **純 Rust 原生執行緒內多大型語言模型引擎**：
  - 徹底擺脫外部 Python HTTP 服務依賴，在桌面端核心中原生實現非同步並發 TLS 請求通道，單句試譯與批次翻譯極速響應。
  - 原生接入 Google Gemini、OpenAI 協定生態（DeepSeek、Moonshot 月之暗面、通義千問、智譜清言、矽基流動、本機 Ollama）與 Anthropic Claude。
- **Google Gemini API Key 智慧輪換與配額保護**：
  - 內建原子級 API Key 輪換池，單 Key 觸發配額限制 (429) 時毫秒級自動切換備用 Key；多 Key 全量超額時智慧轉交備用服務商補救。
- **分集與長片全場景雙語對照與純文字解析**：
  - 無論是長片劇情還是獨立分集，均支援一鍵調用 AI 大型語言模型翻譯並寫入本地資料庫，支援中文譯文與英文原文一鍵切換對照。
  - 針對自填 API Key 深度最佳化，智慧容納大型語言模型直出的純中文內文，徹底消除強制 JSON 解析報錯；端點智慧自動補齊 `/v1`。
- **翻譯數據跨平台無損沉澱**：
  - 翻譯數據支援跨平台（macOS / Windows / Android）以標準 JSON 雙向無縫匯入匯出，零損沉澱。

### 8. 資源檢索與外部擴充外掛程式系統 (v2.0)
- **多站精準直達與版面配置緊湊化**：
  - 影片與演員頁面緊湊單列矩陣排布外部操作按鈕，一鍵精準跳轉 BoyfriendTV、Google 及各大權威影視資料庫，免去二次打字。
- **BT 磁力搜尋整合**：一鍵將電影原名與廠牌組合為標準搜尋式，直通外部資源引擎。
- **獨立自由開關**：外掛程式中心支援針對各個外部跳查源進行獨立細緻的啟閉配置。
- **增量刮削與同步引擎**：支援按需網路刮削更新與本地海報劇照離線快取。
- **PBC (Porn Base Central) 演員維基刮削引擎** (`scrape_pbc_actors.py`)：
  - 收錄 **1,200+ 位演員**，基於 MediaWiki API 執行全量深度爬取；
  - 內建**增量修訂偵測**機制，僅同步自上次執行以來發生變更的詞條，大幅降低頻寬消耗；
  - 橫跨 **25+ 屬性欄位**進行精細化比對（本名、出道年份、族裔、星座、活躍狀態、外部平台連結等）；
  - 實測演員姓名匹配準確率高達 **97.3%**。
- **SmutJunkies 演員刮削引擎** (`scrape_smutjunkies_actors.py`)：
  - 收錄 **6,700+ 位男同成人演員**，按 26 字母索引實現**全站無死角覆蓋**；
  - 採用 **4 級高容錯精準對齊演算法**（精確匹配 → 規範化模糊匹配 → 別名交叉核驗 → 人工審核佇列），確保跨資料來源的高置信度對齊；
  - 支援**雙向增量同步**：新增條目自動入庫，既有檔案欄位級差異更新，存量數據零損耗。

### 9. 全語言國際化與極致輕量化純粹體驗 (Internationalization & Streamlined UX)
- **7 種全介面可選語言**：簡體中文 (`zh-CN`)、繁體中文 (`zh-TW`)、English (`en`)、Italiano (`it`)、日本語 (`ja`)、Español (`es`)、Deutsch (`de`)。
- **極致輕量化精簡架構**：
  - 徹底移除 PlayStation 虛擬成就獎盃系統，消除冗餘計算開銷與無用程式碼；
  - 移除實驗性「AI 偏好畫像」外掛程式，外掛程式管理純粹聚焦於磁力搜尋、數據刮削與大型語言模型翻譯三大核心支柱；
  - 移除評星打分系統與繁複 3D 翻轉實體卡，大幅降低應用程式記憶體佔用與 GPU 渲染耗能。

### 10. 通用使用者資料跨端無縫備份與全平台客戶端一鍵升級 (Cross-Platform Backup & Seamless Update)
- **通用使用者資料與設定跨端無縫備份/遷移 (`GPDb_Backup.json`)**：
  - 提供標準統一的通用 JSON 備份架構，一鍵匯出與匯入所有個人收藏、觀影足跡、標記評分、大型語言模型 API 配置及系統偏好。
  - 徹底打通 macOS、Windows 與 Android 跨平台數據孤島，換機、重灌系統或跨裝置同步僅需單個備份檔案，瞬時無縫遷移。
- **全平台客戶端一鍵檢查更新與斷點續傳增量升級**：
  - 桌面端與行動端均內建全自動/手動版本偵測模組，即時比對最新 GitHub Releases 狀態並清晰展示版本更新日誌。
  - 深度整合斷點續傳高速下載引擎，下載完成後一鍵引導平滑升級與安裝，保持客戶端始終處於最新穩定狀態。

### 11. 全平台深度自訂與三端數據協同 (macOS / Windows / Android)
- **macOS 原生桌面端 (`desktop_client/`)**：透過 Tauri v2 + Rust 架構完美支援 Apple Silicon 與 Intel 架構，原生玻璃擬態與鍵盤快速鍵互動。
- **Windows 原生桌面端深度整合 (`Windows_client/`)**：
  - **原生 Mica / Acrylic 視窗材質與 Fluent Design**：視窗半透明融合桌面桌布，4 檔材質自由切換，淺色主題高對比度防發黑隔離適配；
  - **系統匣常駐 (System Tray) 與 Jump List 直達**：支援關閉時最小化到系統匣、系統匣右鍵快速切換防窺、常用板塊極速直達；
  - **工作列即時進度驅動 (Taskbar Progress)**：執行全量/增量網路刮削時，工作列圖示原生展現綠色進度條與百分比；
  - **WebView2 硬體加速調優**：注入高刷 GPU 光柵化參數，120Hz/144Hz 螢幕極速絲滑。
- **原生 Android 行動端 (`android_client/`)**：採用 Kotlin + Jetpack Compose + Room 現代化技術棧純原生建構，與桌面端數據模式無縫融合，滿足隨身隨時隨地離線查閱與打標收藏的需求。

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

直接前往本倉庫的 [Releases 頁面](https://github.com/GeavenMax/GPDb/releases) 下載最新 **`v2.17.0`** 正式安裝套件：

| 平台 | 安裝套件檔案名稱 | 安裝方式與說明 |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.17.0.dmg` | 按兩下掛載後將 `GPDb.app` 拖入 `Applications`（應用程式）資料夾即可。<br>*(首次開啟若提示未公證，可在「系統設定 → 隱私權與安全性」點擊「仍要開啟」，或在終端機執行 `sudo xattr -cr /Applications/GPDb.app`)* |
| **🪟 Windows** | `GPDb-Windows-v2.17.0.exe` | 按兩下安裝程式完成安裝。採用 NSIS 單使用者免提權架構，無需管理員權限，隨裝即用。 |
| **🤖 Android** | `GPDb-Android-v2.17.0-signed.apk` | 手機下載後直接點擊安裝（已通過官方公鑰強簽名，若系統提示允許未知來源安裝，請予以允許）。 |

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
