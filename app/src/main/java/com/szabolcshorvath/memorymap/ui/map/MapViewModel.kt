package com.szabolcshorvath.memorymap.ui.map

import android.app.Application
import androidx.annotation.ColorInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.perf.metrics.AddTrace
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.data.MarkerClustering
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.data.MemoryRepository
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PermissionUtil.hasLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

/** How many of the shown markers have one colour. */
data class ColorCount(@param:ColorInt val color: Int, val count: Int)

/** The pin whose memories the panel lists: where it is, its memories (one row each), and the one shown first. */
data class MarkerSelection(val position: LatLng, val items: List<Markerable>, val selectedId: Int?)

/** Where the map's camera should go. The screen moves it once, then clears it ([MapViewModel.onCameraMoved]). */
sealed interface MapCameraRequest {
    /** [position] at [zoom], gliding there if [animate]. */
    data class Show(val position: LatLng, val zoom: Float, val animate: Boolean = true) : MapCameraRequest

    /** All of [positions], no closer than the zoom a single memory is shown at. */
    data class Fit(val positions: List<LatLng>) : MapCameraRequest

    /**
     * Where the phone is, for the "my location" button: the first press shows the area around it, and a press while
     * the map still shows that, or while it is closer than that anywhere, shows the streets around it.
     */
    data class MyLocation(val position: LatLng) : MapCameraRequest

    /** One zoom step closer to [position] (a tapped cluster). */
    data class ZoomIn(val position: LatLng) : MapCameraRequest
}

data class MapUiState(
    val pins: List<Markerable.MarkerableCluster> = emptyList(),
    /** Pins close together are drawn as one circle (Settings). */
    val clustering: Boolean = true,
    val filterStart: LocalDate? = null,
    val filterEnd: LocalDate? = null,
    /** The quick filter applied, or null for dates picked by hand. */
    val appliedFilter: DateFilterOption? = null,
    val isStatsVisible: Boolean = false,
    /** The colours of the memories shown, most common first. */
    val colorCounts: List<ColorCount> = emptyList(),
    val selection: MarkerSelection? = null,
    /** The map shows the blue "you are here" dot: location access is granted. */
    val showsMyLocation: Boolean = false,
    val cameraRequest: MapCameraRequest? = null,
)

// One function per intent of the screen.
@Suppress("TooManyFunctions")
class MapViewModel(application: Application, private val memories: MemoryRepository) : AndroidViewModel(application) {

    /** The camera has made its first move (to the phone, to all the pins, or to a memory shown from elsewhere). */
    private var isInitialZoomDone = false

    /** Location access is granted, but the phone couldn't say where it is (location off, no fix yet). */
    private var isLocationUnknown = false

    /** The next pins come from a filter the user picked, so the camera shows them all. */
    private var shouldFitNextPins = false

    private val _pendingFocus = MutableStateFlow<FocusRequest?>(null)

    /** A "show on map" request, handled once the map is shown ([requestFocusOnMemory]). */
    val pendingFocus: StateFlow<FocusRequest?> = _pendingFocus.asStateFlow()

    /** The memory a "show on map" request selects once its pin is there. */
    private var pendingSelection: FocusRequest? = null

    /** The memory and the pin the panel was opened for, so a data change can open it again on the same pin. */
    val selectedMemoryId: Int? get() = screen.value.selection?.selectedId
    val selectedMarkerPosition: LatLng? get() = screen.value.selection?.position

    private val _filterStartDate = MutableStateFlow<LocalDate?>(null)
    val filterStartDate: StateFlow<LocalDate?> = _filterStartDate.asStateFlow()

    private val _filterEndDate = MutableStateFlow<LocalDate?>(null)
    val filterEndDate: StateFlow<LocalDate?> = _filterEndDate.asStateFlow()

    /** The quick filter option applied, or null for dates picked by hand. */
    private val _appliedFilter = MutableStateFlow<DateFilterOption?>(null)

    private val _isDateFilterLoaded = MutableStateFlow(false)
    val isDateFilterLoaded: StateFlow<Boolean> = _isDateFilterLoaded.asStateFlow()

    /** What the screen shows apart from the markers and the filter: the panel, the statistics, the camera. */
    private val screen = MutableStateFlow(MapUiState())

    private var cachedMarkerClusters: Pair<StateFlow<Boolean>, StateFlow<List<Markerable.MarkerableCluster>>>? = null
    private var cachedUiState: StateFlow<MapUiState>? = null

    init {
        viewModelScope.launch {
            val defaultOption = DateFilterOption.getFromDataStore(application.dataStore)
            if (!_isDateFilterLoaded.value) {
                val (start, end) = defaultOption.dateRange(LocalDate.now())
                updateDateFilter(start, end, defaultOption)
            }
        }
    }

    fun updateDateFilter(start: LocalDate?, end: LocalDate?, option: DateFilterOption? = null) {
        _filterStartDate.value = start
        _filterEndDate.value = end
        _appliedFilter.value = option
        _isDateFilterLoaded.value = true
    }

    /** The whole screen's state: the pins after the date filter, with fragments if [showFragments], clustered if [clustering]. */
    fun uiState(showFragments: StateFlow<Boolean>, clustering: StateFlow<Boolean>): StateFlow<MapUiState> =
        cachedUiState ?: combine(
            getMarkerClusters(showFragments),
            clustering,
            combine(filterStartDate, filterEndDate, _appliedFilter) { start, end, option -> Triple(start, end, option) },
            screen,
        ) { pins, clusters, (start, end, option), screen ->
            screen.copy(
                pins = pins,
                clustering = clusters,
                filterStart = start,
                filterEnd = end,
                appliedFilter = option,
                colorCounts = colorCounts(pins),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), MapUiState()).also {
            cachedUiState = it
        }

    fun getMarkerClusters(showFragmentsEnabledFlow: StateFlow<Boolean>): StateFlow<List<Markerable.MarkerableCluster>> {
        val cached = cachedMarkerClusters
        if (cached != null && cached.first === showFragmentsEnabledFlow) {
            return cached.second
        }

        return combine(
            memories.allGroups,
            memories.allFragments,
            filterStartDate,
            filterEndDate,
            showFragmentsEnabledFlow
        ) { groups, fragments, start, end, showFrags ->
            val groupsMap = groups.associateBy { it.id }
            val candidateItems = mutableListOf<Markerable>()
            candidateItems.addAll(groups)
            if (showFrags) {
                fragments.forEach { fragment ->
                    groupsMap[fragment.groupId]?.let { parent -> candidateItems.add(Markerable.FragmentMarker(fragment, parent.title)) }
                }
            }
            candidateItems.filter { item ->
                val itemStart = (item.startDate ?: groupsMap[item.groupId]?.startDate)?.toLocalDate()
                val itemEnd = (item.endDate ?: groupsMap[item.groupId]?.endDate)?.toLocalDate()
                if (itemStart == null || itemEnd == null) return@filter true
                overlapsDateFilter(itemStart, itemEnd, start, end)
            }
        }.flowOn(Dispatchers.Default).map { filteredItems ->
            clusterMarkerables(filteredItems)
        }.flowOn(Dispatchers.Default).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS),
            emptyList()
        ).also {
            cachedMarkerClusters = showFragmentsEnabledFlow to it
        }
    }

    @AddTrace(name = "map_view_model_cluster_markerables", enabled = true)
    private fun clusterMarkerables(items: List<Markerable>): List<Markerable.MarkerableCluster> = MarkerClustering.cluster(items)

    fun toggleStats() = screen.update { it.copy(isStatsVisible = !it.isStatsVisible) }

    fun hideStats() = screen.update { it.copy(isStatsVisible = false) }

    /** Opens the panel for the pin at [position] with [items], showing [selectedId] first (or its first memory). */
    fun select(position: LatLng, items: List<Markerable>, selectedId: Int? = selectedMemoryId) {
        val memories = items.distinctBy { it.groupId }
        val first = selectedId?.takeIf { id -> memories.any { it.groupId == id } } ?: memories.firstOrNull()?.groupId
        screen.update { it.copy(selection = MarkerSelection(position, memories, first)) }
    }

    fun clearSelection() = screen.update { it.copy(selection = null) }

    /** Closes the panel and the colour statistics, and returns whether either was open. */
    fun closeOverlays(): Boolean {
        val wasOpen = screen.value.selection != null || screen.value.isStatsVisible
        screen.update { it.copy(selection = null, isStatsVisible = false) }
        return wasOpen
    }

    fun setShowsMyLocation(shows: Boolean) = screen.update { it.copy(showsMyLocation = shows) }

    fun moveCamera(request: MapCameraRequest) = screen.update { it.copy(cameraRequest = request) }

    /** The screen moved the camera for [request]; a newer request that replaced it meanwhile stays. */
    fun onCameraMoved(request: MapCameraRequest) = screen.update { if (it.cameraRequest == request) it.copy(cameraRequest = null) else it }

    /** Show memory [id], whose marker is at [lat], [lng]. */
    data class FocusRequest(val id: Int, val lat: Double, val lng: Double)

    private val pins: List<Markerable.MarkerableCluster> get() = cachedMarkerClusters?.second?.value.orEmpty()

    private val hasLocationAccess: Boolean get() = hasLocationPermission(getApplication())

    fun pickQuickFilter(option: DateFilterOption) {
        val (start, end) = option.dateRange(LocalDate.now())
        if (start != filterStartDate.value || end != filterEndDate.value) shouldFitNextPins = true
        updateDateFilter(start, end, option)
    }

    fun pickDates(start: LocalDate, end: LocalDate) {
        shouldFitNextPins = true
        updateDateFilter(start, end)
    }

    fun tapPin(pin: Markerable.MarkerableCluster) {
        select(pin.position, pin.items)
        moveCamera(MapCameraRequest.Show(pin.position, MAX_FIT_ZOOM))
    }

    fun tapCluster(position: LatLng) {
        clearSelection()
        moveCamera(MapCameraRequest.ZoomIn(position))
    }

    fun tapMap() {
        clearSelection()
        hideStats()
    }

    /** "My location" found the phone at [position]. */
    fun showMyLocation(position: LatLng) {
        setShowsMyLocation(hasLocationAccess)
        isInitialZoomDone = true
        moveCamera(MapCameraRequest.MyLocation(position))
    }

    /** The map is shown: the "you are here" dot follows location access, and the first look goes to the phone. */
    fun onShown() {
        setShowsMyLocation(hasLocationAccess)
        if (!hasLocationAccess || isInitialZoomDone) return
        AppServices.location.lastLocation(getApplication())
            .addOnSuccessListener { location ->
                if (isInitialZoomDone) return@addOnSuccessListener
                if (location != null) {
                    isInitialZoomDone = true
                    moveCamera(MapCameraRequest.Show(LatLng(location.latitude, location.longitude), MY_LOCATION_ZOOM))
                } else {
                    showAllInstead()
                }
            }
            .addOnFailureListener { if (!isInitialZoomDone) showAllInstead() }
    }

    /** Without the phone's location the first look shows all the pins, as without location access. */
    private fun showAllInstead() {
        isLocationUnknown = true
        if (pendingSelection == null) fitAllOnFirstLook(pins)
    }

    /**
     * The Map tab tapped while the map shows. It closes what is open over the map (the panel, the statistics) and
     * leaves the camera where it is; with nothing open, it goes back to the map's home view: the area around the phone
     * with location access, otherwise all the memories shown, as when the map opens.
     */
    fun onTabTappedAgain() {
        if (closeOverlays()) return
        if (!hasLocationAccess) return fitAll(pins)
        AppServices.location.lastLocation(getApplication())
            .addOnSuccessListener { location ->
                if (location != null) {
                    moveCamera(MapCameraRequest.Show(LatLng(location.latitude, location.longitude), MY_LOCATION_ZOOM))
                } else {
                    fitAll(pins)
                }
            }
            .addOnFailureListener { fitAll(pins) }
    }

    /**
     * Shows memory [id] on the map. The request waits in [pendingFocus] until the map is shown, so it can be made while
     * the tab is hidden; a newer request replaces it.
     */
    fun requestFocusOnMemory(lat: Double, lng: Double, id: Int) {
        isInitialZoomDone = true
        _pendingFocus.value = FocusRequest(id, lat, lng)
    }

    /**
     * Handles [request] now that the map is shown: waits for the memory and the filter to load, widens the filter to
     * include the memory if needed, moves the camera there and selects it once its pin is there.
     */
    suspend fun handleFocus(request: FocusRequest) {
        // Wait until the memory is there, then for the filter from DataStore.
        val groups = withTimeoutOrNull(DATA_LOAD_TIMEOUT_MS.milliseconds) {
            memories.allGroups.first { list -> list.any { it.id == request.id } }
        }
        val isFilterLoaded = groups != null && withTimeoutOrNull(DATA_LOAD_TIMEOUT_MS.milliseconds) {
            isDateFilterLoaded.first { it }
        } != null

        if (_pendingFocus.value != request) return // A newer request replaced this one.
        _pendingFocus.value = null
        val memory = groups?.find { it.id == request.id }
        if (memory == null || !isFilterLoaded) return

        pendingSelection = request
        // Goes there now: the pin may take a moment to come (a widened filter), and is then selected.
        moveCamera(MapCameraRequest.Show(LatLng(request.lat, request.lng), MAX_FIT_ZOOM))
        val widened = widenFilterFor(memory.startDate.toLocalDate(), memory.endDate.toLocalDate())
        // The filter didn't change, so the pins already there are the ones to select from.
        if (!widened && pins.isNotEmpty()) selectPendingMemory(pins)
    }

    /** Widens the filter to include all of a memory from [memoryStart] to [memoryEnd], unless it already overlaps. */
    private fun widenFilterFor(memoryStart: LocalDate, memoryEnd: LocalDate): Boolean {
        val currentStart = filterStartDate.value
        val currentEnd = filterEndDate.value
        if (overlapsDateFilter(memoryStart, memoryEnd, currentStart, currentEnd)) return false
        val newStart = if (currentStart != null && memoryStart.isBefore(currentStart)) memoryStart else currentStart
        val newEnd = if (currentEnd != null && memoryEnd.isAfter(currentEnd)) memoryEnd else currentEnd
        val widened = newStart != currentStart || newEnd != currentEnd
        if (widened) updateDateFilter(newStart, newEnd)
        return widened
    }

    /** Keeps the panel and the camera in step with new [pins] (a filter or data change). */
    fun onPinsChanged(pins: List<Markerable.MarkerableCluster>) = trace("map_view_model_on_pins_changed") {
        val fit = shouldFitNextPins
        shouldFitNextPins = false
        val pending = pendingSelection
        if (pending != null && pins.any { pin -> pin.items.any { it.groupId == pending.id } }) {
            selectPendingMemory(pins)
        } else {
            reopenSelection(pins)
        }
        // A "show on map" request moves the camera itself.
        if (pending != null) return@trace
        if (fit) fitAll(pins) else fitAllOnFirstLook(pins)
    }

    /** The first time the map has pins and won't go to where the phone is, it shows them all. */
    private fun fitAllOnFirstLook(pins: List<Markerable.MarkerableCluster>) {
        val goesToPhone = hasLocationAccess && !isLocationUnknown
        if (!isInitialZoomDone && !goesToPhone) fitAll(pins)
    }

    private fun fitAll(pins: List<Markerable.MarkerableCluster>) {
        if (pins.isEmpty()) return
        isInitialZoomDone = true
        moveCamera(MapCameraRequest.Fit(pins.map { it.position }))
    }

    /** Opens the panel again on the pin it was open for, now that the pins changed, or closes it if that pin is gone. */
    private fun reopenSelection(pins: List<Markerable.MarkerableCluster>) {
        val selectedId = selectedMemoryId ?: return
        val pin = closestPinWith(selectedId, selectedMarkerPosition, pins)
        if (pin != null) select(pin.position, pin.items, selectedId) else clearSelection()
    }

    /** Selects the memory a "show on map" request asked for, on its pin closest to where it was asked for. */
    private fun selectPendingMemory(pins: List<Markerable.MarkerableCluster>) {
        val request = pendingSelection ?: return
        pendingSelection = null
        val near = LatLng(request.lat, request.lng)
        val pin = closestPinWith(request.id, near, pins) ?: return
        select(pin.position, pin.items, request.id)
        moveCamera(MapCameraRequest.Show(near, MAX_FIT_ZOOM))
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

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L
        private const val DATA_LOAD_TIMEOUT_MS = 3000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                MapViewModel(app, app.appContainer.memories)
            }
        }

        /** How many of [pins]' memories have each colour, most common first (ties in colour order, so slices keep their place). */
        internal fun colorCounts(pins: List<Markerable.MarkerableCluster>): List<ColorCount> =
            pins.flatMap { it.items }
                .groupingBy { it.markerColor() }
                .eachCount()
                .map { (color, count) -> ColorCount(color, count) }
                .sortedWith(compareByDescending<ColorCount> { it.count }.thenBy { it.color })

        /**
         * Whether a memory from [itemStart] to [itemEnd] falls at least partly within the filter range.
         * A null [filterStart] or [filterEnd] leaves that side open. Both ends are inclusive.
         */
        internal fun overlapsDateFilter(
            itemStart: LocalDate,
            itemEnd: LocalDate,
            filterStart: LocalDate?,
            filterEnd: LocalDate?
        ): Boolean = (filterEnd == null || !itemStart.isAfter(filterEnd)) &&
            (filterStart == null || !itemEnd.isBefore(filterStart))
    }
}
