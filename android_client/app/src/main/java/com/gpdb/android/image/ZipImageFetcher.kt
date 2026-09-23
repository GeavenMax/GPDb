package com.gpdb.android.image

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.File
import java.io.InputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.zip.ZipFile
import kotlin.concurrent.read
import kotlin.concurrent.write

// ============================================================
//  GPDb — ZipImageFetcher
//
//  三层回退策略：
//  第一层 ► 物理增量缓存（绝对路径直读）
//  第二层 ► ZIP Store-Mode O(1) 随机寻址（全异步调度）
//  第三层 ► 网络 URL 降级
// ============================================================

data class GpdbImageData(
    val relativePath: String,
    val physicalRoot: String,
    val fallbackUrl: String? = null,
)

// ── ZIP 单例持有者 ─────────────────────────────────────────────
object ZipHolder {

    private const val TAG = "GpdbZipHolder"
    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var zipFile: ZipFile? = null

    @Volatile
    private var currentZipPath: String? = null

    /**
     * 异步设置（或切换）ZIP 路径。
     * 必须在 Dispatchers.IO 下运行，绝不阻塞主线程。
     */
    suspend fun setZipPath(absoluteZipPath: String): Result<Int> = withContext(Dispatchers.IO) {
        val file = File(absoluteZipPath)
        if (!file.exists() || !file.isFile) {
            val err = "ZIP 文件不存在或不是有效文件: $absoluteZipPath"
            Log.e(TAG, err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        try {
            lock.write {
                if (currentZipPath == absoluteZipPath && zipFile != null) {
                    Log.d(TAG, "ZIP 路径未变，复用已有实例: $absoluteZipPath")
                    return@withContext Result.success(zipFile!!.size())
                }

                zipFile?.close()
                zipFile = null

                Log.i(TAG, "开始解析 ZIP 中央目录 (后台线程): $absoluteZipPath")
                val startTime = System.currentTimeMillis()

                val newZip = ZipFile(file)
                zipFile = newZip
                currentZipPath = absoluteZipPath

                val cost = System.currentTimeMillis() - startTime
                val entryCount = newZip.size()
                Log.i(TAG, "ZIP 挂载完成: $entryCount 个条目，解析耗时: ${cost}ms")
                Result.success(entryCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZIP 挂载失败: $absoluteZipPath", e)
            lock.write {
                zipFile = null
                currentZipPath = null
            }
            Result.failure(e)
        }
    }

    /**
     * 在 ZIP 中查找指定 entry，成功则对 [block] 传入其 InputStream。
     * 读锁保护，支持大量协程高并发加载。
     */
    fun getInputStream(entryName: String): InputStream? {
        return lock.read {
            val zip = zipFile ?: return@read null
            val entry = zip.getEntry(entryName) ?: return@read null
            zip.getInputStream(entry)
        }
    }

    /**
     * 关闭并释放 ZIP 资源。
     */
    fun release() {
        lock.write {
            try {
                zipFile?.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭 ZipFile 异常", e)
            } finally {
                zipFile = null
                currentZipPath = null
                Log.i(TAG, "ZipHolder 已完全释放")
            }
        }
    }

    val isReady: Boolean get() = lock.read { zipFile != null }
}

// ── 核心 Fetcher 实现 ──────────────────────────────────────────
class ZipImageFetcher(
    private val data: GpdbImageData,
    private val options: Options,
) : Fetcher {

    companion object {
        private const val TAG = "GpdbZipFetcher"
    }

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val relativePath = data.relativePath
        val physicalRoot = data.physicalRoot

        // 1. 物理增量文件直读
        if (physicalRoot.isNotBlank()) {
            val physicalFile = File(physicalRoot, relativePath)
            if (physicalFile.exists() && physicalFile.isFile) {
                val bufferedSource = physicalFile.inputStream().source().buffer()
                val imageSource = ImageSource(
                    source = bufferedSource,
                    context = options.context
                )
                return@withContext SourceResult(
                    source = imageSource,
                    mimeType = physicalFile.name.guessMimeType(),
                    dataSource = DataSource.DISK
                )
            }
        }

        // 2. ZIP Store-Mode O(1) 随机寻址
        if (ZipHolder.isReady) {
            val stream = ZipHolder.getInputStream(relativePath)
            if (stream != null) {
                val bufferedSource = stream.source().buffer()
                val imageSource = ImageSource(
                    source = bufferedSource,
                    context = options.context
                )
                return@withContext SourceResult(
                    source = imageSource,
                    mimeType = relativePath.guessMimeType(),
                    dataSource = DataSource.DISK
                )
            }
        }

        // 3. 网络 URL 降级
        val fallback = data.fallbackUrl
        if (!fallback.isNullOrBlank()) {
            return@withContext null // 交由 Coil 内置网络 Fetcher 处理
        }

        null
    }

    class Factory : Fetcher.Factory<GpdbImageData> {
        override fun create(
            data: GpdbImageData,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ZipImageFetcher(data, options)
    }
}

// ── 辅助扩展函数 ───────────────────────────────────────────────
private fun String.guessMimeType(): String = when {
    endsWith(".jpg", ignoreCase = true) || endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
    endsWith(".png", ignoreCase = true)  -> "image/png"
    endsWith(".webp", ignoreCase = true) -> "image/webp"
    endsWith(".gif", ignoreCase = true)  -> "image/gif"
    endsWith(".avif", ignoreCase = true) -> "image/avif"
    else -> "image/jpeg"
}
