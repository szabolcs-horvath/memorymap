package com.szabolcshorvath.memorymap.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CommonViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CommonRepository.getInstance(application)

    val allGroups = repository.allGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())
    val allPresets = repository.allPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())

    val showFragmentsEnabled: StateFlow<Boolean> = application.dataStore.data
        .map { it[PreferencesKeys.SHOW_FRAGMENT_MARKERS] ?: PreferencesKeys.SHOW_FRAGMENT_MARKERS_DEFAULT_VALUE }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS),
            initialValue = PreferencesKeys.SHOW_FRAGMENT_MARKERS_DEFAULT_VALUE
        )

    val markerClusteringEnabled: StateFlow<Boolean> = application.dataStore.data
        .map { it[PreferencesKeys.MARKER_CLUSTERING_ENABLED] ?: PreferencesKeys.MARKER_CLUSTERING_ENABLED_DEFAULT_VALUE }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS),
            initialValue = PreferencesKeys.MARKER_CLUSTERING_ENABLED_DEFAULT_VALUE
        )

    fun getDb() = repository.getDb()
    fun getMemoryGroupDao() = repository.getMemoryGroupDao()
    fun getHSVPresetDao() = repository.getHSVPresetDao()

    fun refreshDatabase() {
        repository.refreshDatabase(getApplication())
    }

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L
    }
}
