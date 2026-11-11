package com.szabolcshorvath.memorymap.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.DatePickerDialog
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.Flows
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
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every screen keeps what it shows when the phone is turned, and turned back. */
@RunWith(AndroidJUnit4::class)
class RotationTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun theMapTheTimelineAndSettingsSignedOut() {
        TestData.memory(TITLE)
        app.launch()
        MapScreen.assertShown()
        rotateAndBack {
            MapScreen.assertShown()
            BottomNav.assertSelected(Tab.MAP)
        }

        BottomNav.open(Tab.TIMELINE)
        rotateAndBack {
            TimelineScreen.assertShown()
            TimelineScreen.assertMemoryShown(TITLE)
            BottomNav.assertSelected(Tab.TIMELINE)
        }

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertSignedOut()
        rotateAndBack {
            SettingsScreen.assertShown()
            SettingsScreen.assertSignedOut()
        }
    }

    @Test
    fun theMapKeepsThePanelOfATappedPin() {
        TestData.memory(TITLE)
        app.launch()
        MapScreen.tapMarker(TITLE)
        MapScreen.assertOverlayShows(TITLE)

        rotateAndBack {
            MapScreen.assertOverlayShows(TITLE)
        }

        MapScreen.openFromOverlay(TITLE)
        MemoryScreen.assertShowing(TITLE)
    }

    @Test
    fun settingsSignedInWithBackups() {
        TestData.signIn()
        FakeBackupStorage.add(FakeAccountService.DEFAULT_EMAIL, MANUAL_BACKUP, modifiedMillis = System.currentTimeMillis())
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertSignedIn(FakeAccountService.DEFAULT_EMAIL)
        SettingsScreen.assertBackupShown("Manual backup")

        rotateAndBack {
            SettingsScreen.assertSignedIn(FakeAccountService.DEFAULT_EMAIL)
            SettingsScreen.assertBackupShown("Manual backup")
        }
    }

    @Test
    fun restoreConfirmationStaysOpen() {
        TestData.signIn()
        FakeBackupStorage.add(FakeAccountService.DEFAULT_EMAIL, MANUAL_BACKUP, modifiedMillis = System.currentTimeMillis())
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.tapRestoreOnFirstBackup()
        Dialog.assertShown(RESTORE_TITLE)

        rotateAndBack {
            Dialog.assertShown(RESTORE_TITLE)
        }

        Dialog.tap("Cancel")
        Dialog.assertGone(RESTORE_TITLE)
    }

    @Test
    fun aMemoryStaysOnTheSameMemoryAndTheViewerKeepsThePhoto() {
        TestData.memory("Newer", daysAgo = 1)
        TestData.photo(TestData.memory(TITLE, daysAgo = 5))
        app.launch()
        Flows.openMemory(TITLE)
        rotateAndBack {
            MemoryScreen.assertShowing(TITLE)
        }

        MemoryScreen.openFirstPhoto()
        MediaViewerScreen.assertShowingPhoto()
        rotateAndBack {
            MediaViewerScreen.assertShowingPhoto()
            MemoryScreen.assertGone()
        }

        Ui.pressBack()
        MemoryScreen.assertShowing(TITLE)
    }

    @Test
    fun locationPickerKeepsThePickedSpot() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()

        rotateAndBack {
            LocationPickerScreen.assertShown()
            LocationPickerScreen.assertLocationPicked()
        }

        LocationPickerScreen.confirm()
        AddScreen.assertHasLocation()
    }

    @Test
    fun theAddFormKeepsWhatWasTypedAndItsDateAndTimePickersStayOpenAndStillWork() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle("Picnic")
        rotateAndBack {
            AddScreen.assertShown()
            AddScreen.assertTitle("Picnic")
            BottomNav.assertSelected(Tab.ADD)
        }

        val dateBefore = AddScreen.startDateText()
        AddScreen.openStartDatePicker()
        Dialog.assertShown(DATE_PICKER_TITLE)
        rotateAndBack {
            Dialog.assertShown(DATE_PICKER_TITLE)
        }
        DatePickerDialog.pickDayInPreviousMonth(15)
        Dialog.tap("OK")
        Dialog.assertGone(DATE_PICKER_TITLE)
        AddScreen.assertShown()
        assertNotEquals("The picked date was not applied", dateBefore, AddScreen.startDateText())

        val timeBefore = AddScreen.startTimeText()
        AddScreen.openStartTimePicker()
        Dialog.assertShown(TIME_PICKER_TITLE)
        rotateAndBack {
            Dialog.assertShown(TIME_PICKER_TITLE)
        }
        TimePickerDialog.pickNextHour()
        Dialog.tap("OK")
        Dialog.assertGone(TIME_PICKER_TITLE)
        AddScreen.assertShown()
        assertNotEquals("The picked time was not applied", timeBefore, AddScreen.startTimeText())
    }

    /** Turns the phone to landscape and back, checking [expectations] in both. */
    private fun rotateAndBack(expectations: () -> Unit) {
        Ui.rotateToLandscape()
        expectations()
        Ui.rotateToPortrait()
        expectations()
    }

    private companion object {
        const val TITLE = "Vienna trip"
        const val MANUAL_BACKUP = "MemoryMap_Manual_Backup_20261020_101500.zip"
        const val RESTORE_TITLE = "Restore Backup"
        const val DATE_PICKER_TITLE = "Select start date"
        const val TIME_PICKER_TITLE = "Select start time"
    }
}
