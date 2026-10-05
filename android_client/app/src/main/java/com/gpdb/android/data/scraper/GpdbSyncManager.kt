package com.gpdb.android.data.scraper

import android.content.Context
import android.util.Log
import com.gpdb.android.data.db.GpdbDatabase
import com.gpdb.android.data.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * 全局同步任务管理器 (GpdbSyncManager)
 *
 * 统一管理启动后台无感增量更新、定时后台更新与设置页手动更新：
 * - 避免多源并发触发导致的数据库读写锁竞争
 * - 维护 15 分钟冷启动防抖冷却，防止频繁杀进程重启造成网络与端侧性能开销
 * - 集中广播同步进度 StateFlow，前台设置界面可实时感知后台进度
 */
object GpdbSyncManager {
    private const val TAG = "GpdbSyncManager"
    private const val STARTUP_COOLDOWN_MS = 15 * 60 * 1000L // 15 分钟冷却

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Checking : SyncStatus()
        data class Discovered(val updates: DiscoveredUpdates) : SyncStatus()
        data class Syncing(val current: Int, val total: Int, val currentItem: String) : SyncStatus()
        data class Completed(val result: SyncResult) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val syncMutex = Mutex()
    private var lastStartupSyncTime = 0L

    /**
     * 应用启动完成后的无感后台增量更新
     */
    suspend fun triggerStartupSync(
        context: Context,
        database: GpdbDatabase,
        physicalRoot: String,
        onNewContentAdded: ((Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val appSettings = AppSettingsRepository(context)
        val enabled = appSettings.autoSyncOnLaunchFlow.first()
        if (!enabled) {
            Log.d(TAG, "启动时自动增量同步已由用户关闭，跳过")
            return@withContext
        }

        val now = System.currentTimeMillis()
        if (now - lastStartupSyncTime < STARTUP_COOLDOWN_MS) {
            Log.d(TAG, "距上次同步不足 15 分钟，跳过自动增量检查")
            return@withContext
        }

        if (!syncMutex.tryLock()) {
            Log.d(TAG, "已有同步任务正在运行，跳过本次触发")
            return@withContext
        }

        try {
            Log.i(TAG, "启动后台无感增量检查...")
            _syncStatus.value = SyncStatus.Checking

            val engine = GpdbScraperEngine(context, database, physicalRoot)
            val updates = engine.checkForUpdates()
            lastStartupSyncTime = System.currentTimeMillis()

            if (updates.totalCount > 0) {
                Log.i(TAG, "发现官网新内容: 待同步 ${updates.totalCount} 项")
                _syncStatus.value = SyncStatus.Syncing(0, updates.totalCount, "准备中...")
                val result = engine.syncUpdates(updates) { current, total, name ->
                    _syncStatus.value = SyncStatus.Syncing(current, total, name)
                }
                _syncStatus.value = SyncStatus.Completed(result)
                val totalNew = result.newMoviesCount + result.newEpisodesCount + result.newPerformersCount
                if (totalNew > 0) {
                    onNewContentAdded?.invoke(totalNew)
                }
            } else {
                Log.i(TAG, "本地已是最新，无待同步项")
                _syncStatus.value = SyncStatus.Idle
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动后台同步异常", e)
            _syncStatus.value = SyncStatus.Error("后台同步异常: ${e.message}")
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * 手动触发检查更新
     */
    suspend fun checkForUpdates(
        context: Context,
        database: GpdbDatabase,
        physicalRoot: String
    ): DiscoveredUpdates = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            throw IllegalStateException("已有同步任务正在进行中，请稍候")
        }
        try {
            _syncStatus.value = SyncStatus.Checking
            val engine = GpdbScraperEngine(context, database, physicalRoot)
            val updates = engine.checkForUpdates()
            _syncStatus.value = SyncStatus.Discovered(updates)
            updates
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "检查更新失败")
            throw e
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * 手动开始执行同步
     */
    suspend fun startManualSync(
        context: Context,
        database: GpdbDatabase,
        physicalRoot: String,
        updates: DiscoveredUpdates,
        onNewContentAdded: ((Int) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            throw IllegalStateException("已有同步任务正在进行中")
        }
        try {
            val engine = GpdbScraperEngine(context, database, physicalRoot)
            _syncStatus.value = SyncStatus.Syncing(0, updates.totalCount, "准备中...")
            val result = engine.syncUpdates(updates) { current, total, name ->
                _syncStatus.value = SyncStatus.Syncing(current, total, name)
            }
            lastStartupSyncTime = System.currentTimeMillis()
            _syncStatus.value = SyncStatus.Completed(result)
            val totalNew = result.newMoviesCount + result.newEpisodesCount + result.newPerformersCount
            if (totalNew > 0) {
                onNewContentAdded?.invoke(totalNew)
            }
            result
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "同步中断")
            throw e
        } finally {
            syncMutex.unlock()
        }
    }

    fun resetStatus() {
        _syncStatus.value = SyncStatus.Idle
    }
}
