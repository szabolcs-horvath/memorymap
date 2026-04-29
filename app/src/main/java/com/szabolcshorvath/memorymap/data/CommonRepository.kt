package com.szabolcshorvath.memorymap.data

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class CommonRepository private constructor(context: Context) {
    private val _dbFlow = MutableStateFlow(MemoryMapDatabase.getDatabase(context))

    @OptIn(ExperimentalCoroutinesApi::class)
    val allGroups = _dbFlow.flatMapLatest { it.memoryGroupDao().getAllGroupsFlow() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allFragments = _dbFlow.flatMapLatest { it.memoryGroupDao().getAllFragmentsFlow() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPresets = _dbFlow.flatMapLatest { it.hsvPresetDao().getAllPresetsFlow() }

    fun getDb() = _dbFlow.value
    fun getMemoryGroupDao() = _dbFlow.value.memoryGroupDao()
    fun getHSVPresetDao() = _dbFlow.value.hsvPresetDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getGroupWithMedia(id: Int) = _dbFlow.flatMapLatest {
        it.memoryGroupDao().getGroupWithMediaFlow(id)
    }

    fun refreshDatabase(context: Context) {
        _dbFlow.value = MemoryMapDatabase.getDatabase(context)
    }

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
