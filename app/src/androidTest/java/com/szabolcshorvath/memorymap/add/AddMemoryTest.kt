package com.szabolcshorvath.memorymap.add

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakeMediaPicker
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.DatePickerDialog
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.TimePickerDialog
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.ToastMessage
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Add tab: a new memory with its photos, the checks before saving, clearing the form, and editing a memory,
 * including its dates and the question before an edit replaces unsaved changes. The photo picker is a fake
 * ([FakeMediaPicker]).
 */
@RunWith(AndroidJUnit4::class)
class AddMemoryTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aMemoryIsSavedOnceItHasATitleAndALocationAndOpensWithItsPhotos() {
        FakeMediaPicker.nextPick = listOf(TestData.galleryPhoto(Color.RED), TestData.galleryPhoto(Color.BLUE))
        openAdd()

        // Without a title, and then without a location, Save says what is missing, keeps the form open and saves nothing.
        AddScreen.save()
        AddScreen.assertMissingTitleAndPlace()
        AddScreen.assertShown()
        AddScreen.assertNew()
        AddScreen.typeTitle(PICNIC)
        AddScreen.save()
        AddScreen.assertShown()
        MemoryScreen.assertGone()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertEmpty()
        BottomNav.open(Tab.ADD)
        AddScreen.assertTitle(PICNIC)

        AddScreen.typeDescription("By the lake")
        Flows.pickLocationOnMap()
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
    fun pickedPhotosCanBeRemovedAndClearAsksFirstThenEmptiesTheForm() {
        openAdd()
        FakeMediaPicker.nextPick = emptyList() // The user leaves the picker.
        AddScreen.addPhotos()
        AddScreen.assertSelectedCount(0)

        FakeMediaPicker.nextPick = listOf(TestData.galleryPhoto(Color.RED), TestData.galleryPhoto(Color.BLUE))
        AddScreen.addPhotos()
        AddScreen.assertSelectedCount(2)
        AddScreen.removeFirstPhoto()
        AddScreen.assertSelectedCount(1)

        AddScreen.typeTitle(PICNIC)
        AddScreen.clear()
        Dialog.assertShown("Clear Fields")
        Dialog.tap("Cancel")
        AddScreen.assertTitle(PICNIC)
        AddScreen.assertSelectedCount(1)

        AddScreen.clear()
        Dialog.tap("Clear")
        AddScreen.assertNoTitle()
        AddScreen.assertSelectedCount(0)
    }

    @Test
    fun anEditIsSavedToTheSameMemoryWithoutTheRemovedPhoto() {
        val trip = TestData.memory(VIENNA)
        TestData.photo(trip, Color.RED, order = 0)
        TestData.photo(trip, Color.BLUE, order = 1)
        openForEditing(VIENNA)
        AddScreen.assertSelectedCount(2)

        AddScreen.typeTitle(VIENNA_RENAMED)
        AddScreen.removeFirstPhoto()
        AddScreen.assertSelectedCount(1)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA_RENAMED)
        MemoryScreen.assertPhotoCount(1)
        MemoryScreen.assertPhotoColors(Color.BLUE)
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(VIENNA_RENAMED)
        TimelineScreen.assertMemoryGone(VIENNA)
    }

    @Test
    fun editingAsksBeforeItReplacesANewMemory() {
        TestData.memory(VIENNA)
        openAdd()
        AddScreen.typeTitle(DRAFT)

        Flows.openMemory(VIENNA)
        MemoryScreen.edit()
        Dialog.assertShown(DISCARD_DRAFT)
        Dialog.tap("Keep editing")

        AddScreen.assertTitle(DRAFT)
        AddScreen.assertNew()

        Flows.openMemory(VIENNA)
        MemoryScreen.edit()
        Dialog.tap("Discard")

        AddScreen.assertTitle(VIENNA)
        AddScreen.assertEditing()
    }

    @Test
    fun leavingAnEditKeepsItOnlyIfItChanged() {
        TestData.memory(VIENNA)
        openForEditing(VIENNA)

        // Unchanged: the next visit starts a new memory.
        BottomNav.open(Tab.MAP)
        BottomNav.open(Tab.ADD)
        AddScreen.assertNoTitle()
        AddScreen.assertNew()

        // Changed: the edit is still there.
        Flows.openMemory(VIENNA)
        MemoryScreen.edit()
        AddScreen.assertTitle(VIENNA)
        AddScreen.typeTitle(VIENNA_RENAMED)
        BottomNav.open(Tab.MAP)
        BottomNav.open(Tab.ADD)
        AddScreen.assertTitle(VIENNA_RENAMED)
        AddScreen.assertEditing()
    }

    @Test
    fun aSaveThatFinishesAfterLeavingTheTabSaysSoWithoutOpeningTheMemory() {
        openAdd()
        AddScreen.typeTitle(PICNIC)
        AddScreen.setAllDay(true)
        Flows.pickLocationOnMap()

        // The database is busy, so the save waits while the user goes to the map.
        val database = TestData.holdDatabaseWrites()
        try {
            AddScreen.save()
            BottomNav.open(Tab.MAP)
            ToastMessage.assertShownAfter(MEMORY_SAVED) { database.release() }
        } finally {
            database.release()
        }

        MapScreen.assertShown()
        MemoryScreen.assertGone()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(PICNIC)
        BottomNav.open(Tab.ADD)
        AddScreen.assertNoTitle()
        AddScreen.assertAllDay(false)
        AddScreen.assertNew()
    }

    @Test
    fun pickedDatesAndTimesAreSavedWithTheEndAfterTheStartAndAllDayDropsTheTimes() {
        TestData.memory(VIENNA) // Yesterday from 12:00 to 14:00.
        openForEditing(VIENNA)

        // An end before the start moves the start to an hour before it.
        AddScreen.openEndDatePicker()
        DatePickerDialog.pickDayInPreviousMonth(END_DAY)
        Dialog.tap("OK")
        AddScreen.assertEndDay(END_DAY)
        AddScreen.assertStartDay(END_DAY)

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

        // A start well before the end leaves the end where it is.
        AddScreen.openStartDatePicker()
        DatePickerDialog.pickDayInPreviousMonth(START_DAY)
        Dialog.tap("OK")
        AddScreen.assertStartDay(START_DAY)
        AddScreen.assertEndDay(END_DAY)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        // The 12-hour clock puts a narrow no-break space before "PM".
        val date = MemoryScreen.dateText().replace(NARROW_SPACE, ' ')
        assertTrue("The date \"$date\" doesn't start on day $START_DAY", Regex("\\b$START_DAY\\b").containsMatchIn(date))
        assertTrue("The date \"$date\" doesn't end on day $END_DAY", Regex("\\b$END_DAY\\b").containsMatchIn(date))
        assertTrue(
            "The date \"$date\" doesn't end at $LATER_HOUR:00",
            date.endsWith("$LATER_HOUR:00") || date.endsWith("${LATER_HOUR - HALF_DAY}:00 PM")
        )

        MemoryScreen.edit()
        AddScreen.setAllDay(true)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        val allDay = MemoryScreen.dateText()
        assertFalse("The all-day memory shows a time: \"$allDay\"", allDay.contains(":"))
    }

    @Test
    fun timesFollowThePhonesTwelveOrTwentyFourHourClock() {
        app.useClock(24) // In US English, where the language alone would choose 12 hours.
        TestData.memory(VIENNA)
        openForEditing(VIENNA)

        AddScreen.openStartTimePicker()
        TimePickerDialog.assertTwentyFourHourClock()
        TimePickerDialog.pickHour(LATE_HOUR)
        Dialog.tap("OK")
        AddScreen.assertStartTime(Regex("$LATE_HOUR:\\d{2}"))

        app.useClock(12)
        AddScreen.openEndTimePicker()
        TimePickerDialog.assertTwelveHourClock()
        TimePickerDialog.pickHour(LATER_HOUR)
        Dialog.tap("OK")
        AddScreen.assertEndTime(Regex("${LATER_HOUR - HALF_DAY}:\\d{2} PM"))
        AddScreen.assertStartTime(Regex("${LATE_HOUR - HALF_DAY}:\\d{2} PM"))
    }

    private fun openAdd() {
        app.launch()
        BottomNav.open(Tab.ADD)
    }

    private fun openForEditing(title: String) {
        app.launch()
        Flows.editMemory(title)
    }

    private companion object {
        const val PICNIC = "Picnic"
        const val DRAFT = "Half-written memory"
        const val VIENNA = "Vienna trip"
        const val VIENNA_RENAMED = "Vienna weekend"
        const val DISCARD_DRAFT = "Discard the new memory?"
        const val MEMORY_SAVED = "Memory saved"

        // Days and hours that can't be mistaken for one another in the date line ("Aug 5, 2026 8:00 PM - ...").
        const val END_DAY = 15
        const val START_DAY = 5
        const val LATE_HOUR = 20
        const val LATER_HOUR = 22
        const val HALF_DAY = 12
        const val NARROW_SPACE = ' '
    }
}
