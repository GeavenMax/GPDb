package com.gpdb.android.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppSettingsRepository(private val context: Context) {
    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        
        val LLM_PROVIDER = stringPreferencesKey("llm_provider")
        val LLM_AUTO_TRANSLATE = booleanPreferencesKey("llm_auto_translate")
        val LLM_TARGET_LANGUAGE = stringPreferencesKey("llm_target_language")
        val LLM_SYSTEM_PROMPT = stringPreferencesKey("llm_system_prompt")
        
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_MODEL = stringPreferencesKey("llm_model")

        val PERIODIC_SYNC_ENABLED = booleanPreferencesKey("periodic_sync_enabled")
        val AUTO_SYNC_ON_LAUNCH = booleanPreferencesKey("auto_sync_on_launch")

        val SAVE_IMAGES_TO_EXTERNAL = booleanPreferencesKey("save_images_to_external") // default = false (strictly sandboxed)

        val FLAG_SECURE_ENABLED = booleanPreferencesKey("flag_secure_enabled") // default = false

        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled") // default = false
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin") // 4-6 digits, default = ""
        val APP_LOCK_BIOMETRIC_ENABLED = booleanPreferencesKey("app_lock_biometric_enabled") // default = true
        val APP_LOCK_TIMEOUT_SECONDS = intPreferencesKey("app_lock_timeout_seconds") // default = 30

        val PANIC_SWITCH_ENABLED = booleanPreferencesKey("panic_switch_enabled") // default = false
        val PANIC_TRIGGER_FACE_DOWN = booleanPreferencesKey("panic_trigger_face_down") // default = true
        val PANIC_TRIGGER_SHAKE = booleanPreferencesKey("panic_trigger_shake") // default = true
        val PANIC_ACTION = stringPreferencesKey("panic_action") // "CALCULATOR", "HOME", "KILL"

        val SCREENSHOT_PRIVACY_BLUR_ENABLED = booleanPreferencesKey("screenshot_privacy_blur_enabled") // default = false
        val SCREENSHOT_PRIVACY_BLUR_IMAGES = booleanPreferencesKey("screenshot_privacy_blur_images") // default = true
        val SCREENSHOT_PRIVACY_BLUR_TEXT = booleanPreferencesKey("screenshot_privacy_blur_text") // default = true
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "auto"
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[THEME_MODE] ?: "auto"
        when (raw.lowercase()) {
            "light", "classic-light", "glass-light", "my-light" -> ThemeMode.LIGHT
            "dark", "classic-dark", "glass-dark", "my-dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setTheme(themeId: String) {
        context.dataStore.edit { it[THEME_MODE] = themeId }
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR] ?: true
    }
    
    val llmApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LLM_API_KEY] ?: ""
    }
    
    val llmBaseUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LLM_BASE_URL] ?: "https://api.openai.com/v1"
    }
    
    val llmModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LLM_MODEL] ?: "gpt-4o-mini"
    }

    val llmProviderFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LLM_PROVIDER] ?: "OpenAI"
    }

    val llmAutoTranslateFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LLM_AUTO_TRANSLATE] ?: false
    }

    val llmTargetLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LLM_TARGET_LANGUAGE] ?: "简体中文"
    }

    val llmSystemPromptFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LLM_SYSTEM_PROMPT] ?: """你是一名成人影片资料库的专职译者。你会收到英文剧情简介、分集与专有名词，需要如实翻译成{Target_Language}。翻译风格（最重要，必须严格遵守）：
1. 忠于原文：逐句对应，原文说了什么就译什么，不增不减、不概括、不改写、不调整叙述顺序。
2. 非常直白：用词直接、露骨、不回避，按原文露骨程度如实翻译，不要替换成委婉语、暗示或模糊表述，绝不弱化、淡化、"净化"或美化原文内容。
3. 不要意译或润色：宁可保持原文的句式和语序，也不要为了文采而改变原意。
4. 人名、片名、厂牌名（Studio）一律保留原文拉丁字母写法，绝对不要音译成汉字（例：Count Vladimir 保持 "Count Vladimir"，不要写成 "弗拉基米尔伯爵"；Staxus 保持 "Staxus"）。
5. 保持档案记录语气：只陈述原文内容，不添加主观评价。涉及成人内容、身体部位、性取向时照实翻译，使用目标语言成人语境中通用的直接说法，不要因内容露骨而删减、跳过或含糊处理。"""
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }
    
    suspend fun setLlmConfig(
        provider: String,
        apiKey: String, 
        baseUrl: String, 
        model: String,
        autoTranslate: Boolean,
        targetLanguage: String,
        systemPrompt: String
    ) {
        context.dataStore.edit {
            it[LLM_PROVIDER] = provider
            it[LLM_API_KEY] = apiKey
            it[LLM_BASE_URL] = baseUrl
            it[LLM_MODEL] = model
            it[LLM_AUTO_TRANSLATE] = autoTranslate
            it[LLM_TARGET_LANGUAGE] = targetLanguage
            it[LLM_SYSTEM_PROMPT] = systemPrompt
        }
    }

    val periodicSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PERIODIC_SYNC_ENABLED] ?: true
    }

    suspend fun setPeriodicSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PERIODIC_SYNC_ENABLED] = enabled }
    }

    val autoSyncOnLaunchFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_SYNC_ON_LAUNCH] ?: true
    }

    suspend fun setAutoSyncOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_SYNC_ON_LAUNCH] = enabled }
    }

    val saveImagesToExternalFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SAVE_IMAGES_TO_EXTERNAL] ?: false
    }

    suspend fun setSaveImagesToExternal(enabled: Boolean) {
        context.dataStore.edit { it[SAVE_IMAGES_TO_EXTERNAL] = enabled }
    }

    // ── 1. FLAG_SECURE 防截屏与多任务防窥 ─────────────────────
    val flagSecureEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FLAG_SECURE_ENABLED] ?: false
    }

    suspend fun setFlagSecureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[FLAG_SECURE_ENABLED] = enabled }
    }

    // ── 2. 应用锁与生物识别 ───────────────────────────────────
    val appLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_ENABLED] ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    val appLockPinFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_PIN] ?: ""
    }

    suspend fun setAppLockPin(pin: String) {
        context.dataStore.edit { it[APP_LOCK_PIN] = pin }
    }

    val appLockBiometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_BIOMETRIC_ENABLED] ?: true
    }

    suspend fun setAppLockBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK_BIOMETRIC_ENABLED] = enabled }
    }

    val appLockTimeoutSecondsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_TIMEOUT_SECONDS] ?: 30
    }

    suspend fun setAppLockTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { it[APP_LOCK_TIMEOUT_SECONDS] = seconds }
    }

    // ── 3. 紧急脱身 (Panic Switch) ────────────────────────────
    val panicSwitchEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PANIC_SWITCH_ENABLED] ?: false
    }

    suspend fun setPanicSwitchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PANIC_SWITCH_ENABLED] = enabled }
    }

    val panicFaceDownEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PANIC_TRIGGER_FACE_DOWN] ?: true
    }

    suspend fun setPanicFaceDownEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PANIC_TRIGGER_FACE_DOWN] = enabled }
    }

    val panicShakeEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PANIC_TRIGGER_SHAKE] ?: true
    }

    suspend fun setPanicShakeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PANIC_TRIGGER_SHAKE] = enabled }
    }

    val panicActionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PANIC_ACTION] ?: "CALCULATOR"
    }

    suspend fun setPanicAction(action: String) {
        context.dataStore.edit { it[PANIC_ACTION] = action }
    }

    // ── 4. 截屏隐私打码模式 (Screenshot Privacy Blur) ───────────────
    val screenshotPrivacyBlurEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREENSHOT_PRIVACY_BLUR_ENABLED] ?: false
    }

    suspend fun setScreenshotPrivacyBlurEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SCREENSHOT_PRIVACY_BLUR_ENABLED] = enabled }
    }

    val screenshotPrivacyBlurImagesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREENSHOT_PRIVACY_BLUR_IMAGES] ?: true
    }

    suspend fun setScreenshotPrivacyBlurImages(enabled: Boolean) {
        context.dataStore.edit { it[SCREENSHOT_PRIVACY_BLUR_IMAGES] = enabled }
    }

    val screenshotPrivacyBlurTextFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREENSHOT_PRIVACY_BLUR_TEXT] ?: true
    }

    suspend fun setScreenshotPrivacyBlurText(enabled: Boolean) {
        context.dataStore.edit { it[SCREENSHOT_PRIVACY_BLUR_TEXT] = enabled }
    }
}
