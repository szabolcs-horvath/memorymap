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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.databinding.FragmentPickLocationBinding

class PickLocationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentPickLocationBinding? = null
    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var selectedPlaceName: String? = null
    private var selectedAddress: String? = null
    private var listener: PickLocationListener? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            enableMyLocation()
            selectUserLocation()
        }
    }

    private val autocompleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                val latLng = place.location
                if (latLng != null) {
                    selectedPlaceName = place.displayName
                    selectedAddress = place.formattedAddress
                    updateSelectedLocation(latLng, selectedPlaceName)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR) {
             val intent = result.data
             if (intent != null) {
                 val status = Autocomplete.getStatusFromIntent(intent)
                 Log.e(TAG, "Autocomplete error: ${status.statusMessage}")
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
                listener?.onLocationConfirmed(selectedLat!!, selectedLng!!, selectedPlaceName, selectedAddress)
            }
        }

        view.findViewById<Button>(R.id.searchButton).setOnClickListener {
            startAutocomplete()
        }
    }

    private fun startAutocomplete() {
        val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(requireContext())
        autocompleteLauncher.launch(intent)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.mapColorScheme = MapColorScheme.FOLLOW_SYSTEM
        mMap.uiSettings.isRotateGesturesEnabled = false
        mMap.uiSettings.isMyLocationButtonEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true

        val topPadding = binding.searchContainer.height + binding.searchContainer.top
        val bottomPadding = binding.confirmContainer.height + binding.confirmContainer.bottom
        mMap.setPadding(0, topPadding, 0, bottomPadding)

        requestLocationPermissionIfNeeded()
        selectUserLocation()

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
        mMap.addMarker(MarkerOptions()
            .position(latLng)
            .title(title ?: "Selected Location"))?.showInfoWindow()
        selectedLat = latLng.latitude
        selectedLng = latLng.longitude
    }

    private fun requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        enableMyLocation()
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (hasLocationPermission()) {
            mMap.isMyLocationEnabled = true
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun selectUserLocation() {
        if (hasLocationPermission()) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
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
        const val TAG = "PICK_LOCATION_TAG"
    }
}