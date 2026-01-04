package com.szabolcshorvath.memorymap.auth

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.szabolcshorvath.memorymap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthManager(private val context: Context) {
    lateinit var googleCredential: Credential
    private var credentialManager = CredentialManager.create(context)

    suspend fun signIn(callback: (String) -> Unit) {
        withContext(Dispatchers.Main) {
            try {
                val googleIdOption =
                    GetSignInWithGoogleOption.Builder(BuildConfig.OAUTH_WEB_CLIENT_ID)
                        .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                handleSignIn(result, callback)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Sign in failed", e)
                Toast.makeText(context, "Sign in failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Sign in error", e)
                Toast.makeText(context, "Sign in error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse, callback: (String) -> Unit) {
        when (val credential = result.credential) {
//            is PublicKeyCredential -> {
//                val responseJson = credential.authenticationResponseJson
//                val userCredential = fidoAuthenticateWithServer(responseJson)
//                loginWithPasskey(userCredential)
//            }
//            is PasswordCredential -> {
//                val userName = credential.id
//                val password = credential.password
//                loginWithPassword(userName, password)
//            }
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(credential.data)
                    googleCredential = googleIdTokenCredential
                    callback(googleIdTokenCredential.id)
                } else {
                    Log.e(TAG, "Unexpected credential type")
                    Toast.makeText(context, "Unexpected credential type", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            else -> {
                Log.e(TAG, "Unexpected credential type")
                Toast.makeText(context, "Unexpected credential type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    fun getGoogleAccountCredential(email: String?, scopes: List<String>): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(context, scopes).apply {
            selectedAccountName = email
        }
    }

    companion object {
        const val TAG = "GoogleAuthManager"
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
    }
}