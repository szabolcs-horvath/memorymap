package com.szabolcshorvath.memorymap.ui.map

import android.content.Context
import android.content.res.Resources
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.AdvancedMarker
import com.google.android.gms.maps.model.AdvancedMarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultAdvancedMarkersClusterRenderer
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.Markerable
import com.szabolcshorvath.memorymap.util.MarkerGenerator

/**
 * Draws the map's pins in their memories' colours (a pin for several memories at one place shows them all and their
 * count), and pins close together as one circle with their colours and count, unless [clustering] is off.
 */
internal class MarkerRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<Markerable.MarkerableCluster>,
    private val clustering: () -> Boolean,
) : DefaultAdvancedMarkersClusterRenderer<Markerable.MarkerableCluster>(context, map, clusterManager) {

    private val resources: Resources = context.resources

    override fun shouldRenderAsCluster(cluster: Cluster<Markerable.MarkerableCluster>): Boolean =
        clustering() && super.shouldRenderAsCluster(cluster)

    override fun onBeforeClusterItemRendered(pin: Markerable.MarkerableCluster, advancedMarkerOptions: AdvancedMarkerOptions) {
        advancedMarkerOptions.icon(pinIcon(pin))
        advancedMarkerOptions.anchor(MarkerGenerator.PIN_ANCHOR_U, MarkerGenerator.PIN_ANCHOR_V)
        advancedMarkerOptions.title(pinTitle(pin))
    }

    override fun onClusterItemUpdated(pin: Markerable.MarkerableCluster, marker: Marker) {
        marker.setIcon(pinIcon(pin))
        marker.setAnchor(MarkerGenerator.PIN_ANCHOR_U, MarkerGenerator.PIN_ANCHOR_V)
        // The same title as when it was first drawn: a pin of several memories has none of its own.
        marker.title = pinTitle(pin)
    }

    override fun onBeforeClusterRendered(cluster: Cluster<Markerable.MarkerableCluster>, advancedMarkerOptions: AdvancedMarkerOptions) {
        val items = cluster.items.flatMap { it.items }
        advancedMarkerOptions.icon(clusterIcon(items))
        advancedMarkerOptions.anchor(MarkerGenerator.CIRCLE_ANCHOR_U, MarkerGenerator.CIRCLE_ANCHOR_V)
        // A title names the cluster for screen readers (a tap zooms in, so it opens no info window).
        advancedMarkerOptions.title(clusterTitle(items.size))
    }

    override fun onClusterUpdated(cluster: Cluster<Markerable.MarkerableCluster>, marker: AdvancedMarker) {
        val items = cluster.items.flatMap { it.items }
        marker.setIcon(clusterIcon(items))
        marker.setAnchor(MarkerGenerator.CIRCLE_ANCHOR_U, MarkerGenerator.CIRCLE_ANCHOR_V)
        marker.title = clusterTitle(items.size)
    }

    private fun pinIcon(pin: Markerable.MarkerableCluster): BitmapDescriptor =
        if (pin.items.size > 1) {
            val colors = pin.items.map { it.markerColor() }.sorted()
            MarkerGenerator.multiColorBitmapPinIcon(colors, pin.items.size, resources.displayMetrics.density)
        } else {
            MarkerGenerator.singleColorPinConfigIcon(pin.items.first().markerColor())
        }

    private fun clusterIcon(items: List<Markerable>): BitmapDescriptor =
        MarkerGenerator.multiColorBitmapCircleIcon(items.map { it.markerColor() }.sorted(), items.size, resources.displayMetrics.density)

    private fun clusterTitle(count: Int) = resources.getQuantityString(R.plurals.memories_nearby, count, count)

    /** A pin's title: its memory's, or how many memories are at its spot. */
    private fun pinTitle(pin: Markerable.MarkerableCluster): String? =
        if (pin.items.size == 1) {
            pin.items.first().title
        } else {
            resources.getQuantityString(R.plurals.memories_here, pin.items.size, pin.items.size)
        }
}
