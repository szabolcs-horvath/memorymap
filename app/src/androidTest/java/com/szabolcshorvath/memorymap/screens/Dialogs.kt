package com.szabolcshorvath.memorymap.screens

import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.textAsString
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
                    isClickable && textAsString().equals(button, ignoreCase = true) && window?.isFocused != false
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
object DatePickerDialog {
    /** Picks [day] of the month before the one shown, which always differs from the date it opened with. */
    fun pickDayInPreviousMonth(day: Int) {
        Ui.find { hasDescription("Change to previous month") }.click()
        Ui.find { isClickable && hasText(day.toString()) }.click()
        Ui.device.waitForIdle()
    }
}

/**
 * The calendar for picking a range of dates. It types the dates in its text mode, which needs no scrolling through
 * months, in the format the calendar expects for the phone's language.
 */
object DateRangePickerDialog {
    fun pick(start: LocalDate, end: LocalDate) {
        Ui.find { hasDescription("Switch to text input mode") }.click()
        Ui.find { hasHint("Start date") && isEditable }.text = start.format(inputFormat())
        Ui.find { hasHint("End date") && isEditable }.text = end.format(inputFormat())
        Ui.device.waitForIdle()
        // Full-screen, the calendar confirms with "Save"; as a dialog, with "OK".
        val confirm = Ui.find { isClickable && (hasText("Save") || hasText("OK")) && window?.isFocused != false }
        confirm.click()
        Ui.device.waitForIdle()
    }

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

/** The clock dialog for picking a time. */
object TimePickerDialog {
    /** Picks the hour after the one shown, so the time always changes, and returns it (0-23). */
    fun pickNextHour(): Int {
        val hour = (Ui.find { hasId("material_hour_tv") }.text.toInt() + 1) % HOURS_PER_DAY
        pickHour(hour)
        return hour
    }

    /** Picks [hour] (0-23) on the 24-hour clock. */
    fun pickHour(hour: Int) {
        Ui.find { hasDescription("$hour hours") }.click()
        Ui.device.waitForIdle()
    }

    private const val HOURS_PER_DAY = 24
}
