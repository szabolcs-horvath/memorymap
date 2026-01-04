package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.adapter.TimelineAdapter
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentTimelineBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimelineFragment : Fragment() {

    private var _binding: FragmentTimelineBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TimelineAdapter
    private var listener: TimelineListener? = null

    interface TimelineListener {
        fun onMemoryClicked(id: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is TimelineListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadMemories()
    }

    private fun setupRecyclerView() {
        adapter = TimelineAdapter(emptyList()) { memoryGroup ->
            listener?.onMemoryClicked(memoryGroup.id)
        }
        binding.timelineRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.timelineRecyclerView.adapter = adapter
    }

    private fun loadMemories() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
            val groups = db.memoryGroupDao().getAllGroups().sortedByDescending { it.startDate }

            withContext(Dispatchers.Main) {
                adapter.updateData(groups)
                if (groups.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.timelineRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.timelineRecyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    fun refreshData() {
        loadMemories()
    }

    fun scrollToAndFlash(memoryId: Int) {
        val position = adapter.getPositionForId(memoryId)
        if (position != -1) {
            binding.timelineRecyclerView.scrollToPosition(position)

            // We need to wait a bit for the scroll to happen and view holder to be bound/visible
            lifecycleScope.launch {
                delay(100)
                val viewHolder =
                    binding.timelineRecyclerView.findViewHolderForAdapterPosition(position)
                if (viewHolder is TimelineAdapter.TimelineViewHolder) {
                    viewHolder.flash()
                } else {
                    // Sometimes the view holder isn't immediately available even after scroll
                    // Try waiting a bit more or listen for scroll idle
                    binding.timelineRecyclerView.addOnScrollListener(object :
                        RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(
                            recyclerView: RecyclerView,
                            newState: Int
                        ) {
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                val vh =
                                    binding.timelineRecyclerView.findViewHolderForAdapterPosition(
                                        position
                                    )
                                if (vh is TimelineAdapter.TimelineViewHolder) {
                                    vh.flash()
                                }
                                binding.timelineRecyclerView.removeOnScrollListener(this)
                            }
                        }
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadMemories()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TimelineFragment"
    }
}