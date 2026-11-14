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
) {
    /** This item, with the details it doesn't know taken from [other] (the same file saved before, or just read). */
    fun withDetailsOf(other: MediaItem): MediaItem = copy(
        displayName = displayName ?: other.displayName,
        width = width ?: other.width,
        height = height ?: other.height,
        mimeType = mimeType ?: other.mimeType,
        durationMs = durationMs ?: other.durationMs,
        perceptualHash = perceptualHash ?: other.perceptualHash
    )

    /**
     * Whether this row still holds the file [seen] had: the same URI and signature. A restore or an edit may since have
     * put another photo under the same id.
     */
    fun isStill(seen: MediaItem): Boolean = uri == seen.uri && mediaSignature == seen.mediaSignature

    companion object {
        /** The order a memory shows its media in: by `order`, and items without one after them, the newest first. */
        val DISPLAY_ORDER: Comparator<MediaItem> = orderedFirst({ it.order }, compareByDescending { it.dateTaken })
    }
}

enum class MediaType {
    IMAGE, VIDEO
}

/**
 * The order of the media or fragments of a memory: by [order], and the ones without one after them, sorted by
 * [unordered].
 */
internal fun <T> orderedFirst(order: (T) -> Int?, unordered: Comparator<T>): Comparator<T> = Comparator { a, b ->
    val first = order(a)
    val second = order(b)
    when {
        first != null && second != null -> first.compareTo(second)
        first != null -> -1
        second != null -> 1
        else -> unordered.compare(a, b)
    }
}
