package com.szabolcshorvath.memorymap.fragment

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The map preferences as stored; null until loaded, while their controls stay off. */
data class MapPreferences(
    val showFragmentMarkers: Boolean? = null,
    val markerClustering: Boolean? = null,
    val defaultFilter: DateFilterOption? = null,
)

/** The map preferences in Settings: read from and stored in the app's DataStore. */
class MapPreferencesModel(private val scope: CoroutineScope, private val dataStore: DataStore<Preferences>) {
    val state: StateFlow<MapPreferences> = dataStore.data.map { prefs ->
        MapPreferences(
            showFragmentMarkers = prefs[PreferencesKeys.SHOW_FRAGMENT_MARKERS] ?: PreferencesKeys.SHOW_FRAGMENT_MARKERS_DEFAULT_VALUE,
            markerClustering = prefs[PreferencesKeys.MARKER_CLUSTERING_ENABLED]
                ?: PreferencesKeys.MARKER_CLUSTERING_ENABLED_DEFAULT_VALUE,
            defaultFilter = DateFilterOption.ofStoredName(prefs[PreferencesKeys.DEFAULT_DATE_FILTER]),
        )
    }.stateIn(scope, SharingStarted.Eagerly, MapPreferences())

    fun setShowFragmentMarkers(show: Boolean) = set(PreferencesKeys.SHOW_FRAGMENT_MARKERS, show)

    fun setMarkerClustering(enabled: Boolean) = set(PreferencesKeys.MARKER_CLUSTERING_ENABLED, enabled)

    fun setDefaultFilter(option: DateFilterOption) = set(PreferencesKeys.DEFAULT_DATE_FILTER, option.name)

    private fun <T> set(key: Preferences.Key<T>, value: T) {
        scope.launch { dataStore.edit { it[key] = value } }
    }
}
