package com.szabolcshorvath.memorymap.fragment

import android.app.Application
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
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.MemoryRepository
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.MediaRelinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.szabolcshorvath.memorymap.data.MemoryFragment as MemoryFragmentEntity

/**
 * A photo or video of the memory. [missing] when it needs re-linking (`LocalMediaUtil.hasMissingMedia`): its tile
 * shows what it was, over its stored [thumbnail] if it has one, without loading it.
 */
class MediaTile(val item: MediaItem, val missing: Boolean, val thumbnail: ByteArray?) {
    override fun equals(other: Any?) = other is MediaTile && other.item == item && other.missing == missing &&
        other.thumbnail.contentEquals(thumbnail)

    override fun hashCode() = 31 * item.hashCode() + missing.hashCode()
}

/** What the memory page shows. [group] is null until the memory is loaded, and once it has been deleted. */
data class MemoryUiState(
    val group: MemoryGroup? = null,
    val fragments: List<MemoryFragmentEntity> = emptyList(),
    val media: List<MediaTile> = emptyList(),
    /** A re-link runs, started from the could-not-load banner; [relinkStatus] says how far it got. */
    val relinking: Boolean = false,
    val relinkStatus: String? = null,
) {
    val hasMissingMedia get() = media.any { it.missing }
}

class MemoryFragmentViewModel(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val memories: MemoryRepository,
) : ViewModel() {
    /** The memory this page shows, from its arguments. */
    val memoryId = savedStateHandle.get<Int>(ARG_MEMORY_ID) ?: -1

    private data class Relink(val running: Boolean = false, val status: String? = null)

    private val relink = MutableStateFlow(Relink())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content = memories.groupWithMediaFlow(memoryId).mapLatest { data -> data?.let { load(it) } ?: MemoryUiState() }

    val uiState: StateFlow<MemoryUiState> = combine(content, relink) { state, relink ->
        state.copy(relinking = relink.running, relinkStatus = relink.status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), MemoryUiState())

    private var mediaOrderJob: Job? = null
    private var fragmentsOrderJob: Job? = null

    private suspend fun load(data: MemoryGroupWithMedia): MemoryUiState {
        val media = data.mediaItems.sortedWith(MediaItem.DISPLAY_ORDER)
        val missing = withContext(Dispatchers.IO) {
            media.filter { LocalMediaUtil.hasMissingMedia(application, listOf(it)) }.mapTo(HashSet()) { it.id }
        }
        // A tile that can't be loaded shows the photo's stored thumbnail, if it has one. Also when the check above
        // doesn't know yet: a photo deleted from the gallery while the app runs fails only when it loads.
        val thumbnails = memories.getThumbnails(media.map { it.mediaSignature })
        return MemoryUiState(
            group = data.group,
            fragments = data.fragments.sortedWith(MemoryFragmentEntity.DISPLAY_ORDER),
            media = media.map { MediaTile(it, it.id in missing, thumbnails[it.mediaSignature]) },
        )
    }

    /** Whether [item] needs re-linking, which opens the sheet that fixes it instead of the viewer. */
    suspend fun isMissing(item: MediaItem): Boolean =
        withContext(Dispatchers.IO) { LocalMediaUtil.hasMissingMedia(application, listOf(item)) }

    /** Re-links the media of every memory (through [MediaRelinker], so it outlives the screen). */
    fun relink() {
        if (relink.value.running) return
        viewModelScope.launch {
            relink.value = Relink(running = true)
            try {
                MediaRelinker.relink(application) { status -> relink.update { it.copy(status = status) } }
            } finally {
                relink.update { it.copy(running = false) }
            }
        }
    }

    /** Stores [items] in the order shown. */
    fun saveMediaOrder(items: List<MediaItem>) {
        mediaOrderJob?.cancel()
        mediaOrderJob = viewModelScope.launch { memories.saveMediaOrder(items) }
    }

    /** Stores [fragments] in the order shown. */
    fun saveFragmentsOrder(fragments: List<MemoryFragmentEntity>) {
        fragmentsOrderJob?.cancel()
        fragmentsOrderJob = viewModelScope.launch { memories.saveFragmentsOrder(fragments) }
    }

    /** Deletes the memory, and returns what was deleted for the undo, or null if it was already gone. */
    suspend fun delete(): MemoryGroupWithMedia? = memories.delete(memoryId)

    companion object {
        const val ARG_MEMORY_ID = "memory_id"
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY]!!
                MemoryFragmentViewModel(createSavedStateHandle(), application, application.appContainer.memories)
            }
        }
    }
}
