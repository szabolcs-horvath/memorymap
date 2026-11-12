package com.szabolcshorvath.memorymap.ui.add

import android.content.res.Configuration
import android.os.LocaleList
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.add.PickerRequest.Kind.DATE_RANGE
import com.szabolcshorvath.memorymap.ui.add.PickerRequest.Kind.END_DATE
import com.szabolcshorvath.memorymap.ui.add.PickerRequest.Kind.END_TIME
import com.szabolcshorvath.memorymap.ui.add.PickerRequest.Kind.START_DATE
import com.szabolcshorvath.memorymap.ui.add.PickerRequest.Kind.START_TIME
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import com.szabolcshorvath.memorymap.util.firstDayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

// The UI tests find the memory's date buttons by these names (screens/AddScreen); a fragment's have the same names.
private const val TAG_DATE_RANGE = "bt_dateRange"
private const val TAG_START_DATE = "bt_startDate"
private const val TAG_START_TIME = "bt_startTime"
private const val TAG_END_DATE = "bt_endDate"
private const val TAG_END_TIME = "bt_endTime"
private const val TAG_ALL_DAY = "switch_allDay"

// The widths of a date row's label and buttons, so the rows' buttons line up.
private const val LABEL_WEIGHT = 0.8f
private const val DATE_WEIGHT = 1.3f
private const val TIME_WEIGHT = 1f

/** A picker the form shows over itself: what it picks ([kind]) and for which part of the form. */
internal data class PickerRequest(val part: FormPart, val kind: Kind) {
    enum class Kind { START_DATE, END_DATE, START_TIME, END_TIME, DATE_RANGE }

    companion object {
        private const val MEMORY = "memory"

        /** Kept as text, so an open picker comes back after a rotation and after process death. */
        val Saver = Saver<PickerRequest?, String>(
            save = { request ->
                request?.let {
                    val part = when (val part = it.part) {
                        FormPart.Memory -> MEMORY
                        is FormPart.Fragment -> part.localId
                    }
                    "${it.kind.name}:$part"
                }
            },
            restore = { saved ->
                val kind = Kind.valueOf(saved.substringBefore(':'))
                val part = saved.substringAfter(':')
                PickerRequest(if (part == MEMORY) FormPart.Memory else FormPart.Fragment(part), kind)
            },
        )
    }
}

/**
 * The all-day switch and the start and end of [part], each with a button for its day and one for its time. All day,
 * one button picks the range of days instead.
 */
@Composable
@Suppress("LongParameterList")
internal fun DateRows(
    part: FormPart,
    start: ZonedDateTime,
    end: ZonedDateTime,
    isAllDay: Boolean,
    actions: AddMemoryActions,
    onPick: (PickerRequest) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(vertical = 4.dp)) {
        SwitchRow(R.string.all_day, isAllDay, Modifier.testTag(TAG_ALL_DAY)) { actions.setAllDay(part, it) }
        if (isAllDay) {
            val first = DateTimeFormatterUtil.date(start)
            val last = DateTimeFormatterUtil.date(end)
            DateRow(R.string.dates_label) {
                DateButton(
                    modifier = Modifier.weight(DATE_WEIGHT + TIME_WEIGHT),
                    text = if (first == last) first else stringResource(R.string.date_range, first, last),
                    tag = TAG_DATE_RANGE,
                ) { onPick(PickerRequest(part, DATE_RANGE)) }
            }
        } else {
            DateRow(R.string.start_label) {
                DateButton(Modifier.weight(DATE_WEIGHT), DateTimeFormatterUtil.date(start), TAG_START_DATE) { onPick(PickerRequest(part, START_DATE)) }
                DateButton(Modifier.weight(TIME_WEIGHT), DateTimeFormatterUtil.time(context, start), TAG_START_TIME) { onPick(PickerRequest(part, START_TIME)) }
            }
            DateRow(R.string.end_label) {
                DateButton(Modifier.weight(DATE_WEIGHT), DateTimeFormatterUtil.date(end), TAG_END_DATE) { onPick(PickerRequest(part, END_DATE)) }
                DateButton(Modifier.weight(TIME_WEIGHT), DateTimeFormatterUtil.time(context, end), TAG_END_TIME) { onPick(PickerRequest(part, END_TIME)) }
            }
        }
    }
}

/** A setting turned on and off by its row or its switch, with an optional line under its name. */
@Composable
internal fun SwitchRow(@StringRes label: Int, checked: Boolean, modifier: Modifier = Modifier, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = PAGE_MARGIN, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun DateRow(@StringRes label: Int, buttons: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_MARGIN, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(label), Modifier.weight(LABEL_WEIGHT), style = MaterialTheme.typography.bodyLarge)
        buttons()
    }
}

@Composable
private fun DateButton(modifier: Modifier, text: String, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.testTag(tag)) { Text(text, maxLines = 1) }
}

/** The picker [request] asks for, showing what the form holds now; picking changes the form, and either way it closes. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FormPicker(request: PickerRequest, state: AddFormUiState, actions: AddMemoryActions, onClose: () -> Unit) {
    val (start, end) = when (val part = request.part) {
        FormPart.Memory -> state.start to state.end
        is FormPart.Fragment -> {
            val fragment = state.fragments.firstOrNull { it.localId == part.localId }
            if (fragment == null) {
                onClose() // The fragment was removed meanwhile.
                return
            }
            (fragment.startDate ?: ZonedDateTime.now()) to (fragment.endDate ?: ZonedDateTime.now().plusHours(1))
        }
    }
    when (request.kind) {
        PickerRequest.Kind.START_DATE, PickerRequest.Kind.END_DATE -> {
            val isStart = request.kind == PickerRequest.Kind.START_DATE
            DayPicker(
                title = if (isStart) R.string.select_start_date else R.string.select_end_date,
                day = (if (isStart) start else end).toLocalDate(),
                onClose = onClose,
            ) { actions.setDate(request.part, isStart, it) }
        }

        PickerRequest.Kind.START_TIME, PickerRequest.Kind.END_TIME -> {
            val isStart = request.kind == PickerRequest.Kind.START_TIME
            ClockPicker(
                title = if (isStart) R.string.select_start_time else R.string.select_end_time,
                time = (if (isStart) start else end).toLocalTime(),
                onClose = onClose,
            ) { actions.setTime(request.part, isStart, it) }
        }

        PickerRequest.Kind.DATE_RANGE -> RangePicker(start.toLocalDate(), end.toLocalDate(), onClose) { first, last ->
            actions.setDateRange(request.part, first, last)
        }
    }
}

/**
 * Gives the pickers in [content] the phone's language with the first day of the week it uses (Android's regional
 * preference, or its language's), which Material 3's date picker reads from the locale.
 */
@Composable
private fun WithPhonesWeek(content: @Composable () -> Unit) {
    val phone = LocalConfiguration.current.locales[0]
    val day = firstDayOfWeek(phone)
    val locale = Locale.Builder().setLocale(phone).setUnicodeLocaleKeyword(FIRST_DAY_KEY, day.name.take(DAY_CODE_LENGTH).lowercase(Locale.ROOT)).build()
    val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(locale)) }
    CompositionLocalProvider(LocalConfiguration provides configuration) { content() }
}

/** The Unicode locale extension for the first day of the week ("fw"), as in "en-US-u-fw-mon". */
private const val FIRST_DAY_KEY = "fw"

/** The day in that extension: the first three letters of its English name ("mon"). */
private const val DAY_CODE_LENGTH = 3

/** A range picker's headline shows each day without its year, so both fit on one line. */
private const val RANGE_DAY_SKELETON = "MMMd"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DayPicker(@StringRes title: Int, day: LocalDate, onClose: () -> Unit, onPicked: (LocalDate) -> Unit) {
    WithPhonesWeek {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = day.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = onClose,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPicked(it.toPickerDate()) }
                    onClose()
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(
                pickerState,
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                title = { PickerTitle(title) },
            )
        }
    }
}

/**
 * Material 3's dialog for picking a range of days (the form's all-day dates, the map's filter), with weeks starting on
 * the phone's first day. It opens with [first] to [last] selected, or nothing if either is null.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RangePicker(
    first: LocalDate?,
    last: LocalDate?,
    onClose: () -> Unit,
    @StringRes title: Int = R.string.select_date_range,
    onPicked: (LocalDate, LocalDate) -> Unit,
) {
    WithPhonesWeek {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = first?.toPickerMillis(),
            initialSelectedEndDateMillis = last?.toPickerMillis(),
        )
        DatePickerDialog(
            onDismissRequest = onClose,
            confirmButton = {
                TextButton(onClick = {
                    val start = pickerState.selectedStartDateMillis
                    // One day picked: a range of that day.
                    if (start != null) onPicked(start.toPickerDate(), (pickerState.selectedEndDateMillis ?: start).toPickerDate())
                    onClose()
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        ) {
            DateRangePicker(
                pickerState,
                dateFormatter = remember { DatePickerDefaults.dateFormatter(selectedDateSkeleton = RANGE_DAY_SKELETON) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { testTagsAsResourceId = true },
                title = { PickerTitle(title) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ClockPicker(@StringRes title: Int, time: LocalTime, onClose: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(time.hour, time.minute, is24Hour = DateFormat.is24HourFormat(context))
    var typing by rememberSaveable { mutableStateOf(false) }
    TimePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalTime.of(pickerState.hour, pickerState.minute))
                onClose()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(title)) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = { typing = !typing },
                displayMode = if (typing) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker,
            )
        },
    ) {
        val modifier = Modifier.semantics { testTagsAsResourceId = true }
        if (typing) TimeInput(pickerState, modifier) else TimePicker(pickerState, modifier)
    }
}

@Composable
private fun PickerTitle(@StringRes title: Int) {
    Text(stringResource(title), Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
}

/** Material 3's date pickers hold a day as the millisecond its UTC midnight starts. */
private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
