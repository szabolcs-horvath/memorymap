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
    private var mMap: GoogleMap? = null
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var selectedPlaceName: String? = null
    private var selectedAddress: String? = null
    private var pickLocationListener: PickLocationListener? = null

    private var permissionDenied = false
    private var activeAutocompletePlaceId: String? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
            selectUserLocation()
        } else {
            permissionDenied = true
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
                        val placesClient = Places.createClient(requireContext())

                        // Invalidate any ongoing map/POI selection or other autocomplete requests
                        activeAutocompletePlaceId = requestedPlaceId
                        selectedLat = null
                        selectedLng = null
                        selectedPlaceName = null
                        selectedAddress = null
                        mMap?.clear()

                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                setConfirmButtonLoading(true)
                                val response = placesClient.awaitFetchPlace(requestedPlaceId, placeFields) {
                                    sessionToken = sessionTokenFromIntent
                                }

                                // Race condition check: Only proceed if this is still the active request
                                if (activeAutocompletePlaceId != requestedPlaceId) return@launch

                                val place = response.place
                                val latLng = place.location
                                if (latLng != null) {
                                    selectedPlaceName = place.displayName
                                    selectedAddress = place.formattedAddress
                                    updateSelectedLocation(latLng, selectedPlaceName)
                                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM))
                                } else {
                                    Log.e(TAG, "Fetched place has no location")
                                    if (activeAutocompletePlaceId == requestedPlaceId) {
                                        setConfirmButtonLoading(false)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching place details: ${e.message}", e)
                                if (activeAutocompletePlaceId == requestedPlaceId) {
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

        binding.confirmButton.setOnClickListener {
            if (selectedLat != null && selectedLng != null) {
                pickLocationListener?.onLocationConfirmed(
                    selectedLat!!,
                    selectedLng!!,
                    selectedPlaceName,
                    selectedAddress
                )
            }
        }

        binding.searchButton.setOnClickListener { startAutocomplete() }

        binding.root.doOnLayout { setGoogleMapPadding() }
    }

    override fun onResume() {
        super.onResume()
        requestLocationPermissionIfNeeded()
    }

    private fun startAutocomplete() {
        val sessionToken = AutocompleteSessionToken.newInstance()
        val intent = PlaceAutocomplete.createIntent(requireContext()) {
            setAutocompleteSessionToken(sessionToken)
        }
        autocompleteLauncher.launch(intent)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        googleMap.mapColorScheme = MapColorScheme.FOLLOW_SYSTEM
        googleMap.uiSettings.isRotateGesturesEnabled = false
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.uiSettings.isZoomControlsEnabled = true

        enableMyLocation()
        setGoogleMapPadding()

        googleMap.setOnMapClickListener { latLng ->
            selectedPlaceName = null
            selectedAddress = null
            updateSelectedLocation(latLng, isLoading = true)
            reverseGeocode(latLng)
        }

        googleMap.setOnPoiClickListener { poi ->
            val placeId = poi.placeId
            val poiLatLng = poi.latLng
            updateSelectedLocation(poiLatLng, isLoading = true)

            val placesClient = Places.createClient(requireContext())
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = placesClient.awaitFetchPlace(placeId, placeFields)
                    val place = response.place

                    // Race condition check: Only update if the user hasn't clicked elsewhere
                    if (poiLatLng.latitude == selectedLat && poiLatLng.longitude == selectedLng) {
                        selectedPlaceName = place.displayName
                        selectedAddress = place.formattedAddress
                        updateSelectedLocation(poiLatLng, selectedPlaceName)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching place details for POI: ${e.message}", e)
                    // Fallback to reverse geocoding for address if fetch fails
                    if (poiLatLng.latitude == selectedLat && poiLatLng.longitude == selectedLng) {
                        reverseGeocode(poiLatLng)
                    }
                }
            }
        }

        googleMap.setOnMyLocationButtonClickListener {
            selectUserLocation()
            true
        }

        if (selectedLat == null) {
            selectUserLocation()
        }
    }

    private fun reverseGeocode(latLng: LatLng) {
        // Keep a reference to the coordinates for this specific request
        val requestLatLng = latLng
        val context = context ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                        // RACE CONDITION CHECK:
                        // Only process the result if the currently selected location hasn't changed
                        // since this request was initiated.
                        if (requestLatLng.latitude != selectedLat || requestLatLng.longitude != selectedLng) {
                            return@getFromLocation
                        }

                        if (addresses.isNotEmpty()) {
                            val address = addresses.first()
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                // Re-verify on the Main thread before updating UI
                                if (requestLatLng.latitude != selectedLat || requestLatLng.longitude != selectedLng) {
                                    return@launch
                                }
                                if (selectedPlaceName == null) {
                                    selectedPlaceName = address.featureName ?: address.thoroughfare
                                }
                                selectedAddress = address.getAddressLine(0)
                                updateSelectedLocation(latLng, selectedPlaceName)
                            }
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                if (requestLatLng.latitude == selectedLat && requestLatLng.longitude == selectedLng) {
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
                    if (requestLatLng.latitude != selectedLat || requestLatLng.longitude != selectedLng) {
                        return@launch
                    }

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses.first()
                        withContext(Dispatchers.Main) {
                            if (requestLatLng.latitude == selectedLat && requestLatLng.longitude == selectedLng) {
                                if (selectedPlaceName == null) {
                                    selectedPlaceName = address.featureName ?: address.thoroughfare
                                }
                                selectedAddress = address.getAddressLine(0)
                                updateSelectedLocation(latLng, selectedPlaceName)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (requestLatLng.latitude == selectedLat && requestLatLng.longitude == selectedLng) {
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
                    if (requestLatLng.latitude == selectedLat && requestLatLng.longitude == selectedLng) {
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
        activeAutocompletePlaceId = null
        val map = mMap ?: return
        map.clear()
        val markerTitle = when {
            isLoading -> "Loading details…"
            title != null -> title
            else -> "Selected Location"
        }
        map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(markerTitle)
        )?.showInfoWindow()
        selectedLat = latLng.latitude
        selectedLng = latLng.longitude

        setConfirmButtonLoading(isLoading)
    }

    private fun setConfirmButtonLoading(isLoading: Boolean) {
        binding.confirmButton.isEnabled = !isLoading
        binding.confirmButton.text = if (isLoading) {
            "Fetching location…"
        } else {
            "Confirm Location"
        }

        // Use solid colors to avoid transparency over the map when disabled
        val colorRes = if (isLoading) R.color.md_theme_surfaceVariant else R.color.md_theme_primary
        binding.confirmButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), colorRes)
        )
    }

    private fun requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission() && !permissionDenied) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ||
            checkPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        val map = mMap
        if (hasLocationPermission() && map != null) {
            map.isMyLocationEnabled = true
            permissionDenied = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun selectUserLocation() {
        activeAutocompletePlaceId = null
        if (hasLocationPermission()) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
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

    fun clearSelection() {
        activeAutocompletePlaceId = null
        selectedLat = null
        selectedLng = null
        selectedPlaceName = null
        selectedAddress = null
        val map = mMap
        if (map != null) {
            map.clear()
            selectUserLocation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
