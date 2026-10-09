# GPDb · Database manager per cinema gay per adulti ad alte prestazioni e massima privacy

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Client multipiattaforma moderno, ad alte prestazioni, con schedatura completa, privacy assoluta e zero tracciamento cloud, progettato per appassionati di cinema gay per adulti</strong>
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
  <img src="https://img.shields.io/badge/Version-v2.18.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Canale Ufficiale**: Iscriviti al [Canale Telegram Ufficiale (@gpdbnews)](https://t.me/gpdbnews) per ricevere in tempo reale annunci sulle release, aggiornamenti incrementali del database e suggerimenti d'uso!

---

## 📖 Posizionamento del progetto (About GPDb)

**GPDb** (Gay Pornography Database Manager) è un **moderno sistema completo di gestione database offline** progettato per appassionati e collezionisti di **cinema gay per adulti (Gay Adult Media)**.

**Filosofia 100% locale (Offline-First) e privacy assoluta senza tracce**:
- **Zero dipendenza dal cloud**: Metadati multimediali, profili fisici degli attori, cache delle locandine e annotazioni personali sono memorizzati esclusivamente sul disco locale;
- **Architettura stand-alone pura**: Nessun account utente, nessuna telemetria o tracciamento dati, nessun caricamento verso server esterni;
- **Prestazioni native eccezionali**: Basato sul **core ad alte prestazioni in Rust (`gpdb-core`)** + **Tauri v2 + Vue 3** (macOS / Windows) e **Kotlin + Jetpack Compose + Room** (Android nativo). Gestisce oltre **63.000 film, 134.000 scene, 108.000 attori e 2.400 case di produzione** con rendering fluido a 60 fps e ricerche istantanee in pochi millisecondi.

---

## ✨ Caratteristiche salienti (Core Highlights)

### 1. Profili fisici completi degli attori e ricerca avanzata ad alta tolleranza
- **Filtri somatici granulari**: Filtraggio combinato per corporatura (Build), colore di capelli e occhi, pelosità corporea, altezza e peso, tonalità della pelle, dimensioni e prepuzio (Dick Size / Foreskin), tatuaggi e piercing.
- **Aggregazione intelligente degli pseudonimi (AKA)**: Riconciliazione automatica dei molteplici nomi d'arte utilizzati tra studi ed epoche diverse, azzerando le omissioni.
- **Disaccoppiamento accurato tra film e scene**: Chiara distinzione tra pellicole da protagonista e singole scene o apparizioni secondarie.
- **Ricerca combinata ad alta tolleranza agli errori**: Indice FTS5 ad alta velocità con fallback SQL multi-campo; ricerca combinata su titoli bilingui, studi, registi, sinossi e cast.
- **Integrazione completa dell'enciclopedia PBC**: Dati biografici da Porn Base Central (nome reale, anno di debutto, stato di attività, biografia completa e collegamenti a IAFD, IMDb, X).

### 2. Feed immersivo stile streaming e galleria multilocandina
- **5 flussi di scoperta**: Carosello gigante panoramico in primo piano, «Accadde oggi» con prime storiche, «Stelle di oggi» con volti iconici, grandi saghe cinematografiche ed esplorazione casuale a sorpresa.
- **Galleria multilocandina affiancata**: Copertina frontale, retro e locandine alternative disposte orizzontalmente con etichette dinamiche Front/Back.
- **Lightbox a schermo intero con zoom**: Zoom fluido con rotellina del mouse (1.0x ~ 5.0x), panoramica a trascinamento, doppio clic e supporto completo ai gesti touch pinch-to-zoom.

### 3. Saghe cinematografiche intelligenti e copertine a collage
- **Algoritmo di clustering automatico**: Riconoscimento intelligente di numeri romani e sottotitoli per raggruppare automaticamente le serie a episodi.
- **Copertine artistiche a collage adattivo**: Generazione automatica di poster a collage (1-4 immagini) con composizione simmetrica o a matrice, renderizzati all'istante offline.

### 4. Schede registi e 100% loghi ufficiali per oltre 2.400 studi
- **Curriculum dedicato ai registi**: Accesso immediato alla filmografia del regista con filtraggio rapido dell'intero catalogo.
- **Copertura del 100% per loghi e banner degli studi**: Oltre 2.490 case di produzione con loghi HD completi al 100% (formato WebP offline) e oltre 690 banner panoramici ufficiali; copertura dei loghi delle opere al 98%.
- **Layout dei dettagli confortevole**: Sinossi a tutta larghezza per una lettura agevole e matrice compatta dei pulsanti d'azione rapidi.

### 5. 📸 Modalità privacy dello schermo e schede di condivisione luminose (v2.18.0)
- **Anti-sbirciatina globale con un clic**: Interruttore rapido nella barra superiore desktop e nella schermata di esplorazione di Android per applicare istantaneamente una sfocatura gaussiana alle locandine (`blur(24px)`) e al testo delle trame (`blur(7px)`).
- **Schede di condivisione luminose a doppia copertina**:
  - Layout affiancato fronte/retro e ritaglio centrato 16:9 senza distorsioni per le scene;
  - Alone luminoso adattivo offscreen con sfocatura bilineare (preset Flusso luminoso, Scuro, Mezzanotte);
  - Codice QR del canale Telegram ufficiale integrato (`t.me/gpdbnews`);
  - Esportazione desktop Canvas a risoluzione 2x Retina e acquisizione bitmap nativa su Android per condivisione immediata.

### 6. 🛡️ Suite di sicurezza e riservatezza completa
- **Desktop essenziale ed efficiente**: Rimozione di mascheramenti superflui per concentrarsi sulla gestione pura e sicura della cineteca.
- **Autenticazione biometrica e blocco di sicurezza mobile**: Supporto a codice PIN, impronta digitale e riconoscimento facciale con sfocatura protettiva istantanea alla perdita del focus.
- **Isolamento fisico a livello di sistema**: Flag nativo `FLAG_SECURE` su Android contro screenshot e registrazioni; iniezione ricorsiva di `.nomedia` nella sandbox locale per schermare completamente i file dalle app galleria di terze parti.

### 7. Motore di traduzione LLM in-process nativo in Rust
- **Core asincrono TLS in puro Rust**: Zero dipendenze esterne da Python; anteprime di singole frasi e traduzioni in blocco ad altissima velocità.
- **Supporto multi-modello e pool di rotazione chiavi**: Integrazione nativa con Google Gemini (commutazione automatica in millisecondi in caso di superamento quota) e protocollo compatibile OpenAI (DeepSeek, Claude, Moonshot, Ollama, ecc.).
- **Tolleranza e standardizzazione**: Gestione fluida di risposte in testo semplice senza interruzioni di parsing JSON; importazione ed esportazione standard JSON per migrazione senza perdite.

### 8. Ricerca risorse ed ecosistema plugin (v2.0)
- **Matrice di collegamenti rapidi**: Apertura diretta con un clic verso BoyfriendTV, Google, database del settore e motori di ricerca torrent magnetici.
- **Scraper enciclopedico PBC**: Indicizzazione profonda di oltre 1.200 attori, rilevamento delle modifiche incrementali e confronto su oltre 25 attributi (precisione del 97,3%).
- **Scraper SmutJunkies**: Catalogo completo di oltre 6.700 attori, algoritmo di allineamento a 4 livelli ad alta tolleranza e sincronizzazione incrementale bidirezionale.

### 9. Internazionalizzazione in 7 lingue ed esperienza snella
- **Supporto nativo a 7 lingue**: Cinese semplificato, Cinese tradizionale, English, Italiano, 日本語, Español, Deutsch.
- **Allineamento al 100% e fallback multilivello**: 1.096 stringhe per ciascuna lingua con fallback multilivello intelligente per evitare caratteri mancanti.
- **Sincronizzazione di stato e architettura snella**: Separazione e sincronizzazione bidirezionale tra lingua dell'interfaccia e lingua dei contenuti; eliminazione di achievement fittizi e plugin superflui per ridurre al minimo l'uso di memoria.

### 10. Backup universale dei dati e aggiornamenti multipiattaforma
- **Backup universale (`GPDb_Backup.json`)**: Esportazione e importazione con un clic di preferiti, cronologia, note personali e configurazioni, consentendo migrazioni immediate tra macOS, Windows e Android.
- **Verifica integrata degli aggiornamenti con ripresa del download**: Integrazione con l'API GitHub Releases per confronto delle versioni e download incrementale ad alta velocità.

### 11. Ottimizzazione profonda nativa per ogni sistema operativo
- **macOS**: Architettura Tauri v2 + Rust, supporto nativo Apple Silicon e Intel, effetti di sfocatura di sistema e scorciatoie integrate.
- **Windows**: Materiali Mica e Acrylic nativi di Windows 11, contrasto elevato per i temi chiari, icona nella barra delle applicazioni con Jump List, avanzamento dello scraping sulla taskbar e accelerazione GPU WebView2.
- **Android**: Sviluppata nativamente in Kotlin + Jetpack Compose + Room per controlli touch fluidi e consultazione offline ovunque.

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
                                   │         Archiviazione persistente (Storage)         │
                                   │        GPDb.db (SQLite 3 WAL) + image_cache/        │
                                   └─────────────────────────────────────────────────────┘
```

---

## 🚀 Guida rapida e download (Quick Start)

### Download e installazione (Consigliato)

Scarica la versione ufficiale più recente **`v2.18.0`** dalla pagina [Releases](https://github.com/GeavenMax/GPDb/releases) del repository:

| Piattaforma | File di installazione | Istruzioni per l'installazione |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.18.0.dmg` | Apri l'immagine disco e trascina `GPDb.app` nella cartella `Applicazioni`.<br>*(Se compare un avviso di mancata notarizzazione, aprilo da «Impostazioni di Sistema → Privacy e sicurezza» cliccando su «Apri comunque»)* |
| **🪟 Windows** | `GPDb-Windows-v2.18.0.exe` | Fai doppio clic sul file di installazione. Utilizza un'architettura NSIS per singolo utente senza privilegi di amministratore. |
| **🤖 Android** | `GPDb-Android-v2.18.0-signed.apk` | Scarica e installa direttamente sul dispositivo (firmato con chiave privata ufficiale; abilita l'installazione da origini sconosciute se richiesto). |

---

### 💻 Guida alla compilazione per sviluppatori (Developer Guide)

```bash
# 1. Client desktop macOS
cd desktop_client && npm install && npm run tauri dev      # Debug locale
npm run tauri build                                         # Pacchetto di produzione (.app / .dmg)

# 2. Client desktop Windows
cd Windows_client && npm install && npm run tauri dev      # Debug locale
npm run tauri build                                         # Pacchetto di produzione (.exe)

# 3. Client Android nativo
cd android_client && ./gradlew assembleRelease              # Compilazione dell'APK Release firmato

# 4. Verifica automatica di pre-rilascio
./git_tasks/pre_release_check.sh                            # Controllo di conformità dei percorsi, privacy e allineamento versione
```

> 💡 Riferimenti per i sottosistemi:
> - Specifiche di internazionalizzazione dell'interfaccia: [`docs/i18n/UI_I18N_SPEC.md`](docs/i18n/UI_I18N_SPEC.md)
> - Sistema dei loghi HD degli studi: [`studio_logos/README.md`](studio_logos/README.md)
> - Procedura completa di rilascio automatizzato: [`git_tasks/README.md`](git_tasks/README.md)

---

## ⚖️ Note legali ed esclusione di responsabilità (Disclaimer)

1. **Finalità del software**: GPDb è esclusivamente uno **strumento open-source universale per l'indicizzazione locale di metadati e la gestione offline di database di contenuti multimediali per adulti**.
2. **Esclusione di contenuti**: Il codice sorgente e le release distribuite **non contengono, non ospitano e non distribuiscono alcun file audio, video, torrent o immagine protetto da copyright**. I campi e i dati dimostrativi mostrati servono unicamente a scopi di verifica tecnica del database e di layout grafico.
3. **Responsabilità dell'utente**: Tutte le operazioni di gestione dei file del database locale e l'eventuale utilizzo di collegamenti di ricerca esterni sono sotto l'esclusiva responsabilità dell'utente finale.
4. **Conformità normativa**: Il software è destinato esclusivamente a utenti maggiorenni secondo le normative vigenti. Si invita a utilizzarlo in modo conforme alle leggi e ai regolamenti locali.

---

## 📄 Licenza (License)

Questo progetto è rilasciato sotto licenza [MIT](LICENSE).
