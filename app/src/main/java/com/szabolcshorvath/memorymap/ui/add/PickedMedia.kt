package com.szabolcshorvath.memorymap.ui.add

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Photos and videos just picked in the photo picker, read for the Add form. */
internal object PickedMedia {
    private const val TAG = "PickedMedia"

    /**
     * Takes the persisted grant of each of [uris] and reads its type, size and date taken. [deviceId] is this
     * installation's id, if known already. Does content resolver calls, off the main thread.
     */
    suspend fun read(context: Context, uris: List<Uri>, deviceId: String?): List<SelectedMedia> = withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not persist access for $uri", e)
            }
        }
        val installation = deviceId ?: InstallationIdentifier.getInstallationIdentifier(context)
        uris.map { uri -> describe(context, uri, installation) }
    }

    private fun describe(context: Context, uri: Uri, deviceId: String): SelectedMedia {
        val type = context.contentResolver.getType(uri)
        val mediaType = if (type != null && type.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
        val metadata = LocalMediaUtil.queryMetadata(context, uri)
        if (metadata == null) Log.w(TAG, "Could not read the size and date of picked media $uri")
        return SelectedMedia(uri, mediaType, deviceId, metadata?.size ?: 0L, metadata?.dateTaken ?: 0L)
    }
}
