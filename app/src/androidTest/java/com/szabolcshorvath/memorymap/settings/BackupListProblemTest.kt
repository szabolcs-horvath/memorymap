package com.szabolcshorvath.memorymap.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.auth.GoogleAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.fakes.FakeAccountService.DEFAULT_EMAIL
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * When the backup list can't be loaded, Settings says why instead of showing an empty list, which would look as if
 * the backups were gone, and keeps the list it showed before. (The fake Drive fails as the real one does.)
 */
@RunWith(AndroidJUnit4::class)
class BackupListProblemTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Before
    fun signInWithABackup() {
        TestData.signIn()
        FakeBackupStorage.add(DEFAULT_EMAIL, BACKUP, modifiedMillis = System.currentTimeMillis())
    }

    @Test
    fun aRefreshWithoutConnectionKeepsTheListAndSaysWhyUntilItWorksAgain() {
        openSettings()
        SettingsScreen.assertBackupShown(MANUAL)
        SettingsScreen.assertNoBackupListProblem()

        FakeBackupStorage.listFailure = IOException("Unable to resolve host www.googleapis.com")
        SettingsScreen.refreshBackups()

        SettingsScreen.assertBackupListProblem(R.string.backup_list_no_connection)
        SettingsScreen.assertBackupShown(MANUAL)
        SettingsScreen.assertAllowAccessGone()

        FakeBackupStorage.listFailure = null
        SettingsScreen.refreshBackups()

        SettingsScreen.assertNoBackupListProblem()
        SettingsScreen.assertBackupShown(MANUAL)
    }

    @Test
    fun lapsedDriveAccessIsOfferedAgainAndThenTheListLoads() {
        FakeBackupStorage.listFailure = GoogleAuthIOException(GoogleAuthException("NeedRemoteConsent"))
        openSettings()

        SettingsScreen.assertBackupListProblem(R.string.backup_list_needs_access)
        SettingsScreen.assertBackupGone(MANUAL)

        FakeBackupStorage.listFailure = null // What allowing access again does on Drive.
        SettingsScreen.allowDriveAccess()

        SettingsScreen.assertBackupShown(MANUAL)
        SettingsScreen.assertNoBackupListProblem()
        SettingsScreen.assertAllowAccessGone()
    }

    private fun openSettings() {
        app.launch()
        BottomNav.open(Tab.SETTINGS)
    }

    private companion object {
        const val BACKUP = "MemoryMap_Manual_Backup_20261020_101500.zip"
        const val MANUAL = "Manual Backup"
    }
}
