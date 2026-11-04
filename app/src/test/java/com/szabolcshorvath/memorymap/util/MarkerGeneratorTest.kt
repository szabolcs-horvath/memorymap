package com.szabolcshorvath.memorymap.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class) // MarkerGenerator holds Android caches.
class MarkerGeneratorTest {

    @Test
    fun `colour lists with the same hash get different icons`() {
        val first = listOf(0, 31)
        val second = listOf(1, 0)
        assertEquals(first.hashCode(), second.hashCode())

        assertNotEquals(
            MarkerGenerator.drawnIconKey("pin", first, count = 2, density = 2f),
            MarkerGenerator.drawnIconKey("pin", second, count = 2, density = 2f)
        )
    }

    @Test
    fun `the same colours, count, size and shape share an icon`() {
        assertEquals(
            MarkerGenerator.drawnIconKey("circle", listOf(-16777216, 255), count = 2, density = 2.75f),
            MarkerGenerator.drawnIconKey("circle", listOf(-16777216, 255), count = 2, density = 2.75f)
        )
        assertNotEquals(
            MarkerGenerator.drawnIconKey("circle", listOf(255), count = 1, density = 2f),
            MarkerGenerator.drawnIconKey("pin", listOf(255), count = 1, density = 2f)
        )
    }
}
