package com.gpdb.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "gpdb_app_prefs")

class AppPreferences(private val context: Context) {
    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_RECORD_SEARCH_HISTORY = booleanPreferencesKey("record_search_history")
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        private val KEY_THEME = stringPreferencesKey("app_theme")
    }

    val languageFlow: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "system"
    }

    val recordSearchHistoryFlow: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[KEY_RECORD_SEARCH_HISTORY] ?: true
    }

    val searchHistoryFlow: Flow<List<String>> = context.appDataStore.data.map { prefs ->
        val str = prefs[KEY_SEARCH_HISTORY] ?: ""
        if (str.isEmpty()) emptyList() else str.split("|||")
    }


    val themeFlow: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "system"
    }

    suspend fun setTheme(theme: String) {
        context.appDataStore.edit { prefs -> prefs[KEY_THEME] = theme }
    }

    suspend fun setLanguage(lang: String) {
        context.appDataStore.edit { prefs -> prefs[KEY_LANGUAGE] = lang }
    }

    suspend fun setRecordSearchHistory(record: Boolean) {
        context.appDataStore.edit { prefs -> 
            prefs[KEY_RECORD_SEARCH_HISTORY] = record 
            if (!record) {
                prefs.remove(KEY_SEARCH_HISTORY)
            }
        }
    }

    suspend fun addSearchHistory(query: String) {
        context.appDataStore.edit { prefs ->
            val record = prefs[KEY_RECORD_SEARCH_HISTORY] ?: true
            if (!record) return@edit
            val current = prefs[KEY_SEARCH_HISTORY] ?: ""
            val list = if (current.isEmpty()) mutableListOf() else current.split("|||").toMutableList()
            list.remove(query)
            list.add(0, query)
            if (list.size > 20) list.removeLast()
            prefs[KEY_SEARCH_HISTORY] = list.joinToString("|||")
        }
    }

    suspend fun clearSearchHistory() {
        context.appDataStore.edit { prefs -> prefs.remove(KEY_SEARCH_HISTORY) }
    }
}
