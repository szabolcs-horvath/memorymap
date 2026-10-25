package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupDao
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
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

class AddMemoryGroupFragmentViewModel(application: Application) : AndroidViewModel(application) {

    sealed class SaveResult {
        data class Success(val groupId: Int) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    private val _saveResult = Channel<SaveResult>(Channel.BUFFERED)
    val saveResult = _saveResult.receiveAsFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    var selectedMedia = mutableListOf<AddMemoryGroupFragment.SelectedMedia>()
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

    var isInitialized = false

    /** Whether the Add screen is shown (not hidden behind another tab); kept up to date by the fragment. */
    var isScreenShown = true

    /** The form's content when it was last cleared or loaded for editing. Declared after the fields it reads. */
    private var unchangedContent = currentContent()

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
                    val dao = database.memoryGroupDao()

                    var removedUris: Set<String> = emptySet()
                    val savedId = database.withTransaction("save_memory_group") {
                        val groupId = if (snapshotEditingId != null) {
                            dao.updateGroup(group)
                            snapshotEditingId.toLong()
                        } else {
                            dao.insertGroup(group)
                        }

                        removedUris = saveMediaItems(dao, groupId.toInt(), preparedMedia, snapshotEditingId)
                        saveMemoryFragments(dao, groupId, snapshotFragments)
                        groupId.toInt()
                    }

                    LocalMediaUtil.releaseUriGrantsIfUnused(getApplication(), dao, removedUris)
                    savedId
                }
                // In the background: saving no longer waits for the upload.
                AutomaticBackups.schedule(getApplication())
                withContext(Dispatchers.Main) { onSaved(groupIdResult) }
            } catch (e: Exception) {
                Log.e("AddMemoryVM", "Error saving memory group", e)
                _saveResult.send(SaveResult.Error(e.localizedMessage ?: "Unknown error"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Clears the form here, so it is empty whenever the screen is shown next. Opens the saved memory only
     * if the Add screen is on screen: a save that finishes after the user left must not jump there later.
     */
    private suspend fun onSaved(groupId: Int) {
        resetForm()
        if (isScreenShown) {
            _saveResult.send(SaveResult.Success(groupId))
        } else {
            Toast.makeText(getApplication(), R.string.memory_saved, Toast.LENGTH_SHORT).show()
        }
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

    /** What a save would store, to compare the form with how it was. */
    private data class FormContent(
        val title: String?,
        val description: String?,
        val lat: Double?,
        val lng: Double?,
        val placeName: String?,
        val address: String?,
        val startDateTime: ZonedDateTime,
        val endDateTime: ZonedDateTime,
        val isAllDay: Boolean,
        val markerHue: Float,
        val markerSaturation: Float,
        val markerBrightness: Float,
        val media: List<AddMemoryGroupFragment.SelectedMedia>,
        val fragments: List<MemoryFragmentEditAdapter.FragmentEditState>
    )

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
            data.mediaItems.sortedWith(MediaItemComparator()).map {
                AddMemoryGroupFragment.SelectedMedia(it.uri.toUri(), it.type, it.deviceId, it.fileSize, it.dateTaken, it.mediaSignature)
            }
        )

        fragments.clear()
        fragments.addAll(
            data.fragments.sortedWith(MemoryFragmentComparator()).map {
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
        if (editingId != null) {
            dao.deleteMediaByGroupId(groupId)
        }
        val mediaItems = prepared.map { it.copy(groupId = groupId) }
        dao.insertMediaItems(mediaItems)
        return previousUris - mediaItems.mapTo(HashSet()) { it.uri }
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
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                            val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                            if (sizeCol != -1) size = cursor.getLong(sizeCol)
                            if (dateCol != -1) date = cursor.getLong(dateCol)
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("AddMemoryVM", "Error querying media store info for $uri", e)
                } catch (e: java.io.IOException) {
                    Log.e("AddMemoryVM", "Error querying media store info for $uri", e)
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
}

private class MediaItemComparator : Comparator<MediaItem> {
    override fun compare(a: MediaItem, b: MediaItem): Int {
        // Sort media items based on order or dateTaken
        return when {
            a.order != null && b.order != null -> a.order.compareTo(b.order)
            a.order != null -> -1
            b.order != null -> 1
            else -> b.dateTaken.compareTo(a.dateTaken)
        }
    }
}

private class MemoryFragmentComparator : Comparator<MemoryFragment> {
    override fun compare(a: MemoryFragment, b: MemoryFragment): Int {
        // Sort fragments based on order or startDate
        return when {
            a.order != null && b.order != null -> a.order.compareTo(b.order)
            a.order != null -> -1
            b.order != null -> 1
            else -> {
                val dateA = a.startDate
                val dateB = b.startDate
                when {
                    dateA != null && dateB != null -> dateA.compareTo(dateB)
                    dateA != null -> -1
                    dateB != null -> 1
                    else -> 0
                }
            }
        }
    }
}
