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
     * Scrolls the page until [condition] matches: down first, and back up once it reaches the bottom (a page can
     * come back scrolled after a rotation). Waits for content that loads later, such as the backup list. A page
     * only scrolls when it is taller than the screen. If nothing matches in time, the caller's check fails.
     */
    fun scrollTo(condition: AccessibilityNodeInfo.() -> Boolean) {
        val end = SystemClock.uptimeMillis() + TIMEOUT_MS
        var direction = Direction.DOWN
        while (!isShown(timeoutMs = 0, condition = condition) && SystemClock.uptimeMillis() < end) {
            val page = findOrNull(timeoutMs = 0) { isScrollable && className?.toString()?.endsWith("ScrollView") == true }
            val canScrollFurther = page?.scroll(direction, SCROLL_PERCENT) ?: false
            if (page != null && !canScrollFurther) {
                direction = if (direction == Direction.DOWN) Direction.UP else Direction.DOWN
            }
            SystemClock.sleep(SCROLL_PAUSE_MS)
        }
    }

    private const val SCROLL_PERCENT = 0.8f
    private const val SCROLL_PAUSE_MS = 200L

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
