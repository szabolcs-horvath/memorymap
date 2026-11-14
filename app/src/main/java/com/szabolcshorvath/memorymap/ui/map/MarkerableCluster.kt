package com.szabolcshorvath.memorymap.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.szabolcshorvath.memorymap.data.Markerable

/** A pin on the map: the memories and fragments at one spot ([MarkerClustering]), in list order. */
data class MarkerableCluster(val items: List<Markerable>) : ClusterItem {
    /** The middle of the items. Longitudes are taken relative to the first item, so a pin on the 180th meridian stays there. */
    override val position: LatLng = run {
        val first = items.first()
        val longitudeOffset = items.sumOf { MarkerClustering.wrapLongitude(it.longitude - first.longitude) } / items.size
        LatLng(items.sumOf { it.latitude } / items.size, first.longitude + longitudeOffset) // LatLng wraps the longitude
    }

    override val title: String?
        get() = if (items.size == 1) items.first().title else null // The map names a pin of several memories itself, in the phone's language.

    override val snippet: String? = null

    override val zIndex: Float? = null
}
