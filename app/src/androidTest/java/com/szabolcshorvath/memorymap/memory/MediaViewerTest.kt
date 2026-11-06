package com.szabolcshorvath.memorymap.memory

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.MediaViewerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The full-screen viewer a memory's photo opens in. The tests' photos are plain colours, so which photo shows, and
 * how far it is zoomed, is read from the screen's colours.
 */
@RunWith(AndroidJUnit4::class)
class MediaViewerTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun theViewerOpensAtTheTappedPhotoAndSwipesThroughTheOthers() {
        val trip = TestData.memory(TRIP)
        TestData.photo(trip, RED, order = 0)
        TestData.photo(trip, GREEN, order = 1)
        TestData.photo(trip, BLUE, order = 2)
        openMemory()

        MemoryScreen.openPhoto(1)
        MediaViewerScreen.assertColorAt(GREEN)

        MediaViewerScreen.swipeToNext()
        MediaViewerScreen.assertColorAt(BLUE)
        MediaViewerScreen.swipeToPrevious()
        MediaViewerScreen.swipeToPrevious()
        MediaViewerScreen.assertColorAt(RED)
    }

    /** The viewer loads the memory's media itself and remembers which one is shown, not where it opened. */
    @Test
    fun theViewerStaysOnTheSwipedToPhotoThroughRotationAndProcessDeath() {
        val trip = TestData.memory(TRIP)
        TestData.photo(trip, RED, order = 0)
        TestData.photo(trip, GREEN, order = 1)
        TestData.photo(trip, BLUE, order = 2)
        openMemory()
        MemoryScreen.openPhoto(1)
        MediaViewerScreen.swipeToNext()
        MediaViewerScreen.assertColorAt(BLUE)

        Ui.rotateToLandscape()
        MediaViewerScreen.assertColorAt(BLUE)
        Ui.rotateToPortrait()
        MediaViewerScreen.assertColorAt(BLUE)

        app.simulateProcessDeath()
        MediaViewerScreen.assertColorAt(BLUE)
        MediaViewerScreen.swipeToPrevious()
        MediaViewerScreen.assertColorAt(GREEN)
    }

    @Test
    fun doubleTappingAPhotoZoomsIn() {
        // Red on the left half, blue on the right: zoomed in on the left, red reaches past the middle.
        TestData.photo(TestData.memory(TRIP), RED, rightHalf = BLUE)
        openMemory()
        MemoryScreen.openPhoto(0)
        MediaViewerScreen.assertColorAt(BLUE, across = RIGHT_OF_MIDDLE)

        MediaViewerScreen.doubleTapAt(LEFT_QUARTER)

        MediaViewerScreen.assertColorAt(RED, across = RIGHT_OF_MIDDLE)
    }

    private fun openMemory() {
        app.launch()
        Flows.openMemory(TRIP)
    }

    private companion object {
        const val TRIP = "Lake trip"
        val RED = Color.rgb(220, 40, 40)
        val GREEN = Color.rgb(40, 200, 60)
        val BLUE = Color.rgb(40, 60, 220)

        // Zooming in around a spot a quarter of the way across (at least 1.75 times) brings the red half past the
        // middle: the spot at 0.55 then shows the photo at about 0.25 + 0.3 / 1.75 = 0.42.
        const val LEFT_QUARTER = 0.25f
        const val RIGHT_OF_MIDDLE = 0.55f
    }
}
