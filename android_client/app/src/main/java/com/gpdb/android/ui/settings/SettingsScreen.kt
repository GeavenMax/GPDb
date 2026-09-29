package com.gpdb.android.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import org.json.JSONArray

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

import com.gpdb.android.data.settings.AppSettingsRepository
import com.gpdb.android.data.settings.ThemeMode



fun changeAppIcon(context: Context, scope: CoroutineScope, newIcon: String) {
    val pm = context.packageManager
    val packageName = context.packageName
    
    val baseNamespace = "com.gpdb.android"
    val aliases = mapOf(
        "A" to "$baseNamespace.MainActivityAliasA",
        "B" to "$baseNamespace.MainActivityAliasB",
        "C" to "$baseNamespace.MainActivityAliasC",
        "D" to "$baseNamespace.MainActivityAliasD"
    )
    
    // First, immediately enable the new one
    aliases[newIcon]?.let { componentNameStr ->
        pm.setComponentEnabledSetting(
            ComponentName(packageName, componentNameStr),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
    
    // Then delay the disable of the others to prevent immediate process kill
    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
        kotlinx.coroutines.delay(5000)
        try {
            aliases.filterKeys { it != newIcon }.forEach { (_, componentNameStr) ->
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, componentNameStr),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    Toast.makeText(context, "已更新应用图标，桌面图标将在几秒钟后更新", Toast.LENGTH_LONG).show()
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(appPreferences: AppPreferences, appSettingsRepository: AppSettingsRepository, onRemountClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mountPreferences = remember { com.gpdb.android.data.preferences.MountPreferences(context) }
    val viewModel = remember { SettingsViewModel(appPreferences, appSettingsRepository, mountPreferences) }
    val language by viewModel.language.collectAsState()
    val recordHistory by viewModel.recordHistory.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val llmApiKey by viewModel.llmApiKey.collectAsState()
    val llmBaseUrl by viewModel.llmBaseUrl.collectAsState()
    val llmModel by viewModel.llmModel.collectAsState()
    val appIcon by viewModel.appIcon.collectAsState()
    
    val translatableMovies by viewModel.translatableMovies.collectAsState()
    val translatedMovies by viewModel.translatedMovies.collectAsState()
    val translatableEpisodes by viewModel.translatableEpisodes.collectAsState()
    val translatedEpisodes by viewModel.translatedEpisodes.collectAsState()
    
    val llmProvider by viewModel.llmProvider.collectAsState()
    val llmAutoTranslate by viewModel.llmAutoTranslate.collectAsState()
    val llmTargetLanguage by viewModel.llmTargetLanguage.collectAsState()
    val llmSystemPrompt by viewModel.llmSystemPrompt.collectAsState()

    var showLangDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    var showLlmDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        val mainScrollState = rememberScrollState()
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(mainScrollState).padding(16.dp).fillMaxWidth()) {
            Text("增量同步与图库缓存", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val syncStatus by viewModel.syncStatus.collectAsState()
                    val periodicSyncEnabled by viewModel.periodicSyncEnabled.collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("后台静默自动同步", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "仅在 Wi-Fi 且充电时增量同步官网最新数据并预下载海报",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = periodicSyncEnabled,
                            onCheckedChange = { viewModel.setPeriodicSyncEnabled(it, context) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (val status = syncStatus) {
                        is SettingsViewModel.SyncStatus.Idle -> {
                            Button(
                                onClick = { viewModel.checkForUpdates(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("检查官网最新条目")
                            }
                        }
                        is SettingsViewModel.SyncStatus.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("正在扫描官网最新发布 (/newm, /newe, /newp)...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is SettingsViewModel.SyncStatus.Discovered -> {
                            val updates = status.updates
                            if (updates.totalCount == 0) {
                                Text("✅ 当前数据库已是最新，无新增条目", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { viewModel.resetSyncStatus() }) {
                                    Text("完成")
                                }
                            } else {
                                Text(
                                    "发现新发布内容：影片 ${updates.movieIds.size} 部，分集 ${updates.episodeIds.size} 个，演员 ${updates.performerIds.size} 位",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.startSync(context, updates) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("立即同步并下载图片")
                                    }
                                    OutlinedButton(onClick = { viewModel.resetSyncStatus() }) {
                                        Text("取消")
                                    }
                                }
                            }
                        }
                        is SettingsViewModel.SyncStatus.Syncing -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("正在同步: ${status.currentItem} (${status.current}/${status.total})", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { if (status.total > 0) status.current.toFloat() / status.total.toFloat() else 0f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        is SettingsViewModel.SyncStatus.Completed -> {
                            Text("🎉 ${status.result.message}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.resetSyncStatus() }) {
                                Text("完成")
                            }
                        }
                        is SettingsViewModel.SyncStatus.Error -> {
                            Text("❌ ${status.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.resetSyncStatus() }) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("翻译统计", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("影片: 已翻译 $translatedMovies / 总数 $translatableMovies", style = MaterialTheme.typography.bodyMedium)
                    if (translatableMovies > 0) {
                        LinearProgressIndicator(
                            progress = { translatedMovies.toFloat() / translatableMovies.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("分集: 已翻译 $translatedEpisodes / 总数 $translatableEpisodes", style = MaterialTheme.typography.bodyMedium)
                    if (translatableEpisodes > 0) {
                        LinearProgressIndicator(
                            progress = { translatedEpisodes.toFloat() / translatableEpisodes.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val exportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        if (uri != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val db = com.gpdb.android.data.db.DatabaseHolder.db ?: return@launch
                                    val rootObj = JSONObject()
                                    rootObj.put("version", 1)
                                    rootObj.put("export_time", System.currentTimeMillis())
                                    
                                    val moviesArray = JSONArray()
                                    // Fetch more metadata for better portability
                                    db.query(androidx.sqlite.db.SimpleSQLiteQuery("SELECT id, title, studio_name, release_year, category, description, description_zh FROM movies WHERE description_zh IS NOT NULL AND description_zh != ''")).use { cursor ->
                                        while(cursor.moveToNext()) {
                                            val obj = JSONObject()
                                            obj.put("id", cursor.getLong(0))
                                            obj.put("title", cursor.getString(1))
                                            obj.put("studio_name", cursor.getString(2))
                                            obj.put("release_year", cursor.getString(3))
                                            obj.put("category", cursor.getString(4))
                                            obj.put("description", cursor.getString(5))
                                            obj.put("description_zh", cursor.getString(6))
                                            moviesArray.put(obj)
                                        }
                                    }
                                    rootObj.put("movies", moviesArray)
                                    
                                    val episodesArray = JSONArray()
                                    db.query(androidx.sqlite.db.SimpleSQLiteQuery("SELECT id, title, movie_id, studio_name, release_date, description, action_notes, description_zh FROM episodes WHERE description_zh IS NOT NULL AND description_zh != ''")).use { cursor ->
                                        while(cursor.moveToNext()) {
                                            val obj = JSONObject()
                                            obj.put("id", cursor.getLong(0))
                                            obj.put("title", cursor.getString(1))
                                            obj.put("movie_id", cursor.getLong(2))
                                            obj.put("studio_name", cursor.getString(3))
                                            obj.put("release_date", cursor.getString(4))
                                            obj.put("description", cursor.getString(5))
                                            obj.put("action_notes", cursor.getString(6))
                                            obj.put("description_zh", cursor.getString(7))
                                            episodesArray.put(obj)
                                        }
                                    }
                                    rootObj.put("episodes", episodesArray)
                                    
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        out.write(rootObj.toString(4).toByteArray())
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "导出翻译数据成功！", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }

                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val db = com.gpdb.android.data.db.DatabaseHolder.db ?: return@launch
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    if (inputStream != null) {
                                        val jsonString = inputStream.bufferedReader().use { it.readText() }
                                        val rootObj = JSONObject(jsonString)
                                        
                                        var importedMovies = 0
                                        if (rootObj.has("movies")) {
                                            val moviesArray = rootObj.getJSONArray("movies")
                                            for (i in 0 until moviesArray.length()) {
                                                val obj = moviesArray.getJSONObject(i)
                                                if (obj.has("id") && obj.has("description_zh")) {
                                                    val id = obj.getLong("id")
                                                    val zh = obj.getString("description_zh")
                                                    if (zh.isNotBlank()) {
                                                        db.query(androidx.sqlite.db.SimpleSQLiteQuery("UPDATE movies SET description_zh = ? WHERE id = ?", arrayOf<Any>(zh, id)))
                                                        importedMovies++
                                                    }
                                                }
                                            }
                                        }
                                        
                                        var importedEpisodes = 0
                                        if (rootObj.has("episodes")) {
                                            val episodesArray = rootObj.getJSONArray("episodes")
                                            for (i in 0 until episodesArray.length()) {
                                                val obj = episodesArray.getJSONObject(i)
                                                if (obj.has("id") && obj.has("description_zh")) {
                                                    val id = obj.getLong("id")
                                                    val zh = obj.getString("description_zh")
                                                    if (zh.isNotBlank()) {
                                                        db.query(androidx.sqlite.db.SimpleSQLiteQuery("UPDATE episodes SET description_zh = ? WHERE id = ?", arrayOf<Any>(zh, id)))
                                                        importedEpisodes++
                                                    }
                                                }
                                            }
                                        }
                                        
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "成功导入 $importedMovies 部影片与 $importedEpisodes 个分集的翻译！", Toast.LENGTH_LONG).show()
                                            viewModel.refreshStats()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "导入失败，文件格式可能不正确: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf<String>("application/json", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入翻译数据")
                        }
                        OutlinedButton(
                            onClick = { exportLauncher.launch("GPDb_Translations.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出翻译数据")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
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
                supportingContent = { Text(when(themeMode) { ThemeMode.LIGHT -> "浅色主题"; ThemeMode.DARK -> "深色主题"; else -> "跟随系统" }) },
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            ListItem(
                headlineContent = { Text("动态取色") },
                supportingContent = { Text("开启后，应用主色调将根据系统壁纸自动变化 (需要 Android 12+)") },
                trailingContent = {
                    Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
                }
            )
            
            ListItem(
                headlineContent = { Text("AI 翻译引擎") },
                supportingContent = { Text(if (llmApiKey.isNotBlank()) "已配置: $llmModel" else "未配置") },
                modifier = Modifier.clickable { showLlmDialog = true }
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
                    listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色").forEach { (mode, name) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.setThemeMode(mode)
                            showThemeDialog = false
                        }) {
                            RadioButton(selected = themeMode == mode, onClick = {
                                viewModel.setThemeMode(mode)
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
    
    if (showLlmDialog) {
        var inputProvider by remember { mutableStateOf(llmProvider) }
        var inputApiKey by remember { mutableStateOf(llmApiKey) }
        var inputBaseUrl by remember { mutableStateOf(llmBaseUrl) }
        var inputModel by remember { mutableStateOf(llmModel) }
        var inputAutoTranslate by remember { mutableStateOf(llmAutoTranslate) }
        var inputTargetLanguage by remember { mutableStateOf(llmTargetLanguage) }
        var inputSystemPrompt by remember { mutableStateOf(llmSystemPrompt) }
        AlertDialog(
            onDismissRequest = { showLlmDialog = false },
            title = { Text("AI 翻译引擎配置") },
            text = {
                // Wrap with a vertically scrollable Column if it gets too large
                val scrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(scrollState)
                ) {
                    // Provider ExposedDropdownMenu
                    val presets = listOf(
                        Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
                        Triple("Claude (Anthropic)", "https://api.anthropic.com/v1", "claude-3-5-sonnet-20240620"),
                        Triple("Gemini (Google)", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-1.5-pro"),
                        Triple("DeepSeek (深度求索)", "https://api.deepseek.com/v1", "deepseek-chat"),
                        Triple("Kimi (月之暗面)", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
                        Triple("通义千问 (DashScope)", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
                        Triple("智谱清言 (Zhipu)", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
                        Triple("豆包 (Doubao)", "https://ark.cn-beijing.volces.com/api/v3", "ep-xxxx"),
                        Triple("SiliconFlow (硅基流动)", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V2.5"),
                        Triple("Ollama (本地/模拟器)", "http://10.0.2.2:11434/v1", "qwen2:7b"),
                        Triple("自定义 (Custom)", "", "")
                    )
                    var providerExpanded by remember { mutableStateOf(false) }

                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = !providerExpanded }
                    ) {
                        OutlinedTextField(
                            value = inputProvider,
                            onValueChange = { inputProvider = it },
                            label = { Text("服务提供商方案") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            readOnly = false,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false }
                        ) {
                            presets.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption.first) },
                                    onClick = {
                                        inputProvider = selectionOption.first
                                        if (selectionOption.first != "自定义 (Custom)") {
                                            inputBaseUrl = selectionOption.second
                                            inputModel = selectionOption.third
                                        }
                                        providerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = inputBaseUrl,
                        onValueChange = { inputBaseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
                    var fetchingModels by remember { mutableStateOf(false) }
                    var fetchError by remember { mutableStateOf<String?>(null) }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = inputApiKey,
                            onValueChange = { inputApiKey = it },
                            label = { Text("API Key") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (inputBaseUrl.isBlank() || inputApiKey.isBlank()) {
                                fetchError = "请先填写 Base URL 和 API Key"
                                return@IconButton
                            }
                            fetchingModels = true
                            fetchError = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val url = if (inputBaseUrl.endsWith("/")) "${inputBaseUrl}models" else "$inputBaseUrl/models"
                                    val request = okhttp3.Request.Builder()
                                        .url(url)
                                        .get()
                                        .addHeader("Authorization", "Bearer $inputApiKey")
                                        .build()
                                    val client = okhttp3.OkHttpClient()
                                    client.newCall(request).execute().use { response ->
                                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                                        val body = response.body?.string() ?: throw Exception("返回数据为空")
                                        val json = org.json.JSONObject(body)
                                        val data = json.optJSONArray("data") ?: org.json.JSONArray()
                                        val models = mutableListOf<String>()
                                        for (i in 0 until data.length()) {
                                            models.add(data.getJSONObject(i).getString("id"))
                                        }
                                        withContext(Dispatchers.Main) {
                                            fetchedModels = models
                                            Toast.makeText(context, "成功获取 ${models.size} 个可用模型", Toast.LENGTH_SHORT).show()
                                            if (models.isNotEmpty() && !models.contains(inputModel)) {
                                                inputModel = models[0]
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        fetchError = e.message ?: "未知错误"
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        fetchingModels = false
                                    }
                                }
                            }
                        }) {
                            if (fetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "验证 Key 并获取模型")
                            }
                        }
                    }
                    if (fetchError != null) {
                        Text(text = "验证/获取模型失败: $fetchError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    var modelExpanded by remember { mutableStateOf(false) }
                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = !modelExpanded }
                    ) {
                        OutlinedTextField(
                            value = inputModel,
                            onValueChange = { inputModel = it },
                            label = { Text("Model Name") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, true),
                            readOnly = false,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        if (fetchedModels.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                fetchedModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            inputModel = m
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = inputTargetLanguage,
                        onValueChange = { inputTargetLanguage = it },
                        label = { Text("目标语言") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputSystemPrompt,
                        onValueChange = { inputSystemPrompt = it },
                        label = { Text("System Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("进入详情自动翻译")
                        Switch(checked = inputAutoTranslate, onCheckedChange = { inputAutoTranslate = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setLlmConfig(
                        provider = inputProvider,
                        apiKey = inputApiKey,
                        baseUrl = inputBaseUrl,
                        model = inputModel,
                        autoTranslate = inputAutoTranslate,
                        targetLanguage = inputTargetLanguage,
                        systemPrompt = inputSystemPrompt
                    )
                    showLlmDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLlmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    
    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text("选择应用图标") },
            text = {
                Column {
                    listOf(
                        "A" to Pair("方案 A (双雄火星图腾)", com.gpdb.android.R.mipmap.ic_launcher_a),
                        "B" to Pair("方案 B (原版标志)", com.gpdb.android.R.mipmap.ic_launcher_b),
                        "C" to Pair("方案 C", com.gpdb.android.R.mipmap.ic_launcher_c),
                        "D" to Pair("方案 D", com.gpdb.android.R.mipmap.ic_launcher_d)
                    ).forEach { (code, pair) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            if (appIcon != code) {
                                viewModel.setAppIcon(code)
                                changeAppIcon(context, scope, code)
                            }
                            showIconDialog = false
                        }.padding(vertical = 8.dp)) {
                            RadioButton(selected = appIcon == code, onClick = {
                                if (appIcon != code) {
                                    viewModel.setAppIcon(code)
                                    changeAppIcon(context, scope, code)
                                }
                                showIconDialog = false
                            })
                            coil.compose.AsyncImage(
                                model = pair.second,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).padding(start = 8.dp, end = 12.dp)
                            )
                            Text(pair.first)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

