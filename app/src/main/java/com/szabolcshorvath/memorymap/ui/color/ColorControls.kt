package com.szabolcshorvath.memorymap.ui.color

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.util.ColorUtil
import kotlin.math.roundToInt

// The UI tests find the slider values by these names (screens/ColorPresetsSection): on Settings and the Add form.
private const val TAG_HUE_VALUE = "tv_HueValue"
private const val TAG_SATURATION_VALUE = "tv_SaturationValue"
private const val TAG_BRIGHTNESS_VALUE = "tv_BrightnessValue"

private const val MAX_HUE = 360f
private const val HUE_STEP = 5f
private const val FRACTION_STEP = 0.05f

/** Swatches are white or black on top: the check mark takes the other one. */
private const val LIGHT_COLOR = 0.5f

internal val COLOR_STRIP_HEIGHT = 12.dp
internal val SWATCH_SIZE = 44.dp
internal val SWATCH_GAP = 4.dp

/** The slider's handle: Material 3's, with an outline, so a handle as dark or as light as the page still shows. */
private val HANDLE_SIZE = DpSize(6.dp, 44.dp)
private val HANDLE_OUTLINE = 1.dp

internal fun Hsv.toColor() = Color(ColorUtil.hsvToColor(hue, saturation, brightness))

/**
 * A colour as a circle. Selected, it gets a ring in the page's text colour and a check mark, so the selection isn't
 * told by an outline alone; screen readers say "selected" and the colour's numbers.
 */
@Composable
internal fun Swatch(hsv: Hsv, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = hsv.toColor()
    val description = stringResource(R.string.color_preset_description, hsv.hue.toInt(), hsv.saturation, hsv.brightness)
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

/** The hue, saturation and brightness sliders of a marker colour, each with its name and value above it. */
@Composable
internal fun ColorSliders(color: Hsv, enabled: Boolean, onChange: (Hsv) -> Unit) {
    // Followed as the phone's language changes.
    val locale = LocalConfiguration.current.locales[0]
    Column {
        ColorSlider(
            label = R.string.color_slider_hue,
            valueText = String.format(locale, "%d", color.hue.toInt()),
            valueTag = TAG_HUE_VALUE,
            value = color.hue,
            range = 0f..MAX_HUE,
            step = HUE_STEP,
            thumbColor = color.toColor(),
            enabled = enabled,
        ) { onChange(color.copy(hue = it)) }
        ColorSlider(
            label = R.string.color_slider_saturation,
            valueText = String.format(locale, "%.2f", color.saturation),
            valueTag = TAG_SATURATION_VALUE,
            value = color.saturation,
            range = 0f..1f,
            step = FRACTION_STEP,
            thumbColor = color.toColor(),
            enabled = enabled,
        ) { onChange(color.copy(saturation = it)) }
        ColorSlider(
            label = R.string.color_slider_brightness,
            valueText = String.format(locale, "%.2f", color.brightness),
            valueTag = TAG_BRIGHTNESS_VALUE,
            value = color.brightness,
            range = 0f..1f,
            step = FRACTION_STEP,
            thumbColor = color.toColor(),
            enabled = enabled,
        ) { onChange(color.copy(brightness = it)) }
    }
}

/**
 * A slider with its name and value above it. It moves in steps of [step] without drawing a tick for each (the hue has
 * 72), and its handle shows the colour being edited, outlined.
 */
@Composable
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
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
        val outline = MaterialTheme.colorScheme.outline
        Slider(
            state = sliderState,
            onValueChange = { onChange(snap(it)) },
            enabled = enabled,
            colors = colors,
            thumb = {
                Box(
                    Modifier
                        .size(HANDLE_SIZE)
                        .border(HANDLE_OUTLINE, outline, CircleShape)
                        .padding(HANDLE_OUTLINE)
                        .background(thumbColor, CircleShape)
                )
            },
            // No stop indicator: the track's end needs no dot when every value along it can be picked.
            track = { SliderDefaults.Track(it, enabled = enabled, colors = colors, drawStopIndicator = null) },
        )
    }
}
