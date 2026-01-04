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

    private var mediaUris: ArrayList<String>? = null
    private var mediaTypes: ArrayList<String>? = null
    private var startPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mediaUris = it.getStringArrayList(ARG_MEDIA_URIS)
            mediaTypes = it.getStringArrayList(ARG_MEDIA_TYPES)
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

        if (mediaUris != null && mediaTypes != null) {
            val adapter = MediaPagerAdapter(mediaUris!!, mediaTypes!!)
            binding.mediaViewPager.adapter = adapter
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
        private const val ARG_MEDIA_URIS = "media_uris"
        private const val ARG_MEDIA_TYPES = "media_types"
        private const val ARG_START_POSITION = "start_position"

        @JvmStatic
        fun newInstance(
            mediaUris: ArrayList<String>,
            mediaTypes: ArrayList<String>,
            startPosition: Int
        ) = MediaViewerFragment().apply {
            arguments = Bundle().apply {
                putStringArrayList(ARG_MEDIA_URIS, mediaUris)
                putStringArrayList(ARG_MEDIA_TYPES, mediaTypes)
                putInt(ARG_START_POSITION, startPosition)
            }
        }
    }
}