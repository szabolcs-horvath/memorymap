package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.provider.Settings
import com.szabolcshorvath.memorymap.data.MemoryFragment
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
    fun `a date range shows a shared day once, and times unless it is all day`() {
        useClock("24")
        val evening = afternoon.withHour(19)
        val twoDaysLater = afternoon.plusDays(2)

        assertEquals("Nov 6, 2026 15:05 - 19:05", DateTimeFormatterUtil.dateRange(context, afternoon, evening, isAllDay = false))
        assertEquals(
            "Nov 6, 2026 15:05 - Nov 8, 2026 15:05",
            DateTimeFormatterUtil.dateRange(context, afternoon, twoDaysLater, isAllDay = false)
        )
        assertEquals("Nov 6, 2026", DateTimeFormatterUtil.dateRange(context, afternoon, evening, isAllDay = true))
        assertEquals("Nov 6, 2026 - Nov 8, 2026", DateTimeFormatterUtil.dateRange(context, afternoon, twoDaysLater, isAllDay = true))
    }

    @Test
    fun `memories and fragments show their dates as a range, and a fragment without dates shows none`() {
        val fragment = MemoryFragment(
            groupId = 1,
            latitude = 0.0,
            longitude = 0.0,
            placeName = null,
            address = null,
            startDate = afternoon,
            endDate = afternoon.plusDays(2),
            isAllDay = true
        )

        assertEquals("Nov 6, 2026 - Nov 8, 2026", fragment.getFormattedDate(context))
        assertEquals(null, fragment.copy(startDate = null).getFormattedDate(context))
        assertEquals(null, fragment.copy(endDate = null).getFormattedDate(context))
    }

    @Test
    fun `lengths of time are rounded down to seconds, minutes, or hours and minutes, in the phone's language`() {
        assertEquals("0 sec", NearbyMedia.span(400, Locale.US))
        assertEquals("7 sec", NearbyMedia.span(-7_900, Locale.US))
        assertEquals("3 min", NearbyMedia.span(3 * 60_000L + 59_000, Locale.US))
        assertEquals("1 hr", NearbyMedia.span(60 * 60_000L, Locale.US))
        assertEquals("1 hr, 5 min", NearbyMedia.span(65 * 60_000L, Locale.US))
        assertEquals("1 Std., 5 Min.", NearbyMedia.span(65 * 60_000L, Locale.GERMANY))
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
