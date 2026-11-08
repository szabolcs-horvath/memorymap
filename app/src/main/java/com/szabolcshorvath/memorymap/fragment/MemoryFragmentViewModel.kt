package com.szabolcshorvath.memorymap.fragment

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
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.MemoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.szabolcshorvath.memorymap.data.MemoryFragment as MemoryFragmentEntity

class MemoryFragmentViewModel(savedStateHandle: SavedStateHandle, private val memories: MemoryRepository) : ViewModel() {
    private val memoryId = savedStateHandle.get<Int>(ARG_MEMORY_ID) ?: -1

    val groupWithMedia: StateFlow<MemoryGroupWithMedia?> = memories.groupWithMediaFlow(memoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), null)

    var isFragmentsExpanded: Boolean = true
    var currentDeviceId: String? = null
    var mediaItems: List<MediaItem> = emptyList()
    var fragmentItems: List<MemoryFragmentEntity> = emptyList()

    private var mediaOrderJob: Job? = null
    private var fragmentsOrderJob: Job? = null

    /** Stores [mediaItems] in the order shown. */
    fun saveMediaOrder() {
        mediaOrderJob?.cancel()
        val items = mediaItems
        mediaOrderJob = viewModelScope.launch { memories.saveMediaOrder(items) }
    }

    /** Stores [fragmentItems] in the order shown. */
    fun saveFragmentsOrder() {
        fragmentsOrderJob?.cancel()
        val fragments = fragmentItems
        fragmentsOrderJob = viewModelScope.launch { memories.saveFragmentsOrder(fragments) }
    }

    /** The stored thumbnails of [items], by signature. */
    suspend fun thumbnails(items: List<MediaItem>): Map<String, ByteArray> = memories.getThumbnails(items.map { it.mediaSignature })

    /** Deletes the memory, and returns what was deleted for the undo, or null if it was already gone. */
    suspend fun delete(): MemoryGroupWithMedia? = memories.delete(memoryId)

    companion object {
        const val ARG_MEMORY_ID = "memory_id"
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { MemoryFragmentViewModel(createSavedStateHandle(), this[APPLICATION_KEY]!!.appContainer.memories) }
        }
    }
}
