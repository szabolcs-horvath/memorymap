package com.szabolcshorvath.memorymap.util

import com.google.maps.android.clustering.ClusterManager
import com.szabolcshorvath.memorymap.data.Markerable.MarkerableCluster

/** The items to take off the map and to put on it, to go from the markers drawn before to the new ones. */
data class MarkerChanges(val removed: List<MarkerableCluster>, val added: List<MarkerableCluster>) {
    val isEmpty: Boolean get() = removed.isEmpty() && added.isEmpty()

    companion object {
        /**
         * Only what changed: a memory edited elsewhere moves or redraws its own marker, not every marker. An item is
         * compared by everything its marker shows, so an edited one is both removed and added.
         */
        fun between(drawn: Set<MarkerableCluster>, next: Set<MarkerableCluster>) = MarkerChanges(
            removed = (drawn - next).toList(),
            added = (next - drawn).toList()
        )
    }
}

/**
 * Keeps [clusterManager] in step with the map's items: it gets only the items that changed, and re-clusters only if
 * something did, or clustering was turned on or off. Its renderer keeps the marker of every item it still has.
 */
class MarkerSync(private val clusterManager: ClusterManager<MarkerableCluster>) {
    private var drawn: Set<MarkerableCluster> = emptySet()
    private var drawnWithClustering: Boolean? = null

    fun update(items: List<MarkerableCluster>, clustering: Boolean) {
        val next = items.toSet()
        val changes = MarkerChanges.between(drawn, next)
        if (changes.removed.isNotEmpty()) clusterManager.removeItems(changes.removed)
        if (changes.added.isNotEmpty()) clusterManager.addItems(changes.added)
        if (!changes.isEmpty || clustering != drawnWithClustering) clusterManager.cluster()
        drawn = next
        drawnWithClustering = clustering
    }
}
