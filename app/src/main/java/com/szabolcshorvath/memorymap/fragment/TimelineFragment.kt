package com.szabolcshorvath.memorymap.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.adapter.TimelineAdapter
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.databinding.FragmentTimelineBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TimelineFragment : Fragment() {

    private var _binding: FragmentTimelineBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TimelineAdapter
    private var timelineListener: TimelineListener? = null

    private val commonViewModel: CommonViewModel by activityViewModels()
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
        setupSearchBar()
        setupScrollBehavior()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(commonViewModel.allGroups, viewModel.searchQuery) { groups, query ->
                    viewModel.filterGroups(groups, query)
                }.collect { filteredGroups ->
                    val sortedGroups = filteredGroups.sortedByDescending { it.startDate }
                    adapter.updateData(sortedGroups) {
                        viewModel.pendingScrollMemoryId?.let { id ->
                            performScrollAndFlash(id)
                            viewModel.pendingScrollMemoryId = null
                        }
                    }
                    if (sortedGroups.isEmpty()) {
                        binding.emptyView.visibility = View.VISIBLE
                        binding.rvTimeline.visibility = View.GONE
                    } else {
                        binding.emptyView.visibility = View.GONE
                        binding.rvTimeline.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupSearchBar() {
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }
    }

    private fun setupScrollBehavior() {
        binding.rvTimeline.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (firstVisibleItemPosition > 0) {
                    binding.fabTop.show()
                } else {
                    binding.fabTop.hide()
                }
            }
        })

        binding.fabTop.setOnClickListener {
            binding.rvTimeline.stopScroll()
            binding.rvTimeline.smoothScrollToPosition(0)
            binding.appBar.setExpanded(true, true)
        }
    }

    private fun setupRecyclerView() {
        adapter = TimelineAdapter { memoryGroup ->
            timelineListener?.onMemoryClicked(memoryGroup.id)
        }
        binding.rvTimeline.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTimeline.adapter = adapter
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
            (binding.rvTimeline.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, SCROLL_TO_POSITION_OFFSET)

            binding.rvTimeline.post {
                val postBinding = _binding ?: return@post
                val viewHolder = postBinding.rvTimeline.findViewHolderForAdapterPosition(position)
                if (viewHolder is TimelineAdapter.TimelineViewHolder) {
                    viewHolder.flash()
                } else {
                    postBinding.rvTimeline.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                val vh = _binding?.rvTimeline?.findViewHolderForAdapterPosition(position)
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
