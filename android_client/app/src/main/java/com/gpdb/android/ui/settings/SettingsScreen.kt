package com.gpdb.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.gpdb.android.data.preferences.AppPreferences
import androidx.compose.ui.Alignment

class SettingsViewModel(private val appPreferences: AppPreferences) : ViewModel() {
    val language = appPreferences.languageFlow.stateIn(viewModelScope, SharingStarted.Lazily, "system")
    val recordHistory = appPreferences.recordSearchHistoryFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val theme = appPreferences.themeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "system")

    fun setLanguage(lang: String) = viewModelScope.launch { appPreferences.setLanguage(lang) }
    fun setRecordHistory(record: Boolean) = viewModelScope.launch { appPreferences.setRecordSearchHistory(record) }
    fun setTheme(theme: String) = viewModelScope.launch { appPreferences.setTheme(theme) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(appPreferences: AppPreferences, onRemountClick: () -> Unit) {
    val viewModel = remember { SettingsViewModel(appPreferences) }
    val language by viewModel.language.collectAsState()
    val recordHistory by viewModel.recordHistory.collectAsState()
    val theme by viewModel.theme.collectAsState()
    var showLangDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Text("基本设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            
            ListItem(
                headlineContent = { Text("语言") },
                supportingContent = { Text(when(language) { "zh" -> "中文"; "en" -> "English"; else -> "跟随系统" }) },
                modifier = Modifier.clickable { showLangDialog = true }
            )
            
            ListItem(
                headlineContent = { Text("记录搜索历史") },
                supportingContent = { Text("开启后，将保存您在搜索页面的搜索词记录") },
                trailingContent = {
                    Switch(checked = recordHistory, onCheckedChange = { viewModel.setRecordHistory(it) })
                }
            )
            
            ListItem(
                headlineContent = { Text("主题外观") },
                supportingContent = { Text(when(theme) { "light" -> "浅色主题"; "dark" -> "深色主题"; else -> "跟随系统" }) },
                modifier = Modifier.clickable { showThemeDialog = true }
            )
            
            ListItem(
                headlineContent = { Text("重新挂载数据库") },
                supportingContent = { Text("断开当前数据库并重新选择挂载路径") },
                modifier = Modifier.clickable { onRemountClick() }
            )
        }
    }

    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text("选择语言") },
            text = {
                Column {
                    listOf("system" to "跟随系统", "zh" to "中文", "en" to "English").forEach { (code, name) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.setLanguage(code)
                            showLangDialog = false
                        }) {
                            RadioButton(selected = language == code, onClick = {
                                viewModel.setLanguage(code)
                                showLangDialog = false
                            })
                            Text(name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题") },
            text = {
                Column {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (code, name) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.setTheme(code)
                            showThemeDialog = false
                        }) {
                            RadioButton(selected = theme == code, onClick = {
                                viewModel.setTheme(code)
                                showThemeDialog = false
                            })
                            Text(name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
