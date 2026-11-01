package com.szabolcshorvath.memorymap.fragment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** The picked spot is kept in the saved state, so it survives process death. */
class PickLocationFragmentViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var selectedLat: Double?
        get() = savedState[SELECTED_LAT]
        set(value) {
            savedState[SELECTED_LAT] = value
        }
    var selectedLng: Double?
        get() = savedState[SELECTED_LNG]
        set(value) {
            savedState[SELECTED_LNG] = value
        }
    var selectedPlaceName: String?
        get() = savedState[SELECTED_PLACE_NAME]
        set(value) {
            savedState[SELECTED_PLACE_NAME] = value
        }
    var selectedAddress: String?
        get() = savedState[SELECTED_ADDRESS]
        set(value) {
            savedState[SELECTED_ADDRESS] = value
        }
    var activeAutocompletePlaceId: String? = null

    private companion object {
        const val SELECTED_LAT = "selected_lat"
        const val SELECTED_LNG = "selected_lng"
        const val SELECTED_PLACE_NAME = "selected_place_name"
        const val SELECTED_ADDRESS = "selected_address"
    }
}
