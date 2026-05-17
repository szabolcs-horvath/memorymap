package com.szabolcshorvath.memorymap.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

class CommonViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CommonRepository.getInstance(application)

    val allGroups = repository.allGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())
    val allPresets = repository.allPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())

    val showFragmentsEnabled: StateFlow<Boolean> = application.dataStore.data
        .map { it[PreferencesKeys.SHOW_FRAGMENT_MARKERS] ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS),
            initialValue = runBlocking {
                application.dataStore.data
                    .map { it[PreferencesKeys.SHOW_FRAGMENT_MARKERS] ?: false }
                    .first()
            }
        )

    val markerClusteringEnabled: StateFlow<Boolean> = application.dataStore.data
        .map { it[PreferencesKeys.MARKER_CLUSTERING_ENABLED] ?: true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS),
            initialValue = runBlocking {
                application.dataStore.data
                    .map { it[PreferencesKeys.MARKER_CLUSTERING_ENABLED] ?: true }
                    .first()
            }
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
