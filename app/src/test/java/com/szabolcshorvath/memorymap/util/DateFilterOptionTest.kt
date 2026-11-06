package com.szabolcshorvath.memorymap.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DateFilterOptionTest {

    @Test
    fun `a stored name gives its option`() {
        DateFilterOption.entries.forEach { assertEquals(it, DateFilterOption.ofStoredName(it.name)) }
    }

    @Test
    fun `an unknown or missing name gives the default`() {
        assertEquals(DateFilterOption.DEFAULT_DATE_FILTER_OPTION, DateFilterOption.ofStoredName("LAST_10_YEARS"))
        assertEquals(DateFilterOption.DEFAULT_DATE_FILTER_OPTION, DateFilterOption.ofStoredName(""))
        assertEquals(DateFilterOption.DEFAULT_DATE_FILTER_OPTION, DateFilterOption.ofStoredName(null))
    }

    @Test
    fun `this week starts on the day weeks start on for the phone`() {
        val wednesday = LocalDate.of(2026, 11, 4)

        assertEquals(LocalDate.of(2026, 11, 2) to wednesday, DateFilterOption.THIS_WEEK.dateRange(wednesday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 1) to wednesday, DateFilterOption.THIS_WEEK.dateRange(wednesday, DayOfWeek.SUNDAY))
    }

    @Test
    fun `on the first day of the week this week is just today`() {
        val sunday = LocalDate.of(2026, 11, 1)

        assertEquals(sunday to sunday, DateFilterOption.THIS_WEEK.dateRange(sunday, DayOfWeek.SUNDAY))
    }
}
