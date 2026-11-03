package com.szabolcshorvath.memorymap.util

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.util.LocalMediaUtil.MediaMetadata

/**
 * Re-links a photo edited in the gallery after it was picked (a crop, for example). The edit rewrites the file under
 * the same MediaStore id and keeps its date taken, but the size and signature change, so the signature can't find it.
 * The id in the item's photo picker URI can: re-linking takes the MediaStore file of that id if it is of the item's
 * type and has the same date taken. `LocalMediaUtil.verifyAndFixMediaItems` applies the rule and `RelinkDiagnostics`
 * reports it, so keep the two in step.
 */
internal object EditedPhotoRule {
    private val LOCAL_PICKER_URI = Regex("""content://media/picker/\d+/com\.android\.providers\.media\.photopicker/media/(\d+)""")

    /**
     * The MediaStore id in [item]'s photo picker URI, if the rule applies: a local picker URI (cloud picker ids,
     * `com.google.android.apps.photos.cloudpicker`, aren't MediaStore ids), saved by this installation (on another
     * phone the id means nothing), with a known date taken to compare.
     */
    fun mediaStoreId(item: MediaItem, installationIdentifier: String): Long? {
        if (item.deviceId != installationIdentifier || item.dateTaken <= 0) return null
        return LOCAL_PICKER_URI.matchEntire(item.uri)?.groupValues?.get(1)?.toLongOrNull()
    }

    /** The URI of file [id] in the collection of [type], so only a file of the same type can match. */
    fun mediaStoreUri(type: MediaType, id: Long): Uri = ContentUris.withAppendedId(
        when (type) {
            MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        },
        id
    )

    /**
     * Whether a file is [item]'s photo after an in-place edit: the same date taken, to the millisecond. This also
     * guards against Android ever numbering picker ids differently from MediaStore, which isn't documented.
     */
    fun isSameCapture(item: MediaItem, actual: MediaMetadata): Boolean = item.dateTaken > 0 && actual.dateTaken == item.dateTaken
}
