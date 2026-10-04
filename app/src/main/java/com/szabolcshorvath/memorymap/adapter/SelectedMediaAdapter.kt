package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.size.Scale
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemMediaSelectedBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment.SelectedMedia

class SelectedMediaAdapter(private var currentDeviceId: String?, private val onRemove: (Int) -> Unit) :
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
            val zdt = java.time.Instant.ofEpochMilli(item.dateTaken).atZone(java.time.ZoneId.systemDefault())
            zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd\nHH:mm", java.util.Locale.getDefault()))
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

        if (isFromOtherDevice) {
            holder.binding.thumbnailImage.setImageDrawable(null)
            showError(holder.binding, item)
            holder.binding.videoIcon.visibility = View.GONE
        } else {
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

        holder.binding.btRemove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }
}
