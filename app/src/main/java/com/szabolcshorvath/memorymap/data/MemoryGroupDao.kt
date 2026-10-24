package com.szabolcshorvath.memorymap.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("UPDATE media_items SET uri = :uri, deviceId = :deviceId WHERE id = :id")
    suspend fun updateMediaLink(id: Int, uri: String, deviceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFragments(fragments: List<MemoryFragment>)

    @Update
    suspend fun updateFragments(fragments: List<MemoryFragment>)

    @Delete
    suspend fun deleteGroup(group: MemoryGroup)

    /**
     * Undoes [deleteGroup]: the delete cascades to media and fragments, so all three are
     * re-inserted. The group keeps its id; media and fragments get new ids, as on every edit.
     */
    @Transaction
    suspend fun restoreDeletedGroup(deleted: MemoryGroupWithMedia) {
        val groupId = insertGroup(deleted.group).toInt()
        insertMediaItems(deleted.mediaItems.map { it.copy(id = 0, groupId = groupId) })
        insertFragments(deleted.fragments.map { it.copy(id = 0, groupId = groupId) })
    }

    @Delete
    suspend fun deleteMediaItems(items: List<MediaItem>)

    @Query("DELETE FROM media_items WHERE groupId = :groupId")
    suspend fun deleteMediaByGroupId(groupId: Int)

    @Query("DELETE FROM memory_fragments WHERE groupId = :groupId")
    suspend fun deleteFragmentsByGroupId(groupId: Int)

    @Query("SELECT * FROM memory_groups ORDER BY startDate DESC")
    fun getAllGroupsFlow(): Flow<List<MemoryGroup>>

    @Transaction
    @Query("SELECT * FROM memory_groups WHERE id = :id")
    fun getGroupWithMediaFlow(id: Int): Flow<MemoryGroupWithMedia?>

    @Transaction
    @Query("SELECT * FROM memory_groups WHERE id = :id")
    suspend fun getGroupWithMedia(id: Int): MemoryGroupWithMedia?

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaItems(): List<MediaItem>

    @Query("SELECT * FROM memory_fragments")
    fun getAllFragmentsFlow(): Flow<List<MemoryFragment>>
}
