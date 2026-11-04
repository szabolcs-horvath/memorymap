package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.Direction
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.time.LocalDate

/** The Timeline tab. */
object TimelineScreen : TabScreen {
    private const val SEARCH = "Search memories..."
    private const val FLASH_WATCH_MS = 8_000L
    private const val CARD_SPOT_PERCENT = 90

    // The flash colour is close to the card's own (a few steps on one channel): any visible change counts.
    private const val FLASH_TOLERANCE = 8

    // By the list or the text shown instead of an empty one: the search bar scrolls away with the list.
    override fun assertShown() = Ui.assertShown("The timeline") { isTimeline() }

    override fun assertGone() = Ui.assertGone("The timeline") { isTimeline() }

    private fun AccessibilityNodeInfo.isTimeline() = hasId("rv_timeline") || hasId("emptyView")

    /**
     * Runs [action] (such as Show on Timeline) and checks that memory [title]'s card then flashes: its colour changes
     * for a moment. The flash is short, so this doesn't wait for the screen to settle first, and it reads the
     * card's colour from screenshots, near its right edge on the title's line, once the list has stopped moving. It
     * stops watching as soon as the card has shown two clearly different colours.
     */
    fun assertFlashesAfter(title: String, action: () -> Unit) = Ui.withoutSettling {
        action()
        val samples = mutableListOf<Int>()
        var lastTop: Int? = null
        val end = SystemClock.uptimeMillis() + FLASH_WATCH_MS
        while (SystemClock.uptimeMillis() < end) {
            val line = Ui.findOrNull(timeoutMs = 0) { hasId("tv_memoryTitle") && hasText(title) }?.visibleBounds
            if (line != null && line.top == lastTop) {
                val spot = Point(Ui.device.displayWidth * CARD_SPOT_PERCENT / 100, line.centerY())
                val color = ScreenColors.at(listOf(spot)).single()
                if (samples.any { !ScreenColors.isClose(it, color, FLASH_TOLERANCE) }) return@withoutSettling
                samples += color
            }
            lastTop = line?.top
        }
        assertTrue("\"$title\" did not show in the timeline", samples.isNotEmpty())
        fail("\"$title\" did not flash: its card stayed ${samples.distinct().map(ScreenColors::hex)}")
    }

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

    /** Taps the search field, which opens the keyboard, and types [text] in it. */
    fun typeInSearch(text: String) {
        Ui.find { hasHint(SEARCH) && isEditable }.click()
        search(text)
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
