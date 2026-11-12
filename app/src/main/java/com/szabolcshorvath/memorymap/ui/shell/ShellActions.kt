package com.szabolcshorvath.memorymap.ui.shell

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.perf.metrics.Trace
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.ui.add.FormPlace
import com.szabolcshorvath.memorymap.ui.memory.MemoryNavigation
import com.szabolcshorvath.memorymap.util.PerfUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the screens ask the shell for: switching tabs, opening the screens over them, and the requests one screen makes
 * of another tab (show a memory on the map, edit it), which the tab's ViewModel keeps until the tab handles them.
 */
@Stable
class ShellActions internal constructor(
    private val context: Context,
    private val shell: ShellViewModel,
    private val tabs: TabViewModels,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val hideKeyboard: () -> Unit,
) {
    private var tabTrace: Trace? = null

    /** Bumped by every tab switch; the shell stops [tabTrace] once the tab has drawn ([onTabDrawn]). */
    var tabSwitches by mutableIntStateOf(0)
        private set

    /** Shows [tab] (the tab bar's tap), closing the screens over the tabs. */
    fun select(tab: Tab) {
        tabTrace = PerfUtil.startTrace("main_activity_navigate_to_tab_${tab.traceName}")
        tabSwitches++
        if (tab != Tab.ADD) tabs.addIfCreated?.closeUnchangedEdit()
        hideKeyboard()
        shell.select(tab)
    }

    /**
     * The tab bar's tap on the selected tab: back to the tab itself (closing a memory opened over it), and, when it is
     * shown already, back to its start, as navigation bars do: the Timeline to its top, the Map to its home view.
     */
    fun reselect(tab: Tab) {
        val detailOpen = shell.hasDetail
        select(tab)
        if (detailOpen) return
        when (tab) {
            Tab.TIMELINE -> tabs.timeline.requestScrollToTop()
            Tab.MAP -> tabs.map.onTabTappedAgain()
            Tab.ADD, Tab.SETTINGS -> Unit
        }
    }

    /** The tab shown after the latest switch has drawn. */
    fun onTabDrawn() {
        tabTrace?.stop()
        tabTrace = null
    }

    fun openMemory(memoryId: Int) {
        hideKeyboard()
        shell.open(Detail.Memory(memoryId))
    }

    fun showOnTimeline(memoryId: Int) {
        select(Tab.TIMELINE)
        tabs.timeline.requestScrollTo(memoryId)
    }

    fun showOnMap(lat: Double, lng: Double, memoryId: Int) {
        select(Tab.MAP)
        tabs.map.requestFocusOnMemory(lat, lng, memoryId)
    }

    fun edit(memoryId: Int) {
        select(Tab.ADD)
        tabs.add.requestEdit(memoryId)
    }

    /** Opens the location picker for the Add form, on [place] if it has one. */
    fun pickLocation(place: FormPlace?) {
        hideKeyboard()
        shell.open(Detail.PickLocation(place))
    }

    /** The picker [from] confirmed a place: it closes, and the form takes the place. */
    fun locationPicked(from: Detail.PickLocation, lat: Double, lng: Double, placeName: String?, address: String?) {
        shell.close(from)
        tabs.add.setPlace(lat, lng, placeName, address)
    }

    /**
     * Memory [deleted] was deleted: offers Undo. Only once the message is gone without Undo is the delete final: then
     * the media grants are released (a released grant can't be taken back) and a backup runs. If the message is cut
     * short (another delete, the activity recreated), the delete is final too.
     */
    fun memoryDeleted(deleted: MemoryGroupWithMedia) {
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            var undone = false
            try {
                val result = snackbar.showSnackbar(
                    context.getString(R.string.memory_deleted),
                    actionLabel = context.getString(R.string.undo),
                    duration = SnackbarDuration.Long,
                )
                undone = result == SnackbarResult.ActionPerformed
                if (undone) tabs.common.undoDelete(deleted)
            } finally {
                // An edit of the memory still open on the Add tab saves it again as a new memory, so its media keep
                // their grants.
                if (!undone) tabs.common.finishDelete(deleted, tabs.addIfCreated?.mediaUrisInForm().orEmpty())
            }
        }
    }

    /** Where memory [detail]'s pages lead. */
    fun memoryNavigation(detail: Detail.Memory) = object : MemoryNavigation {
        override fun back() = shell.close(detail)
        override fun edit(memoryId: Int) = this@ShellActions.edit(memoryId)
        override fun showOnMap(lat: Double, lng: Double, memoryId: Int) = this@ShellActions.showOnMap(lat, lng, memoryId)
        override fun showOnTimeline(memoryId: Int) = this@ShellActions.showOnTimeline(memoryId)
        override fun openViewer(item: MediaItem) = openOverMemory(detail, Detail.MediaViewer(item.groupId, item.id))
        override fun openMissing(item: MediaItem) = openOverMemory(detail, Detail.MissingMedia(item.id))
        override fun onDeleted(deleted: MemoryGroupWithMedia) = memoryDeleted(deleted)
    }

    /** Opens [next] over memory [memory], unless something is open over it already (a second, quick tap). */
    private fun openOverMemory(memory: Detail.Memory, next: Detail) {
        if (shell.details.last() == memory) shell.open(next)
    }
}
