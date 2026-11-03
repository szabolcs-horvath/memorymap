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
     * Points item [seen] at another file: [replacement]'s URI, device, type, signature, size, date taken and details. It keeps
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
                dateTaken = replacement.dateTaken,
                displayName = replacement.displayName,
                width = replacement.width,
                height = replacement.height,
                mimeType = replacement.mimeType,
                durationMs = replacement.durationMs,
                perceptualHash = replacement.perceptualHash
            )
        )
        return true
    }

    /**
     * Fills in the details item [seen] doesn't know yet from [read] (its name, size in pixels, type, length, perceptual
     * hash), only while the row still has the URI and signature [seen] has. Returns whether the row was updated.
     */
    @Transaction
    suspend fun fillDetails(seen: MediaItem, read: MediaItem): Boolean {
        val current = getMediaItem(seen.id)?.takeIf { it.uri == seen.uri && it.mediaSignature == seen.mediaSignature } ?: return false
        updateMediaItem(
            current.copy(
                displayName = current.displayName ?: read.displayName,
                width = current.width ?: read.width,
                height = current.height ?: read.height,
                mimeType = current.mimeType ?: read.mimeType,
                durationMs = current.durationMs ?: read.durationMs,
                perceptualHash = current.perceptualHash ?: read.perceptualHash
            )
        )
        return true
    }
}
