# GPDb · Gestor de base de datos de cine gay para adultos ultrarrápido y centrado en la privacidad

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Un cliente moderno de gestión multiplataforma de alto rendimiento, con análisis exhaustivo, privacidad absoluta y cero rastreo en la nube, diseñado para coleccionistas y aficionados al cine gay para adultos.</strong>
</p>

<p align="center">
  <a href="./README.md">简体中文</a> |
  <a href="./README_EN.md">English</a> |
  <a href="./README_ZH_TW.md">繁體中文</a> |
  <a href="./README_JA.md">日本語</a> |
  <a href="./README_DE.md">Deutsch</a> |
  <a href="./README_ES.md"><b>Español</b></a> |
  <a href="./README_IT.md">Italiano</a>
</p>

<p align="center">
  <a href="https://t.me/gpdbnews" target="_blank"><img src="https://img.shields.io/badge/Telegram-Channel%20%40gpdbnews-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Channel" /></a>
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Android-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Version-v2.14.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Canal Oficial de Telegram**: ¡Suscríbete al [Canal Oficial de Telegram de GPDb (https://t.me/gpdbnews)](https://t.me/gpdbnews) para recibir las últimas actualizaciones de versiones, noticias de la base de datos y consejos de uso!

---

## 📖 Acerca del proyecto (About GPDb)

**GPDb** (Gay Pornography Database Manager) es un gestor avanzado y completo de **bases de datos offline para medios gay para adultos (Gay Adult Media)**, concebido para entusiastas, coleccionistas digitales e investigadores cinematográficos.

En el ámbito del contenido para adultos, el historial de visualización, las listas de favoritos y las preferencias personales constituyen **la información privada más sensible**. Las plataformas comerciales en la nube conllevan riesgos de rastreo de perfiles, fugas de datos y eliminaciones imprevistas por caducidad de licencias.

**GPDb se rige por una filosofía estricta de «100% Offline-First y privacidad absoluta sin huella»**:
- **Cero dependencia de la nube**: Todos los metadatos, fichas de actores, atributos físicos, miniaturas/pósteres en caché, etiquetas personales, valoraciones y colecciones residen única y exclusivamente en el disco físico de su dispositivo.
- **Arquitectura local pura**: Sin cuentas de usuario, sin telemetría, sin análisis de uso y sin conexión a servidores externos.
- **Rendimiento excepcional**: Impulsado por el motor nativo en **Rust (`gpdb-core`)** y la arquitectura moderna **Tauri v2 + Vue 3** (escritorio macOS/Windows) y **Kotlin + Jetpack Compose + Room** (Android nativo), gestiona sin esfuerzo **más de 60.000 películas completas, 100.000 escenas individuales, 6.000 perfiles de actores y 1.300 estudios clásicos y modernos**, con renderizado fluido a 60 fps y búsquedas en milisegundos.

---

## ✨ Características destacadas del sector (Domain-Specific Features)

### 1. Búsqueda profunda por atributos físicos y corporales de actores
- **Filtros anatómicos de alta precisión**:
  Filtre actores por **Complexión (Build)** (Muscle, Twink, Bear, Hunk, etc.), **Color de pelo (Hair)**, **Color de ojos (Eyes)**, **Densidad de vello facial y corporal (Facial Hair / Body Hair)**, **Altura y peso (Height / Weight)**, **Tono de piel (Skin)**, **Características físicas (Dick Size / Foreskin)**, **Tatuajes y piercings (Tattoos)** y más, con filtros combinados multidimensionales.
- **Unificación inteligente de seudónimos (Aliases)**:
  Agrupa automáticamente los nombres artísticos y alias que un mismo actor utilizó en diferentes productoras y épocas, para evitar omisiones por cambios de nombre.
- **Distinción precisa entre películas completas (Films) y escenas (Scenes)**:
  Explore por separado las obras en las que el actor protagoniza largometrajes frente a sus apariciones en clips, episodios cortos o cameos, con una lista completa de sus créditos.
- **Motor de búsqueda multifuente con alta tolerancia a errores**:
  - Recuperación rápida FTS5 → degradación automática a consulta SQL multifuente → SQLite como respaldo, eliminando resultados vacíos por tokenización o tablas virtuales ausentes.
  - Una sola búsqueda abarca título original (`title`), título en chino (`title_zh`), nombre del estudio (`studio_name`), nombre del director (`director_name`), sinopsis en chino (`description_zh`) y el reparto completo.
- **Filtros temporales y de incorporación multidimensionales**:
  - Opciones: «Todos», «Última incorporación (por fecha de scraping)», «Publicados en los últimos 7 días», «30 días», «90 días» y «Este año».
  - Granularidad temporal diferenciada: largometrajes por año, escenas por fecha exacta.
  - Las entradas recién incorporadas muestran una etiqueta dinámica `NEW` con degradado animado.

### 2. Portada inmersiva con calidad de streaming y tarjetas 3D de colección
- **Cinco flujos de descubrimiento y recomendación**:
  - **Carrusel gigante de pósteres (Hero Carousel)**: Transición automática y fluida de imágenes en alta definición en la parte superior, con efecto de paralaje interactivo.
  - **Tal día como hoy · Estrenos clásicos (On This Day)**: Contrasta la fecha actual con el calendario histórico para recuperar obras estrenadas el mismo día en la época dorada (años 80, 90 y 2000).
  - **Estrellas del día · Rostros icónicos (Star Spotlight)**: Valida la presencia de archivos de póster en el disco local, recomendando con inteligencia figuras icónicas con retratos en alta definición, sin marcadores de texto.
  - **Sagas cinematográficas icónicas (Iconic Series)**: Detecta y destaca automáticamente franquicias longevas con más de diez entregas.
  - **Ruleta de descubrimiento · Caja sorpresa (Lucky Discovery)**: Lance los dados para descubrir joyas ocultas entre más de 60.000 títulos.
- **Doble modo de póster y lightbox de zoom a pantalla completa**:
  - Compatible con **galería HD adaptativa (`adaptive_pager`)** y **tarjetas 3D con física real (`flip_3d`)**: perspectiva CSS 3D a 60 fps, botones de cápsula con giro físico e iluminación ambiental de colección.
  - **Lightbox de zoom a pantalla completa (`ImageLightbox.vue` / Zoom en Compose)**: zoom suave con rueda del ratón (1.0x–5.0x), arrastre con clic sostenido, doble clic para zoom inteligente/restablecer, atajos de teclado (+/-/0/Esc) y pellizco con dos dedos en móvil.

### 3. Agrupación inteligente de series y carátulas en mosaico (Smart Series & Collage Covers)
- **Algoritmo de clustering automático**: Identifica números romanos y subtítulos para unir automáticamente entregas dispersas (*Part I, Part II, Part III*) en una colección cohesionada.
- **Carátulas adaptativas en mosaico**: Genera automáticamente composiciones de 1 póster único, 2 mitades simétricas, 3 en cascada o cuadrícula de 4 cuadrantes, con viñeta de luz y renderizado offline instantáneo.
- **Colecciones exclusivas de series**: Añada con un clic sus sagas favoritas a un marcador independiente de «Series favoritas» para seguir su evolución.

### 4. Fichas de directores y enciclopedia de estudios
- **Historial exclusivo del director**: Desde el detalle de una película, acceda con un clic a la ficha del director con su filmografía completa y filtrado directo en la biblioteca.
- **Catálogo histórico de estudios**: Desde los grandes clásicos del celuloide (Falcon, Colt, Catalina, etc.) hasta los líderes contemporáneos (Men.com, BelAmi, Lucas Entertainment, Corbin Fisher, etc.), con cronología de obras y etiquetas de estilo.

### 5. 📸 Modo antiespionaje y tarjetas de compartición luminosas (Novedad v2.13.0)
- **Modo antiespionaje global con un solo clic**:
  - La barra de navegación superior ofrece un interruptor de «ojo» antiespionaje; compatible con atajos de teclado y cambio de estado instantáneo (Protegido / Navegación normal).
  - **Control granular de privacidad**:
    - `Desenfoque de pósteres e imágenes`: Desenfoque gaussiano global (`blur(24px)`) sobre todas las portadas, fotogramas, avatares de actores y demás imágenes sensibles, evitando su exposición en capturas de pantalla o publicaciones en redes sociales.
    - `Desenfoque de sinopsis y texto sensible`: Desenfoque gaussiano (`blur(7px)`) sobre sinopsis de películas, resúmenes de escenas y texto sensible, con selección de texto deshabilitada para prevenir filtraciones.
  - En Android, el estado de privacidad se gestiona mediante el flujo reactivo `LocalPrivacyBlur`, con efecto instantáneo en toda la pantalla.
- **Generador de tarjetas de compartición luminosas y adaptativas**:
  - Genera tarjetas de compartición al estilo Apple Music / Spotify desde el detalle de películas y escenas con un solo clic.
  - El halo de fondo extrae adaptativamente el tono de la portada y aplica un desenfoque gaussiano de alta precisión y gran radio (`blur(45px–60px)`), con tres presets de halo exclusivos: «Vibrante (Vibrant)», «Negro profundo (Dark)» y «Medianoche (Midnight)».
  - **Privacidad segura antes de compartir**: Marque de forma independiente «Desenfocar póster» y «Desenfocar texto» para compartir con seguridad en grupos y redes sociales.
  - **Exportación sin pérdidas en ambas plataformas**:
    - **Escritorio**: Motor HTML5 Canvas 2D offline, rasterización en resolución Retina 2x, copia PNG al portapapeles del sistema (compatible con WeChat/QQ/Telegram/Discord/X) o guardado local.
    - **Móvil**: Motor de captura de bitmap acelerado por hardware Compose 1.8, guardado sin pérdidas en la galería del sistema (`MediaStore`) y panel nativo de compartición Android mediante `FileProvider`.

### 6. 🛡️ Suite completa de privacidad y seguridad (Enterprise-Grade Privacy & Security)
- **PIN numérico de bloqueo y protección por desenfoque de ventana**:
  - Establezca un PIN de 4–6 dígitos para bloquear la aplicación; al perder el foco o tras el tiempo de espera configurado (1/5/15/30 minutos), se activa automáticamente la pantalla de bloqueo con efecto de vidrio esmerilado (`AppLockOverlay.vue`).
  - Al minimizar la ventana o cambiar a otra aplicación, se superpone instantáneamente una capa antiespionaje de vidrio esmerilado gaussiano.
- **Interruptor de pánico: calculadora camuflada (Panic Switch)**:
  - Cambia al instante a una calculadora oscura real y funcional al estilo Apple (`FakeCalculatorModal.vue`), con soporte para las cuatro operaciones aritméticas.
  - En escritorio, el atajo <kbd>Cmd + Shift + P</kbd> activa el modo pánico; en móvil, mediante un gesto (voltear el dispositivo boca abajo).
  - Introduzca el PIN correcto en la calculadora y pulse `=`, o toque el título superior 4 veces seguidas, para desbloquear y regresar a la biblioteca de forma segura.
- **Camuflaje del título y del icono de la aplicación**:
  - Personalice el título de la ventana (p. ej., «Calculator», «Notes») para evitar que aparezca en el selector de tareas del sistema.
  - Incluye varios temas de icono de diseño premium (como «Tótem Marciano Dual» y «Estuche de Película de Obsidiana»), así como iconos camuflados inofensivos (notas, contabilidad, calculadora).
- **Aislamiento físico a nivel de sistema en Android**:
  - Integración con `FLAG_SECURE` del sistema Android para bloquear grabación de pantalla, capturas y vistas previas en la tarjeta de multitarea del sistema.
  - Almacenamiento en sandbox físico aislado, con inyección recursiva de `.nomedia` en directorios para bloquear completamente el escaneo de galerías de terceros.

### 7. Análisis de gustos por IA y traducción multilingüe con modelos de lenguaje
- **Análisis de preferencias y perfil estético por IA (AI Persona Insights)**:
  - **Arquitectura totalmente asíncrona y transparente**: Tareas en hilo independiente en segundo plano con modelos de lenguaje local o remoto (DeepSeek, OpenAI, Claude, etc.), sin bloquear la interfaz.
  - **Informe de ADN estético en profundidad**: Analiza las colecciones privadas reales y el historial de etiquetas del usuario, disecciona su ADN estético y genera un ensayo de crítica artística de 2.000 palabras que incluye un «arquetipo estético» (p. ej., *«Explorador nostálgico de la nueva ola»*) y un «espectro audiovisual de época».
  - **Ventana de progreso emergente premium**: Panel flotante de vidrio esmerilado de alta gama que muestra paso a paso el protocolo de cifrado del modelo, la extracción de características del historial y el progreso de generación del perfil.
- **Traducción multilingüe con LLM y persistencia de datos multiplataforma**:
  - Integración nativa con OpenAI, DeepSeek, Claude, Gemini y Doubao (ByteDance), entre otros proveedores.
  - Compatible con «Detectar modelos disponibles» para verificar en línea la lista de modelos accesibles por API con un solo clic.
  - Los datos de traducción se pueden importar y exportar bidireccionalmente en JSON estándar entre plataformas (macOS / Windows / Android) sin pérdida alguna.

### 8. Búsqueda de recursos y sistema de extensiones externas (v2.0)
- **Acceso directo con 1 clic a múltiples sitios**: Desde las páginas de películas y actores, salte directamente a BoyfriendTV, Google y otras bases de datos cinematográficas de referencia usando el título original en inglés, sin necesidad de escribir de nuevo.
- **Integración de búsqueda BT Magnet**: Combina automáticamente el título original de la película y el nombre del estudio en una búsqueda estándar dirigida a motores de recursos externos.
- **Interruptores independientes**: El centro de extensiones permite activar o desactivar de forma individual cada fuente de búsqueda externa con configuración detallada.
- **Motor de scraping incremental y sincronización**: Compatible con scraping de actualización por red bajo demanda y caché offline local de pósteres y fotogramas.

### 9. Internacionalización completa y sistema de trofeos gamificado con 77+ logros
- **7 idiomas de interfaz disponibles**: Chino simplificado (`zh-CN`), Chino tradicional (`zh-TW`), Inglés (`en`), Italiano (`it`), Japonés (`ja`), Español (`es`) y Alemán (`de`).
- **Sistema de trofeos estilo PlayStation**:
  - Más de 77 logros de exploración, búsqueda, enfoque y colección integrados; al desbloquearlos se activa una notificación emergente con animación fluida al estilo PSN.
  - Seguimiento basado en métricas de comportamiento reales, con soporte para reinicio y gestión independiente de los datos de trofeos.

### 10. Cobertura multiplataforma y sincronización de datos en tres dispositivos (macOS / Windows / Android)
- **Cliente de escritorio macOS nativo (`desktop_client/`)**: Mediante la arquitectura Tauri v2 + Rust, compatible perfectamente con Apple Silicon e Intel, con efecto de vidrio esmerilado nativo y atajos de teclado.
- **Cliente de escritorio Windows nativo (`Windows_client/`)**: Directorio independiente desacoplado, motor de análisis Python inteligente con múltiples rutas, supresión de consola (`CREATE_NO_WINDOW`), optimización de fuente Microsoft YaHei, barra de desplazamiento delgada, ajuste de ventana Win11 y instalación ligera de usuario único sin privilegios de administrador mediante NSIS.
- **Cliente móvil Android nativo (`android_client/`)**: Desarrollado nativamente con la pila moderna Kotlin + Jetpack Compose + Room, integrado a la perfección con el esquema de datos del escritorio para consultas, etiquetado y colección offline en cualquier momento y lugar.

---

## 🛠️ Arquitectura tecnológica (Technology Stack)

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
                                   │          Almacenamiento Persistente (Storage)        │
                                   │      GPDb.db (SQLite 3 WAL) + image_cache/          │
                                   └─────────────────────────────────────────────────────┘
```

---

## 🚀 Inicio rápido y descarga (Quick Start)

### Para usuarios finales (Recomendado)

Descargue el instalador precompilado de la versión **`v2.14.0`** desde la sección de [Releases](https://github.com/GeavenMax/GPDb/releases):

| Plataforma | Archivo de instalación | Instrucciones |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.14.0.dmg` | Haga doble clic para montar el archivo y arrastre `GPDb.app` a la carpeta `Aplicaciones`.<br>*(Si al abrir por primera vez aparece una advertencia de notarización, vaya a «Configuración del sistema → Privacidad y seguridad» y haga clic en «Abrir de todas formas», o ejecute `sudo xattr -cr /Applications/GPDb.app` en la terminal)* |
| **🪟 Windows** | `GPDb-Windows-v2.14.0.exe` | Haga doble clic en el instalador para completar la instalación. Arquitectura NSIS de usuario único sin privilegios de administrador: listo para usar de inmediato. |
| **🤖 Android** | `GPDb-Android-v2.14.0-signed.apk` | Descargue el archivo en su dispositivo y toque para instalar (firmado con clave oficial; si el sistema solicita permitir instalación desde fuentes desconocidas, acéptelo). |

---

### 💻 Guía de compilación local para desarrolladores (Developer Guide)

#### Requisitos del entorno
- **Node.js**: 20+ y npm
- **Rust**: 1.78+ (`rustup`, `cargo`)
- **Android SDK** (solo para Android): Android Studio Hedgehog+ o JDK 17+

#### 1. Desarrollo y compilación del cliente de escritorio macOS
```bash
# 1. Clonar el repositorio
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. Entrar al directorio del cliente de escritorio
cd desktop_client

# 3. Instalar dependencias del frontend
npm install

# 4. Iniciar el cliente de escritorio en modo desarrollo
npm run tauri dev

# 5. Compilar la aplicación para producción (.app y .dmg)
npm run tauri build
```

#### 2. Desarrollo y compilación del cliente de escritorio Windows
```powershell
# 1. Entrar al directorio del cliente Windows independiente
cd Windows_client

# 2. Instalar dependencias del frontend
npm install

# 3. Iniciar en modo desarrollo
npm run tauri dev

# 4. Compilar el instalador para producción (genera el instalador NSIS .exe)
npm run tauri build

# O ejecutar directamente el script de compilación listo para usar:
.\build_windows.ps1
```

#### 3. Compilación del cliente móvil Android nativo
```bash
# 1. Entrar al directorio del cliente Android nativo
cd android_client

# 2. Compilar el paquete de prueba Debug
./gradlew assembleDebug

# 3. Compilar el APK Release firmado oficial
./gradlew assembleRelease
```

---

## ⚖️ Aviso legal y exención de responsabilidad (Disclaimer)

1. **Naturaleza del software**:
   GPDb es una **herramienta de código abierto de indexación de metadatos offline y gestión de bases de datos locales para medios de adultos (Universal Offline Adult Media Metadata & Library Management Tool)**.
2. **Ausencia de contenidos**:
   El código fuente y las versiones distribuidas **no contienen, alojan ni distribuyen ningún archivo de vídeo, audio, torrents, datos de torrent ni material gráfico protegido por derechos de autor**. Los campos de ejemplo mostrados en el software se usan únicamente para verificación técnica de la base de datos y pruebas de maquetación de la interfaz.
3. **Responsabilidad del usuario**:
   El usuario asume la total responsabilidad legal derivada de la gestión de sus archivos de base de datos locales mediante este software, o del uso de las funciones de búsqueda y redirección externa. Dicha responsabilidad es ajena a los desarrolladores y colaboradores de código abierto del proyecto.
4. **Mayoría de edad y cumplimiento normativo**:
   Este software está dirigido estrictamente a personas que hayan alcanzado la mayoría de edad legal. Asegúrese de cumplir con las leyes y normativas aplicables en su país o región antes de usar esta herramienta de código abierto.

---

## 📄 Licencia (License)

Este proyecto está bajo la [Licencia MIT](LICENSE). Puede leer, modificar, distribuir e integrar libremente el código de este proyecto, siempre que conserve la información de derechos de autor del autor original y este aviso de exención de responsabilidad en las copias.
