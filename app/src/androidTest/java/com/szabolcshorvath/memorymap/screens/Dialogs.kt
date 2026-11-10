package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.textAsString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A dialog with a title, such as a confirmation. */
object Dialog {
    fun assertShown(title: String) = Ui.assertShown("The dialog \"$title\"") { hasText(title) }

    fun assertGone(title: String) = Ui.assertGone("The dialog \"$title\"") { hasText(title) }

    /**
     * Taps [button] in the dialog, not a button with the same text on the screen behind it ("Clear"). A dialog that
     * is still opening can replace its button between finding and tapping it, so this finds it again (a few times).
     */
    fun tap(button: String) {
        repeat(MAX_TRIES) { attempt ->
            try {
                Ui.find {
                    // A Compose button's text is a node inside the clickable button.
                    (isClickable || parent?.isClickable == true) && textAsString().equals(button, ignoreCase = true) && window?.isFocused != false
                }.click()
                Ui.device.waitForIdle()
                return
            } catch (e: StaleObjectException) {
                if (attempt == MAX_TRIES - 1) throw e
            }
        }
    }

    private const val MAX_TRIES = 3
}

/** The calendar dialog for picking a date. */
/** Material 3's calendar for picking a date (the Add form's). */
object DatePickerDialog {
    /** Picks [day] of the month before the one shown, which always differs from the date it opened with. */
    fun pickDayInPreviousMonth(day: Int) {
        val shownMonth = shownMonth()
        Ui.find { hasDescription("Change to previous month") }.click()
        // The calendar slides to that month, and for a moment both months' days are on screen: tap the day only once
        // the calendar says it shows the new month and the day has stopped moving.
        assertTrue("The calendar did not go to the previous month", Ui.waitUntil { shownMonth().let { it != null && it != shownMonth } })
        // A day reads in full, such as "Sunday, November 1, 2026" (with "Today, " before today).
        val dayOfMonth = Regex("""\p{L}+ $day, \d{4}$""")
        repeat(MAX_TRIES) { attempt ->
            try {
                Ui.findSettled { isClickable && dayOfMonth.containsMatchIn(textAsString().orEmpty()) }.click()
                Ui.device.waitForIdle()
                return
            } catch (e: StaleObjectException) {
                if (attempt == MAX_TRIES - 1) throw e
            }
        }
    }

    /** The month and year the calendar shows, above its days (null while it is being replaced). */
    private fun shownMonth(): String? = try {
        Ui.findOrNull { MONTH.matches(textAsString().orEmpty()) }?.text
    } catch (_: StaleObjectException) {
        null
    }

    private val MONTH = Regex("""\p{L}+ \d{4}""")
    private const val MAX_TRIES = 3
}

/** Taps a picker's OK, or Save when it is full-screen: the text of a Compose button sits inside it. */
private fun confirm() {
    Ui.find { (hasText("OK") || hasText("Save")) && (isClickable || parent?.isClickable == true) && window?.isFocused != false }.click()
    Ui.device.waitForIdle()
}

/**
 * The calendar for picking a range of dates: Material 3's on the Add form, Material Components' on the map. It types
 * the dates in its text mode, which needs no scrolling through months, in the format the calendar expects for the
 * phone's language.
 */
object DateRangePickerDialog {
    fun pick(start: LocalDate, end: LocalDate) {
        Ui.find { hasDescription("Switch to text input mode") }.click()
        Ui.waitUntil { fields().size == 2 }
        val (startField, endField) = fields()
        type(startField, start.format(inputFormat()))
        type(endField, end.format(inputFormat()))
        Ui.device.waitForIdle()
        confirm()
    }

    /**
     * Types [date] into [field]. Material 3's field takes only the digits and adds the separators itself, so it keeps
     * its old text when given them: then it gets the digits alone.
     */
    private fun type(field: UiObject2, date: String) {
        field.text = date
        if (field.text != date) field.text = date.filter(Char::isDigit)
    }

    /** The start and end fields, top to bottom (Material Components' side by side, left to right). */
    private fun fields() = Ui.device.findObjects(By.clazz("android.widget.EditText"))
        .sortedWith(compareBy({ it.visibleBounds.top }, { it.visibleBounds.left }))

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
            Ui.find { hasText(if (hour < HALF_DAY) "AM" else "PM") }.click()
            val onClock = (hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }
            Ui.find { hasDescription("$onClock o'clock") && textAsString().isNullOrEmpty() }.click()
        } else {
            Ui.find { hasDescription("$hour hours") && textAsString().isNullOrEmpty() }.click()
        }
        Ui.device.waitForIdle()
    }

    fun assertTwelveHourClock() = assertTrue("The clock does not show AM and PM", isTwelveHourClock())

    fun assertTwentyFourHourClock() {
        assertFalse("The clock shows AM and PM", isTwelveHourClock())
        Ui.assertShown("The clock's hour 23") { hasDescription("23 hours") }
    }

    /** The hour the dialog shows (0-23): the number above the clock, whose description says the hour ("7 o'clock"). */
    private fun shownHour(): Int {
        val shown = Ui.find { isShownHour() }.contentDescription.toString().substringBefore(' ').toInt()
        if (!isTwelveHourClock()) return shown
        val afternoon = Ui.find { hasText("PM") }.parent.isChecked
        return shown % HALF_DAY + if (afternoon) HALF_DAY else 0
    }

    private fun AccessibilityNodeInfo.isShownHour() =
        textAsString().orEmpty().matches(Regex("""\d{2}""")) && (hasDescriptionEnding(" o'clock") || hasDescriptionEnding(" hours"))

    private fun AccessibilityNodeInfo.hasDescriptionEnding(suffix: String) = contentDescription?.endsWith(suffix) == true

    /** Whether the dialog has the AM and PM buttons. It waits for the dialog, then checks once. */
    private fun isTwelveHourClock(): Boolean {
        Ui.find { isShownHour() }
        return Ui.findOrNull(timeoutMs = 0) { hasText("AM") } != null
    }

    private const val HOURS_PER_DAY = 24
    private const val HALF_DAY = 12
}
