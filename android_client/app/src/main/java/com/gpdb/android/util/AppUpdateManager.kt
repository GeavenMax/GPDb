package com.gpdb.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.gpdb.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long
)

sealed class AppUpdateCheckResult {
    data class UpdateAvailable(val info: AppReleaseInfo) : AppUpdateCheckResult()
    data class AlreadyLatest(val currentVersion: String, val remoteVersion: String) : AppUpdateCheckResult()
    data class Error(val message: String) : AppUpdateCheckResult()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val GITHUB_REPO = "GeavenMax/GPDb"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 对比远程版本号与当前客户端版本号
     * 返回 true 表示远程存在更新版本
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        try {
            val cleanRemote = remote.trim().removePrefix("v").removePrefix("V").split("-")[0]
            val cleanCurrent = current.trim().removePrefix("v").removePrefix("V").split("-")[0]

            val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "版本号比对异常: remote=$remote, current=$current", e)
            return false
        }
    }

    /**
     * 巡检 GitHub Releases 最新版本详细信息 (供设置页手动触发与反馈)
     */
    suspend fun checkAppUpdateDetailed(currentVersion: String = BuildConfig.VERSION_NAME): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("User-Agent", "GPDb-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "检查新版本请求失败: HTTP ${response.code}")
                return@withContext AppUpdateCheckResult.Error("网络连接失败 (HTTP ${response.code})")
            }

            val bodyStr = response.body?.string() ?: return@withContext AppUpdateCheckResult.Error("服务器返回内容为空")
            val json = JSONObject(bodyStr)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val notes = json.optString("body", "暂无更新说明")
            val remoteVersion = tagName.removePrefix("v").removePrefix("V")

            if (!isNewerVersion(remoteVersion, currentVersion)) {
                Log.d(TAG, "当前已是最新版本: current=$currentVersion, remote=$remoteVersion")
                return@withContext AppUpdateCheckResult.AlreadyLatest(currentVersion, remoteVersion)
            }

            // 查找可供 Android 安装的 APK Asset
            val assets = json.optJSONArray("assets") ?: return@withContext AppUpdateCheckResult.Error("新版本资产列表为空")
            var apkUrl: String? = null
            var apkSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkUrl.isNullOrBlank()) {
                Log.w(TAG, "新版本 $tagName 尚未打包上传 Android APK 资产")
                return@withContext AppUpdateCheckResult.Error("新版本 $tagName 暂未包含 APK 安装包")
            }

            return@withContext AppUpdateCheckResult.UpdateAvailable(
                AppReleaseInfo(
                    tagName = tagName,
                    versionName = remoteVersion,
                    title = title,
                    notes = notes,
                    apkUrl = apkUrl,
                    apkSize = apkSize
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "检测 GitHub 新版本异常: ${e.message}", e)
            return@withContext AppUpdateCheckResult.Error(e.message ?: "检测更新异常")
        }
    }

    /**
     * 巡检 GitHub Releases 最新版本信息 (简化版)
     */
    suspend fun checkForAppUpdate(currentVersion: String = BuildConfig.VERSION_NAME): AppReleaseInfo? {
        val result = checkAppUpdateDetailed(currentVersion)
        return (result as? AppUpdateCheckResult.UpdateAvailable)?.info
    }

    /**
     * 下载 APK 文件并实时回调下载进度 (0 ~ 100)
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "apk_updates")
        if (!dir.exists()) dir.mkdirs()

        val apkFile = File(dir, "gpdb_update.apk")
        if (apkFile.exists()) apkFile.delete()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GPDb-Android/${BuildConfig.VERSION_NAME}")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("下载安装包失败: HTTP ${response.code}")

        val body = response.body ?: throw Exception("响应体为空")
        val contentLength = body.contentLength()

        body.byteStream().use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0) {
                        val progress = ((totalRead * 100) / contentLength).toInt()
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }
                output.flush()
            }
        }

        return@withContext apkFile
    }

    /**
     * 调用系统安装器安装 APK
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "拉起系统 APK 安装器失败: ${e.message}", e)
        }
    }
}
