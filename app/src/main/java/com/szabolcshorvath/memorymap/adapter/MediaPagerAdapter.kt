package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.databinding.ItemMediaFullBinding
import androidx.core.net.toUri
import androidx.core.view.isVisible

class MediaPagerAdapter(
    private val mediaUris: List<String>,
    private val mediaTypes: List<String>
) : RecyclerView.Adapter<MediaPagerAdapter.MediaViewHolder>() {

    class MediaViewHolder(private val binding: ItemMediaFullBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(uriString: String, typeString: String) {
            val uri = uriString.toUri()
            val isVideo = typeString == "VIDEO"

            if (isVideo) {
                binding.fullImageView.visibility = View.VISIBLE // Keep image visible as thumbnail
                binding.fullVideoView.visibility = View.VISIBLE
                binding.playIcon.visibility = View.VISIBLE

                // Load thumbnail for video
                binding.fullImageView.load(uri) {
                    crossfade(true)
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(
                            result.source,
                            options
                        )
                    }
                }

                binding.fullVideoView.setVideoURI(uri)

                // Simple play/pause on click
                binding.root.setOnClickListener {
                    if (binding.fullVideoView.isPlaying) {
                        binding.fullVideoView.pause()
                        binding.playIcon.visibility = View.VISIBLE
                        binding.fullImageView.visibility =
                            View.VISIBLE // Show thumbnail when paused
                    } else {
                        binding.fullVideoView.start()
                        binding.playIcon.visibility = View.GONE
                        binding.fullImageView.visibility = View.GONE // Hide thumbnail when playing
                    }
                }

                binding.fullVideoView.setOnCompletionListener {
                    binding.playIcon.visibility = View.VISIBLE
                    binding.fullImageView.visibility = View.VISIBLE // Show thumbnail when finished
                }

                // Also ensure play icon starts video
                binding.playIcon.setOnClickListener {
                    binding.fullVideoView.start()
                    binding.playIcon.visibility = View.GONE
                    binding.fullImageView.visibility = View.GONE // Hide thumbnail when playing
                }

            } else {
                binding.fullVideoView.visibility = View.GONE
                binding.playIcon.visibility = View.GONE
                binding.fullImageView.visibility = View.VISIBLE

                binding.fullImageView.load(uri) {
                    crossfade(true)
                }
                binding.root.setOnClickListener(null) // Clear listener
            }
        }

        fun resetVideoState() {
            if (binding.fullVideoView.isVisible) {
                binding.playIcon.visibility = View.VISIBLE
                binding.fullImageView.visibility = View.VISIBLE
                // Ensure video is not playing when restored from cache
                if (binding.fullVideoView.isPlaying) {
                    binding.fullVideoView.pause()
                }
            }
        }
    }

    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.resetVideoState()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding =
            ItemMediaFullBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(mediaUris[position], mediaTypes[position])
    }

    override fun getItemCount(): Int = mediaUris.size
}