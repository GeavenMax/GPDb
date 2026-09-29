package com.gpdb.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.preferences.AppPreferences
import com.gpdb.android.data.repository.BrowseRepository
import com.gpdb.android.data.settings.AppSettingsRepository
import com.gpdb.android.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appPreferences: AppPreferences,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {
    val language = appPreferences.languageFlow.stateIn(viewModelScope, SharingStarted.Lazily, "system")
    val recordHistory = appPreferences.recordSearchHistoryFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    
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
}
