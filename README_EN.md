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
  <img src="https://img.shields.io/badge/Version-v2.17.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Official Telegram Channel**: Subscribe to the [Official GPDb Telegram Channel (https://t.me/gpdbnews)](https://t.me/gpdbnews) for the latest release updates, incremental database notifications, and usage tips!

---

## 📖 About GPDb

**GPDb** (Gay Pornography Database Manager) is an advanced, full-featured **offline media database and metadata client** designed specifically for gay adult film enthusiasts, digital archivists, and media researchers.

In adult media, an individual's viewing history, curated collections, and personal aesthetic preferences constitute **vital and strictly personal privacy**. Commercial streaming platforms and centralized online services present persistent risks of user tracking, data analysis leaks, and sudden library deletions due to copyright expiration.

**GPDb is engineered around a strict "100% Offline-First & Zero-Trace Privacy" philosophy**:
- **Zero Cloud Dependence**: All media metadata, performer body profiles, poster/thumbnail caches, personal ratings, tags, and collection history remain strictly stored on your device's physical disk.
- **Pure Local Architecture**: No account registration, no telemetry, no analytics, and zero external tracking or background uploads of any kind.
- **Uncompromised Performance**: Powered by a high-performance **Rust core engine (`gpdb-core`)** combined with **Tauri v2 + Vue 3** (macOS / Windows desktop) and **Kotlin + Jetpack Compose + Room** (Android native mobile). Effortlessly manages **60,000+ full-length films, 100,000+ individual scenes, 6,000+ detailed performer body profiles, and 1,300+ classic & modern studios** with fluid 60fps rendering and millisecond-level instant search.

---

## ✨ Domain-Specific Features

### 1. Granular Performer Body Attributes & Deep Filtering
- **Fine-Grained Anatomical & Morphological Search**:
  Filter performers by **Body Type / Build** (Muscle, Twink, Bear, Hunk, etc.), **Hair Color**, **Eye Color**, **Facial & Body Hair density**, **Height & Weight**, **Skin Tone**, **Penis Size / Foreskin status**, and **Tattoos / Piercings** — combinable in any configuration.
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
- **PBC Wiki Comprehensive Performer Data Integration**:
  Performer profiles now integrate rich metadata sourced from Porn Base Central Wiki, including **birth name, debut year, active/retired status badge, astrology sign & ethnicity, performance style tags, wiki biography card**, and **cross-platform connected profiles** (IAFD / IMDb / X / OnlyFans / Instagram), powering deep performer research and cross-platform verification.

### 2. Streaming-Grade Immersive Home Feed & Multi-Poster Tile Gallery
- **Five Discovery Streams**:
  - **Hero Carousel**: Smooth auto-cycling full-resolution billboard posters with delicate parallax interaction at the top of the home screen.
  - **On This Day (Retro Premieres)**: Intelligently cross-references historical calendar records to revisit classic titles premiering on the same date from the golden eras (1980s, 1990s, and 2000s).
  - **Star Spotlight (Today's Icons)**: Strictly validates local physical disk poster files, intelligently recommending iconic performers with high-definition headshots while eliminating generic letter placeholders.
  - **Iconic Series Showcase**: Surfaces long-running multi-installment cinematic IP series spanning ten or more entries, with random shuffle exploration and one-click refresh.
  - **Lucky Discovery (Blind Box)**: Roll the dice to randomly uncover hidden vintage gems from over 60,000 titles.
- **Multi-Poster Adaptive Tile Gallery & Full-Screen Zoom Lightbox**:
  - **Multi-Poster Adaptive Tile Display**: Titles with front covers, back cover photoshoots, or alternate poster editions adaptively tile horizontally at the same time, equipped with Front/Back pill badges and one-click activation of high-res lightbox.
  - **Full-Screen Zoom Lightbox (`ImageLightbox.vue` / Compose zoom)**: Supports smooth mouse-wheel zoom (1.0x ~ 5.0x), click-and-drag panning, double-click smart zoom/reset, keyboard shortcuts (+/-/0/Esc), and smooth mobile pinch-to-zoom gestures.

### 3. Smart Series Aggregation & Dynamic Collage Covers
- **Algorithmic Clustering**: Built-in Roman numeral and subtitle recognition automatically groups scattered franchise entries (e.g., *Part I, Part II, Part III*) into unified, coherent collections.
- **Adaptive Poster Collages**: Automatically renders single-cover posters, symmetrical 2-panel splits, 3-cover stepped layouts, or 4-quadrant grid collages for series albums — with vignette lighting and instantaneous offline rendering.
- **Series-Exclusive Bookmarks**: Bookmark your favorite classic series with a single tap to a dedicated "Saved Series" shelf for easy follow-up browsing.

### 4. Director Profiles & 180+ Studio Deep Archives
- **Director Profiles & Filmographies**: Click any director's name on a film's detail page to instantly reveal their dedicated profile card showcasing their full historical directorial catalog, with one-click filtering by director in the movie library.
- **Studio Deep Archives & Visual Index**:
  - **181 Core Studios Comprehensive History & Overview**: Fully documents founding eras, founder backgrounds, aesthetic styles, and cultural evolutions across classic and modern production studios;
  - **Automatic Scraping & Sharp Display of 74 Studio Logos & Banners**: Covers high-resolution official logos and banner artworks for 74 core studios, rendered sharp and distortion-free in large formats, with graceful fallback to multi-dimensional gradient dual-initial badges for studios without logos;
  - **Redesigned Movie Detail Layout**: Movie synopsis expanded full-width directly below the poster for comfortable readability, with core action buttons consolidated into a compact matrix layout.

### 5. 📸 Screenshot Privacy Blur & Ambient Share Cards (v2.17.0 Enhanced)
- **Global One-Tap Screenshot Privacy Mode**:
  - The top navigation bar features a one-tap "Screenshot Privacy" eye toggle; supports keyboard shortcuts and instant status switching (Privacy Active / Normal Browsing).
  - **Android Home Feed Top-Bar One-Tap Privacy Toggle**: The mobile home feed features a persistent top-bar quick-action toggle button for effortless, single-hand one-tap privacy blur switching.
  - **Fine-Grained Privacy Redaction Controls**:
    - `Blur Posters & Media Images`: Globally applies Gaussian blur (`blur(24px)`) to all movie covers, scene stills, and performer avatars, preventing visual leakage during screenshots or social sharing;
    - `Blur Synopses & Sensitive Text`: Applies Gaussian blur (`blur(7px)`) to movie synopses and scene descriptions while disabling text selection, preventing spoilers and sensitive exposure.
  - Driven by `LocalPrivacyBlur` reactive state flow on Android mobile, instantly taking effect across the full screen.
- **Adaptive Poster Ambient Share Card Generator**:
  - One tap on any film or scene detail page generates an Apple Music / Spotify-style ambient share card.
  - **Front & Back Double Poster Side-by-Side Display**: Feature films support automatic extraction or side-by-side presentation of Front / Back double posters, combining visual completeness with collectible aesthetic quality.
  - **Clean Sharing Experience & Official Channel QR Code**: Neatly embeds the official Telegram channel QR code and identifier (`t.me/gpdbnews`) in the bottom corner of the card, removing redundant creation date timestamps while preserving the canonical title/identifier cleanly.
  - **Pure Offscreen Bilinear Blur & 16:9 Distortion-Free Centered Cropping**: Employs offscreen bilinear blur rendering algorithms for background ambient glow; scene and episode stills feature smart 16:9 centered cropping with zero distortion or aspect stretching.
  - Background glow dynamically extracts hue directly from the poster artwork and applies high-precision, large-radius Gaussian blur (`blur(45px~60px)`), offering three signature presets: "Vibrant", "Dark", and "Midnight".
  - **Pre-Share Privacy Redaction**: Independently toggle "Blur Poster" and "Blur Text" to guarantee completely safe sharing across social chats and public forums.
  - **Lossless Dual-Platform Export**:
    - **Desktop**: Rendered via offline HTML5 Canvas 2D engine at 2x Retina ultra-HD resolution — one-click copy PNG to system clipboard (paste directly into WeChat, QQ, Telegram, Discord, X) or export and save locally.
    - **Mobile**: Compose 1.8 hardware-accelerated bitmap capture engine — one-tap lossless save to system photo library (`MediaStore`), invoking native Android share sheet via `FileProvider`.

### 6. 🛡️ Full-Spectrum Privacy & Security Suite (Enterprise-Grade Privacy & Security)
- **Desktop Streamlined Design Focused on Archiving**:
  - The desktop client has completely streamlined and removed the legacy disguised emergency calculator, focusing purely on high-performance, minimalist offline media asset management and library curation.
- **Mobile PIN Lock & Biometric Authentication**:
  - Android mobile supports custom 4–6 digit PIN application lock and fingerprint/face biometric unlocking, automatically superimposing a glassmorphic secure lock screen upon losing focus or timing out (1/5/15/30 minutes).
  - Instantly covers an opaque frosted-glass privacy layer when switching apps or losing window focus.
- **Mobile System-Level Physical Isolation**:
  - Integrates Android system-level `FLAG_SECURE` to block screen recordings, screenshots, and recent task switcher preview snapshots.
  - Physical sandbox storage isolation with recursive `.nomedia` file injection across directories to strictly prevent scanning by third-party photo galleries.

### 7. Native In-Process LLM Translation Engine & Bilingual Dual-View
- **Pure Rust Native In-Process Multi-LLM Engine**:
  - Completely eliminates external Python HTTP service dependencies; natively implements asynchronous concurrent TLS request channels within the desktop Rust core, delivering lightning-fast single-sentence test translations and batch operations.
  - Native support for Google Gemini, OpenAI protocol ecosystem (DeepSeek, Moonshot, Qwen, Zhipu GLM, SiliconFlow, local Ollama), and Anthropic Claude.
- **Google Gemini API Key Rotation Pool & Quota Protection**:
  - Built-in atomic API key rotation pool automatically switches to backup keys in milliseconds upon triggering rate limits (HTTP 429); gracefully delegates to secondary fallback providers if all keys exceed quotas.
- **Bilingual Dual-View for Scenes & Films with Plain-Text Auto-Tolerance**:
  - Seamless one-click AI translation and local SQLite caching for both feature-length films and standalone scene vignettes, with instant one-tap switching between Chinese translation and English original text.
  - Deeply optimized for custom API keys: automatically tolerates plain-text Chinese output from LLMs, completely eliminating strict JSON parsing errors; automatically normalizes and appends missing `/v1` endpoint paths.
- **Cross-Platform Lossless Translation Portability**:
  - Translation data seamlessly exports and imports bidirectionally across platforms (macOS / Windows / Android) via standardized JSON with zero data loss.

### 8. Resource Search & Plugins System (v2.0)
- **Multi-Site Direct Lookups & Compact Matrix Layout**:
  - Film and performer detail pages feature compact single-row matrix action buttons for one-click direct navigation to BoyfriendTV, Google, and major authoritative video databases without re-typing.
- **BT Magnet Search Integration**: Automatically combines movie titles and studio names into standardized search queries, linking straight to external resource search engines.
- **Independent Modular Toggles**: Plugin Center provides granular toggles to enable or disable individual external lookup providers independently.
- **Incremental Scraper & Sync Engine**: Supports on-demand network scraping updates and local offline caching of posters and thumbnails.
- **PBC (Porn Base Central) Performer Wiki Scraper (`scrape_pbc_actors.py`)**:
  - Covers **1,200+ performers**, performing full-depth crawls via MediaWiki API;
  - Built-in **incremental revision detection** synchronizes only entries modified since the last run, drastically slashing network bandwidth;
  - Meticulously compares **25+ attribute fields** (birth name, debut year, ethnicity, astrology, active status, external platform links, etc.);
  - Tested performer name matching accuracy reaches **97.3%**.
- **SmutJunkies Performer Scraper (`scrape_smutjunkies_actors.py`)**:
  - Indexes **6,700+ gay adult performers** across the full 26-letter alphabetical index with **100% full-site coverage**;
  - Employs a **4-tier fault-tolerant precision alignment algorithm** (exact match → normalized fuzzy match → alias cross-verification → manual review queue), ensuring high-confidence cross-source alignment;
  - Supports **bidirectional incremental sync**: automatically imports new entries and updates field-level diffs on existing profiles with zero data loss.

### 9. Full Localization & Streamlined UX (Internationalization & Streamlined UX)
- **7 Fully Localized Interface Languages**: Simplified Chinese (`zh-CN`), Traditional Chinese (`zh-TW`), English (`en`), Italian (`it`), Japanese (`ja`), Spanish (`es`), and German (`de`).
- **Lightweight & Streamlined Architecture**:
  - Completely retired the PlayStation virtual trophy achievement system, eliminating redundant compute overhead and obsolete code;
  - Retired the experimental "AI Persona Analysis" plugin, focusing the plugin architecture strictly on its three core pillars: magnet search, data scraping, and LLM translation;
  - Retired the 5-star rating system and complex 3D flip card rendering, drastically reducing application memory footprint and GPU rendering overhead.

### 10. Universal User Data Backup & Seamless Update (Cross-Platform Backup & Seamless Update)
- **Universal User Data & Configuration Seamless Backup/Migration (`GPDb_Backup.json`)**:
  - Provides a standardized universal JSON backup architecture to export and import all personal bookmarks, viewing history, tags, ratings, LLM API configurations, and system preferences with a single click.
  - Completely bridges cross-platform data silos across macOS, Windows, and Android. Migrating between devices, reinstalling systems, or syncing across platforms requires only a single backup file for instantaneous, seamless migration.
- **One-Click In-App Update Check & Resumable Incremental Upgrades**:
  - Built-in automatic and manual version detection modules across desktop and mobile compare against latest releases in real time, displaying detailed release notes.
  - Deeply integrated high-speed resumable download engine smoothly guides upgrades and installation upon completion, keeping your client always up to date.

### 11. Cross-Platform Deep Customization & Multi-Client Data Harmony (macOS / Windows / Android)
- **macOS Universal Desktop (`desktop_client/`)**: Built on Tauri v2 + Rust, natively supporting Apple Silicon and Intel architectures with native glassmorphism and keyboard shortcut interactions.
- **Windows Native Desktop Deep Integration (`Windows_client/`)**:
  - **Native Mica / Acrylic Window Materials & Fluent Design**: Semi-transparent window blending with desktop wallpaper, 4 selectable material tiers, and dark-mode isolation to prevent muddy contrast in light themes;
  - **System Tray Residence & Jump List Quick Navigation**: Supports minimizing to system tray on close, right-click tray menu for quick privacy toggling, and instant jumping to frequent sections;
  - **Taskbar Progress Indicator (Taskbar Progress)**: Displays native green progress bars and percentage indicators directly on the taskbar icon during full or incremental network scraping;
  - **WebView2 Hardware Acceleration Tuning**: Configured with high-refresh GPU rasterization flags for silky-smooth 120Hz/144Hz displays.
- **Native Android Mobile Client (`android_client/`)**: Built natively with Kotlin + Jetpack Compose + Room, seamlessly harmonized with desktop data schemas for on-the-go offline browsing, tagging, and collection management anywhere.

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

Head directly to the repository's [Releases page](https://github.com/GeavenMax/GPDb/releases) to download the latest **`v2.17.0`** official installer:

| Platform | Installer Filename | Installation Notes |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.17.0.dmg` | Double-click to mount, then drag `GPDb.app` into your `Applications` folder.<br>*(On first launch, if macOS warns the app is not notarized, go to System Settings → Privacy & Security and click "Open Anyway", or run `sudo xattr -cr /Applications/GPDb.app` in Terminal.)* |
| **🪟 Windows** | `GPDb-Windows-v2.17.0.exe` | Double-click the installer to complete setup. Uses NSIS single-user no-elevation architecture — no administrator privileges required. |
| **🤖 Android** | `GPDb-Android-v2.17.0-signed.apk` | Download to your phone and tap to install. (Signed with the official release key. If prompted to allow installation from unknown sources, please permit it.) |

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
