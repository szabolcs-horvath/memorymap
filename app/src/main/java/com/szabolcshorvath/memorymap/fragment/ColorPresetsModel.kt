package com.szabolcshorvath.memorymap.fragment

import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.PresetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A colour as the sliders set it: hue 0 to 360, saturation and brightness 0 to 1. */
data class Hsv(val hue: Float, val saturation: Float, val brightness: Float)

data class ColorPresetsUiState(
    /** The presets in their order, the selected one with the sliders' colour while it has unsaved changes. */
    val presets: List<HSVPreset> = emptyList(),
    /** Whether the editor (the sliders and buttons) is open. */
    val expanded: Boolean = false,
    /** The sliders' colour: the selected preset's, or the one "Add current as new preset" adds. */
    val color: Hsv = DEFAULT_COLOR,
    val selectedPresetId: Int? = null,
    /** Whether the selected preset has unsaved changes, which Save stores and Undo drops. */
    val changed: Boolean = false,
    /** A preset just added, to scroll to once ([ColorPresetsModel.onNewPresetShown]). */
    val newPresetId: Int? = null,
) {
    companion object {
        /** The sliders' colour before a preset is selected. */
        val DEFAULT_COLOR = Hsv(0f, 1f, 1f)
    }
}

/**
 * The colour presets in Settings: selecting one to edit it with the sliders, saving or undoing the change, adding the
 * sliders' colour as a new preset, deleting and reordering them. [onMessage] shows a short message (a string resource).
 */
class ColorPresetsModel(
    private val scope: CoroutineScope,
    private val presets: PresetRepository,
    private val onMessage: suspend (text: Int) -> Unit,
) {
    private val mutableState = MutableStateFlow(ColorPresetsUiState())
    val state: StateFlow<ColorPresetsUiState> = mutableState.asStateFlow()

    /** The presets as stored, and the stored version of the selected one. */
    private var savedPresets: List<HSVPreset> = emptyList()
    private var selectedPreset: HSVPreset? = null

    /** A preset being added: selected as soon as it is stored. */
    private var pendingNewPresetId: Int? = null
    private var saveOrderJob: Job? = null

    init {
        scope.launch { presets.allPresets.collect(::onPresetsStored) }
    }

    private fun onPresetsStored(stored: List<HSVPreset>) {
        savedPresets = stored
        val selectedId = selectedPreset?.id
        val newId = pendingNewPresetId
        when {
            newId != null && stored.any { it.id == newId } -> selectNew(newId)

            selectedId != null && stored.none { it.id == selectedId } -> selectedPreset = null

            // A saved change comes back from the database: it is the selected preset as stored now.
            selectedId != null -> selectedPreset = stored.first { it.id == selectedId }
        }
        show()
    }

    /** Selects the preset just added, and scrolls to it. */
    private fun selectNew(id: Int) {
        pendingNewPresetId = null
        select(savedPresets.first { it.id == id })
        mutableState.update { it.copy(newPresetId = id) }
    }

    /** Shows the presets: the selected one with the sliders' colour, so its swatch follows them. */
    private fun show() {
        mutableState.update { s ->
            val selected = selectedPreset
            s.copy(
                presets = savedPresets.map { if (it.id == selected?.id) it.withColor(s.color) else it },
                selectedPresetId = selected?.id,
                changed = selected != null && !selected.hasColor(s.color.hue, s.color.saturation, s.color.brightness),
            )
        }
    }

    private fun select(preset: HSVPreset) {
        selectedPreset = preset
        mutableState.update { it.copy(color = Hsv(preset.hue, preset.saturation, preset.brightness), expanded = true) }
    }

    fun toggle() = mutableState.update { it.copy(expanded = !it.expanded) }

    /** Selects preset [id], or selects it again, which drops its unsaved changes. Opens the editor. */
    fun select(id: Int) {
        select(savedPresets.firstOrNull { it.id == id } ?: return)
        show()
    }

    fun setColor(color: Hsv) {
        mutableState.update { it.copy(color = color) }
        show()
    }

    fun undo() {
        select(selectedPreset ?: return)
        show()
    }

    fun save() {
        val preset = selectedPreset?.withColor(state.value.color) ?: return
        scope.launch {
            presets.save(preset)
            selectedPreset = preset
            show()
            onMessage(R.string.preset_saved)
        }
    }

    /** Adds the sliders' colour as a new preset, at the end, and selects it once stored. */
    fun add() {
        val color = state.value.color
        val nextOrder = (savedPresets.maxOfOrNull { it.order ?: 0 } ?: -1) + 1
        scope.launch {
            val preset = HSVPreset(hue = color.hue, saturation = color.saturation, brightness = color.brightness, order = nextOrder)
            val id = presets.add(preset) ?: return@launch
            pendingNewPresetId = id
            // The list may already hold it, if the database answered first.
            if (savedPresets.any { it.id == id }) {
                selectNew(id)
                show()
            }
        }
    }

    fun onNewPresetShown() = mutableState.update { it.copy(newPresetId = null) }

    fun delete(id: Int) {
        val preset = savedPresets.firstOrNull { it.id == id } ?: return
        scope.launch {
            presets.delete(preset)
            if (selectedPreset?.id == id) {
                selectedPreset = null
                show()
            }
            onMessage(R.string.preset_deleted)
        }
    }

    /** Stores the presets' order as the user dragged them: [ids] from left to right. */
    fun reorder(ids: List<Int>) {
        // Only a new order of the same presets: one deleted or added meanwhile would otherwise be lost or put back.
        if (ids.sorted() != savedPresets.map { it.id }.sorted()) return
        val byId = savedPresets.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }.mapIndexed { index, preset -> preset.copy(order = index) }
        // Shown at once, so the swatches don't jump back until the database answers.
        savedPresets = ordered
        show()
        saveOrderJob?.cancel()
        saveOrderJob = scope.launch { presets.saveOrder(ordered) }
    }
}

private fun HSVPreset.withColor(color: Hsv) = copy(hue = color.hue, saturation = color.saturation, brightness = color.brightness)
