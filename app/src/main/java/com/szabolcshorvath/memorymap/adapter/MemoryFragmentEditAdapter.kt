package com.szabolcshorvath.memorymap.adapter

import android.animation.ValueAnimator
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
import com.google.android.material.listitem.ListItemViewHolder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.databinding.ItemMemoryFragmentEditBinding
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment.AddMemoryGroupListener
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.timeFormatter
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MemoryFragmentEditAdapter(
    private val fragments: MutableList<FragmentEditState>,
    private val getListener: () -> AddMemoryGroupListener?,
    private val getChildFragmentManager: () -> FragmentManager,
    private val setActivePickingIndex: (Int) -> Unit,
    private val updateFragmentsUI: () -> Unit
) : ListAdapter<MemoryFragmentEditAdapter.FragmentEditState, MemoryFragmentEditAdapter.MemoryFragmentEditViewHolder>(FragmentEditState.FragmentDiffCallback()) {
    private var hsvPresets: List<HSVPreset> = emptyList()

    data class FragmentEditState(
        val id: Int = 0,
        val localId: String = UUID.randomUUID().toString(),
        val lat: Double? = null,
        val lng: Double? = null,
        val placeName: String? = null,
        val address: String? = null,
        val startDate: ZonedDateTime? = null,
        val endDate: ZonedDateTime? = null,
        val isAllDay: Boolean = false,
        val markerHue: Float = 0.0f,
        val markerSaturation: Float = 1.0f,
        val markerBrightness: Float = 1.0f,
        val isTimeVisible: Boolean = false,
        val isDateExpanded: Boolean = false,
        val isColorExpanded: Boolean = false,
        val order: Int? = null
    ) {
        class FragmentDiffCallback : DiffUtil.ItemCallback<FragmentEditState>() {
            override fun areItemsTheSame(oldItem: FragmentEditState, newItem: FragmentEditState) = oldItem.localId == newItem.localId

            override fun areContentsTheSame(oldItem: FragmentEditState, newItem: FragmentEditState) = oldItem == newItem

            @Suppress("CyclomaticComplexMethod")
            override fun getChangePayload(oldItem: FragmentEditState, newItem: FragmentEditState): Any? {
                val payloads = mutableSetOf<String>()
                if (oldItem.lat != newItem.lat ||
                    oldItem.lng != newItem.lng ||
                    oldItem.placeName != newItem.placeName ||
                    oldItem.address != newItem.address
                ) {
                    payloads.add(PAYLOAD_LOCATION)
                }
                if (oldItem.startDate != newItem.startDate ||
                    oldItem.endDate != newItem.endDate ||
                    oldItem.isAllDay != newItem.isAllDay ||
                    oldItem.isTimeVisible != newItem.isTimeVisible ||
                    oldItem.isDateExpanded != newItem.isDateExpanded
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
            const val PAYLOAD_REBIND = "PAYLOAD_REBIND"
        }
    }

    class MemoryFragmentEditViewHolder(val binding: ItemMemoryFragmentEditBinding) : ListItemViewHolder(
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

    override fun onCurrentListChanged(
        previousList: MutableList<FragmentEditState>,
        currentList: MutableList<FragmentEditState>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        // Force a re-bind of all items to update rounding based on new itemCount/position
        if (previousList.size != currentList.size) {
            notifyItemRangeChanged(0, itemCount, FragmentEditState.PAYLOAD_REBIND)
        }
    }

    override fun onBindViewHolder(holder: MemoryFragmentEditViewHolder, position: Int) {
        holder.bind(position, itemCount)
        val item = getItem(position)
        val binding = holder.binding
        binding.fragmentEditCard.clipToOutline = true
        binding.dateSectionCard.clipToOutline = true
        binding.colorSectionCard.clipToOutline = true

        bindLocation(binding, item)
        setupClickListeners(binding, holder, item)
        setupDateTimeSelectors(binding, holder, item)
        updateDateTimeSelectors(binding, item)
        setupColorSection(binding, holder)
        updateColorUI(binding, item)
    }

    override fun onBindViewHolder(
        holder: MemoryFragmentEditViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.bind(position, itemCount)
            val item = getItem(position)
            val binding = holder.binding
            val combinedPayloads = payloads.flatMap { it as? Set<*> ?: listOf(it) }.toSet()

            if (combinedPayloads.contains(FragmentEditState.PAYLOAD_LOCATION)) {
                bindLocation(binding, item)
            }
            if (combinedPayloads.contains(FragmentEditState.PAYLOAD_DATE_TIME)) {
                updateDateTimeSelectors(binding, item)
            }
            if (combinedPayloads.contains(FragmentEditState.PAYLOAD_COLOR)) {
                updateColorUI(binding, item)
            }
            if (combinedPayloads.contains(FragmentEditState.PAYLOAD_COLOR_EXPANDED)) {
                updateColorExpansion(binding, item)
            }
        }
    }

    private fun bindLocation(binding: ItemMemoryFragmentEditBinding, item: FragmentEditState) {
        binding.locationText.text = if (!item.placeName.isNullOrEmpty()) {
            if (!item.address.isNullOrEmpty()) "${item.placeName}\n${item.address}" else item.placeName
        } else if (item.lat != null && item.lng != null) {
            "Coordinates: ${item.lat} ${item.lng}"
        } else {
            "No location selected"
        }
    }

    private fun setupClickListeners(binding: ItemMemoryFragmentEditBinding, holder: MemoryFragmentEditViewHolder, item: FragmentEditState) {
        binding.selectLocationButton.setOnClickListener {
            setActivePickingIndex(holder.bindingAdapterPosition)
            getListener()?.onPickLocation(item.lat, item.lng, item.placeName, item.address)
        }

        binding.removeButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                fragments.removeAt(pos)
                updateFragmentsUI()
            }
        }

        binding.dateHeader.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                // Read from backing collection (fragments[pos]) rather than getItem(pos) to ensure
                // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
                val current = fragments[pos]
                fragments[pos] = current.copy(isDateExpanded = !current.isDateExpanded)
                updateFragmentsUI()
            }
        }

        binding.colorHeader.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                // Read from backing collection (fragments[pos]) rather than getItem(pos) to ensure
                // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
                val current = fragments[pos]
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
        binding.useSpecificTimeSwitch.setOnCheckedChangeListener(null)
        binding.useSpecificTimeSwitch.isChecked = item.isTimeVisible
        binding.useSpecificTimeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
            // Read from backing collection (fragments[pos]) rather than getItem(pos) to ensure
            // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
            val current = fragments[pos]
            val newStart = if (isChecked && current.startDate == null) ZonedDateTime.now() else current.startDate
            val newEnd = if (isChecked && current.endDate == null) {
                ZonedDateTime.now().plusHours(1)
            } else {
                current.endDate
            }

            fragments[pos] = current.copy(
                isTimeVisible = isChecked,
                startDate = newStart,
                endDate = newEnd
            )
            updateFragmentsUI()
        }

        binding.switchAllDay.setOnCheckedChangeListener(null)
        binding.switchAllDay.isChecked = item.isAllDay
        binding.switchAllDay.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                // Read from backing collection (fragments[pos]) rather than getItem(pos) to ensure
                // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
                val current = fragments[pos]
                fragments[pos] = current.copy(isAllDay = isChecked)
                updateFragmentsUI()
            }
        }
        binding.startDateButton.setOnClickListener {
            pickFragmentDate(holder.bindingAdapterPosition, true)
        }
        binding.startTimeButton.setOnClickListener {
            pickFragmentTime(holder.bindingAdapterPosition, true)
        }
        binding.endDateButton.setOnClickListener {
            pickFragmentDate(holder.bindingAdapterPosition, false)
        }
        binding.endTimeButton.setOnClickListener {
            pickFragmentTime(holder.bindingAdapterPosition, false)
        }
        binding.dateRangeButton.setOnClickListener {
            pickFragmentDateRange(holder.bindingAdapterPosition)
        }
    }

    private fun setupColorSection(binding: ItemMemoryFragmentEditBinding, holder: MemoryFragmentEditViewHolder) {
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
        binding.dateExpandedContent.visibility = if (item.isDateExpanded) View.VISIBLE else View.GONE
        binding.dateChevron.rotation = if (item.isDateExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION

        binding.timePickersLayout.visibility = if (item.isTimeVisible) View.VISIBLE else View.GONE

        val dateFormatter = dateFormatter()
        val timeFormatter = timeFormatter()

        if (!item.isTimeVisible) {
            binding.dateSummaryText.text = "Inherited from group"
        } else {
            val start = item.startDate ?: ZonedDateTime.now()
            val end = item.endDate ?: ZonedDateTime.now().plusHours(1)

            if (item.isAllDay) {
                binding.startDateTimeLayout.visibility = View.GONE
                binding.endDateTimeLayout.visibility = View.GONE
                binding.dateRangeButton.visibility = View.VISIBLE

                val startStr = start.format(dateFormatter)
                val endStr = end.format(dateFormatter)
                val summary = if (startStr == endStr) startStr else "$startStr - $endStr"
                binding.dateRangeButton.text = summary
                binding.dateSummaryText.text = summary
            } else {
                binding.startDateTimeLayout.visibility = View.VISIBLE
                binding.endDateTimeLayout.visibility = View.VISIBLE
                binding.dateRangeButton.visibility = View.GONE

                val startDStr = start.format(dateFormatter)
                val startTStr = start.format(timeFormatter)
                val endDStr = end.format(dateFormatter)
                val endTStr = end.format(timeFormatter)

                binding.startDateButton.text = startDStr
                binding.startTimeButton.text = startTStr
                binding.endDateButton.text = endDStr
                binding.endTimeButton.text = endTStr

                binding.dateSummaryText.text = if (startDStr == endDStr) {
                    "$startDStr, $startTStr - $endTStr"
                } else {
                    "$startDStr, $startTStr - $endDStr, $endTStr"
                }
            }
        }
    }

    private fun setupFragmentPresetColors(holder: MemoryFragmentEditViewHolder) {
        holder.colorPresetAdapter.submitList(hsvPresets.toList())
        holder.colorPresetAdapter.onPresetClick = { preset ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                animateFragmentColorToTargets(holder, preset)

                // Still update the underlying data so it's persisted/ready for save.
                // Read from backing collection (fragments[pos]) rather than getItem(pos) to ensure
                // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
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
        // Read from backing collection (fragments[index]) rather than getItem(index) to ensure
        // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
        val item = fragments[index]
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
        val start = item.startDate ?: ZonedDateTime.now()
        val end = item.endDate ?: ZonedDateTime.now().plusHours(1)
        val selection = androidx.core.util.Pair(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
        builder.setSelection(selection)
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { range ->
            if (range.first != null && range.second != null) {
                // Read from backing collection (fragments[index]) rather than getItem(index) to ensure
                // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
                val current = fragments[index]
                fragments[index] = current.copy(
                    startDate = Instant.ofEpochMilli(range.first!!).atZone(ZoneId.systemDefault()),
                    endDate = Instant.ofEpochMilli(range.second!!).atZone(ZoneId.systemDefault())
                )
                updateFragmentsUI()
            }
        }
        picker.show(getChildFragmentManager(), DATE_RANGE_PICKER_TAG)
    }

    private fun pickFragmentDate(index: Int, isStart: Boolean) {
        if (index == RecyclerView.NO_POSITION) return
        // Read from backing collection (fragments[index]) rather than getItem(index) to ensure
        // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
        val item = fragments[index]
        val current = (if (isStart) item.startDate else item.endDate) ?: ZonedDateTime.now()

        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStart) "Select Start Date" else "Select End Date")
            .setSelection(current.toInstant().toEpochMilli())

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val newDate = Instant.ofEpochMilli(selection).atZone(ZoneId.systemDefault()).toLocalDate()
            // Read from backing collection (fragments[index]) rather than getItem(index) to ensure
            // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
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
        }
        picker.show(getChildFragmentManager(), DATE_PICKER_TAG)
    }

    private fun pickFragmentTime(index: Int, isStart: Boolean) {
        if (index == RecyclerView.NO_POSITION) return
        // Read from backing collection (fragments[index]) rather than getItem(index) to ensure
        // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
        val item = fragments[index]
        val current = (if (isStart) item.startDate else item.endDate) ?: ZonedDateTime.now()

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(if (isStart) "Select Start Time" else "Select End Time")
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()

        picker.addOnPositiveButtonClickListener {
            val newTime = LocalTime.of(picker.hour, picker.minute)
            // Read from backing collection (fragments[index]) rather than getItem(index) to ensure
            // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
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
        }
        picker.show(getChildFragmentManager(), TIME_PICKER_TAG)
    }

    companion object {
        private const val DATE_RANGE_PICKER_TAG = "DATE_RANGE_PICKER"
        private const val DATE_PICKER_TAG = "DATE_PICKER"
        private const val TIME_PICKER_TAG = "TIME_PICKER"
        private const val FACING_RIGHT_ROTATION = 0f
        private const val FACING_DOWN_ROTATION = 90f
        private const val COLOR_CHANGE_ANIMATION_DURATION = 300L
    }
}
