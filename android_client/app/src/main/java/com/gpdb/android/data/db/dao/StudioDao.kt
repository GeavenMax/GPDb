package com.gpdb.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gpdb.android.data.db.entities.StudioEntity

@Dao
interface StudioDao {
    @Query("SELECT * FROM studios WHERE name = :name LIMIT 1")
    suspend fun getStudioByName(name: String): StudioEntity?

    @Query("SELECT * FROM studios WHERE name = :name OR name_zh = :name LIMIT 1")
    suspend fun findStudio(name: String): StudioEntity?

    @Query("SELECT * FROM studios WHERE name LIKE '%' || :keyword || '%' OR name_zh LIKE '%' || :keyword || '%'")
    suspend fun searchStudios(keyword: String): List<StudioEntity>

    @Query("SELECT * FROM studios ORDER BY name ASC")
    suspend fun getAllStudios(): List<StudioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(studio: StudioEntity)
}
