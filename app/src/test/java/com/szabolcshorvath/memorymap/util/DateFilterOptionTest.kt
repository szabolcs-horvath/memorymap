package com.szabolcshorvath.memorymap.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
