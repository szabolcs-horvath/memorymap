package com.szabolcshorvath.memorymap.fragment

import androidx.annotation.StringRes
import java.time.ZonedDateTime

/** What the Add form shows: a new memory or one being edited ([editing]). */
data class AddFormUiState(
    /** Bumped when the form is cleared or filled for editing: the text fields then show [title] and [description] anew. */
    val formVersion: Int,
    val editing: Boolean,
    val title: String,
    val description: String,
    val place: FormPlace?,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val isAllDay: Boolean,
    val color: Hsv,
    val colorExpanded: Boolean,
    val fragments: List<FragmentEditState>,
    val media: List<AddMemoryGroupFragment.SelectedMedia>,
    /** This installation's id: media saved by another one can't be shown. Null until it is read. */
    val currentDeviceId: String?,
    val isSaving: Boolean,
    /** Whether the fields a save needs say what they lack: after a save that lacked something. */
    val showErrors: Boolean,
    /** Bumped by each save that lacks something: the screen then scrolls to the first field that needs it. */
    val errorScrollRequest: Int,
    @StringRes val message: Int?,
)

/** A place picked on the map, with its name and address when the picker had them. */
data class FormPlace(val lat: Double, val lng: Double, val name: String?, val address: String?) {
    companion object {
        fun of(lat: Double?, lng: Double?, name: String?, address: String?): FormPlace? =
            if (lat != null && lng != null) FormPlace(lat, lng, name, address) else null
    }
}

/** The part of the form a date, time or colour belongs to: the memory itself or one of its fragments. */
sealed interface FormPart {
    data object Memory : FormPart

    data class Fragment(val localId: String) : FormPart
}
