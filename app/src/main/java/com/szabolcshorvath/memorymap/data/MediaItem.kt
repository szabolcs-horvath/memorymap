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
    val dateTaken: Long
)

enum class MediaType {
    IMAGE, VIDEO
}