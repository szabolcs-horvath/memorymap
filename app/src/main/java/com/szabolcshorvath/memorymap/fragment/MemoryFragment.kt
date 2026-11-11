package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.ui.memory.MemoryNavigation
import com.szabolcshorvath.memorymap.ui.memory.MemoryRoute
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme

/** Hosts one memory's page in the memory pager ([MemoryRoute]) until the app's shell is Compose. */
class MemoryFragment : Fragment(), MemoryNavigation {
    private val commonViewModel: CommonViewModel by activityViewModels { CommonViewModel.Factory }
    private val viewModel: MemoryFragmentViewModel by viewModels { MemoryFragmentViewModel.Factory }
    private var memoryFragmentListener: MemoryFragmentListener? = null

    interface MemoryFragmentListener {
        /** Opens the media viewer on memory [memoryId]'s media, at item [mediaItemId]. */
        fun onMediaClick(memoryId: Int, mediaItemId: Int)
        fun onBackFromMemory()
        fun onNavigateToTimeline(memoryId: Int)
        fun onNavigateToMap(lat: Double, lng: Double, id: Int)
        fun onMemoryDeleted(deleted: MemoryGroupWithMedia)
        fun onEditMemory(memoryId: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MemoryFragmentListener) {
            memoryFragmentListener = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        memoryFragmentListener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose saves the page's state (its scroll position, an open question) under this id.
            id = R.id.memory_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MemoryMapTheme { MemoryRoute(viewModel, commonViewModel, this@MemoryFragment) }
            }
        }

    override fun back() {
        memoryFragmentListener?.onBackFromMemory()
    }

    override fun edit(memoryId: Int) {
        memoryFragmentListener?.onEditMemory(memoryId)
    }

    override fun showOnMap(lat: Double, lng: Double, memoryId: Int) {
        memoryFragmentListener?.onNavigateToMap(lat, lng, memoryId)
    }

    override fun showOnTimeline(memoryId: Int) {
        memoryFragmentListener?.onNavigateToTimeline(memoryId)
    }

    override fun openViewer(item: MediaItem) {
        memoryFragmentListener?.onMediaClick(item.groupId, item.id)
    }

    override fun openMissing(item: MediaItem) = MissingMediaSheet.show(childFragmentManager, item)

    override fun onDeleted(deleted: MemoryGroupWithMedia) {
        memoryFragmentListener?.onMemoryDeleted(deleted)
    }

    companion object {
        @JvmStatic
        fun newInstance(memoryId: Int) =
            MemoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(MemoryFragmentViewModel.ARG_MEMORY_ID, memoryId)
                }
            }
    }
}
