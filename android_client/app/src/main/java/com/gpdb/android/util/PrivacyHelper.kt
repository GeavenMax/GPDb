package com.gpdb.android.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 隐私与媒体扫描防护辅助工具
 *
 * 核心机制：
 * 1. 默认沙盒隔离：图片缓存限定在应用私有沙盒 (/data/user/0/<package>/files/image_cache/)，
 *    受 Android Linux UID 权限保护，第三方 App 与系统媒体库无法穿透访问。
 * 2. .nomedia 保护：递归在图库根目录及所有子目录建立 .nomedia 占位文件，
 *    阻止 Android MediaScanner 索引成人封面至系统相册。
 */
object PrivacyHelper {
    private const val TAG = "PrivacyHelper"

    /**
     * 递归在目标目录及其所有子目录下创建 .nomedia 空文件
     */
    fun ensureNoMedia(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) return
        try {
            val noMediaFile = File(directory, ".nomedia")
            if (!noMediaFile.exists()) {
                noMediaFile.createNewFile()
                Log.d(TAG, "已在目录创建 .nomedia: ${directory.absolutePath}")
            }
            directory.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    ensureNoMedia(child)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "创建 .nomedia 文件异常: ${e.message}")
        }
    }

    /**
     * 初始化应用专属内部沙盒目录并植入 .nomedia 保护
     */
    fun initSandboxPrivacy(context: Context) {
        try {
            val sandboxImageCache = File(context.filesDir, "image_cache")
            if (!sandboxImageCache.exists()) {
                sandboxImageCache.mkdirs()
            }
            ensureNoMedia(sandboxImageCache)

            listOf("Covers", "Episodes", "Performers", "Stars", "misc").forEach { sub ->
                val subDir = File(sandboxImageCache, sub)
                if (!subDir.exists()) subDir.mkdirs()
                ensureNoMedia(subDir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "初始化沙盒图片隐私保护异常: ${e.message}")
        }
    }
}
