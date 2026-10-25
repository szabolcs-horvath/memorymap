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
    fun opensOnTheMap() {
        app.launch()

        MapScreen.assertShown()
        BottomNav.assertSelected(Tab.MAP)
    }

    @Test
    fun switchesFromEveryTabToEveryOtherTab() {
        app.launch()
        MapScreen.assertShown()

        for (from in Tab.entries) {
            for (to in Tab.entries) {
                if (from == to) continue
                BottomNav.open(from) // Tapping the tab that is already open changes nothing.

                BottomNav.select(to)

                BottomNav.screenOf(to).assertShown()
                BottomNav.screenOf(from).assertGone()
                BottomNav.assertSelected(to)
            }
        }
    }

    @Test
    fun backFromAnotherTabGoesToTheMap() {
        app.launch()

        for (tab in listOf(Tab.TIMELINE, Tab.ADD, Tab.SETTINGS)) {
            BottomNav.open(tab)

            Ui.pressBack()

            MapScreen.assertShown()
            BottomNav.screenOf(tab).assertGone()
            BottomNav.assertSelected(Tab.MAP)
        }
    }

    @Test
    fun backOnTheMapLeavesTheApp() {
        app.launch()
        MapScreen.assertShown()

        Ui.pressBack()

        assertTrue("The app is still in front", Ui.isGone { packageName == APP_PACKAGE })
    }

    private companion object {
        const val APP_PACKAGE = "com.szabolcshorvath.memorymap"
    }
}
