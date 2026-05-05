package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.annotation.SuppressLint
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
import com.google.android.gms.maps.model.AdvancedMarker
import com.google.android.gms.maps.model.AdvancedMarkerOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.Marker
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.perf.metrics.AddTrace
import com.google.firebase.perf.metrics.Trace
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultAdvancedMarkersClusterRenderer
import com.google.maps.android.collections.MarkerManager
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
    private lateinit var clusterManager: ClusterManager<Markerable.MarkerableCluster>
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

        // Observe Cluster Markers Preference
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.markerClusteringEnabled.collect {
                    if (this@MapFragment::clusterManager.isInitialized) {
                        clusterManager.clearItems()
                        clusterManager.cluster()
                    }
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
            if (start != viewModel.filterStartDate.value || end != viewModel.filterEndDate.value) {
                shouldAdjustCameraOnUpdate = true
            }
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
        viewModel.isInitialZoomDone = true
        lifecycleScope.launch {
            // Wait for the first groups emission if the current snapshot is empty
            val groups = allGroups.ifEmpty {
                commonViewModel.allGroups.first()
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
                // Filter didn't change, we can try to select immediately if the map and clusters are ready
                val currentClusters = viewModel.markerClusters.value
                if (mMap != null && currentClusters.isNotEmpty()) {
                    moveToLocationAndSelectMarker(lat, lng, id, currentClusters)
                }
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

        val pLat = viewModel.pendingSelectionLat
        val pLng = viewModel.pendingSelectionLng
        if (viewModel.pendingSelectionId != null && pLat != null && pLng != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pLat, pLng), DEFAULT_ZOOM))
        } else {
            viewModel.lastCameraPosition?.let { position ->
                viewModel.isInitialZoomDone = true
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(position))
            }
        }

        enableMyLocation()
        setGoogleMapPadding()

        val markerManager = MarkerManager(googleMap)
        clusterManager = ClusterManager(requireContext(), googleMap, markerManager)
        clusterManager.renderer = MarkerableClusterRenderer(requireContext(), googleMap, clusterManager)

        clusterManager.setOnClusterItemClickListener { locationItem ->
            showMemoryOverlay(locationItem.position.latitude, locationItem.position.longitude, locationItem.items)
            true
        }

        clusterManager.setOnClusterClickListener { cluster ->
            hideMemoryOverlay()
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(cluster.position, googleMap.cameraPosition.zoom + 1f))
            true
        }

        @SuppressLint("PotentialBehaviorOverride")
        googleMap.setOnMarkerClickListener(markerManager)

        googleMap.setOnCameraIdleListener {
            clusterManager.onCameraIdle()
            viewModel.lastCameraPosition = googleMap.cameraPosition
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

    private fun updateMapMarkers(clusters: List<Markerable.MarkerableCluster>? = null, adjustCamera: Boolean = false) {
        val googleMap = mMap ?: return
        trace("map_fragment_update_map_markers") {
            val clustersToUse = clusters ?: viewModel.markerClusters.value
            val items = clustersToUse.flatMap { it.items }

            updateUIWithFreshMarkers(googleMap, items, clustersToUse, adjustCamera)
        }
    }

    private fun updateUIWithFreshMarkers(
        googleMap: GoogleMap,
        filteredItems: List<Markerable>,
        clusters: List<Markerable.MarkerableCluster>,
        adjustCamera: Boolean
    ) {
        trace("map_fragment_update_ui_with_fresh_markers") {
            // 1. Clear ClusterManager instead of the whole map
            clusterManager.clearItems()

            updatePieChart(filteredItems)

            // 2. Efficiently add new clusters and trigger the redraw
            clusterManager.addItems(clusters)
            clusterManager.cluster()

            val hasPendingSelection = viewModel.pendingSelectionId != null
            viewModel.pendingSelectionId?.let { pId ->
                if (filteredItems.any { it.groupId == pId }) {
                    moveToLocationAndSelectMarker(viewModel.pendingSelectionLat, viewModel.pendingSelectionLng, pId, clusters)
                    viewModel.pendingSelectionId = null
                }
            }

            // Handle existing selection after rotation or data update
            if (viewModel.selectedMemoryId != null) {
                val restoredCluster = clusters.find { cluster ->
                    cluster.items.any { it.groupId == viewModel.selectedMemoryId }
                }

                if (restoredCluster != null) {
                    showMemoryOverlay(restoredCluster.position.latitude, restoredCluster.position.longitude, restoredCluster.items, false)
                } else {
                    hideMemoryOverlay()
                }
            } else {
                hideMemoryOverlay()
            }

            if (adjustCamera && !hasPendingSelection && clusters.isNotEmpty()) {
                val boundsBuilder = LatLngBounds.Builder()
                clusters.forEach { boundsBuilder.include(it.position) }
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
            }.mapValues { it.value.size }.toSortedMap()

            val sliceList = colorStats.map { (color, count) ->
                Slice(count / totalCount, color, label = count.toString())
            }
            binding.pieChart.slices = sliceList
        } else {
            binding.pieChart.slices = emptyList()
        }
    }

    private fun moveToLocationAndSelectMarker(lat: Double?, lng: Double?, groupId: Int, clusters: List<Markerable.MarkerableCluster>) {
        if (lat == null || lng == null) return
        val googleMap = mMap ?: return
        val position = LatLng(lat, lng)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, MAX_CAMERA_ZOOM))

        // Find the cluster that contains this memory in the provided clusters
        val cluster = clusters.find { c ->
            c.items.any { it.groupId == groupId }
        }

        if (cluster != null) {
            viewModel.selectedMemoryId = groupId
            showMemoryOverlay(cluster.position.latitude, cluster.position.longitude, cluster.items, false)
        }
    }

    private fun showMemoryOverlay(lat: Double, lng: Double, items: List<Markerable>, shouldAnimateCamera: Boolean = true) {
        val binding = _binding ?: return

        viewModel.selectedMarkerPosition = LatLng(lat, lng)
        if (viewModel.selectedMemoryId == null || items.none { it.groupId == viewModel.selectedMemoryId }) {
            viewModel.selectedMemoryId = items.firstOrNull()?.groupId
        }

        if (shouldAnimateCamera) {
            mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), MAX_CAMERA_ZOOM))
        }

        val distinctItems = items.distinctBy { it.groupId }
        val locationName = distinctItems.firstOrNull {
            it.placeName != null
        }?.placeName ?: "Lat: %.4f, Lng: %.4f".format(lat, lng)

        if (binding.overlayCard.isVisible && binding.overlayLocationTitle.text != locationName) {
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
        mMap = null
        _binding = null
    }

    private inner class MarkerableClusterRenderer(
        context: Context,
        map: GoogleMap,
        clusterManager: ClusterManager<Markerable.MarkerableCluster>
    ) : DefaultAdvancedMarkersClusterRenderer<Markerable.MarkerableCluster>(context, map, clusterManager) {

        override fun shouldRenderAsCluster(cluster: Cluster<Markerable.MarkerableCluster>): Boolean =
            if (viewModel.markerClusteringEnabled.value) super.shouldRenderAsCluster(cluster) else false

        override fun onBeforeClusterItemRendered(
            cluster: Markerable.MarkerableCluster,
            advancedMarkerOptions: AdvancedMarkerOptions
        ) {
            val representative = cluster.items.first()
            if (cluster.items.size > 1) {
                val colors = cluster.items.map {
                    ColorUtil.hsvToColor(
                        it.markerHue ?: DEFAULT_MARKER_HUE,
                        it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                        it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                    )
                }.sorted()
                advancedMarkerOptions.icon(MarkerGenerator.multiColorBitmapPinIcon(colors, cluster.items.size, resources.displayMetrics.density))
                advancedMarkerOptions.anchor(MarkerGenerator.PIN_ANCHOR_U, MarkerGenerator.PIN_ANCHOR_V)
            } else {
                val color = ColorUtil.hsvToColor(
                    representative.markerHue ?: DEFAULT_MARKER_HUE,
                    representative.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                    representative.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                )
                advancedMarkerOptions.icon(MarkerGenerator.singleColorPinConfigIcon(color))
                advancedMarkerOptions.anchor(MarkerGenerator.PIN_ANCHOR_U, MarkerGenerator.PIN_ANCHOR_V)
            }
            advancedMarkerOptions.title(cluster.getTitle())
        }

        override fun onClusterItemUpdated(
            cluster: Markerable.MarkerableCluster,
            marker: Marker
        ) {
            val representative = cluster.items.first()
            if (cluster.items.size > 1) {
                val colors = cluster.items.map {
                    ColorUtil.hsvToColor(
                        it.markerHue ?: DEFAULT_MARKER_HUE,
                        it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                        it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                    )
                }.sorted()
                marker.setIcon(MarkerGenerator.multiColorBitmapPinIcon(colors, cluster.items.size, resources.displayMetrics.density))
                marker.setAnchor(MarkerGenerator.PIN_ANCHOR_U, MarkerGenerator.PIN_ANCHOR_V)
            } else {
                val color = ColorUtil.hsvToColor(
                    representative.markerHue ?: DEFAULT_MARKER_HUE,
                    representative.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                    representative.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                )
                marker.setIcon(MarkerGenerator.singleColorPinConfigIcon(color))
                marker.setAnchor(MarkerGenerator.PIN_ANCHOR_U, MarkerGenerator.PIN_ANCHOR_V)
            }
            marker.title = cluster.getTitle()
        }

        override fun onBeforeClusterRendered(
            cluster: Cluster<Markerable.MarkerableCluster>,
            advancedMarkerOptions: AdvancedMarkerOptions
        ) {
            val allItems = cluster.items.flatMap { it.items }
            val colors = allItems.map {
                ColorUtil.hsvToColor(
                    it.markerHue ?: DEFAULT_MARKER_HUE,
                    it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                    it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                )
            }.sorted()

            advancedMarkerOptions.icon(MarkerGenerator.multiColorBitmapCircleIcon(colors, allItems.size, resources.displayMetrics.density))
            advancedMarkerOptions.anchor(MarkerGenerator.CIRCLE_ANCHOR_U, MarkerGenerator.CIRCLE_ANCHOR_V)
        }

        override fun onClusterUpdated(
            cluster: Cluster<Markerable.MarkerableCluster?>,
            marker: AdvancedMarker
        ) {
            val allItems = cluster.items.flatMap { it?.items ?: emptyList() }
            val colors = allItems.map {
                ColorUtil.hsvToColor(
                    it.markerHue ?: DEFAULT_MARKER_HUE,
                    it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                    it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                )
            }.sorted()

            marker.setIcon(MarkerGenerator.multiColorBitmapCircleIcon(colors, allItems.size, resources.displayMetrics.density))
            marker.setAnchor(MarkerGenerator.CIRCLE_ANCHOR_U, MarkerGenerator.CIRCLE_ANCHOR_V)
        }
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
