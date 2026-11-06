package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.textAsString
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.testing.TestData.Place
import com.szabolcshorvath.memorymap.util.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

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

    /** The memory's location has no name or address, only its coordinates (as when the address lookup failed). */
    fun assertLocationIsCoordinates() {
        val text = locationText().orEmpty()
        assertTrue("The location \"$text\" is not just coordinates", text.startsWith("Coordinates: "))
    }

    /**
     * The memory's location is [place]: its name (found by the address lookup) or its coordinates if that failed,
     * and its address if it has one.
     */
    fun assertLocationIs(place: Place) {
        val text = locationText().orEmpty()
        val coordinates = Coordinates.format(place.latitude, place.longitude)
        assertTrue(
            "The location \"$text\" is not ${place.name}",
            place.name?.let { it in text } == true || coordinates in text
        )
        place.address?.let { assertTrue("The location \"$text\" doesn't show the address \"$it\"", it in text) }
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

    fun openEndDatePicker() = tapInDateSection("bt_endDate")

    fun openEndTimePicker() = tapInDateSection("bt_endTime")

    private fun tapInDateSection(id: String) {
        expandDateSection()
        Ui.scrollTo { hasId(id) }
        Ui.find { hasId(id) }.click()
        Ui.device.waitForIdle()
    }

    fun startDateText(): String? = dateSectionText("bt_startDate")

    fun startTimeText(): String? = dateSectionText("bt_startTime")

    private fun dateSectionText(id: String): String? {
        expandDateSection()
        Ui.scrollTo { hasId(id) }
        return Ui.find { hasId(id) }.text
    }

    /** The start date shows [day] as its day of the month (for example "Sep 15, 2026" for 15). */
    fun assertStartDay(day: Int) = assertDay("The start date", dateSectionText("bt_startDate"), day)

    fun assertEndDay(day: Int) = assertDay("The end date", dateSectionText("bt_endDate"), day)

    private fun assertDay(what: String, text: String?, day: Int) =
        assertTrue("$what \"$text\" is not on day $day", Regex("\\b$day\\b").containsMatchIn(text.orEmpty()))

    /** The start time shows [hour] (0-23), in the 24-hour or the 12-hour clock ("21:05" or "9:05 PM" for 21). */
    fun assertStartHour(hour: Int) = assertHour("The start time", dateSectionText("bt_startTime"), hour)

    fun assertEndHour(hour: Int) = assertHour("The end time", dateSectionText("bt_endTime"), hour)

    private fun assertHour(what: String, text: String?, hour: Int) {
        val shown = text.orEmpty().substringBefore(':').trim().toIntOrNull()
        val twelveHour = (hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }
        assertTrue("$what \"$text\" is not at hour $hour", shown == hour || shown == twelveHour)
    }

    /**
     * Opens the memory's "Date & Time" section if it is closed. Just after a date or time dialog closes, the section's
     * buttons can be off screen for a moment, or a tap can land on the closing dialog, so this checks again after
     * each tap on the header and taps again (a few times) until the section is open.
     */
    private fun expandDateSection() {
        var wait = 0L
        repeat(EXPAND_TRIES) {
            if (Ui.isShown(timeoutMs = wait) { hasText(ALL_DAY) }) return
            Ui.find { hasText(DATE_AND_TIME) }.click()
            wait = EXPAND_WAIT_MS
        }
        Ui.find { hasText(ALL_DAY) }
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
        val switch = Ui.find { hasText(ALL_DAY) }
        if (switch.isChecked != on) switch.click()
        assertAllDay(on)
    }

    fun assertAllDay(on: Boolean) {
        expandDateSection()
        Ui.assertShown("All Day ${if (on) "on" else "off"}") { hasText(ALL_DAY) && isChecked == on }
    }

    /** Opens the marker colour's sliders and picks the preset [color], which moves them to it. */
    fun pickColor(color: Hsv) {
        openColor()
        ColorPresetsSection.select(color)
    }

    /** The marker colour's sliders show [color]. */
    fun assertColor(color: Hsv) {
        openColor()
        ColorPresetsSection.assertSlidersShow(color)
    }

    /** The marker colour's preset [color] is highlighted as the form's colour, or not. */
    fun assertPresetHighlighted(color: Hsv, highlighted: Boolean = true) {
        openColor()
        Ui.scrollTo { hasId("tv_BrightnessValue") } // The swatches and the sliders below them, on screen.
        if (highlighted) ColorPresetsSection.assertSelected(color) else ColorPresetsSection.assertNotSelected(color)
    }

    /** Taps the marker colour's hue slider at [fraction] of its width, and returns the hue it shows then. */
    fun tapHue(fraction: Float): Int {
        openColor()
        return ColorPresetsSection.tapHue(fraction)
    }

    private fun openColor() {
        Ui.scrollTo { hasText(MARKER_COLOR) }
        if (!Ui.isShown(timeoutMs = 0) { hasId("tv_HueValue") }) Ui.find { hasText(MARKER_COLOR) }.click()
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
        fragmentCard().findObject(By.res(Ui.APP_PACKAGE, "tv_location")).text

    fun removeFragment() = tap { hasDescription(REMOVE_FRAGMENT) }

    fun assertNoFragments() = Ui.assertGone("A fragment") { hasDescription(REMOVE_FRAGMENT) }

    /**
     * Gives the first fragment dates of its own ("Use specific time for this fragment") and opens the picker for its
     * start date.
     */
    fun openFragmentStartDatePicker() {
        tapInFragmentCard(By.text(DATE_AND_TIME))
        tapInFragmentCard(By.text("Use specific time for this fragment"))
        tapInFragmentCard(By.res(Ui.APP_PACKAGE, "bt_startDate"))
    }

    /**
     * Gives the first fragment dates of its own, makes it all day, and opens the calendar for its range of days
     * ([DateRangePickerDialog]).
     */
    fun openFragmentAllDayRangePicker() {
        tapInFragmentCard(By.text(DATE_AND_TIME))
        tapInFragmentCard(By.text("Use specific time for this fragment"))
        tapInFragmentCard(By.text(ALL_DAY))
        tapInFragmentCard(By.res(Ui.APP_PACKAGE, "bt_dateRange")) // It shows the days picked so far.
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
    private const val ALL_DAY = "All Day?"
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
