package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.datepicker.MaterialDatePicker
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.MemoryOverlayAdapter
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentMapsBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.MultiColorMarkerGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.system.measureTimeMillis

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private var mMap: GoogleMap? = null
    private var selectedMarker: Marker? = null
    private var selectedMemoryId: Int? = null
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
    private var isInitialZoomDone = false

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
            updateDateFilterForMemory(memory)
            moveToLocationAndSelectMarker(lat, lng, memory)
        } else {
            initialSelectedLat = lat
            initialSelectedLng = lng
            initialSelectedId = id
        }
    }

    fun updateDateFilterForMemory(memory: MemoryGroup?) {
        if (memory != null) {
            val memoryStart = memory.startDate.toLocalDate()
            val memoryEnd = memory.endDate.toLocalDate()

            updateDateFilterForMemory(memoryStart, memoryEnd)
        }
    }

    fun updateDateFilterForMemory(memoryStart: LocalDate, memoryEnd: LocalDate) {
        val currentStart = filterStartDate ?: memoryStart
        val currentEnd = filterEndDate ?: memoryEnd

        val newStart = if (memoryStart.isBefore(currentStart)) memoryStart else currentStart
        val newEnd = if (memoryEnd.isAfter(currentEnd)) memoryEnd else currentEnd

        setDateFilter(newStart, newEnd)
    }

    fun setDateFilter(startDate: LocalDate, endDate: LocalDate) {
        filterStartDate = startDate
        filterEndDate = endDate
        updateDateRangeButtonText()
        updateMapMarkers()
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
            @Suppress("UNCHECKED_CAST")
            val groups = marker.tag as? List<MemoryGroup>
            if (groups != null) {
                selectedMemoryId = groups.firstOrNull()?.id
                showMemoryOverlay(marker.position.latitude, marker.position.longitude, groups)
                mMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        marker.position,
                        MAX_CAMERA_ZOOM
                    )
                )
            }
            true
        }

        googleMap.setOnMapLongClickListener { latLng ->
            listener?.startAddMemoryFlow(latLng.latitude, latLng.longitude)
        }

        googleMap.setOnMapClickListener {
            binding.overlayCard.visibility = View.GONE
            selectedMarker = null
            selectedMemoryId = null
            setGoogleMapPadding()
        }

        googleMap.setOnMyLocationButtonClickListener {
            binding.overlayCard.visibility = View.GONE
            selectedMarker = null
            selectedMemoryId = null
            setGoogleMapPadding()
            false
        }

        googleMap.setOnPoiClickListener {
            // Do nothing
        }

        binding.overlayCard.viewTreeObserver.addOnGlobalLayoutListener {
            setGoogleMapPadding()
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
        } else {
            enableMyLocation()
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun enableMyLocation() {
        val map = mMap
        if (hasLocationPermission() && map != null) {
            map.isMyLocationEnabled = true
            permissionDenied = false
            zoomToUserLocationIfPossible()
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
        if (!isInitialZoomDone && hasLocationPermission() && initialSelectedLat == null) {
            val fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val map = mMap
                if (location != null && map != null && !isInitialZoomDone && initialSelectedLat == null) {
                    isInitialZoomDone = true
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
        }
    }

    fun refreshData() {
        lifecycleScope.launch {
            val currentSelectedId = selectedMemoryId

            loadMarkers()

            if (currentSelectedId != null) {
                val updatedSelectedMemory = allGroups.find { it.id == currentSelectedId }
                if (updatedSelectedMemory != null) {
                    val marker = markerMap[currentSelectedId]
                    if (marker != null) {
                        selectedMarker = marker
                        @Suppress("UNCHECKED_CAST")
                        val groups = marker.tag as? List<MemoryGroup>
                        if (groups != null) {
                            showMemoryOverlay(
                                marker.position.latitude,
                                marker.position.longitude,
                                groups
                            )
                        }
                    } else {
                        binding.overlayCard.visibility = View.GONE
                        selectedMarker = null
                        selectedMemoryId = null
                        setGoogleMapPadding()
                    }
                } else {
                    binding.overlayCard.visibility = View.GONE
                    selectedMarker = null
                    selectedMemoryId = null
                    setGoogleMapPadding()
                }
            }
        }
    }

    private suspend fun loadMarkers() {
        val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
        allGroups = db.memoryGroupDao().getAllGroups()

        withContext(Dispatchers.Main) {
            val map = mMap
            if (map != null) {
                if (allGroups.isNotEmpty()) {
                    val minDate = allGroups.minOf { it.startDate.toLocalDate() }
                    val maxDate = allGroups.maxOf { it.endDate.toLocalDate() }

                    if (filterStartDate == null || filterEndDate == null) {
                        filterStartDate = minDate
                        filterEndDate = maxDate
                    }
                }

                updateDateRangeButtonText()
                updateMapMarkers()

                if (initialSelectedLat != null && initialSelectedLng != null) {
                    isInitialZoomDone = true
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

        val filteredGroups = allGroups.filter { group ->
            val groupStart = group.startDate.toLocalDate()
            val groupEnd = group.endDate.toLocalDate()
            !groupEnd.isBefore(start) && !groupStart.isAfter(end)
        }

        var clusters: Collection<List<MemoryGroup>>
        val duration = measureTimeMillis {
            clusters = clusterMemories(filteredGroups)
        }
        Log.d(TAG, "Clustering took $duration ms")

        val boundsBuilder = LatLngBounds.Builder()
        var markersCount = 0

        clusters.forEach { groups ->
            val marker = getMarker(groups, map)
            if (marker != null) {
                marker.tag = groups
                groups.forEach { group ->
                    markerMap[group.id] = marker
                }
                boundsBuilder.include(marker.position)
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

    fun clusterMemories(memories: List<MemoryGroup>): Collection<List<MemoryGroup>> {
        val n = memories.size
        val parent = IntArray(n) { it }

        fun find(i: Int): Int {
            if (parent[i] == i) return i
            parent[i] = find(parent[i]) // Path compression
            return parent[i]
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) parent[rootI] = rootJ
        }

        // O(N^2) comparisons
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (memories[i].isSameLocationAs(memories[j])) {
                    union(i, j)
                }
            }
        }

        return memories.indices.groupBy { find(it) }.values.map { indices ->
            indices.map { memories[it] }
        }
    }

    private fun getMarker(groups: List<MemoryGroup>, map: GoogleMap): Marker? {
        val representative = groups.first()
        val position = LatLng(representative.latitude, representative.longitude)
        val markerTitle = if (groups.size == 1) groups[0].title else "${groups.size} Memories"

        return if (groups.size > 1) {
            val colors =
                groups.map { ColorUtil.hueToColor(it.markerHue ?: BitmapDescriptorFactory.HUE_RED) }
                    .sorted()
            val density = resources.displayMetrics.density
            val bitmap = MultiColorMarkerGenerator.generateTapered(colors, groups.size, density)

            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(markerTitle)
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    .anchor(0.5f, 1.0f)
            )
        } else {
            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(markerTitle)
                    .icon(
                        BitmapDescriptorFactory.defaultMarker(
                            ColorUtil.normalizeHue(
                                representative.markerHue ?: BitmapDescriptorFactory.HUE_RED
                            )
                        )
                    )
            )
        }
    }

    private fun moveToLocationAndSelectMarker(lat: Double, lng: Double, memory: MemoryGroup?) {
        val map = mMap ?: return
        val position = LatLng(lat, lng)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, MAX_CAMERA_ZOOM))

        val marker = markerMap[memory?.id]
        if (marker != null) {
            selectedMarker = marker
            selectedMemoryId = memory?.id
            @Suppress("UNCHECKED_CAST")
            val groups = marker.tag as? List<MemoryGroup>
            if (groups != null) {
                showMemoryOverlay(lat, lng, groups)
            }
        }
    }

    private fun showMemoryOverlay(lat: Double, lng: Double, groups: List<MemoryGroup>) {
        val locationName = groups.firstOrNull { it.placeName != null }?.placeName
            ?: "Lat: %.4f, Lng: %.4f".format(lat, lng)

        binding.overlayLocationTitle.text = locationName
        binding.rvMemories.adapter = MemoryOverlayAdapter(groups) { memoryId ->
            listener?.onMemoryClicked(memoryId)
        }

        binding.overlayCard.visibility = View.VISIBLE
        setGoogleMapPadding()
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
