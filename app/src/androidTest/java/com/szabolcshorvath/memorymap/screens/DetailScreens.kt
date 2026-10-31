package com.szabolcshorvath.memorymap.screens

import android.graphics.Color
import android.graphics.Point
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.textAsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.util.Locale
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import kotlin.math.abs

/** A memory opened from the map or the timeline (one page of the memory pager). */
object MemoryScreen {
    private const val EDIT = "Edit Memory"

    fun assertShown() = Ui.assertShown("A memory") { hasDescription(EDIT) }

    fun assertGone() = Ui.assertGone("The memory") { hasDescription(EDIT) }

    fun assertShowing(title: String) = Ui.assertShown("The memory \"$title\"") { hasId("tv_title") && hasText(title) }

    fun showOnMap() = tap { hasText("Show on Map") }

    fun showOnTimeline() = tap { hasText("Show on Timeline") }

    fun edit() = tap { hasDescription(EDIT) }

    fun openFirstPhoto() = tap { hasDescription("Open photo") }

    /** Taps Delete, which asks for confirmation first ([Dialog] "Delete Memory"). */
    fun delete() = tap { hasDescription("Delete Memory") }

    /** Swipes to the next memory in the timeline's order (an older one). */
    fun swipeToOlder() = swipe(Direction.LEFT)

    /** Swipes to the previous memory in the timeline's order (a newer one). */
    fun swipeToNewer() = swipe(Direction.RIGHT)

    /** The date line, such as "Oct 24, 2026 12:00 PM - 2:00 PM", or the day alone for an all-day memory. */
    fun dateText(): String = Ui.find { hasId("tv_date") }.text

    fun assertDescription(text: String) = Ui.assertShown("The description \"$text\"") {
        hasId("tv_description") && hasText(text)
    }

    /** Waits until the memory shows [count] photo thumbnails. */
    fun assertPhotoCount(count: Int) {
        val end = SystemClock.uptimeMillis() + Ui.TIMEOUT_MS
        var found = Ui.device.findObjects(By.desc("Open photo")).size
        while (found != count && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(POLL_MS)
            found = Ui.device.findObjects(By.desc("Open photo")).size
        }
        assertEquals("Photo thumbnails", count, found)
    }

    /** Opens the "Memory Fragments" section if it is closed, and waits until it lists [count] fragments. */
    fun assertFragmentCount(count: Int) {
        Ui.scrollTo { hasText(FRAGMENTS) }
        if (!Ui.isShown(timeoutMs = 0) { hasId("fragmentCard") }) Ui.find { hasText(FRAGMENTS) }.click()
        val cards = By.res(Ui.device.currentPackageName, "fragmentCard")
        val end = SystemClock.uptimeMillis() + Ui.TIMEOUT_MS
        var found = Ui.device.findObjects(cards).size
        while (found != count && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(POLL_MS)
            found = Ui.device.findObjects(cards).size
        }
        assertEquals("Fragments of the memory", count, found)
    }

    /**
     * The photos are, from left to right, of these colours (the tests' photos are a single colour each). The
     * thumbnails all read "Open photo", so the colours are read from a screenshot, at each thumbnail's centre.
     */
    fun assertPhotoColors(vararg colors: Int) {
        Ui.scrollTo { hasDescription(OPEN_PHOTO) }
        SystemClock.sleep(CROSSFADE_MS) // Thumbnails fade in.
        val shown = thumbnails().let { thumbs ->
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            thumbs.map { screenshot.getPixel(it.visibleCenter.x, it.visibleCenter.y) }.also { screenshot.recycle() }
        }
        val matches = shown.size == colors.size && shown.zip(colors.toList()).all { (a, b) -> isClose(a, b) }
        assertTrue("The photos' colours ${shown.map(::hex)} are not ${colors.map(::hex)}", matches)
    }

    /** Long-presses photo [from] (0 is the first) and drags it into the place of photo [onto]. */
    fun dragPhoto(from: Int, onto: Int) {
        Ui.scrollTo { hasDescription(OPEN_PHOTO) }
        val thumbs = thumbnails()
        val target = thumbs[onto].visibleBounds
        // The list moves a photo past another only once it covers most of it: end at the far edge.
        val x = if (onto < from) target.left + EDGE_INSET else target.right - EDGE_INSET
        thumbs[from].drag(Point(x, target.centerY()), DRAG_SPEED)
        Ui.device.waitForIdle()
    }

    private fun thumbnails(): List<UiObject2> = Ui.device.findObjects(By.desc(OPEN_PHOTO))
        .sortedWith(compareBy({ it.visibleBounds.top }, { it.visibleBounds.left }))

    private fun isClose(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) < COLOR_TOLERANCE &&
        abs(Color.green(a) - Color.green(b)) < COLOR_TOLERANCE &&
        abs(Color.blue(a) - Color.blue(b)) < COLOR_TOLERANCE

    private fun hex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

    /** The photo shows as a thumbnail, without the could-not-load warning. */
    fun assertPhotoLoaded() {
        Ui.assertShown("A photo thumbnail") { hasDescription("Open photo") }
        Ui.assertGone("A thumbnail that could not be loaded") { hasId("tvErrorDetails") }
        Ui.assertGone("The could-not-load warning") { isMediaWarning() }
    }

    /**
     * A photo from another phone shows as an error tile with its type, size and date instead of the picture, and the
     * screen warns that some media could not be loaded.
     */
    fun assertPhotoFromAnotherDevice() {
        Ui.assertShown("The error tile of a photo from another phone") {
            hasId("tvErrorDetails") && textAsString().orEmpty().startsWith("IMAGE")
        }
        Ui.assertShown("The could-not-load warning") { isMediaWarning() }
    }

    private fun AccessibilityNodeInfo.isMediaWarning() =
        textAsString().orEmpty().startsWith("Some media items could not be loaded")

    /**
     * Swipes slowly across most of the page, so it turns by distance, not speed. A busy emulator can still deliver a
     * swipe so late that the page doesn't turn, so this swipes again (a few times) until another memory shows.
     */
    private fun swipe(direction: Direction) {
        val before = Ui.find { hasId("tv_title") }.text
        repeat(MAX_SWIPES) {
            Ui.find { hasId("memoryViewPager") }.swipe(direction, SWIPE_PERCENT, SWIPE_SPEED)
            Ui.device.waitForIdle()
            if (Ui.isShown(timeoutMs = SWIPE_TIMEOUT_MS) { hasId("tv_title") && textAsString() != before }) return
        }
    }

    private fun tap(condition: AccessibilityNodeInfo.() -> Boolean) {
        Ui.find(condition = condition).click()
        Ui.device.waitForIdle()
    }

    private const val SWIPE_PERCENT = 0.8f
    private const val POLL_MS = 200L
    private const val FRAGMENTS = "Memory Fragments"
    private const val OPEN_PHOTO = "Open photo"
    private const val CROSSFADE_MS = 600L
    private const val COLOR_TOLERANCE = 60
    private const val EDGE_INSET = 2
    private const val DRAG_SPEED = 600 // Pixels per second.
    private const val SWIPE_SPEED = 2500 // Pixels per second.
    private const val MAX_SWIPES = 3
    private const val SWIPE_TIMEOUT_MS = 3_000L
}

/** The bar at the bottom that confirms an action, such as "Memory deleted" with Undo. */
object Snackbar {
    fun assertShown(text: String) = Ui.assertShown("The message \"$text\"") { hasId("snackbar_text") && hasText(text) }

    /** Waits until the message closes by itself (a long message stays for about three seconds). */
    fun assertClosesByItself(text: String) = assertTrue(
        "The message \"$text\" did not close",
        Ui.isGone(timeoutMs = CLOSE_TIMEOUT_MS) { hasId("snackbar_text") && hasText(text) }
    )

    fun tap(action: String) {
        Ui.find { hasId("snackbar_action") && textAsString().equals(action, ignoreCase = true) }.click()
        Ui.device.waitForIdle()
    }

    private const val CLOSE_TIMEOUT_MS = 15_000L
}

/** The full-screen photo and video viewer. */
object MediaViewerScreen {
    fun assertShowingPhoto() = Ui.assertShown("The full-screen photo") { hasDescription("Photo") }

    fun assertGone() = Ui.assertGone("The full-screen viewer") { hasDescription("Photo") }
}

/** The location picker opened from the Add form. */
object LocationPickerScreen {
    private const val CONFIRM = "Confirm Location"
    private const val MAP = "Google Map"
    private const val MAX_TAPS = 3
    private const val MARKER_TIMEOUT_MS = 5_000L
    private const val TAP_HEIGHT_DIVISOR = 4

    fun assertShown() = Ui.assertShown("The location picker") { hasText(CONFIRM) }

    fun assertGone() = Ui.assertGone("The location picker") { hasText(CONFIRM) }

    /**
     * Taps the map a quarter of the way down, which picks that spot: its marker replaces the one shown before.
     * Not in the middle: a spot picked before (or a fragment's inherited place) sits there, and a tap on its marker
     * picks nothing. A busy emulator can deliver the end of a tap so late that the map takes it for a long press and
     * ignores it, so this taps again until a new marker shows (a few times).
     */
    fun tapMap() {
        val bounds = Ui.find { hasDescription(MAP) }.visibleBounds
        val before = markers()
        repeat(MAX_TAPS) {
            Ui.device.click(bounds.centerX(), bounds.top + bounds.height() / TAP_HEIGHT_DIVISOR)
            Ui.device.waitForIdle()
            if (waitUntil(MARKER_TIMEOUT_MS) { markers().any { it !in before } }) return
        }
    }

    /**
     * Waits until a spot is picked: its marker shows on the map. The marker is named after the place, or "Selected
     * Location" when the address lookup finds nothing (as on emulators without network to Google).
     */
    fun assertLocationPicked() =
        assertTrue("A picked location is not shown", waitUntil(Ui.TIMEOUT_MS) { markers().isNotEmpty() })

    /** The descriptions of the markers on the map (the picked spot's). The map describes one as "<title>. ". */
    private fun markers(): Set<String> =
        Ui.device.findObjects(By.desc(MARKER_DESCRIPTION)).mapNotNull { it.contentDescription }.toSet()

    private val MARKER_DESCRIPTION = Pattern.compile("(?s).+\\.\\s+")

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) return false
            SystemClock.sleep(POLL_MS)
        }
        return true
    }

    private const val POLL_MS = 200L

    fun confirm() {
        Ui.find { hasText(CONFIRM) }.click()
        Ui.device.waitForIdle()
    }

    /** Taps the "my location" button, which picks where the phone is (asking for access first). */
    fun tapMyLocation() {
        Ui.find { hasDescription(MY_LOCATION) }.click()
        Ui.device.waitForIdle()
    }

    /** No spot is picked: no marker shows. It waits a moment, in case one is on its way. */
    fun assertNothingPicked() =
        assertTrue("A location was picked", !waitUntil(NOTHING_PICKED_WAIT_MS) { markers().isNotEmpty() })

    private const val MY_LOCATION = "My location"
    private const val NOTHING_PICKED_WAIT_MS = 3_000L
}

/**
 * A short message Android shows near the bottom of the screen. Android draws it outside the app's window, where the
 * element tree doesn't reach, so this listens for the announcement Android makes for screen readers instead.
 */
object ToastMessage {
    /** Runs [action] and waits until it shows the message [text]. */
    fun assertShownAfter(text: String, action: () -> Unit) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeAndWaitForEvent(
                { action() },
                { event ->
                    event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                        event.text.any { it.toString() == text }
                },
                TIMEOUT_MS
            )
        } catch (_: TimeoutException) {
            fail("The message \"$text\" was not shown")
        }
    }

    private const val TIMEOUT_MS = 10_000L
}
