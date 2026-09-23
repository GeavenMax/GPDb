package com.gpdb.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ============================================================
//  MountPreferences — DataStore 持久化挂载路径
//
//  持久化两个关键路径（绝对物理路径）：
//    - mountRootPath:  根目录，如 /storage/emulated/0/Documents/GPDb
//    - dbPath:         GPDb.db 完整路径（mountRootPath + /GPDb.db）
//    - zipPath:        GPDb_Images.zip 完整路径
//
//  使用 Preferences DataStore 而非 SharedPreferences，天然支持
//  Kotlin Flow 订阅，可在 ViewModel 中直接 collectAsState。
// ============================================================

// Top-level 扩展属性：每个进程只创建一个 DataStore 实例
private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "gpdb_mount_prefs")

class MountPreferences(private val context: Context) {

    companion object {
        private val KEY_MOUNT_ROOT = stringPreferencesKey("mount_root_path")
        private val KEY_DB_PATH    = stringPreferencesKey("db_path")
        private val KEY_ZIP_PATH   = stringPreferencesKey("zip_path")

        const val DB_FILENAME  = "GPDb.db"
        const val ZIP_FILENAME = "GPDb_Images.zip"
    }

    // ── 读取 Flow（UI 层 collectAsState 订阅）─────────────────

    /** 挂载根目录绝对路径 Flow。未配置时 emit null。 */
    val mountRootFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_MOUNT_ROOT]
    }

    /** GPDb.db 完整路径 Flow。 */
    val dbPathFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DB_PATH]
    }

    /** GPDb_Images.zip 完整路径 Flow。 */
    val zipPathFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ZIP_PATH]
    }

    /**
     * 挂载就绪状态 Flow。
     * db 与 zip 路径均已配置时为 true（ZIP 可选——仅 db 也能运行）。
     */
    val isMountedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DB_PATH]?.isNotBlank() == true
    }

    // ── 写入（挂载时调用一次）────────────────────────────────

    /**
     * 保存挂载根目录，并自动派生 db/zip 完整路径。
     * 调用此方法后，[dbPathFlow] / [zipPathFlow] 立即 emit 新值。
     */
    suspend fun saveMountRoot(absoluteRootPath: String) {
        val root = absoluteRootPath.trimEnd('/')
        context.dataStore.edit { prefs ->
            prefs[KEY_MOUNT_ROOT] = root
            prefs[KEY_DB_PATH]   = "$root/$DB_FILENAME"
            prefs[KEY_ZIP_PATH]  = "$root/$ZIP_FILENAME"
        }
    }

    /** 清除所有挂载配置（重新选择目录时调用）。 */
    suspend fun clearMount() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_MOUNT_ROOT)
            prefs.remove(KEY_DB_PATH)
            prefs.remove(KEY_ZIP_PATH)
        }
    }
}
