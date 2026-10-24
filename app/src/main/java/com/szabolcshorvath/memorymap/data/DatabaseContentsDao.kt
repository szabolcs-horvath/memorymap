package com.szabolcshorvath.memorymap.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Whole-table access, used to copy a restored backup into the live database ([DatabaseContents]). */
@Dao
interface DatabaseContentsDao {
    @Query("SELECT * FROM memory_groups")
    suspend fun getAllGroups(): List<MemoryGroup>

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaItems(): List<MediaItem>

    @Query("SELECT * FROM memory_fragments")
    suspend fun getAllFragments(): List<MemoryFragment>

    @Query("SELECT * FROM hsv_presets")
    suspend fun getAllPresets(): List<HSVPreset>

    /** Deletes every row of every table and inserts the given rows, all in one transaction. */
    @Transaction
    suspend fun replaceAll(groups: List<MemoryGroup>, mediaItems: List<MediaItem>, fragments: List<MemoryFragment>, presets: List<HSVPreset>) {
        deleteAllMediaItems()
        deleteAllFragments()
        deleteAllGroups()
        deleteAllPresets()

        insertGroups(groups)
        insertMediaItems(mediaItems)
        insertFragments(fragments)
        insertPresets(presets)
    }

    @Query("DELETE FROM memory_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM media_items")
    suspend fun deleteAllMediaItems()

    @Query("DELETE FROM memory_fragments")
    suspend fun deleteAllFragments()

    @Query("DELETE FROM hsv_presets")
    suspend fun deleteAllPresets()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroups(groups: List<MemoryGroup>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMediaItems(mediaItems: List<MediaItem>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFragments(fragments: List<MemoryFragment>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPresets(presets: List<HSVPreset>)
}
