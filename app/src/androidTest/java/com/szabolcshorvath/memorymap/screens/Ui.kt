package com.szabolcshorvath.memorymap.screens

import android.graphics.Color
import android.graphics.Point
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
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
 * The phone, and what [ComposeUi] can't reach: Android's own dialogs and apps, the map's markers (inside Google's
 * map view) and the keyboard, found through the accessibility tree with UI Automator. Its waits let the app catch up
 * ([catchUp]). Only the screen objects in this package look elements up, so a screen that changes changes only its
 * screen object, never the tests.
 */
object Ui {
    const val TIMEOUT_MS = 10_000L

    /** For what a busy emulator can be much slower to show: the map's first markers, and the end of a re-link. */
    const val SLOW_TIMEOUT_MS = 30_000L
    private const val POLL_MS = 100L

    val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** The app's package, for elements found by resource id (`By.res`) and for telling the app's windows apart. */
    val APP_PACKAGE: String get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    /** Checks [condition] every [pollMs] until it holds or [timeoutMs] has passed, and returns whether it held. */
    fun waitUntil(timeoutMs: Long = TIMEOUT_MS, pollMs: Long = POLL_MS, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        catchUp()
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) return false
            SystemClock.sleep(pollMs)
            catchUp()
        }
        return true
    }

    /** Lets the app's Compose screens catch up with the time that passed ([ComposeUi.catchUp]). */
    fun catchUp() = ComposeUi.catchUp()

    /**
     * Reads a value with [read] every [pollMs] until it [matches] or [timeoutMs] has passed, and returns the last one
     * read, for the caller's check and its message (such as the colours that were on screen instead).
     */
    fun <T> eventually(
        timeoutMs: Long = TIMEOUT_MS,
        pollMs: Long = POLL_MS,
        read: () -> T,
        matches: (T) -> Boolean,
    ): T {
        var value = read()
        var first = true
        waitUntil(timeoutMs, pollMs) {
            if (!first) value = read()
            first = false
            matches(value)
        }
        return value
    }

    /** The first visible element matching [condition] in the active window; fails after [timeoutMs]. */
    fun find(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): UiObject2 =
        findOrNull(timeoutMs, condition) ?: device.onElement(timeoutMs = 0) { isVisibleToUser && condition() }

    fun findOrNull(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): UiObject2? {
        var found: UiObject2? = null
        waitUntil(timeoutMs) {
            found = device.onElementOrNull(timeoutMs = 0) { isVisibleToUser && condition() }
            found != null
        }
        return found
    }

    /** Waits up to [timeoutMs] for a visible element matching [condition]. */
    fun isShown(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): Boolean =
        findOrNull(timeoutMs, condition) != null

    /** Waits up to [timeoutMs] until no visible element matches [condition]. */
    fun isGone(timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean): Boolean =
        waitUntil(timeoutMs) { findOrNull(timeoutMs = 0, condition) == null }

    fun assertShown(what: String, timeoutMs: Long = TIMEOUT_MS, condition: AccessibilityNodeInfo.() -> Boolean) =
        assertTrue("$what is not shown", isShown(timeoutMs, condition))

    fun assertGone(what: String, condition: AccessibilityNodeInfo.() -> Boolean) =
        assertTrue("$what is still shown", isGone(condition = condition))

    fun pressBack() {
        device.pressBack()
        device.waitForIdle()
        catchUp()
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

    /** Whether [a] and [b] are the same colour, give or take [tolerance] per channel (what JPEG and scaling do). */
    fun isClose(a: Int, b: Int, tolerance: Int = TOLERANCE) = abs(Color.red(a) - Color.red(b)) < tolerance &&
        abs(Color.green(a) - Color.green(b)) < tolerance &&
        abs(Color.blue(a) - Color.blue(b)) < tolerance

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

/** How a Google map describes itself to accessibility. */
const val GOOGLE_MAP = "Google Map"

/**
 * The title of the map marker this element is, or null if it isn't one. A map shows its markers as elements inside
 * its own ([GOOGLE_MAP]). The latest map renderer describes a marker by its title ("Paris trip"); the legacy renderer
 * (on devices without it, see docs/ui-tests.md) adds the snippet, which the app leaves empty ("Paris trip. ").
 */
fun AccessibilityNodeInfo.mapMarkerTitle(): String? {
    if (parent?.contentDescription?.toString() != GOOGLE_MAP) return null
    return markerTitle(contentDescription)
}

/** The titles of the markers on the map on screen, read from the map's own element (none if no map shows). */
fun mapMarkerTitles(): List<String> =
    Ui.findOrNull(timeoutMs = 0) { hasDescription(GOOGLE_MAP) }?.children?.mapNotNull { markerTitle(it.contentDescription) }
        .orEmpty()

private fun markerTitle(description: CharSequence?): String? =
    description?.toString()?.trimEnd()?.removeSuffix(".")?.takeIf { it.isNotEmpty() }

fun AccessibilityNodeInfo.hasText(text: String): Boolean = textAsString() == text

fun AccessibilityNodeInfo.hasDescription(description: String): Boolean = contentDescription?.toString() == description

fun AccessibilityNodeInfo.hasId(id: String): Boolean = simpleViewResourceName() == id
