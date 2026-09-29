package com.gpdb.android

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
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
import com.gpdb.android.data.preferences.AppPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import com.gpdb.android.image.ZipHolder
import com.gpdb.android.ui.home.HomeScreen
import com.gpdb.android.ui.home.HomeViewModel
import com.gpdb.android.ui.setup.SetupScreen
import com.gpdb.android.ui.theme.GPDbTheme
import kotlinx.coroutines.launch
import java.io.File
import androidx.fragment.app.FragmentActivity
import com.gpdb.android.data.settings.AppSettingsRepository
import com.gpdb.android.data.settings.ThemeMode
import com.gpdb.android.ui.lock.AppLockOverlay
import com.gpdb.android.ui.lock.FakeCalculatorScreen
import com.gpdb.android.util.PanicSensorManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class MainActivity : FragmentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private lateinit var mountPreferences: MountPreferences
    private lateinit var appPreferences: AppPreferences
    private lateinit var appSettingsRepository: AppSettingsRepository

    private lateinit var panicSensorManager: PanicSensorManager
    private val isInFakeCalculatorMode = mutableStateOf(false)
    private val isAppLocked = mutableStateOf(false)
    private var lastBackgroundTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mountPreferences = MountPreferences(this)
        appPreferences = AppPreferences(this)
        appSettingsRepository = AppSettingsRepository(this)
        com.gpdb.android.util.PrivacyHelper.initSandboxPrivacy(this)

        // 1. 传感器紧急脱身监听
        panicSensorManager = PanicSensorManager(this) {
            handlePanicTriggered()
        }

        // 2. 动态响应 FLAG_SECURE 防截屏与多任务防窥
        lifecycleScope.launch {
            appSettingsRepository.flagSecureEnabledFlow.collect { enabled ->
                if (enabled) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        // 3. 观察脱身传感器配置
        lifecycleScope.launch {
            combine(
                appSettingsRepository.panicSwitchEnabledFlow,
                appSettingsRepository.panicFaceDownEnabledFlow,
                appSettingsRepository.panicShakeEnabledFlow
            ) { enabled, faceDown, shake ->
                panicSensorManager.enableFaceDown = faceDown
                panicSensorManager.enableShake = shake
                enabled
            }.collect { enabled ->
                if (enabled) {
                    panicSensorManager.startListening()
                } else {
                    panicSensorManager.stopListening()
                }
            }
        }

        // 4. 冷启动检查应用锁
        lifecycleScope.launch {
            val lockEnabled = appSettingsRepository.appLockEnabledFlow.first()
            if (lockEnabled) {
                isAppLocked.value = true
            }
        }

        setContent {
            val themeMode by appSettingsRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val dynamicColor by appSettingsRepository.dynamicColorFlow.collectAsState(initial = true)
            val isSystemDark = isSystemInDarkTheme()
            
            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemDark
            }
            
            GPDbTheme(darkTheme = useDarkTheme, dynamicColor = dynamicColor) {
                val inFakeCalc by isInFakeCalculatorMode
                val locked by isAppLocked
                val currentPin by appSettingsRepository.appLockPinFlow.collectAsState(initial = "")
                val biometricEnabled by appSettingsRepository.appLockBiometricEnabledFlow.collectAsState(initial = true)

                if (inFakeCalc) {
                    FakeCalculatorScreen(
                        unlockPin = currentPin,
                        onUnlock = {
                            isInFakeCalculatorMode.value = false
                        }
                    )
                } else if (locked) {
                    AppLockOverlay(
                        correctPin = currentPin,
                        biometricEnabled = biometricEnabled,
                        onUnlocked = {
                            isAppLocked.value = false
                        }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val isMounted by mountPreferences.isMountedFlow.collectAsState(initial = false)
                        val mountRoot by mountPreferences.mountRootFlow.collectAsState(initial = null)
                        val dbPath by mountPreferences.dbPathFlow.collectAsState(initial = null)
                        val zipPath by mountPreferences.zipPathFlow.collectAsState(initial = null)

                        // Apply locale
                        val langPref by appPreferences.languageFlow.collectAsState(initial = "system")
                        LaunchedEffect(langPref) {
                            val localeList = if (langPref == "system") {
                                androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                            } else {
                                androidx.core.os.LocaleListCompat.forLanguageTags(langPref)
                            }
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
                        }

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
                            com.gpdb.android.ui.navigation.GpdbNavGraph(
                                homeViewModel = homeViewModel,
                                physicalRootPath = mountRoot ?: "",
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
    }

    private fun handlePanicTriggered() {
        lifecycleScope.launch {
            val action = appSettingsRepository.panicActionFlow.first()
            when (action) {
                "HOME" -> {
                    moveTaskToBack(true)
                }
                "KILL" -> {
                    finishAffinity()
                }
                else -> {
                    // CALCULATOR 伪装计算器模式
                    isInFakeCalculatorMode.value = true
                    isAppLocked.value = true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (appSettingsRepository.panicSwitchEnabledFlow.first()) {
                panicSensorManager.startListening()
            }
            val lockEnabled = appSettingsRepository.appLockEnabledFlow.first()
            if (lockEnabled && !isAppLocked.value && lastBackgroundTimestamp > 0L) {
                val timeoutSec = appSettingsRepository.appLockTimeoutSecondsFlow.first()
                val elapsedSec = (System.currentTimeMillis() - lastBackgroundTimestamp) / 1000L
                if (elapsedSec >= timeoutSec) {
                    isAppLocked.value = true
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lastBackgroundTimestamp = System.currentTimeMillis()
        panicSensorManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        panicSensorManager.stopListening()
        DatabaseHolder.release()
        ZipHolder.release()
    }
}
