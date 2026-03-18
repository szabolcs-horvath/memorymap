package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
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
import com.szabolcshorvath.memorymap.MainActivity
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.MemoryOverlayAdapter
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.databinding.FragmentMapsBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.MultiColorMarkerGenerator
import com.szabolcshorvath.memorymap.util.PermissionUtil.checkPermission
import ir.mahozad.android.PieChart.Slice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.measureTimeMillis

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private var mMap: GoogleMap? = null
    private var selectedMarker: Marker? = null
    private var selectedMemoryId: Int? = null
    private var selectedMarkerPosition: LatLng? = null
    private val markerMap = mutableMapOf<String, Marker>()
    private var listener: MapListener? = null
    private var overlayAdapter: MemoryOverlayAdapter? = null

    private var allGroups: List<MemoryGroup> = emptyList()
    private var allFragments: List<MemoryFragment> = emptyList()
    private var filterStartDate: LocalDate? = null
    private var filterEndDate: LocalDate? = null

    // Parameters to handle initial selection
    private var initialSelectedLat: Double? = null
    private var initialSelectedLng: Double? = null
    private var initialSelectedId: Int? = null

    private var permissionDenied = false
    private var isInitialZoomDone = false
    private var refreshJob: Job? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
        } else {
            permissionDenied = true
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

        binding.btnDateRange.setOnClickListener { showDateRangePicker() }
        binding.btnStats.setOnClickListener { toggleStatsOverlay() }

        binding.root.doOnLayout {
            setGoogleMapPadding()
        }

        setupOverlayRecyclerView()

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                requireContext().dataStore.data
                    .map { it[MainActivity.SHOW_FRAGMENT_MARKERS] ?: false }
                    .distinctUntilChanged()
                    .collect {
                        if (mMap != null) {
                            updateMapMarkers()
                        }
                    }
            }
        }
    }

    private fun setupOverlayRecyclerView() {
        overlayAdapter = MemoryOverlayAdapter { memoryId ->
            listener?.onMemoryClicked(memoryId)
        }
        binding.rvMemories.apply {
            adapter = overlayAdapter
            itemAnimator = null // Disable cross-fade to eliminate "flickering" between markers
        }
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
            lifecycleScope.launch {
                updateMapMarkers(adjustCamera = true)
            }
        }
        picker.show(childFragmentManager, picker.toString())
    }

    private fun toggleStatsOverlay() {
        val transition = TransitionSet().apply {
            addTransition(Fade())
            addTransition(ChangeBounds())
            duration = ANIMATION_DURATION
        }
        TransitionManager.beginDelayedTransition(binding.root, transition)
        binding.statsOverlayCard.visibility =
            if (binding.statsOverlayCard.isVisible) View.GONE else View.VISIBLE
    }

    private fun updateDateRangeButtonText() {
        val dateFormatter = dateFormatter()
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

    suspend fun focusOnMemory(lat: Double, lng: Double, id: Int) {
        val googleMap = mMap
        if (googleMap != null) {
            val memory = allGroups.find { it.id == id }
            updateDateFilterForMemory(memory)
            moveToLocationAndSelectMarker(lat, lng, memory)
        } else {
            initialSelectedLat = lat
            initialSelectedLng = lng
            initialSelectedId = id
        }
    }

    suspend fun updateDateFilterForMemory(memory: MemoryGroup?) {
        if (memory != null) {
            val memoryStart = memory.startDate.toLocalDate()
            val memoryEnd = memory.endDate.toLocalDate()

            updateDateFilterForMemory(memoryStart, memoryEnd)
        }
    }

    suspend fun updateDateFilterForMemory(memoryStart: LocalDate, memoryEnd: LocalDate) {
        val currentStart = filterStartDate ?: memoryStart
        val currentEnd = filterEndDate ?: memoryEnd

        val newStart = if (memoryStart.isBefore(currentStart)) memoryStart else currentStart
        val newEnd = if (memoryEnd.isAfter(currentEnd)) memoryEnd else currentEnd

        filterStartDate = newStart
        filterEndDate = newEnd

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

        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            refreshData()
        }

        googleMap.setOnMarkerClickListener { marker ->
            selectedMarker = marker
            selectedMarkerPosition = marker.position
            @Suppress("UNCHECKED_CAST")
            val items = marker.tag as? List<Markerable>
            if (items != null) {
                selectedMemoryId = items.firstOrNull()?.groupId
                showMemoryOverlay(marker.position.latitude, marker.position.longitude, items)
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
            hideMemoryOverlay()
            if (binding.statsOverlayCard.isVisible) toggleStatsOverlay()
        }

        googleMap.setOnMyLocationButtonClickListener {
            hideMemoryOverlay()
            false
        }

        googleMap.setOnPoiClickListener {
            // Do nothing
        }

        binding.overlayCard.viewTreeObserver.addOnGlobalLayoutListener {
            setGoogleMapPadding()
        }
    }

    private fun hideMemoryOverlay() {
        if (binding.overlayCard.isVisible) {
            TransitionManager.beginDelayedTransition(
                binding.root,
                TransitionSet().apply {
                    addTransition(Fade())
                    addTransition(ChangeBounds())
                    duration = ANIMATION_DURATION
                }
            )
            binding.overlayCard.visibility = View.GONE
            selectedMarker = null
            selectedMemoryId = null
            selectedMarkerPosition = null
            setGoogleMapPadding()
        }
    }

    private fun setGoogleMapPadding() {
        val googleMap = mMap ?: return
        val topPadding = binding.dateFilterContainer.height + binding.dateFilterContainer.top
        if (binding.overlayCard.isVisible) {
            googleMap.setPadding(0, topPadding, 0, binding.overlayCard.height + GOOGLE_LOGO_HEIGHT)
        } else {
            googleMap.setPadding(0, topPadding, 0, 0)
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
        val googleMap = mMap
        if (hasLocationPermission() && googleMap != null) {
            googleMap.isMyLocationEnabled = true
            permissionDenied = false
            zoomToUserLocationIfPossible()
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ||
                checkPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressWarnings("MissingPermission")
    private fun zoomToUserLocationIfPossible() {
        if (hasLocationPermission() && initialZoomAndCoordinatesNotReady()
        ) {
            val fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val googleMap = mMap
                if (location != null && googleMap != null && initialZoomAndCoordinatesNotReady()) {
                    isInitialZoomDone = true
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            latLng,
                            DEFAULT_ZOOM
                        )
                    )
                }
            }
        }
    }

    private fun initialZoomAndCoordinatesNotReady(): Boolean =
        !isInitialZoomDone && (initialSelectedLat == null || initialSelectedLng == null)

    override fun onResume() {
        super.onResume()
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            refreshData()
            requestLocationPermissionIfNeeded()
        }
    }

    suspend fun refreshData() {
        val currentSelectedId = selectedMemoryId
        val currentPos = selectedMarkerPosition

        loadMarkers()

        if (currentSelectedId != null) {
            val updatedSelectedMemory = allGroups.find { it.id == currentSelectedId }
            if (updatedSelectedMemory != null) {
                // Try to find the exact marker we had selected (by position)
                var marker: Marker? = null
                if (currentPos != null) {
                    marker = markerMap.values.find { it.position == currentPos }
                }
                // Fallback to default marker for the ID
                if (marker == null) {
                    marker = markerMap[currentSelectedId.toString()]
                }

                if (marker != null) {
                    showMemoryOverlay(marker)
                } else {
                    hideMemoryOverlay()
                }
            } else {
                hideMemoryOverlay()
            }
        }
    }

    private suspend fun loadMarkers() {
        val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
        allGroups = db.memoryGroupDao().getAllGroups()
        allFragments = db.memoryGroupDao().getAllFragments()

        withContext(Dispatchers.Main) {
            val googleMap = mMap
            if (googleMap != null) {
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
                        allGroups.find { it.id == initialSelectedId }
                    )
                    initialSelectedLat = null
                    initialSelectedLng = null
                    initialSelectedId = null
                }
            }
        }
    }

    private suspend fun updateMapMarkers(adjustCamera: Boolean = false) {
        Log.d(TAG, "Updating map markers")
        val googleMap = mMap ?: return

        val start = filterStartDate ?: LocalDate.MIN
        val end = filterEndDate ?: LocalDate.MAX

        val showFragments = requireContext().dataStore.data
            .first()[MainActivity.SHOW_FRAGMENT_MARKERS] ?: false

        // Perform filtering and clustering in the background
        val filteredItems = withContext(Dispatchers.Default) {
            val groupsMap = allGroups.associateBy { it.id }
            val candidateItems = mutableListOf<Markerable>()
            candidateItems.addAll(allGroups)

            if (showFragments) {
                allFragments.forEach { fragment ->
                    groupsMap[fragment.groupId]?.let { parent ->
                        fragment.title = parent.title
                        candidateItems.add(fragment)
                    }
                }
            }

            candidateItems.filter { item ->
                val itemStart =
                    (item.startDate ?: groupsMap[item.groupId]?.startDate)?.toLocalDate()
                        ?: LocalDate.MIN
                val itemEnd = (item.endDate ?: groupsMap[item.groupId]?.endDate)?.toLocalDate()
                    ?: LocalDate.MAX
                !itemEnd.isBefore(start) && !itemStart.isAfter(end)
            }
        }

        val clusters = withContext(Dispatchers.Default) {
            var result: Collection<List<Markerable>>
            val duration = measureTimeMillis {
                result = clusterMarkerables(filteredItems)
            }
            Log.d(TAG, "Clustering took $duration ms")
            result
        }

        updateUIWithFreshMarkers(googleMap, filteredItems, clusters, adjustCamera)
    }

    private suspend fun updateUIWithFreshMarkers(
        googleMap: GoogleMap,
        filteredItems: List<Markerable>,
        clusters: Collection<List<Markerable>>,
        adjustCamera: Boolean
    ) {
        withContext(Dispatchers.Main) {
            googleMap.clear()
            markerMap.clear()

            // Update stats
            val totalCount = filteredItems.size.toFloat()
            if (totalCount > 0) {
                val colorStats = filteredItems.groupBy {
                    ColorUtil.hueToColor(it.markerHue ?: DEFAULT_MARKER_HUE)
                }.mapValues { it.value.size }

                val sliceList = colorStats.map { (color, count) ->
                    Slice(count / totalCount, color, label = count.toString())
                }
                binding.pieChart.slices = sliceList
            } else {
                binding.pieChart.slices = emptyList()
            }

            val boundsBuilder = LatLngBounds.Builder()
            var markersCount = 0

            clusters.forEach { items ->
                val marker = getMarker(items, googleMap)
                if (marker != null) {
                    marker.tag = items
                    items.forEach { item ->
                        // Store marker for this specific item location
                        val key = "${item.groupId}|${item.latitude}|${item.longitude}"
                        markerMap[key] = marker

                        // Store as default for groupId
                        val idKey = item.groupId.toString()
                        if (!markerMap.containsKey(idKey)) {
                            markerMap[idKey] = marker
                        }
                        // Favor the main location for the default groupId marker
                        val mainGroup = allGroups.find { it.id == item.groupId }
                        if (mainGroup != null &&
                            mainGroup.latitude == item.latitude &&
                            mainGroup.longitude == item.longitude
                        ) {
                            markerMap[idKey] = marker
                        }
                    }
                    boundsBuilder.include(marker.position)
                    markersCount++
                }
            }

            if (adjustCamera && markersCount > 0) {
                val bounds = boundsBuilder.build()
                googleMap.setMaxZoomPreference(MAX_CAMERA_ZOOM)
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, ZOOM_PADDING),
                    object : GoogleMap.CancelableCallback {
                        override fun onFinish() {
                            googleMap.resetMinMaxZoomPreference()
                        }

                        override fun onCancel() {
                            googleMap.resetMinMaxZoomPreference()
                        }
                    }
                )
            }
        }
    }

    fun clusterMarkerables(items: List<Markerable>): Collection<List<Markerable>> {
        val n = items.size
        val parent = IntArray(n) { it }

        fun find(i: Int): Int {
            var curr = i
            while (parent[curr] != curr) {
                parent[curr] = parent[parent[curr]] // Path halving
                curr = parent[curr]
            }
            return curr
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) parent[rootI] = rootJ
        }

        // O(N^2) comparisons, but with optimized distance check
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (items[i].isSameLocationAs(items[j])) {
                    union(i, j)
                }
            }
        }

        return items.indices.groupBy { find(it) }.values.map { indices ->
            indices.map { items[it] }
        }
    }

    private fun getMarker(items: List<Markerable>, googleMap: GoogleMap): Marker? {
        val representative = items.first()
        val position = LatLng(representative.latitude, representative.longitude)
        val markerTitle = if (items.size == 1) items[0].title else "${items.size} Memories"

        return if (items.size > 1) {
            val colors =
                items.map { ColorUtil.hueToColor(it.markerHue ?: DEFAULT_MARKER_HUE) }
                    .sorted()
            val density = resources.displayMetrics.density
            val bitmap = MultiColorMarkerGenerator.generateTapered(colors, items.size, density)

            googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(markerTitle)
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    .anchor(MARKER_ANCHOR_U, MARKER_ANCHOR_V)
            )
        } else {
            googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(markerTitle)
                    .icon(
                        BitmapDescriptorFactory.defaultMarker(
                            ColorUtil.normalizeHue(representative.markerHue ?: DEFAULT_MARKER_HUE)
                        )
                    )
            )
        }
    }

    private fun moveToLocationAndSelectMarker(lat: Double, lng: Double, memory: MemoryGroup?) {
        val googleMap = mMap ?: return
        val position = LatLng(lat, lng)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, MAX_CAMERA_ZOOM))

        val key = "${memory?.id}|$lat|$lng"
        val marker = markerMap[key] ?: markerMap[memory?.id.toString()]

        if (marker != null) {
            selectedMarker = marker
            selectedMarkerPosition = marker.position
            selectedMemoryId = memory?.id
            @Suppress("UNCHECKED_CAST")
            val items = marker.tag as? List<Markerable>
            if (items != null) {
                showMemoryOverlay(marker.position.latitude, marker.position.longitude, items)
            }
        }
    }

    private fun showMemoryOverlay(marker: Marker) {
        selectedMarker = marker
        selectedMarkerPosition = marker.position
        @Suppress("UNCHECKED_CAST")
        val items = marker.tag as? List<Markerable>
        if (items != null) {
            showMemoryOverlay(
                marker.position.latitude,
                marker.position.longitude,
                items
            )
        }
    }

    private fun showMemoryOverlay(lat: Double, lng: Double, items: List<Markerable>) {
        val distinctItems = items.distinctBy { it.groupId }
        val locationName = distinctItems.firstOrNull { it.placeName != null }?.placeName
            ?: "Lat: %.4f, Lng: %.4f".format(lat, lng)

        // Smoothly animate the card appearance, title cross-fade, and list height changes
        val transition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(Fade())
            addTransition(ChangeBounds())
            duration = ANIMATION_DURATION
        }

        TransitionManager.beginDelayedTransition(binding.root, transition)

        if (binding.overlayCard.isVisible && binding.overlayLocationTitle.text != locationName) {
            // Briefly toggling visibility triggers a smooth Fade cross-fade for the text change
            binding.overlayLocationTitle.visibility = View.INVISIBLE
            binding.overlayLocationTitle.text = locationName
            binding.overlayLocationTitle.visibility = View.VISIBLE
        } else {
            binding.overlayLocationTitle.text = locationName
        }

        overlayAdapter?.submitList(distinctItems) {
            val index = distinctItems.indexOfFirst { it.groupId == selectedMemoryId }
            if (index != -1) {
                binding.rvMemories.scrollToPosition(index)
            }
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
        private const val DEFAULT_ZOOM = 12f
        private const val ZOOM_PADDING = 100
        private const val ANIMATION_DURATION = 250L
        private const val GOOGLE_LOGO_HEIGHT = 25
        private const val MARKER_ANCHOR_U = 0.5f
        private const val MARKER_ANCHOR_V = 1.0f
    }
}
