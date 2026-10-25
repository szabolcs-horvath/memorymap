package com.szabolcshorvath.memorymap.fakes

import android.content.Context
import android.content.Intent
import com.szabolcshorvath.memorymap.auth.AccountService
import com.szabolcshorvath.memorymap.auth.DriveAuthorization

/**
 * Sign-in without Google: signing in returns [signInEmail] at once, and Drive access is always granted.
 * The test orchestrator starts a new process for every test, so changes don't leak into the next test.
 */
object FakeAccountService : AccountService {
    const val DEFAULT_EMAIL = "test.user@example.com"

    @Volatile
    var signInEmail: String = DEFAULT_EMAIL

    override suspend fun signIn(activityContext: Context): String = signInEmail

    override suspend fun signOut(context: Context) = Unit

    override suspend fun authorizeDrive(context: Context): DriveAuthorization = DriveAuthorization.Granted

    override fun checkDriveConsentResult(context: Context, data: Intent?) = Unit
}
