package com.szabolcshorvath.memorymap.fragment

import com.szabolcshorvath.memorymap.data.MemoryGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class TimelineFragmentViewModelTest {

    private val memories = listOf(
        MemoryGroup(
            id = 1,
            title = "Trip to Paris",
            description = "Visiting the Eiffel Tower",
            latitude = 48.8584,
            longitude = 2.2945,
            placeName = "Paris",
            address = "Champ de Mars, 5 Av. Anatole France, 75007 Paris, France",
            startDate = ZonedDateTime.now(),
            endDate = ZonedDateTime.now(),
            isAllDay = true
        ),
        MemoryGroup(
            id = 2,
            title = "Birthday Party",
            description = "At home with friends",
            latitude = 0.0,
            longitude = 0.0,
            placeName = "Home",
            address = null,
            startDate = ZonedDateTime.now(),
            endDate = ZonedDateTime.now(),
            isAllDay = false
        ),
        MemoryGroup(
            id = 3,
            title = "Summer vacation",
            description = "Relaxing on the beach",
            latitude = 41.3851,
            longitude = 2.1734,
            placeName = "Barcelona",
            address = "La Rambla, 08002 Barcelona, Spain",
            startDate = ZonedDateTime.now(),
            endDate = ZonedDateTime.now(),
            isAllDay = true
        )
    )

    @Test
    fun `filterGroups returns all when query is empty`() {
        val result = TimelineFragmentViewModel.filterGroups(memories, "")
        assertEquals(3, result.size)
    }

    @Test
    fun `filterGroups filters by title`() {
        val result = TimelineFragmentViewModel.filterGroups(memories, "Paris")
        assertEquals(1, result.size)
        assertEquals("Trip to Paris", result[0].title)
    }

    @Test
    fun `filterGroups filters by description`() {
        val result = TimelineFragmentViewModel.filterGroups(memories, "friends")
        assertEquals(1, result.size)
        assertEquals("Birthday Party", result[0].title)
    }

    @Test
    fun `filterGroups is case insensitive`() {
        val result = TimelineFragmentViewModel.filterGroups(memories, "paris")
        assertEquals(1, result.size)
        assertEquals("Trip to Paris", result[0].title)
    }

    @Test
    fun `filterGroups filters by place name`() {
        // Place name of memory 3 is "Barcelona"
        val result = TimelineFragmentViewModel.filterGroups(memories, "Barcelona")
        assertEquals(1, result.size)
        assertEquals("Summer vacation", result[0].title)
    }

    @Test
    fun `filterGroups filters by address`() {
        // Address of memory 3 contains "Rambla"
        val result = TimelineFragmentViewModel.filterGroups(memories, "Rambla")
        assertEquals(1, result.size)
        assertEquals("Summer vacation", result[0].title)
    }

    @Test
    fun `groupByDay puts the newest day first and keeps each day's memories newest first`() {
        val day1 = ZonedDateTime.parse("2026-05-01T08:00:00+02:00[Europe/Budapest]")
        val day2 = day1.plusDays(3)
        val groups = listOf(
            memories[0].copy(id = 1, startDate = day1.plusHours(1)),
            memories[1].copy(id = 2, startDate = day2),
            memories[2].copy(id = 3, startDate = day1.plusHours(5)),
        )

        val days = TimelineFragmentViewModel.groupByDay(groups)

        assertEquals(listOf(day2.toLocalDate(), day1.toLocalDate()), days.map { it.date })
        assertEquals(listOf(listOf(2), listOf(3, 1)), days.map { day -> day.memories.map { it.id } })
    }
}
