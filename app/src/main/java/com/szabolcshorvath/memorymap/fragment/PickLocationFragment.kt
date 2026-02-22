package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class PickLocationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentPickLocationBinding? = null
    private val binding get() = _binding!!
    private var mMap: GoogleMap? = null
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var selectedPlaceName: String? = null
    private var selectedAddress: String? = null
    private var listener: PickLocationListener? = null

    private var permissionDenied = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
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
                        val sessionTokenFromIntent =
                            PlaceAutocomplete.getSessionTokenFromIntent(intent)
                        val placesClient = Places.createClient(requireContext())

                        lifecycleScope.launch {
                            try {
                                val response =
                                    placesClient.awaitFetchPlace(prediction.placeId, placeFields) {
                                        sessionToken = sessionTokenFromIntent
                                    }
                                val place = response.place
                                val latLng = place.location
                                if (latLng != null) {
                                    selectedPlaceName = place.displayName
                                    selectedAddress = place.formattedAddress
                                    updateSelectedLocation(latLng, selectedPlaceName)
                                    mMap?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            latLng,
                                            15f
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching place details: ${e.message}", e)
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
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPickLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.confirmButton.setOnClickListener {
            if (selectedLat != null && selectedLng != null) {
                listener?.onLocationConfirmed(
                    selectedLat!!,
                    selectedLng!!,
                    selectedPlaceName,
                    selectedAddress
                )
            }
        }

        binding.searchButton.setOnClickListener {
            startAutocomplete()
        }

        binding.root.doOnLayout {
            setGoogleMapPadding()
        }
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
            updateSelectedLocation(latLng)
            reverseGeocode(latLng)
        }

        googleMap.setOnPoiClickListener { poi ->
            selectedPlaceName = poi.name
            selectedAddress = null
            updateSelectedLocation(poi.latLng, poi.name)
            reverseGeocode(poi.latLng)
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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
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
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (selectedPlaceName == null) {
                                    selectedPlaceName = address.featureName ?: address.thoroughfare
                                }
                                selectedAddress = address.getAddressLine(0)
                                updateSelectedLocation(latLng, selectedPlaceName)
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
                            if (selectedPlaceName == null) {
                                selectedPlaceName = address.featureName ?: address.thoroughfare
                            }
                            selectedAddress = address.getAddressLine(0)
                            updateSelectedLocation(latLng, selectedPlaceName)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reverse geocoding failed for $latLng: ${e.message}", e)
            }
        }
    }

    private fun setGoogleMapPadding() {
        val map = mMap ?: return
        val topPadding = binding.searchContainer.height + binding.searchContainer.top
        val bottomPadding =
            binding.confirmContainer.height + (binding.root.height - binding.confirmContainer.bottom)
        map.setPadding(0, topPadding, 0, bottomPadding)
    }

    private fun updateSelectedLocation(latLng: LatLng, title: String? = null) {
        val map = mMap ?: return
        map.clear()
        map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title ?: "Selected Location")
        )?.showInfoWindow()
        selectedLat = latLng.latitude
        selectedLng = latLng.longitude
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

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

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
        if (hasLocationPermission()) {
            val fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val map = mMap
                if (location != null && map != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateSelectedLocation(latLng)
                    reverseGeocode(latLng)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        }
    }

    fun clearSelection() {
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
        private val placeFields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.LOCATION,
            Place.Field.FORMATTED_ADDRESS
        )
    }
}