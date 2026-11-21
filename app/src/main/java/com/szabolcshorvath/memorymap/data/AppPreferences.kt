package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.szabolcshorvath.memorymap.backup.BackupRetention
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
 * The app's preferences (keys and defaults in [PreferencesKeys]): the map's, the signed-in account, how long automatic backups are kept, and whether the user
 * said no to location access. Writes may be called from the main thread.
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

    /** How long automatic backups are kept before they are moved to Drive's trash. */
    val backupRetention: Flow<BackupRetention> = read { BackupRetention.ofStoredName(it[PreferencesKeys.AUTOMATIC_BACKUP_RETENTION]) }

    suspend fun setShowFragmentMarkers(show: Boolean) = set(PreferencesKeys.SHOW_FRAGMENT_MARKERS, show)

    suspend fun setMarkerClustering(enabled: Boolean) = set(PreferencesKeys.MARKER_CLUSTERING_ENABLED, enabled)

    suspend fun setDefaultFilter(option: DateFilterOption) = set(PreferencesKeys.DEFAULT_DATE_FILTER, option.name)

    suspend fun setBackupRetention(retention: BackupRetention) = set(PreferencesKeys.AUTOMATIC_BACKUP_RETENTION, retention.name)

    /** Stores the signed-in account's [email], or forgets it (null) when the user signs out. */
    suspend fun setEmail(email: String?) {
        store.edit { if (email == null) it.remove(PreferencesKeys.USER_EMAIL_KEY) else it[PreferencesKeys.USER_EMAIL_KEY] = email }
    }

    /** Whether the user said no to location access when last asked (see `MyLocationRequester`). */
    suspend fun isLocationDenied(): Boolean = store.data.first()[PreferencesKeys.LOCATION_PERMISSION_DENIED] ?: false

    suspend fun setLocationDenied(denied: Boolean) = set(PreferencesKeys.LOCATION_PERMISSION_DENIED, denied)

    private fun <T> read(value: (Preferences) -> T): Flow<T> = store.data.map(value).distinctUntilChanged()

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }
}
