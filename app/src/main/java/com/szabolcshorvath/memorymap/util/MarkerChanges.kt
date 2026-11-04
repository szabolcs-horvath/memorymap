package com.szabolcshorvath.memorymap.util

import com.google.maps.android.clustering.ClusterManager
import com.szabolcshorvath.memorymap.data.Markerable.MarkerableCluster

/**
 * What a map marker was drawn from: its item, and the titles it showed then. A fragment's title comes from its memory
 * and is not part of its equality, and the same fragment object gets the new title when the memory is renamed, so the
 * titles are copied here when the marker is drawn.
 */
data class DrawnMarker(val item: MarkerableCluster, val titles: List<String?>) {
    companion object {
        fun of(items: List<MarkerableCluster>): Set<DrawnMarker> =
            items.mapTo(LinkedHashSet()) { item -> DrawnMarker(item, item.items.map { it.title }) }
    }
}

/** The items to take off the map and to put on it, to go from the markers drawn before to the new ones. */
data class MarkerChanges(val removed: List<MarkerableCluster>, val added: List<MarkerableCluster>) {
    val isEmpty: Boolean get() = removed.isEmpty() && added.isEmpty()

    companion object {
        /** Only what changed: a memory edited elsewhere moves or redraws its own marker, not every marker. */
        fun between(drawn: Set<DrawnMarker>, next: Set<DrawnMarker>) = MarkerChanges(
            removed = (drawn - next).map { it.item },
            added = (next - drawn).map { it.item }
        )
    }
}

/**
 * Keeps [clusterManager] in step with the map's items: it gets only the items that changed, and re-clusters only if
 * something did, or clustering was turned on or off. Its renderer keeps the marker of every item it still has.
 */
class MarkerSync(private val clusterManager: ClusterManager<MarkerableCluster>) {
    private var drawn: Set<DrawnMarker> = emptySet()
    private var drawnWithClustering: Boolean? = null

    fun update(items: List<MarkerableCluster>, clustering: Boolean) {
        val next = DrawnMarker.of(items)
        val changes = MarkerChanges.between(drawn, next)
        if (changes.removed.isNotEmpty()) clusterManager.removeItems(changes.removed)
        if (changes.added.isNotEmpty()) clusterManager.addItems(changes.added)
        if (!changes.isEmpty || clustering != drawnWithClustering) clusterManager.cluster()
        drawn = next
        drawnWithClustering = clustering
    }
}
