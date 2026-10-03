# GPDb · Blitzschneller, datenschutzorientierter Manager für schwule Erwachsenenfilme

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Ein moderner, leistungsstarker und kompromisslos privater Offline-Verwaltungsclient für Liebhaber schwuler Erwachsenenfilme – maximale Archivierungstiefe, absolute Privatsphäre und null Cloud-Tracking.</strong>
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
  <img src="https://img.shields.io/badge/Version-v2.17.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Offizieller Telegram-Kanal**: Abonnieren Sie den [Offiziellen GPDb Telegram-Kanal (https://t.me/gpdbnews)](https://t.me/gpdbnews), um die neuesten Release-Updates, inkrementelle Datenbank-Aktualisierungen und Anwendungstipps aus erster Hand zu erhalten!

---

## 📖 Projektprofil & Einführung (About GPDb)

**GPDb** (Gay Pornography Database Manager) ist ein moderner, voll ausgestatteter **Offline-Datenbankmanager für schwule Erwachsenenmedien (Gay Adult Media)**, konzipiert für Enthusiasten, Sammler digitaler Medien und Medienarchivare.

Im Bereich der Erwachsenenunterhaltung gehören persönliche Sehgewohnheiten, Favoritenlisten und sexuelle Präferenzen zu den **sensibelsten und schützenswertesten persönlichen Daten überhaupt**. Kommerzielle Streaming-Dienste und zentralisierte Online-Plattformen bergen erhebliche, unkontrollierbare Risiken durch Benutzer-Tracking, Datenpannen sowie plötzliche Löschungen durch Lizenzabläufe.

**GPDb folgt einer strikten Philosophie: „100% Offline-First & Absolute Privatsphäre“**:
- **Keine Cloud-Abhängigkeit**: Alle Medien-Metadaten, Darsteller-Körperprofile, Poster- und Szenenbild-Caches, persönliche Tags, Bewertungen und Sammlungsverläufe verbleiben ausschließlich auf der physischen Festplatte des eigenen Geräts;
- **Reine Standalone-Architektur**: Keinerlei Benutzerkonten, keine Telemetrie- oder Tracking-Routinen und keinerlei Uploads zu externen Servern;
- **Kompromisslose Höchstleistung**: Angetrieben von der extrem schnellen **Rust-Engine (`gpdb-core`)** und **Tauri v2 + Vue 3** (macOS / Windows Desktop) sowie **Kotlin + Jetpack Compose + Room** (natives Android) verwaltet GPDb mühelos **über 60.000 vollständige Filme, 100.000 Einzelszenen, 6.000 Darsteller-Körperprofile und 1.300 klassische sowie moderne Filmstudios** – bei konstant flüssigen 60 fps und blitzschnellen Suchanfragen im Millisekundenbereich.

---

## ✨ Bereichsspezifische Kernfunktionen (Domain-Specific Features)

### 1. Umfassende Filterung nach körperlichen Darstellermerkmalen & PBC-Integration
- **Hochgradig granulare physiologische & morphologische Filterung**:
  Unterstützt die kombinierte Filterung nach **Körperbau (Build)** (z. B. Muscle / Twink / Bear / Hunk), **Haarfarbe (Hair)**, **Augenfarbe (Eyes)**, **Bart- und Körperbehaarung (Facial Hair / Body Hair)**, **Körpergröße & Gewicht (Height / Weight)**, **Hauttyp (Skin)**, **anatomischen Maßen (Dick Size / Foreskin)** sowie **Tattoos & Piercings**.
- **Vollständige Pseudonym-Konsolidierung (Aliases)**:
  Unterschiedliche Künstlernamen desselben Darstellers über verschiedene Studios und Epochen hinweg werden automatisch intelligent zusammengeführt – kein Werk geht durch einen Namenswechsel verloren.
- **Präzise Trennung von Spielfilmen (Films) & Einzelszenen (Scenes)**:
  Unterstützt das separate Anzeigen von vollständigen Spielfilmen mit dem Darsteller in der Hauptbesetzung sowie von Einzelszenen mit Gastauftritten – die gesamte Filmografie bleibt übersichtlich im Blick.
- **Fehlertolerante Mehrfeld-Suchmaschine**:
  - FTS5-Volltextabfrage → automatischer Fallback auf Standard-SQL-Mehrfeldabfrage → Room/SQLite-Absicherung; schließt Nulltreffer durch Tokenisierungsprobleme oder fehlende virtuelle Tabellen zuverlässig aus.
  - Eine einzelne Suchanfrage durchsucht gleichzeitig englischen Originaltitel (`title`), chinesische Übersetzung (`title_zh`), Studioname (`studio_name`), Regisseur (`director_name`), Handlungsbeschreibung (`description_zh`) sowie die gesamte Besetzung.
- **Mehrdimensionale Zeit- und Aktualitätsfilter**:
  - Unterstützt „Alle“, „Zuletzt erfasst (nach Scraping-Zeitpunkt)“, „Letzte 7 Tage“, „Letzte 30 Tage“, „Letzte 90 Tage“ und „Dieses Jahr“.
  - Veröffentlichungsdaten für Spielfilme (nach Jahr) und Einzelszenen (nach genauem Datum) sind präzise getrennt.
  - Neu erfasste Einträge werden mit einem dynamischen Farbverlaufs-Badge `NEW` hervorgehoben.
- **Umfassende PBC (Porn Base Central) Wiki-Datenintegration**:
  Die Darstellerprofile sind nun nahtlos mit den Daten aus der Porn Base Central Wikipedia angereichert – inklusive **bürgerlichem Geburtsnamen, Debütjahr, dynamischem Aktivitäts-Badge, Sternzeichen, ethnischer Herkunft, Performance-Stil-Tags, Wiki-Biografie** sowie direkten Verlinkungen zu IAFD, IMDb, X, OnlyFans und Instagram für fundierte Recherchen und Querverweise.

### 2. Streaming-reifer immersiver Startbildschirm & Mehrfach-Cover-Galerie
- **Fünf Entdeckungs-Feeds**:
  - **Fokus-Großbild-Karussell (Hero Carousel)**: Hochauflösende Titelposter im sanften automatischen Durchlauf mit eleganter Parallaxe-Interaktion am oberen Bildschirmrand.
  - **Heute vor vielen Jahren · Premieren-Klassiker (On This Day)**: Intelligenter Abgleich mit dem Kalendertag, um Klassiker des goldenen Zeitalters (80er, 90er, 2000er Jahre) mit historischem Premierendatum zu entdecken.
  - **Stars des Tages · Ikonische Gesichter (Star Spotlight)**: Strenge Validierung lokaler Bilddateien auf der physischen Festplatte; empfiehlt prominente Darsteller mit gestochen scharfen HD-Porträts ganz ohne Platzhalter-Initialen.
  - **Kultfilm-Reihen (Iconic Series)**: Spürt automatisch langlebige Filmreihen mit mehr als zehn Werken auf – inklusive Zufallsgenerator und „Neu würfeln“-Funktion per Knopfdruck.
  - **Zufalls-Entdecker · Blindbox-Fundus (Lucky Discovery)**: Ein Klick auf den Würfel fördert unerwartete Schätze aus der über 60.000 Titel starken Bibliothek zutage.
- **Mehrfach-Cover-Galerie & Vollbild-Zoom-Leuchtkasten**:
  - **Adaptive Mehrfach-Cover-Darstellung**: Werke mit Front-Cover, Rückseiten-Cover oder alternativen Coverversionen werden gleichzeitig horizontal nebeneinander mit prägnanten Front-/Back-Badges präsentiert und lassen sich mit einem Klick in voller Pracht öffnen.
  - **Vollbild-Zoom-Leuchtkasten (`ImageLightbox.vue` / Compose-Zoom)**: Bietet stufenloses Zoomen per Mausrad (1,0x – 5,0x), Drag-to-Pan zum Verschieben, Doppelklick für Zoom/Reset, Tastaturkürzel (+/-/0/Esc) sowie flüssiges Zwei-Finger-Pinch-to-Zoom auf Mobilgeräten.

### 3. Intelligente Filmreihen & adaptive Cover-Collagen (Smart Series & Collage Covers)
- **Automatischer Clustering-Algorithmus**: Erkennt römische Ziffern und Untertitel, um verstreute Teile einer Reihe (z. B. *Part I, Part II, Part III*) automatisch zu kohärenten Sammlungen zusammenzufügen.
- **Adaptive Poster-Collagen**: Generiert automatisch ausdrucksstarke Kunst-Collagen (1 Einzelposter, 2 symmetrische Hälften, 3 Kaskaden oder 4-Quadranten-Matrix) mit Randvignette und blitzschnellem Offline-Rendering.
- **Eigene Serien-Lesezeichen**: Lieblingsreihen lassen sich mit einem Klick zu den „Gespeicherten Reihen“ hinzufügen, um Fortsetzungen mühelos im Blick zu behalten.

### 4. Regisseur-Profile & 180+ Studio-Chroniken
- **Dedizierte Regisseur-Profile**: Filmdetails öffnen per Klick ein Profil des Regisseurs mit seiner gesamten Filmografie und der Möglichkeit, direkt nach seinen Werken zu filtern.
- **Tiefenarchiv mit 180+ Filmstudios**:
  - **181 Studios im historischen Tiefenarchiv**: Ausführliche Unternehmenschroniken klassischer und moderner Studios – von Gründungsjahr und Gründerpersönlichkeiten bis hin zu visuellen Stilrichtungen und ihrer historischen Entwicklung;
  - **Automatische Erfassung & gestochen scharfe Anzeige von Studio-Logos & Bannern**: Vollständige Erfassung hochauflösender Logos und offizieller Banner für 74 Kern-Studios; Studios ohne Logo erhalten ein elegantes Fallback mit zweibuchstabigem Farbverlaufs-Emblem;
  - **Neu gestaltete Filmdetailseite**: Die Inhaltsangabe erstreckt sich nun übersichtlich in voller Breite unterhalb der Cover-Galerie; Aktionsschaltflächen sind kompakt in einer Matrix-Leiste gebündelt.

### 5. 📸 Blickschutzmodus & Ästhetische Sharing-Karten (v2.17.0 Upgrade)
- **Globaler Ein-Klick-Blickschutzmodus (Screenshot- & Leseschutz)**:
  - Zentraler Augen-Schalter in der oberen Navigationsleiste; schnelles Umschalten per Tastaturkürzel oder Schaltfläche (Geschützt / Normalansicht).
  - **Schnell-Blickschutztaste in Android Home-Feed**: Auf Mobilgeräten befindet sich ein dedizierter Blickschutz-Schalter dauerhaft in der oberen Leiste des Home-Feeds für blitzschnelle Einhand-Deaktivierung bei unerwünschten Blicken.
  - **Feingranulare Datenschutz-Maskierung**:
    - `Poster und Szenenbilder unscharf`: Globale Gaußsche Unschärfe (`blur(24px)`) über sämtliche Cover, Standbilder und Darstellerfotos zur Verhinderung visueller Exposition bei Bildschirmfotos oder Bildfreigaben;
    - `Handlungsbeschreibungen und sensiblen Text unscharf`: Gaußsche Unschärfe (`blur(7px)`) für Inhaltsangaben und Episoden-Zusammenfassungen inklusive gesperrter Textauswahl, um Spoiler und Textlecks zu unterbinden.
  - Auf Android über einen reaktiven `LocalPrivacyBlur`-Zustandsfluss gesteuert – vollflächig und latenzfrei wirksam.
- **Adaptiver Glanz-Sharing-Karten-Generator**:
  - Filmdetails und Einzelszenen verwandeln sich mit einem Klick in kunstvolle Sharing-Karten auf Apple Music- / Spotify-Niveau.
  - **Vorder- und Rückseiten-Cover nebeneinander**: Spielfilme präsentieren Vorder- und Rückseiten-Artwork (Front/Back) parallel im Dual-Cover-Layout für ein ausgewogenes und sammlerwürdiges Gesamtbild.
  - **Aufgeräumtes Design mit offiziellem Telegram-Direktkanal**: Integrierter Vektor-QR-Code in der unteren Kartenecke führt direkt zum offiziellen Kanal `t.me/gpdbnews`; das überflüssige Erstellungsdatum wurde entfernt, um den Fokus ganz auf die Werk-ID und die Metadaten zu richten.
  - **Bilinearer Offscreen-Weichzeichner & verzerrungsfreier 16:9-Zuschnitt**: Hintergrund-Lichtschein wird über einen reinen Offscreen-Algorithmus artefaktfrei gerendert; Episodenbilder werden intelligent im 16:9-Format zentriert zugeschnitten, um Verzerrungen vollständig auszuschließen.
  - Die Hintergrund-Aura wird dynamisch aus den dominanten Farbtönen des Covers abgeleitet und mit großzügiger Gaußscher Unschärfe (`blur(45px~60px)`) gerendert – mit drei kuratierten Voreinstellungen: „Vibrant (Leuchtend)“, „Dark (Dunkel)“ und „Midnight (Mitternacht)“.
  - **Datenschutz vor dem Teilen**: Unabhängig aktivierbare Optionen für „Poster unscharf“ und „Text unscharf“ für sicheres Teilen in Gruppen und sozialen Netzwerken.
  - **Verlustfreier Export auf allen Plattformen**:
    - **Desktop**: 2x Retina-Auflösung via Offline-HTML5-Canvas-2D – PNG per Klick in die Systemzwischenablage kopieren (direktes Einfügen in Telegram, WhatsApp, Discord, X) oder lokal sichern.
    - **Mobil (Android)**: Hardware-beschleunigte Bitmap-Erfassung mit Jetpack Compose 1.8; verlustfreies Speichern in der Systemgalerie (`MediaStore`) und Aufruf des nativen Android-Teilendialogs via `FileProvider`.

### 6. 🛡️ Datenschutz- & Sicherheits-Suite (Enterprise-Grade Privacy & Security)
- **Verschlankte Desktop-Architektur ohne Tarn-Taschenrechner**:
  - Der frühere experimentelle Taschenrechner-Tarnmodus wurde auf dem Desktop vollständig entfernt. Die Anwendung konzentriert sich nun ganz auf eine saubere, performante und kompromisslos lokale Medienverwaltung.
- **Mobile PIN-Sperre & biometrischer Schutz**:
  - Android unterstützt eine eigenständige 4- bis 6-stellige PIN-Sperre sowie biometrische Entsperrung per Fingerabdruck und Gesichtserkennung; bei Fokusverlust oder nach konfigurierbarem Timeout (1/5/15/30 Minuten) blendet sich automatisch ein eleganter Schutz-Sperrbildschirm ein.
  - Beim Wechseln von Apps oder Verlust des Fensterfokus schützt sofort eine Gaußsche Milchglas-Schicht vor neugierigen Blicken.
- **Systemeigene Sicherheitsisolierung auf Mobilgeräten**:
  - Natives Android-`FLAG_SECURE` blockiert Screenshots, Bildschirmaufnahmen und Vorschaubilder in der Multitasking-App-Übersicht zuverlässig.
  - Physische Sandbox-Speicherisolierung mit rekursiver `.nomedia`-Injektion verhindert das automatische Indizieren von Bildern durch Drittanbieter-Galerien.

### 7. Native Rust In-Process LLM-Übersetzungs-Engine & Zweisprachige Gegenüberstellung
- **Reine Rust-native In-Process-Engine**:
  - Vollständige Unabhängigkeit von externen Python-HTTP-Diensten; asynchrone parallele TLS-Verbindungen direkt im Desktop-Kern für verzögerungsfreie Einzel- und Stapelübersetzungen.
  - Direkte Integration von Google Gemini, dem OpenAI-Ökosystem (DeepSeek, Moonshot, Qwen, Zhipu, SiliconFlow, lokales Ollama) und Anthropic Claude.
- **Intelligente Gemini API-Key-Rotation & Kontingentschutz**:
  - Integrierter atomarer API-Key-Rotationspool: Bei Ratenbegrenzungen (HTTP 429) wechselt das System im Millisekundenbereich zum nächsten Schlüssel; sind alle Keys erschöpft, springt automatisch ein konfigurierter Backup-Provider ein.
- **Zweisprachige Ansicht & fehlertoleranter Reintext-Parser**:
  - Sowohl Spielfilm-Handlungen als auch Einzelszenen lassen sich per Knopfdruck per KI übersetzen und lokal speichern – mit nahtlosem Umschalten zwischen Originaltext und Übersetzung.
  - Für benutzerdefinierte API-Keys optimiert: Verarbeitet reine Textausgaben von LLMs fehlertolerant ohne JSON-Parse-Fehler; ergänzt fehlende `/v1`-Endpunkte bei OpenAI-kompatiblen Schnittstellen vollautomatisch.
- **Verlustfreier plattformübergreifender Datentransfer**:
  - Übersetzungsdaten lassen sich plattformübergreifend (macOS / Windows / Android) als standardisierte JSON-Dateien verlustfrei exportieren und importieren.

### 8. Ressourcensuche & Externe Web-Erweiterungen (v2.0)
- **Kompakte Matrix für Direktsprünge**:
  - Kompakt angeordnete Aktionsleiste in Film- und Darstelleransichten für gezielte Ein-Klick-Weiterleitungen zu BoyfriendTV, Google und etablierten Filmdatenbanken – ganz ohne manuelles Abtippen.
- **BT-Magnet-Suche**: Kombiniert Originaltitel und Studio zu einer optimierten Suchanfrage für externe Indexer.
- **Individuelle Konfiguration**: Jede externe Suchquelle lässt sich im Plugin-Bereich einzeln aktivieren oder deaktivieren.
- **Inkrementelles Scraping & Offline-Caching**: Ermöglicht bedarfsgesteuerte Online-Metadaten-Aktualisierungen und das lokale Caching von Covern und Szenenbildern.
- **PBC (Porn Base Central) Darsteller-Wiki-Scraper** (`scrape_pbc_actors.py`):
  - Indexiert über **1.200 Darsteller** über die MediaWiki-API mit tiefgehender Attributerfassung;
  - **Inkrementelle Revisionsprüfung**: Synchronisiert ausschließlich Artikel, die sich seit dem letzten Lauf geändert haben – minimaler Netzwerkverkehr;
  - Gleicht über **25 Attributfelder** präzise ab (Geburtsname, Debütjahr, Ethnizität, Sternzeichen, Aktivitätsstatus, externe Profil-Links etc.);
  - Erreicht eine praxiserprobte Trefferquote von **97,3 %** beim Namensabgleich.
- **SmutJunkies-Darsteller-Scraper** (`scrape_smutjunkies_actors.py`):
  - Umfasst mehr als **6.700 schwule Darsteller** im vollständigen 26-Buchstaben-Alphabet-Index (A–Z);
  - **4-stufiger fehlertoleranter Abgleichalgorithmus** (exakte Übereinstimmung → normalisiertes Fuzzy-Matching → Alias-Quervergleich → manuelle Prüfliste) für maximale Zuverlässigkeit bei unterschiedlichen Schreibweisen;
  - **Bidirektionale inkrementelle Synchronisation**: Fügt neue Einträge hinzu und aktualisiert geänderte Felder, ohne bestehende Datensätze zu beeinträchtigen.

### 9. Vollständige Internationalisierung & Verschlankte Benutzeroberfläche (Internationalization & Streamlined UX)
- **7 vollständig lokalisierte Sprachen**: Vereinfachtes Chinesisch (`zh-CN`), Traditionelles Chinesisch (`zh-TW`), Englisch (`en`), Italienisch (`it`), Japanisch (`ja`), Spanisch (`es`) und Deutsch (`de`).
- **Konsequente Verschlankung für maximale Effizienz**:
  - Vollständige Entfernung des PlayStation-Trophäensystems: Beseitigt redundante Hintergrundüberwachung, Event-Listener und Datenbankabfragen für spürbar geringeren CPU- und RAM-Verbrauch;
  - Entfernung des experimentellen „KI-Vorlieben-Profils“: Das Plugin-Management konzentriert sich gezielt auf die drei Kernbereiche Magnet-Suche, Metadaten-Scraper und LLM-Übersetzung;
  - Entfernung des 5-Sterne-Bewertungssystems und komplexer 3D-Flip-Cards, um den Arbeitsspeicherbedarf und die GPU-Renderinglast auf ein Minimum zu reduzieren.

### 10. Universelles Benutzerdaten-Backup & Nahtlose Updates (Cross-Platform Backup & Seamless Update)
- **Plattformübergreifendes universelles Benutzerdaten-Backup (`GPDb_Backup.json`)**:
  - Einheitliches JSON-Backup-Format für den vollständigen Export und Import persönlicher Sammlungen, Notizen, Kennzeichnungen, LLM-API-Schlüssel und Systemeinstellungen.
  - Beseitigt Datensilos zwischen macOS, Windows und Android vollständig: Gerätewechsel, Neuinstallationen oder plattformübergreifender Abgleich gelingen mit einer einzigen Sicherungsdatei mühelos und verlustfrei.
- **Integrierte Update-Prüfung mit Download-Fortsetzungsfunktion**:
  - Automatische und manuelle Versionsprüfung direkt in den Einstellungen aller Clients mit übersichtlicher Einsicht in die aktuellen Release-Notes.
  - Download-Engine mit Unterstützung für Fortsetzung unterbrochener Downloads (Resumable Downloads), gefolgt von einer einfachen Installation des neuen Pakets.

### 11. Plattformspezifische Tiefenanpassung & Datensynergie (macOS / Windows / Android)
- **Nativer macOS Desktop-Client (`desktop_client/`)**: Entwickelt mit Tauri v2 + Rust; universelle Unterstützung für Apple Silicon und Intel-Prozessoren, native Glasmorphismus-Ästhetik und Tastaturkürzel.
- **Nativer Windows 11 Desktop-Client (`Windows_client/`)**:
  - **Natives Mica / Acrylic & Fluent Design**: Fenster fügen sich mit echter Halbtransparenz nahtlos in das Windows-Hintergrundbild ein; 4 Materialstufen und optimierter Farbkontrast für helle System-Themes;
  - **System-Tray (Infobereich) & Jump List**: Beim Schließen minimierbar in den Infobereich; Rechtsklick-Menü im Tray zum schnellen Blickschutz-Wechsel und direkter Sprung zu Kernbereichen;
  - **Taskleisten-Fortschrittsanzeige (Taskbar Progress)**: Zeigt bei Metadaten-Scraping-Vorgängen den Fortschritt mit einem grünen Balken direkt auf dem Taskleistensymbol an;
  - **WebView2 GPU-Hardwarebeschleunigung**: Optimierte Rasterisierungsparameter für gestochen scharfe und flüssige Darstellung auf 120Hz- und 144Hz-Monitoren.
- **Nativer Android-Client (`android_client/`)**: Rein nativ realisiert mit modernem Kotlin + Jetpack Compose + Room; 100% kompatibles Datenmodell mit dem Desktop für komfortables Offline-Stöbern und Verwalten von unterwegs.

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

Laden Sie das aktuelle vorkompilierte Installationspaket **`v2.17.0`** direkt von der [Releases-Seite](https://github.com/GeavenMax/GPDb/releases) herunter:

| Plattform | Installationspaket | Installation & Hinweise |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.17.0.dmg` | DMG öffnen, `GPDb.app` in den `Programme`-Ordner ziehen.<br>*(Falls beim ersten Start eine Warnung wegen fehlender Notarisierung erscheint: „Systemeinstellungen → Datenschutz & Sicherheit“ → „Trotzdem öffnen“, oder Terminal: `sudo xattr -cr /Applications/GPDb.app`)* |
| **🪟 Windows** | `GPDb-Windows-v2.17.0.exe` | Installer doppelklicken und Installation abschließen. Nutzt NSIS-Einzelbenutzer-Architektur – keine Administratorrechte erforderlich. |
| **🤖 Android** | `GPDb-Android-v2.17.0-signed.apk` | APK auf das Gerät laden und direkt installieren (offiziell signiert; bei Systemaufforderung „Installation aus unbekannten Quellen“ erlauben). |

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
