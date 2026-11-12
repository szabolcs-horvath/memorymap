package com.szabolcshorvath.memorymap.ui.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The media viewer's state. Its arguments hold only the memory and the item it opened on, so they stay small however
 * many photos the memory has; the media themselves come from the database, in the order the memory screen shows them,
 * and follow its changes (a re-link, a reorder, a removal).
 */
class MediaViewerViewModel(
    memoryId: Int,
    private val openedItemId: Int,
    private val savedState: SavedStateHandle,
    memories: MemoryRepository,
) : ViewModel() {

    /** The memory's media, or null until they are loaded. An empty list: the memory or its media are gone. */
    val mediaItems: StateFlow<List<MediaItem>?> = memories
        .groupWithMediaFlow(memoryId)
        .map { group -> group?.mediaItems?.sortedWith(MediaItem.DISPLAY_ORDER).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), null)

    /** The item on screen, by id: kept through rotation and process death, and right when the list changes. */
    var currentItemId: Int
        get() = savedState.get<Int>(CURRENT_ITEM_ID) ?: openedItemId
        set(value) {
            savedState[CURRENT_ITEM_ID] = value
        }

    /** Where [currentItemId] is in [items], or null if it is gone. */
    fun currentPosition(items: List<MediaItem>): Int? = items.indexOfFirst { it.id == currentItemId }.takeIf { it >= 0 }

    companion object {
        private const val CURRENT_ITEM_ID = "current_item_id"
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L

        /** Creates the viewer's ViewModel for memory [memoryId]'s media, opened on item [itemId]. */
        fun factory(memoryId: Int, itemId: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MediaViewerViewModel(memoryId, itemId, createSavedStateHandle(), this[APPLICATION_KEY]!!.appContainer.memories)
            }
        }
    }
}
