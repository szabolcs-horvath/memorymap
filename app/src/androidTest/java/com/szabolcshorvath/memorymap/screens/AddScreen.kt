package com.szabolcshorvath.memorymap.screens

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.screens.ComposeUi.press
import com.szabolcshorvath.memorymap.screens.ComposeUi.text
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.testing.TestData.Place
import com.szabolcshorvath.memorymap.util.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/**
 * The Add tab: the form for a new memory, or for editing one. Found through Compose's semantics tree ([ComposeUi]),
 * apart from the dialogs it opens ([Dialog]).
 */
object AddScreen : TabScreen {
    private const val NO_LOCATION = "No location selected"
    private const val SAVE = "Save memory"
    private const val UPDATE = "Save changes"
    private const val PAGE = "scroll_add"
    private const val LOCATION_ITEM = "bt_selectLocation"
    private const val LOCATION = "tv_location"
    private const val SAVE_BUTTON = "bt_save"
    private const val HALF_DAY = 12

    /** Inside a fragment's card, whose place, dates, switches and colours are named like the memory's own. */
    internal val inFragment: SemanticsMatcher = hasAnyAncestor(hasTestTag("fragmentEditCard"))

    /** [matcher], for the memory itself: not inside a fragment's card. */
    private fun ofMemory(matcher: SemanticsMatcher) = matcher and !inFragment

    /** [matcher], inside the first fragment's card. */
    private fun ofFragment(matcher: SemanticsMatcher) = matcher and inFragment

    override fun assertShown() {
        ComposeUi.waitFor("The Add form", hasTestTag(PAGE))
    }

    override fun assertGone() = ComposeUi.assertGone("The Add form", hasTestTag(PAGE))

    private val titleField = hasTestTag("titleInput") and hasSetTextAction()

    fun typeTitle(title: String) {
        ComposeUi.scrollTo("The title field", titleField).performTextReplacement(title)
    }

    fun assertTitle(title: String) = assertText("The title", title) { ComposeUi.scrollTo("The title field", titleField).text() }

    /** The title field is empty: a new, cleared form. */
    fun assertNoTitle() = assertText("The title", "") { ComposeUi.scrollTo("The title field", titleField).text() }

    fun typeDescription(text: String) {
        ComposeUi.scrollTo("The description field", hasTestTag("descriptionInput") and hasSetTextAction())
            .performTextReplacement(text)
    }

    /** Taps "Save memory", or "Save changes" while editing: pinned under the form, it needs no scrolling. */
    fun save() {
        ComposeUi.waitFor("The Save button", hasTestTag(SAVE_BUTTON)).performClick()
    }

    /** The form edits an existing memory: its button saves the changes instead of saving a new memory. */
    fun assertEditing() {
        ComposeUi.waitFor("The Save changes button of a memory being edited", hasTestTag(SAVE_BUTTON) and hasText(UPDATE))
    }

    /** The form saves a new memory: its button says "Save memory", not "Save changes". */
    fun assertNew() {
        ComposeUi.waitFor("The Save memory button of a new memory", hasTestTag(SAVE_BUTTON) and hasText(SAVE))
    }

    /** Opens the location picker for the memory: its place row, near the top of the form (a fragment has its own). */
    fun selectLocation() {
        ComposeUi.scrollTo("The memory's place", hasTestTag(LOCATION_ITEM)).press()
    }

    fun assertNoLocation() = assertEquals(NO_LOCATION, locationText())

    /** The memory's location has no name or address, only its coordinates (as when the address lookup failed). */
    fun assertLocationIsCoordinates() {
        val text = locationText()
        assertTrue("The location \"$text\" is not just coordinates", COORDINATES.matches(text))
    }

    /**
     * The memory's location is [place]: its name (found by the address lookup) or its coordinates if that failed,
     * and under it its address if it has one.
     */
    fun assertLocationIs(place: Place) {
        val text = locationText()
        val coordinates = Coordinates.format(InstrumentationRegistry.getInstrumentation().targetContext, place.latitude, place.longitude)
        assertTrue(
            "The location \"$text\" is not ${place.name}",
            place.name?.let { it in text } == true || coordinates in text
        )
        place.address?.let { address ->
            if (address !in text) ComposeUi.waitFor("The address \"$address\" of the location", ofMemory(hasText(address)))
        }
    }

    fun assertHasLocation() = assertNotEquals("No location was picked", NO_LOCATION, locationText())

    /** The form says the memory needs a title and a place, after a save without them. */
    fun assertMissingTitleAndPlace() {
        ComposeUi.scrollTo("The missing title's message", hasText(NEEDS_TITLE))
        ComposeUi.scrollTo("The missing place's message", ofMemory(hasText(NEEDS_PLACE)))
    }

    // The place row merges its texts into one element: its name is tagged inside it, in the tree before merging.
    private fun locationText(): String =
        ComposeUi.scrollTo("The memory's place", ofMemory(hasTestTag(LOCATION)), unmerged = true).text()

    fun openStartDatePicker() = tapDateButton("bt_startDate")

    fun openStartTimePicker() = tapDateButton("bt_startTime")

    fun openEndDatePicker() = tapDateButton("bt_endDate")

    fun openEndTimePicker() = tapDateButton("bt_endTime")

    private fun tapDateButton(tag: String) {
        ComposeUi.scrollTo("The memory's $tag", ofMemory(hasTestTag(tag))).press()
    }

    fun startDateText(): String = dateButtonText("bt_startDate")

    fun startTimeText(): String = dateButtonText("bt_startTime")

    private fun dateButtonText(tag: String): String = ComposeUi.scrollTo("The memory's $tag", ofMemory(hasTestTag(tag))).text()

    /** The start date shows [day] as its day of the month (for example "Sep 15, 2026" for 15). */
    fun assertStartDay(day: Int) = assertDay("The start date", dateButtonText("bt_startDate"), day)

    fun assertEndDay(day: Int) = assertDay("The end date", dateButtonText("bt_endDate"), day)

    private fun assertDay(what: String, text: String, day: Int) =
        assertTrue("$what \"$text\" is not on day $day", Regex("\\b$day\\b").containsMatchIn(text))

    /** The start time shows [hour] (0-23), in the 24-hour or the 12-hour clock ("21:05" or "9:05 PM" for 21). */
    fun assertStartHour(hour: Int) = assertHour("The start time", dateButtonText("bt_startTime"), hour)

    fun assertEndHour(hour: Int) = assertHour("The end time", dateButtonText("bt_endTime"), hour)

    private fun assertHour(what: String, text: String, hour: Int) {
        val shown = text.substringBefore(':').trim().toIntOrNull()
        val twelveHour = (hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }
        assertTrue("$what \"$text\" is not at hour $hour", shown == hour || shown == twelveHour)
    }

    /** The start time reads as [time] in full, such as `20:\d{2}` on a 24-hour clock (spaces before AM and PM are plain). */
    fun assertStartTime(time: Regex) = assertTime("The start time", dateButtonText("bt_startTime"), time)

    fun assertEndTime(time: Regex) = assertTime("The end time", dateButtonText("bt_endTime"), time)

    private fun assertTime(what: String, text: String, time: Regex) {
        val shown = text.replace(NARROW_SPACE, ' ')
        assertTrue("$what \"$text\" doesn't read as $time", time.matches(shown))
    }

    /** Taps "Add photos and videos", which opens the photo picker (the fake one in the tests). */
    fun addPhotos() = tap("Add photos and videos")

    /** The form shows "[count] items selected" ("1 item selected") by its photos' header. */
    fun assertSelectedCount(count: Int) {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getQuantityString(R.plurals.items_selected, count, count)
        ComposeUi.scrollTo("$count picked photos", hasText(text))
    }

    fun removeFirstPhoto() {
        ComposeUi.scrollTo("A picked photo's remove button", hasContentDescription("Remove photo")).press()
    }

    /** A picked video shows among the picked items (with its own remove button). */
    fun assertVideoSelected() {
        ComposeUi.scrollTo("A picked video", hasContentDescription("Remove video"))
    }

    /** Taps Clear (Discard while editing) in the top bar, which asks first ([Dialog] "Clear Fields", or "Discard Changes"). */
    fun clear() {
        ComposeUi.waitFor("The Clear button", hasTestTag("bt_clear")).performClick()
    }

    private val allDay = ofMemory(hasTestTag(ALL_DAY))

    /** Turns the memory's All day switch on or off (a fragment's further down has the same name). */
    fun setAllDay(on: Boolean) {
        val switch = ComposeUi.scrollTo("The memory's All day switch", allDay)
        if (!ComposeUi.exists(allDay and switchedOn(on))) switch.press()
        assertAllDay(on)
    }

    fun assertAllDay(on: Boolean) {
        ComposeUi.scrollTo("The memory's All day switch", allDay)
        ComposeUi.waitFor("All day ${if (on) "on" else "off"}", allDay and switchedOn(on))
    }

    private fun switchedOn(on: Boolean) = if (on) isOn() else isOff()

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
        val header = ComposeUi.scrollTo("The marker colour", ofMemory(hasText(MARKER_COLOR)))
        if (!ComposeUi.exists(ofMemory(hasTestTag("tv_HueValue")))) header.press()
    }

    fun addFragment() = tap("Add a memory fragment")

    /** Opens the location picker for the first fragment: its own place row, not the memory's. */
    fun selectFragmentLocation() {
        // The tag is on the row's text, which has no click of its own: tap it where it is, as a finger does.
        fragmentLocation().performTouchInput { click() }
    }

    fun assertFragmentHasLocation() = assertNotEquals("The fragment has no location", NO_LOCATION, fragmentLocation().text())

    fun assertFragmentHasNoLocation() = assertEquals(NO_LOCATION, fragmentLocation().text())

    private fun fragmentLocation() = ComposeUi.scrollTo("The fragment's place", ofFragment(hasTestTag(LOCATION)), unmerged = true)

    fun removeFragment() {
        ComposeUi.scrollTo("A fragment's remove button", hasContentDescription(REMOVE_FRAGMENT)).press()
    }

    fun assertNoFragments() = ComposeUi.assertGone("A fragment", hasContentDescription(REMOVE_FRAGMENT))

    /** Gives the first fragment dates of its own ("Own date and time") and opens the picker for its start date. */
    fun openFragmentStartDatePicker() {
        tapInFragmentCard(hasTestTag(OWN_TIME))
        tapInFragmentCard(hasTestTag("bt_startDate"))
    }

    /**
     * Gives the first fragment dates of its own (unless it [hasOwnDates] already), makes it all day, and opens the
     * calendar for its range of days ([DateRangePickerDialog]).
     */
    fun openFragmentAllDayRangePicker(hasOwnDates: Boolean = false) {
        if (!hasOwnDates) tapInFragmentCard(hasTestTag(OWN_TIME))
        tapInFragmentCard(hasTestTag(ALL_DAY))
        tapInFragmentCard(hasTestTag("bt_dateRange")) // It shows the days picked so far.
    }

    /** Picks [color] from the first fragment's own presets. */
    fun pickFragmentColor(color: Hsv) = tapInFragmentCard(hasContentDescription(ColorPresetsSection.describe(color)))

    private fun tapInFragmentCard(matcher: SemanticsMatcher) {
        ComposeUi.scrollTo("The fragment's ${matcher.description}", ofFragment(matcher)).press()
    }

    private const val ALL_DAY = "switch_allDay"
    private const val OWN_TIME = "switch_useSpecificTime"
    private const val MARKER_COLOR = "Marker color"
    private const val REMOVE_FRAGMENT = "Remove fragment"
    private const val NEEDS_TITLE = "Give the memory a title"
    private const val NEEDS_PLACE = "Choose where it happened"
    private const val NARROW_SPACE = ' ' // Android puts it before AM and PM.

    // How Coordinates.format shows a place: "47.4979° N, 19.0402° E".
    private val COORDINATES = Regex(""".+° [NS], .+° [EW]""")

    private fun tap(text: String) {
        ComposeUi.scrollTo("\"$text\"", hasText(text) and isEnabled()).press()
    }

    /** Reads [read] until it is [expected] (an edit fills the form a moment after it opens), then checks it. */
    private fun assertText(what: String, expected: String, read: () -> String) =
        assertEquals(what, expected, Ui.eventually(read = read) { it == expected })
}
