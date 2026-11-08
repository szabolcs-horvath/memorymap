package com.szabolcshorvath.memorymap

import android.content.Context
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.data.MemoryRepository
import com.szabolcshorvath.memorymap.data.PresetRepository

/**
 * Builds the app's data classes, once per process. ViewModels get what they need from here through their `Factory`,
 * so a test can build a ViewModel with its own (for example a repository on an in-memory database) instead.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    /** One database for the whole process. A restore replaces its contents in place, so it is never swapped. */
    val database: MemoryMapDatabase by lazy { MemoryMapDatabase.getDatabase(app) }

    val memories: MemoryRepository by lazy { MemoryRepository(app, database) }

    val presets: PresetRepository by lazy { PresetRepository(app, database) }

    val backupManager: BackupManager by lazy { BackupManager(app) }
}

/** The process's [AppContainer]. */
val Context.appContainer: AppContainer get() = (applicationContext as MemoryMapApplication).container
