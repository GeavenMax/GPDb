package com.gpdb.android.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.gpdb.android.data.db.dao.SearchDao
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity

class SearchRepository(private val searchDao: SearchDao) {

    private fun sanitizeFts5(query: String): String {
        val clean = query.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").trim()
        val tokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "$it*" }
    }

    suspend fun searchMovies(query: String, limit: Int = 30, offset: Int = 0): List<MovieEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ftsQuery = sanitizeFts5(trimmed)
        val likeQuery = "%$trimmed%"

        val sql = if (ftsQuery.isNotEmpty()) {
            """
            SELECT m.* FROM movies m 
            WHERE (
                m.id IN (SELECT id FROM movies_fts WHERE movies_fts MATCH ?)
                OR m.title LIKE ? 
                OR m.title_zh LIKE ? 
                OR m.description_zh LIKE ?
            )
            ORDER BY m.release_year DESC, m.id DESC 
            LIMIT ? OFFSET ?
            """.trimIndent()
        } else {
            """
            SELECT m.* FROM movies m 
            WHERE (m.title LIKE ? OR m.title_zh LIKE ? OR m.description_zh LIKE ?)
            ORDER BY m.release_year DESC, m.id DESC 
            LIMIT ? OFFSET ?
            """.trimIndent()
        }

        val args: Array<Any> = if (ftsQuery.isNotEmpty()) {
            arrayOf(ftsQuery, likeQuery, likeQuery, likeQuery, limit, offset)
        } else {
            arrayOf(likeQuery, likeQuery, likeQuery, limit, offset)
        }

        return searchDao.searchMoviesFts(SimpleSQLiteQuery(sql, args))
    }

    suspend fun searchPerformers(query: String, limit: Int = 30, offset: Int = 0): List<PerformerEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ftsQuery = sanitizeFts5(trimmed)
        val likeQuery = "%$trimmed%"

        val sql = if (ftsQuery.isNotEmpty()) {
            """
            SELECT p.* FROM performers p 
            WHERE (
                p.id IN (SELECT id FROM performers_fts WHERE performers_fts MATCH ?)
                OR p.name LIKE ?
            )
            ORDER BY p.name ASC 
            LIMIT ? OFFSET ?
            """.trimIndent()
        } else {
            """
            SELECT p.* FROM performers p 
            WHERE p.name LIKE ?
            ORDER BY p.name ASC 
            LIMIT ? OFFSET ?
            """.trimIndent()
        }

        val args: Array<Any> = if (ftsQuery.isNotEmpty()) {
            arrayOf(ftsQuery, likeQuery, limit, offset)
        } else {
            arrayOf(likeQuery, limit, offset)
        }

        return searchDao.searchPerformersFts(SimpleSQLiteQuery(sql, args))
    }
}
