package com.gpdb.android.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.gpdb.android.util.locales.*
import java.util.Locale

enum class AppLanguage(val code: String, val i18nKey: String, val defaultDisplayName: String, val nativeName: String) {
    SYSTEM("system", "lang.system", "跟随系统", "Follow System"),
    ZH_CN("zh-CN", "lang.zhCN", "简体中文", "简体中文"),
    ZH_TW("zh-TW", "lang.zhTW", "繁体中文", "繁體中文"),
    EN("en", "lang.en", "英语", "English"),
    JA("ja", "lang.ja", "日语", "日本語"),
    IT("it", "lang.it", "意大利语", "Italiano"),
    ES("es", "lang.es", "西班牙语", "Español"),
    DE("de", "lang.de", "德语", "Deutsch");

    val isChinese: Boolean
        get() = this == ZH_CN || this == ZH_TW

    @Composable
    fun getDisplayName(): String = I18n.string(i18nKey, defaultVal = defaultDisplayName)

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

    private val dictionaries: Map<AppLanguage, Map<String, String>> = mapOf(
        AppLanguage.ZH_CN to LocaleZhCn.translations,
        AppLanguage.ZH_TW to LocaleZhTw.translations,
        AppLanguage.EN to LocaleEn.translations,
        AppLanguage.JA to LocaleJa.translations,
        AppLanguage.IT to LocaleIt.translations,
        AppLanguage.ES to LocaleEs.translations,
        AppLanguage.DE to LocaleDe.translations
    )

    fun t(
        key: String,
        language: AppLanguage = AppLanguage.ZH_CN,
        params: Map<String, Any>? = null,
        defaultVal: String? = null
    ): String {
        val effectiveLang = if (language == AppLanguage.SYSTEM) AppLanguage.resolveEffective(language) else language
        val dict = dictionaries[effectiveLang]

        // Intelligent Fallback Chain:
        // 1. Target locale
        var text = dict?.get(key)

        // 2. English (for non-Chinese locales to prevent jarring fallback to Chinese)
        if (text == null && effectiveLang != AppLanguage.EN && !effectiveLang.isChinese) {
            text = dictionaries[AppLanguage.EN]?.get(key)
        }

        // 3. Simplified Chinese (root development language)
        if (text == null) {
            text = dictionaries[AppLanguage.ZH_CN]?.get(key)
        }

        // 4. Default value / Key name
        var result = text ?: defaultVal ?: key

        // 5. Interpolate {param}
        if (params != null && params.isNotEmpty()) {
            for ((paramKey, paramValue) in params) {
                result = result.replace("{$paramKey}", paramValue.toString())
            }
        }

        return result
    }

    fun t(
        key: String,
        params: Map<String, Any>?,
        defaultVal: String? = null
    ): String {
        return t(key, AppLanguage.ZH_CN, params, defaultVal)
    }

    @Composable
    fun string(
        key: String,
        params: Map<String, Any>? = null,
        defaultVal: String? = null
    ): String {
        val currentLang = LocalAppLanguage.current
        return t(key, currentLang, params, defaultVal)
    }

    @Composable
    fun string(
        key: String,
        defaultVal: String
    ): String {
        val currentLang = LocalAppLanguage.current
        return t(key, currentLang, null, defaultVal)
    }
}
