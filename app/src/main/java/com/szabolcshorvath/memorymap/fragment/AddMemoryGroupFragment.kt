package com.szabolcshorvath.memorymap.fragment

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.szabolcshorvath.memorymap.adapter.ColorPresetAdapter
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter
import com.szabolcshorvath.memorymap.adapter.SelectedMediaAdapter
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.databinding.FragmentAddMemoryGroupBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.timeFormatter
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt

class AddMemoryGroupFragment : Fragment() {

    private var _binding: FragmentAddMemoryGroupBinding? = null
    private val binding get() = _binding!!
    private val commonViewModel: CommonViewModel by activityViewModels()
    private val viewModel: AddMemoryGroupFragmentViewModel by viewModels()

    data class SelectedMedia(
        val uri: Uri,
        val type: MediaType,
        val deviceId: String
    ) {
        class SelectedMediaDiffCallback : DiffUtil.ItemCallback<SelectedMedia>() {
            override fun areItemsTheSame(oldItem: SelectedMedia, newItem: SelectedMedia) = oldItem.uri == newItem.uri

            override fun areContentsTheSame(oldItem: SelectedMedia, newItem: SelectedMedia) = oldItem == newItem
        }
    }

    private var addMemoryGroupListener: AddMemoryGroupListener? = null
    private lateinit var backupManager: BackupManager
    private lateinit var mediaAdapter: SelectedMediaAdapter
    private lateinit var fragmentsAdapter: MemoryFragmentEditAdapter
    private lateinit var colorPresetAdapter: ColorPresetAdapter

    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            uris.let {
                val contentResolver = requireContext().contentResolver
                viewLifecycleOwner.lifecycleScope.launch {
                    val deviceId = viewModel.currentDeviceId ?: InstallationIdentifier.getInstallationIdentifier(requireContext())
                    val newItems = it.mapNotNull { uri ->
                        if (viewModel.selectedMedia.any { m -> m.uri == uri }) {
                            null
                        } else {
                            val type = contentResolver.getType(uri)
                            val mediaType = if (type != null && type.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
                            SelectedMedia(uri, mediaType, deviceId)
                        }
                    }
                    // New media items should be first in the list
                    viewModel.selectedMedia.addAll(0, newItems)
                    updateMediaUI()
                }
                it.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Error taking persistable permission for $uri", e)
                    }
                }
            }
        }

    interface AddMemoryGroupListener {
        fun onPickLocation(lat: Double?, lng: Double?, placeName: String?, address: String?)
        fun onMemorySaved(id: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is AddMemoryGroupListener) {
            addMemoryGroupListener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddMemoryGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backupManager = BackupManager(requireContext())

        setupRecyclerViews()

        binding.titleInput.doAfterTextChanged { viewModel.title = it?.toString()?.ifBlank { null } }
        binding.descriptionInput.doAfterTextChanged { viewModel.description = it?.toString()?.ifBlank { null } }

        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.currentDeviceId == null) {
                viewModel.currentDeviceId = InstallationIdentifier.getInstallationIdentifier(requireContext())
            }
            mediaAdapter.updateCurrentDeviceId(viewModel.currentDeviceId)

            // Ensure UI matches ViewModel state (both on first load and after rotation)
            updateTitleAndDescription()
            updateLocationText()
            updateDateTimeButtons()
            updateColorUI()
            updateMediaUI()
            updateFragmentsUI()

            if (viewModel.editingMemoryId != null) {
                binding.saveButton.text = "Update Memory"
            }

            viewModel.isInitialized = true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                commonViewModel.allPresets.collect { presets ->
                    colorPresetAdapter.submitList(presets)
                    fragmentsAdapter.setHSVPresets(presets)
                }
            }
        }

        binding.selectLocationButton.setOnClickListener {
            viewModel.activePickingIndex = -1
            addMemoryGroupListener?.onPickLocation(viewModel.lat, viewModel.lng, viewModel.placeName, viewModel.address)
        }

        binding.dateHeader.setOnClickListener { toggleDateSection() }

        binding.allDayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isAllDay = isChecked
            updateDateTimeButtons()
        }

        binding.startDateButton.setOnClickListener { pickDate(true) }
        binding.startTimeButton.setOnClickListener { pickTime(true) }
        binding.endDateButton.setOnClickListener { pickDate(false) }
        binding.endTimeButton.setOnClickListener { pickTime(false) }
        binding.dateRangeButton.setOnClickListener { pickDateRange() }

        binding.colorHeader.setOnClickListener { toggleColor() }

        binding.hueSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.markerHue = value
                updateColorUI()
            }
        }

        binding.saturationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.markerSaturation = value
                updateColorUI()
            }
        }

        binding.brightnessSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.markerBrightness = value
                updateColorUI()
            }
        }

        binding.fragmentsHeader.setOnClickListener { toggleFragments() }
        binding.addFragmentButtonInline.setOnClickListener { addFragment() }

        binding.pickMediaButton.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }

        binding.clearButton.setOnClickListener { showClearConfirmationDialog() }

        binding.saveButton.setOnClickListener {
            try {
                require(viewModel.title?.isNotBlank() ?: false) { "The title must not be blank!" }
                require(viewModel.lat != null && viewModel.lng != null) { "The location must be specified!" }
                require(viewModel.fragments.all { it.lat != null && it.lng != null }) { "The location must be specified for all fragments!" }
            } catch (e: IllegalArgumentException) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveMemoryGroup(commonViewModel.getDb(), backupManager)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSaving.collect { isSaving ->
                        binding.saveButton.isEnabled = !isSaving
                    }
                }
                launch {
                    viewModel.saveResult.collect { result ->
                        when (result) {
                            is AddMemoryGroupFragmentViewModel.SaveResult.Success -> {
                                addMemoryGroupListener?.onMemorySaved(result.groupId)
                                clearFields()
                            }

                            is AddMemoryGroupFragmentViewModel.SaveResult.Error -> {
                                Toast.makeText(requireContext(), "Failed to save: ${result.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        mediaAdapter = SelectedMediaAdapter(viewModel.currentDeviceId) { position ->
            viewModel.selectedMedia.removeAt(position)
            updateMediaUI()
        }
        mediaAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.selectedMediaRecyclerView.scrollToPosition(positionStart)
            }
        })
        binding.selectedMediaRecyclerView.adapter = mediaAdapter

        fragmentsAdapter = MemoryFragmentEditAdapter(
            viewModel.fragments,
            { addMemoryGroupListener },
            { childFragmentManager },
            { viewModel.activePickingIndex = it },
            this::updateFragmentsUI
        )
        binding.fragmentsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.fragmentsRecyclerView.adapter = fragmentsAdapter

        colorPresetAdapter = ColorPresetAdapter { preset ->
            viewModel.markerHue = preset.hue
            viewModel.markerSaturation = preset.saturation
            viewModel.markerBrightness = preset.brightness
            updateColorUI(animate = true)
        }
        binding.presetColorsRecyclerView.adapter = colorPresetAdapter
    }

    private fun updateMediaUI() {
        // We pass a new list instance (toList()) to ensure it detects the change.
        mediaAdapter.submitList(viewModel.selectedMedia.toList())
        binding.selectedMediaCount.text = "${viewModel.selectedMedia.size} items selected"
    }

    private fun toggleDateSection() {
        viewModel.dateExpanded = !viewModel.dateExpanded
        updateDateTimeButtons(animateExpansion = true)
    }

    private fun toggleFragments() {
        viewModel.fragmentsExpanded = !viewModel.fragmentsExpanded
        binding.fragmentsExpandedContent.visibility = if (viewModel.fragmentsExpanded) View.VISIBLE else View.GONE
        binding.fragmentsChevron.animate()
            .rotation(if (viewModel.fragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION)
            .start()
    }

    private fun toggleColor() {
        viewModel.colorExpanded = !viewModel.colorExpanded
        updateColorUI()
    }

    private fun updateColorUI(animate: Boolean = false) {
        val binding = _binding ?: return
        binding.colorExpandedContent.visibility = if (viewModel.colorExpanded) View.VISIBLE else View.GONE
        binding.colorChevron.animate()
            .rotation(if (viewModel.colorExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION)
            .start()

        val color = ColorUtil.hsvToColor(viewModel.markerHue, viewModel.markerSaturation, viewModel.markerBrightness)
        val colorStateList = ColorStateList.valueOf(color)

        if (animate) {
            animateSlidersToTargets()
        } else {
            binding.hueSlider.value = viewModel.markerHue
            binding.saturationSlider.value = viewModel.markerSaturation
            binding.brightnessSlider.value = viewModel.markerBrightness

            binding.hueSlider.thumbTintList = colorStateList
            binding.saturationSlider.thumbTintList = colorStateList
            binding.brightnessSlider.thumbTintList = colorStateList

            binding.colorIndicator.setBackgroundColor(color)
            updateValueTexts(viewModel.markerHue, viewModel.markerSaturation, viewModel.markerBrightness)
        }
    }

    private fun updateValueTexts(h: Float, s: Float, v: Float) {
        val binding = _binding ?: return
        binding.tvHueValue.text = h.toInt().toString()
        binding.tvSaturationValue.text = String.format(Locale.getDefault(), "%.2f", s)
        binding.tvBrightnessValue.text = String.format(Locale.getDefault(), "%.2f", v)
    }

    private fun animateSlidersToTargets() {
        val binding = _binding ?: return
        val startH = binding.hueSlider.value
        val startS = binding.saturationSlider.value
        val startV = binding.brightnessSlider.value

        // Use a single animator from 0 to 1 (representing 0% to 100% of the transition)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOR_CHANGE_ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val currentBinding = _binding ?: return@addUpdateListener

                // Calculate current values based on the animation progress
                val currentH = lerpWithStep(startH, viewModel.markerHue, fraction, currentBinding.hueSlider.stepSize)
                val currentS = lerpWithStep(startS, viewModel.markerSaturation, fraction, currentBinding.saturationSlider.stepSize)
                val currentV = lerpWithStep(startV, viewModel.markerBrightness, fraction, currentBinding.brightnessSlider.stepSize)

                currentBinding.hueSlider.value = currentH
                currentBinding.saturationSlider.value = currentS
                currentBinding.brightnessSlider.value = currentV

                val currentColor = ColorUtil.hsvToColor(currentH, currentS, currentV)
                currentBinding.colorIndicator.setBackgroundColor(currentColor)

                val currentStateList = ColorStateList.valueOf(currentColor)
                currentBinding.hueSlider.thumbTintList = currentStateList
                currentBinding.saturationSlider.thumbTintList = currentStateList
                currentBinding.brightnessSlider.thumbTintList = currentStateList

                updateValueTexts(currentH, currentS, currentV)
            }
            start()
        }
    }

    /**
     * Helper to calculate Linear Interpolation while respecting step size
     */
    private fun lerpWithStep(start: Float, end: Float, fraction: Float, stepSize: Float): Float {
        val rawValue = start + (end - start) * fraction
        return if (stepSize > 0) {
            ((rawValue / stepSize).roundToInt() * stepSize)
        } else {
            rawValue
        }
    }

    private fun addFragment() {
        viewModel.fragments.add(
            MemoryFragmentEditAdapter.FragmentEditState(
                lat = viewModel.lat,
                lng = viewModel.lng,
                placeName = viewModel.placeName,
                address = viewModel.address,
                markerHue = viewModel.markerHue,
                markerSaturation = viewModel.markerSaturation,
                markerBrightness = viewModel.markerBrightness,
                isDateExpanded = false
            )
        )
        if (!viewModel.fragmentsExpanded) toggleFragments()
        updateFragmentsUI(scrollToEnd = true)
    }

    private fun updateFragmentsUI(scrollToEnd: Boolean = false) {
        val binding = _binding ?: return
        binding.fragmentsExpandedContent.visibility = if (viewModel.fragmentsExpanded) View.VISIBLE else View.GONE
        binding.fragmentsChevron.rotation = if (viewModel.fragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION
        // We pass a new list instance (toList()) to ensure it detects the change.
        fragmentsAdapter.submitList(viewModel.fragments.toList()) {
            if (scrollToEnd) {
                binding.nestedScrollView.post {
                    // Scroll the NestedScrollView to the bottom of the fragments section
                    binding.nestedScrollView.smoothScrollTo(0, binding.fragmentsSection.bottom)
                }
            }
        }
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(if (viewModel.editingMemoryId != null) "Discard Changes" else "Clear Fields")
            .setMessage(
                if (viewModel.editingMemoryId != null) {
                    "Are you sure you want to discard your changes?"
                } else {
                    "Are you sure you want to clear all fields? This action cannot be undone."
                }
            )
            .setPositiveButton(if (viewModel.editingMemoryId != null) "Discard" else "Clear") { _, _ -> clearFields() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun clearFields() {
        viewModel.editingMemoryId = null
        viewModel.title = null
        viewModel.description = null
        viewModel.lat = null
        viewModel.lng = null
        viewModel.placeName = null
        viewModel.address = null
        viewModel.isAllDay = false
        viewModel.startDateTime = ZonedDateTime.now()
        viewModel.endDateTime = ZonedDateTime.now().plusHours(1)
        viewModel.markerHue = DEFAULT_MARKER_HUE
        viewModel.markerSaturation = DEFAULT_MARKER_SATURATION
        viewModel.markerBrightness = DEFAULT_MARKER_BRIGHTNESS
        viewModel.dateExpanded = true
        updateDateTimeButtons()
        viewModel.selectedMedia.clear()
        updateMediaUI()
        viewModel.fragments.clear()
        viewModel.fragmentsExpanded = true
        updateFragmentsUI()
        viewModel.colorExpanded = false
        updateColorUI()

        binding.titleInput.text?.clear()
        binding.descriptionInput.text?.clear()
        updateLocationText()
        binding.allDayCheckbox.isChecked = false
        binding.saveButton.text = "Save Memory"
    }

    fun updateLocation(newLat: Double, newLng: Double, newPlaceName: String? = null, newAddress: String? = null) {
        if (viewModel.activePickingIndex == -1) {
            viewModel.lat = newLat
            viewModel.lng = newLng
            viewModel.placeName = newPlaceName
            viewModel.address = newAddress
            if (_binding != null) updateLocationText()
        } else if (viewModel.activePickingIndex in viewModel.fragments.indices) {
            val fragment = viewModel.fragments[viewModel.activePickingIndex]
            viewModel.fragments[viewModel.activePickingIndex] = fragment.copy(
                lat = newLat,
                lng = newLng,
                placeName = newPlaceName,
                address = newAddress
            )
            updateFragmentsUI()
        }
    }

    fun setEditMode(memoryId: Int) {
        viewModel.editingMemoryId = memoryId
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val groupWithMedia = commonViewModel.getMemoryGroupDao().getGroupWithMedia(memoryId)
            withContext(Dispatchers.Main) {
                val binding = _binding ?: return@withContext
                groupWithMedia?.let { data ->
                    val group = data.group
                    viewModel.lat = group.latitude
                    viewModel.lng = group.longitude
                    viewModel.placeName = group.placeName
                    viewModel.address = group.address
                    viewModel.title = group.title
                    viewModel.description = group.description
                    viewModel.isAllDay = group.isAllDay
                    viewModel.startDateTime = group.startDate
                    viewModel.endDateTime = group.endDate
                    viewModel.markerHue = group.markerHue ?: DEFAULT_MARKER_HUE
                    viewModel.markerSaturation = group.markerSaturation ?: DEFAULT_MARKER_SATURATION
                    viewModel.markerBrightness = group.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS

                    binding.titleInput.setText(viewModel.title)
                    binding.descriptionInput.setText(viewModel.description)
                    binding.allDayCheckbox.isChecked = viewModel.isAllDay

                    val sortedItems = data.mediaItems.sortedWith(MediaItemComparator())
                    viewModel.selectedMedia.clear()
                    viewModel.selectedMedia.addAll(
                        sortedItems.map {
                            SelectedMedia(it.uri.toUri(), it.type, it.deviceId)
                        }
                    )
                    updateMediaUI()

                    val sortedFragments = data.fragments.sortedWith(MemoryFragmentComparator())
                    viewModel.fragments.clear()
                    viewModel.fragments.addAll(
                        sortedFragments.map {
                            MemoryFragmentEditAdapter.FragmentEditState(
                                id = it.id,
                                lat = it.latitude,
                                lng = it.longitude,
                                placeName = it.placeName,
                                address = it.address,
                                startDate = it.startDate,
                                endDate = it.endDate,
                                isAllDay = it.isAllDay,
                                markerHue = it.markerHue ?: DEFAULT_MARKER_HUE,
                                markerSaturation = it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                                markerBrightness = it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS,
                                isTimeVisible = it.startDate != null,
                                isDateExpanded = false,
                                isColorExpanded = false,
                                order = it.order
                            )
                        }
                    )
                    viewModel.fragmentsExpanded = true
                    updateFragmentsUI()

                    updateLocationText()
                    viewModel.dateExpanded = true
                    updateDateTimeButtons()
                    updateColorUI()
                    binding.saveButton.text = "Update Memory"
                }
            }
        }
    }

    private class MediaItemComparator : Comparator<MediaItem> {
        override fun compare(a: MediaItem, b: MediaItem): Int {
            // Sort media items based on order or dateTaken
            return when {
                a.order != null && b.order != null -> a.order.compareTo(b.order)
                a.order != null -> -1
                b.order != null -> 1
                else -> b.dateTaken.compareTo(a.dateTaken)
            }
        }
    }

    private class MemoryFragmentComparator : Comparator<MemoryFragment> {
        override fun compare(a: MemoryFragment, b: MemoryFragment): Int {
            // Sort fragments based on order or startDate
            return when {
                a.order != null && b.order != null -> a.order.compareTo(b.order)
                a.order != null -> -1
                b.order != null -> 1
                else -> {
                    val dateA = a.startDate
                    val dateB = b.startDate
                    when {
                        dateA != null && dateB != null -> dateA.compareTo(dateB)
                        dateA != null -> -1
                        dateB != null -> 1
                        else -> 0
                    }
                }
            }
        }
    }

    private fun updateTitleAndDescription() {
        binding.titleInput.setText(viewModel.title)
        binding.descriptionInput.setText(viewModel.description)
    }

    private fun updateLocationText() {
        val lat = viewModel.lat
        val lng = viewModel.lng
        val locationString = StringBuilder()
        if (viewModel.placeName != null) locationString.append(viewModel.placeName).append(System.lineSeparator())
        if (viewModel.address != null) locationString.append(viewModel.address).append(System.lineSeparator())
        if (locationString.isEmpty()) {
            if (lat != null && lng != null) {
                locationString.append("Coordinates: $lat, $lng")
            } else {
                locationString.append("No location selected")
            }
        }
        binding.locationText.text = locationString.toString()
    }

    private fun updateDateTimeButtons(animateExpansion: Boolean = false) {
        val binding = _binding ?: return
        binding.dateExpandedContent.visibility = if (viewModel.dateExpanded) View.VISIBLE else View.GONE
        if (animateExpansion) {
            binding.dateChevron.animate()
                .rotation(if (viewModel.dateExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION)
                .start()
        } else {
            binding.dateChevron.rotation = if (viewModel.dateExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION
        }

        if (viewModel.isAllDay) {
            binding.startDateTimeLayout.visibility = View.GONE
            binding.endDateTimeLayout.visibility = View.GONE
            binding.startDateLabel.visibility = View.GONE
            binding.endDateLabel.visibility = View.GONE
            binding.dateRangeButton.visibility = View.VISIBLE

            val startStr = viewModel.startDateTime.format(dateFormatter())
            val endStr = viewModel.endDateTime.format(dateFormatter())
            val dateRangeStr = if (startStr == endStr) startStr else "$startStr - $endStr"
            binding.dateRangeButton.text = dateRangeStr
            binding.dateSummaryText.text = dateRangeStr
        } else {
            binding.startDateTimeLayout.visibility = View.VISIBLE
            binding.endDateTimeLayout.visibility = View.VISIBLE
            binding.startDateLabel.visibility = View.VISIBLE
            binding.endDateLabel.visibility = View.VISIBLE
            binding.dateRangeButton.visibility = View.GONE

            val startDStr = viewModel.startDateTime.format(dateFormatter())
            val startTStr = viewModel.startDateTime.format(timeFormatter())
            val endDStr = viewModel.endDateTime.format(dateFormatter())
            val endTStr = viewModel.endDateTime.format(timeFormatter())

            binding.startDateButton.text = startDStr
            binding.endDateButton.text = endDStr
            binding.startTimeButton.text = startTStr
            binding.endTimeButton.text = endTStr

            val startFull = "$startDStr, $startTStr"
            val endFull = "$endDStr, $endTStr"
            binding.dateSummaryText.text = if (startDStr == endDStr) {
                "$startDStr, $startTStr - $endTStr"
            } else {
                "$startFull - $endFull"
            }
        }
    }

    private fun pickDateRange() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
        val selection = androidx.core.util.Pair(
            viewModel.startDateTime.toInstant().toEpochMilli(),
            viewModel.endDateTime.toInstant().toEpochMilli()
        )
        builder.setSelection(selection)

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { range ->
            if (range.first != null && range.second != null) {
                viewModel.startDateTime = Instant.ofEpochMilli(range.first!!).atZone(ZoneId.systemDefault())
                viewModel.endDateTime = Instant.ofEpochMilli(range.second!!).atZone(ZoneId.systemDefault())
                updateDateTimeButtons()
            }
        }
        picker.show(childFragmentManager, DATE_RANGE_PICKER_TAG)
    }

    private fun pickDate(isStart: Boolean) {
        val current = if (isStart) viewModel.startDateTime else viewModel.endDateTime
        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStart) "Select Start Date" else "Select End Date")
            .setSelection(current.toInstant().toEpochMilli())

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val newDate = Instant.ofEpochMilli(selection).atZone(ZoneId.systemDefault()).toLocalDate()
            if (isStart) {
                viewModel.startDateTime = newDate.atTime(viewModel.startDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
                if (viewModel.endDateTime.isBefore(viewModel.startDateTime)) viewModel.endDateTime = viewModel.startDateTime.plusHours(1)
            } else {
                viewModel.endDateTime = newDate.atTime(viewModel.endDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
                if (viewModel.endDateTime.isBefore(viewModel.startDateTime)) viewModel.startDateTime = viewModel.endDateTime.minusHours(1)
            }
            updateDateTimeButtons()
        }
        picker.show(childFragmentManager, DATE_PICKER_TAG)
    }

    private fun pickTime(isStart: Boolean) {
        val current = if (isStart) viewModel.startDateTime else viewModel.endDateTime
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(if (isStart) "Select Start Time" else "Select End Time")
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()

        picker.addOnPositiveButtonClickListener {
            val newTime = LocalTime.of(picker.hour, picker.minute)
            if (isStart) {
                viewModel.startDateTime = viewModel.startDateTime.with(newTime)
                if (viewModel.endDateTime.isBefore(viewModel.startDateTime)) viewModel.endDateTime = viewModel.startDateTime.plusHours(1)
            } else {
                viewModel.endDateTime = viewModel.endDateTime.with(newTime)
                if (viewModel.endDateTime.isBefore(viewModel.startDateTime)) viewModel.startDateTime = viewModel.endDateTime.minusHours(1)
            }
            updateDateTimeButtons()
        }
        picker.show(childFragmentManager, TIME_PICKER_TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddMemoryGroupFragment"
        private const val DATE_RANGE_PICKER_TAG = "DATE_RANGE_PICKER"
        private const val DATE_PICKER_TAG = "DATE_PICKER"
        private const val TIME_PICKER_TAG = "TIME_PICKER"
        private const val FACING_RIGHT_ROTATION = 0f
        private const val FACING_DOWN_ROTATION = 90f
        private const val COLOR_CHANGE_ANIMATION_DURATION = 300L
    }
}
