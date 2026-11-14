package com.szabolcshorvath.memorymap.ui.add

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.szabolcshorvath.memorymap.MemoryMapApplication
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaThumbnail
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.MemoryRepository
import com.szabolcshorvath.memorymap.ui.color.Hsv
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.MediaDetails
import com.szabolcshorvath.memorymap.util.MediaDetails.withDetails
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// The form's intents: one small function per thing the user can change.
@Suppress("TooManyFunctions")
class AddMemoryViewModel(
    application: Application,
    savedState: SavedStateHandle,
    private val memories: MemoryRepository
) : AndroidViewModel(application) {

    /** Handled while the Add screen is shown: one that comes while another screen is shown waits for it. */
    sealed class SaveResult {
        /** The form was cleared. [openMemory]: the save finished while the Add screen was shown, so the memory opens. */
        data class Success(val groupId: Int, val openMemory: Boolean) : SaveResult()

        /** The save failed; the cause is logged, and the user gets a plain message instead of the database's text. */
        data object Error : SaveResult()
    }

    private val _saveResult = Channel<SaveResult>(Channel.BUFFERED)
    val saveResult = _saveResult.receiveAsFlow()

    /** What the user is told after a save, wherever they are: the shell shows it over any screen. */
    enum class SaveNotice(@param:StringRes val text: Int, val long: Boolean) {
        /** The save finished after the user left the Add screen. */
        SAVED(R.string.memory_saved, long = false),

        /** An edit whose memory was deleted meanwhile was saved as a new memory. */
        SAVED_AS_NEW(R.string.memory_saved_as_new, long = true),
    }

    private val _saveNotices = Channel<SaveNotice>(Channel.BUFFERED)

    /** Collected whether or not the Add screen is shown, unlike [saveResult]. */
    val saveNotices = _saveNotices.receiveAsFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    /**
     * What the form holds: everything a save stores. Each change publishes the screen's state. Tests set it directly
     * for changes the screen has no intent for.
     */
    @VisibleForTesting
    internal var form = FormContent()
        set(value) {
            field = value
            publish()
        }

    /**
     * Photos and videos picked in this form, whose persisted grant the pick took. One that leaves the form unsaved
     * (removed, cleared or replaced by an edit) has its grant released ([releaseDroppedPicks]), or it would count
     * toward Android's cap of 512 and push out grants that saved media still need.
     */
    private val pickedUris = HashSet<String>()

    private val _pendingEdit = MutableStateFlow<Int?>(null)

    /** An "Edit" request waiting for the form's view (see [requestEdit]): the memory to edit. */
    val pendingEdit = _pendingEdit.asStateFlow()
    private var pendingEditMemoryId: Int?
        get() = _pendingEdit.value
        set(value) {
            _pendingEdit.value = value
        }

    /** The memory the form edits, or null for a new one. */
    var editingMemoryId: Int? = null
        private set

    /** The fragment whose place is being picked ([FragmentEditState.localId]), or null for the memory's. */
    private var pickingFragment: String? = null
    private var currentDeviceId: String? = null
    private var colorExpanded = false

    /** Whether the Add screen is shown (not hidden behind another tab); kept up to date by the route. */
    var isScreenShown = true

    /** Bumped when the form is cleared or filled for editing, so the screen shows the new text in its fields. */
    private var formVersion = 0

    /** Whether the fields a save needs show what is missing: from a save that lacked them until the form is cleared. */
    private var showErrors = false

    /** Bumped by each save that lacks something, so the screen scrolls to the first field that needs it. */
    private var errorScrollRequest = 0

    /** A message for the screen to show (a string resource), until it says it was shown. */
    @StringRes
    private var message: Int? = null

    private val _uiState = MutableStateFlow(buildUiState())

    /** What the Add screen shows. Every change to the form publishes a new one. */
    val uiState = _uiState.asStateFlow()

    /** The form's content when it was last cleared or loaded for editing, without how it looked. */
    private var unchangedContent = form.withoutLooks()

    init {
        // The draft survives process death: Android asks for it when it saves the app's state, and it is read back here.
        savedState.get<Bundle>(FORM_STATE)?.let(::restoreState)
        savedState.setSavedStateProvider(FORM_STATE, ::saveState)
        publish() // The restored draft.
    }

    /** Shows the form as it is now. */
    private fun publish() {
        _uiState.value = buildUiState()
    }

    private fun buildUiState() = AddFormUiState(
        formVersion = formVersion,
        editing = editingMemoryId != null,
        title = form.title.orEmpty(),
        description = form.description.orEmpty(),
        place = form.place,
        start = form.startDateTime,
        end = form.endDateTime,
        isAllDay = form.isAllDay,
        color = form.color,
        colorExpanded = colorExpanded,
        fragments = form.fragments,
        media = form.media,
        currentDeviceId = currentDeviceId,
        isSaving = _isSaving.value,
        showErrors = showErrors,
        errorScrollRequest = errorScrollRequest,
        message = message,
    )

    fun changeTitle(text: String) {
        form = form.copy(title = text.ifBlank { null })
    }

    fun changeDescription(text: String) {
        form = form.copy(description = text.ifBlank { null })
    }

    fun setAllDay(owner: FormPart, allDay: Boolean) {
        form = when (owner) {
            FormPart.Memory -> form.copy(isAllDay = allDay)
            is FormPart.Fragment -> form.withFragment(owner.localId) { it.copy(isAllDay = allDay) }
        }
    }

    /** Gives fragment [localId] dates of its own ([on]), starting now if it had none, or makes it use the memory's. */
    fun setOwnTime(localId: String, on: Boolean) {
        form = form.withFragment(localId) { current ->
            current.copy(
                isTimeVisible = on,
                startDate = if (on && current.startDate == null) ZonedDateTime.now() else current.startDate,
                endDate = if (on && current.endDate == null) ZonedDateTime.now().plusHours(1) else current.endDate,
            )
        }
    }

    /** Sets the start or end day of [owner] to [date], keeping its time; the other end moves if it would be out of order. */
    fun setDate(owner: FormPart, isStart: Boolean, date: LocalDate) {
        changeDateTime(owner, isStart) { current -> date.atTime(current.toLocalTime()).atZone(ZoneId.systemDefault()) }
    }

    /** Sets the start or end time of [owner] to [time]; the other end moves if it would be out of order. */
    fun setTime(owner: FormPart, isStart: Boolean, time: LocalTime) {
        changeDateTime(owner, isStart) { current -> current.with(time) }
    }

    /** Sets the days of an all-day [owner]. */
    fun setDateRange(owner: FormPart, first: LocalDate, last: LocalDate) {
        val start = first.atStartOfDay(ZoneId.systemDefault())
        val end = last.atStartOfDay(ZoneId.systemDefault())
        form = when (owner) {
            FormPart.Memory -> form.copy(startDateTime = start, endDateTime = end)
            is FormPart.Fragment -> form.withFragment(owner.localId) { it.copy(startDate = start, endDate = end) }
        }
    }

    private fun changeDateTime(owner: FormPart, isStart: Boolean, change: (ZonedDateTime) -> ZonedDateTime) {
        form = when (owner) {
            FormPart.Memory -> form.withDateTime(isStart, change)
            is FormPart.Fragment -> form.withFragment(owner.localId) { item -> item.withDateTime(isStart, change) }
        }
    }

    /** Sets the memory's start or end, and moves the other end an hour away if it would be out of order. */
    private fun FormContent.withDateTime(isStart: Boolean, change: (ZonedDateTime) -> ZonedDateTime): FormContent {
        if (isStart) {
            val start = change(startDateTime)
            return copy(startDateTime = start, endDateTime = if (endDateTime.isBefore(start)) start.plusHours(1) else endDateTime)
        }
        val end = change(endDateTime)
        return copy(startDateTime = if (end.isBefore(startDateTime)) end.minusHours(1) else startDateTime, endDateTime = end)
    }

    /** Sets the start or end of a fragment, and moves the other end if it would come before or after it. */
    private fun FragmentEditState.withDateTime(isStart: Boolean, change: (ZonedDateTime) -> ZonedDateTime): FragmentEditState {
        val picked = change((if (isStart) startDate else endDate) ?: ZonedDateTime.now())
        if (isStart) {
            var newEnd = endDate
            if (newEnd != null && newEnd.isBefore(picked)) newEnd = picked.plusHours(1)
            return copy(startDate = picked, endDate = newEnd)
        }
        var newStart = startDate
        if (newStart != null && picked.isBefore(newStart)) newStart = picked.minusHours(1)
        return copy(startDate = newStart, endDate = picked)
    }

    fun setColor(owner: FormPart, color: Hsv) {
        form = when (owner) {
            FormPart.Memory -> form.withColor(color)
            is FormPart.Fragment -> form.withFragment(owner.localId) {
                it.copy(markerHue = color.hue, markerSaturation = color.saturation, markerBrightness = color.brightness)
            }
        }
    }

    /** Opens or closes the colour sliders of [owner]. */
    fun toggleColor(owner: FormPart) {
        when (owner) {
            FormPart.Memory -> {
                colorExpanded = !colorExpanded
                publish()
            }

            is FormPart.Fragment -> form = form.withFragment(owner.localId) { it.copy(isColorExpanded = !it.isColorExpanded) }
        }
    }

    /** Adds a fragment at the memory's place, in its colour. */
    fun addFragment() {
        form = form.copy(
            fragments = form.fragments + FragmentEditState(
                lat = form.lat,
                lng = form.lng,
                placeName = form.placeName,
                address = form.address,
                markerHue = form.markerHue,
                markerSaturation = form.markerSaturation,
                markerBrightness = form.markerBrightness,
            )
        )
    }

    fun removeFragment(localId: String) {
        form = form.copy(fragments = form.fragments.filterNot { it.localId == localId })
    }

    /** Sets the place of the memory, or of the fragment whose location is being picked ([startPickingLocation]). */
    fun setPlace(newLat: Double, newLng: Double, newPlaceName: String?, newAddress: String?) {
        val fragment = pickingFragment
        form = if (fragment == null) {
            form.withPlace(newLat, newLng, newPlaceName, newAddress)
        } else {
            form.withFragment(fragment) { it.copy(lat = newLat, lng = newLng, placeName = newPlaceName, address = newAddress) }
        }
    }

    /** The form is shown: reads this installation's id once, which tells media saved on another phone apart. */
    fun onShown() {
        if (currentDeviceId != null) return
        viewModelScope.launch {
            currentDeviceId = InstallationIdentifier.getInstallationIdentifier(getApplication())
            publish()
        }
    }

    /** [uris] were just picked in the photo picker: takes their grants, reads them and adds them first. */
    fun mediaPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch { addPickedMedia(PickedMedia.read(getApplication(), uris, currentDeviceId)) }
    }

    /**
     * Starts picking the place of [part], the memory or one of its fragments: [open] opens the location picker on the
     * place it has, if any. Does nothing for a fragment that was removed meanwhile.
     */
    fun startPickingLocation(part: FormPart, open: (FormPlace?) -> Unit) {
        when (part) {
            FormPart.Memory -> {
                pickingFragment = null
                open(form.place)
            }

            is FormPart.Fragment -> {
                val fragment = form.fragments.find { it.localId == part.localId } ?: return
                pickingFragment = fragment.localId
                open(FormPlace.of(fragment.lat, fragment.lng, fragment.placeName, fragment.address))
            }
        }
    }

    /** Adds media just picked, first, leaving out what the form already has. Their grants were taken by the pick. */
    fun addPickedMedia(picked: List<SelectedMedia>) {
        pickedUris += picked.map { it.uri.toString() }
        form = form.copy(media = picked.filter { new -> form.media.none { it.uri == new.uri } } + form.media)
    }

    /**
     * Saves the form if it has everything a save needs. Otherwise the fields that lack something say so, and the
     * screen scrolls to the first.
     */
    fun requestSave() {
        if (form.isComplete) {
            saveMemoryGroup()
        } else {
            showErrors = true
            errorScrollRequest++
            publish()
        }
    }

    fun showMessage(@StringRes text: Int) {
        message = text
        publish()
    }

    fun onMessageShown() {
        message = null
        publish()
    }

    fun saveMemoryGroup() {
        if (_isSaving.value) return
        _isSaving.value = true
        publish()

        val saving = form
        val editingId = editingMemoryId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val saved = trace("add_memory_view_model_save_memory_group") {
                    // File reads (sizes, signatures, possibly a cloud download) happen before the save's transaction,
                    // so it only writes and doesn't block other writers, such as a backup snapshot, meanwhile.
                    val preparedMedia = prepareMediaItems(getApplication(), saving.media)
                    val detailed = readNewDetails(getApplication(), saving.media, preparedMedia)
                    memories.save(saving.toGroup(editingId ?: 0), editingId, detailed.items, detailed.thumbnails, saving.fragmentEntities())
                }
                withContext(Dispatchers.Main) { onSaved(saved.groupId, saved.savedAsNew) }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving memory group", e)
                _saveResult.send(SaveResult.Error)
            } finally {
                _isSaving.value = false
                withContext(Dispatchers.Main) {
                    releaseDroppedPicks()
                    publish()
                }
            }
        }
    }

    /** Removes [item] from the form, and releases its grant if this form took it and nothing saved uses it. */
    fun removeMedia(item: SelectedMedia) {
        form = form.copy(media = form.media - item)
        releaseDroppedPicks()
    }

    /**
     * Releases the grants of picked media that are no longer in the form, unless a saved memory uses them. Waits
     * while a save runs (it calls this again when done): the save may be storing them. Only grants this form took
     * are released: media loaded for editing may belong to a memory whose delete can still be undone.
     */
    private fun releaseDroppedPicks() {
        if (_isSaving.value) return
        val inForm = mediaUrisInForm()
        val dropped = pickedUris.filterNotTo(HashSet()) { it in inForm }
        if (dropped.isEmpty()) return
        pickedUris -= dropped
        getApplication<MemoryMapApplication>().applicationScope.launch { releaseGrants(dropped) }
    }

    /** Releases the grants of the given URIs that no saved media item uses. Tests replace it to see what the form releases. */
    @VisibleForTesting
    internal var releaseGrants: suspend (Set<String>) -> Unit = { uris -> memories.releaseGrantsIfUnused(uris) }

    /**
     * Clears the form here, so it is empty whenever the screen is shown next. Opens the saved memory only
     * if the Add screen is on screen: a save that finishes after the user left must not jump there later.
     * [savedAsNew]: an edit whose memory was deleted meanwhile was saved as a new memory, which the user is told.
     */
    private suspend fun onSaved(groupId: Int, savedAsNew: Boolean) {
        resetForm()
        if (savedAsNew) {
            _saveNotices.send(SaveNotice.SAVED_AS_NEW)
        } else if (!isScreenShown) {
            _saveNotices.send(SaveNotice.SAVED)
        }
        _saveResult.send(SaveResult.Success(groupId, openMemory = isScreenShown))
    }

    /** Whether the form differs from how it was last cleared or loaded for editing, in anything a save would store. */
    fun hasUnsavedChanges(): Boolean = form.withoutLooks() != unchangedContent

    /** Takes the current content as unchanged: after the form is cleared or a memory is loaded for editing. */
    fun markUnchanged() {
        unchangedContent = form.withoutLooks()
    }

    /** The draft and what it is for (a new memory or an edit), for the saved state. Read back by [restoreState]. */
    private fun saveState() = Bundle().apply {
        putBundle(FORM, form.toBundle())
        putBundle(UNCHANGED_FORM, unchangedContent.toBundle())
        editingMemoryId?.let { putInt(EDITING_MEMORY_ID, it) }
        pendingEditMemoryId?.let { putInt(PENDING_EDIT_MEMORY_ID, it) }
        putStringArrayList(PICKED_URIS, ArrayList(pickedUris))
        pickingFragment?.let { putString(PICKING_FRAGMENT, it) }
        putBoolean(COLOR_EXPANDED, colorExpanded)
    }

    private fun restoreState(state: Bundle) {
        state.getBundle(FORM)?.let { form = it.toFormContent() }
        state.getBundle(UNCHANGED_FORM)?.let { unchangedContent = it.toFormContent() }
        state.getStringArrayList(PICKED_URIS)?.let { pickedUris += it }
        editingMemoryId = state.getInt(EDITING_MEMORY_ID).takeIf { state.containsKey(EDITING_MEMORY_ID) }
        pendingEditMemoryId = state.getInt(PENDING_EDIT_MEMORY_ID).takeIf { state.containsKey(PENDING_EDIT_MEMORY_ID) }
        pickingFragment = state.getString(PICKING_FRAGMENT)
        colorExpanded = state.getBoolean(COLOR_EXPANDED)
    }

    /** Another tab was selected: an edit without changes is closed, so this tab offers a new memory next time. */
    fun closeUnchangedEdit() {
        if (editingMemoryId == null || hasUnsavedChanges()) return
        resetForm()
    }

    /** The URIs of the media in the form (a draft or an edit), which a save may still store. */
    fun mediaUrisInForm(): Set<String> = form.media.mapTo(HashSet()) { it.uri.toString() }

    /**
     * Asks to open memory [memoryId] for editing. The request stays pending until the form's view handles it
     * ([handlePendingEdit]), and a newer one replaces it.
     */
    fun requestEdit(memoryId: Int) {
        pendingEditMemoryId = memoryId
    }

    /**
     * Handles the pending edit request, if any: opens the memory when the form has no unsaved changes, and keeps the
     * changes when it is the memory already being edited. Returns the memory to ask about otherwise, whether the
     * request may replace the unsaved changes ([confirmEdit] or [cancelEdit]); it stays pending meanwhile.
     */
    fun handlePendingEdit(): Int? {
        val memoryId = pendingEditMemoryId ?: return null
        return when {
            !hasUnsavedChanges() -> null.also { startEditing(memoryId) }
            memoryId == editingMemoryId -> null.also { pendingEditMemoryId = null }
            else -> memoryId
        }
    }

    /** The user let the request for memory [memoryId] replace the unsaved changes. */
    fun confirmEdit(memoryId: Int) {
        pendingEditMemoryId = memoryId // After process death, the new ViewModel may no longer have it.
        startEditing(memoryId)
    }

    /** The user kept the unsaved changes: the request for memory [memoryId] is dropped. */
    fun cancelEdit(memoryId: Int) {
        if (pendingEditMemoryId == memoryId) pendingEditMemoryId = null
    }

    private fun startEditing(memoryId: Int) {
        viewModelScope.launch {
            val groupWithMedia = memories.getGroupWithMedia(memoryId)
            if (pendingEditMemoryId != memoryId) return@launch // A newer request replaced this one.
            pendingEditMemoryId = null
            // Null if the memory was deleted meanwhile: then the form stays as it is.
            groupWithMedia?.let {
                fillForEditing(it)
                markUnchanged()
            }
        }
    }

    /** Fills the form with [data], a memory opened for editing. */
    fun fillForEditing(data: MemoryGroupWithMedia) {
        editingMemoryId = data.group.id
        startOver(FormContent.of(data))
    }

    /** Clears the form: after a successful save, or when the user clears it. */
    fun resetForm() {
        editingMemoryId = null
        colorExpanded = false
        startOver(FormContent())
        markUnchanged()
    }

    /** Shows [content] as a new start: in the text fields too, without the errors of an earlier save. */
    private fun startOver(content: FormContent) {
        formVersion++
        showErrors = false
        form = content
        releaseDroppedPicks()
    }

    /** The prepared media with the details of the newly picked ones, and their thumbnails. */
    private class WithDetails(val items: List<MediaItem>, val thumbnails: List<MediaThumbnail>)

    /**
     * Reads the details (name, size in pixels, thumbnail, perceptual hash) of the media picked in this form: they can
     * be read now, through the pick's grant. Media loaded for editing keep theirs ([MemoryRepository.save]). Reads files, so it
     * runs before the save transaction.
     */
    private fun readNewDetails(context: Context, selected: List<SelectedMedia>, prepared: List<MediaItem>): WithDetails {
        val newUris = selected.filter { it.mediaSignature.isEmpty() }.mapTo(HashSet()) { it.uri.toString() }
        val thumbnails = mutableListOf<MediaThumbnail>()
        val items = prepared.map { item ->
            if (item.uri !in newUris) return@map item
            val details = MediaDetails.read(context, item.uri.toUri(), item.type)
            val thumbnail = details.thumbnail
            if (thumbnail != null && item.mediaSignature.isNotEmpty()) thumbnails += MediaThumbnail(item.mediaSignature, thumbnail)
            item.withDetails(details)
        }
        return WithDetails(items, thumbnails)
    }

    /**
     * Builds the media items to save (group id still 0): fills in missing sizes, dates and signatures,
     * drops duplicates and numbers them. Reads files, so it runs before the save transaction.
     */
    private fun prepareMediaItems(context: Context, mediaToSave: List<SelectedMedia>): List<MediaItem> {
        return mediaToSave.map { selected ->
            val uri = selected.uri
            var size = selected.fileSize
            var date = selected.dateTaken

            if (size == 0L) {
                val metadata = LocalMediaUtil.queryMetadata(context, uri)
                if (metadata != null) {
                    size = metadata.size
                    date = metadata.dateTaken
                } else {
                    Log.w(TAG, "Could not read the size and date of $uri")
                }
            }

            // Keep a stored signature: it identifies the original file for re-linking, while the URI
            // of an item from another device may now resolve to an unrelated file.
            val signature = selected.mediaSignature.ifEmpty {
                LocalMediaUtil.signatureOrNull(context, uri).orEmpty().also {
                    if (it.isEmpty()) Log.w(TAG, "Could not calculate signature for $uri (file missing or unreadable)")
                }
            }

            // The picker URI is kept as is: its persisted grant survives reboots and does not depend on
            // READ_MEDIA_* permissions, which the user can revoke and Android auto-resets for unused apps.
            MediaItem(
                groupId = 0,
                uri = uri.toString(),
                deviceId = selected.deviceId,
                type = selected.type,
                mediaSignature = signature,
                fileSize = size,
                dateTaken = date
            )
        }
            .distinctBy { it.mediaSignature.ifEmpty { it.uri } }
            .mapIndexed { index, item -> item.copy(order = index + 1) }
    }

    companion object {
        private const val TAG = "AddMemoryVM"
        private const val FORM_STATE = "add_memory_form"
        private const val FORM = "form"
        private const val UNCHANGED_FORM = "unchanged_form"
        private const val EDITING_MEMORY_ID = "editing_memory_id"
        private const val PENDING_EDIT_MEMORY_ID = "pending_edit_memory_id"
        private const val PICKING_FRAGMENT = "picking_fragment"
        private const val PICKED_URIS = "picked_uris"
        private const val COLOR_EXPANDED = "color_expanded"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                AddMemoryViewModel(app, createSavedStateHandle(), app.appContainer.memories)
            }
        }
    }
}
