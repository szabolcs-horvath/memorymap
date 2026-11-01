package com.szabolcshorvath.memorymap.screens

import android.graphics.Color
import android.graphics.Point
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.onElement
import androidx.test.uiautomator.onElementOrNull
import androidx.test.uiautomator.simpleViewResourceName
import androidx.test.uiautomator.textAsString
import org.junit.Assert.assertTrue
import java.util.Locale
import kotlin.math.abs

/**
 * Finds things on screen the way a user sees them: by text, content description or hint, and by a resource id
 * only where nothing visible names the element. Only the screen objects in this package look elements up, so a
 * screen that changes (for example to Compose) changes only its screen object, never the tests.
 */
object Ui {
    const val TIMEOUT_MS = 10_000L
    private const val POLL_MS = 100L
    private const val SETTLE_TIMEOUT_MS = 2_000L
    private const val SETTLE_CHECK_MS = 150L

    val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** The first visible element matching [condition] in the active window; fails after [timeoutMs]. */
    fun find(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): UiObject2 =
        device.onElement(timeoutMs) { isVisibleToUser && condition() }

    fun findOrNull(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): UiObject2? =
        device.onElementOrNull(timeoutMs) { isVisibleToUser && condition() }

    /**
     * Like [find], but waits until the element stops moving, for example while a list above it shrinks after a
     * removal. A tap on an element that is still moving can land where it just was.
     */
    fun findSettled(condition: AccessibilityNodeInfo.() -> Boolean): UiObject2 {
        val end = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
        var element = find(condition = condition)
        while (SystemClock.uptimeMillis() < end) {
            val bounds = element.visibleBounds
            SystemClock.sleep(SETTLE_CHECK_MS)
            element = find(condition = condition)
            if (element.visibleBounds == bounds) break
        }
        return element
    }

    /** Waits up to [timeoutMs] for a visible element matching [condition]. */
    fun isShown(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): Boolean =
        findOrNull(timeoutMs, condition) != null

    /** Waits up to [timeoutMs] until no visible element matches [condition]. */
    fun isGone(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            if (findOrNull(timeoutMs = 0, condition) == null) return true
            if (SystemClock.uptimeMillis() >= end) return false
            SystemClock.sleep(POLL_MS)
        }
    }

    fun assertShown(what: String, condition: AccessibilityNodeInfo.() -> Boolean) =
        assertTrue("$what is not shown", isShown(condition = condition))

    fun assertGone(what: String, condition: AccessibilityNodeInfo.() -> Boolean) =
        assertTrue("$what is still shown", isGone(condition = condition))

    /**
     * Scrolls the page until [condition] matches: back to the top first (a page can come back scrolled after a
     * rotation), then down. If it reaches the bottom without a match, it starts again from the top, which waits for
     * content that loads later, such as the backup list. A page only scrolls when it is taller than the screen. If
     * nothing matches in time, the caller's check fails.
     */
    fun scrollTo(condition: AccessibilityNodeInfo.() -> Boolean) {
        val end = SystemClock.uptimeMillis() + SCROLL_TIMEOUT_MS
        var atTop = false
        while (!isShown(timeoutMs = 0, condition = condition) && SystemClock.uptimeMillis() < end) {
            atTop = try {
                scrollStep(atTop)
            } catch (_: StaleObjectException) {
                // The screen replaced the page meanwhile (for example a tab that just opened builds its view): find
                // the new page and start again from the top.
                false
            }
        }
    }

    /** One step of [scrollTo]: up to the top if the page isn't there yet, else down. Returns whether it is at the top. */
    private fun scrollStep(atTop: Boolean): Boolean {
        val page = findOrNull(timeoutMs = 0) { isPage() }
        return when {
            page == null -> {
                SystemClock.sleep(SCROLL_PAUSE_MS)
                atTop
            }

            !atTop -> {
                // A fling that can't move the page waits seconds for a scroll that never comes: ask first.
                for (fling in 1..MAX_FLINGS) if (!canScroll(up = true) || !page.fling(Direction.UP)) break
                true
            }

            !canScroll(up = false) || !page.scroll(Direction.DOWN, SCROLL_PERCENT, SCROLL_SPEED) -> {
                // At the bottom: wait a moment, then search again from the top.
                SystemClock.sleep(SCROLL_PAUSE_MS)
                false
            }

            else -> true
        }
    }

    private fun AccessibilityNodeInfo.isPage() = isScrollable && className?.toString()?.endsWith("ScrollView") == true

    /** Whether the page can scroll further [up] (or down): it offers that scroll action only when it can. */
    private fun canScroll(up: Boolean): Boolean {
        val action = if (up) AccessibilityAction.ACTION_SCROLL_BACKWARD else AccessibilityAction.ACTION_SCROLL_FORWARD
        return isShown(timeoutMs = 0) { isPage() && actionList.contains(action) }
    }

    /** Scrolls the page all the way up, for an element that has a namesake further down (a fragment's). */
    fun scrollToTop() {
        val page = findOrNull(timeoutMs = 0) { isPage() } ?: return
        try {
            for (fling in 1..MAX_FLINGS) if (!canScroll(up = true) || !page.fling(Direction.UP)) break
        } catch (_: StaleObjectException) {
            scrollToTop()
        }
        device.waitForIdle()
    }

    /** Scrolls the page down by about a third of the screen. */
    fun scrollDownALittle() {
        if (!canScroll(up = false)) return
        findOrNull(timeoutMs = 0) { isPage() }?.scroll(Direction.DOWN, NUDGE_PERCENT, SCROLL_SPEED)
        device.waitForIdle()
    }

    private const val SCROLL_TIMEOUT_MS = 30_000L
    private const val NUDGE_PERCENT = 0.3f
    private const val SCROLL_PERCENT = 0.7f

    // Pixels per second. Slow enough not to fling, so a scroll never carries the page past what it looks for.
    private const val SCROLL_SPEED = 1000
    private const val SCROLL_PAUSE_MS = 300L
    private const val MAX_FLINGS = 10

    fun pressBack() {
        device.pressBack()
        device.waitForIdle()
    }

    fun rotateToLandscape() {
        device.setOrientationLeft()
        device.waitForIdle()
    }

    fun rotateToPortrait() {
        device.setOrientationNatural()
        device.waitForIdle()
    }
}

/**
 * Colours read from a screenshot. The tests' photos are plain colours, which nothing on screen names (every
 * thumbnail reads "Open photo"), so this is how a test tells them apart.
 */
object ScreenColors {
    private const val TOLERANCE = 60

    /** The colours on screen at [points], from one screenshot. */
    fun at(points: List<Point>): List<Int> {
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        return points.map { screenshot.getPixel(it.x, it.y) }.also { screenshot.recycle() }
    }

    /** Whether [a] and [b] are the same colour, give or take what JPEG and scaling do to it. */
    fun isClose(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) < TOLERANCE &&
        abs(Color.green(a) - Color.green(b)) < TOLERANCE &&
        abs(Color.blue(a) - Color.blue(b)) < TOLERANCE

    fun hex(color: Int): String = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

    /** [what] is shown in a dark theme: on average, the screen below the status bar is dark. */
    fun assertDark(what: String) {
        val width = Ui.device.displayWidth
        val height = Ui.device.displayHeight
        val grid = (1 until GRID).flatMap { i ->
            (1 until GRID).map { j -> Point(width * i / GRID, height * j / GRID) }
        }
        val brightness = at(grid).map { (Color.red(it) + Color.green(it) + Color.blue(it)) / RGB_CHANNELS }.average()
        assertTrue("$what is not dark: its average brightness is ${brightness.toInt()} of 255", brightness < DARK)
    }

    private const val GRID = 10
    private const val RGB_CHANNELS = 3
    private const val DARK = 100
}

fun AccessibilityNodeInfo.hasText(text: String): Boolean = textAsString() == text

fun AccessibilityNodeInfo.hasDescription(description: String): Boolean = contentDescription?.toString() == description

fun AccessibilityNodeInfo.hasHint(hint: String): Boolean = hintText?.toString() == hint

fun AccessibilityNodeInfo.hasId(id: String): Boolean = simpleViewResourceName() == id
