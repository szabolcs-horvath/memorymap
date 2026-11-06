package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.size.Scale
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemMediaThumbnailBinding
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil

class MediaAdapter(
    private var currentDeviceId: String?,
    private val onMediaClick: (Int) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    /** Stored thumbnails by signature: a tile that can't be loaded shows its own behind the warning. */
    private var thumbnails: Map<String, ByteArray> = emptyMap()

    /** Items known to need re-linking (`LocalMediaUtil.hasMissingMedia`): their tiles show as missing without loading. */
    private var missingIds: Set<Int> = emptySet()

    fun updateMissing(newMissingIds: Set<Int>) {
        if (newMissingIds == missingIds) return
        missingIds = newMissingIds
        notifyItemRangeChanged(0, itemCount)
    }

    fun updateThumbnails(newThumbnails: Map<String, ByteArray>) {
        if (newThumbnails.keys == thumbnails.keys) return
        thumbnails = newThumbnails
        notifyItemRangeChanged(0, itemCount)
    }

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
            // Known to be missing: shown as missing even if an image of it is still in the memory cache.
            val showAsMissing = currentDeviceId != null && mediaItem.deviceId != currentDeviceId || mediaItem.id in missingIds
            binding.root.contentDescription = binding.root.context.getString(
                if (mediaItem.type == MediaType.VIDEO) R.string.open_video else R.string.open_photo
            )

            if (showAsMissing) {
                // Cancel a load still running for the item this view showed before.
                binding.thumbnailImage.dispose()
                binding.thumbnailImage.setImageDrawable(null)
                showError(binding, mediaItem)
                binding.videoIcon.visibility = View.GONE
            } else {
                hideError(binding) // the view may still show the error of the item it showed before
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
            val thumbnail = thumbnails[mediaItem.mediaSignature]
            if (thumbnail != null) binding.thumbnailImage.load(thumbnail) { scale(Scale.FILL) }
            binding.errorContainer.setBackgroundColor(
                context.getColor(if (thumbnail != null) R.color.missing_media_scrim_over_thumbnail else R.color.missing_media_scrim)
            )
            val typeStr = mediaItem.type.name
            val sizeStr = if (mediaItem.fileSize > 0) android.text.format.Formatter.formatFileSize(context, mediaItem.fileSize) else "Unknown size"
            val dateStr = if (mediaItem.dateTaken > 0) {
                DateTimeFormatterUtil.twoLineDateTime(context, mediaItem.dateTaken)
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
