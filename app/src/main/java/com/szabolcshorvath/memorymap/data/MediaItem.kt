package com.szabolcshorvath.memorymap.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = MemoryGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("groupId"),
        Index("dateTaken")
    ]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val uri: String, // URI of the image or video
    val deviceId: String,
    val type: MediaType, // Enum to distinguish between Image and Video
    val mediaSignature: String, // Format: "{SizeBytes}_{PartialHash}"
    val fileSize: Long,
    val dateTaken: Long,
    val order: Int? = null,
    // What the file was, read when it was picked (or later, while it could still be read). Null when unknown, as for
    // items saved by older versions. Its thumbnail is in media_thumbnails, under the signature.
    val displayName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    /** A 64-bit dHash of the picture (`MediaDetails.perceptualHash`), to rank look-alikes. Never used to link on its own. */
    val perceptualHash: Long? = null
)

enum class MediaType {
    IMAGE, VIDEO
}
