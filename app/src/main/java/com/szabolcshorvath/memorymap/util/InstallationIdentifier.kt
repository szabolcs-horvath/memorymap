package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.szabolcshorvath.memorymap.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * A random id for this installation, used to tell whether a media item was saved on this device.
 *
 * The id lives in [Context.getNoBackupFilesDir], which Auto Backup and device-to-device transfer skip.
 * If it were backed up, a new phone would inherit it and treat the old phone's MediaStore URIs as its own.
 */
object InstallationIdentifier {
    const val TAG = "InstallationIdentifier"
    private const val ID_FILE_NAME = "installation_identifier"

    /** Where older versions kept the id. It is backed up, so it is migrated out and removed. */
    private val LEGACY_INSTALLATION_IDENTIFIER_KEY = stringPreferencesKey("installation_identifier")

    private val mutex = Mutex()

    @Volatile
    private var cachedInstallationIdentifier: String? = null

    suspend fun getInstallationIdentifier(context: Context): String =
        cachedInstallationIdentifier ?: mutex.withLock {
            cachedInstallationIdentifier ?: retrieveInstallationIdentifier(context).also {
                cachedInstallationIdentifier = it
            }
        }

    private suspend fun retrieveInstallationIdentifier(context: Context): String {
        val idFile = File(context.noBackupFilesDir, ID_FILE_NAME)
        readIdFile(idFile)?.let {
            Log.i(TAG, "Using existing installation identifier: $it")
            return it
        }

        val legacyId = context.dataStore.data.map { it[LEGACY_INSTALLATION_IDENTIFIER_KEY] }.firstOrNull()
        val id = legacyId ?: UUID.randomUUID().toString()
        withContext(Dispatchers.IO) { idFile.writeText(id) }
        if (legacyId != null) {
            context.dataStore.edit { it.remove(LEGACY_INSTALLATION_IDENTIFIER_KEY) }
            Log.i(TAG, "Migrated installation identifier out of backed-up storage: $id")
        } else {
            Log.i(TAG, "Generated new installation identifier: $id")
        }
        return id
    }

    private suspend fun readIdFile(file: File): String? = withContext(Dispatchers.IO) {
        try {
            file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            Log.e(TAG, "Could not read installation identifier", e)
            null
        }
    }
}
