package com.szabolcshorvath.memorymap.ui.memory

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.szabolcshorvath.memorymap.data.CommonViewModel

private const val TAG_PAGER = "memoryViewPager"

/**
 * The memories as pages, newest first, opened on memory [initialMemoryId]: swiping moves to the next or previous one.
 * Each page has its own ViewModel ([MemoryViewModel], kept while the pager is open). The pager keeps its page
 * by memory, so a memory added or deleted meanwhile doesn't move the one shown.
 */
@Composable
fun MemoryPagerRoute(initialMemoryId: Int, common: CommonViewModel, navigation: MemoryNavigation) {
    val groups by common.allGroups.collectAsStateWithLifecycle()
    val ids = remember(groups) { groups.sortedByDescending { it.startDate }.map { it.id } }
    // Until the memories are loaded there is nothing to show; the pager starts once they are.
    if (ids.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = ids.indexOf(initialMemoryId).coerceAtLeast(0)) { ids.size }
    HorizontalPager(
        state = pagerState,
        // The UI tests find the pager by this name, the id the View pager had.
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag(TAG_PAGER),
        key = { ids[it] },
    ) { page ->
        val memoryId = ids[page]
        val viewModel: MemoryViewModel = viewModel(key = "memory_$memoryId", factory = MemoryViewModel.factory(memoryId))
        MemoryRoute(viewModel, common, navigation)
    }
}
