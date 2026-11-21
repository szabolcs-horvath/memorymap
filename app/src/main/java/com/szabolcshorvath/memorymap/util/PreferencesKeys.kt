package com.szabolcshorvath.memorymap.util

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    val SHOW_FRAGMENT_MARKERS = booleanPreferencesKey("show_fragment_markers")
    const val SHOW_FRAGMENT_MARKERS_DEFAULT_VALUE = false
    val MARKER_CLUSTERING_ENABLED = booleanPreferencesKey("cluster_markers_enabled")
    const val MARKER_CLUSTERING_ENABLED_DEFAULT_VALUE = true
    val DEFAULT_DATE_FILTER = stringPreferencesKey("default_date_filter")
    val USER_EMAIL_KEY = stringPreferencesKey("user_email")

    /** How long automatic backups are kept, by `BackupRetention` name; unset is its default. */
    val AUTOMATIC_BACKUP_RETENTION = stringPreferencesKey("automatic_backup_retention")

    /** Whether the user said no to location access when last asked (see `MyLocationRequester`). */
    val LOCATION_PERMISSION_DENIED = booleanPreferencesKey("location_permission_denied")
}
