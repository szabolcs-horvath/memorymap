package com.szabolcshorvath.memorymap

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.databinding.FragmentAddMemoryGroupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    private var startDateTime: ZonedDateTime = ZonedDateTime.now()
    private var endDateTime: ZonedDateTime = ZonedDateTime.now().plusHours(1)
    private var isAllDay = false
    
    private var listener: AddMemoryListener? = null

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        uris.let {
            val contentResolver = requireContext().contentResolver
            val newItems = it.map { uri ->
                val type = contentResolver.getType(uri)
                val mediaType = if (type != null && type.startsWith("video/")) MediaType.VIDEO else MediaType.IMAGE
                uri to mediaType
            }
            selectedMediaUris.addAll(newItems)
            binding.selectedMediaCount.text = "${selectedMediaUris.size} items selected"
            it.forEach { uri ->
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }


    interface AddMemoryListener {
        fun onPickLocation(currentLat: Double, currentLng: Double)
        fun onMemorySaved()
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

        if (savedInstanceState != null) {
            lat = savedInstanceState.getDouble("LAT")
            lng = savedInstanceState.getDouble("LNG")
        }
        
        binding.locationText.text = "Location: $lat, $lng"
        
        updateDateTimeButtons()

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

        binding.pickMediaButton.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest())
        }

        binding.clearButton.setOnClickListener {
            clearFields()
        }

        binding.saveButton.setOnClickListener {
            saveMemoryGroup()
        }
    }

    fun clearFields() {
        lat = 0.0
        lng = 0.0
        isAllDay = false
        startDateTime = ZonedDateTime.now()
        endDateTime = ZonedDateTime.now().plusHours(1)
        updateDateTimeButtons()
        selectedMediaUris.clear()

        binding.titleInput.text.clear()
        binding.locationText.text = "Location: $lat, $lng"
        binding.selectedMediaCount.text = "0 items selected"
        binding.allDayCheckbox.isChecked = false
    }
    
    fun updateLocation(newLat: Double, newLng: Double) {
        lat = newLat
        lng = newLng
        if (_binding != null) {
            binding.locationText.text = "Location: $lat, $lng"
        }
    }
    
    private fun updateDateTimeButtons() {
        binding.startDateButton.text = startDateTime.format(dateFormatter)
        binding.endDateButton.text = endDateTime.format(dateFormatter)

        if (isAllDay) {
            binding.startTimeButton.visibility = View.GONE
            binding.endTimeButton.visibility = View.GONE
            binding.startSeparator.visibility = View.GONE
            binding.endSeparator.visibility = View.GONE
        } else {
            binding.startTimeButton.visibility = View.VISIBLE
            binding.endTimeButton.visibility = View.VISIBLE
            binding.startSeparator.visibility = View.VISIBLE
            binding.endSeparator.visibility = View.VISIBLE
            binding.startTimeButton.text = startDateTime.format(timeFormatter)
            binding.endTimeButton.text = endDateTime.format(timeFormatter)
        }
    }
    
    private fun pickDate(isStart: Boolean) {
        val current = if (isStart) startDateTime else endDateTime
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newDate = LocalDate.of(year, month + 1, dayOfMonth)
                if (isStart) {
                    startDateTime = newDate.atTime(startDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
                    if (endDateTime.isBefore(startDateTime)) {
                        endDateTime = startDateTime.plusHours(1)
                    }
                } else {
                    endDateTime = newDate.atTime(endDateTime.toLocalTime()).atZone(ZoneId.systemDefault())
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

    private fun saveMemoryGroup() {
        val title = binding.titleInput.text.toString()
        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }
        
        val finalStart = if (isAllDay) startDateTime.toLocalDate().atStartOfDay(ZoneId.systemDefault()) else startDateTime
        val finalEnd = if (isAllDay) endDateTime.toLocalDate().atTime(23, 59, 59).atZone(ZoneId.systemDefault()) else endDateTime

        val context = requireContext()
        val contentResolver = context.contentResolver
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)!!

        lifecycleScope.launch(Dispatchers.IO) {
            val db = StoryMapDatabase.getDatabase(context.applicationContext)
            val group = MemoryGroup(
                title = title,
                latitude = lat,
                longitude = lng,
                startDate = finalStart,
                endDate = finalEnd,
                isAllDay = isAllDay
            )
            val groupId = db.memoryGroupDao().insertGroup(group)
            
            val mediaItems = selectedMediaUris.map { (uri, type) ->
                var name = "unknown"
                var size = 0L
                var date = 0L

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1) {
                                name = cursor.getString(nameIdx)
                            } else {
                                throw Exception("Name not found for $uri")
                            }
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIdx != -1) {
                                size = cursor.getLong(sizeIdx)
                            } else {
                                throw Exception("Size not found for $uri")
                            }
                            val dateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                            if (dateIdx != -1) {
                                date = cursor.getLong(dateIdx)
                            } else {
                                throw Exception("Date taken not found for $uri")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                MediaItem(
                    groupId = groupId.toInt(),
                    uri = uri.toString(),
                    type = type,
                    originalFileName = name,
                    fileSize = size,
                    dateTaken = date,
                    deviceId = deviceId
                )
            }
            db.memoryGroupDao().insertMediaItems(mediaItems)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show()
                listener?.onMemorySaved()
                clearFields() 
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble("LAT", lat)
        outState.putDouble("LNG", lng)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}