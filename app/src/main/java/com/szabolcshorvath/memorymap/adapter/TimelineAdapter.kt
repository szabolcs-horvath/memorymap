package com.szabolcshorvath.memorymap.adapter

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.databinding.ItemTimelineMemoryBinding

class TimelineAdapter(
    private var memoryGroups: List<MemoryGroup>,
    private val onMemoryClick: (MemoryGroup) -> Unit
) : RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder>() {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    inner class TimelineViewHolder(private val binding: ItemTimelineMemoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(memoryGroup: MemoryGroup) {
            binding.memoryTitle.text = memoryGroup.title
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val binding = ItemTimelineMemoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimelineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        holder.bind(memoryGroups[position])
    }

    override fun getItemCount(): Int = memoryGroups.size

    fun updateData(newGroups: List<MemoryGroup>) {
        memoryGroups = newGroups
        notifyDataSetChanged()
    }

    fun getPositionForId(id: Int): Int {
        return memoryGroups.indexOfFirst { it.id == id }
    }
}