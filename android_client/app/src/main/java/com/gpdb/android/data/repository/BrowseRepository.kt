package com.gpdb.android.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.gpdb.android.data.db.dao.BrowseDao
import com.gpdb.android.data.db.entities.CategoryGlossaryEntity
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.SeriesCollectionEntity
import com.gpdb.android.data.db.entities.PerformerEntity

class BrowseRepository(private val browseDao: BrowseDao) {

    suspend fun getAllCategories(): List<CategoryGlossaryEntity> {
        val query = SimpleSQLiteQuery("SELECT * FROM category_glossary ORDER BY term ASC")
        return browseDao.getAllCategories(query)
    }

    suspend fun getAllStudios(sortBy: String = "works", search: String? = null): List<String> {
        val searchCondition = if (!search.isNullOrBlank()) "AND m.studio_name LIKE ?" else ""
        val args = if (!search.isNullOrBlank()) arrayOf("%${search}%") else emptyArray<Any>()
        val queryStr = if (sortBy == "name") {
            "SELECT studio_name FROM movies m WHERE studio_name IS NOT NULL AND studio_name != '' $searchCondition GROUP BY studio_name ORDER BY studio_name ASC"
        } else {
            "SELECT m.studio_name FROM movies m LEFT JOIN (SELECT studio_name, COUNT(*) as ep_count FROM episodes WHERE studio_name IS NOT NULL GROUP BY studio_name) e ON m.studio_name = e.studio_name WHERE m.studio_name IS NOT NULL AND m.studio_name != '' $searchCondition GROUP BY m.studio_name ORDER BY (COUNT(m.id) + IFNULL(MAX(e.ep_count), 0)) DESC, m.studio_name ASC"
        }
        val query = SimpleSQLiteQuery(queryStr, args)
        return browseDao.getAllStudios(query)
    }

    suspend fun getSeriesPaged(limit: Int = 100, offset: Int = 0): List<SeriesCollectionEntity> {
        val query = SimpleSQLiteQuery("SELECT * FROM series_collections ORDER BY movie_count DESC, root_title ASC LIMIT ? OFFSET ?", arrayOf(limit, offset))
        return browseDao.getSeriesPaged(query)
    }

    suspend fun getMoviesByCategory(category: String, limit: Int = 50, offset: Int = 0): List<MovieEntity> {
        val query = SimpleSQLiteQuery("SELECT * FROM movies WHERE category LIKE '%' || ? || '%' ORDER BY release_year DESC, id DESC LIMIT ? OFFSET ?", arrayOf(category, limit, offset))
        return browseDao.getMoviesByCategory(query)
    }

    suspend fun getMoviesByStudio(studio: String, limit: Int = 50, offset: Int = 0): List<MovieEntity> {
        val query = SimpleSQLiteQuery("SELECT * FROM movies WHERE studio_name = ? ORDER BY release_year DESC, id DESC LIMIT ? OFFSET ?", arrayOf(studio, limit, offset))
        return browseDao.getMoviesByStudio(query)
    }

    suspend fun getEpisodesByStudio(studio: String, limit: Int = 50, offset: Int = 0): List<com.gpdb.android.data.db.entities.EpisodeEntity> {
        val query = SimpleSQLiteQuery("SELECT * FROM episodes WHERE studio_name = ? ORDER BY release_date DESC, id DESC LIMIT ? OFFSET ?", arrayOf(studio, limit, offset))
        return browseDao.getEpisodesByStudio(query)
    }



    suspend fun getLibraryMovies(type: String, limit: Int = 50, offset: Int = 0, search: String? = null): List<MovieEntity> {
        val searchCondition = if (!search.isNullOrBlank()) "AND m.title LIKE ?" else ""
        val args = if (!search.isNullOrBlank()) arrayOf("%${search}%", limit, offset) else arrayOf(limit, offset)
        val sql = when(type) {
            "wishlist" -> "SELECT m.* FROM movies m JOIN user_movie_data d ON m.id = d.movie_id WHERE d.status = 'wishlist' $searchCondition ORDER BY d.updated_at DESC LIMIT ? OFFSET ?"
            "watched" -> "SELECT m.* FROM movies m JOIN user_movie_data d ON m.id = d.movie_id WHERE d.status = 'watched' $searchCondition ORDER BY d.updated_at DESC LIMIT ? OFFSET ?"
            else -> "SELECT m.* FROM movies m JOIN user_favorites f ON CAST(m.id AS TEXT) = f.entity_key WHERE f.entity_type = 'movie' $searchCondition ORDER BY f.created_at DESC LIMIT ? OFFSET ?"
        }
        return browseDao.getFavoriteMovies(SimpleSQLiteQuery(sql, args))
    }

    suspend fun getPerformersPaged(sortBy: String = "works", limit: Int = 50, offset: Int = 0, queryStr: String? = null): List<PerformerEntity> {
        val orderClause = if (sortBy == "name") "name ASC" else "((SELECT COUNT(*) FROM movie_performers WHERE performer_id = performers.id) + (SELECT COUNT(*) FROM episode_performers WHERE performer_id = performers.id)) DESC, name ASC"
        val whereClause = if (!queryStr.isNullOrBlank()) "WHERE name LIKE ?" else ""
        val args = if (!queryStr.isNullOrBlank()) arrayOf("%${queryStr}%", limit, offset) else arrayOf(limit, offset)
        val query = SimpleSQLiteQuery("SELECT * FROM performers $whereClause ORDER BY $orderClause LIMIT ? OFFSET ?", args)
        return browseDao.getAllPerformers(query)
    }

    suspend fun getFavoritePerformers(limit: Int = 50, offset: Int = 0, search: String? = null): List<PerformerEntity> {
        val searchCondition = if (!search.isNullOrBlank()) "AND p.name LIKE ?" else ""
        val args = if (!search.isNullOrBlank()) arrayOf("%${search}%", limit, offset) else arrayOf(limit, offset)
        val sql = "SELECT p.* FROM performers p JOIN user_favorites f ON CAST(p.id AS TEXT) = f.entity_key WHERE f.entity_type = 'performer' $searchCondition ORDER BY f.created_at DESC LIMIT ? OFFSET ?"
        return browseDao.getFavoritePerformers(SimpleSQLiteQuery(sql, args))
    }

    suspend fun getFavoriteSeries(limit: Int = 50, offset: Int = 0, search: String? = null): List<SeriesCollectionEntity> {
        val searchCondition = if (!search.isNullOrBlank()) "AND s.root_title LIKE ?" else ""
        val args = if (!search.isNullOrBlank()) arrayOf("%${search}%", limit, offset) else arrayOf(limit, offset)
        val sql = "SELECT s.* FROM series_collections s JOIN user_favorites f ON s.root_title = f.entity_key WHERE f.entity_type = 'series' $searchCondition ORDER BY f.created_at DESC LIMIT ? OFFSET ?"
        return browseDao.getFavoriteSeries(SimpleSQLiteQuery(sql, args))
    }
    suspend fun getMoviesBySeries(seriesRoot: String, limit: Int = 50, offset: Int = 0): List<MovieEntity> {
        val query = SimpleSQLiteQuery("SELECT * FROM movies WHERE title LIKE ? || '%' OR title LIKE '%' || ? || '%' ORDER BY release_year DESC, id DESC LIMIT ? OFFSET ?", arrayOf(seriesRoot, seriesRoot, limit, offset))
        return browseDao.getMoviesBySeries(query)
    }

    suspend fun getMoviesByDirector(director: String, limit: Int = 50, offset: Int = 0): List<MovieEntity> {
        val query = SimpleSQLiteQuery("SELECT m.* FROM movies m JOIN movie_directors md ON m.id = md.movie_id JOIN directors d ON d.id = md.director_id WHERE d.name = ? ORDER BY m.release_year DESC, m.id DESC LIMIT ? OFFSET ?", arrayOf(director, limit, offset))
        return browseDao.getMoviesByDirector(query)
    }
}
