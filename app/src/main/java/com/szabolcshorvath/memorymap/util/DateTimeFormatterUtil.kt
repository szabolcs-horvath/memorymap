package com.szabolcshorvath.memorymap.util

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateTimeFormatterUtil {

    val BACKUP_METADATA_DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val BACKUP_FILE_NAME_DATE_FORMATTER = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private val DATE_FORMATTER_MEDIUM = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val TIME_FORMATTER_SHORT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val DATE_TIME_FORMATTER_MEDIUM = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

    fun dateFormatter(): DateTimeFormatter {
        return DATE_FORMATTER_MEDIUM.withLocale(Locale.getDefault())
    }

    fun timeFormatter(): DateTimeFormatter {
        return TIME_FORMATTER_SHORT.withLocale(Locale.getDefault())
    }

    fun dateTimeFormatter(): DateTimeFormatter {
        return DATE_TIME_FORMATTER_MEDIUM.withLocale(Locale.getDefault())
    }
}
