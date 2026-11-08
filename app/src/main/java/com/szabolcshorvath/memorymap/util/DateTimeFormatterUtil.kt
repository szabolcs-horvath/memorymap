package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.icu.text.DateFormat
import android.icu.util.TimeZone
import com.szabolcshorvath.memorymap.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Dates and times as text. Two kinds, never mixed:
 * - Fixed formats for text that is stored or read back (backup file names, `metadata.json`). Changing one breaks
 *   the backups that already use it.
 * - Text shown on screen ([date], [time], [dateTime], [twoLineDateTime]), formatted by Android's own ICU library as
 *   the rest of the phone formats it: in the phone's language, with its digits and calendar, on the 12- or 24-hour
 *   clock it is set to. Never store, parse or log these: they change with the phone's settings.
 */
object DateTimeFormatterUtil {

    val BACKUP_METADATA_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
    val BACKUP_FILE_NAME_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

    /** The day with a short month name ("Nov 6, 2026"). */
    fun date(day: LocalDate): String = format(DATE_SKELETON, day.atStartOfDay(ZoneId.systemDefault()))

    /** The day of [time], in its own time zone ("Nov 6, 2026"). */
    fun date(time: ZonedDateTime): String = format(DATE_SKELETON, time)

    /** Hours and minutes of [time] on the clock the phone is set to: 12 or 24 hours ("3:05 PM", "15:05"). */
    fun time(context: Context, time: ZonedDateTime): String = format(clockSkeleton(is24HourClock(context), withSeconds = false), time)

    /** The day and the time, as [date] and [time] show them ("Nov 6, 2026, 3:05 PM"), optionally with seconds. */
    fun dateTime(context: Context, time: ZonedDateTime, withSeconds: Boolean = false): String =
        format(DATE_SKELETON + clockSkeleton(is24HourClock(context), withSeconds), time)

    /**
     * A memory's or fragment's dates: the days, with the times unless it is [isAllDay], and a day only once when
     * both ends fall on it ("Nov 6, 2026 3:05 PM - 5:00 PM", "Nov 6, 2026 - Nov 8, 2026").
     */
    fun dateRange(context: Context, start: ZonedDateTime, end: ZonedDateTime, isAllDay: Boolean): String {
        val startDay = date(start)
        val endDay = date(end)
        return when {
            isAllDay && startDay == endDay -> startDay
            isAllDay -> context.getString(R.string.date_range, startDay, endDay)
            startDay == endDay -> context.getString(R.string.date_with_time_range, startDay, time(context, start), time(context, end))
            else -> context.getString(
                R.string.date_range,
                context.getString(R.string.date_and_time, startDay, time(context, start)),
                context.getString(R.string.date_and_time, endDay, time(context, end))
            )
        }
    }

    /** The day on one line and the time on the next, for a small tile ("Nov 6, 2026" / "3:05 PM"). */
    fun twoLineDateTime(context: Context, epochMillis: Long): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return context.getString(R.string.two_line_date_time, date(time), time(context, time))
    }

    /**
     * [time] with the fields of [skeleton], in the order, separators, digits and calendar of [locale]. ICU picks the
     * pattern for the fields, as Android does for its own dates. A new formatter each time: they aren't thread-safe.
     */
    internal fun format(skeleton: String, time: ZonedDateTime, locale: Locale = Locale.getDefault()): String {
        val format = DateFormat.getInstanceForSkeleton(skeleton, locale)
        format.timeZone = TimeZone.getTimeZone(java.util.TimeZone.getTimeZone(time.zone).id)
        return format.format(Date.from(time.toInstant()))
    }

    /** Hours on a 24-hour clock (`H`), or on a 12-hour clock with AM and PM (`h`, `a`), then minutes. */
    internal fun clockSkeleton(is24Hour: Boolean, withSeconds: Boolean): String {
        val seconds = if (withSeconds) "s" else ""
        return if (is24Hour) "Hm$seconds" else "hm${seconds}a"
    }

    private fun is24HourClock(context: Context) = android.text.format.DateFormat.is24HourFormat(context)

    private const val DATE_SKELETON = "yMMMd"
}
