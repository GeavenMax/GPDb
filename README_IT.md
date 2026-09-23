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
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Android%20(In%20Dev)-blue?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=flat-square" alt="Category" />
  <img src="https://img.shields.io/badge/Architecture-Tauri%20v2%20%2B%20Rust%20%2B%20Vue%203-emerald?style=flat-square" alt="Tech Stack" />
  <img src="https://img.shields.io/badge/Database-SQLite%20(WAL%20Mode)-orange?style=flat-square" alt="Database" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=flat-square" alt="Privacy" />
  <img src="https://img.shields.io/badge/License-MIT-purple?style=flat-square" alt="License" />
</p>

---

## 📖 Informazioni sul progetto (About GPDb)

**GPDb** (Gay Pornography Database Manager) è un gestore avanzato e completo di **database offline per media gay per adulti (Gay Adult Media)**, sviluppato specificamente per appassionati, collezionisti digitali e studiosi di cinematografia.

Nell'ambito dei contenuti per adulti, la cronologia di visione, i segnalibri e le preferenze personali rappresentano **i dati personali più sensibili e riservati**. I servizi commerciali in streaming e le piattaforme cloud comportano rischi costanti di profilazione, fughe di dati e cancellazioni arbitrarie per scadenza dei diritti.

**GPDb è progettato attorno alla filosofia "100% Offline-First e privacy assoluta senza tracce"**:
- **Nessuna dipendenza dal cloud**: Tutti i metadati, i profili fisici degli attori, la cache di poster e fotogrammi, le valutazioni e i tag risiedono unicamente sul disco fisico del vostro computer.
- **Architettura locale pura**: Nessuna registrazione di account, nessuna telemetria, nessun tracciamento di utilizzo e zero connessioni verso server esterni.
- **Prestazioni senza compromessi**: Sviluppato con il motore nativo **Rust (`gpdb-core`)** e architettura moderna **Tauri v2 + Vue 3**, gestisce con estrema fluidità **oltre 60.000 film integrali, 100.000 singole scene, 6.000 schede di modelli e 1.300 case di produzione** a 60 fps con ricerche istantanee in millisecondi.

---

## ✨ Caratteristiche specializzate

### 1. Ricerca approfondita per caratteristiche fisiche
- **Filtri anatomici ad alta precisione**:
  Filtrate i performer per **Corporatura (Build)** (Muscle, Twink, Bear, Hunk, ecc.), **Colore dei capelli**, **Colore degli occhi**, **Densità di barba e peli corporei**, **Altezza e peso**, **Carnagione/Etnia**, **Stato del prepuzio (Foreskin) e dimensioni**, oltre a **Tatuaggi e piercing**.
- **Unificazione intelligente degli pseudonimi (Aliases)**:
  Riconcilia in modo automatico i vari nomi d'arte utilizzati da uno stesso modello in diverse epoche e case di produzione.
- **Distinzione accurata tra film completi (Films) e singole scene (Scenes)**:
  Consultate separatamente le pellicole in cui il modello è protagonista rispetto alle partecipazioni in singole scene ed episodi.

### 2. Home Feed immersivo stile piattaforma streaming
- **Carosello poster a schermo panoramico**: Scorrimento fluido ad alta definizione con eleganti effetti di parallasse.
- **Accadde oggi (On This Day)**: Confronta la data odierna con le anteprime storiche dell'epoca d'oro (anni '80, '90 e 2000).
- **Stelle di oggi (Starlight Today)**: Verifica la presenza fisica dei ritratti nella memoria locale per mettere in evidenza i volti iconici con foto autentiche in alta definizione, escludendo segnaposto testuali.
- **Grandi saghe cinematografiche**: Mette in risalto le serie cinematografiche composte da numerosi capitoli.
- **Bussola della scoperta**: Lanciate il dado per scovare titoli rari e tesori nascosti tra più di 60.000 opere.

### 3. Raggruppamento intelligente di serie e copertine a collage
- **Riconoscimento automatico**: Riconosce numeri romani e sottotitoli per riunire automaticamente capitoli sparsi (*Part I, Part II, Part III*) in raccolte tematiche coerenti.
- **Copertine artistiche dinamiche**: Compone automaticamente locandine singole, a 2 metà simmetriche, a 3 livelli o a griglia a 4 riquadri con sfumatura perimetrale e rendering offline immediato.

### 4. Schede registi e archivio delle case di produzione
- **Biografia del regista**: Apertura istantanea della filmografia completa del regista e filtraggio immediato nell'archivio.
- **Catalogo storico degli studi**: Dai pionieri classici della pellicola (Falcon, Colt, Catalina, ecc.) ai leader del digitale contemporaneo (Men.com, BelAmi, Lucas Entertainment, Corbin Fisher, ecc.).

### 5. Analisi del gusto con IA e ritratto estetico (AI Persona Insights)
- **Elaborazione asincrona non bloccante**: Esecuzione in background con modelli linguistici locali o remoti (DeepSeek, OpenAI, Claude, ecc.) senza alcun rallentamento dell'interfaccia.
- **Analisi del DNA estetico**: Esamina le vostre preferenze e genera un saggio critico personalizzato di 2.000 parole con archetipo estetico (es. *«Esploratore nostalgico della New Wave»*).
- **Finestra di avanzamento in vetro satinato**: Visualizza in trasparenza ciascuna fase dell'estrazione e della generazione del profilo.

### 6. Ricerca risorse ed estensioni web esterne (v2.0)
- **Accesso diretto con 1 clic**: Raggiungete direttamente la scheda dell'attore o del film su BoyfriendTV, Google e database cinematografici utilizzando il titolo originale in lingua inglese.
- **Generatore magnet BT**: Compone formule di ricerca standardizzate unendo titolo e studio per i motori di ricerca esterni.
- **Interruttori modulari**: Attivate o disattivate singolarmente ciascun pulsante esterno nelle impostazioni dei plugin.

### 7. Internazionalizzazione completa e trofei stile console
- **7 lingue selezionabili**: Cinese semplificato (`zh-CN`), Cinese tradizionale (`zh-TW`), Inglese (`en`), Italiano (`it`), Giapponese (`ja`), Spagnolo (`es`) e Tedesco (`de`).
- **Sistema di trofei stile PlayStation**: Decine di obiettivi da sbloccare durante l'esplorazione e la catalogazione, con notifiche a comparsa in vetro satinato.

---

## 🛠️ Architettura tecnica

```
┌─────────────────────────────────────────────────────────────┐
│                    Livello Frontend (UI)                    │
│  Vue 3 + Vite + TypeScript + Tailwind CSS + Lucide Icons   │
│  (Layout reattivo, effetto vetro satinato, i18n, stato)     │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Tauri IPC / Ponte binario)
┌──────────────────────────────▼──────────────────────────────┐
│                    Livello Host Tauri v2                    │
│ Protocollo personalizzato (gpdb-img://), gestione finestre  │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Rust Native FFI)
┌──────────────────────────────▼──────────────────────────────┐
│                 Motore Rust Core (gpdb-core)                │
│ • Costruttore query SQL dinamico & ricerca fuzzy (rusqlite) │
│ • Validatore fisico immagini & risolutore cache locale      │
│ • Gestore attività asincrone e runtime LLM                  │
│ • Motore di migrazione e autoriparazione dello schema       │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Archiviazione Persistente                │
│       gevi.db (SQLite 3 WAL) + image_cache/ (Locale)        │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Guida rapida

### Per utenti finali (Consigliato)

Scaricate il file di installazione precompilato dalla pagina delle [Releases](../../releases):
- **macOS**: Scaricate `GPDb-macOS-v2.0.dmg`, aprite l'immagine disco e trascinate `GPDb.app` nella cartella `Applicazioni`.
- **Android** (Applicazione autonoma in fase di sviluppo): Scaricate il pacchetto `.apk` per installarlo direttamente sul vostro dispositivo.

### Per sviluppatori (Compilazione locale)

#### Requisiti di sistema
- Node.js 20+ e npm
- Rust 1.78+ (`cargo`)
- macOS 12+ (Supporta sia Apple Silicon serie M che Intel x86_64)

#### Istruzioni di compilazione
```bash
# 1. Clonare il repository
git clone https://github.com/littlebighero/GPDb.git
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

---

## ⚖️ Note legali e clausola di esclusione della responsabilità

1. **Finalità del software**:
   GPDb è un software open-source generico per l'indicizzazione offline di metadati e la gestione locale di database per collezioni multimediali personali.
2. **Nessun file ospitato**:
   Il codice sorgente e le versioni compilate distribuite **non contengono, non ospitano e non distribuiscono alcun file video, audio, torrent o immagine protetto da copyright**.
3. **Responsabilità dell'utente**:
   L'utente finale è l'unico responsabile della gestione dei propri file di database locali e dell'apertura di eventuali collegamenti esterni.
4. **Maggiore età e conformità normativa**:
   Il software è riservato esclusivamente a utenti maggiorenni secondo le leggi vigenti nella propria giurisdizione.

---

## 📄 Licenza

Il progetto è distribuito sotto [Licenza MIT](LICENSE). Siete liberi di esaminare, modificare e redistribuire questo software a condizione che vengano mantenute le indicazioni di copyright originali e la presente clausola di esclusione di responsabilità.
