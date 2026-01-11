package com.szabolcshorvath.memorymap.adapter

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.databinding.ItemTimelineDateSeparatorBinding
import com.szabolcshorvath.memorymap.databinding.ItemTimelineMemoryBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class TimelineAdapter(
    private var memoryGroups: List<MemoryGroup>,
    private val onMemoryClick: (MemoryGroup) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class TimelineItem {
        data class Memory(val memoryGroup: MemoryGroup) : TimelineItem()
        data class DateSeparator(val date: LocalDate) : TimelineItem()
    }

    private var adapterItems: List<TimelineItem> = emptyList()

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        updateAdapterItems()
    }

    private fun updateAdapterItems() {
        val items = mutableListOf<TimelineItem>()
        var lastDate: LocalDate? = null

        memoryGroups.forEach { group ->
            val currentDate = group.startDate.toLocalDate()
            if (lastDate == null || currentDate != lastDate) {
                items.add(TimelineItem.DateSeparator(currentDate))
                lastDate = currentDate
            }
            items.add(TimelineItem.Memory(group))
        }
        adapterItems = items
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
            binding.root.setOnClickListener { onMemoryClick(memoryGroup) }
        }

        fun flash() {
            val originalColor = binding.root.cardBackgroundColor.defaultColor
            val flashColor = "#80AAAAAA".toColorInt()

            val colorAnim = ValueAnimator.ofObject(
                ArgbEvaluator(),
                originalColor, flashColor, originalColor
            )
            colorAnim.duration = 1000
            colorAnim.addUpdateListener { animator ->
                binding.root.setCardBackgroundColor(animator.animatedValue as Int)
            }

            colorAnim.start()
        }
    }

    inner class DateSeparatorViewHolder(private val binding: ItemTimelineDateSeparatorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: LocalDate) {
            binding.dateText.text = date.format(dateFormatter)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (adapterItems[position]) {
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
        when (val item = adapterItems[position]) {
            is TimelineItem.Memory -> (holder as TimelineViewHolder).bind(item.memoryGroup)
            is TimelineItem.DateSeparator -> (holder as DateSeparatorViewHolder).bind(item.date)
        }
    }

    override fun getItemCount(): Int = adapterItems.size

    fun updateData(newGroups: List<MemoryGroup>) {
        memoryGroups = newGroups
        updateAdapterItems()
        notifyDataSetChanged()
    }

    fun getPositionForId(id: Int): Int {
        return adapterItems.indexOfFirst { 
            it is TimelineItem.Memory && it.memoryGroup.id == id 
        }
    }

    companion object {
        private const val VIEW_TYPE_MEMORY = 0
        private const val VIEW_TYPE_SEPARATOR = 1

        private val dateFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
}