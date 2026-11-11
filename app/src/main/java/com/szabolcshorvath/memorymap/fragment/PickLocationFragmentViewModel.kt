package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.util.ChosenPlace
import com.szabolcshorvath.memorymap.util.PermissionUtil.hasLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

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

/**
 * The location picker: the picked spot, kept in the saved state so it survives process death, and the lookups of its
 * name and address (Google's place details, or Android's address lookup).
 */
// One function per intent of the screen.
@Suppress("TooManyFunctions")
class PickLocationFragmentViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {

    val selectedLat: Double? get() = savedState[SELECTED_LAT]
    val selectedLng: Double? get() = savedState[SELECTED_LNG]
    val selectedPlaceName: String? get() = savedState[SELECTED_PLACE_NAME]
    val selectedAddress: String? get() = savedState[SELECTED_ADDRESS]

    /** The searched place being looked up; a pick made meanwhile clears it, and the late result is then ignored. */
    private var activeAutocompletePlaceId: String? = null

    // A lookup that was running when the process died is not resumed, so this starts false.
    private var isLookingUp = false

    private val _uiState = MutableStateFlow(PickLocationUiState())
    val uiState: StateFlow<PickLocationUiState> = _uiState.asStateFlow()

    private val context: Context get() = getApplication()

    init {
        publish()
    }

    /** The picker is shown: the "you are here" dot follows location access, and with nothing picked it picks the phone. */
    fun onShown() {
        setShowsMyLocation(hasLocationPermission(context))
        if (selectedLat == null) selectUserLocation()
    }

    /** Opens the picker on a spot picked before ([lat], [lng], with its name and address). */
    fun setInitialLocation(lat: Double, lng: Double, placeName: String?, address: String?) {
        pick(lat, lng, placeName, address, lookingUp = false)
        moveCamera(lat, lng, CAMERA_ZOOM, animate = false)
    }

    /** Opens the picker with nothing picked: it picks where the phone is once shown, if it may. */
    fun clearSelection() = clear()

    fun pickOnMap(latLng: LatLng) {
        pick(latLng.latitude, latLng.longitude)
        moveCamera(latLng.latitude, latLng.longitude, zoom = null)
        reverseGeocode(latLng)
    }

    fun pickPoi(poi: PointOfInterest) {
        val position = poi.latLng
        pick(position.latitude, position.longitude)
        moveCamera(position.latitude, position.longitude, zoom = null)
        if (!AppServices.placeSearch.isAvailable()) {
            // Without the Places SDK (no API key) the address lookup still works.
            reverseGeocode(position)
            return
        }
        viewModelScope.launch {
            try {
                val place = AppServices.placeSearch.details(context, poi.placeId, session = null)
                // Only if the user hasn't picked another spot meanwhile.
                if (isPicked(position.latitude, position.longitude)) showDetails(place?.name, place?.address)
            } catch (e: IOException) {
                Log.e(TAG, "Error fetching place details for POI: ${e.message}", e)
                // Fall back to the address lookup.
                if (isPicked(position.latitude, position.longitude)) reverseGeocode(position)
            }
        }
    }

    /** "My location" found the phone at [latLng]: picks it. */
    fun selectMyLocation(latLng: LatLng) {
        setShowsMyLocation(hasLocationPermission(context))
        selectLocation(latLng)
    }

    /** Whether searching works; without it, says so. */
    fun canSearch(): Boolean {
        // Places is not initialized without a Maps API key, and the search screen needs it.
        val available = AppServices.placeSearch.isAvailable()
        if (!available) showMessage(R.string.place_search_unavailable)
        return available
    }

    /** The user chose [chosen] on the search screen: looks it up and picks it. */
    fun lookUpSearched(chosen: ChosenPlace) {
        val requestedPlaceId = chosen.placeId
        // Drops the spot picked before, and any lookup still running for it.
        startSearchLookup(requestedPlaceId)
        viewModelScope.launch {
            try {
                val place = AppServices.placeSearch.details(context, requestedPlaceId, chosen.session)
                // Only if this is still the search being looked up: the user may have picked a spot meanwhile.
                if (activeAutocompletePlaceId != requestedPlaceId) return@launch
                if (place != null) {
                    pick(place.latitude, place.longitude, place.name, place.address, lookingUp = false)
                    moveCamera(place.latitude, place.longitude, CAMERA_ZOOM)
                } else {
                    Log.e(TAG, "Fetched place has no location")
                    stopSearchLookup()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error fetching place details: ${e.message}", e)
                if (activeAutocompletePlaceId == requestedPlaceId) stopSearchLookup()
            }
        }
    }

    /** The screen moved the camera for [request]; a newer request that replaced it meanwhile stays. */
    fun onCameraMoved(request: CameraRequest) {
        if (_uiState.value.cameraRequest == request) _uiState.value = _uiState.value.copy(cameraRequest = null)
    }

    fun onMessageShown() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Selects where the user is, if location access was granted. Never asks for it (see `MyLocationRequester`). */
    private fun selectUserLocation() {
        if (!hasLocationPermission(context)) return
        AppServices.location.lastLocation(context).addOnSuccessListener { location ->
            // Not over a spot the user picked meanwhile.
            if (location != null && selectedLat == null) selectLocation(LatLng(location.latitude, location.longitude))
        }
    }

    private fun selectLocation(latLng: LatLng) {
        pick(latLng.latitude, latLng.longitude)
        moveCamera(latLng.latitude, latLng.longitude, CAMERA_ZOOM)
        reverseGeocode(latLng)
    }

    private fun reverseGeocode(latLng: LatLng) {
        val showResult = { address: Address? -> viewModelScope.launch(Dispatchers.Main) { showGeocodeResult(latLng, address) } }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Geocoder(context, Locale.getDefault()).getFromLocation(
                    latLng.latitude,
                    latLng.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            showResult(addresses.firstOrNull())
                        }

                        // Offline or on a service error only this is called, never onGeocode.
                        override fun onError(errorMessage: String?) {
                            Log.e(TAG, "Reverse geocoding failed for $latLng: $errorMessage")
                            showResult(null)
                        }
                    }
                )
            } catch (e: IllegalArgumentException) {
                // Coordinates out of range. A failed lookup is reported to onError instead.
                Log.e(TAG, "Reverse geocoding failed for $latLng: ${e.message}", e)
                showResult(null)
            }
        }
    }

    /**
     * Shows the reverse-geocoded [address] of [latLng] (null if there is none or the lookup failed), which makes the
     * spot usable: its coordinates alone are enough to save a location.
     */
    private fun showGeocodeResult(latLng: LatLng, address: Address?) {
        // Ignore a late result for a location the user has since moved away from.
        if (!isPicked(latLng.latitude, latLng.longitude)) return
        // A point of interest whose details failed keeps the name it may already have.
        val name = selectedPlaceName ?: address?.let { placeName(context, it) }
        showDetails(name, address?.getAddressLine(0) ?: selectedAddress)
    }

    /** Whether [lat], [lng] is still the picked spot (a late lookup result for an older spot is ignored). */
    private fun isPicked(lat: Double, lng: Double) = lat == selectedLat && lng == selectedLng

    /** Picks [lat], [lng]. Its name and address are looked up next ([lookingUp]), unless they are given. */
    private fun pick(lat: Double, lng: Double, name: String? = null, address: String? = null, lookingUp: Boolean = true) {
        activeAutocompletePlaceId = null
        savedState[SELECTED_LAT] = lat
        savedState[SELECTED_LNG] = lng
        savedState[SELECTED_PLACE_NAME] = name
        savedState[SELECTED_ADDRESS] = address
        isLookingUp = lookingUp
        publish()
    }

    /** The looked-up name and address of the picked spot (null where none was found). */
    private fun showDetails(name: String?, address: String?) {
        savedState[SELECTED_PLACE_NAME] = name
        savedState[SELECTED_ADDRESS] = address
        isLookingUp = false
        publish()
    }

    /** Searched place [placeId] is being looked up: the spot picked before is dropped. */
    private fun startSearchLookup(placeId: String) {
        clear()
        activeAutocompletePlaceId = placeId
        isLookingUp = true
        publish()
    }

    /** The searched place's lookup failed: nothing is picked. */
    private fun stopSearchLookup() {
        activeAutocompletePlaceId = null
        isLookingUp = false
        publish()
    }

    private fun clear() {
        activeAutocompletePlaceId = null
        savedState[SELECTED_LAT] = null
        savedState[SELECTED_LNG] = null
        savedState[SELECTED_PLACE_NAME] = null
        savedState[SELECTED_ADDRESS] = null
        isLookingUp = false
        publish()
    }

    /** Asks the screen to move the map to [lat], [lng], at [zoom] (null: the zoom it has), gliding there if [animate]. */
    private fun moveCamera(lat: Double, lng: Double, zoom: Float?, animate: Boolean = true) {
        _uiState.value = _uiState.value.copy(cameraRequest = CameraRequest(lat, lng, zoom, animate))
    }

    private fun setShowsMyLocation(shows: Boolean) {
        _uiState.value = _uiState.value.copy(showsMyLocation = shows)
    }

    private fun showMessage(@StringRes text: Int) {
        _uiState.value = _uiState.value.copy(message = text)
    }

    private fun publish() {
        val lat = selectedLat
        val lng = selectedLng
        val picked = if (lat != null && lng != null) PickedSpot(lat, lng, selectedPlaceName, selectedAddress) else null
        _uiState.value = _uiState.value.copy(picked = picked, isLookingUp = isLookingUp)
    }

    private companion object {
        const val TAG = "PickLocationViewModel"
        const val CAMERA_ZOOM = 15f
        const val SELECTED_LAT = "selected_lat"
        const val SELECTED_LNG = "selected_lng"
        const val SELECTED_PLACE_NAME = "selected_place_name"
        const val SELECTED_ADDRESS = "selected_address"

        // Android's address lookup names a spot without an address by its plus code ("G23R+C23"): not a name.
        private val PLUS_CODE = Regex("^[23456789CFGHJMPQRVWX]{2,8}\\+[23456789CFGHJMPQRVWX]*$", RegexOption.IGNORE_CASE)

        /**
         * What to call a spot picked on the map: the name the address lookup gives it, unless that is only its plus
         * code or its house number ("10"); then its street, with the house number if it has one.
         */
        fun placeName(context: Context, address: Address): String? {
            val street = address.thoroughfare
            val number = address.subThoroughfare
            val feature = address.featureName?.takeUnless { PLUS_CODE.matches(it) || it == number || it == street }
            return when {
                feature != null -> feature
                street != null && number != null -> context.getString(R.string.street_and_number, street, number)
                else -> street
            }
        }
    }
}
