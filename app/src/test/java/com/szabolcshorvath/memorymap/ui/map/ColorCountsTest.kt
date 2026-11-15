package com.szabolcshorvath.memorymap.ui.map

import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.ui.map.MapViewModel.Companion.colorCounts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.ZonedDateTime

/** The colour statistics count memories (the colours need Android's own colour conversion, hence Robolectric). */
@ExtendWith(RobolectricExtension::class)
class ColorCountsTest {

    @Test
    fun `memories are counted by colour, most common first`() {
        val pins = listOf(MarkerableCluster(listOf(memory(1, RED), memory(2, BLUE))), MarkerableCluster(listOf(memory(3, BLUE))))

        assertEquals(listOf(2, 1), colorCounts(pins).map { it.count })
        assertEquals(memory(2, BLUE).markerColor(), colorCounts(pins).first().color)
    }

    @Test
    fun `fragment markers are not counted, whatever their colour`() {
        val red = memory(1, RED)
        val pins = listOf(
            MarkerableCluster(listOf(red)),
            MarkerableCluster(listOf(fragment(red, BLUE), fragment(red, RED))),
        )

        assertEquals(listOf(ColorCount(red.markerColor(), 1)), colorCounts(pins))
    }

    private fun memory(id: Int, hue: Float) = MemoryGroup(
        id = id,
        title = "Memory $id",
        description = null,
        latitude = 47.5,
        longitude = 19.05,
        placeName = null,
        address = null,
        startDate = DATE,
        endDate = DATE,
        isAllDay = false,
        markerHue = hue,
    )

    private fun fragment(group: MemoryGroup, hue: Float) = Markerable.FragmentMarker(
        MemoryFragment(
            groupId = group.id,
            latitude = 47.6,
            longitude = 19.1,
            placeName = null,
            address = null,
            startDate = null,
            endDate = null,
            markerHue = hue,
        ),
        group.title,
    )

    private companion object {
        val DATE: ZonedDateTime = ZonedDateTime.parse("2026-11-15T12:00:00+01:00[Europe/Budapest]")
        const val RED = 0f
        const val BLUE = 240f
    }
}
