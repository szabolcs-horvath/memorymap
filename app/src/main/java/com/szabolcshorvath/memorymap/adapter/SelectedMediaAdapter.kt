package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.size.Scale
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemMediaSelectedBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment.SelectedMedia
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil

class SelectedMediaAdapter(private var currentDeviceId: String?, private val onRemove: (SelectedMedia) -> Unit) :
    ListAdapter<SelectedMedia, SelectedMediaAdapter.SelectedMediaViewHolder>(
        SelectedMedia.SelectedMediaDiffCallback()
    ) {

    class SelectedMediaViewHolder(val binding: ItemMediaSelectedBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateCurrentDeviceId(deviceId: String?) {
        currentDeviceId = deviceId
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        SelectedMediaViewHolder(ItemMediaSelectedBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    private fun showError(binding: ItemMediaSelectedBinding, item: SelectedMedia) {
        binding.errorContainer.visibility = View.VISIBLE
        val context = binding.root.context
        val typeStr = item.type.name
        val sizeStr = if (item.fileSize > 0) android.text.format.Formatter.formatFileSize(context, item.fileSize) else "Unknown size"
        val dateStr = if (item.dateTaken > 0) {
            DateTimeFormatterUtil.twoLineDateTime(context, item.dateTaken)
        } else {
            "Unknown date"
        }
        binding.tvErrorDetails.text = "$typeStr\n$sizeStr\n$dateStr"
    }

    private fun hideError(binding: ItemMediaSelectedBinding) {
        binding.errorContainer.visibility = View.GONE
    }

    override fun onBindViewHolder(holder: SelectedMediaViewHolder, position: Int) {
        val item = getItem(position)
        val isFromOtherDevice = currentDeviceId != null && item.deviceId != currentDeviceId
        holder.binding.btRemove.contentDescription = holder.binding.root.context.getString(
            if (item.type == MediaType.VIDEO) R.string.remove_video else R.string.remove_photo
        )

        if (isFromOtherDevice) {
            // Cancel a load still running for the item this view showed before.
            holder.binding.thumbnailImage.dispose()
            holder.binding.thumbnailImage.setImageDrawable(null)
            showError(holder.binding, item)
            holder.binding.videoIcon.visibility = View.GONE
        } else {
            hideError(holder.binding) // the view may still show the error of the item it showed before
            holder.binding.thumbnailImage.load(item.uri) {
                // Media thumbnails on AddMemoryGroupFragment
                scale(Scale.FILL)
                crossfade(true)
                if (item.type == MediaType.VIDEO) {
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }
                listener(
                    onError = { _, _ -> showError(holder.binding, item) },
                    onSuccess = { _, _ -> hideError(holder.binding) }
                )
            }
            holder.binding.videoIcon.visibility = if (item.type == MediaType.VIDEO) View.VISIBLE else View.GONE
        }

        holder.binding.btRemove.setOnClickListener {
            // NO_POSITION during the remove animation, for example on a quick double tap.
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRemove(getItem(pos))
        }
    }
}
