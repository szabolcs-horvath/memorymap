package com.szabolcshorvath.memorymap.adapter

import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter.FragmentEditState
import com.szabolcshorvath.memorymap.util.findDatePicker
import com.szabolcshorvath.memorymap.util.findTimePicker
import com.szabolcshorvath.memorymap.util.setOnPicked
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The date and time pickers of the fragment editor ([MemoryFragmentEditAdapter]), which edit [fragments] in place. */
class FragmentEditPickers(
    private val fragments: MutableList<FragmentEditState>,
    private val getChildFragmentManager: () -> FragmentManager,
    private val updateFragmentsUI: () -> Unit
) {
    fun pickDateRange(index: Int) {
        if (index == RecyclerView.NO_POSITION) return
        // Read from backing collection (fragments[index]) rather than getItem(index) to ensure
        // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
        val item = fragments[index]
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
        val start = item.startDate ?: ZonedDateTime.now()
        val end = item.endDate ?: ZonedDateTime.now().plusHours(1)

        // MaterialDatePicker expects UTC midnight millis
        val startMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endMillis = end.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val selection = androidx.core.util.Pair(startMillis, endMillis)

        builder.setSelection(selection)
        val picker = builder.build()
        val localId = item.localId
        picker.setOnPicked { applyPickedDateRange(localId, it) }
        picker.show(getChildFragmentManager(), pickerTag(DATE_RANGE_PICKER, localId))
    }

    fun pickDate(index: Int, isStart: Boolean) {
        if (index == RecyclerView.NO_POSITION) return
        // Read from backing collection (fragments[index]) rather than getItem(index) to ensure
        // consistency with synchronous mutations and avoid potential ListAdapter snapshot lag.
        val item = fragments[index]
        val current = (if (isStart) item.startDate else item.endDate) ?: ZonedDateTime.now()

        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStart) "Select Start Date" else "Select End Date")
            .setSelection(current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())

        val picker = builder.build()
        val localId = item.localId
        picker.setOnPicked { applyPickedDate(localId, it, isStart) }
        picker.show(getChildFragmentManager(), pickerTag(if (isStart) START_DATE_PICKER else END_DATE_PICKER, localId))
    }

    fun pickTime(index: Int, isStart: Boolean) {
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

        val localId = item.localId
        picker.setOnPicked { applyPickedTime(localId, it, isStart) }
        picker.show(getChildFragmentManager(), pickerTag(if (isStart) START_TIME_PICKER else END_TIME_PICKER, localId))
    }

    /** Attaches the listeners of fragment pickers that are still open after the screen's view was recreated. */
    fun reattachListeners() {
        val manager = getChildFragmentManager()
        for (localId in fragments.map { it.localId }) {
            manager.findDatePicker<androidx.core.util.Pair<Long, Long>>(pickerTag(DATE_RANGE_PICKER, localId))
                ?.setOnPicked { applyPickedDateRange(localId, it) }
            manager.findDatePicker<Long>(pickerTag(START_DATE_PICKER, localId))
                ?.setOnPicked { applyPickedDate(localId, it, isStart = true) }
            manager.findDatePicker<Long>(pickerTag(END_DATE_PICKER, localId))
                ?.setOnPicked { applyPickedDate(localId, it, isStart = false) }
            manager.findTimePicker(pickerTag(START_TIME_PICKER, localId))
                ?.setOnPicked { applyPickedTime(localId, it, isStart = true) }
            manager.findTimePicker(pickerTag(END_TIME_PICKER, localId))
                ?.setOnPicked { applyPickedTime(localId, it, isStart = false) }
        }
    }

    private fun applyPickedDateRange(localId: String, range: androidx.core.util.Pair<Long, Long>) {
        val first = range.first ?: return
        val second = range.second ?: return
        // MaterialDatePicker returns UTC midnight millis.
        // We convert to LocalDate using UTC to avoid off-by-one errors in some timezones.
        val newStartDate = Instant.ofEpochMilli(first).atZone(ZoneOffset.UTC).toLocalDate()
        val newEndDate = Instant.ofEpochMilli(second).atZone(ZoneOffset.UTC).toLocalDate()
        updateFragment(localId) {
            it.copy(
                startDate = newStartDate.atStartOfDay(ZoneId.systemDefault()),
                endDate = newEndDate.atStartOfDay(ZoneId.systemDefault())
            )
        }
    }

    private fun applyPickedDate(localId: String, selection: Long, isStart: Boolean) {
        // MaterialDatePicker returns UTC midnight millis.
        // We convert to LocalDate using UTC to avoid off-by-one errors in some timezones.
        val newDate = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate()
        updateFragment(localId) { item ->
            val current = (if (isStart) item.startDate else item.endDate) ?: ZonedDateTime.now()
            withPickedDateTime(item, newDate.atTime(current.toLocalTime()).atZone(ZoneId.systemDefault()), isStart)
        }
    }

    private fun applyPickedTime(localId: String, newTime: LocalTime, isStart: Boolean) {
        updateFragment(localId) { item ->
            val current = (if (isStart) item.startDate else item.endDate) ?: ZonedDateTime.now()
            withPickedDateTime(item, current.with(newTime), isStart)
        }
    }

    /** Sets the start or the end of [item] to [picked], and moves the other end if it would come before or after it. */
    private fun withPickedDateTime(item: FragmentEditState, picked: ZonedDateTime, isStart: Boolean): FragmentEditState {
        if (isStart) {
            var newEnd = item.endDate
            if (newEnd != null && newEnd.isBefore(picked)) newEnd = picked.plusHours(1)
            return item.copy(startDate = picked, endDate = newEnd)
        }
        var newStart = item.startDate
        if (newStart != null && picked.isBefore(newStart)) newStart = picked.minusHours(1)
        return item.copy(startDate = newStart, endDate = picked)
    }

    /** Applies [change] to the fragment with [localId]. The list can change while a picker is open, so never by position. */
    private fun updateFragment(localId: String, change: (FragmentEditState) -> FragmentEditState) {
        val index = fragments.indexOfFirst { it.localId == localId }
        if (index == -1) return
        fragments[index] = change(fragments[index])
        updateFragmentsUI()
    }

    /** The tag of a fragment picker: its kind and the fragment's [FragmentEditState.localId]. */
    private fun pickerTag(kind: String, localId: String) = "FRAGMENT_${kind}_PICKER:$localId"

    private companion object {
        const val DATE_RANGE_PICKER = "DATE_RANGE"
        const val START_DATE_PICKER = "START_DATE"
        const val END_DATE_PICKER = "END_DATE"
        const val START_TIME_PICKER = "START_TIME"
        const val END_TIME_PICKER = "END_TIME"
    }
}
