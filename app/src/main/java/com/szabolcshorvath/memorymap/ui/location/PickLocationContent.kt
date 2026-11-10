package com.szabolcshorvath.memorymap.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.remember
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
import com.szabolcshorvath.memorymap.fragment.CameraRequest
import com.szabolcshorvath.memorymap.fragment.PickLocationUiState
import com.szabolcshorvath.memorymap.fragment.PickedSpot
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
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.cameraRequest) {
        val request = state.cameraRequest ?: return@LaunchedEffect
        try {
            camera.moveTo(request)
        } finally {
            actions.onCameraMoved(request)
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        // Cleared only afterwards: clearing it changes this effect's key, which would cancel the Snackbar.
        snackbars.showSnackbar(resources.getString(message))
        actions.onMessageShown()
    }

    Box(
        Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
    ) {
        PickerMap(
            state = state,
            camera = camera,
            padding = with(density) { PaddingValues(top = topHeight.toDp(), bottom = bottomHeight.toDp()) },
            actions = actions,
        )
        SearchField(
            onBack = actions::close,
            onSearch = actions::search,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topHeight = it.height }
                .padding(MARGIN),
        )
        BottomControls(
            state,
            actions,
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomHeight = it.height },
        )
        SnackbarHost(
            snackbars,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { bottomHeight.toDp() }),
        )
    }
}

private suspend fun CameraPositionState.moveTo(request: CameraRequest) {
    val target = LatLng(request.lat, request.lng)
    val zoom = request.zoom
    if (request.animate) {
        animate(if (zoom == null) CameraUpdateFactory.newLatLng(target) else CameraUpdateFactory.newLatLngZoom(target, zoom))
    } else {
        position = CameraPosition.fromLatLngZoom(target, zoom ?: position.zoom)
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
            zoomControlsEnabled = true,
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

/** The "my location" button over the place panel. */
@Composable
private fun BottomControls(state: PickLocationUiState, actions: PickLocationActions, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(MARGIN),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(MARGIN),
    ) {
        FloatingActionButton(
            onClick = actions::goToMyLocation,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.my_location))
        }
        PlacePanel(state, actions::confirm)
    }
}

/** The picked place's name and address (or what to do when nothing is picked), and the button that uses it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlacePanel(state: PickLocationUiState, onConfirm: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
