package com.szabolcshorvath.memorymap.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.databinding.ItemColorPresetBinding
import com.szabolcshorvath.memorymap.util.ColorUtil

class ColorPresetAdapter(var onPresetClick: ((HSVPreset) -> Unit)? = null) : RecyclerView.Adapter<ColorPresetAdapter.ColorPresetViewHolder>() {

    private val items = mutableListOf<HSVPreset>()
    private var selectedPresetId: Int? = null
    private var selectedColor: FloatArray? = null

    val currentList: List<HSVPreset> get() = items

    class ColorPresetViewHolder(val binding: ItemColorPresetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorPresetViewHolder {
        val binding = ItemColorPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorPresetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorPresetViewHolder, position: Int) {
        val preset = items[position]
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density
        val isSelected = isSelected(preset)

        val color = ColorUtil.hsvToColor(preset.hue, preset.saturation, preset.brightness)
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            val strokeWidth = if (isSelected) {
                (THICK_OUTLINE_FACTOR * density)
            } else {
                (THIN_OUTLINE_FACTOR * density)
            }.toInt()
            val strokeColor = if (isSelected) Color.BLACK else Color.LTGRAY
            setStroke(strokeWidth, strokeColor)
        }
        holder.binding.colorView.background = shape
        // The colour as the sliders show it, so it isn't told by colour alone; screen readers also say "selected".
        holder.binding.colorView.contentDescription = context.getString(
            R.string.color_preset_description,
            preset.hue.toInt(),
            preset.saturation,
            preset.brightness
        )
        holder.binding.colorView.isSelected = isSelected

        holder.binding.colorView.setOnClickListener {
            onPresetClick?.invoke(preset)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<HSVPreset>, commitCallback: (() -> Unit)? = null) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].id == newItems[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = items[oldItemPosition]
                val newItem = newItems[newItemPosition]
                // Ignore the order field in comparison to avoid unnecessary animations
                // when syncing after a drag-and-drop operation.
                return oldItem.copy(order = newItem.order) == newItem
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
        commitCallback?.invoke()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in items.indices && toPosition in items.indices && fromPosition != toPosition) {
            val item = items.removeAt(fromPosition)
            items.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    /**
     * Highlights the presets of this colour: the one a form's colour was picked from, or one it was set to by hand.
     * A form calls it whenever its colour changes, so moving a slider away from a preset clears the highlight.
     */
    fun setSelectedColor(hue: Float, saturation: Float, brightness: Float) {
        val wasSelected = items.map(::isSelected)
        selectedColor = floatArrayOf(hue, saturation, brightness)
        items.forEachIndexed { index, preset -> if (isSelected(preset) != wasSelected[index]) notifyItemChanged(index) }
    }

    private fun isSelected(preset: HSVPreset): Boolean {
        val color = selectedColor
        return preset.id == selectedPresetId || (color != null && preset.hasColor(color[0], color[1], color[2]))
    }

    fun setSelectedPresetId(id: Int?) {
        val oldId = selectedPresetId
        if (oldId == id) return

        selectedPresetId = id

        if (oldId != null) {
            val oldPos = items.indexOfFirst { it.id == oldId }
            if (oldPos != -1) notifyItemChanged(oldPos)
        }
        if (id != null) {
            val newPos = items.indexOfFirst { it.id == id }
            if (newPos != -1) notifyItemChanged(newPos)
        }
    }

    companion object {
        private const val THICK_OUTLINE_FACTOR = 3f
        private const val THIN_OUTLINE_FACTOR = 1f
    }
}
