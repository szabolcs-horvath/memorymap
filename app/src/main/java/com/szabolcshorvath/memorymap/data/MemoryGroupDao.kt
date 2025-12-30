package com.szabolcshorvath.memorymap.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface MemoryGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: MemoryGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItem>)

    @Transaction
    @Query("SELECT * FROM memory_groups")
    suspend fun getAllGroupsWithMedia(): List<MemoryGroupWithMedia>
    
    @Query("SELECT * FROM memory_groups")
    suspend fun getAllGroups(): List<MemoryGroup>

    @Transaction
    @Query("SELECT * FROM memory_groups WHERE id = :id")
    suspend fun getGroupWithMedia(id: Int): MemoryGroupWithMedia?
}