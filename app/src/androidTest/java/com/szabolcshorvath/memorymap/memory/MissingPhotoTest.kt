package com.szabolcshorvath.memorymap.memory

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakeMediaPicker
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.MissingPhotoSheet
import com.szabolcshorvath.memorymap.screens.PermissionDialog
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A photo of a memory whose file is gone: tapping it opens a sheet that looks for it again, suggests the photos taken
 * around the same time, and offers another photo, removing it, or Not now.
 */
@RunWith(AndroidJUnit4::class)
class MissingPhotoTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun suggestsWhatWasTakenAroundTheSameTimeAndUsesTheChosenOne() {
        val taken = TestData.photoTakenAt(GREEN, secondsAfter = 7) - 7_000
        TestData.photoTakenAt(BLUE, secondsAfter = -180)
        TestData.photoTakenAt(RED, secondsAfter = 20 * 60) // More than 5 minutes away.
        TestData.missingPhoto(TestData.memory(TRIP), taken)
        TestData.grantMediaAccess()
        openMissingPhoto()

        MissingPhotoSheet.assertSuggestions("7 sec later", "3 min earlier")
        MissingPhotoSheet.pick("7 sec later")
        Dialog.assertShown(MissingPhotoSheet.USE_TITLE)
        Dialog.tap("Cancel")
        Dialog.assertGone(MissingPhotoSheet.USE_TITLE)
        MissingPhotoSheet.assertShown()

        MissingPhotoSheet.pick("7 sec later")
        Dialog.tap("Use it")

        MissingPhotoSheet.assertGone()
        MemoryScreen.assertPhotoLoaded()
        MemoryScreen.assertPhotoColors(GREEN)
    }

    @Test
    fun aSuggestionThatLooksAlikeComesFirst() {
        val taken = TestData.photoTakenAt(TestData.Stripes.DARK_FIRST, secondsAfter = 7) - 7_000
        TestData.photoTakenAt(TestData.Stripes.LIGHT_FIRST, secondsAfter = -180)
        TestData.missingPhoto(TestData.memory(TRIP), taken, looksLike = TestData.Stripes.LIGHT_FIRST)
        TestData.grantMediaAccess()
        openMissingPhoto()

        MissingPhotoSheet.assertSuggestions("3 min earlier, which looks alike", "7 sec later")
    }

    @Test
    fun theSheetShowsHowTheLostPhotoLookedAndItsName() {
        TestData.missingPhoto(TestData.memory(TRIP), dateTaken = 0, thumbnailColor = GREEN)
        openMissingPhoto()

        MissingPhotoSheet.assertThumbnail(GREEN)
        MissingPhotoSheet.assertFile("IMG_20250314_092653.jpg · 640 × 480")
    }

    @Test
    fun showMoreLooksAnHourAround() {
        val taken = TestData.photoTakenAt(GREEN, secondsAfter = 20 * 60) - 20 * 60_000
        TestData.missingPhoto(TestData.memory(TRIP), taken)
        TestData.grantMediaAccess()
        openMissingPhoto()
        MissingPhotoSheet.assertMessage(NOTHING_WITHIN_5_MINUTES)

        MissingPhotoSheet.showMore()

        MissingPhotoSheet.assertSuggestions("20 min later")
    }

    @Test
    fun withoutAccessItAsksForItAndThenSuggests() {
        val taken = TestData.photoTakenAt(GREEN, secondsAfter = 7) - 7_000
        TestData.missingPhoto(TestData.memory(TRIP), taken)
        openMissingPhoto()
        MissingPhotoSheet.assertMessage(NEEDS_ACCESS)

        MissingPhotoSheet.allowAccess()
        PermissionDialog.allowAll()

        MissingPhotoSheet.assertSuggestions("7 sec later")
    }

    @Test
    fun anotherPhotoCanBeChosenInsteadWithoutAccess() {
        TestData.missingPhoto(TestData.memory(TRIP), dateTaken = 0)
        FakeMediaPicker.nextPick = listOf(TestData.galleryPhoto(RED))
        openMissingPhoto()

        MissingPhotoSheet.chooseAnother()

        MissingPhotoSheet.assertGone()
        MemoryScreen.assertPhotoLoaded()
        MemoryScreen.assertPhotoColors(RED)
    }

    @Test
    fun removeAsksFirstAndKeepsTheOtherPhotos() {
        val memory = TestData.memory(TRIP)
        TestData.missingPhoto(memory, dateTaken = 0, order = 1)
        TestData.photo(memory, BLUE, order = 2)
        openMissingPhoto()

        MissingPhotoSheet.remove()
        Dialog.assertShown(MissingPhotoSheet.REMOVE_TITLE)
        Dialog.tap("Cancel")
        Dialog.assertGone(MissingPhotoSheet.REMOVE_TITLE)
        MissingPhotoSheet.assertShown()

        MissingPhotoSheet.remove()
        Dialog.tap("Remove")

        MissingPhotoSheet.assertGone()
        MemoryScreen.assertPhotoCount(1)
        MemoryScreen.assertPhotoLoaded()
        MemoryScreen.assertPhotoColors(BLUE)
    }

    @Test
    fun notNowLeavesThePhotoMissing() {
        TestData.missingPhoto(TestData.memory(TRIP), dateTaken = 0)
        openMissingPhoto()

        MissingPhotoSheet.notNow()

        MissingPhotoSheet.assertGone()
        MemoryScreen.assertPhotoNotLoaded()
    }

    @Test
    fun theSheetComesBackAfterARotationAndProcessDeath() {
        val taken = TestData.photoTakenAt(GREEN, secondsAfter = 7) - 7_000
        TestData.missingPhoto(TestData.memory(TRIP), taken)
        TestData.grantMediaAccess()
        openMissingPhoto()
        MissingPhotoSheet.assertSuggestions("7 sec later")

        Ui.rotateToLandscape()
        MissingPhotoSheet.assertShown()
        Ui.rotateToPortrait()
        MissingPhotoSheet.assertSuggestions("7 sec later")

        app.simulateProcessDeath()
        MissingPhotoSheet.assertSuggestions("7 sec later")
    }

    @Test
    fun aPhotoTheReLinkFindsClosesTheSheet() {
        // Saved by another app, so the re-link that runs when the app starts can't see it yet: that needs photo access.
        TestData.photoSavedByAnotherApp(TestData.memory(TRIP), BLUE)
        app.launch()
        Flows.openMemory(TRIP)
        MemoryScreen.assertPhotoFromAnotherDevice()
        TestData.grantMediaAccess()

        MemoryScreen.openFirstPhoto()

        MemoryScreen.assertPhotoLoaded()
        MissingPhotoSheet.assertGone()
        MemoryScreen.assertPhotoColors(BLUE)
    }

    private fun openMissingPhoto() {
        app.launch()
        Flows.openMemory(TRIP)
        MemoryScreen.assertPhotoNotLoaded()
        MemoryScreen.openFirstPhoto()
        MissingPhotoSheet.assertShown()
    }

    private companion object {
        const val TRIP = "Vienna trip"
        const val NOTHING_WITHIN_5_MINUTES = "It isn't on this phone any more, and nothing else was taken within 5 minutes of it."
        const val NEEDS_ACCESS =
            "To look for it, and for what was taken around the same time, Memory Map needs access to all your photos and videos."
        val GREEN = Color.rgb(40, 160, 90)
        val BLUE = Color.rgb(30, 120, 200)
        val RED = Color.rgb(200, 60, 50)
    }
}
