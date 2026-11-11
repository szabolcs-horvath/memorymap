package com.szabolcshorvath.memorymap.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.szabolcshorvath.memorymap.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * The map's own buttons in one floating toolbar: zoom in, zoom out and "my location". Google's zoom buttons are turned
 * off for it, and Google's my-location button appears only once location access is granted, so it can't ask for it.
 * The toolbar floats over the map, so the map keeps no room for it and Google's logo stays at the bottom.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapToolbar(camera: CameraPositionState, onMyLocation: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    fun move(update: CameraUpdate) {
        scope.launch { camera.animate(update) }
    }
    VerticalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = mapControlColor(),
            toolbarContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        // A shadow would show through the see-through toolbar; the filter bar has none either.
        expandedShadowElevation = 0.dp,
    ) {
        IconButton(onClick = { move(CameraUpdateFactory.zoomIn()) }) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.zoom_in))
        }
        IconButton(onClick = { move(CameraUpdateFactory.zoomOut()) }) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.zoom_out))
        }
        IconButton(onClick = onMyLocation) {
            Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.my_location))
        }
    }
}

/** The colour of the controls over the map: the panels' colour, a little see-through so the map shows under them. */
@Composable
internal fun mapControlColor() = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MAP_CONTROL_ALPHA)

private const val MAP_CONTROL_ALPHA = 0.85f

/**
 * The map's [toolbar] at the bottom right, with [panel] under it. Where the screen is wide enough (a phone turned
 * sideways, a tablet) the toolbar is at the right edge beside the panel instead: stacked, the two don't fit a turned
 * phone's height. The panel is centred on the screen and as wide as the filter bar over the map, so the two line up.
 * [panel] is told whether it covers the bottom left corner, where Google's logo is (see [PanelSize]).
 */
@Composable
internal fun ToolbarAndPanel(
    toolbar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable (coversLogo: Boolean) -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(MARGIN)
    ) {
        val besideTheLogo = (maxWidth - minOf(maxWidth, PANEL_WIDTH)) / 2 + MARGIN >= LOGO_ROOM
        // Centred, the panel leaves room for the toolbar on both sides.
        if (maxWidth >= PANEL_WIDTH + TOOLBAR_ROOM * 2) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = PANEL_WIDTH)
            ) { panel(!besideTheLogo) }
            Box(Modifier.align(Alignment.BottomEnd)) { toolbar() }
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = PANEL_WIDTH)
                    // Full width, so the toolbar stays at the right with no panel under it.
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                toolbar()
                // The panel brings the gap above it (see [PanelSize]), so the toolbar moves with it as it opens and closes.
                panel(!besideTheLogo)
            }
        }
    }
}

/** Google's logo at the map's bottom left, with its margin. */
private val LOGO_ROOM = 96.dp

/** The toolbar's width with the space beside it. */
private val TOOLBAR_ROOM = 96.dp

/** The filter bar's width: the controls' width less the bar's margins. */
private val PANEL_WIDTH = MAX_CONTROLS_WIDTH - MARGIN * 2

/**
 * The panel at the map's bottom, in pixels: its height as it opens or closes ([shown]), its full height ([full]), and
 * whether it covers the bottom left corner, where Google's logo is ([coversLogo]: on a phone held upright). Both
 * heights include the [MARGIN] gap above the panel, which is as wide as the margin under it: so the panel's height is
 * also how far up the map's bottom it reaches, and the gap opens and closes with it instead of jumping.
 */
internal data class PanelSize(val shown: Int = 0, val full: Int = 0, val coversLogo: Boolean = true) {
    /** The room the map keeps at its bottom, so Google's logo stays above the panel, as it opens or closes. */
    fun mapPadding(): Int = if (coversLogo) shown else 0

    /** How far up the map's bottom the open panel reaches (0 until it has a size). */
    fun coveredBelow(): Int = full
}

/**
 * Where the camera goes to show this place [up] higher than the map's centre at [zoom]: that far south of it, on the
 * map's Web Mercator projection (the whole world is 256 dp wide at zoom 0, twice that at each level). So a place can be
 * centred in the part of the map that a panel leaves open, without the map keeping room for the panel.
 */
internal fun LatLng.cameraTargetFor(up: Dp, zoom: Float): LatLng {
    if (up.value <= 0f) return this
    val world = WORLD_DP * 2.0.pow(zoom.toDouble())
    val mercator = ln(tan(QUARTER_TURN + Math.toRadians(latitude) / 2))
    val y = (1 - mercator / PI) / 2 * world + up.value
    val lat = Math.toDegrees(2 * atan(exp(PI * (1 - 2 * y / world))) - PI / 2)
    return LatLng(lat, longitude)
}

private const val WORLD_DP = 256.0
private const val QUARTER_TURN = PI / 4

/**
 * Runs a camera [move], then says it is [done]. A move cancelled because the map left the screen (the phone was turned,
 * so the screen is built again) is not done: it stays requested and runs on the new screen. A move the map itself
 * interrupted (a finger on it) is done.
 */
internal suspend fun runCameraMove(move: suspend () -> Unit, done: () -> Unit) {
    try {
        move()
    } catch (cancelled: CancellationException) {
        if (!currentCoroutineContext().isActive) throw cancelled
    }
    done()
}
