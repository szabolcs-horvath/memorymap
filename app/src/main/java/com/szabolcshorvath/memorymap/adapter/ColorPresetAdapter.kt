package com.szabolcshorvath.memorymap.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.databinding.ItemColorPresetBinding
import com.szabolcshorvath.memorymap.util.ColorUtil

class ColorPresetAdapter(
    var onPresetClick: ((HSVPreset) -> Unit)? = null
) : ListAdapter<HSVPreset, ColorPresetAdapter.ViewHolder>(DiffCallback()) {

    private var selectedPresetId: Int? = null

    class ViewHolder(val binding: ItemColorPresetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemColorPresetBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val preset = getItem(position)
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density
        val isSelected = preset.id == selectedPresetId

        val color = ColorUtil.hsvToColor(preset.hue, preset.saturation, preset.brightness)
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            val strokeWidth = if (isSelected) (3 * density).toInt() else (1 * density).toInt()
            val strokeColor = if (isSelected) Color.BLACK else Color.LTGRAY
            setStroke(strokeWidth, strokeColor)
        }
        holder.binding.colorView.background = shape

        holder.binding.colorView.setOnClickListener {
            onPresetClick?.invoke(preset)
        }
    }

    fun setSelectedPresetId(id: Int?) {
        val oldId = selectedPresetId
        if (oldId == id) return

        selectedPresetId = id

        // Find positions to update for visual feedback
        if (oldId != null) {
            val oldPos = currentList.indexOfFirst { it.id == oldId }
            if (oldPos != -1) notifyItemChanged(oldPos)
        }
        if (id != null) {
            val newPos = currentList.indexOfFirst { it.id == id }
            if (newPos != -1) notifyItemChanged(newPos)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<HSVPreset>() {
        override fun areItemsTheSame(oldItem: HSVPreset, newItem: HSVPreset) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: HSVPreset, newItem: HSVPreset) = oldItem == newItem
    }
}
