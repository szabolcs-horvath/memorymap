package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.launch

class PickLocationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentPickLocationBinding? = null
    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var selectedPlaceName: String? = null
    private var selectedAddress: String? = null
    private var listener: PickLocationListener? = null

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
                                    mMap.animateCamera(
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

        view.findViewById<Button>(R.id.confirmButton).setOnClickListener {
            if (selectedLat != null && selectedLng != null) {
                listener?.onLocationConfirmed(
                    selectedLat!!,
                    selectedLng!!,
                    selectedPlaceName,
                    selectedAddress
                )
            }
        }

        view.findViewById<Button>(R.id.searchButton).setOnClickListener {
            startAutocomplete()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            selectUserLocation()
        }
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

        mMap.mapColorScheme = MapColorScheme.FOLLOW_SYSTEM
        mMap.uiSettings.isRotateGesturesEnabled = false
        mMap.uiSettings.isMapToolbarEnabled = false
        mMap.uiSettings.isMyLocationButtonEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true

        val topPadding = binding.searchContainer.height + binding.searchContainer.top
        val bottomPadding = binding.confirmContainer.height + binding.confirmContainer.bottom
        mMap.setPadding(0, topPadding, 0, bottomPadding)

        mMap.setOnMapClickListener { latLng ->
            selectedPlaceName = null
            selectedAddress = null
            updateSelectedLocation(latLng)
        }

        mMap.setOnPoiClickListener { poi ->
            selectedPlaceName = poi.name
            // We don't get address from Poi click immediately, would need Places API fetch if critical
            selectedAddress = null
            updateSelectedLocation(poi.latLng, poi.name)
        }

        mMap.setOnMyLocationButtonClickListener {
            selectUserLocation()
            true
        }
    }

    private fun updateSelectedLocation(latLng: LatLng, title: String? = null) {
        mMap.clear()
        mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title ?: "Selected Location")
        )?.showInfoWindow()
        selectedLat = latLng.latitude
        selectedLng = latLng.longitude
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
    private fun selectUserLocation() {
        if (hasLocationPermission()) {
            val fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateSelectedLocation(latLng)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        }
    }

    fun clearSelection() {
        selectedLat = null
        selectedLng = null
        selectedPlaceName = null
        selectedAddress = null
        if (::mMap.isInitialized) {
            mMap.clear()
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