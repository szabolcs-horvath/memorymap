package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.textAsString
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.testing.TestData.Place
import com.szabolcshorvath.memorymap.util.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/** The Add tab: the form for a new memory, or for editing one. */
object AddScreen : TabScreen {
    private const val NO_LOCATION = "No location selected"
    private const val SAVE = "Save memory"
    private const val UPDATE = "Save changes"
    private const val PAGE = "scroll_add"
    private const val LOCATION_ITEM = "bt_selectLocation"
    private const val LOCATION = "tv_location"
    private const val SAVE_BUTTON = "bt_save"
    private const val HALF_DAY = 12

    override fun assertShown() = Ui.assertShown("The Add form") { hasId(PAGE) }

    override fun assertGone() = Ui.assertGone("The Add form") { hasId(PAGE) }

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

    // A Compose text field, found by its tag: Compose shows its label as text inside the field, not as its hint.
    private fun AccessibilityNodeInfo.isTitleField() = hasId("titleInput") && isEditable

    /** Taps "Save memory", or "Save changes" while editing: pinned under the form, it needs no scrolling. */
    fun save() {
        Ui.findSettled { hasId(SAVE_BUTTON) }.click()
        Ui.device.waitForIdle()
    }

    /** The form edits an existing memory: its button saves the changes instead of saving a new memory. */
    fun assertEditing() {
        Ui.assertShown("The Save changes button of a memory being edited") { isSaveButtonText(UPDATE) }
    }

    /** The form saves a new memory: its button says "Save memory", not "Save changes". */
    fun assertNew() {
        Ui.assertShown("The Save memory button of a new memory") { isSaveButtonText(SAVE) }
    }

    // A Compose button's text is a node inside the tagged button.
    private fun AccessibilityNodeInfo.isSaveButtonText(text: String) = hasText(text) && parent?.hasId(SAVE_BUTTON) == true

    /** Opens the location picker for the memory: its place row, near the top of the form (a fragment has its own). */
    fun selectLocation() {
        Ui.scrollToTop()
        Ui.find { hasId(LOCATION_ITEM) }.click()
        Ui.device.waitForIdle()
    }

    fun assertNoLocation() = assertEquals(NO_LOCATION, locationText())

    /** The memory's location has no name or address, only its coordinates (as when the address lookup failed). */
    fun assertLocationIsCoordinates() {
        val text = locationText().orEmpty()
        assertTrue("The location \"$text\" is not just coordinates", COORDINATES.matches(text))
    }

    /**
     * The memory's location is [place]: its name (found by the address lookup) or its coordinates if that failed,
     * and under it its address if it has one.
     */
    fun assertLocationIs(place: Place) {
        val text = locationText().orEmpty()
        val coordinates = Coordinates.format(InstrumentationRegistry.getInstrumentation().targetContext, place.latitude, place.longitude)
        assertTrue(
            "The location \"$text\" is not ${place.name}",
            place.name?.let { it in text } == true || coordinates in text
        )
        place.address?.let { address ->
            if (address !in text) Ui.assertShown("The address \"$address\" of the location") { hasText(address) }
        }
    }

    fun assertHasLocation() = assertNotEquals("No location was picked", NO_LOCATION, locationText())

    /** The form says the memory needs a title and a place, after a save without them. */
    fun assertMissingTitleAndPlace() {
        Ui.scrollToTop()
        Ui.assertShown("The missing title's message") { hasText(NEEDS_TITLE) }
        Ui.assertShown("The missing place's message") { hasText(NEEDS_PLACE) }
    }

    // The memory's location, near the top of the form; a fragment card further down has a location line too.
    private fun locationText(): String? {
        Ui.scrollToTop()
        return Ui.find { hasId(LOCATION) }.text
    }

    fun openStartDatePicker() = tapInDateSection("bt_startDate")

    fun openStartTimePicker() = tapInDateSection("bt_startTime")

    fun openEndDatePicker() = tapInDateSection("bt_endDate")

    fun openEndTimePicker() = tapInDateSection("bt_endTime")

    // The memory's date buttons come first; a fragment's further down have the same names.
    private fun tapInDateSection(id: String) {
        Ui.scrollTo { hasId(id) }
        Ui.find { hasId(id) }.click()
        Ui.device.waitForIdle()
    }

    fun startDateText(): String? = dateSectionText("bt_startDate")

    fun startTimeText(): String? = dateSectionText("bt_startTime")

    private fun dateSectionText(id: String): String? {
        Ui.scrollTo { hasId(id) }
        return Ui.find { hasId(id) }.textOrChildText()
    }

    /** A Compose button's text: on the button itself, or on the text inside it. */
    private fun UiObject2.textOrChildText(): String? = text ?: findObject(By.textStartsWith(""))?.text

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

    /** The start time reads as [time] in full, such as `20:\d{2}` on a 24-hour clock (spaces before AM and PM are plain). */
    fun assertStartTime(time: Regex) = assertTime("The start time", dateSectionText("bt_startTime"), time)

    fun assertEndTime(time: Regex) = assertTime("The end time", dateSectionText("bt_endTime"), time)

    private fun assertTime(what: String, text: String?, time: Regex) {
        val shown = text.orEmpty().replace(' ', ' ') // The narrow space Android puts before AM and PM.
        assertTrue("$what \"$text\" doesn't read as $time", time.matches(shown))
    }

    fun typeDescription(text: String) {
        Ui.scrollTo { hasId("descriptionInput") && isEditable }
        Ui.find { hasId("descriptionInput") && isEditable }.text = text
        Ui.device.waitForIdle()
    }

    /** The title field is empty: a new, cleared form. */
    fun assertNoTitle() {
        Ui.scrollTo { isTitleField() }
        Ui.assertShown("An empty title") { isTitleField() && textAsString().let { it.isNullOrEmpty() || it == "Title" } }
    }

    /** Taps "Add photos and videos", which opens the photo picker (the fake one in the tests). */
    fun addPhotos() = tap("Add photos and videos")

    /** The form shows "[count] items selected" ("1 item selected") by its photos' header. */
    fun assertSelectedCount(count: Int) {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getQuantityString(R.plurals.items_selected, count, count)
        Ui.scrollTo { hasText(text) }
        Ui.assertShown("$count picked photos") { hasText(text) }
    }

    fun removeFirstPhoto() = tap { hasDescription("Remove photo") }

    /** A picked video shows among the picked items (with its own remove button). */
    fun assertVideoSelected() {
        Ui.scrollTo { hasDescription("Remove video") }
        Ui.assertShown("A picked video") { hasDescription("Remove video") }
    }

    /** Taps Clear (Discard while editing) in the top bar, which asks first ([Dialog] "Clear Fields", or "Discard Changes"). */
    fun clear() {
        Ui.find { hasId("bt_clear") }.click()
        Ui.device.waitForIdle()
    }

    /** Turns the memory's All day switch on or off (a fragment's further down has the same name). */
    fun setAllDay(on: Boolean) {
        Ui.scrollTo { hasId(ALL_DAY) }
        val switch = Ui.find { hasId(ALL_DAY) }
        if (switch.isChecked != on) switch.click()
        assertAllDay(on)
    }

    fun assertAllDay(on: Boolean) {
        Ui.scrollTo { hasId(ALL_DAY) }
        Ui.assertShown("All day ${if (on) "on" else "off"}") { hasId(ALL_DAY) && isChecked == on }
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

    fun addFragment() = tap("Add a memory fragment")

    /** Opens the location picker for the first fragment: its own place row, not the memory's. */
    fun selectFragmentLocation() {
        fragmentCard().findObject(By.res(LOCATION)).click()
        Ui.device.waitForIdle()
    }

    fun assertFragmentHasLocation() = assertNotEquals("The fragment has no location", NO_LOCATION, fragmentLocation())

    fun assertFragmentHasNoLocation() = assertEquals(NO_LOCATION, fragmentLocation())

    private fun fragmentLocation(): String? = fragmentCard().findObject(By.res(LOCATION)).text

    fun removeFragment() = tap { hasDescription(REMOVE_FRAGMENT) }

    fun assertNoFragments() = Ui.assertGone("A fragment") { hasDescription(REMOVE_FRAGMENT) }

    /** Gives the first fragment dates of its own ("Own date and time") and opens the picker for its start date. */
    fun openFragmentStartDatePicker() {
        tapInFragmentCard(By.res(OWN_TIME))
        tapInFragmentCard(By.res("bt_startDate"))
    }

    /**
     * Gives the first fragment dates of its own (unless it [hasOwnDates] already), makes it all day, and opens the
     * calendar for its range of days ([DateRangePickerDialog]).
     */
    fun openFragmentAllDayRangePicker(hasOwnDates: Boolean = false) {
        if (!hasOwnDates) tapInFragmentCard(By.res(OWN_TIME))
        tapInFragmentCard(By.res(ALL_DAY))
        tapInFragmentCard(By.res("bt_dateRange")) // It shows the days picked so far.
    }

    /** Picks [color] from the first fragment's own presets. */
    fun pickFragmentColor(color: Hsv) = tapInFragmentCard(By.desc(ColorPresetsSection.describe(color)))

    // The card's date buttons and colour presets are named like the memory's own: look inside the card.
    private fun tapInFragmentCard(element: BySelector) {
        checkNotNull(fragmentCard(element).findObject(element)) { "The fragment has no $element on screen" }.click()
        Ui.device.waitForIdle()
    }

    /**
     * The first fragment's card, with [part] on screen (by default its place row). Scrolling stops as soon as its
     * remove button (at the top of the card) shows, so this nudges the page down until [part] shows too.
     */
    private fun fragmentCard(part: BySelector = By.res(LOCATION)): UiObject2 {
        Ui.scrollTo { hasDescription(REMOVE_FRAGMENT) }
        repeat(MAX_NUDGES) {
            val card = Ui.find { hasId("fragmentEditCard") }
            if (card.findObject(part) != null) return card
            Ui.scrollDownALittle()
        }
        return Ui.find { hasId("fragmentEditCard") }
    }

    private const val MAX_NUDGES = 5
    private const val ALL_DAY = "switch_allDay"
    private const val OWN_TIME = "switch_useSpecificTime"
    private const val MARKER_COLOR = "Marker color"
    private const val REMOVE_FRAGMENT = "Remove fragment"
    private const val NEEDS_TITLE = "Give the memory a title"
    private const val NEEDS_PLACE = "Choose where it happened"

    // How Coordinates.format shows a place: "47.4979° N, 19.0402° E".
    private val COORDINATES = Regex(""".+° [NS], .+° [EW]""")

    private fun tap(text: String) = tap { hasText(text) && isEnabled }

    private fun tap(condition: AccessibilityNodeInfo.() -> Boolean) {
        Ui.scrollTo(condition)
        Ui.find(condition = condition).click()
        Ui.device.waitForIdle()
    }
}
