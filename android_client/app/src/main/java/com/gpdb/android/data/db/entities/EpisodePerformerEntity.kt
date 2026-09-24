package com.gpdb.android.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "episode_performers",
    primaryKeys = ["episode_id", "performer_id"],
    indices = [
        Index(name = "idx_ep_performer", value = ["performer_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EpisodePerformerEntity(
    @ColumnInfo(name = "episode_id")
    val episodeId: Long,

    @ColumnInfo(name = "performer_id")
    val performerId: Long,

    @ColumnInfo(name = "performer_name")
    val performerName: String? = null
)
