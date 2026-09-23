package com.gpdb.android.image

import android.content.Context
import android.os.Environment
import android.util.Log

// ============================================================
//  PathResolver — SAF URI → 真实绝对物理路径
//
//  Android 的 ACTION_OPEN_DOCUMENT_TREE 返回的是 content:// URI，
//  例如：
//    content://com.android.externalstorage.documents/tree/
//      primary%3ADocuments%2FGPDb
//
//  我们需要把它还原为真实的文件系统路径：
//    /storage/emulated/0/Documents/GPDb
//
//  ★ 这套转换仅适用于主存储（primary）分区。
//    外置 SD 卡的路径段为 `<UUID>:path`，需要通过 StorageManager
//    枚举 StorageVolume 来还原，本实现已涵盖此分支。
// ============================================================
object PathResolver {

    private const val TAG = "GpdbPathResolver"

    /**
     * 将 SAF 文档树 URI 的 treeDocumentId（已 URL 解码）
     * 转换为绝对物理路径。
     *
     * 调用示例：
     * ```kotlin
     * val uri = intent.data  // 来自 ACTION_OPEN_DOCUMENT_TREE 结果
     * val docId = DocumentsContract.getTreeDocumentId(uri)
     * // docId 例如 "primary:Documents/GPDb"
     * val absPath = PathResolver.resolve(context, docId)
     * ```
     *
     * @param context     应用 Context
     * @param treeDocId   DocumentsContract.getTreeDocumentId 返回的路径段
     * @return 绝对物理路径字符串，解析失败则返回 null
     */
    fun resolve(context: Context, treeDocId: String): String? {
        return when {
            // ── 主存储：primary:Documents/GPDb ─────────────────
            treeDocId.startsWith("primary:") -> {
                val relative = treeDocId.removePrefix("primary:")
                val base = Environment.getExternalStorageDirectory().absolutePath
                val resolved = "$base/$relative"
                Log.d(TAG, "主存储路径解析：$treeDocId → $resolved")
                resolved
            }

            // ── 外置 SD 卡：<UUID>:path ─────────────────────────
            treeDocId.contains(":") -> {
                val (volumeId, relative) = treeDocId.split(":", limit = 2)
                val storageManager = context.getSystemService(
                    Context.STORAGE_SERVICE
                ) as android.os.storage.StorageManager

                // 枚举所有存储卷，匹配 UUID
                val volume = storageManager.storageVolumes.firstOrNull { vol ->
                    vol.uuid?.equals(volumeId, ignoreCase = true) == true
                }

                val volumePath = volume?.directory?.absolutePath
                if (volumePath == null) {
                    Log.e(TAG, "找不到 SD 卡存储卷：$volumeId")
                    return null
                }

                val resolved = "$volumePath/$relative"
                Log.d(TAG, "SD 卡路径解析：$treeDocId → $resolved")
                resolved
            }

            else -> {
                Log.e(TAG, "无法解析 treeDocId 格式：$treeDocId")
                null
            }
        }
    }

    /**
     * 给定挂载根目录，验证 GPDb.db 与 GPDb_Images.zip 是否均存在。
     *
     * @return Pair<dbExists, zipExists>
     */
    fun validateGpdbDirectory(rootPath: String): Pair<Boolean, Boolean> {
        val dbFile  = java.io.File(rootPath, "GPDb.db")
        val zipFile = java.io.File(rootPath, "GPDb_Images.zip")
        return Pair(dbFile.exists(), zipFile.exists())
    }
}
