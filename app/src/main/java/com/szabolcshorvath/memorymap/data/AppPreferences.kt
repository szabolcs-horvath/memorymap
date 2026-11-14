package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The app's one DataStore. It holds all the app's preferences, not just sign-in, whatever its name says: the name stays,
 * so existing installations keep theirs. Read and write it through [AppPreferences].
 */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "googleAuthDatastore")

/**
 * The app's preferences (keys and defaults in [PreferencesKeys]): the map's, the signed-in account, and what the app
 * remembers between starts. Writes may be called from the main thread.
 */
class AppPreferences(context: Context) {
    private val store = context.applicationContext.dataStore

    /** Whether the map shows fragments as markers of their own. */
    val showFragmentMarkers: Flow<Boolean> =
        read { it[PreferencesKeys.SHOW_FRAGMENT_MARKERS] ?: PreferencesKeys.SHOW_FRAGMENT_MARKERS_DEFAULT_VALUE }

    /** Whether the map draws pins close together as one circle. */
    val markerClustering: Flow<Boolean> =
        read { it[PreferencesKeys.MARKER_CLUSTERING_ENABLED] ?: PreferencesKeys.MARKER_CLUSTERING_ENABLED_DEFAULT_VALUE }

    /** The date filter the map opens with. */
    val defaultFilter: Flow<DateFilterOption> = read { DateFilterOption.ofStoredName(it[PreferencesKeys.DEFAULT_DATE_FILTER]) }

    /** The signed-in account's email, or null. */
    val email: Flow<String?> = read { it[PreferencesKeys.USER_EMAIL_KEY] }

    suspend fun setShowFragmentMarkers(show: Boolean) = set(PreferencesKeys.SHOW_FRAGMENT_MARKERS, show)

    suspend fun setMarkerClustering(enabled: Boolean) = set(PreferencesKeys.MARKER_CLUSTERING_ENABLED, enabled)

    suspend fun setDefaultFilter(option: DateFilterOption) = set(PreferencesKeys.DEFAULT_DATE_FILTER, option.name)

    /** Stores the signed-in account's [email], or forgets it (null) when the user signs out. */
    suspend fun setEmail(email: String?) {
        store.edit { if (email == null) it.remove(PreferencesKeys.USER_EMAIL_KEY) else it[PreferencesKeys.USER_EMAIL_KEY] = email }
    }

    /** Whether the user said no to location access when last asked (see `MyLocationRequester`). */
    suspend fun isLocationDenied(): Boolean = store.data.first()[PreferencesKeys.LOCATION_PERMISSION_DENIED] ?: false

    suspend fun setLocationDenied(denied: Boolean) = set(PreferencesKeys.LOCATION_PERMISSION_DENIED, denied)

    /** Whether the app hasn't finished a start yet on this installation. */
    suspend fun isFirstRun(): Boolean = store.data.first()[PreferencesKeys.IS_FIRST_RUN] ?: true

    suspend fun setFirstRunDone() = set(PreferencesKeys.IS_FIRST_RUN, false)

    /**
     * Records [version] as the version this installation last ran, if it is newer. Nothing reads it yet: it is kept for
     * a future step that must run once after an update.
     */
    suspend fun recordAppVersion(version: Long) {
        store.edit { if (version > (it[PreferencesKeys.LAST_APP_VERSION] ?: 0L)) it[PreferencesKeys.LAST_APP_VERSION] = version }
    }

    private fun <T> read(value: (Preferences) -> T): Flow<T> = store.data.map(value).distinctUntilChanged()

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }
}
