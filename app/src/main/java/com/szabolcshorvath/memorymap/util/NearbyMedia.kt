package com.szabolcshorvath.memorymap.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import kotlin.math.abs

/** A photo or video on this phone, from MediaStore. Times are in milliseconds. */
data class NearbyFile(val uri: String, val size: Long, val dateTaken: Long)

/**
 * Finds the photos or videos taken around the time of a media item that can't be re-linked: usually the user took
 * several shots and deleted the one the memory used. Needs media access; without it, MediaStore returns nothing.
 */
internal object NearbyMedia {
    private const val TAG = "NearbyMedia"
    const val FIRST_WINDOW_MS = 5 * 60 * 1000L
    const val WIDER_WINDOW_MS = 60 * 60 * 1000L
    const val LIMIT = 20

    private val PROJECTION = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN)

    /**
     * Up to [LIMIT] files of [item]'s type taken within [windowMs] of its date taken, nearest first, without the ones
     * already in its memory ([memoryItems]). Empty when the date taken is unknown.
     */
    fun find(context: Context, item: MediaItem, memoryItems: List<MediaItem>, windowMs: Long): List<NearbyFile> {
        if (item.dateTaken <= 0) return emptyList()
        val files = query(context, collection(item.type), item.dateTaken - windowMs, item.dateTaken + windowMs)
        return pick(item, files, memoryItems, LIMIT) { LocalMediaUtil.signatureOrNull(context, it.toUri()) }
    }

    /**
     * The [limit] [files] nearest to [item]'s date taken, nearest first. Leaves out the files already in its memory, by
     * URI or by signature: the de-duplication after a re-link would remove such a copy again. [signatureOf] runs only
     * for the files that could make the list, nearest first.
     */
    fun pick(item: MediaItem, files: List<NearbyFile>, memoryItems: List<MediaItem>, limit: Int, signatureOf: (String) -> String?): List<NearbyFile> {
        val usedUris = memoryItems.mapTo(HashSet()) { it.uri }
        val usedSignatures = memoryItems.filter { it.id != item.id && it.mediaSignature.isNotEmpty() }.mapTo(HashSet()) { it.mediaSignature }
        return files.asSequence()
            .filter { it.uri !in usedUris }
            .sortedBy { abs(it.dateTaken - item.dateTaken) }
            .filter { file -> signatureOf(file.uri)?.let { it !in usedSignatures } ?: true }
            .take(limit)
            .toList()
    }

    /** How long [ms] is, roughly, without a sign: "7 s", "3 min", "1 h 5 min". */
    fun span(ms: Long): String {
        val seconds = abs(ms) / MILLIS_PER_SECOND
        val minutes = seconds / SECONDS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        return when {
            minutes == 0L -> "$seconds s"
            hours == 0L -> "$minutes min"
            minutes % MINUTES_PER_HOUR == 0L -> "$hours h"
            else -> "$hours h ${minutes % MINUTES_PER_HOUR} min"
        }
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L

    fun collection(type: MediaType): Uri = when (type) {
        MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    private fun query(context: Context, collection: Uri, from: Long, to: Long): List<NearbyFile> = try {
        val selection = "${MediaStore.MediaColumns.DATE_TAKEN} BETWEEN ? AND ?"
        context.contentResolver.query(collection, PROJECTION, selection, arrayOf(from.toString(), to.toString()), null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(NearbyFile(ContentUris.withAppendedId(collection, c.getLong(0)).toString(), c.getLong(1), c.getLong(2)))
                }
            }
        }.orEmpty()
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not search $collection", e)
        emptyList()
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Could not search $collection", e)
        emptyList()
    }
}
