package com.gpdb.android.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ============================================================
//  PerformerEntity — 映射 `performers` 表 (精确匹配 SQLite Nullability 与 DefaultValue)
// ============================================================
@Entity(
    tableName = "performers",
    indices = [
        Index(name = "idx_performers_name", value = ["name"]),
        Index(name = "idx_performers_build", value = ["build"]),
        Index(name = "idx_performers_hair", value = ["hair"]),
        Index(name = "idx_performers_eyes", value = ["eyes"]),
        Index(name = "idx_performers_skin", value = ["skin"]),
        Index(name = "idx_performers_body_hair", value = ["body_hair"]),
        Index(name = "idx_performers_facial_hair", value = ["facial_hair"]),
        Index(name = "idx_performers_image", value = ["image_url"]),
    ]
)
data class PerformerEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "hair")
    val hair: String? = null,

    @ColumnInfo(name = "eyes")
    val eyes: String? = null,

    @ColumnInfo(name = "body_hair")
    val bodyHair: String? = null,

    @ColumnInfo(name = "facial_hair")
    val facialHair: String? = null,

    @ColumnInfo(name = "height")
    val height: String? = null,

    @ColumnInfo(name = "weight")
    val weight: String? = null,

    @ColumnInfo(name = "build")
    val build: String? = null,

    @ColumnInfo(name = "skin")
    val skin: String? = null,

    @ColumnInfo(name = "dick_size")
    val dickSize: String? = null,

    @ColumnInfo(name = "foreskin")
    val foreskin: String? = null,

    @ColumnInfo(name = "tattoos")
    val tattoos: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "bftv_url")
    val bftvUrl: String? = null,

    @ColumnInfo(name = "scraped_at", defaultValue = "CURRENT_TIMESTAMP")
    val scrapedAt: String? = null,
)
