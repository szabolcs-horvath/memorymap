package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.textAsString
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.util.Locale
import java.util.regex.Pattern

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

    fun assertEmpty() = Ui.assertShown("The empty timeline message") { hasText("No memories yet. Add some!") }

    fun assertNoMatches(query: String) = Ui.assertShown("The no-match message for \"$query\"") {
        hasText("No memories match \"$query\"")
    }

    /** The header the timeline shows above the memories of [date]. */
    fun dayHeader(date: LocalDate): String = date.format(DateTimeFormatterUtil.dateFormatter())

    /** Day headers and memory titles appear in this order, from top to bottom (all on screen). */
    fun assertTopToBottom(vararg texts: String) {
        val tops = texts.map { text ->
            Ui.find { (hasId("tv_date") || hasId("tv_memoryTitle")) && hasText(text) }.visibleBounds.top
        }
        assertEquals("The timeline order of ${texts.toList()}", tops.sorted(), tops)
        assertEquals("Two of ${texts.toList()} share a row", tops.size, tops.toSet().size)
    }

    /** Scrolls the list down by most of a screen. Returns false at the bottom. */
    fun scrollDown(): Boolean = Ui.find { hasId("rv_timeline") }.scroll(Direction.DOWN, SCROLL_PERCENT)

    fun assertScrollToTopShown() = Ui.assertShown("The Scroll to top button") { hasDescription(SCROLL_TO_TOP) }

    fun assertScrollToTopGone() = Ui.assertGone("The Scroll to top button") { hasDescription(SCROLL_TO_TOP) }

    fun scrollToTop() {
        Ui.find { hasDescription(SCROLL_TO_TOP) }.click()
        Ui.device.waitForIdle()
    }

    private const val SCROLL_TO_TOP = "Scroll to top"
    private const val SCROLL_PERCENT = 0.8f

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
        Ui.scrollTo { isBackup(label) }
        Ui.assertShown("The backup \"$label\"") { isBackup(label) }
    }

    fun assertBackupGone(label: String) = Ui.assertGone("The backup \"$label\"") { isBackup(label) }

    /** Waits up to [timeoutMs] for a backup with [label] to appear in the list, such as an automatic backup. */
    fun assertBackupAppears(label: String, timeoutMs: Long) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            Ui.scrollTo { isBackup(label) }
            if (Ui.isShown(timeoutMs = 0) { isBackup(label) }) return
        }
        assertBackupShown(label)
    }

    private fun AccessibilityNodeInfo.isBackup(label: String) = hasId("tv_backup_name") && hasText(label)

    /** The first backup in the list is the newest. */
    fun tapRestoreOnFirstBackup() = tapOnFirstBackup("Restore")

    fun tapDeleteOnFirstBackup() = tapOnFirstBackup("Delete")

    private fun tapOnFirstBackup(button: String) {
        Ui.scrollTo { hasDescription(button) }
        Ui.find { hasDescription(button) && isEnabled }.click()
        Ui.device.waitForIdle()
    }

    fun signIn() = tapButton(CONNECT)

    fun signOut() = tapButton("Disconnect")

    fun backUpNow() = tapButton("Backup Now")

    fun relinkMedia() = tapButton(RELINK)

    fun sendLogs() = tapButton("Send Debug Logs")

    /** Waits until the re-link run is over: the screen's buttons work again. */
    fun waitForRelinkToFinish() {
        Ui.scrollTo { hasText(RELINK) }
        Ui.assertShown("The Re-link button, enabled again") { hasText(RELINK) && isEnabled }
    }

    /**
     * Waits until a backup, restore or delete and anything after it (a restore re-links media) is over: the
     * loading overlay is gone and the buttons work again.
     */
    fun waitUntilIdle() {
        assertTrue(
            "Settings is still busy",
            Ui.isGone(timeoutMs = BUSY_TIMEOUT_MS) { hasId("loadingOverlay") }
        )
        waitForRelinkToFinish()
    }

    /** The result line of the last backup, restore or delete starts with [text], such as "Backup successful". */
    fun assertResult(text: String) {
        Ui.scrollTo { isResult(text) }
        Ui.assertShown("The result \"$text\"") { isResult(text) }
    }

    private fun AccessibilityNodeInfo.isResult(text: String) =
        hasId("tv_backupStatusDetails") && textAsString().orEmpty().startsWith(text)

    fun assertAutomaticBackupPending() {
        val pending = "Your latest changes will be backed up shortly (when online)."
        Ui.scrollTo { hasText(pending) }
        Ui.assertShown("The automatic backup line") { hasText(pending) }
    }

    /** Opens the colour presets section and adds the colour shown as a new preset. */
    fun addColorPreset() {
        Ui.scrollTo { hasText("Color presets") }
        if (!Ui.isShown(timeoutMs = 0) { hasText(ADD_PRESET) }) Ui.find { hasText("Color presets") }.click()
        tapButton(ADD_PRESET)
    }

    fun setShowFragmentMarkers(on: Boolean) = setSwitch(SHOW_FRAGMENTS, on)

    fun setMarkerClustering(on: Boolean) = setSwitch(CLUSTERING, on)

    fun assertShowFragmentMarkers(on: Boolean) = assertSwitch(SHOW_FRAGMENTS, on)

    fun assertMarkerClustering(on: Boolean) = assertSwitch(CLUSTERING, on)

    /** Picks [label] ("Last 7 Days", "All Time", ...) as the date filter the map opens with. */
    fun setDefaultMapFilter(label: String) {
        Ui.scrollTo { hasId(DEFAULT_FILTER) }
        Ui.find { hasId(DEFAULT_FILTER) }.click()
        Ui.find { hasText(label) && !hasId(DEFAULT_FILTER) }.click()
        Ui.device.waitForIdle()
    }

    fun assertDefaultMapFilter(label: String) {
        Ui.scrollTo { hasId(DEFAULT_FILTER) }
        Ui.assertShown("The default map filter \"$label\"") { hasId(DEFAULT_FILTER) && hasText(label) }
    }

    // The switches stay disabled until the saved setting is loaded.
    private fun setSwitch(label: String, on: Boolean) {
        Ui.scrollTo { hasText(label) }
        val switch = Ui.find { hasText(label) && isEnabled }
        if (switch.isChecked != on) switch.click()
        assertSwitch(label, on)
    }

    private fun assertSwitch(label: String, on: Boolean) {
        Ui.scrollTo { hasText(label) }
        Ui.assertShown("\"$label\" turned ${if (on) "on" else "off"}") { hasText(label) && isEnabled && isChecked == on }
    }

    private fun tapButton(text: String) {
        Ui.scrollTo { hasText(text) }
        Ui.find { hasText(text) && isEnabled }.click()
        Ui.device.waitForIdle()
    }

    private const val CLUSTERING = "Enable Marker Clustering"
    private const val RELINK = "Re-link Gallery Photos"
    private const val ADD_PRESET = "Add current as new preset"
    private const val DEFAULT_FILTER = "spinner_defaultFilter"
    private const val BUSY_TIMEOUT_MS = 30_000L
}

/** The colour presets section of Settings: the swatches and the sliders that edit the selected one. */
object ColorPresetsSection {
    fun open() {
        Ui.scrollTo { hasText(HEADER) }
        if (!Ui.isShown(timeoutMs = 0) { hasText(ADD) }) Ui.find { hasText(HEADER) }.click()
        Ui.scrollTo { hasText(ADD) }
    }

    fun select(color: Hsv) {
        Ui.find { isSwatch(color) }.click()
        Ui.device.waitForIdle()
    }

    fun assertShown(color: Hsv) = Ui.assertShown("The preset ${describe(color)}") { isSwatch(color) }

    fun assertGone(color: Hsv) = Ui.assertGone("The preset ${describe(color)}") { isSwatch(color) }

    fun assertSelected(color: Hsv) = Ui.assertShown("The preset ${describe(color)}, selected") {
        isSwatch(color) && isSelected
    }

    /** The presets appear in this order, from left to right (all on screen). */
    fun assertLeftToRight(vararg colors: Hsv) {
        val lefts = colors.map { color -> Ui.find { isSwatch(color) }.visibleBounds.left }
        assertEquals("The order of the presets ${colors.map(::describe)}", lefts.sorted(), lefts)
    }

    /** The sliders show [color], as they do for the selected preset. */
    fun assertSlidersShow(color: Hsv) {
        Ui.assertShown("Hue ${color.hue.toInt()}") { hasId("tv_HueValue") && hasText(color.hue.toInt().toString()) }
        Ui.assertShown("Saturation ${color.saturation}") {
            hasId("tv_SaturationValue") && hasText(decimal(color.saturation))
        }
        Ui.assertShown("Brightness ${color.brightness}") {
            hasId("tv_BrightnessValue") && hasText(decimal(color.brightness))
        }
    }

    /** Taps the hue slider at [fraction] of its width (0 left, 1 right) and returns the hue it shows then. */
    fun tapHue(fraction: Float): Int {
        Ui.scrollTo { hasId("tv_BrightnessValue") } // The sliders sit below the swatches; this keeps both on screen.
        // The Material sliders report themselves as not visible to accessibility services, so they can't be looked
        // up. The hue slider spans the row between the "Hue" line (label and value) and the "Saturation" line.
        val label = Ui.find { hasText("Hue") }.visibleBounds
        val value = Ui.find { hasId("tv_HueValue") }.visibleBounds
        val next = Ui.find { hasText("Saturation") }.visibleBounds
        val x = label.left + ((value.right - label.left) * fraction).toInt()
        Ui.device.click(x, (value.bottom + next.top) / 2)
        Ui.device.waitForIdle()
        return Ui.find { hasId("tv_HueValue") }.text.toInt()
    }

    fun save() = tap("Save")

    fun undo() = tap("Undo")

    fun addCurrentColor() = tap(ADD)

    /** Taps "Delete Preset", which asks first ([Dialog] with [DELETE_QUESTION]). */
    fun delete() = tap("Delete Preset")

    /**
     * Long-presses the preset [color] and drags it into the place of [onto]. The list moves a preset past another
     * only once it covers most of it, so the drag ends at the far edge of [onto].
     */
    fun drag(color: Hsv, onto: Hsv) {
        val swatch = Ui.find { isSwatch(color) }
        val target = Ui.find { isSwatch(onto) }.visibleBounds
        val from = swatch.visibleCenter
        val x = if (target.centerX() < from.x) target.left + EDGE_INSET else target.right - EDGE_INSET
        swatch.drag(Point(x, target.centerY()), DRAG_SPEED)
        Ui.device.waitForIdle()
    }

    const val DELETE_QUESTION = "Are you sure you want to delete this color preset?"

    private fun tap(text: String) {
        Ui.scrollTo { hasText(text) }
        Ui.find { hasText(text) && isEnabled }.click()
        Ui.device.waitForIdle()
    }

    private fun AccessibilityNodeInfo.isSwatch(color: Hsv) = hasDescription(describe(color))

    private fun describe(color: Hsv): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(
        R.string.color_preset_description,
        color.hue.toInt(),
        color.saturation,
        color.brightness
    )

    private fun decimal(value: Float) = String.format(Locale.getDefault(), "%.2f", value)

    private const val HEADER = "Color presets"
    private const val ADD = "Add current as new preset"
    private const val DRAG_SPEED = 600 // Pixels per second.
    private const val EDGE_INSET = 2
}

/** The system's share sheet, which another app shows over this one. */
object ShareSheet {
    // Android 14 and newer show it from its own app; older versions from "android".
    private val PACKAGES = Pattern.compile("com\\.android\\.intentresolver|android")

    fun assertShown() = assertTrue(
        "The share sheet did not open",
        Ui.device.wait(Until.hasObject(By.pkg(PACKAGES)), TIMEOUT_MS) == true
    )

    private const val TIMEOUT_MS = 15_000L
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
