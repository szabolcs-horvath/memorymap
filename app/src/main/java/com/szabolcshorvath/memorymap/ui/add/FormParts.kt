package com.szabolcshorvath.memorymap.ui.add

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.fragment.FormPart
import com.szabolcshorvath.memorymap.fragment.FormPlace
import com.szabolcshorvath.memorymap.fragment.FragmentEditState
import com.szabolcshorvath.memorymap.fragment.Hsv
import com.szabolcshorvath.memorymap.fragment.SelectedMedia
import com.szabolcshorvath.memorymap.ui.color.COLOR_STRIP_HEIGHT
import com.szabolcshorvath.memorymap.ui.color.ColorSliders
import com.szabolcshorvath.memorymap.ui.color.SWATCH_GAP
import com.szabolcshorvath.memorymap.ui.color.Swatch
import com.szabolcshorvath.memorymap.ui.color.toColor
import com.szabolcshorvath.memorymap.util.Coordinates
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import java.time.ZonedDateTime

// The UI tests find these by name (screens/AddScreen).
private const val TAG_FRAGMENT_CARD = "fragmentEditCard"
private const val TAG_OWN_TIME = "switch_useSpecificTime"
private const val TAG_TILE_DETAILS = "tvErrorDetails"

/** Turns the right-pointing chevron down while the sliders are open. */
private const val CHEVRON_OPEN = 90f

/** How long a colour takes to reach a preset tapped. */
private const val PRESET_ANIMATION_MS = 300

/** The scrim on a video's thumbnail behind its play icon. */
private const val PLAY_SCRIM_ALPHA = 0.45f

/** The memory's marker colour, as on Settings: a card with the colour across its top, the presets and the sliders. */
@Composable
@Suppress("LongParameterList")
internal fun MarkerColorCard(
    color: Hsv,
    expanded: Boolean,
    presets: List<HSVPreset>,
    enabled: Boolean,
    part: FormPart,
    actions: AddMemoryActions,
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_MARGIN),
        shape = ListItemDefaults.segmentedShapes(0, 1).shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        MarkerColorBlock(color, expanded, presets, enabled, showStrip = true, part, actions)
    }
}

/**
 * A marker colour: optionally a strip of it on top ([showStrip]), its name with a chevron that opens the sliders, and
 * the presets as swatches (the one with this colour selected). A tapped preset slides the colour to it.
 */
@Composable
@Suppress("LongParameterList")
private fun MarkerColorBlock(
    color: Hsv,
    expanded: Boolean,
    presets: List<HSVPreset>,
    enabled: Boolean,
    showStrip: Boolean,
    part: FormPart,
    actions: AddMemoryActions,
) {
    var fromPreset by remember { mutableStateOf(false) }
    val spec = if (fromPreset) tween<Float>(PRESET_ANIMATION_MS) else snap()
    val hue by animateFloatAsState(color.hue, spec, label = "hue")
    val saturation by animateFloatAsState(color.saturation, spec, label = "saturation")
    val brightness by animateFloatAsState(color.brightness, spec, label = "brightness")
    val shown = Hsv(hue, saturation, brightness)
    Column {
        if (showStrip) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(COLOR_STRIP_HEIGHT)
                    .background(shown.toColor())
            )
        }
        val rotation by animateFloatAsState(if (expanded) CHEVRON_OPEN else 0f, label = "chevron")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { actions.toggleColor(part) }
                .padding(horizontal = PAGE_MARGIN, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.marker_color), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PAGE_MARGIN - SWATCH_GAP)
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
        ) {
            presets.forEach { preset ->
                val presetColor = Hsv(preset.hue, preset.saturation, preset.brightness)
                Swatch(presetColor, selected = preset.hasColor(color.hue, color.saturation, color.brightness), onClick = {
                    fromPreset = true
                    actions.setColor(part, presetColor)
                })
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = PAGE_MARGIN, end = PAGE_MARGIN, bottom = 12.dp)) {
                ColorSliders(shown, enabled) {
                    fromPreset = false
                    actions.setColor(part, it)
                }
            }
        }
    }
}

/**
 * A fragment of the memory, as one card: its colour across the top, its place (tap to pick it, with Remove), its own
 * dates or the memory's, and its marker colour.
 */
@Composable
@Suppress("LongParameterList")
internal fun FragmentCard(
    fragment: FragmentEditState,
    showErrors: Boolean,
    presets: List<HSVPreset>,
    enabled: Boolean,
    actions: AddMemoryActions,
    onPick: (PickerRequest) -> Unit,
) {
    val part = FormPart.Fragment(fragment.localId)
    val color = Hsv(fragment.markerHue, fragment.markerSaturation, fragment.markerBrightness)
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_MARGIN)
            .testTag(TAG_FRAGMENT_CARD),
        shape = ListItemDefaults.segmentedShapes(0, 1).shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(COLOR_STRIP_HEIGHT)
                    .background(color.toColor())
            )
            FragmentPlace(FormPlace.of(fragment.lat, fragment.lng, fragment.placeName, fragment.address), showErrors, { actions.pickLocation(part) }) {
                actions.removeFragment(fragment.localId)
            }
            SwitchRow(
                R.string.fragment_own_time,
                fragment.isTimeVisible,
                Modifier.testTag(TAG_OWN_TIME),
                supporting = if (fragment.isTimeVisible) null else stringResource(R.string.inherited_from_group),
            ) { actions.setOwnTime(fragment.localId, it) }
            if (fragment.isTimeVisible) {
                DateRows(
                    part,
                    fragment.startDate ?: ZonedDateTime.now(),
                    fragment.endDate ?: ZonedDateTime.now().plusHours(1),
                    fragment.isAllDay,
                    actions,
                    onPick,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = PAGE_MARGIN, vertical = 4.dp))
            MarkerColorBlock(color, fragment.isColorExpanded, presets, enabled, showStrip = false, part, actions)
        }
    }
}

/** A fragment's place: a row that opens the location picker, with a button that removes the fragment. */
@Composable
private fun FragmentPlace(place: FormPlace?, showErrors: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val missing = showErrors && place == null
    val errorColor = MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(start = PAGE_MARGIN, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_location_on),
            contentDescription = null,
            tint = if (missing) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            val headline = when {
                place == null -> stringResource(R.string.no_location_selected)
                !place.name.isNullOrEmpty() -> place.name
                else -> Coordinates.format(context, place.lat, place.lng)
            }
            Text(headline, Modifier.testTag(TAG_LOCATION), style = MaterialTheme.typography.bodyLarge)
            val supporting = when {
                missing -> stringResource(R.string.save_needs_fragment_locations)
                place != null && !place.name.isNullOrEmpty() -> place.address?.takeIf { it.isNotEmpty() }
                else -> null
            }
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (missing) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.remove_fragment))
        }
    }
}

/**
 * A photo or video picked for the memory, square with rounded corners, with a small button in its corner that removes
 * it. One saved on another phone, or that fails to load, shows what it was: its type, size and date.
 */
@Composable
internal fun SelectedMediaTile(item: SelectedMedia, currentDeviceId: String?, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val isVideo = item.type == MediaType.VIDEO
    val fromOtherDevice = currentDeviceId != null && item.deviceId != currentDeviceId
    var failed by remember(item.uri) { mutableStateOf(false) }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (fromOtherDevice || failed) {
            MediaDetails(item)
        } else {
            val context = LocalContext.current
            val request = remember(item.uri, isVideo) {
                ImageRequest.Builder(context).data(item.uri).crossfade(true).apply {
                    if (isVideo) {
                        videoFrameMicros(0)
                        decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                    }
                }.build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
            if (isVideo) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = PLAY_SCRIM_ALPHA), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = Color.White)
                }
            }
        }
        FilledTonalIconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                stringResource(if (isVideo) R.string.remove_video else R.string.remove_photo),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MediaDetails(item: SelectedMedia) {
    val context = LocalContext.current
    val type = stringResource(if (item.type == MediaType.VIDEO) R.string.video else R.string.photo)
    val size = if (item.fileSize > 0) Formatter.formatFileSize(context, item.fileSize) else stringResource(R.string.unknown_size)
    val date = if (item.dateTaken > 0) DateTimeFormatterUtil.twoLineDateTime(context, item.dateTaken) else stringResource(R.string.unknown_date)
    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_warning),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(R.string.media_tile_details, type, size, date),
            modifier = Modifier.testTag(TAG_TILE_DETAILS),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
