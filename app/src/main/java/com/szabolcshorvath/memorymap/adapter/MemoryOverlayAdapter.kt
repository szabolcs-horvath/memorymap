package com.szabolcshorvath.memorymap.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.databinding.ItemMemoryOverlayBinding
import com.szabolcshorvath.memorymap.util.ColorUtil

class MemoryOverlayAdapter(
    private val onDetailsClick: (Int) -> Unit
) : ListAdapter<MemoryGroup, MemoryOverlayAdapter.MemoryOverlayViewHolder>(MemoryGroupDiffCallback()) {

    class MemoryOverlayViewHolder(val binding: ItemMemoryOverlayBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryOverlayViewHolder {
        val binding = ItemMemoryOverlayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MemoryOverlayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemoryOverlayViewHolder, position: Int) {
        val memory = getItem(position)
        bindTitle(holder, memory)
        bindDate(holder, memory)
        bindColor(holder, memory)

        holder.binding.btnDetails.setOnClickListener {
            onDetailsClick(memory.id)
        }

        holder.binding.root.setOnClickListener {
            onDetailsClick(memory.id)
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
            val memory = getItem(position)

            @Suppress("UNCHECKED_CAST")
            val changes = payloads.first() as Set<String>
            if (changes.contains(TITLE_DIFF_PAYLOAD)) bindTitle(holder, memory)
            if (changes.contains(DATE_DIFF_PAYLOAD)) bindDate(holder, memory)
            if (changes.contains(COLOR_DIFF_PAYLOAD)) bindColor(holder, memory)
        }
    }

    private fun bindTitle(holder: MemoryOverlayViewHolder, memory: MemoryGroup) {
        holder.binding.memoryTitle.text = memory.title
    }

    private fun bindDate(holder: MemoryOverlayViewHolder, memory: MemoryGroup) {
        holder.binding.memoryDate.text = memory.getFormattedDate()
    }

    private fun bindColor(holder: MemoryOverlayViewHolder, memory: MemoryGroup) {
        holder.binding.colorIndicator.backgroundTintList = ColorStateList.valueOf(
            ColorUtil.hueToColor(memory.markerHue ?: BitmapDescriptorFactory.HUE_RED)
        )
    }

    private class MemoryGroupDiffCallback : DiffUtil.ItemCallback<MemoryGroup>() {
        override fun areItemsTheSame(oldItem: MemoryGroup, newItem: MemoryGroup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MemoryGroup, newItem: MemoryGroup): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: MemoryGroup, newItem: MemoryGroup): Any? {
            val diff = mutableSetOf<String>()
            if (oldItem.title != newItem.title) diff.add(TITLE_DIFF_PAYLOAD)
            if (oldItem.startDate != newItem.startDate || oldItem.endDate != newItem.endDate) diff.add(
                DATE_DIFF_PAYLOAD
            )
            if (oldItem.markerHue != newItem.markerHue) diff.add(COLOR_DIFF_PAYLOAD)
            return if (diff.isEmpty()) null else diff
        }
    }

    companion object {
        const val TAG = "MemoryOverlayAdapter"
        const val TITLE_DIFF_PAYLOAD = "TITLE"
        const val DATE_DIFF_PAYLOAD = "DATE"
        const val COLOR_DIFF_PAYLOAD = "COLOR"
    }
}