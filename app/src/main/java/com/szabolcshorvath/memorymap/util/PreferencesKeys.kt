package com.szabolcshorvath.memorymap.util

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
    val LAST_APP_VERSION = longPreferencesKey("last_app_version")
    val SHOW_FRAGMENT_MARKERS = booleanPreferencesKey("show_fragment_markers")
    val DEFAULT_DATE_FILTER = stringPreferencesKey("default_date_filter")
    val USER_EMAIL_KEY = stringPreferencesKey("user_email")
}