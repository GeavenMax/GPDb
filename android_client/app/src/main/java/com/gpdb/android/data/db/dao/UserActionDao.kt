package com.gpdb.android.data.db.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

// Note: These classes are just POJOs, not Room @Entity to avoid FUSE validation issues
data class UserMovieData(
    val movie_id: Long,
    val rating: Float?,
    val status: String?,
    val notes: String?
)

@Dao
interface UserActionDao {
    @RawQuery
    suspend fun getUserMovieData(query: SupportSQLiteQuery): List<UserMovieData>
    
    @RawQuery
    suspend fun checkIsFavorite(query: SupportSQLiteQuery): List<Int>
}
