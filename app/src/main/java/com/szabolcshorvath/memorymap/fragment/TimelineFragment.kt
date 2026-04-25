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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.adapter.TimelineAdapter
import com.szabolcshorvath.memorymap.data.MemoryMapViewModel
import com.szabolcshorvath.memorymap.databinding.FragmentTimelineBinding
import kotlinx.coroutines.launch

class TimelineFragment : Fragment() {

    private var _binding: FragmentTimelineBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TimelineAdapter
    private var timelineListener: TimelineListener? = null

    private val memoryMapViewModel: MemoryMapViewModel by activityViewModels()
    private val viewModel: TimelineFragmentViewModel by viewModels()

    interface TimelineListener {
        fun onMemoryClicked(id: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is TimelineListener) {
            timelineListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                memoryMapViewModel.allGroups.collect { groups ->
                    val sortedGroups = groups.sortedByDescending { it.startDate }
                    adapter.updateData(sortedGroups) {
                        viewModel.pendingScrollMemoryId?.let { id ->
                            performScrollAndFlash(id)
                            viewModel.pendingScrollMemoryId = null
                        }
                    }
                    if (sortedGroups.isEmpty()) {
                        binding.emptyView.visibility = View.VISIBLE
                        binding.timelineRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyView.visibility = View.GONE
                        binding.timelineRecyclerView.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = TimelineAdapter { memoryGroup ->
            timelineListener?.onMemoryClicked(memoryGroup.id)
        }
        binding.timelineRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.timelineRecyclerView.adapter = adapter
    }

    fun scrollToAndFlash(memoryId: Int) {
        val position = adapter.getPositionForId(memoryId)
        if (position != -1) {
            performScrollAndFlash(memoryId)
        } else {
            viewModel.pendingScrollMemoryId = memoryId
        }
    }

    private fun performScrollAndFlash(memoryId: Int) {
        val binding = _binding ?: return
        val position = adapter.getPositionForId(memoryId)
        if (position != -1) {
            (binding.timelineRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, SCROLL_TO_POSITION_OFFSET)

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TimelineFragment"

        private const val SCROLL_TO_POSITION_OFFSET = 100
    }
}
