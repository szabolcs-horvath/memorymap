package com.szabolcshorvath.memorymap.data

import android.content.Context

class CommonRepository private constructor(context: Context) {
    // One database instance for the whole process. A restore replaces its contents in place, so it is never swapped.
    private val db = MemoryMapDatabase.getDatabase(context)

    val allGroups = db.memoryGroupDao().getAllGroupsFlow()
    val allFragments = db.memoryGroupDao().getAllFragmentsFlow()
    val allPresets = db.hsvPresetDao().getAllPresetsFlow()

    fun getDb() = db
    fun getMemoryGroupDao() = db.memoryGroupDao()
    fun getHSVPresetDao() = db.hsvPresetDao()

    fun getGroupWithMedia(id: Int) = db.memoryGroupDao().getGroupWithMediaFlow(id)

    companion object {
        @Volatile
        private var INSTANCE: CommonRepository? = null

        fun getInstance(context: Context): CommonRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CommonRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
