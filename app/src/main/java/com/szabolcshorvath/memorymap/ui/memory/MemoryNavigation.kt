package com.szabolcshorvath.memorymap.ui.memory

import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia

/** Where the memory page leads: the other screens it opens, and what follows a delete. */
interface MemoryNavigation {
    fun back()
    fun edit(memoryId: Int)
    fun showOnMap(lat: Double, lng: Double, memoryId: Int)
    fun showOnTimeline(memoryId: Int)

    /** Opens the media viewer at [item]. */
    fun openViewer(item: MediaItem)

    /** Opens the sheet that fixes [item], which needs re-linking. */
    fun openMissing(item: MediaItem)

    /** [deleted] was deleted (the page then goes back): offers the undo. */
    fun onDeleted(deleted: MemoryGroupWithMedia)
}
