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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.ui.memory.MemoryActions
import com.szabolcshorvath.memorymap.ui.memory.MemoryContent
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.util.ConfirmDialog
import com.szabolcshorvath.memorymap.util.RelinkAction
import kotlinx.coroutines.launch
import com.szabolcshorvath.memorymap.data.MemoryFragment as MemoryFragmentEntity

/** One memory's page in the memory pager ([MemoryContent]). */
class MemoryFragment : Fragment(), MemoryActions {
    private val commonViewModel: CommonViewModel by activityViewModels { CommonViewModel.Factory }
    private val viewModel: MemoryFragmentViewModel by viewModels { MemoryFragmentViewModel.Factory }
    private var memoryId: Int = -1
    private var memoryFragmentListener: MemoryFragmentListener? = null

    private val relinkAction = RelinkAction(this) { viewModel.relink() }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryId = requireArguments().getInt(MemoryFragmentViewModel.ARG_MEMORY_ID)
        ConfirmDialog.listen(this, DELETE_REQUEST) { deleteMemory() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            // Compose saves the page's state (its scroll position) under this id.
            id = R.id.memory_content
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val showFragmentMarkers by commonViewModel.showFragmentsEnabled.collectAsStateWithLifecycle()
                MemoryMapTheme { MemoryContent(state, showFragmentMarkers, this@MemoryFragment) }
            }
        }

    override fun back() {
        memoryFragmentListener?.onBackFromMemory()
    }

    override fun edit() {
        memoryFragmentListener?.onEditMemory(memoryId)
    }

    override fun delete() {
        ConfirmDialog.show(
            childFragmentManager,
            DELETE_REQUEST,
            title = getString(R.string.delete_memory),
            message = getString(R.string.delete_memory_message),
            confirm = getString(R.string.delete),
            cancel = getString(R.string.cancel)
        )
    }

    override fun showOnMap() {
        val group = viewModel.uiState.value.group ?: return
        memoryFragmentListener?.onNavigateToMap(group.latitude, group.longitude, group.id)
    }

    override fun showOnTimeline() {
        memoryFragmentListener?.onNavigateToTimeline(memoryId)
    }

    override fun showFragmentOnMap(fragment: MemoryFragmentEntity) {
        memoryFragmentListener?.onNavigateToMap(fragment.latitude, fragment.longitude, fragment.groupId)
    }

    /** Opens the media viewer at [item], or for an item that can't be re-linked, the sheet that fixes it. */
    override fun openMedia(item: MediaItem) {
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.isMissing(item)) {
                MissingMediaSheet.show(childFragmentManager, item)
            } else {
                memoryFragmentListener?.onMediaClick(item.groupId, item.id)
            }
        }
    }

    override fun relinkMedia() = relinkAction.start()

    override fun saveMediaOrder(items: List<MediaItem>) = viewModel.saveMediaOrder(items)

    override fun saveFragmentsOrder(fragments: List<MemoryFragmentEntity>) = viewModel.saveFragmentsOrder(fragments)

    private fun deleteMemory() {
        lifecycleScope.launch {
            val deleted = viewModel.delete() ?: return@launch
            memoryFragmentListener?.onMemoryDeleted(deleted)
            memoryFragmentListener?.onBackFromMemory()
        }
    }

    companion object {
        private const val DELETE_REQUEST = "delete_memory"

        @JvmStatic
        fun newInstance(memoryId: Int) =
            MemoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(MemoryFragmentViewModel.ARG_MEMORY_ID, memoryId)
                }
            }
    }
}
