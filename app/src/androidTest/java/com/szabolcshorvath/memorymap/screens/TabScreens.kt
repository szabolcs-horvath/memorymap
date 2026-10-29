package com.szabolcshorvath.memorymap.screens

import androidx.test.uiautomator.textAsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/** The Map tab. */
object MapScreen : TabScreen {
    private const val STATS = "Color statistics"

    override fun assertShown() = Ui.assertShown("The map") { hasDescription(STATS) }

    override fun assertGone() = Ui.assertGone("The map") { hasDescription(STATS) }

    /** The card that lists the memories at the selected marker shows [title]. */
    fun assertOverlayShows(title: String) = Ui.assertShown("The map overlay with \"$title\"") {
        hasId("tv_memoryTitle") && hasText(title)
    }
}

/** The Timeline tab. */
object TimelineScreen : TabScreen {
    private const val SEARCH = "Search memories..."

    override fun assertShown() = Ui.assertShown("The timeline") { hasHint(SEARCH) }

    override fun assertGone() = Ui.assertGone("The timeline") { hasHint(SEARCH) }

    fun assertMemoryShown(title: String) = Ui.assertShown("\"$title\" in the timeline") {
        hasId("tv_memoryTitle") && hasText(title)
    }

    fun assertMemoryGone(title: String) = Ui.assertGone("\"$title\" in the timeline") {
        hasId("tv_memoryTitle") && hasText(title)
    }

    fun search(text: String) {
        Ui.find { hasHint(SEARCH) && isEditable }.text = text
        Ui.device.waitForIdle()
    }

    fun assertSearch(text: String) = Ui.assertShown("The search \"$text\"") { hasHint(SEARCH) && hasText(text) }

    fun openMemory(title: String) {
        Ui.find { hasId("tv_memoryTitle") && hasText(title) }.click()
        Ui.device.waitForIdle()
    }
}

/** The Add tab: the form for a new memory, or for editing one. */
object AddScreen : TabScreen {
    private const val SELECT_LOCATION = "Select on Map"
    private const val NO_LOCATION = "No location selected"
    private const val SAVE = "Save Memory"
    private const val UPDATE = "Update"
    private const val HALF_DAY = 12

    override fun assertShown() = Ui.assertShown("The Add form") { hasText(SELECT_LOCATION) }

    override fun assertGone() = Ui.assertGone("The Add form") { hasText(SELECT_LOCATION) }

    fun typeTitle(title: String) {
        Ui.find { hasHint("Title") && isEditable }.text = title
        Ui.device.waitForIdle()
    }

    fun assertTitle(title: String) = Ui.assertShown("The title \"$title\"") { hasHint("Title") && hasText(title) }

    fun save() {
        Ui.scrollTo { hasText(SAVE) }
        Ui.find { hasText(SAVE) }.click()
        Ui.device.waitForIdle()
    }

    /** The form edits an existing memory: its button updates that memory instead of saving a new one. */
    fun assertEditing() {
        Ui.scrollTo { hasText(UPDATE) || hasText(SAVE) }
        Ui.assertShown("The Update button of a memory being edited") { hasText(UPDATE) }
    }

    fun selectLocation() {
        Ui.find { hasText(SELECT_LOCATION) }.click()
        Ui.device.waitForIdle()
    }

    fun assertNoLocation() = assertEquals(NO_LOCATION, locationText())

    fun assertHasLocation() = assertNotEquals("No location was picked", NO_LOCATION, locationText())

    private fun locationText(): String? = Ui.find { hasId("tv_location") }.text

    fun openStartDatePicker() {
        expandDateSection()
        Ui.find { hasId("bt_startDate") }.click()
        Ui.device.waitForIdle()
    }

    fun openStartTimePicker() {
        expandDateSection()
        Ui.find { hasId("bt_startTime") }.click()
        Ui.device.waitForIdle()
    }

    fun startDateText(): String? {
        expandDateSection()
        return Ui.find { hasId("bt_startDate") }.text
    }

    fun startTimeText(): String? {
        expandDateSection()
        return Ui.find { hasId("bt_startTime") }.text
    }

    /** The start date shows [day] as its day of the month (for example "Sep 15, 2026" for 15). */
    fun assertStartDay(day: Int) {
        val text = startDateText().orEmpty()
        assertTrue("The start date \"$text\" is not on day $day", Regex("\\b$day\\b").containsMatchIn(text))
    }

    /** The start time shows [hour] (0-23), in the 24-hour or the 12-hour clock ("21:05" or "9:05 PM" for 21). */
    fun assertStartHour(hour: Int) {
        val text = startTimeText().orEmpty()
        val shown = text.substringBefore(':').trim().toIntOrNull()
        val twelveHour = (hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }
        assertTrue("The start time \"$text\" is not at hour $hour", shown == hour || shown == twelveHour)
    }

    private fun expandDateSection() {
        if (Ui.isShown(timeoutMs = 0) { hasId("bt_startDate") }) return
        Ui.find { hasText("Date & Time") }.click()
        Ui.find { hasId("bt_startDate") }
    }
}

/** The Settings tab. */
object SettingsScreen : TabScreen {
    private const val SHOW_FRAGMENTS = "Show Memory Fragment Markers"
    private const val CONNECT = "Connect to Google Drive"

    override fun assertShown() = Ui.assertShown("Settings") { hasText(SHOW_FRAGMENTS) }

    override fun assertGone() = Ui.assertGone("Settings") { hasText(SHOW_FRAGMENTS) }

    fun assertSignedOut() {
        Ui.scrollTo { hasText(CONNECT) }
        Ui.assertShown("The Connect to Google Drive button") { hasText(CONNECT) }
    }

    fun assertSignedIn(email: String) {
        Ui.scrollTo { hasText("Signed in as: $email") }
        Ui.assertShown("The signed-in account $email") { hasText("Signed in as: $email") }
    }

    /** The backup list shows a backup with [label] ("Manual Backup", "Automatic Backup" or the file name). */
    fun assertBackupShown(label: String) {
        Ui.scrollTo { hasId("tv_backup_name") && hasText(label) }
        Ui.assertShown("The backup \"$label\"") { hasId("tv_backup_name") && hasText(label) }
    }

    fun tapRestoreOnFirstBackup() {
        Ui.scrollTo { hasDescription("Restore") }
        Ui.find { hasDescription("Restore") }.click()
        Ui.device.waitForIdle()
    }
}

/** A dialog with a title, such as a confirmation. */
object Dialog {
    fun assertShown(title: String) = Ui.assertShown("The dialog \"$title\"") { hasText(title) }

    fun assertGone(title: String) = Ui.assertGone("The dialog \"$title\"") { hasText(title) }

    fun tap(button: String) {
        Ui.find { isClickable && textAsString().equals(button, ignoreCase = true) }.click()
        Ui.device.waitForIdle()
    }
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

/** The clock dialog for picking a time. */
object TimePickerDialog {
    /** Picks the hour after the one shown, so the time always changes, and returns it (0-23). */
    fun pickNextHour(): Int {
        val hour = (Ui.find { hasId("material_hour_tv") }.text.toInt() + 1) % HOURS_PER_DAY
        Ui.find { hasDescription("$hour hours") }.click()
        Ui.device.waitForIdle()
        return hour
    }

    private const val HOURS_PER_DAY = 24
}
