package com.szabolcshorvath.memorymap.fragment

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.provider.MediaStore
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.withTransaction
import com.google.android.material.datepicker.MaterialDatePicker
import com.szabolcshorvath.memorymap.adapter.ColorPresetAdapter
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter
import com.szabolcshorvath.memorymap.adapter.SelectedMediaAdapter
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentAddMemoryGroupBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.timeFormatter
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.MediaHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.roundToInt

class AddMemoryGroupFragment : Fragment() {

    private var _binding: FragmentAddMemoryGroupBinding? = null
    private val binding get() = _binding!!

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
            override fun areItemsTheSame(
                oldItem: FragmentEditState,
                newItem: FragmentEditState
            ) = oldItem.localId == newItem.localId

            override fun areContentsTheSame(oldItem: FragmentEditState, newItem: FragmentEditState) = oldItem == newItem
        }
    }

    private val selectedMedia = mutableListOf<SelectedMedia>()
    private val fragments = mutableListOf<FragmentEditState>()

    private var lat = 0.0
    private var lng = 0.0
    private var placeName: String? = null
    private var address: String? = null

    private var startDateTime: ZonedDateTime = ZonedDateTime.now()
    private var endDateTime: ZonedDateTime = ZonedDateTime.now().plusHours(1)
    private var isAllDay = false
    private var markerHue = DEFAULT_MARKER_HUE
    private var markerSaturation = DEFAULT_MARKER_SATURATION
    private var markerBrightness = DEFAULT_MARKER_BRIGHTNESS

    private var listener: AddMemoryListener? = null
    private lateinit var backupManager: BackupManager
    private var editingMemoryId: Int? = null
    private lateinit var mediaAdapter: SelectedMediaAdapter
    private lateinit var fragmentsAdapter: MemoryFragmentEditAdapter
    private lateinit var colorPresetAdapter: ColorPresetAdapter
    private var currentDeviceId: String? = null
    private var activePickingIndex: Int = -1 // -1 for main, 0+ for fragments
    private var fragmentsExpanded = true
    private var colorExpanded = false

    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            uris.let {
                val contentResolver = requireContext().contentResolver
                lifecycleScope.launch {
                    val deviceId = currentDeviceId ?: InstallationIdentifier.getInstallationIdentifier(requireContext())
                    val newItems = it.mapNotNull { uri ->
                        if (selectedMedia.any { m -> m.uri == uri }) {
                            null
                        } else {
                            val type = contentResolver.getType(uri)
                            val mediaType = if (type != null && type.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
                            SelectedMedia(uri, mediaType, deviceId)
                        }
                    }
                    // New media items should be first in the list
                    selectedMedia.addAll(0, newItems)
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

    interface AddMemoryListener {
        fun onPickLocation(lat: Double, lng: Double)
        fun onMemorySaved(
            lat: Double,
            lng: Double,
            id: Int,
            startDate: LocalDate,
            endDate: LocalDate
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is AddMemoryListener) {
            listener = context
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

        lifecycleScope.launch {
            if (currentDeviceId == null) {
                currentDeviceId = InstallationIdentifier.getInstallationIdentifier(requireContext())
            }
            mediaAdapter.updateCurrentDeviceId(currentDeviceId)
            updateLocationText()
            updateDateTimeButtons()
            updateFragmentsUI()
            updateColorUI()
        }

        observeHSVPresets()

        binding.selectLocationButton.setOnClickListener {
            activePickingIndex = -1
            listener?.onPickLocation(lat, lng)
        }

        binding.allDayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            isAllDay = isChecked
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
                markerHue = value
                updateColorUI()
            }
        }

        binding.saturationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                markerSaturation = value
                updateColorUI()
            }
        }

        binding.brightnessSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                markerBrightness = value
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
            lifecycleScope.launch {
                saveMemoryGroup()
            }
        }
    }

    private fun setupRecyclerViews() {
        mediaAdapter = SelectedMediaAdapter(currentDeviceId) { position ->
            selectedMedia.removeAt(position)
            updateMediaUI()
        }
        mediaAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.selectedMediaRecyclerView.scrollToPosition(positionStart)
            }
        })
        binding.selectedMediaRecyclerView.adapter = mediaAdapter

        fragmentsAdapter = MemoryFragmentEditAdapter(
            fragments,
            { listener },
            { childFragmentManager },
            { activePickingIndex = it },
            this::updateFragmentsUI
        )
        binding.fragmentsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.fragmentsRecyclerView.adapter = fragmentsAdapter

        colorPresetAdapter = ColorPresetAdapter { preset ->
            markerHue = preset.hue
            markerSaturation = preset.saturation
            markerBrightness = preset.brightness
            updateColorUI(animate = true)
        }
        binding.presetColorsRecyclerView.adapter = colorPresetAdapter
    }

    private fun updateMediaUI() {
        // We pass a new list instance (toList()) to ensure it detects the change.
        mediaAdapter.submitList(selectedMedia.toList())
        binding.selectedMediaCount.text = "${selectedMedia.size} items selected"
    }

    private fun toggleFragments() {
        fragmentsExpanded = !fragmentsExpanded
        binding.fragmentsExpandedContent.visibility = if (fragmentsExpanded) View.VISIBLE else View.GONE
        binding.fragmentsChevron.animate()
            .rotation(if (fragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION)
            .start()
    }

    private fun toggleColor() {
        colorExpanded = !colorExpanded
        updateColorUI()
    }

    private fun updateColorUI(animate: Boolean = false) {
        binding.colorExpandedContent.visibility = if (colorExpanded) View.VISIBLE else View.GONE
        binding.colorChevron.animate()
            .rotation(if (colorExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION)
            .start()

        val color = ColorUtil.hsvToColor(markerHue, markerSaturation, markerBrightness)
        val colorStateList = ColorStateList.valueOf(color)

        if (animate) {
            animateSlidersToTargets()
        } else {
            binding.hueSlider.value = markerHue
            binding.saturationSlider.value = markerSaturation
            binding.brightnessSlider.value = markerBrightness

            binding.hueSlider.thumbTintList = colorStateList
            binding.saturationSlider.thumbTintList = colorStateList
            binding.brightnessSlider.thumbTintList = colorStateList

            binding.colorIndicator.setBackgroundColor(color)
        }
    }

    private fun animateSlidersToTargets() {
        val startH = binding.hueSlider.value
        val startS = binding.saturationSlider.value
        val startV = binding.brightnessSlider.value

        // Use a single animator from 0 to 1 (representing 0% to 100% of the transition)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOR_CHANGE_ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val fraction = animator.animatedFraction

                // Calculate current values based on the animation progress
                val currentH = lerpWithStep(startH, markerHue, fraction, binding.hueSlider.stepSize)
                val currentS = lerpWithStep(startS, markerSaturation, fraction, binding.saturationSlider.stepSize)
                val currentV = lerpWithStep(startV, markerBrightness, fraction, binding.brightnessSlider.stepSize)

                binding.hueSlider.value = currentH
                binding.saturationSlider.value = currentS
                binding.brightnessSlider.value = currentV

                val currentColor = ColorUtil.hsvToColor(currentH, currentS, currentV)
                binding.colorIndicator.setBackgroundColor(currentColor)

                val currentStateList = ColorStateList.valueOf(currentColor)
                binding.hueSlider.thumbTintList = currentStateList
                binding.saturationSlider.thumbTintList = currentStateList
                binding.brightnessSlider.thumbTintList = currentStateList
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
        fragments.add(
            FragmentEditState(
                latitude = lat,
                longitude = lng,
                placeName = placeName,
                address = address,
                markerHue = markerHue,
                markerSaturation = markerSaturation,
                markerBrightness = markerBrightness
            )
        )
        if (!fragmentsExpanded) toggleFragments()
        updateFragmentsUI(scrollToEnd = true)
    }

    private fun updateFragmentsUI(scrollToEnd: Boolean = false) {
        binding.fragmentsExpandedContent.visibility = if (fragmentsExpanded) View.VISIBLE else View.GONE
        binding.fragmentsChevron.rotation = if (fragmentsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION
        // We pass a new list instance (toList()) to ensure it detects the change.
        fragmentsAdapter.submitList(fragments.toList()) {
            if (scrollToEnd) {
                binding.root.post {
                    // Scroll the NestedScrollView to the bottom of the fragments section
                    binding.root.smoothScrollTo(0, binding.fragmentsSection.bottom)
                }
            }
        }
    }

    private fun observeHSVPresets() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val db = MemoryMapDatabase.getDatabase(requireContext().applicationContext)
                db.hsvPresetDao().getAllPresetsFlow().collect { presets ->
                    colorPresetAdapter.submitList(presets)
                    fragmentsAdapter.setHSVPresets(presets)
                }
            }
        }
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(if (editingMemoryId != null) "Discard Changes" else "Clear Fields")
            .setMessage(
                if (editingMemoryId != null) {
                    "Are you sure you want to discard your changes?"
                } else {
                    "Are you sure you want to clear all fields? This action cannot be undone."
                }
            )
            .setPositiveButton(if (editingMemoryId != null) "Discard" else "Clear") { _, _ -> clearFields() }
            .setNegativeButton("Cancel", null).show()
    }

    fun clearFields() {
        editingMemoryId = null
        lat = 0.0
        lng = 0.0
        placeName = null
        address = null
        isAllDay = false
        startDateTime = ZonedDateTime.now()
        endDateTime = ZonedDateTime.now().plusHours(1)
        markerHue = DEFAULT_MARKER_HUE
        markerSaturation = DEFAULT_MARKER_SATURATION
        markerBrightness = DEFAULT_MARKER_BRIGHTNESS
        updateDateTimeButtons()
        selectedMedia.clear()
        updateMediaUI()
        fragments.clear()
        fragmentsExpanded = true
        updateFragmentsUI()
        colorExpanded = false
        updateColorUI()

        binding.titleInput.text?.clear()
        binding.descriptionInput.text?.clear()
        updateLocationText()
        binding.allDayCheckbox.isChecked = false
        binding.saveButton.text = "Save Memory"
    }

    fun updateLocation(newLat: Double, newLng: Double, newPlaceName: String? = null, newAddress: String? = null) {
        if (activePickingIndex == -1) {
            lat = newLat
            lng = newLng
            placeName = newPlaceName
            address = newAddress
            if (_binding != null) updateLocationText()
        } else if (activePickingIndex in fragments.indices) {
            val fragment = fragments[activePickingIndex]
            fragments[activePickingIndex] = fragment.copy(
                latitude = newLat,
                longitude = newLng,
                placeName = newPlaceName,
                address = newAddress
            )
            updateFragmentsUI()
        }
    }

    fun setEditMode(memoryId: Int) {
        editingMemoryId = memoryId
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MemoryMapDatabase.getDatabase(requireContext().applicationContext)
            val groupWithMedia = db.memoryGroupDao().getGroupWithMedia(memoryId)
            withContext(Dispatchers.Main) {
                groupWithMedia?.let { data ->
                    val group = data.group
                    lat = group.latitude
                    lng = group.longitude
                    placeName = group.placeName
                    address = group.address
                    isAllDay = group.isAllDay
                    startDateTime = group.startDate
                    endDateTime = group.endDate
                    markerHue = group.markerHue ?: DEFAULT_MARKER_HUE
                    markerSaturation = group.markerSaturation ?: DEFAULT_MARKER_SATURATION
                    markerBrightness = group.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS

                    binding.titleInput.setText(group.title)
                    binding.descriptionInput.setText(group.description)
                    binding.allDayCheckbox.isChecked = isAllDay

                    val sortedItems = data.mediaItems.sortedWith(MediaItemComparator())
                    selectedMedia.clear()
                    selectedMedia.addAll(
                        sortedItems.map {
                            SelectedMedia(it.uri.toUri(), it.type, it.deviceId)
                        }
                    )
                    updateMediaUI()

                    val sortedFragments = data.fragments.sortedWith(MemoryFragmentComparator())
                    fragments.clear()
                    fragments.addAll(
                        sortedFragments.map {
                            FragmentEditState(
                                id = it.id,
                                latitude = it.latitude,
                                longitude = it.longitude,
                                placeName = it.placeName,
                                address = it.address,
                                startDate = it.startDate,
                                endDate = it.endDate,
                                isAllDay = it.isAllDay,
                                markerHue = it.markerHue ?: DEFAULT_MARKER_HUE,
                                markerSaturation = it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                                markerBrightness = it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS,
                                isTimeVisible = it.startDate != null,
                                isColorExpanded = false,
                                order = it.order
                            )
                        }
                    )
                    fragmentsExpanded = true
                    updateFragmentsUI()

                    updateLocationText()
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

    private fun updateLocationText() {
        val locationString = StringBuilder()
        if (placeName != null) locationString.append(placeName).append(System.lineSeparator())
        if (address != null) locationString.append(address).append(System.lineSeparator())
        if (locationString.isEmpty()) locationString.append("Coordinates: $lat, $lng")
        binding.locationText.text = locationString.toString()
    }

    private fun updateDateTimeButtons() {
        if (isAllDay) {
            binding.startDateTimeLayout.visibility = View.GONE
            binding.endDateTimeLayout.visibility = View.GONE
            binding.startDateLabel.visibility = View.GONE
            binding.endDateLabel.visibility = View.GONE
            binding.dateRangeButton.visibility = View.VISIBLE

            val startStr = startDateTime.format(dateFormatter())
            val endStr = endDateTime.format(dateFormatter())
            binding.dateRangeButton.text = if (startStr == endStr) startStr else "$startStr - $endStr"
        } else {
            binding.startDateTimeLayout.visibility = View.VISIBLE
            binding.endDateTimeLayout.visibility = View.VISIBLE
            binding.startDateLabel.visibility = View.VISIBLE
            binding.endDateLabel.visibility = View.VISIBLE
            binding.dateRangeButton.visibility = View.GONE

            binding.startDateButton.text = startDateTime.format(dateFormatter())
            binding.endDateButton.text = endDateTime.format(dateFormatter())
            binding.startTimeButton.text = startDateTime.format(timeFormatter())
            binding.endTimeButton.text = endDateTime.format(timeFormatter())
        }
    }

    private fun pickDateRange() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
        val selection = androidx.core.util.Pair(
            startDateTime.toInstant().toEpochMilli(),
            endDateTime.toInstant().toEpochMilli()
        )
        builder.setSelection(selection)

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { range ->
            if (range.first != null && range.second != null) {
                startDateTime = Instant.ofEpochMilli(range.first!!).atZone(ZoneId.systemDefault())
                endDateTime = Instant.ofEpochMilli(range.second!!).atZone(ZoneId.systemDefault())
                updateDateTimeButtons()
            }
        }
        picker.show(childFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun pickDate(isStart: Boolean) {
        val current = if (isStart) startDateTime else endDateTime
        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            val newDate = LocalDate.of(year, month + 1, dayOfMonth)
            if (isStart) {
                startDateTime = newDate.atTime(startDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
                if (endDateTime.isBefore(startDateTime)) endDateTime = startDateTime.plusHours(1)
            } else {
                endDateTime = newDate.atTime(endDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
                if (endDateTime.isBefore(startDateTime)) startDateTime = endDateTime.minusHours(1)
            }
            updateDateTimeButtons()
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    private fun pickTime(isStart: Boolean) {
        val current = if (isStart) startDateTime else endDateTime
        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            val newTime = LocalTime.of(hourOfDay, minute)
            if (isStart) {
                startDateTime = startDateTime.with(newTime)
                if (endDateTime.isBefore(startDateTime)) endDateTime = startDateTime.plusHours(1)
            } else {
                endDateTime = endDateTime.with(newTime)
                if (endDateTime.isBefore(startDateTime)) startDateTime = endDateTime.minusHours(1)
            }
            updateDateTimeButtons()
        }, current.hour, current.minute, true).show()
    }

    private suspend fun saveMemoryGroup() {
        try {
            validateRequiredFields()
        } catch (e: IllegalStateException) {
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            return
        }

        val effectiveStart = calculateEffectiveStartTime()
        val effectiveEnd = calculateEffectiveEndTime()
        val group = assembleMemoryGroup(effectiveStart, effectiveEnd)

        try {
            binding.saveButton.isEnabled = false

            val context = requireContext().applicationContext
            val db = MemoryMapDatabase.getDatabase(context)

            val groupIdResult = withContext(Dispatchers.IO) {
                db.withTransaction {
                    val groupId = saveMemoryGroup(db, group)
                    saveMediaItems(db, groupId, context)
                    saveMemoryFragments(db, groupId)
                    groupId.toInt()
                }
            }

            backupManager.triggerAutomaticBackup()
            listener?.onMemorySaved(lat, lng, groupIdResult, effectiveStart.toLocalDate(), effectiveEnd.toLocalDate())
            clearFields()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving memory group", e)
            Toast.makeText(
                requireContext(),
                "Failed to save: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            _binding?.saveButton?.isEnabled = true
        }
    }

    private fun validateRequiredFields() {
        check(binding.titleInput.text.toString().isNotBlank()) { "Title cannot be empty" }
    }

    private fun calculateEffectiveStartTime(): ZonedDateTime = if (isAllDay) {
        startDateTime.toLocalDate().atStartOfDay(ZoneId.systemDefault())
    } else {
        startDateTime
    }

    private fun calculateEffectiveEndTime(): ZonedDateTime = if (isAllDay) {
        endDateTime.toLocalDate()
            .atTime(LocalTime.MAX)
            .atZone(ZoneId.systemDefault())
    } else {
        endDateTime
    }

    private fun assembleMemoryGroup(effectiveStart: ZonedDateTime, effectiveEnd: ZonedDateTime): MemoryGroup = MemoryGroup(
        id = editingMemoryId ?: 0,
        title = binding.titleInput.text.toString(),
        description = binding.descriptionInput.text.toString().ifBlank { null },
        latitude = lat,
        longitude = lng,
        placeName = placeName,
        address = address,
        startDate = effectiveStart,
        endDate = effectiveEnd,
        isAllDay = isAllDay,
        markerHue = markerHue,
        markerSaturation = markerSaturation,
        markerBrightness = markerBrightness
    )

    private suspend fun saveMemoryGroup(db: MemoryMapDatabase, group: MemoryGroup): Long {
        val groupId = if (editingMemoryId != null) {
            db.memoryGroupDao().updateGroup(group)
            editingMemoryId!!.toLong()
        } else {
            db.memoryGroupDao().insertGroup(group)
        }
        return groupId
    }

    private suspend fun saveMediaItems(db: MemoryMapDatabase, groupId: Long, context: Context) {
        // If editing, we delete the old media associations and re-insert the current selection
        if (editingMemoryId != null) {
            db.memoryGroupDao().deleteMediaByGroupId(groupId.toInt())
        }

        val mediaItems = selectedMedia.mapIndexed { index, (uri, type, itemDeviceId) ->
            var size = 0L
            var date = 0L

            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_TAKEN
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                    date = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
                }
            }

            MediaItem(
                groupId = groupId.toInt(),
                uri = uri.toString(),
                deviceId = itemDeviceId,
                type = type,
                mediaSignature = MediaHasher.calculateMediaSignature(context, uri),
                fileSize = size,
                dateTaken = date,
                order = index + 1
            )
        }
            .distinctBy { it.mediaSignature }
            .mapIndexed { index, item -> item.copy(order = index + 1) }

        db.memoryGroupDao().insertMediaItems(mediaItems)
    }

    private suspend fun saveMemoryFragments(db: MemoryMapDatabase, groupId: Long) {
        db.memoryGroupDao().deleteFragmentsByGroupId(groupId.toInt())
        val fragmentEntities = fragments.mapIndexed { index, f ->
            val saveTime = f.isTimeVisible
            val fragmentStart = if (saveTime) {
                if (f.isAllDay) {
                    f.startDate?.toLocalDate()?.atStartOfDay(ZoneId.systemDefault())
                } else {
                    f.startDate
                }
            } else {
                null
            }
            val fragmentEnd = if (saveTime) {
                if (f.isAllDay) {
                    f.endDate?.toLocalDate()?.atTime(LocalTime.MAX)?.atZone(ZoneId.systemDefault())
                } else {
                    f.endDate
                }
            } else {
                null
            }

            MemoryFragment(
                groupId = groupId.toInt(),
                latitude = f.latitude,
                longitude = f.longitude,
                placeName = f.placeName,
                address = f.address,
                startDate = fragmentStart,
                endDate = fragmentEnd,
                isAllDay = f.isAllDay && saveTime,
                markerHue = f.markerHue,
                markerSaturation = f.markerSaturation,
                markerBrightness = f.markerBrightness,
                order = index + 1
            )
        }
        db.memoryGroupDao().insertFragments(fragmentEntities)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddMemoryGroupFragment"
        private const val FACING_RIGHT_ROTATION = 0f
        private const val FACING_DOWN_ROTATION = 90f
        private const val COLOR_CHANGE_ANIMATION_DURATION = 300L
    }
}
