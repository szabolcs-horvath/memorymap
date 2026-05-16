package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.listitem.ListItemViewHolder
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.databinding.ItemMemoryFragmentBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryFragmentAdapter(private val onShowOnMapClick: (MemoryFragment) -> Unit) : RecyclerView.Adapter<MemoryFragmentAdapter.MemoryFragmentViewHolder>() {

    private val items = mutableListOf<MemoryFragment>()

    init {
        setHasStableIds(true)
    }

    class MemoryFragmentViewHolder(val binding: ItemMemoryFragmentBinding) : ListItemViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryFragmentViewHolder {
        val binding = ItemMemoryFragmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemoryFragmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemoryFragmentViewHolder, position: Int) {
        holder.bind(position, itemCount)
        val binding = holder.binding
        binding.fragmentCard.clipToOutline = true
        val fragment = items[position]

        binding.locationText.text = if (!fragment.placeName.isNullOrEmpty()) {
            fragment.placeName
        } else {
            "${fragment.latitude}, ${fragment.longitude}"
        }

        val dateText = fragment.getFormattedDate()
        if (dateText != null) {
            binding.timeText.text = dateText
            binding.timeText.visibility = View.VISIBLE
        } else {
            binding.timeText.visibility = View.GONE
        }

        binding.colorIndicator.setBackgroundColor(
            ColorUtil.hsvToColor(
                fragment.markerHue ?: DEFAULT_MARKER_HUE,
                fragment.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                fragment.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
            )
        )

        binding.btnShowOnMap.setOnClickListener {
            onShowOnMapClick(fragment)
        }

        // Check if fragment markers are enabled to show/hide the button
        CoroutineScope(Dispatchers.IO).launch {
            val showMarkers = binding.root.context.dataStore.data
                .map { it[PreferencesKeys.SHOW_FRAGMENT_MARKERS] ?: false }
                .first()
            withContext(Dispatchers.Main) {
                binding.btnShowOnMap.visibility = if (showMarkers) View.VISIBLE else View.GONE
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        return if (position in items.indices) items[position].id.toLong() else RecyclerView.NO_ID
    }

    fun updateData(newItems: List<MemoryFragment>) {
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
        val diffResult = DiffUtil.calculateDiff(diffCallback, true)
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in items.indices && toPosition in items.indices && fromPosition != toPosition) {
            val item = items.removeAt(fromPosition)
            items.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
        }
    }
}
