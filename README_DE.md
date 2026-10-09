# GPDb · Blitzschneller, privater Offline-Manager für schwule Erwachsenenmedien

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Leistungsstarker, detailreicher und kompromisslos privater nativer Desktop- & Mobile-Client für Liebhaber schwuler Erwachsenenmedien – 100% offline und null Cloud-Tracking.</strong>
</p>

<p align="center">
  <a href="./README.md">简体中文</a> |
  <a href="./README_EN.md">English</a> |
  <a href="./README_ZH_TW.md">繁體中文</a> |
  <a href="./README_JA.md">日本語</a> |
  <a href="./README_DE.md"><b>Deutsch</b></a> |
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

> 📢 **Offizieller Kanal**: Abonnieren Sie den [offiziellen GPDb Telegram-Kanal (@gpdbnews)](https://t.me/gpdbnews) für Release-Updates, inkrementelle Datenbank-Aktualisierungen und Praxistipps!

---

## 📖 Projektpositionierung (About GPDb)

**GPDb** (*Gay Pornography Database Manager*) ist ein modernes, voll ausgestattetes **Offline-Datenbankverwaltungssystem für schwule Erwachsenenmedien (Gay Adult Media)**, entwickelt für Sammler und Archivare.

**Kompromisslose Philosophie: 100% Offline-First & Absolute Privatsphäre**:
- **Null Cloud-Abhängigkeit**: Medien-Metadaten, Darsteller-Körperprofile, Cover-Caches und persönliche Notizen verbleiben ausnahmslos auf dem lokalen Speicher;
- **Reine Standalone-Architektur**: Keine Benutzerkonten, keine Telemetrie- oder Tracking-Dienste, keinerlei Datentransfer an externe Server;
- **Native Höchstleistung**: Angetrieben von einem **hochperformanten Rust-Kern (`gpdb-core`)** + **Tauri v2 + Vue 3** (macOS / Windows) sowie **Kotlin + Jetpack Compose + Room** (natives Android) – meistert **63.000+ Filme, 134.000+ Einzelszenen, 108.000+ Darsteller und 2.400+ Studios** mit flüssigen 60 fps und Millisekunden-Suchgeschwindigkeiten.

---

## ✨ Kern-Highlights (Core Highlights)

### 1. Detaillierte Darsteller-Körperprofile & fehlertolerante Verbundsuche
- **Granulare physiologische Filterung**: Gezielte Filter nach Körperbau (Build), Haar-/Augenfarbe, Körperbehaarung, Größe, Gewicht, Hauttyp, anatomischen Merkmalen (Dick Size / Foreskin) sowie Tattoos und Piercings.
- **Intelligente Pseudonym-Zusammenführung (AKA)**: Automatische Bündelung unterschiedlicher Künstlernamen über Epochen und Studios hinweg – kein Eintrag geht verloren.
- **Präzise Trennung von Hauptfilmen & Einzelszenen**: Klare Unterscheidung zwischen abendfüllenden Spielfilmen und Gastauftritten in einzelnen Szenen.
- **Fehlertolerante Verbundsuche**: FTS5-Volltextindex mit SQL-Mehrfeld-Fallback; zweisprachige Suche (DE/EN/ZH) nach Titel, Studio, Regisseur, Handlungsbeschreibung und Darstellern.
- **PBC-Enzyklopädie-Integration**: Umfassende Datenanbindung an die Porn Base Central (bürgerlicher Name, Debütjahr, Aktivitätsstatus, Biografie sowie Querverlinkungen zu IAFD/IMDb/X).

### 2. Immersiver Streaming-Startbildschirm & Multi-Cover-Galerie
- **Fünf Entdeckungs-Feeds**: Hero-Großbild-Karussell, „Heute vor Jahren“-Klassikerpremieren, Stars des Tages mit Porträt-Garantie, legendäre Filmreihen und Zufalls-Entdecker (Blindbox).
- **Multi-Cover-Galerie**: Horizontale, adaptive Kachelung für Vorder- und Rückseiten-Cover sowie alternative Artworks mit Front-/Back-Badges.
- **Vollbild-Zoom-Leuchtkasten**: Stufenloser Zoom per Mausrad (1,0x – 5,0x), Drag-to-Pan, Doppelklick-Zoom und mobile Zwei-Finger-Gestensteuerung.

### 3. Smarte Filmreihen & adaptive Cover-Collagen
- **Automatisches Clustering**: Intelligente Erkennung römischer Ziffern und Untertitel fasst zusammengehörige Teile automatisch zu Serien zusammen.
- **Adaptive Kunst-Collagen**: Automatische Generierung symmetrischer 1- bis 4-teiliger Poster-Collagen mit blitzschnellem Offline-Rendering.

### 4. Regisseur-Profile & 2.400+ Studio-HD-Archive
- **Regisseur-Profilkarten**: Filmografie per Klick aufrufen und gezielt nach Werken des jeweiligen Regisseurs filtern.
- **100% Studio-Logo- & Banner-Abdeckung**: Alle 2.490+ Studios mit verlustfreien WebP-Logos ausgestattet; 690+ Studios verfügen über offizielle Breitbild-Banner; 98% Logo-Abdeckung bei Filmtiteln.
- **Ergonomisches Detail-Layout**: Vollflächig lesbare Handlungsbeschreibungen und kompakt gruppierte Aktionsschaltflächen.

### 5. 📸 Blickschutzmodus & Ästhetische Sharing-Karten (v2.18.0)
- **Globaler Ein-Klick-Blickschutz**: Umschalter in der Desktop-Titelleiste und Android-Startansicht; aktiviert sofort Gaußsche Unschärfe auf Covern (`blur(24px)`) und Handlungsbeschreibungen (`blur(7px)`).
- **Fluide Dual-Cover-Sharing-Karten**:
  - Paralleles Vorder- und Rückseiten-Layout, verzerrungsfreier 16:9-Zuschnitt für Szenenbilder;
  - Bilineare Offscreen-Unschärfe-Aura mit Stil-Presets (*Vibrant*, *Dark*, *Midnight*);
  - Integrierter Telegram-Kanal-QR-Code (`t.me/gpdbnews`);
  - 2x Retina-Canvas-Export auf dem Desktop / Native Bitmap-Erfassung auf Android für direktes Teilen.

### 6. 🛡️ Datenschutz- & Sicherheits-Suite
- **Schlanker Desktop-Client**: Verzicht auf redundante Tarnmodi zugunsten einer puristischen, fokussierten Medienarchivierung.
- **Biometrische App-Sperre (Mobil)**: PIN-Code, Fingerabdruck und Face-Unlock mit automatischem Unschärfe-Schutz bei Fokusverlust.
- **Systemeigene Sicherheitsisolierung**: Android-`FLAG_SECURE` gegen Screenshots und App-Vorschauen; physische Sandbox mit `.nomedia`-Dateien schützt vor externen Galerie-Scans.

### 7. Native Rust In-Process LLM-Übersetzungs-Engine
- **Reine asynchrone Rust-TLS-Engine**: Keine Python-Laufzeitumgebung erforderlich; extrem schnelle Einzel- und Stapelübersetzungen.
- **Multi-Modell-Unterstützung & Key-Rotation**: Google Gemini mit automatischer Key-Pool-Rotation bei Kontingenterschöpfung; OpenAI-kompatible Protokolle (DeepSeek, Claude, Moonshot, Ollama etc.).
- **Fehlertolerante Textverarbeitung**: Verarbeitet reine Textausgaben robust ohne JSON-Parsing-Abbrüche; verlustfreier Im-/Export übersetzter Daten als Standard-JSON.

### 8. Ressourcensuche & Scraper-Plugins (v2.0)
- **Gezielte Direktsprünge**: Ein-Klick-Weiterleitung zu BoyfriendTV, Google, Fachportalen und BT-Magnet-Suchanfragen.
- **PBC-Wiki-Scraper**: Detaillierte Erfassung von 1.200+ Darstellern, Revisionserkennung und 25+ Attributabgleiche (97,3 % Trefferquote).
- **SmutJunkies-Scraper**: Vollständiger A–Z-Index für 6.700+ Darsteller mit 4-stufigem Fehlertoleranz-Algorithmus und bidirektionaler Synchronisation.

### 9. 7 Benutzeroberflächen-Sprachen & Schlanke Systemarchitektur
- **7 native UI-Sprachen**: Vereinfachtes Chinesisch, Traditionelles Chinesisch, Englisch, Italienisch, Japanisch, Spanisch und Deutsch.
- **1.096 Lokalisierungs-Keys**: Vollständige Konsistenz mit intelligenten Fallbacks zur Vermeidung von Platzhaltern.
- **Getrennte Sprachverwaltung & maximale Effizienz**: Unabhängige Steuerung von Oberflächen- und Metadatensprache; Entfernung virtueller Trophäen für minimale Speicherauslastung.

### 10. Universelles Backup & Plattformübergreifendes Auto-Update
- **Universelles JSON-Backup (`GPDb_Backup.json`)**: Nahtloser Export und Import von Favoriten, Historie, Notizen und Konfigurationen zwischen macOS, Windows und Android.
- **Integrierte Update-Prüfung**: Versionsabgleich über die GitHub Releases API mit stabiler Wiederaufnahme unterbrochener Downloads.

### 11. Native Systemoptimierung
- **macOS**: Tauri v2 + Rust, optimiert für Apple Silicon und Intel, native Milchglas-Effekte und Systemkürzel.
- **Windows**: Windows 11 Mica / Acrylic-Effekte, kontrastoptimiertes helles Design, System-Tray-Integration, Taskleisten-Fortschrittsanzeige bei Scraper-Vorgängen, WebView2-GPU-Hardwarebeschleunigung.
- **Android**: Natives Jetpack Compose + Room mit optimierten Touch-Gesten und vollständiger Offline-Nutzung.

---

## 🛠️ Technologie-Architektur (Technology Stack)

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
                                   │         Persistente Speicherebene (Storage)         │
                                   │      GPDb.db (SQLite 3 WAL) + image_cache/          │
                                   └─────────────────────────────────────────────────────┘
```

---

## 🚀 Schnellstart & Download (Quick Start)

### Installationspakete (Empfohlen)

Laden Sie das offizielle Installationspaket für **`v2.18.0`** direkt von der [Releases-Seite](https://github.com/GeavenMax/GPDb/releases) herunter:

| Plattform | Dateiname | Installationshinweise |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.18.0.dmg` | DMG öffnen und `GPDb.app` in den Ordner `Programme` (`Applications`) ziehen.<br>*(Falls eine Notarisierungs-Warnung erscheint: „Systemeinstellungen → Datenschutz & Sicherheit“ → „Trotzdem öffnen“)* |
| **🪟 Windows** | `GPDb-Windows-v2.18.0.exe` | Installationsprogramm per Doppelklick ausführen. NSIS-Einzelbenutzer-Architektur ohne Administratorrechte erforderlich. |
| **🤖 Android** | `GPDb-Android-v2.18.0-signed.apk` | APK auf das Smartphone herunterladen und direkt installieren (offiziell signiert; ggf. Installation aus unbekannten Quellen erlauben). |

---

### 💻 Entwickleranleitung (Developer Guide)

```bash
# 1. macOS Desktop-Client
cd desktop_client && npm install && npm run tauri dev      # Lokale Entwicklung
npm run tauri build                                         # Produktions-Build (.app / .dmg)

# 2. Windows Desktop-Client
cd Windows_client && npm install && npm run tauri dev      # Lokale Entwicklung
npm run tauri build                                         # Produktions-Build (.exe)

# 3. Android nativer Mobile-Client
cd android_client && ./gradlew assembleRelease              # Release-APK kompilieren und signieren

# 4. Automatisierte Pre-Release-Prüfung
./git_tasks/pre_release_check.sh                            # Pfadkonformität, Datenschutz und Versionskonsistenz validieren
```

> 💡 Weiterführende Dokumentation:
> - Spezifikation für Benutzeroberflächen-Lokalisierung: [`docs/i18n/UI_I18N_SPEC.md`](docs/i18n/UI_I18N_SPEC.md)
> - Studio-HD-Logo-System: [`studio_logos/README.md`](studio_logos/README.md)
> - Automatisierter Release-Workflow: [`git_tasks/README.md`](git_tasks/README.md)

---

## ⚖️ Rechtlicher Hinweis & Haftungsausschluss (Disclaimer)

1. **Zweck der Software**: GPDb ist ein **universelles, quelloffenes Offline-Verwaltungswerkzeug für Mediendatenbanken und Metadaten-Indexierung**.
2. **Frei von Inhalten**: Das Projekt und seine Release-Pakete **enthalten, hosten oder vertreiben keinerlei urheberrechtlich geschützte Video-, Audio- oder Bilddateien**. Alle im Quellcode enthaltenen Beispieldaten dienen ausschließlich Test- und Validierungszwecken der Benutzeroberfläche und Datenbankstruktur.
3. **Eigenverantwortung**: Sämtliche Handlungen und rechtlichen Pflichten bei der lokalen Datenbankverwaltung sowie der Nutzung externer Weblinks liegen allein in der Verantwortung des Nutzers.
4. **Gesetzliche Vorgaben**: Die Software richtet sich ausschließlich an volljährige Nutzer im Rahmen der geltenden lokalen Gesetze und Bestimmungen.

---

## 📄 Lizenz (License)

Dieses Projekt ist unter der [MIT-Lizenz](LICENSE) lizenziert.
