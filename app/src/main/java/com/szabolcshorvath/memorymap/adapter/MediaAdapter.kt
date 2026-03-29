package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
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
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    init {
        setHasStableIds(true)
    }

    inner class MediaViewHolder(private val binding: ItemMediaThumbnailBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {
        fun bind(mediaItem: MediaItem) {
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

                binding.videoIcon.visibility = if (mediaItem.type == MediaType.VIDEO) View.VISIBLE else View.GONE
            }

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onMediaClick(pos)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaThumbnailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        return if (position in items.indices) items[position].id.toLong() else RecyclerView.NO_ID
    }

    fun updateData(newItems: List<MediaItem>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].id == newItems[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = items[oldItemPosition]
                val newItem = newItems[newItemPosition]
                // Ignore the order field in comparison to avoid unnecessary animations
                // when syncing after a drag-and-drop operation.
                return oldItem.copy(order = newItem.order) == newItem
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback, true)
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in items.indices && toPosition in items.indices && fromPosition != toPosition) {
            val item = items.removeAt(fromPosition)
            items.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
        }
    }
}
