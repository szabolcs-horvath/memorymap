package com.szabolcshorvath.memorymap.screens

import org.junit.Assert.assertTrue

/** The location picker opened from the Add form. */
object LocationPickerScreen {
    private const val CONFIRM = "Confirm Location"
    private const val MAX_TAPS = 3
    private const val MARKER_TIMEOUT_MS = 5_000L
    private const val DEFAULT_ACROSS = 0.5f
    private const val DEFAULT_DOWN = 0.25f

    fun assertShown() = Ui.assertShown("The location picker") { hasText(CONFIRM) }

    fun assertGone() = Ui.assertGone("The location picker") { hasText(CONFIRM) }

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
     * Waits until a spot is picked: its marker shows on the map. The marker is named after the place, or "Selected
     * Location" when the address lookup finds nothing (as on emulators without network to Google).
     */
    fun assertLocationPicked() =
        assertTrue("A picked location is not shown", Ui.waitUntil(Ui.SLOW_TIMEOUT_MS) { mapMarkerTitles().isNotEmpty() })

    /** Taps "Confirm Location" once it works: it is turned off while the picked spot's address is looked up. */
    fun confirm() {
        Ui.find { hasText(CONFIRM) && isEnabled }.click()
        Ui.device.waitForIdle()
    }

    /** Taps "Search Location", which opens the place search (the fake one in the tests answers at once). */
    fun search() {
        Ui.find { hasText("Search Location") }.click()
        Ui.device.waitForIdle()
    }

    /** The spot named [name] is picked: its marker shows on the map. */
    fun assertPicked(name: String) = assertTrue(
        "The place \"$name\" is not picked",
        Ui.waitUntil(Ui.SLOW_TIMEOUT_MS) { name in mapMarkerTitles() }
    )

    /** Taps the "my location" button, which picks where the phone is (asking for access first). */
    fun tapMyLocation() {
        Ui.find { hasDescription(MY_LOCATION) }.click()
        Ui.device.waitForIdle()
    }

    /** No spot is picked: no marker shows. It waits a moment, in case one is on its way. */
    fun assertNothingPicked() =
        assertTrue("A location was picked", !Ui.waitUntil(NOTHING_PICKED_WAIT_MS) { mapMarkerTitles().isNotEmpty() })

    private const val MY_LOCATION = "My location"
    private const val NOTHING_PICKED_WAIT_MS = 3_000L
}
