package com.szabolcshorvath.memorymap.ui.location

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.AdvancedMarker
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.common.rememberSafeArea
import com.szabolcshorvath.memorymap.ui.map.MAX_CONTROLS_WIDTH
import com.szabolcshorvath.memorymap.ui.map.MapToolbar
import com.szabolcshorvath.memorymap.ui.map.PanelSize
import com.szabolcshorvath.memorymap.ui.map.ToolbarAndPanel
import com.szabolcshorvath.memorymap.ui.map.cameraTargetFor
import com.szabolcshorvath.memorymap.ui.map.mapControlColor
import com.szabolcshorvath.memorymap.ui.map.runCameraMove
import com.szabolcshorvath.memorymap.util.Coordinates
import com.szabolcshorvath.memorymap.util.MarkerGenerator

/**
 * The location picker: the map, a search bar over its top (it opens Google's place search screen), and at the bottom
 * the "my location" button and a panel with the picked place and the button that uses it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PickLocationContent(state: PickLocationUiState, actions: PickLocationActions) {
    val camera = rememberCameraPositionState()
    val density = LocalDensity.current
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    // Only the place panel keeps the map's bottom clear, and only where it would cover Google's logo: the toolbar
    // floats over the map. Elsewhere a shown place goes in the part of the map above the panel.
    var panel by remember { mutableStateOf(PanelSize()) }
    val above by rememberUpdatedState(with(density) { (if (panel.coversLogo) 0 else panel.coveredBelow() / 2).toDp() })
    val snackbars = remember { SnackbarHostState() }
    Effects(state, camera, snackbars, actions) { above }

    // The map draws behind the status bar (and in landscape the navigation bar); its controls stay clear of them.
    val safeArea = rememberSafeArea()
    Box(
        safeArea.track(Modifier.fillMaxSize())
            .semantics { testTagsAsResourceId = true }
    ) {
        PickerMap(
            state = state,
            camera = camera,
            // The search field's height includes the status bar.
            padding = with(density) {
                safeArea.mapPadding(top = topHeight.toDp(), bottom = panel.mapPadding().toDp(), topInset = false)
            },
            actions = actions,
        )
        SearchField(
            onBack = actions::close,
            onSearch = actions::search,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topHeight = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                // As wide as the Map's filter bar, so it lines up with the place panel when the phone is turned.
                .widthIn(max = MAX_CONTROLS_WIDTH)
                .padding(MARGIN),
        )
        BottomControls(
            state,
            camera,
            actions,
            onPanelSize = { change -> panel = panel.change() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomHeight = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        )
        SnackbarHost(
            snackbars,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { bottomHeight.toDp() }),
        )
    }
}

/** Moves the camera for each request (a place [above] the map's centre) and shows each message. */
@Composable
private fun Effects(
    state: PickLocationUiState,
    camera: CameraPositionState,
    snackbars: SnackbarHostState,
    actions: PickLocationActions,
    above: () -> Dp,
) {
    val resources = LocalResources.current
    LaunchedEffect(state.cameraRequest) {
        val request = state.cameraRequest ?: return@LaunchedEffect
        runCameraMove(move = { camera.moveTo(request, above()) }, done = { actions.onCameraMoved(request) })
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        // Cleared only afterwards: clearing it changes this effect's key, which would cancel the Snackbar.
        snackbars.showSnackbar(resources.getString(message))
        actions.onMessageShown()
    }
}

/** Moves the camera to [request]'s place, which goes [above] the map's centre. */
private suspend fun CameraPositionState.moveTo(request: CameraRequest, above: Dp) {
    val zoom = request.zoom ?: position.zoom
    val target = LatLng(request.lat, request.lng).cameraTargetFor(above, zoom)
    if (request.animate) {
        animate(CameraUpdateFactory.newLatLngZoom(target, zoom))
    } else {
        position = CameraPosition.fromLatLngZoom(target, zoom)
    }
}

@Composable
private fun PickerMap(
    state: PickLocationUiState,
    camera: CameraPositionState,
    padding: PaddingValues,
    actions: PickLocationActions,
) {
    val context = LocalContext.current
    val mapId = stringResource(R.string.map_id)
    val pinColor = MaterialTheme.colorScheme.primary.toArgb()
    val pin = remember(pinColor) { MarkerGenerator.singleColorPinConfigIcon(pinColor) }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        // Advanced markers need the map's id.
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
        onMapClick = actions::pickOnMap,
        onPOIClick = actions::pickPoi,
        contentPadding = padding,
    ) {
        state.picked?.let { spot ->
            val title = spot.title(context)
            // A new marker for each spot and name, so screen readers hear the new one.
            key(spot.lat, spot.lng, title) {
                AdvancedMarker(
                    state = rememberUpdatedMarkerState(LatLng(spot.lat, spot.lng)),
                    title = title,
                    contentDescription = title,
                    icon = pin,
                    // The panel shows the name: no info window.
                    onClick = { true },
                )
            }
        }
    }
}

private fun PickedSpot.title(context: android.content.Context): String =
    name ?: address ?: Coordinates.format(context, lat, lng)

/** A search bar's look, but a tap opens Google's place search screen, where the typing happens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(onBack: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onSearch,
        modifier = modifier
            .fillMaxWidth()
            .height(SearchBarDefaults.InputFieldHeight),
        shape = SearchBarDefaults.inputFieldShape,
        color = mapControlColor(),
        shadowElevation = ELEVATION,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
            }
            Text(
                stringResource(R.string.pick_location_search),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.padding(horizontal = MARGIN),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The map's toolbar and the place panel. */
@Composable
private fun BottomControls(
    state: PickLocationUiState,
    camera: CameraPositionState,
    actions: PickLocationActions,
    onPanelSize: (PanelSize.() -> PanelSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolbarAndPanel(toolbar = { MapToolbar(camera, actions::goToMyLocation) }, modifier) { coversLogo ->
        LaunchedEffect(coversLogo) { onPanelSize { copy(coversLogo = coversLogo) } }
        // Always open: its height is both its full and its shown height.
        PlacePanel(
            state,
            actions::confirm,
            Modifier
                .onSizeChanged { size -> onPanelSize { copy(shown = size.height, full = size.height) } }
                .padding(top = MARGIN),
        )
    }
}

/** The picked place's name and address (or what to do when nothing is picked), and the button that uses it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlacePanel(state: PickLocationUiState, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = ELEVATION,
    ) {
        Column(Modifier.padding(PANEL_PADDING)) {
            val spot = state.picked
            when {
                state.isLookingUp -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(LOADING_SIZE))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.pick_location_looking_up), style = MaterialTheme.typography.titleMedium)
                }
                spot != null -> {
                    val title = spot.title(context)
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = when (title) {
                        spot.name -> spot.address ?: Coordinates.format(context, spot.lat, spot.lng)
                        spot.address -> Coordinates.format(context, spot.lat, spot.lng)
                        else -> null
                    }
                    details?.takeIf { it != title }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                else -> Text(
                    stringResource(R.string.pick_location_nothing_picked),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(MARGIN))
            Button(
                onClick = onConfirm,
                enabled = spot != null && !state.isLookingUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CONFIRM),
            ) {
                Text(stringResource(R.string.pick_location_confirm))
            }
        }
    }
}

private const val TAG_CONFIRM = "bt_confirm"
private val MARGIN = 16.dp
private val PANEL_PADDING = 20.dp
private val ELEVATION = 3.dp
private val LOADING_SIZE = 32.dp
