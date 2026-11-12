package com.szabolcshorvath.memorymap.ui.settings

import android.app.Application
import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.auth.DriveAccessNeededException
import com.szabolcshorvath.memorymap.backup.AutomaticBackupStatus
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.backup.DriveProblem
import com.szabolcshorvath.memorymap.backup.RestoreRejectedException
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.data.MemoryRepository
import com.szabolcshorvath.memorymap.data.PresetRepository
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.MediaRelinker
import com.szabolcshorvath.memorymap.util.PermissionUtil
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.api.services.drive.model.File as DriveFile

/** A backup in the list: Drive's file [id] and [name], when it was last changed and its size in bytes. */
data class BackupItem(val id: String, val name: String, val modifiedMillis: Long?, val size: Long)

/** The line under "Back up now" that says how the last backup, restore or delete went. */
data class OperationResult(val message: String, val isError: Boolean)

/**
 * Something runs that the screen waits for (a backup, a sign-in, a re-link): its buttons are off meanwhile, and a card
 * at the bottom shows [status], if any.
 */
data class Busy(val status: String?)

data class SettingsUiState(
    val map: MapPreferences = MapPreferences(),
    val colorPresets: ColorPresetsUiState = ColorPresetsUiState(),
    /** The signed-in account, or null. */
    val email: String? = null,
    val backups: List<BackupItem> = emptyList(),
    /** Why the backup list couldn't be loaded the last time, or null; the list shown before stays. */
    val backupListProblem: DriveProblem? = null,
    /** The backup list is being pulled down to refresh it. */
    val refreshing: Boolean = false,
    val busy: Busy? = null,
    val result: OperationResult? = null,
    val automaticBackup: AutomaticBackupStatus = AutomaticBackupStatus.IDLE,
)

/** What Settings asks the route to show or do once: a short message, or the offer of full media access. */
sealed interface SettingsEvent {
    data class Message(@StringRes val text: Int) : SettingsEvent

    /** After a restore, some photos are still not linked and the app lacks full media access. */
    data object OfferMoreMediaAccess : SettingsEvent
}

/**
 * Settings' state ([uiState]) and what the user does on it. What needs an activity (signing in, Drive's consent screen,
 * permission requests, dialogs, sharing the logs) stays with [SettingsFragment], which reports it here.
 */
class SettingsViewModel(
    application: Application,
    presetRepository: PresetRepository,
    private val memories: MemoryRepository,
    private val database: MemoryMapDatabase,
    private val backupManager: BackupManager,
) : AndroidViewModel(application) {

    private val state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /** The colour presets: their own part of the screen. */
    val colorPresets = ColorPresetsModel(viewModelScope, presetRepository) { eventChannel.send(SettingsEvent.Message(it)) }

    /** The map preferences: their own part of the screen. */
    val mapPreferences = MapPreferencesModel(viewModelScope, application.dataStore)

    private val app get() = getApplication<Application>()

    private var backupsLoadedForEmail: String? = null

    /** Why the backup list last failed to load, kept for the consent screen it may carry. */
    private var backupListFailure: Throwable? = null

    /** Whether a Drive authorization under way is for a backup (else for loading the list). */
    var isBackupRequested: Boolean = false

    init {
        viewModelScope.launch { mapPreferences.state.collect { map -> state.update { it.copy(map = map) } } }
        viewModelScope.launch { colorPresets.state.collect { presets -> state.update { it.copy(colorPresets = presets) } } }
        viewModelScope.launch {
            app.dataStore.data.map { it[PreferencesKeys.USER_EMAIL_KEY] }.distinctUntilChanged().collect(::onAccount)
        }
        viewModelScope.launch {
            var wasRunning = false
            AutomaticBackups.status(app).collect { status ->
                state.update { it.copy(automaticBackup = status) }
                // A backup just finished: show it in the list, without blocking the screen.
                if (wasRunning && status != AutomaticBackupStatus.RUNNING) state.value.email?.let { loadBackups(it, quiet = true) }
                wasRunning = status == AutomaticBackupStatus.RUNNING
            }
        }
    }

    // Busy state and results

    /** Turns the screen's buttons off and shows [status]; [hideResult] hides the last result line meanwhile. */
    fun startBusy(status: String?, hideResult: Boolean = true) {
        state.update { it.copy(busy = Busy(status), result = if (hideResult) null else it.result) }
    }

    fun endBusy() = state.update { it.copy(busy = null) }

    private fun showResult(message: String, isError: Boolean) =
        state.update { it.copy(result = OperationResult(message, isError)) }

    // Account

    private fun onAccount(email: String?) {
        state.update { it.copy(email = email) }
        if (email == null) {
            backupsLoadedForEmail = null
            backupListFailure = null
            state.update { it.copy(backups = emptyList(), backupListProblem = null) }
        } else if (backupsLoadedForEmail != email) {
            loadBackups(email)
        }
    }

    /** The user signed in as [email]. */
    suspend fun signedIn(email: String) {
        app.dataStore.edit { it[PreferencesKeys.USER_EMAIL_KEY] = email }
    }

    /** The user signed out. */
    suspend fun signedOut() {
        backupsLoadedForEmail = null
        app.dataStore.edit { it.remove(PreferencesKeys.USER_EMAIL_KEY) }
    }

    /** Drive access was granted: back up, if that is what it was asked for, else load the list. */
    fun onDriveAuthorized() {
        val email = state.value.email
        if (email == null) {
            isBackupRequested = false
            endBusy()
            return
        }
        if (isBackupRequested) {
            isBackupRequested = false
            performBackup(email)
        } else {
            loadBackups(email)
        }
    }

    /** The consent screen that the last failed list call came with, if any. */
    fun driveConsent(): IntentSender? = (backupListFailure as? DriveAccessNeededException)?.consent

    // Backups

    fun refreshBackups() {
        val email = state.value.email ?: return
        loadBackups(email, refreshing = true)
    }

    /** [quiet]: without the busy card, for example after an automatic backup; [refreshing]: pulled down. */
    private fun loadBackups(email: String, quiet: Boolean = false, refreshing: Boolean = false) {
        viewModelScope.launch {
            if (refreshing) state.update { it.copy(refreshing = true) }
            if (!quiet) startBusy(app.getString(R.string.loading_backups), hideResult = false)
            try {
                backupManager.listBackups(email).fold(
                    onSuccess = { files ->
                        backupsLoadedForEmail = email
                        backupListFailure = null
                        state.update { s -> s.copy(backups = files.map(DriveFile::toBackupItem), backupListProblem = null) }
                    },
                    // The list shown before stays: an empty one would look as if the backups were gone.
                    onFailure = { e ->
                        backupListFailure = e
                        state.update { it.copy(backupListProblem = DriveProblem.of(e)) }
                    },
                )
            } finally {
                if (!quiet) endBusy()
                state.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun performBackup(email: String) {
        viewModelScope.launch {
            startBusy(app.getString(R.string.backup_starting))
            val result = backupManager.performBackup(email, database, isAutomatic = false) { status -> startBusy(status) }
            endBusy()
            result.fold(
                onSuccess = {
                    showResult(app.getString(R.string.backup_succeeded), isError = false)
                    loadBackups(email)
                },
                onFailure = { e -> showError(failureMessage(R.string.backup_failed, e), e) },
            )
        }
    }

    fun restoreBackup(fileId: String) {
        val email = state.value.email ?: return
        viewModelScope.launch {
            startBusy(app.getString(R.string.restore_starting))
            val result = backupManager.restoreBackup(email, fileId, database) { status -> startBusy(status) }
            endBusy()
            result.fold(
                onSuccess = {
                    showResult(app.getString(R.string.restore_succeeded), isError = false)
                    relinkAfterRestore()
                    loadBackups(email)
                },
                onFailure = { e ->
                    // A rejected backup says why itself, for the user (too new, unreadable), and is no Drive problem.
                    val message = if (e is RestoreRejectedException) {
                        app.getString(R.string.operation_failed, app.getString(R.string.restore_failed), e.message)
                    } else {
                        failureMessage(R.string.restore_failed, e)
                    }
                    showError(message, e)
                },
            )
        }
    }

    /**
     * The restore replaced the database contents in place: re-links its media to this device, with whatever media
     * access was granted. The result line stays meanwhile, so the user still sees that the restore worked. Then offers
     * full access if some photos are still not linked.
     */
    private fun relinkAfterRestore() {
        viewModelScope.launch {
            startBusy(app.getString(R.string.relink_scanning), hideResult = false)
            try {
                MediaRelinker.relink(app) { status -> startBusy(status, hideResult = false) }
            } finally {
                endBusy()
            }
            if (!PermissionUtil.hasAllMediaPermissions(app) && hasMissingMedia()) {
                eventChannel.send(SettingsEvent.OfferMoreMediaAccess)
            }
        }
    }

    fun deleteBackup(fileId: String) {
        val email = state.value.email ?: return
        viewModelScope.launch {
            startBusy(app.getString(R.string.backup_deleting))
            val result = backupManager.deleteBackup(email, fileId)
            endBusy()
            result.fold(
                onSuccess = {
                    showResult(app.getString(R.string.backup_deleted), isError = false)
                    loadBackups(email)
                },
                onFailure = { e -> showError(failureMessage(R.string.delete_backup_failed, e), e) },
            )
        }
    }

    private fun showError(message: String, cause: Throwable) {
        showResult(message, isError = true)
        // The message points to "Allow access", which the backup list then offers.
        if (DriveProblem.of(cause) == DriveProblem.NEEDS_ACCESS) {
            backupListFailure = cause
            state.update { it.copy(backupListProblem = DriveProblem.NEEDS_ACCESS) }
        }
    }

    /**
     * What failed ([what], such as "Backup failed.") and why, in words the user can act on. The exception itself is
     * logged where it happened, for "Send logs".
     */
    private fun failureMessage(@StringRes what: Int, e: Throwable): String =
        app.getString(R.string.operation_failed, app.getString(what), app.getString(DriveProblem.of(e).reason))

    // Media

    /** Re-links the photos and videos (the Re-link button), showing the run's progress. */
    fun relink() {
        viewModelScope.launch {
            startBusy(app.getString(R.string.relink_scanning))
            try {
                MediaRelinker.relink(app) { status -> startBusy(status) }
            } finally {
                endBusy()
                eventChannel.send(SettingsEvent.Message(R.string.relink_complete))
            }
        }
    }

    /** Whether some saved photos or videos are not linked to a file on this phone. Reads MediaStore: may take a while. */
    private suspend fun hasMissingMedia(): Boolean {
        val items = memories.getAllMediaItems()
        return withContext(Dispatchers.IO) { LocalMediaUtil.hasMissingMedia(app, items) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val container = app.appContainer
                SettingsViewModel(app, container.presets, container.memories, container.database, container.backupManager)
            }
        }
    }
}

// `getSize()`: the size property resolves to `AbstractMap.size`, not the size of the file.
@Suppress("UsePropertyAccessSyntax")
private fun DriveFile.toBackupItem() = BackupItem(id = id, name = name, modifiedMillis = modifiedTime?.value, size = getSize() ?: 0L)
