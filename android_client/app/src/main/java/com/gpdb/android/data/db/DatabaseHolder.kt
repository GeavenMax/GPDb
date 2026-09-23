package com.gpdb.android.data.db

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// ============================================================
//  DatabaseHolder — 数据库单例生命周期管理 (增强异常追踪版)
// ============================================================
object DatabaseHolder {

    private const val TAG = "DatabaseHolder"
    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var database: GpdbDatabase? = null

    @Volatile
    private var currentPath: String? = null

    /**
     * 在后台协程池异步初始化/挂载数据库，并捕获完整的底层 SQLite 堆栈信息
     */
    suspend fun initDatabaseAsync(context: Context, absoluteDbPath: String): Result<GpdbDatabase> =
        withContext(Dispatchers.IO) {
            lock.write {
                if (currentPath == absoluteDbPath && database != null) {
                    Log.d(TAG, "数据库路径未改变，复用已有连接: $absoluteDbPath")
                    return@withContext Result.success(database!!)
                }

                try {
                    database?.close()
                    database = null

                    Log.i(TAG, "开始挂载数据库 (后台线程): $absoluteDbPath")
                    val db = GpdbDatabase.buildFromExternalFile(context, absoluteDbPath)

                    // 预执行轻量 probe 查询，在后台线程及早验证连接有效性
                    val probeCount = db.movieDao().getMovieCount()
                    Log.i(TAG, "数据库挂载成功！movies 表探测总行数: $probeCount")

                    database = db
                    currentPath = absoluteDbPath
                    Result.success(db)
                } catch (e: Exception) {
                    Log.e(TAG, "【CRITICAL】数据库挂载失败: $absoluteDbPath\n原因: ${e.message}", e)
                    database = null
                    currentPath = null
                    Result.failure(e)
                }
            }
        }

    val db: GpdbDatabase?
        get() = lock.read { database }

    val isReady: Boolean
        get() = lock.read { database != null }

    fun release() {
        lock.write {
            try {
                database?.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭数据库异常", e)
            } finally {
                database = null
                currentPath = null
                Log.i(TAG, "DatabaseHolder 已完全释放连接")
            }
        }
    }
}
