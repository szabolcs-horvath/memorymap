package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.textAsString
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Locale

/** The Settings tab. */
object SettingsScreen : TabScreen {
    private const val SHOW_FRAGMENTS = "Show memory fragment markers"
    private const val CONNECT = "Connect"

    // By the scrolling page, which is on screen wherever it was scrolled to: the tab keeps its scroll position.
    override fun assertShown() = Ui.assertShown("Settings") { hasId(PAGE) }

    override fun assertGone() = Ui.assertGone("Settings") { hasId(PAGE) }

    fun assertSignedOut() {
        Ui.scrollTo { hasText(CONNECT) }
        Ui.assertShown("The Connect to Google Drive button") { hasText(CONNECT) }
    }

    fun assertSignedIn(email: String) {
        Ui.scrollTo { isAccount(email) }
        Ui.assertShown("The signed-in account $email") { isAccount(email) }
    }

    private fun AccessibilityNodeInfo.isAccount(email: String) = hasId("tv_accountName") && hasText(email)

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

    /** Under the newest backups, a row opens the page with all [total] of them. */
    fun assertShowAllBackups(total: Int) {
        // To its second line, the row's bottom.
        Ui.scrollTo { hasText("$total backups") }
        Ui.assertShown("Show all backups") { hasText(SHOW_ALL) }
        Ui.assertShown("Show all backups, with $total backups") { hasText("$total backups") }
    }

    fun assertNoShowAllBackups() = Ui.assertGone("Show all backups") { hasText(SHOW_ALL) }

    /** Opens the page with every backup; [assertBackupShown] and the restore and delete buttons work on it too. */
    fun showAllBackups() {
        Ui.scrollTo { hasText(SHOW_ALL) }
        Ui.find { hasText(SHOW_ALL) }.click()
        assertAllBackupsShown()
    }

    fun assertAllBackupsShown() = Ui.assertShown("All backups") { hasId(ALL_BACKUPS_PAGE) }

    /** Leaves the page with every backup by its back arrow. */
    fun closeAllBackups() {
        Ui.find { hasDescription("Back") }.click()
        assertAllBackupsGone()
    }

    fun assertAllBackupsGone() = Ui.assertGone("All backups") { hasId(ALL_BACKUPS_PAGE) }

    /** Pulls the page down from its top, which loads the backup list again. */
    fun refreshBackups() {
        Ui.scrollToTop()
        Ui.find { hasId(PAGE) }.swipe(Direction.DOWN, PULL_PERCENT)
        Ui.device.waitForIdle()
    }

    /** The line above the backup list says it couldn't be loaded, with the message [message] (a string resource). */
    fun assertBackupListProblem(message: Int) {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.getString(message)
        Ui.scrollTo { hasText(text) }
        Ui.assertShown("The backup list problem \"$text\"") { hasText(text) }
    }

    fun assertNoBackupListProblem() = Ui.assertGone("A backup list problem") { hasId("tv_backupListProblem") }

    fun assertAllowAccessGone() = Ui.assertGone("The Allow access button") { hasText(ALLOW_ACCESS) }

    fun allowDriveAccess() {
        Ui.scrollTo { hasText(ALLOW_ACCESS) }
        Ui.find { hasText(ALLOW_ACCESS) }.click()
        Ui.device.waitForIdle()
    }

    private const val ALLOW_ACCESS = "Allow access"
    private const val PULL_PERCENT = 0.8f

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

    fun backUpNow() = tapButton("Back up now")

    fun relinkMedia() = tapItem(RELINK, RELINK_ITEM)

    fun sendLogs() = tapItem("Send debug logs", "bt_sendLogs")

    /** Waits until the re-link run is over: the screen's buttons work again. */
    fun waitForRelinkToFinish() {
        Ui.scrollTo { hasText(RELINK) }
        Ui.assertShown("The Re-link item, enabled again", Ui.SLOW_TIMEOUT_MS) { hasId(RELINK_ITEM) && isEnabled }
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

    // The switches stay disabled until the saved setting is loaded. A switch row is checked and enabled itself, while its
    // label is a line inside it: the row is the label's parent.
    private fun setSwitch(label: String, on: Boolean) {
        Ui.scrollTo { hasText(label) }
        val row = Ui.find { hasText(label) && parent?.isEnabled == true }.parent
        if (row.isChecked != on) row.click()
        assertSwitch(label, on)
    }

    private fun assertSwitch(label: String, on: Boolean) {
        Ui.scrollTo { hasText(label) }
        Ui.assertShown("\"$label\" turned ${if (on) "on" else "off"}") {
            hasText(label) && parent?.let { it.isEnabled && it.isChecked == on } == true
        }
    }

    private fun tapButton(text: String) {
        Ui.scrollTo { hasText(text) }
        Ui.find { hasText(text) && isEnabled }.click()
        Ui.device.waitForIdle()
    }

    // A list item that does something: its text is a line inside it, so it is told enabled by its own name.
    private fun tapItem(text: String, id: String) {
        Ui.scrollTo { hasText(text) }
        Ui.find { hasId(id) && isEnabled }.click()
        Ui.device.waitForIdle()
    }

    private const val CLUSTERING = "Enable marker clustering"
    private const val RELINK = "Re-link photos and videos"
    private const val RELINK_ITEM = "bt_relinkMedia"
    private const val PAGE = "scroll_settings"
    private const val ALL_BACKUPS_PAGE = "scroll_all_backups"
    private const val SHOW_ALL = "Show all backups"
    private const val ADD_PRESET = "Add current as new preset"
    private const val DEFAULT_FILTER = "spinner_defaultFilter"
    private const val BUSY_TIMEOUT_MS = 30_000L
}

/**
 * The colour presets section of Settings: the swatches and the sliders that edit the selected one. [select] and
 * [assertSlidersShow] also work for the Add form's marker colour, which has the same swatches and sliders.
 */
object ColorPresetsSection {
    fun open() {
        Ui.scrollTo { hasText(HEADER) }
        if (!Ui.isShown(timeoutMs = 0) { hasText(ADD) }) Ui.find { hasText(HEADER) }.click()
        Ui.scrollTo { hasText(ADD) }
    }

    fun select(color: Hsv) {
        Ui.scrollTo { isSwatch(color) }
        Ui.find { isSwatch(color) }.click()
        Ui.device.waitForIdle()
    }

    fun assertShown(color: Hsv) = Ui.assertShown("The preset ${describe(color)}") { isSwatch(color) }

    fun assertGone(color: Hsv) = Ui.assertGone("The preset ${describe(color)}") { isSwatch(color) }

    fun assertSelected(color: Hsv) = Ui.assertShown("The preset ${describe(color)}, selected") {
        isSwatch(color) && isChosen()
    }

    fun assertNotSelected(color: Hsv) = Ui.assertShown("The preset ${describe(color)}, not selected") {
        isSwatch(color) && !isChosen()
    }

    /** The presets appear in this order, from left to right (all on screen, once the first is). */
    fun assertLeftToRight(vararg colors: Hsv) {
        Ui.scrollTo { isSwatch(colors.first()) }
        val lefts = colors.map { color -> Ui.find { isSwatch(color) }.visibleBounds.left }
        assertEquals("The order of the presets ${colors.map(::describe)}", lefts.sorted(), lefts)
    }

    /** The sliders show [color], as they do for the selected preset. */
    fun assertSlidersShow(color: Hsv) {
        Ui.scrollTo { hasId("tv_BrightnessValue") }
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
        // The Add form's Material sliders (Views) report themselves as not visible to accessibility services, so they
        // can't be looked up. The hue slider spans the row between the "Hue" line (label and value) and the
        // "Saturation" line.
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

    /** Taps "Delete preset", which asks first ([Dialog] with [DELETE_QUESTION]). */
    fun delete() = tap("Delete preset")

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

    // Selected: the Add form's View swatch says so itself; a Compose swatch is a checked item whose colour is a node inside it.
    private fun AccessibilityNodeInfo.isChosen() = isSelected || isChecked || parent?.isChecked == true

    /** How the app describes the swatch of [color] to screen readers. */
    fun describe(color: Hsv): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(
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
