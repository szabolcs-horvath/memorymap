package com.szabolcshorvath.memorymap.fragment

import com.szabolcshorvath.memorymap.data.MemoryGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class TimelineFragmentViewModelTest {

    private val viewModel = TimelineFragmentViewModel()

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
        val result = viewModel.filterGroups(memories, "")
        assertEquals(3, result.size)
    }

    @Test
    fun `filterGroups filters by title`() {
        val result = viewModel.filterGroups(memories, "Paris")
        assertEquals(1, result.size)
        assertEquals("Trip to Paris", result[0].title)
    }

    @Test
    fun `filterGroups filters by description`() {
        val result = viewModel.filterGroups(memories, "friends")
        assertEquals(1, result.size)
        assertEquals("Birthday Party", result[0].title)
    }

    @Test
    fun `filterGroups is case insensitive`() {
        val result = viewModel.filterGroups(memories, "paris")
        assertEquals(1, result.size)
        assertEquals("Trip to Paris", result[0].title)
    }

    @Test
    fun `filterGroups filters by place name`() {
        // Place name of memory 3 is "Barcelona"
        val result = viewModel.filterGroups(memories, "Barcelona")
        assertEquals(1, result.size)
        assertEquals("Summer vacation", result[0].title)
    }

    @Test
    fun `filterGroups filters by address`() {
        // Address of memory 3 contains "Rambla"
        val result = viewModel.filterGroups(memories, "Rambla")
        assertEquals(1, result.size)
        assertEquals("Summer vacation", result[0].title)
    }
}
