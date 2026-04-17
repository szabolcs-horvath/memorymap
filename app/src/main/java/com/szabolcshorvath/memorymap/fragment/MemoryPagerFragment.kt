package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.szabolcshorvath.memorymap.data.ViewModel
import com.szabolcshorvath.memorymap.databinding.FragmentMemoryPagerBinding
import kotlinx.coroutines.launch

class MemoryPagerFragment : Fragment() {

    private var _binding: FragmentMemoryPagerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ViewModel by activityViewModels()
    private var initialMemoryId: Int = -1
    private var memoryIds: List<Int> = emptyList()
    private var isInitialSetupDone = false

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allGroups.collect { groups ->
                    val sortedIds = groups.sortedByDescending { it.startDate }.map { it.id }

                    if (memoryIds != sortedIds) {
                        memoryIds = sortedIds
                        updatePager()
                    }
                }
            }
        }
    }

    private fun updatePager() {
        val binding = _binding ?: return
        val adapter = MemoryPagerAdapter(this, memoryIds)
        binding.memoryViewPager.adapter = adapter

        if (!isInitialSetupDone) {
            val initialPosition = memoryIds.indexOf(initialMemoryId)
            if (initialPosition != -1) {
                binding.memoryViewPager.setCurrentItem(initialPosition, false)
            }
            isInitialSetupDone = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class MemoryPagerAdapter(fragment: Fragment, private val memoryIds: List<Int>) :
        FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = memoryIds.size

        override fun createFragment(position: Int): Fragment {
            return MemoryFragment.newInstance(memoryIds[position])
        }
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
