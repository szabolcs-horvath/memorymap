package com.szabolcshorvath.memorymap.data

import android.content.Context
import com.szabolcshorvath.memorymap.backup.AutomaticBackups
import com.szabolcshorvath.memorymap.util.ColorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Reads and writes the colour presets. Every change schedules an automatic backup. Writes may be called from the main thread. */
class PresetRepository(private val context: Context, db: MemoryMapDatabase) {
    private val dao = db.hsvPresetDao()

    val allPresets: Flow<List<HSVPreset>> = dao.getAllPresetsFlow()

    /** Stores [preset], replacing the one with its id. */
    suspend fun save(preset: HSVPreset) = withContext(Dispatchers.IO) {
        dao.insertPresets(listOf(preset))
        AutomaticBackups.schedule(context)
    }

    /** Adds [preset] and returns its id, or null if it wasn't added. */
    suspend fun add(preset: HSVPreset): Int? = withContext(Dispatchers.IO) {
        val id = dao.insertPresets(listOf(preset.copy(id = 0))).firstOrNull()?.toInt()
        if (id != null) AutomaticBackups.schedule(context)
        id
    }

    suspend fun delete(preset: HSVPreset) = withContext(Dispatchers.IO) {
        dao.deletePreset(preset)
        AutomaticBackups.schedule(context)
    }

    /** Stores the presets' order fields as they are. */
    suspend fun saveOrder(presets: List<HSVPreset>) = withContext(Dispatchers.IO) {
        dao.updatePresets(presets)
        AutomaticBackups.schedule(context)
    }

    /** Adds the default presets when there are none, as on the first start. Not a change of the user's, so no backup. */
    suspend fun addDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.getCount() > 0) return@withContext
        val defaults = ColorUtil.DEFAULT_HSV_PRESETS.mapIndexed { index, hsv ->
            HSVPreset(hue = hsv[0], saturation = hsv[1], brightness = hsv[2], order = index)
        }
        dao.insertPresets(defaults)
    }
}
