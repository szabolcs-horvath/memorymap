package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.MainActivity
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.databinding.ItemMemoryFragmentBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryFragmentAdapter(
    private val onShowOnMapClick: (MemoryFragment) -> Unit
) : RecyclerView.Adapter<MemoryFragmentAdapter.ViewHolder>() {

    private val items = mutableListOf<MemoryFragment>()

    init {
        setHasStableIds(true)
    }

    inner class ViewHolder(private val binding: ItemMemoryFragmentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(fragment: MemoryFragment) {
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
                ColorUtil.hueToColor(fragment.markerHue ?: 0f)
            )

            binding.btnShowOnMap.setOnClickListener {
                onShowOnMapClick(fragment)
            }

            // Check if fragment markers are enabled to show/hide the button
            CoroutineScope(Dispatchers.IO).launch {
                val showMarkers = binding.root.context.dataStore.data
                    .map { it[MainActivity.SHOW_FRAGMENT_MARKERS] ?: false }
                    .first()
                withContext(Dispatchers.Main) {
                    binding.btnShowOnMap.visibility = if (showMarkers) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemMemoryFragmentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
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
