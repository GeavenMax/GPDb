package com.gpdb.android.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpdb.android.data.preferences.AppPreferences
import com.gpdb.android.data.preferences.MountPreferences
import com.gpdb.android.data.settings.AppSettingsRepository
import com.gpdb.android.ui.theme.*
import com.gpdb.android.util.AppLanguage
import com.gpdb.android.util.I18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SettingsTab(val label: String, val icon: ImageVector) {
    ALL("全部", Icons.Default.Settings),
    APPEARANCE("外观", Icons.Default.Palette),
    ANALYTICS("统计", Icons.Default.Insights),
    PRIVACY("隐私", Icons.Default.Security),
    TRANSLATE("AI翻译", Icons.Default.Translate),
    SYNC("数据同步", Icons.Default.Storage),
    ABOUT("关于更新", Icons.Default.Info)
}

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

    aliases[newIcon]?.let { componentNameStr ->
        pm.setComponentEnabledSetting(
            ComponentName(packageName, componentNameStr),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    scope.launch(Dispatchers.Main) {
        delay(5000)
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
    onBackClick: (() -> Unit)? = null,
    onNavigateToAnalytics: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val mountPreferences = remember { MountPreferences(context) }
    val viewModel = remember { SettingsViewModel(appPreferences, appSettingsRepository, mountPreferences) }
    val scope = rememberCoroutineScope()

    val language by viewModel.language.collectAsState()
    val themeChoice by viewModel.themeChoice.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val appIcon by viewModel.appIcon.collectAsState()
    val recordHistory by viewModel.recordHistory.collectAsState()

    val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appLockPin by viewModel.appLockPin.collectAsState()
    val appLockBiometricEnabled by viewModel.appLockBiometricEnabled.collectAsState()
    val appLockTimeoutSeconds by viewModel.appLockTimeoutSeconds.collectAsState()

    val panicSwitchEnabled by viewModel.panicSwitchEnabled.collectAsState()
    val panicFaceDownEnabled by viewModel.panicFaceDownEnabled.collectAsState()
    val panicShakeEnabled by viewModel.panicShakeEnabled.collectAsState()
    val panicAction by viewModel.panicAction.collectAsState()

    val screenshotPrivacyBlurEnabled by viewModel.screenshotPrivacyBlurEnabled.collectAsState()
    val screenshotPrivacyBlurImages by viewModel.screenshotPrivacyBlurImages.collectAsState()
    val screenshotPrivacyBlurText by viewModel.screenshotPrivacyBlurText.collectAsState()

    val llmApiKey by viewModel.llmApiKey.collectAsState()
    val llmBaseUrl by viewModel.llmBaseUrl.collectAsState()
    val llmModel by viewModel.llmModel.collectAsState()
    val llmProvider by viewModel.llmProvider.collectAsState()
    val llmAutoTranslate by viewModel.llmAutoTranslate.collectAsState()
    val llmTargetLanguage by viewModel.llmTargetLanguage.collectAsState()
    val llmSystemPrompt by viewModel.llmSystemPrompt.collectAsState()

    val translatableMovies by viewModel.translatableMovies.collectAsState()
    val translatedMovies by viewModel.translatedMovies.collectAsState()
    val translatableEpisodes by viewModel.translatableEpisodes.collectAsState()
    val translatedEpisodes by viewModel.translatedEpisodes.collectAsState()

    var showLangDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    var showLlmDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showPanicActionDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SettingsTab.ALL) }
    var isCheckingAppUpdate by remember { mutableStateOf(false) }
    var activeAppUpdateRelease by remember { mutableStateOf<com.gpdb.android.util.AppReleaseInfo?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportUserDataToFile(context, uri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importUserDataFromFile(context, uri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = I18n.string("settings.title"),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = I18n.string("common.back")
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部二级胶囊分类切换栏 (仿照 macOS SettingsSubTab)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Section 1: 外观与个性化 ─────────────────────────────────
                if (selectedTab == SettingsTab.ALL || selectedTab == SettingsTab.APPEARANCE) {
                    SectionHeader(
                        icon = Icons.Default.Palette,
                        title = I18n.string("settings.appearance"),
                        subtitle = I18n.string("settings.themeDesc")
                    )
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            val currentTheme = AppTheme.fromId(themeChoice)
                            SettingItemRow(
                                title = I18n.string("settings.theme"),
                                subtitle = currentTheme.label,
                                icon = Icons.Default.Brightness4,
                        onClick = { showThemeDialog = true }
                    ) {
                        ThemeSwatchesMini(theme = currentTheme)
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    val currentAppLang = AppLanguage.fromCode(language)
                    SettingItemRow(
                        title = I18n.string("settings.language"),
                        subtitle = "${currentAppLang.displayName} (${currentAppLang.nativeName})",
                        icon = Icons.Default.Language,
                        onClick = { showLangDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingItemRow(
                        title = I18n.string("settings.appIcon"),
                        subtitle = when (appIcon) {
                            "D" -> "方案二：黑曜石金 (Scheme D)"
                            else -> "方案一：经典典藏蓝 (Scheme A · 默认)"
                        },
                        icon = Icons.Default.AppShortcut,
                        onClick = { showIconDialog = true }
                    )

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        SettingSwitchRow(
                            title = I18n.string("settings.dynamicColor"),
                            subtitle = "壁纸色相智能取色 (Android 12+)",
                            icon = Icons.Default.ColorLens,
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                }
            }
        }

        // ── Section 2: 本地使用统计 ─────────────────────────────────
        if (selectedTab == SettingsTab.ALL || selectedTab == SettingsTab.ANALYTICS) {
            SectionHeader(
                icon = Icons.Default.Insights,
                title = I18n.string("settings.analytics"),
                subtitle = I18n.string("settings.analyticsDesc")
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    if (onNavigateToAnalytics != null) {
                        SettingItemRow(
                            title = "查看完整使用统计报告",
                            subtitle = "探索时长、夜猫子指数、作品浏览量与足迹",
                            icon = Icons.Default.BarChart,
                            onClick = onNavigateToAnalytics
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }

                    SettingSwitchRow(
                        title = "记录搜索历史",
                        subtitle = "保存搜索页快捷检索词，可随时清空",
                        icon = Icons.Default.History,
                        checked = recordHistory,
                        onCheckedChange = { viewModel.setRecordHistory(it) }
                    )

                    SettingItemRow(
                        title = "清空本地搜索历史",
                        subtitle = "清除已保存的搜索词推荐",
                        icon = Icons.Default.DeleteSweep,
                        onClick = {
                            scope.launch {
                                appPreferences.clearSearchHistory()
                                Toast.makeText(context, "搜索历史已清空", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // ── Section 3: 隐私与安全 ─────────────────────────────────
        if (selectedTab == SettingsTab.ALL || selectedTab == SettingsTab.PRIVACY) {
            SectionHeader(
                icon = Icons.Default.Security,
                title = I18n.string("settings.privacy"),
                subtitle = I18n.string("settings.privacyDesc")
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingSwitchRow(
                        title = I18n.string("settings.flagSecure"),
                        subtitle = "多任务视图黑屏模糊遮罩，禁止截图录屏",
                        icon = Icons.Default.ScreenLockPortrait,
                        checked = flagSecureEnabled,
                        onCheckedChange = { viewModel.setFlagSecureEnabled(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = "防窥模式",
                        subtitle = if (screenshotPrivacyBlurEnabled) "已开启：所有界面图片与介绍文字高斯模糊" else "未开启：正常显示（主页顶部亦可快捷开关）",
                        icon = Icons.Default.BlurOn,
                        checked = screenshotPrivacyBlurEnabled,
                        onCheckedChange = { viewModel.setScreenshotPrivacyBlurEnabled(it) }
                    )

                    if (screenshotPrivacyBlurEnabled) {
                        SettingSwitchRow(
                            title = "高斯模糊所有图片与海报",
                            subtitle = "全库封面、海报、剧照及演员头像模糊化",
                            icon = Icons.Default.Image,
                            checked = screenshotPrivacyBlurImages,
                            onCheckedChange = { viewModel.setScreenshotPrivacyBlurImages(it) }
                        )
                        SettingSwitchRow(
                            title = "高斯模糊简介与描述文字",
                            subtitle = "剧情简介与细节说明文字模糊化防偷窥",
                            icon = Icons.Default.TextFields,
                            checked = screenshotPrivacyBlurText,
                            onCheckedChange = { viewModel.setScreenshotPrivacyBlurText(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = I18n.string("settings.appLock"),
                        subtitle = if (appLockEnabled) "已启用 (超时 ${appLockTimeoutSeconds}s)" else "未启用",
                        icon = Icons.Default.Lock,
                        checked = appLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && appLockPin.isBlank()) {
                                showPinDialog = true
                            } else {
                                viewModel.setAppLockEnabled(enabled)
                            }
                        }
                    )

                    if (appLockEnabled) {
                        SettingItemRow(
                            title = "修改安全 PIN 码",
                            subtitle = if (appLockPin.isNotBlank()) "已设置密码" else "尚未设置",
                            icon = Icons.Default.Pin,
                            onClick = { showPinDialog = true }
                        )
                        SettingSwitchRow(
                            title = "允许指纹 / 人脸生物识别解锁",
                            subtitle = "免密快速验证通过安全锁",
                            icon = Icons.Default.Fingerprint,
                            checked = appLockBiometricEnabled,
                            onCheckedChange = { viewModel.setAppLockBiometricEnabled(it) }
                        )
                        SettingItemRow(
                            title = "锁屏等待超时",
                            subtitle = "切换后台超过 ${appLockTimeoutSeconds} 秒时自动上锁",
                            icon = Icons.Default.Timer,
                            onClick = { showTimeoutDialog = true }
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = I18n.string("settings.panicSwitch"),
                        subtitle = if (panicSwitchEnabled) "已启用: 动作【$panicAction】" else "未启用",
                        icon = Icons.Default.WarningAmber,
                        checked = panicSwitchEnabled,
                        onCheckedChange = { viewModel.setPanicSwitchEnabled(it) }
                    )

                    if (panicSwitchEnabled) {
                        SettingSwitchRow(
                            title = "翻转手机 (正面朝下扣桌即退)",
                            subtitle = "利用光感与重力传感器瞬间侦测",
                            icon = Icons.Default.ScreenRotation,
                            checked = panicFaceDownEnabled,
                            onCheckedChange = { viewModel.setPanicFaceDownEnabled(it) }
                        )
                        SettingSwitchRow(
                            title = "剧烈摇晃手机脱身",
                            subtitle = "遇紧急情况猛摇手机立即响应",
                            icon = Icons.Default.Vibration,
                            checked = panicShakeEnabled,
                            onCheckedChange = { viewModel.setPanicShakeEnabled(it) }
                        )
                        SettingItemRow(
                            title = "脱身响应动作",
                            subtitle = when(panicAction) {
                                "CALCULATOR" -> "跳转伪装计算器"
                                "HOME" -> "退回系统桌面"
                                "KILL" -> "彻底销毁进程并退出"
                                else -> "跳转伪装计算器"
                            },
                            icon = Icons.Default.DirectionsRun,
                            onClick = { showPanicActionDialog = true }
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // Sandbox info banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "图片缓存已严格锁定在 Linux UID 私有沙盒，系统相册与第三方 App 绝无法扫描窥探。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Section 4: AI 智能直白翻译 ─────────────────────────────
        if (selectedTab == SettingsTab.ALL || selectedTab == SettingsTab.TRANSLATE) {
            SectionHeader(
                icon = Icons.Default.Translate,
                title = "AI 智能翻译引擎",
                subtitle = "配置大模型直白翻译服务 (OpenAI / DeepSeek / Claude / Ollama)"
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingItemRow(
                        title = "服务商与模型配置",
                        subtitle = if (llmApiKey.isNotBlank()) "$llmProvider · $llmModel (已配置)" else "点击配置 API 密钥与端点",
                        icon = Icons.Default.SettingsSuggest,
                        onClick = { showLlmDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "翻译数据进度",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "长片: $translatedMovies / $translatableMovies · 分集: $translatedEpisodes / $translatableEpisodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.refreshStats()
                                Toast.makeText(context, "翻译统计已刷新", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("刷新")
                        }
                    }
                }
            }
        }

        // ── Section 5: 同步与数据存储 ───────────────────────────────
        if (selectedTab == SettingsTab.ALL || selectedTab == SettingsTab.SYNC) {
            SectionHeader(
                icon = Icons.Default.Storage,
                title = "同步与数据存储",
                subtitle = "管理官网增量数据更新及本地库挂载"
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    val syncStatus by viewModel.syncStatus.collectAsState()
                    val periodicSyncEnabled by viewModel.periodicSyncEnabled.collectAsState()

                    SettingSwitchRow(
                        title = "后台静默自动同步",
                        subtitle = "仅在 Wi-Fi 且充电时增量同步新条目并缓存海报",
                        icon = Icons.Default.Sync,
                        checked = periodicSyncEnabled,
                        onCheckedChange = { viewModel.setPeriodicSyncEnabled(it, context) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    Column(modifier = Modifier.padding(14.dp)) {
                        when (val status = syncStatus) {
                            is SettingsViewModel.SyncStatus.Idle -> {
                                OutlinedButton(
                                    onClick = { viewModel.checkForUpdates(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("检查官方最新条目 (/newm, /newe, /newp)")
                                }
                            }
                            is SettingsViewModel.SyncStatus.Checking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("正在检查官网最新数据...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is SettingsViewModel.SyncStatus.Discovered -> {
                                val updates = status.updates
                                if (updates.totalCount == 0) {
                                    Text("当前本地已是最新，暂无新内容发布。", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("发现 ${updates.totalCount} 项待入库 (长片: ${updates.movieIds.size}, 分集: ${updates.episodeIds.size}, 演员: ${updates.performerIds.size})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.startSync(context, updates) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("立即入库并后台刮削海报")
                                    }
                                }
                            }
                            is SettingsViewModel.SyncStatus.Syncing -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("同步中: ${status.current}/${status.total} · ${status.currentItem}", style = MaterialTheme.typography.bodySmall)
                                    LinearProgressIndicator(
                                        progress = { if (status.total > 0) status.current.toFloat() / status.total else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            is SettingsViewModel.SyncStatus.Completed -> {
                                val totalNew = status.result.newMoviesCount + status.result.newEpisodesCount + status.result.newPerformersCount
                                Text("同步完成！新增 $totalNew 项 (长片: ${status.result.newMoviesCount}, 分集: ${status.result.newEpisodesCount}, 演员: ${status.result.newPerformersCount})，缓存图片: ${status.result.cachedImagesCount}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { viewModel.resetSyncStatus() }) {
                                    Text("完成")
                                }
                            }
                            is SettingsViewModel.SyncStatus.Error -> {
                                Text("同步异常: ${status.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { viewModel.resetSyncStatus() }) {
                                    Text("重试")
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingItemRow(
                        title = "重新挂载数据库",
                        subtitle = "断开当前数据库并重新选择存储位置",
                        icon = Icons.Default.FolderOpen,
                        onClick = onRemountClick
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingItemRow(
                        title = "导出通用配置备份 (JSON)",
                        subtitle = "导出“收藏”、统计时长与用户个人数据，四端通用",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                            val filename = "GPDb_Backup_${sdf.format(java.util.Date())}.json"
                            exportLauncher.launch(filename)
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingItemRow(
                        title = "导入通用配置恢复 (JSON)",
                        subtitle = "从 macOS、Windows、iOS 或其他设备备份导入收藏与统计",
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )
                }
            }
        }

        // ── Section 6: 关于与软件更新 ───────────────────────────────
        if (selectedTab == SettingsTab.ALL || selectedTab == SettingsTab.ABOUT) {
            SectionHeader(
                icon = Icons.Default.Info,
                title = "关于与软件更新",
                subtitle = "当前版本: v${com.gpdb.android.BuildConfig.VERSION_NAME} · 巡检 GitHub Releases 官方版本"
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingItemRow(
                        title = "检查新版本更新",
                        subtitle = if (isCheckingAppUpdate) "正在连接 GitHub 巡检最新版本..." else "点击立即检测官方仓库是否有新版本",
                        icon = if (isCheckingAppUpdate) Icons.Default.CloudSync else Icons.Default.SystemUpdate,
                        onClick = {
                            if (!isCheckingAppUpdate) {
                                isCheckingAppUpdate = true
                                scope.launch {
                                    when (val res = com.gpdb.android.util.AppUpdateManager.checkAppUpdateDetailed()) {
                                        is com.gpdb.android.util.AppUpdateCheckResult.UpdateAvailable -> {
                                            isCheckingAppUpdate = false
                                            activeAppUpdateRelease = res.info
                                        }
                                        is com.gpdb.android.util.AppUpdateCheckResult.AlreadyLatest -> {
                                            isCheckingAppUpdate = false
                                            Toast.makeText(context, "当前已是最新版本 (v${res.currentVersion})", Toast.LENGTH_SHORT).show()
                                        }
                                        is com.gpdb.android.util.AppUpdateCheckResult.Error -> {
                                            isCheckingAppUpdate = false
                                            Toast.makeText(context, "检查更新失败: ${res.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        if (isCheckingAppUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "开源代码仓库",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "github.com/GeavenMax/GPDb",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        TextButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/GeavenMax/GPDb")
                                )
                                context.startActivity(intent)
                            }
                        ) {
                            Text("访问 ↗")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
}

    // ── Dialogs ──────────────────────────────────────────────────
    if (showThemeDialog) {
        val themes = listOf(
            AppTheme.AUTO to "跟随系统",
            AppTheme.GLASS_DARK to "流体玻璃 · 暗 (默认)",
            AppTheme.GLASS_LIGHT to "流体玻璃 · 浅",
            AppTheme.CLASSIC_DARK to "经典 · 暗",
            AppTheme.CLASSIC_LIGHT to "经典 · 浅",
            AppTheme.MY_DARK to "Material You · 暗",
            AppTheme.MY_LIGHT to "Material You · 浅"
        )
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(I18n.string("settings.theme")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    themes.forEach { (theme, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.setTheme(theme.id)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = AppTheme.fromId(themeChoice) == theme,
                                onClick = {
                                    viewModel.setTheme(theme.id)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            ThemeSwatchesMini(theme = theme)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(I18n.string("common.close"))
                }
            }
        )
    }

    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(I18n.string("settings.language")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.setLanguage(lang.code)
                                    showLangDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = language.equals(lang.code, ignoreCase = true),
                                onClick = {
                                    viewModel.setLanguage(lang.code)
                                    showLangDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(lang.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(lang.nativeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) {
                    Text(I18n.string("common.close"))
                }
            }
        )
    }

    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text(I18n.string("settings.appIcon")) },
            text = {
                Column {
                    listOf(
                        "A" to "方案一：经典典藏蓝 (Scheme A · 默认)",
                        "D" to "方案二：黑曜石金 / 极简 (Scheme D)"
                    ).forEach { (iconKey, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setAppIcon(iconKey)
                                    changeAppIcon(context, scope, iconKey)
                                    showIconDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = appIcon == iconKey,
                                onClick = {
                                    viewModel.setAppIcon(iconKey)
                                    changeAppIcon(context, scope, iconKey)
                                    showIconDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconDialog = false }) { Text(I18n.string("common.close")) }
            }
        )
    }

    if (showPinDialog) {
        var inputPin by remember { mutableStateOf(appLockPin) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("设置安全锁 PIN 码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入 4~6 位数字应用解锁密码：", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) inputPin = it },
                        label = { Text("PIN 码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputPin.length >= 4) {
                            viewModel.setAppLockPin(inputPin)
                            viewModel.setAppLockEnabled(true)
                            showPinDialog = false
                            Toast.makeText(context, "安全锁密码已设置", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "密码至少需 4 位数字", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text(I18n.string("common.save")) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text(I18n.string("common.cancel")) }
            }
        )
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("锁屏等待超时") },
            text = {
                Column {
                    listOf(0 to "立即锁定", 15 to "15 秒", 30 to "30 秒", 60 to "1 分钟", 300 to "5 分钟").forEach { (sec, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setAppLockTimeoutSeconds(sec)
                                    showTimeoutDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = appLockTimeoutSeconds == sec,
                                onClick = {
                                    viewModel.setAppLockTimeoutSeconds(sec)
                                    showTimeoutDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) { Text(I18n.string("common.close")) }
            }
        )
    }

    if (showPanicActionDialog) {
        AlertDialog(
            onDismissRequest = { showPanicActionDialog = false },
            title = { Text("脱身响应动作") },
            text = {
                Column {
                    listOf("CALCULATOR" to "跳转伪装计算器 (可输入 PIN 解锁)", "HOME" to "迅速退回系统主屏幕", "KILL" to "直接销毁应用进程").forEach { (action, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setPanicAction(action)
                                    showPanicActionDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = panicAction == action,
                                onClick = {
                                    viewModel.setPanicAction(action)
                                    showPanicActionDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPanicActionDialog = false }) { Text(I18n.string("common.close")) }
            }
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = inputProvider,
                        onValueChange = { inputProvider = it },
                        label = { Text("服务商 (如 OpenAI, DeepSeek)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputBaseUrl,
                        onValueChange = { inputBaseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputApiKey,
                        onValueChange = { inputApiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputModel,
                        onValueChange = { inputModel = it },
                        label = { Text("Model (如 gpt-4o-mini, deepseek-chat)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("进入详情页自动触发翻译")
                        Switch(checked = inputAutoTranslate, onCheckedChange = { inputAutoTranslate = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
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
                        Toast.makeText(context, "AI 翻译配置已保存", Toast.LENGTH_SHORT).show()
                    }
                ) { Text(I18n.string("common.save")) }
            },
            dismissButton = {
                TextButton(onClick = { showLlmDialog = false }) { Text(I18n.string("common.cancel")) }
            }
        )
    }

    activeAppUpdateRelease?.let { releaseInfo ->
        com.gpdb.android.ui.components.AppUpdateDialog(
            releaseInfo = releaseInfo,
            onDismiss = { activeAppUpdateRelease = null }
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 26.dp, top = 2.dp)
        )
    }
}

@Composable
private fun SettingItemRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeSwatchesMini(theme: AppTheme) {
    val swatches = when (theme) {
        AppTheme.GLASS_DARK -> listOf(GlassDarkBg, GlassDarkSurface, GlassDarkPrimary)
        AppTheme.GLASS_LIGHT -> listOf(GlassLightBg, GlassLightSurface, GlassLightPrimary)
        AppTheme.CLASSIC_DARK -> listOf(ClassicDarkBg, ClassicDarkSurface, ClassicDarkPrimary)
        AppTheme.CLASSIC_LIGHT -> listOf(ClassicLightBg, ClassicLightSurface, ClassicLightPrimary)
        AppTheme.MY_DARK -> listOf(MyDarkBg, MyDarkSurface, MyDarkPrimary)
        AppTheme.MY_LIGHT -> listOf(MyLightBg, MyLightSurface, MyLightPrimary)
        AppTheme.AUTO -> listOf(Color(0xFF141416), Color(0xFFFFFFFF), Color(0xFFFCD34D))
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatches.forEach { c ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }
    }
}
