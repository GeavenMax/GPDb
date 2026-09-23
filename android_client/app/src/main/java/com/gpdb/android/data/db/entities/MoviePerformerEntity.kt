package com.gpdb.android.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

// ============================================================
//  MoviePerformerEntity — 映射 `movie_performers` 关联表
//
//  SQLite TableInfo 对比说明：
//  - movie_id: NOT NULL -> Long
//  - performer_id: NOT NULL -> Long
//  - performer_name: nullable -> String?
// ============================================================
@Entity(
    tableName = "movie_performers",
    primaryKeys = ["movie_id", "performer_id"],
    indices = [
        Index(name = "idx_mp_performer_id", value = ["performer_id"])
    ],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["id"],
            childColumns = ["movie_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class MoviePerformerEntity(

    @ColumnInfo(name = "movie_id")
    val movieId: Long,

    @ColumnInfo(name = "performer_id")
    val performerId: Long,

    @ColumnInfo(name = "performer_name")
    val performerName: String? = null,
)
