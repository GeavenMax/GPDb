# GPDb · 極速隱私優先的男同成人影視資料庫管理客戶端

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>專為男同成人影視愛好者打造的高效能、全維度檔案管理、絕對隱私與零雲端追蹤的現代化跨平台管理客戶端</strong>
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
  <img src="https://img.shields.io/badge/Version-v2.19.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **官方頻道**：訂閱 [GPDb 官方 Telegram 頻道 (@gpdbnews)](https://t.me/gpdbnews)，第一時間獲取版本發布、資料庫增量更新與使用技巧！

---

## 📖 專案定位 (About GPDb)

**GPDb**（Gay Pornography Database Manager）是專為**男同成人影視（Gay Adult Media）**愛好者與收藏家打造的**現代化全功能離線資料庫管理系統**。

**堅持 100% 本地優先（Offline-First）與絕對私密無痕**：
- **零雲端依賴**：媒體元數據、演員身體檔案、海報快取及個人標記完全儲存於本地實體磁碟；
- **純單機架構**：無使用者帳號體系、無數據遙測追蹤、無外部伺服器上傳；
- **原生級強悍效能**：基於 **Rust 高效能核心 (`gpdb-core`)** + **Tauri v2 + Vue 3**（macOS / Windows）及 **Kotlin + Jetpack Compose + Room**（Android 原生），面對 **63,000+ 部長片、134,000+ 分集、108,000+ 演員與 2,400+ 廠牌**，保持 60fps 絲滑渲染與毫秒級瞬時檢索。

---

## ✨ 核心特色亮點 (Core Highlights)

### 1. 全維度演員身體檔案與高容錯聯合檢索
- **細粒度體貌篩選**：支援體型身段（Build）、髮色、瞳色、體毛豐度、身高體重、膚色、生理尺寸（Dick Size / Foreskin）與刺青穿孔組合過濾。
- **藝名 (AKA) 智慧聚合**：自動歸納跨廠牌、跨年代的多重曾用藝名，杜絕遺漏。
- **正片與獨立分集精準解耦**：清晰區分主角長片與客串短劇場景。
- **高容錯聯合搜尋**：FTS5 高速索引 + SQL 多欄位兜底，支援中英雙語、廠牌、導演、簡介與演員多維度聯合檢索。
- **PBC 百科全維度整合**：整合 Porn Base Central 百科檔案（本名、出道年份、活躍狀態、生平小傳及 IAFD/IMDb/X 互聯檔案）。

### 2. 沉浸式串流首頁與多封面平鋪畫廊
- **五大發現推薦流**：焦點巨幕海報輪播、往年今日經典首映、今日星光標誌面孔、經典系列大放送及隨心探索盲盒。
- **多封面平鋪畫廊**：正向封面、封底寫真及多版本海報橫向自適應平鋪展示，配備 Front/Back 角標。
- **全螢幕縮放燈箱**：支援滑鼠滾輪平滑縮放 (1.0x ~ 5.0x)、平移拖曳、按兩下縮放及行動端雙指手勢。

### 3. 智慧系列影片集與封面拼貼
- **自動分群演算法**：智慧識別羅馬數字與副標題，自動將系列作品歸攏成套。
- **自適應藝術拼貼封面**：自動生成 1~4 圖對稱或矩陣式藝術拼貼海報，離線極速渲染。

### 4. 導演檔案卡與 2,400+ 廠牌超清圖鑑
- **導演專屬履歷**：一鍵喚起執導作品集，支援全庫作品快速聯動篩選。
- **廠牌 Logo & 橫幅 100% 覆蓋**：全庫 2,490+ 廠牌已 100% 配齊高畫質 Logo（WebP 離線直傳），690+ 廠牌收錄官方寬螢幕 Banner；作品 Logo 覆蓋率達 98%。
- **舒適詳情版面配置**：劇情簡介通欄展開易讀，核心操作按鈕矩陣化收攏。

### 5. 📸 截圖防窺模式與流光分享卡片 (v2.19.0)
- **全域一鍵截圖防窺**：桌面頂欄眼睛開關 / Android 探索首頁頂部快捷開關，瞬間觸發海報高斯模糊 (`blur(24px)`) 與劇情文字脫敏 (`blur(7px)`)，杜絕外洩。
- **流光雙封面分享卡片**：
  - 支援正反雙封面並排排版、16:9 分集置中裁切防拉伸；
  - 純離屏自適應雙線性模糊光暈（流光/深黑/午夜預設）；
  - 嵌入 Telegram 官方頻道二維碼 (`t.me/gpdbnews`)；
  - 桌面端 2x Retina Canvas 匯出 / Android 原生點陣圖擷取一鍵分享。

### 6. 🛡️ 全維度隱私安防套件
- **桌面端精簡高效**：去除冗餘偽裝，專注極致純粹的媒體檔案管理。
- **行動端生物識別與安全鎖**：支援 PIN 碼與指紋/面容解鎖，失焦自動模糊遮罩。
- **系統級實體絕緣**：Android 系統級 `FLAG_SECURE` 防錄影截圖，實體沙盒遞迴注入 `.nomedia`，徹底絕緣第三方相簿掃描。

### 7. 原生執行緒內 LLM 大型語言模型翻譯引擎
- **純 Rust 非同步 TLS 核心**：零外部 Python 依賴，單句試譯與批次翻譯極速回應。
- **多模型支援與 Key 輪換池**：原生接入 Google Gemini（配額超限自動切換備用 Key）、OpenAI 協定（DeepSeek、Claude、Moonshot、Ollama 等）。
- **容錯與標準化沉澱**：智慧容納純中文內文，消除強行 JSON 解析報錯；支援翻譯數據標準 JSON 跨平台無損匯入匯出。

### 8. 資源檢索與擴充外掛程式系統 (v2.0)
- **矩陣化直達跳轉**：一鍵跳轉 BoyfriendTV、Google、各大影視庫及 BT 磁力資源搜尋。
- **PBC 維基刮削引擎**：1,200+ 演員深度抓取，增量修訂偵測，25+ 屬性比對（準確率 97.3%）。
- **SmutJunkies 刮削引擎**：6,700+ 演員全站索引，4 級高容錯對齊演算法，雙向增量同步。

### 9. 介面國際化與極致輕量純粹體驗
- **7 國語言原生支援**：簡體中文、繁體中文、English、Italiano、日本語、Español、Deutsch。
- **100% 詞條對齊與多級回退**：每種語言維護 1,096 個詞條，多級智慧回退杜絕夾雜生硬字元。
- **狀態雙向連動與輕量架構**：選單語言與數據內容語言解耦排程並雙向連動；完全剝離虛擬成就與冗餘畫像外掛程式，記憶體開銷極致精簡。

### 10. 通用數據跨端無縫備份與全平台線上升級
- **跨平台通用備份 (`GPDb_Backup.json`)**：一鍵匯出/匯入收藏、足跡、筆記、配置，打通 macOS、Windows、Android 換機遷移。
- **一鍵檢查更新與斷點續傳**：整合 GitHub Releases API 版本比對與增量斷點高速下載。

### 11. 全平台深度原生自訂
- **macOS**：Tauri v2 + Rust 原生架構，適配 Apple Silicon / Intel，系統級毛玻璃與快速鍵。
- **Windows**：原生 Windows 11 Mica / Acrylic 材質，淺色主題高對比度隔離，系統匣常駐與 Jump List，工作列刮削即時進度指示，WebView2 高刷 GPU 硬體加速。
- **Android**：Kotlin + Jetpack Compose + Room 原生打造，流暢手勢觸控與隨身離線查閱。

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

### 使用者下載安裝（推薦）

直接前往本倉庫的 [Releases 頁面](https://github.com/GeavenMax/GPDb/releases) 下載最新 **`v2.19.0`** 正式安裝套件：

| 平台 | 安裝套件檔案名稱 | 安裝方式與說明 |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.19.0.dmg` | 按兩下掛載後將 `GPDb.app` 拖入 `Applications` 目錄即可。<br>*(若提示未公證，可在「系統設定 → 隱私權與安全性」點擊「仍要開啟」)* |
| **🪟 Windows** | `GPDb-Windows-v2.19.0.exe` | 按兩下安裝程式即可執行。採用 NSIS 免提權單使用者架構，無需管理員權限。 |
| **🤖 Android** | `GPDb-Android-v2.19.0-signed.apk` | 手機下載直接安裝（官方正式私鑰強簽名；若系統提示請允許未知來源安裝）。 |

---

### 💻 開發者編譯指南 (Developer Guide)

```bash
# 1. macOS 桌面端
cd desktop_client && npm install && npm run tauri dev      # 本地除錯
npm run tauri build                                         # 生產打包 (.app / .dmg)

# 2. Windows 桌面端
cd Windows_client && npm install && npm run tauri dev      # 本地除錯
npm run tauri build                                         # 生產打包 (.exe)

# 3. Android 原生端
cd android_client && ./gradlew assembleRelease              # 編譯簽名 Release APK

# 4. 發版合規自動化預檢
./git_tasks/pre_release_check.sh                            # 一鍵驗證路徑合規、隱私洩漏與版本對齊
```

> 💡 更多子系統架構參考：
> - 介面多語言規範：[`docs/i18n/UI_I18N_SPEC.md`](docs/i18n/UI_I18N_SPEC.md)
> - 片商超清 Logo 系統：[`studio_logos/README.md`](studio_logos/README.md)
> - 自動化發版全流程：[`git_tasks/README.md`](git_tasks/README.md)

---

## ⚖️ 法律聲明與免責條款 (Disclaimer)

1. **軟體定位**：本軟體（GPDb）僅為**通用開源的離線成人媒體元數據本地索引與資料庫管理工具**。
2. **內容免責**：本專案原始碼及其發布版本中**不包含、不代管、不散布任何受版權保護的影音檔案、種子數據或圖片素材**。軟體中展示的範例欄位僅用於資料庫技術驗證與介面排版測試。
3. **使用者責任**：使用者管理本地資料庫檔案或跳轉外部搜尋所產生的一切行為及版權責任，均由使用者本人獨立承擔。
4. **合規遵守**：本專案僅面向達到法定成年年齡的使用者。請在遵守當地法律法規的前提下合理、合法使用。

---

## 📄 開源許可證 (License)

本專案採用 [MIT 許可證](LICENSE) 開源。
