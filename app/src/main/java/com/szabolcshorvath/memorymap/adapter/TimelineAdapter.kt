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
import com.google.android.material.listitem.ListItemViewHolder
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
        data class Memory(
            val memoryGroup: MemoryGroup,
            val sectionPosition: Int = 0,
            val sectionCount: Int = 1
        ) : TimelineItem()

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
        if (groups.isEmpty()) return items

        var i = 0
        while (i < groups.size) {
            val currentDate = groups[i].startDate.toLocalDate()
            items.add(TimelineItem.DateSeparator(currentDate))

            val section = mutableListOf<MemoryGroup>()
            while (i < groups.size && groups[i].startDate.toLocalDate() == currentDate) {
                section.add(groups[i])
                i++
            }

            section.forEachIndexed { index, memoryGroup ->
                items.add(TimelineItem.Memory(memoryGroup, index, section.size))
            }
        }
        return items
    }

    inner class TimelineViewHolder(private val binding: ItemTimelineMemoryBinding) :
        ListItemViewHolder(binding.root) {
        fun bind(memoryGroup: MemoryGroup, sectionPosition: Int, sectionCount: Int) {
            bind(sectionPosition, sectionCount)
            binding.timelineCard.clipToOutline = true
            binding.tvMemoryTitle.text = memoryGroup.title

            if (!memoryGroup.placeName.isNullOrEmpty()) {
                binding.tvMemoryLocation.text = memoryGroup.placeName
                binding.tvMemoryLocation.visibility = View.VISIBLE
            } else if (!memoryGroup.address.isNullOrEmpty()) {
                binding.tvMemoryLocation.text = memoryGroup.address
                binding.tvMemoryLocation.visibility = View.VISIBLE
            } else {
                binding.tvMemoryLocation.visibility = View.GONE
            }

            if (!memoryGroup.description.isNullOrEmpty()) {
                binding.tvMemoryDescription.text = memoryGroup.description
                binding.tvMemoryDescription.visibility = View.VISIBLE
            } else {
                binding.tvMemoryDescription.visibility = View.GONE
            }

            binding.tvMemoryDate.text = memoryGroup.getFormattedDate()

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
            val originalColor = binding.timelineCard.cardBackgroundColor.defaultColor
            val flashColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)

            val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), originalColor, flashColor, originalColor)
            colorAnim.duration = FLASH_ANIMATION_DURATION_MILLIS
            colorAnim.addUpdateListener { animator ->
                binding.timelineCard.setCardBackgroundColor(animator.animatedValue as Int)
            }

            colorAnim.start()
        }
    }

    class DateSeparatorViewHolder(private val binding: ItemTimelineDateSeparatorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(date: LocalDate) {
            binding.tvDate.text = date.format(dateFormatter())
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
            is TimelineItem.Memory -> (holder as TimelineViewHolder).bind(item.memoryGroup, item.sectionPosition, item.sectionCount)
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
