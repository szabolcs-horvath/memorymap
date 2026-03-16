package com.szabolcshorvath.memorymap.util

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateTimeFormatterUtil {

    private val DATE_FORMATTER_MEDIUM = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    private val TIME_FORMATTER_SHORT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun dateFormatter(): DateTimeFormatter {
        return DATE_FORMATTER_MEDIUM.withLocale(Locale.getDefault())
    }

    fun timeFormatter(): DateTimeFormatter {
        return TIME_FORMATTER_SHORT.withLocale(Locale.getDefault())
    }
}