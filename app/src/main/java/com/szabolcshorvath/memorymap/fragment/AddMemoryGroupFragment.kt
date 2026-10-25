package com.szabolcshorvath.memorymap.fragment

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.ColorPresetAdapter
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter
import com.szabolcshorvath.memorymap.adapter.SelectedMediaAdapter
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.databinding.FragmentAddMemoryGroupBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.timeFormatter
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.findDatePicker
import com.szabolcshorvath.memorymap.util.findTimePicker
import com.szabolcshorvath.memorymap.util.setOnPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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
        val deviceId: String,
        val fileSize: Long = 0L,
        val dateTaken: Long = 0L,
        val mediaSignature: String = ""
    ) {
        class SelectedMediaDiffCallback : DiffUtil.ItemCallback<SelectedMedia>() {
            override fun areItemsTheSame(oldItem: SelectedMedia, newItem: SelectedMedia) = oldItem.uri == newItem.uri

            override fun areContentsTheSame(oldItem: SelectedMedia, newItem: SelectedMedia) = oldItem == newItem
        }
    }

    private var addMemoryGroupListener: AddMemoryGroupListener? = null
    private var discardForEditDialog: AlertDialog? = null
    private lateinit var mediaAdapter: SelectedMediaAdapter
    private lateinit var fragmentsAdapter: MemoryFragmentEditAdapter
    private lateinit var colorPresetAdapter: ColorPresetAdapter

    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            val context = context ?: return@registerForActivityResult
            val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@registerForActivityResult
            val knownDeviceId = viewModel.currentDeviceId
            viewLifecycleOwner.lifecycleScope.launch {
                // One content resolver call or more per picked item: keep them off the main thread.
                val pickedItems = withContext(Dispatchers.IO) {
                    val contentResolver = context.contentResolver
                    uris.forEach { uri ->
                        try {
                            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Could not persist access for $uri", e)
                        }
                    }
                    val deviceId = knownDeviceId ?: InstallationIdentifier.getInstallationIdentifier(context)
                    uris.map { uri -> readPickedMedia(contentResolver, uri, deviceId) }
                }
                val newItems = pickedItems.filter { picked -> viewModel.selectedMedia.none { it.uri == picked.uri } }
                // New media items should be first in the list
                viewModel.selectedMedia.addAll(0, newItems)
                updateMediaUI()
            }
        }

    /** Reads the type, size and date taken of a picked item. Does content resolver calls: not on the main thread. */
    private fun readPickedMedia(contentResolver: ContentResolver, uri: Uri, deviceId: String): SelectedMedia {
        val type = contentResolver.getType(uri)
        val mediaType = if (type != null && type.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
        val projection = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN)
        val (size, date) = try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                (if (sizeCol != -1) cursor.getLong(sizeCol) else 0L) to (if (dateCol != -1) cursor.getLong(dateCol) else 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying picked media info", e)
            null
        } ?: (0L to 0L)
        return SelectedMedia(uri, mediaType, deviceId, size, date)
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
        val viewLifecycleOwner = viewLifecycleOwner
        val context = context ?: return

        setupRecyclerViews()
        reattachPickerListeners()
        fragmentsAdapter.reattachPickerListeners()
        loadPendingEdit()

        binding.titleInput.doAfterTextChanged { viewModel.title = it?.toString()?.ifBlank { null } }
        binding.descriptionInput.doAfterTextChanged { viewModel.description = it?.toString()?.ifBlank { null } }

        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.currentDeviceId == null) {
                viewModel.currentDeviceId = InstallationIdentifier.getInstallationIdentifier(context)
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
                binding.btSave.text = "Update"
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

        binding.btSelectLocation.setOnClickListener {
            viewModel.activePickingIndex = -1
            addMemoryGroupListener?.onPickLocation(viewModel.lat, viewModel.lng, viewModel.placeName, viewModel.address)
        }

        binding.dateHeader.setOnClickListener { toggleDateSection() }

        binding.switchAllDay.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isAllDay = isChecked
            updateDateTimeButtons()
        }

        binding.btStartDate.setOnClickListener { pickDate(true) }
        binding.btStartTime.setOnClickListener { pickTime(true) }
        binding.btEndDate.setOnClickListener { pickDate(false) }
        binding.btEndTime.setOnClickListener { pickTime(false) }
        binding.btDateRange.setOnClickListener { pickDateRange() }

        binding.colorHeader.setOnClickListener { toggleColor() }

        binding.sliderHue.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.markerHue = value
                updateColorUI()
            }
        }

        binding.sliderSaturation.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.markerSaturation = value
                updateColorUI()
            }
        }

        binding.sliderBrightness.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.markerBrightness = value
                updateColorUI()
            }
        }

        binding.fragmentsHeader.setOnClickListener { toggleFragments() }
        binding.btAddFragment.setOnClickListener { addFragment() }

        binding.btPickMedia.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }

        binding.btClear.setOnClickListener { showClearConfirmationDialog() }

        binding.btSave.setOnClickListener {
            try {
                require(viewModel.title?.isNotBlank() ?: false) { "The title must not be blank!" }
                require(viewModel.lat != null && viewModel.lng != null) { "The location must be specified!" }
                require(viewModel.fragments.all { it.lat != null && it.lng != null }) { "The location must be specified for all fragments!" }
            } catch (e: IllegalArgumentException) {
                context.let { Toast.makeText(it, e.message, Toast.LENGTH_SHORT).show() }
                return@setOnClickListener
            }
            viewModel.saveMemoryGroup(commonViewModel.getDb())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSaving.collect { isSaving ->
                        binding.btSave.visibility = if (isSaving) View.INVISIBLE else View.VISIBLE
                        binding.btClear.isEnabled = !isSaving
                        binding.progressSave.visibility = if (isSaving) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.saveResult.collect { result ->
                        val context = context
                        when (result) {
                            is AddMemoryGroupFragmentViewModel.SaveResult.Success -> {
                                // The view model has already cleared the form.
                                addMemoryGroupListener?.onMemorySaved(result.groupId)
                                showClearedForm()
                            }

                            is AddMemoryGroupFragmentViewModel.SaveResult.Error -> {
                                context.let {
                                    Toast.makeText(it, "Failed to save: ${result.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        mediaAdapter = SelectedMediaAdapter(viewModel.currentDeviceId) { item ->
            viewModel.selectedMedia.remove(item)
            updateMediaUI()
        }
        mediaAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.rvSelectedMedia.scrollToPosition(positionStart)
            }
        })
        binding.rvSelectedMedia.adapter = mediaAdapter

        fragmentsAdapter = MemoryFragmentEditAdapter(
            viewModel.fragments,
            { addMemoryGroupListener },
            { childFragmentManager },
            { viewModel.activePickingIndex = it },
            this::updateFragmentsUI
        )
        binding.rvFragments.layoutManager = LinearLayoutManager(context)
        binding.rvFragments.adapter = fragmentsAdapter

        colorPresetAdapter = ColorPresetAdapter { preset ->
            viewModel.markerHue = preset.hue
            viewModel.markerSaturation = preset.saturation
            viewModel.markerBrightness = preset.brightness
            updateColorUI(animate = true)
        }
        binding.rvPresetColors.adapter = colorPresetAdapter
    }

    private fun updateMediaUI() {
        // We pass a new list instance (toList()) to ensure it detects the change.
        mediaAdapter.submitList(viewModel.selectedMedia.toList())
        binding.tvSelectedMediaCount.text = "${viewModel.selectedMedia.size} items selected"
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
            binding.sliderHue.value = viewModel.markerHue
            binding.sliderSaturation.value = viewModel.markerSaturation
            binding.sliderBrightness.value = viewModel.markerBrightness

            binding.sliderHue.thumbTintList = colorStateList
            binding.sliderSaturation.thumbTintList = colorStateList
            binding.sliderBrightness.thumbTintList = colorStateList

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
        val startH = binding.sliderHue.value
        val startS = binding.sliderSaturation.value
        val startV = binding.sliderBrightness.value

        // Use a single animator from 0 to 1 (representing 0% to 100% of the transition)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOR_CHANGE_ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val currentBinding = _binding ?: return@addUpdateListener

                // Calculate current values based on the animation progress
                val currentH = lerpWithStep(startH, viewModel.markerHue, fraction, currentBinding.sliderHue.stepSize)
                val currentS = lerpWithStep(startS, viewModel.markerSaturation, fraction, currentBinding.sliderSaturation.stepSize)
                val currentV = lerpWithStep(startV, viewModel.markerBrightness, fraction, currentBinding.sliderBrightness.stepSize)

                currentBinding.sliderHue.value = currentH
                currentBinding.sliderSaturation.value = currentS
                currentBinding.sliderBrightness.value = currentV

                val currentColor = ColorUtil.hsvToColor(currentH, currentS, currentV)
                currentBinding.colorIndicator.setBackgroundColor(currentColor)

                val currentStateList = ColorStateList.valueOf(currentColor)
                currentBinding.sliderHue.thumbTintList = currentStateList
                currentBinding.sliderSaturation.thumbTintList = currentStateList
                currentBinding.sliderBrightness.thumbTintList = currentStateList

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
        val context = context ?: return
        AlertDialog.Builder(context)
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
        viewModel.resetForm()
        showClearedForm()
    }

    /** Shows the empty form after [AddMemoryGroupFragmentViewModel.resetForm]. */
    private fun showClearedForm() {
        updateDateTimeButtons()
        updateMediaUI()
        updateFragmentsUI()
        updateColorUI()

        binding.titleInput.text?.clear()
        binding.descriptionInput.text?.clear()
        updateLocationText()
        binding.switchAllDay.isChecked = false
        binding.btSave.text = "Save Memory"
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // A save that finishes while another tab is shown must not open the memory once this tab is shown again.
        viewModel.isScreenShown = !hidden
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

    /**
     * Opens memory [memoryId] for editing. The request is kept in the ViewModel and handled once this fragment has a
     * view (right away if it has one), so it can be made while the tab is hidden or still being shown. If the form has
     * unsaved changes, the user is asked first; for the memory already being edited, the changes are kept.
     */
    fun requestEditMode(memoryId: Int) {
        viewModel.pendingEditMemoryId = memoryId
        loadPendingEdit()
    }

    /** Handles the pending edit request, if any. It stays pending until handled, so a view destroyed meanwhile retries it. */
    private fun loadPendingEdit() {
        val memoryId = viewModel.pendingEditMemoryId ?: return
        val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        when {
            !viewModel.hasUnsavedChanges() -> loadForEditing(memoryId, viewLifecycleOwner)
            memoryId == viewModel.editingMemoryId -> viewModel.pendingEditMemoryId = null
            else -> confirmDiscardForEdit(memoryId)
        }
    }

    private fun loadForEditing(memoryId: Int, viewLifecycleOwner: LifecycleOwner) {
        viewLifecycleOwner.lifecycleScope.launch {
            val groupWithMedia = withContext(Dispatchers.IO) { commonViewModel.getMemoryGroupDao().getGroupWithMedia(memoryId) }
            if (viewModel.pendingEditMemoryId != memoryId) return@launch // A newer request replaced this one.
            viewModel.pendingEditMemoryId = null
            groupWithMedia?.let(::showForEditing) // Null if the memory was deleted meanwhile: then the form stays as it is.
        }
    }

    /**
     * Asks whether the edit request may replace the unsaved changes. The request stays pending while the question is
     * shown, so it is asked again if the view is recreated (the dialog is dismissed with the view).
     */
    private fun confirmDiscardForEdit(memoryId: Int) {
        val context = context ?: return
        val isEditing = viewModel.editingMemoryId != null
        discardForEditDialog?.dismiss()
        discardForEditDialog = AlertDialog.Builder(context)
            .setTitle(if (isEditing) R.string.discard_edit_title else R.string.discard_draft_title)
            .setMessage(if (isEditing) R.string.discard_edit_message else R.string.discard_draft_message)
            .setPositiveButton(R.string.discard) { _, _ ->
                val viewLifecycleOwner = viewLifecycleOwnerLiveData.value
                if (viewLifecycleOwner != null && viewModel.pendingEditMemoryId == memoryId) loadForEditing(memoryId, viewLifecycleOwner)
            }
            .setNegativeButton(R.string.keep_editing) { _, _ -> dropPendingEdit(memoryId) }
            .setOnCancelListener { dropPendingEdit(memoryId) }
            .show()
    }

    private fun dropPendingEdit(memoryId: Int) {
        if (viewModel.pendingEditMemoryId == memoryId) viewModel.pendingEditMemoryId = null
    }

    /** Called when another tab is selected: an edit without changes is closed, so this tab offers a new memory next time. */
    fun closeUnchangedEdit() {
        if (viewModel.editingMemoryId == null || viewModel.hasUnsavedChanges()) return
        viewModel.resetForm()
        if (_binding != null) showClearedForm()
    }

    private fun showForEditing(data: MemoryGroupWithMedia) {
        val binding = _binding ?: return
        viewModel.fillForEditing(data)
        binding.titleInput.setText(viewModel.title)
        binding.descriptionInput.setText(viewModel.description)
        binding.switchAllDay.isChecked = viewModel.isAllDay
        updateMediaUI()
        updateFragmentsUI()
        updateLocationText()
        updateDateTimeButtons()
        updateColorUI()
        binding.btSave.text = "Update"
        viewModel.markUnchanged() // Last, after the views have written back what they show.
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
        binding.tvLocation.text = locationString.toString()
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
            binding.tvStartDate.visibility = View.GONE
            binding.tvEndDate.visibility = View.GONE
            binding.btDateRange.visibility = View.VISIBLE

            val startStr = viewModel.startDateTime.format(dateFormatter())
            val endStr = viewModel.endDateTime.format(dateFormatter())
            val dateRangeStr = if (startStr == endStr) startStr else "$startStr - $endStr"
            binding.btDateRange.text = dateRangeStr
            binding.tvDateSummary.text = dateRangeStr
        } else {
            binding.startDateTimeLayout.visibility = View.VISIBLE
            binding.endDateTimeLayout.visibility = View.VISIBLE
            binding.tvStartDate.visibility = View.VISIBLE
            binding.tvEndDate.visibility = View.VISIBLE
            binding.btDateRange.visibility = View.GONE

            val startDStr = viewModel.startDateTime.format(dateFormatter())
            val startTStr = viewModel.startDateTime.format(timeFormatter())
            val endDStr = viewModel.endDateTime.format(dateFormatter())
            val endTStr = viewModel.endDateTime.format(timeFormatter())

            binding.btStartDate.text = startDStr
            binding.btEndDate.text = endDStr
            binding.btStartTime.text = startTStr
            binding.btEndTime.text = endTStr

            val startFull = "$startDStr, $startTStr"
            val endFull = "$endDStr, $endTStr"
            binding.tvDateSummary.text = if (startDStr == endDStr) {
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

        // MaterialDatePicker expects UTC midnight millis
        val startMillis = viewModel.startDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endMillis = viewModel.endDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val selection = androidx.core.util.Pair(startMillis, endMillis)

        builder.setSelection(selection)

        val picker = builder.build()
        picker.setOnPicked(::applyPickedDateRange)
        picker.show(childFragmentManager, DATE_RANGE_PICKER_TAG)
    }

    private fun pickDate(isStart: Boolean) {
        val current = if (isStart) viewModel.startDateTime else viewModel.endDateTime
        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStart) "Select Start Date" else "Select End Date")
            .setSelection(current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())

        val picker = builder.build()
        picker.setOnPicked { applyPickedDate(it, isStart) }
        picker.show(childFragmentManager, if (isStart) START_DATE_PICKER_TAG else END_DATE_PICKER_TAG)
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

        picker.setOnPicked { applyPickedTime(it, isStart) }
        picker.show(childFragmentManager, if (isStart) START_TIME_PICKER_TAG else END_TIME_PICKER_TAG)
    }

    /** Attaches the listeners of the memory's date and time pickers that are still open after the view was recreated. */
    private fun reattachPickerListeners() {
        val manager = childFragmentManager
        manager.findDatePicker<androidx.core.util.Pair<Long, Long>>(DATE_RANGE_PICKER_TAG)
            ?.setOnPicked(::applyPickedDateRange)
        manager.findDatePicker<Long>(START_DATE_PICKER_TAG)?.setOnPicked { applyPickedDate(it, isStart = true) }
        manager.findDatePicker<Long>(END_DATE_PICKER_TAG)?.setOnPicked { applyPickedDate(it, isStart = false) }
        manager.findTimePicker(START_TIME_PICKER_TAG)?.setOnPicked { applyPickedTime(it, isStart = true) }
        manager.findTimePicker(END_TIME_PICKER_TAG)?.setOnPicked { applyPickedTime(it, isStart = false) }
    }

    private fun applyPickedDateRange(range: androidx.core.util.Pair<Long, Long>) {
        val first = range.first ?: return
        val second = range.second ?: return
        // MaterialDatePicker returns UTC midnight millis.
        // We convert to LocalDate using UTC to avoid off-by-one errors in some timezones.
        val newStartDate = Instant.ofEpochMilli(first).atZone(ZoneOffset.UTC).toLocalDate()
        val newEndDate = Instant.ofEpochMilli(second).atZone(ZoneOffset.UTC).toLocalDate()

        viewModel.startDateTime = newStartDate.atStartOfDay(ZoneId.systemDefault())
        viewModel.endDateTime = newEndDate.atStartOfDay(ZoneId.systemDefault())
        updateDateTimeButtons()
    }

    private fun applyPickedDate(selection: Long, isStart: Boolean) {
        // MaterialDatePicker returns UTC midnight millis.
        // We convert to LocalDate using UTC to avoid off-by-one errors in some timezones.
        val newDate = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate()
        if (isStart) {
            viewModel.startDateTime = newDate.atTime(viewModel.startDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
            if (viewModel.endDateTime.isBefore(viewModel.startDateTime)) viewModel.endDateTime = viewModel.startDateTime.plusHours(1)
        } else {
            viewModel.endDateTime = newDate.atTime(viewModel.endDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
            if (viewModel.endDateTime.isBefore(viewModel.startDateTime)) viewModel.startDateTime = viewModel.endDateTime.minusHours(1)
        }
        updateDateTimeButtons()
    }

    private fun applyPickedTime(newTime: LocalTime, isStart: Boolean) {
        if (isStart) {
            viewModel.startDateTime = viewModel.startDateTime.with(newTime)
            if (viewModel.endDateTime.isBefore(viewModel.startDateTime)) viewModel.endDateTime = viewModel.startDateTime.plusHours(1)
        } else {
            viewModel.endDateTime = viewModel.endDateTime.with(newTime)
            if (viewModel.endDateTime.isBefore(viewModel.startDateTime)) viewModel.startDateTime = viewModel.endDateTime.minusHours(1)
        }
        updateDateTimeButtons()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        discardForEditDialog?.dismiss()
        discardForEditDialog = null
        _binding = null
    }

    companion object {
        const val TAG = "AddMemoryGroupFragment"

        // Distinct from the tags of the fragment pickers (MemoryFragmentEditAdapter), which share childFragmentManager.
        private const val DATE_RANGE_PICKER_TAG = "MEMORY_DATE_RANGE_PICKER"
        private const val START_DATE_PICKER_TAG = "MEMORY_START_DATE_PICKER"
        private const val END_DATE_PICKER_TAG = "MEMORY_END_DATE_PICKER"
        private const val START_TIME_PICKER_TAG = "MEMORY_START_TIME_PICKER"
        private const val END_TIME_PICKER_TAG = "MEMORY_END_TIME_PICKER"
        private const val FACING_RIGHT_ROTATION = 0f
        private const val FACING_DOWN_ROTATION = 90f
        private const val COLOR_CHANGE_ANIMATION_DURATION = 300L
    }
}
