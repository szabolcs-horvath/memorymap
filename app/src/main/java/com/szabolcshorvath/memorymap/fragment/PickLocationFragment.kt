package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.kotlin.awaitFetchPlace
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.databinding.FragmentPickLocationBinding
import com.szabolcshorvath.memorymap.util.PermissionUtil.checkPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class PickLocationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentPickLocationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PickLocationFragmentViewModel by viewModels()
    private var mMap: GoogleMap? = null
    private var pickLocationListener: PickLocationListener? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (viewLifecycleOwnerLiveData.value == null) return@registerForActivityResult
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
            selectUserLocation()
        } else {
            viewModel.permissionDenied = true
        }
    }

    private val autocompleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val intent = result.data
            if (intent != null) {
                when (result.resultCode) {
                    PlaceAutocompleteActivity.RESULT_OK -> {
                        val prediction = PlaceAutocomplete.getPredictionFromIntent(intent)!!
                        val requestedPlaceId = prediction.placeId
                        val sessionTokenFromIntent = PlaceAutocomplete.getSessionTokenFromIntent(intent)
                        val context = context ?: return@registerForActivityResult
                        val placesClient = Places.createClient(context)
                        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@registerForActivityResult

                        // Invalidate any ongoing map/POI selection or other autocomplete requests
                        viewModel.activeAutocompletePlaceId = requestedPlaceId
                        viewModel.selectedLat = null
                        viewModel.selectedLng = null
                        viewModel.selectedPlaceName = null
                        viewModel.selectedAddress = null
                        mMap?.clear()

                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                setConfirmButtonLoading(true)
                                val response = placesClient.awaitFetchPlace(requestedPlaceId, placeFields) {
                                    sessionToken = sessionTokenFromIntent
                                }

                                // Race condition check: Only proceed if this is still the active request
                                if (viewModel.activeAutocompletePlaceId != requestedPlaceId) return@launch

                                val place = response.place
                                val latLng = place.location
                                if (latLng != null) {
                                    viewModel.selectedPlaceName = place.displayName
                                    viewModel.selectedAddress = place.formattedAddress
                                    updateSelectedLocation(latLng, viewModel.selectedPlaceName)
                                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM))
                                } else {
                                    Log.e(TAG, "Fetched place has no location")
                                    if (viewModel.activeAutocompletePlaceId == requestedPlaceId) {
                                        setConfirmButtonLoading(false)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching place details: ${e.message}", e)
                                if (viewModel.activeAutocompletePlaceId == requestedPlaceId) {
                                    setConfirmButtonLoading(false)
                                }
                            }
                        }
                    }

                    PlaceAutocompleteActivity.RESULT_ERROR -> {
                        val status = PlaceAutocomplete.getResultStatusFromIntent(intent)
                        Log.e(TAG, "Autocomplete error: ${status?.statusMessage}")
                    }
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPickLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btConfirm.setOnClickListener {
            if (viewModel.selectedLat != null && viewModel.selectedLng != null) {
                pickLocationListener?.onLocationConfirmed(
                    viewModel.selectedLat!!,
                    viewModel.selectedLng!!,
                    viewModel.selectedPlaceName,
                    viewModel.selectedAddress
                )
            }
        }

        binding.btSearch.setOnClickListener { startAutocomplete() }

        binding.root.doOnLayout { setGoogleMapPadding() }
    }

    override fun onResume() {
        super.onResume()
        requestLocationPermissionIfNeeded()
    }

    private fun startAutocomplete() {
        val context = context ?: return
        val sessionToken = AutocompleteSessionToken.newInstance()
        val intent = PlaceAutocomplete.createIntent(context) {
            setAutocompleteSessionToken(sessionToken)
        }
        autocompleteLauncher.launch(intent)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        mMap = googleMap

        googleMap.mapColorScheme = MapColorScheme.FOLLOW_SYSTEM
        googleMap.uiSettings.isRotateGesturesEnabled = false
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.uiSettings.isZoomControlsEnabled = true

        enableMyLocation()
        setGoogleMapPadding()

        googleMap.setOnMapClickListener { latLng ->
            viewModel.selectedPlaceName = null
            viewModel.selectedAddress = null
            updateSelectedLocation(latLng, isLoading = true)
            reverseGeocode(latLng)
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }

        googleMap.setOnPoiClickListener { poi ->
            val placeId = poi.placeId
            val poiLatLng = poi.latLng
            updateSelectedLocation(poiLatLng, isLoading = true)
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(poiLatLng))

            val context = context
            if (context != null) {
                val placesClient = Places.createClient(context)
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val response = placesClient.awaitFetchPlace(placeId, placeFields)
                        val place = response.place

                        // Race condition check: Only update if the user hasn't clicked elsewhere
                        if (poiLatLng.latitude == viewModel.selectedLat && poiLatLng.longitude == viewModel.selectedLng) {
                            viewModel.selectedPlaceName = place.displayName
                            viewModel.selectedAddress = place.formattedAddress
                            updateSelectedLocation(poiLatLng, viewModel.selectedPlaceName)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching place details for POI: ${e.message}", e)
                        // Fallback to reverse geocoding for address if fetch fails
                        if (poiLatLng.latitude == viewModel.selectedLat && poiLatLng.longitude == viewModel.selectedLng) {
                            reverseGeocode(poiLatLng)
                        }
                    }
                }
            }
        }

        googleMap.setOnMyLocationButtonClickListener {
            selectUserLocation()
            true
        }

        if (viewModel.selectedLat == null) {
            selectUserLocation()
        } else {
            // Restore marker after rotation
            val latLng = LatLng(viewModel.selectedLat!!, viewModel.selectedLng!!)
            updateSelectedLocation(latLng, viewModel.selectedPlaceName)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM))
        }
    }

    private fun reverseGeocode(latLng: LatLng) {
        // Keep a reference to the coordinates for this specific request
        val requestLatLng = latLng
        val context = context ?: return
        val viewLifecycleOwner = viewLifecycleOwner

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                        // RACE CONDITION CHECK:
                        // Only process the result if the currently selected location hasn't changed
                        // since this request was initiated.
                        if (requestLatLng.latitude != viewModel.selectedLat || requestLatLng.longitude != viewModel.selectedLng) {
                            return@getFromLocation
                        }

                        if (addresses.isNotEmpty()) {
                            val address = addresses.first()
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                // Re-verify on the Main thread before updating UI
                                if (requestLatLng.latitude != viewModel.selectedLat || requestLatLng.longitude != viewModel.selectedLng) {
                                    return@launch
                                }
                                if (viewModel.selectedPlaceName == null) {
                                    viewModel.selectedPlaceName = address.featureName ?: address.thoroughfare
                                }
                                viewModel.selectedAddress = address.getAddressLine(0)
                                updateSelectedLocation(latLng, viewModel.selectedPlaceName)
                            }
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                if (requestLatLng.latitude == viewModel.selectedLat && requestLatLng.longitude == viewModel.selectedLng) {
                                    setConfirmButtonLoading(false)
                                }
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

                    // RACE CONDITION CHECK:
                    // Only process the result if the currently selected location hasn't changed
                    // since this request was initiated.
                    if (requestLatLng.latitude != viewModel.selectedLat || requestLatLng.longitude != viewModel.selectedLng) {
                        return@launch
                    }

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses.first()
                        withContext(Dispatchers.Main) {
                            if (requestLatLng.latitude == viewModel.selectedLat && requestLatLng.longitude == viewModel.selectedLng) {
                                if (viewModel.selectedPlaceName == null) {
                                    viewModel.selectedPlaceName = address.featureName ?: address.thoroughfare
                                }
                                viewModel.selectedAddress = address.getAddressLine(0)
                                updateSelectedLocation(latLng, viewModel.selectedPlaceName)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (requestLatLng.latitude == viewModel.selectedLat && requestLatLng.longitude == viewModel.selectedLng) {
                                setConfirmButtonLoading(false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is IllegalArgumentException,
                    is IOException -> Log.e(TAG, "Reverse geocoding failed for $latLng: ${e.message}", e)

                    else -> Log.e(TAG, "Reverse geocoding failed for $latLng", e)
                }
                withContext(Dispatchers.Main) {
                    if (requestLatLng.latitude == viewModel.selectedLat && requestLatLng.longitude == viewModel.selectedLng) {
                        setConfirmButtonLoading(false)
                    }
                }
            }
        }
    }

    private fun setGoogleMapPadding() {
        val binding = _binding ?: return
        val map = mMap ?: return
        val topPadding = binding.searchContainer.height + binding.searchContainer.top
        val bottomPadding = binding.confirmContainer.height + (binding.root.height - binding.confirmContainer.bottom)
        map.setPadding(0, topPadding, 0, bottomPadding)
    }

    private fun updateSelectedLocation(latLng: LatLng, title: String? = null, isLoading: Boolean = false) {
        viewModel.activeAutocompletePlaceId = null
        val map = mMap ?: return
        map.clear()
        val markerTitle = when {
            isLoading -> "Loading details..."
            title != null -> title
            else -> "Selected Location"
        }
        map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(markerTitle)
        )?.showInfoWindow()
        viewModel.selectedLat = latLng.latitude
        viewModel.selectedLng = latLng.longitude

        setConfirmButtonLoading(isLoading)
    }

    private fun setConfirmButtonLoading(isLoading: Boolean) {
        val binding = _binding ?: return
        val context = context ?: return
        binding.btConfirm.isEnabled = !isLoading
        binding.btConfirm.text = if (isLoading) {
            "Fetching location..."
        } else {
            "Confirm Location"
        }

        // Use solid colors to avoid transparency over the map when disabled
        val colorRes = if (isLoading) R.color.md_theme_surfaceVariant else R.color.md_theme_primary
        binding.btConfirm.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, colorRes)
        )
    }

    private fun requestLocationPermissionIfNeeded() {
        val context = context
        if (context != null && !hasLocationPermission(context) && !viewModel.permissionDenied) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(context: Context): Boolean =
        checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        val map = mMap
        val context = context
        if (context != null && hasLocationPermission(context) && map != null) {
            map.isMyLocationEnabled = true
            viewModel.permissionDenied = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun selectUserLocation() {
        viewModel.activeAutocompletePlaceId = null
        val context = context
        if (context != null && hasLocationPermission(context)) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (viewLifecycleOwnerLiveData.value == null) return@addOnSuccessListener
                val map = mMap
                if (location != null && map != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateSelectedLocation(latLng, isLoading = true)
                    reverseGeocode(latLng)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM))
                }
            }
        }
    }

    fun setInitialLocation(lat: Double, lng: Double, placeName: String?, address: String?) {
        viewModel.selectedLat = lat
        viewModel.selectedLng = lng
        viewModel.selectedPlaceName = placeName
        viewModel.selectedAddress = address

        val map = mMap
        if (map != null && _binding != null) {
            val latLng = LatLng(lat, lng)
            updateSelectedLocation(latLng, placeName)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM))
        }
    }

    fun clearSelection() {
        viewModel.activeAutocompletePlaceId = null
        viewModel.selectedLat = null
        viewModel.selectedLng = null
        viewModel.selectedPlaceName = null
        viewModel.selectedAddress = null
        val map = mMap
        if (map != null) {
            map.clear()
            selectUserLocation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        mMap = null
    }

    companion object {
        const val TAG = "PickLocationFragment"
        const val CAMERA_ZOOM = 15f
        private val placeFields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.LOCATION,
            Place.Field.FORMATTED_ADDRESS
        )
    }
}
