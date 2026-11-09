package com.szabolcshorvath.memorymap.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.Keyboard
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

/**
 * Opening a memory, the media viewer and the location picker, and getting back from them. (Show on Timeline from a
 * timeline that was opened before is in `TimelineTest`, Edit from a form that wasn't in `AddMemoryTest`.)
 */
@RunWith(AndroidJUnit4::class)
class DetailNavigationTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Before
    fun addMemories() {
        val withPhoto = TestData.memory(NEWER, daysAgo = 1)
        TestData.photo(withPhoto)
        TestData.memory(OLDER, daysAgo = 10, place = TestData.Place(48.2082, 16.3738, "Vienna"))
    }

    @Test
    fun backFromAMemoryReturnsToTheTimelineAndATabOpenedFromAMemoryClosesIt() {
        openFromTimeline(OLDER)

        Ui.pressBack()

        TimelineScreen.assertShown()
        TimelineScreen.assertMemoryShown(OLDER)
        MemoryScreen.assertGone()
        BottomNav.assertSelected(Tab.TIMELINE)

        for (tab in Tab.entries) {
            openFromTimeline(OLDER, launch = false)

            BottomNav.select(tab)

            BottomNav.screenOf(tab).assertShown()
            MemoryScreen.assertGone()
            BottomNav.assertSelected(tab)
        }
    }

    @Test
    fun theKeyboardClosesAndTheSearchStaysWhenAMemoryIsOpenedOrTheSearchIsClosedForAnotherTab() {
        app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.typeInSearch("Vienna")
        Keyboard.assertShown()

        TimelineScreen.openMemory(OLDER)

        MemoryScreen.assertShown()
        Keyboard.assertHidden()

        Ui.pressBack()

        TimelineScreen.assertSearch("Vienna")
        TimelineScreen.assertMemoryShown(OLDER)
        TimelineScreen.assertMemoryGone(NEWER)

        TimelineScreen.typeInSearch("Vienna trip")
        Keyboard.assertShown()

        // The search view covers the tabs: it is closed first.
        TimelineScreen.closeSearch()
        Keyboard.assertHidden()
        BottomNav.select(Tab.MAP)

        MapScreen.assertShown()

        BottomNav.select(Tab.TIMELINE)

        TimelineScreen.assertSearch("Vienna trip")
        TimelineScreen.assertMemoryShown(OLDER)
    }

    @Test
    fun backFromTheViewerReturnsToTheSameMemoryAndATabOpenedFromTheViewerClosesBoth() {
        openFromTimeline(NEWER)
        MemoryScreen.openFirstPhoto()
        MediaViewerScreen.assertShowingPhoto()
        MemoryScreen.assertGone()

        Ui.pressBack()

        MemoryScreen.assertShowing(NEWER)
        MediaViewerScreen.assertGone()

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
    fun theLocationPickerFillsTheFormOnlyWhenConfirmedAndShowOnTimelineWorksBeforeTheTimelineWasOpened() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle(PICNIC)

        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()
        Ui.pressBack()

        AddScreen.assertShown()
        LocationPickerScreen.assertGone()
        AddScreen.assertTitle(PICNIC)
        AddScreen.assertNoLocation()

        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()

        AddScreen.assertShown()
        LocationPickerScreen.assertGone()
        AddScreen.assertTitle(PICNIC)
        AddScreen.assertHasLocation()

        // Saving a memory opens it, so the timeline was never opened before "Show on Timeline".
        AddScreen.save()
        MemoryScreen.assertShowing(PICNIC)
        MemoryScreen.showOnTimeline()

        TimelineScreen.assertShown()
        BottomNav.assertSelected(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(PICNIC)
    }

    @Test
    fun editAfterTheFormWasOpenedBefore() {
        app.launch()
        BottomNav.open(Tab.ADD)
        openFromTimeline(NEWER, launch = false)

        MemoryScreen.edit()

        AddScreen.assertShown()
        BottomNav.assertSelected(Tab.ADD)
        AddScreen.assertTitle(NEWER)
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
        Flows.openMemory(title)
    }

    private companion object {
        const val NEWER = "Danube walk"
        const val OLDER = "Vienna trip"
        const val PICNIC = "Picnic"
    }
}
