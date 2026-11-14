package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.szabolcshorvath.memorymap.screens.ComposeUi.centerOnScreen
import com.szabolcshorvath.memorymap.screens.ComposeUi.tap
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.time.LocalDate

/**
 * The Timeline tab. Found through [ComposeUi]: a memory counts as shown while its card is placed, which a lazy list
 * does only for the rows on screen.
 */
object TimelineScreen : TabScreen {
    private const val SEARCH_FIELD = "et_search"
    private const val FLASH_WATCH_MS = 8_000L
    private const val CARD_SPOT_PERCENT = 90

    // The flash colour is close to the card's own (a few steps on one channel): any visible change counts.
    private const val FLASH_TOLERANCE = 8

    // By the list or the text shown instead of an empty one: the search bar scrolls away with the list.
    private val timeline = hasTestTag("rv_timeline") or hasTestTag("emptyView")
    private val searchView = hasTestTag(RESULTS) or hasTestTag(NO_RESULTS)

    override fun assertShown() {
        ComposeUi.waitFor("The timeline", timeline)
    }

    override fun assertGone() = ComposeUi.assertGone("The timeline", timeline)

    /**
     * Runs [action] (such as Show on Timeline) and checks that memory [title]'s card then flashes: its colour changes
     * for a moment. It reads the card's colour from screenshots, near its right edge on the title's line, once the
     * list has stopped moving, letting the app catch up between them. It stops watching as soon as the card has shown
     * two clearly different colours.
     */
    fun assertFlashesAfter(title: String, action: () -> Unit) {
        action()
        val samples = mutableListOf<Int>()
        var lastY: Int? = null
        val end = SystemClock.uptimeMillis() + FLASH_WATCH_MS
        while (SystemClock.uptimeMillis() < end) {
            Ui.catchUp()
            val line = memory(title).takeIf { ComposeUi.exists(it, unmerged = true) }
                ?.let { ComposeUi.node(it, unmerged = true).centerOnScreen().y }
            if (line != null && line == lastY) {
                val spot = Point(Ui.device.displayWidth * CARD_SPOT_PERCENT / 100, line)
                val color = ScreenColors.at(listOf(spot)).single()
                if (samples.any { !ScreenColors.isClose(it, color, FLASH_TOLERANCE) }) return
                samples += color
            }
            lastY = line
        }
        assertTrue("\"$title\" did not show in the timeline", samples.isNotEmpty())
        fail("\"$title\" did not flash: its card stayed ${samples.distinct().map(ScreenColors::hex)}")
    }

    // While the search view is open, the memories are its results; the Timeline stays beneath it.
    fun assertMemoryShown(title: String) {
        ComposeUi.waitFor("\"$title\" in the ${where()}", memory(title), unmerged = true)
    }

    fun assertMemoryGone(title: String) = ComposeUi.assertGone("\"$title\" in the ${where()}", memory(title), unmerged = true)

    private fun memory(title: String) = hasTestTag(titleTag()) and hasText(title)

    private fun titleTag() = if (isSearchOpen()) RESULT_TITLE else TITLE

    private fun where() = if (isSearchOpen()) "search results" else "timeline"

    private fun isSearchOpen() = ComposeUi.exists(searchView)

    private val searchField = hasTestTag(SEARCH_FIELD) and hasSetTextAction()

    /** Opens the search view (tapping the search bar), and types [text] in its field. The view stays open. */
    fun search(text: String) {
        if (!isSearchOpen()) {
            ComposeUi.waitFor("The search bar", searchField).performClick()
            ComposeUi.waitFor("The search view", searchView)
        }
        // The open view's field has the focus; the bar's own stays beneath it.
        ComposeUi.waitFor("The search field", searchField and isFocused()).performTextReplacement(text)
    }

    /** Like [search]: tapping the search bar also opens the keyboard. */
    fun typeInSearch(text: String) = search(text)

    /** Closes the search view with its back arrow; the search bar keeps the search. */
    fun closeSearch() {
        ComposeUi.waitFor("The search view's back arrow", hasContentDescription(CLOSE_SEARCH)).performClick()
        ComposeUi.assertGone("The search view", searchView)
    }

    /** Closes the search view with the phone's Back: the first Back closes the keyboard, the next one the view. */
    fun closeSearchWithBack() {
        Ui.pressBack()
        Keyboard.assertHidden()
        Ui.pressBack()
        ComposeUi.assertGone("The search view", searchView)
    }

    private const val TITLE = "tv_memoryTitle"
    private const val RESULTS = "search_results"
    private const val NO_RESULTS = "search_empty"
    private const val RESULT_TITLE = "tv_resultTitle"
    private const val CLOSE_SEARCH = "Close search"

    fun assertSearch(text: String) {
        ComposeUi.waitFor("The search \"$text\"", searchField and hasText(text))
    }

    fun assertEmpty() {
        ComposeUi.waitFor("The empty timeline message", hasText("No memories yet. Add some!"))
    }

    fun assertNoMatches(query: String) {
        ComposeUi.waitFor("The no-match message for \"$query\"", hasText("No memories match \"$query\""))
    }

    /** The header the timeline shows above the memories of [date]. */
    fun dayHeader(date: LocalDate): String = DateTimeFormatterUtil.date(date)

    /** Day headers and memory titles appear in this order, from top to bottom (all on screen). */
    fun assertTopToBottom(vararg texts: String) {
        val tops = texts.map { text ->
            val matcher: SemanticsMatcher = (hasTestTag("tv_date") or hasTestTag(TITLE)) and hasText(text)
            ComposeUi.waitFor("\"$text\"", matcher, unmerged = true).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals("The timeline order of ${texts.toList()}", tops.sorted(), tops)
        assertEquals("Two of ${texts.toList()} share a row", tops.size, tops.toSet().size)
    }

    /** Swipes the list up by most of a screen, as by hand (which also hides the search bar). */
    fun scrollDown() {
        ComposeUi.waitFor("The timeline", hasTestTag("rv_timeline")).performTouchInput { swipeUp() }
    }

    fun assertScrollToTopShown() {
        ComposeUi.waitFor("The Scroll to top button", hasContentDescription(SCROLL_TO_TOP))
    }

    fun assertScrollToTopGone() = ComposeUi.assertGone("The Scroll to top button", hasContentDescription(SCROLL_TO_TOP))

    fun scrollToTop() {
        ComposeUi.waitFor("The Scroll to top button", hasContentDescription(SCROLL_TO_TOP)).performClick()
    }

    private const val SCROLL_TO_TOP = "Scroll to top"

    /** Opens memory [title] from the timeline, or from the search results while the search view is open. */
    fun openMemory(title: String) {
        // The title is a line inside the card: a tap on it taps the card.
        ComposeUi.waitFor("\"$title\" in the ${where()}", memory(title), unmerged = true).tap()
    }
}
