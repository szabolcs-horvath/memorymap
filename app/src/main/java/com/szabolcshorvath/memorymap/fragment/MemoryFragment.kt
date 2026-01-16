package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.szabolcshorvath.memorymap.adapter.MediaAdapter
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentMemoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MemoryFragment : Fragment() {

    private var _binding: FragmentMemoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: MediaAdapter
    private var memoryId: Int = -1
    private var mediaItems: List<MediaItem> = emptyList()
    private var listener: MemoryFragmentListener? = null
    private var currentMemoryGroup: MemoryGroupWithMedia? = null

    interface MemoryFragmentListener {
        fun onMediaClick(
            mediaItems: ArrayList<String>,
            types: ArrayList<String>,
            startPosition: Int
        )

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadMemoryDetails()

        binding.editButton.setOnClickListener {
            listener?.onEditMemory(memoryId)
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = MediaAdapter(emptyList()) { position ->
            val uris = ArrayList(mediaItems.map { it.uri })
            val types = ArrayList(mediaItems.map { it.type.name })
            listener?.onMediaClick(uris, types, position)
        }
        // Use a GridLayout with 3 columns for thumbnails
        binding.mediaRecyclerView.layoutManager = GridLayoutManager(context, 3)
        binding.mediaRecyclerView.adapter = adapter
    }

    private fun loadMemoryDetails() {
        if (memoryId == -1) return

        lifecycleScope.launch(Dispatchers.IO) {
            val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
            currentMemoryGroup = db.memoryGroupDao().getGroupWithMedia(memoryId)

            withContext(Dispatchers.Main) {
                if (currentMemoryGroup != null) {
                    displayDetails(currentMemoryGroup!!)
                }
            }
        }
    }

    private fun displayDetails(data: MemoryGroupWithMedia) {
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
            "${
                String.format(
                    Locale.getDefault(),
                    "%.4f",
                    group.latitude
                )
            }, ${
                String.format(
                    Locale.getDefault(),
                    "%.4f",
                    group.longitude
                )
            }"
        }
        binding.locationText.text = locationString

        binding.showOnTimelineButton.setOnClickListener {
            listener?.onNavigateToTimeline(group.id)
        }

        binding.showOnMapButton.setOnClickListener {
            listener?.onNavigateToMap(group.latitude, group.longitude, group.id)
        }

        // Sort media by dateTaken
        mediaItems = data.mediaItems
            .filter {
                it.deviceId == Settings.Secure.getString(
                    requireContext().contentResolver,
                    Settings.Secure.ANDROID_ID
                )!!
            }
            .sortedBy { it.dateTaken }
        adapter.updateData(mediaItems)
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
        if (currentMemoryGroup == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
            db.memoryGroupDao().deleteGroup(currentMemoryGroup!!.group)

            withContext(Dispatchers.Main) {
                listener?.onMemoryDeleted(
                    currentMemoryGroup!!.group,
                    currentMemoryGroup!!.mediaItems
                )
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

        @JvmStatic
        fun newInstance(memoryId: Int) =
            MemoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MEMORY_ID, memoryId)
                }
            }
    }
}