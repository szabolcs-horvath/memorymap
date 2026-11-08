package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
import androidx.core.graphics.scale
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What a photo or video is, beyond its bytes: its name, size in pixels, type, length, a small thumbnail and a
 * perceptual hash. Read while the file can be read (when it is picked, or later by [MediaDetailsBackfill]), so the app
 * can show what a photo looked like once its file is gone, and rank look-alikes in the missing-photo sheet.
 */
object MediaDetails {
    private const val TAG = "MediaDetails"
    private const val LOAD_SIZE = 256
    private const val THUMBNAIL_SIZE = 128
    private const val JPEG_QUALITY = 75
    private const val HASH_WIDTH = 9
    private const val HASH_HEIGHT = 8

    /** Hashes at most this far apart ([hashDistance], out of 64 bits) look alike. */
    const val LOOKS_ALIKE_DISTANCE = 10

    /** What [read] found. Anything it couldn't read is null. */
    class Details(
        val info: Info,
        val perceptualHash: Long?,
        /** A JPEG, about 128 px on its longest side. */
        val thumbnail: ByteArray?
    )

    /** The file's name, size in pixels, type and length. */
    data class Info(val displayName: String?, val width: Int?, val height: Int?, val mimeType: String?, val durationMs: Long?)

    /** Whether [item] still lacks the details this reads (older items, or ones whose file couldn't be read). */
    fun isMissing(item: MediaItem, hasThumbnail: Boolean): Boolean = item.perceptualHash == null || item.displayName == null || !hasThumbnail

    /** This item with [details] filled in where they are known; what [details] doesn't know is kept. */
    fun MediaItem.withDetails(details: Details): MediaItem = copy(
        displayName = details.info.displayName ?: displayName,
        width = details.info.width ?: width,
        height = details.info.height ?: height,
        mimeType = details.info.mimeType ?: mimeType,
        durationMs = details.info.durationMs ?: durationMs,
        perceptualHash = details.perceptualHash ?: perceptualHash
    )

    /** This item, with the details it doesn't know taken from [other] (the same file saved before). */
    fun MediaItem.withDetailsOf(other: MediaItem): MediaItem = copy(
        displayName = displayName ?: other.displayName,
        width = width ?: other.width,
        height = height ?: other.height,
        mimeType = mimeType ?: other.mimeType,
        durationMs = durationMs ?: other.durationMs,
        perceptualHash = perceptualHash ?: other.perceptualHash
    )

    /** Reads [uri]'s details. What can't be read stays null. Reads the file: not on the main thread. */
    fun read(context: Context, uri: Uri, type: MediaType): Details {
        val columns = queryColumns(context, uri)
        val bitmap = loadBitmap(context, uri)
        return try {
            val bounds = if (type == MediaType.IMAGE && (columns.width == null || columns.height == null)) imageBounds(context, uri) else null
            Details(
                info = Info(
                    displayName = columns.displayName,
                    width = columns.width ?: bounds?.width,
                    height = columns.height ?: bounds?.height,
                    mimeType = context.contentResolver.getType(uri),
                    durationMs = if (type == MediaType.VIDEO) columns.durationMs ?: videoDuration(context, uri) else null
                ),
                perceptualHash = bitmap?.let(::perceptualHash),
                thumbnail = bitmap?.let(::encodeThumbnail)
            )
        } finally {
            bitmap?.recycle()
        }
    }

    /** The perceptual hash of the picture at [uri], from its thumbnail, or null if it can't be read. */
    fun hashOf(context: Context, uri: Uri): Long? {
        val bitmap = loadBitmap(context, uri) ?: return null
        return try {
            perceptualHash(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** A 64-bit difference hash of [bitmap]: its 9×8 greyscale copy, one bit per pair of neighbouring pixels in a row. */
    fun perceptualHash(bitmap: Bitmap): Long {
        val small = bitmap.scale(HASH_WIDTH, HASH_HEIGHT)
        val pixels = IntArray(HASH_WIDTH * HASH_HEIGHT)
        small.getPixels(pixels, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT)
        if (small !== bitmap) small.recycle()
        return differenceHash(pixels.map(::grey).toIntArray())
    }

    /** The difference hash of a 9×8 greyscale image ([grey], row by row): bit set where a pixel is brighter than its right neighbour. */
    internal fun differenceHash(grey: IntArray): Long {
        require(grey.size == HASH_WIDTH * HASH_HEIGHT) { "Expected ${HASH_WIDTH * HASH_HEIGHT} pixels, got ${grey.size}" }
        var hash = 0L
        var bit = 0
        for (row in 0 until HASH_HEIGHT) {
            for (column in 0 until HASH_WIDTH - 1) {
                val left = grey[row * HASH_WIDTH + column]
                val right = grey[row * HASH_WIDTH + column + 1]
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** How many of the 64 bits of two hashes differ: 0 for the same picture, about 32 for unrelated ones. */
    fun hashDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    @Suppress("MagicNumber") // The usual luma weights.
    private fun grey(color: Int): Int = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000

    private fun encodeThumbnail(bitmap: Bitmap): ByteArray {
        val scale = THUMBNAIL_SIZE.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            bitmap.scale((bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1))
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            if (scaled !== bitmap) scaled.recycle()
            stream.toByteArray()
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.loadThumbnail(uri, Size(LOAD_SIZE, LOAD_SIZE), null)
    } catch (e: IOException) {
        Log.w(TAG, "No thumbnail for $uri", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "No thumbnail for $uri", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "No thumbnail for $uri", e)
        null
    } catch (e: UnsupportedOperationException) {
        Log.w(TAG, "No thumbnail for $uri", e)
        null
    }

    private data class Columns(val displayName: String?, val width: Int?, val height: Int?, val durationMs: Long?)

    /** The name, size and length the provider knows. A provider that doesn't know a column may refuse the query: then the name alone. */
    private fun queryColumns(context: Context, uri: Uri): Columns {
        val full = arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.DURATION)
        return query(context, uri, full) ?: query(context, uri, arrayOf(OpenableColumns.DISPLAY_NAME)) ?: Columns(null, null, null, null)
    }

    private fun query(context: Context, uri: Uri, projection: Array<String>): Columns? = try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            fun index(column: String) = c.getColumnIndex(column).takeIf { it != -1 && !c.isNull(it) }
            Columns(
                displayName = index(OpenableColumns.DISPLAY_NAME)?.let(c::getString),
                width = index(MediaStore.MediaColumns.WIDTH)?.let(c::getInt)?.takeIf { it > 0 },
                height = index(MediaStore.MediaColumns.HEIGHT)?.let(c::getInt)?.takeIf { it > 0 },
                durationMs = index(MediaStore.MediaColumns.DURATION)?.let(c::getLong)?.takeIf { it > 0 }
            )
        }
    } catch (e: IllegalArgumentException) {
        Log.d(TAG, "Query of $uri refused: ${e.message}")
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "Query of $uri refused", e)
        null
    }

    private fun imageBounds(context: Context, uri: Uri): Size? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            Size(options.outWidth, options.outHeight).takeIf { it.width > 0 && it.height > 0 }
        }
    } catch (e: IOException) {
        Log.w(TAG, "Could not read the size of $uri", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not read the size of $uri", e)
        null
    }

    // MediaMetadataRetriever throws a plain RuntimeException when it can't open a file.
    @Suppress("TooGenericExceptionCaught")
    private fun videoDuration(context: Context, uri: Uri): Long? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Could not read the length of $uri", e)
        null
    } catch (e: RuntimeException) {
        Log.w(TAG, "Could not read the length of $uri", e)
        null
    }
}
