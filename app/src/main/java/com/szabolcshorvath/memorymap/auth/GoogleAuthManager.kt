package com.szabolcshorvath.memorymap.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.firebase.perf.metrics.AddTrace
import com.szabolcshorvath.memorymap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthManager(private val context: Context) {
    private var credentialManager = CredentialManager.create(context)

    /** Signs in with Google and returns the account's email. Throws if sign-in fails or is cancelled. */
    suspend fun signIn(): String = withContext(Dispatchers.Main) {
        if (BuildConfig.OAUTH_WEB_CLIENT_ID.isBlank()) throw SignInNotConfiguredException()
        val googleIdOption = GetSignInWithGoogleOption.Builder(BuildConfig.OAUTH_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        val result = credentialManager.getCredential(request = request, context = context)

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

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    @AddTrace(name = "google_auth_manager_get_google_account_credential", enabled = true)
    fun getGoogleAccountCredential(email: String?, scopes: List<String>): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(context, scopes).apply {
            selectedAccountName = email
        }
    }

    companion object {
        const val TAG = "GoogleAuthManager"
    }
}
