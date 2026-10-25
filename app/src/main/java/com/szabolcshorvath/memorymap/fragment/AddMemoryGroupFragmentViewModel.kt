package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupDao
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.PerfUtil.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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

    var editingMemoryId: Int? = null
    var activePickingIndex: Int = -1 // -1 for main, 0+ for fragments
    var currentDeviceId: String? = null
    var fragmentsExpanded = true
    var colorExpanded = false
    var dateExpanded = true

    var isInitialized = false

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
                // File reads (sizes, signatures, possibly a cloud download) happen before the transaction,
                // so it only writes and doesn't block other writers, such as a backup snapshot, meanwhile.
                val preparedMedia = prepareMediaItems(getApplication(), snapshotMedia)
                val dao = database.memoryGroupDao()

                var removedUris: Set<String> = emptySet()
                val groupIdResult = database.withTransaction("save_memory_group") {
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
                // In the background: saving no longer waits for the upload.
                AutomaticBackups.schedule(getApplication())
                _saveResult.send(SaveResult.Success(groupIdResult))
            } catch (e: Exception) {
                Log.e("AddMemoryVM", "Error saving memory group", e)
                _saveResult.send(SaveResult.Error(e.localizedMessage ?: "Unknown error"))
            } finally {
                _isSaving.value = false
            }
        }
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
