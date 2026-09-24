package com.gpdb.android.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val appPreferences: AppPreferences) : ViewModel() {
    val language = appPreferences.languageFlow.stateIn(viewModelScope, SharingStarted.Lazily, "system")
    val recordHistory = appPreferences.recordSearchHistoryFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val theme = appPreferences.themeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "system")
    val appIcon = appPreferences.appIconFlow.stateIn(viewModelScope, SharingStarted.Lazily, "B")

    fun setLanguage(lang: String) = viewModelScope.launch { appPreferences.setLanguage(lang) }
    fun setRecordHistory(record: Boolean) = viewModelScope.launch { appPreferences.setRecordSearchHistory(record) }
    fun setTheme(theme: String) = viewModelScope.launch { appPreferences.setTheme(theme) }
    fun setAppIcon(icon: String) = viewModelScope.launch { appPreferences.setAppIcon(icon) }
}

fun changeAppIcon(context: Context, newIcon: String) {
    val pm = context.packageManager
    val packageName = context.packageName
    
    val aliases = mapOf(
        "A" to "$packageName.MainActivityAliasA",
        "B" to "$packageName.MainActivityAliasB",
        "C" to "$packageName.MainActivityAliasC",
        "D" to "$packageName.MainActivityAliasD"
    )
    
    aliases.forEach { (key, componentNameStr) ->
        val state = if (key == newIcon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(
            ComponentName(packageName, componentNameStr),
            state,
            PackageManager.DONT_KILL_APP
        )
    }
    
    Toast.makeText(context, "已更新应用图标，桌面图标可能需要几秒钟更新，应用可能重启", Toast.LENGTH_LONG).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(appPreferences: AppPreferences, onRemountClick: () -> Unit) {
    val viewModel = remember { SettingsViewModel(appPreferences) }
    val language by viewModel.language.collectAsState()
    val recordHistory by viewModel.recordHistory.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val appIcon by viewModel.appIcon.collectAsState()
    
    var showLangDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

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
                headlineContent = { Text("更换应用图标") },
                supportingContent = { Text("方案 $appIcon") },
                modifier = Modifier.clickable { showIconDialog = true }
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
    
    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text("选择应用图标") },
            text = {
                Column {
                    listOf("A" to "方案 A", "B" to "方案 B", "C" to "方案 C", "D" to "方案 D").forEach { (code, name) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            if (appIcon != code) {
                                viewModel.setAppIcon(code)
                                changeAppIcon(context, code)
                            }
                            showIconDialog = false
                        }) {
                            RadioButton(selected = appIcon == code, onClick = {
                                if (appIcon != code) {
                                    viewModel.setAppIcon(code)
                                    changeAppIcon(context, code)
                                }
                                showIconDialog = false
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
