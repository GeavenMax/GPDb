# GPDb · Blitzschneller, datenschutzorientierter Manager für schwule Erwachsenenfilme

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Ein moderner, leistungsstarker und kompromisslos privater Offline-Verwaltungsclient für Liebhaber und Sammler schwuler Erwachsenenfilme – ohne Cloud-Zwang und ohne Tracking.</strong>
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
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Android%20(In%20Dev)-blue?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=flat-square" alt="Category" />
  <img src="https://img.shields.io/badge/Architecture-Tauri%20v2%20%2B%20Rust%20%2B%20Vue%203-emerald?style=flat-square" alt="Tech Stack" />
  <img src="https://img.shields.io/badge/Database-SQLite%20(WAL%20Mode)-orange?style=flat-square" alt="Database" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=flat-square" alt="Privacy" />
  <img src="https://img.shields.io/badge/License-MIT-purple?style=flat-square" alt="License" />
</p>

---

## 📖 Über das Projekt (About GPDb)

**GPDb** (Gay Pornography Database Manager) ist ein fortschrittlicher, voll ausgestatteter **Offline-Datenbankmanager für schwule Erwachsenenmedien (Gay Adult Media)**, entwickelt für Sammler, Enthusiasten und Archivare digitaler Medien.

Im Bereich der Erwachsenenunterhaltung gehören Sehgewohnheiten, Favoriten und persönliche Vorlieben zu den **sensibelsten privaten Daten**. Kommerzielle Streaming-Dienste und Cloud-Plattformen bergen erhebliche Risiken durch Benutzerüberwachung, Datenlecks und plötzliche Löschungen bei Lizenzablauf.

**GPDb basiert auf einer strikten „100% Offline-First & Zero-Trace“-Philosophie**:
- **Keine Cloud-Abhängigkeit**: Alle Metadaten, Darstellerprofile, physischen Attribute, Bild-Caches, Notizen, Tags und Sammlungen verbleiben ausschließlich auf der physischen Festplatte Ihres Geräts.
- **Reine lokale Architektur**: Keine Benutzerkonten, keine Telemetrie, keine Nutzungsanalyse und keinerlei Hintergrundübertragung zu externen Servern.
- **Höchste Leistung**: Dank der nativen **Rust-Core-Engine (`gpdb-core`)** und moderner **Tauri v2 + Vue 3**-Architektur verwaltet GPDb mühelos **über 60.000 Filme, 100.000 Einzelszenen, 6.000 Darstellerprofile und 1.300 Filmstudios** mit flüssigen 60 fps und blitzschnellen Suchanfragen im Millisekundenbereich.

---

## ✨ Bereichsspezifische Hauptfunktionen

### 1. Detaillierte Filterung nach körperlichen Merkmalen
- **Präzise anatomische & morphologische Filter**:
  Filtern Sie Darsteller nach **Körperbau (Build)** (z. B. Muscle, Twink, Bear, Hunk), **Haarfarbe**, **Augenfarbe**, **Bart- und Körperbehaarung**, **Körpergröße & Gewicht**, **Hauttyp/Ethnie**, **Beschneidungsstatus (Foreskin) & Penisgröße** sowie **Tattoos & Piercings**.
- **Intelligente Pseudonym-Zusammenführung (Aliases)**:
  Führt unterschiedliche Künstlernamen desselben Darstellers über verschiedene Studios und Epochen hinweg automatisch zusammen.
- **Präzise Trennung von Hauptfilmen (Films) & Einzelszenen (Scenes)**:
  Unterscheiden Sie nahtlos zwischen vollwertigen Spielfilmen und einzelnen Episoden oder Clips.

### 2. Immersiver Startbildschirm im Streaming-Stil (Home Feed)
- **Großformatiges Poster-Karussell**: Sanft animierte, hochauflösende Titelplakate mit Parallaxe-Effekt.
- **Heute vor vielen Jahren (On This Day)**: Gleicht das heutige Datum mit Premieren aus den goldenen Jahrzehnten (80er, 90er, 2000er) ab.
- **Stars des Tages (Starlight Today)**: Zeigt Darsteller mit verifizierten, hochauflösenden Porträts aus dem lokalen Festplatten-Cache – ganz ohne generische Platzhalter.
- **Kultfilm-Reihen**: Hebt langlebige Filmreihen mit zahlreichen Teilen hervor.
- **Zufalls-Entdecker (Dice)**: Finden Sie per Zufallsgenerator verborgene Klassiker aus über 60.000 Werken.

### 3. Intelligente Serienbündelung & Dynamische Collagen-Cover
- **Automatische Cluster-Erkennung**: Erkennt römische Ziffern und Untertitel, um verstreute Teile (*Part I, Part II, Part III*) automatisch zu Serien zusammenzufassen.
- **Adaptive Poster-Collagen**: Erstellt automatisch elegante Collagen (1 Cover, 2 geteilte Hälften, 3 Kaskaden oder 4-Quadrant-Raster) für Serienalben – komplett offline gerendert.

### 4. Regisseur-Archive & Studio-Chroniken
- **Regie-Profile**: Öffnet mit einem Klick alle Werke eines Regisseurs zur direkten Filterung in der Bibliothek.
- **Umfassender Studio-Katalog**: Von Vintage-Pionieren (Falcon, Colt, Catalina usw.) bis zu modernen Marktführern (Men.com, BelAmi, Lucas Entertainment, Corbin Fisher usw.).

### 5. KI-Vorlieben-Analyse & Ästhetik-Profil (AI Persona Insights)
- **Vollständig asynchron**: Kommuniziert im Hintergrund mit lokalen oder entfernten Sprachmodellen (DeepSeek, OpenAI, Claude etc.), ohne die Oberfläche zu blockieren.
- **Ausführliche ästhetische DNA-Analyse**: Analysiert Ihre Sammlungen und generiert einen 2.000 Wörter umfassenden Kunstbericht samt persönlichem Typus (z. B. *„Nostalgischer Entdecker der Neuen Welle“*).
- **Echtzeit-Fortschritt im Frosted-Glass-Design**: Visualisiert Tokenisierung, Merkmalsextraktion und Profilerstellung transparent.

### 6. Ressourcensuche & Externe Web-Erweiterungen (v2.0)
- **Direktsprung mit 1 Klick**: Wechseln Sie direkt zur Darsteller- oder Filmsuche auf BoyfriendTV, Google und Film-Datenbanken unter Nutzung der englischen Originaltitel.
- **BT-Magnet-Generator**: Generiert standardisierte Suchabfragen aus Filmtitel und Studio für externe Suchmaschinen.
- **Individuelle Schalter**: Alle externen Such-Buttons können in den Einstellungen separat aktiviert oder deaktiviert werden.

### 7. Vollständige Mehrsprachigkeit & Trophäen-System
- **7 Benutzeroberflächen-Sprachen**: Vereinfachtes Chinesisch (`zh-CN`), Traditionelles Chinesisch (`zh-TW`), Englisch (`en`), Italienisch (`it`), Japanisch (`ja`), Spanisch (`es`) und Deutsch (`de`).
- **PlayStation-Trophäensystem**: Dutzende Errungenschaften fürs Stöbern und Sammeln mit flüssigen Frosted-Glass-Popups.

---

## 🛠️ Technologie-Stack

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend-Ebene (UI)                      │
│  Vue 3 + Vite + TypeScript + Tailwind CSS + Lucide Icons   │
│ (Responsives Layout, Frosted Glass, i18n, State Management) │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Tauri IPC / High-Speed Binary)
┌──────────────────────────────▼──────────────────────────────┐
│                    Tauri v2 Host-Ebene                      │
│ Asset-Protokoll (gpdb-img://), Fenstersteuerung, Dialoge    │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Rust Native FFI)
┌──────────────────────────────▼──────────────────────────────┐
│                 Rust Core-Engine (gpdb-core)                │
│ • Dynamischer SQL-Query-Builder & Fuzzy-Suche (rusqlite)    │
│ • Physische Bildprüfung & lokaler Cache-Resolver            │
│ • Asynchroner Task- und LLM-Runtime                         │
│ • Automatische Schema-Migration & Selbstheilung             │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                   Persistente Speicherebene                 │
│      gevi.db (SQLite 3 WAL-Modus) + image_cache/ (Lokal)    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Schnellstart

### Für Endanwender (Empfohlen)

Laden Sie das vorkompilierte Installationspaket direkt von der [Releases-Seite](https://github.com/GeavenMax/GPDb/releases) herunter:
- **macOS**: Laden Sie `GPDb-macOS-v2.6.0.dmg` herunter, öffnen Sie die Datei und ziehen Sie `GPDb.app` in Ihren `Programme`-Ordner.
- **Android** (Eigenständige App in Entwicklung): Laden Sie die `.apk`-Datei herunter und installieren Sie diese auf Ihrem Gerät.

### Für Entwickler (Lokaler Build)

#### Voraussetzungen
- Node.js 20+ und npm
- Rust 1.78+ (`cargo`)
- macOS 12+ (Unterstützt Apple Silicon M-Serie und Intel x86_64)

#### Schritte
```bash
# 1. Repository klonen
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. In das Desktop-Client-Verzeichnis wechseln
cd desktop_client

# 3. Abhängigkeiten installieren
npm install

# 4. Im Entwicklungsmodus starten
npm run tauri dev

# 5. Produktionspaket erstellen (.app und .dmg)
npm run tauri build
```

---

## ⚖️ Rechtlicher Hinweis & Haftungsausschluss

1. **Zweck der Software**:
   GPDb ist ein quelloffenes, universelles Offline-Metadaten- und Bibliotheksverwaltungswerkzeug für persönliche digitale Mediensammlungen.
2. **Keine Inhalte im Repository**:
   Der Quellcode und die offiziellen Release-Pakete **enthalten, hosten oder verteilen keinerlei urheberrechtlich geschützte Video-, Audio-, Torrent- oder Bilddateien**.
3. **Eigenverantwortung**:
   Die Verantwortung für verwaltete lokale Datenbanken und das Aufrufen externer Links liegt uneingeschränkt beim Nutzer.
4. **Volljährigkeit & Gesetzeskonformität**:
   Diese Software richtet sich ausschließlich an volljährige Personen im Sinne der geltenden Gesetze Ihres Landes.

---

## 📄 Lizenz

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE). Sie dürfen den Code frei verwenden, modifizieren und weitergeben, solange Urheberrechtshinweis und Haftungsausschluss erhalten bleiben.
