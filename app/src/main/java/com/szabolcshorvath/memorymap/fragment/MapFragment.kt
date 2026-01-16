package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
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
    private var mMap: GoogleMap? = null
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

    private var permissionDenied = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            enableMyLocation()
        } else {
            permissionDenied = true
            Toast.makeText(
                requireContext(),
                "Location permissions are required for My Location to work",
                Toast.LENGTH_SHORT
            ).show()
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.overlayActionButton.text = "Show Details"
        binding.btnDateRange.setOnClickListener { showDateRangePicker() }

        binding.root.doOnLayout {
            setGoogleMapPadding()
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Select dates")

        if (filterStartDate != null && filterEndDate != null) {
            val startMillis =
                filterStartDate!!.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            val endMillis =
                filterEndDate!!.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            builder.setSelection(androidx.core.util.Pair(startMillis, endMillis))
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val startMillis = selection.first
            val endMillis = selection.second

            filterStartDate =
                Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            filterEndDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()

            updateDateRangeButtonText()
            updateMapMarkers(adjustCamera = true)
        }
        picker.show(childFragmentManager, picker.toString())
    }

    private fun updateDateRangeButtonText() {
        if (filterStartDate != null && filterEndDate != null) {
            if (filterStartDate != filterEndDate) {
                binding.btnDateRange.text =
                    "${dateFormatter.format(filterStartDate)} - ${dateFormatter.format(filterEndDate)}"
            } else {
                binding.btnDateRange.text = "${dateFormatter.format(filterStartDate)}"
            }
        } else {
            binding.btnDateRange.text = "Select Date Range"
        }
    }

    fun focusOnMemory(lat: Double, lng: Double, id: Int) {
        val map = mMap
        if (map != null) {
            val memory = allGroups.find { it.id == id }
            setDateFilter(memory)
            moveToLocationAndSelectMarker(lat, lng, memory)
        } else {
            initialSelectedLat = lat
            initialSelectedLng = lng
            initialSelectedId = id
        }
    }

    fun setDateFilter(startDate: LocalDate, endDate: LocalDate) {
        filterStartDate = startDate
        filterEndDate = endDate
        updateDateRangeButtonText()
        updateMapMarkers()
    }

    fun setDateFilter(memory: MemoryGroup?) {
        if (memory != null) {
            val startDate = memory.startDate.toLocalDate()
            val endDate = memory.endDate.toLocalDate()
            setDateFilter(startDate, endDate)
        }
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

        lifecycleScope.launch {
            loadMarkers()
        }

        googleMap.setOnMarkerClickListener { marker ->
            selectedMarker = marker
            moveToLocationAndSelectMarker(
                marker.position.latitude, marker.position.longitude, marker.tag as? MemoryGroup
            )
            true
        }

        googleMap.setOnMapLongClickListener { latLng ->
            listener?.startAddMemoryFlow(latLng.latitude, latLng.longitude)
        }

        googleMap.setOnMapClickListener {
            binding.overlayCard.visibility = View.GONE
            selectedMarker = null
        }

        googleMap.setOnMyLocationButtonClickListener {
            binding.overlayCard.visibility = View.GONE
            selectedMarker = null
            false
        }

        googleMap.setOnPoiClickListener {
            // Do nothing
        }

        binding.overlayCard.viewTreeObserver.addOnGlobalLayoutListener {
            setGoogleMapPadding()
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

    private fun setGoogleMapPadding() {
        val map = mMap ?: return
        val topPadding = binding.dateFilterContainer.height + binding.dateFilterContainer.top
        if (binding.overlayCard.isVisible) {
            map.setPadding(0, topPadding, 0, binding.overlayCard.height + 10)
        } else {
            map.setPadding(0, topPadding, 0, 0)
        }
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
        enableMyLocation()
    }

    @SuppressWarnings("MissingPermission")
    private fun enableMyLocation() {
        val map = mMap
        if (hasLocationPermission() && map != null) {
            map.isMyLocationEnabled = true
            permissionDenied = false
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressWarnings("MissingPermission")
    private fun zoomToUserLocationIfPossible() {
        if (hasLocationPermission() && initialSelectedLat == null) {
            val fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val map = mMap
                if (location != null && map != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
        lifecycleScope.launch {
            requestLocationPermissionIfNeeded()
            zoomToUserLocationIfPossible()
        }
    }

    fun refreshData() {
        lifecycleScope.launch {
            loadMarkers()
            if (!allGroups.contains(selectedMarker?.tag)) {
                binding.overlayCard.visibility = View.GONE
            }
        }
    }

    private suspend fun loadMarkers() {
        val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
        allGroups = db.memoryGroupDao().getAllGroups()

        withContext(Dispatchers.Main) {
            val map = mMap
            if (map != null) {
                if (filterStartDate == null || filterEndDate == null) {
                    filterStartDate = LocalDate.now()
                    filterEndDate = LocalDate.now()
                }

                updateDateRangeButtonText()
                updateMapMarkers()

                if (initialSelectedLat != null && initialSelectedLng != null) {
                    moveToLocationAndSelectMarker(
                        initialSelectedLat!!,
                        initialSelectedLng!!,
                        allGroups.find { it.id == initialSelectedId })
                    initialSelectedLat = null
                    initialSelectedLng = null
                    initialSelectedId = null
                }
            }
        }
    }

    private fun updateMapMarkers(adjustCamera: Boolean = false) {
        val map = mMap ?: return
        map.clear()
        markerMap.clear()

        val start = filterStartDate ?: LocalDate.MIN
        val end = filterEndDate ?: LocalDate.MAX

        val boundsBuilder = LatLngBounds.Builder()
        var markersCount = 0

        allGroups.forEach { group ->
            val groupStart = group.startDate.toLocalDate()
            val groupEnd = group.endDate.toLocalDate()

            if (!groupEnd.isBefore(start) && !groupStart.isAfter(end)) {
                val position = LatLng(group.latitude, group.longitude)
                val markerTitle = group.title
                val marker = map.addMarker(MarkerOptions().position(position).title(markerTitle))
                if (marker != null) {
                    marker.tag = group
                    markerMap[group.id] = marker
                }
                boundsBuilder.include(LatLng(group.latitude, group.longitude))
                markersCount++
            }
        }

        if (adjustCamera && markersCount > 0) {
            val bounds = boundsBuilder.build()
            map.setMaxZoomPreference(MAX_CAMERA_ZOOM)
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, ZOOM_PADDING),
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        map.resetMinMaxZoomPreference()
                    }

                    override fun onCancel() {
                        map.resetMinMaxZoomPreference()
                    }
                })
        }
    }

    private fun moveToLocationAndSelectMarker(lat: Double, lng: Double, memory: MemoryGroup?) {
        val map = mMap ?: return
        val position = LatLng(lat, lng)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, MAX_CAMERA_ZOOM))

        val marker = markerMap[memory?.id]
        if (marker != null) {
            selectedMarker = marker
            binding.overlayTitle.text = marker.title
            val group = marker.tag as? MemoryGroup
            if (group != null) {
                val description = if (group.placeName != null) {
                    "${group.placeName}\nDate: ${group.getFormattedDate()}"
                } else {
                    "Date: ${group.getFormattedDate()}"
                }
                binding.overlayDescription.text = description
            } else {
                binding.overlayDescription.text =
                    "Lat: ${marker.position.latitude}, Lng: ${marker.position.longitude}"
            }
            binding.overlayCard.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MapFragment"
        private const val MAX_CAMERA_ZOOM = 15f
        private const val ZOOM_PADDING = 100
    }
}
