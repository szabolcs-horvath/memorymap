package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import com.google.android.gms.maps.model.AdvancedMarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PinConfig
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.perf.metrics.Trace
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.MemoryOverlayAdapter
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryMapViewModel
import com.szabolcshorvath.memorymap.databinding.FragmentMapsBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.MultiColorMarkerGenerator
import com.szabolcshorvath.memorymap.util.PerfUtil
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PermissionUtil.checkPermission
import ir.mahozad.android.PieChart.Slice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private var mMap: GoogleMap? = null
    private var selectedMarker: Marker? = null
    private var selectedMemoryId: Int? = null
    private var selectedMarkerPosition: LatLng? = null
    private val markerMap = mutableMapOf<String, Marker>()
    private var pendingSelectionId: Int? = null
    private var pendingSelectionLat: Double? = null
    private var pendingSelectionLng: Double? = null
    private var listener: MapListener? = null
    private var overlayAdapter: MemoryOverlayAdapter? = null

    private val memoryMapViewModel: MemoryMapViewModel by activityViewModels()
    private var allGroups: List<MemoryGroup> = emptyList()

    private var permissionDenied = false
    private var isInitialZoomDone = false
    private var mapLoadTrace: Trace? = null

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapLoadTrace = PerfUtil.startTrace("map_full_load")

        binding.btnDateRange.setOnClickListener { showDateRangePicker() }
        binding.btnQuickFilter.setOnClickListener { showQuickFilterMenu() }
        binding.btnStats.setOnClickListener { toggleStatsOverlay() }

        binding.root.doOnLayout {
            setGoogleMapPadding()
        }

        setupOverlayRecyclerView()

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Observe Groups (needed for focusOnMemory lookup)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                memoryMapViewModel.allGroups.collect { groups ->
                    this@MapFragment.allGroups = groups
                }
            }
        }

        // Observe Filter State for UI updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    memoryMapViewModel.filterStartDate,
                    memoryMapViewModel.filterEndDate,
                    memoryMapViewModel.appliedFilterLabel
                ) { start, end, label ->
                    Triple(start, end, label)
                }.collect { (start, end, label) ->
                    updateDateRangeButtonText(start, end, label)
                }
            }
        }

        // Observe Filtered Data
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                memoryMapViewModel.filteredMarkerables.collectLatest { markerables ->
                    updateMapMarkers(markerables)
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

    private fun showQuickFilterMenu() {
        val binding = _binding ?: return
        val popup = PopupMenu(requireContext(), binding.btnQuickFilter)
        DateFilterOption.entries.forEach { option ->
            popup.menu.add(option.label)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val selectedOption = DateFilterOption.ofLabel(menuItem.title.toString())
            val (start, end) = selectedOption.dateRangeProvider(LocalDate.now())
            memoryMapViewModel.updateDateFilter(start, end, selectedOption.label)
            viewLifecycleOwner.lifecycleScope.launch {
                updateMapMarkers(adjustCamera = true)
            }
            true
        }
        popup.show()
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Select dates")

        val currentStart = memoryMapViewModel.filterStartDate.value
        val currentEnd = memoryMapViewModel.filterEndDate.value

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

            memoryMapViewModel.updateDateFilter(newStart, newEnd, null)
            viewLifecycleOwner.lifecycleScope.launch {
                updateMapMarkers(adjustCamera = true)
            }
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
        binding.statsOverlayCard.visibility = if (binding.statsOverlayCard.isVisible) View.GONE else View.VISIBLE
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
        val memory = allGroups.find { it.id == id } ?: return
        
        pendingSelectionId = id
        pendingSelectionLat = lat
        pendingSelectionLng = lng

        val oldStart = memoryMapViewModel.filterStartDate.value
        val oldEnd = memoryMapViewModel.filterEndDate.value
        
        updateDateFilterForMemory(memory.startDate.toLocalDate(), memory.endDate.toLocalDate())
        
        if (oldStart == memoryMapViewModel.filterStartDate.value && 
            oldEnd == memoryMapViewModel.filterEndDate.value) {
            // Filter didn't change, we can try to select immediately
            moveToLocationAndSelectMarker(lat, lng, memory)
            pendingSelectionId = null
        }
    }

    fun updateDateFilterForMemory(memoryStart: LocalDate, memoryEnd: LocalDate) {
        val currentStart = memoryMapViewModel.filterStartDate.value ?: memoryStart
        val currentEnd = memoryMapViewModel.filterEndDate.value ?: memoryEnd

        val newStart = if (memoryStart.isBefore(currentStart)) memoryStart else currentStart
        val newEnd = if (memoryEnd.isAfter(currentEnd)) memoryEnd else currentEnd

        memoryMapViewModel.updateDateFilter(newStart, newEnd)
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

        enableMyLocation()
        setGoogleMapPadding()

        googleMap.setOnMarkerClickListener { marker ->
            showMemoryOverlay(marker)
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
            selectedMemoryId = null
            selectedMarkerPosition = null
            setGoogleMapPadding()
        }
    }

    private fun setGoogleMapPadding() {
        val binding = _binding ?: return
        val googleMap = mMap ?: return
        val topPadding = binding.dateFilterContainer.height + binding.dateFilterContainer.top
        if (binding.overlayCard.isVisible) {
            googleMap.setPadding(0, topPadding, 0, binding.overlayCard.height + GOOGLE_LOGO_HEIGHT)
        } else {
            googleMap.setPadding(0, topPadding, 0, 0)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun enableMyLocation() {
        val googleMap = mMap ?: return
        if (hasLocationPermission()) {
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
        if (hasLocationPermission() && !isInitialZoomDone) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val googleMap = mMap
                if (location != null && googleMap != null && !isInitialZoomDone) {
                    isInitialZoomDone = true
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
                }
            }
        }
    }

    private fun showMemoryOverlay(marker: Marker) {
        selectedMarker = marker
        selectedMarkerPosition = marker.position
        @Suppress("UNCHECKED_CAST")
        val items = marker.tag as? List<Markerable>
        if (items != null) {
            selectedMemoryId = items.firstOrNull()?.groupId
            showMemoryOverlay(marker.position.latitude, marker.position.longitude, items)
            mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, MAX_CAMERA_ZOOM))
        }
    }

    private suspend fun updateMapMarkers(filteredItems: List<Markerable>? = null, adjustCamera: Boolean = false) {
        val googleMap = mMap ?: return
        trace("map_fragment_update_map_markers") {
            val items = filteredItems ?: memoryMapViewModel.filteredMarkerables.value

            val clusters = withContext(Dispatchers.Default) {
                clusterMarkerables(items)
            }

            withContext(Dispatchers.Main) {
                updateUIWithFreshMarkers(googleMap, items, clusters, adjustCamera)
            }
        }
    }

    private fun updateUIWithFreshMarkers(googleMap: GoogleMap, filteredItems: List<Markerable>, clusters: Collection<List<Markerable>>, adjustCamera: Boolean) {
        val binding = _binding ?: return
        trace("map_fragment_update_ui_with_fresh_markers") {
            googleMap.clear()
            markerMap.clear()

            // Update stats
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
            pendingSelectionId?.let { pId ->
                if (filteredItems.any { it.groupId == pId }) {
                    val pLat = pendingSelectionLat ?: 0.0
                    val pLng = pendingSelectionLng ?: 0.0
                    allGroups.find { it.id == pId }?.let { memory ->
                        val key = "${memory.id}|$pLat|$pLng"
                        val marker = markerMap[key] ?: markerMap[pId.toString()]
                        if (marker != null) {
                            moveToLocationAndSelectMarker(pLat, pLng, memory)
                        }
                    }
                    pendingSelectionId = null
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
        trace("map_fragment_cluster_markerables") {
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
    }

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
            val color = ColorUtil.hsvToColor(
                representative.markerHue ?: DEFAULT_MARKER_HUE,
                representative.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                representative.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
            )
            val contrastColor = ColorUtil.generateColorWithTargetContrast(color, TARGET_CONTRAST_FOR_MARKER_COLORS)

            googleMap.addMarker(
                AdvancedMarkerOptions()
                    .position(position)
                    .title(markerTitle)
                    .icon(
                        BitmapDescriptorFactory.fromPinConfig(
                            PinConfig.builder()
                                .setBackgroundColor(color)
                                .setGlyph(PinConfig.Glyph(contrastColor))
                                .setBorderColor(contrastColor)
                                .build()
                        )
                    )
            )
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
            selectedMarkerPosition = marker.position
            selectedMemoryId = memory.id
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
        mapLoadTrace = null
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
        private const val TARGET_CONTRAST_FOR_MARKER_COLORS = 2.0
    }
}
