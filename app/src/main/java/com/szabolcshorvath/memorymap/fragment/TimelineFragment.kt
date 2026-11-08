package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.ui.timeline.TimelineContent

/** The Timeline tab, built with Compose ([TimelineContent]). */
class TimelineFragment : Fragment() {

    private var timelineListener: TimelineListener? = null

    private val viewModel: TimelineFragmentViewModel by viewModels { TimelineFragmentViewModel.Factory }

    interface TimelineListener {
        fun onMemoryClicked(id: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is TimelineListener) {
            timelineListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose keeps its saved state (the search, the scroll position) under the view's id.
            id = R.id.timeline_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MemoryMapTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    TimelineContent(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onMemoryClick = { id -> timelineListener?.onMemoryClicked(id) },
                        onScrollHandled = viewModel::onScrollHandled,
                    )
                }
            }
        }

    /**
     * Scrolls to memory [memoryId] and flashes it. The request is kept in the ViewModel until the tab is shown and its
     * list contains the memory, so it can be made while the tab is hidden, with or without a view, or still being shown.
     */
    fun requestScrollToAndFlash(memoryId: Int) = viewModel.requestScrollTo(memoryId)

    override fun onDetach() {
        super.onDetach()
        timelineListener = null
    }

    companion object {
        const val TAG = "TimelineFragment"
    }
}
