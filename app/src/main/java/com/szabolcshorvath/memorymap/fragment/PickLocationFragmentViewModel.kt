package com.szabolcshorvath.memorymap.fragment

import androidx.lifecycle.ViewModel

class PickLocationFragmentViewModel : ViewModel() {
    var selectedLat: Double? = null
    var selectedLng: Double? = null
    var selectedPlaceName: String? = null
    var selectedAddress: String? = null
    var activeAutocompletePlaceId: String? = null
    var permissionDenied = false
}
