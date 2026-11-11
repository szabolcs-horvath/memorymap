package com.szabolcshorvath.memorymap.ui.location

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.fragment.CameraRequest
import com.szabolcshorvath.memorymap.fragment.PickLocationFragmentViewModel
import com.szabolcshorvath.memorymap.ui.common.MyLocationQuestions
import com.szabolcshorvath.memorymap.ui.common.rememberMyLocationRequester

/**
 * The location picker with its ViewModel: the screen, Google's place search screen, and the "my location" flow.
 * [onConfirm] gets the picked spot with its name and address; [onClose] leaves without one.
 */
@Composable
fun PickLocationRoute(
    viewModel: PickLocationFragmentViewModel,
    onClose: () -> Unit,
    onConfirm: (lat: Double, lng: Double, placeName: String?, address: String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The only place the picker asks for location access: when the user taps the "my location" button.
    val myLocation = rememberMyLocationRequester(viewModel::selectMyLocation)
    val search = rememberLauncherForActivityResult(AppServices.placeSearch.screen) { chosen ->
        // Null: the user left the search screen, or it failed.
        chosen?.let(viewModel::lookUpSearched)
    }
    val close by rememberUpdatedState(onClose)
    val confirm by rememberUpdatedState(onConfirm)
    val actions = remember(viewModel, myLocation, search) {
        object : PickLocationActions {
            override fun close() = close()
            override fun search() {
                if (viewModel.canSearch()) search.launch(Unit)
            }
            override fun goToMyLocation() = myLocation.start()
            override fun pickOnMap(latLng: LatLng) = viewModel.pickOnMap(latLng)
            override fun pickPoi(poi: PointOfInterest) = viewModel.pickPoi(poi)
            override fun confirm() {
                val lat = viewModel.selectedLat ?: return
                val lng = viewModel.selectedLng ?: return
                confirm(lat, lng, viewModel.selectedPlaceName, viewModel.selectedAddress)
            }
            override fun onCameraMoved(request: CameraRequest) = viewModel.onCameraMoved(request)
            override fun onMessageShown() = viewModel.onMessageShown()
        }
    }
    LaunchedEffect(viewModel) { viewModel.onShown() }

    PickLocationContent(state, actions)
    MyLocationQuestions(myLocation)
}
