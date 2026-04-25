package com.szabolcshorvath.memorymap.fragment

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
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.szabolcshorvath.memorymap.data.MemoryMapViewModel
import com.szabolcshorvath.memorymap.databinding.FragmentMemoryPagerBinding
import kotlinx.coroutines.launch

class MemoryPagerFragment : Fragment() {

    private var _binding: FragmentMemoryPagerBinding? = null
    private val binding get() = _binding!!
    private val memoryMapViewModel: MemoryMapViewModel by activityViewModels()
    private val viewModel: MemoryPagerFragmentViewModel by viewModels()
    private var initialMemoryId: Int = -1
    private var pagerAdapter: MemoryPagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialMemoryId = it.getInt(ARG_INITIAL_MEMORY_ID)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemoryPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pagerAdapter = MemoryPagerAdapter(this)
        binding.memoryViewPager.adapter = pagerAdapter

        if (viewModel.memoryIds.isNotEmpty()) {
            updatePager()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                memoryMapViewModel.allGroups.collect { groups ->
                    val sortedIds = groups.sortedByDescending { it.startDate }.map { it.id }

                    if (viewModel.memoryIds != sortedIds) {
                        viewModel.memoryIds = sortedIds
                        updatePager()
                    }
                }
            }
        }
    }

    private fun updatePager() {
        if (!viewModel.isInitialSetupDone) {
            val initialPosition = viewModel.memoryIds.indexOf(initialMemoryId)
            pagerAdapter?.submitList(viewModel.memoryIds)
            if (initialPosition != -1) {
                binding.memoryViewPager.post {
                    _binding?.memoryViewPager?.setCurrentItem(initialPosition, false)
                }
            }
            viewModel.isInitialSetupDone = true
        } else {
            pagerAdapter?.submitList(viewModel.memoryIds)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        pagerAdapter = null
    }

    private class MemoryPagerAdapter(fragment: Fragment) :
        FragmentStateAdapter(fragment) {

        private var memoryIds: List<Int> = emptyList()

        fun submitList(newIds: List<Int>) {
            val diffCallback = object : DiffUtil.Callback() {
                override fun getOldListSize() = memoryIds.size
                override fun getNewListSize() = newIds.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = memoryIds[oldItemPosition] == newIds[newItemPosition]
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = memoryIds[oldItemPosition] == newIds[newItemPosition]
            }
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            memoryIds = newIds
            diffResult.dispatchUpdatesTo(this)
        }

        override fun getItemCount(): Int = memoryIds.size

        override fun createFragment(position: Int): Fragment {
            return MemoryFragment.newInstance(memoryIds[position])
        }

        override fun getItemId(position: Int): Long = memoryIds[position].toLong()

        override fun containsItem(itemId: Long): Boolean = memoryIds.contains(itemId.toInt())
    }

    companion object {
        const val TAG = "MemoryPagerFragment"
        private const val ARG_INITIAL_MEMORY_ID = "initial_memory_id"

        @JvmStatic
        fun newInstance(memoryId: Int) = MemoryPagerFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_INITIAL_MEMORY_ID, memoryId)
            }
        }
    }
}
