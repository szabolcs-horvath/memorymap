package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.provider.Settings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Locale

/**
 * Shown dates and times look as the rest of the phone shows them: its language, digits and calendar, and its 12- or
 * 24-hour setting. Weeks start where the phone's region, or its regional preferences, start them.
 */
@ExtendWith(RobolectricExtension::class)
class PhoneDateFormatsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val afternoon = ZonedDateTime.of(2026, 11, 6, 15, 5, 7, 0, ZoneId.of("UTC"))
    private lateinit var localeBefore: Locale

    @BeforeEach
    fun useAmericanEnglish() {
        localeBefore = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(localeBefore)
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, null)
    }

    @Test
    fun `a phone set to 24 hours shows 24-hour times even in a language that uses 12`() {
        useClock("24")

        assertEquals("15:05", DateTimeFormatterUtil.time(context, afternoon))
        assertEquals("Nov 6, 2026, 15:05:07", DateTimeFormatterUtil.dateTime(context, afternoon, withSeconds = true))
    }

    @Test
    fun `a phone set to 12 hours shows AM and PM`() {
        useClock("12")

        assertEquals("3:05 PM", DateTimeFormatterUtil.time(context, afternoon).spaced())
        assertEquals("Nov 6, 2026, 3:05 PM", DateTimeFormatterUtil.dateTime(context, afternoon).spaced())
        val takenHere = afternoon.withZoneSameLocal(ZoneId.systemDefault()).toInstant().toEpochMilli() // Tiles use the phone's zone.
        assertEquals("Nov 6, 2026\n3:05 PM", DateTimeFormatterUtil.twoLineDateTime(context, takenHere).spaced())
    }

    @Test
    fun `without a setting the language decides`() {
        assertEquals("3:05 PM", DateTimeFormatterUtil.time(context, afternoon).spaced())
    }

    @Test
    fun `the language decides the order and separators of the clock`() {
        val skeleton = DateTimeFormatterUtil.clockSkeleton(is24Hour = false, withSeconds = false)

        assertEquals("오후 3:05", DateTimeFormatterUtil.format(skeleton, afternoon, Locale.KOREA).spaced())
    }

    @Test
    fun `dates use the digits of the phone's language`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        assertTrue(DateTimeFormatterUtil.date(afternoon).contains("٢٠٢٦"), DateTimeFormatterUtil.date(afternoon))
    }

    @Test
    fun `dates use the calendar chosen for the phone`() {
        Locale.setDefault(Locale.forLanguageTag("en-US-u-ca-buddhist"))

        assertEquals("Nov 6, 2569 BE", DateTimeFormatterUtil.date(afternoon))
    }

    @Test
    fun `a time is shown in its own time zone, also when that is a fixed offset`() {
        val lateInUtc = ZonedDateTime.of(2026, 11, 6, 23, 30, 0, 0, ZoneId.of("UTC"))

        assertEquals("Nov 7, 2026", DateTimeFormatterUtil.date(lateInUtc.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))))
        assertEquals("Nov 7, 2026", DateTimeFormatterUtil.date(lateInUtc.withZoneSameInstant(ZoneOffset.ofHours(9))))
        assertEquals("Nov 6, 2026", DateTimeFormatterUtil.date(lateInUtc))
    }

    @Test
    fun `a day is shown as that day`() {
        assertEquals("Nov 6, 2026", DateTimeFormatterUtil.date(LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun `weeks start on sunday in the us, on monday in hungary, and on the day chosen in the regional preferences`() {
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeek(Locale.US))
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeek(Locale.forLanguageTag("hu-HU")))
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeek(Locale.forLanguageTag("en-US-u-fw-mon")))
        assertEquals(DayOfWeek.SATURDAY, firstDayOfWeek(Locale.forLanguageTag("hu-HU-u-fw-sat")))
    }

    @Test
    fun `the date picker numbers days as java util calendar does`() {
        assertEquals(Calendar.SUNDAY, DayOfWeek.SUNDAY.calendarDay())
        assertEquals(Calendar.MONDAY, DayOfWeek.MONDAY.calendarDay())
        assertEquals(Calendar.SATURDAY, DayOfWeek.SATURDAY.calendarDay())
    }

    private fun useClock(hours: String) {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, hours)
    }

    /** Newer language data puts a narrow no-break space before AM and PM; the tests read it as a plain one. */
    private fun String.spaced() = replace(' ', ' ').replace(' ', ' ')
}
