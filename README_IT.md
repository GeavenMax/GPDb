# GPDb · Gestore di database per cinema gay per adulti ad alte prestazioni e massima privacy

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Un client moderno di gestione offline ad alte prestazioni, schedatura dettagliata e riservatezza assoluta senza tracciamento su cloud, creato per appassionati e collezionisti di cinema gay per adulti.</strong>
</p>

<p align="center">
  <a href="./README.md">简体中文</a> |
  <a href="./README_EN.md">English</a> |
  <a href="./README_ZH_TW.md">繁體中文</a> |
  <a href="./README_JA.md">日本語</a> |
  <a href="./README_DE.md">Deutsch</a> |
  <a href="./README_ES.md">Español</a> |
  <a href="./README_IT.md"><b>Italiano</b></a>
</p>

<p align="center">
  <a href="https://t.me/gpdbnews" target="_blank"><img src="https://img.shields.io/badge/Telegram-Channel%20%40gpdbnews-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Channel" /></a>
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Android-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Version-v2.14.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Canale Telegram Ufficiale**: Iscriviti al [Canale Telegram Ufficiale di GPDb (https://t.me/gpdbnews)](https://t.me/gpdbnews) per gli ultimi aggiornamenti sulle release, le novità sul database e i consigli d'uso!

---

## 📖 Informazioni sul progetto (About GPDb)

**GPDb** (Gay Pornography Database Manager) è un gestore avanzato e completo di **database offline per media gay per adulti (Gay Adult Media)**, sviluppato specificamente per appassionati, collezionisti digitali e studiosi di cinematografia.

Nell'ambito dei contenuti per adulti, la cronologia di visione, i segnalibri e le preferenze personali rappresentano **i dati personali più sensibili e riservati**. I servizi commerciali in streaming e le piattaforme cloud comportano rischi costanti di profilazione, fughe di dati e cancellazioni arbitrarie per scadenza dei diritti.

**GPDb è progettato attorno alla filosofia "100% Offline-First e privacy assoluta senza tracce"**:
- **Nessuna dipendenza dal cloud**: Tutti i metadati, i profili fisici degli attori, la cache di poster e fotogrammi, le valutazioni personali e i tag risiedono unicamente sul disco fisico del vostro dispositivo.
- **Architettura locale pura**: Nessuna registrazione di account, nessuna telemetria, nessun tracciamento di utilizzo e zero connessioni verso server esterni.
- **Prestazioni senza compromessi**: Sviluppato con il motore nativo **Rust (`gpdb-core`)** e architettura moderna **Tauri v2 + Vue 3** (client desktop macOS / Windows) e **Kotlin + Jetpack Compose + Room** (app Android nativa), gestisce con estrema fluidità **oltre 60.000 film integrali, 100.000 singole scene, 6.000 schede di modelli e 1.300 case di produzione classiche e moderne** a 60 fps con ricerche istantanee in millisecondi.

---

## ✨ Caratteristiche specializzate (Domain-Specific Features)

### 1. Ricerca approfondita per caratteristiche fisiche e attributi degli attori
- **Filtri anatomici ad alta precisione**:
  Filtrate i performer per **Corporatura (Build)** (Muscle / Twink / Bear / Hunk, ecc.), **Colore dei capelli**, **Colore degli occhi**, **Densità di barba e peli corporei (Facial Hair / Body Hair)**, **Altezza e peso (Height / Weight)**, **Carnagione (Skin)**, **Dimensioni e stato del prepuzio (Dick Size / Foreskin)**, **Tatuaggi e piercing (Tattoos)** e molti altri attributi combinabili.
- **Unificazione intelligente degli pseudonimi (Aliases)**:
  Riconcilia automaticamente i vari nomi d'arte utilizzati dallo stesso modello in diverse epoche e case di produzione, evitando di perdere filmografie per via di variazioni nel nome d'arte.
- **Distinzione accurata tra film completi (Films) e singole scene (Scenes)**:
  Consultate separatamente le pellicole in cui il modello è protagonista rispetto alle partecipazioni in singole scene ed episodi, con una panoramica completa di ogni apparizione.
- **Motore di ricerca multi-campo ad alta tolleranza agli errori**:
  - FTS5 ad alta velocità → fallback automatico a ricerca SQL multi-campo → Room / SQLite come rete di sicurezza: nessun risultato vuoto causato da tokenizzazione o tabelle virtuali mancanti.
  - Una singola ricerca copre contemporaneamente il titolo originale in inglese (`title`), il titolo in cinese (`title_zh`), il nome dello studio (`studio_name`), il nome del regista (`director_name`), la sinossi in cinese (`description_zh`) e il cast completo.
- **Filtri temporali e per data di inserimento multi-dimensionali**:
  - Supporta «Tutti», «Ultimo inserimento (per data di scraping)», «Ultimi 7 giorni», «Ultimi 30 giorni», «Ultimi 90 giorni» e «Anno corrente».
  - Granularità temporale separata per film (per anno) e scene (per data specifica).
  - I nuovi inserimenti mostrano un badge dinamico `NEW` con sfumatura animata.

### 2. Home immersiva stile piattaforma streaming e schede da collezione 3D
- **Cinque flussi di scoperta**:
  - **Carosello poster panoramico (Hero Carousel)**: Scorrimento fluido ad alta definizione con raffinati effetti di parallasse interattiva.
  - **Accadde oggi · Prime storiche (On This Day)**: Confronto intelligente con il calendario storico per rivivere le anteprime classiche dello stesso giorno (anni '80, '90, 2000).
  - **Stelle di oggi · Volti iconici (Star Spotlight)**: Verifica fisica dei file poster sul disco locale, mettendo in evidenza i volti iconici con immagini ad alta definizione autentiche, escludendo segnaposto testuali.
  - **Grandi saghe cinematografiche (Iconic Series)**: Recupero automatico delle serie iconiche che si estendono su oltre dieci capitoli.
  - **Scoperta casuale · Tesori nascosti (Lucky Discovery)**: Un tasto per lanciare i dadi e scovare titoli rari tra oltre 60.000 opere in archivio.
- **Due layout poster e lightbox a schermo intero con zoom gestuale/rotella**:
  - Supporto per **galleria adattiva ad alta definizione (`adaptive_pager`)** e **schede tridimensionali fisiche (`flip_3d`)**: con prospettiva fisica CSS 3D a 60 fps, pulsanti girevoli e illuminazione ambientale da collezione.
  - **Lightbox a schermo intero con zoom (`ImageLightbox.vue` / Compose)**: Supporta zoom fluido con rotella del mouse (1.0x ~ 5.0x), trascinamento panoramico, doppio clic per zoom/reset intelligente, scorciatoie da tastiera (+/-/0/Esc) e pinch-to-zoom su dispositivi mobili.

### 3. Raggruppamento intelligente di serie e copertine a collage (Smart Series & Collage Covers)
- **Algoritmo di clustering automatico**: Tecnologia integrata di riconoscimento di numeri romani e sottotitoli, che raggruppa automaticamente i capitoli dispersi di una serie (*Part I, Part II, Part III*) in raccolte tematiche coerenti.
- **Copertine artistiche adattive**: Genera automaticamente copertine composte da 1 poster singolo, 2 metà simmetriche, 3 livelli scalati o una griglia a 4 riquadri, con effetto vignetta perimetrale e rendering offline immediato.
- **Collezione dedicata alle serie**: Aggiungete con un solo tocco le serie preferite ai «Preferiti Serie» come segnalibri indipendenti per seguirle con facilità.

### 4. Schede registi e archivio delle case di produzione
- **Filmografia dedicata al regista**: Apertura istantanea della scheda regista dalla pagina del film, con la filmografia completa e la possibilità di filtrare direttamente l'archivio per regista.
- **Catalogo storico degli studi**: Dai pionieri classici della pellicola (Falcon, Colt, Catalina, ecc.) ai leader del digitale contemporaneo (Men.com, BelAmi, Lucas Entertainment, Corbin Fisher, ecc.), con cronologia delle opere e tag di genere.

### 5. 📸 Modalità privacy schermata e schede di condivisione luminose (Novità v2.13.0)
- **Modalità privacy schermata globale con un solo tocco**:
  - La barra di navigazione superiore offre un interruttore a forma di occhio per la «privacy schermata»; supporta scorciatoie da tastiera e commutazione istantanea dello stato (privacy attiva / navigazione normale).
  - **Controllo granulare della desensibilizzazione della privacy**:
    - `Oscura poster e fotogrammi`: sfocatura gaussiana globale (`blur(24px)`) su tutte le copertine, fotogrammi e avatar di attori e registi, impedendo la divulgazione di immagini sensibili tramite screenshot o condivisione social.
    - `Oscura sinossi e testo sensibile`: sfocatura gaussiana (`blur(7px)`) sulle sinossi e le trame delle scene, con selezione del testo disabilitata, per prevenire la divulgazione di contenuti riservati.
  - Sul client Android, basato sul flusso di stato reattivo `LocalPrivacyBlur`, l'effetto è applicato a schermo intero in tempo reale.
- **Generatore adattivo di schede di condivisione con effetto luce**:
  - Dalle pagine dei film e delle scene, generate con un solo tocco schede di condivisione di livello Apple Music / Spotify.
  - L'alone di sfondo viene estratto adattivamente dall'immagine di copertina con una sfocatura gaussiana ad ampio raggio e alta precisione (`blur(45px~60px)`), disponibile nei preset «Vibrante (Vibrant)», «Scuro (Dark)» e «Mezzanotte (Midnight)».
  - **Desensibilizzazione sicura prima della condivisione**: selezionate indipendentemente «Oscura poster» e «Oscura testo» per una condivisione sicura nei gruppi social e sulle reti pubbliche.
  - **Esportazione senza perdite su entrambe le piattaforme**:
    - **Client desktop**: Basato sul motore HTML5 Canvas 2D offline, rasterizza in risoluzione ultra-HD 2x Retina, con copia PNG negli appunti di sistema con un clic (incolla direttamente su WeChat/QQ/Telegram/Discord/X) oppure esportazione e salvataggio in locale.
    - **Client mobile**: Utilizza il motore di acquisizione bitmap con accelerazione hardware di Compose 1.8, supporta il salvataggio senza perdite nell'album di sistema (`MediaStore`) con un solo tocco e il pannello di condivisione nativo Android tramite `FileProvider`.

### 6. 🛡️ Suite completa di sicurezza e privacy (Enterprise-Grade Privacy & Security)
- **Blocco schermo PIN e protezione dalla perdita di focus**:
  - Supporta un PIN numerico da 4 a 6 cifre per il blocco dell'app; in caso di perdita di focus o timeout (1/5/15/30 minuti) viene applicata automaticamente una schermata di blocco con effetto vetro satinato (`AppLockOverlay.vue`).
  - Quando la finestra perde il focus o si passa ad un'altra app, viene istantaneamente sovrapposto uno strato protettivo con effetto vetro smerigliato.
- **Interruttore di emergenza – Calcolatrice camuffata (Panic Switch)**:
  - Commutazione istantanea verso una calcolatrice nera in stile Apple, pienamente funzionante (`FakeCalculatorModal.vue`), con supporto per le quattro operazioni aritmetiche.
  - Sul client desktop, la scorciatoia <kbd>Cmd + Shift + P</kbd> attiva l'interruttore di emergenza; sul client mobile è sufficiente un gesto (capovolgere il dispositivo).
  - Inserite il PIN corretto nella calcolatrice e premete `=`, oppure toccate rapidamente il titolo in alto per 4 volte, per sbloccare e tornare all'archivio.
- **Camuffamento del titolo dell'app e dell'icona sul desktop**:
  - Supporta la personalizzazione del titolo della finestra (ad es. "Calculator", "Notes") per evitare che il selettore attività del sistema riveli l'app.
  - Icone tematiche premium integrate (come «Totem dei Due Eroi» e «Cassetta di Pellicola in Ossidiana»), oltre a icone innocue (note, contabilità, calcolatrice).
- **Isolamento fisico a livello di sistema sul mobile**:
  - Integrazione con il flag di sistema Android `FLAG_SECURE` per prevenire registrazione schermo, screenshot e anteprima nelle miniature del multitasking.
  - Archiviazione in sandbox fisicamente isolata, con iniezione ricorsiva di `.nomedia` nelle directory per bloccare completamente la scansione da parte di gallerie fotografiche di terze parti.

### 7. Analisi del gusto con IA e traduzione multilingue con grandi modelli linguistici
- **Analisi del gusto con IA e ritratto estetico (AI Persona Insights)**:
  - **Architettura completamente asincrona e non invasiva**: Thread indipendente in background per l'esecuzione di grandi modelli linguistici locali o remoti (DeepSeek, OpenAI, Claude, ecc.), eliminando completamente il blocco dell'interfaccia.
  - **Analisi estetica approfondita in formato lungo**: Basata sulla collezione privata reale e sui tag dell'utente, analizza il suo DNA estetico e genera un saggio critico personalizzato di 2.000 parole contenente un «archetipo estetico» (es. *«Esploratore nostalgico della New Wave»*) e uno «spettro audiovisivo dell'epoca».
  - **Finestra di avanzamento in tempo reale**: Pannello flottante in vetro satinato di alta qualità che mostra passo per passo la stretta di mano crittografata del grande modello, l'estrazione delle caratteristiche e l'avanzamento della generazione del profilo.
- **Traduzione multilingue con grandi modelli e persistenza dei dati multipiattaforma**:
  - Integrazione nativa con OpenAI, DeepSeek, Claude, Gemini, Doubao (ByteDance) e altri principali fornitori di servizi.
  - Supporto al «rilevamento dei modelli disponibili» per verificare online con un clic l'elenco dei modelli API disponibili.
  - Supporto all'importazione/esportazione bidirezionale senza perdite dei dati di traduzione tra piattaforme (macOS / Windows / Android) in formato JSON standard.

### 8. Ricerca risorse ed estensioni esterne (v2.0)
- **Accesso diretto con 1 clic a più siti**: Dalle pagine dei film e degli attori, è possibile raggiungere con un solo clic BoyfriendTV, Google e i principali database cinematografici autorevoli, utilizzando il titolo originale in inglese per una ricerca precisa, senza dover riscrivere nulla.
- **Integrazione della ricerca magnet BT**: Combina con un clic il titolo originale e il nome dello studio in una formula di ricerca standard, collegandosi direttamente ai motori di ricerca di risorse esterni.
- **Interruttori modulari indipendenti**: Il centro plugin supporta la configurazione di attivazione/disattivazione fine e indipendente per ogni sorgente di collegamento esterno.
- **Motore di scraping e sincronizzazione incrementale**: Supporta lo scraping di aggiornamenti in rete su richiesta e il caching offline locale di poster e fotogrammi.

### 9. Internazionalizzazione completa e sistema di 77+ trofei gamificati
- **7 lingue selezionabili per tutta l'interfaccia**: Cinese semplificato (`zh-CN`), Cinese tradizionale (`zh-TW`), Inglese (`en`), Italiano (`it`), Giapponese (`ja`), Spagnolo (`es`) e Tedesco (`de`).
- **Sistema di trofei stile PlayStation**:
  - Oltre 77 obiettivi di esplorazione, ricerca, focus e collezione integrati; al momento dello sblocco viene attivato un popup con animazione fluida in stile PSN.
  - Basato su metriche comportamentali reali, con supporto al reset indipendente e alla gestione della verifica dei dati trofeo.

### 10. Copertura multipiattaforma e sincronizzazione dati su tre client (macOS / Windows / Android)
- **Client desktop macOS nativo (`desktop_client/`)**: Tramite l'architettura Tauri v2 + Rust, supporta perfettamente Apple Silicon e architettura Intel, con vetro satinato nativo e interazione tramite scorciatoie da tastiera.
- **Client desktop Windows nativo (`Windows_client/`)**: Directory dedicata e indipendente, con motore di analisi Python intelligente multi-percorso integrato, soppressione della console nera (`CREATE_NO_WINDOW`), ottimizzazione del font Microsoft YaHei, barra di scorrimento sottile, aggancio a schermo diviso di Win11 e installazione NSIS leggera per singolo utente senza privilegi elevati.
- **App Android nativa (`android_client/`)**: Costruita nativamente con il moderno stack Kotlin + Jetpack Compose + Room, si integra perfettamente con il modello dati del client desktop, per consultare e catalogare l'archivio ovunque e in qualsiasi momento in modalità offline.

---

## 🛠️ Architettura tecnica (Technology Stack)

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
                                   │           Archiviazione Persistente (Storage)        │
                                   │      GPDb.db (SQLite 3 WAL) + image_cache/          │
                                   └─────────────────────────────────────────────────────┘
```

I tre client — **macOS**, **Windows** e **Android** — condividono lo stesso schema del database GPDb.db e la struttura della cache immagini, garantendo una perfetta compatibilità e migrazione dei dati tra le piattaforme.

---

## 🚀 Guida rapida (Quick Start)

### Per utenti finali (Consigliato)

Scaricate il pacchetto di installazione più recente **`v2.14.0`** dalla pagina delle [Releases](https://github.com/GeavenMax/GPDb/releases) di questo repository:

| Piattaforma | File di installazione | Modalità di installazione e note |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.14.0.dmg` | Aprite l'immagine disco e trascinate `GPDb.app` nella cartella `Applicazioni`.<br>*(Al primo avvio, se viene segnalato come non notarizzato, andate su «Impostazioni di Sistema → Privacy e sicurezza» e fate clic su «Apri comunque», oppure eseguite nel Terminale `sudo xattr -cr /Applications/GPDb.app`)* |
| **🪟 Windows** | `GPDb-Windows-v2.14.0.exe` | Fate doppio clic sul programma di installazione per completare l'installazione. Utilizza l'architettura NSIS per singolo utente senza privilegi elevati: nessun permesso di amministratore richiesto, pronto all'uso immediatamente. |
| **🤖 Android** | `GPDb-Android-v2.14.0-signed.apk` | Scaricate sul dispositivo e fate tap per installare (firmato con chiave pubblica ufficiale; se il sistema chiede di consentire l'installazione da origini sconosciute, confermate l'autorizzazione). |

---

### 💻 Guida alla compilazione locale per sviluppatori (Developer Guide)

#### Requisiti di sistema
- **Node.js**: 20+ e npm
- **Rust**: 1.78+ (`rustup`, `cargo`)
- **Android SDK** (solo per il client Android): Android Studio Hedgehog+ oppure JDK 17+

#### 1. Client desktop macOS — Sviluppo e compilazione
```bash
# 1. Clonare il repository
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. Accedere alla cartella del client desktop
cd desktop_client

# 3. Installare le dipendenze frontend
npm install

# 4. Avviare in modalità sviluppo
npm run tauri dev

# 5. Generare il pacchetto di distribuzione finale (.app e .dmg)
npm run tauri build
```

#### 2. Client desktop Windows — Sviluppo e compilazione
```powershell
# 1. Accedere alla cartella del client Windows indipendente
cd Windows_client

# 2. Installare le dipendenze frontend
npm install

# 3. Avviare in modalità sviluppo
npm run tauri dev

# 4. Generare il pacchetto di installazione finale (programma di installazione NSIS .exe)
npm run tauri build

# Oppure eseguite direttamente lo script di packaging preconfigurato:
.\build_windows.ps1
```

#### 3. App Android nativa — Compilazione
```bash
# 1. Accedere alla cartella del client Android nativo
cd android_client

# 2. Compilare il pacchetto Debug per i test
./gradlew assembleDebug

# 3. Compilare l'APK Release firmato ufficialmente
./gradlew assembleRelease
```

---

## ⚖️ Note legali e clausola di esclusione della responsabilità (Disclaimer)

1. **Finalità del software**:
   GPDb (Gay Pornography Database Manager) è esclusivamente uno **strumento open-source generico per l'indicizzazione offline di metadati e la gestione locale di database per collezioni multimediali personali (Universal Offline Adult Media Metadata & Library Management Tool)**.
2. **Nessun file ospitato**:
   Il codice sorgente e le versioni compilate distribuite **non contengono, non ospitano e non distribuiscono alcun file video, audio, torrent o immagine protetto da copyright**. I campi di esempio mostrati nel software sono utilizzati esclusivamente per la verifica tecnica del database e il test dell'interfaccia grafica.
3. **Responsabilità dell'utente**:
   L'utente finale è l'unico responsabile di tutte le azioni compiute nella gestione dei propri file di database locali e nell'utilizzo delle funzioni di collegamento esterno, nonché della conformità al diritto d'autore; lo sviluppatore del software e i contributori open-source non hanno alcuna responsabilità al riguardo.
4. **Maggiore età e conformità normativa**:
   Il software è riservato esclusivamente a utenti che abbiano raggiunto la maggiore età legale nella propria giurisdizione. Si prega di utilizzare questo strumento open-source in modo ragionevole e legale, nel rispetto delle leggi e dei regolamenti del proprio paese e della propria regione.

---

## 📄 Licenza (License)

Il progetto è distribuito sotto [Licenza MIT](LICENSE). Siete liberi di esaminare, modificare, distribuire o integrare il codice di questo progetto, a condizione che vengano mantenute le indicazioni di copyright originali dell'autore e la presente clausola di esclusione di responsabilità in tutte le copie.
