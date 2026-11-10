package com.szabolcshorvath.memorymap.ui.location

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.szabolcshorvath.memorymap.fragment.CameraRequest

/** What the location picker's controls do. */
interface PickLocationActions {
    fun close()
    fun search()
    fun goToMyLocation()
    fun pickOnMap(latLng: LatLng)
    fun pickPoi(poi: PointOfInterest)
    fun confirm()
    fun onCameraMoved(request: CameraRequest)
    fun onMessageShown()
}
