package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.BACKUP_FILE_NAME_DATE_FORMATTER
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.BACKUP_METADATA_DATE_FORMATTER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/** Backup file names and metadata dates must keep the format every existing backup already uses. */
class DateTimeFormatterUtilTest {

    private val time = LocalDateTime.of(2026, 1, 4, 9, 5, 7)

    @Test
    fun `backup file name date keeps its format`() {
        assertEquals("20260104_090507", time.format(BACKUP_FILE_NAME_DATE_FORMATTER))
    }

    @Test
    fun `backup metadata date keeps its format`() {
        assertEquals("2026-01-04 09:05:07", time.format(BACKUP_METADATA_DATE_FORMATTER))
    }
}
