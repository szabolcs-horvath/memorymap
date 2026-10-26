package com.szabolcshorvath.memorymap.screens

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.onElement
import androidx.test.uiautomator.onElementOrNull
import androidx.test.uiautomator.simpleViewResourceName
import androidx.test.uiautomator.textAsString
import org.junit.Assert.assertTrue

/**
 * Finds things on screen the way a user sees them: by text, content description or hint, and by a resource id
 * only where nothing visible names the element. Only the screen objects in this package look elements up, so a
 * screen that changes (for example to Compose) changes only its screen object, never the tests.
 */
object Ui {
    const val TIMEOUT_MS = 10_000L
    private const val POLL_MS = 100L

    val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** The first visible element matching [condition] in the active window; fails after [timeoutMs]. */
    fun find(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): UiObject2 =
        device.onElement(timeoutMs) { isVisibleToUser && condition() }

    fun findOrNull(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): UiObject2? =
        device.onElementOrNull(timeoutMs) { isVisibleToUser && condition() }

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
            val page = findOrNull(timeoutMs = 0) { isScrollable && className?.toString()?.endsWith("ScrollView") == true }
            when {
                page == null -> SystemClock.sleep(SCROLL_PAUSE_MS)

                !atTop -> {
                    for (fling in 1..MAX_FLINGS) if (!page.fling(Direction.UP)) break
                    atTop = true
                }

                // At the bottom: wait a moment, then search again from the top.
                !page.scroll(Direction.DOWN, SCROLL_PERCENT, SCROLL_SPEED) -> {
                    SystemClock.sleep(SCROLL_PAUSE_MS)
                    atTop = false
                }
            }
        }
    }

    private const val SCROLL_TIMEOUT_MS = 30_000L
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

fun AccessibilityNodeInfo.hasText(text: String): Boolean = textAsString() == text

fun AccessibilityNodeInfo.hasDescription(description: String): Boolean = contentDescription?.toString() == description

fun AccessibilityNodeInfo.hasHint(hint: String): Boolean = hintText?.toString() == hint

fun AccessibilityNodeInfo.hasId(id: String): Boolean = simpleViewResourceName() == id
