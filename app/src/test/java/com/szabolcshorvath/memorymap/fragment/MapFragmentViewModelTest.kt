package com.szabolcshorvath.memorymap.fragment

import com.szabolcshorvath.memorymap.fragment.MapFragmentViewModel.Companion.overlapsDateFilter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MapFragmentViewModelTest {

    private val yearStart = LocalDate.of(2026, 1, 1)
    private val yearEnd = LocalDate.of(2026, 12, 31)

    @Test
    fun `a memory that starts before the range and ends in it is shown`() {
        assertTrue(overlapsDateFilter(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3), yearStart, yearEnd))
    }

    @Test
    fun `a memory that starts in the range and ends after it is shown`() {
        assertTrue(overlapsDateFilter(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3), yearStart, yearEnd))
    }

    @Test
    fun `a memory longer than the range is shown`() {
        assertTrue(overlapsDateFilter(LocalDate.of(2025, 6, 1), LocalDate.of(2027, 6, 1), yearStart, yearEnd))
    }

    @Test
    fun `a memory on the first or last day of the range is shown`() {
        assertTrue(overlapsDateFilter(yearStart, yearStart, yearStart, yearEnd))
        assertTrue(overlapsDateFilter(yearEnd, yearEnd, yearStart, yearEnd))
    }

    @Test
    fun `a memory entirely outside the range is hidden`() {
        assertFalse(overlapsDateFilter(LocalDate.of(2025, 12, 20), LocalDate.of(2025, 12, 31), yearStart, yearEnd))
        assertFalse(overlapsDateFilter(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2), yearStart, yearEnd))
    }

    @Test
    fun `a missing end of the range is open`() {
        assertTrue(overlapsDateFilter(LocalDate.of(1990, 1, 1), LocalDate.of(1990, 1, 2), null, yearEnd))
        assertTrue(overlapsDateFilter(LocalDate.of(2090, 1, 1), LocalDate.of(2090, 1, 2), yearStart, null))
        assertTrue(overlapsDateFilter(LocalDate.of(1990, 1, 1), LocalDate.of(1990, 1, 2), null, null))
        assertFalse(overlapsDateFilter(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2), null, yearEnd))
    }
}
