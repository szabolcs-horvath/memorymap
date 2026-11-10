package com.szabolcshorvath.memorymap.memory

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakeMediaPicker
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.MediaViewerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Videos in a memory: picking one on the Add form, its thumbnail, and playing it in the full-screen viewer. The
 * videos are five seconds of one colour, made on the phone (`TestData.galleryVideo`).
 */
@RunWith(AndroidJUnit4::class)
class VideoTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aPickedVideoIsSavedAsAVideoAndPlaysAndPausesInTheViewer() {
        TestData.memory(TRIP)
        FakeMediaPicker.nextPick = listOf(TestData.galleryVideo(GREEN))
        app.launch()
        Flows.openMemory(TRIP)
        MemoryScreen.edit()

        AddScreen.addPhotos()
        AddScreen.assertVideoSelected()
        AddScreen.save()

        MemoryScreen.assertShowing(TRIP)
        MemoryScreen.assertVideoCount(1)
        MemoryScreen.assertPhotoCount(0)

        MemoryScreen.openFirstVideo()
        MediaViewerScreen.assertShowingVideo()
        // Read left of the middle, where the player's buttons are.
        MediaViewerScreen.assertColorAt(GREEN, across = LEFT_OF_BUTTONS)

        MediaViewerScreen.play()
        MediaViewerScreen.assertPlaying()
        MediaViewerScreen.pause()
        MediaViewerScreen.assertPaused()
    }

    private companion object {
        const val TRIP = "Lake trip"
        val GREEN = Color.rgb(40, 200, 60)
        const val LEFT_OF_BUTTONS = 0.15f
    }
}
