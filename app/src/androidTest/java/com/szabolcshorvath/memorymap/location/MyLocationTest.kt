package com.szabolcshorvath.memorymap.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.LocationAccess
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MapScreen
import com.szabolcshorvath.memorymap.screens.ToastMessage
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Place
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The user's location on the map and in the location picker. The app asks for location access only when a "my
 * location" button is tapped, never when a screen opens; with access, it uses the location on its own too. The phone
 * says it is in Budapest (`TestData.phoneAt`), and every test starts without location access.
 *
 * The map's tests have a memory in Budapest and one in Rome: both show on the map's first view of Europe, and only
 * Budapest once the map moves to where the phone is.
 */
@RunWith(AndroidJUnit4::class)
class MyLocationTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun theMapAsksForLocationOnlyWhenMyLocationIsTappedAndAllowingItMovesTheMap() {
        TestData.phoneAt(Place.BUDAPEST)
        openMapWithMemories()
        LocationAccess.assertNotAsked()

        MapScreen.tapMyLocation()
        LocationAccess.assertExplained()
        LocationAccess.notNow()

        LocationAccess.assertNotAsked()
        MapScreen.assertMarkerShown(ROME_TRIP)

        MapScreen.tapMyLocation()
        LocationAccess.assertExplained()
        LocationAccess.continueToAndroid()
        LocationAccess.allow()

        MapScreen.assertMarkerGone(ROME_TRIP)
        MapScreen.assertMarkerShown(BUDAPEST_TRIP)
    }

    @Test
    fun withAccessTheMapOpensWhereThePhoneIs() {
        TestData.phoneAt(Place.BUDAPEST)
        TestData.grantLocationAccess()
        addMemories()

        app.launch()

        MapScreen.assertMarkerShown(BUDAPEST_TRIP)
        MapScreen.assertMarkerGone(ROME_TRIP)
    }

    @Test
    fun afterTwoRefusalsMyLocationOffersTheAppSettings() {
        openMapWithMemories()
        repeat(2) {
            MapScreen.tapMyLocation()
            LocationAccess.assertExplained()
            LocationAccess.continueToAndroid()
            LocationAccess.refuse()
        }

        // Android no longer asks, so the app points to its settings instead.
        MapScreen.tapMyLocation()
        LocationAccess.assertSettingsOffered()
        LocationAccess.openSettings()

        LocationAccess.assertAppSettingsShown()
    }

    @Test
    fun withLocationTurnedOffMyLocationSaysSo() {
        // No TestData.phoneAt: the phone doesn't know where it is.
        TestData.grantLocationAccess()
        openMapWithMemories()

        ToastMessage.assertShownAfter(LOCATION_UNAVAILABLE) { MapScreen.tapMyLocation() }
        MapScreen.assertMarkerShown(ROME_TRIP)
    }

    @Test
    fun withAccessThePickerStartsWhereThePhoneIs() {
        TestData.phoneAt(Place.BUDAPEST)
        TestData.grantLocationAccess()
        app.launch()
        BottomNav.open(Tab.ADD)

        AddScreen.selectLocation()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()

        AddScreen.assertLocationIs(Place.BUDAPEST)
    }

    @Test
    fun withoutAccessThePickerPicksNothingUntilItsMyLocationButtonAsks() {
        TestData.phoneAt(Place.BUDAPEST)
        app.launch()
        BottomNav.open(Tab.ADD)

        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()
        LocationAccess.assertNotAsked()
        LocationPickerScreen.assertNothingPicked()

        LocationPickerScreen.tapMyLocation()
        LocationAccess.assertExplained()
        LocationAccess.continueToAndroid()
        LocationAccess.allow()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()

        AddScreen.assertLocationIs(Place.BUDAPEST)
    }

    private fun addMemories() {
        TestData.markerClustering(false)
        TestData.memory(BUDAPEST_TRIP, place = Place.BUDAPEST)
        TestData.memory(ROME_TRIP, place = Place.ROME)
    }

    private fun openMapWithMemories() {
        addMemories()
        app.launch()
        MapScreen.assertMarkerShown(BUDAPEST_TRIP)
        MapScreen.assertMarkerShown(ROME_TRIP)
    }

    private companion object {
        const val BUDAPEST_TRIP = "Danube walk"
        const val ROME_TRIP = "Colosseum"
        const val LOCATION_UNAVAILABLE = "Your location isn't available right now. Check that location is turned on."
    }
}
