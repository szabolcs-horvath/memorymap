package com.szabolcshorvath.memorymap.adapter

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.databinding.ItemMemoryFragmentEditBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment.AddMemoryListener
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.timeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MemoryFragmentEditAdapter(
    private val fragments: MutableList<FragmentEditState>,
    private val getListener: () -> AddMemoryListener?,
    private val getChildFragmentManager: () -> FragmentManager,
    private val setActivePickingIndex: (Int) -> Unit,
    private val updateFragmentsUI: () -> Unit
) : ListAdapter<MemoryFragmentEditAdapter.FragmentEditState, MemoryFragmentEditAdapter.MemoryFragmentEditViewHolder>(FragmentEditState.FragmentDiffCallback()) {
    private var hsvPresets: List<HSVPreset> = emptyList()

    data class FragmentEditState(
        val id: Int = 0,
        val localId: String = UUID.randomUUID().toString(),
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val placeName: String? = null,
        val address: String? = null,
        val startDate: ZonedDateTime? = null,
        val endDate: ZonedDateTime? = null,
        val isAllDay: Boolean = false,
        val markerHue: Float = 0.0f,
        val markerSaturation: Float = 1.0f,
        val markerBrightness: Float = 1.0f,
        val isTimeVisible: Boolean = false,
        val isColorExpanded: Boolean = false,
        val order: Int? = null
    ) {
        class FragmentDiffCallback : DiffUtil.ItemCallback<FragmentEditState>() {
            override fun areItemsTheSame(oldItem: FragmentEditState, newItem: FragmentEditState) = oldItem.localId == newItem.localId

            override fun areContentsTheSame(oldItem: FragmentEditState, newItem: FragmentEditState) = oldItem == newItem

            override fun getChangePayload(oldItem: FragmentEditState, newItem: FragmentEditState): Any? {
                val payloads = mutableSetOf<String>()
                if (oldItem.latitude != newItem.latitude ||
                    oldItem.longitude != newItem.longitude ||
                    oldItem.placeName != newItem.placeName ||
                    oldItem.address != newItem.address
                ) {
                    payloads.add(PAYLOAD_LOCATION)
                }
                if (oldItem.startDate != newItem.startDate ||
                    oldItem.endDate != newItem.endDate ||
                    oldItem.isAllDay != newItem.isAllDay ||
                    oldItem.isTimeVisible != newItem.isTimeVisible
                ) {
                    payloads.add(PAYLOAD_DATE_TIME)
                }
                if (oldItem.markerHue != newItem.markerHue ||
                    oldItem.markerSaturation != newItem.markerSaturation ||
                    oldItem.markerBrightness != newItem.markerBrightness
                ) {
                    payloads.add(PAYLOAD_COLOR)
                }
                if (oldItem.isColorExpanded != newItem.isColorExpanded) {
                    payloads.add(PAYLOAD_COLOR_EXPANDED)
                }
                return if (payloads.isEmpty()) null else payloads
            }
        }

        companion object {
            const val PAYLOAD_LOCATION = "PAYLOAD_LOCATION"
            const val PAYLOAD_DATE_TIME = "PAYLOAD_DATE_TIME"
            const val PAYLOAD_COLOR = "PAYLOAD_COLOR"
            const val PAYLOAD_COLOR_EXPANDED = "PAYLOAD_COLOR_EXPANDED"
        }
    }

    class MemoryFragmentEditViewHolder(val binding: ItemMemoryFragmentEditBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {
        val colorPresetAdapter = ColorPresetAdapter()
        var colorAnimator: ValueAnimator? = null

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

        bindLocation(binding, item, holder)
        setupClickListeners(binding, holder, item)
        setupDateTimeSelectors(binding, holder, item)
        updateDateTimeSelectors(binding, item)
        setupColorSection(binding, item, holder)
        updateColorUI(binding, item)
    }

    override fun onBindViewHolder(
        holder: MemoryFragmentEditViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position)
            val binding = holder.binding
            val combinedPayloads = payloads.flatMap { it as? Set<*> ?: listOf(it) }.toSet()

            if (combinedPayloads.contains(FragmentEditState.PAYLOAD_LOCATION)) {
                bindLocation(binding, item, holder)
            }
            if (combinedPayloads.contains(FragmentEditState.PAYLOAD_DATE_TIME)) {
                updateDateTimeSelectors(binding, item)
            }
            if (combinedPayloads.contains(FragmentEditState.PAYLOAD_COLOR)) {
                // If this color update came from a preset click, it might be already being animated.
                // However, the standard updateColorUI will just set values.
                // If we want to support animation here, we'd need a way to distinguish.
                // For now, we only animate when the preset click itself triggers it.
                updateColorUI(binding, item)
            }
            if (combinedPayloads.contains(FragmentEditState.PAYLOAD_COLOR_EXPANDED)) {
                updateColorExpansion(binding, item)
            }
        }
    }

    private fun bindLocation(binding: ItemMemoryFragmentEditBinding, item: FragmentEditState, holder: MemoryFragmentEditViewHolder) {
        binding.locationText.text = if (!item.placeName.isNullOrEmpty()) {
            if (!item.address.isNullOrEmpty()) "${item.placeName}\n${item.address}" else item.placeName
        } else {
            "Coordinates: ${item.latitude} ${item.longitude}"
        }
    }

    private fun setupClickListeners(binding: ItemMemoryFragmentEditBinding, holder: MemoryFragmentEditViewHolder, item: FragmentEditState) {
        binding.selectLocationButton.setOnClickListener {
            setActivePickingIndex(holder.bindingAdapterPosition)
            getListener()?.onPickLocation(item.latitude, item.longitude)
        }

        binding.removeButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                fragments.removeAt(pos)
                updateFragmentsUI()
            }
        }

        binding.colorHeader.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val current = getItem(pos)
                fragments[pos] = current.copy(isColorExpanded = !current.isColorExpanded)
                updateFragmentsUI()
            }
        }
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

    private fun updateColorExpansion(binding: ItemMemoryFragmentEditBinding, item: FragmentEditState) {
        binding.colorExpandedContent.visibility = if (item.isColorExpanded) View.VISIBLE else View.GONE
        binding.colorChevron.rotation = if (item.isColorExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION
    }

    private fun updateColorUI(binding: ItemMemoryFragmentEditBinding, item: FragmentEditState) {
        updateColorExpansion(binding, item)

        val color = ColorUtil.hsvToColor(item.markerHue, item.markerSaturation, item.markerBrightness)
        val colorStateList = ColorStateList.valueOf(color)

        binding.colorIndicator.setBackgroundColor(color)

        binding.hueSlider.value = item.markerHue
        binding.saturationSlider.value = item.markerSaturation
        binding.brightnessSlider.value = item.markerBrightness

        binding.hueSlider.thumbTintList = colorStateList
        binding.saturationSlider.thumbTintList = colorStateList
        binding.brightnessSlider.thumbTintList = colorStateList

        updateValueTexts(binding, item.markerHue, item.markerSaturation, item.markerBrightness)
    }

    private fun updateValueTexts(binding: ItemMemoryFragmentEditBinding, h: Float, s: Float, v: Float) {
        binding.tvHueValue.text = h.toInt().toString()
        binding.tvSaturationValue.text = String.format(Locale.getDefault(), "%.2f", s)
        binding.tvBrightnessValue.text = String.format(Locale.getDefault(), "%.2f", v)
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
        updateValueTexts(binding, newH, newS, newV)
    }

    private fun updateDateTimeSelectors(binding: ItemMemoryFragmentEditBinding, item: FragmentEditState) {
        binding.timeSection.visibility = if (item.isTimeVisible) View.VISIBLE else View.GONE

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
                animateFragmentColorToTargets(holder, preset)
                
                // Still update the underlying data so it's persisted/ready for save
                val current = fragments[pos]
                fragments[pos] = current.copy(
                    markerHue = preset.hue,
                    markerSaturation = preset.saturation,
                    markerBrightness = preset.brightness
                )
                // We don't call updateFragmentsUI() here immediately to avoid re-binding during animation
            }
        }
    }

    private fun animateFragmentColorToTargets(holder: MemoryFragmentEditViewHolder, targetPreset: HSVPreset) {
        val binding = holder.binding
        val startH = binding.hueSlider.value
        val startS = binding.saturationSlider.value
        val startV = binding.brightnessSlider.value

        holder.colorAnimator?.cancel()
        holder.colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOR_CHANGE_ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val fraction = animator.animatedFraction

                val currentH = lerpWithStep(startH, targetPreset.hue, fraction, binding.hueSlider.stepSize)
                val currentS = lerpWithStep(startS, targetPreset.saturation, fraction, binding.saturationSlider.stepSize)
                val currentV = lerpWithStep(startV, targetPreset.brightness, fraction, binding.brightnessSlider.stepSize)

                binding.hueSlider.value = currentH
                binding.saturationSlider.value = currentS
                binding.brightnessSlider.value = currentV

                val currentColor = ColorUtil.hsvToColor(currentH, currentS, currentV)
                binding.colorIndicator.setBackgroundColor(currentColor)

                val currentStateList = ColorStateList.valueOf(currentColor)
                binding.hueSlider.thumbTintList = currentStateList
                binding.saturationSlider.thumbTintList = currentStateList
                binding.brightnessSlider.thumbTintList = currentStateList

                updateValueTexts(binding, currentH, currentS, currentV)
            }
            start()
        }
    }

    private fun lerpWithStep(start: Float, end: Float, fraction: Float, stepSize: Float): Float {
        val rawValue = start + (end - start) * fraction
        return if (stepSize > 0) {
            ((rawValue / stepSize).roundToInt() * stepSize)
        } else {
            rawValue
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
        private const val COLOR_CHANGE_ANIMATION_DURATION = 300L
    }
}
