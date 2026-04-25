package com.szabolcshorvath.memorymap.fragment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.szabolcshorvath.memorymap.data.MemoryFragment as MemoryFragmentEntity

class MemoryFragmentViewModel : ViewModel() {
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
}
