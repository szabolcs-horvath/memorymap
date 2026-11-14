package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** The full-screen photo and video viewer. Found through [ComposeUi]; what a photo shows, through screenshots. */
object MediaViewerScreen {
    fun assertShowingPhoto() {
        ComposeUi.waitFor("The full-screen photo", hasContentDescription("Photo"))
    }

    fun assertGone() = ComposeUi.assertGone("The full-screen viewer", hasContentDescription("Photo"))

    /** Leaves the viewer with its top bar's back arrow (the top bar shows when the viewer opens). */
    fun close() {
        ComposeUi.waitFor("The back arrow", hasContentDescription("Back")).performClick()
    }

    /** A video shows, ready to play: its first frame and the Play button. */
    fun assertShowingVideo() {
        ComposeUi.waitFor("The full-screen video", hasContentDescription("Video"))
        ComposeUi.waitFor("The Play button", hasContentDescription(PLAY))
    }

    fun play() {
        ComposeUi.waitFor("The Play button", hasContentDescription(PLAY)).performClick()
    }

    fun pause() {
        ComposeUi.waitFor("The Pause button", hasContentDescription(PAUSE)).performClick()
    }

    /** The video plays: the button offers Pause, and the position bar moves on. */
    fun assertPlaying() {
        ComposeUi.waitFor("The Pause button", hasContentDescription(PAUSE))
        val start = videoPosition()
        val now = Ui.eventually(read = ::videoPosition) { it > start }
        assertTrue("The video did not move on from ${start}ms", now > start)
    }

    /** The video is paused: the button offers Play again, and the position bar stays where it is. */
    fun assertPaused() {
        ComposeUi.waitFor("The Play button", hasContentDescription(PLAY))
        val position = videoPosition()
        SystemClock.sleep(PAUSE_CHECK_MS)
        Ui.catchUp()
        assertEquals("The video's position while paused", position, videoPosition())
    }

    /** Where the video is, in milliseconds, as its position bar reports it (found by its tag: nothing names it). */
    // Inside the player's controls, which merge their parts: in the tree before merging.
    private fun videoPosition(): Float = ComposeUi.waitFor("The video's position bar", hasTestTag(SCRUBBER), unmerged = true)
        .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

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

    fun swipeToNext() = swipe(left = true)

    fun swipeToPrevious() = swipe(left = false)

    /** Double-taps the photo at [across] of the screen's width, half-way down, which zooms in around that spot. */
    fun doubleTapAt(across: Float) {
        pager().performTouchInput { doubleClick(percentOffset(across, CENTER)) }
    }

    /** Swipes across the pager, which turns to the next photo once the swipe ends. */
    private fun swipe(left: Boolean) {
        pager().performTouchInput { if (left) swipeLeft() else swipeRight() }
    }

    private fun pager() = ComposeUi.waitFor("The viewer", hasTestTag("mediaViewPager"))

    private fun colorAt(across: Float): Int = ScreenColors.at(listOf(spot(across))).single()

    private fun spot(across: Float) = Point((Ui.device.displayWidth * across).toInt(), Ui.device.displayHeight / 2)

    private const val CENTER = 0.5f
}
