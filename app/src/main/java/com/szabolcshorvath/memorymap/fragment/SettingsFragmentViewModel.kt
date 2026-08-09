package com.szabolcshorvath.memorymap.fragment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.google.api.services.drive.model.File as DriveFile

class SettingsFragmentViewModel : ViewModel() {

    sealed class BackupRestoreOperationResult {
        data class Success(val message: String? = null) : BackupRestoreOperationResult()
        data class RestoreSuccess(val message: String? = null) : BackupRestoreOperationResult()
        data class Error(val message: String) : BackupRestoreOperationResult()
    }

    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus = _operationStatus.asStateFlow()

    private val _backupRestoreOperationResult = Channel<BackupRestoreOperationResult>(Channel.BUFFERED)
    val backupRestoreOperationResult = _backupRestoreOperationResult.receiveAsFlow()

    var originalPreset: HSVPreset? = null
    var editingPreset: HSVPreset? = null
    var colorPresetsExpanded: Boolean = false
    var newlyAddedPresetId: Int? = null
    var backupsLoadedForEmail: String? = null
    var lastLoadedBackups: List<DriveFile> = emptyList()
    var isBackupRequested: Boolean = false
    var pendingRestoreFile: DriveFile? = null

    var isInitialized = false

    private var savePresetsOrderJob: Job? = null

    fun saveNewPresetsOrder(presets: List<HSVPreset>, database: MemoryMapDatabase, backupManager: BackupManager) {
        savePresetsOrderJob?.cancel()
        savePresetsOrderJob = viewModelScope.launch(Dispatchers.IO) {
            database.hsvPresetDao().updatePresets(presets)
            backupManager.triggerAutomaticBackup(database)
        }
    }

    fun performBackup(credential: GoogleAccountCredential, database: MemoryMapDatabase, backupManager: BackupManager) {
        viewModelScope.launch {
            _operationStatus.value = "Starting backup..."
            val result = backupManager.performBackup(credential, database, isAutomatic = false) { status ->
                _operationStatus.value = status
            }
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Success("Backup successful"))
                },
                onFailure = { e ->
                    val errorMsg = "Backup failed: ${e.javaClass.simpleName}${e.message?.let { " - $it" } ?: ""}"
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Error(errorMsg))
                }
            )
        }
    }

    fun restoreBackup(credential: GoogleAccountCredential, fileId: String, backupManager: BackupManager) {
        viewModelScope.launch {
            _operationStatus.value = "Starting restore..."
            val result = backupManager.restoreBackup(credential, fileId) { status ->
                _operationStatus.value = status
            }
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.RestoreSuccess("Restore successful"))
                },
                onFailure = { e ->
                    val errorMsg = "Restore failed: ${e.javaClass.simpleName}${e.message?.let { " - $it" } ?: ""}"
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Error(errorMsg))
                }
            )
        }
    }

    fun deleteBackup(credential: GoogleAccountCredential, fileId: String, backupManager: BackupManager) {
        viewModelScope.launch {
            _operationStatus.value = "Deleting backup..."
            val result = backupManager.deleteBackup(credential, fileId)
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Success("Backup deleted"))
                },
                onFailure = { e ->
                    val errorMsg = "Failed to delete backup: ${e.javaClass.simpleName}${e.message?.let { " - $it" } ?: ""}"
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Error(errorMsg))
                }
            )
        }
    }
}
