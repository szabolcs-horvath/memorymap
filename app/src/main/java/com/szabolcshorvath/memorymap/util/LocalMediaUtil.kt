package com.szabolcshorvath.memorymap.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.StoryMapDatabase

object LocalMediaUtil {
    private const val TAG = "LocalMediaUtil"

    data class LocalMediaInfo(
        val uri: String,
        val mediaSignature: String,
        val size: Long
    )

    suspend fun hasMissingMedia(context: Context, mediaItems: List<MediaItem>): Boolean {
        val installationIdentifier = InstallationIdentifier.getInstallationIdentifier(context)
        return mediaItems.any { item ->
            item.deviceId != installationIdentifier || !isMediaAvailable(context, item.uri)
        }
    }

    fun isMediaAvailable(context: Context, uriString: String): Boolean {
        return try {
            context.contentResolver.openInputStream(uriString.toUri())?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isSignatureValid(context: Context, item: MediaItem): Boolean {
        val uri = item.uri.toUri()
        return try {
            val signatureOfMediaOfUri = MediaHasher.calculateMediaSignature(context, uri)
            signatureOfMediaOfUri == item.mediaSignature
        } catch (_: Exception) {
            false
        }
    }

    fun getLocalMediaForItems(context: Context, mediaItems: List<MediaItem>): List<LocalMediaInfo> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
        val uniqueSizes = mediaItems.map { it.fileSize }.distinct()
        if (uniqueSizes.isEmpty()) return emptyList()
        val selection = "${MediaStore.MediaColumns.SIZE} IN (${uniqueSizes.joinToString(", ")})"

        val mediaList = mutableListOf<LocalMediaInfo>()
        mediaList.addAll(
            queryMediaStore(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection
            )
        )
        mediaList.addAll(
            queryMediaStore(
                context,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection
            )
        )
        return mediaList
    }

    fun queryMediaStore(
        context: Context,
        contentUri: Uri,
        projection: Array<String>,
        selection: String?
    ): List<LocalMediaInfo> {
        val mediaList = mutableListOf<LocalMediaInfo>()
        try {
            context.contentResolver.query(contentUri, projection, selection, null, null)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val size = cursor.getLong(sizeCol)
                        val uri = ContentUris.withAppendedId(contentUri, id)

                        mediaList.add(
                            LocalMediaInfo(
                                uri.toString(),
                                MediaHasher.calculateMediaSignature(context, uri),
                                size
                            )
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying media store: ${e.message}")
        }
        return mediaList
    }

    suspend fun verifyAndFixMediaItems(context: Context) {
        val installationIdentifier = InstallationIdentifier.getInstallationIdentifier(context)
        val dao = StoryMapDatabase.getDatabase(context).memoryGroupDao()
        val mediaItems = dao.getAllMediaItems()
        val localMediaList = getLocalMediaForItems(context, mediaItems)
        val itemsToUpdate = mutableListOf<MediaItem>()

        for (item in mediaItems) {
            if (item.deviceId != installationIdentifier
                || item.uri.contains("photopicker")
                || !isSignatureValid(context, item)
            ) {
                val candidate = localMediaList.find { it.mediaSignature == item.mediaSignature }
                if (candidate != null) {
                    itemsToUpdate.add(
                        item.copy(
                            deviceId = installationIdentifier,
                            uri = candidate.uri
                        )
                    )
                } else {
                    Log.w(TAG, "No local media found with signature ${item.mediaSignature}")
                }
            }
        }

        if (itemsToUpdate.isNotEmpty()) {
            dao.updateMediaItems(itemsToUpdate)
        }
    }
}