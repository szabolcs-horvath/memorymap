package com.szabolcshorvath.memorymap.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** What the Timeline tab shows. */
data class TimelineUiState(
    /** The search as typed. */
    val query: String = "",
    /** The memories that match [query], newest first, one entry per day. Null until the memories are loaded. */
    val days: List<TimelineDay>? = null,
    /** A memory to scroll to and flash once the tab is shown and the list has it (Show on Timeline). */
    val pendingScrollMemoryId: Int? = null,
    /** The list should go back to its top (the Timeline tab tapped again while shown). */
    val scrollToTopRequested: Boolean = false,
)

/** The memories that start on [date], in the timeline's order. */
data class TimelineDay(val date: LocalDate, val memories: List<MemoryGroup>)

class TimelineViewModel(memories: MemoryRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val pendingScrollMemoryId = MutableStateFlow<Int?>(null)
    private val scrollToTopRequested = MutableStateFlow(false)

    val uiState: StateFlow<TimelineUiState> =
        combine(memories.allGroups, query, pendingScrollMemoryId, scrollToTopRequested) { groups, query, pendingScroll, toTop ->
            TimelineUiState(query, groupByDay(filterGroups(groups, query)), pendingScroll, toTop)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MILLIS), TimelineUiState())

    fun onQueryChange(query: String) {
        this.query.value = query
    }

    /** Asks for memory [memoryId] to be scrolled to and flashed. A newer request replaces one not handled yet. */
    fun requestScrollTo(memoryId: Int) {
        pendingScrollMemoryId.value = memoryId
    }

    /** The screen scrolled to memory [memoryId]; a newer request made meanwhile stays pending. */
    fun onScrollHandled(memoryId: Int) {
        pendingScrollMemoryId.update { if (it == memoryId) null else it }
    }

    /** Asks for the list to go back to its top. */
    fun requestScrollToTop() {
        scrollToTopRequested.value = true
    }

    fun onScrollToTopHandled() {
        scrollToTopRequested.value = false
    }

    companion object {
        private const val STATE_FLOW_TIMEOUT_MILLIS = 5000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TimelineViewModel(this[APPLICATION_KEY]!!.appContainer.memories) }
        }

        /** The memories whose title, description, place name or address contain [query], ignoring case. */
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

        /** [groups] newest first, grouped by the day they start on. */
        fun groupByDay(groups: List<MemoryGroup>): List<TimelineDay> = groups
            .sortedByDescending { it.startDate }
            .groupBy { it.startDate.toLocalDate() }
            .map { (date, memories) -> TimelineDay(date, memories) }
    }
}
