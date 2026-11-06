package com.szabolcshorvath.memorymap.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.auth.DriveAccessNeededException
import com.szabolcshorvath.memorymap.fakes.FakeAccountService.DEFAULT_EMAIL
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * A backup, restore or delete that fails says what failed and why in plain words, never the error's class name.
 * (The fake Drive fails as the real one does.)
 */
@RunWith(AndroidJUnit4::class)
class BackupErrorTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Before
    fun signInWithABackup() {
        TestData.signIn()
        TestData.grantMediaAccess()
        FakeBackupStorage.add(DEFAULT_EMAIL, BACKUP, modifiedMillis = System.currentTimeMillis(), content = byteArrayOf(1))
    }

    @Test
    fun aBackupWithoutConnectionSaysSo() {
        FakeBackupStorage.uploadFailure = IOException("Unable to resolve host www.googleapis.com")
        openSettings()

        SettingsScreen.backUpNow()

        SettingsScreen.assertResult(failure(R.string.backup_failed, R.string.drive_failure_no_connection))
    }

    @Test
    fun aDeleteThatDriveCantDoSaysToTryLaterAndKeepsTheBackup() {
        FakeBackupStorage.deleteFailure = driveError(SERVICE_UNAVAILABLE)
        openSettings()

        SettingsScreen.tapDeleteOnFirstBackup()
        Dialog.tap("Delete")

        SettingsScreen.assertResult(failure(R.string.delete_backup_failed, R.string.drive_failure_unavailable))
        SettingsScreen.assertBackupShown(MANUAL)
    }

    @Test
    fun aRestoreWithoutDriveAccessOffersToAllowIt() {
        FakeBackupStorage.downloadFailure = DriveAccessNeededException(consent = null)
        openSettings()

        SettingsScreen.tapRestoreOnFirstBackup()
        Dialog.tap("Restore")

        SettingsScreen.assertResult(failure(R.string.restore_failed, R.string.drive_failure_needs_access))
        SettingsScreen.assertBackupListProblem(R.string.backup_list_needs_access)

        FakeBackupStorage.downloadFailure = null // What allowing access again does on Drive.
        SettingsScreen.allowDriveAccess()

        SettingsScreen.assertNoBackupListProblem()
        SettingsScreen.assertBackupShown(MANUAL)
    }

    private fun failure(what: Int, why: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(R.string.operation_failed, context.getString(what), context.getString(why))
    }

    private fun driveError(status: Int) =
        GoogleJsonResponseException(HttpResponseException.Builder(status, "error", HttpHeaders()), GoogleJsonError())

    private fun openSettings() {
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertBackupShown(MANUAL)
    }

    private companion object {
        const val BACKUP = "MemoryMap_Manual_Backup_20261020_101500.zip"
        const val MANUAL = "Manual Backup"
        const val SERVICE_UNAVAILABLE = 503
    }
}
