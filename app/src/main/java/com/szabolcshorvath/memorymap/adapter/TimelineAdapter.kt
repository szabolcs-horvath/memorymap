package com.szabolcshorvath.memorymap.adapter

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.databinding.ItemTimelineDateSeparatorBinding
import com.szabolcshorvath.memorymap.databinding.ItemTimelineMemoryBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import java.time.LocalDate

class TimelineAdapter(private val onMemoryClick: (MemoryGroup) -> Unit) :
    ListAdapter<TimelineAdapter.TimelineItem, RecyclerView.ViewHolder>(TimelineItem.TimelineDiffCallback()) {

    sealed class TimelineItem {
        data class Memory(val memoryGroup: MemoryGroup) : TimelineItem()
        data class DateSeparator(val date: LocalDate) : TimelineItem()

        fun getItemId(): String {
            return when (this) {
                is Memory -> "memory_${memoryGroup.id}"
                is DateSeparator -> "date_$date"
            }
        }

        class TimelineDiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
            override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
                return oldItem.getItemId() == newItem.getItemId()
            }

            override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    private fun generateTimelineItems(groups: List<MemoryGroup>): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()
        var lastDate: LocalDate? = null

        groups.forEach { group ->
            val currentDate = group.startDate.toLocalDate()
            if (lastDate == null || currentDate != lastDate) {
                items.add(TimelineItem.DateSeparator(currentDate))
                lastDate = currentDate
            }
            items.add(TimelineItem.Memory(group))
        }
        return items
    }

    inner class TimelineViewHolder(private val binding: ItemTimelineMemoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(memoryGroup: MemoryGroup) {
            binding.memoryTitle.text = memoryGroup.title

            if (!memoryGroup.placeName.isNullOrEmpty()) {
                binding.memoryLocation.text = memoryGroup.placeName
                binding.memoryLocation.visibility = View.VISIBLE
            } else if (!memoryGroup.address.isNullOrEmpty()) {
                binding.memoryLocation.text = memoryGroup.address
                binding.memoryLocation.visibility = View.VISIBLE
            } else {
                binding.memoryLocation.visibility = View.GONE
            }

            if (!memoryGroup.description.isNullOrEmpty()) {
                binding.memoryDescription.text = memoryGroup.description
                binding.memoryDescription.visibility = View.VISIBLE
            } else {
                binding.memoryDescription.visibility = View.GONE
            }

            binding.memoryDate.text = memoryGroup.getFormattedDate()

            binding.colorIndicator.setBackgroundColor(
                ColorUtil.hsvToColor(
                    memoryGroup.markerHue ?: DEFAULT_MARKER_HUE,
                    memoryGroup.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                    memoryGroup.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS
                )
            )

            binding.root.setOnClickListener { onMemoryClick(memoryGroup) }
        }

        fun flash() {
            val originalColor = binding.root.cardBackgroundColor.defaultColor
            val flashColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)

            val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), originalColor, flashColor, originalColor)
            colorAnim.duration = FLASH_ANIMATION_DURATION_MILLIS
            colorAnim.addUpdateListener { animator ->
                binding.root.setCardBackgroundColor(animator.animatedValue as Int)
            }

            colorAnim.start()
        }
    }

    class DateSeparatorViewHolder(private val binding: ItemTimelineDateSeparatorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(date: LocalDate) {
            binding.dateText.text = date.format(dateFormatter())
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TimelineItem.Memory -> VIEW_TYPE_MEMORY
            is TimelineItem.DateSeparator -> VIEW_TYPE_SEPARATOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MEMORY -> {
                val binding = ItemTimelineMemoryBinding.inflate(inflater, parent, false)
                TimelineViewHolder(binding)
            }

            VIEW_TYPE_SEPARATOR -> {
                val binding = ItemTimelineDateSeparatorBinding.inflate(inflater, parent, false)
                DateSeparatorViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TimelineItem.Memory -> (holder as TimelineViewHolder).bind(item.memoryGroup)
            is TimelineItem.DateSeparator -> (holder as DateSeparatorViewHolder).bind(item.date)
        }
    }

    fun updateData(newGroups: List<MemoryGroup>, commitCallback: Runnable? = null) {
        submitList(generateTimelineItems(newGroups), commitCallback)
    }

    fun getPositionForId(id: Int): Int {
        for (i in 0 until itemCount) {
            val item = currentList[i]
            if (item is TimelineItem.Memory && item.memoryGroup.id == id) {
                return i
            }
        }
        return -1
    }

    companion object {
        private const val FLASH_ANIMATION_DURATION_MILLIS = 1000L
        private const val VIEW_TYPE_MEMORY = 0
        private const val VIEW_TYPE_SEPARATOR = 1
    }
}
