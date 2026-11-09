package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.common.api.ApiException
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.auth.DriveAuthorization
import com.szabolcshorvath.memorymap.auth.SignInNotConfiguredException
import com.szabolcshorvath.memorymap.ui.settings.ColorPresetActions
import com.szabolcshorvath.memorymap.ui.settings.SettingsActions
import com.szabolcshorvath.memorymap.ui.settings.SettingsContent
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.util.ConfirmDialog
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.LogManager
import com.szabolcshorvath.memorymap.util.PermissionUtil
import com.szabolcshorvath.memorymap.util.RelinkAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The Settings tab, built with Compose ([SettingsContent]). Its state is [SettingsFragmentViewModel]'s; this fragment
 * does what needs an activity: signing in, Drive's consent screen, permission requests, the questions asked before
 * something is deleted or replaced, and sharing the logs.
 */
class SettingsFragment : Fragment(), SettingsActions, ColorPresetActions {
    private val viewModel: SettingsFragmentViewModel by viewModels { SettingsFragmentViewModel.Factory }

    private val startAuthorizationIntent = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        try {
            val context = context ?: return@registerForActivityResult
            AppServices.accounts.checkDriveConsentResult(context, activityResult.data)
            viewModel.onDriveAuthorized()
        } catch (e: ApiException) {
            Log.e(TAG, "Authorization failed", e)
            viewModel.isBackupRequested = false
            viewModel.endBusy()
        }
    }

    private val relinkAction = RelinkAction(this) { viewModel.relink() }

    /** The backup to restore once media access was asked for, kept with the question's answer. */
    private var pendingRestoreId: String? = null

    private val restorePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Restoring the database needs no media access. Re-linking afterwards uses whatever was granted,
        // and offers to grant more if photos are still missing.
        pendingRestoreId?.let(viewModel::restoreBackup)
        pendingRestoreId = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRestoreId = savedInstanceState?.getString(PENDING_RESTORE)
        // The questions come back after a rotation or process death, so what they act on comes with the answer.
        ConfirmDialog.listen(this, DELETE_PRESET_REQUEST) { viewModel.colorPresets.delete(it.getInt(PRESET_ID)) }
        ConfirmDialog.listen(this, RESTORE_REQUEST) { extras ->
            val id = extras.getString(FILE_ID) ?: return@listen
            if (PermissionUtil.hasAllMediaPermissions(requireContext())) {
                viewModel.restoreBackup(id)
            } else {
                askForMediaAccessBeforeRestore(id, extras.getString(FILE_NAME))
            }
        }
        ConfirmDialog.listen(this, RESTORE_ACCESS_REQUEST) { extras ->
            pendingRestoreId = extras.getString(FILE_ID)
            restorePermissionLauncher.launch(PermissionUtil.getRequiredMediaPermissions())
        }
        ConfirmDialog.listen(this, MORE_ACCESS_REQUEST) { relinkAction.askForAccess() }
        ConfirmDialog.listen(this, DELETE_BACKUP_REQUEST) { extras -> extras.getString(FILE_ID)?.let(viewModel::deleteBackup) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SettingsEvent.Message -> Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
                        SettingsEvent.OfferMoreMediaAccess -> offerMoreMediaAccess()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PENDING_RESTORE, pendingRestoreId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose keeps its saved state (the scroll position) under the view's id.
            id = R.id.settings_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MemoryMapTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    SettingsContent(state, this@SettingsFragment, this@SettingsFragment)
                }
            }
        }

    // Map preferences and colour presets: the ViewModel's.

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
        val id = viewModel.colorPresets.state.value.selectedPresetId ?: return
        ConfirmDialog.show(
            childFragmentManager,
            DELETE_PRESET_REQUEST,
            title = getString(R.string.delete_preset),
            message = getString(R.string.delete_preset_message),
            confirm = getString(R.string.delete),
            cancel = getString(R.string.cancel),
            extras = bundleOf(PRESET_ID to id),
        )
    }

    // Account and backups

    override fun signIn() {
        viewModel.startBusy(getString(R.string.signing_in))
        lifecycleScope.launch {
            try {
                val email = AppServices.accounts.signIn(requireActivity())
                viewModel.signedIn(email)
                requestDriveAuthorization(isBackup = false, email = email)
                viewModel.endBusy()
            } catch (e: CancellationException) {
                viewModel.endBusy()
                throw e
            } catch (e: Exception) {
                viewModel.endBusy()
                when (e) {
                    is SignInNotConfiguredException -> {
                        Log.e(TAG, "Sign in is not configured", e)
                        toast(R.string.sign_in_not_configured)
                    }

                    // The user closed the account picker: nothing to tell them.
                    is GetCredentialCancellationException -> Log.i(TAG, "Sign in cancelled")

                    is NoCredentialException -> {
                        Log.w(TAG, "Sign in failed: no account", e)
                        toast(R.string.sign_in_no_account)
                    }

                    // The details are in the log, for "Send logs".
                    else -> {
                        Log.e(TAG, "Sign in failed", e)
                        toast(R.string.sign_in_failed)
                    }
                }
            }
        }
    }

    override fun signOut() {
        lifecycleScope.launch {
            AppServices.accounts.signOut(requireContext())
            viewModel.signedOut()
        }
    }

    override fun backUpNow() {
        viewModel.startBusy(getString(R.string.backup_starting))
        requestDriveAuthorization(isBackup = true)
    }

    override fun refreshBackups() = viewModel.refreshBackups()

    /**
     * Asks for Drive access again, then reloads the list: on the consent screen the failed call came with, or else as
     * the app usually does (which may find that access was granted meanwhile, or show the consent screen itself).
     */
    override fun allowDriveAccess() {
        val consent = viewModel.driveConsent()
        if (consent != null) {
            viewModel.isBackupRequested = false
            startAuthorizationIntent.launch(IntentSenderRequest.Builder(consent).build())
        } else {
            requestDriveAuthorization(isBackup = false)
        }
    }

    /** [email]: the account to authorize, the signed-in one unless the user just signed in. */
    private fun requestDriveAuthorization(isBackup: Boolean, email: String? = viewModel.uiState.value.email) {
        val context = context
        if (context == null || email == null) {
            viewModel.endBusy()
            return
        }
        viewModel.isBackupRequested = isBackup
        lifecycleScope.launch {
            try {
                when (val authorization = AppServices.accounts.authorizeDrive(context, email)) {
                    DriveAuthorization.Granted -> viewModel.onDriveAuthorized()

                    is DriveAuthorization.NeedsConsent -> startAuthorizationIntent.launch(
                        IntentSenderRequest.Builder(authorization.intentSender).build()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to authorize", e)
                viewModel.isBackupRequested = false
                viewModel.endBusy()
            }
        }
    }

    override fun restoreBackup(backup: BackupItem) {
        ConfirmDialog.show(
            childFragmentManager,
            RESTORE_REQUEST,
            title = getString(R.string.restore_backup_title),
            message = getString(R.string.restore_backup_message, backup.name),
            confirm = getString(R.string.restore),
            cancel = getString(R.string.cancel),
            extras = bundleOf(FILE_ID to backup.id, FILE_NAME to backup.name),
        )
    }

    private fun askForMediaAccessBeforeRestore(id: String, name: String?) {
        ConfirmDialog.show(
            childFragmentManager,
            RESTORE_ACCESS_REQUEST,
            title = getString(R.string.restore_media_access_title),
            message = getString(R.string.restore_media_access_message),
            confirm = getString(R.string.restore_media_access_continue),
            cancel = getString(android.R.string.cancel),
            extras = bundleOf(FILE_ID to id, FILE_NAME to name),
        )
    }

    /** After a restore with partial or no media access, offers full access; granting it runs the re-link again. */
    private fun offerMoreMediaAccess() {
        if (isStateSaved) return
        ConfirmDialog.show(
            childFragmentManager,
            MORE_ACCESS_REQUEST,
            title = getString(R.string.relink_more_title),
            message = getString(R.string.relink_more_message),
            confirm = getString(R.string.relink_more_allow),
            cancel = getString(R.string.relink_more_not_now),
        )
    }

    override fun deleteBackup(backup: BackupItem) {
        ConfirmDialog.show(
            childFragmentManager,
            DELETE_BACKUP_REQUEST,
            title = getString(R.string.delete_backup_title),
            message = getString(R.string.delete_backup_message, backup.name),
            confirm = getString(R.string.delete_backup_confirm),
            cancel = getString(android.R.string.cancel),
            extras = bundleOf(FILE_ID to backup.id, FILE_NAME to backup.name),
        )
    }

    // Media library

    override fun relinkMedia() = relinkAction.start()

    override fun sendLogs() {
        val context = context ?: return
        viewModel.startBusy(getString(R.string.preparing_logs))
        lifecycleScope.launch {
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

    private fun toast(text: Int) {
        context?.let { Toast.makeText(it, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        const val TAG = "SettingsFragment"
        private const val DELETE_PRESET_REQUEST = "delete_preset"
        private const val RESTORE_REQUEST = "restore_backup"
        private const val RESTORE_ACCESS_REQUEST = "restore_media_access"
        private const val MORE_ACCESS_REQUEST = "more_media_access"
        private const val DELETE_BACKUP_REQUEST = "delete_backup"
        private const val PRESET_ID = "preset_id"
        private const val FILE_ID = "file_id"
        private const val FILE_NAME = "file_name"
        private const val PENDING_RESTORE = "pending_restore"
    }
}
