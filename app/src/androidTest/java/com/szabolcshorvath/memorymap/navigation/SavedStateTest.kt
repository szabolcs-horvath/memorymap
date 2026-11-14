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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen keeps what it shows when the phone is turned and turned back, and when Android killed the app in the
 * background and the user returns to it ([MemoryMapAppRule.simulateProcessDeath]). Each state goes through both
 * ([keeps]), so a screen's setup is paid for once.
 */
@RunWith(AndroidJUnit4::class)
class SavedStateTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun theMapTheTimelineWithItsSearchAndSettingsSignedOut() {
        TestData.memory(OTHER_TITLE, daysAgo = 1)
        TestData.memory(TITLE, daysAgo = 5)
        app.launch()
        MapScreen.assertShown()
        keeps {
            MapScreen.assertShown()
            BottomNav.assertSelected(Tab.MAP)
        }

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.search("Vienna")
        TimelineScreen.assertMemoryGone(OTHER_TITLE)
        TimelineScreen.closeSearch()
        keeps {
            TimelineScreen.assertShown()
            BottomNav.assertSelected(Tab.TIMELINE)
            TimelineScreen.assertSearch("Vienna")
            TimelineScreen.assertMemoryShown(TITLE)
            TimelineScreen.assertMemoryGone(OTHER_TITLE)
        }

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertSignedOut()
        keeps {
            SettingsScreen.assertShown()
            BottomNav.assertSelected(Tab.SETTINGS)
            SettingsScreen.assertSignedOut()
        }
    }

    /** Only through a rotation: the open panel is the map's own state, which a process death doesn't keep. */
    @Test
    fun theMapKeepsThePanelOfATappedPinWhenTurned() {
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
    fun settingsSignedInWithBackupsAndTheRestoreQuestion() {
        TestData.signIn()
        FakeBackupStorage.add(FakeAccountService.DEFAULT_EMAIL, MANUAL_BACKUP, modifiedMillis = System.currentTimeMillis())
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertSignedIn(FakeAccountService.DEFAULT_EMAIL)
        SettingsScreen.assertBackupShown("Manual backup")
        keeps {
            SettingsScreen.assertSignedIn(FakeAccountService.DEFAULT_EMAIL)
            SettingsScreen.assertBackupShown("Manual backup")
        }

        SettingsScreen.tapRestoreOnFirstBackup()
        Dialog.assertShown(RESTORE_TITLE)
        keeps {
            Dialog.assertShown(RESTORE_TITLE)
        }

        Dialog.tap("Cancel")
        Dialog.assertGone(RESTORE_TITLE)
    }

    @Test
    fun aMemoryAndTheViewerStayAsTheyWereAndBackStillWorks() {
        TestData.memory(OTHER_TITLE, daysAgo = 1)
        TestData.photo(TestData.memory(TITLE, daysAgo = 5))
        app.launch()
        Flows.openMemory(TITLE)
        keeps {
            MemoryScreen.assertShowing(TITLE)
        }

        MemoryScreen.openFirstPhoto()
        MediaViewerScreen.assertShowingPhoto()
        keeps {
            MediaViewerScreen.assertShowingPhoto()
            MemoryScreen.assertGone()
        }

        Ui.pressBack()
        MediaViewerScreen.assertGone()
        MemoryScreen.assertShowing(TITLE)
        Ui.pressBack()
        MemoryScreen.assertGone()
        TimelineScreen.assertShown()
        BottomNav.assertSelected(Tab.TIMELINE)
    }

    @Test
    fun theAddFormKeepsWhatWasTypedAndTheLocationPickerItsSpot() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle("Picnic")
        keeps {
            AddScreen.assertShown()
            BottomNav.assertSelected(Tab.ADD)
            AddScreen.assertTitle("Picnic")
        }

        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        keeps {
            LocationPickerScreen.assertShown()
            LocationPickerScreen.assertLocationPicked()
        }

        LocationPickerScreen.confirm()
        AddScreen.assertHasLocation()
        keeps {
            AddScreen.assertShown()
            AddScreen.assertTitle("Picnic")
            AddScreen.assertHasLocation()
        }
    }

    @Test
    fun anEditKeepsEditingTheSameMemory() {
        TestData.memory(TITLE)
        app.launch()
        Flows.editMemory(TITLE)

        keeps {
            AddScreen.assertShown()
            BottomNav.assertSelected(Tab.ADD)
            AddScreen.assertTitle(TITLE)
            AddScreen.assertEditing()
        }
    }

    @Test
    fun theDateAndTimePickersStayOpenAndStillWork() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.openStartDatePicker()
        Dialog.assertShown(DATE_PICKER_TITLE)
        keeps {
            Dialog.assertShown(DATE_PICKER_TITLE)
        }
        DatePickerDialog.pickDayInPreviousMonth(15)
        Dialog.tap("OK")
        Dialog.assertGone(DATE_PICKER_TITLE)
        AddScreen.assertShown()
        AddScreen.assertStartDay(15)

        AddScreen.openStartTimePicker()
        Dialog.assertShown(TIME_PICKER_TITLE)
        keeps {
            Dialog.assertShown(TIME_PICKER_TITLE)
        }
        val hour = TimePickerDialog.pickNextHour()
        Dialog.tap("OK")
        Dialog.assertGone(TIME_PICKER_TITLE)
        AddScreen.assertShown()
        AddScreen.assertStartHour(hour)
    }

    /**
     * Turns the phone to landscape and back, then lets Android kill the app in the background and returns to it,
     * checking [expectations] after each.
     */
    private fun keeps(expectations: () -> Unit) {
        rotateAndBack(expectations)
        app.simulateProcessDeath()
        expectations()
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
        const val OTHER_TITLE = "Lake day"
        const val MANUAL_BACKUP = "MemoryMap_Manual_Backup_20261020_101500.zip"
        const val RESTORE_TITLE = "Restore Backup"
        const val DATE_PICKER_TITLE = "Select start date"
        const val TIME_PICKER_TITLE = "Select start time"
    }
}
