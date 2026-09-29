package com.gpdb.android.data.repository

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import com.gpdb.android.data.db.dao.MovieDao
import com.gpdb.android.data.db.dao.PerformerDao
import com.gpdb.android.data.db.dao.SearchDao
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity

class SearchRepository(
    private val searchDao: SearchDao,
    private val movieDao: MovieDao? = null,
    private val performerDao: PerformerDao? = null
) {
    private var isFts5Usable = true

    private fun sanitizeFts5(query: String): String {
        val clean = query.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").trim()
        val tokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "$it*" }
    }

    suspend fun searchMovies(query: String, limit: Int = 50, offset: Int = 0): List<MovieEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ftsQuery = sanitizeFts5(trimmed)
        val likeQuery = "%$trimmed%"
        val numericId = trimmed.toLongOrNull()

        // 1. 优先尝试 FTS5 全文检索引擎
        if (isFts5Usable && ftsQuery.isNotEmpty()) {
            try {
                val ftsSql = """
                    SELECT m.* FROM movies m 
                    WHERE (
                        m.id IN (SELECT id FROM movies_fts WHERE movies_fts MATCH ?)
                        OR m.title LIKE ? 
                        OR m.title_zh LIKE ? 
                        OR m.studio_name LIKE ?
                        OR m.director_name LIKE ?
                        OR m.description_zh LIKE ?
                        ${if (numericId != null) "OR m.id = ?" else ""}
                    )
                    ORDER BY m.release_year DESC, m.id DESC 
                    LIMIT ? OFFSET ?
                """.trimIndent()
                val ftsArgs = mutableListOf<Any>(ftsQuery, likeQuery, likeQuery, likeQuery, likeQuery, likeQuery)
                if (numericId != null) ftsArgs.add(numericId)
                ftsArgs.add(limit)
                ftsArgs.add(offset)

                val ftsResults = searchDao.searchMoviesFts(SimpleSQLiteQuery(ftsSql, ftsArgs.toTypedArray()))
                if (ftsResults.isNotEmpty()) {
                    return ftsResults
                }
            } catch (e: Exception) {
                Log.w("SearchRepository", "FTS5 电影检索不可用或执行异常，自动降级至标准 SQL LIKE: ${e.message}")
                isFts5Usable = false
            }
        }

        // 2. 健壮的标准 SQL 多字段联合检索（含演员表关联、片商、导演、中文译名与简介）
        try {
            val args = mutableListOf<Any>(likeQuery, likeQuery, likeQuery, likeQuery, likeQuery, likeQuery)
            val sql = """
                SELECT m.* FROM movies m 
                WHERE (
                    m.title LIKE ? 
                    OR m.title_zh LIKE ? 
                    OR m.studio_name LIKE ? 
                    OR m.director_name LIKE ? 
                    OR m.description_zh LIKE ?
                    OR m.id IN (SELECT mp.movie_id FROM movie_performers mp WHERE mp.performer_name LIKE ?)
                    ${if (numericId != null) "OR m.id = ?" else ""}
                )
                ORDER BY m.release_year DESC, m.id DESC 
                LIMIT ? OFFSET ?
            """.trimIndent()
            if (numericId != null) args.add(numericId)
            args.add(limit)
            args.add(offset)
            val results = searchDao.searchMoviesFts(SimpleSQLiteQuery(sql, args.toTypedArray()))
            if (results.isNotEmpty()) {
                return results
            }
        } catch (e: Exception) {
            Log.w("SearchRepository", "联合多表检索异常，尝试基础单表 LIKE 兜底: ${e.message}")
        }

        // 3. 基础单表多字段检索（防止 movie_performers 表异常）
        try {
            val args = mutableListOf<Any>(likeQuery, likeQuery, likeQuery, likeQuery, likeQuery)
            val sql = """
                SELECT m.* FROM movies m 
                WHERE (
                    m.title LIKE ? 
                    OR m.title_zh LIKE ? 
                    OR m.studio_name LIKE ? 
                    OR m.director_name LIKE ? 
                    OR m.description_zh LIKE ?
                    ${if (numericId != null) "OR m.id = ?" else ""}
                )
                ORDER BY m.release_year DESC, m.id DESC 
                LIMIT ? OFFSET ?
            """.trimIndent()
            if (numericId != null) args.add(numericId)
            args.add(limit)
            args.add(offset)
            val results = searchDao.searchMoviesFts(SimpleSQLiteQuery(sql, args.toTypedArray()))
            if (results.isNotEmpty()) {
                return results
            }
        } catch (e: Exception) {
            Log.w("SearchRepository", "基础单表多字段检索异常: ${e.message}")
        }

        // 4. MovieDao 终极兜底
        return try {
            movieDao?.searchMovies(trimmed, limit, offset) ?: emptyList()
        } catch (e: Exception) {
            Log.e("SearchRepository", "所有电影搜索策略均未命中或异常: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun searchPerformers(query: String, limit: Int = 30, offset: Int = 0): List<PerformerEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ftsQuery = sanitizeFts5(trimmed)
        val likeQuery = "%$trimmed%"

        // 1. 尝试 FTS5
        if (isFts5Usable && ftsQuery.isNotEmpty()) {
            try {
                val ftsSql = """
                    SELECT p.* FROM performers p 
                    WHERE (
                        p.id IN (SELECT id FROM performers_fts WHERE performers_fts MATCH ?)
                        OR p.name LIKE ?
                    )
                    ORDER BY p.name ASC 
                    LIMIT ? OFFSET ?
                """.trimIndent()
                val ftsArgs = arrayOf<Any>(ftsQuery, likeQuery, limit, offset)
                val ftsResults = searchDao.searchPerformersFts(SimpleSQLiteQuery(ftsSql, ftsArgs))
                if (ftsResults.isNotEmpty()) {
                    return ftsResults
                }
            } catch (e: Exception) {
                Log.w("SearchRepository", "FTS5 演员检索不可用或异常: ${e.message}")
                isFts5Usable = false
            }
        }

        // 2. PerformerDao / 原生 SQL LIKE 降级
        return try {
            performerDao?.searchPerformers(trimmed, limit) ?: run {
                val sql = """
                    SELECT p.* FROM performers p 
                    WHERE p.name LIKE ?
                    ORDER BY p.name ASC 
                    LIMIT ? OFFSET ?
                """.trimIndent()
                val args = arrayOf<Any>(likeQuery, limit, offset)
                searchDao.searchPerformersFts(SimpleSQLiteQuery(sql, args))
            }
        } catch (e: Exception) {
            Log.e("SearchRepository", "演员检索失败: ${e.message}", e)
            emptyList()
        }
    }
}
