package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryRepository
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.MediaRelinker
import com.szabolcshorvath.memorymap.util.MissingMedia
import com.szabolcshorvath.memorymap.util.NearbyFile
import com.szabolcshorvath.memorymap.util.NearbyMedia
import com.szabolcshorvath.memorymap.util.PermissionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The state of [MissingMediaSheet] for one media item that can't be re-linked. It survives a rotation; after process
 * death the sheet starts again from the item's id.
 */
class MissingMediaSheetViewModel(application: Application, private val memories: MemoryRepository) : AndroidViewModel(application) {

    sealed interface State {
        /** Reading the item. */
        data object Loading : State

        /** A re-link is running; [progress] is its latest message. */
        data class Looking(val progress: String?) : State

        /** Looking for the file and for nearby ones needs access to all photos and videos. */
        data object NeedsAccess : State

        /** The file isn't on this phone. [nearby] were taken around the same time, within the [wider] window or not. */
        data class NotFound(val nearby: List<NearbyFile>, val wider: Boolean, val dateKnown: Boolean) : State

        /** The re-link found the file: the memory shows it again. */
        data object Found : State

        /** The item was deleted or changed meanwhile, or the user fixed it here: the sheet closes. */
        data object Done : State
    }

    /** What an action of the user led to, for a message. */
    enum class Outcome { CHANGED, ALREADY_IN_MEMORY, UNREADABLE }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    private val _outcomes = Channel<Outcome>(Channel.BUFFERED)
    val outcomes = _outcomes.receiveAsFlow()

    /** The item as last read, once loaded. */
    var item: MediaItem? = null
        private set

    /** The item's stored thumbnail (a JPEG), if it has one: how the lost photo looked. */
    var thumbnail: ByteArray? = null
        private set
    private var memoryItems: List<MediaItem> = emptyList()
    private var checking: Job? = null

    private val app get() = getApplication<Application>()

    /** Starts with item [itemId], once: a rotation keeps what was found. */
    fun start(itemId: Int) {
        if (checking == null) check(itemId)
    }

    /** Media access may have changed: checks again. */
    fun onAccessChanged(itemId: Int) {
        if (_state.value == State.NeedsAccess) check(itemId)
    }

    /** Widens the search for nearby files to [NearbyMedia.WIDER_WINDOW_MS], once. */
    fun showMore() {
        val current = (_state.value as? State.NotFound)?.takeIf { !it.wider }
        val item = item
        if (current == null || item == null) return
        viewModelScope.launch {
            _state.value = State.NotFound(findNearby(item, NearbyMedia.WIDER_WINDOW_MS), wider = true, dateKnown = current.dateKnown)
        }
    }

    /** Replaces the item with [uri], one of the nearby files. */
    fun use(uri: String) = act { item ->
        val file = withContext(Dispatchers.IO) { MissingMedia.describe(app, uri.toUri()) } ?: return@act Outcome.UNREADABLE
        replace(item, file)
    }

    /** Replaces the item with [uri], a photo or video the user picked. The pick's grant is taken here. */
    fun choose(uri: Uri) = act { item ->
        val file = withContext(Dispatchers.IO) {
            try {
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not persist access for $uri", e)
            }
            MissingMedia.describe(app, uri)
        } ?: return@act Outcome.UNREADABLE
        val others = memoryItems.filter { it.id != item.id }
        if (others.any { it.uri == file.uri || it.mediaSignature == file.signature }) {
            memories.releaseGrantsIfUnused(listOf(file.uri))
            return@act Outcome.ALREADY_IN_MEMORY
        }
        replace(item, file)
    }

    /** Removes the item from its memory. */
    fun remove() = act { item ->
        if (withContext(Dispatchers.IO) { MissingMedia.remove(app, item) }) null else Outcome.CHANGED
    }

    private suspend fun replace(item: MediaItem, file: MissingMedia.MediaFile): Outcome? =
        if (withContext(Dispatchers.IO) { MissingMedia.replace(app, item, file) }) null else Outcome.CHANGED

    /** Runs [action] on the loaded item. A null outcome means it was done, and the sheet closes. */
    private fun act(action: suspend (MediaItem) -> Outcome?) {
        val item = item ?: return
        viewModelScope.launch {
            val outcome = action(item)
            if (outcome == null) _state.value = State.Done else _outcomes.send(outcome)
        }
    }

    private fun check(itemId: Int) {
        checking = viewModelScope.launch {
            _state.value = State.Loading
            val loaded = load(itemId) ?: return@launch done()
            if (!isMissing(loaded)) return@launch done()
            if (!PermissionUtil.hasAllMediaPermissions(app)) {
                _state.value = State.NeedsAccess
                return@launch
            }
            _state.value = State.Looking(null)
            MediaRelinker.relink(app) { _state.value = State.Looking(it) }
            val after = load(itemId) ?: return@launch done() // De-duplicated or deleted meanwhile.
            if (!isMissing(after)) {
                _state.value = State.Found
                return@launch
            }
            _state.value = State.NotFound(findNearby(after, NearbyMedia.FIRST_WINDOW_MS), wider = false, dateKnown = after.dateTaken > 0)
        }
    }

    private fun done() {
        _state.value = State.Done
    }

    /** Reads the item and the rest of its memory's media. Null if the item is gone. */
    private suspend fun load(itemId: Int): MediaItem? = run {
        val loaded = memories.getMediaItem(itemId) ?: return@run null
        memoryItems = memories.getGroupWithMedia(loaded.groupId)?.mediaItems.orEmpty()
        thumbnail = memories.getThumbnails(listOf(loaded.mediaSignature))[loaded.mediaSignature]
        loaded
    }.also { item = it }

    private suspend fun isMissing(item: MediaItem) = withContext(Dispatchers.IO) { LocalMediaUtil.hasMissingMedia(app, listOf(item)) }

    private suspend fun findNearby(item: MediaItem, windowMs: Long) =
        withContext(Dispatchers.IO) { NearbyMedia.find(app, item, memoryItems, windowMs) }

    companion object {
        private const val TAG = "MissingMediaSheetVM"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                MissingMediaSheetViewModel(app, app.appContainer.memories)
            }
        }
    }
}
