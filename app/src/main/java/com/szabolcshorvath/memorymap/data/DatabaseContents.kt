package com.szabolcshorvath.memorymap.data

import android.util.Log

/**
 * Every row of every app table. A restore reads this from the (migrated) backup copy and writes it
 * into the live database in one transaction, so the live database is never closed or swapped, and a
 * failure leaves the user's current data untouched.
 *
 * When adding a table, add it here, to [DatabaseContentsDao] and to [TABLES], or restores will
 * silently drop its rows (`RestoreCompatibilityTest` checks [TABLES] against the latest schema).
 */
data class DatabaseContents(
    val groups: List<MemoryGroup>,
    val mediaItems: List<MediaItem>,
    val fragments: List<MemoryFragment>,
    val presets: List<HSVPreset>
) {
    /**
     * Replaces everything in the database behind [dao] with these contents, atomically. Rows whose
     * group doesn't exist are skipped (they would be invisible anyway, and would fail the foreign key).
     */
    suspend fun replaceAllIn(dao: DatabaseContentsDao) {
        val groupIds = groups.mapTo(HashSet()) { it.id }
        val validMedia = mediaItems.filter { it.groupId in groupIds }
        val validFragments = fragments.filter { it.groupId in groupIds }
        val orphans = (mediaItems.size - validMedia.size) + (fragments.size - validFragments.size)
        if (orphans > 0) Log.w(TAG, "Skipping $orphans media items or fragments without a memory")

        dao.replaceAll(groups, validMedia, validFragments, presets)
    }

    companion object {
        private const val TAG = "DatabaseContents"

        /** The tables these contents cover. */
        val TABLES = setOf("memory_groups", "media_items", "memory_fragments", "hsv_presets")

        suspend fun readFrom(dao: DatabaseContentsDao) = DatabaseContents(
            groups = dao.getAllGroups(),
            mediaItems = dao.getAllMediaItems(),
            fragments = dao.getAllFragments(),
            presets = dao.getAllPresets()
        )
    }
}
