package com.gpdb.android.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ============================================================
//  MovieEntity — 映射 `movies` 表 (精确匹配 SQLite Nullability 与 DefaultValue)
// ============================================================
@Entity(
    tableName = "movies",
    indices = [
        Index(name = "idx_movies_year", value = ["release_year"]),
        Index(name = "idx_movies_studio", value = ["studio_name"]),
        Index(name = "idx_movies_category", value = ["category"]),
        Index(name = "idx_movies_director", value = ["director_name"]),
    ]
)
data class MovieEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long? = null,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "studio_id")
    val studioId: Long? = null,

    @ColumnInfo(name = "studio_name")
    val studioName: String? = null,

    @ColumnInfo(name = "release_year")
    val releaseYear: Int? = null,

    @ColumnInfo(name = "duration_mins")
    val durationMins: Int? = null,

    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "rating")
    val rating: String? = null,

    @ColumnInfo(name = "movie_type")
    val movieType: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "cover_icon")
    val coverIcon: String? = null,

    @ColumnInfo(name = "cover_full")
    val coverFull: String? = null,

    @ColumnInfo(name = "cover_back")
    val coverBack: String? = null,

    @ColumnInfo(name = "covers_json")
    val coversJson: String? = null,

    @ColumnInfo(name = "director_id")
    val directorId: Long? = null,

    @ColumnInfo(name = "director_name")
    val directorName: String? = null,

    @ColumnInfo(name = "description_zh")
    val descriptionZh: String? = null,

    @ColumnInfo(name = "translation_attempts", defaultValue = "0")
    val translationAttempts: Int? = null,

    @ColumnInfo(name = "title_zh")
    val titleZh: String? = null,

    @ColumnInfo(name = "title_attempts", defaultValue = "0")
    val titleAttempts: Int? = null,

    @ColumnInfo(name = "scraped_at", defaultValue = "CURRENT_TIMESTAMP")
    val scrapedAt: String? = null,
)

/**
 * 将数据库中存储的 `images/Covers/xxx.jpg` 格式 URL
 * 转换为本地 image_cache 相对路径，供 [ZipImageFetcher] 使用。
 */
fun String?.toImageCachePath(): String? {
    if (this.isNullOrBlank()) return null
    val regex = Regex(
        """images/(Covers|Episodes|Stars|icons|logo)/([^?#]+)""",
        RegexOption.IGNORE_CASE
    )
    val match = regex.find(this) ?: return null
    val folder   = match.groupValues[1].replaceFirstChar { it.uppercase() }
    val filename = match.groupValues[2]
    return "image_cache/$folder/$filename"
}
