package com.szabolcshorvath.memorymap.ui.settings

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.auth.DriveAuthorization
import com.szabolcshorvath.memorymap.auth.SignInNotConfiguredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Signing in and out, and Drive access, for Settings: what needs the activity (Credential Manager's account picker,
 * Drive's consent screen). The state they lead to is [viewModel]'s. Get it with [rememberAccountFlow].
 */
@Stable
class AccountFlow internal constructor(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val viewModel: SettingsViewModel,
    private val consentLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
) {
    // Credential Manager throws many kinds of exception: each is logged for "Send logs", and the user gets plain words.
    @Suppress("TooGenericExceptionCaught")
    fun signIn() {
        viewModel.startBusy(activity.getString(R.string.signing_in))
        scope.launch {
            try {
                val email = AppServices.accounts.signIn(activity)
                viewModel.signedIn(email)
                requestDriveAuthorization(isBackup = false, email = email)
                viewModel.endBusy()
            } catch (e: CancellationException) {
                viewModel.endBusy()
                throw e
            } catch (e: Exception) {
                viewModel.endBusy()
                signInFailed(e)
            }
        }
    }

    private fun signInFailed(e: Exception) {
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

            else -> {
                Log.e(TAG, "Sign in failed", e)
                toast(R.string.sign_in_failed)
            }
        }
    }

    fun signOut() {
        scope.launch {
            AppServices.accounts.signOut(activity)
            viewModel.signedOut()
        }
    }

    fun backUpNow() {
        viewModel.startBusy(activity.getString(R.string.backup_starting))
        requestDriveAuthorization(isBackup = true)
    }

    /**
     * Asks for Drive access again, then reloads the list: on the consent screen the failed call came with, or else as
     * the app usually does (which may find that access was granted meanwhile, or show the consent screen itself).
     */
    fun allowDriveAccess() {
        val consent = viewModel.driveConsent()
        if (consent != null) {
            viewModel.isBackupRequested = false
            consentLauncher.launch(IntentSenderRequest.Builder(consent).build())
        } else {
            requestDriveAuthorization(isBackup = false)
        }
    }

    /** [email]: the account to authorize, the signed-in one unless the user just signed in. */
    // Play services throws many kinds of exception: the cause is logged for "Send logs".
    @Suppress("TooGenericExceptionCaught")
    private fun requestDriveAuthorization(isBackup: Boolean, email: String? = viewModel.uiState.value.email) {
        if (email == null) {
            viewModel.endBusy()
            return
        }
        viewModel.isBackupRequested = isBackup
        scope.launch {
            try {
                when (val authorization = AppServices.accounts.authorizeDrive(activity, email)) {
                    DriveAuthorization.Granted -> viewModel.onDriveAuthorized()

                    is DriveAuthorization.NeedsConsent ->
                        consentLauncher.launch(IntentSenderRequest.Builder(authorization.intentSender).build())
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

    private fun toast(@StringRes text: Int) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    internal companion object {
        const val TAG = "AccountFlow"
    }
}

/** The [AccountFlow] of Settings' [viewModel]. */
@Composable
fun rememberAccountFlow(viewModel: SettingsViewModel): AccountFlow {
    val activity = checkNotNull(LocalActivity.current) { "Settings must be shown in an activity" }
    val scope = rememberCoroutineScope()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try {
            AppServices.accounts.checkDriveConsentResult(activity, result.data)
            viewModel.onDriveAuthorized()
        } catch (e: ApiException) {
            Log.e(AccountFlow.TAG, "Authorization failed", e)
            viewModel.isBackupRequested = false
            viewModel.endBusy()
        }
    }
    return remember(activity, scope, viewModel, consent) { AccountFlow(activity, scope, viewModel, consent) }
}
