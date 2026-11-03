package com.szabolcshorvath.memorymap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A small JPEG of a photo or video (about 128 px on its longest side), so a memory can show what a photo looked like
 * once its file is gone. Kept by the media item's signature rather than its id: editing a memory deletes and re-inserts
 * its media rows, and the same photo in two memories shares one thumbnail. A separate table, so that lists of media
 * items don't load every thumbnail with them.
 */
@Entity(tableName = "media_thumbnails")
data class MediaThumbnail(
    @PrimaryKey val signature: String,
    val jpeg: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        this === other || other is MediaThumbnail && signature == other.signature && jpeg.contentEquals(other.jpeg)

    override fun hashCode(): Int = 31 * signature.hashCode() + jpeg.contentHashCode()
}
