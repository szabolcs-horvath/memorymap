package com.szabolcshorvath.memorymap.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The location picker's own behaviour. (Getting to it and back is in `DetailNavigationTest`, the "my location"
 * button in `MyLocationTest`.)
 */
@RunWith(AndroidJUnit4::class)
class LocationPickerTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun offlineAPickedSpotIsKeptAsCoordinates() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()
        // The map has loaded what it shows; without a network, the address of a spot can't be looked up.
        app.goOffline()

        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()

        AddScreen.assertLocationIsCoordinates()
    }
}
