package com.szabolcshorvath.memorymap.edge

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.ScreenColors
import com.szabolcshorvath.memorymap.screens.SettingsScreen
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Place
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Edge cases across the app: very long texts, a large database and dark mode. */
@RunWith(AndroidJUnit4::class)
class EdgeCaseTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun veryLongTextsShowInFullAndSurviveAnEdit() {
        val title = "A walk along the river ".repeat(LONG_TITLE_REPEATS).trim()
        val description = "We walked and talked until the sun went down. ".repeat(LONG_DESCRIPTION_REPEATS).trim()
        TestData.memory(title, description = description)
        app.launch()
        Flows.openMemory(title)
        MemoryScreen.assertDescription(description)

        MemoryScreen.edit()
        AddScreen.assertTitle(title)
        AddScreen.save()
        MemoryScreen.assertShowing(title)
        MemoryScreen.assertDescription(description)
    }

    @Test
    fun aLargeDatabaseStillOpensAndSearches() {
        // Memories spread over a grid across Europe, one a day going back: at the map's first zoom they cluster.
        repeat(LARGE) { index ->
            val place = Place(
                latitude = SOUTH_EDGE + (index / GRID_COLUMNS) * GRID_STEP_DEGREES,
                longitude = WEST_EDGE + (index % GRID_COLUMNS) * GRID_STEP_DEGREES,
                name = "Place ${index + 1}"
            )
            TestData.memory("Memory ${index + 1}", daysAgo = index.toLong(), place = place)
        }
        app.launch()
        MapScreen.assertClusterShown()

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown("Memory 1")
        TimelineScreen.search(SEARCHED)
        TimelineScreen.openMemory(SEARCHED)

        MemoryScreen.assertShowing(SEARCHED)
    }

    @Test
    fun darkModeDarkensTheScreens() {
        TestData.memory(TRIP)
        app.useDarkMode()
        app.launch()
        MapScreen.assertShown()

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(TRIP)
        ScreenColors.assertDark("The timeline")
        TimelineScreen.openMemory(TRIP)
        MemoryScreen.assertShowing(TRIP)
        ScreenColors.assertDark("The memory")
        BottomNav.open(Tab.ADD)
        AddScreen.assertShown()
        ScreenColors.assertDark("The Add form")
        BottomNav.open(Tab.SETTINGS)
        SettingsScreen.assertShown()
        ScreenColors.assertDark("Settings")
    }

    private companion object {
        const val TRIP = "Lake trip"
        const val LONG_TITLE_REPEATS = 14 // About 320 characters.
        const val LONG_DESCRIPTION_REPEATS = 45 // About 2,100 characters.
        const val LARGE = 500
        const val SEARCHED = "Memory 437"
        const val GRID_COLUMNS = 25
        const val GRID_STEP_DEGREES = 0.8
        const val SOUTH_EDGE = 40.0
        const val WEST_EDGE = -5.0
    }
}
