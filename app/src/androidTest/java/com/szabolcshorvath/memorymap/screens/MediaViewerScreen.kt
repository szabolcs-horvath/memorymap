package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** The full-screen photo and video viewer. */
object MediaViewerScreen {
    fun assertShowingPhoto() = Ui.assertShown("The full-screen photo") { hasDescription("Photo") }

    fun assertGone() = Ui.assertGone("The full-screen viewer") { hasDescription("Photo") }

    /** Leaves the viewer with its top bar's back arrow (the top bar shows when the viewer opens). */
    fun close() {
        Ui.find { hasDescription("Back") }.click()
        Ui.device.waitForIdle()
    }

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

    // While a video plays, its position bar moves all the time, so the screen settles only once the video ends.
    private fun <T> whilePlaying(block: () -> T): T = Ui.withoutSettling(block)

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
        val shown = Ui.eventually(read = { colorAt(across) }) { ScreenColors.isClose(it, color) }
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
            if (Ui.waitUntil(SWIPE_TIMEOUT_MS) { !ScreenColors.isClose(colorAt(CENTER), before) }) return
        }
    }

    private fun colorAt(across: Float): Int = ScreenColors.at(listOf(spot(across))).single()

    private fun spot(across: Float) = Point((Ui.device.displayWidth * across).toInt(), Ui.device.displayHeight / 2)

    private const val CENTER = 0.5f
    private const val TAP_MS = 50L
    private const val SWIPE_PERCENT = 0.8f
    private const val SWIPE_SPEED = 2500 // Pixels per second.
    private const val MAX_SWIPES = 3
    private const val SWIPE_TIMEOUT_MS = 3_000L
}
