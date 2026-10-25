package com.szabolcshorvath.memorymap.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.perf.trace
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroupDao
import com.szabolcshorvath.memorymap.util.LocalMediaUtil.needsRelink
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object LocalMediaUtil {
    const val TAG = "LocalMediaUtil"
    private const val SIZE_QUERY_CHUNK = 100
    private val CANDIDATE_PROJECTION = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
    private val METADATA_PROJECTION = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN)

    data class LocalMediaCandidate(
        val uri: String,
        val size: Long
    )

    /** Size and date reported by the provider for a URI the app can currently read. */
    data class MediaMetadata(
        val size: Long,
        val dateTaken: Long
    )

    /** What a re-link run left: how many items still point to no file on this device, and a message for the UI. */
    data class RelinkResult(
        val missingCount: Int,
        val summary: String
    )

    /** Everything [needsRelink] looks at. A change to any of these gives a new key, so a new check. */
    private data class LinkKey(
        val id: Int,
        val uri: String,
        val deviceId: String,
        val fileSize: Long,
        val dateTaken: Long
    )

    private val MediaItem.linkKey get() = LinkKey(id, uri, deviceId, fileSize, dateTaken)

    /**
     * Results of [needsRelink] for this process. Each check is a ContentResolver call, and the detail page
     * asks on every emission. [verifyAndFixMediaItems] (cold start, restore, permission grants, the Re-link
     * buttons) checks every item again and replaces the cache. A photo deleted from the gallery while the
     * app runs is noticed at the next of those.
     */
    private val relinkNeeded = ConcurrentHashMap<LinkKey, Boolean>()

    /** Whether any of [mediaItems] needs re-linking. Uses the cached results, checking only new items. */
    suspend fun hasMissingMedia(context: Context, mediaItems: List<MediaItem>): Boolean {
        trace("local_media_util_has_missing_media") {
            val installationIdentifier = InstallationIdentifier.getInstallationIdentifier(context)
            return mediaItems.any { item ->
                relinkNeeded.getOrPut(item.linkKey) { needsRelink(context, item, installationIdentifier) }
            }
        }
    }

    private fun needsRelink(context: Context, item: MediaItem, installationIdentifier: String): Boolean =
        item.deviceId != installationIdentifier || !isMediaValid(context, item)

    /**
     * Checks that [MediaItem.uri] is readable and still points to the file it was saved with,
     * using only a metadata query (the file itself is not opened).
     *
     * MediaStore ids are per device, so a `content://media/external/...` URI carried over to another
     * phone can resolve to an unrelated photo. Comparing the stored size and date catches that.
     */
    fun isMediaValid(context: Context, item: MediaItem): Boolean {
        val metadata = queryMetadata(context, item.uri.toUri()) ?: return false
        return isSameFile(item.fileSize, item.dateTaken, metadata)
    }

    /**
     * A file counts as the same one when its size matches, or its capture date matches (an in-place
     * edit, e.g. Samsung Gallery "Save", changes the size but keeps the date). Unknown values (0) pass.
     */
    fun isSameFile(storedSize: Long, storedDateTaken: Long, actual: MediaMetadata): Boolean {
        return storedSize <= 0 || actual.size <= 0 || storedSize == actual.size || storedDateTaken > 0 && storedDateTaken == actual.dateTaken
    }

    private fun queryMetadata(context: Context, uri: Uri): MediaMetadata? = try {
        context.contentResolver.query(uri, METADATA_PROJECTION, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            MediaMetadata(
                size = if (sizeCol != -1 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L,
                dateTaken = if (dateCol != -1 && !c.isNull(dateCol)) c.getLong(dateCol) else 0L
            )
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    fun signatureOrNull(context: Context, uri: Uri): String? = try {
        MediaHasher.calculateMediaSignature(context, uri).takeIf { it.isNotEmpty() }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private class SignatureCache(private val compute: (Uri) -> String?) {
        private val cache = HashMap<String, String?>()
        fun of(uri: String): String? =
            if (cache.containsKey(uri)) cache[uri] else compute(uri.toUri()).also { cache[uri] = it }
    }

    /**
     * Re-links media items that came from another installation or whose URI no longer resolves to
     * their file. Readable photo picker URIs are left alone: their persisted grant survives reboots
     * and, unlike MediaStore URIs, does not depend on the broad media permission.
     *
     * Checks every item again (media access or the database may have changed) and refreshes the cache
     * [hasMissingMedia] uses.
     */
    suspend fun verifyAndFixMediaItems(
        context: Context,
        dao: MemoryGroupDao,
        onProgress: ((String) -> Unit)? = null
    ): RelinkResult =
        trace("local_media_util_verify_and_fix_media_items") {
            val installationIdentifier = InstallationIdentifier.getInstallationIdentifier(context)
            val mediaItems = dao.getAllMediaItems()
            relinkNeeded.clear()
            val itemsNeedingFix = mediaItems.filter { item ->
                needsRelink(context, item, installationIdentifier).also { relinkNeeded[item.linkKey] = it }
            }

            val result = when {
                itemsNeedingFix.isEmpty() -> {
                    Log.d(TAG, "All ${mediaItems.size} media items are valid on this device.")
                    RelinkResult(0, "All media items are valid")
                }

                !PermissionUtil.hasAnyMediaPermission(context) -> {
                    Log.w(TAG, "Storage permission not granted. Skipping MediaStore candidate re-linking.")
                    RelinkResult(itemsNeedingFix.size, "Permission required to re-link media")
                }

                else -> {
                    relink(context, dao, itemsNeedingFix, mediaItems.size, installationIdentifier, onProgress)
                }
            }
            onProgress?.invoke(result.summary)
            result
        }

    private suspend fun relink(
        context: Context,
        dao: MemoryGroupDao,
        itemsNeedingFix: List<MediaItem>,
        totalMediaCount: Int,
        installationIdentifier: String,
        onProgress: ((String) -> Unit)?
    ): RelinkResult {
        onProgress?.invoke("Scanning local media library...")
        Log.d(
            TAG,
            "RE-LINK START: installationId=$installationIdentifier, " +
                "itemsNeedingFix=${itemsNeedingFix.size}, totalItems=$totalMediaCount"
        )

        val targetSizes = itemsNeedingFix.map { it.fileSize }.filter { it > 0 }.distinct()
        val candidatePool = queryCandidatesForSizes(context, targetSizes)
        val sigs = SignatureCache { signatureOrNull(context, it) }
        var relinkedCount = 0
        val stillMissing = HashSet<Int>()

        for ((index, item) in itemsNeedingFix.withIndex()) {
            onProgress?.invoke("Re-linking photo ${index + 1} of ${itemsNeedingFix.size}...")

            val match = findBySignature(item, candidatePool, sigs)
            when {
                match == null -> {
                    stillMissing += item.id
                    Log.w(
                        TAG,
                        "RE-LINK SKIPPED [No Match]: item id=${item.id}, origUri=${item.uri}, " +
                            "deviceId=${item.deviceId}, fileSize=${item.fileSize}, dateTaken=${item.dateTaken}, " +
                            "targetSig=${item.mediaSignature}. No candidate matched exact criteria."
                    )
                }

                // Updates only the link columns, so a concurrent edit (e.g. reordering) is not overwritten, and only
                // if the row still holds the photo this run read: a restore during the run can reuse its id.
                dao.updateMediaLink(item.id, item.uri, item.mediaSignature, match.uri, installationIdentifier) == 1 -> {
                    Log.i(
                        TAG,
                        "RE-LINK SUCCESS [Exact Signature]: item id=${item.id} " +
                            "(size=${item.fileSize}, sig=${item.mediaSignature}) -> matched MediaStore uri=${match.uri}"
                    )
                    relinkNeeded[item.copy(uri = match.uri, deviceId = installationIdentifier).linkKey] = false
                    relinkedCount++
                }

                else -> Log.w(TAG, "RE-LINK SKIPPED [Changed]: item id=${item.id} changed during the run (restore or edit).")
            }
        }

        if (relinkedCount > 0) {
            Log.i(TAG, "RE-LINK COMPLETE: Successfully updated $relinkedCount media items in Room DB.")
        }
        val removedDuplicates = deduplicateMediaItems(context, dao)
        stillMissing -= removedDuplicates.mapTo(HashSet()) { it.id }

        val summary = if (relinkedCount > 0) "Media re-linking complete. Re-linked $relinkedCount items." else "No matching photos found to re-link."
        return RelinkResult(
            missingCount = stillMissing.size,
            summary = if (removedDuplicates.isNotEmpty()) "$summary Removed ${removedDuplicates.size} duplicate photos." else summary
        )
    }

    private fun findBySignature(
        item: MediaItem,
        pool: List<LocalMediaCandidate>,
        sigs: SignatureCache
    ): LocalMediaCandidate? {
        if (item.mediaSignature.isEmpty()) return null
        return pool.firstOrNull { it.size == item.fileSize && sigs.of(it.uri) == item.mediaSignature }
    }

    /**
     * Releases the persisted grants of [uris] (media the user just removed from a memory) unless a saved
     * media item still uses them. Android keeps at most 512 persisted grants per app and silently revokes
     * the oldest beyond that, which would break older photo picker items.
     *
     * Deliberately not a sweep of every unreferenced grant: after restoring an older backup, grants used
     * only by a newer backup must survive so that restoring the newer one keeps working.
     */
    suspend fun releaseUriGrantsIfUnused(context: Context, dao: MemoryGroupDao, uris: Collection<String>) {
        if (uris.isEmpty()) return
        val referenced = dao.getAllMediaItems().mapTo(HashSet()) { it.uri }
        val candidates = uris.filterNotTo(HashSet()) { it in referenced }
        val resolver = context.contentResolver
        var released = 0
        for (permission in resolver.persistedUriPermissions) {
            if (permission.uri.toString() !in candidates) continue
            try {
                resolver.releasePersistableUriPermission(permission.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                released++
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not release grant for ${permission.uri}", e)
            }
        }
        if (released > 0) Log.d(TAG, "Released $released unused persisted URI grants.")
    }

    /** The MediaStore collections the app may query with its current media access. */
    internal fun readableContentUris(context: Context): List<Uri> {
        val contentUris = mutableListOf<Uri>()
        if (PermissionUtil.hasImagePermission(context)) {
            contentUris.add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
        if (PermissionUtil.hasVideoPermission(context)) {
            contentUris.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        return contentUris
    }

    private fun queryCandidatesForSizes(context: Context, sizes: List<Long>): List<LocalMediaCandidate> =
        readableContentUris(context).flatMap { contentUri ->
            sizes.chunked(SIZE_QUERY_CHUNK).flatMap { queryChunk(context, contentUri, it) }
        }

    private fun queryChunk(context: Context, contentUri: Uri, sizes: List<Long>): List<LocalMediaCandidate> {
        val selection = "${MediaStore.MediaColumns.SIZE} IN (${sizes.joinToString(",")})"
        return try {
            context.contentResolver.query(contentUri, CANDIDATE_PROJECTION, selection, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            LocalMediaCandidate(
                                uri = ContentUris.withAppendedId(contentUri, c.getLong(0)).toString(),
                                size = c.getLong(1)
                            )
                        )
                    }
                }
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Candidate query failed for $contentUri", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Candidate query failed for $contentUri", e)
            emptyList()
        }
    }

    /**
     * After re-linking, two items of one memory can point to the same photo (same signature). Keeps the
     * oldest of each, deletes the rest, closes the gaps they leave in `order` and releases grants no other
     * item uses. Returns the removed items.
     */
    suspend fun deduplicateMediaItems(context: Context, dao: MemoryGroupDao): List<MediaItem> =
        trace("local_media_util_deduplicate_media_items") {
            val allMedia = dao.getAllMediaItems()
            val duplicates = findDuplicateMedia(allMedia)
            if (duplicates.isNotEmpty()) {
                val removedIds = duplicates.mapTo(HashSet()) { it.id }
                val affectedGroups = duplicates.mapTo(HashSet()) { it.groupId }
                val newOrders = allMedia
                    .filter { it.groupId in affectedGroups && it.id !in removedIds }
                    .groupBy { it.groupId }
                    .values
                    .fold(emptyMap<Int, Int>()) { acc, groupItems -> acc + closeOrderGaps(groupItems) }

                Log.i(TAG, "Removing ${duplicates.size} duplicate media items: ids=${duplicates.map { it.id }}")
                dao.deleteDuplicateMedia(duplicates, newOrders)
                releaseUriGrantsIfUnused(context, dao, duplicates.map { it.uri })
            }
            duplicates
        }

    /** Items of the same memory with the same signature, except the oldest (lowest id) of each set. */
    internal fun findDuplicateMedia(media: List<MediaItem>): List<MediaItem> = media
        .filter { it.mediaSignature.isNotEmpty() }
        .groupBy { it.groupId to it.mediaSignature }
        .values
        .flatMap { items -> items.sortedBy { it.id }.drop(1) }

    /**
     * New `order` values (id to order) that number one memory's remaining [items] 1..n in their current
     * order, for the items whose value changes. Leaves the memory alone if any item has no `order`, so
     * the displayed order (which then falls back to dates) can't change.
     */
    internal fun closeOrderGaps(items: List<MediaItem>): Map<Int, Int> {
        if (items.any { it.order == null }) return emptyMap()
        return items.sortedWith(compareBy<MediaItem>({ it.order }, { it.id }))
            .mapIndexedNotNull { index, item -> (item.id to index + 1).takeIf { item.order != index + 1 } }
            .toMap()
    }
}
