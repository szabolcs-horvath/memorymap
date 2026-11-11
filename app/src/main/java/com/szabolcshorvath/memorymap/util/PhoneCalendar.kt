package com.szabolcshorvath.memorymap.util

import androidx.core.text.util.LocalePreferences
import androidx.core.text.util.LocalePreferences.FirstDayOfWeek
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

// The day weeks start on for the phone, for the date pickers and the "This week" filter.

/**
 * The day weeks start on: the one chosen in Android's regional preferences, or else the usual one for the phone's
 * language and region (Sunday in the US, Monday in most of Europe).
 */
fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek =
    when (LocalePreferences.getFirstDayOfWeek(locale)) {
        FirstDayOfWeek.MONDAY -> DayOfWeek.MONDAY
        FirstDayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
        FirstDayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
        FirstDayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
        FirstDayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
        FirstDayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
        FirstDayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
        else -> WeekFields.of(locale).firstDayOfWeek
    }
