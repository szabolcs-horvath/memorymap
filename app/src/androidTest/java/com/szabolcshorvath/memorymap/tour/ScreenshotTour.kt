package com.szabolcshorvath.memorymap.tour

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.szabolcshorvath.memorymap.fakes.FakeAccountService
import com.szabolcshorvath.memorymap.fakes.FakeBackupStorage
import com.szabolcshorvath.memorymap.fakes.FakeMediaPicker
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.ColorPresetsSection
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.Keyboard
import com.szabolcshorvath.memorymap.screens.LocationAccess
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.MediaViewerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.MissingPhotoSheet
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.screens.Snackbar
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.testing.TestData.Place
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test: a walk through every screen and state that saves a screenshot of each, for reviewing the app's look as
 * a whole. It runs only when asked for, with the instrumentation argument `screenshots=true`, and is skipped
 * otherwise. Each test takes the whole walk in one configuration; the screenshots are named `<configuration>_<nn>_<step>.png`
 * and land with the failed tests' screenshots (`app/build/outputs/connected_android_test_additional_output/`). A step
 * that fails is logged and saved as `..._FAILED.png`, and the walk goes on with the next section.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule
    val app = MemoryMapAppRule()

    private var configuration = ""
    private var count = 0
    private var sideways = false

    @Before
    fun onlyWhenAsked() {
        assumeTrue("Run with screenshots=true", InstrumentationRegistry.getArguments().getString("screenshots") == "true")
    }

    @Test
    fun lightUpright() = walk("light")

    @Test
    fun darkUpright() {
        app.useDarkMode()
        walk("dark")
    }

    @Test
    fun mediumContrastUpright() = withContrast(MEDIUM_CONTRAST) { walk("medium-contrast") }

    @Test
    fun highContrastUpright() = withContrast(HIGH_CONTRAST) { walk("high-contrast") }

    @Test
    fun lightSideways() = walk("light-sideways", sideways = true)

    @Test
    fun darkSideways() {
        app.useDarkMode()
        walk("dark-sideways", sideways = true)
    }

    private fun walk(name: String, sideways: Boolean = false) {
        configuration = name
        this.sideways = sideways
        addData()
        app.launch()
        if (sideways) Ui.rotateToLandscape() else Ui.rotateToPortrait()
        section("map") { map() }
        section("timeline") { timeline() }
        section("memory") { memory() }
        section("edit") { edit() }
        section("missing photo") { missingPhoto() }
        section("add") { add() }
        section("settings") { settings() }
        section("delete") { delete() }
    }

    private fun addData() {
        TestData.colorPresets(RED, ORANGE, GREEN, BLUE, PURPLE)
        val vienna = TestData.memory(VIENNA, daysAgo = 1, place = Place.VIENNA, description = DESCRIPTION)
        TestData.photo(vienna, Color.rgb(30, 120, 200), order = 0)
        TestData.photo(vienna, Color.rgb(200, 80, 40), order = 1)
        TestData.photo(vienna, Color.rgb(40, 160, 90), order = 2)
        TestData.fragment(vienna, Place.ATHENS)
        TestData.markerColor(TestData.memory(LAKE, daysAgo = 3, place = Place.BUDAPEST), GREEN)
        TestData.markerColor(TestData.memory(PARIS, daysAgo = 12, place = Place.PARIS), BLUE)
        TestData.markerColor(TestData.trip(ROME, startedDaysAgo = 50, endedDaysAgo = 45, place = Place.ROME), ORANGE)
        TestData.markerColor(TestData.memory(MADRID, daysAgo = 200, place = Place.MADRID), PURPLE)
        val taken = TestData.photoTakenAt(Color.rgb(220, 180, 40), secondsAfter = 30) - 30_000
        TestData.missingPhoto(TestData.memory(LOST, daysAgo = 2, place = Place.CAPE_TOWN), taken, thumbnailColor = Color.rgb(220, 180, 40))
        TestData.grantMediaAccess()
        TestData.signIn()
        (1..BACKUPS).forEach { n ->
            val name = if (n % 2 == 0) "MemoryMap_Automatic_Backup_2026111${n % 10}_080000.zip" else "MemoryMap_Manual_Backup_2026111${n % 10}_101500.zip"
            FakeBackupStorage.add(FakeAccountService.DEFAULT_EMAIL, name, modifiedMillis = System.currentTimeMillis() - n * HOUR_MS)
        }
    }

    private fun map() {
        BottomNav.open(Tab.MAP)
        MapScreen.assertMarkerShown(LAKE)
        shot("map")
        // Sideways, the map opens with Europe's pins under the status bar and the filter bar: drag them down.
        if (sideways) {
            val x = Ui.device.displayWidth / 2
            val y = Ui.device.displayHeight / 3
            Ui.device.swipe(x, y, x, y * 2, PAN_STEPS)
        }
        MapScreen.tapMarker(VIENNA)
        MapScreen.assertOverlayShows(VIENNA)
        shot("map_card")
        BottomNav.tapAgain(Tab.MAP)
        MapScreen.assertOverlayGone()
        MapScreen.toggleColorStatistics()
        MapScreen.assertColorStatisticsShown()
        shot("map_color_statistics")
        MapScreen.toggleColorStatistics()
        MapScreen.openQuickFilters()
        shot("map_quick_filters")
        Ui.pressBack()
        MapScreen.openDateRangePicker()
        shot("map_date_range_picker")
        Dialog.tap("Cancel")
    }

    private fun timeline() {
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(VIENNA)
        shot("timeline")
        TimelineScreen.search("trip")
        TimelineScreen.assertMemoryShown(VIENNA)
        shot("timeline_search")
        TimelineScreen.search("Atlantis")
        TimelineScreen.assertNoMatches("Atlantis")
        shot("timeline_search_no_matches")
        TimelineScreen.search("")
        TimelineScreen.closeSearch()
    }

    private fun memory() {
        Flows.openMemory(VIENNA)
        shot("memory")
        MemoryScreen.assertPhotoCount(PHOTOS)
        shot("memory_media")
        MemoryScreen.assertFragmentCount(1)
        shot("memory_fragments")
        MemoryScreen.openMoreMenu()
        shot("memory_more_menu")
        Ui.pressBack()
        MemoryScreen.openFirstPhoto()
        MediaViewerScreen.assertShowingPhoto()
        shot("viewer_photo")
        Ui.pressBack()
    }

    private fun edit() {
        FakeMediaPicker.nextPick = listOf(TestData.galleryVideo(Color.rgb(150, 60, 170)))
        Flows.editMemory(VIENNA)
        shot("edit")
        AddScreen.addPhotos()
        AddScreen.assertSelectedCount(PHOTOS + 1)
        shot("edit_media")
        AddScreen.save()
        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertVideoCount(1)
        shot("memory_with_video")
        MemoryScreen.openFirstVideo()
        MediaViewerScreen.assertShowingVideo()
        shot("viewer_video")
        Ui.pressBack()
    }

    private fun missingPhoto() {
        Flows.openMemory(LOST)
        MemoryScreen.assertPhotoNotLoaded()
        shot("memory_missing_photo")
        MemoryScreen.openFirstPhoto()
        MissingPhotoSheet.assertSuggestions("30 sec later")
        shot("missing_photo_sheet")
        MissingPhotoSheet.pick("30 sec later")
        Dialog.assertShown(MissingPhotoSheet.USE_TITLE)
        shot("missing_photo_use_question")
        Dialog.tap("Cancel")
        MissingPhotoSheet.close()
    }

    private fun add() {
        BottomNav.open(Tab.ADD)
        shot("add")
        AddScreen.save()
        AddScreen.assertMissingTitleAndPlace()
        shot("add_missing_title_and_place")
        AddScreen.typeTitle("Picnic")
        shot("add_typing")
        // Sideways, the keyboard leaves the form no room, and Compose's scroll to an element then never returns.
        Keyboard.hide()
        AddScreen.assertPresetHighlighted(RED)
        shot("add_marker_color")
        AddScreen.addFragment()
        shot("add_fragment")
        AddScreen.openStartDatePicker()
        shot("add_date_picker")
        Dialog.tap("Cancel")
        AddScreen.openStartTimePicker()
        shot("add_time_picker")
        Dialog.tap("Cancel")
        AddScreen.openFragmentAllDayRangePicker()
        shot("add_date_range_picker")
        Dialog.tap("Cancel")
        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()
        shot("location_picker")
        LocationPickerScreen.tapMyLocation()
        LocationAccess.assertExplained()
        shot("location_access_explanation")
        LocationAccess.notNow()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        shot("location_picker_picked")
        LocationPickerScreen.confirm()
        AddScreen.assertHasLocation()
        shot("add_with_location")
        AddScreen.clear()
        Dialog.assertShown("Clear the form?")
        shot("add_clear_question")
        Dialog.tap("Clear")
    }

    private fun settings() {
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertSignedIn(FakeAccountService.DEFAULT_EMAIL)
        shot("settings")
        SettingsScreen.assertBackupShown("Automatic backup") // Saving the edit added one more: no count.
        shot("settings_backups")
        ColorPresetsSection.open()
        shot("settings_color_presets")
        ColorPresetsSection.select(GREEN)
        ColorPresetsSection.assertSlidersShow(GREEN)
        shot("settings_color_preset_selected")
        SettingsScreen.assertDefaultMapFilter("All time")
        shot("settings_map")
        SettingsScreen.openDefaultMapFilterMenu()
        shot("settings_default_filter_menu")
        Ui.pressBack()
        SettingsScreen.waitForRelinkToFinish()
        shot("settings_media")
        SettingsScreen.showAllBackups()
        shot("settings_all_backups")
        SettingsScreen.closeAllBackups()
        SettingsScreen.tapRestoreOnFirstBackup()
        shot("settings_restore_question")
        Dialog.tap("Cancel")
        SettingsScreen.signOut()
        SettingsScreen.assertSignedOut()
        shot("settings_signed_out")
    }

    private fun delete() {
        Flows.openMemory(VIENNA) // The top card: the timeline lookup doesn't scroll, and sideways only two fit.
        MemoryScreen.delete()
        shot("memory_delete_question")
        Dialog.tap("Delete")
        Snackbar.assertShown("Memory deleted")
        shot("memory_deleted_message")
    }

    /** Runs [steps]; if one fails, saves what the screen shows and goes back to the map for the next section. */
    private fun section(name: String, steps: () -> Unit) {
        try {
            steps()
        } catch (e: Throwable) {
            Log.e(TAG, "$configuration: the $name section failed", e)
            shot("${name.replace(' ', '_')}_FAILED")
            // Starts the app again, on the map, as its launcher icon does: the data stays.
            runCatching {
                app.launch()
                if (sideways) Ui.rotateToLandscape()
            }
        }
    }

    /** Saves a screenshot once the app has settled and its pictures have had time to load. */
    private fun shot(step: String) {
        repeat(SETTLE_STEPS) {
            Ui.catchUp()
            SystemClock.sleep(SETTLE_STEP_MS)
        }
        val name = "%s_%02d_%s.png".format(configuration, ++count, step)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        TestStorage().openOutputFile(name).use { screenshot.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        screenshot.recycle()
    }

    private fun withContrast(level: String, walk: () -> Unit) {
        val before = Ui.device.executeShellCommand("settings get secure $CONTRAST").trim()
        Ui.device.executeShellCommand("settings put secure $CONTRAST $level")
        try {
            walk()
        } finally {
            if (before.isEmpty() || before == "null") {
                Ui.device.executeShellCommand("settings delete secure $CONTRAST")
            } else {
                Ui.device.executeShellCommand("settings put secure $CONTRAST $before")
            }
        }
    }

    private companion object {
        const val TAG = "ScreenshotTour"
        const val VIENNA = "Vienna trip"
        const val LAKE = "Lake day"
        const val PARIS = "Paris museum"
        const val ROME = "Rome weekend"
        const val MADRID = "Madrid tapas"
        const val LOST = "Cape Town hike"
        const val DESCRIPTION = "Schönbrunn in the morning, the Naschmarkt for lunch, and the opera in the evening."
        const val PHOTOS = 3
        const val BACKUPS = 7
        const val HOUR_MS = 3_600_000L
        const val PAN_STEPS = 30
        const val SETTLE_STEPS = 6
        const val SETTLE_STEP_MS = 150L
        const val PNG_QUALITY = 100 // Ignored for PNG, which is lossless.
        const val CONTRAST = "contrast_level"
        const val MEDIUM_CONTRAST = "0.5"
        const val HIGH_CONTRAST = "1.0"
        val RED = Hsv(0f)
        val ORANGE = Hsv(30f)
        val GREEN = Hsv(120f)
        val BLUE = Hsv(240f)
        val PURPLE = Hsv(280f)
    }
}
