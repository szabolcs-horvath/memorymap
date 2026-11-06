package com.szabolcshorvath.memorymap.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class CoordinatesTest {

    @Test
    fun `north and east are shown with four decimals`() {
        assertEquals("47.4979° N, 19.0402° E", Coordinates.format(47.49791, 19.04023, Locale.US))
    }

    @Test
    fun `south and west are shown as letters, not minus signs`() {
        assertEquals("33.9249° S, 70.6693° W", Coordinates.format(-33.9249, -70.6693, Locale.US))
    }

    @Test
    fun `the phone's decimal separator is used`() {
        assertEquals("47,4979° N, 19,0402° E", Coordinates.format(47.4979, 19.0402, Locale.forLanguageTag("hu")))
    }

    @Test
    fun `the equator and the prime meridian count as north and east`() {
        assertEquals("0.0000° N, 0.0000° E", Coordinates.format(0.0, 0.0, Locale.US))
    }
}
