package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemMediaThumbnailBinding

class MediaAdapter(
    private var mediaItems: List<MediaItem>,
    private val onMediaClick: (Int) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    inner class MediaViewHolder(private val binding: ItemMediaThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(mediaItem: MediaItem, position: Int) {
            binding.thumbnailImage.load(mediaItem.uri) {
                crossfade(true)
                if (mediaItem.type == MediaType.VIDEO) {
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(
                            result.source,
                            options
                        )
                    }
                }
            }

            binding.videoIcon.visibility =
                if (mediaItem.type == MediaType.VIDEO) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onMediaClick(position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding =
            ItemMediaThumbnailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(mediaItems[position], position)
    }

    override fun getItemCount(): Int = mediaItems.size

    fun updateData(newItems: List<MediaItem>) {
        mediaItems = newItems
        notifyDataSetChanged()
    }
}