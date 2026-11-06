package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.szabolcshorvath.memorymap.adapter.MediaPagerAdapter
import com.szabolcshorvath.memorymap.databinding.FragmentMediaViewerBinding
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MediaViewerFragment : Fragment() {

    private var _binding: FragmentMediaViewerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MediaViewerViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMediaViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewLifecycleOwner = viewLifecycleOwner
        val adapter = MediaPagerAdapter()
        val pager = binding.mediaViewPager
        pager.adapter = adapter

        // Until the first list is shown at the remembered item, the pager's own page changes (to the first page, as
        // items arrive) are not the user's.
        var isPlaced = false
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (isPlaced) adapter.currentList.getOrNull(position)?.let { viewModel.currentItemId = it.id }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mediaItems.filterNotNull().collect { items ->
                    adapter.submitList(items) {
                        // Stay on the same item when the list changes, such as a photo before it removed elsewhere. If
                        // that item itself is gone, stay at the same place in the list.
                        val position = viewModel.currentPosition(items)
                            ?: if (isPlaced) pager.currentItem.coerceAtMost(items.lastIndex).coerceAtLeast(0) else 0
                        if (!isPlaced || pager.currentItem != position) pager.setCurrentItem(position, false)
                        isPlaced = true
                        items.getOrNull(position)?.let { viewModel.currentItemId = it.id }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MediaViewerFragment"

        /** Shows memory [memoryId]'s media, starting at item [mediaItemId]. */
        @JvmStatic
        fun newInstance(memoryId: Int, mediaItemId: Int) = MediaViewerFragment().apply {
            arguments = bundleOf(
                MediaViewerViewModel.ARG_MEMORY_ID to memoryId,
                MediaViewerViewModel.ARG_MEDIA_ITEM_ID to mediaItemId
            )
        }
    }
}
