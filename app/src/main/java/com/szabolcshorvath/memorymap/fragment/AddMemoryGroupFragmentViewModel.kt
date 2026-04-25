package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupDao
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.MediaHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AddMemoryGroupFragmentViewModel(application: Application) : AndroidViewModel(application) {

    sealed class SaveResult {
        data class Success(val groupId: Int) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    private val _saveResult = MutableSharedFlow<SaveResult>()
    val saveResult = _saveResult.asSharedFlow()

    var selectedMedia = mutableListOf<AddMemoryGroupFragment.SelectedMedia>()
    var fragments = mutableListOf<MemoryFragmentEditAdapter.FragmentEditState>()

    var lat = 0.0
    var lng = 0.0
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

    fun saveMemoryGroup(
        title: String,
        description: String?,
        database: MemoryMapDatabase,
        backupManager: BackupManager
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val effectiveStart = calculateEffectiveStartTime()
                val effectiveEnd = calculateEffectiveEndTime()
                val group = assembleMemoryGroup(title, description, effectiveStart, effectiveEnd)
                val dao = database.memoryGroupDao()

                val groupIdResult = database.withTransaction {
                    val groupId = if (editingMemoryId != null) {
                        dao.updateGroup(group)
                        editingMemoryId!!.toLong()
                    } else {
                        dao.insertGroup(group)
                    }

                    saveMediaItems(dao, groupId, getApplication())
                    saveMemoryFragments(dao, groupId)
                    groupId.toInt()
                }

                backupManager.triggerAutomaticBackup(database)
                _saveResult.emit(SaveResult.Success(groupIdResult))
            } catch (e: Exception) {
                Log.e("AddMemoryVM", "Error saving memory group", e)
                _saveResult.emit(SaveResult.Error(e.localizedMessage ?: "Unknown error"))
            }
        }
    }

    private fun calculateEffectiveStartTime(): ZonedDateTime = if (isAllDay) {
        startDateTime.toLocalDate().atStartOfDay(ZoneId.systemDefault())
    } else {
        startDateTime
    }

    private fun calculateEffectiveEndTime(): ZonedDateTime = if (isAllDay) {
        endDateTime.toLocalDate()
            .atTime(LocalTime.MAX)
            .atZone(ZoneId.systemDefault())
    } else {
        endDateTime
    }

    private fun assembleMemoryGroup(title: String, description: String?, effectiveStart: ZonedDateTime, effectiveEnd: ZonedDateTime): MemoryGroup = MemoryGroup(
        id = editingMemoryId ?: 0,
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

    private suspend fun saveMediaItems(dao: MemoryGroupDao, groupId: Long, context: Context) {
        if (editingMemoryId != null) {
            dao.deleteMediaByGroupId(groupId.toInt())
        }

        val mediaItems = selectedMedia.mapIndexed { index, (uri, type, itemDeviceId) ->
            var size = 0L
            var date = 0L

            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                    date = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
                }
            }

            MediaItem(
                groupId = groupId.toInt(),
                uri = uri.toString(),
                deviceId = itemDeviceId,
                type = type,
                mediaSignature = MediaHasher.calculateMediaSignature(context, uri),
                fileSize = size,
                dateTaken = date,
                order = index + 1
            )
        }
            .distinctBy { it.mediaSignature }
            .mapIndexed { index, item -> item.copy(order = index + 1) }

        dao.insertMediaItems(mediaItems)
    }

    private suspend fun saveMemoryFragments(dao: MemoryGroupDao, groupId: Long) {
        dao.deleteFragmentsByGroupId(groupId.toInt())
        val fragmentEntities = fragments.mapIndexed { index, f ->
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
                latitude = f.latitude,
                longitude = f.longitude,
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
