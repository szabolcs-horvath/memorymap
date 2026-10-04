package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.size.Scale
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemMediaThumbnailBinding

class MediaAdapter(
    private var currentDeviceId: String?,
    private val onMediaClick: (Int) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    init {
        setHasStableIds(true)
    }

    fun updateCurrentDeviceId(deviceId: String?) {
        currentDeviceId = deviceId
        notifyItemRangeChanged(0, itemCount)
    }

    inner class MediaViewHolder(private val binding: ItemMediaThumbnailBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {
        fun bind(mediaItem: MediaItem) {
            val isFromOtherDevice = currentDeviceId != null && mediaItem.deviceId != currentDeviceId

            if (isFromOtherDevice) {
                binding.thumbnailImage.setImageDrawable(null)
                showError(binding, mediaItem)
                binding.videoIcon.visibility = View.GONE
            } else {
                binding.thumbnailImage.load(mediaItem.uri) {
                    // Media thumbnails on MemoryFragment
                    scale(Scale.FILL)
                    crossfade(true)
                    if (mediaItem.type == MediaType.VIDEO) {
                        videoFrameMicros(0)
                        decoderFactory { result, options, _ ->
                            VideoFrameDecoder(result.source, options)
                        }
                    }
                    listener(
                        onError = { _, _ -> showError(binding, mediaItem) },
                        onSuccess = { _, _ -> hideError(binding) }
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

        private fun showError(binding: ItemMediaThumbnailBinding, mediaItem: MediaItem) {
            binding.errorContainer.visibility = View.VISIBLE
            val context = binding.root.context
            val typeStr = mediaItem.type.name
            val sizeStr = if (mediaItem.fileSize > 0) android.text.format.Formatter.formatFileSize(context, mediaItem.fileSize) else "Unknown size"
            val dateStr = if (mediaItem.dateTaken > 0) {
                val zdt = java.time.Instant.ofEpochMilli(mediaItem.dateTaken).atZone(java.time.ZoneId.systemDefault())
                zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd\nHH:mm", java.util.Locale.getDefault()))
            } else {
                "Unknown date"
            }
            binding.tvErrorDetails.text = "$typeStr\n$sizeStr\n$dateStr"
        }

        private fun hideError(binding: ItemMediaThumbnailBinding) {
            binding.errorContainer.visibility = View.GONE
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
