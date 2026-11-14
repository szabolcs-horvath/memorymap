package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue

/**
 * The location picker opened from the Add form. Its own controls are found through [ComposeUi]; the map and its
 * markers are Google's view, read through [Ui] and screenshots.
 */
object LocationPickerScreen {
    private const val CONFIRM = "Use this location"
    private const val SEARCH = "Search for a place"
    private const val CONFIRM_BUTTON = "bt_confirm"
    private const val MAX_TAPS = 3
    private const val MARKER_TIMEOUT_MS = 5_000L
    private const val DEFAULT_ACROSS = 0.5f
    private const val DEFAULT_DOWN = 0.25f

    fun assertShown() {
        ComposeUi.waitFor("The location picker", hasText(CONFIRM))
    }

    /**
     * Waits until the map has drawn what it shows and stopped loading. Until the map has fetched what it needs from
     * Google it shows one plain colour, and without a network it may never get further. The map draws without
     * anything a screen reader can read, so this reads its colours from screenshots, on a grid across its middle (away
     * from the search bar above it and the toolbar and panel below it): they must show more than one colour (land and sea)
     * and stay the same between two reads.
     */
    fun awaitMapDrawn() {
        val map = Ui.find { hasDescription(GOOGLE_MAP) }.visibleBounds
        val grid = (0 until DRAWN_GRID).flatMap { i ->
            (0 until DRAWN_GRID).map { j ->
                Point(
                    map.left + (map.width() * (DRAWN_LEFT + DRAWN_WIDTH * i / (DRAWN_GRID - 1))).toInt(),
                    map.top + (map.height() * (DRAWN_TOP + DRAWN_HEIGHT * j / (DRAWN_GRID - 1))).toInt()
                )
            }
        }
        var previous = emptyList<Int>()
        fun List<Int>.isDrawn() = any { !ScreenColors.isClose(it, first()) }
        fun List<Int>.isSettled() = size == previous.size && indices.all { ScreenColors.isClose(this[it], previous[it]) }
        val shown = Ui.eventually(Ui.SLOW_TIMEOUT_MS, DRAWN_POLL_MS, read = { ScreenColors.at(grid) }) { colors ->
            (colors.isDrawn() && colors.isSettled()).also { previous = colors }
        }
        assertTrue(
            "The location picker's map has not drawn anything: it shows only ${shown.distinct().map(ScreenColors::hex)}",
            shown.isDrawn()
        )
    }

    fun assertGone() = ComposeUi.assertGone("The location picker", hasText(CONFIRM))

    /**
     * Taps the map at [across] of its width and [down] of its height, which picks that spot: its marker replaces the
     * one shown before. By default a quarter of the way down the middle, not the centre: a spot picked before (or a
     * fragment's inherited place) sits there, and a tap on its marker picks nothing. A busy emulator can deliver the
     * end of a tap so late that the map takes it for a long press and ignores it, so this taps again until a new
     * marker shows (a few times).
     */
    fun tapMap(across: Float = DEFAULT_ACROSS, down: Float = DEFAULT_DOWN) {
        val bounds = Ui.find { hasDescription(GOOGLE_MAP) }.visibleBounds
        val before = mapMarkerTitles()
        repeat(MAX_TAPS) {
            Ui.device.click(bounds.left + (bounds.width() * across).toInt(), bounds.top + (bounds.height() * down).toInt())
            Ui.device.waitForIdle()
            if (Ui.waitUntil(MARKER_TIMEOUT_MS) { mapMarkerTitles().any { it !in before } }) return
        }
    }

    /**
     * Waits until a spot is picked: its marker shows on the map. The marker is named after the place, or after its
     * coordinates when the address lookup finds nothing (as on emulators without network to Google).
     */
    fun assertLocationPicked() =
        assertTrue("A picked location is not shown", Ui.waitUntil(Ui.SLOW_TIMEOUT_MS) { mapMarkerTitles().isNotEmpty() })

    /** Taps "Use this location" once it works: it is turned off while the picked spot's address is looked up. */
    fun confirm() {
        ComposeUi.waitFor("The Use this location button, enabled,", hasTestTag(CONFIRM_BUTTON) and isEnabled()).performClick()
    }

    /** Taps the search bar, which opens the place search (the fake one in the tests answers at once). */
    fun search() {
        ComposeUi.waitFor("The search bar", hasText(SEARCH)).performClick()
    }

    /** The spot named [name] is picked: its marker shows on the map. */
    fun assertPicked(name: String) = assertTrue(
        "The place \"$name\" is not picked",
        Ui.waitUntil(Ui.SLOW_TIMEOUT_MS) { name in mapMarkerTitles() }
    )

    /** Taps the "my location" button, which picks where the phone is (asking for access first). */
    fun tapMyLocation() {
        ComposeUi.waitFor("The my location button", hasContentDescription(MY_LOCATION)).performClick()
    }

    /** No spot is picked: no marker shows. It waits a moment, in case one is on its way. */
    fun assertNothingPicked() =
        assertTrue("A location was picked", !Ui.waitUntil(NOTHING_PICKED_WAIT_MS) { mapMarkerTitles().isNotEmpty() })

    private const val MY_LOCATION = "My location"
    private const val DRAWN_GRID = 5
    private const val DRAWN_LEFT = 0.1f
    private const val DRAWN_WIDTH = 0.7f
    private const val DRAWN_TOP = 0.2f
    private const val DRAWN_HEIGHT = 0.5f
    private const val DRAWN_POLL_MS = 500L
    private const val NOTHING_PICKED_WAIT_MS = 3_000L
}
