package com.gpdb.android.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity

// ============================================================
//  PerformerDao — 演员数据访问对象
// ============================================================
@Dao
interface PerformerDao {

    /** 演员总数 */
    @Query("SELECT COUNT(*) FROM performers")
    suspend fun getPerformerCount(): Int

    /** 分页查询演员列表 */
    @Query("SELECT * FROM performers ORDER BY name ASC LIMIT :limit OFFSET :offset")
    suspend fun getPerformersPaged(limit: Int, offset: Int): List<PerformerEntity>

    /** 根据 ID 获取演员详情 */
    @Query("SELECT * FROM performers WHERE id = :performerId LIMIT 1")
    suspend fun getPerformerById(performerId: Long): PerformerEntity?

    /** 按姓名模糊搜索演员 */
    @Query("SELECT * FROM performers WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchPerformers(query: String, limit: Int = 30): List<PerformerEntity>

    /** 查询指定演员出演的所有影片 (手动 JOIN 方式) */
    @Query("""
        SELECT m.* FROM movies m
        INNER JOIN movie_performers mp ON m.id = mp.movie_id
        WHERE mp.performer_id = :performerId
        ORDER BY m.release_year DESC, m.id DESC
    """)
    suspend fun getMoviesForPerformer(performerId: Long): List<MovieEntity>
    
    /** 联表查询演员及其出演影片 (Room Relation 方式) */
    @androidx.room.Transaction
    @Query("SELECT * FROM performers WHERE id = :performerId LIMIT 1")
    suspend fun getPerformerWithMovies(performerId: Long): com.gpdb.android.data.db.relations.PerformerWithMovies?

    /** 查询指定演员出演的分集 (Episodes) */
    @Query("""
        SELECT e.* FROM episodes e
        INNER JOIN episode_performers ep ON e.id = ep.episode_id
        WHERE ep.performer_id = :performerId
        ORDER BY e.release_date DESC, e.id DESC
    """)
    suspend fun getEpisodesForPerformer(performerId: Long): List<com.gpdb.android.data.db.entities.EpisodeEntity>
}
