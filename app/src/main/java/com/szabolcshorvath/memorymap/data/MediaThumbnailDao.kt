package com.szabolcshorvath.memorymap.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaThumbnailDao {
    /** Stores [thumbnail], keeping one already stored for the same signature (the same bytes, so the same picture). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(thumbnail: MediaThumbnail)

    @Query("SELECT * FROM media_thumbnails WHERE signature IN (:signatures)")
    suspend fun getThumbnails(signatures: List<String>): List<MediaThumbnail>

    @Query("SELECT signature FROM media_thumbnails")
    suspend fun getSignatures(): List<String>

    /**
     * Deletes the thumbnails no media item uses any more. Run it only at start-up: the Undo of a delete re-inserts the
     * memory's media, which must still find their thumbnails. Returns how many were deleted.
     */
    @Query("DELETE FROM media_thumbnails WHERE signature NOT IN (SELECT mediaSignature FROM media_items)")
    suspend fun deleteUnused(): Int
}
