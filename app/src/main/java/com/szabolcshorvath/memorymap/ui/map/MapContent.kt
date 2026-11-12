package com.szabolcshorvath.memorymap.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.rememberCameraPositionState
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.fragment.MapCameraRequest
import com.szabolcshorvath.memorymap.fragment.MapUiState
import com.szabolcshorvath.memorymap.ui.add.RangePicker
import com.szabolcshorvath.memorymap.ui.common.rememberSafeArea
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

/**
 * The Map tab: the memories' pins on Google's map, the date filter's button group over its top, the colour statistics
 * under it, and at the bottom the "my location" button and the panel listing the memories of a tapped pin.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MapContent(state: MapUiState, actions: MapActions) {
    val camera = rememberCameraPositionState()
    val density = LocalDensity.current
    var topHeight by remember { mutableIntStateOf(0) }
    // Only the memories panel keeps the map's bottom clear, and only where it would cover Google's logo: the toolbar
    // floats over the map.
    var panel by remember { mutableStateOf(PanelSize()) }
    var mapReady by remember { mutableStateOf(false) }
    var pickingDates by rememberSaveable { mutableStateOf(false) }

    // Where the map keeps no room for the open panel, a shown place goes in the part of the map above it.
    val panelOpen = state.selection != null
    CameraRequests(state.cameraRequest, camera, mapReady, actions) {
        if (panelOpen && !panel.coversLogo) panel.coveredBelow() else null
    }

    // The map draws behind the status bar (and in landscape the navigation bar); its controls stay clear of them.
    val safeArea = rememberSafeArea()
    Box(
        safeArea.track(Modifier.fillMaxSize())
            .semantics { testTagsAsResourceId = true }
    ) {
        MemoryMap(
            state = state,
            camera = camera,
            padding = with(density) { safeArea.mapPadding(top = topHeight.toDp(), bottom = panel.mapPadding().toDp()) },
            actions = actions,
            onReady = { mapReady = true },
        )
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .widthIn(max = MAX_CONTROLS_WIDTH)
                .padding(horizontal = MARGIN, vertical = TOP_MARGIN),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(TOP_MARGIN),
        ) {
            FilterBar(
                state = state,
                pickingDates = pickingDates,
                onPickDates = { pickingDates = true },
                actions = actions,
                modifier = Modifier.onSizeChanged { topHeight = it.height + with(density) { (TOP_MARGIN * 2).roundToPx() } },
            )
            StatsCard(state)
        }
        BottomControls(
            state,
            camera,
            actions,
            onPanelSize = { change -> panel = panel.change() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        )
    }
    if (pickingDates) {
        RangePicker(state.filterStart, state.filterEnd, onClose = { pickingDates = false }, title = R.string.map_pick_dates) { start, end ->
            actions.pickDates(start, end)
        }
    }
}

/** The map's toolbar and the panel of the tapped pin's memories. */
@Composable
private fun BottomControls(
    state: MapUiState,
    camera: CameraPositionState,
    actions: MapActions,
    onPanelSize: (PanelSize.() -> PanelSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolbarAndPanel(toolbar = { MapToolbar(camera, actions::goToMyLocation) }, modifier) { coversLogo ->
        LaunchedEffect(coversLogo) { onPanelSize { copy(coversLogo = coversLogo) } }
        // The last selection stays drawn while the panel slides away.
        var shown by remember { mutableStateOf(state.selection) }
        if (state.selection != null) shown = state.selection
        AnimatedVisibility(
            state.selection != null,
            Modifier
                .fillMaxWidth()
                // Its height as it opens and closes: the map's room for it follows, so nothing jumps.
                .onSizeChanged { size -> onPanelSize { copy(shown = size.height) } },
        ) {
            // Once closed the panel leaves the layout without a last size change, so the map would keep its height.
            DisposableEffect(Unit) { onDispose { onPanelSize { copy(shown = 0) } } }
            Box(
                Modifier
                    .onSizeChanged { size -> onPanelSize { copy(full = size.height) } }
                    .padding(top = MARGIN)
            ) {
                shown?.let { MemoriesPanel(it, actions::openMemory) }
            }
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun MemoryMap(
    state: MapUiState,
    camera: CameraPositionState,
    padding: PaddingValues,
    actions: MapActions,
    onReady: () -> Unit,
) {
    val context = LocalContext.current
    val mapId = stringResource(R.string.map_id)
    val clustering by rememberUpdatedState(state.clustering)
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        // The pins are advanced markers, which need the map's id.
        googleMapOptionsFactory = { GoogleMapOptions().mapId(mapId) },
        properties = MapProperties(isMyLocationEnabled = state.showsMyLocation),
        uiSettings = MapUiSettings(
            mapToolbarEnabled = false,
            // Our own button instead: Google's appears only once location access is granted, and can't ask for it.
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            // Our own, in MapToolbar.
            zoomControlsEnabled = false,
        ),
        onMapClick = { actions.tapMap() },
        onMapLoaded = actions::onMapLoaded,
        contentPadding = padding,
    ) {
        val clusterManager = rememberClusterManager<Markerable.MarkerableCluster>()
        MapEffect(clusterManager) { map ->
            val manager = clusterManager ?: return@MapEffect
            manager.renderer = MarkerRenderer(context, map, manager) { clustering }
            manager.setOnClusterItemClickListener { pin ->
                actions.tapPin(pin)
                true
            }
            manager.setOnClusterClickListener { cluster ->
                actions.tapCluster(cluster.position)
                true
            }
            onReady()
        }
        if (clusterManager != null) {
            // Clustering regroups the pins when it sees the camera stop, which it misses for a move within one frame.
            LaunchedEffect(clusterManager) {
                snapshotFlow { if (camera.isMoving) null else camera.position.zoom }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { clusterManager.onCameraIdle() }
            }
            // Turning clustering on or off draws the pins again.
            key(state.clustering) { Clustering(items = state.pins, clusterManager = clusterManager) }
        }
    }
}

/** Moves the camera for each request once the map is ready and the tab is shown, then clears the request. */
@Composable
private fun CameraRequests(
    request: MapCameraRequest?,
    camera: CameraPositionState,
    mapReady: Boolean,
    actions: MapActions,
    coveredBelow: () -> Int?,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val covered by rememberUpdatedState(coveredBelow)
    LaunchedEffect(request, mapReady) {
        if (request == null || !mapReady) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        runCameraMove(
            move = {
                // A pin tapped while the panel opens: wait for its size, briefly, to centre the pin above it.
                val below = if (request is MapCameraRequest.Show && covered() != null) {
                    withTimeoutOrNull(PANEL_WAIT_MS) { snapshotFlow { covered() ?: 0 }.first { it > 0 } } ?: 0
                } else {
                    0
                }
                camera.moveFor(request, density, above = with(density) { (below / 2).toDp() })
            },
            done = { actions.onCameraMoved(request) },
        )
    }
}

/** Moves the camera for [request]; a place it shows goes [above] the map's centre. */
private suspend fun CameraPositionState.moveFor(request: MapCameraRequest, density: Density, above: Dp) {
    when (request) {
        is MapCameraRequest.Show -> {
            val target = request.position.cameraTargetFor(above, request.zoom)
            if (request.animate) {
                animate(CameraUpdateFactory.newLatLngZoom(target, request.zoom))
            } else {
                position = CameraPosition.fromLatLngZoom(target, request.zoom)
            }
        }
        is MapCameraRequest.MyLocation -> {
            // Never zooms out: closer than the area view, or already on it around the phone, it shows the streets.
            val closer = position.zoom > MY_LOCATION_ZOOM + ZOOM_SLACK ||
                (position.zoom >= MY_LOCATION_ZOOM - ZOOM_SLACK && isCentredOn(request.position, density))
            val zoom = if (closer) maxOf(position.zoom, MY_LOCATION_CLOSE_ZOOM) else MY_LOCATION_ZOOM
            animate(CameraUpdateFactory.newLatLngZoom(request.position, zoom))
        }
        is MapCameraRequest.ZoomIn -> animate(CameraUpdateFactory.newLatLngZoom(request.position, position.zoom + 1f))
        is MapCameraRequest.Fit -> fit(request.positions, density)
    }
}

/** Whether the map's centre is on [target], give or take a fingertip. */
private fun CameraPositionState.isCentredOn(target: LatLng, density: Density): Boolean {
    val projection = projection ?: return false
    val here = projection.toScreenLocation(target)
    val centre = projection.toScreenLocation(position.target)
    return hypot((here.x - centre.x).toFloat(), (here.y - centre.y).toFloat()) <= with(density) { CENTRED_TOLERANCE.toPx() }
}

/** Shows all of [positions], but no closer than a single memory is shown at. */
private suspend fun CameraPositionState.fit(positions: List<LatLng>, density: Density) {
    val distinct = positions.distinct()
    if (distinct.isEmpty()) return
    if (distinct.size == 1) {
        animate(CameraUpdateFactory.newLatLngZoom(distinct.first(), MAX_FIT_ZOOM))
        return
    }
    val bounds = LatLngBounds.builder().apply { distinct.forEach(::include) }.build()
    animate(CameraUpdateFactory.newLatLngBounds(bounds, with(density) { FIT_PADDING.roundToPx() }))
    if (position.zoom > MAX_FIT_ZOOM) move(CameraUpdateFactory.zoomTo(MAX_FIT_ZOOM))
}

/** The closest the map zooms in to show memories: a single memory, a tapped pin, a memory shown from another tab. */
internal const val MAX_FIT_ZOOM = 15f

/** The area around the phone: where the map opens with location access, and the first press of "my location". */
internal const val MY_LOCATION_ZOOM = 12f

/** The streets around the phone: a second press of "my location". */
private const val MY_LOCATION_CLOSE_ZOOM = 16f
private const val ZOOM_SLACK = 0.1f
private val CENTRED_TOLERANCE = 24.dp

/** How long a camera move waits for a panel that opens with it to have a size. */
private const val PANEL_WAIT_MS = 500L
private val FIT_PADDING = 40.dp
internal val MARGIN = 16.dp
private val TOP_MARGIN = 8.dp
internal val MAX_CONTROLS_WIDTH = 500.dp

/** The colour statistics under the filter bar, opening and closing as the filter's menu does, from their button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatsCard(state: MapUiState) {
    val motion = MaterialTheme.motionScheme
    // The statistics button is at the bar's right end, right above the card's top right corner.
    val fromButton = TransformOrigin(1f, 0f)
    AnimatedVisibility(
        state.isStatsVisible,
        enter = scaleIn(motion.fastSpatialSpec(), CLOSED_SCALE, fromButton) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec(), CLOSED_SCALE, fromButton) + fadeOut(motion.fastEffectsSpec()),
    ) {
        ColorStats(state.colorCounts)
    }
}

/** How small a menu starts and ends (Material 3's menus use the same). */
private const val CLOSED_SCALE = 0.8f
