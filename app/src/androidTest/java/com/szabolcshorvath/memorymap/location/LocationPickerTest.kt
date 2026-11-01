package com.szabolcshorvath.memorymap.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.fakes.FakePlaceSearch
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.ToastMessage
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Place
import com.szabolcshorvath.memorymap.util.FoundPlace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The location picker's own behaviour: place search, leaving it without a new place, and a spot picked offline.
 * (Getting to it and back from a new memory is in `DetailNavigationTest`, the "my location" button in
 * `MyLocationTest`.) The place search is a fake (`FakePlaceSearch`): it returns the place a test chose, as if the user
 * had searched for it.
 */
@RunWith(AndroidJUnit4::class)
class LocationPickerTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aSearchedPlaceIsPickedWithItsNameAndAddress() {
        FakePlaceSearch.nextPick = FoundPlace(PALACE.name, PALACE.address, PALACE.latitude, PALACE.longitude)
        openPickerForANewMemory()

        LocationPickerScreen.search()
        LocationPickerScreen.assertPicked(PALACE.name!!)
        LocationPickerScreen.confirm()

        AddScreen.assertLocationIs(PALACE)
    }

    @Test
    fun leavingTheSearchOrGoingBackKeepsThePlaceTheMemoryHad() {
        TestData.memory(TRIP, place = Place.VIENNA)
        app.launch()
        Flows.openMemory(TRIP)
        MemoryScreen.edit()

        AddScreen.selectLocation()
        LocationPickerScreen.search() // FakePlaceSearch.nextPick is null: the user leaves the search.
        LocationPickerScreen.confirm()
        AddScreen.assertLocationIs(Place.VIENNA)

        // A spot tapped on the map, then Back instead of Confirm.
        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        Ui.pressBack()
        AddScreen.assertShown()
        AddScreen.assertLocationIs(Place.VIENNA)
    }

    @Test
    fun withoutAMapsKeySearchSaysItIsUnavailable() {
        FakePlaceSearch.searchWorks = false
        openPickerForANewMemory()

        ToastMessage.assertShownAfter(SEARCH_UNAVAILABLE) { LocationPickerScreen.search() }
        LocationPickerScreen.assertShown()
    }

    @Test
    fun offlineAPickedSpotIsKeptAsCoordinates() {
        openPickerForANewMemory()
        // The map has loaded what it shows; without a network, the address of a spot can't be looked up.
        app.goOffline()

        // A spot no other test taps (in the Sahara on the map's first view): Google Play services keeps the addresses it
        // looked up, even after the app's data is cleared, and would know the address of a spot tapped before.
        LocationPickerScreen.tapMap(across = UNTAPPED_ACROSS, down = UNTAPPED_DOWN)
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()

        AddScreen.assertLocationIsCoordinates()
    }

    private fun openPickerForANewMemory() {
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.selectLocation()
        LocationPickerScreen.assertShown()
    }

    private companion object {
        const val TRIP = "Vienna trip"
        const val UNTAPPED_ACROSS = 0.62f
        const val UNTAPPED_DOWN = 0.3f
        const val SEARCH_UNAVAILABLE = "Place search isn't available in this build. Tap the map to pick a location."
        val PALACE = Place(48.1845, 16.3122, "Schönbrunn Palace", "Schönbrunner Schloßstraße 47, 1130 Wien, Austria")
    }
}
