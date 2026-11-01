package com.szabolcshorvath.memorymap.settings

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.PermissionDialog
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Access to photos and videos, which re-linking needs to find photos that came from another phone. Every test starts
 * without it. The memory's photo is in the gallery, saved by another app (`TestData.photoSavedByAnotherApp`), so the
 * app can only find it once it has access: the app always sees the photos it saved itself.
 */
@RunWith(AndroidJUnit4::class)
class MediaAccessTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun reLinkingAsksForAccessAndThenFindsThePhoto() {
        TestData.photoSavedByAnotherApp(TestData.memory(TRIP), PHOTO_COLOR)
        app.launch()
        openMemory()
        MemoryScreen.assertPhotoFromAnotherDevice()

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.relinkMedia()
        PermissionDialog.allowAll()
        SettingsScreen.waitForRelinkToFinish()

        openMemory()
        MemoryScreen.assertPhotoLoaded()
    }

    @Test
    fun reLinkingFromTheMemoryAsksForAccessAndThenShowsThePhoto() {
        TestData.photoSavedByAnotherApp(TestData.memory(TRIP), PHOTO_COLOR)
        app.launch()
        openMemory()
        MemoryScreen.assertPhotoFromAnotherDevice()

        MemoryScreen.relinkMedia()
        PermissionDialog.allowAll()

        // The memory shows the photo as soon as it is linked, without being opened again.
        MemoryScreen.assertPhotoLoaded()
    }

    @Test
    fun aRestoreWithoutAccessStillRestoresThenOffersAccessForTheUnlinkedPhoto() {
        TestData.photoSavedByAnotherApp(TestData.memory(TRIP), PHOTO_COLOR)
        backUpThenAddAMemory()

        startRestore()
        Dialog.tap("Continue")
        PermissionDialog.refuse()

        // The restore ran without access, so the photo is still not linked: the app offers access again.
        Dialog.assertShown(MORE_ACCESS_TITLE)
        Dialog.tap("Allow access")
        PermissionDialog.allowAll()
        SettingsScreen.waitUntilIdle()

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryGone(ADDED_AFTER_THE_BACKUP)
        TimelineScreen.openMemory(TRIP)
        MemoryScreen.assertPhotoLoaded()
    }

    @Test
    fun notNowLeavesThePhotoUnlinked() {
        TestData.photoSavedByAnotherApp(TestData.memory(TRIP), PHOTO_COLOR)
        backUpThenAddAMemory()
        startRestore()
        Dialog.tap("Continue")
        PermissionDialog.refuse()

        Dialog.assertShown(MORE_ACCESS_TITLE)
        Dialog.tap("Not now")
        Dialog.assertGone(MORE_ACCESS_TITLE)
        SettingsScreen.waitUntilIdle()

        openMemory()
        MemoryScreen.assertPhotoFromAnotherDevice()
    }

    @Test
    fun cancellingTheExplanationRestoresNothing() {
        TestData.memory(TRIP)
        backUpThenAddAMemory()

        startRestore()
        Dialog.tap("Cancel")
        Dialog.assertGone(ACCESS_TITLE)

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(ADDED_AFTER_THE_BACKUP)
    }

    /** Backs up what the app holds, then adds a memory that a restore of that backup removes. */
    private fun backUpThenAddAMemory() {
        TestData.signIn()
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.backUpNow()
        SettingsScreen.assertResult("Backup successful")
        TestData.memory(ADDED_AFTER_THE_BACKUP)
    }

    /** Taps Restore on the backup and confirms it: without access, the app first explains why it asks for it. */
    private fun startRestore() {
        SettingsScreen.tapRestoreOnFirstBackup()
        Dialog.tap("Restore")
        Dialog.assertShown(ACCESS_TITLE)
    }

    private fun openMemory() {
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(TRIP)
        MemoryScreen.assertShowing(TRIP)
    }

    private companion object {
        const val TRIP = "Vienna trip"
        const val ADDED_AFTER_THE_BACKUP = "Lake day"
        const val ACCESS_TITLE = "Access to your photos"
        const val MORE_ACCESS_TITLE = "Some photos aren't linked yet"
        val PHOTO_COLOR = Color.rgb(150, 60, 170)
    }
}
