package com.szabolcshorvath.memorymap.screens

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.szabolcshorvath.memorymap.screens.ComposeUi.centerOnScreen
import com.szabolcshorvath.memorymap.screens.ComposeUi.dragOnto
import com.szabolcshorvath.memorymap.screens.ComposeUi.tap
import com.szabolcshorvath.memorymap.screens.ComposeUi.text
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate

/** A memory opened from the map or the timeline (one page of the memory pager). Found through [ComposeUi]. */
object MemoryScreen {
    private const val EDIT = "Edit Memory"

    fun assertShown() {
        ComposeUi.waitFor("A memory", hasContentDescription(EDIT))
    }

    fun assertGone() = ComposeUi.assertGone("The memory", hasContentDescription(EDIT))

    fun assertShowing(title: String) {
        ComposeUi.waitFor("The memory \"$title\"", title(title), unmerged = true)
    }

    private fun title(title: String) = hasTestTag("tv_title") and hasText(title)

    fun showOnMap() = tap("\"$SHOW_ON_MAP\"", hasText(SHOW_ON_MAP))

    fun showOnTimeline() = tap("\"Show on timeline\"", hasText("Show on timeline"))

    fun edit() = tap("The Edit button", hasContentDescription(EDIT))

    fun openFirstPhoto() {
        ComposeUi.scrollTo("A photo", photo).performClick()
    }

    /** Taps Delete in the More menu, which asks for confirmation first ([Dialog] "Delete Memory"). */
    fun delete() {
        tap("The More button", hasContentDescription("More"))
        tap("Delete in the More menu", hasText("Delete"))
    }

    /** Swipes to the next memory in the timeline's order (an older one). */
    fun swipeToOlder() = swipe(left = true)

    /** Swipes to the previous memory in the timeline's order (a newer one). */
    fun swipeToNewer() = swipe(left = false)

    /** The date line, such as "Oct 24, 2026 12:00 PM - 2:00 PM", or the day alone for an all-day memory. */
    fun dateText(): String = ComposeUi.waitFor("The memory's date", hasTestTag("tv_date"), unmerged = true).text()

    fun assertDescription(text: String) {
        ComposeUi.scrollTo("The description \"$text\"", hasTestTag("tv_description") and hasText(text), unmerged = true)
    }

    /** Waits until the memory shows [count] photo thumbnails. */
    fun assertPhotoCount(count: Int) = assertThumbnailCount(photo, "Photo", count)

    /** Waits until the memory shows [count] video thumbnails (marked with a play icon). */
    fun assertVideoCount(count: Int) = assertThumbnailCount(video, "Video", count)

    fun openFirstVideo() {
        ComposeUi.scrollTo("A video", video).performClick()
    }

    private fun assertThumbnailCount(matcher: SemanticsMatcher, what: String, count: Int) {
        if (count > 0) ComposeUi.scrollTo("A thumbnail", matcher)
        val found = Ui.eventually(read = { ComposeUi.nodes(matcher).size }) { it == count }
        assertEquals("$what thumbnails", count, found)
    }

    /** Waits until the memory lists [count] fragments. */
    fun assertFragmentCount(count: Int) {
        if (count > 0) ComposeUi.scrollTo("A fragment", fragmentCard)
        val found = Ui.eventually(read = { ComposeUi.nodes(fragmentCard).size }) { it == count }
        assertEquals("Fragments of the memory", count, found)
    }

    /**
     * The (only) fragment has dates of its own, starting on [date] (its dates read like "Sep 10, 2026 9:00 AM - …").
     * A fragment without dates of its own shows none.
     */
    fun assertFragmentStartsOn(date: LocalDate) {
        val text = fragmentDates()
        val day = DateTimeFormatterUtil.date(date)
        assertTrue("The fragment's dates \"$text\" don't start on $day", text.startsWith(day))
    }

    /** The (only) fragment's dates read exactly [text], such as "Sep 10, 2026 - Sep 12, 2026" for an all-day one. */
    fun assertFragmentDates(text: String) = assertEquals("The fragment's dates", text, fragmentDates())

    private fun fragmentDates(): String {
        ComposeUi.scrollTo("A fragment", fragmentCard)
        val dates = hasTestTag("tv_time") and inFragment
        return if (ComposeUi.exists(dates, unmerged = true)) ComposeUi.node(dates, unmerged = true).text() else ""
    }

    /**
     * The (only) fragment shows the marker colour [color], in the strip along its left edge. Waits for it, in case
     * the fragments section is still opening.
     */
    fun assertFragmentColor(color: Int) {
        val spot = ComposeUi.scrollTo("The fragment's colour strip", hasTestTag("colorIndicator"), unmerged = true).centerOnScreen()
        val shown = Ui.eventually(read = { ScreenColors.at(listOf(spot)).single() }) { ScreenColors.isClose(it, color) }
        assertTrue(
            "The fragment's colour ${ScreenColors.hex(shown)} is not ${ScreenColors.hex(color)}",
            ScreenColors.isClose(shown, color)
        )
    }

    /** The fragments are listed in this order, from top to bottom, by the place each card shows. */
    fun assertFragmentsTopToBottom(vararg places: String) {
        ComposeUi.scrollTo("The fragment at ${places.last()}", fragmentPlace(places.last()), unmerged = true)
        val shown = Ui.eventually(read = ::fragmentPlaces) { it == places.toList() }
        assertEquals("The order of the fragments", places.toList(), shown)
    }

    private fun fragmentPlaces(): List<String> =
        ComposeUi.nodes(hasTestTag(PLACE) and inFragment, unmerged = true).map { it.text() }

    private fun fragmentPlace(place: String) = hasTestTag(PLACE) and hasText(place) and inFragment

    /** Long-presses the fragment card showing [place] and drags it into the place of the one showing [onto]. */
    fun dragFragment(place: String, onto: String) {
        fun card(name: String) = ComposeUi.node(fragmentCard and hasAnyDescendant(hasTestTag(PLACE) and hasText(name)), unmerged = true)
        card(place).dragOnto(card(onto), horizontal = false)
    }

    /** Taps the (only) fragment, which shows it on the map while fragment markers are turned on. */
    fun showFragmentOnMap() = ComposeUi.scrollTo("A fragment", fragmentCard).tap()

    /** Taps "Re-link" on the could-not-load banner, which asks for photo access first if needed. */
    fun relinkMedia() {
        ComposeUi.scrollTo("\"$RELINK\"", hasText(RELINK) and isEnabled()).performClick()
    }

    /**
     * The photos are, from left to right, of these colours (the tests' photos are a single colour each). The
     * thumbnails all read "Open photo", so the colours are read from a screenshot, at each thumbnail's centre. Waits
     * for the thumbnails to load and fade in.
     */
    fun assertPhotoColors(vararg colors: Int) {
        ComposeUi.scrollTo("A photo", photo)

        fun read() = ScreenColors.at(ComposeUi.nodes(photo).map { it.centerOnScreen() })

        fun List<Int>.matches() = size == colors.size && zip(colors.toList()).all { (a, b) -> ScreenColors.isClose(a, b) }
        val shown = Ui.eventually(read = ::read) { it.matches() }
        assertTrue(
            "The photos' colours ${shown.map(ScreenColors::hex)} are not ${colors.map(ScreenColors::hex)}",
            shown.matches()
        )
    }

    /** Opens photo [index] (0 is the first) in the full-screen viewer. */
    fun openPhoto(index: Int) {
        ComposeUi.scrollTo("A photo", photo)
        ComposeUi.nodes(photo)[index].performClick()
    }

    /** Long-presses photo [from] (0 is the first) and drags it into the place of photo [onto]. */
    fun dragPhoto(from: Int, onto: Int) {
        ComposeUi.scrollTo("A photo", photo)
        val photos = ComposeUi.nodes(photo)
        photos[from].dragOnto(photos[onto], horizontal = true)
    }

    /** The photo shows as a thumbnail, without the could-not-load warning. */
    fun assertPhotoLoaded() {
        ComposeUi.scrollTo("A photo thumbnail", photo)
        ComposeUi.assertGone("A thumbnail that could not be loaded", hasTestTag(TILE_DETAILS), unmerged = true)
        ComposeUi.assertGone("The could-not-load warning", mediaWarning)
    }

    /**
     * A photo from another phone shows as an error tile with its type, size and date instead of the picture, and the
     * screen warns that some media could not be loaded.
     */
    fun assertPhotoFromAnotherDevice() = assertPhotoNotLoaded()

    /**
     * A photo the app can't read shows as an error tile with its type, size and date instead of the picture, and the
     * screen warns that some media could not be loaded.
     */
    fun assertPhotoNotLoaded() {
        ComposeUi.scrollTo(
            "The error tile of a photo that could not be loaded",
            hasTestTag(TILE_DETAILS) and ComposeUi.textStartsWith("Photo"),
            unmerged = true
        )
        ComposeUi.scrollTo("The could-not-load warning", mediaWarning)
    }

    private val mediaWarning = ComposeUi.textStartsWith("Some media items could not be loaded")

    /**
     * Swipes across the pager, which turns to the next page once the swipe ends. Compose's own swipe moves the app's
     * clock with it, and the next lookup waits until the page has settled.
     */
    private fun swipe(left: Boolean) {
        ComposeUi.node(hasTestTag("memoryViewPager")).performTouchInput { if (left) swipeLeft() else swipeRight() }
    }

    private fun tap(what: String, matcher: SemanticsMatcher) {
        ComposeUi.waitFor(what, matcher).performClick()
    }

    private val inFragment = hasAnyAncestor(hasTestTag(FRAGMENT))
    private val fragmentCard = hasTestTag(FRAGMENT)
    private val photo = hasContentDescription(OPEN_PHOTO)
    private val video = hasContentDescription(OPEN_VIDEO)

    private const val FRAGMENT = "fragmentCard"
    private const val PLACE = "tv_location"
    private const val TILE_DETAILS = "tvErrorDetails"
    private const val SHOW_ON_MAP = "Show on map"
    private const val RELINK = "Re-link"
    private const val OPEN_PHOTO = "Open photo"
    private const val OPEN_VIDEO = "Open video"
}
