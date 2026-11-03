package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.perf.trace
import com.szabolcshorvath.memorymap.MemoryMapApplication
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaThumbnail
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.util.MediaDetails.withDetails
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fills in the details ([MediaDetails]) of media items saved without them, by older versions or while their file
 * couldn't be read, as long as their file can be read now. Runs at start-up, after the re-link, in the background.
 * Also deletes the thumbnails no item uses any more: only at start-up, since an Undo of a delete still needs them.
 */
object MediaDetailsBackfill {
    private const val TAG = "MediaDetailsBackfill"
    private val running = AtomicBoolean(false)

    /** Starts a run in [MemoryMapApplication.applicationScope], unless one is going. */
    fun start(context: Context) {
        val app = context.applicationContext as MemoryMapApplication
        if (!running.compareAndSet(false, true)) return
        app.applicationScope.launch {
            try {
                run(app)
            } finally {
                running.set(false)
            }
        }
    }

    /** Fills in what it can, one file at a time. Returns how many items it filled in. */
    internal suspend fun run(context: Context): Int = trace("media_details_backfill_run") {
        val db = MemoryMapDatabase.getDatabase(context)
        val thumbnails = db.mediaThumbnailDao()
        val pruned = thumbnails.deleteUnused()
        val withThumbnail = thumbnails.getSignatures().toHashSet()
        // The re-link cache answers whether a file can be read, without opening it.
        val toFill = db.memoryGroupDao().getAllMediaItems().filter { item ->
            MediaDetails.isMissing(item, item.mediaSignature in withThumbnail) && !LocalMediaUtil.hasMissingMedia(context, listOf(item))
        }
        val filled = toFill.count { item -> fillIn(context, db, item, withThumbnail) }
        Log.i(TAG, "Filled in the details of $filled of ${toFill.size} media items; deleted $pruned unused thumbnails.")
        filled
    }

    /** Reads [item]'s details and stores what it lacks. [withThumbnail] holds the signatures that have a thumbnail. */
    private suspend fun fillIn(context: Context, db: MemoryMapDatabase, item: MediaItem, withThumbnail: MutableSet<String>): Boolean {
        val details = MediaDetails.read(context, item.uri.toUri(), item.type)
        if (!db.mediaItemDao().fillDetails(item, item.withDetails(details))) return false
        val thumbnail = details.thumbnail
        if (thumbnail != null && item.mediaSignature.isNotEmpty() && withThumbnail.add(item.mediaSignature)) {
            db.mediaThumbnailDao().insert(MediaThumbnail(item.mediaSignature, thumbnail))
        }
        return true
    }
}
