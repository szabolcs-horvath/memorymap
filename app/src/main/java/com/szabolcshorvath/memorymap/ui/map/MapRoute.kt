package com.szabolcshorvath.memorymap.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.model.LatLng
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.ui.common.MyLocationQuestions
import com.szabolcshorvath.memorymap.ui.common.rememberMyLocationRequester
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.PerfUtil
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The Map tab with its ViewModel: the screen, the "my location" flow, the pins it keeps the panel and the camera in step
 * with, and what it does once shown (go to the phone, show a memory asked for from another screen).
 */
@Composable
fun MapRoute(viewModel: MapViewModel, common: CommonViewModel, onOpenMemory: (Int) -> Unit) {
    val state by viewModel.uiState(common.showFragmentsEnabled, common.markerClusteringEnabled).collectAsStateWithLifecycle()
    // The only place the map asks for location access: when the user taps the "my location" button.
    val myLocation = rememberMyLocationRequester(viewModel::showMyLocation)
    val openMemory by rememberUpdatedState(onOpenMemory)
    val loadTrace = remember { PerfUtil.startTrace("map_full_load") }
    val actions = remember(viewModel, myLocation) {
        object : MapActions {
            override fun pickQuickFilter(option: DateFilterOption) = viewModel.pickQuickFilter(option)
            override fun pickDates(start: LocalDate, end: LocalDate) = viewModel.pickDates(start, end)
            override fun toggleStats() = viewModel.toggleStats()
            override fun goToMyLocation() {
                viewModel.clearSelection()
                myLocation.start()
            }
            override fun tapPin(pin: Markerable.MarkerableCluster) = viewModel.tapPin(pin)
            override fun tapCluster(position: LatLng) = viewModel.tapCluster(position)
            override fun tapMap() = viewModel.tapMap()
            override fun openMemory(id: Int) = openMemory(id)
            override fun onMapLoaded() = loadTrace.stop()
            override fun onCameraMoved(request: MapCameraRequest) = viewModel.onCameraMoved(request)
        }
    }

    // The pins, while the map is at least started (also hidden behind another screen), keep the panel and camera in step.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, common, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.getMarkerClusters(common.showFragmentsEnabled).collect(viewModel::onPinsChanged)
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose { }
    }
    // A memory asked for from another screen, once the map is shown.
    val focus by viewModel.pendingFocus.collectAsStateWithLifecycle()
    LaunchedEffect(focus, lifecycleOwner) {
        val request = focus ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        viewModel.handleFocus(request)
    }

    MapContent(state, actions)
    MyLocationQuestions(myLocation)
}
