package com.szabolcshorvath.memorymap.fragment

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
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.data.MarkerClustering
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.data.MemoryRepository
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.DateFilterOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How many of the shown markers have one colour. */
data class ColorCount(@param:ColorInt val color: Int, val count: Int)

/** The pin whose memories the panel lists: where it is, its memories (one row each), and the one shown first. */
data class MarkerSelection(val position: LatLng, val items: List<Markerable>, val selectedId: Int?)

/** Where the map's camera should go. The screen moves it once, then clears it ([MapFragmentViewModel.onCameraMoved]). */
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

class MapFragmentViewModel(application: Application, private val memories: MemoryRepository) : AndroidViewModel(application) {

    var isInitialZoomDone = false

    /** Location access is granted, but the phone couldn't say where it is (location off, no fix yet). */
    var isLocationUnknown = false

    /** A "show on map" request waiting for the fragment's view (see `MapFragment.requestFocusOnMemory`). */
    var pendingFocus: FocusRequest? = null

    var pendingSelectionId: Int? = null
    var pendingSelectionLat: Double? = null
    var pendingSelectionLng: Double? = null

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

    @AddTrace(name = "map_fragment_view_model_cluster_markerables", enabled = true)
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

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                MapFragmentViewModel(app, app.appContainer.memories)
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
