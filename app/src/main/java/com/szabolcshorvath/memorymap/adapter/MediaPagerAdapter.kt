package com.szabolcshorvath.memorymap.adapter

import android.media.MediaPlayer
import android.net.Uri
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
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemMediaFullBinding

class MediaPagerAdapter : ListAdapter<MediaItem, MediaPagerAdapter.MediaViewHolder>(
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

        fun bind(item: MediaItem) {
            resetState()
            val uri = item.uri.toUri()
            val isVideo = item.type == MediaType.VIDEO
            val description = binding.root.context.getString(if (isVideo) R.string.video else R.string.photo)
            binding.fullImageView.contentDescription = description
            binding.fullVideoView.contentDescription = description

            binding.root.setOnClickListener(null)
            if (isVideo) bindVideo(uri) else bindPhoto(uri)
        }

        private fun bindVideo(uri: Uri) {
            binding.fullImageView.visibility = View.VISIBLE
            binding.fullImageView.isZoomable = false
            binding.fullVideoView.visibility = View.GONE
            binding.videoControlsContainer.visibility = View.VISIBLE
            showPlayButton(isPlaying = false)

            binding.fullVideoView.setZOrderMediaOverlay(true)

            val displayMetrics = binding.root.context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            binding.fullImageView.load(uri) {
                // Full size video preview image
                memoryCacheKey("${uri}_full")
                size(screenWidth, screenHeight)
                scale(Scale.FIT)
                crossfade(true)
                videoFrameMicros(0)
                decoderFactory { result, options, _ ->
                    VideoFrameDecoder(result.source, options)
                }
            }

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

            binding.fullVideoView.setVideoURI(uri)
            setUpVideoControls()
        }

        private fun setUpVideoControls() {
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
                    showPlayButton(isPlaying = false)
                    stopProgressUpdates()
                } else {
                    if (!binding.fullVideoView.isVisible) {
                        binding.fullVideoView.visibility = View.VISIBLE
                        binding.fullVideoView.alpha = 0f
                    }
                    binding.fullVideoView.start()
                    showPlayButton(isPlaying = true)
                    startProgressUpdates()
                }
            }

            binding.fullVideoView.setOnCompletionListener {
                binding.fullVideoView.visibility = View.GONE
                binding.fullImageView.visibility = View.VISIBLE
                showPlayButton(isPlaying = false)
                binding.videoScrubber.progress = 0
                binding.fullVideoView.seekTo(0)
                stopProgressUpdates()
            }
        }

        private fun bindPhoto(uri: Uri) {
            binding.fullVideoView.visibility = View.GONE
            binding.fullImageView.visibility = View.VISIBLE
            binding.fullImageView.isZoomable = true
            binding.videoControlsContainer.visibility = View.GONE

            val displayMetrics = binding.root.context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            binding.fullImageView.load(uri) {
                // Full size image view
                memoryCacheKey("${uri}_full")
                size(screenWidth, screenHeight)
                scale(Scale.FIT)
                crossfade(true)
            }
        }

        fun resetState() {
            binding.fullImageView.animate().cancel()
            binding.fullImageView.alpha = 1f
            binding.fullImageView.setScale(1.0f, false)
            binding.videoScrubber.progress = 0
            if (binding.fullVideoView.visibility != View.GONE || binding.videoControlsContainer.isVisible) {
                binding.fullVideoView.visibility = View.GONE
                binding.fullVideoView.alpha = 1f
                binding.fullImageView.visibility = View.VISIBLE
                showPlayButton(isPlaying = false)
                if (binding.fullVideoView.isPlaying) {
                    binding.fullVideoView.pause()
                }
                binding.fullVideoView.seekTo(0)
            }
            stopProgressUpdates()
        }

        /** Shows "Pause" while the video plays and "Play" while it doesn't, as an icon with a matching description. */
        private fun showPlayButton(isPlaying: Boolean) {
            val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
            binding.btnPlayPause.setIconResource(icon)
            binding.btnPlayPause.contentDescription =
                binding.root.context.getString(if (isPlaying) R.string.pause_video else R.string.play_video)
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
        holder.resetState()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        return MediaViewHolder(ItemMediaFullBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class MediaPageDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean = oldItem.id == newItem.id

        // Only what a page shows: a new order or newly read details must not reload a photo or stop a video.
        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
            oldItem.uri == newItem.uri && oldItem.type == newItem.type
    }

    companion object {
        private const val SEEKBAR_UPDATE_DELAY = 100L
        private const val THUMBNAIL_TO_VIDEO_TRANSITION_TIME = 200L
    }
}
