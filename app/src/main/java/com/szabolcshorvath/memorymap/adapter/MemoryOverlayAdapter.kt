package com.szabolcshorvath.memorymap.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.listitem.ListItemViewHolder
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.databinding.ItemMemoryOverlayBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION

class MemoryOverlayAdapter(private val onDetailsClick: (Int) -> Unit) : ListAdapter<Markerable, ListItemViewHolder>(Markerable.MarkerableDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(com.szabolcshorvath.memorymap.R.layout.item_memory_overlay, parent, false)
        return ListItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ListItemViewHolder, position: Int) {
        holder.bind(position, itemCount)
        val binding = ItemMemoryOverlayBinding.bind(holder.itemView)
        binding.overlayCard.clipToOutline = true
        val item = getItem(position)
        bindTitle(binding, item)
        bindDate(binding, item)
        bindColor(binding, item)

        binding.btnDetails.setOnClickListener {
            onDetailsClick(item.groupId)
        }
    }

    override fun onBindViewHolder(holder: ListItemViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bind(position, itemCount)
            val binding = ItemMemoryOverlayBinding.bind(holder.itemView)
            val item = getItem(position)

            @Suppress("UNCHECKED_CAST")
            val changes = payloads.first() as Set<String>
            if (changes.contains(Markerable.TITLE_DIFF_PAYLOAD)) bindTitle(binding, item)
            if (changes.contains(Markerable.DATE_DIFF_PAYLOAD)) bindDate(binding, item)
            if (changes.contains(Markerable.COLOR_DIFF_PAYLOAD)) bindColor(binding, item)
        }
    }

    private fun bindTitle(binding: ItemMemoryOverlayBinding, item: Markerable) {
        binding.memoryTitle.text = item.title
    }

    private fun bindDate(binding: ItemMemoryOverlayBinding, item: Markerable) {
        val dateText = item.getFormattedDate()
        binding.memoryDate.text = dateText
        binding.memoryDate.visibility = if (dateText.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun bindColor(binding: ItemMemoryOverlayBinding, item: Markerable) {
        binding.colorIndicator.backgroundTintList = ColorStateList.valueOf(
            ColorUtil.hsvToColor(
                item.markerHue ?: DEFAULT_MARKER_HUE,
                item.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                item.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
            )
        )
    }
}
