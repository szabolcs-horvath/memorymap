package com.szabolcshorvath.memorymap.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.MediaViewerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opening a memory, the media viewer and the location picker, and getting back from them. */
@RunWith(AndroidJUnit4::class)
class DetailNavigationTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Before
    fun addMemories() {
        val withPhoto = TestData.memory(NEWER, daysAgo = 1)
        TestData.photo(withPhoto)
        TestData.memory(OLDER, daysAgo = 10, latitude = 48.2082, longitude = 16.3738, placeName = "Vienna")
    }

    @Test
    fun backFromAMemoryReturnsToTheTimeline() {
        openFromTimeline(OLDER)

        Ui.pressBack()

        TimelineScreen.assertShown()
        TimelineScreen.assertMemoryShown(OLDER)
        MemoryScreen.assertGone()
        BottomNav.assertSelected(Tab.TIMELINE)
    }

    @Test
    fun backFromTheViewerReturnsToTheSameMemory() {
        openFromTimeline(NEWER)
        MemoryScreen.openFirstPhoto()
        MediaViewerScreen.assertShowingPhoto()
        MemoryScreen.assertGone()

        Ui.pressBack()

        MemoryScreen.assertShowing(NEWER)
        MediaViewerScreen.assertGone()
    }

    @Test
    fun aTabOpenedFromAMemoryClosesIt() {
        app.launch()
        for (tab in Tab.entries) {
            openFromTimeline(OLDER, launch = false)

            BottomNav.select(tab)

            BottomNav.screenOf(tab).assertShown()
            MemoryScreen.assertGone()
            BottomNav.assertSelected(tab)
        }
    }

    @Test
    fun aTabOpenedFromTheViewerClosesIt() {
        openFromTimeline(NEWER)
        MemoryScreen.openFirstPhoto()
        MediaViewerScreen.assertShowingPhoto()

        BottomNav.select(Tab.MAP)

        MapScreen.assertShown()
        MediaViewerScreen.assertGone()
        MemoryScreen.assertGone()

        // Nothing is left behind to go back to.
        Ui.pressBack()
        MemoryScreen.assertGone()
    }

    @Test
    fun showOnMapOpensTheMapAtTheMemory() {
        openFromTimeline(OLDER)

        MemoryScreen.showOnMap()

        MapScreen.assertShown()
        BottomNav.assertSelected(Tab.MAP)
        MapScreen.assertOverlayShows(OLDER)
    }

    @Test
    fun showOnTimelineOpensTheTimelineAtTheMemory() {
        openFromTimeline(OLDER)

        MemoryScreen.showOnTimeline()

        TimelineScreen.assertShown()
        BottomNav.assertSelected(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(OLDER)
    }

    @Test
    fun showOnTimelineWhenTheTimelineWasNotOpenedYet() {
        // Saving a memory opens it, so the timeline is never opened before "Show on Timeline".
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle("Picnic")
        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()
        AddScreen.save()
        MemoryScreen.assertShowing("Picnic")

        MemoryScreen.showOnTimeline()

        TimelineScreen.assertShown()
        BottomNav.assertSelected(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown("Picnic")
    }

    @Test
    fun editOpensTheFormWithTheMemory() {
        openFromTimeline(OLDER)

        MemoryScreen.edit()

        AddScreen.assertShown()
        BottomNav.assertSelected(Tab.ADD)
        AddScreen.assertTitle(OLDER)
    }

    @Test
    fun editAfterTheFormWasOpenedBefore() {
        app.launch()
        BottomNav.open(Tab.ADD)
        openFromTimeline(NEWER, launch = false)

        MemoryScreen.edit()

        AddScreen.assertShown()
        AddScreen.assertTitle(NEWER)
    }

    @Test
    fun backFromTheLocationPickerKeepsTheForm() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle("Picnic")
        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()

        Ui.pressBack()

        AddScreen.assertShown()
        LocationPickerScreen.assertGone()
        AddScreen.assertTitle("Picnic")
        AddScreen.assertNoLocation()
    }

    @Test
    fun confirmingALocationFillsItIntoTheForm() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle("Picnic")
        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()

        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()

        AddScreen.assertShown()
        LocationPickerScreen.assertGone()
        AddScreen.assertTitle("Picnic")
        AddScreen.assertHasLocation()
    }

    @Test
    fun aTabOpenedFromTheLocationPickerClosesIt() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()

        BottomNav.select(Tab.TIMELINE)

        TimelineScreen.assertShown()
        LocationPickerScreen.assertGone()

        BottomNav.select(Tab.ADD)
        AddScreen.assertShown()
        LocationPickerScreen.assertGone()
    }

    private fun openFromTimeline(title: String, launch: Boolean = true) {
        if (launch) app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(title)
        MemoryScreen.assertShowing(title)
    }

    private companion object {
        const val NEWER = "Danube walk"
        const val OLDER = "Vienna trip"
    }
}
