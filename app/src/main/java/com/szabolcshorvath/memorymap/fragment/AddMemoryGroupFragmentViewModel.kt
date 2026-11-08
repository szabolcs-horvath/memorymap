package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.MemoryMapApplication
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaThumbnail
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupDao
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.MediaDetails
import com.szabolcshorvath.memorymap.util.MediaDetails.withDetails
import com.szabolcshorvath.memorymap.util.MediaDetails.withDetailsOf
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PerfUtil.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AddMemoryGroupFragmentViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {

    /** Handled while the Add screen is shown: one that comes while another screen is shown waits for it. */
    sealed class SaveResult {
        /** The form was cleared. [openMemory]: the save finished while the Add screen was shown, so the memory opens. */
        data class Success(val groupId: Int, val openMemory: Boolean) : SaveResult()

        /** The save failed; the cause is logged, and the user gets a plain message instead of the database's text. */
        data object Error : SaveResult()
    }

    private val _saveResult = Channel<SaveResult>(Channel.BUFFERED)
    val saveResult = _saveResult.receiveAsFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    var selectedMedia = mutableListOf<AddMemoryGroupFragment.SelectedMedia>()

    /**
     * Photos and videos picked in this form, whose persisted grant the pick took. One that leaves the form unsaved
     * (removed, cleared or replaced by an edit) has its grant released ([releaseDroppedPicks]), or it would count
     * toward Android's cap of 512 and push out grants that saved media still need.
     */
    private val pickedUris = HashSet<String>()

    var fragments = mutableListOf<MemoryFragmentEditAdapter.FragmentEditState>()

    var title: String? = null
    var description: String? = null

    var lat: Double? = null
    var lng: Double? = null
    var placeName: String? = null
    var address: String? = null

    var startDateTime: ZonedDateTime = ZonedDateTime.now()
    var endDateTime: ZonedDateTime = ZonedDateTime.now().plusHours(1)
    var isAllDay = false
    var markerHue = DEFAULT_MARKER_HUE
    var markerSaturation = DEFAULT_MARKER_SATURATION
    var markerBrightness = DEFAULT_MARKER_BRIGHTNESS

    /** An "Edit" request waiting for the fragment's view (see `AddMemoryGroupFragment.requestEditMode`). */
    var pendingEditMemoryId: Int? = null
    var editingMemoryId: Int? = null
    var activePickingIndex: Int = -1 // -1 for main, 0+ for fragments
    var currentDeviceId: String? = null
    var fragmentsExpanded = true
    var colorExpanded = false
    var dateExpanded = true

    /** Whether the Add screen is shown (not hidden behind another tab); kept up to date by the fragment. */
    var isScreenShown = true

    /** The form's content when it was last cleared or loaded for editing. Declared after the fields it reads. */
    private var unchangedContent = currentContent()

    init {
        // The draft survives process death: Android asks for it when it saves the app's state, and it is read back here.
        savedState.get<Bundle>(FORM_STATE)?.let(::restoreState)
        savedState.setSavedStateProvider(FORM_STATE, ::saveState)
    }

    fun saveMemoryGroup(database: MemoryMapDatabase) {
        if (_isSaving.value) return
        _isSaving.value = true

        val snapshotMedia = selectedMedia.toList()
        val snapshotFragments = fragments.toList()
        val snapshotTitle = title!!
        val snapshotDescription = description
        val snapshotLat = lat!!
        val snapshotLng = lng!!
        val snapshotPlaceName = placeName
        val snapshotAddress = address
        val snapshotStart = startDateTime
        val snapshotEnd = endDateTime
        val snapshotIsAllDay = isAllDay
        val snapshotHue = markerHue
        val snapshotSat = markerSaturation
        val snapshotBri = markerBrightness
        val snapshotEditingId = editingMemoryId

        viewModelScope.launch(Dispatchers.IO) {
            var savedAsNew = false
            try {
                val effectiveStart = calculateEffectiveStartTime(snapshotIsAllDay, snapshotStart)
                val effectiveEnd = calculateEffectiveEndTime(snapshotIsAllDay, snapshotEnd)
                val group = assembleMemoryGroup(
                    snapshotEditingId, snapshotTitle, snapshotDescription, snapshotLat, snapshotLng,
                    snapshotPlaceName, snapshotAddress, effectiveStart, effectiveEnd,
                    snapshotIsAllDay, snapshotHue, snapshotSat, snapshotBri
                )
                val groupIdResult = trace("add_memory_group_view_model_save_memory_group") {
                    // File reads (sizes, signatures, possibly a cloud download) happen before the transaction,
                    // so it only writes and doesn't block other writers, such as a backup snapshot, meanwhile.
                    val preparedMedia = prepareMediaItems(getApplication(), snapshotMedia)
                    val detailed = readNewDetails(getApplication(), snapshotMedia, preparedMedia)
                    val dao = database.memoryGroupDao()

                    var removedUris: Set<String> = emptySet()
                    val savedId = database.withTransaction("save_memory_group") {
                        // An edit of a memory deleted meanwhile (from its own screen) updates nothing:
                        // it is saved as a new memory, so the user's changes aren't lost.
                        val updatedId = snapshotEditingId?.takeIf { dao.updateGroup(group) > 0 }
                        savedAsNew = snapshotEditingId != null && updatedId == null
                        val groupId = updatedId?.toLong() ?: dao.insertGroup(group.copy(id = 0))

                        removedUris = saveMediaItems(dao, groupId.toInt(), detailed.items, updatedId)
                        detailed.thumbnails.forEach { database.mediaThumbnailDao().insert(it) }
                        saveMemoryFragments(dao, groupId, snapshotFragments)
                        groupId.toInt()
                    }

                    LocalMediaUtil.releaseUriGrantsIfUnused(getApplication(), dao, removedUris)
                    savedId
                }
                // In the background: saving no longer waits for the upload.
                AutomaticBackups.schedule(getApplication())
                withContext(Dispatchers.Main) { onSaved(groupIdResult, savedAsNew) }
            } catch (e: Exception) {
                Log.e("AddMemoryVM", "Error saving memory group", e)
                _saveResult.send(SaveResult.Error)
            } finally {
                _isSaving.value = false
                withContext(Dispatchers.Main) { releaseDroppedPicks() }
            }
        }
    }

    /** Remembers [uris], just picked, whose persisted grant the pick took. */
    fun onMediaPicked(uris: Collection<String>) {
        pickedUris += uris
    }

    /** Removes [item] from the form, and releases its grant if this form took it and nothing saved uses it. */
    fun removeMedia(item: AddMemoryGroupFragment.SelectedMedia) {
        selectedMedia.remove(item)
        releaseDroppedPicks()
    }

    /**
     * Releases the grants of picked media that are no longer in the form, unless a saved memory uses them. Waits
     * while a save runs (it calls this again when done): the save may be storing them. Only grants this form took
     * are released: media loaded for editing may belong to a memory whose delete can still be undone.
     */
    private fun releaseDroppedPicks() {
        if (_isSaving.value) return
        val inForm = selectedMedia.mapTo(HashSet()) { it.uri.toString() }
        val dropped = pickedUris.filterNotTo(HashSet()) { it in inForm }
        if (dropped.isEmpty()) return
        pickedUris -= dropped
        getApplication<MemoryMapApplication>().applicationScope.launch { releaseGrants(dropped) }
    }

    /** Releases the grants of the given URIs that no saved media item uses. Tests replace it to see what the form releases. */
    @VisibleForTesting
    internal var releaseGrants: suspend (Set<String>) -> Unit = { uris ->
        val app = getApplication<Application>()
        LocalMediaUtil.releaseUriGrantsIfUnused(app, MemoryMapDatabase.getDatabase(app).memoryGroupDao(), uris)
    }

    /**
     * Clears the form here, so it is empty whenever the screen is shown next. Opens the saved memory only
     * if the Add screen is on screen: a save that finishes after the user left must not jump there later.
     * [savedAsNew]: an edit whose memory was deleted meanwhile was saved as a new memory, which the user is told.
     */
    private suspend fun onSaved(groupId: Int, savedAsNew: Boolean) {
        resetForm()
        if (savedAsNew) {
            Toast.makeText(getApplication(), R.string.memory_saved_as_new, Toast.LENGTH_LONG).show()
        }
        if (!isScreenShown && !savedAsNew) {
            Toast.makeText(getApplication(), R.string.memory_saved, Toast.LENGTH_SHORT).show()
        }
        _saveResult.send(SaveResult.Success(groupId, openMemory = isScreenShown))
    }

    /** Whether the form differs from how it was last cleared or loaded for editing, in anything a save would store. */
    fun hasUnsavedChanges(): Boolean = currentContent() != unchangedContent

    /** Takes the current content as unchanged: after the form is cleared or a memory is loaded for editing. */
    fun markUnchanged() {
        unchangedContent = currentContent()
    }

    private fun currentContent() = FormContent(
        title, description, lat, lng, placeName, address, startDateTime, endDateTime, isAllDay,
        markerHue, markerSaturation, markerBrightness, selectedMedia.toList(),
        // Whether a fragment's sections are expanded is only how the form looks.
        fragments.map { it.copy(isDateExpanded = false, isColorExpanded = false) }
    )

    /** The draft and what it is for (a new memory or an edit), for the saved state. Read back by [restoreState]. */
    private fun saveState() = Bundle().apply {
        val form = FormContent(
            title, description, lat, lng, placeName, address, startDateTime, endDateTime, isAllDay,
            markerHue, markerSaturation, markerBrightness, selectedMedia.toList(), fragments.toList()
        )
        putBundle(FORM, form.toBundle())
        putBundle(UNCHANGED_FORM, unchangedContent.toBundle())
        editingMemoryId?.let { putInt(EDITING_MEMORY_ID, it) }
        pendingEditMemoryId?.let { putInt(PENDING_EDIT_MEMORY_ID, it) }
        putStringArrayList(PICKED_URIS, ArrayList(pickedUris))
        putInt(ACTIVE_PICKING_INDEX, activePickingIndex)
        putBoolean(FRAGMENTS_EXPANDED, fragmentsExpanded)
        putBoolean(COLOR_EXPANDED, colorExpanded)
        putBoolean(DATE_EXPANDED, dateExpanded)
    }

    private fun restoreState(state: Bundle) {
        state.getBundle(FORM)?.toFormContent()?.let { form ->
            title = form.title
            description = form.description
            lat = form.lat
            lng = form.lng
            placeName = form.placeName
            address = form.address
            startDateTime = form.startDateTime
            endDateTime = form.endDateTime
            isAllDay = form.isAllDay
            markerHue = form.markerHue
            markerSaturation = form.markerSaturation
            markerBrightness = form.markerBrightness
            selectedMedia.addAll(form.media)
            fragments.addAll(form.fragments)
        }
        state.getBundle(UNCHANGED_FORM)?.let { unchangedContent = it.toFormContent() }
        state.getStringArrayList(PICKED_URIS)?.let { pickedUris += it }
        editingMemoryId = state.getInt(EDITING_MEMORY_ID).takeIf { state.containsKey(EDITING_MEMORY_ID) }
        pendingEditMemoryId = state.getInt(PENDING_EDIT_MEMORY_ID).takeIf { state.containsKey(PENDING_EDIT_MEMORY_ID) }
        activePickingIndex = state.getInt(ACTIVE_PICKING_INDEX, -1)
        fragmentsExpanded = state.getBoolean(FRAGMENTS_EXPANDED, true)
        colorExpanded = state.getBoolean(COLOR_EXPANDED)
        dateExpanded = state.getBoolean(DATE_EXPANDED, true)
    }

    /** Fills the form with [data], a memory opened for editing. */
    fun fillForEditing(data: MemoryGroupWithMedia) {
        val group = data.group
        editingMemoryId = group.id
        lat = group.latitude
        lng = group.longitude
        placeName = group.placeName
        address = group.address
        title = group.title
        description = group.description
        isAllDay = group.isAllDay
        startDateTime = group.startDate
        endDateTime = group.endDate
        markerHue = group.markerHue ?: DEFAULT_MARKER_HUE
        markerSaturation = group.markerSaturation ?: DEFAULT_MARKER_SATURATION
        markerBrightness = group.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
        dateExpanded = true

        selectedMedia.clear()
        selectedMedia.addAll(
            data.mediaItems.sortedWith(MediaItem.DISPLAY_ORDER).map {
                AddMemoryGroupFragment.SelectedMedia(it.uri.toUri(), it.type, it.deviceId, it.fileSize, it.dateTaken, it.mediaSignature)
            }
        )

        fragments.clear()
        fragments.addAll(
            data.fragments.sortedWith(MemoryFragment.DISPLAY_ORDER).map {
                MemoryFragmentEditAdapter.FragmentEditState(
                    id = it.id,
                    lat = it.latitude,
                    lng = it.longitude,
                    placeName = it.placeName,
                    address = it.address,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    isAllDay = it.isAllDay,
                    markerHue = it.markerHue ?: DEFAULT_MARKER_HUE,
                    markerSaturation = it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                    markerBrightness = it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS,
                    isTimeVisible = it.startDate != null,
                    isDateExpanded = false,
                    isColorExpanded = false,
                    order = it.order
                )
            }
        )
        fragmentsExpanded = true
        releaseDroppedPicks()
    }

    /** Clears the form: after a successful save, or when the user clears it. */
    fun resetForm() {
        editingMemoryId = null
        title = null
        description = null
        lat = null
        lng = null
        placeName = null
        address = null
        isAllDay = false
        startDateTime = ZonedDateTime.now()
        endDateTime = ZonedDateTime.now().plusHours(1)
        markerHue = DEFAULT_MARKER_HUE
        markerSaturation = DEFAULT_MARKER_SATURATION
        markerBrightness = DEFAULT_MARKER_BRIGHTNESS
        dateExpanded = true
        selectedMedia.clear()
        fragments.clear()
        fragmentsExpanded = true
        colorExpanded = false
        markUnchanged()
        releaseDroppedPicks()
    }

    private fun calculateEffectiveStartTime(isAllDay: Boolean, startDateTime: ZonedDateTime): ZonedDateTime = if (isAllDay) {
        startDateTime.toLocalDate().atStartOfDay(ZoneId.systemDefault())
    } else {
        startDateTime
    }

    private fun calculateEffectiveEndTime(isAllDay: Boolean, endDateTime: ZonedDateTime): ZonedDateTime = if (isAllDay) {
        endDateTime.toLocalDate()
            .atTime(LocalTime.MAX)
            .atZone(ZoneId.systemDefault())
    } else {
        endDateTime
    }

    @Suppress("LongParameterList")
    private fun assembleMemoryGroup(
        editingId: Int?,
        title: String,
        description: String?,
        lat: Double,
        lng: Double,
        placeName: String?,
        address: String?,
        effectiveStart: ZonedDateTime,
        effectiveEnd: ZonedDateTime,
        isAllDay: Boolean,
        markerHue: Float,
        markerSaturation: Float,
        markerBrightness: Float
    ): MemoryGroup = MemoryGroup(
        id = editingId ?: 0,
        title = title,
        description = description,
        latitude = lat,
        longitude = lng,
        placeName = placeName,
        address = address,
        startDate = effectiveStart,
        endDate = effectiveEnd,
        isAllDay = isAllDay,
        markerHue = markerHue,
        markerSaturation = markerSaturation,
        markerBrightness = markerBrightness
    )

    /**
     * Writes the [prepared] media of group [groupId], replacing its old media when editing.
     * Returns the URIs the group no longer uses. Database work only: runs inside the save transaction.
     */
    private suspend fun saveMediaItems(dao: MemoryGroupDao, groupId: Int, prepared: List<MediaItem>, editingId: Int?): Set<String> {
        val previousUris = if (editingId != null) {
            dao.getGroupWithMedia(groupId)?.mediaItems?.mapTo(HashSet()) { it.uri }.orEmpty()
        } else {
            emptySet()
        }
        val previous = if (editingId != null) dao.getGroupWithMedia(groupId)?.mediaItems.orEmpty().associateBy { it.uri } else emptyMap()
        if (editingId != null) {
            dao.deleteMediaByGroupId(groupId)
        }
        // Items kept through an edit keep the details they were saved with: the form doesn't carry them.
        val mediaItems = prepared.map { item -> previous[item.uri]?.let { item.withDetailsOf(it) } ?: item }.map { it.copy(groupId = groupId) }
        dao.insertMediaItems(mediaItems)
        return previousUris - mediaItems.mapTo(HashSet()) { it.uri }
    }

    /** The prepared media with the details of the newly picked ones, and their thumbnails. */
    private class WithDetails(val items: List<MediaItem>, val thumbnails: List<MediaThumbnail>)

    /**
     * Reads the details (name, size in pixels, thumbnail, perceptual hash) of the media picked in this form: they can
     * be read now, through the pick's grant. Media loaded for editing keep theirs ([saveMediaItems]). Reads files, so it
     * runs before the save transaction.
     */
    private fun readNewDetails(context: Context, selected: List<AddMemoryGroupFragment.SelectedMedia>, prepared: List<MediaItem>): WithDetails {
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
    private fun prepareMediaItems(context: Context, mediaToSave: List<AddMemoryGroupFragment.SelectedMedia>): List<MediaItem> {
        return mediaToSave.mapIndexed { index, selected ->
            val uri = selected.uri
            var size = selected.fileSize
            var date = selected.dateTaken

            if (size == 0L) {
                val metadata = LocalMediaUtil.queryMetadata(context, uri)
                if (metadata != null) {
                    size = metadata.size
                    date = metadata.dateTaken
                } else {
                    Log.w("AddMemoryVM", "Could not read the size and date of $uri")
                }
            }

            // Keep a stored signature: it identifies the original file for re-linking, while the URI
            // of an item from another device may now resolve to an unrelated file.
            val signature = selected.mediaSignature.ifEmpty {
                LocalMediaUtil.signatureOrNull(context, uri).orEmpty().also {
                    if (it.isEmpty()) Log.w("AddMemoryVM", "Could not calculate signature for $uri (file missing or unreadable)")
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
                dateTaken = date,
                order = index + 1
            )
        }
            .distinctBy { it.mediaSignature.ifEmpty { it.uri } }
            .mapIndexed { index, item -> item.copy(order = index + 1) }
    }

    private suspend fun saveMemoryFragments(dao: MemoryGroupDao, groupId: Long, fragmentsToSave: List<MemoryFragmentEditAdapter.FragmentEditState>) {
        dao.deleteFragmentsByGroupId(groupId.toInt())
        val fragmentEntities = fragmentsToSave.mapIndexed { index, f ->
            val saveTime = f.isTimeVisible
            val fragmentStart = if (saveTime) {
                if (f.isAllDay) f.startDate?.toLocalDate()?.atStartOfDay(ZoneId.systemDefault()) else f.startDate
            } else {
                null
            }
            val fragmentEnd = if (saveTime) {
                if (f.isAllDay) f.endDate?.toLocalDate()?.atTime(LocalTime.MAX)?.atZone(ZoneId.systemDefault()) else f.endDate
            } else {
                null
            }

            MemoryFragment(
                groupId = groupId.toInt(),
                latitude = f.lat!!,
                longitude = f.lng!!,
                placeName = f.placeName,
                address = f.address,
                startDate = fragmentStart,
                endDate = fragmentEnd,
                isAllDay = f.isAllDay && saveTime,
                markerHue = f.markerHue,
                markerSaturation = f.markerSaturation,
                markerBrightness = f.markerBrightness,
                order = index + 1
            )
        }
        dao.insertFragments(fragmentEntities)
    }

    private companion object {
        const val FORM_STATE = "add_memory_form"
        const val FORM = "form"
        const val UNCHANGED_FORM = "unchanged_form"
        const val EDITING_MEMORY_ID = "editing_memory_id"
        const val PENDING_EDIT_MEMORY_ID = "pending_edit_memory_id"
        const val ACTIVE_PICKING_INDEX = "active_picking_index"
        const val PICKED_URIS = "picked_uris"
        const val FRAGMENTS_EXPANDED = "fragments_expanded"
        const val COLOR_EXPANDED = "color_expanded"
        const val DATE_EXPANDED = "date_expanded"
    }
}
