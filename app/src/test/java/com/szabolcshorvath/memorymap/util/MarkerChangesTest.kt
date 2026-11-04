package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.data.Markerable.FragmentMarker
import com.szabolcshorvath.memorymap.data.Markerable.MarkerableCluster
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class MarkerChangesTest {

    @Test
    fun `the same memories again change nothing`() {
        val drawn = setOf(pin(group(1)), pin(group(2)))

        val changes = MarkerChanges.between(drawn, setOf(pin(group(1)), pin(group(2))))

        assertTrue(changes.isEmpty)
    }

    @Test
    fun `only an edited, added or deleted memory changes`() {
        val drawn = setOf(pin(group(1)), pin(group(2)), pin(group(3)))
        val next = setOf(pin(group(1)), pin(group(2, title = "Renamed")), pin(group(4)))

        val changes = MarkerChanges.between(drawn, next)

        assertEquals(listOf(pin(group(2)), pin(group(3))), changes.removed)
        assertEquals(listOf(pin(group(2, title = "Renamed")), pin(group(4))), changes.added)
    }

    @Test
    fun `a fragment changes when its memory is renamed, though its own fields don't`() {
        val fragment = fragment(groupId = 1)
        val drawn = setOf(pin(FragmentMarker(fragment, "Trip")))

        val changes = MarkerChanges.between(drawn, setOf(pin(FragmentMarker(fragment, "Renamed trip"))))

        assertEquals(listOf(pin(FragmentMarker(fragment, "Trip"))), changes.removed)
        assertEquals(listOf(pin(FragmentMarker(fragment, "Renamed trip"))), changes.added)
    }

    private fun pin(vararg items: Markerable) = MarkerableCluster(items.toList())

    private fun group(id: Int, title: String = "Memory $id") = MemoryGroup(
        id = id,
        title = title,
        description = null,
        latitude = 47.5 + id,
        longitude = 19.0,
        placeName = null,
        address = null,
        startDate = START,
        endDate = START.plusDays(1),
        isAllDay = true
    )

    private fun fragment(groupId: Int) = MemoryFragment(
        id = 7,
        groupId = groupId,
        latitude = 48.2,
        longitude = 16.4,
        placeName = null,
        address = null,
        startDate = null,
        endDate = null
    )

    private companion object {
        val START: ZonedDateTime = ZonedDateTime.parse("2026-05-01T10:00:00+02:00[Europe/Budapest]")
    }
}
