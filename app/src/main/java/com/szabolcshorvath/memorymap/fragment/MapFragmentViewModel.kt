package com.szabolcshorvath.memorymap.fragment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.perf.metrics.AddTrace
import com.szabolcshorvath.memorymap.data.CommonRepository
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

    var pendingSelectionId: Int? = null
    var pendingSelectionLat: Double? = null
    var pendingSelectionLng: Double? = null

    var isStatsVisible = false

    private val _filterStartDate = MutableStateFlow<LocalDate?>(null)
    val filterStartDate: StateFlow<LocalDate?> = _filterStartDate.asStateFlow()

    private val _filterEndDate = MutableStateFlow<LocalDate?>(null)
    val filterEndDate: StateFlow<LocalDate?> = _filterEndDate.asStateFlow()

    private val _appliedFilterLabel = MutableStateFlow<String?>(null)
    val appliedFilterLabel: StateFlow<String?> = _appliedFilterLabel.asStateFlow()

    private val _isDateFilterLoaded = MutableStateFlow(false)
    val isDateFilterLoaded: StateFlow<Boolean> = _isDateFilterLoaded.asStateFlow()

    private var cachedMarkerClusters: StateFlow<List<Markerable.MarkerableCluster>>? = null

    init {
        viewModelScope.launch {
            val defaultOption = DateFilterOption.getFromDataStore(application.dataStore)
            if (!_isDateFilterLoaded.value) {
                val (start, end) = defaultOption.dateRangeProvider(LocalDate.now())
                updateDateFilter(start, end, defaultOption.label)
            }
        }
    }

    fun updateDateFilter(start: LocalDate?, end: LocalDate?, label: String? = null) {
        _filterStartDate.value = start
        _filterEndDate.value = end
        _appliedFilterLabel.value = label
        _isDateFilterLoaded.value = true
    }

    fun getMarkerClusters(showFragmentsEnabledFlow: StateFlow<Boolean>): StateFlow<List<Markerable.MarkerableCluster>> {
        return cachedMarkerClusters ?: combine(
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
                    groupsMap[fragment.groupId]?.let { parent ->
                        fragment.title = parent.title
                        candidateItems.add(fragment)
                    }
                }
            }
            val effStart = start ?: LocalDate.MIN
            val effEnd = end ?: LocalDate.MAX
            candidateItems.filter { item ->
                val itemStart = (item.startDate ?: groupsMap[item.groupId]?.startDate)?.toLocalDate()
                val itemEnd = (item.endDate ?: groupsMap[item.groupId]?.endDate)?.toLocalDate()
                if (itemStart == null || itemEnd == null) return@filter true
                !itemStart.isBefore(effStart) && !itemEnd.isAfter(effEnd)
            }
        }.flowOn(Dispatchers.Default).map { filteredItems ->
            clusterMarkerables(filteredItems)
        }.flowOn(Dispatchers.Default).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS),
            emptyList()
        ).also { cachedMarkerClusters = it }
    }

    @AddTrace(name = "map_fragment_view_model_cluster_markerables", enabled = true)
    private fun clusterMarkerables(items: List<Markerable>): List<Markerable.MarkerableCluster> {
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

        return items.indices.groupBy { find(it) }.values
            .map { indices -> Markerable.MarkerableCluster(indices.map { items[it] }) }
    }

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L
    }
}
