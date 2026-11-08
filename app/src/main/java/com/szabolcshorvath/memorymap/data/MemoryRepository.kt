package com.szabolcshorvath.memorymap.data

import android.content.Context
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.MediaDetails.withDetailsOf
import com.szabolcshorvath.memorymap.util.PerfUtil.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Reads and writes memories with their media and fragments. Every write schedules an automatic backup, except a
 * delete: that one backs up when it becomes final ([finishDelete]) or is undone ([undoDelete]). Writes may be called
 * from the main thread.
 */
class MemoryRepository(private val context: Context, private val db: MemoryMapDatabase) {
    private val dao = db.memoryGroupDao()

    val allGroups: Flow<List<MemoryGroup>> = dao.getAllGroupsFlow()
    val allFragments: Flow<List<MemoryFragment>> = dao.getAllFragmentsFlow()

    fun groupWithMediaFlow(id: Int): Flow<MemoryGroupWithMedia?> = dao.getGroupWithMediaFlow(id)

    suspend fun getGroupWithMedia(id: Int): MemoryGroupWithMedia? = dao.getGroupWithMedia(id)

    suspend fun getMediaItem(id: Int): MediaItem? = db.mediaItemDao().getMediaItem(id)

    suspend fun getAllMediaItems(): List<MediaItem> = dao.getAllMediaItems()

    /** The stored thumbnails (JPEGs) of the given signatures, by signature. */
    suspend fun getThumbnails(signatures: Collection<String>): Map<String, ByteArray> {
        val known = signatures.filter { it.isNotEmpty() }.distinct()
        if (known.isEmpty()) return emptyMap()
        return db.mediaThumbnailDao().getThumbnails(known).associate { it.signature to it.jpeg }
    }

    /** What [save] did. [savedAsNew]: an edit whose memory was deleted meanwhile was saved as a new memory. */
    data class Saved(val groupId: Int, val savedAsNew: Boolean)

    /**
     * Saves [group] with its [media] and [fragments] (their group ids and orders are set here) and the [thumbnails] of
     * newly picked media, in one transaction. [editingId]: the memory being edited, whose media and fragments are
     * replaced; media kept through the edit keep the details stored about them. Then releases the grants of the media
     * the edit removed, if nothing else uses them. Reads no files: prepare the media before calling this.
     */
    suspend fun save(
        group: MemoryGroup,
        editingId: Int?,
        media: List<MediaItem>,
        thumbnails: List<MediaThumbnail>,
        fragments: List<MemoryFragment>
    ): Saved = withContext(Dispatchers.IO) {
        var savedAsNew = false
        var removedUris: Set<String> = emptySet()
        val savedId = db.withTransaction("save_memory_group") {
            // An edit of a memory deleted meanwhile (from its own screen) updates nothing:
            // it is saved as a new memory, so the user's changes aren't lost.
            val updatedId = editingId?.takeIf { dao.updateGroup(group.copy(id = it)) > 0 }
            savedAsNew = editingId != null && updatedId == null
            val groupId = updatedId ?: dao.insertGroup(group.copy(id = 0)).toInt()

            removedUris = saveMediaItems(groupId, media, replacing = updatedId != null)
            thumbnails.forEach { db.mediaThumbnailDao().insert(it) }
            dao.deleteFragmentsByGroupId(groupId)
            dao.insertFragments(fragments.mapIndexed { index, fragment -> fragment.copy(groupId = groupId, order = index + 1) })
            groupId
        }
        LocalMediaUtil.releaseUriGrantsIfUnused(context, dao, removedUris)
        AutomaticBackups.schedule(context)
        Saved(savedId, savedAsNew)
    }

    /**
     * Writes the [media] of group [groupId], replacing its old media if [replacing]. Returns the URIs the group no
     * longer uses. Database work only: runs inside the save transaction.
     */
    private suspend fun saveMediaItems(groupId: Int, media: List<MediaItem>, replacing: Boolean): Set<String> {
        val previous = if (replacing) dao.getGroupWithMedia(groupId)?.mediaItems.orEmpty().associateBy { it.uri } else emptyMap()
        if (replacing) dao.deleteMediaByGroupId(groupId)
        // Items kept through an edit keep the details they were saved with: the form doesn't carry them.
        val items = media.map { item -> previous[item.uri]?.let { item.withDetailsOf(it) } ?: item }.map { it.copy(groupId = groupId) }
        dao.insertMediaItems(items)
        return previous.keys - items.mapTo(HashSet()) { it.uri }
    }

    /** Stores [items] in this order (numbered from 1). */
    suspend fun saveMediaOrder(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        dao.updateMediaItems(items.mapIndexed { index, item -> item.copy(order = index + 1) })
        AutomaticBackups.schedule(context)
    }

    /** Stores [fragments] in this order (numbered from 1). */
    suspend fun saveFragmentsOrder(fragments: List<MemoryFragment>) = withContext(Dispatchers.IO) {
        dao.updateFragments(fragments.mapIndexed { index, fragment -> fragment.copy(order = index + 1) })
        AutomaticBackups.schedule(context)
    }

    /**
     * Deletes memory [id] with its media and fragments, and returns what was deleted (null if it was already gone),
     * for [undoDelete] or [finishDelete]. Keeps the media's grants: an undo needs them.
     */
    suspend fun delete(id: Int): MemoryGroupWithMedia? = withContext(Dispatchers.IO) {
        val deleted = dao.getGroupWithMedia(id) ?: return@withContext null
        dao.deleteGroup(deleted.group)
        deleted
    }

    /** Puts back a memory [delete] removed, with its media and fragments. */
    suspend fun undoDelete(deleted: MemoryGroupWithMedia) = withContext(Dispatchers.IO) {
        dao.restoreDeletedGroup(deleted)
        AutomaticBackups.schedule(context)
    }

    /**
     * The delete can no longer be undone: releases the grants of its media that nothing else uses, apart from
     * [keptUris] (media that are still somewhere else, such as in the Add form), and backs up.
     */
    suspend fun finishDelete(deleted: MemoryGroupWithMedia, keptUris: Set<String>) = withContext(Dispatchers.IO) {
        val released = deleted.mediaItems.map { it.uri }.filterNot { it in keptUris }
        LocalMediaUtil.releaseUriGrantsIfUnused(context, dao, released)
        AutomaticBackups.schedule(context)
    }

    /** Releases the grants of [uris] that no saved media item uses. */
    suspend fun releaseGrantsIfUnused(uris: Collection<String>) = withContext(Dispatchers.IO) {
        LocalMediaUtil.releaseUriGrantsIfUnused(context, dao, uris)
    }
}
