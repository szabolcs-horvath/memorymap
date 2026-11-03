package com.szabolcshorvath.memorymap.memory

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakeMediaPicker
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.MissingPhotoSheet
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the app stores about each photo while its file can be read (a thumbnail among it), so the memory can still show
 * how a photo looked once its file is gone.
 */
@RunWith(AndroidJUnit4::class)
class MediaDetailsTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aPhotoSavedThroughTheFormStillShowsHowItLookedOnceItsFileIsGone() {
        FakeMediaPicker.nextPick = listOf(TestData.galleryPhoto(GREEN))
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle(PICNIC)
        Flows.pickLocationOnMap()
        AddScreen.addPhotos()
        AddScreen.save()
        MemoryScreen.assertShowing(PICNIC)
        MemoryScreen.assertPhotoLoaded()

        TestData.deleteCreatedPhotos()
        app.launch()
        Flows.openMemory(PICNIC)
        MemoryScreen.assertPhotoNotLoaded()
        MemoryScreen.openFirstPhoto()

        MissingPhotoSheet.assertThumbnail(GREEN)
    }

    @Test
    fun aPhotoSavedWithoutDetailsGetsThemAtTheNextStart() {
        // As saved by an older version: no thumbnail, no name, no perceptual hash.
        TestData.photo(TestData.memory(PICNIC), GREEN)
        app.launch()
        TestData.awaitStoredThumbnails(1)

        TestData.deleteCreatedPhotos()
        app.launch()
        Flows.openMemory(PICNIC)
        MemoryScreen.assertPhotoNotLoaded()
        MemoryScreen.openFirstPhoto()

        MissingPhotoSheet.assertThumbnail(GREEN)
    }

    private companion object {
        const val PICNIC = "Picnic"
        val GREEN = Color.rgb(40, 160, 90)
    }
}
