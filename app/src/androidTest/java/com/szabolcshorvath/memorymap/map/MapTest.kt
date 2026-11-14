package com.szabolcshorvath.memorymap.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.DateRangePickerDialog
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.Flows
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun everyMemoryHasAMarkerThatListsItsMemoryWhichOpensFromThere() {
        TestData.markerClustering(false)
        TestData.memory(MADRID_TRIP, place = Place.MADRID)
        TestData.memory(ATHENS_TRIP, place = Place.ATHENS)
        TestData.memory(PARIS_TRIP, place = Place.PARIS)

        openMap()

        MapScreen.assertMarkerShown(MADRID_TRIP)
        MapScreen.assertMarkerShown(ATHENS_TRIP)
        MapScreen.assertMarkerShown(PARIS_TRIP)

        // A tap on a marker zooms in on it, so the later taps are on the same marker.
        MapScreen.tapMarker(ATHENS_TRIP)
        MapScreen.assertOverlayShows(ATHENS_TRIP)
        MapScreen.tapEmptyMap()
        MapScreen.assertOverlayGone()

        MapScreen.tapMarker(ATHENS_TRIP)
        MapScreen.assertOverlayShows(ATHENS_TRIP)
        MapScreen.openFromOverlay(ATHENS_TRIP)
        MemoryScreen.assertShowing(ATHENS_TRIP)
    }

    @Test
    fun memoriesAtOnePlaceShareAMarker() {
        TestData.markerClustering(false)
        TestData.memory(MADRID_TRIP, place = Place.MADRID)
        TestData.memory(MADRID_AGAIN, daysAgo = 30, place = Place.MADRID)
        openMap()

        MapScreen.assertMarkerShown("2 memories")
        MapScreen.tapMarker("2 memories")

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
        MapScreen.assertClusterShown()
        nearby.forEach { MapScreen.assertMarkerGone(it) }

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.setMarkerClustering(false)
        BottomNav.open(Tab.MAP)

        nearby.forEach { MapScreen.assertMarkerShown(it) }
    }

    @Test
    fun tappingAClusterZoomsInUntilItsMemoriesShow() {
        // Four memories north of Budapest, about 30 km apart: one cluster on the map's first view of Europe, which
        // splits up a few zoom levels in. Zoomed in on them, Cape Town is off the map.
        val spread = (1..NEARBY).map { "Danube bend $it" }
        spread.forEachIndexed { index, title ->
            val offset = (index + 1) * SPREAD_STEP_DEGREES
            TestData.memory(title, place = Place(Place.BUDAPEST.latitude + offset, Place.BUDAPEST.longitude, "Hungary"))
        }
        TestData.memory(CAPE_TOWN_TRIP, place = Place.CAPE_TOWN)
        openMap()
        MapScreen.assertMarkerShown(CAPE_TOWN_TRIP)
        MapScreen.assertClusterShown()

        var taps = 0
        while (taps < MAX_CLUSTER_TAPS && MapScreen.tapClusterIfShown()) {
            taps++
        }

        MapScreen.assertMarkerGone(CAPE_TOWN_TRIP)
        assertTrue("No memory of the cluster showed after $taps taps", spread.any { MapScreen.isMarkerShown(it) })
    }

    @Test
    fun aMapWithoutMemoriesHasNoMarkers() {
        openMap()

        MapScreen.assertNoMarkers()
        MapScreen.toggleColorStatistics()
        MapScreen.assertColorStatisticsShown()
    }

    /**
     * The map opens showing all its pins, and turning fragments on doesn't move it, so a memory far away keeps the
     * fragment's place (Rome, between the two) in view.
     */
    @Test
    fun fragmentMarkersShowOnlyOnceTurnedOn() {
        TestData.markerClustering(false)
        val trip = TestData.memory(EURO_TRIP, place = Place.MADRID)
        TestData.fragment(trip, Place.ROME)
        TestData.memory(CAPE_TOWN_TRIP, place = Place.CAPE_TOWN)
        openMap()
        MapScreen.assertMarkerCount(EURO_TRIP, 1)

        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.setShowFragmentMarkers(true)
        BottomNav.open(Tab.MAP)

        MapScreen.assertMarkerCount(EURO_TRIP, 2)
    }

    /** The map stays alive while another tab is shown, and updates only the markers of what changed there. */
    @Test
    fun markersFollowARenameAndADeleteMadeOnOtherTabs() {
        TestData.markerClustering(false)
        TestData.showFragmentMarkers(true)
        val trip = TestData.memory(EURO_TRIP, place = Place.MADRID)
        TestData.fragment(trip, Place.ATHENS)
        TestData.memory(PARIS_TRIP, place = Place.PARIS)
        TestData.memory(CAPE_TOWN_TRIP, place = Place.CAPE_TOWN)
        openMap()
        MapScreen.assertMarkerCount(EURO_TRIP, 2)
        MapScreen.assertMarkerShown(PARIS_TRIP)

        Flows.editMemory(EURO_TRIP)
        AddScreen.typeTitle(GRAND_TOUR)
        AddScreen.save()
        MemoryScreen.assertShowing(GRAND_TOUR)
        BottomNav.open(Tab.MAP)

        // The fragment's marker takes its memory's new title too.
        MapScreen.assertMarkerCount(GRAND_TOUR, 2)
        MapScreen.assertMarkerCount(EURO_TRIP, 0)

        Flows.openMemory(PARIS_TRIP)
        MemoryScreen.delete()
        Dialog.tap("Delete")
        BottomNav.open(Tab.MAP)

        MapScreen.assertMarkerGone(PARIS_TRIP)
        MapScreen.assertMarkerShown(CAPE_TOWN_TRIP)
        MapScreen.assertMarkerCount(GRAND_TOUR, 2)
    }

    @Test
    fun aFragmentsShowOnMapOpensTheMapAtTheFragment() {
        TestData.markerClustering(false)
        TestData.showFragmentMarkers(true)
        val trip = TestData.memory(EURO_TRIP, place = Place.MADRID)
        TestData.fragment(trip, Place.ATHENS)
        app.launch()
        Flows.openMemory(EURO_TRIP)

        MemoryScreen.showFragmentOnMap()

        MapScreen.assertShown()
        BottomNav.assertSelected(Tab.MAP)
        // The fragment's marker in Athens is selected, not the memory's own in Madrid, which is off the zoomed-in map.
        MapScreen.assertOverlayPlace(Place.ATHENS.name!!)
        MapScreen.assertOverlayShows(EURO_TRIP)
        MapScreen.assertMarkerCount(EURO_TRIP, 1)
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
        MapScreen.assertFilter("All time")

        expectMarkers("Last 7 days", all, TODAY, LONG_TRIP)
        expectMarkers("Last 30 days", all, TODAY, TWENTY_DAYS_AGO, LONG_TRIP)
        expectMarkers("Last 365 days", all, TODAY, TWENTY_DAYS_AGO, TWO_HUNDRED_DAYS_AGO, LONG_TRIP)
        expectMarkers("All time", all, *all.toTypedArray())

        // These depend on today's date: only today's memory and the one from two years ago are certain.
        for (label in listOf("This week", "This month", "This year")) {
            MapScreen.pickQuickFilter(label)
            MapScreen.assertFilter(label)
            MapScreen.assertMarkerShown(TODAY)
            MapScreen.assertMarkerGone(TWO_YEARS_AGO)
        }

        // Picked dates: from 30 to 10 days ago.
        val start = LocalDate.now().minusDays(30)
        val end = LocalDate.now().minusDays(10)
        MapScreen.openDateRangePicker()
        DateRangePickerDialog.pick(start, end)
        MapScreen.assertFilter(start, end)
        expectMarkersNow(all, TWENTY_DAYS_AGO, LONG_TRIP)
    }

    @Test
    fun theMapOpensWithTheDefaultFilterFromSettings() {
        TestData.markerClustering(false)
        TestData.defaultMapFilter(DateFilterOption.LAST_7_DAYS)
        TestData.memory(TODAY, daysAgo = 0, place = Place.MADRID)
        TestData.memory(TWENTY_DAYS_AGO, daysAgo = 20, place = Place.ATHENS)

        openMap()

        MapScreen.assertFilter("Last 7 days")
        MapScreen.assertMarkerShown(TODAY)
        MapScreen.assertMarkerGone(TWENTY_DAYS_AGO)
    }

    @Test
    fun colorStatisticsShowTheColorsOfTheMemoriesOnTheMapUntilTheMapIsTapped() {
        TestData.markerClustering(false)
        TestData.memory(TODAY, daysAgo = 0, place = Place.MADRID) // Red, as every memory starts.
        TestData.markerColor(TestData.memory(TWENTY_DAYS_AGO, daysAgo = 20, place = Place.PARIS), BLUE)
        openMap()

        MapScreen.toggleColorStatistics()
        MapScreen.assertColorStatisticsShown()
        MapScreen.assertColorStatisticsShow(listOf(color(RED), color(BLUE)))

        MapScreen.pickQuickFilter("Last 7 days")
        MapScreen.assertColorStatisticsShow(listOf(color(RED)), absent = listOf(color(BLUE)))

        MapScreen.tapEmptyMap()
        MapScreen.assertColorStatisticsGone()
    }

    /**
     * Upright and sideways, a tapped marker shows above the card, the map's buttons stay at the right edge, and closing
     * the card leaves the map where it was, with no room kept at its bottom.
     */
    @Test
    fun aTappedMarkerShowsAboveTheCardWithTheButtonsAtTheRightEdgeUprightAndSideways() {
        TestData.markerClustering(false)
        TestData.memory(ATHENS_TRIP, place = Place.ATHENS)
        openMap()
        MapScreen.assertToolbarAtRightEdge()

        // Upright, the map keeps room for the card (it covers Google's logo) and gives all of it back once it closes,
        // without the map jumping.
        tappedMarkerShowsAboveTheCardAndStaysWhenItCloses()

        // Sideways the card is beside Google's logo, so the map keeps no room for it: nothing moves when it closes.
        Ui.rotateToLandscape()
        tappedMarkerShowsAboveTheCardAndStaysWhenItCloses()
        Ui.rotateToPortrait()
    }

    /** Taps Athens's marker, checks it and the map's buttons against the card, and closes the card. */
    private fun tappedMarkerShowsAboveTheCardAndStaysWhenItCloses() {
        MapScreen.tapMarker(ATHENS_TRIP)
        MapScreen.assertOverlayShows(ATHENS_TRIP)
        MapScreen.assertToolbarAtRightEdge()
        MapScreen.assertMarkerAboveTheCard(ATHENS_TRIP)
        val tip = MapScreen.markerTip(ATHENS_TRIP)

        MapScreen.tapEmptyMap()
        MapScreen.assertOverlayGone()
        assertEquals("Where the marker points", tip, MapScreen.markerTip(ATHENS_TRIP))
        MapScreen.assertMapKeepsNoRoomAtTheBottom(ATHENS_TRIP)
    }

    @Test
    fun tappingTheMapTabClosesAMemoryThenTheOpenCardOrTheColorStatisticsThenShowsAllTheMemories() {
        TestData.markerClustering(false)
        TestData.memory(MADRID_TRIP, place = Place.MADRID)
        TestData.memory(ATHENS_TRIP, place = Place.ATHENS)
        openMap()
        // The tap zooms in on Athens, so Madrid is off screen.
        MapScreen.tapMarker(ATHENS_TRIP)
        MapScreen.assertOverlayShows(ATHENS_TRIP)
        MapScreen.assertMarkerGone(MADRID_TRIP)

        // Over a memory opened from the card, the Map tab closes only the memory: the card stays open, on Athens.
        MapScreen.openFromOverlay(ATHENS_TRIP)
        MemoryScreen.assertShowing(ATHENS_TRIP)
        BottomNav.select(Tab.MAP)
        MapScreen.assertShown()
        MapScreen.assertOverlayShows(ATHENS_TRIP)
        MapScreen.assertMarkerStaysGone(MADRID_TRIP)

        BottomNav.tapAgain(Tab.MAP)

        // The first tap only closes the card: the map stays on Athens.
        MapScreen.assertOverlayGone()
        MapScreen.assertMarkerStaysGone(MADRID_TRIP)

        BottomNav.tapAgain(Tab.MAP)

        // With nothing open, and no location access, the map shows all the memories again.
        MapScreen.assertMarkerShown(MADRID_TRIP)
        MapScreen.assertMarkerShown(ATHENS_TRIP)

        // The colour statistics close the same way, and the map stays as it was.
        MapScreen.toggleColorStatistics()
        MapScreen.assertColorStatisticsShown()

        BottomNav.tapAgain(Tab.MAP)

        MapScreen.assertColorStatisticsGone()
        MapScreen.assertMarkerShown(MADRID_TRIP)
        MapScreen.assertMarkerShown(ATHENS_TRIP)
    }

    private fun color(hsv: Hsv) = ColorUtil.hsvToColor(hsv.hue, hsv.saturation, hsv.brightness)

    private fun openMap() {
        app.launch()
        MapScreen.assertShown()
    }

    private fun expectMarkers(filter: String, all: List<String>, vararg shown: String) {
        MapScreen.pickQuickFilter(filter)
        MapScreen.assertFilter(filter)
        expectMarkersNow(all, *shown)
    }

    /** Of [all] the memories, only those [shown] have a marker. */
    private fun expectMarkersNow(all: List<String>, vararg shown: String) {
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
        const val GRAND_TOUR = "Grand tour"
        const val TODAY = "Today"
        const val TWENTY_DAYS_AGO = "Twenty days ago"
        const val TWO_HUNDRED_DAYS_AGO = "Two hundred days ago"
        const val TWO_YEARS_AGO = "Two years ago"
        const val LONG_TRIP = "Long trip"
        const val NEARBY = 4
        val RED = Hsv(0f)
        val BLUE = Hsv(240f)
        const val NEARBY_STEP_DEGREES = 0.0005 // About 55 metres.
        const val SPREAD_STEP_DEGREES = 0.27 // About 30 km.
        const val MAX_CLUSTER_TAPS = 12
    }
}
