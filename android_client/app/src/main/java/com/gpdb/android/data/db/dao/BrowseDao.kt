package com.gpdb.android.data.db.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.gpdb.android.data.db.entities.CategoryGlossaryEntity
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.SeriesCollectionEntity
import com.gpdb.android.data.db.entities.PerformerEntity

@Dao
interface BrowseDao {
    @RawQuery
    suspend fun getAllCategories(query: SupportSQLiteQuery): List<CategoryGlossaryEntity>
    
    @RawQuery
    suspend fun getAllStudios(query: SupportSQLiteQuery): List<String>
    
    @RawQuery
    suspend fun getSeriesPaged(query: SupportSQLiteQuery): List<SeriesCollectionEntity>

    @RawQuery
    suspend fun getMoviesByCategory(query: SupportSQLiteQuery): List<MovieEntity>

    @RawQuery
    suspend fun getMoviesByStudio(query: SupportSQLiteQuery): List<MovieEntity>

    @RawQuery
    suspend fun getFavoriteMovies(query: SupportSQLiteQuery): List<MovieEntity>

    @RawQuery
    suspend fun getMoviesBySeries(query: SupportSQLiteQuery): List<MovieEntity>

    @RawQuery
    suspend fun getAllPerformers(query: SupportSQLiteQuery): List<PerformerEntity>

    @RawQuery
    suspend fun getFavoritePerformers(query: SupportSQLiteQuery): List<PerformerEntity>

    @RawQuery
    suspend fun getEpisodesByStudio(query: SupportSQLiteQuery): List<com.gpdb.android.data.db.entities.EpisodeEntity>
}
