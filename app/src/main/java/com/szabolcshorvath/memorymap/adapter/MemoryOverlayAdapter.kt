package com.szabolcshorvath.memorymap.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.databinding.ItemMemoryOverlayBinding

class MemoryOverlayAdapter(
    private val memories: List<MemoryGroup>,
    private val onDetailsClick: (Int) -> Unit
) : RecyclerView.Adapter<MemoryOverlayAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemMemoryOverlayBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMemoryOverlayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val memory = memories[position]
        holder.binding.memoryTitle.text = memory.title
        holder.binding.memoryDate.text = memory.getFormattedDate()
        
        val hue = memory.markerHue ?: 0f
        val color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        holder.binding.colorIndicator.backgroundTintList = ColorStateList.valueOf(color)

        holder.binding.btnDetails.setOnClickListener {
            onDetailsClick(memory.id)
        }
        
        holder.binding.root.setOnClickListener {
            onDetailsClick(memory.id)
        }
    }

    override fun getItemCount() = memories.size
}