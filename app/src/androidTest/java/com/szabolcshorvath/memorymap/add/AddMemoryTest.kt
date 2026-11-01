package com.szabolcshorvath.memorymap.add

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakeMediaPicker
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.DatePickerDialog
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.TimePickerDialog
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Place
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Add tab: a new memory with its photos, the checks before saving, clearing the form, and editing a memory,
 * including the question before an edit replaces unsaved changes. The photo picker is a fake ([FakeMediaPicker]).
 */
@RunWith(AndroidJUnit4::class)
class AddMemoryTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aSavedMemoryOpensWithItsPhotosAndShowsInTheTimeline() {
        FakeMediaPicker.nextPick = listOf(TestData.galleryPhoto(Color.RED), TestData.galleryPhoto(Color.BLUE))
        openAdd()
        AddScreen.typeTitle(PICNIC)
        AddScreen.typeDescription("By the lake")
        pickALocation()
        AddScreen.addPhotos()
        AddScreen.assertSelectedCount(2)

        AddScreen.save()

        MemoryScreen.assertShowing(PICNIC)
        MemoryScreen.assertDescription("By the lake")
        MemoryScreen.assertPhotoCount(2)
        MemoryScreen.assertPhotoLoaded()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(PICNIC)
    }

    @Test
    fun savingNeedsATitleAndALocation() {
        openAdd()

        AddScreen.save()
        AddScreen.assertShown()
        AddScreen.typeTitle(PICNIC)
        AddScreen.save()
        AddScreen.assertShown()

        MemoryScreen.assertGone()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertEmpty()
    }

    @Test
    fun aRemovedPhotoLeavesThePickedOnes() {
        FakeMediaPicker.nextPick = listOf(TestData.galleryPhoto(Color.RED), TestData.galleryPhoto(Color.BLUE))
        openAdd()
        AddScreen.addPhotos()
        AddScreen.assertSelectedCount(2)

        AddScreen.removeFirstPhoto()

        AddScreen.assertSelectedCount(1)
    }

    @Test
    fun aCancelledPhotoPickerAddsNothing() {
        FakeMediaPicker.nextPick = emptyList()
        openAdd()

        AddScreen.addPhotos()

        AddScreen.assertSelectedCount(0)
    }

    @Test
    fun clearAsksFirstAndEmptiesTheForm() {
        FakeMediaPicker.nextPick = listOf(TestData.galleryPhoto())
        openAdd()
        AddScreen.typeTitle(PICNIC)
        AddScreen.addPhotos()
        AddScreen.assertSelectedCount(1)

        AddScreen.clear()
        Dialog.assertShown("Clear Fields")
        Dialog.tap("Cancel")
        AddScreen.assertTitle(PICNIC)

        AddScreen.clear()
        Dialog.tap("Clear")

        AddScreen.assertNoTitle()
        AddScreen.assertSelectedCount(0)
    }

    @Test
    fun anEditedMemoryIsUpdatedNotCopied() {
        TestData.memory(VIENNA)
        openForEditing(VIENNA)

        AddScreen.typeTitle(VIENNA_RENAMED)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA_RENAMED)
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(VIENNA_RENAMED)
        TimelineScreen.assertMemoryGone(VIENNA)
    }

    @Test
    fun editingAsksBeforeItReplacesANewMemory() {
        TestData.memory(VIENNA)
        openAdd()
        AddScreen.typeTitle(DRAFT)

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(VIENNA)
        MemoryScreen.edit()
        Dialog.assertShown(DISCARD_DRAFT)
        Dialog.tap("Keep editing")

        AddScreen.assertTitle(DRAFT)
        AddScreen.assertNew()

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(VIENNA)
        MemoryScreen.edit()
        Dialog.tap("Discard")

        AddScreen.assertTitle(VIENNA)
        AddScreen.assertEditing()
    }

    @Test
    fun leavingAnUnchangedEditStartsANewMemoryNextTime() {
        TestData.memory(VIENNA)
        openForEditing(VIENNA)

        BottomNav.open(Tab.MAP)
        BottomNav.open(Tab.ADD)

        AddScreen.assertNoTitle()
        AddScreen.assertNew()
    }

    @Test
    fun leavingAChangedEditKeepsIt() {
        TestData.memory(VIENNA)
        openForEditing(VIENNA)
        AddScreen.typeTitle(VIENNA_RENAMED)

        BottomNav.open(Tab.MAP)
        BottomNav.open(Tab.ADD)

        AddScreen.assertTitle(VIENNA_RENAMED)
        AddScreen.assertEditing()
    }

    @Test
    fun backFromThePickerKeepsTheLocationTheMemoryHad() {
        TestData.memory(VIENNA, place = Place.VIENNA)
        openForEditing(VIENNA)

        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        Ui.pressBack()

        AddScreen.assertShown()
        AddScreen.assertLocationIs(Place.VIENNA)
    }

    @Test
    fun aPickedStartDateIsSaved() {
        TestData.memory(VIENNA)
        openForEditing(VIENNA)

        AddScreen.openStartDatePicker()
        DatePickerDialog.pickDayInPreviousMonth(PICKED_DAY)
        Dialog.tap("OK")
        AddScreen.assertStartDay(PICKED_DAY)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        val date = MemoryScreen.dateText()
        assertTrue("The date \"$date\" is not on day $PICKED_DAY", Regex("\\b$PICKED_DAY\\b").containsMatchIn(date))
    }

    @Test
    fun anAllDayMemoryShowsNoTimes() {
        TestData.memory(VIENNA)
        openForEditing(VIENNA)

        AddScreen.setAllDay(true)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        val date = MemoryScreen.dateText()
        assertFalse("The all-day memory shows a time: \"$date\"", date.contains(":"))
    }

    @Test
    fun theEndStaysAfterTheStart() {
        TestData.memory(VIENNA) // Yesterday from 12:00 to 14:00.
        openForEditing(VIENNA)

        // An end before the start moves the start to an hour before it.
        AddScreen.openEndDatePicker()
        DatePickerDialog.pickDayInPreviousMonth(PICKED_DAY)
        Dialog.tap("OK")
        AddScreen.assertEndDay(PICKED_DAY)
        AddScreen.assertStartDay(PICKED_DAY)

        // A start after the end moves the end to an hour after it.
        AddScreen.openStartTimePicker()
        TimePickerDialog.pickHour(LATE_HOUR)
        Dialog.tap("OK")
        AddScreen.assertStartHour(LATE_HOUR)
        AddScreen.assertEndHour(LATE_HOUR + 1)

        AddScreen.openEndTimePicker()
        TimePickerDialog.pickHour(LATER_HOUR)
        Dialog.tap("OK")
        AddScreen.assertEndHour(LATER_HOUR)
        AddScreen.assertStartHour(LATE_HOUR)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        // The 12-hour clock puts a narrow no-break space before "PM".
        val date = MemoryScreen.dateText().replace(NARROW_SPACE, ' ')
        assertTrue("The date \"$date\" is not on day $PICKED_DAY", Regex("\\b$PICKED_DAY\\b").containsMatchIn(date))
        assertTrue(
            "The date \"$date\" doesn't end at $LATER_HOUR:00",
            date.endsWith("$LATER_HOUR:00") || date.endsWith("${LATER_HOUR - HALF_DAY}:00 PM")
        )
    }

    @Test
    fun aPhotoRemovedWhileEditingIsGoneOnceSaved() {
        val trip = TestData.memory(VIENNA)
        TestData.photo(trip, Color.RED, order = 0)
        TestData.photo(trip, Color.BLUE, order = 1)
        openForEditing(VIENNA)
        AddScreen.assertSelectedCount(2)

        AddScreen.removeFirstPhoto()
        AddScreen.assertSelectedCount(1)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertPhotoCount(1)
        MemoryScreen.assertPhotoColors(Color.BLUE)
    }

    private fun openAdd() {
        app.launch()
        BottomNav.open(Tab.ADD)
    }

    private fun openForEditing(title: String) {
        app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(title)
        MemoryScreen.edit()
        AddScreen.assertTitle(title)
        AddScreen.assertEditing()
    }

    private fun pickALocation() {
        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()
        AddScreen.assertHasLocation()
    }

    private companion object {
        const val PICNIC = "Picnic"
        const val DRAFT = "Half-written memory"
        const val VIENNA = "Vienna trip"
        const val VIENNA_RENAMED = "Vienna weekend"
        const val DISCARD_DRAFT = "Discard the new memory?"
        const val PICKED_DAY = 15
        const val LATE_HOUR = 20
        const val LATER_HOUR = 22
        const val HALF_DAY = 12
        const val NARROW_SPACE = ' '
    }
}
