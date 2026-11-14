package com.szabolcshorvath.memorymap.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.szabolcshorvath.memorymap.screens.ComposeUi.tap
import com.szabolcshorvath.memorymap.screens.ComposeUi.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Inside one of the app's dialogs (its questions and pickers), not on the screen behind it. */
private val inDialog = hasAnyAncestor(isDialog())

/** A dialog of the app's with a title, such as a confirmation. Found through [ComposeUi], as its pickers below. */
object Dialog {
    fun assertShown(title: String) {
        ComposeUi.waitFor("The dialog \"$title\"", hasText(title) and inDialog)
    }

    fun assertGone(title: String) = ComposeUi.assertGone("The dialog \"$title\"", hasText(title) and inDialog)

    /** Taps [button] in the dialog, not a button with the same text on the screen behind it ("Clear"). */
    fun tap(button: String) {
        ComposeUi.waitFor("The button \"$button\" in a dialog", hasText(button, ignoreCase = true) and hasClickAction() and inDialog)
            .performClick()
    }
}

/** Material 3's calendar for picking a date (the Add form's). */
object DatePickerDialog {
    /** Picks [day] of the month before the one shown, which always differs from the date it opened with. */
    fun pickDayInPreviousMonth(day: Int) {
        val shownMonth = shownMonth()
        ComposeUi.waitFor("The previous month button", hasContentDescription("Change to previous month")).performClick()
        val month = Ui.eventually(read = ::shownMonth) { it != shownMonth }
        assertTrue("The calendar did not go from $shownMonth to the previous month", month != shownMonth)
        // A day reads in full, such as "Sunday, November 1, 2026" (with "Today, " before today).
        val dayOfMonth = Regex(""".*\p{L}+ $day, \d{4}""")
        ComposeUi.waitFor("Day $day", ComposeUi.textMatches(dayOfMonth) and hasClickAction() and inDialog).performClick()
    }

    /** The month and year the calendar shows, above its days. */
    private fun shownMonth(): String = ComposeUi.waitFor("The month", ComposeUi.textMatches(MONTH) and inDialog).text()

    private val MONTH = Regex("""\p{L}+ \d{4}""")
}

/** Taps a picker's OK, or Save when it is full-screen. */
private fun confirm() {
    ComposeUi.waitFor("The OK button", (hasText("OK") or hasText("Save")) and hasClickAction() and inDialog).performClick()
}

/**
 * The calendar for picking a range of dates (Material 3's, on the Add form and the map). It types the dates in its
 * text mode, which needs no scrolling through months.
 */
object DateRangePickerDialog {
    fun pick(start: LocalDate, end: LocalDate) {
        ComposeUi.waitFor("The text input mode button", hasContentDescription("Switch to text input mode")).performClick()
        val fields = Ui.eventually(read = { ComposeUi.nodes(field) }) { it.size == 2 }
        assertEquals("Date fields", 2, fields.size)
        // Material 3's field takes only the digits, in the order of the phone's date format, and adds the separators.
        val dates = listOf(start, end).map { digits(it.format(inputFormat())) }
        fields.zip(dates).forEach { (field, date) -> field.performTextReplacement(date) }
        val shown = ComposeUi.nodes(field).map { digits(it.text()) }
        assertEquals("The date fields", dates, shown)
        confirm()
    }

    private val field = hasSetTextAction() and inDialog

    private fun digits(text: String) = text.filter(Char::isDigit)

    /** The calendar's text format: the phone's short date format with two-digit days and months, and four-digit years. */
    private fun inputFormat(): DateTimeFormatter {
        val short = (DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()) as SimpleDateFormat).toPattern()
        val pattern = short.replace(Regex("[^dMy/\\-.]"), "")
            .replace(Regex("d{1,2}"), "dd")
            .replace(Regex("M{1,2}"), "MM")
            .replace(Regex("y{1,4}"), "yyyy")
            .removeSuffix(".")
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }
}

/** Material 3's clock for picking a time, on the 12- or 24-hour clock the phone is set to. */
object TimePickerDialog {
    /** Picks the hour after the one shown, so the time always changes, and returns it (0-23). */
    fun pickNextHour(): Int {
        val hour = (shownHour() + 1) % HOURS_PER_DAY
        pickHour(hour)
        return hour
    }

    /** Picks [hour] (0-23): on a 12-hour clock, its half of the day first. */
    fun pickHour(hour: Int) {
        if (isTwelveHourClock()) {
            ComposeUi.waitFor("The AM and PM switch", hasText(if (hour < HALF_DAY) "AM" else "PM") and inDialog).performClick()
            val onClock = (hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }
            clockNumber("$onClock o'clock").tap()
        } else {
            clockNumber("$hour hours").tap()
        }
    }

    // A number on the clock face is named by its description and shows no text: the shown hour above it has both.
    private fun clockNumber(name: String) =
        ComposeUi.waitFor("The clock's \"$name\"", hasContentDescription(name) and ComposeUi.hasNoText and inDialog)

    fun assertTwelveHourClock() = assertTrue("The clock does not show AM and PM", isTwelveHourClock())

    fun assertTwentyFourHourClock() {
        assertFalse("The clock shows AM and PM", isTwelveHourClock())
        clockNumber("23 hours")
    }

    /** The hour the dialog shows (0-23): the number above the clock, whose description says the hour ("7 o'clock"). */
    private fun shownHour(): Int {
        // Its descriptions: what it is ("Select hour"), then the hour ("7 o'clock" or "19 hours").
        val descriptions = ComposeUi.waitFor("The shown hour", shownHourBox).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val hour = descriptions.firstNotNullOfOrNull { HOUR.matchEntire(it) }
        val shown = checkNotNull(hour) { "No hour in $descriptions" }.groupValues[1].toInt()
        if (!isTwelveHourClock()) return shown
        val afternoon = ComposeUi.exists(hasText("PM") and (isSelected() or isOn()) and inDialog)
        return shown % HALF_DAY + if (afternoon) HALF_DAY else 0
    }

    private val shownHourBox: SemanticsMatcher = ComposeUi.textMatches(Regex("""\d{2}""")) and
        (ComposeUi.descriptionEndsWith(" o'clock") or ComposeUi.descriptionEndsWith(" hours")) and inDialog

    /** Whether the dialog has the AM and PM buttons. It waits for the dialog, then checks once. */
    private fun isTwelveHourClock(): Boolean {
        ComposeUi.waitFor("The time picker", shownHourBox)
        return ComposeUi.exists(hasText("AM") and inDialog)
    }

    private val HOUR = Regex("""(\d+) (o'clock|hours)""")
    private const val HOURS_PER_DAY = 24
    private const val HALF_DAY = 12
}
