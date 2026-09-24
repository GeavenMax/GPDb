package com.gpdb.android.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity
import kotlinx.coroutines.flow.Flow

// ============================================================
//  MovieDao — 影片数据访问对象
// ============================================================
@Dao
interface MovieDao {

    /** 影片总数 */
    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getMovieCount(): Int

    /** 分页流式查询影片列表（默认按 ID 降序） */
    @Query("SELECT * FROM movies ORDER BY id DESC LIMIT :limit OFFSET :offset")
    fun getMoviesPaged(limit: Int, offset: Int): Flow<List<MovieEntity>>

    /** 直接挂起查询影片分页 */
    @Query("SELECT * FROM movies ORDER BY id DESC LIMIT :limit OFFSET :offset")
    suspend fun getMoviesList(limit: Int, offset: Int): List<MovieEntity>

    /** 按发布年份降序查询影片分页 */
    @Query("SELECT * FROM movies ORDER BY release_year DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getMoviesByYear(limit: Int, offset: Int): List<MovieEntity>

    /** 根据 ID 获取单部影片详情 */
    @Query("SELECT * FROM movies WHERE id = :movieId LIMIT 1")
    suspend fun getMovieById(movieId: Long): MovieEntity?

    /** 全局模糊搜索（英文名、中文名、片商名） */
    @Query("""
        SELECT * FROM movies 
        WHERE title LIKE '%' || :query || '%' 
           OR title_zh LIKE '%' || :query || '%' 
           OR studio_name LIKE '%' || :query || '%'
        ORDER BY id DESC LIMIT :limit OFFSET :offset
    """)
    suspend fun searchMovies(query: String, limit: Int = 50, offset: Int = 0): List<MovieEntity>

    /** 查询指定影片的所有出演演员 (手动 JOIN 方式) */
    @Query("""
        SELECT p.* FROM performers p
        INNER JOIN movie_performers mp ON p.id = mp.performer_id
        WHERE mp.movie_id = :movieId
        ORDER BY p.name ASC
    """)
    suspend fun getPerformersForMovie(movieId: Long): List<PerformerEntity>
    
    /** 联表查询影片及关联演员 (Room Relation 方式) */
    @androidx.room.Transaction
    @Query("SELECT * FROM movies WHERE id = :movieId LIMIT 1")
    suspend fun getMovieWithPerformers(movieId: Long): com.gpdb.android.data.db.relations.MovieWithPerformers?

    /** 查询指定影片包含的所有分集 */
    @Query("SELECT * FROM episodes WHERE movie_id = :movieId ORDER BY release_date ASC, id ASC")
    suspend fun getEpisodesForMovie(movieId: Long): List<com.gpdb.android.data.db.entities.EpisodeEntity>
}
