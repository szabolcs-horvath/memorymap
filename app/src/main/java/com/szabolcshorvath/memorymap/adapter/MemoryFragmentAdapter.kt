package com.szabolcshorvath.memorymap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.databinding.ItemMemoryFragmentBinding
import com.szabolcshorvath.memorymap.util.ColorUtil

class MemoryFragmentAdapter :
    ListAdapter<MemoryFragment, MemoryFragmentAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemMemoryFragmentBinding) :
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
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MemoryFragment>() {
        override fun areItemsTheSame(oldItem: MemoryFragment, newItem: MemoryFragment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MemoryFragment, newItem: MemoryFragment): Boolean {
            return oldItem == newItem
        }
    }
}
