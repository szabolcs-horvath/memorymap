package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.textAsString
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.testing.TestData.Place
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    fun assertOverlayGone() = Ui.assertGone("The map overlay") { hasId("tv_memoryTitle") }

    /** Opens memory [title] from the overlay card. */
    fun openFromOverlay(title: String) {
        Ui.find { hasId("tv_memoryTitle") && hasText(title) }.click()
        Ui.device.waitForIdle()
    }

    /**
     * A marker titled [title] is on the map: a memory's title, or "N Memories" for several at one place. A
     * fragment's marker has its memory's title. Cluster circles have no title.
     */
    fun assertMarkerShown(title: String) = Ui.assertShown("The marker \"$title\"") { isMarker(title) }

    fun assertMarkerGone(title: String) = Ui.assertGone("The marker \"$title\"") { isMarker(title) }

    /** Whether a marker titled [title] is on the map, without waiting for one. */
    fun isMarkerShown(title: String): Boolean = Ui.isShown(timeoutMs = 0) { isMarker(title) }

    /** Waits until exactly [count] markers titled [title] are on the map. */
    fun assertMarkerCount(title: String, count: Int) {
        val marker = By.desc(markerDescription(title))
        val end = SystemClock.uptimeMillis() + Ui.TIMEOUT_MS
        var found = Ui.device.findObjects(marker).size
        while (found != count && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(POLL_MS)
            found = Ui.device.findObjects(marker).size
        }
        assertEquals("Markers titled \"$title\"", count, found)
    }

    fun tapMarker(title: String) {
        Ui.find { isMarker(title) }.click()
        Ui.device.waitForIdle()
    }

    /** Whether a cluster ("4 memories nearby") is on the map. */
    fun isClusterShown(timeoutMs: Long = 0): Boolean = Ui.isShown(timeoutMs) { isCluster() }

    fun assertClusterShown() = Ui.assertShown("A cluster of memories") { isCluster() }

    /** Taps a cluster, which zooms the map in on it, and waits for the map to settle. */
    fun tapCluster() {
        Ui.find { isCluster() }.click()
        Ui.device.waitForIdle()
        SystemClock.sleep(CAMERA_MOVE_MS)
    }

    /** No markers or clusters are on the map (the status bar's "Wifi signal full." and the like don't count). */
    fun assertNoMarkers() = Ui.assertGone("A marker") {
        packageName?.toString() == APP && MARKER.matcher(contentDescription ?: "").matches()
    }

    private fun AccessibilityNodeInfo.isCluster() = CLUSTER.matcher(contentDescription ?: "").matches()

    private fun AccessibilityNodeInfo.isMarker(title: String) =
        markerDescription(title).matcher(contentDescription ?: "").matches()

    private val CLUSTER = Pattern.compile("\\d+ memories nearby\\.\\s*")
    private val MARKER = Pattern.compile("(?s).+\\.\\s*")
    private val APP = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    // The map describes a marker as its title and snippet: "Paris trip. " (the app sets no snippet).
    private fun markerDescription(title: String): Pattern = Pattern.compile(Pattern.quote(title) + "\\.\\s*")

    /** Picks a date filter from the quick filter menu ("Last 7 Days", ..., "All Time"). */
    fun pickQuickFilter(label: String) {
        Ui.find { hasDescription("Quick date filters") }.click()
        Ui.find { hasText(label) && !hasId(DATE_RANGE) }.click()
        Ui.device.waitForIdle()
    }

    /** The date filter button shows [label]: a quick filter's name, or the picked dates. */
    fun assertFilter(label: String) = Ui.assertShown("The date filter \"$label\"") { hasId(DATE_RANGE) && hasText(label) }

    /** The date filter button shows the dates from [start] to [end], as the app writes them. */
    fun assertFilter(start: LocalDate, end: LocalDate) {
        val formatter = DateTimeFormatterUtil.dateFormatter()
        assertFilter("${start.format(formatter)} - ${end.format(formatter)}")
    }

    /** Opens the calendar for picking the dates to show ([DateRangePickerDialog]). */
    fun openDateRangePicker() {
        Ui.find { hasId(DATE_RANGE) }.click()
        Ui.device.waitForIdle()
    }

    fun toggleColorStatistics() {
        Ui.find { hasDescription(STATS) }.click()
        Ui.device.waitForIdle()
    }

    fun assertColorStatisticsShown() = Ui.assertShown("The colour statistics") { hasId(PIE_CHART) }

    fun assertColorStatisticsGone() = Ui.assertGone("The colour statistics") { hasId(PIE_CHART) }

    /**
     * The colour statistics' chart has a slice of each of [colors] and none of [absent]. The chart is drawn, with
     * nothing a screen reader can read, so its colours are read from a screenshot, on a grid across the chart.
     */
    fun assertColorStatisticsShow(colors: List<Int>, absent: List<Int> = emptyList()) {
        val chart = Ui.find { hasId(PIE_CHART) }.visibleBounds
        val grid = (1 until CHART_GRID).flatMap { i ->
            (1 until CHART_GRID).map { j ->
                Point(chart.left + chart.width() * i / CHART_GRID, chart.top + chart.height() * j / CHART_GRID)
            }
        }
        val end = SystemClock.uptimeMillis() + Ui.TIMEOUT_MS
        var shown = ScreenColors.at(grid)
        fun matches() = colors.all { color -> shown.any { ScreenColors.isClose(it, color) } } &&
            absent.none { color -> shown.any { ScreenColors.isClose(it, color) } }
        while (!matches() && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(POLL_MS)
            shown = ScreenColors.at(grid)
        }
        assertTrue(
            "The chart shows ${shown.distinct().map(ScreenColors::hex)}, " +
                "expected ${colors.map(ScreenColors::hex)} and not ${absent.map(ScreenColors::hex)}",
            matches()
        )
    }

    /** Taps the "my location" button, which moves the map to where the phone is (asking for access first). */
    fun tapMyLocation() {
        Ui.find { hasDescription(MY_LOCATION) }.click()
        Ui.device.waitForIdle()
    }

    /** Taps an empty spot of the map: the top left corner, away from the markers and the buttons. */
    fun tapEmptyMap() {
        val map = Ui.find { hasDescription("Google Map") }.visibleBounds
        Ui.device.click(map.left + map.width() / EMPTY_SPOT_DIVISOR, map.top + map.height() / 2)
        Ui.device.waitForIdle()
    }

    private const val MY_LOCATION = "My location"
    private const val DATE_RANGE = "bt_dateRange"
    private const val PIE_CHART = "pieChart"
    private const val POLL_MS = 200L
    private const val EMPTY_SPOT_DIVISOR = 10
    private const val CHART_GRID = 12
    private const val CAMERA_MOVE_MS = 1_000L
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

    // The title and description are at the top of the form: scroll back up first, the form may be scrolled down.
    fun typeTitle(title: String) {
        Ui.scrollTo { isTitleField() }
        Ui.find { isTitleField() }.text = title
        Ui.device.waitForIdle()
    }

    fun assertTitle(title: String) {
        Ui.scrollTo { isTitleField() }
        Ui.assertShown("The title \"$title\"") { isTitleField() && hasText(title) }
    }

    private fun AccessibilityNodeInfo.isTitleField() = hasHint("Title") && isEditable

    /** Taps "Save Memory", or "Update" while editing. */
    fun save() {
        Ui.scrollTo { hasText(SAVE) || hasText(UPDATE) }
        Ui.findSettled { hasText(SAVE) || hasText(UPDATE) }.click()
        Ui.device.waitForIdle()
    }

    /** The form edits an existing memory: its button updates that memory instead of saving a new one. */
    fun assertEditing() {
        Ui.scrollTo { hasText(UPDATE) || hasText(SAVE) }
        Ui.assertShown("The Update button of a memory being edited") { hasText(UPDATE) }
    }

    /** Opens the location picker for the memory (its own "Select on Map", at the top of the form, not a fragment's). */
    fun selectLocation() {
        Ui.scrollToTop()
        Ui.find { hasText(SELECT_LOCATION) }.click()
        Ui.device.waitForIdle()
    }

    fun assertNoLocation() = assertEquals(NO_LOCATION, locationText())

    /** The memory's location is [place]: its name, found by the address lookup, or its coordinates if that failed. */
    fun assertLocationIs(place: Place) {
        val text = locationText().orEmpty()
        val coordinates = "${place.latitude}, ${place.longitude}"
        assertTrue(
            "The location \"$text\" is not ${place.name}",
            place.name?.let { it in text } == true || coordinates in text
        )
    }

    fun assertHasLocation() = assertNotEquals("No location was picked", NO_LOCATION, locationText())

    // The memory's location, near the top of the form; a fragment card further down has a location line too.
    private fun locationText(): String? {
        Ui.scrollToTop()
        return Ui.find { hasId("tv_location") }.text
    }

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

    /**
     * Opens the memory's "Date & Time" section if it is closed. Just after a date or time dialog closes, the section's
     * buttons can be off screen for a moment, or a tap can land on the closing dialog, so this checks again after
     * each tap on the header and taps again (a few times) until the section is open.
     */
    private fun expandDateSection() {
        var wait = 0L
        repeat(EXPAND_TRIES) {
            if (Ui.isShown(timeoutMs = wait) { hasId("bt_startDate") }) return
            Ui.find { hasText(DATE_AND_TIME) }.click()
            wait = EXPAND_WAIT_MS
        }
        Ui.find { hasId("bt_startDate") }
    }

    fun typeDescription(text: String) {
        Ui.scrollTo { hasHint("Description") && isEditable }
        Ui.find { hasHint("Description") && isEditable }.text = text
        Ui.device.waitForIdle()
    }

    /** The title field is empty: a new, cleared form. */
    fun assertNoTitle() {
        Ui.scrollTo { isTitleField() }
        Ui.assertShown("An empty title") { isTitleField() && textAsString().let { it.isNullOrEmpty() || it == "Title" } }
    }

    /** The form saves a new memory: its button says "Save Memory", not "Update". */
    fun assertNew() {
        Ui.scrollTo { hasText(UPDATE) || hasText(SAVE) }
        Ui.assertShown("The Save Memory button of a new memory") { hasText(SAVE) }
    }

    /** Taps "Add Photos/Videos", which opens the photo picker (the fake one in the tests). */
    fun addPhotos() = tap("Add Photos/Videos")

    /** The form shows "[count] items selected" under its photos. */
    fun assertSelectedCount(count: Int) {
        Ui.scrollTo { hasText("$count items selected") }
        Ui.assertShown("$count picked photos") { hasText("$count items selected") }
    }

    fun removeFirstPhoto() = tap { hasDescription("Remove photo") }

    /** A picked video shows among the picked items (with its own remove button). */
    fun assertVideoSelected() {
        Ui.scrollTo { hasDescription("Remove video") }
        Ui.assertShown("A picked video") { hasDescription("Remove video") }
    }

    /** Taps Clear, which asks first ([Dialog] "Clear Fields", or "Discard Changes" while editing). */
    fun clear() = tap("Clear")

    /** Turns the All Day switch on or off. */
    fun setAllDay(on: Boolean) {
        expandDateSection()
        val switch = Ui.find { hasText("All Day?") }
        if (switch.isChecked != on) switch.click()
        Ui.assertShown("All Day ${if (on) "on" else "off"}") { hasText("All Day?") && isChecked == on }
    }

    /** Opens the marker colour's sliders and picks the preset [color], which moves them to it. */
    fun pickColor(color: Hsv) {
        Ui.scrollTo { hasText(MARKER_COLOR) }
        if (!Ui.isShown(timeoutMs = 0) { hasId("tv_HueValue") }) Ui.find { hasText(MARKER_COLOR) }.click()
        ColorPresetsSection.select(color)
    }

    /** The marker colour's sliders show [color]. */
    fun assertColor(color: Hsv) {
        Ui.scrollTo { hasText(MARKER_COLOR) }
        if (!Ui.isShown(timeoutMs = 0) { hasId("tv_HueValue") }) Ui.find { hasText(MARKER_COLOR) }.click()
        ColorPresetsSection.assertSlidersShow(color)
    }

    fun addFragment() = tap("Add a Memory Fragment")

    /** Opens the location picker for the first fragment (its own "Select on Map", not the memory's). */
    fun selectFragmentLocation() {
        fragmentCard().findObject(By.text(SELECT_LOCATION)).click()
        Ui.device.waitForIdle()
    }

    fun assertFragmentHasLocation() = assertNotEquals("The fragment has no location", NO_LOCATION, fragmentLocation())

    fun assertFragmentHasNoLocation() = assertEquals(NO_LOCATION, fragmentLocation())

    private fun fragmentLocation(): String? =
        fragmentCard().findObject(By.res(Ui.device.currentPackageName, "tv_location")).text

    fun removeFragment() = tap { hasDescription(REMOVE_FRAGMENT) }

    fun assertNoFragments() = Ui.assertGone("A fragment") { hasDescription(REMOVE_FRAGMENT) }

    /**
     * Gives the first fragment dates of its own ("Use specific time for this fragment") and opens the picker for its
     * start date.
     */
    fun openFragmentStartDatePicker() {
        tapInFragmentCard(By.text(DATE_AND_TIME))
        tapInFragmentCard(By.text("Use specific time for this fragment"))
        tapInFragmentCard(By.res(Ui.device.currentPackageName, "bt_startDate"))
    }

    /** Picks [color] from the first fragment's own presets. */
    fun pickFragmentColor(color: Hsv) = tapInFragmentCard(By.desc(ColorPresetsSection.describe(color)))

    // The card's "Date & Time" part and colour presets are named like the memory's own: look inside the card.
    private fun tapInFragmentCard(element: BySelector) {
        checkNotNull(fragmentCard(element).findObject(element)) { "The fragment has no $element on screen" }.click()
        Ui.device.waitForIdle()
    }

    /**
     * The first fragment's card, with [part] on screen (by default its location part). Scrolling stops as soon as
     * its remove button (at the top of the card) shows, so this nudges the page down until [part] shows too.
     */
    private fun fragmentCard(part: BySelector = By.text(SELECT_LOCATION)): UiObject2 {
        Ui.scrollTo { hasDescription(REMOVE_FRAGMENT) }
        repeat(MAX_NUDGES) {
            val card = Ui.find { hasId("fragmentEditCard") }
            if (card.findObject(part) != null) return card
            Ui.scrollDownALittle()
        }
        return Ui.find { hasId("fragmentEditCard") }
    }

    private const val MAX_NUDGES = 5
    private const val DATE_AND_TIME = "Date & Time"
    private const val EXPAND_TRIES = 3
    private const val EXPAND_WAIT_MS = 2_000L
    private const val MARKER_COLOR = "Marker Color"
    private const val REMOVE_FRAGMENT = "Remove fragment"

    private fun tap(text: String) = tap { hasText(text) && isEnabled }

    private fun tap(condition: AccessibilityNodeInfo.() -> Boolean) {
        Ui.scrollTo(condition)
        Ui.find(condition = condition).click()
        Ui.device.waitForIdle()
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
        isSwatch(color) && isSelected
    }

    /** The presets appear in this order, from left to right (all on screen). */
    fun assertLeftToRight(vararg colors: Hsv) {
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
        Ui.find { hasDescription("$hour hours") }.click()
        Ui.device.waitForIdle()
        return hour
    }

    private const val HOURS_PER_DAY = 24
}
