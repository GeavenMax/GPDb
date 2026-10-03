package com.gpdb.android.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ============================================================
//  StudioEntity — 映射 `studios` 表 (包含 181 家厂牌历史档案与双语信息)
// ============================================================
@Entity(
    tableName = "studios",
    indices = [
        Index(name = "idx_studios_name", value = ["name"])
    ]
)
data class StudioEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "name_zh")
    val nameZh: String? = null,

    @ColumnInfo(name = "description_zh")
    val descriptionZh: String? = null,

    @ColumnInfo(name = "logo_url")
    val logoUrl: String? = null,

    @ColumnInfo(name = "banner_url")
    val bannerUrl: String? = null,

    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP")
    val updatedAt: String? = null
)
