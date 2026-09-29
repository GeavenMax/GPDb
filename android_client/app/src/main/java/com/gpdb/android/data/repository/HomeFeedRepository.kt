package com.gpdb.android.data.repository

import android.database.Cursor
import android.util.Log
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.SeriesCollectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeSpotlightMovie(
    val id: Long,
    val title: String,
    val titleZh: String?,
    val studioName: String?,
    val directorName: String?,
    val releaseYear: Int?,
    val coverFull: String?,
    val coverBack: String?,
    val descriptionZh: String?,
    val description: String?,
    val rating: String?,
    val category: String?
)

data class HomeAnniversaryItem(
    val episodeId: Long,
    val episodeTitle: String?,
    val releaseDate: String?,
    val movieId: Long?,
    val movieTitle: String?,
    val movieTitleZh: String?,
    val coverFull: String?,
    val studioName: String?,
    val yearsAgo: Int
)

data class HomeFeaturedPerformer(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val build: String?,
    val hair: String?,
    val worksCount: Int
)

data class HomeFeedData(
    val spotlightMovies: List<HomeSpotlightMovie> = emptyList(),
    val onThisDay: List<HomeAnniversaryItem> = emptyList(),
    val starSpotlight: List<HomeFeaturedPerformer> = emptyList(),
    val seriesList: List<SeriesCollectionEntity> = emptyList(),
    val luckyMovies: List<MovieEntity> = emptyList(),
    val totalMovies: Int = 0,
    val totalEpisodes: Int = 0,
    val totalPerformers: Int = 0,
    val totalStudios: Int = 0,
    val isLuckyLoading: Boolean = false
)

class HomeFeedRepository {

    companion object {
        private const val TAG = "HomeFeedRepository"
    }

    suspend fun getHomeFeed(): HomeFeedData = withContext(Dispatchers.IO) {
        val db = DatabaseHolder.db ?: return@withContext HomeFeedData()
        val sdb = db.openHelper.readableDatabase

        val spotlight = mutableListOf<HomeSpotlightMovie>()
        val onThisDay = mutableListOf<HomeAnniversaryItem>()
        val starSpotlight = mutableListOf<HomeFeaturedPerformer>()
        val seriesList = mutableListOf<SeriesCollectionEntity>()
        val luckyMovies = mutableListOf<MovieEntity>()
        var totalMovies = 0
        var totalEpisodes = 0
        var totalPerformers = 0
        var totalStudios = 0

        // 1. 焦点推荐电影 (Spotlight Movies: 12 部有封面且有剧情简介的高质量作品)
        try {
            val spotSql = """
                SELECT id, title, title_zh, studio_name, director_name, release_year,
                       cover_full, cover_back, description_zh, description, rating, category
                FROM movies
                WHERE cover_full IS NOT NULL AND trim(cover_full) != ''
                  AND ((description_zh IS NOT NULL AND trim(description_zh) != '') OR (description IS NOT NULL AND trim(description) != ''))
                ORDER BY RANDOM()
                LIMIT 12
            """.trimIndent()
            sdb.query(spotSql).use { c ->
                while (c.moveToNext()) {
                    spotlight.add(
                        HomeSpotlightMovie(
                            id = c.getLong(0),
                            title = c.getString(1) ?: "",
                            titleZh = c.getString(2),
                            studioName = c.getString(3),
                            directorName = c.getString(4),
                            releaseYear = if (!c.isNull(5)) c.getInt(5) else null,
                            coverFull = c.getString(6),
                            coverBack = c.getString(7),
                            descriptionZh = c.getString(8),
                            description = c.getString(9),
                            rating = c.getString(10),
                            category = c.getString(11)
                        )
                    )
                }
            }
            Log.d(TAG, "加载焦点推荐完成: ${spotlight.size} 部")
        } catch (e: Exception) {
            Log.e(TAG, "加载焦点推荐异常: ${e.message}", e)
        }

        // 2. 往年今日 · 经典首映 (On This Day in History: 当天 MM-DD 发售的分集场景)
        try {
            val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
            val todayMd = sdf.format(Date())
            val otdSql = """
                SELECT e.id, e.title, e.release_date, m.id, m.title, m.title_zh,
                       COALESCE(NULLIF(trim(e.thumbnail_url), ''), m.cover_full),
                       COALESCE(m.studio_name, e.studio_name),
                       (CAST(strftime('%Y', 'now') AS INTEGER) - CAST(substr(e.release_date, 1, 4) AS INTEGER))
                FROM episodes e
                LEFT JOIN movies m ON e.movie_id = m.id
                WHERE substr(e.release_date, 6, 5) = ?
                ORDER BY e.release_date DESC
                LIMIT 20
            """.trimIndent()

            sdb.query(otdSql, arrayOf(todayMd)).use { c ->
                while (c.moveToNext()) {
                    val yearsAgoRaw = if (!c.isNull(8)) c.getInt(8) else 0
                    onThisDay.add(
                        HomeAnniversaryItem(
                            episodeId = c.getLong(0),
                            episodeTitle = c.getString(1),
                            releaseDate = c.getString(2),
                            movieId = if (!c.isNull(3)) c.getLong(3) else null,
                            movieTitle = c.getString(4),
                            movieTitleZh = c.getString(5),
                            coverFull = c.getString(6),
                            studioName = c.getString(7),
                            yearsAgo = maxOf(0, yearsAgoRaw)
                        )
                    )
                }
            }

            // 往年今日无匹配时降级为本月历史
            if (onThisDay.isEmpty()) {
                val monthOnly = todayMd.take(2)
                val fbSql = """
                    SELECT e.id, e.title, e.release_date, m.id, m.title, m.title_zh,
                           COALESCE(NULLIF(trim(e.thumbnail_url), ''), m.cover_full),
                           COALESCE(m.studio_name, e.studio_name),
                           (CAST(strftime('%Y', 'now') AS INTEGER) - CAST(substr(e.release_date, 1, 4) AS INTEGER))
                    FROM episodes e
                    LEFT JOIN movies m ON e.movie_id = m.id
                    WHERE substr(e.release_date, 6, 2) = ?
                    ORDER BY RANDOM()
                    LIMIT 15
                """.trimIndent()
                sdb.query(fbSql, arrayOf(monthOnly)).use { c ->
                    while (c.moveToNext()) {
                        val yearsAgoRaw = if (!c.isNull(8)) c.getInt(8) else 0
                        onThisDay.add(
                            HomeAnniversaryItem(
                                episodeId = c.getLong(0),
                                episodeTitle = c.getString(1),
                                releaseDate = c.getString(2),
                                movieId = if (!c.isNull(3)) c.getLong(3) else null,
                                movieTitle = c.getString(4),
                                movieTitleZh = c.getString(5),
                                coverFull = c.getString(6),
                                studioName = c.getString(7),
                                yearsAgo = maxOf(0, yearsAgoRaw)
                            )
                        )
                    }
                }
            }
            Log.d(TAG, "加载往年今日完成: ${onThisDay.size} 部")
        } catch (e: Exception) {
            Log.e(TAG, "加载往年今日异常: ${e.message}", e)
        }

        // 3. 今日星光 · 标志面孔 (Star Spotlight: 作品数 >= 5 的高活跃度演员)
        try {
            val perfSql = """
                SELECT p.id, p.name, p.image_url, p.build, p.hair, COUNT(mp.movie_id) as works_count
                FROM performers p
                JOIN movie_performers mp ON p.id = mp.performer_id
                WHERE p.image_url IS NOT NULL AND trim(p.image_url) != ''
                GROUP BY p.id
                HAVING works_count >= 5
                ORDER BY RANDOM()
                LIMIT 20
            """.trimIndent()
            sdb.query(perfSql).use { c ->
                while (c.moveToNext()) {
                    starSpotlight.add(
                        HomeFeaturedPerformer(
                            id = c.getLong(0),
                            name = c.getString(1) ?: "",
                            imageUrl = c.getString(2),
                            build = c.getString(3),
                            hair = c.getString(4),
                            worksCount = c.getInt(5)
                        )
                    )
                }
            }
            Log.d(TAG, "加载今日星光完成: ${starSpotlight.size} 位")
        } catch (e: Exception) {
            Log.e(TAG, "加载今日星光异常: ${e.message}", e)
        }

        // 4. 经典系列 (Series Collections: 热门多部曲大放送)
        try {
            val seriesSql = """
                SELECT id, root_title, studio_name, movie_count, year_start, year_end, sample_covers
                FROM series_collections
                ORDER BY movie_count DESC
                LIMIT 10
            """.trimIndent()
            sdb.query(seriesSql).use { c ->
                while (c.moveToNext()) {
                    seriesList.add(
                        SeriesCollectionEntity(
                            id = c.getLong(0),
                            rootTitle = c.getString(1) ?: "",
                            studioName = c.getString(2),
                            movieCount = c.getInt(3),
                            yearStart = if (!c.isNull(4)) c.getInt(4) else null,
                            yearEnd = if (!c.isNull(5)) c.getInt(5) else null,
                            sampleCovers = c.getString(6)
                        )
                    )
                }
            }
            Log.d(TAG, "加载经典系列完成: ${seriesList.size} 组")
        } catch (e: Exception) {
            Log.e(TAG, "加载经典系列异常: ${e.message}", e)
        }

        // 5. 随心探索 · 盲盒发现 (Lucky Discovery: 随机 6 部影片)
        try {
            val luckySql = """
                SELECT * FROM movies
                WHERE cover_full IS NOT NULL AND trim(cover_full) != ''
                ORDER BY RANDOM()
                LIMIT 6
            """.trimIndent()
            sdb.query(luckySql).use { c ->
                while (c.moveToNext()) {
                    luckyMovies.add(readMovieFromCursor(c))
                }
            }
            Log.d(TAG, "加载盲盒发现完成: ${luckyMovies.size} 部")
        } catch (e: Exception) {
            Log.e(TAG, "加载盲盒发现异常: ${e.message}", e)
        }

        // 6. 统计磁贴 (Quick Stats)
        try {
            sdb.query("SELECT count(*) FROM movies").use { c ->
                if (c.moveToFirst()) totalMovies = c.getInt(0)
            }
            sdb.query("SELECT count(*) FROM episodes").use { c ->
                if (c.moveToFirst()) totalEpisodes = c.getInt(0)
            }
            sdb.query("SELECT count(*) FROM performers").use { c ->
                if (c.moveToFirst()) totalPerformers = c.getInt(0)
            }
            sdb.query("SELECT count(DISTINCT NULLIF(trim(studio_name), '')) FROM movies").use { c ->
                if (c.moveToFirst()) totalStudios = c.getInt(0)
            }
            Log.d(TAG, "加载统计磁贴完成: $totalMovies 部影片, $totalEpisodes 个分集")
        } catch (e: Exception) {
            Log.e(TAG, "加载统计磁贴异常: ${e.message}", e)
        }

        HomeFeedData(
            spotlightMovies = spotlight,
            onThisDay = onThisDay,
            starSpotlight = starSpotlight,
            seriesList = seriesList,
            luckyMovies = luckyMovies,
            totalMovies = totalMovies,
            totalEpisodes = totalEpisodes,
            totalPerformers = totalPerformers,
            totalStudios = totalStudios
        )
    }

    suspend fun getMoreLuckyMovies(): List<MovieEntity> = withContext(Dispatchers.IO) {
        val db = DatabaseHolder.db ?: return@withContext emptyList()
        val sdb = db.openHelper.readableDatabase
        val list = mutableListOf<MovieEntity>()
        val luckySql = """
            SELECT * FROM movies
            WHERE cover_full IS NOT NULL AND trim(cover_full) != ''
            ORDER BY RANDOM()
            LIMIT 6
        """.trimIndent()
        try {
            sdb.query(luckySql, emptyArray<Any>()).use { c ->
                while (c.moveToNext()) {
                    list.add(readMovieFromCursor(c))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "刷新盲盒发现异常", e)
        }
        list
    }

    private fun readMovieFromCursor(c: Cursor): MovieEntity {
        fun col(name: String) = c.getColumnIndex(name)
        val id = c.getLong(col("id"))
        val title = c.getString(col("title")) ?: ""
        val studioId = if (!c.isNull(col("studio_id"))) c.getLong(col("studio_id")) else null
        val studioName = c.getString(col("studio_name"))
        val releaseYear = if (!c.isNull(col("release_year"))) c.getInt(col("release_year")) else null
        val durationMins = if (!c.isNull(col("duration_mins"))) c.getInt(col("duration_mins")) else null
        val category = c.getString(col("category"))
        val rating = c.getString(col("rating"))
        val movieType = c.getString(col("movie_type"))
        val description = c.getString(col("description"))
        val coverIcon = c.getString(col("cover_icon"))
        val coverFull = c.getString(col("cover_full"))
        val coverBack = if (col("cover_back") >= 0) c.getString(col("cover_back")) else null
        val titleZh = if (col("title_zh") >= 0) c.getString(col("title_zh")) else null
        val descriptionZh = if (col("description_zh") >= 0) c.getString(col("description_zh")) else null

        return MovieEntity(
            id = id,
            title = title,
            studioId = studioId,
            studioName = studioName,
            releaseYear = releaseYear,
            durationMins = durationMins,
            category = category,
            rating = rating,
            movieType = movieType,
            description = description,
            coverIcon = coverIcon,
            coverFull = coverFull,
            coverBack = coverBack,
            titleZh = titleZh,
            descriptionZh = descriptionZh
        )
    }
}
