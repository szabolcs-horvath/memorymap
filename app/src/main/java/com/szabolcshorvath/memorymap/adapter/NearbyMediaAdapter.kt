package com.szabolcshorvath.memorymap.adapter

import android.content.Context
import android.text.format.Formatter
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
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.ItemNearbyMediaBinding
import com.szabolcshorvath.memorymap.util.NearbyFile
import com.szabolcshorvath.memorymap.util.NearbyMedia
import kotlin.math.abs

/** The photos or videos taken around the time of a missing one, with how much earlier or later and their size. */
internal class NearbyMediaAdapter(private val onPick: (NearbyFile) -> Unit) :
    ListAdapter<NearbyFile, NearbyMediaAdapter.ViewHolder>(DIFF) {

    /** The missing item's date taken and type, which the rows are compared with. */
    var dateTaken: Long = 0L
    var type: MediaType = MediaType.IMAGE

    inner class ViewHolder(private val binding: ItemNearbyMediaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: NearbyFile) {
            val context = binding.root.context
            val taken = whenTaken(context, file.dateTaken - dateTaken)
            binding.tvNearbyWhen.text = taken.replaceFirstChar { it.titlecase() }
            binding.tvNearbySize.text = Formatter.formatFileSize(context, file.size)
            binding.tvNearbyAlike.visibility = if (file.looksAlike) View.VISIBLE else View.GONE
            binding.root.contentDescription = context.getString(if (file.looksAlike) R.string.nearby_use_alike else R.string.nearby_use, taken)
            binding.nearbyThumbnail.load(file.uri) {
                crossfade(true)
                if (type == MediaType.VIDEO) {
                    videoFrameMicros(0)
                    decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                }
            }
            binding.root.setOnClickListener { onPick(file) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemNearbyMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private const val MILLIS_PER_SECOND = 1000L

        private val DIFF = object : DiffUtil.ItemCallback<NearbyFile>() {
            override fun areItemsTheSame(oldItem: NearbyFile, newItem: NearbyFile) = oldItem.uri == newItem.uri
            override fun areContentsTheSame(oldItem: NearbyFile, newItem: NearbyFile) = oldItem == newItem
        }

        /** "7 s later", "3 min earlier" or "at the same time", for a file taken [deltaMs] after the missing one. */
        fun whenTaken(context: Context, deltaMs: Long): String {
            return when {
                abs(deltaMs) < MILLIS_PER_SECOND -> context.getString(R.string.nearby_same_time)
                deltaMs > 0 -> context.getString(R.string.nearby_later, NearbyMedia.span(deltaMs))
                else -> context.getString(R.string.nearby_earlier, NearbyMedia.span(deltaMs))
            }
        }
    }
}
