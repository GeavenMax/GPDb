# GPDb · Gestor de base de datos de cine gay para adultos ultrarrápido y centrado en la privacidad

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Un cliente moderno de gestión offline de alto rendimiento, análisis exhaustivo y privacidad absoluta sin rastreo en la nube, diseñado para coleccionistas y aficionados al cine gay para adultos.</strong>
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
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Android%20(In%20Dev)-blue?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=flat-square" alt="Category" />
  <img src="https://img.shields.io/badge/Architecture-Tauri%20v2%20%2B%20Rust%20%2B%20Vue%203-emerald?style=flat-square" alt="Tech Stack" />
  <img src="https://img.shields.io/badge/Database-SQLite%20(WAL%20Mode)-orange?style=flat-square" alt="Database" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=flat-square" alt="Privacy" />
  <img src="https://img.shields.io/badge/License-MIT-purple?style=flat-square" alt="License" />
</p>

---

## 📖 Acerca del proyecto (About GPDb)

**GPDb** (Gay Pornography Database Manager) es un gestor avanzado y completo de **bases de datos offline para medios gay para adultos (Gay Adult Media)**, concebido para entusiastas, coleccionistas digitales e investigadores cinematográficos.

En el ámbito del contenido para adultos, el historial de visualización, las listas de favoritos y las preferencias personales constituyen **la información privada más sensible**. Las plataformas comerciales en la nube conllevan riesgos de rastreo de perfiles, fugas de datos y eliminaciones imprevistas por caducidad de licencias.

**GPDb se rige por una filosofía estricta de «100% Offline-First y privacidad absoluta sin huella»**:
- **Cero dependencia de la nube**: Todos los metadatos, fichas de actores, atributos físicos, miniaturas/pósteres en caché, etiquetas y colecciones residen única y exclusivamente en el disco físico de su dispositivo.
- **Arquitectura local pura**: Sin cuentas de usuario, sin telemetría, sin análisis de uso y sin conexión a servidores externos.
- **Rendimiento excepcional**: Impulsado por el motor nativo en **Rust (`gpdb-core`)** y la arquitectura moderna **Tauri v2 + Vue 3**, gestiona sin esfuerzo **más de 60.000 películas, 100.000 escenas individuales, 6.000 perfiles de actores y 1.300 estudios** a 60 fps fluidos con consultas en milisegundos.

---

## ✨ Características destacadas del sector

### 1. Búsqueda profunda por atributos físicos y corporales
- **Filtros anatómicos de alta precisión**:
  Filtre actores por **Complexión (Build)** (Muscle, Twink, Bear, Hunk, etc.), **Color de pelo**, **Color de ojos**, **Densidad de vello facial y corporal**, **Altura y peso**, **Tono de piel/Etnia**, **Estado del prepucio (Foreskin) y tamaño**, así como **Tatuajes y piercings**.
- **Unificación inteligente de seudónimos (Aliases)**:
  Agrupa automáticamente los nombres artísticos que un mismo actor utilizó en diferentes productoras y épocas para evitar omisiones.
- **Distinción nítida entre películas completas (Films) y escenas (Scenes)**:
  Explore por separado las obras en las que el actor protagoniza largometrajes frente a sus apariciones en clips o episodios cortos.

### 2. Portada inmersiva con calidad de servicio de streaming (Home Feed)
- **Carrusel gigante de pósteres**: Transición automática y fluida de imágenes en alta definición con efecto de paralaje.
- **Tal día como hoy (On This Day)**: Conecta la fecha actual con los estrenos de la época dorada (años 80, 90 y 2000).
- **Estrellas del día (Starlight Today)**: Valida la presencia de fotos en el disco local para destacar a figuras clave con retratos nítidos, sin comodines de texto.
- **Sagas cinematográficas icónicas**: Destaca colecciones de franquicias longevas con múltiples entregas.
- **Ruleta de descubrimiento**: Lance los dados para descubrir joyas ocultas entre más de 60.000 títulos.

### 3. Agrupación inteligente de series y carátulas en collage
- **Algoritmo de clustering**: Identifica números romanos y subtítulos para unir automáticamente entregas dispersas (*Part I, Part II, Part III*) en una sola colección.
- **Carátulas adaptativas en mosaico**: Genera automáticamente composiciones de 1 póster, 2 mitades, 3 cascadas o 4 cuadrantes con sombreado perimetral y renderizado offline instantáneo.

### 4. Fichas de directores y enciclopedia de estudios
- **Historial de directores**: Despliega con un clic la ficha del director con todas sus obras y filtrado directo en la biblioteca.
- **Catálogo histórico de estudios**: Desde gigantes clásicos del celuloide (Falcon, Colt, Catalina, etc.) hasta líderes contemporáneos (Men.com, BelAmi, Lucas Entertainment, Corbin Fisher, etc.).

### 5. Análisis de gustos por IA y perfil estético (AI Persona Insights)
- **Procesamiento totalmente asíncrono**: Tareas en segundo plano con modelos LLM locales o remotos (DeepSeek, OpenAI, Claude, etc.) sin congelar la interfaz.
- **Informe de ADN estético**: Analiza sus colecciones privadas y genera un ensayo de crítica artística de 2.000 palabras junto a un arquetipo estético (p. ej., *«Explorador nostálgico de la nueva ola»*).
- **Ventana de progreso en cristal esmerilado**: Monitoriza de forma transparente cada fase del análisis y la generación.

### 6. Búsqueda de recursos y extensiones externas (v2.0)
- **Acceso directo con 1 clic**: Salte directamente a la búsqueda de actores o títulos en BoyfriendTV, Google y bases de datos cinematográficas utilizando los títulos originales en inglés.
- **Generador de enlaces BT Magnet**: Compone búsquedas estándar con el nombre del estudio y el título para motores externos.
- **Conmutadores independientes**: Active o desactive individualmente cada botón de búsqueda externa desde Configuración.

### 7. Internacionalización completa y trofeos tipo consola
- **7 idiomas integrados**: Chino simplificado (`zh-CN`), Chino tradicional (`zh-TW`), Inglés (`en`), Italiano (`it`), Japonés (`ja`), Español (`es`) y Alemán (`de`).
- **Sistema de trofeos estilo PlayStation**: Decenas de logros desbloqueables al explorar y clasificar su colección, con notificaciones emergentes de cristal líquido.

---

## 🛠️ Pila tecnológica

```
┌─────────────────────────────────────────────────────────────┐
│                    Capa de Interfaz (UI)                    │
│  Vue 3 + Vite + TypeScript + Tailwind CSS + Lucide Icons   │
│  (Diseño responsivo, cristal esmerilado, i18n, estado)      │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Tauri IPC / Puente binario)
┌──────────────────────────────▼──────────────────────────────┐
│                    Capa Anfitrión Tauri v2                  │
│ Protocolo propio (gpdb-img://), control de ventanas         │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Rust Native FFI)
┌──────────────────────────────▼──────────────────────────────┐
│                 Motor Núcleo Rust (gpdb-core)               │
│ • Constructor SQL dinámico e índice difuso (rusqlite)       │
│ • Validador físico de imágenes y resolución de caché local  │
│ • Tareas asíncronas y motor de inferencia LLM               │
│ • Migración y autorreparación de esquemas SQLite            │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                 Almacenamiento Persistente                  │
│       gevi.db (SQLite 3 WAL) + image_cache/ (Local)         │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Inicio rápido

### Para usuarios finales (Recomendado)

Descargue el instalador precompilado desde la sección de [Releases](https://github.com/GeavenMax/GPDb/releases):
- **macOS**: Descargue `GPDb-macOS-v2.4.0.dmg`, abra el archivo y arrastre `GPDb.app` a la carpeta `Aplicaciones`.
- **Android** (Aplicación independiente en desarrollo): Descargue el archivo `.apk` e instálelo en su dispositivo.

### Para desarrolladores (Compilación local)

#### Requisitos
- Node.js 20+ y npm
- Rust 1.78+ (`cargo`)
- macOS 12+ (Compatible con Apple Silicon M-Series e Intel x86_64)

#### Instrucciones
```bash
# 1. Clonar el repositorio
git clone https://github.com/GeavenMax/GPDb.git
cd GPDb

# 2. Entrar al directorio del cliente de escritorio
cd desktop_client

# 3. Instalar dependencias
npm install

# 4. Iniciar en modo desarrollo
npm run tauri dev

# 5. Compilar el paquete final (.app y .dmg)
npm run tauri build
```

---

## ⚖️ Aviso legal y exención de responsabilidad

1. **Naturaleza del software**:
   GPDb es una herramienta de código abierto de indexación de metadatos offline y gestión de bases de datos locales para colecciones personales de medios digitales.
2. **Ausencia de contenidos**:
   El código fuente y las versiones distribuidas **no contienen, alojan ni distribuyen ningún archivo de vídeo, audio, torrents ni material protegido por derechos de autor**.
3. **Responsabilidad del usuario**:
   El usuario asume la total responsabilidad legal derivada de la gestión de sus bases de datos locales y del acceso a enlaces de búsqueda externos.
4. **Mayoría de edad y cumplimiento normativo**:
   Este software está dirigido estrictamente a personas mayores de edad legal. Asegúrese de cumplir con las leyes aplicables en su país o región.

---

## 📄 Licencia

Este proyecto está bajo la [Licencia MIT](LICENSE). Puede usar, modificar y distribuir libremente este software siempre que mantenga el aviso de derechos de autor y este descargo de responsabilidad.
