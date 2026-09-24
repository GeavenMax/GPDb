package com.gpdb.android.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    indices = [
        Index(name = "idx_episodes_movie_id", value = ["movie_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["id"],
            childColumns = ["movie_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EpisodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long? = null,

    @ColumnInfo(name = "movie_id")
    val movieId: Long? = null,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "action_notes")
    val actionNotes: String? = null,

    @ColumnInfo(name = "description_zh")
    val descriptionZh: String? = null,

    @ColumnInfo(name = "release_date")
    val releaseDate: String? = null,

    @ColumnInfo(name = "studio_id")
    val studioId: Long? = null,

    @ColumnInfo(name = "studio_name")
    val studioName: String? = null
)
