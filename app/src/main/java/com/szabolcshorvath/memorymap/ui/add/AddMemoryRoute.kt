package com.szabolcshorvath.memorymap.ui.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragmentViewModel
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragmentViewModel.SaveResult
import com.szabolcshorvath.memorymap.fragment.FormPart
import com.szabolcshorvath.memorymap.fragment.FormPlace
import com.szabolcshorvath.memorymap.fragment.Hsv
import com.szabolcshorvath.memorymap.fragment.SelectedMedia
import com.szabolcshorvath.memorymap.ui.common.ConfirmDialog
import java.time.LocalDate
import java.time.LocalTime

/**
 * The Add form with its ViewModel: the form, the photo picker, the questions before the form is cleared or an edit
 * request replaces unsaved changes, and what follows a save. [onPickLocation] opens the location picker on the place
 * the memory or fragment has; [onSaved] opens a memory saved while the form is shown.
 */
@Composable
fun AddMemoryRoute(
    viewModel: AddMemoryGroupFragmentViewModel,
    common: CommonViewModel,
    onPickLocation: (FormPlace?) -> Unit,
    onSaved: (memoryId: Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by common.allPresets.collectAsStateWithLifecycle(emptyList())
    val pickLocation by rememberUpdatedState(onPickLocation)
    val saved by rememberUpdatedState(onSaved)
    var askingToClear by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(AppServices.mediaPicker, viewModel::mediaPicked)
    val actions = remember(viewModel, pick) {
        formActions(
            viewModel,
            askToClear = { askingToClear = true },
            openLocationPicker = { pickLocation(it) },
            openMediaPicker = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        )
    }

    LaunchedEffect(viewModel) { viewModel.onShown() }
    // Only while shown: a hidden tab keeps its view, and a save error must not show over another screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.saveResult.collect { result ->
                when (result) {
                    // The view model has already cleared the form.
                    is SaveResult.Success -> if (result.openMemory) saved(result.groupId)
                    SaveResult.Error -> viewModel.showMessage(R.string.memory_save_failed)
                }
            }
        }
    }

    AddMemoryContent(state, presets, actions)
    EditRequestQuestion(viewModel)
    if (askingToClear) {
        val isEditing = viewModel.editingMemoryId != null
        ConfirmDialog(
            title = stringResource(if (isEditing) R.string.discard_changes_title else R.string.clear_fields_title),
            message = stringResource(if (isEditing) R.string.discard_changes_message else R.string.clear_fields_message),
            confirm = stringResource(if (isEditing) R.string.discard else R.string.clear),
            cancel = stringResource(R.string.cancel),
            onConfirm = {
                askingToClear = false
                viewModel.resetForm()
            },
            onCancel = { askingToClear = false },
        )
    }
}

/**
 * Handles an edit request as soon as the form has a view, also while its tab is hidden: filling a hidden form shows
 * nothing. When it would replace unsaved changes, asks first; the question comes back after a rotation or process
 * death, and a newer request replaces it.
 */
@Composable
private fun EditRequestQuestion(viewModel: AddMemoryGroupFragmentViewModel) {
    val pendingEdit by viewModel.pendingEdit.collectAsState()
    var askingFor by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingEdit) {
        if (pendingEdit != null) viewModel.handlePendingEdit()?.let { askingFor = it }
    }
    val memoryId = askingFor ?: return
    val isEditing = viewModel.editingMemoryId != null
    ConfirmDialog(
        title = stringResource(if (isEditing) R.string.discard_edit_title else R.string.discard_draft_title),
        message = stringResource(if (isEditing) R.string.discard_edit_message else R.string.discard_draft_message),
        confirm = stringResource(R.string.discard),
        cancel = stringResource(R.string.keep_editing),
        onConfirm = {
            askingFor = null
            viewModel.confirmEdit(memoryId)
        },
        onCancel = {
            askingFor = null
            viewModel.cancelEdit(memoryId)
        },
    )
}

/** The form's actions: most go straight to [viewModel]; clearing asks first ([askToClear]). */
private fun formActions(
    viewModel: AddMemoryGroupFragmentViewModel,
    askToClear: () -> Unit,
    openLocationPicker: (FormPlace?) -> Unit,
    openMediaPicker: () -> Unit,
) = object : AddMemoryActions {
    override fun changeTitle(text: String) = viewModel.changeTitle(text)
    override fun changeDescription(text: String) = viewModel.changeDescription(text)
    override fun pickLocation(part: FormPart) = viewModel.startPickingLocation(part, openLocationPicker)
    override fun setAllDay(part: FormPart, allDay: Boolean) = viewModel.setAllDay(part, allDay)
    override fun setOwnTime(localId: String, on: Boolean) = viewModel.setOwnTime(localId, on)
    override fun setDate(part: FormPart, isStart: Boolean, date: LocalDate) = viewModel.setDate(part, isStart, date)
    override fun setTime(part: FormPart, isStart: Boolean, time: LocalTime) = viewModel.setTime(part, isStart, time)
    override fun setDateRange(part: FormPart, first: LocalDate, last: LocalDate) = viewModel.setDateRange(part, first, last)
    override fun setColor(part: FormPart, color: Hsv) = viewModel.setColor(part, color)
    override fun toggleColor(part: FormPart) = viewModel.toggleColor(part)
    override fun addFragment() = viewModel.addFragment()
    override fun removeFragment(localId: String) = viewModel.removeFragment(localId)
    override fun pickMedia() = openMediaPicker()
    override fun removeMedia(item: SelectedMedia) = viewModel.removeMedia(item)
    override fun clear() = askToClear()
    override fun save() = viewModel.requestSave()
    override fun onMessageShown() = viewModel.onMessageShown()
}
