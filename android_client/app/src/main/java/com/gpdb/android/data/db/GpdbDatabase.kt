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
import com.gpdb.android.data.db.entities.StudioEntity
import com.gpdb.android.data.db.dao.SearchDao
import com.gpdb.android.data.db.dao.BrowseDao
import com.gpdb.android.data.db.dao.StudioDao
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
        EpisodePerformerEntity::class,
        StudioEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class GpdbDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao
    abstract fun performerDao(): PerformerDao
    abstract fun episodeDao(): com.gpdb.android.data.db.dao.EpisodeDao
    abstract fun searchDao(): SearchDao
    abstract fun browseDao(): BrowseDao
    abstract fun studioDao(): StudioDao
    abstract fun userActionDao(): UserActionDao

    companion object {
        private const val TAG = "GpdbDatabase"

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i(TAG, "执行 4->5 平滑迁移: 补齐 studios 表及相关索引")
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS studios (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            name_zh TEXT,
                            description_zh TEXT,
                            logo_url TEXT,
                            banner_url TEXT,
                            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                        );
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);")
                } catch (e: Exception) {
                    Log.w(TAG, "迁移 4->5 告警: ${e.message}")
                }
            }
        }

        private val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try { database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;") } catch (_: Exception) {}
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS studios (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            name_zh TEXT,
                            description_zh TEXT,
                            logo_url TEXT,
                            banner_url TEXT,
                            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                        );
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try { database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;") } catch (_: Exception) {}
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS studios (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            name_zh TEXT,
                            description_zh TEXT,
                            logo_url TEXT,
                            banner_url TEXT,
                            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                        );
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try { database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;") } catch (_: Exception) {}
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS studios (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            name_zh TEXT,
                            description_zh TEXT,
                            logo_url TEXT,
                            banner_url TEXT,
                            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                        );
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_0_5 = object : Migration(0, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try { database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;") } catch (_: Exception) {}
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS studios (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            name_zh TEXT,
                            description_zh TEXT,
                            logo_url TEXT,
                            banner_url TEXT,
                            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                        );
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_0_4 = object : Migration(0, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i(TAG, "检测到外部 GPDb.db (version 0)，已执行 0->4 平滑迁移")
                try { database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;") } catch (_: Exception) {}
            }
        }

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try { database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;") } catch (_: Exception) {}
            }
        }

        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try { database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;") } catch (_: Exception) {}
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i(TAG, "检测到外部 GPDb.db (version 3)，已执行 3->4 平滑迁移 (补齐 sj_url)")
                try { database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;") } catch (_: Exception) {}
            }
        }

        private val MIGRATION_0_3 = object : Migration(0, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i(TAG, "检测到外部 macOS GPDb.db (version 0)，已自动平滑挂载并建立 Room 元数据索引")
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;")
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
         * 前置兼容性检查与物理列自动补全：
         * 针对外部传入的 SQLite 文件，先使用 Android 原生 SQLite 进行无损检测与轻量 ALTER TABLE，
         * 确保即使目标库此前已被 Room 或其他客户端写入了不同的版本元数据，performers/movies 表依然具备
         * 当前代码所需的全部列，彻底防止 Room TableInfo 验证阶段报 Migration didn't properly handle。
         */
        private fun ensureSchemaCompatibility(dbFile: File) {
            var rawDb: android.database.sqlite.SQLiteDatabase? = null
            try {
                rawDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                )

                // 确保 TRUNCATE 日志模式，避免 FUSE 文件系统生成 -wal / -shm 触发 ioctl 权限异常
                try {
                    rawDb.rawQuery("PRAGMA journal_mode = TRUNCATE;", null)?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "设置 journal_mode=TRUNCATE 警告: ${e.message}")
                }

                // 1. 检查并补全 performers 表字段
                val perfCursor = rawDb.rawQuery("PRAGMA table_info(performers)", null)
                val perfCols = mutableSetOf<String>()
                val nameIdx = perfCursor.getColumnIndex("name")
                while (perfCursor.moveToNext()) {
                    if (nameIdx >= 0) perfCols.add(perfCursor.getString(nameIdx).lowercase())
                }
                perfCursor.close()

                if (!perfCols.contains("pbc_url")) {
                    try {
                        rawDb.execSQL("ALTER TABLE performers ADD COLUMN pbc_url TEXT;")
                        Log.i(TAG, "前置自愈: 已补齐 performers.pbc_url 字段")
                    } catch (e: Exception) {
                        Log.w(TAG, "补齐 pbc_url 异常: ${e.message}")
                    }
                }
                if (!perfCols.contains("sj_url")) {
                    try {
                        rawDb.execSQL("ALTER TABLE performers ADD COLUMN sj_url TEXT;")
                        Log.i(TAG, "前置自愈: 已补齐 performers.sj_url 字段")
                    } catch (e: Exception) {
                        Log.w(TAG, "补齐 sj_url 异常: ${e.message}")
                    }
                }

                // 2. 检查并补全 movies 表可能缺失的字段
                val movieCursor = rawDb.rawQuery("PRAGMA table_info(movies)", null)
                val movieCols = mutableSetOf<String>()
                val mNameIdx = movieCursor.getColumnIndex("name")
                while (movieCursor.moveToNext()) {
                    if (mNameIdx >= 0) movieCols.add(movieCursor.getString(mNameIdx).lowercase())
                }
                movieCursor.close()

                if (!movieCols.contains("cover_back")) {
                    try { rawDb.execSQL("ALTER TABLE movies ADD COLUMN cover_back TEXT;") } catch (_: Exception) {}
                }
                if (!movieCols.contains("title_zh")) {
                    try { rawDb.execSQL("ALTER TABLE movies ADD COLUMN title_zh TEXT;") } catch (_: Exception) {}
                }
                if (!movieCols.contains("title_attempts")) {
                    try { rawDb.execSQL("ALTER TABLE movies ADD COLUMN title_attempts INTEGER DEFAULT 0;") } catch (_: Exception) {}
                }
                if (!movieCols.contains("description_zh")) {
                    try { rawDb.execSQL("ALTER TABLE movies ADD COLUMN description_zh TEXT;") } catch (_: Exception) {}
                }
                if (!movieCols.contains("translation_attempts")) {
                    try { rawDb.execSQL("ALTER TABLE movies ADD COLUMN translation_attempts INTEGER DEFAULT 0;") } catch (_: Exception) {}
                }
                if (!movieCols.contains("covers_json")) {
                    try { rawDb.execSQL("ALTER TABLE movies ADD COLUMN covers_json TEXT;") } catch (_: Exception) {}
                }
                if (!movieCols.contains("director_id")) {
                    try { rawDb.execSQL("ALTER TABLE movies ADD COLUMN director_id INTEGER;") } catch (_: Exception) {}
                }
                if (!movieCols.contains("director_name")) {
                    try { rawDb.execSQL("ALTER TABLE movies ADD COLUMN director_name TEXT;") } catch (_: Exception) {}
                }

                // 3. 补齐扩展档案表结构
                rawDb.execSQL("""
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

                rawDb.execSQL("""
                    CREATE TABLE IF NOT EXISTS performer_sj_profiles (
                        performer_id INTEGER PRIMARY KEY,
                        sj_url TEXT NOT NULL,
                        sj_id TEXT,
                        hair TEXT,
                        eyes TEXT,
                        height TEXT,
                        weight TEXT,
                        ethnicity TEXT,
                        dick_size TEXT,
                        scraped_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY(performer_id) REFERENCES performers(id) ON DELETE CASCADE
                    );
                """.trimIndent())

                // 4. 补齐 studios 厂牌历史档案表结构及索引
                rawDb.execSQL("""
                    CREATE TABLE IF NOT EXISTS studios (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        name_zh TEXT,
                        description_zh TEXT,
                        logo_url TEXT,
                        banner_url TEXT,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                    );
                """.trimIndent())
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
                try {
                    rawDb.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);")
                } catch (_: Exception) {}

                val studioCols = mutableMapOf<String, String>()
                rawDb.rawQuery("PRAGMA table_info(studios);", null).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    val typeIdx = cursor.getColumnIndex("type")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) {
                            val colName = cursor.getString(nameIdx)
                            val colType = if (typeIdx >= 0) cursor.getString(typeIdx) else ""
                            studioCols[colName] = colType
                        }
                    }
                }
                if (!studioCols.containsKey("name_zh")) {
                    try { rawDb.execSQL("ALTER TABLE studios ADD COLUMN name_zh TEXT;") } catch (_: Exception) {}
                }
                if (!studioCols.containsKey("description_zh")) {
                    try { rawDb.execSQL("ALTER TABLE studios ADD COLUMN description_zh TEXT;") } catch (_: Exception) {}
                }
                if (!studioCols.containsKey("logo_url")) {
                    try { rawDb.execSQL("ALTER TABLE studios ADD COLUMN logo_url TEXT;") } catch (_: Exception) {}
                }
                if (!studioCols.containsKey("banner_url")) {
                    try { rawDb.execSQL("ALTER TABLE studios ADD COLUMN banner_url TEXT;") } catch (_: Exception) {}
                }

                // 核心关键修复：检查 updated_at 的类型亲和性。
                // 若为 TIMESTAMP (如旧版本 DDL 或外部工具创建)，SQLite 亲和性为 NUMERIC(1)，
                // 而 Room Entity 映射 String 期望 TEXT(2)，会导致 Pre-packaged database has an invalid schema 校验崩溃。
                // 此处执行无损热迁移：将 studios 重建为 TEXT DEFAULT CURRENT_TIMESTAMP 并完整保留全部厂牌数据。
                val updatedAtType = studioCols["updated_at"]
                if (updatedAtType != null && updatedAtType.uppercase().contains("TIMESTAMP")) {
                    Log.i(TAG, "检测到 studios.updated_at 字段类型为 $updatedAtType，正在无损升级为 TEXT 以满足 Room 架构校验...")
                    try {
                        rawDb.execSQL("""
                            CREATE TABLE IF NOT EXISTS studios_schema_fix (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                name TEXT NOT NULL UNIQUE,
                                name_zh TEXT,
                                description_zh TEXT,
                                logo_url TEXT,
                                banner_url TEXT,
                                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                            );
                        """.trimIndent())
                        rawDb.execSQL("""
                            INSERT OR IGNORE INTO studios_schema_fix (id, name, name_zh, description_zh, logo_url, banner_url, updated_at)
                            SELECT id, name, name_zh, description_zh, logo_url, banner_url, updated_at FROM studios;
                        """.trimIndent())
                        rawDb.execSQL("DROP TABLE studios;")
                        rawDb.execSQL("ALTER TABLE studios_schema_fix RENAME TO studios;")
                        rawDb.execSQL("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
                        Log.i(TAG, "studios 表无损重建升级为 TEXT 完成")
                    } catch (e: Exception) {
                        Log.e(TAG, "studios 表无损重建失败: ${e.message}", e)
                    }
                }

                // 5. 补齐 episodes 历史可能缺失的列与索引
                val epCols = mutableSetOf<String>()
                rawDb.rawQuery("PRAGMA table_info(episodes);", null).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) epCols.add(cursor.getString(nameIdx))
                    }
                }
                if (!epCols.contains("description_zh")) {
                    try { rawDb.execSQL("ALTER TABLE episodes ADD COLUMN description_zh TEXT;") } catch (_: Exception) {}
                }
                if (!epCols.contains("action_notes")) {
                    try { rawDb.execSQL("ALTER TABLE episodes ADD COLUMN action_notes TEXT;") } catch (_: Exception) {}
                }
                if (!epCols.contains("release_date")) {
                    try { rawDb.execSQL("ALTER TABLE episodes ADD COLUMN release_date TEXT;") } catch (_: Exception) {}
                }
                if (!epCols.contains("studio_id")) {
                    try { rawDb.execSQL("ALTER TABLE episodes ADD COLUMN studio_id INTEGER;") } catch (_: Exception) {}
                }
                if (!epCols.contains("studio_name")) {
                    try { rawDb.execSQL("ALTER TABLE episodes ADD COLUMN studio_name TEXT;") } catch (_: Exception) {}
                }
                try {
                    rawDb.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_movie_id ON episodes(movie_id);")
                    rawDb.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);")
                } catch (_: Exception) {}

                Log.i(TAG, "前置架构自愈检查完成，数据库物理列已就绪")
            } catch (e: Exception) {
                Log.w(TAG, "前置架构自愈检查告警 (不阻断正常挂载): ${e.message}", e)
            } finally {
                try {
                    rawDb?.close()
                } catch (_: Exception) {}
            }
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

            // ★ 关键前置步骤：在 Room 连接前完成底层物理表列完整性校验与自动补齐
            ensureSchemaCompatibility(dbFile)

            return Room.databaseBuilder(
                context.applicationContext,
                GpdbDatabase::class.java,
                dbFile.absolutePath // 绝对路径直连，零文件拷贝
            )
                .addMigrations(
                    MIGRATION_0_1, MIGRATION_0_2, MIGRATION_1_2,
                    MIGRATION_0_3, MIGRATION_1_3, MIGRATION_2_3,
                    MIGRATION_0_4, MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4,
                    MIGRATION_0_5, MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5, MIGRATION_4_5
                )
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
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS performer_sj_profiles (
                                    performer_id INTEGER PRIMARY KEY,
                                    sj_url TEXT NOT NULL,
                                    sj_id TEXT,
                                    hair TEXT,
                                    eyes TEXT,
                                    height TEXT,
                                    weight TEXT,
                                    ethnicity TEXT,
                                    dick_size TEXT,
                                    scraped_at TEXT DEFAULT CURRENT_TIMESTAMP,
                                    FOREIGN KEY(performer_id) REFERENCES performers(id) ON DELETE CASCADE
                                );
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS studios (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                                    name TEXT NOT NULL UNIQUE,
                                    name_zh TEXT,
                                    description_zh TEXT,
                                    logo_url TEXT,
                                    banner_url TEXT,
                                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                                );
                            """.trimIndent())
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_studios_name ON studios(name);")
                            try {
                                db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_studio ON episodes(studio_name);")
                            } catch (_: Exception) {}
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
