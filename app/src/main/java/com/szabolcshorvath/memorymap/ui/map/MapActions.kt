package com.szabolcshorvath.memorymap.ui.map

import androidx.compose.ui.unit.IntSize
import com.google.android.gms.maps.model.LatLng
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.util.DateFilterOption
import java.time.LocalDate

/** What the Map tab's controls do. */
interface MapActions {
    fun pickQuickFilter(option: DateFilterOption)
    fun pickDates(start: LocalDate, end: LocalDate)
    fun toggleStats()
    fun goToMyLocation()
    fun tapPin(pin: Markerable.MarkerableCluster)
    fun tapCluster(position: LatLng)
    fun tapMap()
    fun openMemory(id: Int)
    fun onMapLoaded()
    fun onCameraMoved(request: MapCameraRequest)

    /** The user moved the map by hand (a drag, a pinch, the zoom buttons). */
    fun onMapMovedByHand()

    /** The map is [size] big: a new size means the phone was turned, or the window resized. */
    fun onMapSized(size: IntSize)
}
