package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.CommonRepository
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.szabolcshorvath.memorymap.data.MemoryFragment as MemoryFragmentEntity

class MemoryFragmentViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val commonRepository = CommonRepository.getInstance(application)

    val groupWithMedia: StateFlow<MemoryGroupWithMedia?> = commonRepository.getGroupWithMedia(savedStateHandle.get<Int>(ARG_MEMORY_ID) ?: -1)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), null)

    var isFragmentsExpanded: Boolean = true
    var currentDeviceId: String? = null
    var mediaItems: List<MediaItem> = emptyList()
    var fragmentItems: List<MemoryFragmentEntity> = emptyList()

    private var mediaOrderJob: Job? = null
    private var fragmentsOrderJob: Job? = null

    fun saveNewMediaOrder(items: List<MediaItem>, database: MemoryMapDatabase, backupManager: BackupManager) {
        mediaOrderJob?.cancel()
        val updatedItems = items.mapIndexed { index, item ->
            item.copy(order = index + 1)
        }
        mediaOrderJob = viewModelScope.launch(Dispatchers.IO) {
            database.memoryGroupDao().updateMediaItems(updatedItems)
            backupManager.triggerAutomaticBackup(database)
        }
    }

    fun saveNewFragmentsOrder(fragments: List<MemoryFragmentEntity>, database: MemoryMapDatabase, backupManager: BackupManager) {
        fragmentsOrderJob?.cancel()
        val updatedFragments = fragments.mapIndexed { index, item ->
            item.copy(order = index + 1)
        }
        fragmentsOrderJob = viewModelScope.launch(Dispatchers.IO) {
            database.memoryGroupDao().updateFragments(updatedFragments)
            backupManager.triggerAutomaticBackup(database)
        }
    }

    companion object {
        const val ARG_MEMORY_ID = "memory_id"
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L
    }
}
