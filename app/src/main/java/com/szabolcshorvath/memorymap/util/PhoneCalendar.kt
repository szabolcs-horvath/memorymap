package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.text.format.DateFormat
import androidx.core.text.util.LocalePreferences
import androidx.core.text.util.LocalePreferences.FirstDayOfWeek
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale

// The calendar and clock the phone is set to, for the date and time pickers and the "This week" filter.

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

/** Starts the calendar's weeks on [firstDayOfWeek]. */
fun <S> MaterialDatePicker.Builder<S>.startingWeeksOnPhonesFirstDay(): MaterialDatePicker.Builder<S> =
    setCalendarConstraints(CalendarConstraints.Builder().setFirstDayOfWeek(firstDayOfWeek().calendarDay()).build())

/** Shows the clock the phone is set to: 12 hours with AM and PM, or 24 hours. */
fun MaterialTimePicker.Builder.onPhonesClock(context: Context): MaterialTimePicker.Builder =
    setTimeFormat(if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)

/** The day as `java.util.Calendar` numbers it (Sunday is 1), which the date picker takes. */
internal fun DayOfWeek.calendarDay(): Int = value % DAYS_PER_WEEK + Calendar.SUNDAY

private const val DAYS_PER_WEEK = 7
