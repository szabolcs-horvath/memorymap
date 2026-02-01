package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.szabolcshorvath.memorymap.dataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID

object InstallationIdentifier {
    private const val TAG = "InstallationIdentifier"
    private val INSTALLATION_IDENTIFIER_KEY = stringPreferencesKey("installation_identifier")

    private var cachedInstallationIdentifier: String? = null

    suspend fun getInstallationIdentifier(context: Context): String {
        return cachedInstallationIdentifier ?: retrieveInstallationIdentifier(context)
    }

    private suspend fun retrieveInstallationIdentifier(context: Context): String {
        val idFromDataStore =
            context.dataStore.data.map { it[INSTALLATION_IDENTIFIER_KEY] }.firstOrNull()

        if (idFromDataStore != null) {
            Log.i(TAG, "Using existing installation identifier in dataStore: $idFromDataStore")
            cachedInstallationIdentifier = idFromDataStore
            return idFromDataStore
        } else {
            return generateInstallationIdentifier(context)
        }
    }

    private suspend fun generateInstallationIdentifier(context: Context): String {
        val newInstallationIdentifier = UUID.randomUUID().toString()
        context.dataStore.edit {
            it[INSTALLATION_IDENTIFIER_KEY] = newInstallationIdentifier
        }
        Log.i(TAG, "Generated new installation identifier: $newInstallationIdentifier")
        cachedInstallationIdentifier = newInstallationIdentifier
        return newInstallationIdentifier
    }
}