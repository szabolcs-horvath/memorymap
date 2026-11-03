package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaThumbnail
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.util.MediaDetails.withDetails

/**
 * What the user can do with a media item that can't be re-linked: replace it with another file, or remove it from its
 * memory. Both release the old URI's grant if nothing else uses it, and schedule a backup.
 */
object MissingMedia {
    private const val TAG = "MissingMedia"
    private val METADATA_PROJECTION = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN)

    /** A file the user chose to use instead. */
    data class MediaFile(val uri: String, val type: MediaType, val size: Long, val dateTaken: Long, val signature: String)

    /** Reads [uri]'s type, size, date taken and signature, or null if it can't be read. Reads the file: not on the main thread. */
    fun describe(context: Context, uri: Uri): MediaFile? {
        val signature = LocalMediaUtil.signatureOrNull(context, uri) ?: return null
        val resolver = context.contentResolver
        val type = if (resolver.getType(uri)?.startsWith("video/") == true) MediaType.VIDEO else MediaType.IMAGE
        val (size, dateTaken) = try {
            resolver.query(uri, METADATA_PROJECTION, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val size = c.getColumnIndex(MediaStore.MediaColumns.SIZE).takeIf { it != -1 && !c.isNull(it) }?.let(c::getLong)
                val date = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN).takeIf { it != -1 && !c.isNull(it) }?.let(c::getLong)
                (size ?: 0L) to (date ?: 0L)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not read the details of $uri", e)
            null
        } ?: (0L to 0L)
        return MediaFile(uri.toString(), type, size.takeIf { it > 0 } ?: signature.substringBefore('_').toLongOrNull() ?: 0L, dateTaken, signature)
    }

    /**
     * Points [item] at [file] instead, keeping its place in the memory. Returns false if the item changed meanwhile
     * (an edit, a restore or a re-link), and then changes nothing.
     */
    suspend fun replace(context: Context, item: MediaItem, file: MediaFile): Boolean {
        val db = MemoryMapDatabase.getDatabase(context)
        val details = MediaDetails.read(context, file.uri.toUri(), file.type)
        val replacement = MediaItem(
            groupId = item.groupId,
            uri = file.uri,
            deviceId = InstallationIdentifier.getInstallationIdentifier(context),
            type = file.type,
            mediaSignature = file.signature,
            fileSize = file.size,
            dateTaken = file.dateTaken
        ).withDetails(details)
        if (!db.mediaItemDao().replaceMedia(item, replacement)) return false
        details.thumbnail?.let { db.mediaThumbnailDao().insert(MediaThumbnail(file.signature, it)) }
        Log.i(TAG, "Replaced media item ${item.id} (${item.uri}) with ${file.uri}")
        if (item.uri != file.uri) LocalMediaUtil.releaseUriGrantsIfUnused(context, db.memoryGroupDao(), listOf(item.uri))
        AutomaticBackups.schedule(context)
        return true
    }

    /** Removes [item] from its memory and closes the gap it leaves in the order. Returns false if it changed or is gone. */
    suspend fun remove(context: Context, item: MediaItem): Boolean {
        val dao = MemoryMapDatabase.getDatabase(context).memoryGroupDao()
        val media = dao.getGroupWithMedia(item.groupId)?.mediaItems.orEmpty()
        val current = media.find { it.id == item.id && it.uri == item.uri && it.mediaSignature == item.mediaSignature } ?: return false
        dao.deleteMediaAndRenumber(listOf(current), LocalMediaUtil.closeOrderGaps(media - current))
        Log.i(TAG, "Removed media item ${item.id} (${item.uri}) from memory ${item.groupId}")
        LocalMediaUtil.releaseUriGrantsIfUnused(context, dao, listOf(item.uri))
        AutomaticBackups.schedule(context)
        return true
    }
}
