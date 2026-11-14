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

    /**
     * With access, the map opens on the area around the phone; "my location" there zooms in on the streets, but never
     * out from closer than the area; and tapping the Map tab again goes back to the area, not to all the memories.
     */
    @Test
    fun withAccessTheMapOpensWhereThePhoneIsAndMyLocationOrTheMapTabGoBackThere() {
        TestData.phoneAt(Place.BUDAPEST)
        TestData.grantLocationAccess()
        TestData.markerClustering(false)
        TestData.memory(BUDAPEST_TRIP, place = Place.BUDAPEST)
        TestData.memory(OBUDA_TRIP, place = OBUDA)
        TestData.memory(ROME_TRIP, place = Place.ROME)
        app.launch()
        // The map opens on the area around the phone, as the first press of "my location" shows it.
        MapScreen.assertMarkerShown(BUDAPEST_TRIP)
        MapScreen.assertMarkerShown(OBUDA_TRIP)
        MapScreen.assertMarkerGone(ROME_TRIP)

        // From the area, "my location" zooms in on the streets around the phone.
        MapScreen.tapMyLocation()
        MapScreen.assertMarkerGone(OBUDA_TRIP)
        MapScreen.assertMarkerShown(BUDAPEST_TRIP)

        // The first tap on the Map tab closes the card, the second goes back to the area around the phone.
        MapScreen.tapMarker(BUDAPEST_TRIP)
        MapScreen.assertOverlayShows(BUDAPEST_TRIP)
        BottomNav.tapAgain(Tab.MAP)
        MapScreen.assertOverlayGone()
        BottomNav.tapAgain(Tab.MAP)
        MapScreen.assertMarkerShown(BUDAPEST_TRIP)
        MapScreen.assertMarkerShown(OBUDA_TRIP)
        MapScreen.assertMarkerStaysGone(ROME_TRIP)

        // Tapping a pin zooms in on it, closer than the area: Budapest's centre is off screen. "My location" then goes
        // to the streets around the phone, not out to the area.
        MapScreen.tapMarker(OBUDA_TRIP)
        MapScreen.assertMarkerGone(BUDAPEST_TRIP)
        MapScreen.tapMyLocation()
        MapScreen.assertMarkerShown(BUDAPEST_TRIP)
        MapScreen.assertMarkerGone(OBUDA_TRIP)
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
    fun withAccessButLocationOffTheMapOpensOnAllTheMemoriesAndMyLocationSaysSo() {
        // No TestData.phoneAt: the phone doesn't know where it is. On the map's view of the whole world, before it
        // moves, these four memories are under the date filter or in one cluster.
        TestData.grantLocationAccess()
        val memories = mapOf(
            BUDAPEST_TRIP to Place.BUDAPEST,
            ROME_TRIP to Place.ROME,
            "Vienna trip" to Place.VIENNA,
            "Paris museum" to Place.PARIS,
        )
        memories.forEach { (title, place) -> TestData.memory(title, place = place) }

        app.launch()

        MapScreen.assertMarkersInView(memories.keys)

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
        const val OBUDA_TRIP = "Roman ruins"

        /** About 4 km north of Budapest's centre: on screen around the phone, off it on the streets. */
        val OBUDA = Place(47.5339, 19.0402, "Óbuda")
        const val LOCATION_UNAVAILABLE = "Your location isn't available right now. Check that location is turned on."
    }
}
