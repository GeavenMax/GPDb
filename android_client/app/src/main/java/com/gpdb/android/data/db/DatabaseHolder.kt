package com.gpdb.android.data.db

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// ============================================================
//  DatabaseHolder — 数据库单例生命周期管理 (协程安全 Mutex 版)
// ============================================================
object DatabaseHolder {

    private const val TAG = "DatabaseHolder"
    private val mutex = Mutex()

    @Volatile
    private var database: GpdbDatabase? = null

    @Volatile
    private var currentPath: String? = null

    private val _isReadyFlow = MutableStateFlow(false)
    val isReadyFlow: StateFlow<Boolean> = _isReadyFlow

    /**
     * 在后台协程池异步初始化/挂载数据库，并捕获完整的底层 SQLite 堆栈信息
     */
    suspend fun initDatabaseAsync(context: Context, absoluteDbPath: String): Result<GpdbDatabase> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (currentPath == absoluteDbPath && database != null) {
                    Log.d(TAG, "数据库路径未改变，复用已有连接: $absoluteDbPath")
                    _isReadyFlow.value = true
                    return@withContext Result.success(database!!)
                }

                try {
                    database?.close()
                    database = null
                    _isReadyFlow.value = false

                    Log.i(TAG, "开始挂载数据库 (后台线程): $absoluteDbPath")
                    val db = GpdbDatabase.buildFromExternalFile(context, absoluteDbPath)

                    // 预执行轻量 probe 查询，在后台线程及早验证连接有效性
                    val probeCount = db.movieDao().getMovieCount()
                    Log.i(TAG, "数据库挂载成功！movies 表探测总行数: $probeCount")

                    database = db
                    currentPath = absoluteDbPath
                    _isReadyFlow.value = true
                    Result.success(db)
                } catch (e: Exception) {
                    Log.e(TAG, "【CRITICAL】数据库挂载失败: $absoluteDbPath\n原因: ${e.message}", e)
                    database = null
                    currentPath = null
                    _isReadyFlow.value = false
                    Result.failure(e)
                }
            }
        }

    val db: GpdbDatabase?
        get() = database

    val isReady: Boolean
        get() = database != null

    fun release() {
        try {
            database?.close()
        } catch (e: Exception) {
            Log.e(TAG, "关闭数据库异常", e)
        } finally {
            database = null
            currentPath = null
            _isReadyFlow.value = false
            Log.i(TAG, "DatabaseHolder 已完全释放连接")
        }
    }
}
