package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.location.PickLocationActions
import com.szabolcshorvath.memorymap.ui.location.PickLocationContent
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.util.MyLocationAction
import com.szabolcshorvath.memorymap.util.PermissionUtil.hasLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class PickLocationFragment : Fragment(), PickLocationActions {

    private val viewModel: PickLocationFragmentViewModel by viewModels()
    private var pickLocationListener: PickLocationListener? = null

    // The only place the picker asks for location access: when the user taps the "my location" button.
    private val myLocationAction = MyLocationAction(this) { latLng ->
        refreshMyLocationDot()
        selectLocation(latLng)
    }

    private val autocompleteLauncher = registerForActivityResult(AppServices.placeSearch.screen) { chosen ->
        chosen ?: return@registerForActivityResult // The user left the search screen, or it failed.
        val requestedPlaceId = chosen.placeId
        val context = context ?: return@registerForActivityResult
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@registerForActivityResult

        // Drops the spot picked before, and any lookup still running for it.
        viewModel.startSearchLookup(requestedPlaceId)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val place = AppServices.placeSearch.details(context, requestedPlaceId, chosen.session)

                // Only if this is still the search being looked up: the user may have picked a spot meanwhile.
                if (viewModel.activeAutocompletePlaceId != requestedPlaceId) return@launch

                if (place != null) {
                    viewModel.pick(place.latitude, place.longitude, place.name, place.address, lookingUp = false)
                    viewModel.moveCamera(place.latitude, place.longitude, CAMERA_ZOOM)
                } else {
                    Log.e(TAG, "Fetched place has no location")
                    viewModel.stopSearchLookup()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching place details: ${e.message}", e)
                if (viewModel.activeAutocompletePlaceId == requestedPlaceId) viewModel.stopSearchLookup()
            }
        }
    }

    interface PickLocationListener {
        fun onLocationConfirmed(lat: Double, lng: Double, placeName: String?, address: String?)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is PickLocationListener) {
            pickLocationListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose saves the map's camera under this id.
            id = R.id.pick_location_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MemoryMapTheme { PickLocationContent(state, this@PickLocationFragment) }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshMyLocationDot()
        if (viewModel.selectedLat == null) selectUserLocation()
    }

    override fun close() {
        activity?.onBackPressedDispatcher?.onBackPressed()
    }

    override fun search() {
        if (!AppServices.placeSearch.isAvailable()) {
            // Places is not initialized without a Maps API key, and the search screen needs it.
            viewModel.showMessage(R.string.place_search_unavailable)
            return
        }
        autocompleteLauncher.launch(Unit)
    }

    override fun goToMyLocation() = myLocationAction.start()

    override fun pickOnMap(latLng: LatLng) {
        viewModel.pick(latLng.latitude, latLng.longitude)
        viewModel.moveCamera(latLng.latitude, latLng.longitude, zoom = null)
        reverseGeocode(latLng)
    }

    override fun pickPoi(poi: PointOfInterest) {
        val poiLatLng = poi.latLng
        viewModel.pick(poiLatLng.latitude, poiLatLng.longitude)
        viewModel.moveCamera(poiLatLng.latitude, poiLatLng.longitude, zoom = null)

        val context = context
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        if (context == null || !AppServices.placeSearch.isAvailable()) {
            // Without the Places SDK (no API key) the address lookup still works.
            reverseGeocode(poiLatLng)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val place = AppServices.placeSearch.details(context, poi.placeId, session = null)
                // Only if the user hasn't picked another spot meanwhile.
                if (viewModel.isPicked(poiLatLng.latitude, poiLatLng.longitude)) {
                    viewModel.showDetails(place?.name, place?.address)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching place details for POI: ${e.message}", e)
                // Fall back to the address lookup.
                if (viewModel.isPicked(poiLatLng.latitude, poiLatLng.longitude)) reverseGeocode(poiLatLng)
            }
        }
    }

    override fun confirm() {
        val lat = viewModel.selectedLat ?: return
        val lng = viewModel.selectedLng ?: return
        pickLocationListener?.onLocationConfirmed(lat, lng, viewModel.selectedPlaceName, viewModel.selectedAddress)
    }

    override fun onCameraMoved(request: CameraRequest) {
        // A newer request that replaced this one, mid-move, stays for the screen to handle.
        if (viewModel.uiState.value.cameraRequest == request) viewModel.onCameraMoved()
    }

    override fun onMessageShown() = viewModel.onMessageShown()

    private fun reverseGeocode(latLng: LatLng) {
        val context = context ?: return
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        val showResult = { address: Address? ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) { showGeocodeResult(latLng, address) }
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
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
            } catch (e: Exception) {
                when (e) {
                    is IllegalArgumentException -> Log.e(TAG, "Reverse geocoding failed for $latLng: ${e.message}", e)
                    else -> Log.e(TAG, "Reverse geocoding failed for $latLng", e)
                }
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
        if (!viewModel.isPicked(latLng.latitude, latLng.longitude)) return
        // A point of interest whose details failed keeps the name it may already have.
        val name = viewModel.selectedPlaceName ?: address?.let { placeName(requireContext(), it) }
        viewModel.showDetails(name, address?.getAddressLine(0) ?: viewModel.selectedAddress)
    }

    private fun refreshMyLocationDot() {
        val context = context ?: return
        viewModel.setShowsMyLocation(hasLocationPermission(context))
    }

    /** Selects where the user is, if location access was granted. Never asks for it (see [myLocationAction]). */
    private fun selectUserLocation() {
        val context = context
        if (context != null && hasLocationPermission(context)) {
            AppServices.location.lastLocation(context).addOnSuccessListener { location ->
                if (viewLifecycleOwnerLiveData.value == null) return@addOnSuccessListener
                // Not over a spot the user picked meanwhile.
                if (location != null && viewModel.selectedLat == null) selectLocation(LatLng(location.latitude, location.longitude))
            }
        }
    }

    private fun selectLocation(latLng: LatLng) {
        viewModel.pick(latLng.latitude, latLng.longitude)
        viewModel.moveCamera(latLng.latitude, latLng.longitude, CAMERA_ZOOM)
        reverseGeocode(latLng)
    }

    fun setInitialLocation(lat: Double, lng: Double, placeName: String?, address: String?) {
        viewModel.pick(lat, lng, placeName, address, lookingUp = false)
        viewModel.moveCamera(lat, lng, CAMERA_ZOOM, animate = false)
    }

    fun clearSelection() {
        viewModel.clear()
        if (view != null) selectUserLocation()
    }

    companion object {
        const val TAG = "PickLocationFragment"
        const val CAMERA_ZOOM = 15f

        // Android's address lookup names a spot without an address by its plus code ("G23R+C23"): not a name.
        private val PLUS_CODE = Regex("^[23456789CFGHJMPQRVWX]{2,8}\\+[23456789CFGHJMPQRVWX]*$", RegexOption.IGNORE_CASE)

        /**
         * What to call a spot picked on the map: the name the address lookup gives it, unless that is only its plus
         * code or its house number ("10"); then its street, with the house number if it has one.
         */
        private fun placeName(context: Context, address: Address): String? {
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
