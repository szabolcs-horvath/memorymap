package com.szabolcshorvath.memorymap.edge

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakeAccountService.DEFAULT_EMAIL
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage
import com.szabolcshorvath.memorymap.fakes.FakeMediaPicker
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.ColorPresetsSection
import com.szabolcshorvath.memorymap.screens.ControlNames
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.MediaViewerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.MissingPhotoSheet
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every control on every screen has a name a screen reader can read (`ControlNames`), with the screens filled in:
 * memories with photos, a video, a fragment and a missing photo, colour presets, and a signed-in account with a backup.
 */
@RunWith(AndroidJUnit4::class)
class ControlNamesTest {
    @get:Rule
    val app = MemoryMapAppRule()

    private val names = ControlNames()

    @Test
    fun onTheMapTheTimelineAndSettings() {
        TestData.markerClustering(false)
        TestData.showFragmentMarkers(true)
        TestData.colorPresets(RED, GREEN)
        // Well inside the map's starting view: a marker near its top edge sits under the filter bar.
        val trip = TestData.memory(TRIP, place = TestData.Place.CAPE_TOWN)
        TestData.photo(trip)
        TestData.fragment(trip, TestData.Place.ATHENS)
        TestData.signIn()
        FakeBackupStorage.add(DEFAULT_EMAIL, BACKUP, modifiedMillis = System.currentTimeMillis())
        app.launch()

        MapScreen.assertMarkerShown(TRIP)
        names.check("map")
        MapScreen.tapMarker(TRIP)
        MapScreen.assertOverlayShows(TRIP)
        names.check("map, with a marker's memories")
        MapScreen.toggleColorStatistics()
        MapScreen.assertColorStatisticsShown()
        names.check("map, with the colour statistics")

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(TRIP)
        names.check("timeline")

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertBackupShown(MANUAL)
        ColorPresetsSection.open()
        names.check("settings, signed in, with the colour presets open")
        ColorPresetsSection.select(GREEN)
        ColorPresetsSection.assertSlidersShow(GREEN)
        names.check("settings, editing a colour preset")

        names.assertAllNamed()
    }

    @Test
    fun onTheAddFormAndTheLocationPicker() {
        TestData.colorPresets(RED, GREEN)
        FakeMediaPicker.nextPick = listOf(TestData.galleryPhoto(Color.RED), TestData.galleryVideo(Color.BLUE))
        app.launch()
        BottomNav.open(Tab.ADD)
        names.check("empty Add form")

        AddScreen.addPhotos()
        AddScreen.assertSelectedCount(2)
        AddScreen.pickColor(GREEN)
        AddScreen.addFragment()
        AddScreen.pickFragmentColor(RED)
        names.check("Add form, with a photo, a video, a fragment and the colours open")

        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()
        names.check("location picker")

        names.assertAllNamed()
    }

    @Test
    fun onAMemoryTheMediaViewerAndTheMissingPhotoSheet() {
        val trip = TestData.memory(TRIP)
        TestData.photo(trip)
        TestData.fragment(trip, TestData.Place.VIENNA)
        val taken = TestData.photoTakenAt(Color.GREEN, secondsAfter = 30) - 30_000
        TestData.missingPhoto(TestData.memory(LOST, daysAgo = 2), taken)
        TestData.grantMediaAccess()
        FakeMediaPicker.nextPick = listOf(TestData.galleryVideo(Color.BLUE))
        app.launch()
        Flows.editMemory(TRIP)
        AddScreen.addPhotos()
        AddScreen.save()

        MemoryScreen.assertShowing(TRIP)
        names.check("memory, with a photo, a video and a fragment")

        MemoryScreen.openFirstPhoto()
        MediaViewerScreen.assertShowingPhoto()
        names.check("media viewer, photo")
        Ui.pressBack()
        MemoryScreen.openFirstVideo()
        MediaViewerScreen.assertShowingVideo()
        names.check("media viewer, video")
        Ui.pressBack()

        Flows.openMemory(LOST)
        MemoryScreen.openFirstPhoto()
        MissingPhotoSheet.assertShown()
        MissingPhotoSheet.assertSuggestions("30 sec later")
        names.check("missing photo sheet")

        names.assertAllNamed()
    }

    private companion object {
        const val TRIP = "Vienna trip"
        const val LOST = "Lost photo"
        const val BACKUP = "MemoryMap_Manual_Backup_20261020_101500.zip"
        const val MANUAL = "Manual Backup"
        val RED = Hsv(0f)
        val GREEN = Hsv(120f)
    }
}
