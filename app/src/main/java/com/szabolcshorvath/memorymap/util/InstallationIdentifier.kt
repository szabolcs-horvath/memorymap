package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.szabolcshorvath.memorymap.dataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

object InstallationIdentifier {
    private const val TAG = "InstallationIdentifier"
    private val INSTALLATION_IDENTIFIER_KEY = stringPreferencesKey("installation_identifier")

    suspend fun getInstallationIdentifier(context: Context): String {
        val installationIdentifier =
            context.dataStore.data.map { it[INSTALLATION_IDENTIFIER_KEY] }.firstOrNull()
        if (installationIdentifier == null) {
            val newInstallationIdentifier = java.util.UUID.randomUUID().toString()
            context.dataStore.edit { it[INSTALLATION_IDENTIFIER_KEY] = newInstallationIdentifier }
            Log.i(TAG, "Generated new installation identifier: $newInstallationIdentifier")
            return newInstallationIdentifier
        } else {
            Log.d(TAG, "Using existing installation identifier: $installationIdentifier")
            return installationIdentifier
        }
    }
}