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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.text.font.FontWeight
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
fun SettingsScreen(
    appPreferences: AppPreferences,
    appSettingsRepository: AppSettingsRepository,
    onRemountClick: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
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

    val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appLockPin by viewModel.appLockPin.collectAsState()
    val appLockBiometricEnabled by viewModel.appLockBiometricEnabled.collectAsState()
    val appLockTimeoutSeconds by viewModel.appLockTimeoutSeconds.collectAsState()

    val panicSwitchEnabled by viewModel.panicSwitchEnabled.collectAsState()
    val panicFaceDownEnabled by viewModel.panicFaceDownEnabled.collectAsState()
    val panicShakeEnabled by viewModel.panicShakeEnabled.collectAsState()
    val panicAction by viewModel.panicAction.collectAsState()

    var showLangDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    var showLlmDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showPanicActionDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
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

            Text("海报与视觉展示", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val posterMode by viewModel.posterDisplayMode.collectAsState()
                    var showPosterModeDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text("影片档案海报展示方案") },
                        supportingContent = { 
                            Text(
                                (if (posterMode == "flip_3d") "方案一：3D 拟真翻转卡片 (正面封面 + 封底 3D 旋转)" else "方案二：自适应画廊轮播 (多海报完整无裁切，高精度原比例)") +
                                "\n注：轻触任意海报均可唤起全屏手势缩放灯箱（方案三）"
                            ) 
                        },
                        modifier = Modifier.clickable { showPosterModeDialog = true }
                    )

                    if (showPosterModeDialog) {
                        AlertDialog(
                            onDismissRequest = { showPosterModeDialog = false },
                            title = { Text("选择影片档案海报展示方案") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            viewModel.setPosterDisplayMode("adaptive_pager")
                                            showPosterModeDialog = false
                                        }.padding(vertical = 8.dp)
                                    ) {
                                        RadioButton(selected = posterMode == "adaptive_pager", onClick = {
                                            viewModel.setPosterDisplayMode("adaptive_pager")
                                            showPosterModeDialog = false
                                        })
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("方案二：自适应画廊轮播 (推荐)", fontWeight = FontWeight.Bold)
                                            Text("完整呈现海报无裁切，支持正面/封底/多变体海报平滑滑动与分页指示器", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            viewModel.setPosterDisplayMode("flip_3d")
                                            showPosterModeDialog = false
                                        }.padding(vertical = 8.dp)
                                    ) {
                                        RadioButton(selected = posterMode == "flip_3d", onClick = {
                                            viewModel.setPosterDisplayMode("flip_3d")
                                            showPosterModeDialog = false
                                        })
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("方案一：3D 拟真翻转实体卡片", fontWeight = FontWeight.Bold)
                                            Text("正面封面与封底封套 3D 空间立体旋转，高拟真还原实体碟片把玩质感", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showPosterModeDialog = false }) { Text("关闭") }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("高级隐私安全与沙盒防护", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. 多任务防窥与防截屏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("多任务防窥与防截屏 (FLAG_SECURE)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "在最近任务切换器中隐藏应用缩略图，防止身边他人窥屏；同时禁止系统截屏与录屏。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = flagSecureEnabled,
                            onCheckedChange = { viewModel.setFlagSecureEnabled(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 2. 应用锁与生物识别
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("应用安全锁 (PIN / 生物识别)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (appLockPin.isBlank()) "未设置独立密码。开启将引导设置 4-6 位安全 PIN 码。"
                                else "已启用安全锁。冷启动或切到后台超时后需验证进入。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { enable ->
                                if (enable && appLockPin.isBlank()) {
                                    showPinDialog = true
                                } else {
                                    viewModel.setAppLockEnabled(enable)
                                }
                            }
                        )
                    }

                    if (appLockEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPinDialog = true }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("修改独立 PIN 码", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text("点击修改", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("指纹 / 面容快速解锁", style = MaterialTheme.typography.bodyMedium)
                                Text("支持调用设备生物识别传感器一键解锁", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = appLockBiometricEnabled,
                                onCheckedChange = { viewModel.setAppLockBiometricEnabled(it) }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimeoutDialog = true }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("自动锁定超时", style = MaterialTheme.typography.bodyMedium)
                                Text("应用退至后台超过此时长自动锁定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                when (appLockTimeoutSeconds) {
                                    0 -> "立即锁定"
                                    30 -> "30 秒"
                                    60 -> "1 分钟"
                                    300 -> "5 分钟"
                                    else -> "${appLockTimeoutSeconds} 秒"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 3. 紧急一键脱身 (Panic Switch)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("紧急一键脱身 (Panic Switch)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "感知设备姿态异常时，瞬间执行紧急隐蔽或伪装逃逸。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = panicSwitchEnabled,
                            onCheckedChange = { viewModel.setPanicSwitchEnabled(it) }
                        )
                    }

                    if (panicSwitchEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("正面朝下快速扣桌触发 (Face Down)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = panicFaceDownEnabled,
                                onCheckedChange = { viewModel.setPanicFaceDownEnabled(it) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("剧烈晃动手机触发 (Shake)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = panicShakeEnabled,
                                onCheckedChange = { viewModel.setPanicShakeEnabled(it) }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPanicActionDialog = true }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("脱身响应动作", style = MaterialTheme.typography.bodyMedium)
                                Text("触发脱身后执行的具体行为", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                when (panicAction) {
                                    "CALCULATOR" -> "伪装计算器"
                                    "HOME" -> "退回桌面"
                                    "KILL" -> "彻底结束"
                                    else -> "伪装计算器"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 4. 沙盒与外部存储隔离
                    val saveExternal by viewModel.saveImagesToExternal.collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("允许将图库保存至外部存储", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "默认禁用（最高隐私安全）。开启后将网络拉取的图片写入挂载目录以便多端备份；关闭时图片 100% 隔离在应用专属私有沙盒内，其他 App 和系统相册绝对无权访问。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = saveExternal,
                            onCheckedChange = { viewModel.setSaveImagesToExternal(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.enforceExternalNoMedia()
                            Toast.makeText(context, "已在挂载目录及所有子文件夹重新注入 .nomedia 保护文件！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("在挂载目录重新注入 .nomedia 防扫描")
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

    
    if (showPinDialog) {
        var inputPin by remember { mutableStateOf(appLockPin) }
        var confirmPin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("设置独立 PIN 码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("请输入 4 至 6 位纯数字密码，用于解锁 App 与伪装计算器脱出。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) inputPin = it },
                        label = { Text("PIN 码 (4-6 位数字)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) confirmPin = it },
                        label = { Text("确认 PIN 码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(pinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputPin.length !in 4..6) {
                        pinError = "PIN 码必须为 4 至 6 位数字"
                    } else if (inputPin != confirmPin) {
                        pinError = "两次输入的 PIN 码不一致"
                    } else {
                        viewModel.setAppLockPin(inputPin)
                        viewModel.setAppLockEnabled(true)
                        Toast.makeText(context, "已成功设置应用锁密码", Toast.LENGTH_SHORT).show()
                        showPinDialog = false
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("自动锁定超时时间") },
            text = {
                Column {
                    listOf(
                        0 to "立即锁定 (退出即锁)",
                        30 to "30 秒 (推荐)",
                        60 to "1 分钟",
                        300 to "5 分钟"
                    ).forEach { (seconds, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLockTimeoutSeconds(seconds)
                                    showTimeoutDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = appLockTimeoutSeconds == seconds,
                                onClick = {
                                    viewModel.setAppLockTimeoutSeconds(seconds)
                                    showTimeoutDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showPanicActionDialog) {
        AlertDialog(
            onDismissRequest = { showPanicActionDialog = false },
            title = { Text("脱身响应动作") },
            text = {
                Column {
                    listOf(
                        "CALCULATOR" to Pair("伪装跳转至高仿计算器 (推荐)", "极具迷惑性，可进行真实四则运算，输入密码加等号即可退出"),
                        "HOME" to Pair("极速退回手机主屏幕", "立即将 App 置于后台并隐藏界面"),
                        "KILL" to Pair("彻底结束 App 进程", "清退进程与所有活动任务栈")
                    ).forEach { (actionKey, pair) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPanicAction(actionKey)
                                    showPanicActionDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = panicAction == actionKey,
                                onClick = {
                                    viewModel.setPanicAction(actionKey)
                                    showPanicActionDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(pair.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(pair.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
            title = { Text("选择应用图标与名称伪装") },
            text = {
                Column {
                    listOf(
                        "A" to Pair("原版标识 (GPDb)", com.gpdb.android.R.mipmap.ic_launcher_b),
                        "B" to Pair("伪装图标 1 (极简便签)", com.gpdb.android.R.mipmap.ic_launcher_a),
                        "C" to Pair("伪装图标 2 (常用计算器)", com.gpdb.android.R.mipmap.ic_launcher_c),
                        "D" to Pair("伪装图标 3 (收支记账)", com.gpdb.android.R.mipmap.ic_launcher_d)
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

