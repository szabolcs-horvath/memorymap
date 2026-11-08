package com.szabolcshorvath.memorymap.fragment

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
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
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.ColorPresetAdapter
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter
import com.szabolcshorvath.memorymap.adapter.SelectedMediaAdapter
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.databinding.FragmentAddMemoryGroupBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.ConfirmDialog
import com.szabolcshorvath.memorymap.util.Coordinates
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import com.szabolcshorvath.memorymap.util.InstallationIdentifier
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.findTimePicker
import com.szabolcshorvath.memorymap.util.onPhonesClock
import com.szabolcshorvath.memorymap.util.reattachDatePicker
import com.szabolcshorvath.memorymap.util.setOnPicked
import com.szabolcshorvath.memorymap.util.startingWeeksOnPhonesFirstDay
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
    private lateinit var mediaAdapter: SelectedMediaAdapter
    private lateinit var fragmentsAdapter: MemoryFragmentEditAdapter
    private lateinit var colorPresetAdapter: ColorPresetAdapter

    private val pickMediaLauncher =
        registerForActivityResult(AppServices.mediaPicker) { uris ->
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
                    uris.map { uri -> readPickedMedia(context, uri, deviceId) }
                }
                viewModel.onMediaPicked(uris.map { it.toString() })
                val newItems = pickedItems.filter { picked -> viewModel.selectedMedia.none { it.uri == picked.uri } }
                // New media items should be first in the list
                viewModel.selectedMedia.addAll(0, newItems)
                updateMediaUI()
            }
        }

    /** Reads the type, size and date taken of a picked item. Does content resolver calls: not on the main thread. */
    private fun readPickedMedia(context: Context, uri: Uri, deviceId: String): SelectedMedia {
        val type = context.contentResolver.getType(uri)
        val mediaType = if (type != null && type.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
        val metadata = LocalMediaUtil.queryMetadata(context, uri)
        if (metadata == null) Log.w(TAG, "Could not read the size and date of picked media $uri")
        return SelectedMedia(uri, mediaType, deviceId, metadata?.size ?: 0L, metadata?.dateTaken ?: 0L)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The questions come back after a rotation or process death, so what they act on comes with the answer.
        ConfirmDialog.listen(this, CLEAR_REQUEST) { clearFields() }
        ConfirmDialog.listen(this, DISCARD_FOR_EDIT_REQUEST, onCancel = { dropPendingEdit(it.getInt(MEMORY_ID)) }) { extras ->
            val memoryId = extras.getInt(MEMORY_ID)
            val viewLifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@listen
            viewModel.pendingEditMemoryId = memoryId // After process death, the new ViewModel no longer has it.
            loadForEditing(memoryId, viewLifecycleOwner)
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

            // The form shows what the ViewModel holds: on first load, after a rotation and after process death.
            showForm()
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
            val missing = when {
                viewModel.title.isNullOrBlank() -> R.string.save_needs_title
                viewModel.lat == null || viewModel.lng == null -> R.string.save_needs_location
                viewModel.fragments.any { it.lat == null || it.lng == null } -> R.string.save_needs_fragment_locations
                else -> null
            }
            if (missing != null) {
                Toast.makeText(requireContext(), missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveMemoryGroup(commonViewModel.getDb())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSaving.collect { isSaving ->
                    binding.btSave.visibility = if (isSaving) View.INVISIBLE else View.VISIBLE
                    binding.btClear.isEnabled = !isSaving
                    binding.progressSave.visibility = if (isSaving) View.VISIBLE else View.GONE
                }
            }
        }

        // Only while shown: a hidden tab keeps its view, and a save error must not show over another screen.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.saveResult.collect { result ->
                    when (result) {
                        is AddMemoryGroupFragmentViewModel.SaveResult.Success -> {
                            if (result.openMemory) addMemoryGroupListener?.onMemorySaved(result.groupId)
                            // The view model has already cleared the form. One that waited may have been filled since
                            // (an Edit while this tab was hidden), so show whatever it holds now.
                            showForm()
                        }

                        AddMemoryGroupFragmentViewModel.SaveResult.Error -> {
                            Toast.makeText(context, R.string.memory_save_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        mediaAdapter = SelectedMediaAdapter(viewModel.currentDeviceId) { item ->
            viewModel.removeMedia(item)
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
        binding.tvSelectedMediaCount.text = resources.getQuantityString(R.plurals.items_selected, viewModel.selectedMedia.size, viewModel.selectedMedia.size)
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
        colorPresetAdapter.setSelectedColor(viewModel.markerHue, viewModel.markerSaturation, viewModel.markerBrightness)

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
        val isEditing = viewModel.editingMemoryId != null
        ConfirmDialog.show(
            childFragmentManager,
            CLEAR_REQUEST,
            title = getString(if (isEditing) R.string.discard_changes_title else R.string.clear_fields_title),
            message = if (isEditing) {
                getString(R.string.discard_changes_message)
            } else {
                getString(R.string.clear_fields_message)
            },
            confirm = getString(if (isEditing) R.string.discard else R.string.clear),
            cancel = getString(R.string.cancel)
        )
    }

    private fun clearFields() {
        viewModel.resetForm()
        showForm()
    }

    /** Shows everything the ViewModel holds: when the view is created, and after the form was cleared. */
    private fun showForm() {
        updateInputs()
        updateLocationText()
        updateDateTimeButtons()
        updateColorUI()
        updateMediaUI()
        updateFragmentsUI()
        binding.btSave.setText(if (viewModel.editingMemoryId != null) R.string.update_memory else R.string.save_memory)
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
     * shown, and the question comes back after a rotation or process death. A recreated view asks it again, replacing
     * the open one.
     */
    private fun confirmDiscardForEdit(memoryId: Int) {
        if (isStateSaved) return
        val isEditing = viewModel.editingMemoryId != null
        ConfirmDialog.show(
            childFragmentManager,
            DISCARD_FOR_EDIT_REQUEST,
            title = getString(if (isEditing) R.string.discard_edit_title else R.string.discard_draft_title),
            message = getString(if (isEditing) R.string.discard_edit_message else R.string.discard_draft_message),
            confirm = getString(R.string.discard),
            cancel = getString(R.string.keep_editing),
            extras = bundleOf(MEMORY_ID to memoryId)
        )
    }

    private fun dropPendingEdit(memoryId: Int) {
        if (viewModel.pendingEditMemoryId == memoryId) viewModel.pendingEditMemoryId = null
    }

    /** The URIs of the media in the form (a draft or an edit), which a save may still store. */
    fun mediaUrisInForm(): Set<String> {
        if (!isAdded) return emptySet() // A detached fragment has no ViewModel to read.
        return viewModel.selectedMedia.mapTo(HashSet()) { it.uri.toString() }
    }

    /** Called when another tab is selected: an edit without changes is closed, so this tab offers a new memory next time. */
    fun closeUnchangedEdit() {
        if (viewModel.editingMemoryId == null || viewModel.hasUnsavedChanges()) return
        viewModel.resetForm()
        if (_binding != null) showForm()
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
        binding.btSave.setText(R.string.update_memory)
        viewModel.markUnchanged() // Last, after the views have written back what they show.
    }

    /**
     * Shows the title, description and all-day switch the ViewModel holds. Android doesn't restore these inputs itself
     * (`saveEnabled="false"`): the text they had when the view was last destroyed would overwrite a form that was
     * cleared meanwhile, such as by a save that finished while another tab was shown.
     */
    private fun updateInputs() {
        binding.titleInput.setText(viewModel.title)
        binding.descriptionInput.setText(viewModel.description)
        binding.switchAllDay.isChecked = viewModel.isAllDay
    }

    private fun updateLocationText() {
        val lat = viewModel.lat
        val lng = viewModel.lng
        val locationString = StringBuilder()
        if (viewModel.placeName != null) locationString.append(viewModel.placeName).append(System.lineSeparator())
        if (viewModel.address != null) locationString.append(viewModel.address).append(System.lineSeparator())
        if (locationString.isEmpty()) {
            if (lat != null && lng != null) {
                locationString.append(getString(R.string.location_coordinates, Coordinates.format(requireContext(), lat, lng)))
            } else {
                locationString.append(getString(R.string.no_location_selected))
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

            val startStr = DateTimeFormatterUtil.date(viewModel.startDateTime)
            val endStr = DateTimeFormatterUtil.date(viewModel.endDateTime)
            val dateRangeStr = if (startStr == endStr) startStr else getString(R.string.date_range, startStr, endStr)
            binding.btDateRange.text = dateRangeStr
            binding.tvDateSummary.text = dateRangeStr
        } else {
            binding.startDateTimeLayout.visibility = View.VISIBLE
            binding.endDateTimeLayout.visibility = View.VISIBLE
            binding.tvStartDate.visibility = View.VISIBLE
            binding.tvEndDate.visibility = View.VISIBLE
            binding.btDateRange.visibility = View.GONE

            val startDStr = DateTimeFormatterUtil.date(viewModel.startDateTime)
            val startTStr = DateTimeFormatterUtil.time(binding.root.context, viewModel.startDateTime)
            val endDStr = DateTimeFormatterUtil.date(viewModel.endDateTime)
            val endTStr = DateTimeFormatterUtil.time(binding.root.context, viewModel.endDateTime)

            binding.btStartDate.text = startDStr
            binding.btEndDate.text = endDStr
            binding.btStartTime.text = startTStr
            binding.btEndTime.text = endTStr

            val startFull = getString(R.string.date_comma_time, startDStr, startTStr)
            val endFull = getString(R.string.date_comma_time, endDStr, endTStr)
            binding.tvDateSummary.text = if (startDStr == endDStr) {
                getString(R.string.date_comma_time_range, startDStr, startTStr, endTStr)
            } else {
                getString(R.string.date_range, startFull, endFull)
            }
        }
    }

    private fun pickDateRange() {
        val builder = MaterialDatePicker.Builder.dateRangePicker().startingWeeksOnPhonesFirstDay()
            .setTitleText(R.string.select_date_range)
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
        val builder = MaterialDatePicker.Builder.datePicker().startingWeeksOnPhonesFirstDay()
            .setTitleText(if (isStart) R.string.select_start_date else R.string.select_end_date)
            .setSelection(current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())

        val picker = builder.build()
        picker.setOnPicked { applyPickedDate(it, isStart) }
        picker.show(childFragmentManager, if (isStart) START_DATE_PICKER_TAG else END_DATE_PICKER_TAG)
    }

    private fun pickTime(isStart: Boolean) {
        val current = if (isStart) viewModel.startDateTime else viewModel.endDateTime
        val picker = MaterialTimePicker.Builder()
            .onPhonesClock(requireContext())
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(if (isStart) R.string.select_start_time else R.string.select_end_time)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()

        picker.setOnPicked { applyPickedTime(it, isStart) }
        picker.show(childFragmentManager, if (isStart) START_TIME_PICKER_TAG else END_TIME_PICKER_TAG)
    }

    /** Attaches the listeners of the memory's date and time pickers that are still open after the view was recreated. */
    private fun reattachPickerListeners() {
        val manager = childFragmentManager
        manager.reattachDatePicker(DATE_RANGE_PICKER_TAG, ::applyPickedDateRange)
        manager.reattachDatePicker<Long>(START_DATE_PICKER_TAG) { applyPickedDate(it, isStart = true) }
        manager.reattachDatePicker<Long>(END_DATE_PICKER_TAG) { applyPickedDate(it, isStart = false) }
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
        private const val CLEAR_REQUEST = "clear_form"
        private const val DISCARD_FOR_EDIT_REQUEST = "discard_for_edit"
        private const val MEMORY_ID = "memory_id"
    }
}
