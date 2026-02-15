package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.databinding.ItemMediaFullBinding

class MediaPagerAdapter :
    ListAdapter<Pair<String, String>, MediaPagerAdapter.MediaViewHolder>(MediaPageDiffCallback()) {

    class MediaViewHolder(private val binding: ItemMediaFullBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Pair<String, String>) {
            val uri = item.first.toUri()
            val isVideo = item.second == "VIDEO"

            binding.fullImageView.setScale(1.0f, false)

            if (isVideo) {
                binding.fullImageView.visibility = View.VISIBLE
                binding.fullImageView.isZoomable = false
                binding.fullVideoView.visibility = View.VISIBLE
                binding.playIcon.visibility = View.VISIBLE

                binding.fullImageView.load(uri) {
                    crossfade(true)
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }

                binding.fullVideoView.setVideoURI(uri)

                binding.root.setOnClickListener {
                    if (binding.fullVideoView.isPlaying) {
                        binding.fullVideoView.pause()
                        binding.playIcon.visibility = View.VISIBLE
                        binding.fullImageView.visibility = View.VISIBLE
                    } else {
                        binding.fullVideoView.start()
                        binding.playIcon.visibility = View.GONE
                        binding.fullImageView.visibility = View.GONE
                    }
                }

                binding.fullVideoView.setOnCompletionListener {
                    binding.playIcon.visibility = View.VISIBLE
                    binding.fullImageView.visibility = View.VISIBLE
                }

                binding.playIcon.setOnClickListener {
                    binding.fullVideoView.start()
                    binding.playIcon.visibility = View.GONE
                    binding.fullImageView.visibility = View.GONE
                }

            } else {
                binding.fullVideoView.visibility = View.GONE
                binding.playIcon.visibility = View.GONE
                binding.fullImageView.visibility = View.VISIBLE
                binding.fullImageView.isZoomable = true

                binding.fullImageView.load(uri) {
                    crossfade(true)
                }
                binding.root.setOnClickListener(null)
            }
        }

        fun resetState() {
            binding.fullImageView.setScale(1.0f, false)
            if (binding.fullVideoView.isVisible) {
                binding.playIcon.visibility = View.VISIBLE
                binding.fullImageView.visibility = View.VISIBLE
                if (binding.fullVideoView.isPlaying) {
                    binding.fullVideoView.pause()
                }
            }
        }
    }

    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.resetState()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding =
            ItemMediaFullBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class MediaPageDiffCallback : DiffUtil.ItemCallback<Pair<String, String>>() {
        override fun areItemsTheSame(
            oldItem: Pair<String, String>,
            newItem: Pair<String, String>
        ): Boolean {
            return oldItem.first == newItem.first
        }

        override fun areContentsTheSame(
            oldItem: Pair<String, String>,
            newItem: Pair<String, String>
        ): Boolean {
            return oldItem == newItem
        }
    }
}
