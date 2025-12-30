package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentMemoryPagerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryPagerFragment : Fragment() {

    private var _binding: FragmentMemoryPagerBinding? = null
    private val binding get() = _binding!!
    private var initialMemoryId: Int = -1
    private var memoryIds: List<Int> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialMemoryId = it.getInt(ARG_INITIAL_MEMORY_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemoryPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadMemoriesAndSetupPager()
    }

    private fun loadMemoriesAndSetupPager() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
            // Same sorting as TimelineFragment
            val groups = db.memoryGroupDao().getAllGroups().sortedByDescending { it.startDate }
            memoryIds = groups.map { it.id }

            withContext(Dispatchers.Main) {
                setupViewPager()
            }
        }
    }

    private fun setupViewPager() {
        val adapter = MemoryPagerAdapter(this, memoryIds)
        binding.memoryViewPager.adapter = adapter
        
        val initialPosition = memoryIds.indexOf(initialMemoryId)
        if (initialPosition != -1) {
            binding.memoryViewPager.setCurrentItem(initialPosition, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INITIAL_MEMORY_ID = "initial_memory_id"

        @JvmStatic
        fun newInstance(memoryId: Int) =
            MemoryPagerFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_INITIAL_MEMORY_ID, memoryId)
                }
            }
    }
}

class MemoryPagerAdapter(fragment: Fragment, private val memoryIds: List<Int>) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = memoryIds.size

    override fun createFragment(position: Int): Fragment {
        return MemoryFragment.newInstance(memoryIds[position])
    }
}