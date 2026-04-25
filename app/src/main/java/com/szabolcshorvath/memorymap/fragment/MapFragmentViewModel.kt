package com.szabolcshorvath.memorymap.fragment

import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

class MapFragmentViewModel : ViewModel() {
    var selectedMemoryId: Int? = null
    var selectedMarkerPosition: LatLng? = null
    var isInitialZoomDone = false
    var lastCameraPosition: CameraPosition? = null

    var pendingSelectionId: Int? = null
    var pendingSelectionLat: Double? = null
    var pendingSelectionLng: Double? = null

    var isStatsVisible = false
}
