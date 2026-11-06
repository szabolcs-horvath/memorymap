package com.szabolcshorvath.memorymap.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.drive.DriveScopes
import com.szabolcshorvath.memorymap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google sign-in (Credential Manager) and Drive consent (AuthorizationClient). */
object GoogleAccountService : AccountService {
    private const val TAG = "GoogleAccountService"

    override suspend fun signIn(activityContext: Context): String = withContext(Dispatchers.Main) {
        if (BuildConfig.OAUTH_WEB_CLIENT_ID.isBlank()) throw SignInNotConfiguredException()
        val googleIdOption = GetSignInWithGoogleOption.Builder(BuildConfig.OAUTH_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        val result = CredentialManager.create(activityContext).getCredential(request = request, context = activityContext)

        emailFrom(result)
    }

    private fun emailFrom(result: GetCredentialResponse): String {
        val credential = result.credential
        // Throw rather than return quietly, so the caller always gets a result or an error.
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            Log.e(TAG, "Unexpected credential type: ${credential.type}")
            error("Unexpected credential type: ${credential.type}")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).id
    }

    override suspend fun signOut(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    override suspend fun authorizeDrive(context: Context, email: String): DriveAuthorization = suspendCancellableCoroutine { continuation ->
        Identity.getAuthorizationClient(context).authorize(driveRequest(email))
            .addOnSuccessListener { result ->
                val pendingIntent = result.pendingIntent
                val authorization = if (result.hasResolution() && pendingIntent != null) {
                    DriveAuthorization.NeedsConsent(pendingIntent.intentSender)
                } else {
                    DriveAuthorization.Granted
                }
                continuation.resume(authorization)
            }
            .addOnFailureListener { e -> continuation.resumeWithException(e) }
    }

    override fun checkDriveConsentResult(context: Context, data: Intent?) {
        // Throws an ApiException when the user didn't allow access.
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
    }

    /**
     * An access token for [email]'s Drive, for the Drive calls. Blocks, so call it off the main thread. Play services
     * caches the token and renews it, and shows no screen: when the user has to allow access first, this throws
     * [DriveAccessNeededException] with the consent screen to show (a background backup can't show it).
     */
    fun driveAccessToken(context: Context, email: String): String {
        val result = try {
            Tasks.await(Identity.getAuthorizationClient(context).authorize(driveRequest(email)))
        } catch (e: ExecutionException) {
            throw authorizationFailure(e.cause ?: e)
        }
        val token = result.accessToken
        if (result.hasResolution() || token == null) throw DriveAccessNeededException(result.pendingIntent?.intentSender)
        return token
    }

    /** Forgets [token], which Drive rejected, so the next [driveAccessToken] gets a new one. Blocks. */
    fun clearAccessToken(context: Context, token: String) {
        Tasks.await(Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()))
    }

    private fun driveRequest(email: String) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_FILE)))
        .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
        .build()

    // As IOExceptions, like the Drive calls' own failures: no connection, or the account has to be signed in again.
    private fun authorizationFailure(e: Throwable): IOException = when ((e as? ApiException)?.statusCode) {
        CommonStatusCodes.NETWORK_ERROR -> IOException("No connection to authorize Drive access", e)
        CommonStatusCodes.SIGN_IN_REQUIRED -> DriveAccessNeededException(consent = null, cause = e)
        else -> DriveAuthorizationFailedException(e)
    }

    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
}
