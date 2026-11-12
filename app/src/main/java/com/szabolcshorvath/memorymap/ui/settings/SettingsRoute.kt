package com.szabolcshorvath.memorymap.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.color.Hsv
import com.szabolcshorvath.memorymap.ui.common.ConfirmDialog
import com.szabolcshorvath.memorymap.ui.common.RelinkRequester
import com.szabolcshorvath.memorymap.ui.common.rememberRelinkRequester
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.LogManager
import com.szabolcshorvath.memorymap.util.PermissionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A question Settings asks before something is deleted or replaced, with what it acts on. */
private sealed interface Question {
    data class DeletePreset(val id: Int) : Question
    data class Restore(val id: String, val name: String) : Question

    /** Restoring without full media access: the photos may not show until it is allowed. */
    data class RestoreAccess(val id: String) : Question
    data object MoreAccess : Question
    data class DeleteBackup(val id: String, val name: String) : Question
}

/** Keeps the open question, and what it acts on, for a rotation and process death. */
private val QuestionSaver = Saver<MutableState<Question?>, List<String>>(
    save = { state ->
        when (val question = state.value) {
            null -> null
            is Question.DeletePreset -> listOf("delete_preset", question.id.toString())
            is Question.Restore -> listOf("restore", question.id, question.name)
            is Question.RestoreAccess -> listOf("restore_access", question.id)
            Question.MoreAccess -> listOf("more_access")
            is Question.DeleteBackup -> listOf("delete_backup", question.id, question.name)
        }
    },
    restore = { saved ->
        val question = when (saved.first()) {
            "delete_preset" -> Question.DeletePreset(saved[1].toInt())
            "restore" -> Question.Restore(saved[1], saved[2])
            "restore_access" -> Question.RestoreAccess(saved[1])
            "more_access" -> Question.MoreAccess
            "delete_backup" -> Question.DeleteBackup(saved[1], saved[2])
            else -> null
        }
        mutableStateOf(question)
    },
)

/**
 * Settings with its ViewModel: the screen, signing in and Drive access ([AccountFlow]), permission requests, the
 * questions asked before something is deleted or replaced, and sharing the logs.
 */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accounts = rememberAccountFlow(viewModel)
    val relink = rememberRelinkRequester(viewModel::relink)
    val scope = rememberCoroutineScope()
    var question by rememberSaveable(saver = QuestionSaver) { mutableStateOf<Question?>(null) }
    // The backup to restore once media access was asked for.
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        // Restoring the database needs no media access. Re-linking afterwards uses whatever was granted,
        // and offers to grant more if photos are still missing.
        pendingRestore?.let(viewModel::restoreBackup)
        pendingRestore = null
    }
    val actions = remember(viewModel, accounts, relink, scope) {
        settingsActions(viewModel, accounts, relink, ask = { question = it }) { sendLogs(context, viewModel, scope) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is SettingsEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                    SettingsEvent.OfferMoreMediaAccess -> question = Question.MoreAccess
                }
            }
        }
    }

    SettingsContent(state, actions, actions)
    question?.let { open ->
        QuestionDialog(open, onClose = { question = null }) {
            question = null
            when (open) {
                is Question.DeletePreset -> viewModel.colorPresets.delete(open.id)
                is Question.Restore ->
                    if (PermissionUtil.hasAllMediaPermissions(context)) {
                        viewModel.restoreBackup(open.id)
                    } else {
                        question = Question.RestoreAccess(open.id)
                    }
                is Question.RestoreAccess -> {
                    pendingRestore = open.id
                    restoreAccess.launch(PermissionUtil.getRequiredMediaPermissions())
                }
                Question.MoreAccess -> relink.askForAccess()
                is Question.DeleteBackup -> viewModel.deleteBackup(open.id)
            }
        }
    }
}

/** [question] as a dialog: [onConfirm] when the user agrees, [onClose] when they don't. */
@Composable
private fun QuestionDialog(question: Question, onClose: () -> Unit, onConfirm: () -> Unit) {
    val (title, message) = when (question) {
        is Question.DeletePreset -> stringResource(R.string.delete_preset) to stringResource(R.string.delete_preset_message)
        is Question.Restore ->
            stringResource(R.string.restore_backup_title) to stringResource(R.string.restore_backup_message, question.name)
        is Question.RestoreAccess ->
            stringResource(R.string.restore_media_access_title) to stringResource(R.string.restore_media_access_message)
        Question.MoreAccess -> stringResource(R.string.relink_more_title) to stringResource(R.string.relink_more_message)
        is Question.DeleteBackup ->
            stringResource(R.string.delete_backup_title) to stringResource(R.string.delete_backup_message, question.name)
    }
    val (confirm, cancel) = when (question) {
        is Question.DeletePreset -> R.string.delete to R.string.cancel
        is Question.Restore -> R.string.restore to R.string.cancel
        is Question.RestoreAccess -> R.string.restore_media_access_continue to android.R.string.cancel
        Question.MoreAccess -> R.string.relink_more_allow to R.string.relink_more_not_now
        is Question.DeleteBackup -> R.string.delete_backup_confirm to android.R.string.cancel
    }
    ConfirmDialog(title, message, stringResource(confirm), stringResource(cancel), onConfirm = onConfirm, onCancel = onClose)
}

/** Shares the logs ("Send logs"), with the date of the latest backup. */
private fun sendLogs(context: Context, viewModel: SettingsViewModel, scope: CoroutineScope) {
    viewModel.startBusy(context.getString(R.string.preparing_logs))
    scope.launch {
        try {
            // For the logs, not shown: a fixed format.
            val lastBackupDate = viewModel.uiState.value.backups.firstOrNull()?.modifiedMillis?.let { millis ->
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
                    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
            }
            LogManager.sendDebugLogs(context, lastBackupDate)
        } finally {
            viewModel.endBusy()
        }
    }
}

/** The screen's actions: map preferences and colour presets go to [viewModel]; what asks first goes to [ask]. */
private fun settingsActions(
    viewModel: SettingsViewModel,
    accounts: AccountFlow,
    relink: RelinkRequester,
    ask: (Question) -> Unit,
    shareLogs: () -> Unit,
) = object : SettingsActions, ColorPresetActions {
    override fun setShowFragmentMarkers(show: Boolean) = viewModel.mapPreferences.setShowFragmentMarkers(show)
    override fun setMarkerClustering(enabled: Boolean) = viewModel.mapPreferences.setMarkerClustering(enabled)
    override fun setDefaultFilter(option: DateFilterOption) = viewModel.mapPreferences.setDefaultFilter(option)
    override fun togglePresets() = viewModel.colorPresets.toggle()
    override fun selectPreset(id: Int) = viewModel.colorPresets.select(id)
    override fun setColor(color: Hsv) = viewModel.colorPresets.setColor(color)
    override fun addPreset() = viewModel.colorPresets.add()
    override fun reorderPresets(ids: List<Int>) = viewModel.colorPresets.reorder(ids)
    override fun onNewPresetShown() = viewModel.colorPresets.onNewPresetShown()
    override fun undoPresetChange() = viewModel.colorPresets.undo()
    override fun savePreset() = viewModel.colorPresets.save()
    override fun deletePreset() {
        viewModel.colorPresets.state.value.selectedPresetId?.let { ask(Question.DeletePreset(it)) }
    }
    override fun signIn() = accounts.signIn()
    override fun signOut() = accounts.signOut()
    override fun backUpNow() = accounts.backUpNow()
    override fun refreshBackups() = viewModel.refreshBackups()
    override fun allowDriveAccess() = accounts.allowDriveAccess()
    override fun restoreBackup(backup: BackupItem) = ask(Question.Restore(backup.id, backup.name))
    override fun deleteBackup(backup: BackupItem) = ask(Question.DeleteBackup(backup.id, backup.name))
    override fun relinkMedia() = relink.start()
    override fun sendLogs() = shareLogs()
}
