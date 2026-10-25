package com.szabolcshorvath.memorymap.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender

/**
 * Signing in to a Google account and getting access to its Drive. The app uses [GoogleAccountService]; UI tests
 * replace it with a fake (see [com.szabolcshorvath.memorymap.AppServices]).
 */
interface AccountService {
    /** Signs in and returns the account's email. [activityContext] shows the sign-in UI. Throws if it fails or is cancelled. */
    suspend fun signIn(activityContext: Context): String

    suspend fun signOut(context: Context)

    /** Asks for access to the app's files on Drive. Throws if the request fails. */
    suspend fun authorizeDrive(context: Context): DriveAuthorization

    /** Checks the result of the consent screen from [DriveAuthorization.NeedsConsent]. Throws if access wasn't allowed. */
    fun checkDriveConsentResult(context: Context, data: Intent?)
}

sealed interface DriveAuthorization {
    data object Granted : DriveAuthorization

    /** The user must allow access first: launch [intentSender], then pass its result to [AccountService.checkDriveConsentResult]. */
    data class NeedsConsent(val intentSender: IntentSender) : DriveAuthorization
}
