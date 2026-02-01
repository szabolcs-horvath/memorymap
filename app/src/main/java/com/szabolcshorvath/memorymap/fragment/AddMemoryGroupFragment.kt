package com.szabolcshorvath.memorymap.fragment

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.material.datepicker.MaterialDatePicker
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentAddMemoryGroupBinding
import com.szabolcshorvath.memorymap.databinding.ItemMediaSelectedBinding
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class AddMemoryGroupFragment : Fragment() {

    private var _binding: FragmentAddMemoryGroupBinding? = null
    private val binding get() = _binding!!
    private val selectedMediaUris = mutableListOf<Pair<Uri, MediaType>>()
    private var lat = 0.0
    private var lng = 0.0
    private var placeName: String? = null
    private var address: String? = null

    private var startDateTime: ZonedDateTime = ZonedDateTime.now()
    private var endDateTime: ZonedDateTime = ZonedDateTime.now().plusHours(1)
    private var isAllDay = false
    private var markerHue: Float = BitmapDescriptorFactory.HUE_RED

    private var listener: AddMemoryListener? = null
    private lateinit var backupManager: BackupManager
    private var editingMemoryId: Int? = null
    private lateinit var mediaAdapter: SelectedMediaAdapter

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(
        Locale.getDefault()
    )
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(
        Locale.getDefault()
    )

    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            uris.let { it ->
                val contentResolver = requireContext().contentResolver
                val newItems = it.mapNotNull { uri ->
                    if (selectedMediaUris.any { it.first == uri }) {
                        null
                    } else {
                        val type = contentResolver.getType(uri)
                        val mediaType =
                            if (type != null && type.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
                        uri to mediaType
                    }
                }
                selectedMediaUris.addAll(newItems)
                updateMediaUI()
                it.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
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
        setupRecyclerView()
        updateLocationText()
        updateDateTimeButtons()
        setupPresetColors()
        updateHueUI()

        binding.selectLocationButton.setOnClickListener {
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

        binding.hueSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                markerHue = value
                updateHueUI()
            }
        }

        binding.pickMediaButton.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }

        binding.clearButton.setOnClickListener {
            showClearConfirmationDialog()
        }

        binding.saveButton.setOnClickListener {
            lifecycleScope.launch {
                saveMemoryGroup()
            }
        }
    }

    private fun setupRecyclerView() {
        mediaAdapter = SelectedMediaAdapter(selectedMediaUris) { position ->
            selectedMediaUris.removeAt(position)
            updateMediaUI()
        }
        binding.selectedMediaRecyclerView.adapter = mediaAdapter
    }

    private fun updateMediaUI() {
        mediaAdapter.notifyDataSetChanged()
        binding.selectedMediaCount.text = "${selectedMediaUris.size} items selected"
    }

    private fun setupPresetColors() {
        val presets = listOf(
            BitmapDescriptorFactory.HUE_RED,
            BitmapDescriptorFactory.HUE_ORANGE,
            BitmapDescriptorFactory.HUE_YELLOW,
            BitmapDescriptorFactory.HUE_GREEN,
            BitmapDescriptorFactory.HUE_CYAN,
            BitmapDescriptorFactory.HUE_AZURE,
            BitmapDescriptorFactory.HUE_BLUE,
            BitmapDescriptorFactory.HUE_VIOLET,
            BitmapDescriptorFactory.HUE_MAGENTA,
            BitmapDescriptorFactory.HUE_ROSE
        )

        binding.presetColorsLayout.removeAllViews()
        val size = (32 * resources.displayMetrics.density).toInt()
        val margin = (12 * resources.displayMetrics.density).toInt()

        presets.forEach { hue ->
            val view = View(requireContext())
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(0, 0, margin, 0)
            view.layoutParams = params

            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            shape.setColor(Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            // Add a stroke to make it look nicer, especially for light colors
            shape.setStroke((1 * resources.displayMetrics.density).toInt(), Color.LTGRAY)
            view.background = shape

            view.setOnClickListener {
                markerHue = hue
                updateHueUI()
            }
            binding.presetColorsLayout.addView(view)
        }
    }

    private fun updateHueUI() {
        binding.hueSlider.value = markerHue
        val color = Color.HSVToColor(floatArrayOf(markerHue, 1f, 1f))
        val colorStateList = ColorStateList.valueOf(color)
        binding.hueSlider.thumbTintList = colorStateList
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(if (editingMemoryId != null) "Discard Changes" else "Clear Fields")
            .setMessage(if (editingMemoryId != null) "Are you sure you want to discard your changes?" else "Are you sure you want to clear all fields? This action cannot be undone.")
            .setPositiveButton(if (editingMemoryId != null) "Discard" else "Clear") { _, _ ->
                clearFields()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        markerHue = BitmapDescriptorFactory.HUE_RED
        updateDateTimeButtons()
        updateHueUI()
        selectedMediaUris.clear()
        updateMediaUI()

        binding.titleInput.text?.clear()
        binding.descriptionInput.text?.clear()
        updateLocationText()
        binding.allDayCheckbox.isChecked = false
        binding.saveButton.text = "Save Memory"
    }

    fun updateLocation(
        newLat: Double,
        newLng: Double,
        newPlaceName: String? = null,
        newAddress: String? = null
    ) {
        lat = newLat
        lng = newLng
        placeName = newPlaceName
        address = newAddress
        if (_binding != null) {
            updateLocationText()
        }
    }

    fun setEditMode(memoryId: Int) {
        editingMemoryId = memoryId
        lifecycleScope.launch(Dispatchers.IO) {
            val db = StoryMapDatabase.getDatabase(requireContext().applicationContext)
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
                    markerHue = group.markerHue ?: BitmapDescriptorFactory.HUE_RED

                    binding.titleInput.setText(group.title)
                    binding.descriptionInput.setText(group.description)
                    binding.allDayCheckbox.isChecked = isAllDay

                    selectedMediaUris.clear()
                    selectedMediaUris.addAll(data.mediaItems.map { it.uri.toUri() to it.type })
                    updateMediaUI()

                    updateLocationText()
                    updateDateTimeButtons()
                    updateHueUI()
                    binding.saveButton.text = "Update Memory"
                }
            }
        }
    }

    private fun updateLocationText() {
        val locationString = StringBuilder()
        if (placeName != null) {
            locationString.append(placeName).append("\n")
        }
        if (address != null) {
            locationString.append(address).append("\n")
        }
        locationString.append("$lat, $lng")
        binding.locationText.text = locationString.toString()
    }

    private fun updateDateTimeButtons() {
        if (isAllDay) {
            binding.startDateTimeLayout.visibility = View.GONE
            binding.endDateTimeLayout.visibility = View.GONE
            binding.startDateLabel.visibility = View.GONE
            binding.endDateLabel.visibility = View.GONE
            binding.dateRangeButton.visibility = View.VISIBLE

            val startStr = startDateTime.format(dateFormatter)
            val endStr = endDateTime.format(dateFormatter)
            binding.dateRangeButton.text =
                if (startStr == endStr) startStr else "$startStr - $endStr"
        } else {
            binding.startDateTimeLayout.visibility = View.VISIBLE
            binding.endDateTimeLayout.visibility = View.VISIBLE
            binding.startDateLabel.visibility = View.VISIBLE
            binding.endDateLabel.visibility = View.VISIBLE
            binding.dateRangeButton.visibility = View.GONE

            binding.startDateButton.text = startDateTime.format(dateFormatter)
            binding.endDateButton.text = endDateTime.format(dateFormatter)
            binding.startTimeButton.text = startDateTime.format(timeFormatter)
            binding.endTimeButton.text = endDateTime.format(timeFormatter)
        }
    }

    private fun pickDateRange() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Select Date Range")

        val selection = androidx.core.util.Pair(
            startDateTime.toInstant().toEpochMilli(),
            endDateTime.toInstant().toEpochMilli()
        )
        builder.setSelection(selection)

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { range ->
            val startMillis = range.first
            val endMillis = range.second

            if (startMillis != null && endMillis != null) {
                startDateTime = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault())
                endDateTime = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault())
                updateDateTimeButtons()
            }
        }
        picker.show(childFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun pickDate(isStart: Boolean) {
        val current = if (isStart) startDateTime else endDateTime
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newDate = LocalDate.of(year, month + 1, dayOfMonth)
                if (isStart) {
                    startDateTime =
                        newDate.atTime(startDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
                    if (endDateTime.isBefore(startDateTime)) {
                        endDateTime = startDateTime.plusHours(1)
                    }
                } else {
                    endDateTime =
                        newDate.atTime(endDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
                    if (endDateTime.isBefore(startDateTime)) {
                        startDateTime = endDateTime.minusHours(1)
                    }
                }
                updateDateTimeButtons()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        )
        datePickerDialog.show()
    }

    private fun pickTime(isStart: Boolean) {
        val current = if (isStart) startDateTime else endDateTime
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val newTime = LocalTime.of(hourOfDay, minute)
                if (isStart) {
                    startDateTime = startDateTime.with(newTime)
                    if (endDateTime.isBefore(startDateTime)) {
                        endDateTime = startDateTime.plusHours(1)
                    }
                } else {
                    endDateTime = endDateTime.with(newTime)
                    if (endDateTime.isBefore(startDateTime)) {
                        startDateTime = endDateTime.minusHours(1)
                    }
                }
                updateDateTimeButtons()
            },
            current.hour,
            current.minute,
            true
        )
        timePickerDialog.show()
    }

    private suspend fun saveMemoryGroup() {
        val title = binding.titleInput.text.toString()
        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        val description = binding.descriptionInput.text.toString().ifBlank { null }

        val finalStart = if (isAllDay) startDateTime.toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()) else startDateTime
        val finalEnd = if (isAllDay) endDateTime.toLocalDate().atTime(23, 59, 59)
            .atZone(ZoneId.systemDefault()) else endDateTime

        val context = requireContext()
        val contentResolver = context.contentResolver
        val deviceId = InstallationIdentifier.getInstallationIdentifier(context)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = StoryMapDatabase.getDatabase(context.applicationContext)
            val group = MemoryGroup(
                id = editingMemoryId ?: 0,
                title = title,
                description = description,
                latitude = lat,
                longitude = lng,
                placeName = placeName,
                address = address,
                startDate = finalStart,
                endDate = finalEnd,
                isAllDay = isAllDay,
                markerHue = markerHue
            )

            val groupId = if (editingMemoryId != null) {
                db.memoryGroupDao().updateGroup(group)
                editingMemoryId!!.toLong()
            } else {
                db.memoryGroupDao().insertGroup(group)
            }

            // If editing, we delete the old media associations and re-insert the current selection
            if (editingMemoryId != null) {
                db.memoryGroupDao().deleteMediaByGroupId(groupId.toInt())
            }

            val mediaItems = selectedMediaUris.map { (uri, type) ->
                var size = 0L
                var date = 0L

                try {
                    contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                            val dateIdx =
                                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                            size = cursor.getLong(sizeIdx)
                            date = cursor.getLong(dateIdx)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting file size", e)
                }

                MediaItem(
                    groupId = groupId.toInt(),
                    uri = uri.toString(),
                    deviceId = deviceId,
                    type = type,
                    mediaSignature = MediaHasher.calculateMediaSignature(context, uri),
                    fileSize = size,
                    dateTaken = date
                )
            }
            db.memoryGroupDao().insertMediaItems(mediaItems)

            launch {
                backupManager.triggerAutomaticBackup()
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    if (editingMemoryId != null) "Updated!" else "Saved!",
                    Toast.LENGTH_SHORT
                ).show()
                listener?.onMemorySaved(
                    group.latitude,
                    group.longitude,
                    groupId.toInt(),
                    group.startDate.toLocalDate(),
                    group.endDate.toLocalDate()
                )
                clearFields()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class SelectedMediaAdapter(
        private val items: List<Pair<Uri, MediaType>>,
        private val onRemove: (Int) -> Unit
    ) : RecyclerView.Adapter<SelectedMediaAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemMediaSelectedBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                ItemMediaSelectedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (uri, type) = items[position]
            holder.binding.thumbnailImage.load(uri)
            holder.binding.videoIcon.visibility =
                if (type == MediaType.VIDEO) View.VISIBLE else View.GONE
            holder.binding.removeButton.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val TAG = "AddMemoryGroupFragment"
    }
}