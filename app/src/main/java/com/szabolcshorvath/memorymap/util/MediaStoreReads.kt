package com.szabolcshorvath.memorymap.util

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.szabolcshorvath.memorymap.data.MediaType
import java.io.IOException

// Reading photos and videos through the content resolver, where a file may be gone, unreadable or of another app.

/**
 * What [read] returns, or null when the file can't be read: it is gone or was never there, the app may no longer read
 * it, or its provider doesn't know the URI.
 */
internal inline fun <T> readOrNull(read: () -> T?): T? = try {
    read()
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: IllegalStateException) {
    null
}

/** The value of [column] in the cursor's row, or null when the cursor has no such column or the value is null. */
internal fun Cursor.longOrNull(column: String): Long? = getColumnIndex(column).takeIf { it != -1 && !isNull(it) }?.let(::getLong)

/** As [longOrNull], for a whole number. */
internal fun Cursor.intOrNull(column: String): Int? = getColumnIndex(column).takeIf { it != -1 && !isNull(it) }?.let(::getInt)

/** As [longOrNull], for text. */
internal fun Cursor.stringOrNull(column: String): String? = getColumnIndex(column).takeIf { it != -1 && !isNull(it) }?.let(::getString)

/** The MediaStore collection of the photos or the videos on the phone. */
internal fun mediaStoreCollection(type: MediaType): Uri = when (type) {
    MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
}
