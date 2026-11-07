package com.szabolcshorvath.memorymap.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** The order a memory shows its media and fragments in, on the memory screen and in the Add form alike. */
class DisplayOrderTest {

    @Test
    fun `media follow their order, then the ones without one come newest first`() {
        val items = listOf(
            media(id = 1, order = null, dateTaken = 100),
            media(id = 2, order = 2, dateTaken = 0),
            media(id = 3, order = null, dateTaken = 300),
            media(id = 4, order = 1, dateTaken = 0)
        )

        assertEquals(listOf(4, 2, 3, 1), items.sortedWith(MediaItem.DISPLAY_ORDER).map { it.id })
    }

    @Test
    fun `fragments follow their order, then the ones without one come earliest first and undated ones last`() {
        val fragments = listOf(
            fragment(id = 1, order = null, startDay = null),
            fragment(id = 2, order = null, startDay = 20),
            fragment(id = 3, order = 2, startDay = null),
            fragment(id = 4, order = null, startDay = 10),
            fragment(id = 5, order = 1, startDay = 30)
        )

        assertEquals(listOf(5, 3, 4, 2, 1), fragments.sortedWith(MemoryFragment.DISPLAY_ORDER).map { it.id })
    }

    @Test
    fun `fragments without an order or dates keep the order they came in`() {
        val fragments = listOf(fragment(id = 7, order = null, startDay = null), fragment(id = 3, order = null, startDay = null))

        assertEquals(listOf(7, 3), fragments.sortedWith(MemoryFragment.DISPLAY_ORDER).map { it.id })
    }

    private fun media(id: Int, order: Int?, dateTaken: Long) = MediaItem(
        id = id,
        groupId = 1,
        uri = "content://media/$id",
        deviceId = "device",
        type = MediaType.IMAGE,
        mediaSignature = "",
        fileSize = 0,
        dateTaken = dateTaken,
        order = order
    )

    private fun fragment(id: Int, order: Int?, startDay: Int?): MemoryFragment {
        val start = startDay?.let { ZonedDateTime.of(2026, 1, it, 0, 0, 0, 0, ZoneId.of("UTC")) }
        return MemoryFragment(
            id = id,
            groupId = 1,
            latitude = 0.0,
            longitude = 0.0,
            placeName = null,
            address = null,
            startDate = start,
            endDate = start,
            order = order
        )
    }
}
