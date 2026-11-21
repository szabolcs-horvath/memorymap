package com.szabolcshorvath.memorymap.settings

import android.graphics.Color
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.auth.SignInNotConfiguredException
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.backup.BackupManager.Companion.AUTOMATIC_BACKUP_PREFIX
import com.szabolcshorvath.memorymap.backup.BackupManager.Companion.MANUAL_BACKUP_PREFIX
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
import com.szabolcshorvath.memorymap.screens.Snackbar
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The Settings tab: the Google account, Drive backups and restores, map preferences and media maintenance. */
@RunWith(AndroidJUnit4::class)
class SettingsTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aFailedSignInSaysWhyAndStaysSignedOutThenSigningInShowsTheBackupsAndSigningOutHidesThem() {
        addBackup(MANUAL_BACKUP)
        // A build without an OAuth client ID can't sign in at all.
        FakeAccountService.signInError = SignInNotConfiguredException()
        openSettings()
        SettingsScreen.assertSignedOut()

        SettingsScreen.signIn()
        Snackbar.assertShown(NOT_SET_UP)
        SettingsScreen.waitUntilIdle()
        SettingsScreen.assertSignedOut()

        FakeAccountService.signInError = NoCredentialException() // The phone has no Google account.
        SettingsScreen.signIn()
        Snackbar.assertShown(NO_ACCOUNT)
        SettingsScreen.assertSignedOut()

        FakeAccountService.signInError = null // The user tries again, and it works.
        SettingsScreen.signIn()
        SettingsScreen.assertSignedIn(DEFAULT_EMAIL)
        SettingsScreen.assertBackupShown(MANUAL)

        SettingsScreen.signOut()
        SettingsScreen.assertSignedOut()
        SettingsScreen.assertBackupGone(MANUAL)
    }

    /** Backs up a memory, adds another one, and restores the backup, which brings back only the first. */
    @Test
    fun backupNowAddsABackupThatRestoresWhatItHoldsAndSaysSo() {
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
        // It names the backup by its kind and when it was made, not by its file name.
        Dialog.assertSays("Manual backup from ")
        Dialog.tap("Restore")

        SettingsScreen.waitUntilIdle()
        SettingsScreen.assertResult("Restore successful")
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(KEPT)
        TimelineScreen.assertMemoryGone(ADDED_AFTER_THE_BACKUP)
        // Both memories are in Budapest: before the restore, the map showed one marker for the two.
        BottomNav.open(Tab.MAP)
        MapScreen.assertMarkerShown(KEPT)
        MapScreen.assertMarkerGone("2 Memories")
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
    fun theNewestFiveBackupsAreShownAndAllOfThemOnAPageOfTheirOwn() {
        TestData.signIn()
        // "Backup 1.zip" is the newest. A name the app didn't make is shown as it is, so each row can be told apart.
        (1..7).forEach { addBackup("Backup $it.zip", minutesAgo = it.toLong()) }
        openSettings()
        SettingsScreen.assertBackupShown("Backup 5.zip")
        SettingsScreen.assertShowAllBackups(total = 7)
        SettingsScreen.assertBackupGone("Backup 6.zip")

        SettingsScreen.showAllBackups()
        SettingsScreen.assertBackupShown("Backup 7.zip")
        Ui.pressBack()
        SettingsScreen.assertAllBackupsGone()
        SettingsScreen.assertShown()

        SettingsScreen.showAllBackups()
        SettingsScreen.closeAllBackups()
        SettingsScreen.assertShowAllBackups(total = 7)
    }

    @Test
    fun withFiveBackupsOrFewerAllAreShownOnThePage() {
        TestData.signIn()
        (1..5).forEach { addBackup("Backup $it.zip", minutesAgo = it.toLong()) }
        openSettings()
        SettingsScreen.assertBackupShown("Backup 5.zip")
        SettingsScreen.assertNoShowAllBackups()
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

    /**
     * Once old automatic backups are to be deleted, the next automatic backup moves those older than that to the trash,
     * but never the newest three automatic ones nor a manual one, and the setting stays set.
     */
    @Test
    fun anAutomaticBackupTrashesOldAutomaticBackupsButKeepsTheNewestThreeAndManualOnes() {
        AutomaticBackups.debounceSeconds = AUTOMATIC_BACKUP_DELAY_SECONDS
        TestData.signIn()
        val recent = listOf(oldBackup(AUTOMATIC_BACKUP_PREFIX, daysAgo = 2), oldBackup(AUTOMATIC_BACKUP_PREFIX, daysAgo = 40))
        val tooOld = listOf(50L, 60L, 400L).map { oldBackup(AUTOMATIC_BACKUP_PREFIX, daysAgo = it) }
        val manual = oldBackup(MANUAL_BACKUP_PREFIX, daysAgo = 500)
        openSettings()
        SettingsScreen.assertBackupRetention("Never")
        SettingsScreen.assertBackupRetentionNote(shown = false)

        SettingsScreen.setBackupRetention("Older than 1 month")
        SettingsScreen.assertBackupRetentionNote(shown = true)
        SettingsScreen.addColorPreset()

        val trashed = Ui.eventually(AUTOMATIC_BACKUP_TIMEOUT_MS, read = { FakeBackupStorage.trashedNames(DEFAULT_EMAIL) }) {
            it.isNotEmpty()
        }
        assertEquals(tooOld.toSet(), trashed)
        val kept = FakeBackupStorage.names(DEFAULT_EMAIL)
        // The new automatic backup is the third of the newest three.
        assertEquals(recent.size + 2, kept.size)
        assertTrue("Kept: $kept", kept.containsAll(recent + manual))
        BottomNav.open(Tab.MAP)
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertBackupRetention("Older than 1 month")
    }

    @Test
    fun mapPreferencesStaySet() {
        openSettings()

        SettingsScreen.setShowFragmentMarkers(true)
        SettingsScreen.setMarkerClustering(false)
        SettingsScreen.setDefaultMapFilter("Last 30 days")
        BottomNav.open(Tab.MAP)
        BottomNav.open(Tab.SETTINGS)

        SettingsScreen.assertShowFragmentMarkers(true)
        SettingsScreen.assertMarkerClustering(false)
        SettingsScreen.assertDefaultMapFilter("Last 30 days")
    }

    /** One re-link finds both kinds of lost photo: one brought from another phone, and one edited after it was picked. */
    @Test
    fun reLinkingFindsAPhotoBroughtFromAnotherPhoneAndAPickedPhotoEditedAfterwards() {
        // Saved by another app, so the re-link that runs when the app starts can't see it yet: that needs photo access.
        TestData.photoSavedByAnotherApp(TestData.memory(KEPT, daysAgo = 1), PHOTO_COLOR)
        TestData.pickedPhotoEditedLater(
            TestData.memory(EDITED, daysAgo = 2),
            pickedColor = PHOTO_COLOR,
            editedColor = EDITED_PHOTO_COLOR
        )
        openSettings()
        Flows.openMemory(KEPT)
        MemoryScreen.assertPhotoFromAnotherDevice()
        Flows.openMemory(EDITED)
        MemoryScreen.assertPhotoNotLoaded()
        TestData.grantMediaAccess() // As "Allow all" on the app's page in Android's settings.

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.relinkMedia()
        SettingsScreen.waitForRelinkToFinish()

        Flows.openMemory(KEPT)
        MemoryScreen.assertPhotoLoaded()
        Flows.openMemory(EDITED)
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

    /** Adds a backup named with [prefix], made [daysAgo]; returns its name. */
    private fun oldBackup(prefix: String, daysAgo: Long): String {
        val made = Instant.now().minus(Duration.ofDays(daysAgo))
        val name = prefix + BACKUP_TIME.format(made.atZone(ZoneOffset.UTC)) + ".zip"
        FakeBackupStorage.add(DEFAULT_EMAIL, name, modifiedMillis = made.toEpochMilli())
        return name
    }

    private companion object {
        val BACKUP_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        const val KEPT = "Vienna trip"
        const val ADDED_AFTER_THE_BACKUP = "Lake day"
        const val EDITED = "Picnic"
        const val MANUAL = "Manual backup"
        const val AUTOMATIC = "Automatic backup"
        const val MANUAL_BACKUP = "MemoryMap_Manual_Backup_20261020_101500.zip"
        const val AUTOMATIC_BACKUP = "MemoryMap_Automatic_Backup_20261019_080000.zip"
        const val RESTORE_TITLE = "Restore this backup?"
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
