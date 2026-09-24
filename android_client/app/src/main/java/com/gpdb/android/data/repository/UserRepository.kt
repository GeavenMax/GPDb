package com.gpdb.android.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.gpdb.android.data.db.GpdbDatabase
import com.gpdb.android.data.db.dao.UserActionDao
import com.gpdb.android.data.db.dao.UserMovieData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(
    private val db: GpdbDatabase,
    private val userActionDao: UserActionDao
) {
    // ----------------------------------------------------------------------
    // Reads
    // ----------------------------------------------------------------------
    suspend fun getMovieData(movieId: Long): UserMovieData? {
        val query = SimpleSQLiteQuery("SELECT movie_id, rating, status, notes FROM user_movie_data WHERE movie_id = ?", arrayOf(movieId))
        return userActionDao.getUserMovieData(query).firstOrNull()
    }

    suspend fun isFavorite(entityType: String, entityKey: String): Boolean {
        val query = SimpleSQLiteQuery("SELECT 1 FROM user_favorites WHERE entity_type = ? AND entity_key = ? LIMIT 1", arrayOf(entityType, entityKey))
        return userActionDao.checkIsFavorite(query).isNotEmpty()
    }

    // ----------------------------------------------------------------------
    // Writes (Bypassing Room compilation checks with exact execSQL)
    // ----------------------------------------------------------------------
    suspend fun updateMovieRating(movieId: Long, rating: Float) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO user_movie_data (movie_id, rating) 
            VALUES (?, ?) 
            ON CONFLICT(movie_id) DO UPDATE SET rating=excluded.rating, updated_at=CURRENT_TIMESTAMP
            """.trimIndent(),
            arrayOf(movieId, rating)
        )
    }

    suspend fun updateMovieStatus(movieId: Long, status: String?) = withContext(Dispatchers.IO) {
        if (status == null) {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE user_movie_data SET status = NULL, updated_at = CURRENT_TIMESTAMP WHERE movie_id = ?",
                arrayOf(movieId)
            )
        } else {
            db.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO user_movie_data (movie_id, status) 
                VALUES (?, ?) 
                ON CONFLICT(movie_id) DO UPDATE SET status=excluded.status, updated_at=CURRENT_TIMESTAMP
                """.trimIndent(),
                arrayOf(movieId, status)
            )
        }
    }

    suspend fun toggleFavorite(entityType: String, entityKey: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (isFavorite) {
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR IGNORE INTO user_favorites (entity_type, entity_key) VALUES (?, ?)",
                arrayOf(entityType, entityKey)
            )
        } else {
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM user_favorites WHERE entity_type = ? AND entity_key = ?",
                arrayOf(entityType, entityKey)
            )
        }
    }
}
