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
}
