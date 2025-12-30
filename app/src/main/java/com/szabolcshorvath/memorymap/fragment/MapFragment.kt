package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.datepicker.MaterialDatePicker
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentMapsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private var selectedMarker: Marker? = null
    private val markerMap = mutableMapOf<Int, Marker>()
    private var listener: MapListener? = null

    private var allGroups: List<MemoryGroup> = emptyList()
    private var filterStartDate: LocalDate? = null
    private var filterEndDate: LocalDate? = null
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    // Parameters to handle initial selection
    private var initialSelectedLat: Double? = null
    private var initialSelectedLng: Double? = null
    private var initialSelectedId: Int? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            enableMyLocation()
        }
    }

    interface MapListener {
        fun onNavigateToTimeline(memoryId: Int)
        fun startAddMemoryFlow(lat: Double, lng: Double)
        fun onMemoryClicked(id: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MapListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.overlayActionButton.text = "Show Details"

        binding.btnDateRange.setOnClickListener { showDateRangePicker() }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Select dates")

        if (filterStartDate != null && filterEndDate != null) {
            val startMillis = filterStartDate!!.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            val endMillis = filterEndDate!!.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            builder.setSelection(androidx.core.util.Pair(startMillis, endMillis))
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val startMillis = selection.first
            val endMillis = selection.second

            filterStartDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            filterEndDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()

            updateDateRangeButtonText()
            if (::mMap.isInitialized) {
                updateMapMarkers(adjustCamera = true)
            }
        }
        picker.show(childFragmentManager, picker.toString())
    }

    private fun updateDateRangeButtonText() {
        if (filterStartDate != null && filterEndDate != null) {
            binding.btnDateRange.text = "${dateFormatter.format(filterStartDate)} - ${dateFormatter.format(filterEndDate)}"
        } else {
            binding.btnDateRange.text = "Select Date Range"
        }
    }

    fun focusOnMemory(lat: Double, lng: Double, id: Int) {
        if (::mMap.isInitialized) {
            moveToLocationAndSelectMarker(lat, lng, id)
        } else {
             initialSelectedLat = lat
             initialSelectedLng = lng
             initialSelectedId = id
        }
    }

    fun setDateFilter(startDate: LocalDate, endDate: LocalDate) {
        filterStartDate = startDate
        filterEndDate = endDate
        if (::mMap.isInitialized) {
            updateDateRangeButtonText()
            updateMapMarkers()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isRotateGesturesEnabled = false
        mMap.uiSettings.isMyLocationButtonEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true

        loadMarkers()
        requestLocationPermissionIfNeeded()
        zoomToUserLocationIfPossible()

        mMap.setOnMarkerClickListener { marker ->
            selectedMarker = marker
            moveToLocationAndSelectMarker(marker.position.latitude, marker.position.longitude, (marker.tag as MemoryGroup).id)
            true
        }

        mMap.setOnMapLongClickListener { latLng ->
            listener?.startAddMemoryFlow(latLng.latitude, latLng.longitude)
        }

        mMap.setOnMapClickListener {
            binding.overlayCard.visibility = View.GONE
            selectedMarker = null
        }

        mMap.setOnMyLocationButtonClickListener {
            binding.overlayCard.visibility = View.GONE
            selectedMarker = null
            false
        }

        binding.overlayActionButton.setOnClickListener {
            selectedMarker?.let { marker ->
                 val group = marker.tag as? MemoryGroup
                 if (group != null) {
                     listener?.onMemoryClicked(group.id)
                 }
            }
        }
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

    @SuppressWarnings("MissingPermission")
    private fun enableMyLocation() {
        if (hasLocationPermission()) {
            mMap.isMyLocationEnabled = true
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressWarnings("MissingPermission")
    private fun zoomToUserLocationIfPossible() {
        if (hasLocationPermission() && initialSelectedLat == null) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    fun refreshData() {
        if (::mMap.isInitialized) {
            loadMarkers()
        }
    }

    private fun loadMarkers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
            allGroups = db.memoryGroupDao().getAllGroups()

            withContext(Dispatchers.Main) {
                if (::mMap.isInitialized) {
                    if (filterStartDate == null && allGroups.isNotEmpty()) {
                        val dates = allGroups.flatMap { listOf(it.startDate.toLocalDate(), it.endDate.toLocalDate()) }
                        filterStartDate = dates.minOrNull()
                        filterEndDate = dates.maxOrNull()
                    }
                    
                    updateDateRangeButtonText()
                    updateMapMarkers()

                    if (initialSelectedLat != null && initialSelectedLng != null) {
                        moveToLocationAndSelectMarker(
                            initialSelectedLat!!,
                            initialSelectedLng!!,
                            initialSelectedId ?: -1
                        )
                        initialSelectedLat = null
                        initialSelectedLng = null
                        initialSelectedId = null
                    }
                }
            }
        }
    }

    private fun updateMapMarkers(adjustCamera: Boolean = false) {
        mMap.clear()
        markerMap.clear()
        val debugText = StringBuilder("Markers:\n")

        val start = filterStartDate ?: LocalDate.MIN
        val end = filterEndDate ?: LocalDate.MAX
        
        val boundsBuilder = LatLngBounds.Builder()
        var markersCount = 0

        allGroups.forEach { group ->
            val groupStart = group.startDate.toLocalDate()
            val groupEnd = group.endDate.toLocalDate()

            // Check if the memory overlaps with the selected range
            if (!groupEnd.isBefore(start) && !groupStart.isAfter(end)) {
                val position = LatLng(group.latitude, group.longitude)
                val marker = mMap.addMarker(MarkerOptions().position(position).title(group.title))
                if (marker != null) {
                    marker.tag = group
                    markerMap[group.id] = marker
                    debugText.append("${group.title} (${group.id})\n")
                }
                boundsBuilder.include(LatLng(group.latitude, group.longitude))
                markersCount++
            }
        }
        binding.debugMarkersList.text = debugText.toString()

        if (adjustCamera && markersCount > 0) {
            val bounds = boundsBuilder.build()
            val padding = 100 // pixels
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
    }

    private fun moveToLocationAndSelectMarker(lat: Double, lng: Double, memoryId: Int) {
        val position = LatLng(lat, lng)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))

        val marker = markerMap[memoryId]
        if (marker != null) {
            selectedMarker = marker
            binding.overlayTitle.text = marker.title
            val group = marker.tag as? MemoryGroup
            if (group != null) {
                binding.overlayDescription.text = "Date: ${group.getFormattedDate()}"
            } else {
                binding.overlayDescription.text = "Lat: ${marker.position.latitude}, Lng: ${marker.position.longitude}"
            }
            binding.overlayCard.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MAP_TAG"
    }
}