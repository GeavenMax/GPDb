package com.gpdb.android.data.db

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SandboxDatabaseInitializer {

    private const val TAG = "SandboxDbInitializer"

    suspend fun initializeSandbox(context: Context): File = withContext(Dispatchers.IO) {
        val sandboxDir = File(context.filesDir, "gpdb_database")
        if (!sandboxDir.exists()) {
            sandboxDir.mkdirs()
        }

        val dbFile = File(sandboxDir, "GPDb.db")
        if (!dbFile.exists()) {
            Log.i(TAG, "正在为全新用户创建专属沙盒数据库: ${dbFile.absolutePath}")
            val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                db.beginTransaction()

                // 1. Auxiliary tables required by GPDb schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS directors (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        site_id INTEGER,
                        name TEXT NOT NULL UNIQUE,
                        works_count INTEGER DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS movie_directors (
                        movie_id INTEGER,
                        director_id INTEGER,
                        position INTEGER DEFAULT 0,
                        PRIMARY KEY(movie_id, director_id)
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS series_collections (
                        root_title TEXT PRIMARY KEY,
                        studio_name TEXT,
                        movie_count INTEGER,
                        cover_url TEXT,
                        sample_covers TEXT
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_favorites (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        entity_type TEXT NOT NULL,
                        entity_key TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(entity_type, entity_key)
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_movie_data (
                        movie_id INTEGER PRIMARY KEY,
                        rating REAL,
                        status TEXT,
                        notes TEXT,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_glossary (
                        term TEXT PRIMARY KEY,
                        definition TEXT,
                        zh_term TEXT
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attr_glossary (
                        category TEXT,
                        attr_key TEXT,
                        zh_val TEXT,
                        PRIMARY KEY(category, attr_key)
                    );
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scraped_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        entry_type TEXT NOT NULL,
                        entry_id INTEGER NOT NULL,
                        scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        status TEXT DEFAULT 'success',
                        UNIQUE(entry_type, entry_id)
                    );
                """.trimIndent())

                // 2. Core Room Tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS movies (
                        id INTEGER PRIMARY KEY,
                        title TEXT NOT NULL,
                        studio_id INTEGER,
                        studio_name TEXT,
                        release_year INTEGER,
                        duration_mins INTEGER,
                        category TEXT,
                        rating TEXT,
                        movie_type TEXT,
                        description TEXT,
                        cover_icon TEXT,
                        cover_full TEXT,
                        cover_back TEXT,
                        covers_json TEXT,
                        director_id INTEGER,
                        director_name TEXT,
                        description_zh TEXT,
                        translation_attempts INTEGER DEFAULT 0,
                        title_zh TEXT,
                        title_attempts INTEGER DEFAULT 0,
                        scraped_at TEXT DEFAULT CURRENT_TIMESTAMP
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_year ON movies(release_year);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_studio ON movies(studio_name);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_category ON movies(category);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_movies_director ON movies(director_name);")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS performers (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        hair TEXT,
                        eyes TEXT,
                        body_hair TEXT,
                        facial_hair TEXT,
                        height TEXT,
                        weight TEXT,
                        build TEXT,
                        skin TEXT,
                        dick_size TEXT,
                        foreskin TEXT,
                        tattoos TEXT,
                        notes TEXT,
                        image_url TEXT,
                        bftv_url TEXT,
                        pbc_url TEXT,
                        scraped_at TEXT DEFAULT CURRENT_TIMESTAMP
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_performers_name ON performers(name);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_performers_build ON performers(build);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_performers_hair ON performers(hair);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_performers_eyes ON performers(eyes);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_performers_skin ON performers(skin);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_performers_body_hair ON performers(body_hair);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_performers_facial_hair ON performers(facial_hair);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_performers_image ON performers(image_url);")

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
                    CREATE TABLE IF NOT EXISTS movie_performers (
                        movie_id INTEGER NOT NULL,
                        performer_id INTEGER NOT NULL,
                        performer_name TEXT,
                        PRIMARY KEY(movie_id, performer_id),
                        FOREIGN KEY(movie_id) REFERENCES movies(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_mp_performer_id ON movie_performers(performer_id);")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS episodes (
                        id INTEGER PRIMARY KEY,
                        movie_id INTEGER,
                        title TEXT,
                        thumbnail_url TEXT,
                        description TEXT,
                        action_notes TEXT,
                        description_zh TEXT,
                        release_date TEXT,
                        studio_id INTEGER,
                        studio_name TEXT,
                        FOREIGN KEY(movie_id) REFERENCES movies(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_movie_id ON episodes(movie_id);")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS episode_performers (
                        episode_id INTEGER NOT NULL,
                        performer_id INTEGER NOT NULL,
                        performer_name TEXT,
                        PRIMARY KEY(episode_id, performer_id),
                        FOREIGN KEY(episode_id) REFERENCES episodes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ep_performer ON episode_performers(performer_id);")

                try {
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS movies_fts USING fts5(
                            id UNINDEXED,
                            title,
                            studio_name,
                            category,
                            description,
                            performers,
                            tokenize = 'unicode61'
                        );
                    """.trimIndent())
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS performers_fts USING fts5(
                            id UNINDEXED,
                            name,
                            tattoos,
                            notes,
                            tokenize = 'unicode61'
                        );
                    """.trimIndent())
                } catch (e: Exception) {
                    Log.w(TAG, "平台未启用 FTS5 或模块缺失，已跳过虚拟表创建: ${e.message}")
                }

                db.setTransactionSuccessful()
                Log.i(TAG, "沙盒基础数据库结构建表完成！")
            } finally {
                db.endTransaction()
                db.close()
            }
        }
        sandboxDir
    }
}
