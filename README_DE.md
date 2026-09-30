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
  <a href="https://t.me/gpdbnews" target="_blank"><img src="https://img.shields.io/badge/Telegram-Channel%20%40gpdbnews-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Channel" /></a>
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Android-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Version-v2.14.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Offizieller Telegram-Kanal**: Abonnieren Sie den [Offiziellen GPDb Telegram-Kanal (https://t.me/gpdbnews)](https://t.me/gpdbnews) für die neuesten Release-Updates, inkrementelle Datenbank-Updates und Anwendungstipps!

---

## 📖 Über das Projekt (About GPDb)

**GPDb** (Gay Pornography Database Manager) ist ein fortschrittlicher, voll ausgestatteter **Offline-Datenbankmanager für schwule Erwachsenenmedien (Gay Adult Media)**, entwickelt für Sammler, Enthusiasten und Archivare digitaler Medien.

Im Bereich der Erwachsenenunterhaltung gehören persönliche Sehgewohnheiten, Favoriten und sexuelle Präferenzen zu den **sensibelsten und privatesten Daten überhaupt**. Kommerzielle Streaming-Dienste und zentralisierte Online-Plattformen bergen erhebliche und unkontrollierbare Risiken durch Benutzerüberwachung, Datenlecks sowie abrupte Löschungen bei Lizenzablauf.

**GPDb basiert auf einer strikten „100% Offline-First & Absolut-Privat"-Philosophie**:
- **Keine Cloud-Abhängigkeit**: Alle Medien-Metadaten, Darstellerprofile, körperliche Attribute, Poster-/Szenenbild-Caches, persönliche Tags, Bewertungen und Sammlungsverläufe verbleiben ausschließlich auf der physischen Festplatte Ihres Geräts.
- **Reine lokale Architektur**: Keine Benutzerkonten, keine Telemetrie, keine Nutzungsanalyse und keinerlei Datenübertragung an externe Server.
- **Höchste Leistung**: Dank der hochleistungsfähigen **Rust-Engine (`gpdb-core`)** sowie **Tauri v2 + Vue 3** (macOS / Windows Desktop) und **Kotlin + Jetpack Compose + Room** (natives Android), verwaltet GPDb mühelos **über 60.000 vollständige Filme, 100.000 Einzelszenen, 6.000 Darstellerprofile und 1.300 klassische und moderne Filmstudios** – bei konstant flüssigen 60 fps und blitzschnellen Suchanfragen im Millisekundenbereich.

---

## ✨ Bereichsspezifische Hauptfunktionen (Domain-Specific Features)

### 1. Umfassende Filterung nach körperlichen Darstellermerkmalen
- **Hochgradig präzise physiologische & morphologische Filter**:
  Filtern Sie Darsteller nach **Körperbau (Build)** (z. B. Muscle / Twink / Bear / Hunk), **Haarfarbe**, **Augenfarbe**, **Bart- und Körperbehaarung (Facial Hair / Body Hair)**, **Körpergröße & Gewicht**, **Hauttyp (Skin)**, **anatomischen Merkmalen (Dick Size / Foreskin)** sowie **Tattoos & Piercings** – frei kombinierbar in Mehrfachfiltern.
- **Vollständige Pseudonym-Synchronisierung (Aliases)**:
  Unterschiedliche Künstlernamen desselben Darstellers über verschiedene Studios und Epochen hinweg werden automatisch intelligent zusammengeführt – kein Werk geht durch einen Namenswechsel verloren.
- **Präzise Trennung von Spielfilmen (Films) & Einzelszenen (Scenes)**:
  Zeigen Sie separat vollständige Spielfilme, in denen der Darsteller die Hauptrolle übernimmt, sowie eigenständige Kurzszenen, in denen er als Gastauftritt mitwirkt – die vollständige Filmografie auf einen Blick.
- **Fehlertolerante Mehrfeld-Suchmaschine**:
  - FTS5-Schnellsuche → automatischer Fallback auf Standard-SQL-Mehrfeldsuche → Room/SQLite-Absicherung; verhindert Nulltreffer durch Tokenisierungsprobleme oder fehlende virtuelle Tabellen.
  - Eine einzige Suchanfrage durchsucht gleichzeitig englischen Originaltitel (`title`), chinesische Übersetzung (`title_zh`), Studioname (`studio_name`), Regisseur (`director_name`), chinesische Beschreibung (`description_zh`) sowie die gesamte Besetzung.
- **Mehrdimensionale Zeit- und Aktualitätsfilter**:
  - Unterstützt „Alle", „Zuletzt hinzugefügt (nach Scraping-Zeitpunkt)", „Letzte 7 Tage", „Letzte 30 Tage", „Letzte 90 Tage" und „Dieses Jahr".
  - Veröffentlichungsdaten werden für Spielfilme (nach Jahr) und Einzelszenen (nach Datum) getrennt behandelt.
  - Neu hinzugefügte Einträge erhalten ein dynamisch animiertes `NEW`-Highlight-Badge.

### 2. Streaming-ähnlicher Immersions-Startbildschirm & 3D-Sammlerkarten
- **Fünf Entdeckungs-Streams**:
  - **Hero-Karussell (Hero Carousel)**: Hochauflösende Poster-Großdias mit sanften Parallaxe-Effekten oben auf der Startseite.
  - **Heute in der Geschichte (On This Day)**: Gleicht das heutige Datum mit historischen Premieren aus den goldenen Jahrzehnten (80er, 90er, 2000er) ab.
  - **Stars des Tages (Star Spotlight)**: Überprüft lokal vorhandene HD-Portraitdateien auf der Festplatte und empfiehlt ikonische Darsteller – ganz ohne generische Buchstabenplatzhalter.
  - **Kultfilm-Reihen (Iconic Series)**: Hebt langlebige Filmreihen mit zehn oder mehr Teilen automatisch hervor.
  - **Zufalls-Entdecker (Lucky Discovery)**: Per Knopfdruck einen zufälligen Geheimtipp aus über 60.000 Werken entdecken.
- **Zwei Poster-Layoutmodi & Vollbild-Leuchtkasten mit Gesten-/Radzoom**:
  - **Adaptive Hochauflösungs-Galerie (`adaptive_pager`)** & **3D-Sammlerkarten (`flip_3d`)**: 60 fps CSS-3D-Tiefenperspektive, physisch anmutende Flip-Kapseln und sammelwürdige Umgebungsbeleuchtung.
  - **Vollbild-Leuchtkasten (`ImageLightbox.vue` / Compose-Zoom)**: Unterstützt sanftes Mausrad-Zoomen (1,0x – 5,0x), Klicken-und-Ziehen zum Verschieben, Doppelklick-Zoom/Reset, Tastaturkürzel (+/-/0/Esc) und mobiles Zwei-Finger-Pinch-Zoom.

### 3. Intelligente Seriengruppierung & Collage-Cover (Smart Series & Collage Covers)
- **Automatischer Clustering-Algorithmus**: Erkennt römische Ziffern und Untertitel, um verstreute Serienteile (z. B. *Part I, Part II, Part III*) automatisch zu kohärenten Reihen zusammenzufassen.
- **Adaptive Poster-Collagen**: Erstellt automatisch 1 Einzelposter, 2 symmetrische Hälften, 3 Kaskaden oder 4-Quadrant-Raster als Kunst-Collagen-Cover für Serienalben – mit Vignetten-Glow und komplett offline gerendert.
- **Serien-Sammlung**: Fügen Sie Lieblingsserien mit einem Klick zu den „Gespeicherten Reihen" als eigenständige Lesezeichen hinzu.

### 4. Regisseur-Profile & Studio-Chroniken
- **Regisseur-Steckbriefe**: In der Filmdetailansicht lässt sich per Klick die vollständige Filmografie eines Regisseurs aufrufen, inklusive direkter Filterung in der Bibliothek.
- **Umfassender Studio-Katalog**: Von klassischen Analogfilm-Pionieren (Falcon, Colt, Catalina u. a.) bis hin zu modernen Marktführern (Men.com, BelAmi, Lucas Entertainment, Corbin Fisher u. a.) – Filmchroniken und Stilmerkmale auf einen Blick.

### 5. 📸 Screenshot-Datenschutzmodus & Hochwertige Glanz-Sharingkarten (Neu in v2.13.0)
- **Globaler Ein-Klick-Screenshot-Datenschutzmodus**:
  - Die obere Navigationsleiste bietet einen Ein-Klick-Datenschutz-Augenschalter; unterstützt Tastenkürzel und sofortige Statusumschaltung (Datenschutz aktiv / Normales Browsing).
  - **Feingranulare Datenschutz-Maskierung**:
    - `Poster und Szenenbilder unscharf`: Globale Gaußsche Unschärfe (`blur(24px)`) auf alle Filmcover, Szenenbilder und Darsteller-Avatare – verhindert die versehentliche Preisgabe sensibler Bilder bei Screenshots oder Social-Media-Shares.
    - `Handlungsbeschreibungen und sensiblen Text unscharf`: Gaußsche Unschärfe (`blur(7px)`) auf Filmsynopsen und Szenenbeschreibungen, Textauswahl deaktiviert – schützt vor unbeabsichtigten Inhaltsleaks.
  - Android-Mobile-Ende: Basiert auf dem reaktiven `LocalPrivacyBlur`-Zustandsfluss für sofortige vollbildschirmweite Wirksamkeit.
- **Adaptiver Poster-Glanz-Sharingkarten-Generator**:
  - Erstellt mit einem Klick Apple-Music-/Spotify-artige Glanz-Sharingkarten direkt aus Film- und Szenendetailansichten.
  - Hintergrundleuchten wird direkt aus dem Cover-Farbton extrahiert und mit hochpräziser Gaußscher Großradius-Unschärfe (`blur(45px–60px)`) gerendert – drei exklusive Glow-Voreinstellungen: „Vibrant (Leuchtend)", „Dark (Dunkel)" und „Midnight (Mitternacht)".
  - **Datenschutz vor dem Teilen**: Separat anwählbar „Poster unscharf" und „Text unscharf" – für sicheres Teilen in Chats und im öffentlichen Netz.
  - **Verlustfreier Export auf beiden Plattformen**:
    - **Desktop**: HTML5-Canvas-2D-Engine (offline, 2x Retina) rastert die Karte in höchster Auflösung; Ein-Klick-Kopieren als PNG in die Systemzwischenablage (direkt einfügen in WeChat / QQ / Telegram / Discord / X) oder lokales Speichern.
    - **Mobil**: Compose-1.8-Hardware-beschleunigte Bitmap-Aufnahme-Engine; Ein-Klick-Speichern ins Systemalbum (`MediaStore`) und Öffnen des nativen Android-Teilen-Panels via `FileProvider`.

### 6. 🛡️ Umfassendes Datenschutz- & Sicherheits-Suite (Enterprise-Grade Privacy & Security)
- **PIN-Zahlencode-Sperrbildschirm & Fokusverlust-Schutz**:
  - Unterstützt einen eigenständigen 4–6-stelligen numerischen PIN-App-Sperrmechanismus; bei Fokusverlust oder Zeitüberschreitung (1/5/15/30 Minuten) wird automatisch ein Glasmorphismus-Sperrbildschirm (`AppLockOverlay.vue`) eingeblendet.
  - Bei Fensterfokusverlust oder App-Wechsel wird sofort eine Gaußsche Milchglas-Datenschutzebene darübergelegt.
- **Notfall-Tarnmodus: Panic-Switch-Rechner**:
  - Blitzschnelles Umschalten in einen Apple-Design-getreuen, voll funktionsfähigen Dunkelrechner (`FakeCalculatorModal.vue`) mit echter Grundrechenlogik.
  - Desktop: Tastenkürzel <kbd>Cmd + Shift + P</kbd> für sofortigen Aufruf; Mobile: Geste (Bildschirm umdrehen/umklappen) für sofortigen Aufruf.
  - Korrekten PIN im Rechner eingeben und `=` drücken, oder 4-mal auf den Kopftitel tippen, um sicher zurück zur Bibliothek zu gelangen.
- **Harmlose App-Titel- & Desktop-Icon-Tarnung**:
  - Unterstützt benutzerdefinierte Fenstertitel (z. B. „Calculator", „Notes"), um Preisgabe im System-Task-Switcher zu vermeiden.
  - Integriert mehrere hochwertige Design-Theme-Icons (z. B. „Dual-Hero Mars Totem", „Obsidian Film Vault") sowie harmlose Tarnsymbole (Notizzettel, Buchführung, Taschenrechner).
- **Mobile System-Level-Isolation**:
  - Android-System-`FLAG_SECURE` gegen Bildschirmaufnahme, Screenshots und Multitasking-Kartenvorschauen.
  - Physische Sandbox-Speicherisolierung; rekursive `.nomedia`-Injektion verhindert Drittanbieter-Galerie-Scans vollständig.

### 7. KI-Vorlieben-Analyse & Mehrsprachige LLM-Übersetzung
- **KI-Vorlieben-Analyse & Ästhetik-Profil (AI Persona Insights)**:
  - **Vollständig asynchrone Architektur**: Ein Hintergrundthread kommuniziert mit lokalen oder entfernten Großen Sprachmodellen (z. B. DeepSeek, OpenAI, Claude) – die Benutzeroberfläche bleibt jederzeit reaktionsfähig.
  - **Ausführliche ästhetische DNA-Analyse**: Analysiert die echten, privaten Sammlungen und Tags des Nutzers, destilliert ästhetische Grundmuster und erstellt einen 2.000-Wörter-Kunstbericht mit „Kern-Ästhetik-Archetyp-Code" (z. B. *„Nostalgischer Entdecker der Neuen Welle"*) und „Zeitgenössischem Audiovisuellen Spektrum".
  - **Echtzeit-Fortschritts-Popup**: Luxuriöses Milchglas-Schwebefeld, das schrittweise LLM-Handshake, Merkmalextraktion und Profilgenerierung anzeigt.
- **LLM-Mehrsprachige Übersetzung & Plattformübergreifende Datensynchronisierung**:
  - Nativ integriert: OpenAI, DeepSeek, Claude, Gemini, ByteDance Doubao und weitere Anbieter.
  - „Verfügbare Modelle erkunden" prüft per Knopfdruck die erreichbaren API-Modelle.
  - Übersetzungsdaten können plattformübergreifend (macOS / Windows / Android) als Standard-JSON bidirektional importiert und exportiert werden – verlustfrei.

### 8. Ressourcensuche & Externe Web-Erweiterungen (v2.0)
- **Direktsprung mit einem Klick**: Öffnen Sie in Film- und Darsteller-Detailansichten mit einem Klick BoyfriendTV, Google und führende Filmdatenbanken unter Nutzung des englischen Originaltitels – kein erneutes Eintippen erforderlich.
- **BT-Magnet-Suchintegration**: Kombiniert Filmtitel und Studio zu einer standardisierten Suchabfrage für externe Ressourcen-Engines.
- **Individuelle Schalter**: Das Plugin-Center erlaubt das individuelle Aktivieren und Deaktivieren jeder einzelnen externen Suchquelle.
- **Inkrementelles Scraping & Synchronisations-Engine**: Unterstützt bedarfsgesteuerte Online-Metadaten-Aktualisierungen und lokales Offline-Caching von Postern und Szenenbildern.

### 9. Vollständige Internationalisierung & 77+ Gamification-Trophäen-System
- **7 vollständig lokalisierte Benutzeroberflächen-Sprachen**: Vereinfachtes Chinesisch (`zh-CN`), Traditionelles Chinesisch (`zh-TW`), Englisch (`en`), Italienisch (`it`), Japanisch (`ja`), Spanisch (`es`) und Deutsch (`de`).
- **PlayStation-Stil-Trophäen-System**:
  - Über 77 Entdeckungs-, Such-, Fokus- und Sammlungs-Errungenschaften; Entsperren löst PSN-artige Flüssig-Animations-Popups aus.
  - Basiert auf echten Verhaltensmetriken; unterstützt unabhängiges Zurücksetzen und Prüfen von Trophäendaten.

### 10. Plattformübergreifende Abdeckung & Dreifach-Datensynchronisierung (macOS / Windows / Android)
- **macOS nativer Desktop-Client (`desktop_client/`)**: Über Tauri v2 + Rust-Architektur vollständige Unterstützung von Apple Silicon und Intel; native Glasmorphismus-Ästhetik und Tastaturkürzel.
- **Windows nativer Desktop-Client (`Windows_client/`)**: Eigenständiges Verzeichnis mit dedizierter Entkopplung; integrierte Multi-Pfad-Python-Intelligenz-Parser-Engine, Unterdrückung des Konsolen-Schwarzfensters (`CREATE_NO_WINDOW`), Microsoft YaHei-Schriftglättung, schlanke Scrollbalken, Win11-Snap-Layout-Unterstützung und schlanke NSIS-Einzelbenutzer-Installation ohne Administratorrechte.
- **Nativer Android-Mobile-Client (`android_client/`)**: Vollständig nativ mit Kotlin + Jetpack Compose + Room gebaut; nahtlose Datenkompatibilität mit dem Desktop-Client – für unterwegs jederzeit offline stöbern, taggen und sammeln.

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

### Für Endanwender (Empfohlen)

Laden Sie das aktuelle vorkompilierte Installationspaket **`v2.14.0`** direkt von der [Releases-Seite](https://github.com/GeavenMax/GPDb/releases) herunter:

| Plattform | Installationspaket | Installation & Hinweise |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.14.0.dmg` | DMG öffnen, `GPDb.app` in den `Programme`-Ordner ziehen.<br>*(Falls beim ersten Start eine Warnung wegen fehlender Notarisierung erscheint: „Systemeinstellungen → Datenschutz & Sicherheit" → „Trotzdem öffnen", oder Terminal: `sudo xattr -cr /Applications/GPDb.app`)* |
| **🪟 Windows** | `GPDb-Windows-v2.14.0.exe` | Installer doppelklicken und Installation abschließen. Nutzt NSIS-Einzelbenutzer-Architektur – keine Administratorrechte erforderlich. |
| **🤖 Android** | `GPDb-Android-v2.14.0-signed.apk` | APK auf das Gerät laden und direkt installieren (offiziell signiert; bei Systemaufforderung „Installation aus unbekannten Quellen" erlauben). |

---

### 💻 Entwickler: Lokale Kompilierung & Ausführung (Developer Guide)

#### Systemvoraussetzungen
- **Node.js**: 20+ und npm
- **Rust**: 1.78+ (`rustup`, `cargo`)
- **Android SDK** (nur für Android-Client erforderlich): Android Studio Hedgehog+ oder JDK 17+

#### 1. macOS Desktop-Client – Entwicklung & Build
```bash
# 1. Repository klonen
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. In das Desktop-Client-Verzeichnis wechseln
cd desktop_client

# 3. Frontend-Abhängigkeiten installieren
npm install

# 4. Im Entwicklungsmodus starten
npm run tauri dev

# 5. Produktionspaket erstellen (.app und .dmg automatisch gepackt)
npm run tauri build
```

#### 2. Windows Desktop-Client – Entwicklung & Build
```powershell
# 1. In das eigenständige Windows-Client-Verzeichnis wechseln
cd Windows_client

# 2. Frontend-Abhängigkeiten installieren
npm install

# 3. Im Entwicklungsmodus starten
npm run tauri dev

# 4. Produktionsinstaller erstellen (erzeugt NSIS-.exe-Installer)
npm run tauri build

# Oder direkt das vorkonfigurierte Build-Skript ausführen:
.\build_windows.ps1
```

#### 3. Android nativer Mobile-Client – Kompilierung
```bash
# 1. In das native Android-Client-Verzeichnis wechseln
cd android_client

# 2. Debug-Test-APK kompilieren
./gradlew assembleDebug

# 3. Offiziell signiertes Release-APK kompilieren
./gradlew assembleRelease
```

---

## ⚖️ Rechtlicher Hinweis & Haftungsausschluss (Disclaimer)

1. **Zweck der Software**:
   Diese Software (GPDb) ist ausschließlich ein **universelles, quelloffenes Offline-Metadaten-Indexierungs- und Bibliotheksverwaltungswerkzeug für Erwachsenenmedien (Universal Offline Adult Media Metadata & Library Management Tool)** für den persönlichen Gebrauch.
2. **Keine Inhalte im Repository**:
   Der Quellcode und die offiziellen Release-Pakete **enthalten, hosten oder verteilen keinerlei urheberrechtlich geschützte Video-, Audio-, Torrent- oder Bilddateien**. Beispieldaten im Quellcode dienen ausschließlich zur technischen Datenbankvalidierung und UI-Layouttests.
3. **Eigenverantwortung des Nutzers**:
   Alle Handlungen und rechtlichen Verantwortlichkeiten im Zusammenhang mit der Verwaltung persönlicher lokaler Datenbankdateien oder der Nutzung externer Suchverlinkungen liegen ausschließlich beim Nutzer – der Softwareentwickler und die Open-Source-Mitwirkenden tragen keinerlei Verantwortung.
4. **Volljährigkeit & Gesetzeskonformität**:
   Diese Software richtet sich ausschließlich an Personen, die das gesetzliche Volljährigkeitsalter in ihrem Land erreicht haben. Nutzen Sie dieses Open-Source-Werkzeug nur im Einklang mit den geltenden Gesetzen und Vorschriften Ihres Landes bzw. Ihrer Region.

---

## 📄 Lizenz (License)

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE). Sie dürfen den Code frei lesen, modifizieren, verteilen und integrieren, solange der Urheberrechtshinweis des Originalautors und dieser Haftungsausschluss in Kopien erhalten bleiben.
