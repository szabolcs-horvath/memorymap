package com.szabolcshorvath.memorymap.util

import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import java.time.LocalTime

// Material date and time pickers are restored after a configuration change, but the listeners added to them
// are not. Screens show each picker under a tag that says what it is for, attach its listener when showing it,
// and attach it again in onViewCreated to the picker found by that tag.

/** Replaces the picker's listeners with [action], so attaching again never applies a pick twice. */
fun <S> MaterialDatePicker<S>.setOnPicked(action: (S) -> Unit) {
    clearOnPositiveButtonClickListeners()
    addOnPositiveButtonClickListener { action(it) }
}

/** Replaces the picker's listeners with [action], so attaching again never applies a pick twice. */
fun MaterialTimePicker.setOnPicked(action: (LocalTime) -> Unit) {
    clearOnPositiveButtonClickListeners()
    addOnPositiveButtonClickListener { action(LocalTime.of(hour, minute)) }
}

/** The date picker shown under [tag], if it is still open. [S] must match the type it was built with. */
@Suppress("UNCHECKED_CAST")
fun <S> FragmentManager.findDatePicker(tag: String): MaterialDatePicker<S>? =
    findFragmentByTag(tag) as? MaterialDatePicker<S>

/** The time picker shown under [tag], if it is still open. */
fun FragmentManager.findTimePicker(tag: String): MaterialTimePicker? = findFragmentByTag(tag) as? MaterialTimePicker
