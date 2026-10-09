# GPDb · Gestor de base de datos de cine gay para adultos ultrarrápido y centrado en la privacidad

<p align="center">
  <img src="./desktop_client/src/assets/icons/scheme-a.svg" alt="GPDb Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Cliente moderno de gestión multiplataforma de alto rendimiento, archivo integral, privacidad absoluta y cero rastreo en la nube, diseñado para entusiastas del cine gay para adultos</strong>
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
  <img src="https://img.shields.io/badge/Version-v2.19.0-orange?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Category-Gay%20Adult%20Video%20Manager-ff69b4?style=for-the-badge" alt="Category" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green?style=for-the-badge" alt="Privacy" />
</p>

> 📢 **Canal oficial**: ¡Suscríbete al [Canal oficial de Telegram de GPDb (@gpdbnews)](https://t.me/gpdbnews) para recibir en primicia los lanzamientos de versiones, actualizaciones incrementales de la base de datos y consejos de uso!

---

## 📖 Posicionamiento del proyecto (About GPDb)

**GPDb** (Gay Pornography Database Manager) es un sistema moderno de gestión de bases de datos offline todo en uno, diseñado específicamente para entusiastas y coleccionistas del **cine gay para adultos (Gay Adult Media)**.

**Compromiso estricto de 100% Local y Offline-First con privacidad absoluta**:
- **Cero dependencia de la nube**: Metadatos de medios, fichas anatómicas de actores, caché de pósteres y etiquetas personales almacenados íntegramente en el disco físico local;
- **Arquitectura monousuario local pura**: Sin cuentas de usuario, sin telemetría ni rastreo de datos, sin subidas a servidores externos;
- **Rendimiento nativo de alta potencia**: Desarrollado con el **núcleo de alto rendimiento en Rust (`gpdb-core`)** + **Tauri v2 + Vue 3** (macOS / Windows) y **Kotlin + Jetpack Compose + Room** (Android nativo). Gestiona con solvencia **63.000+ largometrajes, 134.000+ escenas, 108.000+ actores y 2.400+ productoras**, manteniendo 60 fps fluidos y búsquedas instantáneas en milisegundos.

---

## ✨ Aspectos destacados (Core Highlights)

### 1. Fichas corporales de actores y búsqueda combinada de alta tolerancia
- **Filtros anatómicos de alta precisión**: Filtrado combinado por complexión física (Build), color de pelo, color de ojos, vello corporal, altura, peso, tono de piel, atributos anatómicos (Dick Size / Foreskin) y tatuajes o piercings.
- **Unificación inteligente de alias (AKA)**: Agrupación y deduplicación automática de múltiples nombres artísticos utilizados en diferentes productoras y épocas.
- **Desacoplamiento preciso entre películas y escenas**: Distinción nítida entre apariciones en largometrajes principales y escenas cortas o cameos independientes.
- **Búsqueda combinada de alta tolerancia a fallos**: Indexación ultrarrápida FTS5 + respaldo multicampo SQL; soporte de búsqueda multidimensional combinando títulos bilingües, productora, director, sinopsis y reparto.
- **Integración enciclopédica total con PBC**: Incorporación de fichas completas de Porn Base Central (nombre real, año de debut, estado de actividad, biografía y perfiles interconectados de IAFD/IMDb/X).

### 2. Feed principal inmersivo y galería multicarátula horizontal
- **Cinco flujos de descubrimiento y recomendación**: Carrusel panorámico de pósteres destacados, clásicos estrenados tal día como hoy, estrellas del día con rostros icónicos, grandes sagas clásicas y caja sorpresa de exploración libre.
- **Galería multicarátula horizontal**: Carátula frontal, contraportada y versiones alternativas desplegadas en paralelo de forma adaptativa, con distintivos Front/Back.
- **Lightbox con zoom a pantalla completa**: Zoom suave con rueda del ratón (1.0x ~ 5.0x), desplazamiento por arrastre, doble clic y gestos táctiles de pellizco en dispositivos móviles.

### 3. Sagas inteligentes y mosaico artístico de carátulas
- **Algoritmo de agrupación inteligente**: Detección automática de números romanos y subtítulos para consolidar series y sagas completas en una sola colección.
- **Carátulas en mosaico artístico adaptativo**: Generación automática de composiciones de 1 a 4 imágenes en matriz o simétricas, con renderizado offline ultrarrápido.

### 4. Perfiles de directores y 100% de logos de productoras en ultra HD
- **Historial exclusivo de directores**: Acceso con un clic a la filmografía completa del director con filtrado interactivo en toda la biblioteca.
- **Cobertura del 100% en logos y banners de productoras**: Las 2.490+ productoras de la base de datos cuentan con logos en alta definición al 100% (formato WebP offline directo), y 690+ disponen de banners panorámicos oficiales; tasa de cobertura de logos en películas del 98%.
- **Diseño cómodo de detalles**: Sinopsis argumental desplegada a ancho completo para una lectura clara y matriz compacta de botones de acción esenciales.

### 5. 📸 Modo antiespionaje y tarjetas luminosas para compartir (v2.19.0)
- **Modo antiespionaje global con un solo clic**: Interruptor de ojo en la barra superior de escritorio / conmutador rápido en la cabecera de Explorar en Android; aplica al instante desenfoque gaussiano en pósteres (`blur(24px)`) y anonimización de sinopsis (`blur(7px)`), evitando miradas indiscretas.
- **Tarjetas luminosas de doble carátula para compartir**:
  - Disposición en paralelo de carátula frontal y trasera; recorte centrado inteligente 16:9 sin distorsión para escenas;
  - Halo bilineal adaptativo puramente fuera de pantalla (ajustes preestablecidos: Luminoso, Oscuro, Medianoche);
  - Código QR integrado del canal oficial de Telegram (`t.me/gpdbnews`);
  - Exportación Retina 2x en Canvas de escritorio / captura nativa de mapa de bits en Android para compartir con un clic.

### 6. 🛡️ Suite completa de privacidad y seguridad
- **Escritorio depurado y eficiente**: Supresión de camuflajes innecesarios, enfocado en una gestión de medios pura, ágil y sin distracciones.
- **Bloqueo biométrico y de seguridad en móviles**: Compatibilidad con código PIN y desbloqueo por huella dactilar/facial, con máscara de desenfoque automático al perder el foco.
- **Aislamiento físico a nivel de sistema**: `FLAG_SECURE` en Android para bloquear grabaciones y capturas de pantalla; inyección recursiva de `.nomedia` en el almacenamiento aislado para evitar escaneos de galerías de terceros.

### 7. Motor nativo de traducción LLM en Rust dentro del proceso
- **Núcleo asíncrono Rust con TLS puro**: Cero dependencias externas de Python; respuesta inmediata para pruebas individuales y traducciones por lotes.
- **Soporte multimodelo y rotación inteligente de claves**: Conexión nativa con Google Gemini (rotación automática a clave de reserva ante cuota agotada), protocolo OpenAI (DeepSeek, Claude, Moonshot, Ollama, etc.).
- **Tolerancia a fallos y estandarización**: Lectura fluida de respuestas en texto plano sin errores de análisis forzado de JSON; exportación e importación de datos de traducción en formato JSON estándar entre plataformas.

### 8. Búsqueda de recursos y sistema de plugins (v2.0)
- **Accesos directos matriciales**: Navegación directa con un clic a BoyfriendTV, Google, bases de datos especializadas y motores de búsqueda de enlaces magnet BT.
- **Motor de raspado wiki PBC**: Rastreo exhaustivo de 1.200+ actores, detección de revisiones incrementales y comparación de 25+ atributos (97,3% de precisión).
- **Motor de raspado SmutJunkies**: Indexación completa de 6.700+ actores, algoritmo de alineación de 4 niveles de alta tolerancia y sincronización bidireccional incremental.

### 9. Internacionalización en 7 idiomas y experiencia ligera depurada
- **Soporte nativo en 7 idiomas**: Chino simplificado, Chino tradicional, Inglés, Italiano, Japonés, Español y Alemán.
- **Alineación del 100% con 1.096 términos y respaldo multinivel**: Traducción completa sin caracteres extraños ni inconsistencias gracias a un mecanismo inteligente de fallback.
- **Sincronización bidireccional y arquitectura liviana**: Desacoplamiento y sincronización entre idioma de interfaz y contenidos; eliminación completa de logros virtuales y módulos redundantes para un consumo de memoria mínimo.

### 10. Copia de seguridad universal entre plataformas y actualizaciones en línea
- **Copia de seguridad universal (`GPDb_Backup.json`)**: Exportación/importación en un clic de colecciones, historial, notas y configuración; migración directa y transparente entre macOS, Windows y Android.
- **Comprobación de actualizaciones con reanudación de descargas**: Comparación de versiones mediante la API de GitHub Releases y descarga de alta velocidad con reanudación ante interrupciones.

### 11. Optimización profunda y nativa por plataforma
- **macOS**: Arquitectura nativa Tauri v2 + Rust, optimizada para Apple Silicon e Intel, con efectos de cristal esmerilado del sistema y atajos de teclado.
- **Windows**: Efectos nativos Mica / Acrylic de Windows 11, aislamiento de alto contraste en tema claro, bandeja del sistema con Jump List, indicador en tiempo real en la barra de tareas durante el raspado y aceleración GPU con WebView2 para pantallas de alta tasa de refresco.
- **Android**: Desarrollo 100% nativo con Kotlin + Jetpack Compose + Room, gestos táctiles fluidos y consulta totalmente offline.

---

## 🛠️ Arquitectura técnica (Technology Stack)

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
                                   │      Almacenamiento persistente (Storage)           │
                                   │      GPDb.db (SQLite 3 WAL) + image_cache/          │
                                   └─────────────────────────────────────────────────────┘
```

---

## 🚀 Inicio rápido y descargas (Quick Start)

### Descarga e instalación para usuarios (Recomendado)

Descargue el instalador oficial de la versión **`v2.19.0`** directamente desde la página de [Releases](https://github.com/GeavenMax/GPDb/releases):

| Plataforma | Archivo de instalación | Instrucciones y notas |
| :--- | :--- | :--- |
| ** macOS** | `GPDb-macOS-v2.19.0.dmg` | Haga doble clic para montar el archivo y arrastre `GPDb.app` a la carpeta `Aplicaciones`.<br>*(Si el sistema indica que no está notariado, vaya a «Ajustes del Sistema → Privacidad y seguridad» y haga clic en «Abrir igualmente»)* |
| **🪟 Windows** | `GPDb-Windows-v2.19.0.exe` | Haga doble clic en el instalador para ejecutar. Utiliza arquitectura NSIS monousuario sin necesidad de privilegios de administrador. |
| **🤖 Android** | `GPDb-Android-v2.19.0-signed.apk` | Descargue e instale directamente en el dispositivo móvil (firmado oficialmente con clave privada; si el sistema lo solicita, autorice la instalación desde fuentes desconocidas). |

---

### 💻 Guía de compilación para desarrolladores (Developer Guide)

```bash
# 1. Cliente de escritorio macOS
cd desktop_client && npm install && npm run tauri dev      # Desarrollo local
npm run tauri build                                         # Compilación de producción (.app / .dmg)

# 2. Cliente de escritorio Windows
cd Windows_client && npm install && npm run tauri dev      # Desarrollo local
npm run tauri build                                         # Compilación de producción (.exe)

# 3. Cliente nativo Android
cd android_client && ./gradlew assembleRelease              # Compilar APK Release firmado

# 4. Verificación previa de conformidad de lanzamiento
./git_tasks/pre_release_check.sh                            # Valida rutas, fugas de privacidad y versiones
```

> 💡 Referencias de arquitectura y subsistemas:
> - Especificación de internacionalización (i18n): [`docs/i18n/UI_I18N_SPEC.md`](docs/i18n/UI_I18N_SPEC.md)
> - Sistema de logotipos en ultra HD: [`studio_logos/README.md`](studio_logos/README.md)
> - Flujo de trabajo de lanzamiento automatizado: [`git_tasks/README.md`](git_tasks/README.md)

---

## ⚖️ Aviso legal y exención de responsabilidad (Disclaimer)

1. **Propósito del software**: GPDb es únicamente una **herramienta de código abierto para la gestión de bases de datos y la indexación local de metadatos de medios para adultos sin conexión**.
2. **Exención de contenidos**: El código fuente de este proyecto y sus versiones compiladas **no contienen, no alojan ni distribuyen ningún archivo de vídeo, audio, enlaces torrent ni material gráfico protegido por derechos de autor**. Los campos de muestra exhibidos en el software se utilizan exclusivamente para la verificación técnica de bases de datos y pruebas de diseño de la interfaz.
3. **Responsabilidad del usuario**: El usuario asume de forma exclusiva cualquier responsabilidad legal o infracción de derechos derivada de la gestión de bases de datos locales o de las búsquedas externas realizadas mediante el software.
4. **Cumplimiento normativo**: Este proyecto está destinado estrictamente a usuarios que hayan alcanzado la mayoría de edad legal. Utilice esta herramienta de manera legal y ética, en estricto cumplimiento de las leyes y normativas de su jurisdicción.

---

## 📄 Licencia (License)

Este proyecto se distribuye bajo la licencia [MIT](LICENSE).
