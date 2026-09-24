package com.gpdb.android.data.db.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity

@Dao
interface SearchDao {
    /** 
     * 全局搜片 - 基于 FTS5 虚拟表，实现毫秒级召回
     */
    @RawQuery
    suspend fun searchMoviesFts(query: SupportSQLiteQuery): List<MovieEntity>
    
    /** 
     * 全局搜演员 - 基于 FTS5
     */
    @RawQuery
    suspend fun searchPerformersFts(query: SupportSQLiteQuery): List<PerformerEntity>
}
