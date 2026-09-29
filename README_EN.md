# GPDb · High-Performance, Privacy-First Gay Adult Video Database Manager

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>A modern, high-performance, privacy-focused, fully offline library and metadata management client crafted specifically for gay adult cinema enthusiasts and collectors — with zero cloud tracking.</strong>
</p>

<p align="center">
  <a href="./README.md">简体中文</a> |
  <a href="./README_EN.md"><b>English</b></a> |
  <a href="./README_ZH_TW.md">繁體中文</a> |
  <a href="./README_JA.md">日本語</a> |
  <a href="./README_DE.md">Deutsch</a> |
  <a href="./README_ES.md">Español</a> |
  <a href="./README_IT.md">Italiano</a>
</p>

<p align="center">
  <a href="https://t.me/gpdbnews" target="_blank"><img src="https://img.shields.io/badge/Telegram-Channel%20%40gpdbnews-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Channel" /></a>
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Android-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Version-v2.13.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Official Telegram Channel**: Subscribe to the [Official GPDb Telegram Channel (https://t.me/gpdbnews)](https://t.me/gpdbnews) for the latest release updates, incremental database notifications, and usage tips!

---

## 📖 About GPDb

**GPDb** (Gay Pornography Database Manager) is an advanced, full-featured **offline media database and metadata client** designed specifically for gay adult film enthusiasts, digital archivists, and media researchers.

In adult media, an individual's viewing history, curated collections, and personal aesthetic preferences constitute **vital and strictly personal privacy**. Commercial streaming platforms and cloud-based services present persistent risks of user tracking, data leaks, and sudden library deletions due to copyright expiration.

**GPDb is engineered around a strict "100% Offline-First & Zero-Trace Privacy" philosophy**:
- **Zero Cloud Dependence**: All media metadata, performer body profiles, poster/thumbnail caches, personal ratings, tags, and collection history remain strictly stored on your device's physical disk — never leaving your machine.
- **Pure Local Architecture**: No account registration, no telemetry, no analytics, and zero external tracking or background uploads of any kind.
- **Uncompromised Performance**: Powered by a high-performance **Rust core engine (`gpdb-core`)** combined with **Tauri v2 + Vue 3** (macOS / Windows desktop) and **Kotlin + Jetpack Compose + Room** (Android native mobile). Effortlessly manages **60,000+ full-length films, 100,000+ individual scenes, 6,000+ detailed performer body profiles, and 1,300+ classic & modern studios** with fluid 60fps rendering and millisecond-level instant search.

---

## ✨ Domain-Specific Features

### 1. Granular Performer Body Attributes & Deep Filtering
- **Fine-Grained Anatomical & Morphological Search**:
  Filter performers by **Body Type / Build** (Muscle, Twink, Bear, Hunk, etc.), **Hair Color**, **Eye Color**, **Facial & Body Hair density**, **Height & Weight**, **Ethnicity / Skin Tone**, **Penis Size / Foreskin status**, and **Tattoos / Piercings** — combinable in any configuration.
- **Performer Aliases & Cross-Studio Alignment**:
  Intelligently reconciles alternate screen names and aliases used across different production companies and eras, preventing missed appearances due to name changes.
- **Precise Film vs. Scene Distinction**:
  Seamlessly view a performer's appearances in feature-length movies (**Films**) separately from their standalone episodic vignettes (**Scenes**) — every credit accounted for at a glance.
- **High-Tolerance Multi-Field Unified Search Engine**:
  - FTS5 high-speed recall → automatic fallback to standard SQL multi-field combined search → Room / SQLite safety net, eliminating empty results caused by tokenization or missing virtual tables.
  - A single query simultaneously searches the English title (`title`), Chinese title (`title_zh`), studio name (`studio_name`), director name (`director_name`), Chinese synopsis (`description_zh`), and the full performer cast.
- **Multi-Dimensional Time & Recency Filtering**:
  - Options include "All", "Last Scraped", "Released in Last 7 Days", "Released in Last 30 Days", "Released in Last 90 Days", and "This Year".
  - Film and scene release time granularity is precisely separated (features by year, scenes by exact date).
  - Newly indexed entries display a dynamic gradient `NEW` highlight badge.

### 2. Streaming-Grade Immersive Home Feed & 3D Collector Cards
- **Five Discovery Streams**:
  - **Hero Carousel**: Smooth auto-cycling full-resolution billboard posters with delicate parallax interaction at the top of the home screen.
  - **On This Day (Retro Premieres)**: Intelligently cross-references the current calendar date against historical premiere records to surface classic titles from the golden eras of the 1980s, 1990s, and 2000s.
  - **Star Spotlight (Today's Icons)**: Strictly validates local disk poster file availability, intelligently featuring iconic performers with high-definition headshots — no generic letter placeholders ever shown.
  - **Legendary Franchises Showcase**: Automatically surfaces long-running multi-installment cinematic series spanning ten or more entries.
  - **Lucky Discovery (Blind Box)**: Roll the dice to randomly uncover hidden vintage gems from over 60,000 titles.
- **Dual Poster Layout Modes & Full-Screen Gesture/Scroll Lightbox**:
  - Supports **Adaptive HD Gallery (`adaptive_pager`)** and **3D Realistic Collector Cards (`flip_3d`)**: featuring 60fps CSS 3D depth-of-field physical perspective, tactile flip capsule buttons, and collector-grade ambient glow.
  - **Full-Screen Zoom Lightbox (`ImageLightbox.vue` / Compose zoom)**: Supports smooth mouse-wheel zoom (1.0x ~ 5.0x), click-and-drag panning, double-click smart zoom/reset, keyboard shortcuts (+/-/0/Esc), and mobile pinch-to-zoom.

### 3. Smart Series Aggregation & Dynamic Collage Covers
- **Algorithmic Clustering**: Built-in Roman numeral and subtitle recognition automatically groups scattered franchise entries (e.g., *Part I, Part II, Part III*) into unified, coherent collections.
- **Adaptive Poster Collages**: Automatically renders single-cover posters, symmetrical 2-panel splits, 3-cover stepped layouts, or 4-quadrant grid collages for series albums — with vignette lighting and instantaneous offline rendering.
- **Series-Exclusive Bookmarks**: Bookmark your favorite classic series with a single tap to a dedicated "Saved Series" shelf for easy follow-up browsing.

### 4. Director Profiles & Studio Genre Index
- **Director Filmographies**: Click any director's name on a film's detail page to instantly reveal their full directorial catalog, with one-click library filtering by director.
- **Comprehensive Studio Catalog**: Covers historic celluloid giants (Falcon, Colt, Catalina, etc.) through to modern high-definition powerhouses (Men.com, BelAmi, Lucas Entertainment, Corbin Fisher, etc.) — complete with release timelines and signature genre tags.

### 5. 📸 Screenshot Privacy Blur & Share Cards (New in v2.13.0)
- **Global One-Tap Screenshot Privacy Mode**:
  - The top navigation bar features a one-tap "Privacy Mode" eye toggle; supports keyboard shortcuts and instant status switching (Privacy Active / Normal Browsing).
  - **Granular Privacy Redaction Controls**:
    - `Blur Posters & Media Images`: Applies global Gaussian blur (`blur(24px)`) to all film covers, scene stills, and performer headshots, preventing visual exposure in screenshots or social media shares.
    - `Blur Synopses & Sensitive Text`: Applies Gaussian blur (`blur(7px)`) to film synopses and scene descriptions while disabling text selection, preventing spoiler or sensitive content leakage.
  - Android mobile uses `LocalPrivacyBlur` reactive state flow, taking effect instantly across the entire screen.
- **Adaptive Poster Ambient Share Card Generator**:
  - One tap on any film or scene detail page generates an Apple Music / Spotify-style ambient share card.
  - Background glow is adaptively extracted from the cover's dominant hues and processed with high-precision large-radius Gaussian blur (`blur(45px~60px)`), with three exclusive glow presets: **Vibrant**, **Dark**, and **Midnight**.
  - **Pre-Share Privacy Redaction**: Independently toggle "Blur Poster" and "Blur Text" to ensure safe sharing to social groups and public networks.
  - **Lossless Dual-Platform Export**:
    - **Desktop**: Offline HTML5 Canvas 2D engine rasterizes at 2x Retina resolution — one-click copy PNG to system clipboard (paste directly into WeChat/QQ/Telegram/Discord/X) or export and save locally.
    - **Mobile**: Compose 1.8 hardware-accelerated bitmap capture engine — one-tap lossless save to the system photo album (`MediaStore`), with Android native share sheet invoked via `FileProvider`.

### 6. 🛡️ Full-Spectrum Privacy & Security Suite
- **PIN Lock Screen & Focus-Loss Protection**:
  - Set a dedicated 4–6 digit PIN app lock; on focus loss or timeout (1/5/15/30 minutes) the app is instantly covered by a glassmorphic lock screen overlay (`AppLockOverlay.vue`).
  - Window focus loss or app switching triggers an immediate Gaussian frosted-glass privacy layer.
- **Emergency Panic Switch — Disguised Calculator**:
  - Instantly transforms into a fully functional Apple-style dark-mode calculator (`FakeCalculatorModal.vue`) supporting real arithmetic operations.
  - Desktop: keyboard shortcut <kbd>Cmd + Shift + P</kbd> for instant invocation; Mobile: gesture trigger (flip/face-down the screen).
  - Enter your correct PIN followed by `=` in the calculator, or tap the top title 4 times in quick succession to safely unlock and return to your library.
- **Innocuous App Title & Icon Disguise**:
  - Supports custom window titles (e.g., "Calculator", "Notes") to prevent exposure in the system app switcher.
  - Ships with multiple premium design theme icons (e.g., "Dual Mars Fire Totem", "Obsidian Film Vault") plus harmless decoy icons (notepad, ledger, calculator).
- **Mobile System-Level Physical Isolation**:
  - Hooks into Android's system-level `FLAG_SECURE` to block screen recording, screenshots, and recent apps preview thumbnails.
  - Physical sandbox storage isolation with recursive `.nomedia` injection across all directories to completely block third-party gallery scanning.

### 7. AI Persona Insights & LLM Multi-Language Translation
- **AI Fan Persona Insights & Aesthetic Profiling**:
  - **Fully Async Non-Blocking Architecture**: Background threads independently orchestrate local or remote large language models (DeepSeek, OpenAI, Claude, etc.) — zero UI stutter or application freeze.
  - **Deep Aesthetic Portrait Report**: Based on your actual private favorites and tagging history, analyzes your aesthetic DNA to generate a 2,000-word in-depth art appreciation report including a "Core Aesthetic Archetype" codename (e.g., *"Retro New-Wave Explorer"*) and an "Era Audio-Visual Spectrum" section.
  - **Real-Time Progress Modal**: A premium frosted-glass floating panel displays LLM encrypted handshake, fingerprint extraction, and portrait generation progress step by step.
- **LLM Multi-Language Translation & Cross-Platform Data Portability**:
  - Native integration with OpenAI, DeepSeek, Claude, Gemini, ByteDance Doubao, and other major providers.
  - "Detect Available Models" button for one-click live API model discovery.
  - Translation data can be seamlessly imported/exported as standard JSON across platforms (macOS / Windows / Android) with zero data loss.

### 8. Resource Search & External Plugin System (v2.0)
- **Direct Multi-Site Navigation**: On film and performer pages, one click jumps to BoyfriendTV, Google, and major video database sites using the canonical English title — no retyping required.
- **BT Magnet Search Integration**: Formats canonical movie titles and studio names into ready-made search queries for external resource engines.
- **Modular Independent Toggles**: The Plugin Center allows granular enable/disable configuration for each individual external lookup source.
- **Incremental Scrape & Sync Engine**: Supports on-demand network scraping updates and local poster/thumbnail offline caching.

### 9. Full Localization & 77+ Gamified Achievement Trophies
- **7 Fully Localized Interface Languages**: Simplified Chinese (`zh-CN`), Traditional Chinese (`zh-TW`), English (`en`), Italian (`it`), Japanese (`ja`), Spanish (`es`), and German (`de`).
- **PlayStation-Style Trophy System**:
  - 77+ achievements covering exploration, search, focus, and collection milestones — unlocking triggers PSN-inspired fluid animation popups.
  - Achievement tracking is based on real behavioral metrics, with independent trophy data reset and verification management.

### 10. Full Cross-Platform Coverage & Three-Client Data Harmony (macOS / Windows / Android)
- **macOS Native Desktop (`desktop_client/`)**: Built on Tauri v2 + Rust with full Apple Silicon and Intel support, native glassmorphism UI and keyboard shortcut interactions.
- **Windows Native Desktop (`Windows_client/`)**: Dedicated decoupled directory, built-in multi-path Python smart parsing engine, console window suppression (`CREATE_NO_WINDOW`), Microsoft YaHei font rendering optimization, slim scrollbars, Windows 11 snap layout support, and lightweight NSIS single-user no-elevation-required installer.
- **Native Android Mobile (`android_client/`)**: Built purely in Kotlin + Jetpack Compose + Room, seamlessly aligned with the desktop data schema — ideal for on-the-go offline browsing, tagging, and collection management anywhere.

---

## 🛠️ Technology Stack

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
                                   │              Persistent Storage Layer               │
                                   │      GPDb.db (SQLite 3 WAL) + image_cache/          │
                                   └─────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### For General Users (Recommended)

Head directly to the repository's [Releases page](https://github.com/GeavenMax/GPDb/releases) to download the latest **`v2.13.0`** official installer:

| Platform | Installer Filename | Installation Notes |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.13.0.dmg` | Double-click to mount, then drag `GPDb.app` into your `Applications` folder.<br>*(On first launch, if macOS warns the app is not notarized, go to System Settings → Privacy & Security and click "Open Anyway", or run `sudo xattr -cr /Applications/GPDb.app` in Terminal.)* |
| **🪟 Windows** | `GPDb-Windows-v2.13.0.exe` | Double-click the installer to complete setup. Uses NSIS single-user no-elevation architecture — no administrator privileges required. |
| **🤖 Android** | `GPDb-Android-v2.13.0-signed.apk` | Download to your phone and tap to install. (Signed with the official release key. If prompted to allow installation from unknown sources, please permit it.) |

---

### 💻 Developer Guide — Local Build & Run

#### Prerequisites
- **Node.js**: 20+ and npm
- **Rust**: 1.78+ (`rustup`, `cargo`)
- **Android SDK** (Android client only): Android Studio Hedgehog+ or JDK 17+

#### 1. macOS Desktop — Development & Build
```bash
# 1. Clone the repository
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. Enter the desktop client directory
cd desktop_client

# 3. Install frontend dependencies
npm install

# 4. Launch in development mode
npm run tauri dev

# 5. Build production bundle (outputs .app and .dmg)
npm run tauri build
```

#### 2. Windows Desktop — Development & Build
```powershell
# 1. Enter the Windows standalone client directory
cd Windows_client

# 2. Install frontend dependencies
npm install

# 3. Launch in development mode
npm run tauri dev

# 4. Build production installer (generates NSIS .exe)
npm run tauri build

# Or run the ready-made build script directly:
.\build_windows.ps1
```

#### 3. Android Native Mobile — Build
```bash
# 1. Enter the Android native client directory
cd android_client

# 2. Build a debug test APK
./gradlew assembleDebug

# 3. Build the signed release APK
./gradlew assembleRelease
```

---

## ⚖️ Legal Disclaimer

1. **Software Nature**:
   GPDb (Gay Pornography Database Manager) is an **open-source, generic offline adult media metadata indexing and local database management tool**. Its sole purpose is to assist users in organizing and retrieving personal, locally stored media metadata.
2. **No Content Hosting**:
   This repository and its distribution packages **do not contain, host, distribute, or link to any copyrighted video, audio, torrent, or image files**. Example metadata fields displayed within the app are strictly for database schema verification and UI layout testing.
3. **User Responsibility**:
   Users bear sole and exclusive legal responsibility for their personal database files, locally managed media assets, and any external links or searches initiated via the app's plugin features. Developers and contributors assume no liability for any misuse.
4. **Age & Legal Compliance**:
   This software is intended strictly for adults who have reached the legal age of majority in their respective jurisdiction. Please ensure full compliance with all applicable local laws and regulations before use.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE). You are free to inspect, modify, fork, and distribute this software, provided that original author attribution and this disclaimer are retained in all copies.
