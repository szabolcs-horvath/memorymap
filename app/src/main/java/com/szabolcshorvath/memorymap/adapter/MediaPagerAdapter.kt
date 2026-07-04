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
import coil3.size.Size
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.databinding.ItemMediaFullBinding

class MediaPagerAdapter : ListAdapter<Pair<String, String>, MediaPagerAdapter.MediaViewHolder>(
    MediaPageDiffCallback()
) {

    class MediaViewHolder(private val binding: ItemMediaFullBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Pair<String, String>) {
            val uri = item.first.toUri()
            val isVideo = item.second == "VIDEO"

            binding.fullImageView.setScale(1.0f, false)

            if (isVideo) {
                binding.fullImageView.visibility = View.VISIBLE
                binding.fullImageView.isZoomable = false
                binding.fullVideoView.visibility = View.GONE
                binding.playIcon.visibility = View.VISIBLE

                binding.fullImageView.load(uri) {
                    size(Size.ORIGINAL)
                    crossfade(true)
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options.copy(size = Size.ORIGINAL))
                    }
                }

                binding.fullVideoView.setVideoURI(uri)

                binding.root.setOnClickListener {
                    if (binding.fullVideoView.isPlaying) {
                        binding.fullVideoView.pause()
                        binding.playIcon.visibility = View.VISIBLE
                    } else {
                        binding.playIcon.visibility = View.GONE
                        binding.fullImageView.visibility = View.GONE
                        binding.fullVideoView.start()
                    }
                }

                binding.fullVideoView.setOnCompletionListener {
                    binding.fullVideoView.visibility = View.GONE
                    binding.playIcon.visibility = View.VISIBLE
                    binding.fullImageView.visibility = View.VISIBLE
                }

                binding.playIcon.setOnClickListener {
                    binding.playIcon.visibility = View.GONE
                    binding.fullImageView.visibility = View.GONE
                    binding.fullVideoView.visibility = View.VISIBLE
                    binding.fullVideoView.start()
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
                binding.fullVideoView.visibility = View.GONE
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
        return MediaViewHolder(ItemMediaFullBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class MediaPageDiffCallback : DiffUtil.ItemCallback<Pair<String, String>>() {
        override fun areItemsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>): Boolean = oldItem.first == newItem.first

        override fun areContentsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>): Boolean = oldItem == newItem
    }
}
