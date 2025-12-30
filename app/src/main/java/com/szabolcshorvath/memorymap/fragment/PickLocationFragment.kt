package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.szabolcshorvath.memorymap.R

class PickLocationFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
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

    interface PickLocationListener {
        fun onLocationConfirmed(lat: Double, lng: Double)
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
        return inflater.inflate(R.layout.fragment_pick_location, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        view.findViewById<Button>(R.id.confirmButton).setOnClickListener {
            if (selectedLat != null && selectedLng != null) {
                listener?.onLocationConfirmed(selectedLat!!, selectedLng!!)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isRotateGesturesEnabled = false
        mMap.uiSettings.isMyLocationButtonEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true

        requestLocationPermissionIfNeeded()
        selectUserLocation()

        mMap.setOnMapClickListener { latLng ->
            updateSelectedLocation(latLng)
        }

        mMap.setOnMyLocationButtonClickListener {
            selectUserLocation()
            true
        }
    }

    private fun updateSelectedLocation(latLng: LatLng) {
        mMap.clear()
        mMap.addMarker(MarkerOptions()
            .position(latLng)
            .title("Selected Location"))?.showInfoWindow()
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
        if (::mMap.isInitialized) {
            mMap.clear()
            selectUserLocation()
        }
    }

    companion object {
        const val TAG = "PICK_LOCATION_TAG"
    }
}