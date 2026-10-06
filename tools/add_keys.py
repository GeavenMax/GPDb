# Auto-update locales script
import os

base_dir = "desktop_client/src/i18n/locales"
locales = ["zh-CN", "zh-TW", "en", "ja", "it", "es", "de"]

batch_additions = {
    "common.retry": {
        "zh-CN": "重试", "zh-TW": "重試", "en": "Retry",
        "ja": "再試行", "it": "Riprova", "es": "Reintentar", "de": "Wiederholen"
    },
    "common.sort": {
        "zh-CN": "排序", "zh-TW": "排序", "en": "Sort",
        "ja": "並べ替え", "it": "Ordina", "es": "Ordenar", "de": "Sortieren"
    },
    "common.minutes": {
        "zh-CN": "分钟", "zh-TW": "分鐘", "en": "min",
        "ja": "分", "it": "min", "es": "min", "de": "Min."
    },
    "common.favorite": {
        "zh-CN": "收藏", "zh-TW": "收藏", "en": "Favorite",
        "ja": "お気に入り", "it": "Preferito", "es": "Favorito", "de": "Favorit"
    },
    "common.unfavorite": {
        "zh-CN": "取消收藏", "zh-TW": "取消收藏", "en": "Unfavorite",
        "ja": "お気に入り解除", "it": "Rimuovi dai preferiti", "es": "Quitar de favoritos", "de": "Aus Favoriten entfernen"
    },
    "search.placeholder": {
        "zh-CN": "搜索影片或演员...", "zh-TW": "搜尋影片或演員...", "en": "Search movies or performers...",
        "ja": "映画または出演者を検索...", "it": "Cerca film o interpreti...", "es": "Buscar películas o actores...", "de": "Filme oder Darsteller suchen..."
    },
    "search.history": {
        "zh-CN": "搜索历史", "zh-TW": "搜尋歷史", "en": "Search History",
        "ja": "検索履歴", "it": "Cronologia ricerche", "es": "Historial de búsqueda", "de": "Suchverlauf"
    },
    "search.hotCategories": {
        "zh-CN": "热门分类标签", "zh-TW": "熱門分類標籤", "en": "Popular Categories",
        "ja": "人気カテゴリー", "it": "Categorie popolari", "es": "Categorías populares", "de": "Beliebte Kategorien"
    },
    "search.emptyPrompt": {
        "zh-CN": "输入关键字开始检索", "zh-TW": "輸入關鍵字開始檢索", "en": "Enter keywords to start search",
        "ja": "キーワードを入力して検索を開始", "it": "Inserisci parole chiave per cercare", "es": "Ingresa palabras clave para buscar", "de": "Suchbegriff eingeben, um zu beginnen"
    },
    "search.noResults": {
        "zh-CN": "未找到相关结果", "zh-TW": "未找到相關結果", "en": "No matching results found",
        "ja": "一致する結果が見つかりません", "it": "Nessun risultato trovato", "es": "No se encontraron resultados", "de": "Keine Ergebnisse gefunden"
    },
    "search.performers": {
        "zh-CN": "演员", "zh-TW": "演員", "en": "Performers",
        "ja": "出演者", "it": "Interpreti", "es": "Actores", "de": "Darsteller"
    },
    "search.movies": {
        "zh-CN": "影片", "zh-TW": "影片", "en": "Movies",
        "ja": "映画", "it": "Film", "es": "Películas", "de": "Filme"
    },
    "browse.categoriesTitle": {
        "zh-CN": "分类标签", "zh-TW": "分類標籤", "en": "Categories",
        "ja": "カテゴリー", "it": "Categorie", "es": "Categorías", "de": "Kategorien"
    },
    "director.searchPrompt": {
        "zh-CN": "搜索导演姓名...", "zh-TW": "搜尋導演姓名...", "en": "Search director name...",
        "ja": "監督名を検索...", "it": "Cerca nome regista...", "es": "Buscar nombre del director...", "de": "Regisseurnamen suchen..."
    },
    "director.sortByWorks": {
        "zh-CN": "按执导作品数", "zh-TW": "按執導作品數", "en": "By Directed Works",
        "ja": "監督作品数順", "it": "Per numero di opere", "es": "Por número de obras", "de": "Nach Anzahl der Werke"
    },
    "director.sortByName": {
        "zh-CN": "按姓名 A-Z", "zh-TW": "按姓名 A-Z", "en": "By Name (A-Z)",
        "ja": "名前順 (A-Z)", "it": "Per nome (A-Z)", "es": "Por nombre (A-Z)", "de": "Nach Name (A-Z)"
    },
    "studio.searchPrompt": {
        "zh-CN": "搜索片商 (支持中英文)...", "zh-TW": "搜尋片商 (支援中英文)...", "en": "Search studios...",
        "ja": "スタジオを検索...", "it": "Cerca studi cinematografici...", "es": "Buscar estudios...", "de": "Studios suchen..."
    },
    "studio.sortByWorks": {
        "zh-CN": "按作品数排序", "zh-TW": "按作品數排序", "en": "By Title Count",
        "ja": "作品数順", "it": "Per numero di titoli", "es": "Por cantidad de títulos", "de": "Nach Anzahl der Titel"
    },
    "studio.sortByName": {
        "zh-CN": "按拼音/字母排序", "zh-TW": "按拼音/字母排序", "en": "By Name (A-Z)",
        "ja": "名前順 (A-Z)", "it": "Per nome (A-Z)", "es": "Por nombre (A-Z)", "de": "Nach Name (A-Z)"
    },
    "studio.noStudiosFound": {
        "zh-CN": "未找到匹配的片商", "zh-TW": "未找到匹配的片商", "en": "No matching studios found",
        "ja": "一致するスタジオが見つかりません", "it": "Nessun studio corrispondente trovato", "es": "No se encontraron estudios coincidentes", "de": "Keine passenden Studios gefunden"
    },
    "performer.searchPrompt": {
        "zh-CN": "搜索演员...", "zh-TW": "搜尋演員...", "en": "Search performers...",
        "ja": "出演者を検索...", "it": "Cerca interpreti...", "es": "Buscar actores...", "de": "Darsteller suchen..."
    },
    "browse.searchInList": {
        "zh-CN": "在当前列表中搜索...", "zh-TW": "在目前清單中搜尋...", "en": "Search in current list...",
        "ja": "現在のリスト内を検索...", "it": "Cerca nell elenco corrente...", "es": "Buscar en la lista actual...", "de": "In aktueller Liste suchen..."
    },
    "browse.closeSearch": {
        "zh-CN": "关闭搜索", "zh-TW": "關閉搜尋", "en": "Close Search",
        "ja": "検索を閉じる", "it": "Chiudi ricerca", "es": "Cerrar búsqueda", "de": "Suche schließen"
    },
    "performer.detailTitle": {
        "zh-CN": "演员档案", "zh-TW": "演員檔案", "en": "Performer Profile",
        "ja": "出演者プロフィール", "it": "Profilo interprete", "es": "Perfil del actor", "de": "Darstellerprofil"
    },
    "performer.noMovies": {
        "zh-CN": "暂无出演影片记录", "zh-TW": "暫無出演影片記錄", "en": "No movie credits recorded",
        "ja": "出演映画の記録はありません", "it": "Nessun film registrato", "es": "No hay películas registradas", "de": "Keine Filme verzeichnet"
    },
    "performer.noEpisodes": {
        "zh-CN": "暂无出演分集记录", "zh-TW": "暫無出演分集記錄", "en": "No episode credits recorded",
        "ja": "出演エピソードの記録はありません", "it": "Nessun episodio registrato", "es": "No hay episodios registrados", "de": "Keine Episoden verzeichnet"
    },
    "performer.birthNameLabel": {
        "zh-CN": "本名", "zh-TW": "本名", "en": "Birth Name",
        "ja": "本名", "it": "Nome di nascita", "es": "Nombre de nacimiento", "de": "Geburtsname"
    },
    "performer.debutYearLabel": {
        "zh-CN": "出道年份", "zh-TW": "出道年份", "en": "Debut Year",
        "ja": "デビュー年", "it": "Anno di debutto", "es": "Año de debut", "de": "Debütjahr"
    },
    "performer.pbcBiography": {
        "zh-CN": "PBC 维基人物小传", "zh-TW": "PBC 維基人物小傳", "en": "PBC Wiki Biography",
        "ja": "PBC Wiki 人物紹介", "it": "Biografia PBC Wiki", "es": "Biografía PBC Wiki", "de": "PBC Wiki Biografie"
    },
    "performer.fullWikiEntry": {
        "zh-CN": "完整词条 ↗", "zh-TW": "完整詞條 ↗", "en": "Full Entry ↗",
        "ja": "完全な記事 ↗", "it": "Voce completa ↗", "es": "Entrada completa ↗", "de": "Vollständiger Eintrag ↗"
    },
    "performer.webProfiles": {
        "zh-CN": "互联档案", "zh-TW": "互聯檔案", "en": "Web Profiles",
        "ja": "ウェブプロフィール", "it": "Profili Web", "es": "Perfiles web", "de": "Web-Profile"
    },
    "performer.bt4gSearch": {
        "zh-CN": "BT4G 搜索", "zh-TW": "BT4G 搜尋", "en": "BT4G Search",
        "ja": "BT4G 検索", "it": "Cerca su BT4G", "es": "Buscar en BT4G", "de": "BT4G-Suche"
    },
    "performer.pbcWiki": {
        "zh-CN": "PBC 百科 ↗", "zh-TW": "PBC 百科 ↗", "en": "PBC Wiki ↗",
        "ja": "PBC 百科 ↗", "it": "PBC Wiki ↗", "es": "PBC Wiki ↗", "de": "PBC Wiki ↗"
    },
    "performer.aka": {
        "zh-CN": "曾用艺名 / 别名 (AKA)", "zh-TW": "曾用藝名 / 別名 (AKA)", "en": "Also Known As (AKA)",
        "ja": "別名 / 名義 (AKA)", "it": "Pseudonimi / Alias (AKA)", "es": "Alias / Seudónimos (AKA)", "de": "Bekannt als / Alias (AKA)"
    },
    "performer.totalCount": {
        "zh-CN": "共 {count} 个", "zh-TW": "共 {count} 個", "en": "{count} total",
        "ja": "全 {count} 件", "it": "{count} totali", "es": "{count} en total", "de": "Gesamt: {count}"
    },
    "performer.collapse": {
        "zh-CN": "收起", "zh-TW": "收起", "en": "Collapse",
        "ja": "折りたたむ", "it": "Comprimi", "es": "Contraer", "de": "Einklappen"
    },
    "performer.expandAll": {
        "zh-CN": "展开全部", "zh-TW": "展開全部", "en": "Expand All",
        "ja": "すべて展開", "it": "Espandi tutto", "es": "Expandir todo", "de": "Alle ausklappen"
    },
    "studio.releasedMovies": {
        "zh-CN": "发行作品 ({count})", "zh-TW": "發行作品 ({count})", "en": "Released Movies ({count})",
        "ja": "リリース作品 ({count})", "it": "Film pubblicati ({count})", "es": "Películas lanzadas ({count})", "de": "Veröffentlichte Filme ({count})"
    },
    "studio.releasedEpisodes": {
        "zh-CN": "发行分集 ({count})", "zh-TW": "發行分集 ({count})", "en": "Released Episodes ({count})",
        "ja": "リリースエピソード ({count})", "it": "Episodi pubblicati ({count})", "es": "Episodios lanzados ({count})", "de": "Veröffentlichte Episoden ({count})"
    },
    "studio.noMovies": {
        "zh-CN": "暂无发行作品记录", "zh-TW": "暫無發行作品記錄", "en": "No released movies recorded",
        "ja": "リリース作品の記録はありません", "it": "Nessun film registrato", "es": "No hay películas registradas", "de": "Keine Filme verzeichnet"
    },
    "studio.noEpisodes": {
        "zh-CN": "暂无发行分集记录", "zh-TW": "暫無發行分集記錄", "en": "No released episodes recorded",
        "ja": "リリースエピソードの記録はありません", "it": "Nessun episodio registrato", "es": "No hay episodios registrados", "de": "Keine Episoden verzeichnet"
    },
    "episode.detailTitle": {
        "zh-CN": "分集档案", "zh-TW": "分集檔案", "en": "Episode Details",
        "ja": "エピソード詳細", "it": "Dettagli episodio", "es": "Detalles del episodio", "de": "Episodendetails"
    },
    "episode.releaseDate": {
        "zh-CN": "发布日期", "zh-TW": "發布日期", "en": "Release Date",
        "ja": "公開日", "it": "Data di uscita", "es": "Fecha de estreno", "de": "Veröffentlichungsdatum"
    },
    "episode.unknown": {
        "zh-CN": "未知分集", "zh-TW": "未知分集", "en": "Unknown Episode",
        "ja": "不明なエピソード", "it": "Episodio sconosciuto", "es": "Episodio desconocido", "de": "Unbekannte Episode"
    },
    "movie.btSearch": {
        "zh-CN": "BT 磁链", "zh-TW": "BT 磁力", "en": "BT Magnet",
        "ja": "BT 磁石リンク", "it": "Magnet BT", "es": "Magnet BT", "de": "BT Magnet"
    },
    "movie.posterFallback": {
        "zh-CN": "暂无海报", "zh-TW": "暫無海報", "en": "No Poster Available",
        "ja": "ポスターなし", "it": "Nessun poster disponibile", "es": "Sin póster disponible", "de": "Kein Poster verfügbar"
    },
    "settings.securityPinTitle": {
        "zh-CN": "设置安全锁 PIN 码", "zh-TW": "設定安全鎖 PIN 碼", "en": "Set Security PIN",
        "ja": "セキュリティPINを設定", "it": "Imposta PIN di sicurezza", "es": "Establecer PIN de seguridad", "de": "Sicherheits-PIN festlegen"
    },
    "settings.securityPinPrompt": {
        "zh-CN": "请输入 4~6 位数字应用解锁密码：", "zh-TW": "請輸入 4~6 位數字應用解鎖密碼：", "en": "Enter 4~6 digits unlock code:",
        "ja": "4〜6桁のロック解除PINを入力してください：", "it": "Inserisci il codice di sblocco a 4~6 cifre:", "es": "Introduce el PIN de desbloqueo de 4 a 6 dígitos:", "de": "Geben Sie die 4- bis 6-stellige PIN ein:"
    },
    "settings.pinLabel": {
        "zh-CN": "PIN 码", "zh-TW": "PIN 碼", "en": "PIN Code",
        "ja": "PIN コード", "it": "Codice PIN", "es": "Código PIN", "de": "PIN-Code"
    },
    "settings.pinSaved": {
        "zh-CN": "安全锁密码已设置", "zh-TW": "安全鎖密碼已設定", "en": "Security PIN has been set",
        "ja": "セキュリティPINが設定されました", "it": "PIN di sicurezza impostato", "es": "PIN de seguridad configurado", "de": "Sicherheits-PIN eingerichtet"
    },
    "settings.pinMinLength": {
        "zh-CN": "密码至少需 4 位数字", "zh-TW": "密碼至少需 4 位數字", "en": "PIN must be at least 4 digits",
        "ja": "PINは4桁以上である必要があります", "it": "Il PIN deve essere di almeno 4 cifre", "es": "El PIN debe tener al menos 4 dígitos", "de": "Die PIN muss mindestens 4 Ziffern enthalten"
    },
    "settings.lockTimeoutTitle": {
        "zh-CN": "锁屏等待超时", "zh-TW": "鎖屏等待超時", "en": "Lock Screen Timeout",
        "ja": "画面ロック待機時間", "it": "Timeout blocco schermo", "es": "Tiempo de espera para bloqueo", "de": "Sperrbildschirm-Timeout"
    },
    "settings.timeoutImmediately": {
        "zh-CN": "立即锁定", "zh-TW": "立即鎖定", "en": "Immediately",
        "ja": "即時ロック", "it": "Immediatamente", "es": "Inmediatamente", "de": "Sofort"
    },
    "settings.timeout15s": {
        "zh-CN": "15 秒", "zh-TW": "15 秒", "en": "15 seconds",
        "ja": "15 秒", "it": "15 secondi", "es": "15 segundos", "de": "15 Sekunden"
    },
    "settings.timeout30s": {
        "zh-CN": "30 秒", "zh-TW": "30 秒", "en": "30 seconds",
        "ja": "30 秒", "it": "30 secondi", "es": "30 segundos", "de": "30 Sekunden"
    },
    "settings.timeout1m": {
        "zh-CN": "1 分钟", "zh-TW": "1 分鐘", "en": "1 minute",
        "ja": "1 分", "it": "1 minuto", "es": "1 minuto", "de": "1 Minute"
    },
    "settings.timeout5m": {
        "zh-CN": "5 分钟", "zh-TW": "5 分鐘", "en": "5 minutes",
        "ja": "5 分", "it": "5 minuti", "es": "5 minutos", "de": "5 Minuten"
    },
    "settings.panicActionTitle": {
        "zh-CN": "脱身响应动作", "zh-TW": "脫身響應動作", "en": "Panic Action",
        "ja": "緊急離脱アクション", "it": "Azione antipanico", "es": "Acción de emergencia", "de": "Panik-Aktion"
    },
    "settings.panicCalc": {
        "zh-CN": "跳转伪装计算器 (可输入 PIN 解锁)", "zh-TW": "跳轉偽裝計算機 (可輸入 PIN 解鎖)", "en": "Switch to fake calculator (Enter PIN to unlock)",
        "ja": "電卓画面に偽装 (PIN入力で解除)", "it": "Passa alla calcolatrice fittizia (PIN per sbloccare)", "es": "Cambiar a calculadora falsa (PIN para desbloquear)", "de": "Zu Fake-Rechner wechseln (PIN zum Entsperren)"
    },
    "settings.panicHome": {
        "zh-CN": "迅速退回系统主屏幕", "zh-TW": "迅速退回系統主畫面", "en": "Quickly return to home screen",
        "ja": "ホーム画面へ素早く戻る", "it": "Torna rapidamente alla schermata iniziale", "es": "Volver rápidamente a la pantalla de inicio", "de": "Schnell zum Startbildschirm zurückkehren"
    },
    "settings.panicKill": {
        "zh-CN": "直接销毁应用进程", "zh-TW": "直接銷毀應用程式行程", "en": "Kill app process immediately",
        "ja": "アプリプロセスを直ちに終了", "it": "Termina immediatamente il processo dell app", "es": "Cerrar proceso de la app inmediatamente", "de": "App-Prozess sofort beenden"
    },
    "settings.aiConfigTitle": {
        "zh-CN": "AI 翻译引擎配置", "zh-TW": "AI 翻譯引擎設定", "en": "AI Translation Engine Settings",
        "ja": "AI 翻訳エンジン設定", "it": "Configurazione motore traduzione AI", "es": "Configuración del motor de traducción de IA", "de": "KI-Übersetzungs-Engine konfigurieren"
    },
    "settings.providerPlaceholder": {
        "zh-CN": "服务商 (如 OpenAI, DeepSeek)", "zh-TW": "服務商 (如 OpenAI, DeepSeek)", "en": "Provider (e.g. OpenAI, DeepSeek)",
        "ja": "プロバイダー (例: OpenAI, DeepSeek)", "it": "Provider (es. OpenAI, DeepSeek)", "es": "Proveedor (ej. OpenAI, DeepSeek)", "de": "Anbieter (z.B. OpenAI, DeepSeek)"
    },
    "settings.modelPlaceholder": {
        "zh-CN": "Model (如 gpt-4o-mini, deepseek-chat)", "zh-TW": "Model (如 gpt-4o-mini, deepseek-chat)", "en": "Model (e.g. gpt-4o-mini, deepseek-chat)",
        "ja": "モデル (例: gpt-4o-mini, deepseek-chat)", "it": "Modello (es. gpt-4o-mini, deepseek-chat)", "es": "Modelo (ej. gpt-4o-mini, deepseek-chat)", "de": "Modell (z.B. gpt-4o-mini, deepseek-chat)"
    },
    "settings.autoTranslateDetail": {
        "zh-CN": "进入详情页自动触发翻译", "zh-TW": "進入詳情頁自動觸發翻譯", "en": "Auto-translate synopsis on detail page",
        "ja": "詳細ページを開いたときに自動翻訳", "it": "Traduci automaticamente nella pagina dei dettagli", "es": "Traducir automáticamente en la página de detalles", "de": "Synopsis auf Detailseite automatisch übersetzen"
    },
    "settings.aiConfigSaved": {
        "zh-CN": "AI 翻译配置已保存", "zh-TW": "AI 翻譯設定已儲存", "en": "AI translation config saved",
        "ja": "AI 翻訳設定が保存されました", "it": "Configurazione traduzione AI salvata", "es": "Configuración de traducción guardada", "de": "KI-Übersetzungskonfiguration gespeichert"
    },
    "settings.iconSchemeA": {
        "zh-CN": "方案一：经典典藏蓝 (Scheme A · 默认)", "zh-TW": "方案一：經典典藏藍 (Scheme A · 預設)", "en": "Scheme A: Classic Royal Blue (Default)",
        "ja": "スキームA: クラシックブルー (デフォルト)", "it": "Schema A: Blu reale classico (Predefinito)", "es": "Esquema A: Azul real clásico (Predeterminado)", "de": "Schema A: Klassisches Königsblau (Standard)"
    },
    "settings.iconSchemeD": {
        "zh-CN": "方案二：黑曜石金 / 极简 (Scheme D)", "zh-TW": "方案二：黑曜石金 / 極簡 (Scheme D)", "en": "Scheme D: Obsidian Gold / Minimalist",
        "ja": "スキームD: オブシディアンゴールド / ミニマル", "it": "Schema D: Oro ossidiana / Minimalista", "es": "Esquema D: Oro obsidiana / Minimalista", "de": "Schema D: Obsidiangold / Minimalistisch"
    },
    "settings.aiSectionTitle": {
        "zh-CN": "AI 智能翻译引擎", "zh-TW": "AI 智慧翻譯引擎", "en": "AI Translation Engine",
        "ja": "AI 翻訳エンジン", "it": "Motore di traduzione AI", "es": "Motor de traducción con IA", "de": "KI-Übersetzungs-Engine"
    },
    "settings.aiSectionSubtitle": {
        "zh-CN": "配置大模型直白翻译服务 (OpenAI / DeepSeek / Claude / Ollama)", "zh-TW": "設定大模型直白翻譯服務 (OpenAI / DeepSeek / Claude / Ollama)", "en": "Configure LLM translation services (OpenAI / DeepSeek / Claude / Ollama)",
        "ja": "LLM翻訳サービスの設定 (OpenAI / DeepSeek / Claude / Ollama)", "it": "Configura servizi di traduzione LLM (OpenAI / DeepSeek / Claude / Ollama)", "es": "Configurar servicios de traducción LLM (OpenAI / DeepSeek / Claude / Ollama)", "de": "LLM-Übersetzungsdienste einrichten (OpenAI / DeepSeek / Claude / Ollama)"
    },
    "settings.aiProviderConfig": {
        "zh-CN": "服务商与模型配置", "zh-TW": "服務商與模型設定", "en": "Provider & Model Configuration",
        "ja": "プロバイダーとモデルの設定", "it": "Configurazione provider e modello", "es": "Configuración de proveedor y modelo", "de": "Anbieter- & Modellkonfiguration"
    },
    "settings.aiConfigured": {
        "zh-CN": "已配置", "zh-TW": "已設定", "en": "Configured",
        "ja": "設定済み", "it": "Configurato", "es": "Configurado", "de": "Konfiguriert"
    },
    "settings.aiClickToConfig": {
        "zh-CN": "点击配置 API 密钥与端点", "zh-TW": "點擊設定 API 金鑰與端點", "en": "Tap to configure API key & endpoint",
        "ja": "APIキーとエンドポイントを設定", "it": "Tocca per configurare chiave API ed endpoint", "es": "Toca para configurar clave API y endpoint", "de": "Tippen zum Konfigurieren von API-Schlüssel & Endpunkt"
    },
    "settings.translationProgress": {
        "zh-CN": "翻译数据进度", "zh-TW": "翻譯資料進度", "en": "Translation Progress",
        "ja": "翻訳進捗", "it": "Avanzamento traduzioni", "es": "Progreso de traducción", "de": "Übersetzungsfortschritt"
    },
    "settings.translationStatsRefreshed": {
        "zh-CN": "翻译统计已刷新", "zh-TW": "翻譯統計已重新整理", "en": "Translation statistics refreshed",
        "ja": "翻訳の統計を更新しました", "it": "Statistiche di traduzione aggiornate", "es": "Estadísticas de traducción actualizadas", "de": "Übersetzungsstatistik aktualisiert"
    },
    "settings.syncSectionTitle": {
        "zh-CN": "同步与数据存储", "zh-TW": "同步與資料儲存", "en": "Sync & Data Storage",
        "ja": "同期とデータストレージ", "it": "Sincronizzazione e archiviazione dati", "es": "Sincronización y almacenamiento de datos", "de": "Synchronisierung & Datenspeicherung"
    },
    "settings.syncSectionSubtitle": {
        "zh-CN": "管理官网增量数据更新及本地库挂载", "zh-TW": "管理官網增量資料更新及本地庫掛載", "en": "Manage official incremental data updates & library mount",
        "ja": "公式サイトからの差分更新とライブラリのマウントを管理", "it": "Gestisci aggiornamenti incrementali e montaggio libreria", "es": "Gestionar actualizaciones incrementales y montaje de biblioteca", "de": "Inkrementelle Updates & Bibliotheks-Mount verwalten"
    },
    "settings.autoSyncTitle": {
        "zh-CN": "启动时后台自动增量同步", "zh-TW": "啟動時背景自動增量同步", "en": "Auto Background Sync on Launch",
        "ja": "起動時のバックグラウンド自動同期", "it": "Sincronizzazione automatica in background all avvio", "es": "Sincronización automática en segundo plano al iniciar", "de": "Automatische Hintergrund-Synchronisierung beim Start"
    },
    "settings.autoSyncSubtitle": {
        "zh-CN": "打开应用后在后台静默拉取官网最新长片、分集、演员并缓存海报", "zh-TW": "開啟應用程式後在背景靜默抓取官網最新長片、分集、演員並快取海報", "en": "Silently fetch newest movies, episodes, and performers with poster caching on app launch",
        "ja": "アプリ起動時に最新の映画、エピソード、出演者とポスターをサイレント同期", "it": "Scarica silenziosamente gli ultimi film, episodi, interpreti e locandine all avvio", "es": "Descarga silenciosamente las últimas películas, episodios y actores al abrir la app", "de": "Neueste Filme, Episoden und Darsteller beim Start unbemerkt im Hintergrund abrufen"
    },
    "settings.scheduleSyncTitle": {
        "zh-CN": "系统定时后台同步", "zh-TW": "系統定時背景同步", "en": "Scheduled Periodic Background Sync",
        "ja": "定期バックグラウンド同期", "it": "Sincronizzazione periodica programmata", "es": "Sincronización programada en segundo plano", "de": "Geplante Hintergrund-Synchronisierung"
    },
    "settings.scheduleSyncSubtitle": {
        "zh-CN": "设备连接 Wi-Fi 网络时每日定时在后台静默增量同步", "zh-TW": "裝置連接 Wi-Fi 網路時每日定時在背景靜默增量同步", "en": "Periodically sync incrementally in background when connected to Wi-Fi",
        "ja": "Wi-Fi接続時に毎日バックグラウンドで差分同期", "it": "Sincronizza giornalmente in background quando connesso al Wi-Fi", "es": "Sincronizar a diario en segundo plano al estar conectado a Wi-Fi", "de": "Täglich im Hintergrund synchronisieren, wenn mit WLAN verbunden"
    },
    "settings.cacheSyncToPublicTitle": {
        "zh-CN": "缓存图片同步写回公共目录", "zh-TW": "快取圖片同步寫回公共目錄", "en": "Sync Cached Images to Public Directory",
        "ja": "キャッシュ画像を共有ディレクトリに書き戻す", "it": "Sincronizza immagini memorizzate nella cartella pubblica", "es": "Sincronizar imágenes en caché con el directorio público", "de": "Zwischengespeicherte Bilder in öffentliches Verzeichnis schreiben"
    },
    "settings.cacheSyncToPublicSubtitle": {
        "zh-CN": "开启后网络缓存图片将写回 Documents/GPDb/image_cache/，方便文件管理器或电脑直接查看；关闭则仅保存在应用内部私有沙盒中", "zh-TW": "開啟後網路快取圖片將寫回 Documents/GPDb/image_cache/，方便檔案管理員或電腦直接查看；關閉則僅儲存在應用程式內部私有沙盒中", "en": "Save cached images to Documents/GPDb/image_cache/ for direct access; otherwise keep in app sandbox",
        "ja": "有効にすると画像が Documents/GPDb/image_cache/ に保存され外部から閲覧可能になります", "it": "Salva le immagini in Documents/GPDb/image_cache/ per accesso esterno", "es": "Guarda las imágenes en Documents/GPDb/image_cache/ para acceso externo", "de": "Bilder in Documents/GPDb/image_cache/ ablegen, um extern darauf zuzugreifen"
    },
    "settings.checkNewEntries": {
        "zh-CN": "检查官方最新条目 (/newm, /newe, /newp)", "zh-TW": "檢查官方最新條目 (/newm, /newe, /newp)", "en": "Check Official Updates (/newm, /newe, /newp)",
        "ja": "公式の最新更新を確認 (/newm, /newe, /newp)", "it": "Controlla aggiornamenti ufficiali (/newm, /newe, /newp)", "es": "Verificar actualizaciones oficiales (/newm, /newe, /newp)", "de": "Offizielle Aktualisierungen prüfen (/newm, /newe, /newp)"
    },
    "settings.checkingUpdates": {
        "zh-CN": "正在检查官网最新数据...", "zh-TW": "正在檢查官網最新資料...", "en": "Checking latest data from official source...",
        "ja": "公式サイトの最新データを確認中...", "it": "Verifica degli ultimi dati dalla fonte ufficiale...", "es": "Verificando los últimos datos de la fuente oficial...", "de": "Überprüfe neueste Daten von offizieller Quelle..."
    },
    "settings.alreadyLatestData": {
        "zh-CN": "当前本地已是最新，暂无新内容发布。", "zh-TW": "目前本機已是最新，暫無新內容發布。", "en": "Local library is up to date, no new entries found.",
        "ja": "ライブラリは最新です。新しい項目はありません。", "it": "La libreria è aggiornata, nessun nuovo elemento.", "es": "La biblioteca está al día, no hay nuevos contenidos.", "de": "Lokale Bibliothek ist auf dem neuesten Stand."
    },
    "settings.syncNowAndScrape": {
        "zh-CN": "立即入库并后台刮削海报", "zh-TW": "立即入庫並背景刮削海報", "en": "Sync Now & Scrape Posters in Background",
        "ja": "今すぐ取り込み＆ポスターを取得", "it": "Importa ora e scarica le locandine in background", "es": "Importar ahora y descargar pósteres en segundo plano", "de": "Jetzt importieren & Poster im Hintergrund laden"
    },
    "settings.syncCompleted": {
        "zh-CN": "完成", "zh-TW": "完成", "en": "Completed",
        "ja": "完了", "it": "Completato", "es": "Completado", "de": "Abgeschlossen"
    },
    "settings.remountDb": {
        "zh-CN": "重新挂载数据库", "zh-TW": "重新掛載資料庫", "en": "Remount Database",
        "ja": "データベースを再マウント", "it": "Ricollega database", "es": "Remontar base de datos", "de": "Datenbank neu mounten"
    },
    "settings.remountDbSubtitle": {
        "zh-CN": "断开当前数据库并重新选择存储位置", "zh-TW": "斷開目前資料庫並重新選擇儲存位置", "en": "Disconnect current database and select a new storage location",
        "ja": "現在のデータベースを切断し、別の場所を選択", "it": "Disconnetti il database corrente e scegli una nuova posizione", "es": "Desconectar base de datos actual y elegir nueva ubicación", "de": "Aktuelle Datenbank trennen und Speicherort neu wählen"
    },
    "settings.exportUserBackup": {
        "zh-CN": "导出通用配置备份 (JSON)", "zh-TW": "匯出通用設定備份 (JSON)", "en": "Export User Data Backup (JSON)",
        "ja": "ユーザー設定バックアップをエクスポート (JSON)", "it": "Esporta backup dati utente (JSON)", "es": "Exportar copia de seguridad de usuario (JSON)", "de": "Benutzerdaten-Backup exportieren (JSON)"
    },
    "settings.exportUserBackupSubtitle": {
        "zh-CN": "导出“收藏”、统计时长与用户个人数据，四端通用", "zh-TW": "匯出「收藏」、統計時長與使用者個人資料，四端通用", "en": "Export favorites, view time, and statistics across macOS, Windows, iOS, and Android",
        "ja": "お気に入り、視聴時間、個人データをエクスポート (各OS共通)", "it": "Esporta preferiti, statistiche e dati utente per tutte le piattaforme", "es": "Exportar favoritos, estadísticas y datos de usuario multiplataforma", "de": "Favoriten, Nutzungsdauer und Statistiken plattformübergreifend exportieren"
    },
    "settings.importUserBackup": {
        "zh-CN": "导入通用配置恢复 (JSON)", "zh-TW": "匯入通用設定復原 (JSON)", "en": "Import User Data Backup (JSON)",
        "ja": "ユーザー設定バックアップをインポート (JSON)", "it": "Importa backup dati utente (JSON)", "es": "Importar copia de seguridad de usuario (JSON)", "de": "Benutzerdaten-Backup importieren (JSON)"
    },
    "settings.importUserBackupSubtitle": {
        "zh-CN": "从 macOS、Windows、iOS 或其他设备备份导入收藏与统计", "zh-TW": "從 macOS、Windows、iOS 或其他裝置備份匯入收藏與統計", "en": "Import favorites and statistics from macOS, Windows, iOS, or Android backups",
        "ja": "macOS、Windows、iOS、Android のバックアップから復元", "it": "Ripristina preferiti e statistiche da backup di macOS, Windows, iOS o Android", "es": "Restaurar favoritos y estadísticas de copias de seguridad de cualquier plataforma", "de": "Favoriten und Statistiken aus Backups aller Plattformen importieren"
    },
    "settings.aboutSectionTitle": {
        "zh-CN": "关于与软件更新", "zh-TW": "關於與軟體更新", "en": "About & App Updates",
        "ja": "アプリ情報とアップデート", "it": "Info e aggiornamenti", "es": "Acerca de y actualizaciones", "de": "Über & App-Updates"
    },
    "settings.aboutVersionSubtitle": {
        "zh-CN": "巡检 GitHub Releases 官方版本", "zh-TW": "巡檢 GitHub Releases 官方版本", "en": "Inspect official GitHub Releases",
        "ja": "GitHub Releases 公式バージョンを確認", "it": "Verifica le versioni ufficiali su GitHub Releases", "es": "Verificar versiones oficiales en GitHub Releases", "de": "Offizielle Releases auf GitHub prüfen"
    },
    "settings.checkAppUpdate": {
        "zh-CN": "检查新版本更新", "zh-TW": "檢查新版本更新", "en": "Check for App Updates",
        "ja": "アップデートを確認", "it": "Verifica aggiornamenti", "es": "Buscar actualizaciones", "de": "Nach Updates suchen"
    },
    "settings.checkingAppUpdate": {
        "zh-CN": "正在连接 GitHub 巡检最新版本...", "zh-TW": "正在連線 GitHub 巡檢最新版本...", "en": "Connecting to GitHub to check latest release...",
        "ja": "GitHubに接続して最新版を確認中...", "it": "Connessione a GitHub per verificare l ultima versione...", "es": "Conectando con GitHub para buscar la versión más reciente...", "de": "Verbindung zu GitHub zur Prüfung auf neue Versionen..."
    },
    "settings.checkAppUpdatePrompt": {
        "zh-CN": "点击立即检测官方仓库是否有新版本", "zh-TW": "點擊立即檢測官方倉庫是否有新版本", "en": "Tap to check if a newer version is available on GitHub",
        "ja": "タップして新しいバージョンがあるか確認", "it": "Tocca per verificare la disponibilità di nuove versioni", "es": "Toca para verificar si hay una nueva versión disponible", "de": "Tippen, um nach neuen Versionen zu suchen"
    },
    "settings.alreadyLatestVersion": {
        "zh-CN": "当前已是最新版本", "zh-TW": "目前已是最新版本", "en": "Already on the latest version",
        "ja": "最新バージョンを使用中です", "it": "Stai già utilizzando l ultima versione", "es": "Ya estás utilizando la versión más reciente", "de": "Sie verwenden bereits die neueste Version"
    },
    "settings.openRepo": {
        "zh-CN": "开源代码仓库", "zh-TW": "開原始碼倉庫", "en": "Open Source Repository",
        "ja": "オープンソースリポジトリ", "it": "Repository Open Source", "es": "Repositorio de código abierto", "de": "Open-Source-Repository"
    },
    "settings.visitRepo": {
        "zh-CN": "访问 ↗", "zh-TW": "訪問 ↗", "en": "Visit ↗",
        "ja": "表示 ↗", "it": "Visita ↗", "es": "Visitar ↗", "de": "Besuchen ↗"
    },
    "analytics.title": {
        "zh-CN": "本地光影漫游纪实", "zh-TW": "本地光影漫遊紀實", "en": "Local Cinematic Chronicle",
        "ja": "ローカルシネマクロニクル", "it": "Cronaca cinematografica locale", "es": "Crónica cinematográfica local", "de": "Lokale Film-Chronik"
    },
    "analytics.journeySince": {
        "zh-CN": "自 {date} 启程 · 忠实记录每一次影视交互", "zh-TW": "自 {date} 啟程 · 忠實記錄每一次影視互動", "en": "Since {date} · Faithfully chronicling every interaction",
        "ja": "{date} から開始 · すべての映画インタラクションを記録", "it": "Dal {date} · Cronaca fedele di ogni interazione cinematografica", "es": "Desde {date} · Registrando fielmente cada interacción", "de": "Seit {date} · Jede Film-Interaktion getreu dokumentiert"
    },
    "analytics.appActiveDwell": {
        "zh-CN": "应用交互活跃时长", "zh-TW": "應用程式互動活躍時長", "en": "Active App Interaction Time",
        "ja": "アクティブ利用時間", "it": "Tempo di interazione attivo nell app", "es": "Tiempo activo en la aplicación", "de": "Aktive App-Nutzungszeit"
    },
    "analytics.moviesExplored": {
        "zh-CN": "部长片", "zh-TW": "部長片", "en": "movies",
        "ja": "作品", "it": "film", "es": "películas", "de": "Filme"
    },
    "analytics.episodesClips": {
        "zh-CN": "段独立分集", "zh-TW": "段獨立分集", "en": "episodes & clips",
        "ja": "エピソード", "it": "episodi", "es": "episodios", "de": "Episoden"
    },
    "analytics.performersLooked": {
        "zh-CN": "位演员", "zh-TW": "位演員", "en": "performers",
        "ja": "名の出演者", "it": "interpreti", "es": "actores", "de": "Darsteller"
    },
    "analytics.searchesExecuted": {
        "zh-CN": "次检索", "zh-TW": "次檢索", "en": "searches",
        "ja": "回の検索", "it": "ricerche", "es": "búsquedas", "de": "Suchanfragen"
    },
    "analytics.daysActive": {
        "zh-CN": "天打卡", "zh-TW": "天打卡", "en": "active days",
        "ja": "日アクティブ", "it": "giorni attivi", "es": "días activos", "de": "aktive Tage"
    },
    "analytics.nightOwlClicks": {
        "zh-CN": "次午夜探索", "zh-TW": "次午夜探索", "en": "midnight dives",
        "ja": "回の深夜閲覧", "it": "esplorazioni notturne", "es": "inmersiones nocturnas", "de": "Mitternachts-Erkundungen"
    },
    "analytics.translationsCount": {
        "zh-CN": "篇 AI 翻译", "zh-TW": "篇 AI 翻譯", "en": "AI translations",
        "ja": "件のAI翻訳", "it": "traduzioni AI", "es": "traducciones de IA", "de": "KI-Übersetzungen"
    },
    "analytics.interactionsTitle": {
        "zh-CN": "互动收藏与打分", "zh-TW": "互動收藏與評分", "en": "Favorites & Ratings",
        "ja": "お気に入りと評価", "it": "Preferiti e valutazioni", "es": "Favoritos y calificaciones", "de": "Favoriten & Bewertungen"
    },
    "analytics.interactionsSubtitle": {
        "zh-CN": "个人偏好与评分标记", "zh-TW": "個人偏好與評分標記", "en": "Personal taste and star ratings",
        "ja": "個人の好みと星評価の記録", "it": "Preferenze personali e valutazioni con stelle", "es": "Preferencias personales y valoraciones", "de": "Persönliche Vorlieben & Sternbewertungen"
    },
    "analytics.directorsStudiosTitle": {
        "zh-CN": "导演与片商", "zh-TW": "導演與片商", "en": "Directors & Studios",
        "ja": "監督とスタジオ", "it": "Registi e studi cinematografici", "es": "Directores y estudios", "de": "Regisseure & Studios"
    },
    "analytics.directorsStudiosSubtitle": {
        "zh-CN": "深入幕后制作脉络", "zh-TW": "深入幕後製作脈絡", "en": "Behind-the-scenes production insights",
        "ja": "制作の舞台裏と系譜", "it": "Dietro le quinte della produzione", "es": "Detrás de escena de la producción", "de": "Einblicke in die Hintergründe der Produktion"
    },
    "analytics.privacyGuaranteeTitle": {
        "zh-CN": "100% 本地隐私保证", "zh-TW": "100% 本地隱私保證", "en": "100% Local Privacy Guarantee",
        "ja": "100% ローカルプライバシー保証", "it": "Garanzia di privacy al 100% locale", "es": "Garantía de privacidad 100% local", "de": "100% lokale Privatsphäre-Garantie"
    },
    "analytics.privacyGuaranteeText": {
        "zh-CN": "GPDb 坚持绝对纯粹的无网络追踪设计。所有使用统计数据完全存储在手机私有沙盒内，绝无任何第三方埋点探针或远程数据分析，随时可在设置中关闭或一键清空。",
        "zh-TW": "GPDb 堅持絕對純粹的無網路追蹤設計。所有使用統計資料完全儲存在手機私有沙盒內，絕無任何第三方埋點探針或遠端數據分析，隨時可在設定中關閉或一鍵清空。",
        "en": "GPDb adheres to a zero-network-tracking principle. All usage metrics are strictly stored within your device sandbox without third-party telemetry, and can be disabled or purged anytime in Settings.",
        "ja": "GPDbは追跡ゼロの設計を維持しています。すべての統計情報は端末のプライベートサンドボックスにのみ保存され、設定からいつでも無効化または消去できます。",
        "it": "GPDb garantisce zero tracciamento di rete. Tutte le metriche rimangono nella sandbox locale del dispositivo e possono essere eliminate in qualsiasi momento.",
        "es": "GPDb no realiza ningún rastreo de red. Todas las estadísticas se almacenan estrictamente en la memoria del dispositivo y pueden eliminarse en cualquier momento.",
        "de": "GPDb verfolgt ein striktes Zero-Tracking-Prinzip. Alle Nutzungsdaten bleiben in der lokalen Sandbox Ihres Geräts und können jederzeit gelöscht werden."
    },
    "update.newVersionFound": {
        "zh-CN": "发现新版本 {version}", "zh-TW": "發現新版本 {version}", "en": "New version available: {version}",
        "ja": "新しいバージョンが見つかりました: {version}", "it": "Nuova versione disponibile: {version}", "es": "Nueva versión disponible: {version}", "de": "Neue Version verfügbar: {version}"
    },
    "update.currentVersionLabel": {
        "zh-CN": "当前版本: v{version}", "zh-TW": "目前版本: v{version}", "en": "Current: v{version}",
        "ja": "現在のバージョン: v{version}", "it": "Versione attuale: v{version}", "es": "Versión actual: v{version}", "de": "Aktuelle Version: v{version}"
    },
    "update.packageSize": {
        "zh-CN": "体积: {size}", "zh-TW": "體積: {size}", "en": "Size: {size}",
        "ja": "サイズ: {size}", "it": "Dimensione: {size}", "es": "Tamaño: {size}", "de": "Größe: {size}"
    },
    "update.releaseNotesTitle": {
        "zh-CN": "更新说明：", "zh-TW": "更新說明：", "en": "Release Notes:",
        "ja": "更新内容：", "it": "Note di rilascio:", "es": "Notas de la versión:", "de": "Versionshinweise:"
    },
    "update.downloadProgress": {
        "zh-CN": "正在下载更新包... {progress}%", "zh-TW": "正在下載更新包... {progress}%", "en": "Downloading update package... {progress}%",
        "ja": "アップデートをダウンロード中... {progress}%", "it": "Download del pacchetto di aggiornamento... {progress}%", "es": "Descargando paquete de actualización... {progress}%", "de": "Update-Paket wird heruntergeladen... {progress}%"
    },
    "update.downloadComplete": {
        "zh-CN": "下载完成，正在唤起安装...", "zh-TW": "下載完成，正在喚起安裝...", "en": "Download complete, launching installer...",
        "ja": "ダウンロード完了。インストーラーを起動中...", "it": "Download completato, avvio dell installazione...", "es": "Descarga completada, iniciando el instalador...", "de": "Download abgeschlossen, Installationsprogramm wird gestartet..."
    },
    "update.updateNowBtn": {
        "zh-CN": "立即更新", "zh-TW": "立即更新", "en": "Update Now",
        "ja": "今すぐ更新", "it": "Aggiorna ora", "es": "Actualizar ahora", "de": "Jetzt aktualisieren"
    },
    "update.postponeBtn": {
        "zh-CN": "稍后再说", "zh-TW": "稍後再說", "en": "Later",
        "ja": "後で", "it": "Più tardi", "es": "Más tarde", "de": "Später"
    },
    "share.episodeCardHeader": {
        "zh-CN": "GPDb · 场景分集档案", "zh-TW": "GPDb · 場景分集檔案", "en": "GPDb · Scene & Episode Profile",
        "ja": "GPDb · シーン＆エピソードプロファイル", "it": "GPDb · Profilo scena ed episodio", "es": "GPDb · Perfil de escena y episodio", "de": "GPDb · Szenen- & Episodenprofil"
    },
    "share.movieCardHeader": {
        "zh-CN": "GPDb · 影视档案", "zh-TW": "GPDb · 影視檔案", "en": "GPDb · Cinematic Archive",
        "ja": "GPDb · シネマアーカイブ", "it": "GPDb · Archivio cinematografico", "es": "GPDb · Archivo cinematográfico", "de": "GPDb · Filmarchiv"
    },
    "share.savedToGallery": {
        "zh-CN": "已成功保存分享卡片至系统相册", "zh-TW": "已成功儲存分享卡片至系統相簿", "en": "Share card saved to system gallery",
        "ja": "共有カードをギャラリーに保存しました", "it": "Scheda salvata nella galleria del sistema", "es": "Tarjeta guardada en la galería del sistema", "de": "Freigabekarte in Systemgalerie gespeichert"
    },
    "share.saveFailed": {
        "zh-CN": "保存卡片失败", "zh-TW": "儲存卡片失敗", "en": "Failed to save share card",
        "ja": "共有カードの保存に失敗しました", "it": "Impossibile salvare la scheda", "es": "Error al guardar la tarjeta", "de": "Speichern der Freigabekarte fehlgeschlagen"
    },
    "share.shareSubject": {
        "zh-CN": "《{title}》- 分享自 GPDb 个人数字影库", "zh-TW": "《{title}》- 分享自 GPDb 個人數位影庫", "en": "\"{title}\" - Shared from GPDb Personal Digital Archive",
        "ja": "『{title}』- GPDb 個人シネマアーカイブより共有", "it": "\"{title}\" - Condiviso da GPDb Archivio Digitale Personale", "es": "\"{title}\" - Compartido desde GPDb Archivo Digital Personal", "de": "\"{title}\" - Geteilt aus dem persönlichen GPDb Filmarchiv"
    },
    "share.chooserTitle": {
        "zh-CN": "分享电影档案卡片", "zh-TW": "分享電影檔案卡片", "en": "Share Movie Profile Card",
        "ja": "映画プロファイルカードを共有", "it": "Condividi scheda del profilo del film", "es": "Compartir tarjeta de perfil de película", "de": "Filmprofilkarte teilen"
    },
    "share.shareFailed": {
        "zh-CN": "调起分享失败", "zh-TW": "叫起分享失敗", "en": "Failed to launch share dialog",
        "ja": "共有の起動に失敗しました", "it": "Impossibile avviare la condivisione", "es": "Error al iniciar el diálogo de compartir", "de": "Freigabe konnte nicht gestartet werden"
    },
    "zoom.viewFullscreen": {
        "zh-CN": "全屏查看", "zh-TW": "全螢幕檢視", "en": "View Fullscreen",
        "ja": "全画面表示", "it": "Visualizza a schermo intero", "es": "Ver en pantalla completa", "de": "Vollbildansicht"
    }
}

for loc in locales:
    fpath = os.path.join(base_dir, f"{loc}.ts")
    with open(fpath, "r", encoding="utf-8") as f:
        content = f.read()

    last_brace_idx = content.rfind("}")
    if last_brace_idx == -1:
        print(f"Error: no closing brace in {fpath}")
        continue

    insert_lines = []
    for key, trans_dict in sorted(batch_additions.items()):
        val = trans_dict[loc]
        escaped_val = val.replace("'", "\\'")
        if f"'{key}':" in content:
            continue
        insert_lines.append(f"  '{key}': '{escaped_val}',")

    if insert_lines:
        new_content = content[:last_brace_idx].rstrip() + "\n" + "\n".join(insert_lines) + "\n};\n"
        with open(fpath, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"Updated {loc}.ts with {len(insert_lines)} new keys.")
    else:
        print(f"No new keys to add for {loc}.ts")
