package com.gpdb.android.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    SYSTEM("system", "跟随系统", "Follow System"),
    ZH_CN("zh-CN", "简体中文", "简体中文"),
    ZH_TW("zh-TW", "繁体中文", "繁體中文"),
    EN("en", "英语", "English"),
    JA("ja", "日语", "日本語"),
    IT("it", "意大利语", "Italiano"),
    ES("es", "西班牙语", "Español"),
    DE("de", "德语", "Deutsch");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
        }

        fun resolveEffective(preference: AppLanguage): AppLanguage {
            if (preference != SYSTEM) return preference
            val tag = Locale.getDefault().toLanguageTag().lowercase()
            return when {
                tag.startsWith("zh-tw") || tag.startsWith("zh-hk") || tag.startsWith("zh-hant") -> ZH_TW
                tag.startsWith("zh") -> ZH_CN
                tag.startsWith("ja") -> JA
                tag.startsWith("it") -> IT
                tag.startsWith("es") -> ES
                tag.startsWith("de") -> DE
                tag.startsWith("en") -> EN
                else -> ZH_CN
            }
        }
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.ZH_CN }

object I18n {

    private val translations: Map<AppLanguage, Map<String, String>> = mapOf(
        AppLanguage.ZH_CN to mapOf(
            "nav.home" to "主页",
            "nav.movies" to "影片",
            "nav.featureMovies" to "长片",
            "nav.episodes" to "分集",
            "nav.series" to "系列",
            "nav.performers" to "演员",
            "nav.studios" to "片商",
            "nav.directors" to "导演",
            "nav.library" to "我的收藏",
            "nav.settings" to "设置",
            "nav.analytics" to "使用统计",

            "common.search" to "检索影片、演员、分集...",
            "common.filter" to "筛选",
            "common.all" to "全部",
            "common.save" to "保存",
            "common.cancel" to "取消",
            "common.confirm" to "确认",
            "common.delete" to "删除",
            "common.edit" to "修改",
            "common.close" to "关闭",
            "common.loading" to "加载中...",
            "common.noData" to "暂无相关数据",
            "common.refresh" to "刷新",
            "common.back" to "返回",
            "common.works" to "部作品",
            "common.episodesCount" to "个分集",

            "filter.all" to "全部",
            "filter.lastScraped" to "上次入库",
            "filter.recent7" to "最近7天",
            "filter.recent30" to "最近30天",
            "filter.recent90" to "最近90天",
            "filter.recentYear" to "最近一年",

            "home.spotlight" to "镇馆之选 · 焦点典藏",
            "home.onThisDay" to "往年今日 · 经典首映",
            "home.starSpotlight" to "今日星光 · 标志面孔",
            "home.seriesShowcase" to "经典系列大放送 · 连贯篇章",
            "home.luckyDiscovery" to "随心探索 · 盲盒发现",
            "home.luckyShuffle" to "换一批",
            "home.exploreNow" to "立即探索",

            "settings.title" to "设置中心",
            "settings.appearance" to "外观与显示",
            "settings.theme" to "外观主题",
            "settings.themeDesc" to "提供经典、流体玻璃与 Material You 多套深浅配色",
            "settings.language" to "界面语言",
            "settings.languageDesc" to "切换应用交互语言，即时生效",
            "settings.appIcon" to "应用图标与伪装",
            "settings.posterMode" to "海报展示模式",
            "settings.dynamicColor" to "动态取色 (Material You)",

            "settings.privacy" to "隐私与安全",
            "settings.privacyDesc" to "多任务防窥、应用安全锁与沙盒隔离保护",
            "settings.flagSecure" to "多任务防窥与防截屏 (FLAG_SECURE)",
            "settings.appLock" to "应用安全锁 (PIN / 生物识别)",
            "settings.panicSwitch" to "紧急一键脱身 (Panic Switch)",
            "settings.sandbox" to "沙盒存储与防扫描保护",

            "settings.translation" to "AI 翻译与同步",
            "settings.translationDesc" to "配置大模型直白翻译引擎及后台增量同步",
            "settings.sync" to "增量同步与图库缓存",

            "settings.storage" to "数据与存储",
            "settings.analytics" to "本地使用统计",
            "settings.analyticsDesc" to "忠实记录本地探索时长、浏览足迹与夜猫子指数",

            "analytics.title" to "本地使用数据统计",
            "analytics.subtitle" to "所有数据仅保存在当前设备本地，永不上报任何云端服务器",
            "analytics.totalTime" to "累计探索时长",
            "analytics.moviesExplored" to "累计探索影片",
            "analytics.episodesViewed" to "累计鉴赏分集",
            "analytics.performersKnown" to "累计了解演员",
            "analytics.searchesCount" to "全站检索频次",
            "analytics.nightOwl" to "夜猫子深夜探索",
            "analytics.activeDays" to "累计活跃天数",
            "analytics.translationsCount" to "AI 翻译累计量",
            "analytics.clear" to "清空统计数据",
            "analytics.clearConfirm" to "确认清空所有累计使用统计数据吗？此操作不可撤销。"
        ),

        AppLanguage.ZH_TW to mapOf(
            "nav.home" to "首頁",
            "nav.movies" to "影片",
            "nav.featureMovies" to "長片",
            "nav.episodes" to "分集",
            "nav.series" to "系列",
            "nav.performers" to "演員",
            "nav.studios" to "片商",
            "nav.directors" to "導演",
            "nav.library" to "我的收藏",
            "nav.settings" to "設定",
            "nav.analytics" to "使用統計",

            "common.search" to "檢索影片、演員、分集...",
            "common.filter" to "篩選",
            "common.all" to "全部",
            "common.save" to "儲存",
            "common.cancel" to "取消",
            "common.confirm" to "確認",
            "common.delete" to "刪除",
            "common.edit" to "修改",
            "common.close" to "關閉",
            "common.loading" to "載入中...",
            "common.noData" to "暫無相關數據",
            "common.refresh" to "重新整理",
            "common.back" to "返回",
            "common.works" to "部作品",
            "common.episodesCount" to "個分集",

            "filter.all" to "全部",
            "filter.lastScraped" to "上次入庫",
            "filter.recent7" to "最近7天",
            "filter.recent30" to "最近30天",
            "filter.recent90" to "最近90天",
            "filter.recentYear" to "最近一年",

            "home.spotlight" to "鎮館之選 · 焦點典藏",
            "home.onThisDay" to "往年今日 · 經典首映",
            "home.starSpotlight" to "今日星光 · 標誌面孔",
            "home.seriesShowcase" to "經典系列大放送 · 連貫篇章",
            "home.luckyDiscovery" to "隨心探索 · 盲盒發現",
            "home.luckyShuffle" to "換一批",
            "home.exploreNow" to "立即探索",

            "settings.title" to "設定中心",
            "settings.appearance" to "外觀與顯示",
            "settings.theme" to "外觀主題",
            "settings.themeDesc" to "提供經典、流體玻璃與 Material You 多套深淺配色",
            "settings.language" to "介面語言",
            "settings.languageDesc" to "切換應用程式互動語言，即時生效",
            "settings.appIcon" to "應用程式圖示與偽裝",
            "settings.posterMode" to "海報展示模式",
            "settings.dynamicColor" to "動態取色 (Material You)",

            "settings.privacy" to "隱私與安全",
            "settings.privacyDesc" to "多工防窺、應用安全鎖與沙盒隔離保護",
            "settings.flagSecure" to "多工防窺與防截圖 (FLAG_SECURE)",
            "settings.appLock" to "應用安全鎖 (PIN / 生物識別)",
            "settings.panicSwitch" to "緊急一鍵脫身 (Panic Switch)",
            "settings.sandbox" to "沙盒儲存與防掃描保護",

            "settings.translation" to "AI 翻譯與同步",
            "settings.translationDesc" to "設定大模型直白翻譯引擎及背景增量同步",
            "settings.sync" to "增量同步與圖庫快取",

            "settings.storage" to "數據與儲存",
            "settings.analytics" to "本地使用統計",
            "settings.analyticsDesc" to "忠實記錄本地探索時長、瀏覽足跡與夜貓子指數",

            "analytics.title" to "本地使用數據統計",
            "analytics.subtitle" to "所有數據僅儲存於本機設備，絕不上報任何雲端伺服器",
            "analytics.totalTime" to "累計探索時長",
            "analytics.moviesExplored" to "累計探索影片",
            "analytics.episodesViewed" to "累計鑑賞分集",
            "analytics.performersKnown" to "累計了解演員",
            "analytics.searchesCount" to "全站檢索頻次",
            "analytics.nightOwl" to "夜貓子深夜探索",
            "analytics.activeDays" to "累計活躍天數",
            "analytics.translationsCount" to "AI 翻譯累計量",
            "analytics.clear" to "清空統計數據",
            "analytics.clearConfirm" to "確認清空所有累計使用統計數據嗎？此操作不可撤銷。"
        ),

        AppLanguage.EN to mapOf(
            "nav.home" to "Home",
            "nav.movies" to "Movies",
            "nav.featureMovies" to "Features",
            "nav.episodes" to "Episodes",
            "nav.series" to "Series",
            "nav.performers" to "Performers",
            "nav.studios" to "Studios",
            "nav.directors" to "Directors",
            "nav.library" to "Favorites",
            "nav.settings" to "Settings",
            "nav.analytics" to "Analytics",

            "common.search" to "Search movies, performers, episodes...",
            "common.filter" to "Filter",
            "common.all" to "All",
            "common.save" to "Save",
            "common.cancel" to "Cancel",
            "common.confirm" to "Confirm",
            "common.delete" to "Delete",
            "common.edit" to "Edit",
            "common.close" to "Close",
            "common.loading" to "Loading...",
            "common.noData" to "No matching data",
            "common.refresh" to "Refresh",
            "common.back" to "Back",
            "common.works" to "works",
            "common.episodesCount" to "episodes",

            "filter.all" to "All",
            "filter.lastScraped" to "Last Scraped",
            "filter.recent7" to "Last 7 Days",
            "filter.recent30" to "Last 30 Days",
            "filter.recent90" to "Last 90 Days",
            "filter.recentYear" to "Last Year",

            "home.spotlight" to "Spotlight Carousel",
            "home.onThisDay" to "On This Day in History",
            "home.starSpotlight" to "Star Spotlight",
            "home.seriesShowcase" to "Classic Series Showcase",
            "home.luckyDiscovery" to "Lucky Discovery",
            "home.luckyShuffle" to "Shuffle",
            "home.exploreNow" to "Explore Now",

            "settings.title" to "Settings Center",
            "settings.appearance" to "Appearance & Display",
            "settings.theme" to "Theme Palette",
            "settings.themeDesc" to "Classic, Liquid Glass and Material You palettes in light & dark",
            "settings.language" to "Display Language",
            "settings.languageDesc" to "Switch UI language with instant preview",
            "settings.appIcon" to "App Icon & Alias Disguise",
            "settings.posterMode" to "Poster Display Mode",
            "settings.dynamicColor" to "Dynamic Color (Material You)",

            "settings.privacy" to "Privacy & Security",
            "settings.privacyDesc" to "FLAG_SECURE anti-peek, app lock, and storage sandboxing",
            "settings.flagSecure" to "Anti-Peek & Anti-Screenshot (FLAG_SECURE)",
            "settings.appLock" to "App Lock (PIN / Biometrics)",
            "settings.panicSwitch" to "Panic Switch (Flip / Shake)",
            "settings.sandbox" to "Sandbox Storage Isolation",

            "settings.translation" to "AI Translation & Sync",
            "settings.translationDesc" to "Configure LLM translation engine and background sync",
            "settings.sync" to "Incremental Sync & Image Cache",

            "settings.storage" to "Data & Storage",
            "settings.analytics" to "Usage Analytics",
            "settings.analyticsDesc" to "Local exploration time, view history, and night-owl stats",

            "analytics.title" to "Local Usage Analytics",
            "analytics.subtitle" to "All analytics stored locally on device; never sent to any server",
            "analytics.totalTime" to "Total Exploration Time",
            "analytics.moviesExplored" to "Movies Explored",
            "analytics.episodesViewed" to "Episodes Viewed",
            "analytics.performersKnown" to "Performers Known",
            "analytics.searchesCount" to "Search Queries",
            "analytics.nightOwl" to "Night Owl Explorations",
            "analytics.activeDays" to "Active Days",
            "analytics.translationsCount" to "AI Translations",
            "analytics.clear" to "Clear Analytics",
            "analytics.clearConfirm" to "Are you sure you want to clear all analytics data? This action cannot be undone."
        ),

        AppLanguage.JA to mapOf(
            "nav.home" to "ホーム",
            "nav.movies" to "作品",
            "nav.featureMovies" to "長編",
            "nav.episodes" to "エピソード",
            "nav.series" to "シリーズ",
            "nav.performers" to "俳優",
            "nav.studios" to "スタジオ",
            "nav.directors" to "監督",
            "nav.library" to "お気に入り",
            "nav.settings" to "設定",
            "nav.analytics" to "使用統計",
            "common.search" to "作品、俳優、シーンを検索...",
            "common.filter" to "絞り込み",
            "common.all" to "すべて",
            "common.save" to "保存",
            "common.cancel" to "キャンセル",
            "common.confirm" to "確認",
            "common.refresh" to "更新",
            "home.spotlight" to "注目の傑作",
            "home.luckyShuffle" to "シャッフル",
            "settings.title" to "設定",
            "settings.appearance" to "外観と表示",
            "settings.language" to "言語設定",
            "settings.privacy" to "プライバシーとセキュリティ",
            "analytics.title" to "ローカル使用状況統計"
        ),

        AppLanguage.IT to mapOf(
            "nav.home" to "Home",
            "nav.movies" to "Film",
            "nav.featureMovies" to "Lungometraggi",
            "nav.episodes" to "Episodi",
            "nav.series" to "Serie",
            "nav.performers" to "Attori",
            "nav.studios" to "Studi",
            "nav.directors" to "Registi",
            "nav.library" to "I Miei Preferiti",
            "nav.settings" to "Impostazioni",
            "nav.analytics" to "Statistiche",
            "common.search" to "Cerca film, attori, episodi...",
            "common.filter" to "Filtra",
            "common.all" to "Tutti",
            "common.save" to "Salva",
            "common.cancel" to "Annulla",
            "common.confirm" to "Conferma",
            "common.refresh" to "Aggiorna",
            "settings.title" to "Impostazioni",
            "settings.appearance" to "Aspetto e Tema",
            "settings.language" to "Lingua",
            "settings.privacy" to "Privacy e Sicurezza",
            "analytics.title" to "Statistiche di Utilizzo"
        ),

        AppLanguage.ES to mapOf(
            "nav.home" to "Inicio",
            "nav.movies" to "Películas",
            "nav.featureMovies" to "Largometrajes",
            "nav.episodes" to "Episodios",
            "nav.series" to "Series",
            "nav.performers" to "Actores",
            "nav.studios" to "Estudios",
            "nav.directors" to "Directores",
            "nav.library" to "Mis Favoritos",
            "nav.settings" to "Ajustes",
            "nav.analytics" to "Estadísticas",
            "common.search" to "Buscar películas, actores...",
            "common.filter" to "Filtrar",
            "common.all" to "Todo",
            "common.save" to "Guardar",
            "common.cancel" to "Cancelar",
            "common.confirm" to "Confirmar",
            "common.refresh" to "Actualizar",
            "settings.title" to "Ajustes",
            "settings.appearance" to "Apariencia",
            "settings.language" to "Idioma",
            "settings.privacy" to "Privacidad y Seguridad",
            "analytics.title" to "Estadísticas de Uso"
        ),

        AppLanguage.DE to mapOf(
            "nav.home" to "Startseite",
            "nav.movies" to "Filme",
            "nav.featureMovies" to "Spielfilme",
            "nav.episodes" to "Episoden",
            "nav.series" to "Serien",
            "nav.performers" to "Darsteller",
            "nav.studios" to "Studios",
            "nav.directors" to "Regisseure",
            "nav.library" to "Meine Favoriten",
            "nav.settings" to "Einstellungen",
            "nav.analytics" to "Statistiken",
            "common.search" to "Filme, Darsteller suchen...",
            "common.filter" to "Filter",
            "common.all" to "Alle",
            "common.save" to "Speichern",
            "common.cancel" to "Abbrechen",
            "common.confirm" to "Bestätigen",
            "common.refresh" to "Aktualisieren",
            "settings.title" to "Einstellungen",
            "settings.appearance" to "Design & Anzeige",
            "settings.language" to "Sprache",
            "settings.privacy" to "Datenschutz & Sicherheit",
            "analytics.title" to "Nutzungsstatistiken"
        )
    )

    fun t(key: String, language: AppLanguage = AppLanguage.ZH_CN): String {
        val langDict = translations[language] ?: translations[AppLanguage.ZH_CN]!!
        return langDict[key] ?: translations[AppLanguage.ZH_CN]?.get(key) ?: key
    }

    @Composable
    fun string(key: String): String {
        val currentLang = LocalAppLanguage.current
        return t(key, currentLang)
    }
}
