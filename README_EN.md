# GPDb · High-Performance, Privacy-First Gay Adult Video Database Manager

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>A modern, high-performance, privacy-focused offline library and metadata management client crafted specifically for gay adult cinema enthusiasts and collectors.</strong>
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
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Android%20(In%20Dev)-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Official Telegram Channel**: Subscribe to the [Official GPDb Telegram Channel (https://t.me/gpdbnews)](https://t.me/gpdbnews) for the latest release updates, database news, and usage tips!

---

## 📖 About GPDb

**GPDb** (Gay Pornography Database Manager) is an advanced, full-featured **offline media database and metadata client** designed specifically for gay adult film enthusiasts, digital archivists, and media researchers.

In adult media, an individual's viewing history, curated collections, and sexual/aesthetic preferences constitute **vital and strictly personal privacy**. Commercial streaming platforms and cloud-based services present persistent risks of user tracking, data leaks, and sudden library deletions due to copyright expiration.

**GPDb is engineered around a strict "100% Offline-First & Zero-Trace Privacy" philosophy**:
- **Zero Cloud Dependence**: All metadata, actor profiles, physical attribute archives, poster/thumbnail caches, ratings, tags, and collections remain strictly stored on your device's physical disk.
- **Pure Local Architecture**: No account registration, no telemetry, no analytics, and zero external tracking or background telemetry.
- **Uncompromised Performance**: Powered by a high-performance **Rust core engine (`gpdb-core`)** and a modern **Tauri v2 + Vue 3** architecture. Effortlessly manages **60,000+ full-length films, 100,000+ individual scenes, 6,000+ detailed performer profiles, and 1,300+ classic & modern studios** with fluid 60fps rendering and millisecond search queries.

---

## ✨ Domain-Specific Features

### 1. Granular Physical Attribute & Performer Filtering
- **Fine-Grained Anatomical & Morphological Search**:
  Filter performers by **Body Type / Build** (Muscle, Twink, Bear, Hunk, etc.), **Hair Color**, **Eye Color**, **Facial & Body Hair density**, **Height & Weight**, **Ethnicity / Skin Tone**, **Circumcision / Foreskin status & Penis Size**, and **Tattoos / Piercings**.
- **Performer Aliases & Cross-Studio Alignment**:
  Intelligently reconciles alternate screen names and aliases across different production companies and eras, preventing duplicate entries and missed appearances.
- **Precise Film vs. Scene Distinctions**:
  Seamlessly toggle between an actor's appearances in feature-length movies (**Films**) and their appearances in standalone episodic vignettes (**Scenes**).

### 2. Streaming-Grade Immersive Home Feed
- **Hero Backdrop Showcase**: Smooth auto-cycling high-definition billboard posters with delicate parallax animation.
- **On This Day (Retro Premieres)**: Automatically matches the current calendar date against historical premiere records from the 1980s, 1990s, and 2000s golden eras.
- **Starlight Today (Verified Portraits)**: Strictly verifies local disk image cache to feature legendary performers with high-definition portraits, avoiding generic placeholder initials.
- **Legendary Franchises Showcase**: Surfaces long-running multi-installment cinematic series.
- **Discovery Roulette**: Roll the dice to randomly uncover hidden vintage gems from over 60,000 titles.

### 3. Smart Series Aggregation & Dynamic Collage Covers
- **Algorithmic Clustering**: Identifies Roman numerals, episode subtitles, and naming conventions to group scattered franchise entries (*Part I, Part II, Part III*) into unified collections.
- **Adaptive Poster Collages**: Automatically renders 1-cover posters, 2-cover symmetrical splits, 3-cover stepped layouts, or 4-quadrant grid collages for series albums with vignette lighting and instantaneous offline rendering.

### 4. Director Biographies & Studio Chronicles
- **Director Profiles**: Click any director's name in movie details to reveal their complete directorial filmography with instant library filtering.
- **Comprehensive Studio Catalog**: Covers historic celluloid giants (Falcon, Colt, Catalina, etc.) to modern high-definition powerhouses (Men.com, BelAmi, Lucas Entertainment, Corbin Fisher, etc.) with release timelines and signature genres.

### 5. AI Persona Insights & Aesthetic Profiling
- **Non-Blocking Asynchronous Pipeline**: Background workers orchestrate local or remote LLMs (DeepSeek, OpenAI, Claude, etc.) without UI stutter or application freeze.
- **Deep Aesthetic DNA Analysis**: Evaluates your private favorites, ratings, and tag clusters to produce a personalized 2,000-word art critique report and aesthetic persona codename (e.g., *"Retro New-Wave Explorer"*).
- **Glassmorphic Real-Time Progress Modal**: Elegantly visualizes prompt tokenization, fingerprint analysis, and generative profiling stage-by-stage.

### 6. Resource Search & Web Extension Integrations (v2.0)
- **Direct 1-Click Lookups**: Navigate directly to actor and movie profiles on external sites such as BoyfriendTV, Google, and major video indexes using canonical English titles.
- **BT Magnet Generator**: Formats canonical studio codes and movie titles into search queries for external resource engines.
- **Modular Toggle Control**: Individually enable or disable external destination buttons from the Settings & Plugins page.

### 7. Global Localization & Gamified Trophies
- **7 Fully Localized Languages**: Simplified Chinese (`zh-CN`), Traditional Chinese (`zh-TW`), English (`en`), Italian (`it`), Japanese (`ja`), Spanish (`es`), and German (`de`).
- **PlayStation-Style Trophy System**: Unlock dozens of achievements for exploring, bookmarking, and querying your collection, complete with PSN-inspired frosted glass popups and sound cues.

---

## 🛠️ Technology Stack

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend UI Layer                      │
│  Vue 3 + Vite + TypeScript + Tailwind CSS + Lucide Icons   │
│  (Responsive Layout, Glassmorphism, i18n, State Management) │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Tauri IPC / High-Speed Binary Bridge)
┌──────────────────────────────▼──────────────────────────────┐
│                    Tauri v2 Host Layer                      │
│ Custom Asset Protocol (gpdb-img://), Window Control, Dialogs│
└──────────────────────────────┬──────────────────────────────┘
                               │ (Rust Native FFI)
┌──────────────────────────────▼──────────────────────────────┐
│                 Rust Core Engine (gpdb-core)                │
│ • Dynamic SQL Query Builder & Fuzzy Indexing (rusqlite)     │
│ • Multi-Tier Physical Image Resolver & Local Cache Validator│
│ • Async Background Task & LLM Runtime                       │
│ • Autonomous Schema Migration & Self-Healing Engine         │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Persistent Storage Layer                 │
│      GPDb.db (SQLite 3 WAL Mode) + image_cache/ (Local)     │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### For General Users (Recommended)

Download the pre-compiled installer directly from the [GitHub Releases](https://github.com/GeavenMax/GPDb/releases) page:
- **macOS**: Download `GPDb-macOS-v2.6.0.dmg`. Double-click and drag `GPDb.app` to your `Applications` folder.
- **Android** (Stand-alone client currently under development): Download the `.apk` package to install on your mobile device.

### For Developers

#### Prerequisites
- Node.js 20+ and npm
- Rust 1.78+ (`cargo`)
- macOS 12+ (Supports both Apple Silicon M-Series and Intel x86_64)

#### Build Instructions
```bash
# 1. Clone the repository
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. Enter desktop client directory
cd desktop_client

# 3. Install frontend dependencies
npm install

# 4. Launch in development mode
npm run tauri dev

# 5. Build production bundle (.app and .dmg)
npm run tauri build
```

---

## ⚖️ Legal Disclaimer

1. **Software Nature**:
   GPDb is an open-source, generic offline metadata indexing and local database management utility for personal digital media collections.
2. **No Content Hosting**:
   This repository and its distribution packages **do not contain, host, distribute, or link to any copyrighted video, audio, torrent, or image files**. Example metadata fields displayed within tests are strictly for structural schema verification and UI rendering validation.
3. **User Responsibility**:
   Users bear sole and exclusive legal responsibility for their personal database files, media assets, and any external links visited. Developers and contributors assume no liability for misuse.
4. **Age & Legal Compliance**:
   This software is intended strictly for adults of legal age in their respective jurisdiction. Please ensure full compliance with all applicable local laws and regulations.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE). You are free to inspect, modify, fork, and distribute this software provided that original attribution and this disclaimer are retained.
