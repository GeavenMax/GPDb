# GPDb · Gestore di database per cinema gay per adulti ad alte prestazioni e massima privacy

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Un client moderno di gestione offline ad alte prestazioni, schedatura dettagliata, riservatezza assoluta e zero tracciamento su cloud, creato per appassionati e collezionisti di cinema gay per adulti.</strong>
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
  <img src="https://img.shields.io/badge/Version-v2.17.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Canale Telegram Ufficiale**: Iscriviti al [Canale Telegram Ufficiale di GPDb (https://t.me/gpdbnews)](https://t.me/gpdbnews) per ricevere in tempo reale le ultime novità sulle release, le notifiche sugli aggiornamenti incrementali del database e i consigli d'uso!

---

## 📖 Informazioni sul progetto (About GPDb)

**GPDb** (Gay Pornography Database Manager) è un gestore avanzato e completo di **database offline per media gay per adulti (Gay Adult Media)**, sviluppato specificamente per appassionati, collezionisti digitali e studiosi di cinematografia.

Nell'ambito dei contenuti per adulti, la cronologia di visione, i segnalibri e le preferenze personali rappresentano **i dati personali più sensibili e riservati**. I servizi commerciali in streaming e le piattaforme cloud comportano rischi costanti di profilazione, fughe di dati e cancellazioni arbitrarie per scadenza dei diritti.

**GPDb è progettato attorno alla filosofia "100% Offline-First e privacy assoluta senza tracce"**:
- **Nessuna dipendenza dal cloud**: Tutti i metadati, i profili fisici degli attori, la cache di poster e fotogrammi, le annotazioni personali e i tag risiedono unicamente sul disco fisico del vostro dispositivo.
- **Architettura locale pura**: Nessuna registrazione di account, nessuna telemetria, nessun tracciamento di utilizzo e zero connessioni verso server esterni.
- **Prestazioni senza compromessi**: Sviluppato con il motore nativo **Rust (`gpdb-core`)** e architettura moderna **Tauri v2 + Vue 3** (client desktop macOS / Windows) e **Kotlin + Jetpack Compose + Room** (app Android nativa), gestisce con estrema fluidità **oltre 60.000 film integrali, 100.000 singole scene, 6.000 schede di modelli e 1.300 case di produzione classiche e moderne** a 60 fps con ricerche istantanee in millisecondi.

---

## ✨ Caratteristiche specializzate (Domain-Specific Features)

### 1. Ricerca approfondita per caratteristiche fisiche e attributi degli attori
- **Filtri anatomici e somatici ad alta precisione**:
  Filtrate i performer per **Corporatura (Build)** (Muscle / Twink / Bear / Hunk, ecc.), **Colore dei capelli**, **Colore degli occhi**, **Densità di barba e peli corporei (Facial Hair / Body Hair)**, **Altezza e peso (Height / Weight)**, **Carnagione (Skin)**, **Dimensioni e stato del prepuzio (Dick Size / Foreskin)**, **Tatuaggi e piercing (Tattoos)** e molti altri attributi combinabili.
- **Unificazione olografica degli pseudonimi (Aliases)**:
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
- **Integrazione wiki PBC — Scheda attore arricchita**:
  Ogni profilo attore include i dati estratti dall'enciclopedia wiki Porn Base Central: **nome di nascita**, **anno di debutto**, **badge di stato attivo/ritirato**, **segno zodiacale ed etnia**, **tag di stile**, **scheda biografica wiki** in formato testo completo, e **link ai profili collegati** su IAFD, IMDb, X (Twitter), OnlyFans e Instagram per ricerche incrociate approfondite.

### 2. Home immersiva stile piattaforma streaming e galleria di poster multipli affiancati
- **Cinque flussi di scoperta**:
  - **Carosello gigante panoramico (Hero Carousel)**: Scorrimento fluido ad alta definizione dei poster principali con raffinati effetti di parallasse interattiva.
  - **Accadde oggi · Prime storiche (On This Day)**: Confronto intelligente con il calendario storico per rivivere le anteprime classiche dello stesso giorno (anni '80, '90, 2000).
  - **Stelle di oggi · Volti iconici (Star Spotlight)**: Verifica rigorosa dei file poster sul disco locale, mettendo in evidenza solo i volti iconici dotati di autentiche immagini ad alta definizione ed escludendo qualsiasi segnaposto privo di foto.
  - **Grandi saghe cinematografiche (Iconic Series)**: Selezione ad estrazione casuale delle saghe storiche composte da decine di episodi, con pratico pulsante per estrarre una nuova selezione con un tocco.
  - **Bussola della scoperta · Tesori nascosti (Lucky Discovery)**: Un tocco per lanciare i dadi e scovare gemme nascoste tra oltre 60.000 opere in archivio.
- **Galleria di poster multipli affiancati e lightbox a schermo intero con zoom**:
  - **Visualizzazione orizzontale affiancata e adattiva di poster multipli**: Per le opere dotate di locandina frontale, retro o versioni alternative, le immagini vengono mostrate contemporaneamente e affiancate in orizzontale, complete di etichette «Front» e «Back» e apertura immediata del lightbox ad alta risoluzione.
  - **Lightbox a schermo intero con zoom (`ImageLightbox.vue` / Compose)**: Supporta lo zoom fluido con rotella del mouse (1.0x ~ 5.0x), trascinamento per panoramica, doppio clic per zoom/reset intelligente, scorciatoie da tastiera (+/-/0/Esc) e gesture pinch-to-zoom fluide su dispositivi mobili.

### 3. Raggruppamento intelligente di serie e copertine a collage (Smart Series & Collage Covers)
- **Algoritmo di clustering automatico**: Tecnologia integrata di riconoscimento di numeri romani e sottotitoli, che raggruppa automaticamente i capitoli dispersi di una serie (*Part I, Part II, Part III*) in raccolte tematiche coerenti.
- **Copertine artistiche a collage adattivo**: Genera automaticamente copertine composte da 1 poster singolo, 2 metà simmetriche, 3 livelli scalati o una griglia a 4 riquadri, con effetto vignetta perimetrale e rendering offline immediato.
- **Collezione dedicata alle serie**: Aggiungete con un solo tocco le serie preferite ai «Preferiti Serie» come segnalibri indipendenti per seguirle con facilità.

### 4. Schede registi e archivio approfondito di oltre 180 case di produzione
- **Filmografia dedicata al regista**: Apertura istantanea della scheda regista dalla pagina del film, con l'elenco completo delle opere dirette e la possibilità di filtrare direttamente l'archivio per regista.
- **Archivio approfondito e catalogo delle case di produzione**:
  - **Archivio storico e panoramica dettagliata di 181 case di produzione fondamentali**: Raccolta completa della storia dei marchi classici e moderni, anno di fondazione, profilo dei fondatori, canoni estetici ed evoluzione culturale;
  - **Acquisizione e rendering ad alta risoluzione di 74 loghi e banner ufficiali**: Copertura dei marchi principali con banner ufficiali e loghi nitidi a grande formato, con fallback elegante a badge con sfumatura per gli studi sprovvisti di logo;
  - **Ristrutturazione della pagina dettagli del film**: La sinossi della trama è riposizionata a tutta larghezza sotto la locandina per una lettura ariosa e confortevole; i pulsanti operativi principali sono organizzati in una matrice compatta ed efficiente.

### 5. 📸 Modalità privacy dello schermo e schede di condivisione luminose (v2.17.0 Ottimizzato)
- **Modalità privacy globale con interruttore rapido**:
  - Interruttore a forma di occhio nella barra di navigazione superiore, con scorciatoie da tastiera e commutazione immediata tra visualizzazione normale e protetta;
  - **Interruttore rapido nella schermata Home di Android**: Icona a forma di occhio posizionata stabilmente nella barra superiore per desensibilizzare l'intera interfaccia con un solo tocco;
  - **Controllo granulare della desensibilizzazione**:
    - `Oscura poster e fotogrammi`: sfocatura gaussiana globale (`blur(24px)`) su tutte le locandine, scene e ritratti dei modelli per impedire la visione involontaria di immagini esplicite durante la navigazione o la cattura dello schermo;
    - `Oscura sinossi e testo sensibile`: sfocatura gaussiana (`blur(7px)`) su trame e descrizioni con inibizione della selezione del testo, prevenendo spoiler o fughe di dettagli confidenziali.
  - Su Android mobile, il rendering protettivo è gestito in modo reattivo dallo state flow `LocalPrivacyBlur`, con effetto immediato sull'intero schermo.
- **Generatore di schede di condivisione luminose stile Apple Music / Spotify**:
  - Creazione con un clic dalle pagine di film e scene;
  - **Doppia locandina affiancata fronte e retro (Front / Back)**: Per i film completi, visualizzazione affiancata di copertina e retro per una resa visiva ricca e da collezione;
  - **Condivisione pulita con accesso diretto al canale ufficiale**: Codice QR ufficiale Telegram (`t.me/gpdbnews`) posizionato con eleganza nell'angolo inferiore destro, rimozione della data di generazione ridondante dal fondo per focalizzare l'attenzione sul codice identificativo dell'opera;
  - **Sfocatura bilineare offscreen affidabile e ritaglio 16:9 senza distorsioni**: Rendering dell'alone di sfondo tramite algoritmo offscreen puro; ritaglio intelligente centrato in rapporto 16:9 per fotogrammi e singole scene, eliminando qualsiasi deformazione o stiramento;
  - Alone luminoso di sfondo estratto automaticamente dalle tinte dominanti della locandina con sfocatura gaussiana ad ampio raggio (`blur(45px~60px)`), con 3 preset dedicati: «Vibrante (Vibrant)», «Scuro (Dark)» e «Mezzanotte (Midnight)»;
  - **Desensibilizzazione di sicurezza prima della condivisione**: Opzioni indipendenti per applicare la sfocatura su poster e testi prima di condividere le schede su canali o social network;
  - **Esportazione senza perdite su entrambe le piattaforme**:
    - **Desktop**: Motore Canvas 2D offline con rasterizzazione a risoluzione 2x Retina, copia PNG negli appunti con un clic (pronto da incollare su Telegram, Discord, X, ecc.) o salvataggio su file;
    - **Mobile**: Motore hardware-accelerated Compose 1.8, salvataggio nella galleria di sistema (`MediaStore`) e condivisione nativa tramite `FileProvider`.

### 6. 🛡️ Suite completa di sicurezza e privacy (Enterprise-Grade Privacy & Security)
- **Desktop snellito e focalizzato sull'archiviazione**:
  - Rimozione totale della vecchia calcolatrice di emergenza finta sui client desktop, focalizzando l'applicazione sulla gestione pura, ordinata ed essenziale del patrimonio multimediale offline.
- **Blocco con PIN e autenticazione biometrica su mobile**:
  - Supporto per codice PIN numerico (4~6 cifre) e sblocco tramite impronta digitale o riconoscimento facciale su Android, con schermata di blocco in vetro smerigliato che compare automaticamente all'uscita dal focus o dopo un intervallo di inattività (1/5/15/30 minuti).
  - Schermatura protettiva immediata con effetto satinato non appena la finestra perde il focus o si passa a un'altra applicazione.
- **Isolamento fisico a livello di sistema operativo su mobile**:
  - Integrazione con il flag nativo Android `FLAG_SECURE` per impedire screenshot, registrazioni dello schermo e anteprime nelle miniature del multitasking.
  - Isolamento in sandbox con file `.nomedia` posizionati ricorsivamente in tutte le cartelle per precludere la scansione da parte di app galleria esterne.

### 7. Motore nativo di traduzione LLM in-process e confronto bilingue
- **Motore multi-LLM puro Rust nativo in-process**:
  - Completa eliminazione della dipendenza da servizi HTTP esterni in Python; implementazione nel core desktop di canali asincroni concorrenti TLS per traduzioni di singole frasi o in blocco fulminee.
  - Supporto nativo per Google Gemini, ecosistema protocollo OpenAI (DeepSeek, Moonshot, Qwen, Zhipu, SiliconFlow, Ollama locale) e Anthropic Claude.
- **Pool di rotazione chiavi API Google Gemini e protezione delle quote**:
  - Pool atomico integrato di rotazione delle chiavi API: se una chiave raggiunge il limite di quota (errore 429), il sistema commuta in millisecondi a una chiave di riserva; se tutte le chiavi sono esaurite, passa al fornitore alternativo configurato.
- **Confronto bilingue scene/film e tolleranza al testo semplice**:
  - Traduzione con un clic tramite IA per trame di film completi e singole scene con scrittura nel database locale e possibilità di alternare istantaneamente il testo originale e la traduzione.
  - Ottimizzato per chiavi API personalizzate: tolleranza automatica del testo in output diretto senza errori bloccanti di parsing JSON; normalizzazione automatica dell'endpoint con autocompletamento di `/v1`.
- **Persistenza e migrazione dei dati di traduzione**:
  - Esportazione e importazione bidirezionale e senza perdite dei dati di traduzione su tutte le piattaforme (macOS / Windows / Android) in formato JSON standard.

### 8. Ricerca risorse ed estensioni v2.0
- **Accesso rapido con layout a matrice compatto**:
  - Pulsanti esterni raggruppati in una matrice compatta sulle schede di film e attori per raggiungere con un solo clic BoyfriendTV, Google e database autorevoli, senza bisogno di digitare nuovamente il titolo.
- **Integrazione della ricerca magnet BT**: Combinazione automatica del titolo originale e del nome dello studio in una formula di ricerca standard, collegandosi direttamente ai motori di ricerca esterni.
- **Interruttori modulari indipendenti**: Il centro plugin consente di attivare o disattivare singolarmente ogni sorgente di collegamento esterno.
- **Motore di scraping e sincronizzazione incrementale**: Supporta l'aggiornamento dei metadati via rete su richiesta e la memorizzazione locale nella cache di poster e fotogrammi.
- **Motore di scraping wiki PBC (Porn Base Central)** (`scrape_pbc_actors.py`):
  - Oltre **1.200 profili attori** indicizzati tramite crawl completo delle pagine MediaWiki API;
  - Meccanismo di **rilevamento delle revisioni incrementali** per sincronizzare unicamente le voci modificate, riducendo al minimo il consumo di banda;
  - Confronto dettagliato su **oltre 25 campi** (nome reale, anno di debutto, etnia, segno zodiacale, stato di attività, link esterni ai profili, ecc.);
  - Precisione di corrispondenza dei nomi verificata al **97,3%**.
- **Motore di scraping SmutJunkies** (`scrape_smutjunkies_actors.py`):
  - Oltre **6.700 attori gay per adulti** indicizzati con copertura completa dell'intero archivio sulle **26 lettere dell'alfabeto**;
  - Algoritmo di allineamento a **4 livelli ad alta tolleranza** (corrispondenza esatta → fuzzy matching normalizzato → verifica incrociata degli pseudonimi → coda di revisione manuale);
  - **Sincronizzazione incrementale bidirezionale**: inserimento automatico delle nuove schede e aggiornamento differenziale a livello di campo per i record esistenti, salvaguardando i dati memorizzati localmente.

### 9. Internazionalizzazione completa ed esperienza snella (Internationalization & Streamlined UX)
- **7 lingue selezionabili per l'intera interfaccia**: Cinese semplificato (`zh-CN`), Cinese tradizionale (`zh-TW`), Inglese (`en`), Italiano (`it`), Giapponese (`ja`), Spagnolo (`es`) e Tedesco (`de`).
- **Architettura estremamente snella e focalizzata**:
  - Rimozione totale del sistema di trofei PlayStation, azzerando calcoli superflui e codice non necessario;
  - Rimozione del plugin sperimentale di analisi estetica IA (AI Persona Insights), per concentrare le estensioni sui tre pilastri essenziali: ricerca torrent, scraping dei dati e traduzione con modelli LLM;
  - Eliminazione del sistema di valutazione a 5 stelle e delle complesse carte 3D ribaltabili per minimizzare l'impegno di memoria RAM e il carico sulla GPU.

### 10. Backup universale dei dati utente e aggiornamenti integrati (Cross-Platform Backup & Seamless Update)
- **Backup e migrazione universale dei dati utente (`GPDb_Backup.json`)**:
  - Architettura JSON standard unificata per esportare e importare con un solo tocco preferiti, cronologia di visualizzazione, tag, note personali, configurazioni API dei modelli LLM e preferenze di sistema.
  - Abbattimento definitivo dei silos di dati tra macOS, Windows e Android: il cambio di dispositivo o la reinstallazione del sistema richiede solo un singolo file di backup, con migrazione istantanea e senza perdite.
- **Verifica aggiornamenti in-app e download riprendibili**:
  - Modulo di controllo automatico o manuale della versione integrato su desktop e mobile, con confronto in tempo reale con le release su GitHub e visualizzazione delle note di rilascio.
  - Motore di download ad alta velocità integrato con supporto alla ripresa automatica dei download interrotti (resumable downloads) e procedura guidata di installazione per mantenere l'applicazione costantemente aggiornata.

### 11. Personalizzazione profonda del sistema operativo (macOS / Windows / Android)
- **Client desktop macOS nativo (`desktop_client/`)**: Architettura Tauri v2 + Rust con supporto universale ad Apple Silicon e Intel, trasparenze native in vetro satinato e scorciatoie da tastiera.
- **Client desktop Windows nativo con integrazione profonda (`Windows_client/`)**:
  - **Materiali di finestra Mica e Acrylic nativi con Fluent Design**: Trasparenze che si fondono con lo sfondo del desktop, 4 livelli di materiale configurabili e isolamento dedicato della modalità scura per garantire contrasto elevato nei temi chiari ed evitare scurimenti anomali;
  - **Barra delle applicazioni, System Tray e Jump List**: Riduzione a icona nella barra delle applicazioni alla chiusura, attivazione rapida della modalità privacy dal menu contestuale della tray e scorciatoie Jump List per accedere direttamente alle sezioni principali;
  - **Indicatore di avanzamento sulla barra delle applicazioni (Taskbar Progress)**: Durante lo scraping di rete completo o incrementale, l'icona della barra mostra nativamente una barra verde e la percentuale di completamento;
  - **Accelerazione hardware WebView2 ottimizzata**: Parametri di rasterizzazione GPU per frequenze di aggiornamento elevate a 120Hz/144Hz con estrema fluidità.
- **App Android nativa (`android_client/`)**: Costruita nativamente con lo stack moderno Kotlin + Jetpack Compose + Room, perfettamente allineata al modello dati del client desktop per una consultazione e catalogazione offline comoda e immediata ovunque ci si trovi.

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

---

## 🚀 Guida rapida e download (Quick Start)

### Per utenti finali (Consigliato)

Scaricate il pacchetto di installazione ufficiale più recente **`v2.17.0`** direttamente dalla pagina delle [Releases](https://github.com/GeavenMax/GPDb/releases) di questo repository:

| Piattaforma | File di installazione | Modalità di installazione e note |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.17.0.dmg` | Aprite l'immagine disco e trascinate `GPDb.app` nella cartella `Applicazioni`.<br>*(Al primo avvio, se viene segnalato come non notarizzato, andate su «Impostazioni di Sistema → Privacy e sicurezza» e fate clic su «Apri comunque», oppure eseguite nel Terminale `sudo xattr -cr /Applications/GPDb.app`)* |
| **🪟 Windows** | `GPDb-Windows-v2.17.0.exe` | Fate doppio clic sul programma di installazione per completare l'installazione. Utilizza l'architettura NSIS per singolo utente senza privilegi elevati: nessun permesso di amministratore richiesto, pronto all'uso immediatamente. |
| **🤖 Android** | `GPDb-Android-v2.17.0-signed.apk` | Scaricate sul dispositivo e fate tap per installare (firmato con chiave pubblica ufficiale; se il sistema chiede di consentire l'installazione da origini sconosciute, confermate l'autorizzazione). |

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

# 5. Generare il pacchetto di distribuzione finale (pacchetti .app e .dmg automatici)
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

# Oppure eseguite direttamente lo script di packaging pronto all'uso:
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
