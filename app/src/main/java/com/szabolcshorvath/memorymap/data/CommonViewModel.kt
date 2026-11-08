package com.szabolcshorvath.memorymap.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.szabolcshorvath.memorymap.MemoryMapApplication
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The activity's data, shared by its screens: the memories, the presets and the map preferences, and deleting a memory for good. */
class CommonViewModel(
    application: Application,
    private val memories: MemoryRepository,
    private val presets: PresetRepository
) : AndroidViewModel(application) {

    val allGroups = memories.allGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())
    val allPresets = presets.allPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())

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

    /** Adds the default colour presets when there are none, as on the first start. */
    suspend fun addDefaultPresetsIfEmpty() = presets.addDefaultsIfEmpty()

    // The application scope: an undo or the clean-up after a delete must finish even if the activity is destroyed
    // meanwhile (which also dismisses the Snackbar that offers the undo).
    private val applicationScope get() = getApplication<MemoryMapApplication>().applicationScope

    /** Puts back a deleted memory. */
    fun undoDelete(deleted: MemoryGroupWithMedia) {
        applicationScope.launch { memories.undoDelete(deleted) }
    }

    /** The delete can no longer be undone. [keptUris]: media still in use elsewhere (the Add form), whose grants stay. */
    fun finishDelete(deleted: MemoryGroupWithMedia, keptUris: Set<String>) {
        applicationScope.launch { memories.finishDelete(deleted, keptUris) }
    }

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                CommonViewModel(app, app.appContainer.memories, app.appContainer.presets)
            }
        }
    }
}
