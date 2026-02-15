package com.szabolcshorvath.memorymap.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface MemoryGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: MemoryGroup): Long

    @Update
    suspend fun updateGroup(group: MemoryGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItem>)

    @Update
    suspend fun updateMediaItems(mediaItems: List<MediaItem>)

    @Delete
    suspend fun deleteGroup(group: MemoryGroup)

    @Query("DELETE FROM media_items WHERE groupId = :groupId")
    suspend fun deleteMediaByGroupId(groupId: Int)

    @Transaction
    @Query("SELECT * FROM memory_groups ORDER BY startDate DESC")
    suspend fun getAllGroupsWithMedia(): List<MemoryGroupWithMedia>

    @Query("SELECT * FROM memory_groups ORDER BY startDate DESC")
    suspend fun getAllGroups(): List<MemoryGroup>

    @Transaction
    @Query("SELECT * FROM memory_groups WHERE id = :id")
    suspend fun getGroupWithMedia(id: Int): MemoryGroupWithMedia?

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaItems(): List<MediaItem>
}