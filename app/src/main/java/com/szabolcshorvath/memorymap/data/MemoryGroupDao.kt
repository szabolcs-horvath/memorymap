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

    /** Returns the number of rows updated: 0 if the group no longer exists. */
    @Update
    suspend fun updateGroup(group: MemoryGroup): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItem>)

    @Update
    suspend fun updateMediaItems(mediaItems: List<MediaItem>)

    /**
     * Points a re-linked item at [uri] on device [deviceId], but only while the row still has the URI and
     * signature the re-link read ([seenUri], [signature]): a restore during the run can put another photo
     * under the same id. Returns the number of rows updated (0 or 1).
     *
     * [newSignature] and [newSize] are the item's own for a file found by its signature. A photo edited in place
     * after it was picked takes the edited file's, since the bytes it was saved with no longer exist on this phone.
     */
    @Query(
        "UPDATE media_items SET uri = :uri, deviceId = :deviceId, mediaSignature = :newSignature, fileSize = :newSize " +
            "WHERE id = :id AND uri = :seenUri AND mediaSignature = :signature"
    )
    @Suppress("LongParameterList")
    suspend fun updateMediaLink(
        id: Int,
        seenUri: String,
        signature: String,
        uri: String,
        deviceId: String,
        newSignature: String,
        newSize: Long
    ): Int

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

    @Query("UPDATE media_items SET `order` = :order WHERE id = :id")
    suspend fun updateMediaOrder(id: Int, order: Int)

    /**
     * Deletes [items] (duplicates, or a photo removed from its memory) and sets the new `order` of the remaining items
     * ([newOrders], id to order) in one transaction. Only the `order` column is written, so a concurrent re-link isn't
     * overwritten.
     */
    @Transaction
    suspend fun deleteMediaAndRenumber(items: List<MediaItem>, newOrders: Map<Int, Int>) {
        deleteMediaItems(items)
        newOrders.forEach { (id, order) -> updateMediaOrder(id, order) }
    }

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
