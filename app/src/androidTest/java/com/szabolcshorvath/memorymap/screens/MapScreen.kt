package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.StaleObjectException
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate

/** The Map tab. */
object MapScreen : TabScreen {
    private const val STATS = "Color statistics"

    override fun assertShown() = Ui.assertShown("The map") { hasDescription(STATS) }

    override fun assertGone() = Ui.assertGone("The map") { hasDescription(STATS) }

    /** The card that lists the memories at the selected marker shows [title]. */
    fun assertOverlayShows(title: String) = Ui.assertShown("The map overlay with \"$title\"") {
        hasId("tv_memoryTitle") && hasText(title)
    }

    fun assertOverlayGone() = Ui.assertGone("The map overlay") { hasId("tv_memoryTitle") }

    /** The overlay card is about the place [name]: the selected marker's place, which heads the card. */
    fun assertOverlayPlace(name: String) = Ui.assertShown("The map overlay for \"$name\"") {
        hasId("tv_overlayLocationTitle") && hasText(name)
    }

    /** Opens memory [title] from the overlay card. */
    fun openFromOverlay(title: String) {
        Ui.find { hasId("tv_memoryTitle") && hasText(title) }.click()
        Ui.device.waitForIdle()
    }

    /**
     * A marker titled [title] is on the map: a memory's title, or "N memories" for several at one place. A
     * fragment's marker has its memory's title. A cluster circle is titled "N memories nearby".
     */
    fun assertMarkerShown(title: String) =
        Ui.assertShown("The marker \"$title\"", Ui.SLOW_TIMEOUT_MS) { isMarker(title) }

    fun assertMarkerGone(title: String) = Ui.assertGone("The marker \"$title\"") { isMarker(title) }

    /** Once the map has stopped moving, no marker titled [title] is on it: the camera stayed away from it. */
    fun assertMarkerStaysGone(title: String) {
        val markers = awaitMarkersSettled()
        assertTrue("The marker \"$title\" came back: $markers", !isMarkerShown(title))
    }

    /** Whether a marker titled [title] is on the map, without waiting for one. */
    fun isMarkerShown(title: String): Boolean = Ui.isShown(timeoutMs = 0) { isMarker(title) }

    /** Waits until exactly [count] markers titled [title] are on the map. */
    fun assertMarkerCount(title: String, count: Int) {
        val found = Ui.eventually(Ui.SLOW_TIMEOUT_MS, read = { mapMarkerTitles().count { it == title } }) { it == count }
        assertEquals("Markers titled \"$title\"", count, found)
    }

    /**
     * Taps marker [title] once the map has stopped moving. The marker shows before the first zoom has moved the camera
     * to the memories, and a tap read from where it was then lands on the empty map beside it, which closes the card.
     */
    fun tapMarker(title: String) {
        assertMarkerShown(title)
        var attempts = 0
        while (!tapSettledMarker(title)) {
            assertTrue("The marker \"$title\" kept changing while being tapped", ++attempts < MARKER_TAP_ATTEMPTS)
        }
        Ui.device.waitForIdle()
    }

    /** Taps marker [title] once the map has settled; false if it went away while being tapped (the map changed). */
    private fun tapSettledMarker(title: String): Boolean {
        awaitMarkersSettled()
        return try {
            Ui.find { isMarker(title) }.click()
            true
        } catch (_: StaleObjectException) {
            false
        }
    }

    fun assertClusterShown() = Ui.assertShown("A cluster of memories", Ui.SLOW_TIMEOUT_MS) { isCluster() }

    /**
     * Taps a cluster ("4 memories nearby") if the map shows one once it has stopped moving, and returns whether it
     * did. The tap zooms the map in on the cluster; this then waits until the markers have changed and stopped
     * moving again. A cluster seen while the map still zooms in can split up before it is tapped, so the map has
     * to settle before it is looked for.
     */
    fun tapClusterIfShown(): Boolean {
        var tapped: Boolean? = null
        var attempts = 0
        while (tapped == null && attempts++ < CLUSTER_TAP_ATTEMPTS) tapped = tapSettledCluster()
        return tapped == true
    }

    /**
     * Taps a cluster once the map has settled: true if it did, false if the map shows none, and null if the cluster
     * went away while being tapped (the map was still changing after all, so it should look again).
     */
    private fun tapSettledCluster(): Boolean? {
        var before = awaitMarkersSettled()
        var cluster = Ui.findOrNull(timeoutMs = 0) { isCluster() }
        if (cluster == null) {
            // The map may have started moving just as it looked settled (the first zoom, under load), and a cluster is
            // missing while the markers are drawn again: only a cluster that is still missing once the map has settled
            // again has split up.
            before = awaitMarkersSettled()
            cluster = Ui.findOrNull(timeoutMs = 0) { isCluster() }
        }
        Log.i(TAG, "Tapping a cluster: ${cluster != null}, markers $before")
        if (cluster == null) return false
        val tapped = try {
            cluster.click()
            true
        } catch (_: StaleObjectException) {
            null
        }
        if (tapped == true) {
            Ui.device.waitForIdle()
            Ui.waitUntil(CAMERA_MOVE_TIMEOUT_MS) { mapMarkers() != before }
            awaitMarkersSettled()
        }
        return tapped
    }

    /**
     * Waits until the map's markers (their descriptions and places on screen) have stayed the same for
     * [MARKERS_STILL_MS], and returns them. The camera's zoom and the clustering after it both change them.
     */
    private fun awaitMarkersSettled(): List<Pair<String, Rect>>? {
        var last = mapMarkers()
        var since = SystemClock.uptimeMillis()
        val settled = Ui.waitUntil(MARKERS_SETTLE_TIMEOUT_MS) {
            val now = mapMarkers()
            if (now == null || now != last) {
                last = now
                since = SystemClock.uptimeMillis()
            }
            SystemClock.uptimeMillis() - since >= MARKERS_STILL_MS
        }
        if (!settled) Log.w(TAG, "The markers were still changing after ${MARKERS_SETTLE_TIMEOUT_MS} ms: $last")
        return last
    }

    /** The map's markers as description and place on screen, or null when one went away while being read. */
    private fun mapMarkers(): List<Pair<String, Rect>>? = try {
        Ui.findOrNull(timeoutMs = 0) { hasDescription(GOOGLE_MAP) }?.children.orEmpty().mapNotNull { marker ->
            marker.contentDescription?.let { it to marker.visibleBounds }
        }
    } catch (_: StaleObjectException) {
        null
    }

    /**
     * Once the map has settled, each of [titles] has a marker of its own in the open part of the map, below the date
     * filter, not under it or in a cluster.
     */
    fun assertMarkersInView(titles: Collection<String>) {
        val barBottom = Ui.find { hasId(DATE_RANGE) }.visibleBounds.bottom
        fun List<Pair<String, Rect>>?.inView() = titles.filter { title ->
            this.orEmpty().any { (description, bounds) -> description.trimEnd().removeSuffix(".") == title && bounds.top >= barBottom }
        }
        val markers = Ui.eventually(Ui.SLOW_TIMEOUT_MS, read = { awaitMarkersSettled() }) { it.inView().size == titles.size }
        assertEquals("Markers below the date filter, of $markers", titles.toList(), markers.inView())
    }

    /** No markers or clusters are on the map. */
    fun assertNoMarkers() = Ui.assertGone("A marker") { mapMarkerTitle() != null }

    private fun AccessibilityNodeInfo.isCluster() = mapMarkerTitle()?.let { CLUSTER.matches(it) } == true

    private fun AccessibilityNodeInfo.isMarker(title: String) = mapMarkerTitle() == title

    private val CLUSTER = Regex("""\d+ memories nearby""")

    /** Picks a date filter from the quick filter menu ("Last 7 days", ..., "All time"). */
    fun pickQuickFilter(label: String) {
        Ui.find { hasDescription("Quick date filters") }.click()
        Ui.find { hasText(label) && !isDateRangeText() }.click()
        Ui.device.waitForIdle()
    }

    /** The date filter button shows [label]: a quick filter's name, or the picked dates. */
    fun assertFilter(label: String) = Ui.assertShown("The date filter \"$label\"") { isDateRangeText() && hasText(label) }

    // A Compose button's text is a node inside the tagged button.
    private fun AccessibilityNodeInfo.isDateRangeText() = parent?.hasId(DATE_RANGE) == true

    /** The date filter button shows the dates from [start] to [end], as the app writes them. */
    fun assertFilter(start: LocalDate, end: LocalDate) {
        assertFilter("${DateTimeFormatterUtil.date(start)} - ${DateTimeFormatterUtil.date(end)}")
    }

    /** Opens the calendar for picking the dates to show ([DateRangePickerDialog]). */
    fun openDateRangePicker() {
        Ui.find { hasId(DATE_RANGE) }.click()
        Ui.device.waitForIdle()
    }

    fun toggleColorStatistics() {
        Ui.find { hasDescription(STATS) }.click()
        Ui.device.waitForIdle()
    }

    fun assertColorStatisticsShown() = Ui.assertShown("The colour statistics") { hasId(PIE_CHART) }

    fun assertColorStatisticsGone() = Ui.assertGone("The colour statistics") { hasId(PIE_CHART) }

    /**
     * The colour statistics' chart has a slice of each of [colors] and none of [absent]. The chart is drawn, with
     * nothing a screen reader can read, so its colours are read from a screenshot, on a grid across the chart.
     */
    fun assertColorStatisticsShow(colors: List<Int>, absent: List<Int> = emptyList()) {
        val chart = Ui.find { hasId(PIE_CHART) }.visibleBounds
        val grid = (1 until CHART_GRID).flatMap { i ->
            (1 until CHART_GRID).map { j ->
                Point(chart.left + chart.width() * i / CHART_GRID, chart.top + chart.height() * j / CHART_GRID)
            }
        }
        fun List<Int>.matches() = colors.all { color -> any { ScreenColors.isClose(it, color) } } &&
            absent.none { color -> any { ScreenColors.isClose(it, color) } }
        val shown = Ui.eventually(read = { ScreenColors.at(grid) }) { it.matches() }
        assertTrue(
            "The chart shows ${shown.distinct().map(ScreenColors::hex)}, " +
                "expected ${colors.map(ScreenColors::hex)} and not ${absent.map(ScreenColors::hex)}",
            shown.matches()
        )
    }

    /**
     * The map keeps no room at its bottom: it zooms around the middle of everything under the date filter. Zooming in
     * once doubles every distance from the point it zooms around, so where marker [title] points before and after "Zoom
     * in" tells where that point is. Room kept for a panel that has closed would put it higher (and lift Google's logo).
     */
    fun assertMapKeepsNoRoomAtTheBottom(title: String) {
        val before = markerTip(title)
        Ui.find { hasDescription(ZOOM_IN) }.click()
        Ui.waitUntil(CAMERA_MOVE_TIMEOUT_MS) { mapMarkers() != null && markerTipNow(title) != before }
        val after = markerTip(title)
        val zoomsAround = 2 * before - after
        val barBottom = Ui.find { hasId(DATE_RANGE) }.visibleBounds.bottom
        val mapBottom = Ui.find { hasDescription(GOOGLE_MAP) }.visibleBounds.bottom
        val expected = (barBottom + mapBottom) / 2
        assertTrue(
            "The map zooms around y=$zoomsAround (the marker went from $before to $after), expected about $expected, " +
                "midway between the date filter ($barBottom) and the map's bottom ($mapBottom)",
            kotlin.math.abs(zoomsAround - expected) <= (mapBottom - barBottom) * CENTRE_TOLERANCE
        )
    }

    private fun markerTipNow(title: String): Int? = try {
        Ui.findOrNull(timeoutMs = 0) { isMarker(title) }?.visibleBounds?.bottom
    } catch (_: StaleObjectException) {
        null
    }

    /** Where marker [title] points (its tip's height on screen) once the map has settled. */
    fun markerTip(title: String): Int {
        awaitMarkersSettled()
        return Ui.find(Ui.SLOW_TIMEOUT_MS) { isMarker(title) }.visibleBounds.bottom
    }

    /** Once the map has settled, marker [title] points above the open card, not under it. */
    fun assertMarkerAboveTheCard(title: String) {
        val tip = markerTip(title)
        val cardText = Ui.find { hasId("tv_overlayLocationTitle") }.visibleBounds.top
        assertTrue("The marker \"$title\" points at y=$tip, under the card (its text starts at $cardText)", tip < cardText)
    }

    /** The map's toolbar (zoom in, zoom out, my location) is at the screen's right edge, not centred. */
    fun assertToolbarAtRightEdge() {
        val screenWidth = Ui.device.displayWidth
        listOf(ZOOM_IN, ZOOM_OUT, MY_LOCATION).forEach { name ->
            val bounds = Ui.find { hasDescription(name) }.visibleBounds
            assertTrue(
                "\"$name\" is at $bounds, not at the right edge of a screen $screenWidth wide",
                bounds.left > screenWidth * TOOLBAR_MIN_LEFT
            )
        }
    }

    /** Taps the "my location" button, which moves the map to where the phone is (asking for access first). */
    fun tapMyLocation() {
        Ui.find { hasDescription(MY_LOCATION) }.click()
        Ui.device.waitForIdle()
    }

    /** Taps an empty spot of the map: the top left corner, away from the markers and the buttons. */
    fun tapEmptyMap() {
        val map = Ui.find { hasDescription(GOOGLE_MAP) }.visibleBounds
        Ui.device.click(map.left + map.width() / EMPTY_SPOT_DIVISOR, map.top + map.height() / 2)
        Ui.device.waitForIdle()
    }

    private const val MY_LOCATION = "My location"
    private const val ZOOM_IN = "Zoom in"
    private const val ZOOM_OUT = "Zoom out"

    /** How far from the middle a centred marker may point, as a share of the map's open height. */
    private const val CENTRE_TOLERANCE = 0.08

    /** The toolbar's buttons start in the right fifth of the screen. */
    private const val TOOLBAR_MIN_LEFT = 0.8
    private const val DATE_RANGE = "bt_dateRange"
    private const val PIE_CHART = "pieChart"
    private const val EMPTY_SPOT_DIVISOR = 10
    private const val CHART_GRID = 12
    private const val CAMERA_MOVE_TIMEOUT_MS = 3_000L
    private const val MARKERS_SETTLE_TIMEOUT_MS = 10_000L
    private const val MARKERS_STILL_MS = 1_500L
    private const val TAG = "MapScreen"
    private const val CLUSTER_TAP_ATTEMPTS = 3
    private const val MARKER_TAP_ATTEMPTS = 3
}
