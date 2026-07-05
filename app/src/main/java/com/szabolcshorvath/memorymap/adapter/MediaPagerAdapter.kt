package com.szabolcshorvath.memorymap.adapter

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.net.toUri
import androidx.core.view.isGone
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

        private val handler = Handler(Looper.getMainLooper())
        private val updateProgressAction = object : Runnable {
            override fun run() {
                if (binding.fullVideoView.isPlaying) {
                    binding.videoScrubber.progress = binding.fullVideoView.currentPosition
                }
                handler.postDelayed(this, SEEKBAR_UPDATE_DELAY)
            }
        }

        fun bind(item: Pair<String, String>) {
            val uri = item.first.toUri()
            val isVideo = item.second == "VIDEO"

            binding.fullImageView.setScale(1.0f, false)

            if (isVideo) {
                binding.fullImageView.visibility = View.VISIBLE
                binding.fullImageView.isZoomable = false
                binding.fullVideoView.visibility = View.GONE
                binding.videoControlsContainer.visibility = View.VISIBLE
                binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_play)

                binding.fullImageView.load(uri) {
                    size(Size.ORIGINAL)
                    crossfade(true)
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options.copy(size = Size.ORIGINAL))
                    }
                }

                binding.fullVideoView.setVideoURI(uri)

                binding.fullVideoView.setOnPreparedListener { mp ->
                    binding.videoScrubber.max = mp.duration
                }

                binding.videoScrubber.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            binding.fullVideoView.seekTo(progress)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                })

                binding.btnPlayPause.setOnClickListener {
                    if (binding.fullVideoView.isPlaying) {
                        binding.fullVideoView.pause()
                        binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
                    } else {
                        if (binding.fullVideoView.isGone) {
                            binding.fullImageView.visibility = View.GONE
                            binding.fullVideoView.visibility = View.VISIBLE
                            startProgressUpdates()
                        }
                        binding.fullVideoView.start()
                        binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_pause)
                    }
                }

                binding.root.setOnClickListener(null)

                binding.fullVideoView.setOnCompletionListener {
                    binding.fullVideoView.visibility = View.GONE
                    binding.fullImageView.visibility = View.VISIBLE
                    binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
                    stopProgressUpdates()
                }
            } else {
                binding.fullVideoView.visibility = View.GONE
                binding.fullImageView.visibility = View.VISIBLE
                binding.fullImageView.isZoomable = true
                binding.videoControlsContainer.visibility = View.GONE

                binding.fullImageView.load(uri) {
                    crossfade(true)
                }
                binding.root.setOnClickListener(null)
            }
        }

        fun resetState() {
            binding.fullImageView.setScale(1.0f, false)
            if (binding.fullVideoView.isVisible || binding.videoControlsContainer.isVisible) {
                binding.fullVideoView.visibility = View.GONE
                binding.fullImageView.visibility = View.VISIBLE
                binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
                if (binding.fullVideoView.isPlaying) {
                    binding.fullVideoView.pause()
                }
            }
            stopProgressUpdates()
        }

        fun startProgressUpdates() {
            handler.removeCallbacks(updateProgressAction)
            handler.post(updateProgressAction)
        }

        fun stopProgressUpdates() {
            handler.removeCallbacks(updateProgressAction)
        }
    }

    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.resetState()
    }

    override fun onViewDetachedFromWindow(holder: MediaViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.stopProgressUpdates()
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

    companion object {
        private const val SEEKBAR_UPDATE_DELAY = 100L
    }
}
