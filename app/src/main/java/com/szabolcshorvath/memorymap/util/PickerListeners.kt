package com.szabolcshorvath.memorymap.util

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
    markLive()
}

/** Replaces the picker's listeners with [action], so attaching again never applies a pick twice. */
fun MaterialTimePicker.setOnPicked(action: (LocalTime) -> Unit) {
    clearOnPositiveButtonClickListeners()
    addOnPositiveButtonClickListener { action(LocalTime.of(hour, minute)) }
}

/**
 * Attaches [action] to the date picker shown under [tag], if it is still open. [S] must match the type it was built
 * with. A date picker that Android brought back after process death ignores the dates tapped in it: the picker and its
 * calendar each restore their own copy of the selection, taps change the calendar's, and OK reads the picker's. So
 * such a picker is replaced by a new one with the same options and selection. One kept through a rotation shares a
 * single copy and works, so it stays.
 */
fun <S> FragmentManager.reattachDatePicker(tag: String, action: (S) -> Unit) {
    val picker = findDatePicker<S>(tag) ?: return
    val working = if (ViewModelProvider(picker)[PickerSession::class.java].isLive) picker else picker.reopened()
    working.setOnPicked(action)
}

/** The time picker shown under [tag], if it is still open. */
fun FragmentManager.findTimePicker(tag: String): MaterialTimePicker? = findFragmentByTag(tag) as? MaterialTimePicker

@Suppress("UNCHECKED_CAST")
private fun <S> FragmentManager.findDatePicker(tag: String): MaterialDatePicker<S>? =
    findFragmentByTag(tag) as? MaterialDatePicker<S>

/**
 * Closes this restored picker and shows a new one in its place, built from the same options (its arguments). Both happen
 * at once, while the screen's view is being created, so the old picker never shows and nobody can tap it as it closes.
 */
private fun <S> MaterialDatePicker<S>.reopened(): MaterialDatePicker<S> {
    val manager = parentFragmentManager
    val replacement = MaterialDatePicker<S>().also { it.arguments = arguments }
    dismissNow()
    replacement.showNow(manager, tag)
    return replacement
}

/**
 * Notes in the picker's own ViewModel, once it is created, that it was shown or checked in this session. Its ViewModel
 * survives a rotation but not process death, so a picker restored without the note is one that lost its state.
 */
private fun Fragment.markLive() {
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            ViewModelProvider(this@markLive)[PickerSession::class.java].isLive = true
            lifecycle.removeObserver(this)
        }
    })
}

/** Whether a picker was shown in this session (see [markLive]). Public: the default ViewModel factory creates it. */
class PickerSession : ViewModel() {
    var isLive = false
}
