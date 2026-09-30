package com.gpdb.android.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gpdb.android.data.db.dao.MovieDao
import com.gpdb.android.data.db.dao.PerformerDao
import com.gpdb.android.data.db.dao.UserActionDao

import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.MoviePerformerEntity
import com.gpdb.android.data.db.entities.PerformerEntity
import com.gpdb.android.data.db.entities.EpisodeEntity
import com.gpdb.android.data.db.entities.EpisodePerformerEntity
import com.gpdb.android.data.db.dao.SearchDao
import com.gpdb.android.data.db.dao.BrowseDao
import java.io.File

import androidx.room.migration.Migration

// ============================================================
//  GpdbDatabase — Room 核心数据库类 (FUSE 兼容修复版)
//
//  ★ FUSE 存储适配要点：
//  1. 现代 Android 外部存储 (/storage/emulated/0/) 经由 FUSE 模拟层管理，
//     FUSE 不支持 POSIX 共享内存映射锁定（mmap -shm / -wal 文件），
//     强制启用 WAL 会触发 SELinux `avc: denied { ioctl }` 与 SQLiteCantOpenException。
//  2. 针对外部挂载，必须使用 [JournalMode.TRUNCATE] 或 [JournalMode.DELETE]。
//  3. 绝不调用 fallbackToDestructiveMigration()，确保原始数据安全。
// ============================================================
@Database(
    entities = [
        MovieEntity::class,
        PerformerEntity::class,
        MoviePerformerEntity::class,
        EpisodeEntity::class,
        EpisodePerformerEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class GpdbDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun performerDao(): PerformerDao
    abstract fun episodeDao(): com.gpdb.android.data.db.dao.EpisodeDao
    abstract fun searchDao(): SearchDao
    abstract fun browseDao(): BrowseDao
    abstract fun userActionDao(): UserActionDao

    companion object {
        private const val TAG = "GpdbDatabase"

        private val MIGRATION_0_3 = object : Migration(0, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i(TAG, "检测到外部 macOS GPDb.db (version 0)，已自动平滑挂载并建立 Room 元数据索引")
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_0_2 = object : Migration(0, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }

        private val MIGRATION_0_1 = object : Migration(0, 1) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }

        /**
         * 基于外部物理路径就地挂载 Room 数据库实例
         */
        fun buildFromExternalFile(context: Context, absoluteDbPath: String): GpdbDatabase {
            val dbFile = File(absoluteDbPath)
            require(dbFile.exists() && dbFile.isFile) {
                "数据库文件不存在或不可读取: $absoluteDbPath"
            }

            Log.i(TAG, "正在以 TRUNCATE 日志模式直连挂载外部 SQLite: $absoluteDbPath")

            return Room.databaseBuilder(
                context.applicationContext,
                GpdbDatabase::class.java,
                dbFile.absolutePath // 绝对路径直连，零文件拷贝
            )
                .addMigrations(MIGRATION_0_1, MIGRATION_0_2, MIGRATION_1_2, MIGRATION_0_3, MIGRATION_1_3, MIGRATION_2_3)
                // ★ 关键修复：强制使用 TRUNCATE 日志模式，彻底消除 FUSE 下 WAL 模式的 -shm / -wal ioctl 权限冲突
                .setJournalMode(JournalMode.TRUNCATE)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            db.query("PRAGMA journal_mode = TRUNCATE;").close()
                            db.query("PRAGMA synchronous = NORMAL;").close()
                            db.query("PRAGMA foreign_keys = ON;").close()
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS performer_pbc_profiles (
                                    performer_id INTEGER PRIMARY KEY,
                                    pbc_url TEXT NOT NULL,
                                    pbc_id TEXT,
                                    birth_name TEXT,
                                    career_start INTEGER,
                                    career_end INTEGER,
                                    career_status TEXT,
                                    bio TEXT,
                                    birth_date TEXT,
                                    birth_place TEXT,
                                    ethnicity TEXT,
                                    astrology TEXT,
                                    height TEXT,
                                    weight TEXT,
                                    dick_size TEXT,
                                    foreskin TEXT,
                                    tattoos TEXT,
                                    piercings TEXT,
                                    roles TEXT,
                                    social_links TEXT,
                                    external_ids TEXT,
                                    tags TEXT,
                                    image_url TEXT,
                                    scraped_at TEXT DEFAULT CURRENT_TIMESTAMP,
                                    FOREIGN KEY(performer_id) REFERENCES performers(id) ON DELETE CASCADE
                                );
                            """.trimIndent())
                            Log.i(TAG, "GPDb SQLite 成功就绪 (TRUNCATE mode on FUSE)")
                        } catch (e: Exception) {
                            Log.w(TAG, "配置 PRAGMA 出现警告: ${e.message}", e)
                        }
                    }
                })
                .build()
        }
    }
}
