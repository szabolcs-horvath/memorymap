package com.szabolcshorvath.memorymap.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.databinding.ItemMemoryOverlayBinding
import com.szabolcshorvath.memorymap.util.ColorUtil

class MemoryOverlayAdapter(
    private val memories: List<MemoryGroup>,
    private val onDetailsClick: (Int) -> Unit
) : RecyclerView.Adapter<MemoryOverlayAdapter.MemoryOverlayViewHolder>() {

    class MemoryOverlayViewHolder(val binding: ItemMemoryOverlayBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryOverlayViewHolder {
        val binding = ItemMemoryOverlayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MemoryOverlayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemoryOverlayViewHolder, position: Int) {
        val memory = memories[position]
        holder.binding.memoryTitle.text = memory.title
        holder.binding.memoryDate.text = memory.getFormattedDate()

        holder.binding.colorIndicator.backgroundTintList = ColorStateList.valueOf(
            ColorUtil.hueToColor(memory.markerHue ?: BitmapDescriptorFactory.HUE_RED)
        )

        holder.binding.btnDetails.setOnClickListener {
            onDetailsClick(memory.id)
        }

        holder.binding.root.setOnClickListener {
            onDetailsClick(memory.id)
        }
    }

    override fun getItemCount() = memories.size
}