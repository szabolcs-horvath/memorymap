package com.szabolcshorvath.memorymap

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentTimelineBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimelineFragment : Fragment() {

    private var _binding: FragmentTimelineBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TimelineAdapter
    private var listener: TimelineListener? = null

    interface TimelineListener {
        fun onMemorySelected(lat: Double, lng: Double, id: Int)
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
            listener?.onMemorySelected(memoryGroup.latitude, memoryGroup.longitude, memoryGroup.id)
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
            }
        }
    }
    
    fun refreshData() {
        loadMemories()
    }
    
    override fun onResume() {
        super.onResume()
        loadMemories()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}