# GPDb · Ultra-Fast, Privacy-First Gay Adult Media Database Manager

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>A high-performance, full-dimensional archival, zero-cloud-tracking, privacy-first cross-platform management client tailored for gay adult media enthusiasts and collectors.</strong>
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
  <img src="https://img.shields.io/badge/Version-v2.18.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Official Channel**: Subscribe to the [Official GPDb Telegram Channel (@gpdbnews)](https://t.me/gpdbnews) for immediate release updates, incremental database patches, and usage tips!

---

## 📖 About GPDb

**GPDb** (Gay Pornography Database Manager) is a modern, full-featured **offline media database and metadata management system** built specifically for **gay adult media** enthusiasts, digital archivists, and collectors.

**100% Offline-First & Absolute Zero-Trace Privacy**:
- **Zero Cloud Dependency**: Media metadata, performer physical profiles, poster caches, and user bookmarks remain strictly on your local physical drive;
- **Pure Standalone Architecture**: No user accounts, no telemetry or tracking, and zero data uploads to external servers;
- **Native-Grade High Performance**: Built on a high-performance **Rust core (`gpdb-core`)** + **Tauri v2 + Vue 3** (macOS / Windows) and native **Kotlin + Jetpack Compose + Room** (Android). Effortlessly manages **63,000+ movies, 134,000+ scenes, 108,000+ performers, and 2,400+ studios** with fluid 60fps rendering and millisecond-level instant search.

---

## ✨ Core Highlights

### 1. Performer Physical Attributes & High-Tolerance Unified Search
- **Granular Anatomical Filtering**: Filter performers by body build, hair color, eye color, body hair density, height/weight, skin tone, anatomical dimensions (penis size / foreskin status), and tattoos/piercings.
- **Smart AKA Aggregation**: Automatically reconciles multi-era and cross-studio aliases, preventing missed appearances due to alternate stage names.
- **Feature Film & Scene Decoupling**: Precisely distinguishes full-length movies from episodic scene vignettes.
- **High-Tolerance Unified Search**: FTS5 high-speed indexed search with SQL multi-field fallback; supports simultaneous cross-field search across titles (EN/ZH), studios, directors, synopses, and performers.
- **PBC Wiki Comprehensive Integration**: Integrates Porn Base Central wiki profiles (legal names, debut years, career status, biographies, and interconnected IAFD/IMDb/X profiles).

### 2. Immersive Home Feed & Multi-Poster Tile Gallery
- **Five Discovery Streams**: Hero Carousel billboard, On This Day (classic premieres), Star Spotlight (iconic faces), Iconic Series Showcase, and Lucky Discovery (blind-box random explore).
- **Multi-Poster Tile Gallery**: Front covers, back covers, and alternate release posters tile horizontally with Front/Back badges.
- **Full-Screen Zoom Lightbox**: Smooth mouse-wheel zoom (1.0x–5.0x), drag-to-pan, double-click smart zoom/reset, and mobile pinch-to-zoom gestures.

### 3. Smart Series Clustering & Dynamic Collage Covers
- **Algorithmic Clustering**: Intelligently identifies Roman numerals and subtitles to automatically group franchise entries into coherent series.
- **Adaptive Art Collage Covers**: Automatically generates 1–4 poster symmetrical or grid art collages with instant offline rendering.

### 4. Director Profiles & 2,400+ Studio Ultra-HD Encyclopedia
- **Dedicated Director Profiles**: One-click lookup for directorial filmographies with library-wide linked filtering.
- **100% Studio Logo & Banner Coverage**: Complete 100% HD WebP logo coverage across 2,490+ studios; 690+ official widescreen banners; 98% movie logo coverage.
- **Ergonomic Detail Layout**: Full-width synopsis expansion under posters with a consolidated matrix action bar.

### 5. 📸 Screenshot Privacy Blur & Ambient Share Cards (v2.18.0)
- **Global One-Tap Privacy Blur**: Eye toggle on desktop header / Android discover top bar instantly applies Gaussian blur to posters (`blur(24px)`) and synopses (`blur(7px)`).
- **Ambient Dual-Poster Share Cards**:
  - Front/Back dual-poster side-by-side layout and 16:9 distortion-free centered scene cropping;
  - Pure offscreen bilinear blur ambient glow (Vibrant / Dark / Midnight presets);
  - Embedded Telegram official channel QR code (`t.me/gpdbnews`);
  - 2x Retina Canvas export on Desktop / native hardware bitmap capture on Android.

### 6. 🛡️ Full-Spectrum Privacy & Security Suite
- **Streamlined Desktop Experience**: Clean, focused media asset manager with zero redundant disguise overhead.
- **Mobile Biometric & PIN Security**: Android PIN and fingerprint/face unlock with automatic blur overlay when losing focus.
- **System-Level Physical Isolation**: Android system-level `FLAG_SECURE` blocks screenshots and screen recordings; recursive `.nomedia` sandbox completely isolates media from third-party gallery scanners.

### 7. Native In-Process LLM Translation Engine
- **Pure Rust Async TLS Core**: Zero external Python runtime dependencies; lightning-fast single-phrase testing and batch translation.
- **Multi-Model Support & Key Rotation Pool**: Native Google Gemini (automatic backup key switching on quota limits), OpenAI-compatible protocol (DeepSeek, Claude, Moonshot, Ollama, etc.).
- **High Tolerance & Standardized Persistence**: Tolerates plain-text output without JSON parse crashes; lossless cross-platform translation export/import via standard JSON.

### 8. Resource Search & External Plugins (v2.0)
- **Matrix Direct Lookups**: One-click quick search jumps to BoyfriendTV, Google, major media databases, and BT magnet engines.
- **PBC Wiki Scraper Engine**: Deep scraping across 1,200+ performers with incremental revision detection and 25+ attribute comparisons (97.3% accuracy).
- **SmutJunkies Scraper Engine**: 6,700+ performers with 100% full-site index, 4-tier fault-tolerant alignment, and bidirectional incremental sync.

### 9. UI Internationalization & Streamlined UX
- **7 Native Languages**: Simplified Chinese, Traditional Chinese, English, Italian, Japanese, Spanish, and German.
- **100% Key Alignment & Smart Fallback**: 1,096 aligned keys per language with multi-tier fallback to eliminate raw missing key strings.
- **Bidirectional Sync & Clean Architecture**: Decoupled interface language and content language with synchronized state; stripped virtual trophies and rating overhead for minimal memory footprint.

### 10. Universal Cross-Platform Backup & Seamless Updates
- **Cross-Platform Universal Backup (`GPDb_Backup.json`)**: One-click export/import for bookmarks, history, notes, and settings across macOS, Windows, and Android.
- **One-Click Update Check & Resumable Downloads**: GitHub Releases API version checking with high-speed resumable delta downloads.

### 11. Deep Native Platform Integration
- **macOS**: Tauri v2 + Rust native architecture, optimized for Apple Silicon and Intel, system vibrancy, and native shortcuts.
- **Windows**: Native Windows 11 Mica / Acrylic materials, light theme high-contrast isolation, system tray residence & Jump Lists, taskbar scraper progress indicator, and WebView2 GPU hardware acceleration.
- **Android**: Native Kotlin + Jetpack Compose + Room with fluid touch gestures and on-the-go offline browsing.

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

### Download & Install (Recommended)

Download official **`v2.18.0`** release packages directly from the [Releases page](https://github.com/GeavenMax/GPDb/releases):

| Platform | Package Filename | Installation Notes |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.18.0.dmg` | Double-click to mount and drag `GPDb.app` into `Applications`.<br>*(If prompted about an unverified developer, go to System Settings → Privacy & Security and click "Open Anyway".)* |
| **🪟 Windows** | `GPDb-Windows-v2.18.0.exe` | Double-click to run. Uses an NSIS single-user architecture requiring no administrator privileges. |
| **🤖 Android** | `GPDb-Android-v2.18.0-signed.apk` | Download and install directly on your device (officially signed with release key; allow unknown sources if prompted). |

---

### 💻 Developer Guide

```bash
# 1. macOS Desktop
cd desktop_client && npm install && npm run tauri dev      # Local dev
npm run tauri build                                         # Production build (.app / .dmg)

# 2. Windows Desktop
cd Windows_client && npm install && npm run tauri dev      # Local dev
npm run tauri build                                         # Production build (.exe)

# 3. Android Native
cd android_client && ./gradlew assembleRelease              # Build signed release APK

# 4. Pre-Release Compliance Check
./git_tasks/pre_release_check.sh                            # Verify path hygiene, leak prevention & version alignment
```

> 💡 Additional Subsystem References:
> - UI Internationalization Specification: [`docs/i18n/UI_I18N_SPEC.md`](docs/i18n/UI_I18N_SPEC.md)
> - Ultra-HD Studio Logo System: [`studio_logos/README.md`](studio_logos/README.md)
> - Automated Release Workflow: [`git_tasks/README.md`](git_tasks/README.md)

---

## ⚖️ Disclaimer

1. **Software Scope**: GPDb is an **open-source, generic offline adult media metadata indexing and local database management tool**.
2. **Content Disclaimer**: The source code and official releases of this project **do not contain, host, or distribute any copyrighted audio, video, torrents, or graphic assets**. Sample metadata fields are strictly used for schema validation and UI layout testing.
3. **User Responsibility**: Users bear full and independent legal responsibility for managing their local database files, media assets, and any external lookup operations.
4. **Legal Compliance**: This project is intended exclusively for adults of legal age. Please ensure compliance with all applicable local laws and regulations before use.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
