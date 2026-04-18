package com.szabolcshorvath.memorymap.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MemoryMapViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore

    private val _dbFlow = MutableStateFlow(MemoryMapDatabase.getDatabase(application))

    init {
        viewModelScope.launch {
            val defaultOption = DateFilterOption.getFromDataStore(dataStore)
            val (start, end) = defaultOption.dateRangeProvider(LocalDate.now())
            updateDateFilter(start, end, defaultOption.label)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allGroups = _dbFlow.flatMapLatest { db ->
        db.memoryGroupDao().getAllGroupsFlow()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allFragments = _dbFlow.flatMapLatest { db ->
        db.memoryGroupDao().getAllFragmentsFlow()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPresets = _dbFlow.flatMapLatest { db ->
        db.hsvPresetDao().getAllPresetsFlow()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())

    private val _filterStartDate = MutableStateFlow<LocalDate?>(null)
    val filterStartDate: StateFlow<LocalDate?> = _filterStartDate.asStateFlow()

    private val _filterEndDate = MutableStateFlow<LocalDate?>(null)
    val filterEndDate: StateFlow<LocalDate?> = _filterEndDate.asStateFlow()

    private val _appliedFilterLabel = MutableStateFlow<String?>(null)
    val appliedFilterLabel: StateFlow<String?> = _appliedFilterLabel.asStateFlow()

    val showFragments = dataStore.data
        .map { it[PreferencesKeys.SHOW_FRAGMENT_MARKERS] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), false)

    val filteredMarkerables: StateFlow<List<Markerable>> = combine(
        allGroups,
        allFragments,
        filterStartDate,
        filterEndDate,
        showFragments
    ) { groups, fragments, filterStart, filterEnd, showFrags ->
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

        val effectiveStart = filterStart ?: LocalDate.MIN
        val effectiveEnd = filterEnd ?: LocalDate.MAX

        candidateItems.filter { item ->
            val itemStart = (item.startDate ?: groupsMap[item.groupId]?.startDate)?.toLocalDate()
            val itemEnd = (item.endDate ?: groupsMap[item.groupId]?.endDate)?.toLocalDate()

            if (itemStart == null || itemEnd == null) return@filter true

            !itemStart.isBefore(effectiveStart) && !itemEnd.isAfter(effectiveEnd)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())

    fun getDb() = _dbFlow.value
    fun getMemoryGroupDao() = _dbFlow.value.memoryGroupDao()
    fun getHSVPresetDao() = _dbFlow.value.hsvPresetDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getGroupWithMedia(id: Int) = _dbFlow.flatMapLatest { db ->
        db.memoryGroupDao().getGroupWithMediaFlow(id)
    }

    fun refreshDatabase() {
        _dbFlow.value = MemoryMapDatabase.getDatabase(getApplication())
    }

    fun updateDateFilter(start: LocalDate?, end: LocalDate?, label: String? = null) {
        _filterStartDate.value = start
        _filterEndDate.value = end
        _appliedFilterLabel.value = label
    }

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L
    }
}
