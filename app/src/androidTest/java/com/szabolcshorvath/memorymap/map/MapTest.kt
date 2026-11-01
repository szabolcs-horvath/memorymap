package com.szabolcshorvath.memorymap.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.DateRangePickerDialog
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.testing.TestData.Place
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateFilterOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The Map tab: markers, the overlay that lists a marker's memories, clustering, fragment markers, the date filters
 * and the colour statistics. The map opens on its default view, which shows Europe and Africa, so the memories are
 * there; tests that aren't about clustering turn it off, because at that zoom nearby cities would merge.
 */
@RunWith(AndroidJUnit4::class)
class MapTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun everyMemoryHasAMarker() {
        TestData.markerClustering(false)
        TestData.memory(MADRID_TRIP, place = Place.MADRID)
        TestData.memory(ATHENS_TRIP, place = Place.ATHENS)
        TestData.memory(PARIS_TRIP, place = Place.PARIS)

        openMap()

        MapScreen.assertMarkerShown(MADRID_TRIP)
        MapScreen.assertMarkerShown(ATHENS_TRIP)
        MapScreen.assertMarkerShown(PARIS_TRIP)
    }

    @Test
    fun tappingAMarkerListsItsMemoryWhichOpensFromThere() {
        TestData.markerClustering(false)
        TestData.memory(MADRID_TRIP, place = Place.MADRID)
        TestData.memory(ATHENS_TRIP, place = Place.ATHENS)
        openMap()

        MapScreen.tapMarker(ATHENS_TRIP)
        MapScreen.assertOverlayShows(ATHENS_TRIP)
        MapScreen.openFromOverlay(ATHENS_TRIP)

        MemoryScreen.assertShowing(ATHENS_TRIP)
    }

    @Test
    fun tappingTheMapClosesTheOverlay() {
        TestData.markerClustering(false)
        TestData.memory(ATHENS_TRIP, place = Place.ATHENS)
        openMap()
        MapScreen.tapMarker(ATHENS_TRIP)
        MapScreen.assertOverlayShows(ATHENS_TRIP)

        MapScreen.tapEmptyMap()

        MapScreen.assertOverlayGone()
    }

    @Test
    fun memoriesAtOnePlaceShareAMarker() {
        TestData.markerClustering(false)
        TestData.memory(MADRID_TRIP, place = Place.MADRID)
        TestData.memory(MADRID_AGAIN, daysAgo = 30, place = Place.MADRID)
        openMap()

        MapScreen.assertMarkerShown("2 Memories")
        MapScreen.tapMarker("2 Memories")

        MapScreen.assertOverlayShows(MADRID_TRIP)
        MapScreen.assertOverlayShows(MADRID_AGAIN)
    }

    @Test
    fun nearbyMemoriesClusterUntilClusteringIsTurnedOff() {
        // Four memories in Budapest, 50-150 metres apart: one cluster at the map's zoom. The clustering
        // library only clusters four or more. The memory in Cape Town shows the markers are drawn.
        val nearby = (1..NEARBY).map { "Budapest walk $it" }
        nearby.forEachIndexed { index, title ->
            val offset = (index + 1) * NEARBY_STEP_DEGREES
            TestData.memory(title, place = Place(Place.BUDAPEST.latitude + offset, Place.BUDAPEST.longitude, "Budapest"))
        }
        TestData.memory(CAPE_TOWN_TRIP, place = Place.CAPE_TOWN)
        openMap()
        MapScreen.assertMarkerShown(CAPE_TOWN_TRIP)
        nearby.forEach { MapScreen.assertMarkerGone(it) }

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.setMarkerClustering(false)
        BottomNav.open(Tab.MAP)

        nearby.forEach { MapScreen.assertMarkerShown(it) }
    }

    @Test
    fun fragmentMarkersShowOnlyOnceTurnedOn() {
        TestData.markerClustering(false)
        val trip = TestData.memory(EURO_TRIP, place = Place.MADRID)
        TestData.fragment(trip, Place.ATHENS)
        openMap()
        MapScreen.assertMarkerCount(EURO_TRIP, 1)

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.setShowFragmentMarkers(true)
        BottomNav.open(Tab.MAP)

        MapScreen.assertMarkerCount(EURO_TRIP, 2)
    }

    @Test
    fun quickFiltersShowTheMemoriesThatOverlapTheirRange() {
        TestData.markerClustering(false)
        TestData.memory(TODAY, daysAgo = 0, place = Place.MADRID)
        TestData.memory(TWENTY_DAYS_AGO, daysAgo = 20, place = Place.PARIS)
        TestData.memory(TWO_HUNDRED_DAYS_AGO, daysAgo = 200, place = Place.ROME)
        TestData.memory(TWO_YEARS_AGO, daysAgo = 800, place = Place.ATHENS)
        // Started 40 days ago, ended 2 days ago: it overlaps the last 7 days.
        TestData.trip(LONG_TRIP, startedDaysAgo = 40, endedDaysAgo = 2, place = Place.BUDAPEST)
        val all = listOf(TODAY, TWENTY_DAYS_AGO, TWO_HUNDRED_DAYS_AGO, TWO_YEARS_AGO, LONG_TRIP)
        openMap()
        MapScreen.assertFilter("All Time")

        expectMarkers("Last 7 Days", all, TODAY, LONG_TRIP)
        expectMarkers("Last 30 Days", all, TODAY, TWENTY_DAYS_AGO, LONG_TRIP)
        expectMarkers("Last 365 Days", all, TODAY, TWENTY_DAYS_AGO, TWO_HUNDRED_DAYS_AGO, LONG_TRIP)
        expectMarkers("All Time", all, *all.toTypedArray())

        // These depend on today's date: only today's memory and the one from two years ago are certain.
        for (label in listOf("This Week", "This Month", "This Year")) {
            MapScreen.pickQuickFilter(label)
            MapScreen.assertFilter(label)
            MapScreen.assertMarkerShown(TODAY)
            MapScreen.assertMarkerGone(TWO_YEARS_AGO)
        }
    }

    @Test
    fun pickedDatesShowOnlyTheMemoriesBetweenThem() {
        TestData.markerClustering(false)
        TestData.memory(TODAY, daysAgo = 0, place = Place.MADRID)
        TestData.memory(TWENTY_DAYS_AGO, daysAgo = 20, place = Place.PARIS)
        TestData.memory(TWO_HUNDRED_DAYS_AGO, daysAgo = 200, place = Place.ROME)
        openMap()
        val start = LocalDate.now().minusDays(30)
        val end = LocalDate.now().minusDays(10)

        MapScreen.openDateRangePicker()
        DateRangePickerDialog.pick(start, end)

        MapScreen.assertFilter(start, end)
        MapScreen.assertMarkerShown(TWENTY_DAYS_AGO)
        MapScreen.assertMarkerGone(TODAY)
        MapScreen.assertMarkerGone(TWO_HUNDRED_DAYS_AGO)
    }

    @Test
    fun theMapOpensWithTheDefaultFilterFromSettings() {
        TestData.markerClustering(false)
        TestData.defaultMapFilter(DateFilterOption.LAST_7_DAYS)
        TestData.memory(TODAY, daysAgo = 0, place = Place.MADRID)
        TestData.memory(TWENTY_DAYS_AGO, daysAgo = 20, place = Place.ATHENS)

        openMap()

        MapScreen.assertFilter("Last 7 Days")
        MapScreen.assertMarkerShown(TODAY)
        MapScreen.assertMarkerGone(TWENTY_DAYS_AGO)
    }

    @Test
    fun colorStatisticsOpenAndCloseWhenTheMapIsTapped() {
        TestData.memory(MADRID_TRIP, place = Place.MADRID)
        openMap()

        MapScreen.toggleColorStatistics()
        MapScreen.assertColorStatisticsShown()

        MapScreen.tapEmptyMap()
        MapScreen.assertColorStatisticsGone()
    }

    @Test
    fun colorStatisticsShowTheColorsOfTheMemoriesOnTheMap() {
        TestData.markerClustering(false)
        TestData.memory(TODAY, daysAgo = 0, place = Place.MADRID) // Red, as every memory starts.
        TestData.markerColor(TestData.memory(TWENTY_DAYS_AGO, daysAgo = 20, place = Place.PARIS), BLUE)
        openMap()

        MapScreen.toggleColorStatistics()
        MapScreen.assertColorStatisticsShow(listOf(color(RED), color(BLUE)))

        MapScreen.pickQuickFilter("Last 7 Days")
        MapScreen.assertColorStatisticsShow(listOf(color(RED)), absent = listOf(color(BLUE)))
    }

    private fun color(hsv: Hsv) = ColorUtil.hsvToColor(hsv.hue, hsv.saturation, hsv.brightness)

    private fun openMap() {
        app.launch()
        MapScreen.assertShown()
    }

    private fun expectMarkers(filter: String, all: List<String>, vararg shown: String) {
        MapScreen.pickQuickFilter(filter)
        MapScreen.assertFilter(filter)
        shown.forEach { MapScreen.assertMarkerShown(it) }
        (all - shown.toSet()).forEach { MapScreen.assertMarkerGone(it) }
        Ui.device.waitForIdle()
    }

    private companion object {
        const val MADRID_TRIP = "Madrid trip"
        const val MADRID_AGAIN = "Back in Madrid"
        const val ATHENS_TRIP = "Athens trip"
        const val PARIS_TRIP = "Paris trip"
        const val CAPE_TOWN_TRIP = "Cape Town trip"
        const val EURO_TRIP = "Euro trip"
        const val TODAY = "Today"
        const val TWENTY_DAYS_AGO = "Twenty days ago"
        const val TWO_HUNDRED_DAYS_AGO = "Two hundred days ago"
        const val TWO_YEARS_AGO = "Two years ago"
        const val LONG_TRIP = "Long trip"
        const val NEARBY = 4
        val RED = Hsv(0f)
        val BLUE = Hsv(240f)
        const val NEARBY_STEP_DEGREES = 0.0005 // About 55 metres.
    }
}
