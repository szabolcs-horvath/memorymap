package com.szabolcshorvath.memorymap.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/** Changes to single media items, made by the sheet that fixes a photo that can't be re-linked. */
@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaItem(id: Int): MediaItem?

    @Update
    suspend fun updateMediaItem(item: MediaItem)

    /**
     * Points item [seen] at another file: [replacement]'s URI, device, type, signature, size and date taken. It keeps
     * its id, memory and position. Only while the row still has the URI and signature [seen] has: a restore or an edit
     * may have put another photo under the same id. Returns whether the row was updated.
     */
    @Transaction
    suspend fun replaceMedia(seen: MediaItem, replacement: MediaItem): Boolean {
        val current = getMediaItem(seen.id)?.takeIf { it.uri == seen.uri && it.mediaSignature == seen.mediaSignature } ?: return false
        updateMediaItem(
            current.copy(
                uri = replacement.uri,
                deviceId = replacement.deviceId,
                type = replacement.type,
                mediaSignature = replacement.mediaSignature,
                fileSize = replacement.fileSize,
                dateTaken = replacement.dateTaken
            )
        )
        return true
    }
}
