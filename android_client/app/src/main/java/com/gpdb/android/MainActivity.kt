package com.gpdb.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.preferences.MountPreferences
import com.gpdb.android.image.ZipHolder
import com.gpdb.android.ui.home.HomeScreen
import com.gpdb.android.ui.home.HomeViewModel
import com.gpdb.android.ui.setup.SetupScreen
import com.gpdb.android.ui.theme.GPDbTheme
import kotlinx.coroutines.launch
import java.io.File

// ============================================================
//  MainActivity — 核心主 Activity (全异步响应版)
// ============================================================
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private lateinit var mountPreferences: MountPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mountPreferences = MountPreferences(this)

        setContent {
            GPDbTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isMounted by mountPreferences.isMountedFlow.collectAsState(initial = false)
                    val mountRoot by mountPreferences.mountRootFlow.collectAsState(initial = null)
                    val dbPath by mountPreferences.dbPathFlow.collectAsState(initial = null)
                    val zipPath by mountPreferences.zipPathFlow.collectAsState(initial = null)

                    // 当检测到有效配置，交给 ViewModel 在 Dispatchers.IO 异步挂载
                    LaunchedEffect(isMounted, dbPath, zipPath, mountRoot) {
                        if (isMounted && dbPath != null && mountRoot != null) {
                            if (File(dbPath!!).exists()) {
                                homeViewModel.mountAndInitialize(
                                    context = this@MainActivity,
                                    mountRoot = mountRoot!!,
                                    dbPath = dbPath!!,
                                    zipPath = zipPath
                                )
                            }
                        }
                    }

                    if (isMounted) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onMovieClick = { movieId ->
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "点击了影视资源 ID: $movieId，详情页敬请期待",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRemountClick = {
                                lifecycleScope.launch {
                                    mountPreferences.clearMount()
                                    DatabaseHolder.release()
                                    ZipHolder.release()
                                }
                            }
                        )
                    } else {
                        SetupScreen(
                            mountPrefs = mountPreferences,
                            onMountComplete = {
                                // 挂载完成后 LaunchedEffect 会自动响应并调度挂载
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DatabaseHolder.release()
        ZipHolder.release()
    }
}
