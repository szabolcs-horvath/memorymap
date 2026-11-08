package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.backup.DriveProblem
import com.szabolcshorvath.memorymap.backup.RestoreRejectedException
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.data.MemoryRepository
import com.szabolcshorvath.memorymap.data.PresetRepository
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.api.services.drive.model.File as DriveFile

class SettingsFragmentViewModel(
    application: Application,
    private val presets: PresetRepository,
    private val memories: MemoryRepository,
    private val database: MemoryMapDatabase,
    private val backupManager: BackupManager
) : AndroidViewModel(application) {

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

    /** Stores [ordered] as the presets' order. */
    fun savePresetsOrder(ordered: List<HSVPreset>) {
        savePresetsOrderJob?.cancel()
        savePresetsOrderJob = viewModelScope.launch { presets.saveOrder(ordered) }
    }

    /** Stores [preset], replacing the one with its id. */
    suspend fun savePreset(preset: HSVPreset) = presets.save(preset)

    /** Adds [preset] and returns its id, or null if it wasn't added. */
    suspend fun addPreset(preset: HSVPreset): Int? = presets.add(preset)

    suspend fun deletePreset(preset: HSVPreset) = presets.delete(preset)

    /** Whether some saved photos or videos are not linked to a file on this phone. Reads MediaStore: may take a while. */
    suspend fun hasMissingMedia(): Boolean {
        val items = memories.getAllMediaItems()
        return withContext(Dispatchers.IO) { LocalMediaUtil.hasMissingMedia(app, items) }
    }

    suspend fun listBackups(email: String): Result<List<DriveFile>> = backupManager.listBackups(email)

    fun performBackup(email: String) {
        viewModelScope.launch {
            _operationStatus.value = app.getString(R.string.backup_starting)
            val result = backupManager.performBackup(email, database, isAutomatic = false) { status ->
                _operationStatus.value = status
            }
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Success(app.getString(R.string.backup_succeeded)))
                },
                onFailure = { e ->
                    val errorMsg = failureMessage(R.string.backup_failed, e)
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Error(errorMsg, e))
                }
            )
        }
    }

    fun restoreBackup(email: String, fileId: String) {
        viewModelScope.launch {
            _operationStatus.value = app.getString(R.string.restore_starting)
            val result = backupManager.restoreBackup(email, fileId, database) { status ->
                _operationStatus.value = status
            }
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.RestoreSuccess(app.getString(R.string.restore_succeeded)))
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

    fun deleteBackup(email: String, fileId: String) {
        viewModelScope.launch {
            _operationStatus.value = app.getString(R.string.backup_deleting)
            val result = backupManager.deleteBackup(email, fileId)
            _operationStatus.value = null
            result.fold(
                onSuccess = {
                    _backupRestoreOperationResult.send(BackupRestoreOperationResult.Success(app.getString(R.string.backup_deleted)))
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val container = app.appContainer
                SettingsFragmentViewModel(app, container.presets, container.memories, container.database, container.backupManager)
            }
        }
    }
}
