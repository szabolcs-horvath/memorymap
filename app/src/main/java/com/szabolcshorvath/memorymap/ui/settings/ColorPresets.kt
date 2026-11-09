package com.szabolcshorvath.memorymap.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.fragment.ColorPresetsUiState
import com.szabolcshorvath.memorymap.fragment.Hsv
import com.szabolcshorvath.memorymap.util.ColorUtil
import kotlin.math.abs
import kotlin.math.roundToInt

// The UI tests find the slider values by these names (screens/ColorPresetsSection), shared with the Add form.
private const val TAG_HUE_VALUE = "tv_HueValue"
private const val TAG_SATURATION_VALUE = "tv_SaturationValue"
private const val TAG_BRIGHTNESS_VALUE = "tv_BrightnessValue"

private const val MAX_HUE = 360f
private const val HUE_STEP = 5f
private const val FRACTION_STEP = 0.05f

/** Turns the right-pointing chevron down while the editor is open. */
private const val CHEVRON_OPEN = 90f
private const val DRAGGED_SCALE = 1.15f

/** Swatches are white or black on top: the check mark takes the other one. */
private const val LIGHT_COLOR = 0.5f

private val COLOR_STRIP_HEIGHT = 12.dp
private val SWATCH_SIZE = 44.dp
private val SWATCH_GAP = 4.dp
private val GROUP_GAP = 12.dp
private val BUTTON_GAP = 8.dp

/** What the user can do with the colour presets. */
interface ColorPresetActions {
    fun togglePresets()
    fun selectPreset(id: Int)
    fun setColor(color: Hsv)
    fun addPreset()
    fun reorderPresets(ids: List<Int>)
    fun onNewPresetShown()

    /** Deletes the selected preset, after asking. */
    fun deletePreset()
    fun undoPresetChange()
    fun savePreset()
}

/**
 * The marker colour presets: the swatches (tap one to edit it, long-press and drag to reorder), and, opened, the
 * sliders that edit the selected one, with Add, Delete, Undo and Save. A strip on top shows the sliders' colour.
 */
internal fun LazyListScope.colorPresetsSection(state: ColorPresetsUiState, actions: ColorPresetActions, enabled: Boolean) {
    // The map's marker colours: a group of their own under the map's settings.
    item(key = "presets_gap") { Spacer(Modifier.height(GROUP_GAP)) }
    item(key = "presets") {
        // One card, which holds more than a list item's slots: the editor opens inside it, under the swatches it edits.
        Surface(
            modifier = Modifier.segment(0).fillMaxWidth(),
            shape = ListItemDefaults.segmentedShapes(0, 1).shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(COLOR_STRIP_HEIGHT)
                        .background(state.color.toColor())
                )
                PresetsHeader(state.expanded, actions::togglePresets)
                Swatches(state, actions)
                AnimatedVisibility(state.expanded) {
                    Column {
                        HorizontalDivider(Modifier.padding(horizontal = ITEM_PADDING))
                        PresetEditor(state, actions, enabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetsHeader(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) CHEVRON_OPEN else 0f, label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = ITEM_PADDING, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.color_presets),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A swatch being dragged to another place: it follows the finger, and passes a neighbour once it covers more than half
 * of it. The row shows [order] meanwhile; [pitch] is a swatch's width with its gap, in pixels.
 */
private class SwatchDrag(private val pitch: Float) {
    var draggedId by mutableStateOf<Int?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var order by mutableStateOf(emptyList<HSVPreset>())
        private set

    fun start(presets: List<HSVPreset>, id: Int) {
        order = presets
        draggedId = id
        offset = 0f
    }

    fun drag(by: Float) {
        offset += by
        var from = order.indexOfFirst { it.id == draggedId }
        while (abs(offset) > pitch / 2) {
            val to = if (offset > 0) from + 1 else from - 1
            if (to !in order.indices) break
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            offset -= if (offset > 0) pitch else -pitch
            from = to
        }
    }

    /**
     * Ends the drag and returns the presets' ids in their new order, or null if it didn't change. Also called when no drag
     * runs: a swatch that goes away (its preset deleted) cancels its gesture.
     */
    fun end(presets: List<HSVPreset>): List<Int>? {
        if (draggedId == null) return null
        val ids = order.map { it.id }
        draggedId = null
        offset = 0f
        return ids.takeIf { it != presets.map { preset -> preset.id } }
    }
}

/**
 * The presets' swatches in a row that scrolls sideways. Long-pressed, a swatch follows the finger and the others make
 * room; letting go stores the new order. A preset just added is scrolled into view.
 */
@Composable
private fun Swatches(state: ColorPresetsUiState, actions: ColorPresetActions) {
    val scroll = rememberScrollState()
    val pitch = with(LocalDensity.current) { (SWATCH_SIZE + SWATCH_GAP).toPx() }
    val drag = remember(pitch) { SwatchDrag(pitch) }
    // The drag's callbacks outlive a composition: they read the presets as they are when called.
    val presets by rememberUpdatedState(state.presets)
    val currentActions by rememberUpdatedState(actions)

    LaunchedEffect(state.newPresetId, state.presets) {
        val index = state.presets.indexOfFirst { it.id == state.newPresetId }
        if (index < 0) return@LaunchedEffect
        withFrameNanos { } // Laid out with the new swatch first.
        val end = ((index + 1) * pitch).roundToInt() - scroll.viewportSize
        scroll.animateScrollTo(end.coerceAtLeast(0))
        actions.onNewPresetShown()
    }

    fun endDrag() {
        drag.end(presets)?.let(currentActions::reorderPresets)
    }

    Row(
        Modifier
            .horizontalScroll(scroll)
            .padding(horizontal = ITEM_PADDING - SWATCH_GAP)
            .padding(top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
    ) {
        (if (drag.draggedId == null) state.presets else drag.order).forEach { preset ->
            key(preset.id) {
                val dragged = preset.id == drag.draggedId
                Swatch(
                    preset = preset,
                    selected = preset.id == state.selectedPresetId,
                    onClick = { actions.selectPreset(preset.id) },
                    modifier = Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (dragged) drag.offset else 0f
                            scaleX = if (dragged) DRAGGED_SCALE else 1f
                            scaleY = scaleX
                        }
                        .pointerInput(preset.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { drag.start(presets, preset.id) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag.drag(amount.x)
                                },
                                onDragEnd = ::endDrag,
                                onDragCancel = ::endDrag,
                            )
                        },
                )
            }
        }
    }
}

/**
 * A preset's colour as a circle. Selected, it gets a ring in the page's text colour and a check mark, so the selection
 * isn't told by an outline alone; screen readers say "selected" and the colour's numbers.
 */
@Composable
private fun Swatch(preset: HSVPreset, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = Hsv(preset.hue, preset.saturation, preset.brightness).toColor()
    val description = stringResource(
        R.string.color_preset_description,
        preset.hue.toInt(),
        preset.saturation,
        preset.brightness,
    )
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SWATCH_SIZE - 4.dp)
                .then(if (selected) Modifier.border(2.dp, colors.onSurface, CircleShape) else Modifier)
                .padding(4.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, colors.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (color.luminance() > LIGHT_COLOR) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun PresetEditor(state: ColorPresetsUiState, actions: ColorPresetActions, enabled: Boolean) {
    val color = state.color
    // Followed as the phone's language changes.
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.padding(horizontal = ITEM_PADDING, vertical = 12.dp)) {
        OutlinedButton(onClick = actions::addPreset, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.add_preset))
        }
        Spacer(Modifier.height(12.dp))
        ColorSlider(
            label = R.string.color_slider_hue,
            valueText = String.format(locale, "%d", color.hue.toInt()),
            valueTag = TAG_HUE_VALUE,
            value = color.hue,
            range = 0f..MAX_HUE,
            step = HUE_STEP,
            thumbColor = color.toColor(),
            enabled = enabled,
        ) { actions.setColor(color.copy(hue = it)) }
        ColorSlider(
            label = R.string.color_slider_saturation,
            valueText = String.format(locale, "%.2f", color.saturation),
            valueTag = TAG_SATURATION_VALUE,
            value = color.saturation,
            range = 0f..1f,
            step = FRACTION_STEP,
            thumbColor = color.toColor(),
            enabled = enabled,
        ) { actions.setColor(color.copy(saturation = it)) }
        ColorSlider(
            label = R.string.color_slider_brightness,
            valueText = String.format(locale, "%.2f", color.brightness),
            valueTag = TAG_BRIGHTNESS_VALUE,
            value = color.brightness,
            range = 0f..1f,
            step = FRACTION_STEP,
            thumbColor = color.toColor(),
            enabled = enabled,
        ) { actions.setColor(color.copy(brightness = it)) }
        Spacer(Modifier.height(8.dp))
        PresetButtons(state, actions, enabled)
    }
}

/**
 * A slider with its name and value above it. It moves in steps of [step] without drawing a tick for each (the hue has
 * 72), and its thumb shows the colour being edited.
 */
@Composable
@Suppress("LongParameterList")
private fun ColorSlider(
    @StringRes label: Int,
    valueText: String,
    valueTag: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    thumbColor: Color,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val name = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(
            valueText,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .testTag(valueTag),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    fun snap(raw: Float) = ((raw / step).roundToInt() * step).coerceIn(range)
    // One accessibility node with the slider's name, value and range, in place of the slider's own: Material3 1.5's
    // slider keeps its node apart from any name set on it or around it, so a screen reader read it without its name.
    Box(
        Modifier.clearAndSetSemantics {
            contentDescription = name
            stateDescription = valueText
            progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps = ((range.endInclusive - range.start) / step).roundToInt() - 1)
            setProgress { target ->
                onChange(snap(target))
                true
            }
            if (!enabled) disabled()
        }
    ) {
        val sliderState = remember(range) { SliderState(value, trackRange = range) }
        sliderState.value = value
        val colors = SliderDefaults.colors(thumbColor = thumbColor)
        Slider(
            state = sliderState,
            onValueChange = { onChange(snap(it)) },
            enabled = enabled,
            colors = colors,
            // No stop indicator: the track's end needs no dot when every value along it can be picked.
            track = { SliderDefaults.Track(it, enabled = enabled, colors = colors, drawStopIndicator = null) },
        )
    }
}

/**
 * Delete, Undo and Save for the selected preset, as a Material 3 Expressive button group: a pressed button grows a
 * little, and on a narrow screen the buttons that don't fit move into a menu.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun PresetButtons(state: ColorPresetsUiState, actions: ColorPresetActions, enabled: Boolean) {
    val changed = enabled && state.changed
    val delete = stringResource(R.string.delete_preset)
    val undo = stringResource(R.string.undo)
    val save = stringResource(R.string.save)
    val deleteColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ButtonGroup(
        overflowIndicator = { menu ->
            IconButton(onClick = { if (menu.isShowing) menu.dismiss() else menu.show() }) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_actions))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP, Alignment.End),
    ) {
        if (state.selectedPresetId != null) {
            button(delete, enabled, actions::deletePreset) { interaction, modifier ->
                TextButton(actions::deletePreset, modifier, enabled, colors = deleteColors, interactionSource = interaction) {
                    Text(delete)
                }
            }
        }
        button(undo, changed, actions::undoPresetChange) { interaction, modifier ->
            FilledTonalButton(actions::undoPresetChange, modifier, changed, interactionSource = interaction) { Text(undo) }
        }
        button(save, changed, actions::savePreset) { interaction, modifier ->
            Button(actions::savePreset, modifier, changed, interactionSource = interaction) { Text(save) }
        }
    }
}

/**
 * A button of a button group: [content] draws it with the interaction source and modifier that let it grow when
 * pressed; in the overflow menu it is an item [label] that calls [onClick].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.button(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable (MutableInteractionSource, Modifier) -> Unit,
) = customItem(
    buttonGroupContent = {
        val interaction = remember { MutableInteractionSource() }
        content(interaction, Modifier.animateWidth(interaction))
    },
    menuContent = { menu ->
        DropdownMenuItem(
            text = { Text(label) },
            enabled = enabled,
            onClick = {
                menu.dismiss()
                onClick()
            },
        )
    },
)

private fun Hsv.toColor() = Color(ColorUtil.hsvToColor(hue, saturation, brightness))
