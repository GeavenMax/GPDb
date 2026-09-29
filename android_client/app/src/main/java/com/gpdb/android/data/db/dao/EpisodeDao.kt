package com.gpdb.android.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.gpdb.android.data.db.entities.EpisodeEntity
import com.gpdb.android.data.db.entities.PerformerEntity
import com.gpdb.android.data.db.relations.EpisodeWithPerformers

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: Long): EpisodeEntity?

    @androidx.room.Transaction
    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisodeWithPerformers(episodeId: Long): EpisodeWithPerformers?

    /** 增量刮削写入单条分集 */
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    /** 增量批量插入分集 */
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long>

    /** 插入分集-演员关联 */
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertEpisodePerformers(links: List<com.gpdb.android.data.db.entities.EpisodePerformerEntity>): List<Long>

    /** 查询所有已收录的分集 ID */
    @Query("SELECT id FROM episodes")
    suspend fun getAllEpisodeIds(): List<Long>

    /** 分页查询分集列表（默认按发售日期与 ID 倒序） */
    @Query("SELECT * FROM episodes ORDER BY release_date DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getEpisodesPaged(limit: Int, offset: Int): List<EpisodeEntity>

    /** 分页查询指定发布日期之后的分集 */
    @Query("SELECT * FROM episodes WHERE release_date >= :minDate ORDER BY release_date DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getEpisodesByMinDate(minDate: String, limit: Int, offset: Int): List<EpisodeEntity>

    /** 获取分集总数 */
    @Query("SELECT COUNT(*) FROM episodes")
    suspend fun getEpisodeCount(): Int
}
