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
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentTimelineBinding
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimelineFragment : Fragment() {

    private var _binding: FragmentTimelineBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TimelineAdapter
    private var listener: TimelineListener? = null
    private var pendingScrollMemoryId: Int? = null

    interface TimelineListener {
        fun onMemoryClicked(id: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is TimelineListener) {
            listener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        lifecycleScope.launch {
            loadMemories()
        }
    }

    private fun setupRecyclerView() {
        adapter = TimelineAdapter { memoryGroup ->
            listener?.onMemoryClicked(memoryGroup.id)
        }
        binding.timelineRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.timelineRecyclerView.adapter = adapter
    }

    private suspend fun loadMemories() {
        trace("timeline_fragment_load_memories") {
            val context = context ?: return@trace
            val db = MemoryMapDatabase.getDatabase(context.applicationContext)
            val groups = db.memoryGroupDao().getAllGroups().sortedByDescending { it.startDate }

            withContext(Dispatchers.Main) {
                val binding = _binding ?: return@withContext
                adapter.updateData(groups) {
                    pendingScrollMemoryId?.let { id ->
                        performScrollAndFlash(id)
                        pendingScrollMemoryId = null
                    }
                }
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

    suspend fun refreshData() {
        trace("timeline_fragment_refresh_data") {
            loadMemories()
        }
    }

    fun scrollToAndFlash(memoryId: Int) {
        if (adapter.itemCount > 0) {
            performScrollAndFlash(memoryId)
        } else {
            pendingScrollMemoryId = memoryId
        }
    }

    private fun performScrollAndFlash(memoryId: Int) {
        val binding = _binding ?: return
        val position = adapter.getPositionForId(memoryId)
        if (position != -1) {
            (binding.timelineRecyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, 100)

            binding.timelineRecyclerView.post {
                val postBinding = _binding ?: return@post
                val viewHolder = postBinding.timelineRecyclerView.findViewHolderForAdapterPosition(position)
                if (viewHolder is TimelineAdapter.TimelineViewHolder) {
                    viewHolder.flash()
                } else {
                    postBinding.timelineRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                val vh = _binding?.timelineRecyclerView?.findViewHolderForAdapterPosition(position)
                                if (vh is TimelineAdapter.TimelineViewHolder) {
                                    vh.flash()
                                }
                                recyclerView.removeOnScrollListener(this)
                            }
                        }
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            loadMemories()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            lifecycleScope.launch {
                loadMemories()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TimelineFragment"
    }
}
