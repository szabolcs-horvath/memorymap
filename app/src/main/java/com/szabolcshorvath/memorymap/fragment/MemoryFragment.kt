package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.adapter.MediaAdapter
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentAdapter
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.databinding.FragmentMemoryBinding
import com.szabolcshorvath.memorymap.util.ConfirmDialog
import com.szabolcshorvath.memorymap.util.Coordinates
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.MediaRelinker
import com.szabolcshorvath.memorymap.util.RelinkAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import com.szabolcshorvath.memorymap.data.MemoryFragment as MemoryFragmentEntity

class MemoryFragment : Fragment() {
    private var _binding: FragmentMemoryBinding? = null
    private val binding get() = _binding!!
    private val commonViewModel: CommonViewModel by activityViewModels()
    private val viewModel: MemoryFragmentViewModel by viewModels()
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var fragmentsAdapter: MemoryFragmentAdapter
    private var memoryId: Int = -1
    private var memoryFragmentListener: MemoryFragmentListener? = null

    private val relinkAction = RelinkAction(this) { triggerBackgroundRelink() }

    private fun triggerBackgroundRelink() {
        val ctx = context
        val binding = _binding
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value
        if (ctx == null || binding == null || viewLifecycleOwner == null) return
        binding.btRelinkMedia.isEnabled = false
        binding.progressRelinkMedia.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                MediaRelinker.relink(ctx) { status -> _binding?.tvMediaWarningText?.text = status }
            } finally {
                val currentBinding = _binding
                if (currentBinding != null) {
                    currentBinding.progressRelinkMedia.visibility = View.GONE
                    currentBinding.btRelinkMedia.isEnabled = true
                }
            }
        }
    }

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
        arguments?.let {
            memoryId = it.getInt(MemoryFragmentViewModel.ARG_MEMORY_ID)
        }
        ConfirmDialog.listen(this, DELETE_REQUEST) { deleteMemory() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewLifecycleOwner = viewLifecycleOwner
        val context = context ?: return

        setupRecyclerViews()

        // Restore lists in adapter if they are already in ViewModel (on rotation)
        if (viewModel.fragmentItems.isNotEmpty()) {
            binding.fragmentsSection.visibility = View.VISIBLE
            fragmentsAdapter.updateData(viewModel.fragmentItems)
            binding.fragmentsExpandedContent.visibility =
                if (viewModel.isFragmentsExpanded) View.VISIBLE else View.GONE
            binding.fragmentsChevron.rotation =
                if (viewModel.isFragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION
        }
        if (viewModel.mediaItems.isNotEmpty()) {
            mediaAdapter.updateData(viewModel.mediaItems)
        }

        // Each fragment instance observes its own specific ID through a private flow
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.currentDeviceId == null) {
                viewModel.currentDeviceId = InstallationIdentifier.getInstallationIdentifier(context)
            }
            mediaAdapter.updateCurrentDeviceId(viewModel.currentDeviceId)

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.groupWithMedia.collect { it?.let { displayDetails(it) } }
                }
                launch {
                    commonViewModel.showFragmentsEnabled.collect { fragmentsAdapter.showFragmentsEnabled = it }
                }
            }
        }

        binding.btEdit.setOnClickListener {
            memoryFragmentListener?.onEditMemory(memoryId)
        }

        binding.btDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        binding.fragmentsHeader.setOnClickListener {
            toggleFragments()
        }
    }

    private fun setupRecyclerViews() {
        mediaAdapter = MediaAdapter(viewModel.currentDeviceId, ::onMediaTapped)
        mediaAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.rvMedia.scrollToPosition(positionStart)
            }
        })
        binding.rvMedia.layoutManager = GridLayoutManager(context, MEDIA_GRID_SPAN_COUNT)
        binding.rvMedia.adapter = mediaAdapter

        binding.btRelinkMedia.setOnClickListener { relinkAction.start() }

        fragmentsAdapter = MemoryFragmentAdapter { fragment ->
            memoryFragmentListener?.onNavigateToMap(fragment.latitude, fragment.longitude, fragment.groupId)
        }
        binding.rvFragments.layoutManager = LinearLayoutManager(context)
        binding.rvFragments.adapter = fragmentsAdapter

        setupMediaTouchHelper()
        setupFragmentsTouchHelper()
    }

    /** Opens the media viewer at [position], or for an item that can't be re-linked, the sheet that fixes it. */
    private fun onMediaTapped(position: Int) {
        val context = context
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value
        val item = viewModel.mediaItems.getOrNull(position)
        if (context == null || viewLifecycleOwner == null || item == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val missing = withContext(Dispatchers.IO) { LocalMediaUtil.hasMissingMedia(context, listOf(item)) }
            if (missing) {
                MissingMediaSheet.show(childFragmentManager, item)
            } else {
                memoryFragmentListener?.onMediaClick(item.groupId, item.id)
            }
        }
    }

    private fun setupMediaTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            private var initialOrder: List<Int>? = null

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    initialOrder = viewModel.mediaItems.map { it.id }
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                val mutableMedia = viewModel.mediaItems.toMutableList()
                if (fromPos < toPos) {
                    for (i in fromPos until toPos) {
                        Collections.swap(mutableMedia, i, i + 1)
                        mediaAdapter.moveItem(i, i + 1)
                    }
                } else {
                    for (i in fromPos downTo toPos + 1) {
                        Collections.swap(mutableMedia, i, i - 1)
                        mediaAdapter.moveItem(i, i - 1)
                    }
                }
                viewModel.mediaItems = mutableMedia
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentOrder = viewModel.mediaItems.map { it.id }
                if (initialOrder != null && initialOrder != currentOrder) {
                    saveNewMediaOrder()
                }
                initialOrder = null
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvMedia)
    }

    private fun setupFragmentsTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            private var initialOrder: List<Int>? = null

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    initialOrder = viewModel.fragmentItems.map { it.id }
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                val mutableFragments = viewModel.fragmentItems.toMutableList()
                if (fromPos < toPos) {
                    for (i in fromPos until toPos) {
                        Collections.swap(mutableFragments, i, i + 1)
                        fragmentsAdapter.moveItem(i, i + 1)
                    }
                } else {
                    for (i in fromPos downTo toPos + 1) {
                        Collections.swap(mutableFragments, i, i - 1)
                        fragmentsAdapter.moveItem(i, i - 1)
                    }
                }
                viewModel.fragmentItems = mutableFragments
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentOrder = viewModel.fragmentItems.map { it.id }
                if (initialOrder != null && initialOrder != currentOrder) {
                    saveNewFragmentsOrder()
                }
                initialOrder = null
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvFragments)
    }

    private fun toggleFragments() {
        viewModel.isFragmentsExpanded = !viewModel.isFragmentsExpanded
        binding.fragmentsExpandedContent.visibility = if (viewModel.isFragmentsExpanded) View.VISIBLE else View.GONE
        binding.fragmentsChevron.animate()
            .rotation(if (viewModel.isFragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION)
            .start()
    }

    private fun saveNewMediaOrder() {
        viewModel.saveNewMediaOrder(viewModel.mediaItems, commonViewModel.getDb())
    }

    private fun saveNewFragmentsOrder() {
        viewModel.saveNewFragmentsOrder(viewModel.fragmentItems, commonViewModel.getDb())
    }

    private suspend fun displayDetails(data: MemoryGroupWithMedia) {
        displayMemoryGroupDetails(data)
        displayMemoryFragments(data)
        displayMediaItems(data)
    }

    private fun displayMemoryGroupDetails(data: MemoryGroupWithMedia) {
        val binding = _binding ?: return
        val group = data.group
        binding.tvTitle.text = group.title

        if (!group.description.isNullOrEmpty()) {
            binding.tvDescription.text = group.description
            binding.tvDescription.visibility = View.VISIBLE
        } else {
            binding.tvDescription.visibility = View.GONE
        }

        binding.tvDate.text = group.getFormattedDate(binding.root.context)

        val locationString = if (!group.placeName.isNullOrEmpty()) {
            if (!group.address.isNullOrEmpty()) {
                "${group.placeName}\n${group.address}"
            } else {
                group.placeName
            }
        } else if (!group.address.isNullOrEmpty()) {
            group.address
        } else {
            Coordinates.format(binding.root.context, group.latitude, group.longitude)
        }
        binding.tvLocation.text = locationString

        binding.divider.dividerColor = group.markerColor()

        binding.btShowOnTimeline.setOnClickListener {
            memoryFragmentListener?.onNavigateToTimeline(group.id)
        }

        binding.btShowOnMap.setOnClickListener {
            memoryFragmentListener?.onNavigateToMap(group.latitude, group.longitude, group.id)
        }
    }

    private fun displayMemoryFragments(data: MemoryGroupWithMedia) {
        val binding = _binding ?: return
        viewModel.fragmentItems = data.fragments.sortedWith(MemoryFragmentEntity.DISPLAY_ORDER)

        if (viewModel.fragmentItems.isEmpty()) {
            binding.fragmentsSection.visibility = View.GONE
        } else {
            binding.fragmentsSection.visibility = View.VISIBLE
            fragmentsAdapter.updateData(viewModel.fragmentItems)
            binding.fragmentsExpandedContent.visibility =
                if (viewModel.isFragmentsExpanded) View.VISIBLE else View.GONE
            binding.fragmentsChevron.rotation =
                if (viewModel.isFragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION
        }
    }

    private suspend fun displayMediaItems(data: MemoryGroupWithMedia) {
        val binding = _binding ?: return
        val context = context ?: return
        viewModel.mediaItems = data.mediaItems.sortedWith(MediaItem.DISPLAY_ORDER)

        val missingIds = withContext(Dispatchers.IO) {
            viewModel.mediaItems.filter { LocalMediaUtil.hasMissingMedia(context, listOf(it)) }.mapTo(HashSet()) { it.id }
        }
        val hasMissingMedia = missingIds.isNotEmpty()
        mediaAdapter.updateMissing(missingIds)
        binding.cardMediaWarning.visibility = if (hasMissingMedia) View.VISIBLE else View.GONE
        if (hasMissingMedia) {
            binding.tvMediaWarningText.text =
                "Some media items could not be loaded. They may have been deleted or are from a different device."
        }
        // A tile that can't be loaded shows the photo's stored thumbnail, if it has one. Also when the check above
        // doesn't know yet: a photo deleted from the gallery while the app runs fails only when it loads.
        val signatures = viewModel.mediaItems.map { it.mediaSignature }.filter { it.isNotEmpty() }
        val thumbnails = withContext(Dispatchers.IO) { commonViewModel.getDb().mediaThumbnailDao().getThumbnails(signatures) }
        mediaAdapter.updateThumbnails(thumbnails.associate { it.signature to it.jpeg })
        mediaAdapter.updateData(viewModel.mediaItems)
    }

    private fun showDeleteConfirmationDialog() {
        ConfirmDialog.show(
            childFragmentManager,
            DELETE_REQUEST,
            title = "Delete Memory",
            message = "Are you sure you want to delete this memory?",
            confirm = "Delete",
            cancel = "Cancel"
        )
    }

    private fun deleteMemory() {
        val viewLifecycleOwner = viewLifecycleOwner
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val groupWithMedia = commonViewModel.getMemoryGroupDao().getGroupWithMedia(memoryId) ?: return@launch
            commonViewModel.getMemoryGroupDao().deleteGroup(groupWithMedia.group)

            withContext(Dispatchers.Main) {
                memoryFragmentListener?.onMemoryDeleted(groupWithMedia)
                memoryFragmentListener?.onBackFromMemory()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MEDIA_GRID_SPAN_COUNT = 3
        private const val DELETE_REQUEST = "delete_memory"
        private const val FACING_RIGHT_ROTATION = 0f
        private const val FACING_DOWN_ROTATION = 90f

        @JvmStatic
        fun newInstance(memoryId: Int) =
            MemoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(MemoryFragmentViewModel.ARG_MEMORY_ID, memoryId)
                }
            }
    }
}
