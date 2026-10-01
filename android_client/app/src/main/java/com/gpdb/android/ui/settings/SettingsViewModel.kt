package com.gpdb.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.preferences.AppPreferences
import com.gpdb.android.data.preferences.MountPreferences
import com.gpdb.android.data.repository.BrowseRepository
import com.gpdb.android.data.settings.AppSettingsRepository
import com.gpdb.android.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val appPreferences: AppPreferences,
    private val appSettingsRepository: AppSettingsRepository,
    private val mountPreferences: MountPreferences? = null
) : ViewModel() {
    val language = appPreferences.languageFlow.stateIn(viewModelScope, SharingStarted.Lazily, "system")
    val recordHistory = appPreferences.recordSearchHistoryFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    
    val themeChoice = appSettingsRepository.themeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "auto")
    val themeMode = appSettingsRepository.themeModeFlow.stateIn(viewModelScope, SharingStarted.Lazily, ThemeMode.SYSTEM)
    val dynamicColor = appSettingsRepository.dynamicColorFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val llmApiKey = appSettingsRepository.llmApiKeyFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val llmBaseUrl = appSettingsRepository.llmBaseUrlFlow.stateIn(viewModelScope, SharingStarted.Lazily, "https://api.openai.com/v1")
    val llmModel = appSettingsRepository.llmModelFlow.stateIn(viewModelScope, SharingStarted.Lazily, "gpt-4o-mini")
    
    val llmProvider = appSettingsRepository.llmProviderFlow.stateIn(viewModelScope, SharingStarted.Lazily, "OpenAI")
    val llmAutoTranslate = appSettingsRepository.llmAutoTranslateFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val llmTargetLanguage = appSettingsRepository.llmTargetLanguageFlow.stateIn(viewModelScope, SharingStarted.Lazily, "简体中文")
    val llmSystemPrompt = appSettingsRepository.llmSystemPromptFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    
    val appIcon = appPreferences.appIconFlow.stateIn(viewModelScope, SharingStarted.Lazily, "B")
    val periodicSyncEnabled = appSettingsRepository.periodicSyncEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)

    val posterDisplayMode = appSettingsRepository.posterDisplayModeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "adaptive_pager")
    val saveImagesToExternal = appSettingsRepository.saveImagesToExternalFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val flagSecureEnabled = appSettingsRepository.flagSecureEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val appLockEnabled = appSettingsRepository.appLockEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val appLockPin = appSettingsRepository.appLockPinFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val appLockBiometricEnabled = appSettingsRepository.appLockBiometricEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val appLockTimeoutSeconds = appSettingsRepository.appLockTimeoutSecondsFlow.stateIn(viewModelScope, SharingStarted.Lazily, 30)

    val panicSwitchEnabled = appSettingsRepository.panicSwitchEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val panicFaceDownEnabled = appSettingsRepository.panicFaceDownEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val panicShakeEnabled = appSettingsRepository.panicShakeEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val panicAction = appSettingsRepository.panicActionFlow.stateIn(viewModelScope, SharingStarted.Lazily, "CALCULATOR")

    val screenshotPrivacyBlurEnabled = appSettingsRepository.screenshotPrivacyBlurEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val screenshotPrivacyBlurImages = appSettingsRepository.screenshotPrivacyBlurImagesFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val screenshotPrivacyBlurText = appSettingsRepository.screenshotPrivacyBlurTextFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)

    private val _translatableMovies = MutableStateFlow(0)
    val translatableMovies: StateFlow<Int> = _translatableMovies
    
    private val _translatedMovies = MutableStateFlow(0)
    val translatedMovies: StateFlow<Int> = _translatedMovies

    private val _translatableEpisodes = MutableStateFlow(0)
    val translatableEpisodes: StateFlow<Int> = _translatableEpisodes

    private val _translatedEpisodes = MutableStateFlow(0)
    val translatedEpisodes: StateFlow<Int> = _translatedEpisodes

    init {
        loadTranslationStats()
    }

    fun refreshStats() {
        loadTranslationStats()
    }

    private fun loadTranslationStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DatabaseHolder.db?.browseDao()?.let { dao ->
                    val repo = BrowseRepository(dao)
                    _translatableMovies.value = repo.getTotalTranslatableMovies()
                    _translatedMovies.value = repo.getTranslatedMovies()
                    _translatableEpisodes.value = repo.getTotalTranslatableEpisodes()
                    _translatedEpisodes.value = repo.getTranslatedEpisodes()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setLanguage(lang: String) = viewModelScope.launch { appPreferences.setLanguage(lang) }
    fun setRecordHistory(record: Boolean) = viewModelScope.launch { appPreferences.setRecordSearchHistory(record) }
    fun setTheme(themeId: String) = viewModelScope.launch { appSettingsRepository.setTheme(themeId) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { appSettingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { appSettingsRepository.setDynamicColor(enabled) }
    fun setLlmConfig(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        autoTranslate: Boolean,
        targetLanguage: String,
        systemPrompt: String
    ) = viewModelScope.launch {
        appSettingsRepository.setLlmConfig(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
            autoTranslate = autoTranslate,
            targetLanguage = targetLanguage,
            systemPrompt = systemPrompt
        )
    }
    fun setAppIcon(icon: String) = viewModelScope.launch { appPreferences.setAppIcon(icon) }

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Checking : SyncStatus()
        data class Discovered(val updates: com.gpdb.android.data.scraper.DiscoveredUpdates) : SyncStatus()
        data class Syncing(val current: Int, val total: Int, val currentItem: String) : SyncStatus()
        data class Completed(val result: com.gpdb.android.data.scraper.SyncResult) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    fun setPeriodicSyncEnabled(enabled: Boolean, context: android.content.Context) {
        viewModelScope.launch {
            appSettingsRepository.setPeriodicSyncEnabled(enabled)
            com.gpdb.android.data.scraper.GpdbSyncWorker.schedulePeriodicSync(context, enabled)
        }
    }

    fun checkForUpdates(context: android.content.Context) {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.Checking
            try {
                val db = DatabaseHolder.db
                if (db == null) {
                    _syncStatus.value = SyncStatus.Error("数据库尚未挂载或连接不可用")
                    return@launch
                }
                val physicalRoot = mountPreferences?.mountRootFlow?.first() ?: ""
                val engine = com.gpdb.android.data.scraper.GpdbScraperEngine(context, db, physicalRoot)
                val updates = engine.checkForUpdates()
                _syncStatus.value = SyncStatus.Discovered(updates)
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("检查官方更新失败: ${e.message}")
            }
        }
    }

    fun startSync(context: android.content.Context, updates: com.gpdb.android.data.scraper.DiscoveredUpdates) {
        viewModelScope.launch {
            try {
                val db = DatabaseHolder.db
                if (db == null) {
                    _syncStatus.value = SyncStatus.Error("数据库未连接")
                    return@launch
                }
                val physicalRoot = mountPreferences?.mountRootFlow?.first() ?: ""
                val engine = com.gpdb.android.data.scraper.GpdbScraperEngine(context, db, physicalRoot)
                _syncStatus.value = SyncStatus.Syncing(0, updates.totalCount, "准备中...")
                val result = engine.syncUpdates(updates) { current, total, name ->
                    _syncStatus.value = SyncStatus.Syncing(current, total, name)
                }
                _syncStatus.value = SyncStatus.Completed(result)
                refreshStats()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("同步中断: ${e.message}")
            }
        }
    }

    fun resetSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }

    fun setPosterDisplayMode(mode: String) {
        viewModelScope.launch {
            appSettingsRepository.setPosterDisplayMode(mode)
        }
    }

    fun setSaveImagesToExternal(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setSaveImagesToExternal(enabled)
            if (enabled && mountPreferences != null) {
                val root = mountPreferences.mountRootFlow.first()
                if (!root.isNullOrBlank()) {
                    com.gpdb.android.util.PrivacyHelper.ensureNoMedia(java.io.File(root))
                }
            }
        }
    }

    fun enforceExternalNoMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            if (mountPreferences != null) {
                val root = mountPreferences.mountRootFlow.first()
                if (!root.isNullOrBlank()) {
                    com.gpdb.android.util.PrivacyHelper.ensureNoMedia(java.io.File(root))
                }
            }
        }
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setFlagSecureEnabled(enabled)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setAppLockEnabled(enabled)
        }
    }

    fun setAppLockPin(pin: String) {
        viewModelScope.launch {
            appSettingsRepository.setAppLockPin(pin)
        }
    }

    fun setAppLockBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setAppLockBiometricEnabled(enabled)
        }
    }

    fun setAppLockTimeoutSeconds(seconds: Int) {
        viewModelScope.launch {
            appSettingsRepository.setAppLockTimeoutSeconds(seconds)
        }
    }

    fun setPanicSwitchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setPanicSwitchEnabled(enabled)
        }
    }

    fun setPanicFaceDownEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setPanicFaceDownEnabled(enabled)
        }
    }

    fun setPanicShakeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setPanicShakeEnabled(enabled)
        }
    }

    fun setPanicAction(action: String) {
        viewModelScope.launch {
            appSettingsRepository.setPanicAction(action)
        }
    }

    fun setScreenshotPrivacyBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setScreenshotPrivacyBlurEnabled(enabled)
        }
    }

    fun setScreenshotPrivacyBlurImages(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setScreenshotPrivacyBlurImages(enabled)
        }
    }

    fun setScreenshotPrivacyBlurText(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setScreenshotPrivacyBlurText(enabled)
        }
    }

    fun exportUserDataToFile(context: Context, uri: android.net.Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = DatabaseHolder.db ?: throw IllegalStateException("数据库未挂载")
                val userRepo = com.gpdb.android.data.repository.UserRepository(db, db.userActionDao())
                val favList = userRepo.exportFavorites()
                val analyticsRepo = com.gpdb.android.data.analytics.UserAnalyticsRepository.getInstance(context)
                val analyticsMap = analyticsRepo.exportAnalyticsMap()

                val rootJson = org.json.JSONObject()
                rootJson.put("format", "gpdb_universal_backup")
                rootJson.put("version", 2)
                rootJson.put("app_version", "2.15.0")
                rootJson.put("platform", "android")
                rootJson.put("exported_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))

                val favArr = org.json.JSONArray()
                for (fav in favList) {
                    val obj = org.json.JSONObject()
                    obj.put("entity_type", fav.entity_type)
                    obj.put("entity_key", fav.entity_key)
                    if (fav.created_at != null) obj.put("created_at", fav.created_at)
                    favArr.put(obj)
                }
                rootJson.put("favorites", favArr)

                val analyticsObj = org.json.JSONObject()
                for ((k, v) in analyticsMap) {
                    analyticsObj.put(k, v)
                }
                rootJson.put("analytics", analyticsObj)

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                }

                withContext(Dispatchers.Main) {
                    onComplete(true, "导出成功：已备份 ${favList.size} 条收藏与全量统计数据！")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, "导出失败: ${e.message}")
                }
            }
        }
    }

    fun importUserDataFromFile(context: Context, uri: android.net.Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = DatabaseHolder.db ?: throw IllegalStateException("数据库未挂载")
                val jsonStr = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("无法读取文件内容")

                val rootJson = org.json.JSONObject(jsonStr)

                // 1. Import favorites
                var favImported = 0
                if (rootJson.has("favorites")) {
                    val favArr = rootJson.getJSONArray("favorites")
                    val favList = mutableListOf<com.gpdb.android.data.repository.FavoriteBackupItem>()
                    for (i in 0 until favArr.length()) {
                        val obj = favArr.getJSONObject(i)
                        val type = obj.optString("entity_type", "")
                        val key = obj.optString("entity_key", "")
                        val time = if (obj.has("created_at")) obj.getString("created_at") else null
                        if (type.isNotBlank() && key.isNotBlank()) {
                            favList.add(com.gpdb.android.data.repository.FavoriteBackupItem(type, key, time))
                        }
                    }
                    val userRepo = com.gpdb.android.data.repository.UserRepository(db, db.userActionDao())
                    favImported = userRepo.importFavorites(favList)
                }

                // 2. Import analytics
                if (rootJson.has("analytics")) {
                    val analyticsObj = rootJson.getJSONObject("analytics")
                    val analyticsMap = mutableMapOf<String, Any>()
                    val keys = analyticsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        analyticsMap[k] = analyticsObj.get(k)
                    }
                    val analyticsRepo = com.gpdb.android.data.analytics.UserAnalyticsRepository.getInstance(context)
                    analyticsRepo.importAnalyticsMap(analyticsMap)
                }

                withContext(Dispatchers.Main) {
                    onComplete(true, "导入成功：已恢复 $favImported 条收藏与用户统计时长！")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, "导入失败: ${e.message}")
                }
            }
        }
    }
}
