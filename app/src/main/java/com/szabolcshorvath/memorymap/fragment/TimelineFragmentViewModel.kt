package com.szabolcshorvath.memorymap.fragment

import androidx.lifecycle.ViewModel
import com.szabolcshorvath.memorymap.data.MemoryGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TimelineFragmentViewModel : ViewModel() {
    var pendingScrollMemoryId: Int? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun filterGroups(groups: List<MemoryGroup>, query: String): List<MemoryGroup> {
        if (query.isBlank()) return groups
        val normalizedQuery = query.trim()
        return groups.filter { group ->
            group.title.contains(normalizedQuery, ignoreCase = true) ||
                (group.description?.contains(normalizedQuery, ignoreCase = true) == true) ||
                (group.placeName?.contains(normalizedQuery, ignoreCase = true) == true) ||
                (group.address?.contains(normalizedQuery, ignoreCase = true) == true)
        }
    }
}
