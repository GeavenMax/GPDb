package com.gpdb.android.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gpdb.android.image.PathResolver
import com.gpdb.android.data.db.SandboxDatabaseInitializer
import com.gpdb.android.data.preferences.MountPreferences
import kotlinx.coroutines.launch

// ============================================================
//  SetupScreen — 权限引导与目录挂载界面
//
//  流程图：
//  ┌──────────────────────────────────────────────────────┐
//  │  方案 A（推荐）：一键创建本地沙盒影库 (零外部依赖)     │
//  │  └─ 点击 → 创建私有沙盒 SQLite → 挂载进入应用        │
//  ├──────────────────────────────────────────────────────┤
//  │  方案 B：挂载已有数据包 (电脑端迁移)                 │
//  │  ├─ 步骤 1：检查 MANAGE_EXTERNAL_STORAGE             │
//  │  │   └─ 未授权 → [授权按钮] → 跳转系统设置           │
//  │  └─ 步骤 2：选择 GPDb 根目录                         │
//  │      ├─ [选择目录按钮] → ACTION_OPEN_DOCUMENT_TREE    │
//  │      └─ 校验并挂载 GPDb.db 与 GPDb_Images.zip         │
//  └──────────────────────────────────────────────────────┘
// ============================================================

@Composable
fun SetupScreen(
    mountPrefs: MountPreferences,
    onMountComplete: () -> Unit,
) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()

    // ── 状态 ────────────────────────────────────────────────
    var hasStoragePerm by remember {
        mutableStateOf(Environment.isExternalStorageManager())
    }
    var mountRootPath  by remember { mutableStateOf<String?>(null) }
    var dbExists       by remember { mutableStateOf(false) }
    var zipExists      by remember { mutableStateOf(false) }
    var errorMessage   by remember { mutableStateOf<String?>(null) }
    var isCreatingSandbox by remember { mutableStateOf(false) }

    val errorNoDirSelectedText = com.gpdb.android.util.I18n.string("setup.errorNoDirSelected")
    val errorUriParseText = com.gpdb.android.util.I18n.string("setup.errorUriParse")
    val errorMissingFilesText = com.gpdb.android.util.I18n.string("setup.errorMissingFiles")
    val errorMissingDbText = com.gpdb.android.util.I18n.string("setup.errorMissingDb")

    // ── 重新进入前台时刷新权限状态（用户在系统设置开启权限后返回）
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasStoragePerm = Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Launcher: ACTION_OPEN_DOCUMENT_TREE ─────────────────
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) {
            errorMessage = errorNoDirSelectedText
            return@rememberLauncherForActivityResult
        }

        // 从 content:// URI 提取树文档 ID，再还原为绝对物理路径
        val treeDocId = DocumentsContract.getTreeDocumentId(uri) ?: run {
            errorMessage = errorUriParseText
            return@rememberLauncherForActivityResult
        }
        val absPath = PathResolver.resolve(context, treeDocId) ?: run {
            errorMessage = errorUriParseText
            return@rememberLauncherForActivityResult
        }

        // 校验必要文件是否存在
        val (db, zip) = PathResolver.validateGpdbDirectory(absPath)
        mountRootPath = absPath
        dbExists      = db
        zipExists     = zip
        errorMessage  = when {
            !db && !zip -> errorMissingFilesText
            !db         -> errorMissingDbText
            else        -> null
        }

        // 只要 DB 存在就可以完成挂载（ZIP 缺失仅影响图片，不阻塞启动）
        if (db) {
            scope.launch {
                mountPrefs.saveMountRoot(absPath)
                onMountComplete()
            }
        }
    }

    // ── UI ──────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // App 图标 + 标题
            Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = com.gpdb.android.util.I18n.string("setup.welcome"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = com.gpdb.android.util.I18n.string("setup.welcomeDesc"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            // ── 推荐：全新用户免配置开箱 ──────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = com.gpdb.android.util.I18n.string("setup.onboarding"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = com.gpdb.android.util.I18n.string("setup.onboardingDesc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isCreatingSandbox = true
                                try {
                                    val sandboxDir = SandboxDatabaseInitializer.initializeSandbox(context)
                                    mountPrefs.saveMountRoot(sandboxDir.absolutePath)
                                    onMountComplete()
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                } finally {
                                    isCreatingSandbox = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreatingSandbox
                    ) {
                        if (isCreatingSandbox) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(com.gpdb.android.util.I18n.string("setup.creatingDatabase"))
                        } else {
                            Icon(Icons.Outlined.Bolt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(com.gpdb.android.util.I18n.string("setup.createSandbox"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = com.gpdb.android.util.I18n.string("setup.orImportFromPc"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // ── 步骤 1：文件访问权限 ─────────────────────────
            SetupStep(
                step = 1,
                title = com.gpdb.android.util.I18n.string("setup.grantStoragePermission"),
                description = com.gpdb.android.util.I18n.string("setup.grantStoragePermissionDesc"),
                isDone = hasStoragePerm,
            ) {
                if (!hasStoragePerm) {
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(com.gpdb.android.util.I18n.string("setup.openSystemSettings"))
                    }
                }
            }

            // ── 步骤 2：选择挂载目录 ─────────────────────────
            SetupStep(
                step = 2,
                title = com.gpdb.android.util.I18n.string("setup.chooseGpdbFolder"),
                description = com.gpdb.android.util.I18n.string("setup.chooseGpdbFolderDesc"),
                isDone = mountRootPath != null && dbExists,
                isEnabled = hasStoragePerm,
            ) {
                if (hasStoragePerm && (mountRootPath == null || !dbExists)) {
                    Button(
                        onClick = { dirPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(com.gpdb.android.util.I18n.string("setup.chooseFolderButton"))
                    }
                }

                // 已选目录的状态反馈
                AnimatedVisibility(
                    visible = mountRootPath != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut(),
                ) {
                    mountRootPath?.let { path ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FileStatusRow("GPDb.db", dbExists)
                            FileStatusRow("GPDb_Images.zip", zipExists)
                        }
                    }
                }
            }

            // 错误提示
            errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ── 子组件：步骤卡片 ──────────────────────────────────────────

@Composable
private fun SetupStep(
    step: Int,
    title: String,
    description: String,
    isDone: Boolean,
    isEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerColor = when {
        isDone      -> MaterialTheme.colorScheme.primaryContainer
        !isEnabled  -> MaterialTheme.colorScheme.surfaceVariant
        else        -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEnabled && !isDone) 2.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 步骤编号 / 完成对勾
                if (isDone) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = com.gpdb.android.util.I18n.string("setup.done"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(28.dp),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = step.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled || isDone) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun FileStatusRow(filename: String, exists: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (exists) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (exists) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.error,
        )
        Text(
            text = filename,
            style = MaterialTheme.typography.labelMedium,
            color = if (exists) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
        )
    }
}
