package com.szabolcshorvath.memorymap.adapter

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.szabolcshorvath.memorymap.databinding.ItemMemoryFragmentEditBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment.AddMemoryListener
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment.FragmentEditState
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.timeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MemoryFragmentEditAdapter(
    private val fragments: MutableList<FragmentEditState>,
    private val resources: Resources,
    private val getListener: () -> AddMemoryListener?,
    private val getChildFragmentManager: () -> FragmentManager,
    private val setActivePickingIndex: (Int) -> Unit,
    private val updateFragmentsUI: () -> Unit
) : ListAdapter<FragmentEditState, MemoryFragmentEditAdapter.MemoryFragmentEditViewHolder>(
    FragmentDiffCallback()
) {
    class MemoryFragmentEditViewHolder(val binding: ItemMemoryFragmentEditBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MemoryFragmentEditViewHolder(
            ItemMemoryFragmentEditBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: MemoryFragmentEditViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding

        binding.locationText.text = if (!item.placeName.isNullOrEmpty()) {
            if (!item.address.isNullOrEmpty()) "${item.placeName}\n${item.address}" else item.placeName
        } else {
            "Coordinates: ${item.latitude} ${item.longitude}"
        }

        binding.selectLocationButton.setOnClickListener {
            setActivePickingIndex(holder.bindingAdapterPosition)
            getListener()?.onPickLocation(item.latitude, item.longitude)
        }

        binding.removeButton.setOnClickListener {
            fragments.removeAt(holder.bindingAdapterPosition)
            updateFragmentsUI()
        }

        binding.toggleTimeButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            val current = getItem(pos)
            val newStart =
                if (!current.isTimeVisible && current.startDate == null) ZonedDateTime.now() else current.startDate
            val newEnd =
                if (!current.isTimeVisible && current.endDate == null) {
                    ZonedDateTime.now()
                        .plusHours(1)
                } else {
                    current.endDate
                }

            fragments[pos] = current.copy(
                isTimeVisible = !current.isTimeVisible,
                startDate = newStart,
                endDate = newEnd
            )
            updateFragmentsUI()
        }
        binding.timeSection.visibility = if (item.isTimeVisible) View.VISIBLE else View.GONE

        binding.allDayCheckbox.setOnCheckedChangeListener(null)
        binding.allDayCheckbox.isChecked = item.isAllDay
        binding.allDayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            val current = getItem(pos)
            fragments[pos] = current.copy(isAllDay = isChecked)
            updateFragmentsUI()
        }

        updateFragmentDateTimeUI(binding, item)

        binding.startDateButton.setOnClickListener {
            pickFragmentDate(
                holder.itemView.context,
                holder.bindingAdapterPosition,
                true
            )
        }
        binding.startTimeButton.setOnClickListener {
            pickFragmentTime(
                holder.itemView.context,
                holder.bindingAdapterPosition,
                true
            )
        }
        binding.endDateButton.setOnClickListener {
            pickFragmentDate(
                holder.itemView.context,
                holder.bindingAdapterPosition,
                false
            )
        }
        binding.endTimeButton.setOnClickListener {
            pickFragmentTime(
                holder.itemView.context,
                holder.bindingAdapterPosition,
                false
            )
        }
        binding.dateRangeButton.setOnClickListener {
            pickFragmentDateRange(holder.bindingAdapterPosition)
        }

        binding.hueSlider.value = item.markerHue
        binding.hueSlider.thumbTintList =
            ColorStateList.valueOf(ColorUtil.hueToColor(item.markerHue))

        binding.hueSlider.clearOnSliderTouchListeners()
        binding.hueSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val pos = holder.bindingAdapterPosition
                val current = getItem(pos)
                fragments[pos] = current.copy(markerHue = value)
                binding.hueSlider.thumbTintList =
                    ColorStateList.valueOf(ColorUtil.hueToColor(value))
            }
        }

        setupFragmentPresetColors(binding, holder.bindingAdapterPosition)
    }

    private fun updateFragmentDateTimeUI(
        binding: ItemMemoryFragmentEditBinding,
        item: FragmentEditState
    ) {
        val dateFormatter = dateFormatter()
        val timeFormatter = timeFormatter()

        if (item.isAllDay) {
            binding.startDateTimeLayout.visibility = View.GONE
            binding.endDateTimeLayout.visibility = View.GONE
            binding.dateRangeButton.visibility = View.VISIBLE

            val start = item.startDate ?: ZonedDateTime.now()
            val end = item.endDate ?: ZonedDateTime.now().plusHours(1)
            val startStr = start.format(dateFormatter)
            val endStr = end.format(dateFormatter)
            binding.dateRangeButton.text =
                if (startStr == endStr) startStr else "$startStr - $endStr"
        } else {
            binding.startDateTimeLayout.visibility = View.VISIBLE
            binding.endDateTimeLayout.visibility = View.VISIBLE
            binding.dateRangeButton.visibility = View.GONE

            val start = item.startDate ?: ZonedDateTime.now()
            val end = item.endDate ?: ZonedDateTime.now().plusHours(1)
            binding.startDateButton.text = start.format(dateFormatter)
            binding.startTimeButton.text = start.format(timeFormatter)
            binding.endDateButton.text = end.format(dateFormatter)
            binding.endTimeButton.text = end.format(timeFormatter)
        }
    }

    private fun setupFragmentPresetColors(
        binding: ItemMemoryFragmentEditBinding,
        position: Int
    ) {
        binding.presetColorsLayout.removeAllViews()
        val size = (32 * resources.displayMetrics.density).toInt()
        val margin = (12 * resources.displayMetrics.density).toInt()

        ColorUtil.COLOR_PRESETS.forEach { hue ->
            val view = View(binding.root.context)
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(0, 0, margin, 0)
            view.layoutParams = params

            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            shape.setColor(ColorUtil.hueToColor(hue))
            shape.setStroke((1 * resources.displayMetrics.density).toInt(), Color.LTGRAY)
            view.background = shape

            view.setOnClickListener {
                val current = getItem(position)
                fragments[position] = current.copy(markerHue = hue)
                updateFragmentsUI()
            }
            binding.presetColorsLayout.addView(view)
        }
    }

    private fun pickFragmentDateRange(index: Int) {
        val item = fragments[index]
        val builder =
            MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select Date Range")
        val start = item.startDate ?: ZonedDateTime.now()
        val end = item.endDate ?: ZonedDateTime.now().plusHours(1)
        val selection = androidx.core.util.Pair(
            start.toInstant().toEpochMilli(),
            end.toInstant().toEpochMilli()
        )
        builder.setSelection(selection)
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { range ->
            if (range.first != null && range.second != null) {
                val current = fragments[index]
                fragments[index] = current.copy(
                    startDate = Instant.ofEpochMilli(range.first!!)
                        .atZone(ZoneId.systemDefault()),
                    endDate = Instant.ofEpochMilli(range.second!!)
                        .atZone(ZoneId.systemDefault())
                )
                updateFragmentsUI()
            }
        }
        picker.show(getChildFragmentManager(), "DATE_RANGE_PICKER")
    }

    private fun pickFragmentDate(context: Context, index: Int, isStart: Boolean) {
        val item = fragments[index]
        val current = (if (isStart) item.startDate else item.endDate) ?: ZonedDateTime.now()
        DatePickerDialog(context, { _, year, month, dayOfMonth ->
            val newDate = LocalDate.of(year, month + 1, dayOfMonth)
            val currentItem = fragments[index]
            if (isStart) {
                val newStart =
                    newDate.atTime(current.toLocalTime()).atZone(ZoneId.systemDefault())
                var newEnd = currentItem.endDate
                if (newEnd != null && newEnd.isBefore(newStart)) newEnd = newStart.plusHours(1)
                fragments[index] = currentItem.copy(startDate = newStart, endDate = newEnd)
            } else {
                val newEnd =
                    newDate.atTime(current.toLocalTime()).atZone(ZoneId.systemDefault())
                var newStart = currentItem.startDate
                if (newStart != null && newEnd.isBefore(newStart)) {
                    newStart =
                        newEnd.minusHours(1)
                }
                fragments[index] = currentItem.copy(startDate = newStart, endDate = newEnd)
            }
            updateFragmentsUI()
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    private fun pickFragmentTime(context: Context, index: Int, isStart: Boolean) {
        val item = fragments[index]
        val current = (if (isStart) item.startDate else item.endDate) ?: ZonedDateTime.now()
        TimePickerDialog(context, { _, hourOfDay, minute ->
            val newTime = LocalTime.of(hourOfDay, minute)
            val currentItem = fragments[index]
            if (isStart) {
                val newStart = current.with(newTime)
                var newEnd = currentItem.endDate
                if (newEnd != null && newEnd.isBefore(newStart)) newEnd = newStart.plusHours(1)
                fragments[index] = currentItem.copy(startDate = newStart, endDate = newEnd)
            } else {
                val newEnd = current.with(newTime)
                var newStart = currentItem.startDate
                if (newStart != null && newEnd.isBefore(newStart)) {
                    newStart =
                        newEnd.minusHours(1)
                }
                fragments[index] = currentItem.copy(startDate = newStart, endDate = newEnd)
            }
            updateFragmentsUI()
        }, current.hour, current.minute, true).show()
    }

    class FragmentDiffCallback : DiffUtil.ItemCallback<FragmentEditState>() {
        override fun areItemsTheSame(oldItem: FragmentEditState, newItem: FragmentEditState) =
            oldItem.localId == newItem.localId

        override fun areContentsTheSame(oldItem: FragmentEditState, newItem: FragmentEditState) =
            oldItem == newItem
    }
}
