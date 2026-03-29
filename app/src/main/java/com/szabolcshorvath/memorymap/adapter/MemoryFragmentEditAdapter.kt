package com.szabolcshorvath.memorymap.adapter

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.szabolcshorvath.memorymap.data.HSVPreset
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
    private val getListener: () -> AddMemoryListener?,
    private val getChildFragmentManager: () -> FragmentManager,
    private val setActivePickingIndex: (Int) -> Unit,
    private val updateFragmentsUI: () -> Unit
) : ListAdapter<FragmentEditState, MemoryFragmentEditAdapter.MemoryFragmentEditViewHolder>(
    FragmentEditState.FragmentDiffCallback()
) {
    private var hsvPresets: List<HSVPreset> = emptyList()

    class MemoryFragmentEditViewHolder(val binding: ItemMemoryFragmentEditBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {
        val colorPresetAdapter = ColorPresetAdapter()

        init {
            binding.presetColorsRecyclerView.adapter = colorPresetAdapter
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryFragmentEditViewHolder {
        val binding = ItemMemoryFragmentEditBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemoryFragmentEditViewHolder(binding)
    }

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

        setupDateTimeSelectors(binding, holder, item)
        updateDateTimeSelectors(binding, item)
        setupColorSection(binding, item, holder)
    }

    fun setHSVPresets(presets: List<HSVPreset>) {
        this.hsvPresets = presets
        notifyItemRangeChanged(0, itemCount)
    }

    private fun setupDateTimeSelectors(binding: ItemMemoryFragmentEditBinding, holder: MemoryFragmentEditViewHolder, item: FragmentEditState) {
        binding.toggleTimeButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val current = getItem(pos)
            val newStart = if (!current.isTimeVisible && current.startDate == null) ZonedDateTime.now() else current.startDate
            val newEnd = if (!current.isTimeVisible && current.endDate == null) {
                ZonedDateTime.now().plusHours(1)
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
            if (pos != RecyclerView.NO_POSITION) {
                val current = getItem(pos)
                fragments[pos] = current.copy(isAllDay = isChecked)
                updateFragmentsUI()
            }
        }
        binding.startDateButton.setOnClickListener {
            pickFragmentDate(holder.itemView.context, holder.bindingAdapterPosition, true)
        }
        binding.startTimeButton.setOnClickListener {
            pickFragmentTime(holder.itemView.context, holder.bindingAdapterPosition, true)
        }
        binding.endDateButton.setOnClickListener {
            pickFragmentDate(holder.itemView.context, holder.bindingAdapterPosition, false)
        }
        binding.endTimeButton.setOnClickListener {
            pickFragmentTime(holder.itemView.context, holder.bindingAdapterPosition, false)
        }
        binding.dateRangeButton.setOnClickListener {
            pickFragmentDateRange(holder.bindingAdapterPosition)
        }
    }

    private fun setupColorSection(binding: ItemMemoryFragmentEditBinding, item: FragmentEditState, holder: MemoryFragmentEditViewHolder) {
        binding.colorExpandedContent.visibility = if (item.isColorExpanded) View.VISIBLE else View.GONE
        binding.colorChevron.rotation = if (item.isColorExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION

        binding.colorHeader.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val current = getItem(pos)
                fragments[pos] = current.copy(isColorExpanded = !current.isColorExpanded)
                updateFragmentsUI()
            }
        }

        val color = ColorUtil.hsvToColor(item.markerHue, item.markerSaturation, item.markerBrightness)
        val colorStateList = ColorStateList.valueOf(color)

        binding.colorIndicator.setBackgroundColor(color)

        binding.hueSlider.value = item.markerHue
        binding.saturationSlider.value = item.markerSaturation
        binding.brightnessSlider.value = item.markerBrightness

        binding.hueSlider.thumbTintList = colorStateList
        binding.saturationSlider.thumbTintList = colorStateList
        binding.brightnessSlider.thumbTintList = colorStateList

        binding.hueSlider.clearOnSliderTouchListeners()
        binding.hueSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateFragmentColor(binding, holder.bindingAdapterPosition, h = value)
            }
        }

        binding.saturationSlider.clearOnSliderTouchListeners()
        binding.saturationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateFragmentColor(binding, holder.bindingAdapterPosition, s = value)
            }
        }

        binding.brightnessSlider.clearOnSliderTouchListeners()
        binding.brightnessSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateFragmentColor(binding, holder.bindingAdapterPosition, v = value)
            }
        }

        setupFragmentPresetColors(holder)
    }

    private fun updateFragmentColor(binding: ItemMemoryFragmentEditBinding, position: Int, h: Float? = null, s: Float? = null, v: Float? = null) {
        if (position == RecyclerView.NO_POSITION) return
        val current = fragments[position]
        val newH = h ?: current.markerHue
        val newS = s ?: current.markerSaturation
        val newV = v ?: current.markerBrightness

        fragments[position] = current.copy(
            markerHue = newH,
            markerSaturation = newS,
            markerBrightness = newV
        )

        val color = ColorUtil.hsvToColor(newH, newS, newV)
        binding.colorIndicator.setBackgroundColor(color)
        val colorStateList = ColorStateList.valueOf(color)
        binding.hueSlider.thumbTintList = colorStateList
        binding.saturationSlider.thumbTintList = colorStateList
        binding.brightnessSlider.thumbTintList = colorStateList
    }

    private fun updateDateTimeSelectors(binding: ItemMemoryFragmentEditBinding, item: FragmentEditState) {
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

    private fun setupFragmentPresetColors(holder: MemoryFragmentEditViewHolder) {
        holder.colorPresetAdapter.submitList(hsvPresets.toList())
        holder.colorPresetAdapter.onPresetClick = { preset ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val current = fragments[pos]
                fragments[pos] = current.copy(
                    markerHue = preset.hue,
                    markerSaturation = preset.saturation,
                    markerBrightness = preset.brightness
                )
                updateFragmentsUI()
            }
        }
    }

    private fun pickFragmentDateRange(index: Int) {
        if (index == RecyclerView.NO_POSITION) return
        val item = fragments[index]
        val builder = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select Date Range")
        val start = item.startDate ?: ZonedDateTime.now()
        val end = item.endDate ?: ZonedDateTime.now().plusHours(1)
        val selection = androidx.core.util.Pair(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
        builder.setSelection(selection)
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { range ->
            if (range.first != null && range.second != null) {
                val current = fragments[index]
                fragments[index] = current.copy(
                    startDate = Instant.ofEpochMilli(range.first!!).atZone(ZoneId.systemDefault()),
                    endDate = Instant.ofEpochMilli(range.second!!).atZone(ZoneId.systemDefault())
                )
                updateFragmentsUI()
            }
        }
        picker.show(getChildFragmentManager(), "DATE_RANGE_PICKER")
    }

    private fun pickFragmentDate(context: Context, index: Int, isStart: Boolean) {
        if (index == RecyclerView.NO_POSITION) return
        val item = fragments[index]
        val current = (if (isStart) item.startDate else item.endDate) ?: ZonedDateTime.now()
        DatePickerDialog(context, { _, year, month, dayOfMonth ->
            val newDate = LocalDate.of(year, month + 1, dayOfMonth)
            val currentItem = fragments[index]
            if (isStart) {
                val newStart = newDate.atTime(current.toLocalTime()).atZone(ZoneId.systemDefault())
                var newEnd = currentItem.endDate
                if (newEnd != null && newEnd.isBefore(newStart)) newEnd = newStart.plusHours(1)
                fragments[index] = currentItem.copy(startDate = newStart, endDate = newEnd)
            } else {
                val newEnd = newDate.atTime(current.toLocalTime()).atZone(ZoneId.systemDefault())
                var newStart = currentItem.startDate
                if (newStart != null && newEnd.isBefore(newStart)) {
                    newStart = newEnd.minusHours(1)
                }
                fragments[index] = currentItem.copy(startDate = newStart, endDate = newEnd)
            }
            updateFragmentsUI()
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    private fun pickFragmentTime(context: Context, index: Int, isStart: Boolean) {
        if (index == RecyclerView.NO_POSITION) return
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
                    newStart = newEnd.minusHours(1)
                }
                fragments[index] = currentItem.copy(startDate = newStart, endDate = newEnd)
            }
            updateFragmentsUI()
        }, current.hour, current.minute, true).show()
    }

    companion object {
        private const val FACING_RIGHT_ROTATION = 0f
        private const val FACING_DOWN_ROTATION = 90f
    }
}
