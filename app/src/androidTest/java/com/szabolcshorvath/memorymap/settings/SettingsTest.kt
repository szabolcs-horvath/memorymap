package com.szabolcshorvath.memorymap.settings

import android.graphics.Color
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.auth.SignInNotConfiguredException
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.fakes.FakeAccountService.DEFAULT_EMAIL
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.screens.ShareSheet
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.ToastMessage
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Settings tab: the Google account, Drive backups and restores, map preferences and media maintenance. */
@RunWith(AndroidJUnit4::class)
class SettingsTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aFailedSignInStaysSignedOutThenSigningInShowsTheBackupsAndSigningOutHidesThem() {
        addBackup(MANUAL_BACKUP)
        FakeAccountService.signInError = NoCredentialException()
        openSettings()
        SettingsScreen.assertSignedOut()

        ToastMessage.assertShownAfter(NO_ACCOUNT) { SettingsScreen.signIn() }
        SettingsScreen.assertSignedOut()

        FakeAccountService.signInError = null // The user tries again, and it works.
        SettingsScreen.signIn()
        SettingsScreen.assertSignedIn(DEFAULT_EMAIL)
        SettingsScreen.assertBackupShown(MANUAL)

        SettingsScreen.signOut()
        SettingsScreen.assertSignedOut()
        SettingsScreen.assertBackupGone(MANUAL)
    }

    @Test
    fun signingInToABuildWithoutAnOAuthClientIdSaysSoAndStaysSignedOut() {
        FakeAccountService.signInError = SignInNotConfiguredException()
        openSettings()

        ToastMessage.assertShownAfter(NOT_SET_UP) { SettingsScreen.signIn() }
        SettingsScreen.waitUntilIdle()
        SettingsScreen.assertSignedOut()
    }

    @Test
    fun backupNowAddsABackupThatRestoresWhatItHolds() {
        backUpThenRestore()

        SettingsScreen.waitUntilIdle()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(KEPT)
        TimelineScreen.assertMemoryGone(ADDED_AFTER_THE_BACKUP)
        // Both memories are in Budapest: before the restore, the map showed one marker for the two.
        BottomNav.open(Tab.MAP)
        MapScreen.assertMarkerShown(KEPT)
        MapScreen.assertMarkerGone("2 Memories")
    }

    @Test
    fun aRestoreSaysThatItSucceeded() {
        backUpThenRestore()

        SettingsScreen.waitUntilIdle()
        SettingsScreen.assertResult("Restore successful")
    }

    /** Backs up a memory, adds another one, and restores the backup. */
    private fun backUpThenRestore() {
        TestData.memory(KEPT)
        TestData.signIn()
        TestData.grantMediaAccess()
        openSettings()
        SettingsScreen.backUpNow()
        SettingsScreen.assertResult("Backup successful")
        SettingsScreen.assertBackupShown(MANUAL)
        TestData.memory(ADDED_AFTER_THE_BACKUP)

        SettingsScreen.tapRestoreOnFirstBackup()
        Dialog.assertShown(RESTORE_TITLE)
        Dialog.tap("Restore")
    }

    @Test
    fun aBrokenBackupFailsToRestoreAndKeepsTheData() {
        TestData.memory(KEPT)
        TestData.signIn()
        TestData.grantMediaAccess()
        addBackup(MANUAL_BACKUP) // Without content: the download fails.
        openSettings()

        SettingsScreen.tapRestoreOnFirstBackup()
        Dialog.tap("Cancel")
        Dialog.assertGone(RESTORE_TITLE)
        SettingsScreen.tapRestoreOnFirstBackup()
        Dialog.tap("Restore")

        SettingsScreen.assertResult("Restore failed")
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(KEPT)
    }

    @Test
    fun deletingABackupAsksFirst() {
        TestData.signIn()
        addBackup(AUTOMATIC_BACKUP, minutesAgo = 60)
        addBackup(MANUAL_BACKUP, minutesAgo = 5)
        openSettings()
        SettingsScreen.assertBackupShown(MANUAL)

        SettingsScreen.tapDeleteOnFirstBackup()
        Dialog.assertShown(DELETE_TITLE)
        Dialog.tap("Cancel")
        Dialog.assertGone(DELETE_TITLE)
        SettingsScreen.assertBackupShown(MANUAL)

        SettingsScreen.tapDeleteOnFirstBackup()
        Dialog.tap("Delete")

        SettingsScreen.assertResult("Backup deleted")
        SettingsScreen.assertBackupGone(MANUAL)
        SettingsScreen.assertBackupShown(AUTOMATIC)
    }

    @Test
    fun aChangeIsBackedUpAutomatically() {
        // Long enough to see that a backup is on its way, without waiting the app's 15 seconds.
        AutomaticBackups.debounceSeconds = AUTOMATIC_BACKUP_DELAY_SECONDS
        TestData.signIn()
        openSettings()

        SettingsScreen.addColorPreset()

        SettingsScreen.assertAutomaticBackupPending()
        SettingsScreen.assertBackupAppears(AUTOMATIC, timeoutMs = AUTOMATIC_BACKUP_TIMEOUT_MS)
    }

    @Test
    fun mapPreferencesStaySet() {
        openSettings()

        SettingsScreen.setShowFragmentMarkers(true)
        SettingsScreen.setMarkerClustering(false)
        SettingsScreen.setDefaultMapFilter("Last 30 Days")
        BottomNav.open(Tab.MAP)
        BottomNav.open(Tab.SETTINGS)

        SettingsScreen.assertShowFragmentMarkers(true)
        SettingsScreen.assertMarkerClustering(false)
        SettingsScreen.assertDefaultMapFilter("Last 30 Days")
    }

    @Test
    fun reLinkingFindsAPhotoBroughtFromAnotherPhone() {
        // Saved by another app, so the re-link that runs when the app starts can't see it yet: that needs photo access.
        TestData.photoSavedByAnotherApp(TestData.memory(KEPT), PHOTO_COLOR)
        openSettings()
        Flows.openMemory(KEPT)
        MemoryScreen.assertPhotoFromAnotherDevice()
        TestData.grantMediaAccess() // As "Allow all" on the app's page in Android's settings.

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.relinkMedia()
        SettingsScreen.waitForRelinkToFinish()

        Flows.openMemory(KEPT)
        MemoryScreen.assertPhotoLoaded()
    }

    @Test
    fun reLinkingFindsAPickedPhotoEditedAfterwards() {
        TestData.pickedPhotoEditedLater(TestData.memory(KEPT), pickedColor = PHOTO_COLOR, editedColor = EDITED_PHOTO_COLOR)
        openSettings()
        Flows.openMemory(KEPT)
        MemoryScreen.assertPhotoNotLoaded()
        TestData.grantMediaAccess()

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.relinkMedia()
        SettingsScreen.waitForRelinkToFinish()

        Flows.openMemory(KEPT)
        MemoryScreen.assertPhotoLoaded()
        MemoryScreen.assertPhotoColors(EDITED_PHOTO_COLOR)
    }

    @Test
    fun sendingLogsOpensTheShareSheet() {
        openSettings()

        SettingsScreen.sendLogs()

        ShareSheet.assertShown()
        Ui.pressBack()
        SettingsScreen.assertShown()
    }

    private fun openSettings() {
        app.launch()
        BottomNav.open(Tab.SETTINGS)
    }

    private fun addBackup(name: String, minutesAgo: Long = 0) {
        FakeBackupStorage.add(DEFAULT_EMAIL, name, modifiedMillis = System.currentTimeMillis() - minutesAgo * MINUTE_MS)
    }

    private companion object {
        const val KEPT = "Vienna trip"
        const val ADDED_AFTER_THE_BACKUP = "Lake day"
        const val MANUAL = "Manual Backup"
        const val AUTOMATIC = "Automatic Backup"
        const val MANUAL_BACKUP = "MemoryMap_Manual_Backup_20261020_101500.zip"
        const val AUTOMATIC_BACKUP = "MemoryMap_Automatic_Backup_20261019_080000.zip"
        const val RESTORE_TITLE = "Restore Backup"
        const val DELETE_TITLE = "Delete backup?"
        const val NOT_SET_UP = "Google sign-in isn't set up in this build (missing OAuth client ID)."
        const val NO_ACCOUNT = "There's no Google account to sign in with. Add one in the phone's settings, then try again."
        const val MINUTE_MS = 60_000L
        val PHOTO_COLOR = Color.rgb(150, 60, 170)
        val EDITED_PHOTO_COLOR = Color.rgb(40, 160, 90)

        const val AUTOMATIC_BACKUP_DELAY_SECONDS = 5L
        const val AUTOMATIC_BACKUP_TIMEOUT_MS = 30_000L
    }
}
