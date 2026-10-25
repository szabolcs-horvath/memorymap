package com.szabolcshorvath.memorymap.screens

import androidx.test.uiautomator.textAsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

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

    fun openMemory(title: String) {
        Ui.find { hasId("tv_memoryTitle") && hasText(title) }.click()
        Ui.device.waitForIdle()
    }
}

/** The Add tab: the form for a new memory, or for editing one. */
object AddScreen : TabScreen {
    private const val SELECT_LOCATION = "Select on Map"
    private const val NO_LOCATION = "No location selected"

    override fun assertShown() = Ui.assertShown("The Add form") { hasText(SELECT_LOCATION) }

    override fun assertGone() = Ui.assertGone("The Add form") { hasText(SELECT_LOCATION) }

    fun typeTitle(title: String) {
        Ui.find { hasHint("Title") && isEditable }.text = title
        Ui.device.waitForIdle()
    }

    fun assertTitle(title: String) = Ui.assertShown("The title \"$title\"") { hasHint("Title") && hasText(title) }

    fun save() {
        Ui.scrollTo { hasText("Save Memory") }
        Ui.find { hasText("Save Memory") }.click()
        Ui.device.waitForIdle()
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
    /** Picks the hour after the one shown, so the time always changes. */
    fun pickNextHour() {
        val hour = Ui.find { hasId("material_hour_tv") }.text.toInt()
        Ui.find { hasDescription("${(hour + 1) % HOURS_PER_DAY} hours") }.click()
        Ui.device.waitForIdle()
    }

    private const val HOURS_PER_DAY = 24
}
