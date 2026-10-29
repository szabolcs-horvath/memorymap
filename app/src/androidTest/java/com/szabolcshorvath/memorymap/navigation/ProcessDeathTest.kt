package com.szabolcshorvath.memorymap.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.DatePickerDialog
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.MediaViewerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.screens.TimePickerDialog
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen comes back as it was when Android killed the app in the background and the user returns to it
 * ([MemoryMapAppRule.simulateProcessDeath]).
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun map() {
        app.launch()
        MapScreen.assertShown()

        app.simulateProcessDeath()

        MapScreen.assertShown()
        BottomNav.assertSelected(Tab.MAP)
    }

    @Test
    fun timelineKeepsTheSearch() {
        TestData.memory(OTHER_TITLE, daysAgo = 1)
        TestData.memory(TITLE, daysAgo = 5)
        app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.search("Vienna")
        TimelineScreen.assertMemoryGone(OTHER_TITLE)

        app.simulateProcessDeath()

        TimelineScreen.assertShown()
        BottomNav.assertSelected(Tab.TIMELINE)
        TimelineScreen.assertSearch("Vienna")
        TimelineScreen.assertMemoryShown(TITLE)
        TimelineScreen.assertMemoryGone(OTHER_TITLE)
    }

    @Test
    @Ignore("The Add form keeps only what its text fields show (UX-7). Turn this on with the fix.")
    fun addFormKeepsTheTitleAndLocation() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle("Picnic")
        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()
        AddScreen.assertHasLocation()

        app.simulateProcessDeath()

        AddScreen.assertShown()
        BottomNav.assertSelected(Tab.ADD)
        AddScreen.assertTitle("Picnic")
        AddScreen.assertHasLocation()
    }

    @Test
    @Ignore("The Add form forgets which memory it was editing (UX-7). Turn this on with the fix.")
    fun editKeepsEditingTheSameMemory() {
        TestData.memory(TITLE)
        app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(TITLE)
        MemoryScreen.edit()
        AddScreen.assertTitle(TITLE)
        AddScreen.assertEditing()

        app.simulateProcessDeath()

        AddScreen.assertShown()
        BottomNav.assertSelected(Tab.ADD)
        AddScreen.assertTitle(TITLE)
        AddScreen.assertEditing()
    }

    @Test
    fun settingsSignedOut() {
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertSignedOut()

        app.simulateProcessDeath()

        SettingsScreen.assertShown()
        BottomNav.assertSelected(Tab.SETTINGS)
        SettingsScreen.assertSignedOut()
    }

    @Test
    fun settingsSignedInWithBackups() {
        TestData.signIn()
        FakeBackupStorage.add(FakeAccountService.DEFAULT_EMAIL, MANUAL_BACKUP, modifiedMillis = System.currentTimeMillis())
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertSignedIn(FakeAccountService.DEFAULT_EMAIL)
        SettingsScreen.assertBackupShown("Manual Backup")

        app.simulateProcessDeath()

        SettingsScreen.assertSignedIn(FakeAccountService.DEFAULT_EMAIL)
        SettingsScreen.assertBackupShown("Manual Backup")
    }

    @Test
    @Ignore("Plain AlertDialogs don't come back when Android rebuilds the screen (UX-16). Turn this on with the fix.")
    fun restoreConfirmationStaysOpen() {
        TestData.signIn()
        FakeBackupStorage.add(FakeAccountService.DEFAULT_EMAIL, MANUAL_BACKUP, modifiedMillis = System.currentTimeMillis())
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.tapRestoreOnFirstBackup()
        Dialog.assertShown(RESTORE_TITLE)

        app.simulateProcessDeath()

        Dialog.assertShown(RESTORE_TITLE)
        Dialog.tap("Cancel")
        Dialog.assertGone(RESTORE_TITLE)
    }

    @Test
    fun memoryStaysOnTheSameMemoryAndBackStillWorks() {
        TestData.memory(OTHER_TITLE, daysAgo = 1)
        TestData.memory(TITLE, daysAgo = 5)
        app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(TITLE)
        MemoryScreen.assertShowing(TITLE)

        app.simulateProcessDeath()

        MemoryScreen.assertShowing(TITLE)
        Ui.pressBack()
        MemoryScreen.assertGone()
        TimelineScreen.assertShown()
        BottomNav.assertSelected(Tab.TIMELINE)
    }

    @Test
    fun viewerKeepsThePhotoAndBackStillWorks() {
        TestData.photo(TestData.memory(TITLE))
        app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(TITLE)
        MemoryScreen.openFirstPhoto()
        MediaViewerScreen.assertShowingPhoto()

        app.simulateProcessDeath()

        MediaViewerScreen.assertShowingPhoto()
        MemoryScreen.assertGone()
        Ui.pressBack()
        MediaViewerScreen.assertGone()
        MemoryScreen.assertShowing(TITLE)
    }

    @Test
    @Ignore("The location picker forgets the picked spot (UX-7). Turn this on with the fix.")
    fun locationPickerKeepsThePickedSpot() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()

        app.simulateProcessDeath()

        LocationPickerScreen.assertShown()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()
        AddScreen.assertShown()
        AddScreen.assertHasLocation()
    }

    @Test
    @Ignore("A date picker restored after process death ignores the picked date (UX-17). Turn this on with the fix.")
    fun datePickerStaysOpenAndStillWorks() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.openStartDatePicker()
        Dialog.assertShown(DATE_PICKER_TITLE)

        app.simulateProcessDeath()

        Dialog.assertShown(DATE_PICKER_TITLE)
        DatePickerDialog.pickDayInPreviousMonth(15)
        Dialog.tap("OK")
        Dialog.assertGone(DATE_PICKER_TITLE)
        AddScreen.assertShown()
        AddScreen.assertStartDay(15)
    }

    @Test
    fun timePickerStaysOpenAndStillWorks() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.openStartTimePicker()
        Dialog.assertShown(TIME_PICKER_TITLE)

        app.simulateProcessDeath()

        Dialog.assertShown(TIME_PICKER_TITLE)
        val hour = TimePickerDialog.pickNextHour()
        Dialog.tap("OK")
        Dialog.assertGone(TIME_PICKER_TITLE)
        AddScreen.assertShown()
        AddScreen.assertStartHour(hour)
    }

    private companion object {
        const val TITLE = "Vienna trip"
        const val OTHER_TITLE = "Lake day"
        const val MANUAL_BACKUP = "MemoryMap_Manual_Backup_20261020_101500.zip"
        const val RESTORE_TITLE = "Restore Backup"
        const val DATE_PICKER_TITLE = "Select Start Date"
        const val TIME_PICKER_TITLE = "Select Start Time"
    }
}
