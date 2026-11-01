package com.szabolcshorvath.memorymap.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Switching tabs and going back between them. */
@RunWith(AndroidJUnit4::class)
class TabNavigationTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun switchesFromEveryTabToEveryOtherTab() {
        app.launch()
        MapScreen.assertShown()
        BottomNav.open(Tab.MAP) // Tapping the tab that is already open changes nothing.

        // A route that makes each of the 12 switches between two tabs exactly once.
        val route = listOf(
            Tab.MAP, Tab.TIMELINE, Tab.MAP, Tab.ADD, Tab.MAP, Tab.SETTINGS, Tab.TIMELINE,
            Tab.ADD, Tab.TIMELINE, Tab.SETTINGS, Tab.ADD, Tab.SETTINGS, Tab.MAP,
        )
        for ((from, to) in route.zipWithNext()) {
            BottomNav.select(to)

            BottomNav.screenOf(to).assertShown()
            BottomNav.screenOf(from).assertGone()
            BottomNav.assertSelected(to)
        }
    }

    @Test
    fun opensOnTheMapWhichBackFromAnotherTabReturnsToAndBackThenLeaves() {
        app.launch()
        MapScreen.assertShown()
        BottomNav.assertSelected(Tab.MAP)

        for (tab in listOf(Tab.TIMELINE, Tab.ADD, Tab.SETTINGS)) {
            BottomNav.open(tab)

            Ui.pressBack()

            MapScreen.assertShown()
            BottomNav.screenOf(tab).assertGone()
            BottomNav.assertSelected(Tab.MAP)
        }

        Ui.pressBack()
        assertTrue("The app is still in front", Ui.isGone { packageName == APP_PACKAGE })
    }

    private companion object {
        const val APP_PACKAGE = "com.szabolcshorvath.memorymap"
    }
}
