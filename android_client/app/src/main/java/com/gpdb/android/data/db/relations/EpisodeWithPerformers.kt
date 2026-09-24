package com.gpdb.android.data.db.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.gpdb.android.data.db.entities.EpisodeEntity
import com.gpdb.android.data.db.entities.EpisodePerformerEntity
import com.gpdb.android.data.db.entities.PerformerEntity

data class EpisodeWithPerformers(
    @Embedded
    val episode: EpisodeEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EpisodePerformerEntity::class,
            parentColumn = "episode_id",
            entityColumn = "performer_id"
        )
    )
    val performers: List<PerformerEntity>
)
