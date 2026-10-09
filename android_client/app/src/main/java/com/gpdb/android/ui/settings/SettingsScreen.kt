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
import kotlinx.coroutines.withContext

enum class SettingsTab(val labelZh: String, val labelZhTw: String, val labelEn: String, val icon: ImageVector) {
    ALL("全部", "全部", "All", Icons.Default.Settings),
    APPEARANCE("外观", "外觀", "Appearance", Icons.Default.Palette),
    PRIVACY("隐私安全", "隱私安全", "Privacy", Icons.Default.Security),
    TRANSLATE("AI 翻译", "AI 翻譯", "Translation", Icons.Default.Translate),
    SYNC("同步存储", "同步儲存", "Sync & Storage", Icons.Default.Storage),
    ABOUT("关于与更新", "關於與更新", "About & Updates", Icons.Default.Info);

    @Composable
    fun getLabel(): String {
        val lang = com.gpdb.android.util.LocalAppLanguage.current
        return when {
            lang == com.gpdb.android.util.AppLanguage.ZH_CN -> labelZh
            lang == com.gpdb.android.util.AppLanguage.ZH_TW -> labelZhTw
            else -> labelEn
        }
    }
}

fun changeAppIcon(context: Context, scope: CoroutineScope, newIcon: String, updatedToastMessage: String) {
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

    Toast.makeText(context, updatedToastMessage, Toast.LENGTH_LONG).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    appSettingsRepository: AppSettingsRepository,
    onRemountClick: () -> Unit,
    onBackClick: (() -> Unit)? = null
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

    val appIconUpdatedToast = I18n.string("settings.appIconUpdated")
    val translationStatsRefreshedToast = I18n.string("settings.translationStatsRefreshed")
    val alreadyLatestVersionToast = I18n.string("settings.alreadyLatestVersion")
    val updateCheckFailedToast = I18n.string("update.checkFailed")
    val pinSavedToast = I18n.string("settings.pinSaved")
    val pinMinLengthToast = I18n.string("settings.pinMinLength")
    val aiConfigSavedToast = I18n.string("settings.aiConfigSaved")
    val currentAppLanguage = com.gpdb.android.util.LocalAppLanguage.current

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
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SettingsTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.getLabel(), style = MaterialTheme.typography.labelMedium) },
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
                                subtitle = currentTheme.getLabel(),
                                icon = Icons.Default.Brightness4,
                        onClick = { showThemeDialog = true }
                    ) {
                        ThemeSwatchesMini(theme = currentTheme)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    val currentAppLang = AppLanguage.fromCode(language)
                    SettingItemRow(
                        title = I18n.string("settings.language"),
                        subtitle = "${currentAppLang.getDisplayName()} (${currentAppLang.nativeName})",
                        icon = Icons.Default.Language,
                        onClick = { showLangDialog = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingItemRow(
                        title = I18n.string("settings.appIcon"),
                        subtitle = when (appIcon) {
                            "D" -> I18n.string("settings.iconSchemeD")
                            else -> I18n.string("settings.iconSchemeA")
                        },
                        icon = Icons.Default.AppShortcut,
                        onClick = { showIconDialog = true }
                    )

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        SettingSwitchRow(
                            title = I18n.string("settings.dynamicColor"),
                            subtitle = I18n.string("settings.dynamicColorDesc"),
                            icon = Icons.Default.ColorLens,
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                }
            }
        }

        // ── Section 2: 隐私与安全 ─────────────────────────────────
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
                        title = I18n.string("settings.searchHistoryToggle"),
                        subtitle = I18n.string("settings.searchHistoryDesc"),
                        icon = Icons.Default.History,
                        checked = recordHistory,
                        onCheckedChange = { viewModel.setRecordHistory(it) }
                    )

                    val searchClearedToast = I18n.string("common.searchHistoryCleared", defaultVal = "Search history cleared")
                    SettingItemRow(
                        title = I18n.string("settings.clearSearchHistory"),
                        subtitle = I18n.string("settings.searchHistoryDesc"),
                        icon = Icons.Default.DeleteSweep,
                        onClick = {
                            scope.launch {
                                appPreferences.clearSearchHistory()
                                Toast.makeText(context, searchClearedToast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                    SettingSwitchRow(
                        title = I18n.string("settings.flagSecure"),
                        subtitle = I18n.string("settings.flagSecureSubtitle"),
                        icon = Icons.Default.ScreenLockPortrait,
                        checked = flagSecureEnabled,
                        onCheckedChange = { viewModel.setFlagSecureEnabled(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = I18n.string("settings.privacyBlur"),
                        subtitle = if (screenshotPrivacyBlurEnabled) I18n.string("settings.privacyBlurActive") else I18n.string("settings.privacyBlurInactive"),
                        icon = Icons.Default.BlurOn,
                        checked = screenshotPrivacyBlurEnabled,
                        onCheckedChange = { viewModel.setScreenshotPrivacyBlurEnabled(it) }
                    )

                    if (screenshotPrivacyBlurEnabled) {
                        SettingSwitchRow(
                            title = I18n.string("settings.privacyBlurImages"),
                            subtitle = I18n.string("settings.privacyBlurImagesDesc"),
                            icon = Icons.Default.Image,
                            checked = screenshotPrivacyBlurImages,
                            onCheckedChange = { viewModel.setScreenshotPrivacyBlurImages(it) }
                        )
                        SettingSwitchRow(
                            title = I18n.string("settings.privacyBlurText"),
                            subtitle = I18n.string("settings.privacyBlurTextDesc"),
                            icon = Icons.Default.TextFields,
                            checked = screenshotPrivacyBlurText,
                            onCheckedChange = { viewModel.setScreenshotPrivacyBlurText(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = I18n.string("settings.appLock"),
                        subtitle = if (appLockEnabled) I18n.string("settings.appLockEnabledStatus", mapOf("seconds" to appLockTimeoutSeconds.toString())) else I18n.string("settings.appLockDisabledStatus"),
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
                            title = I18n.string("settings.changePin"),
                            subtitle = if (appLockPin.isNotBlank()) I18n.string("settings.pinConfigured") else I18n.string("settings.pinNotConfigured"),
                            icon = Icons.Default.Pin,
                            onClick = { showPinDialog = true }
                        )
                        SettingSwitchRow(
                            title = I18n.string("settings.allowBiometrics"),
                            subtitle = I18n.string("settings.allowBiometricsDesc"),
                            icon = Icons.Default.Fingerprint,
                            checked = appLockBiometricEnabled,
                            onCheckedChange = { viewModel.setAppLockBiometricEnabled(it) }
                        )
                        SettingItemRow(
                            title = I18n.string("settings.lockTimeoutTitle"),
                            subtitle = I18n.string("settings.lockTimeoutSecondsDesc", mapOf("seconds" to appLockTimeoutSeconds.toString())),
                            icon = Icons.Default.Timer,
                            onClick = { showTimeoutDialog = true }
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    val panicSubtitle = if (panicSwitchEnabled) {
                        val actionDesc = when(panicAction) {
                            "CALCULATOR" -> I18n.string("settings.panicActionCalculator")
                            "HOME" -> I18n.string("settings.panicActionHome")
                            "KILL" -> I18n.string("settings.panicActionKill")
                            else -> I18n.string("settings.panicActionCalculator")
                        }
                        "${I18n.string("common.enabled")}: $actionDesc"
                    } else {
                        I18n.string("common.disabled")
                    }

                    SettingSwitchRow(
                        title = I18n.string("settings.panicSwitch"),
                        subtitle = panicSubtitle,
                        icon = Icons.Default.WarningAmber,
                        checked = panicSwitchEnabled,
                        onCheckedChange = { viewModel.setPanicSwitchEnabled(it) }
                    )

                    if (panicSwitchEnabled) {
                        SettingSwitchRow(
                            title = I18n.string("settings.panicFaceDown"),
                            subtitle = I18n.string("settings.panicFaceDownDesc"),
                            icon = Icons.Default.ScreenRotation,
                            checked = panicFaceDownEnabled,
                            onCheckedChange = { viewModel.setPanicFaceDownEnabled(it) }
                        )
                        SettingSwitchRow(
                            title = I18n.string("settings.panicShake"),
                            subtitle = I18n.string("settings.panicShakeDesc"),
                            icon = Icons.Default.Vibration,
                            checked = panicShakeEnabled,
                            onCheckedChange = { viewModel.setPanicShakeEnabled(it) }
                        )
                        SettingItemRow(
                            title = I18n.string("settings.panicActionTitle"),
                            subtitle = when(panicAction) {
                                "CALCULATOR" -> I18n.string("settings.panicActionCalculator")
                                "HOME" -> I18n.string("settings.panicActionHome")
                                "KILL" -> I18n.string("settings.panicActionKill")
                                else -> I18n.string("settings.panicActionCalculator")
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
                            text = I18n.string("settings.sandboxSecurityNotice"),
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
                title = I18n.string("settings.aiSectionTitle"),
                subtitle = I18n.string("settings.aiSectionSubtitle")
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    val configuredSuffix = I18n.string("settings.aiConfigured")
                    SettingItemRow(
                        title = I18n.string("settings.aiProviderConfig"),
                        subtitle = if (llmApiKey.isNotBlank()) "$llmProvider · $llmModel ($configuredSuffix)" else I18n.string("settings.aiClickToConfig"),
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
                                text = I18n.string("settings.translationProgress"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val progressSummary = if (currentAppLanguage.isChinese) {
                                "长片: $translatedMovies / $translatableMovies · 分集: $translatedEpisodes / $translatableEpisodes"
                            } else {
                                "Movies: $translatedMovies / $translatableMovies · Episodes: $translatedEpisodes / $translatableEpisodes"
                            }
                            Text(
                                text = progressSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.refreshStats()
                                Toast.makeText(context, translationStatsRefreshedToast, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(I18n.string("common.refresh"))
                        }
                    }
                }
            }
        }

        // ── Section 5: 同步与数据存储 ───────────────────────────────
        if (selectedTab == SettingsTab.ALL || selectedTab == SettingsTab.SYNC) {
            SectionHeader(
                icon = Icons.Default.Storage,
                title = I18n.string("settings.syncSectionTitle"),
                subtitle = I18n.string("settings.syncSectionSubtitle")
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    val syncStatus by viewModel.syncStatus.collectAsState()
                    val autoSyncOnLaunchEnabled by viewModel.autoSyncOnLaunchEnabled.collectAsState()
                    val periodicSyncEnabled by viewModel.periodicSyncEnabled.collectAsState()

                    SettingSwitchRow(
                        title = I18n.string("settings.autoSyncTitle"),
                        subtitle = I18n.string("settings.autoSyncSubtitle"),
                        icon = Icons.Default.CloudSync,
                        checked = autoSyncOnLaunchEnabled,
                        onCheckedChange = { viewModel.setAutoSyncOnLaunchEnabled(it) }
                    )

                    SettingSwitchRow(
                        title = I18n.string("settings.scheduleSyncTitle"),
                        subtitle = I18n.string("settings.scheduleSyncSubtitle"),
                        icon = Icons.Default.Sync,
                        checked = periodicSyncEnabled,
                        onCheckedChange = { viewModel.setPeriodicSyncEnabled(it, context) }
                    )

                    val saveImagesToExternal by viewModel.saveImagesToExternal.collectAsState()

                    SettingSwitchRow(
                        title = I18n.string("settings.cacheSyncToPublicTitle"),
                        subtitle = I18n.string("settings.cacheSyncToPublicSubtitle"),
                        icon = Icons.Default.FolderOpen,
                        checked = saveImagesToExternal,
                        onCheckedChange = { viewModel.setSaveImagesToExternal(it) }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    Column(modifier = Modifier.padding(14.dp)) {
                        when (val status = syncStatus) {
                            is com.gpdb.android.data.scraper.GpdbSyncManager.SyncStatus.Idle -> {
                                OutlinedButton(
                                    onClick = { viewModel.checkForUpdates(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(I18n.string("settings.checkNewEntries"))
                                }
                            }
                            is com.gpdb.android.data.scraper.GpdbSyncManager.SyncStatus.Checking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(I18n.string("settings.checkingUpdates"), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is com.gpdb.android.data.scraper.GpdbSyncManager.SyncStatus.Discovered -> {
                                val updates = status.updates
                                if (updates.totalCount == 0) {
                                    Text(I18n.string("settings.alreadyLatestData"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    val discText = if (currentAppLanguage.isChinese) {
                                        "发现 ${updates.totalCount} 项待入库 (长片: ${updates.movieIds.size}, 分集: ${updates.episodeIds.size}, 演员: ${updates.performerIds.size})"
                                    } else {
                                        "Found ${updates.totalCount} new entries (Movies: ${updates.movieIds.size}, Episodes: ${updates.episodeIds.size}, Performers: ${updates.performerIds.size})"
                                    }
                                    Text(discText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.startSync(context, updates) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(I18n.string("settings.syncNowAndScrape"))
                                    }
                                }
                            }
                            is com.gpdb.android.data.scraper.GpdbSyncManager.SyncStatus.Syncing -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val syncingLabel = if (currentAppLanguage.isChinese) "同步中" else "Syncing"
                                    Text("$syncingLabel: ${status.current}/${status.total} · ${status.currentItem}", style = MaterialTheme.typography.bodySmall)
                                    LinearProgressIndicator(
                                        progress = { if (status.total > 0) status.current.toFloat() / status.total else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            is com.gpdb.android.data.scraper.GpdbSyncManager.SyncStatus.Completed -> {
                                val totalNew = status.result.newMoviesCount + status.result.newEpisodesCount + status.result.newPerformersCount
                                val compText = if (currentAppLanguage.isChinese) {
                                    "同步完成！新增 $totalNew 项 (长片: ${status.result.newMoviesCount}, 分集: ${status.result.newEpisodesCount}, 演员: ${status.result.newPerformersCount})，缓存图片: ${status.result.cachedImagesCount}"
                                } else {
                                    "Sync completed! Added $totalNew items (Movies: ${status.result.newMoviesCount}, Episodes: ${status.result.newEpisodesCount}, Performers: ${status.result.newPerformersCount}), Cached: ${status.result.cachedImagesCount}"
                                }
                                Text(compText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { viewModel.resetSyncStatus() }) {
                                    Text(I18n.string("settings.syncCompleted"))
                                }
                            }
                            is com.gpdb.android.data.scraper.GpdbSyncManager.SyncStatus.Error -> {
                                val errLabel = if (currentAppLanguage.isChinese) "同步异常" else "Sync error"
                                Text("$errLabel: ${status.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { viewModel.resetSyncStatus() }) {
                                    Text(I18n.string("common.retry"))
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingItemRow(
                        title = I18n.string("settings.remountDb"),
                        subtitle = I18n.string("settings.remountDbSubtitle"),
                        icon = Icons.Default.FolderOpen,
                        onClick = onRemountClick
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingItemRow(
                        title = I18n.string("settings.exportUserBackup"),
                        subtitle = I18n.string("settings.exportUserBackupSubtitle"),
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                            val filename = "GPDb_Backup_${sdf.format(java.util.Date())}.json"
                            exportLauncher.launch(filename)
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    SettingItemRow(
                        title = I18n.string("settings.importUserBackup"),
                        subtitle = I18n.string("settings.importUserBackupSubtitle"),
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
                title = I18n.string("settings.aboutSectionTitle"),
                subtitle = "v${com.gpdb.android.BuildConfig.VERSION_NAME} · ${I18n.string("settings.aboutVersionSubtitle")}"
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingItemRow(
                        title = I18n.string("settings.checkAppUpdate"),
                        subtitle = if (isCheckingAppUpdate) I18n.string("settings.checkingAppUpdate") else I18n.string("settings.checkAppUpdatePrompt"),
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
                                            Toast.makeText(context, "$alreadyLatestVersionToast (v${res.currentVersion})", Toast.LENGTH_SHORT).show()
                                        }
                                        is com.gpdb.android.util.AppUpdateCheckResult.Error -> {
                                            isCheckingAppUpdate = false
                                            Toast.makeText(context, "$updateCheckFailedToast: ${res.message}", Toast.LENGTH_LONG).show()
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
                                text = I18n.string("settings.openRepo"),
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
                            Text(I18n.string("settings.visitRepo"))
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
            AppTheme.AUTO,
            AppTheme.GLASS_DARK,
            AppTheme.GLASS_LIGHT,
            AppTheme.CLASSIC_DARK,
            AppTheme.CLASSIC_LIGHT,
            AppTheme.MY_DARK,
            AppTheme.MY_LIGHT
        )
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(I18n.string("settings.theme")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    themes.forEach { theme ->
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
                            Text(theme.getLabel(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
                                Text(lang.getDisplayName(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                        "A" to I18n.string("settings.iconSchemeA"),
                        "D" to I18n.string("settings.iconSchemeD")
                    ).forEach { (iconKey, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setAppIcon(iconKey)
                                    changeAppIcon(context, scope, iconKey, appIconUpdatedToast)
                                    showIconDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = appIcon == iconKey,
                                onClick = {
                                    viewModel.setAppIcon(iconKey)
                                    changeAppIcon(context, scope, iconKey, appIconUpdatedToast)
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
            title = { Text(I18n.string("settings.securityPinTitle")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(I18n.string("settings.securityPinPrompt"), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) inputPin = it },
                        label = { Text(I18n.string("settings.pinLabel")) },
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
                            Toast.makeText(context, pinSavedToast, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, pinMinLengthToast, Toast.LENGTH_SHORT).show()
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
            title = { Text(I18n.string("settings.lockTimeoutTitle")) },
            text = {
                Column {
                    listOf(
                        0 to I18n.string("settings.timeoutImmediately"),
                        15 to I18n.string("settings.timeout15s"),
                        30 to I18n.string("settings.timeout30s"),
                        60 to I18n.string("settings.timeout1m"),
                        300 to I18n.string("settings.timeout5m")
                    ).forEach { (sec, label) ->
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
            title = { Text(I18n.string("settings.panicActionTitle")) },
            text = {
                Column {
                    listOf(
                        "CALCULATOR" to I18n.string("settings.panicCalc"),
                        "HOME" to I18n.string("settings.panicHome"),
                        "KILL" to I18n.string("settings.panicKill")
                    ).forEach { (action, label) ->
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
        data class LlmPreset(val name: String, val provider: String, val baseUrl: String, val model: String)
        val presets = listOf(
            LlmPreset("DeepSeek V3", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
            LlmPreset("OpenAI (GPT-4o Mini)", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            LlmPreset("Claude 3.5 Haiku", "Anthropic / Claude", "https://api.anthropic.com/v1", "claude-3-5-haiku-20241022"),
            LlmPreset("Google Gemini 2.5 Flash", "Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"),
            LlmPreset("SiliconFlow 硅基流动", "SiliconFlow", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3"),
            LlmPreset("Ollama 本地守护进程", "Ollama (Local)", "http://127.0.0.1:11434/v1", "qwen2.5:7b")
        )

        var inputProvider by remember { mutableStateOf(llmProvider) }
        var inputApiKey by remember { mutableStateOf(llmApiKey) }
        var inputBaseUrl by remember { mutableStateOf(llmBaseUrl) }
        var inputModel by remember { mutableStateOf(llmModel) }
        var inputAutoTranslate by remember { mutableStateOf(llmAutoTranslate) }
        var inputTargetLanguage by remember { mutableStateOf(llmTargetLanguage) }
        var inputSystemPrompt by remember { mutableStateOf(llmSystemPrompt) }

        AlertDialog(
            onDismissRequest = { showLlmDialog = false },
            title = { Text(I18n.string("settings.aiConfigTitle")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "选择预设服务商（快速填入 Base URL 与推荐模型）：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = inputBaseUrl == preset.baseUrl,
                                onClick = {
                                    inputProvider = preset.provider
                                    inputBaseUrl = preset.baseUrl
                                    inputModel = preset.model
                                },
                                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                    OutlinedTextField(
                        value = inputApiKey,
                        onValueChange = { inputApiKey = it },
                        label = { Text("API Key (密钥)") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 智能模型自动拉取与下拉选择器
                    var detectedModels by remember { mutableStateOf<List<String>>(emptyList()) }
                    var isDetectingModels by remember { mutableStateOf(false) }
                    var detectError by remember { mutableStateOf<String?>(null) }
                    var showModelDropdown by remember { mutableStateOf(false) }

                    val coroutineScope = rememberCoroutineScope()

                    fun fetchAvailableModels() {
                        if (inputBaseUrl.isBlank()) return
                        isDetectingModels = true
                        detectError = null
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val urlStr = if (inputBaseUrl.endsWith("/")) "${inputBaseUrl}models" else "${inputBaseUrl}/models"
                                val url = java.net.URL(urlStr)
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.connectTimeout = 8000
                                conn.readTimeout = 8000
                                if (inputApiKey.isNotBlank()) {
                                    conn.setRequestProperty("Authorization", "Bearer ${inputApiKey.trim()}")
                                }
                                if (conn.responseCode in 200..299) {
                                    val stream = conn.inputStream
                                    val text = stream.bufferedReader().use { it.readText() }
                                    val json = org.json.JSONObject(text)
                                    val dataArray = json.optJSONArray("data")
                                    val modelsList = mutableListOf<String>()
                                    if (dataArray != null) {
                                        for (i in 0 until dataArray.length()) {
                                            val obj = dataArray.getJSONObject(i)
                                            val id = obj.optString("id")
                                            if (!id.isNullOrBlank()) {
                                                modelsList.add(id)
                                            }
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        detectedModels = modelsList.sorted()
                                        isDetectingModels = false
                                        if (modelsList.isNotEmpty() && (inputModel.isBlank() || !modelsList.contains(inputModel))) {
                                            inputModel = modelsList.first()
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        isDetectingModels = false
                                        detectError = "HTTP ${conn.responseCode}"
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isDetectingModels = false
                                    detectError = e.message ?: "无法连接"
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { fetchAvailableModels() },
                            enabled = !isDetectingModels && inputBaseUrl.isNotBlank()
                        ) {
                            if (isDetectingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("正在拉取模型库...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text("🔍 自动检测可用模型列表", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (detectError != null) {
                            Text(detectError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = showModelDropdown && detectedModels.isNotEmpty(),
                        onExpandedChange = { showModelDropdown = !showModelDropdown }
                    ) {
                        OutlinedTextField(
                            value = inputModel,
                            onValueChange = { inputModel = it },
                            label = { Text(I18n.string("settings.modelPlaceholder")) },
                            trailingIcon = {
                                if (detectedModels.isNotEmpty()) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelDropdown)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        if (detectedModels.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = showModelDropdown,
                                onDismissRequest = { showModelDropdown = false }
                            ) {
                                detectedModels.forEach { modelId ->
                                    DropdownMenuItem(
                                        text = { Text(modelId, style = MaterialTheme.typography.bodySmall) },
                                        onClick = {
                                            inputModel = modelId
                                            showModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputProvider,
                        onValueChange = { inputProvider = it },
                        label = { Text(I18n.string("settings.providerPlaceholder")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputBaseUrl,
                        onValueChange = { inputBaseUrl = it },
                        label = { Text("Base URL (接口基址)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(I18n.string("settings.autoTranslateDetail"), style = MaterialTheme.typography.bodyMedium)
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
                        Toast.makeText(context, aiConfigSavedToast, Toast.LENGTH_SHORT).show()
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
