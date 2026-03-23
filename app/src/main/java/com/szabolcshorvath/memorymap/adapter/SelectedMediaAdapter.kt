package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemMediaSelectedBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment.SelectedMedia

class SelectedMediaAdapter(
    private val currentDeviceId: String?,
    private val onRemove: (Int) -> Unit
) : ListAdapter<SelectedMedia, SelectedMediaAdapter.SelectedMediaViewHolder>(
    SelectedMedia.SelectedMediaDiffCallback()
) {

    class SelectedMediaViewHolder(val binding: ItemMediaSelectedBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SelectedMediaViewHolder(
        ItemMediaSelectedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: SelectedMediaViewHolder, position: Int) {
        val item = getItem(position)
        val isFromOtherDevice = currentDeviceId != null && item.deviceId != currentDeviceId

        if (isFromOtherDevice) {
            holder.binding.thumbnailImage.setImageDrawable(null)
            holder.binding.errorIcon.visibility = View.VISIBLE
            holder.binding.videoIcon.visibility = View.GONE
        } else {
            holder.binding.thumbnailImage.load(item.uri) {
                crossfade(true)
                if (item.type == MediaType.VIDEO) {
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }
                listener(
                    onError = { _, _ -> holder.binding.errorIcon.visibility = View.VISIBLE },
                    onSuccess = { _, _ -> holder.binding.errorIcon.visibility = View.GONE }
                )
            }
            holder.binding.videoIcon.visibility =
                if (item.type == MediaType.VIDEO) View.VISIBLE else View.GONE
        }

        holder.binding.removeButton.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }
}
