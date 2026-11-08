package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.perf.metrics.AddTrace
import com.szabolcshorvath.memorymap.data.CommonRepository
import com.szabolcshorvath.memorymap.data.MarkerClustering
import com.szabolcshorvath.memorymap.data.Markerable
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
import kotlinx.coroutines.launch
import java.time.LocalDate

class MapFragmentViewModel(application: Application) : AndroidViewModel(application) {
    private val commonRepository = CommonRepository.getInstance(application)

    var selectedMemoryId: Int? = null
    var selectedMarkerPosition: LatLng? = null
    var isInitialZoomDone = false
    var lastCameraPosition: CameraPosition? = null

    /** A "show on map" request waiting for the fragment's view (see `MapFragment.requestFocusOnMemory`). */
    var pendingFocus: FocusRequest? = null

    var pendingSelectionId: Int? = null
    var pendingSelectionLat: Double? = null
    var pendingSelectionLng: Double? = null

    var isStatsVisible = false

    private val _filterStartDate = MutableStateFlow<LocalDate?>(null)
    val filterStartDate: StateFlow<LocalDate?> = _filterStartDate.asStateFlow()

    private val _filterEndDate = MutableStateFlow<LocalDate?>(null)
    val filterEndDate: StateFlow<LocalDate?> = _filterEndDate.asStateFlow()

    /** The quick filter option applied, or null for dates picked by hand. */
    private val _appliedFilter = MutableStateFlow<DateFilterOption?>(null)
    val appliedFilter: StateFlow<DateFilterOption?> = _appliedFilter.asStateFlow()

    private val _isDateFilterLoaded = MutableStateFlow(false)
    val isDateFilterLoaded: StateFlow<Boolean> = _isDateFilterLoaded.asStateFlow()

    private var cachedMarkerClusters: Pair<StateFlow<Boolean>, StateFlow<List<Markerable.MarkerableCluster>>>? = null

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

    fun getMarkerClusters(showFragmentsEnabledFlow: StateFlow<Boolean>): StateFlow<List<Markerable.MarkerableCluster>> {
        val cached = cachedMarkerClusters
        if (cached != null && cached.first === showFragmentsEnabledFlow) {
            return cached.second
        }

        return combine(
            commonRepository.allGroups,
            commonRepository.allFragments,
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

    /** Show memory [id], whose marker is at [lat], [lng]. */
    data class FocusRequest(val id: Int, val lat: Double, val lng: Double)

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L

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
