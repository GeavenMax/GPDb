package com.gpdb.android.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_glossary")
data class CategoryGlossaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "term")
    val term: String,
    
    @ColumnInfo(name = "zh")
    val zh: String,
    
    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP")
    val updatedAt: String? = null
)
