package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.textAsString
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.time.LocalDate
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

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
    fun assertPhotoCount(count: Int) = assertThumbnailCount(OPEN_PHOTO, count)

    /** Waits until the memory shows [count] video thumbnails (marked with a play icon). */
    fun assertVideoCount(count: Int) = assertThumbnailCount(OPEN_VIDEO, count)

    fun openFirstVideo() {
        Ui.scrollTo { hasDescription(OPEN_VIDEO) }
        tap { hasDescription(OPEN_VIDEO) }
    }

    private fun assertThumbnailCount(description: String, count: Int) {
        if (count > 0) Ui.scrollTo { hasDescription(description) }
        val end = SystemClock.uptimeMillis() + Ui.TIMEOUT_MS
        var found = Ui.device.findObjects(By.desc(description)).size
        while (found != count && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(POLL_MS)
            found = Ui.device.findObjects(By.desc(description)).size
        }
        assertEquals("Thumbnails described \"$description\"", count, found)
    }

    /** Opens the "Memory Fragments" section if it is closed, and waits until it lists [count] fragments. */
    fun assertFragmentCount(count: Int) {
        openFragments()
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
     * The (only) fragment has dates of its own, starting on [date] (its dates read like "Sep 10, 2026 9:00 AM - …").
     * A fragment without dates of its own shows none.
     */
    fun assertFragmentStartsOn(date: LocalDate) {
        val text = fragmentCard().findObject(By.res(Ui.device.currentPackageName, "tv_time"))?.text.orEmpty()
        val day = date.format(DateTimeFormatterUtil.dateFormatter())
        assertTrue("The fragment's dates \"$text\" don't start on $day", text.startsWith(day))
    }

    /** The (only) fragment shows the marker colour [color], in the strip along its left edge. */
    fun assertFragmentColor(color: Int) {
        val strip = checkNotNull(fragmentCard().findObject(By.res(Ui.device.currentPackageName, "colorIndicator"))) {
            "The fragment has no colour strip"
        }
        val shown = ScreenColors.at(listOf(strip.visibleCenter)).single()
        assertTrue(
            "The fragment's colour ${ScreenColors.hex(shown)} is not ${ScreenColors.hex(color)}",
            ScreenColors.isClose(shown, color)
        )
    }

    /** The first fragment's card, on screen, with the fragments section opened. */
    private fun fragmentCard(): UiObject2 {
        openFragments()
        Ui.scrollTo { hasText("Show on Map") && isInFragmentCard() }
        return Ui.find { hasId("fragmentCard") }
    }

    private fun AccessibilityNodeInfo.isInFragmentCard(): Boolean {
        var node = parent
        while (node != null) {
            if (node.viewIdResourceName?.endsWith(":id/fragmentCard") == true) return true
            node = node.parent
        }
        return false
    }

    private fun openFragments() {
        Ui.scrollTo { hasText(FRAGMENTS) }
        if (!Ui.isShown(timeoutMs = 0) { hasId("fragmentCard") }) Ui.find { hasText(FRAGMENTS) }.click()
    }

    /**
     * The photos are, from left to right, of these colours (the tests' photos are a single colour each). The
     * thumbnails all read "Open photo", so the colours are read from a screenshot, at each thumbnail's centre.
     */
    fun assertPhotoColors(vararg colors: Int) {
        Ui.scrollTo { hasDescription(OPEN_PHOTO) }
        SystemClock.sleep(CROSSFADE_MS) // Thumbnails fade in.
        val shown = ScreenColors.at(thumbnails().map { it.visibleCenter })
        val matches = shown.size == colors.size && shown.zip(colors.toList()).all { (a, b) -> ScreenColors.isClose(a, b) }
        assertTrue(
            "The photos' colours ${shown.map(ScreenColors::hex)} are not ${colors.map(ScreenColors::hex)}",
            matches
        )
    }

    /** Opens photo [index] (0 is the first) in the full-screen viewer. */
    fun openPhoto(index: Int) {
        Ui.scrollTo { hasDescription(OPEN_PHOTO) }
        thumbnails()[index].click()
        Ui.device.waitForIdle()
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
    private const val OPEN_VIDEO = "Open video"
    private const val CROSSFADE_MS = 600L
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

    /** A video shows, ready to play: its first frame and the Play button. */
    fun assertShowingVideo() {
        Ui.assertShown("The full-screen video") { hasDescription("Video") }
        Ui.assertShown("The Play button") { hasDescription(PLAY) }
    }

    fun play() = whilePlaying { Ui.find { hasDescription(PLAY) }.click() }

    fun pause() {
        whilePlaying { Ui.find { hasDescription(PAUSE) }.click() }
        Ui.device.waitForIdle()
    }

    /** The video plays: the button offers Pause, and the position bar moves on. */
    fun assertPlaying() = whilePlaying {
        Ui.assertShown("The Pause button") { hasDescription(PAUSE) }
        val start = videoPosition()
        Ui.assertShown("The video moving on from ${start}ms") { hasId(SCRUBBER) && position() > start }
    }

    /**
     * Runs [block] without waiting for the screen to settle first, as UI Automator does before every tap and lookup.
     * While a video plays, its position bar moves all the time, so the screen settles only once the video ends.
     */
    private fun <T> whilePlaying(block: () -> T): T {
        val configurator = Configurator.getInstance()
        val settleTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 0
        try {
            return block()
        } finally {
            configurator.waitForIdleTimeout = settleTimeout
        }
    }

    /** The video is paused: the button offers Play again, and the position bar stays where it is. */
    fun assertPaused() {
        Ui.assertShown("The Play button") { hasDescription(PLAY) }
        val position = videoPosition()
        SystemClock.sleep(PAUSE_CHECK_MS)
        assertEquals("The video's position while paused", position, videoPosition())
    }

    /** Where the video is, in milliseconds, as its position bar reports it (found by id: nothing on screen names it). */
    private fun videoPosition(): Float {
        var position = 0f
        Ui.find { hasId(SCRUBBER).also { found -> if (found) position = position() } }
        return position
    }

    private fun AccessibilityNodeInfo.position(): Float = rangeInfo?.current ?: 0f

    private const val PLAY = "Play"
    private const val PAUSE = "Pause"
    private const val SCRUBBER = "videoScrubber"
    private const val PAUSE_CHECK_MS = 1_000L

    /**
     * The photo on screen has [color] at [across] of the screen's width (0 is the left edge, 1 the right), half-way
     * down. The tests' photos are wider than tall, so they fill the width of an upright screen. Waits for the photo
     * to load and fade in.
     */
    fun assertColorAt(color: Int, across: Float = CENTER) {
        val end = SystemClock.uptimeMillis() + Ui.TIMEOUT_MS
        var shown = colorAt(across)
        while (!ScreenColors.isClose(shown, color) && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(POLL_MS)
            shown = colorAt(across)
        }
        assertTrue(
            "The photo is ${ScreenColors.hex(shown)} at $across across, not ${ScreenColors.hex(color)}",
            ScreenColors.isClose(shown, color)
        )
    }

    fun swipeToNext() = swipe(Direction.LEFT)

    fun swipeToPrevious() = swipe(Direction.RIGHT)

    /**
     * Double-taps the photo at [across] of the screen's width, which zooms in around that spot. Two separate clicks
     * come too far apart to count as a double tap (each holds the finger down for a while), so this sends the four
     * touches itself, a short moment apart.
     */
    fun doubleTapAt(across: Float) {
        val spot = spot(across)
        tap(spot)
        SystemClock.sleep(TAP_MS)
        tap(spot)
        Ui.device.waitForIdle()
    }

    /** A quick tap: the finger down at [spot] and up again [TAP_MS] later. */
    private fun tap(spot: Point) {
        val down = SystemClock.uptimeMillis()
        inject(down, down, MotionEvent.ACTION_DOWN, spot)
        SystemClock.sleep(TAP_MS)
        inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, spot)
    }

    private fun inject(downTime: Long, eventTime: Long, action: Int, spot: Point) {
        val event = MotionEvent.obtain(downTime, eventTime, action, spot.x.toFloat(), spot.y.toFloat(), 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    /**
     * Swipes slowly across most of the screen, so the page turns by distance, not speed. A busy emulator can deliver
     * a swipe so late that the page doesn't turn, so this swipes again (a few times) until another photo shows.
     */
    private fun swipe(direction: Direction) {
        val before = colorAt(CENTER)
        repeat(MAX_SWIPES) {
            Ui.find { hasId("mediaViewPager") }.swipe(direction, SWIPE_PERCENT, SWIPE_SPEED)
            Ui.device.waitForIdle()
            val end = SystemClock.uptimeMillis() + SWIPE_TIMEOUT_MS
            while (SystemClock.uptimeMillis() < end) {
                if (!ScreenColors.isClose(colorAt(CENTER), before)) return
                SystemClock.sleep(POLL_MS)
            }
        }
    }

    private fun colorAt(across: Float): Int = ScreenColors.at(listOf(spot(across))).single()

    private fun spot(across: Float) = Point((Ui.device.displayWidth * across).toInt(), Ui.device.displayHeight / 2)

    private const val CENTER = 0.5f
    private const val TAP_MS = 50L
    private const val POLL_MS = 200L
    private const val SWIPE_PERCENT = 0.8f
    private const val SWIPE_SPEED = 2500 // Pixels per second.
    private const val MAX_SWIPES = 3
    private const val SWIPE_TIMEOUT_MS = 3_000L
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
