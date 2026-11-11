package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.perf.metrics.Trace
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.ui.map.MAX_FIT_ZOOM
import com.szabolcshorvath.memorymap.ui.map.MY_LOCATION_ZOOM
import com.szabolcshorvath.memorymap.ui.map.MapActions
import com.szabolcshorvath.memorymap.ui.map.MapContent
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.MyLocationAction
import com.szabolcshorvath.memorymap.util.PerfUtil
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PermissionUtil.hasLocationPermission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

class MapFragment : Fragment(), MapActions {

    private var mapListener: MapListener? = null

    private val commonViewModel: CommonViewModel by activityViewModels { CommonViewModel.Factory }
    private val viewModel: MapFragmentViewModel by viewModels { MapFragmentViewModel.Factory }

    private val clustersFlow by lazy {
        viewModel.getMarkerClusters(commonViewModel.showFragmentsEnabled)
    }

    private var shouldAdjustCameraOnUpdate = false
    private var mapLoadTrace: Trace? = null

    interface MapListener {
        fun onMemoryClicked(id: Int)
    }

    // The only place the map asks for location access: when the user taps the "my location" button.
    private val myLocationAction = MyLocationAction(this) { latLng ->
        refreshMyLocationDot()
        viewModel.isInitialZoomDone = true
        viewModel.moveCamera(MapCameraRequest.MyLocation(latLng))
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MapListener) {
            mapListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose saves the map's camera and an open date picker under this id.
            id = R.id.map_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState(commonViewModel.showFragmentsEnabled, commonViewModel.markerClusteringEnabled)
                    .collectAsStateWithLifecycle()
                MemoryMapTheme { MapContent(state, this@MapFragment) }
            }
        }

    override fun onResume() {
        super.onResume()
        refreshMyLocationDot()
        zoomToUserLocationIfPossible()
        focusOnPendingMemory()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewLifecycleOwner = viewLifecycleOwner
        mapLoadTrace = PerfUtil.startTrace("map_full_load")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                clustersFlow.collect { pins ->
                    onPinsChanged(pins, adjustCamera = shouldAdjustCameraOnUpdate)
                    shouldAdjustCameraOnUpdate = false
                }
            }
        }
    }

    override fun pickQuickFilter(option: DateFilterOption) {
        val (start, end) = option.dateRange(LocalDate.now())
        if (start != viewModel.filterStartDate.value || end != viewModel.filterEndDate.value) {
            shouldAdjustCameraOnUpdate = true
        }
        viewModel.updateDateFilter(start, end, option)
    }

    override fun pickDates(start: LocalDate, end: LocalDate) {
        shouldAdjustCameraOnUpdate = true
        viewModel.updateDateFilter(start, end)
    }

    override fun toggleStats() = viewModel.toggleStats()

    override fun goToMyLocation() {
        viewModel.clearSelection()
        myLocationAction.start()
    }

    override fun tapPin(pin: Markerable.MarkerableCluster) {
        viewModel.select(pin.position, pin.items)
        viewModel.moveCamera(MapCameraRequest.Show(pin.position, MAX_FIT_ZOOM))
    }

    override fun tapCluster(position: LatLng) {
        viewModel.clearSelection()
        viewModel.moveCamera(MapCameraRequest.ZoomIn(position))
    }

    override fun tapMap() {
        viewModel.clearSelection()
        viewModel.hideStats()
    }

    /**
     * The Map tab tapped while the map shows. It closes what is open over the map (the panel, the statistics) and
     * leaves the camera where it is; with nothing open, it goes back to the map's home view: the area around the phone
     * with location access, otherwise all the memories shown, as when the map opens.
     */
    fun onTabTappedAgain() {
        if (!viewModel.closeOverlays()) showHomeView()
    }

    private fun showHomeView() {
        val context = context ?: return
        if (!hasLocationPermission(context)) return fitAll(clustersFlow.value)
        AppServices.location.lastLocation(context)
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.moveCamera(MapCameraRequest.Show(LatLng(location.latitude, location.longitude), MY_LOCATION_ZOOM))
                } else {
                    fitAll(clustersFlow.value)
                }
            }
            .addOnFailureListener { fitAll(clustersFlow.value) }
    }

    override fun openMemory(id: Int) {
        mapListener?.onMemoryClicked(id)
    }

    override fun onMapLoaded() {
        mapLoadTrace?.stop()
        mapLoadTrace = null
    }

    override fun onCameraMoved(request: MapCameraRequest) = viewModel.onCameraMoved(request)

    /**
     * Shows memory [id] on the map. The request is kept in the ViewModel and handled once this tab is shown (right away
     * if it is), so it can be made while the tab is hidden, with or without a view, or still being shown.
     */
    fun requestFocusOnMemory(lat: Double, lng: Double, id: Int) {
        viewModel.isInitialZoomDone = true
        viewModel.pendingFocus = MapFragmentViewModel.FocusRequest(id, lat, lng)
        if (isResumed) focusOnPendingMemory()
    }

    /** Handles the pending focus request, if any. It stays pending until handled, so a view destroyed meanwhile retries it. */
    private fun focusOnPendingMemory() {
        val request = viewModel.pendingFocus ?: return
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            // Wait until groups are loaded and the requested memory is present, then for the filter from DataStore
            val groups = withTimeoutOrNull(DATA_LOAD_TIMEOUT_MS.milliseconds) {
                commonViewModel.allGroups.first { list -> list.any { it.id == request.id } }
            }
            val isFilterLoaded = groups != null && withTimeoutOrNull(DATA_LOAD_TIMEOUT_MS.milliseconds) {
                viewModel.isDateFilterLoaded.first { it }
            } != null

            if (viewModel.pendingFocus != request) return@launch // A newer request replaced this one.
            viewModel.pendingFocus = null
            val memory = groups?.find { it.id == request.id }
            if (memory == null || !isFilterLoaded) return@launch

            viewModel.pendingSelectionId = request.id
            viewModel.pendingSelectionLat = request.lat
            viewModel.pendingSelectionLng = request.lng
            // Goes there now: the pin may take a moment to come (a widened filter), and is then selected.
            viewModel.moveCamera(MapCameraRequest.Show(LatLng(request.lat, request.lng), MAX_FIT_ZOOM))

            val oldStart = viewModel.filterStartDate.value
            val oldEnd = viewModel.filterEndDate.value

            updateDateFilterForMemory(memory.startDate.toLocalDate(), memory.endDate.toLocalDate())

            if (oldStart == viewModel.filterStartDate.value && oldEnd == viewModel.filterEndDate.value) {
                // The filter didn't change, so the pins already there are the ones to select from.
                val clusters = clustersFlow.value
                if (clusters.isNotEmpty()) {
                    selectPendingMemory(clusters)
                }
            }
        }
    }

    private fun updateDateFilterForMemory(memoryStart: LocalDate, memoryEnd: LocalDate) {
        val currentStart = viewModel.filterStartDate.value
        val currentEnd = viewModel.filterEndDate.value

        // A memory that overlaps the range is already shown. Otherwise widen the range to include all of it.
        if (MapFragmentViewModel.overlapsDateFilter(memoryStart, memoryEnd, currentStart, currentEnd)) return
        val newStart = if (currentStart != null && memoryStart.isBefore(currentStart)) memoryStart else currentStart
        val newEnd = if (currentEnd != null && memoryEnd.isAfter(currentEnd)) memoryEnd else currentEnd

        if (newStart != currentStart || newEnd != currentEnd) {
            viewModel.updateDateFilter(newStart, newEnd)
        }
    }

    /** Keeps the panel and the camera in step with new [pins] (a filter or data change). */
    private fun onPinsChanged(pins: List<Markerable.MarkerableCluster>, adjustCamera: Boolean) = trace("map_fragment_on_pins_changed") {
        val hasPendingSelection = viewModel.pendingSelectionId != null
        val pendingId = viewModel.pendingSelectionId
        if (pendingId != null && pins.any { pin -> pin.items.any { it.groupId == pendingId } }) {
            selectPendingMemory(pins)
        } else {
            reopenSelection(pins)
        }

        // A "show on map" request moves the camera itself.
        if (hasPendingSelection) return@trace
        if (adjustCamera) fitAll(pins) else fitAllOnFirstLook(pins)
    }

    /** The first time the map has pins and won't go to where the phone is, it shows them all. */
    private fun fitAllOnFirstLook(pins: List<Markerable.MarkerableCluster>) {
        val context = context
        val goesToPhone = context != null && hasLocationPermission(context) && !viewModel.isLocationUnknown
        if (!viewModel.isInitialZoomDone && !goesToPhone) fitAll(pins)
    }

    private fun fitAll(pins: List<Markerable.MarkerableCluster>) {
        if (pins.isEmpty()) return
        viewModel.isInitialZoomDone = true
        viewModel.moveCamera(MapCameraRequest.Fit(pins.map { it.position }))
    }

    /** Opens the panel again on the pin it was open for, now that the pins changed, or closes it if that pin is gone. */
    private fun reopenSelection(pins: List<Markerable.MarkerableCluster>) {
        val selectedId = viewModel.selectedMemoryId ?: return
        val pin = closestPinWith(selectedId, viewModel.selectedMarkerPosition, pins)
        if (pin != null) viewModel.select(pin.position, pin.items, selectedId) else viewModel.clearSelection()
    }

    /** Selects the memory a "show on map" request asked for, on its pin closest to where it was asked for. */
    private fun selectPendingMemory(pins: List<Markerable.MarkerableCluster>) {
        val id = viewModel.pendingSelectionId ?: return
        val lat = viewModel.pendingSelectionLat
        val lng = viewModel.pendingSelectionLng
        viewModel.pendingSelectionId = null
        viewModel.pendingSelectionLat = null
        viewModel.pendingSelectionLng = null
        val near = if (lat != null && lng != null) LatLng(lat, lng) else null
        val pin = closestPinWith(id, near, pins) ?: return
        viewModel.select(pin.position, pin.items, id)
        if (near != null) viewModel.moveCamera(MapCameraRequest.Show(near, MAX_FIT_ZOOM))
    }

    /** The pin with memory [id] (a memory and its fragments can have several) closest to [near]. */
    private fun closestPinWith(id: Int, near: LatLng?, pins: List<Markerable.MarkerableCluster>): Markerable.MarkerableCluster? {
        val candidates = pins.filter { pin -> pin.items.any { it.groupId == id } }
        return if (near == null) candidates.firstOrNull() else candidates.minByOrNull { distanceSquared(it.position, near) }
    }

    private fun distanceSquared(a: LatLng, b: LatLng): Double {
        val dLat = a.latitude - b.latitude
        val dLng = a.longitude - b.longitude
        return dLat * dLat + dLng * dLng
    }

    /** Shows the user's position on the map, if location access was granted. Never asks for it (see [myLocationAction]). */
    private fun refreshMyLocationDot() {
        val context = context ?: return
        viewModel.setShowsMyLocation(hasLocationPermission(context))
    }

    private fun zoomToUserLocationIfPossible() {
        val context = context
        if (context != null && hasLocationPermission(context) && !viewModel.isInitialZoomDone) {
            AppServices.location.lastLocation(context)
                .addOnSuccessListener { location ->
                    if (viewLifecycleOwnerLiveData.value == null || viewModel.isInitialZoomDone) return@addOnSuccessListener
                    if (location != null) {
                        viewModel.isInitialZoomDone = true
                        viewModel.moveCamera(MapCameraRequest.Show(LatLng(location.latitude, location.longitude), MY_LOCATION_ZOOM))
                    } else {
                        showAllInstead()
                    }
                }
                .addOnFailureListener {
                    if (viewLifecycleOwnerLiveData.value != null && !viewModel.isInitialZoomDone) showAllInstead()
                }
        }
    }

    /** Without the phone's location the first look shows all the pins, as without location access. */
    private fun showAllInstead() {
        viewModel.isLocationUnknown = true
        if (viewModel.pendingSelectionId == null) fitAllOnFirstLook(clustersFlow.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapLoadTrace = null
    }

    companion object {
        const val TAG = "MapFragment"
        private const val DATA_LOAD_TIMEOUT_MS = 3000L
    }
}
