package com.szabolcshorvath.memorymap.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.perf.metrics.AddTrace
import com.google.firebase.perf.trace
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroupDao
import java.io.IOException

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

    suspend fun hasMissingMedia(context: Context, mediaItems: List<MediaItem>): Boolean {
        trace("local_media_util_has_missing_media") {
            val installationIdentifier = InstallationIdentifier.getInstallationIdentifier(context)
            return mediaItems.any { needsRelink(context, it, installationIdentifier) }
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
    @AddTrace(name = "local_media_util_is_media_valid", enabled = true)
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
     */
    suspend fun verifyAndFixMediaItems(
        context: Context,
        dao: MemoryGroupDao,
        onProgress: ((String) -> Unit)? = null
    ) {
        trace("local_media_util_verify_and_fix_media_items") {
            val installationIdentifier = InstallationIdentifier.getInstallationIdentifier(context)
            val mediaItems = dao.getAllMediaItems()
            val itemsNeedingFix = mediaItems.filter { needsRelink(context, it, installationIdentifier) }

            when {
                itemsNeedingFix.isEmpty() -> {
                    Log.d(TAG, "All ${mediaItems.size} media items are valid on this device.")
                    onProgress?.invoke("All media items are valid")
                }

                !PermissionUtil.hasAnyMediaPermission(context) -> {
                    Log.w(TAG, "Storage permission not granted. Skipping MediaStore candidate re-linking.")
                    onProgress?.invoke("Permission required to re-link media")
                }

                else -> {
                    relink(context, dao, itemsNeedingFix, mediaItems.size, installationIdentifier, onProgress)
                }
            }
        }
    }

    private suspend fun relink(
        context: Context,
        dao: MemoryGroupDao,
        itemsNeedingFix: List<MediaItem>,
        totalMediaCount: Int,
        installationIdentifier: String,
        onProgress: ((String) -> Unit)?
    ) {
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

        for ((index, item) in itemsNeedingFix.withIndex()) {
            onProgress?.invoke("Re-linking photo ${index + 1} of ${itemsNeedingFix.size}...")

            val match = findBySignature(item, candidatePool, sigs)
            if (match != null) {
                Log.i(
                    TAG,
                    "RE-LINK SUCCESS [Exact Signature]: item id=${item.id} " +
                        "(size=${item.fileSize}, sig=${item.mediaSignature}) -> matched MediaStore uri=${match.uri}"
                )
                // Update only the link columns so a concurrent edit (e.g. reordering) is not overwritten.
                dao.updateMediaLink(item.id, match.uri, installationIdentifier)
                relinkedCount++
            } else {
                Log.w(
                    TAG,
                    "RE-LINK SKIPPED [No Match]: item id=${item.id}, origUri=${item.uri}, " +
                        "deviceId=${item.deviceId}, fileSize=${item.fileSize}, dateTaken=${item.dateTaken}, " +
                        "targetSig=${item.mediaSignature}. No candidate matched exact criteria."
                )
            }
        }

        if (relinkedCount > 0) {
            Log.i(TAG, "RE-LINK COMPLETE: Successfully updated $relinkedCount media items in Room DB.")
            onProgress?.invoke("Media re-linking complete. Re-linked $relinkedCount items.")
        } else {
            onProgress?.invoke("No matching photos found to re-link.")
        }

        deduplicateMediaItems(dao)
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

    private fun getReadableContentUris(context: Context): List<Uri> {
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
        getReadableContentUris(context).flatMap { contentUri ->
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

    suspend fun deduplicateMediaItems(dao: MemoryGroupDao) {
        trace("local_media_util_deduplicate_media_items") {
            val allMedia = dao.getAllMediaItems()

            val duplicates = allMedia
                .filter { it.mediaSignature.isNotEmpty() }
                .groupBy { it.groupId to it.mediaSignature }
                .filter { it.value.size > 1 }

            val itemsToDelete = mutableListOf<MediaItem>()
            for ((_, items) in duplicates) {
                val sorted = items.sortedBy { it.id }
                itemsToDelete.addAll(sorted.drop(1))
            }

            if (itemsToDelete.isNotEmpty()) {
                Log.d(TAG, "Deleting ${itemsToDelete.size} duplicate media items")
                dao.deleteMediaItems(itemsToDelete)
            }
        }
    }
}
