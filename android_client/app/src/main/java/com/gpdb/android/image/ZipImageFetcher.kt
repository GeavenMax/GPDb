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

// ── 专用图片下载客户端（单例连接池）────────────────────────────────
object ImageHttpClient {
    val client: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
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
        val relativePath = data.relativePath.trimStart('/')
        val physicalRoot = data.physicalRoot

        // 1. 物理增量文件直读 (外部挂载根目录)
        if (physicalRoot.isNotBlank() && relativePath.isNotBlank()) {
            val physicalFile = File(physicalRoot, relativePath)
            if (physicalFile.exists() && physicalFile.isFile && physicalFile.length() > 100) {
                val bufferedSource = physicalFile.inputStream().source().buffer()
                return@withContext SourceResult(
                    source = ImageSource(source = bufferedSource, context = options.context),
                    mimeType = physicalFile.name.guessMimeType(),
                    dataSource = DataSource.DISK
                )
            }
        }

        // 1.5. App 本地内部私有持久化目录直读 (context.filesDir/relativePath)
        if (relativePath.isNotBlank()) {
            val internalFile = File(options.context.filesDir, relativePath)
            if (internalFile.exists() && internalFile.isFile && internalFile.length() > 100) {
                val bufferedSource = internalFile.inputStream().source().buffer()
                return@withContext SourceResult(
                    source = ImageSource(source = bufferedSource, context = options.context),
                    mimeType = internalFile.name.guessMimeType(),
                    dataSource = DataSource.DISK
                )
            }
        }

        // 2. ZIP Store-Mode O(1) 随机寻址 (如果挂载了 GPDb_Images.zip)
        if (ZipHolder.isReady && relativePath.isNotBlank()) {
            val stream = ZipHolder.getInputStream(relativePath)
            if (stream != null) {
                val bufferedSource = stream.source().buffer()
                return@withContext SourceResult(
                    source = ImageSource(source = bufferedSource, context = options.context),
                    mimeType = relativePath.guessMimeType(),
                    dataSource = DataSource.DISK
                )
            }
        }

        // 3. 网络按需自动拉取 + 自动本地持久化落盘 (防盗链伪装)
        val fallback = data.fallbackUrl
        val remoteUrl = when {
            !fallback.isNullOrBlank() && (fallback.startsWith("http://", ignoreCase = true) || fallback.startsWith("https://", ignoreCase = true)) -> fallback
            !fallback.isNullOrBlank() -> "https://gayeroticvideoindex.com/${fallback.trimStart('/')}"
            relativePath.isNotBlank() -> {
                val clean = relativePath.removePrefix("image_cache/").trimStart('/')
                "https://gayeroticvideoindex.com/images/$clean"
            }
            else -> null
        }

        if (!remoteUrl.isNullOrBlank() && relativePath.isNotBlank()) {
            val downloadedFile = downloadAndCacheImage(
                context = options.context,
                remoteUrl = remoteUrl,
                relativePath = relativePath,
                physicalRoot = physicalRoot
            )
            if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 100) {
                val bufferedSource = downloadedFile.inputStream().source().buffer()
                return@withContext SourceResult(
                    source = ImageSource(source = bufferedSource, context = options.context),
                    mimeType = downloadedFile.name.guessMimeType(),
                    dataSource = DataSource.NETWORK
                )
            }
        }

        null
    }

    private fun downloadAndCacheImage(
        context: Context,
        remoteUrl: String,
        relativePath: String,
        physicalRoot: String
    ): File? {
        try {
            val request = okhttp3.Request.Builder()
                .url(remoteUrl)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://gayeroticvideoindex.com/")
                .header("Connection", "keep-alive")
                .build()

            ImageHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "下载图片失败 ($remoteUrl): HTTP ${response.code}")
                    return null
                }
                val body = response.body ?: return null
                val bytes = body.bytes()
                if (bytes.size < 100) return null

                // 优先尝试写回外部存储 physicalRoot (若已挂载且可写)
                var writtenFile: File? = null
                if (physicalRoot.isNotBlank()) {
                    try {
                        val pFile = File(physicalRoot, relativePath)
                        pFile.parentFile?.mkdirs()
                        if (pFile.parentFile?.canWrite() == true) {
                            pFile.writeBytes(bytes)
                            writtenFile = pFile
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "外部物理存储写入跳过: ${e.message}")
                    }
                }

                // 无论外部存储是否可写，均写入应用内部文件目录，确保持久离线可用
                val internalFile = File(context.filesDir, relativePath)
                internalFile.parentFile?.mkdirs()
                val tmpFile = File(context.filesDir, "$relativePath.tmp")
                tmpFile.parentFile?.mkdirs()
                tmpFile.writeBytes(bytes)
                if (tmpFile.renameTo(internalFile)) {
                    return writtenFile ?: internalFile
                } else {
                    internalFile.writeBytes(bytes)
                    tmpFile.delete()
                    return writtenFile ?: internalFile
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "网络拉取图片异常: $remoteUrl", e)
            return null
        }
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
