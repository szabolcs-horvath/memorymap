package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.data.CommonRepository
import com.szabolcshorvath.memorymap.data.MediaItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The media viewer's state. Its arguments hold only the memory and the item it opened on, so they stay small however
 * many photos the memory has; the media themselves come from the database, in the order the memory screen shows them,
 * and follow its changes (a re-link, a reorder, a removal).
 */
class MediaViewerViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {

    /** The memory's media, or null until they are loaded. An empty list: the memory or its media are gone. */
    val mediaItems: StateFlow<List<MediaItem>?> = CommonRepository.getInstance(application)
        .getGroupWithMedia(savedState.get<Int>(ARG_MEMORY_ID) ?: -1)
        .map { group -> group?.mediaItems?.sortedWith(MediaItem.DISPLAY_ORDER).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), null)

    /** The item on screen, by id: kept through rotation and process death, and right when the list changes. */
    var currentItemId: Int
        get() = savedState.get<Int>(CURRENT_ITEM_ID) ?: savedState.get<Int>(ARG_MEDIA_ITEM_ID) ?: NO_ITEM
        set(value) {
            savedState[CURRENT_ITEM_ID] = value
        }

    /** Where [currentItemId] is in [items], or null if it is gone. */
    fun currentPosition(items: List<MediaItem>): Int? = items.indexOfFirst { it.id == currentItemId }.takeIf { it >= 0 }

    companion object {
        const val ARG_MEMORY_ID = "memory_id"
        const val ARG_MEDIA_ITEM_ID = "media_item_id"
        private const val CURRENT_ITEM_ID = "current_item_id"
        private const val NO_ITEM = -1
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L
    }
}
