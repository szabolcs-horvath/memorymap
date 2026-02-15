package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemMediaThumbnailBinding

class MediaAdapter(
    private val currentDeviceId: String?,
    private val onMediaClick: (Int) -> Unit
) : ListAdapter<MediaItem, MediaAdapter.MediaViewHolder>(MediaItemDiffCallback()) {

    inner class MediaViewHolder(private val binding: ItemMediaThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(mediaItem: MediaItem, position: Int) {
            val isFromOtherDevice = currentDeviceId != null && mediaItem.deviceId != currentDeviceId

            if (isFromOtherDevice) {
                binding.thumbnailImage.setImageDrawable(null)
                binding.errorIcon.visibility = View.VISIBLE
                binding.videoIcon.visibility = View.GONE
            } else {
                binding.thumbnailImage.load(mediaItem.uri) {
                    crossfade(true)
                    if (mediaItem.type == MediaType.VIDEO) {
                        videoFrameMicros(0)
                        decoderFactory { result, options, _ ->
                            VideoFrameDecoder(result.source, options)
                        }
                    }
                    listener(
                        onError = { _, _ -> binding.errorIcon.visibility = View.VISIBLE },
                        onSuccess = { _, _ -> binding.errorIcon.visibility = View.GONE }
                    )
                }

                binding.videoIcon.visibility =
                    if (mediaItem.type == MediaType.VIDEO) View.VISIBLE else View.GONE
            }

            binding.root.setOnClickListener { onMediaClick(position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding =
            ItemMediaThumbnailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    fun updateData(newItems: List<MediaItem>) {
        submitList(newItems)
    }

    private class MediaItemDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }
}