package com.szabolcshorvath.memorymap.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.databinding.ItemMemoryOverlayBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE

class MemoryOverlayAdapter(
    private val onDetailsClick: (Int) -> Unit
) : ListAdapter<Markerable, MemoryOverlayAdapter.MemoryOverlayViewHolder>(MarkerableDiffCallback()) {

    class MemoryOverlayViewHolder(val binding: ItemMemoryOverlayBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryOverlayViewHolder {
        val binding = ItemMemoryOverlayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MemoryOverlayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemoryOverlayViewHolder, position: Int) {
        val item = getItem(position)
        bindTitle(holder, item)
        bindDate(holder, item)
        bindColor(holder, item)

        holder.binding.btnDetails.setOnClickListener {
            onDetailsClick(item.groupId)
        }

        holder.binding.root.setOnClickListener {
            onDetailsClick(item.groupId)
        }
    }

    override fun onBindViewHolder(
        holder: MemoryOverlayViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position)

            @Suppress("UNCHECKED_CAST")
            val changes = payloads.first() as Set<String>
            if (changes.contains(TITLE_DIFF_PAYLOAD)) bindTitle(holder, item)
            if (changes.contains(DATE_DIFF_PAYLOAD)) bindDate(holder, item)
            if (changes.contains(COLOR_DIFF_PAYLOAD)) bindColor(holder, item)
        }
    }

    private fun bindTitle(holder: MemoryOverlayViewHolder, item: Markerable) {
        holder.binding.memoryTitle.text = item.title
    }

    private fun bindDate(holder: MemoryOverlayViewHolder, item: Markerable) {
        val dateText = item.getFormattedDate()
        holder.binding.memoryDate.text = dateText
        holder.binding.memoryDate.visibility =
            if (dateText.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun bindColor(holder: MemoryOverlayViewHolder, item: Markerable) {
        holder.binding.colorIndicator.backgroundTintList = ColorStateList.valueOf(
            ColorUtil.hueToColor(item.markerHue ?: DEFAULT_MARKER_HUE)
        )
    }

    private class MarkerableDiffCallback : DiffUtil.ItemCallback<Markerable>() {
        override fun areItemsTheSame(oldItem: Markerable, newItem: Markerable): Boolean {
            return oldItem.groupId == newItem.groupId &&
                oldItem.latitude == newItem.latitude &&
                oldItem.longitude == newItem.longitude
        }

        override fun areContentsTheSame(oldItem: Markerable, newItem: Markerable): Boolean {
            return oldItem.title == newItem.title &&
                oldItem.startDate == newItem.startDate &&
                oldItem.endDate == newItem.endDate &&
                oldItem.markerHue == newItem.markerHue &&
                oldItem.latitude == newItem.latitude &&
                oldItem.longitude == newItem.longitude
        }

        override fun getChangePayload(oldItem: Markerable, newItem: Markerable): Any? {
            val diff = mutableSetOf<String>()
            if (oldItem.title != newItem.title) diff.add(TITLE_DIFF_PAYLOAD)
            if (oldItem.startDate != newItem.startDate || oldItem.endDate != newItem.endDate) {
                diff.add(
                    DATE_DIFF_PAYLOAD
                )
            }
            if (oldItem.markerHue != newItem.markerHue) diff.add(COLOR_DIFF_PAYLOAD)
            return if (diff.isEmpty()) null else diff
        }
    }

    companion object {
        const val TITLE_DIFF_PAYLOAD = "TITLE"
        const val DATE_DIFF_PAYLOAD = "DATE"
        const val COLOR_DIFF_PAYLOAD = "COLOR"
    }
}
