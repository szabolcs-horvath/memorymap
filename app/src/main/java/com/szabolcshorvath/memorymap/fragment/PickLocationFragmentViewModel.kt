package com.szabolcshorvath.memorymap.fragment

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A spot picked in the location picker, with its name and address once they are known. */
data class PickedSpot(val lat: Double, val lng: Double, val name: String?, val address: String?)

/** Where the map's camera should go. The screen moves it once, then clears it ([PickLocationFragmentViewModel.onCameraMoved]). */
data class CameraRequest(val lat: Double, val lng: Double, val zoom: Float?, val animate: Boolean)

data class PickLocationUiState(
    val picked: PickedSpot? = null,
    /** The picked spot's name and address, or a searched place, are being looked up: it can't be used yet. */
    val isLookingUp: Boolean = false,
    /** The map shows the blue "you are here" dot: location access is granted. */
    val showsMyLocation: Boolean = false,
    val cameraRequest: CameraRequest? = null,
    @param:StringRes val message: Int? = null
)

/** The picked spot is kept in the saved state, so it survives process death. */
class PickLocationFragmentViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val selectedLat: Double? get() = savedState[SELECTED_LAT]
    val selectedLng: Double? get() = savedState[SELECTED_LNG]
    val selectedPlaceName: String? get() = savedState[SELECTED_PLACE_NAME]
    val selectedAddress: String? get() = savedState[SELECTED_ADDRESS]

    /** The searched place being looked up; a pick made meanwhile clears it, and the late result is then ignored. */
    var activeAutocompletePlaceId: String? = null
        private set

    // A lookup that was running when the process died is not resumed, so this starts false.
    private var isLookingUp = false

    private val _uiState = MutableStateFlow(PickLocationUiState())
    val uiState: StateFlow<PickLocationUiState> = _uiState.asStateFlow()

    init {
        publish()
    }

    /** Whether [lat], [lng] is still the picked spot (a late lookup result for an older spot is ignored). */
    fun isPicked(lat: Double, lng: Double) = lat == selectedLat && lng == selectedLng

    /** Picks [lat], [lng]. Its name and address are looked up next ([lookingUp]), unless they are given. */
    fun pick(lat: Double, lng: Double, name: String? = null, address: String? = null, lookingUp: Boolean = true) {
        activeAutocompletePlaceId = null
        savedState[SELECTED_LAT] = lat
        savedState[SELECTED_LNG] = lng
        savedState[SELECTED_PLACE_NAME] = name
        savedState[SELECTED_ADDRESS] = address
        isLookingUp = lookingUp
        publish()
    }

    /** The looked-up name and address of the picked spot (null where none was found). */
    fun showDetails(name: String?, address: String?) {
        savedState[SELECTED_PLACE_NAME] = name
        savedState[SELECTED_ADDRESS] = address
        isLookingUp = false
        publish()
    }

    /** Searched place [placeId] is being looked up: the spot picked before is dropped. */
    fun startSearchLookup(placeId: String) {
        clear()
        activeAutocompletePlaceId = placeId
        isLookingUp = true
        publish()
    }

    /** The searched place's lookup failed: nothing is picked. */
    fun stopSearchLookup() {
        activeAutocompletePlaceId = null
        isLookingUp = false
        publish()
    }

    fun clear() {
        activeAutocompletePlaceId = null
        savedState[SELECTED_LAT] = null
        savedState[SELECTED_LNG] = null
        savedState[SELECTED_PLACE_NAME] = null
        savedState[SELECTED_ADDRESS] = null
        isLookingUp = false
        publish()
    }

    /** Asks the screen to move the map to [lat], [lng], at [zoom] (null: the zoom it has), gliding there if [animate]. */
    fun moveCamera(lat: Double, lng: Double, zoom: Float?, animate: Boolean = true) {
        _uiState.value = _uiState.value.copy(cameraRequest = CameraRequest(lat, lng, zoom, animate))
    }

    fun onCameraMoved() {
        _uiState.value = _uiState.value.copy(cameraRequest = null)
    }

    fun setShowsMyLocation(shows: Boolean) {
        _uiState.value = _uiState.value.copy(showsMyLocation = shows)
    }

    fun showMessage(@StringRes text: Int) {
        _uiState.value = _uiState.value.copy(message = text)
    }

    fun onMessageShown() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun publish() {
        val lat = selectedLat
        val lng = selectedLng
        val picked = if (lat != null && lng != null) PickedSpot(lat, lng, selectedPlaceName, selectedAddress) else null
        _uiState.value = _uiState.value.copy(picked = picked, isLookingUp = isLookingUp)
    }

    private companion object {
        const val SELECTED_LAT = "selected_lat"
        const val SELECTED_LNG = "selected_lng"
        const val SELECTED_PLACE_NAME = "selected_place_name"
        const val SELECTED_ADDRESS = "selected_address"
    }
}
