package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.backup.DriveProblem
import com.szabolcshorvath.memorymap.backup.RestoreRejectedException
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

class SettingsFragmentViewModel(application: Application) : AndroidViewModel(application) {

    sealed class BackupRestoreOperationResult {
        data class Success(val message: String? = null) : BackupRestoreOperationResult()
        data class RestoreSuccess(val message: String? = null) : BackupRestoreOperationResult()

        /** [cause]: what failed, for Settings to offer Drive access when that is what is missing. */
        data class Error(val message: String, val cause: Throwable? = null) : BackupRestoreOperationResult()
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

    /** Why the backup list last failed to load, or null; the list shown before stays. */
    var backupListFailure: Throwable? = null
    var isBackupRequested: Boolean = false
    var pendingRestoreFile: DriveFile? = null

    var isInitialized = false

    private var savePresetsOrderJob: Job? = null

    fun saveNewPresetsOrder(presets: List<HSVPreset>, database: MemoryMapDatabase) {
        savePresetsOrderJob?.cancel()
        savePresetsOrderJob = viewModelScope.launch(Dispatchers.IO) {
            database.hsvPresetDao().updatePresets(presets)
            AutomaticBackups.schedule(getApplication())
        }
    }

    fun performBackup(email: String, database: MemoryMapDatabase, backupManager: BackupManager) {
        viewModelScope.launch {
            _operationStatus.value = "Starting backup..."
            val result = backupManager.performBackup(email, database, isAutomatic = false) { status ->
                _operationStatus.value = status
            }
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Success("Backup successful"))
                },
                onFailure = { e ->
                    val errorMsg = failureMessage(R.string.backup_failed, e)
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Error(errorMsg, e))
                }
            )
        }
    }

    fun restoreBackup(email: String, fileId: String, database: MemoryMapDatabase, backupManager: BackupManager) {
        viewModelScope.launch {
            _operationStatus.value = "Starting restore..."
            val result = backupManager.restoreBackup(email, fileId, database) { status ->
                _operationStatus.value = status
            }
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.RestoreSuccess("Restore successful"))
                },
                onFailure = { e ->
                    // A rejected backup says why itself, for the user (too new, unreadable), and is no Drive problem.
                    val errorMsg = if (e is RestoreRejectedException) {
                        app.getString(R.string.operation_failed, app.getString(R.string.restore_failed), e.message)
                    } else {
                        failureMessage(R.string.restore_failed, e)
                    }
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Error(errorMsg, e))
                }
            )
        }
    }

    fun deleteBackup(email: String, fileId: String, backupManager: BackupManager) {
        viewModelScope.launch {
            _operationStatus.value = "Deleting backup..."
            val result = backupManager.deleteBackup(email, fileId)
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Success("Backup deleted"))
                },
                onFailure = { e ->
                    val errorMsg = failureMessage(R.string.delete_backup_failed, e)
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Error(errorMsg, e))
                }
            )
        }
    }

    private val app get() = getApplication<Application>()

    /**
     * What failed ([what], such as "Backup failed.") and why, in words the user can act on. The exception itself is
     * logged where it happened, for "Send logs".
     */
    private fun failureMessage(@StringRes what: Int, e: Throwable): String =
        app.getString(R.string.operation_failed, app.getString(what), app.getString(DriveProblem.of(e).reason))
}
