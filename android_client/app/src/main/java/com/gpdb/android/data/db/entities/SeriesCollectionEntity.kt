package com.gpdb.android.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series_collections",
    indices = [
        Index(name = "idx_series_collections_root", value = ["root_title"]),
        Index(name = "idx_series_collections_studio", value = ["studio_name"])
    ]
)
data class SeriesCollectionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    
    @ColumnInfo(name = "root_title")
    val rootTitle: String,
    
    @ColumnInfo(name = "studio_name")
    val studioName: String? = null,
    
    @ColumnInfo(name = "movie_count", defaultValue = "0")
    val movieCount: Int = 0,
    
    @ColumnInfo(name = "cover_url")
    val coverUrl: String? = null,
    
    @ColumnInfo(name = "year_start")
    val yearStart: Int? = null,
    
    @ColumnInfo(name = "year_end")
    val yearEnd: Int? = null,
    
    @ColumnInfo(name = "sample_movie_ids")
    val sampleMovieIds: String? = null,
    
    @ColumnInfo(name = "sample_covers")
    val sampleCovers: String? = null,
    
    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP")
    val updatedAt: String? = null
)
