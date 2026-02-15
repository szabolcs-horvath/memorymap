package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.szabolcshorvath.memorymap.adapter.MediaPagerAdapter
import com.szabolcshorvath.memorymap.databinding.FragmentMediaViewerBinding

class MediaViewerFragment : Fragment() {

    private var _binding: FragmentMediaViewerBinding? = null
    private val binding get() = _binding!!

    private var mediaItems: ArrayList<Pair<String, String>>? = null
    private var startPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("UNCHECKED_CAST")
            mediaItems = it.getSerializable(ARG_MEDIA_ITEMS) as? ArrayList<Pair<String, String>>
            startPosition = it.getInt(ARG_START_POSITION, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaItems?.let { items ->
            val adapter = MediaPagerAdapter()
            binding.mediaViewPager.adapter = adapter
            adapter.submitList(items)
            binding.mediaViewPager.setCurrentItem(startPosition, false)
        }

        binding.closeButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MediaViewerFragment"
        private const val ARG_MEDIA_ITEMS = "media_items"
        private const val ARG_START_POSITION = "start_position"

        @JvmStatic
        fun newInstance(
            mediaItems: ArrayList<Pair<String, String>>,
            startPosition: Int
        ) = MediaViewerFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_MEDIA_ITEMS, mediaItems)
                putInt(ARG_START_POSITION, startPosition)
            }
        }
    }
}