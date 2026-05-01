package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.Marker
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.perf.metrics.AddTrace
import com.google.firebase.perf.metrics.Trace
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.MemoryOverlayAdapter
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.databinding.FragmentMapsBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.MarkerGenerator
import com.szabolcshorvath.memorymap.util.PerfUtil
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PermissionUtil.checkPermission
import ir.mahozad.android.PieChart.Slice
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private var mMap: GoogleMap? = null
    private var selectedMarker: Marker? = null
    private val markerMap = mutableMapOf<String, Marker>()
    private var mapListener: MapListener? = null
    private var overlayAdapter: MemoryOverlayAdapter? = null

    private val commonViewModel: CommonViewModel by activityViewModels()
    private val viewModel: MapFragmentViewModel by viewModels()
    private var allGroups: List<MemoryGroup> = emptyList()

    private var permissionDenied = false
    private var shouldAdjustCameraOnUpdate = false
    private var mapLoadTrace: Trace? = null

    interface MapListener {
        fun onMemoryClicked(id: Int)
    }

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MapListener) {
            mapListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            enableMyLocation()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapLoadTrace = PerfUtil.startTrace("map_full_load")

        binding.btnDateRange.setOnClickListener { showDateRangePicker() }
        binding.btnQuickFilter.setOnClickListener { showQuickFilterMenu() }
        binding.btnStats.setOnClickListener { toggleStatsOverlay() }

        if (viewModel.isStatsVisible) {
            binding.statsOverlayCard.visibility = View.VISIBLE
        }

        binding.root.doOnLayout {
            setGoogleMapPadding()
        }

        setupOverlayRecyclerView()

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Observe Groups (needed for focusOnMemory lookup)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                commonViewModel.allGroups.collect { groups ->
                    this@MapFragment.allGroups = groups
                }
            }
        }

        // Observe Filter State for UI updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.filterStartDate,
                    viewModel.filterEndDate,
                    viewModel.appliedFilterLabel
                ) { start, end, label ->
                    Triple(start, end, label)
                }.collect { (start, end, label) ->
                    updateDateRangeButtonText(start, end, label)
                }
            }
        }

        // Observe Filtered Data (Clusters)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.markerClusters.collectLatest { clusters ->
                    updateMapMarkers(clusters, adjustCamera = shouldAdjustCameraOnUpdate)
                    shouldAdjustCameraOnUpdate = false
                }
            }
        }
    }

    private fun setupOverlayRecyclerView() {
        overlayAdapter = MemoryOverlayAdapter { memoryId ->
            mapListener?.onMemoryClicked(memoryId)
        }
        binding.rvMemories.apply {
            adapter = overlayAdapter
            itemAnimator = null // Disable cross-fade to eliminate "flickering" between markers
        }
    }

    private fun showQuickFilterMenu() {
        val binding = _binding ?: return
        val popup = PopupMenu(requireContext(), binding.btnQuickFilter)
        DateFilterOption.entries.forEach { option ->
            popup.menu.add(option.label)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val selectedOption = DateFilterOption.ofLabel(menuItem.title.toString())
            val (start, end) = selectedOption.dateRangeProvider(LocalDate.now())
            shouldAdjustCameraOnUpdate = true
            viewModel.updateDateFilter(start, end, selectedOption.label)
            true
        }
        popup.show()
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Select dates")

        val currentStart = viewModel.filterStartDate.value
        val currentEnd = viewModel.filterEndDate.value

        if (currentStart != null && currentEnd != null) {
            val startMillis = currentStart.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            val endMillis = currentEnd.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            builder.setSelection(androidx.core.util.Pair(startMillis, endMillis))
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val startMillis = selection.first
            val endMillis = selection.second

            val newStart = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            val newEnd = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()

            shouldAdjustCameraOnUpdate = true
            viewModel.updateDateFilter(newStart, newEnd)
        }
        picker.show(childFragmentManager, picker.toString())
    }

    private fun toggleStatsOverlay() {
        val binding = _binding ?: return
        val transition = TransitionSet().apply {
            addTransition(Fade())
            addTransition(ChangeBounds())
            duration = ANIMATION_DURATION
        }
        TransitionManager.beginDelayedTransition(binding.root, transition)
        viewModel.isStatsVisible = !viewModel.isStatsVisible
        binding.statsOverlayCard.visibility = if (viewModel.isStatsVisible) View.VISIBLE else View.GONE
    }

    private fun updateDateRangeButtonText(start: LocalDate?, end: LocalDate?, label: String?) {
        val binding = _binding ?: return
        if (label != null) {
            binding.btnDateRange.text = label
            return
        }

        val dateFormatter = dateFormatter()
        if (start != null && end != null) {
            if (start != end) {
                binding.btnDateRange.text = "${dateFormatter.format(start)} - ${dateFormatter.format(end)}"
            } else {
                binding.btnDateRange.text = "${dateFormatter.format(start)}"
            }
        } else {
            binding.btnDateRange.text = DateFilterOption.DEFAULT_DATE_FILTER_OPTION.label
        }
    }

    fun focusOnMemory(lat: Double, lng: Double, id: Int) {
        lifecycleScope.launch {
            // Wait for groups to be loaded if they are empty
            val groups = allGroups.ifEmpty {
                commonViewModel.allGroups.first { it.isNotEmpty() }
            }
            val memory = groups.find { it.id == id } ?: return@launch

            // Wait for filter to be loaded from DataStore
            viewModel.isDateFilterLoaded.first { it }

            viewModel.pendingSelectionId = id
            viewModel.pendingSelectionLat = lat
            viewModel.pendingSelectionLng = lng

            val oldStart = viewModel.filterStartDate.value
            val oldEnd = viewModel.filterEndDate.value

            updateDateFilterForMemory(memory.startDate.toLocalDate(), memory.endDate.toLocalDate())

            if (oldStart == viewModel.filterStartDate.value && oldEnd == viewModel.filterEndDate.value) {
                // Filter didn't change, we can try to select immediately
                moveToLocationAndSelectMarker(lat, lng, memory)
                viewModel.pendingSelectionId = null
            }
        }
    }

    private fun updateDateFilterForMemory(memoryStart: LocalDate, memoryEnd: LocalDate) {
        val currentStart = viewModel.filterStartDate.value
        val currentEnd = viewModel.filterEndDate.value

        // If current is null, it means ALL_TIME (unbounded), so memory is already within range.
        // If current is NOT null, and memory is outside, we expand to include it.
        val newStart = if (currentStart != null && memoryStart.isBefore(currentStart)) memoryStart else currentStart
        val newEnd = if (currentEnd != null && memoryEnd.isAfter(currentEnd)) memoryEnd else currentEnd

        if (newStart != currentStart || newEnd != currentEnd) {
            viewModel.updateDateFilter(newStart, newEnd)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        googleMap.setOnMapLoadedCallback {
            mapLoadTrace?.stop()
            mapLoadTrace = null
        }

        googleMap.mapColorScheme = MapColorScheme.FOLLOW_SYSTEM
        googleMap.uiSettings.isRotateGesturesEnabled = false
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.uiSettings.isZoomControlsEnabled = true

        viewModel.lastCameraPosition?.let { position ->
            viewModel.isInitialZoomDone = true
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(position))
        }

        enableMyLocation()
        setGoogleMapPadding()

        googleMap.setOnCameraIdleListener {
            viewModel.lastCameraPosition = googleMap.cameraPosition
        }

        googleMap.setOnMarkerClickListener { marker ->
            showMemoryOverlay(marker)
            true
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

        viewLifecycleOwner.lifecycleScope.launch {
            updateMapMarkers()
        }
    }

    private fun hideMemoryOverlay() {
        val binding = _binding ?: return
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
            viewModel.selectedMemoryId = null
            viewModel.selectedMarkerPosition = null
            setGoogleMapPadding()
        }
    }

    private fun setGoogleMapPadding() {
        val binding = _binding ?: return
        val googleMap = mMap ?: return

        val density = resources.displayMetrics.density
        val controlMarginThreshold = MAP_CONTROLS_MARGIN_DP * density // Margin required to avoid overlapping corner controls

        val rootWidth = binding.root.width
        if (rootWidth == 0) return

        val topPadding = if (binding.dateFilterContainer.height > 0) {
            val containerWidth = binding.dateFilterContainer.width
            val sideMargin = (rootWidth - containerWidth) / 2f
            if (sideMargin < controlMarginThreshold) {
                binding.dateFilterContainer.height + binding.dateFilterContainer.top
            } else {
                0
            }
        } else {
            0
        }

        val bottomPadding = if (binding.overlayCard.isVisible) {
            val cardWidth = binding.overlayCard.width
            val sideMargin = (rootWidth - cardWidth) / 2f
            if (sideMargin < controlMarginThreshold) {
                binding.overlayCard.height + GOOGLE_LOGO_HEIGHT_DP
            } else {
                0
            }
        } else {
            0
        }

        googleMap.setPadding(0, topPadding, 0, bottomPadding)
    }

    @SuppressWarnings("MissingPermission")
    private fun enableMyLocation() {
        val googleMap = mMap ?: return
        if (hasLocationPermission()) {
            googleMap.isMyLocationEnabled = true
            permissionDenied = false
            zoomToUserLocationIfPossible()
        } else if (!permissionDenied) {
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

    @SuppressWarnings("MissingPermission")
    private fun zoomToUserLocationIfPossible() {
        if (hasLocationPermission() && !viewModel.isInitialZoomDone) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val googleMap = mMap
                if (location != null && googleMap != null && !viewModel.isInitialZoomDone) {
                    viewModel.isInitialZoomDone = true
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
                }
            }
        }
    }

    private fun showMemoryOverlay(marker: Marker) {
        selectedMarker = marker
        viewModel.selectedMarkerPosition = marker.position
        @Suppress("UNCHECKED_CAST")
        val items = marker.tag as? List<Markerable>
        if (items != null) {
            viewModel.selectedMemoryId = items.firstOrNull()?.groupId
            showMemoryOverlay(marker.position.latitude, marker.position.longitude, items)
            mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, MAX_CAMERA_ZOOM))
        }
    }

    private fun updateMapMarkers(clusters: Collection<List<Markerable>>? = null, adjustCamera: Boolean = false) {
        val googleMap = mMap ?: return
        trace("map_fragment_update_map_markers") {
            val clustersToUse = clusters ?: viewModel.markerClusters.value
            val items = clustersToUse.flatten()

            updateUIWithFreshMarkers(googleMap, items, clustersToUse, adjustCamera)
        }
    }

    private fun updateUIWithFreshMarkers(googleMap: GoogleMap, filteredItems: List<Markerable>, clusters: Collection<List<Markerable>>, adjustCamera: Boolean) {
        trace("map_fragment_update_ui_with_fresh_markers") {
            googleMap.clear()
            markerMap.clear()
            selectedMarker = null

            updatePieChart(filteredItems)

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
                        if (mainGroup != null && mainGroup.latitude == item.latitude && mainGroup.longitude == item.longitude) {
                            markerMap[idKey] = marker
                        }
                    }
                    boundsBuilder.include(marker.position)
                    markersCount++
                }
            }

            // Handle pending selection if any
            viewModel.pendingSelectionId?.let { pId ->
                if (filteredItems.any { it.groupId == pId }) {
                    val pLat = viewModel.pendingSelectionLat ?: 0.0
                    val pLng = viewModel.pendingSelectionLng ?: 0.0
                    allGroups.find { it.id == pId }?.let { memory ->
                        val key = "${memory.id}|$pLat|$pLng"
                        val marker = markerMap[key] ?: markerMap[pId.toString()]
                        if (marker != null) {
                            moveToLocationAndSelectMarker(pLat, pLng, memory)
                        }
                    }
                    viewModel.pendingSelectionId = null
                }
            }

            // Handle existing selection after rotation
            if (selectedMarker == null && viewModel.selectedMemoryId != null) {
                val sId = viewModel.selectedMemoryId!!
                val sPos = viewModel.selectedMarkerPosition
                val restoredMarker = allGroups.find { it.id == sId }?.let { memory ->
                    val lat = sPos?.latitude ?: memory.latitude
                    val lng = sPos?.longitude ?: memory.longitude
                    val key = "${memory.id}|$lat|$lng"
                    markerMap[key] ?: markerMap[sId.toString()]
                }

                if (restoredMarker != null) {
                    selectedMarker = restoredMarker
                    @Suppress("UNCHECKED_CAST")
                    val items = restoredMarker.tag as? List<Markerable>
                    if (items != null) {
                        showMemoryOverlay(restoredMarker.position.latitude, restoredMarker.position.longitude, items)
                    }
                } else {
                    hideMemoryOverlay()
                }
            } else if (selectedMarker == null) {
                hideMemoryOverlay()
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

    @AddTrace(name = "map_fragment_update_pie_chart", enabled = true)
    private fun updatePieChart(filteredItems: List<Markerable>) {
        val binding = _binding ?: return
        val totalCount = filteredItems.size.toFloat()
        if (totalCount > 0) {
            val colorStats = filteredItems.groupBy {
                ColorUtil.hsvToColor(
                    it.markerHue ?: DEFAULT_MARKER_HUE,
                    it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                    it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                )
            }.mapValues { it.value.size }

            val sliceList = colorStats.map { (color, count) ->
                Slice(count / totalCount, color, label = count.toString())
            }
            binding.pieChart.slices = sliceList
        } else {
            binding.pieChart.slices = emptyList()
        }
    }

    @AddTrace(name = "map_fragment_get_marker", enabled = true)
    private fun getMarker(items: List<Markerable>, googleMap: GoogleMap): Marker? {
        val representative = items.first()
        val position = LatLng(representative.latitude, representative.longitude)
        val markerTitle = if (items.size == 1) items[0].title else "${items.size} Memories"

        return if (items.size > 1) {
            val colors = items.map {
                ColorUtil.hsvToColor(
                    it.markerHue ?: DEFAULT_MARKER_HUE,
                    it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                    it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                )
            }.sorted()

            googleMap.addMarker(MarkerGenerator.advancedMarkerOptions(position, markerTitle, colors, items.size, resources.displayMetrics.density))
        } else {
            val color = ColorUtil.hsvToColor(
                representative.markerHue ?: DEFAULT_MARKER_HUE,
                representative.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                representative.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
            )

            googleMap.addMarker(MarkerGenerator.advancedMarkerOptions(position, markerTitle, color))
        }
    }

    private fun moveToLocationAndSelectMarker(lat: Double, lng: Double, memory: MemoryGroup) {
        val googleMap = mMap ?: return
        val position = LatLng(lat, lng)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, MAX_CAMERA_ZOOM))

        val key = "${memory.id}|$lat|$lng"
        val marker = markerMap[key] ?: markerMap[memory.id.toString()]

        if (marker != null) {
            selectedMarker = marker
            viewModel.selectedMarkerPosition = marker.position
            viewModel.selectedMemoryId = memory.id
            @Suppress("UNCHECKED_CAST")
            val items = marker.tag as? List<Markerable>
            if (items != null) {
                showMemoryOverlay(marker.position.latitude, marker.position.longitude, items)
            }
        }
    }

    private fun showMemoryOverlay(lat: Double, lng: Double, items: List<Markerable>) {
        val binding = _binding ?: return
        val distinctItems = items.distinctBy { it.groupId }
        val locationName = distinctItems.firstOrNull {
            it.placeName != null
        }?.placeName ?: "Lat: %.4f, Lng: %.4f".format(lat, lng)

        if (binding.overlayCard.isVisible && binding.overlayLocationTitle.text != locationName) {
            // Smoothly animate the title cross-fade if the card is already visible
            val titleTransition = TransitionSet().apply {
                addTransition(Fade())
                duration = ANIMATION_DURATION
            }
            TransitionManager.beginDelayedTransition(binding.overlayCard, titleTransition)
            binding.overlayLocationTitle.visibility = View.INVISIBLE
            binding.overlayLocationTitle.text = locationName
            binding.overlayLocationTitle.visibility = View.VISIBLE
        } else {
            binding.overlayLocationTitle.text = locationName
        }

        overlayAdapter?.submitList(distinctItems) {
            val transition = TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(Fade())
                addTransition(ChangeBounds())
                duration = ANIMATION_DURATION
            }
            TransitionManager.beginDelayedTransition(binding.root, transition)

            val index = distinctItems.indexOfFirst { it.groupId == viewModel.selectedMemoryId }
            if (index != -1) {
                binding.rvMemories.scrollToPosition(index)
            }
            binding.overlayCard.visibility = View.VISIBLE
            setGoogleMapPadding()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapLoadTrace = null
        _binding = null
    }

    companion object {
        const val TAG = "MapFragment"
        private const val MAX_CAMERA_ZOOM = 15f
        private const val DEFAULT_ZOOM = 12f
        private const val ZOOM_PADDING = 100
        private const val ANIMATION_DURATION = 250L
        private const val MAP_CONTROLS_MARGIN_DP = 100
        private const val GOOGLE_LOGO_HEIGHT_DP = 25
    }
}
