package com.gpdb.android.data.scraper

import android.content.Context
import android.util.Log
import androidx.work.*
import com.gpdb.android.data.db.GpdbDatabase
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.preferences.MountPreferences
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * GPDb 静默后台自动同步与图库更新 Worker (Jetpack WorkManager)
 *
 * 调度策略：
 * - 仅在连接 Wi-Fi 且处于充电状态时执行
 * - 每日夜间定时静默检查官网发布
 * - 自动增量入库并预下载封面
 */
class GpdbSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "GpdbSyncWorker"
        const val WORK_NAME = "gpdb_periodic_incremental_sync"

        /**
         * 注册或更新每日后台静默同步任务 (建议在 Application 或设置开关中调用)
         */
        fun schedulePeriodicSync(context: Context, enabled: Boolean = true) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                Log.i(TAG, "已取消后台定时同步任务")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // 仅 Wi-Fi 网络
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<GpdbSyncWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 2,
                flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.i(TAG, "后台每日静默同步任务已成功排期 (Wi-Fi 网络)")
        }

        /**
         * 立即触发一次一次性后台同步任务 (用于用户点击手动同步)
         */
        fun triggerOneTimeSync(context: Context): Operation {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<GpdbSyncWorker>()
                .setConstraints(constraints)
                .build()

            return WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "开始执行 GPDb 增量数据与图库后台同步...")

        return try {
            val mountPreferences = MountPreferences(applicationContext)
            val dbPath = mountPreferences.dbPathFlow.first() ?: ""
            val physicalRoot = mountPreferences.mountRootFlow.first() ?: ""

            if (dbPath.isBlank()) {
                Log.w(TAG, "未配置数据库挂载路径，跳过同步")
                return Result.success()
            }

            val dbFile = File(dbPath)
            if (!dbFile.exists() || !dbFile.isFile) {
                Log.w(TAG, "数据库文件不存在: $dbPath")
                return Result.success()
            }

            val database = DatabaseHolder.db ?: GpdbDatabase.buildFromExternalFile(applicationContext, dbPath)
            val engine = GpdbScraperEngine(
                context = applicationContext,
                database = database,
                physicalRootPath = physicalRoot
            )

            val updates = engine.checkForUpdates()
            if (updates.totalCount > 0) {
                Log.i(TAG, "发现待同步新条目: 影片 ${updates.movieIds.size}, 分集 ${updates.episodeIds.size}")
                val syncResult = engine.syncUpdates(updates)
                Log.i(TAG, "后台同步完成: ${syncResult.message}")
            } else {
                Log.i(TAG, "本地已是最新，无新增条目")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "后台同步异常", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
