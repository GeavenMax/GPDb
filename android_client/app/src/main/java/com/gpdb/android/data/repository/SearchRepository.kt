package com.gpdb.android.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.gpdb.android.data.db.dao.SearchDao
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity

class SearchRepository(private val searchDao: SearchDao) {

    suspend fun searchMovies(query: String, limit: Int = 30, offset: Int = 0): List<MovieEntity> {
        if (query.isBlank()) return emptyList()
        val ftsQuery = "\"$query\"*" 
        val sqliteQuery = SimpleSQLiteQuery(
            "SELECT m.* FROM movies m JOIN movies_fts fts ON m.id = fts.id WHERE movies_fts MATCH ? ORDER BY m.release_year DESC, m.id DESC LIMIT ? OFFSET ?",
            arrayOf(ftsQuery, limit, offset)
        )
        return searchDao.searchMoviesFts(sqliteQuery)
    }

    suspend fun searchPerformers(query: String, limit: Int = 30, offset: Int = 0): List<PerformerEntity> {
        if (query.isBlank()) return emptyList()
        val ftsQuery = "\"$query\"*"
        val sqliteQuery = SimpleSQLiteQuery(
            "SELECT p.* FROM performers p JOIN performers_fts fts ON p.id = fts.id WHERE performers_fts MATCH ? ORDER BY p.name ASC LIMIT ? OFFSET ?",
            arrayOf(ftsQuery, limit, offset)
        )
        return searchDao.searchPerformersFts(sqliteQuery)
    }
}
