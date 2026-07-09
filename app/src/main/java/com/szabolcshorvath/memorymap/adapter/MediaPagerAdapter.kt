package com.szabolcshorvath.memorymap.adapter

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.size.Scale
import coil3.size.Size
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.databinding.ItemMediaFullBinding

class MediaPagerAdapter : ListAdapter<Pair<String, String>, MediaPagerAdapter.MediaViewHolder>(
    MediaPageDiffCallback()
) {

    class MediaViewHolder(private val binding: ItemMediaFullBinding) : RecyclerView.ViewHolder(binding.root) {

        private val handler = Handler(Looper.getMainLooper())
        private var isUpdaterRunning = false
        private val updateProgressAction = object : Runnable {
            override fun run() {
                if (!isUpdaterRunning) return
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

                binding.fullVideoView.setZOrderMediaOverlay(true)

                binding.fullImageView.load(uri) {
                    size(Size.ORIGINAL)
                    scale(Scale.FIT)
                    crossfade(true)
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }

                binding.fullVideoView.setVideoURI(uri)

                binding.fullVideoView.setOnPreparedListener { mp ->
                    binding.videoScrubber.max = maxOf(mp.duration, 0)
                    binding.videoScrubber.progress = 0
                }

                binding.fullVideoView.setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        binding.fullVideoView.alpha = 1f
                        binding.fullImageView.animate()
                            .alpha(0f)
                            .setDuration(THUMBNAIL_TO_VIDEO_TRANSITION_TIME)
                            .withEndAction {
                                binding.fullImageView.visibility = View.GONE
                                binding.fullImageView.alpha = 1f
                            }
                            .start()
                    }
                    false
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
                        stopProgressUpdates()
                    } else {
                        if (!binding.fullVideoView.isVisible) {
                            binding.fullVideoView.visibility = View.VISIBLE
                            binding.fullVideoView.alpha = 0f
                        }
                        binding.fullVideoView.start()
                        binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_pause)
                        startProgressUpdates()
                    }
                }

                binding.root.setOnClickListener(null)

                binding.fullVideoView.setOnCompletionListener {
                    binding.fullVideoView.visibility = View.GONE
                    binding.fullImageView.visibility = View.VISIBLE
                    binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
                    binding.videoScrubber.progress = 0
                    binding.fullVideoView.seekTo(0)
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
            if (binding.fullVideoView.visibility != View.GONE || binding.videoControlsContainer.isVisible) {
                binding.fullVideoView.visibility = View.GONE
                binding.fullVideoView.alpha = 1f
                binding.fullImageView.visibility = View.VISIBLE
                binding.btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
                if (binding.fullVideoView.isPlaying) {
                    binding.fullVideoView.pause()
                }
            }
            stopProgressUpdates()
        }

        fun startProgressUpdates() {
            if (!isUpdaterRunning) {
                isUpdaterRunning = true
                handler.removeCallbacks(updateProgressAction)
                handler.post(updateProgressAction)
            }
        }

        fun stopProgressUpdates() {
            isUpdaterRunning = false
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
        private const val THUMBNAIL_TO_VIDEO_TRANSITION_TIME = 200L
    }
}
