package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.view.accessibility.AccessibilityNodeInfo
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
     * A marker titled [title] is on the map: a memory's title, or "N Memories" for several at one place. A
     * fragment's marker has its memory's title. A cluster circle is titled "N memories nearby".
     */
    fun assertMarkerShown(title: String) =
        Ui.assertShown("The marker \"$title\"", Ui.SLOW_TIMEOUT_MS) { isMarker(title) }

    fun assertMarkerGone(title: String) = Ui.assertGone("The marker \"$title\"") { isMarker(title) }

    /** Whether a marker titled [title] is on the map, without waiting for one. */
    fun isMarkerShown(title: String): Boolean = Ui.isShown(timeoutMs = 0) { isMarker(title) }

    /** Waits until exactly [count] markers titled [title] are on the map. */
    fun assertMarkerCount(title: String, count: Int) {
        val found = Ui.eventually(Ui.SLOW_TIMEOUT_MS, read = { mapMarkerTitles().count { it == title } }) { it == count }
        assertEquals("Markers titled \"$title\"", count, found)
    }

    fun tapMarker(title: String) {
        Ui.find(Ui.SLOW_TIMEOUT_MS) { isMarker(title) }.click()
        Ui.device.waitForIdle()
    }

    /** Whether a cluster ("4 memories nearby") is on the map. */
    fun isClusterShown(timeoutMs: Long = 0): Boolean = Ui.isShown(timeoutMs) { isCluster() }

    fun assertClusterShown() = Ui.assertShown("A cluster of memories", Ui.SLOW_TIMEOUT_MS) { isCluster() }

    /** Taps a cluster, which zooms the map in on it, and waits until the map shows other markers. */
    fun tapCluster() {
        val before = mapMarkerTitles()
        Ui.find { isCluster() }.click()
        Ui.device.waitForIdle()
        Ui.waitUntil(CAMERA_MOVE_TIMEOUT_MS) { mapMarkerTitles() != before }
    }

    /** No markers or clusters are on the map. */
    fun assertNoMarkers() = Ui.assertGone("A marker") { mapMarkerTitle() != null }

    private fun AccessibilityNodeInfo.isCluster() = mapMarkerTitle()?.let { CLUSTER.matches(it) } == true

    private fun AccessibilityNodeInfo.isMarker(title: String) = mapMarkerTitle() == title

    private val CLUSTER = Regex("""\d+ memories nearby""")

    /** Picks a date filter from the quick filter menu ("Last 7 Days", ..., "All Time"). */
    fun pickQuickFilter(label: String) {
        Ui.find { hasDescription("Quick date filters") }.click()
        Ui.find { hasText(label) && !hasId(DATE_RANGE) }.click()
        Ui.device.waitForIdle()
    }

    /** The date filter button shows [label]: a quick filter's name, or the picked dates. */
    fun assertFilter(label: String) = Ui.assertShown("The date filter \"$label\"") { hasId(DATE_RANGE) && hasText(label) }

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
    private const val DATE_RANGE = "bt_dateRange"
    private const val PIE_CHART = "pieChart"
    private const val EMPTY_SPOT_DIVISOR = 10
    private const val CHART_GRID = 12
    private const val CAMERA_MOVE_TIMEOUT_MS = 3_000L
}
