package com.szabolcshorvath.memorymap.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HSVPresetDao {
    @Query("SELECT * FROM hsv_presets ORDER BY id ASC")
    suspend fun getAllPresets(): List<HSVPreset>

    @Query("SELECT * FROM hsv_presets ORDER BY id ASC")
    fun getAllPresetsFlow(): Flow<List<HSVPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<HSVPreset>)

    @Delete
    suspend fun deletePreset(preset: HSVPreset)

    @Query("SELECT COUNT(*) FROM hsv_presets")
    suspend fun getCount(): Int
}
