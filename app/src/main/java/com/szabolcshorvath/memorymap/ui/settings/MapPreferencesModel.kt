package com.szabolcshorvath.memorymap.ui.settings

import com.szabolcshorvath.memorymap.data.AppPreferences
import com.szabolcshorvath.memorymap.util.DateFilterOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The map preferences as stored; null until loaded, while their controls stay off. */
data class MapPreferences(
    val showFragmentMarkers: Boolean? = null,
    val markerClustering: Boolean? = null,
    val defaultFilter: DateFilterOption? = null,
)

/** The map preferences in Settings: read from and stored in the app's preferences. */
class MapPreferencesModel(private val scope: CoroutineScope, private val preferences: AppPreferences) {
    val state: StateFlow<MapPreferences> = combine(
        preferences.showFragmentMarkers,
        preferences.markerClustering,
        preferences.defaultFilter,
        ::MapPreferences
    ).stateIn(scope, SharingStarted.Eagerly, MapPreferences())

    fun setShowFragmentMarkers(show: Boolean) {
        scope.launch { preferences.setShowFragmentMarkers(show) }
    }

    fun setMarkerClustering(enabled: Boolean) {
        scope.launch { preferences.setMarkerClustering(enabled) }
    }

    fun setDefaultFilter(option: DateFilterOption) {
        scope.launch { preferences.setDefaultFilter(option) }
    }
}
