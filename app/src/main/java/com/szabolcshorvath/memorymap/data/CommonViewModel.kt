package com.szabolcshorvath.memorymap.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class CommonViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CommonRepository.getInstance(application)

    val allGroups = repository.allGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())
    val allPresets = repository.allPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), emptyList())

    fun getDb() = repository.getDb()
    fun getMemoryGroupDao() = repository.getMemoryGroupDao()
    fun getHSVPresetDao() = repository.getHSVPresetDao()

    fun refreshDatabase() {
        repository.refreshDatabase(getApplication())
    }

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L
    }
}
