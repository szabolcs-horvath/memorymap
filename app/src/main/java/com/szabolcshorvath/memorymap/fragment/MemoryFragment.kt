package com.szabolcshorvath.memorymap.fragment

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.adapter.MediaAdapter
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentAdapter
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.MemoryMapViewModel
import com.szabolcshorvath.memorymap.databinding.FragmentMemoryBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import com.szabolcshorvath.memorymap.data.MemoryFragment as MemoryFragmentEntity

class MemoryFragment : Fragment() {
    private var _binding: FragmentMemoryBinding? = null
    private val binding get() = _binding!!
    private val memoryMapViewModel: MemoryMapViewModel by activityViewModels()
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var fragmentsAdapter: MemoryFragmentAdapter
    private var memoryId: Int = -1
    private var mediaItems: MutableList<MediaItem> = mutableListOf()
    private var fragmentItems: MutableList<MemoryFragmentEntity> = mutableListOf()
    private var listener: MemoryFragmentListener? = null
    private var currentDeviceId: String? = null
    private lateinit var backupManager: BackupManager
    private var isFragmentsExpanded = true
    private var mediaOrderJob: Job? = null
    private var fragmentsOrderJob: Job? = null

    interface MemoryFragmentListener {
        fun onMediaClick(mediaItems: ArrayList<Pair<String, String>>, startPosition: Int)
        fun onBackFromMemory()
        fun onNavigateToTimeline(memoryId: Int)
        fun onNavigateToMap(lat: Double, lng: Double, id: Int)
        fun onMemoryDeleted(memoryGroup: MemoryGroup, mediaItems: List<MediaItem>)
        fun onEditMemory(memoryId: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MemoryFragmentListener) {
            listener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            memoryId = it.getInt(ARG_MEMORY_ID)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backupManager = BackupManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            currentDeviceId = InstallationIdentifier.getInstallationIdentifier(requireContext())
            setupRecyclerViews()
        }

        // Each fragment instance observes its own specific ID through a private flow
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                memoryMapViewModel.getGroupWithMedia(memoryId).collect { data ->
                    data?.let { displayDetails(it) }
                }
            }
        }

        binding.editButton.setOnClickListener {
            listener?.onEditMemory(memoryId)
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        binding.fragmentsHeader.setOnClickListener {
            toggleFragments()
        }
    }

    private fun setupRecyclerViews() {
        mediaAdapter = MediaAdapter(currentDeviceId) { position ->
            val mediaPairs = ArrayList(mediaItems.map { it.uri to it.type.name })
            listener?.onMediaClick(mediaPairs, position)
        }
        mediaAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.mediaRecyclerView.scrollToPosition(positionStart)
            }
        })
        binding.mediaRecyclerView.layoutManager = GridLayoutManager(requireContext(), MEDIA_GRID_SPAN_COUNT)
        binding.mediaRecyclerView.adapter = mediaAdapter

        fragmentsAdapter = MemoryFragmentAdapter { fragment ->
            listener?.onNavigateToMap(fragment.latitude, fragment.longitude, fragment.groupId)
        }
        binding.fragmentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.fragmentsRecyclerView.adapter = fragmentsAdapter

        setupMediaTouchHelper()
        setupFragmentsTouchHelper()
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
                    initialOrder = mediaItems.map { it.id }
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                if (fromPos < toPos) {
                    for (i in fromPos until toPos) {
                        Collections.swap(mediaItems, i, i + 1)
                        mediaAdapter.moveItem(i, i + 1)
                    }
                } else {
                    for (i in fromPos downTo toPos + 1) {
                        Collections.swap(mediaItems, i, i - 1)
                        mediaAdapter.moveItem(i, i - 1)
                    }
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentOrder = mediaItems.map { it.id }
                if (initialOrder != null && initialOrder != currentOrder) {
                    saveNewMediaOrder()
                }
                initialOrder = null
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.mediaRecyclerView)
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
                    initialOrder = fragmentItems.map { it.id }
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                if (fromPos < toPos) {
                    for (i in fromPos until toPos) {
                        Collections.swap(fragmentItems, i, i + 1)
                        fragmentsAdapter.moveItem(i, i + 1)
                    }
                } else {
                    for (i in fromPos downTo toPos + 1) {
                        Collections.swap(fragmentItems, i, i - 1)
                        fragmentsAdapter.moveItem(i, i - 1)
                    }
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentOrder = fragmentItems.map { it.id }
                if (initialOrder != null && initialOrder != currentOrder) {
                    saveNewFragmentsOrder()
                }
                initialOrder = null
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.fragmentsRecyclerView)
    }

    private fun toggleFragments() {
        isFragmentsExpanded = !isFragmentsExpanded
        binding.fragmentsExpandedContent.visibility = if (isFragmentsExpanded) View.VISIBLE else View.GONE
        binding.fragmentsChevron.animate()
            .rotation(if (isFragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION)
            .start()
    }

    private fun saveNewMediaOrder() {
        mediaOrderJob?.cancel()
        val updatedItems = mediaItems.mapIndexed { index, item ->
            item.copy(order = index + 1)
        }
        mediaOrderJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            memoryMapViewModel.getMemoryGroupDao().updateMediaItems(updatedItems)
            backupManager.triggerAutomaticBackup(memoryMapViewModel.getDb())
        }
    }

    private fun saveNewFragmentsOrder() {
        fragmentsOrderJob?.cancel()
        val updatedFragments = fragmentItems.mapIndexed { index, item ->
            item.copy(order = index + 1)
        }
        fragmentsOrderJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            memoryMapViewModel.getMemoryGroupDao().updateFragments(updatedFragments)
            backupManager.triggerAutomaticBackup(memoryMapViewModel.getDb())
        }
    }

    private suspend fun displayDetails(data: MemoryGroupWithMedia) {
        displayMemoryGroupDetails(data)
        displayMemoryFragments(data)
        displayMediaItems(data)
    }

    private fun displayMemoryGroupDetails(data: MemoryGroupWithMedia) {
        val binding = _binding ?: return
        val group = data.group
        binding.titleText.text = group.title

        if (!group.description.isNullOrEmpty()) {
            binding.descriptionText.text = group.description
            binding.descriptionText.visibility = View.VISIBLE
        } else {
            binding.descriptionText.visibility = View.GONE
        }

        binding.dateText.text = group.getFormattedDate()

        val locationString = if (!group.placeName.isNullOrEmpty()) {
            if (!group.address.isNullOrEmpty()) {
                "${group.placeName}\n${group.address}"
            } else {
                group.placeName
            }
        } else if (!group.address.isNullOrEmpty()) {
            group.address
        } else {
            "${group.latitude}, ${group.longitude}"
        }
        binding.locationText.text = locationString

        binding.divider.setBackgroundColor(
            ColorUtil.hsvToColor(
                group.markerHue ?: DEFAULT_MARKER_HUE,
                group.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                group.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
            )
        )

        binding.showOnTimelineButton.setOnClickListener {
            listener?.onNavigateToTimeline(group.id)
        }

        binding.showOnMapButton.setOnClickListener {
            listener?.onNavigateToMap(group.latitude, group.longitude, group.id)
        }
    }

    private fun displayMemoryFragments(data: MemoryGroupWithMedia) {
        val binding = _binding ?: return
        fragmentItems = data.fragments.sortedWith { a, b ->
            when {
                a.order != null && b.order != null -> a.order.compareTo(b.order)
                a.order != null -> -1
                b.order != null -> 1
                else -> {
                    val dateA = a.startDate
                    val dateB = b.startDate
                    when {
                        dateA != null && dateB != null -> dateA.compareTo(dateB)
                        dateA != null -> -1
                        dateB != null -> 1
                        else -> 0
                    }
                }
            }
        }.toMutableList()

        if (fragmentItems.isEmpty()) {
            binding.fragmentsSection.visibility = View.GONE
        } else {
            binding.fragmentsSection.visibility = View.VISIBLE
            fragmentsAdapter.updateData(fragmentItems.toList())
            binding.fragmentsExpandedContent.visibility =
                if (isFragmentsExpanded) View.VISIBLE else View.GONE
            binding.fragmentsChevron.rotation =
                if (isFragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION
        }
    }

    private suspend fun displayMediaItems(data: MemoryGroupWithMedia) {
        val binding = _binding ?: return
        val context = context ?: return
        mediaItems = data.mediaItems.sortedWith { a, b ->
            when {
                a.order != null && b.order != null -> a.order.compareTo(b.order)
                a.order != null -> -1
                b.order != null -> 1
                else -> b.dateTaken.compareTo(a.dateTaken)
            }
        }.toMutableList()

        val hasMissingMedia = withContext(Dispatchers.IO) {
            LocalMediaUtil.hasMissingMedia(context, mediaItems)
        }
        binding.mediaWarningText.visibility = if (hasMissingMedia) View.VISIBLE else View.GONE
        mediaAdapter.updateData(mediaItems.toList())
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Memory")
            .setMessage("Are you sure you want to delete this memory?")
            .setPositiveButton("Delete") { _, _ ->
                deleteMemory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMemory() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val groupWithMedia = memoryMapViewModel.getMemoryGroupDao().getGroupWithMedia(memoryId) ?: return@launch
            memoryMapViewModel.getMemoryGroupDao().deleteGroup(groupWithMedia.group)

            withContext(Dispatchers.Main) {
                listener?.onMemoryDeleted(groupWithMedia.group, groupWithMedia.mediaItems)
                listener?.onBackFromMemory()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_MEMORY_ID = "memory_id"
        private const val MEDIA_GRID_SPAN_COUNT = 3
        private const val FACING_RIGHT_ROTATION = 0f
        private const val FACING_DOWN_ROTATION = 90f

        @JvmStatic
        fun newInstance(memoryId: Int) =
            MemoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MEMORY_ID, memoryId)
                }
            }
    }
}
