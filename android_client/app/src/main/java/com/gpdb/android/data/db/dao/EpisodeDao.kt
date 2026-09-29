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
}
